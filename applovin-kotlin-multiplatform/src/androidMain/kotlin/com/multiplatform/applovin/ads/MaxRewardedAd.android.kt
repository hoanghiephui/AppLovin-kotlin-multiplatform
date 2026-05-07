package com.multiplatform.applovin.ads

import com.applovin.mediation.MaxAd
import com.applovin.mediation.MaxError
import com.applovin.mediation.MaxReward
import com.applovin.mediation.MaxRewardedAdListener
import com.applovin.mediation.ads.MaxRewardedAd
import com.multiplatform.applovin.utils.AdListener
import com.multiplatform.applovin.utils.AdType

actual class ApplovinRewardedAd actual constructor(actual val adUnitId: String) {

    private var rewardedAds: MaxRewardedAd? = null
    private var currentListener: AdListener? = null
    private var rewardCallback: ((String, Int) -> Unit)? = null

    actual val isReady: Boolean
        get() = rewardedAds?.isReady ?: false

    actual fun initialize() {
        if (rewardedAds == null) {
            rewardedAds = MaxRewardedAd.getInstance(adUnitId)
        }
    }

    actual fun loadAd() {
        // Lazily initialize so callers do not need to call initialize() explicitly.
        initialize()
        rewardedAds?.loadAd()
    }

    actual fun showAd() {
        if (rewardedAds?.isReady == true) {
            @Suppress("DEPRECATION")
            rewardedAds?.showAd()
        }
    }

    /**
     * Shows the rewarded ad using the recommended Activity-based API.
     *
     * AppLovin MAX requires an [Activity] reference to display the ad overlay correctly.
     * Always prefer this overload over the no-arg [showAd] on Android.
     */
    fun showAd(activity: android.app.Activity) {
        if (rewardedAds?.isReady == true) {
            rewardedAds?.showAd(activity)
        }
    }

    actual fun setListener(listener: AdListener) {

        currentListener = listener

        rewardedAds?.setListener(object : MaxRewardedAdListener {
            override fun onAdLoaded(ad: MaxAd) {
                listener.onAdLoaded(adUnitId, AdType.REWARDED)
            }

            override fun onAdLoadFailed(adUnitId: String, error: MaxError) {
                listener.onAdLoadFailed(adUnitId, AdType.REWARDED, error.message)
            }

            override fun onAdDisplayed(ad: MaxAd) {
                listener.onAdDisplayed(adUnitId, AdType.REWARDED)
            }

            override fun onAdDisplayFailed(ad: MaxAd, error: MaxError) {
                listener.onAdDisplayFailed(adUnitId, AdType.REWARDED, error.message)
            }

            override fun onAdClicked(ad: MaxAd) {
                listener.onAdClicked(adUnitId, AdType.REWARDED)
            }

            override fun onAdHidden(ad: MaxAd) {
                listener.onAdHidden(adUnitId, AdType.REWARDED)
            }

            override fun onUserRewarded(ad: MaxAd, reward: MaxReward) {
                rewardCallback?.invoke(reward.label, reward.amount)
            }
        })
    }

    actual fun setRewardListener(onRewarded: (String, Int) -> Unit) {
        rewardCallback = onRewarded
    }

    actual fun destroy() {
        rewardedAds?.destroy()
        currentListener = null
        rewardCallback = null
    }
}