package com.venera.compose

import com.venera.compose.testsupport.RepoSources
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **文案纪律尺**（2026-10-20，主人「全部一起改掉吧」）。
 *
 * ## 这一族防的是什么
 *
 * 全仓约 600 处用户可见中文字符串，历史上长出了一整套**机器腔**：一句里塞两三个分句、
 * 技术名词直接甩给用户、失败态全用一个模板、把异常串原样贴到屏上。
 * 主人原话是「有什么很像 ai 的话，改成人说的那种简单简洁」。
 *
 * 六笔各钉一件事：
 *
 * 1. **禁机器腔词**：`的的`、`将会`、`从而实现`、`值得注意的是`、`可能是网络波动` 这类；
 * 2. **禁内部术语**：`种子色`、`静态色板`、`明文字段` 不许出现在用户可见串里；
 * 3. **禁把异常变量甩进 UI**：`$error` / `$state.networkError` 直接进 Text/Toast；
 * 4. **失败态标题不许雷同**：`XX加载失败` 这一族最多两处（曾经 7 处一模一样）；
 * 5. **「重试」这词最多三处**：曾经 17 处（各自场景不同，不该共用一个词）；
 * 6. **禁半截话**：以「如果」「当」「点击此处」结尾却没有下文的短句。
 *
 * 尺子只扫**用户可见的那些行**（`Text(` / `text =` / `summary =` / `title =` /
 * `actionText =` / `Toast` / `contentDescription =` / `label =` / `hint =`），
 * 注释行不参与 —— 注释里写技术细节是对的。
 */
class CopyDisciplineTest {

    private val roots = listOf(
        "com/venera/compose/feature",
        "com/venera/compose/gallery",
        "com/venera/compose/reader",
        "com/venera/compose/components",
    )

    /** 用户可见那一行的判据：这些写法后面的字符串会摆到屏上。 */
    private val visibleMarkers = listOf(
        "Text(\"", "Text(\"", "text = \"", "summary = \"", "title = \"", "actionText = \"",
        "Toast.makeText(", "contentDescription = \"", "label = \"", "hint = \"", "placeholder = \"",
    )

    /** 全仓用户可见行（原文件名 → 行号 → 原文）。 */
    private fun visibleLines(): List<Triple<String, Int, String>> {
        val out = mutableListOf<Triple<String, Int, String>>()
        for (root in roots) {
            val dir = File(RepoSources.appMain, root)
            if (!dir.isDirectory) continue
            dir.walkTopDown().filter { it.isFile && it.name.endsWith(".kt") }.forEach { f ->
                f.readText().replace("\r\n", "\n").lines().forEachIndexed { i, line ->
                    val t = line.trimStart()
                    // 注释行不参与：注释里写技术细节是**对的**，尺子不该逼人把解释删掉。
                    if (t.startsWith("//") || t.startsWith("*") || t.startsWith("/*")) return@forEachIndexed
                    if (visibleMarkers.any { line.contains(it) } && Regex("[\\u4e00-\\u9fa5]{2,}").containsMatchIn(line)) {
                        out += Triple(f.name, i + 1, line.trim())
                    }
                }
            }
        }
        return out
    }

    @Test
    fun `用户可见文案不许长机器腔`() {
        // 这几颗词都是"AI 写产品文案"的典型标记：读起来不像人说话。
        val banned = listOf(
            "的的", "将会", "从而实现", "值得注意的是", "需要注意的是",
            "可能是网络波动", "请稍后重试", "不妨", "务必请注意",
        )
        val hits = visibleLines().filter { (_, _, line) -> banned.any { line.contains(it) } }
        assertEquals(
            "用户可见文案里不许出现机器腔：\n" + hits.joinToString("\n") { "${it.first}:${it.second} ${it.third}" },
            0,
            hits.size,
        )
    }

    @Test
    fun `用户可见文案不许出现内部术语`() {
        // 这些是**实现细节**，用户不该看到（主人「改成人说的那种」）。
        val jargon = listOf("种子色", "静态色板", "动态色板", "明文字段", "原子替换", "熔断")
        val hits = visibleLines().filter { (_, _, line) -> jargon.any { line.contains(it) } }
        assertEquals(
            "用户可见文案里不许出现内部术语：\n" + hits.joinToString("\n") { "${it.first}:${it.second} ${it.third}" },
            0,
            hits.size,
        )
    }

    @Test
    fun `失败态不许把异常变量原样甩到屏上`() {
        // 2026-10-20 之前的两处：`"详情加载失败…：$error"`、`"网络收藏加载失败：" + state.networkError`。
        // 用户读不懂异常串，而且它会把内部路径/类名漏出去。
        val hits = visibleLines().filter { (_, _, line) ->
            (line.contains("text = \"") || line.contains("Text(\"") || line.contains("Toast.makeText(")) &&
                (Regex("\\$\\{?error\\b").containsMatchIn(line) || line.contains("networkError") ||
                    Regex("\\$\\{?e\\.message").containsMatchIn(line))
        }
        assertEquals(
            "失败态不许把异常变量甩进 UI（要写人话）：\n" +
                hits.joinToString("\n") { "${it.first}:${it.second} ${it.third}" },
            0,
            hits.size,
        )
    }

    @Test
    fun `失败态标题不许一个模板套到底`() {
        // 曾经七处全部是「XX加载失败」。人说话不会这样 —— 每处该说自己的事。
        val hits = visibleLines().filter { (_, _, line) ->
            // 中文 + "加载失败"：用字符区间写，Kotlin 的字符串里不认 `\uXXXX` 那段转义。
            Regex("[\\u4e00-\\u9fa5]{0,6}加载失败").containsMatchIn(line) &&
                (line.contains("title = \"") || line.contains("text = \""))
        }
        assertTrue(
            "「XX加载失败」这一族不许超过两处（要各说各的）：\n" +
                hits.joinToString("\n") { "${it.first}:${it.second} ${it.third}" },
            hits.size <= 2,
        )
    }

    @Test
    fun `重试这词不许一个词用到底`() {
        // 曾经 17 处全是「重试」。各场景该有自己的说法（再来一次 / 再搜一次 / 重新加载…）。
        val hits = visibleLines().filter { (_, _, line) ->
            (line.contains("actionText = \"重试\"") || line.contains("text = \"重试\"") ||
                line.contains("Text(\"重试\""))
        }
        assertTrue(
            "「重试」不许超过三处（其余按场景换词）：\n" +
                hits.joinToString("\n") { "${it.first}:${it.second} ${it.third}" },
            hits.size <= 3,
        )
    }

    @Test
    fun `用户可见文案不许是半截话`() {
        // 2026-10-20 前最长的一条：`"点击此处如果登录已过期"`（明显没写完）。
        // 判据：以「如果 / 当 / 点击此处」**结尾**，或者以「如果」结尾却没有后半句。
        val hits = visibleLines().filter { (_, _, line) ->
            Regex("\"[^\"]*(如果|点击此处)\"\\s*[,)]").containsMatchIn(line)
        }
        assertEquals(
            "这些是半截话（以「如果/点击此处」结尾却没下文）：\n" +
                hits.joinToString("\n") { "${it.first}:${it.second} ${it.third}" },
            0,
            hits.size,
        )
    }
}
