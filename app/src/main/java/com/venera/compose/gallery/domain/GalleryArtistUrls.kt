package com.venera.compose.gallery.domain

/**
 * 站方/第三方响应里**哪些地址可用**（批次 L，判据层的唯一一份）。
 *
 * 两件事各一条，都是"猜错就静默什么都不显示"的那类，所以不收在取数层里：
 *
 * - [split]：danbooru 系的画师地址有 `urls`（字符串数组）与 `url_string`（空白分隔的一整串）两种写法，
 *   取数层把数组**先并成串**再走这里，两种形态就共用同一条切分口径。
 *   串里以 `-` 打头的那条**不是脏数据**，是站方的"这条已停用"标记（源码里 `ArtistURL.parse_prefix`
 *   就是干这个的）—— 原样收下等于把站方作废的地址摆到画师行上。
 * - [avatarUrl]：pixiv 给的两档头像只差尺寸（实测同一位是 `…_50.png` / `…_170.png`）。
 *   拿不到交 **null**，不交空串：空串进 Coil 是"什么都不显示且没有原因"，
 *   null 才让调用方有机会换成首字母占位。
 */
object GalleryArtistUrls {

    private val BLANK = Regex("\\s+")

    /** 切出一串里的地址；`-` 打头（站方标停用）与空白的都不进结果，重复只留一次。 */
    fun split(urlString: String?): List<String> {
        if (urlString.isNullOrBlank()) return emptyList()
        return urlString.split(BLANK)
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("-") }
            .distinct()
    }

    /** 两档里挑可用的那档（大的优先）。不是绝对地址的一律算"没有"。 */
    fun avatarUrl(imageBig: String?, image: String?): String? =
        listOfNotNull(imageBig, image).firstOrNull {
            it.startsWith("http://", ignoreCase = true) || it.startsWith("https://", ignoreCase = true)
        }
}
