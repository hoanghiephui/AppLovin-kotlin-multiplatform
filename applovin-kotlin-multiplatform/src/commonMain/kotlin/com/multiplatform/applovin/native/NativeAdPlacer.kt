package com.multiplatform.applovin.native

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.first

/**
 * Number of native ad slots preloaded by [rememberNativeAdPlacer].
 *
 * Four slots cover the most common feed layouts without excessive memory usage:
 * - A vertical single-column feed typically shows ≤ 2 ads simultaneously.
 * - A 2-column grid ([LazyVerticalGrid]) shows at most 1 full-width ad at a time.
 * Four preloaded creatives ensure ads are ready before the user scrolls to them.
 */
private const val POOL_SIZE = 4

/**
 * Manages a fixed pool of preloaded native ads and calculates their insertion positions
 * in a feed ([LazyColumn] / [LazyVerticalGrid]).
 *
 * ### Position algorithm
 * Mirrors AppLovin's `MaxAdPlacer` logic:
 * 1. [fixedPositions] — explicit indices in the **combined** (content + ad) stream where
 *    ads appear first. Example: `[3]` → first ad occupies combined-stream index 3.
 * 2. [repeatingInterval] — after the last fixed position, an ad is inserted every N
 *    combined-stream slots. Must be ≥ 2.
 * 3. [maxAdCount] — hard cap on total ad insertions, further capped to [POOL_SIZE].
 *
 * ### Usage with LazyVerticalGrid
 * ```kotlin
 * val placer = rememberNativeAdPlacer(adUnitId = "...", adPlacement = "BrowseGrid")
 *
 * LazyVerticalGrid(columns = GridCells.Fixed(2)) {
 *     val totalSize = placer.adjustedSize(items.size)
 *
 *     items(
 *         count = totalSize,
 *         key = { i ->
 *             if (placer.isAdAt(i)) "ad_$i"
 *             else "content_${items[placer.contentIndexFor(i)].id}"
 *         },
 *         span = { i ->
 *             // Ads span the full row so each ad gets its own row.
 *             if (placer.isAdAt(i)) GridItemSpan(maxLineSpan) else GridItemSpan(1)
 *         },
 *     ) { i ->
 *         if (placer.isAdAt(i)) {
 *             // NativeAdSlot handles all three states automatically:
 *             //   loading  → shows a placeholder that reserves space (no layout shift)
 *             //   ready    → shows the actual ad creative
 *             //   failed   → animates to 0-height and disappears quietly
 *             NativeAdSlot(adState = placer.adStateAt(i))
 *         } else {
 *             ContentItem(items[placer.contentIndexFor(i)])
 *         }
 *     }
 * }
 * ```
 *
 * @param adPool Preloaded ad slots. Each element is a distinct [NativeAdState]; the index
 *   equals the ad-slot number (0-based).
 * @param fixedPositions Sorted list of combined-stream indices where the first ads appear.
 * @param repeatingInterval Combined-stream interval at which ads repeat after fixed positions.
 * @param maxAdCount Effective ad count (already capped to [POOL_SIZE] by [rememberNativeAdPlacer]).
 */
@Stable
class NativeAdPlacerState(
    val adPool: List<NativeAdState>,
    val fixedPositions: List<Int>,
    val repeatingInterval: Int,
    val maxAdCount: Int,
) {
    /**
     * Pre-calculated sorted array of combined-stream indices where ads appear.
     *
     * Mirrors AppLovin's `y2` internal position manager which builds a sorted position
     * list from fixedPositions then extends with repeating steps up to maxAdCount.
     *
     * Length ≤ min([maxAdCount], [adPool].size).
     *
     * Example with fixedPositions=[3], repeatingInterval=5, maxAdCount=4:
     * → [3, 8, 13, 18]
     *
     * Combined stream: C C C **A** C C C C **A** C C C C **A** …
     *                  0 1 2   3  4 5 6 7   8  9 …      13
     *
     * Using IntArray instead of List<Int> avoids boxing on every binary-search access.
     */
    private val adIndices: IntArray = buildList<Int> {
        // Phase 1: fixed positions — sorted and deduplicated, same as AppLovin's TreeSet
        // input via MaxAdPlacerSettings.getFixedPositions().
        for (pos in fixedPositions.sorted().distinct()) {
            if (size >= maxAdCount || size >= adPool.size) break
            add(pos)
        }
        // Phase 2: repeating positions after the last fixed position.
        // AppLovin SDK (MaxAdPlacerSettings.setRepeatingInterval): values < 2 are set to
        // 0 (disabled) and a warning is logged. When fixedPositions is empty AppLovin seeds
        // the first position as (repeatingInterval − 1), which equals (lastOrNull() ?: -1)
        // + repeatingInterval when the list is empty — behaviour is identical.
        if (repeatingInterval >= 2) {
            var next = (lastOrNull() ?: -1) + repeatingInterval
            while (size < maxAdCount && size < adPool.size) {
                add(next)
                next += repeatingInterval
            }
        }
    }.toIntArray()

    /**
     * O(1) set-membership check for ad positions.
     *
     * Mirrors AppLovin's `o4` dual ArrayList+HashSet structure: `contains` routes through
     * the HashSet for O(1) lookup rather than a linear scan of the position list.
     */
    private val adIndexSet: Set<Int> = adIndices.toHashSet()

    /**
     * O(1) reverse-lookup map: combined-stream index → pool slot index (0-based).
     *
     * Replaces the former `adIndices.indexOf(adjustedIndex)` O(n) call in [adStateAt].
     * Built once at construction time; size ≤ [adPool].size.
     */
    private val adIndexToSlot: Map<Int, Int> = HashMap<Int, Int>(adIndices.size * 2).also { map ->
        adIndices.forEachIndexed { slot, idx -> map[idx] = slot }
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Returns `true` when [adjustedIndex] (0-based index in the combined content+ad stream)
     * maps to an ad slot backed by a pool entry.
     *
     * O(1) — backed by [adIndexSet].
     */
    fun isAdAt(adjustedIndex: Int): Boolean = adjustedIndex in adIndexSet

    /**
     * Returns the [NativeAdState] for the ad at [adjustedIndex], or `null` if
     * [adjustedIndex] is not an ad position.
     *
     * Note: the returned state's [NativeAdState.isAdReady] may still be `false` if the
     * creative has not finished loading. Guard with `state?.isAdReady == true` before
     * calling [NativeAdView].
     *
     * O(1) — backed by [adIndexToSlot] HashMap, replacing the former O(n) `indexOf` call.
     */
    fun adStateAt(adjustedIndex: Int): NativeAdState? {
        val slot = adIndexToSlot[adjustedIndex] ?: return null
        return adPool.getOrNull(slot)
    }

    /**
     * Converts a combined-stream [adjustedIndex] to the original 0-based content index.
     *
     * Formula: `contentIndex = adjustedIndex − (number of ad positions ≤ adjustedIndex)`
     *
     * O(log n) binary search on the sorted [adIndices] array, replacing the former O(n)
     * `count { it < adjustedIndex }` linear scan called for every content item in the feed.
     *
     * Only call this when [isAdAt] returns `false`.
     */
    fun contentIndexFor(adjustedIndex: Int): Int {
        // IntArray.binarySearch is JVM-only, so we use a manual binary search that is
        // identical in semantics: returns the insertion point when the element is absent.
        // The insertion point equals the number of ad positions strictly less than
        // adjustedIndex, which is the offset to subtract to get the content index.
        var lo = 0
        var hi = adIndices.size
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            when {
                adIndices[mid] < adjustedIndex -> lo = mid + 1
                adIndices[mid] > adjustedIndex -> hi = mid
                else -> return adjustedIndex - mid  // found: slot `mid` is an ad, not content
            }
        }
        // Not found (expected for content items): `lo` is the count of ad slots
        // at positions strictly less than adjustedIndex.
        return adjustedIndex - lo
    }

    /**
     * Returns the total combined-stream size for a feed with [contentCount] content items.
     *
     * An ad at slot `i` (combined index = [adIndices][i]) is valid only when enough content
     * precedes it: `adIndices[i] − i < contentCount`. This prevents phantom ad slots
     * beyond the end of the content list.
     *
     * O(log n) binary search exploiting the monotonic property of `adIndices[i] − i`:
     * since positions are strictly increasing, `adIndices[i] − i` is non-decreasing, so
     * the first invalid slot can be found with a single binary search pass. Replaces the
     * former O(n) `mapIndexed.count` with implicit boxing.
     */
    fun adjustedSize(contentCount: Int): Int {
        // Find the first slot index where adIndices[i] - i >= contentCount.
        // All slots before that index are valid (adIndices[i] - i < contentCount).
        var lo = 0
        var hi = adIndices.size
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            // adIndices[mid] - mid is the number of content items preceding ad slot `mid`.
            if (adIndices[mid] - mid < contentCount) lo = mid + 1 else hi = mid
        }
        // `lo` is the count of valid ad slots.
        return contentCount + lo
    }

    /**
     * `true` if at least one ad in the pool has a ready creative.
     *
     * Useful for deciding whether to show the ad feed variant or a purely content feed.
     */
    val hasAnyAdReady: Boolean get() = adPool.any { it.isAdReady }

    /**
     * Calls [NativeAdState.refresh] on every slot in the pool.
     *
     * Invoke this when the host screen performs a pull-to-refresh so all preloaded ads
     * are refreshed alongside the content. Each slot resets its failure state and
     * re-initiates the load sequence.
     */
    fun refreshAll() = adPool.forEach { it.refresh() }

    /**
     * Number of ad slots that are currently visible in the feed while preserving slot order.
     *
     * Visibility is limited to the longest ready prefix from slot 0:
     * - slot 1 cannot appear before slot 0 is ready
     * - slot 2 cannot appear before slots 0 and 1 are ready
     *
     * This prevents position jumps (e.g. first visible ad suddenly starting at a later slot)
     * and avoids odd DirectoryCard rows before the first ad in 2-column grids.
     */
    private fun readyPrefixVisibleCount(contentCount: Int): Int {
        var visible = 0
        for (slot in adIndices.indices) {
            val state = adPool.getOrNull(slot) ?: break
            if (!state.isAdReady) break
            // Valid only when enough content precedes this slot.
            if (adIndices[slot] - slot >= contentCount) break
            visible++
        }
        return visible
    }

    // -------------------------------------------------------------------------
    // Ready-aware API (preferred for feed rendering)
    //
    // The base `adjustedSize` / `isAdAt` / `contentIndexFor` include ALL pre-computed
    // ad positions regardless of pool readiness. This causes invisible full-span grid
    // rows when a slot is still loading, breaking the LazyVerticalGrid row grouping.
    //
    // The `ready*` variants below only consider slots whose creative is ready.
    // Slots that have not yet loaded are simply absent from the combined stream —
    // matching the original single-ad behaviour where ads only appeared after load.
    // -------------------------------------------------------------------------

    /**
     * Returns `true` when [adjustedIndex] maps to an ad slot whose pool entry currently
     * has a ready creative. Use this instead of [isAdAt] when building feed item spans
     * and types to avoid invisible grid rows.
     *
     * Reads [NativeAdState.isAdReady] which is backed by snapshot state, so any
     * composable reading this function will recompose when a slot finishes loading.
     */
    fun isReadyAdAt(adjustedIndex: Int): Boolean {
        val slot = adIndexToSlot[adjustedIndex] ?: return false
        return slot < readyPrefixVisibleCount(Int.MAX_VALUE) && adPool.getOrNull(slot)?.isAdReady == true
    }

    /**
     * Like [adjustedSize] but counts only pool slots whose creative is currently ready.
     *
     * When no ads are ready the returned value equals [contentCount] (pure content stream).
     * As each sequential slot finishes loading the value grows by 1, inserting an ad
     * position into the combined stream at the pre-calculated index.
     *
     * **Approximation note**: the validity check `adIndices[slot] − slot < contentCount`
     * uses the original slot ordinal. This is exact for sequential loading (slots settle
     * 0→1→2→3) and is a safe approximation (off-by-1 near the content boundary) for
     * the unlikely case where an early slot fails and a later slot is ready first.
     */
    fun readyAdjustedSize(contentCount: Int): Int {
        return contentCount + readyPrefixVisibleCount(contentCount)
    }

    /**
     * Like [contentIndexFor] but only subtracts **ready** ad positions that appear before
     * [adjustedIndex] in the combined stream.
     *
     * Use alongside [readyAdjustedSize] and [isReadyAdAt]: non-ready slots are absent from
     * the combined stream so they must not reduce the content index for positions after them.
     *
     * O(n) where n ≤ pool size (≤ 4), so effectively O(1) in practice.
     *
     * Only call this when [isReadyAdAt] returns `false` for [adjustedIndex].
     */
    fun readyContentIndexFor(adjustedIndex: Int): Int {
        var readyAdsBeforeIndex = 0
        val visiblePrefixCount = readyPrefixVisibleCount(Int.MAX_VALUE)
        for ((slot, idx) in adIndices.withIndex()) {
            if (slot >= visiblePrefixCount) break
            if (idx >= adjustedIndex) break
            readyAdsBeforeIndex++
        }
        return adjustedIndex - readyAdsBeforeIndex
    }
}

/**
 * Creates and remembers a [NativeAdPlacerState] that pre-loads a pool of [POOL_SIZE]
 * native ads at the **screen level** (outside any `LazyColumn`/`LazyVerticalGrid`).
 *
 * ### Why screen-level preloading matters for revenue
 * - Ads are fetched before the user scrolls to them → zero latency when the slot becomes
 *   visible → higher impression rate.
 * - The same [NativeAdState] (and its underlying native view) is reused on scroll — no
 *   redundant network requests or view recreation.
 * - Lifecycle is tied to the screen, not to individual list items; AppLovin's viewability
 *   timer counts only when the view is on-screen.
 *
 * ### Compose rules
 * [rememberNativeAd] is called exactly [POOL_SIZE] times in fixed order — compliant with
 * the Compose composable call-order invariant. All [POOL_SIZE] slots start loading
 * immediately; slots beyond [maxAdCount] still hold a loader but their callbacks are
 * silenced.
 *
 * ### iOS note
 * On iOS each slot gets its own [MANativeAdLoader] and [MANativeAdView], matching the
 * Android pool structure. No shared state between slots.
 *
 * @param adUnitId AppLovin MAX ad unit ID for this native ad placement.
 * @param adPlacement Descriptive placement name used for AppLovin reporting (e.g. `"BrowseGrid"`).
 *   All slots share the same placement so impressions aggregate correctly in the dashboard.
 * @param fixedPositions Indices in the combined (content + ad) stream where ads appear first.
 *   Defaults to `[3]` — first ad after 3 content items.
 * @param repeatingInterval After the last fixed position, insert an ad every N combined-stream
 *   slots. Must be ≥ 2. Defaults to `5` (i.e., ad at every 5th position after the last fixed).
 * @param maxAdCount Hard cap on total ad slots. Values above [POOL_SIZE] are clamped to
 *   [POOL_SIZE] since the pool has a fixed size.
 * @param onAdLoaded Invoked on the main thread when a slot's ad finishes loading; receives
 *   the 0-based slot index.
 * @param onAdLoadFailed Invoked on the main thread when a slot exhausts all retries; receives
 *   the slot index and an error description.
 */
@Composable
fun rememberNativeAdPlacer(
    adUnitId: String,
    adPlacement: String,
    fixedPositions: List<Int> = listOf(3),
    repeatingInterval: Int = 5,
    maxAdCount: Int = 10,
    onAdLoaded: (slot: Int) -> Unit = {},
    onAdLoadFailed: (slot: Int, error: String) -> Unit = { _, _ -> },
): NativeAdPlacerState {
    // Effective pool size: capped so we never allocate more slots than needed.
    val effectiveMax = minOf(maxAdCount, POOL_SIZE)

    // All POOL_SIZE composable calls MUST appear unconditionally and in fixed order to
    // satisfy Compose's call-order invariant.
    //
    // Sequential loading strategy — mirrors MaxAdPlacer's internal z2 preloaded:
    //   slot 0 starts immediately (autoLoad = true).
    //   slots 1-3 start only after the previous slot settles (loaded OR permanently failed).
    //
    // This avoids firing N simultaneous requests to AppLovin servers, matching the SDK's
    // one-at-a-time queue behavior. The LaunchedEffect below observes each slot's
    // isAdReady / hasFailed state and calls startLoad() on the next slot in sequence.
    val slot0 = rememberNativeAd(
        adUnitId = adUnitId,
        adPlacement = adPlacement,
        autoLoad = true,
        onAdLoaded = { if (0 < effectiveMax) onAdLoaded(0) },
        onAdLoadFailed = { e -> if (0 < effectiveMax) onAdLoadFailed(0, e) },
    )
    val slot1 = rememberNativeAd(
        adUnitId = adUnitId,
        adPlacement = adPlacement,
        autoLoad = false,
        onAdLoaded = { if (1 < effectiveMax) onAdLoaded(1) },
        onAdLoadFailed = { e -> if (1 < effectiveMax) onAdLoadFailed(1, e) },
    )
    val slot2 = rememberNativeAd(
        adUnitId = adUnitId,
        adPlacement = adPlacement,
        autoLoad = false,
        onAdLoaded = { if (2 < effectiveMax) onAdLoaded(2) },
        onAdLoadFailed = { e -> if (2 < effectiveMax) onAdLoadFailed(2, e) },
    )
    val slot3 = rememberNativeAd(
        adUnitId = adUnitId,
        adPlacement = adPlacement,
        autoLoad = false,
        onAdLoaded = { if (3 < effectiveMax) onAdLoaded(3) },
        onAdLoadFailed = { e -> if (3 < effectiveMax) onAdLoadFailed(3, e) },
    )

    // Sequential load chain: each slot starts only after the previous one settles.
    // "Settled" = isAdReady (success) OR hasFailed (all retries exhausted).
    // This matches MaxAdPlacer's z2 preloaded which uses a single MaxNativeAdLoader
    // and enqueues the next loadAd() call inside onNativeAdLoaded.
    LaunchedEffect(adUnitId, adPlacement) {
        if (effectiveMax > 1) {
            snapshotFlow { slot0.isAdReady || slot0.hasFailed }.first { it }
            slot1.startLoad()
        }
        if (effectiveMax > 2) {
            snapshotFlow { slot1.isAdReady || slot1.hasFailed }.first { it }
            slot2.startLoad()
        }
        if (effectiveMax > 3) {
            snapshotFlow { slot2.isAdReady || slot2.hasFailed }.first { it }
            slot3.startLoad()
        }
    }

    // Build the pool list once. Invalidates only when adUnitId or adPlacement changes,
    // which also invalidates each rememberNativeAd's internal remember blocks — so the
    // pool always references the current NativeAdState instances.
    val pool = remember(adUnitId, adPlacement) {
        listOf(slot0, slot1, slot2, slot3).take(effectiveMax)
    }

    // NativeAdPlacerState is cheap to recreate and only rebuilt when position parameters
    // or the pool identity changes.
    return remember(adUnitId, adPlacement, fixedPositions, repeatingInterval, effectiveMax) {
        NativeAdPlacerState(
            adPool = pool,
            fixedPositions = fixedPositions,
            repeatingInterval = repeatingInterval,
            maxAdCount = effectiveMax,
        )
    }
}
