package com.multiplatform.applovin.mrec

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

/**
 * Android actual for [MrecAdView].
 *
 * Passes the pre-created [MaxAdView] from [adState] directly to [AndroidView].
 * No [loadAd] call is made here — the ad was already loaded in [rememberMrecAd].
 */
@Composable
actual fun MrecAdView(
    adState: MrecAdState,
    modifier: Modifier,
) {
    AndroidView(
        factory = { adState.nativeAdView },
        modifier = modifier.fillMaxWidth(),
    )
}
