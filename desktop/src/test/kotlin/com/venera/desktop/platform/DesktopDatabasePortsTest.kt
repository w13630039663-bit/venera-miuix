package com.venera.desktop.platform

import com.venera.compose.data.db.CoreDbSchema
import com.venera.compose.data.db.DatabasePorts
import com.venera.compose.data.db.FavoriteItem
import com.venera.compose.data.db.LocalFavoriteDbSchema
import com.venera.compose.data.db.LocalFavoritesManager
import com.venera.compose.data.platform.PreferenceKeys
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * 桌面接线的"存得下、进程内重开得回"用例（Task 5 Step 2 的进程内版）。
 *
 * 真 sqlite、真 [JsonKeyValueStore] 落盘、真临时目录 —— 没有一颗 mock。
 * 验的是**接缝本身**：`DatabasePorts` 用 `JdbcSqliteDatabase` + `PathProvider` 拼起来之后，
 * 收藏写进 `local_favorite.db`、关掉全部连接、重新接线（新实例、同文件）、读回同一颗并逐字段断死；
 * 偏好那三项也走一遍"写 → 重开 → 读回"。
 *
 * **偏好键名"两端一致"不是本类钉的**：[PreferenceKeys] 由 `VeneraPreferences`（Android 侧）与
 * 桌面接线共同引用，那颗一致性是编译期事实，JVM 用例碰不到前者（它不在桌面编译面里）。
 * 本类能钉的是桌面侧自己"写→关→重开→读回"落在同一批键名与同一批文件上。
 * 两端今天连的也**不是同一个文件**（Android 是 `venera_preferences.xml`，桌面是
 * `prefs/venera_preferences.json`），名字对得上不等于数据共享。
 *
 * **静态端口口径**：`DatabasePorts` 装了就不清零，所以全类共用一份数据目录与一个标签
 * （"desktop"）。各条用例谁先跑都成立：每条的第一个动作永远是先把 desktop 标签装上
 * （同落点重复装幂等），要撞闸门的再拿 `"android"` 或换目录去撞、且撞完不改绑定。
 */
class DesktopDatabasePortsTest {

    companion object {
        private val dataDir: File = Files.createTempDirectory("r1f-desktop-ports-").toFile()
        private const val FOLDER = "桌面收藏夹"

        /** 只在 Windows 上跑的闸门用例：改绑判据必须按 canonicalFile 认"同一目录的不同写法"。 */
        private val IS_WINDOWS: Boolean =
            System.getProperty("os.name").lowercase().contains("windows")
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
        // 桌面单连接语义：reader/writer 交回的是同一实例，不是两条连接
        assertSame(ports.core.reader(), ports.core.writer())
        assertSame(ports.localFavorites.reader(), ports.localFavorites.writer())
        val manager = LocalFavoritesManager(ports.core, ports.localFavorites, ports.favoritesPreferences)
        runBlocking {
            val folder = manager.createFolder(FOLDER)
            assertEquals(FOLDER, folder)
            assertTrue(manager.addComic(folder, item))
            // 顺带把追更指向写进偏好：桌面侧写/读回是不是同一批键，重开之后读回来见分晓
            ports.favoritesPreferences.setFollowUpdatesFolder(folder)
        }
        manager.close()
        val firstCoreReader = ports.core.reader()
        DesktopDatabasePorts.close()
        // "关掉全部连接"这半句的断言：旧 core 连接必须真的已废（close 漏关 db 的话这里红），
        // 且收藏确实落在磁盘文件上（落在别处/内存库的话这里红）
        assertTrue(
            "close 后旧 core 连接应已作废",
            runCatching { firstCoreReader.query("SELECT 1").size }.isFailure,
        )
        assertTrue("收藏库应是磁盘上的真文件", DesktopDatabasePorts.favoritesDbFile().exists())

        // 重开：再装一遍（同标签同落点 ⇒ 幂等），of 交回的是新端口与新连接
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
        // 不碰库表：本类用例的跑序不固定，库里有没有那颗收藏不作弊说不清。
        val ports = DatabasePorts.of(Unit)
        assertEquals("start", ports.favoritesPreferences.newFavoriteAddTo)
        DesktopDatabasePorts.close()
    }

    @Test
    fun 关掉全部连接后拿旧端口再取用直接抛而不是悄悄多出一条活连接() {
        DesktopDatabasePorts.install(FakePaths(dataDir))
        val ports = DatabasePorts.of(Unit)
        val firstCoreReader = ports.core.reader()
        DesktopDatabasePorts.close()
        // 旧连接本体已废
        assertTrue(
            "close 前拿到的连接应已作废",
            runCatching { firstCoreReader.query("SELECT 1").size }.isFailure,
        )
        // 已退役的 source 再取用必须是响的：悄悄重建一条活连接就是 F6 的句柄泄漏形状
        val boom = runCatching { ports.core.reader() }
        val err = boom.exceptionOrNull()
        assertTrue("退役 source 再取用应抛，实际：$err", err is IllegalStateException)
        // 新取用路不受牵连：of 交回整套新端口，取用正常
        val ports2 = DatabasePorts.of(Unit)
        assertNotSame(ports, ports2)
        assertSame(ports2.core.reader(), ports2.core.writer())
        DesktopDatabasePorts.close()
    }

    @Test
    fun 换缓存目录与换数据目录一样被拒而同一落点的不同写法仍算幂等() {
        DesktopDatabasePorts.install(FakePaths(dataDir))
        // 只换 cacheRoot 也算改绑：静态口不会清零，悄悄换落点等于让全 JVM 后续取用打到别的目录
        val boom = runCatching {
            DesktopDatabasePorts.install(FakePaths(dataDir, File(dataDir, "another-cache")))
        }
        val err = boom.exceptionOrNull()
        assertTrue("换 cacheRoot 应被拒，实际：$err", err is IllegalStateException)
        assertTrue("报错要点名拒绝改绑，实际：${err?.message}", err?.message?.contains("拒绝改绑") == true)
        // "本目录/." 与原目录是同一落点 ⇒ 幂等，不该被串比较误判成改绑
        val dotted = File(dataDir, ".")
        DesktopDatabasePorts.install(FakePaths(dotted, File(dotted, "cache")))
        // 撞完闸门绑定没被换掉：仍走桌面接线
        val ports = DatabasePorts.of(Unit)
        assertSame(ports.core.reader(), ports.core.writer())
        DesktopDatabasePorts.close()
    }

    @Test
    fun windows上大小写不同的同一数据目录仍算幂等而非拒绝改绑() {
        // Windows 的坑：判据若退化成路径串比较，"C:\Users\…" 与 "C:\users\…" 就会被当成两个目录。
        // （2026-10 本机 JDK 21 实测 File.equals 对全路径已大小写不敏感，这条在今天的树上并不红；
        // 它钉的是"改绑判据不许写成串比较"这个性质 —— install 一旦改用 path 串对比就红。）
        assumeTrue(IS_WINDOWS)
        val path = dataDir.path
        // 翻目录名里第一颗字母（跳过盘符那一位，路径首字母就是盘符）
        val i = path.indices.firstOrNull { it > 1 && path[it].isLetter() } ?: -1
        assumeTrue("数据目录路径里没有可翻转的字母", i >= 0)
        val ch = path[i]
        val flipped = File(
            path.substring(0, i) +
                (if (ch.isUpperCase()) ch.lowercaseChar() else ch.uppercaseChar()) +
                path.substring(i + 1),
        )
        assumeTrue("翻转后路径串确实不同", flipped.path != path)
        DesktopDatabasePorts.install(FakePaths(dataDir))
        DesktopDatabasePorts.install(FakePaths(flipped, File(flipped, "cache")))
        val ports = DatabasePorts.of(Unit)
        assertSame(ports.core.reader(), ports.core.writer())
        DesktopDatabasePorts.close()
    }

    @Test
    fun 接线读数是纯计算全新目录里只算路径不会把db目录建出来() {
        // `D_接线` 那行"已建="想反映磁盘现状，读数本身不许顺手把 db/ 建出来（5a 只有代码级
        // 证据：dbFile 不走 subDir；这条把同一性质钉在用例面——dbFileAt 一旦改用会 mkdirs 的
        // PathProvider.subDir，最后的 assertFalse 就红）。
        val fresh = Files.createTempDirectory("r1f-desktop-pure-").toFile()
        val core = DesktopDatabasePorts.dbFileAt(FakePaths(fresh), CoreDbSchema.DATABASE_NAME)
        val fav = DesktopDatabasePorts.dbFileAt(FakePaths(fresh), LocalFavoriteDbSchema.DATABASE_NAME)
        assertEquals(File(File(fresh, "db"), CoreDbSchema.DATABASE_NAME), core)
        assertEquals(File(File(fresh, "db"), LocalFavoriteDbSchema.DATABASE_NAME), fav)
        assertFalse("算落点不该把 db/ 建出来", File(fresh, "db").exists())
    }
}
