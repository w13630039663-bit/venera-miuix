package com.venera.compose.gallery.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import coil3.ImageLoader
import com.venera.compose.components.venera.VeneraCover
import com.venera.compose.components.venera.VeneraDialog
import com.venera.compose.components.venera.VeneraTextButton
import com.venera.compose.components.venera.VeneraTextField
import com.venera.compose.components.venera.VeneraTopBarPill
import com.venera.compose.components.venera.veneraAvatarRing
import com.venera.compose.gallery.data.GalleryPorts
import com.venera.compose.gallery.data.PixivAuthState
import com.venera.compose.gallery.data.PixivCredentials
import com.venera.compose.ui.tokens.VeneraTokens
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Text

/**
 * 顶栏左槽那颗 **pixiv 账号头像**（方案 `docs/rounds/gallery-pixiv-account-pkce-2026-10-17.md` §四 · P3）。
 *
 * ## 对齐靠的是同一颗件，不是同一对数字
 *
 * 外壳直接就是 [VeneraTopBarPill] —— 右侧那排按钮本来就是它，触达 48dp / 视觉 40dp 那一对
 * 由它自己带（批次 H 定的"视觉 40 + 触达 48"）。所以"和其他按钮对齐"不是本鱼抄一遍尺寸，
 * 而是**共用同一个外壳**：以后那对尺寸再改，这颗跟着一起改。
 *
 * ## 为什么不复用 [GalleryArtistAvatar]
 *
 * 那一颗是**全站唯一的画师脸**，尺寸档却是首页那行的 56dp，连首字母的 22sp 都是在那一档上调定的
 * （它的注释写明"56dp 的圆座里 14sp 的字母读起来像没画完"）。装进 40dp 的顶栏座里，
 * 22sp 那枚字母会顶满圆边 —— 形状对、密度不对。所以这里只搬**画法**（`clip(CircleShape)` +
 * `surfaceVariant` 底 + [VeneraCover]），文字与图标各用顶栏那一档。
 *
 * ## 未登录那一档念得出话，不是空圈
 *
 * 空圈会被读成"这张图挂了"。所以：没登录 = 一枚人形图标 + `contentDescription` 直说"登录 pixiv 账号"，
 * 点下去打开**登录弹层**（2026-10-17 第四轮起不再是直接跳浏览器，那一路搬进弹层的第一颗按钮）；
 * 登过但刷不动 / 刚那一趟没登上 = 同一枚图标换成"重新登录 pixiv 账号"那句话
 * （方案 §三 第 4 步：这两档不许静默退回"没登录"，所以话要不一样，而首页「关注画师」那一行
 * 念的是 `authState.screenLine` 那一份，不是这里另抄的一句）；
 * 已登录 = 真头像或名字首字母，点下去是"这是谁 + 退出登录"。
 *
 * ## 登录弹层里为什么有四件（2026-10-17 第四轮，用户点了 B）
 *
 * 1. **去浏览器登录** —— 站方唯一认的那条路：密码 / Google / Apple 都在它自己那一页上接
 *    （WebView 里点 Google 会被 `disallowed_useragent` 拦，这一条 P6 量过）。
 * 2. **粘贴一条** —— 浏览器那一路偶尔断在半路（回跳没交回来、或在别的机器上登的）。
 *    认 token JSON / 回跳链接 / 光秃秃的 refresh token 三种形状，判据在
 *    `PixivCredentialState.signInWithPasted` 的注释里。
 * 3. **本机 Google 账号** —— 系统账号选择器，只作**身份标记**，界面上写死它不等于 pixiv 登录
 *    （为什么这条路不能当 pixiv 登录，逐条写在 `GoogleAccountIdentity` 头注上）。
 * 4. 那句说明文字 —— 上面第 3 条是这一轮最容易读错的一格：一枚写着"Google"的按钮躺在登录框里，
 *    不写明就一定会被读成"点它能登进 pixiv"。所以那句话是**契约的一部分**，不是文案装饰。
 */
@Composable
internal fun GalleryPixivAccountButton(
    account: PixivCredentials,
    imageLoader: ImageLoader,
) {
    val tokens = VeneraTokens
    val context = LocalContext.current
    val state by account.authState.collectAsState()
    val signedIn = state as? PixivAuthState.SignedIn

    // 没登上那几档的话要说得出"是哪一档"（穷尽 when，不留 else —— 加第四档会编译不过，
    // 而漏了表态的表现是"登录失败念成还没登录"，屏上看不出来）。
    val loginHint = when (state) {
        is PixivAuthState.SignedIn -> "pixiv 账号"
        PixivAuthState.Anonymous -> "登录 pixiv 账号"
        is PixivAuthState.NeedsRelogin, is PixivAuthState.LoginRejected -> "重新登录 pixiv 账号"
    }

    var manageOpen by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf<String?>(null) }

    var loginOpen by remember { mutableStateOf(false) }
    var pasted by remember { mutableStateOf("") }
    var pastedBusy by remember { mutableStateOf(false) }
    var pastedError by remember { mutableStateOf<String?>(null) }
    // 本机 Google 账号从**画廊业务口**读（判据与理由见 `LocalGoogleIdentity`）：
    // 这一侧没接（比如桌面没有账号框架）时它是 null，下面那一整块就不摆 —— 不摆半件。
    val googleIdentity = GalleryPorts.of(context).googleIdentity
    var googleEmail by remember { mutableStateOf(googleIdentity?.email()) }
    var googleNotice by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    // 系统账号选择器：选完只把**邮箱**记下来，不换任何票据（理由见 GoogleAccountIdentity 头注）。
    val googlePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val picked = GoogleAccountIdentity.emailFrom(result.resultCode, result.data)
        when {
            picked != null -> {
                googleIdentity?.remember(picked)
                googleEmail = picked
                googleNotice = null
            }
            // 取消不算失败，什么都不说；只有"看着选了却读不回来"才值得念一句。
            result.resultCode == Activity.RESULT_OK -> googleNotice = GOOGLE_PICK_FAILED
            else -> Unit
        }
    }

    // 回跳在别处落地（manifest → MainActivity → completeLogin）：登上了就把弹层收掉，
    // 否则用户在浏览器里登完回到 app，屏上还摆着一张"登录 pixiv"的框。
    LaunchedEffect(signedIn) {
        if (signedIn != null) {
            loginOpen = false
            pasted = ""
            pastedError = null
        }
    }

    // 回过浏览器却没登上，必须**当面**说一遍（2026-10-17 第五轮）。
    //
    // `LoginRejected` 只活在这条流上、不落盘，而屏上原本唯一那句在「关注画师」那一行 ——
    // 那一行常常在屏幕外，于是"登录失败"读起来就是"点完回来什么都没发生"。
    // 键取 `state` 而不是 `rejected`：它是 data class，每次推入都是新实例 ⇒ 同一原因再失败一次也照说
    // （能重复推就是真的又试了一次，不该被吞）。用户点掉之后 `notice` 归 null，state 不变 ⇒ 不会复弹。
    LaunchedEffect(state) {
        (state as? PixivAuthState.LoginRejected)?.let { notice = it.reason }
    }

    // 头像地址**每次现取**（口径同 `PixivClient` 文件头那条：这一族的地址会随账号改名而失效，
    // 落库过的旧地址会把头像钉在错的那一张上）。失败不摆话：退到首字母/人形，不弹错误。
    var avatarUrl by remember { mutableStateOf<String?>(null) }
    val userId = signedIn?.userId?.toLongOrNull()
    LaunchedEffect(userId) {
        avatarUrl = if (userId == null) null else GalleryPorts.of(context).artists.pixivAvatarUrl(userId).getOrNull()
    }

    VeneraTopBarPill(
        onClick = {
            when {
                signedIn != null -> manageOpen = true
                // 没配 client 凭据也开弹层：弹层里有"粘贴一条"那一路，它不依赖 client 凭据以外的条件
                // （凭据真没配时两条都会念同一句话，见弹层里那颗按钮）。
                else -> {
                    pastedError = null
                    googleNotice = null
                    loginOpen = true
                }
            }
        },
    ) {
        Box(
            modifier = Modifier
                .size(tokens.spacing.iconButtonSize)
                // 与首页那两排头像同一颗描边（第二十三轮，用户「给页面所有的头像都加一层淡淡的描边」）：
                // 这颗是**页面底**上的圆，压在头图的彩色区域上时没有边就只是"一块颜色"。
                .veneraAvatarRing(CircleShape)
                .background(tokens.color.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            val name = signedIn?.userName.orEmpty()
            // 委托属性智能转换不过来（`by mutableStateOf`），所以先落成局部名再用。
            val face = avatarUrl
            when {
                face != null -> VeneraCover(
                    url = face,
                    contentDescription = name.ifBlank { "pixiv 头像" },
                    preserveAspectRatio = false,
                    imageLoader = imageLoader,
                    modifier = Modifier.fillMaxSize(),
                )
                // 已登录但取不到脸：摆首字母。40dp 的座子里用 `caption` 那一档，
                // 不搬首页 56dp 座里定下来的 22sp（那颗的注释就是为那一档写的）。
                signedIn != null -> Text(
                    text = name.take(1).uppercase(),
                    fontSize = tokens.type.caption,
                    color = tokens.color.onSurfaceVariant,
                    maxLines = 1,
                )
                else -> Icon(
                    imageVector = Icons.Outlined.Person,
                    contentDescription = loginHint,
                    tint = tokens.color.textSecondary,
                )
            }
        }
    }

    // 已登录点开的是"这是谁 + 要不要退出"。退出是**破坏性**动作（清掉本机票据），
    // 所以走弹层确认这一档，而不是点头像那一下就生效。
    VeneraDialog(
        show = manageOpen,
        onDismissRequest = { manageOpen = false },
        title = signedIn?.userName?.takeIf { it.isNotBlank() } ?: "已登录 pixiv",
        content = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "退出会清掉这台机器上的 pixiv 登录凭据。收藏与关注都存在站方那边，不受影响。",
                    fontSize = tokens.type.caption,
                    color = tokens.color.textSecondary,
                )
                // 本机 Google 账号只在这里与登录弹层里露一行 —— 它不是账号体系的一部分，
                // 摆进"这是谁"这一格里正好当一句备注用。
                googleEmail?.let { email ->
                    Text(
                        text = "本机 Google 账号：$email",
                        fontSize = tokens.type.caption,
                        color = tokens.color.textTertiary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = tokens.spacing.space3),
                    )
                }
            }
        },
        confirmText = "退出登录",
        confirmDestructive = true,
        onConfirm = {
            account.signOut()
            manageOpen = false
        },
        dismissText = "先不",
    )

    // ── 登录弹层（2026-10-17 第四轮）──
    //
    // 四件摆在一处，顺序就是"哪条路最可能成"：浏览器 → 粘贴 → 本机 Google 账号 → 说明。
    VeneraDialog(
        show = loginOpen,
        onDismissRequest = { loginOpen = false },
        title = "登录 pixiv",
        dismissText = "取消",
        confirmText = "去浏览器登录",
        buttonsEnabled = !pastedBusy,
        onConfirm = {
            // 试过所有前提再开页面：没配 client 凭据时 beginLogin 回 null，
            // 而"打开一张注定换不到票据的登录页"就是方案 §五 那条假开关。
            if (!account.isClientConfigured) {
                notice = MISSING_CLIENT_CREDENTIALS
            } else {
                val challenge = account.beginLogin()
                if (challenge == null) {
                    notice = MISSING_CLIENT_CREDENTIALS
                } else {
                    // 交出去之后**不在这里等**：换票据那一跳由回跳自己做
                    // （manifest 的 intent-filter → MainActivity → `completeLogin`），
                    // 成败都由 `authState` 那一条流推回这一颗与首页那一行。
                    notice = openPixivLoginInBrowser(context, challenge.authorizeUrl)
                    loginOpen = false
                }
            }
        },
        content = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    // 这句要点名 Chrome：用户问的是"能不能用手机上的 Google 账号登 pixiv"，
                    // 而账号列表跟着浏览器会话走 —— 说清落在哪个浏览器，他才排得掉"怎么没列出来"。
                    text = "登录在浏览器里完成（优先 Chrome）：pixiv 的密码、Google、Apple 都是站方那一页自己接的，" +
                        "点 Google 会列出这个浏览器里已登录的账号。登完会自动回到这里。",
                    fontSize = tokens.type.caption,
                    color = tokens.color.textSecondary,
                )
                VeneraTextField(
                    value = pasted,
                    onValueChange = {
                        pasted = it
                        pastedError = null
                    },
                    label = "粘贴 refresh token / 回跳链接 / token JSON",
                    singleLine = false,
                    enabled = !pastedBusy,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = tokens.spacing.space4),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    VeneraTextButton(
                        text = if (pastedBusy) "换票据…" else "用这一条登录",
                        enabled = pasted.isNotBlank() && !pastedBusy,
                        onClick = {
                            pastedBusy = true
                            pastedError = null
                            scope.launch {
                                val result = account.signInWithPasted(pasted)
                                pastedBusy = false
                                result
                                    .onSuccess {
                                        loginOpen = false
                                        pasted = ""
                                    }
                                    .onFailure { pastedError = it.readableLine() }
                            }
                        },
                    )
                }
                pastedError?.let { line ->
                    Text(
                        text = line,
                        fontSize = tokens.type.caption,
                        color = tokens.color.textTertiary,
                        modifier = Modifier.padding(top = tokens.spacing.space2),
                    )
                }

                // 这一侧没接账号框架时整块不摆（不摆半件：一枚点了没反应的"Google"按钮，
                // 比没有这枚按钮更糟）。
                if (googleIdentity != null) {
                    Spacer(modifier = Modifier.height(tokens.spacing.space4))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "本机 Google 账号",
                                fontSize = tokens.type.caption,
                                color = tokens.color.textPrimary,
                            )
                            Text(
                                text = googleEmail ?: "还没选",
                                fontSize = tokens.type.caption,
                                color = tokens.color.textTertiary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        VeneraTextButton(
                            text = if (googleEmail == null) "选择账号" else "更换",
                            onClick = {
                                val intent = GoogleAccountIdentity.pickerIntent()
                                if (intent == null) {
                                    googleNotice = NO_GOOGLE_PICKER
                                } else {
                                    googleNotice = null
                                    googlePicker.launch(intent)
                                }
                            },
                        )
                        if (googleEmail != null) {
                            VeneraTextButton(
                                text = "清除",
                                onClick = {
                                    googleIdentity.forget()
                                    googleEmail = null
                                    googleNotice = null
                                },
                            )
                        }
                    }
                    // ⚠️ 这句是本轮那张"Google"按钮的护栏：不写清楚，它一定会被读成
                    // "点这里能用 Google 登进 pixiv"（`PixivLoginPathsTest` 钉着它）。
                    // ⚠️ 别在这句里写 Markdown：`Text` 不解析星号，星号会连字一起印上屏。
                    // 强调走「」这一档；判据（`PixivLoginPathsTest`）认的是那两个关键词。
                    Text(
                        text = "这枚只代表这台手机上的 Google 账号（用来认机器），「不等于」pixiv 登录 —— " +
                            "pixiv 那边仍在上面的浏览器页里登。",
                        fontSize = tokens.type.caption,
                        color = tokens.color.textTertiary,
                        modifier = Modifier.padding(top = tokens.spacing.space2),
                    )
                    googleNotice?.let { line ->
                        Text(
                            text = line,
                            fontSize = tokens.type.caption,
                            color = tokens.color.textTertiary,
                            modifier = Modifier.padding(top = tokens.spacing.space2),
                        )
                    }
                }
            }
        },
    )

    VeneraDialog(
        show = notice != null,
        onDismissRequest = { notice = null },
        title = "现在登不了 pixiv",
        content = { notice?.let { line -> Text(text = line, fontSize = tokens.type.caption) } },
        confirmText = "知道了",
        onConfirm = { notice = null },
    )
}

/** 顶栏那颗件里唯一一句界面话：没配 client 凭据时念它（不编一句"密码错了"）。 */
private const val MISSING_CLIENT_CREDENTIALS =
    "这台机器还没填 pixiv 的客户端凭据，登录这一路暂时走不了。"

/** 拉不起账号选择器时的那一句（不静默：点了没反应与"系统里没有"是两件事）。 */
private const val NO_GOOGLE_PICKER =
    "这台机器拉不起系统账号选择器（多半没有 Google 服务），只能手动填。"
private const val GOOGLE_PICK_FAILED =
    "系统把账号交回来了，但没带邮箱这一项 —— 这次没记下来。"

/**
 * `Throwable.message` 的可读版（与 `PixivCredentialState.readableReason` 同一条口径：
 * 空消息给一句实话，绝不返回 null）。
 */
private fun Throwable.readableLine(): String =
    message?.trim()?.take(160)?.ifBlank { null } ?: "这一条没换到票据（${javaClass.simpleName}）"

/**
 * 把 pixiv 的授权页交给**外部浏览器**（方案 §四 · P6，用户 2026-10-17 点名
 * 「要有跳转到外部网页登录的授权登录和 google 账号登录的办法」）。
 *
 * ## 为什么不再是本 app 里那一屏 WebView
 *
 * P4 那版是自持的一小颗 WebView 宿主，量过的结论是"不能复用漫画源那颗 `WebLoginScreen`"
 * （它的判定只发在 `onPageFinished` 三处，自定义 scheme 交回 WebView 不会有"页面加载完成"）。
 * 但 WebView 这条路还有一道**不是我们能改的**闸：Google 在自己的 OAuth 页上认得出嵌入式
 * WebView 的 user-agent，直接回 `disallowed_useragent` —— 于是"用 Google 账号登录"那一枚
 * 按下去必然是一页报错，而 pixiv 的登录页上正是拿 Google/Apple 当主要登录方式之一。
 * 交给真浏览器之后：密码、Google、Apple 各走站方自己那条正常路径，我们这边只管
 * PKCE 那一跳与回跳，**不碰站方的登录方式**（也就不需要为每一种登录方式写一条退化路径）。
 *
 * ## 用户要的"能拉起手机的 Google 账号"在这里是怎么被满足的
 *
 * 真浏览器（Chrome）里点 pixiv 那一页的 Google 按钮，Google 的账号选择页会列出**这台设备上
 * 已登录的那个 Google 账号** —— 那一步就是原生账号列表，不用敲邮箱密码。协议上这是唯一处
 * "设备账号列表"出现的地方：pixiv 的 Google client 归站方，我们的原生选择器换不到它认的凭据
 * （逐条理由见 `GoogleAccountIdentity` 头注）。
 *
 * 回跳由 `AndroidManifest.xml` 里那两条 intent-filter（`pixiv://account…` 与
 * 站方登记的 https 回跳）交回 MainActivity，再走 [PixivCredentials.completeLogin] ——
 * 这一颗**不等**结果，所以这里返回 null 只说明"浏览器收下了这一跳"，不说明登录成没成
 * （那件事全在 `authState` 那一条流上）。
 *
 * ## 为什么用 Custom Tab 而不是裸 `ACTION_VIEW`（2026-10-17 第五轮）
 *
 * 用户的原话是「我还是要 google 这样登录 pixiv 试试」。**pixiv 那一侧的 Google 登录只能发生在
 * 站方网页里**（我们的 Google 身份是发给我们自己 client 的，站方两把锁一起拒：client 归属站方、
 * `/auth/token` 没有 ID token 这一种 grant），于是这条路唯一能改的地方就是**把哪份浏览器会话交给它**。
 *
 * 关键事实：Google 的账号列表跟着**浏览器自己那份会话**走，不跟系统账号框架走 ——
 * 裸 `ACTION_VIEW` 会交给用户设的默认浏览器（可能是夸克/UC/Edge），那份会话里通常没有 Google；
 * Custom Tab 固定走 Chrome，Chrome 里已登录的 Google 账号才会在 pixiv 那一页点 Google 时直接列出来。
 * 这就是"能拉起手机上的谷歌账号"在这条链上唯一站得住的位置。
 *
 * 退回顺序是有意的：**先 Custom Tab，起不来才裸 VIEW**。反过来的话，凡是有默认浏览器的机器
 * 就永远不会走 Chrome（多数机型都能起 Custom Tab，等于把这一改直接废掉）。
 *
 * @return null = 已经交出去了；非 null = 那句要上屏的原因（连裸 VIEW 都起不来）。
 */
private fun openPixivLoginInBrowser(context: Context, authorizeUrl: String): String? {
    val uri = Uri.parse(authorizeUrl)
    // ① Custom Tab：要的就是 Chrome 那份会话。
    val viaTab = runCatching {
        CustomTabsIntent.Builder()
            .setShowTitle(true)
            // 地址栏别在滚动时收起：用户要能看见自己在 pixiv 的域上（授权页尤其该看得见）。
            .setUrlBarHidingEnabled(false)
            .build()
            .apply { intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
            .launchUrl(context, uri)
    }
    if (viaTab.isSuccess) return null
    // ② 设备上没有支持 Custom Tabs 的浏览器（少数精简 ROM）：退回任意能开 https 的那一个。
    //    ⚠️ 这一档里 Google 账号列表**不保证**列得出来 —— 那句实话在弹层文案里已经写了。
    val plain = Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    // runCatching 而不是让异常穿过 onClick：崩在顶栏那一颗件里，用户只会看到"点了没反应"。
    return runCatching { context.startActivity(plain) }
        .exceptionOrNull()
        ?.let { "这台机器打不开浏览器，所以到不了 pixiv 的登录页（$it）" }
}
