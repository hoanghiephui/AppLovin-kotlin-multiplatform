package com.multiplatform.applovin.native

import kotlin.test.Test
import kotlin.test.assertEquals

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
}
