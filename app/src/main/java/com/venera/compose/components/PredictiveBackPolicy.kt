package com.venera.compose.components

/** Finite clamping protects graphics layers from malformed vendor progress events. */
internal fun predictiveBackProgress(progress: Float): Float =
    if (progress.isFinite()) progress.coerceIn(0f, 1f) else 0f

/** A gesture may commit its original layer once; cancellation is terminal. */
internal class PredictiveBackCommit(private val startedKey: Any?) {
    private var finished = false

    fun cancel() { finished = true }

    fun tryCommit(enabled: Boolean, currentKey: Any?): Boolean {
        if (finished) return false
        finished = true
        return enabled && currentKey == startedKey
    }
}
