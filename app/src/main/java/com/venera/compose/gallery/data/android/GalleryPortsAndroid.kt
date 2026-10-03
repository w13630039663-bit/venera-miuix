package com.venera.compose.gallery.data

import android.content.Context
import com.venera.compose.data.prefs.VeneraPreferences
import com.venera.compose.gallery.domain.GalleryAnimatedMode
import com.venera.compose.gallery.domain.GalleryColumnMode
import com.venera.compose.gallery.domain.GalleryPreloadMode
import com.venera.compose.gallery.domain.GalleryPreviewQuality
import com.venera.compose.gallery.domain.GalleryViewerBackdrop
import com.venera.compose.security.guard.ContentGuardManager
import com.venera.compose.security.guard.GuardRule
import kotlinx.coroutines.flow.StateFlow

/**
 * `GalleryPorts` 的 **Android 接线半边**（容器与契约留在 `GalleryPorts.kt`）。
 *
 * 为什么要拆：容器 `GalleryPorts` + `install/of` 的形状本来就是桌面就绪的（`platform: String`
 * 只是用来拒绝重复接线，`of(handle: Any?)` 收的是不透明句柄），而这一半边里的 `Context`
 * 是它上不了桌面编译面的唯一原因。`android/` 子目录被 `desktop/build.gradle.kts` 那颗
 * 全局的那颗 android 目录排除挡在桌面外，所以搬到这里就等于"契约两端共享、接线各端自持"。
 *
 * ⚠️ 包名仍写 `com.venera.compose.gallery.data`（Kotlin 允许包名不等于目录名）：
 * `VeneraApp.kt` 那句 `AndroidGalleryPorts.install()` 的 import 因此一字不用改。
 */
object AndroidGalleryPorts {

    const val PLATFORM = "android"

    @Volatile
    private var ports: GalleryPorts? = null

    fun install() {
        GalleryPorts.install(platform = PLATFORM) { handle -> createPorts(handle) }
    }

    private fun createPorts(handle: Any?): GalleryPorts = ports ?: synchronized(this) {
        ports ?: run {
            val context = handle as? Context
                ?: throw IllegalStateException(
                    "画廊业务 API 的 Android 接线需要 Context，实际收到：${handle?.javaClass?.name ?: "null"}"
                )
            GalleryPorts(
                contentGuard = AndroidGalleryContentGuard(context),
                prefs = AndroidGalleryPreferences(context),
                favorites = AndroidGalleryFavorites(context),
                follows = AndroidGalleryArtistFollows(context),
                avatars = AndroidGalleryArtistAvatars(context),
                lexicon = AndroidGalleryTagLexicon(context),
                credentials = AndroidGalleryCredentials(context),
                stores = AndroidGalleryStoreFactory(context),
                boards = AndroidGalleryBoards(context),
                artists = AndroidGalleryArtistDirectory(context),
                reverse = AndroidGalleryReverseSearch(context),
                hygiene = AndroidGalleryNetworkHygiene(context),
            ).also { ports = it }
        }
    }
}

/** Android 接线。⚠️ 构造不调 `getInstance`，只在成员方法体里现取 —— 取用时机与改造前逐点相同。 */
private class AndroidGalleryContentGuard(private val context: Context) : GalleryContentGuard {

    private val manager: ContentGuardManager get() = ContentGuardManager.getInstance(context)

    override val nsfwMaskMode: StateFlow<String> get() = manager.nsfwMaskMode

    override val rules: StateFlow<List<GuardRule>> get() = manager.rules

    override fun blockedGalleryRule(author: String, tags: List<String>): GuardRule? =
        manager.findGalleryBlockedRule(author = author, tags = tags)

    override suspend fun addRule(type: String, pattern: String, isRegex: Boolean): Long =
        manager.addRule(type, pattern, isRegex)
}

/** 12 枚只读 getter，交回同一个 `StateFlow` 实例：订阅对象没换、重组时序没换。 */
private class AndroidGalleryPreferences(private val context: Context) : GalleryPreferences {

    private val prefs: VeneraPreferences get() = VeneraPreferences.getInstance(context)

    override val galleryColumnMode: StateFlow<GalleryColumnMode> get() = prefs.galleryColumnMode
    override val galleryPreviewQuality: StateFlow<GalleryPreviewQuality> get() = prefs.galleryPreviewQuality
    override val galleryKeepScreenOn: StateFlow<Boolean> get() = prefs.galleryKeepScreenOn
    override val galleryVolumeKeyTurn: StateFlow<Boolean> get() = prefs.galleryVolumeKeyTurn
    override val galleryAutoPlaySec: StateFlow<Int> get() = prefs.galleryAutoPlaySec
    override val galleryPreload: StateFlow<GalleryPreloadMode> get() = prefs.galleryPreload
    override val galleryAnimated: StateFlow<GalleryAnimatedMode> get() = prefs.galleryAnimated
    override val galleryBackdrop: StateFlow<GalleryViewerBackdrop> get() = prefs.galleryBackdrop
    override val galleryBlockAi: StateFlow<Boolean> get() = prefs.galleryBlockAi
    override val galleryAiBadge: StateFlow<Boolean> get() = prefs.galleryAiBadge
    override val galleryHideTopBar: StateFlow<Boolean> get() = prefs.galleryHideTopBar
    override val galleryHideBottomBar: StateFlow<Boolean> get() = prefs.galleryHideBottomBar
}
