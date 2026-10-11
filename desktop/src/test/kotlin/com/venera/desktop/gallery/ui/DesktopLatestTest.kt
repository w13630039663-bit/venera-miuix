package com.venera.desktop.gallery.ui

import com.venera.compose.gallery.data.GallerySite
import com.venera.compose.gallery.domain.GallerySearchSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 「最新」那一屏（桌面第十批 ⑤）。
 *
 * ## 这批为什么能点亮它
 *
 * 手册 §23.9 量完的读数：两站的**默认序就是最新在前**（danbooru 镜像口 ids 递减、yande.re 同），
 * 而空标签在两边都是合法查询（200 非空）。所以"最新"从来不需要给共享接口加一枚排序参数 ——
 * `searchPosts("", page = 1, limit = pageSize)` 就是它。判据 ⑥ 把这条钉住：
 * 谁哪天给 `searchPosts` 加了排序形参，这一条会红，逼他先回答"那两站的默认序算什么"。
 *
 * ## 两种核对混在一颗类里，各按各的规矩
 *
 * ①②⑤ 是纯函数与真数据，**真跑取值**；③④⑥ 钉的是"不该存在的东西"，只能扫源码，
 * 于是每条负向断言都配对照组，且一律只看代码行（`codeText` 跳注释）。
 */
class DesktopLatestTest {

    private val policy = DesktopSourceTree.codeText(
        DesktopSourceTree.desktopUiSource("DesktopLatestPolicy.kt"),
    )

    private val pane = DesktopSourceTree.codeText(
        DesktopSourceTree.desktopUiSource("DesktopLatestPane.kt"),
    )

    /** ① 没配 Gelbooru 凭据 ⇒ 那条腿**发车前就不发**，且原因逐字转交共享那一句。 */
    @Test
    fun 没配凭据时Gelbooru不发车而原因转交共享那句() {
        assertEquals(
            "没配凭据该只发两腿（yande.re 与 Safebooru）",
            listOf(GallerySite.YANDERE, GallerySite.SAFEBOORU),
            desktopWallLegs(gelbooruConfigured = false),
        )
        val silent = desktopWallSilentSites(gelbooruConfigured = false)
        assertEquals("没发出去的站应当恰好点名一枚", listOf(GallerySite.GELBOORU), silent.keys.toList())
        assertEquals(
            "那句原因必须逐字转交 `GallerySearchSource.NEEDS_ACCOUNT_REASON`，桌面不许另写一份",
            GallerySearchSource.NEEDS_ACCOUNT_REASON,
            silent.getValue(GallerySite.GELBOORU),
        )
    }

    /** ② 配了凭据 ⇒ 三腿都发，且"没发出去的站"是空表（不是 null、不是"没有"）。 */
    @Test
    fun 配了凭据时三腿都发而没有不发出去的站() {
        assertEquals(
            "配好凭据就该三腿都发（次序恒为枚举自然序）",
            GallerySite.entries.toList(),
            desktopWallLegs(gelbooruConfigured = true),
        )
        assertTrue(
            "对照组失效：三腿都发时「没发出去的站」必须是空表",
            desktopWallSilentSites(gelbooruConfigured = true).isEmpty(),
        )
    }

    /** ③ 那一屏必须过第四批那层守卫，且墙上三枚偏好显式传下去（漏一枚 = 某一屏静默不生效）。 */
    @Test
    fun 那一屏的墙要过守卫而三枚偏好必须显式传() {
        assertTrue("这一屏没走 `DesktopGalleryWallPolicy.of` —— 那就是又一面没过守卫的墙", pane.contains("DesktopGalleryWallPolicy.of("))
        assertTrue("守卫没拿到分级档", pane.contains("maskMode"))
        assertTrue("守卫没拿到屏蔽 AI 那枚", pane.contains("blockAi"))
        assertTrue("守卫没拿到 contentGuard（规则与模式那两份）", pane.contains("ports.contentGuard"))
        val at = pane.indexOf("DesktopGalleryPostWall(")
        assertTrue("找不到那一面墙的调用点", at >= 0)
        val call = pane.substring(at).take(320)
        listOf("wall = wall", "columnMode", "quality", "aiBadge", "weight(1f)").forEach { arg ->
            assertTrue("那一面墙没收到「$arg」：$call", call.contains(arg))
        }
        // 对照组：真跑过守卫的产物才走这一颗墙。
        assertTrue("对照组失效：没读到那一颗共用的墙", call.isNotEmpty())
    }

    /**
     * ④ 屏上要说清"最新 = 站方默认序"，而**桌面自己一行都不排**。
     *
     * 负向那一半防的是：有人为了让这一屏"看起来像最新"，在桌面侧补一次本地排序 ——
     * 那既不是站方的序（我们只拿到第一页），又会把"这一站没答上"掩盖成"排完就是这样"。
     */
    @Test
    fun 桌面自己不许排序而屏上那句要念出默认序() {
        listOf("sortedWith", "sortBy", "sortedBy", "sorted(", "reversed(").forEach { banned ->
            val where = "$policy\n$pane"
            assertTrue("桌面这一族里出现了本地排序「$banned」—— 最新只许是站方默认序", where.contains(banned).not())
        }
        // 对照组：不排序 ≠ 什么都没做，腿与混排确实转交了共享判据。
        assertTrue("对照组失效：没转交每腿预算件", pane.contains("GalleryLegGuard.guard("))
        assertTrue("对照组失效：没转交跨站混排件", pane.contains("GallerySearchMerge.interleave("))
        assertTrue("屏上那句没念出「默认序」这一层", pane.contains("默认序"))
        assertTrue("屏上那句没念出「桌面不重排」这一层", pane.contains("不重排") || pane.contains("不二次排序"))
    }

    /** ⑤ 那一行**已转正**：内容件在、缺席说明撤掉。 */
    @Test
    fun 最新那一行已转正() {
        val latest = desktopGalleryHomeRows().first { it.key == DesktopGalleryHomeSectionKey.LATEST }
        assertNotNull("「最新」应当有内容件", latest.content)
        assertNull("「最新」转正后不许还挂着缺席说明", latest.reason)
        // ⚠️ 原来这里还数了一遍"首页未实现的行数"（批 28 / 批 85 两次改数都是从这颗开始找地方）。
        // 那个数住在 `DesktopGalleryHomeSectionsTest` ③，一颗件事两把尺子就会漂（批 14 那条），整条撤到这里。
    }

    /**
     * ⑥ 共享接口**不许**因为这一屏而长出排序形参。
     *
     * 给 `searchPosts` 加 `sort` 是要牵动 Android 侧冻结面的（手册 §23.9 那条"防止有人去加用不上的参数"）。
     */
    @Test
    fun 共享取数口不许被加上排序形参() {
        val api = DesktopSourceTree.codeText(
            DesktopSourceTree.androidSource("gallery/data/GalleryBusinessApi.kt"),
        )
        val line = api.lines().first { it.contains("fun searchPosts(") }
        assertTrue(
            "`searchPosts` 被加了排序形参（这一行是：$line）—— 最新用的是站方默认序，不需要它",
            Regex("""searchPosts\([^)]*\b(sort|order)\b""", RegexOption.IGNORE_CASE).find(line) == null,
        )
        // 对照组：那一行确实存在且带三个形参，不是"读空了所以没匹配"。
        assertTrue("对照组失效：那一行形状不对：$line", line.contains("tags:") && line.contains("page:") && line.contains("limit:"))
    }
}
