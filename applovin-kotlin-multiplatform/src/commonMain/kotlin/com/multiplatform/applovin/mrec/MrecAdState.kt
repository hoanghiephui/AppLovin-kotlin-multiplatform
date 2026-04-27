package com.multiplatform.applovin.mrec

import androidx.compose.runtime.Composable

/**
 * Holds the pre-loaded state for a MAX MREC (300×250) ad.
 *
 * Create an instance via [rememberMrecAd] at the *screen* level (outside any
 * [LazyColumn] or conditional branch) so the native view survives scrolling.
 * Pass the instance to [MrecAdView] when you want to display it.
 *
 * @property isAdReady `true` once the ad creative has loaded and is ready to display.
 */
expect class MrecAdState {
    val isAdReady: Boolean
}

/**
 * Creates and remembers a [MrecAdState] for the given [adUnitId].
 *
 * The underlying native view ([MaxAdView] on Android, [MAAdView] on iOS) is created
 * once and lives as long as the calling composable is in composition.
 * [loadAd] is called immediately so the creative is typically ready before
 * the list item becomes visible.
 *
 * Only include the banner list item when [MrecAdState.isAdReady] is `true` to
 * avoid reserving empty layout space while the ad is pending or when there is no fill.
 *
 * @param adUnitId AppLovin MAX ad unit ID for this placement.
 * @param onAdLoaded Invoked on the main thread when the ad creative is ready.
 * @param onAdLoadFailed Invoked on the main thread when the ad fails to load;
 *   receives an error description string.
 */
@Composable
expect fun rememberMrecAd(
    adUnitId: String,
    onAdLoaded: () -> Unit = {},
    onAdLoadFailed: (error: String) -> Unit = {},
): MrecAdState
