package com.venera.compose.architecture

import com.venera.compose.testsupport.RepoSources
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 分层边界守卫：**下层不许 import 上层**（审计 `responsibility-dependency-audit-2026-10-03.md` 的 W3）。
 *
 * 为什么现在建它：B 系列每一批都在改「UI 直连实现类」那一条边（`BusinessApiBoundaryTest` 管的是
 * 上→下那半边），而下→上这半边今天**一条用例都没有** —— 也就是说接下来 B5/B7'/构造注入这几批
 * 往里写的每一行 import，都没人管。先立守卫再动那几批，顺序不能反。
 *
 * 它同时是 W1/W2 的**兑现探测器**：那两批的内容就是「类型从 `feature` 搬到 `source/model`」，
 * 一旦搬完，[BASELINE] 里 `source/js/JsComicSource.kt` 那三条会自动变成假条目、用例转红，
 * 逼人来把名单摘干净 —— 这正是「解锁条件已兑现但债务未清」的可执行形态（不用人肉记）。
 *
 * 覆盖面如实声明（不许假绿）：只扫 `app/src/main/java`（`testsupport/RepoSources.kt:31-35`），
 * 看不到 `desktop/src` 与测试源树；只看 **import 行**，所以同包内引用（不需要 import）不在口径内 ——
 * 下层与上层从来不同包，这条成立。`res`/manifest 更管不到。
 */
class LayeringEdgeTest {

    // ─────────────────────────── 层的定义 ───────────────────────────

    /**
     * 下层 = 不含 Composable 的那几棵：数据、平台、源、统计、守卫、下载、同步、引擎、画廊的数据与域层。
     *
     * 逐棵对着 `app/src/main/java/com/venera/compose/` 的实际目录列，判据是「这颗包里有没有
     * `@Composable`」。`engine/` 是 JS 引擎壳、`sync/` 与 `download/` 是 IO 层，都算下层。
     */
    private val lowerDirs = listOf(
        "data/",
        "source/",
        "stats/",
        "security/",
        "download/",
        "sync/",
        "engine/",
        "gallery/data/",
        "gallery/domain/",
    )

    /**
     * 上层 = UI 那五棵 + 根目录的 Activity 壳（`MainActivity.kt`、`VeneraApp.kt` 等同层文件）。
     *
     * 根类那条判式只吃**单段**包名（`com.venera.compose.XxxActivity`），多段的 `…compose.source.model`
     * 不会被误算；`stats/`、`ui/tokens/` 里的纯数据不算违规，违规判的是「谁 import 谁」不是「什么是数据」。
     */
    private val upperImport = Regex("""^import com\.venera\.compose\.(feature|gallery\.ui|reader|components|ui)\b""")
    private val rootImport = Regex("""^import com\.venera\.compose\.[A-Z][A-Za-z0-9_]*$""")

    /**
     * `RepoSources.linesOf` 给的相对路径是相对 `app/src/main/java` 的，所以带 `com/venera/compose/`
     * 前缀（口径照 `data/api/BusinessApiBoundaryTest.kt:238`）。不剥这层的判式会**恒不命中** ——
     * 本守卫第一版就是这么扫出 0 条的，靠下面那条「扫描口径自己要有读数」才没变成假绿。
     */
    private fun lowerLines() = RepoSources.linesOf("com")
        .map { (rel, no, text) -> Triple(rel.removePrefix(ROOT), no, text) }
        .filter { (rel, _, _) -> lowerDirs.any { d -> rel.startsWith(d) } }

    /**
     * 一条命中：`相对路径 -> 被 import 的全名（去掉包前缀）`。
     *
     * 路径前缀判定与行文本判定分开写：`rel` 拿去比 [lowerDirs]，`text.trim()` 拿去比 import 判式，
     * 别把两者混在一个三元组里绕。注释行（`//`、KDoc 的 `*` 续行、块注释起始）不算命中 ——
     * 本仓的 KDoc 里大量出现「照 feature/… 那样」这类字样。
     */
    private fun lowerViolations(): List<Pair<String, String>> =
        lowerLines()
            .mapNotNull { (rel, _, text) ->
                val t = text.trim()
                if (t.startsWith("//") || t.startsWith("*") || t.startsWith("/*")) return@mapNotNull null
                val m = upperImport.find(t) ?: rootImport.find(t) ?: return@mapNotNull null
                rel to t.removePrefix("import com.venera.compose.")
            }
            .sortedBy { (rel, sym) -> "$rel#$sym" }

    // ─────────────────────────── 基线：10 条 / 5 颗（2026-10-03 实测） ───────────────────────────

    /**
     * 每条都有「归哪一批摘掉」或「为什么长期留存」，口径照 `data/api/BusinessApiBaseline.kt`：
     * 键是**文件 → 符号集合**，绝不写行号（行号会同一天被别的提交挪掉，把白名单跟着假红）。
     *
     * 断言写成集合相等，删多了也要红一次（照 `ui/tokens/TopBarFloorGuardTest` 与
     * `BusinessApiBoundaryTest` 的纪律）。
     */
    private val BASELINE: Map<String, Set<String>> = mapOf(
        "data/network/CloudflareBypassActivity.kt" to setOf(
            "components.venera.VeneraTextButton",
            "feature.VeneraTheme",
        ),
        "data/prefs/ComicListPreferences.kt" to setOf(
            "components.normalizeComicDisplayMode",
        ),
        "data/prefs/VeneraPreferences.kt" to setOf(
            "ui.tokens.ThemeSeedPresets",
        ),
        "security/guard/ContentGuardManager.kt" to setOf(
            "feature.ComicItem",
        ),
        "source/js/JsComicSource.kt" to setOf(
            "feature.sourcemanage.SelectOption",
            "feature.sourcemanage.SourceAccountInfo",
            "feature.sourcemanage.SourceSettingItem",
        ),
        "sync/BackupManager.kt" to setOf(
            "feature.favoriteimages.FavoriteImagesManager",
            "feature.favoriteimages.ImageFavoriteBackupRows",
        ),
    )

    /** 逐条解锁条件：报错时直接把「该谁来做、前置是什么」打在断言消息里。 */
    private val UNLOCK: Map<String, String> = mapOf(
        "data/network/CloudflareBypassActivity.kt" to
            "这颗是 Activity（UI 宿主）住在了 data/network 包。解锁 = 移包到根目录（与 MainActivity.kt 同级），" +
            "但要连带改 AndroidManifest 里那条 activity name —— 动的是发布物形状，须单独一批 + 真机验一次过盾链路，" +
            "不属本轮任何一批",
        "data/prefs/ComicListPreferences.kt" to
            "一行纯判据（components/ComicPresentationPolicy.kt:16）住在了 UI 层。" +
            "解锁 = 把 normalizeComicDisplayMode 下移进 data/prefs，三处消费点（ComicListPresentation、" +
            "ComicPresentationPolicy、本颗）一并改引；零 IO、零行为，可独立小批做，本轮没做是因为它要动 " +
            "components 那棵的公开面",
        "data/prefs/VeneraPreferences.kt" to
            "纯常量表住错层：ThemeSeedPresets 在 ui/tokens/Color.kt:472，而偏好侧只吃它的 DefaultArgb。" +
            "解锁 = 整颗 ThemeSeedPresets 下移到 data/prefs（它是纯数据、没有 Composable），" +
            "feature/settings/AppearanceSettings.kt 改引；上→下的 import 是合法方向",
        "security/guard/ContentGuardManager.kt" to
            "ComicItem 是 data class（feature/ComicItem.kt:27）却住在 UI 包里 —— 与 W1 同族。" +
            "解锁 = W1 的类型搬家那一批（迁 source/model 或新建 data/model），守卫这侧不用动",
        "source/js/JsComicSource.kt" to
            "W2 的目标本体：SelectOption / SourceAccountInfo / SourceSettingItem 三枚类型住在 " +
            "feature/sourcemanage/，而它们是 JsComicSource 的**返回类型**。" +
            "解锁 = W2 完成后这三条自动变假条目、用例转红，那时来摘名单",
        "sync/BackupManager.kt" to
            "半条在 B5 之后可摘：FavoriteImagesManager.getInstance(:108) 改走收藏契约；" +
            "另一条 ImageFavoriteBackupRows 是图片收藏的备份行模型，住在 feature/favoriteimages/，" +
            "要随图片收藏侧一起收口（口径同方案 §六 的 W5），B5 单批摘不干净 —— 所以名单按符号粒度记，" +
            "摘掉一半就要红一次",
    )

    // ─────────────────────────── 断言 ───────────────────────────

    /** 主断言：实测违规集合 == 基线集合。新增一条即红，少一条也红（少了一条 = 名单该摘）。 */
    @Test
    fun `下层不许 import 上层，且白名单只许与实际一致`() {
        val found = lowerViolations()
        val actual = found.groupBy({ it.first }, { it.second }).mapValues { it.value.toSet() }

        val extra = actual.entries.toSet() - BASELINE.entries.toSet()
        val gone = BASELINE.entries.toSet() - actual.entries.toSet()
        val message = buildString {
            append("分层违规集合与基线不一致。\n")
            if (extra.isNotEmpty()) {
                append("新出现的（不许新增下层→上层的 import；把它改走契约或把类型搬下来）：\n")
                extra.forEach { (rel, sym) -> append("  $rel -> $sym\n") }
            }
            if (gone.isNotEmpty()) {
                append("已消失但名单还挂着（该摘，否则这条守卫就成死开关）：\n")
                gone.forEach { (rel, sym) -> append("  $rel -> $sym\n") }
            }
        }
        assertEquals(message, BASELINE.entries.sortedBy { it.key }, actual.entries.sortedBy { it.key })
    }

    /** 白名单每颗文件必须有解锁条件；「FROZEN」不算条件（照 BusinessApiBoundaryTest 断言 F 的口径）。 */
    @Test
    fun `基线里每一条都要写明解锁条件`() {
        val missing = BASELINE.keys.filter { UNLOCK[it] == null }
        assertTrue("缺解锁条件：$missing", missing.isEmpty())

        val stale = UNLOCK.keys.filter { it !in BASELINE }
        assertTrue("解锁条件挂在已不存在的文件上（名单该同步）：$stale", stale.isEmpty())

        UNLOCK.forEach { (file, note) ->
            assertTrue("$file 的解锁条件太短，不像是能执行的条件：$note", note.length > 40)
        }
    }

    /** 兜住扫描本身：口径若因为目录改名而扫不到东西，用例必须红而不是「通过」。 */
    @Test
    fun `扫描口径自己要有读数`() {
        val lowerFileCount = lowerLines().map { it.first }.toSet().size
        assertTrue(
            "下层只扫到 $lowerFileCount 颗文件（实测 171），判式八成漂了 —— 源码扫描用例静默通过等于没有用例",
            lowerFileCount > 100,
        )
        assertEquals(
            "命中总条数与基线总条数对不上（改基线时漏改其中一处）",
            10,
            lowerViolations().size,
        )
        assertEquals(6, BASELINE.size)
    }

    private companion object {
        const val ROOT = "com/venera/compose/"
    }
}
