package com.venera.compose.data.db

import com.venera.compose.data.platform.SqlDatabase
import com.venera.compose.data.platform.quoteIdentifier

/**
 * 两个核心库的**版本 → 该跑哪几条语句**的编排，两端共用这一份（R20）。
 *
 * 改造前这套编排住在 Android 的 `SQLiteOpenHelper` 回调里（`VeneraDatabase.onCreate` /
 * `onUpgrade`），桌面侧根本没有 helper，只能另写一份 —— 那正是这次要拆掉的耦合。
 * 现在的分工：
 *  - 本文件：唯一的编排（版本号、建表全集、v2 DROP 重建、v3 补表、`PRAGMA user_version` 读写）；
 *  - [VeneraDatabase]（Android 接线处）：只把 helper 回调转给 [CoreDbSchema]；
 *  - 桌面 / JVM 测试：调 [ensureOn]，同一个入口、同一套语义。
 *
 * 语句文本的唯一出处仍是 [SchemaSql]（venera_core 那 21 条逐字原文）；本文件只补
 * `local_favorite.db` 的 DDL —— 它原本就写在这份编排要接管的 `LocalFavoriteDatabase.onCreate` 里，
 * 搬到这里是为了让桌面也能建同一个库，一字未改。
 */
interface SchemaOps {
    /** 代码期望的库版本，写进 `PRAGMA user_version`。 */
    val version: Int

    /** 全新库建表。 */
    fun create(db: SqlDatabase)

    /** 已有库从 [oldVersion] 升到 [newVersion]。 */
    fun upgrade(db: SqlDatabase, oldVersion: Int, newVersion: Int)

    /**
     * 两端统一入口：按 `PRAGMA user_version` 决定建表、升级还是什么都不做。
     *
     * 判据与 Android `SQLiteOpenHelper` 完全一致 —— `user_version == 0` 即新库走 create，
     * 否则只在低于目标版本时走 upgrade；库版本比代码还新（回滚安装、多版本共存）时抛，
     * 不装作"降级成功"。只有版本真的变了才回写 `user_version`，避免每次打开都写盘。
     */
    fun ensureOn(db: SqlDatabase) {
        val current = userVersion(db)
        when {
            current == 0 -> create(db)
            current < version -> upgrade(db, current, version)
            current > version -> throw IllegalStateException(
                "库版本 $current 高于代码期望的 $version，拒绝降级打开（$name）"
            )
        }
        if (current != version) setUserVersion(db, version)
    }

    /** 报错文案里的库名。 */
    val name: String
}

/** `PRAGMA user_version` 的读；空结果 = 0（与 SQLite 自身的空库读数一致）。 */
fun userVersion(db: SqlDatabase): Int {
    val rows = db.query("PRAGMA user_version")
    if (rows.isEmpty()) return 0
    return rows.first().long("user_version").toInt()
}

/**
 * `PRAGMA user_version` 的写。
 *
 * PRAGMA 不接受 `?` 绑定参数，所以这里是字符串拼接 —— 但拼的是本模块自己持有的 `Int` 常量
 * （不是任何用户输入），并且先校验为正整数，注入面无从成立。
 */
fun setUserVersion(db: SqlDatabase, version: Int) {
    require(version > 0) { "user_version 必须是正整数，收到 $version" }
    db.exec("PRAGMA user_version = $version")
}

/**
 * `venera_core.db`（阅读历史 / 旧单表收藏 / 漫画源元数据）的建表与迁移。
 *
 * [upgrade] 是原 `VeneraDatabase.onUpgrade` 的逐字翻译，**包括那条 `return`**：
 * v1 起的库走 DROP 重建分支后直接返回，不再走 v3 那段补表。这条 `return` 在**可观察层面
 * 没有差异** —— v2 分支里 `create(db)` 跑的就是 `SchemaSql.V1_CREATE_STATEMENTS`，它本身
 * 已含 v3 那三张表（reading_stats / favorite_images / content_guard_rules），DROP 只删老三张，
 * 重跑建表全集后三张表照样在库中。逐字保留它只是为了不偷改编排结构（本轮口径：搬，不改）。
 *
 * 由此推出一条约束：**若将来有人把这三张表移出 `V1_CREATE_STATEMENTS`**（例如想让 v2 分支
 * 真的"不含 v3 表"），`CoreDbMigrationTest` 的「v1 升 v2 走 DROP 重建且保留提前 return 的
 * 既有语义」必须先失败 —— 那条用例连同本注释就是这条假设的哨兵。
 * 另注：三个表的管理器（`FavoriteImagesManager` / `ReadingStatsManager` / `ContentGuardManager`）
 * **没有任何自建表 DDL**（全项目 `CREATE TABLE` 只出现在本文件与 [SchemaSql]），
 * 它们从来不做"兜底建表"，别把这条写进任何假设里。
 */
object CoreDbSchema : SchemaOps {
    const val DATABASE_NAME = "venera_core.db"

    // v3: 新增阅读统计 (reading_stats)、单页收藏 (favorite_images)、内容屏蔽 (content_guard_rules)
    const val DATABASE_VERSION = 3

    override val name: String get() = DATABASE_NAME
    override val version: Int get() = DATABASE_VERSION

    override fun create(db: SqlDatabase) {
        // 6 张建表语句 + 索引，逐字存于 SchemaSql（与 :desktop 共享同一棵源），按原顺序执行
        SchemaSql.V1_CREATE_STATEMENTS.forEach { db.exec(it) }
    }

    override fun upgrade(db: SqlDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            // v2：DROP 重建语义不变——先删三张旧表，再跑一遍建表全集
            SchemaSql.V2_DROP_STATEMENTS.forEach { db.exec(it) }
            create(db)
            return
        }
        if (oldVersion < 3) {
            // v3：补建 reading_stats / favorite_images / content_guard_rules 三张表及索引
            SchemaSql.V3_CREATE_STATEMENTS.forEach { db.exec(it) }
        }
    }
}

/**
 * `local_favorite.db`（「一个收藏夹一张表」那套）的建表与迁移。
 *
 * v1 为首个版本，原 `LocalFavoriteDatabase.onUpgrade` 是空的，这里同样留空 —— 没有历史数据要迁。
 */
object LocalFavoriteDbSchema : SchemaOps {
    const val DATABASE_NAME = "local_favorite.db"
    const val DATABASE_VERSION = 1

    override val name: String get() = DATABASE_NAME
    override val version: Int get() = DATABASE_VERSION

    /** 收藏夹元表：排序与网络源绑定。列定义、语句顺序与原 onCreate 逐条一致。 */
    private val META_TABLE_STATEMENTS = listOf(
        """
        CREATE TABLE IF NOT EXISTS folder_order (
            folder_name TEXT PRIMARY KEY,
            order_value INTEGER
        );
        """.trimIndent(),
        """
        CREATE TABLE IF NOT EXISTS folder_sync (
            folder_name TEXT PRIMARY KEY,
            source_key TEXT,
            source_folder TEXT
        );
        """.trimIndent(),
    )

    /**
     * 收藏夹表建表语句。表名即文件夹名、来自用户输入，必须过 [quoteIdentifier]
     * （原版 Dart 直接字符串插值，有注入风险，这里不照搬）。
     *
     * 追更三列 `last_update_time / has_new_update / last_check_time` 建表时**不带**，
     * 由 [LocalFavoriteDatabase.prepareTableForFollowUpdates] 按需 ALTER —— 与官方一致。
     */
    fun folderTableCreate(name: String): String = """
        CREATE TABLE IF NOT EXISTS ${quoteIdentifier(name)}(
            id TEXT,
            name TEXT,
            author TEXT,
            type INTEGER,
            source_key TEXT,
            tags TEXT,
            cover_path TEXT,
            time TEXT,
            display_order INTEGER,
            translated_tags TEXT,
            PRIMARY KEY (id, type)
        );
    """.trimIndent()

    override fun create(db: SqlDatabase) {
        META_TABLE_STATEMENTS.forEach { db.exec(it) }
        db.exec(folderTableCreate(LocalFavoriteDatabase.DEFAULT_FOLDER))
        db.exec(
            "INSERT OR IGNORE INTO folder_order(folder_name, order_value) VALUES (?, 0)",
            LocalFavoriteDatabase.DEFAULT_FOLDER,
        )
    }

    override fun upgrade(db: SqlDatabase, oldVersion: Int, newVersion: Int) {
        // v1 为首个版本，暂无历史数据需要迁移。
    }
}
