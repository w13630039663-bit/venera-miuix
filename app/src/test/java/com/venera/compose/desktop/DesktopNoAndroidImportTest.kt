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
        // 约束原文：「严禁两端 UI 互相耦合」。
        //
        // ⚠️ 2026-10-20 口径改判（写法 A，用户裁决「之前做的平行线就不要了，按照大屏端复用」）：
        // 本条的**意图没变** —— 禁的是"桌面去依赖**某一端专属**的 UI"（`feature/` 与
        // `gallery/ui/` 那两棵属于 Android：38 处 `LocalContext`、18 处 `Toast`、
        // `AndroidViewModel` / `Intent` / Activity 承载，桌面侧根本没有那套宿主）。
        // 但 `components/` 与 `ui/tokens/` 这一批**已经改成两端共用同一份源码**了
        // （`desktop/build.gradle.kts` 的 srcDir + 本轮把最后 5 颗的平台调用抽进了
        // `UxPlatformServices` 接口）。它们不再是"某一端的 UI"，而是**共享基建层** ——
        // 再禁就等于禁止复用本身，与用户这次的裁决直接冲突。
        //
        // 判据的收紧面说清楚，别让它变成"什么都放行"：
        //  - 仍禁：`feature` / `gallery.ui` / `reader` / `MainActivity`（真·端专属）；
        //  - 新禁：`com.venera.compose.platform`（Android 平台实现，桌面不许碰它）；
        //  - 放行：`components` / `ui.tokens` / `source.model`（共享面，两端编译同一份）。
        val hits = (DesktopFace.sharedFiles() + DesktopFace.ownFiles()).flatMap { file ->
            val rel = DesktopFace.relOf(file)
            file.readLines().mapIndexedNotNull { index, line ->
                val t = line.trim()
                if (!t.startsWith("import ")) return@mapIndexedNotNull null
                val m = UI_PACKAGE.find(t)
                if (m == null) {
                    // `feature` 那一族按**文件级**判：共享白名单外的仍禁。
                    val fm = Regex("""com\.venera\.compose\.feature\.(\w+)""").find(t)
                    val sym = fm?.groupValues?.get(1)
                    return@mapIndexedNotNull if (sym == null || sym in sharedFeatureStems) {
                        null
                    } else {
                        "$rel:${index + 1}  $t  ← com.venera.compose.feature.$sym（不在共享面里）"
                    }
                }
                "$rel:${index + 1}  $t  ← ${m.value}"
            }
        }
        assertTrue("桌面编译面 import 了某一端专属的 UI 包：\n$hits", hits.isEmpty())
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

        /**
         * 端**专属**的 UI 面（2026-10-20 改判后）。
         *
         * `feature/` `gallery/ui/` `reader/` `MainActivity` 仍然禁 —— 那几棵靠 Android 宿主
         * （`LocalContext` / `Toast` / `Intent` / `AndroidViewModel` / Activity 承载）活着，
         * 桌面侧没有对应物。
         *
         * **`components/` 与 `ui/tokens/` 已从这里移除**：本轮（写法 A）把它们改成了两端共用，
         * 理由是用户裁决「按照大屏端复用」——那是共享基建层，不是"某一端的 UI"。
         * 移除不是放宽，是口径跟着事实改：那两颗目录现在确实两端编译同一份源码。
         */
        /**
         * 端专属 UI 的包前缀。
         *
         * **`feature` 不再整包禁**（2026-10-20）：`feature/HomeHomeWidgets.kt` 那一批
         * 首页展示零件已经是两端编译同一份源码，整包禁就等于禁止复用本身 —— 与用户
         * 「按照大屏端复用」的裁决直接冲突。
         *
         * 现在 `feature` 的判据是**文件级**的（见 [sharedFeatureStems]）：从
         * `desktop/build.gradle.kts` 把 `feature` 的排除清单读出来，没被排除的就是共享的。
         * 下一轮谁再共享一颗不必回来改这里；谁把某颗踢出共享，判据自动跟上。
         *
         * 这不是放宽：`gallery.ui` / `reader` / `MainActivity` / `platform` 仍然整包禁。
         */
        val UI_PACKAGE = Regex(
            """com\.venera\.compose\.(gallery\.ui|reader|MainActivity|platform)\b""",
        )

        /** `com.venera.compose.feature.X` 里真正共享的名字（= 没被 gradle 排除的那些）。 */
        val sharedFeatureStems: Set<String> by lazy {
            // 判据要的是"这个名字定义在哪颗文件里、那颗文件共享了吗"，
            // 不是"符号名等于文件名"（PillButton 是函数名，它住在 HomeHomeWidgets.kt）。
            // 所以：对每颗**未被排除**的 feature 文件，把它的顶层声明名全部收进来。
            val excluded = DesktopFace.excludedNames
            DesktopFace.candidateFiles
                .filter { it.parentFile?.name == "feature" || it.parentFile?.parentFile?.name == "feature" }
                .filterNot { it.name in excluded }
                .flatMap { f ->
                    Regex("""(?m)^(?:internal |private |public )?(?:fun|val|class|object|interface|enum class) (\w+)""")
                        .findAll(f.readText())
                        .map { it.groupValues[1] }
                        .toList()
                }
                .toSet()
        }
    }
}
