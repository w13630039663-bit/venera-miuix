package com.venera.desktop.db

import com.venera.compose.data.db.CoreDbSchema
import com.venera.compose.data.db.GuardRuleStore
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * `content_guard_rules`（用户屏蔽规则）的读写（R18：真库）。
 *
 * 这一层坏掉的表现是"规则写了但列表里没有"或"已启用的规则其实永不生效"，
 * 所以逐列读回、显示顺序、删除是否命中都要钉住。命中判定本身在
 * `security/guard`（那颗还吃 Context，进不了桌面编译面），本轮不动它的既有口径。
 */
class GuardRuleStoreTest {

    private lateinit var store: TestDatabase
    private lateinit var rules: GuardRuleStore

    @Before
    fun setUp() {
        store = TestDatabase("guard")
        CoreDbSchema.ensureOn(store.db)
        rules = GuardRuleStore(store.source)
    }

    @After
    fun tearDown() = store.close()

    @Test
    fun `新增规则回报新行 rowid 并逐列读回`() {
        val id = rules.add("KEYWORD", "某作者", isRegex = false, createdAt = 1730000000000L)

        assertEquals(1L, id)
        val loaded = rules.loadAll()
        assertEquals(1, loaded.size)
        assertEquals(1L, loaded[0].id)
        assertEquals("KEYWORD", loaded[0].ruleType)
        assertEquals("某作者", loaded[0].pattern)
        assertFalse(loaded[0].isRegex)
        // is_enabled 由落库语句恒写 1（新规则一律启用，与改造前 ContentValues 的 put 一致）
        assertTrue(loaded[0].isEnabled)
    }

    @Test
    fun `整数 1 读成启用而 0 读成停用而不是拿非零当真`() {
        rules.add("TAG", "启用", isRegex = true, createdAt = 1L)
        store.db.exec(
            "INSERT INTO content_guard_rules (rule_type, pattern, is_regex, is_enabled, created_at)" +
                " VALUES ('TAG', '停用', 0, 0, 2)"
        )

        val loaded = rules.loadAll().associate { it.pattern to it }
        assertTrue(loaded.getValue("启用").isRegex)
        assertFalse(loaded.getValue("停用").isRegex)
        assertFalse(loaded.getValue("停用").isEnabled)
    }

    @Test
    fun `读回的顺序是新规则在前（屏蔽列表的显示顺序靠它）`() {
        rules.add("KEYWORD", "旧", isRegex = false, createdAt = 1L)
        rules.add("KEYWORD", "新", isRegex = false, createdAt = 2L)

        assertEquals(listOf("新", "旧"), rules.loadAll().map { it.pattern })
    }

    @Test
    fun `删除命中回报 true 且库里没有这条`() {
        val id = rules.add("COMIC_ID", "g1", isRegex = false, createdAt = 1L)

        assertTrue(rules.delete(id))
        assertEquals(0, rules.loadAll().size)
    }

    @Test
    fun `删除不存在的 id 回报 false 而不是假装删掉了`() {
        rules.add("COMIC_ID", "g1", isRegex = false, createdAt = 1L)

        assertFalse(rules.delete(999L))
        // 那条还在：误报"已删除"会让屏蔽列表上少一格而库里仍生效
        assertEquals(1, rules.loadAll().size)
    }

    @Test
    fun `中文与引号写在 pattern 里原样读回`() {
        val pattern = "带\"引号与\\反斜杠的关键词"
        rules.add("KEYWORD", pattern, isRegex = true, createdAt = 1L)

        assertEquals(pattern, rules.loadAll()[0].pattern)
    }
}
