package com.venera.compose.gallery.ui

import android.accounts.Account
import android.accounts.AccountManager
import android.app.Activity
import android.content.Intent
import android.os.Bundle
import java.util.ArrayList

/**
 * 系统账号选择器的**两件纯动作**：拉起它、读回它交的那个人。
 *
 * ## 为什么这一件只剩这两件事
 *
 * "记下来"那半边搬进了数据层（`LocalGoogleIdentity` / `GoogleIdentityAndroid`）：
 * 本仓有一条边界判据（`BusinessApiBoundaryTest`）不许 UI 目录 import 业务实现类、
 * 也不许直连 `AndroidKeyValueStore` —— 而这颗件是界面目录里的，落盘就不该在这里做。
 * 于是这里只剩**Android 框架调用**（`AccountManager` + `Intent`），没有第二套判据。
 *
 * ## 它是什么，以及它**不是**什么
 *
 * 拉起的是系统自带的账号选择器，选完拿到的是**邮箱那一串**，仅此而已。它**不是** pixiv 的登录 ——
 * 逐条理由（client 归属、`redirect_uri` 白名单、站方没有 ID token 换会话的端点）写在
 * `LocalGoogleIdentity` 的头注上，界面那句"不等于 pixiv 登录"由 `PixivLoginPathsTest` 钉着。
 *
 * ## 为什么不用 Credential Manager
 *
 * 那一套能给真 ID token，但前提是有一枚 Google OAuth client id（还要包名+签名在 Google Cloud
 * 登记过）—— 本仓没有，也不该替用户申请一个。系统账号选择器零配置就能拉起手机里那几个
 * Google 账号，正好对上"能拉起手机的 Google 账号"这一条要求，而它不假装能换票据。
 */
internal object GoogleAccountIdentity {

    /** Google 账号在 `AccountManager` 里的 type 串。 */
    private const val GOOGLE_ACCOUNT_TYPE = "com.google"

    /**
     * 拉起系统账号选择器的意图；**null = 这台机器起不来**（没有账号框架/没有账号管理界面，
     * 多半是没装 Google 服务）。调用方拿到 null 要念一句实话，不许静默吞掉。
     *
     * ⚠️ 两个坑，都是本仓用两轮编译换来的：
     * 1. 它是 **Java 静态方法** —— Kotlin **不许经实例调静态**，写成
     *    `AccountManager.get(ctx).newChooseAccountIntent(…)` 报的是 "Unresolved reference"，
     *    一句看起来像"这个 API 不存在"的话（拿 javac 编同一条却只报 deprecated，很容易被骗）。
     *    所以只能 `AccountManager.xxx(…)`，`context` 也就不必传进来。
     * 2. `AccountManager` 里这一族有**两个重载**（8 参收 `ArrayList<Account>`、7 参收 `List<Account>`），
     *    全传裸 `null` 判不出该走哪一支 ⇒ 显式标类型，并且走带 `alwaysPromptForAccount` 的 8 参那支
     *    （不强制提示的话，设备上只有一个账号时它会静默选中，用户以为按了没反应）。
     */
    @Suppress("DEPRECATION")
    fun pickerIntent(): Intent? = runCatching {
        AccountManager.newChooseAccountIntent(
            /* selectedAccount = */ null as Account?,
            /* allowableAccounts = */ null as ArrayList<Account>?,
            /* allowableAccountTypes = */ arrayOf(GOOGLE_ACCOUNT_TYPE),
            /* alwaysPromptForAccount = */ true,
            /* descriptionOverrideText = */ DESCRIPTION,
            /* addAccountAuthTokenType = */ null as String?,
            /* addAccountRequiredFeatures = */ null as Array<String>?,
            /* addAccountOptions = */ null as Bundle?,
        )
    }.getOrNull()

    /** 从选择器回来的结果里取邮箱；取消（`RESULT_CANCELED`）或空值都回 null。 */
    fun emailFrom(resultCode: Int, data: Intent?): String? {
        if (resultCode != Activity.RESULT_OK || data == null) return null
        return data.getStringExtra(AccountManager.KEY_ACCOUNT_NAME)?.trim()?.takeIf { it.isNotBlank() }
    }

    /** 选择器里那句说明（系统对话框上那一行，用户看得见）。 */
    private const val DESCRIPTION = "选一个 Google 账号：只用来标记这台机器上的身份，不等于 pixiv 登录"
}
