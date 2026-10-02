package com.venera.desktop.platform

import com.venera.compose.data.db.CoreDbSchema
import com.venera.compose.data.db.DatabasePorts
import com.venera.compose.data.db.FavoritesPreferences
import com.venera.compose.data.db.LocalFavoriteDbSchema
import com.venera.compose.data.db.SchemaOps
import com.venera.compose.data.db.SqlDatabaseSource
import com.venera.compose.data.platform.JsonKeyValueStore
import com.venera.compose.data.platform.PathProvider
import com.venera.compose.data.platform.SqlDatabase
import java.io.File

/**
 * `data/db` 在桌面侧的接线（与 `AndroidDatabasePorts` 对口，标签 `"desktop"`）。
 *
 * 结构与 Android 侧逐面对齐：
 *  - **两棵库各自独立**：`venera_core.db` 与 `local_favorite.db` 两个文件、两条连接，
 *    对应 Android 的两个 helper；`reader()` / `writer()` 交回**同一实例**
 *    （桌面是单连接语义，R21(a) 的串行化就压在那条连接上）。
 *  - **建库时机同 helper**：连接推迟到第一次取用才建，`ensureOn` 就在首次取用里跑
 *    （不许包进 `inTransaction` —— 单连接没有 savepoint，嵌套事务会当场抛）。
 *  - **三项偏好**从桌面自己的 [JsonKeyValueStore] 现读，写走 `put`。
 *
 * 未接线就取用 ⇒ [DatabasePorts.of] 直接抛（它自带的语义，桌面侧不加 try/catch 咽掉）。
 * [install] 只装 lambda、不碰 SQLite；同标签重复装（数据目录不变）是幂等，
 * 换了目录则抛 —— 静态口不会清零，悄悄换目录等于让全 JVM 后续取用打到别的库上。
 *
 * 进程内"关窗 → 再开"走 [close]：关掉两棵库的连接并清掉缓存端口，
 * 下一次 [DatabasePorts.of] 会拿同一批文件重建新连接（版本编排 `ensureOn` 幂等，
 * 已建好的库只是重开连接，不会重跑建表）。
 */
object DesktopDatabasePorts {

    /** 平台标签：与 [DatabasePorts.install] 的异标签闸门配套（Android 侧传 `"android"`）。 */
    const val PLATFORM = "desktop"

    /** prefs 文件名与 `VeneraPreferences.PREFS_NAME` 逐字一致（那颗里是 private，复制来钉在这注释里）。 */
    private const val PREFS_NAME = "venera_preferences"

    // 三个键名与 `VeneraPreferences` companion 里的 KEY_NEW_FAVORITE_ADD_TO /
    // KEY_MOVE_FAVORITE_AFTER_READ / KEY_FOLLOW_UPDATES_FOLDER 逐字一致（那三颗是 private，
    // 这里只能抄常量；DesktopDatabasePortsTest 用"写一遍→重开→读回"钉住两端不再漂移）。
    private const val KEY_NEW_FAVORITE_ADD_TO = "pref_new_favorite_add_to"
    private const val KEY_MOVE_FAVORITE_AFTER_READ = "pref_move_favorite_after_read"
    private const val KEY_FOLLOW_UPDATES_FOLDER = "pref_follow_updates_folder"

    @Volatile
    private var paths: PathProvider? = null

    @Volatile
    private var ports: DatabasePorts? = null

    private var coreSource: DesktopDatabaseSource? = null

    private var favoritesSource: DesktopDatabaseSource? = null

    /**
     * 装桌面接线。同目录重复装幂等（供"关窗再开"后重新走一遍启动路径的用例用）；
     * 已装过且数据目录不同 ⇒ 抛，不咽。
     */
    fun install(paths: PathProvider) {
        val current = this.paths
        if (current == null) {
            this.paths = paths
            DatabasePorts.install(platform = PLATFORM) { handle -> ports(handle) }
        } else if (current.dataRoot != paths.dataRoot) {
            throw IllegalStateException(
                "桌面接线已绑定 ${current.dataRoot.path}，拒绝改绑 ${paths.dataRoot.path}（静态口不会清零）"
            )
        }
    }

    /** `venera_core.db` 的落点（`db/` 子目录下，与 Android 的同名同层）。 */
    fun coreDbFile(): File = dbFile(CoreDbSchema.DATABASE_NAME)

    /** `local_favorite.db` 的落点。 */
    fun favoritesDbFile(): File = dbFile(LocalFavoriteDbSchema.DATABASE_NAME)

    private fun dbFile(name: String): File {
        val p = paths
            ?: throw IllegalStateException("桌面 data/db 尚未接线：先在 main 里调 DesktopDatabasePorts.install(paths)")
        return File(p.subDir("db"), name)
    }

    /**
     * 关掉两棵库的连接并清掉缓存端口（连接随机关不掉也要清，故 try/finally）。
     * 关完再取用 [DatabasePorts.of] 就是新连接 —— "写-关-新开-读回"用例钉的就是这条路径。
     */
    fun close() = synchronized(this) {
        ports = null
        try {
            coreSource?.close()
        } finally {
            favoritesSource?.close()
            coreSource = null
            favoritesSource = null
        }
    }

    /**
     * factory 本体：桌面不解读句柄（路径来自 [install] 的参数，不是调用点递下来的 Context），
     * 句柄原样忽略。端口缓存一份，连接被 [close] 掉后重建。
     */
    private fun ports(handle: Any?): DatabasePorts = ports ?: synchronized(this) {
        ports ?: run {
            val p = paths
                ?: throw IllegalStateException("桌面 data/db 接线端口在 factory 已装后丢了数据目录，不可能到这步")
            val core = DesktopDatabaseSource(dbFile(CoreDbSchema.DATABASE_NAME), CoreDbSchema)
            val favorites = DesktopDatabaseSource(dbFile(LocalFavoriteDbSchema.DATABASE_NAME), LocalFavoriteDbSchema)
            coreSource = core
            favoritesSource = favorites
            DatabasePorts(
                core = core,
                localFavorites = favorites,
                favoritesPreferences = DesktopFavoritesPreferences(JsonKeyValueStore(PREFS_NAME, p)),
            ).also { ports = it }
        }
    }

    /**
     * 一棵库的连接获取口：第一次取用才建连接并跑 [SchemaOps.ensureOn]，之后 reader/writer
     * 恒交回同一实例（桌面单连接语义）。close 后即弃 —— 由 [DesktopDatabasePorts.close]
     * 整颗换新，不做"同颗重开"（同颗重开会让别的线程手里的旧实例作废得更隐蔽）。
     */
    private class DesktopDatabaseSource(
        private val file: File,
        private val schema: SchemaOps,
    ) : SqlDatabaseSource {

        @Volatile
        private var db: JdbcSqliteDatabase? = null

        private fun obtain(): SqlDatabase = synchronized(this) {
            db ?: JdbcSqliteDatabase(file).also { fresh ->
                schema.ensureOn(fresh)
                db = fresh
            }
        }

        override fun reader(): SqlDatabase = obtain()

        override fun writer(): SqlDatabase = obtain()

        fun close() {
            db?.close()
            db = null
        }
    }

    /**
     * 三项偏好的桌面实现：每次现读磁盘（[JsonKeyValueStore] 本身不缓存，立场一致）。
     *
     * 默认值对齐 Android 侧的初始化：`newFavoriteAddTo` 缺省 `"start"`
     * （`VeneraPreferences:381` 的 `getString(key, "start") ?: "start"`），另两项缺省 null。
     * `setFollowUpdatesFolder(null)` 走 `put(key, null)` ⇒ JsonKeyValueStore 语义是删键，
     * 与 Android 侧 SharedPreferences 的 remove 等效。
     */
    private class DesktopFavoritesPreferences(
        private val store: JsonKeyValueStore,
    ) : FavoritesPreferences {
        override val newFavoriteAddTo: String?
            get() = store.getString(KEY_NEW_FAVORITE_ADD_TO, "start") ?: "start"

        override val moveFavoriteAfterRead: String?
            get() = store.getString(KEY_MOVE_FAVORITE_AFTER_READ, null)

        override val followUpdatesFolder: String?
            get() = store.getString(KEY_FOLLOW_UPDATES_FOLDER, null)

        override fun setFollowUpdatesFolder(folder: String?) {
            store.put(KEY_FOLLOW_UPDATES_FOLDER, folder)
        }
    }
}
