package com.multiplatform.applovin.mrec

import androidx.compose.runtime.Composable

/**
 * Holds the pre-loaded state for a MAX MREC / LEADER ad.
 *
 * Create an instance via [rememberMrecAd] at the *screen* level (outside any
 * [LazyColumn] or conditional branch) so the native view survives scrolling.
 * Pass the instance to [MrecAdView] when you want to display it.
 *
 * @property isAdReady `true` once the ad creative has loaded and is ready to display.
 * @property isTablet `true` when the ad was created with [MaxAdFormat.LEADER] (tablet layout).
 *   [MrecAdView] uses this to apply the correct dimensions (full-width × 90 dp instead of
 *   300 × 250 dp).
 */
expect class MrecAdState {
    val isAdReady: Boolean
    /** `true` when the underlying ad format is LEADER (tablet); `false` for MREC (phone). */
    val isTablet: Boolean
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
 * @param isTablet When `true` the ad is loaded as [MaxAdFormat.LEADER] (full-width × 90 dp,
 *   suited for tablets). When `false` (default) [MaxAdFormat.MREC] (300 × 250 dp) is used.
 *   Derive this value from [NavigationSuiteType.isTablet] so it stays consistent with the
 *   active navigation layout.
 * @param onAdLoaded Invoked on the main thread when the ad creative is ready.
 * @param onAdLoadFailed Invoked on the main thread when the ad fails to load;
 *   receives an error description string.
 */
@Composable
expect fun rememberMrecAd(
    adUnitId: String,
    isTablet: Boolean = false,
    adPlacement: String,
    onAdLoaded: () -> Unit = {},
    onAdLoadFailed: (error: String) -> Unit = {},
): MrecAdState
