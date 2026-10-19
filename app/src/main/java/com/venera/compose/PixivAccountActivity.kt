package com.venera.compose

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.compose.runtime.Composable
import androidx.core.app.ActivityOptionsCompat
import com.venera.compose.gallery.ui.PixivAccountSection
import com.venera.compose.gallery.ui.PixivAccountScreen

/**
 * pixiv 个人账号页的宿主 —— **独立 Activity**（2026-10-19，方案 §10 点名的那颗）。
 *
 * 为什么不是 NavHost 里的目的地：跨 Activity 的预测式返回在 animation 1.12 上给不到页内目的地。
 * 从前这一页跟画师页一样靠窗口 blur-behind 透出首页那面墙；2026-10-19 真机截图里整屏是一层
 * 粉色糊底，用户点名「整个页面背景怎么是模糊背景」—— 这一页不是图片浮层，它是**一张正常的内容页**，
 * 于是改回不透明氛围底、blur-behind 一并关掉（关的是能力，不只是拿 Canvas 盖住它）。
 *
 * 栈是 Main → Account：只有一级，所以没有"返回两次"那一族问题（画师页从大图页点进去才有那一级）。
 * 退出登录之后由屏自己 `finish()`，落回首页 —— 那一屏在没有凭据之后三段全是同一句"还没登录"，
 * 留着只是让人再点三次。
 */
class PixivAccountActivity : VeneraSubActivityBase() {

    override val opaqueAmbientBackground: Boolean = true

    override val blurBehindEnabled: Boolean = false

    /**
     * 进来落在哪一段。
     *
     * 认不出来的字符串退「动态」而不是崩：这一枚 extra 只有本 app 自己写得进来，
     * 认不出来意味着旧版本存下的返回栈里带着一个已经改名的档位 —— 那种情况下
     * 把用户丢回默认那一屏比给他一个闪退好，而**这一屏三段都在**，没有"丢错地方"这一说。
     */
    private val startSection: PixivAccountSection by lazy {
        when (intent.getStringExtra(EXTRA_SECTION)) {
            PixivAccountSectionEntry.BOOKMARKS -> PixivAccountSection.BOOKMARKS
            PixivAccountSectionEntry.FOLLOWING -> PixivAccountSection.FOLLOWING
            else -> PixivAccountSection.DYNAMIC
        }
    }

    @Composable
    override fun SubScreen() {
        PixivAccountScreen(
            startSection = startSection,
            onBack = { finish() },
            onOpenPost = { site, id -> openGalleryPost(site, id) },
        )
    }

    companion object {
        /** 进来就落在哪一段（`account` / `bookmarks` / `following`）。 */
        const val EXTRA_SECTION = "pixiv_account_section"
    }
}

/**
 * 打开 pixiv 账号页。
 *
 * `makeCustomAnimation(0, 0)` 压掉的只是**打开**那套系统转场，返回留系统那套跨 Activity
 * 预测式动画 —— 与 [openGalleryArtistProfile] 完全同一个理由。
 *
 * @param section 落在哪一段；收藏 hub 那枚 🔖 改跳之后交的是 `bookmarks`（2026-10-19 拍板 ⑦）。
 */
fun Context.openPixivAccountPage(section: String = PixivAccountSectionEntry.DYNAMIC) {
    startActivity(
        Intent(this, PixivAccountActivity::class.java)
            .putExtra(PixivAccountActivity.EXTRA_SECTION, section),
        ActivityOptionsCompat.makeCustomAnimation(this, 0, 0).toBundle(),
    )
}

/**
 * 那三段在** Intent 上**的名字。
 *
 * ⚠️ 与 `gallery/ui/PixivAccountSection` 的枚举名**必须逐字同串**（那里是 `DYNAMIC/BOOKMARKS/FOLLOWING`）。
 * 这一颗故意不 import 屏里那枚枚举：Intent 的 extras 是跨进程的字符串，
 * 让宿主依赖界面层的枚举会把"Activity 只递身份键、对象走屏里"那条口径破掉
 * （平板分栏那次闪退的教训：`rememberSaveable` 的载荷只认 Bundle 类型）。
 */
object PixivAccountSectionEntry {
    const val DYNAMIC = "DYNAMIC"
    const val BOOKMARKS = "BOOKMARKS"
    const val FOLLOWING = "FOLLOWING"
}
