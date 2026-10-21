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
    /** ⑧ 浮层要能被**无人值守**摆出来（否则这两批的观感永远拍不到图）。 */
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
}
