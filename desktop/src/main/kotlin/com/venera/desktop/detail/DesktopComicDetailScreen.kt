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
 * - 下载整话 / 下载队列入口（`DesktopComicChapterDownload` 逻辑已有，缺 UI）
 * - 书源切换、标签跳转、评论
 * - 收藏的**文件夹**选择（`FavoriteRecord.folderName` 恒走默认那一档）
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
                EpisodeRow(ep = ep, onOpen = { onOpenEpisode(ep) })
            }
        }
    }
}

@Composable
private fun EpisodeRow(
    ep: EngineSession.Episode,
    onOpen: () -> Unit,
) {
    val tokens = VeneraTokens
    // `placeholder` 是源站的「占位话」（一般要付费/要登录才给内容）。
    // 这类条目**仍然可点**：点进去由阅读器如实说「这一话取不到页」，
    // 比在这里静默变灰（用户点了没反应，像坏了）要好。
    VeneraCard(
        modifier = Modifier.fillMaxWidth(),
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
}
