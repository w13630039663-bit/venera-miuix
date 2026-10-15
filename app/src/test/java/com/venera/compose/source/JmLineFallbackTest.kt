package com.venera.compose.source

import com.venera.compose.testsupport.RepoSources
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 禁漫「线路回退」与「随包源更新落地」这两轮的尺子（2026-10-15）。
 *
 * ## 为什么要钉在源码层面
 *
 * 两件都不是编译器能管的事，也不是运行时一眼能看见的事：
 *
 * ① **线路回退**：站方几条线路不是同一份库。实测同一个号 `1482354`，线路 1 回
 * `{"code":200,"data":[]}`（`data` 是空数组 ⇒ `jm.js` 抛 `Invalid Data`），线路 2 给了完整数据；
 * 另一个时刻线路回的是明文 `Could not find the album!`（`JSON.parse` 直接炸）。两种都在
 * `loadInfo` 里抛 ⇒ 详情页靠卡片带着封面标题照常渲染，只有章节那一列整条消失 ——
 * 正是用户报的「封面和详情都能搜到，但章节加载不出来」。回退**只许**吃这两种形态：
 * 登录过期、限流、非 200 一律照原样抛，换线路重试等于把真问题藏起来还多打站方。
 *
 * ② **随包源的更新落地**：`bootstrapBundledSources` 只在「本机没有这颗文件」时才写副本，
 * 而运行期读的是那份副本（`loadInstalledJsSources` 里 `parser.parseFile(File(sourceDir, …))`）
 * ⇒ 改了随包脚本对**已装过的设备永远不生效**。这条比没修更难查：代码看起来已经修过了。
 */
class JmLineFallbackTest {

    private fun scriptFile(): File =
        File(RepoSources.root, "app/src/main/assets/sources/jm.js").also {
            check(it.isFile) { "找不到禁漫源脚本：${it.absolutePath}" }
        }

    private fun codeLines(packagePath: String, name: String): List<IndexedValue<String>> {
        val file = RepoSources.kotlinFiles(packagePath).firstOrNull { it.name == name }
            ?: error("找不到 $name（尝试过的根：${RepoSources.root.absolutePath}）")
        return file.readLines().withIndex().filter { (_, line) ->
            val t = line.trim()
            !(t.startsWith("//") || t.startsWith("*") || t.startsWith("/*"))
        }
    }

    /** ① 详情与章节这两颗走回退，而且**只有这两颗**走。 */
    @Test
    fun `详情与章节换线路重试 别的接口不重试`() {
        val lines = scriptFile().readLines().withIndex()
            .filter { !it.value.trim().startsWith("//") && !it.value.trim().startsWith("*") }
        val fallbacks = lines.filter { it.value.contains("this.getAcrossLines(") }
        assertEquals(
            "回退只该吃 /album 与 /chapter 这两颗按号取数接口：" +
                fallbacks.map { it.index + 1 to it.value.trim() },
            2,
            fallbacks.size,
        )
        assertTrue("详情那颗没接回退", fallbacks.any { it.value.contains("/album?id=") })
        assertTrue("章节那颗没接回退", fallbacks.any { it.value.contains("/chapter?id=") })
        // 搜索、排行、收藏仍走单线路：给它们加重试只会放大对站方的请求量。
        assertEquals(
            "有按号以外的接口被顺手套上回退了",
            0,
            lines.count {
                it.value.contains("this.getAcrossLines(") &&
                    !(it.value.contains("/album?id=") || it.value.contains("/chapter?id="))
            },
        )
    }

    /** ② 回退的判据钉整串表达式：只认那两种形态。 */
    @Test
    fun `只有线路答不上来才换线路`() {
        val text = scriptFile().readText()
        assertTrue(
            "回退条件被改窄或改宽了（原样：只认 Invalid Data 与 not valid JSON）",
            text.contains("if (msg !== 'Invalid Data' && msg.indexOf('is not valid JSON') < 0)"),
        )
        assertTrue("换线路前必须原样抛出其它错误", scriptFile().readLines().any { it.trim() == "throw e" })
        // 线路清单一条也不许漏：回退必须覆盖 apiDomains 全表，而不是只试两条。
        assertTrue(
            "循环没有吃整张线路表",
            text.contains("for (let offset = 0; offset < JM.apiDomains.length; offset++)"),
        )
        assertTrue("取模回绕丢了（会越界成 undefined 主机）", text.contains("(start + offset) % JM.apiDomains.length"))
    }

    /** ③ 随包源的更新真被调用，且三道边界都在。 */
    @Test
    fun `随包源更新会在已装过的设备上落地`() {
        val lines = codeLines("com/venera/compose/source", "ComicSourceManager.kt")
        val call = lines.filter { it.value.contains("refreshBundledSourceUpdates(valid)") }
        assertEquals("刷新被接上了启动路径", 1, call.size)
        val def = lines.filter { it.value.contains("private fun refreshBundledSourceUpdates(") }
        assertEquals(1, def.size)
        // 边界①：只碰内置清单里的那几颗。
        assertTrue(
            "范围闸丢了（会覆写用户自己导入的源）",
            lines.any { it.value.contains("if (meta.fileName !in BUNDLED_DEFAULT_FILES) continue") },
        )
        // 边界②：只有随包版本严格更新才写。
        assertTrue(
            "版本闸丢了（内容不同就覆写 ⇒ 用户本机的调试改动会被抹掉）",
            lines.any { it.value.contains("if (compareVersion(bundledVersion, installedVersion) <= 0) continue") },
        )
        // 边界③：版本读不出来不动。
        assertEquals(
            "两处版本解析都必须兜住解析失败",
            2,
            lines.count { it.value.contains("runCatching { parser.parse(") && it.value.contains("}.getOrNull() ?: continue") },
        )
        // 刷新必须排在解析注册之前，否则这一轮注册的还是旧副本。
        val parse = lines.first { it.value.contains("parser.parseFile(File(sourceDir, m.fileName))") }.index
        assertTrue("刷新排在注册之后（本轮装的还是旧副本）", call.single().index < parse)
    }
}
