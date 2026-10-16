package com.venera.desktop.gallery.ui

import com.venera.compose.gallery.data.GalleryPost
import com.venera.compose.gallery.data.GallerySite
import com.venera.compose.gallery.domain.GalleryPreviewQuality
import com.venera.compose.gallery.domain.GallerySettingsModel
import com.venera.desktop.ForensicFlags
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 桌面第十一批 **P1**：详情浮层能换档、能翻页。
 *
 * ## 这一批的前提是先量掉一条旧拍板
 *
 * 方案 §6 第 3 条批准的是"把三档取图算式从 `GalleryPostScreen.kt:1668-1735` 搬进 `gallery/domain`"。
 * 开工前去读了那颗区间：**能搬的那半早就在共享面上**（`GallerySettingsModel.wallUrl`，
 * 视频只吃静帧、`LARGE` 退 `previewUrl`、不给空地址三条判据都在它里面，桌面第五批起就在吃它），
 * 搬不动的那半（Coil `ImageRequest` + 显式缓存键 + 解码上限）是 Android 类型，而桌面只要一个地址串。
 * ⇒ 本批**不动 `app/`、不开冻结豁免**，只补"第三档的地址"（一颗字段，没有判据可漂）。
 * 全过程记在 `docs/rounds/desktop-detail-panel-plan-2026-10-04.md` §8。
 *
 * ## 于是"不许重述判据"这件事怎么钉
 *
 * 面板的三档**逐字转交**那颗共享件，所以桌面这一侧不许再出现"视频取哪一枚地址"的算式
 * （判据 ①）。⚠️ 禁的是 `videoPosterUrl` 那一族**算式**，不是 `isVideo` 这个名字 ——
 * "视频没有原图档可看"是另一条判据（那一档按下去只会解不出来），它必须能在桌面表达。
 */
class DesktopGalleryDetailP1Test {

    private fun post(
        ext: String = "jpg",
        preview: String = "https://p/1.jpg",
        large: String = "https://l/1.jpg",
        file: String = "https://f/1.jpg",
    ) = GalleryPost(
        site = GallerySite.YANDERE,
        id = 7L,
        tags = "t",
        previewUrl = preview,
        largeUrl = large,
        fileUrl = file,
        fileExt = ext,
    )

    /** ① 三档各取哪个地址：两档**转交**共享那颗件，第三档才是桌面自己的一枚字段。 */
    @Test
    fun 三档地址两档转交一档自取() {
        val plain = post()
        assertEquals(
            "预览档必须逐字等于共享 `wallUrl(PREVIEW)`",
            GallerySettingsModel.wallUrl(plain, GalleryPreviewQuality.PREVIEW),
            desktopDetailTierUrl(plain, DesktopDetailTier.PREVIEW),
        )
        assertEquals(
            "大图档必须逐字等于共享 `wallUrl(LARGE)`（含「取不到退 preview」那条）",
            GallerySettingsModel.wallUrl(plain, GalleryPreviewQuality.LARGE),
            desktopDetailTierUrl(plain, DesktopDetailTier.SAMPLE),
        )
        assertEquals("原图档就是站方那一枚 `file_url`，桌面不另算", "https://f/1.jpg", desktopDetailTierUrl(plain, DesktopDetailTier.ORIGINAL))

        // 对照组：共享件那条"取不到 LARGE 退 preview"真的透过来了。
        val noLarge = post(large = "")
        assertEquals(
            "对照组失效：大图档没走共享那颗回退（桌面自己写死 largeUrl 就会给空地址 = 灰块）",
            "https://p/1.jpg",
            desktopDetailTierUrl(noLarge, DesktopDetailTier.SAMPLE),
        )
    }

    /** ② 哪几档**该摆**：遮挡档一档不摆，视频与原图空串各撤一档。 */
    @Test
    fun 摆不摆那一枚按真拿不拿得到决定() {
        assertEquals(
            "被挡住的那一张：三档全不摆（摆了就是一枚按下去只会换灰块的开关）",
            emptyList<DesktopDetailTier>(),
            desktopDetailTiers(post(), masked = true),
        )
        assertEquals("普通一条是三档", 3, desktopDetailTiers(post(), masked = false).size)

        val video = post(ext = "mp4")
        assertFalse(
            "视频条目不许摆「原图」那一枚：那一档的地址是原片 mp4，解码器解不出来（站方没有更小的动图/视频转码档）",
            desktopDetailTiers(video, masked = false).contains(DesktopDetailTier.ORIGINAL),
        )
        assertEquals("视频仍然有预览与大图两档（都是静帧，判据在共享那颗里）", 2, desktopDetailTiers(video, masked = false).size)

        val noFile = post(file = "")
        assertFalse(
            "站方没给 file_url 时也不许摆「原图」",
            desktopDetailTiers(noFile, masked = false).contains(DesktopDetailTier.ORIGINAL),
        )
        // 对照组：档位序**稳定**（预览 → 大图 → 原图），换序会让那排分段控件自己跳。
        assertEquals(
            listOf(DesktopDetailTier.PREVIEW, DesktopDetailTier.SAMPLE, DesktopDetailTier.ORIGINAL),
            desktopDetailTiers(post(), masked = false),
        )
    }

    /** ③ 步进：钳制而不是回绕。 */
    @Test
    fun 步进钳在两端而不回绕() {
        assertEquals("第一张再往回还是第一张", 0, desktopDetailStep(0, -1, 40))
        assertEquals("最后一张再往前还是最后一张", 39, desktopDetailStep(39, 1, 40))
        assertEquals("中间照常走", 12, desktopDetailStep(11, 1, 40))
        // ⚠️ 不许回绕：从 40 跳回 1 会被读成"这面墙自己重排了"，而它没有。
        assertFalse("回绕了：第一张往回跳到了末尾", desktopDetailStep(0, -1, 40) == 39)
        assertEquals("只有一张时怎么走都是它", 0, desktopDetailStep(0, 1, 1))
    }

    /** ④ 两枚步进各自的可点档 —— 「到底了还摆一枚按下去没反应的按钮」是假开关。 */
    @Test
    fun 两端各撤一枚而单张整排不摆() {
        assertEquals(true to true, desktopDetailCanStep(5, 40))
        assertEquals(false to true, desktopDetailCanStep(0, 40))
        assertEquals(true to false, desktopDetailCanStep(39, 40))
        assertEquals("一张都没有 ⇒ 两枚都不摆（而不是崩在 -1 上）", false to false, desktopDetailCanStep(0, 0))
        assertEquals("只有一张 ⇒ 整排不摆", false to false, desktopDetailCanStep(0, 1))
    }

    /** ⑤ 墙往下交的是**整批 + 序号**，而五处调用点一字没漏。 */
    @Test
    fun 墙交出整批而选中态仍只有一颗() {
        val tile = DesktopSourceTree.codeText(DesktopSourceTree.desktopUiSource("DesktopGalleryPostTile.kt"))
        assertTrue("墙没改成交整批（翻页就没有数据源）", tile.contains("onPick: (DesktopWallSelection) -> Unit"))
        assertTrue("那颗墙没把序号算出来", tile.contains("DesktopWallSelection("))
        listOf(
            "DesktopDailyPane.kt",
            "DesktopGalleryFavoritesPane.kt",
            "DesktopGallerySearchPane.kt",
            "DesktopForYouPane.kt",
            "DesktopLatestPane.kt",
        ).forEach { name ->
            val code = DesktopSourceTree.codeText(DesktopSourceTree.desktopUiSource(name))
            assertTrue("「$name」那一屏没传 onPick", code.contains("onPick ="))
        }
        val app = DesktopSourceTree.codeText(DesktopSourceTree.desktopUiSource("VeneraDesktopApp.kt"))
        assertEquals("选中态声明处必须恰好一处", 1, Regex("var selected").findAll(app).count())
        assertFalse("P0 那棵旧名字还在（换成整批之后它就是死代码）", app.contains("selectedCard"))
    }

    /** ⑥ 桌面不许重述"视频取哪一枚地址"那族算式（它归共享那颗件）。 */
    @Test
    fun 静帧算式不许在桌面重述() {
        val offenders = listOf("DesktopGalleryDetailPanel.kt", "DesktopGalleryDetailPolicy.kt").flatMap { name ->
            DesktopSourceTree.codeLinesOf(DesktopSourceTree.desktopUiSource(name))
                .filter { it.contains("videoPosterUrl") }
                .map { "$name：$it" }
        }
        assertTrue(
            "桌面自己算了一遍底图地址（两端会漂，而漂的表现是「某一档显示的图不一样」）：" + offenders.joinToString(" ｜ "),
            offenders.isEmpty(),
        )
        // 对照组：转交的那两颗名字真在（读空了上面几条恒真）。
        val policy = DesktopSourceTree.codeText(DesktopSourceTree.desktopUiSource("DesktopGalleryDetailPolicy.kt"))
        listOf("GallerySettingsModel.wallUrl", "GalleryPreviewQuality.PREVIEW", "GalleryPreviewQuality.LARGE")
            .forEach { token -> assertTrue("对照组失效：策略件里没有「$token」", policy.contains(token)) }
    }

    /** ⑦ 换档与翻页都不许是"看着能点其实不是"：档位与页码都要在屏上念出来。 */
    @Test
    fun 当前档与当前张都要念在屏上() {
        val panel = DesktopSourceTree.codeText(DesktopSourceTree.desktopUiSource("DesktopGalleryDetailPanel.kt"))
        // ⚠️ 批 92 改判：那三串屏名搬进了 `desktopDetailTierLabel`（取证读数 `D_档位种子` 要与屏上同一颗生产者），
        // 面板里只剩一行委托。从前钉的是"面板里有那三串"，搬家后那条恒红（新坑 ㊞ 的同族），改钉**委托那一行**。
        val labels = DesktopSourceTree.codeText(DesktopSourceTree.desktopUiSource("DesktopGalleryDetailPolicy.kt"))
        listOf("预览", "大图", "原图").forEach { label ->
            assertTrue("档排上少了「$label」那一枚（现在住在 desktopDetailTierLabel）", labels.contains("\"$label\""))
        }
        assertTrue("面板不再委托那颗共用的屏名函数（那就是第二份词，改名会有一处指错地方）", panel.contains("desktopDetailTierLabel(this)"))
        assertTrue("页码读数不在屏上（翻页翻到哪了没人知道）", panel.contains("张"))
        assertTrue("步进没接 onStep", panel.contains("onStep"))
        assertTrue("换档没接 tier 状态", panel.contains("DesktopDetailTier"))
        // 空 lambda 那一族（P0 判据 ⑦ 同一条）在 P1 加的两枚按钮上同样不许出现。
        listOf("onClick = { }", "onClick = {}").forEach { empty ->
            assertTrue("面板上出现了空 lambda「$empty」", panel.contains(empty).not())
        }
    }

    /** ⑧ 浮层要能被**无人值守**摆出来（否则这两批的观感永远拍不到图）。 */
    @Test
    fun 浮层能被取证参数摆出来() {
        // 真跑：登记与"没被报成拼错"是同一条链的两端，只断言一端会被清空 KNOWN 绕过。
        assertTrue("`--detail` 没登记进 KNOWN —— 那它每次都会被报成拼错的参数", ForensicFlags.KNOWN.contains("--detail"))
        assertEquals("`--detail` 被报成了未识别参数", emptyList<String>(), ForensicFlags.unknown(listOf("--detail")))
        assertEquals(
            "`--detail=0` 必须是**关**而不是开（旧那枚 `args.contains` 的写法就是关不掉的开关）",
            false,
            ForensicFlags.state(listOf("--detail=0"), "--detail"),
        )
        assertEquals("`--detail` 只给名字不带值 = 开", true, ForensicFlags.state(listOf("--detail"), "--detail"))

        // 接线形状：参数一路到那一面墙，中间每一段都不许带默认值。
        val app = DesktopSourceTree.codeText(DesktopSourceTree.desktopUiSource("VeneraDesktopApp.kt"))
        assertTrue("应用根没收 `startDetail`", app.contains("startDetail: Boolean"))
        assertFalse(
            "`startDetail` 带了默认值 —— 漏传不报错，而漏传的形状正是「取证参数打进去了但屏上没反应」",
            Regex("startDetail: Boolean\\s*=").containsMatchIn(app),
        )
        assertTrue("应用根没把它 provide 出去", app.contains("LocalDesktopWallSeedFirst provides"))
        val tile = DesktopSourceTree.codeText(DesktopSourceTree.desktopUiSource("DesktopGalleryPostTile.kt"))
        assertTrue("那面墙没消费种子（provide 了没人读 = 假参数）", tile.contains("LocalDesktopWallSeedFirst.current"))
        assertTrue("种子没在算出卡之后才发（组合期直接点会把浮层开在空批上）", tile.contains("LaunchedEffect(wall.cards)"))
        // 集成那一侧：参数真从命令行读出来并传下去。
        val shell = DesktopSourceTree.codeText(
            File(DesktopSourceTree.repoRoot, "desktop/src/main/kotlin/com/venera/desktop/VeneraDesktop.kt"),
        )
        assertTrue("`--detail` 没在集成处读", shell.contains("\"--detail\""))
        assertTrue("读出来没传给应用根", shell.contains("startDetail ="))
    }

    /** ⑨ 不带那枚参数时，浮层**绝不自己开**（默认行为不许被动过）。 */
    @Test
    fun 默认那颗种子是关() {
        val panel = DesktopSourceTree.codeText(DesktopSourceTree.desktopUiSource("DesktopGalleryDetailPanel.kt"))
        assertTrue(
            "种子的默认值不是 false —— 那等于把「启动就弹一层浮层」变成桌面默认行为",
            panel.contains("compositionLocalOf<Boolean> { false }"),
        )
    }

    /** ⑩ 标签墙必须在**动作与信息表之后**（图证读出来的缺陷，见下面那句注释）。 */
    @Test
    fun 标签墙不许把动作与信息表挤出第一屏() {
        val panel = DesktopSourceTree.codeText(DesktopSourceTree.desktopUiSource("DesktopGalleryDetailPanel.kt"))
        val tags = panel.indexOf("post.tagList.forEach")
        val actions = panel.indexOf("复制链接")
        val table = panel.indexOf("desktopDetailInfoRows(")
        assertTrue("找不到标签墙 / 动作排 / 信息表（读空了下面几条恒真）", tags > 0 && actions > 0 && table > 0)
        assertTrue(
            "标签墙排在了动作前面 —— 2026-10-05 那张 `--detail` 帧上 Safebooru 一条有几十枚标签，" +
                "把三个动作与整张信息表全推到折叠线以下，而稿假设标签只有四五枚（它那里是 4 枚 + 一枚「+3」）。" +
                "本仓不靠截断标签解决（页脚那句写的是「按该站自己的返回原样展示」）⇒ 改成把它挪到最后",
            tags > actions && tags > table,
        )
    }
}
