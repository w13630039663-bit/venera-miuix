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
import java.io.File
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
 *
 * ## 第一百一十六批：本机那一棵树排在引擎**前面**
 *
 * 批 96 把整话写下盘，批 97 数得着，批 98 删得掉，批 111/113 补得齐，批 115 把那些接上了屏 ——
 * 而**翻开那一屏一页都不吃盘**（`fetch()` 里只有 `engine.pageImage`）。用户攒了 24 MB 在机器上，
 * 断网还是翻不开，联网还得把同样的字节再取一遍。这一格补的就是那一句。
 *
 * ⚠️ 判「这一页在不在盘上」吃的仍是 `desktopComicPageLooksWhole`（第一百一十批那把**唯一**的尺子），
 *    不在这里另写第四种"齐不齐"。代价是那一页读两次（一次前 12 字节、一次整颗）；
 *    换来的是「续跑跳过它 / 屏上念缺 N 页 / 翻开吃它」三件事同一把口径 ——
 *    这里自己判头部的话，坏页那一档就会在屏上念「缺 2 页」而翻开时当成有。
 *
 * ⚠️ 缓存键**不掺** `localDir`：盘上那一颗字节就是当初从源站取回的那一颗，同一内容必须是同一个键。
 *    掺了就是"离线读过一次，联网回来又整话重取一遍"。由 `DesktopComicOfflineReadingTest` 钉。
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
 *
 * [localDir] 是**第一百一十六批**那一格：这一话在盘上的目录（`…/<话目录>`），`null` = 本机没有这一话。
 * 非空时那一页只要盘上算「一整页」就直接吃盘而不问引擎；缺的那几页仍回落到 [engine]。
 * 页序 [page] 就是盘上那一档页名的索引（`0001.jpg` 从 1 数起而库里从 0 数起，两边都 0 基）。
 */
internal data class DesktopComicPage(
    val sourceKey: String,
    val comicId: String,
    val episodeId: String,
    val imageKey: String,
    val page: Int,
    val engine: EngineSession?,
    /** 这一话在本机那棵树里的目录；`null` = 本机没有 ⇒ 只能问源站。 */
    val localDir: File? = null,
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

/**
 * 这一格能不能真取到字节 —— 引擎与本机**两路都没有**才不取。
 *
 * 第一百一十六批把"盘上有那一话"算作一条能取的路（断网时那一话仍翻得开）。
 * ⚠️ 旧那句「引擎没装载就不取」的判据没有被削弱：两枚都缺的页仍然不造 fetcher
 *    （屏上念"这一页没取成"，而不是转一下再红得像网络坏了）。
 */
internal fun desktopComicPageCanFetch(page: DesktopComicPage): Boolean =
    page.engine != null || page.localDir != null

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

/**
 * 把字节包成 Coil 的 `SourceFetchResult`（形状照 `coil3/fetch/ByteArrayFetcher.kt` 那一件）。
 *
 * `dataSource` 是**必传**而不是默认 `NETWORK`：那一格会被 Coil 写进 `Image.result.dataSource`，
 * 而"从盘上读的"若报成 NETWORK，屏上看不出来而日志会说谎（本仓那一族「读数说成另一路」）。
 */
internal fun desktopComicPageResult(
    bytes: ByteArray,
    options: Options,
    dataSource: DataSource,
): SourceFetchResult = SourceFetchResult(
    source = ImageSource(source = Buffer().apply { write(bytes) }, fileSystem = options.fileSystem),
    mimeType = null,
    dataSource = dataSource,
)

/**
 * 从本机那一棵目录里取第 [index] 页的字节；**不该吃盘**或读不出时交回 `null`（调用处回落到引擎）。
 *
 * ## 为什么单独抽出来（而不是写在 `fetch()` 里）
 *
 * `fetch()` 要吃 Coil 的 `Options`，无头够不到；而"这一页到底吃没吃盘"正是第一百一十六批要钉的那一格。
 * 抽成这颗之后它可纯跑（真临时目录 + 真字节），壳里只剩「有字节就交回 DISK，没有就问引擎」。
 *
 * ## 读两次是刻意的代价
 *
 * 先走那颗共用的判式（只读前 12 字节），过了才把整颗读进来。在这里自己判头部 = 第四种"齐不齐"，
 * 而第一百一十批刚把那种漂移收成一颗 —— 各判各的坏页就会念成「屏上说缺 2 页而翻开时当成有」。
 *
 * ⚠️ 判式过了而整颗读不出（正被补下载换掉之类）交回 `null` 而**不是空表**：
 *    谎称"从盘上读到了空"会让那一页变白图，而真相是"这一页该去问源站"。
 */
internal fun desktopComicLocalPageBytes(dir: File, index: Int): ByteArray? {
    if (!desktopComicPageLooksWhole(dir, index)) return null
    val bytes = runCatching { File(dir, desktopComicChapterPageFileName(index)).readBytes() }.getOrNull()
    return if (bytes != null && bytes.isNotEmpty()) bytes else null
}

internal class DesktopComicPageFetcher(
    private val page: DesktopComicPage,
    private val options: Options,
) : Fetcher {

    override suspend fun fetch(): FetchResult? {
        // ── 本机排在引擎前面（第一百一十六批）──
        page.localDir?.let { dir ->
            val local = desktopComicLocalPageBytes(dir, page.page)
            if (local != null) return desktopComicPageResult(local, options, DataSource.DISK)
        }
        val engine = page.engine ?: return null
        // 两跳都在锁里：`pageImage` 里既有 JS 求值也有取字节，而那颗 Context 单线程。
        // `withContext(IO)` 是因为它是阻塞调用，不能让 Coil 的 fetch 调度器替我们排队。
        val bytes = withDesktopEngineLock(page.lockKey) {
            withContext(Dispatchers.IO) { engine.pageImage(page.imageKey, page.comicId, page.episodeId) }
        }
        return desktopComicPageResult(bytes, options, DataSource.NETWORK)
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
