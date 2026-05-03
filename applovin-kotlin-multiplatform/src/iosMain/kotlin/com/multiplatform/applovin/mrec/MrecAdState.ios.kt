@file:OptIn(ExperimentalForeignApi::class)

package com.multiplatform.applovin.mrec

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
 * iOS implementation of [MrecAdState].
 *
 * @param nativeAdView the underlying [MAAdView]; `internal` so [MrecAdView] (same module)
 *   can pass it directly to [UIKitView] without an extra wrapper.
 * @param isAdReadyState mutable backing state for [isAdReady].
 * @param isTablet `true` when the ad was created with [MAAdFormat.leader] (tablet layout).
 */
actual class MrecAdState(
    internal val nativeAdView: MAAdView,
    private val isAdReadyState: MutableState<Boolean>,
    actual val isTablet: Boolean = false,
) {
    actual val isAdReady: Boolean get() = isAdReadyState.value
}

/**
 * iOS actual for [rememberMrecAd].
 *
 * Creates a [MAAdView] once with an explicit frame so [ALViewabilityTimer] does not log
 * a "0 area" error. [loadAd] is called inside [DisposableEffect] to pre-load the creative
 * before the list item is visible.
 *
 * When [isTablet] is `true` the view is created with [MAAdFormat.leader] and a
 * full-width × 90 pt frame (suited for tablets). Otherwise [MAAdFormat.mrec] with a
 * 300 × 250 pt frame is used.
 *
 * Note: [loadAd] fires before the view is embedded in the window hierarchy, so
 * [ALViewabilityTimer] may emit a single informational warning during the pre-load phase.
 * This is non-fatal; the ad loads correctly and [isAdReady] transitions to `true`
 * once [didLoadAd] is called.
 */
@Composable
actual fun rememberMrecAd(
    adUnitId: String,
    isTablet: Boolean,
    onAdLoaded: () -> Unit,
    onAdLoadFailed: (error: String) -> Unit,
): MrecAdState {
    val isAdReady = remember { mutableStateOf(false) }

    // Create the MAAdView once; it lives for the lifetime of the calling composable.
    val adView = remember(adUnitId) {
        val screenWidth = UIScreen.mainScreen.bounds.useContents { size.width }
        // Tablets use LEADER format (full-width × 90 pt); phones use MREC (300 × 250 pt).
        // Setting an explicit frame ensures ALViewabilityTimer sees a non-zero area even
        // before the view is embedded in the Compose UIKit hierarchy.
        if (isTablet) {
            MAAdView(adUnitId, MAAdFormat.leader()).apply {
                setFrame(CGRectMake(0.0, 0.0, screenWidth, 90.0))
                backgroundColor = UIColor.clearColor
            }
        } else {
            MAAdView(adUnitId, MAAdFormat.mrec()).apply {
                setFrame(CGRectMake(0.0, 0.0, minOf(screenWidth, 300.0), 250.0))
                backgroundColor = UIColor.clearColor
            }
        }
    }

    // Compose holds a strong reference to the delegate for the composable's lifetime,
    // preventing Kotlin/Native GC from collecting it before callbacks fire.
    // MAAdView.delegate is an ObjC `weak` property and does NOT retain the object.
    val delegate = remember(adView) {
        MrecAdDelegate(
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

    return remember(adView, isAdReady) { MrecAdState(adView, isAdReady, isTablet) }
}

// ---------------------------------------------------------------------------
// Private delegate
// ---------------------------------------------------------------------------

private class MrecAdDelegate(
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
