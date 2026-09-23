package com.venera.compose.source

import android.content.Context
import com.venera.compose.data.network.ComicUrlTable
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/**
 * 入站链接 → 漫画身份。两级：本仓库的双向表 → 各源自己声明的 `comic.link`。
 *
 * 顺序与 master 相反（master 只有 JS 那一级），理由是主线程：JS 引擎是 WebView，
 * `evaluateJavascript` 全部 post 到主线程串行执行，能不吃这一趟就不吃；表是纯字符串匹配，
 * 零成本就能覆盖 manifest 声明的绝大多数链接。**表 miss 才去问源**。
 *
 * 第二级不是冗余：源自己认的路径形状比我们的正则宽 —— 例如 `hitomi.js:1605` 认
 * `hitomi.la/<任意类型>/<名>-<id>.html` 全部类型，而表里只写了 `reader|galleries` 两种。
 *
 * ⚠️ 四条 Outcome 里只有 [Outcome.Resolved] 会跳页，其余三条一律**如实提示后停在原地**。
 * 特别不能学 `ComicDetailViewModel.resolveSourceKey` 那条回落（名字匹配不上就用
 * `activeSourceKey`）：列表点击时那条永远命中不了（显示名总对得上），但深链带进来一个
 * 本机没有的源时，它等于**拿另一个源的同 id 漫画开详情** —— 完全不相干的一本，且零提示。
 */
class ComicLinkResolver private constructor(private val context: Context) {

    sealed interface Outcome {
        /** 认出链接，且这个源本机装着：可以跳详情。 */
        data class Resolved(val sourceKey: String, val sourceName: String, val comicId: String) : Outcome

        /** 表与所有源都不认这条链接。 */
        data class Unrecognized(val url: String) : Outcome

        /** 认出了是哪个源，但本机没装或已禁用。 */
        data class SourceMissing(val sourceKeyOrName: String) : Outcome

        /** 源列表到点还没就绪（冷启动竞速）。不猜，如实报。 */
        data class SourcesNotReady(val url: String) : Outcome
    }

    /**
     * @param url 浏览器/别的 app 交进来的完整链接。
     */
    suspend fun resolve(url: String): Outcome {
        val manager = ComicSourceManager.getInstance(context)
        // 表这一级其实不需要源列表，但"这台机器装没装这个源"必须等列表就绪才能判 ——
        // 冷启动时 sourcesFlow 还是空的，直接判会把每一条链接都误报成"未安装"。
        val sources = withTimeoutOrNull(SOURCES_READY_TIMEOUT_MS) {
            manager.sourcesFlow.first { it.isNotEmpty() }
        } ?: manager.sourcesFlow.value
        if (sources.isEmpty()) return Outcome.SourcesNotReady(url)

        val hit = ComicUrlTable.match(url)?.let { it.sourceKey to it.comicId }
            ?: manager.resolveComicLink(url).firstOrNull()?.let { it.sourceKey to it.comicId }
            ?: return Outcome.Unrecognized(url)

        val installed = sources.find {
            it.key.equals(hit.first, ignoreCase = true) || it.name.equals(hit.first, ignoreCase = true)
        } ?: return Outcome.SourceMissing(hit.first)

        return Outcome.Resolved(sourceKey = installed.key, sourceName = installed.name, comicId = hit.second)
    }

    companion object {
        /** 冷启动等源列表就绪的上限：预warm 通常几百毫秒，给 10s 是把"首启在装 34 个源"算进去。 */
        private const val SOURCES_READY_TIMEOUT_MS = 10_000L

        @Volatile
        private var instance: ComicLinkResolver? = null

        fun getInstance(context: Context): ComicLinkResolver =
            instance ?: synchronized(this) {
                instance ?: ComicLinkResolver(context.applicationContext).also { instance = it }
            }
    }
}
