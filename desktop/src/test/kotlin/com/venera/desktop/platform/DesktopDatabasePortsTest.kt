package com.venera.desktop.platform

import com.venera.compose.data.db.DatabasePorts
import com.venera.compose.data.db.FavoriteItem
import com.venera.compose.data.db.LocalFavoritesManager
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * 桌面接线的"存得下、进程内重开得回"用例（Task 5 Step 2 的进程内版）。
 *
 * 真 sqlite、真 [JsonKeyValueStore] 落盘、真临时目录 —— 没有一颗 mock。
 * 验的是**接缝本身**：`DatabasePorts` 用 `JdbcSqliteDatabase` + `PathProvider` 拼起来之后，
 * 收藏写进 `local_favorite.db`、关掉全部连接、重新接线（新实例、同文件）、读回同一颗并逐字段断死；
 * 偏好那三项也走一遍"写 → 重开 → 读回"，把键名与 `VeneraPreferences` 常量的逐字一致钉成事实
 * （那三颗常量是 private，对不上就只会在真文件上红）。
 *
 * **静态端口口径**：`DatabasePorts` 装了就不清零，所以全类共用一份数据目录与一个标签
 * （"desktop"）。两条用例谁先跑都成立：本类第一条动作永远是先把 desktop 标签装上
 * （同目录重复装幂等），异标签那颗再拿 `"android"` 去撞闸门。
 */
class DesktopDatabasePortsTest {

    companion object {
        private val dataDir: File = Files.createTempDirectory("r1f-desktop-ports-").toFile()
        private const val FOLDER = "桌面收藏夹"
    }

    private val item = FavoriteItem(
        id = "d-comic-1",
        name = "桌面收藏的那一本",
        author = "某作者",
        sourceKey = "jm",
        tags = listOf("恋爱", "中文标签"),
        coverPath = "https://cover/d-comic-1",
        time = "2026-10-02 12:00:00",
    )

    @Test
    fun 收藏写入后关光连接重新接线仍读回同一颗且字段一字不差() {
        DesktopDatabasePorts.install(FakePaths(dataDir))
        val ports = DatabasePorts.of(Unit)
        val manager = LocalFavoritesManager(ports.core, ports.localFavorites, ports.favoritesPreferences)
        runBlocking {
            val folder = manager.createFolder(FOLDER)
            assertEquals(FOLDER, folder)
            assertTrue(manager.addComic(folder, item))
            // 顺带把追更指向写进偏好：键名对不对，重开之后读回来见分晓
            ports.favoritesPreferences.setFollowUpdatesFolder(folder)
        }
        manager.close()
        val firstCoreReader = ports.core.reader()
        DesktopDatabasePorts.close()

        // 重开：再装一遍（同标签同目录 ⇒ 幂等），of 交回的是新端口与新连接
        DesktopDatabasePorts.install(FakePaths(dataDir))
        val ports2 = DatabasePorts.of(Unit)
        assertNotSame(ports, ports2)
        assertNotSame(firstCoreReader, ports2.core.reader())
        val manager2 = LocalFavoritesManager(ports2.core, ports2.localFavorites, ports2.favoritesPreferences)
        runBlocking {
            val items = manager2.getFolderComics(FOLDER)
            assertEquals(1, items.size)
            val back = items.single()
            assertEquals(item.id, back.id)
            assertEquals(item.name, back.name)
            assertEquals(item.author, back.author)
            assertEquals(item.sourceKey, back.sourceKey)
            assertEquals(item.tags, back.tags)
            assertEquals(item.coverPath, back.coverPath)
            assertEquals(item.time, back.time)
            assertEquals(item.type, back.type)
            // 偏好：追更指向从 JSON 文件里读回来；插入位置读到的是与 Android 同口径的缺省值
            assertEquals(FOLDER, ports2.favoritesPreferences.followUpdatesFolder)
            assertEquals("start", ports2.favoritesPreferences.newFavoriteAddTo)
            assertEquals(null, ports2.favoritesPreferences.moveFavoriteAfterRead)
        }
        manager2.close()
        DesktopDatabasePorts.close()
    }

    @Test
    fun 异标签二次装直接抛且已装端口不被换掉() {
        DesktopDatabasePorts.install(FakePaths(dataDir))
        val boom = runCatching {
            DatabasePorts.install(platform = "android") { error("闸门没拦住：factory 竟被取用了") }
        }
        val err = boom.exceptionOrNull()
        assertTrue("期望 IllegalStateException，实际：$err", err is IllegalStateException)
        assertTrue("报错要点名已装的平台，实际：${err?.message}", err!!.message?.contains("desktop") == true)

        // 闸门只拦装、不伤已装：取用仍走桌面 factory（那颗被拒的 android factory 从未被装进去，
        // 若它顶替了桌面端口，这里读偏好的缺省值就不是 "start" 而是 error() 抛的"闸门没拦住"）。
        // 不碰库表：本类两条用例的跑序不固定，库里有没有那颗收藏不作弊说不清。
        val ports = DatabasePorts.of(Unit)
        assertEquals("start", ports.favoritesPreferences.newFavoriteAddTo)
        DesktopDatabasePorts.close()
    }
}
