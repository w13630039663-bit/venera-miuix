package com.venera.compose.feature.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import coil3.compose.AsyncImage
import com.venera.compose.R
import com.venera.compose.ui.tokens.VeneraTokens
import com.venera.compose.data.api.AppearancePreferences
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Text

/**
 * 名言卡：设置首页与各子页**页尾**的一句短话（2026-10-02，参考图同位）。
 *
 * ── 内容 ──
 *
 * 进页时从 [QuotePool] 随机抽一句、之后定格：下标存 `rememberSaveable`，
 * 重组与旋屏都不换句（「进页定格不闪换」是拍板过的验收项）。换句的唯一方式
 * 是离开这一页再进来 —— 这是特性：名言要能被读完，不是跑马灯。
 *
 * ── 左侧小图 ──
 *
 * 默认应用图标；用户可自定义（点小图换图、长按清除，prefs 的
 * `settingsQuoteAvatarPath`）。与 hero 走同一条拷贝落盘链
 * （[rememberSettingsImagePicker]），只是槽位不同。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun SettingsQuoteCard(prefs: AppearancePreferences) {
    val tokens = VeneraTokens
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // 进页定格：下标进 saveable，旋屏不换。
    var quoteIndex by rememberSaveable { mutableIntStateOf(QuotePool.randomIndex()) }
    val quote = QuotePool.quotes[quoteIndex.coerceIn(QuotePool.quotes.indices)]

    val avatarPath by prefs.settingsQuoteAvatarPath.collectAsState()
    val avatarFile = remember(avatarPath) { SettingsHeroImageStore.resolve(avatarPath) }

    val pickAvatar = rememberSettingsImagePicker(SettingsHeroImageStore.KIND_QUOTE_AVATAR) { path ->
        if (path != null) prefs.setSettingsQuoteAvatarPath(path)
    }

    SettingsGroup {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = tokens.spacing.rowHorizontal,
                    vertical = tokens.spacing.space9,
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 小图：可点换图、长按清除恢复应用图标。
            Box(
                Modifier
                    .size(tokens.spacing.quoteAvatarSize)
                    .clip(RoundedCornerShape(tokens.shape.card))
                    .combinedClickable(onClick = pickAvatar, onLongClick = {
                        SettingsHeroImageStore.clear(context, SettingsHeroImageStore.KIND_QUOTE_AVATAR)
                        scope.launch { prefs.setSettingsQuoteAvatarPath("") }
                    }),
                contentAlignment = Alignment.Center,
            ) {
                if (avatarFile != null) {
                    AsyncImage(
                        model = avatarFile,
                        contentDescription = "名言卡小图（点按更换，长按恢复默认）",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(tokens.spacing.quoteAvatarSize),
                    )
                } else {
                    Image(
                        painter = painterResource(R.drawable.app_icon),
                        contentDescription = "名言卡小图（点按更换）",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(tokens.spacing.quoteAvatarSize),
                    )
                }
            }
            Spacer(Modifier.width(tokens.spacing.space9))
            Column(Modifier.weight(1f)) {
                Text(
                    text = quote.text,
                    fontSize = tokens.type.body,
                    color = tokens.color.textPrimary,
                )
                if (quote.source != null) {
                    Text(
                        text = "—— ${quote.source}",
                        fontSize = tokens.type.caption,
                        color = tokens.color.textTertiary,
                        modifier = Modifier.padding(top = tokens.spacing.space4),
                    )
                }
            }
        }
    }
}
