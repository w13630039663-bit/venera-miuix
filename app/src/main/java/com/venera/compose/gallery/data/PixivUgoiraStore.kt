package com.venera.compose.gallery.data

import com.venera.compose.data.platform.HttpEngine
import com.venera.compose.data.platform.PathProvider
import java.io.File
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.ZipFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * 一整个解好包的 ugoira：**帧表 + 帧文件所在的那个目录**（2026-10-23，动图播放）。
 *
 * [dir] 里放的是站方 zip 原样解出来的那些帧（`000000.jpg`…），文件名与
 * [PixivUgoiraFrame.file] 逐字相等 —— 翻帧就是按表读文件，不再拼任何名字。
 * [original] 为 true = 解的是 `originalSrc` 那一档（HD），false = `src` 小档。
 */
data class PixivUgoiraClip(
    val ugoira: PixivUgoira,
    val dir: File,
    val original: Boolean,
)

/**
 * ugoira 逐帧 zip 的**取回、解包与目录账**（2026-10-23）。
 *
 * ## 为什么这条腿必须存在（立案读数）
 *
 * 用户 2026-10-23：「目前 app pixiv 源的动图无法播放」。逐处查过：
 * - 作品页遇到 ugoira 只摆第一帧静图 + 一句「这一屏只画得出第一帧」（`PixivWorkScreen`）；
 * - 「去看图器放」那颗出口交的仍是站方的 `urls.original` —— 对 ugoira 那是
 *   `…_ugoira0.jpg`，**一张静帧 JPEG**（现量真号 151160372）；
 * - 全仓没有任何一处打过 `/ajax/illust/{id}/ugoira_meta`，也没有任何一处解过那个 zip。
 *
 * 所以动图不是"坏了"，是**这条腿压根没建**：Coil 那颗 [GalleryAnimationGate] 只管
 * GIF / 动图 WebP 的字节，而 pixiv 的动图是"一堆 JPEG + 每帧延时"，任何解码器都不可能
 * 把它当动图放出来。这一颗就是那条腿的落地侧（取表在 `PixivClient.ugoiraMeta`）。
 *
 * ## 为什么落盘而不是只进内存
 *
 * 动图一开就是十几到几十帧的位图（小档 600×600 一帧解码后约 1.4 MB，17 帧就是 24 MB），
 * 全塞内存缓存会重演 2026-09-29 那条 OOM 读数（见 [GalleryAnimationGate] 的头注）。
 * 而 zip 的**字节**本来就已经躺在画廊那份 Coil 磁盘缓存里（`src` 也是 http 地址）——
 * 解包落盘只多占那一包解压后的量，翻回来不必重新下，代价可控。
 *
 * ## 三条判据
 *
 * 1. **落点是 `cacheRoot`**（`cacheDir/ugoira/`）：整目录可被系统回收，与画廊图片缓存同档；
 *    不写进 `dataRoot` —— 那是用户数据，动图是可重下的临时件。
 * 2. **一档一目录**（`{id}_s` / `{id}_o`）：小档与 HD 的帧文件同名（`000000.jpg`），
 *    混在一个目录里就是"开了 HD 画的还是小档"那种静默错图。
 * 3. **半包不算有货**：解包先写 `unzipping` 旗标、写完才落 `ok` 旗标 ——
 *    上次崩在中途留下的半堆帧，没有 `ok` 旗标就整目录重来，而不是画到第 7 帧读不到文件。
 */
class PixivUgoiraStore private constructor(
    private val engine: HttpEngine,
    private val paths: PathProvider,
) {

    /**
     * 同一作品档**串行化**：一次翻帧会话可能同时有"取元信息 + 解包"与"HD 那一档"两笔，
     * 而两笔都会往同一目录写。锁按目录键分，不给整颗 store 上大锁。
     */
    private val locks = ConcurrentHashMap<String, Mutex>()

    /** 解包目录（`cacheRoot/ugoira/{id}_{s|o}`）。不存在就建。 */
    private fun dirFor(illustId: Long, original: Boolean): File =
        File(File(paths.cacheRoot, DIR_NAME), "${illustId}_${if (original) 'o' else 's'}").apply {
            if (!mkdirs() && !isDirectory) throw IOException("建不出动图缓存目录 $absolutePath")
        }

    private fun lockFor(dir: File): Mutex = locks.getOrPut(dir.absolutePath) { Mutex() }

    /**
     * 下 zip → 逐帧落盘，交回解好的那一档。
     *
     * 已经解好的直接回目录（不重下、不重解）。zip 里认不出的成员（非图片、目录项）跳过，
     * 但**表里点名的那一帧缺文件就整次失败** —— 少一帧的动画播到那里就是卡住，
     * 那与"这一作没法播"是同一档表现，宁可整次失败让屏上退回静帧。
     *
     * @param meta 已由调用方取回的帧表（`PixivClient.ugoiraMeta`）——
     *   这一颗不自己打元信息那一跳，"要不要为动图多问一次站方"归取数侧判。
     */
    suspend fun clip(illustId: Long, meta: PixivUgoira, original: Boolean): Result<PixivUgoiraClip> =
        withContext(Dispatchers.IO) {
            runCatching {
                val dir = dirFor(illustId, original)
                val zipUrl = if (original) meta.originalSrc else meta.src
                lockFor(dir).withLock {
                    if (!isReady(dir, meta, original)) {
                        unzipInto(zipUrl, dir, meta, original)
                    }
                    PixivUgoiraClip(ugoira = meta, dir = dir, original = original)
                }
            }
        }

    /** 这一目录是不是**已经按这一份表**齐了（旗标 + 表点名的每一帧都在）。 */
    private fun isReady(dir: File, meta: PixivUgoira, original: Boolean): Boolean {
        if (!File(dir, OK_FLAG).isFile) return false
        // 档别记在旗标里：拿小档的货去顶 HD 那一档，屏上会读成"HD 更糊"。
        if (File(dir, OK_FLAG).readText() != tierKey(original)) return false
        return meta.frames.all { File(dir, it.file).isFile }
    }

    /**
     * 下一包、解一次。写盘顺序：清旧 → 解 → 落旗标（见类头那条"半包不算有货"）。
     */
    private fun unzipInto(zipUrl: String, dir: File, meta: PixivUgoira, original: Boolean) {
        val bytes = fetch(zipUrl)
        dir.listFiles()?.forEach { it.delete() }
        File(dir, OK_FLAG).delete()
        val tmp = File.createTempFile("ugoira", ".zip", dir.parentFile ?: dir)
        try {
            tmp.outputStream().use { it.write(bytes) }
            val seen = HashSet<String>()
            ZipFile(tmp).use { zip ->
                for (frame in meta.frames) {
                    val entry = zip.getEntry(frame.file) ?: continue
                    if (entry.isDirectory) continue
                    zip.getInputStream(entry).use { input ->
                        File(dir, frame.file).outputStream().use { output -> input.copyTo(output) }
                    }
                    seen += frame.file
                }
            }
            // 表里点名却没在包里出现的帧 ⇒ 抛（宁可整次失败，不交一份播到半路卡死的包）。
            val missing = meta.frames.filterNot { it.file in seen }
            if (missing.isNotEmpty()) {
                throw IOException("动图包少了 ${missing.size} 帧（${missing.first().file} 起）")
            }
            File(dir, OK_FLAG).writeText(tierKey(original))
        } finally {
            tmp.delete()
        }
    }

    /**
     * 取 zip 的字节。**必须走 [HttpEngine] 那颗共享 client**：`i.pximg.net` 不带站内
     * `Referer` 是 403（实测，口径记在 `ImageHeaderPolicy` 的 pximg 那一行），
     * 而那条拦截器就挂在共享 client 的链上 —— 自己 new 一颗 OkHttpClient 就是拿 403 当"这作没动画"。
     */
    private fun fetch(url: String): ByteArray {
        val attempts = buildList {
            add(url)
            if (url.contains("i.pximg.net")) {
                add(url.replace("i.pximg.net", "i.pixiv.re"))
            }
        }.distinct()

        var lastError: Exception? = null
        for (targetUrl in attempts) {
            try {
                val request = okhttp3.Request.Builder()
                    .url(targetUrl)
                    .header("Referer", "https://www.pixiv.net/")
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .header("Accept", "*/*")
                    .build()
                engine.okHttpClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val bytes = response.body?.bytes()
                        if (bytes != null && bytes.isNotEmpty()) return bytes
                    }
                }
            } catch (e: Exception) {
                lastError = e
            }
        }
        throw lastError ?: IOException("动图包下载失败: $url")
    }

    companion object {
        /** `cacheDir` 下那个目录名（与画廊图片缓存分开：清图不该顺手清掉解好的帧，反之也一样）。 */
        const val DIR_NAME = "ugoira"

        private const val OK_FLAG = ".ok"

        private fun tierKey(original: Boolean) = if (original) "original" else "small"

        @Volatile
        private var INSTANCE: PixivUgoiraStore? = null

        /** 只收平台口，不收 `Context`（口径同 [PixivClient]：类体要留在桌面编译面里）。 */
        fun getInstance(engine: HttpEngine, paths: PathProvider): PixivUgoiraStore =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: PixivUgoiraStore(engine, paths).also { INSTANCE = it }
            }
    }
}
