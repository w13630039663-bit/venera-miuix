package com.venera.compose.gallery.data

import com.venera.compose.gallery.domain.GalleryArtistUrls
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * 画师取数层里**响应形状不确定**的两处解析（批次 L）。
 *
 * 为什么手写逐字段走、不给 `@Serializable` DTO：DTO 等于**押一种形态**。押错的表现是
 * "安静地什么都不显示"（键对不上就取默认值，编译与运行都不报），而这一层的形态恰恰押不了 ——
 * Danbooru 三个同库站点在 2026-09-30 本机出口全部 403 卡在过盾页，拿不到一份真样例。
 * 于是把源码里查得到的候选形态**全部吃掉**（`urls` 字符串数组 / `urls` 对象数组 / `url_string` 一整串），
 * 每种形态各有一条单测兜着（见 `GalleryArtistEndpointParseTest`）。
 *
 * 统一口径只有一条：**认不出 = 抛错**，绝不静默交回空表。空表在屏上读起来是"这位没有外链"，
 * 而"我们被挡在门外"和"这位真没外链"必须能被区分开 —— 前者要能被日志查出来。
 */
internal object GalleryArtistEndpointParse {

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * 从 danbooru 系 `artists.json` 的答复里取那位画师的外链地址。
     *
     * 名字**按全等**取（`name=` 那侧是前缀匹配，实测一筛就出一串邻居记录，
     * 挑第一条等于把隔壁画师的链接挂到这位名下）。空表 = 站方答上了、但没有全等记录，那是合法答复。
     */
    fun artistUrlsByName(body: String, name: String): List<String> {
        val record = exactRecord(body, name) ?: return emptyList()
        return urlsOf(record)
    }

    /**
     * 同一条记录里介绍页要用的**两个**字段（批次 Q+R：外链 + 别名一次拿，不打两笔）。
     *
     * `other_names` 是 danbooru 系的键（真样本 `_probe/hub/dan_setmen.json`：
     * `name=setmen` 挨着 `other_names:["u_u","セトマン"]`）。**键不在 = 空表**，
     * 那是站方答上了"这位没有登记别名"，与"这一路没走通"是两句话 —— 后者由状态码那一关抛出去。
     *
     * 与 [artistUrlsByName] **各自取各自的键**（只共用 [exactRecord] 那一跳）：别名那个键读不出
     * 形状时不该把画师行已经能摆的外链一起拖没 —— 少一枚胶囊与少一排图标是两件事。
     */
    fun artistCreditsByName(body: String, name: String): DanbooruArtistCredits {
        val record = exactRecord(body, name) ?: return DanbooruArtistCredits(emptyList(), emptyList())
        // `runCatching` 包住的只有**别名那一栏**：形状对不上时只丢这一栏，
        // 不能让介绍页新读的一个键把画师行已经能摆的外链一起拖没（那一档在屏上是"少一行"，
        // 而后者是"少一整排"，代价不对等）。整份答复读不懂仍然照抛（见 [exactRecord]）。
        val otherNames = record[OTHER_NAMES]?.let { element ->
            runCatching { element.jsonArray }.getOrNull()
                ?.mapNotNull { it.jsonPrimitive.contentOrNull?.takeIf { value -> value.isNotBlank() } }
        }.orEmpty()
        return DanbooruArtistCredits(urlsOf(record), otherNames)
    }

    /** 数组里按**全等名**（去空白、大小写无关）取那一条记录；取不到回 null = 站方没这条记录。 */
    private fun exactRecord(body: String, name: String): JsonObject? {
        val wanted = name.trim().lowercase()
        return json.parseToJsonElement(body).jsonArray
            .firstOrNull { it.jsonObject[NAME]?.jsonPrimitive?.contentOrNull?.trim()?.lowercase() == wanted }
            ?.jsonObject
    }

    /** pixiv `/ajax/user/{id}` 的答复里取头像地址。`error=true` 是站方明说没这个人 —— 那是失败，不是"没头像"。 */
    fun pixivAvatarUrl(body: String): String? {
        val root = json.parseToJsonElement(body).jsonObject
        if (root[ERROR]?.jsonPrimitive?.contentOrNull?.lowercase() == "true") {
            throw IllegalStateException("pixiv 回 error=true（message=${root[MESSAGE]?.jsonPrimitive?.contentOrNull.orEmpty()}）")
        }
        val user = root[BODY]?.jsonObject ?: throw IllegalStateException("pixiv 答复里没有 body")
        return GalleryArtistUrls.avatarUrl(
            imageBig = user[IMAGE_BIG]?.jsonPrimitive?.contentOrNull,
            image = user[IMAGE]?.jsonPrimitive?.contentOrNull,
        )
    }

    /**
     * fanbox `creator.get` 的答复里取创作者头像地址（批次 L · L9）。
     *
     * 字段路径是 2026-09-30 14:05 的真答复（`body.user.iconUrl`，那台图床 `pixiv.pximg.net` 实测不挡防盗链）。
     * 失败那一档站方回的是 **400 + `{"error":"general_error"}`**，正常取数层在状态码上就抛了；
     * 这里再挡一次是因为**站方哪天改成 200 也带 error 串**时，把它读成"这位没头像"就是静默交错。
     */
    fun fanboxAvatarUrl(body: String): String? {
        val root = json.parseToJsonElement(body).jsonObject
        val error = root[ERROR]?.jsonPrimitive?.contentOrNull
        if (error != null && !error.equals("false", ignoreCase = true)) {
            throw IllegalStateException("fanbox 回 error=$error")
        }
        val user = root[BODY]?.jsonObject?.get(USER)?.jsonObject
            ?: throw IllegalStateException("fanbox 答复里没有 body.user")
        return GalleryArtistUrls.avatarUrl(
            imageBig = user[ICON_URL]?.jsonPrimitive?.contentOrNull,
            image = null,
        )
    }

    /**
     * Mastodon `accounts/lookup` 的答复里取头像地址（批次 L · L9）。
     *
     * 两根键都给出时**先取静态档**：头像位只有 34dp，那是 chrome 不是内容，为一枚圆座挂动图解码器不值
     * （与"画廊墙缩略档恒 jpg"同一条口径）。
     *
     * 实例给没设过头像的账号挂的是 `/avatars/original/missing.png` 那张通用剪影 —— 它**是个合法地址**，
     * 摆上屏却读成"这位有头像"。所以判掉，让调用方退回首字母座（说的是同一件事的诚实版本）。
     */
    fun mastodonAvatarUrl(body: String): String? {
        val root = json.parseToJsonElement(body).jsonObject
        val staticUrl = root[AVATAR_STATIC]?.jsonPrimitive?.contentOrNull
        val animatedUrl = root[AVATAR]?.jsonPrimitive?.contentOrNull
        if (staticUrl == null && animatedUrl == null) {
            throw IllegalStateException("Mastodon 答复里既没有 avatar 也没有 avatar_static")
        }
        val url = GalleryArtistUrls.avatarUrl(imageBig = staticUrl, image = animatedUrl) ?: return null
        if (url.substringBefore('?').endsWith("/missing.png")) return null
        return url
    }

    /**
     * pixiv `ajax/illust/{作品号}` 的答复里取**这张图的作者**。
     *
     * 为什么走这一条：出处常是作品页（`/artworks/N`），那串数字既不能当用户号也不能直接当入口，
     * 而这个端点**匿名可读**（2026-09-30 14:26 实测，三个字段都是字符串），一跳就拿到真的用户号。
     *
     * 删帖/锁帖那一档站方回 **404 + `{"error":true}`**（15 个样本里 4 个），取数层在状态码上就抛了；
     * 这里对 `error=true`、对**没有可用 userId** 的 200 一律再抛一次 —— 交 null 会让画师行少一枚
     * 而没人知道为什么，而"猜一个号去要头像"就是拿别人的脸挂到这位名下。
     */
    fun pixivArtworkAuthor(body: String): PixivArtworkAuthor {
        val root = json.parseToJsonElement(body).jsonObject
        if (root[ERROR]?.jsonPrimitive?.contentOrNull?.lowercase() == "true") {
            throw IllegalStateException("pixiv 回 error=true（message=${root[MESSAGE]?.jsonPrimitive?.contentOrNull.orEmpty()}）")
        }
        val detail = root[BODY]?.jsonObject ?: throw IllegalStateException("ajax/illust 答复里没有 body")
        val userId = detail[USER_ID]?.jsonPrimitive?.contentOrNull?.toLongOrNull()?.takeIf { it > 0 }
            ?: throw IllegalStateException("ajax/illust 答复里没有可用的 userId")
        return PixivArtworkAuthor(
            userId = userId,
            account = detail[USER_ACCOUNT]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() },
            name = detail[USER_NAME]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() },
        )
    }

    /** 这一条记录里的外链地址，三种候选形态都吃。 */
    private fun urlsOf(record: JsonObject): List<String> {
        val urls = record[URLS]
        if (urls == null) return GalleryArtistUrls.split(record[URL_STRING]?.jsonPrimitive?.contentOrNull)
        return urls.jsonArray.flatMap { item ->
            when (val element = item) {
                // 对象形态：地址在 `url` 键里；`is_active=false` 那条与 `-` 前缀同义，一并跳过。
                is JsonObject -> {
                    val url = element[URL]?.jsonPrimitive?.contentOrNull ?: return@flatMap emptyList()
                    val inactive = element[IS_ACTIVE]?.jsonPrimitive?.booleanOrNull == false
                    if (inactive) emptyList() else listOf(url)
                }
                else -> listOfNotNull(element.jsonPrimitive.contentOrNull)
            }
        }.let { GalleryArtistUrls.split(it.joinToString("\n")) }
    }

    private const val NAME = "name"
    private const val USER = "user"
    private const val USER_ID = "userId"
    private const val USER_NAME = "userName"
    private const val USER_ACCOUNT = "userAccount"
    private const val ICON_URL = "iconUrl"
    private const val AVATAR = "avatar"
    private const val AVATAR_STATIC = "avatar_static"
    private const val URLS = "urls"
    private const val OTHER_NAMES = "other_names"
    private const val URL = "url"
    private const val URL_STRING = "url_string"
    private const val IS_ACTIVE = "is_active"
    private const val ERROR = "error"
    private const val MESSAGE = "message"
    private const val BODY = "body"
    private const val IMAGE = "image"
    private const val IMAGE_BIG = "imageBig"
}

/**
 * 一张 pixiv 作品的作者。
 *
 * [account] / [name] 可能是 null（站方给空串时我们不当"有值"用）；画师行那枚胶囊带账号，
 * 就是为了"这张图的作者"与"站方登记的这位"不同名时，屏上看得出来挂的是谁的主页。
 */
data class PixivArtworkAuthor(val userId: Long, val account: String?, val name: String?)

/**
 * 一条 danbooru 系画师记录里介绍页要用的两栏（批次 Q+R）。
 *
 * 空表在两边都是**合法答复**（站方答上了、只是没登记），所以这里不区分"没键"与"键为空" ——
 * 区分它需要在屏上说"这位没有别名"，而那句话我们没资格说（口径见
 * [com.venera.compose.gallery.domain.GalleryArtistProfile]）。
 */
data class DanbooruArtistCredits(val urls: List<String>, val otherNames: List<String>)
