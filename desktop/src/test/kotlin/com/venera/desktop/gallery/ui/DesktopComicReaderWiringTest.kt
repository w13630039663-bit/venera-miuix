package com.venera.desktop.gallery.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 批 81 · 阅读那一屏的**接线层**判据。
 *
 * 屏上那一层由 `DesktopComicReaderSemanticsTest` 钉、算式与锁由 `DesktopComicPageFetcherTest` 钉，
 * 这一颗只管那两层够不着的事：三跳一处不缺、挂载次序、装载在 IO 线程上问、展示层不吃引擎句柄、
 * 阅读屏不许自己造引擎。同一件事不重测两把尺子（本仓的老毛病）。
 */
class DesktopComicReaderWiringTest {

    private val readerFile = DesktopSourceTree.desktopUiSource("DesktopComicReader.kt")
    private val paneFile = DesktopSourceTree.desktopSourceOf("gallery/ui/DesktopComicPane.kt")
    private val detailFile = DesktopSourceTree.desktopSourceOf("gallery/ui/DesktopComicDetail.kt")
    private val appFile = DesktopSourceTree.desktopUiSource("VeneraDesktopApp.kt")
    private val mainFile = DesktopSourceTree.desktopSourceOf("VeneraDesktop.kt")
    private val pipelineFile = DesktopSourceTree.desktopSourceOf("gallery/data/DesktopImagePipeline.kt")
    private val fetcherFile = DesktopSourceTree.desktopSourceOf("gallery/data/DesktopComicPageFetcher.kt")

    /** ① 页表那一问在 IO 线程上，而且**先转线程再问**（顺序钉，不是两枚存在性判式）。 */
    /** ② 展示层不吃引擎句柄：它一吃，语义树那一层就整体退回只有像素。 */
    /** ③ 阅读屏不许自己造引擎 —— 引擎句柄只有一颗，住在探索那一屏（批 80 ⑦ 钉的是退场，这一条钉的是新建）。 */
    /** ④ 取字节那一跳被锁包着，而锁与那一问的**次序**要紧（两枚存在性判式盖不住"锁在外头"）。 */
    @Test
    fun 取页那一问被引擎锁包着() {
        val fetcher = DesktopSourceTree.codeText(fetcherFile)
        val lock = fetcher.indexOf("withDesktopEngineLock(page.lockKey)")
        val ask = fetcher.indexOf("engine.pageImage(")
        assertTrue("找不到锁那一笔", lock >= 0)
        assertTrue("取字节那一问排在锁**之外**（并发进去就是批 80 那种 Context cancelled）", lock < ask)
        assertEquals("取页那一问只许出现在锁里那一处", 1, DesktopSourceTree.codeOccurrences(fetcherFile, "engine.pageImage("))
        // 工厂必须吃那颗谓词：整串表达式（只数前缀的话"恒返回一颗 fetcher"也绿 —— 坑 ㉔）
        assertTrue(
            "工厂没走那颗谓词（引擎缺席时硬造一页，红得像网络坏了）",
            fetcher.contains("if (!desktopComicPageCanFetch(data)) return null"),
        )
    }

    /** ⑤ 内存缓存那两枚成对挂：只挂 fetcher 不挂 Keyer 时 Coil 交回 null 键，屏上看不出来而缓存是假的。 */
    @Test
    fun 取图那台成对挂着键与取数件() {
        val pipeline = DesktopSourceTree.codeText(pipelineFile)
        val block = Regex("""components \{([\s\S]*?)\n\s*\}\s*\n\s*\.build""").find(pipeline)
        assertTrue("找不到 components 那一段（换了写法就要跟着换截法）", block != null)
        val body = block!!.groupValues[1]
        assertTrue("没挂页的取数件", body.contains("add(DesktopComicPageFetcher.Factory())"))
        assertTrue("没挂页的缓存键：那一格的语义是「不进内存缓存」，而屏上完全看不出来", body.contains("add(DesktopComicPageKeyer)"))
        // 对照组：网络那一枚仍在（撤掉它等于画廊的图全断了）
        assertTrue("对照组失效：网络取数件不在这段里了", body.contains("OkHttpNetworkFetcherFactory("))
    }

    /** ⑥ 三跳一处不缺：构建脚本 → 集成方 → 应用根 → 域 body → 探索那一屏。 */
    /**
     * ⑦ 阅读比详情高一层，且装载那一件在**分岔之前**（退回时底下那一屏已经在，不重跑 `loadInfo`）。
     *
     * ⚠️ 这一条从前钉的是源码里 `readTarget?.let` / `comicTarget?.let` 两块的**先后**。
     * 那个先后是"当时那一版实现的形状"，而形状本身是缺陷（两屏只挂在探索那一支里 ⇒ 从「阅读历史」
     * 按一行进不去详情）；批 82.7 把渲染点提到域层后，优先级归了纯判断 `desktopComicScreen`
     * —— 那一格的判据在 `DesktopComicRowDispatchTest`（同一件事只许一把尺子钉措辞，这里不重钉）。
     * 这一颗改成钉它自己那一份**接线顺序**：装载 effect 必须排在分岔**前面**。
     */
    /** ⑧ 落点那一行念得出阅读开没开、带"是谁改的"那枚戳，钥匙里有那两枚状态。 */
    /** ⑨ 章节行点得动，而且那句"整部（这一本没有分话）"只住在一颗件里。 */
    /** ⑩ 观感那三枚口径是从对岸抄来的，不许悄悄换成桌面自定的值。 */
}
