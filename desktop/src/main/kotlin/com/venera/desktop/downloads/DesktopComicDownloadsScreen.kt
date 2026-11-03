package com.venera.desktop.downloads

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import com.venera.compose.components.WideScreenLayoutMode
import com.venera.compose.components.contentMaxWidthFor
import com.venera.compose.components.venera.VeneraButton
import com.venera.compose.components.venera.VeneraCard
import com.venera.compose.ui.tokens.VeneraTokens
import com.venera.desktop.gallery.data.DesktopComicDownloadedChapter
import com.venera.desktop.gallery.ui.DesktopComicDownloadListing
import com.venera.desktop.gallery.ui.desktopComicDeleteArmedLabel
import com.venera.desktop.gallery.ui.desktopComicDeleteIdleLabel
import com.venera.desktop.gallery.ui.desktopComicDeleteRowKey
import com.venera.desktop.gallery.ui.desktopComicDownloadedRowLine
import com.venera.desktop.gallery.ui.desktopComicDownloadedPendingLine
import com.venera.desktop.gallery.ui.desktopComicDownloadedScreenHeader
import com.venera.desktop.gallery.data.desktopComicRepairLabel
import com.venera.desktop.gallery.data.desktopComicRepairShouldShow

/**
 * 桌面「本机已下载」屏 —— 对应安卓漫画域首页那一行点进去的那一屏。
 *
 * ## 这一批评的是"有逻辑没有门"
 *
 * 那一屏的四件事从批 97 起就一件件接完了并有判据盯着：数盘上那一棵树（读侧）、
 * 「缺 N 页」那一行的**补齐**把手（批 111 只入队而不动盘）、**删除**那两步（批 98：武装 → 再按才真删，
 * 而删没删掉是删完回读盘定的）、以及**什么时候该再读一次盘**（批 113 那一枚键）。
 * 而当前这条渲染路里**没有任何一屏**把它们摆出来：`gallery/ui/DesktopComicDownloadsPane.kt`
 * 只剩四颗纯函数 + 一个 data class（它的 `@Composable` 是 0），而且它还带着
 * `io.github.composefluent` 的 import —— 那是已被拆掉的 Fluent 平行线的残留。
 * 于是「盘上有什么 / 缺哪几页 / 怎么补 / 怎么删」这四件事用户今天看不见也改不了。
 *
 * ## 形状照队列屏（第一百一十四批），组件只用同一套
 *
 * `VeneraButton` / `VeneraCard` / `VeneraTokens` + material3 `Text`，**不引 Fluent 第二套**。
 * 文案逐字复用现成那几颗纯函数（表头那句、行里那句、两枚把手的两句、待读数那一句），
 * 这一屏只负责把它们摆上屏，一个字都不另写。
 *
 * ## 四件事刻意没做（不悄悄省掉）
 *
 * - **屏不碰盘**：读树、删除、入队全在外面（`gallery/ui/DesktopComicDownloadsDelete.kt` 与
 *   `gallery/data/DesktopComicShelfRepair.kt` 那两颗件里）。这一颗文件里 `withContext` 是 **0 处** ——
 *   旧屏那条「整颗文件只一处 IO」的判据在这里干脆收成「一颗都没有」。
 * - **屏不算武装态**：删除那两步的状态机（`armedKey` 记在哪一行）由壳拿着，屏只吃
 *   `armedKey: String?` 并据此选两枚文案之一。这样"第二次按下才真删"那条纪律能被无头喂数据钉住。
 * - **不摆封面**：这一屏要说的是页码与齐不齐，而它自己判遮罩会撞
 *   `DesktopMaskJudgementSingleSourceTest` ③（判据只有壳那一处）。
 * - **没有"全部删除 / 按本子删"**：那是会对多行同时扳确认的形状，而这一屏的确认口径是**一行一武装**。
 *
 * ## ⚠️ 行里念的是**号**而不是题名
 *
 * `DesktopComicDownloadedGroup.label` 就是号（盘上没有本子的题名，清单存的是话的题名，批 111 KDoc 明写）。
 * 造一个题名就是假话，所以这里连"本"那一行都不摆标题 —— 行那句自己带号。
 */
@Composable
internal fun DesktopComicDownloadsScreen(
    mode: WideScreenLayoutMode,
    /** 盘上那一棵树数回来的结果；`null` = **还没数回来**（念另一句，不念"本机没有"）。 */
    listing: DesktopComicDownloadListing?,
    /** 上一次按下的果（补齐排进队 / 删完了 / 删不动 —— 由壳现算，屏上照原话摆）。 */
    note: String?,
    /** 武装着的那一行的身份串（`desktopComicDeleteRowKey` 算出来的那一串）；`null` = 没武装。 */
    armedKey: String?,
    /** 「补齐这一话」：只挂在缺页那一行，按下去**只入队**而盘上一颗文件都不动。 */
    onRepair: (DesktopComicDownloadedChapter) -> Unit,
    /** 「删掉第 N 话的本机文件」：第一按只武装、武装着的正是这一行才真删。 */
    onDelete: (DesktopComicDownloadedChapter) -> Unit,
    /** 回底栏那一格。与源管理/队列/详情同一条口径：二级页必须有出路。 */
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = VeneraTokens
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
                    text = "本机已下载",
                    fontSize = tokens.type.screenTitle,
                    color = tokens.color.textPrimary,
                    modifier = Modifier.weight(1f),
                )
            }

            // 还没数回来与数回来而确实空了是两句话；后者那一句由表头那颗纯函数说。
            Text(
                text = listing?.let(::desktopComicDownloadedScreenHeader) ?: desktopComicDownloadedPendingLine(),
                fontSize = tokens.type.caption,
                color = tokens.color.textSecondary,
            )

            if (note != null) {
                Text(
                    text = note,
                    fontSize = tokens.type.caption,
                    color = tokens.color.textTertiary,
                    modifier = Modifier.padding(top = tokens.spacing.space2),
                )
            }

            Spacer(Modifier.height(tokens.spacing.space3))

            // 没数回来 / 数回来而确实空了：两种都不摆行，也不摆"没有行"的假把手。
            val chapters = listing?.groups?.flatMap { it.chapters }.orEmpty()
            if (chapters.isEmpty()) return@Column

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = tokens.spacing.space6),
                verticalArrangement = Arrangement.spacedBy(tokens.spacing.space1),
            ) {
                items(chapters, key = ::desktopComicDeleteRowKey) { chapter ->
                    DownloadedRow(
                        chapter = chapter,
                        armed = armedKey == desktopComicDeleteRowKey(chapter),
                        onRepair = { onRepair(chapter) },
                        onDelete = { onDelete(chapter) },
                    )
                }
            }
        }
    }
}

/** 一话一行：左边那一句念号/页/齐缺/体积，右边最多两枚把手（补齐只在缺页那一档摆）。 */
@Composable
private fun DownloadedRow(
    chapter: DesktopComicDownloadedChapter,
    armed: Boolean,
    onRepair: () -> Unit,
    onDelete: () -> Unit,
) {
    val tokens = VeneraTokens
    // 把手一律挂在卡片**外面**当兄弟节点：`VeneraCard` 底层是 miuix `Card`，
    // 那颗 `combinedClickable` 会把嵌在里面的把手整颗吃掉（批 96 / 114 都记过这一眼）。
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(tokens.spacing.space2),
    ) {
        VeneraCard(modifier = Modifier.weight(1f)) {
            Text(
                text = desktopComicDownloadedRowLine(chapter),
                fontSize = tokens.type.caption,
                color = tokens.color.textPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth().padding(
                    horizontal = tokens.spacing.space3,
                    vertical = tokens.spacing.space2,
                ),
            )
        }
        // 「补齐」只有缺页那一档才摆（齐了摆它就是多一枚没意义的把手）。
        if (desktopComicRepairShouldShow(chapter)) {
            VeneraButton(onClick = onRepair) {
                Text(desktopComicRepairLabel(chapter), fontSize = tokens.type.caption)
            }
        }
        // 删除那两档：没武装点名"删哪一话"，武装着点名"再按一次就删"。
        VeneraButton(onClick = onDelete) {
            Text(
                text = if (armed) {
                    desktopComicDeleteArmedLabel(chapter.order + 1)
                } else {
                    desktopComicDeleteIdleLabel(chapter.order + 1)
                },
                fontSize = tokens.type.caption,
            )
        }
    }
}
