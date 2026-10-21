package com.venera.desktop

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import com.venera.compose.components.LocalUxPlatformServices
import com.venera.compose.components.NoOpUxPlatformServices
import com.venera.compose.components.UxPlatformServices
import com.venera.compose.gallery.domain.GalleryGuard

/**
 * 桌面侧的 [UxPlatformServices]。
 *
 * ## 为什么必须提供它（2026-10-21 验收发现 R3）
 *
 * `LocalUxPlatformServices` 是 `staticCompositionLocalOf { NoOpUxPlatformServices }`，
 * 而桌面此前**从未 provide** ⇒ 一直吃 `NoOp`，它的 `coverMaskStateOf(...)` **恒返回 `"VISIBLE"`**。
 *
 * 后果：共享零件 `RecommendCarouselItem` / `HistoryCardHome` 里那句
 * `maskState == "VISIBLE"` 恒真 ⇒ **所有封面不打码**，且 `coverSharedElement(allowFly = …)`
 * 会允许打码命中项也做共享元素飞行（安卓侧明确禁止这种行为）。
 *
 * 这是一条**内容安全**缺口，不是样式问题。
 *
 * ## 判据从哪来（不自己发明一套）
 *
 * 桌面画廊墙已在用 `GalleryGuard.maskStateFor`（`gallery/domain/GalleryGuard.kt`，
 * 零平台耦合，已在 `:desktop` 编译面）。它的三档字面值与漫画侧
 * `ContentGuardManager.coverMaskStateFor` **是同一套** `VISIBLE`/`BLURRED`/`HIDDEN`
 * —— 那颗文件的类注释第 13-14 行明写了这件事。
 *
 * 所以这里搬**判据形状**，不另立门户：黑名单 > 成人标记 > 模式档。
 *
 * ## 与安卓的差异（如实记，不掩盖）
 *
 * 安卓 `ContentGuardManager` 的判定链更长：还查**源级预设表**（`sourcePresets`，按
 * `resolveSourceKey` 归一别名）、**显式标记正则**（R18 词表，来自 assets 的
 * `source_content_warning.json`）、以及 **AI 标记**（`isAiMarked`）。三者都要 `Context`。
 *
 * 也就是说：**桌面今天只判「用户黑名单 + 条目自带成人标签」，比安卓松。**
 * 这不是"已对齐"，是"已接通、口径更窄"。补齐得先把那几件数据源搬到桌面。
 */
internal class DesktopUxPlatformServices(
    /** 分级档当前值。桌面已有 `DesktopGalleryContentGuard.nsfwMaskMode` 这条流。 */
    private val maskMode: () -> String,
    /** 用户黑名单判定：命中就给 `HIDDEN`。桌面已有 `DesktopGalleryContentGuard.rules`。 */
    private val blockedByUser: (sourceKey: String, title: String, author: String, tags: List<String>) -> Boolean,
) : UxPlatformServices by NoOpUxPlatformServices {

    /**
     * 与 `ContentGuardManager.coverMaskStateFor(sourceKey, …)` 同一套三档字面值。
     *
     * 复用 `GalleryGuard` 的**判据形状**：黑名单 > 成人标记 > 模式（`GalleryGuard.kt:29-35`）。
     * `GalleryGuard.maskStateFor` 吃 `GalleryPost`，漫画侧只有字段，所以照它的
     * `when (mode)` 写一遍 —— 档位字面值与早退顺序逐字相同，不另立一套。
     */
    override fun coverMaskStateOf(
        sourceKey: String,
        title: String,
        author: String,
        tags: List<String>,
        comicId: String,
        description: String,
    ): String {
        if (blockedByUser(sourceKey, title, author, tags)) return GalleryGuard.HIDDEN
        if (!looksAdult(sourceKey, tags)) return GalleryGuard.VISIBLE
        return when (maskMode()) {
            "OFF" -> GalleryGuard.VISIBLE
            "HIDE" -> GalleryGuard.HIDDEN
            else -> GalleryGuard.BLURRED
        }
    }

    /**
     * 「这条看起来是不是成人向」。**这是比安卓窄的替代判据**，如实写明。
     *
     * 安卓走 `hasExplicitMark(...)` 那套预编译正则（R18 词表，来自 assets 的
     * `source_content_warning.json`，要 `Context`）。桌面拿不到那份词表，所以这里
     * 只判**标签里明确的成人标记** —— 宁可漏判，也不假装有那份词表。
     *
     * 为什么"宁可漏判"要写出来：漏判会让本该遮住的漏出来，是**安全方向**的欠账；
     * 而编一张词表又会让主人以为"和安卓一样"了 —— 后者更危险，因为它把这条缺口
     * 从"已知未做完"变成"看起来做完了"。
     */
    /**
     * 「这条要不要按成人向处理」。
     *
     * ## 两道判据，缺一不可（2026-10-21 安全评审 C）
     *
     * **第一道：源级预设表**（[DesktopSourceContentPresets]）。
     * 安卓 `ContentGuardManager.maskHit:441-443` 就是这道：`nsfw`/`mixed` ⇒ 命中。
     *
     * 此前桌面**没有这道**，而历史记录调 `coverMaskStateOf` 时**不传 tags**
     * （`HomeHomeWidgets.kt:165` 只传 sourceKey/title/author/comicId）⇒
     * 只看 tags 就永远判不出成人向 ⇒ **历史卡封面恒裸**。
     * 这不是「判据更宽」，是**少了一整道**。
     *
     * **第二道：标签里明确的成人标记**。比安卓的 `hasExplicitMark(...)`
     * （R18 词表正则）窄 —— 那份词表要 `Context`，桌面尚未移植。
     * 所以这一道是**补充**，不是替代。宁漏勿假：漏判是安全方向的欠账（已记账），
     * 而编一张假词表会让主人以为「和安卓一样了」。
     */
    private fun looksAdult(sourceKey: String, tags: List<String>): Boolean =
        DesktopSourceContentPresets.isGuarded(sourceKey) || tags.any { it.isAdultTag() }

    private fun String.isAdultTag(): Boolean = lowercase() in ADULT_TAGS

    private companion object {
        /** 只收**明确**的成人标签。不收含糊词（`mature` 之类在部分站点是非成人分区名）。 */
        val ADULT_TAGS = setOf(
            "r-18", "r18", "r18g", "adult", "nsfw", "explicit", "hentai", "smut", "lewd",
        )
    }
}

/** 把桌面实现装进作用域。**缺了它，共享零件里的内容守卫就恒 `VISIBLE`。** */
@Composable
internal fun ProvideDesktopUxPlatformServices(
    maskMode: () -> String,
    blockedByUser: (String, String, String, List<String>) -> Boolean,
    content: @Composable () -> Unit,
) {
    val services = remember(maskMode, blockedByUser) {
        DesktopUxPlatformServices(maskMode, blockedByUser)
    }
    CompositionLocalProvider(LocalUxPlatformServices provides services, content = content)
}
