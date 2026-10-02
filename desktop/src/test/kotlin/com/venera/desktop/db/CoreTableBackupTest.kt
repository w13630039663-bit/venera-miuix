package com.venera.desktop.db

import com.venera.compose.data.db.CoreDbSchema
import com.venera.compose.data.db.CoreTableBackup
import com.venera.compose.data.db.ReadingStatsStore
import com.venera.compose.data.db.intValue
import com.venera.compose.data.db.objectAt
import com.venera.compose.data.db.parseJsonArray
import com.venera.compose.data.db.textValue
import com.venera.compose.data.platform.SqlDatabase
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

/**
 * 备份那一层的**格式锚点**与往返用例。
 *
 * 为什么要有逐字节断言：`.venera` 归档要跨设备、跨版本读，`org.json` 换成 gson 换的是实现，
 * 不是格式。下面每条 `assertEquals(期望文本, 实际文本)` 的期望都是**按改造前 android
 * `JSONObject` / `JsonWriter` 的产出规则手写死的**（紧凑无空格、键序 = 列序/插入序、
 * 数字按 `Number.toString()`、控制符写成 `\u00XX` 小写十六进制、非 ASCII 原样输出、
 * `null` 出一个 `null` 字面量而不是省掉那个键），不是"先跑一遍把实际值抄进来"。
 */
class CoreTableBackupTest {

    private lateinit var store: TestDatabase
    private lateinit var db: SqlDatabase

    @Before
    fun setUp() {
        store = TestDatabase("backup")
        db = store.db
        CoreDbSchema.ensureOn(db)
    }

    @After
    fun tearDown() = store.close()

    // ── 导出侧：逐字节锚点 ──

    @Test
    fun `导出 reading_stats 一行的文本逐字节与改造前相同`() {
        ReadingStatsStore(store.source).insertSession(
            comicId = "a\"b\\c",           // 一个双引号 + 一个反斜杠 → \" 与 \\
            comicTitle = "中文标题",          // 非 ASCII 原样输出，不做 \u 转义
            sourceName = "<img>&'=/",      // 尖括号 / 与号 / 单引号 / 斜杠：两端都不做 html 转义
            tags = "女仆\u001F咖啡",         // TAG_SEPARATOR 是控制符 U+001F → 小写 \u001f
            chapterTitle = "第一话\n第二\t话", // 换行与制表符走短转义 \n \t
            pagesRead = 3,
            durationSeconds = 12,
            readDate = "2026-10-02",
            createdAt = 1_730_000_000_000L, // 大整数不许写成 1.73E12
        )

        assertEquals(
            "[{\"id\":1," +
                "\"comic_id\":\"a\\\"b\\\\c\"," +
                "\"comic_title\":\"中文标题\"," +
                "\"source_name\":\"<img>&'=/\"," +
                "\"tags\":\"女仆\\u001f咖啡\"," +
                "\"chapter_title\":\"第一话\\n第二\\t话\"," +
                "\"pages_read\":3," +
                "\"duration_seconds\":12," +
                "\"read_date\":\"2026-10-02\"," +
                "\"created_at\":1730000000000}]",
            CoreTableBackup.exportTable(db, "reading_stats").toString(),
        )
    }

    @Test
    fun `导出把 NULL 列写成 null 字面量而不是省掉那个键`() {
        // comic_history.author 建表时没带 NOT NULL，真机库里确实有 null 行。
        // 改造前那一支是 obj.put(name, JSONObject.NULL)：键要留着、值写 null，两者都不能少。
        db.exec(
            "INSERT INTO comic_history (comic_id, title, author, cover_url, source_name," +
                " last_chapter_title, last_chapter_index, last_page_index, total_pages, updated_at)" +
                " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
            "c1", "t", null, "", "s", "", 0, 1, 2, -5L,
        )

        assertEquals(
            "[{\"comic_id\":\"c1\",\"title\":\"t\",\"author\":null,\"cover_url\":\"\"," +
                "\"source_name\":\"s\",\"last_chapter_title\":\"\"," +
                "\"last_chapter_index\":0,\"last_page_index\":1,\"total_pages\":2," +
                "\"updated_at\":-5}]",
            CoreTableBackup.exportTable(db, "comic_history").toString(),
        )
    }

    @Test
    fun `导出把 REAL 值原样写出小数点而不是收成整数`() {
        // duration_seconds 是 INTEGER 亲和列，塞 1.5 进去 SQLite 会保留 REAL（不做无损转换）；
        // 改造前 getDouble → 1.5 → 文本 "1.5"。
        db.exec(
            "INSERT INTO reading_stats (comic_id, comic_title, source_name, tags, chapter_title," +
                " pages_read, duration_seconds, read_date, created_at) VALUES (?,?,?,?,?,?,?,?,?)",
            "c1", "t", "s", "", "", 3, 1.5, "2026-10-02", 1L,
        )

        val text = CoreTableBackup.exportTable(db, "reading_stats").toString()
        assertTrue("实际文本：$text", text.contains("\"duration_seconds\":1.5"))
    }

    @Test
    fun `导出的行序就是扫描顺序且每行都进数组`() {
        val stats = ReadingStatsStore(store.source)
        stats.insertSession("c1", "t1", "s", "", "", 1, 10, "2026-10-01", 1L)
        stats.insertSession("c2", "t2", "s", "", "", 2, 20, "2026-10-02", 2L)

        val array = CoreTableBackup.exportTable(db, "reading_stats")
        assertEquals(2, array.size())
        assertEquals("c1", array.objectAt(0).textValue("comic_id"))
        assertEquals("c2", array.objectAt(1).textValue("comic_id"))
    }

    // ── 恢复侧：固定文本 → 落库 → 再导出逐字段对照 ──

    @Test
    fun `恢复历史行后再导出与原文逐字节相同`() {
        val json = "[{\"comic_id\":\"c1\",\"title\":\"本子\",\"author\":\"作者\",\"cover_url\":\"\"," +
            "\"source_name\":\"jm\",\"last_chapter_title\":\"第1话\",\"last_chapter_index\":1," +
            "\"last_page_index\":2,\"total_pages\":3,\"updated_at\":1730000000000}]"

        val restored = TestDatabase("history-restore")
        try {
            CoreDbSchema.ensureOn(restored.db)
            assertEquals(1, CoreTableBackup.importHistory(restored.db, parseJsonArray(json)))
            assertEquals(json, CoreTableBackup.exportTable(restored.db, "comic_history").toString())
        } finally {
            restored.close()
        }
    }

    @Test
    fun `恢复历史时 null 作者按改造前写成空串`() {
        // 这是改造前就有的不对称：optString("author") 把 null 读成 ""，
        // 于是"没填作者"与"作者真是 null"在恢复后长一样。本轮不改良它（改了会让旧备份的
        // 恢复结果与新版不一致），只把它钉在用例上，免得日后被当成 bug 顺手"修"成 null。
        val json = "[{\"comic_id\":\"c1\",\"title\":\"t\",\"author\":null,\"cover_url\":\"\"," +
            "\"source_name\":\"s\",\"last_chapter_title\":\"\",\"last_chapter_index\":0," +
            "\"last_page_index\":0,\"total_pages\":0,\"updated_at\":1}]"

        val restored = TestDatabase("history-null-author")
        try {
            CoreDbSchema.ensureOn(restored.db)
            CoreTableBackup.importHistory(restored.db, parseJsonArray(json))
            val exported = CoreTableBackup.exportTable(restored.db, "comic_history").toString()
            assertEquals(
                "[{\"comic_id\":\"c1\",\"title\":\"t\",\"author\":\"\",\"cover_url\":\"\"," +
                    "\"source_name\":\"s\",\"last_chapter_title\":\"\",\"last_chapter_index\":0," +
                    "\"last_page_index\":0,\"total_pages\":0,\"updated_at\":1}]",
                exported,
            )
        } finally {
            restored.close()
        }
    }

    @Test
    fun `恢复统计行保留原 created_at 与 read_date 并逐列落库`() {
        val json = "[{\"id\":9,\"comic_id\":\"c1\",\"comic_title\":\"本子\",\"source_name\":\"jm\"," +
            "\"tags\":\"女仆\u001F咖啡\",\"chapter_title\":\"第1话\",\"pages_read\":3," +
            "\"duration_seconds\":12,\"read_date\":\"2026-10-02\",\"created_at\":1730000000000}]"

        val restored = TestDatabase("stats-restore")
        try {
            CoreDbSchema.ensureOn(restored.db)
            assertEquals(1, CoreTableBackup.importStats(restored.db, parseJsonArray(json)))
            // 归档里那个本机自增 id 不进语句（列清单里没有它），新库自己发号；其余七列逐字回来
            assertEquals(
                "[{\"id\":1," +
                    "\"comic_id\":\"c1\",\"comic_title\":\"本子\",\"source_name\":\"jm\"," +
                    "\"tags\":\"女仆\\u001f咖啡\",\"chapter_title\":\"第1话\",\"pages_read\":3," +
                    "\"duration_seconds\":12,\"read_date\":\"2026-10-02\",\"created_at\":1730000000000}]",
                CoreTableBackup.exportTable(restored.db, "reading_stats").toString(),
            )
            // 恢复完的行能被统计页那条读法原样读回（页数/时长/标签一个不差）
            val rows = ReadingStatsStore(restored.source).taggedRows("2026-10-01")
            assertEquals(1, rows.size)
            assertEquals(3, rows[0].pages)
            assertEquals("女仆\u001F咖啡", rows[0].tags)
        } finally {
            restored.close()
        }
    }

    @Test
    fun `恢复屏蔽规则跳过编译不过的正则并如实计数`() {
        // 三条：好正则收、坏正则不收并计入 skipped、非正则照收；
        // is_enabled 缺省那一条按改造前 `optInt("is_enabled", 1)` 落成"启用"。
        val json = "[{\"rule_type\":\"KEYWORD\",\"pattern\":\"好\",\"is_regex\":1,\"is_enabled\":1,\"created_at\":10}," +
            "{\"rule_type\":\"TAG\",\"pattern\":\"坏\",\"is_regex\":1,\"created_at\":20}," +
            "{\"rule_type\":\"AUTHOR\",\"pattern\":\"某某\",\"is_regex\":0,\"is_enabled\":0,\"created_at\":30}]"

        val restored = TestDatabase("guard-restore")
        try {
            CoreDbSchema.ensureOn(restored.db)
            val outcome = CoreTableBackup.importGuardRules(restored.db, parseJsonArray(json)) { it != "坏" }
            assertEquals(2, outcome.imported)
            assertEquals(1, outcome.skipped)

            val rules = com.venera.compose.data.db.GuardRuleStore(restored.source).loadAll()
            assertEquals(2, rules.size)
            // ORDER BY id DESC：后写入的那条在前
            assertEquals("AUTHOR", rules[0].ruleType)
            assertEquals(false, rules[0].isEnabled)
            assertEquals("KEYWORD", rules[1].ruleType)
            assertEquals(true, rules[1].isRegex)
            assertEquals(true, rules[1].isEnabled)
        } finally {
            restored.close()
        }
    }

    // ── 读法的抛点：缺字段、类型不符、数组元素不是对象 ──

    @Test
    fun `恢复时缺字段当场抛出并点名缺哪一个`() {
        val rows = parseJsonArray("[{\"comic_id\":\"c1\"}]")
        val restored = TestDatabase("history-missing")
        try {
            CoreDbSchema.ensureOn(restored.db)
            CoreTableBackup.importHistory(restored.db, rows)
            fail("缺 title 的行本该抛")
        } catch (e: IllegalStateException) {
            assertTrue("实际消息：${e.message}", e.message!!.contains("title"))
            // 一笔都没写进去（抛在第一行，事务还没提交）
            assertEquals(0, CoreTableBackup.exportTable(restored.db, "comic_history").size())
        } finally {
            restored.close()
        }
    }

    @Test
    fun `恢复时数字字段是字符串就抛而不是当零用`() {
        // 对齐改造前 JSONObject.getInt：值必须是 JSON 数字，"5" 这种串要抛。
        val rows = parseJsonArray(
            "[{\"comic_id\":\"c1\",\"title\":\"t\",\"author\":\"\",\"cover_url\":\"\"," +
                "\"source_name\":\"s\",\"last_chapter_title\":\"\",\"last_chapter_index\":\"1\"," +
                "\"last_page_index\":0,\"total_pages\":0,\"updated_at\":1}]"
        )
        val restored = TestDatabase("history-string-int")
        try {
            CoreDbSchema.ensureOn(restored.db)
            CoreTableBackup.importHistory(restored.db, rows)
            fail("last_chapter_index 是字符串，本该抛")
        } catch (e: IllegalStateException) {
            assertTrue("实际消息：${e.message}", e.message!!.contains("last_chapter_index"))
        } finally {
            restored.close()
        }
    }

    @Test
    fun `恢复时小数字段按改造前截断而不是四舍五入`() {
        val rows = parseJsonArray(
            "[{\"comic_id\":\"c1\",\"title\":\"t\",\"author\":\"\",\"cover_url\":\"\"," +
                "\"source_name\":\"s\",\"last_chapter_title\":\"\",\"last_chapter_index\":1.7," +
                "\"last_page_index\":0,\"total_pages\":0,\"updated_at\":1}]"
        )
        val restored = TestDatabase("history-double-int")
        try {
            CoreDbSchema.ensureOn(restored.db)
            CoreTableBackup.importHistory(restored.db, rows)
            // 改造前是 ((Number) 1.7).intValue() = 1；四舍五入成 2 会把阅读进度整体前进一页
            val index = CoreTableBackup.exportTable(restored.db, "comic_history")
                .objectAt(0).intValue("last_chapter_index")
            assertEquals(1, index)
        } finally {
            restored.close()
        }
    }

    @Test
    fun `数组里混进非对象元素当场抛出`() {
        val rows = parseJsonArray("[\"不是对象\"]")
        try {
            rows.objectAt(0)
            fail("本该抛")
        } catch (e: IllegalStateException) {
            assertTrue("实际消息：${e.message}", e.message!!.contains("不是 JSON object"))
        }
    }
}
