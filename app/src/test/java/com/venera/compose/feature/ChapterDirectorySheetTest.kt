package com.venera.compose.feature

import com.venera.compose.testsupport.RepoSources
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * ChapterDirectorySheet 目录面板的测试：一半是纯逻辑
 * （虚拟分卷带表、特典判据），一半是接线锚点
 * （详情页章节胶囊截断 + 面板入口、下载面板抓手）。
 */
class ChapterDirectorySheetTest {

    private fun src(rel: String): String {
        val f = File(RepoSources.appMain, rel)
        assertTrue("找不到 $rel，量具失效", f.isFile)
        return f.readText().replace("\r\n", "\n")
    }

    /** 注释行不参与匹配（与 TokenBudgetFrozenTest / TopBarTitleScaleTest 同一把口径）。 */
    private fun codeLines(rel: String): List<String> = src(rel).lines().map { it.trim() }
        .filter { !it.startsWith("//") && !it.startsWith("*") && !it.startsWith("/*") }

    private val sheetRel = "com/venera/compose/feature/ChapterDirectorySheet.kt"
    private val detailRel = "com/venera/compose/feature/ComicDetailScreen.kt"

    // ── 纯逻辑：虚拟分卷 ──

    @Test fun `235 话按 50 切出五带且尾带不足整带`() {
        val bands = virtualBandRanges(235)
        assertEquals(5, bands.size)
        assertEquals(0 to 50, bands[0])
        assertEquals(200 to 35, bands[4])
        // 全覆盖、不重不漏：每带起点 = 上一带起点 + 上一带话数
        bands.zipWithNext().forEach { (prev, next) ->
            assertEquals(prev.first + prev.second, next.first)
        }
        assertEquals(235, bands.sumOf { it.second })
    }

    @Test fun `边界总数不产生空带`() {
        assertEquals(0, virtualBandRanges(0).size)
        assertEquals(listOf(0 to 1), virtualBandRanges(1))
        assertEquals(1, virtualBandRanges(50).size)
        assertEquals(2, virtualBandRanges(51).size)
        assertEquals(1, virtualBandRanges(51)[1].second)
    }

    @Test fun `带表游标按带行加格行数推进`() {
        val bands = virtualBandRanges(120).mapIndexed { i, (lo, cnt) -> DirBand("v$i", lo, cnt) }
        val table = bandItemTable(bands, columns = 2)
        assertEquals(0, table[0].second)
        // 第一带：50 话 = 25 行 + 1 带行 → 下一带从 26 起
        assertEquals(26, table[1].second)
        // 手算总项数 = 带行数 + ceil(每带格数)
        val totalItems = table.last().second + 1 + (bands.last().count + 1) / 2
        assertEquals(3 + 60, totalItems)
    }

    // ── 纯逻辑：特典判据 ──

    @Test fun `特典认身份词与无量词，正篇量词不误伤`() {
        assertTrue(isSpecialTitle("兔女郎特典"))
        assertTrue(isSpecialTitle("番外小剧场"))
        assertTrue(isSpecialTitle("OAD 剧本"))
        assertTrue(isSpecialTitle("画集节选"))
        assertFalse(isSpecialTitle("第09话"))
        assertFalse(isSpecialTitle("第 123 话"))
        assertFalse(isSpecialTitle("episode 7"))
        assertFalse(isSpecialTitle(""))
    }

    @Test fun `特典判据不许变成猜数字`() {
        // 插位会猜，猜错就把第 10 话挪到第 9 话前头 —— 序列里只许打徽章，不许动顺序。
        val lines = codeLines(sheetRel)
        assertTrue("面板里出现了按数字重排章节的排序调用",
            lines.none { it.contains("sortedBy") || it.contains("sortedWith") })
    }

    // ── 接线锚点：页内截断 + 面板入口 ──

    @Test fun `页内截断保留且出口指向完整目录面板`() {
        val lines = codeLines(detailRel)
        assertTrue("页内章节胶囊没了首屏截断地板", lines.any { it.contains("list.take(CHAPTER_CHIP_LIMIT)") })
        assertTrue(
            "截断出口不再打开完整目录面板（第 180 话又成静默不可达）",
            lines.any { it.contains("CHAPTER_CHIP_LIMIT") && true } &&
                lines.any { it.contains("showChapterSheet = true") },
        )
    }

    @Test fun `详情页接上了面板并给全量章节`() {
        val lines = codeLines(detailRel)
        assertTrue("ChapterDirectorySheet 没被调用", lines.any { it.contains("ChapterDirectorySheet(") })
        assertTrue(
            "面板的选章入口没走 launchChapter 同一条读章腿",
            lines.any { it.contains("onPickChapter = { ch -> launchChapter(") },
        )
        assertTrue(
            "下载真值没接进面板（未下载筛选与绿点会哑掉）—— 且必须是 IO 预扫的快照 Map，" +
                "不是组合里逐话现扫的谓词",
            lines.any { it.contains("offlineMap = offlineMap") } &&
                lines.any { it.contains("Dispatchers.IO") },
        )
    }

    @Test fun `面板字阶全部吃 token 无裸 sp`() {
        val spPattern = Regex("""[0-9]+(\.[0-9]+)?\.sp""")
        val hits = codeLines(sheetRel).filter { spPattern.containsMatchIn(it) }
        assertTrue("面板里出现裸 sp：\n" + hits.joinToString("\n"), hits.isEmpty())
    }

    // ── 下载面板（屏 02）：居中 Dialog 已被替换，三档抓手都在 ──

    private val dlSheetRel = "com/venera/compose/feature/ChapterDownloadSheet.kt"

    @Test fun `旧居中下载弹窗没有复活`() {
        val lines = codeLines(detailRel)
        assertTrue(
            "「选择下载章节」AlertDialog 又回来了（117 个单列勾 = 本轮要修的病）",
            lines.none { it.contains("选择下载章节") },
        )
        assertTrue(
            "下载入口没接 ChapterDownloadSheet",
            lines.any { it.contains("ChapterDownloadSheet(") },
        )
    }

    @Test fun `下载面板三档抓手齐着`() {
        val lines = codeLines(dlSheetRel)
        assertTrue("「下一批」预设没了（从在读位置 +30 是追更的标准动作）",
            lines.any { it.contains("下一批 +30") })
        assertTrue("区间芯片没了（窗口外的章就只剩逐个点）",
            lines.any { it.contains("virtualBandRanges") })
        assertTrue("拖动圈选没了",
            lines.any { it.contains("awaitEachGesture") && it.contains("圈选") } ||
                lines.any { it.contains("awaitEachGesture") } && lines.any { it.contains("圈选") })
    }

    @Test fun `下载面板不编造体积估算`() {
        // 没有逐话体积真值 —— 「预计 xxx MB」会是编造数；只许报实测剩余空间。
        // 口径只吃代码行：设计意图的注释里出现「估算」字样不算违规（本仓的注释会被读）。
        val lines = codeLines(dlSheetRel)
        assertTrue("面板里出现了预计体积字样", lines.none { it.contains("预计") })
        val hasComplete = lines.any { it.contains("ChapterOffline.COMPLETE") }
        val hasPartial = lines.any { it.contains("ChapterOffline.PARTIAL") }
        assertTrue("离线状态判据没沿用 chapterOffline 三档（齐/不全要分得清）",
            hasComplete && hasPartial)
    }

    @Test fun `下载面板字阶全部吃 token 无裸 sp`() {
        val spPattern = Regex("""[0-9]+(\.[0-9]+)?\.sp""")
        val hits = codeLines(dlSheetRel).filter { spPattern.containsMatchIn(it) }
        assertTrue("下载面板出现裸 sp：\n" + hits.joinToString("\n"), hits.isEmpty())
    }

    @Test fun `页内章节段是严格双列`() {
        val lines = codeLines(detailRel)
        assertTrue(
            "页内章节不再是两枚一行（chunked(2) 没了 —— 用户 2026-11-09 指定双列）",
            lines.any { it.contains("visibleChapters.chunked(2)") },
        )
        assertTrue(
            "双列列宽没走约束换算（BoxWithConstraints.maxWidth）—— rememberContentWidth 首帧快照裁零、" +
                "onSizeChanged 回写踩跨相位环，两版都在这条线上翻过车",
            lines.any { it.contains("BoxWithConstraints(modifier = Modifier.fillMaxWidth())") } &&
                lines.any { it.contains("(maxWidth - chipGap) / 2") },
        )
        val hasFloor = lines.any { it.contains("coerceAtLeast(tokens.spacing.detailChapterChipMinWidth)") }
        assertTrue("列宽没了最小值兜底 —— 约束异常时又会渲染零宽格子", hasFloor)
        assertTrue(
            "章节网格不许用 onSizeChanged 回写组合态（measure→compose 跨相位回写 = 反复重组合）",
            lines.none { it.contains(".onSizeChanged { chipAreaMeasured") },
        )
    }

    @Test fun `目录面板网格是定高视口而不是无界填充`() {
        val lines = codeLines(sheetRel)
        assertTrue(
            "网格视口回到 fillMaxHeight(分数)：ModalBottomSheet 内容列是无界高，分数填充等于无界 —— " +
                "Lazy 网格全量组合（卡），索引轨被无限行高挤成一坨（用户 2026-11-09 两宗罪）",
            lines.none { it.contains(".fillMaxHeight(0.74f)") },
        )
        assertTrue("网格视口没有定高", lines.any { it.contains(".height(440.dp)") })
    }

    @Test fun `下载面板格宽走约束换算不回写`() {
        val lines = codeLines(dlSheetRel)
        assertTrue(
            "三列格宽没从 BoxWithConstraints 换算（上一版包裹列没 fillMaxWidth，量到自包裹窄宽塌成 2dp）",
            lines.any { it.contains("(maxWidth - gap * (columns - 1)) / columns") },
        )
        assertTrue("下载面板又出现 onSizeChanged 回写", lines.none { it.contains("onSizeChanged") })
    }

    @Test fun `离线预扫是批量单次且不被任务流掐死`() {
        val lines = codeLines(detailRel)
        assertTrue(
            "预扫回到逐问 chapterOffline（117 话 = 一万三千次目录探测，面板永远「核对中」—— " +
                "用户 2026-11-09 实锤），必须走 chaptersOfflineBatch",
            lines.any { it.contains("chaptersOfflineBatch(") },
        )
        assertTrue(
            "扫描的 LaunchedEffect key 里又挂了任务流 —— 每 tick 重启并掐死上一轮，扫描永远跑不完",
            lines.none { it.contains("LaunchedEffect(offlineScanChapters, downloadTasks)") },
        )
        val mgrLines = codeLines("com/venera/compose/download/DownloadManager.kt")
        assertTrue(
            "批量腿不许逐章 listFiles 整本目录（那就是把旧病搬进新函数）",
            mgrLines.none { it.contains("chapterDirs.firstNotNullOfOrNull") && it.contains("chapterId") && it.contains("Batch") },
        )
    }

    @Test fun `下载面板把开始下载钉在裁不到的位置`() {
        val lines = codeLines(dlSheetRel)
        assertTrue(
            "逐话窗口与底栏不再是同列顺延的兄弟（内容一高「开始下载」就被裁出屏 —— 用户 2026-11-09 实测）",
            lines.any { it.contains("DOWNLOAD_VIEWPORT_HEIGHT") },
        )
        assertTrue("主按钮回退成会被面板裁掉的普通 chip", lines.none { it.contains("text = \"开始下载\"") && it.contains("VeneraChip") })
        assertTrue(
            "磁盘态又回到组合里现扫（chapterOffline 每话 listFiles，235 话 = 打开即卡）",
            lines.none { it.contains("offlineState(") },
        )
    }

}
