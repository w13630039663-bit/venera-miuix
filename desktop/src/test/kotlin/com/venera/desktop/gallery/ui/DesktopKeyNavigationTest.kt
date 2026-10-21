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
    /**
     * ⑦ 键盘与鼠标走**同一份**状态改动：步进与关闭各只有一个主人。
     *
     * 复制一份 `selected = selection.copy(...)` 给键盘，两份的差别会在第一次翻页时现形
     * （一边钳位、一边不钳位是这一族最常见的写法差）。
     */
    /**
     * ⑧ 搜索框回车**不造第二步**：那一屏即输入即重查，`KeyboardActions` 一条都不许接。
     *
     * 计划单第 ⑥ 条这一项是被量掉的，不是被遗忘的 —— 这条判据的作用是逼后来人先读那一句再决定。
     */
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
