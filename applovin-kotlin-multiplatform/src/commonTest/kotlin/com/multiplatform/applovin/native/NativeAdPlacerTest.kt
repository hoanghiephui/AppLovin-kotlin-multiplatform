package com.multiplatform.applovin.native

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class NativeAdPlacerTest {
    @Test
    fun nativeAdSlotsNearViewportIncludesOnlySlotsWithinLookAheadWindow() {
        val slots = nativeAdSlotsNearViewport(
            adIndices = intArrayOf(2, 5, 8, 11),
            poolSize = 4,
            firstVisibleAdjustedIndex = 0,
            lastVisibleAdjustedIndex = 3,
            lookAheadItems = 1,
        )

        assertEquals(listOf(0), slots)
    }

    @Test
    fun nativeAdSlotsNearViewportMapsRecycledAdPositionsBackToPoolSlot() {
        val slots = nativeAdSlotsNearViewport(
            adIndices = intArrayOf(2, 5, 8, 11, 14),
            poolSize = 4,
            firstVisibleAdjustedIndex = 9,
            lastVisibleAdjustedIndex = 12,
            lookAheadItems = 2,
        )

        assertEquals(listOf(2, 3, 0), slots)
    }

    @Test
    fun nativeAdSlotsNearViewportReturnsEmptyListWhenPoolIsEmpty() {
        val slots = nativeAdSlotsNearViewport(
            adIndices = intArrayOf(2, 5, 8),
            poolSize = 0,
            firstVisibleAdjustedIndex = 0,
            lastVisibleAdjustedIndex = 10,
            lookAheadItems = 2,
        )

        assertEquals(emptyList(), slots)
    }

    @Test
    fun nativeAdReadyLayoutKeepsContentKeysUniqueWhenReadyAdsAreInserted() {
        val layout = nativeAdReadyLayout(
            adIndices = intArrayOf(2, 5, 8, 11),
            readyAdCount = 2,
            contentCount = 10,
        )

        val contentKeys = (0 until layout.itemCount)
            .filterNot(layout::isAdAt)
            .map { index -> "shelf_${layout.contentIndexFor(index)}" }

        assertEquals(12, layout.itemCount)
        assertEquals((0 until 10).map { "shelf_$it" }, contentKeys)
        assertFalse(contentKeys.groupingBy { it }.eachCount().any { it.value > 1 })
    }
}
