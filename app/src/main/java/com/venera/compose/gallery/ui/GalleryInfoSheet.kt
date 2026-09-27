package com.venera.compose.gallery.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.HapticFeedbackConstants
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.venera.compose.components.venera.VeneraCard
import com.venera.compose.components.venera.VeneraChip
import com.venera.compose.components.venera.VeneraChipVariant
import com.venera.compose.gallery.data.GalleryPost
import com.venera.compose.gallery.data.GalleryTagGroup
import com.venera.compose.security.guard.ContentGuardManager
import com.venera.compose.ui.tokens.StatusColors
import com.venera.compose.ui.tokens.VeneraSpacing
import com.venera.compose.ui.tokens.VeneraTokens
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Text

/**
 * 「关于这张图」半模态（照 Breadboard 的 `InfoSheet`，用户 2026-09-25 点名复刻）。
 * 它**取代**二级页原来那两张往下滚的卡 —— 二级现在是满屏图 + 底部工具条，信息收在这里。
 *
 * 用 M3 的 `ModalBottomSheet`，不用 miuix 那个 `OverlayBottomSheet`：
 * 后者把内容渲染在独立窗口里，读不到 `CompositionLocal`，会崩
 * （同一个坑记在 `feature/SearchScreen.kt:1500` 与 `:1645` 的注释里）。
 *
 * **只摆站方真给的字段**：参考截图里那行 `Pixiv URL` 与底部 `Sources | Imageboard` 分段，
 * 是它把"上游出处"和"原站作品页"分开存，而我们只有 `source` 一个串
 * （实测 yande.re 40 条里 3 条为空 → 空就整块不摆）。没有的字段一律不编。
 *
 * @param onSearchTag 用户点了某一枚标签：把这枚标签交回一级画廊去搜（页面那边负责关 sheet、
 *   关本页，见 [GallerySearchHandoff]）。**这里不自己发请求** —— 搜索结果归画廊那一屏的
 *   ViewModel 持有，在这一页里另起一份就会出现在两处各存一半结果的分叉。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GalleryInfoSheet(
    post: GalleryPost,
    onDismiss: () -> Unit,
    onSearchTag: (String) -> Unit,
) {
    val tokens = VeneraTokens
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState()
    // 屏蔽规则要落库（suspend），动作归本面板持有；与漫画详情页那三个标签动作同一份分工。
    val guard = remember { ContentGuardManager.getInstance(context) }
    val tagScope = rememberCoroutineScope()

    val copyTag: (String) -> Unit = { tag -> copyToClipboard(context, "标签「$tag」", tag) }
    val blockTag: (String) -> Unit = { tag ->
        tagScope.launch {
            // 一律存成 TAG 规则，且存**站点原值**（译文会随字典更新失效）。选 TAG 而不是 KEYWORD
            // 是量出来的：画廊那面墙的屏蔽判据只喂 `post.tagList`，而两站的 `tags`
            // 就是那一串平铺标签（Gelbooru 实测没有 `tag_string_*` 那种分桶串）——
            // 所以这里每一枚 chip 的原串都必然在 tagList 里，规则加下去一定挡得住
            // （用 KEYWORD 会连作者名一起误伤）。
            val exists = guard.rules.value
                .any { it.type == "TAG" && it.pattern.equals(tag, ignoreCase = true) }
            when {
                exists -> Toast.makeText(context, "「$tag」已在屏蔽列表", Toast.LENGTH_SHORT).show()
                guard.addRule("TAG", tag) >= 0 -> Toast.makeText(
                    context, "已屏蔽「$tag」，可在设置 → 内容屏蔽管理", Toast.LENGTH_SHORT,
                ).show()
                else -> Toast.makeText(context, "屏蔽失败，请重试", Toast.LENGTH_SHORT).show()
            }
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                // 标签多的条目会顶到窗口上限，靠滚动看全；
                // 这里不另定最大高度 —— M3 的 sheet 自己会裁到窗口内，造一个数字只会比它更矮。
                .verticalScroll(rememberScrollState())
                .padding(horizontal = tokens.spacing.screenHorizontal),
        ) {
            Text(
                text = "关于这张图",
                fontSize = tokens.type.sectionTitle,
                fontWeight = tokens.type.weightBold,
                color = tokens.color.textPrimary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(tokens.spacing.space5))

            // 两张小卡：取值在上、字段名在下，与截图同一读法。
            Row(horizontalArrangement = Arrangement.spacedBy(tokens.spacing.space3)) {
                FactTile(value = ratingLabel(post.rating), label = "分级", modifier = Modifier.weight(1f))
                FactTile(value = post.site.displayName, label = "站点", modifier = Modifier.weight(1f))
            }
            Spacer(modifier = Modifier.height(tokens.spacing.space3))

            VeneraCard(modifier = Modifier.fillMaxWidth()) {
                InfoRow("尺寸", "${post.width}×${post.height}")
                InfoRow("评分", post.score.toString())
                // yande.re 实测没有 `fav_count` → null 就整行不摆：摆 0 是"这张零收藏"，
                // 与"站方没给"是两回事。
                post.favCount?.let { InfoRow("收藏", it.toString()) }
                post.durationSeconds?.let { InfoRow("时长", "${it.toInt()} 秒") }
                // 作者单独一行（两站都只有一桶标签，画师不会在桶里另列一遍，
                // 所以这一行不是重复信息）。
                if (post.tagGroups.none { it.label == "画师" }) {
                    InfoRow("作者", post.author.ifBlank { "—" })
                }
                // 原图那一档的体积与指纹。**读数没有就不摆**：Gelbooru 的 post JSON 根本没有
                // file_size（`GelbooruClient.kt:62` 记着这条天花板，也写了"UI 那边没这个读数就不显示"），
                // 解析处只能一律填 0 —— 旧写法于是每张 Gelbooru 图都写着「0 MB」，
                // 看着像"这文件是空的"。同一处判据 `GalleryVideoViewer.kt:130` 早修过，这是漏网的第二份。
                // 有值也必须走一位小数：整数除法会把 yande.re 上不到 1.5 MB 的真文件也压成「0 MB」。
                val fileFacts = listOfNotNull(
                    post.fileSize.takeIf { it > 0 }?.let { "%.1f MB".format(it / 1024.0 / 1024.0) },
                    post.md5.takeIf { it.isNotBlank() }?.let { "md5 ${it.take(8)}" },
                )
                if (fileFacts.isNotEmpty()) {
                    InfoRow(if (post.isVideo) "视频" else "原图", fileFacts.joinToString(" · "))
                }
            }

            // 可复制的两行：上游出处 + 本站单页地址。出处为空就整块不摆。
            if (post.source.isNotBlank()) {
                Spacer(modifier = Modifier.height(tokens.spacing.space3))
                CopyRow(label = "出处", value = post.source) { copyToClipboard(context, "出处", it) }
            }
            Spacer(modifier = Modifier.height(tokens.spacing.space3))
            CopyRow(label = "本站地址", value = post.pageUrl) { copyToClipboard(context, "本站地址", it) }
            // 顶栏整条去掉之后，"在站点打开"这个入口搬到这里 —— 不能因为没了顶栏就把功能弄丢。
            Spacer(modifier = Modifier.height(tokens.spacing.space3))
            LinkRow(
                label = "在 ${post.site.displayName} 打开",
                value = post.pageUrl,
                onClick = {
                    runCatching {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse(post.pageUrl))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    }.onFailure {
                        Toast.makeText(context, "打不开浏览器：${it.message}", Toast.LENGTH_SHORT).show()
                    }
                },
            )

            GalleryTagGroups(
                groups = post.tagGroups,
                onSearchTag = onSearchTag,
                onCopyTag = copyTag,
                onBlockTag = blockTag,
            )
            Spacer(modifier = Modifier.height(VeneraSpacing.bottomBarClearance))
        }
    }
}

/** 一张"值 + 字段名"的小卡（截图里 `General / Rating` 那种读法）。 */
@Composable
private fun FactTile(value: String, label: String, modifier: Modifier = Modifier) {
    val tokens = VeneraTokens
    VeneraCard(modifier = modifier) {
        Text(
            text = value,
            fontSize = tokens.type.itemTitle,
            fontWeight = tokens.type.weightSemibold,
            color = tokens.color.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = label,
            fontSize = tokens.type.caption,
            color = tokens.color.textTertiary,
        )
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    val tokens = VeneraTokens
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = tokens.spacing.space1),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = label,
            fontSize = tokens.type.caption,
            color = tokens.color.textTertiary,
            modifier = Modifier.width(tokens.spacing.space10),
        )
        Text(
            text = value,
            fontSize = tokens.type.caption,
            color = tokens.color.textPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

/** 一行"值 + 复制"。值可能很长（pixiv 那种带日期的原图地址），所以最多两行并截断。 */
@Composable
private fun CopyRow(label: String, value: String, onCopy: (String) -> Unit) {
    val tokens = VeneraTokens
    VeneraCard(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    fontSize = tokens.type.caption,
                    color = tokens.color.textTertiary,
                )
                Text(
                    text = value,
                    fontSize = tokens.type.body,
                    color = tokens.color.textPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = { onCopy(value) }) {
                Icon(
                    imageVector = Icons.Outlined.ContentCopy,
                    contentDescription = "复制$label",
                    tint = tokens.color.textPrimary,
                )
            }
        }
    }
}

/** 一行"值 + 打开原站"。与 [CopyRow] 同一形状，只是尾巴那枚钮的动作不同。 */
@Composable
private fun LinkRow(label: String, value: String, onClick: () -> Unit) {
    val tokens = VeneraTokens
    VeneraCard(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    fontSize = tokens.type.caption,
                    color = tokens.color.textTertiary,
                )
                Text(
                    text = value,
                    fontSize = tokens.type.body,
                    color = tokens.color.textPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = onClick) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.OpenInNew,
                    contentDescription = label,
                    tint = tokens.color.textPrimary,
                )
            }
        }
    }
}

/**
 * 标签区，**按站方给的分类分桶**（截图里 `Character` / `Copyright` 那种分组）。
 *
 * ⚠️ 两站的 **post 端点都不给分类**（实测）：yande.re 的 44 个键里没有任何分类字段，
 * Gelbooru 也只有一串平铺 `tags`（没有 `tag_string_*` 那五串）→ **两站都只能一桶**「标签」。
 * 这里不硬造分组（见 `GalleryPost.tagGroups` 的说明）——硬按标签名猜分类
 * 会在下一版站方数据变化时静默分错，比不分更坏。
 *
 * chips 是**可点的**（用户 2026-09-25 点名，语义与漫画详情页那枚药丸一模一样）：
 * 点击=用这枚标签去画廊搜索，长按=「搜索 / 复制 / 屏蔽」三项菜单。
 * 早先这里写的是"刻意不可点，因为 `/tag.json` 那条链还没实测" —— 那条前提已经作废：
 * 两站的标签检索与结果接口都实测过了（见 `gallery-search-2026-09.md` §〇）。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GalleryTagGroups(
    groups: List<GalleryTagGroup>,
    onSearchTag: (String) -> Unit,
    onCopyTag: (String) -> Unit,
    onBlockTag: (String) -> Unit,
) {
    val tokens = VeneraTokens
    groups.forEach { group ->
        Spacer(modifier = Modifier.height(tokens.spacing.space5))
        Text(
            text = "${group.label} ${group.tags.size}",
            fontSize = tokens.type.itemTitle,
            fontWeight = tokens.type.weightSemibold,
            color = tokens.color.textPrimary,
        )
        Spacer(modifier = Modifier.height(tokens.spacing.space3))
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(tokens.spacing.space2),
            verticalArrangement = Arrangement.spacedBy(tokens.spacing.space2),
        ) {
            group.tags.forEach { tag ->
                GalleryTagChip(
                    tag = tag,
                    onClick = { onSearchTag(tag) },
                    onCopy = { onCopyTag(tag) },
                    onBlock = { onBlockTag(tag) },
                )
            }
        }
    }
}

/**
 * 一枚可交互的标签药丸 —— **画法照 `feature/ComicDetailScreen.kt` 的 `DetailTagChip`**：
 * 点击直达该标签搜索，长按起菜单。按压反馈由 [VeneraChip] 统一持有，这里只给回调。
 *
 * 菜单**逐项各挂一个**：FlowRow 排布下共用一个锚会让菜单永远从第一枚的位置弹出来
 * （详情页已经踩过，`FavoriteImagesScreen.kt:601` 那条注释记着同一件事）。
 *
 * 三项里"搜索"与点击重复，是**刻意**的：长按菜单是"这块区域能做什么"的目录，
 * 少了这一项，用户只能靠猜知道点击也可以。
 */
@Composable
private fun GalleryTagChip(
    tag: String,
    onClick: () -> Unit,
    onCopy: () -> Unit,
    onBlock: () -> Unit,
) {
    val tokens = VeneraTokens
    val view = LocalView.current
    var menuExpanded by remember { mutableStateOf(false) }
    Box {
        VeneraChip(
            text = tag,
            variant = VeneraChipVariant.Tag,
            onClick = onClick,
            onLongClick = {
                view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                menuExpanded = true
            },
        )
        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
            DropdownMenuItem(
                text = {
                    Text("搜索「$tag」", fontSize = tokens.type.caption, color = tokens.color.textPrimary)
                },
                onClick = {
                    menuExpanded = false
                    onClick()
                },
            )
            DropdownMenuItem(
                text = {
                    Text("复制「$tag」", fontSize = tokens.type.caption, color = tokens.color.textPrimary)
                },
                onClick = {
                    menuExpanded = false
                    onCopy()
                },
            )
            DropdownMenuItem(
                text = {
                    Text("屏蔽该标签", fontSize = tokens.type.caption, color = StatusColors.Failing)
                },
                onClick = {
                    menuExpanded = false
                    onBlock()
                },
            )
        }
    }
}

/**
 * 分级值 → 人话。
 *
 * ⚠️ **两站的字形不同**（实测），所以这张表要同时收两套：
 * - yande.re：单字母 `s` / `q` / `e`；
 * - Gelbooru：单词 `general` / `sensitive` / `questionable` / `explicit`。
 *
 * 只认单字母的话，Gelbooru 的每一条都会显示成「未知」—— 那不是"数据缺失"，
 * 是我们没认出来，属于最没必要的一种显示瑕疵。
 */
internal fun ratingLabel(rating: String): String = when (rating.lowercase()) {
    "s", "safe" -> "安全 (s)"
    "g", "general" -> "一般 (general)"
    "sensitive" -> "敏感 (sensitive)"
    "q", "questionable" -> "存疑 (questionable)"
    "e", "explicit" -> "成人 (explicit)"
    else -> "未知 (${rating.ifBlank { "空" }})"
}

private fun copyToClipboard(context: Context, label: String, value: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText(label, value))
    Toast.makeText(context, "已复制$label", Toast.LENGTH_SHORT).show()
}
