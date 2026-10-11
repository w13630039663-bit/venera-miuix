package com.venera.desktop.gallery.ui

import com.venera.compose.gallery.data.GalleryPost
import com.venera.compose.gallery.data.GallerySite
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 桌面第三十二批 批 32 · 页尾那句「少了谁」（主语按**发没发过分**档，而旧账随答上而销）。
 *
 * ## 抓到的两处
 *
 * 发现屏那一句从前写的是「没发出去的站：X——原因」，而那张名单里**混着两种站**：
 * 发车前被拦的（没凭据、没标签）与**发出去而没答上的**（超时、403）。后者恰恰是发出去了的那一站，
 * 屏上却说它没发出去 —— 主语假。另一半：上一轮没答上的那一站这一轮答上了，它的旧原因不销，
 * 于是墙上摆着它的图而同一行说它没答上（与批 31 在 `fetchWallPage` 里抓到的同形，那一屏改了一处，这一屏是另一处）。
 *
 * ## 修法只有一颗判据
 *
 * 主语不另立一本账：`DesktopGalleryBatch.queries` 的 KDoc 写的就是"这一轮**真发出去**的标签串"，
 * 所以"发没发过"这一问由它答（[desktopMissingReading] 收 `dispatched`）。
 * 两屏的名单形状不同（最新/排行另存一份 `silent`，发现屏把两样混进 `failures`），
 * 而**同一句话只许一处拼**（批 12 定过的那条：四处同一句而其中一处要自己躲，下一处一定会忘）。
 */
class DesktopMissingSitesTest {

    private val yandere = GallerySite.YANDERE
    private val gelbooru = GallerySite.GELBOORU
    private val safebooru = GallerySite.SAFEBOORU

    private fun post(site: GallerySite, id: Long) = GalleryPost(
        site = site,
        id = id,
        tags = "missing",
        previewUrl = "https://img/$site-$id.jpg",
        fileExt = "jpg",
    )

    /** ① 四档各念一句：只有没答上 / 只有没发车 / 两样都有 / 两样都空（不念）。 */
    @Test
    fun `① 四档各念一句而都空时不念`() {
        assertEquals(
            "发出去而没答上的那一站，主语是「没答上」",
            "yande.re没答上——超过 15s 没返回",
            desktopMissingReading(dispatched = setOf(yandere), failures = mapOf(yandere to "超过 15s 没返回")),
        )
        assertEquals(
            "压根没被问的那一站，主语是「没发车」",
            "Gelbooru没发车——需要账号",
            desktopMissingReading(dispatched = emptySet(), failures = mapOf(gelbooru to "需要账号")),
        )
        val both = requireNotNull(
            desktopMissingReading(
                dispatched = setOf(yandere),
                failures = mapOf(yandere to "超时", gelbooru to "需要账号"),
            ),
        )
        assertEquals(
            "两样都有时各念一句、用「；」连着（次序跟着站表，不跟着传入名单）",
            "yande.re没答上——超时；Gelbooru没发车——需要账号",
            both,
        )
        assertNull("两样都空 ⇒ 交回 null，那一格整段不画", desktopMissingReading(setOf(yandere), emptyMap()))
    }

    /**
     * ② 主语不许混：同一份名单，"发没发过"换了档，念出来的两句必须不同。
     *
     * 这一条是那句「没发出去的站」被钉成的反面对照 —— 从前发现屏把超时那一站也说成没发出去。
     */
    @Test
    fun `② 同一站换了发没发过就换一句`() {
        assertEquals(
            "发出去的那一站主语必须是「没答上」，句里不许出现「没发车」",
            "Safebooru没答上——403",
            desktopMissingReading(setOf(safebooru), mapOf(safebooru to "403")),
        )
        assertEquals(
            "压根没被问的那一站主语必须是「没发车」，句里不许出现「没答上」",
            "Safebooru没发车——403",
            desktopMissingReading(emptySet(), mapOf(safebooru to "403")),
        )
    }

    /**
     * ③ 真跑发现屏那条链：两样站各归各档，而这一轮答上的那一站旧账要销。
     *
     * `fetchForYouPage` 从前写 `outcomes = HashMap(reasons)` 再覆盖 —— 答上了的腿不在"这一轮的原因"里，
     * 上一轮那句就活了下来（与批 31 抓到的同一族，只是这一屏是另一处）。
     */
    @Test
    fun `③ 发现屏的名单各归各档而答上就销账`() {
        val boards = ScriptedBoards(
            pageSize = mapOf(yandere to 2, safebooru to 2),
            pages = mapOf(
                yandere to listOf(
                    Result.success(listOf(post(yandere, 1), post(yandere, 2))),
                    Result.success(listOf(post(yandere, 3), post(yandere, 4))),
                ),
                // 脚本按**页号**索引，所以这里放的是"答上那一回的内容"；
                // 第一笔的失败由 failFirst 注入（与批 31 那颗夹具同一套写法）。
                safebooru to listOf(Result.success(listOf(post(safebooru, 11), post(safebooru, 12)))),
            ),
            failFirst = setOf(safebooru),
        )
        val ports = fakeGalleryPorts(boards)
        val base = DesktopGalleryBatch(seed = 7L, queries = mapOf(yandere to "a", safebooru to "b"))
        val silent = mapOf(gelbooru to "需要账号")

        val first = runBlocking { fetchForYouPage(ports, silent, base, emptySet()).getOrThrow() }
        assertEquals("第一笔的缺席名单该是没凭据与没答上那两站", setOf(gelbooru, safebooru), first.failures.keys)
        val firstReading = requireNotNull(desktopMissingReading(first.queries.keys, first.failures))
        assertTrue(
            "各归各档：没凭据那站念「没发车」在前，发出去而没答上那站念「没答上」（次序跟着站表；原因那句由守卫给，不钉它）\n实际读数：$firstReading",
            firstReading.startsWith("Gelbooru没发车——需要账号；Safebooru没答上——"),
        )
        assertTrue("答上了的那一站被写进了缺席名单：" + firstReading, !firstReading.contains("yande.re"))

        val second = runBlocking { fetchForYouPage(ports, first.failures, first, emptySet()).getOrThrow() }
        assertEquals(
            "这一轮答上了的那一站，上一轮那句「没答上」必须一起销掉",
            setOf(gelbooru),
            second.failures.keys,
        )
        assertEquals("墙上该是第一轮两张 + 第二轮四张", 6, second.posts.size)
        assertEquals(
            "只剩没发车那一句",
            "Gelbooru没发车——需要账号",
            requireNotNull(desktopMissingReading(second.queries.keys, second.failures)),
        )
    }

    /**
     * ④ 三面墙各吃那颗件（一处一份手拼就是批 12 判过的那族）。
     *
     * 钉调用点而不是钉句子：句子进件之后，"那一屏里含那串字面"的锚点会红在搬家上而不是红在行为上（坑 ㉛）。
     */
    @Test
    fun `④ 三面墙都调那颗件而不各写一份`() {
        listOf("DesktopLatestPane.kt", "DesktopRankingPane.kt", "DesktopForYouPane.kt").forEach { name ->
            val code = DesktopSourceTree.codeText(DesktopSourceTree.desktopUiSource(name))
            assertTrue("$name 没吃那颗件：$name", code.contains("desktopMissingReading("))
            // 调用点还得传**对那枚主语**：吃的是发没发过车这一问（`queries.keys`），而不是手里那份名单的键。
            // 这一枚是接线锚而不是句子锚 —— 扰动 z7（排行屏改喂 `state.silent.keys`）只有它抓得到，
            // 而排行屏那句是 @Composable 里的读数，没有无头组合可跑，真跑那一格交回给这颗件自己。
            assertTrue("$name 那颗件喂错了主语（该是这一轮发出去的腿）", code.contains("desktopMissingReading(batch.queries.keys"))
            // 反向：不许再留各屏自己拼的那两串字面（它们只该住在那颗件里）。
            // 钉**带破折号那一串**而不是钉「没答上」整词：尺子咬到过一条合法的屏上字 ——
            // 最新那一屏的「这一轮没取成…与「某一站没答上」是两句话」不是拼那句话，是把动词拿来**对照**
            // （手册 §0 坑 ㉜：「名字在不在」的尺子会咬到自己）。要禁的是"动词紧跟站名、后面挂原因"这个**拼法**。
            listOf("没答上——", "没发车——").forEach { banned ->
                assertTrue("$name 还在自己拼「$banned」（那句话只许住在那颗件里）", code.contains(banned).not())
            }
        }
        val readings = DesktopSourceTree.codeText(DesktopSourceTree.desktopUiSource("DesktopWallReadings.kt"))
        assertTrue("对照组失效：那颗件里连分隔符都没读到", readings.contains("DESKTOP_SITE_SEPARATOR"))
        assertTrue("那颗件里该有那两串字面", readings.contains("没答上——") && readings.contains("没发车——"))
    }
}
