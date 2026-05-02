@file:OptIn(ExperimentalForeignApi::class)

package com.multiplatform.applovin.ads

import cocoapods.AppLovinSDK.MAAd
import cocoapods.AppLovinSDK.MAAdDelegateProtocol
import cocoapods.AppLovinSDK.MAError
import cocoapods.AppLovinSDK.MAInterstitialAd
import com.multiplatform.applovin.utils.AdListener
import com.multiplatform.applovin.utils.AdType
import kotlinx.cinterop.ExperimentalForeignApi
import platform.darwin.NSObject

actual class ApplovinInterstitialAd actual constructor(
    actual val adUnitId: String
) {

    private var nativeAd: MAInterstitialAd? = null
    private var delegate: InterstitialAdDelegate? = null

    actual val isReady: Boolean
        get() = nativeAd?.isReady() ?: false

    actual fun initialize() {
        if (nativeAd == null) {
            nativeAd = MAInterstitialAd(adUnitId)
        }
    }

    actual fun loadAd() {
        // Lazily initialize so callers do not need to call initialize() explicitly.
        initialize()
        nativeAd?.loadAd()
    }

    actual fun showAd() {
        if (nativeAd?.isReady() == true) {
            nativeAd?.showAd()
        }
    }

    actual fun setListener(listener: AdListener) {
        delegate = InterstitialAdDelegate(adUnitId, listener)
        nativeAd?.setDelegate(delegate)
    }

    actual fun destroy() {
        nativeAd = null
        delegate = null
    }
}

private class InterstitialAdDelegate(
    private val adUnitId: String,
    private val listener: AdListener
) : NSObject(), MAAdDelegateProtocol {

    override fun didLoadAd(ad: MAAd) {
        listener.onAdLoaded(adUnitId, AdType.INTERSTITIAL)
    }

    override fun didFailToLoadAdForAdUnitIdentifier(
        adUnitIdentifier: String,
        withError: MAError
    ) {
        listener.onAdLoadFailed(adUnitId, AdType.INTERSTITIAL, withError.message)
    }

    override fun didDisplayAd(ad: MAAd) {
        listener.onAdDisplayed(adUnitId, AdType.INTERSTITIAL)
    }

    override fun didFailToDisplayAd(ad: MAAd, withError: MAError) {
        listener.onAdDisplayFailed(adUnitId, AdType.INTERSTITIAL, withError.message)
    }

    override fun didClickAd(ad: MAAd) {
        listener.onAdClicked(adUnitId, AdType.INTERSTITIAL)
    }

    override fun didHideAd(ad: MAAd) {
        listener.onAdHidden(adUnitId, AdType.INTERSTITIAL)
    }
}