@file:OptIn(ExperimentalForeignApi::class)

package com.multiplatform.applovin.banner

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.UIKitView
import kotlinx.cinterop.ExperimentalForeignApi

/**
 * iOS actual for [BannerAdStateView].
 *
 * Passes the pre-created [MAAdView] from [adState] directly to [UIKitView].
 * Standard banner dimensions (full-width × 50 dp) are applied so that Compose
 * correctly sizes the UIKit view container. Because [BannerAdStateView] is only
 * shown when [BannerAdState.isAdReady] is `true`, this space is never visible
 * while the ad is loading or when there is no fill.
 */
@Composable
actual fun BannerAdStateView(
    adState: BannerAdState,
    modifier: Modifier,
) {
    UIKitView(
        factory = { adState.nativeAdView },
        modifier = modifier
            .fillMaxWidth()
            .height(50.dp),
    )
}
