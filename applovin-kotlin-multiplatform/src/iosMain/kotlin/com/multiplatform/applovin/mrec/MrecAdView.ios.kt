@file:OptIn(ExperimentalForeignApi::class)

package com.multiplatform.applovin.mrec

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.UIKitView
import kotlinx.cinterop.ExperimentalForeignApi

/**
 * iOS actual for [MrecAdView].
 *
 * Passes the pre-created [MAAdView] from [adState] directly to [UIKitView].
 * Dimensions are derived from [MrecAdState.isTablet]:
 * - **Tablet** (LEADER format): full-width × 90 dp
 * - **Phone** (MREC format): full-width × 250 dp (standard 300×250 MREC)
 *
 * Because [MrecAdView] is only shown when [MrecAdState.isAdReady] is `true`,
 * this space is never visible while the ad is loading or when there is no fill.
 */
@Composable
actual fun MrecAdView(
    adState: MrecAdState,
    modifier: Modifier,
) {
    UIKitView(
        factory = { adState.nativeAdView },
        modifier = modifier
            .fillMaxWidth()
            .height(if (adState.isTablet) 90.dp else 250.dp),
    )
}
