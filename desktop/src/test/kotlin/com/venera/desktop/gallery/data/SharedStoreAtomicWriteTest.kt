package com.venera.desktop.gallery.data

import com.venera.compose.data.platform.Logger
import com.venera.compose.data.platform.PathProvider
import com.venera.compose.gallery.data.GalleryArtistAvatarStore
import com.venera.compose.gallery.data.GalleryArtistFollowsStore
import com.venera.compose.gallery.data.GalleryFavoritesStore
import com.venera.compose.gallery.data.GalleryPost
import com.venera.compose.gallery.data.GallerySite
import com.venera.desktop.gallery.ui.DesktopSourceTree
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 共享 store 那三处"先写 `*.tmp` 再改名"的原子写，**第二笔**在 Windows 上会不会丢。
 *
 * ## 这条用例的由来（2026-10-04 桌面第十批 ②）
 *
 * 头像地址档连落两位时，stdout 冒出一句 `D_警告 [GalleryArtistAvatar] 头像地址档没写成：…改名失败`，
 * 而盘上恰好只剩**第一条**记录。用 jshell 在这台机器上量死了成因：
 * `File.renameTo` 在 Windows 上遇到目标已存在**直接返回 false**（POSIX 的 `rename` 会替换），
 * 所以这份写法在 Android 侧一直是对的，在桌面**从第二笔起全丢**。
 *
 * ## 为什么这三条只能真跑、不能只扫源码
 *
 * 那个形状在源码里完全合法（甚至注释写着"原子"），扫什么都是绿的 ——
 * 唯一能钉住它的动作是**同一进程里连写两笔再把文件读回来**。
 * 三颗 store 各一条：它们各自独立命中过这条，而"只修头像那一颗"的形状
 * 正是本仓最忌的"同一族缺陷修了一处，另两处留着"。
 * ④⑤ 两条是**回不去**的尺子（不许退回 `renameTo`），⑤ 扫的是整个 `gallery/data` 而不是三颗名字 ——
 * 判据取"这颗文件带不带 `android.content.Context`"那条结构性分界，理由写在它自己的注释里。
 *
 * ⚠️ 三条真跑用例都另断言"日志里一句 warn 都没有"：写失败时 store 的行为是**留一句话然后继续**，
 * 只数盘上的条数会把"丢了但说了"读成通过 —— 那一句在这里不是交付，是事故报告。
 */
class SharedStoreAtomicWriteTest {

    private class TempPaths(root: File) : PathProvider {
        override val dataRoot: File = root
        override val cacheRoot: File = File(root, "cache").also { it.mkdirs() }
    }

    private class CollectingLogger : Logger {
        val lines = mutableListOf<String>()
        override fun warn(tag: String, message: String) { lines += "W/$tag $message" }
        override fun info(tag: String, message: String) { lines += "I/$tag $message" }
    }

    private fun freshRoot(name: String): File =
        Files.createTempDirectory("shared-store-$name").toFile().also { it.deleteOnExit() }

    /** 盘上那份 JSON 的条目数 —— 直接数文件，不看内存里的表（内存表会替丢失的写打掩护）。 */
    private fun recordsOnDisk(file: File): Int {
        assertTrue("盘上没有 ${file.name}（一次都没写成）", file.isFile)
        return Json.parseToJsonElement(file.readText()).jsonArray.size
    }

    private fun assertNoLeftoverTmp(root: File) {
        val leftovers = root.listFiles()?.filter { it.name.endsWith(".tmp") }.orEmpty()
        assertTrue("留下了一堆 .tmp（改名失败时原写法会把 tmp 留在原地）：${leftovers.map { it.name }}", leftovers.isEmpty())
    }

    /** ① 头像地址档：两位画师都要落盘（桌面这一屏是按视口一位一位落的，第二笔是常态不是边角）。 */
    @Test
    fun 头像地址档连落两位第二笔不许丢() {
        runBlocking {
            val root = freshRoot("avatars")
            val logger = CollectingLogger()
            val store = GalleryArtistAvatarStore.getInstance(TempPaths(root), logger)
            store.remember(GallerySite.YANDERE, "first", "", "https://img/a.jpg")
            store.remember(GallerySite.YANDERE, "second", "", "https://img/b.jpg")
            assertEquals("盘上只剩一条 ⇒ 第二笔的改名没覆盖掉第一笔（Windows 上 renameTo 不替换）",
                2, recordsOnDisk(File(root, "gallery_artist_avatars.json")))
            assertEquals("日志里不该有事故报告", emptyList<String>(), logger.lines)
            assertNoLeftoverTmp(root)
        }
    }

    /** ② 关注名单：连关注两位，第二位不许丢。 */
    @Test
    fun 关注名单连写两笔第二笔不许丢() {
        runBlocking {
            val root = freshRoot("follows")
            val store = GalleryArtistFollowsStore.getInstance(TempPaths(root))
            store.toggle(GallerySite.YANDERE, "first")
            store.toggle(GallerySite.YANDERE, "second")
            assertEquals("盘上只剩一条 ⇒ 第二笔的改名没覆盖掉第一笔",
                2, recordsOnDisk(File(root, "gallery_artist_follows.json")))
            assertEquals("内存里的表也必须是两条（丢了盘而表还在，就是「下次启动人没了」）",
                2, store.follows.value.size)
            assertNoLeftoverTmp(root)
        }
    }

    /** ③ 收藏：连收藏两张，第二张不许丢（`-Pautofav` 那条取证链今天只写一笔，所以一直没露）。 */
    @Test
    fun 收藏连写两笔第二笔不许丢() {
        runBlocking {
            val root = freshRoot("favorites")
            val store = GalleryFavoritesStore.getInstance(TempPaths(root))
            store.toggle(GalleryPost(site = GallerySite.YANDERE, id = 1L, previewUrl = "https://img/1.jpg"))
            store.toggle(GalleryPost(site = GallerySite.YANDERE, id = 2L, previewUrl = "https://img/2.jpg"))
            assertEquals("盘上只剩一条 ⇒ 第二笔的改名没覆盖掉第一笔",
                2, recordsOnDisk(File(root, "gallery_favorites.json")))
            assertNoLeftoverTmp(root)
        }
    }

    /**
     * ④ 旧写法不许回到任何一颗 store（上面三条钉的是"现在对不对"，这一条钉"别改回去"）。
     *
     * 只禁 `tmp.renameTo(` 那一枚 —— 同三颗文件里还有一处合法的 `file.renameTo(backup)`：
     * 那是把**读不出来的旧档**另存成带时间戳的新名字，目标恒不存在，Windows 上照样成。
     * 判式扫宽了就会逼着后人把那句留档一起删掉，那是拿判据换掉一条真需要的行为。
     */
    @Test
    fun 原子写不许退回renameTo() {
        val files = listOf(
            "GalleryArtistAvatarStore.kt",
            "GalleryArtistFollowsStore.kt",
            "GalleryFavoritesStore.kt",
        )
        var moves = 0
        files.forEach { name ->
            val code = DesktopSourceTree.codeText(
                File(DesktopSourceTree.repoRoot, "app/src/main/java/com/venera/compose/gallery/data/$name"),
            )
            assertTrue("「$name」里又出现 tmp.renameTo —— Windows 上第二笔起全丢（成因见本类注释）",
                code.contains("tmp.renameTo(").not())
            moves += code.split("Files.move(").size - 1
        }
        // 对照组：三颗都真换成了 Files.move（读空了会让上面那条负断言恒真）。
        assertEquals("三颗 store 各该有一处 Files.move", 3, moves)
    }

    /**
     * ⑤ **整目录扫**，而不是只扫那三颗名字：同一族写法会不会再长出来，
     * 取决于有没有一处把整个面盖住的尺子（名单式判据会把"漏列的文件"当成不存在，手册 §0 坑 ⑧）。
     *
     * 判据取的是**结构性那条**：`gallery/data` 里凡是**不 import `android.content.Context`** 的文件
     * 都在桌面的编译面里（带 Context 的那几颗 —— FeedCache / ForYouCache / TagDictionary ——
     * 在 `desktop/build.gradle.kts` 的 exclude 清单上，桌面今天不编译它们，
     * 而它们写的又是 `context.filesDir`，Windows 上根本走不到）。
     * 禁的是 `renameTo(file)` / `renameTo(target)` 这一对"改名盖到活文件上"的形状；
     * `file.renameTo(backup)` **不在禁列** —— 那是把读不出来的旧档另存成带时间戳的新名字，
     * 目标恒不存在，Windows 上照样成，且那是留档行为不是发布行为。
     */
    @Test
    fun 桌面编译面里的任何store都不许改名盖到活文件上() {
        val dir = File(DesktopSourceTree.repoRoot, "app/src/main/java/com/venera/compose/gallery/data")
        val shared = dir.walkTopDown().filter { it.isFile && it.extension == "kt" }
            .filterNot { DesktopSourceTree.codeText(it).contains("android.content.Context") }
            .toList()
        val offenders = shared.filter { file ->
            val code = DesktopSourceTree.codeText(file)
            code.contains("renameTo(file)") || code.contains("renameTo(target)")
        }
        assertTrue(
            "这些 store 会被桌面编译，而 `renameTo` 在 Windows 上不覆盖已有文件（第二笔起全丢）：" +
                offenders.joinToString(" · ") { it.name },
            offenders.isEmpty(),
        )
        // 对照组：扫到的确实是"桌面编译面"那一族，而不是扫了个空目录让上面那条恒真。
        assertTrue("对照组失效：一颗都没扫到（目录或过滤器错了）", shared.isNotEmpty())
        val names = shared.map { it.name }
        assertTrue(
            "对照组失效：三颗修好的 store 不在扫描结果里（过滤器把它们也滤掉了）",
            names.containsAll(
                listOf("GalleryArtistAvatarStore.kt", "GalleryArtistFollowsStore.kt", "GalleryFavoritesStore.kt"),
            ),
        )
        assertTrue(
            "对照组失效：带 Context 的那颗本该被排除却没被排除（判式取错了面）",
            names.contains("GalleryFeedCache.kt").not(),
        )
    }
}
