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
import com.venera.desktop.gallery.data.DesktopDecoTier
import com.venera.desktop.gallery.data.DesktopDecoTierPreferences
import com.venera.desktop.gallery.data.DesktopGalleryCredentials
import com.venera.desktop.gallery.data.DesktopGalleryPreferences
import io.github.composefluent.component.AccentButton
import io.github.composefluent.component.Button
import io.github.composefluent.component.RadioButton
import io.github.composefluent.component.Switcher
import io.github.composefluent.component.Text
import io.github.composefluent.component.TextField
import kotlinx.coroutines.launch

internal const val DECO_TIER_SECTION_TITLE: String = "首页装饰的浓度"

/**
 * 三档各摆一枚按钮，档名就是稿上那三档的中文名（素 / 纹样 / 神社）。
 *
 * ⚠️ 说明里那句「窗口窄到封面墙少一列时自动退一档」是**屏上会自己发生**的事，必须明写：
 * 用户选了神社档而在窄窗上看不见鸟居，那句实话只有这一格能说（稿 :714 那条检查单）。
 *
 * ⚠️ 这一颗是 `internal` 而不是 `private`：**离屏真渲要能单独渲这一行**（`DesktopDecoTierWiringTest`
 * 的 `performClick` 那一颗），而整屏那颗宿主吃的是 `DesktopGalleryPorts`，测试里为了一枚单选钮
 * 去搭一整副端口不值当。抬成 internal 不扩大对外面（同模块内才可见）。
 */
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

private fun preloadTierLabel(mode: GalleryPreloadMode): String = when (mode) {
    GalleryPreloadMode.OFF -> "不提前取"
    GalleryPreloadMode.NEXT -> "只取下一张"
    GalleryPreloadMode.BOTH_TWO -> "前后各两张"
}
