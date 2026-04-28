package com.multiplatform.applovin.banner

import androidx.compose.runtime.Composable

/**
 * Holds the preloaded state for a MAX Anchored Adaptive Banner ad.
 *
 * Create an instance via [rememberBannerAd] at the *screen* level (outside any
 * pager, [LazyColumn], or conditional branch) so the native view survives
 * recomposition. Pass the instance to [BannerAdStateView] when you want to
 * display it.
 *
 * The banner height adapts dynamically based on device type (phone: ~50dp, tablet: ~90dp).
 * [adaptiveHeightDp] is populated once the ad loads; use it to adjust your layout accordingly.
 *
 * @property isAdReady `true` once the ad creative has loaded and is ready to display.
 * @property adaptiveHeightDp Height of the banner in dp; updated when the ad loads.
 */
expect class BannerAdState {
    val isAdReady: Boolean
    val adaptiveHeightDp: Float
}

/**
 * Creates and remembers a [BannerAdState] for the given [adUnitId].
 *
 * The underlying native view ([MaxAdView] on Android, [MAAdView] on iOS) is created
 * once and lives as long as the calling composable is in composition.
 * [loadAd] is called immediately so the creative is typically ready before
 * the placement becomes visible.
 *
 * Only render [BannerAdStateView] when [BannerAdState.isAdReady] is `true` to
 * avoid reserving empty layout space while the ad is pending or when there is no fill.
 *
 * ```kotlin
 * // At screen level, outside any pager or lazy list:
 * val bannerAdState = rememberBannerAd(adUnitId = "your_unit_id")
 *
 * // In the layout:
 * if (bannerAdState.isAdReady) {
 *     BannerAdStateView(
 *         adState = bannerAdState,
 *         modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth(),
 *     )
 * }
 * ```
 *
 * @param adUnitId          AppLovin MAX ad unit ID for this placement.
 * @param onAdLoaded        Invoked on the main thread when the ad creative is ready.
 * @param onAdLoadFailed    Invoked when no fill is available; [isAdReady] stays `false`.
 */
@Composable
expect fun rememberBannerAd(
    adUnitId: String,
    onAdLoaded: () -> Unit = {},
    onAdLoadFailed: (error: String) -> Unit = {},
): BannerAdState
