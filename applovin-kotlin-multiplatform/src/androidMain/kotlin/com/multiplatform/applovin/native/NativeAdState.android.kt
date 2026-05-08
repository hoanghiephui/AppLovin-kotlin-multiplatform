package com.multiplatform.applovin.native

import android.content.res.ColorStateList
import android.content.res.Configuration
import android.content.res.Resources
import android.graphics.Color
import android.view.ContextThemeWrapper
import android.widget.TextView
import androidx.annotation.MainThread
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.ViewCompat
import com.applovin.mediation.MaxAd
import com.applovin.mediation.MaxError
import com.applovin.mediation.nativeAds.MaxNativeAdListener
import com.applovin.mediation.nativeAds.MaxNativeAdLoader
import com.applovin.mediation.nativeAds.MaxNativeAdView
import com.applovin.mediation.nativeAds.MaxNativeAdViewBinder
import com.google.android.material.button.MaterialButton
import com.multiplatform.applovin.utils.AdRetryState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.core.graphics.toColorInt

/**
 * Resolves a layout resource ID by name from the application's merged resource set.
 *
 * **Why `Resources.getIdentifier` and not reflection on `R` inner classes?**
 * In a KMP project the ad layout lives in the *host* Android module (e.g. `androidApp`)
 * whose Gradle `namespace` differs from `applicationId` (e.g. `com.bitby.twitch.android`
 * vs `com.bitby.twitchtv`). The generated `R` class uses the namespace as its Java package,
 * so `Class.forName("${context.packageName}.R\$layout")` will always throw
 * `ClassNotFoundException` because the class file lives under a different package.
 *
 * `Resources.getIdentifier` bypasses the Java class hierarchy entirely: it queries the
 * Android asset manager's merged resource table using the runtime *application* package name
 * (`context.packageName` == `applicationId`), which is exactly how all merged resources are
 * indexed at runtime regardless of which module they originated from.
 *
 * **Thread safety:** Must be called on the **main thread**. The underlying `AssetManager2`
 * native implementation is not thread-safe and produces SIGSEGV when called concurrently
 * from background threads. All call sites in this file are inside Compose `remember {}` blocks,
 * which execute on the main thread during composition.
 *
 * @param name Layout resource name (without the `layout/` prefix).
 * @return Non-zero resource ID; throws [IllegalStateException] if the resource is not found.
 */
@MainThread
private fun Resources.resolveLayoutId(name: String, pkg: String): Int =
    getIdentifier(name, "layout", pkg).also { id ->
        check(id != 0) { "Layout resource '$name' not found in package '$pkg'" }
    }

/**
 * Resolves a view ID resource by name from the application's merged resource set.
 *
 * See [resolveLayoutId] for the rationale behind using [Resources.getIdentifier].
 *
 * @param name View resource name (without the `id/` prefix).
 * @return Non-zero resource ID; throws [IllegalStateException] if the resource is not found.
 */
@MainThread
private fun Resources.resolveViewId(name: String, pkg: String): Int =
    getIdentifier(name, "id", pkg).also { id ->
        check(id != 0) { "View ID resource '$name' not found in package '$pkg'" }
    }

/**
 * Applies theme-aware colors to all text views and the CTA [MaterialButton] inside [adView].
 *
 * ### Why programmatic, not XML attrs?
 * AppLovin inflates [MaxNativeAdView] with a [ContextThemeWrapper] that carries
 * [com.google.android.material.R.style.Theme_Material3_DayNight]. While this makes
 * [MaterialButton] inflate correctly, the ad background colour is set by the
 * advertiser's creative (typically white/light), so `?attr/colorOnSurface` may still
 * produce text that is nearly invisible against that background.
 * Hard-coded high-contrast values are the only reliable guarantee.
 *
 * Night-mode detection uses [Configuration.UI_MODE_NIGHT_MASK] from [themedContext]
 * rather than from Compose so it works inside a `remember` block (non-composable context).
 *
 * @param adView  inflated [MaxNativeAdView] to style.
 * @param themedContext the [ContextThemeWrapper] used to create [adView]; its configuration
 *   carries the correct night/day flag.
 */
private fun applyNativeAdColors(adView: MaxNativeAdView, themedContext: ContextThemeWrapper) {
    val isDark = (themedContext.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
        Configuration.UI_MODE_NIGHT_YES

    val titleColor = if (isDark) Color.WHITE else Color.BLACK
    val bodyColor  = if (isDark) "#ADADB8".toColorInt() else "#53535F".toColorInt()
    val ctaBg      = ColorStateList.valueOf("#9146FF".toColorInt()) // Twitch purple
    val ctaText    = Color.WHITE

    val adContext = adView.context
    val res = adContext.resources
    val pkg = adContext.packageName
    fun id(name: String) = res.resolveViewId(name, pkg)

    adView.findViewById<TextView>(id("title_text_view"))?.setTextColor(titleColor)
    adView.findViewById<TextView>(id("advertiser_text_view"))?.setTextColor(bodyColor)
    adView.findViewById<TextView>(id("body_text_view"))?.setTextColor(bodyColor)
    adView.findViewById<MaterialButton>(id("cta_button"))?.let { btn ->
        ViewCompat.setBackgroundTintList(btn, ctaBg)
        btn.setTextColor(ctaText)
    }
}

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
 * ### R class cross-module resource resolution
 * The `com.android.kotlin.multiplatform.library` plugin does not expose this module's
 * generated R class during `compileAndroidMain`, and the ad layout lives in the *host*
 * Android module (e.g. `androidApp`) whose `namespace` differs from `applicationId`.
 * Therefore resource IDs are resolved via [Resources.getIdentifier] using the runtime
 * application package name (`context.packageName` == `applicationId`), which correctly
 * indexes the merged resource table regardless of the originating module's namespace.
 * All lookups are performed on the **main thread** inside Compose `remember {}` blocks
 * and the resolved integer IDs are passed directly to [MaxNativeAdViewBinder], so
 * AppLovin never needs to call [Resources.getIdentifier] from a background thread.
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

    // All resource ID lookups happen here, on the main thread (Compose composition context),
    // which is required for thread-safe AssetManager access. The resolved integer IDs are
    // then passed directly to MaxNativeAdViewBinder, so AppLovin never needs to call
    // getIdentifier() itself.
    val binder = remember(adUnitId) {
        val res = context.resources
        val pkg = context.packageName
        MaxNativeAdViewBinder.Builder(res.resolveLayoutId("max_native_ad_view", pkg))
            .setTitleTextViewId(res.resolveViewId("title_text_view", pkg))
            .setBodyTextViewId(res.resolveViewId("body_text_view", pkg))
            .setAdvertiserTextViewId(res.resolveViewId("advertiser_text_view", pkg))
            .setIconImageViewId(res.resolveViewId("icon_image_view", pkg))
            .setMediaContentViewGroupId(res.resolveViewId("media_view_container", pkg))
            .setOptionsContentViewGroupId(res.resolveViewId("options_view", pkg)) // required — privacy icon
            .setStarRatingContentViewGroupId(res.resolveViewId("star_rating_view", pkg))
            .setCallToActionButtonId(res.resolveViewId("cta_button", pkg))
            .build()
    }

    // NativeAdView is the render target — inflates max_native_ad_view.xml and holds all
    // sub-views AppLovin will populate. Created ONCE; reused across reloads and expiries.
    //
    // IMPORTANT: wrap the base context with a Material3 DayNight ContextThemeWrapper so that
    // all XML theme attributes (?attr/colorOnSurface, ?attr/colorOnSurfaceVariant, etc.) and
    // MaterialButton styles resolve correctly. The Activity theme is Theme.DeviceDefault which
    // has no Material attrs, so without this wrapper all colors would fall back to defaults.
    // ContextThemeWrapper.resources inherits the correct night/day configuration from the
    // original context, so dark-mode switching works automatically.
    val nativeAdView = remember(adUnitId, adPlacement) {
        val themedContext = ContextThemeWrapper(
            context,
            com.google.android.material.R.style.Theme_Material3_DayNight,
        )
        MaxNativeAdView(binder, themedContext).also { applyNativeAdColors(it, themedContext) }
    }

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
                    // Colors will be re-applied by the next onNativeAdLoaded callback.
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
