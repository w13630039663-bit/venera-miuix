package com.venera.compose

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.compose.runtime.Composable
import androidx.core.app.ActivityOptionsCompat
import com.venera.compose.gallery.data.GallerySite
import com.venera.compose.gallery.ui.GalleryPostScreen

/**
 * 画廊大图页的宿主 —— **独立 Activity**，不是 NavHost 里的目的地。
 *
 * 三条要求只有跨过 Activity 边界才同时给得到（用户 2026-09-25 点名）：
 * 1. **实时背景模糊**：本窗口的 blur-behind 由系统实时糊掉后面的 MainActivity，
 *    也就是真那一屏一级列表 —— 滚到哪、换哪一批，背景就是哪一屏。
 *    （在同一个 Activity 里"重画一面墙"是假的：它永远从列表第一行开始。）
 * 2. **跨 activity 预测式返回**：`targetSdk ≥ 36` 时系统才对这一跳施加 AOSP 动画，
 *    判据与实现都在 [VeneraSubActivityBase]。
 * 3. **向上滑入 + 卡片那一帧占位**：平台没有跨 activity 的共享图像 API（实测本机 android.jar 33~36 里
 *    只有启动图那套），所以滑入期间垫着的那一帧由 [com.venera.compose.gallery.ui.GalleryFlyIn] 递过去，
 *    页面自己弹到位；系统那套打开动画用 `makeCustomAnimation(0, 0)` 压掉，免得两套动画打架。
 *
 * 边到边、防窥、blur-behind 生命周期全部复用基类，与设置子页同一份契约。
 */
class GalleryPostActivity : VeneraSubActivityBase() {

    private lateinit var site: GallerySite
    private var postId: Long = 0L

    /** 这一页要透出后面那屏，所以基类那层不透明氛围底必须关掉。 */
    override val opaqueAmbientBackground: Boolean = false

    /**
     * 背景是整面照片墙，18dp（打码封面那档）糊不动它 —— 还能认出构图就不叫玻璃。
     * 取 32dp：与仓内"内容重度模糊"再上一档的 `space11` 同一口径。
     */
    override val blurBehindDp: Float = 32f

    override fun onCreate(savedInstanceState: Bundle?) {
        val siteKey = intent.getStringExtra(EXTRA_SITE).orEmpty()
        // 站点键只可能由 openGalleryPost 产出，认不出来就是编码错了：
        // 宁可直接炸，也不要"退回某一站"取图 —— 那会把另一站同号码的画当答案交出去。
        site = requireNotNull(GallerySite.fromRouteKey(siteKey)) { "未知画廊站点键 $siteKey" }
        postId = intent.getLongExtra(EXTRA_POST_ID, 0L)
        super.onCreate(savedInstanceState)
    }

    @Composable
    override fun SubScreen() {
        // 系统返回 / 预测式返回都落回一级；页内那套下滑关闭走同一个 finish。
        GalleryPostScreen(
            site = site,
            postId = postId,
            onBack = { finish() },
        )
    }

    companion object {
        const val EXTRA_SITE = "gallery_site_key"
        const val EXTRA_POST_ID = "gallery_post_id"
    }
}

/**
 * 从一级打开大图页。
 *
 * `makeCustomAnimation(0, 0)` 是把**打开**那套系统转场压掉：向上滑入由页面自己画，
 * 两套同时跑会出现"整页淡入 + 图从卡片飞过来"叠在一起。
 * 只压打开，不压关闭 —— 返回要留系统那套跨 activity 预测式动画。
 */
fun Activity.openGalleryPost(site: GallerySite, postId: Long) {
    startActivity(
        Intent(this, GalleryPostActivity::class.java)
            .putExtra(GalleryPostActivity.EXTRA_SITE, site.routeKey)
            .putExtra(GalleryPostActivity.EXTRA_POST_ID, postId),
        ActivityOptionsCompat.makeCustomAnimation(this, 0, 0).toBundle(),
    )
}
