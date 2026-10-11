package com.venera.desktop.gallery.ui

import com.venera.compose.gallery.data.GalleryContentGuard
import com.venera.compose.gallery.data.GalleryPost
import com.venera.compose.gallery.data.GallerySite
import com.venera.compose.security.guard.GuardRule
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 桌面墙的分级判据层（[DesktopGalleryWallPolicy]）。
 *
 * ## 这一批补的是什么缺口
 *
 * 桌面从 S2-A 起就在读 `nsfwMaskMode` 与 `rules`，但**没有任何一处消费它们** ——
 * Android 那把过滤住在 `gallery/ui/`（不在桌面编译面内）。结果同一条内容在手机上被挡、
 * 在桌面上直接摆出来，用户设的「成人内容处理」与「屏蔽 AI 生成」在桌面形同虚设。
 * 所以这里的用例不是"新功能的验收"，是**把一条安全相关的行为差异钉平**。
 *
 * ## 分层
 *
 * 前四条真跑纯函数（能真跑的判据永远比能扫的可靠）；后两条只能扫文本 ——
 * 它们要防的是"以后有人在这里自己写条件 / 把常量抄漂"，那种"不该存在的东西"跑不出来。
 */
class DesktopWallGuardPolicyTest {

    private val guard = FakeGuard()

    private fun post(id: Long, rating: String = "s", tags: String = "", author: String = "") = GalleryPost(
        site = GallerySite.YANDERE,
        id = id,
        rating = rating,
        tags = tags,
        author = author,
    )

    /**
     * ① 用户黑名单**排在模式判定之前**，而成人档才跟着模式走。
     *
     * 这四条各对一个形状：黑名单命中时哪怕"成人内容处理 = 关闭"也必须不摆（否则黑名单是假开关）；
     * 非成人内容任何档都原样摆（不该因为开了 BLUR 就把普通图糊掉）；
     * 成人内容在 BLUR 档是"摆但盖住"，在 HIDE 档才是"不落屏"。
     */
    @Test
    fun 黑名单先判而成人档才跟着模式走() {
        // 黑名单这一问要**真的命中**：不设置这颗，`blockedGalleryRule` 恒给 null，
        // 那"黑名单先判"这一条就成了空转（首跑就是这么绿着过去的，读数却是 0）。
        guard.blockedByTag = "blacklisted"
        val blocked = post(1, rating = "s", tags = "blacklisted")
        val adult = post(2, rating = "e")
        val safe = post(3, rating = "s")

        val off = DesktopGalleryWallPolicy.of(listOf(blocked, adult, safe), "OFF", false, guard)
        assertEquals("黑名单那条必须不摆，且算在屏蔽那一笔而不是分级那一笔", 1, off.blockedCount)
        assertEquals("点名的规则原文就是命中的那一颗", listOf("blacklisted"), off.blockedRules)
        assertEquals("成人内容在 OFF 档原样摆", listOf(2L, 3L), off.cards.map { it.post.id })
        assertEquals("没有按分级收起的", 0, off.hiddenByRating)

        val blur = DesktopGalleryWallPolicy.of(listOf(adult, safe), "BLUR", false, guard)
        assertEquals("BLUR 档两张都落屏", listOf(2L, 3L), blur.cards.map { it.post.id })
        assertEquals("盖住的是那一张成人的", listOf(true, false), blur.cards.map { it.masked })

        val hide = DesktopGalleryWallPolicy.of(listOf(adult, safe), "HIDE", false, guard)
        assertEquals("HIDE 档成人那张不落屏", listOf(3L), hide.cards.map { it.post.id })
        assertEquals("这一笔要念成「按成人内容处理收起」，不是屏蔽规则", 1, hide.hiddenByRating)
        assertEquals(0, hide.blockedCount)
    }

    /** ② 命中黑名单的那条**计数与点名各一份**：条数不去重、规则原文去重。 */
    @Test
    fun 命中同一规则的三条念三条而规则点两颗() {
        val posts = listOf(
            post(1, tags = "landscape"),
            post(2, tags = "landscape"),
            post(3, tags = "landscape"),
        )
        guard.blockedByTag = "landscape"
        val wall = DesktopGalleryWallPolicy.of(posts, "OFF", false, guard)
        assertEquals("三条都被挡，计数照实念 3 条", 3, wall.blockedCount)
        assertEquals("规则原文去重后只点一颗（用户要拿它去规则表里定位）", listOf("landscape"), wall.blockedRules)
        assertTrue("挡完不许还剩卡", wall.cards.isEmpty())
    }

    /** ③ 屏蔽 AI 是**开关控制的**，而且它挡下的那条念成一条规则。 */
    @Test
    fun 屏蔽AI开关关掉时一张都不挡而打开时按站方标签判() {
        val ai = post(1, tags = "1girl ai-generated")
        val plain = post(2, tags = "1girl")

        val off = DesktopGalleryWallPolicy.of(listOf(ai, plain), "OFF", blockAi = false, guard)
        assertEquals("开关关着就不许挡", listOf(1L, 2L), off.cards.map { it.post.id })
        assertEquals(0, off.blockedCount)

        val on = DesktopGalleryWallPolicy.of(listOf(ai, plain), "OFF", blockAi = true, guard)
        assertEquals("打开之后只挡站方真标了 AI 的那张", listOf(2L), on.cards.map { it.post.id })
        assertEquals(listOf(DesktopGalleryWallPolicy.AI_BLOCKED_RULE), on.blockedRules)
    }

    /**
     * ④ 两笔缺席读数各念各的，且**没有东西被挡时一句都不念**。
     *
     * 合并那两句会把"你屏蔽的"念成"你不想看的"；常显一句"没有内容被屏蔽"则是把不存在的事说一遍。
     */
    @Test
    fun 收起与屏蔽两句分开念而都没挡时一句不念() {
        assertEquals(emptyList<String>(), DesktopWall().guardFragments())
        assertEquals(
            listOf("2 张按「成人内容处理」收起"),
            DesktopWall(hiddenByRating = 2).guardFragments(),
        )
        assertEquals(
            listOf("3 张命中屏蔽规则 landscape、author:foo"),
            // 笔数是 3 而规则只有两颗：一条规则挡下三张时，念出来的必须是张数而不是规则数。
            DesktopWall(blockedCount = 3, blockedRules = listOf("landscape", "author:foo")).guardFragments(),
        )
        assertEquals(
            "两句都在时各留各的，顺序是先分级后屏蔽",
            listOf("1 张按「成人内容处理」收起", "1 张命中屏蔽规则 landscape"),
            DesktopWall(blockedCount = 1, blockedRules = listOf("landscape"), hiddenByRating = 1).guardFragments(),
        )
        // 两句的**字面**逐字回核 Android 侧（`gallery/ui/GalleryFeedReadout.kt:36,:40`）：
        // 桌面这两句是文本重述，对岸改了措辞而这里没跟，用户就会看到两端不同的说法。
        val android = DesktopSourceTree.codeText(
            File(DesktopSourceTree.repoRoot, "app/src/main/java/com/venera/compose/gallery/ui/GalleryFeedReadout.kt"),
        )
        assertTrue(
            "Android 侧「N 张按「成人内容处理」收起」那句的措辞变了 —— 桌面那句要跟着改，别各念一套",
            android.contains("张按「成人内容处理」收起"),
        )
        assertTrue(
            "Android 侧「N 张命中屏蔽规则 …」那句的措辞变了（或改成了不带张数）—— 桌面那句跟着对表",
            android.contains("张命中屏蔽规则"),
        )
        // 对照组：抽取失效时上面两条会恒真，所以钉一条 Android 里**不存在**的措辞必须找不到。
        assertTrue("对照组失效：Android 那颗文件读的是空串", android.contains("ratingHiddenFragment"))
    }

    /**
     * ⑤ 判据不许在这里被**重写第二份**。
     *
     * 这一层只许"把共享那颗 `GalleryGuard.maskStateFor` 的三档落到桌面上"。
     * 自己写 `rating == "e"` 或 `mode == "HIDE"` 就是第二份口径，而漂的那一侧永远是桌面
     * （桌面没人拿它跟漫画侧对表）。
     */
    @Test
    fun 分级判定只许调共享那颗而不许自己写条件() {
        val code = DesktopSourceTree.codeText(DesktopSourceTree.desktopUiSource("DesktopGalleryWallPolicy.kt"))
        assertEquals("必须恰好调一次共享判据", 1, Regex(Regex.escape("GalleryGuard.maskStateFor")).findAll(code).count())
        listOf("isAdultMarked", "\"HIDE\"", "\"BLUR\"", "rating ==", "rating.contains")
            .forEach { banned ->
                assertTrue(
                    "这里出现了「$banned」—— 那是把共享判据抄成第二份，抄的那一份迟早漂",
                    !code.contains(banned),
                )
            }
    }

    /**
     * ⑥ `AI_BLOCKED_RULE` 是对 Android 侧同名常量的**文本重述**（那颗在桌面编译面外拿不到），
     * 重述就要回核。与 `DesktopGalleryPreferencesTest` 对键名做的那件事同一形状。
     */
    @Test
    fun AI规则原文与Android那枚常量逐字同串() {
        val android = File(
            DesktopSourceTree.repoRoot,
            "app/src/main/java/com/venera/compose/gallery/ui/GalleryScreen.kt",
        )
        assertTrue("Android 侧那颗文件找不到时这条判据就是空转", android.isFile)
        val line = android.readLines().firstOrNull { it.trim().startsWith("const val AI_BLOCKED_RULE") }
        assertTrue("Android 侧的 AI_BLOCKED_RULE 被删或改名了 —— 桌面这边要跟着改，不能自己留一份", line != null)
        val value = Regex("""=\s*"(.*)"""").find(line!!)?.groupValues?.get(1)
        assertEquals("两头必须是同一串（读数里摆的就是它）", value, DesktopGalleryWallPolicy.AI_BLOCKED_RULE)
    }

    /**
     * ⑦ **张数守恒**：取回的那些张，每一张都必须正好落在三个格子之一（墙上 / 命中屏蔽 / 分级收起）。
     *
     * 这一条是把"数对不上"那类缺陷**结构性挡掉**的回归网：`of()` 里那个 `when` 少一个分支、
     * 或者某一张既进不了卡也不算进任何一笔缺席，屏上就会出现"取回 20 张而屏上只说得出 16 张"——
     * 那正是本批在单人页那一格量到的形状（见 `DesktopWallGuardCoverageTest` 类注释）。
     *
     * ⚠️ 三档 `maskMode` × `blockAi` 两种**都要喂**（手册 §0：值由调用点给的参数至少喂两种），
     * 且语料要同时含"成人档"、"黑名单命中"、"站方 AI 标"、"遮挡与不遮挡"四种形状 —— 只喂一种的话
     * `blockAi` 那一半分支根本不会被走到（批 35 的 p1 就是这么绿过去的）。
     */
    @Test
    fun 取回的张数守恒在墙上加被挡加收起() {
        guard.blockedByTag = "blacklisted"
        val corpus = listOf(
            post(1, rating = "s"),
            post(2, rating = "e"),
            post(3, rating = "q"),
            post(4, rating = "e", tags = "blacklisted"),
            post(5, rating = "s", tags = "blacklisted female"),
            post(6, rating = "s", tags = "1girl ai-generated"),
        )
        listOf("OFF", "BLUR", "HIDE").forEach { maskMode ->
            listOf(false, true).forEach { blockAi ->
                val wall = DesktopGalleryWallPolicy.of(corpus, maskMode, blockAi, guard)
                assertEquals(
                    "$maskMode 档 / 屏蔽AI=$blockAi：取回 ${corpus.size} 张必须等于" +
                        " 墙上 ${wall.cards.size} + 命中屏蔽 ${wall.blockedCount} + 分级收起 ${wall.hiddenByRating}",
                    corpus.size, wall.cards.size + wall.blockedCount + wall.hiddenByRating,
                )
            }
        }
        // 这条语料要真的把三个格子都踩到，否则上面那条等式两边可以全是同一个数（样例绕过了判式）。
        val hidden = DesktopGalleryWallPolicy.of(corpus, "HIDE", true, guard)
        assertTrue(
            "对照组失效：这份语料在 HIDE 档没能同时造出被挡与收起两笔（${hidden.blockedCount}/${hidden.hiddenByRating}）",
            hidden.blockedCount > 0 && hidden.hiddenByRating > 0 && hidden.cards.isNotEmpty(),
        )
    }

    /**
     * ⑧′ 那两笔缺席合成一行时的**连接符逐字照对岸**（`GalleryScreen.kt:1817` 的 `append("；…")`）。
     *
     * 旧形状是四处各拼一遍，于是同一件事在两头念成" · "与"；"两种，而字号与颜色也各两档 ——
     * 本批收进 [desktopWallGuardReading] 那一颗件，连接符只剩一处出处。
     */
    @Test
    fun 那两笔的连接符只有一处出处而它对得上对岸() {
        val reading = desktopWallGuardReading(
            DesktopWall(blockedCount = 2, blockedRules = listOf("blacklisted", "author:foo"), hiddenByRating = 3),
        )
        assertEquals("两笔缺席该由一枚连接符拼成一行：" + reading, 1, reading.split(DESKTOP_GUARD_FRAGMENT_JOINER).size - 1)
        assertTrue("连接符不是对岸那枚：$reading", reading.contains(DESKTOP_GUARD_FRAGMENT_JOINER))
        val android = DesktopSourceTree.androidSource("gallery/ui/GalleryScreen.kt").readText()
        assertTrue("对照组失效：对岸那两笔的连接符形状变了（这条回核没有底本了）", android.contains("""append("；另有"""))
    }

    /**
     * ⑧ hero 那一大位与墙**同一把判据**，而且遮挡档根本不发起取图。
     *
     * hero 原来直接拿 `daily.merged.posts.firstOrNull()` 并把 `backdropUrl`（中档大图）铺上去 ——
     * 也就是"该挡的内容摆在最大最清楚的那一格"。这一颗函数把"要不要取"提成可测的形状，
     * 而不是让 `masked` 埋在 composable 的 `if` 里。
     */
    @Test
    fun hero在遮挡档根本不去取那张大图() {
        val adult = GalleryPost(
            site = GallerySite.YANDERE,
            id = 1,
            rating = "e",
            largeUrl = "https://static1.example/large/1.jpg",
        )
        assertEquals("遮住的这一张不许给出去（给了就会发起一次取图）", null, heroBackdropOf(DesktopWallCard(adult, masked = true)))
        assertEquals(
            "没遮的那张要的是 backdropUrl 那一档，不是网格缩略档",
            "https://static1.example/large/1.jpg",
            heroBackdropOf(DesktopWallCard(adult, masked = false)),
        )
        assertEquals("墙上什么都没有时也是 null", null, heroBackdropOf(null))
    }

    /**
     * ⑨ hero 不许再自己去挑第一张。
     *
     * 这一条要扫整个文件而不是只看大位：hero 里**两处**从原始池取数，大位那处修过之后右列
     * 「屏上的头三条」还在读 `daily.merged.posts` —— 于是墙上念着「3 张被挡下」、右列却照旧报那三条的
     * 站名与分数。只报名字确实没把内容摆出去，但同一屏上互相打脸的话也不该留，所以判据按整文件收。
     */
    @Test
    fun hero也走这一层而不是自己挑第一张() {
        val code = DesktopSourceTree.codeText(DesktopSourceTree.desktopUiSource("DesktopDailyHero.kt"))
        assertTrue("hero 必须经 heroBackdropOf 决定取不取图", code.contains("heroBackdropOf("))
        assertTrue(
            "hero 还在直接读 merged.posts —— 那是绕过分级那一关挑内容，" +
                "墙上第一张被挡下时大位会把同一张原样摆出来、右列会照旧报名",
            !code.contains("merged.posts"),
        )
    }

    /** 只装这一批要用的那一问；其余成员在这一条路径上不该被碰到，碰到了就抛。 */
    private class FakeGuard : GalleryContentGuard {
        var blockedByTag: String? = null

        override val nsfwMaskMode: StateFlow<String> = MutableStateFlow("OFF")
        override val rules: StateFlow<List<GuardRule>> = MutableStateFlow(emptyList())

        override fun blockedGalleryRule(author: String, tags: List<String>): GuardRule? =
            blockedByTag?.let { pattern ->
                if (pattern in tags) {
                    GuardRule(id = 1L, type = "TAG", pattern = pattern, isRegex = false, isEnabled = true)
                } else {
                    null
                }
            }

        override suspend fun addRule(type: String, pattern: String, isRegex: Boolean): Long =
            throw UnsupportedOperationException("这一条路径不该写规则")
    }
}
