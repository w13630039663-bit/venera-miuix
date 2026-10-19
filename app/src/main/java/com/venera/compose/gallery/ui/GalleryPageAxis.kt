package com.venera.compose.gallery.ui

import androidx.compose.runtime.Composable
import coil3.ImageLoader
import com.venera.compose.gallery.data.GalleryPost
import com.venera.compose.gallery.data.GallerySite

/**
 * **页轴**交接：这一作的这几页，交给大图播放器当"左右翻的那面墙"（2026-10-18）。
 *
 * 为什么走这种跨 Activity 的静态交接（与 [GalleryViewerQueue]、[GalleryFlyIn] 同一路数）：
 * 播放器是独立 Activity（实时 blur-behind + 跨窗口预测式返回），而"同一作的第 2、3、4 页"
 * 是内存里的一组地址，Intent 塞不下。
 *
 * 与 [GalleryViewerQueue] 的分工是**两件事**，不是两份缓存：
 * - 那颗装的是"把我送进来的那面墙"（邻居作品，左右翻 = 换作品）；
 * - 这一颗装的是"这一条作品自己的几页"（左右翻 = 换页，**不许翻到别条作品** —— 用户点名）。
 * 两件事混在一颗里就会出现在某一处 `set` 掉另一处：作品页写邻居墙那一刻，播放器正拿着它翻页。
 *
 * 信息面板那一格也走这里，而且**只交一个函数引用**（`::pixivWorkInfoPanel` 那种顶层的，
 * 不是从组合里现攒的 lambda）：从前那种闭包会把点图那一刻的收藏态、关注态冻在交接件里，
 * 回到播放器再点关注就成"屏上说了不算"。面板要的数据走 [Wall.panelData] 一起交，
 * 这一颗本身**不认识**它是什么类型 —— 所以旧看图器那一侧不需要认识任何新屏的模型。
 */
object GalleryPageAxis {

    /** 传给面板渲染器的那一包：播放器自己的回调与件，由播放器现场给（不是交接时抓的）。 */
    class PanelParams(
        val post: GalleryPost,
        val imageLoader: ImageLoader,
        /** 画师行那一块整块可点 → 介绍页（与 [GalleryInfoSheet] 同一口径：不离开本页）。 */
        val onOpenArtist: (String, String) -> Unit,
        /** 下载当前这一页：进度与 toast 归播放器那一层持有，面板只显示。 */
        val onDownload: () -> Unit,
        val onDismiss: () -> Unit,
        /**
         * 面板里点一枚作品标签 → 按它搜一轮（用户 2026-10-19：「作品标签目前不可以点击，
         * 点击后跳转到标签的搜索页面」）。
         *
         * 默认 `null` = **这一层没有搜索出口**：面板照旧摆标签、只是点不动（与
         * [GalleryPageAxis.Wall] 那条"绝不摆点了没反应的把手"同一口径，所以这里不硬塞一个空实现
         * —— 空实现就是一按没反应的假开关）。播放器那一侧给的是一个真出口：收起面板、把这一枚
         * 标签交给 `GallerySearchHandoff`。
         */
        val onSearchTag: ((String) -> Unit)? = null,
    )

    /**
     * [data] 就是 [Wall.panelData] 原样交回（这一颗不解释它）；返回 false = 那一版面板
     * **这时无话可说**，播放器退回自己原有的那一版，而不是摆一块空白。
     */
    class Wall(
        val site: GallerySite,
        val postId: Long,
        val startIndex: Int,
        val pages: List<GalleryPost>,
        val panelData: Any?,
        val infoPanel: (@Composable (Any?, PanelParams) -> Unit)?,
    )

    private var wall: Wall? = null

    /** 起跳之前由打开方写这一次（顺序就是页序）。 */
    fun open(wall: Wall) {
        this.wall = wall
    }

    /**
     * 只有**同一站同一个号**才应答。
     *
     * 站点必须一起对：同号码在另一站是另一幅画（那一族判据写在 [GallerySite] 上），
     * 答错了就是把别站的图当这一作的第 3 页画出来。认不出就交 null，播放器退回邻居墙或单张。
     */
    fun peek(site: GallerySite, postId: Long): Wall? =
        wall?.takeIf { it.site == site && it.postId == postId }
}
