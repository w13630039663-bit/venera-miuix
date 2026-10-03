package com.venera.compose.gallery.ui

import android.app.Application
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.venera.compose.gallery.data.GalleryPorts
import com.venera.compose.gallery.data.GallerySite
import com.venera.compose.gallery.data.SauceNaoException
import com.venera.compose.gallery.data.SauceNaoHit
import com.venera.compose.gallery.data.SauceNaoPage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.withContext

/** 用户从本机挑中的那一张。只留定位信息，**字节在提交那一刻才读**。 */
data class PickedImage(
    val uri: Uri,
    val name: String,
    val mimeType: String,
    /** 拿不到就 null（部分 provider 不报 SIZE）—— 那时不拦，让请求自己去撞。 */
    val sizeBytes: Long? = null,
)

/**
 * 「以图搜图」这一层的宿主状态。
 *
 * ## 为什么不塞进 [GallerySearchViewModel]
 *
 * 那边管的是"标签条件 → 站方搜索"这一条链：`filters / term / suggestions / history / page`。
 * 反搜的输入是**一张图**，没有标签、没有补全、没有翻页，两件事只共用"结果摆在墙那一片"。
 * 混进去的代价是搜索那一侧的每一处判定都要多问一句"现在是不是反搜"，
 * 而它自己那套 `collapseToResults` / `restoreHistoryIfNeeded` 会对着一个根本不存在的
 * 查询串做动作。与日榜 / 搜索分两个 ViewModel 是同一条理由（见 [GallerySearchViewModel] 头注）。
 *
 * ## 为什么住 ViewModel 而不是 `rememberSaveable`
 *
 * 结果要活过"点进大图页再返回"。导航条目是重建的（那条根因见记忆「导航条目会重建组合」），
 * 存组合里的话返回那一刻整屏结果会闪没。
 */
class GalleryReverseViewModel(application: Application) : AndroidViewModel(application) {

    private val app: Context = application
    private val client = GalleryPorts.of(application).reverse
    private val accountHasKey = GalleryPorts.of(application).credentials.sauceNaoHasKey

    /** 这一层开没开。开着 = 搜索卡换成反搜输入形态、墙那一片换成结果。 */
    var open by mutableStateOf(false)
        private set

    /** 图片链接。与 [picked] 互斥，谁后填谁作数（见 [setUrl] / [pick]）。 */
    var url by mutableStateOf("")
        private set

    var picked by mutableStateOf<PickedImage?>(null)
        private set

    var hits by mutableStateOf<List<SauceNaoHit>>(emptyList())
        private set

    var isSearching by mutableStateOf(false)
        private set

    /** 出错原因。与 [noResults] 是两件事，绝不能合成一条"没有结果"。 */
    var error by mutableStateOf<String?>(null)
        private set

    /** 站方正常回答"没有相似图" —— 这是一个答案，不是故障。 */
    var noResults by mutableStateOf(false)
        private set

    /** 输入端的小提醒（没填东西之类）。只说不发请求的理由，不报错误。 */
    var notice by mutableStateOf<String?>(null)

    /** 配没配 key —— UI 直接吃这个 StateFlow，设置页存完立刻跟着变。 */
    val hasKey = accountHasKey

    /**
     * 每成功落地一轮就 +1。
     *
     * 页面用它当"该滚回顶部"的信号。用 `hits.size` 不行：两轮条数一样就观察不到变化，
     * 用户会落在上一轮滚到的深处 —— 与搜索那侧 `searchGeneration` 同一条理由。
     */
    var generation by mutableIntStateOf(0)
        private set

    private var job: Job? = null

    fun openLayer() {
        open = true
    }

    /** 关掉这一层。输入与结果**都不清**：再点进来还是刚才那张图、刚才那批结果。 */
    fun closeLayer() {
        open = false
    }

    fun onUrlChange(next: String) {
        url = next
        if (next.isNotEmpty()) picked = null
        notice = null
    }

    /** 从本机挑一张。读文件名与体积都要过 ContentResolver，所以放 IO。 */
    fun pick(uri: Uri) {
        viewModelScope.launch {
            val meta = withContext(Dispatchers.IO) { describe(uri) }
            // 整张图要**原样**进内存再上传（降采样会改变特征，相似度就不是那个相似度了），
            // 所以这里必须有一道闸：挑到一张几十 MB 的原图时，宁可当场说一句太大，
            // 也不要把 30MB 塞进 heap 然后在一笔要等几秒的请求里 OOM。
            if (meta.sizeBytes != null && meta.sizeBytes > MAX_UPLOAD_BYTES) {
                notice = "这张图 ${meta.sizeBytes / (1024 * 1024)} MB，超过 ${MAX_UPLOAD_BYTES / (1024 * 1024)} MB 不发"
                return@launch
            }
            picked = meta
            url = ""
            notice = null
        }
    }

    fun clearInput() {
        url = ""
        picked = null
        notice = null
    }

    /**
     * 发一笔反搜。
     *
     * @param allowNsfw 由调用方给**与那面墙同一把**的分级判据（`nsfwMaskMode == "OFF"`）。
     *   不在这里读守卫：这一层不该新造第二个 NSFW 开关的读取点。
     *
     * 重入时**掐掉上一笔**而不是并两笔：反搜一笔要几秒，连点两次让后到的那笔覆盖先到的
     * 那批结果是错的读数。
     */
    fun submit(allowNsfw: Boolean) {
        val target = url.trim()
        val file = picked
        if (target.isEmpty() && file == null) {
            notice = "先贴一个图片链接，或者从本机挑一张图"
            return
        }
        // **没配 Key 就不发这一笔**（与 Breadboard 同一条裁决：它没 Key 时直接跳设置页）。
        // 这不是保守，是实测：2026-09-27 真机 + 本机双路复现，匿名请求过盾能成功
        // （`onBypassSuccess`、存下 cf_clearance、重试），**重试仍然 403**，
        // 且日志显示只存下 1 枚 cookie（Cloudflare 正常同时给 cf_clearance 与 __cf_bm）。
        // 也就是这一笔注定白等 90 秒。发出去并让用户盯着转圈，比先说一句"去配 Key"更坏。
        if (!accountHasKey.value) {
            notice = "匿名请求实测过不去 Cloudflare。先去配一枚免费 Key：" +
                "设置 → 漫画源管理 → 「以图搜图（SauceNAO）」"
            return
        }
        job?.cancel()
        error = null
        noResults = false
        isSearching = true
        val seq = ++callSeq
        val done = CompletableDeferred<Result<SauceNaoPage>>()

        // 这笔网络**不挂在 viewModelScope 的那条子协程上**，是刻意的：
        // Cloudflare 过盾内部是 `runBlocking { await() }` 且**没有超时**，用户 Home 掉验证窗口时
        // 那条线程会一直 parked —— 而 parked 的线程到不了任何可取消点，协程超时对它无效。
        // 所以让工作线程自己跑，UI 这一侧只等一个**有上限的** deferred；晚到的结果按 seq 丢弃。
        ioScope.launch {
            val outcome = runCatching {
                if (file != null) {
                    client.searchByFile(readBytes(file.uri), file.name, file.mimeType, allowNsfw)
                } else {
                    client.searchByUrl(target, allowNsfw)
                }
            }
            done.complete(outcome)
        }

        job = viewModelScope.launch {
            val settled = withTimeoutOrNull(WALL_CLOCK_MS) { done.await() }
            if (seq != callSeq) return@launch // 已被更晚的一笔取代，别把它的状态盖掉
            isSearching = false
            if (settled == null) {
                error = "SauceNAO 这一笔没有回来（超过 ${WALL_CLOCK_MS / 1000} 秒）。" +
                    "若刚弹过人机验证窗口且没点完，它会一直挡在这里 —— 再搜一次并先把那一步走完"
                return@launch
            }
            settled.onSuccess { page ->
                hits = page.hits
                noResults = page.hits.isEmpty()
                generation++
            }.onFailure { e ->
                hits = emptyList()
                noResults = false
                // 原因必须原样落上来：反搜失败的形态与"没有相似图"太像，
                // 混成一句会让人去换图而不是去过盾 / 配 Key。
                error = e.message ?: e.javaClass.simpleName
            }
        }
    }

    /** 每发起一笔就 +1；晚到的结果靠它判"还算不算数"。 */
    private var callSeq = 0

    /** 独立于 viewModelScope：过盾挂住时它不归零，但 UI 已经落了确定态。 */
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCleared() {
        ioScope.cancel()
        super.onCleared()
    }

    /** 命中的那条其实就在我们已支持的两站上 —— 直接开我们自己的大图页。 */
    fun openTargets(hit: SauceNaoHit): Pair<GallerySite, Long>? =
        hit.siteRef?.let { it.site to it.id }

    private fun describe(uri: Uri): PickedImage {
        val resolver = app.contentResolver
        var name: String? = null
        var size: Long? = null
        runCatching {
            resolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex >= 0) name = cursor.getString(nameIndex)
                    val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) size = cursor.getLong(sizeIndex)
                }
            }
        }
        val mime = resolver.getType(uri) ?: "application/octet-stream"
        return PickedImage(
            uri = uri,
            name = name ?: uri.lastPathSegment.orEmpty(),
            mimeType = mime,
            sizeBytes = size,
        )
    }

    /**
     * 把挑中的图整个读进内存。
     *
     * 反搜要**整张图**，不能像缩略图那样降采样 —— 降过再发给站方会改变特征，
     * 相似度就不是刚才那个相似度了。所以这里刻意不接 Coil 的解码链。
     * 上限由挑图这一步天然约束（用户挑的是本机的一张图，不是一包）。
     */
    private suspend fun readBytes(uri: Uri): ByteArray = withContext(Dispatchers.IO) {
        app.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw SauceNaoException("读不出那张图，换一张试试")
    }

    companion object {
        /**
         * 挑中的图最大多少字节。
         *
         * 6MB 是**我们自己的闸**，不是站方公布的数 —— 依据只有两条：整张图要原样进内存，
         * 以及这一笔是 multipart 上传。真撞上限时站方会说话，那句话照原样念。
         */
        private const val MAX_UPLOAD_BYTES = 6L * 1024 * 1024

        /**
         * UI 愿意等多久。**比过盾那一层的"没有超时"长**才有意义：
         * 90 秒是"人来得及读完一个 Cloudflare 窗口并点过去"的量级，再长就只是在等一个死线程。
         */
        private const val WALL_CLOCK_MS = 90_000L
    }
}
