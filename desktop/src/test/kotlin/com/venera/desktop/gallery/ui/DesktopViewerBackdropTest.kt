package com.venera.desktop.gallery.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.venera.compose.gallery.domain.GalleryViewerBackdrop
import com.venera.desktop.gallery.data.DesktopGalleryPreferences
import com.venera.desktop.platform.FakePaths
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 大图那一层画什么、底下那面墙糊不糊，都跟着那枚偏好走（第六十一批接色，第六十三批接糊）。
 *
 * ## 为什么四档能算"全真"
 *
 * `BLACK` / `DARK_GRAY` / `WHITE` 三档直接吃枚举自己带的 `argb`（那是全仓现成的口径，不另造数）。
 * `GLASS` 那一档**两半都有**（批 63）：底下那面墙由 `desktopWallBlurRadius` 挂上模糊，
 * 这一层交回的是黑 @ 0.45 压暗 —— 两枚数逐字照对岸（`BlurBehindDp = 18f` / `BACKDROP_SCRIM_ALPHA = 0.45f`，
 * 判据 ⑨ 钉着，因为对岸那两颗常量一颗 `private`、一颗 `protected`，桌面只能重述）。
 *
 * ## 这一格补的是什么
 *
 * `galleryBackdrop` 从前在屏上是**点名缺席**的那一枚：那句写的是「桌面的浮层没有那一层背景可以换」。
 * 而批 55 之后大图那一屏确实有一层底（`.background(DesktopTheme.WindowBackground)`）—— 有那一层，
 * 只是没人接它。批 61 接上色（四档全真、GLASS 那时画实心底并明说），批 62 量到应用内模糊画得出来，
 * 批 63 把它接成真的糊 ⇒ 屏上那句标签与 caption 也**同一批**改掉（写"糊"而画实心底、
 * 或写"实心底"而真糊了，是同一颗缺陷的两个方向）。
 *
 * ⚠️ 对岸那一档走的是**窗口级** `FLAG_BLUR_BEHIND`，那条路在桌面判负（S0-6：材质没画出来）。
 * 桌面走的是应用内那一层，它画不画得出来由批 62 那颗探针量过（`BlurRenderProbe`）。
 */
class DesktopViewerBackdropTest {

    private fun tmp(tag: String) = File(System.getProperty("java.io.tmpdir"), "r1f-bd-$tag-${System.nanoTime()}")

    // ① 四档各一色：三档吃枚举自带的 argb，GLASS 交回**压暗那一层**（底下那面墙真被糊）。
    @Test
    fun `四档各算各的色`() {
        assertEquals("纯黑那档：" + Color.Black, Color.Black, desktopBackdropColor(GalleryViewerBackdrop.BLACK))
        assertEquals("纯白那档：", Color.White, desktopBackdropColor(GalleryViewerBackdrop.WHITE))
        assertEquals(
            "深灰那档取全仓现成那一枚口径 0xFF121212：",
            Color(0xFF121212),
            desktopBackdropColor(GalleryViewerBackdrop.DARK_GRAY),
        )
        assertEquals(
            "GLASS 那档交回压暗那一层（黑 @ 0.45，照对岸 `BACKDROP_SCRIM_ALPHA`）—— 底下那面墙由 `desktopWallBlurRadius` 糊：",
            Color.Red.copy(alpha = DesktopBackdropScrimAlpha),
            desktopBackdropColor(GalleryViewerBackdrop.GLASS).copy(red = 1f, green = 0f, blue = 0f),
        )
        val colors = GalleryViewerBackdrop.entries.map { desktopBackdropColor(it) }.distinct()
        assertEquals("四档里不许有两档画成同一个色（含 alpha）：" + colors, 4, colors.size)
    }

    // ② 三档的不透明色不许自己写死在桌面：吃的是枚举那枚 argb（对岸改数，桌面跟着改）。
    @Test
    fun `三档的色来自枚举而不是桌面另造`() {
        GalleryViewerBackdrop.entries.filter { it.argb != null }.forEach { 档 ->
            assertEquals(
                "「$档」那一档的色与枚举带的 argb 对不上：" + 档.argb,
                Color(档.argb!!),
                desktopBackdropColor(档),
            )
        }
        val file = DesktopSourceTree.desktopUiSource("DesktopViewerBackdropPolicy.kt")
        val code = DesktopSourceTree.codeText(file)
        listOf("0xFF000000", "0xFFFFFFFF", "0xFF121212").forEach { 字面 ->
            assertFalse("桌面这颗件里写死了色值（该吃枚举的 argb）：$字面", code.contains(字面))
        }
    }

    // ③ 四档标签：都在、各不同；GLASS 那枚现在必须说"糊"，而**旧那句"不透明底"不许再留在屏上**。
    @Test
    fun `四档标签齐而 GLASS 那枚说的是今天画的东西`() {
        val labels = DesktopGallerySettingsSpec.backdropTierLabels
        assertEquals("四档都要摆：" + labels.map { it.second }, 4, labels.size)
        assertEquals("四档不许撞名：" + labels.map { it.second }, labels.size, labels.map { it.second }.distinct().size)
        assertEquals(
            "标签覆盖的就是那枚枚举的全部：" + labels.map { it.first },
            GalleryViewerBackdrop.entries.toList(),
            labels.map { it.first },
        )
        val glass = labels.first { it.first == GalleryViewerBackdrop.GLASS }.second
        assertTrue("GLASS 那枚要说清它画的是什么（批 63 起它是真糊）：" + glass, glass.contains("模糊"))
        // 反方向那一条更要防：文案说"还没有 X"而 X 已落地（本仓那一族活了四批）。
        assertFalse("那一档已经真糊了，屏上还写着不透明底：" + glass, glass.contains("不透明底"))
    }

    // ④ 那一行已从"点名缺席"搬进"屏上能改"，而旧那句点名文案整棵工程都不许再留。
    @Test
    fun `那一枚已从点名搬进能改而旧句子下架`() {
        val spec = DesktopSourceTree.desktopUiSource("DesktopGallerySettingsSpec.kt")
        val code = DesktopSourceTree.codeText(spec)
        assertTrue(
            "editable 里没有 galleryBackdrop（还在点名那一栏）",
            Regex("""editable[\s\S]*?galleryBackdrop""").containsMatchIn(code),
        )
        assertFalse("notOnScreen 里还留着 galleryBackdrop：" + code, Regex("""notOnScreen[\s\S]*?galleryBackdrop""").containsMatchIn(code))
        val stale = DesktopSourceTree.desktopMainSources().filter {
            DesktopSourceTree.codeText(it).contains("桌面的浮层没有那一层背景可以换")
        }
        assertEquals("那句点名文案已经过期，还留在：" + stale.map { it.name }, emptyList<String>(), stale.map { it.name })
    }

    // ⑤ 写口真跑：改完立刻动，而且落盘留得住（与批 52 那枚同一条）。
    @Test
    fun `写口两步都真`() {
        val dir = tmp("write")
        val p = DesktopGalleryPreferences(FakePaths(dir))
        assertEquals("默认档", GalleryViewerBackdrop.GLASS, p.galleryBackdrop.value)
        p.setGalleryBackdrop(GalleryViewerBackdrop.DARK_GRAY)
        assertEquals("推流那一跳：" + p.galleryBackdrop.value, GalleryViewerBackdrop.DARK_GRAY, p.galleryBackdrop.value)
        val again = DesktopGalleryPreferences(FakePaths(dir))
        assertEquals("落盘那一跳（重启还认得）", GalleryViewerBackdrop.DARK_GRAY, again.galleryBackdrop.value)
    }

    // ⑥ 接线：那一层吃的是这颗件，且不许再直接吃主题底。
    @Test
    fun `那一层只从这颗件拿色`() {
        val viewer = DesktopSourceTree.desktopUiSource("DesktopGalleryViewer.kt")
        assertEquals("那一层没吃共用那颗件", 1, DesktopSourceTree.codeOccurrences(viewer, "desktopBackdropColor("))
        assertEquals(
            "那一层还在直接吃主题底（绕过了那枚偏好）",
            0,
            DesktopSourceTree.codeOccurrences(viewer, ".background(DesktopTheme.WindowBackground)"),
        )
        // 对照组：这颗件确实被扫到了，且它真画了那一层。
        assertTrue("对照组失效：那颗件里没有 .background(", DesktopSourceTree.codeText(viewer).contains(".background("))
    }

    /**
     * ⑦ 集成方递进去的必须是**那条流**，不是一枚常量。
     *
     * ⑥ 只钉了"那一层吃这颗件"，管不到**谁把档递给这颗件**：调用点写 `backdrop = GalleryViewerBackdrop.GLASS`
     * 时⑥ 全绿，而屏上第八行当场变回假开关（按下去存盘成功、那一层永远画同一色）—— 本仓最忌的那一颗。
     * 与批 52 钉 `setGalleryAnimated(GalleryAnimatedMode.` 那一条同族：**吃用户点的那一枚**这句话
     * 只能靠"调用点念的是 collectAsState 出来的那枚变量"来钉，而变量名会被重构改掉，所以两头各一钉。
     */
    @Test
    fun `集成方递进去的是那条流而不是常量`() {
        val appFile = DesktopSourceTree.desktopUiSource("VeneraDesktopApp.kt")
        val viewerFile = DesktopSourceTree.desktopUiSource("DesktopGalleryViewer.kt")
        val app = DesktopSourceTree.codeText(appFile)
        assertEquals(
            "集成方没把那条流收进组合（收一次都不许，何况递进去）",
            1,
            DesktopSourceTree.codeOccurrences(appFile, "settings.galleryBackdrop.collectAsState()"),
        )
        // 两头钉成**一根线**：从收流那一句把变量名抽出来，再要求调用点递的正是那一名。
        // 写死 `backdrop = backdropTier,` 也行，但改名重构会把判据一起改红 —— 而"名字换了、线断了"
        // 与"名字没换、线断了"是同一件事，抽名字才是这条判据真正想说的话。
        val seeded = Regex("""val (\w+) by settings\.galleryBackdrop\.collectAsState\(\)""").find(app)
            ?: throw AssertionError("收流那一句的形状不是 `val X by settings.galleryBackdrop.collectAsState()`")
        assertEquals(
            "调用点递的不是收来的那一枚（`backdrop = ${seeded.groupValues[1]},` 只许出现一次）",
            1,
            DesktopSourceTree.codeOccurrences(appFile, "backdrop = ${seeded.groupValues[1]},"),
        )
        // 负向：调用点写死一枚档 —— 存在性判式抓不到，只有这一条抓得到。
        assertEquals(
            "调用点写死了那一枚档（屏上第八行当场变回假开关）",
            0,
            DesktopSourceTree.codeOccurrences(appFile, "backdrop = GalleryViewerBackdrop."),
        )
        // 反向钉参数面：那一枚必须是**必填**（带默认值时漏传不报错，而漏传的形状正是"改了不动"）。
        val viewer = DesktopSourceTree.codeText(viewerFile)
        assertTrue("那一枚形参不见了（签名上没 `backdrop: GalleryViewerBackdrop`）", viewer.contains("backdrop: GalleryViewerBackdrop"))
        assertEquals(
            "那一枚形参带了默认值（漏传不报错，而集成方一旦漏传这一屏就偷偷画 GLASS）",
            0,
            DesktopSourceTree.codeOccurrences(viewerFile, "backdrop: GalleryViewerBackdrop ="),
        )
        // 对照组：这两颗文件确实被扫到了（读空时上面五条全恒真）。
        assertTrue("对照组失效：集成方那颗没读到 collectAsState", app.contains("collectAsState()"))
        assertTrue("对照组失效：viewer 那颗没读到 .background(", viewer.contains(".background("))
    }

    /**
     * ⑧ 墙该不该糊：只有「GLASS 且大图那一屏开着」才糊（批 63）。
     *
     * 另三档画的是**不透明**那一层，糊了也看不见 ⇒ 白烧一层 GPU。
     * 而浮层开着但大图没开时也不糊：那一档在对岸属于大图页的幕布，不是浮层的。
     */
    @Test
    fun 只有那一档开着大图才糊墙() {
        assertEquals("GLASS 且大图开着才糊，半径照对岸 18dp", 18.dp, desktopWallBlurRadius(GalleryViewerBackdrop.GLASS, viewerOpen = true))
        GalleryViewerBackdrop.entries.filter { it != GalleryViewerBackdrop.GLASS }.forEach { 档 ->
            assertEquals("「$档」那一档不许糊墙（画的是不透明底，糊了看不见）", 0.dp, desktopWallBlurRadius(档, viewerOpen = true))
        }
        assertEquals("大图没开着时不许糊（墙上正要点卡，糊一层是白烧）", 0.dp, desktopWallBlurRadius(GalleryViewerBackdrop.GLASS, viewerOpen = false))
    }

    /**
     * ⑨ 两枚数照对岸逐字回核，且桌面这边**各只写一次**。
     *
     * 对岸那两枚常量都是 private / protected ⇒ 桌面编译面读不到，只能重述；
     * 那就要有一条尺子把"重述的值 == 对岸的值"钉住（口径账的规矩：引对岸原值，不另造数）。
     */
    @Test
    fun 半径与压暗两枚数逐字照对岸() {
        val base = DesktopSourceTree.codeText(DesktopSourceTree.androidSource("VeneraSubActivityBase.kt"))
        val post = DesktopSourceTree.codeText(DesktopSourceTree.androidSource("gallery/ui/GalleryPostScreen.kt"))
        assertTrue("对岸的半径口径变了（不再是 18f）—— 桌面要跟着改，别自己拿主意", base.contains("BlurBehindDp = 18f"))
        assertTrue("对岸的压暗口径变了（不再是 0.45f）", post.contains("BACKDROP_SCRIM_ALPHA = 0.45f"))
        val policy = DesktopSourceTree.desktopUiSource("DesktopViewerBackdropPolicy.kt")
        assertEquals("半径 18 在桌面这颗件里出现了多处（口径只许一处写）", 1, DesktopSourceTree.codeOccurrences(policy, "= 18"))
        assertEquals("压暗 0.45 在桌面这颗件里出现了多处", 1, DesktopSourceTree.codeOccurrences(policy, "= 0.45f"))
    }

    /**
     * ⑩ 接线：集成方吃的是那颗判据，而**不糊那一档不许留一层**。
     *
     * `Modifier.blur(0.dp)` 在桌面上会不会仍挂一枚 graphicsLayer 没有量过 ⇒ 不糊就走 `Modifier`（空的那一枚），
     * 把"零成本"这件事交给形状而不是交给库的善意。
     */
    @Test
    fun 集成方把糊接在墙那棵上而零档不留层() {
        val appFile = DesktopSourceTree.desktopUiSource("VeneraDesktopApp.kt")
        assertEquals("糊那颗判据在集成方只许被吃一次", 1, DesktopSourceTree.codeOccurrences(appFile, "desktopWallBlurRadius("))
        assertEquals("blur 只许一处调用点", 1, DesktopSourceTree.codeOccurrences(appFile, "Modifier.blur(radius = wallBlur)"))
        assertEquals("没有「零档就不挂层」那一道闸（不糊的档会白挂一层）", 1, DesktopSourceTree.codeOccurrences(appFile, "if (wallBlur > 0.dp)"))
        // 对照组：钉的是**墙那一棵**，不是大图自己那层（后者吃的是颜色那颗件，见 ⑥）。
        val code = DesktopSourceTree.codeText(appFile)
        assertTrue("糊没接在墙那棵 Column 上：" + code.substringAfter("val wallBlur").take(220),
            Regex("""wallBlur[\s\S]{0,260}background\(DesktopTheme\.WindowBackground\)""").containsMatchIn(code))
    }
}
