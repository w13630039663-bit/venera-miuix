package com.venera.compose.gallery.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.venera.compose.gallery.data.GalleryPost

/**
 * 画廊日榜流的**状态持有者**。
 *
 * 为什么要单独一个 ViewModel（而不是就地 remember）：本项目的导航条目在被下一页覆盖时
 * **组合会销毁**，返回时从零重建 —— 裸 `remember` 的列表会一起没了，观感就是
 * 「点进大图再返回，整屏重新拉一遍并跳回顶部」（这条根因已在探索页/搜索页上真机撞过多次，
 * 见记忆「导航条目会重建组合」）。取数逻辑仍留在页面里，本类只放要跨往返活下来的那份状态。
 *
 * 2026-09-25 起这一屏是**两站上一天热门各 20 张打乱**，一屏到底：
 * 所以这里没有 `hasMore` / `round` / 翻页 `exclude` 那一套了 —— 日榜没有下一页，
 * 留着"还有更多"的状态机只会产出「到底了还显示加载更多」那类假象（搜索页同一个病）。
 */
class GalleryViewModel : ViewModel() {

    var posts by mutableStateOf<List<GalleryPost>>(emptyList())
        internal set

    var isLoadingFeed by mutableStateOf(false)
    var feedError by mutableStateOf<String?>(null)

    /**
     * 本轮打乱用的种子。**只在这里生成**，所以组合重建后重跑 `mix` 得到的是同一个序列；
     * 只有 [refresh] 会换种子 —— 那就是用户唯一一次"要换一批"的表达。
     */
    var seed by mutableLongStateOf(System.currentTimeMillis())
        internal set

    /** 实际请求的那一天（`yyyy-MM-dd`），给页面把"看的是哪天的热门"说出来。 */
    var date by mutableStateOf("")
        internal set

    /**
     * 「这一轮只有一站给了内容」之类的提示文案。
     *
     * 双源混合静默退化成单源，是这一屏最容易被"看起来正常"糊过去的一种错 ——
     * 所以要占页面一条可见的位置，而不是只写日志。
     */
    var sourceNotice by mutableStateOf<String?>(null)

    /**
     * 手动刷新计数：参与 [feedLoadedKey] 的比对口径。
     *
     * 没有它，「刷新」按钮点下去会被 loadedKey 守卫当成"已经拉过"而什么都不做 ——
     * 那是一个假按钮。
     */
    var refreshTick by mutableIntStateOf(0)

    /**
     * 「这一轮已经成功加载过」的凭证，**只在成功后写**。
     *
     * 失败不写，所以从错误态点重试仍会真发请求；条目组合重建时它会命中，于是返回不重拉。
     */
    var feedLoadedKey by mutableStateOf<String?>(null)

    fun refresh() {
        refreshTick++
        seed = System.currentTimeMillis()
        posts = emptyList()
        date = ""
        sourceNotice = null
        feedLoadedKey = null
    }

    /** 收下本轮产出（整屏替换，不是追加 —— 日榜只有一轮）。 */
    fun accept(list: List<GalleryPost>, day: String, notice: String?) {
        posts = list
        date = day
        sourceNotice = notice
    }
}
