package com.venera.compose.components

import android.content.Context
import android.widget.Toast
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import com.venera.compose.data.db.FavoriteItem
import com.venera.compose.data.db.LocalFavoriteDatabase
import com.venera.compose.data.db.LocalFavoritesManager
import com.venera.compose.security.guard.ContentGuardManager
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
    val context = LocalContext.current
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
                toast(context, "标题已复制")
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
                    val added = runCatching {
                        LocalFavoritesManager.getInstance(context).addComic(
                            folder,
                            FavoriteItem(
                                id = comic.id,
                                name = comic.title,
                                author = comic.subTitle,
                                sourceKey = comic.sourceKey,
                                tags = comic.tags,
                                coverPath = comic.cover,
                            ),
                        )
                    }
                    toast(context, when {
                        added.getOrDefault(false) -> "已收藏到「$folder」"
                        added.isSuccess -> "这本已经在「$folder」里了"
                        else -> "收藏失败：${added.exceptionOrNull()?.let {
                            it.message?.takeIf(String::isNotBlank) ?: it.javaClass.simpleName
                        } ?: "未知原因"}"
                    })
                }
            },
        )
        DropdownMenuItem(
            text = { Text("屏蔽本作", fontSize = tokens.type.caption, color = StatusColors.Failing) },
            onClick = {
                onDismiss()
                scope.launch {
                    val saved = ContentGuardManager.getInstance(context)
                        .addRule("COMIC_ID", comic.id) >= 0
                    toast(context, if (saved) "已屏蔽本作，可在 设置→屏蔽与过滤→作品 里撤销"
                        else "屏蔽失败，请重试")
                }
            },
        )
    }
}

private fun toast(context: Context, message: String) =
    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
