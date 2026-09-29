package com.venera.compose.gallery.domain

import com.venera.compose.gallery.data.GalleryPost

/** 网格列数。AUTO = 按屏宽自己定（大屏 3 列、手机 2 列，那是 2026-09 真机定下来的口径）。 */
enum class GalleryColumnMode {
    AUTO, TWO, THREE
}

/**
 * 墙上每一格摆哪一档图。
 *
 * ⚠️ 这一档只改**墙上摆的那一档**，不改大图页的三档叠画（开门档→中档→原图那套另有判据，
 * 见 [GalleryPost] 头注的地址表）。
 */
enum class GalleryPreviewQuality {
    /** 站方的 preview/thumbnail 档（两站实测都是 jpg，几十 KB 一张）。 */
    PREVIEW,

    /** 站方的 sample 档：清楚得多，但一屏几十张就是几十 MB 级流量。 */
    LARGE,
}

/** 保存到相册时文件名怎么起。 */
enum class GallerySaveNaming {
    /** `站名键-id`（本功能最初的写法，也是默认值）。 */
    SITE_ID,

    /** 站方原文件名（实测多半是一串哈希，不认人，但能与站上一一对上）。 */
    ORIGINAL,

    /** `画师-id`：画师取不到时退化成 [SITE_ID]，**不编一个名字**。 */
    ARTIST_ID,

    /** 保存那一刻的时间戳。 */
    TIMESTAMP,
}

/**
 * 画廊设置的三个"档位型"偏好合起来放这儿，外加两处**纯判据**（能上单测的那种）。
 *
 * 为什么判据不留在 UI：本项目单元测试没有 Robolectric，写进 composable 的算式就永远没人钉。
 */
object GallerySettingsModel {

    /** 墙上到底摆几列。 */
    fun gridColumns(mode: GalleryColumnMode, wide: Boolean): Int = when (mode) {
        GalleryColumnMode.AUTO -> if (wide) 3 else 2
        GalleryColumnMode.TWO -> 2
        GalleryColumnMode.THREE -> 3
    }

    /**
     * 墙上那一格取哪个地址。取不到 LARGE 档时退回 preview，**不给空地址**（空地址=灰卡）。
     *
     * 视频条目**永远只吃静帧**（与大图页底图同一把判据 [GalleryPost.videoPosterUrl]）：
     * 站方不给更小的转码档，Gelbooru 的 `sample_url` 还是空串、翻译时兜底成了原片 mp4，
     * "清晰预览"那一档照字面去取 largeUrl 就等于让一屏卡片各下一整片 16~26 MB 的视频
     * 再全部解码失败（2026-09-29 真机闪退的一条成因）。
     */
    fun wallUrl(post: GalleryPost, quality: GalleryPreviewQuality): String = when {
        post.isVideo -> post.videoPosterUrl
        quality == GalleryPreviewQuality.LARGE ->
            post.largeUrl.takeIf { it.isNotBlank() } ?: post.previewUrl
        else -> post.previewUrl
    }

    /**
     * 文件名（不含目录，含扩展名）。
     *
     * 扩展名一律取站方给的那一档（[GalleryPost.fileExt]），拿不到才落 `bin` —— 与我们声明的
     * MIME 同源，别让相册里出现一张"名字叫 jpg、其实是 mp4"的东西。
     */
    fun fileName(post: GalleryPost, naming: GallerySaveNaming, timestamp: String?): String {
        val ext = post.fileExt.ifBlank { "bin" }
        val base = when (naming) {
            GallerySaveNaming.SITE_ID -> "${post.site.routeKey}-${post.id}"
            // 原名自己就带扩展名，而下面统一要拼 `.$ext`（MIME 按这一档声明，两处必须同源）——
            // 所以先摘掉它那份，否则会拼出 `hash.png.png`。
            GallerySaveNaming.ORIGINAL ->
                originalName(post.fileUrl)?.removeSuffix(".$ext") ?: "${post.site.routeKey}-${post.id}"

            GallerySaveNaming.ARTIST_ID -> {
                val artist = sanitize(post.author)
                if (artist.isEmpty()) "${post.site.routeKey}-${post.id}" else "${artist}_${post.id}"
            }
            GallerySaveNaming.TIMESTAMP -> requireNotNull(timestamp) { "时间戳命名必须给时间戳" }
        }
        return "$base.$ext"
    }

    /** 原图地址里的最后一段（去掉查询串）。地址里没有文件名时返回 null，由调用方退化。 */
    private fun originalName(fileUrl: String): String? = fileUrl
        .substringBefore('?')
        .substringAfterLast('/')
        .takeIf { it.contains('.') && !it.startsWith(".") }

    /**
     * 文件系统安全字符集：留字母数字与 `-`、`_`、`.`，其余（含 `/`、`:`、空格、中文标点）换成 `_`。
     *
     * 画师名里出现 `/` 不是假设 —— booru 的标签本身允许一些非常规字符，而这一串会直接进
     * `File(dir, name)` 与 MediaStore 的 `DISPLAY_NAME`。
     */
    private fun sanitize(raw: String): String = raw
        .trim()
        .replace(Regex("[^A-Za-z0-9._-]+"), "_")
        .trim('_', '.')
}
