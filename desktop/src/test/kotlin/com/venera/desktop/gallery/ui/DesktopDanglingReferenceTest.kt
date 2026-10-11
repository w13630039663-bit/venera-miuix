package com.venera.desktop.gallery.ui

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 桌面第十七批 批 17 · **注释里的引用必须落得了地**。
 *
 * ## 这一族为什么单独立一颗尺子
 *
 * 批 16 手工抓到两处注释在引用 `DesktopGalleryDomain.notWiredReason` —— 那个符号随第九批的坦白页
 * 一起删了，而两句注释还拿它当"别人已经在用"的依据。那种错靠人逐条读屏是数不全的：
 * 本文件把口径写成机械判式，一次列全违例。
 *
 * ## 口径（三条，缺一条就误红）
 *
 * 1. **只看注释行**，而"存不存在"只看**代码行** —— 注释不许自证。
 *    第一版扫描器把整份文件当依据，注入一颗悬空引用照样数出 0：那是恒假的尺子（手册 §0 坑 ⑲ 的近亲）。
 * 2. **文件名不算成员**：`GalleryInfoSheet.kt:444` 这种是"去那份文件看第几行"，
 *    按 `X.y` 判就把它读成"成员 kt 不存在"。豁免表按实际出现过的扩展名给。
 * 3. **只判"看着属于本仓"的属主**：属主命中源码里声明过的类型名，或以 `Desktop` / `Gallery` / `Venera`
 *    开头。其余（`FocusOwnerImpl.dispatchKeyEvent`、`Bitmap.makeFromEncoded` 这类库内的东西）桌面管不着，
 *    判了就是误红 —— 这一条就是 §45 说的"允许指向桌面之外"的白名单口径。
 *
 * 违例的两种形状都点名：属主不在（那颗件被删或改名了）与成员不在（那颗件还在、这一格没了）。
 */
class DesktopDanglingReferenceTest {

    private val repoRoot = DesktopSourceTree.repoRoot

    /** 代码行拼成的"存在性"底本：桌面 main + Android main + 两边的测试源（判据类名是注释里最常引用的对象）。 */
    private fun faceSources(): List<File> =
        DesktopSourceTree.desktopMainSources() + DesktopSourceTree.androidMainSources() +
            walkKt(File(repoRoot, "desktop/src/test")) + walkKt(File(repoRoot, "app/src/test"))

    /** 桌面 main 的全部 .kt（被扫的那一片）。 */
    private fun scannedSources(): List<File> = DesktopSourceTree.desktopMainSources()

    /**
     * 存在性底本。
     *
     * 口径：**产品代码（两边 main）全量 + 测试源里的声明行**。
     * 为什么不整份算测试源：这一颗判据自己的合成输入里就写着 `notWiredReason`、`askFirstNowhere`
     * 那种假名字，连着算进"存在"就把判据读瞎（第一版 ① 数出 0 处就是这么来的）。
     * 试过"剥掉字符串字面量"，但 KDoc 里的三引号正则会把配对错开、反而留下幸存者 ——
     * 所以换成形状口径：**一个名字只有在被声明或写在产品代码里时才算它存在**，
     * 只活在别人测试夹具字符串里的不算（那正是"判据在自证"的形状）。
     */
    private fun faceText(): String {
        val declaration = Regex("""^\s*(?:@\w+\s+)?(?:private |internal |public |open |abstract |sealed |data |value |override )*(?:class|interface|object|enum class|fun|val|var)\s+([A-Za-z_][A-Za-z0-9_]*)""")
        val parts = mutableListOf<String>()
        for (file in faceSources()) {
            val lines = DesktopSourceTree.codeLinesOf(file)
            parts += if (file.invariantSeparatorsPath.contains("/test/")) {
                lines.filter { line -> declaration.containsMatchIn(line) }
            } else {
                lines
            }
        }
        return parts.joinToString("\n")
    }

    /** 源码里声明过的类型名（从底本的声明行现取）。 */
    private fun declaredTypes(haystack: String): Set<String> {
        val re = Regex("""\b(?:class|interface|object|enum class)\s+([A-Z][A-Za-z0-9_]*)""")
        val out = mutableSetOf<String>()
        haystack.lines().forEach { line ->
            re.findAll(line).forEach { out += it.groupValues[1] }
        }
        return out
    }

    /**
     * 判式本体（合成输入也能直接喂它 —— 判据 ②③ 就是靠这一点证明它不是恒真/恒假）。
     *
     * @param commentLines 待扫的注释行
     * @param codeHaystack 存在性底本（只有代码行）
     * @param declared 源码里声明过的类型名
     */
    private fun dangling(
        commentLines: List<String>,
        codeHaystack: String,
        declared: Set<String>,
    ): List<String> {
        val ref = Regex("""\b([A-Z][A-Za-z0-9_]*)\.([A-Za-z_][A-Za-z0-9_]*)\b""")
        val fileExtensions = setOf("kt", "kts", "md", "html", "js", "json", "xml", "jar", "png", "log", "ps1", "cjs", "txt", "gradle")
        val violations = mutableListOf<String>()
        commentLines.forEach { line ->
            ref.findAll(line).forEach { match ->
                val owner = match.groupValues[1]
                val member = match.groupValues[2]
                if (member in fileExtensions) return@forEach
                // 泛指写法豁免：`VeneraPreferences.setGalleryXxx` 说的是"这一族写口"，不是某一颗成员名。
                if (member.contains("Xxx") || member.contains("xxx")) return@forEach
                if (owner !in declared && !owner.startsWith("Desktop") && !owner.startsWith("Gallery") && !owner.startsWith("Venera")) {
                    return@forEach
                }
                val ownerPresent = Regex("\\b$owner\\b").containsMatchIn(codeHaystack)
                val memberPresent = Regex("\\b$member\\b").containsMatchIn(codeHaystack)
                if (!ownerPresent || !memberPresent) {
                    violations += "$owner.$member" +
                        (if (ownerPresent) "" else "[属主不在]") +
                        (if (memberPresent) "" else "[成员不在]") +
                        " ← " + line.trim().take(70)
                }
            }
        }
        return violations
    }

    /** ① 真树扫一遍：今天必须零违例。 */
    @Test
    fun 桌面注释里的引用全部落得了地() {
        val haystack = faceText()
        val declared = declaredTypes(haystack)
        val comments = scannedSources().flatMap { file ->
            file.readText().lines().mapNotNull { line ->
                val t = line.trim()
                if (t.startsWith("//") || t.startsWith("*") || t.startsWith("/*")) "${file.name} $t" else null
            }
        }
        val found = dangling(comments, haystack, declared)
        assertEquals("有注释在引用源码里已经不存在的东西：" + found.joinToString(" ; "), 0, found.size)
    }

    /** ② 对照组（证明 ① 会红）：同一颗判式喂一段已知悬空的注释，必须数出两处。 */
    @Test
    fun 对照_悬空引用数得出来() {
        val haystack = faceText()
        val declared = declaredTypes(haystack)
        val found = dangling(
            listOf(
                "// 见 com.venera.desktop.gallery.ui.DesktopGalleryDomain.notWiredReason 那一格",
                "// 依据 GalleryRules.askFirstNowhere 这条口径",
            ),
            haystack,
            declared,
        )
        assertEquals("这台机器抓不到悬空引用 ⇒ 判据 ① 是恒假的空判式：" + found, 2, found.size)
        assertTrue("两种形状都要点名：" + found, found.all { it.contains("成员不在") || it.contains("属主不在") })
    }

    /** ③ 对照组（证明它不误红）：文件名与库内名字都不算违例。 */
    @Test
    fun 对照_文件名与库内名字不算违例() {
        val haystack = faceText()
        val declared = declaredTypes(haystack)
        val found = dangling(
            listOf(
                "// 实测在 GalleryInfoSheet.kt:444-447，稿 :340 的 .tb 与 .body 是兄弟节点",
                "// 无焦点那一路只往祖先找 KeyInput（`FocusOwnerImpl.dispatchKeyEventNoWhere`）",
                "// 见 DesktopStaleClaimsTest ⑬ 与 settings.gradle.kts",
            ),
            haystack,
            declared,
        )
        assertEquals("口径误伤了合法引用：" + found, 0, found.size)
    }

    /**
     * ④ 批 16 那两处引用与今天这一处 `GalleryRules.askFirst` 都不许回来。
     *
     * ① 已经能抓它们，这一条只是把"那两颗确实不在源码里"钉成读数：
     * 哪天 `askFirst` 真被加回来，① 会自动不红，而这条会红着提醒重新对口径。
     */
    @Test
    fun 被删掉的那两颗件确实是删掉了() {
        val haystack = faceText()
        listOf("notWiredReason", "askFirst").forEach { gone ->
            assertEquals("$gone 又回到源码里了（判据 ① 的口径要重新对）", 0, Regex("\\b$gone\\b").findAll(haystack).count())
        }
        // 取而代之的真实出处必须在：守卫取库是在**构造那一刻**，不在"首问规则时"。
        val guard = DesktopSourceTree.androidSource("security/guard/ContentGuardManager.kt")
        assertTrue("对照失效：`ContentGuardManager` 里那颗构造期建的 ruleStore 不见了", DesktopSourceTree.codeText(guard).contains("GuardRuleStore("))
    }

    private fun walkKt(root: File): List<File> =
        if (!root.isDirectory) emptyList() else root.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
}
