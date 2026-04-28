@file:OptIn(ExperimentalForeignApi::class)

package com.multiplatform.applovin.banner

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import cocoapods.AppLovinSDK.MAAd
import cocoapods.AppLovinSDK.MAAdFormat
import cocoapods.AppLovinSDK.MAAdView
import cocoapods.AppLovinSDK.MAAdViewAdDelegateProtocol
import cocoapods.AppLovinSDK.MAError
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import platform.CoreGraphics.CGRectMake
import platform.UIKit.UIColor
import platform.UIKit.UIScreen
import platform.darwin.NSObject

/**
 * iOS implementation of [BannerAdState].
 *
 * @param nativeAdView   the underlying [MAAdView]; `internal` so [BannerAdStateView]
 *   (same module) can pass it directly to [UIKitView] without an extra wrapper.
 * @param isAdReadyState mutable backing state for [isAdReady].
 */
actual class BannerAdState(
    internal val nativeAdView: MAAdView,
    private val isAdReadyState: MutableState<Boolean>,
) {
    actual val isAdReady: Boolean get() = isAdReadyState.value
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

    // Create the MAAdView once; it lives for the lifetime of the calling composable.
    val adView = remember(adUnitId) {
        val screenWidth = UIScreen.mainScreen.bounds.useContents { size.width }
        MAAdView(adUnitId, MAAdFormat.banner()).apply {
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
            onAdLoaded = {
                isAdReady.value = true
                onAdLoaded()
            },
            onAdLoadFailed = { error ->
                // isAdReady stays false — no empty layout slot will appear.
                onAdLoadFailed(error)
            },
        )
    }

    DisposableEffect(adView) {
        adView.setDelegate(delegate)
        adView.loadAd()

        onDispose {
            adView.setDelegate(null)
            adView.removeFromSuperview()
        }
    }

    return remember(adView, isAdReady) { BannerAdState(adView, isAdReady) }
}

// ---------------------------------------------------------------------------
// Private delegate — named BannerStateAdDelegate to avoid clashing with the
// file-private BannerAdDelegate in BannerAdView.ios.kt (same package).
// ---------------------------------------------------------------------------

private class BannerStateAdDelegate(
    private val onAdLoaded: () -> Unit,
    private val onAdLoadFailed: (String) -> Unit,
) : NSObject(), MAAdViewAdDelegateProtocol {

    override fun didLoadAd(ad: MAAd) {
        onAdLoaded()
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
