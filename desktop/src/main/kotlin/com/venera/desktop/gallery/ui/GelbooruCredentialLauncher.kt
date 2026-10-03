package com.venera.desktop.gallery.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.venera.compose.gallery.data.GelbooruCredentialState
import com.venera.desktop.gallery.data.DesktopGalleryCredentials
import io.github.composefluent.component.Text

/**
 * 凭据面板的**入口** —— 一行可展开的卡片，不占核心视觉区。
 *
 * ## 为什么要收起来（2026-10-04 视觉重构）
 *
 * 上一轮把整颗 [DesktopGelbooruCredentialPanel] 直接挂在缺席读数下面，于是首屏最值钱的位置
 * 被两个输入框 + 一颗按钮占掉，而那一屏本该给图。凭据是**偶尔配一次**的东西，
 * 不是每次进首页都要看的东西 —— 所以收成一行摘要 + 可展开：
 *
 * - 收起态一行说清「配没配 + 缺什么」，缺席时**始终可见**（那正是用户需要它的时候）；
 * - 展开态才是两个输入框与那颗「保存并校验」。
 *
 * ## 缺席时它仍然是**缺席读数的一部分**，不是"设置项"
 *
 * ⚠️ 收起态那行不是导航链接、不是角标，是缺席说明的延续 ——
 * 「这一轮没给内容的站：Gelbooru：…」下面紧跟「DAPI 匿名一律 401，需 api_key + user_id」，
 * 用户不必猜去哪配。点开是就地展开，不跳走：跳走会让他丢失"这一轮另外两站在哪"这个上下文。
 */
@Composable
internal fun GelbooruCredentialLauncher(
    credentials: DesktopGalleryCredentials,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val configured = credentials.gelbooru.isConfigured

    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(DesktopTheme.CardBackground)
                .clickable { expanded = !expanded }
                .padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                "Gelbooru 凭据",
                color = DesktopTheme.TextPrimary,
                fontSize = 13.sp,
            )
            Text(
                if (configured) {
                    "已配好 · 点这里管理"
                } else {
                    // 这句就是 S4 的判据文案本身，界面不另写一份
                    GelbooruCredentialState.ANONYMOUS_HINT
                },
                color = if (configured) DesktopTheme.TextSecondary else DesktopTheme.AccentBeni,
                fontSize = 12.sp,
                modifier = Modifier.weight(1f),
            )
            Text(
                if (expanded) "收起" else "展开",
                color = DesktopTheme.AccentFuji,
                fontSize = 12.sp,
            )
        }

        AnimatedVisibility(visible = expanded) {
            Column(Modifier.padding(top = 6.dp)) {
                DesktopGelbooruCredentialPanel(credentials)
            }
        }
    }
}
