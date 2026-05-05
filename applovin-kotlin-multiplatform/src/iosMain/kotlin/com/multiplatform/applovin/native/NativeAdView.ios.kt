@file:OptIn(ExperimentalForeignApi::class)

package com.multiplatform.applovin.native

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.UIKitView
import kotlinx.cinterop.ExperimentalForeignApi

/**
 * iOS actual for [NativeAdView].
 *
 * Passes the pre-created [MANativeAdView] from [adState] directly to [UIKitView].
 * The view is collapsed to 0×0 until [NativeAdState.isAdReady] is `true` (the caller
 * should only include this composable when the ad is ready, but the guard here prevents
 * any accidental empty-space flash).
 *
 * [MANativeAdView] is a singleton owned by [NativeAdState] (created in [rememberNativeAd]).
 * UIKitView does not retain an extra reference — it only embeds the view in the UIKit
 * hierarchy while this composable is in composition.
 */
@Composable
actual fun NativeAdView(
    adState: NativeAdState,
    modifier: Modifier,
) {
    // Guard: collapse to 0×0 if called before isAdReady to avoid layout reservation.
    val effectiveMod = if (adState.isAdReady) {
        modifier.fillMaxWidth().wrapContentHeight()
    } else {
        modifier.size(0.dp)
    }

    UIKitView(
        factory = { adState.nativeAdView },
        modifier = effectiveMod,
    )
}
