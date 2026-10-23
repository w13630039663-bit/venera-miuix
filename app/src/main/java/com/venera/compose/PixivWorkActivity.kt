package com.venera.compose

import android.content.Intent
import android.os.Bundle
import androidx.compose.runtime.Composable
import androidx.core.app.ActivityOptionsCompat
import com.venera.compose.gallery.data.GallerySite
import com.venera.compose.gallery.ui.GallerySearchHandoff
import com.venera.compose.gallery.ui.PixivWorkScreen

/**
 * **pixiv 作品页**的宿主（2026-10-18，站源独用）。
 *
 * 与 [GalleryPostActivity] 平行而不是复用：那颗继续承载图库三站那套"一张 = 一屏、左右翻邻居"，
 * 这一颗承载 pixiv 的"纵轴同作品多页连读、横轴换作品"。两屏互不吃对方的界面
 * （用户 2026-10-18 点名：「这个作品页是 pixiv 站源独用的，之前图库源的应该用回之前的，两个隔离开」）。
 *
 * 承载方式照旧是独立 Activity：这一页要跨 activity 的预测式返回。窗口模糊不挂
 * （见 [blurBehindEnabled]），因为作品页本身就是照片墙，那层底只在返回手势里露馅。
 */
class PixivWorkActivity : VeneraSubActivityBase() {

    private var postId: Long = 0L

    /** 这一页要透出后面那屏，所以基类那层不透明氛围底必须关掉。 */
    override val opaqueAmbientBackground: Boolean = false

    /**
     * 这一屏不挂窗口模糊。
     *
     * 作品页的内容本身就是照片墙，模糊底几乎不会露出来；真正会被看见的是返回手势里
     * 那层半径变化 —— 用户读成"背景先糊后清"，与页面缩回的形变分成两件事。
     * 关掉之后返回只剩页面离场动画，和其他内容页一致。
     */
    override val blurBehindEnabled: Boolean = false

    private val artistProfile = registerForActivityResult(ArtistProfileResultContract()) { outcome ->
        val (targetSite, artistName) = outcome ?: return@registerForActivityResult
        GallerySearchHandoff.postTag(targetSite, artistName)
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val siteKey = intent.getStringExtra(GalleryPostActivity.EXTRA_SITE).orEmpty()
        // 站点键只可能由 openGalleryPost 产出；认不出来或不是 pixiv 就是编码错了：
        // 宁可直接炸，也不要"退回某一站"取图 —— 那会把另一站同号码的画当答案交出去。
        val site = requireNotNull(GallerySite.fromRouteKey(siteKey)) { "未知画廊站点键 $siteKey" }
        require(site == GallerySite.PIXIV) { "pixiv 作品页收到了 $site —— 分流那一层写错了" }
        postId = intent.getLongExtra(GalleryPostActivity.EXTRA_POST_ID, 0L)
        super.onCreate(savedInstanceState)
    }

    @Composable
    override fun SubScreen() {
        PixivWorkScreen(
            postId = postId,
            onBack = { finish() },
            onOpenArtist = { site, name, source ->
                artistProfile.launch(artistProfileIntent(this, site, name, source))
            },
            // 「相关图片」里点一张 = **新开一颗作品页**（2026-10-19 第 8 条）。
            //
            // 旧写法是 `intent.putExtra(...) + recreate()`，两个病一起长在上面：
            // 1. `recreate()` 不压栈 ⇒ 返回直接落回画廊主屏（用户：「返回不会回到上一个作品页」）；
            // 2. `recreate()` 让系统按它自己存的那份 Intent 重开，运行时 `putExtra` 那一笔
            //    不保证带过去 ⇒ 新那一屏的 `postId` 可能还是上一条，而横轴已经是那一排相关图片
            //    ⇒ `indexOfFirst` = -1 ⇒ 落在第一张，正好不是用户点的那张（用户：「点开是别的作品」）。
            //
            // 现在：那一排先由页面交给 `PixivWorkWallHandoff`（按目标 id 配对、一次性），
            // 这里只负责 `startActivity` 压一层 —— 返回逐级退回，点击目标由 Intent 里的 id 钉死。
            onOpenWork = { id ->
                startActivity(
                    intent(this, id),
                    ActivityOptionsCompat.makeCustomAnimation(this, R.anim.miuix_activity_open_enter, R.anim.miuix_activity_open_exit).toBundle(),
                )
            },
            // 动图交回旧看图器：那一颗已经会解逐帧动画，这一屏只画得出第一帧。
            onOpenViewer = { id ->
                startActivity(
                    Intent(this, GalleryPostActivity::class.java)
                        .putExtra(GalleryPostActivity.EXTRA_SITE, GallerySite.PIXIV.routeKey)
                        .putExtra(GalleryPostActivity.EXTRA_POST_ID, id),
                    ActivityOptionsCompat.makeCustomAnimation(this, R.anim.miuix_activity_open_enter, R.anim.miuix_activity_open_exit).toBundle(),
                )
            },
        )
    }

    companion object {

        /** 由 [openGalleryPost] 拼：站点键与作品号两枚 extra 与大图页同一对，分流只在这一颗里判一次。 */
        fun intent(context: android.content.Context, postId: Long): Intent =
            Intent(context, PixivWorkActivity::class.java)
                .putExtra(GalleryPostActivity.EXTRA_SITE, GallerySite.PIXIV.routeKey)
                .putExtra(GalleryPostActivity.EXTRA_POST_ID, postId)
    }
}
