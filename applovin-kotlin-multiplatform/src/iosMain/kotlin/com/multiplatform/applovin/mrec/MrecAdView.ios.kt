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
 * Standard MREC dimensions (300×250 dp) are applied here so that Compose correctly
 * sizes the UIKit view container. Because [MrecAdView] is only shown when
 * [MrecAdState.isAdReady] is `true`, this space is never visible while the ad
 * is loading or when there is no fill.
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
            .height(250.dp),
    )
}
