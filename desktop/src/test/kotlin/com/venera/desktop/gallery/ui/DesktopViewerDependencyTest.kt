package com.venera.desktop.gallery.ui

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 依赖探针。批 47 那枚「单测从不构造 ImageLoader」的教训反过来读就是：
 * 依赖**解析得到**不等于**编得动**，而这两条都不等于**屏上真能用**（那一条在图证那一轮）。
 *
 * 这一颗只钉三件事：坐标在构建脚本里（只许一处）、构建脚本自己是测试输入（防旧结果放行）、
 * 桌面没有偷偷换另一族缩放件。第三条是**防以后**的负向锚点，今天就该绿。
 */
class DesktopViewerDependencyTest {

    // 与既有那把同族尺子同一口径（`DesktopGalleryPreloadTest` ⑦ 就是这么取的）：
    // desktopSourceOf 只到 desktop/src/main/kotlin/… 底下，构建脚本要用 repoRoot 拼。
    private val build get() = java.io.File(DesktopSourceTree.repoRoot, "desktop/build.gradle.kts")

    @Test
    fun `构建脚本带那枚坐标`() {
        assertTrue(
            "构建脚本里没有 net.engawapg.lib:zoomable —— 探针没做或依赖没落地",
            DesktopSourceTree.codeOccurrences(build, "\"net.engawapg.lib:zoomable:2.16.0\"") == 1,
        )
    }

    @Test
    fun `构建脚本仍是这颗判据的输入`() {
        // 这条钉的是**既有事实**（第十批就落了 `inputs.file(project.buildFile)`）：它现在就该绿，
        // 作用是防以后被删 —— 删掉之后改依赖会被判成 UP-TO-DATE 而拿旧结果放行。
        assertTrue(
            "构建脚本没把自身声明成测试输入，改依赖会被 UP-TO-DATE 放行旧结论",
            DesktopSourceTree.codeOccurrences(build, "inputs.file(project.buildFile)") >= 1,
        )
    }

    @Test
    fun `桌面没有偷偷改用另一族缩放件`() {
        val forbidden = listOf("me.saket.telephoto", "io.github.panpf.zoomimage")
        val hits = DesktopSourceTree.desktopMainSources().flatMap { f ->
            DesktopSourceTree.codeLinesOf(f).mapIndexedNotNull { n, l ->
                if (forbidden.any { l.contains(it) }) "${f.name}:${n + 1} $l" else null
            }
        }
        assertTrue("桌面引了别的缩放族：$hits", hits.isEmpty())
    }
}
