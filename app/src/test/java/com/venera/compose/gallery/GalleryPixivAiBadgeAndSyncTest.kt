package com.venera.compose.gallery

import com.venera.compose.gallery.data.pixivAiFlagOf
import com.venera.compose.gallery.domain.GalleryAi
import com.venera.compose.gallery.domain.GalleryHomeDomain
import com.venera.compose.gallery.domain.GallerySearch
import com.venera.compose.testsupport.RepoSources
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 批 117（AI 角标接站方硬位 + 补渲染面）、批 118（历史分域各存一份键）、
 * 批 120（pixiv 收藏同步到站方）的接线（2026-10-18）。
 *
 * 三件事共同的起因都是"静默"：角标整域零枚、历史一行凭空少、收藏只在本地而屏上不说。
 * 所以这里的判据**不钉措辞**，钉的是"数据填没填上、画不画得到、写口通不通、键在哪一枚"。
 */
class GalleryPixivAiBadgeAndSyncTest {

    private val appMain = RepoSources.appMain

    private fun src(rel: String): String {
        val f = File(appMain, rel)
        assertTrue("找不到 $rel，量具失效", f.isFile)
        return f.readText().replace("\r\n", "\n")
    }

    /** 注释行不参与匹配（与 `TokenBudgetFrozenTest` 同一把尺子）。 */
    private fun code(rel: String): List<String> = src(rel).lines().map { it.trim() }
        .filter { !it.startsWith("//") && !it.startsWith("*") && !it.startsWith("/*") }

    private fun List<String>.countWith(needle: String): Int = count { it.contains(needle) }

    // ── 一、站方硬位的翻译与判据 ─────────────────────────────────────────

    @Test
    fun `aiType 三形各落一档，缺键不是非 AI`() {
        // 读数（2026-10-18）：`AI生成画像` 60 条里 59 枚 =2、`NovelAI` 58 枚 =2，
        // 对照词 `オリジナル` =1 有 37 / =2 有 22，而那一页带 AI 味标签的 5 条全部 =2。
        assertEquals(true, pixivAiFlagOf("2"))
        assertEquals(false, pixivAiFlagOf("1"))
        // `0` 与缺键都不是"站方说这不是 AI"：把它读成 false 就是替站方编一个结论。
        assertEquals(null, pixivAiFlagOf("0"))
        assertEquals(null, pixivAiFlagOf(null))
        assertEquals(null, pixivAiFlagOf(""))
    }

    @Test
    fun `硬位优先，硬位缺席才退回词表，裸 ai 仍然不算`() {
        assertTrue(GalleryAi.isAiMarked(siteAiFlag = true, tags = emptyList()))
        assertFalse(GalleryAi.isAiMarked(siteAiFlag = false, tags = emptyList()))
        assertTrue(GalleryAi.isAiMarked(siteAiFlag = null, tags = listOf("NovelAI")))
        assertFalse(GalleryAi.isAiMarked(siteAiFlag = null, tags = listOf("ai")))
        // 两种信号都没有 = 不摆（没有信号就不摆这条口径不变）。
        assertFalse(GalleryAi.isAiMarked(siteAiFlag = null, tags = emptyList()))
    }

    @Test
    fun `三条 pixiv 腿都把硬位填进模型`() {
        val feed = code("com/venera/compose/gallery/data/PixivFeedParse.kt")
        // 匿名搜索腿 + 单张详情腿各一处；榜单腿**没有**这一格，留 null（那条理由写在它的注释里）。
        assertEquals("匿名腿与详情腿要各填一处", 2, feed.countWith("siteAiFlag = pixivAiFlagOf("))
        val app = code("com/venera/compose/gallery/data/PixivAppParse.kt")
        assertEquals("登录腿那颗行解析要填（推荐/榜单/书签/搜索共用）", 1, app.countWith("siteAiFlag = pixivAiFlagOf("))
    }

    @Test
    fun `角标在四处表面都画得到`() {
        // 从前只有墙上那枚卡与 pixiv 卡会画 ⇒ 用户在首页那一排、大图页里翻遍所有站点也见不到一枚。
        val surfaces = listOf(
            "com/venera/compose/gallery/ui/GalleryScreen.kt",
            "com/venera/compose/gallery/ui/GalleryPixivHome.kt",
            "com/venera/compose/gallery/ui/GalleryHomeSections.kt",
            "com/venera/compose/gallery/ui/GalleryPostScreen.kt",
        )
        surfaces.forEach { rel ->
            assertTrue("${rel.substringAfterLast('/')} 不画角标", code(rel).countWith("GalleryAiPill()") >= 1)
        }
        val total = surfaces.sumOf { code(it).countWith("GalleryAiPill()") }
        assertTrue("四处表面至少各一枚，实测 $total", total >= 4)
    }

    @Test
    fun `每处角标都吃同一枚开关，不各读各的默认值`() {
        listOf(
            "com/venera/compose/gallery/ui/GalleryScreen.kt",
            "com/venera/compose/gallery/ui/GalleryPixivHome.kt",
            "com/venera/compose/gallery/ui/GalleryHomeSections.kt",
            "com/venera/compose/gallery/ui/GalleryPostScreen.kt",
        ).forEach { rel ->
            assertTrue("$rel 没读 galleryAiBadge", code(rel).countWith(".galleryAiBadge.collectAsState()") >= 1)
        }
        val prefs = code("com/venera/compose/data/prefs/VeneraPreferences.kt")
        assertTrue("开关键名漂了", prefs.any { it.contains("\"pref_gallery_ai_badge\"") })
        // 默认档是"开"：关掉它才是需要用户动手的那一档。
        assertTrue("默认档不再是开", prefs.any { it.contains("KEY_GALLERY_AI_BADGE, true") })
    }

    // ── 二、收藏同步到站方 ──────────────────────────────────────────────

    @Test
    fun `写口只有 pixiv 这一站，取消也对称撤`() {
        val adapter = code("com/venera/compose/gallery/data/android/GalleryAdaptersAndroid.kt")
        assertTrue("没有先按站点收口（booru 三站会被拿去打 pixiv 的写口）",
            adapter.any { it.contains("post.site != GallerySite.PIXIV") })
        assertTrue("加那一笔没接", adapter.any { it.contains("appClient.bookmarkAdd(bearer, post.id") })
        assertTrue("本地取消时不撤站方那份（下次进 pixiv 主页还能看见）",
            adapter.any { it.contains("appClient.bookmarkRemove(bearer, post.id") })
        // 本地先落、站方失败要出声：两条读数都在。
        assertTrue("站方失败没有交代", adapter.any { it.contains("站方那份没加上") })
        assertTrue("未登录没有交代", adapter.any { it.contains("没登录 pixiv") })
    }

    @Test
    fun `可见性按会员位给，端点与站方形状一致`() {
        val client = code("com/venera/compose/gallery/data/PixivAppClient.kt")
        assertTrue("restrict 不是二选一", client.any { it.contains("if (private) \"private\" else \"public\"") })
        // 站方自己就不对称：加是 v2、删是 v1（pixivpy aapi.py:610-639 与 pixez api_client.dart:240-269 同读）。
        assertTrue(client.any { it.contains("/v2/illust/bookmark/add") })
        assertTrue(client.any { it.contains("/v1/illust/bookmark/delete") })
        assertTrue("写口没带 Bearer", client.any { it.contains(".post(form)") })
    }

    @Test
    fun `会员位只有一颗，两处都用它`() {
        val shared = src("com/venera/compose/gallery/data/PixivPremium.kt")
        assertTrue(shared.contains("suspend fun of(userKey: String?, fetch: suspend () -> Boolean?): Boolean"))
        assertTrue("搜索那一侧没走这颗", code("com/venera/compose/gallery/ui/GallerySearchViewModel.kt")
            .any { it.contains("PixivPremium.of(userId)") })
        assertTrue("收藏同步没走这颗", code("com/venera/compose/gallery/data/android/GalleryAdaptersAndroid.kt")
            .any { it.contains("PixivPremium.of(userId)") })
    }

    // ── 三、历史按内容域各存一份键 ───────────────────────────────────────

    @Test
    fun `键名在共享层定一次，两端同一个来源`() {
        assertEquals("history_booru", GallerySearch.historyKeyOf(GalleryHomeDomain.BOORU))
        assertEquals("history_pixiv", GallerySearch.historyKeyOf(GalleryHomeDomain.PIXIV))
        assertEquals("history", GallerySearch.LEGACY_SHARED_HISTORY_KEY)
        // 两域各一枚：不同名就等于两端不同轨（桌面那颗直接吃 BOORU 这一枚）。
        assertFalse(GallerySearch.historyKeyOf(GalleryHomeDomain.BOORU) ==
            GallerySearch.historyKeyOf(GalleryHomeDomain.PIXIV))
    }

    @Test
    fun `读写都落在当前域那一枚键上，旧混域档摊一次就删`() {
        val vm = code("com/venera/compose/gallery/ui/GallerySearchViewModel.kt")
        // 写侧两枚（落一条 / 删一条）走 put，清空走 remove —— 三条都必须按域取键，一枚不许再写旧的混域键。
        assertEquals("写侧还有没走域键的", 0, vm.countWith("prefs.put(\"history\""))
        assertEquals("还有没走域键的 remove", 0, vm.countWith("prefs.remove(\"history\""))
        assertTrue("落一条没写域键", vm.any { it.contains("prefs.put(historyKey(domain), encodeGallerySearchHistory(next))") })
        // 清空 = **删掉这一域的键**，不是写入编码后的空表（两端同一语义，桌面那颗 `desktopHistoryClearAll` 走 remove；
        // 写成空表在盘上留下「v2」哨兵，读侧哪天改成"没这个键就带默认词"时会被读成"用户清过"）。
        assertTrue("清空没按域键 remove", vm.any { it.contains("prefs.remove(historyKey(domain))") })
        assertFalse(
            "清空退回成「写一份空表」了（与桌面那颗不同语义）",
            vm.any { it.contains("prefs.put(historyKey(domain), encodeGallerySearchHistory(emptyList()))") },
        )
        // 恢复跟 ViewModel 走（不是跟搜索卡组合走）+ 换域换那一份。
        assertTrue(vm.any { it.contains("acceptHistory(loadHistoryFor(domain))") })
        assertTrue("旧档摊完没删（留着就会读到两份）", vm.countWith("prefs.remove(legacyKey)") >= 1)
    }
}
