package com.venera.desktop.gallery.data

import com.venera.compose.data.platform.PathProvider
import com.venera.compose.gallery.data.GalleryPost
import com.venera.compose.gallery.data.GallerySite
import com.venera.desktop.gallery.ui.DesktopSourceTree
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 批 86 · 桌面自持的那颗**图库浏览历史**。
 *
 * ## 这一格缺的是哪一层（题面在 rounds 文档 §1.3）
 *
 * 首页「历史」那一行的三条判据是「有存储 **且** 注册表有 `HISTORY` **且** 有内容件」，
 * 现量：存储与那一屏今天一起补上。
 * ⚠️ `data/db` 里那张 `comic_history` **不是**它（那是漫画阅读进度，批 82 起桌面真在写，
 * 首页「阅读历史」那一行读的就是它）。把两件事记成一件，本仓红过一次（批 14 那句假话）。
 *
 * ## 五条不显然的口径，各由一颗判据钉着
 *
 * 1. **键是「站 + 号」两枚**（② 专门拿同号异站喂它）：三站各有自己的编号空间。
 *    本仓记过一枚同族的债 —— 复合键两端不同轨（一头恒传空串），档位在而永远命中不了，单测全绿。
 * 2. **只存"看过"这一件事**（⑨ 逐键数磁盘字段）：站键、号、预览档地址、时刻，四样。
 *    不存原图地址与宽高 = 不欠"它过期了"这笔账；也因此那一屏摆不出"点开回原卡"，
 *    于是本批**不摆**那一枚按钮（摆了就是一枚只能带回半屏的假开关）。
 * 3. **磁盘那份只有一份事实**（⑰）：浮层写、那一屏读，两处必须拿**同一颗**实例，
 *    两颗各记各的内存 = 刚点开的那张在屏上看不见。
 * 4. **不设条数上限**（⑩）：对岸那两颗同类件（收藏 / 关注）都没有上限，这里不造一个新数字。
 * 5. **缺档与坏档是两句话**（⑦⑧）：缺 = 还没看过，坏 = 读不开且**先留档再开空档**。
 *    直接覆盖等于把用户攒的记录删了 —— 对岸那两颗 store 就是这么定的，这里照它。
 */
class DesktopGalleryBrowseHistoryTest {

    private val tempRoot: File = Files.createTempDirectory("b86_history").toFile()

    /** 只给 dataRoot/cacheRoot 的一枚假 PathProvider：与批 85 那颗同形状，这颗件也只认 dataRoot。 */
    private fun paths(root: File = tempRoot) = object : PathProvider {
        override val dataRoot: File = root
        override val cacheRoot: File = File(root, "cache")
    }

    private fun post(
        site: GallerySite = GallerySite.YANDERE,
        id: Long = 123L,
        preview: String = "https://p.example/$id.jpg",
        fileUrl: String = "https://f.example/$id.png?api_key=SECRET",
    ) = GalleryPost(site = site, id = id, previewUrl = preview, fileUrl = fileUrl)

    private fun view(
        site: GallerySite = GallerySite.YANDERE,
        id: Long = 123L,
        at: Long = 1_000L,
        thumb: String = "https://p.example/$id.jpg",
    ) = DesktopBrowseView(site = site, postId = id, thumbUrl = thumb, viewedAt = at)

    // ① 同一张图反复点开**只留一行**，且那一行的时刻是最近一次、位置顶到最前。
    @Test
    fun 同一张图只留一行而时刻跟着刷新() {
        val rows = desktopBrowseRowsViewed(
            listOf(view(id = 7L, at = 100L), view(id = 9L, at = 200L)),
            view(id = 7L, at = 300L),
        )
        assertEquals("重复点开不该叠出第二行：${rows.map { it.postId }}", 2, rows.size)
        assertEquals("最近点开的排最前", 7L, rows[0].postId)
        assertEquals("另一行还在", 9L, rows[1].postId)
        assertEquals("时刻要换成最近这一次", 300L, rows[0].viewedAt)
    }

    // ② 复合键：**同号异站是两张图**，不许互相顶掉。这条是本批最要紧的一颗尺子。
    @Test
    fun 同一个号在两个站是两行() {
        val rows = desktopBrowseRowsViewed(
            listOf(view(site = GallerySite.GELBOORU, id = 123L, at = 100L)),
            view(site = GallerySite.YANDERE, id = 123L, at = 200L),
        )
        assertEquals("站点键不同就是两张图，别拿号当唯一键", 2, rows.size)
        assertEquals(
            "两行各自认自己的站",
            listOf(GallerySite.YANDERE, GallerySite.GELBOORU),
            rows.map { it.site },
        )
    }

    // ③ 排序口径：时刻倒序，**同刻按站点键再按号**（同刻不是假想：一次连点两张会撞在同一毫秒）。
    @Test
    fun 同刻的两行按站键与号定序() {
        val rows = desktopBrowseRowsSorted(
            listOf(
                view(site = GallerySite.SAFEBOORU, id = 5L, at = 900L),
                view(site = GallerySite.YANDERE, id = 2L, at = 900L),
                view(site = GallerySite.YANDERE, id = 1L, at = 900L),
                view(site = GallerySite.YANDERE, id = 3L, at = 1_000L),
            ),
        )
        assertEquals(
            "倒序 + 同刻按站键再按号",
            listOf(
                Triple(GallerySite.YANDERE, 3L, 1_000L),
                Triple(GallerySite.SAFEBOORU, 5L, 900L),
                Triple(GallerySite.YANDERE, 1L, 900L),
                Triple(GallerySite.YANDERE, 2L, 900L),
            ),
            rows.map { Triple(it.site, it.postId, it.viewedAt) },
        )
    }

    // ④ 写盘之后新开一颗读回 = 同一份名单（含顺序）。跨进程读的是盘上那一份，不是内存。
    @Test
    fun 写盘之后新开一颗读回同一份() = runBlocking {
        val store = DesktopGalleryBrowseHistory(desktopBrowseHistoryFile(paths()))
        store.recordView(post(id = 11L), 1_000L)
        store.recordView(post(id = 22L), 2_000L)
        val reopened = DesktopGalleryBrowseHistory(desktopBrowseHistoryFile(paths()))
        assertEquals("读回的行数与顺序", listOf(22L, 11L), reopened.rows.value.map { it.postId })
        assertEquals("缩略档也要落盘", "https://p.example/22.jpg", reopened.rows.value[0].thumbUrl)
        assertEquals("时刻同样落盘", 2_000L, reopened.rows.value[0].viewedAt)
    }

    // ⑤ 第二笔写**覆盖**第一笔而不是失败。Windows 上 `File.renameTo` 目标已存在只返回 false
    //    （本仓 5d74155 那一笔栽过：三颗 store 的原子写从第二笔起全丢），所以这里要真写三笔。
    @Test
    fun 第二次写不丢() = runBlocking {
        val file = desktopBrowseHistoryFile(paths())
        val store = DesktopGalleryBrowseHistory(file)
        store.recordView(post(id = 1L), 1_000L)
        store.recordView(post(id = 2L), 2_000L)
        store.recordView(post(id = 3L), 3_000L)
        val onDisk = file.readText()
        assertTrue("盘上那份要带第三笔：$onDisk", onDisk.contains("\"id\":3"))
        assertTrue("第二笔也不能被挤掉", onDisk.contains("\"id\":2"))
        assertEquals("三笔都在", 3, desktopBrowseReadRows(file).size)
    }

    // ⑥ 那颗件自己走的是「先写 tmp 再改名过去」，而且必须是**带覆盖选项**的那一颗。
    @Test
    fun 原子写用的是覆盖那条路() {
        val history = DesktopSourceTree.desktopSourceOf("gallery/data/DesktopGalleryBrowseHistory.kt")
        assertEquals(
            "落盘没走带 REPLACE_EXISTING 的改名（Windows 上 renameTo 目标已存在只返回 false）",
            1,
            DesktopSourceTree.codeOccurrences(history, "StandardCopyOption.REPLACE_EXISTING"),
        )
        assertFalse("还在用会静默失败的 renameTo", DesktopSourceTree.codeText(history).contains("renameTo"))
        assertEquals("临时档那一颗只许出现在这一处写里", 1, DesktopSourceTree.codeOccurrences(history, ".tmp"))
    }

    // ⑦ 缺档 = 还没看过：**空名单且不报坏**。把"没有"念成"读不开"是另一族假读数。
    @Test
    fun 没有那份文件时不报错() {
        val dir = Files.createTempDirectory("b86_missing").toFile()
        val store = DesktopGalleryBrowseHistory(desktopBrowseHistoryFile(paths(dir)))
        assertEquals("空名单", emptyList<Long>(), store.rows.value.map { it.postId })
        assertNull("缺档不许念成读不开", store.notice.value)
    }

    // ⑧ 坏档 = 读不开：**先把原文件留档**再开空档，并通过 notice 说一句，那句要点名留档落在哪。
    @Test
    fun 读不开的那份先留档再开空档() {
        val dir = Files.createTempDirectory("b86_corrupt").toFile()
        val file = desktopBrowseHistoryFile(paths(dir))
        file.parentFile.mkdirs()
        file.writeText("{这已经不是那份 JSON 了")
        val store = DesktopGalleryBrowseHistory(file)
        assertEquals("坏档交回空名单", emptyList<Long>(), store.rows.value.map { it.postId })
        assertNotNull("坏档必须说一句", store.notice.value)
        val kept = dir.listFiles { child -> child.name.contains(".corrupt-") }!!
        assertEquals("原文件要留档一份，不许直接覆盖：${kept.map { it.name }}", 1, kept.size)
        assertTrue("留档里还是原来那串坏内容", kept[0].readText().contains("已经不是"))
        assertTrue("屏上那句要点名留档的文件名", store.notice.value!!.contains(kept[0].name))
        assertFalse("坏档那份本体不许还留在原地当可读档", file.exists())
    }

    // ⑨ 磁盘那一行只有四个键：站键、号、预览档、时刻。**原图地址不许进档**（进了就欠一笔过期账）。
    @Test
    fun 磁盘那一行只有四个键() = runBlocking {
        val file = desktopBrowseHistoryFile(paths())
        DesktopGalleryBrowseHistory(file).recordView(post(id = 8L, fileUrl = "https://f.example/SECRET.png"), 1_000L)
        val line = file.readText()
        val keysOfFirst = line.substringAfter("{").substringBefore("}").split(",")
            .map { it.substringBefore(":").trim().replace("\"", "") }
        assertEquals("四个键，多一个都算越界", listOf("site", "id", "thumb", "viewed_at"), keysOfFirst)
        assertFalse("原图地址不许落盘", line.contains("SECRET"))
        assertEquals("存的预览档就是 previewUrl", "https://p.example/8.jpg", desktopBrowseReadRows(file)[0].thumbUrl)
    }

    // ⑩ 不设上限：1,000 条写完读回还是 1,000 条（对岸那两颗同类件也没有 cap，不造新数字）。
    @Test
    fun 一千条不截断() {
        // 号从 1 起：`id=0` 是判据 ⑪ 里那一档"坏行"，拿它当样例会让这一条数出 999 而看不出原因。
        val rows = (1L..1_000L).fold(emptyList<DesktopBrowseView>()) { acc, id ->
            desktopBrowseRowsViewed(acc, view(site = GallerySite.GELBOORU, id = id, at = 1_000L + id))
        }
        assertEquals("进行集不许丢行", 1_000, rows.size)
        val file = desktopBrowseHistoryFile(paths())
        desktopBrowseWriteRows(file, rows)
        val read = desktopBrowseReadRows(file)
        assertEquals("写盘读回也不许丢行", 1_000, read.size)
        assertEquals("最新那条在最前", 1_000L, read.first().postId)
    }

    // ⑪ 认不出站点键 / 号是 0 的那一行**当场摘掉**（对岸那颗收藏件同一条判据），屏上念不到半截行。
    @Test
    fun 坏行当场摘掉而不是念半截() {
        val decoded = desktopBrowseDecode(
            """[{"site":"yandere","id":1,"thumb":"https://p/1.jpg","viewed_at":10},
               {"site":"某站不认识","id":2,"thumb":"https://p/2.jpg","viewed_at":20},
               {"site":"gelbooru","id":0,"thumb":"https://p/3.jpg","viewed_at":30}]""",
        )
        assertEquals("两行该被摘掉：${decoded.map { it.postId }}", 1, decoded.size)
        assertEquals(1L, decoded[0].postId)
        assertEquals("站点键翻成枚举", GallerySite.YANDERE, decoded[0].site)
    }

    // ⑫ 表头分两种说法：没点开过 vs 看过 N 张，而"没点开过"那一档不许念成"读不到"。
    @Test
    fun 表头分得清没看过与看过几张() {
        assertEquals(
            "空的那一档要说清这里会怎么写",
            "本机还没点开过图 —— 点开一张卡就记在这里",
            desktopBrowseHeaderReading(emptyList()),
        )
        val one = desktopBrowseHeaderReading(listOf(view(at = 1_700_000_000_000L)))
        assertTrue("一张也要带出条数：$one", one.contains("1 张"))
        val many = (1L..3L).map { view(id = it, at = 1_700_000_000_000L + it) }
        val reading = desktopBrowseHeaderReading(many)
        assertTrue("三张要说三张：$reading", reading.contains("3 张"))
        assertTrue("要带最近那一格的时刻：$reading", reading.contains("最近 "))
        assertFalse("表头不许带出站方地址：$reading", reading.contains("http"))
    }

    // ⑬ 一行念「站名 · #号 · 时刻」，用的是站点的**显示名**而不是 routeKey。
    @Test
    fun 每一行念站名与号() {
        val line = desktopBrowseRowLine(view(site = GallerySite.SAFEBOORU, id = 42L, at = 1_700_000_000_000L))
        assertTrue("要带站名：$line", line.contains(GallerySite.SAFEBOORU.displayName))
        assertTrue("要带号：$line", line.contains("#42"))
        assertFalse("不许把 routeKey 念给用户：$line", line.contains(GallerySite.SAFEBOORU.routeKey))
    }

    // ⑭ 落点这颗**只算不建**（与批 85 那颗存图件同一条：算一下不许在盘上多出目录）。
    @Test
    fun 算落点不建目录() {
        val root = Files.createTempDirectory("b86_dir").toFile()
        val file = desktopBrowseHistoryFile(paths(File(root, "venera")))
        assertEquals("档名", "gallery_browse_history.json", file.name)
        assertEquals("落在数据根这一层", File(root, "venera").absolutePath, file.parent)
        assertFalse("算了一下就在盘上建出目录：${file.parentFile}", file.parentFile.exists())
    }

    // ⑮ stdout 那句读数要能算账，但**带不出地址**（站方地址常含 api_key）。
    @Test
    fun 读数里不许出现地址() {
        val reading = desktopBrowseStdoutReading(view(id = 99L), totalRows = 4L)
        assertTrue("要念号：$reading", reading.contains("#99"))
        assertTrue("要念总条数：$reading", reading.contains("共 4 行"))
        assertFalse("不许带出 http：$reading", reading.contains("http"))
        // 形参表里就没有地址这一枚：整串签名钉住，别只钉标识符。
        val text = DesktopSourceTree.codeText(
            DesktopSourceTree.desktopSourceOf("gallery/data/DesktopGalleryBrowseHistory.kt"),
        )
        assertEquals(
            "读数那颗的形参表变了，地址就可能被顺手加进来",
            1,
            text.lines().count {
                it.trim().startsWith("internal fun desktopBrowseStdoutReading(row: DesktopBrowseView, totalRows: Long): String")
            },
        )
    }

    // ⑯ 字段名的蛇形口径与对岸那两颗 store 同轨。⚠️ 这把尺子扫的是**对岸的文件**：
    //    那句搬家了它必须红给人看（§0 坑 ㊞：扫源码的尺子会在被扫那句搬家后恒红）。
    @Test
    fun 时刻那个键名与对岸同形() {
        val follows = DesktopSourceTree.androidSource("gallery/data/GalleryArtistFollowsStore.kt")
        assertEquals(
            "对照组失效：对岸那颗 store 的蛇形键口径变了，这条尺子就没有对象了",
            1,
            DesktopSourceTree.codeOccurrences(follows, "@SerialName(\"added_at\")"),
        )
        val mine = DesktopSourceTree.desktopSourceOf("gallery/data/DesktopGalleryBrowseHistory.kt")
        assertEquals("本颗同一口径的键名", 1, DesktopSourceTree.codeOccurrences(mine, "@SerialName(\"viewed_at\")"))
    }

    // ⑰ 同一个数据根只许建**一颗**：浮层写的那颗与那一屏读的那颗必须是同一个对象。
    //     两颗各记各的内存，表现就是"刚点开的那张在屏上看不见"，而且单看每一颗都对。
    @Test
    fun 同一数据根拿到的就是同一颗() {
        val dir = Files.createTempDirectory("b86_singleton").toFile()
        assertSame(
            "getInstance 换了站点根之外每次新建，浮层与那一屏就会各记各的",
            DesktopGalleryBrowseHistory.getInstance(paths(dir)),
            DesktopGalleryBrowseHistory.getInstance(paths(dir)),
        )
    }

}
