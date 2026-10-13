package com.venera.compose.gallery.watercolor

import android.graphics.Bitmap
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Done
import androidx.compose.material.icons.outlined.RemoveRedEye
import androidx.compose.material.icons.outlined.Replay
import androidx.compose.material.icons.outlined.SaveAlt
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import coil3.BitmapImage
import coil3.ImageLoader
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import com.venera.compose.components.venera.VeneraIconButton
import com.venera.compose.gallery.data.GalleryPost
import com.venera.compose.gallery.data.GallerySaver
import com.venera.compose.gallery.ui.galleryCacheKey
import com.venera.compose.gallery.ui.galleryLargeRequest
import com.venera.compose.ui.tokens.VeneraTokens
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.random.Random
import top.yukonga.miuix.kmp.basic.Text

/** 绘制浮层的四个阶段（外加一个失败出口）。 */
private enum class PainterStage { LOADING, PLANNING, PAINTING, DONE, FAILED }

/** 一幅正在画的画：规划 + 渲染器 + 给 Compose 的贴图句柄。 */
private class PainterEngine(
    val renderer: WatercolorRenderer,
    val plan: WatercolorPlan,
    val image: ImageBitmap,
)

/** 每秒钟落多少笔。110 笔/秒 ≈ 3000 笔画 27 秒 —— 够看又不至于等得烦。 */
private const val STROKES_PER_SECOND = 110f

@Composable
fun WatercolorPainterScreen(
    post: GalleryPost,
    imageLoader: ImageLoader,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val tokens = VeneraTokens
    val scope = rememberCoroutineScope()

    var stage by remember(post.uid) { mutableStateOf(PainterStage.LOADING) }
    var failReason by remember(post.uid) { mutableStateOf("") }
    // 重画 = 换种子：规划、构图、尿点全换，同一张图画出另一幅。
    var seed by remember(post.uid) { mutableStateOf(Random.nextLong()) }
    var engine by remember(post.uid) { mutableStateOf<PainterEngine?>(null) }
    var applied by remember(post.uid) { mutableIntStateOf(0) }
    // 帧计数：只在绘制期读它来触发重绘 —— 它不进组合，翻页/存画都不该惊动整棵树。
    val frameTick = remember(post.uid) { mutableIntStateOf(0) }
    var showOriginal by remember(post.uid) { mutableStateOf(false) }
    var saving by remember(post.uid) { mutableStateOf(false) }

    // ── 取图 → 规划 → 开画布（LOADING → PLANNING → PAINTING）──
    // seed 是 key：重画换颗种子，这一趟从头再来（新画布、新规划）。
    LaunchedEffect(post.uid, seed) {
        stage = PainterStage.LOADING
        val bitmap = withContext(Dispatchers.IO) {
            runCatching {
                // 键与屏上那张大图**同一颗**：命中内存缓存就不再下一遍。
                val request = ImageRequest.Builder(context)
                    .data(post.largeUrl)
                    .memoryCacheKey(galleryCacheKey("large", post))
                    .diskCacheKey(galleryCacheKey("large", post))
                    .allowHardware(false) // 规划要 getPixels，硬件位图读不了
                    .build()
                val result = imageLoader.execute(request)
                (result as? SuccessResult)?.let { (it.image as? BitmapImage)?.bitmap }
            }.getOrNull()
        }
        if (bitmap == null) {
            failReason = "这张图的像素取不到，画不了"
            stage = PainterStage.FAILED
            return@LaunchedEffect
        }
        stage = PainterStage.PLANNING
        val plan = withContext(Dispatchers.Default) {
            WatercolorPlanner.plan(WatercolorRenderer.downscaleForPlanning(bitmap), seed)
        }
        val renderer = WatercolorRenderer.create(bitmap, plan)
        engine = PainterEngine(renderer, plan, renderer.asImageBitmap())
        applied = 0
        stage = PainterStage.PAINTING
    }

    // ── 绘制循环：每帧按时间预算补几笔 ──
    LaunchedEffect(engine, stage) {
        val e = engine ?: return@LaunchedEffect
        if (stage != PainterStage.PAINTING) return@LaunchedEffect
        val total = e.plan.strokes.size
        var last = 0L
        while (true) {
            withFrameNanos { t ->
                if (last == 0L) last = t
                // 单帧最多追 0.25 秒的账：从浮层外回来时不要一口气补一百笔。
                val dt = ((t - last) / 1_000_000_000.0).coerceAtMost(0.25)
                last = t
                val target = e.renderer.applied + (STROKES_PER_SECOND * dt).roundToInt()
                while (e.renderer.applied < target && e.renderer.applied < total) {
                    e.renderer.drawStroke(e.plan.strokes[e.renderer.applied])
                }
                applied = e.renderer.applied
                frameTick.intValue++
                if (e.renderer.applied >= total) stage = PainterStage.DONE
            }
            if (stage != PainterStage.PAINTING) break
        }
    }

    BackHandler { onClose() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        // ── 画布：Fit 居中，四周留黑 ──
        val e = engine
        if (e != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(tokens.shape.large))
                    .drawBehind {
                        // 读 frameTick 只为登记"这笔又变了"；drawImage 画的是位图**当前**像素。
                        val tick = frameTick.intValue
                        if (tick >= 0) drawFit(e.image)
                    },
            )
        }

        // ── 顶栏：关闭 + 阶段与进度 ──
        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = tokens.spacing.space2, vertical = tokens.spacing.space2),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            VeneraIconButton(onClick = onClose) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "收起这幅画",
                    tint = Color.White,
                )
            }
            Column(modifier = Modifier.padding(start = tokens.spacing.space2)) {
                val title = when (stage) {
                    PainterStage.LOADING -> "正在取图…"
                    PainterStage.PLANNING -> "正在构图…"
                    PainterStage.PAINTING -> "正在画·${
                        phaseName(e?.plan?.phaseAt(applied) ?: WatercolorPhase.WASH)
                    } ${(applied * 100 / (e?.plan?.strokes?.size ?: 1))}%"
                    PainterStage.DONE -> "画好了"
                    PainterStage.FAILED -> failReason
                }
                Text(
                    text = title,
                    color = Color.White,
                    fontSize = tokens.type.body,
                )
                if (stage == PainterStage.PAINTING && e != null) {
                    val total = e.plan.strokes.size
                    Box(
                        modifier = Modifier
                            .padding(top = tokens.spacing.space2)
                            .fillMaxWidth(0.5f)
                            .height(tokens.spacing.autoPlayProgressThickness)
                            .clip(RoundedCornerShape(tokens.shape.small))
                            .background(Color.White.copy(alpha = 0.25f))
                            .drawBehind {
                                val p = (applied.toFloat() / total).coerceIn(0f, 1f)
                                drawRect(
                                    color = Color.White,
                                    size = Size(size.width * p, size.height),
                                )
                            },
                    )
                }
            }
        }

        // ── 中央：取图/构图中的环；失败的一句话 ──
        if (stage == PainterStage.LOADING || stage == PainterStage.PLANNING) {
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                CircularWavyProgressIndicator(
                    modifier = Modifier.size(tokens.spacing.loaderPage),
                    color = Color.White,
                    trackColor = Color.White.copy(alpha = 0.2f),
                )
            }
        }
        if (stage == PainterStage.FAILED) {
            Row(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(tokens.spacing.screenHorizontal),
            ) {
                Text(
                    text = failReason,
                    color = Color.White,
                    fontSize = tokens.type.body,
                )
            }
        }

        // ── 画成之后的动作条：保存 / 重画 / 看原图 / 完成 ──
        AnimatedVisibility(
            visible = stage == PainterStage.DONE,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            Row(
                modifier = Modifier
                    .navigationBarsPadding()
                    .padding(
                        start = tokens.spacing.screenHorizontal,
                        end = tokens.spacing.screenHorizontal,
                        bottom = tokens.spacing.space5,
                    )
                    .clip(RoundedCornerShape(tokens.shape.extraLarge))
                    .background(Color.Black.copy(alpha = 0.55f))
                    .padding(horizontal = tokens.spacing.space4, vertical = tokens.spacing.space2),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(tokens.spacing.space3),
            ) {
                DockButton(
                    icon = Icons.Outlined.SaveAlt,
                    label = if (saving) "存图中…" else "保存",
                    enabled = !saving,
                ) {
                    val bmp = e?.renderer?.outputBitmap()
                    if (bmp != null) {
                        saving = true
                        scope.launch {
                            val saved = withContext(Dispatchers.IO) {
                                runCatching {
                                    // 复制一份再存：重画会换画布，别让保存和重绘抢同一块位图。
                                    val snapshot = bmp.copy(Bitmap.Config.ARGB_8888, false)
                                    GallerySaver.saveArtwork(context, snapshot, artworkName(post)).getOrThrow()
                                }.getOrNull()
                            }
                            saving = false
                            Toast.makeText(
                                context,
                                if (saved != null) "画作已存进相册：${saved.path}" else "保存失败了",
                                Toast.LENGTH_LONG,
                            ).show()
                        }
                    }
                }
                DockButton(
                    icon = Icons.Outlined.Replay,
                    label = "重画",
                ) {
                    showOriginal = false
                    seed = Random.nextLong()
                }
                DockButton(
                    icon = Icons.Outlined.RemoveRedEye,
                    label = if (showOriginal) "看画" else "看原图",
                ) {
                    showOriginal = !showOriginal
                }
                DockButton(
                    icon = Icons.Outlined.Done,
                    label = "完成",
                ) { onClose() }
            }
        }

        // ── 看原图：原档淡入盖在画上 ──
        AnimatedVisibility(
            visible = showOriginal,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black),
                contentAlignment = Alignment.Center,
            ) {
                AsyncImage(
                    model = galleryLargeRequest(context, post, false),
                    contentDescription = "${post.site.displayName} #${post.id} 原图",
                    contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

/** 阶段名 —— 摆上屏的三个字，不带实现笔记。 */
private fun phaseName(phase: WatercolorPhase): String = when (phase) {
    WatercolorPhase.WASH -> "晕染"
    WatercolorPhase.BACKGROUND -> "背景"
    WatercolorPhase.FORMS -> "主体"
    WatercolorPhase.DETAILS -> "细节"
}

/** 保存文件名：站名 + 作品号 + 时刻。 */
private fun artworkName(post: GalleryPost): String {
    val stamp = java.time.LocalDateTime.now()
        .format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
    return "水彩_${post.site.routeKey}_${post.id}_$stamp.png"
}

/** 动作条上的一枚钮：图标 + 两个字的说明（ dock 上不能只有图标让人猜）。 */
@Composable
private fun DockButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val tokens = VeneraTokens
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        VeneraIconButton(onClick = onClick, enabled = enabled) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (enabled) Color.White else Color.White.copy(alpha = 0.4f),
            )
        }
        Text(
            text = label,
            color = if (enabled) Color.White else Color.White.copy(alpha = 0.4f),
            fontSize = tokens.type.caption,
        )
    }
}

/** 把位图按 Fit 画进当前 DrawScope（居中、不变形）。 */
private fun DrawScope.drawFit(image: ImageBitmap) {
    val iw = image.width.toFloat()
    val ih = image.height.toFloat()
    val scale = min(size.width / iw, size.height / ih)
    val dw = (iw * scale).roundToInt()
    val dh = (ih * scale).roundToInt()
    drawImage(
        image = image,
        dstOffset = IntOffset(((size.width - dw) / 2f).roundToInt(), ((size.height - dh) / 2f).roundToInt()),
        dstSize = androidx.compose.ui.unit.IntSize(dw, dh),
    )
}

