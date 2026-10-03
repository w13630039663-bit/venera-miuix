package com.venera.compose.gallery.data

import com.venera.compose.data.platform.NoInteractiveBypassTag
import com.venera.compose.data.platform.HttpEngine
import java.io.IOException
import java.net.URLEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request

/**
 * Danbooru 的**画师外链**读取（批次 L）—— Gelbooru 那一侧画师唯一有可能给出平台链接的来源。
 *
 * 为什么要绕到第三方站：Gelbooru 自己给不出。2026-09-30 实测三处结构性缺口：
 *
 * - DAPI 的 `s=artist` **匿名一律 401**（与 `s=post`/`s=tag` 同一条凭据规则）；
 * - 原生画师页 `page=artist&s=show&id=NNN` **可以**匿名读（`id=1` 实测回 8781 字节、正文里就有
 *   `member.php?id=9311`），但**要的是 Gelbooru 自己的画师编号**；
 * - 而 `page=artist&s=list&name=X` 那个 `name=` **根本不生效**：正例、不存在的名字、不带参数
 *   三次返回**逐字节相同**（都是 17621 字节、同样 64 条链接），只是那一屏默认列表。
 *   于是从"这张图的画师标签"到"那条画师记录的编号"之间没有匿名可走的一跳。
 *
 * Danbooru 与 Gelbooru 共享画师名，且 `artists.json?name=` 匿名语义可用（用户 2026-09-30 点名走这条）。
 *
 * ⚠️ **今天这台出口拿不到样例**：danbooru / safebooru / testbooru 三站同测**全部 403 卡过盾页**
 * （`Just a moment...`）。所以这一条：
 *
 * - 打 [NoInteractiveBypassTag] —— 画廊这一路**不弹过盾窗口**（与日榜同口径，挂在那儿等交互会把整屏挂死），
 *   用户拍板的形态是「只静默试一次，失败就什么都不摆」；
 * - 但**照常享受**漫画侧那条已经有效的过盾链：`cf_clearance` 存在共享 CookieJar 里按 host 生效，
 *   谁在漫画侧给 danbooru 通关一次，这里的同一笔请求就直接可读，不需要在本模块再造一份 UI；
 * - 卡盾时 [GalleryArtistEndpointParse] 抛错 → `Result.failure` → 画师行不摆图标也不说"这位没有外链"。
 *   这条区分是这一整块的红线：**"没取到"与"没有"必须是两句话**。
 *
 * 字段形态押不了（拿不到真样例），所以走 [GalleryArtistEndpointParse] 的多形态宽容解析，
 * 每一种候选形态各有一条单测兜着。
 */
class DanbooruArtistClient internal constructor(private val engine: HttpEngine) {

    /** 那位画师的外链地址。空表 = 站方答上了但查不到同名记录；`failure` = 这一路没走通（含卡盾）。 */
    suspend fun artistUrls(name: String): Result<List<String>> = withContext(Dispatchers.IO) {
        runCatching {
            GalleryArtistEndpointParse.artistUrlsByName(execute("$BASE/artists.json?name=${enc(name)}"), name)
        }
    }

    /**
     * 同一条记录的**外链 + 登记别名**（批次 Q+R：介绍页两栏都要，一次拿）。
     *
     * 与 [artistUrls] 打的是同一个端点、同一笔请求形状，失败口径也照它：
     * 站方没这条记录 = 两个空表（合法答复）；404 / 非 JSON / 卡盾 = `failure`。
     */
    suspend fun artistCredits(name: String): Result<DanbooruArtistCredits> = withContext(Dispatchers.IO) {
        runCatching {
            GalleryArtistEndpointParse.artistCreditsByName(execute("$BASE/artists.json?name=${enc(name)}"), name)
        }
    }

    private fun execute(url: String): String {
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .tag(NoInteractiveBypassTag::class.java, NoInteractiveBypassTag())
            .build()
        val client = engine.okHttpClient
        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            // 过盾页就是 403 + 一坨 HTML：这里先按状态码抛，正文里的形状由解析层第二条兜住。
            if (!response.isSuccessful) throw IOException("danbooru 返回 ${response.code}")
            return body
        }
    }

    companion object {
        private const val BASE = "https://danbooru.donmai.us"

        private fun enc(v: String): String = URLEncoder.encode(v, "UTF-8")

        @Volatile
        private var INSTANCE: DanbooruArtistClient? = null

        fun getInstance(engine: HttpEngine): DanbooruArtistClient = INSTANCE ?: synchronized(this) {
            INSTANCE ?: DanbooruArtistClient(engine).also { INSTANCE = it }
        }
    }
}
