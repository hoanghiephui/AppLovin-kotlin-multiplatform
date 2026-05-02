package com.multiplatform.applovin.ads

import com.multiplatform.applovin.utils.AdListener

expect class ApplovinInterstitialAd(
    adUnitId: String
) {
    val adUnitId: String
    val isReady: Boolean
    /**
     * Creates the underlying platform ad object. Safe to call multiple times — guarded
     * internally by a null-check so only the first call allocates resources.
     */
    fun initialize()
    fun loadAd()
    fun showAd()
    fun setListener(listener: AdListener)
    fun destroy()
}