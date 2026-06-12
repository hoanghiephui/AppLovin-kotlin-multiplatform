package com.multiplatform.applovin.mrec

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import com.applovin.mediation.MaxAd
import com.applovin.mediation.MaxAdFormat
import com.applovin.mediation.MaxAdViewAdListener
import com.applovin.mediation.MaxAdViewConfiguration
import com.applovin.mediation.MaxError
import com.applovin.mediation.ads.MaxAdView
import com.multiplatform.applovin.utils.AdRetryState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

/**
 * Android implementation of [MrecAdState].
 *
 * @param nativeAdView the underlying [MaxAdView]; `internal` so [MrecAdView] (same module)
 *   can pass it directly to [AndroidView] without an extra wrapper.
 * @param isAdReadyState mutable backing state for [isAdReady].
 * @param isTablet `true` when the ad was created with [MaxAdFormat.LEADER] (tablet layout).
 */
@Immutable
actual class MrecAdState(
    internal val nativeAdView: MaxAdView,
    private val isAdReadyState: MutableState<Boolean>,
    private val hasFailedState: MutableState<Boolean>,
    actual val isTablet: Boolean = false,
    private val onRefresh: () -> Unit,
    private val onStartLoad: () -> Unit,
) {
    actual val isAdReady: Boolean get() = isAdReadyState.value

    /** `true` when all retry attempts are exhausted and no ad could be loaded. */
    actual val hasFailed: Boolean get() = hasFailedState.value

    /** Cancels any pending retry and fires a fresh ad load request. */
    actual fun refresh() = onRefresh()

    /**
     * Triggers the initial ad load for slots created with `autoLoad = false`.
     * No-op if loading has already started (guarded by [onStartLoad]'s internal flag).
     */
    actual fun startLoad() = onStartLoad()
}

/**
 * Android actual for [rememberMrecAd].
 *
 * Creates a [MaxAdView] once and loads the ad inside a [DisposableEffect] so that
 * [loadAd] fires only on the first composition, not on every recomposition.
 * [MaxAdView.destroy] is called when the calling composable leaves composition.
 *
 * When [isTablet] is `true` the ad is created with [MaxAdFormat.LEADER] (full-width × 90 dp)
 * instead of the default [MaxAdFormat.MREC] (300 × 250 dp).
 */
@Composable
actual fun rememberMrecAd(
    adUnitId: String,
    isTablet: Boolean,
    adPlacement: String,
    autoLoad: Boolean,
    onAdLoaded: () -> Unit,
    onAdLoadFailed: (error: String) -> Unit,
): MrecAdState {
    val isAdReady = remember { mutableStateOf(false) }
    val hasFailed = remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    // Non-observable retry holder — does not trigger recomposition on mutation.
    val retryState = remember { AdRetryState() }

    // ... (rest of the code same as before, updated to use hasFailed)
    // Create the MaxAdView once; it lives for the lifetime of the calling composable
    // (typically the full screen), not the LazyList item lifecycle.
    // We include widthDp in the remember keys to recreate and adjust configuration on configuration changes (e.g. orientation)

    // ... (logic for widthDp, density etc)
    val density = LocalDensity.current
    val containerSize = LocalWindowInfo.current.containerSize
    val screenWidthDp = with(density) { containerSize.width.toDp().value.toInt() }
    // Ensure width is at least 300dp for MREC to avoid "smaller than required adaptive size" errors.
    val widthDp = maxOf(300, screenWidthDp)
    val config = MaxAdViewConfiguration.builder()
        .setAdaptiveType(MaxAdViewConfiguration.AdaptiveType.INLINE)
        .setAdaptiveWidth(widthDp)
        .setInlineMaximumHeight(300)
        .build()
    val adFormat = if (isTablet) MaxAdFormat.LEADER else MaxAdFormat.MREC

    val adView = remember(adUnitId, adPlacement, widthDp) {
        MaxAdView(adUnitId, adFormat, config).apply {
            setListener(object : MaxAdViewAdListener {
                override fun onAdLoaded(ad: MaxAd) {
                    retryState.reset()
                    isAdReady.value = true
                    hasFailed.value = false
                    onAdLoaded()
                }

                override fun onAdLoadFailed(adUnitId: String, error: MaxError) {
                    if (retryState.canRetry) {
                        val delayMs = retryState.incrementAndGetDelayMs()
                        retryState.setJob(scope.launch {
                            delay(delayMs.milliseconds)
                            loadAd()
                        })
                    } else {
                        hasFailed.value = true
                        onAdLoadFailed(error.message)
                    }
                    isAdReady.value = false
                }

                // ... other overrides
                override fun onAdClicked(ad: MaxAd) {}
                override fun onAdDisplayed(ad: MaxAd) {}
                override fun onAdDisplayFailed(ad: MaxAd, error: MaxError) {}
                override fun onAdHidden(ad: MaxAd) {}
                override fun onAdExpanded(ad: MaxAd) {}
                override fun onAdCollapsed(ad: MaxAd) {}
            })
        }
    }

    val hasLoadStarted = remember { mutableStateOf(autoLoad) }
    DisposableEffect(adUnitId, adPlacement) {
        adView.placement = adPlacement
        if (autoLoad) adView.loadAd()
        onDispose {
            adView.setExtraParameter("allow_pause_auto_refresh_immediately", "true")
            adView.stopAutoRefresh()
            retryState.reset()
            adView.destroy()
        }
    }

    return remember(adUnitId, adPlacement) {
        MrecAdState(
            nativeAdView = adView,
            isAdReadyState = isAdReady,
            hasFailedState = hasFailed,
            isTablet = isTablet,
            onRefresh = {
                retryState.reset()
                isAdReady.value = false
                hasFailed.value = false
                hasLoadStarted.value = true
                adView.loadAd()
            },
            onStartLoad = {
                if (!hasLoadStarted.value) {
                    hasLoadStarted.value = true
                    adView.loadAd()
                }
            }
        )
    }
}
