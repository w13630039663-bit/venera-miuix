package com.venera.compose.gallery

import com.venera.compose.gallery.data.GallerySite
import com.venera.compose.gallery.domain.GallerySearchSource
import com.venera.compose.gallery.domain.allLegsExhausted
import com.venera.compose.gallery.domain.gallerySourceMarkText
import com.venera.compose.gallery.domain.isStaleLeg
import com.venera.compose.gallery.domain.nextPagesForAppend
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 搜索「来源」那一轴的判据锁（批次 K）。
 *
 * 这一轴存在的全部理由：**「全部」绝不能是 `GallerySite` 的第三个枚举值**。那个枚举有约一百处
 * 引用，一大半是身份语义（条目 uid、路由键、收藏档、缓存键、大图页队列、熔断遍历），
 * 混进"两站的并"就会把这些键全部污染。所以来源是**另一枚类型**，只在发请求那一处展开成腿。
 *
 * 钉死的几件事都有代价：
 * - 腿的顺序按 `GallerySite.entries` 定死 —— 不钉就会跟着 `HashMap` 的无序飘（同 `GalleryMerge` 那条）；
 * - **未配账号的那条腿不进"发出的腿"集合**：进了就永远到底不了，页尾会一直挂着"加载更多"；
 * - 缺席也不算到底（与 `GalleryForYouMerge.exhaustedSites` 同一条口径，一次抖动不许永久踢掉一站）。
 */
class GallerySearchSourceTest {

    @Test
    fun `全部档展开成全部站且顺序按站表定死`() {
        assertEquals(
            listOf(GallerySite.YANDERE, GallerySite.GELBOORU, GallerySite.SAFEBOORU),
            GallerySearchSource.ALL.availableLegs(hasGelbooruAccount = true),
        )
    }

    @Test
    fun `单站档只有一腿`() {
        val single = GallerySearchSource.single(GallerySite.YANDERE)
        assertEquals(listOf(GallerySite.YANDERE), single.availableLegs(hasGelbooruAccount = true))
        assertFalse(single.isMulti)
    }

    @Test
    fun `全部档的读数标签是全部 单站档用站方自己的写法`() {
        assertEquals("全部", GallerySearchSource.ALL.label)
        assertEquals("yande.re", GallerySearchSource.single(GallerySite.YANDERE).label)
    }

    @Test
    fun `未配账号时可用腿只剩免账号的两站 且缺席那条腿说得出原因`() {
        val legs = GallerySearchSource.ALL.availableLegs(hasGelbooruAccount = false)
        // Safebooru 匿名可用（Danbooru 全年龄镜像），与 yande.re 一样不需要账号。
        assertEquals(listOf(GallerySite.YANDERE, GallerySite.SAFEBOORU), legs)

        val missing = GallerySearchSource.ALL.missingLegs(hasGelbooruAccount = false)
        assertEquals(setOf(GallerySite.GELBOORU), missing.keys)
        // 原因必须是一句能直接上屏的话 —— 这一腿没跑起来的原因不写出来，用户只会以为"这一站今天没图"。
        assertTrue(missing.getValue(GallerySite.GELBOORU).isNotEmpty())
    }

    @Test
    fun `未配账号时单站 Gelbooru 档一条腿都发不出去`() {
        val single = GallerySearchSource.single(GallerySite.GELBOORU)
        assertTrue(single.availableLegs(hasGelbooruAccount = false).isEmpty())
        // 发不出去不等于到底了：那一档要由"发车前拦住"那条路去说，不许被到底判据吞掉。
        assertFalse(allLegsExhausted(dispatched = emptyList(), exhaustedBySite = emptySet()))
    }

    @Test
    fun `换轮次就作废 且只丢不匹配的那一条腿`() {
        val active = listOf(GallerySite.YANDERE, GallerySite.GELBOORU)
        // 轮次对不上：弹栈之后站点恰好没变，只有轮次拦得住旧那一笔回填。
        assertTrue(isStaleLeg(requestRound = 3, requestSite = GallerySite.YANDERE, currentRound = 4, activeSites = active))
        // 轮次对得上、腿还在当前来源里：不丢。
        assertFalse(isStaleLeg(3, GallerySite.YANDERE, 3, active))
        // 轮次对得上但来源已经换成单站：这一腿落地就是脏数据。
        assertTrue(isStaleLeg(3, GallerySite.GELBOORU, 3, listOf(GallerySite.YANDERE)))
    }

    @Test
    fun `到底的那条腿不再要页 没取过的那条腿从第 1 页起`() {
        val dispatched = listOf(GallerySite.YANDERE, GallerySite.GELBOORU)
        val pages = nextPagesForAppend(
            pageBySite = mapOf(GallerySite.YANDERE to 2),
            exhaustedBySite = setOf(GallerySite.GELBOORU),
            dispatched = dispatched,
        )
        // Gelbooru 已经到底 —— 再要第 3 页就是"到底了还显示加载更多"那类假读数的成因。
        assertEquals(mapOf(GallerySite.YANDERE to 3), pages)

        // 一条腿这一轮整个缺席（超时/熔断），它没进过 pageBySite：续页要从第 1 页补，而不是第 2 页。
        val rejoin = nextPagesForAppend(
            pageBySite = mapOf(GallerySite.YANDERE to 2),
            exhaustedBySite = emptySet(),
            dispatched = dispatched,
        )
        assertEquals(mapOf(GallerySite.YANDERE to 3, GallerySite.GELBOORU to 1), rejoin)
    }

    @Test
    fun `一腿到底另一腿还在出货时整体不算到底`() {
        val dispatched = listOf(GallerySite.YANDERE, GallerySite.GELBOORU)
        assertFalse(allLegsExhausted(dispatched, setOf(GallerySite.YANDERE)))
        assertTrue(allLegsExhausted(dispatched, setOf(GallerySite.YANDERE, GallerySite.GELBOORU)))
        // 没发出的腿不许把整体判成"没到底"，否则页尾的"加载更多"会一直挂在一个永远不来的请求上。
        assertTrue(allLegsExhausted(listOf(GallerySite.YANDERE), setOf(GallerySite.YANDERE, GallerySite.GELBOORU)))
    }

    @Test
    fun `来源小徽标按站表序排 两站中间加间隔号`() {
        assertEquals("Y", gallerySourceMarkText(setOf(GallerySite.YANDERE)))
        assertEquals("G", gallerySourceMarkText(setOf(GallerySite.GELBOORU)))
        // 传参顺序反过来也必须同一个串 —— 否则同一个历史条目会因为集合实现而换字形。
        val a = gallerySourceMarkText(setOf(GallerySite.GELBOORU, GallerySite.YANDERE))
        val b = gallerySourceMarkText(setOf(GallerySite.YANDERE, GallerySite.GELBOORU))
        assertEquals("Y·G", a)
        assertEquals(a, b)
    }

    @Test
    fun `分段器那四档的顺序是全部在最前 后面按站表序`() {
        // 默认档必须落在 index 0（用户拍板「全部」为默认），单站档跟在后面按站表序。
        // 这一条锁的是 UI 直接照抄的那个列表：顺序漂了，分段器上「全部」就会跑到中间。
        assertEquals(
            listOf(
                GallerySearchSource.ALL,
                GallerySearchSource.single(GallerySite.YANDERE),
                GallerySearchSource.single(GallerySite.GELBOORU),
                GallerySearchSource.single(GallerySite.SAFEBOORU),
            ),
            GallerySearchSource.options,
        )
        assertEquals(
            listOf("全部", "yande.re", "Gelbooru", "Safebooru"),
            GallerySearchSource.options.map { it.label },
        )
        // 各档都能被 indexOf 认回来 —— 认不回来分段器就会恒选中第一档。
        GallerySearchSource.options.forEachIndexed { i, source ->
            assertEquals(i, GallerySearchSource.options.indexOf(source))
        }
    }

    @Test
    fun `空腿来源直接拦住`() {
        // 一个来源至少要有一条腿。空腿来源会让"到底"判据恒真，页尾从此不再出货。
        assertTrue(runCatching { GallerySearchSource(emptyList()) }.isFailure)
    }

    @Test
    fun `重复的腿在构造时被并掉且顺序定死`() {
        val src = GallerySearchSource(listOf(GallerySite.GELBOORU, GallerySite.YANDERE, GallerySite.GELBOORU))
        assertEquals(listOf(GallerySite.YANDERE, GallerySite.GELBOORU), src.sites)
        assertTrue(src.isMulti)
    }
}
