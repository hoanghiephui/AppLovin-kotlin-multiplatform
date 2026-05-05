package com.multiplatform.applovin.native

import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

/**
 * Android actual for [NativeAdView].
 *
 * Wraps [adState.nativeAdView] ([MaxNativeAdView]) inside a [FrameLayout] that [AndroidView]
 * manages. [MaxNativeAdView] must **not** be passed directly as the [AndroidView] root because
 * it is a singleton living at screen scope: when this composable leaves and re-enters
 * composition (e.g. [NativeAdState.isAdReady] toggles or tab navigation occurs), [AndroidView]
 * calls [factory] again with the same [MaxNativeAdView] instance — which still has a parent —
 * causing `IllegalStateException: The specified child already has a parent`.
 *
 * ### Why a FrameLayout wrapper?
 * - [factory] creates a fresh [FrameLayout] on every entry, so it never carries a stale parent.
 * - The ad view is detached from its previous parent (if any) and added to the new container
 *   inside [factory], preserving AppLovin's internal WebView and keeping the ad visible.
 * - When the composable leaves composition the container's `removeAllViews()` call sets
 *   [MaxNativeAdView.parent] to `null`, so the next [factory] can safely call [addView].
 *
 * IMPORTANT: never call [removeAllViews] on [MaxNativeAdView] directly — doing so destroys
 * AppLovin's internal WebView and causes the ad content to go blank.
 */
@Composable
actual fun NativeAdView(
    adState: NativeAdState,
    modifier: Modifier,
) {
    val adView = adState.nativeAdView
    AndroidView(
        factory = { ctx ->
            FrameLayout(ctx).also { container ->
                // Detach adView from any stale parent left over from a previous composition.
                (adView.parent as? ViewGroup)?.removeView(adView)
                container.addView(
                    adView,
                    FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.WRAP_CONTENT,
                    ),
                )
            }
        },
        modifier = modifier
            .fillMaxWidth()
            .wrapContentHeight(),
    )
}
