package com.multiplatform.applovin.native

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * A smart container for a native ad slot inside a feed ([LazyColumn] / [LazyVerticalGrid]).
 *
 * ## State machine
 * ```
 *  isAdReady == false && !hasFailed && placeholder != null
 *      → show [placeholder] (space reserved; reduces layout shift when creative arrives)
 *
 *  isAdReady == false && !hasFailed && placeholder == null  ← default
 *      → render nothing (0-height); creative appears in place when ready
 *
 *  isAdReady == true
 *      → show [NativeAdView]
 *
 *  hasFailed == true (or adState == null)
 *      → animate collapse to 0-height so the slot quietly disappears
 * ```
 *
 * ## Usage — no placeholder (default, avoids blank space)
 * ```kotlin
 * val placer = rememberNativeAdPlacer(adUnitId = "...", adPlacement = "BrowseGrid")
 *
 * LazyColumn {
 *     items(count = placer.adjustedSize(items.size), key = { i ->
 *         if (placer.isAdAt(i)) "ad_$i" else "content_${items[placer.contentIndexFor(i)].id}"
 *     }) { i ->
 *         if (placer.isAdAt(i)) {
 *             NativeAdSlot(adState = placer.adStateAt(i))
 *         } else {
 *             ContentItem(items[placer.contentIndexFor(i)])
 *         }
 *     }
 * }
 * ```
 *
 * ## Usage — opt-in placeholder (reserves space, reduces layout shift)
 * ```kotlin
 * NativeAdSlot(
 *     adState = placer.adStateAt(i),
 *     placeholder = { DefaultNativeAdPlaceholder(height = 250.dp) },
 * )
 * ```
 *
 * @param adState The [NativeAdState] for this slot. `null` means the slot index is out of
 *   range (e.g. only 2 ads were requested but this is slot 3); treated as [hasFailed].
 * @param modifier Modifier applied to the outermost container.
 * @param placeholder Optional composable shown while the ad is loading (`!isAdReady && !hasFailed`).
 *   When `null` (the default) the slot renders nothing until the creative arrives — no blank
 *   space is reserved. Pass [DefaultNativeAdPlaceholder] or a custom shimmer to opt-in to
 *   space reservation and reduce layout shift.
 */
@Composable
fun NativeAdSlot(
    adState: NativeAdState?,
    modifier: Modifier = Modifier,
    placeholder: (@Composable () -> Unit)? = null,
) {
    when {
        // Creative ready — show the real ad.
        adState?.isAdReady == true -> {
            NativeAdView(adState = adState, modifier = modifier)
        }

        // Permanently failed — animate the slot to 0-height so it disappears without a
        // hard jump. Content items below slide up smoothly.
        adState == null || adState.hasFailed -> {
            AnimatedVisibility(
                visible = false,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
                modifier = modifier,
            ) {
                // Content doesn't matter; visible = false collapses this immediately.
                Box(Modifier.fillMaxWidth())
            }
        }

        // Still loading — show placeholder if provided, otherwise render nothing.
        placeholder != null -> placeholder()

        // No placeholder requested: render nothing until the creative is ready.
        // The slot will gain its natural height when isAdReady flips to true.
    }
}

/**
 * Default loading placeholder: a solid grey rectangle whose [height] approximates the
 * rendered ad height.
 *
 * Exposed so callers can opt-in to space reservation:
 * ```kotlin
 * NativeAdSlot(
 *     adState = ...,
 *     placeholder = { DefaultNativeAdPlaceholder(height = 250.dp) },
 * )
 * ```
 *
 * Replace with your app's shimmer / skeleton component for a polished look.
 * The color ([Color.LightGray]) is intentionally neutral so it is visible against both
 * light and dark backgrounds without importing a theme dependency.
 */
@Composable
fun DefaultNativeAdPlaceholder(
    height: Dp,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .background(Color.LightGray.copy(alpha = 0.3f)),
    )
}
