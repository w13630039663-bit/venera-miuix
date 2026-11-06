package com.venera.compose.reader

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.venera.compose.ui.tokens.StatusColors
import com.venera.compose.ui.tokens.VeneraTokens
import java.util.Calendar
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/**
 * 阅读器常驻信息角标（2026-11-07，用户拍板「双角胶囊」方案）。
 *
 * - **左角**：`当前页/总页`；
 * - **右角**：时钟 + 系统电量（`ACTION_BATTERY_CHANGED` sticky 广播，充电中换充电图标）；
 * - 低于 20% 整组转警示色；
 * - 自动巡航的迷你控制器出现在右下时，右角整组**让位上移**（150ms；减弱动效时直切）；
 * - 显示/隐藏由调用方给 [visible]（控制栏或面板打开时整组让位消失），淡入淡出 120ms。
 *
 * 黑 45% 胶囊 + 轻模糊（与阅读器控制岛同一材质语言），任意漫画底色都可读。
 */
@Composable
fun ReaderHudOverlay(
    visible: Boolean,
    currentPage: Int,
    totalPages: Int,
    shifted: Boolean,
    modifier: Modifier = Modifier,
) {
    val tokens = VeneraTokens
    val context = LocalContext.current

    // 时钟对齐分钟刷新：每轮睡到下一个整分，而不是每 60s 硬轮询（漂移会让分钟跳变）。
    var clock by remember { mutableStateOf(hudClockText()) }
    LaunchedEffect(Unit) {
        while (isActive) {
            clock = hudClockText()
            val secondsLeft = 60 - Calendar.getInstance().get(Calendar.SECOND)
            delay(secondsLeft.coerceAtLeast(1) * 1000L)
        }
    }

    // 电量：注册一次广播长期收；sticky 初值当场补一枪（注册前的那一拍也有数）。
    var batteryPct by remember { mutableIntStateOf(-1) }
    var charging by remember { mutableStateOf(false) }
    DisposableEffect(Unit) {
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                val i = intent ?: return
                val level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                if (level >= 0 && scale > 0) batteryPct = level * 100 / scale
                val status = i.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                    status == BatteryManager.BATTERY_STATUS_FULL
            }
        }
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        context.registerReceiver(null, filter)?.let { receiver.onReceive(context, it) }
        onDispose { runCatching { context.unregisterReceiver(receiver) } }
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(120)),
        exit = fadeOut(tween(120)),
        modifier = modifier,
    ) {
        // 右角让位目标：迷你巡航控制器占住 bottom≈16dp 一条（高约 36dp），
        // 让到它上方 = 24 + 54。数值与设计稿一致。
        val rightBottom: Dp by animateDpAsState(
            targetValue = if (shifted) 78.dp else 24.dp,
            animationSpec = tween(150, easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)),
            label = "hud-right-bottom",
        )
        val lowBattery = batteryPct in 1..19
        val alertColor = MaterialTheme.colorScheme.error

        Box(modifier = Modifier.fillMaxSize()) {
            // 左角：页码
            HudPill(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .navigationBarsPadding()
                    .padding(start = 12.dp, bottom = 24.dp),
            ) {
                Text(
                    text = "$currentPage/$totalPages",
                    color = Color.White,
                    fontSize = tokens.type.caption,
                    style = androidx.compose.ui.text.TextStyle(
                        fontFeatureSettings = "tnum",
                    ),
                )
            }

            // 右角：时钟 + 电量（巡航时整组上移让位）
            HudPill(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 12.dp, bottom = rightBottom),
            ) {
                Text(
                    text = clock,
                    color = Color.White,
                    fontSize = tokens.type.caption,
                    style = androidx.compose.ui.text.TextStyle(fontFeatureSettings = "tnum"),
                )
                // 电量：与系统状态栏同一形态的**环形进度**（不是图标换档）——
                // 环随实时电量扫过对应弧长，充电时中央一枚闪电，低电整组警示红。
                BatteryRing(
                    pct = batteryPct,
                    charging = charging,
                    low = lowBattery,
                    normalColor = Color.White.copy(alpha = 0.92f),
                    alertColor = alertColor,
                )
                if (batteryPct >= 0) {
                    Text(
                        text = "$batteryPct%",
                        color = if (lowBattery) alertColor else Color.White.copy(alpha = 0.92f),
                        fontSize = tokens.type.caption,
                        style = androidx.compose.ui.text.TextStyle(fontFeatureSettings = "tnum"),
                    )
                }
            }
        }
    }
}

/**
 * 环形电量：与系统状态栏同一形态。直径与旁边 12sp 文字等高（13dp），
 * 轨道是前景的 28% 透明环，进度弧从 12 点钟方向按实时电量扫过；
 * 充电时环心叠一枚小闪电。电量未读到（-1）只画空轨道。
 */
@Composable
private fun BatteryRing(
    pct: Int,
    charging: Boolean,
    low: Boolean,
    normalColor: Color,
    alertColor: Color,
) {
    val fg = if (low) alertColor else normalColor
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(13.dp)) {
        androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = 2.dp.toPx()
            val track = fg.copy(alpha = 0.28f)
            drawArc(
                color = track,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                style = Stroke(width = stroke, cap = StrokeCap.Butt),
            )
            if (pct > 0) {
                drawArc(
                    color = fg,
                    startAngle = -90f,
                    sweepAngle = 360f * pct.coerceIn(0, 100) / 100f,
                    useCenter = false,
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
            }
        }
        if (charging) {
            Icon(
                imageVector = Icons.Filled.Bolt,
                contentDescription = null,
                tint = fg,
                modifier = Modifier.size(8.dp),
            )
        }
    }
}

/** 双角胶囊本体：黑 45% + 12dp 圆角，与阅读器控制岛同一套材质语言。 */@Composable
private fun HudPill(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Row(
        modifier = modifier
            .background(
                color = StatusColors.BadgeSurface.copy(alpha = 0.45f),
                shape = RoundedCornerShape(12.dp),
            )
            .padding(horizontal = 10.dp, vertical = 7.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        content()
    }
}

/** `HH:mm`，24 小时制，两位补零。 */
private fun hudClockText(): String {
    val now = Calendar.getInstance()
    return "%02d:%02d".format(now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE))
}
