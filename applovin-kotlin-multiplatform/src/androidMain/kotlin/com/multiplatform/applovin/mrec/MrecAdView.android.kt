package com.multiplatform.applovin.mrec

import android.widget.FrameLayout
import android.view.ViewGroup
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

/**
 * Android actual for [MrecAdView].
 *
 * Wraps [adState.nativeAdView] inside a [FrameLayout] that [AndroidView] manages.
 * [MaxAdView] is never passed directly as the [AndroidView] view because it is a
 * singleton living at screen scope: if this composable leaves and re-enters
 * composition ([isAdReady] toggles false → true, or tab navigation), [AndroidView]
 * creates a **new** wrapper and calls [factory] again with the same [MaxAdView]
 * instance — causing `IllegalStateException: The specified child already has a parent`.
 *
 * ### Why a FrameLayout wrapper?
 * - [factory] creates a fresh [FrameLayout] every time, so it never has a stale parent.
 * - The ad view is detached from its previous parent (if any) and added to the new
 *   container inside [factory], keeping it visible and properly laid out.
 * - [onRelease] calls [FrameLayout.removeAllViews] when the composable leaves
 *   composition, so the next [factory] call finds [adView.parent] == null.
 */
@Composable
actual fun MrecAdView(
    adState: MrecAdState,
    modifier: Modifier,
) {
    val adView = adState.nativeAdView
    val minWidthDp = 300.dp

    AndroidView(
        factory = { ctx ->
            FrameLayout(ctx).also { container ->
                // Move adView from any previous parent into this fresh container.
                (adView.parent as? ViewGroup)?.removeView(adView)
                container.addView(
                    adView,
                    FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT,
                    ),
                )
            }
        },
        update = { container ->
            if (adView.parent !== container) {
                (adView.parent as? ViewGroup)?.removeView(adView)
                container.addView(
                    adView,
                    FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT,
                    ),
                )
            }
        },
        onReset = { container ->
            // Called when the AndroidView is recycled by LazyList item reuse.
            // Re-attach the singleton adView into this container if it was detached
            // by a previous onRelease, or if it ended up in a different parent.
            if (adView.parent !== container) {
                (adView.parent as? ViewGroup)?.removeView(adView)
                container.addView(
                    adView,
                    FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT,
                    ),
                )
            }
        },
        onRelease = { container ->
            // Detach adView from the FrameLayout container so it can be safely
            // re-parented by the next factory or onReset call without
            // "The specified child already has a parent" IllegalStateException.
            container.removeAllViews()
        },
        modifier = modifier
            .fillMaxWidth()
            .widthIn(min = minWidthDp),
    )
}

