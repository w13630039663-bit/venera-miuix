package com.venera.compose.feature

import com.venera.compose.source.model.Comment
import org.junit.Assert.*
import org.junit.Test

class DetailCommentStateTest {
    private val first = Comment(id = "1", userName = "Reader", content = "First")
    private val second = first.copy(id = "2", content = "Second")

    @Test fun refreshWithEmptyResultClearsOldComments() {
        val state = DetailCommentState(items = listOf(first), page = 3, maxPage = 10)
            .received(emptyList(), 1, null)
        assertTrue(state.items.isEmpty())
        assertFalse(state.hasMore)
        assertNull(state.maxPage)
        assertEquals(1, state.page)
    }

    @Test fun nextPageAppendsAndHonorsMaximum() {
        val state = DetailCommentState().received(listOf(first), 1, 2)
        assertTrue(state.hasMore)
        val next = state.received(listOf(second), 2, 2)
        assertEquals(listOf(first, second), next.items)
        assertFalse(next.hasMore)
    }

    @Test fun unknownMaximumStopsOnEmptyPageWithoutLosingPreviousItems() {
        val state = DetailCommentState().received(listOf(first), 1, null)
        assertTrue(state.hasMore)
        val next = state.received(emptyList(), 2, null)
        assertEquals(listOf(first), next.items)
        assertFalse(next.hasMore)
    }

    @Test fun failureDoesNotAdvanceCursorOrDiscardItems() {
        val state = DetailCommentState().received(listOf(first), 1, 3)
        val failed = state.copy(isLoading = false, error = "offline", requestedPage = 2)
        assertEquals(1, failed.page)
        assertEquals(listOf(first), failed.items)
        assertEquals(2, failed.requestedPage)
    }

    @Test fun refreshingClearsPreviousErrorAndPagination() {
        val state = DetailCommentState(items = listOf(first), error = "offline", page = 3, maxPage = 9)
            .received(listOf(second), 1, 1)
        assertEquals(listOf(second), state.items)
        assertNull(state.error)
        assertFalse(state.hasMore)
        assertTrue(state.loaded)
    }
}
