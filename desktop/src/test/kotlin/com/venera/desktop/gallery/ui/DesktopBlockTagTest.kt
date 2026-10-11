package com.venera.desktop.gallery.ui

import com.venera.compose.gallery.data.GalleryContentGuard
import com.venera.compose.security.guard.GuardRule
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 桌面第十四批 批 10 · 浮层那排标签的**长按 = 屏蔽这一枚**。
 *
 * ## 为什么是长按
 *
 * 对岸那一路就是长按菜单里的「屏蔽该标签」（`GalleryInfoSheet.kt` 的 `blockTag`），
 * 桌面没有那套长按弹层，所以把这枚动作直接挂在那颗 chip 的长按上，
 * 并把这件事**写在页脚那句里** —— 一个既看不见也猜不到的动作等于没做。
 * 点一下那枚动作（去搜这一枚，批 2 ⑤）**原样保留**：判据 ⑤ 钉的就是"加了长按别把点击改掉"。
 *
 * ## 三条来自对岸实测的口径，一条都不许在桌面重定
 *
 * 1. **存 TAG 型而不是 KEYWORD**：画廊那面墙的屏蔽判据只喂 `post.tagList`，KEYWORD 会连作者名一起误伤。
 * 2. **存站点原值**，不存译文 —— 译文随字典更新会变，那时这条规则挡不住任何东西。
 * 3. **成功判据是 `>= 0`**：写不进去回报 -1，而 -1 也是一枚合法的 Long。
 *
 * ## 判据为什么分「真跑」与「扫描」
 *
 * ②③④ 是纯函数真跑（三档落点、大小写、同型同名那条边界样例）。⑤⑥⑦⑧⑨ 只能扫源码 ——
 * 它们钉的是"这一处接的是那颗件、那句说的是真话"，而那类事实在运行期看不出来。
 * 每条负向扫描都配一条"锚点真抓到了"的对照，并按批 8 那条规矩**扫整串调用而不是标识符**。
 */
class DesktopBlockTagTest {

    private val panelPath = "desktop/src/main/kotlin/com/venera/desktop/gallery/ui/DesktopGalleryDetailPanel.kt"
    private val panelText get() = File(DesktopSourceTree.repoRoot, panelPath).readText()
    private val panelCode get() = DesktopSourceTree.codeText(File(DesktopSourceTree.repoRoot, panelPath))
    private val policyCode get() = DesktopSourceTree.codeText(DesktopSourceTree.desktopUiSource("DesktopGuardPolicy.kt"))

    private fun rule(id: Long, type: String, pattern: String) =
        GuardRule(id, type, pattern, isRegex = false, isEnabled = true)

    /** ① 那一枚型与那三句落点，逐字回核 Android 的 blockTag。 */
    @Test
    fun 型与三句落点逐字回核对岸() {
        assertEquals("桌面自造了一枚型（对岸存的是 TAG）", "TAG", DESKTOP_GUARD_TAG_RULE_TYPE)
        val sheet = DesktopSourceTree.androidSource("gallery/ui/GalleryInfoSheet.kt").readText()
        listOf(
            "已在屏蔽列表",
            "屏蔽失败，请重试",
            "已屏蔽「\$tag」",
            "会连作者名一起误伤",
            "站点原值",
        ).forEach { line ->
            assertTrue("对岸那一句不在了（桌面这条判据的前提变了）：$line", sheet.contains(line))
        }
    }

    /** ② 「已经有这一条」怎么算：同型同名才算，而异型同名不算（批 6 那条边界样例的教训）。 */
    @Test
    fun 已有那一条怎么算() {
        assertFalse(
            "把关键词规则算成了标签规则 ⇒ 长按一枚标签只会念「已经在列表里」，而那一条其实挡不住标签",
            desktopTagAlreadyBlocked(listOf(rule(1L, "KEYWORD", "landscape")), "landscape"),
        )
        assertTrue("同型同名没算已有", desktopTagAlreadyBlocked(listOf(rule(2L, "TAG", "landscape")), "landscape"))
        assertTrue(
            "大小写不同要算同一条（两站原值写法不一，而匹配本身不区分大小写）",
            desktopTagAlreadyBlocked(listOf(rule(3L, "TAG", "Landscape")), "landscape"),
        )
        assertFalse("空表里不该判出已有", desktopTagAlreadyBlocked(emptyList<GuardRule>(), "landscape"))
        // ⚠️ 停用着的那一条**也算**已经在列表里 —— 与对岸同一档（那边 `any { type == "TAG" && equalsIgnoreCase }`
        // 没有 isEnabled 那一半）。这里不许"顺手改得更聪明"：屏上那句说的是"在列表里"而不是"正在挡"，
        // 而按启用与否分档会在设置那一屏的表里长出第二枚同名项（用户想启用那一枚，结果拿到的是重复项）。
        assertTrue(
            "停用着的那一条被当成了「可以再加一枚」（会在表里长出第二枚同名项）",
            desktopTagAlreadyBlocked(
                listOf(GuardRule(4L, "TAG", "landscape", isRegex = false, isEnabled = false)),
                "landscape",
            ),
        )
    }

    /** ③ 三档落点怎么定：先看有没有已有，再看 id；而 -1 不是成功。 */
    @Test
    fun 成功判据是那一个大于等于零() {
        assertEquals("0 号规则是一枚合法 id", DesktopBlockTagOutcome.Added, desktopBlockTagOutcome(false, 0L))
        assertEquals("正 id 是加上了", DesktopBlockTagOutcome.Added, desktopBlockTagOutcome(false, 7L))
        assertEquals("-1 是没写成，不许报「已屏蔽」", DesktopBlockTagOutcome.Failed, desktopBlockTagOutcome(false, -1L))
        assertEquals("写口没返回 id（异常路径）也是没写成", DesktopBlockTagOutcome.Failed, desktopBlockTagOutcome(false, null))
        assertEquals(
            "已经有这一条时不许再看 id（那一步根本没发车）",
            DesktopBlockTagOutcome.Already,
            desktopBlockTagOutcome(true, null),
        )
    }

    /** ④ 三句落点各说一句、互不相同，而「改回来在哪」那一路径名与那一区的标题是同一颗常量。 */
    @Test
    fun 三句落点各说一句() {
        val already = desktopBlockTagReading("landscape", DesktopBlockTagOutcome.Already)
        val added = desktopBlockTagReading("landscape", DesktopBlockTagOutcome.Added)
        val failed = desktopBlockTagReading("landscape", DesktopBlockTagOutcome.Failed)
        listOf(already, added, failed).forEach { assertTrue("有一句是空的", it.isNotBlank()) }
        assertEquals(
            "三句合成了一句（把「没做成」与「本来就是」念成同一句，是最忌的那种读数）",
            3, listOf(already, added, failed).distinct().size,
        )
        assertTrue("「已经在列表里」那句得点名是哪一枚：" + already, already.contains("landscape"))
        assertTrue("「加上了」那句也得点名：" + added, added.contains("landscape"))
        assertTrue("那句要指出改回来的地方，且路径名引用那颗常量：" + added, added.contains(DESKTOP_GUARD_SECTION_TITLE))
        listOf("已保存", "操作成功", "完成").forEach { word ->
            assertFalse("界面上出现了说不出是哪一档的话（$word）：$added / $failed", added.contains(word) || failed.contains(word))
        }
    }

    /** ⑤ 加了长按，而点击那一枚没被顺手改掉（批 2 ⑤ 那枚动作仍在）。 */
    @Test
    fun 长按屏蔽而点击仍是去搜() {
        val from = panelCode.indexOf("combinedClickable(")
        val to = panelCode.indexOf(".padding(horizontal = DesktopGalleryMetrics.chipHorizontalPadding)")
        assertTrue("锚点没抓到（那枚 chip 的形状变了）from=$from to=$to", from in 0 until to)
        val block = panelCode.substring(from, to)
        listOf(
            "onLongClickLabel = ",
            "onLongClick = {",
            "onClick = { onSearch(listOf(post.site), tag) }",
            "desktopBlockTag(ports.contentGuard, guardRules, tag)",
        ).forEach {
            assertTrue("那枚 chip 少了这一步（$it）", block.contains(it))
        }
        // 对照：长按那一步是在协程里发的（写口是 suspend），而不是在组合期里跑。
        assertTrue("长按那一格没有 scope.launch", block.contains("scope.launch"))
    }

    /** ⑥ 存进去的是站点原值，而译文那一路在别的格子上（不是「没接译文所以只好存原值」）。 */
    @Test
    fun 存的是站点原值而不是译文() {
        assertTrue(
            "长按那一步传的不是 tagList 里那一枚原串",
            panelCode.contains("desktopBlockTag(ports.contentGuard, guardRules, tag)"),
        )
        // 那颗件把传进来的串**原样**交下去（不 trim、不小写、不查词典）。
        assertTrue("写口传的不是原值：" + policyCode, policyCode.contains("guard.addRule(DESKTOP_GUARD_TAG_RULE_TYPE, tag)"))
        // 对照：面板确实有词典那一路（画师那一栏），所以这一条不是"接不到译文"。
        assertTrue("对照组失效：面板上没有词典那一路了", panelCode.contains("desktopDetailArtists("))
    }

    /** ⑥′ 那颗件交下去的是**站点原值**：真跑一颗假守卫，看写口收到的（型, 串）到底是什么。 */
    @Test
    fun 写口收到的是原值() = kotlinx.coroutines.runBlocking {
        val seen = mutableListOf<Pair<String, String>>()
        val guard = object : GalleryContentGuard {
            override val nsfwMaskMode = MutableStateFlow("NONE")
            override val rules = MutableStateFlow(emptyList<GuardRule>())
            override fun blockedGalleryRule(author: String, tags: List<String>): GuardRule? = null
            override suspend fun addRule(type: String, pattern: String, isRegex: Boolean): Long {
                seen += type to pattern
                return 11L
            }
        }
        val reading = desktopBlockTag(guard, emptyList(), "Landscape Painting")
        assertEquals("发出去的型不是 TAG：" + seen, listOf("TAG"), seen.map { it.first })
        assertEquals(
            "那串被改过了 —— 小写或 trim 都会让规则挡不住站方那一枚（译文更不行：它会随字典更新变）",
            listOf("Landscape Painting"), seen.map { it.second },
        )
        assertTrue("落点那句没念成「加上了」：" + reading, reading.contains("已屏蔽「Landscape Painting」"))
        // 已经有那一枚时**根本不发那一笔**（写口一次都不该被调到）。
        seen.clear()
        desktopBlockTag(guard, listOf(rule(9L, "TAG", "landscape painting")), "Landscape Painting")
        assertEquals("已经在列表里却还发那一笔（会长出第二枚同名项）", emptyList<Pair<String, String>>(), seen)
    }

    /** ⑦ 写口只有共享契约那一枚：浮层不自开 store、不自拼 SQL，也不许再多一枚写口。 */
    @Test
    fun 写口只有契约上那一枚() {
        listOf(".insert(", "GuardRuleStore", "ruleStore", "PreferenceKeys").forEach { banned ->
            assertFalse("浮层自己碰了屏蔽库（$banned）—— 匹配口径与写口就分叉了", panelCode.contains(banned))
        }
        assertEquals(
            "浮层直接调了 addRule（写这一条要走那颗件，判档与读数才只有一处口径）",
            0, panelCode.split("addRule(").size - 1,
        )
        // 对照：契约上那枚写口早就有（这一批没动共享面），而桌面那颗件真在用它。
        val ports = DesktopSourceTree.codeText(DesktopSourceTree.androidSource("gallery/data/GalleryPorts.kt"))
        assertTrue("对照组失效：契约上那枚写口不在了", ports.contains("suspend fun addRule(type: String, pattern: String"))
        assertTrue("桌面那颗件没转交契约写口", policyCode.contains("guard.addRule("))
    }

    /** ⑧ 页脚那句与动作同步：说了「按住」就真有那颗 onLongClick，而路径名不许两处各写一份。 */
    @Test
    fun 页脚那句说的就是屏上能做的() {
        assertTrue("页脚那句不再说按住能干什么（做了却不说 = 用户只能猜）", panelCode.contains("按住一枚标签 = 屏蔽这一枚"))
        assertTrue("那句里的路径名没引用那颗常量（两处各写一份迟早漂）", panelCode.contains("DESKTOP_GUARD_SECTION_TITLE"))
        val literal = "\"敏感内容与屏蔽规则\""
        val copies = DesktopSourceTree.desktopUiSources().filter { DesktopSourceTree.codeText(it).contains(literal) }
        assertEquals(
            "「敏感内容与屏蔽规则」那枚字面量不止一处：" + copies.map { it.name },
            1, copies.size,
        )
        // 对照：动作真在（哪天长按撤了，上面那句要跟着撤，而这条对照会先红）。
        assertTrue("对照组失效：那颗 onLongClick 没了", panelCode.contains("onLongClick = {"))
    }

    /** ⑨ 加完之后墙上真会重算：面板收的是同一颗 rules 流，而三台墙都过那颗策略件。 */
    @Test
    fun 那一关读的是同一颗流() {
        assertTrue("面板没在收守卫那份流（加了规则屏上不认）", panelCode.contains("ports.contentGuard.rules.collectAsState()"))
        val wallFiles = DesktopSourceTree.desktopUiSources().filter {
            DesktopSourceTree.codeText(it).contains("DesktopGalleryWallPolicy.of(")
        }
        assertTrue(
            "过那一关的墙不止三面了？还是少了一面：" + wallFiles.map { it.name },
            wallFiles.size >= 3,
        )
        // 对照：策略件收的是那颗守卫（不是自己数一遍标签）。
        val wall = DesktopSourceTree.codeText(DesktopSourceTree.desktopUiSource("DesktopGalleryWallPolicy.kt"))
        assertTrue("墙那颗策略件不吃守卫了（屏蔽加上去墙上不认）", wall.contains("guard: GalleryContentGuard"))
    }
}
