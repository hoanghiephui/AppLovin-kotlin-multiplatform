package com.multiplatform.applovin.native

import android.content.res.ColorStateList
import android.graphics.Color
import android.view.ContextThemeWrapper
import android.widget.TextView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.SideEffect
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
import com.applovin.sdk.R
import kotlin.time.Duration.Companion.milliseconds

/**
 * Schedules theme-aware color application for all text views and the CTA [MaterialButton]
 * inside [adView].
 *
 * AppLovin's binding pipeline dispatches sub-view population work on the main-thread message
 * queue **after** [MaxNativeAdListener.onNativeAdLoaded] returns, so a plain `setTextColor`
 * call inside that callback is immediately overwritten. Using [android.view.View.post] defers
 * our styling to the next message-queue iteration, which runs after AppLovin's binding
 * completes and makes the color change stick reliably.
 *
 * [isDark] must come from [isSystemInDarkTheme] captured in a [MutableState] so the callback
 * always reads the **current** dark-mode value even after the user toggles the theme, rather
 * than a stale value baked into the `remember` closure at first composition.
 *
 * @param adView inflated [MaxNativeAdView] to style.
 * @param isDark `true` when the app is currently in dark mode.
 * @param ids resource IDs registered by the host app via [configureNativeAdResourceIds].
 */
private fun applyNativeAdColors(
    adView: MaxNativeAdView,
    isDark: Boolean,
    ids: NativeAdResourceIds,
) {
    val titleColor = if (isDark) Color.WHITE else Color.BLACK
    val bodyColor  = if (isDark) "#ADADB8".toColorInt() else "#53535F".toColorInt()
    val ctaBg      = ColorStateList.valueOf("#0F7DEC".toColorInt())
    val ctaText    = Color.WHITE

    // post() defers until after AppLovin finishes populating sub-views on the main thread,
    // preventing our colors from being overwritten by the ad-binding pass.
    adView.post {
        adView.findViewById<TextView>(ids.titleTextViewId)?.setTextColor(titleColor)
        adView.findViewById<TextView>(ids.advertiserTextViewId)?.setTextColor(bodyColor)
        adView.findViewById<TextView>(ids.bodyTextViewId)?.setTextColor(bodyColor)
        adView.findViewById<MaterialButton>(ids.callToActionButtonId)?.let { btn ->
            ViewCompat.setBackgroundTintList(btn, ctaBg)
            btn.setTextColor(ctaText)
        }
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
 * Resource IDs come from [configureNativeAdResourceIds] in the host app
 * (`Application.onCreate`). This avoids runtime name lookups entirely and removes
 * any dependence on [android.content.res.Resources.getIdentifier].
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
    isDark: Boolean,
    autoLoad: Boolean,
    onAdLoaded: () -> Unit,
    onAdLoadFailed: (error: String) -> Unit,
    onAdClicked: () -> Unit,
    onAdRevenuePaid: () -> Unit,
    onAdRetrying: (attempt: Int, delayMs: Long) -> Unit,
): NativeAdState {
    if (adUnitId.isBlank()) {
        throw IllegalArgumentException("adUnitId must be a non-blank string")
    }
    val context = LocalContext.current
    val nativeAdResourceIds = remember { requireNativeAdResourceIds() }
    // isDark is provided by the caller (e.g. TwitchTheme.isDark) so in-app theme overrides
    // (Light / Dark / Auto) are respected. Capture into a MutableState so the onNativeAdLoaded
    // callback — which lives inside a remember closure — always reads the latest value even
    // after the user toggles the theme between ad loads.
    val isDarkState = remember { mutableStateOf(isDark) }
    SideEffect { isDarkState.value = isDark }
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

    // Binder IDs are compile-time IDs provided by the host app at startup.
    val binder = remember(adUnitId) {
        MaxNativeAdViewBinder.Builder(nativeAdResourceIds.layoutId)
            .setTitleTextViewId(nativeAdResourceIds.titleTextViewId)
            .setBodyTextViewId(nativeAdResourceIds.bodyTextViewId)
            .setAdvertiserTextViewId(nativeAdResourceIds.advertiserTextViewId)
            .setIconImageViewId(nativeAdResourceIds.iconImageViewId)
            .setMediaContentViewGroupId(nativeAdResourceIds.mediaContentViewGroupId)
            .setOptionsContentViewGroupId(nativeAdResourceIds.optionsContentViewGroupId) // required - privacy icon
            .setStarRatingContentViewGroupId(nativeAdResourceIds.starRatingContentViewGroupId)
            .setCallToActionButtonId(nativeAdResourceIds.callToActionButtonId)
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
    //
    val nativeAdView = remember(adUnitId, adPlacement) {
        // Wrap with Material3 DayNight theme so ?attr/color* and MaterialButton styles
        // resolve correctly. Dark mode is NOT read from this context — isDarkState (above)
        // is used instead, because themedContext is cached and would return a stale uiMode
        // after the user toggles the theme.
        val themedContext = ContextThemeWrapper(
            context,
            com.google.android.material.R.style.Theme_Material3_DayNight,
        )
        MaxNativeAdView(binder, themedContext).also {
            applyNativeAdColors(it, isDarkState.value, nativeAdResourceIds)
        }
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
                            delay(delayMs.milliseconds)
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

    return remember(adUnitId, adPlacement, isAdReady) {
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
