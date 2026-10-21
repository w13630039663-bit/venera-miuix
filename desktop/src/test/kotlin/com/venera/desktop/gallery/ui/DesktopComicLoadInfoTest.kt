package com.venera.desktop.gallery.ui

import com.venera.engineprobe.parseComicLoadInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 批 80 · `loadInfo` 那一坨答复在桌面这一侧怎么读成「标题 + 简介 + 章节表」。
 *
 * 判据住在桌面测试里而实现住在 `engine-probe`：那一颗模块是桌面与探针共用的取数层，
 * 而**解析语义的权威是 Android 侧 `JsComicSource.getComicDetails`**（同一份 JS 答复，两处读成两种形状
 * 就是"桌面看到的章节比手机少"那一族缺陷的成因），所以这里逐条回核它，而不是自造一套。
 */
class DesktopComicLoadInfoTest {

    @Test
    fun `chapters 是 Map 时 key 是话号而 value 是话名`() {
        // ⚠️ 这一条从前被写过一次反的（`EngineSession.details` 的注释：「我第一版写反了，
        // loadEp 当场报 Invalid Data」）。反了的样子在屏上完全正常 —— 列表照摆，只有点开是错的，
        // 所以这一格必须由键值对逐字钉住，不许靠"看起来对"。
        val info = parseComicLoadInfo(
            "9",
            mapOf(
                "success" to true,
                "data" to mapOf(
                    "title" to "某本漫画",
                    "description" to "一句话简介",
                    "chapters" to mapOf("1" to "第 1 话", "2" to "第 2 话"),
                ),
            ),
        )
        assertEquals("某本漫画", info.title)
        assertEquals("一句话简介", info.description)
        assertEquals(listOf("第 1 话" to "1", "第 2 话" to "2"), info.episodes.map { it.name to it.id })
        assertTrue("话表非空时不许掺占位章", info.episodes.none { it.placeholder })
    }

    @Test
    fun `chapters 是分组表时按外层分组次序摊平`() {
        val info = parseComicLoadInfo(
            "9",
            mapOf(
                "success" to true,
                "data" to mapOf(
                    "title" to "T",
                    "chapters" to mapOf(
                        "第一卷" to mapOf("a" to "1 话", "b" to "2 话"),
                        "第二卷" to mapOf("c" to "3 话"),
                    ),
                ),
            ),
        )
        assertEquals(listOf("1 话", "2 话", "3 话"), info.episodes.map { it.name })
        assertEquals(listOf("a", "b", "c"), info.episodes.map { it.id })
    }

    @Test
    fun `chapters 是 List 时取 title 与 id 而缺项各自有兜底`() {
        val info = parseComicLoadInfo(
            "9",
            mapOf(
                "success" to true,
                "data" to mapOf(
                    "title" to "T",
                    "episodes" to listOf(
                        mapOf("title" to "有名字", "id" to "x"),
                        mapOf("id" to "y"),
                        mapOf("title" to "没给号"),
                    ),
                ),
            ),
        )
        assertEquals(listOf("有名字" to "x", "第 1 话" to "y", "没给号" to "2"), info.episodes.map { it.name to it.id })
    }

    @Test
    fun `完全没有章节表时补一章占位而话号沿用漫画号`() {
        val info = parseComicLoadInfo("9", mapOf("success" to true, "data" to mapOf("title" to "单本")))
        assertEquals(1, info.episodes.size)
        assertEquals("9", info.episodes.single().id)
        assertEquals("", info.episodes.single().name)
        // 占位那一档不是装饰：`pages` 拿它决定第三个实参交 `null` 还是交货号，漏了就取不到页。
        assertTrue(info.episodes.single().placeholder)
    }

    @Test
    fun `缺 description 与 null description 都读成空串而不是没有这一栏`() {
        val absent = parseComicLoadInfo("9", mapOf("success" to true, "data" to mapOf("title" to "T")))
        val nulled = parseComicLoadInfo(
            "9",
            mapOf("success" to true, "data" to mapOf("title" to "T", "description" to null)),
        )
        assertEquals("", absent.description)
        assertEquals("", nulled.description)
    }

    @Test
    fun `答复里的 cover 读成详情封面而缺栏与 null 都是空串`() {
        // 批 82.6：这一栏从前没读，于是桌面写进 `comic_history` 的封面恒空 —— 而那一格是两端共用的。
        val given = parseComicLoadInfo(
            "9",
            mapOf("success" to true, "data" to mapOf("title" to "T", "cover" to "https://cdn.example/c.jpg")),
        )
        assertEquals("https://cdn.example/c.jpg", given.cover)
        val absent = parseComicLoadInfo("9", mapOf("success" to true, "data" to mapOf("title" to "T")))
        val nulled = parseComicLoadInfo(
            "9",
            mapOf("success" to true, "data" to mapOf("title" to "T", "cover" to null)),
        )
        assertEquals("缺这一栏读成空串而不是「没有这一格」", "", absent.cover)
        assertEquals("", nulled.cover)
    }

    @Test
    fun `cover 那一栏的键名回核 Android 侧而不是桌面自定`() {
        // 行尾归一之后才回核：这条钉的是**相邻关系**（详情那一段读完 `cover` 紧接着读 `desc`），不是字节。
        // 对岸那颗文件什么时候变成 CRLF 不归桌面管（且它正被并行线改），拿 `\n` 去夹就会假红 ——
        // 批 115 现量：`app/…/JsComicSource.kt` 1918 行全是 CRLF，归一后这条立刻匹配（`:464` 紧接 `:465` 一字未漂）。
        val counterpart = DesktopSourceTree.androidSource("source/js/JsComicSource.kt").readText().replace("\r\n", "\n")
        // 对岸 `getComicDetails` 读的就是 `res["cover"]`，**没有**第二个候选键。
        // 桌面要是"顺手多认一个 coverUrl"，两端的章节表会一致而封面不一致 —— 那正是共用的表上最难查的漂。
        assertTrue(
            "对岸详情那一段不再用 cover 这一栏了 ⇒ 桌面这一条要跟着改判",
            Regex("""val cover = res\["cover"\]\?\.toString\(\) \?: ""\n\s*val desc""").containsMatchIn(counterpart),
        )
        // 反面那一半也要有尺子：只回核对岸的话，桌面"顺手多认一个候选键"这一笔就没人管，
        // 而那正是缺栏时两端读出两种东西的成因（列表一致而封面不一致，最难查的那种漂）。
        val parsed = java.io.File(DesktopSourceTree.repoRoot, "engine-probe/src/main/kotlin/com/venera/engineprobe/ComicLoadInfo.kt")
        assertEquals("桌面这一侧只许认 cover 这一栏", 1, DesktopSourceTree.codeOccurrences(parsed, "cover = data[\"cover\"]"))
        assertEquals("不许多认候选键（coverUrl 是列表那一侧的写法，详情答复里没有这一栏）", 0, DesktopSourceTree.codeOccurrences(parsed, "data[\"coverUrl\"]"))
    }

    @Test
    fun `站方明说失败时抛出去而不是交回一张空详情`() {
        // 这条与「降级不许静默」同族：`success=false` 读成"这本没有章节"，
        // 用户看到的是一句谎话（"源没给目录"）而不是一句故障。
        val failure = runCatching {
            parseComicLoadInfo("9", mapOf("success" to false, "error" to "Invalid Authorization"))
        }.exceptionOrNull()
        assertTrue("站方回 success=false 时必须抛，不能交回空详情", failure != null)
        assertTrue(
            "抛出去的那句要带上站方的原话，否则排障时只剩一个类型名：${failure?.message}",
            failure?.message?.contains("Invalid Authorization") == true,
        )
    }

    @Test
    fun `答复里没有 data 时那句点名是信封缺货而不是这本漫画没内容`() {
        val failure = runCatching { parseComicLoadInfo("9", mapOf("success" to true)) }.exceptionOrNull()
        assertTrue("没有 data 必须抛", failure != null)
        assertTrue(
            "那一句要念得出缺的是哪一层：${failure?.message}",
            failure?.message?.contains("data") == true,
        )
    }
}
