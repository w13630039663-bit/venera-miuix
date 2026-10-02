package com.venera.compose.gallery.data

import android.content.Context
import com.venera.compose.security.guard.ContentGuardManager
import com.venera.compose.security.guard.GuardRule
import kotlinx.coroutines.flow.StateFlow

/**
 * 画廊侧的**业务 API**：契约与适配同处一颗文件。
 *
 * 为什么不并进漫画侧的 `data/api/BusinessPorts.kt`：画廊与漫画的隔离口径禁的是
 * `feature ↔ gallery` 互引，但更硬的一条是「各侧自持」（`gallery/ui/GalleryFeedReadout.kt:32`
 * 同一条纪律：跨侧相似不许合并，两边各留一份、互相注明必须同串）。让 `gallery/ui` 去 import
 * 漫画侧的业务口，等于把分级模式、屏蔽口径这些**只读共享**的东西重新绑回一个发布者 ——
 * `docs/rounds/gallery-module-isolation-plan-2026-09.md:36-47` 给这三类共用定的档是
 * 「只读共享」，不是「共用一个对象」。
 *
 * 形状照 `data/db/DatabasePorts.kt:60-103`（容器 + `install(platform, factory)` + `of(handle: Any?)`），
 * 所以这颗文件里出现的只有 `Context` 一句平台类型，与同目录其余颗一样。
 */
class GalleryPorts(val contentGuard: GalleryContentGuard) {

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
                    "画廊业务 API 尚未完成平台接线：请在启动处调用 AndroidGalleryPorts.install()。" +
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

/** Android 接线。⚠️ 构造不调 `getInstance`，只在成员方法体里现取 —— 取用时机与改造前逐点相同。 */
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
            GalleryPorts(contentGuard = AndroidGalleryContentGuard(context)).also { ports = it }
        }
    }
}

private class AndroidGalleryContentGuard(private val context: Context) : GalleryContentGuard {

    private val manager: ContentGuardManager get() = ContentGuardManager.getInstance(context)

    override val nsfwMaskMode: StateFlow<String> get() = manager.nsfwMaskMode

    override val rules: StateFlow<List<GuardRule>> get() = manager.rules

    override fun blockedGalleryRule(author: String, tags: List<String>): GuardRule? =
        manager.findGalleryBlockedRule(author = author, tags = tags)

    override suspend fun addRule(type: String, pattern: String, isRegex: Boolean): Long =
        manager.addRule(type, pattern, isRegex)
}
