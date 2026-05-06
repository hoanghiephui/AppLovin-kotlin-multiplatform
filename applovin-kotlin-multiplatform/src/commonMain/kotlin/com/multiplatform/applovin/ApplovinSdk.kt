package com.multiplatform.applovin

import com.multiplatform.applovin.ads.ApplovinAdView
import com.multiplatform.applovin.ads.ApplovinInterstitialAd
import com.multiplatform.applovin.ads.ApplovinRewardedAd

expect class ApplovinSdk {

    /**
     * Initialize the AppLovin SDK
     * @param sdkKey Your AppLovin SDK key
     */
    fun initialize(
        sdkKey: String,
        userIdentifier: String,
        onInitialized: () -> Unit,
        debugMode: Boolean = false,
        testDeviceIds: List<String> = emptyList(),
        privacyPolicyUrl: String,
        termsOfServiceUrl: String,
        showTermsAndPrivacyAlertInGdpr: Boolean = false
    )

    /**
     * create a banner ad
     * @param adUnitId The ad unit ID
     */
    fun createBanner(adUnitId: String): ApplovinAdView

    /**
     * create a mrec ad
     * @param adUnitId The ad unit ID
     */
    fun createMrec(adUnitId: String): ApplovinAdView

    /**
     * create a leader ad
     * @param adUnitId The ad unit ID
     */
    fun createLeader(adUnitId: String): ApplovinAdView

    /**
     * create an interstitial ad
     * @param adUnitId The ad unit ID
     */
    fun createInterstitial(adUnitId: String): ApplovinInterstitialAd

    /**
     * create a rewarded ad
     * @param adUnitId The ad unit ID
     */
    fun createRewarded(adUnitId: String): ApplovinRewardedAd


    fun destroy()

}