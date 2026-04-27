Pod::Spec.new do |spec|
    spec.name                     = 'applovin_kotlin_multiplatform'
    spec.version                  = '1.0.0'
    spec.homepage                 = 'https://github.com/Aditya-gupta99/AppLovin-kotlin-multiplatform'
    spec.source                   = { :http=> ''}
    spec.authors                  = ''
    spec.license                  = ''
    spec.summary                  = 'AppLovin MAX SDK wrapper for Kotlin Multiplatform'
    spec.vendored_frameworks      = 'build/cocoapods/framework/applovin_kotlin_multiplatform.framework'
    spec.libraries                = 'c++'
    spec.ios.deployment_target    = '15.0'
    spec.dependency 'AppLovinSDK', '13.6.2'
    if !Dir.exist?('build/cocoapods/framework/applovin_kotlin_multiplatform.framework') || Dir.empty?('build/cocoapods/framework/applovin_kotlin_multiplatform.framework')
        raise "
        Kotlin framework 'applovin_kotlin_multiplatform' doesn't exist yet, so a proper Xcode project can't be generated.
        'pod install' should be executed after running ':generateDummyFramework' Gradle task:
            ./gradlew :applovin-kotlin-multiplatform:generateDummyFramework
        Alternatively, proper pod installation is performed during Gradle sync in the IDE (if Podfile location is set)"
    end
    spec.xcconfig = {
        'ENABLE_USER_SCRIPT_SANDBOXING' => 'NO',
    }
    spec.pod_target_xcconfig = {
        'KOTLIN_PROJECT_PATH' => ':applovin-kotlin-multiplatform',
        'PRODUCT_MODULE_NAME' => 'applovin_kotlin_multiplatform',
    }
    spec.script_phases = [
        {
            :name => 'Build applovin_kotlin_multiplatform',
            :execution_position => :before_compile,
            :shell_path => '/bin/sh',
            :script => <<-SCRIPT
                if [ "YES" = "$OVERRIDE_KOTLIN_BUILD_IDE_SUPPORTED" ]; then
                    echo "Skipping Gradle build task invocation due to OVERRIDE_KOTLIN_BUILD_IDE_SUPPORTED environment variable set to \"YES\""
                    exit 0
                fi
                set -ev
                REPO_ROOT="$PODS_TARGET_SRCROOT"
                "$REPO_ROOT/../gradlew" -p "$REPO_ROOT" $KOTLIN_PROJECT_PATH:syncFramework \
                    -Pkotlin.native.cocoapods.platform=$PLATFORM_NAME \
                    -Pkotlin.native.cocoapods.archs="$ARCHS" \
                    -Pkotlin.native.cocoapods.configuration="$CONFIGURATION"
            SCRIPT
        }
    ]
end
