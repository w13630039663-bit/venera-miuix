package com.venera.compose.gallery

import com.venera.compose.testsupport.RepoSources
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 首页 chrome 那一行的**材质与层级**（2026-10-17 第二十五轮，方案 B，用户拍板「先按方案b」）。
 *
 * 起点是评审第四节那三版 CSS 复刻挑中的 B：「一浮一沉 + 一个焦点色块」——
 * 搜索条浮成主角、站源分段器沉成底、图搜收进一枚粉圆钮。
 *
 * ## 这一刀动的是"材质"，所以尺子钉的是**形状**不是颜色值
 *
 * CSS 里写死的是 `#FFFFFF/#FDF6F6`、`#FFD2D8/#FFB6C2` 那一对 —— 那些值只对那一套粉色板成立，
 * 动态取色与深色档下会整片脱节。所以代码里两端一律从语义槽 `lerp` 插值，
 * 于是"渐变端点不许再出现字面色"这条成了本轮最该被钉住的那一条。
 *
 * 三条不变量（各有真凶，不是风格偏好）：
 *  1. **渐变端点不许写死 hex** —— 否则换壁纸色就脱节（与 `TokenBudgetFrozenTest` 同源纪律，
 *     但那条只拦 `Color(0x`，拦不住 `lerp("#FFF…", …)` 这种从别处搬进来的字面串）。
 *  2. **玻璃档整条 no-op** —— 面材质挂到 `veneraGlassSurface` **外面**会铺一层不透明 brush，
 *     把刚采到的模糊盖死，玻璃当场退化成平涂（评审第四节点出的坑②）。
 *  3. **反编码字色** —— 站源未选中那格此前吃 `textPrimary`（浅色实测 #67262A，9.0:1），
 *     比选中格的 `onPrimaryContainer` 还艳 ⇒ 第一眼会以为 Pixiv 才是当前站。
 *
 * 另有两条配套：`Surface` 的底色必须**两档都透明**（否则渐变被它压在下面），
 * 以及外框不许自己铺面材质（它带 `padding`、没有圆角，画不出圆角顶）。
 */
class GalleryHomeChromeMaterialTest {

    private val uiDir = "com/venera/compose"
    private val chromeName = "GalleryHomeChrome.kt"
    private val pixivHomeName = "GalleryPixivHome.kt"
    private val sheenName = "VeneraFaceSheen.kt"

    private fun file(dir: String, name: String): List<String> =
        RepoSources.kotlinFiles(dir).first { it.name == name }.readLines()

    private val chrome = file("$uiDir/gallery/ui", chromeName)
    private val pixivHome = file("$uiDir/gallery/ui", pixivHomeName)
    private val sheen = file("$uiDir/components/venera", sheenName)

    /** 只看代码行 —— 头注里点名 CSS 的那对色值是**必要**的，记述不该被判成违例。 */
    private fun code(of: List<String>): String =
        of.filter {
            val t = it.trim()
            !(t.startsWith("//") || t.startsWith("*") || t.startsWith("/*"))
        }.joinToString("\n")

    private fun codeHits(of: List<String>, needle: String): List<Int> =
        of.indices.filter { of[it].contains(needle) }.filter { n ->
            val t = of[n].trim()
            !(t.startsWith("//") || t.startsWith("*") || t.startsWith("/*"))
        }.map { it + 1 }

    @Test fun `量具自证三处读得到代码行`() {
        assertTrue(
            "GalleryHomeChrome.kt 读不到代码行，量具失效",
            code(chrome).contains("internal fun GalleryHomeChrome("),
        )
        assertTrue(
            "GalleryPixivHome.kt 读不到分段器，量具失效",
            code(pixivHome).contains("internal fun GalleryHomeDomainChips("),
        )
        assertTrue(
            "VeneraFaceSheen.kt 读不到修饰符本体，量具失效",
            code(sheen).contains("fun Modifier.veneraFaceSheen("),
        )
    }

    @Test fun `渐变端点不许写死十六进制`() {
        // 只扫**调用点**那三个文件里的面材质入参；共用件自己那两个色是参数不是字面量。
        val offenders = mutableListOf<String>()
        listOf(chrome to chromeName, pixivHome to pixivHomeName).forEach { (lines, name) ->
            codeHits(lines, "veneraFaceSheen(").forEach { line ->
                // 从这一行往下读到收尾的 `)` —— 入参可能折行（我们自己的写法就是折的）。
                val window = lines.drop(line - 1).take(12).joinToString("\n")
                if (Regex("""0x[0-9A-Fa-f]{6,}|#[0-9A-Fa-f]{6}\b""").containsMatchIn(window)) {
                    offenders += "$name 第 $line 行"
                }
            }
        }
        assertTrue(
            "面材质两端必须是语义槽之间 `lerp` 出来的值，不是写死的十六进制 —— " +
                "CSS 里那对 `#FFFFFF/#FDF6F6` 只对那一套粉色板成立，动态取色与深色档下会整片脱节。" +
                "违例：\n" + offenders.joinToString("\n"),
            offenders.isEmpty(),
        )
        // 配偶：三处面材质都必须真的走 `tokens.color.*`（防止"删掉参数传个常量颜色"这种绕法）
        val calls = codeHits(chrome, "veneraFaceSheen(") + codeHits(pixivHome, "veneraFaceSheen(")
        assertEquals("方案 B 落了 3 处面材质：搜索条 / 站源轨道 / 图搜圆钮", 3, calls.size)
        listOf(chrome to chromeName, pixivHome to pixivHomeName).forEach { (lines, name) ->
            codeHits(lines, "veneraFaceSheen(").forEach { line ->
                val window = lines.drop(line - 1).take(12).joinToString("\n")
                assertTrue(
                    "$name 第 $line 行的面材质没有吃语义槽（`tokens.color.*`）—— " +
                        "窗口：\n$window",
                    window.contains("tokens.color."),
                )
            }
        }
    }

    @Test fun `玻璃档下不许在玻璃层外面再铺一层不透明面材质`() {
        // 坑②：不透明 brush 铺在 `veneraGlassSurface` 之外 = 把刚采到的模糊盖死。
        // 正确做法是面材质自己判档、玻璃档 no-op —— 钉在共用件里，三处调用点就都受益。
        val body = code(sheen)
        val glassCheck = body.indexOf("veneraGlassEnabled()")
        val brushStart = body.indexOf("Brush.verticalGradient")
        assertTrue(
            "量具找不到 `veneraGlassEnabled()` 的提前返回（$glassCheck）或渐变本体（$brushStart）",
            glassCheck >= 0 && brushStart > glassCheck,
        )
        assertTrue(
            "玻璃档的 return 必须在铺渐变**之前**，否则玻璃档会叠一层不透明 brush 把模糊盖死。" +
                "现在的顺序是：判档@$glassCheck → 渐变@$brushStart",
            glassCheck < brushStart,
        )
        // 调用侧配偶：站源轨道那处面材质必须排在 `veneraGlassSurface` **之后**（即在它之内）
        val seg = code(pixivHome)
        val segIdx = seg.indexOf("veneraGlassSurface(VeneraGlassRole.CHIP")
        val segSheen = seg.indexOf("veneraFaceSheen(")
        assertTrue(
            "站源轨道：面材质要挂在 `veneraGlassSurface` 之内（链上更靠后），否则玻璃档被盖死。" +
                "找到 CHIP 玻璃@$segIdx / 面材质@$segSheen",
            segIdx >= 0 && segSheen > segIdx,
        )
    }

    @Test fun `搜索条的底色两档都透明且面材质落在 Surface 之内`() {
        // 这一条是**层序**判据：miuix `Surface` 的 `background(color)` 在调用方 modifier **之内**
        // （`basic/Surface.kt:146`），而 Compose 外层先画 ⇒ 若 `Surface.color` 还铺实色，
        // 它就压在渐变**之上**，方案 B 白改。
        //
        // 配偶：面材质不能挂共享元素**外框**上 —— 外框带 `padding` 且没有圆角，
        // 画不出 CSS 里那个圆角顶。
        val seg = code(chrome)
        val colorLine = codeHits(chrome, "color = ").firstOrNull { n ->
            chrome[n - 1].contains("Surface") || chrome[n - 1].contains("Color.Transparent")
        }
        assertTrue("量具找不到 Surface 的 color 入参行", colorLine != null)
        assertEquals(
            "Surface 的底色必须两档都 `Color.Transparent`（面材质独家提供），" +
                "留一个 `if (veneraGlassEnabled())` 分支就会把渐变盖死。当前第 $colorLine 行是：" +
                chrome[colorLine!! - 1].trim(),
            "color = Color.Transparent,",
            chrome[colorLine - 1].trim(),
        )
        val surfaceAt = seg.indexOf("Surface(")
        val sheenAt = seg.indexOf("veneraFaceSheen(", surfaceAt)
        assertTrue(
            "面材质必须落在 Surface **之内**（$surfaceAt → $sheenAt），挂外框会两个后果：" +
                "被 Surface 底色盖死、且外框没有圆角裁",
            surfaceAt >= 0 && sheenAt > surfaceAt,
        )
        // 配偶：外框上不许自己铺一份（第二处调用点）
        assertEquals(
            "面材质只许在 Surface 之内出现一次；挂外框那一处要么删掉、要么改成不透明（都会出事）",
            2,
            codeHits(chrome, "veneraFaceSheen(").size,
        )
    }

    @Test fun `站源未选中那格不许吃 textPrimary`() {
        // 反编码：切换器里两格的差别是"选没选中"，不是"重不重要"。
        // 未选中吃 `textPrimary`（浅色实测 #67262A，9.0:1）比选中的 `onPrimaryContainer` 还艳，
        // 第一眼会以为另一站才是当前站。
        val line = codeHits(pixivHome, "onPrimaryContainer else tokens.color.text")
        assertTrue("量具找不到分段器两格的颜色分支", line.isNotEmpty())
        // 只取 `color = …` 那一截：整行是 `color = if (isOn) … else …`，前缀由调用点自己写。
        val expr = pixivHome[line.first() - 1].trim()
            .removePrefix("color = ")
            .removePrefix("if (isOn) ")
            .removeSuffix(",")
        assertEquals(
            "未选中那格必须退到 `textSecondary`（不是 `textPrimary`）",
            "tokens.color.onPrimaryContainer else tokens.color.textSecondary",
            expr,
        )
    }

    @Test fun `图搜那一枚收进粉圆钮且是这一行唯一的彩色焦点`() {
        // 方案 B 的第三条。裸灰图标与左边 🔍 同档 ⇒ 两个动作在屏上没有主次。
        val body = code(chrome)
        val btn = body.indexOf("Icons.Outlined.ImageSearch")
        assertTrue("量具找不到图搜图标", btn >= 0)
        // 窗口取整颗按钮：从 `onOpenReverse()`（它只出现这一次）之后到图标之前。
        // ⚠️ 不能只取图标前 900 字符 —— 面材质入参是**折行**的（4 行），窗口一窄就切掉了
        // `.clip(CircleShape)` 那几行，读起来像"形状没挂"（本仓踩过：判据红在窗口长度上、
        // 而代码是对的）。
        val start = body.lastIndexOf("onOpenReverse()", btn)
        assertTrue("量具找不到图搜按钮的点击点", start >= 0)
        // 往上再扩到那枚 Box 的 `modifier =`（它只出现三次：外框 / 分段器轨道 / 图搜钮）
        val boxAt = body.lastIndexOf("modifier = ", start)
        val window = body.substring(boxAt, btn + 200)
        assertTrue(
            "图搜圆钮要 `CircleShape`（方案 B 的形状），现在这一段是：\n$window",
            window.contains("CircleShape"),
        )
        assertTrue(
            "图搜圆钮的底色要吃粉色面（`primaryContainer` 插值），不能是裸灰 —— " +
                "这一格是整条 chrome 里唯一的彩色焦点",
            window.contains("tokens.color.primaryContainer"),
        )
        // 图标色：粉底上必须是容器对（`onPrimaryContainer`），不是中性灰。
        // ⚠️ 用 `codeHits`（行号）而不是 `body.indexOf`（字符下标）——
        // 两者混用会拿一个字符偏移去 `drop` 行列表，越界时抛的是
        // "Requested element count -1"，读起来像判据本身坏了（同一个坑见 MEMORY §0 末条）。
        val iconLine = codeHits(chrome, "Icons.Outlined.ImageSearch")
        assertTrue("量具找不到图搜图标所在行", iconLine.isNotEmpty())
        assertTrue(
            "图搜图标在粉底上要吃 `onPrimaryContainer`（深色档下这套配对才有对比度保证）",
            chrome.drop(iconLine.first() - 1).take(6).joinToString("\n").contains("onPrimaryContainer"),
        )
    }
}