@file:OptIn(ExperimentalForeignApi::class)

package com.multiplatform.applovin

import cocoapods.AppLovinSDK.ALMediationProviderMAX
import cocoapods.AppLovinSDK.ALSdk
import cocoapods.AppLovinSDK.ALSdkInitializationConfiguration
import com.multiplatform.applovin.ads.ApplovinAdView
import com.multiplatform.applovin.ads.ApplovinInterstitialAd
import com.multiplatform.applovin.ads.ApplovinRewardedAd
import com.multiplatform.applovin.banner.AdFormat
import kotlinx.cinterop.ExperimentalForeignApi

actual class ApplovinSdk {

    actual fun initialize(
        sdkKey: String,
        userIdentifier: String,
        onInitialized: () -> Unit,
        debugMode: Boolean,
        testDeviceIds: List<String>
    ) {
        // Create the initialization configuration with mediationProvider set to MAX
        val initConfig = ALSdkInitializationConfiguration
            .builderWithSdkKey(sdkKey)
            .apply {
                mediationProvider = ALMediationProviderMAX
                setTestDeviceAdvertisingIdentifiers(testDeviceIds)
            }
            .build()

        // Configure the SDK settings before SDK initialization
        val settings = ALSdk.shared().settings
        settings.userIdentifier = userIdentifier
        settings.verboseLoggingEnabled = debugMode
        settings.setCreativeDebuggerEnabled(debugMode)

        // Initialize the SDK with the configuration
        ALSdk.shared().initializeWithConfiguration(
            initializationConfiguration = initConfig,
            completionHandler = {
                onInitialized()
            }
        )
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