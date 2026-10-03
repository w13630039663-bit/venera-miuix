package com.venera.compose.data.api

import com.venera.compose.testsupport.RepoSources
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 业务 API 的边界守卫：**UI 不许再直连实现类，白名单只许缩短**。
 *
 * 这是 `responsibility-dependency-audit-2026-10-03.md` §二 第 8 行（UI 直连基础设施）的可执行
 * 翻译。B0 落地时白名单登记的是**当前全量**（150 处取用 + 125 行实现类 import），断言写成
 * **集合相等**而非子集 —— 抄 [com.venera.compose.ui.tokens.TopBarFloorGuardTest] 的纪律：
 * 哪天某个点位自然消失、有人顺手删了名单里的一行，用例要红一次逼人来确认，而不是静默通过。
 *
 * ⚠️ 每条白名单都必须能被解读成「归哪一批摘掉」或「解锁条件」，见断言 F ——
 * 「FROZEN」本身不是解锁条件，「契约已就绪，只差一次点名豁免」才是。
 *
 * 覆盖面如实声明（不许假绿）：`RepoSources` 只看 `app/src/main/java`
 * （`testsupport/RepoSources.kt:31-35`），**扫不到 `desktop/src`、`app/src/test` 与 `res` 目录**。
 * `desktop/src` 今天不含 UI（只有宿主与平台实现），那一侧由 `:desktop:compileKotlin` 兜住。
 * 三个已知坑的防法逐条落在 [codeOf]（注释与尾随注释）与各 scan 的锚式写法里（声明行不可能命中
 * `.getInstance(`；类型引用位由断言 C 承担，它不依赖与 `getInstance` 同行）。
 */
class BusinessApiBoundaryTest {

    /** [portInterfaces] 的结果缓存：同一颗文件不必为每条断言重读一遍。 */
    private var cachedPorts: Set<String>? = null

    // ─────────────────────────── 扫描底座 ───────────────────────────

    /**
     * 「UI」的范围：四棵 UI 目录，外加 `MainActivity.kt` —— 它是 Activity 入口、不在那四棵目录里，
     * 但今天确实直连了两处单例（`:90` 取 `ComicSourceManager`、`:167` 取 `VeneraPreferences`），
     * 不把它算进来的话，这两处就只归 E 那条粗粒度断言管，A/B/C 三条细口径会漏掉它。
     */
    private fun uiLines() = RepoSources.allMainLines()
        .map { (rel, no, text) -> Triple(rel.removePrefix(ROOT), no, text) }
        .filter { t -> UI_DIRS.any { d -> t.first.startsWith(d) } || t.first == "MainActivity.kt" }

    /** 整行注释、KDoc 续行、以及**尾随注释**都不算命中。 */
    private fun codeOf(text: String): String {
        val t = text.trim()
        if (t.startsWith("//") || t.startsWith("*") || t.startsWith("/*")) return ""
        return t.split("//")[0]
    }

    private fun scan(pattern: Regex, group: Int = 1): List<Triple<String, Int, String>> =
        uiLines().mapNotNull { (rel, no, text) ->
            val code = codeOf(text)
            if (code.isEmpty()) return@mapNotNull null
            val m = pattern.find(code) ?: return@mapNotNull null
            Triple(rel, no, m.groupValues[group])
        }

    private fun scanImplCall() = scan(GET_INSTANCE).filter { it.third !in PLATFORM_ONLY }

    private fun scanImplImport(): List<Pair<String, String>> =
        uiLines().mapNotNull { (rel, _, text) ->
            val t = text.trim()
            val m = IMPORT.find(t) ?: return@mapNotNull null
            val sym = m.groupValues[1].split('.').last()
            if (!IMPL_SUFFIX.matches(sym)) return@mapNotNull null
            if (m.groupValues[1].startsWith("source.model")) return@mapNotNull null
            if (sym in EXCLUDED_SYMBOLS) return@mapNotNull null
            if (sym in portInterfaces()) return@mapNotNull null
            rel to sym
        }

    /**
     * 「被声明成 `interface` 的名字是端口，不是实现」—— 这条是自维持的排除法。
     *
     * 本轮交付的契约（`ContentGuard`、`ReaderPreferences`、`NetworkPreferences`、
     * `GalleryPreferences`…）后缀全都长得像实现类（`Preferences` 正是 `VeneraPreferences` 那个
     * 后缀），如果靠手写排除名单，每落一批就要加几颗名字，漏一颗就是白名单自己变红、
     * 而那红是**假的**。判据改成回源码看它到底是不是接口：`source/ComicSource.kt` 那种
     * "本来就是接口"的存量条目也一并自动落进这条规则里。
     * 数据类（`FavoriteItem` / `HistoryRecord` / `GuardRule`）不是接口，仍走 [EXCLUDED_SYMBOLS]。
     */
    private fun portInterfaces(): Set<String> {
        cachedPorts?.let { return it }
        val found = RepoSources.kotlinFiles("com/venera/compose")
            .flatMap { file ->
                INTERFACE_NAME.findAll(file.readText()).map { it.groupValues[1] }.toList()
            }.toSet()
        cachedPorts = found
        return found
    }

    /**
     * 断言 C 的判据：实现类的简单名出现在**非 import、非 `getInstance`、非注释**的行上 ——
     * 也就是类型引用位（参数类型、字段类型、`is`/`as?` 强转、伴生常量读取）。
     * 这类写法单行扫 `getInstance` 永远抓不到，正是 `PreferenceStorageNamesTest.kt:80-87`
     * 那条真实漏网的同形状（它要求同一行**同时**具备两个特征，于是 `SearchViewModel.kt:456` 的
     * 私有 const + `:72` 的构造两行写法全部扫不到）。
     */
    private fun scanTypeSite(): List<Pair<String, String>> =
        uiLines().flatMap { (rel, _, text) ->
            val t = text.trim()
            if (codeOf(t).isEmpty() || t.startsWith("import ")) return@flatMap emptyList()
            val code = t.split("//")[0]
            IMPL_NAMES.mapNotNull { sym ->
                if (!Regex("\\b$sym\\b").containsMatchIn(code)) return@mapNotNull null
                if (code.contains("$sym.getInstance")) return@mapNotNull null
                rel to sym
            }
        }

    private fun <K, V> report(label: String, actual: Map<K, Set<V>>, expected: Map<K, Set<V>>) {
        val diff = buildString {
            for ((f, s) in actual) {
                val news = s - (expected[f] ?: emptySet())
                if (news.isNotEmpty()) append("\n  新出现 $label @ $f -> $news")
            }
            for ((f, s) in expected) {
                val gone = s - (actual[f] ?: emptySet())
                if (gone.isNotEmpty()) append("\n  已消失 $label @ $f -> $gone（确实迁完了就把这行从白名单删掉）")
            }
        }
        assertEquals("$label 的白名单与实测不相等（只许缩短不许变长）：$diff", expected, actual)
    }

    private fun byFileSym(pairs: List<Pair<String, String>>) =
        pairs.groupBy({ it.first }, { it.second }).mapValues { it.value.toSet() }

    // ─────────────────────────── 六条断言 ───────────────────────────

    /** A ——「UI 目录不许 import 业务实现类」。B1 后基线 = 56 颗文件 / 119 条（B0 当天 57 / 128）。 */
    @Test
    fun `A UI 不许 import 业务实现类`() = report("import", byFileSym(scanImplImport()), BASELINE_IMPORT)

    /**
     * B ——「UI 目录不许 `实现类.getInstance`」。判据取**接收者简单名**，所以
     * `com.venera.compose.security.guard.ContentGuardManager.getInstance(…)` 这种全限定内联
     * （`feature/FavoritesScreen.kt:795` 就是这个形状）同样落网。
     *
     * 两道钉：① 文件 → 符号集合相等（哪颗文件还直连着谁，B1 后 50 颗）；② **站点总数**
     * （同一符号在一颗文件里取用三次算三处，B1 摘掉 14 处后 [SITE_TOTAL_GET_INSTANCE]=138）。
     * 基线**不记行号** —— B0 记过，结果同一文件加一行 import 就让别处行号全漂、白名单跟着假红。
     */
    @Test
    fun `B UI 不许直连取单例`() {
        val calls = scanImplCall()
        report("取用", byFileSym(calls.map { it.first to it.third }), BASELINE_GET_INSTANCE)
        assertEquals(
            "取用点位总数变了（迁完一处就从这里减掉；新增点位必须先加契约）",
            SITE_TOTAL_GET_INSTANCE,
            calls.size,
        )
    }

    /** C ——「不许把实现类当类型用」（跨两行的间接穿透）。B1 后 32 颗 / 48 条（B0 当天 33 / 49）。 */
    @Test
    fun `C 不许把实现类当类型用`() = report("类型引用位", byFileSym(scanTypeSite()), BASELINE_TYPE_SITE)

    /** D ——「非 getInstance 的直连」五张名单，按文件记**处数**（不记行号，理由同 B）。 */
    @Test
    fun `D 非 getInstance 的直连同样要登记`() {
        assertEquals("AndroidKeyValueStore( 的点位变了", BASELINE_KEY_VALUE_STORE, countsOf(Regex("AndroidKeyValueStore\\(")))
        assertEquals("HostCircuitBreaker. 的点位变了", BASELINE_BREAKER, countsOf(Regex("HostCircuitBreaker\\.")))
        assertEquals("okhttp3. 的点位变了", BASELINE_RAW_OKHTTP, countsOf(Regex("okhttp3\\.")))
        assertEquals("PreferredIpRuntime. 的点位变了", BASELINE_PREFERRED_IP, countsOf(Regex("PreferredIpRuntime\\.")))
        assertEquals("ComicStorageRoot. 的点位变了", BASELINE_STORAGE_ROOT, countsOf(Regex("ComicStorageRoot\\.")))
    }

    /**
     * E ——「静态取用的出处必须可数」：全仓 `*.getInstance` 只能出现在装配根、适配层、
     * 基础设施目录内部互调、**自己声明了这个 `getInstance` 的文件**，或 B0 登记的存量名单里。
     * 存量之外新增一处即红 ⇒ 这条不靠白名单变细，它靠"新出处一律红"。
     *
     * 与 A/B/C 同一条**集合相等**纪律：名单里的文件哪天不再取用了（迁完了），本条也会红一次，
     * 逼人来把它的行删掉 —— 抄 `TopBarFloorGuardTest.kt:53-71`（好消息必须有人显式确认）。
     *
     * 如实声明它的粗粒度：一颗文件只要自己声明过任意 `getInstance`，本条就整体放过它
     * （`feature/favoriteimages/FavoriteImagesManager.kt` 属于这种）。细的口径由 A/B/C 承担。
     */
    @Test
    fun `E 静态取用的出处必须可数`() {
        val declarers = RepoSources.kotlinFiles("com/venera/compose")
            .filter { Regex("fun getInstance\\(").containsMatchIn(it.readText()) }
            .map { relOf(it.absolutePath) }
            .toSet()
        val hits = RepoSources.allMainLines().mapNotNull { (rel, _, text) ->
            val code = codeOf(text)
            if (code.isEmpty()) return@mapNotNull null
            val m = GET_INSTANCE.find(code) ?: return@mapNotNull null
            if (m.groupValues[1] in PLATFORM_ONLY) return@mapNotNull null
            rel.removePrefix(ROOT)
        }.toSet()
        val legal = declarers + ADAPTERS + INFRA_DIRS + E_SEED
        val fresh = hits.filter { rel -> legal.none { rel == it || rel.startsWith(it) } }.sorted()
        val stale = E_SEED.filter { it !in hits }.sorted()
        assertTrue(
            "新增静态取用出处（要开口就加契约，别直连）：$fresh\n" +
                "存量已清空却没从 E_SEED 删掉：$stale",
            fresh.isEmpty() && stale.isEmpty(),
        )
    }

    /**
     * F ——「白名单不许有无主条目」：2026-10-03 那句「为这 15 处明确标注解锁条件」的机器化。
     *
     * 每个条目必须解读成**归属批次**（值以 `B` 开头）或**解锁条件**；后者要求
     * ① 长度 ≥ 20（光写 "FROZEN"、"待办" 不算）、② 含可复核的锚（`文件.kt:行号` / `W1..W6` /
     * `FREEZE-STATEMENT`）。空口理由不是理由。
     */
    @Test
    fun `F 白名单每条都要有归属或解锁条件`() {
        val entries = buildSet {
            for ((f, set) in BASELINE_IMPORT) for (s in set) add(Triple(f, s, "import"))
            for ((f, set) in BASELINE_GET_INSTANCE) for (s in set) add(Triple(f, s, "取用"))
            for ((f, set) in BASELINE_TYPE_SITE) for (s in set) add(Triple(f, s, "类型位"))
        }
        val orphans = entries.mapNotNull { (path, sym, kind) ->
            val why = attribOf(path, sym)
                ?: return@mapNotNull "$kind @ $path#$sym —— 既没归批次，也没写解锁条件"
            if (why.startsWith("B")) return@mapNotNull null
            val anchored = Regex("[A-Za-z]+\\.kt:\\d+").containsMatchIn(why) ||
                why.contains("FREEZE-STATEMENT") || Regex("W[1-6]").containsMatchIn(why)
            when {
                why.length < 20 -> "$kind @ $path#$sym —— 解锁条件太短：$why"
                !anchored -> "$kind @ $path#$sym —— 解锁条件没有可复核的锚：$why"
                else -> null
            }
        }
        assertTrue("白名单里有无主条目：\n${orphans.sorted().joinToString("\n")}", orphans.isEmpty())
    }

    /**
     * 反方向：归属表里也不许留**已消失点位的遗言**。
     *
     * 断言 F 管的是「点位有没有归属」，管不到反过来那一半 —— 而 D2 那批摘掉 10 行之后，
     * `SITE_OVERRIDE` 里当场浮出两条谁也不会想起去删的死键（其中一条还是 B6 那批留下的）。
     * 死键的危害不是难看：它让人以为那一行还在挂着，下一轮就照错的前提排活。
     */
    @Test
    fun `G 归属表里不许留已消失点位的死键`() {
        val live = buildSet {
            for ((f, set) in BASELINE_IMPORT) for (s in set) add("$f#$s")
            for ((f, set) in BASELINE_GET_INSTANCE) for (s in set) add("$f#$s")
            for ((f, set) in BASELINE_TYPE_SITE) for (s in set) add("$f#$s")
        }
        val dead = ATTRIBUTION.keys.filter { it.contains('#') && it !in live }.sorted()
        assertTrue(
            "这些「文件#符号」的归属条目对应的点位已经不挂在白名单上了，把归属行一起删掉：\n" +
                dead.joinToString("\n"),
            dead.isEmpty(),
        )
    }

    private fun countsOf(pattern: Regex): Map<String, Int> =
        scan(pattern, group = 0).groupingBy { it.first }.eachCount()

    private fun relOf(absolutePath: String) =
        absolutePath.replace('\\', '/').substringAfter("app/src/main/java/com/venera/compose/")

    private fun attribOf(path: String, sym: String): String? = ATTRIBUTION["$path#$sym"] ?: ATTRIBUTION[sym]

    private companion object {
        const val ROOT = "com/venera/compose/"
        val UI_DIRS = listOf("feature/", "gallery/ui/", "reader/", "components/")

        /** 平台 API 不是业务单例：`android.webkit.CookieManager`、`android.view.Choreographer`。 */
        val PLATFORM_ONLY = setOf("CookieManager", "Choreographer")

        val GET_INSTANCE = Regex("([A-Za-z0-9_]+)\\.getInstance\\(")
        val IMPORT = Regex("^import com\\.venera\\.compose\\.([A-Za-z0-9_.]+)$")
        val IMPL_SUFFIX = Regex(".*(Manager|Dao|Repository|Client|Breaker|Preferences|Store|Account|Resolver|Converter|JsComicSource)$")

        /** 声明形如 `interface X` / `internal sealed interface X`（用于把端口从实现里分出去）。 */
        val INTERFACE_NAME = Regex(
            "^\\s*(?:(?:public|internal|private|sealed|fun|abstract|nested)\\s+)*interface\\s+([A-Za-z0-9_]+)",
            RegexOption.MULTILINE,
        )

        /** 后缀像实现类、但判为端口/真底层的：`ComicSource` 是接口（正是端口对象本身）。 */
        val EXCLUDED_SYMBOLS = setOf("ComicSource", "FavoriteItem", "HistoryRecord", "GuardRule")

        /** 参与「类型引用位」扫描的名字集合 = 所有被裁决过的实现类简单名。 */
        val IMPL_NAMES: Set<String> = ATTRIBUTION.keys.map { it.substringAfterLast('#') }.toSet()

        /** 装配根 + 本轮的适配层：E 的合法出处。 */
        val ADAPTERS = setOf(
            "VeneraApp.kt",
            "data/api/android/AndroidBusinessPorts.kt",
            "data/platform/android/AndroidDatabasePorts.kt",
        )

        /** 基础设施目录：它们之间的互调不算「UI 穿透」（如 `gallery/domain/GalleryFeedSource.kt:75-77`）。 */
        val INFRA_DIRS = listOf(
            "source/", "data/", "download/", "stats/", "sync/", "security/", "engine/", "gallery/data/",
        )
    }
}
