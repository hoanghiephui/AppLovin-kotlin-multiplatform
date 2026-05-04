@file:OptIn(ExperimentalForeignApi::class)

package com.multiplatform.applovin.banner

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import cocoapods.AppLovinSDK.MAAd
import cocoapods.AppLovinSDK.MAAdFormat
import cocoapods.AppLovinSDK.MAAdView
import cocoapods.AppLovinSDK.MAAdViewAdDelegateProtocol
import cocoapods.AppLovinSDK.MAAdViewAdaptiveType
import cocoapods.AppLovinSDK.MAAdViewConfiguration
import cocoapods.AppLovinSDK.MAError
import com.multiplatform.applovin.utils.AdRetryState
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import platform.CoreGraphics.CGRectMake
import platform.UIKit.UIColor
import platform.UIKit.UIScreen
import platform.UIKit.UIView
import platform.darwin.NSObject

/**
 * iOS implementation of [BannerAdState].
 *
 * @param nativeAdView        the underlying [MAAdView]; `internal` so [BannerAdStateView]
 *   can pass it directly to [UIKitView] without an extra wrapper.
 * @param isAdReadyState      mutable backing state for [isAdReady].
 * @param adaptiveHeightDpState mutable backing state for [adaptiveHeightDp].
 */
actual class BannerAdState(
    internal val nativeAdView: MAAdView,
    private val isAdReadyState: MutableState<Boolean>,
    private val adaptiveHeightDpState: MutableState<Float>,
) {
    actual val isAdReady: Boolean get() = isAdReadyState.value
    actual val adaptiveHeightDp: Float get() = adaptiveHeightDpState.value
}

/**
 * iOS actual for [rememberBannerAd].
 *
 * Creates a [MAAdView] once with an explicit full-width × 50 pt frame so
 * [ALViewabilityTimer] does not log a "0 area" error. [loadAd] is called inside
 * [DisposableEffect] to preload the creative before the placement is visible.
 *
 * Note: [MAAdView.delegate] is an ObjC `weak` property and does NOT retain the
 * assigned object. The delegate is stored in [remember] so Kotlin/Native's ARC-based
 * GC cannot collect it before callbacks fire.
 */
@Composable
actual fun rememberBannerAd(
    adUnitId: String,
    onAdLoaded: () -> Unit,
    onAdLoadFailed: (error: String) -> Unit,
): BannerAdState {
    val isAdReady = remember { mutableStateOf(false) }
    val adaptiveHeightDp = remember { mutableStateOf(50f) } // Default iPhone height
    val scope = rememberCoroutineScope()
    // Non-observable retry holder — does not trigger recomposition on mutation.
    val retryState = remember { AdRetryState() }

    // Create the MAAdView once; it lives for the lifetime of the calling composable.
    val adView = remember(adUnitId) {
        val screenWidth = UIScreen.mainScreen.bounds.useContents { size.width }

        // Build Anchored Adaptive Banner config before creating the view.
        // Matches the Swift pattern:
        //   let config = MAAdViewConfiguration { builder in builder.adaptiveType = .anchored }
        //   adView = MAAdView(adUnitIdentifier: id, adFormat: .banner, configuration: config)
        val config = MAAdViewConfiguration.configurationWithBuilderBlock { builder ->
            builder?.setAdaptiveType(MAAdViewAdaptiveType.MAAdViewAdaptiveTypeAnchored)
        }

        MAAdView(adUnitId, MAAdFormat.banner(), config).apply {
            // Set an explicit banner frame so ALViewabilityTimer sees a non-zero area
            // even before the view is embedded in the Compose UIKit hierarchy.
            setFrame(CGRectMake(0.0, 0.0, screenWidth, 50.0))
            backgroundColor = UIColor.clearColor
        }
    }

    // Compose holds a strong reference to the delegate for the composable's lifetime,
    // preventing Kotlin/Native GC from collecting it before callbacks fire.
    // MAAdView.delegate is an ObjC `weak` property and does NOT retain the object.
    val delegate = remember(adView) {
        BannerStateAdDelegate(
            onAdLoaded = { heightDp ->
                retryState.reset()
                adaptiveHeightDp.value = heightDp
                isAdReady.value = true
                onAdLoaded()
            },
            onAdLoadFailed = { error ->
                if (retryState.canRetry) {
                    // Exponential back-off: retry 1 → 2s, retry 2 → 4s, retry 3 → 8s.
                    val delayMs = retryState.incrementAndGetDelayMs()
                    retryState.setJob(scope.launch {
                        delay(delayMs)
                        adView.loadAd()
                    })
                } else {
                    // All retries exhausted — surface the failure to the caller.
                    onAdLoadFailed(error)
                }
            },
        )
    }

    DisposableEffect(adView) {
        adView.setDelegate(delegate)
        adView.loadAd()

        onDispose {
            retryState.reset()
            adView.setDelegate(null)
            adView.removeFromSuperview()
        }
    }

    return remember(adView, isAdReady, adaptiveHeightDp) {
        BannerAdState(
            adView,
            isAdReady,
            adaptiveHeightDp
        )
    }

}

// ---------------------------------------------------------------------------
// Private delegate — named BannerStateAdDelegate to avoid clashing with the
// file-private BannerAdDelegate in BannerAdView.ios.kt (same package).
// ---------------------------------------------------------------------------

private class BannerStateAdDelegate(
    private val onAdLoaded: (Float) -> Unit,
    private val onAdLoadFailed: (String) -> Unit,
) : NSObject(), MAAdViewAdDelegateProtocol {

    override fun didLoadAd(ad: MAAd) {
        // Get the adaptive banner height from the ad size after it loads.
        // This automatically returns ~50pt on iPhones, ~90pt on iPads.
        val adaptiveHeight = ad.size.useContents { height.toFloat() }
        onAdLoaded(adaptiveHeight)
    }

    override fun didFailToLoadAdForAdUnitIdentifier(
        adUnitIdentifier: String,
        withError: MAError,
    ) {
        onAdLoadFailed(withError.message)
    }

    override fun didClickAd(ad: MAAd) {}
    override fun didDisplayAd(ad: MAAd) {}
    override fun didFailToDisplayAd(ad: MAAd, withError: MAError) {}
    override fun didHideAd(ad: MAAd) {}
    override fun didExpandAd(ad: MAAd) {}
    override fun didCollapseAd(ad: MAAd) {}
}
