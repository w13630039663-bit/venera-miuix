package com.venera.desktop.gallery.ui

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 桌面 UI 的**回调参数台账**（第九批收尾）。
 *
 * ## 为什么"带默认值的回调"要判死
 *
 * `onCleared: () -> Unit = {}` 这种形状一旦成立，"漏传"就**不报错**，
 * 而漏传的形状正好是"这个动作做完了，而没有任何一屏知道" —— 与手册 §23.8 撤掉那枚
 * `isBlocked = { false }` 是同一条病，也只是这一枚的消费者恰好是零。
 * 本仓对"实现了但零调用点"这一族已经记过一整轮的账（手册 §13.2 与那条同名记忆），
 * 而**带默认值的回调**是它最省事的诞生方式：写的人以为留了个可选项，读的人以为有人会用。
 *
 * ## 为什么扫源码而不是编译
 *
 * 这条判的是"不该存在的形状"（分层原则见手册 §10），而它没有可跑的对应物：
 * 少一个消费者不产生任何异常。所以扫**代码行**（`codeLinesOf` 跳注释，
 * 免得类注释里解释"为什么不许带默认值"时把那串照抄一遍就把自己判红）。
 */
class DesktopCallbackParamLedgerTest {

    /**
     * 形参声明那一行：`名字: (…) -> … = 默认值`。
     *
     * ⚠️ 结尾**必须允许逗号**：真实的一行是 `onCleared: () -> Unit = {},`，
     * 第一版把判式写成以 `=` 收尾，结果整条**恒绿**（假通过），而它该报的就是那一颗。
     */
    private val defaultedCallback = Regex("""^\p{L}[\p{L}\p{Digit}_]*: \(.*\)\s*->\s*.+=.+[,;]?$""")

    /** 必填回调（没有 `=`）：对照组靠它证明"扫到了形参行"这件事本身成立。 */
    private val requiredCallback = Regex("""^\p{L}[\p{L}\p{Digit}_]*: \(.*\)\s*->\s*[\p{L}\p{Digit}_.<>?]+,?$""")

    @Test
    fun 桌面UI里不许有带默认值的回调参数() {
        val offenders = DesktopSourceTree.desktopUiSources().flatMap { file ->
            DesktopSourceTree.codeLinesOf(file)
                .filter { defaultedCallback.matches(it) }
                .map { "${file.name}: $it" }
        }
        // ⚠️ 一次报全，不短路：这条抓的是"还有哪几枚同病"（手册 §23.3 那条读数）。
        assertTrue(
            "这些回调参数带了默认值 ⇒ 漏传不报错，而漏传的形状是「动作完成而没人知道」。" +
                "要必填就删掉 `= …`，调用点自己写：\n${offenders.joinToString("\n")}",
            offenders.isEmpty(),
        )
        // 对照组：整棵树里确实扫得到"必填回调"那种行 —— 否则上面那条是恒真的空判式。
        val required = DesktopSourceTree.desktopUiSources().sumOf { file ->
            DesktopSourceTree.codeLinesOf(file).count { requiredCallback.matches(it) }
        }
        assertTrue(
            "对照组失效：整棵桌面 UI 里一颗必填回调都没扫到（$requiredCallback 那判式飘了）",
            required >= 3,
        )
    }
}
