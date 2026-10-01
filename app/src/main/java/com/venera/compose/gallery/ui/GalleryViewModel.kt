package com.venera.compose.gallery.ui

import android.app.Application
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.venera.compose.gallery.data.GalleryFeedCache
import com.venera.compose.gallery.data.GalleryFeedSnapshot
import com.venera.compose.gallery.data.GalleryPost
import com.venera.compose.gallery.data.GallerySite
import com.venera.compose.gallery.data.toFavorite
import com.venera.compose.gallery.data.toPost
import com.venera.compose.gallery.domain.GalleryFeedRefreshPolicy
import com.venera.compose.gallery.domain.GalleryMerge
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.launch

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
 *
 * 2026-09-29 第四轮起**多带一份离线快照**（[GalleryFeedCache]）：冷启动第一帧就摆上次那一屏，
 * 同一天之内不再自动联网，要新内容靠下拉或那枚「刷新」。改走 `AndroidViewModel` 就是为了拿
 * Context 读那份档（与搜索层同一理由）。
 */
class GalleryViewModel(application: Application) : AndroidViewModel(application) {

    private val app: Context = application

    var posts by mutableStateOf<List<GalleryPost>>(emptyList())
        internal set

    /**
     * 两站**滤之前的原始池**（[remix] 的唯一原料）。
     *
     * 只在成功那一轮写、[refresh] 时清。它得活过"点进大图再返回"的那次组合重建 ——
     * 不然用户点第二下「换一批」会突然又变成一次网络请求，而这屏的判据是零请求。
     * 刻意**不进离线快照**：缓存只存屏上那 40 张，池子有上百条，为它写档不划算。
     */
    var pools by mutableStateOf<Map<GallerySite, List<GalleryPost>>>(emptyMap())
        internal set

    var isLoadingFeed by mutableStateOf(false)
    var feedError by mutableStateOf<String?>(null)

    /**
     * 本轮抽样与打乱用的种子。**只在这里生成**，所以组合重建后重跑 `mix` 得到的是同一个序列；
     * 换种子只有两处：[refresh]（真取一次，同时换池子）与 [remix]（同一批池子只重排）。
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

    /**
     * 屏上这一屏是**从缓存铺出来的**、本轮还没成功取过。
     *
     * 与 [feedLoadedKey] 分开记是必须的：那份凭证"只在成功后写"，从错误态点重试要靠它为空才放行；
     * 而缓存那一屏谈不上"成功"（它不是本轮取来的），写进那份凭证里就会连带把「重试」也变成假按钮。
     */
    private var hydratedFromCache by mutableStateOf(false)

    /** 缓存写于哪一天（epochDay）；null = 没有缓存。 */
    private var cachedOnEpochDay by mutableStateOf<Long?>(null)

    init {
        // 同步读，为的是让下面那次判断有结果可用（理由见 [GalleryFeedCache.read]）。
        val snapshot = GalleryFeedCache.read(app)
        if (snapshot != null) {
            // 认不出站点键的行当场摘掉（与收藏那头同一条口径）：留着它就是一格永远点不开的卡。
            posts = snapshot.posts.mapNotNull { fav -> fav.site?.let { fav.toPost(it) } }
            date = snapshot.date
            sourceNotice = snapshot.notice
            cachedOnEpochDay = snapshot.savedEpochDay
            hydratedFromCache = posts.isNotEmpty()
        }
    }

    /**
     * 页面问"这一轮要不要自己去联网取"。
     *
     * 答案是"不要"的唯一情形：屏上摆着缓存那一屏，而它就是今天存的。跨了天必须补拉 ——
     * 日榜按天换，让它永远是缓存等于让用户看不到今天的热门（判据与"补拉不清屏"的理由都在
     * [GalleryFeedRefreshPolicy]）。
     */
    fun shouldAutoLoad(todayEpochDay: Long = LocalDate.now(ZoneOffset.UTC).toEpochDay()): Boolean =
        !hydratedFromCache || GalleryFeedRefreshPolicy.shouldRefetch(cachedOnEpochDay, todayEpochDay)

    fun refresh() {
        refreshTick++
        seed = System.currentTimeMillis()
        posts = emptyList()
        pools = emptyMap()
        date = ""
        sourceNotice = null
        feedLoadedKey = null
        // 用户明确要"换一批"：缓存那一屏当场作废，下一次判断必须放行去取。
        hydratedFromCache = false
        cachedOnEpochDay = null
    }

    /** 收下本轮产出（整屏替换，不是追加 —— 日榜只有一轮）。 */
    fun accept(list: List<GalleryPost>, day: String, notice: String?, pools: Map<GallerySite, List<GalleryPost>>) {
        posts = list
        date = day
        sourceNotice = notice
        this.pools = pools
        hydratedFromCache = false
        cachedOnEpochDay = null
        // 只有拿到内容那一轮才落盘：错误态与空屏不存，否则下次冷启动铺一屏空。
        if (list.isEmpty()) return
        val snapshot = GalleryFeedSnapshot(
            savedEpochDay = LocalDate.now(ZoneOffset.UTC).toEpochDay(),
            date = day,
            notice = notice,
            posts = list.map { it.toFavorite() },
        )
        viewModelScope.launch { GalleryFeedCache.write(app, snapshot) }
    }

    /**
     * 「换一批」= **只在本地重排**（用户 2026-09-30 拍板：留原始池，换一批不重新请求）。
     *
     * 屏上那 40 张是从两站各上百条的池里抽的，所以"再抽一次"根本不需要再问站方 ——
     * 问一次反而要用户等一轮网络，而日榜那一天内压根没新内容。
     *
     * @return 是否真的重排了。**false 表示手上没有池**（冷启动那一屏是从缓存铺的，
     *   或这一轮还没取成），调用方必须退回真取一次 —— 按下去屏上纹丝不动就是假按钮，
     *   那正是这一屏历史上被真机报过多次的那一类缺陷。
     */
    fun remix(): Boolean {
        if (!GalleryMerge.canRemix(pools)) return false
        seed = System.currentTimeMillis()
        // 判据炸了（给了内容却滤光）也回 false：那一档交回调用方去真取一次，
        // 不能静默把旧的一屏原样交回去 —— 用户会以为"换一批"生效了。
        val merged = GalleryMerge.mix(pools, seed).getOrNull() ?: return false
        posts = merged.posts
        return true
    }
}
