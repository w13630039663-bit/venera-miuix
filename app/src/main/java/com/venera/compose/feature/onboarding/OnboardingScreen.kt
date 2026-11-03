package com.venera.compose.feature.onboarding

import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Monitor
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.venera.compose.components.LocalWindowWidthDp
import com.venera.compose.components.contentMaxWidthFor
import com.venera.compose.components.wideScreenChromeMaxWidth
import com.venera.compose.components.WideScreenLayoutMode
import com.venera.compose.components.wideScreenLayoutMode
import com.venera.compose.data.platform.SurfaceMaterial
import com.venera.compose.data.platform.TagTranslationMode
import com.venera.compose.data.platform.ThemeColorSource
import com.venera.compose.data.platform.ThemeMode
import com.venera.compose.data.api.AppearancePreferences
import com.venera.compose.data.api.ComicPreferences
import com.venera.compose.data.api.NetworkPreferences
import com.venera.compose.gallery.data.GelbooruIdentity
import com.venera.compose.gallery.data.PixivAuthState
import com.venera.compose.ui.tokens.ThemeSeedPreset
import com.venera.compose.ui.tokens.VeneraTokens
import kotlinx.coroutines.flow.StateFlow
import com.venera.compose.ui.tokens.ThemeSeedPresets

/**
 * 引导屏要的**全部外部依赖**，由装配点（`MainActivity`）组好递进来。
 *
 * 为什么不给屏一个 Context 让它自己去 `getInstance`/`of`：`BusinessApiBoundaryTest` 那条尺
 * 早就量过 —— UI 目录不许直连业务实现类单例，存量都在逐批摘除，新界面上绝不新增。
 * 三枚偏好走 `data/api` 的接口；账号两条走**判据件的流**；跳转是装配点闭好 Context 的回调。
 */
@Immutable
data class OnboardingPorts(
    val appearance: AppearancePreferences,
    val comic: ComicPreferences,
    val network: NetworkPreferences,
    /** pixiv 登录态。桌面没接这一路 ⇒ 可空；null 时那一行摆"未登录"。 */
    val pixivAuth: StateFlow<PixivAuthState>?,
    /** Gelbooru 身份流（null = 没配）。 */
    val gelbooruIdentity: StateFlow<GelbooruIdentity?>,
    val onOpenPixiv: () -> Unit,
    val onOpenSourceManage: () -> Unit,
    val onOpenAppSettings: () -> Unit,
    /** 完成页按下「进入 Venera」时写引导标记。 */
    val onFinish: () -> Unit,
)

/**
 * 首次启动引导（2026-11-03，主人点名；口径对齐 `docs` 里那份原型）。
 *
 * ## 为什么步骤切换用 AnimatedContent 而不是 HorizontalPager
 *
 * 这七屏要的是一步一切换（底栏 CTA 与左右按钮是主路径），更要的是**标题、跳过、
 * 主按钮这三枚跨步骤的身份连续** —— 那需要 sharedBounds，而 sharedBounds 只认
 * AnimatedVisibilityScope，Pager 的相邻页没有 enter/exit 生命周期，给不出作用域。
 * AnimatedContent 每个分支自带作用域，正好是要的那一条（动画 API 选型表里
 * "Different composable trees fill one region" 那行）。
 *
 * ## 共享元素的 key 与降级
 *
 * key 三枚写在一处（[OnboardingSharedKeys]），两端同串的老规矩照 `ComicSharedTransition`
 * 那条注释执行；系统动画缩放为 0 时整段不挂共享修饰件 —— 转场是增强，不是渲染前提。
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun VeneraOnboardingScreen(ports: OnboardingPorts) {
    val context = LocalContext.current
    var step by rememberSaveable { mutableStateOf(OnboardingStep.WELCOME) }

    // 系统「移除动画」那一档（过渡动画缩放 = 0）时不挂共享元素：直接切屏，别硬飞。
    // 键名口径照 `feature/pet/PetMascot.kt` 那条量过的结论：公开的只有 TRANSITION/WINDOW 两枚。
    val motionEnabled = remember(context) {
        runCatching {
            Settings.Global.getFloat(context.contentResolver, Settings.Global.TRANSITION_ANIMATION_SCALE, 1f) > 0f
        }.getOrDefault(true)
    }

    // 返回 = 退一步；欢迎页交还给系统（再按才退应用），与全 app「返回可预期」那条口径一致。
    BackHandler(enabled = step != OnboardingStep.WELCOME) {
        step = OnboardingStep.entries[step.ordinal - 1]
    }

    // 大屏版式与本 app 的导航档同一把尺（`WideScreenPolicy`：>600 Medium、>1300 Expanded）。
    // Medium 只放宽单栏上限（600dp 出头的窗口塞两栏会挤爆）；Expanded 才上「左品牌右步骤卡」。
    val layoutMode = wideScreenLayoutMode(LocalWindowWidthDp)

    SharedTransitionLayout(Modifier.fillMaxSize()) {
        val sharedScope = this
        val motionScope = sharedScope.takeIf { motionEnabled }
        val select: (OnboardingStep) -> Unit = { step = it }
        Box(Modifier.fillMaxSize().safeDrawingPadding()) {
            if (layoutMode == WideScreenLayoutMode.Expanded) {
                BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    val windowMaxHeight = maxHeight
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(OnbBrandGap),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        OnboardingBrand(Modifier.width(OnbBrandWidth))
                        val scheme = MaterialTheme.colorScheme
                        val cardHeight = OnbCardHeight.coerceAtMost(windowMaxHeight - VeneraTokens.spacing.space10).coerceAtLeast(OnbCardMinHeight)
                        Box(
                            Modifier
                                .then(
                                    wideScreenChromeMaxWidth(LocalWindowWidthDp)
                                        ?.let { Modifier.widthIn(max = it) } ?: Modifier,
                                )
                                .height(cardHeight)
                                .clip(RoundedCornerShape(VeneraTokens.shape.extraLarge))
                                .background(scheme.surfaceContainerHigh)
                                .padding(
                                    start = VeneraTokens.spacing.space10,
                                    end = VeneraTokens.spacing.space10,
                                    top = VeneraTokens.spacing.space9,
                                    bottom = VeneraTokens.spacing.space9,
                                ),
                        ) {
                            StepSwitcher(
                                step = step,
                                ports = ports,
                                sharedScope = motionScope,
                                onSelectDot = select,
                                onSkip = { step = OnboardingStep.DONE },
                                onNext = { step = OnboardingStep.entries[(it.ordinal + 1).coerceAtMost(OnboardingStep.DONE.ordinal)] },
                                onBackToIntro = { step = OnboardingStep.SOURCE },
                                onEnterApp = ports.onFinish,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }
                }
            } else {
                StepSwitcher(
                    step = step,
                    ports = ports,
                    sharedScope = motionScope,
                    onSelectDot = select,
                    onSkip = { step = OnboardingStep.DONE },
                    onNext = { step = OnboardingStep.entries[(it.ordinal + 1).coerceAtMost(OnboardingStep.DONE.ordinal)] },
                    onBackToIntro = { step = OnboardingStep.SOURCE },
                    onEnterApp = ports.onFinish,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .then(
                            contentMaxWidthFor(layoutMode)
                                ?.let { Modifier.widthIn(max = it) } ?: Modifier,
                        )
                        .fillMaxSize()
                        .padding(horizontal = VeneraTokens.spacing.space10),
                )
            }
        }
    }
}

/** 步骤切换器：AnimatedContent 给每个分支自带 AnimatedVisibilityScope —— 共享元素只认这一颗。 */
@Composable
private fun StepSwitcher(
    step: OnboardingStep,
    ports: OnboardingPorts,
    sharedScope: SharedTransitionScope?,
    onSelectDot: (OnboardingStep) -> Unit,
    onSkip: () -> Unit,
    onNext: (OnboardingStep) -> Unit,
    onBackToIntro: () -> Unit,
    onEnterApp: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedContent(
        targetState = step,
        modifier = modifier,
        transitionSpec = {
            // 快出慢进：退屏 140ms，进屏 240ms 压 80ms 起 —— 让共享的标题先落位。
            fadeIn(tween(240, delayMillis = 80)) togetherWith fadeOut(tween(140))
        },
        label = "onboarding_step",
    ) { target ->
        OnboardingColumn(
            target = target,
            ports = ports,
            sharedScope = sharedScope,
            visibilityScope = this.takeIf { sharedScope != null },
            onSelectDot = onSelectDot,
            onSkip = onSkip,
            onNext = onNext,
            onBackToIntro = onBackToIntro,
            onEnterApp = onEnterApp,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

/** 大屏（Expanded）左边那块常驻的品牌区：窗口切换时它不动，动的只有右卡。 */
@Composable
private fun OnboardingBrand(modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Column(modifier.padding(start = VeneraTokens.spacing.space10)) {
        Box(
            Modifier
                .size(OnbLogoWide)
                .clip(RoundedCornerShape(VeneraTokens.shape.large))
                .background(scheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.MenuBook,
                contentDescription = null,
                tint = scheme.onPrimaryContainer,
                modifier = Modifier.size(VeneraTokens.spacing.space11),
            )
        }
        Spacer(Modifier.height(VeneraTokens.spacing.space8))
        Text("Venera", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(VeneraTokens.spacing.space3))
        Text(
            "漫画、图廊和 Pixiv 阅读器",
            style = MaterialTheme.typography.bodyMedium,
            color = scheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(VeneraTokens.spacing.space6))
        Text(
            "设置和记录只存在这台设备上",
            style = MaterialTheme.typography.bodySmall,
            color = scheme.onSurfaceVariant.copy(alpha = 0.8f),
        )
    }
}

// ── 引导自持的几档尺寸 ──
// ui/tokens 今天没有 300/580/56 这几档语义槽位，先集中成本文件具名常量（`TokenBudgetFrozenTest`
// 的口径下计数只发生在这些声明行）；将来 token 迁移那批把合适档位收进 `Spacing.kt` 再摘。
private val OnbBrandWidth = 300.dp
private val OnbBrandGap = 56.dp
private val OnbCardHeight = 580.dp
private val OnbCardMinHeight = 360.dp
private val OnbLogoTile = 56.dp
private val OnbLogoWide = 64.dp

/** 七步的静态口径：标题与描述都只在这里写一遍，页面只管摆控件。 */
private enum class OnboardingStep(val heading: String, val blurb: String) {
    WELCOME(
        "欢迎使用 Venera",
        "漫画、图廊和 Pixiv 阅读器。\n接下来花一分钟设置几个基本选项。",
    ),
    SOURCE(
        "添加漫画源",
        "导入 .js 源文件后，首页、搜索和探索才会有内容。",
    ),
    READING(
        "阅读记录与收藏",
        "这些都会自动帮你管好，不用额外设置。",
    ),
    APPEARANCE(
        "外观与主题",
        "主题色、深浅模式和材质，都能立刻看到效果。",
    ),
    ACCOUNTS(
        "账号与存储",
        "都可以跳过，之后在「设置」里随时补。",
    ),
    PREFS(
        "顺手设两项",
        "标签译文与屏幕防窥；其余进「设置」再调。",
    ),
    DONE(
        "可以开始了",
        "偏好已保存。阅读记录、账号和设置只存在这台设备上。",
    ),
}

@Composable
private fun OnboardingColumn(
    target: OnboardingStep,
    ports: OnboardingPorts,
    sharedScope: SharedTransitionScope?,
    visibilityScope: AnimatedVisibilityScope?,
    onSelectDot: (OnboardingStep) -> Unit,
    onSkip: () -> Unit,
    onNext: (OnboardingStep) -> Unit,
    onBackToIntro: () -> Unit,
    onEnterApp: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        // ── 顶行：跳过（完成页不收这一步，留着它反而是负担） ──
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.weight(1f))
            if (target != OnboardingStep.DONE) {
                TextButton(
                    onClick = onSkip,
                    modifier = Modifier.obShared(OnboardingSharedKeys.SKIP, sharedScope, visibilityScope),
                ) {
                    Text(if (target == OnboardingStep.WELCOME) "跳过引导" else "跳过")
                }
            }
        }
        Text(
            target.heading,
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.obShared(OnboardingSharedKeys.TITLE, sharedScope, visibilityScope),
        )
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
        ) {
            Spacer(Modifier.height(VeneraTokens.spacing.space4))
            Text(
                target.blurb,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(VeneraTokens.spacing.space8))
            when (target) {
                OnboardingStep.WELCOME -> WelcomeBody()
                OnboardingStep.SOURCE -> SourceBody()
                OnboardingStep.READING -> ReadingBody()
                OnboardingStep.APPEARANCE -> AppearanceBody(ports.appearance)
                OnboardingStep.ACCOUNTS -> AccountsBody(ports)
                OnboardingStep.PREFS -> PrefsBody(ports.appearance, ports.comic)
                OnboardingStep.DONE -> { /* 完成页不摆新信息，正文一句由 blurb 说清。 */ }
            }
            Spacer(Modifier.height(VeneraTokens.spacing.space9))
        }
        Button(
            onClick = { if (target == OnboardingStep.DONE) onEnterApp() else onNext(target) },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = ButtonDefaults.MinHeight)
                .obShared(OnboardingSharedKeys.CTA, sharedScope, visibilityScope),
        ) {
            Text(
                when (target) {
                    OnboardingStep.WELCOME -> "开始"
                    OnboardingStep.DONE -> "进入 Venera"
                    else -> "下一步"
                },
            )
        }
        if (target == OnboardingStep.DONE) {
            TextButton(onClick = onBackToIntro, modifier = Modifier.fillMaxWidth()) {
                Text("回看功能介绍")
            }
        }
        OnboardingDots(current = target, onSelect = onSelectDot)
    }
}

/** 三枚跨步骤持续存在的身份元素共用的 key；与封面那族同一口径写在一处。 */
private object OnboardingSharedKeys {
    const val TITLE = "onboarding-title"
    const val SKIP = "onboarding-skip"
    const val CTA = "onboarding-cta"

    /**
     * 320ms 的 M-B 曲线 —— 与封面飞行（[com.venera.compose.components.ComicSharedTransition.CoverBounds]）
     * 同族，只把时长收短一档：这里飞的是标题和按钮，不是整张封面。
     */
    val Bounds = BoundsTransform { _, _ ->
        tween<Rect>(
            durationMillis = 320,
            easing = CubicBezierEasing(0.2f, 0f, 0f, 1f),
        )
    }
}

/** 给共享身份元素挂 sharedBounds；作用域缺一枚就原样返回（转场是增强，不是渲染前提）。 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun Modifier.obShared(
    key: String,
    sharedScope: SharedTransitionScope?,
    visibilityScope: AnimatedVisibilityScope?,
): Modifier {
    if (sharedScope == null || visibilityScope == null) return this
    return with(sharedScope) {
        this@obShared.sharedBounds(
            sharedContentState = rememberSharedContentState(key = key),
            animatedVisibilityScope = visibilityScope,
            // 内容跟着动画 bounds 重排，不整体缩放（标题与按钮字不能被拉糊）。
            resizeMode = SharedTransitionScope.ResizeMode.RemeasureToBounds,
            boundsTransform = OnboardingSharedKeys.Bounds,
        )
    }
}

@Composable
private fun WelcomeBody() {
    val scheme = MaterialTheme.colorScheme
    Box(
        Modifier
            .size(OnbLogoTile)
            .clip(RoundedCornerShape(VeneraTokens.shape.large))
            .background(scheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Filled.MenuBook,
            contentDescription = null,
            tint = scheme.onPrimaryContainer,
            modifier = Modifier.size(VeneraTokens.spacing.badgeSize),
        )
    }
}

@Composable
private fun SourceBody() {
    OnbInfoRow(Icons.Filled.FolderOpen, "源管理与更新", "导入 / 检查更新")
    OnbInfoRow(Icons.Filled.Search, "多源并行搜索", "一次输入")
    OnbInfoRow(Icons.Filled.Wifi, "网络测速与线路", "设置内可改")
    OnbHint("还没有源文件也没关系，进入应用后可随时在「首页 · 漫画源」导入。")
}

@Composable
private fun ReadingBody() {
    OnbInfoRow(Icons.Filled.Download, "离线下载", "下载队列 / 本地漫画")
    OnbInfoRow(Icons.Filled.Favorite, "漫画收藏、画廊收藏", "分开存放")
    OnbInfoRow(Icons.Filled.History, "阅读历史", "自动续读")
    OnbHint("首页默认摆着「最近在读」，打开就能接着读。")
}

@Composable
private fun AppearanceBody(prefs: AppearancePreferences) {
    val themeMode by prefs.themeMode.collectAsState()
    val material by prefs.surfaceMaterial.collectAsState()
    val seed by prefs.themeSeedColor.collectAsState()
    val colorSource by prefs.themeColorSource.collectAsState()

    OnbFieldLabel("主题色")
    ThemeSeedPresets.All.take(6).chunked(3).forEach { rowPresets ->
        Row(horizontalArrangement = Arrangement.spacedBy(VeneraTokens.spacing.space5)) {
            rowPresets.forEach { preset ->
                OnbSwatch(
                    preset = preset,
                    selected = colorSource == ThemeColorSource.CUSTOM && seed == preset.argb,
                    onClick = {
                        prefs.setThemeColorSource(ThemeColorSource.CUSTOM)
                        prefs.setThemeSeedColor(preset.argb)
                    },
                )
            }
        }
        Spacer(Modifier.height(VeneraTokens.spacing.space3))
    }
    Spacer(Modifier.height(VeneraTokens.spacing.space6))

    OnbFieldLabel("主题模式")
    Row(horizontalArrangement = Arrangement.spacedBy(VeneraTokens.spacing.space4)) {
        OnbModeChip("系统", Icons.Filled.Monitor, themeMode == ThemeMode.SYSTEM) { prefs.setThemeMode(ThemeMode.SYSTEM) }
        OnbModeChip("浅色", Icons.Filled.LightMode, themeMode == ThemeMode.LIGHT) { prefs.setThemeMode(ThemeMode.LIGHT) }
        OnbModeChip("深色", Icons.Filled.DarkMode, themeMode == ThemeMode.DARK) { prefs.setThemeMode(ThemeMode.DARK) }
    }
    Spacer(Modifier.height(VeneraTokens.spacing.space6))

    OnbFieldLabel("界面材质")
    Row(horizontalArrangement = Arrangement.spacedBy(VeneraTokens.spacing.space4)) {
        OnbModeChip("实色", null, material == SurfaceMaterial.SOLID) { prefs.setSurfaceMaterial(SurfaceMaterial.SOLID) }
        OnbModeChip("液态玻璃", null, material == SurfaceMaterial.LIQUID_GLASS) { prefs.setSurfaceMaterial(SurfaceMaterial.LIQUID_GLASS) }
    }
    OnbHint("还有 25 套预设与跟随壁纸取色，可在「设置 · 外观与主题」里挑。")
}

@Composable
private fun OnbSwatch(preset: ThemeSeedPreset, selected: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(VeneraTokens.shape.medium))
            .clickable(onClick = onClick)
            .padding(horizontal = VeneraTokens.spacing.space3, vertical = VeneraTokens.spacing.space3),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Box(
                Modifier
                    .size(VeneraTokens.spacing.space11)
                    .clip(CircleShape)
                    .background(preset.color.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) {
                Box(Modifier.size(VeneraTokens.spacing.space10).clip(CircleShape).background(preset.color))
            }
            if (selected) {
                Box(
                    Modifier
                        .size(VeneraTokens.spacing.space11)
                        .clip(CircleShape)
                        .border(VeneraTokens.spacing.space1, preset.color, CircleShape),
                )
            }
        }
        Spacer(Modifier.height(VeneraTokens.spacing.space2))
        Text(preset.name, style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant)
    }
}

@Composable
private fun OnbModeChip(label: String, icon: ImageVector?, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        leadingIcon = icon?.let { vector ->
            @Composable { Icon(vector, null, Modifier.size(VeneraTokens.spacing.chipIconSize)) }
        },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AccountsBody(ports: OnboardingPorts) {
    val pixivAuth = ports.pixivAuth?.collectAsState()?.value ?: PixivAuthState.Anonymous
    val pixivSigned = pixivAuth is PixivAuthState.SignedIn
    val pixivName = (pixivAuth as? PixivAuthState.SignedIn)?.userName?.takeIf { it.isNotBlank() }
    val gelbooruIdentity by ports.gelbooruIdentity.collectAsState()
    val configuredPath by ports.network.comicStoragePath.collectAsState()
    val storageDir = remember(configuredPath) { ports.network.downloadRootPath }

    OnbActionRow(
        main = "Pixiv",
        note = if (pixivSigned) (if (pixivName != null) "已登录：$pixivName" else "已登录") else "登录后可看关注画师动态",
        action = if (pixivSigned) "账号" else "去登录",
        onClick = ports.onOpenPixiv,
    )
    OnbActionRow(
        main = "画廊站点账号",
        note = if (gelbooruIdentity != null) "已配置" else "Gelbooru 等站点的凭据",
        action = if (gelbooruIdentity != null) "管理" else "配置",
        onClick = ports.onOpenSourceManage,
    )
    OnbActionRow(
        main = "下载目录",
        note = storageDir,
        action = "更改",
        onClick = ports.onOpenAppSettings,
    )
    OnbHint("登录凭据只存在本机。")
}

@Composable
private fun PrefsBody(appearance: AppearancePreferences, comic: ComicPreferences) {
    val tagMode by appearance.tagTranslationMode.collectAsState()
    val secure by comic.secureScreen.collectAsState()

    OnbFieldLabel("标签译文")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(VeneraTokens.spacing.space4)) {
        OnbModeChip("跟随系统", null, tagMode == TagTranslationMode.SYSTEM) { appearance.setTagTranslationMode(TagTranslationMode.SYSTEM) }
        OnbModeChip("简体", null, tagMode == TagTranslationMode.SIMPLIFIED) { appearance.setTagTranslationMode(TagTranslationMode.SIMPLIFIED) }
        OnbModeChip("繁体", null, tagMode == TagTranslationMode.TRADITIONAL) { appearance.setTagTranslationMode(TagTranslationMode.TRADITIONAL) }
        OnbModeChip("不翻译", null, tagMode == TagTranslationMode.OFF) { appearance.setTagTranslationMode(TagTranslationMode.OFF) }
    }
    Spacer(Modifier.height(VeneraTokens.spacing.space8))

    Row(Modifier.fillMaxWidth().heightIn(min = ButtonDefaults.MinHeight), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("屏幕防窥", style = MaterialTheme.typography.bodyMedium)
            Text(
                "阻止截屏与任务栏预览",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = secure, onCheckedChange = { comic.setSecureScreen(it) })
    }
    OnbHint("网络与代理、屏蔽与过滤、阅读设置都在「设置」里。")
}

@Composable
private fun OnbActionRow(main: String, note: String, action: String, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.heightIn(min = ButtonDefaults.MinHeight), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(main, style = MaterialTheme.typography.bodyMedium)
                Text(
                    note,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            TextButton(onClick = onClick) { Text(action) }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

@Composable
private fun OnbInfoRow(icon: ImageVector, main: String, note: String) {
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.heightIn(min = ButtonDefaults.MinHeight), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(VeneraTokens.spacing.rowHorizontal))
            Text(main, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Text(
                note,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

@Composable
private fun OnbFieldLabel(label: String) {
    Text(
        label,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(bottom = VeneraTokens.spacing.space3),
    )
}

@Composable
private fun OnbHint(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
        modifier = Modifier.padding(top = VeneraTokens.spacing.space6),
    )
}

/** 底部七枚点位的进度条：可点跳转，当前位拉长 —— 与原型同一形态。 */
@Composable
private fun OnboardingDots(current: OnboardingStep, onSelect: (OnboardingStep) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = VeneraTokens.spacing.space5),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OnboardingStep.entries.forEach { s ->
            val active = s == current
            val pillWidth by animateDpAsState(if (active) VeneraTokens.spacing.space8 else VeneraTokens.spacing.space3, label = "onboardingDotWidth")
            Box(
                Modifier
                    .size(VeneraTokens.spacing.space11)
                    .clickable { onSelect(s) }
                    .semantics { contentDescription = "第 ${s.ordinal + 1} 步：${s.heading}" },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .width(pillWidth)
                        .height(VeneraTokens.spacing.space3)
                        .clip(CircleShape)
                        .background(if (active) scheme.primary else scheme.outlineVariant),
                )
            }
        }
    }
}
