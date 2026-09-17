package com.venera.compose.components

import org.junit.Assert.*
import org.junit.Test

class PredictiveBackPolicyTest {
    @Test fun realProgressIsNotEasedOrReplacedByATimer() {
        assertEquals(.37f, predictiveBackProgress(.37f), 0f)
        assertEquals(0f, predictiveBackProgress(-1f), 0f)
        assertEquals(1f, predictiveBackProgress(2f), 0f)
        assertEquals(0f, predictiveBackProgress(Float.NaN), 0f)
        assertEquals(0f, predictiveBackProgress(Float.POSITIVE_INFINITY), 0f)
    }

    @Test fun completedGestureCommitsExactlyOnce() {
        val gesture = PredictiveBackCommit("settings/reader")
        assertTrue(gesture.tryCommit(true, "settings/reader"))
        assertFalse(gesture.tryCommit(true, "settings/reader"))
    }

    @Test fun cancelledGestureNeverCommits() {
        val gesture = PredictiveBackCommit("category")
        gesture.cancel()
        assertFalse(gesture.tryCommit(true, "category"))
        assertTrue(PredictiveBackCommit("category").tryCommit(true, "category"))
    }

    @Test fun changedStackOrDismissedLayerCannotPopAnotherPage() {
        assertFalse(PredictiveBackCommit(listOf("home", "reader")).tryCommit(true, listOf("home")))
        assertFalse(PredictiveBackCommit("controls").tryCommit(false, "controls"))
    }
}
