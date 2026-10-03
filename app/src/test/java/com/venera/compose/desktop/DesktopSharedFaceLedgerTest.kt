package com.venera.compose.desktop

import com.venera.compose.testsupport.DesktopFace
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 桌面**共享面**的账本：`:app` 借给 `:desktop` 的那几颗目录里，"哪些文件上得了桌面"必须是
 * 一份**对得上的账**，而不是 gradle 里写着、没人核过。
 *
 * ## 为什么 `:app` 那几套边界守卫管不到这件事
 *
 * `BusinessApiBoundaryTest` / `DebtReadinessTest` / `LayeringEdgeTest` 扫的是 `app/src/main`
 * 的**业务穿透**；而"一颗文件能不能进桌面编译面"是另一件事，`desktop/` 那边今天零判据。
 * 最坏的两个形态编译器都只说一半：
 * - 新增一颗带 `data.prefs` 的文件而忘了排除 ⇒ `:desktop:compileKotlin` 炸，但读数是一句
 *   `Unresolved reference`，说不出"这颗为什么上不去"；
 * - 那颗已经脱离 Android 了、排除行还挂着 ⇒ **一声不响**。清单只涨不消，最后没人敢碰。
 * 所以本条按"好消息也必须有人显式确认"的纪律**双向**钉（口径抄 `BusinessApiBoundaryTest` 的 E 条）。
 *
 * ## 判据是**禁引集合**，不是 `^import android`
 *
 * 反例是我自己量出来的第一遍：`GalleryImageLoader.kt` 只 import 了一句 `android.content.Context`，
 * 看起来"换个平台件就能上桌面"，而真正把它钉死的是它间接吃的 `data.prefs` + `data.network`
 * （`VeneraImageFetcher` / `ImageFetchCallFactory`）。只看 android import 会**一边**把它误判成
 * "该进却没进"、**一边**对这类间接绑定视而不见。
 */
class DesktopSharedFaceLedgerTest {

    @Test
    fun `禁引文件与排除清单必须双向对得上`() {
        val all = DesktopFace.candidateFiles
        val offenders = all.filter { f -> FORBIDDEN.any { it.containsMatchIn(f.readText()) } }.map { it.name }.toSet()
        val names = all.map { it.name }.toSet()
        val excludes = DesktopFace.excludedNames

        val missing = (offenders - excludes).sorted()
        val stale = (excludes - offenders).filter { it in names }.sorted()
        val dead = (excludes - names).sorted()

        assertEquals(
            "① 有禁引却没点名排除（桌面编不过，而编译器说不清成因）：$missing\n" +
                "② 已无禁引成因却还排着（该摘行，摘了才算真共享）：$stale\n" +
                "③ 排除清单里的名字在 srcDir 下不存在（改名或删掉了没人摘行）：$dead",
            emptyList<String>(),
            missing + stale + dead,
        )
    }

    /**
     * 每颗被排出的文件都要说得出**被哪一族禁引挡住**。
     *
     * 与上面第②条看着重复，钉的是两种不同的漂：②管"禁引没了、行还挂着"，这条管
     * "有人往禁引集合里加/减一族"—— 那会让整张清单的口径一夜之间换个意思，
     * 而 `:desktop:compileKotlin` 照样绿（少一族判据不报错，只会让该排的没排上）。
     */
    @Test
    fun `每颗被排出的文件都要给得出一条禁引成因`() {
        val byName = DesktopFace.candidateFiles.groupBy { it.name }
        val unexplained = DesktopFace.excludeEntries().mapNotNull { entry ->
            val file = byName[entry.name]?.firstOrNull()
                ?: return@mapNotNull "${entry.name}（gradle 第 ${entry.lineNo} 行点了这颗，srcDir 里没有）"
            if (FORBIDDEN.none { it.containsMatchIn(file.readText()) }) {
                "${DesktopFace.relOf(file)}（gradle 第 ${entry.lineNo} 行）"
            } else {
                null
            }
        }
        assertTrue("这些排除项给不出一条禁引成因：$unexplained", unexplained.isEmpty())
    }

    private companion object {
        /**
         * 六族"上了桌面就得重写第二份"的绑定。
         *
         * `androidx.` 在这里算禁引（那是 AndroidX 运行时，共享面被它绑住就等于桌面要装一套
         * Android 运行时）；而**桌面自己的** UI 代码用 `androidx.compose.*` 是合法的 ——
         * 那条口径由 [DesktopNoAndroidImportTest] 按另一个集合单独管，两边不许并成一个。
         */
        val FORBIDDEN = listOf(
            Regex("^import android\\.", RegexOption.MULTILINE),
            Regex("^import androidx\\.", RegexOption.MULTILINE),
            Regex("^import coil", RegexOption.MULTILINE),
            Regex("^import com\\.venera\\.compose\\.data\\.prefs\\.", RegexOption.MULTILINE),
            Regex("^import com\\.venera\\.compose\\.data\\.network\\.", RegexOption.MULTILINE),
            Regex("^import com\\.venera\\.compose\\.[a-z0-9_.]*\\.android\\.", RegexOption.MULTILINE),
        )
    }
}
