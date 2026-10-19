package com.venera.compose.gallery

import com.venera.compose.gallery.domain.GalleryHomeDomain
import com.venera.compose.gallery.ui.galleryHomeSectionOrder
import com.venera.compose.gallery.ui.GalleryHomeSectionKey
import com.venera.compose.testsupport.RepoSources
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
    fun `两档都恒为三节且 key 不重复`() {
        GalleryHomeDomain.entries.forEach { picked ->
            val keys = galleryHomeSectionOrder(picked)
            assertEquals("每一档都应当恰好三节", 3, keys.size)
            assertEquals("节的 key 不许重复", keys.size, keys.toSet().size)
        }

    }

    @Test
    fun `图库站那一档的次序是 画师 热门 猜你喜欢`() {
        assertEquals(
            listOf(
                GalleryHomeSectionKey.ARTISTS,
                GalleryHomeSectionKey.DAILY,
                GalleryHomeSectionKey.FOR_YOU,
            ),
            galleryHomeSectionOrder(GalleryHomeDomain.BOORU),
        )
    }

    /** 用户 2026-10-17 那句"切换到 pixiv 就换成这些排布"的落地形。 */
    @Test
    fun `Pixiv 那一档的次序是 关注画师 排行榜 推荐`() {
        assertEquals(
            listOf(
                GalleryHomeSectionKey.PIXIV_ARTISTS,
                GalleryHomeSectionKey.PIXIV_RANKING,
                GalleryHomeSectionKey.PIXIV_RECOMMEND,
            ),
            galleryHomeSectionOrder(GalleryHomeDomain.PIXIV),
        )
    }

    /**
     * "图库站保持不变"这句的尺子：**逐字就是从前那三节**。
     * 两档节数还必须相等 —— 切域时"多出来的一节插在第 0 项之后"是锚点漂移那一族。
     */
    @Test
    fun `切域不动图库站那三节也不改节数`() {
        val booru = galleryHomeSectionOrder(GalleryHomeDomain.BOORU)
        val pixiv = galleryHomeSectionOrder(GalleryHomeDomain.PIXIV)
        assertEquals("两档节数必须相等（切域不该插入或摘掉一节）", booru.size, pixiv.size)
        assertEquals(
            listOf(GalleryHomeSectionKey.ARTISTS, GalleryHomeSectionKey.DAILY, GalleryHomeSectionKey.FOR_YOU),
            booru,
        )
    }

    /**
     * 内容域那一排第三轮起**住在搜索那一行的左格**（用户稿「看下站源切换能不能做成这样」），
     * 不再是一节。这里钉两件事：清单里不许有 `section-domain`、那一颗件真的被 chrome 调用，
     * 并且它与搜索条共用同一条 56dp 的带（"chrome 高度常量只认那三项"由 `GalleryHomeChromeTest` 钉，
     * 同一件事不在这里再立一把尺子）。
     */
    @Test
    fun `内容域那一排在搜索那一行的左格，不再是一节`() {
        GalleryHomeDomain.entries.forEach { picked ->
            assertTrue(
                "首页节清单里不许再有域那一排：${galleryHomeSectionOrder(picked)}",
                galleryHomeSectionOrder(picked).none { it.contains("domain") },
            )
        }
        val chrome = RepoSources.kotlinFiles("com/venera/compose/gallery/ui")
            .first { it.name == "GalleryHomeChrome.kt" }
            .readLines()
        val code = chrome.map { it.trim() }.filter { !it.startsWith("//") && !it.startsWith("*") && !it.startsWith("/*") }
        // 2026-10-17：先换成下拉，同日又按用户「改回前面版本的分段切换器」改回来了，锚点跟着改回原名。
        assertEquals("切换器只许在 chrome 那一行里出现一次", 1, code.count { it.contains("GalleryHomeDomainChips(") })
        // 道数而不是"有没有"：搜索条自己就吃那一档高度，只问"文件里有没有那一档"
        // 会被条那一处合法引用喂饱 —— 摘掉切换器那圈外框照样绿（本仓那族锚点被喂饱的形状）。
        // 2026-10-17：那一档从 56dp 的 `dockedSearchBarHeight` 换成 48dp 的 `segmentedHeight`
        // （用户：搜索框缩成和切换源一样大小），两处仍然同档 —— 道数不变。
        assertEquals(
            "切换器那一圈外框要与搜索条共用同一条 48dp 的带：文件里应恰好两处（条自己 + 切换器那圈）",
            2, code.count { it.contains(".height(tokens.spacing.segmentedHeight)") },
        )
    }

    @Test
    fun `根据你的收藏与最近搜索都不再是首页的一节`() {
        // 2026-09-30 第二轮：前者整节撤下（推荐标签仍是猜你喜欢的种子，只是不摆在首页），
        // 后者搬回搜索卡。这两条都是用户点名的信息架构改动，别被"顺手加回来"。
        assertTrue(
            "首页不许再出现「根据你的收藏」那一节",
            galleryHomeSectionOrder(GalleryHomeDomain.BOORU).none { it.contains("chips") } &&
                galleryHomeSectionOrder(GalleryHomeDomain.PIXIV).none { it.contains("chips") },
        )
        assertTrue(
            "首页不许再出现「最近搜索」那一节",
            galleryHomeSectionOrder(GalleryHomeDomain.BOORU).none { it.contains("recent") } &&
                galleryHomeSectionOrder(GalleryHomeDomain.PIXIV).none { it.contains("recent") },
        )
    }

    @Test
    fun `每一节的 key 都带 section 前缀`() {
        // 前缀是"这是一个墙内节"的可见标记：`GalleryCardsGrid` 里节的 key 与卡片的 key
        // （`post.uid`）共用一个命名空间，没有前缀的话一张 id 恰好叫 `section-daily` 的图
        // 理论上能撞上 —— 现实里不会，但这条约束零成本，而撞 key 的代价是崩溃。
        GalleryHomeDomain.entries.forEach { picked ->
            galleryHomeSectionOrder(picked).forEach { key ->
                assertTrue("节的 key 应以 section- 开头，实际是 $key", key.startsWith("section-"))
            }
        }
    }

    /**
     * 图库站的「正在关注的画师」那一行与 pixiv 那一排**同形**（2026-10-17 第九轮）。
     *
     * 用户原话：「图库那边的关注画师列也要同步更改，不要只改 pixiv 那边的」。
     * 两条判据都写在源码上，因为这两件事都是"形态"而不是"数据"：
     *  · 一格 = 裸圆头像 + 一行名字（**没有**卡面）⇒ 函数体里不许有 `VeneraCard(`；
     *  · 格宽就是脸的直径（`artistAvatarSize`）⇒ 旧方卡那枚 `artistAvatarSlotWidth` 已退役。
     *
     * ⚠️ 量法按**函数体**切段（从签名那一行到下一条顶格 `}`），不按文件切 ——
     * 同一颗文件里「每日热门」那一排仍在用 `VeneraCard`，按文件切会被它喂饱（假绿）。
     */
    @Test
    fun `图库站的关注画师那一行与 pixiv 同形：裸圆头像，不再是方卡`() {
        val body = functionBody("GalleryHomeSections.kt", "private fun GalleryArtistRow(")
        assertTrue("要用横滑一排（不是自动换行的网格）：\n$body", body.contains("LazyRow("))
        // 格宽 = 脸的直径：裸摆之后两者是同一个数。
        assertTrue("一格要按 artistAvatarSize 定宽：\n$body", body.contains(".width(tokens.spacing.artistAvatarSize)"))
        // 卡面撤了 ⇒ 按压反馈得自己挂（这一栏 2026-10-16 修过一次"按下去没回话"）。
        assertTrue("可点面要走 combinedClickable：\n$body", body.contains("combinedClickable("))
        assertTrue("反馈走 SinkFeedback：\n$body", body.contains("indication = VeneraPressFeedback.indication"))
        // 两个手势都得真：单击直接搜、长按进介绍页（2026-09-30 拍板 2 那条不动）。
        assertTrue("单击仍直接搜：\n$body", body.contains("onClick = { onPick(artist) }"))
        assertTrue("长按仍进介绍页：\n$body", body.contains("onLongClick = { onOpenProfile(artist) }"))
        // 旧形状：方卡 + 卡里那行「[站标] N 张收藏」（56dp 一格装不下第二行读数）。
        assertTrue("这一格不许再有卡面：\n$body", !body.contains("VeneraCard("))
        assertTrue("旧方卡那枚令牌已退役：\n$body", !body.contains("artistAvatarSlotWidth"))
        assertTrue("旧读数行已撤：\n$body", !body.contains("张收藏"))

        // pixiv 那一排：同一条形状（用户「只显示头像」）。
        val rail = functionBody("GalleryPixivHome.kt", "internal fun PixivArtistRail(")
        assertTrue("pixiv 那一排也按 artistAvatarSize 定宽：\n$rail", rail.contains(".width(tokens.spacing.artistAvatarSize)"))
        assertTrue("pixiv 那一排也不许有卡面：\n$rail", !rail.contains("VeneraCard("))
    }

    /** 按**函数体**切一段：从含 [signature] 的那一行，到其后第一条顶格 `}`（含）。 */
    private fun functionBody(fileName: String, signature: String): String {
        val lines = RepoSources.kotlinFiles("com/venera/compose/gallery/ui")
            .first { it.name == fileName }
            .readLines()
        val start = lines.indexOfFirst { it.contains(signature) }
            .also { check(it >= 0) { "找不到 $fileName 里的 `$signature`" } }
        val end = (start + 1 until lines.size).firstOrNull { lines[it] == "}" }
            ?: error("$fileName:$start 那段没有顶格 `}` 收尾")
        return lines.subList(start, end + 1)
            .filterNot { line ->
                val t = line.trim()
                t.startsWith("//") || t.startsWith("*") || t.startsWith("/*")
            }
            .joinToString("\n")
    }
}
