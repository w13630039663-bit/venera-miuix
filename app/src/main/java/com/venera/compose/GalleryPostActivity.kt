package com.venera.compose

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.compose.runtime.Composable
import androidx.core.app.ActivityOptionsCompat
import com.venera.compose.gallery.data.GallerySite
import com.venera.compose.gallery.ui.GalleryPostScreen
import com.venera.compose.gallery.ui.GallerySearchHandoff

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

    /**
     * 画师介绍页那一跳要**收结果**（用户 2026-10-01 拍板：返回栈上不许出现"返回两次"）。
     *
     * 页里那枚「看 TA 全部作品」交回站点与画师名后，这里做两件事：把它写进
     * [com.venera.compose.gallery.ui.GallerySearchHandoff]（一级那侧的消费方与跳转都不必知道
     * 这一跳去过哪儿），然后**自己也 finish** —— 两级一起退，落回 Main 后由宿主层那条
     * 窥视 pending 的 effect 把画廊 Tab 推到眼前。用户只是按返回退出时结果码不是 OK，
     * 什么都不做（大图页还压着，退回它才是对的）。
     */
    private val artistProfile = registerForActivityResult(ArtistProfileResultContract()) { outcome ->
        val (targetSite, artistName) = outcome ?: return@registerForActivityResult
        GallerySearchHandoff.postTag(targetSite, artistName)
        finish()
    }

    /** 这一页要透出后面那屏，所以基类那层不透明氛围底必须关掉。 */
    override val opaqueAmbientBackground: Boolean = false

    /**
     * 背景是整面照片墙，18dp（打码封面那档）糊不动它 —— 还能认出构图就不叫玻璃。
     * 取 32dp：与仓内"内容重度模糊"再上一档的 `space11` 同一口径。
     */
    override val blurBehindDp: Float = 32f

    /**
     * **本页是"推迟型"**：`onStart` 不挂模糊，等页面那侧说"图要起飞了"再挂。
     *
     * 理由与读数写在 [VeneraSubActivityBase.deferredBlurBehind] 与 `GalleryPostScreen.backdropEntryAlpha`：
     * 入场第一帧屏上必须与点击前那一帧相同（清晰的主界面），否则新窗口一出现就先糊一下、暗一下，
     * 那正是"打开图片会闪一下"。起飞之后图在动，背景同时退下去，那一下突变才被运动吃掉。
     */
    override val deferredBlurBehind: Boolean = true

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
            // 出处由页面那侧交进来（它就在当前那一条帖子身上）：介绍页要拿它补外链与头像，
            // 而 Activity 侧够不着 —— 留一份空串当"没有出处"就是让那一页少摆两样。
            onOpenArtist = { name, postSource ->
                artistProfile.launch(artistProfileIntent(this, site, name, postSource))
            },
            // 入场动作开始（图起飞 / 整页抬起）才把玻璃底挂上 —— 见 [deferredBlurBehind]。
            onEnterStart = { armBlurBehind() },
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
