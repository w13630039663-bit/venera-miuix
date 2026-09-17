package com.venera.compose.feature.sourcemanage

/**
 * 仓库条目统一使用 [com.venera.compose.source.RepoIndexEntry]，
 * 其字段与官方 `index.json` 逐字对齐（name / fileName / key / version / description）。
 */
typealias RepoSourceItem = com.venera.compose.source.RepoIndexEntry

sealed class SourceSettingItem(
    open val key: String,
    open val title: String
) {
    data class Input(
        override val key: String,
        override val title: String,
        val value: String,
        val defaultValue: String = "",
        val validator: String? = null
    ) : SourceSettingItem(key, title)

    data class Select(
        override val key: String,
        override val title: String,
        val value: String,
        val defaultValue: String = "",
        val options: List<SelectOption>
    ) : SourceSettingItem(key, title)

    data class Switch(
        override val key: String,
        override val title: String,
        val value: Boolean,
        val defaultValue: Boolean = false
    ) : SourceSettingItem(key, title)

    data class Callback(
        override val key: String,
        override val title: String,
        val buttonText: String = "点击执行"
    ) : SourceSettingItem(key, title)
}

data class SelectOption(
    val value: String,
    val text: String
)

/**
 * 源的账号体系状态，字段与官方 `AccountConfig` 逐项对齐。
 *
 * 官方契约（见 `lib/foundation/comic_source/parser.dart::_loadAccountConfig`）：
 * - 账密登录：`account.login(account, pwd)`
 * - 网页登录：`account.loginWithWebview.url` / `.checkStatus(url,title)` / `.onLoginSuccess()`
 * - Cookie 直填：`account.loginWithCookies.fields` / `.validate(cookies)`
 * - 注册外链：`account.registerWebsite`
 * - 注销：`account.logout()`
 */
data class SourceAccountInfo(
    val hasAccount: Boolean = false,
    val isLogged: Boolean = false,
    val username: String? = null,
    val infoItems: List<Pair<String, String>> = emptyList(),

    /** 支持账号密码登录（官方 `account.login` 存在） */
    val supportsPasswordLogin: Boolean = false,

    /** 网页登录地址（官方 `account.loginWithWebview.url`） */
    val loginWebsite: String? = null,

    /** 注册外链（官方 `account.registerWebsite`），部分源用 getter 动态返回 */
    val registerWebsite: String? = null,

    /** 手填 cookie 的字段名（官方 `account.loginWithCookies.fields`） */
    val cookieFields: List<String> = emptyList()
) {
    /** 是否支持内嵌 WebView 网页登录 */
    val supportsWebLogin: Boolean get() = !loginWebsite.isNullOrBlank()

    /** 是否支持手填 cookie 登录 */
    val supportsCookieLogin: Boolean get() = cookieFields.isNotEmpty()

    /** 三种登录方式都不支持 —— UI 应提示「该源未提供登录方式」 */
    val loginUnavailable: Boolean
        get() = !supportsPasswordLogin && !supportsWebLogin && !supportsCookieLogin
}

data class SourceConfigBundle(
    val sourceKey: String,
    val sourceName: String,
    val version: String,
    val isJsSource: Boolean,
    val settings: List<SourceSettingItem> = emptyList(),
    val accountInfo: SourceAccountInfo = SourceAccountInfo()
)
