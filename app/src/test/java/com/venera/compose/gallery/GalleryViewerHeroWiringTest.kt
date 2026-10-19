package com.venera.compose.gallery

import com.venera.compose.testsupport.RepoSources
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * pixiv 作品页 ↔ 大图播放器 那一段 hero 的尺子（2026-10-18）。
 *
 * 题面是用户真机的三句：
 * - 「pixiv 站源点击进图片详情页，再点击图片进大图模式下和从大图返回到图片详情页的 hero 元素有问题错位很严重」；
 * - 「去掉 pixiv 站源的大图模式的信息图标」；
 * - 「放大的时候应该是像图三那样全屏放大」。
 *
 * 错位那一条的成因**不在动画里**，在两处接线：
 * 1. [GalleryFlyIn] 是进程内单例，去程起点（矩形 + 那一帧像素）只在**点击那一刻**由打开方递。
 *    墙上那一跳递过一次，而那一笔只有大图播放器会 `consume()` —— 作品页压根不读它，于是它一路
 *    活到用户点某一页为止，播放器拿着**墙上那张卡的矩形与缩略构图**起飞；
 * 2. 一个作品的四页共用同一个 `uid`（那是"这一条作品"，收藏与历史按它记是对的），
 *    画面框与返回程落点都按 `uid` 存就是四页互相盖 —— 邻页比例不同，落点差一截。
 * 两条都改回旧写法照样编译、屏上照样动，所以只能由尺子钉：一处钉**顺序**，一处钉**键的形状**。
 */
class GalleryViewerHeroWiringTest {

    private val appMain = RepoSources.appMain

    /** 只留代码行 —— 注释里出现同一个标识符不算接线（KDoc 也会替不存在的实现说话）。 */
    private fun codeLines(rel: String): List<String> {
        val file = File(appMain, rel)
        assertTrue("找不到 $rel，量具失效（根：${appMain.absolutePath}）", file.isFile)
        return file.readLines().filterNot {
            val t = it.trim()
            t.startsWith("//") || t.startsWith("*") || t.startsWith("/*")
        }
    }

    private fun workScreen() = codeLines("com/venera/compose/gallery/ui/PixivWorkScreen.kt")
    private fun postScreen() = codeLines("com/venera/compose/gallery/ui/GalleryPostScreen.kt")
    private fun toolbar() = codeLines("com/venera/compose/gallery/ui/GalleryViewerToolbar.kt")

    /**
     * 去程起点：`GalleryFlyIn.capture` 必须**夹在**「写页轴」与「换 Activity」之间。
     *
     * 只钉"这一颗文件里出现过 capture"会被骗过去 —— 写在 `onOpenViewer` 之后就是"新窗口已经开了、
     * 起点才递进去"，屏上症状与没写一模一样（照旧拿旧矩形飞）。
     */
    @Test
    fun `点图那一刻重新截起点，顺序钉死`() {
        val lines = workScreen()
        val open = lines.indexOfFirst { it.contains("GalleryPageAxis.open(") }
        val launch = lines.indexOfFirst { it.contains("onOpenViewer(target.post.id)") }
        val captures = lines.withIndex().filter { (_, l) -> l.contains("GalleryFlyIn.capture(") }
        assertTrue("量具失效：找不到 GalleryPageAxis.open(", open >= 0)
        assertTrue("量具失效：找不到 onOpenViewer(target.post.id)", launch >= 0)
        assertEquals("作品页里截起点必须恰好一处（多一处就是第二处真相）", 1, captures.size)
        val (at, _) = captures.first()
        assertTrue(
            "去程起点没有夹在「写页轴」与「换 Activity」之间（capture 在第 ${at + 1} 行，" +
                "open 在第 ${open + 1} 行，launch 在第 ${launch + 1} 行）—— 播放器会拿上一跳留下的旧矩形飞",
            at in (open + 1) until launch,
        )
    }

    /** 量不到矩形那一档必须**清掉**交接件，而不是留着：宁可不飞，也不能飞错地方。 */
    @Test
    fun `量不到矩形时退回抬页而不是留着旧起点`() {
        val hit = workScreen().count { it.contains("else GalleryFlyIn.consume()") }
        assertEquals("capture 的失败分支要显式 consume（缺了就是拿上一跳的矩形飞）", 1, hit)
    }

    /** 返回程落点：回写与注销成对，缺一条就是"卡片回收后还往那儿飞"。 */
    @Test
    fun `返回程落点成对回写与注销`() {
        val lines = workScreen()
        val note = lines.count { it.contains("GalleryFlyIn.noteCardBounds(viewerKey") }
        val forget = lines.count { it.contains("GalleryFlyIn.forgetCardBounds(viewerKey") }
        assertEquals("回写落点恰好一处", 1, note)
        assertEquals("注销与回写成对", 1, forget)
    }

    /**
     * 播放器两半用**同一把逐页键**。
     *
     * 负向扫描一次列全（不是抓到第一条就停）：这一族曾经"某一处修了、另一处漏了"，
     * 而两半判据不一致就是用户读到的"返回会飞、进去不飞"那一类。
     */
    @Test
    fun `画面框与落点两半都按逐页键`() {
        val lines = postScreen()
        val stale = lines.mapIndexed { i, l -> "第 ${i + 1} 行：${l.trim()}" }
            .filter { it.contains("frameBounds[") && it.contains(".uid]") }
        assertTrue(
            "这些画面框仍按 uid 取，页轴四页会互相盖：\n" + stale.joinToString("\n"),
            stale.isEmpty(),
        )
        assertTrue(
            "返回程落点仍按 uid 查（cardBoundsOf）",
            lines.none { it.contains("cardBoundsOf(post.uid)") },
        )
        assertTrue("去程落点等的是这一页的键", lines.any { it.contains("frameBounds[initialViewerKey]") })
        assertTrue("返回程起点查当前页的键", lines.any { it.contains("frameBounds[post.viewerKey]") })
        assertTrue("返回程落点查同一把键", lines.any { it.contains("cardBoundsOf(post.viewerKey)") })
    }

    /**
     * 整屏档三面留白各自钉在同一枚判据上（用户：「放大的时候应该是像图三那样全屏放大」）。
     *
     * 钉的是"整格撤掉"而不是"高度变小"：`Spacer(height = 0.dp)` 与不摆等价，但把 padding 改成
     * 小一点的数照样编译、屏上照样是那张悬浮卡 —— 那正是这一条要防的写法。
     */
    @Test
    fun `整屏档撤掉三面留白`() {
        val lines = postScreen()
        assertEquals(
            "顶部让开状态栏那一格必须整格不摆",
            1,
            lines.count { it.contains("if (!fullBleed) Spacer(modifier = Modifier.height(statusBarTop") },
        )
        assertEquals(
            "底部让开底栏净空那一格必须整格不摆",
            1,
            lines.count { it.contains("if (!fullBleed) Spacer(modifier = Modifier.height(LocalBottomBarClearance") },
        )
        assertEquals(
            "左右那两枚 screenHorizontal 必须换成 0",
            1,
            lines.count { it.contains("padding(horizontal = if (fullBleed) 0.dp else tokens.spacing.screenHorizontal)") },
        )
        assertTrue("整屏档的判据是页轴（不是入口姓什么）", lines.any { it.contains("val fullBleed = axis != null") })
    }

    /** 信息钮：页轴那一屏撤整格；形参必填，新调用点不许不说清。 */
    @Test
    fun `页轴那一屏不摆信息钮`() {
        assertEquals(
            "底栏调用点要交 showInfo = axis == null",
            1,
            postScreen().count { it.contains("showInfo = axis == null") },
        )
        assertTrue(
            "GalleryViewerToolbar 的 showInfo 必须是必填形参（带默认值 = 调用点可以不说清）",
            toolbar().any { it.trim() == "showInfo: Boolean," },
        )
        assertEquals("信息钮那一格只挂一处判据", 1, toolbar().count { it.contains("if (showInfo) {") })
    }
}
