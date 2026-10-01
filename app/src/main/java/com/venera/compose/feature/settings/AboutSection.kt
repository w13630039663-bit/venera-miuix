package com.venera.compose.feature.settings

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import com.venera.compose.R
import com.venera.compose.data.prefs.VeneraPreferences
import com.venera.compose.data.update.AppUpdateChecker
import com.venera.compose.data.update.ProjectChannel
import com.venera.compose.data.update.UpdateCheck
import com.venera.compose.ui.tokens.VeneraTokens
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Text

/**
 * 应用身份卡（原「关于区」重排后的形态，2026-10-02 设置页重设计）。
 *
 * 参考图里这块是一张「图标 + 版本 + 检查更新」的紧凑卡，原来的 AboutSection
 * 则是一整屏（大图标 96dp + 三行文字 + 五行动作）。重排时：
 * - 身份行收成一张卡的一行（图标 56dp 档 + 名称 + 版本/副标）；
 * - 「项目主页 / 原始项目 / 上游频道」三个纯外链挪去 [SettingsQuickLinks]
 *   （链接不是设置项，混在开关列表里只会把列表拉长）；
 * - 检查更新与启动检查开关留在卡内 —— 这两条是真正的「应用级行为」。
 */
@Composable
internal fun AppIdentityCard(prefs: VeneraPreferences) {
    val context = LocalContext.current
    val version = remember(context) { AppUpdateChecker.localVersion(context) ?: "未知" }
    val checkOnStart by prefs.checkUpdateOnStart.collectAsState()
    val scope = rememberCoroutineScope()
    var checking by remember { mutableStateOf(false) }
    var availableVersion by remember { mutableStateOf<String?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }

    fun checkUpdate() {
        if (checking) return
        checking = true
        scope.launch {
            when (val result = AppUpdateChecker.check(context)) {
                is UpdateCheck.Available -> availableVersion = result.remoteVersion
                // 拿不到远端版本 ≠ 没有更新，播报必须分开，否则一次断网就是一次假成功
                UpdateCheck.UpToDate -> notice = "已经是最新版"
                UpdateCheck.Failed -> notice = "找不到 ${ProjectChannel.SLUG} 的发布记录，稍后再试"
            }
            checking = false
        }
    }

    SettingsGroup {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = VeneraTokens.spacing.rowHorizontal,
                    vertical = VeneraTokens.spacing.space9,
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(
                painter = painterResource(R.drawable.app_icon),
                contentDescription = "venera-miuix",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(VeneraTokens.spacing.quoteAvatarSize)
                    .clip(RoundedCornerShape(VeneraTokens.shape.card)),
            )
            Spacer(Modifier.size(VeneraTokens.spacing.space9))
            Column(Modifier.weight(1f)) {
                Text(
                    text = "venera-miuix",
                    fontSize = VeneraTokens.type.itemTitle,
                    fontWeight = VeneraTokens.type.weightMedium,
                    color = VeneraTokens.color.textPrimary,
                )
                Text(
                    text = "版本 $version · 免费开源的漫画阅读应用",
                    fontSize = VeneraTokens.type.caption,
                    color = VeneraTokens.color.textTertiary,
                    modifier = Modifier.padding(top = VeneraTokens.spacing.space1),
                )
            }
        }
        SettingsAction(
            title = if (checking) "正在检查更新…" else "检查更新",
            summary = "发布通道：${ProjectChannel.SLUG}",
            enabled = !checking,
        ) { checkUpdate() }
        SettingsToggle(
            title = "启动时检查更新",
            checked = checkOnStart,
            onCheckedChange = prefs::setCheckUpdateOnStart,
            summary = "24 小时内不重复检查",
        )
    }

    availableVersion?.let { UpdateAvailableDialog(it) { availableVersion = null } }
    notice?.let {
        LaunchedEffect(it) {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            notice = null
        }
    }
}

/**
 * 外链快捷行：三枚等宽胶囊（项目主页 / 原始项目 / 上游频道）。
 *
 * 位置在应用身份卡正下方 —— 参考图那一行「关于/项目/社区」的同位。
 * 打不开浏览器时 toast（原 AboutSection 的行为照搬）。
 */
@Composable
internal fun SettingsQuickLinks() {
    val context = LocalContext.current
    val tokens = VeneraTokens

    fun open(url: String) {
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
            .onFailure { Toast.makeText(context, "没找到可用的浏览器", Toast.LENGTH_SHORT).show() }
    }

    Row(Modifier.fillMaxWidth()) {
        QuickLink("项目主页", Modifier.weight(1f)) { open(ProjectChannel.REPO_URL) }
        Spacer(Modifier.size(tokens.spacing.space4))
        QuickLink("原始项目", Modifier.weight(1f)) { open("https://github.com/venera-app/venera") }
        Spacer(Modifier.size(tokens.spacing.space4))
        QuickLink("上游频道", Modifier.weight(1f)) { open("https://t.me/venera_release") }
    }
    Spacer(Modifier.size(tokens.spacing.space4))
}

@Composable
private fun QuickLink(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val tokens = VeneraTokens
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(tokens.shape.small))
            .background(tokens.color.surfaceContainerHigh)
            .clickable(onClick = onClick)
            .padding(vertical = tokens.spacing.space9),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            fontSize = tokens.type.caption,
            color = tokens.color.textSecondary,
        )
    }
}
