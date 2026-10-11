package com.venera.desktop.gallery.data

import com.venera.compose.data.platform.PathProvider
import com.venera.desktop.platform.DesktopLogger
import java.io.File
import java.net.JarURLConnection
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * 画廊离线词典那份 sqlite 在**桌面**上的取用口（批 40）。
 *
 * ## 为什么 Android 那一颗不能直接用
 *
 * `gallery/data/GalleryTagDictionary` 干了两件事：把资产从 `Context.assets` 复制成真实路径，
 * 再走 `data.platform.android.openReadOnlySqlDatabase` 只读打开。两件都上不了桌面编译面
 * （`desktop/build.gradle.kts` 的 exclude 清单上就是那两枚理由），所以桌面自持这一颗 ——
 * **只换"从哪读"，读法与档位判据仍走共享的 `data/db/TagDictionaryStore`**。
 *
 * ## classpath 的两种形状（2026-10-08 探针实测）
 *
 * - 打包（jar）：锚点 URL 是 `jar:file:…!/venera-init.js`，条目清单要从 `JarURLConnection.jarFile` 枚举；
 *   这条连接是 JVM 缓存的，**不许关**（关了之后同包内资源再取就取不到，与 `EngineAssets` 那条同坑）。
 * - 开发跑（`:desktop:app` / `:desktop:test`）：锚点 URL 是 `file:`，父目录就是资源根，`listFiles` 即可。
 *
 * 锚点必须钉在一颗**随包资源**上而不是类文件上：`classLoader.getResource("")` 在 jar 形状实测回 `null`，
 * 而 exploded 形状下类的落点（`build/classes/kotlin/main`）与资源的落点（`build/resources/main`）是两个目录，
 * 拿 `codeSource` 会指错地方。`venera-init.js` 是装载序列无条件要读的那颗，包里有它 ≡ 包是完整的。
 *
 * ## 为什么还要复制一份出来
 *
 * sqlite-jdbc 与 Android 一样只认真实路径，而 jar 里那条条目不是文件。副本文件名**就是资产文件名**
 * （里面带行数）：词典换一批 → 行数变 → 名字变 → 重新复制。只按"存在就不复制"会在换词典后继续读旧副本，
 * 而屏上的表现是"有些标签就是没译名"，与真相（我们的副本过期了）在屏上长得一样 —— 那条口径是 Android 侧写的，
 * 这里逐字沿用。落盘走"先 `.tmp` 再改名"，与 `JsonKeyValueStore` 同一形：`File.renameTo` 在 Windows 上
 * 遇目标已存在直接返回 false，所以要 `Files.move` + `REPLACE_EXISTING`（能 `ATOMIC_MOVE` 就优先原子）。
 */
object DesktopTagDictionaryAsset {

    /** 资产名前缀：`gallery_tags_<行数>.sqlite`（由 `scripts/build_tag_dictionaries.mjs` 生成）。 */
    const val ASSET_PREFIX: String = "gallery_tags_"

    private const val ASSET_SUFFIX = ".sqlite"
    private const val CLASSPATH_ANCHOR = "/venera-init.js"
    private const val DB_DIR = "db"
    private const val TAG = "DesktopTagDictionaryAsset"

    /**
     * 包内（classpath 资源根）有哪些词典资产，按名字排序 —— 交回的是**哪一种读法**而不是清单。
     *
     * 批 78 的改判：从前枚举失败交**空清单**，而空清单在下游就是「包里没有」——
     * 「我们数不出来」与「包里真没带」压成了同一格（§1 那一族）。现在两格各交各的：
     * [DesktopDictRead.Hit] 是数出来了（可以是空），[DesktopDictRead.Miss] 带着成因。
     * 锚点本身不在（包残缺）也归"数不出来"那一格，因为那时连"哪种形状"都判不了。
     */
    internal fun classpathListing(): DesktopDictRead<List<String>> {
        val url = javaClass.getResource(CLASSPATH_ANCHOR)
            ?: return DesktopDictRead.Miss(
                DesktopDictWhy.Unreadable(
                    IllegalStateException("classpath 上没有锚点资源 $CLASSPATH_ANCHOR，词典资产枚举无从起步（包不完整？）"),
                ),
            )
        val listed = when (url.protocol) {
            // 这条连接是 JVM 缓存的，**不许关**（关了之后同包内资源再取就取不到，与 `EngineAssets` 那条同坑）
            "jar" -> runCatching {
                val conn = url.openConnection() as JarURLConnection
                conn.jarFile.entries().asSequence()
                    .filter { !it.isDirectory && matches(it.name) }
                    .map { it.name }
                    .toList()
            }

            "file" -> runCatching {
                val dir = File(url.toURI()).parentFile ?: error("锚点没有父目录：${url.path}")
                val present = dir.listFiles { f: File -> f.isFile && matches(f.name) }
                    ?: error("列不出目录内容：${dir.path}")
                present.map { it.name }
            }

            else -> Result.failure(IllegalStateException("锚点 $CLASSPATH_ANCHOR 的协议是 ${url.protocol}，jar 与 exploded 两种形状都不认"))
        }
        return listed.fold({ DesktopDictRead.Hit(it.sorted()) }, { DesktopDictRead.Miss(DesktopDictWhy.Unreadable(it)) })
    }

    /**
     * 从清单推到"用哪一份资产"，三态各归各的（批 78）：
     * 命中给资产名 / 清单是空的或全认不出行数给[DesktopDictWhy.Absent] / 数不出来原样带着成因。
     *
     * 抽出来而不是埋在 `openDatabase()` 里，是因为"没有"与"数不出"这两格在真包上永远走不到另一格 ——
     * 留在里面就没有尺子（判据 ③ 逐格喂）。
     */
    internal fun assetOf(listing: DesktopDictRead<List<String>>): DesktopDictRead<String> = when (listing) {
        is DesktopDictRead.Miss -> listing
        is DesktopDictRead.Hit -> newest(listing.value)?.let { DesktopDictRead.Hit(it) }
            ?: DesktopDictRead.Miss(DesktopDictWhy.Absent)
    }

    /** 按名字里的**行数**取最新那一份；认不出行数的排 -1，空清单交 null。 */
    fun newest(names: List<String>): String? =
        names.filter { matches(it) }.maxByOrNull { it.rowCount() ?: -1 }

    /** 副本落点：`dataRoot/db/<资产名>`。**纯计算**，不建任何目录。 */
    fun copyFileAt(paths: PathProvider, assetName: String): File =
        File(File(paths.dataRoot, DB_DIR), assetName)

    /**
     * 把包内那份复制成真实路径并交回它；已存在就原样交回（不重复制、不动它）。
     *
     * @throws IllegalStateException 包内没有这颗资产、目录建不出、或复制/改名失败 —— 全部带路径
     */
    fun materialize(paths: PathProvider, assetName: String): File {
        val target = copyFileAt(paths, assetName)
        if (target.isFile) return target
        val dir = target.parentFile
            ?: throw IllegalStateException("词典副本没有父目录，建不出：${target.path}")
        if (!dir.mkdirs() && !dir.isDirectory) {
            throw IllegalStateException("建不出词典副本目录：${dir.path}")
        }
        val temp = File(dir, "$assetName.tmp")
        try {
            val stream = javaClass.getResourceAsStream("/$assetName")
                ?: throw IllegalStateException("包内没有 /$assetName（构建脚本的 resources.include 没带上那颗？）")
            stream.use { input -> temp.outputStream().use { to -> input.copyTo(to) } }
            Files.move(
                temp.toPath(), target.toPath(),
                StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE,
            )
            DesktopLogger.info(TAG, "词典副本落盘 $assetName 字节=${target.length()} 落点=${target.path}")
        } catch (e: AtomicMoveNotSupportedException) {
            // 文件系统不支持原子改名（跨卷那类）：退成"替换改名"，仍是一次搬动，不是重写活档
            Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
            DesktopLogger.info(TAG, "词典副本落盘（非原子）$assetName 字节=${target.length()} 落点=${target.path}")
        } finally {
            temp.delete()
        }
        return target
    }

    /**
     * 包里那份词典此刻是**哪种状态**（第五十八批）：有（带行数）/ 清单是空的 / 连清单都数不出来。
     *
     * 为什么不并成一枚 Int：批 78 之前 [classpathListing] 在枚举失败时交**空清单**，
     * 于是"包残缺/数不出"被念成"没打包典"（§1 那一格）；现在两格各归各的，读数吃的是这三态。
     * 措辞不住在这里 —— 命名规则（含行数解析）只许住在这颗件里，判据 ⑥ 钉着这一条。
     */
    internal fun presence(): DesktopDictionaryPresence = presence(classpathListing())

    /** 三态映射，抽成吃清单的纯那一形（判据 ② 逐格喂：真包上永远只有一格走得到，另两格量不到）。 */
    internal fun presence(listing: DesktopDictRead<List<String>>): DesktopDictionaryPresence = when (listing) {
        is DesktopDictRead.Miss -> when (val why = listing.why) {
            is DesktopDictWhy.Unreadable -> DesktopDictionaryPresence.Unreadable(why.error)
            DesktopDictWhy.Absent -> DesktopDictionaryPresence.NotInPackage
        }

        is DesktopDictRead.Hit -> listing.value.filter(::matches).mapNotNull { it.rowCount() }.maxOrNull()
            ?.let { DesktopDictionaryPresence.InPackage(it) } ?: DesktopDictionaryPresence.NotInPackage
    }

    private fun matches(name: String): Boolean =
        name.startsWith(ASSET_PREFIX) && name.endsWith(ASSET_SUFFIX)

    /** 名字里那段行数；`gallery_tags_79415.sqlite` → 79415。 */
    private fun String.rowCount(): Int? =
        removePrefix(ASSET_PREFIX).removeSuffix(ASSET_SUFFIX).toIntOrNull()
}

/** 包里那份词典的三种状态；措辞住在 `DesktopCapabilityReading.kt`，这里只留形状。 */
internal sealed interface DesktopDictionaryPresence {
    /** 包里有，行数取自资产名（多份时取最大的那一份，与 [DesktopTagDictionaryAsset.newest] 同一条口径）。 */
    data class InPackage(val rows: Int) : DesktopDictionaryPresence

    /** 枚举成功而清单是空的（认不出行数的也算这一格：那等于没有可用的词典）。 */
    data object NotInPackage : DesktopDictionaryPresence

    /** 连清单都数不出来（锚点缺失、jar 读不动…）—— 与"清单是空的"不许混成一句。 */
    data class Unreadable(val error: Throwable) : DesktopDictionaryPresence
}
