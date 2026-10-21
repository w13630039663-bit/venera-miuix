package com.venera.desktop.gallery.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.venera.compose.gallery.data.GalleryFavorite
import com.venera.compose.gallery.data.GalleryPost
import com.venera.compose.gallery.data.GalleryPorts
import com.venera.compose.gallery.data.toPost
import com.venera.compose.gallery.domain.GalleryMerge
import io.github.composefluent.component.Text
import kotlinx.coroutines.launch

internal data class FavoritesProjection(
    val posts: List<GalleryPost>,
    val total: Int,
    val unrecognizedSites: List<String>,
    val unrecognizedCount: Int,
    val droppedUnusable: Int,
    val readAtMillis: Long,
) {
    val droppedLabel: String
        get() = listOfNotNull(
            droppedUnusable.takeIf { it > 0 }?.let { "不可摆 $it 条" },
            // 计数那一项按**条数**念（[unrecognizedCount]），不是按去重后的键数 ——
            // 用户丢的是记录，屏上就该念记录；键是另一件事，由 [unrecognizedSites] 点名。
            unrecognizedCount.takeIf { it > 0 }?.let { "站方键认不出 $it 条" },
        ).joinToString(" · ")
}

internal fun projectFavorites(
    favorites: List<GalleryFavorite>,
    nowMillis: Long = System.currentTimeMillis(),
): FavoritesProjection {
    val posts = ArrayList<GalleryPost>(favorites.size)
    val unrecognized = ArrayList<String>()
    var dropped = 0
    favorites.forEach { favorite ->
        val site = favorite.site
        if (site == null) {
            // 键认不出来 ⇒ 这一条**画不了**（toPost 要一个非空站）。计进去并点名，
            // 不拿"当成某一站"兜 —— 那会把 A 站的收藏摆到 B 站的坐标系里，是串图。
            unrecognized += favorite.siteKey
            return@forEach
        }
        val post = favorite.toPost(site)
        if (GalleryMerge.isDisplayable(post)) {
            posts += post
        } else {
            dropped++
        }
    }
    return FavoritesProjection(
        posts = posts,
        total = favorites.size,
        // 同一个键认不出来可能是**多条**（存档里那条键写错了）。列出去重，
        // 让那句话念的是"哪几个键有问题"而不是同一串重复 N 遍；
        // 而条数另存一枚（[unrecognizedCount]），屏上计数那一项按条数念。
        unrecognizedSites = unrecognized.distinct().sorted(),
        unrecognizedCount = unrecognized.size,
        droppedUnusable = dropped,
        readAtMillis = nowMillis,
    )
}

/**
 * 「没有每站预算」的哨兵值。传它给 [DesktopGalleryStatusBar] 的 `budgetMs` ⇒ 末句整条不念。
 *
 * 抽成常量而不是让调用点写 `-1`：这个数在桌面侧**只有一个含义**（"这一屏不涉及站"），
 * 而 `budgetMs` 是 `Long`，写裸 `-1` 会被下一个人读成"预算是负一"。
 */
internal const val STATUS_NO_BUDGET_MS = -1L

private fun unrenderableSentence(projection: FavoritesProjection): String {
    val parts = listOfNotNull(
        projection.unrecognizedCount.takeIf { it > 0 }
            ?.let { "站方键认不出来 $it 条（${projection.unrecognizedSites.joinToString("、")}）" },
        projection.droppedUnusable.takeIf { it > 0 }?.let { "没有可摆的缩略图 $it 条" },
    )
    return parts.joinToString("，") + "。这些记录还在存档里，界面上画不出来。"
}

/**
 * 收藏 → 墙的投影。**纯函数**，所以"三种空态各归各"这件事能上单测而不是靠人眼点。
 *
 * @param unrecognizedSites 站方 routeKey 认不出来的**键**（已去重）。
 *   点名到键而不是只给条数：用户能拿这个键去 `gallery_favorites.json` 里找到那几行并自己改；
 *   只给"3 条"他无处下手。去重是因为同一串坏键可能重复几行，重复念会把别的成因挤出屏幕。
 * @param unrecognizedCount 认不出来的**条数**（不去重）。
 *   这一枚与 [unrecognizedSites] 分开是**必须的**，不是冗余：坏键去重之后
 *   `unrecognizedSites.size` 是"几个键"，而屏上那句念的是"几条记录摆不出来" ——
 *   存档里有 3 条同坏键的记录时，去重后的 size 会让屏上念"认不出 1 条"，
 *   而用户实际丢掉的是 3 条。那是本仓最忌的假读数（少报，用户以为只丢了一条）。
 *   这条判据（`DesktopGalleryFavoritesPaneTest` ②）就是钉这一枚的。
 * @param readAtMillis 读这一屏的那一刻。收藏不出网，所以这枚时刻戳的语义是"**存档**读了几点"，
 *   不是"取数完成几点" —— 状态栏那句话在本屏改念「存档」，见 [DesktopGalleryFavoritesPane]。
 */
