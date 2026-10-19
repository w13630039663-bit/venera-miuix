package com.venera.compose.gallery.data

/**
 * 这台手机上的 **Google 账号身份**（2026-10-17 第四轮，用户点了 B 之后加的那一枚）。
 *
 * ## 它不是什么
 *
 * **不是 pixiv 的登录**。pixiv 的 Google 登录发生在它自己的页面里：Google 那一侧的 OAuth client
 * 归站方，`redirect_uri` 白名单只回站方的域；而手机上的原生账号选择器要求**我们自己**是
 * Google OAuth 的 client（还要包名 + 签名 SHA-1 在 Google Cloud 登记过），换回来的是 ID token ——
 * 站方没有任何"拿 ID token 换会话"的端点。三条缺一条都不成立，所以"点一下 Google 就登进 pixiv"
 * 在协议层就不存在。
 *
 * ## 它是什么
 *
 * 一枚**本机身份标记**：用户在系统账号选择器里挑一个 Google 账号，我们把邮箱记下来，
 * 用来在登录弹层里说清"这台机器上的是哪个 Google 账号"（浏览器里那一步会列出它），
 * 将来做云同步一类的东西时也有一个现成的"这台机器属于谁"。不取 token、不联网、不碰票据。
 *
 * ## 为什么是一颗契约而不是直接读盘
 *
 * 判据与装配的分法照本仓口径：这颗只声明**问什么**，落盘在 `android/GoogleIdentityAndroid`
 * （那里才允许出现 `AndroidKeyValueStore`），界面只认这颗接口 —— 于是
 * `BusinessApiBoundaryTest` 那三条（UI 不许 import 实现类 / 不许直连存储）一条都不会破。
 *
 * 桌面那一侧没有账号框架，`GalleryPorts.googleIdentity` 默认 null（"这一侧没有这一档"，
 * 与 `pixiv` / `pixivAccount` 同一条口径）；界面拿到 null 就把这一行整块收起来，不摆半件。
 */
interface LocalGoogleIdentity {

    /** 记下来的那个邮箱；没有就是 null（"没选过"，不冒充任何别的结论）。 */
    fun email(): String?

    /** 记下来（用户在系统选择器里选完之后）。 */
    fun remember(email: String)

    /** 清掉（用户按"清除"）。 */
    fun forget()
}
