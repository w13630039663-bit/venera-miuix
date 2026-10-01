package com.venera.compose.gallery.domain

import com.venera.compose.gallery.data.GallerySite

/**
 * 一次搜索**打哪些站** —— 与 [GallerySite] 是两回事。
 *
 * 为什么要另立一枚类型而不是给 `GallerySite` 加一个"全部"值：那个枚举有约一百处引用，
 * 一大半是**身份语义**（条目 uid、路由 `routeKey`、收藏档、缓存键、大图页队列、熔断遍历）。
 * 把"两站的并"塞进去，那些键会一起被污染 —— 而污染的方式是静默的：
 * 收藏会存成 `全部:12345`，大图页队列会拿到一个没有地址前缀的站。
 * 所以来源只活在**发请求那一处**，一进来就展开成腿。
 *
 * 腿的顺序恒按 `GallerySite.entries` 定死（构造时归一）：不钉住就会跟着 `HashMap` 的无序飘，
 * 与 `GalleryMerge` 那条"站序决定谁留下"是同一类隐患。
 *
 * @param sites 至少一条腿；构造时按站表序去重归一。
 */
class GallerySearchSource(sites: Collection<GallerySite>) {

    val sites: List<GallerySite> = GallerySite.entries.filter { it in sites }

    init {
        // 空腿来源会让"到底"判据恒真 —— 页尾从此不再出货，比报错更难查。
        require(this.sites.isNotEmpty()) { "一个来源至少要有一条腿" }
    }

    /** 是不是"全部"那一档（两腿及以上）。 */
    val isMulti: Boolean get() = sites.size > 1

    /** 分段器与读数上那一格的字。单站用**站方自己的写法**，不另造词。 */
    val label: String get() = if (isMulti) ALL_LABEL else sites.single().displayName

    /**
     * 这一档里**真的能发车**的腿。
     *
     * Gelbooru 的 DAPI 匿名一律 401（不配账号那一站一张图都取不到），所以没账号时它根本不该被算进
     * "发出的腿" —— 算进去就会永远到底不了，页尾挂着一个不会来的"加载更多"。
     */
    fun availableLegs(hasGelbooruAccount: Boolean): List<GallerySite> =
        sites.filter { it != GallerySite.GELBOORU || hasGelbooruAccount }

    /**
     * 发不出去的那些腿，连**能直接上屏的原因**一起交回去。
     *
     * 这一层不许把缺席吞掉：缺席被吞掉的样子就是"这一站今天没图"，而真正的原因是他没配账号。
     * 串本身与 `GallerySearchViewModel.NEEDS_GELBOORU_ACCOUNT` 同源（那边引用这里的常量），
     * 两处各写一份就会长成"空态标题与页尾读数对不上"。
     */
    fun missingLegs(hasGelbooruAccount: Boolean): Map<GallerySite, String> =
        sites.filter { it == GallerySite.GELBOORU && !hasGelbooruAccount }
            .associateWith { NEEDS_ACCOUNT_REASON }

    override fun equals(other: Any?): Boolean =
        other is GallerySearchSource && other.sites == sites

    override fun hashCode(): Int = sites.hashCode()

    override fun toString(): String = sites.joinToString("+") { it.routeKey }

    companion object {
        const val ALL_LABEL = "全部"

        /**
         * Gelbooru 没配账号时给用户看的那句话。**这一份是唯一来源**，
         * ViewModel 那边只是别名 —— 两处各写一份就会长成"空态标题与页尾读数对不上"。
         *
         * 措辞的三个要点，都不是随便写的：
         * - **说清"完全用不了"而不是"效果差一点"**：它的接口对匿名请求一律拒绝，
         *   不是少几档权限。写成"登录后体验更好"会让用户以为不配也能凑合用；
         * - **指出在哪儿配**（设置 → 画廊 那张账号卡）—— 只说"需要账号"等于把问题丢回给用户去找入口；
         * - **不说 yande.re 也受影响**：这一站挂了不影响另一站，说清边界，
         *   否则用户会以为整个画廊都瘫了。
         */
        const val NEEDS_ACCOUNT_REASON: String =
            "Gelbooru 需要先配置账号才能用：它的接口对匿名请求一律拒绝（401）。" +
                "到「设置 → 画廊」里的「画廊站点账号」卡中填 User ID 与 API Key 即可；" +
                "yande.re 不受影响，照常能搜。"

        /** 两站都打。顺序恒为站表序。 */
        val ALL = GallerySearchSource(GallerySite.entries.toList())

        fun single(site: GallerySite): GallerySearchSource = GallerySearchSource(listOf(site))

        /**
         * 分段器上那三档，顺序就是它们画出来的顺序：**默认档在首**（用户 2026-09-30 拍板
         * 「『全部』作为默认选项」），两站跟在后面按站表序。
         *
         * 列表定在 domain 而不是 UI：`selectedIndex` 靠 `indexOf` 反查，UI 里各写一份
         * 就会出现"分段器点亮第一格、状态层其实在单站档"那种错位。
         */
        val options: List<GallerySearchSource> =
            listOf(ALL) + GallerySite.entries.map { single(it) }
    }
}

/**
 * 这一笔响应落地前那道闸：**认轮次，也认腿**。
 *
 * [isStaleContext] 的按腿版本 —— 两腿并发时旧那笔落地只该丢自己那一条，
 * 丢错了就会把已经到货的另一腿一起抹掉（现象是"搜出来又自己空了"）。
 */
fun isStaleLeg(
    requestRound: Int,
    requestSite: GallerySite,
    currentRound: Int,
    activeSites: List<GallerySite>,
): Boolean = requestRound != currentRound || requestSite !in activeSites

/**
 * 续页时**每条腿各自要第几页**。
 *
 * 两条不显然的判据：
 * - 已经到底的腿不再要页（再要就是"到底了还显示加载更多"那类假读数）；
 * - 这一轮缺席的腿（超时、熔断、没账号）没有页码记录，续页要**从第 1 页补**，
 *   而不是跟着别的腿跳页 —— 跳过去就是永久漏掉那一批。
 */
fun nextPagesForAppend(
    pageBySite: Map<GallerySite, Int>,
    exhaustedBySite: Set<GallerySite>,
    dispatched: List<GallerySite>,
): Map<GallerySite, Int> = dispatched
    .filterNot { it in exhaustedBySite }
    .associateWith { (pageBySite[it] ?: 0) + 1 }

/**
 * 整体"到底"了没：**只问发出去的那些腿**。
 *
 * 没发出的腿（未配账号）不参与判断 —— 否则那一档永远不算到底，页尾会一直挂着"加载更多"。
 * 一条腿都没发出去时同样回 false：那一档该由"发车前拦住"那条路去说，不该被到底判据吞掉。
 */
fun allLegsExhausted(dispatched: List<GallerySite>, exhaustedBySite: Set<GallerySite>): Boolean =
    dispatched.isNotEmpty() && dispatched.all { it in exhaustedBySite }

/**
 * 来源小徽标上那几个字母（`Y` / `G` / `Y·G`）。
 *
 * 用在**文字位**（历史 chip 的前置小字、多站合并的那一格）：那里只有两三个字符的宽度，
 * 摆图标会把关键词挤没。
 *
 * ⚠️ 2026-09-30 起"不引站方 logo"这条**只对文字位成立**：卡片角上与首页画师行那两枚来源标识
 * 已经换成**站方真图标**（`components/venera/VeneraGallerySourceMark.kt`，那里记着两站各拿到
 * 什么原档、以及 yande.re 那枚为什么只有 16×16）。两者不是一回事，别拿这条注释去否决那枚组件。
 * 反过来也成立：本函数只服务文字位，**不要**把它接到图形位上当占位。
 *
 * 顺序按站表定死，传参顺序反过来也必须得到同一个串 —— 否则同一条历史会因为
 * `HashSet` 的实现而换字形，`remember` 与 diff 都会跟着抖。
 */
fun gallerySourceMarkText(sites: Collection<GallerySite>): String =
    GallerySite.entries.filter { it in sites }.joinToString("·") { it.sourceMarkLetter }

/** 一枚站的字母标识。`when` 不留 else：加新站时必须在这里显式表态。 */
val GallerySite.sourceMarkLetter: String
    get() = when (this) {
        GallerySite.YANDERE -> "Y"
        GallerySite.GELBOORU -> "G"
    }
