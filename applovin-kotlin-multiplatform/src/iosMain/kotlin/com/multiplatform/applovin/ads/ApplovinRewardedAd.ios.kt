@file:OptIn(ExperimentalForeignApi::class)

package com.multiplatform.applovin.ads

import cocoapods.AppLovinSDK.MAAd
import cocoapods.AppLovinSDK.MAError
import cocoapods.AppLovinSDK.MAReward
import cocoapods.AppLovinSDK.MARewardedAd
import cocoapods.AppLovinSDK.MARewardedAdDelegateProtocol
import com.multiplatform.applovin.utils.AdListener
import com.multiplatform.applovin.utils.AdType
import kotlinx.cinterop.ExperimentalForeignApi
import platform.darwin.NSObject

actual class ApplovinRewardedAd actual constructor(
    actual val adUnitId: String
) {

    private var nativeAd: MARewardedAd? = null
    private var delegate: RewardedAdDelegate? = null
    private var rewardCallback: ((String, Int) -> Unit)? = null

    actual val isReady: Boolean
        get() = nativeAd?.isReady() ?: false

    internal fun initialize() {
        if (nativeAd == null) {
            nativeAd = MARewardedAd.sharedWithAdUnitIdentifier(adUnitId)
        }
    }

    actual fun loadAd() {
        nativeAd?.loadAd()
            ?: throw IllegalStateException("Ad not initialized. SDK must be initialized first.")
    }

    actual fun showAd() {
        if (nativeAd?.isReady() == true) {
            nativeAd?.showAd()
        }
    }

    actual fun setListener(listener: AdListener) {
        delegate = RewardedAdDelegate(adUnitId, listener) { reward, amount ->
            rewardCallback?.invoke(reward, amount)
        }
        nativeAd?.setDelegate(delegate)
    }

    actual fun setRewardListener(onRewarded: (String, Int) -> Unit) {
        rewardCallback = onRewarded
    }

    actual fun destroy() {
        nativeAd = null
        delegate = null
        rewardCallback = null
    }
}

private class RewardedAdDelegate(
    private val adUnitId: String,
    private val listener: AdListener,
    private val onRewarded: (String, Int) -> Unit
) : NSObject(), MARewardedAdDelegateProtocol {

    override fun didLoadAd(ad: MAAd) {
        listener.onAdLoaded(adUnitId, AdType.REWARDED)
    }

    override fun didFailToLoadAdForAdUnitIdentifier(
        adUnitIdentifier: String,
        withError: MAError
    ) {
        listener.onAdLoadFailed(adUnitId, AdType.REWARDED, withError.message)
    }

    override fun didDisplayAd(ad: MAAd) {
        listener.onAdDisplayed(adUnitId, AdType.REWARDED)
    }

    override fun didFailToDisplayAd(ad: MAAd, withError: MAError) {
        listener.onAdDisplayFailed(adUnitId, AdType.REWARDED, withError.message)
    }

    override fun didClickAd(ad: MAAd) {
        listener.onAdClicked(adUnitId, AdType.REWARDED)
    }

    override fun didHideAd(ad: MAAd) {
        listener.onAdHidden(adUnitId, AdType.REWARDED)
    }

    override fun didRewardUserForAd(ad: MAAd, withReward: MAReward) {
        val rewardLabel = withReward.label
        val rewardAmount = withReward.amount.toInt()
        onRewarded(rewardLabel, rewardAmount)
    }
}