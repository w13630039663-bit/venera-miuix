package com.venera.compose.gallery.ui

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.ImageSearch
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import com.venera.compose.components.isWideScreen
import com.venera.compose.gallery.data.GalleryImageLoader
import com.venera.compose.ui.tokens.VeneraTokens
import top.yukonga.miuix.kmp.basic.Text

/**
 * 反搜态在共享搜索卡里的**下半身**：隐私提示 + 两个动作 chip + 预览。
 *
 * ## 2026-09-28 形变改造后，本组件**瘦身了一半**
 *
 * 「图从哪来」那条输入槽（图标 + 链接框 + 尾随钮）**搬走了** —— 搬进
 * `GallerySearchArea` 里那条两态共用的输入行（图标 / 占位 / 尾随钮都在原地过渡）。
 * 这里只留反搜态**专属**的东西。原因：形变要求"输入行永远在组、原地变化"，
 * 若本组件还自带一条输入行，切过来时就会出现**两条一模一样的槽**。
 *
 * 与输入槽的分工：`GallerySearchArea` 管输入与尾随动作（含粘贴），
 * 本组件管"按下之后会发生什么"的说明与动作钮。剪贴板读取那段也跟着搬走了
 * （尾随钮是粘贴的宿主）。
 *
 * ## 三条刻意的取舍（沿用）
 *
 * - **没配 key 照发**：SauceNAO 匿名能搜，只是额度小。这里只把这件事**说出来**，
 *   不拦、不假装拦不住（拦了就是把一个能用的功能做成假开关）。
 * - **隐私那句必须写**：这是把用户挑的图上传给一个外部服务，不写就等于没说。
 * - **不做"识别剪贴板里的图"**：Breadboard 那一路要读剪贴板的 URI 权限，
 *   而我们已经有一个明确的「从本机选图」入口，不值得为省一次点击多开一个权限面。
 */
@Composable
fun GalleryReverseSearchArea(
    rvm: GalleryReverseViewModel,
    allowNsfw: Boolean,
    modifier: Modifier = Modifier,
) {
    val tokens = VeneraTokens
    val context = LocalContext.current
    val cardShape = RoundedCornerShape(tokens.shape.extraLarge)
    val hasKey by rvm.hasKey.collectAsState()

    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri -> uri?.let { rvm.pick(it) } }

    val picked = rvm.picked

    Column(modifier = modifier.fillMaxWidth()) {
        // ── 这一张会发给谁（必须写，见头注）──
        SearchNoticeLine(
            "这张图会发送给 SauceNAO（第三方服务）" +
                if (hasKey) "" else "。没配 API Key 时这一笔发不出去（实测匿名过不去 Cloudflare）",
        )

        rvm.notice?.let {
            Spacer(modifier = Modifier.height(tokens.spacing.space2))
            SearchNoticeLine(it)
        }

        Spacer(modifier = Modifier.height(tokens.spacing.space4))

        // ── 两个动作 ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = tokens.spacing.space8)
                .padding(bottom = tokens.spacing.space6),
            horizontalArrangement = Arrangement.spacedBy(tokens.spacing.space4),
        ) {
            ReverseChip(
                icon = Icons.Outlined.PhotoLibrary,
                label = "从本机选图",
                filled = false,
                onClick = { picker.launch("image/*") },
                modifier = Modifier.weight(1f),
            )
            ReverseChip(
                icon = Icons.Outlined.Search,
                label = if (rvm.isSearching) "搜索中" else "搜相似",
                filled = true,
                // 搜索中**不置灰、只不响应**：置灰的淡主色按钮在这套主题里读起来像坏了，
                // 而这一颗此刻确实不该再发一笔 —— 屏上那圈波浪环才是它的反馈。
                onClick = { if (!rvm.isSearching) rvm.submit(allowNsfw) },
                modifier = Modifier.weight(1f),
            )
        }

        // ── 预览：挑中或链接起来之后才出现，让"要发的是哪一张"看得见 ──
        val previewModel: Any? = picked?.uri ?: rvm.url.takeIf { it.isNotBlank() && it.isHttpUrl() }
        if (previewModel != null) {
            Box(
                modifier = Modifier
                    .padding(horizontal = tokens.spacing.space8)
                    .padding(bottom = tokens.spacing.space6)
                    .size(if (isWideScreen(LocalConfiguration.current.screenWidthDp.dp)) 96.dp else 64.dp)
                    .clip(cardShape)
                    .background(tokens.color.surfaceVariant),
            ) {
                AsyncImage(
                    model = previewModel,
                    contentDescription = "要发送给 SauceNAO 的那张图",
                    imageLoader = GalleryImageLoader.get(context),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

/** 与标签搜索那侧的胶囊同一种件，只是这里两颗都是动作而不是条件。 */
@Composable
private fun ReverseChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    filled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = VeneraTokens
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(tokens.shape.large))
            .background(
                if (filled) tokens.color.primaryContainer else tokens.color.surfaceVariant
            )
            .clickable { onClick() }
            .padding(horizontal = tokens.spacing.space6, vertical = tokens.spacing.space5),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (filled) tokens.color.onPrimaryContainer else tokens.color.textPrimary,
            modifier = Modifier.size(tokens.spacing.chipIconSize),
        )
        Spacer(modifier = Modifier.width(tokens.spacing.space3))
        Text(
            text = label,
            fontSize = tokens.type.body,
            color = if (filled) tokens.color.onPrimaryContainer else tokens.color.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** 只判"像不像 http 链接"，与解析层同一条口径，不做完整 URI 解析。 */
private fun String.isHttpUrl(): Boolean =
    startsWith("http://", ignoreCase = true) || startsWith("https://", ignoreCase = true)

/** 让外部浏览器接手一条链接；解不动时说一句，不静默。 */
internal fun openExternally(context: android.content.Context, url: String) {
    runCatching {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }.onFailure {
        android.widget.Toast.makeText(context, "打不开这条链接：$url", android.widget.Toast.LENGTH_SHORT)
            .show()
    }
}
