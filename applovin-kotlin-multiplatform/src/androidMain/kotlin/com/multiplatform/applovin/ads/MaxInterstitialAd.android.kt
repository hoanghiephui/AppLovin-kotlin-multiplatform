package com.multiplatform.applovin.ads

import android.app.Activity
import com.applovin.mediation.MaxAd
import com.applovin.mediation.MaxAdListener
import com.applovin.mediation.MaxError
import com.applovin.mediation.ads.MaxInterstitialAd
import com.multiplatform.applovin.utils.AdListener
import com.multiplatform.applovin.utils.AdType

actual class ApplovinInterstitialAd actual constructor(actual val adUnitId: String) {

    private var interstitialAds: MaxInterstitialAd? = null
    private var currentListener: AdListener? = null

    actual fun initialize() {
        if (interstitialAds == null) {
            interstitialAds = MaxInterstitialAd(adUnitId)
        }
    }

    actual val isReady: Boolean
        get() = interstitialAds?.isReady ?: false

    actual fun loadAd() {
        // Lazily initialize so callers do not need to call initialize() explicitly.
        initialize()
        interstitialAds?.loadAd()
    }

    /**
     * Shows the interstitial using the deprecated no-Activity API.
     * Prefer [showAd(Activity)] which uses the non-deprecated API.
     */
    actual fun showAd() {
        if (interstitialAds?.isReady == true) {
            @Suppress("DEPRECATION")
            interstitialAds?.showAd()
        }
    }

    /**
     * Shows the interstitial using the recommended Activity-based API.
     *
     * AppLovin MAX requires an [Activity] reference to display the ad overlay correctly.
     * Always prefer this overload over the no-arg [showAd] on Android.
     */
    fun showAd(activity: Activity) {
        if (interstitialAds?.isReady == true) {
            interstitialAds?.showAd(activity)
        }
    }

    actual fun setListener(listener: AdListener) {

        currentListener = listener

        interstitialAds?.setListener(object : MaxAdListener {
            override fun onAdLoaded(ad: MaxAd) {
                listener.onAdLoaded(adUnitId, AdType.INTERSTITIAL)
            }

            override fun onAdLoadFailed(adUnitId: String, error: MaxError) {
                listener.onAdLoadFailed(adUnitId, AdType.INTERSTITIAL, error.message)
            }

            override fun onAdDisplayed(ad: MaxAd) {
                listener.onAdDisplayed(adUnitId, AdType.INTERSTITIAL)
            }

            override fun onAdDisplayFailed(ad: MaxAd, error: MaxError) {
                listener.onAdDisplayFailed(adUnitId, AdType.INTERSTITIAL, error.message)
            }

            override fun onAdClicked(ad: MaxAd) {
                listener.onAdClicked(adUnitId, AdType.INTERSTITIAL)
            }

            override fun onAdHidden(ad: MaxAd) {
                listener.onAdHidden(adUnitId, AdType.INTERSTITIAL)
            }
        })
    }

    actual fun destroy() {
        interstitialAds?.destroy()
        interstitialAds = null
        currentListener = null
    }
}