package com.multiplatform.applovin.ads

import com.multiplatform.applovin.utils.AdListener

expect class ApplovinRewardedAd(
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

    /**
     * Opaque string forwarded by MAX to your server-side reward callback (the `{CUSTOM_DATA}` macro).
     * Applies to the NEXT [showAd]; pass null to clear it.
     */
    fun setCustomData(customData: String?)
    fun setListener(listener: AdListener)
    fun setRewardListener(onRewarded: (reward: String, amount: Int) -> Unit)
    fun destroy()
}