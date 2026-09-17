package com.venera.compose.feature

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class DetailRouteTest {
    private val route = DetailRoute("comic-a", "source-a")
    private val cached = ComicItem("comic-a", "Title", "Author", "cover", sourceName = "source-a")

    @Test
    fun `restored route loads identity without in-memory selection`() {
        val comic = resolveDetailComic(route, null)
        assertEquals(route.comicId, comic.id)
        assertEquals(route.sourceName, comic.sourceName)
        assertEquals("", comic.title)
    }

    @Test
    fun `matching selection keeps preview data`() {
        assertSame(cached, resolveDetailComic(route, cached))
    }

    @Test
    fun `another comic cannot replace route identity`() {
        val comic = resolveDetailComic(route, cached.copy(id = "comic-b"))
        assertEquals(route.comicId, comic.id)
        assertEquals("", comic.title)
    }

    @Test
    fun `same id from another source cannot replace route identity`() {
        val comic = resolveDetailComic(route, cached.copy(sourceName = "source-b"))
        assertEquals(route.sourceName, comic.sourceName)
        assertEquals("", comic.coverUrl)
    }
}
