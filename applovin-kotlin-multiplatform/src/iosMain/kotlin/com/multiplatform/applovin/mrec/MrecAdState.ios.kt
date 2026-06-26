@file:OptIn(ExperimentalForeignApi::class)

package com.multiplatform.applovin.mrec

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import co.touchlab.kermit.Logger
import swiftPMImport.twix.watch.applovin.kotlin.multiplatform.MAAd
import swiftPMImport.twix.watch.applovin.kotlin.multiplatform.MAAdFormat
import swiftPMImport.twix.watch.applovin.kotlin.multiplatform.MAAdView
import swiftPMImport.twix.watch.applovin.kotlin.multiplatform.MAAdViewAdDelegateProtocol
import swiftPMImport.twix.watch.applovin.kotlin.multiplatform.MAAdViewAdaptiveType
import swiftPMImport.twix.watch.applovin.kotlin.multiplatform.MAAdViewConfiguration
import swiftPMImport.twix.watch.applovin.kotlin.multiplatform.MAError
import com.multiplatform.applovin.utils.AdRetryState
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import platform.CoreGraphics.CGRectMake
import platform.UIKit.UIColor
import platform.UIKit.UIScreen
import platform.darwin.NSObject
import kotlin.time.Duration.Companion.milliseconds

/**
 * iOS implementation of [MrecAdState].
 *
 * @param nativeAdView the underlying [MAAdView]; `internal` so [MrecAdView] (same module)
 *   can pass it directly to [UIKitView] without an extra wrapper.
 * @param isAdReadyState mutable backing state for [isAdReady].
 * @param isTablet `true` when the ad was created with [MAAdFormat.leader] (tablet layout).
 */
@Immutable
actual class MrecAdState(
    internal val nativeAdView: MAAdView,
    private val isAdReadyState: MutableState<Boolean>,
    private val hasFailedState: MutableState<Boolean>,
    actual val isTablet: Boolean = false,
    private val onRefresh: () -> Unit,
    private val onStartLoad: () -> Unit,
) {
    actual val isAdReady: Boolean get() = isAdReadyState.value

    /** `true` when all retry attempts are exhausted and no ad could be loaded. */
    actual val hasFailed: Boolean get() = hasFailedState.value

    /** Cancels any pending retry and fires a fresh ad load request. */
    actual fun refresh() = onRefresh()

    /**
     * Triggers the initial ad load for slots created with `autoLoad = false`.
     * No-op if loading has already started (guarded by [onStartLoad]'s internal flag).
     */
    actual fun startLoad() = onStartLoad()
}

/**
 * iOS actual for [rememberMrecAd].
 *
 * Creates a [MAAdView] once with an explicit frame so [ALViewabilityTimer] does not log
 * a "0 area" error. [loadAd] is called inside [DisposableEffect] to preload the creative
 * before the list item is visible.
 *
 * When [isTablet] is `true` the view is created with [MAAdFormat.leader] and a
 * full-width × 90 pt frame (suited for tablets). Otherwise [MAAdFormat.mrec] with a
 * 300 × 250 pt frame is used.
 *
 * Note: [loadAd] fires before the view is embedded in the window hierarchy, so
 * [ALViewabilityTimer] may emit a single informational warning during the preload phase.
 * This is non-fatal; the ad loads correctly and [isAdReady] transitions to `true`
 * once [didLoadAd] is called.
 */
@Composable
actual fun rememberMrecAd(
    adUnitId: String,
    isTablet: Boolean,
    adPlacement: String,
    autoLoad: Boolean,
    onAdLoaded: () -> Unit,
    onAdLoadFailed: (error: String) -> Unit,
): MrecAdState {
    val isAdReady = remember { mutableStateOf(false) }
    val hasFailed = remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    // Non-observable retry holder — does not trigger recomposition on mutation.
    val retryState = remember { AdRetryState() }

    // Build Inline Adaptive MREC config before creating the view.
    // Set a custom width, in points, for the inline adaptive MREC. Otherwise, stretch to screen width.
    val screenWidth = UIScreen.mainScreen.bounds.useContents { size.width }
    // Ensure width is at least 300 points for MREC to avoid "smaller than required adaptive size" errors.
    val adaptiveWidth = maxOf(300.0, screenWidth)

    // Set a maximum height, in points, for the inline adaptive MREC. Otherwise, use standard MREC height of 250 points.
    val height = 300.0

    val config = MAAdViewConfiguration.configurationWithBuilderBlock { builder ->
        builder?.setAdaptiveType(MAAdViewAdaptiveType.MAAdViewAdaptiveTypeInline)
        builder?.setAdaptiveWidth(adaptiveWidth)
        builder?.setInlineMaximumHeight(height)
    }

    // Create the MAAdView once; it lives for the lifetime of the calling composable.
    val adView = remember(adUnitId, adPlacement) {
        // Tablets use LEADER format (full-width × 90 pt); phones use MREC (300 × 250 pt).
        // Setting an explicit frame ensures ALViewabilityTimer sees a non-zero area even
        // before the view is embedded in the Compose UIKit hierarchy.
        Logger.d { "Creating MAAdView for placement '$adPlacement' with format ${if (isTablet) "LEADER" else "MREC"} at width $screenWidth" }
        if (isTablet) {
            MAAdView(adUnitId, MAAdFormat.leader()).apply {
                setFrame(CGRectMake(0.0, 0.0, adaptiveWidth, 90.0))
                backgroundColor = UIColor.clearColor
            }
        } else {
            MAAdView(adUnitId, MAAdFormat.mrec(), config).apply {
                setFrame(
                    CGRectMake(
                        0.0, 0.0,
                        adaptiveWidth,
                        height
                    )
                )
                backgroundColor = UIColor.clearColor
            }
        }
    }

    // Compose holds a strong reference to the delegate for the composable's lifetime,
    // preventing Kotlin/Native GC from collecting it before callbacks fire.
    // MAAdView.delegate is an ObjC `weak` property and does NOT retain the object.
    val delegate = remember(adView, adPlacement) {
        MrecAdDelegate(
            onAdLoaded = {
                retryState.reset()
                isAdReady.value = true
                hasFailed.value = false
                onAdLoaded()
            },
            onAdLoadFailed = { error ->
                if (retryState.canRetry) {
                    // Exponential back-off: retry 1 → 2s, retry 2 → 4s, retry 3 → 8s.
                    val delayMs = retryState.incrementAndGetDelayMs()
                    retryState.setJob(scope.launch {
                        delay(delayMs.milliseconds)
                        adView.loadAd()
                    })
                } else {
                    // All retries exhausted — surface the failure to the caller.
                    hasFailed.value = true
                    onAdLoadFailed(error)
                }
                isAdReady.value = false
            },
        )
    }

    // Fire the first load only when autoLoad is requested. When autoLoad = false the
    // caller is responsible for invoking startLoad() at the right time.
    // A hasLoadStarted flag prevents startLoad() from firing a second time.
    val hasLoadStarted = remember { mutableStateOf(autoLoad) }
    DisposableEffect(adUnitId, adPlacement) {
        adView.setDelegate(delegate)
        adView.setPlacement(adPlacement)
        if (autoLoad) adView.loadAd()

        onDispose {
            adView.setExtraParameterForKey("allow_pause_auto_refresh_immediately", "true")
            adView.stopAutoRefresh()
            retryState.reset()
            adView.setDelegate(null)  // nullify delegate — any in-flight auto-refresh callbacks become no-ops
            adView.removeFromSuperview()
        }
    }

    return remember(adUnitId, adPlacement) {
        MrecAdState(
            nativeAdView = adView,
            isAdReadyState = isAdReady,
            hasFailedState = hasFailed,
            isTablet = isTablet,
            onRefresh = {
                retryState.reset()
                isAdReady.value = false
                hasFailed.value = false
                hasLoadStarted.value = true
                adView.loadAd()
            },
            onStartLoad = {
                // Guard: only start once; subsequent calls are no-ops.
                if (!hasLoadStarted.value) {
                    hasLoadStarted.value = true
                    adView.loadAd()
                }
            }
        )
    }
}

// ---------------------------------------------------------------------------
// Private delegate
// ---------------------------------------------------------------------------

private class MrecAdDelegate(
    private val onAdLoaded: () -> Unit,
    private val onAdLoadFailed: (String) -> Unit,
) : NSObject(), MAAdViewAdDelegateProtocol {

    override fun didLoadAd(ad: MAAd) {
        onAdLoaded()
    }

    override fun didFailToLoadAdForAdUnitIdentifier(
        adUnitIdentifier: String,
        withError: MAError,
    ) {
        onAdLoadFailed(withError.message)
    }

    override fun didClickAd(ad: MAAd) {}
    override fun didDisplayAd(ad: MAAd) {}
    override fun didFailToDisplayAd(ad: MAAd, withError: MAError) {}
    override fun didHideAd(ad: MAAd) {}
    override fun didExpandAd(ad: MAAd) {}
    override fun didCollapseAd(ad: MAAd) {}
}
