package com.venera.compose

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultCallback
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.runtime.Composable
import androidx.core.app.ActivityOptionsCompat
import com.venera.compose.gallery.data.GallerySite
import com.venera.compose.gallery.ui.GalleryArtistProfileScreen

/**
 * 画廊画师介绍页的宿主 —— **独立 Activity**，与大图页同一承载（用户 2026-10-01 拍板）。
 *
 * 为什么不是 NavHost 里的目的地：那条路要的是实时背景模糊与跨 Activity 的预测式返回，
 * 这两样在 animation 1.12 上**结构性地给不到**页内目的地（查证与"别再重开这条"记在
 * `memory/project-shared-element-transition.md`）。本页从大图页点开，背景正是那一屏大图，
 * 模糊 32dp 就是页的底（与 [GalleryPostActivity] 同一档：还能认出构图就不叫玻璃）。
 *
 * ## 返回栈上不许出现"返回两次"
 *
 * 栈是 Main → Post → Profile。页里那枚「看 TA 全部作品」如果只是 `finish()` 自己，
 * 用户退回的是**还压着的那张大图**，读起来就是"这一下没反应"。所以那一枚走 [setResult]
 * 交回站点与画师名后 `finish()`，由 [GalleryPostActivity] 收到结果把它写进
 * [com.venera.compose.gallery.ui.GallerySearchHandoff] 并**自行 finish** —— 两级一起退，
 * 落到一级的画廊搜索（跳转由 `Navigation.kt` 里那条宿主层 effect 负责，批次 Q+R 批量 2）。
 *
 * 从首页「正在关注的画师」长按进时栈里只有 Main → Profile：没人收结果，走普通
 * [openGalleryArtistProfile]（不取结果）那一条，退回 Main 后同样由那条 effect 落到画廊 Tab。
 */
class GalleryArtistProfileActivity : VeneraSubActivityBase() {

    private lateinit var site: GallerySite
    private lateinit var artistName: String
    private var source: String = ""

    /** 这一页要透出后面那屏，所以基类那层不透明氛围底必须关掉（与大图页同一条）。 */
    override val opaqueAmbientBackground: Boolean = false

    override val blurBehindDp: Float = 32f

    override fun onCreate(savedInstanceState: Bundle?) {
        val siteKey = intent.getStringExtra(EXTRA_SITE).orEmpty()
        // 站点键只可能由 openGalleryArtistProfile 产出，认不出来就是编码错了：
        // 宁可直接炸，也不要"退回某一站"去取 —— 那会把另一站同名画师的数据交成答案。
        site = requireNotNull(GallerySite.fromRouteKey(siteKey)) { "未知画廊站点键 $siteKey" }
        artistName = intent.getStringExtra(EXTRA_ARTIST).orEmpty()
        source = intent.getStringExtra(EXTRA_SOURCE).orEmpty()
        super.onCreate(savedInstanceState)
    }

    @Composable
    override fun SubScreen() {
        GalleryArtistProfileScreen(
            site = site,
            artistName = artistName,
            source = source,
            onBack = { finish() },
            onBrowseAllWorks = {
                setResult(
                    Activity.RESULT_OK,
                    profileResultIntent(site, artistName),
                )
                finish()
            },
        )
    }

    companion object {
        const val EXTRA_SITE = "gallery_artist_site"
        const val EXTRA_ARTIST = "gallery_artist_name"
        const val EXTRA_SOURCE = "gallery_artist_source"
    }
}

/** 结果里带回来的两样：去哪一站、搜谁。 */
private fun profileResultIntent(site: GallerySite, artistName: String): Intent = Intent()
    .putExtra(GalleryArtistProfileActivity.EXTRA_SITE, site.routeKey)
    .putExtra(GalleryArtistProfileActivity.EXTRA_ARTIST, artistName)

internal fun artistProfileIntent(
    context: Context,
    site: GallerySite,
    artistName: String,
    source: String,
): Intent = Intent(context, GalleryArtistProfileActivity::class.java)
    .putExtra(GalleryArtistProfileActivity.EXTRA_SITE, site.routeKey)
    .putExtra(GalleryArtistProfileActivity.EXTRA_ARTIST, artistName)
    .putExtra(GalleryArtistProfileActivity.EXTRA_SOURCE, source)

/**
 * 从一级打开画师介绍页（**不取结果**那一条：首页画师行长按）。
 *
 * `makeCustomAnimation(0, 0)` 压掉的只是**打开**那套系统转场，返回留系统那套跨 Activity
 * 预测式动画 —— 与 [openGalleryPost] 完全同一个理由。
 */
fun Context.openGalleryArtistProfile(site: GallerySite, artistName: String, source: String = "") {
    startActivity(artistProfileIntent(this, site, artistName, source), ActivityOptionsCompat.makeCustomAnimation(this, 0, 0).toBundle())
}

/**
 * 大图页侧那一跳的结果契约（注册点见 `GalleryPostActivity`）。
 *
 * 为什么用一份自己的 [ActivityResultContract] 而不是 `StartActivityForResult()`：后者回的是
 * 整个 `ActivityResult`，调用点得再挖一遍 extras 里的站点键、再判空、再认站 —— 而"认不出站点键
 * 就当没这回事"正是这一层最不想要的形状。这一份在**交回之前**就把站点认完，只给
 * `(站点, 画师名)`；认不出（含用户只是按返回退出）一律 null，调用点没有第二种写法可选。
 */
class ArtistProfileResultContract : ActivityResultContract<Intent, Pair<GallerySite, String>?>() {

    override fun createIntent(context: Context, input: Intent): Intent = input

    override fun parseResult(resultCode: Int, intent: Intent?): Pair<GallerySite, String>? {
        if (resultCode != Activity.RESULT_OK) return null
        val key = intent?.getStringExtra(GalleryArtistProfileActivity.EXTRA_SITE).orEmpty()
        val name = intent?.getStringExtra(GalleryArtistProfileActivity.EXTRA_ARTIST).orEmpty()
        val site = GallerySite.fromRouteKey(key) ?: return null
        return site to name
    }
}
