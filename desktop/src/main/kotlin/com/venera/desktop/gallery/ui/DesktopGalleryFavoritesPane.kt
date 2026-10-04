package com.venera.desktop.gallery.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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

/**
 * 画廊收藏域的那一面墙。
 *
 * ## 取数链一步都不新造
 *
 * `ports.favorites.favorites` 是**真 StateFlow**（`GalleryFavoritesStore` 在构造时同步读完本机
 * `gallery_favorites.json`），本屏整条链就是读它 → [GalleryFavorite.toPost] → 墙。
 * 桌面这一侧此前**消费数 0**（2026-10-04 骨架那批量的），而 Android 侧那份数据一直在落盘。
 *
 * ## 三条口径，每条都对着一种"假读数"
 *
 * 1. **站方键认不出来的条目要单独说出来，不许静默丢**。`GalleryFavorite.site` 认不出 routeKey
 *    时给 `null`（`GallerySite.fromRouteKey` 的口径），而 [GalleryFavorite.toPost] 的签名要一个
 *    非空的站 —— 于是这批条目**没法画**。直接 `mapNotNull { it.site?.let { s -> it.toPost(s) } }`
 *    的形状是"收藏 12 张"而屏上只有 9 张，用户会去数。本屏把那几条的**条数与键**一起念出来。
 * 2. **不可摆上屏的（[GalleryMerge.isDisplayable] 为假）也要计数**。日榜那头有
 *    `droppedUnusable` 这条读数，本屏照同一把尺；否则"收藏里有 3 张是站方没给图的记录"
 *    会被读成"这 3 张加载失败"—— 两件事的成因不同。
 * 3. **空收藏与"有收藏但一张都摆不出来"必须念成两句话**。前者是"你还没收藏过"，
 *    后者是"有 8 条记录但全都摆不上屏"，后者需要用户去设置/数据目录里排查。
 *
 * ## 为什么本屏**没有**移除/清空按钮
 *
 * `GalleryFavorites` 的 `toggle` / `removeAll` / `clear` 三枚写动作都在（缺席说明里点过名），
 * 而本屏刻意不摆它们的入口：Android 侧那面墙的移除入口是**长按 → 确认弹层**
 * （`GalleryFavoritesBody` 的类注释写着为什么不在卡上挂心形），而桌面今天没有弹层宿主
 * （2026-10-04 那批把 `Dialog` 系弹层判负过，理由记在弹层那几份轮次文档里）。
 * 摆一枚"点了就永久删掉用户收藏、且不可撤销"的按钮而没有确认层，是本仓最不该有的形状。
 * ⇒ 缺席如实说：本屏是**只读**的那一档，写侧等弹层宿主。
 */
@Composable
internal fun DesktopGalleryFavoritesPane(ports: GalleryPorts) {
    val favorites by ports.favorites.favorites.collectAsState()

    // `site` 为 null 的那批（站方键认不出来）与不可摆上屏的计数都从**这一屏的收藏**现算，
    // 不另存一份 state —— 另存就会与收藏流不同步，而不同步的读数比没有读数更坏。
    val projection = remember(favorites) { projectFavorites(favorites) }

    val pad = DesktopGalleryMetrics.pageHorizontalPadding
    Column(Modifier.fillMaxWidth()) {
        Column(
            Modifier
                .weight(1f)
                .padding(start = pad, end = pad, bottom = 10.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            DesktopFavoritesHead(projection)

            when {
                // 分支次序是判据的次序：先"一条收藏都没有"，再"有收藏但一张都摆不出来"。
                // 反过来写会让后者落进空态那句话，用户读完以为是自己没收藏。
                favorites.isEmpty() -> DesktopFavoritesEmpty(
                    "还没有收藏过",
                    "这一屏读的是本机 `gallery_favorites.json`。收藏一条之后它就会出现在这儿。",
                )

                projection.posts.isEmpty() -> DesktopFavoritesEmpty(
                    "收藏里有 ${favorites.size} 条，但一张都摆不上屏",
                    unrenderableSentence(projection),
                )

                else -> DesktopGalleryPostWall(
                    posts = projection.posts,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        // 状态栏与日榜墙**同一颗**。三句读数被显式换掉，三句都不能照抄日榜：
        // - 「本次取数」→ [LOCAL_READ_LABEL]「本机存档读于」：收藏读的是本机 JSON，一次网都不发；
        // - 「每站 N 预算 · 超时那一站这一轮缺席」→ 整条不念（[STATUS_NO_BUDGET_MS]）：
        //   收藏屏没有"站"这个动作，说超时是凭空指控；
        // - 「N 站 · 都给内容」→ 整条不念（[STATUS_NO_SITE_TALLY]）：那句的主语是"站出勤"，
        //   而存档是**已经躺在本机**的，没有任何一站出过勤；空收藏时它会念「0 站 · 都给内容」。
        //
        // 站分布并没有因此少讲 —— 它挪到了段头那句「本机存档 N 条 · 摆上屏 M 张 · 某某站 k 张」，
        // 那一句的主语是"存档"，在本屏是真读数。
        // ⚠️ `missingCount` 不再传：首句整条不念时它是**没人读**的参数，
        // 留一个 0 在那儿会让下一个人以为"0 缺席"是本屏的一处判断。
        DesktopGalleryStatusBar(
            siteTotal = STATUS_NO_SITE_TALLY,
            missingCount = 0,
            fetchedAtMillis = projection.readAtMillis,
            budgetMs = STATUS_NO_BUDGET_MS,
            droppedLabel = projection.droppedLabel,
            elapsedLabelPrefix = LOCAL_READ_LABEL,
        )
    }
}

/** 段头：标题 + 条数读数。**不套** [DesktopGalleryPageHeader] —— 那一颗的日期与"换一批"是日榜专属。 */
@Composable
private fun DesktopFavoritesHead(projection: FavoritesProjection) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "画廊收藏",
            color = DesktopTheme.TextPrimary,
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            " · 本机存档 ${projection.total} 条 · 摆上屏 ${projection.posts.size} 张" +
                (if (projection.perSite.isEmpty()) "" else projection.perSiteLabel),
            color = DesktopTheme.TextSecondary,
            fontSize = 12.5.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * 空态。**刻意不居中** —— 与 [DesktopAbsentDomainBody] 同一条理由：
 * 居中会把"这一屏没东西"变成版面设计的一部分，而这里要说的两句话（没收藏过 / 有收藏摆不出来）
 * 都需要用户去对照自己的存档，不是让他欣赏一个空画面。
 */
@Composable
private fun DesktopFavoritesEmpty(title: String, detail: String) {
    Column(
        Modifier.fillMaxWidth().padding(top = 24.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(title, color = DesktopTheme.TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        Text(detail, color = DesktopTheme.TextSecondary, fontSize = 12.5.sp)
    }
}

/**
 * "一张都摆不出来"那一档把三条成因都念出来，不让用户猜是哪一条。
 *
 * 条数一律取 [FavoritesProjection.unrecognizedCount] 而不是 `unrecognizedSites.size`：
 * 后者是去重后的**键数**，屏上要念的是丢了几条**记录**。3 条同坏键的记录只念"1 条"会让用户
 * 以为只丢了一条，回头去数存档时对不上 —— 那比少念一条成因更坏。
 */
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
internal data class FavoritesProjection(
    val posts: List<GalleryPost>,
    val perSite: Map<com.venera.compose.gallery.data.GallerySite, Int>,
    val total: Int,
    val unrecognizedSites: List<String>,
    val unrecognizedCount: Int,
    val droppedUnusable: Int,
    val readAtMillis: Long,
) {
    val perSiteLabel: String
        get() = " · " + perSite.entries.joinToString("、") { "${it.key.displayName} ${it.value} 张" }

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
        perSite = posts.groupingBy { it.site }.eachCount(),
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
