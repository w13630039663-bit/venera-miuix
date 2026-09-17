package com.venera.compose.download

import com.venera.compose.source.model.ComicChapter
import com.venera.compose.source.model.ComicDetails

/** The Flutter reader uses eid "0" for a gallery without chapters. */
const val GALLERY_CHAPTER_ID = "0"

/** Do not confuse details still loading with a loaded chapterless gallery. */
fun downloadChapters(details: ComicDetails?, selectedGroupIndex: Int = 0): List<ComicChapter> {
    details ?: return emptyList()
    val chapters = if (details.chapterGroups.isNotEmpty()) {
        (details.chapterGroups.getOrNull(selectedGroupIndex) ?: details.chapterGroups.first()).chapters
    } else {
        details.chapters
    }
    if (chapters.isNotEmpty()) return chapters
    // An empty selected group in a chaptered work is not a gallery.
    if (details.chapters.isNotEmpty() || details.chapterGroups.any { it.chapters.isNotEmpty() }) {
        return emptyList()
    }
    return listOf(ComicChapter(id = GALLERY_CHAPTER_ID, title = "整本", order = 0))
}
