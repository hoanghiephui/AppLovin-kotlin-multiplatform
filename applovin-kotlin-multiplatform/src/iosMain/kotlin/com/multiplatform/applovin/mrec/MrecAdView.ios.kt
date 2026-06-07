@file:OptIn(ExperimentalForeignApi::class)

package com.multiplatform.applovin.mrec

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.UIKitView
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import platform.CoreGraphics.CGRectMake
import platform.UIKit.UIScreen
import platform.UIKit.UIView
import platform.UIKit.UIViewAutoresizingFlexibleHeight
import platform.UIKit.UIViewAutoresizingFlexibleWidth

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
    val adView = adState.nativeAdView
    val isTablet = adState.isTablet
    val adHeight = if (isTablet) 90.dp else 250.dp

    UIKitView(
        factory = {
            // Calculate a non-zero initial frame based on screen width and expected height.
            // This prevents AppLovin SDK from seeing a 0-area view during the initial
            // attachment phase in a Lazy List.
            val screenWidth = UIScreen.mainScreen.bounds.useContents { size.width }
            val initialHeight = if (isTablet) 90.0 else 250.0
            val container = UIView(frame = CGRectMake(0.0, 0.0, screenWidth, initialHeight))

            // Ensure adView is detached from any previous container.
            adView.removeFromSuperview()

            // Configure adView to automatically follow container's size changes.
            adView.setAutoresizingMask(
                UIViewAutoresizingFlexibleWidth or UIViewAutoresizingFlexibleHeight
            )

            // Sync adView frame to the container's initial non-zero frame.
            adView.setFrame(container.bounds)

            container.addSubview(adView)
            container
        },
        modifier = modifier
            .fillMaxWidth()
            .height(adHeight),
        update = { container ->
            // Re-parent if needed (e.g., if the container was recycled or recreated)
            if (adView.superview !== container) {
                adView.removeFromSuperview()
                adView.setFrame(container.bounds)
                container.addSubview(adView)
            }

            // Sync frame one more time to ensure no drift during layout passes.
            adView.setFrame(container.bounds)

            // Force a layout pass on the native side to ensure the internal webview
            // or native components of the ad are correctly rendered in the new frame.
            adView.setNeedsLayout()
            adView.layoutIfNeeded()
        },
        onReset = { _ ->
            // Detach the singleton adView when the UIKitView is hidden/recycled in a list.
            // This prevents the adView from being stuck in a detached window hierarchy.
            adView.removeFromSuperview()
        },
        onRelease = { _ ->
            // Final detachment when the composable leaves the composition.
            adView.removeFromSuperview()
        }
    )
}
