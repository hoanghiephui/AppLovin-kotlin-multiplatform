@file:OptIn(ExperimentalForeignApi::class)

package com.multiplatform.applovin.mrec

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.UIKitInteropInteractionMode
import androidx.compose.ui.viewinterop.UIKitInteropProperties
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

    val expectedWidth = UIScreen.mainScreen.bounds.useContents { size.width }
    val expectedHeight = if (isTablet) 90.0 else 250.0
    val adHeightDp = if (isTablet) 90.dp else 250.dp

    UIKitView(
        factory = {
            val container = UIView(frame = CGRectMake(0.0, 0.0, expectedWidth, expectedHeight))

            adView.removeFromSuperview()
            adView.setAutoresizingMask(
                UIViewAutoresizingFlexibleWidth or UIViewAutoresizingFlexibleHeight
            )
            adView.setFrame(container.bounds)

            // Ép cấu hình hiển thị hiển nhiên của lớp Native
            adView.setHidden(false)
            adView.setAlpha(1.0)

            container.addSubview(adView)
            container
        },
        modifier = modifier
            .fillMaxWidth()
            .height(adHeightDp),
        // --- ĐÂY LÀ CHÌA KHÓA CHO COMPOSE 1.11.1 ---
        properties = UIKitInteropProperties(
            // Chế độ Cooperative cấu hình cho các View phức tạp/có tương tác cuộn/quảng cáo
            // Giúp đồng bộ layer hiển thị của iOS Native trực tiếp đè lên canvas của Compose mà không bị delay vẽ
            interactionMode = UIKitInteropInteractionMode.Cooperative(),
            isNativeAccessibilityEnabled = true
        ),
        update = { container ->
            // Chống hiện tượng LazyColumn gán frame (0,0) cho container khi tái sử dụng
            if (container.bounds.useContents { size.width } == 0.0) {
                container.setFrame(CGRectMake(0.0, 0.0, expectedWidth, expectedHeight))
            }

            if (adView.superview !== container) {
                adView.removeFromSuperview()
                container.addSubview(adView)
            }

            // Ép cứng frame cho AdView bằng kích thước thật để SDK không kích hoạt Viewability ẩn
            adView.setFrame(CGRectMake(0.0, 0.0, expectedWidth, expectedHeight))
            adView.setHidden(false)
            adView.setAlpha(1.0)

            // Yêu cầu iOS CoreAnimation vẽ lại ngay lập tức lớp view này
            adView.setNeedsLayout()
            adView.layoutIfNeeded()
        },
        onReset = { _ ->
            // BỎ trống hoặc KHÔNG gọi removeFromSuperview() tại đây ở phiên bản 1.11.1
            // Việc tháo rời view trong danh sách cuộn sẽ làm AppLovin mất dấu cửa sổ window và ẩn đồ họa nội bộ
        },
        onRelease = { _ ->
            // Chỉ tháo hẳn ra khi màn hình này bị đóng hoàn toàn
            adView.removeFromSuperview()
        }
    )
}
