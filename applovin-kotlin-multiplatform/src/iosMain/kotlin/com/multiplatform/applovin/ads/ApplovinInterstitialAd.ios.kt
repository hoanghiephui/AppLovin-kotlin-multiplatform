@file:OptIn(ExperimentalForeignApi::class)

package com.multiplatform.applovin.ads

import swiftPMImport.twix.watch.applovin.kotlin.multiplatform.MAAd
import swiftPMImport.twix.watch.applovin.kotlin.multiplatform.MAAdDelegateProtocol
import swiftPMImport.twix.watch.applovin.kotlin.multiplatform.MAError
import swiftPMImport.twix.watch.applovin.kotlin.multiplatform.MAInterstitialAd
import com.multiplatform.applovin.utils.AdListener
import com.multiplatform.applovin.utils.AdType
import kotlinx.cinterop.ExperimentalForeignApi
import platform.darwin.NSObject

actual class ApplovinInterstitialAd actual constructor(
    actual val adUnitId: String
) {

    private var interstitialAd: MAInterstitialAd? = null
    private var delegate: InterstitialAdDelegate? = null

    actual val isReady: Boolean
        get() = interstitialAd?.isReady() ?: false

    actual fun initialize() {
        if (interstitialAd == null) {
            interstitialAd = MAInterstitialAd(adUnitId)
            delegate?.let { interstitialAd?.setDelegate(it) }
        }
    }

    actual fun loadAd() {
        // Lazily initialize so callers do not need to call initialize() explicitly.
        initialize()
        interstitialAd?.loadAd()
    }

    actual fun showAd() {
        if (interstitialAd?.isReady() == true) {
            interstitialAd?.showAd()
        }
    }

    actual fun setListener(listener: AdListener) {
        delegate = InterstitialAdDelegate(adUnitId, listener)
        interstitialAd?.setDelegate(delegate)
    }

    actual fun destroy() {
        interstitialAd = null
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