package com.multiplatform.applovin.native

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Renders the preloaded MAX Native ad held by [adState].
 *
 * This composable does **not** trigger a new load — the ad was already loaded inside
 * [rememberNativeAd]. Passing a preloaded [NativeAdState] ensures the native view is
 * never recreated when the list item scrolls off-screen and back.
 *
 * Only show this composable when [NativeAdState.isAdReady] is `true` to avoid
 * reserving empty layout space before the ad is ready:
 * ```kotlin
 * if (adState.isAdReady) {
 *     NativeAdView(adState = adState)
 * }
 * ```
 *
 * @param adState preloaded state obtained from [rememberNativeAd].
 * @param modifier modifier applied to the ad container.
 */
@Composable
expect fun NativeAdView(
    adState: NativeAdState,
    modifier: Modifier = Modifier,
)
