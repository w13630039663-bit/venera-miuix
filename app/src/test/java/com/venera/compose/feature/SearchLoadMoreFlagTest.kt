package com.venera.compose.feature

import com.venera.compose.testsupport.RepoSources
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 「加载更多」那枚波浪环的开关归属。
 *
 * 病（2026-10-15 用户报「一直在加载更多，但就是一直在加载」）：`loadMore()` 升起
 * `loadingMore` 之后，翻页那一笔协程被任何一轮新检索 `searchJob?.cancel()` 掐掉时走的是
 * `catch (e: CancellationException) { throw e }`，它不写状态；而「把旗放下」这件事本来
 * 只能落在发起新一轮的那几个重置块里 —— 那些块一个都没有写 `loadingMore`。
 * 于是**转圈时改一个字**（`onQueryChange` 就在那一批里）就会让旗永远升着：
 * 环转不停、「到底了」不再出现、`loadMore()` 的门也一直进不去，且没有任何报错。
 *
 * 钉两条不变量：
 *  - 整个 ViewModel 只有翻页那一处能升旗；
 *  - 任何碰 `isSearching` 的更新块都必须同时落旗 —— 新一轮开始、上一轮结束、出错、
 *    切源、清空，全都算「上一笔翻页作废」。
 */
class SearchLoadMoreFlagTest {

    private val src = RepoSources.kotlinFiles("com/venera/compose/feature")
        .first { it.name == "SearchViewModel.kt" }
        .readLines()

    /**
     * 剥掉注释后再数花括号。
     *
     * `//` 只有在**行首**或前面引号数为偶数时才是注释 —— 源码里的错误文案可能带
     * `https://` 这种串，无脑 `substringBefore("//")` 会把半行代码吞掉，
     * 于是括号数就对不上、块边界找错、判据变成假绿。
     */
    private fun codePart(line: String): String {
        val trimmed = line.trim()
        if (trimmed.startsWith("//") || trimmed.startsWith("*")) return ""
        val idx = trimmed.indexOf("//")
        if (idx < 0) return line
        val quotesBefore = trimmed.substring(0, idx).count { it == '"' }
        return if (quotesBefore % 2 == 0) line.substringBefore(trimmed.substring(idx)) else line
    }

    /** 从 1-based 命中行往外扩出它所在的那条 `_uiState.update { … }` 语句文本。 */
    private fun updateBlock(lineNo: Int): String {
        var start = lineNo - 1
        while (start >= 0 && !src[start].contains("_uiState.update {")) start--
        assertTrue(
            "第 $lineNo 行改了 isSearching，却找不到它所属的 _uiState.update 块 —— 量具失效，不能算通过",
            start >= 0,
        )
        val sb = StringBuilder()
        var depth = 0
        var i = start
        while (i < src.size) {
            val code = codePart(src[i])
            sb.append(code).append('\n')
            depth += code.count { it == '{' } - code.count { it == '}' }
            if (depth <= 0) break
            i++
        }
        assertTrue("第 $lineNo 行那条 update 语句花括号不闭合（扫到文件尾）", depth <= 0)
        return sb.toString()
    }

    @Test fun `只有翻页那一处能升起 loadingMore 旗`() {
        val rises = src.indices.filter { src[it].contains("loadingMore = true") }
        assertEquals(
            "升旗的地方应当只有一处（loadMore 的起点）：\n" +
                rises.joinToString { "第 ${it + 1} 行" },
            1,
            rises.size,
        )
        val open = src.indexOfFirst { it.contains("fun loadMore()") }
        val close = src.indexOfFirst { it.contains("fun clearHistory()") }
        assertTrue("量具的 loadMore() / clearHistory() 锚点不见了", open >= 0 && close > open)
        assertTrue("唯一的升旗必须在 loadMore() 体内，第 ${rises[0] + 1} 行不在",
            rises[0] in open..close)
    }

    @Test fun `loadMore 的门确实读这枚旗`() {
        // 落旗之所以要紧，是因为门里有它：旗不落，门永远进不去，用户看到的就是"点了没反应"。
        val open = src.indexOfFirst { it.contains("fun loadMore()") }
        assertTrue("量具的 loadMore() 锚点不见了", open >= 0)
        val gate = src.drop(open).take(6).firstOrNull { it.contains("snapshot.loadingMore") }
        assertTrue("loadMore() 开头六行里没有读 snapshot.loadingMore 的门", gate != null)
    }

    @Test fun `碰 isSearching 的更新块必须同时落翻页旗`() {
        val sites = src.indices.filter { src[it].contains("isSearching = ") }
        assertTrue("一条 isSearching 的写入都没找到，量具失效", sites.isNotEmpty())
        val offenders = sites.filter { !updateBlock(it + 1).contains("loadingMore = false") }
        assertTrue(
            "这些块只改了 isSearching、没把 loadingMore 放下 —— 转圈时动一下就永久卡死：\n" +
                offenders.joinToString("\n") { "  第 ${it + 1} 行：" + src[it].trim().take(72) },
            offenders.isEmpty(),
        )
    }
}
