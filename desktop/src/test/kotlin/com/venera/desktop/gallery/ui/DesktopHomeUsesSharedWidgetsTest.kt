package com.venera.desktop.gallery.ui

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 钉住「桌面首页用的是安卓那一批零件」—— 2026-10-20 用户第二次抓到的那一颗。
 *
 * ## 两次越界的形状
 *
 * 1. 第一版：把**画廊图片墙**摆在首页位置（五个 tab 四个同形）→ 用户：
 *    「你这个首页怎么和安卓端的完全不一样？」
 * 2. 第二版（本条钉的这一颗）：改成 `DesktopNotWiredPane` 如实报未接 —— 诚实了，
 *    但首页**仍然是桌面自己画的**，一个零件都没复用。
 *
 * 现在的形状：`DesktopHomePane` 用 `feature/HomeHomeWidgets.kt`（与安卓**同一份源码**）
 * 摆出统计格 / 源状态 / 分区头 —— 结构是安卓的，数据口子是待接的。
 *
 * ## 判据
 *
 * 扫 `desktop/.../DesktopHomePane.kt` 的**代码行**（非注释）：
 *  - 必须 import 至少 4 个 `com.venera.compose.feature.*` 的共享零件；
 *  - 不许出现 `DesktopWallScaffold` / `VeneraCover` —— 那两面是画廊与封面墙的东西，
 *    出现在首页就又是「拿图片墙冒充首页」。
 */
class DesktopHomeUsesSharedWidgetsTest {

    private fun homePane(): File {
        val f = File(
            DesktopSourceTree.repoRoot,
            "desktop/src/main/kotlin/com/venera/desktop/DesktopHomePane.kt",
        )
        assertTrue("读不到桌面首页文件，判据失效（不是通过）：${f.path}", f.isFile)
        return f
    }

    /** 非注释的代码行。 */
    private fun codeLines(file: File): List<String> =
        file.readText().lines().filterNot { ln ->
            val s = ln.trim()
            s.startsWith("//") || s.startsWith("*") || s.startsWith("/*")
        }

    @Test
    fun `首页用的是安卓那一批共享零件 不是自己画的`() {
        val imports = codeLines(homePane()).filter { it.trim().startsWith("import com.venera.compose.feature.") }
        assertTrue(
            "桌面首页一个安卓共享零件都没 import —— 那就是自己画了一份。实际 import：" +
                codeLines(homePane()).filter { it.trim().startsWith("import ") }.joinToString("\n  "),
            imports.size >= 4,
        )
        // 这几颗是最能说明「统计格用的是同一把尺」的：少一颗都意味着有人重画了。
        val must = listOf("StatCell", "StatDivider")
        val missing = must.filter { sym -> imports.none { it.endsWith(".$sym") } }
        assertTrue("首页缺这几颗共享零件（多半是重画了）：$missing", missing.isEmpty())
    }

    @Test
    fun `首页不许再出现画廊与封面墙的零件`() {
        val bad = codeLines(homePane()).filter {
            it.contains("DesktopWallScaffold") || it.contains("VeneraCover(") || it.contains("GalleryWallPane")
        }
        assertTrue(
            "桌面首页里出现了图片墙的零件：$bad —— 那正是 2026-10-20 用户抓到的那一颗" +
                "（「你这个首页怎么和安卓端的完全不一样？」）",
            bad.isEmpty(),
        )
    }

    @Test
    fun `共享零件文件本身零 Android 导入`() {
        val f = File(
            DesktopSourceTree.repoRoot,
            "app/src/main/java/com/venera/compose/feature/HomeHomeWidgets.kt",
        )
        assertTrue("读不到 HomeHomeWidgets.kt：${f.path}", f.isFile)
        val offenders = codeLines(f).filter {
            it.trim().startsWith("import android.") ||
                it.contains("LocalContext") ||
                it.contains("viewModel(") ||
                it.contains("ComicSourceManager")
        }
        assertTrue(
            "HomeHomeWidgets.kt 又被平台耦合了（它就进不了桌面编译面了）：$offenders",
            offenders.isEmpty(),
        )
    }
}
