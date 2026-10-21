package com.venera.compose.components

import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import com.venera.compose.data.db.LocalFavoriteDatabase
import com.venera.compose.source.model.Comic
import com.venera.compose.ui.tokens.StatusColors
import com.venera.compose.ui.tokens.VeneraTokens
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Text

/**
 * 漫画卡片的长按菜单（对齐 master `lib/components/comic.dart:117-160` 的
 * 详情 / 复制标题 / 加入收藏 / 屏蔽）。
 *
 * 「屏蔽」这里只做**屏蔽本作**（COMIC_ID 等值命中），不做 master 那个多选词表：
 * 那个对话框会让你在「标题分词 / 副标题 / 标签」里勾选，分别写进模糊的 blockedWords
 * 与精确的 blockedTags。分词器还没移植，图省事把整条标题存成 KEYWORD 会变成
 * "屏蔽任何标题含这串字的书"，比 master 的窄判据更容易误伤一整批正常漫画。
 * 想按标签/关键词屏蔽走 设置 → 屏蔽与过滤。
 */
@Composable
fun ComicCardContextMenu(
    comic: Comic,
    expanded: Boolean,
    onDismiss: () -> Unit,
    onOpenDetail: () -> Unit,
) {
    if (!expanded) return
    // 2026-10-20（写法 A）：`LocalContext.current` + `BusinessPorts.of(context)` 换成平台服务。
    // 那颗 `of(context)` 吃 `android.content.Context`，一句话就把整颗文件钉在 Android 上，
    // 而它在 `components/` —— 那一层要上桌面。收藏 / 屏蔽 / 剪贴板三个动作与原来的
    // **调用顺序、异常语义、吐司文案逐字保留**，实现搬到 `AndroidUxPlatformServices`。
    val ux = LocalUxPlatformServices.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val tokens = VeneraTokens
    DropdownMenu(expanded = true, onDismissRequest = onDismiss) {
        DropdownMenuItem(
            text = { Text("查看详情", fontSize = tokens.type.caption, color = tokens.color.textPrimary) },
            onClick = { onDismiss(); onOpenDetail() },
        )
        DropdownMenuItem(
            text = { Text("复制标题", fontSize = tokens.type.caption, color = tokens.color.textPrimary) },
            onClick = {
                onDismiss()
                clipboard.setText(AnnotatedString(comic.title))
                ux.toast("标题已复制")
            },
        )
        DropdownMenuItem(
            text = { Text("加入收藏", fontSize = tokens.type.caption, color = tokens.color.textPrimary) },
            onClick = {
                onDismiss()
                scope.launch {
                    val folder = LocalFavoriteDatabase.DEFAULT_FOLDER
                    // addComic 抛异常=收藏夹不存在，返回 false=这本已经在里面了。两种要分开说，
                    // 否则重复收藏看起来像"点了没反应"。
                    val result = ux.saveComicToFavorites(
                        id = comic.id,
                        title = comic.title,
                        author = comic.subTitle,
                        sourceKey = comic.sourceKey,
                        tags = comic.tags,
                        coverPath = comic.cover,
                        folder = folder,
                    )
                    ux.toast(
                        when (result) {
                            is FavoriteSaveResult.Added -> "已收藏到「${result.folder}」"
                            is FavoriteSaveResult.AlreadyThere -> "这本已经在「${result.folder}」里了"
                            // 本地收藏夹是设备上的库操作，异常原文（SQLite / IO）对用户没有可操作性。
                            FavoriteSaveResult.Failed -> "收藏没成功，再试一次"
                        },
                    )
                }
            },
        )
        DropdownMenuItem(
            text = { Text("屏蔽本作", fontSize = tokens.type.caption, color = StatusColors.Failing) },
            onClick = {
                onDismiss()
                scope.launch {
                    val saved = ux.blockComic(comic.id)
                    ux.toast(if (saved) "已屏蔽本作，可在 设置→屏蔽与过滤→作品 里撤销"
                        else "屏蔽失败，请重试")
                }
            },
        )
    }
}
