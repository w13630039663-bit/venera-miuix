package com.venera.compose.data.db

/**
 * `content_guard_rules`（用户屏蔽规则）的读写层。
 *
 * 存在的理由同 [ReadingStatsStore]：这一层不碰 Android 类型，`:desktop:test` 能拿真库跑
 * 「写入 → 读回逐列 → 删除精确命中」，而调用方 `security/guard/ContentGuardManager` 那边
 * 才需要 Context（assets 里的源分级预设表、偏好、StateFlow 缓存）。
 *
 * 逐列对过 `SchemaSql` 的建表语句：`rule_type` / `pattern` / `created_at` 是 `NOT NULL`
 * ⇒ 文本走 [requiredString]；`is_regex` / `is_enabled` 是带默认值的 `NOT NULL INTEGER`
 * ⇒ 按改造前的 `getInt(idx) == 1` 读成布尔（读出别的整数就当 false，与改造前一致）。
 *
 * 返回值用的是这一层自己的 [GuardRuleRow]，不是 `security.guard.GuardRule`：
 * 后者与判定逻辑同处一颗 Android 侧的文件里，这一层不许朝那个方向依赖。
 */
class GuardRuleStore(private val source: SqlDatabaseSource) {

    /** 全部规则，新→旧（改造前的 `ORDER BY id DESC`，屏蔽列表的显示顺序就靠它）。 */
    fun loadAll(): List<GuardRuleRow> =
        source.reader().query(
            "SELECT id, rule_type, pattern, is_regex, is_enabled FROM content_guard_rules ORDER BY id DESC"
        ).map { row ->
            GuardRuleRow(
                id = row.long("id"),
                ruleType = row.requiredString("rule_type"),
                pattern = row.requiredString("pattern"),
                isRegex = row.long("is_regex").toInt() == 1,
                isEnabled = row.long("is_enabled").toInt() == 1,
            )
        }

    /** 新增一条规则，回报新行 rowid（调用方拿 `>= 0` 判成败，与改造前同一判据）。 */
    fun add(ruleType: String, pattern: String, isRegex: Boolean, createdAt: Long): Long =
        source.writer().insert(
            INSERT_GUARD_RULE, ruleType, pattern, if (isRegex) 1 else 0, 1, createdAt
        )

    /** 删一条，回报**库里本来有没有这一条**（`exec` 不回报行数，判据同 [FavoriteImagesStore.deleteByIds]）。 */
    fun delete(id: Long): Boolean {
        val db = source.writer()
        val existed = db.query(
            "SELECT id FROM content_guard_rules WHERE id = ? LIMIT 1", id
        ).isNotEmpty()
        db.exec("DELETE FROM content_guard_rules WHERE id = ?", id)
        return existed
    }

    companion object {
        /**
         * 列序与改造前 `ContentValues` 的 put 顺序一致。
         * [CoreTableBackup] 恢复 `guard_rules.json` 用的也是这一条，列名只有一处定义。
         */
        internal const val INSERT_GUARD_RULE =
            "INSERT INTO content_guard_rules (rule_type, pattern, is_regex, is_enabled, created_at) " +
                "VALUES (?, ?, ?, ?, ?)"
    }
}

/** 一条屏蔽规则在库里的那五行。 */
data class GuardRuleRow(
    val id: Long,
    val ruleType: String,
    val pattern: String,
    val isRegex: Boolean,
    val isEnabled: Boolean,
)
