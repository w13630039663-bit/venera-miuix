package com.venera.compose.components

import com.venera.compose.gallery.data.GalleryPost
import com.venera.compose.gallery.data.GallerySite
import com.venera.compose.gallery.domain.GalleryTagFilter
import com.venera.compose.testsupport.RepoSources
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 2026-10-15 问题清单 G2 批的尺子：**屏上不教内部语法** + **同一件事只一种写法** + **空态要么有真出口要么明写没有**。
 *
 * 四条都是从真机截图立案的，不是从统计来的：
 * ① 搜索结果区那行常驻灰字写着"（前面加 -）"—— 那是把**站方查询语法**教给用户，
 *    而这一屏的排除项本来就是点胶囊翻面（即时生效），语法那一路是内部写法；
 * ② 视频角标 `3″` 与同模块别处的"超过 12s 没返回"、"熔断中，Ns 后重试" **一屏两种秒数写法**，
 *    而 `″` 是英寸的排版记号；
 * ③ `VeneraChip` 的次要前缀那档把行间隙**自收**成 `space1`（2dp），实测前缀与查询词之间只剩
 *    字形自带的 1.9dp ⇒ 屏上是 `Y·G·Slong_hair censored`；
 * ④ `VeneraEmptyView` 的 `actionText`/`onAction` 两参数在画廊几乎无人传（S-3）。
 *
 * ⚠️ ①②③三条各配一把**负向尺**（扫代码行、跳注释），④一把**道数尺**（只许加长）。
 * 负向尺的命中行列出来，免得下次红在拼写上。
 */
class UiAuditG2Test {

    private fun codeText(dir: String, name: String): String =
        RepoSources.kotlinFiles(dir)
            .first { it.name == name }
            .readLines()
            .map { it.trim() }
            .filter { !it.startsWith("//") && !it.startsWith("*") && !it.startsWith("/*") }
            .joinToString("\n")

    /** 整棵 `app/src/main` 的代码行（跳三种注释行），返回"文件:行号 内容"。 */
    private fun codeLineHits(needle: String): List<String> =
        RepoSources.allMainLines()
            .filter { (_, _, line) ->
                val t = line.trim()
                !t.startsWith("//") && !t.startsWith("*") && !t.startsWith("/*") && t.contains(needle)
            }
            .map { (rel, no, line) -> "$rel:$no ${line.trim()}" }

    private val galleryUi = "com/venera/compose/gallery/ui"

    /** ① 屏上那句常驻说明不再教语法；语法本身**仍然可用**（②那条行为判据守着）。 */
    @Test
    fun `常驻行不再教查询语法`() {
        val hits = codeLineHits("前面加")
        assertEquals("代码行里又出现了教语法的句子：$hits", emptyList<String>(), hits)
        // 对照：撤掉的是"教"，不是那项能力 —— 输入框里打 `-词` 照样是排除项。
        val excluded = GalleryTagFilter.of("-long_hair")
        assertTrue("`-` 前缀这条解析路被撤掉了：$excluded", excluded?.excluded == true)
        assertEquals("long_hair", excluded?.name)
        assertEquals("-long_hair", excluded?.token)
        // 而普通词不会被误判成排除项。
        assertFalse(GalleryTagFilter.of("long_hair")?.excluded == true)
    }

    /** ② 秒数写法在 `app/src/main` 只剩 `s` 那一档（与同模块别处同一口径）。 */
    @Test
    fun `app 侧秒数写法只剩一档`() {
        val hits = codeLineHits("″")
        assertEquals("代码行里还有双撇号当秒数单位：$hits", emptyList<String>(), hits)
    }

    /** ②′ 时长角标的三档行为（有数 / 0 / 没给），钉的是"编不出假数"那条。 */
    @Test
    fun `时长角标只在真有秒数时上屏`() {
        fun label(seconds: Double?) = GalleryPost(
            site = GallerySite.YANDERE,
            id = 1L,
            previewUrl = "https://example.invalid/1.jpg",
            fileExt = "mp4",
            durationSeconds = seconds,
        ).durationLabel

        assertEquals("3s", label(3.0))
        assertEquals("95s", label(95.4))
        // 0 与 null 都交空串：编一个"0s"出来比不写更糟（角标会显示成一枚假的读数）。
        assertEquals("", label(0.0))
        assertEquals("", label(-2.0))
        assertEquals("", label(null))
    }

    /** ③ chip 的前缀档不再自收间隙。 */
    @Test
    fun `chip 前缀与主文字吃同一档间隙`() {
        val chip = codeText("com/venera/compose/components/venera", "VeneraChip.kt")
        assertEquals(
            "行间距又按 leadingText 分档了（那一档实测只剩 1.9dp，屏上读成乱码）",
            0,
            Regex("""if \(leadingText == null\) tokens\.spacing\.space2 else tokens\.spacing\.space1""").findAll(chip).count(),
        )
        assertEquals(
            "本颗 chip 的行间距应只有 `space2` 那一处定义",
            1,
            Regex("""horizontalArrangement = Arrangement\.spacedBy\(tokens\.spacing\.space2\)""").findAll(chip).count(),
        )
    }

    /** ④′ 空态出口的道数只许加长。 */
    @Test
    fun `画廊空态出口道数只许加长`() {
        val baseline = 13
        val count = RepoSources.linesOf(galleryUi).count {
            val t = it.third.trim()
            !t.startsWith("//") && !t.startsWith("*") && !t.startsWith("/*") && t.startsWith("actionText =")
        }
        assertTrue(
            "画廊那几面墙的空态出口从 $baseline 处掉到 $count 处：`actionText` 是空态唯一的行动层，" +
                "撤掉一处就等于把一屏读数的出路撤掉",
            count >= baseline,
        )
    }

    /**
     * ④″ 本批新接的那两枚出口**逐串**钉住（与上面那颗分开：归因要能一一对到扰动笔上）。
     *
     * 只数道数会被"把一枚有出口的换成一枚没出口的"喂饱 —— 那条路要靠这里的表达式钉。
     */
    @Test
    fun `空态那两枚新出口的条件与去向钉住`() {
        // 钉整串表达式而不是钉"存在"：日榜空态那枚重试**只在站方没回内容那一档**给，
        // 挡完那两档重试还是同一个结果 —— 按下去不变的是假开关。
        val daily = codeText(galleryUi, "GalleryScreen.kt")
        assertEquals(
            1,
            Regex("""actionText = if \(blocked == 0 && hidden == 0\) "重试" else null""").findAll(daily).count(),
        )
        assertEquals(
            1,
            Regex("""onAction = if \(blocked == 0 && hidden == 0\) onRetry else null""").findAll(daily).count(),
        )
        // 历史读不出来那档给的是"清掉这份历史"，且它打开的是既有的确认浮层（两步，不静默删）。
        val history = codeText(galleryUi, "GalleryHistoryScreen.kt")
        assertEquals(1, Regex("""actionText = "清掉这份历史"""").findAll(history).count())
        assertEquals(1, Regex("""onClear = \{ showClearDialog = true \}""").findAll(history).count())
    }
}
