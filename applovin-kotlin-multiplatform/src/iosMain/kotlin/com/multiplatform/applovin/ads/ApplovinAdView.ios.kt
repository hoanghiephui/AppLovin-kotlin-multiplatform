@file:OptIn(ExperimentalForeignApi::class)

package com.multiplatform.applovin.ads

import cocoapods.AppLovinSDK.MAAd
import cocoapods.AppLovinSDK.MAAdFormat
import cocoapods.AppLovinSDK.MAAdView
import cocoapods.AppLovinSDK.MAAdViewAdDelegateProtocol
import cocoapods.AppLovinSDK.MAError
import com.multiplatform.applovin.banner.AdFormat
import com.multiplatform.applovin.utils.AdListener
import com.multiplatform.applovin.utils.AdType
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import platform.CoreGraphics.CGRectMake
import platform.UIKit.UIColor
import platform.UIKit.UIScreen
import platform.darwin.NSObject

actual class ApplovinAdView actual constructor(
    actual val adUnitId: String,
    val adFormat: AdFormat
) {
    private var nativeAdView: MAAdView? = null
    private var delegate: BannerAdDelegate? = null

    internal fun initialize(): MAAdView {
        if (nativeAdView == null) {
            val iosAdFormat = when (adFormat) {
                AdFormat.BANNER -> MAAdFormat.banner()
                AdFormat.MREC -> MAAdFormat.mrec()
                AdFormat.LEADER -> MAAdFormat.leader()
            }
            // Set an explicit initial frame so ALViewabilityTimer sees a non-zero area.
            val screenWidth = UIScreen.mainScreen.bounds.useContents { size.width }
            val (w, h) = when (adFormat) {
                AdFormat.BANNER -> screenWidth to 50.0
                AdFormat.LEADER -> screenWidth to 90.0
                AdFormat.MREC   -> 300.0 to 250.0
            }
            nativeAdView = MAAdView(adUnitId, iosAdFormat).apply {
                setFrame(CGRectMake(0.0, 0.0, w, h))
                // backgroundColor must be set for banners to be fully functional (per AppLovin docs).
                backgroundColor = UIColor.clearColor
            }
        }
        return nativeAdView!!
    }

    actual fun loadAd() {
        nativeAdView?.loadAd()
            ?: throw IllegalStateException("Ad not initialized. SDK must be initialized first.")
    }

    actual fun startAutoRefresh() {
        nativeAdView?.startAutoRefresh()
    }

    actual fun stopAutoRefresh() {
        nativeAdView?.stopAutoRefresh()
    }

    actual fun setListener(listener: AdListener) {
        delegate = BannerAdDelegate(adUnitId, listener, getAdType())
        nativeAdView?.setDelegate(delegate)
    }

    actual fun destroy() {
//        nativeAdView?.destroy()
        nativeAdView = null
        delegate = null
    }

    private fun getAdType(): AdType {
        return when (adFormat) {
            AdFormat.BANNER -> AdType.BANNER
            AdFormat.MREC -> AdType.MREC
            AdFormat.LEADER -> AdType.BANNER
        }
    }
}

private class BannerAdDelegate(
    private val adUnitId: String,
    private val listener: AdListener,
    private val adType: AdType
) : NSObject(), MAAdViewAdDelegateProtocol {

    override fun didLoadAd(ad: MAAd) {
        listener.onAdLoaded(adUnitId, adType)
    }

    override fun didFailToLoadAdForAdUnitIdentifier(
        adUnitIdentifier: String,
        withError: MAError
    ) {
        listener.onAdLoadFailed(adUnitId, adType, withError.message)
    }

    override fun didDisplayAd(ad: MAAd) {
        listener.onAdDisplayed(adUnitId, adType)
    }

    override fun didFailToDisplayAd(ad: MAAd, withError: MAError) {
        listener.onAdDisplayFailed(adUnitId, adType, withError.message)
    }

    override fun didClickAd(ad: MAAd) {
        listener.onAdClicked(adUnitId, adType)
    }

    override fun didHideAd(ad: MAAd) {
        listener.onAdHidden(adUnitId, adType)
    }

    override fun didExpandAd(ad: MAAd) {}
    override fun didCollapseAd(ad: MAAd) {}
}