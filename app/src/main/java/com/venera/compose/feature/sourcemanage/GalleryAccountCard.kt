package com.venera.compose.feature.sourcemanage

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Logout
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.venera.compose.components.venera.VeneraCard
import com.venera.compose.gallery.data.DanbooruAccount
import com.venera.compose.ui.tokens.VeneraTokens
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 「画廊站点账号」—— 漫画源管理页顶部那张卡。
 *
 * ## 为什么摆在漫画源管理里
 *
 * Danbooru / yande.re 这两站是**原生客户端**（直接打官方 JSON API，不走 JS 源脚本），
 * 所以它们没有"源"可以挂在那个列表里。但账号这一档事在用户心里与"源"是同一件事
 * （"我要给某个站点配个号"），而且整仓只有这一页是"各站点的配置"的落点 ——
 * 摆在这儿最容易被找到，也不用在设置首页多开一条只放一个条目的入口。
 *
 * ## 登录换来的是什么（只有一件，其余都别指望）
 *
 * **一次能搜几枚标签**：按账号等级算 —— 匿名与 Member **都是 2 枚**，Gold 6 枚，
 * Platinum 及以上不限。**免费注册不放宽这 2 枚**，所以卡上必须写清楚，
 * 否则"登录了怎么还是 2 枚"会变成一次没必要的投诉。
 *
 * ⚠️ **登录不解锁成人图**。这里从前写着"成人分级匿名看不到、登录后可见"，那是个错判：
 * 2026-09-26 复核，`cat rating:e` 匿名取回 5/5 都带图（成人分级本来就不挡）；
 * 真正被挡的是**带 `loli` / `shota` 的条目**，而站方对 Member 与未登录**一视同仁地封**
 * （wiki `help:censored_tags`：要 Gold / Builder / Platinum）。详见
 * [com.venera.compose.gallery.data.DanbooruClient.CENSORED_TAGS]。
 * 这条结论要是不写在这儿，用户会为了一个"登录就能看到"的错觉去注册、去验证邮箱。
 *
 * ## 凭据怎么填
 *
 * 用户名 + **API Key**（不是登录密码）。站方的两条正规路之一 —— HTTP Basic
 * `base64(用户名:API Key)` —— 走的是这一条。卡里直接给一枚「去生成 API Key」，
 * 落点是站方的个人页（`danbooru.donmai.us/profile`），Key 就在那一页生成。
 */
@Composable
fun GalleryAccountCard() {
    val tokens = VeneraTokens
    val context = LocalContext.current
    val account = remember { DanbooruAccount.getInstance(context) }
    val identity by account.identity.collectAsStateWithLifecycle()

    var loginOpen by remember { mutableStateOf(false) }
    var logoutConfirm by remember { mutableStateOf(false) }

    // 打开这张卡顺手**校准一次等级**：这里显示的是登录那一刻记下的值，而站方会变
    // （最日常的一条：注册走了 VPN / 代理 → Restricted(10)，验证邮箱后才升 Member）。
    // 成功了下面的 StateFlow 自己会把文字刷成新值；失败**不提示也不注销** ——
    // 屏上继续显示上次问到的等级，那是当时为真的事实，而一次网络抽风不该把人登出。
    LaunchedEffect(Unit) { account.refresh() }

    VeneraCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(tokens.spacing.space7)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MiuixTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.AccountCircle,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Spacer(modifier = Modifier.width(tokens.spacing.space5))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "画廊站点账号",
                        fontSize = tokens.type.itemTitle,
                        fontWeight = tokens.type.weightBold,
                        color = tokens.color.textPrimary,
                    )
                    Text(
                        text = "Danbooru（yande.re 不需要账号）",
                        fontSize = tokens.type.overline,
                        color = tokens.color.textTertiary,
                    )
                }
            }

            Spacer(modifier = Modifier.height(tokens.spacing.space5))
            Text(
                text = "登录换来的是「一次可搜的标签数」（按等级：会员与匿名都是 2 枚，Gold 6 枚，" +
                    "Platinum 及以上不限）。它不解锁图：带 loli / shota 的条目站方对会员也一律封，要 Gold 起。",
                fontSize = tokens.type.caption,
                color = tokens.color.textSecondary,
            )

            Spacer(modifier = Modifier.height(tokens.spacing.space5))

            val signedIn = identity
            Surface(
                shape = RoundedCornerShape(tokens.shape.small),
                color = tokens.color.primaryContainer.copy(alpha = 0.35f),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { if (signedIn == null) loginOpen = true else logoutConfirm = true },
            ) {
                Row(
                    modifier = Modifier.padding(vertical = tokens.spacing.space5),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = if (signedIn == null) Icons.Outlined.AccountCircle else Icons.Outlined.Logout,
                        contentDescription = null,
                        tint = tokens.color.primary,
                        modifier = Modifier.size(tokens.spacing.chipIconSize),
                    )
                    Spacer(modifier = Modifier.width(tokens.spacing.space4))
                    Text(
                        text = if (signedIn == null) {
                            "登录 Danbooru"
                        } else {
                            "已登录 ${signedIn.name}（${danbooruLevelLabel(signedIn.level)}）· 点此注销"
                        },
                        fontSize = tokens.type.overline,
                        fontWeight = tokens.type.weightMedium,
                        color = tokens.color.primary,
                    )
                }
            }
        }
    }

    if (loginOpen) {
        DanbooruLoginDialog(
            account = account,
            onDismiss = { loginOpen = false },
        )
    }

    if (logoutConfirm) {
        Dialog(onDismissRequest = { logoutConfirm = false }) {
            VeneraCard(modifier = Modifier.fillMaxWidth().padding(tokens.spacing.space8)) {
                Column(modifier = Modifier.padding(tokens.spacing.space9)) {
                    Text(
                        text = "注销 Danbooru 账号？",
                        fontSize = tokens.type.itemTitle,
                        fontWeight = tokens.type.weightBold,
                        color = tokens.color.textPrimary,
                    )
                    Spacer(modifier = Modifier.height(tokens.spacing.space5))
                    Text(
                        text = "本机保存的用户名与 API Key 会被删掉，一次可搜的标签数回到匿名的 2 枚" +
                            "（会员本来也是 2 枚）。带 loli / shota 的图仍然看不到 —— 那与登不登录无关。",
                        fontSize = tokens.type.body,
                        color = tokens.color.textSecondary,
                    )
                    Spacer(modifier = Modifier.height(tokens.spacing.space7))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        TextButton(onClick = { logoutConfirm = false }) { Text("取消") }
                        Spacer(modifier = Modifier.width(tokens.spacing.space3))
                        TextButton(
                            onClick = {
                                account.signOut()
                                logoutConfirm = false
                                Toast.makeText(context, "已注销 Danbooru", Toast.LENGTH_SHORT).show()
                            },
                        ) {
                            Text("注销", color = tokens.color.actionFavorite)
                        }
                    }
                }
            }
        }
    }
}

/**
 * 登录弹窗：用户名 + API Key。
 *
 * 两个字段都必须年落**站方核实过**才算登录成功（[DanbooruAccount.signIn] 走 `/profile.json`）：
 * 只把输入存下来就是"看起来登录了、其实每笔请求还是匿名"——那种假登录比不登录更坏。
 */
@Composable
private fun DanbooruLoginDialog(
    account: DanbooruAccount,
    onDismiss: () -> Unit,
) {
    val tokens = VeneraTokens
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // 用户名回填上次那一个（换 Key 时不用重敲名字）；API Key 一律留空，不预填密钥。
    var login by remember { mutableStateOf(account.loginName) }
    var apiKey by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }

    fun openApiKeyPage() {
        runCatching {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(DanbooruAccount.API_KEY_PAGE)),
            )
        }.onFailure {
            Toast.makeText(context, "打不开浏览器：${it.message}", Toast.LENGTH_SHORT).show()
        }
    }

    Dialog(onDismissRequest = { if (!busy) onDismiss() }) {
        VeneraCard(modifier = Modifier.fillMaxWidth().padding(tokens.spacing.space8)) {
            Column(modifier = Modifier.padding(tokens.spacing.space9)) {
                Text(
                    text = "登录 Danbooru",
                    fontSize = tokens.type.itemTitle,
                    fontWeight = tokens.type.weightBold,
                    color = tokens.color.textPrimary,
                )
                Spacer(modifier = Modifier.height(tokens.spacing.space3))
                Text(
                    text = "填站方的用户名与 API Key（不是登录密码）。" +
                        "Key 在 Danbooru 个人页生成，本站只把它存在本机、只用于这一站的请求。",
                    fontSize = tokens.type.caption,
                    color = tokens.color.textSecondary,
                )
                Spacer(modifier = Modifier.height(tokens.spacing.space6))

                OutlinedTextField(
                    value = login,
                    onValueChange = { login = it },
                    label = { Text("用户名") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(tokens.spacing.space5))
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = { Text("API Key") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(modifier = Modifier.height(tokens.spacing.space3))
                TextButton(onClick = { openApiKeyPage() }) {
                    Icon(
                        imageVector = Icons.Outlined.Link,
                        contentDescription = null,
                        modifier = Modifier.size(tokens.spacing.chipIconSize),
                    )
                    Spacer(modifier = Modifier.width(tokens.spacing.space3))
                    Text("去生成 API Key")
                }

                Spacer(modifier = Modifier.height(tokens.spacing.space5))
                Button(
                    onClick = {
                        if (login.isBlank() || apiKey.isBlank()) {
                            Toast.makeText(context, "用户名与 API Key 都不能为空", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        busy = true
                        scope.launch {
                            account.signIn(login, apiKey)
                                .onSuccess { identity ->
                                    busy = false
                                    onDismiss()
                                    Toast.makeText(
                                        context,
                                        "已登录 ${identity.name}" +
                                            "（${danbooruLevelLabel(identity.level)}）",
                                        Toast.LENGTH_SHORT,
                                    ).show()
                                }
                                .onFailure { e ->
                                    busy = false
                                    // 原样转达站方的答案（401 / 403 / 网络错误各自可分辨），
                                    // 不统一糊成一句"登录失败"。
                                    Toast.makeText(
                                        context,
                                        e.message ?: "登录失败",
                                        Toast.LENGTH_LONG,
                                    ).show()
                                }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !busy,
                ) {
                    if (busy) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(tokens.spacing.loaderInline),
                            color = Color.White,
                        )
                    } else {
                        Text("登录")
                    }
                }
            }
        }
    }
}

/**
 * 站方等级 → 人话。
 *
 * 只翻**已经核实过**的那几档（`/profile.json` 回的 `level`）：20 Member、30 Gold、31+
 * Platinum 及以上，其余（0/10）归到"受限账号"—— 受限账号的行为与匿名一致
 * （站方 `help:users` 里写明"验证邮箱前与未注册用户相同"），所以标签上限也是 2。
 */
private fun danbooruLevelLabel(level: Int): String = when {
    level >= 31 -> "Platinum 及以上"
    level >= 30 -> "Gold"
    level >= 20 -> "Member"
    else -> "受限账号"
}
