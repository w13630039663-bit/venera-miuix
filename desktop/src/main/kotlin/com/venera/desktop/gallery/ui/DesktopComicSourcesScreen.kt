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

