package com.venera.compose.gallery

import com.venera.compose.gallery.ui.GALLERY_HOME_SECTION_ORDER
import com.venera.compose.gallery.ui.GalleryHomeSectionKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 锁住画廊首页那几节的**清单**。
 *
 * 为什么这几条值得单独一个测试类：本项目没有 Robolectric，composable 一行都测不到，
 * 所以"节的数量与次序"这个**唯一会影响屏上结构**的判据只能落在常量上测。
 * 它守的是两件后果都很重的事：
 *
 * 1. **key 不许重复** —— 它进 `LazyStaggeredGrid` 的 `item(key = …)`，重复直接崩，
 *    而崩的位置在真机上读起来像"进画廊就闪退"，跟"我改了个字符串"联系不起来；
 * 2. **次序不许被顺手重排** —— 2026-09-30 第二轮那一版是用户点名定的
 *    （画师 → 每日热门 → 猜你喜欢），重排是一次产品改动，
 *    不该以"整理代码"的名义发生。顺带把"加了一节却忘了登记进清单"也挡住。
 *
 * ⚠️ **这一类测不到表面在哪一层**（批次 N · N2 那次"撤节背板、下放到条目"在这里一条都不会红）。
 * 背板摆不摆、摆在节上还是条目上，是 composable 的布局层，没有 Robolectric 就够不着 ——
 * 所以那类改动的验收**只在真机清单上**（见 `gallery-home-round4-2026-09-30.md` §五）。
 * 记在这里是为了别把"这一类全绿"当成"首页结构没被改坏"的证据。
 */
class GalleryHomeSectionsTest {

    @Test
    fun `首页恒为三节且 key 不重复`() {
        val keys = GALLERY_HOME_SECTION_ORDER
        assertEquals("节的清单应当恰好三节", 3, keys.size)
        // 重复的 key 会让 LazyStaggeredGrid 直接崩，而屏上看不出是这里的问题。
        assertEquals("节的 key 不许重复", keys.size, keys.toSet().size)
    }

    @Test
    fun `次序是画师 热门 猜你喜欢`() {
        assertEquals(
            listOf(
                GalleryHomeSectionKey.ARTISTS,
                GalleryHomeSectionKey.DAILY,
                GalleryHomeSectionKey.FOR_YOU,
            ),
            GALLERY_HOME_SECTION_ORDER,
        )
    }

    @Test
    fun `根据你的收藏与最近搜索都不再是首页的一节`() {
        // 2026-09-30 第二轮：前者整节撤下（推荐标签仍是猜你喜欢的种子，只是不摆在首页），
        // 后者搬回搜索卡。这两条都是用户点名的信息架构改动，别被"顺手加回来"。
        assertTrue(
            "首页不许再出现「根据你的收藏」那一节",
            GALLERY_HOME_SECTION_ORDER.none { it.contains("chips") },
        )
        assertTrue(
            "首页不许再出现「最近搜索」那一节",
            GALLERY_HOME_SECTION_ORDER.none { it.contains("recent") },
        )
    }

    @Test
    fun `每一节的 key 都带 section 前缀`() {
        // 前缀是"这是一个墙内节"的可见标记：`GalleryCardsGrid` 里节的 key 与卡片的 key
        // （`post.uid`）共用一个命名空间，没有前缀的话一张 id 恰好叫 `section-daily` 的图
        // 理论上能撞上 —— 现实里不会，但这条约束零成本，而撞 key 的代价是崩溃。
        GALLERY_HOME_SECTION_ORDER.forEach { key ->
            assertTrue("节的 key 应以 section- 开头，实际是 $key", key.startsWith("section-"))
        }
    }
}
