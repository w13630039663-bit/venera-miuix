package com.venera.desktop.assets

import com.venera.engineprobe.DesktopJsHost
import com.venera.engineprobe.EngineAssets
import com.venera.engineprobe.EngineSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * 源脚本**随包分发**的 classpath 级取证（Task 6 第 2 件）。
 *
 * 这一类不跑打包 exe（本机那个未签名 exe 连数据目录都写不出来，是环境闸门，见方案文档 §七
 * "追加更正"），它钉的是另一件事：**资源真的在 classpath 上，装载路径真的从 classpath 取**。
 * `:desktop:test` 的运行 classpath 里那份资源，与 `createDistributable` 打进 jar 的那份
 * 出自同一个 `processResources` 输出（同一张 include 清单），所以这里的绿 = 包内有货。
 *
 * 三条不许静默的形状各钉一颗：包内命中、仓库兜底、两处都没有 ⇒ **抛**
 * （空内容往下跑就是"包内没有却当成读到了"的假绿）。
 */
class DesktopAssetsClasspathTest {

    /** 一个在磁盘上**不存在**的 assets 目录：拿它当 assetDir 还能读到东西，才叫不依赖仓库 */
    private fun missingAssetDir(): File =
        File(Files.createTempDirectory("r1f-assets-").toFile(), "仓库已经不在了")

    private fun repoAssetsDir(): File? = listOf(
        File(System.getProperty("user.dir"), "app/src/main/assets"),
        File(System.getProperty("user.dir"), "../app/src/main/assets"),
    ).firstOrNull { File(it, "sources/jm.js").isFile }?.absoluteFile

    @Test
    fun 包内源脚本与装载脚本都能从classpath解析到且非空() {
        val none = missingAssetDir()
        // "jm" 是桌面默认起始源；三颗脚本都是 DesktopJsHost / EngineSession 实际要 eval 的字节
        for (relative in listOf("sources/jm.js", "venera-init.js", "venera-shim.js")) {
            val text = EngineAssets.readText(none, relative)
            assertTrue("$relative 取回来是空的", text.isNotBlank())
            assertFalse("兜底目录本就不该存在：$relative", File(none, relative).exists())
        }
        // 源脚本的形状判据（装载侧就是按这个正则找类名的，内容不对这里先红）
        assertTrue(
            EngineAssets.readText(none, "sources/jm.js").contains("extends ComicSource"),
        )
    }

    @Test
    fun 不存在的assetDir走装载入口仍能建起js宿主() {
        val none = missingAssetDir()
        val dataDir = Files.createTempDirectory("r1f-assets-data-").toFile()
        // 装载入口 = DesktopJsHost 的 init 序列（venera-shim.js → venera-init.js → post-init 补丁）
        DesktopJsHost(none, dataDir, null).use { host ->
            // venera-init.js 定义的 ComicSource 在不在，就是"init 脚本真从包里装起来了"的读数
            assertEquals("function", host.evaluate("typeof ComicSource"))
            // EngineSession 那颗源脚本的取用口同样不许碰仓库目录：不存在的 key 必须**抛**，
            // 且消息里点名两处落点（不是"源返回 0 条"那种看起来像成功的形状）
            val session = EngineSession(host, none)
            val failure = runCatching { session.load("这一颗根本不存在") }.exceptionOrNull()
            assertNotNull("装载入口在包内与仓库都没有时不该静默往下走", failure)
            val message = failure!!.message.orEmpty()
            assertTrue("消息该点名 classpath 落点：$message", message.contains("/sources/这一颗根本不存在.js"))
            assertTrue("消息该点名仓库落点：$message", message.contains(none.path))
        }
    }

    @Test
    fun 包内的空条目不会被当成读到了内容() {
        // 测试资源里躺着一颗 0 字节的 /empty-asset-probe.txt：它模拟"打进包但那是空的"
        val url = javaClass.getResource(EMPTY_PROBE)
        assumeTrue("测试资源没进 classpath 或不是目录形态（processTestResources 没跑？）", url?.protocol == "file")
        assertEquals(0L, File(url!!.file).length())
        val outcome = runCatching { EngineAssets.readText(missingAssetDir(), EMPTY_PROBE.removePrefix("/")) }
        val failure = outcome.exceptionOrNull()
        assertNotNull("空条目必须当成取不到（返回空内容继续跑就是假绿）：${outcome.getOrNull()}", failure)
        assertTrue(failure!!.message.orEmpty().contains(EMPTY_PROBE))
    }

    @Test
    fun 只有桌面链路用得上的资源进了包其余没被整个目录拖进来() {
        // R39 口径：随包只带 sources/*.js + venera-init.js + venera-shim.js
        for (hit in listOf("/sources/jm.js", "/sources/goda.js", "/venera-init.js", "/venera-shim.js")) {
            assertNotNull("包内应有 $hit", javaClass.getResource(hit))
        }
        // 这些是 app/src/main/assets 里的资源，桌面链路一行都不读 ⇒ 不许进包
        for (miss in listOf(
            "/opencc.txt",              // 唯一消费点在 data/tags/ChineseVariantConverter（不在桌面编译面）
            "/tags.json",
            "/tags_tw.json",
            "/source_content_warning.json",
            "/gallery_tags_79415.sqlite",
            "/licenses",
            "/sources/index.json",      // 源清单是枚举出来的，不是读这份索引
        )) {
            assertFalse("包内不该有 $miss", javaClass.getResource(miss) != null)
        }
        // 清单枚举走的是包内（assetDir 不存在 ⇒ 任何"有货"都只可能来自 classpath）
        val keys = EngineAssets.listSourceKeys(missingAssetDir())
        assertTrue("包内源清单不该为空：${keys.size}", keys.isNotEmpty())
        assertTrue("默认起始源 jm 必须在清单里", keys.contains("jm"))
        assertFalse("index.json 不是源脚本，不许混进清单", keys.contains("index"))
        assertEquals("清单该是排序的", keys.sorted(), keys)
    }

    @Test
    fun 包内没有的资源仍按老路从仓库兜底() {
        val repo = repoAssetsDir()
        assumeTrue("仓库目录不在（这条只在源码仓里可跑）", repo != null)
        // index.json 判过不进包 ⇒ 这一颗只能走仓库兜底，读数是 `D_资源 来源=仓库 …`
        val text = EngineAssets.readText(repo!!, "sources/index.json")
        assertTrue("兜底读到的是空内容：$text", text.isNotBlank())
        // 兜底不该把包内已有的东西顶掉：两边都在时命中包内（同内容，顺序语义不变）
        assertTrue(EngineAssets.readText(repo, "sources/jm.js").contains("extends ComicSource"))
    }

    private companion object {
        const val EMPTY_PROBE = "/empty-asset-probe.txt"
    }
}
