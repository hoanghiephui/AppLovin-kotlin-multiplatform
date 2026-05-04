@file:OptIn(ExperimentalForeignApi::class)

package com.multiplatform.applovin.banner

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.UIKitView
import cocoapods.AppLovinSDK.MAAd
import cocoapods.AppLovinSDK.MAAdFormat
import cocoapods.AppLovinSDK.MAAdView
import cocoapods.AppLovinSDK.MAAdViewAdDelegateProtocol
import cocoapods.AppLovinSDK.MAError
import com.multiplatform.applovin.utils.BannerSize
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import platform.CoreGraphics.CGRectMake
import platform.UIKit.UIColor
import platform.UIKit.UIScreen
import platform.darwin.NSObject

@Composable
actual fun BannerAdView(
    adUnitId: String,
    stopAutoRefresh: Boolean,
    bannerSize: BannerSize,
    modifier: Modifier,
    onAdLoaded: () -> Unit,
    onAdLoadFailed: (String) -> Unit,
    onAdClicked: () -> Unit
) {
    var adView by remember { mutableStateOf<MAAdView?>(null) }
    // Guard so loadAd() is called only once, after the view is in the window hierarchy.
    var adLoaded by remember { mutableStateOf(false) }
    // Tracks whether the ad creative is ready to display.
    var adVisible by remember { mutableStateOf(false) }

    // Full dimensions when the ad is visible.
    val sizeMod = when (bannerSize) {
        BannerSize.BANNER -> Modifier.fillMaxWidth().height(50.dp)
        BannerSize.LEADER -> Modifier.fillMaxWidth().height(90.dp)
        BannerSize.MREC   -> Modifier.width(300.dp).height(250.dp)
    }

    // Before the ad loads: collapse to 0×0 so no empty space appears in the layout.
    // UIKitView still exists in composition (enabling loadAd() via the update block),
    // but occupies no screen area. ALViewabilityTimer may emit a single informational
    // warning about 0 visible area during this loading phase — this is non-fatal.
    // After load: switch to the proper ad dimensions.
    val effectiveMod = if (adVisible) modifier.then(sizeMod) else modifier.size(0.dp)

    DisposableEffect(adUnitId) {
        onDispose {
            adView?.removeFromSuperview()
            adView?.delegate = null
            adView = null
        }
    }

    // Compose holds a strong reference to the delegate for the composable's lifetime,
    // preventing Kotlin/Native GC from collecting it before callbacks fire.
    // MAAdView.delegate is an ObjC `weak` property and does NOT retain the object.
    val bannerDelegate = remember(adUnitId) {
        BannerAdDelegate(
            onAdLoaded = {
                adVisible = true
                onAdLoaded()
            },
            onAdLoadFailed = { error ->
                // Leave adVisible = false so the 0×0 modifier stays in effect.
                onAdLoadFailed(error)
            },
            onAdClicked = onAdClicked,
        )
    }

    UIKitView(
        factory = {
            val adFormat = when (bannerSize) {
                BannerSize.BANNER -> MAAdFormat.banner()
                BannerSize.LEADER -> MAAdFormat.leader()
                BannerSize.MREC -> MAAdFormat.mrec()
            }

            // Set an explicit frame so ALViewabilityTimer sees a non-zero area once
            // the view is embedded in the window hierarchy (via the update block below).
            val screenWidth = UIScreen.mainScreen.bounds.useContents { size.width }
            val (w, h) = when (bannerSize) {
                BannerSize.BANNER -> screenWidth to 50.0
                BannerSize.LEADER -> screenWidth to 90.0
                BannerSize.MREC   -> 300.0 to 250.0
            }

            MAAdView(adUnitId, adFormat).apply {
                setFrame(CGRectMake(0.0, 0.0, w, h))
                // backgroundColor must be set for banners to be fully functional (per AppLovin docs).
                backgroundColor = UIColor.clearColor
                adView = this
                setDelegate(bannerDelegate)

                if (stopAutoRefresh) {
                    setExtraParameterForKey(
                        "allow_pause_auto_refresh_immediately",
                        "true"
                    )
                    stopAutoRefresh()
                }

                // loadAd() is intentionally NOT called here — the view is not yet in
                // the window hierarchy, so ALViewabilityTimer would see area = 0.
                // It is called in the update block below, after Compose has embedded
                // the view into the UIKit hierarchy.
            }
        },
        update = { view ->
            // Called after the view is attached to the Compose UIKit hierarchy.
            if (!adLoaded) {
                adLoaded = true
                view.loadAd()
            }
        },
        modifier = effectiveMod,
    )
}

private class BannerAdDelegate(
    private val onAdLoaded: () -> Unit,
    private val onAdLoadFailed: (String) -> Unit,
    private val onAdClicked: () -> Unit
) : NSObject(), MAAdViewAdDelegateProtocol {

    override fun didLoadAd(ad: MAAd) {
        onAdLoaded()
    }

    override fun didFailToLoadAdForAdUnitIdentifier(
        adUnitIdentifier: String,
        withError: MAError
    ) {
        onAdLoadFailed(withError.message)
    }

    override fun didClickAd(ad: MAAd) {
        onAdClicked()
    }

    override fun didDisplayAd(ad: MAAd) {}
    override fun didFailToDisplayAd(ad: MAAd, withError: MAError) {}
    override fun didHideAd(ad: MAAd) {}
    override fun didExpandAd(ad: MAAd) {}
    override fun didCollapseAd(ad: MAAd) {}
}
