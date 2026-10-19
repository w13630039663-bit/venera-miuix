package com.venera.compose.gallery.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.em
import com.venera.compose.components.venera.VeneraCard
import com.venera.compose.components.venera.VeneraPressFeedback
import com.venera.compose.components.venera.VeneraTopAppBar
import com.venera.compose.components.venera.VeneraTopBarPill
import com.venera.compose.components.venera.rememberTopBarBackdrop
import com.venera.compose.components.venera.rememberVeneraTopAppBarBehavior
import com.venera.compose.ui.tokens.LocalBottomBarClearance
import com.venera.compose.ui.tokens.VeneraSpacing
import com.venera.compose.ui.tokens.VeneraTokens

/**
 * 「收藏」hub：本地收藏与 pixiv 云端书签**两个入口**（2026-10-17 第三十一轮，用户稿那两颗卡）。
 *
 * ## 为什么是两颗卡，不是一屏混排的墙
 *
 * 用户原话：「按照这个方式去做收藏」—— 稿上两颗卡并排，卡下一行说明把边界说死：
 * **本地收藏**继续统一展示 Yande、Gelbooru、Safebooru 和已接入的 Pixiv 作品；
 * **Pixiv 书签**单独读取账号数据，不与本地收藏混为一谈。
 *
 * 这不是排版偏好，是**两套账**的边界：本地收藏存在这台机器上（删了这边那边还在），
 * pixiv 书签活在 pixiv 账号里（我们只能读，删除要走站方）。把两边并成一屏，
 * 用户就无法回答"这张图到底存在哪儿"—— 那是比多一次点击更贵的错。
 *
 * ## 入口在哪
 *
 * 画廊首页顶栏那枚 🔖（在搜索与浏览历史两枚之间，见 `GalleryScreen` 的 actions）。
 * 点它进这一页，再按稿上的两颗卡分头走。
 *
 * ## 为什么未登录也摆 pixiv 那颗卡
 *
 * 卡**不是开关**，是入口：点进去之后由书签页自己念"未登录"（`VeneraEmptyTone.NotYet`），
 * 并说清去哪儿登录。在 hub 上就把那颗卡置灰，用户就不知道这个功能存在 ——
 * 而"功能存在但要先登录"与"没有这个功能"是两件事。
 */
@Composable
internal fun GalleryBookmarksHubScreen(
    onOpenLocalFavorites: () -> Unit,
    onOpenPixivBookmarks: () -> Unit,
    onBack: () -> Unit,
) {
    val tokens = VeneraTokens
    val topBarBehavior = rememberVeneraTopAppBarBehavior()
    val topBarBackdrop = rememberTopBarBackdrop()
    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    // 与「每日热门」二级页同一条顶栏避让地板（口径出处见冻结声明第七轮）：
    // 这一页没有常驻 chrome，避让就是地板本身。
    val topPadding = statusBarTop + VeneraSpacing.topBarFloor

    // 根节点是一层 Box：内容与顶栏是**兄弟**，顶栏靠 align(TopCenter) 叠在内容之上。
    // 形状与 `GalleryDailyScreen` 那一屏同构（页内自治：外壳不挂 TopAppBar）。
    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(topBarBehavior.nestedScrollConnection),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = tokens.spacing.screenHorizontal)
                    .padding(top = topPadding)
                    // 底栏避让走契约真源（这一页是画廊 Tab 的子页，底栏在位）。
                    .padding(bottom = LocalBottomBarClearance.current),
                verticalArrangement = Arrangement.spacedBy(tokens.spacing.space6),
            ) {
                // ── 两颗卡（用户稿：等宽并排，图标在上、标题居中、副标题居中）──
                Row(horizontalArrangement = Arrangement.spacedBy(tokens.spacing.space4)) {
                    HubCard(
                        icon = Icons.Outlined.FavoriteBorder,
                        title = "本地收藏",
                        subtitle = "Venera 保存的图片",
                        onClick = onOpenLocalFavorites,
                        modifier = Modifier.weight(1f),
                    )
                    HubCard(
                        icon = Icons.Outlined.BookmarkBorder,
                        title = "Pixiv 书签",
                        subtitle = "Pixiv 账号的云端书签",
                        onClick = onOpenPixivBookmarks,
                        modifier = Modifier.weight(1f),
                    )
                }
                // ── 卡下那一行说明（用户稿原文）──
                //
                // 逐字摆稿上那句话：它说的是**两套账的边界**，不是营销文案。
                // 哪天改了这句话，改的是用户对"图存在哪儿"的理解 —— 动它之前先念一遍稿。
                Text(
                    text = "本地收藏继续统一展示 Yande、Gelbooru、Safebooru 和已接入的 Pixiv 作品；" +
                        "Pixiv 书签则单独读取账号数据，不与本地收藏混为一谈。",
                    fontSize = tokens.type.caption,
                    // 行高写死（`TEXT_LINE_HEIGHT` 那一档，1.2f 照抄 miuix paragraph）：
                    // 这一段会折行，不写死的话行高跟着字形走（拉丁 / CJK 差 1~2px），
                    // 与全站那一条「横滑/定高容器里行高必须钉住」同源。
                    lineHeight = TEXT_LINE_HEIGHT.em,
                    color = tokens.color.textTertiary,
                    modifier = Modifier.fillMaxWidth(),
                )
                // 尾部留一截：滚到底时说明文字不贴着底栏（卡与卡之间已是 space6，
                // 这里只补"最后一项与屏底"的那一份）。
                Spacer(modifier = Modifier.height(tokens.spacing.space2))
            }
        }

        VeneraTopAppBar(
            title = "收藏",
            largeTitle = "收藏",
            scrollBehavior = topBarBehavior,
            backdrop = topBarBackdrop,
            navigationIcon = {
                VeneraTopBarPill(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = "返回",
                        tint = tokens.color.textPrimary,
                    )
                }
            },
            modifier = Modifier.align(Alignment.TopCenter),
        )
    }
}

/**
 * hub 上那一颗卡：图标在上、标题与副标题居中（用户稿的形状）。
 *
 * 按压反馈走 [VeneraPressFeedback.card]（miuix 枚举那一档）—— 卡是可点的，
 * 而 `VeneraCard` 的 `pressFeedback` 默认是 `None`，不传就是"按得动、什么都不发生"。
 */
@Composable
private fun HubCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = VeneraTokens
    VeneraCard(
        onClick = onClick,
        pressFeedback = VeneraPressFeedback.card,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = tokens.spacing.space6, horizontal = tokens.spacing.space4),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tokens.color.textSecondary,
                // 24dp（`space10`）：比标题字高一档、又不到图标按钮那一档（40dp）——
                // 这一颗是"这是什么"的记号，不是按钮。
                modifier = Modifier.size(tokens.spacing.space10),
            )
            Spacer(modifier = Modifier.height(tokens.spacing.space3))
            Text(
                text = title,
                fontSize = tokens.type.itemTitle,
                fontWeight = tokens.type.weightSemibold,
                color = tokens.color.textPrimary,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(tokens.spacing.space1))
            Text(
                text = subtitle,
                fontSize = tokens.type.caption,
                color = tokens.color.textTertiary,
                textAlign = TextAlign.Center,
            )
        }
    }
}
