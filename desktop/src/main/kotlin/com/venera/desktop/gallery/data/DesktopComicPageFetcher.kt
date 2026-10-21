package com.venera.desktop.gallery.data

import coil3.ImageLoader
import coil3.decode.DataSource
import coil3.decode.ImageSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.SourceFetchResult
import coil3.key.Keyer
import coil3.request.Options
import com.venera.engineprobe.EngineSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okio.Buffer
import java.util.concurrent.ConcurrentHashMap

/**
 * 批 81 · 阅读那一屏的**一页**：模型 + Coil 取数件 + 引擎锁。
 *
 * ## 为什么这一屏要新造一枚 fetcher（而不是走网络那一条）
 *
 * 漫画的页不是"一个能直接GET的地址"：源脚本的 `onImageLoad` 会给出地址、头，并且 jm 系那一类
 * 还要**切块还原**（`EngineSession.pageImage` 里读 `const num` 后重排）。混淆块数的权威在引擎侧
 * （逐字照 `jm.js`），桌面**一个字都不重述** —— 所以取字节这一跳只能交回引擎，而交回引擎的形状
 * 就是一枚自定义 fetcher。
 *
 * ## 为什么必须同时注册 Keyer
 *
 * 这是量出来的，不是推测：`ComponentRegistry.key()` 在**没有**匹配的 Keyer 时直接返回 `null`
 * （`coil3/ComponentRegistry.kt:62-70`），而 `null` 的语义是"这一格不进内存缓存"。
 * 也就是说只挂 fetcher 不挂 Keyer，每次滚回上一页都会重新问一遍源站并重新解混淆，
 * 而屏上看不出任何异常 —— 正是本仓最忌的"实现了但那是假开关"。
 *
 * ## 缓存键与锁键为什么是两枚（这是本批最容易写错的一格）
 *
 * 引擎句柄 `EngineSession` 没有 `equals`，默认按对象身份。把身份掺进**缓存键** ⇒ 重取一次引擎
 * 整话缓存全废；不掺进**锁键** ⇒ 两颗 Context 抢同一把锁。所以：缓存键只由那四枚字符串算出，
 * 锁键只由引擎身份算出，两枚互不相干（三条判据分别钉这两向）。
 *
 * ## 为什么锁是"每颗引擎一把"而不是全局一条
 *
 * `DesktopJsHost` 零同步（`evaluateAsync` 是发出去 + `Thread.sleep(2)` 轮询，全文件没有一枚
 * `synchronized`/`Mutex`），而 Coil 会在自己的 fetch 调度器上**并发**跑多枚 fetcher。两枚同时进
 * 一颗 Context 就是批 80 那种 `Context execution was cancelled` 的姊妹形，且只在滚得快时出现。
 * 全局一条锁会把"两本漫画的页"排成一条队；按引擎分一把只挡住真正共享 Context 的那些。
 */
internal const val DESKTOP_COMIC_PAGE_SCHEME: String = "comic"

/**
 * 一页的缓存键：源 + 漫画号 + 话号 + 页键。
 *
 * 不含页序是**有意的**：`imageKey` 在一话内就是那一页自己的地址键，页序只是列表位置。
 */
internal fun desktopComicPageCacheKey(
    sourceKey: String,
    comicId: String,
    episodeId: String,
    imageKey: String,
): String = "$DESKTOP_COMIC_PAGE_SCHEME|$sourceKey|$comicId|$episodeId|$imageKey"

/**
 * 阅读屏交给 `AsyncImage` 的那枚模型。
 *
 * ⚠️ [engine] 可以为一枚都没装载引擎的槽留 `null`：那种页**不取**（见 [desktopComicPageCanFetch]），
 * 让 Coil 把它走成错误态，屏上念"这一页没取成"。硬造一颗 fetcher 再在 fetch 里抛，看起来像网络坏了。
 */
internal data class DesktopComicPage(
    val sourceKey: String,
    val comicId: String,
    val episodeId: String,
    val imageKey: String,
    val page: Int,
    val engine: EngineSession?,
) {
    val cacheKey: String = desktopComicPageCacheKey(sourceKey, comicId, episodeId, imageKey)

    val lockKey: String get() = desktopEngineLockKey(engine)
}

/**
 * 锁键的唯一公式（2026-10-21 从 `lockKey` 里抽出来）。
 *
 * 抽出来而不是在第二处重写：锁键公式一旦有两份就会漂，
 * 漂成的样子正是「取页上了锁、取章节没上锁」—— 那等于没有锁，
 * 而且只在并发时才露出来。
 *
 * 锁的粒度 = 一颗引擎。身份哈希在这里是**对的**（它挡的正是同一颗 Context）。
 */
internal fun desktopEngineLockKey(engine: EngineSession?): String =
    "engine-${engine?.let { System.identityHashCode(it) } ?: "none"}"

/** 这一格能不能真取到字节 —— 引擎没装载就不取。 */
internal fun desktopComicPageCanFetch(page: DesktopComicPage): Boolean = page.engine != null

private val engineLocks = ConcurrentHashMap<String, Mutex>()

/**
 * 按锁键拿到那一把（同键同一把，不同键各拿一把）。
 *
 * 用 `computeIfAbsent` 而不是 `getOrPut`：后者在并发下能让两个人各造一把，那时"锁"等于没有。
 * 表里每颗引擎留一把 Mutex（几十字节），引擎本身在退场时被关掉，所以这一格不构成泄漏面。
 */
internal fun desktopEngineMutex(lockKey: String): Mutex = engineLocks.computeIfAbsent(lockKey) { Mutex() }

internal suspend fun <R> withDesktopEngineLock(lockKey: String, block: suspend () -> R): R =
    desktopEngineMutex(lockKey).withLock { block() }

/** 把字节包成 Coil 的 `SourceFetchResult`（形状照 `coil3/fetch/ByteArrayFetcher.kt` 那一件）。 */
internal fun desktopComicPageResult(bytes: ByteArray, options: Options): SourceFetchResult = SourceFetchResult(
    source = ImageSource(source = Buffer().apply { write(bytes) }, fileSystem = options.fileSystem),
    mimeType = null,
    dataSource = DataSource.NETWORK,
)

internal class DesktopComicPageFetcher(
    private val page: DesktopComicPage,
    private val options: Options,
) : Fetcher {

    override suspend fun fetch(): FetchResult? {
        val engine = page.engine ?: return null
        // 两跳都在锁里：`pageImage` 里既有 JS 求值也有取字节，而那颗 Context 单线程。
        // `withContext(IO)` 是因为它是阻塞调用，不能让 Coil 的 fetch 调度器替我们排队。
        val bytes = withDesktopEngineLock(page.lockKey) {
            withContext(Dispatchers.IO) { engine.pageImage(page.imageKey, page.comicId, page.episodeId) }
        }
        return desktopComicPageResult(bytes, options)
    }

    class Factory : Fetcher.Factory<DesktopComicPage> {
        override fun create(data: DesktopComicPage, options: Options, imageLoader: ImageLoader): Fetcher? {
            if (!desktopComicPageCanFetch(data)) return null
            return DesktopComicPageFetcher(data, options)
        }
    }
}

/** 缓存键的那一枚提供者（少了它这一屏每次滚回来都重新解混淆，而屏上看不出来）。 */
internal object DesktopComicPageKeyer : Keyer<DesktopComicPage> {
    override fun key(data: DesktopComicPage, options: Options): String = data.cacheKey
}
