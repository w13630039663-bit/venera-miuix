package com.venera.compose.gallery

import com.venera.compose.gallery.data.GalleryPost
import com.venera.compose.gallery.data.GallerySite
import com.venera.compose.gallery.domain.LEGACY_CARD_RATIO_MAX
import com.venera.compose.gallery.domain.LEGACY_CARD_RATIO_MIN
import com.venera.compose.gallery.domain.PIXIV_CARD_RATIO_MAX
import com.venera.compose.gallery.domain.PIXIV_CARD_RATIO_MIN
import com.venera.compose.gallery.domain.cardBoxRatio
import com.venera.compose.gallery.domain.cardBoxRatioClamped
import com.venera.compose.testsupport.RepoSources
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 卡片框比例那把尺（2026-10-20，主人「限制 pixiv 站点图片卡片的显示比例，
 * 要整张卡全部显示出来而不是被裁掉一点，这样能解决很多飞行动画乱变的问题」）。
 *
 * ## 这一族防的是什么
 *
 * 封面上是 `ContentScale.Crop`：**框比例 ≠ 图比例就裁掉一条**。而裁的代价不止"少看一点" ——
 * 离场起点是作品页那一页图块（不夹、图完整），落点是墙上那张卡（夹过），两端比例不同
 * ⇒ `flightFrame` 的几何必须变形成另一个比例，读起来就是"飞行动画乱变"。主人那句
 * "这样能解决很多飞行动画乱变"说的正是这条因果。
 *
 * ## 四件事各一把尺
 *
 * 1. **竖长条不裁**：pixiv 跨页那一档（0.27）落在新区间内 ⇒ 框 = 图 = 0.27，Crop 一刀不裁；
 * 2. **横宽图不裁**：跨页大图 3.0 也落在内 ⇒ 同上（**这一格是本轮主人追问"有考虑过横图吗"
 *    之后补上的**，前一版只算了竖的）；
 * 3. **极端才夹**：<0.2 或 >4.0 的仍然夹住（不然一条 10:1 长卷会把整列撑出屏幕）；
 * 4. **三站原样**：图库三站仍是 0.4~2.5 —— 主人这次点的是 pixiv 站点，三站的口径不动。
 *
 * 另有两笔负向：**不许把 Crop 改成 Fit**（那会让飞行算式的 cover 假设崩），
 * 以及**屏上不许再就地写夹取**（第二个旋钮就是第二处真相）。
 */
class GalleryCardAspectTest {

    /** 造一张带尺寸的作品卡：`w × h` 是站方报的原图那一对（`cardRatio` 优先吃它）。 */
    private fun post(site: GallerySite, w: Int, h: Int): GalleryPost = GalleryPost(
        site = site,
        id = 1L,
        author = "a",
        previewUrl = "https://example.invalid/p.jpg",
        width = w,
        height = h,
    )

    private val fallback = 0.75f

    @Test
    fun `竖向跨页那一档不再被裁`() {
        // pixiv 真机读数：跨页 `1080×4000` ⇒ 0.27。旧区间 0.4 会把它夹成 0.4（裁左右两条），
        // 新区间 0.2~4.0 容得下 ⇒ 框 = 图，Crop 一刀不裁。
        val p = post(GallerySite.PIXIV, 1080, 4000)
        assertEquals(0.27f, p.cardRatio, 5e-3f)
        assertEquals("框比例要等于图比例", p.cardRatio, cardBoxRatio(p, fallback), 1e-4f)
        assertFalse("这一档不该再被夹（夹了就是裁）", cardBoxRatioClamped(p, fallback))
    }

    @Test
    fun `横向大图那一档也不被裁`() {
        // 这一格是主人追问"有考虑过横图吗"之后补的：跨页大图 / 长条海报常到 2.5~3.2，
        // 旧区间 2.5 会把它夹成 2.5（裁上下两条），新区间容得下。
        val p = post(GallerySite.PIXIV, 3000, 1000)
        assertEquals(3f, p.cardRatio, 5e-3f)
        assertEquals("横图框比例也要等于图比例", p.cardRatio, cardBoxRatio(p, fallback), 1e-4f)
        assertFalse("横图这一档不该再被夹", cardBoxRatioClamped(p, fallback))
    }

    @Test
    fun `区间两端正好容得下 pixiv 两族的极端`() {
        // 竖向跨页实际跨度 0.25~0.5、横向 2.5~3.2 —— 两端都要容得下。
        val tall = post(GallerySite.PIXIV, 1, 4) // 0.25
        assertEquals(0.25f, cardBoxRatio(tall, fallback), 1e-4f)
        val wide = post(GallerySite.PIXIV, 16, 5) // 3.2
        assertEquals(3.2f, cardBoxRatio(wide, fallback), 1e-4f)
        // 区间常量本身也钉住：改它就是改"裁多少"的唯一旋钮。
        assertEquals(0.2f, PIXIV_CARD_RATIO_MIN, 1e-6f)
        assertEquals(4.0f, PIXIV_CARD_RATIO_MAX, 1e-6f)
    }

    @Test
    fun `极端长条仍然夹住`() {
        // 不夹会让一条 10:1 的长卷把整列撑出屏幕（墙的网格随之崩）。
        val tenToOne = post(GallerySite.PIXIV, 4000, 400)
        assertEquals(10f, tenToOne.cardRatio, 5e-2f)
        assertEquals("夹到上界", PIXIV_CARD_RATIO_MAX, cardBoxRatio(tenToOne, fallback), 1e-4f)
        assertTrue("这一档要说得出自己在裁", cardBoxRatioClamped(tenToOne, fallback))
    }

    @Test
    fun `图库三站的区间原样不动`() {
        // 主人这次点的是 pixiv 站点；三站那面墙是共用的，口径不动它一根手指。
        assertEquals(0.4f, LEGACY_CARD_RATIO_MIN, 1e-6f)
        assertEquals(2.5f, LEGACY_CARD_RATIO_MAX, 1e-6f)
        // 0.27 的竖条在三站仍被夹（这是既有行为，不是本轮要改的）。
        val yandere = post(GallerySite.YANDERE, 300, 1100)
        assertEquals(LEGACY_CARD_RATIO_MIN, cardBoxRatio(yandere, fallback), 1e-4f)
        assertTrue("三站那一档仍会夹（既有口径）", cardBoxRatioClamped(yandere, fallback))
        // 3.0 的横图在三站也被夹到 2.5。
        val safebooru = post(GallerySite.SAFEBOORU, 1500, 500)
        assertEquals(LEGACY_CARD_RATIO_MAX, cardBoxRatio(safebooru, fallback), 1e-4f)
    }

    @Test
    fun `站方没给尺寸就交调用侧的占位比例`() {
        // 不替站方编一个形状（同 `placeholderAspect` 那条口径）。
        val noSize = post(GallerySite.PIXIV, 0, 0)
        assertEquals(0f, noSize.cardRatio, 1e-6f)
        assertEquals("没比例时交调用侧 token", fallback, cardBoxRatio(noSize, fallback), 1e-6f)
        assertFalse("没比例不算'被夹'", cardBoxRatioClamped(noSize, fallback))
    }

    // ── 负向扫描：两条"演回来"的路各钉一笔 ──────────────────────────────────

    @Test
    fun `不许把封面的 Crop 改成 Fit`() {
        // `Fit` 能让整图可见，但框里会**留白条**；而飞行体那套算式（`flightFrame`）是按
        // **cover 语义**写的 —— 它假定"图铺满框"。卡一旦留白，那套算式的假设就崩，
        // 等于把"乱变"换个样子演回来。所以封面的 ContentScale 必须一直是 Crop。
        val cover = File(RepoSources.appMain, "com/venera/compose/components/venera/VeneraCover.kt")
        assertTrue("找不到 VeneraCover.kt，量具失效", cover.isFile)
        val body = cover.readText().replace("\r\n", "\n")
        assertTrue(
            "封面必须仍是 ContentScale.Crop（Fit 会让飞行算式的 cover 假设崩）",
            body.contains("contentScale = ContentScale.Crop"),
        )
        assertFalse(
            "封面不许改成 Fit",
            body.contains("contentScale = ContentScale.Fit"),
        )
    }

    @Test
    fun `屏上不许再就地写卡片夹取`() {
        // 第二个旋钮就是第二处真相：改区间的人只改一处，另一处悄悄留着旧区间。
        // 唯一出处是 `GalleryCardAspect.kt` 的 `cardBoxRatio`，屏上只许调它。
        val screen = File(RepoSources.appMain, "com/venera/compose/gallery/ui/GalleryScreen.kt")
        assertTrue("找不到 GalleryScreen.kt，量具失效", screen.isFile)
        val code = screen.readText().replace("\r\n", "\n").lines()
            .filter { !it.trimStart().startsWith("//") && !it.trimStart().startsWith("*") }
        val inlineClamp = code.count { it.contains("cardRatio.coerceIn(") }
        assertEquals(
            "屏上不许就地夹 cardRatio（唯一出处是 cardBoxRatio）",
            0,
            inlineClamp,
        )
        assertTrue(
            "屏上要调唯一出处那颗",
            code.any { it.contains("cardBoxRatio(post,") },
        )
    }
}
