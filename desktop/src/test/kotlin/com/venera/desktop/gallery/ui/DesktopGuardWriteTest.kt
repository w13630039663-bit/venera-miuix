package com.venera.desktop.gallery.ui

import com.venera.compose.security.guard.GuardRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 桌面第十二批 批 6 · 分级档与屏蔽规则的**写侧**（原排期 批 4）。
 *
 * ## 先记一条改判：这一批原本标着"要动共享契约、等点头"，那是错的
 *
 * 排期（`docs/rounds/desktop-basic-gaps-plan-2026-10-05.md` §2 批 4）说 ⑦ 要给 `GalleryContentGuard`
 * 加 setter、⑧ 说"删一条"共享面根本没有 ⇒ 两条都要点头。动手前重量了一遍，两道写口都不必动契约：
 * - 分级档落在 `venera_guard_prefs.xml` 的 `nsfw_mode`，而桌面那颗具体件 `DesktopGalleryContentGuard`
 *   已经在读这一枚（`KEY_NSFW_MODE`），写它只是补一道同类 setter；
 * - 删除落在 `GuardRuleStore.delete(id)`（`data/db`，**已在桌面编译面上**）。
 *
 * 走的是第七批那条现成形状：界面吃**桌面那颗具体件**当形参（与 `settings: DesktopGalleryPreferences` 同形），
 * 转换点在集成方、拿不对就抛 ⇒ `app/` 一字不动、没开冻结豁免、契约一个字没改。判据 ⑧ 反向钉着这件事。
 *
 * ## 这一批仍然守着的三条
 *
 * 1. **词与 Android 逐字同一份**：三档「不过滤 / 封面打码 / 彻底隐藏」与四型「关键词 / 标签 / 画师 / 作品」
 *    都由判据从 `BlockingSettings.kt` 那两行**抽字面量**回来比，不靠我抄一遍（抄的那份会过期）。
 * 2. **正则在写库前校验**：Android 原话是"坏模式会静默永不命中，比报错更难查"，
 *    判据是共享那颗 `GuardRulePattern.compiles`，桌面不自写正则。
 * 3. **删除不摆确认层，但行上明写"点了就删"**：收藏那一屏的"不可撤销"规矩不适用于这一枚 ——
 *    删一条屏蔽规则当场可以再敲一遍加回来，它不是用户攒的数据。
 */
class DesktopGuardWriteTest {

    private fun rule(id: Long, type: String, pattern: String, regex: Boolean = false) = GuardRule(
        id = id,
        type = type,
        pattern = pattern,
        isRegex = regex,
        isEnabled = true,
    )

    private val quoted = Regex(""""[^"]*"""")

    /** 从对岸那一行抽出「值 → 词」那几对（判式失效时下面各条会先红在对照组上，不会假绿）。 */
    private fun androidPairs(predicate: (String) -> Boolean, expected: Int): List<Pair<String, String>> {
        val line = DesktopSourceTree.codeLinesOf(DesktopSourceTree.androidSource("feature/settings/BlockingSettings.kt"))
            .firstOrNull(predicate)
        checkNotNull(line) { "对岸 BlockingSettings.kt 里找不到那一行 —— 判式先失效，别信后面的断言" }
        val pairs = quoted.findAll(line).map { it.value.trim('"') }.toList().chunked(2).map { (a, b) -> a to b }
        assertEquals("对岸那一行应当恰好 $expected 对，读到 ${pairs.size} 对：$line", expected, pairs.size)
        return pairs
    }

    /**
     * ① 三档那三个词与 Android **逐字**同一份。
     *
     * 值那一列是落盘字符串（`nsfw_mode` 的取值），Android 与桌面读的是同一个键 —— 值分叉就是互读不懂。
     */
    @Test
    fun 三档词逐字回核Android() {
        val pairs = androidPairs({ it.contains("""listOf("OFF" to""") }, 3)
        assertEquals("三档的值或词与 Android 分叉了（同一枚键两份措辞）", pairs, desktopMaskModeLabels())
    }

    /** ② 第四枚值不许静默收下：`desktopRequireMaskMode` 只认那三枚，其余**抛**。 */
    @Test
    fun 分级档只收那三枚值() {
        desktopMaskModeLabels().forEach { (mode, _) ->
            assertEquals("认下来的值原样交回", mode, desktopRequireMaskMode(mode))
        }
        listOf("READABLE", "blur", "", "OFF ", "OFF;DROP").forEach { bad ->
            val thrown = runCatching { desktopRequireMaskMode(bad) }.isFailure
            assertTrue("「$bad」被收下了 —— 写进档里就是 Android 侧读不懂的一枚值", thrown)
        }
    }

    /** ③ 四型标题同样逐字回核 Android（那一行是 `listOf("KEYWORD" to "关键词", …)`）。 */
    @Test
    fun 四型标题逐字回核Android() {
        val pairs = androidPairs({ it.contains("""listOf("KEYWORD" to""") }, 4)
        assertEquals("桌面的四型与 Android 分叉", pairs, desktopRuleTypes())

        // 每一型那句"怎么匹配"也逐字回核：这一句决定用户往里填什么（填作品编号还是填标题片段），
        // 两端各写一份措辞，就是"在手机上懂、换到电脑上又开始猜"。
        val android = DesktopSourceTree.codeText(DesktopSourceTree.androidSource("feature/settings/BlockingSettings.kt"))
        desktopRuleTypes().forEach { (type, 名) ->
            val hint = desktopRuleTypeHint(type)
            assertTrue("「$名」那句匹配说明在 Android 里找不到同一句：$hint", android.contains("\"$hint\""))
            assertTrue("「$名」那句没在说怎么匹配：$hint", hint.contains("匹配"))
        }
        assertEquals("认不出的那一型不许编一句匹配说明", "", desktopRuleTypeHint("WEIRD"))
    }

    /** ④ 加一条的四档判据：空 / 坏正则 / 重复 / 收。四档各说一句，且**不写库**就能判出来。 */
    @Test
    fun 加一条的四档判据() {
        assertEquals("空白串不发（发了就是往库里塞一颗空规则）", DesktopAddRule.Blank, desktopAddRule("   ", false, "TAG", emptyList()))
        assertTrue(
            "坏正则必须写库前拦：它静默永不命中，比报错更难查（Android 同一句）",
            desktopAddRule("(", true, "TAG", emptyList()) is DesktopAddRule.BadPattern,
        )
        val existing = listOf(rule(1, "TAG", "ai-generated"))
        assertTrue("同型同串（忽略大小写）算重复，不许塞第二条", desktopAddRule("AI-GENERATED", false, "TAG", existing) is DesktopAddRule.Duplicate)
        // 跨型那一档要拿**同一串**过：从前这里的样例是 `ai` 对 `ai-generated`（本来就不相等），
        // 于是"把查重的型那一半判式删掉"这条扰动跑出来是绿的 —— 样例自己把判据绕过去了。
        val 同名两型 = listOf(rule(2, "TAG", "ai"))
        assertTrue(
            "别的型里同名一枚不算重复（标签 ai 与画师 ai 是两条规则）⇒ 这一笔必须发",
            desktopAddRule("ai", false, "AUTHOR", 同名两型) is DesktopAddRule.Accept,
        )
        assertTrue(
            "同型同名才算重复：这一笔与上面那一笔只差型，判落点必须不同",
            desktopAddRule("ai", false, "TAG", 同名两型) is DesktopAddRule.Duplicate,
        )
        val accepted = desktopAddRule("  long_hair  ", false, "TAG", existing)
        assertEquals("站点原值进库前先 trim（Android 同一句 input.trim()）", DesktopAddRule.Accept("long_hair"), accepted)
        assertEquals("关掉正则开关时坏模式不算坏（它只是普通文字）", DesktopAddRule.Accept("("), desktopAddRule("(", false, "TAG", emptyList()))
    }

    /** ⑤ 规则表按型分组是**现算**的，而认不出的那一型要点名而不是丢掉。 */
    @Test
    fun 分组现算而未知型点名() {
        val rules = listOf(rule(1, "TAG", "a"), rule(2, "AUTHOR", "b"), rule(3, "KEYWORD", "c"), rule(4, "WEIRD", "d"))
        val table = desktopRulesByType(rules)
        assertEquals("四型各一行（次序跟 Android 那行）", listOf("KEYWORD", "TAG", "AUTHOR", "COMIC_ID"), desktopRuleTypes().map { it.first })
        assertEquals(1, table.of("TAG").size)
        assertEquals("空那一型给空表而不是 null", emptyList<GuardRule>(), table.of("COMIC_ID"))
        assertEquals("未知那一型必须被点名（静默丢掉就是「屏上少一条而没人知道」）", listOf("WEIRD"), table.unlisted.map { it.type })
    }

    /** ⑥ 两道写口在桌面那颗具体件上**真存在**（反射数签名，不靠扫文本）。 */
    @Test
    fun 具体件上真有两道写口() {
        val cls = Class.forName("com.venera.desktop.gallery.data.DesktopGalleryContentGuard")
        val names = cls.methods.map { it.name }
        assertTrue("没有 setNsfwMaskMode（分级档还是只能读）：$names", names.contains("setNsfwMaskMode"))
        assertTrue("没有 deleteRule（规则表只能进不能出）：$names", names.contains("deleteRule"))
        // 对照：读面两枚一直在，证明这颗类没读空。
        assertTrue("对照组失效：读面没数到", names.contains("blockedGalleryRule") && names.contains("addRule"))
        // 名字也是回核对岸的，不是桌面挑一枚"看着更像"的动词：两端各叫一名，
        // 读代码的人就要各记一份"哪一侧重叫什么"，而这两枚做的是同一件事。
        val android = DesktopSourceTree.codeText(DesktopSourceTree.androidSource("feature/settings/BlockingSettings.kt"))
        assertTrue(
            "对岸那道写口已不叫 setNsfwMaskMode —— 桌面这枚名字要跟着它改，别各叫各的",
            android.contains("guard.setNsfwMaskMode("),
        )
    }

    /**
     * ⑥′ 那枚键落在**哪一份键值件**上也要与对岸同轨 —— 这一条是本批动手时顺手量到的一处旧错。
     *
     * 桌面从前把 `nsfw_mode` 读在 `PreferenceKeys.PREFS_NAME`（= `venera_preferences`）上，
     * 而对岸那颗守卫建的是 `AndroidKeyValueStore(context, "venera_guard_prefs")`
     * （`ContentGuardManager.kt:82-84`）。**键名同、文件不同轨**：读侧一直拿默认档 `OFF`，
     * 而这件事在只有读面的时候量不出来（桌面上那份文件里本来就没写过这一枚），
     * 补上写口才让它从"看不出"变成"看得出错" —— 写进一份没人读的文件，正是"存下来了而不生效"的形状。
     */
    @Test
    fun 分级档那份键值件与对岸同轨() {
        val guard = DesktopSourceTree.codeText(
            java.io.File(
                DesktopSourceTree.repoRoot,
                "desktop/src/main/kotlin/com/venera/desktop/gallery/data/DesktopGalleryContentGuard.kt",
            ),
        )
        assertTrue(
            "桌面把 nsfw_mode 读在建了 venera_preferences 的那份键值件上 —— 对岸那一枚在 venera_guard_prefs",
            guard.contains("JsonKeyValueStore(PreferenceKeys.PREFS_GUARD"),
        )
        // 对照组：对岸的 store 名与常量值逐字同一份（读空会让上面那条 contains 恒真）。
        val manager = DesktopSourceTree.codeText(DesktopSourceTree.androidSource("security/guard/ContentGuardManager.kt"))
        assertTrue("对照组失效：对岸守卫件读不到那句建 store", manager.contains("""AndroidKeyValueStore(context, "venera_guard_prefs")"""))
        val keys = DesktopSourceTree.codeText(DesktopSourceTree.androidSource("data/platform/PreferenceKeys.kt"))
        assertTrue("PREFS_GUARD 的常量值已不等于对岸那一句（桌面这枚引用会跟着漂）", keys.contains("""const val PREFS_GUARD = "venera_guard_prefs""""))
    }

    /** ⑦ 四跳接线：具体件由集成方取、根上收下、设置页真调着三处写口。 */
    @Test
    fun 写侧四跳真通() {
        val ports = DesktopSourceTree.codeText(
            java.io.File(DesktopSourceTree.repoRoot, "desktop/src/main/kotlin/com/venera/desktop/gallery/data/DesktopGalleryPorts.kt"),
        )
        assertTrue("DesktopGalleryPorts 没给那颗具体件的取法（界面不许自己 as 静态口）", ports.contains("fun contentGuard("))
        assertTrue("取法没在读不对时抛（退成「读得到但改不动」就是假开关）", ports.contains("?: error("))

        val shell = DesktopSourceTree.codeText(
            java.io.File(DesktopSourceTree.repoRoot, "desktop/src/main/kotlin/com/venera/desktop/VeneraDesktop.kt"),
        )
        assertTrue("main() 没把那颗具体件取出来传给应用根", shell.contains("DesktopGalleryPorts.contentGuard("))
        assertTrue("应用根没收这颗参数", shell.contains("guard = guard"))

        val app = DesktopSourceTree.codeText(DesktopSourceTree.desktopUiSource("VeneraDesktopApp.kt"))
        assertTrue("应用根没有 guard: DesktopGalleryContentGuard 这颗必填参数", app.contains("guard: DesktopGalleryContentGuard"))

        val pane = DesktopSourceTree.codeText(DesktopSourceTree.desktopUiSource("DesktopGallerySettingsPane.kt"))
        listOf("setNsfwMaskMode(", "addRule(", "deleteRule(").forEach { call ->
            assertTrue("设置页没真调 $call（账上写着能改而屏上没接线）", pane.contains(call))
        }
    }

    /**
     * ⑧ 这一批**没有**动共享契约，也没在桌面另起一套判据。
     *
     * 反向钉两件事：`GalleryContentGuard` 那圈成员一个都没多（改判的根据就在这）；
     * 而桌面既不自己拼 SQL 删除、也不自己 `Regex(` 校验正则。
     */
    @Test
    fun 契约没动而桌面不另起一套() {
        val face = DesktopSourceTree.codeText(DesktopSourceTree.androidSource("gallery/data/GalleryPorts.kt"))
        val body = face.substring(face.indexOf("interface GalleryContentGuard"))
            .let { it.substring(0, it.indexOf("\n}")) }
        assertTrue("对照组失效：读不到那颗接口的成员表", body.contains("nsfwMaskMode") && body.contains("addRule"))
        listOf("fun setNsfwMaskMode", "fun deleteRule", "fun removeRule").forEach { banned ->
            assertFalse("共享契约被加了成员「$banned」（这一批的前提就是不动它）：$body", body.contains(banned))
        }

        val guard = DesktopSourceTree.codeText(
            java.io.File(DesktopSourceTree.repoRoot, "desktop/src/main/kotlin/com/venera/desktop/gallery/data/DesktopGalleryContentGuard.kt"),
        )
        listOf("DELETE FROM", "executeUpdate", "PreparedStatement").forEach { banned ->
            assertFalse("桌面守卫件里出现了自己落库的形状「$banned」（删除必须转交 GuardRuleStore.delete）", guard.contains(banned))
        }
        assertTrue("删除没转交共享那颗 store", guard.contains("ruleStore.delete("))

        val policy = DesktopSourceTree.codeText(DesktopSourceTree.desktopUiSource("DesktopGuardPolicy.kt"))
        val pane = DesktopSourceTree.codeText(DesktopSourceTree.desktopUiSource("DesktopGallerySettingsPane.kt"))
        assertFalse("判据件或界面自己 new Regex（校验必须走共享那颗 GuardRulePattern）", policy.contains("Regex(") || pane.contains("Regex("))
        assertTrue("判据没走共享那颗正则件", policy.contains("GuardRulePattern.compiles("))
    }

    /** ⑨ 落点分档各说一句：正在写 / 写成了 / 写砸了 / 重复 / 坏模式，不许合成一句"已保存"。 */
    @Test
    fun 写侧读数分档() {
        val pane = DesktopSourceTree.codeText(DesktopSourceTree.desktopUiSource("DesktopGallerySettingsPane.kt"))
        listOf("正在保存", "保存失败", "这条已经加过了", "正则写法有误", "已删掉").forEach { 句 ->
            assertTrue("设置页少了那一句「$句」", pane.contains(句))
        }
        assertFalse("把几种落点压成了一句「已保存」（那正好是屏上最没信息量的一句）", pane.contains("Text(\"已保存\")"))

        // 三句**与 Android 同一份**的读数逐字回核：那三句是同一件事在两端的两种说法的话，
        // 用户换设备就得重新学一次"这句到底指没指写进去了"。
        val android = DesktopSourceTree.codeText(DesktopSourceTree.androidSource("feature/settings/BlockingSettings.kt"))
        listOf("正在保存", "这条已经加过了", "正则写法有误").forEach { 句 ->
            assertTrue("「$句」在 Android 那侧没有同一句（桌面自己造了一枚新词）", android.contains(句))
        }
    }
}
