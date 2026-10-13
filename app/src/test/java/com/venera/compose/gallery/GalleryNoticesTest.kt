package com.venera.compose.gallery

import com.venera.compose.gallery.data.GallerySite
import com.venera.compose.gallery.domain.GalleryLegGuard
import com.venera.compose.gallery.domain.GalleryNoticeAction
import com.venera.compose.gallery.domain.GalleryNoticeKind
import com.venera.compose.gallery.domain.GalleryNoticeTone
import com.venera.compose.gallery.domain.GallerySearchSource
import com.venera.compose.gallery.domain.galleryNotices
import com.venera.compose.gallery.domain.noRecommendTagsReason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 画廊「这一轮少了什么」那一块的**归类判据**（2026-10-12 改造）。
 *
 * ## 这一层存在的理由，以及为什么必须有单测
 *
 * 改造前，三处页面把缺席原因拼成同一句灰字（`站：原因 · 站：原因`）。三类处境的
 * **出路完全不同**却在屏上长得一模一样：
 * - 没配账号 → 要用户去设置里填两个字段（有真入口）；
 * - 这一笔出错了 → 重试（有真动作）；
 * - 这一站这轮没货 / 还没收藏 → 什么都不用做（**不许**给按钮）。
 *
 * 所以"哪一句属于哪一档"是一个**有对错的判据**，不是排版偏好。它错了不会崩、
 * 不会报错，只会在屏上换个颜色、多一个或**少一个**按钮 —— 正是本仓反复记为
 * "最难查的一类"。这里把它钉死。
 */
class GalleryNoticesTest {

    @Test
    fun `未配账号那一档归警告 且给配置入口`() {
        val notices = galleryNotices(
            mapOf(GallerySite.GELBOORU to GallerySearchSource.NEEDS_ACCOUNT_REASON),
        )
        assertEquals(1, notices.size)
        val notice = notices.single()
        assertEquals(GalleryNoticeKind.MissingAccount, notice.kind)
        assertEquals(GalleryNoticeTone.Warning, notice.tone)
        assertEquals(GalleryNoticeAction.ConfigureAccount, notice.action)
        assertEquals(listOf(GallerySite.GELBOORU), notice.sites)
    }

    @Test
    fun `超时与未知报错归错误档 且给重试`() {
        val notices = galleryNotices(
            mapOf(
                GallerySite.YANDERE to "超过 12s 没返回",
                GallerySite.SAFEBOORU to "HTTP 500",
            ),
        )
        val notice = notices.single()
        assertEquals(GalleryNoticeKind.LegFailed, notice.kind)
        assertEquals(GalleryNoticeTone.Error, notice.tone)
        assertEquals(GalleryNoticeAction.Retry, notice.action)
        // 两站同类 → 并成**一条**（不是两条各说一遍），站序按站表序。
        assertEquals(listOf(GallerySite.YANDERE, GallerySite.SAFEBOORU), notice.sites)
    }

    @Test
    fun `站方答上了却没内容归信息档 且不给任何按钮`() {
        val notices = galleryNotices(mapOf(GallerySite.YANDERE to GalleryLegGuard.NO_CONTENT))
        val notice = notices.single()
        assertEquals(GalleryNoticeKind.NoContent, notice.kind)
        assertEquals(GalleryNoticeTone.Info, notice.tone)
        // ⚠️ 这一条是整个改造里最要紧的一个"不给"：站方这一轮就是没货，
        // 同一串标签再发一次还是那批没有 —— 摆一枚「重试」就是本仓明令禁止的假开关。
        assertNull("信息档不许有出口", notice.action)
    }

    @Test
    fun `还没有可推荐标签归信息档 且不给任何按钮`() {
        val notices = galleryNotices(mapOf(GallerySite.GELBOORU to noRecommendTagsReason(GallerySite.GELBOORU)))
        val notice = notices.single()
        assertEquals(GalleryNoticeKind.NoRecommendTags, notice.kind)
        assertNull("这一站只是还没被收藏过，重发不会变 —— 不许给按钮", notice.action)
    }

    @Test
    fun `三档并存时出错的排在最前`() {
        // 一次凑齐三档，检查屏上读序 = 严重度从高到低。
        val notices = galleryNotices(
            mapOf(
                GallerySite.YANDERE to GalleryLegGuard.NO_CONTENT,
                GallerySite.GELBOORU to GallerySearchSource.NEEDS_ACCOUNT_REASON,
                GallerySite.SAFEBOORU to "超过 12s 没返回",
            ),
        )
        assertEquals(
            listOf(GalleryNoticeTone.Error, GalleryNoticeTone.Warning, GalleryNoticeTone.Info),
            notices.map { it.tone },
        )
    }

    @Test
    fun `条序与站序都不跟着 Map 的插入顺序飘`() {
        // 同一份内容，两种插入顺序必须得到**逐项相同**的结果 ——
        // 不然 `remember` 与 diff 会跟着抖（与腿序那条纪律同源）。
        val a = galleryNotices(
            linkedMapOf(
                GallerySite.SAFEBOORU to "超时",
                GallerySite.YANDERE to "超时",
                GallerySite.GELBOORU to GallerySearchSource.NEEDS_ACCOUNT_REASON,
            ),
        )
        val b = galleryNotices(
            linkedMapOf(
                GallerySite.GELBOORU to GallerySearchSource.NEEDS_ACCOUNT_REASON,
                GallerySite.YANDERE to "超时",
                GallerySite.SAFEBOORU to "超时",
            ),
        )
        assertEquals(a.map { it.kind to it.sites }, b.map { it.kind to it.sites })
        // 出错那一档里的两站也必须按站表序（YANDERE 在 SAFEBOORU 之前）。
        assertEquals(listOf(GallerySite.YANDERE, GallerySite.SAFEBOORU), a.first().sites)
    }

    @Test
    fun `空表返回空表 页面据此整块不摆`() {
        assertTrue(galleryNotices(emptyMap()).isEmpty())
    }

    @Test
    fun `整句仍然以标题半句起头 归类判据与文案是同一份真相`() {
        // 拆成"标题 + 正文"两半之后，归类那一层是按标题前缀认的。
        // 这条用例把两者的关系钉死：改文案时只动正文不会红，但把标题换掉会。
        assertTrue(
            GallerySearchSource.NEEDS_ACCOUNT_REASON
                .startsWith(GallerySearchSource.NEEDS_ACCOUNT_TITLE),
        )
        // 拼回去仍是原来那句话（一个字都没变）—— 页面按整句上屏。
        assertEquals(
            "Gelbooru 需要先配置账号才能用：它的接口对匿名请求一律拒绝（401）。" +
                "到「设置 → 画廊」里的「画廊站点账号」卡中填 User ID 与 API Key 即可；" +
                "yande.re 不受影响，照常能搜。",
            GallerySearchSource.NEEDS_ACCOUNT_REASON,
        )
    }

    @Test
    fun `一句原因带出的原话一个字都不丢`() {
        // `reasons` 里仍原样存着那句话（改造不许把"为什么"吞掉），
        // 而出错那一档的正文就是逐站念它 —— 两件事一起钉。
        val reason = "yande.re：超过 12s 没返回"
        val notices = galleryNotices(mapOf(GallerySite.YANDERE to "超过 12s 没返回"))
        assertEquals("超过 12s 没返回", notices.single().reasons.getValue(GallerySite.YANDERE))
        assertEquals(reason, notices.single().body)
    }

    @Test
    fun `正文不许复述标题`() {
        // 2026-10-13 真机截图上读出来的缺陷：那一版把 reasons 一律当正文，
        // 于是 Gelbooru 那块成了「…需要先配置账号才能用」+「…需要先配置账号才能用：它的接口…」，
        // Safebooru 那块更糟（标题与正文几乎同一句）。这条用例把"分工"钉住。
        val missingAccount = galleryNotices(
            mapOf(GallerySite.GELBOORU to GallerySearchSource.NEEDS_ACCOUNT_REASON),
        ).single()
        // 正文**不含**标题那句话，但含标题答不出的那半句（为什么 + 怎么办）。
        assertTrue(
            "未配账号那一档的正文不许复述标题：${missingAccount.body}",
            !missingAccount.body.contains(GallerySearchSource.NEEDS_ACCOUNT_TITLE),
        )
        assertEquals(GallerySearchSource.NEEDS_ACCOUNT_BODY, missingAccount.body)
        // 拼回去仍是原来那句话（一个字都没变）。
        assertEquals(
            GallerySearchSource.NEEDS_ACCOUNT_REASON,
            GallerySearchSource.NEEDS_ACCOUNT_TITLE + "：" + GallerySearchSource.NEEDS_ACCOUNT_BODY,
        )

        val noTags = galleryNotices(
            mapOf(GallerySite.SAFEBOORU to noRecommendTagsReason(GallerySite.SAFEBOORU)),
        ).single()
        assertTrue(
            "还没有可推荐标签那一档的正文不许复述标题：${noTags.body}",
            !noTags.body.contains("还没有可推荐的标签"),
        )
        // 它要说的是"为什么"与"去哪儿解决"。
        assertTrue(noTags.body.contains("收藏"))

        val noContent = galleryNotices(mapOf(GallerySite.YANDERE to GalleryLegGuard.NO_CONTENT)).single()
        assertTrue(
            "没内容那一档的正文不许复述标题：${noContent.body}",
            !noContent.body.contains("这一轮没有内容"),
        )
        // 它要说清"这不是出错"，否则用户会一直重试。
        assertTrue(noContent.body.contains("不是出错"))
    }
}
