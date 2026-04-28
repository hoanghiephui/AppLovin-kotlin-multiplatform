package com.multiplatform.applovin.banner

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Renders the preloaded MAX standard Banner ad held by [adState].
 *
 * This composable does **not** call [loadAd] — the ad was already loaded inside
 * [rememberBannerAd]. Passing a preloaded [BannerAdState] ensures the native view
 * is never recreated due to recomposition.
 *
 * Only show this composable when [BannerAdState.isAdReady] is `true` to avoid
 * reserving empty layout space before the ad is ready:
 * ```kotlin
 * if (adState.isAdReady) {
 *     BannerAdStateView(adState = adState)
 * }
 * ```
 *
 * @param adState  preloaded state obtained from [rememberBannerAd].
 * @param modifier modifier applied to the ad container.
 */
@Composable
expect fun BannerAdStateView(
    adState: BannerAdState,
    modifier: Modifier = Modifier,
)
