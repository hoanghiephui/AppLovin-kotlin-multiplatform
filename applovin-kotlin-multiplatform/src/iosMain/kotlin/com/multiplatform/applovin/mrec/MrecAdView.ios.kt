@file:OptIn(ExperimentalForeignApi::class)

package com.multiplatform.applovin.mrec

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.UIKitView
import kotlinx.cinterop.ExperimentalForeignApi
import platform.UIKit.UIView

/**
 * iOS actual for [MrecAdView].
 *
 * Uses a **container [UIView] wrapper pattern** (mirroring Android's FrameLayout approach)
 * to decouple [UIKitView]'s lifecycle from [MAAdView]'s lifecycle.
 *
 * ### Scroll-off recovery (full disposal)
 * - [onRelease] detaches [MAAdView] from the outgoing container.
 * - [onReset] re-attaches [MAAdView] and calls [loadAd] to restore content.
 *
 */
@Composable
actual fun MrecAdView(
    adState: MrecAdState,
    modifier: Modifier,
) {
    val isTablet = adState.isTablet
    val adHeightDp = if (isTablet) 90.dp else 250.dp
    val minWidthDp = 300.dp

    UIKitView(
        factory = {
            adState.nativeAdView
        },
        modifier = modifier
            .fillMaxWidth()
            .widthIn(min = minWidthDp)
            .height(adHeightDp)
    )
}
