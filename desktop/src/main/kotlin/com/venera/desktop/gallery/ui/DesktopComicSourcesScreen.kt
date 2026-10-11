package com.venera.desktop.gallery.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.venera.desktop.ForensicFlags
import com.venera.desktop.gallery.data.DesktopCookieLogin
import com.venera.desktop.gallery.data.DesktopCookieOutcome
import com.venera.desktop.gallery.data.DesktopSourceCatalog
import com.venera.desktop.gallery.data.DesktopSourceNames
import com.venera.desktop.gallery.data.DesktopSourceSelection
import com.venera.desktop.gallery.data.DesktopSourceState
import com.venera.engineprobe.EngineAssets
import io.github.composefluent.component.AccentButton
import io.github.composefluent.component.ProgressBar
import io.github.composefluent.component.Text
import io.github.composefluent.component.TextField
import kotlinx.coroutines.launch

/**
 * 源管理那一屏（批 83）+ 「当前该装哪一颗」的域层折算法。
 *
 * ## 为什么这一格住在域层而不是这一屏里
 *
 * 选源与停用改的是**整个漫画域**吃的那颗 key：探索的封面墙、详情的 `loadInfo`、阅读的取页
 * 全都跟着换。把它收进这一屏里，就成了"在源管理上按了一下而探索那一屏还在念上一颗源"。
 * 所以 [rememberDesktopSources] 由 `DesktopComicBody` 调用，而这一屏只是它的一个消费者。
 *
 * ## 停用是真生效的
 *
 * [DesktopSourceSelection.resolve] 把"用户点名要的那颗已被停用"折成**落回另一颗**并念出从谁落到谁；
 * 只在列表上标个灰而装载照旧，就是本仓那一族假开关。
 */
/**
 * 「网页登录那一形桌面没有宿主」那句实话的**唯一出处**。
 *
 * 做成一颗件而不是把句子写在屏上：判据要钉"这一屏念得出是哪一形"，而钉字面句会把句子内联回各屏
 * （坑 ㉛ 那一族），改了一个字就红在措辞上而不是红在行为上。这里屏上摆的是这颗件，判据跑的也是
 * 这颗件，句子只有一处能改。
 */
internal val desktopWebLoginAbsentLine =
    "网页登录那一形靠的是应用内嵌的浏览器页面，桌面没有那一层宿主；这里只有手工粘贴凭据这一形。"

internal class DesktopSourcesState(
    /** 列表：一颗源一行，停用不是删除。 */
    val rows: List<com.venera.desktop.gallery.data.DesktopSourceRow>,
    /** 此刻真该装载的那一颗（空串 = 清单全停用，谁也装不起来）。 */
    val activeKey: String,
    /** 只给屏上念数用：停用了几个、清单几个。**不是** stdout 那一句 —— `D_` 那种内部读数不许上屏。 */
    val disabledCount: Int,
    val total: Int,
    /** 选它：返回非 null = 那一下没生效，值是屏上要说的那句。 */
    val pick: (String) -> String?,
    /** 停用 / 启用：同上，非 null 是"这一颗关不掉"那句。 */
    val toggle: (String) -> String?,
    /** 落盘失败那一句（null = 没失败过）。不静默：屏上得看得见这份状态没写进去。 */
    val saveNote: String?,
    /**
     * 每颗源**自己声明**的手填字段名单（`account.loginWithCookies.fields`，脚本现取）。
     *
     * 空表 = 这一形给不出这一颗 ⇒ 屏上**不摆**那一格。摆一枚按不动的框比不摆更坏：
     * 用户会以为填了就能登，而源根本没有那一路。
     */
    val cookieFields: Map<String, List<String>>,
) {
    /**
     * 那颗源的显示名，取不到时交回**空串**而不是键 ——
     * 历史那一屏拿它去做"键或显示名任一命中"的判断（批 82.8），交回键会让那一格看起来像命中而其实没名字表撑着。
     */
    fun nameOf(key: String): String = rows.firstOrNull { it.key == key }?.title?.takeIf { it != key } ?: ""
}

@Composable
internal fun rememberDesktopSources(config: DesktopComicEngineConfig): DesktopSourcesState {
    // 清单的真值是随包那 34 颗 js（`EngineAssets.listSourceKeys`），不是共享表 `comic_source`
    //（那张表首跑播种五条写死的默认源，是"手机上装过哪些"的另一件事）。
    val keys = remember(config.assetDir) { EngineAssets.listSourceKeys(config.assetDir) }
    // 脚本原文读**一遍**（名与名单都从它出）：两处各读各的，读到一半文件被换会摆出名与名单不配套的格子。
    val scripts = remember(keys) {
        keys.mapNotNull { key -> DesktopSourceNames.scriptOf(config.assetDir, key)?.let { key to it } }.toMap()
    }
    val names = remember(scripts) {
        scripts.mapNotNull { (key, text) -> DesktopSourceNames.displayNameOf(text)?.let { key to it } }.toMap()
    }
    val cookieFields = remember(scripts) {
        scripts.mapValues { (_, text) -> DesktopSourceNames.cookieFieldsOf(text) }
    }
    // 起手那份 = 盘上那份 **∪ 本次种子**（批 83.1）：种子只活在这一趟，不写回盘（理由见 `config.seedDisabled` 的注释）。
    // 并在这一层而不是并进 `resolve`，是因为屏上那行"停用了 N 颗"与那些灰行吃的是同一份 `disabled` ——
    // 分开并就会长成"列表说它停用而装载照旧"那一族假开关。整条写成一行：判式只看含锚点那一行（手册 §0 坑 ㉔）。
    var disabled by remember { mutableStateOf(runCatching { config.sourceState().disabled() }.getOrDefault(emptySet()) + config.seedDisabled) }
    var picked by remember { mutableStateOf(config.sourceKey) }
    var saveNote by remember { mutableStateOf<String?>(null) }
    val outcome = remember(keys, disabled, picked) {
        DesktopSourceSelection.resolve(keys, disabled, picked)
    }
    // 读数从**折完之后的状态**现取（念的是结果，不是请求）：落空与落定两种形状要分得开。
    // 屏上那一行不吃这两句 —— `D_` 是内部读数的形状，进界面就成了用户看得懂而外人抄不走的黑话。
    LaunchedEffect(outcome.reading, disabled, keys.size) {
        println(outcome.reading)
        println(DesktopSourceState.reading(disabled, keys.size))
        // 种子那一句要在**这里**念：认不认得那串要跟清单比，而清单（`keys`）只在消费点现取。
        // 递到集成方去比等于列两遍资产目录，还会让"念的那一份"与"画的那一份"分家（两条轨那一族）。
        ForensicFlags.disableNote(config.seedDisabled.firstOrNull(), keys)?.let(::println)
    }
    val active = if (outcome.key.isBlank()) config.sourceKey else outcome.key
    val rows = remember(keys, names, disabled) { DesktopSourceCatalog.rows(keys, names, disabled) }
    return DesktopSourcesState(
        rows = rows,
        activeKey = active,
        disabledCount = disabled.size,
        total = keys.size,
        pick = { key ->
            if (key in disabled) {
                "「${names[key] ?: key}」是停用着的，要先把它的停用点开"
            } else {
                picked = key
                null
            }
        },
        toggle = { key ->
            if (!DesktopSourceSelection.canDisable(keys, disabled, key)) {
                "这是最后一颗启用着的源，再关就没有可装的源了"
            } else {
                val next = DesktopSourceSelection.toggle(disabled, key)
                disabled = next
                saveNote = runCatching { config.sourceState().setDisabled(next) }
                    .exceptionOrNull()
                    ?.let { "停用状态没落盘：${it::class.java.simpleName}" }
                null
            }
        },
        saveNote = saveNote,
        cookieFields = cookieFields,
    )
}

@Composable
internal fun DesktopComicSourcesPane(
    sources: DesktopSourcesState,
    /**
     * 按下「登录」那一趟（必填、不给默认值）：交**源自己的** `loginWithCookies.validate` 跑一遍，
     * 再现读盘上那份回核。真实现是 `DesktopCookieLogin.run` —— 它起宿主，所以只在按下那一刻付代价。
     */
    login: suspend (sourceKey: String, values: List<String>) -> DesktopCookieOutcome,
) {
    // 那两枚"没生效"的句子必须上屏：只把动作吞掉而不说，用户读到的是"点了没反应"。
    var refusal by remember { mutableStateOf<String?>(null) }
    // 一次只摊开一颗源的格子：摊开两颗时输入框会挤成一排而看不出哪一格属于哪一站。
    var openKey by remember { mutableStateOf<String?>(null) }
    var draft by remember { mutableStateOf(mapOf<String, String>()) }
    var busyKey by remember { mutableStateOf<String?>(null) }
    var loginNote by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("源管理", color = DesktopTheme.TextPrimary, fontSize = 18.sp)
        Text(
            if (sources.disabledCount == 0) "清单共 ${sources.total} 颗，这一台机器上一颗都没停用"
            else "清单共 ${sources.total} 颗，这一台机器上停用了 ${sources.disabledCount} 颗",
            color = DesktopTheme.TextSecondary,
            fontSize = 12.sp,
        )
        Text(
            "停用只影响这台机器上的桌面端；手机上装过哪些源是另一张表，两边刻意不同步。",
            color = DesktopTheme.TextTertiary,
            fontSize = 11.sp,
        )
        // 那句必须是实话且点名是哪一形（批 88 的裁决：桌面结构性没有内嵌 WebView 的宿主，
        // 所以"打开登录页让页面自己回话"那一路在这儿没有，能给的只有手工粘贴）。
        // 句子只有这一处出处：屏上摆它，判据拿**同一颗件**跑一遍（两处各写一遍就是两条轨，坑 ㉛）。
        Text(desktopWebLoginAbsentLine, color = DesktopTheme.TextTertiary, fontSize = 11.sp)
        sources.saveNote?.let { Text(it, color = DesktopTheme.AccentBeni, fontSize = 12.sp) }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items(sources.rows) { row ->
                // 名单空 = 这一源的脚本里根本没有那一形 ⇒ 连那一枚按钮都不摆（摆着按不动比不摆更坏）。
                val fields = sources.cookieFields[row.key].orEmpty()
                val selected = row.key == sources.activeKey
                Column(Modifier.fillMaxWidth()) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .background(
                                if (selected) DesktopTheme.SurfaceRaised else Color.Transparent,
                                RoundedCornerShape(4.dp),
                            )
                            .clickable { refusal = sources.pick(row.key) }
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            if (selected) "在用" else "选它",
                            color = if (selected) DesktopTheme.AccentBeni else DesktopTheme.TextSecondary,
                            fontSize = 11.sp,
                        )
                        Text(
                            "${row.title}　（${row.key}）",
                            color = DesktopTheme.TextPrimary,
                            fontSize = 13.sp,
                            modifier = Modifier.weight(1f),
                        )
                        if (!row.enabled) {
                            Text("已停用", color = DesktopTheme.TextTertiary, fontSize = 11.sp)
                        }
                        if (fields.isNotEmpty()) {
                            Box(
                                Modifier
                                    .background(DesktopTheme.SurfaceRaised, RoundedCornerShape(4.dp))
                                    .clickable {
                                        if (openKey == row.key) {
                                            openKey = null
                                        } else {
                                            openKey = row.key
                                            draft = fields.associateWith { "" }
                                            loginNote = null
                                        }
                                    }
                                    .padding(horizontal = 8.dp, vertical = 3.dp),
                            ) {
                                Text(
                                    if (openKey == row.key) "收起" else "填凭据",
                                    color = DesktopTheme.TextSecondary,
                                    fontSize = 11.sp,
                                )
                            }
                        }
                        Box(
                            Modifier
                                .background(DesktopTheme.SurfaceRaised, RoundedCornerShape(4.dp))
                                .clickable { refusal = sources.toggle(row.key) }
                                .padding(horizontal = 8.dp, vertical = 3.dp),
                        ) {
                            Text(if (row.enabled) "停用" else "启用", color = DesktopTheme.TextSecondary, fontSize = 11.sp)
                        }
                    }
                    if (fields.isNotEmpty() && openKey == row.key) {
                        Column(
                            Modifier.fillMaxWidth().padding(start = 12.dp, top = 2.dp, bottom = 6.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(
                                "这几格从那一站的网页里自己复制。值只落在本机数据目录那一份凭据里，不上传；屏上也不回显。",
                                color = DesktopTheme.TextTertiary,
                                fontSize = 11.sp,
                            )
                            fields.forEach { name ->
                                Text(name, color = DesktopTheme.TextSecondary, fontSize = 11.sp)
                                TextField(
                                    value = draft[name].orEmpty(),
                                    onValueChange = { typed -> draft = draft + (name to typed) },
                                    modifier = Modifier.fillMaxWidth(),
                                    visualTransformation = PasswordVisualTransformation(),
                                )
                            }
                            if (busyKey == row.key) {
                                ProgressBar()
                            } else {
                                AccentButton(
                                    onClick = {
                                        busyKey = row.key
                                        loginNote = null
                                        scope.launch {
                                            val outcome = login(
                                                row.key,
                                                DesktopCookieLogin.payload(fields, draft),
                                            )
                                            // stdout 那一行念的是**回核结果**（盘上每枚落了几颗），
                                            // 屏上那一句是人话 —— 两轨各归各的形状（坑 ⑬）。
                                            println(DesktopCookieLogin.reading(row.key, outcome))
                                            busyKey = null
                                            loginNote = DesktopCookieLogin.screenNote(outcome)
                                        }
                                    },
                                    disabled = fields.any { (draft[it] ?: "").isBlank() },
                                ) { Text("登录") }
                            }
                            loginNote?.let {
                                Text(it, color = DesktopTheme.AccentBeni, fontSize = 12.sp, maxLines = 3)
                            }
                        }
                    }
                }
            }
        }
        refusal?.let { Text(it, color = DesktopTheme.AccentBeni, fontSize = 12.sp) }
    }
}
