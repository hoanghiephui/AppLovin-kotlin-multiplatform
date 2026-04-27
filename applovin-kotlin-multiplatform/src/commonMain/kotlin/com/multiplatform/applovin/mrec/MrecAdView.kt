package com.multiplatform.applovin.mrec

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Renders the pre-loaded MAX MREC ad held by [adState].
 *
 * This composable does **not** call [loadAd] — the ad was already loaded inside
 * [rememberMrecAd]. Passing a pre-loaded [MrecAdState] ensures the native view is
 * never recreated when the list item scrolls off-screen and back.
 *
 * Only show this composable when [MrecAdState.isAdReady] is `true` to avoid
 * reserving empty layout space before the ad is ready:
 * ```kotlin
 * if (adState.isAdReady) {
 *     MrecAdView(adState = adState)
 * }
 * ```
 *
 * @param adState pre-loaded state obtained from [rememberMrecAd].
 * @param modifier modifier applied to the ad container.
 */
@Composable
expect fun MrecAdView(
    adState: MrecAdState,
    modifier: Modifier = Modifier,
)
