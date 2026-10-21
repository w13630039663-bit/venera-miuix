package com.venera.desktop.gallery.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.venera.compose.gallery.data.GalleryPorts
import com.venera.desktop.gallery.data.DesktopGalleryBrowseHistory
import com.venera.desktop.gallery.data.DesktopDecoTier
import io.github.composefluent.component.Text
import java.io.File

internal object DesktopGalleryHomeSectionKey {
    const val DAILY = "section-daily"
    const val FOLLOWED_ARTISTS = "section-followed-artists"
    const val HISTORY = "section-history"
    const val DOWNLOADS = "section-downloads"
    const val LATEST = "section-latest"
    const val RANKING = "section-ranking"
}

/**
 * 「有内容件」的六种说法 —— 只有这一颗 sealed 才是 `when` 穷尽检查的载体
 * （理由见类注释里那一节）。今天六档；S3 接上「最新」之类时加一档，忘了在两颗 `when` 里出形状的代价是编译不过。
 *
 * ⚠️ 下面几档不属于图库域 —— 它们是**别的域**的页（2026-10-04 先后接回漫画域两页、
 * 收藏域与搜索域各一页）。之所以放在这颗 sealed 里而不是另开一颗：pane 的行模型
 * [DesktopGalleryHomeRow] 是全桌面共用的一颗，它的 `when` 必须能同时排开四个域的货；
 * 另开一颗 sealed 就等于给每个域配一套行模型，那是本仓判过负的"第二套布局系统"
 * 在更小尺度上的重演。
 */
internal sealed interface DesktopGallerySectionContent {
    data object Daily : DesktopGallerySectionContent
    data object FollowedArtists : DesktopGallerySectionContent

    /** 漫画域：源的探索页（分区 + 封面墙）。 */
    data object ComicExplore : DesktopGallerySectionContent

    /** 漫画域：本机收藏夹（`LocalFavoritesManager`，与画廊收藏不是同一份数据）。 */
    data object ComicFavorites : DesktopGallerySectionContent

    /** 漫画域：阅读历史（`comic_history` 那张表，第八十二批接上写口后点亮）。 */
    data object ComicHistory : DesktopGallerySectionContent

    /** 漫画域：源管理（批 83 —— 有哪几颗源、当前用哪一颗、哪几颗被停用）。 */
    data object ComicSources : DesktopGallerySectionContent
    /** 批 97：本机存过的那些整话（`数据根/downloads` 那一棵树）。 */
    data object ComicDownloads : DesktopGallerySectionContent
    /** 第一百零五批：队列里每一笔那一屏（`数据根/downloads/download_tasks.json` 那张表）。 */
    data object ComicQueue : DesktopGallerySectionContent

    /** 画廊收藏域：本机 `gallery_favorites.json` 那面墙。 */
    data object GalleryFavorites : DesktopGallerySectionContent

    /** 画廊搜索域：标签串 → 三站并发 → 一面结果墙。 */
    data object GallerySearch : DesktopGallerySectionContent

    /** 设置域：墙上那四枚档（第七批）。它不吃 `GalleryPorts`，吃那颗具体的桌面偏好件。 */
    data object GalleryDisplay : DesktopGallerySectionContent

    /**
     * 图库域：本机存过的图那一屏（批 85）。
     *
     * 它既不吃 `GalleryPorts` 也不吃网络 —— 只数落点目录里已有的文件。
     * 与 [GalleryDisplay] 同一族形状（形参给的是桌面侧那颗具体的东西，这里是集成方算好的落点）。
     */
    data object Downloads : DesktopGallerySectionContent

    /**
     * 图库域：本机点开过的那些图那一屏（批 86）。
     *
     * 与上面那枚 [Downloads] 同一族形状：都不吃 `GalleryPorts`、都不问网络，
     * 只把本机已有的一份档念出来 —— 区别是那一屏数的是**盘上的文件**，这一屏读的是**档里的行**，
     * 而它摆预览档（那一屏刻意不摆，理由在两颗屏的类注释里各写一份）。
     */
    data object BrowseHistory : DesktopGallerySectionContent

    /**
     * 画师域：本机关注名单那一屏（第八批）。
     *
     * ⚠️ 与上面那枚 [FollowedArtists] **不是一页**：那一枚是图库首页的横卡（按 `ROW_SIZE` 截断八位），
     * 这一枚是整页（摆全）。两枚分开登记，正是为了让"截不截断"这件事在类型上看得见 ——
     * 合成一枚的话，下一次有人把首页那栏改成整页长排，编译器不会拦。
     */
    data object ArtistsWall : DesktopGallerySectionContent

    /**
     * 发现域：从收藏里抽标签、每站各取一页混摆的那面墙（第九批）。
     *
     * 与 [Daily] 分开是必须的：两面墙的取数链不同（一轮抽样 vs 站方榜），
     * 合一枚就等于让"这一屏少发了一站"与"榜那一屏少了一站"共用一条读数。
     */
    data object ForYouWall : DesktopGallerySectionContent

    /**
     * 图库域「最新」那一行（第十批 ⑤ 点亮）。
     *
     * 与 [Daily]、[ForYouWall] 分成三枚是必须的：三面墙的取数链各不相同
     * （站方榜 / 一轮抽样 / 空标签默认序），合一枚就等于让"这一屏少发了一站"
     * 与"那一屏少抽了一个标签"共用一条读数，而那是两件事。
     */
    data object Latest : DesktopGallerySectionContent

    /**
     * 图库域「排行榜」那一行（第二十八批 批 28 点亮）。
     *
     * 与 [Latest]、[Daily] 分成三枚的理由同上一条（取数链不同）：这一屏发出去的是**带伪标签**的串
     * （`date:… order:score` / `sort:score:desc`），而那一半屏发的是空标签或真标签 ——
     * 合一枚就等于让"榜那一屏少了一站"与"最新那一屏少发了一站"共用一条读数。
     */
    data object Ranking : DesktopGallerySectionContent
}

/**
 * pane 里的一行。
 *
 * ⚠️ 这里**没有** `onClick` 字段：未实现那一档没有任何可执行动作，而把一颗对这一行恒为 `null`、
 * 对另四行没人读的字段放进模型里，就是本仓记过的那种"漏传不报错的参数"。
 * （那个数会跟着账走：「最新」第十批 ⑤ 点亮前这里是四行，「排行榜」第二十八批 批 28 点亮后是二行
 * —— `DesktopStaleClaimsTest` ⑦ 现数比一遍。）
 * 「未实现的行不许点」改由两处管住：这颗文件的两颗 `when` 里 `null` 分支只出 [DesktopUnimplementedRow]
 * （那颗件连 `clickable` 都没 import），以及用例对源码文本的负向断言。
 */
internal data class DesktopGalleryHomeRow(
    val key: String,
    val title: String,
    val glyph: String,
    val content: DesktopGallerySectionContent?,
    val reason: String?,
)

/**
 * 六行。**唯一的输入是"这一行被点了要做什么"** —— 没有任何数据输入，所以项数在结构上就不可能跟着数据变。
 *
 * ## 三行「未实现」各自的判据（原文抄自 S2-B 计划 §2.2）—— 今天**三行都已点亮**，逐条留在原地当账
 *
 * ~~**历史**：存在 `gallery/data/GalleryHistoryStore.kt`（或 `data/db` 里一张 gallery 历史表）
 * **且**注册表有 `HISTORY` **且**有内容件 —— 三者同时。~~
 * **已于第八十六批点亮**，而那两条存储形态都**没有**被采用 —— 补的是第三种：桌面自持一颗
 * `gallery/data/DesktopGalleryBrowseHistory.kt`。理由是那两条点名的都是**对岸**的候选，
 * 而这一格没有可对岸的东西：Android 侧根本没有 gallery 浏览历史，`app/` 又是受保域。
 * 批 85 是同一条处理（对岸 `GallerySaver` 因 MediaStore 整颗在排除清单里 ⇒ 桌面自持一颗）。
 * ⚠️ 2026-10-07 批 14 量过一句假话：`data/db` 里**确实有一张历史表**（`HistoryDao` 的 `comic_history`，
 * 整个目录在桌面编译面上，而桌面还有一颗 `HistoryDaoTest` 在读写它）—— 那是**漫画阅读进度**，
 * 不是 gallery 历史表。屏上那句从前把这一行说成"本机没有存储"，那是假的：表在，
 * **缺的是会把进度写进去的那一笔**。⚠️ 第八十一批又改判一次：那句当时把差的一层说成
 * "还没有那一屏"，而桌面今天**有**阅读那一屏了（它只读不写）—— 一句点名"差哪一层"的话也会过期，
 * 所以那句当时只说那笔没写的账，不再说某一屏存不存在。
 * ⚠️ 第八十二批把那句里"那笔没写"也改掉了：写口今天**有了**（阅读那一屏每翻一页写一行，
 * 见 `gallery/data/DesktopComicHistory.kt`）。屏上那句因此换成点名真正缺的一层 ——
 * gallery 这一侧的**浏览历史**从没存过。漫画域另起了一行「阅读历史」（key `comic-history`），
 * 读的就是 `comic_history` 那张表，与这一行不是同一件事。
 * ⚠️ 第八十六批把那最后半句也撤了（它现在**不再成立**）：写口在详情浮层里（点开一张卡记一行，
 * 挡住的那一档不记），读侧是 [DesktopGallerySectionContent.BrowseHistory] 那一屏。
 * ~~**下载**：`GallerySaver` 落点从 MediaStore 改为 `PathProvider` 目录 **且**存在一颗枚举读侧。~~
 * **已于第八十五批点亮**：那两条判据今天都成立 —— 落点是 `数据根/downloads/图库`
 * （那颗对岸件因为 MediaStore 整颗在排除清单里，桌面自持一颗只写字节的 `DesktopImageSaver`），
 * 枚举读侧就是 [DesktopGallerySectionContent.Downloads] 那一屏。
 * ⚠️ **这一格改了账（第九十六批）**：从前那半句连写的是"仍然缺的是"（**漫画章节下载**，说它是 feature 侧的另一件事），
 * 而今天整话落盘接上了 —— 详情那一屏长按某一话就把整话写进 `数据根/downloads/<站名键>/<号>/<话目录>/NNNN.jpg`
 * （命名逐字沿用对岸，见 `gallery/data/DesktopComicChapterDownload.kt`）。"别把两件事记成一件"仍然成立，只是两件事都做了：
 * 这一屏数的是 `downloads/图库` 里的散图（`listFiles` 不递归，所以漫画那三层混不进来），
 * 而**已下的那些今天数得着了**（第九十七批：漫画域首页「本机已下载」那一屏，读盘上那一棵树、逐页查盘），
 * 而那一屏**删得掉**了（第九十八批：每一行右边那颗把手，按两次才真删，删完回读盘才定档 ——
 * 对岸那一形丢掉返回值再恒说已删除，桌面不照抄，见 `gallery/data/DesktopComicChapterDelete.kt`）。
 * 判据在 `DesktopStaleClaimsTest` ㉓ 与 ㉔：它们扫全文含注释（那句从前躲在 `codeText` 看不见的地方活了六批），
 * 而它禁的是**那句连写的形状** —— 本行故意把引号与括号隔开写，账读得出，禁串也不被再抄一遍。
 * Coil 自己的盘缓存住在数据根下那份 `cache/venera_image_cache`（批 90 搬进来的；从前在系统 Temp）—— 那仍是缓存不是落盘，
 * 名字是地址哈希、随时会被容量闸清掉，所以那一屏数的不是它。
 * ~~**排行榜**：那一屏自己有得摆（选哪一档时间 + 按它分页）。~~
 * **已于第二十八批 批 28 点亮**：`GalleryRankings` 给六档、各站分数串与窗口，
 * `searchPosts(query, page, limit)` 给分页（那条契约认 `limit`，天花板 320），桌面这一屏把两者接起来。
 * 原判据里那句"自己有得摆"落地成两档：档行 + 续页（吃墙上那颗共用持有件）。
 * **仍然成立**的那条边界留着：三站"热门"不是同一件事（只有 yande.re 有官方日榜，另两站是高分池模拟），
 * 所以那一屏不许拿日榜那个日期去描述三站（读数在 `GalleryDailyFeed` 的类注释里）；
 * 而 Gelbooru 不认四档时间窗 ⇒ 带着它的那一组腿**只摆两档**（判据在 `DesktopRankingTest` ①②③）。
 *
 * ~~**最新**：`BoardSource.searchPosts` 有可用排序参数**且**三站中 ≥2 站真返回。~~
 * **这一条今天被实测推翻并已于第十批 ⑤ 点亮**（读数在手册 §23.9）：两站的**默认序就是最新在前**，
 * 而空标签在两边都是合法查询 ⇒ 要的根本不是"排序参数"，而是 `searchPosts("", 1, n)`。
 * 原判据里那半句"三站中 ≥2 站真返回"照样成立（Gelbooru 匿名 401，没量就不算它）。
 * ⚠️ 别为此给共享接口加 `sort` 形参 —— 那会牵动 Android 侧的冻结面，而这一枚今天没有对象可喂
 * （`DesktopLatestTest` ⑥ 钉着这一条）。
 *
 * 那句 caption 点名的缺席件（写进界面上的句子必须短到两行念完，见 [DesktopUnimplementedRow] 的预算说明）：
 * ~~历史 = 会记下阅读进度的那一屏（**不是**"没有存储"，见上面那条）~~（批 86 那一屏与那一笔写都落地了，
 * 那句跟着撤 —— 留着它就是"文案说没有而东西已在"那一族的**第七次**，同一族的前六次逐次记在手册 §0）。
 * ~~下载 = 存图落点~~（批 85 那一屏已落地，那句跟着撤 —— "文案说没有而东西已在"那一族的第六次）。
 * ~~排行榜 = 选档那一屏~~（批 28 那一屏已落地，那句跟着撤）。
 * ⇒ 今天这六行**零行未实现**（没有一行带 caption）：`reason` 那枚字段与 [DesktopUnimplementedRow] 那一档仍留在类型上
 * （谁新加一行不接内容件，界面上还有地方说实话），但生产路径上走到那一档的输入今天是空集 ——
 * 判据把这一点钉在 `DesktopGalleryHistorySeedTest` ②，而不是钉在某一句散文上。
 */
internal fun desktopGalleryHomeRows(): List<DesktopGalleryHomeRow> = listOf(
    DesktopGalleryHomeRow(
        key = DesktopGalleryHomeSectionKey.DAILY,
        title = "每日热门",
        glyph = "日",
        content = DesktopGallerySectionContent.Daily,
        reason = null,
    ),
    DesktopGalleryHomeRow(
        key = DesktopGalleryHomeSectionKey.FOLLOWED_ARTISTS,
        title = "正在关注的画师",
        glyph = "绘",
        content = DesktopGallerySectionContent.FollowedArtists,
        reason = null,
    ),
    DesktopGalleryHomeRow(
        key = DesktopGalleryHomeSectionKey.HISTORY,
        title = "历史",
        glyph = "历",
        // 第八十六批点亮：那笔写入住在详情浮层里（点开一张卡就记一行），存储是桌面自持那颗
        // `DesktopGalleryBrowseHistory`，内容件就是 [DesktopGallerySectionContent.BrowseHistory] 那一屏。
        content = DesktopGallerySectionContent.BrowseHistory,
        reason = null,
    ),
    DesktopGalleryHomeRow(
        key = DesktopGalleryHomeSectionKey.DOWNLOADS,
        title = "下载",
        glyph = "存",
        // 第八十五批点亮：落点 `数据根/downloads/图库` + 那一屏的枚举读侧都在了（判据与图证在 rounds 文档）。
        content = DesktopGallerySectionContent.Downloads,
        reason = null,
    ),
    DesktopGalleryHomeRow(
        key = DesktopGalleryHomeSectionKey.LATEST,
        title = "最新",
        glyph = "新",
        // 第十批 ⑤ 点亮：查询串恒空、各站默认序就是最新在前（读数与实测在手册 §23.9）。
        content = DesktopGallerySectionContent.Latest,
        reason = null,
    ),
    DesktopGalleryHomeRow(
        key = DesktopGalleryHomeSectionKey.RANKING,
        title = "排行榜",
        glyph = "榜",
        // 第二十八批 批 28 点亮：口与六档判据本来就在共享面（`GalleryRankings`），缺的就是这一屏。
        content = DesktopGallerySectionContent.Ranking,
        reason = null,
    ),
)

/**
 * 六行按 key 取一行 —— 取不到就**抛并说清**，不许静默退回第一项。
 *
 * 批 27 之前这一段是零调用点：段头 `desktopGallerySegments()` 自己写了一份 `associateBy { it.key }`
 * 加一次 `rows[key] ?: error(...)`，两处同一句话（批 12 判过的正是这一族）⇒ 现在段头吃这一颗。
 * ⚠️ 取证参数那一路**不走**这一颗：`--start-section=` 对不上任何一行时要念一句实话而不是崩窗，
 * 所以它用自己的 `firstOrNull`（"没取到 / 没有 / 没接"是三句话，这里要的是中间那句）。
 */
internal fun List<DesktopGalleryHomeRow>.byKey(key: String): DesktopGalleryHomeRow =
    firstOrNull { it.key == key } ?: error("注册表里没有 key「$key」—— 段头与 pane 必须对同一份六项表说话")

/**
 * pane 里的一行怎么**摆**：内容件决定形状，`null` 那一档只有 [DesktopUnimplementedRow] 一条路。
 *
 * `when` 挂在 `val rendered: Unit =` 这个**表达式位置**上（而不是写成一条语句）：
 * Kotlin 只对表达式位置的 `when` 做穷尽检查，那颗 `val` 就是这根钩子，别把它当冗余删掉。
 */
private val HoverBackground = DesktopTheme.SurfaceRaised
private val IndicatorColor = DesktopTheme.AccentFuji
private val SelectedGlyph = DesktopTheme.AccentFuji
private val Glyph = DesktopTheme.TextSecondary

private val SelectionStart = DesktopTheme.SelectionGradientStart

private val SelectionEnd = DesktopTheme.SelectionGradientEnd

// 稿上 `.nav.sel` 是 `linear-gradient(90deg,#3A2A55,#2C2C2C 78%)`；Compose 的停靠点写法与 CSS 同形，
// 0f 与 0.78f 就是那两档，无需换算（方向差一条 90deg 与对角线的观感，S5 出图时一并校）。

private val SelectionBrush = Brush.linearGradient(0f to SelectionStart, 0.78f to SelectionEnd)
