package com.multiplatform.applovin.mrec

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import com.applovin.mediation.MaxAd
import com.applovin.mediation.MaxAdFormat
import com.applovin.mediation.MaxAdViewAdListener
import com.applovin.mediation.MaxError
import com.applovin.mediation.ads.MaxAdView

/**
 * Android implementation of [MrecAdState].
 *
 * @param nativeAdView the underlying [MaxAdView]; `internal` so [MrecAdView] (same module)
 *   can pass it directly to [AndroidView] without an extra wrapper.
 * @param isAdReadyState mutable backing state for [isAdReady].
 * @param isTablet `true` when the ad was created with [MaxAdFormat.LEADER] (tablet layout).
 */
actual class MrecAdState(
    internal val nativeAdView: MaxAdView,
    private val isAdReadyState: MutableState<Boolean>,
    actual val isTablet: Boolean = false,
) {
    actual val isAdReady: Boolean get() = isAdReadyState.value
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
    onAdLoaded: () -> Unit,
    onAdLoadFailed: (error: String) -> Unit,
): MrecAdState {
    val isAdReady = remember { mutableStateOf(false) }

    // Select ad format based on device type: LEADER for tablets, MREC for phones.
    val adFormat = if (isTablet) MaxAdFormat.LEADER else MaxAdFormat.MREC

    // Create the MaxAdView once; it lives for the lifetime of the calling composable
    // (typically the full screen), not the LazyList item lifecycle.
    val adView = remember(adUnitId) {
        MaxAdView(adUnitId, adFormat).apply {
            setListener(object : MaxAdViewAdListener {
                override fun onAdLoaded(ad: MaxAd) {
                    isAdReady.value = true
                    onAdLoaded()
                }

                override fun onAdLoadFailed(adUnitId: String, error: MaxError) {
                    // isAdReady stays false — no empty layout slot will appear.
                    onAdLoadFailed(error.message)
                }

                override fun onAdClicked(ad: MaxAd) {}
                override fun onAdDisplayed(ad: MaxAd) {}
                override fun onAdDisplayFailed(ad: MaxAd, error: MaxError) {}
                override fun onAdHidden(ad: MaxAd) {}
                override fun onAdExpanded(ad: MaxAd) {}
                override fun onAdCollapsed(ad: MaxAd) {}
            })
        }
    }

    // loadAd() once; destroy on disposal.
    DisposableEffect(adView) {
        adView.loadAd()
        onDispose { adView.destroy() }
    }

    return remember(adView, isAdReady) { MrecAdState(adView, isAdReady, isTablet) }
}
