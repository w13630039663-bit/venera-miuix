package com.venera.desktop

import com.venera.desktop.gallery.ui.DesktopSourceTree
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 批 82.9 · 打包运行时那串模块表不许漏掉读库要用的那一枚（2026-10-10）。
 *
 * ## 为什么桌面判据里会出现构建脚本
 *
 * 交付包第一次跑到"阅读历史"那一屏时，屏上是空态而盘上那棵库里有三行，成因只在 stdout：
 *
 * ```
 * D_异常 线程=DefaultDispatcher-worker-1 次=1 因=NoClassDefFoundError: java/sql/SQLException 链=1 栈=DesktopDatabasePorts.kt:200
 * ```
 *
 * `desktop/build.gradle.kts` 里那串 `modules(...)` 是 jlink 的模块清单，而 **sqlite-jdbc 是自动模块**：
 * 它对 `java.sql` 的依赖 jlink 推不出来 ⇒ 打出来的运行时里没有 `java.sql.SQLException`，
 * 于是"读库"这一条路在**包里**当场死，而**开发跑**用的是全 JDK ⇒ 判据全绿、dev 起窗也对，只有交付物是坏的。
 * 同一族的第一课是 `sun.misc.Unsafe`（缺 `jdk.unsupported` 时 GraalJS 启动即打死，
 * 读数见 `docs/rounds/windows-r1f-s0-spike-2026-10-02.md` §七），那颗当时也是靠打包跑才露出来。
 *
 * ## 这把尺子钉的是什么
 *
 * 不是"文件里出现过 `java.sql` 这四个字"（注释里写一句就恒绿），而是**那一行 `modules(` 里**带着它，
 * 并且原来那两枚一颗都不许掉 —— 少一枚就是换一种崩法。
 */
class DesktopRuntimeModulesTest {

    private val gradle: String by lazy {
        File(DesktopSourceTree.repoRoot, "desktop/build.gradle.kts").readText()
    }

    /** 那一行 `modules(`；跳过注释行，免得口径注释自己把判式喂饱。 */
    private fun modulesLine(): String {
        val line = gradle.lines().firstOrNull { !it.trimStart().startsWith("//") && it.contains("modules(") }
        return requireNotNull(line) { "构建脚本里找不到那行 modules(…) —— 打包配置搬家了，这把尺子要跟着改判" }
    }

    @Test
    fun 运行时模块表带着读库要用的那一枚() {
        val line = modulesLine()
        assertTrue("jlink 运行时缺 java.sql ⇒ 包里读库当场 NoClassDefFoundError（dev 跑是全 JDK，抓不到）：$line",
            line.contains("\"java.sql\""))
    }

    @Test
    fun 原有那两枚一颗都不许掉() {
        // 少 `jdk.unsupported` = GraalJS 启动即死；少 `jdk.accessibility` = 读屏软件全哑。
        val line = modulesLine()
        assertTrue("不许顺手把 jdk.unsupported 换掉（sun.misc.Unsafe 那一课）：$line", line.contains("\"jdk.unsupported\""))
        assertTrue("不许顺手把 jdk.accessibility 换掉（Java Access Bridge）：$line", line.contains("\"jdk.accessibility\""))
    }

    @Test
    fun 那一行确实住在打包配置那块里() {
        // 钉顺序而不是"文件里有这两串"：`modules(` 若跑到 nativeDistributions 之外就不再生效。
        val head = gradle.indexOf("nativeDistributions")
        val modules = gradle.indexOf(modulesLine().trim())
        assertTrue("找不到 nativeDistributions 那一块", head >= 0)
        assertTrue("那行 modules(…) 不在 nativeDistributions 之后（= 不在那块里，不生效）", modules > head)
    }
}
