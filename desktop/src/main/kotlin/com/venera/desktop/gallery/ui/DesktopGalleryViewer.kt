package com.venera.desktop.gallery.ui

import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import com.venera.compose.gallery.domain.GalleryViewerBackdrop
// 文字走本仓那枚（compose-fluent），不是 material3 —— 桌面依赖里根本没有 material3
// （第一次写错时编译报的就是 `Unresolved reference 'material3'`）。
import io.github.composefluent.component.Button
import io.github.composefluent.component.Text
import net.engawapg.lib.zoomable.MouseWheelZoom
import net.engawapg.lib.zoomable.ZoomState
import net.engawapg.lib.zoomable.zoomable
import kotlinx.coroutines.launch

/**
 * 大图那一屏：占满内容区的一张图，带缩放、平移、页码，以及放大时的倍数与那一枚「回到原大小」。
 *
 * ## 形态
 *
 * 全屏接管内容区，原浮层继续浮在右侧（形态已拍板，见
 * `docs/rounds/desktop-image-viewer-design-2026-10-08.md` §3）。可用宽度由
 * [desktopViewerContentWidth] 给 —— 面板与它左缘那道渐隐带**都要**让开。
 *
 * ## 覆盖层的三条纪律（这一层全部适用）
 *
 * 1. **动作走必填形参**，不吃 compositionLocal：本层挂在应用根那圈 provider **外面**
 *    （浮层也是同一形状），2026-10-05 用户一按就炸在 local 默认值上、读数只落 stdout 而屏上不动。
 *    这里连"提示"也不走 provider：失败那句是**本地态**（应用根今天没有可复用的提示通道，
 *    `VeneraDesktopApp` 里那两枚坏档读数各消费一次就完了，不是通知）。
 * 2. **缺席文案与浮层逐字同串**（下面三句）：同一件事在两屏念成两个名字就是假话。
 * 3. **不许重述共享判据**：取哪一档的地址转交 [desktopDetailTierUrl]，前两档在它里面
 *    逐字转交对岸的 `GallerySettingsModel.wallUrl`。
 *
 * ## 翻页与缩放
 *
 * 翻页时缩放**复位到 1x**：带着放大态翻页，页码与屏上那张就对不上（假读数）。
 * 缩放态住在应用根（[ZoomState] 由那边持有），而连播那颗判据要读它的 `scale`。
 */
@Composable
internal fun DesktopGalleryViewer(
    selection: DesktopWallSelection,
    tier: DesktopDetailTier,
    zoomState: ZoomState,
    viewerZoomSeed: Float?,
    contentWidth: Dp,
    /** 该不该逐帧。**判据不在这里**：由集成方现算共享面那颗 `GalleryMotion.animates` 传下来（批 51）。 */
    animateGifs: Boolean,
    /**
     * 那一层底吃哪一档（批 61）。**必填参数**：给默认值等于允许调用点漏传，
     * 而漏传的后果是"屏上改了档而这一层不动"—— 正是这一族最贵的形状。
     */
    backdrop: GalleryViewerBackdrop,
) {
    // 翻页 / 换档就清掉失败那句：上一张的失败原话压在下一张上面就是假读数。
    var failure by remember(selection.index, tier) { mutableStateOf<String?>(null) }
    val card = selection.card
    // 那一枚「回到原大小」要发一笔 suspend 的复位 ⇒ 得有一枚挂在组合上的协程作用域。
    val scope = rememberCoroutineScope()
    Box(
        Modifier
            .width(contentWidth)
            .fillMaxHeight()
            .background(desktopBackdropColor(backdrop)),
        contentAlignment = Alignment.Center,
    ) {
        // 缩放层与覆盖层**分家**（第五十五批图证抓到的那一条）：`.zoomable` 的变换吃的是它那一格的
        // 整棵子树，覆盖层若住在里面，放大到 2.5 倍时页码与倍数会被一起推出屏外 —— 而那一格存在的
        // 唯一理由就是「放大之后还看得见」。第一版就栽在这里：帧上整屏没有那两格。
        // 取址全文件只这一处（`DesktopGalleryViewerTest` ① 钉它），底下两轨吃同一份。
        val regionUrl = card?.takeIf { !it.masked }?.let { desktopDetailTierUrl(it.post, tier) }.orEmpty()
        DesktopViewerStage(
            card = card,
            tier = tier,
            url = regionUrl,
            zoomState = zoomState,
            animateGifs = animateGifs,
            // 失败那句交给上面那一格摆：覆盖层不住在变换里。
            onFailure = { why -> failure = why },
        )
        // 批 89：放大到"这一块还有更多细节可取"那一档以上时，盖一层现解的源像素。
        // ⚠️ 它**不住在 `.zoomable` 里面** —— 分块图本身已经是看得见的那一块，再吃一遍变换会错两回；
        // 位置由 DesktopVisibleRegion 那一处算式给，与底下那层对齐（对不齐只有图证抓得到）。
        if (desktopRegionOverlayLands && regionUrl.isNotBlank()) {
            DesktopRegionOverlay(
                url = regionUrl,
                zoomState = zoomState,
                tier = tier.name,
                onFallback = { why -> failure = why },
            )
        }
        failure?.let { why -> Text(why, Modifier.align(Alignment.BottomStart)) }
        Text(
            "第 ${selection.index + 1} / ${selection.size} 张",
            Modifier.align(Alignment.BottomCenter),
        )
        // 放大到几倍要看得境（第五十五批）。这一格吃的必须是**活的那枚** `zoomState.scale`
        // 而不是种子那个数：滚轮、双指、种子三条路都写进它，读初值就等于只给种子那一路作证。
        // 没放大时整格不摆 —— 常驻一句「放大 1.0x」只会把右下角占成背景。
        val zoomChrome = desktopZoomChrome(zoomState.scale)
        if (zoomChrome != null) {
            Row(
                modifier = Modifier.align(Alignment.BottomEnd),
                horizontalArrangement = Arrangement.spacedBy(DesktopGalleryMetrics.tightGap),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(zoomChrome)
                // 复位走库那颗 `reset()` 而不是 `changeScale(1f)`：放大后拖偏过，
                // 只改倍数的话图还偏在屏幕外，那一枚就成了假开关。它是 suspend ⇒ 要有人 launch。
                Button(onClick = { scope.launch { zoomState.reset() } }) {
                    Text("回到原大小")
                }
            }
        }
    }
    // 翻页复位到 1x（理由写在类注释里）。用 `snapTo` 的那一格而不是动画：复位是纠正，
    // 带着过场就又变成"我以为换了张图其实还在放大"。
    LaunchedEffect(selection.index) { zoomState.reset() }
    // 取证种子（`--viewer-zoom=`）。走**公开的** `changeScale` 而不是构造参数
    // `initialScale`（它在库里是 private）—— 这样生产与取证两条路的初值都留 1f，
    // `reset()` 的语义在两路上完全一致。
    LaunchedEffect(viewerZoomSeed) {
        val raw = viewerZoomSeed ?: return@LaunchedEffect
        val applied = desktopViewerZoomScale(raw, zoomState.maxScale)
        println(desktopViewerZoomReadout(raw, applied))
        zoomState.changeScale(applied, Offset.Zero, tween(0))
    }
}


/**
 * 缩放层那一格：只有这里面的东西跟着倍数走（批 55 分家的另一半）。
 *
 * 失败那句交回 [onFailure] 由外面那一格摆 —— 覆盖层不住在变换里，
 * 而「取这一档失败」那句原话压在屏上才量得到（它跟着图一起被推出屏外就等于没说）。
 */
@Composable
private fun DesktopViewerStage(
    card: DesktopWallCard?,
    tier: DesktopDetailTier,
    // 批 89：地址由外层算一次递进来 —— 取址那一处全文件只许有一处（旧尺子钉的就是它）。
    url: String,
    zoomState: ZoomState,
    animateGifs: Boolean,
    /** 必填、不给默认值：漏传的形状就是「取图失败而屏上什么都不念」，那一枚正是本仓最忌的静默。 */
    onFailure: (String) -> Unit,
) {
    Box(
        Modifier
            .fillMaxSize()
            // ⚠️ 库的滚轮默认档是 `EnabledWithCtrlKey`（裸滚轮**不**缩放）。不显式选 `Enabled`，
            // "滚轮没反应"会被读成"库在桌面上不生效"——而那一屏底下没有可滚的内容，让开滚轮不抢东西。
            .zoomable(zoomState, mouseWheelZoom = MouseWheelZoom.Enabled),
        contentAlignment = Alignment.Center,
    ) {
        when {
            // 被分级挡住的那一张连发起取图都不该有（与墙上、浮层同一条规矩）。
            card?.masked == true -> Text("分级遮挡", textAlign = TextAlign.Center)
            // 序号不在这一批里：说清而不是摆一张空卡（同一句在浮层那边是 requireNotNull + 报错）。
            card == null -> Text("这一批里没有这一张", textAlign = TextAlign.Center)
            else -> {
                // 走不走逐帧那一档：先看地址像不像，再看这一屏该不该动（两枚都是纯函数）。
                // 播放器一旦退化就把这一格翻回 false ⇒ 落到下面那条 `AsyncImage`，**不许留白屏**。
                var playAnimated by remember(url, animateGifs) {
                    mutableStateOf(desktopLooksAnimated(url) && animateGifs)
                }
                if (url.isBlank()) {
                    // 「没给」与「没取到」是两句话：这一句说的是站方那一档本来就没有。
                    Text("站方没给这一档的图", textAlign = TextAlign.Center)
                } else if (playAnimated) {
                    DesktopAnimatedImage(
                        url = url,
                        tier = tier,
                        animateGifs = animateGifs,
                        contentDescription = "${card.post.site.displayName} 的作品",
                        onFallback = { cause ->
                            // 退化要出声：openSnapshot 键不中是恒空，不说就量不到（设计 §2 第 3 条）。
                            println("D_动图 退化 ${desktopAnimatedFallback(cause)} 档=$tier")
                            playAnimated = false
                        },
                    )
                } else {
                    AsyncImage(
                        model = url,
                        contentDescription = "${card.post.site.displayName} 的作品",
                        // **恒 Fit**（浮层今天是「原图档才 Fit、其余 Crop」）：放大看图时
                        // Crop 裁掉的那块回不来，而这一层存在的意义就是看细节。
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize(),
                        onState = { state ->
                            if (state is AsyncImagePainter.State.Error) {
                                // 失败原话 + 本机那半句（反搜那批定的写法），不静默变白屏。
                                val why = state.result.throwable.message?.take(200)
                                    ?: state.result.throwable.javaClass.simpleName
                                onFailure("取这一档失败：$why")
                                println("D_大图 取图失败 $why 档=$tier")
                            }
                        },
                    )
                }
            }
        }
    }
}