package com.venera.compose.data.db

import com.venera.compose.data.platform.SqlDatabase

/**
 * 一个库的**连接获取口**，而不是一个连接实例。
 *
 * 为什么不是 `SqlDatabase` 直接注入：Android 侧 `SQLiteOpenHelper.readableDatabase` /
 * `writableDatabase` 是「每次取用现取当前连接」的引用语义，把返回值缓存下来就等于把
 * 连接生命周期从 helper 手里抢走（helper 重开、版本升级换连接时这里会拿到废连接）。
 * 所以这里保留「取」这个动作，两端各自决定怎么取：
 *  - Android：`HelperDatabaseSource` 贴 SQLiteOpenHelper，reader/writer 分别对应原调用点；
 *  - 桌面：`JdbcSqliteDatabase` 一条连接两头共用（同一实例）。
 *
 * 本接口不碰任何 Android 类型：`data/db` 只认它，不认 helper 类。
 */
interface SqlDatabaseSource {
    /** 读用连接（原 `readableDatabase`）。 */
    fun reader(): SqlDatabase

    /** 写用连接（原 `writableDatabase`，取它即触发建表/迁移）。 */
    fun writer(): SqlDatabase
}

/**
 * [LocalFavoritesManager] 需要的三项偏好。
 *
 * 为什么要抽接口：偏好层的 `VeneraPreferences` 是 `:app` 侧的类（内部还挂 android 的
 * SharedPreferences 接线），核心收藏夹逻辑不许朝它伸手 —— 否则 `data/db` 永远进不了桌面编译。
 * 三个 getter 每次现读，语义与原来在调用点读 `StateFlow.value` 一字不差。
 */
interface FavoritesPreferences {
    /** `newFavoriteAddTo`：`"end"` 加到夹子末尾，其他值加到开头。 */
    val newFavoriteAddTo: String?

    /** `moveFavoriteAfterRead`：`"start"` / `"end"` / null（读完不动位置）。 */
    val moveFavoriteAfterRead: String?

    /** `followUpdatesFolder`：追更收藏夹名，null = 未开启追更。 */
    val followUpdatesFolder: String?

    /** `setFollowUpdatesFolder`：改名/删夹时同步追更指向。 */
    fun setFollowUpdatesFolder(folder: String?)
}

/**
 * `data/db` 的平台接线口：两个库 + 三项偏好的来路，一次性交给核心。
 *
 * 装法（两端各一份、各一次）：
 *  - Android：`data/platform/android/AndroidDatabasePorts.install()`（由 `VeneraApp.onCreate` 触发）；
 *  - 桌面：Task 5 在桌面启动处 `install`，用 `JdbcSqliteDatabase` + `DesktopPaths` 拼同一份口。
 * [install] 带平台标签：装的时候记下是谁装的，**换了平台标签再装直接抛** ——
 * 本对象是静态口，测试里装了假端口若不清零，会留给整个 JVM 的后续用例（R22）。
 *
 * 关于 `factory` 的参数类型 `Any?`：安装的是「怎么从调用点递来的句柄造出口」这段逻辑，
 * 句柄本身核心不解读 —— Android 侧传 `Context`（旧调用点原样递下来），桌面侧传 `Unit` 或
 * `PathProvider`。核心之所以仍然收这个参数，是为了保住原有的**首次取用时才建库**的时机：
 * 启动阶段只装 lambda，不碰 SQLite，冷启动开销与改造前相同。
 */
class DatabasePorts(
    val core: SqlDatabaseSource,
    val localFavorites: SqlDatabaseSource,
    val favoritesPreferences: FavoritesPreferences,
) {
    companion object {
        @Volatile
        private var factory: ((Any?) -> DatabasePorts)? = null

        @Volatile
        private var installedPlatform: String? = null

        /**
         * 安装接线 factory：**同一平台**重复调用以先装的那份为准（幂等，两端互不覆盖）；
         * 平台标签不同再装 ⇒ 直接抛 —— 静态口一旦被一个装走，另一个平台悄悄换 factory 会
         * 让全 JVM 后续取用都打到错连接上（含测试装了假端口留给整个进程的情形，R22）。
         */
        fun install(platform: String, factory: (Any?) -> DatabasePorts) {
            val current = installedPlatform
            if (current == null) {
                installedPlatform = platform
                this.factory = factory
            } else if (current != platform) {
                throw IllegalStateException(
                    "data/db 接线已由平台「$current」安装，拒绝平台「$platform」重复安装（静态口不会清零）"
                )
            }
        }

        /**
         * 由调用点递来的平台句柄造出接线口。未接线 ⇒ 抛并说清是谁该在什么时候装，
         * 不许返回 null 让上层拿空库继续跑。
         */
        fun of(handle: Any?): DatabasePorts {
            val f = factory
                ?: throw IllegalStateException(
                    "data/db 尚未完成平台接线（of 取用时无任何已装 factory）：请在启动处调用 " +
                        "DatabasePorts.install —— Android 侧为 AndroidDatabasePorts.install()。" +
                        "当前收到的句柄：${handle?.javaClass?.name ?: "null"}"
                )
            return f(handle)
        }
    }
}
