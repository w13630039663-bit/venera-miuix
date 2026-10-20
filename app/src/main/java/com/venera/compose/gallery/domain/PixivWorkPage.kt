package com.venera.compose.gallery.domain

import com.venera.compose.gallery.data.GalleryPost
import com.venera.compose.gallery.data.PixivPage
import com.venera.compose.gallery.data.PixivWorkDetail
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 作品页标题下面那一行的三格。UI 侧按这一枚挑图标，**不在 UI 里判"有没有"** ——
 * 站方没给的那格必须在这里就掉出去，屏上才不会长出"0 浏览"这种看着像读数的假话。
 */
enum class PixivWorkFact { VIEWS, BOOKMARKS, PUBLISHED }

/** 一格的成品文本（纯数字或 `yyyy-MM-dd`，不带单位、不带 locale 月份名）。 */
data class PixivWorkFactCell(val kind: PixivWorkFact, val text: String)

/** 同作品多页之间的缝（2026-10-18 用户「最好无缝或者只留一条边」；12dp 那一档是网格口径，已撤）。 */
enum class PixivPageSeam { NONE, HAIRLINE }

/**
 * pixiv 作品页的纯判据（2026-10-18 落地）。
 *
 * 这一颗不收 Android、不收 Compose、不收网络：三格计数、占位比例、页轴墙的逐页映射、标签同屏上限
 * 都是"能拿真读数复算"的判断，摆在这里才有单测可钉（同一族的口径见 `GalleryAi`、`GalleryCardTitle`）。
 *
 * ⚠️ 从前这里还有两颗「横轴走一步」（`step` / `PixivStep`），钉的是"两轴互换"那一档滑到尽头的四种结局；
 *    2026-10-18 第四批整档撤掉横着一页一页之后它们**零调用点**，一起摘了 —— 不留"看着像判据的死表面"。
 */
object PixivWorkPage {

    /**
     * 标签同屏上限（用户 2026-10-18「tag 同屏最多可以同时有 8 个，更多才需要展开」）。
     *
     * 这是**上限不是目标**：不足 8 枚时「更多」把手一枚都不许出现 —— 没有可展开的东西却有把手，
     * 就是假开关。所以 [tagsHidden] 与 [tagsShown] 是同一把尺子的两头。
     */
    const val TAGS_INLINE_MAX = 8

    /**
     * 图回来之前那一格占位按什么比例（返回值 = 宽 / 高）。
     *
     * 站方没报宽高就交 null：屏上那一格**不预留比例**，而不是拿 1:1 顶一个 —— 替站方编一个形状，
     * 表现是图落位那一刻整屏跳一下，用户读到的是"这屏在抽风"（用户 2026-10-18：「p站的图片加载
     * 都没骨架图」，而骨架要按真比例摆才有意义）。
     *
     * ## 为什么这里**不**夹区间（而墙上那张卡夹）
     *
     * 这一格画的是**正文那一页**，完整显示内容是它的本份 —— 夹了就是"为了墙的整齐去裁读者正在看的图"。
     * 墙上那张卡不同：它是**索引**，整齐度是它的一半职责，所以它有区间（[cardBoxRatio]，
     * pixiv 那一档 0.2~4.0）。
     *
     * 两边于是有一处**故意的不对称**：比例落在 pixiv 区间内（0.2~4.0）时两把尺**同值**
     * （竖向跨页 0.27、横向大图 3.0 都落在里面 ⇒ 不裁、飞行两端同比例）；
     * 只有极端长条（<0.2 或 >4.0）才会一头夹一头不夹，那时飞行体略有形变 —— 宁可如此，
     * 也不裁读者正在看的那一页。
     */
    fun placeholderAspect(width: Int?, height: Int?): Float? {
        if (width == null || height == null) return null
        // 0 与负数不是"很扁"或"很高"：除出去是 Infinity 或负比例，骨架会撑满整屏或倒过来。
        if (width <= 0 || height <= 0) return null
        return width.toFloat() / height.toFloat()
    }

    /**
     * 把"这一作的几页"长成一堵**页轴墙**，交给大图播放器左右翻（2026-10-18，用户
     * 「点击图片单独显示高清点的图片，复用图库的大图播放器就行，支持左右滑动切换本作品的多张图片，
     * 不能切换其他作品，HD 才显示原图」）。
     *
     * 为什么摆在这里而不是屏上：这一颗不收 Android、不收 Compose，四格映射全是纯换算，
     * 而它错一格的后果是"翻到第 2 页画的还是第 1 页"—— 那种错只有算式级用例逮得住。
     *
     * 四条口径：
     * - **默认那一屏画中档**（`fast`/`large`/`preview` 三格都指站方的 `regular`），
     *   原图只住 [GalleryPost.fileUrl] —— 播放器顶上那一层只在 HD 那一档才画它（与图库三站同一套）；
     * - **扩展名逐页从地址尾巴取**：同一位画师的两张原图一张 `.png`、一张 `.jpg`（现量），
     *   沿用第 0 页那档会把 jpg 存成 png；
     * - 逐页宽高缺档就交 **0**，不拿首叶的尺寸顶 —— 0 会顺着 [placeholderAspect] 与
     *   `galleryNativeZoomFactor` 各自退成"不预留比例""算不出 1:1 就不摆双击"，
     *   而顶一个错尺寸进去是替站方编一个形状（1:1 那一档会跟着算错）；
     * - [GalleryPost.uid] **不带页位**（收藏、历史、去重记的是这一条作品），逐页分开的那几处
     *   吃 [GalleryPost.viewerKey] 与缓存键里的页位。
     */
    fun pageWallOf(detail: PixivWorkDetail): List<GalleryPost> =
        detail.pages.mapIndexed { index, page ->
            detail.post.copy(
                previewUrl = page.mediumUrl,
                fastUrl = page.mediumUrl,
                largeUrl = page.mediumUrl,
                fileUrl = page.originalUrl,
                fileExt = page.originalUrl.substringAfterLast('.', "").substringBefore('?'),
                width = page.width ?: 0,
                height = page.height ?: 0,
                pageCount = detail.pages.size,
                pageOrdinal = index,
            )
        }

    /**
     * 首帧那一格的**种子**（批 B⑥，2026-10-19）：详情腿还没到，用列表腿那条作品长出一枚
     * "只有第 0 页"的假详情，交给 [pageWallOf] 走**同一颗算式** —— 逐页键于是与真详情那一档
     * 天然相等（`uid:p0`，屏上不另拼 ":p0"，那是第二处真相），落点登记 / 交棒 / `openPages`
     * 三条都接得住同一把键。
     *
     * pixez 从不"等落点"的凭据就压在这一格上（计划 §4.3 第 1 条）：它的首帧有真比例，落点
     * 第一帧就量得出来；而这一屏从前把"详情到手"当成起飞前置 —— 真机慢首开（详情 700ms >
     * 预算 600ms）读起来就是"点卡进去压根不飞"（§9.3 排行榜那一格的读数，`起飞` 那行压根没打）。
     *
     * **只许用来摆第 0 页那一格、量落点、报逐页键、喂 `openPages`**（点首帧那格进播放器时
     * 手里得有东西，否则是死把手）；"读数的脸"一律留空 —— 空 = 站方没给，屏上那一格随之不摆
     * （与 [facts] / [tagsShown] 既有口径同一条，绝不摆"0 浏览 / 共 1 页"这种看着像读数的假话）：
     * - 标题空、`viewCount`/`uploadDate`/`commentCount` 全 null ⇒ 三格计数与评论节都不出现；
     *   详情段那一格仍由**真 detail 非空**守卫（见 `PixivWorkFlow`），种子不把它顶出来；
     * - `tags` 空表 ⇒ 标签节没有可摆的枚数；`authorId=null` ⇒ 关注/看这位作品两把手都不摆；
     * - `ugoira=false` ⇒ 走页那一支（ugoira 判据只认真 detail，种子不承担）；
     * - `pageCount` 取列表腿的真值，站方没给就 0（`pageSemantic` 遇 0 交空串，不编"共 1 页"）；
     * - 图源吃 [GalleryPost.previewUrl]：墙上那张卡载的就是它，Coil 内存缓存就在手边，
     *   首帧因此是"糊一点的真图"而不是骨架；详情到手换中档（regular）是**变清晰**、不是跳一下
     *   （`PixivPageImage` 头注那条"同比例 Crop 不改构图"的口径，两腿同比例故格子不跳、落点稳）。
     * - `originalUrl` 仍交真原图档：若详情永不到而用户点了首帧那格，播放器 HD 那一档是真的；
     * - 宽高取站方报的那一对（列表腿恒有；真机现量 2400×4000），缺则 null ⇒ [placeholderAspect]
     *   照旧交"不预留比例"，不替站方编一个形状。
     */
    fun seedDetailOf(post: GalleryPost): PixivWorkDetail = PixivWorkDetail(
        post = post,
        title = "",
        authorId = null,
        authorAccount = post.author,
        pages = listOf(
            PixivPage(
                mediumUrl = post.previewUrl,
                originalUrl = post.fileUrl,
                width = post.width.takeIf { it > 0 },
                height = post.height.takeIf { it > 0 },
            ),
        ),
        pageCount = post.pageCount ?: 0,
        tags = emptyList(),
        viewCount = null,
        uploadDate = null,
        commentCount = null,
        commentsOff = false,
        ugoira = false,
    )

    /**
     * 标题下面那一行：三格各自"站方给了才算一格"。
     *
     * [bookmarkCount] 传的是 `GalleryPost.favCount`（同一格 `bookmarkCount` 来的，两处不各读一遍）；
     * [viewCount] 与 [uploadDate] 只有作品页那一腿有（2026-10-18 现量 851 / `2026-10-01T06:12:00+00:00`）。
     * 三格全空 ⇒ 交空表，那一行整行不摆。
     */
    fun facts(viewCount: Int?, bookmarkCount: Int?, uploadDate: String?): List<PixivWorkFactCell> {
        val out = ArrayList<PixivWorkFactCell>(3)
        viewCount?.let { out += PixivWorkFactCell(PixivWorkFact.VIEWS, it.toString()) }
        bookmarkCount?.let { out += PixivWorkFactCell(PixivWorkFact.BOOKMARKS, it.toString()) }
        publishedDay(uploadDate)?.let { out += PixivWorkFactCell(PixivWorkFact.PUBLISHED, it) }
        return out
    }

    /**
     * 发布日：**只到日**，且换到本机时区再截。
     *
     * 为什么要换：站方那格是 UTC 串（现量 `2026-10-01T06:12:00+00:00`），而同一张图的地址路径里
     * 写的是 `2026-10-01/15/12/34` —— 站方自己按 +09:00 记的。直接截前 10 位会把
     * 「本机凌晨发布的那批」显示成前一天。读不懂（缺键、坏格式）⇒ 交 null，那一格不摆。
     */
    fun publishedDay(raw: String?): String? = raw?.let { text ->
        runCatching {
            OffsetDateTime.parse(text)
                .atZoneSameInstant(ZoneId.systemDefault())
                .format(DateTimeFormatter.ISO_LOCAL_DATE)
        }.getOrNull()
    }

    /** 屏上铺得下的那几枚：折叠态最多 [TAGS_INLINE_MAX] 枚，展开态给全部（顺序照站方给的来）。 */
    fun tagsShown(tags: List<String>, expanded: Boolean): List<String> =
        if (expanded || tags.size <= TAGS_INLINE_MAX) tags else tags.take(TAGS_INLINE_MAX)

    /** 折叠着没铺的枚数。**0 就意味着「更多」那一枚把手不许出现**。 */
    fun tagsHidden(tags: List<String>): Int = (tags.size - TAGS_INLINE_MAX).coerceAtLeast(0)

    /**
     * 「站方答上了、可这一条没有可看的图」到底是哪一面墙（2026-10-18 第四批第七件）。
     *
     * 从前这四种与"真被删"盖成同一句「被删或被锁」，用户读到的是"我明明登录了怎么说被删"。
     * 站方在那一份答复里把四面旗标都竖得很清楚，分开念才有下一步可做：前三面在**登录腿**下
     * 本来就取得到图（所以取数那一层改成有票据先走登录腿），只有"一枚旗标都没竖"才是真的没有。
     *
     * ⚠️ 四枚都是**可空**的：缺键与置位是两句话 —— 把"站方这一格没给"读成"锁着"，
     * 屏上就会对一条正常作品念"这一条被作者设为私密"（同一族假归因本仓立过案）。
     * 次序是**私密 → 隐藏 → 分级 → 仅登录**：作者自己关了的那一条，不必再看其余旗标。
     */
    fun lockReason(
        isLoginOnly: Boolean?,
        xRestrict: Int?,
        restrict: Int?,
        isUnlisted: Boolean?,
    ): PixivLockReason? = when {
        (restrict ?: 0) != 0 -> PixivLockReason.PRIVATE
        isUnlisted == true -> PixivLockReason.UNLISTED
        (xRestrict ?: 0) != 0 -> PixivLockReason.ADULT
        isLoginOnly == true -> PixivLockReason.LOGIN_ONLY
        else -> null
    }
}

/**
 * 一条作品"有答复、没图"的四种成因（判式在 [PixivWorkPage.lockReason]）。
 *
 * 每一档自带屏上那一句（[sentence]）：成因与下一步在同一条里说完，用户不必再猜该做什么。
 * 措辞留在这里而不是散在 UI，是因为它要**能拿真读数复算**（`PixivWorkPageDataTest` 逐档钉），
 * 而四档若各写各的句子，两档撞成同一句就是"分档根本没分开"——那种错编译器看不见。
 */
enum class PixivLockReason(val sentence: String) {
    /** 站方 `restrict != 0`：作者把这一条设成私密。 */
    PRIVATE("这一条被作者设为私密，只有作者本人在登录状态下能看"),

    /** 站方 `isUnlisted == true`：不在任何列表里公开出现（能从号直达，但不算公开）。 */
    UNLISTED("这一条被作者隐藏，站方不把它公开列出来，图也就没给到这一腿"),

    /** 站方 `xRestrict` = 1（R-18）或 2（R-18G）：未登录的读者整条不给图。 */
    ADULT("这一条是成人向，站方对未登录的读取不给图 —— 在设置里登录 pixiv 账号就能看"),

    /** 站方 `isLoginOnly == true`：分级之外的"仅登录可见"（粉丝限定这类）。 */
    LOGIN_ONLY("这一条要登录才能看，当前这一腿没有可用的登录凭据"),
}
