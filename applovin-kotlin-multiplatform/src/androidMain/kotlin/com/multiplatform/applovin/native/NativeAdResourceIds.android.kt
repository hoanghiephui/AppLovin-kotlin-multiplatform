package com.multiplatform.applovin.native

import androidx.annotation.IdRes
import androidx.annotation.LayoutRes
import androidx.annotation.MainThread

/**
 * Compile-time resource IDs required by MAX NativeAd binder and ad view styling.
 *
 * Host apps should provide these IDs during application startup so runtime code never
 * needs to call Resources.getIdentifier(), which is vulnerable to native crashes when
 * reached from background threads.
 */
data class NativeAdResourceIds(
    @LayoutRes val layoutId: Int,
    @IdRes val titleTextViewId: Int,
    @IdRes val bodyTextViewId: Int,
    @IdRes val advertiserTextViewId: Int,
    @IdRes val iconImageViewId: Int,
    @IdRes val mediaContentViewGroupId: Int,
    @IdRes val optionsContentViewGroupId: Int,
    @IdRes val starRatingContentViewGroupId: Int,
    @IdRes val callToActionButtonId: Int,
)

private object NativeAdResourceRegistry {
    @Volatile
    private var ids: NativeAdResourceIds? = null

    @MainThread
    fun configure(resourceIds: NativeAdResourceIds) {
        ids = resourceIds
    }

    fun requireIds(): NativeAdResourceIds = checkNotNull(ids) {
        "Native ad resource IDs are not configured. Call configureNativeAdResourceIds(...) during Application.onCreate()."
    }
}

/**
 * Configures compile-time resource IDs used by Android NativeAd rendering.
 * configureNativeAdResourceIds(
 *             NativeAdResourceIds(
 *                 layoutId = R.layout.max_native_ad_view,
 *                 titleTextViewId = R.id.title_text_view,
 *                 bodyTextViewId = R.id.body_text_view,
 *                 advertiserTextViewId = R.id.advertiser_text_view,
 *                 iconImageViewId = R.id.icon_image_view,
 *                 mediaContentViewGroupId = R.id.media_view_container,
 *                 optionsContentViewGroupId = R.id.options_view,
 *                 starRatingContentViewGroupId = R.id.star_rating_view,
 *                 callToActionButtonId = R.id.cta_button,
 *             )
 *         )
 */
@MainThread
fun configureNativeAdResourceIds(resourceIds: NativeAdResourceIds) {
    NativeAdResourceRegistry.configure(resourceIds)
}

internal fun requireNativeAdResourceIds(): NativeAdResourceIds =
    NativeAdResourceRegistry.requireIds()
