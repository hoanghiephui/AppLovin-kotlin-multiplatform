package com.multiplatform.applovin

import android.content.Context
import androidx.core.net.toUri
import com.applovin.sdk.AppLovinMediationProvider
import com.applovin.sdk.AppLovinSdk
import com.applovin.sdk.AppLovinSdkConfiguration
import com.applovin.sdk.AppLovinSdkInitializationConfiguration
import com.multiplatform.applovin.ads.ApplovinAdView
import com.multiplatform.applovin.ads.ApplovinInterstitialAd
import com.multiplatform.applovin.ads.ApplovinRewardedAd
import com.multiplatform.applovin.banner.AdFormat

actual class ApplovinSdk {

    private var context: Context? = null

    fun setContext(context: Context) {
        this.context = context
    }

    actual fun initialize(
        sdkKey: String,
        userIdentifier: String,
        onInitialized: () -> Unit,
        debugMode: Boolean,
        testDeviceIds: List<String>,
        privacyPolicyUrl: String,
        termsOfServiceUrl: String,
        showTermsAndPrivacyAlertInGdpr: Boolean
    ) {
        context ?: throw IllegalStateException("Context not set. Call setContext() first")
        val initConfig = AppLovinSdkInitializationConfiguration.builder(sdkKey)
            .setMediationProvider(AppLovinMediationProvider.MAX)
            .setTestDeviceAdvertisingIds(testDeviceIds)
            .build()

        AppLovinSdk.getInstance(context).apply {
            settings.apply {
                //settings.userIdentifier = userIdentifier
                setVerboseLogging(debugMode)
                isCreativeDebuggerEnabled = debugMode
                termsAndPrivacyPolicyFlowSettings.apply {
                    isEnabled = true
                    privacyPolicyUri = privacyPolicyUrl.toUri()
                    termsOfServiceUri = termsOfServiceUrl.toUri()
                    setShowTermsAndPrivacyPolicyAlertInGdpr(showTermsAndPrivacyAlertInGdpr)
                    if (debugMode) {
                        debugUserGeography = AppLovinSdkConfiguration.ConsentFlowUserGeography.GDPR
                    }
                }
            }

            initialize(initConfig) {
                onInitialized()
            }
        }

    }

    actual fun createBanner(adUnitId: String): ApplovinAdView {
        return ApplovinAdView(adUnitId, AdFormat.BANNER).apply {
            initialize()
        }
    }

    actual fun createMrec(adUnitId: String): ApplovinAdView {
        return ApplovinAdView(adUnitId, AdFormat.MREC).apply {
            initialize()
        }
    }

    actual fun createLeader(adUnitId: String): ApplovinAdView {
        return ApplovinAdView(adUnitId, AdFormat.LEADER).apply {
            initialize()
        }
    }

    actual fun createInterstitial(adUnitId: String): ApplovinInterstitialAd {
        return ApplovinInterstitialAd(adUnitId).apply {
            initialize()
        }
    }

    actual fun createRewarded(adUnitId: String): ApplovinRewardedAd {
        return ApplovinRewardedAd(adUnitId).apply {
            initialize()
        }
    }

    actual fun destroy() {
    }
}