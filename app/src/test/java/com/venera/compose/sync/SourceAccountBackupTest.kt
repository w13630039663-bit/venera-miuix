package com.venera.compose.sync

import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 归档里「漫画源账号」那一栏的判据。
 *
 * 这一栏的特殊性在于它**不是**用来复现屏上内容的，而是把登录凭据搬过设备 ——
 * 出错的形态有两种，都很安静：
 *  1. 打包时**多带了**不该带的（源设置混进"只导账号"那一档，或者干脆整栏不该出现却出现了）；
 *  2. 导入时**少带了**本该带的（键名对不上，恢复出来还是没登录）。
 * 两种都不会抛异常，所以逐条钉住。
 */
class SourceAccountBackupTest {

    // ── 编码 ──

    @Test
    fun `一行的键序固定，data 原样内嵌而不是转成字符串`() {
        val array = SourceAccountBackup.encode(
            listOf(
                SourceAccountBackup.Row(
                    fileName = "ehentai.js",
                    key = "ehentai",
                    name = "E-Hentai",
                    version = "1.4.2",
                    enabled = true,
                    pinned = false,
                    data = linkedMapOf("account" to "ok", "settings" to mapOf("domain" to "exhentai.org")),
                )
            )
        )
        // data 内嵌成对象（转字符串会让整份归档在文本编辑器里不可读，
        // 也让"少带了一个键"在肉眼层面藏起来）。
        assertEquals(
            """[{"fileName":"ehentai.js","key":"ehentai","name":"E-Hentai","version":"1.4.2",""" +
                """"enabled":true,"pinned":false,"data":{"account":"ok","settings":{"domain":"exhentai.org"}}}]""",
            array.toString(),
        )
    }

    @Test
    fun `空 data 写成空对象而不是省掉那个键`() {
        val array = SourceAccountBackup.encode(
            listOf(
                SourceAccountBackup.Row("x.js", "x", "X", "1.0", true, false, emptyMap())
            )
        )
        assertTrue(array.toString(), array.toString().endsWith(""""data":{}}]"""))
    }

    // ── 只导账号时摘掉设置 ──

    @Test
    fun `只导账号时设置留在本机，账号键一个不少`() {
        val data = mapOf(
            "account" to "ok",
            "token" to "abc",
            "_cookies" to listOf("a=1"),
            "_localStorage" to mapOf("accessToken" to "jwt"),
            "settings" to mapOf("domain" to "e-hentai.org"),
            "lastRead" to 42,
        )
        val filtered = SourceAccountBackup.filterData(data, SourceAccountBackup.Kind.ACCOUNT_ONLY)

        assertEquals(setOf("account", "token", "_cookies", "_localStorage"), filtered.keys)
    }

    @Test
    fun `全量导出时一个键都不动`() {
        val data = mapOf("account" to "ok", "settings" to mapOf("domain" to "e-hentai.org"), "lastRead" to 42)
        assertEquals(data, SourceAccountBackup.filterData(data, SourceAccountBackup.Kind.ALL))
    }

    @Test
    fun `localStorage 算账号 —— 网页登录的 accessToken 就存在那里`() {
        // 不算进去的话，ccc.js 这类源"登录成功了但 token 没带过来"，
        // 恢复后表现为仍是未登录，而没有任何异常指过来。
        assertTrue("_localStorage" in SourceAccountBackup.ACCOUNT_KEYS)
        val row = SourceAccountBackup.Row(
            "ccc.js", "ccc", "CCC", "1.0", true, false,
            mapOf("_localStorage" to mapOf("accessToken" to "jwt")),
        )
        assertTrue(row.hasAccount)
    }

    // ── 解码 .data 档 ──

    @Test
    fun `data 档里的数字按 plain gson 的口径收成 Double，嵌套对象与布尔按原类型解出`() {
        // 数字刻意是 Double 而不是 gson 的 LazilyParsedNumber：这份 Map 要交给
        // JsSourceDataStore 那只 plain gson 落盘，它自己解出来就是 Double。
        val map = SourceAccountBackup.decodeSourceData(
            """{"account":"ok","settings":{"page":2,"deep":{"flag":true}},"list":[1,"a"]}"""
        )
        assertEquals("ok", map["account"])
        @Suppress("UNCHECKED_CAST")
        val settings = map["settings"] as Map<String, Any?>
        assertEquals(2.0, settings["page"])
        @Suppress("UNCHECKED_CAST")
        assertEquals(true, (settings["deep"] as Map<String, Any?>)["flag"])
        @Suppress("UNCHECKED_CAST")
        assertEquals(listOf(1.0, "a"), map["list"] as List<Any?>)
    }

    @Test
    fun `坏掉的 data 档交回空对象而不是抛`() {
        // 这份档只承载登录态，而登录态是"重新登一次"就能恢复的损失；
        // 让它把整包导入拖垮才是真的亏。缺档与坏档在这里都收敛成"没有数据"。
        assertEquals(emptyMap<String, Any?>(), SourceAccountBackup.decodeSourceData(null))
        assertEquals(emptyMap<String, Any?>(), SourceAccountBackup.decodeSourceData(""))
        assertEquals(emptyMap<String, Any?>(), SourceAccountBackup.decodeSourceData("{不是 JSON"))
        assertEquals(emptyMap<String, Any?>(), SourceAccountBackup.decodeSourceData("[1,2,3]"))
    }

    // ── 解码归档数组 ──

    @Test
    fun `缺 member 与坏 member 是两件事`() {
        // 缺（null）= v6 之前的归档 / 没勾选导出 ⇒ 空列表，不是错误。
        assertEquals(emptyList<SourceAccountBackup.Row>(), SourceAccountBackup.decode(null))
        // 有但解不开 ⇒ 抛，由调用方在动任何存储之前失败。
        var threw = false
        try {
            SourceAccountBackup.decode("{不是数组}")
        } catch (e: Exception) {
            threw = true
        }
        assertTrue("坏 member 必须抛，不能静默当成没有这一栏", threw)
    }

    @Test
    fun `缺 fileName 的行跳过 —— 留着就是一条写不回任何源的幽灵登录态`() {
        val rows = SourceAccountBackup.decode(
            """[{"key":"a","data":{"account":"ok"}},{"fileName":"b.js","key":"b","data":{"account":"ok"}}]"""
        )
        assertEquals(1, rows.size)
        assertEquals("b.js", rows[0].fileName)
    }

    @Test
    fun `缺状态键时默认已启用而不是已停用`() {
        // 默认停用的话，恢复出来的源一个都不参与检索，用户看到的是
        // "导进来了但搜不到东西"。
        val rows = SourceAccountBackup.decode("""[{"fileName":"a.js","key":"a"}]""")
        assertEquals(1, rows.size)
        assertTrue(rows[0].enabled)
        assertFalse(rows[0].pinned)
        assertEquals(emptyMap<String, Any?>(), rows[0].data)
    }

    // ── 往返 ──

    @Test
    fun `编码再解码，data 逐字段一致`() {
        // 数字一律给 Double：这正是 decode 侧的口径（见上一条），
        // 拿 3L 当输入再断言相等，测的就是一个 decode 根本不会产出的形状。
        val original = listOf(
            SourceAccountBackup.Row(
                fileName = "copymanga.js",
                key = "copy_manga",
                name = "拷贝漫画",
                version = "2.1.0",
                enabled = false,
                pinned = true,
                data = linkedMapOf(
                    "account" to listOf("user", "pass"),
                    "settings" to mapOf("domain" to "copymanga.tv", "page" to 3.0),
                    "nested" to mapOf("a" to listOf(mapOf("b" to true))),
                ),
            )
        )
        val decoded = SourceAccountBackup.decode(SourceAccountBackup.encode(original).toString())
        assertEquals(original, decoded)
    }

    @Test
    fun `写回数据档的文本能被 gson 还原成同一份数据`() {
        // 归档里那份 data 的唯一归宿就是 JsSourceDataStore 的 gson，
        // 两边用同一个 gson 才谈得上"写回去的档跟原档同形"。
        val data = mapOf<String, Any?>(
            "account" to "ok",
            "settings" to mapOf("page" to 2.0, "flag" to true),
            "list" to listOf("a", 1.0),
        )
        val text = SourceAccountBackup.encodeSourceData(data)
        val parsed = JsonParser.parseString(text).asJsonObject
        assertEquals("ok", parsed.get("account").asString)
        assertEquals(2.0, parsed.getAsJsonObject("settings").get("page").asDouble, 0.0)
        assertEquals(true, parsed.getAsJsonObject("settings").get("flag").asBoolean)
        assertEquals(1.0, parsed.getAsJsonArray("list").get(1).asDouble, 0.0)
        // 数值不许被写成 `2.0` 之外的形状：plain gson 读回同一份 Map 才谈得上往返。
        assertEquals(
            """{"account":"ok","settings":{"page":2.0,"flag":true},"list":["a",1.0]}""",
            text,
        )
    }

    @Test
    fun `meta 计数键与 member 名稳定`() {
        // 这两个字面量是跨版本读的口径，改名即等于"读不到"，而不会报错。
        assertEquals("source_accounts.json", SourceAccountBackup.MEMBER)
        assertEquals("sourceAccountCount", SourceAccountBackup.META_COUNT_KEY)
    }
}
