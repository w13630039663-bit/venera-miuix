package com.venera.desktop.gallery.ui

import com.venera.compose.data.db.LocalFavoriteDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 漫画卡那一排的「收藏」（批 76）。
 *
 * ## 这一条为什么是"接线"而不是"新功能"
 *
 * 写链整条早在：共享面的 `LocalFavoritesManager.addComic`（抛=收藏夹不存在、返回 `false`=这本已经在
 * 里面）、桌面侧那一句三态措辞也逐字照 Android 的 `ComicCardContextMenu` 抄好了 —— 而**唯一调用它的是
 * `--autofav` 那段取证路径**（手册 §16 批 42 之前就在账上）。于是屏上收藏夹进得去、看得到，**存不进东西**：
 * "实现了而界面没有入口"那一族里最刺眼的一格，因为用户点不到的那颗钮不会自己报错。
 *
 * ## 四条纪律各由一颗判据钉着
 *
 * 1. **三态不许塌成两句**："已在里面"与"收藏失败"撞成一句，读起来就是"点了没反应"。
 * 2. **两端同词**：桌面自持一份句子可以（UI 跨端 import 是禁的），但两处的**句核**由判据 ② 逐字回核，
 *    一侧改了另一侧就红 —— 与批 12 那枚「关注 / 已关注」同一形状。
 * 3. **按钮与取证种子同一枚动作**：种子存在的理由是"按下去才出来的那一格拍不到"（禁鼠标），
 *    两处各写一遍的话，种子跑通的那条路就不是用户按的那条路（判据 ④ 数出现次数，不数存在）。
 * 4. **不编字段**：桌面的 `EngineSession.ComicCard` 没带 `subTitle`/`tags`，那两枚就按 `FavoriteItem`
 *    自身的空默认值落库（判据 ⑤）。写了假值就是"收藏里躺着一条看着像而来源不明的记录"。
 */
class DesktopComicFavoriteTest {

    private val folder = LocalFavoriteDatabase.DEFAULT_FOLDER

    /** ① 三种结果各一句，两两不许相同。 */
    @Test
    fun 三种结果各念一句() {
        val lines = listOf(
            desktopComicFavoriteScreenReading(DesktopComicFavoriteOutcome.Added(folder)),
            desktopComicFavoriteScreenReading(DesktopComicFavoriteOutcome.Already(folder)),
            desktopComicFavoriteScreenReading(
                DesktopComicFavoriteOutcome.Failed("Folder does not exists", IllegalStateException("Folder does not exists")),
            ),
        )
        assertTrue("每句都得能上屏：" + lines, lines.all { it.isNotBlank() })
        assertEquals("三态里有两句撞在一起（塌成一句就是把「没成」读成「点了没反应」）：" + lines, 3, lines.distinct().size)
        assertTrue("已在里面那句要点名「已经」：" + lines[1], lines[1].contains("已经"))
        assertTrue("没成那句要点名失败：" + lines[2], lines[2].contains("收藏失败"))
    }

    /** ② 三句的句核与 Android 那侧逐字同词（两端各写一份词，换设备就读成两件事）。 */
    @Test
    fun 三句与android那侧同词() {
        val android = DesktopSourceTree.codeText(DesktopSourceTree.androidSource("components/ComicCardContextMenu.kt"))
        val desktop = DesktopSourceTree.codeText(DesktopSourceTree.desktopUiSource("DesktopComicFavorite.kt"))
        listOf("已收藏到「", "这本已经在「", "收藏失败：").forEach { core ->
            assertTrue("对照组失效：Android 那侧没这句核「$core」", android.contains(core))
            assertTrue("桌面这句核「$core」没了（或换了说法）：两端同词靠它", desktop.contains(core))
        }
    }

    /**
     * ③ 屏上真有那颗钮，且各枚锚点**只一处**。
     *
     * 数出现次数而不是数"存在"：本仓记过一条"锚点被同屏另一处合法调用喂饱"——只断言 contains 的话，
     * 取证段里多一处调用就能替屏上那一处作保。
     */
    @Test
    fun 卡那一排有收藏那颗() {
        val pane = DesktopSourceTree.desktopUiSource("DesktopComicPane.kt")
        val code = DesktopSourceTree.codeText(pane)
        assertEquals(
            "那颗药丸的出现次数不对（声明一处、卡上一处）：" +
                DesktopSourceTree.codeOccurrences(pane, "ComicFavoritePill("),
            2, DesktopSourceTree.codeOccurrences(pane, "ComicFavoritePill("),
        )
        assertEquals(
            "屏上调的那枚写口不是正好一处（" + DesktopSourceTree.codeOccurrences(pane, "desktopAddComicFavorite(") + " 处）—— 零处=没接，两处=各写一份",
            1, DesktopSourceTree.codeOccurrences(pane, "desktopAddComicFavorite("),
        )
        assertEquals(
            "结果那句只许在屏上摆一处，实际 ${DesktopSourceTree.codeOccurrences(pane, "desktopComicFavoriteScreenReading(")} 处",
            1, DesktopSourceTree.codeOccurrences(pane, "desktopComicFavoriteScreenReading("),
        )
        // 对照：那颗药丸是**点得动的**（挂 clickable），且走协程（写库是 suspend）
        val decl = code.indexOf("private fun ComicFavoritePill(")
        assertTrue("那颗药丸的声明没找到（判式先失效）", decl >= 0)
        // 窗口只取声明往后那一截：文件里另有一枚「重取」也挂着 clickable，全文 contains 会替它作保
        // （本仓那条"锚点被同屏另一处合法调用喂饱"的形状）。
        assertTrue(
            "那颗药丸没挂 clickable（摆着点不动就是假按钮）",
            code.substring(decl, minOf(decl + 320, code.length)).contains(".clickable"),
        )
        assertTrue("按下去那一笔没起协程（suspend 那颗调不到）", code.contains("rememberCoroutineScope"))
    }

    /** ④ 按钮与种子共用同一枚动作，而集成方不许自带一份写。 */
    @Test
    fun 种子与按钮同一枚动作() {
        val app = DesktopSourceTree.desktopSourceOf("VeneraDesktop.kt")
        val code = DesktopSourceTree.codeText(app)
        assertEquals(
            "取证那一路没按抽出来的那一枚（出现 ${DesktopSourceTree.codeOccurrences(app, "desktopAddComicFavorite(")} 次）——" +
                "自己写第二份，种子跑通的就不是用户按的那条路",
            1, DesktopSourceTree.codeOccurrences(app, "desktopAddComicFavorite("),
        )
        assertEquals(
            "集成方还自己写着一份 addComic：" +
                DesktopSourceTree.codeLinesOf(app).filter { it.contains("addComic(") }.joinToString(" ｜ "),
            0, DesktopSourceTree.codeOccurrences(app, "addComic("),
        )
        assertFalse("旧的那颗私有件还在（两处各一份三态措辞就是将来只改一处）", code.contains("fun addFavorite("))
        // 对照：共享面那颗写口真的在（读空了上面两条会一起恒真）
        val manager = DesktopSourceTree.codeText(DesktopSourceTree.androidSource("data/db/LocalFavoritesManager.kt"))
        assertTrue("对照组失效：共享面的 addComic 声明没了", manager.contains("suspend fun addComic("))
    }

    /** ⑤ 落库字段逐条对应，模型没有的那两枚不许编。 */
    @Test
    fun 落库字段不许编() {
        val file = DesktopSourceTree.desktopUiSource("DesktopComicFavorite.kt")
        val code = DesktopSourceTree.codeText(file)
        listOf("id = card.id", "name = card.title", "sourceKey = sourceKey", "coverPath = card.cover").forEach {
            assertTrue("少了这一行映射（Android 那侧同一枚 FavoriteItem 就是这么填的）：" + it, code.contains(it))
        }
        listOf("author =", "tags =").forEach {
            assertFalse("桌面模型没带这一枚，写了就是编字段：" + it, code.contains(it))
        }
    }

    /** ⑥ 没成那一行念得出成因、类型与栈顶，且键值进不了读数。 */
    @Test
    fun 失败念得出未成那一行() {
        val thrown = IllegalStateException("Folder does not exists")
        val plain = desktopComicFavoriteFailureReason(thrown)
        val line = desktopComicFavoriteRefusalReading(
            DesktopComicFavoriteOutcome.Failed("Folder does not exists", thrown),
        )
        println("CF_$line")
        assertTrue("没成的那一行前缀不在：" + line, line.startsWith("D_收藏 未成 因="))
        assertTrue("类型名没念出来：" + line, line.contains("IllegalStateException"))
        assertTrue("栈那一格空着（拿不到要念没有二字）：" + line, Regex("栈=([A-Za-z0-9.<>]+\\.kt:-?\\d+|没有)").containsMatchIn(line))
        val keyed = IllegalStateException("https://x/ajax?api_key=SECRETVALUE&user_id=42")
        val reason = desktopComicFavoriteFailureReason(keyed)
        val scrubbed = desktopComicFavoriteRefusalReading(DesktopComicFavoriteOutcome.Failed(reason, keyed))
        println("CF_$scrubbed")
        assertFalse("键值进了读数：" + scrubbed, scrubbed.contains("SECRETVALUE"))
        assertTrue("键名要留着（说得出是哪一枚被挡）：" + scrubbed, scrubbed.contains("api_key=***"))
        // 对照：普通消息不许被抹平（防止脱敏做成"整条打星"）
        assertTrue("对照组失效：普通成因也被抹平了", !plain.contains("***"))
    }

    /** ⑦ 三态各有一行 stdout 读数 —— "没跑到"与"跑到了而失败"不许在 stdout 上同一个样。 */
    @Test
    fun 三种结果都念得出一行() {
        val added = desktopComicFavoriteReadout("jm", "abc123", DesktopComicFavoriteOutcome.Added(folder))
        println("CF_$added")
        assertTrue("点击那一行要能被 grep 挑出来：" + added, added.startsWith("D_收藏 点击"))
        assertTrue("少了源那枚：" + added, added.contains("源=jm"))
        assertTrue("少了卡片号：" + added, added.contains("id=abc123"))
        val already = desktopComicFavoriteReadout("jm", "abc123", DesktopComicFavoriteOutcome.Already(folder))
        val failed = desktopComicFavoriteReadout(
            "jm", "abc123", DesktopComicFavoriteOutcome.Failed("没有这一夹", IllegalStateException("没有这一夹")),
        )
        assertEquals("三态的读数互不相同（撞在一起就没法归因）", 3, listOf(added, already, failed).distinct().size)
        assertTrue("没成也要有点击那一行：" + failed, failed.contains("收藏失败"))
        // 写口两步：落库之外必须出声。三颗纯函数若没人打印，屏上与 stdout 都是哑的（本批那条"件在而没接"的近亲）。
        val helper = DesktopSourceTree.desktopUiSource("DesktopComicFavorite.kt")
        assertEquals("点击读数被打了两遍或没打：" + DesktopSourceTree.codeOccurrences(helper, "println(desktopComicFavoriteReadout("),
            1, DesktopSourceTree.codeOccurrences(helper, "println(desktopComicFavoriteReadout("))
        assertEquals("没成那一行没打印（或打了两遍）：" + DesktopSourceTree.codeOccurrences(helper, "println(desktopComicFavoriteRefusalReading("),
            1, DesktopSourceTree.codeOccurrences(helper, "println(desktopComicFavoriteRefusalReading("))
    }

    /** ⑧ 建库那一步留到按下去，且不在主线程（`getInstance` 会建两棵库）。 */
    @Test
    fun 建库留到按下去() {
        val file = DesktopSourceTree.desktopUiSource("DesktopComicFavorite.kt")
        val code = DesktopSourceTree.codeText(file)
        assertTrue("那颗件没走到 IO 线程（按下去就卡 UI）", code.contains("withContext(Dispatchers.IO)"))
        assertTrue("它自己建库而不是吃懒装载那颗（开机即建=白建两棵）", code.contains("favorites()"))
        assertFalse("那颗件直接 getInstance（懒装载就白做了）", code.contains("getInstance("))
        // 对照：集成方那颗懒取函数还在，两处吃的都是它
        val app = DesktopSourceTree.codeText(DesktopSourceTree.desktopSourceOf("VeneraDesktop.kt"))
        assertTrue("对照组失效：集成方的懒取那颗没了", app.contains("private fun favorites("))
    }

    /** ⑨ 那句"没有入口"跟着钮一起下架（§22.3 那条回查）。 */
    @Test
    fun 没有入口那句不许残留() {
        val offenders = DesktopSourceTree.codeLinesOf(DesktopSourceTree.desktopUiSource("DesktopComicPane.kt"))
            .filter { it.contains("没有入口") || it.contains("还接不了") }
            .map { it.trim() }
        assertTrue("那一屏还写着收藏没有入口：" + offenders.joinToString(" ｜ "), offenders.isEmpty())
    }
}
