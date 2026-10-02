package com.venera.compose.data.db

import com.venera.compose.data.platform.SqlDatabase

/**
 * 画廊离线词典（`tags` 表那份只读库）的读法。
 *
 * 这张表**不是**本应用维护的库：它由 `scripts/build_tag_dictionaries.mjs` 从上游
 * Danbooru 译名表截出来，随包成 `assets/gallery_tags_*.sqlite`，首次使用时复制到
 * `databases/` 再只读打开（打开那一步在 `gallery/data/GalleryTagDictionary`，那需要 Context
 * 的 assets，所以留在 Android 侧）。这一层只认已打开的只读连接，因而能在 `:desktop:test`
 * 里拿真库跑「按名查 → 逐列读回 → 分块不超变量上限」。
 *
 * 列的形状照构建脚本那句 `CREATE TABLE tags (name TEXT PRIMARY KEY, category INTEGER, cn TEXT)`：
 * `name` 是主键（`WITHOUT ROWID`，实际强制非空）⇒ 走 [requiredString]；
 * `category` / `cn` 建表时没带 `NOT NULL`，但它们是构建脚本逐行写入的，读出 null 时
 * `category` 按改造前的 `getInt` 记 0（未知档位，调用方本来就不认 0 以外的兜底），
 * `cn` 交 null（"这条没有中文译名"是合法状态，不是故障）。
 *
 * 两条沿用改造前的口径：**不拼 SQL**（标签名是站方给的外部数据，里面有引号与 `%`），
 * 以及一次 `name IN (?,...)` 的占位符个数就是 SQLite 的变量上限 —— 超了是抛异常而不是少查几个，
 * 所以按 [rowsForInChunks] 切批。
 */
class TagDictionaryStore(private val dictionary: SqlDatabase) {

    /**
     * 这批名字的词典行。
     *
     * 名字一律先转小写再去重：词典的 `name` 存的是小写标识符，而调用方递来的标签可能带大写。
     * 库里没有的名字**不出现在结果里**（调用方据此原样显示），这与"一条都没查到"是同一件事，
     * 所以这里不返回 null —— 库打不开那种"我们的故障"由调用方区分。
     */
    fun rowsFor(names: List<String>): List<TagDictRow> {
        val targets = names.map { it.lowercase() }.distinct()
        return if (targets.isEmpty()) emptyList() else query(targets)
    }

    /**
     * [rowsFor] 的分块版本：每批最多 [chunkSize] 个名字。
     *
     * 一屏几十张卡摊平去重后能到上千个名字，而 `name IN (?,?,…)` 的占位符个数就是
     * SQLite 的变量上限（老版本 999）。超限不是"少查几个"，是直接抛异常。
     */
    fun rowsForInChunks(names: List<String>, chunkSize: Int): List<TagDictRow> {
        require(chunkSize > 0) { "分块大小必须为正，实际是 $chunkSize" }
        val targets = names.map { it.lowercase() }.distinct()
        return targets.chunked(chunkSize).flatMap { query(it) }
    }

    private fun query(targets: List<String>): List<TagDictRow> {
        val placeholders = targets.joinToString(",") { "?" }
        return dictionary.query(
            "SELECT name, category, cn FROM tags WHERE name IN ($placeholders)",
            *targets.toTypedArray(),
        ).map { row ->
            TagDictRow(
                name = row.requiredString("name").lowercase(),
                category = row.long("category").toInt(),
                cn = row.string("cn"),
            )
        }
    }
}

/** 词典里的一行：小写标签名、站方档位、中文译名（没有译名时为 null）。 */
data class TagDictRow(
    val name: String,
    val category: Int,
    val cn: String?,
)
