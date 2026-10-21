package com.venera.desktop

import com.venera.desktop.gallery.ui.DesktopSourceTree
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 钉住「首页历史区不许把『还没读到』说成『还没有记录』」—— 2026-10-21 实测抓到的一颗真 bug。
 *
 * ## 那颗 bug 长什么样
 *
 * `HistoryDao.refresh()`（`data/db/HistoryDao.kt:48-50`）**不是同步的**：
 * ```kotlin
 * fun refresh() { scope.launch { _historyFlow.value = getAllHistorySync() } }
 * ```
 * 发起即返回，而 `_historyFlow` 的初值是 `emptyList()`。
 *
 * 第一版 `DesktopHomeHistorySource.read()` 写的是：
 * ```kotlin
 * dao.refresh()
 * DesktopHomeHistorySnapshot(records = dao.historyFlow.value, loaded = true, note = null)
 * ```
 * 两行紧挨 ⇒ 协程还没被调度 ⇒ 读到初值 `emptyList()` ⇒ 配合 `loaded = true`，
 * 屏上显示**「还没有阅读记录。读一本之后，这里会出现最近 4 部。」**
 *
 * 而当时 `comic_history` 表里**有 3 行**（`_qa/home-with-history.png` 是证据：
 * 统计区 42/74/3 是真值，历史区却斩钉截铁说没有）。
 *
 * **这是"假数据"的另一种形状**：不是编造数字，是把"还没读到"当成"确实没有"，
 * 还配了一句很肯定的文案。比编数字更难查 —— 因为编的数字至少看起来像数字，
 * 而"还没有阅读记录"看起来像是**功能正确**。
 *
 * ## 为什么这条尺要扫源码而不是跑行为
 *
 * 要真跑出来得：造一颗库、塞历史、让协程真正调度、再观察首帧 —— 而首帧那几十毫秒
 * 在单元测试里本来就不可靠（跑得快反而抓不到）。所以这里钉**形状**：
 *   ① `read()` 里不许出现 `refresh()` 之后**直接**读 `.value`；
 *   ② 必须有一个「等 flow 出值」的动作（`first()` / `awaitFirst` / 超时兜底）；
 *   ③ 屏上「还没读」与「确实没有」必须是两句不同的话。
 */
class DesktopHomeHistoryRaceTest {

    private fun src(name: String): File {
        val f = File(DesktopSourceTree.repoRoot, "desktop/src/main/kotlin/com/venera/desktop/$name")
        assertTrue("读不到 $name —— 判据失效（不是通过）：${f.path}", f.isFile)
        return f
    }

    private fun code(file: File): List<String> = file.readText().lines().filterNot { ln ->
        val s = ln.trim()
        s.startsWith("//") || s.startsWith("*") || s.startsWith("/*")
    }

    @Test
    fun `refresh 之后不许直接读 value 那是初值`() {
        val lines = code(src("DesktopHomeHistorySource.kt"))
        val refreshAt = lines.indexOfFirst { "dao.refresh()" in it }
        assertTrue("找不到 `dao.refresh()` —— 判据失效", refreshAt >= 0)

        // refresh 之后 3 行内不允许直接取 `.value`（那就是踩初值）
        val window = lines.drop(refreshAt).take(4)
        val direct = window.any {
            "historyFlow.value" in it && ".first(" !in it && "getAllHistorySync" !in it
        }
        assertTrue(
            "`dao.refresh()` 之后直接读 `historyFlow.value` —— `refresh()` 是 " +
                "`scope.launch { }`（`HistoryDao.kt:48-50`），发起的协程还没跑，" +
                "读到的是初值 emptyList()。绑定 `loaded = true` 就会把「没读到」说成「没有记录」。\n" +
                "实际窗口：\n" + window.joinToString("\n"),
            !direct,
        )
    }

    @Test
    fun `必须等 flow 真的出值 且有超时兜底`() {
        val text = code(src("DesktopHomeHistorySource.kt")).joinToString("\n")
        // 形状必须**带谓词**：`first()` 会立刻返回当前值（初值 emptyList()），
        // 而 `first { it.isNotEmpty() }` 才是"等一个满足条件的值"。这一条正是第二层坑
        // （第一版把它写成不带谓词的 `first()`，屏上与没修一模一样）。
        assertTrue(
            "没有「等一个满足条件的值」的动作 —— 应形如 `historyFlow.first { it.isNotEmpty() }`。" +
                "注意 `first()` 不带谓词时返回的是当前值（初值），等于没等。",
            Regex("historyFlow\\.first\\s*\\{").containsMatchIn(text),
        )
        assertTrue(
            "没有超时兜底 —— 真没有历史时会永远挂着。应有 `withTimeoutOrNull`",
            "withTimeoutOrNull" in text,
        )
    }

    @Test
    fun `还没读 与 确实没有 必须是两句话`() {
        val section = code(src("DesktopHomeHistorySection.kt")).joinToString("\n")
        assertTrue(
            "历史区没有「还没读回来」那一句（`!loaded` 分支）—— " +
                "少了它，冷启动那一帧与「确实没有记录」同形",
            "!loaded" in section,
        )
        // 两句文案必须是不同的字符串
        // 只取**单行内**的字符串字面量：跨行匹配会把 	ext = "..." 到下一个引号之间的
        // 代码全吞进来（第一次写就踩了这个，报错信息里全是 fontSize/color）。
        val msgs = Regex("\"([^\"\n]{6,})\"")
            .findAll(section)
            .map { it.groupValues[1] }
            .filter { !it.startsWith("Modifier") && !it.contains("tokens.") }
            .toList()
        val hasLoading = msgs.any { "正在读" in it || "读取中" in it }
        val hasEmpty = msgs.any { "还没有阅读记录" in it }
        assertTrue("缺「正在读…」那句文案，实际有：$msgs", hasLoading)
        assertTrue("缺「还没有阅读记录」那句文案，实际有：$msgs", hasEmpty)
        assertTrue(
            "两句文案不能是同一句 —— 那等于把「没读到」谎报成「没读过书」",
            msgs.filter { "正在读" in it || "还没有阅读记录" in it }.distinct().size >= 2,
        )
    }
}
