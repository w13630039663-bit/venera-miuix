package com.venera.compose.download

import com.venera.compose.source.model.ChapterGroup
import com.venera.compose.source.model.Comic
import com.venera.compose.source.model.ComicChapter
import com.venera.compose.source.model.ComicDetails
import org.junit.Assert.*
import org.junit.Test

class DownloadChapterSelectionTest {
    private val details = ComicDetails(Comic("gallery", "Gallery", sourceKey = "ehentai"))

    @Test fun unloadedDetailsDoNotCreateFakeChapter() {
        assertTrue(downloadChapters(null).isEmpty())
    }

    @Test fun galleryUsesExactlyTheReaderChapterId() {
        val chapter = downloadChapters(details).single()
        assertEquals("0", chapter.id)
        assertEquals(GALLERY_CHAPTER_ID, chapter.id)
        assertEquals(0, chapter.order)
    }

    @Test fun realChaptersRetainTheirSourceIdsAndOrder() {
        val chapters = listOf(ComicChapter("episode-2", "Two", 2), ComicChapter("episode-9", "Nine", 9))
        assertEquals(chapters, downloadChapters(details.copy(chapters = chapters)))
    }

    @Test fun selectedGroupDoesNotMixOtherGroups() {
        val first = ComicChapter("first", "First")
        val second = ComicChapter("second", "Second")
        val grouped = details.copy(chapterGroups = listOf(ChapterGroup("A", listOf(first)), ChapterGroup("B", listOf(second))))
        assertEquals(listOf(second), downloadChapters(grouped, 1))
        assertEquals(listOf(first), downloadChapters(grouped, 99))
    }

    @Test fun emptyGroupInChapteredComicIsNotGallery() {
        val grouped = details.copy(chapterGroups = listOf(ChapterGroup("Empty", emptyList()), ChapterGroup("Real", listOf(ComicChapter("ep", "Episode")))))
        assertTrue(downloadChapters(grouped).isEmpty())
    }
}
