package com.venera.desktop.platform

import com.venera.compose.data.platform.JsonKeyValueStore
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 键值门面的桌面实现判据：存得下、读得回、坏文件不许装成"没配置"。
 * 每条用例各占一个临时目录，跑完自己清掉，免得留下互相污染的读数。
 */
class JsonKeyValueStoreTest {
    private fun store(dir: File, name: String = "venera_preferences") =
        JsonKeyValueStore(name, FakePaths(dir))

    @Test fun `八种类型 round-trip`() {
        val dir = File(System.getProperty("java.io.tmpdir"), "r1f-kv-${System.nanoTime()}")
        val s = store(dir)
        s.put("s", "中文 值"); s.put("b", true); s.put("i", 42); s.put("l", 9_000_000_000L)
        s.put("f", 1.5f); s.put("set", setOf("a", "b"))
        val r = store(dir)   // 新实例 = 必须从磁盘重建
        assertEquals("中文 值", r.getString("s", null))
        assertTrue(r.getBoolean("b", false)); assertEquals(42, r.getInt("i", 0))
        assertEquals(9_000_000_000L, r.getLong("l", 0)); assertEquals(1.5f, r.getFloat("f", 0f))
        assertEquals(setOf("a", "b"), r.getStringSet("set"))
        dir.deleteRecursively()
    }

    @Test fun `删掉最后一个键后文件还在且该键取到默认值`() {
        val dir = File(System.getProperty("java.io.tmpdir"), "r1f-kv2-${System.nanoTime()}")
        store(dir).put("x", 1); store(dir).remove("x")
        assertEquals(7, store(dir).getInt("x", 7))
        dir.deleteRecursively()
    }

    @Test fun `半截 JSON 不许静默当成空表 —— 必须抛`() {
        val dir = File(System.getProperty("java.io.tmpdir"), "r1f-kv3-${System.nanoTime()}")
        store(dir).put("x", 1)
        File(dir, "prefs/venera_preferences.json").writeText("{\"x\": 1")   // 写坏
        assertTrue(runCatching { store(dir).getInt("x", 0) }.isFailure)
        dir.deleteRecursively()
    }
}
