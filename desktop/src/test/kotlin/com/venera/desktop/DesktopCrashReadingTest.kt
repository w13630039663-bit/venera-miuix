package com.venera.desktop

import com.venera.desktop.gallery.ui.DesktopSourceTree
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 桌面崩了之后**念得出什么**（批 53 · 基础框架那一层）。
 *
 * ## 这一格为什么要单独做
 *
 * 本仓已经吃过两次"崩了但读不出来"：批 47 那颗 `ImageLoader` 缺 dispatcher 的崩溃，
 * 屏上是一枚**空白 Error 框**（Compose 内容不进 UIA 树），成因只在 stdout 那一大段 stack 里；
 * 手册 §0.1 也记着"崩溃的读数不在屏上"。而取证那一跑通常是几十行 `D_` 读数里挑，
 * 一段裸 stack 既没归属（哪颗线程、第几笔）、也没法 grep。
 *
 * ## 两条纪律
 *
 * 1. **一行**：换行与制表全压掉，长度有上限 —— 读数是要被 `grep "^D_异常"` 一眼挑出来的东西。
 * 2. **凭据不进读数**：崩溃消息里最常出现的正是那条**带 query 的 URL**（反搜键、Gelbooru 的
 *    `api_key`/`user_id` 都走 query），所以剔除是这颗件的一部分而不是调用方的事。
 *    判据一律喂两种输入：带键的那条与不带键的那条 —— 只喂一种的话，恒不命中也跑得绿。
 */
class DesktopCrashReadingTest {

    /** 造一条带 cause 链的异常：外层 `boom 外层`，cause 是 `boom 内层`。 */
    private fun chain(depth: Int, message: String = "boom 外层"): Throwable {
        var error: Throwable = IllegalStateException("链尾")
        repeat(depth) { 层 ->
            error = RuntimeException(if (层 == depth - 1) message else "中间层$层", error)
        }
        return error
    }

    // ① 四枚归属都要在：哪颗线程、第几笔、什么因、链多深；外加顶帧。
    //    "第几笔"不是装饰：EDT 上同一颗崩会连着来几十次，没有那枚计数就分不清"一次大崩"与"每分钟一崩"。
    @Test
    fun `读数把归属四枚都念出来`() {
        val line = desktopCrashReading("AWT-EventQueue-0", chain(2), sequence = 3)
        println("CRP_$line")
        assertTrue("少了线程名：$line", line.contains("线程=AWT-EventQueue-0"))
        assertTrue("少了第几笔：$line", line.contains("次=3"))
        assertTrue("少了那颗异常的类型：$line", line.contains("因=RuntimeException"))
        assertTrue("少了消息本体：$line", line.contains("boom 外层"))
        assertTrue("少了链深：$line", line.contains("链=2"))
        assertTrue("顶帧没念出来：$line", Regex("栈=[A-Za-z0-9.<>]+\\.kt:\\d+").containsMatchIn(line))
    }

    // ② 多行消息压成一行：stack 里的换行若原样带出，那行读数就断在半路，grep 只拿到前半截。
    @Test
    fun `多行消息压成一行`() {
        val line = desktopCrashReading("pool-1", RuntimeException("第一行\n第二行\t带制表"), sequence = 1)
        assertFalse("读数里还有换行：$line", line.contains('\n'))
        assertFalse("读数里还有制表：$line", line.contains('\t'))
        assertTrue("两行内容都得留着（压行不是截断）：$line", line.contains("第一行") && line.contains("第二行"))
    }

    // ③ 长度有上限：站方那一路的错误页常把整段 HTML 塞进 message，一行读数不能被它撑成一篇。
    @Test
    fun `超长消息截断而短消息不截`() {
        val long = desktopCrashReading("t", RuntimeException("x".repeat(400)), sequence = 1)
        assertTrue("四十字的短消息也被截了：$long", long.length < 600)
        assertTrue("截断要说出来（不说就是读数在撒谎）：$long", long.contains("截断"))
        val short = desktopCrashReading("t", RuntimeException("刚好一句"), sequence = 1)
        assertFalse("短消息被标成截断：$short", short.contains("截断"))
    }

    // ④ 凭据不进读数：这条是**安全**判据，不是观感。喂两种输入 —— 带键的那条与不带键的那条，
    //    后者保证"剔除"不是把整条消息抹平（那也会让上面那条断言恒真）。
    @Test
    fun `崩溃消息里的键值不许进读数`() {
        val leaky = desktopCrashReading(
            "t",
            RuntimeException("取 https://saucenao.com/search.cgi?db=99&api_key=SEKR3Tabc 失败"),
            sequence = 1,
        )
        println("CRP_$leaky")
        assertFalse("反搜键进了读数：$leaky", leaky.contains("SEKR3Tabc"))
        assertTrue("键名要留着（要说得出是哪一枚被挡了）：$leaky", leaky.contains("api_key"))
        val gelbooru = desktopCrashReading(
            "t",
            RuntimeException("401 https://gelbooru.com/index.php?page=dapi&s=post&api_key=AAAbbbCCC&user_id=4242"),
            sequence = 1,
        )
        assertFalse("Gelbooru 键进了读数：$gelbooru", gelbooru.contains("AAAbbbCCC"))
        assertFalse("Gelbooru 用户号进了读数：$gelbooru", gelbooru.contains("4242"))
        // 对照组：不带键的那条**不许**被顺手抹掉 —— 否则"剔得干净"其实是"念得最少"。
        val clean = desktopCrashReading("t", RuntimeException("DiskCache 满了"), sequence = 1)
        assertTrue("没键的消息也被改写了（剔除做成了整句打码）：$clean", clean.contains("DiskCache 满了"))
    }

    // ⑤ 链深两档各念各的：无 cause 念 `链=0`，不许念成空或负。
    @Test
    fun `链深把两种输入都念对`() {
        assertTrue(
            "无 cause 也要念得出：${desktopCrashReading("t", IllegalStateException("光棍"), 1)}",
            desktopCrashReading("t", IllegalStateException("光棍"), 1).contains("链=0"),
        )
        assertTrue(
            "三层 cause 要念成 3",
            desktopCrashReading("t", chain(3, "三层底"), 1).contains("链=3"),
        )
    }

    // ⑥ 顶帧拿不到（合成栈、Native 帧）要念成一句人话，而不是 `栈=` 空着 —— 空着会被读成"读数件坏了"。
    @Test
    fun `拿不到顶帧也要念得出`() {
        val frameless = RuntimeException("没有栈")
        frameless.stackTrace = emptyArray()
        val line = desktopCrashReading("t", frameless, 1)
        println("CRP_$line")
        assertTrue("顶帧缺失时念的不是人话：$line", line.contains("栈=没有"))
    }

    // ⑦ 读数件自己**绝不许抛**：它就是为"已经崩了"那条路服务的，再崩一次等于把现场也带走。
    //    message 为 null 是那条路上最常见的第二颗雷。
    @Test
    fun `消息为空的异常也出得来一行`() {
        val line = desktopCrashReading("t", NullPointerException(), 1)
        assertTrue("空 message 那档没出一行：$line", line.startsWith("D_异常"))
        assertTrue("要念得出类型：$line", line.contains("NullPointerException"))
    }

    // ⑧ 接线：那颗 handler 只在应用入口装一次，且**装在任何开窗之前**
    //    （装在 `application {}` 之后就管不到启动阶段那一段，而批 47 那次崩的正是启动阶段）。
    @Test
    fun `入口装了那颗handler且只装一次`() {
        val entry = DesktopSourceTree.codeText(DesktopSourceTree.desktopSourceOf("VeneraDesktop.kt"))
        val lines = DesktopSourceTree.codeLinesOf(DesktopSourceTree.desktopSourceOf("VeneraDesktop.kt"))
        val installed = lines.indexOfFirst { it.contains("installDesktopCrashHandler") }
        val windowed = lines.indexOfFirst { it.contains("application {") }
        assertEquals("入口没装那颗 handler（或装了多处）", 1, lines.count { it.contains("installDesktopCrashHandler") })
        assertTrue("装 handler 的那一行必须在开窗**之前**（找不到两处之一 ⇒ 判式要看的东西变了）：$installed / $windowed",
            installed in 0 until windowed)
        // 全树只许这一处 `setDefaultUncaughtExceptionHandler`：两处各装一份就是后装的替掉先装的。
        // ⚠️ 锚点不带尾括号：那一行写的是 `Thread.setDefaultUncaughtExceptionHandler { thread, error ->`
        //    （Kotlin 尾随 lambda 省掉了括号），带括号那把尺子数出 0 颗 —— 真形状拼错，坑 ① 的近亲。
        val everywhere = DesktopSourceTree.desktopMainSources()
            .sumOf { DesktopSourceTree.codeOccurrences(it, "Thread.setDefaultUncaughtExceptionHandler") }
        assertEquals("装 handler 的地方不止一处", 1, everywhere)
        assertFalse("整颗文件读空了（上面那几条会恒真）", entry.isBlank())
    }

    // ⑨ 取证种子那一跳全在（同一颗纪律第四次）：`-Pcrash=` 拼进 args、判读层认它、消费点真吃它。
    //    漏任何一环，"崩了念得出"这一格就只能在人手上验，而人手是禁的。
    @Test
    fun `崩一枚那枚种子三跳都在`() {
        val build = File(DesktopSourceTree.repoRoot, "desktop/build.gradle.kts").readText()
        assertTrue("构建脚本没把 -Pcrash 拼进 :desktop:app 的 args", build.contains("providers.gradleProperty(\"crash\")"))
        val flags = DesktopSourceTree.codeText(DesktopSourceTree.desktopSourceOf("ForensicFlags.kt"))
        assertTrue("判读层不认 --crash", flags.contains("--crash"))
        val entry = DesktopSourceTree.codeText(DesktopSourceTree.desktopSourceOf("VeneraDesktop.kt"))
        assertTrue("消费点没吃它", entry.contains("ForensicFlags.crash"))
    }
}
