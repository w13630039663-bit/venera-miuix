package com.venera.compose.gallery.domain

import com.venera.compose.gallery.data.GalleryPost
import com.venera.compose.gallery.data.GallerySite
import java.time.LocalDate

/**
 * 一轮搜索上下文的**可逆快照**：换到别的一轮之前把当前这一屏整片存下来，
 * 返回时逐字段抄回去，用户接着看自己刚才那面墙。
 *
 * 为什么存这些项而不是只存条件：弹栈**不发请求**（那是"回到上一轮"的全部意义 ——
 * 不重新转圈、不打断他已经看到的深度），所以结果、翻页游标、每页张数、
 * "站方给了行没给图"的张数都得一起存。少了 [pageSize]，续页与「重试续页」的
 * "到底"判据会拿 0 去比，那一屏就再也翻不动了。
 *
 * **不存 [com.venera.compose.gallery.ui.GallerySearchMode]**：domain 反向依赖 ui 是错的，
 * 而且弹栈一律回"看墙"那一态 —— 用户离开那一轮是去别处看图，回来该看到墙，不是键盘。
 */
data class GallerySearchContext(
    val site: GallerySite,
    val filters: List<GalleryTagFilter>,
    val results: List<GalleryPost>,
    val page: Int,
    val exhausted: Boolean,
    val droppedNoImage: Int,
    val pageSize: Int,
    /**
     * 当时那一屏用的**排行档**。
     *
     * 必须一起存：弹栈只认快照、不发请求，漏了它就会出现
     * 「按周排行看着 → 点一枚标签 → 返回 → 屏上还是那 100 张但档级悄悄变回默认」
     * 那种读不出来也没法解释的错位。默认值是 [GalleryRanking.NEWEST]（新条件=新一轮=默认档）。
     */
    val ranking: GalleryRanking = GalleryRanking.NEWEST,
    /**
     * 排行档看的是**哪一期**（null = 本期）。月/年档可以从日期弹层里挑一个历史期，
     * 那时窗口不再跟着"今天"走，所以这一项必须和 [ranking] 一起存 ——
     * 漏了它就会出现「弹栈后屏上还是 2024 年 3 月那批图，档级读数却写着本期」。
     */
    val periodAnchor: LocalDate? = null,
    /**
     * 离开那一轮时用户**看到第几张**。
     *
     * 为什么连这个都要存：弹栈不发请求，滚动位置也就没人恢复 —— 2026-09-29 真机那条反馈
     * 「点标签搜完返回，会自动回到最上面，不保存之前的进度」。只把结果抄回去而把人扔回顶部，
     * 等于让他从第 1 张重新找刚才看到哪儿。默认 0 = 顶部（新一轮本来就该从顶部开始）。
     */
    val scrollIndex: Int = 0,
)

/** 换一轮上下文之前，先问这一轮该怎么处置。三个出口，没有第四个。 */
enum class GalleryContextPlan {
    /** 上一轮值得留：压栈，然后装这一轮。 */
    PUSH,

    /** 与当前这一轮逐枚相等：不压栈，只**再跑一次**（幂等，不是静默）。 */
    RESEARCH_IN_PLACE,

    /** 当前条件为空 —— 屏上根本没有"上一轮"，压进去就是一格空壳。 */
    SKIP_EMPTY,
}

/**
 * 搜索上下文栈的判据层（纯函数，为的是上单测：本项目单元测试没有 Robolectric，
 * ViewModel 那层接线摸不到）。
 *
 * 三条决定都有用例锁着（`GallerySearchContextStackTest`）：
 *
 * 1. **不与上一轮 AND 合并**。从一张 `touhou` 同人图里点画师名，用户要的是"这个人的别的画"；
 *    合成 `touhou wowoguni` 只剩寥寥几张，恰好把他想要的那批排掉。所以是**另起一轮**。
 * 2. **同站同条件 = 幂等**，不压栈。同一枚标签点两次若压栈，返回会先弹回"一模一样的一轮"，
 *    白按一次返回键 —— 那种"按了没反应"正是最难被当成缺陷发现的东西。
 * 3. **超过 [MAX_DEPTH] 丢栈底**（最旧那轮）。nav 侧没有"删中间那条"的口子，深度必须有上限；
 *    丢最旧的是安全的：那是八次返回之前再也到不了的上下文，用户读不出来。
 *
 * ⚠️ [plan] 假定 `incomingFilters` **非空** —— 空条件（交接来一串空的、或历史里混进一条空的）
 * 由调用方在进这里之前退回输入态，那一档没有"这一轮"可装。
 */
object GallerySearchContextStack {

    /** 最多留几轮"返回能回去的上下文"。 */
    const val MAX_DEPTH = 8

    fun plan(
        currentSite: GallerySite,
        currentFilters: List<GalleryTagFilter>,
        incomingSite: GallerySite,
        incomingFilters: List<GalleryTagFilter>,
    ): GalleryContextPlan = when {
        currentFilters.isEmpty() -> GalleryContextPlan.SKIP_EMPTY
        // 跨站必然开新一轮：条件一串不差但换了站，结果也不同源（两站词表不通）。
        incomingSite == currentSite && incomingFilters == currentFilters -> GalleryContextPlan.RESEARCH_IN_PLACE
        else -> GalleryContextPlan.PUSH
    }

    /** 压入 [entry] 作为新的栈顶；超深时从**栈底**挤掉。 */
    fun push(stack: List<GallerySearchContext>, entry: GallerySearchContext): List<GallerySearchContext> =
        (stack + entry).takeLast(MAX_DEPTH)

    /** 弹出栈顶，交回"这一轮 + 剩下那些"；栈已经空了就返回 null（调用方据此关掉搜索）。 */
    fun pop(stack: List<GallerySearchContext>): Pair<GallerySearchContext, List<GallerySearchContext>>? =
        stack.lastOrNull()?.let { it to stack.dropLast(1) }
}

/**
 * 在途响应落地前那道闸：**认轮次，也认站点**。
 *
 * 只认站点拦不住压栈之后的新一类串台：`touhou` 正在飞第 2 页 → 交接进 `wowoguni` →
 * 返回弹回 `touhou` —— 站**没变**，旧那笔落地时会把 100 张新页 append 回错误的轮次
 * （同一类"胶囊是新的、图是旧的"脏数据，而且不会报错）。
 * 轮次计数器每开/弹一轮就 +1，所以那一笔的轮次早就不是当前的了。
 *
 * 站点这一半仍然留着：顶栏那排源切换不压栈、不转轮次（它是"换数据源"，不是"换一轮上下文"），
 * 靠它才拦得住旧站的在途响应回填到新站上下文里。
 */
fun isStaleContext(
    requestRound: Int,
    requestSite: GallerySite,
    currentRound: Int,
    currentSite: GallerySite,
): Boolean = requestRound != currentRound || requestSite != currentSite
