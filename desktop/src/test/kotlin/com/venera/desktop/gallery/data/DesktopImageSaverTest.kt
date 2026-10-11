package com.venera.desktop.gallery.data

import com.venera.compose.gallery.data.GalleryPost
import com.venera.compose.gallery.data.GallerySite
import com.venera.compose.gallery.domain.GallerySaveNaming
import com.venera.compose.gallery.domain.GallerySettingsModel
import com.venera.desktop.gallery.ui.DesktopSourceTree
import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 批 85 · 桌面自持的那颗存图层。
 *
 * ## 这一格缺的到底是哪一层（题面当场改窄过）
 *
 * 排期表原本把"接 Coil 单例"也算进这一批 —— 现量发现那层 **批 79 就装好了**
 * （`DesktopImagePipeline.kt:62-65` 装的就是 `SingletonImageLoader`），而 `DesktopCoilBytes.kt`
 * 已在吃同一份单例。所以这里钉的只有落盘那一层：字节 → 一条用户数得出的路径。
 *
 * ## 三条口径各由一颗判据钉着（与批 54 那颗同族形状）
 *
 * 1. **算落点不许建目录**：那一路要建，是因为**真要把东西写下去**；而首页「下载」那一屏只是去看一眼
 *    —— 看一眼就在盘上多出一只目录，是"读数把自己写的东西当成事实"那一族（与 `DesktopDatabasePorts.dbFileAt`
 *    刻意不走 `subDir` 同一条理由）。
 * 2. **命名档不重述**：文件名那颗判据住在共享面 `GallerySettingsModel.fileName`，桌面只递档位与字节。
 *    重述一遍就是将来只改一处，而对岸那颗是**带用例的**（改了对岸这里会红，反过来自拼不会）。
 * 3. **读数带不出地址**：站方那一路地址常带 `api_key`/`user_id`，所以两枚读数函数的形参里就没有地址，
 *    失败那一档只交回**异常类型名**而不是 message（Coil/OkHttp 的 message 里就含着完整 URL）。
 *
 * ⚠️ 0 字节那一档是**独立一句**，不并进"没取到字节"：对岸 `GallerySaver` 为它专门写了
 * `throw IOException("站方给了 0 字节")`，因为 OkHttp 那条路上它是"成功但空 body"。
 */
class DesktopImageSaverTest {

    private val tempRoot: File = Files.createTempDirectory("b85_save").toFile()

    /** 只给 dataRoot/cacheRoot 的一枚假 PathProvider：`subDir` 有默认实现，桌面这一颗本来也不靠它建存图目录。 */
    private fun paths(root: File = tempRoot) = object : com.venera.compose.data.platform.PathProvider {
        override val dataRoot: File = root
        override val cacheRoot: File = File(root, "cache")
    }

    private fun post(
        id: Long = 123L,
        ext: String = "jpg",
        fileUrl: String = "https://img.example/a/123.jpg?api_key=SECRET",
    ) = GalleryPost(
        site = GallerySite.YANDERE,
        id = id,
        previewUrl = "https://img.example/p/123.jpg",
        fileUrl = fileUrl,
        fileExt = ext,
    )

    // ① 算落点这一颗**只算不建**（那一屏只是去看一眼，看一眼不许在盘上多出目录）。
    @Test
    fun `算落点不建目录`() {
        val dir = desktopGallerySaveDir(paths())
        assertEquals("落点要在数据根下的 downloads 里", "downloads", dir.parentFile.name)
        assertEquals("目录名", "图库", dir.name)
        assertFalse("算了一下就在盘上建出目录：$dir", dir.exists())
    }

    // ② 目录名与对岸那一串同轨。⚠️ 这把尺子扫的是**对岸的文件**：那句搬家了它必须红给人看，
    //    不许退化成"找不到就算通过"（坑 ㊞ 那一族：被扫的那句搬走后尺子恒绿）。
    @Test
    fun `目录名与对岸同一串`() {
        val saver = DesktopSourceTree.codeText(DesktopSourceTree.androidSource("gallery/data/GallerySaver.kt"))
        assertTrue(
            "对岸那颗目录名常量找不到了（搬家了就跟着改判这一条，别让它恒绿）",
            saver.contains("const val DIR_NAME = \"图库\""),
        )
        assertEquals("桌面这一颗与对岸同串", "图库", DESKTOP_GALLERY_DIR_NAME)
    }

    // ③ 重名先由我们自己让位（`名字 (1).jpg`），而不是让文件系统覆盖掉那一张。
    @Test
    fun `重名让位`() {
        val dir = File(tempRoot, "collide").apply { mkdirs() }
        File(dir, "a.jpg").writeBytes(byteArrayOf(1))
        assertEquals("撞一次让一位", "a (1).jpg", desktopSaveUniqueName(dir, "a.jpg"))
        File(dir, "a (1).jpg").writeBytes(byteArrayOf(1))
        assertEquals("撞两次让两位", "a (2).jpg", desktopSaveUniqueName(dir, "a.jpg"))
        File(dir, "plain").writeBytes(byteArrayOf(1))
        assertEquals("没有扩展名也照让", "plain (1)", desktopSaveUniqueName(dir, "plain"))
    }

    // ④ 让位的**写法**与对岸同形（那一段住在被排除的 `GallerySaver.kt` 里，桌面拿不到，只能各持一颗 ——
    //     所以这一条是"两颗必须同轨"的唯一守卫）。
    @Test
    fun `让位写法与对岸同形`() {
        val saver = DesktopSourceTree.codeText(DesktopSourceTree.androidSource("gallery/data/GallerySaver.kt"))
        assertTrue(
            "对岸那句让位写法变了 ⇒ 桌面这颗要跟着改判，不许两边各写一种",
            saver.contains("\"\$base (\$index)\$suffix\""),
        )
    }

    // ⑤ 文件名转交共享那颗，桌面不自己拼；命名档取对岸的默认档 SITE_ID。
    @Test
    fun `文件名转交共享那颗`() {
        val expected = GallerySettingsModel.fileName(post(), GallerySaveNaming.SITE_ID, null)
        assertEquals("逐字转交", expected, desktopSaveImageFileName(post()))
        assertTrue("默认档就是 <站名键>-<号>.<扩展名>：" + expected, expected.endsWith(".jpg"))
        val saver = DesktopSourceTree.desktopSourceOf("gallery/data/DesktopImageSaver.kt")
        val text = DesktopSourceTree.codeText(saver)
        assertTrue("没转交共享那颗", text.contains("GallerySettingsModel.fileName"))
        assertTrue("档位没取对岸的默认档", text.contains("GallerySaveNaming.SITE_ID"))
        assertEquals("桌面自己拼了一次站名键（那颗共享件就管不到它了）：" + text, 0, DesktopSourceTree.codeOccurrences(saver, "routeKey"))
    }

    // ⑥ 四种失败各一句、两两不许相同（「没地址 / 没字节 / 0 字节 / 写不进去」是四句话）。
    @Test
    fun `四种失败各念一句`() {
        val lines = DesktopSaveImageCause.entries.map { desktopSaveImageFallback(it) }
        assertEquals("四档要各一句", 4, lines.size)
        assertEquals("有两句撞在一起：" + lines, lines.size, lines.distinct().size)
        assertTrue("每句都得能上屏：" + lines, lines.all { it.isNotBlank() })
    }

    // ⑦ 存的永远是原图那一档；站方没给地址时判得出 null（那时连目录都不碰）。
    @Test
    fun `取地址只认原图那一档`() {
        assertEquals("原档", "https://img.example/a/123.jpg?api_key=SECRET", desktopSaveImageUrl(post()))
        assertNull("站方没给原图地址", desktopSaveImageUrl(post(fileUrl = "   ")))
    }

    // ⑧ 0 字节不当成保存成功，也不在盘上留一只空文件（对岸为它专门抛过错）。
    @Test
    fun `零字节不算保存成功`() {
        val dir = File(tempRoot, "zero").apply { mkdirs() }
        val outcome = desktopSaveImageWrite(dir, post(), ByteArray(0))
        assertTrue("0 字节被读成了成功：" + outcome, outcome is DesktopSaveImageOutcome.Refused)
        assertEquals(DesktopSaveImageCause.ZERO_BYTES, (outcome as DesktopSaveImageOutcome.Refused).cause)
        assertEquals("盘上不该留下任何东西：" + dir.list()?.toList(), emptyList<String>(), dir.list()?.toList())
    }

    // ⑨ 目录建不出来时说得出是哪一种、并带得出异常类型（不许原样交回、也不许换个地方藏）。
    @Test
    fun `目录建不出来就如实报错`() {
        val blocker = File(tempRoot, "blocker").apply { writeText("我不是目录") }
        val dir = File(blocker, "图库")
        val outcome = desktopSaveImageWrite(dir, post(), byteArrayOf(1, 2, 3))
        assertTrue("写不进去却被读成了成功：" + outcome, outcome is DesktopSaveImageOutcome.Refused)
        val refused = outcome as DesktopSaveImageOutcome.Refused
        assertEquals(DesktopSaveImageCause.DISK, refused.cause)
        assertNotNull("这一档是真的抛了东西，类型名必须带得出", refused.errorType)
    }

    // ⑩ 落盘的字节数从**盘上那颗文件**回读，而不是拿请求参数里的数（读数从状态现取）。
    @Test
    fun `字节数从盘上回读`() {
        val dir = File(tempRoot, "real").apply { mkdirs() }
        val bytes = ByteArray(4096) { (it % 251).toByte() }
        val saved = desktopSaveImageWrite(dir, post(), bytes)
        assertTrue("没写成：" + saved, saved is DesktopSaveImageOutcome.Saved)
        val onDisk = saved as DesktopSaveImageOutcome.Saved
        assertEquals("报的路径就是真落点", true, File(onDisk.path).exists())
        assertEquals("报的字节数等于盘上那颗的实际大小", Files.size(File(onDisk.path).toPath()), onDisk.bytesOnDisk)
        assertEquals("实际大小就是写进去的那些", 4096L, onDisk.bytesOnDisk)
        val text = DesktopSourceTree.codeText(DesktopSourceTree.desktopSourceOf("gallery/data/DesktopImageSaver.kt"))
        assertTrue(
            "那个数不是从盘上量的（它拿的是传入的字节数 ⇒ 半截失败也会报「已存好」）：" + text,
            text.contains("bytesOnDisk = readBackSize(target)"),
        )
        assertTrue(
            "回读那颗件自己不看文件（形参只剩一枚 File 才算结构上拿不到请求字节）：" + text,
            text.contains("private fun readBackSize(file: File): Long") && text.contains("Files.size(file.toPath())"),
        )
    }

    // ⑪ 重名走让位而不是覆盖：同一条存两次，盘上必须留得下两张。
    @Test
    fun `第二次存不覆盖第一张`() {
        val dir = File(tempRoot, "twice").apply { mkdirs() }
        val first = desktopSaveImageWrite(dir, post(), ByteArray(8) { 1 }) as DesktopSaveImageOutcome.Saved
        val second = desktopSaveImageWrite(dir, post(), ByteArray(8) { 2 }) as DesktopSaveImageOutcome.Saved
        assertFalse("两次落在同一颗文件上（那第二张就把第一张洗掉了）", first.path == second.path)
        assertEquals("盘上两张", 2, dir.listFiles()?.size)
        assertTrue("第一张还在", File(first.path).readBytes().all { it == 1.toByte() })
    }

    // ⑫ 两枚读数都带不出站方地址（失败那一档只交回类型名，message 里含着整串 URL）。
    @Test
    fun `读数带不出地址`() {
        // 先钉**形参表整串**：那句"结构上拿不到"要能在有人多递一枚 post 进来时当场红，而不是红在措辞上。
        val saver = DesktopSourceTree.codeText(DesktopSourceTree.desktopSourceOf("gallery/data/DesktopImageSaver.kt"))
        assertTrue("屏上那枚读数的形参表整串：" + saver, saver.contains("fun desktopSaveImageScreenReading(outcome: DesktopSaveImageOutcome): String"))
        assertTrue("stdout 那枚读数的形参表整串：" + saver, saver.contains("fun desktopSaveImageStdoutReading(outcome: DesktopSaveImageOutcome): String"))
        val saved = DesktopSaveImageOutcome.Saved(name = "yandere-123.jpg", path = "D:\\x\\yandere-123.jpg", bytesOnDisk = 4096L)
        val stdout = desktopSaveImageStdoutReading(saved)
        val screen = desktopSaveImageScreenReading(saved)
        assertTrue("stdout 那行要能被 grep 挑出来：" + stdout, stdout.startsWith("D_存图"))
        assertTrue("少了字节那枚：" + stdout, stdout.contains("4096"))
        assertTrue("少了文件名：" + stdout, stdout.contains("yandere-123.jpg"))
        assertFalse("读数里出现了 http：" + stdout, stdout.contains("http"))
        assertFalse("屏上那句出现了 http：" + screen, screen.contains("http"))
        val refused = DesktopSaveImageOutcome.Refused(DesktopSaveImageCause.NO_BYTES, "SocketTimeoutException")
        val refusedLine = desktopSaveImageStdoutReading(refused)
        assertTrue("失败那行没念成因：" + refusedLine, refusedLine.contains(desktopSaveImageFallback(DesktopSaveImageCause.NO_BYTES)))
        assertTrue("失败那行没带出异常类型：" + refusedLine, refusedLine.contains("SocketTimeoutException"))
        assertFalse("失败那行出现了 http：" + refusedLine, refusedLine.contains("http"))
    }

    // ⑬ 「没存过」是**三种**状态：目录还没建 / 目录在而里面空着 / 读不到（不是两句话）。
    @Test
    fun `枚举分得清三态`() {
        val missing = desktopListSavedImages(File(tempRoot, "not-yet"))
        assertTrue("目录不存在那一档：" + missing, missing is DesktopSavedListing.Missing)
        val emptyDir = File(tempRoot, "empty").apply { mkdirs() }
        assertTrue("目录在而空着那一档：" + emptyDir, desktopListSavedImages(emptyDir) is DesktopSavedListing.Empty)
        val listed = File(tempRoot, "listed").apply { mkdirs() }
        val older = File(listed, "a.jpg").apply { writeBytes(ByteArray(100)) }
        val newer = File(listed, "b.jpg").apply { writeBytes(ByteArray(200)) }
        // 时刻**自己钉**而不是靠"写文件先后自然差一秒"：NTFS 的粒度够细，但同一趟里两笔可能落在同一格，
        // 那时排序读数的先后就成了掷硬币 —— 掷硬币的尺子下一轮就会红得莫名其妙。
        stamp(older, 1_700_000_000_000L)
        stamp(newer, 1_700_000_600_000L)
        File(listed, "子目录").mkdirs()
        val result = desktopListSavedImages(listed) as DesktopSavedListing.Listed
        assertEquals("新的在前，且目录不当成一张图：" + result.items.map { it.name }, listOf("b.jpg", "a.jpg"), result.items.map { it.name })
        assertEquals("字节数从盘上取", 100L, result.items.last().bytes)
    }

    private fun stamp(file: File, epochMillis: Long) = Files.setLastModifiedTime(
        file.toPath(),
        java.nio.file.attribute.FileTime.fromMillis(epochMillis),
    )
}
