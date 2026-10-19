package com.venera.compose.gallery.data

/**
 * pixiv 的一行作品（批次 pixiv · T3′）。
 *
 * 为什么不直接把标题塞进 [GalleryPost]：那颗是**三站共用**的行模型，而它没有 `title` 格是
 * 有原因的 —— booru 三站的 API 都不给标题，屏上那行标题是 [com.venera.compose.gallery.domain.GalleryCardTitle]
 * 从标签**算**出来的（判据在那颗的注释里）。pixiv 给的是**站方原题**，
 * 把两种来源并成一格，屏上就分不出"这是画师自己起的名字"还是"这是我们猜的"。
 *
 * [rank] 只有榜单那一腿有（站方直接给的 1..500），按标签那一腿交 `null` ——
 * 序号摆上去就必须是真序号，不能拿"这一排里的第几枚"冒充站方的排名。
 */
data class PixivWork(
    val post: GalleryPost,
    val title: String,
    val rank: Int? = null,
    /**
     * 作者头像（榜单卡文字区左边那一枚，用户 2026-10-17 「排行榜也没画师头像」）。
     *
     * 直接取记录里 `user.profile_image_urls.medium`（与 [PixivUser.avatarUrl] 同一格），
     * **不按 userId 另发一次请求**：一排 10 张卡就是 10 次额外往返，而头像本来就在同一个响应里。
     *
     * 空串 = 这条腿没给 ⇒ 卡上画首字母座（[com.venera.compose.gallery.ui] 那侧的降级），
     * 不拿别人的头像顶，也不补一张默认图 —— 那会让人以为站方给过。
     */
    val authorAvatarUrl: String = "",
    /**
     * 这一条是不是**动图**（ugoira）。
     *
     * 为什么要单独一格而不是读 `post.isVideo`：那一格判的是**扩展名**（`fileExt in VIDEO_EXTS`），
     * 而 pixiv 的动图给的是 `..._p0.jpg` 静帧 + 一个 zip —— 按扩展名判永远为假。
     * 站方两条腿各有自己的说法：www 搜索是 `illustType == 2`，app-api 是 `type == "ugoira"`。
     *
     * 用途只有一个：搜索面板里「作品：仅动图 / 排除动图」那一档**只能在本地筛**
     * （站方的 `type=` / `mode=` 参数现量被忽略，见 `PixivWorkType`）。
     */
    val ugoira: Boolean = false,
)

/**
 * pixiv 榜单的档位（2026-10-16 深夜现量：这五档匿名全 200，每页 50 条、`p=2` 首条 `"rank":51`）。
 *
 * ⚠️ 档名**不是** `day/week/month` —— 那三串现量 404（站方回的是「ランキングが見つかりません」）。
 * 而 R-18 那两档（`daily_r18`/`male_r18`）匿名是 **403 整档墙**，所以这里**一枚都不列**：
 * 摆一档点开必然 403 的开关就是假开关。`male`/`female` 匿名虽 200，但用户那张稿上只有这五档，
 * 加不加由产品定，不由"反正拿得到"定。
 *
 * ## 两套档名，两条腿各用各的（2026-10-17 第七轮）
 *
 * - [siteParam]：`www.pixiv.net/ranking.php?mode=` 那一套（`daily/weekly/…`，**匿名可读**）；
 * - [appParam]：`app-api.pixiv.net/v1/illust/ranking?mode=` 那一套（`day/week/…`，**必须带票据**）。
 *
 * 两套**不通用**（拿 `daily` 打 app-api、拿 `day` 打 www 都回 400/404）。所以档位这一层
 * 同时收着两个名字，而不是让调用点各拼一份 —— 拼错不报错，只会在屏上留下一档空白。
 */
enum class PixivRankingMode(val label: String, val siteParam: String, val appParam: String) {

    DAILY("日榜", "daily", "day"),
    WEEKLY("周榜", "weekly", "week"),
    MONTHLY("月榜", "monthly", "month"),
    ROOKIE("新人", "rookie", "week_rookie"),
    ORIGINAL("原创", "original", "week_original"),
    ;
}
