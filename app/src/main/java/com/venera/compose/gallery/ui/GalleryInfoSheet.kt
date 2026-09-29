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
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.venera.compose.components.venera.VeneraCard
import com.venera.compose.components.venera.VeneraChip
import com.venera.compose.components.venera.VeneraChipVariant
import com.venera.compose.gallery.data.GalleryPost
import com.venera.compose.gallery.data.GalleryTagGroup
import com.venera.compose.gallery.data.galleryTagCategoryLabel
import com.venera.compose.gallery.domain.GalleryTagCategory
import com.venera.compose.gallery.domain.buildGalleryTagBuckets
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
 * @param categories 站方对**这张帖**给的「标签 → 档位」（来自那张帖的 HTML，见
 *   [com.venera.compose.gallery.data.GalleryTagCategories]）。null = 这一笔没取到。
 * @param fallbackArtistNames 只在 [categories] 为 null 时参与：离线词典里判为画师的那几枚。
 *   两者**不会同时生效**（页面那边只在站方没给时才查词典），所以这里不需要仲裁规则。
 * @param tagTranslations 标签 → 中文译名（离线词典）。**只改显示**：菜单里那三项与
 *   [onSearchTag] 回给画廊的仍是原文 —— 译文会随词典更新而变，屏蔽词表与检索词都存原值。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GalleryInfoSheet(
    post: GalleryPost,
    categories: Map<String, Int>?,
    fallbackArtistNames: Set<String>,
    tagTranslations: Map<String, String>,
    onDismiss: () -> Unit,
    onSearchTag: (String) -> Unit,
) {
    val tokens = VeneraTokens
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState()
    // 屏蔽规则要落库（suspend），动作归本面板持有；与漫画详情页那三个标签动作同一份分工。
    val guard = remember { ContentGuardManager.getInstance(context) }
    val tagScope = rememberCoroutineScope()

    // 分桶（判据与降级全在 buildGalleryTagBuckets，那边可单测）。
    val groups = remember(post.tagList, categories, fallbackArtistNames) {
        buildGalleryTagBuckets(post.tagList, categories, fallbackArtistNames)
    }
    val artistLabel = galleryTagCategoryLabel(GalleryTagCategory.ARTIST)
    /*
     * **署名三组**（画师 / 角色 / 作品）与那面通用标签墙**分家**（用户 2026-09-29 点名）：
     * 它们摆到左边那张信息卡的**右列**，与尺寸/评分并排；通用那一大排仍留在下面。
     * 原来画师单独占卡里一行，卡越垫越高、标签墙越沉越下面（截图上"通用 31"要滚两屏）。
     */
    val creditLabels = setOf(
        artistLabel,
        galleryTagCategoryLabel(GalleryTagCategory.CHARACTER),
        galleryTagCategoryLabel(GalleryTagCategory.COPYRIGHT),
    )
    val creditGroups = groups.filter { it.label in creditLabels }
    val wallGroups = groups.filterNot { it.label in creditLabels }

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

            /*
             * 两列并排（用户 2026-09-29 点名"和左边的尺寸分开两列显示"）：
             * 左列 = 尺寸/评分/上传者/原图那一卡，右列 = 画师/角色/作品（有则摆）。
             * 右列一枚都没有时**不占位**：那时左列那一枚 weight(1f) 自己铺满整行，
             * 不会留出半屏空白。
             */
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(tokens.spacing.space3),
                verticalAlignment = Alignment.Top,
            ) {
                VeneraCard(modifier = Modifier.weight(1f)) {
                    InfoRow("尺寸", "${post.width}×${post.height}")
                    InfoRow("评分", post.score.toString())
                    // yande.re 实测没有 `fav_count` → null 就整行不摆：摆 0 是"这张零收藏"，
                    // 与"站方没给"是两回事。
                    post.favCount?.let { InfoRow("收藏", it.toString()) }
                    post.durationSeconds?.let { InfoRow("时长", "${it.toInt()} 秒") }
                    // 上传者与画师**是两件事**，以前这里只有一行而且写错了名字。
                    // 实测（方案 §0.6）：yande.re 的 `author` 与 Gelbooru 的 `owner` 都是**上传者的登录名**
                    // —— 真帖 1269641 的 `author=Arsy`，而那张画的画师是标签 `gijang`（现在在右列）。
                    post.author.takeIf { it.isNotBlank() }?.let { InfoRow("上传者", it) }

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
                if (creditGroups.isNotEmpty()) {
                    Column(modifier = Modifier.weight(1f)) {
                        GalleryTagGroups(
                            groups = creditGroups,
                            tagLabel = { tag -> tagDisplayLabel(tag, tagTranslations) },
                            onSearchTag = onSearchTag,
                            onCopyTag = copyTag,
                            onBlockTag = blockTag,
                            showCount = false,
                            leadingSpacer = tokens.spacing.none,
                        )
                    }
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
                groups = wallGroups,
                tagLabel = { tag -> tagDisplayLabel(tag, tagTranslations) },
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
            // 只设**下限**不设定宽：这张卡现在只占半屏，而定宽是按整屏那版留的 ——
            // 「上传者」三个字在 24dp 里会折成两行（真机截图上就是那样）。
            modifier = Modifier.widthIn(min = tokens.spacing.space10).padding(end = tokens.spacing.space2),
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
 * 标签墙，**按站方给的分类分桶**（截图里 `Character` / `Copyright` 那种分组）。
 *
 * 判据与降级都不在这一层：见 [buildGalleryTagBuckets] 与
 * [com.venera.compose.gallery.data.GalleryTagCategories]。
 * 两站的 post JSON 确实不给分类（实测 yande.re 44 个键里没有、Gelbooru 只有一串平铺 `tags`），
 * 但那张贴的 **HTML 条目页**每枚标签都带 `tag-type-*` 类名 —— 那是站方对这张画的判定，
 * 所以这里摆得出的桶名**全部有出处**，仍然没有一处是按标签名猜的。
 * 站方判定取不到时只兜「画师」一栏（离线词典对两站的画师档误报实测 0 例），
 * 其它桶宁可不摆。
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
    tagLabel: (String) -> String,
    onSearchTag: (String) -> Unit,
    onCopyTag: (String) -> Unit,
    onBlockTag: (String) -> Unit,
    /** 署名那一列窄，"角色 1" 这种计数只是噪声；标签墙那边照旧带数。 */
    showCount: Boolean = true,
    /** 第一组**上面**留多高：并排进另一列时首组不该再垫一整段（外层已经对好顶）。 */
    leadingSpacer: Dp = VeneraTokens.spacing.space5,
) {
    val tokens = VeneraTokens
    groups.forEachIndexed { index, group ->
        Spacer(modifier = Modifier.height(if (index == 0) leadingSpacer else tokens.spacing.space5))
        Text(
            text = if (showCount) "${group.label} ${group.tags.size}" else group.label,
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
                    text = tagLabel(tag),
                    onClick = { onSearchTag(tag) },
                    onCopy = { onCopyTag(tag) },
                    onBlock = { onBlockTag(tag) },
                )
            }
        }
    }
}

/**
 * 一枚药丸上的显示文本：**只改显示**。
 *
 * `译文 (原文)` 这个双显形状与漫画侧 [com.venera.compose.data.tags.rememberTagDisplayLabel] 同一口径，
 * 原文常驻括号里是刻意的 —— 词典覆盖不到的标签会退回原文，若同时隐藏原文，
 * 用户就无从判断这枚药丸对应站方那个词。
 *
 * ⚠️ 点它发出去的、复制的、屏蔽落库的都得是**原串**（调用方一直传 `tag`，不传这个返回值）。
 */
internal fun tagDisplayLabel(tag: String, translations: Map<String, String>): String {
    val label = translations[tag.lowercase()]?.takeIf { it.isNotBlank() && it != tag }
    return if (label == null) tag else "$label ($tag)"
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
 *
 * [onCopy]/[onBlock] 给 null 就是"这一枚不带菜单"（画师那一行用它）。
 */
@Composable
private fun GalleryTagChip(
    tag: String,
    text: String,
    onClick: () -> Unit,
    onCopy: (() -> Unit)?,
    onBlock: (() -> Unit)?,
) {
    val tokens = VeneraTokens
    val view = LocalView.current
    var menuExpanded by remember { mutableStateOf(false) }
    Box {
        VeneraChip(
            text = text,
            variant = VeneraChipVariant.Tag,
            onClick = onClick,
            // 条件与下面那块菜单**各写一次**，不用一个布尔变量接着：
            // 那样 Kotlin 认不出 `onCopy` 已经不是 null，只能写成 `?.invoke()` 反而更绕。
            onLongClick = if (onCopy != null && onBlock != null) {
                {
                    view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                    menuExpanded = true
                }
            } else null,
        )
        if (onCopy != null && onBlock != null) DropdownMenu(
            expanded = menuExpanded,
            onDismissRequest = { menuExpanded = false },
        ) {
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
