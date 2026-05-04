package com.multiplatform.applovin.ads

import com.multiplatform.applovin.banner.AdFormat
import com.multiplatform.applovin.utils.AdListener

expect class ApplovinAdView(
    adUnitId: String, adFormat: AdFormat
) {
    val adUnitId: String
    /** Initialises the underlying native ad view. Must be called before [loadAd]. */
    fun initialize()
    fun loadAd()
    fun startAutoRefresh()
    fun stopAutoRefresh()
    fun setListener(listener: AdListener)
    fun destroy()
}