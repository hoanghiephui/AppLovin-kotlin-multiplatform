@file:OptIn(ExperimentalKotlinGradlePluginApi::class)

import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.compose.compiler)
}

group = "twix.watch"
version = "1.0.5"

kotlin {
    jvmToolchain(21)
    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
        freeCompilerArgs.add("-XXLanguage:+PropertyParamAnnotationDefaultTargetMode")
    }
    configure<com.android.build.api.dsl.KotlinMultiplatformAndroidLibraryTarget> {
        namespace = "twix.watch.applovin"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()
    }

    swiftPMDependencies {
        swiftPackage(
            url = url("https://github.com/AppLovin/AppLovin-MAX-Swift-Package.git"),
            version = exact("13.6.3"),
            products = listOf(product("AppLovinSDK")),
        )
        swiftPackage(
            url = url("https://github.com/AppLovin/AppLovin-MAX-Swift-Package-InMobi.git"),
            version = exact("11030000.0.0"),
            products = listOf(product("AppLovinMediationInMobiAdapter")),
        )
    }

    listOf(
        iosArm64(),
        iosSimulatorArm64()
    ).forEach {
        it.binaries.framework {
            baseName = "ApplovinMaxSDK"
            isStatic = true
        }
    }



    sourceSets {
        commonMain.dependencies {
            implementation(libs.kermit)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material)

        }

        commonTest.dependencies {
            implementation(kotlin("test"))
        }

        androidMain.dependencies {
            implementation(libs.lifecycle.process)
            implementation(libs.applovin.sdk)
            implementation(libs.google.user.messaging.platform)
            implementation(libs.services.ads.identifier)
            implementation("androidx.constraintlayout:constraintlayout:2.2.1")
            implementation("com.google.android.material:material:1.14.0")
            implementation("com.applovin.mediation:inmobi-adapter:11.3.0.1")
            //api("com.applovin.mediation:ironsource-adapter:9.4.3.0.0")
            implementation("com.applovin.mediation:yandex-adapter:8.2.0.0")
            implementation("com.applovin.mediation:unityads-adapter:4.19.0.0")
            implementation(libs.cronet.api)
        }
    }
}
