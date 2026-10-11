package com.venera.desktop.platform

import java.io.File
import java.nio.file.Files

/**
 * 桌面自己把 sqlite-jdbc 那颗原生库落到磁盘上，再把驱动认的那两枚属性指过去。
 *
 * 为什么要我们代劳（批 82.9b，2026-10-10）：交付包里驱动自己去 jar 里取那一档时
 * `Class.getResource` 交回 null ⇒ `NativeLibraryNotFoundException`，而开发跑同一颗 jar 非 null
 * （四条候选已量掉，见 `docs/rounds/desktop-package-runtime-modules-2026-10-10.md` §4.1；
 * "为什么包内 null"到今天没有定论）。我们这一侧的包内资源读法在包里是**量过能用的**
 * （`/sources/` 34 份、词典那份都读到了），所以由我们取字节、落盘、指属性。
 * §4.2 已用包内 exe 证过：属性指到一份真实 dll 之后 `System.load` 那条路是通的。
 *
 * 落点为什么是**调用点递进来的那棵目录**而不是 `java.io.tmpdir`（第二格读数，同一批）：
 * 第一版走 `Files.createTempDirectory`（默认落 `C:\Users\leimi\AppData\Local\Temp\`），
 * 包内 exe 当场 `AccessDeniedException`（栈：`WindowsFileSystemProvider.createDirectory`），
 * 而**同一个包进程**在数据根里写入回读是验过的（`D_数据目录 …（写入回读已验）`；那次跑的数据根
 * 已被 `LOCALAPPDATA` 指到 `_qa/b828_local`，即 D 盘）。开发跑同一行 `createTempDirectory` 是成功的
 * ⇒ 差的不是路径就是"那个进程被允许往哪儿写"，**成因未定论**，这里只按量到的那一侧走：
 * 落在库文件旁边（同一棵数据根里），并且隔离数据根会把它一起隔离开。
 *
 * 三件事刻意不做：
 *  - **不"先让驱动自己试、失败再兜底"** —— 两条路会漂成"只有开发跑能读库"，正是这一族的成因；
 *  - **认不出的档位不就近猜**（x86_64 的二进制发给 arm64 只会崩在更远的地方），直接抛并点名 os 两枚值；
 *  - **不把 dll 作为文件随包** —— 那要按 `os.arch` 在打包期选档，而打包期手上只有构建机那一档。
 */
internal object DesktopSqliteNative {

    /** sqlite-jdbc 自己那颗 jar 里原生库的布局。 */
    private const val NATIVE_ROOT = "/org/sqlite/native"
    private const val LIB_BASE = "sqlitejdbc"

    /** 落点里那棵子目录的名字（摆在库文件旁边）。 */
    private const val NATIVE_DIR = "sqlite-native"

    /** 驱动认的两枚属性：给了 `lib.path` 它就不再去 jar 里找资源。 */
    private const val LIB_PATH_PROPERTY = "org.sqlite.lib.path"
    private const val LIB_NAME_PROPERTY = "org.sqlite.lib.name"

    /** [entry] 是"从哪一条资源取的"，出事时要能点名；[dir]/[file]/[bytes] 是落点现取的读数。 */
    data class Placed(val entry: String, val dir: String, val file: String, val bytes: Long)

    /** 档位算式：系统与架构各只认表里那几串，认不出交回 null（调用点负责响，不许猜一档）。 */
    fun nativeEntry(osName: String, osArch: String): String? {
        val os = when {
            osName.startsWith("Windows", ignoreCase = true) -> "Windows"
            osName.startsWith("Mac", ignoreCase = true) || osName.startsWith("Darwin", ignoreCase = true) -> "Mac"
            osName.startsWith("Linux", ignoreCase = true) -> "Linux"
            else -> return null
        }
        val arch = when (osArch.lowercase()) {
            "amd64", "x64", "x86_64" -> "x86_64"
            "arm64", "aarch64" -> "aarch64"
            else -> return null
        }
        val fileName = when (os) {
            "Windows" -> "$LIB_BASE.dll"
            "Mac" -> "lib$LIB_BASE.dylib"
            else -> "lib$LIB_BASE.so"
        }
        return "$NATIVE_ROOT/$os/$arch/$fileName"
    }

    /**
     * 取字节 → 落到 [root] 底下（库文件那一侧，不是 `java.io.tmpdir`：见类注释第二格读数）→
     * 把驱动认的那两枚属性指过去。
     * [readResource] 是缝：真跑走包内资源，用例递一份假的进来验"取不到必须响"那一格。
     * 同档位那份字节数一致时**复用而不重写** —— 正在用的 dll 会被系统锁住，重写必红在写那一跳。
     */
    fun place(root: File, readResource: (String) -> ByteArray?): Placed {
        val osName = System.getProperty("os.name")
        val osArch = System.getProperty("os.arch")
        val entry = nativeEntry(osName, osArch)
            ?: throw IllegalStateException(
                "认不出这一台机器的 SQLite 原生库档位：os.name=$osName os.arch=$osArch" +
                    "（sqlite-jdbc 的档位表里没有这一档，不就近取另一档）",
            )
        val bytes = readResource(entry)
            ?: throw IllegalStateException("取不到 SQLite 的原生库：$entry（档位算得出而包里没有这一条）")
        val dir = File(root, NATIVE_DIR)
        if (!dir.mkdirs() && !dir.isDirectory) {
            throw IllegalStateException("建不出 SQLite 原生库的落点目录：${dir.path}")
        }
        // 档位在文件名里：换系统/换架构不会撞上同一颗
        val file = File(dir, entry.removePrefix("$NATIVE_ROOT/").replace('/', '-'))
        if (!(file.isFile && file.length() == bytes.size.toLong())) {
            file.writeBytes(bytes)
        }
        System.setProperty(LIB_PATH_PROPERTY, dir.absolutePath)
        System.setProperty(LIB_NAME_PROPERTY, file.name)
        return Placed(entry, dir.absolutePath, file.name, file.length())
    }

    /** 落点由调用点给 ⇒ 每一棵数据根各落各的（隔离数据根跑实验时不会互相踩）。 */
    private val memo = LinkedHashMap<String, Placed>()

    /** 同一棵 root 一次 JVM 只落一份（每次取连接都重落 1 MB 没有意义，而落点会被正在用的 dll 锁住）。 */
    @Synchronized
    fun ensure(root: File): Placed = memo.getOrPut(root.absolutePath) {
        place(root) { name -> DesktopSqliteNative::class.java.getResourceAsStream(name)?.use { it.readBytes() } }
    }

    /** 那句读数从落点现取，不从请求值回读（"包里有货"与"落到位了"是两件事）。 */
    fun reading(placed: Placed): String =
        "D_原生库 档=" + placed.entry + " 落点=" + placed.dir + " 字节=" + placed.bytes
}
