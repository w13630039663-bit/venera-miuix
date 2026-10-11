package com.venera.desktop.gallery.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import com.venera.desktop.gallery.data.DesktopReaderMode
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import io.github.composefluent.component.Text
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 批 81 · 阅读那一屏的**离屏语义树**判据。
 *
 * 与详情那颗同形（批 80 立的这一层）：渲的是 [DesktopComicReaderContent] 而不是 pane ——
 * pane 要真 `EngineSession`（GraalJS + 真源 + 真网络），离屏渲不出来。
 * 页那一格由 [pageImage] 插槽喂一枚占位件：真实的槽要 Coil 与 `PlatformContext`，
 * 而"每一页有没有各摆一格"这一层与取字节无关（那一层由起窗图证与 fetcher 那三判分别钉）。
 */
@OptIn(ExperimentalTestApi::class)
class DesktopComicReaderSemanticsTest {

    private val reading =
        DesktopComicReading("1461316", "某本漫画", "g", "第 1 话", placeholder = false, episodeIndex = 0, startPage = 0, coverUrl = "")

    @Composable
    private fun Screen(
        state: ComicReaderState,
        onBack: () -> Unit = {},
        pages: MutableList<String>? = null,
        shown: DesktopComicReading = reading,
        listState: LazyListState = rememberLazyListState(),
        mode: DesktopReaderMode = DesktopReaderMode.VERTICAL_CONTINUOUS,
    ) {
        DesktopComicReaderContent(
            reading = shown,
            state = state,
            sourceKey = "jm",
            listState = listState,
            // 批 87 起展示件有两面墙：这一组离屏用例默认站在纵向那一档，
            // 需要横向那一档的用例自己递 `mode`，pager 那面墙由 `rememberPagerState` 现造。
            pagerState = rememberPagerState { (state as? ComicReaderState.Ready)?.pages?.size ?: 0 },
            mode = mode,
            seedNote = null,
            onModeChange = {},
            onStep = {},
            onBack = onBack,
            pageImage = { index, imageKey ->
                pages?.add(imageKey)
                Box(Modifier.fillMaxSize()) { Text("页位$index") }
            },
        )
    }

    @Test
    fun 有页时每一页各摆一格而页数的读数上屏() = runComposeUiTest {
        val keys = mutableListOf<String>()
        setContent { Screen(ComicReaderState.Ready(listOf("a.jpg", "b.jpg", "c.jpg")), pages = keys) }
        onNodeWithText("共 3 页").assertExists("页数那一句该上屏（读数与屏上念同一件事）")
        onNodeWithText("第 1 / 3 页").assertExists("当前页号也得单独念一格（只算不摆就是屏上什么都没有）")
        listOf("页位0", "页位1", "页位2").forEach {
            onNodeWithText(it).assertExists("三页该各摆一格，缺的就是$it")
        }
        assertEquals("插槽收到的页键必须就是那三枚，按列表次序", listOf("a.jpg", "b.jpg", "c.jpg"), keys)
    }

    @Test
    fun 等回话那一档不摆页也不摆失败句() = runComposeUiTest {
        setContent { Screen(ComicReaderState.Loading) }
        onNodeWithText("正在问源站这一话的页表…").assertExists()
        onNodeWithText("共 0 页").assertDoesNotExist() // 还没回话就念页数是替源站撒谎
        onNodeWithText("没问到页表：", substring = true).assertDoesNotExist()
    }

    @Test
    fun 失败那一句把原话念到屏上而不是只进日志() = runComposeUiTest {
        setContent { Screen(ComicReaderState.Failed("IllegalStateException: 取页失败 http=403")) }
        onNodeWithText("没问到页表：IllegalStateException: 取页失败 http=403")
            .assertExists("失败原话必须上屏（只进 stdout 等于屏上什么都没有）")
    }

    @Test
    fun 回了零页时屏上也念那一句() = runComposeUiTest {
        setContent { Screen(ComicReaderState.Ready(emptyList())) }
        onNodeWithText("共 0 页").assertExists()
        onNodeWithText("源站这一话回了 0 页。").assertExists("摆一片空不是一句实话，念出来才是")
    }

    @Test
    fun 题名与话名都在屏上() = runComposeUiTest {
        setContent { Screen(ComicReaderState.Loading) }
        onNodeWithText("某本漫画", substring = true).assertExists()
        onNodeWithText("第 1 话", substring = true).assertExists()
    }

    @Test
    fun 占位章那一档念的是详情同一句() = runComposeUiTest {
        setContent {
            Screen(
                ComicReaderState.Loading,
                shown = DesktopComicReading("9", "单本", "", "", placeholder = true, episodeIndex = 0, startPage = 0, coverUrl = ""),
            )
        }
        // 那一行与题名同一形（前面带 " · "），所以按子串找 —— 精确匹配会把分隔符算进断言里。
        onNodeWithText("整部（这一本没有分话）", substring = true).assertExists()
    }

    @Test
    fun 返回那一枚按一次落到回调() = runComposeUiTest {
        var backed = 0
        setContent { Screen(ComicReaderState.Ready(listOf("a.jpg")), onBack = { backed += 1 }) }
        assertEquals("没按的时候不许先落一笔", 0, backed)
        onNodeWithText("← 返回详情").performClick()
        assertEquals("「← 返回详情」按一次该退一笔", 1, backed)
    }
}
