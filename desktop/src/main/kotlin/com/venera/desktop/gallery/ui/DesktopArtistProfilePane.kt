package com.venera.desktop.gallery.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.venera.compose.gallery.data.GalleryPorts
import com.venera.compose.gallery.domain.GalleryLegGuard
import io.github.composefluent.component.Button
import io.github.composefluent.component.Text
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneOffset

internal sealed interface DesktopArtistProfileState {
    data object Loading : DesktopArtistProfileState

    /** 整条链出事（判据本身炸了那一类），与"这一站没答上"是两句话。 */
    data class Failed(val message: String) : DesktopArtistProfileState

    /**
     * 交回的是**那颗持有件**而不是一张裸的张数表：续页是往同一屏上**追加**，
     * 谁去过重、谁到底、下一笔第几页都得有人记账 —— 那些都在 [DesktopGalleryBatch] 上。
     */
    data class Ready(val batch: DesktopGalleryBatch) : DesktopArtistProfileState
}

/**
 * 取**一页**并落地成新一颗持有件。第一轮与第 N 轮走的是同一颗函数，
 * 差别只在传进来的 `base` —— 两条路各写一份取数就是"第一页能翻而续页判据漂"的成因。
 *
 * 这一颗只剩"网络那一跳"：该要第几页交 [desktopArtistNextPage]，回来后怎么落地交
 * [desktopArtistBatchAfter]（两颗都不发网络，所以判据 ⑰–⑳ 真跑得到）。
 * 页码、去重键、到底三本账全在 [DesktopGalleryBatch] 上，桌面这边不另造一份（§0 坑 ㉓）。
 */
private suspend fun fetchArtistPage(
    ports: GalleryPorts,
    target: DesktopArtistTarget,
    base: DesktopGalleryBatch,
): Result<DesktopGalleryBatch> = withContext(Dispatchers.IO) {
    runCatching {
        val query = desktopArtistProfileQuery(target, LocalDate.now(ZoneOffset.UTC))
        val page = desktopArtistNextPage(base, target, query)
            // 这一腿已经到底 ⇒ 这一笔没有腿可发。屏上那枚按钮此时本该已经消失了，真走到这里
            // 就把原样交回 —— 重复取同一页会看着"翻得动"而其实一直在换同一批。
            ?: return@runCatching base
        val outcome = GalleryLegGuard.guard(target.site) {
            boardOf(ports, target.site).searchPosts(query, page = page, limit = DESKTOP_ARTIST_SLOT_SIZE)
        }
        desktopArtistBatchAfter(base, target, page, outcome.posts, outcome.answered, outcome.reason)
    }
}

