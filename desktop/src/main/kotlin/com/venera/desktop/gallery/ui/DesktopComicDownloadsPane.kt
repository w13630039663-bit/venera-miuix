package com.venera.desktop.gallery.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import com.venera.desktop.gallery.data.DesktopComicDownloadedChapter
import com.venera.desktop.gallery.data.DesktopComicDownloadedGroup
import com.venera.desktop.gallery.data.desktopComicChaptersByComic
import com.venera.desktop.gallery.data.desktopComicDownloadedChapters
import com.venera.desktop.gallery.data.desktopComicDownloadedHeaderReading
import com.venera.desktop.gallery.data.desktopComicDownloadedSkipped
import com.venera.desktop.gallery.data.DesktopComicDownloadQueue
import com.venera.desktop.gallery.data.desktopComicSafePathKey
import com.venera.desktop.gallery.data.DesktopComicShelfRawIdIndex
import com.venera.desktop.gallery.data.desktopComicShelfRerollKey
import com.venera.desktop.gallery.data.desktopComicRepairChapter
import com.venera.desktop.gallery.data.desktopComicRepairLabel
import com.venera.desktop.gallery.data.desktopComicRepairSeedHitLine
import com.venera.desktop.gallery.data.desktopComicRepairSeedMissLine
import com.venera.desktop.gallery.data.desktopComicRepairShouldShow
import io.github.composefluent.component.Button
import io.github.composefluent.component.Text
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal data class DesktopComicDownloadListing(
    val groups: List<DesktopComicDownloadedGroup>,
    val skipped: Int,
    val rootExists: Boolean,
)

/** 表头那一句（上屏的话）。三态各一句，与读数那一行是**两句不同的话**：读数给取证，这句给用户。 */
internal fun desktopComicDownloadedScreenHeader(listing: DesktopComicDownloadListing): String {
    val groups = listing.groups
    val chapters = groups.flatMap { it.chapters }
    return when {
        chapters.isEmpty() && listing.skipped == 0 && !listing.rootExists -> "本机还没存过整话（那一目录还没建出来）"
        chapters.isEmpty() && listing.skipped == 0 -> "存话的目录建好了，里面还一条都没有"
        chapters.isEmpty() -> "那一目录里有 ${listing.skipped} 条清单读不出（手工放的书或下到一半的话），今天摆不出"
        else -> "本机存过 ${groups.size} 本 · ${chapters.size} 话 · 齐了 ${chapters.count { it.complete }} 话" +
            (if (listing.skipped > 0) " · 另有 ${listing.skipped} 条读不出" else "")
    }
}

/**
 * 行里那一句：**号**、哪一话、盘上真有几页、齐没齐、多大。一屏可能摆着好几本，号不带上就分不出哪一话属于哪一本。
 *
 * ⚠️ 题名用「」包起来：源站的章节题名本身就常长成「第2話」，与前缀那截挨着念就成了
 * `第 2 话 第2話`（批 97/98 两帧图证都拍到这一眼）。
 */
internal fun desktopComicDownloadedRowLine(chapter: DesktopComicDownloadedChapter): String =
    if (chapter.complete) {
        "${chapter.comicId} · 第 ${chapter.order + 1} 话「${chapter.title}」 · ${chapter.writtenPages}/${chapter.expectedPages} 页 · 已下全 · ${desktopByteLabel(chapter.bytes)}"
    } else {
        "${chapter.comicId} · 第 ${chapter.order + 1} 话「${chapter.title}」 · ${chapter.writtenPages}/${chapter.expectedPages} 页 · 缺 ${chapter.expectedPages - chapter.writtenPages} 页 · ${desktopByteLabel(chapter.bytes)}"
    }

/** 那一屏的取证读数：六格全从状态现取（与表头那句同一次读盘，不另走一趟）。 */
internal fun desktopComicDownloadedReading(listing: DesktopComicDownloadListing): String =
    desktopComicDownloadedHeaderReading(listing.groups, listing.skipped)
