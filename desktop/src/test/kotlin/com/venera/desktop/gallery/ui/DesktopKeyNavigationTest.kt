package com.venera.desktop.gallery.ui

import androidx.compose.ui.input.key.Key
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 桌面第十二批 批 3 · ⑥：浮层那三枚键（Esc 关、← / → 翻页）。
 *
 * ## 为什么这一批算"基础功能"而不是锦上添花
 *
 * 桌面是键鼠设备，而批 0 到批 2 之后整个模块**零键盘接线** —— 关掉浮层只有 × 那一枚、翻页只有两枚
 * 按钮。手册 §8 又禁了鼠标自动化，于是"浮层能关"这件事在无人值守下只能靠 `--detail` 反向证明
 * （它能摆出来），却没有任何一条路证明它能收回去。
 *
 * ## 焦点不是可选项：量过的两件事
 *
 * 一、`FocusOwnerImpl.dispatchKeyEvent`（`ui:1.12.0` commonMain:352-373）里那三行择路：
 *
 * ```
 * activeFocusTarget?.lastLocalKeyInputNode()
 *     ?: activeFocusTarget?.nearestAncestorIncludingSelf(Nodes.KeyInput)?.node
 *     ?: rootFocusNode.nearestAncestor(Nodes.KeyInput)?.node
 * ```
 *
 * 什么都没聚焦时 `activeFocusTarget` 是 null（`findFocusTargetNode()` = `rootFocusNode.findActiveFocusNode()`），
 * 只剩第三行；而 `rootFocusNode` 是焦点系统**自己造的那一颗**（`FocusOwnerImpl.kt:75`），它上面没有祖先
 * ⇒ 第三行恒为 null ⇒ **没有焦点时按键送不进组合树**。所以浮层必须自己拿焦点，
 * 挂在最外层那格的按键修饰不是"更省事"，是"看着接了其实收不到"。
 *
 * 二、compose-fluent `v0.1.0` 的 `ButtonKt` 常量池里只有 `clickable`/`combinedClickable`，
 * **没有任何 focus 相关符号** ⇒ 库里那枚按钮不可键盘聚焦，Tab 遍历今天整仓不存在（批 3 量掉的这条路）。
 * 本批因此只做浮层内那三枚键 —— 它自己拿焦点就能成立，不依赖库的遍历。
 *
 * ## 回车那一枚为什么不做
 *
 * 计划单第 ⑥ 条原本写着「搜索框回车发车」。量过之后它没有活干：那一屏是即输入即重查
 * （用户 2026-09-25 定的「条件即输入、加删即重查、别多一步提交」），再加一条回车通路就是第二步。
 * 判据 ⑧ 把这决定钉住 —— 防的是后来人"补一个 onSubmit 看起来更像搜索框"。
 */
class DesktopKeyNavigationTest {

    private fun down(key: Key) = DesktopKeySignal(key, isKeyDown = true)
    private fun up(key: Key) = DesktopKeySignal(key, isKeyDown = false)

    private class Recorder {
        val closed = mutableListOf<Int>()
        val steps = mutableListOf<Int>()
        /** 大图那一层（第五十批）的关闭计数。分开的理由：两层各有主人，塌成一个计数就看不出"退了两层"。 */
        val viewerClosed = mutableListOf<Int>()
        fun signal() = DesktopKeySignalRecorder(closed, steps, viewerClosed)
    }

    /** 动作以**计数**记录，而不是布尔：一次按键触发两次翻页与触发一次是两种缺陷。 */
    private class DesktopKeySignalRecorder(
        val closed: MutableList<Int>,
        val steps: MutableList<Int>,
        val viewerClosed: MutableList<Int>,
    ) {
        fun close() = closed.add(1)
        fun step(delta: Int) = steps.add(delta)
        fun closeViewer() = viewerClosed.add(1)
    }

    /** 既有四颗按键用例的落点：查看器一律关着（那一层今天还不存在，第四十九批之前这就是全部）。 */
    private fun DesktopKeySignalRecorder.key(signal: DesktopKeySignal): Boolean =
        onDesktopOverlayKey(
            signal,
            viewerOpen = false,
            onCloseViewer = ::closeViewer,
            onClose = ::close,
            onStep = ::step,
        )

    /**
     * ① 三枚键各只有**一个**落点：Esc 关、← → 各翻一张，其余一律不吞。
     */
    @Test
    fun 浮层那三枚键各有落点() {
        val rec = Recorder()
        val r = rec.signal()
        assertTrue("Esc 必须被消费（否则窗口把它交给系统，行为不可预期）", r.key(down(Key.Escape)))
        assertEquals(1, rec.closed.size)
        assertTrue(r.key(down(Key.DirectionRight)))
        assertTrue(r.key(down(Key.DirectionLeft)))
        assertEquals("三枚键之外不许再动关闭", 1, rec.closed.size)
    }

    /** ② 方向不许反：← 是上一张（-1），→ 是下一张（+1）。 */
    @Test
    fun 左右方向与翻页按钮同向() {
        val rec = Recorder()
        val r = rec.signal()
        r.key(down(Key.DirectionLeft))
        r.key(down(Key.DirectionRight))
        assertEquals(
            "键盘的 delta 必须与浮层那两枚按钮同向（屏上 ‹ 在左、› 在右）",
            listOf(-1, 1),
            rec.steps.toList(),
        )
        assertEquals("方向键不该顺手把浮层关掉", 0, rec.closed.size)
    }

    /** ③ 长按会连发 KeyUp/KeyDown：抬起那一半不许再翻一页，也不许被吞。 */
    @Test
    fun 抬起的那一半不重复动作() {
        val rec = Recorder()
        val r = rec.signal()
        r.key(down(Key.DirectionRight))
        assertFalse("KeyUp 被消费了 —— 长按一次翻两页就是这么来的", r.key(up(Key.DirectionRight)))
        assertFalse(r.key(up(Key.Escape)))
        assertEquals("抬起也算一次动作", 1, rec.steps.size)
        assertEquals(0, rec.closed.size)
    }

    /** ④ 不相干的键必须交回系统：吞掉键盘的浮层比没有键盘更坏。 */
    @Test
    fun 不相干的键不吞() {
        val rec = Recorder()
        val r = rec.signal()
        listOf(Key.A, Key.Tab, Key.Spacebar, Key.DirectionUp, Key.DirectionDown, Key.Enter, Key.Zero).forEach { key ->
            assertFalse("「$key」不该被浮层吃掉", r.key(down(key)))
        }
        assertEquals(0, rec.steps.size + rec.closed.size)
    }

    /**
     * ⑤ 浮层要**自己拿焦点**：`FocusRequester.requestFocus()` 的返回值不许丢掉，拿不到要有读数。
     *
     * 这一条钉的是本仓最忌的那个形状 —— 接线写了、量具也在，但失败是静默的。
     * 没有焦点 ⇒ 三枚键一律送不进来（见类注释里那三行择路），而屏上一点反应都没有、
     * stdout 一个字都不说，用户只会得到"这软件的 Esc 坏了"。
     */
    @Test
    fun 焦点拿不到要说出来() {
        val app = DesktopSourceTree.codeText(DesktopSourceTree.desktopUiSource("VeneraDesktopApp.kt"))
        assertTrue("根上没要焦点（没有焦点这三枚键根本送不进来）", app.contains("requestFocus()"))
        assertTrue(
            "requestFocus() 的返回值被丢掉了 —— 拿不到焦点就是静默失败",
            app.contains("if (!granted)"),
        )
        assertTrue("拿不到焦点时那条读数不在（KEYNAV 那一行是这条链唯一的可见证据）", app.contains("KEYNAV"))
        // 对照组：这个文件不是读空了（读空会让上面三条 contains 恒真）。
        assertTrue("对照组失效：根文件里连浮层那一格都没读到", app.contains("DesktopGalleryDetailPanel("))
    }

    /**
     * ⑥ 按键接线**一屏一处**，而每一处三件齐（focusRequester + focusTarget + onKeyEvent）。
     *
     * 第八十七批·下半把这条从「全模块只有一处」改成「两处、各在自己的那一屏上」。
     * 这条原来防的是同一条链被写两遍（两处都要自己拿焦点，同一枚键会被读两遍），那一条**没有放宽**：
     * 现在仍然只许两处，且各在哪一屏是钉住的。改成两处的前提是这两层不共存 ——
     * 而"不共存"不是口味，是 `LaunchedEffect(domain, selectedKey) { selected = null }` 那一句，
     * 所以下面把它一起钉着：哪天换域不收浮层了，这两处就会同时站着抢同一枚键，这条要红在那一刻。
     *
     * 少 `focusTarget()` ⇒ 节点不在焦点序里，`requestFocus()` 恒 false；
     * 少 `focusRequester(` ⇒ 没有要焦点的手；少 `onKeyEvent` ⇒ 拿到了焦点也没人读键。
     */
    @Test
    fun 按键接线一屏一处而两处各自三件齐() {
        // 判式带右花括号：import 那行 `androidx.compose.ui.input.key.onKeyEvent` 里也含 `.onKeyEvent`，
        // 不带花括号会把"import 了这颗修饰"数成一次接线 —— 数出 2 处然后红在一行无害的 import 上。
        val attachments = DesktopSourceTree.desktopMainSources()
            .flatMap { file ->
                DesktopSourceTree.codeLinesOf(file)
                    .filter { it.contains(".onKeyEvent {") || it.contains(".onPreviewKeyEvent {") }
                    .map { file.name }
            }
            .sorted()
        assertEquals(
            "按键接线该是一屏一处（画廊浮层那一屏与阅读那一屏），多一处就有人在同一枚键上读两遍：" +
                attachments.joinToString(" | "),
            listOf("DesktopComicReader.kt", "VeneraDesktopApp.kt"),
            attachments,
        )
        val app = DesktopSourceTree.codeText(DesktopSourceTree.desktopUiSource("VeneraDesktopApp.kt"))
        assertTrue(
            "换域不再收浮层 ⇒ 那两层同时站着、各自要焦点，同一枚键被读两遍（这条的前提塌了）。" +
                "钉的是**相邻两行**而不是那句键名：这一格里另有第二枚同键 `LaunchedEffect`，" +
                "只数「那串存在」会被第二枚喂饱（台架 P12 量的正是这一格）",
            app.contains("LaunchedEffect(domain, selectedKey) {\nselected = null"),
        )
        listOf("VeneraDesktopApp.kt", "DesktopComicReader.kt").forEach { name ->
            val text = DesktopSourceTree.codeText(DesktopSourceTree.desktopUiSource(name))
            val start = text.indexOf(".focusRequester(")
            assertTrue("$name 里没有 focusRequester（那一屏根本没要焦点）", start >= 0)
            val chain = text.substring(start, minOf(start + 500, text.length))
            assertTrue("$name 那条修饰链里没有 focusTarget() —— 节点不在焦点序里", chain.contains(".focusTarget()"))
            assertTrue("$name 那条修饰链里没有 onKeyEvent", chain.contains(".onKeyEvent {"))
            assertTrue(
                "$name 那条链没把事件交给共享判据件（写了第二份键位判据就是第二份真相）",
                chain.contains("asDesktopKeySignal()"),
            )
        }
        // 对照组：根文件没读空（读空会让上面那几串 contains 恒真）。
        assertTrue("对照组失效：根文件里连浮层那一格都没读到", app.contains("DesktopGalleryDetailPanel("))
    }

    /**
     * ⑦ 键盘与鼠标走**同一份**状态改动：步进与关闭各只有一个主人。
     *
     * 复制一份 `selected = selection.copy(...)` 给键盘，两份的差别会在第一次翻页时现形
     * （一边钳位、一边不钳位是这一族最常见的写法差）。
     */
    @Test
    fun 键盘与鼠标共用同一份步进() {
        val app = DesktopSourceTree.codeText(DesktopSourceTree.desktopUiSource("VeneraDesktopApp.kt"))
        assertEquals(
            "`selected = selection.copy(` 出现了多处 —— 键盘那一路复制了步进算式：" + app,
            1,
            DesktopSourceTree.codeOccurrences(
                DesktopSourceTree.desktopUiSource("VeneraDesktopApp.kt"),
                "selected = selection.copy(",
            ),
        )
        assertTrue("浮层那两枚按钮没吃抽出来的那颗 step（接线还是各写各的）", app.contains("onStep = step"))
        assertTrue("键盘那一路没吃同一颗 onClose", app.contains("onClose = onClose"))
        // 对照组：步进算式本身仍在这份文件里（否则上面那条"恰一处"会因为读空而恒真）。
        assertTrue("对照组失效：根文件里找不到钳位步进件", app.contains("desktopDetailStep("))
    }

    /**
     * ⑧ 搜索框回车**不造第二步**：那一屏即输入即重查，`KeyboardActions` 一条都不许接。
     *
     * 计划单第 ⑥ 条这一项是被量掉的，不是被遗忘的 —— 这条判据的作用是逼后来人先读那一句再决定。
     */
    @Test
    fun 搜索框回车没有第二步() {
        val pane = DesktopSourceTree.codeText(DesktopSourceTree.desktopUiSource("DesktopGallerySearchPane.kt"))
        assertFalse(
            "搜索屏接了 KeyboardActions —— 那一屏每敲一个字就重查一次，回车再发一次就是第二步（多一笔同样的请求）",
            pane.contains("KeyboardActions("),
        )
        // 对照组：输入框是真输入框（改掉 onValueChange 就是真的重查通路，读空会让上面那条恒真）。
        assertTrue("对照组失效：搜索屏里没有 onValueChange，那它根本没有输入框", pane.contains("onValueChange"))
    }

    /**
     * ⑨ `-Pdetail=` 必须真被拼进 `:desktop:app` 的参数（本批动手前量出来的洞）。
     *
     * 批 3 要做的是"浮层按 Esc 收回去"，第一件事就是**把浮层摆出来** —— 而 `-Pdetail=1` 打上去
     * 什么都不会发生：判读层（`ForensicFlags`）认 `--detail`，`VeneraDesktop.kt` 也把它接进了
     * `startDetail`，唯独构建脚本那串 `args(...)` 里没有这一枚（它只传 key / proxy / shot /
     * startDomain / query / startSection / autofav / favcheck）。
     * 那颗洞的形状恰好是本仓最忌的那一种：**参数给了不报错、屏上却是另一屏**，
     * 于是"浮层拍不到图"会被读成"浮层起不来"。
     * ⚠️ 这条判据扫的是构建脚本，而 gradle 不知道自己该为它重跑测试 ——
     * 那份文件已经由 `inputs.file(project.buildFile)` 声明成测试输入（第十批那一坑）。
     */
    @Test
    fun 浮层取证开关真被传进app任务() {
        val gradle = java.io.File(DesktopSourceTree.repoRoot, "desktop/build.gradle.kts").readText()
        assertTrue("`app` 任务没读 gradleProperty(\"detail\")", gradle.contains("""gradleProperty("detail")"""))
        assertTrue("`--detail` 没被拼进 args（值传不下去，判读层再对也接不到）", gradle.contains("\"--detail=\$it\""))
        // 对照组：读的是那颗任务体（读空会让上面两条 contains 恒真）。
        assertTrue("对照组失效：这份构建脚本里没有 :desktop:app 那颗粒任务", gradle.contains("""tasks.register<JavaExec>("app")"""))
    }

    // ── 第五十批：大图那一层的四枚（⑩~⑬）──────────────────────────────

    /** ⑩ 查看器开着时 Esc **只**关查看器，浮层那一句不许被叫。 */
    @Test
    fun `Esc 先关查看器`() {
        val rec = Recorder()
        val r = rec.signal()
        assertTrue(
            onDesktopOverlayKey(
                down(Key.Escape),
                viewerOpen = true,
                onCloseViewer = r::closeViewer,
                onClose = r::close,
                onStep = r::step,
            ),
        )
        assertEquals("Esc 必须只落在大图那一层上", 1, rec.viewerClosed.size)
        assertEquals("浮层被一起关了 —— 那就是点进去一下全没了", 0, rec.closed.size)
    }

    /** ⑪ 查看器关着时 Esc 才关浮层（次序的另一半，⑩ 单钉不构成"一层一层退"）。 */
    @Test
    fun `Esc 再关浮层`() {
        val rec = Recorder()
        val r = rec.signal()
        assertTrue(
            onDesktopOverlayKey(
                down(Key.Escape),
                viewerOpen = false,
                onCloseViewer = r::closeViewer,
                onClose = r::close,
                onStep = r::step,
            ),
        )
        assertEquals("大图那层已经关着，不该再动它", 0, rec.viewerClosed.size)
        assertEquals(1, rec.closed.size)
    }

    /** ⑫ 抬起那一半照旧不消费 —— 加了一层不许把长按连发那个老坑带进来。 */
    @Test
    fun `抬起那一半不吞`() {
        val rec = Recorder()
        val r = rec.signal()
        assertFalse(
            onDesktopOverlayKey(
                up(Key.Escape),
                viewerOpen = true,
                onCloseViewer = r::closeViewer,
                onClose = r::close,
                onStep = r::step,
            ),
        )
        assertEquals(0, rec.viewerClosed.size)
        assertEquals(0, rec.closed.size)
    }

    /**
     * ⑬ ← / → 在**两层里都**落到同一对 onStep。
     *
     * "步进只有一个主人"是批 46 立的：查看器不因自己开着就换一份算式，
     * 否则两份的第一处差别就会落在"一边钳位一边不钳位"上。
     */
    @Test
    fun `键与步进同向`() {
        val rec = Recorder()
        val r = rec.signal()
        onDesktopOverlayKey(down(Key.DirectionRight), viewerOpen = true, onCloseViewer = r::closeViewer, onClose = r::close, onStep = r::step)
        onDesktopOverlayKey(down(Key.DirectionLeft), viewerOpen = false, onCloseViewer = r::closeViewer, onClose = r::close, onStep = r::step)
        assertEquals(
            "大图开着与关着时方向键必须是同一个 delta（屏上 ‹ 在左、› 在右）",
            listOf(1, -1),
            rec.steps.toList(),
        )
    }
}
