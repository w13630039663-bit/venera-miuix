package com.venera.desktop.gallery.ui

import com.venera.compose.gallery.data.GalleryPost
import com.venera.compose.gallery.domain.GalleryColumnMode
import com.venera.compose.gallery.domain.GalleryPreviewQuality
import com.venera.compose.gallery.data.GallerySite
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 墙上那三枚画廊偏好的接线判据（第五批）。
 *
 * ## 这批为什么是"接消费点"而不是"做设置页"
 *
 * 第四批数过一遍：桌面那 12 枚偏好里 6 枚属于桌面根本没有的大图页、2 枚给常量，
 * 只剩列数 / 缩略档 / AI 角标这三枚**今天就在墙上、接得住**。先接它们，
 * 设置页摆出来才不是一屏假开关（手册 §18.1）。
 *
 * ## 三枚都只许"交给共享那颗判据"，不许桌面再写一份
 *
 * 列数走 `GallerySettingsModel.gridColumns`、地址走 `GallerySettingsModel.wallUrl`、
 * AI 判定走 `GalleryAi.isAiMarked` —— 三颗都在桌面编译面内（`gallery/domain`），
 * 所以这里**没有重述**的必要。②④ 三条负向判据钉的就是这一条：
 * 桌面这层只许决定"要不要发起、什么时候发起"，不许决定"怎么算"。
 */
class DesktopWallPreferenceWiringTest {

    private fun post(
        id: Long,
        preview: String = "https://static1.example/preview/$id.jpg",
        large: String = "https://static1.example/large/$id.jpg",
        tags: String = "",
        rating: String = "s",
    ) = GalleryPost(
        site = GallerySite.YANDERE,
        id = id,
        rating = rating,
        previewUrl = preview,
        largeUrl = large,
        tags = tags,
    )

    /** ① 列数：AUTO 跟宽度走，而 TWO / THREE 是用户显式覆盖 —— 窗口再宽也不许跟着动。 */
    @Test
    fun 列数AUTO跟宽走而两档覆盖不跟() {
        // AUTO 那一档沿用既有推导（1168 → 6，与稿 `:338`/`:657` 的标注同值，见 DesktopWidthCaliberDriftTest）。
        assertEquals(6, desktopWallColumnCount(1168f, GalleryColumnMode.AUTO))
        assertEquals(3, desktopWallColumnCount(300f, GalleryColumnMode.AUTO))
        // 覆盖档存在的意义就是"我就要这么多列"：宽屏不放大，窄屏也不被那个下限抬走。
        assertEquals(2, desktopWallColumnCount(1168f, GalleryColumnMode.TWO))
        assertEquals(3, desktopWallColumnCount(1168f, GalleryColumnMode.THREE))
        assertEquals(2, desktopWallColumnCount(300f, GalleryColumnMode.TWO))
    }

    /** ② 列数判据不许在桌面重写第二份：只许调共享那颗，宽屏自适应那半颗才归桌面。 */
    @Test
    fun 列数只许调共享那颗而不许桌面自己写覆盖() {
        val code = DesktopSourceTree.codeText(DesktopSourceTree.desktopUiSource("DesktopGalleryWallPolicy.kt"))
        assertTrue("桌面自己算列数覆盖了 —— 覆盖档的语义归 GallerySettingsModel.gridColumns，抄第二份就会漂",
            code.contains("GallerySettingsModel.gridColumns"))
        assertTrue(
            "桌面这边出现了 `GalleryColumnMode.TWO ->` 这种本地覆盖分支 —— 那就是第二套列数口径",
            !code.contains("GalleryColumnMode.TWO ->"),
        )
        // 对照组：AUTO 那半颗确实归桌面（它要吃桌面自己量出来的内容宽），扫描失效时这条会恒真。
        assertTrue("对照组失效：桌面这边没在调 imageWallColumnCount", code.contains("imageWallColumnCount"))
    }

    /** ③ 遮挡档排在清晰度档**前面**：被挡的那一张连地址都不该给出去，无论用户选了哪一档。 */
    @Test
    fun 遮挡优先于清晰度档() {
        val p = post(1)
        assertNull(tileImageRequest(p, masked = true, quality = GalleryPreviewQuality.PREVIEW))
        assertNull(tileImageRequest(p, masked = true, quality = GalleryPreviewQuality.LARGE))
        assertEquals(p.previewUrl, tileImageRequest(p, masked = false, quality = GalleryPreviewQuality.PREVIEW))
    }

    /** ④ 取哪一档交给共享那颗 `wallUrl`：桌面不许再自己写 `post.previewUrl`。 */
    @Test
    fun 清晰度档真的换了档而不是恒给缩略图() {
        val p = post(2)
        // 这一条是"改之前必然红"的那一条：原来卡上写死 previewUrl，选 LARGE 也不会变。
        assertEquals(p.largeUrl, tileImageRequest(p, masked = false, quality = GalleryPreviewQuality.LARGE))
        val tile = DesktopSourceTree.codeText(DesktopSourceTree.desktopUiSource("DesktopGalleryPostTile.kt"))
        assertTrue("卡上不再经 tileImageRequest 拿地址", tile.contains("tileImageRequest("))
        assertTrue("卡里还自己写着 post.previewUrl —— 那是绕过清晰度档", !tile.contains("post.previewUrl"))
        // 对照组：这颗文件读到了**代码**（`codeText` 会跳注释，所以拿一行必然存在的代码作样；
        // 用 `previewUrl` 作样会适得其反 —— 那个词如今只出现在注释里，跳完注释就没了）。
        assertTrue("对照组失效：这颗文件读出来是空的，扫描器可能根本没读到内容", tile.contains("post.site.displayName"))
    }

    /** ⑤ AI 角标：站方没打标就一律不摆，开关关掉也一律不摆 —— 两枚都是"没有信号就不摆"。 */
    @Test
    fun AI角标只在开关开着且站方打过标时摆() {
        val marked = post(3, tags = "landscape ai-generated")
        val plain = post(4, tags = "landscape")
        assertTrue(aiBadgeFor(marked, badgeOn = true))
        assertFalse("开关关着还摆角标 —— 屏蔽与角标是两枚独立开关", aiBadgeFor(marked, badgeOn = false))
        assertFalse("站方没打标的条目摆角标 = 靠画风编一个读数出来", aiBadgeFor(plain, badgeOn = true))
        assertFalse(aiBadgeFor(plain, badgeOn = false))
        assertFalse("标签为空时不许摆", aiBadgeFor(post(5), badgeOn = true))
    }

    /** ⑥ 角标那两个字与 Android 那颗胶囊逐字同串（重述就要回核，与第四批那两条同一形状）。 */
    @Test
    fun AI角标文案与Android那颗胶囊同串() {
        assertEquals("AI", AI_BADGE_TEXT)
        val android = DesktopSourceTree.codeText(
            File(DesktopSourceTree.repoRoot, "app/src/main/java/com/venera/compose/gallery/ui/GalleryScreen.kt"),
        )
        assertTrue(
            "Android 那颗 AI 胶囊的文案不再是 GalleryAiPill(\"AI\") 这一形 —— 桌面这枚要跟着对表，别两端各摆一个词",
            android.contains("GalleryCornerPill(\"AI\""),
        )
        // 对照组：那颗文件读到了内容（读空会让上面那条恒真）。
        assertTrue("对照组失效：Android 那颗文件没读到内容", android.contains("buildGalleryWall"))
    }

    /** ⑦ 三面墙都吃这三枚 —— 漏一面就是"同一份设置在两屏上行为不同"。 */
    @Test
    fun 三面墙都吃这三枚() {
        for (name in listOf("DesktopDailyPane.kt", "DesktopGalleryFavoritesPane.kt", "DesktopGallerySearchPane.kt")) {
            val code = DesktopSourceTree.codeText(DesktopSourceTree.desktopUiSource(name))
            assertTrue("$name 没吃列数档", code.contains("galleryColumnMode"))
            assertTrue("$name 没吃清晰度档", code.contains("galleryPreviewQuality"))
            assertTrue("$name 没吃 AI 角标档", code.contains("galleryAiBadge"))
            // 对照组：这三枚是**加在**既有那面墙上的，墙本身还在（否则扫描器读错文件也会全绿）。
            assertTrue("$name 不再从 DesktopGalleryWallPolicy 拿墙", code.contains("DesktopGalleryWallPolicy.of"))
        }
    }
}
