package com.venera.compose.data.update

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/**
 * 一次 `api.github.com/repos/<slug>/releases/latest` 响应的解析层。
 *
 * ## 为什么单独立一颗，而不是在 `AppUpdateChecker` 里就地抠字段
 *
 * 旧写法用 `org.json.JSONObject` 只抠 `tag_name`，而这一层要的东西多了三样：更新日志（`body`）、
 * 候选安装包（`assets`）、以及每颗包的 **`digest`**。三样都必须解析得**可脱机钉住**，而
 * `org.json` 在 JVM 单测里是 android.jar 的空壳桩（返回默认值）—— 本仓已经为这件事写过两次
 * 结案陈词（`data/db/JsonValues.kt:13-16`、`gallery/data/SauceNaoParsingTest.kt:20`），
 * 所以这里用 kotlinx.serialization 的树式读法，与 `gallery/data/SauceNao.kt:89` 同一个口径。
 *
 * ## `digest` 是这一颗存在的主要理由
 *
 * GitHub 在 asset 上直接给 `"sha256:e6f6…"`。有了它，"下载源可以是别人家的域名"这件事
 * 才从信任问题降级成可达性问题：字节走镜像，**事实**（版本号、日志、哈希）只走 api.github.com。
 * 那条分工写在 [downloadCandidates] 与方案文档 §1.2，别在后来被"顺手统一"掉。
 */

/** GitHub 给安装包的那个类型，也是「这条产物是不是我们要的东西」的唯一判式。 */
private const val APK_CONTENT_TYPE = "application/vnd.android.package-archive"

/** 一条发布产物。[url] 恒为官方直链，镜像前缀不在这里拼。 */
data class ReleaseAsset(
    val name: String,
    /** 字节数。拿不到时是 0，只影响"还剩多少"这句文案，不影响判据。 */
    val size: Long,
    /** 小写十六进制的 sha256，**形状不对就当没有**（null），见 [sha256Of]。 */
    val sha256: String?,
    val url: String,
)

/** 发布通道上的最新一版。 */
data class LatestRelease(
    val tagName: String,
    /** `v2.0.3-miuix` → `2.0.3`，展示与比较都用它。 */
    val version: String,
    /** GitHub 上手写的 Markdown，逐字交回 [changelogBlocks] 处理。 */
    val changelog: String,
    /** 只有 APK；`output-metadata.json` 这类伴生产物已被 [APK_CONTENT_TYPE] 挡在门外。 */
    val apks: List<ReleaseAsset>,
)

/**
 * 解一次 `releases/latest`。返回 null 的含义**只有这一种**：这不是一个能据以判断的发布响应。
 *
 * 为什么这么在意：调用方把 null 折成 `UpdateCheck.Failed`，而 `Failed` 与 `UpToDate` 必须分开 ——
 * 一次限流（GitHub 匿名 API 每小时 60 次）若被解成"已是最新版本"，就是一次静默的假成功。
 * 所以 `tag_name` 缺失/空白、根不是 object、JSON 半截，三种都算 null；
 * 而 `assets` 缺失**不算** null（版本号与日志照样成立，只是没有可下的包，那一面由
 * [pickApkAsset] 交 null、UI 如实说"这一版没挂安装包"）。
 */
internal fun parseLatestRelease(body: String): LatestRelease? {
    val root = runCatching { Json.parseToJsonElement(body).let { it as? JsonObject } }.getOrNull()
        ?: return null
    val tag = root.stringOf("tag_name")
    if (tag.isNullOrBlank()) return null
    val assets = root["assets"].asArrayOrNull()?.mapNotNull { element ->
        val asset = element as? JsonObject ?: return@mapNotNull null
        if (asset.stringOf("content_type") != APK_CONTENT_TYPE) return@mapNotNull null
        val name = asset.stringOf("name").orEmpty()
        if (name.isBlank()) return@mapNotNull null
        ReleaseAsset(
            name = name,
            size = asset["size"]?.jsonPrimitive?.longOrNull ?: 0L,
            sha256 = sha256Of(asset.stringOf("digest")),
            url = asset.stringOf("browser_download_url").orEmpty(),
        )
    }.orEmpty()
    return LatestRelease(
        tagName = tag,
        version = AppUpdateChecker.displayVersion(tag),
        changelog = root.stringOf("body").orEmpty(),
        apks = assets.filter { it.url.isNotBlank() },
    )
}

/**
 * GitHub 的 `digest` 是 `"sha256:<hex>"`。这里剥前缀并**验形状**：长度不是 64、或含十六进制以外
 * 的字符，一律当没拿到。
 *
 * 判式收紧的方向是刻意的：宁可退化成"只走官方直链"（见 [downloadCandidates]），
 * 也不能拿一个残缺的哈希去做"相等吗"的比较 —— 那种比较永远返回不等，
 * 于是每条正常下载都会被报成校验失败，而那正是"看起来在工作、其实一直错"的形状。
 */
internal fun sha256Of(digest: String?): String? {
    val value = digest?.trim()?.lowercase()?.removePrefix("sha256:") ?: return null
    return if (value.length == 64 && value.all { it in '0'..'9' || it in 'a'..'f' }) value else null
}

private fun JsonObject.stringOf(key: String): String? = this[key]?.jsonPrimitive?.contentOrNull

private fun kotlinx.serialization.json.JsonElement?.asArrayOrNull() =
    runCatching { this?.jsonArray }.getOrNull()
