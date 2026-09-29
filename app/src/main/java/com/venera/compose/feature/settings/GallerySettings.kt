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
import com.venera.compose.gallery.domain.GalleryAnimatedMode
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
                    "${outcome.path} 写不进去，需要所有文件访问权限。可以先在 应用 → 本地漫画存储路径 里授予，" +
                        "再回到这里重新选目录）",
                    Toast.LENGTH_LONG,
                ).show()

                is DirChoice.Accept -> {
                    prefs.setGalleryDownloadPath(outcome.path)
                    Toast.makeText(context, "下载到：${outcome.path}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    SettingsPage(title = "画廊", onBack = onBack, largeTitle = "画廊设置") {
        // 两张卡本身已经是 VeneraCard，所以这一组只借标题、不再套一层 SettingsGroup 的 Card。
        SettingsGroupTitle("账号与密钥")
        GalleryAccountCard()
        SauceNaoKeyCard()
        // 与 SettingsGroup 收尾那段同值：自绘卡片不吃分组容器的间距，得自己留。
        Spacer(Modifier.height(VeneraTokens.spacing.space4))

        SettingsGroup("浏览与布局") {
            SettingsSelect(
                "网格列数", columnMode.name,
                listOf(
                    GalleryColumnMode.AUTO.name to "自动（大屏 3 列、手机 2 列）",
                    GalleryColumnMode.TWO.name to "两列",
                    GalleryColumnMode.THREE.name to "三列",
                ),
                { prefs.setGalleryColumnMode(GalleryColumnMode.valueOf(it)) },
                summary = "自动按屏幕宽度决定。平板上觉得格子太大就手动选两列。",
            )
            SettingsSelect(
                "预览清晰度", previewQuality.name,
                listOf(
                    GalleryPreviewQuality.PREVIEW.name to "标准（站方缩略图，一张几十 KB）",
                    GalleryPreviewQuality.LARGE.name to "更清晰（站方 sample 图，流量大约十倍）",
                ),
                { prefs.setGalleryPreviewQuality(GalleryPreviewQuality.valueOf(it)) },
                summary = "只影响列表里的小图。点开大图仍然按中档、原图两步加载，" +
                    "不受这里影响。",
            )
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
                summary = "超过上限会自动淘汰最久没用过的，不用手动清理。",
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
                if (customDownloadPath.isBlank()) "$downloadPath（沿用漫画存储目录下的 图库 子目录）" else "$downloadPath（自己选的目录）",
                onClick = { pickDirLauncher.launch(null) },
            )
            if (customDownloadPath.isNotBlank()) {
                SettingsAction("改回沿用漫画目录", "当前：$customDownloadPath") {
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
                summary = "取不到画师时改用站名-编号；文件名撞了会自动加 (1)。",
            )
        }

        SettingsGroup("大图页") {
            SettingsToggle(
                "屏幕常亮", keepScreenOn, { prefs.setGalleryKeepScreenOn(it) },
                summary = "看大图时不让屏幕熄灭，离开这一页就恢复。",
            )
            SettingsToggle(
                "音量键翻页", volumeKeyTurn, { prefs.setGalleryVolumeKeyTurn(it) },
                summary = "音量键向下是下一张、向上是上一张，到头就停，不循环。" +
                    "关闭时音量键还是系统的音量条。",
            )
            SettingsSlider(
                "自动连播间隔", autoPlaySec.toFloat(), 0f..30f,
                { prefs.setGalleryAutoPlaySec(it.toInt()) }, steps = 29, suffix = " 秒",
                summary = "在当前列表里自动翻到下一张，翻到头就停，填 0 关闭。" +
                    "信息面板开着、放大看、或者这一张是视频的时候不会自动翻。" +
                    "大图页底栏的播放按钮可以临时改间隔（长按出滑条），离开那一页就恢复成这里的设置。",
            )
            SettingsSelect(
                "预加载", preloadMode.name,
                listOf(
                    GalleryPreloadMode.OFF.name to "关闭",
                    GalleryPreloadMode.NEXT.name to "提前加载下一张",
                    GalleryPreloadMode.BOTH_TWO.name to "提前加载前后各两张",
                ),
                { prefs.setGalleryPreload(GalleryPreloadMode.valueOf(it)) },
                summary = "决定翻页之前先把旁边几张的中档图取进缓存。" +
                    "关闭时当前这张照常加载，但翻过去时旁边的要现取，会白一下。" +
                    "调大更跟手，也更费流量和缓存。",
            )
            SettingsSelect(
                "动图自动播放", animatedMode.name,
                listOf(
                    GalleryAnimatedMode.WIFI_ONLY.name to "仅 Wi-Fi",
                    GalleryAnimatedMode.ALWAYS.name to "始终",
                    GalleryAnimatedMode.NEVER.name to "从不（停在第一帧）",
                ),
                { prefs.setGalleryAnimated(GalleryAnimatedMode.valueOf(it)) },
                summary = "只影响大图页里的这一张，列表中的动图一直显示第一帧，" +
                    "一屏同时解很多动画会很占内存。" +
                    "是不是计费网络按系统判断，判断不了就按计费网络处理，不自动播。" +
                    "进入这一页时判断一次，中途换网络不会让动画忽开忽停。",
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
                summary = "没有跟随图片主色调这一项：试过，深色主题下顶部会出现一块颜色很硬的色块，已经撤掉。" +
                    "选纯黑、深灰或纯白时背景会马上挡住后面的模糊；" +
                    "改回模糊那一档要重新进入大图页才生效。",
            )
        }

        SettingsGroup("内容与屏蔽") {
            SettingsToggle(
                "屏蔽 AI 生成的条目", blockAi, { prefs.setGalleryBlockAi(it) },
                summary = "图库单独的开关，和屏蔽与过滤里漫画那一侧互不影响。" +
                    "只认站方标的 ai-generated 或 ai_generated 标签，没有标签就不算，" +
                    "也不按看起来像不像来判断。单独的 ai 标签不算：它在 yande.re 上是一个角色名，" +
                    "算进来会把 19 张手工图误判成 AI。",
            )
            SettingsToggle(
                "给 AI 条目摆角标", aiBadge, { prefs.setGalleryAiBadge(it) },
                summary = "在卡片左上角标一个 AI。开着屏蔽时基本看不到（那些条目不会上屏），" +
                    "用处是关掉屏蔽以后还能分辨哪张是 AI 画的。",
            )
            SettingsAction(
                "分级遮罩与屏蔽规则在「屏蔽与过滤」里改",
                "那两样漫画和图库共用一份设置，这里不重复摆开关，免得两边不一致。" +
                    "AI 这一条是例外：两边分开设置，但用的词表是同一份。",
            )
        }
    }
}
