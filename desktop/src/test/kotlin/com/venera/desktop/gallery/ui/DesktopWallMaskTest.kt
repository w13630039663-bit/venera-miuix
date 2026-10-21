package com.venera.desktop.gallery.ui

import com.venera.compose.gallery.data.GalleryContentGuard
import com.venera.compose.gallery.data.GalleryPost
import com.venera.compose.gallery.data.GallerySite
import com.venera.compose.security.guard.GuardRule
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 钉住「画廊墙真的会打码」—— 安全评审 **B**，本轮最严重的一条。
 *
 * ## 缺口长什么样（三条独立证据，本喵已复核）
 *
 * 1. `DesktopGalleryWallPolicy.of(...)` 是真判据，但在 `desktop/src/main` 里**零调用点**；
 * 2. 生产取数路走 `desktopWallCardsOf(merged.posts, quality)`，而 `desktopWallCardOf`
 *    的 `masked` 形参**默认 `false`**（`DesktopWallCardModel.kt:46`）；
 * 3. 大屏照 `card.masked` 上遮罩 ⇒ 恒 false。
 *
 * ⇒ **无论档位是 OFF / BLUR / HIDE，墙上一张图都不会被遮。**
 * 这条不报错、不变慢、屏上也看不出来，只是该遮的露出来。
 *
 * ## 一个本喵自己踩的坑（写进来免得下一个人再踩）
 *
 * 一开始我以为"成人标记"是看 tags 里有没有 `r-18`。**不是。**
 * `GalleryPost.isAdultMarked`（`GalleryPost.kt:189-190`）判的是**站方 `rating` 字段**：
 * `val isAdultMarked: Boolean get() = rating.lowercase() !in SAFE_RATINGS`
 * 因为三家站 rating 字形不同（yande.re 用 `s`/`q`/`e`、Gelbooru 用
 * `general`/`sensitive`/`questionable`/`explicit`、Safebooru 用 `g`/`s`/`q`/`e`），
 * 所以判据是"只认安全的那几档"，其余一律算成人。tags 只有用户黑名单那一关用得上。
 *
 * ## 为什么直接打 policy 而不打整个取数路
 *
 * 取数路要真网络（三个图站）。policy 是**纯函数**，能真跑、能断言三个档位分支。
 * 「取数路有没有真调 policy」由另一条尺钉。
 */
class DesktopWallMaskTest {

    /** 最小可用守卫。`blockedGalleryRule` 故意只按 TAG 精确匹配，够测黑名单那一关。 */
    private class FakeGuard(
        private val ruleList: List<GuardRule> = emptyList(),
        mode: String = "BLUR",
    ) : GalleryContentGuard {
        override val nsfwMaskMode: StateFlow<String> = MutableStateFlow(mode)
        override val rules: StateFlow<List<GuardRule>> = MutableStateFlow(ruleList)

        override fun blockedGalleryRule(author: String, tags: List<String>): GuardRule? =
            ruleList.firstOrNull { rule ->
                rule.isEnabled && tags.any { it.equals(rule.pattern, ignoreCase = true) }
            }

        override suspend fun addRule(type: String, pattern: String, isRegex: Boolean): Long = -1
    }

    /** @param rating 站方原值。`"s"` / `"g"` 是安全档，其余（`e`/`q`/`explicit`…）算成人。 */
    private fun post(
        id: Long,
        tags: String = "",
        rating: String = "s",
        author: String = "someone",
    ): GalleryPost = GalleryPost(
        site = GallerySite.YANDERE,
        id = id,
        tags = tags,
        rating = rating,
        author = author,
        previewUrl = "https://example.invalid/$id.jpg",
    )

    @Test
    fun `BLUR 档下成人那张必须 masked 而安全那张不许 masked`() {
        val wall = DesktopGalleryWallPolicy.of(
            posts = listOf(post(1, rating = "s"), post(2, rating = "e")),
            maskMode = "BLUR",
            blockAi = false,
            guard = FakeGuard(),
        )
        assertEquals("两张都该留在墙上（BLUR 是遮，不是藏）", 2, wall.cards.size)
        val byId = wall.cards.associateBy { it.post.id }
        assertEquals("安全档那张不该被遮（误遮是另一种伤害）", false, byId[1L]!!.masked)
        assertEquals("成人档那张必须被遮 —— 不遮就等于这张封面裸奔", true, byId[2L]!!.masked)
    }

    @Test
    fun `HIDE 档下成人那张必须整张消失 而且算进 hiddenByRating`() {
        val wall = DesktopGalleryWallPolicy.of(
            posts = listOf(post(1, rating = "s"), post(2, rating = "e")),
            maskMode = "HIDE",
            blockAi = false,
            guard = FakeGuard(),
        )
        assertEquals("HIDE 档下成人那张必须不在 cards 里（= 不取图）", 1, wall.cards.size)
        assertEquals(1L, wall.cards.single().post.id)
        assertEquals("按分级藏起来的张数要念得出来", 1, wall.hiddenByRating)
    }

    @Test
    fun `OFF 档下什么也不遮 —— 这是用户明确关掉的那一档`() {
        val wall = DesktopGalleryWallPolicy.of(
            posts = listOf(post(1, rating = "e")),
            maskMode = "OFF",
            blockAi = false,
            guard = FakeGuard(mode = "OFF"),
        )
        assertEquals(1, wall.cards.size)
        assertEquals(
            "OFF 档必须真的不遮 —— 遮了就是假开关（用户关不掉）",
            false,
            wall.cards.single().masked,
        )
    }

    @Test
    fun `用户黑名单排在档位之前 连 OFF 档都挡得住`() {
        val rules = listOf(
            GuardRule(id = 1, type = "TAG", pattern = "guilty_tag", isRegex = false, isEnabled = true),
        )
        val wall = DesktopGalleryWallPolicy.of(
            posts = listOf(post(1, tags = "guilty_tag other", rating = "s")),
            maskMode = "OFF",
            blockAi = false,
            guard = FakeGuard(ruleList = rules, mode = "OFF"),
        )
        assertTrue(
            "黑名单必须在档位判定之前（`GalleryGuard.maskStateFor` 第 29 行）：" +
                "用户把分级遮罩关掉，不该连带把「我屏蔽过这个词」也废掉 —— 那是假开关。",
            wall.cards.isEmpty() || wall.cards.all { it.masked },
        )
    }
}
