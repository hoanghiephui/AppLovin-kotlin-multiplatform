@file:OptIn(ExperimentalForeignApi::class)

package com.multiplatform.applovin.native

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import cocoapods.AppLovinSDK.MAAd
import cocoapods.AppLovinSDK.MAError
import cocoapods.AppLovinSDK.MANativeAdDelegateProtocol
import cocoapods.AppLovinSDK.MANativeAdLoader
import cocoapods.AppLovinSDK.MANativeAdView
import cocoapods.AppLovinSDK.MANativeAdViewBinder
import com.multiplatform.applovin.utils.AdRetryState
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import platform.UIKit.NSLayoutConstraint
import platform.UIKit.UIButton
import platform.UIKit.UIButtonTypeSystem
import platform.UIKit.UIColor
import platform.UIKit.UIFont
import platform.UIKit.UIImageView
import platform.UIKit.UILabel
import platform.UIKit.UIView
import platform.darwin.NSObject

// Integer view tags matching those used in the official AppLovin iOS sample (XIB-based).
// The MANativeAdViewBinder uses these tags to locate sub-views inside MANativeAdView
// so it can populate them with the loaded ad's assets.
private const val TAG_TITLE: Long = 1001
private const val TAG_ADVERTISER: Long = 1002
private const val TAG_BODY: Long = 1003
private const val TAG_ICON: Long = 1004
private const val TAG_OPTIONS: Long = 1005  // required — privacy / AdChoices icon
private const val TAG_MEDIA: Long = 1006
private const val TAG_CTA: Long = 1007
private const val TAG_STAR_RATING: Long = 1008

/**
 * Constructs a [MANativeAdView] with all sub-views tagged and positioned via programmatic
 * Auto Layout constraints. Called once per ad unit inside `remember { }` so it always
 * runs on the main composition thread.
 *
 * Layout:
 * ```
 * ┌──────────────────────────────────────────────────────┐
 * │ [Icon 48×48]  [Title bold 14pt]       [Options 20×20] │
 * │               [Advertiser 12pt]                       │
 * │       [Media — full width, 16:9 aspect ratio]         │
 * │ [Body — up to 2 lines, 12pt]                          │
 * │                      [CTA button 44×120 pt]           │
 * └──────────────────────────────────────────────────────┘
 * ```
 * Star-rating ([TAG_STAR_RATING]) is registered in the binder so AppLovin can populate it
 * when present, but is excluded from the constraint graph — its intrinsic size drives layout.
 */
private fun buildMANativeAdView(): MANativeAdView = MANativeAdView().apply {
    // Icon — square 48×48 pt, top-left.
    val iconView = UIImageView().apply {
        tag = TAG_ICON
        translatesAutoresizingMaskIntoConstraints = false
        clipsToBounds = true
    }
    // Options (AdChoices) — REQUIRED by AppLovin policy; rendered by the SDK.
    val optionsView = UIView().apply {
        tag = TAG_OPTIONS
        translatesAutoresizingMaskIntoConstraints = false
    }
    // Title — ad headline, bold 14pt, single line.
    val titleLabel = UILabel().apply {
        tag = TAG_TITLE
        translatesAutoresizingMaskIntoConstraints = false
        font = UIFont.boldSystemFontOfSize(14.0)
        numberOfLines = 1
    }
    // Advertiser — brand/company name, regular 12pt.
    val advertiserLabel = UILabel().apply {
        tag = TAG_ADVERTISER
        translatesAutoresizingMaskIntoConstraints = false
        font = UIFont.systemFontOfSize(12.0)
        numberOfLines = 1
    }
    // Media — main creative (image or video); AppLovin adds its own subview here.
    val mediaView = UIView().apply {
        tag = TAG_MEDIA
        translatesAutoresizingMaskIntoConstraints = false
        clipsToBounds = true
    }
    // Body — ad description, regular 12pt, up to 2 lines.
    val bodyLabel = UILabel().apply {
        tag = TAG_BODY
        translatesAutoresizingMaskIntoConstraints = false
        font = UIFont.systemFontOfSize(12.0)
        numberOfLines = 2
    }
    // CTA button — call-to-action text; AppLovin sets the title.
    val ctaButton = UIButton.buttonWithType(UIButtonTypeSystem).apply {
        tag = TAG_CTA
        translatesAutoresizingMaskIntoConstraints = false
    }

    // Add all sub-views before activating constraints.
    addSubview(iconView)
    addSubview(optionsView)
    addSubview(titleLabel)
    addSubview(advertiserLabel)
    addSubview(mediaView)
    addSubview(bodyLabel)
    addSubview(ctaButton)

    // Activate all constraints in a single batch for performance.
    // Sign convention: positive constant → away from the referenced edge (inward offset);
    //                  negative constant → toward the referenced edge (outward offset).
    NSLayoutConstraint.activateConstraints(
        listOf(
            // --- Options (AdChoices) — top-right corner, 20×20 pt ---
            optionsView.topAnchor.constraintEqualToAnchor(topAnchor, 4.0),
            optionsView.trailingAnchor.constraintEqualToAnchor(trailingAnchor, -4.0),
            optionsView.widthAnchor.constraintEqualToConstant(20.0),
            optionsView.heightAnchor.constraintEqualToConstant(20.0),

            // --- Icon — top-left, 48×48 pt ---
            iconView.topAnchor.constraintEqualToAnchor(topAnchor, 8.0),
            iconView.leadingAnchor.constraintEqualToAnchor(leadingAnchor, 8.0),
            iconView.widthAnchor.constraintEqualToConstant(48.0),
            iconView.heightAnchor.constraintEqualToConstant(48.0),

            // --- Title — right of icon, left of options ---
            titleLabel.topAnchor.constraintEqualToAnchor(topAnchor, 8.0),
            titleLabel.leadingAnchor.constraintEqualToAnchor(iconView.trailingAnchor, 8.0),
            titleLabel.trailingAnchor.constraintEqualToAnchor(optionsView.leadingAnchor, -4.0),

            // --- Advertiser — directly below title, same horizontal extent ---
            advertiserLabel.topAnchor.constraintEqualToAnchor(titleLabel.bottomAnchor, 2.0),
            advertiserLabel.leadingAnchor.constraintEqualToAnchor(titleLabel.leadingAnchor),
            advertiserLabel.trailingAnchor.constraintEqualToAnchor(titleLabel.trailingAnchor),

            // --- Media — full width below icon/text row, 16:9 aspect ratio ---
            mediaView.topAnchor.constraintEqualToAnchor(iconView.bottomAnchor, 8.0),
            mediaView.leadingAnchor.constraintEqualToAnchor(leadingAnchor),
            mediaView.trailingAnchor.constraintEqualToAnchor(trailingAnchor),
            mediaView.heightAnchor.constraintEqualToAnchor(mediaView.widthAnchor, multiplier = 9.0 / 16.0),

            // --- Body — below media, 8 pt horizontal inset ---
            bodyLabel.topAnchor.constraintEqualToAnchor(mediaView.bottomAnchor, 8.0),
            bodyLabel.leadingAnchor.constraintEqualToAnchor(leadingAnchor, 8.0),
            bodyLabel.trailingAnchor.constraintEqualToAnchor(trailingAnchor, -8.0),

            // --- CTA button — below body, trailing-aligned, 44 pt tall × 120 pt wide ---
            ctaButton.topAnchor.constraintEqualToAnchor(bodyLabel.bottomAnchor, 8.0),
            ctaButton.trailingAnchor.constraintEqualToAnchor(trailingAnchor, -8.0),
            ctaButton.widthAnchor.constraintEqualToConstant(120.0),
            ctaButton.heightAnchor.constraintEqualToConstant(44.0),
            ctaButton.bottomAnchor.constraintEqualToAnchor(bottomAnchor, -8.0),
        )
    )
}

/**
 * iOS implementation of [NativeAdState].
 *
 * @param nativeAdView the programmatically-constructed [MANativeAdView]; `internal` so
 *   [NativeAdView] (same module) can pass it directly to [UIKitView] without exposing
 *   platform types through the common API.
 * @param isAdReadyState mutable backing state for [isAdReady].
 * @param hasFailedState mutable backing state for [hasFailed]; set when all retries fail.
 * @param onRefresh lambda captured from [rememberNativeAd]'s composition scope; encapsulates
 *   all platform-specific reload logic (retry reset + [MANativeAdLoader.loadAdIntoAdView]).
 * @param onStartLoad lambda that fires the initial load; guarded internally against double-calls.
 */
actual class NativeAdState(
    internal val nativeAdView: MANativeAdView,
    private val isAdReadyState: MutableState<Boolean>,
    private val hasFailedState: MutableState<Boolean>,
    private val onRefresh: () -> Unit,
    private val onStartLoad: () -> Unit,
) {
    actual val isAdReady: Boolean get() = isAdReadyState.value

    /** `true` when all retry attempts are exhausted and no ad could be loaded. */
    actual val hasFailed: Boolean get() = hasFailedState.value

    /** Cancels any pending retry and fires a fresh ad load request. */
    actual fun refresh() = onRefresh()

    /**
     * Triggers the initial load for slots created with `autoLoad = false`.
     * No-op if loading has already started.
     */
    actual fun startLoad() = onStartLoad()
}

/**
 * iOS actual for [rememberNativeAd].
 *
 * Because AppLovin iOS does not provide a XIB-free template API in KMP, the [MANativeAdView]
 * is constructed **programmatically**: sub-views ([UILabel], [UIImageView], [UIButton], [UIView])
 * are added to the container and tagged with the integer constants above. The [MANativeAdViewBinder]
 * then uses those tags to locate and populate the views when the ad loads.
 *
 * Uses the **pre-rendered** flow: [MANativeAdLoader.loadAdIntoAdView] receives the fully
 * configured [MANativeAdView] and AppLovin populates all sub-views before calling
 * [MANativeAdDelegate.didLoadNativeAd].
 *
 * ### Delegate GC prevention
 * [MANativeAdLoader.nativeAdDelegate] is an ObjC `weak` property and does NOT retain the
 * delegate object. A strong Kotlin reference is kept in `remember { }` for the composable's
 * lifetime so Kotlin/Native's garbage collector does not collect the delegate before callbacks
 * fire — identical pattern to `BannerAdView.ios.kt`.
 */
@Composable
actual fun rememberNativeAd(
    adUnitId: String,
    adPlacement: String,
    autoLoad: Boolean,
    onAdLoaded: () -> Unit,
    onAdLoadFailed: (error: String) -> Unit,
    onAdClicked: () -> Unit,
    onAdRevenuePaid: () -> Unit,
    onAdRetrying: (attempt: Int, delayMs: Long) -> Unit,
): NativeAdState {
    val isAdReady = remember { mutableStateOf(false) }
    // Observable flag set when all retry attempts are exhausted so the sequential
    // load chain in rememberNativeAdPlacer can advance to the next slot.
    val hasFailed = remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    // Non-observable retry holder — mutations do NOT trigger recomposition.
    val retryState = remember { AdRetryState() }

    // Build MANativeAdView once — sub-views are tagged and positioned via Auto Layout so
    // MANativeAdViewBinder can populate each slot when the ad loads.
    val nativeAdView = remember(adUnitId) { buildMANativeAdView() }

    // Configure the binder: maps integer tags to native ad asset slots.
    val binder = remember(adUnitId) {
        MANativeAdViewBinder { builder ->
            builder?.titleLabelTag = TAG_TITLE
            builder?.advertiserLabelTag = TAG_ADVERTISER
            builder?.bodyLabelTag = TAG_BODY
            builder?.iconImageViewTag = TAG_ICON
            builder?.optionsContentViewTag = TAG_OPTIONS  // required — privacy icon
            builder?.mediaContentViewTag = TAG_MEDIA
            builder?.callToActionButtonTag = TAG_CTA
            // TAG_STAR_RATING intentionally omitted from constraint graph; AppLovin
            // populates it via intrinsic size when a star-rating asset is present.
            builder?.starRatingContentViewTag = TAG_STAR_RATING
        }
    }

    // Bind the view hierarchy to the binder so AppLovin knows which sub-view to populate
    // for each asset slot when the ad loads.
    remember(nativeAdView, binder) {
        nativeAdView.bindViewsWithAdViewBinder(binder)
        true // dummy non-Unit return to satisfy remembers signature
    }

    // Holds the MAAd returned by didLoadNativeAd — passed to destroyAd() on dispose/reload.
    // Uses holder pattern so the DisposableEffect closure captures the object reference,
    // not a copy of the var value (same pattern as Android).
    val loadedAdHolder = remember { object { var ad: MAAd? = null } }

    val loader = remember(adUnitId, adPlacement) {
        MANativeAdLoader(adUnitId).apply {
            setPlacement(adPlacement)
        }
    }

    // Compose holds a strong reference to the delegate for the composable's lifetime,
    // preventing Kotlin/Native GC from collecting it before callbacks fire.
    // MANativeAdLoader.nativeAdDelegate is an ObjC `weak` property and does NOT retain.
    val delegate = remember(loader, adPlacement) {
        NativeAdDelegate(
            onAdLoaded = { ad: MAAd ->
                // Destroy the previous MAAd before storing the new one to prevent memory leaks.
                loadedAdHolder.ad?.let { loader.destroyAd(it) }
                loadedAdHolder.ad = ad
                retryState.reset()
                isAdReady.value = true
                onAdLoaded()
            },
            onAdLoadFailed = { error ->
                if (retryState.canRetry) {
                    // Exponential back-off: attempt 1 → 2 s, 2 → 4 s, 3 → 8 s.
                    val delayMs = retryState.incrementAndGetDelayMs()
                    onAdRetrying(retryState.count, delayMs)
                    retryState.setJob(scope.launch {
                        delay(delayMs)
                        loader.loadAdIntoAdView(nativeAdView)
                    })
                } else {
                    // All retries exhausted — surface the failure to the caller.
                    hasFailed.value = true
                    onAdLoadFailed(error)
                }
            },
            onAdClicked = onAdClicked,
        )
    }

    // Attach delegate and fire initial load only when autoLoad is requested.
    // When autoLoad = false the caller (e.g. rememberNativeAdPlacer) is responsible
    // for invoking startLoad() to implement sequential loading.
    val hasLoadStarted = remember { mutableStateOf(autoLoad) }
    DisposableEffect(adUnitId, adPlacement) {
        loader.setNativeAdDelegate(delegate)
        if (autoLoad) loader.loadAdIntoAdView(nativeAdView)
        onDispose {
            retryState.reset()
            loadedAdHolder.ad?.let { loader.destroyAd(it) }
            loader.setNativeAdDelegate(null)
        }
    }

    // Key on adUnitId + adPlacement so onRefresh always captures the current loader
    // (loader is recreated when adPlacement changes but nativeAdView is not).
    return remember(adUnitId, adPlacement) {
        NativeAdState(
            nativeAdView = nativeAdView,
            isAdReadyState = isAdReady,
            hasFailedState = hasFailed,
            onRefresh = {
                retryState.reset()
                isAdReady.value = false
                hasFailed.value = false
                hasLoadStarted.value = true
                loader.loadAdIntoAdView(nativeAdView)
            },
            onStartLoad = {
                // Guard: only start once; subsequent calls are no-ops.
                if (!hasLoadStarted.value) {
                    hasLoadStarted.value = true
                    loader.loadAdIntoAdView(nativeAdView)
                }
            },
        )
    }
}

/**
 * ObjC-compatible delegate that bridges [MANativeAdDelegate] callbacks to Kotlin lambdas.
 *
 * The strong reference to this object must be held in `remember { }` at the call site so
 * Kotlin/Native's GC does not collect it before callbacks fire (MANativeAdLoader.nativeAdDelegate
 * is a `weak` ObjC property).
 */
private class NativeAdDelegate(
    private val onAdLoaded: (MAAd) -> Unit,
    private val onAdLoadFailed: (String) -> Unit,
    private val onAdClicked: () -> Unit,
) : NSObject(), MANativeAdDelegateProtocol {

    override fun didLoadNativeAd(
        nativeAdView: MANativeAdView?,
        forAd: MAAd
    ) {
        // forAd is the MAAd object needed for destroyAd().
        // nativeAdView is the same MANativeAdView passed to loadAdIntoAdView — AppLovin
        // has already populated its subviews; pass forAd so the caller can call destroyAd.
        onAdLoaded(forAd)
    }

    override fun didFailToLoadNativeAdForAdUnitIdentifier(
        adUnitIdentifier: String,
        withError: MAError,
    ) {
        onAdLoadFailed(withError.message)
    }

    override fun didClickNativeAd(ad: MAAd) {
        onAdClicked()
    }
}
