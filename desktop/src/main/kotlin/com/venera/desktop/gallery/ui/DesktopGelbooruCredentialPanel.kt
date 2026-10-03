package com.venera.desktop.gallery.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.venera.compose.gallery.data.GelbooruCredentialState
import com.venera.compose.gallery.data.GelbooruIdentity
import com.venera.desktop.gallery.data.DesktopGalleryCredentials
import io.github.composefluent.component.AccentButton
import io.github.composefluent.component.Button
import io.github.composefluent.component.ProgressBar
import io.github.composefluent.component.Text
import io.github.composefluent.component.TextField
import kotlinx.coroutines.launch

/**
 * S4：Gelbooru 凭据面板。
 *
 * ## 落盘与判据都不在这颗文件里
 *
 * 写侧的**全部**判据（两样都非空才算配过、站方点头才落盘、401 那句话怎么写）都在
 * `GelbooruCredentialState` —— 与 Android 侧 `GelbooruAccount` 共用同一颗（见那颗的类注释）。
 * 这颗文件只做两件事：把用户敲的两个值交给它、把它的回答（身份流 / 失败句）摆出来。
 * 一旦这里再写一遍 `if (uid.isNotBlank() && key.isNotBlank())`，两端就有了两种"算配好了"的口径。
 *
 * ## 三条不能省的读数
 *
 * 1. **没配时必须写出 [GelbooruCredentialState.ANONYMOUS_HINT]** —— DAPI 匿名一律 401，
 *    没配不是"少几档权限"，是**一张图都取不到**。不写这句，它会静默长成"这一站今天没图"。
 * 2. **明文只落本机**：不加密、不上传、不写日志（照 `GelbooruAccount` 同一条规矩）。
 *    键回显一律脱敏，且**不回显 User ID**（它与 key 是一对，回显一半等于给爆破省一半功）。
 * 3. **一条已拍板的红线**：不从浏览器 cookie 库取凭据。本仓不提供那条路 ——
 *    那条路看着"方便"，实际是把用户没声明来源的会话凭据搬到另一台机器上。
 *
 * ## 为什么用 fluent `TextField` 而不是 `BasicTextField`
 *
 * 先前一版用 `BasicTextField` + `enabled = false` 做只读回显，编译上就卡住（面板是 `@Composable`，
 * 而那处调用被写在非组合上下文里），而且那个形状本身也是错的：把输入框画成"能点但点不了"
 * 正是本仓记过的那类假开关。改用 fluent 自己的 `TextField` 后，输入/只读都是它自己那份。
 */
@Composable
internal fun DesktopGelbooruCredentialPanel(
    credentials: DesktopGalleryCredentials,
    modifier: Modifier = Modifier,
    onCleared: () -> Unit = {},
) {
    // 身份流是判据件的单一事实源：存好/注销之后这一屏立刻跟着变，不靠本地再存一份。
    // 局部化一次是为了避开委托属性的 smart cast 限制（`by` 出来的属性编译器判不出非空）。
    val identityState by credentials.gelbooru.identity.collectAsState()
    val identity: GelbooruIdentity? = identityState

    val scope = rememberCoroutineScope()

    var userIdInput by remember { mutableStateOf("") }
    var apiKeyInput by remember { mutableStateOf("") }
    var revealKey by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var rejection by remember { mutableStateOf<String?>(null) }

    // 身份从"有"变"无"时清掉输入框里的半截值：留着它会让用户以为"我填过，只是没生效"。
    LaunchedEffect(identity) {
        if (identity == null) {
            userIdInput = ""
            apiKeyInput = ""
            rejection = null
        }
    }

    Column(modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Gelbooru 凭据", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)

        if (identity == null) {
            // 缺席必须明说，而且要说清"要什么"：这一站没有匿名读，所以这是硬门槛不是加分项。
            Text(
                "没配凭据时这一站一张图都取不到：${GelbooruCredentialState.ANONYMOUS_HINT}。",
                color = WarnColor,
                fontSize = 13.sp,
            )
            Text(
                "User ID 与 API Key 都在 Gelbooru 账号选项页（两样必须来自同一账号）。",
                fontSize = 12.sp,
            )
        } else {
            Text("已配好：User ID ${maskUserId(identity.userId)}", fontSize = 13.sp)
            // ⚠️ 不回显 key 本身。桌面这一侧不加加密层（与 Android 侧同一条取舍），
            // 所以唯一能做的就是"配没配"这件事如实说，而不给它上屏。
            Text(
                if (revealKey) "API Key：${credentials.gelbooru.apiKey}" else "API Key：已保存（不回显）",
                fontSize = 13.sp,
            )
            Text(
                "凭据只落在本机数据目录的 prefs 文件里，不上传、不写日志。" +
                    "共享机器上别在这上面配。",
                color = MutedColor,
                fontSize = 12.sp,
            )
        }

        // ⚠️ fluent `TextField` 的第二个位置参数是 **enabled**（不是 material 那套的 `onValueChange`
        // 命名位置），而它是 trailing-lambda 形状的第二个 —— 所以这里的写法是
        // `TextField(value, onValueChange = …, enabled = …)`，与 `ImeProbe.kt:50` 那处同形。
        // 配好之后这两个框**只读**（enabled = false）：配好的凭据不许就地改半个字符 ——
        // 半边改了会被判成"没配"（`GelbooruCredentialState` 判据第一条），用户看到的是"我明明填了"。
        TextField(
            value = userIdInput,
            onValueChange = { userIdInput = it },
            modifier = Modifier.fillMaxWidth(),
            enabled = identity == null,
        )
        TextField(
            value = apiKeyInput,
            onValueChange = { apiKeyInput = it },
            modifier = Modifier.fillMaxWidth(),
            enabled = identity == null,
            visualTransformation = if (revealKey) VisualTransformation.None else PasswordVisualTransformation(),
        )

        rejection?.let {
            Text(it, color = WarnColor, fontSize = 12.sp, maxLines = 3)
        }

        if (busy) {
            ProgressBar()
        } else if (identity == null) {
            // ⚠️ 两处 API 形状都别照 material 那套写：
            //  - `AccentButton(onClick, modifier, **disabled**, colors, interactionSource, …, content)` ——
            //    fluent 把第三枚叫 `disabled` 而不是 `enabled`（javap 读 fluent-desktop 0.1.0 的
            //    常量池实测：只有 `disabled` / `iconOnly` / `contentArrangement`，没有 `enabled`），
            //    所以"两框没填满就不许点"要写成 `disabled = …`；
            //  - `content` 是 **trailing lambda**，不是双 lambda。
            AccentButton(
                onClick = {
                    busy = true
                    rejection = null
                    scope.launch {
                        // 站方点头才落盘（`GelbooruCredentialState.signIn`）—— 401 时原来那份存储不动。
                        val outcome = credentials.gelbooru.signIn(userIdInput, apiKeyInput)
                        busy = false
                        rejection = outcome.exceptionOrNull()?.message
                    }
                },
                disabled = userIdInput.isBlank() || apiKeyInput.isBlank(),
            ) { Text("保存并校验") }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (identity != null) {
                Button(onClick = {
                    revealKey = !revealKey
                }) { Text(if (revealKey) "隐藏 Key" else "显示 Key") }

                Button(onClick = {
                    credentials.gelbooru.signOut()
                    rejection = null
                    onCleared()
                }) { Text("清除凭据") }
            }
        }
    }
}

/**
 * User ID 脱敏：**只留末两位**。
 *
 * 刻意不给"末四位" —— key 给末四位是常见做法（泄露 4 位十六进制哈希猜不出来），
 * 而 User ID 在 Gelbooru 是**递增的数字 id**，公开的账号页一眼能对上一个号段，
 * 留四位等于把号段窗口缩到一万，而全站号是千万级。所以这里比 key 更严。
 */
private fun maskUserId(userId: String): String =
    if (userId.length <= 2) "**" else "…" + userId.takeLast(2)

private val WarnColor = androidx.compose.ui.graphics.Color(0xFFE0A030)
private val MutedColor = androidx.compose.ui.graphics.Color(0xFF8B8B8B)
