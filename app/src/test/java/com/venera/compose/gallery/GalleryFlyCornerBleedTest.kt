package com.venera.compose.gallery

import com.venera.compose.testsupport.RepoSources
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 飞行体四角那层"卡片面色"（2026-10-16，用户报「改了之后，图片这个 hero 共享动画也会跟着
 * 这个按压动画下压的白色一起带进了图片的飞行动画」）。
 *
 * ## 定案（真机图证逐像素量的，不是推断）
 *
 * 把用户那张截图按行列采样：飞行体四角各有一块**近似白**（RGB ≈ 240/226/225）、半径
 * ≈ 32~37px 的圆角残块，位置正好落在封面圆的四角；而图与图之间没有边距（粉色像素直接顶到
 * 飞行体边缘）—— 所以不是"按压把卡缩小了、留了一圈背景"（那会是一整圈均匀的边），
 * 而是**这块像素本身四角就不是图**。
 *
 * 来源：`VeneraCover` 先按 `veneraCoverCornerRadius()`（= `shape.medium`）把封面裁成圆角，
 * 四角露出来的是**卡片自己的面色**（浅色档接近白）。飞行体截的又是"卡片里封面那一块的屏幕像素"
 * （跨 activity 没有共享图像 API，见 `GalleryFlyIn`），于是四角一起被截进来。
 * 而旧写法给飞行体裁的圆角是 `shape.large`（比 `shape.medium` **松**一档）⇒ 那四块原样放行。
 *
 * ## 这一颗守什么
 *
 * 判据只有一条：**谁画这块像素，就用封面自己那一档圆角裁**。所以：
 * - `VeneraCover` 与飞行体读的是**同一颗** `veneraCoverCornerRadius()`，不许各处 copy dp；
 * - 去程飞行体、垫帧、返回程落点三处都不许再用 `flyCornerRadius` 当"裁这块像素"的半径
 *   （它管的是"落到画面框时形状对得上"）。
 *
 * ⚠️ 2026-10-19 批 A：两趟绘制并成共享件之后，**去程那一支住 `GalleryEntryFlyBody.kt`、
 * 返回程那一支住 `GalleryExitFlyBody.kt`**。下面 ②③ 两条跟着搬到那两颗件上量 ——
 * 尺子跟着代码走，代码搬了而尺子留在原地，量具就成了替违例者打掩护的那一个。
 *
 * 负向对照（每一轮都当场验过会红）：
 * - 去程半径改回 `flyCornerRadius.toPx() * (width / end.width)` ⇒ 第 2 条红；
 * - `VeneraCover` 改回 `RoundedCornerShape(tokens.shape.medium)` ⇒ 第 1 条红；
 * - 垫帧去掉那圈 `clip` ⇒ 第 3 条红。
 */
class GalleryFlyCornerBleedTest {

    private val appMain = RepoSources.appMain

    private fun src(rel: String): String {
        val f = File(appMain, rel)
        assertTrue("找不到 $rel，量具失效", f.isFile)
        return f.readText().replace("\r\n", "\n")
    }

    private val cover = "com/venera/compose/components/venera/VeneraCover.kt"
    private val post = "com/venera/compose/gallery/ui/GalleryPostScreen.kt"
    private val entry = "com/venera/compose/gallery/ui/GalleryEntryFlyBody.kt"
    private val exitBody = "com/venera/compose/gallery/ui/GalleryExitFlyBody.kt"

    /**
     * 只保留**代码行**。
     *
     * 凡是"数某个符号出现几次"的判据都必须先过这一道：注释里也会写形状名（本仓已经栽过一次
     * ——"函数声明行自身含被数字符串"，那次数的是 `Modifier.gallerySearchContainerElement(`）。
     * 把文档算成一处真相，量具就在替违例者打掩护。
     */
    private fun codeLines(body: String): List<String> =
        body.lines().filterNot { it.trimStart().startsWith("//") || it.trimStart().startsWith("*") || it.trimStart().startsWith("/*") }

    /**
     * 唯一出处：`shape.medium` 只许出现在 `veneraCoverCornerRadius()` 里，裁封面与裁位图都走它。
     *
     * 种下它的是"两处各写一个 dp 值、改一处漂一处"那族：封面的圆角只要跟飞行体读的那档不一致，
     * 要么露白角（飞行体更松）、要么把图啃掉一块（飞行体更紧）。
     */
    @Test
    fun `封面圆角只有一处出处`() {
        val body = src(cover)
        val hits = codeLines(body).filter { it.contains("shape.medium") }
        assertEquals("shape.medium 只许出现在 veneraCoverCornerRadius() 里，现读：$hits", 1, hits.size)
        assertTrue("那一处必须是那颗函数的返回式", hits[0].contains("fun veneraCoverCornerRadius(): Dp = VeneraTokens.shape.medium"))
        assertTrue("裁封面必须读那颗函数", body.contains("val shape = RoundedCornerShape(veneraCoverCornerRadius())"))
        // 飞行侧不许绕过去自己取 token（否则就是第二处真相）
        val postHits = codeLines(src(post)).filter { it.contains("shape.medium") }
        assertTrue("GalleryPostScreen 不许直接读 shape.medium，现读：$postHits", postHits.isEmpty())
    }

    /**
     * 去程飞行体：半径来自位图那一档（`payloadCornerRadiusPx`），源宽取的是**裁过之后**那块。
     *
     * 2026-10-19 批 A：去程那一支搬进共享件 `GalleryEntryFlyBody.kt`，所以在这里量。
     */
    @Test
    fun `去程飞行体按位图自己的圆角裁`() {
        val body = src(entry)
        val calls = codeLines(body).filter { it.contains("payloadCornerRadiusPx(") && !it.contains("import ") }
        assertEquals("payloadCornerRadiusPx 在飞行体上只该有一处调用，现读：$calls", 1, calls.size)
        assertTrue("半径必须按封面那一档算", body.contains("payloadCornerPx = coverCornerRadius.toPx()"))
        assertTrue("源宽必须是裁过那块（src.width），不是位图全宽", body.contains("srcWidthPx = src.width"))
        // 负向：旧写法"画面框那一档半径 × 宽度比"一处都不许剩（去程改回去 ⇒ 这条红）
        assertTrue(
            "不许再用 flyCornerRadius.toPx() 当这块像素的圆角 —— 那是画面框那档，比封面松 ⇒ 白角",
            !body.contains("flyCornerRadius.toPx()"),
        )
    }

    /** 垫帧：也按封面那一档裁，倍率取 `Crop` 的两个方向里的大者。 */
    @Test
    fun `垫帧按封面圆角裁`() {
        val body = src(post)
        assertTrue("垫帧缺那圈 clip", body.contains(".clip(RoundedCornerShape(padCornerRadius))"))
        assertTrue("垫帧倍率必须按 Crop 取大者", body.contains("boxWidth.toPx() / cardFrame.width.coerceAtLeast(1)"))
        assertTrue("垫帧半径必须由封面圆角乘倍率得到", body.contains("(coverCornerRadius.toPx() * scale).toDp()"))
    }

    /**
     * 返回程落点：两头不是同一档圆角 —— 起点是画面框（`shape.large`）、终点是**卡里那张封面**
     * （`shape.medium`），所以半径要随路程插值，落回去那一帧才与封面逐像素重合。
     *
     * 2026-10-19 批 A：返回程那一支搬进共享件 `GalleryExitFlyBody.kt`，尺子跟着搬。
     * 形参从 `Float` 改成 `() -> Float` 之后（§2.7 那条：动画值在绘制阶段每帧现取），
     * 局部量改叫 `p`，所以插值那句与缩放那句的锚点也跟着换。
     * ⚠️ 锚点用**压平空白**的正则匹配，不认字面整行：那颗件里 `lerp(` 是四行写法，
     * 拿单行 contains 去钉会被格式化变动搅黄（尺子该拦"谁在飞、按哪档裁"，
     * 不该顺带规定换行怎么断）。
     */
    @Test
    fun `返回程圆角从画面框那档收到封面那档`() {
        val flat = src(exitBody).replace(Regex("\\s+"), " ")
        assertTrue(
            "返回程半径缺插值（落点会用画面框那档圆角，比封面圆 4dp ⇒ 撤走飞行体时四角补一次白）",
            Regex("lerp\\(\\s*flyCornerRadius\\s*,\\s*coverCornerRadius\\s*,\\s*p\\.coerceIn\\(0f,\\s*1f\\)")
                .containsMatchIn(flat),
        )
        assertTrue(
            "返回程仍要按当前宽度等比缩放",
            Regex("\\*\\s*computed\\.cornerRadiusScale").containsMatchIn(flat),
        )
    }
}
