package com.venera.compose.data.update

/**
 * 一条 APK 直链的**候选下载顺序**。
 *
 * ## 为什么需要候选，而不是"就用官方"
 *
 * 官方直链在开发机上通（本机实测 `https://github.com/…` HEAD 200 / 0.87s），
 * 但**设备侧不是开发机** —— 14 MB 的 release 资产在国内设备上经常长时间无响应或直接断。
 * 拿不到字节的更新检测等于没有，所以留一条回退。
 *
 * ## 为什么这条回退是可以接受的
 *
 * 因为字节可校验。asset 上带 GitHub 自己算的 `digest`（sha256），落盘后逐字节比对，
 * 不一致就当失败删掉 —— 镜像能篡改"传什么"，篡改不了"我们认哪个哈希"，**前提是哈希不来自它**。
 * 于是有了下面那条收紧的判式。
 *
 * 前缀表取自 2026-10-04 本机 HEAD 实测（同一条 v2.0.3 直链）：
 * `gh-proxy.com` 200 / 1.19s，`ghfast.top` 200 / 1.09s，`ghproxy.net` 200 / 1.69s，
 * 三者返回的 `content_type` 都是 `application/vnd.android.package-archive`；
 * `mirror.ghproxy.com` **连不上（http=000）所以不进表**。
 * 这张表会过期 —— 过期了不改它，就让 [ApkDownloader] 的"每个候选都试过且都失败"那句读数
 * 把域名报出来，下一轮照着读数换。
 */
internal val UPDATE_MIRROR_PREFIXES = listOf(
    "https://gh-proxy.com/",
    "https://ghfast.top/",
    "https://ghproxy.net/",
)

/**
 * 官方在第一位，后面才是镜像。
 *
 * **硬规则（方案文档 §1.2）：拿不到可信 sha256 时，候选表塌成只剩官方。**
 * 没有哈希就没有校验，没有校验就不该把 14 MB 可执行包的来源交给别人的域名 ——
 * 这里宁可"下不动、报得清楚"，也不"下得动但可能装进去一个别人的包"。
 *
 * 另一条收紧：只有 `https://github.com/` 上的 release 直链才配加镜像前缀。
 * 镜像转发的语义就是"帮我取 github 上那个文件"，喂它一条别的 URL 等于把它变成任意代理。
 */
internal fun downloadCandidates(officialUrl: String, sha256: String?): List<String> {
    val official = listOf(officialUrl)
    // 空串与空白也算"没有哈希"：调用侧一旦把缺字段折成 ""，判式就该同样退掉，
    // 不能让它比 null 更宽松 —— 那正好是"字段缺失却照常走镜像"的那条路。
    val mirrorable = !sha256.isNullOrBlank() && officialUrl.startsWith("https://github.com/")
    return if (mirrorable) official + UPDATE_MIRROR_PREFIXES.map { it + officialUrl } else official
}
