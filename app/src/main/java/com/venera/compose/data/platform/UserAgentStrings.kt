package com.venera.compose.data.platform

/**
 * 出站请求的两条 User-Agent 串 —— **单一定义处**。
 *
 * 存在的理由：这两枚常量原先住在 `data/network/UserAgentPolicy`（那颗吃 `Context` 与
 * `AndroidKeyValueStore`，桌面不可编），而画廊的 `GelbooruClient` 与 `SauceNao` 要引它们 ——
 * 于是那两颗客户端被钉在 Android 面上。声明本身是两枚 `const val`，与平台无关。
 *
 * ⚠️ 两枚**不许合并**（这条不变量由 `ImageHeaderPolicyUaSourceTest` 钉着，不靠注释）：
 *
 * - [DEFAULT_USER_AGENT] = 移动端 Chrome 串，走漫画源与网页；
 * - [APP_USER_AGENT] = 明确表明自己不是浏览器的产品串，走图床与部分 DAPI。
 *
 * `UserAgentPolicy` 侧仍保留同名 `const val` 别名（成员名一字未改），所以六处既有调用点
 * 与那颗 UI 文件（`gallery/ui/GalleryVideoViewer.kt`）都不动 —— 桌面端 UI 零改动这条约束
 * 逼出来的别名，不是兼容包袱。
 */
object UserAgentStrings {

    const val DEFAULT_USER_AGENT =
        "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36 Venera/1.0.0"

    /**
     * 我们自己的**产品串**，非浏览器身份。两处图站规则与 Gelbooru 接口侧都必须发同一条 ——
     * 这条不变量此前只有注释级证据（`ImageHeaderPolicy` 里那句"UA 一并带上只为与接口侧
     * `GelbooruClient.API_USER_AGENT` 保持一致"），改一处另一处不会报错，症状是那一站整站取不到图。
     * 现在两边引这一枚常量。
     *
     * ⚠️ 与 [DEFAULT_USER_AGENT] 是两回事：那枚是移动端 Chrome 串（走漫画源与网页），
     * 这枚是"明确表明自己不是浏览器"的串（走图床与部分 DAPI）。别合并。
     */
    const val APP_USER_AGENT = "Venera/1.0 (Android)"
}
