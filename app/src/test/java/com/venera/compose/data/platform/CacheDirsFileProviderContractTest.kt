package com.venera.compose.data.platform

import com.venera.compose.testsupport.RepoSources
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * `cacheDir` 目录名与 `FileProvider` 白名单的**成对性**。
 *
 * 这条洞的形状是：代码写文件永远成功，`getUriForFile()` 才抛，
 * 而抛出的异常历史上被 `catch (_: Exception) {}` 吞掉 —— 表现是"导出成功"的
 * Toast 弹了、分享面板从没出现（`file_paths.xml` 顶部记的 G-2，当时三条里缺两条）。
 * XML 引用不到 Kotlin 常量，编译期管不了，所以只能在测试里把两边读出来逐字对账。
 */
class CacheDirsFileProviderContractTest {

    private val xml: File by lazy {
        File(RepoSources.root, "app/src/main/res/xml/file_paths.xml").also {
            check(it.isFile) { "找不到 FileProvider 配置：${it.absolutePath}" }
        }
    }

    /** XML 里配过的 cache-path 目录名集合。 */
    private fun configuredDirs(): Set<String> =
        xml.readLines().mapNotNull { line ->
            Regex("""<cache-path\s+name="([^"]*)"\s+path="([^"]*)"""").find(line)?.let { m ->
                assertEquals(
                    "file_paths.xml 的 name 与 path 应当只差结尾的 /（name=\"${m.groupValues[1]}\" path=\"${m.groupValues[2]}\"）",
                    m.groupValues[2].trimEnd('/'),
                    m.groupValues[1],
                )
                m.groupValues[1]
            }
        }.toSet()

    @Test
    fun `三个分享落点都在 FileProvider 白名单里`() {
        val configured = configuredDirs()
        assertTrue("shared_images 没配 → 阅读器与画廊分享当场失败", CacheDirs.SHARED_IMAGES in configured)
        assertTrue("exports 没配 → CBZ 导出分享静默失败（G-2 的一条）", CacheDirs.EXPORTS in configured)
        assertTrue("backups 没配 → 备份导出分享静默失败（G-2 的另一条）", CacheDirs.BACKUPS in configured)
    }

    @Test
    fun `目录名的值逐字不变`() {
        // 改名 = 老目录里的临时档没人清、而 XML 那一行也一起要改；这里只保证不手滑。
        assertEquals("shared_images", CacheDirs.SHARED_IMAGES)
        assertEquals("exports", CacheDirs.EXPORTS)
        assertEquals("backups", CacheDirs.BACKUPS)
    }

    @Test
    fun `代码侧不许再手写这三个目录名`() {
        val names = setOf(CacheDirs.SHARED_IMAGES, CacheDirs.EXPORTS, CacheDirs.BACKUPS)
        val self = "com/venera/compose/data/platform/CacheDirs.kt"
        val hits = RepoSources.allMainLines().filter { (rel, _, line) ->
            rel != self && names.any { line.contains("\"$it\"") }
        }
        // file_paths.xml 那一侧的字符串扫不到（它不是 .kt），正是本用例第一那条断言在管。
        assertTrue(
            "cacheDir 目录名回潮成内联字面量：\n${hits.joinToString("\n") { "${it.first}:${it.second} ${it.third.trim()}" }}",
            hits.isEmpty(),
        )
    }
}
