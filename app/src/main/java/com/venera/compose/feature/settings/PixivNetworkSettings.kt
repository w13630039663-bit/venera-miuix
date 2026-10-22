package com.venera.compose.feature.settings

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import com.venera.compose.data.prefs.VeneraPreferences
import com.venera.compose.gallery.domain.PixivNetworkMode
import com.venera.compose.ui.tokens.VeneraTokens

/**
 * pixiv 专属网络设置页面（2026-10-22）。
 *
 * 包含：OAuth 网络模式、API 服务网络模式、图片源（pximg / pixiv.re / 自定义 Host）。
 */
@Composable
internal fun PixivNetworkSettings(prefs: VeneraPreferences, onBack: () -> Unit) {
    val tokens = VeneraTokens
    val oauthMode by prefs.pixivOAuthNetworkMode.collectAsState()
    val apiMode by prefs.pixivApiNetworkMode.collectAsState()
    val currentImageHost by prefs.pixivImageHost.collectAsState()

    var customHostText by remember(currentImageHost) {
        mutableStateOf(if (currentImageHost != "i.pximg.net" && currentImageHost != "i.pixiv.re") currentImageHost else "")
    }

    SettingsPage(
        title = "网络设置",
        onBack = onBack,
        largeTitle = "网络设置",
        heroSubtitle = "OAuth · API 服务 · 图片源",
    ) {
        SettingsGroupTitle("OAuth 网络模式")
        NetworkModeCard(
            current = oauthMode,
            onSelect = { prefs.setPixivOAuthNetworkMode(it) },
        )

        Spacer(Modifier.height(tokens.spacing.space4))

        SettingsGroupTitle("API 服务网络模式")
        NetworkModeCard(
            current = apiMode,
            onSelect = { prefs.setPixivApiNetworkMode(it) },
        )

        Spacer(Modifier.height(tokens.spacing.space4))

        SettingsGroupTitle("图片源")
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(tokens.shape.card))
                .background(tokens.color.surfaceContainerHigh)
                .padding(tokens.spacing.space4),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(tokens.spacing.space2)) {
                ImageHostOptionItem(
                    title = "i.pximg.net",
                    summary = "i.pximg.net",
                    selected = currentImageHost == "i.pximg.net",
                    onClick = { prefs.setPixivImageHost("i.pximg.net") },
                )

                ImageHostOptionItem(
                    title = "i.pixiv.re",
                    summary = "i.pixiv.re",
                    selected = currentImageHost == "i.pixiv.re",
                    onClick = { prefs.setPixivImageHost("i.pixiv.re") },
                )

                Spacer(Modifier.height(tokens.spacing.space2))

                Text(
                    text = "自定义图片源 Host",
                    fontSize = tokens.type.body,
                    fontWeight = tokens.type.weightMedium,
                    color = tokens.color.textPrimary,
                )
                OutlinedTextField(
                    value = customHostText,
                    onValueChange = {
                        customHostText = it
                        if (it.isNotBlank()) {
                            prefs.setPixivImageHost(it.trim())
                        }
                    },
                    placeholder = { Text("自定义图片源 Host") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
            }
        }
    }
}

@Composable
private fun NetworkModeCard(
    current: PixivNetworkMode,
    onSelect: (PixivNetworkMode) -> Unit,
) {
    val tokens = VeneraTokens
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(tokens.shape.card))
            .background(tokens.color.surfaceContainerHigh)
            .padding(tokens.spacing.space4),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(tokens.spacing.space3)) {
            PixivNetworkMode.entries.forEach { mode ->
                NetworkModeOptionItem(
                    title = mode.label,
                    summary = mode.summary,
                    selected = current == mode,
                    onClick = { onSelect(mode) },
                )
            }
        }
    }
}

@Composable
private fun NetworkModeOptionItem(
    title: String,
    summary: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val tokens = VeneraTokens
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(tokens.shape.medium))
            .clickable(onClick = onClick)
            .padding(tokens.spacing.space3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = tokens.type.itemTitle,
                fontWeight = tokens.type.weightSemibold,
                color = tokens.color.textPrimary,
            )
            Spacer(Modifier.height(tokens.spacing.space1))
            Text(
                text = summary,
                fontSize = tokens.type.caption,
                color = tokens.color.textSecondary,
            )
        }
        if (selected) {
            Icon(
                imageVector = Icons.Outlined.Check,
                contentDescription = "已选择",
                tint = tokens.color.primary,
            )
        }
    }
}

@Composable
private fun ImageHostOptionItem(
    title: String,
    summary: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val tokens = VeneraTokens
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(tokens.shape.medium))
            .clickable(onClick = onClick)
            .padding(tokens.spacing.space3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = tokens.type.itemTitle,
                fontWeight = tokens.type.weightSemibold,
                color = tokens.color.textPrimary,
            )
            Spacer(Modifier.height(tokens.spacing.space1))
            Text(
                text = summary,
                fontSize = tokens.type.caption,
                color = tokens.color.textSecondary,
            )
        }
        if (selected) {
            Icon(
                imageVector = Icons.Outlined.Check,
                contentDescription = "已选择",
                tint = tokens.color.primary,
            )
        }
    }
}
