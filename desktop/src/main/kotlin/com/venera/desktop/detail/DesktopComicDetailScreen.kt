package com.venera.desktop.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.venera.compose.components.venera.VeneraButton
import com.venera.compose.components.venera.VeneraCard
import com.venera.compose.components.venera.VeneraCover
import com.venera.compose.components.venera.VeneraCoverMask
import com.venera.compose.components.contentMaxWidthFor
import com.venera.compose.ui.tokens.VeneraTokens
import com.venera.compose.components.WideScreenLayoutMode
import com.venera.engineprobe.ComicInfo
import com.venera.engineprobe.EngineSession

/** 章节行右侧那枚把手的文案（这一屏自持，不与队列屏那两枚把手共用同一颗串）。 */
internal const val DESKTOP_EPISODE_DOWNLOAD_LABEL: String = "下载这一话"

/**
 * 桌面漫画详情屏 —— 对应安卓 `ComicDetailScreen`（2322 行）。
 *
 * ## 与安卓的差别（**不假装一样**）
 *
 * 安卓那 2322 行里一大半是手机专属：下拉刷新、手势返回、共享元素转场、底部弹层、
 * 长按菜单。大屏上没有这些（`WideScreenPolicy` 那三条断点就是布局分家的地方），
 * 所以这里刻意做成**宽屏两栏**（左封面、右章节表）、**窄屏一栏**。
 *
 * 本轮做到的是**能真读**这条主线：展示详情 → 点章节进阅读器。
 * 收藏那一枚 2026-10-21 接上了（它是**收藏格唯一的造数据入口** —— 库里 0 条收藏时
 * 那一屏永远量不出「还没读回来」与「确实没有」谁在说话）。
 * 没做的明确列在这儿，不悄悄省掉：
 * - 书源切换、标签跳转、评论
 * - 收藏的**文件夹**选择（`FavoriteRecord.folderName` 恒走默认那一档）
 *
 * ## 第一百一十四批（2026-10-21）：把「下载这一话」那一枚摆上章节行
 *
 * 那一整条落盘链（入队 → 泵 → 逐页写盘 → 回读判齐 → 停手 / 撤掉）早在批 96~108 就接完并有判据盯着，
 * 缺的只有**门**：当前这条渲染路（本文件与 `VeneraDesktopLargeScreen`）里没有任何一处能把那一笔排进队，
 * 于是「下载整话」在桌面上等于不存在。旧说明写的是「长按某一话」，那是**已经被拆掉的 Fluent 平行线**
 * （`gallery/ui/DesktopComicPane.kt`）里的形状，本屏从来没有长按 ⇒ 那句是假话，本轮改掉。
 *
 * 为什么这一枚做成**看得见的按钮**而不是长按：`DesktopComicDetailSemanticsTest` 批 96 记过
 * 「行里再摆一枚按钮会把整行的点击吃掉」，而那一次的形状是按钮**嵌在整行那颗 clickable 里面**。
 * 本屏把把手放到卡片**外面**（同一 Row 的两个兄弟节点），整行的"点题名进阅读器"与把手的"排进队列"
 * 各拿各的命中框，因此没有那一次的问题 —— 这条由新加的无头判据钉（按把手时 `opened` 必须还是 0）。
 *
 * ## 为什么详情是必需的一屏
 *
 * 阅读器要「这一本有哪些话」那份章节表，而只有 `EngineSession.details()` 给得出。
 * 所以详情屏是阅读器的**上游**，不是装饰。
 */
@Composable
internal fun DesktopComicDetailScreen(
    mode: WideScreenLayoutMode,
    title: String,
    /** 取到的详情；`null` = 还没读回来。 */
    info: ComicInfo?,
    /** 读取中 / 失败原因。非空时屏上照原话说，**不留白**。 */
    note: String?,
    /** 这一本要不要打码。判据在外面算好，这里只搬（与 `DesktopWallCardModel` 同一条口径）。 */
    masked: Boolean,
    /**
     * 这一本收没收藏：三态 —— `null` = **还没读回来**、`false` = 读了而确实没收、`true` = 已收。
     *
     * 为什么要有 `null` 那一档：`FavoriteDao` 的读是 `scope.launch { … }` 发起即返回，
     * 把它当成 `false` 就会在冷启动那一帧念出「没收过」，而用户按下去是**再写一条**。
     * 与收藏屏那句「还没读回来 / 确实没有」分两句是同一条口径。
     */
    favorited: Boolean?,
    /**
     * 正在写收藏（按下去到落库之间那一档）。`true` 时禁用这枚钮，
     * 免得连点把两条 INSERT 打进同一格（`comic_favorite` 主键是 `(comic_id, source_name)`，
     * 重复那条只是覆盖，但"已收藏"与"收藏"来回翻是用户看得见的错乱）。
     */
    favoriteBusy: Boolean = false,
    /**
     * 按一下收藏 / 取消收藏。`null` = 桌面没接写侧（装配点没递 lambda）⇒ **按钮不摆**。
     *
     * 为什么不是"摆了而按不动"：本仓「灰行不许留在屏上」那条口径，也是评审 R6
     * （`onRetest = {}` 假开关）被点名的同一件事。没有写口就没有这枚钮。
     */
    onToggleFavorite: (() -> Unit)?,
    onBack: () -> Unit,
    onOpenEpisode: (EngineSession.Episode) -> Unit,
    /**
     * 把某一话排进下载队列。`null` = 桌面没接这一路（装配点没给下载根目录）⇒ **那一枚把手不摆**。
     *
     * 为什么不是"摆了而按不动"：与上面那枚收藏钮同一条口径（评审 R6 那一族）。
     * 更具体的理由是这一路的写口只有一颗：`DesktopComicDownloadQueue.enqueue` 要**绝对根目录**，
     * 而那一路拿不到根就是拿不到 —— 没有根的时候屏上一个下载把手都不该出现。
     *
     * ⚠️ 必传、无默认值（同 `onOpenSourceManage` 那一条：带 `{}` 默认值 = 忘传也不红）。
     */
    onDownloadEpisode: ((EngineSession.Episode) -> Unit)?,
    /**
     * 某一话在本机/队列里的状态那句话（算好的**成品文案**，`null` = 这一话没有任何可念的）。
     *
     * 为什么判据在壳里而不是这一屏自己查那张表：那张队列表是进程级单例，
     * `DesktopMaskJudgementSingleSourceTest` ③ 对"屏自己去找一份状态"同一口径已经钉过一轮 ——
     * 屏只搬，不算。这一格与收藏那三态同形：**还没读回来 ≠ 确实没有**。
     */
    episodeNoteOf: (EngineSession.Episode) -> String?,
    modifier: Modifier = Modifier,
) {
    val tokens = VeneraTokens
    val wide = contentMaxWidthFor(mode) != null
    val maxWidth = contentMaxWidthFor(mode)

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier = Modifier
                .then(if (maxWidth != null) Modifier.widthIn(max = maxWidth) else Modifier)
                .fillMaxSize()
                .padding(horizontal = tokens.spacing.screenHorizontal),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = tokens.spacing.space3),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(tokens.spacing.space3),
            ) {
                VeneraButton(onClick = onBack) { Text("← 返回") }
                Text(
                    text = title,
                    fontSize = tokens.type.screenTitle,
                    color = tokens.color.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                // 收藏那一枚。三档各念一句，`null` 那一档禁用而不是假装"没收过"：
                // 与搜索屏那枚「搜索中…」禁用是同一个形状（有明确成因、会自己变好）。
                if (onToggleFavorite != null) {
                    VeneraButton(
                        onClick = onToggleFavorite,
                        enabled = favorited != null && !favoriteBusy,
                    ) {
                        Text(
                            when {
                                favoriteBusy -> "正在写收藏…"
                                favorited == null -> "正在读收藏…"
                                favorited -> "已收藏 · 点取消"
                                else -> "收藏"
                            },
                            fontSize = tokens.type.caption,
                        )
                    }
                }
            }

            // 读失败 / 读取中要念出来：空白屏看起来像"这本没内容"。
            if (note != null) {
                Text(
                    text = note,
                    fontSize = tokens.type.caption,
                    color = tokens.color.textTertiary,
                    modifier = Modifier.padding(bottom = tokens.spacing.space3),
                )
            }

            if (info == null) return@Column

            if (wide) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(tokens.spacing.space6),
                ) {
                    DetailHead(info = info, masked = masked, modifier = Modifier.width(280.dp))
                    EpisodeList(
                        episodes = info.episodes,
                        onOpenEpisode = onOpenEpisode,
                        onDownloadEpisode = onDownloadEpisode,
                        episodeNoteOf = episodeNoteOf,
                        modifier = Modifier.weight(1f),
                    )
                }
            } else {
                Column(modifier = Modifier.fillMaxSize()) {
                    DetailHead(info = info, masked = masked, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(tokens.spacing.space4))
                    EpisodeList(
                        episodes = info.episodes,
                        onOpenEpisode = onOpenEpisode,
                        onDownloadEpisode = onDownloadEpisode,
                        episodeNoteOf = episodeNoteOf,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }
}

/** 左栏：封面 + 标题 + 简介。 */
@Composable
private fun DetailHead(
    info: ComicInfo,
    masked: Boolean,
    modifier: Modifier = Modifier,
) {
    val tokens = VeneraTokens
    Column(modifier = modifier) {
        VeneraCard {
            Box(modifier = Modifier.fillMaxWidth().aspectRatio(0.7f)) {
                VeneraCover(
                    url = info.cover,
                    contentDescription = info.title,
                    mask = if (masked) VeneraCoverMask.Masked else VeneraCoverMask.Visible,
                )
            }
        }
        Spacer(Modifier.height(tokens.spacing.space3))
        Text(
            text = info.title,
            fontSize = tokens.type.sectionTitle,
            color = tokens.color.textPrimary,
        )
        if (info.description.isNotBlank()) {
            Spacer(Modifier.height(tokens.spacing.space2))
            Text(
                text = info.description,
                fontSize = tokens.type.caption,
                color = tokens.color.textSecondary,
            )
        }
    }
}

/** 右栏：章节表。点一条进阅读器。 */
@Composable
private fun EpisodeList(
    episodes: List<EngineSession.Episode>,
    onOpenEpisode: (EngineSession.Episode) -> Unit,
    onDownloadEpisode: ((EngineSession.Episode) -> Unit)?,
    episodeNoteOf: (EngineSession.Episode) -> String?,
    modifier: Modifier = Modifier,
) {
    val tokens = VeneraTokens
    Column(modifier = modifier) {
        Text(
            text = "章节（${episodes.size}）",
            fontSize = tokens.type.sectionTitle,
            color = tokens.color.textPrimary,
        )
        Spacer(Modifier.height(tokens.spacing.space2))

        if (episodes.isEmpty()) {
            // 空章节表要说清是"源站没回话"，不是"这本就这样"。
            Text(
                text = "这一本没取到章节。可能是源站限流，或这本尚未更新。",
                fontSize = tokens.type.caption,
                color = tokens.color.textTertiary,
            )
            return@Column
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = tokens.spacing.space6),
            verticalArrangement = Arrangement.spacedBy(tokens.spacing.space1),
            // 章节表**不嵌外层滚动**：它自己就是这一栏的滚动容器。
            // （本仓记过 `LazyVerticalGrid` 进 `verticalScroll` 那条 `infinity maximum height` 崩溃。）
        ) {
            items(episodes) { ep ->
                EpisodeRow(
                    ep = ep,
                    onOpen = { onOpenEpisode(ep) },
                    onDownload = onDownloadEpisode?.let { { it(ep) } },
                    note = episodeNoteOf(ep),
                )
            }
        }
    }
}

@Composable
private fun EpisodeRow(
    ep: EngineSession.Episode,
    onOpen: () -> Unit,
    onDownload: (() -> Unit)?,
    note: String?,
) {
    val tokens = VeneraTokens
    // `placeholder` 是源站的「占位话」（一般要付费/要登录才给内容）。
    // 这类条目**仍然可点**：点进去由阅读器如实说「这一话取不到页」，
    // 比在这里静默变灰（用户点了没反应，像坏了）要好。
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(tokens.spacing.space2),
        ) {
            // 整行那颗卡片仍是"点题名进阅读器"（与批 96 之前一致）。
            VeneraCard(
                modifier = Modifier.weight(1f),
                onClick = onOpen,
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(
                        horizontal = tokens.spacing.space3,
                        vertical = tokens.spacing.space3,
                    ),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = ep.name.ifBlank { ep.id },
                        fontSize = tokens.type.body,
                        color = tokens.color.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (ep.placeholder) {
                        Text(
                            text = "占位话",
                            fontSize = tokens.type.caption,
                            color = tokens.color.textTertiary,
                        )
                    }
                }
            }
            // 把手放在卡片**外面**：批 96 那一次"按钮嵌在整行 clickable 里会吃掉点击"
            // 的坑就是这么避开的（两枚各拿各的命中框；无头判据钉"按把手时没进阅读器"）。
            // 没有写口（`onDownload == null`）时这一枚**整颗不摆**。
            if (onDownload != null) {
                VeneraButton(onClick = onDownload) {
                    Text(DESKTOP_EPISODE_DOWNLOAD_LABEL, fontSize = tokens.type.caption)
                }
            }
        }
        // 那一行下面念的是这一话的本机状态（排队中 / 正在下几页 / 已停手 / 失败 / 已有几页）。
        // 只挂在**这一行**上：从前批 97 量到过"三行一起念同一句进度"，靠的就是话号各归各行。
        if (note != null) {
            Text(
                text = note,
                fontSize = tokens.type.caption,
                color = tokens.color.textTertiary,
                modifier = Modifier.padding(
                    start = tokens.spacing.space3,
                    top = tokens.spacing.space1,
                ),
            )
        }
    }
}
