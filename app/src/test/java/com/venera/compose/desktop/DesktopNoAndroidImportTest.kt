package com.venera.compose.desktop

import com.venera.compose.testsupport.DesktopFace
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 桌面编译面（`:app` 借出去的那几颗 + 桌面自己的那些）里**一次都不许点名 Android SDK**。
 *
 * ## 为什么不按 `^import android` 扫
 *
 * 因为 import 只是其中一种写法，而漏掉的那种我今天真的踩到了：
 * `GalleryTagCategories.kt` 里那句日志是**内联 FQN** `android.util.Log.i(…)`，整颗文件
 * 一句 `import android.*` 都没有 —— 于是"脱 Context"那批改动逐颗查过 import 之后仍给它留了过去，
 * 直到 `:desktop:compileKotlin` 报 `Unresolved reference 'android'` 才被抓回来。
 * 与既有那条"`getInstance` 跨行内联链单行判式扫不到"是同一族的坑：**按形状扫就要把形状扫全**。
 *
 * ## 为什么这条用例住在 `:app` 的测试里
 *
 * 它要钉的失败形态之一就是"`:desktop:compileKotlin` 炸了"，而那次跑之后 `:desktop:test`
 * 根本不会执行 —— 判据只在最该说话的时候缺席。放在 `:app` 侧，桌面编不过时它照样红，
 * 并且直接打印是**哪个文件哪一行**（编译器只给一句 `Unresolved reference`）。
 *
 * ⚠️ `androidx.compose.*` 不在禁引集合里：那是 Compose Multiplatform 在桌面上也用的包名，
 * 桌面自己的 UI 代码合法。这里禁的是 `android.*`（Android SDK 本体）。
 * 而**共享面**那颗目录里连 `androidx.` 都不许出现 —— 那是另一条账，见
 * [DesktopSharedFaceLedgerTest] 的 `FORBIDDEN`。
 */
class DesktopNoAndroidImportTest {

    @Test
    fun `桌面编译面不许出现 Android SDK 的点名`() {
        val hits = (DesktopFace.sharedFiles() + DesktopFace.ownFiles()).flatMap { file ->
            val rel = DesktopFace.relOf(file)
            file.readLines().mapIndexedNotNull { index, line ->
                // 注释行放过：文档里提一句"这颗从前吃 Context"是要的，不是越界
                if (line.isCommentLine()) return@mapIndexedNotNull null
                val m = ANDROID_SDK.find(line) ?: return@mapIndexedNotNull null
                "$rel:${index + 1}  ${line.trim()}  ← ${m.value}"
            }
        }
        assertTrue("桌面编译面里点名了 Android SDK：\n$hits", hits.isEmpty())
    }

    @Test
    fun `桌面编译面不许反向依赖任何一端的 UI`() {
        // 约束原文：「严禁两端 UI 互相耦合」。编译器今天也管得住（那些包不在 srcDir 里），
        // 这条的存在是为了让"越界"这件事有一个说人话的名字，而不是某天有人把某颗 UI 目录
        // 也 srcDir 进来时，整条口径静默消失。
        val hits = (DesktopFace.sharedFiles() + DesktopFace.ownFiles()).flatMap { file ->
            val rel = DesktopFace.relOf(file)
            file.readLines().mapIndexedNotNull { index, line ->
                val t = line.trim()
                if (!t.startsWith("import ")) return@mapIndexedNotNull null
                val m = UI_PACKAGE.find(t) ?: return@mapIndexedNotNull null
                "$rel:${index + 1}  $t  ← ${m.value}"
            }
        }
        assertTrue("桌面编译面 import 了某一端的 UI 包：\n$hits", hits.isEmpty())
    }

    /**
     * 注释行判式要同时认三种起法：双斜杠、块注释正文那个星号，以及**整行写完的块注释**。
     * 最后那种今天真命中过一次：`SqlDatabase.kt:74` 那句"与 android.database.Cursor 对齐"
     * 是一整行写完的文档注释，只按星号起头判就会把文档里的一句话当成越界。
     */
    private fun String.isCommentLine(): Boolean {
        val t = trimStart()
        // 第三个分支是"整行写完的块注释"的起手，用字符比较写而不是字面量，是为了不在 KDoc 里
        // 造出那两个字符连写（块注释可嵌套，KDoc 里写它就等于把这颗文件的注释吞掉半截）。
        return t.startsWith("//") || t.startsWith("*") || (t.length > 1 && t[0] == '/' && t[1] == '*')
    }

    private companion object {
        /** Android SDK 的顶层包（`java.*` / `kotlin.*` / `okhttp3.*` 不在此列，桌面有对应物）。 */
        val ANDROID_SDK = Regex(
            """\bandroid\.(app|content|graphics|media|net|os|provider|text|util|view|webkit|database|widget)\b""",
        )

        /** 两端 UI 面：`feature/` `gallery/ui/` `reader/` `components/` 与漫画侧的 Activity。 */
        val UI_PACKAGE = Regex(
            """com\.venera\.compose\.(feature|gallery\.ui|reader|components|MainActivity)\b""",
        )
    }
}
