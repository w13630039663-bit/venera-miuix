package com.venera.compose.feature.settings

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.venera.compose.data.prefs.VeneraPreferences
import com.venera.compose.ui.tokens.VeneraTokens
import com.venera.compose.download.ComicStorageRoot
import com.venera.compose.feature.sourcemanage.GalleryAccountCard
import com.venera.compose.feature.sourcemanage.SauceNaoKeyCard
import com.venera.compose.gallery.data.GalleryImageLoader
import com.venera.compose.gallery.data.GalleryPorts
import com.venera.compose.gallery.domain.GalleryAnimatedMode
import com.venera.compose.gallery.domain.PixivPageSeam
import com.venera.compose.gallery.domain.GalleryColumnMode
import com.venera.compose.gallery.domain.GalleryPreloadMode
import com.venera.compose.gallery.domain.GalleryPreviewQuality
import com.venera.compose.gallery.domain.GallerySaveNaming
import com.venera.compose.gallery.domain.GalleryViewerBackdrop
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 画廊设置（设置首页第 8 个分区，2026-09-29 第五轮）。
 *
 * ## 为什么单独一块，而不是散在「探索 / 应用 / 网络」里
 *
 * 从前画廊的配置分在三处：账号与 SauceNAO Key 在**漫画源管理**页顶部（那页本来的主题是 JS 源脚本），
 * 图片缓存预算根本没法改（写死 512 MB），下载目录跟着漫画根走。用户 2026-09-29 的口径是
 * "做单独的画廊设置，把 api key 迁移到这个设置里，并采用分组卡片"。
 *
 * ## 分组怎么分（按"改它的人当时在解决什么问题"，不是按字段类型）
 *
 * 1. **账号与密钥** —— 不配就取不到图的那一类，风险最高、最少改。
 * 2. **浏览与布局** —— 一屏摆几列、每格摆多清楚。
 * 3. **缓存** —— 磁盘预算与"现在占了多少、一键清掉"。
 * 4. **下载** —— 存到哪、叫什么名。
 *
 * ## 一条边界
 *
 * 分级遮罩与屏蔽规则**不在这里另摆一份**：那是漫画与画廊**共用**的一把判据
 * （见 `GalleryGuard` 与 `ContentGuardManager.findGalleryBlockedRule`），
 * 在「屏蔽与过滤」那一区改一次两边都跟着变。这里再摆一枚开关就是第二份真相。
 */
@Composable
internal fun GallerySettings(prefs: VeneraPreferences, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val columnMode by prefs.galleryColumnMode.collectAsState()
    val previewQuality by prefs.galleryPreviewQuality.collectAsState()
    val cacheMaxMb by prefs.galleryCacheMaxMb.collectAsState()
    val saveNaming by prefs.gallerySaveNaming.collectAsState()
    val customDownloadPath by prefs.galleryDownloadPath.collectAsState()
    // 大图页那四条（批次 C1）：常亮 / 音量键 / 连播间隔 / 智能预加载。
    val keepScreenOn by prefs.galleryKeepScreenOn.collectAsState()
    val volumeKeyTurn by prefs.galleryVolumeKeyTurn.collectAsState()
    val autoPlaySec by prefs.galleryAutoPlaySec.collectAsState()
    val preloadMode by prefs.galleryPreload.collectAsState()
    // 批次 C2 那四条：动图三档、背景四档、画廊自己的 AI 两枚。
    val animatedMode by prefs.galleryAnimated.collectAsState()
    val backdrop by prefs.galleryBackdrop.collectAsState()
    val blockAi by prefs.galleryBlockAi.collectAsState()
    val aiBadge by prefs.galleryAiBadge.collectAsState()
    // 两枚"滚动收起"（2026-09-30）：默认关，见 VeneraPreferences 那两处的说明。
    val hideTopBar by prefs.galleryHideTopBar.collectAsState()
    val hideBottomBar by prefs.galleryHideBottomBar.collectAsState()
    // 「画成水彩」Beta 开关（2026-10-13）：默认关，见 VeneraPreferences 该条说明。
    val watercolorBeta by prefs.galleryWatercolorBeta.collectAsState()
    val pixivWorkSeam by prefs.pixivWorkSeam.collectAsState()
    val pixivWorkDetailsInFlow by prefs.pixivWorkDetailsInFlow.collectAsState()

    /*
     * 浏览历史（2026-10-14）。可空读而不是 checkNotNull：这一颗在契约上就是"移动端才有"，
     * 而设置页属于漫画侧那一棵 —— 读不到就整行不摆。摆一枚永远清不出东西的钮才是假开关，
     * 少一行设置不是缺陷。
     */
    val history = remember { GalleryPorts.of(context).history }
    val historyRows = history?.rows?.collectAsState()?.value ?: emptyList()

    // 磁盘读数与探针一样：都不许在组合期跑（数目录要开文件句柄）。
    var cacheBytes by remember { mutableLongStateOf(-1L) }
    var cacheTick by remember { mutableIntStateOf(0) }
    LaunchedEffect(cacheTick) {
        cacheBytes = withContext(Dispatchers.IO) { GalleryImageLoader.diskUsedBytes(context) }
    }

    val downloadPath = customDownloadPath.ifBlank {
        File(ComicStorageRoot.resolve(context), "图库").absolutePath
    }
    val pickDirLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        // 三道关里有实写探针（要往目标目录写一个测试文件），不能在回调那一帧直接跑。
        scope.launch {
            when (val outcome = withContext(Dispatchers.IO) { evaluatePickedDir(context, uri) }) {
                is DirChoice.Reject -> Toast.makeText(context, outcome.reason, Toast.LENGTH_LONG).show()
                is DirChoice.NeedsPermission -> Toast.makeText(
                    context,
                    "无法写入 ${outcome.path}。请先在「应用 → 本地漫画存储路径」里授权。",
                    Toast.LENGTH_LONG,
                ).show()

                is DirChoice.Accept -> {
                    prefs.setGalleryDownloadPath(outcome.path)
                    Toast.makeText(context, "下载到：${outcome.path}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    SettingsPage(title = "画廊", onBack = onBack, largeTitle = "画廊设置", heroSubtitle = "浏览 · 缓存 · 下载") {
        // 两张卡本身已经是 VeneraCard，所以这一组只借标题、不再套一层 SettingsGroup 的 Card。
        SettingsGroupTitle("账号与密钥")
        GalleryAccountCard()
        SauceNaoKeyCard()
        // 与 SettingsGroup 收尾那段同值：自绘卡片不吃分组容器的间距，得自己留。
        Spacer(Modifier.height(VeneraTokens.spacing.space4))

        SettingsGroup("浏览与布局") {
            // 画廊首页头图（2026-10-15，用户「图廊首页也加上和设置页面一样的可以切换的头图」）。
            // 与设置页那一条**同机制、不同键不同槽位**：共用一张会让"换画廊的头"顺手改掉设置页，
            // 反之也一样。未设置 = 首页维持现在的样子（hero 整条链路不参与）。
            val heroPath by prefs.galleryHeroPath.collectAsState()
            val heroFile = remember(heroPath) { SettingsHeroImageStore.resolve(heroPath) }
            val pickGalleryHero = rememberSettingsImagePicker(SettingsHeroImageStore.KIND_GALLERY_HERO) { path ->
                if (path != null) prefs.setGalleryHeroPath(path)
            }
            SettingsAction(
                title = "更换首页头图",
                summary = if (heroFile != null) "只影响画廊首页顶部" else "未设置，首页保持默认样式",
            ) { pickGalleryHero() }
            SettingsAction(
                title = "恢复默认头图",
                summary = "清除所选图片",
                enabled = heroFile != null,
            ) {
                SettingsHeroImageStore.clear(context, SettingsHeroImageStore.KIND_GALLERY_HERO)
                prefs.setGalleryHeroPath("")
            }
            SettingsSelect(
                "网格列数", columnMode.name,
                listOf(
                    GalleryColumnMode.AUTO.name to "自动（大屏 3 列、手机 2 列）",
                    GalleryColumnMode.TWO.name to "两列",
                    GalleryColumnMode.THREE.name to "三列",
                ),
                { prefs.setGalleryColumnMode(GalleryColumnMode.valueOf(it)) },
                summary = "自动按屏幕宽度决定。平板上觉得太大，可以选两列。",
            )
            SettingsSelect(
                "预览清晰度", previewQuality.name,
                listOf(
                    GalleryPreviewQuality.PREVIEW.name to "标准（站方缩略图，一张几十 KB）",
                    GalleryPreviewQuality.LARGE.name to "更清晰（站方 sample 图，流量大约十倍）",
                ),
                { prefs.setGalleryPreviewQuality(GalleryPreviewQuality.valueOf(it)) },
                summary = "只影响列表小图，点开后仍按原方式加载。",
            )
            SettingsToggle(
                "上滑时收起顶栏", hideTopBar, { prefs.setGalleryHideTopBar(it) },
                summary = "向上浏览时收起顶栏，给图片留出空间。向下滑会重新显示。",
            )
            SettingsToggle(
                "上滑时收起底栏", hideBottomBar, { prefs.setGalleryHideBottomBar(it) },
                summary = "向上浏览时收起底部导航栏，只影响画廊。",
            )
            history?.let { store ->
                // 张数直接订阅存储件那份名单：清完自己归零，不需要图片缓存那种重取读数。
                SettingsAction(
                    "清空画廊浏览历史",
                    if (historyRows.isEmpty()) "还没有记下看过哪些图" else "当前记了 ${historyRows.size} 张",
                    onClick = {
                        scope.launch { store.clear() }
                        Toast.makeText(context, "浏览历史已清空", Toast.LENGTH_SHORT).show()
                    },
                )
            }
        }

        SettingsGroup("缓存") {
            SettingsSelect(
                "图片缓存上限", cacheMaxMb.toString(),
                listOf("256", "512", "1024", "2048").map { it to "$it MB" },
                { value ->
                    val mb = value.toIntOrNull() ?: return@SettingsSelect
                    prefs.setGalleryCacheMaxMb(mb)
                    // 上限只在构造 DiskCache 时读一次，不丢实例重建就是个假开关。
                    scope.launch(Dispatchers.IO) { GalleryImageLoader.reset(context) }
                    Toast.makeText(context, "图片缓存已按 $mb MB 重建", Toast.LENGTH_SHORT).show()
                },
                summary = "超过上限会自动清理最久未使用的内容。",
            )
            SettingsAction(
                "立即清除图片缓存",
                if (cacheBytes < 0) "正在统计…" else "当前占用 ${(cacheBytes + 999_999) / 1_000_000} MB",
                onClick = {
                    scope.launch(Dispatchers.IO) {
                        val cleared = GalleryImageLoader.clearDiskCache(context)
                        Toast.makeText(
                            context,
                            "已清除 ${cleared / 1_000_000} MB",
                            Toast.LENGTH_SHORT,
                        ).show()
                        cacheTick++
                    }
                },
            )
        }

        SettingsGroup("下载") {
            SettingsAction(
                "下载目录",
                if (customDownloadPath.isBlank()) "$downloadPath（默认）" else "$downloadPath（自定义）",
                onClick = { pickDirLauncher.launch(null) },
            )
            if (customDownloadPath.isNotBlank()) {
                SettingsAction("恢复默认下载目录", "当前：$customDownloadPath") {
                    prefs.setGalleryDownloadPath("")
                    Toast.makeText(context, "已改回用漫画存储目录下的 图库", Toast.LENGTH_SHORT).show()
                }
            }
            SettingsSelect(
                "文件名规则", saveNaming.name,
                listOf(
                    GallerySaveNaming.SITE_ID.name to "站名-编号（yandere-1234567.png）",
                    GallerySaveNaming.ORIGINAL.name to "原图文件名（站方给的名字，通常是一串乱码）",
                    GallerySaveNaming.ARTIST_ID.name to "画师_编号（wlop_1234567.png）",
                    GallerySaveNaming.TIMESTAMP.name to "时间戳（20260929-113045.png）",
                ),
                { prefs.setGallerySaveNaming(GallerySaveNaming.valueOf(it)) },
                summary = "取不到画师时使用站名和编号，重名会自动加编号。",
            )
        }

        SettingsGroup("大图页") {
            SettingsToggle(
                "屏幕常亮", keepScreenOn, { prefs.setGalleryKeepScreenOn(it) },
                summary = "看大图时保持屏幕常亮，离开后恢复。",
            )
            SettingsToggle(
                "音量键翻页", volumeKeyTurn, { prefs.setGalleryVolumeKeyTurn(it) },
                summary = "音量下键下一张，上键上一张，不循环。",
            )
            SettingsSlider(
                "自动连播间隔", autoPlaySec.toFloat(), 0f..30f,
                { prefs.setGalleryAutoPlaySec(it.toInt()) }, steps = 29, suffix = " 秒",
                summary = "到头就停，0 关闭。",
            )
            SettingsSelect(
                "预加载", preloadMode.name,
                listOf(
                    GalleryPreloadMode.OFF.name to "关闭",
                    GalleryPreloadMode.NEXT.name to "提前加载下一张",
                    GalleryPreloadMode.BOTH_TWO.name to "提前加载前后各两张",
                ),
                { prefs.setGalleryPreload(GalleryPreloadMode.valueOf(it)) },
                summary = "提前加载前后几张图。调大更流畅，也更费流量。",
            )
            SettingsSelect(
                "动图自动播放", animatedMode.name,
                listOf(
                    GalleryAnimatedMode.WIFI_ONLY.name to "仅 Wi-Fi",
                    GalleryAnimatedMode.ALWAYS.name to "始终",
                    GalleryAnimatedMode.NEVER.name to "从不（停在第一帧）",
                ),
                { prefs.setGalleryAnimated(GalleryAnimatedMode.valueOf(it)) },
                summary = "只影响大图页。列表里的动图始终显示第一帧。",
            )
            SettingsSelect(
                "大图页背景", backdrop.name,
                listOf(
                    GalleryViewerBackdrop.GLASS.name to "模糊 + 压暗（默认）",
                    GalleryViewerBackdrop.BLACK.name to "纯黑",
                    GalleryViewerBackdrop.DARK_GRAY.name to "深灰",
                    GalleryViewerBackdrop.WHITE.name to "纯白",
                ),
                { prefs.setGalleryBackdrop(GalleryViewerBackdrop.valueOf(it)) },
                summary = "只影响大图页背景。改回模糊后，需要重新进入大图页。",
            )
            SettingsToggle(
                "画成水彩（Beta）", watercolorBeta, { prefs.setGalleryWatercolorBeta(it) },
                summary = "开启后，可在「关于这张图」面板把当前图片重画成水彩，原图不变。",
            )
        }

        SettingsGroup("pixiv 作品页") {
            SettingsSelect(
                "页之间留一条边", pixivWorkSeam.name,
                listOf(
                    PixivPageSeam.HAIRLINE.name to "留一条极淡的边（默认）",
                    PixivPageSeam.NONE.name to "完全无缝",
                ),
                { prefs.setPixivWorkSeam(PixivPageSeam.valueOf(it)) },
                summary = "只影响竖向连续翻页。留边更容易看出已经翻页。",
            )
            SettingsToggle(
                "详情接在最后一页之后", pixivWorkDetailsInFlow, { prefs.setPixivWorkDetailsInFlow(it) },
                summary = "开启后，看完最后一页继续下滑就是作品详情。",
            )
        }

        SettingsGroup("内容与屏蔽") {
            SettingsToggle(
                "屏蔽 AI 生成的条目", blockAi, { prefs.setGalleryBlockAi(it) },
                summary = "只认站方 AI 标记，不靠画风判断。",
            )
            SettingsToggle(
                "给 AI 条目摆角标", aiBadge, { prefs.setGalleryAiBadge(it) },
                summary = "在卡片左上角标出 AI。浏览历史里不显示角标。",
            )
            SettingsAction(
                "分级遮罩与屏蔽规则在「屏蔽与过滤」里改",
                "漫画和画廊共用这些规则。",
            )
        }
    }
}
