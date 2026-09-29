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
import com.venera.compose.components.venera.VeneraTextButton
import com.venera.compose.components.venera.VeneraTextField
import androidx.compose.material3.Icon
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
import com.venera.compose.gallery.data.GelbooruAccount
import com.venera.compose.ui.tokens.VeneraTokens
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 「画廊站点账号」—— 现在挂在**设置 → 画廊**的「账号与密钥」那一组。
 *
 * ## 为什么搬了地方
 *
 * 从前它寄在**漫画源管理**页顶部，理由是"这两站没有源可挂，而整仓只有那一页是各站点的配置"。
 * 2026-09-29 用户点名要单独的画廊设置，那一页的主题本来就一直是 **JS 源脚本**
 * （Gelbooru / yande.re 是原生客户端，压根不在那份源清单里）—— 寄居的理由没了。
 * 类还留在这个包，只为少动一处 import；要看它摆在哪，去 `GallerySettings`。
 *
 * ## ⚠️ Gelbooru 的账号是**必需**，不是加分
 *
 * 这一条与从前 Danbooru 那套**根本不同**，卡上必须写在最显眼处：
 * 它的 DAPI **匿名一律 401**（2026-09-26 实测 posts 与 tag 两条端点都不例外），
 * 所以**没配账号时 Gelbooru 在画廊里一张图都取不到**。
 * 从前那张卡的文案是"登录换来的是「一次可搜的标签数」"—— 那个说法在这里是**错的**：
 * 不配就连基本取图都不成立。所以这里的措辞是"配置"而不是"登录之后更好"。
 *
 * yande.re 那一路**不需要账号**，照旧能用 —— 卡上写明，免得用户以为整个画廊都瘫了。
 *
 * ## 凭据怎么填
 *
 * **User ID（纯数字）+ API Key**，两样都在站方账号选项页。
 * ⚠️ 这与 Danbooru 那套（用户名 + API Key 走 HTTP Basic）不是一回事：
 * Gelbooru 是把这两个值当 **query 参数**发出去的（`&api_key=&user_id=`）。
 * 所以输入框是"User ID"而不是"用户名" —— 填用户名会 401，而站方**不会告诉你哪里错了**
 * （实测三种错法回的都是同一个空 body 的 401）。卡里给一枚「去账号页拿 Key」。
 */
@Composable
fun GalleryAccountCard() {
    val tokens = VeneraTokens
    val context = LocalContext.current
    val account = remember { GelbooruAccount.getInstance(context) }
    val identity by account.identity.collectAsStateWithLifecycle()

    var loginOpen by remember { mutableStateOf(false) }
    var logoutConfirm by remember { mutableStateOf(false) }

    // 打开这张卡顺手**验一次凭据**还在不在有效：站方的 Key 可以被吊销、账号也可能被停。
    // 成功了下面的 StateFlow 自己会刷新；失败**不提示也不注销** ——
    // 一次网络抽风不该把用户的配置抹掉，那比留着更糟（他得重新去站上找 Key）。
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
                        text = "Gelbooru 必须配置（yande.re 不需要账号）",
                        fontSize = tokens.type.overline,
                        color = tokens.color.textTertiary,
                    )
                }
            }

            Spacer(modifier = Modifier.height(tokens.spacing.space5))
            Text(
                text = "Gelbooru 的接口要求凭据：不配置就一张图都取不到（不是少几档权限，是完全用不了）。" +
                    "配置后两站都能正常出图 —— 它不改变能看到什么内容，" +
                    "四个分级（一般 / 敏感 / 存疑 / 露骨）照常都能看。",
                fontSize = tokens.type.caption,
                color = tokens.color.textSecondary,
            )

            Spacer(modifier = Modifier.height(tokens.spacing.space5))

            val configured = identity
            Surface(
                shape = RoundedCornerShape(tokens.shape.small),
                color = tokens.color.primaryContainer.copy(alpha = 0.35f),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { if (configured == null) loginOpen = true else logoutConfirm = true },
            ) {
                Row(
                    modifier = Modifier.padding(vertical = tokens.spacing.space5),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = if (configured == null) Icons.Outlined.AccountCircle else Icons.Outlined.Logout,
                        contentDescription = null,
                        tint = tokens.color.primary,
                        modifier = Modifier.size(tokens.spacing.chipIconSize),
                    )
                    Spacer(modifier = Modifier.width(tokens.spacing.space4))
                    Text(
                        text = if (configured == null) {
                            "配置 Gelbooru 账号"
                        } else {
                            "已配置 User ID ${configured.userId} · 点此清除"
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
        GelbooruLoginDialog(
            account = account,
            onDismiss = { loginOpen = false },
        )
    }

    if (logoutConfirm) {
        Dialog(onDismissRequest = { logoutConfirm = false }) {
            VeneraCard(modifier = Modifier.fillMaxWidth().padding(tokens.spacing.space8)) {
                Column(modifier = Modifier.padding(tokens.spacing.space9)) {
                    Text(
                        text = "清除 Gelbooru 账号？",
                        fontSize = tokens.type.itemTitle,
                        fontWeight = tokens.type.weightBold,
                        color = tokens.color.textPrimary,
                    )
                    Spacer(modifier = Modifier.height(tokens.spacing.space5))
                    Text(
                        text = "本机保存的 User ID 与 API Key 会被删掉。" +
                            "⚠️ 删掉之后 Gelbooru 就完全不能用了（它的接口要求凭据，匿名一律被拒）——" +
                            "画廊里只剩 yande.re 那一路出图。",
                        fontSize = tokens.type.body,
                        color = tokens.color.textSecondary,
                    )
                    Spacer(modifier = Modifier.height(tokens.spacing.space7))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        VeneraTextButton(text = "取消", onClick = { logoutConfirm = false })
                        Spacer(modifier = Modifier.width(tokens.spacing.space3))
                        VeneraTextButton(
                            text = "清除",
                            destructive = true,
                            onClick = {
                                account.signOut()
                                logoutConfirm = false
                                Toast.makeText(context, "已清除 Gelbooru 账号", Toast.LENGTH_SHORT).show()
                            },
                        )
                    }
                }
            }
        }
    }
}

/**
 * 配置弹窗：**User ID + API Key**。
 *
 * 两样都必须经**站方核实过**才算配置成功（[GelbooruAccount.signIn] 会实打一次最小请求）：
 * 只把输入存下来就是"看起来配好了、其实每笔请求还是被 401 拒" —— 那种假配置比不配更坏，
 * 因为用户会以为是自己网络的问题。
 *
 * ⚠️ 第一个框是 **User ID**（纯数字），不是用户名。这一条与旧实现不同，
 * 也是用户最容易填错的地方：填了用户名会 401，而站方**不会说明是哪个字段错**
 * （实测三种错法回的都是同一个空 body 的 401），所以标签与提示都要点明"数字"。
 */
@Composable
private fun GelbooruLoginDialog(
    account: GelbooruAccount,
    onDismiss: () -> Unit,
) {
    val tokens = VeneraTokens
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // User ID 回填上次那个（换 Key 时不用重敲）；API Key 一律留空，不预填密钥。
    var userId by remember { mutableStateOf(account.loginUserId) }
    var apiKey by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }

    fun openAccountPage() {
        runCatching {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(GelbooruAccount.ACCOUNT_PAGE)),
            )
        }.onFailure {
            Toast.makeText(context, "打不开浏览器：${it.message}", Toast.LENGTH_SHORT).show()
        }
    }

    Dialog(onDismissRequest = { if (!busy) onDismiss() }) {
        VeneraCard(modifier = Modifier.fillMaxWidth().padding(tokens.spacing.space8)) {
            Column(modifier = Modifier.padding(tokens.spacing.space9)) {
                Text(
                    text = "配置 Gelbooru",
                    fontSize = tokens.type.itemTitle,
                    fontWeight = tokens.type.weightBold,
                    color = tokens.color.textPrimary,
                )
                Spacer(modifier = Modifier.height(tokens.spacing.space3))
                Text(
                    text = "填站方的 User ID 与 API Key（都在账号选项页，User ID 是一串数字、不是用户名）。" +
                        "本站只把它们存在本机、只用于这一站的请求。",
                    fontSize = tokens.type.caption,
                    color = tokens.color.textSecondary,
                )
                Spacer(modifier = Modifier.height(tokens.spacing.space6))

                VeneraTextField(
                    value = userId,
                    onValueChange = { userId = it },
                    label = "User ID（数字）",
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(tokens.spacing.space5))
                VeneraTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = "API Key",
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(modifier = Modifier.height(tokens.spacing.space3))
                TextButton(onClick = { openAccountPage() }) {
                    Icon(
                        imageVector = Icons.Outlined.Link,
                        contentDescription = null,
                        modifier = Modifier.size(tokens.spacing.chipIconSize),
                    )
                    Spacer(modifier = Modifier.width(tokens.spacing.space3))
                    Text("去账号页拿 Key")
                }

                Spacer(modifier = Modifier.height(tokens.spacing.space5))
                Button(
                    onClick = {
                        if (userId.isBlank() || apiKey.isBlank()) {
                            Toast.makeText(context, "User ID 与 API Key 都不能为空", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        busy = true
                        scope.launch {
                            account.signIn(userId, apiKey)
                                .onSuccess { identity ->
                                    busy = false
                                    onDismiss()
                                    Toast.makeText(
                                        context,
                                        "已配置 Gelbooru（User ID ${identity.userId}）",
                                        Toast.LENGTH_SHORT,
                                    ).show()
                                }
                                .onFailure { e ->
                                    busy = false
                                    // 原样转达站方的答案（401 / 403 / 网络错误各自可分辨），
                                    // 不统一糊成一句"配置失败"。
                                    Toast.makeText(
                                        context,
                                        e.message ?: "配置失败",
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
                        Text("保存")
                    }
                }
            }
        }
    }
}
