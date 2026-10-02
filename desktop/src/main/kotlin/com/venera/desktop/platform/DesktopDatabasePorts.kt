package com.venera.desktop.platform

import com.venera.compose.data.db.CoreDbSchema
import com.venera.compose.data.db.DatabasePorts
import com.venera.compose.data.db.FavoritesPreferences
import com.venera.compose.data.db.LocalFavoriteDbSchema
import com.venera.compose.data.db.SchemaOps
import com.venera.compose.data.db.SqlDatabaseSource
import com.venera.compose.data.platform.JsonKeyValueStore
import com.venera.compose.data.platform.PathProvider
import com.venera.compose.data.platform.PreferenceKeys
import com.venera.compose.data.platform.SqlDatabase
import java.io.File
import java.io.IOException

/**
 * `data/db` 在桌面侧的接线（与 `AndroidDatabasePorts` 对口，标签 `"desktop"`）。
 *
 * 结构与 Android 侧逐面对齐：
 *  - **两棵库各自独立**：`venera_core.db` 与 `local_favorite.db` 两个文件、两条连接，
 *    对应 Android 的两个 helper；`reader()` / `writer()` 交回**同一实例**
 *    （桌面是单连接语义，R21(a) 的串行化就压在那条连接上）。
 *  - **建库时机同 helper**：连接推迟到第一次取用才建（`db/` 目录也在那一刻才建，
 *    [coreDbFile]/[favoritesDbFile] 是纯计算、不带副作用），`ensureOn` 就在首次取用里跑
 *    （不许包进 `inTransaction` —— 单连接没有 savepoint，嵌套事务会当场抛）。
 *  - **三项偏好**从桌面自己的 [JsonKeyValueStore] 现读，写走 `put`；文件名与键名引用
 *    [PreferenceKeys]（Android 侧 `VeneraPreferences` 引用同一批常量，两端名字对不上是
 *    编译错误，不是"注释失守"）。两端今天落在**不同的文件**上：Android
 *    `venera_preferences.xml`、桌面 `prefs/venera_preferences.json` —— 名字一致 ≠ 数据共享。
 *
 * 未接线就取用 ⇒ [DatabasePorts.of] 直接抛（它自带的语义，桌面侧不加 try/catch 咽掉）。
 * [install] 只装 lambda、不碰 SQLite；同落点重复装是幂等，换了落点（dataRoot 或 cacheRoot
 * 任一）则抛 —— 静态口不会清零，悄悄换目录等于让全 JVM 后续取用打到别的库上。
 *
 * 进程内"关窗 → 再开"走 [close]：它与取用（端口创建、连接获取）走同一把锁，关掉两棵库的
 * 连接并清掉缓存端口；被关掉的端口整颗退役，拿它再取用直接抛。下一次 [DatabasePorts.of]
 * 拿同一批文件重建新连接与新端口（版本编排 `ensureOn` 幂等，已建好的库只是重开连接，
 * 不会重跑建表）。
 */
object DesktopDatabasePorts {

    /** 平台标签：与 [DatabasePorts.install] 的异标签闸门配套（Android 侧传 `"android"`）。 */
    const val PLATFORM = "desktop"

    // 偏好文件名与三颗键名都引用 [PreferenceKeys]，这里不再自备字面量：与 Android 侧
    // `VeneraPreferences` 的名字一致因此是编译期事实。桌面用例钉的是自己"写→关→重开→读回"
    // 落在同一批键上，钉不了（也不需要钉）跨端一致 —— `VeneraPreferences` 不在桌面编译面里。

    @Volatile
    private var paths: PathProvider? = null

    @Volatile
    private var ports: DatabasePorts? = null

    private var coreSource: DesktopDatabaseSource? = null

    private var favoritesSource: DesktopDatabaseSource? = null

    /**
     * 装桌面接线。同落点重复装幂等（供"关窗再开"后重新走一遍启动路径的用例用）；
     * 已装过且落点不同（dataRoot 或 cacheRoot 任一变了）⇒ 抛，不咽。
     */
    fun install(paths: PathProvider) {
        val current = this.paths
        if (current == null) {
            this.paths = paths
            DatabasePorts.install(platform = PLATFORM) { handle -> ports(handle) }
        } else if (!sameLanding(current, paths)) {
            throw IllegalStateException(
                "桌面接线已绑定 数据=${current.dataRoot.path} 缓存=${current.cacheRoot.path}，" +
                    "拒绝改绑 数据=${paths.dataRoot.path} 缓存=${paths.cacheRoot.path}（静态口不会清零）"
            )
        }
    }

    /**
     * 同一落点与否：dataRoot 与 cacheRoot 一起比 canonicalFile。
     * 不许退化成串比较 —— 同一目录有 `dir\.` 这类别名写法，Windows 上还有一串
     * "两个对象、一个目录"的写法；也只许比 dataRoot —— 只换缓存目录同样是改绑。
     */
    private fun sameLanding(a: PathProvider, b: PathProvider): Boolean =
        canonicalPath(a.dataRoot) == canonicalPath(b.dataRoot) &&
            canonicalPath(a.cacheRoot) == canonicalPath(b.cacheRoot)

    private fun canonicalPath(file: File): String = try {
        file.canonicalFile.path
    } catch (e: IOException) {
        // 规范化取不到（异常路径）：退成绝对路径比较。降级方向是"更可能拒绝改绑"，
        // 响着比悄悄接受换目录安全，故不另抛也不静默吞。
        file.absolutePath
    }

    /** `venera_core.db` 的落点（`db/` 子目录下，与 Android 的同名同层）。纯计算，不建目录。 */
    fun coreDbFile(): File = dbFile(CoreDbSchema.DATABASE_NAME)

    /** `local_favorite.db` 的落点。纯计算，不建目录。 */
    fun favoritesDbFile(): File = dbFile(LocalFavoriteDbSchema.DATABASE_NAME)

    private fun dbFile(name: String): File {
        val p = paths
            ?: throw IllegalStateException("桌面 data/db 尚未接线：先在 main 里调 DesktopDatabasePorts.install(paths)")
        // 不走 PathProvider.subDir —— 那颗顺手 mkdirs，`D_接线` 这行读数不该自己把 db/ 建出来。
        // 目录创建在 DesktopDatabaseSource.obtain() 里显式做。
        return File(File(p.dataRoot, "db"), name)
    }

    /**
     * 关掉两棵库的连接并清掉缓存端口。三条性质（各有用例钉，见 `DesktopDatabasePortsTest`）：
     *  - 与端口创建、连接获取走 [DesktopDatabasePorts] 的同一把锁：连接要么被这次关掉、
     *    要么根本没建出来，不存在"close 返回后又多出一条活连接"的句柄泄漏；
     *  - 两颗 source 引用先摘下即清，关闭抛不抛都不留残留；
     *  - 关闭有抛先等全部关完再重抛第一颗（不吞、也不因前一颗抛让后一颗躲过关闭）。
     * 关完拿旧端口再取用必抛（[DesktopDatabaseSource] 已退役），[DatabasePorts.of] 重建新端口。
     */
    fun close() = synchronized(this) {
        ports = null
        val core = coreSource
        val favorites = favoritesSource
        coreSource = null
        favoritesSource = null
        var first: Throwable? = null
        for (source in listOfNotNull(core, favorites)) {
            try {
                source.close()
            } catch (e: Throwable) {
                if (first == null) {
                    first = e
                } else {
                    first.addSuppressed(e)
                }
            }
        }
        first?.let { throw it }
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
                favoritesPreferences = DesktopFavoritesPreferences(JsonKeyValueStore(PreferenceKeys.PREFS_NAME, p)),
            ).also { ports = it }
        }
    }

    /**
     * 一棵库的连接获取口：第一次取用才建 `db/` 目录、建连接并跑 [SchemaOps.ensureOn]，
     * 之后 reader/writer 恒交回同一实例（桌面单连接语义）。
     *
     * 锁取在外层 [DesktopDatabasePorts] 上而不是自己 —— 与 [close] 互斥，
     * "close 关完又新建一条活连接"的交错因此被锁掉。close 之后整颗退役：再取用直接抛，
     * 不做"同颗重开"（悄悄重建会把活连接留在已退役的端口里，句柄泄漏；同颗复活
     * 也会让别的线程手里的旧实例变成暗状态 —— 打到已退役的端口要响）。
     */
    private class DesktopDatabaseSource(
        private val file: File,
        private val schema: SchemaOps,
    ) : SqlDatabaseSource {

        private var db: JdbcSqliteDatabase? = null

        /** 读与写都在 [DesktopDatabasePorts] 的锁内，不外泄，所以不加 @Volatile。 */
        private var retired = false

        private fun obtain(): SqlDatabase = synchronized(this@DesktopDatabasePorts) {
            check(!retired) {
                "桌面库连接口已随 DesktopDatabasePorts.close() 退役：${file.path}" +
                    "（再取用请走 DatabasePorts.of 拿新端口，旧端口整颗作废）"
            }
            db ?: run {
                // 建目录显式在这里做（dbFile 是纯计算，不顺手建目录）
                val dir = file.parentFile
                    ?: throw IllegalStateException("库路径没有父目录，建不出库：${file.path}")
                if (!dir.mkdirs() && !dir.isDirectory) {
                    throw IllegalStateException("建不出库目录：${dir.path}")
                }
                JdbcSqliteDatabase(file).also { fresh ->
                    schema.ensureOn(fresh)
                    db = fresh
                }
            }
        }

        override fun reader(): SqlDatabase = obtain()

        override fun writer(): SqlDatabase = obtain()

        /** 调用方持 [DesktopDatabasePorts] 锁。先标记退役，之后任何取用都响。 */
        fun close() {
            retired = true
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
            get() = store.getString(PreferenceKeys.KEY_NEW_FAVORITE_ADD_TO, "start") ?: "start"

        override val moveFavoriteAfterRead: String?
            get() = store.getString(PreferenceKeys.KEY_MOVE_FAVORITE_AFTER_READ, null)

        override val followUpdatesFolder: String?
            get() = store.getString(PreferenceKeys.KEY_FOLLOW_UPDATES_FOLDER, null)

        override fun setFollowUpdatesFolder(folder: String?) {
            store.put(PreferenceKeys.KEY_FOLLOW_UPDATES_FOLDER, folder)
        }
    }
}
