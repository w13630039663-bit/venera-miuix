package com.venera.desktop.db

import com.venera.compose.data.db.TagDictionaryStore
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

/**
 * 画廊离线词典的读法（R18：真 `JdbcSqliteDatabase` + 临时 `.db`）。
 *
 * 这一层坏掉的表现是"卡片没标题、画师栏不出、标签没译文"，屏上与"词典就是没这些词"长得一样，
 * 所以列的读法、大小写归一、分块边界都得有用例。
 *
 * 注意库的来源：真词典是 `assets/gallery_tags_*.sqlite`（由 `scripts/build_tag_dictionaries.mjs`
 * 生成，构建期截 `post_count >= 100` 的那些行），Android 侧首次使用时复制到 `databases/`。
 * 本用例不复制资产（那是那颗还吃 Context 的文件的活），只按同一套 `tags` 表形状造一份小词典。
 */
class TagDictionaryStoreTest {

    private lateinit var store: TestDatabase
    private lateinit var dictionary: TagDictionaryStore

    @Before
    fun setUp() {
        store = TestDatabase("tagdict")
        // 形状逐字照构建脚本那句 DDL：scripts/build_tag_dictionaries.mjs
        store.db.exec("CREATE TABLE tags (name TEXT PRIMARY KEY, category INTEGER, cn TEXT) WITHOUT ROWID;")
        dictionary = TagDictionaryStore(store.db)
    }

    @After
    fun tearDown() = store.close()

    private fun put(name: String, category: Int, cn: String?) {
        store.db.exec("INSERT INTO tags (name, category, cn) VALUES (?, ?, ?)", name, category, cn)
    }

    @Test
    fun `按名查交回三列且档位与译名各按原形状`() {
        put("artist:foo", 1, "画师甲")
        put("shirt_lift", 0, "掀衣")

        val rows = dictionary.rowsFor(listOf("artist:foo", "shirt_lift")).associateBy { it.name }

        assertEquals(1, rows.getValue("artist:foo").category)
        assertEquals("画师甲", rows.getValue("artist:foo").cn)
        assertEquals(0, rows.getValue("shirt_lift").category)
        assertEquals("掀衣", rows.getValue("shirt_lift").cn)
    }

    @Test
    fun `没有中文译名的行交回 null 而不是空串`() {
        // 库里 cn 为 NULL 表示"没有公认中文名"，调用方要据此回显原词；
        // 读成空串就会让那一格变成空白，与"有译名但译名是空"分不开。
        put("some_name", 3, null)

        val row = dictionary.rowsFor(listOf("some_name")).single()
        assertNull(row.cn)
    }

    @Test
    fun `名字一律先小写再查且一批里的重复只查一次`() {
        // 真词典的 name 存的就是小写标识符（构建脚本按 Danbooru 标签原样小写入库），
        // 这一条钉的是**输入侧**的大写归一：传 "BIG_BREASTS" 也该命中 "big_breasts"。
        // SQLite 的 `=` 大小写敏感，改造前后同判据 —— 库里若混进大写名，改造前也查不到。
        put("big_breasts", 0, "大胸")

        assertEquals("大胸", dictionary.rowsFor(listOf("BIG_BREASTS", "big_breasts")).single().cn)
    }

    @Test
    fun `词典里没有的名字不出现而不是补一行空的`() {
        put("known", 0, "已知")

        assertEquals(listOf("known"), dictionary.rowsFor(listOf("known", "unknown")).map { it.name })
    }

    @Test
    fun `空输入不碰库`() {
        assertEquals(0, dictionary.rowsFor(emptyList()).size)
        assertEquals(0, dictionary.rowsForInChunks(emptyList(), 2).size)
    }

    @Test
    fun `分块查询把整批名字都查回来且不超过给定批大小`() {
        // 一屏几十张卡摊平后能到上千个名字，而 `name IN (?,?,…)` 的占位符个数就是 SQLite 的
        // 变量上限：超限是直接抛异常，而外层把它吞成 null 就成了"所有卡都没标题"。
        (1..5).forEach { put("t$it", 0, "第${it}个") }

        val rows = dictionary.rowsForInChunks((1..5).map { "t$it" }, chunkSize = 2)

        assertEquals(5, rows.size)
        assertEquals((1..5).map { "第${it}个" }.sorted(), rows.mapNotNull { it.cn }.sorted())
    }

    @Test
    fun `分块大小为零直接抛而不是悄悄不发查询`() {
        try {
            dictionary.rowsForInChunks(listOf("t1"), chunkSize = 0)
            fail("批大小为 0 本该抛")
        } catch (e: IllegalArgumentException) {
            assertTrue("实际消息：${e.message}", e.message!!.contains("分块大小"))
        }
    }

    @Test
    fun `标签名里的引号当参数绑定而不是拼进 SQL`() {
        put("安全", 0, "正常行")

        // 站方数据里合法出现过引号与 `%`；这一条如果被拼进 SQL 就会把整表都查出来
        val rows = dictionary.rowsFor(listOf("' OR '1'='1"))
        assertEquals(0, rows.size)
        // 库里那一行没被动过
        assertEquals(1, dictionary.rowsFor(listOf("安全")).size)
    }
}
