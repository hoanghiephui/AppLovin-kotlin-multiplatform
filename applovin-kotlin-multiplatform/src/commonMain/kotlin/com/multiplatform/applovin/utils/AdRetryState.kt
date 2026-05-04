package com.multiplatform.applovin.utils

import kotlinx.coroutines.Job

/**
 * Lightweight, non-observable holder for ad-load retry state.
 *
 * Intentionally NOT a Compose [State] — mutations do NOT trigger recomposition.
 * Should be created with [androidx.compose.runtime.remember] at the call site so it
 * survives recomposition but is disposed when the composable leaves the tree.
 *
 * @param maxRetries Maximum number of consecutive retry attempts before the failure is
 *   surfaced to the caller's [onAdLoadFailed] callback.
 */
internal class AdRetryState(val maxRetries: Int = 3) {

    /** Number of consecutive failed load attempts since the last [reset]. */
    var count: Int = 0
        private set

    /** Handle for the currently-scheduled retry coroutine, if any. */
    var job: Job? = null
        private set

    /** `true` when another retry attempt is still allowed. */
    val canRetry: Boolean get() = count < maxRetries

    /**
     * Increments the retry counter and returns the exponential back-off delay in
     * milliseconds for this attempt:
     * - attempt 1 → 2 000 ms
     * - attempt 2 → 4 000 ms
     * - attempt 3 → 8 000 ms
     */
    fun incrementAndGetDelayMs(): Long {
        count++
        return (1L shl count) * 1_000L
    }

    /** Stores the newly-launched retry [job] so it can be cancelled by [reset]. */
    fun setJob(job: Job) {
        this.job = job
    }

    /**
     * Cancels any pending retry coroutine and resets the counter back to zero.
     * Call this on a successful ad load or when the composable is disposed.
     */
    fun reset() {
        job?.cancel()
        job = null
        count = 0
    }
}
