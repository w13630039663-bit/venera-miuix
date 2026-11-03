package com.venera.desktop.gallery.data

import com.venera.engineprobe.EngineSession
import java.io.File

/**
 * 第一百一十六批 · **离线阅读**那一格：翻开那一屏先问本机那一棵树，缺的才问源站。
 *
 * ## 这一格补的是哪条缺口
 *
 * 漫画下载那一条线到今天接了五件事：整话落盘（批 96）、数得着（批 97）、删得掉（批 98）、
 * 缺页补得齐（批 111 / 113）、以及那四件事接上了屏（批 115）。而**翻开那一屏**（阅读器）
 * 从头到尾只问引擎 —— `VeneraDesktopLargeScreen.kt` 那一趟取页是 `e.load` + `e.pages`，
 * `DesktopComicPageFetcher.fetch()` 里只有 `engine.pageImage`，全路**一次都没碰过盘**。
 * 于是用户攒在机器上的 24 MB：断网翻不开，联网还要把同样的字节再取一遍。
 *
 * ## 三件事各自住在哪（不在这里重复一份"齐不齐"）
 *
 * - 「这一页在不在盘上」= `desktopComicPageLooksWhole`（第一百一十批那把**唯一**的尺子，
 *   续跑跳过 / 屏上念缺 N 页 / 队列那一行 已经吃它，这一批加阅读器成第四个消费者）。
 * - 「这一话在本机是哪一条目录」= [desktopComicLocalChapter]（按**源站话号**认，不按行号）。
 * - 「这一页的字节从哪来」= `DesktopComicPageFetcher.fetch()`：`localDir` 非空且那一页算一整页就吃盘，
 *   否则问引擎（那颗「吃盘」的读法在 `desktopComicLocalPageBytes`，与它同一颗文件）。
 *   **本文件自己不取字节**：除 [desktopComicOfflineLocalChapter] 把认话那一趟委托给读侧那颗件之外，
 *   这里只算那两格判断与两句人话。
 *
 * ## 为什么"该不该问引擎"必须是颗纯函数
 *
 * 那一判决定要不要起引擎、要不要发那一趟 `pages` 请求。写在 `LaunchedEffect` 里就成了
 * 「壳里那一段 if」——无头够不到、也钉不住。抽出来之后它可纯跑，而壳里只剩「问 / 不问」。
 */

/** 本机整话够用（一颗都不缺）⇒ 不必起引擎、不必问那一趟 `pages`。 */
internal fun desktopComicOfflineEpisodeIsWhole(local: DesktopComicLocalChapter?): Boolean =
    local != null && local.expectedPages > 0 && local.writtenPages >= local.expectedPages

/**
 * 阅读器手上那一问的本机侧：把源与号**归一到盘上那一形**再去认话。
 *
 * ## 为什么必须归一（第一百一十六批自审时补逮）
 *
 * 盘上那两层目录名是写侧净化过的（`desktopComicSafePathKey`：`/ \ : * ? " < > |` → `_`），
 * 而阅读器手上拿的是**原始值**（`EngineSession.Comic.id` / `sourceKey`）。直接比的话，凡是号里带
 * 那一串字符的源就永远认不到 —— 坏的样子不是读错话（那还只是保守），而是「明明本机有，
 * 翻开还是去问源站」，正好把这一批要治的病留下。
 *
 * ## 为什么这一格住在这里而不是读侧那颗件里
 *
 * 第一百一十二批把「读侧不许自己算净化」钉成一条判据（`DesktopComicShelfRawIdTest` ⑥）：
 * 「本机已下载」那一屏念的「已下全 / 缺 N 页」必须只跟着盘上状态改口，不跟着净化口径与队列表改口。
 * 本喵头一版就是把净化塞进 `desktopComicLocalChapter` 里，那条尺子当场红了 —— 红得对。
 * 归一是**阅读器那一问**的事（它手上是源站给的原始值），所以它住在这颗件里，读侧那颗仍零净化。
 *
 * 话号那一格不净化：它住在**清单里**而不是目录名里，两端本来就是同一枚字符串。
 */
internal fun desktopComicOfflineLocalChapter(
    root: File,
    sourceKey: String,
    comicId: String,
    chapterId: String,
): DesktopComicLocalChapter? =
    desktopComicLocalChapter(
        root,
        desktopComicSafePathKey(sourceKey),
        desktopComicSafePathKey(comicId),
        chapterId,
    )

/**
 * 要不要问引擎那两跳（`load` + `pages`）。
 *
 * 三档都答"要"，各是一回事：
 *  - `local == null`：本机没这一话 ⇒ 只能问源站；
 *  - 盘上的颗数**小于**清单应写：缺的那几页没有页键，而页键只有引擎回得出来（`onImageLoad` 那一路归它）；
 *  - 清单应写 ≤ 0：那条清单自己都说不出该有几页，**不许**据此断网（宁可多问一次源站）。
 */
internal fun desktopComicOfflineShouldAskEngine(local: DesktopComicLocalChapter?): Boolean =
    !desktopComicOfflineEpisodeIsWhole(local)

/**
 * 页表那一趟的**决策件**（可纯跑）：把「引擎回的页键 + 本机那一话」并成阅读器吃的那一串。
 *
 * 三种形状：
 *  - `imageKeys` 为空而本机有页 ⇒ 整表来自盘上（断网 / 引擎坏了 / 整话本来就齐）；
 *  - `imageKeys` 非空 ⇒ 每页都带上 `localDir`：盘上有的那一页吃盘，缺的那几页回落到引擎；
 *  - 两路都没有 ⇒ 空表（屏上念"这一话没取到页"，而不是摆一排空白）。
 *
 * ⚠️ 页序 [DesktopComicPage.page] 直接当盘上页名的索引：`desktopComicChapterPageFileName(index)`
 *    与 `writtenPages` 的那一趟循环吃的是**同一个 0 基索引**。这里掺一枚 ±1 就是"复合键两端不同轨"
 *    那一族，坏页那一档会永远错位一页（屏上念「缺 2 页」而翻开少了别的两页）。
 */
internal fun desktopComicReaderPages(
    sourceKey: String,
    comicId: String,
    episodeId: String,
    /** 引擎回的页键（源站标识，不是能直接 GET 的地址）；本机整话那一支为空。 */
    imageKeys: List<String>,
    /** 这一话在本机那棵树里的目录；`null` = 本机没有。 */
    localDir: File?,
    /** 盘上**算一整页**的颗数（缺页与坏页都不在内）。 */
    localPages: Int,
    engine: EngineSession?,
): List<DesktopComicPage> {
    val fromDiskOnly = imageKeys.isEmpty() && localDir != null && localPages > 0
    val size = if (fromDiskOnly) localPages else imageKeys.size
    if (size <= 0) return emptyList()
    return (0 until size).map { index ->
        DesktopComicPage(
            sourceKey = sourceKey,
            comicId = comicId,
            episodeId = episodeId,
            // 纯本机那一支里页键用的是盘上页名：那种页**不吃引擎**，所以它只是占位；
            // 真缺页时走的是另一支（imageKeys 非空），那才有源站的页键。
            imageKey = if (imageKeys.isEmpty()) desktopComicChapterPageFileName(index) else imageKeys[index],
            page = index,
            engine = engine,
            localDir = localDir,
        )
    }
}

/**
 * 源站那一趟的三种果（第一百一十六批）。
 *
 * 为什么是 sealed 三档而不是「一枚 Boolean + 一句成因」：`问过了而回 0 页`、`压根不必问`、
 * `想问而问不到` 是三件事，混成一档就会把第三种念成第一种（本仓那一族「读数说成另一路」）。
 */
internal sealed interface DesktopComicSourceOutcome {
    /** 源站那一趟**回来了**，带回几枚页键（回 0 枚也算回来了 —— 那是源站自己说的话）。 */
    data class Delivered(val pageCount: Int) : DesktopComicSourceOutcome

    /** 本机整话已齐 ⇒ 一颗源站都没问（断网也算成了）。 */
    data object NotNeeded : DesktopComicSourceOutcome

    /** 想问而问不到（含"引擎没起来"与那一趟塌了），带念得出的人话成因。 */
    data class Unreachable(val cause: String) : DesktopComicSourceOutcome
}

/**
 * 那一屏顶上念的那一句（`null` = 没什么要说）。
 *
 * ## 这一句要守的两条假话
 *
 * - **不许把"靠盘上翻开"念成整话都在**：源站问不到而盘上撑着时，必须念得出「缺的那几页看不了」，
 *   否则用户以为整话都在，翻到第 29 页才看到空白（第一百一十批那一族「念已下全而翻开缺页」的姊妹形）。
 * - **不许把"靠盘上翻开"念成整屏失败**：从前那一趟只要引擎塌了就 `readerPages = emptyList()`，
 *   本机明明有 28 页也一并清空 —— 那是把成功说小，与上一条同样坏。
 *
 * @param local 本机那一话（`null` = 本机没有）
 * @param outcome 源站那一趟的果
 * @param servedPages 最后上屏的页数
 */
internal fun desktopComicOfflineReadingNote(
    local: DesktopComicLocalChapter?,
    outcome: DesktopComicSourceOutcome,
    servedPages: Int,
): String? {
    // 为什么要 null：源站带回页键、或本机本来就齐到不必问 ⇒ 那是正常那一屏。
    // 缺页不在顶上念成因 —— 每一页自己去吃盘或取站，真缺的那一档由那一页自己报错，
    // 比屏顶那一句更靠近用户眼睛（而且屏顶那句一写就得替整话说谎：它数不到"哪一页缺"）。
    val why = when (outcome) {
        is DesktopComicSourceOutcome.Delivered -> if (outcome.pageCount == 0) "源站这一话回了 0 页" else null
        DesktopComicSourceOutcome.NotNeeded -> null
        is DesktopComicSourceOutcome.Unreachable -> outcome.cause
    } ?: return null
    if (servedPages == 0) return "这一话翻不开（$why），而本机一颗都没有。"
    val onDisk = local?.writtenPages ?: 0
    return if (local != null && onDisk < local.expectedPages) {
        "$why · 这一话靠本机翻开（盘上 $onDisk/${local.expectedPages} 页 · 缺的那几页看不了）。"
    } else {
        "$why · 这一话靠本机翻开（盘上 $onDisk 页）。"
    }
}

/**
 * 取证读数：`D_离线阅读 话=… 本机=…/… 源站=… 上屏=… 来自盘上=…`。
 *
 * 为什么单独一行而不并进 `desktopComicReaderReading`：那一颗钉的是"哪一话几页"（页表已定之后），
 * 这一颗钉的是**字节该从哪一路来** —— 批 116 要量的正是这一格，混在一起就分不出
 * "页表有 30 页而其中 28 页吃盘"。
 *
 * ⚠️ 最后一格叫「本机候选」而不是「来自盘上」：它数的是**页表里带了 `localDir` 的页**，
 *    而那一格里头每一页真吃没吃盘要到了 fetch 里才定得着（判式过了才算）。把候选说成已吃盘
 *    就是本仓那一族「读数说成另一路」—— 缺页混合时它会多报几页。
 */
internal fun desktopComicOfflineReadingReading(
    episodeId: String,
    local: DesktopComicLocalChapter?,
    outcome: DesktopComicSourceOutcome,
    servedPages: Int,
    pagesWithLocalDir: Int,
): String =
    "D_离线阅读 话=$episodeId" +
        " 本机=${local?.writtenPages ?: 0}/${local?.expectedPages ?: 0}" +
        " 源站=" + when (outcome) {
            is DesktopComicSourceOutcome.Delivered -> "回了${outcome.pageCount}页键"
            DesktopComicSourceOutcome.NotNeeded -> "未问"
            is DesktopComicSourceOutcome.Unreachable -> "问不到"
        } +
        " 上屏=$servedPages" +
        " 本机候选=$pagesWithLocalDir"
