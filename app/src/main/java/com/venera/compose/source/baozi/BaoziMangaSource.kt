package com.venera.compose.source.baozi

import android.content.Context
import com.venera.compose.data.network.VeneraNetworkClient
import com.venera.compose.source.ComicSource
import com.venera.compose.source.model.ChapterPages
import com.venera.compose.source.model.Comic
import com.venera.compose.source.model.ComicChapter
import com.venera.compose.source.model.ComicDetails
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import java.net.URLEncoder

/**
 * 包子漫画 (Baozi Manga) 漫画源
 * 特点：基于 Jsoup 高性能 HTML DOM 解析，免登录直接爬取国内海量条漫与日漫
 */
class BaoziMangaSource(private val context: Context) : ComicSource {

    override val key: String = "baozi"
    override val name: String = "包子漫画"
    override val version: String = "1.1.6"
    override val iconUrl: String = "https://baozimhcn.com/favicon.ico"

    private val networkClient by lazy { VeneraNetworkClient.getInstance(context) }
    private val baseUrl = "https://baozimhcn.com"

    override suspend fun ping(): Long = withContext(Dispatchers.IO) {
        val start = System.currentTimeMillis()
        try {
            val html = networkClient.get("$baseUrl/search?q=one")
            if (html.isNotEmpty()) {
                System.currentTimeMillis() - start
            } else {
                -1L
            }
        } catch (e: Exception) {
            -1L
        }
    }

    override suspend fun search(keyword: String, page: Int, options: List<String>?): Result<List<Comic>> = withContext(Dispatchers.IO) {
        runCatching {
            val encoded = URLEncoder.encode(keyword, "UTF-8")
            val html = networkClient.get("$baseUrl/search?q=$encoded")
            val doc = Jsoup.parse(html)
            val cards = doc.select("div.comics-card")
            val list = mutableListOf<Comic>()

            for (card in cards) {
                val linkEl = card.selectFirst("a")
                val href = linkEl?.attr("href") ?: ""
                val comicId = href.removePrefix("/comic/").removeSuffix("/").trim()
                if (comicId.isEmpty()) continue

                val title = card.selectFirst(".comics-card__title")?.text()
                    ?: linkEl?.attr("title")?.ifEmpty { "未知标题" } ?: "未知标题"
                val cover = card.selectFirst("amp-img")?.attr("src")
                    ?: card.selectFirst("img")?.attr("src") ?: ""
                val subTitle = card.selectFirst(".comics-card__sub")?.text() ?: "包子精选"

                list.add(
                    Comic(
                        id = comicId,
                        title = title,
                        subTitle = subTitle,
                        cover = cover,
                        sourceKey = key
                    )
                )
            }
            list
        }
    }

    override suspend fun getComicDetails(comicId: String): Result<ComicDetails> = withContext(Dispatchers.IO) {
        runCatching {
            val html = networkClient.get("$baseUrl/comic/$comicId")
            val doc = Jsoup.parse(html)

            val title = doc.selectFirst("h1.comics-detail__title")?.text() ?: comicId
            val author = doc.selectFirst(".comics-detail__author")?.text() ?: "包子作者"
            val desc = doc.selectFirst(".comics-detail__desc")?.text() ?: ""
            val cover = doc.selectFirst(".comics-detail__cover amp-img")?.attr("src")
                ?: doc.selectFirst(".comics-detail__cover img")?.attr("src") ?: ""

            val chapterElements = doc.select("#chapter-items .comics-chapters__item a")
            val chapters = mutableListOf<ComicChapter>()

            for ((index, item) in chapterElements.withIndex()) {
                val href = item.attr("href")
                val epId = href.substringAfterLast("/").removeSuffix(".html")
                val chTitle = item.text().ifEmpty { "第 ${index + 1} 话" }

                chapters.add(
                    ComicChapter(
                        id = epId,
                        title = chTitle,
                        order = index + 1
                    )
                )
            }

            val tags = doc.select("div.tag-list span").map { it.text().trim() }.filter { it.isNotEmpty() }
            val status = if (tags.any { it.contains("完结") }) "已完结" else "连载中"

            ComicDetails(
                comic = Comic(
                    id = comicId,
                    title = title,
                    subTitle = author,
                    cover = cover,
                    sourceKey = key,
                    tags = tags,
                    description = desc
                ),
                author = author,
                status = status,
                rating = 0f,
                chapters = chapters
            )
        }
    }

    override suspend fun getChapterPages(comicId: String, chapterId: String): Result<ChapterPages> = withContext(Dispatchers.IO) {
        runCatching {
            // 包子漫画章节图源地址
            val chapterUrl = "https://appcn.baozimh.com/baozimhapp/comic/chapter/$comicId/0_$chapterId.html"
            val html = networkClient.get(chapterUrl)
            val doc = Jsoup.parse(html)
            val imgNodes = doc.select(".comic-contain > .chapter-img")
            val pages = mutableListOf<String>()

            for (node in imgNodes) {
                val dataSrc = node.selectFirst(".comic-contain__item")?.attr("data-src")
                    ?: node.selectFirst("amp-img")?.attr("src")
                    ?: node.selectFirst("img")?.attr("src")
                if (!dataSrc.isNullOrEmpty()) {
                    pages.add(dataSrc)
                }
            }

            ChapterPages(
                comicId = comicId,
                chapterId = chapterId,
                pages = pages,
                headers = mapOf("Referer" to baseUrl)
            )
        }
    }

    override suspend fun getExploreComics(page: Int): Result<List<Comic>> = withContext(Dispatchers.IO) {
        runCatching {
            val html = networkClient.get("$baseUrl/classify?page=$page")
            val doc = Jsoup.parse(html)
            val cards = doc.select("div.comics-card")
            val list = mutableListOf<Comic>()

            for (card in cards) {
                val linkEl = card.selectFirst("a")
                val href = linkEl?.attr("href") ?: ""
                val comicId = href.removePrefix("/comic/").removeSuffix("/").trim()
                if (comicId.isEmpty()) continue

                val title = card.selectFirst(".comics-card__title")?.text() ?: "精选"
                val cover = card.selectFirst("amp-img")?.attr("src")
                    ?: card.selectFirst("img")?.attr("src") ?: ""

                list.add(
                    Comic(
                        id = comicId,
                        title = title,
                        subTitle = "🔥 热门收录",
                        cover = cover,
                        sourceKey = key
                    )
                )
            }
            list
        }
    }
}
