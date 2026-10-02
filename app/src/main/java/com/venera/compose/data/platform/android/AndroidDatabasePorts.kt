package com.venera.compose.data.platform.android

import android.content.Context
import android.database.sqlite.SQLiteOpenHelper
import com.venera.compose.data.db.DatabasePorts
import com.venera.compose.data.db.FavoritesPreferences
import com.venera.compose.data.db.SqlDatabaseSource
import com.venera.compose.data.db.VeneraDatabase
import com.venera.compose.data.platform.SqlDatabase
import com.venera.compose.data.prefs.VeneraPreferences

/**
 * [SqlDatabaseSource] 的 Android 实现：把 `SQLiteOpenHelper` 的两个取连接方法原样包一层。
 *
 * 关键点是**每次现取**：`readableDatabase` / `writableDatabase` 由 helper 负责建库、跑迁移、
 * 以及连接失效后重开。缓存返回值就等于把连接生命周期从 helper 手里抢走。
 */
class HelperDatabaseSource(private val helper: SQLiteOpenHelper) : SqlDatabaseSource {
    override fun reader(): SqlDatabase = AndroidSqlDatabase(helper.readableDatabase)
    override fun writer(): SqlDatabase = AndroidSqlDatabase(helper.writableDatabase)
}

/**
 * 偏好层 → [FavoritesPreferences] 的适配。
 *
 * 三个 getter 每次读 `StateFlow.value`，与改造前在调用点直接读一模一样（含可见性时机），
 * 不缓存、不另起一份状态。
 */
private class AndroidFavoritesPreferences(private val prefs: VeneraPreferences) : FavoritesPreferences {
    override val newFavoriteAddTo: String? get() = prefs.newFavoriteAddTo.value
    override val moveFavoriteAfterRead: String? get() = prefs.moveFavoriteAfterRead.value
    override val followUpdatesFolder: String? get() = prefs.followUpdatesFolder.value
    override fun setFollowUpdatesFolder(folder: String?) = prefs.setFollowUpdatesFolder(folder)
}

/**
 * `data/db` 在 Android 侧的接线。
 *
 * 两个库各一个 helper（进程内单例，与改造前 `VeneraDatabase.getInstance` /
 * `LocalFavoriteDatabase.getInstance` 的单例口径一致 —— 一个库一个 helper，多开就是多连接），
 * 外面再套 [HelperDatabaseSource]；偏好走 [AndroidFavoritesPreferences]。
 *
 * 调用点在 `VeneraApp.onCreate`：那里只装一个 lambda，不碰 SQLite，
 * 真正的 helper / 建库仍然推迟到第一次取用 DAO 或管理器时（与改造前同一时机）。
 */
object AndroidDatabasePorts {

    @Volatile
    private var ports: DatabasePorts? = null

    fun install() {
        DatabasePorts.install { handle -> createPorts(handle) }
    }

    /** 句柄必须是 Context；不是就抛并说清收到的是什么，不猜、不降级。 */
    private fun createPorts(handle: Any?): DatabasePorts = ports ?: synchronized(this) {
        ports ?: run {
            val context = handle as? Context
                ?: throw IllegalStateException(
                    "Android 侧的 data/db 接线需要 Context，实际收到：${handle?.javaClass?.name ?: "null"}"
                )
            DatabasePorts(
                core = HelperDatabaseSource(VeneraDatabase.getInstance(context)),
                localFavorites = HelperDatabaseSource(AndroidLocalFavoriteOpenHelper(context)),
                favoritesPreferences = AndroidFavoritesPreferences(VeneraPreferences.getInstance(context)),
            ).also { ports = it }
        }
    }
}
