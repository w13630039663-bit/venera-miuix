package com.venera.compose.architecture

import com.venera.compose.data.api.BASELINE_GET_INSTANCE
import com.venera.compose.data.api.BASELINE_IMPORT
import com.venera.compose.data.api.BASELINE_TYPE_SITE
import com.venera.compose.testsupport.RepoSources
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 债务前置条件的**兑现探测器**（D4）：把「一旦 W1/W2 落地就立刻来摘名单」这句话变成用例。
 *
 * 为什么现有的两条守卫不够：`BusinessApiBoundaryTest` 钉的是「UI 现在还剩几处穿透」，
 * 白名单行只有在**有人去改那一行**时才会消失；而 W1/W2 那种「类型先搬家、消费点才能改引」的债，
 * 前置条件成立之后 UI 那行**照样挂着**，边界守卫一片绿 —— 这正是没人会想起去摘的那一类。
 * 这里加的是反方向的判据：**前置已成立而点位未清 ⇒ 红**，红的那句话直接写清该摘哪几行。
 *
 * 与 `LayeringEdgeTest` 的分工：那一条用「集合相等」天然会红（类型搬走 → 下层那几行 import 自动消失 →
 * 名单多出一条）；这一条管的是**边界白名单**那些不会自动消失的行。
 *
 * ⚠️ 判式一律按**符号声明**读源码，不按 git 状态、不按行号：`internal` 的三张基线表就是点位所在地的账本，
 * 拿它当判据不会漂。另外每条债都带 `probeTypes`，由第二条用例确认那些类型名**今天在仓库里真的存在** ——
 * 判式里写错一个符号名，探测器就永远不亮，那是比没有探测器更糟的形态（参 `docs/rounds/business-api-boundary-2026-10.md` §五 B1 那次假绿）。
 */
class DebtReadinessTest {

    private data class Debt(
        /** 债务编号，与方案 §六/§七 的行对得上。 */
        val id: String,
        /** 前置条件（给人看的那句话）。 */
        val unlock: String,
        /** 机器判据：前置是否已成立。 */
        val unlocked: () -> Boolean,
        /** 机器判据：待摘的点位是否还挂在边界白名单上。 */
        val stillThere: () -> Boolean,
        /** 探测器自己不许指着不存在的符号。 */
        val probeTypes: List<String>,
    )

    // ─────────────────────────── 判式底座 ───────────────────────────

    private fun mainText(): String = RepoSources.kotlinFiles("com/venera/compose").joinToString("\n") { it.readText() }

    /**
     * 目录不存在 ⇒ 返回 false（那就是「还没搬过来」），**不许让 [RepoSources.kotlinFiles] 抛出来**。
     * 抛出来的话探测器会以 IllegalStateException 的形态红，看着像守卫坏了而不是债没到期；
     * 而 W1 真要落地时是先建出 `source/model/`（或 `data/model/`）那颗目录，判式当场生效。
     */
    private fun declaredIn(dir: String, name: String): Boolean {
        val root = java.io.File(RepoSources.appMain, "com/venera/compose/$dir")
        if (!root.isDirectory) return false
        return RepoSources.kotlinFiles("com/venera/compose/$dir").any { DECLARATION(name).containsMatchIn(it.readText()) }
    }

    private fun declaredSomewhere(name: String): Boolean = DECLARATION(name).containsMatchIn(mainText())

    /** 允许缩进（嵌套类型）、允许 `data class` / `value class` / 注解前置。 */
    private fun DECLARATION(name: String) = Regex(
        """^\s*(?:@\w+(?:\([^)]*\))?\s*)*(?:(?:public|internal|private|sealed|abstract|open|data|value|nested)\s+)*(?:class|interface|object|enum\s+class)\s+$name\b""",
        RegexOption.MULTILINE,
    )

    private fun importer(file: String, symbol: String) = BASELINE_IMPORT[file]?.contains(symbol) == true
    private fun getter(file: String, symbol: String) = BASELINE_GET_INSTANCE[file]?.contains(symbol) == true
    private fun typeSite(file: String, symbol: String) = BASELINE_TYPE_SITE[file]?.contains(symbol) == true

    // ─────────────────────────── 债务清单 ───────────────────────────

    private val debts: List<Debt> = listOf(
        Debt(
            id = "W1-a（SourceSearchResult / InstalledSourceMeta）",
            unlock = "两枚嵌套在 ComicSourceManager 类体里的类型搬进 source/model/",
            unlocked = { declaredIn("source/model", "SourceSearchResult") || declaredIn("source/model", "InstalledSourceMeta") },
            stillThere = { typeSite("feature/SearchViewModel.kt", "ComicSourceManager") || getter("feature/settings/PreferredIpSpeedTestScreen.kt", "ComicSourceManager") },
            probeTypes = listOf("SourceSearchResult", "InstalledSourceMeta", "ComicSourceManager"),
        ),
        Debt(
            id = "W1-b（FavoriteImageItem）",
            unlock = "FavoriteImageItem 搬出 feature/favoriteimages/（方案 §七.6：契约的返回类型住 UI 层就是 W3 要禁的那条边）",
            unlocked = { declaredIn("source/model", "FavoriteImageItem") || declaredIn("data/model", "FavoriteImageItem") },
            stillThere = { typeSite("reader/VeneraReaderScreen.kt", "FavoriteImagesManager") || getter("feature/FavoriteImagesScreen.kt", "FavoriteImagesManager") },
            probeTypes = listOf("FavoriteImageItem", "FavoriteImagesManager"),
        ),
        Debt(
            id = "W1-c（ComicLinkResolver.Outcome）",
            unlock = "Outcome 那枚 sealed interface 搬进 source/model/（方案 §六：这是硬技术前提，缺它 Navigation 那行连契约都没有）",
            unlocked = { declaredIn("source/model", "Outcome") },
            stillThere = { getter("feature/Navigation.kt", "ComicLinkResolver") || typeSite("feature/Navigation.kt", "ComicLinkResolver") },
            probeTypes = listOf("Outcome", "ComicLinkResolver"),
        ),
        Debt(
            id = "W2（SelectOption / SourceAccountInfo / SourceSettingItem）",
            unlock = "三枚类型搬进 source/model/ ⇒ ComicSourceManager 的接口面不再 import feature.*，" +
                "ComicSourceViewModel 那 33 处与 JsComicSource 的三条分层违规同时可摘",
            unlocked = { declaredIn("source/model", "SelectOption") && declaredIn("source/model", "SourceSettingItem") },
            stillThere = { getter("feature/sourcemanage/ComicSourceViewModel.kt", "ComicSourceManager") },
            probeTypes = listOf("SelectOption", "SourceAccountInfo", "SourceSettingItem", "JsComicSource"),
        ),
        Debt(
            id = "W5（画廊偏好**写口**）",
            unlock = "gallery/data/ 的画廊契约出现写成员（今天 `GalleryPreferences` 12 枚 getter-only，" +
                "而唯一写者 feature/settings/GallerySettings.kt 要吃的是 15 枚 setGalleryXxx）⇒ " +
                "那两行才有的契约可引",
            // ⚠️ 判式不是「实现类存在」：读侧实现 AndroidGalleryPreferences 早已接在 GalleryPorts 上
            //   （B2 那批顺手落了，方案 §六/§八 却还写着 W5 未做 —— 文档那句已按实码纠正）。
            unlocked = { RepoSources.kotlinFiles("com/venera/compose/gallery/data").any { GALLERY_PREFS_WRITE.containsMatchIn(it.readText()) } },
            stillThere = { typeSite("feature/settings/GallerySettings.kt", "VeneraPreferences") || getter("feature/SettingsHost.kt", "VeneraPreferences") },
            probeTypes = listOf("GalleryPreferences", "AndroidGalleryPreferences", "VeneraPreferences"),
        ),
        Debt(
            id = "§七.7（TagTranslationManager / ChineseVariantConverter）",
            unlock = "data/api/ 出现这两颗的契约（今天不收的唯一理由是并行线的脏文件不许抢改，不是判据不足）",
            unlocked = { declaredIn("data/api", "TagTranslation") || declaredIn("data/api", "ChineseVariantConversion") },
            stillThere = { getter("feature/ComicDetailScreen.kt", "TagTranslationManager") || getter("feature/SearchViewModel.kt", "ChineseVariantConverter") },
            probeTypes = listOf("TagTranslationManager", "ChineseVariantConverter"),
        ),
    )

    private companion object {
        /**
         * 「画廊侧契约有写成员」的判式：`fun setGalleryXxx` / `fun toggleGalleryXxx` / `var galleryXxx:` 三种形状之一。
         * 今天三种都没有（`GalleryPreferences` 是 12 枚 `val`），所以这条探测器现在**不该亮**。
         */
        val GALLERY_PREFS_WRITE = Regex("""\b(?:fun\s+(?:set|toggle)Gallery[A-Z]\w*|var\s+gallery[A-Z]\w*\s*:)""")
    }

    // ─────────────────────────── 断言 ───────────────────────────

    /** 主断言：前置已兑现而点位仍挂着 ⇒ 红，并点名该摘哪几行。 */
    @Test
    fun `前置条件一旦兑现，对应白名单行就必须当场摘掉`() {
        val overdue = debts.filter { it.unlocked() && it.stillThere() }
        assertTrue(
            "这几条债的前置条件已经成立，但穿透点位还挂在白名单上（该摘了）：\n" +
                overdue.joinToString("\n") { "  · ${it.id}\n    前置：${it.unlock}" } +
                "\n\n做法：改引契约 → 复跑 _qa/scan.mjs 把基线灌回去 → 顺手把方案文档 §六/§七 对应行改成已落地。",
            overdue.isEmpty(),
        )
    }

    /** 探测器不许指着不存在的符号：判式写错名字时它会永不点亮，那比没有探测器更糟。 */
    @Test
    fun `每条债务的判式都指着仓库里真实存在的类型`() {
        val unknown = debts.flatMap { d -> d.probeTypes.filterNot { declaredSomewhere(it) }.map { d.id to it } }
        assertTrue("这些类型名在 app/src/main 里找不到声明，说明判式写漂了：$unknown", unknown.isEmpty())
    }

    /**
     * 每条待摘点位今天必须**真的还挂着**：如果哪天它自己消失了（或被别人摘了），这条 Debt 就成空壳，
     * 必须显式删掉 —— 照本仓「集合相等、消失也要红一次」的纪律。
     */
    @Test
    fun `债务清单里没有已经清掉的空壳`() {
        val resolved = debts.filterNot { it.stillThere() }.map { it.id }
        assertTrue(
            "这几条 Debt 的待摘点位已经不挂在白名单上了（要么已落地、要么判式对错了键名），把它从清单里删掉：" +
                resolved,
            resolved.isEmpty(),
        )
    }
}
