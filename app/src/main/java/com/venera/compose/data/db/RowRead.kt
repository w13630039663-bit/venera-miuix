package com.venera.compose.data.db

import com.venera.compose.data.platform.SqlRow

/**
 * `data/db` 的读列口径。
 *
 * 两套读法，分工写死在这里，谁也不许拿另一套凑合：
 *  - [requiredString]：建表语句里就 `NOT NULL` 的列。读出 null 说明表和代码对不上，
 *    直接抛并带列名；列本身不存在时 [SqlRow.string] 也会抛（同一套口径）。
 *  - [optString] / [optInt] / [optLongOrNull] / [optLongOrZero]：改造前就用
 *    `Cursor.getColumnIndex(...) < 0` 容忍「列不存在」的那几处（`local_favorite.db` 的追更三列
 *    是运行时按需 ALTER 的，缺列是合法状态）。缺列的默认值逐条照抄改造前的行为，
 *    没有放宽：值本身读不成对应类型（例如 TEXT 里塞了非数字）时 [SqlRow.long] 仍会抛。
 */

/** NOT NULL 列的文本读法：null 即结构不符，抛，不许扮成空串。 */
internal fun SqlRow.requiredString(column: String): String =
    string(column) ?: throw IllegalStateException(
        "列「$column」按建表语句是 NOT NULL，实际读出 null —— 表结构与代码预期不符"
    )

/** 对应改造前的 `getString(idx) ?: ""`（含列缺失时的 `""`）。 */
internal fun SqlRow.optString(column: String): String =
    if (!has(column)) "" else (string(column) ?: "")

/** 对应改造前的 `optInt`：列缺失 0、值为 NULL 时 `Cursor.getInt` 也是 0。 */
internal fun SqlRow.optInt(column: String): Int =
    if (!has(column)) 0 else long(column).toInt()

/** 对应改造前的 `optLong`：列缺失或值为 NULL 都给 null（用它区分「没检查过」与「检查过」）。 */
internal fun SqlRow.optLongOrNull(column: String): Long? =
    if (!has(column)) null else if (isNull(column)) null else long(column)

/** 对应迁移路径上的 `optLongRaw`：列缺失给 0L，与改造前一致。 */
internal fun SqlRow.optLongOrZero(column: String): Long =
    if (!has(column)) 0L else long(column)
