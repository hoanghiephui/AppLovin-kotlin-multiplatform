package com.multiplatform.applovin.ads

import com.applovin.mediation.MaxAd
import com.applovin.mediation.MaxAdFormat
import com.applovin.mediation.MaxAdViewAdListener
import com.applovin.mediation.MaxError
import com.applovin.mediation.ads.MaxAdView
import com.multiplatform.applovin.banner.AdFormat
import com.multiplatform.applovin.utils.AdListener
import com.multiplatform.applovin.utils.AdType

actual class ApplovinAdView actual constructor(
    actual val adUnitId: String,
    val adFormat: AdFormat
) {
    private var adView: MaxAdView? = null
    private var currentListener: AdListener? = null
    private val applovinAdFormat = when (adFormat) {
        AdFormat.BANNER -> MaxAdFormat.BANNER
        AdFormat.MREC -> MaxAdFormat.MREC
        AdFormat.LEADER -> MaxAdFormat.LEADER
    }

    actual fun initialize() {
        if (adView == null) {
            adView = MaxAdView(adUnitId, applovinAdFormat)
        }
    }

    actual fun loadAd() {
        adView?.loadAd()
            ?: throw IllegalStateException("Ad not initialized. SDK must be initialized first.")
    }

    actual fun startAutoRefresh() {
        adView?.startAutoRefresh()
    }

    actual fun stopAutoRefresh() {
        adView?.stopAutoRefresh()
    }

    actual fun setListener(listener: AdListener) {
        currentListener = listener

        adView?.setListener(object : MaxAdViewAdListener {
            override fun onAdLoaded(ad: MaxAd) {
                listener.onAdLoaded(adUnitId, AdType.BANNER)
            }

            override fun onAdLoadFailed(adUnitId: String, error: MaxError) {
                listener.onAdLoadFailed(adUnitId, AdType.BANNER, error.message)
            }

            override fun onAdDisplayed(ad: MaxAd) {
                listener.onAdDisplayed(adUnitId, AdType.BANNER)
            }

            override fun onAdDisplayFailed(ad: MaxAd, error: MaxError) {
                listener.onAdDisplayFailed(adUnitId, AdType.BANNER, error.message)
            }

            override fun onAdClicked(ad: MaxAd) {
                listener.onAdClicked(adUnitId, AdType.BANNER)
            }

            override fun onAdHidden(ad: MaxAd) {
                listener.onAdHidden(adUnitId, AdType.BANNER)
            }

            override fun onAdExpanded(ad: MaxAd) {}
            override fun onAdCollapsed(ad: MaxAd) {}
        })
    }

    actual fun destroy() {
        adView?.destroy()
        adView = null
        currentListener = null
    }

}