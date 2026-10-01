package com.venera.compose.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 外部归档（官方 Venera / PicaComic）来源判读规则的判据。
 *
 * 这些规则全部照抄官方 Venera 的 `utils/data.dart: importPicaData()`，不自行发明：
 * 认错一个数字，用户看到的不是报错，而是「收藏导进来了但一点开就说找不到漫画」。
 */
class ForeignImportMappingTest {

    /** index.json 与本测试同级：单测的工作目录是模块目录 `app/`。 */
    private val sourceIndexKeys: Set<String> by lazy {
        val file = File("src/main/assets/sources/index.json")
        assertTrue("找不到 $file.absolutePath", file.exists())
        // 不用 org.json：JVM 单测里它是 android.jar 的空壳桩（返回默认值），解析不出东西。
        Regex("\"key\"\\s*:\\s*\"([^\"]+)\"")
            .findAll(file.readText())
            .map { it.groupValues[1] }
            .toSet()
    }

    @Test
    fun `内置 key 表与随包源目录保持一致`() {
        assertEquals(
            "内置 key 表与 assets/sources/index.json 漂移了；反查兜底会失效",
            sourceIndexKeys,
            ForeignImportMapping.BUILT_IN_SOURCE_KEYS.toSet(),
        )
    }

    @Test
    fun `PicaComic 收藏枚举按上游口径映射`() {
        assertEquals("picacg", ForeignImportMapping.picaFavoriteSourceKey(0))
        assertEquals("ehentai", ForeignImportMapping.picaFavoriteSourceKey(1))
        assertEquals("jm", ForeignImportMapping.picaFavoriteSourceKey(2))
        assertEquals("hitomi", ForeignImportMapping.picaFavoriteSourceKey(3))
        // 上游注释「换名字了, 绅士漫画」：4 对应 htmanga，在本仓叫 wnacg
        assertEquals("wnacg", ForeignImportMapping.picaFavoriteSourceKey(4))
        assertEquals("nhentai", ForeignImportMapping.picaFavoriteSourceKey(6))
        assertNull("收藏表没有 5 这个值", ForeignImportMapping.picaFavoriteSourceKey(5))
        assertNull(ForeignImportMapping.picaFavoriteSourceKey(7))
    }

    @Test
    fun `PicaComic 历史枚举里 nhentai 是 5 而不是 6`() {
        assertEquals("picacg", ForeignImportMapping.picaHistorySourceKey(0))
        assertEquals("ehentai", ForeignImportMapping.picaHistorySourceKey(1))
        assertEquals("jm", ForeignImportMapping.picaHistorySourceKey(2))
        assertEquals("hitomi", ForeignImportMapping.picaHistorySourceKey(3))
        assertEquals("wnacg", ForeignImportMapping.picaHistorySourceKey(4))
        assertEquals("nhentai", ForeignImportMapping.picaHistorySourceKey(5))
        assertNull("历史表没有 6 这个值", ForeignImportMapping.picaHistorySourceKey(6))
    }

    @Test
    fun `收藏与历史的枚举在 5 和 6 上确实不同——这不是笔误，是上游的历史包袱`() {
        assertNotEquals(
            ForeignImportMapping.picaFavoriteSourceKey(6),
            ForeignImportMapping.picaHistorySourceKey(6),
        )
        assertNotEquals(
            ForeignImportMapping.picaFavoriteSourceKey(5),
            ForeignImportMapping.picaHistorySourceKey(5),
        )
    }

    @Test
    fun `htmanga 一律归一到 wnacg，且不误伤别的 key`() {
        assertEquals("wnacg", ForeignImportMapping.normalizeSourceKey("htmanga"))
        assertEquals("wnacg", ForeignImportMapping.normalizeSourceKey("HtManga"))
        assertEquals("wnacg", ForeignImportMapping.normalizeSourceKey("wnacg"))
        assertEquals("picacg", ForeignImportMapping.normalizeSourceKey("picacg"))
        assertEquals("", ForeignImportMapping.normalizeSourceKey(""))
    }

    @Test
    fun `Dart 哈希反查表能认出候选源，认不出的返回空`() {
        val index = ForeignImportMapping.dartHashSourceIndex(listOf("picacg", "ehentai", "my_custom_src"))

        assertEquals("picacg", index[DartStringHash.hash("picacg")])
        assertEquals("ehentai", index[DartStringHash.hash("ehentai")])
        assertEquals("my_custom_src", index[DartStringHash.hash("my_custom_src")])
        // 不在候选集合里的源查不出来 —— 调用方要靠这个计数并跳过
        assertNull(index[DartStringHash.hash("kavita")])
    }

    @Test
    fun `反查表忽略空 key，不会把空串塞进表里`() {
        val index = ForeignImportMapping.dartHashSourceIndex(listOf("", "  ", "picacg"))
        assertEquals(1, index.size)
    }

    @Test
    fun `章节与页码由 1 基转 0 基，且不会转出负数`() {
        assertEquals(0, ForeignImportMapping.toZeroBasedIndex(1))
        assertEquals(4, ForeignImportMapping.toZeroBasedIndex(5))
        // PicaComic 用 0 表示「没有阅读位置记录」
        assertEquals(0, ForeignImportMapping.toZeroBasedIndex(0))
        assertEquals(0, ForeignImportMapping.toZeroBasedIndex(-3))
    }

    // ---- 源文件 → key：官方归档里只能靠内容拿 key，文件名靠不住 ----

    private fun sourceFile(name: String) = File("src/main/assets/sources/$name")

    @Test
    fun `文件名不等于 key，所以必须读内容`() {
        // 这两个正是"看文件名必然认错"的例子，用真实的随包源来验，不另造样例
        assertEquals(
            "Komiic",
            ForeignImportMapping.sourceKeyFromSourceFile(sourceFile("komiic.js").readText()),
        )
        assertEquals(
            "copy_manga",
            ForeignImportMapping.sourceKeyFromSourceFile(
                sourceFile("copy_manga_multi_accounts.js").readText()
            ),
        )
    }

    @Test
    fun `不会把函数体里的局部变量 key 误当成源的 key`() {
        // picacg.js 的 createSignature() 里有一句 `let key = '~d}$Q7...'`，
        // 正则若不带行首约束就会抓到它
        val key = ForeignImportMapping.sourceKeyFromSourceFile(sourceFile("picacg.js").readText())
        assertEquals("picacg", key)
    }

    @Test
    fun `每个随包源都能读出 key，且都落在源索引里`() {
        val jsFiles = File("src/main/assets/sources").listFiles { f -> f.extension == "js" }
        assertTrue("没有找到任何源文件", jsFiles != null && jsFiles.isNotEmpty())

        @Suppress("UNCHECKED_CAST")
        val files = jsFiles as Array<File>
        val extracted = files.associate {
            it.name to ForeignImportMapping.sourceKeyFromSourceFile(it.readText())
        }
        for (file in files) {
            assertTrue("${file.name} 读不出 key", extracted[file.name] != null)
        }
        val unknown = extracted.values.filterNotNull().toSet() - sourceIndexKeys
        assertTrue("这些 key 不在 index.json 里，说明匹配错了位置：$unknown", unknown.isEmpty())
    }
}
