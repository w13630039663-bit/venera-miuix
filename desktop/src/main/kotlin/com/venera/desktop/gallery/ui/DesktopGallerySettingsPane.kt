package com.venera.desktop.gallery.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.venera.compose.gallery.domain.GalleryColumnMode
import com.venera.compose.gallery.domain.GalleryPreloadMode
import com.venera.compose.gallery.domain.GalleryPreviewQuality
import com.venera.compose.security.guard.GuardRule
import com.venera.desktop.gallery.data.DesktopGalleryContentGuard
import com.venera.desktop.gallery.data.DesktopGalleryCredentials
import com.venera.desktop.gallery.data.DesktopGalleryPreferences
import io.github.composefluent.component.AccentButton
import io.github.composefluent.component.Button
import io.github.composefluent.component.RadioButton
import io.github.composefluent.component.Switcher
import io.github.composefluent.component.Text
import io.github.composefluent.component.TextField
import kotlinx.coroutines.launch

/**
 * 设置域那一屏（第七批）。
 *
 * ## 这一屏的规矩：只摆真有消费点的，其余**逐颗点名为什么不摆**
 *
 * 这一屏那八枚档全部来自 [DesktopGallerySettingsSpec] —— 判据看到的与用户看到的是同一份清单，
 * 所以"账上少一枚"这件事会先让测试红，而不是先让用户撞到一颗没反应的开关。
 *
 * ## 批 6 起这一屏还管**分级与屏蔽规则**
 *
 * 那两块不在上面那份账里，因为它们根本不是 `GalleryPreferences` 的档：分级档落在
 * `venera_guard_prefs` 的 `nsfw_mode`，规则落在 `content_guard_rules` 那张表。
 * 两边吃的是同一份数据，所以改完这一屏，摆着的那些墙下一帧就重算 ——
 * 走的是 `DesktopGalleryWallPolicy` 已经在读的那两枚流，桌面没有第二套判定。
 *
 * ## 为什么改完就生效，不需要"保存"那颗钮
 *
 * 八枚档各是一颗 `StateFlow`，三面墙在组合里 `collectAsState` 它们；
 * 写侧（`DesktopGalleryPreferences` 那八颗 setter）落盘之后会把同一条流推到新值 ⇒
 * 墙上立刻重算。这一条与 Android 侧 `VeneraPreferences.setGalleryXxx` 是同一形状。
 * 摆一颗「保存」就是把这件事说成两步，而那件事本来就是一步。
 * （分级与规则同理：`DesktopGalleryContentGuard` 那两道写口也是"落盘 + 推流"两步。）
 *
 * ## 控件面只有三枚，且都是 fluent 现成的
 *
 * 单选（`RadioButton`）给"三档/两档/四型"，开关（`Switcher`）给布尔，
 * 输入框（`TextField`）给规则那一串 —— 加与删各一枚 `Button`/`AccentButton`。
 * 没有下拉、没有滑条：对岸那枚 0..30 的连播滑条在这一屏落成**一排档位按钮**（批 46），
 * 而滑条本身今天还没有需要它的档 —— 摆一枚只能改五个数的滑条，比按钮更难读准。
 */
@Composable
internal fun DesktopGallerySettingsBody(
    row: DesktopGalleryHomeRow,
    settings: DesktopGalleryPreferences,
    guard: DesktopGalleryContentGuard,
    credentials: DesktopGalleryCredentials,
) {
    val rendered: Unit = when (row.content) {
        DesktopGallerySectionContent.GalleryDisplay -> DesktopGallerySettingsPane(settings, guard, credentials)
        DesktopGallerySectionContent.Daily,
        DesktopGallerySectionContent.FollowedArtists,
        DesktopGallerySectionContent.ComicExplore,
        DesktopGallerySectionContent.ComicFavorites,
        DesktopGallerySectionContent.ComicHistory,
        DesktopGallerySectionContent.ComicSources,
        DesktopGallerySectionContent.ComicDownloads,
        DesktopGallerySectionContent.ComicQueue,
        DesktopGallerySectionContent.GalleryFavorites,
        DesktopGallerySectionContent.GallerySearch,
        DesktopGallerySectionContent.Downloads,
        DesktopGallerySectionContent.BrowseHistory,
        DesktopGallerySectionContent.ArtistsWall,
        DesktopGallerySectionContent.ForYouWall,
        DesktopGallerySectionContent.Latest,
        DesktopGallerySectionContent.Ranking,
        -> error("「${row.title}」不是设置域的页 —— 域路的分岔在 VeneraDesktopApp.DesktopDomainBody")
        null -> error("设置域今天没有未实现的行，「${row.title}」却带了空的 content 位")
    }
}

@Composable
private fun DesktopGallerySettingsPane(
    settings: DesktopGalleryPreferences,
    guard: DesktopGalleryContentGuard,
    credentials: DesktopGalleryCredentials,
) {
    val pad = DesktopGalleryMetrics.pageHorizontalPadding
    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(start = pad, end = pad, top = 10.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            "设置",
            color = DesktopTheme.TextPrimary,
            fontSize = 20.sp,
            fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
        )
        Text(
            "这一屏管画廊那八枚档，以及敏感内容怎么挡；改完立刻生效，不需要保存。",
            color = DesktopTheme.TextSecondary,
            fontSize = 12.sp,
        )

        val columnMode by settings.galleryColumnMode.collectAsState()
        SettingsRow(DesktopGallerySettingsSpec.editable[0]) {
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                COLUMN_MODE_LABELS.forEach { (mode, 名) ->
                    // ⚠️ fluent 的这颗叫 `selected` / `onClick` / `label`，不叫 Material 那套 `checked`；
                    // 而 `Switcher` 那颗又叫 `checked` / `onCheckStateChange`。两枚不对称，
                    // 名字是从类元数据里取的（判法见手册 §16.2 那条 javap 路），别照 Kotlin 语感猜。
                    RadioButton(
                        selected = columnMode == mode,
                        onClick = { settings.setGalleryColumnMode(mode) },
                        label = 名,
                    )
                }
            }
        }

        val quality by settings.galleryPreviewQuality.collectAsState()
        SettingsRow(DesktopGallerySettingsSpec.editable[1]) {
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                QUALITY_LABELS.forEach { (档, 名) ->
                    RadioButton(
                        selected = quality == 档,
                        onClick = { settings.setGalleryPreviewQuality(档) },
                        label = 名,
                    )
                }
            }
        }

        val blockAi by settings.galleryBlockAi.collectAsState()
        SettingsRow(DesktopGallerySettingsSpec.editable[2]) {
            Switcher(checked = blockAi, onCheckStateChange = { settings.setGalleryBlockAi(it) })
        }

        val aiBadge by settings.galleryAiBadge.collectAsState()
        SettingsRow(DesktopGallerySettingsSpec.editable[3]) {
            Switcher(checked = aiBadge, onCheckStateChange = { settings.setGalleryAiBadge(it) })
        }

        // 连播那一档（批 46）：桌面这一屏没有对岸那枚滑条，所以摆**档位**而不是连续值。
        // "关"就是 0 那一档，与对岸同一个数；生效那一头在集成方（`VeneraDesktopApp` 里那段连播），
        // 这一行只负责把秒数写进去并立刻把选中态挪过来。
        val autoPlaySec by settings.galleryAutoPlaySec.collectAsState()
        SettingsRow(DesktopGallerySettingsSpec.editable[4]) {
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                DesktopGallerySettingsSpec.autoPlayTiers.forEach { 秒 ->
                    RadioButton(
                        selected = autoPlaySec == 秒,
                        onClick = { settings.setGalleryAutoPlaySec(秒) },
                        label = if (秒 == 0) "关" else "$秒 秒",
                    )
                }
            }
        }

        // 第六行（第四十七批）：提前取哪几张。三档就是共享那颗枚举的全部，"关"那一档真一张都不发。
        val preloadMode by settings.galleryPreload.collectAsState()
        SettingsRow(DesktopGallerySettingsSpec.editable[5]) {
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                DesktopGallerySettingsSpec.preloadTiers.forEach { mode ->
                    RadioButton(
                        selected = preloadMode == mode,
                        onClick = { settings.setGalleryPreload(mode) },
                        label = preloadTierLabel(mode),
                    )
                }
            }
        }

        // 第七行（批 52）：动图。三档就是共享那枚枚举的全部，选中态吃这一枚流；
        // 生效那一头在大图那一屏那颗播放器（批 51），集成方现算 `GalleryMotion.animates` 传下去。
        // ⚠️ 中间那一档在桌面走"照播"（量不出哪种网不算流量），那句写在 caption 里，不许删。
        val animatedTier by settings.galleryAnimated.collectAsState()
        SettingsRow(DesktopGallerySettingsSpec.editable[6]) {
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                DesktopGallerySettingsSpec.animatedTierLabels.forEach { (档, 名) ->
                    RadioButton(
                        selected = animatedTier == 档,
                        onClick = { settings.setGalleryAnimated(档) },
                        label = 名,
                    )
                }
            }
        }

        // 第八行（批 61）：大图那一屏的那层底。四档 = 共享那枚枚举的全部，一档都不删；
        // GLASS 那一档在桌面画的是实心底，那句实话写在标签与 caption 里（降级必须可见）。
        val backdropTier by settings.galleryBackdrop.collectAsState()
        SettingsRow(DesktopGallerySettingsSpec.editable[7]) {
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                DesktopGallerySettingsSpec.backdropTierLabels.forEach { (档, 名) ->
                    RadioButton(
                        selected = backdropTier == 档,
                        onClick = { settings.setGalleryBackdrop(档) },
                        label = 名,
                    )
                }
            }
        }

        DesktopGuardSection(guard)

        DesktopSauceNaoKeySection(credentials)

        // 下面这一区是**故意摆出来的缺席**：不提就等于"看起来这一屏把画廊设置管全了"。
        Text(
            "这一屏没有的两枚 · 各自差的是哪一层",
            color = DesktopTheme.TextPrimary,
            fontSize = 13.sp,
            fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
            modifier = Modifier.padding(top = 6.dp),
        )
        DesktopGallerySettingsSpec.notOnScreen.forEach { absent ->
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = 2.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(absent.title, color = DesktopTheme.TextSecondary, fontSize = 12.5.sp)
                Text(absent.why, color = DesktopTheme.TextTertiary, fontSize = 11.sp)
            }
        }
    }
}

/** 一行：标题 + 一句说明 + 右边那颗控件。控件位是 `content`，所以四行长势一致。 */
@Composable
private fun SettingsRow(spec: DesktopGallerySettingsSpec.Editable, content: @Composable () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(DesktopTheme.CardBackground, RoundedCornerShape(8.dp))
            .border(1.dp, DesktopTheme.SurfaceRaised, RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(spec.title, color = DesktopTheme.TextPrimary, fontSize = 13.sp)
            Text(spec.caption, color = DesktopTheme.TextSecondary, fontSize = 11.sp)
        }
        content()
    }
}

/** 三档的屏上叫法。枚举名不进界面（那是实现笔记），这里给的是人读的那三个字。 */
private val COLUMN_MODE_LABELS = listOf(
    GalleryColumnMode.AUTO to "自适应",
    GalleryColumnMode.TWO to "两列",
    GalleryColumnMode.THREE to "三列",
)

private val QUALITY_LABELS = listOf(
    GalleryPreviewQuality.PREVIEW to "站方小图",
    GalleryPreviewQuality.LARGE to "清楚一档",
)

/**
 * 敏感内容那一区（第十二批 批 6）：三档分级 + 四型屏蔽规则的增与删。
 *
 * ## 这一区的三枚读口都在 `guard` 上，桌面不另起判据
 *
 * 三档的词与值、四型的词、"这一串该不该发"都在 [DesktopGuardPolicy]（那两颗判据件由
 * `DesktopGuardWriteTest` ①③④ 逐字回核 Android），落盘与推流在 `DesktopGalleryContentGuard`。
 * 这一颗文件只做三件事：把用户点的那一枚交给判据、把判据说该发的交给写口、把落点念成人话。
 *
 * ## 读数为什么分两枚而不是一枚
 *
 * 「这条已经加过了」与「已删掉这一条」如果共用一颗 `notice`，屏上就是同一档颜色、同一个位置，
 * 用户读到的是"有反应"—— 而前者其实什么都没发生。所以分 `rejection`（没写成）与 `done`（写成了）。
 *
 * ## 删除刻意不摆确认那一步
 *
 * 收藏那一屏的"不可撤销"规矩不适用于这一枚：屏蔽规则不是用户攒的数据，删错了再敲一遍就回来。
 * 代价是"手一抖就没了"，所以行上那颗钮写的是「删掉」而区里明说一句没有确认。
 */
@Composable
private fun DesktopGuardSection(guard: DesktopGalleryContentGuard) {
    // 三档与规则表都直接 collect 守卫那两枚流：本地再存一份"现在的档"或"现在这几条"，
    // 就是第二份状态 —— 而不同步的读数比没有读数更坏。
    val maskMode by guard.nsfwMaskMode.collectAsState()
    val rules by guard.rules.collectAsState()
    val scope = rememberCoroutineScope()

    var type by rememberSaveable { mutableStateOf(desktopRuleTypes().first().first) }
    var input by rememberSaveable { mutableStateOf("") }
    var regexMode by rememberSaveable { mutableStateOf(false) }
    var adding by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<Long?>(null) }
    var rejection by remember { mutableStateOf<String?>(null) }
    var done by remember { mutableStateOf<String?>(null) }

    // 这一区的标题。**它是唯一一份**：浮层页脚那句"要改回去去哪一格"引用的就是这颗常量
    // （`DESKTOP_GUARD_SECTION_TITLE`），两处各写一份字面量的话，改名时屏上会指到一个不存在的地方。
    Text(
        DESKTOP_GUARD_SECTION_TITLE,
        color = DesktopTheme.TextPrimary,
        fontSize = 13.sp,
        fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
        modifier = Modifier.padding(top = 6.dp),
    )

    GuardRow(
        title = "R18 敏感内容分级",
        caption = "被判为敏感的那一张怎么摆。打码那一档桌面连图都不取（少一次外泄），点进浮层也仍然是遮着的；" +
            "彻底隐藏是不上墙。",
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            desktopMaskModeLabels().forEach { (mode, 名) ->
                RadioButton(
                    selected = maskMode == mode,
                    onClick = {
                        done = null
                        // 合法值这件事只判一处：认不出来的一枚在落盘之前就抛（写进一档两边各读各的，
                        // 比崩在这颗钮上难查得多）。值表在 DesktopGuardPolicy，桌面只有这一份。
                        rejection = if (guard.setNsfwMaskMode(desktopRequireMaskMode(mode))) {
                            null
                        } else {
                            "保存失败，再试一次"
                        }
                    },
                    label = 名,
                )
            }
        }
    }

    GuardCard {
        Text(
            "屏蔽规则",
            color = DesktopTheme.TextPrimary,
            fontSize = 13.sp,
            fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
        )
        Text(
            "先挑一型，再往里加一串。命中哪一型就不上墙，与上面那档分级各管各的。" +
                "这一屏删规则没有确认那一步：按下删掉当场就删，再想挡它敲回来一遍就行。",
            color = DesktopTheme.TextSecondary,
            fontSize = 11.sp,
        )

        val table = desktopRulesByType(rules)
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            desktopRuleTypes().forEach { (值, 名) ->
                RadioButton(
                    selected = type == 值,
                    onClick = {
                        type = 值
                        // 换型时清掉上一型的反馈：留着它会挂在另一型的列表头上说谎。
                        rejection = null
                        done = null
                    },
                    label = 名,
                )
            }
        }

        val 型名 = desktopRuleTypes().firstOrNull { it.first == type }?.second
        val 这一型 = table.of(type)
        Text(
            desktopRuleTypeHint(type) + if (型名 != null) "已存 ${这一型.size} 条。" else "",
            color = DesktopTheme.TextTertiary,
            fontSize = 11.sp,
        )

        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                TextField(
                    value = input,
                    onValueChange = {
                        input = it
                        rejection = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !adding,
                )
                Switcher(checked = regexMode, onCheckStateChange = { regexMode = it })
                Text(
                    if (regexMode) "按正则匹配：写法有误会在加进去之前就被拦下（坏的正则永不命中，比报错难查）。"
                    else "按普通文字匹配（关掉左边那颗开关就是这一档）。",
                    color = DesktopTheme.TextTertiary,
                    fontSize = 11.sp,
                )
            }
            AccentButton(
                onClick = {
                    done = null
                    // 四档落点各说一句，而且前三档**根本不发**（不发就不往库里塞东西）。
                    when (val verdict = desktopAddRule(input, regexMode, type, rules)) {
                        // 空串那一档在屏上到不了：那颗钮没字就不给点（与 Android 同一档）。
                        // 留这一支是因为 desktopAddRule 是全函数，删掉它等于把判据少写一档。
                        DesktopAddRule.Blank -> Unit
                        DesktopAddRule.BadPattern -> rejection = "正则写法有误"
                        DesktopAddRule.Duplicate -> rejection = "这条已经加过了"
                        is DesktopAddRule.Accept -> {
                            adding = true
                            scope.launch {
                                rejection = if (guard.addRule(type, verdict.pattern, regexMode) >= 0) {
                                    input = ""
                                    null
                                } else {
                                    "保存失败，再试一次"
                                }
                                adding = false
                            }
                        }
                    }
                },
                disabled = adding || input.isBlank(),
            ) { Text(if (adding) "正在保存" else "添加") }
        }

        rejection?.let { Text(it, color = DesktopTheme.AccentBeni, fontSize = 11.sp) }
        done?.let { Text(it, color = DesktopTheme.TextTertiary, fontSize = 11.sp) }

        // 两批规则行（认得出的与认不出的）吃同一道删除：两份 lambda 的话，
        // 第一处差别就会长在"其中一份忘了说那句读数"上。
        val delete: (GuardRule) -> Unit = { rule ->
            done = null
            scope.launch {
                deleting = rule.id
                // false 有两种意思（删砸了 / 那一条本来就不在表里），这里不替它挑一种：
                // "库里本来有没有这一条"是 `GuardRuleStore.delete` 的返回值说了算，桌面不再判一遍。
                if (guard.deleteRule(rule.id)) {
                    done = "已删掉这一条"
                    rejection = null
                } else {
                    rejection = "没有删掉，这一条可能本来就不在表里"
                }
                deleting = null
            }
        }
        if (这一型.isEmpty()) {
            Text("暂无屏蔽项", color = DesktopTheme.TextTertiary, fontSize = 11.sp)
        }
        这一型.forEach { rule -> RuleLine(rule, deleting = deleting, onDelete = delete) }

        // 认不出的那一型点名而不是丢掉：它今天仍然生效（命中判定读的是全表），
        // 屏上不说它，就成了"墙上少了几张而这一屏一条都没显示"。
        if (table.unlisted.isNotEmpty()) {
            Text(
                "另有 ${table.unlisted.size} 条不属于这四型（" +
                    table.unlisted.map { it.type }.distinct().joinToString(" / ") +
                    "）。它们照旧生效，只是这一屏按型分不出行 —— 要撤它就在下面这一行撤。",
                color = DesktopTheme.TextSecondary,
                fontSize = 11.sp,
            )
            table.unlisted.forEach { rule -> RuleLine(rule, deleting = deleting, onDelete = delete) }
        }
    }
}

/** 与 `SettingsRow` 同一长相，但标题与说明是这里现写的（那两块不在 12 枚偏好的账上）。 */
@Composable
private fun GuardRow(title: String, caption: String, content: @Composable () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(DesktopTheme.CardBackground, RoundedCornerShape(8.dp))
            .border(1.dp, DesktopTheme.SurfaceRaised, RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, color = DesktopTheme.TextPrimary, fontSize = 13.sp)
            Text(caption, color = DesktopTheme.TextSecondary, fontSize = 11.sp)
        }
        content()
    }
}

/** 一块整幅的卡片：型选择、输入行与那一列规则都横向排不开，所以走竖排。 */
@Composable
private fun GuardCard(content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(DesktopTheme.CardBackground, RoundedCornerShape(8.dp))
            .border(1.dp, DesktopTheme.SurfaceRaised, RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        content()
    }
}

/**
 * 一条规则一行：串本身 + 它是哪一型、是不是正则 + 那颗「删掉」。
 *
 * [deleting] 装的是"此刻正在删的是哪一条"（`null` = 没有）。整区一次只放一条出去：
 * 两份并发删除会说不清最后落进表里的是哪一笔，而这一屏显示的正是那张表。
 */
@Composable
private fun RuleLine(rule: GuardRule, deleting: Long?, onDelete: (GuardRule) -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(rule.pattern, color = DesktopTheme.TextPrimary, fontSize = 12.5.sp)
            Text(
                "那一型是 ${rule.type}" + if (rule.isRegex) " · 正则" else "",
                color = DesktopTheme.TextTertiary,
                fontSize = 11.sp,
            )
        }
        Button(onClick = { onDelete(rule) }, disabled = deleting != null) {
            Text(if (deleting == rule.id) "正在删" else "删掉")
        }
    }
}

/**
 * 「以图反搜（SauceNAO）的 key」那一区的标题。
 *
 * 收成常量是因为**两处要念它**：这一区的头部，与浮层上"没填键就不发车"那句要去的那儿
 * （`DesktopReverseSearchPolicy`）。两处各写一份字面量，改名时一定会留下一句指错地方的话
 * —— 与 [DESKTOP_GUARD_SECTION_TITLE] 同一条理由，由 `DesktopReverseSearchTest` ⑫ 数着次数。
 */
internal const val DESKTOP_SAUCE_NAO_KEY_SECTION_TITLE: String = "以图反搜（SauceNAO）的 key"

/**
 * 键那一行屏上念的那句。
 *
 * 刻意**不收键本体**：这一颗函数拿不到那枚串，所以"把 key 摆上屏"在这里是结构上不可能的，
 * 比"记得脱敏"硬（Gelbooru 那一屏要回显，得靠 `maskUserId` 加一枚"显示 Key"按钮去守；
 * 反搜这一枚今天没有回显的需求，就不给回显留门）。
 */
internal fun desktopSauceNaoKeyReading(hasKey: Boolean): String =
    if (hasKey) "已保存一枚 key（不回显）" else "还没填 key —— 「$DESKTOP_REVERSE_ENTRY_LABEL」那一族今天用不了"

/**
 * 对岸 `SauceNaoAccount.kt` 里那枚 `KEY_PAGE` 的**文本重述** —— 那颗类要 `Context`，
 * 整颗不在桌面编译面上（`desktop/build.gradle.kts` 点名排除）。重述就是会漂的那种重复，
 * 所以 `DesktopSauceNaoKeyTest` ② 拿文本回核两头是不是同一串（与 `AI_BLOCKED_RULE` 同一手法）。
 */
private const val SAUCE_NAO_KEY_PAGE = "https://saucenao.com/user.php?page=search-api"

/**
 * 第三十四批 批 34 · 反搜那枚 key 的输入口。
 *
 * ## 这一节先于反搜本身落地的理由已经还掉了
 *
 * 批 34 那一句"先给键再谈反搜"针对的是"摆一枚永远按不动的开关"；批 35 把浮层那枚入口与结果那一格
 * 接上了，所以这里写的与请求带的必须是**同一份**：对岸那颗 account 是单例 + 设置页直接拿它的流渲染，
 * 桌面这一侧对应的是"凭据只建一颗"（见 `DesktopGalleryPorts.credentials`）。
 *
 * ## 只落盘、不发校验请求
 *
 * 对岸那条理由在它自己的类注释里：SauceNAO 没有"保证非空的最小请求"那种端点，
 * 填完就发一笔等于拿用户的键去撞墙。所以屏上只说「已保存」，**不许说「已校验」**。
 *
 * ## 清掉走同一枚写口
 *
 * 空串就是清（对岸 `clear() = save("")` 同一条）。批 26 撤掉的正是"成对而死"的那两枚，
 * 这里只有一枚函数、两处调用点。
 */
@Composable
private fun DesktopSauceNaoKeySection(credentials: DesktopGalleryCredentials) {
    val hasKey by credentials.sauceNaoHasKey.collectAsState()
    var input by rememberSaveable { mutableStateOf("") }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            DESKTOP_SAUCE_NAO_KEY_SECTION_TITLE,
            color = DesktopTheme.TextPrimary,
            fontSize = 16.sp,
            fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
        )
        Text(
            desktopSauceNaoKeyReading(hasKey),
            color = if (hasKey) DesktopTheme.TextSecondary else DesktopTheme.AccentBeni,
            fontSize = 12.sp,
        )
        Text(
            "键在 $SAUCE_NAO_KEY_PAGE 申请；只落在本机数据目录的那份 prefs 文件里，不上传、不写日志。",
            color = DesktopTheme.TextTertiary,
            fontSize = 11.5.sp,
        )
        TextField(
            value = input,
            onValueChange = { input = it },
            modifier = Modifier.fillMaxWidth(),
            visualTransformation = PasswordVisualTransformation(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AccentButton(
                onClick = {
                    credentials.setSauceNaoKey(input)
                    // 落盘之后把框清掉：留着它，用户下一次看到这行会以为"还没保存"。
                    input = ""
                },
                disabled = input.isBlank(),
            ) { Text("保存") }
            if (hasKey) {
                Button(onClick = { credentials.setSauceNaoKey("") }) { Text("清掉") }
            }
        }
    }
}

/** 三档在屏上的说法。档位是共享那颗枚举，措辞是桌面的（"前后各两张"说得清那一档取几张）。 */
private fun preloadTierLabel(mode: GalleryPreloadMode): String = when (mode) {
    GalleryPreloadMode.OFF -> "不提前取"
    GalleryPreloadMode.NEXT -> "只取下一张"
    GalleryPreloadMode.BOTH_TWO -> "前后各两张"
}
