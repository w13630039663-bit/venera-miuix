package com.venera.desktop.platform

import com.venera.desktop.gallery.ui.DesktopSourceTree
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * 批 82.9b：交付包里 sqlite-jdbc 取不到自己那颗原生库（`Class.getResource` 在包内交回 null，
 * 而开发跑非 null —— 见 `docs/rounds/desktop-package-runtime-modules-2026-10-10.md` §4.1）。
 * 这一族的正解是**我们自己**去 jar 里取那一档的字节、落到磁盘、再把驱动认的那两枚系统属性指过去
 * （§4.2 已证 `System.load` 那条路在包里是通的）。
 *
 * 落点为什么不是 `java.io.tmpdir`：包内进程在 Temp 里 `Files.createTempDirectory` 抛
 * `AccessDeniedException`，而同一个进程在数据根里写入回读是验过的（本批第二格读数）⇒
 * **落点由调用点递进来**，这几颗用例也就各自递自己那棵临时目录，不往真用户目录写。
 *
 * 尺子各管一层：档位算式（① ②）、真落盘并指属性且落在递进去那棵底下（③）、
 * 失败必须响而不许往下走（④）、接线顺序（⑤）、读数从落点现取 + 同一棵 root 只落一次（⑥）。
 */
class DesktopSqliteNativeTest {

    private fun tempRoot(name: String): File = Files.createTempDirectory("b829b-$name").toFile()

    /** ① 档位算式：OS 与架构各喂两种写法，认不出的档交回 null 而不是猜一档。 */
    @Test
    fun `原生库那一档按系统与架构算`() {
        assertEquals(
            "/org/sqlite/native/Windows/x86_64/sqlitejdbc.dll",
            DesktopSqliteNative.nativeEntry("Windows 11", "amd64"),
        )
        assertEquals(
            "/org/sqlite/native/Windows/aarch64/sqlitejdbc.dll",
            DesktopSqliteNative.nativeEntry("Windows 11", "aarch64"),
        )
        assertEquals(
            "/org/sqlite/native/Mac/x86_64/libsqlitejdbc.dylib",
            DesktopSqliteNative.nativeEntry("Mac OS X", "x86_64"),
        )
        assertEquals(
            "/org/sqlite/native/Linux/aarch64/libsqlitejdbc.so",
            DesktopSqliteNative.nativeEntry("Linux", "arm64"),
        )
        // 认不出的架构不许被"就近算成 x86_64"：那等于把 x86 的二进制发给 arm
        assertEquals(null, DesktopSqliteNative.nativeEntry("Linux", "sparc"))
        assertEquals(null, DesktopSqliteNative.nativeEntry("OS/2", "amd64"))
    }

    /** ② 本机这一档必须算得出来 —— 算不出来时 ③④ 都在测空气。 */
    @Test
    fun `本机这一档算得出来`() {
        val entry = DesktopSqliteNative.nativeEntry(
            System.getProperty("os.name"),
            System.getProperty("os.arch"),
        )
        assertNotNull("本机这一档算不出来：${System.getProperty("os.name")} / ${System.getProperty("os.arch")}", entry)
        // 本仓跑在 Windows x64 上；这句同时钉住"下面那几颗真跑用例测的是这一档"
        assertTrue("本机档位：$entry", entry!!.endsWith("sqlitejdbc.dll"))
    }

    /** ③ 真跑：字节从资源里来、落在**递进去的那棵 root** 底下、驱动认的那两枚属性指过去。 */
    @Test
    fun `落盘之后把驱动认的那两枚属性指过去`() {
        val root = tempRoot("place")
        val placed = DesktopSqliteNative.place(root) { name ->
            DesktopSqliteNative::class.java.getResourceAsStream(name)?.readBytes()
        }
        assertTrue(
            "落点跑到了递进去的那棵 root 外面：${placed.dir} 不在 ${root.path} 底下",
            File(placed.dir).canonicalPath.startsWith(root.canonicalPath),
        )
        val file = File(placed.dir, placed.file)
        assertTrue("落点文件不存在：${file.path}", file.isFile)
        assertEquals("落盘字节与报出来的不一致", placed.bytes, file.length())
        assertTrue("空文件不算落到位", placed.bytes > 0L)
        // 头两字节：Windows 是 MZ（PE），Mac/Linux 是 ELF（0x7f 'E'）—— 拿错档会在这里露出来
        val head = file.inputStream().use { it.readNBytes(4) }
        val magic = head.joinToString(" ") { it.toInt().toString(16) }
        assertTrue(
            "落盘的不是原生库：$magic",
            head.size >= 2 && ((head[0] == 'M'.code.toByte() && head[1] == 'Z'.code.toByte()) ||
                (head[0] == 0x7f.toByte() && head[1] == 'E'.code.toByte())),
        )
        assertEquals(placed.dir, System.getProperty("org.sqlite.lib.path"))
        assertEquals(placed.file, System.getProperty("org.sqlite.lib.name"))
    }

    /** ④ 取不到那一档必须响，且消息点名是哪一条资源。 */
    @Test
    fun `取不到原生库时抛错点名而不是静默往下走`() {
        val e = assertThrows(IllegalStateException::class.java) {
            DesktopSqliteNative.place(tempRoot("miss")) { null }
        }
        assertTrue(
            "报错没点名那一条资源：${e.message}",
            e.message != null && e.message!!.contains(
                DesktopSqliteNative.nativeEntry(
                    System.getProperty("os.name"),
                    System.getProperty("os.arch"),
                ) ?: "native",
            ),
        )
    }

    /** ⑤ 接线：那一跳必须排在第一次要连接之前（放在之后就是"第一笔永远失败"）。 */
    @Test
    fun `落原生库那一跳排在取连接之前`() {
        val source = DesktopSourceTree.codeText(
            File(DesktopSourceTree.repoRoot, "desktop/src/main/kotlin/com/venera/desktop/platform/JdbcSqliteDatabase.kt"),
        )
        val ensure = source.indexOf("DesktopSqliteNative.ensure(")
        val firstConnect = source.indexOf("DriverManager.getConnection(")
        assertTrue("JdbcSqliteDatabase 里没有落原生库那一跳", ensure >= 0)
        assertTrue("落原生库那一跳排在取连接之后", ensure in 0 until firstConnect)
    }

    /** ⑥ 那句读数从落点现取（不是从请求值回读），且同一棵 root 第二次不再走一遍落盘。 */
    @Test
    fun `读数念的是落点而同一棵 root 不重落`() {
        val root = tempRoot("ensure")
        val first = DesktopSqliteNative.ensure(root)
        val second = DesktopSqliteNative.ensure(root)
        // 只比路径会被"重新落了一遍、落在同一棵目录"骗过（P7 第一次就是这么跑出绿的）⇒ 比**是不是同一份**
        assertSame("第二次又叫 ensure 时又走了一遍落盘", first, second)
        assertEquals(first.dir, second.dir)
        val reading = DesktopSqliteNative.reading(first)
        assertTrue("读数没带前缀：$reading", reading.startsWith("D_原生库"))
        assertTrue("读数没念落点：$reading", reading.contains("落点=" + first.dir))
        assertTrue("读数没念字节：$reading", reading.contains("字节=" + first.bytes))
        assertTrue("读数没念档位：$reading", reading.contains("档=" + first.entry))
    }
}
