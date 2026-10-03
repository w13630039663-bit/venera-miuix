package com.venera.compose.gallery.data

import com.venera.compose.gallery.domain.GalleryAnimatedMode
import com.venera.compose.gallery.domain.GalleryColumnMode
import com.venera.compose.gallery.domain.GalleryPreloadMode
import com.venera.compose.gallery.domain.GalleryPreviewQuality
import com.venera.compose.gallery.domain.GalleryViewerBackdrop
import com.venera.compose.security.guard.GuardRule
import kotlinx.coroutines.flow.StateFlow

/**
 * 画廊侧的**业务 API 容器 + 守卫口 + 偏好口**（适配器在 `android/GalleryPortsAndroid.kt`，
 * 其余十二颗契约与它们的适配器在 `GalleryBusinessApi.kt` / `android/GalleryAdaptersAndroid.kt`）。
 *
 * 为什么不并进漫画侧的 `data/api/BusinessPorts.kt`：画廊与漫画的隔离口径禁的是
 * `feature ↔ gallery` 互引，但更硬的一条是「各侧自持」（`gallery/ui/GalleryFeedReadout.kt:32`
 * 同一条纪律：跨侧相似不许合并，两边各留一份、互相注明必须同串）。让 `gallery/ui` 去 import
 * 漫画侧的业务口，等于把分级模式、屏蔽口径这些**只读共享**的东西重新绑回一个发布者 ——
 * `docs/rounds/gallery-module-isolation-plan-2026-09.md:36-47` 给这三类共用定的档是
 * 「只读共享」，不是「共用一个对象」。
 *
 * 形状照 `data/db/DatabasePorts.kt:60-103`（容器 + `install(platform, factory)` + `of(handle: Any?)`）。
 *
 * ## 这颗文件现在**一句平台类型都没有**
 *
 * `install` 收的是 `platform: String`（只用来拒绝第二个平台重复接线），`of` 收的是不透明句柄
 * `Any?`，而 `Context` 只在接线那半边用 —— 那半边整颗搬进了 `android/GalleryPortsAndroid.kt`，
 * 于是容器 + 契约这半边可以进桌面编译面（`:desktop` 的 srcDir 排除 android 子目录）。
 */
class GalleryPorts(
    val contentGuard: GalleryContentGuard,
    val prefs: GalleryPreferences,
    val favorites: GalleryFavorites,
    val follows: GalleryArtistFollows,
    val avatars: GalleryArtistAvatars,
    val lexicon: GalleryTagLexicon,
    val credentials: GalleryCredentials,
    val stores: GalleryStoreFactory,
    val boards: GalleryBoards,
    val artists: GalleryArtistDirectory,
    val reverse: GalleryReverseSearch,
    val hygiene: GalleryNetworkHygiene,
) {

    companion object {
        @Volatile
        private var factory: ((Any?) -> GalleryPorts)? = null

        @Volatile
        private var installedPlatform: String? = null

        fun install(platform: String, factory: (Any?) -> GalleryPorts) {
            val current = installedPlatform
            if (current == null) {
                installedPlatform = platform
                this.factory = factory
            } else if (current != platform) {
                throw IllegalStateException(
                    "画廊业务 API 已由平台「$current」接线，拒绝平台「$platform」重复安装（静态口不会清零）"
                )
            }
        }

        fun of(handle: Any?): GalleryPorts {
            val f = factory
                ?: throw IllegalStateException(
                    "画廊业务 API 尚未完成平台接线（of 取用时无任何已装 factory）：请在启动处调用 " +
                        "GalleryPorts.install —— Android 侧为 AndroidGalleryPorts.install()。" +
                        "当前收到的句柄：${handle?.javaClass?.name ?: "null"}"
                )
            return f(handle)
        }
    }
}

/**
 * 画廊问内容守卫只需要四件事 —— 而 `ContentGuardManager` 的公开面是 16 个成员。
 *
 * 不收的那些在漫画侧：`filterComicModels`（画廊的数据模型是 `GalleryPost`，不是 `Comic`）、
 * `coverMaskStateFor` 的三个重载（画廊的遮蔽判定链在 `gallery/domain/GalleryGuard.kt`，
 * 纯判据、可 JVM 单测）、`registerSourceNameAliases`（只有装配根 `VeneraApp.kt:46` 用）。
 *
 * ⚠️ [blockedGalleryRule] 的匹配口径**不许在画廊侧另立一份**：图站 tag 是下划线标识符，
 * 子串匹配会让 `ai` 命中 `long_hair`（`ContentGuardManager.kt:285-286` 的注释就是为这条写的，
 * 大图页 `GalleryPostScreen.kt:685` 与墙侧都注明了"不能换一套口径"）。这里交回的是同一个方法。
 */
interface GalleryContentGuard {

    /** 分级遮罩模式 "OFF"/"BLUR"/"HIDE" —— 与漫画侧读的是同一条流（只读共享的档）。 */
    val nsfwMaskMode: StateFlow<String>

    /** 规则总表，画廊只读（写口只有 `addRule`，那是长按标签"屏蔽此标签"那一路）。 */
    val rules: StateFlow<List<GuardRule>>

    /** 命中了哪条用户规则；没命中给 null。 */
    fun blockedGalleryRule(author: String, tags: List<String>): GuardRule?

    /** 写进的是**站点原值**，判成功用 `>= 0`（`FREEZE-STATEMENT.md:122` 定过的口径）。 */
    suspend fun addRule(type: String, pattern: String, isRegex: Boolean = false): Long
}

/**
 * 画廊**显示与播放行为**读的那 12 枚偏好，getter-only。
 *
 * 为什么没有写口：逐颗核过调用点 —— 画廊侧（`gallery/ui` 全部 10 处取用）对这些值**只读**，
 * 15 枚 `setGalleryXxx` 的写口今天全在漫画侧的设置页 `feature/settings/GallerySettings.kt`
 * （它是"给画廊配开关"的界面，不是画廊本身）。把写口收进画廊契约就等于把设置页搬进画廊，
 * 那是 Part B 的 W5（`docs/rounds/business-api-boundary-2026-10.md` §八）要一起做的一件事：
 * W5 落 `gallery/data/GalleryPreferences.kt` 实现类时直接接上这颗接口，本轮的声明就是它的前置。
 *
 * 存储语义不在这里发明：值仍然来自漫画侧那一份 `VeneraPreferences`（只读共享的档，
 * `docs/rounds/gallery-module-isolation-plan-2026-09.md:36-47`），存储名与 15 枚键名逐字不变。
 */
interface GalleryPreferences {

    /** 图片墙列数档位（AUTO / TWO / THREE），与窗口宽一起算成实际列数。 */
    val galleryColumnMode: StateFlow<GalleryColumnMode>

    /** 墙上缩略档取哪一档画质。 */
    val galleryPreviewQuality: StateFlow<GalleryPreviewQuality>

    val galleryKeepScreenOn: StateFlow<Boolean>
    val galleryVolumeKeyTurn: StateFlow<Boolean>

    /** 幻灯片自动翻页秒数；0 = 关。 */
    val galleryAutoPlaySec: StateFlow<Int>

    val galleryPreload: StateFlow<GalleryPreloadMode>
    val galleryAnimated: StateFlow<GalleryAnimatedMode>

    /** 播放器那一层的背景处理方式（与漫画阅读器的夜间柔光是两条独立开关）。 */
    val galleryBackdrop: StateFlow<GalleryViewerBackdrop>

    /** 画廊**自己的** AI 屏蔽开关（批次 C2 拍板"画廊和漫画分开"，不是复用 blockAiComics）。 */
    val galleryBlockAi: StateFlow<Boolean>

    /** 是否在卡片上标 AI 角标。 */
    val galleryAiBadge: StateFlow<Boolean>

    val galleryHideTopBar: StateFlow<Boolean>
    val galleryHideBottomBar: StateFlow<Boolean>
}
