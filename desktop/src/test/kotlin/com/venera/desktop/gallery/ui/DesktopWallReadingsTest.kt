package com.venera.desktop.gallery.ui

import com.venera.compose.gallery.data.GallerySite
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 桌面第十五批 批 12 · **墙上那一行的读数**收进一颗件（批 11 那两帧图证抓到的形状）。
 *
 * ## 抓到的不是"数错"，是"分隔符悬着"
 *
 * 「进屏 N 张 · 各站 x 张」这一串在四处各写了一份，其中三处是**拼出来的**：
 * 各站那一段为空时（0 张那一档真会走到 —— 全被分级挡掉、或站方答上但 0 条），
 * 屏上读到的是末尾悬着的一枚「·」；而最新那一屏更糟，它把空段 `add` 进列表里，
 * 于是悬着的那一枚跑到**开头**。第四处（收藏）自己写了个 `if (isEmpty) ""` 才躲过去 ——
 * 同一句话四处各写一遍，其中一处要自己躲，那就是"下一处会忘"的形状。
 *
 * ## 另一条是中西文之间那枚空格
 *
 * 「yande.re这一页之后没有更多」——站名是拉丁文而它后面直接跟中文，读起来像站名叫
 * "yande.re这一页"。这与第八批那条"两枚站名用空格分隔被读成一枚站名"是同一族：**图证才看得见**。
 *
 * ## 单位词不统一，是故意的
 *
 * 三面墙念「N 张」而最新那一屏念「N 条」—— 前者数的是摆上屏的图，后者数的是取回的条目
 * （它下面另有一行"滤掉 N 张不能上屏的"）。所以 `unit` 是**必填参数**而不是一个默认值：
 * 带默认值时漏传不报错，而漏传的形状恰好是"那一屏悄悄换了单位"。
 */
class DesktopWallReadingsTest {

    private val yandere = GallerySite.YANDERE
    private val gelbooru = GallerySite.GELBOORU

    /** ① 各站那一小串：顿号分隔、单位词由调用点给。 */
    @Test
    fun 各站那串按顿号分隔而单位由调用点给() {
        val line = desktopPerSiteReading(
            linkedMapOf(yandere to 74, gelbooru to 97),
            unit = "张",
        )
        assertEquals("yande.re 74 张、Gelbooru 97 张", line)
    }

    /** ①′ 单位词真的被吃进去（最新那一屏念「条」而三面墙念「张」—— 写死成"张"会让前者静默换单位）。 */
    @Test
    fun 单位词由调用点给而那颗件不写死() {
        assertEquals("yande.re 3 条", desktopPerSiteReading(mapOf(yandere to 3), unit = "条"))
    }

    /** ② 空表交回空串，而不是"看起来像有一站"的那一枚顿号。 */
    @Test
    fun 没有任何一站时交回空串() {
        assertEquals("", desktopPerSiteReading(emptyMap<GallerySite, Int>(), unit = "条"))
    }

    /** ③ 段与段之间只有一枚分隔符，而**空段与 null 段一律丢掉**（这一条就是那枚悬着的「·」）。 */
    @Test
    fun 空段丢掉所以末尾不悬着分隔符() {
        assertEquals(
            "进屏 0 张",
            desktopWallReading("进屏 0 张", desktopPerSiteReading(emptyMap(), unit = "张"), null),
        )
        assertEquals("进屏 171 张 · yande.re 171 张", desktopWallReading("进屏 171 张", "yande.re 171 张"))
    }

    /** ③′ 续行那一档：有内容才带那一枚前导分隔符，全空时交回**空串**而不是孤零零的一枚「·」。 */
    @Test
    fun 续行那档只在有内容时带前导分隔符() {
        assertEquals(" · 进屏 0 张", desktopWallReadingLine("进屏 0 张", ""))
        assertEquals("", desktopWallReadingLine("", null))
    }

    /** ④ 一段都没有 ⇒ 空串（调用方据此**整格不画**，而不是画一枚孤零零的分隔符）。 */
    @Test
    fun 全空时整串是空串() {
        assertEquals("", desktopWallReading(null, "", "   "))
    }

    /** ⑤ 顺序 = 传入顺序（页码那一句永远在最后，它是最次要的一笔）。 */
    @Test
    fun 段序就是传入顺序() {
        assertEquals(
            "进屏 20 张 · yande.re 20 张 · 已给到第 2 页",
            desktopWallReading("进屏 20 张", "yande.re 20 张", "已给到第 2 页"),
        )
    }

    /** ⑥ 站名与中文之间那枚空格（拉丁文直接顶住中文会被读成站名的一部分）。 */
    @Test
    fun 到底那句在站名与中文之间留一枚空格() {
        val line = desktopExhaustedReading(listOf("yande.re"))
        assertEquals("已经取到底：yande.re 这一页之后没有更多（这一轮发过的站都到底了）", line)
        assertFalse("站名顶住中文：$line", line!!.contains("yande.re这"))
    }

    /** ⑦ 多站那几枚之间是顿号，与「各站张数」那一串同一口径。 */
    @Test
    fun 到底那句多站时按顿号分隔() {
        assertEquals(
            "已经取到底：yande.re 这一页之后没有更多、Gelbooru 这一页之后没有更多（这一轮发过的站都到底了）",
            desktopExhaustedReading(listOf("yande.re", "Gelbooru")),
        )
    }

    /** ⑧ 没有一站到底 ⇒ **不念**（上一版会念出「已经取到底：（这一轮发过的站都到底了）」这种空壳）。 */
    @Test
    fun 没有一站到底时那句不念() {
        assertNull(desktopExhaustedReading(emptyList()))
    }

    /**
     * ⑨ 负向：那三处手拼的形状不许回来。
     *
     * ⚠️ 一次列全违例而不是找到第一处就短路 —— 这条尺子要的是"还剩几处在手写"，
     * 短路版本会把"还剩三处"读成"还剩一处"。
     */
    @Test
    fun 那三处手拼的形状不许回来() {
        val shapes = listOf("\" · 进屏 ", "\" · \" + ", "add(state.perSite")
        val offenders = mutableListOf<String>()
        DesktopSourceTree.desktopUiSources().forEach { file ->
            val code = DesktopSourceTree.codeText(file)
            shapes.forEach { shape -> if (code.contains(shape)) offenders += "${file.name} 还含「$shape」" }
        }
        assertEquals("手写那串读数的地方：\n${offenders.joinToString("\n")}", emptyList<String>(), offenders)
    }

    /**
     * ⑩ 正向锚点：四处都真走那颗件，而且**各用对那一档**。
     *
     * 三面墙的「进屏 N 张」接在标题后面 ⇒ 用带前导分隔符的那一档；最新那一屏是独立一行 ⇒ 用不带的那一档。
     * 用错的读数没有测试可跑（差的就是屏上那一枚「 · 」），所以这里按"哪一处该用哪一颗"一对一钉死。
     */
    @Test
    fun 四处调用点各用对那一档() {
        val expected = listOf(
            "DesktopDailyPane.kt" to "desktopWallReadingLine(",
            "DesktopGalleryFavoritesPane.kt" to "desktopWallReadingLine(",
            "DesktopGallerySearchPane.kt" to "desktopWallReadingLine(",
            "DesktopLatestPane.kt" to "desktopWallReading(",
        )
        expected.forEach { (name, call) ->
            val code = DesktopSourceTree.codeText(DesktopSourceTree.desktopUiSource(name))
            assertTrue("$name 没走「$call」那一档", code.contains(call))
        }
        val exhausted = DesktopSourceTree.codeText(DesktopSourceTree.desktopUiSource("DesktopGallerySearchPane.kt"))
        assertTrue("页尾那句没走那颗件", exhausted.contains("desktopExhaustedReading("))
    }

    /**
     * ⑩′ 单位词各点自己给：三面墙数的是**摆上屏的图**（张），最新那一屏数的是**取回的条目**（条）。
     *
     * 这一条钉的是值而不是形状 —— 谁把某一处顺手改成"张"，屏上那句话就换了主语，
     * 而那种改动在纯函数的判据里量不出来（①′ 只证明那颗件吃参数）。
     */
    @Test
    fun 三面墙数张而最新那一屏数条() {
        mapOf(
            "DesktopDailyPane.kt" to "unit = \"张\"",
            "DesktopGallerySearchPane.kt" to "unit = \"张\"",
            "DesktopGalleryFavoritesPane.kt" to "unit = \"张\"",
            "DesktopLatestPane.kt" to "unit = \"条\"",
        ).forEach { (name, unit) ->
            val code = DesktopSourceTree.codeText(DesktopSourceTree.desktopUiSource(name))
            assertTrue("$name 的单位档不是「$unit」", code.contains(unit))
        }
    }

    /** ⑪ 分隔符口径只有一处字面量（两处：段与段、站与站），别处再写一份就会漂。 */
    @Test
    fun 两枚分隔符全仓只许那颗件写一次() {
        val holder = File(
            DesktopSourceTree.repoRoot,
            "desktop/src/main/kotlin/com/venera/desktop/gallery/ui/DesktopWallReadings.kt",
        )
        assertTrue("读不到那颗件：${holder.path}", holder.isFile)
        val code = holder.readText()
        assertEquals("段与段那枚分隔符的字面量只许出现一次", 1, countOf(code, "String = \" · \""))
        assertEquals("站与站那枚分隔符的字面量只许出现一次", 1, countOf(code, "String = \"、\""))
    }

    private fun countOf(text: String, token: String): Int = text.split(token).size - 1
}
