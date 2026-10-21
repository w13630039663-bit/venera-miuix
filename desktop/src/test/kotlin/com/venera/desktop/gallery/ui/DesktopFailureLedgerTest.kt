package com.venera.desktop.gallery.ui

import com.venera.compose.gallery.data.GalleryPost
import com.venera.compose.gallery.data.GallerySite
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 桌面第三十三批 批 33 · 那本「谁缺席了」的账**只许一处并**。
 *
 * ## 同一条规则被写了四遍，而两处方向是反的
 *
 * 批 31 在 `fetchWallPage` 抓到"这一轮答上的那一站，页尾还挂着上一轮那句「没答上」"，
 * 批 32 在 `fetchForYouPage` 抓到同一处形状 —— 本批顺着"还有谁自己写了一遍"数下去，
 * 又数到**两屏**：搜索屏写的是 `base.failures + 这一轮的原因`（并集，旧的赢），
 * 单人页写的是 `base.failures.toMutableMap()` 再 put（只有又失败才改得动）。
 * 于是那两屏今天真会念出这句假话：**墙上摆着那一站的图，同一行说它没答上**
 * （搜索屏两腿时最容易走到：一站抖动一次之后翻页，它就永久挂在名单里）。
 *
 * ## 为什么收成一颗纯函数而不是各屏各改一行
 *
 * 规则只有一句：**这一轮答过的腿只看这一轮的答案**，旧账只留给这一轮压根没被问的那几条腿（到底的那一站）。
 * 它住在 `fetchXxxPage` 里时只能靠扫表达式钉（批 22 那条老账），抽出来之后 ①②③ 三条能真跑；
 * 而"只许一处并"由 ④ 那把尺子钉着 —— 三处并集形状各禁一遍，第四处再冒出来就红。
 */
class DesktopFailureLedgerTest {

    private val yandere = GallerySite.YANDERE
    private val gelbooru = GallerySite.GELBOORU
    private val safebooru = GallerySite.SAFEBOORU

    private fun post(site: GallerySite, id: Long) = GalleryPost(
        site = site,
        id = id,
        tags = "ledger",
        previewUrl = "https://img/$site-$id.jpg",
        fileExt = "jpg",
    )

    /** ① 那颗账本身四档各一个答案（真跑，不扫源码）。 */
    @Test
    fun `① 答过就看这一轮而没问的才留旧账`() {
        assertEquals(
            "这一轮问过了而答上了 ⇒ 上一轮那句要销掉",
            emptyMap<GallerySite, String>(),
            desktopFailureLedgerAfter(previous = mapOf(yandere to "超时"), asked = setOf(yandere), fresh = emptyMap()),
        )
        assertEquals(
            "这一轮压根没问的那一站（到底的）旧账要留着",
            mapOf(yandere to "超时"),
            desktopFailureLedgerAfter(previous = mapOf(yandere to "超时"), asked = emptySet(), fresh = emptyMap()),
        )
        assertEquals(
            "这一轮又失败了 ⇒ 说这一轮那句，不留上一轮的旧话",
            mapOf(safebooru to "403"),
            desktopFailureLedgerAfter(previous = mapOf(safebooru to "上一轮那句"), asked = setOf(safebooru), fresh = mapOf(safebooru to "403")),
        )
        assertEquals(
            "两站各归各档：问过的按这一轮、没问的按旧账",
            mapOf(gelbooru to "需要账号"),
            desktopFailureLedgerAfter(
                previous = mapOf(yandere to "超时", gelbooru to "需要账号"),
                asked = setOf(yandere),
                fresh = mapOf(gelbooru to "需要账号"),
            ),
        )
    }

    /** ② 单人页真跑：第一笔抖了、第二笔答上 ⇒ 名单清空而墙上有货。 */
    @Test
    fun `② 单人页第二笔答上就不该还挂着第一笔那句`() {
        val target = DesktopArtistTarget(yandere, "anmi")
        val base = DesktopGalleryBatch(seed = 0L, queries = mapOf(yandere to "anmi"))
        val first = desktopArtistBatchAfter(base, target, page = 1, posts = emptyList(), answered = false, reason = "这一站抖了")
        assertEquals("第一笔没答上而要挂着缺席原因", setOf(yandere), first.failures.keys)

        val second = desktopArtistBatchAfter(
            first,
            target,
            page = 1,
            posts = listOf(post(yandere, 1), post(yandere, 2)),
            answered = true,
            reason = null,
        )
        assertEquals("这一轮答上了，页尾那句「没答上」必须一起销掉", emptySet<GallerySite>(), second.failures.keys)
        assertEquals("销账顺带把墙上的货也弄没了（两张该摆着）", 2, second.posts.size)
    }

    /** ③ 搜索屏真跑（吃 fake 端口那层）：两腿各走两笔。 */
    @Test
    fun `③ 搜索屏第二笔答上就不该还挂着第一笔那句`() {
        val boards = ScriptedBoards(
            pageSize = mapOf(yandere to 2, safebooru to 2),
            pages = mapOf(
                yandere to listOf(
                    Result.success(listOf(post(yandere, 1), post(yandere, 2))),
                    Result.success(listOf(post(yandere, 3), post(yandere, 4))),
                ),
                safebooru to listOf(Result.success(listOf(post(safebooru, 11), post(safebooru, 12)))),
            ),
            failFirst = setOf(safebooru),
        )
        val ports = fakeGalleryPorts(boards)
        val base = DesktopGalleryBatch(seed = 3L, queries = mapOf(yandere to "q", safebooru to "q"))
        val first = runBlocking { fetchSearchPage(ports, base).getOrThrow() }
        assertEquals("第一笔那一站抖了要点名", setOf(safebooru), first.batch.failures.keys)

        val second = runBlocking { fetchSearchPage(ports, first.batch).getOrThrow() }
        assertEquals("补上之后失败名单要清空", emptySet<GallerySite>(), second.batch.failures.keys)
        assertEquals("墙上该是第一轮两张 + 第二轮四张", 6, second.batch.posts.size)
    }

    /**
     * ④ 四处调用点各吃那颗件，而三种"自己并"的形状各禁一遍。
     *
     * 反向那三条不是修辞：并集方向写反**不崩不报错**，只有屏上那一句是假的，
     * 而"少掉的那一批图没人会去数"这条老规矩说明它能量到五批不被发现（手册 §0 坑 ㉓）。
     */
}
