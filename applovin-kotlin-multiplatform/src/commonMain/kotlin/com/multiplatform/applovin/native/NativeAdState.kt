package com.multiplatform.applovin.native

import androidx.compose.runtime.Composable

/**
 * Holds the pre-loaded state for a MAX Native ad.
 *
 * Create an instance via [rememberNativeAd] at the *screen* level (outside any
 * [LazyColumn]/[LazyVerticalGrid] or conditional branch) so the native view survives
 * scrolling and recomposition.
 * Pass the instance to [NativeAdView] when you want to display it.
 *
 * @property isAdReady `true` once the native ad creative has loaded and is ready to display.
 *   Only insert the list item when this is `true` to avoid reserving empty layout space
 *   while the ad is pending or when there is no fill.
 */
expect class NativeAdState {
    val isAdReady: Boolean

    /**
     * Manually triggers a fresh ad load, resetting the retry counter.
     *
     * Use this to implement pull-to-refresh or to force a new creative after
     * the user dismisses an error state. Safe to call at any time; if a load
     * is already in progress it will be superseded by the new request.
     */
    fun refresh()
}

/**
 * Creates and remembers a [NativeAdState] for the given [adUnitId].
 *
 * The underlying native ad view is created once and lives as long as the calling
 * composable is in composition. [loadAd] is called immediately so the creative is
 * typically ready before the list item becomes visible.
 *
 * Expiry is handled automatically: when the loaded ad expires the loader transparently
 * reloads a fresh creative into the same view.
 *
 * Only show the ad container when [NativeAdState.isAdReady] is `true` to avoid
 * reserving empty layout space while the ad is pending or when there is no fill.
 *
 * @param adUnitId AppLovin MAX ad unit ID for this placement.
 * @param adPlacement Descriptive placement name used for AppLovin reporting (e.g. "BrowseCategories").
 * @param onAdLoaded Invoked on the main thread when the native ad creative is ready.
 * @param onAdLoadFailed Invoked on the main thread when the ad fails to load after all
 *   retry attempts; receives an error description string.
 * @param onAdClicked Invoked on the main thread when the user taps the ad.
 * @param onAdRevenuePaid Invoked on the main thread each time an impression is tracked;
 *   use this to forward impression-level revenue data to your analytics SDK.
 * @param onAdRetrying Invoked before each retry attempt with the 1-based attempt number
 *   and the back-off delay in milliseconds. Useful for debug logging to confirm retries
 *   are firing (e.g. attempt 1 → 2 000 ms, attempt 2 → 4 000 ms, attempt 3 → 8 000 ms).
 */
@Composable
expect fun rememberNativeAd(
    adUnitId: String,
    adPlacement: String,
    onAdLoaded: () -> Unit = {},
    onAdLoadFailed: (error: String) -> Unit = {},
    onAdClicked: () -> Unit = {},
    onAdRevenuePaid: () -> Unit = {},
    onAdRetrying: (attempt: Int, delayMs: Long) -> Unit = { _, _ -> },
): NativeAdState
