package com.multiplatform.applovin.native

import android.content.res.Resources
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import com.applovin.mediation.MaxAd
import com.applovin.mediation.MaxError
import com.applovin.mediation.nativeAds.MaxNativeAdListener
import com.applovin.mediation.nativeAds.MaxNativeAdLoader
import com.applovin.mediation.nativeAds.MaxNativeAdView
import com.applovin.mediation.nativeAds.MaxNativeAdViewBinder
import com.multiplatform.applovin.utils.AdRetryState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Resolves a layout resource ID using the application's package name.
 *
 * The `com.android.kotlin.multiplatform.library` plugin does NOT package `androidMain/res`
 * into the library AAR — `generateAndroidMainEmptyResourceFiles` produces an empty symbol list.
 * The native ad layout therefore lives in `androidApp/src/main/res/layout/` where standard AGP
 * resource processing applies. At runtime all app resources are accessible via [Context.packageName]
 * in [Resources.getIdentifier].
 */
private fun Resources.libLayout(name: String, pkg: String): Int =
    getIdentifier(name, "layout", pkg)

/**
 * Resolves a view resource ID using the application's package name.
 * See [libLayout] for why [Context.packageName] is used instead of a library namespace.
 */
private fun Resources.libId(name: String, pkg: String): Int =
    getIdentifier(name, "id", pkg)

/**
 * Android implementation of [NativeAdState].
 *
 * @param nativeAdView the pre-rendered [MaxNativeAdView]; `internal` so [NativeAdView]
 *   (same module) can add it to its [FrameLayout] wrapper via [AndroidView] without exposing
 *   platform types through the common API.
 * @param isAdReadyState mutable backing state for [isAdReady]; updating it triggers
 *   recomposition in any composable observing [isAdReady].
 * @param onRefresh lambda captured from [rememberNativeAd]'s composition scope; encapsulates
 *   all platform-specific reload logic (retry reset + [MaxNativeAdLoader.loadAd]).
 */
actual class NativeAdState(
    internal val nativeAdView: MaxNativeAdView,
    private val isAdReadyState: MutableState<Boolean>,
    private val hasFailedState: MutableState<Boolean>,
    private val onRefresh: () -> Unit,
    private val onStartLoad: () -> Unit,
) {
    actual val isAdReady: Boolean get() = isAdReadyState.value

    /** `true` when all retry attempts are exhausted and no ad could be loaded. */
    actual val hasFailed: Boolean get() = hasFailedState.value

    /** Cancels any pending retry and fires a fresh ad load request. */
    actual fun refresh() = onRefresh()

    /**
     * Triggers the initial load for slots created with `autoLoad = false`.
     * No-op if loading has already started (guarded by [onStartLoad]'s internal flag).
     */
    actual fun startLoad() = onStartLoad()
}

/**
 * Android actual for [rememberNativeAd].
 *
 * Implementation uses the **pre-rendered** flow: [MaxNativeAdLoader.loadAd] receives the
 * [MaxNativeAdView] directly and AppLovin populates all sub-views before calling
 * [MaxNativeAdListener.onNativeAdLoaded]. This is simpler than the late-binding flow and
 * avoids an extra render step.
 *
 * ### R class workaround
 * The `com.android.kotlin.multiplatform.library` plugin does not expose the module's
 * generated R class during `compileAndroidMain`. Resource IDs are therefore resolved
 * via [Resources.getIdentifier] using [APPLOVIN_LIB_NAMESPACE] at runtime. This is
 * safe because the IDs remain stable in the merged APK and ProGuard/R8 does not shrink
 * layout or view IDs by name.
 *
 * ### Object lifetimes
 * - [MaxNativeAdViewBinder] — built once; immutable.
 * - [MaxNativeAdView] — created once per [adUnitId]+[adPlacement]; AppLovin renders content
 *   directly into this view on each load.
 * - [MaxNativeAdLoader] — created once; owns the network request lifecycle.
 *
 * ### Ad expiry
 * When [MaxNativeAdListener.onNativeAdExpired] fires the loader immediately requests a fresh
 * creative into the same [MaxNativeAdView] so the UI stays up to date without user action.
 *
 * ### Cleanup
 * [DisposableEffect] destroys the current [MaxAd] (if any) and then the loader itself when
 * the composable leaves composition, releasing all AppLovin resources.
 */
@Composable
actual fun rememberNativeAd(
    adUnitId: String,
    adPlacement: String,
    autoLoad: Boolean,
    onAdLoaded: () -> Unit,
    onAdLoadFailed: (error: String) -> Unit,
    onAdClicked: () -> Unit,
    onAdRevenuePaid: () -> Unit,
    onAdRetrying: (attempt: Int, delayMs: Long) -> Unit,
): NativeAdState {
    val context = LocalContext.current
    val isAdReady = remember { mutableStateOf(false) }
    // Observable flag set when all retry attempts are exhausted so the sequential
    // load chain in rememberNativeAdPlacer can advance to the next slot.
    val hasFailed = remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    // Non-observable retry holder — mutations do NOT trigger recomposition.
    val retryState = remember { AdRetryState() }

    // Mutable holder for the last loaded MaxAd, captured by reference so DisposableEffect's
    // onDispose sees the latest value even after recomposition.
    val loadedAdHolder = remember { object { var ad: MaxAd? = null } }

    // Build the view binder once; it maps layout/view IDs to native ad asset slots.
    // Resource IDs are resolved at runtime via context.packageName because the
    // com.android.kotlin.multiplatform.library plugin does not package androidMain/res — the
    // layout lives in androidApp/src/main/res and is accessible via the app package name.
    val binder = remember(adUnitId) {
        val res = context.resources
        val pkg = context.packageName
        MaxNativeAdViewBinder.Builder(res.libLayout("max_native_ad_view", pkg))
            .setTitleTextViewId(res.libId("title_text_view", pkg))
            .setBodyTextViewId(res.libId("body_text_view", pkg))
            .setAdvertiserTextViewId(res.libId("advertiser_text_view", pkg))
            .setIconImageViewId(res.libId("icon_image_view", pkg))
            .setMediaContentViewGroupId(res.libId("media_view_container", pkg))
            .setOptionsContentViewGroupId(res.libId("options_view", pkg)) // required — privacy icon
            .setStarRatingContentViewGroupId(res.libId("star_rating_view", pkg))
            .setCallToActionButtonId(res.libId("cta_button", pkg))
            .build()
    }

    // NativeAdView is the render target — inflates max_native_ad_view.xml and holds all
    // sub-views AppLovin will populate. Created ONCE; reused across reloads and expiries.
    val nativeAdView = remember(adUnitId, adPlacement) { MaxNativeAdView(binder, context) }

    val nativeAdLoader = remember(adUnitId, adPlacement) {
        MaxNativeAdLoader(adUnitId).apply {
            placement = adPlacement
            setNativeAdListener(object : MaxNativeAdListener() {

                override fun onNativeAdLoaded(loadedView: MaxNativeAdView?, ad: MaxAd) {
                    // Destroy the previously loaded ad before storing the new one.
                    loadedAdHolder.ad?.let { this@apply.destroy(it) }
                    loadedAdHolder.ad = ad
                    retryState.reset()
                    isAdReady.value = true
                    onAdLoaded()
                }

                override fun onNativeAdLoadFailed(adUnitId: String, error: MaxError) {
                    if (retryState.canRetry) {
                        // Exponential back-off: attempt 1 → 2s, retry 2 → 4s, retry 3 → 8s.
                        val delayMs = retryState.incrementAndGetDelayMs()
                        onAdRetrying(retryState.count, delayMs)
                        retryState.setJob(scope.launch {
                            delay(delayMs)
                            loadAd(nativeAdView)
                        })
                    } else {
                        // All retries exhausted — surface the failure to the caller.
                        hasFailed.value = true
                        onAdLoadFailed(error.message)
                    }
                }

                override fun onNativeAdClicked(ad: MaxAd) {
                    onAdClicked()
                }

                override fun onNativeAdExpired(ad: MaxAd) {
                    // Transparent refresh: reload into the same view so isAdReady stays true.
                    loadAd(nativeAdView)
                }
            })
        }
    }

    // Fire the first load only when autoLoad is requested. When autoLoad = false the
    // caller (e.g. rememberNativeAdPlacer) is responsible for invoking startLoad() at
    // the right time to implement sequential loading that mirrors MaxAdPlacer's strategy.
    // A hasLoadStarted flag prevents startLoad() from firing a second time if the caller
    // accidentally invokes it on an already-loading slot.
    val hasLoadStarted = remember { mutableStateOf(autoLoad) }
    DisposableEffect(adUnitId, adPlacement) {
        if (autoLoad) nativeAdLoader.loadAd(nativeAdView)
        onDispose {
            retryState.reset()
            loadedAdHolder.ad?.let { nativeAdLoader.destroy(it) }
            nativeAdLoader.destroy()
        }
    }

    return remember(adUnitId, adPlacement) {
        NativeAdState(
            nativeAdView = nativeAdView,
            isAdReadyState = isAdReady,
            hasFailedState = hasFailed,
            onRefresh = {
                retryState.reset()
                isAdReady.value = false
                hasFailed.value = false
                hasLoadStarted.value = true
                nativeAdLoader.loadAd(nativeAdView)
            },
            onStartLoad = {
                // Guard: only start once; subsequent calls are no-ops.
                if (!hasLoadStarted.value) {
                    hasLoadStarted.value = true
                    nativeAdLoader.loadAd(nativeAdView)
                }
            },
        )
    }
}
