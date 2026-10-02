package com.venera.compose.architecture

import com.venera.compose.testsupport.RepoSources
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ViewModel 装配守卫：D3「构造注入」这条改动**能被机器复查**的那三条判据。
 *
 * 为什么要有这颗文件：D3 换的是「端口从哪来」，属编译期看不出错、跑起来也大多不错的改动，
 * 而本仓的 JVM 用例不吃 Robolectric —— 也就是说**没有任何用例能证明 `viewModel()` 还建得出 VM**。
 * 加上这三条之后，形状漂移（有人把 arity=1 那行删了、或者又把 `BusinessPorts.of` 写回 VM 方法体）
 * 会在 `:app:testDebugUnitTest` 里红，而不是在真机上以「首页空白」的形式出现。
 *
 * 顺带钉住一个我**自己写错过两遍**的数字：`viewModel()` 的真调用点。第一次量成 18（把
 * `BusinessPorts.kt` 自己那句 KDoc 算成了调用点），第二次是 §一 #20 的原句。口径 =
 * 含 `viewModel()` 且**不是注释行**的行数，实测 17；注释行 4 处。
 */
class ViewModelAssemblyGuardTest {

    private fun lines() = RepoSources.allMainLines()
        .map { (rel, no, text) -> Triple(rel.removePrefix(ROOT), no, text) }

    /** 注释行不算命中（KDoc 续行 ` * `、`//`、块注释起始）。 */
    private fun isCode(text: String): Boolean {
        val t = text.trim()
        return !(t.startsWith("//") || t.startsWith("*") || t.startsWith("/*"))
    }

    private fun vmLines(): List<Triple<String, Int, String>> =
        lines().filter { (rel, _, _) -> rel.substringAfterLast('/').endsWith("ViewModel.kt") }

    // ─────────────── 1. 服务定位器不许留在 VM 的方法体里 ───────────────

    /**
     * 一颗 VM 只许在**它自己的委托构造行**里出现一次 `BusinessPorts.of(`；方法体/属性初始化器里
     * 出现即红。按文件计成一张表比对（照本仓白名单纪律：集合相等，多一条少一条都红）。
     */
    private val LOCATOR_EXEMPT: Map<String, String> = mapOf(
        "feature/sourcemanage/ComicSourceViewModel.kt" to
            "它今天构造期一次都不碰端口图（唯一的 of() 在 :571 那笔裸 okhttp 的替代里，属函数体）。" +
            "把 ports 注进主构造会把建图提前到 VM 构造 = 时序变更，所以 D3 不动这颗；" +
            "它同时是 §七.1 与 W2 的文件。摘除条件 = W2 落地、或那笔取用改走 HttpTextFetch 的构造注入形态",
    )

    @Test
    fun `VM 的方法体里不许再出现 BusinessPorts of`() {
        val bodies = vmLines()
            .filter { (_, _, text) -> isCode(text) }
            .filter { (_, _, text) -> text.contains("BusinessPorts.of(") }
            .filter { (_, _, text) -> !Regex("""^\s*constructor\s*\(""").containsMatchIn(text) }
            .groupBy({ it.first }, { it.second })
            .mapValues { it.value.size }

        val bodyPairs = bodies.entries.map { it.key to it.value }.sortedBy { it.first }
        assertEquals(
            "VM 体内直取端口（应改走构造参数 ports）。豁免名单见 LOCATOR_EXEMPT：\n" +
                bodyPairs.joinToString("\n") { (f, n) -> "  $f -> $n 处（解锁：${LOCATOR_EXEMPT[f] ?: "无豁免，就是要改"}）" },
            LOCATOR_EXEMPT.entries.map { it.key to 1 }.sortedBy { it.first },
            bodyPairs,
        )
    }

    // ─────────────── 2. arity=1 那行构造不许消失 ───────────────

    /**
     * `viewModel()` 走的是 `AndroidViewModelFactory`，它反射 `getConstructor(Application::class.java)` ——
     * 主构造加了第二枚参数之后，**arity=1 那行委托构造就是唯一的调用点兼容来源**。
     * 删掉它不会让本模块编译失败（没有别的 Kotlin 调用点），只会在运行期抛
     * "Cannot create an instance of class …" —— 所以必须在这儿钉住。
     */
    @Test
    fun `注入端口的每一颗 VM 都要留着 arity=1 的委托构造`() {
        val text = vmLines().joinToString("\n") { it.third }
        val filesWithInjectedPorts = RepoSources.kotlinFiles("com/venera/compose")
            .filter { it.name.endsWith("ViewModel.kt") }
            .filter { it.readText().contains("private val ports: BusinessPorts") }
            .map { it.relativeTo(RepoSources.appMain).path.replace('\\', '/').removePrefix(ROOT) }
            .sorted()

        assertEquals(
            "主构造吃端口的 VM 清单漂了（新增要走 D3 的形状，减少要说明为什么）",
            INJECTED.sorted(),
            filesWithInjectedPorts,
        )

        val delegating = Regex("""constructor\s*\(\s*\w+\s*:\s*Application\s*\)\s*:\s*this\(\s*\w+\s*,\s*BusinessPorts\.of\(""")
            .findAll(text).count()
        assertEquals("委托构造行数应与已注入的 VM 数相等", INJECTED.size, delegating)
        assertTrue("注入清单不能是空的（空 = 判式漂了而不是通过）", INJECTED.isNotEmpty())
    }

    /** D3 落地时已注入端口的 7 颗（`ComicSourceViewModel` 不在内，理由见 [LOCATOR_EXEMPT]）。 */
    private val INJECTED = listOf(
        "feature/ComicDetailViewModel.kt",
        "feature/FavoritesViewModel.kt",
        "feature/FollowUpdatesViewModel.kt",
        "feature/HistoryViewModel.kt",
        "feature/HomeViewModel.kt",
        "feature/NetworkFavoritesViewModel.kt",
        "feature/SearchViewModel.kt",
    )

    // ─────────────── 3. 调用点数量：把散在文档里那个数交给机器 ───────────────

    @Test
    fun `viewModel 真调用点仍是 17 处，且冻结屏内那 7 处没被改动`() {
        val sites = lines()
            .filter { (_, _, text) -> isCode(text) }
            .filter { (_, _, text) -> text.contains("viewModel()") }
            .map { it.first }
        assertEquals(
            "调用点数变了 —— 要么有人给某颗 VM 上了 ViewModelProvider.Factory（那是新增点位，要走评审），" +
                "要么有 VM 被 remember 掉了（那是行为变更）。逐处：" + sites.groupingBy { it }.eachCount(),
            17,
            sites.size,
        )

        val frozen = setOf(
            "feature/explore/UnifiedExploreScreen.kt",
            "feature/explore/SourceSectionScreen.kt",
            "feature/SearchScreen.kt",
            "feature/HistoryScreen.kt",
            "feature/FavoritesScreen.kt",
            "feature/NetworkFavoritesScreen.kt",
            "feature/HomeScreen.kt",
        )
        val inFrozen = sites.filter { it in frozen }.distinct()
        assertEquals(
            "冻结屏内的 viewModel() 应恰好是这 7 颗文件各一处（D3 的整条前提就是调用点零改动）：" + inFrozen,
            frozen.size,
            inFrozen.size,
        )
    }

    private companion object {
        const val ROOT = "com/venera/compose/"
    }
}
