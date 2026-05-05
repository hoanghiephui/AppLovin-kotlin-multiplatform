plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlinCocoapods)
}

group = "io.github.aditya-gupta99"
version = "1.0.4"

kotlin {
    jvmToolchain(21)
    compilerOptions { freeCompilerArgs.add("-Xexpect-actual-classes") }
    android {
        namespace = "com.aditya.gupta99.applovin"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()
    }

    cocoapods {
        summary = "AppLovin MAX SDK wrapper for Kotlin Multiplatform"
        homepage = "https://github.com/Aditya-gupta99/AppLovin-kotlin-multiplatform"
        version = "1.0.0"
        ios.deploymentTarget = "15.0"

        framework {
            baseName = "applovin_kotlin_multiplatform"
        }

        pod("AppLovinSDK") {
            version = "13.6.2"
        }
    }

    listOf(
        iosX64(),
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
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material)

        }

        androidMain.dependencies {
            implementation(libs.lifecycle.process)
            api(libs.applovin.sdk)
            api(libs.services.ads.identifier)
            api("androidx.constraintlayout:constraintlayout:2.2.1")
            api("com.google.android.material:material:1.14.0-rc01")
            //api(libs.applovin.mediation.google)
        }
    }
}