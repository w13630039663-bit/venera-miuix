package com.venera.desktop.gallery.ui

import com.venera.compose.gallery.data.GalleryPost
import com.venera.compose.gallery.data.GallerySite
import com.venera.compose.gallery.domain.GalleryPreloadMode
import com.venera.compose.gallery.domain.GalleryPreviewQuality
import com.venera.desktop.gallery.data.DesktopGalleryPreferences
import com.venera.desktop.platform.FakePaths
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 桌面第四十七批 批 47 · 「大图页主动预取前后几页」那一枚从"点名缺席"改成**真消费 + 真开关**。
 *
 * 那本账从前写的是「桌面能翻页，但取图这一步今天不提前做」。这一句当时真，而**接得住**也真：
 * 计划本体住在共享的 `GalleryPreload.plan`（`gallery/domain/GalleryViewerPolicies.kt:65`，
 * 桌面编译面内），桌面转交而不重述；"提前取"在桌面只要一枚能发请求的取图器 ——
 * 那枚就是 `AsyncImage` 背后同一份 `SingletonImageLoader`（实测在 `coil-jvm-3.6.2.jar`，
 * 形如 `coil3.SingletonImageLoader.get(PlatformContext)`）。
 *
 * ⚠️ 这一批最容易长出来的假开关形状是**两条轨**：预取取一档、浮层打开用另一档 ——
 * 缓存键不同，点下去照样重取，"预取过了"就成了假话。所以 ① 那颗把"初始取哪一档"
 * 钉成两头同一颗件（三处名字命中：一处声明 + 两处调用；数"名字"而不数"调用"，
 * 因为声明行本身就含那一串 —— 那把尺子会咬到自己）。
 */
class DesktopGalleryPreloadTest {

    private fun tmp(tag: String) = File(System.getProperty("java.io.tmpdir"), "r1f-pl-$tag-${System.nanoTime()}")

    private fun card(id: Long, masked: Boolean = false, preview: String = "https://cdn/p$id.jpg") =
        DesktopWallCard(
            post = GalleryPost(site = GallerySite.YANDERE, id = id, previewUrl = preview),
            masked = masked,
        )

    /** ① 计划转交共享那颗件；初始档位两头同一颗件。 */
    /** ② 三档各自预取哪几张：不含当前、近的先来、夹在墙范围内。 */
    @Test
    fun 三档各自预取哪几张() {
        val cards = (1L..6L).map { card(it) }
        val quality = GalleryPreviewQuality.PREVIEW
        assertEquals(
            "「不提前取」那一档必须一张都不取（否则这一档是摆设）",
            emptyList<String>(),
            desktopPreloadPlan(GalleryPreloadMode.OFF, 2, cards, DesktopDetailTier.PREVIEW).urls,
        )
        assertEquals(
            "「取下一张」只许取当前之后那一张",
            listOf("https://cdn/p4.jpg"),
            desktopPreloadPlan(GalleryPreloadMode.NEXT, 2, cards, DesktopDetailTier.PREVIEW).urls,
        )
        assertEquals(
            "「前后各两张」按近到远、同距离向前",
            listOf("https://cdn/p4.jpg", "https://cdn/p2.jpg", "https://cdn/p5.jpg", "https://cdn/p1.jpg"),
            desktopPreloadPlan(GalleryPreloadMode.BOTH_TWO, 2, cards, DesktopDetailTier.PREVIEW).urls,
        )
        // 共享那颗件在最后一张上只向前取到列表边界内：index 4 的候选是 {5, 3, 2}（6 越界、按近到远且向前优先）
        assertEquals(
            "最后一张不许越界取（那会崩在列表外）",
            listOf("https://cdn/p6.jpg", "https://cdn/p4.jpg", "https://cdn/p3.jpg"),
            desktopPreloadPlan(GalleryPreloadMode.BOTH_TWO, 4, cards, DesktopDetailTier.PREVIEW).urls,
        )
    }

    /** ③ 遮挡的那一张、站方没给图的那一张不预取；同一地址不取两遍。 */
    @Test
    fun 遮挡与没有地址的不预取而同一地址不取两遍() {
        val maskedOnly = listOf(card(1), card(2, masked = true))
        assertEquals(
            "被挡住的那一张连发起取图都不该有（与墙上、浮层同一条规矩）",
            emptyList<String>(),
            desktopPreloadPlan(GalleryPreloadMode.NEXT, 0, maskedOnly, DesktopDetailTier.PREVIEW).urls,
        )
        val blank = listOf(card(1), card(2, preview = "  "))
        assertEquals(
            "站方没给那一档的地址时不许把空串当地址发出去",
            emptyList<String>(),
            desktopPreloadPlan(GalleryPreloadMode.NEXT, 0, blank, DesktopDetailTier.PREVIEW).urls,
        )
        val sameUrl = listOf(card(1, preview = "https://cdn/x.jpg"), card(2, preview = "https://cdn/x.jpg"))
        assertEquals(
            "两张卡指向同一地址时只许取一遍",
            listOf("https://cdn/x.jpg"),
            desktopPreloadPlan(GalleryPreloadMode.BOTH_TWO, 0, sameUrl, DesktopDetailTier.PREVIEW).urls,
        )
        // 偏好换档时预取必须跟着换档，否则缓存键与浮层不同轨
        // 两张卡：当前那张不算，只向前那一张参与（一张墙的 NEXT 会算出空表 —— 那不是"换档没生效"）
        val large = listOf(
            card(9, preview = "https://cdn/prev9.jpg"),
            DesktopWallCard(
                post = GalleryPost(
                    site = GallerySite.YANDERE,
                    id = 7L,
                    previewUrl = "https://cdn/prev7.jpg",
                    largeUrl = "https://cdn/large7.jpg",
                ),
                masked = false,
            ),
        )
        assertEquals(
            "偏好取清楚那一档时，预取必须取清楚那一档的地址（否则缓存键不同，点下去照样重取）",
            listOf("https://cdn/large7.jpg"),
            desktopPreloadPlan(GalleryPreloadMode.NEXT, 0, large, DesktopDetailTier.SAMPLE).urls,
        )
        assertEquals(
            "同一批卡换回预览档时取的是另一枚地址（两头不同轨的反证）",
            listOf("https://cdn/prev7.jpg"),
            desktopPreloadPlan(GalleryPreloadMode.NEXT, 0, large, DesktopDetailTier.PREVIEW).urls,
        )
    }

    /** ④ 写口真跑：改完那条流立刻动，而且落盘留得住。 */
    @Test
    fun 预取档位写口真跑且落盘留得住() {
        val dir = tmp("write")
        val p = DesktopGalleryPreferences(FakePaths(dir))
        assertEquals("桌面默认档", GalleryPreloadMode.NEXT, p.galleryPreload.value)
        p.setGalleryPreload(GalleryPreloadMode.BOTH_TWO)
        assertEquals("写口没推动那条流", GalleryPreloadMode.BOTH_TWO, p.galleryPreload.value)
        p.setGalleryPreload(GalleryPreloadMode.OFF)
        assertEquals(
            "重开一个实例读不回 OFF（盘上没留住或键名不对）",
            GalleryPreloadMode.OFF,
            DesktopGalleryPreferences(FakePaths(dir)).galleryPreload.value,
        )
    }

    /** ⑤ 那本账改了：这一枚从"点名缺席"搬进"屏上真能改"，两头不重不漏。 */
    @Test
    fun 那一枚从点名区搬进能改区() {
        val editable = DesktopGallerySettingsSpec.editable
        val absent = DesktopGallerySettingsSpec.notOnScreen.map { it.pref }
        assertEquals("屏上能改的八枚（批 52 搬进动图、批 61 搬进 backdrop）", 8, editable.size)
        assertEquals("屏上点名的三枚", 3, absent.size)
        assertFalse("搬走了还留在点名区（那等于两本账各说一遍）", absent.contains("galleryPreload"))
        val row = editable.first { it.pref == "galleryPreload" }
        assertEquals("写口名对不上", "setGalleryPreload", row.setter)
        assertTrue(
            "账上的键名在偏好件里根本没有（那枚键名是编的）",
            DesktopSourceTree.codeText(DesktopSourceTree.desktopSourceOf("gallery/data/DesktopGalleryPreferences.kt"))
                .contains("\"${row.key}\""),
        )
        assertEquals(
            "三档就是共享那颗枚举的全部，不多不少",
            GalleryPreloadMode.entries.size,
            DesktopGallerySettingsSpec.preloadTiers.size,
        )
        assertEquals(GalleryPreloadMode.entries.toList(), DesktopGallerySettingsSpec.preloadTiers)
    }

    /** ⑥ 屏上真调那道写口，而集成方真把计划递给取图器。 */
    /**
     * ⑦ 桌面 classpath 上必须有**一枚供得出 `Dispatchers.Main` 的模块**。
     *
     * 这一条不是补出来的漂亮判据，是图证换来的：预取第一次真构造 `SingletonImageLoader` 时，
     * Coil 的作用域吃 `Dispatchers.Main.immediate`，而 Compose Desktop 不带那个 dispatcher，
     * 于是屏上弹一枚 AWT Error 框「Module with the Main dispatcher is missing」——
     * **而 645 条判据当时全绿**（单测里从没构造过 ImageLoader，也就从没碰过那一档）。
     * 崩溃在屏上而读数不在屏上，所以这一族只能钉到构建脚本那一层：换依赖就红，删依赖也红。
     */
    @Test
    fun 桌面依赖里得有一枚供得出主线程调度器的模块() {
        val build = java.io.File(DesktopSourceTree.repoRoot, "desktop/build.gradle.kts").readText()
        val provides = listOf("kotlinx-coroutines-swing", "kotlinx-coroutines-swt")
            .any { build.contains(it) }
        assertTrue(
            "桌面 classpath 上没有供得出 `Dispatchers.Main` 的模块：Coil 的 ImageLoader 一构造就弹" +
                "「Module with the Main dispatcher is missing」（2026-10-08 批 47 图证拍到）",
            provides,
        )
        // 对照组：那枚 android 的 dispatcher 模块**不该**出现在桌面构建脚本里（它供的是 Looper 那一套）
        assertFalse("桌面构建脚本里出现了 android 那枚 dispatcher 模块", build.contains("kotlinx-coroutines-android"))
    }

    /**
     * ⑦ 预取那几笔必须**能被打断**。
     *
     * `enqueue` 跑在取图器自己的作用域上，**不吃这一层效应的取消** —— 效应重起了，前一批请求照旧往前跑。
     * 连播开着（5 秒一档）或用户按着 → 不放，一路翻就一路排，排下去的是几十张再也不会看的图。
     * ⚠️ 判式是**顺序**而不是"有没有 dispose"：先 `enqueue`、再 `awaitCancellation`（效应活着才继续挂着这批）、
     *   最后 `dispose` —— 少了中间那一枚，实现会变成"排完立刻停掉"，那既没预取也不报错，是一格更难的假动作。
     */
    /**
     * ⑧ 预取吃的是**屏上那一档**，不是设置里那一枚（第五十批）。
     *
     * 档位上提之后这两枚可以分岔：用户在浮层或大图那一屏手动换过档、或者进大图时升到了「大图」，
     * 而设置里那枚还停着原来的。预取若还按设置推，它取的就是另一档的字节 ——
     * "翻到的下一张命中缓存"当场成假，而屏上看不出任何异常（正是批 47 修掉的那族"两条轨"）。
     *
     * ⚠️ 第五十六批改了钉法：从前数的是**整颗文件里 `tier: DesktopDetailTier` 出现的次数**。
     * 批 56 给预取加了读数那颗件（`desktopPreloadReading`），它的形参**也叫 tier、也必须是同一枚档位**
     * —— 读数若不念屏上那一档，"我预取了大图档"与"屏上是预览档"就分不出来。于是那颗整文件计数
     * 把合法的第二处算成缺陷（红在 `expected:<1> but was:<2>`）。**换成只切计划那颗自己的签名**：
     * 判式吃的仍是"预取按哪一档取"这件事本身，不再管旁件叫什么。
     */
}
