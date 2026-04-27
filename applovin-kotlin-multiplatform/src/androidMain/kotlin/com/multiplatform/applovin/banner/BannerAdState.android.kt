package com.multiplatform.applovin.banner

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
 * Android implementation of [BannerAdState].
 *
 * @param nativeAdView   the underlying [MaxAdView]; `internal` so [BannerAdStateView]
 *   (same module) can pass it directly to [AndroidView] without an extra wrapper.
 * @param isAdReadyState mutable backing state for [isAdReady].
 */
actual class BannerAdState(
    internal val nativeAdView: MaxAdView,
    private val isAdReadyState: MutableState<Boolean>,
) {
    actual val isAdReady: Boolean get() = isAdReadyState.value
}

/**
 * Android actual for [rememberBannerAd].
 *
 * Creates a [MaxAdView] once with [MaxAdFormat.BANNER] and loads the ad inside a
 * [DisposableEffect] so that [loadAd] fires only on the first composition, not on
 * every recomposition. [MaxAdView.destroy] is called when the calling composable
 * leaves composition.
 */
@Composable
actual fun rememberBannerAd(
    adUnitId: String,
    onAdLoaded: () -> Unit,
    onAdLoadFailed: (error: String) -> Unit,
): BannerAdState {
    val isAdReady = remember { mutableStateOf(false) }

    // Create the MaxAdView once; it lives for the lifetime of the calling composable
    // (typically the full screen), not an inner list-item lifecycle.
    val adView = remember(adUnitId) {
        MaxAdView(adUnitId, MaxAdFormat.BANNER).apply {
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

    return remember(adView, isAdReady) { BannerAdState(adView, isAdReady) }
}
