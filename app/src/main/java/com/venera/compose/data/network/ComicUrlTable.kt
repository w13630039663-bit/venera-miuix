package com.venera.compose.data.network

/**
 * 漫画 URL 双向表 (S4 起的核心能力，本轮补上反方向)
 *
 * 一个源一条记录，三个方向共用同一条，不另立第二张表：
 * - [match] 从链接抽 `sourceKey` + `comicId`（搜索框粘贴直达 / 应用链接入站）
 * - [shareUrlFor] 反向拼「这本在原站的链接」（分享 / 复制链接）
 * - [appLinkScopes] 决定 `AndroidManifest` 里该声明哪些 host + pathPrefix
 *
 * ⚠️ 分成两张表的必然结果是口径漂移：镜像域名换址时只改了一张，另一个方向就静默失效。
 * 声明域名的清单与匹配正则同源，才有 [ComicUrlTableAppLinkScopeTest] 那种"XML 与表逐条对齐"的校验。
 */
object ComicUrlTable {

    data class MatchedComic(
        val sourceKey: String,
        val sourceName: String,
        val comicId: String,
        val originalUrl: String
    )

    /** manifest 的声明位：系统对 host 精确匹配、对 pathPrefix 前缀匹配，两者都不支持通配。 */
    data class AppLinkScope(val host: String, val pathPrefix: String)

    private data class Entry(
        val sourceKey: String,
        val sourceName: String,
        /** host+路径写在同一条正则里，字符串与 S4 时期逐字一致（收紧匹配会打断搜索框直达）。 */
        val patterns: List<Regex>,
        /** `%s` = comicId。多镜像时取下面注释里那个有依据的域。 */
        val shareTemplate: String,
        val appLinks: List<AppLinkScope>,
    )

    private val entries: List<Entry> = listOf(
        // 拷贝漫画：分享域取源脚本自己 favicon 用的 www.copymanga.tv（CopyMangaSource.kt:35）
        Entry(
            sourceKey = "copy_manga",
            sourceName = "拷贝漫画",
            patterns = listOf(
                Regex("""copymanga\.[^/]+/comic/([a-zA-Z0-9_-]+)"""),
                Regex("""mangacopy\.[^/]+/comic/([a-zA-Z0-9_-]+)"""),
            ),
            shareTemplate = "https://www.copymanga.tv/comic/%s",
            appLinks = hostsWithPrefix(
                listOf("copymanga.tv", "www.copymanga.tv", "copymanga.site", "www.copymanga.site", "mangacopy.com", "www.mangacopy.com"),
                "/comic",
            ),
        ),
        // 包子漫画：www.baozimh.com 是表注释里的网页域；baozimhcn.com 是 API 域（BaoziMangaSource.kt:28），路径形状不同，故不声明
        Entry(
            sourceKey = "baozi",
            sourceName = "包子漫画",
            patterns = listOf(Regex("""baozimh\.[^/]+/comic/([a-zA-Z0-9_-]+)""")),
            shareTemplate = "https://www.baozimh.com/comic/%s",
            appLinks = hostsWithPrefix(
                listOf("baozimh.com", "www.baozimh.com", "cn.baozimh.com"),
                "/comic",
            ),
        ),
        Entry(
            sourceKey = "manga_dex",
            sourceName = "MangaDex",
            patterns = listOf(Regex("""mangadex\.org/title/([a-zA-Z0-9_-]+)""")),
            shareTemplate = "https://mangadex.org/title/%s",
            appLinks = hostsWithPrefix(listOf("mangadex.org", "www.mangadex.org"), "/title"),
        ),
        // B 站漫画的 id 在站内带 `mc` 前缀（/detail/mc12345），模板要把前缀拼回去
        Entry(
            sourceKey = "bilibili",
            sourceName = "哔哩哔哩漫画",
            patterns = listOf(Regex("""manga\.bilibili\.com/detail/mc(\d+)""")),
            shareTemplate = "https://manga.bilibili.com/detail/mc%s",
            appLinks = hostsWithPrefix(listOf("manga.bilibili.com"), "/detail"),
        ),
        // 禁漫：域名与路径逐字取自 master 的硬编码分享表（comic_details_page/actions.dart:92-105）
        Entry(
            sourceKey = "jm",
            sourceName = "禁漫天堂",
            patterns = listOf(Regex("""(?:18comic|jmcomic|jmapinode)[^/]+/(?:album|photo)/(\d+)""")),
            shareTemplate = "https://18comic.vip/album/%s",
            appLinks = hostsWithPrefix(
                listOf("18comic.vip", "www.18comic.vip", "18comic.org", "www.18comic.org", "jmcomic.me", "www.jmcomic.me"),
                "/album", "/photo",
            ),
        ),
        // EH 的 id 只是 gid，站内地址还要 token；master 同样只拼到 /g/{gid}/（公开本子可用）。
        // token 在匹配侧刻意做成可选 —— 否则我们分享出去的 /g/{gid}/ 自己认不回来（双向一致性有单测锁）。
        Entry(
            sourceKey = "ehentai",
            sourceName = "E-Hentai",
            patterns = listOf(Regex("""(?:e-hentai|exhentai)\.org/g/(\d+)(?:/([a-zA-Z0-9]+))?""")),
            shareTemplate = "https://e-hentai.org/g/%s/",
            appLinks = hostsWithPrefix(listOf("e-hentai.org", "exhentai.org"), "/g"),
        ),
        // nhentai：域名与路径同样逐字取自 master 的分享表
        Entry(
            sourceKey = "nhentai",
            sourceName = "nhentai",
            patterns = listOf(Regex("""nhentai\.net/g/(\d+)""")),
            shareTemplate = "https://nhentai.net/g/%s/",
            appLinks = hostsWithPrefix(listOf("nhentai.net", "www.nhentai.net"), "/g"),
        ),
        Entry(
            sourceKey = "wnacg",
            sourceName = "绅士漫画",
            patterns = listOf(Regex("""wnacg\.[^/]+/photos-(?:index|slide)-aid-(\d+)""")),
            shareTemplate = "https://wnacg.com/photos-index-aid-%s.html",
            // wn01.link 是 wnacg.js:91 的域名刷新页，路径形状与站内一致与否未证 —— 先只声明表内已证的 wnacg.*
            appLinks = hostsWithPrefix(listOf("wnacg.com", "www.wnacg.com", "wnacg.org"), "/photos-"),
        ),
        // 哔咔 PicaWeb 的单本路由：2026-09-23 三条依据齐了才敢写进来 ——
        // ① 它的路由表里是 `/comic/:comicId`（**单数**；`/comics/*` 那一族全是列表页）；
        // ② 点一本后地址栏是 https://manhuabika.com/comic/<24位hex>，直接打开该地址也能渲染详情；
        // ③ 我们的 `picacg.js:96` 里 `id: comic._id`，与路由参数是同一个 24 位 hex ObjectId。
        // 阅读器路径 `/comic/reader/<id>/<order>` 用非捕获组一并认掉（分享出去的地址仍指详情页）。
        // ⚠️ 只有 manhuabika.com 这一条域是有依据的：哔咔网页版镜像常年漂移，别的域要么实测过
        // 要么等用户给出真链接再补 —— 而 intent-filter 是静态清单，补域必须发版。
        Entry(
            sourceKey = "picacg",
            sourceName = "Picacg",
            patterns = listOf(
                Regex("""manhuabika\.[^/]+/comic/(?:reader/)?([0-9a-f]{24})(?:/|$)"""),
            ),
            shareTemplate = "https://manhuabika.com/comic/%s",
            appLinks = hostsWithPrefix(listOf("manhuabika.com", "www.manhuabika.com"), "/comic/"),
        ),
        Entry(
            sourceKey = "hitomi",
            sourceName = "Hitomi",
            patterns = listOf(Regex("""hitomi\.[^/]+/(?:reader|galleries)/(\d+)""")),
            shareTemplate = "https://hitomi.la/reader/%s.html",
            appLinks = hostsWithPrefix(listOf("hitomi.la", "www.hitomi.la"), "/reader", "/galleries"),
        ),
    )

    /** 这条链接是不是某个已知源的漫画页；是就给出身份。 */
    fun match(input: String): MatchedComic? {
        val trimmed = input.trim()
        if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) return null
        for (entry in entries) {
            for (pattern in entry.patterns) {
                val hit = pattern.find(trimmed) ?: continue
                return MatchedComic(
                    sourceKey = entry.sourceKey,
                    sourceName = entry.sourceName,
                    comicId = hit.groupValues[1],
                    originalUrl = trimmed,
                )
            }
        }
        return null
    }

    /**
     * 反方向：按源标识（key 或显示名）+ 漫画 id 拼原站链接。
     *
     * 表里没有这个源、或 id 为空 → 返回 null。**调用方必须把 null 如实退化**（只分享标题），
     * 不要拿源根地址或站内搜索页凑一个"看起来像链接"的东西出去。
     */
    fun shareUrlFor(sourceKeyOrName: String?, comicId: String?): String? {
        val id = comicId?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val needle = sourceKeyOrName?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val entry = entries.firstOrNull {
            it.sourceKey.equals(needle, ignoreCase = true) || it.sourceName.equals(needle, ignoreCase = true)
        } ?: return null
        return entry.shareTemplate.format(id)
    }

    /** manifest 该声明的 (host, pathPrefix) 全集；由 ComicUrlTableAppLinkScopeTest 与 XML 逐条对齐。 */
    val appLinkScopes: List<AppLinkScope> = entries.flatMap { it.appLinks }

    private fun hostsWithPrefix(hosts: List<String>, vararg prefixes: String): List<AppLinkScope> =
        hosts.flatMap { host -> prefixes.map { AppLinkScope(host, it) } }
}
