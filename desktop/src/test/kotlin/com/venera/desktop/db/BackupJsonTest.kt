package com.venera.desktop.db

import com.venera.compose.data.db.asMap
import com.venera.compose.data.db.jsonArray
import com.venera.compose.data.db.jsonObject
import com.venera.compose.data.db.jsonObjectOf
import com.venera.compose.data.db.longValue
import com.venera.compose.data.db.optIntValue
import com.venera.compose.data.db.optLongValue
import com.venera.compose.data.db.optTextValue
import com.venera.compose.data.db.parseJsonArray
import com.venera.compose.data.db.parseJsonObject
import com.venera.compose.data.db.textValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * 备份 JSON 那一层的**格式锚点**：写死的期望文本是按改造前 android `JSONObject.toString()`
 * 的规则一条条手算出来的（紧凑无空格、键序 = 给定顺序、数字按 `Number.toString()`、
 * `null` 出一个 `null` 字面量、非 ASCII 不转义、`"`/`\`/控制符按同一套转义表）。
 *
 * 为什么单独一层测：`BackupManager` 那一半要 Context 才跑得动，真机上点一次导出只能证明
 * "能读回来"；而归档格式坏掉的表现恰好是"另一台机器/另一个版本读不回原来那份"，
 * 只有逐字节断言能挡住。
 */
class BackupJsonTest {

    @Test
    fun `meta 那一栏的文本逐字节锚点`() {
        // 键序与改造前逐次 put 的顺序一致：version,timestamp,app,historyCount,...,galleryFavoriteCount
        val text = jsonObject(
            listOf(
                "version" to 5,
                "timestamp" to 1_730_000_000_000L,
                "app" to "Venera Compose",
                "historyCount" to 12,
                "favoriteCount" to 0,
            )
        ).toString()

        assertEquals(
            "{\"version\":5,\"timestamp\":1730000000000,\"app\":\"Venera Compose\"," +
                "\"historyCount\":12,\"favoriteCount\":0}",
            text,
        )
    }

    @Test
    fun `一批行的文本锚点含嵌套数组与 null`() {
        // 收藏那几栏的行形状：tags 是数组（改造前 JSONArray 也是 List，导出的就是 ["a","b"]）、
        // displayOrder 是整数、作者为空串时仍然是 ""（不许省掉键）。
        val text = jsonArray(
            listOf(
                linkedMapOf<String, Any?>(
                    "folder" to "默认",
                    "id" to "g1",
                    "tags" to listOf("a", "b"),
                    "author" to "",
                    "displayOrder" to 0,
                ),
                linkedMapOf<String, Any?>(
                    "folder" to "带\"引号\\反斜杠",
                    "id" to "g2",
                    "tags" to emptyList<String>(),
                    "author" to null,
                    "displayOrder" to -1,
                ),
            )
        ).toString()

        assertEquals(
            "[{\"folder\":\"默认\",\"id\":\"g1\",\"tags\":[\"a\",\"b\"],\"author\":\"\"," +
                "\"displayOrder\":0}," +
                "{\"folder\":\"带\\\"引号\\\\反斜杠\",\"id\":\"g2\",\"tags\":[],\"author\":null," +
                "\"displayOrder\":-1}]",
            text,
        )
    }

    @Test
    fun `插图收藏那一行的七个字段逐字节锚点`() {
        val text = jsonObjectOf(
            linkedMapOf<String, Any?>(
                "comic_id" to "g1",
                "comic_title" to "本子",
                "source_name" to "jm",
                "chapter_title" to "第1话",
                "page_index" to 3,
                "image_url" to "https://a/b?c=1&d=2",
                "created_at" to 1_730_000_000_000L,
            )
        ).toString()

        assertEquals(
            "{\"comic_id\":\"g1\",\"comic_title\":\"本子\",\"source_name\":\"jm\"," +
                "\"chapter_title\":\"第1话\",\"page_index\":3," +
                "\"image_url\":\"https://a/b?c=1&d=2\",\"created_at\":1730000000000}",
            text,
        )
    }

    @Test
    fun `控制符与制表符的转义表与改造前一致`() {
        val text = jsonObjectOf(mapOf("tags" to "女仆\u001F咖啡\u0001\n\t\"\\")).toString()
        assertEquals(
            "{\"tags\":\"女仆\\u001f咖啡\\u0001\\n\\t\\\"\\\\\"}",
            text,
        )
    }

    @Test
    fun `解析只认严格 JSON 并拒绝尾部多余内容`() {
        // 改造前 JSONArray(text) 对这些形态一律抛 JSONException；gson 默认宽容，
        // 所以这里显式关掉 lenient —— 半截/手工拼的归档不能被读成合法数据。
        listOf("[1, 2,]", "{a:1}", "['x']", "[1] [2]", "NaN").forEach { bad ->
            try {
                parseJsonArrayOrObject(bad)
                fail("这份内容本该抛：$bad")
            } catch (e: IllegalStateException) {
                assertTrue("实际消息：${e.message}", e.message!!.contains("JSON"))
            }
        }
    }

    private fun parseJsonArrayOrObject(text: String) {
        if (text.startsWith("[")) parseJsonArray(text) else parseJsonObject(text)
    }

    @Test
    fun `member 不是数组或不是对象时点名形状`() {
        try {
            parseJsonArray("{}")
            fail("本该抛")
        } catch (e: IllegalStateException) {
            assertTrue(e.message!!.contains("不是 JSON 数组"))
        }
        try {
            parseJsonObject("[]")
            fail("本该抛")
        } catch (e: IllegalStateException) {
            assertTrue(e.message!!.contains("不是 JSON object"))
        }
    }

    @Test
    fun `读法与改造前的 getX 一样对缺键与 null 抛`() {
        val obj = parseJsonObject("{\"a\":\"x\",\"b\":null,\"c\":1.5,\"d\":\"5\"}")

        assertEquals("x", obj.textValue("a"))
        // getString 对 null 值抛（改造前 JSON.toString 给 null 后走 wrongValue）
        assertThrowsMessageContains({ obj.textValue("b") }, "b")
        assertThrowsMessageContains({ obj.longValue("d") }, "d") // 数字串不算数字，与 getInt 一致
        // 小数按 Number.longValue() 截断（改造前 ((Number)v).intValue() 就是这个语义）
        assertEquals(1L, obj.longValue("c"))
        assertThrowsMessageContains({ obj.textValue("zz") }, "zz")
    }

    @Test
    fun `opt 读法的默认值与改造前逐条一致`() {
        val obj = parseJsonObject("{\"a\":2,\"b\":\"3.7\",\"c\":true,\"d\":null}")

        assertEquals("2", obj.optTextValue("a"))          // 数字也按 JSON.toString 取文本
        assertEquals("", obj.optTextValue("d"))           // null → fallback
        assertEquals("fallback", obj.optTextValue("zz", "fallback"))
        assertEquals(2L, obj.optLongValue("a", 0L))
        assertEquals(3L, obj.optLongValue("b", 0L))       // 数字串认（android optLong 认）
        assertEquals(1L, obj.optLongValue("c", 0L))       // 布尔 → 1/0
        assertEquals(9L, obj.optLongValue("d", 9L))       // null → fallback
        assertEquals(9L, obj.optLongValue("zz", 9L))
        assertEquals(3, obj.optIntValue("b", 0))
        assertEquals(0, obj.optIntValue("zz", 0))
    }

    @Test
    fun `JsonObject 转回普通 Map 时 null 与嵌套都按原形状`() {
        val map = asMap(parseJsonObject("{\"s\":\"x\",\"n\":null,\"i\":5,\"f\":1.5,\"arr\":[1,\"two\"],\"obj\":{\"k\":true}}"))

        assertEquals("x", map["s"])
        assertNull(map["n"])
        assertEquals(5L, map["i"])
        assertEquals(1.5, map["f"])
        // 这条是**改对了**的一处：改造前 tags 交回来的是 JSONArray（它本身是 List），
        // gson 的 JsonArray 不是 List，所以这里必须显式转成 List，
        // 否则 FavoriteBackupRows.strings("tags") 会走 else 分支交回空表（收藏恢复完就没标签了）。
        assertEquals(listOf(1L, "two"), map["arr"])
        assertEquals(true, (map["obj"] as Map<*, *>)["k"])
        assertFalse(map.containsKey("zz"))
    }

    private fun assertThrowsMessageContains(block: () -> Unit, needle: String) {
        try {
            block()
            fail("本该抛出并带上「$needle」")
        } catch (e: IllegalStateException) {
            assertTrue("实际消息：${e.message}", e.message!!.contains(needle))
        }
    }
}
