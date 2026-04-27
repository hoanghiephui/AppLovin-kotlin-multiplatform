package com.multiplatform.applovin.banner

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

/**
 * Android actual for [BannerAdStateView].
 *
 * Passes the pre-created [MaxAdView] from [adState] directly to [AndroidView].
 * No [loadAd] call is made here — the ad was already loaded in [rememberBannerAd].
 * [MaxAdView] auto-sizes its height for [MaxAdFormat.BANNER] (~50 dp).
 */
@Composable
actual fun BannerAdStateView(
    adState: BannerAdState,
    modifier: Modifier,
) {
    AndroidView(
        factory = { adState.nativeAdView },
        modifier = modifier.fillMaxWidth(),
    )
}
