package com.venera.desktop.gallery.ui

import com.venera.compose.gallery.data.GalleryArtistAvatars
import com.venera.compose.gallery.data.GalleryFavorite
import com.venera.compose.gallery.data.GalleryPorts
import com.venera.compose.gallery.data.GallerySite
import com.venera.compose.gallery.domain.FollowedArtistRef
import com.venera.compose.gallery.domain.GalleryArtistAvatarProbe
import com.venera.compose.gallery.domain.GalleryArtistFollow
import com.venera.compose.gallery.domain.GalleryArtistLinks
import com.venera.compose.gallery.domain.GalleryAvatarProbeEndpoint
import com.venera.compose.gallery.domain.GalleryFollowedArtists
import com.venera.compose.gallery.domain.GalleryFollowedArtistsReadout
import com.venera.desktop.platform.DesktopLogger

/**
 * 桌面「画师」这一屏的判据落点（第八批）。
 *
 * ## 这颗文件存在的唯一目的：**只转交，不重述**
 *
 * 「什么叫同一位画师」「谁排在最前」「圆座这一档摆哪一张」三条判据全都住在共享那颗
 * [GalleryFollowedArtists] 里（Android 首页那一栏读的也是同一颗）。桌面这边一旦自己写一遍
 * 分组或排序，两处就会漂 —— 而漂出来的形状是"首页第一位与画师页第一位不是同一个人"，
 * 用户只会读成数据错了，谁也不会想到是两处各判了一次。
 * 所以这条纪律由 `DesktopArtistsWallTest` ⑦ 扫着：这颗文件里不许出现 `groupBy` / `sortedWith` /
 * `lowercase()` / `distinctBy` / `sortBy`。
 *
 * ## 为什么整页要单独一颗函数而不是直接调 `rowOf`
 *
 * `rowOf` 的 `rowSize` 管的是**横卡一行摆几位**（[GalleryFollowedArtists.ROW_SIZE] = 8，
 * 摆不下的用 `overflow` 念出来）。这一屏是整页，不是横卡，所以它要的是"摆全"。
 * 这一档差别如果只写在调用点的注释里，下一次有人把首页那栏与这一屏合并时就会连 `rowSize` 一起抄走 ——
 * 提出来命名，是为了让"整页不截断"这件事在类型上看得见。
 */

/** 整页不截断：把名单里有的位数全摆出来（横卡那一栏仍按 `ROW_SIZE` 截）。 */
internal const val DESKTOP_ARTISTS_PAGE_SIZE: Int = Int.MAX_VALUE

/** 名单 → 这一屏的读数。同人、排位、张数、卡面备用地址四件事一律由共享那颗判。 */
internal fun desktopArtistsReadout(
    follows: List<GalleryArtistFollow>,
    favorites: List<GalleryFavorite>,
): GalleryFollowedArtistsReadout = GalleryFollowedArtists.rowOf(follows, favorites, DESKTOP_ARTISTS_PAGE_SIZE)

/**
 * 名单 → 图库首页那一栏的读数：按共享那颗的 `ROW_SIZE`（8 位）截断，摆不下的由 `overflow` 念出来。
 *
 * 与整页那一颗分开，是为了让"这一栏只摆八位"这件事有个名字 ——
 * 两处都直接调 `rowOf` 时，下一次合并很容易把 `Int.MAX_VALUE` 抄进横卡，
 * 首页那一栏就变成一条无限长的横排。
 */
internal fun desktopFollowedArtistsRowReadout(
    follows: List<GalleryArtistFollow>,
    favorites: List<GalleryFavorite>,
): GalleryFollowedArtistsReadout = GalleryFollowedArtists.rowOf(follows, favorites)

/**
 * 圆座这一档摆哪一张：真头像 > 名下最近一张收藏 > `null`（`null` 才由界面落首字母座）。
 *
 * ⚠️ 这里**不**判"要不要去站方取头像" —— 那是调用点的事：整页那一屏按视口闸门现取
 * （[desktopArtistAvatarChain]），首页那一栏只读本机已存的那一份。两处都吃这一颗判档。
 */
internal fun desktopArtistFace(avatar: String?, previewUrl: String): String? =
    GalleryFollowedArtists.faceOf(avatar, previewUrl)

/** 组合重建时的脸表初值：档里已答上过真地址的那几位直接进表，不等异步。 */
internal fun desktopArtistFaces(
    readout: GalleryFollowedArtistsReadout,
    peek: (com.venera.compose.gallery.data.GallerySite, String) -> String?,
): Map<String, String> = GalleryFollowedArtists.seedFaces(readout.artists, peek)

/**
 * 头像地址档在桌面这一族的**规范键轨**：`source` 恒空串。
 *
 * 地址档的键是 `(site, name, source)`（判据在共享那颗 `GalleryArtistAvatars`），所以**写侧用哪一条，
 * 读侧就必须用同一条**。桌面名单里那一枚 `FollowedArtistRef` 根本不带出处 URL（它带的是名下那张收藏图的
 * 地址，而那是图不是"这位发布在哪儿"），所以规范键只能是空串 —— 读侧从第八批起就一直在交空串。
 *
 * ⚠️ 这一枚与探针端点里那个 URL（fanbox / Mastodon 的主页地址）**是两回事**，别混：
 * 端点用完即弃，键要跨会话命中。写成端点地址也能跑，但下一次同一位从另一条链接探到时键就换了，
 * 于是"修完取数还是读不到" —— 那是本缺陷第二版的形状，所以五处调用点只许吃这一枚常量（判据 ③ 扫着）。
 */
internal const val DESKTOP_AVATAR_SOURCE_KEY: String = ""

/** 画师行的 key 前缀：可见集要从 key 里认出"哪些是画师行"，节头页脚那些 item 不是。 */
internal const val DESKTOP_ARTIST_ROW_KEY_PREFIX: String = "artist:"

/**
 * 一位画师（在一站上）的头像端点：站方登记的链接表优先，出处地址退后。
 *
 * 次序照 Android 侧那一颗（`GalleryArtistRows.probeArtistAvatar`）：**站方逐条记在画师页上的链接
 * 比"这张图发布在哪儿"更像本人**。两条偏差都在注释里，不是随手加的：
 *
 * - 链接表也过 Mastodon 那一档。Android 那三档里 Mastodon 只吃 `source`，而桌面名单里**没有 source**
 *   （恒空串 ⇒ 那两档在桌面是死路）；不在链接表上试 Mastodon，baraag / pawoo 上那位就永远探不到。
 * - pixiv 那一档**在探针之前**试（批 42，Android `GalleryArtistRows.probeArtistAvatar` 同一次序）：
 *   链接表里那位留了 pixiv 主页就直接问一张脸；用户号的取法转交共享那颗 `GalleryArtistLinks.pixivUserId`，
 *   桌面不重述。问不到（`success(null)`）才继续退到下面两路，而**那一笔没走通不是定论**（不落档）。
 *
 * 派生规则本身（哪条 URL 算 fanbox、哪条算 Mastodon、白名单实例、必须带的 Origin）一律转交共享那颗
 * [GalleryArtistAvatarProbe]，桌面不重述。
 */
internal fun desktopAvatarProbePlan(
    links: List<String>,
    sourceFallback: String,
): GalleryAvatarProbeEndpoint? =
    links.firstNotNullOfOrNull { GalleryArtistAvatarProbe.fanboxCreator(it) }
        ?: links.firstNotNullOfOrNull { GalleryArtistAvatarProbe.mastodonLookup(it) }
        ?: GalleryArtistAvatarProbe.fanboxCreator(sourceFallback)
        ?: GalleryArtistAvatarProbe.mastodonLookup(sourceFallback)

/**
 * 取这一位的头像：先问本机档，没记录才发两笔（链接表 → 探针），跑完按"是不是定论"决定落不落档。
 *
 * 三件口径都是被形状教出来的，不是洁癖：
 *
 * - **档在前面**（Android 2026-10-03 真机读数量过的那条）： peek 是纯内存查表，
 *   把它排在一笔外链请求之后就等于每次进屏都为已知答案重付一次网络往返。
 * - **失败与"没有"是两句话**：`Result.failure` 交出去、**不落档**；只有定论（拿到地址 / 没有可探端点 /
 *   站方答上了但没那张脸）才落档。把一次断网落成"查过、没有"，这位在本机上就永远只剩一个字母 ——
 *   而那与"确实没头像"在屏上长成同一个样子。
 * - **端点派生不出来就不发第 2 笔**：注定失败的一笔不是"多试一次"，是硬凑。
 */
internal suspend fun desktopArtistAvatarChain(
    avatars: GalleryArtistAvatars,
    site: GallerySite,
    name: String,
    linkUrls: suspend (GallerySite, String) -> Result<List<String>>,
    probe: suspend (GalleryAvatarProbeEndpoint) -> Result<String?>,
    pixivAvatar: suspend (Long) -> Result<String?>,
): Result<String?> {
    avatars.peek(site, name, DESKTOP_AVATAR_SOURCE_KEY)?.let { recorded ->
        return Result.success(recorded.ifBlank { null })
    }
    val links = linkUrls(site, name).getOrElse { return Result.failure(it) }
    // pixiv 那一档在**最前面**（Android `GalleryArtistRows.probeArtistAvatar` 同一次序）：
    // 链接表里那位留了 pixiv 主页，就直接拿他的用户号问一张脸，问不到才退到 fanbox / Mastodon 的探针。
    // 用户号的取法转交共享那颗 GalleryArtistLinks.pixivUserId —— 桌面不重述（两份规则会漂，
    // 而漂出来的形状是"某一位画师永远只剩字母座"）。
    var pixivFailed: Throwable? = null
    val userId = links.firstNotNullOfOrNull { GalleryArtistLinks.pixivUserId(it) }
    if (userId != null) {
        val fromPixiv = pixivAvatar(userId)
        fromPixiv.getOrNull()?.let { url ->
            avatars.remember(site, name, DESKTOP_AVATAR_SOURCE_KEY, url)
            return Result.success(url)
        }
        // `success(null)` = 站方答上了而答复里没那张脸（可以继续退）；`failure` = 这一笔没走通（要记着）
        pixivFailed = fromPixiv.exceptionOrNull()
    }
    val endpoint = desktopAvatarProbePlan(links, sourceFallback = DESKTOP_AVATAR_SOURCE_KEY)
        ?: return pixivFailed?.let { failure ->
            // pixiv 那一笔没走通，而再没有别的端点可试 ⇒ 这不是定论，**不许落档**
            // （落一次档 = 一次网络抖动把这位永久判成"没有头像"）
            Result.failure(failure)
        } ?: Result.success(null).also { avatars.remember(site, name, DESKTOP_AVATAR_SOURCE_KEY, null) }
    val avatar = probe(endpoint).getOrElse { failure -> return Result.failure(pixivFailed ?: failure) }
    avatars.remember(site, name, DESKTOP_AVATAR_SOURCE_KEY, avatar)
    return Result.success(avatar)
}

/**
 * 这一位的链接表由**哪一颗客户端**答：与 Android 首页那一栏同一张对表。
 *
 * yande.re 有自己的画师端点，拿 danbooru 的供体去问它名下的画师多半查无此人 ——
 * 于是"这位没头像"其实是"问错了站"。
 */
internal suspend fun desktopArtistLinkUrls(
    ports: GalleryPorts,
    site: GallerySite,
    name: String,
): Result<List<String>> = when (site) {
    GallerySite.YANDERE -> ports.boards.yandere.artistLinks(name)
    GallerySite.GELBOORU, GallerySite.SAFEBOORU -> ports.artists.danbooruArtistUrls(name)
    // 2026-10-20：`GallerySite` 在 `:app` 侧加了 `PIXIV`（那批 Pixiv 接入），
    // 于是这颗 `when` 不再穷尽 —— 编译器把缺口指出来了，这里补上那一支。
    //
    // Pixiv **没有**"画师外链"这种端点：它的画挂在 `artworks/<id>`，画师是一个 `userId`
    // 而不是可枚举的外链串。所以答**空表**而不是 `failure`：
    // 空表在上层读作"这位没有可点的外链"，与 yande.re 查无此人是同一个观感；
    // 答 failure 会让那一格显示成错误，而"这站不提供"并不是错误。
    GallerySite.PIXIV -> Result.success(emptyList())
}

/**
 * 跨站同名并成一条的那一位：按徽标次序逐站试，先答上的赢（次序恒为枚举自然序，不会飘）。
 *
 * 每一站的失败都在这里留一句日志再试下一站 —— 屏上"字母座"这一种形状背后可能是
 * "两站都没这张脸"、"两站都没走通"、"够得着的两路都没有可探端点"三种事，
 * 能分清它们的只有日志（与 Android 侧同一条口径）。
 */
internal suspend fun desktopArtistProbedFace(ports: GalleryPorts, artist: FollowedArtistRef): DesktopFaceProbe {
    var firstFailure: String? = null
    artist.sites.forEach { site ->
        val outcome = desktopArtistAvatarChain(
            ports.avatars,
            site,
            artist.name,
            linkUrls = { key, name -> desktopArtistLinkUrls(ports, key, name) },
            probe = { endpoint -> ports.artists.probeAvatarUrl(endpoint) },
            pixivAvatar = { userId -> ports.artists.pixivAvatarUrl(userId) },
        )
        outcome.getOrNull()?.let { url -> return DesktopFaceProbe.Found(url) }
        outcome.exceptionOrNull()?.let { failure ->
            // ⚠️ 失败**不退成"没有"**：这句只进日志，屏上保持字母座；而调用方拿到的是
            // [DesktopFaceProbe.Failed]，它不会被落进定论表（换一次列表可以再问）。
            DesktopLogger.warn(AVATAR_PROBE_TAG, "画师「${artist.name}」($site) 的头像没取到：${failure.message}")
            firstFailure = firstFailure ?: failure::class.java.simpleName
        }
    }
    // 走到这里：要么有一条路**答上了"没有"**（定论），要么全部没答上（不是定论）。
    firstFailure?.let { return DesktopFaceProbe.Failed(it) }
    return DesktopFaceProbe.DefinitiveNone
}

/**
 * 视口闸门：从当前可见行的 key 里挑出"这一轮该发车"的那几位。
 *
 * 三条过滤各挡一种重复付费：
 * - `resolved`（本轮已查过，含"查过、没有"）—— 少了它，每次滚动都会把离开又回来的那几位重问一遍；
 * - `hasFace`（本机档已答上过）—— 少了它，冷启动带着地址进屏还要再发一次；
 * - 前缀 + 非空 —— 节头与页脚那些 item 也在可见集里，它们不是画师行。
 *
 * 次序按视口次序（从上到下），所以滚到哪儿取到哪儿，而不是名单顺序上的一长串。
 */
internal fun desktopVisibleProbeTargets(
    visibleKeys: List<Any?>,
    resolved: Set<String>,
    hasFace: Set<String>,
): List<String> = visibleKeys.filterIsInstance<String>()
    .filter { it.startsWith(DESKTOP_ARTIST_ROW_KEY_PREFIX) }
    .map { it.removePrefix(DESKTOP_ARTIST_ROW_KEY_PREFIX) }
    .filter { it.isNotEmpty() }
    .distinct()
    .filterNot { it in resolved || it in hasFace }

private const val AVATAR_PROBE_TAG = "DesktopArtistAvatar"

// ── 批 2 ④：关注 / 取关那两枚字与那一步的闸 ──────────────────────────────

/**
 * 「关注 / 已关注」那两枚字。
 *
 * 词与 Android 那颗胶囊同一颗（`GalleryArtistRows.kt:342` 的 `if (following) "已关注" else "关注"`）。
 * 两端各写一份的话，用户换设备会读成两件事 —— 而"已关注"与"关注"差的恰好是关系本身。
 */
internal fun desktopFollowLabel(following: Boolean): String = if (following) "已关注" else "关注"

/**
 * 取关要翻的那几站 —— **只翻已经在名单上的**。
 *
 * `toggle` 是翻转而不是移除：对一位没在名单上的站点下去会把他**加进**名单，与「取关」的本意
 * 完全相反，而且不报错。整页那一行的站集合来自**并条**（判据是名字），并条不代表每一站都有一条
 * 关注记录；那一格真出现时（老档残留、或并条规则以后被放宽），这一处筛子就是唯一拦住反向写入的地方。
 */
internal fun desktopUnfollowTargets(rowSites: List<GallerySite>, followed: Set<GallerySite>): List<GallerySite> =
    rowSites.filter { it in followed }

