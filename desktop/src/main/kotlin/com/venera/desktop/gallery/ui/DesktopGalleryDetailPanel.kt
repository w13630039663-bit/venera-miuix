package com.venera.desktop.gallery.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import com.venera.compose.gallery.data.GalleryPost
import com.venera.compose.gallery.data.GalleryPorts
import com.venera.compose.gallery.data.GallerySite
import com.venera.compose.gallery.data.preferredExternalUrl
import com.venera.compose.gallery.domain.GalleryPreviewQuality
import com.venera.desktop.gallery.data.DesktopDictRead
import com.venera.desktop.gallery.data.DesktopDictWhy
import com.venera.desktop.gallery.data.DesktopGalleryTagLexicon
import com.venera.desktop.gallery.data.DesktopGalleryBrowseHistory
import com.venera.desktop.gallery.data.desktopSaveImageRun
import com.venera.desktop.gallery.data.desktopSaveImageScreenReading
import io.github.composefluent.component.Button
import io.github.composefluent.component.Text
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.io.File
import kotlinx.coroutines.launch

internal val LocalDesktopWallPick = compositionLocalOf<(DesktopWallSelection) -> Unit> {
    { error("这一屏的墙没被 provide 过点卡回调：选中态要从应用根（VeneraDesktopApp）传下来") }
}

/**
 * 「起窗后要不要自己点开第一张卡」—— 只服务取证参数 `--detail`（判读见 `ForensicFlags`）。
 *
 * ⚠️ 默认值**必须是 `false`**：这一枚与上面那颗不同，漏 provide 的形状不是假开关，
 * 而是"每次启动都无缘无故弹一层浮层在屏上"—— 把取证专用的行为变成默认行为。
 * 那条由 `DesktopGalleryDetailP1Test` ⑨ 钉着。
 */
internal val LocalDesktopWallSeedFirst = compositionLocalOf<Boolean> { false }

/**
 * 点一张卡之后从右侧盖上来的那 340 宽浮层（第十一批 P0，稿画板 B `.detail`）。
 *
 * ## 它是**覆盖层**，不是主区的一列
 *
 * 稿 `:660` 量过：并排会把每列压到 148、违反"每列 200"那条预算，所以它是 `align(TopEnd)` 叠上去的，
 * **不参与宽度分配**（判据 ⑥ 扫着这一颗件里不许出现 `weight(` —— 正文那一栏靠"剩余高度"就够，
 * 它不需要、也不该要权重）。左缘那道 46 宽渐隐（稿 `.detail::before`）同样不在这颗件里画，
 * 而在骨架那一层：它要压在**别人的**内容上，画在自己框内就只是自己边上的一条脏带。
 *
 * ## 挡住的那一档**照挡**
 *
 * [masked] 是从墙上那张卡一路传进来的（墙往下交的是整张卡 [DesktopWallCard] 而不是裸 post，
 * 就是为了这一档不丢）。面板里不重新判分级，也不"点开就放行"：墙上不给看的东西，点开也不给 ——
 * 反过来就是给第四批那层守卫开一个绕出口。
 *
 * ## 稿上有六格四钮，这里只摆今天填得出的
 *
 * 标题、相关标签、画师"作品 N"、上传时间**整格不摆**（前三样结构性没有：三站 API 都不给标题、
 * 没有 related_tags 的来源端点、Android 那侧也没有作品数；上传时间是三颗 post DTO 都没解 `created_at`）。
 * 稿上那四钮今天接三钮（收藏、复制链接、在浏览器打开原图）：下载要另造桌面落盘件（P2），而稿上那枚
 * "分享"在桌面能做的其实只有"复制链接" ⇒ 按拍板改稿叫**复制链接**，不留一枚语义含糊的字。
 * 第四钮是批 35 加的「找同一张图」，稿上原来没有它 —— 它带的是一整格结果，所以那一格排在
 * 动作之后、信息表之前（与标签墙那条同一个理由：按完看不见的东西等于没做）。
 * 第五钮是批 54 加的「复制图片」，稿上也没有：链接那一条交出去的是地址，而对面要图时地址粘不出东西。
 * 这一条由判据 ③ 钉住：撤掉的东西一旦被谁顺手加回来，屏上就会长出一格常年空白的表。
 *
 * ## 尺寸一个都不写死
 *
 * 稿上那批值（340 / 46 / 200 / 30 / 16 / 12 / 76 / 10 / 7 / 1px / 6px / 8px）全在
 * [DesktopGalleryMetrics] 的"详情浮层"那一段，判据 ⑧ 扫这一颗件里**一个写死的 dp 都不许有**。
 * 底色取 [DesktopTheme.SidebarBackground] —— 稿的 `--layer`（`:10`）与侧栏是**同一颗** `#272727`，
 * 所以这里复用它的名字，不新增第二颗同值色。
 */

private fun DesktopDetailTier.panelLabel(): String = desktopDetailTierLabel(this)

/** 图位取不到时的底：灰块 + 一句"是哪一档取不到"。与墙上同一族，但这里只有一句（没有站名，站名在头顶那行已经念过）。 */
