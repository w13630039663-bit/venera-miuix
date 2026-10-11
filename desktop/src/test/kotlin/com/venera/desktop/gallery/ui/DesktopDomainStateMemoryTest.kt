package com.venera.desktop.gallery.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 桌面第十二批 批 5 · 换域再换回来，那一屏不该重头。
 *
 * ## 排期里那两条都不是正解
 *
 * 计划单 §8 列的是 A（把每一屏的账提到根上那张表）与 B（别拆组合，七域同时留在树上）。
 * 动手前量到第三档，而它是 Compose 自己那一个：**`SaveableStateHolder.SaveableStateProvider(key)`** ——
 * 官方语义就是"把 subtree 里所有 `rememberSaveable` 在离开组合之前存下，用同一个 key 再组合时还原"
 * （`runtime-saveable` commonMain `SaveableStateHolder.kt:38-50`）。
 * 它既不像 A 那样要新造一张表并自己管清理，也不像 B 那样为了保状态而养七棵活树（= 冷启动七域同时发车）。
 *
 * ## ⚠️ 单用 `rememberSaveable` 救不了这一族（这条是量出来的，不是推的）
 *
 * `RememberSaveable.kt:91-100` 那一段：还原走的是 `registry?.consumeRestored(finalKey)`，
 * 而 `SaveableHolder.onForgotten` / `onDiscarded`（`:307`、`:311`）只是把 provider **unregister** 掉，
 * 没有任何一步把当前值写回 registry ⇒ **离开组合即丢**，它管的是"进程/配置重建"而不是"换屏再换回来"。
 * 必须配 `SaveableStateProvider` 那一圈，两者是一套。
 *
 * ## 还原了状态还不够：effect 一定会重跑
 *
 * 组合重新进来时 `LaunchedEffect` 必然重启（这是 effect 的语义，与 key 变不变无关）。
 * 所以每一面会发车的屏都要有一道"这一轮已经有结果就跳过"的守卫，否则用户看到的是
 * "状态明明还在，却又转了一圈圈"。这一条要求**状态那枚 `rememberSaveable` 的 inputs 与 effect 的 key 一致** ——
 * 不一致时"Ready"就不等于"为这一组输入取到手"，守卫会把该重取的那一笔挡掉
 * （`DesktopForYouPane` 的 `plan` 与 `DesktopGallerySearchPane` 的 `query` 都是这一型，改的时候一起对齐）。
 *
 * ## 这一批**不**碰漫画域
 *
 * `DesktopComicPane.kt:184` 那枚 `session: EngineSession?` 是一棵活的 GraalJS 会话。
 * 把它交给 holder 存下来 = 整场窗口生命周期永久持有（还要配"什么时候释放"那笔账），
 * 而这一批的判据面里没有那笔账的对象 ⇒ 刻意留在外面，那一屏今天仍然重头，理由写在下面那条负向判据里。
 */
class DesktopDomainStateMemoryTest {

    /** 每一面"会发车"的屏：文件、那颗取数态、取数 effect 的守卫该落在哪一段。 */
    private data class Screen(val file: String, val stateLine: String)

    private val fetchingScreens = listOf(
        Screen("DesktopDailyPane.kt", "var state: DailyState"),
        Screen("DesktopLatestPane.kt", "var state by"),
        Screen("DesktopForYouPane.kt", "var state by"),
        Screen("DesktopGallerySearchPane.kt", "var state: SearchState"),
    )

    /**
     * ① 根上挂着那颗 holder，而域路整块被包进 `SaveableStateProvider` —— 键**同时含域与行**
     * （换页与换域是同一件事：那一屏的账本来就该按"哪一屏"存）。
     */
    @Test
    fun 根上挂着一颗holder而域路被包进provider() {
        val app = DesktopSourceTree.codeText(DesktopSourceTree.desktopUiSource("VeneraDesktopApp.kt"))
        assertTrue("根上没有那颗 holder（`rememberSaveable` 单用离开组合即丢，见类注释）", app.contains("rememberSaveableStateHolder()"))
        assertTrue("域路没被包进 provider（没有那一圈，还原根本不会发生）", app.contains(".SaveableStateProvider("))
        val at = app.indexOf(".SaveableStateProvider(")
        val window = app.substring(at, minOf(at + 260, app.length))
        assertTrue("provider 的键里没读域：$window", window.contains("domain"))
        assertTrue("provider 的键里没读那一行：$window", window.contains("selectedKey"))
        assertTrue("provider 里面没摆域路本体", window.contains("DesktopDomainBody"))
        // 对照组：这颗文件不是读空了（读空会让上面那几条 contains 恒真）。
        assertTrue("对照组失效：根文件里连 rail 都没读到", app.contains("DesktopGalleryRail("))
    }

    /** 那一行是不是把值交给 saveable 了（`rememberSaveable {` 与 `rememberSaveable(x, y) {` 两种写法都算）。 */
    private fun onSaveable(line: String): Boolean =
        Regex("rememberSaveable\\s*[({]").containsMatchIn(line)

    /**
     * ② 每一面会发车的屏，取数态都必须是 `rememberSaveable`。
     *
     * 一次列出全部违例（本仓负向尺子的老规矩：撞到第一颗就停的那条尺子会把"还剩三颗"读成"只剩一颗"）。
     */
    @Test
    fun 取数态一律走saveable() {
        val offenders = fetchingScreens.filter { screen ->
            val code = DesktopSourceTree.codeText(DesktopSourceTree.desktopUiSource(screen.file))
            val line = code.lines().firstOrNull { it.trim().startsWith(screen.stateLine) }
            line == null || !onSaveable(line)
        }
        assertTrue(
            "这些屏的取数态还是普通 `remember` ⇒ 换回来那一刻它就被重掷成 Loading：" +
                offenders.joinToString(" | ") { "${it.file}:${it.stateLine}" },
            offenders.isEmpty(),
        )
    }

    /** ③ 每一面会发车的屏，取数 effect 开头要有一道"这一轮已有结果就跳过"的守卫（逐屏点名的那一句）。 */
    @Test
    fun 取数effect有跳过守卫() {
        val guards = mapOf(
            "DesktopDailyPane.kt" to "if (state is DailyState.Ready) return@LaunchedEffect",
            "DesktopLatestPane.kt" to "if (state is DesktopLatestState.Ready) return@LaunchedEffect",
            "DesktopForYouPane.kt" to "if (state is DesktopForYouState.Ready) return@LaunchedEffect",
            "DesktopGallerySearchPane.kt" to "if (state is SearchState.Ready) return@LaunchedEffect",
        )
        guards.forEach { (name, guard) ->
            val code = DesktopSourceTree.codeText(DesktopSourceTree.desktopUiSource(name))
            assertTrue(
                "$name 没有那道守卫（或守卫写的不是它自己那一档状态）⇒ 还原了状态还是每次进屏重发一笔：$guard",
                code.contains(guard),
            )
        }
    }

    /** ④ 种子那两枚必须落在 `saveable` 上 —— 否则"换回来"就是"换了一批卡"。 */
    @Test
    fun 种子换回来还是同一批() {
        listOf("DesktopDailyPane.kt", "DesktopForYouPane.kt").forEach { name ->
            val code = DesktopSourceTree.codeText(DesktopSourceTree.desktopUiSource(name))
            val lines = code.lines().filter { it.contains("System.currentTimeMillis()") }
            assertTrue("$name 里找不到那枚种子（判式先失效）", lines.isNotEmpty())
            val seeded = lines.filter { onSaveable(it) }
            assertTrue(
                "$name 那枚种子还在普通 `remember` 上 ⇒ 每次进这一屏整屏换序（读起来像页面自己抖了一下）：" + lines,
                seeded.isNotEmpty(),
            )
        }
    }

    /** ⑤ 已废弃的 `key =` 那颗参数一枚都不许有（它绕过位置定域，会串状态）。 */
    @Test
    fun 不许用已废弃的saveable键参数() {
        val offenders = DesktopSourceTree.desktopUiSources()
            .flatMap { file -> DesktopSourceTree.codeLinesOf(file).map { file.name to it } }
            .filter { (_, line) -> line.contains("rememberSaveable(") && line.contains("key =") }
        assertTrue(
            "用了 `rememberSaveable(key = …)`（1.12 已 @Deprecated：绕过位置定域，状态会串）：" +
                offenders.joinToString(" | ") { "${it.first}:${it.second}" },
            offenders.isEmpty(),
        )
    }

    /**
     * ⑥ 漫画域**刻意不接**这一档 —— 那条不许被"顺手补上"。
     *
     * 那一屏的 `session` 是一棵活的 GraalJS 会话，交给 holder 存下来就是整场持有；
     * 要接得先答"什么时候释放"。这条判据把那个决定钉成**明说的**而不是漏掉的。
     */
    @Test
    fun 漫画域今天不接那一档() {
        val code = DesktopSourceTree.codeText(DesktopSourceTree.desktopUiSource("DesktopComicPane.kt"))
        assertFalse(
            "漫画域被顺手接进 saveable 了（那枚 EngineSession 的释放还没算过账）",
            code.lines().any { onSaveable(it) },
        )
        // 对照组：这颗文件不是读空了。
        assertTrue("对照组失效：那一屏里连引擎会话都没读到", code.contains("EngineSession"))
    }
}
