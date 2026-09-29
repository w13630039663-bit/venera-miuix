package com.venera.compose.feature.sourcemanage

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.outlined.ImageSearch
import androidx.compose.material3.Icon
import com.venera.compose.components.venera.VeneraTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.venera.compose.components.venera.VeneraCard
import com.venera.compose.gallery.data.SauceNaoAccount
import com.venera.compose.ui.tokens.VeneraTokens
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 「以图搜图（SauceNAO）」—— 紧跟在画廊站点账号那张卡后面。
 *
 * ## 与 Gelbooru 那张卡**不是一类**，卡上必须说清
 *
 * | | Gelbooru | SauceNAO |
 * |---|---|---|
 * | 不配 | 画廊里**一张图都取不到** | **照样能搜**，只是额度小、更容易被限流 |
 * | 凭据 | User ID + API Key 两样 | 只有 API Key 一样 |
 * | 校验 | 有探针 | **刻意不做**，理由见 [SauceNaoAccount] |
 *
 * 所以这一张的措辞是"可选"，不摆成"必须配置否则功能不可用" —— 那是假前提。
 * 也不做登录框 / 注销确认那一套：只有一个字段，就地填就地存。
 *
 * ## Key 用密码框遮着
 *
 * 它是一枚能花钱的东西（付费档按月计费），明文摆在设置页里会被肩读、会被截图。
 * 代价是看不见填对没填对 —— 但第一次真搜的时候站方会说话（"Invalid API key"
 * 原样念出来，见 [com.venera.compose.gallery.data.parseSauceNao]），
 * 比在这里给一个"看起来填好了"的绿色对勾诚实。
 */
@Composable
fun SauceNaoKeyCard() {
    val tokens = VeneraTokens
    val context = LocalContext.current
    val account = remember { SauceNaoAccount.getInstance(context) }
    val hasKey by account.hasKey.collectAsStateWithLifecycle()

    var draft by rememberSaveable { mutableStateOf("") }
    var editing by rememberSaveable { mutableStateOf(false) }

    VeneraCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(tokens.spacing.space7)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(tokens.color.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.ImageSearch,
                        contentDescription = null,
                        tint = tokens.color.onPrimaryContainer,
                        modifier = Modifier.size(22.dp),
                    )
                }
                Spacer(modifier = Modifier.width(tokens.spacing.space6))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "以图搜图（SauceNAO）",
                        fontSize = tokens.type.itemTitle,
                        color = tokens.color.textPrimary,
                    )
                    Text(
                        // 状态直接给结论，不写"已启用/未启用"那种两头话。
                        text = if (hasKey) "已配 API Key：额度更大" else "未配 Key：仍可搜，但额度小、易被限流",
                        fontSize = tokens.type.caption,
                        color = tokens.color.textSecondary,
                    )
                }
                Text(
                    text = "去拿 Key",
                    fontSize = tokens.type.caption,
                    color = tokens.color.primary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(tokens.shape.small))
                        .clickable {
                            runCatching {
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW, android.net.Uri.parse(SauceNaoAccount.KEY_PAGE))
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                                )
                            }
                        }
                        .padding(horizontal = tokens.spacing.space4, vertical = tokens.spacing.space2),
                )
            }

            if (editing) {
                Spacer(modifier = Modifier.height(tokens.spacing.space5))
                VeneraTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    label = "SauceNAO API Key",
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(tokens.spacing.space5))
                Row {
                    Button(
                        onClick = {
                            account.save(draft)
                            draft = ""
                            editing = false
                        },
                        modifier = Modifier.weight(1f),
                    ) { Text("保存") }
                    Spacer(modifier = Modifier.width(tokens.spacing.space4))
                    Button(
                        onClick = {
                            draft = ""
                            editing = false
                        },
                        modifier = Modifier.weight(1f),
                    ) { Text("取消") }
                }
            } else {
                Spacer(modifier = Modifier.height(tokens.spacing.space5))
                Button(
                    onClick = { editing = true },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(if (hasKey) "更换 Key" else "填入 Key") }
                if (hasKey) {
                    Spacer(modifier = Modifier.height(tokens.spacing.space3))
                    Text(
                        text = "清除 Key",
                        fontSize = tokens.type.caption,
                        color = MiuixTheme.colorScheme.error,
                        modifier = Modifier
                            .clip(RoundedCornerShape(tokens.shape.small))
                            .clickable { account.clear() }
                            .padding(horizontal = tokens.spacing.space2, vertical = tokens.spacing.space2),
                    )
                }
            }
        }
    }
}
