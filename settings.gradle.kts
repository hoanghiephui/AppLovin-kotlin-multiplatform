pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.10.0"
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral {}
        maven("https://dl.google.com/dl/android/maven2/")
    }
}

rootProject.name = "applovin-kmp-root"
include(":applovin-kotlin-multiplatform")