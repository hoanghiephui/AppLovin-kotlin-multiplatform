package com.multiplatform.applovin.banner

import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

/**
 * Android actual for [BannerAdStateView].
 *
 * Wraps [adState.nativeAdView] inside a [FrameLayout] that [AndroidView] manages.
 * [MaxAdView] is never passed directly as the [AndroidView] root view because it is a
 * singleton living at screen scope: if this composable leaves and re-enters
 * composition ([isAdReady] toggles false → true, or tab navigation), [AndroidView]
 * creates a **new** wrapper and calls [factory] again with the same [MaxAdView]
 * instance — causing `IllegalStateException: The specified child already has a parent`.
 *
 * ### Why a FrameLayout wrapper?
 * - [factory] creates a fresh [FrameLayout] on every entry, so it never has a stale parent.
 * - The ad view is detached from its previous parent (if any) and added to the new
 *   container inside [factory], keeping its internal WebView intact and visible.
 * - [onRelease] calls [FrameLayout.removeAllViews] on the **container** (not on the
 *   MaxAdView) when the composable leaves composition. This sets [adView.parent] = null
 *   so the next [factory] call can safely call [addView] again without a crash.
 *
 * IMPORTANT: never call [removeAllViews] on the [MaxAdView] itself — doing so destroys
 * AppLovin's internal [WebView] and causes the ad to appear blank on re-entry.
 */
@Composable
actual fun BannerAdStateView(
    adState: BannerAdState,
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
        modifier = modifier.fillMaxWidth().height(52.dp),
    )
}
