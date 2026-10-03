package com.venera.compose.gallery.data

import android.content.Context
import com.venera.compose.data.platform.HttpEngine
import com.venera.compose.data.platform.PathProvider
import com.venera.compose.data.platform.android.AndroidHttpEngine
import com.venera.compose.data.platform.android.AndroidLogger
import com.venera.compose.data.platform.android.AndroidPaths

/**
 * 画廊取数层的 **Android 侧工厂**（十颗 `getInstance(context)`）。
 *
 * ## 为什么是"扩展函数"而不是把 `getInstance(context)` 留在各类里
 *
 * 类体要上桌面编译面，而 `getInstance(context: Context)` 里的 `Context` 就是它上不去的原因。
 * 但这十颗的调用点分布在 `GalleryBusinessApi.kt` 的适配器与客户端彼此之间 —— 改签名就得改那些
 * 文件，而其中一路会牵到 `gallery/ui`（本轮硬约束：Android UI 零改动）。
 *
 * 解法是把 Context 工厂搬成**同包扩展函数**：包名仍是 `com.venera.compose.gallery.data`，
 * 所以同包内 `YandeReClient.getInstance(context)` 这种写法**一字不改**就能解析到这里
 * （同包声明不需要 import）。而本文件所在的 `android/` 子目录被
 * `desktop/build.gradle.kts` 里那颗全局的 android 目录排除挡在桌面编译面外 ——
 * 于是桌面看到的是 `getInstance(engine)`，Android 看到的还是 `getInstance(context)`，
 * 两边都不用改调用点。
 *
 * ⚠️ 新增客户端时，把 Context 工厂写在这里，不要写回类里 —— 写回去就等于把那颗类重新钉死在
 * Android 面，而 `DesktopSharedFaceLedgerTest` 只会抓到"类体里出现了 android import"，
 * 抓不到"Android 工厂没搬出来"（那是形状问题，不是编译问题）。
 *
 * ## 语义对齐点
 *
 * 每颗都照旧是 `@Volatile` + `synchronized` 的单例，且**在成员被真正调用时**才建
 * `VeneraNetworkClient`（`AndroidHttpEngine` 里是 `get()`，见那颗文件的注释）——
 * 与改造前的取用时机逐点相同。
 */

private fun engineFor(context: Context): HttpEngine = AndroidHttpEngine(context.applicationContext)

fun PixivClient.Companion.getInstance(context: Context): PixivClient =
    getInstance(engineFor(context))

fun YandeReClient.Companion.getInstance(context: Context): YandeReClient =
    getInstance(engineFor(context))

fun SafebooruClient.Companion.getInstance(context: Context): SafebooruClient =
    getInstance(engineFor(context))

fun GelbooruClient.Companion.getInstance(context: Context): GelbooruClient =
    getInstance(engineFor(context), GelbooruAccount.getInstance(context))

fun DanbooruArtistClient.Companion.getInstance(context: Context): DanbooruArtistClient =
    getInstance(engineFor(context))

fun GalleryArtistProbeClient.Companion.getInstance(context: Context): GalleryArtistProbeClient =
    getInstance(engineFor(context))

fun GalleryTagCategories.Companion.getInstance(context: Context): GalleryTagCategories =
    getInstance(engineFor(context), AndroidLogger)

fun SauceNaoClient.Companion.getInstance(context: Context): SauceNaoClient =
    getInstance(engineFor(context), SauceNaoAccount.getInstance(context))

// ---------------------------------------------------------------------------
// 三颗磁盘档
// ---------------------------------------------------------------------------

private fun pathsFor(context: Context): PathProvider = AndroidPaths(context.applicationContext)

fun GalleryFavoritesStore.Companion.getInstance(context: Context): GalleryFavoritesStore =
    getInstance(pathsFor(context))

fun GalleryArtistFollowsStore.Companion.getInstance(context: Context): GalleryArtistFollowsStore =
    getInstance(pathsFor(context))

internal fun GalleryArtistAvatarStore.Companion.getInstance(context: Context): GalleryArtistAvatarStore =
    getInstance(pathsFor(context), AndroidLogger)

// 两颗 account 没有搬出来：设置页那三处 UI 直接拿它们的 identity / hasKey 流在渲染，
// 而客户端只需要 apiKey + userId 两枚串 —— 所以客户端认 GelbooruCredentials /
// SauceNaoCredentials 这两颗窄接口，account 留在 Android 面并整颗进桌面 exclude。
//
// 另外两颗缓存（GalleryFeedCache / GalleryForYouCache）同理留 Android 面并 exclude：
// 它们的 read(app)/write(app,…) 签名被 gallery/ui 的 ViewModel 直接拿着，而那半边
// 本轮一个字都不许动。
