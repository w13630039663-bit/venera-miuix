package com.venera.compose.source

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.venera.compose.source.model.RepoIndexEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 校验版本号比对策略（支持 SemVer / Pre-release）与拷贝漫画源接入 venera-next 的元数据一致性。
 */
class ComicSourceManagerVersionTest {

    @Test
    fun testCompareVersionWithPreRelease() {
        // 关键用例：venera-next 1.4.15-debug1 必须高于官方 1.4.3，保证升级时随包覆写生效
        assertTrue(
            "1.4.15-debug1 必须大于 1.4.3",
            ComicSourceManager.compareVersion("1.4.15-debug1", "1.4.3") > 0
        )
        assertTrue(
            "1.4.3 必须小于 1.4.15-debug1",
            ComicSourceManager.compareVersion("1.4.3", "1.4.15-debug1") < 0
        )
        // 语义化版本规范：正式版大于同版本的预发布版
        assertTrue(
            "1.4.15 正式版必须大于 1.4.15-debug1",
            ComicSourceManager.compareVersion("1.4.15", "1.4.15-debug1") > 0
        )
        // 递增预发布版本
        assertTrue(
            "1.4.15-debug2 必须大于 1.4.15-debug1",
            ComicSourceManager.compareVersion("1.4.15-debug2", "1.4.15-debug1") > 0
        )
        // 下一主版本
        assertTrue(
            "1.4.16 必须大于 1.4.15-debug1",
            ComicSourceManager.compareVersion("1.4.16", "1.4.15-debug1") > 0
        )
        // 完全相等
        assertEquals(
            0,
            ComicSourceManager.compareVersion("1.4.15-debug1", "1.4.15-debug1")
        )
        assertEquals(
            0,
            ComicSourceManager.compareVersion("1.4.3", "1.4.3")
        )
    }

    @Test
    fun testBundledCopyMangaMetadataMatchesVeneraNext() {
        val assetsDir = File("src/main/assets/sources")
        val copyMangaJs = File(assetsDir, "copy_manga.js")
        assertTrue("内置 copy_manga.js 必须存在", copyMangaJs.exists())

        val content = copyMangaJs.readText()
        assertTrue("必须继承 ComicSource", content.contains("class CopyManga extends ComicSource"))
        assertTrue(
            "更新 url 必须指向 moonskycake/venera-next 仓库",
            content.contains("https://cdn.jsdelivr.net/gh/moonskycake/venera-next@main/copy_manga.js")
        )
        assertTrue(
            "内置版本必须为 1.4.15-debug1",
            content.contains("""version = "1.4.15-debug1"""")
        )

        val indexJson = File(assetsDir, "index.json")
        assertTrue("内置 index.json 必须存在", indexJson.exists())
        val entries: List<RepoIndexEntry> = Gson().fromJson(
            indexJson.readText(),
            object : TypeToken<List<RepoIndexEntry>>() {}.type
        )
        val copyMangaEntry = entries.firstOrNull { it.fileName == "copy_manga.js" }
        assertTrue("index.json 中必须包含 copy_manga.js", copyMangaEntry != null)
        assertEquals("index.json 中的版本号必须与 JS 源码一致", "1.4.15-debug1", copyMangaEntry?.version)
    }
}
