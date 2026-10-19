package com.venera.compose.gallery.domain

import com.venera.compose.gallery.data.PixivRestrict
import com.venera.compose.testsupport.RepoSources
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * pixiv 个人账号页「公开 / 非公开 / 全部」那一档的判据（方案 `docs/rounds/gallery-pixiv-account-page-2026-10-19.html` §七）。
 *
 * 这一层要钉的东西全是**错了不会报错、只会静默少一批**的那一类：
 *
 * - 「全部」要是被实现成"传一枚更宽的 restrict 值"，屏上写着全部而只读了公开 ——
 *   站方根本没有那枚值，所以这条只能靠"发两趟"落地，而两趟这件事最容易在后续改动里被悄悄收成一趟；
 * - 到底判据要是只看第一条腿，私密那批永远进不来而页脚已经在念"都摆完了"；
 * - 合屏的顺序要是交给"哪趟先到货"，每次刷新墙都在跳。
 *
 * 后半部分是**源码尺子**（本仓没有 Robolectric，composable 组合测不到；参数面的缺陷同样测不到）：
 * `restrict` 一旦被谁加回默认值，编译器逼调用点表态这件事就没了，只有文本能钉住。
 */
class PixivAccountFeedTest {

    private fun code(rel: String): List<String> {
        val f = File(RepoSources.appMain, rel)
        assertTrue("找不到 $rel，量具失效（根：${RepoSources.root.absolutePath}）", f.isFile)
        return f.readText().replace("\r\n", "\n").lines().map { it.trim() }
            .filter { !it.startsWith("//") && !it.startsWith("*") && !it.startsWith("/*") }
    }

    private val appClient = "com/venera/compose/gallery/data/PixivAppClient.kt"
    private val contract = "com/venera/compose/gallery/data/GalleryBusinessApi.kt"

    // ── 档位本身 ──

    @Test
    fun `全部那一档是两趟 不是一枚更宽的参数`() {
        // 站方的 restrict 只有 public / private 两个值。"全部"必须是两趟，
        // 被改成"发 public 然后声称含私密"就是本仓反复立案的假开关。
        assertEquals(listOf(PixivRestrict.Public, PixivRestrict.Private), PixivReadScope.All.legs)
        assertEquals(listOf(PixivRestrict.Public), PixivReadScope.Public.legs)
        assertEquals(listOf(PixivRestrict.Private), PixivReadScope.Private.legs)
    }

    @Test
    fun `两趟的先后是公开在前 那决定了合屏的顺序`() {
        // 顺序写死在枚举的 legs 上，不靠到货先后（见 legs 那段的注释）。
        assertEquals(PixivRestrict.Public, PixivReadScope.All.legs.first())
    }

    @Test
    fun `只有公开那一档不含私密`() {
        // 这一枚决定屏上那句"这里有你标为非公开的东西"常驻点名摆不摆。
        assertFalse(PixivReadScope.Public.showsPrivate)
        assertTrue(PixivReadScope.Private.showsPrivate)
        assertTrue(PixivReadScope.All.showsPrivate)
    }

    @Test
    fun `三档在屏上的话各不相同`() {
        // 三档挤在同一枚下拉里，同名或近似就是用户点了看不出切没切。
        val labels = PixivReadScope.entries.map { it.screenLabel }
        assertEquals(3, labels.distinct().size)
        assertEquals(setOf("全部", "公开", "非公开"), labels.toSet())
    }

    // ── 到底 ──

    @Test
    fun `一条腿到底不算整屏到底`() {
        val oneDone = listOf(
            PixivLegCursor(PixivRestrict.Public, null),
            PixivLegCursor(PixivRestrict.Private, "https://app-api.pixiv.net/next?max_bookmark_id=1"),
        )
        assertFalse(pixivFeedExhausted(oneDone))

        val bothDone = listOf(
            PixivLegCursor(PixivRestrict.Public, null),
            PixivLegCursor(PixivRestrict.Private, null),
        )
        assertTrue(pixivFeedExhausted(bothDone))
    }

    @Test
    fun `一条游标都没记时不算到底`() {
        // 空表 = 这一屏还没发过趟。念成"到底了"会让首屏在装载前就摆出"都摆完了"。
        assertFalse(pixivFeedExhausted(emptyList()))
    }

    // ── 合屏 ──

    @Test
    fun `合屏公开的在前 同键只留第一枚`() {
        val merged = mergePixivLegs(
            legs = listOf(
                listOf("pixiv:1", "pixiv:2"),
                listOf("pixiv:2", "pixiv:3"),
            ),
            dedupKey = { it },
        )
        assertEquals(listOf("pixiv:1", "pixiv:2", "pixiv:3"), merged)
    }

    @Test
    fun `同一位画师同名两张不算重复`() {
        // 去重键必须是身份（站方作品号），不是标题/作者 —— 用标题去重会把两张真图吃掉一张，
        // 而那一屏看起来只是"少了点东西"。这里用同一 title 不同 uid 的两枚把方向钉住。
        val merged = mergePixivLegs(
            legs = listOf(listOf("pixiv:10"), listOf("pixiv:11")),
            dedupKey = { it },
        )
        assertEquals(2, merged.size)
    }

    @Test
    fun `两趟都空回空表 不猜`() {
        assertEquals(emptyList<String>(), mergePixivLegs(listOf(emptyList<String>(), emptyList()), { it }))
        assertEquals(emptyList<String>(), mergePixivLegs<String>(emptyList(), { it }))
    }

    // ── 源码尺子：参数面 ──

    @Test
    fun `三条读腿的 restrict 只从枚举取值 一处字面量都没有`() {
        val body = code(appClient)
        // 关注列表 / 关注动态 / 书签三条读腿各一枚。
        assertEquals(
            "三条读腿各发一枚 restrict=restrict.param", 3,
            body.count { it.contains("""addQueryParameter("restrict", restrict.param)""") },
        )
        // 写死字面量那一族（2026-10-19 之前书签与关注两条就是 `"public"` 写死）。
        // 只禁 query 那一形：两条写腿发的是 form 的 restrict，由 private 那枚布尔决定，不在禁内。
        val offenders = body.filter { it.contains("""addQueryParameter("restrict", """") }
        assertTrue("restrict 不许再写死字面量，实测：$offenders", offenders.isEmpty())
    }

    @Test
    fun `关注动态那条腿的端点只有一处声明`() {
        assertEquals(
            1,
            code(appClient).count {
                it.contains("""FOLLOW_ILLUST_FEED = "https://app-api.pixiv.net/v2/illust/follow"""")
            },
        )
    }

    @Test
    fun `restrict 形参不给默认值`() {
        // 一旦有人加 `restrict: PixivRestrict = Public` 来"少改几个调用点"，
        // 漏传就再也不报错，屏上会安静地少一整档 —— 这条尺子钉的就是这件事。
        for ((rel, expected) in listOf(appClient to 3, contract to 3)) {
            val body = code(rel)
            assertEquals("$rel 里 restrict 形参的颗数", expected, body.count { it.startsWith("restrict: PixivRestrict,") })
            assertTrue("$rel 里 restrict 不许有默认值", body.none { it.startsWith("restrict: PixivRestrict =") })
        }
    }

    @Test
    fun `看别人的画师页只许走公开那一档`() {
        // 私密档的权力**只在账号页**：那里读的是号主自己。画师页看的是别人，
        // 传 Private 站方要么回空要么回 403，而"替用户去翻别人的私密书签"这一条本身就不做。
        val artist = code("com/venera/compose/gallery/ui/PixivArtistScreen.kt")
        assertTrue("画师页取云端书签要明说 Public", artist.any { it.contains("PixivRestrict.Public") })
        assertTrue(
            "画师页不许出现 Private：${artist.filter { it.contains("PixivRestrict") }}",
            artist.none { it.contains("PixivRestrict.Private") },
        )
    }

    @Test
    fun `契约与实现与适配层三处各有那三枚`() {
        // 少一处就是契约上加了参数而适配器没跟上（那种漂移的表现是"屏上永远只读公开"）。
        val adapters = code("com/venera/compose/gallery/data/android/GalleryAdaptersAndroid.kt")
        assertEquals(3, adapters.count { it.contains("restrict: PixivRestrict") })
        val feed = code("com/venera/compose/gallery/domain/PixivAccountFeed.kt")
        assertEquals("legs 里两趟都点名", 1, feed.count { it.contains("listOf(PixivRestrict.Public, PixivRestrict.Private)") })
    }
}
