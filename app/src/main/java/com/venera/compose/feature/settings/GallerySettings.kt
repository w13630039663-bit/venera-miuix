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
                    "${outcome.path} 写不进去：需要「所有文件访问」权限（可先在「应用 → 本地漫画存储路径」" +
                        "那一步授予，回来再挑）",
                    Toast.LENGTH_LONG,
                ).show()

                is DirChoice.Accept -> {
                    prefs.setGalleryDownloadPath(outcome.path)
                    Toast.makeText(context, "画廊下载目录：${outcome.path}", Toast.LENGTH_SHORT).show()
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
                summary = "自动那一档按屏宽定，平板上觉得格子太大就手动选两列。",
            )
            SettingsSelect(
                "预览清晰度", previewQuality.name,
                listOf(
                    GalleryPreviewQuality.PREVIEW.name to "标准（站方缩略档，几十 KB 一张）",
                    GalleryPreviewQuality.LARGE.name to "更清晰（站方 sample 档，一屏流量按十倍计）",
                ),
                { prefs.setGalleryPreviewQuality(GalleryPreviewQuality.valueOf(it)) },
                summary = "只改**墙上那一格**。大图页仍是三档叠画（开门档→中档→原图），" +
                    "那一套的判据在条目地址表里，不归这一档管。",
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
                    Toast.makeText(context, "已按 $mb MB 重建画廊图片缓存", Toast.LENGTH_SHORT).show()
                },
                summary = "超出上限时自动淘汰最久没再用的那些（LRU，不用手动收拾）。",
            )
            SettingsAction(
                "立即清除图片缓存",
                if (cacheBytes < 0) "正在统计…" else "当前占用 ${(cacheBytes + 999_999) / 1_000_000} MB",
                onClick = {
                    scope.launch(Dispatchers.IO) {
                        val cleared = GalleryImageLoader.clearDiskCache(context)
                        Toast.makeText(
                            context,
                            "已清除画廊图片缓存 ${cleared / 1_000_000} MB",
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
                if (customDownloadPath.isBlank()) "$downloadPath（沿用本地漫画存储根下的「图库」）" else "$downloadPath（画廊自选）",
                onClick = { pickDirLauncher.launch(null) },
            )
            if (customDownloadPath.isNotBlank()) {
                SettingsAction("改回沿用漫画库", "当前：$customDownloadPath") {
                    prefs.setGalleryDownloadPath("")
                    Toast.makeText(context, "已改回沿用漫画存储根下的「图库」", Toast.LENGTH_SHORT).show()
                }
            }
            SettingsSelect(
                "文件名规则", saveNaming.name,
                listOf(
                    GallerySaveNaming.SITE_ID.name to "站名-编号（yandere-1234567.png）",
                    GallerySaveNaming.ORIGINAL.name to "原图文件名（站方那个名字，多半是一串哈希）",
                    GallerySaveNaming.ARTIST_ID.name to "画师_编号（wlop_1234567.png）",
                    GallerySaveNaming.TIMESTAMP.name to "时间戳（20260929-113045.png）",
                ),
                { prefs.setGallerySaveNaming(GallerySaveNaming.valueOf(it)) },
                summary = "取不到画师时「画师_编号」会退回「站名-编号」，不编一个名字出来。" +
                    "重名一律让位成「名字 (1).jpg」。",
            )
        }

        SettingsGroup("大图页") {
            SettingsToggle(
                "屏幕常亮", keepScreenOn, { prefs.setGalleryKeepScreenOn(it) },
                summary = "停在大图页时不让屏幕熄灭。退出那一页时这条 flag 一并撤掉，" +
                    "不会让整台机器跟着不睡。",
            )
            SettingsToggle(
                "音量键翻页", volumeKeyTurn, { prefs.setGalleryVolumeKeyTurn(it) },
                summary = "音量下 = 下一张、音量上 = 上一张，到底就停、不循环。" +
                    "关着的时候按音量键还是系统的音量条 —— 这一档默认是关的。",
            )
            SettingsSlider(
                "自动连播间隔", autoPlaySec.toFloat(), 0f..30f,
                { prefs.setGalleryAutoPlaySec(it.toInt()) }, steps = 29, suffix = " 秒",
                summary = "沿当前那面墙自动翻到下一张，到底就停（0 = 关闭连播）。" +
                    "信息面板开着、正在放大、或那一条是视频时都不走 —— " +
                    "连播不该替人决定什么时候起播视频、什么时候出声。" +
                    "大图页底栏那颗 ▶ 可以**当场**改这个间隔（长按它出滑条），" +
                    "那一调只活在那一屏，退出仍回到这里设的值。",
            )
            SettingsSelect(
                "智能预加载", preloadMode.name,
                listOf(
                    GalleryPreloadMode.OFF.name to "关闭（只取当前这一张）",
                    GalleryPreloadMode.NEXT.name to "预加载下一张",
                    GalleryPreloadMode.BOTH_TWO.name to "预加载前后两张",
                ),
                { prefs.setGalleryPreload(GalleryPreloadMode.valueOf(it)) },
                summary = "这一档管的是「提前把隔壁那几张的中档拉进缓存」。" +
                    "选「关闭」**不等于没有流量**：当前这一张自己的三档（开门→中档→原图）照旧会发，" +
                    "而且翻页时隔壁要现组合，会白闪一下。调大则流量与那份图片缓存都跟着涨。",
            )
            SettingsSelect(
                "动图自动播放", animatedMode.name,
                listOf(
                    GalleryAnimatedMode.WIFI_ONLY.name to "仅 Wi-Fi（不计费网络才解动画）",
                    GalleryAnimatedMode.ALWAYS.name to "始终",
                    GalleryAnimatedMode.NEVER.name to "从不（动图停在首帧）",
                ),
                { prefs.setGalleryAnimated(GalleryAnimatedMode.valueOf(it)) },
                summary = "只管**大图页**那一张：墙上的卡片一律静帧 —— 一屏动图同时解动画" +
                    "就是今天那条闪退读数的形状（256 MB 堆只剩 52 MB）。" +
                    "「不计费」按系统的网络能力判，拿不到读数时按计费处理（宁可不动，不偷跑流量）；" +
                    "这一屏只在**打开时问一次**，中途切网络不会让眼前的图忽动忽静。",
            )
            SettingsSelect(
                "大图页背景", backdrop.name,
                listOf(
                    GalleryViewerBackdrop.GLASS.name to "现状（模糊背后那面墙 + 压暗）",
                    GalleryViewerBackdrop.BLACK.name to "纯黑",
                    GalleryViewerBackdrop.DARK_GRAY.name to "深灰",
                    GalleryViewerBackdrop.WHITE.name to "纯白",
                ),
                { prefs.setGalleryBackdrop(GalleryViewerBackdrop.valueOf(it)) },
                summary = "没有「跟随图片主色调」那一档：仓库里做过一次，真机否了" +
                    "（深色主题下头部变成一块边缘清晰的紫色矩形），依赖也已经回退。" +
                    "选不透明那三档时背景**立刻**盖住窗口模糊；改回「现状」要重进大图页才恢复模糊" +
                    "（窗口那条 flag 是页面起来时挂的）。",
            )
        }

        SettingsGroup("内容与屏蔽") {
            SettingsToggle(
                "屏蔽 AI 生成的条目", blockAi, { prefs.setGalleryBlockAi(it) },
                summary = "画廊**自己这一把**，与「屏蔽与过滤」里那枚漫画侧的 AI 开关各走各的" +
                    "（用户 2026-09-29 拍板：画廊和漫画分开）。命中的判据是站方打的标签" +
                    "（`ai-generated` 与 `ai_generated` 两种写法都认），" +
                    "不是「看着像 AI」那种主观判据 —— 没有标签就不算。" +
                    "裸标签 `ai` **不算**：yande.re 上那 19 条是角色名（《Artery Gear》的 AI），" +
                    "把它算进来就是把 19 张手工插画判成 AI 画。",
            )
            SettingsToggle(
                "给 AI 条目摆角标", aiBadge, { prefs.setGalleryAiBadge(it) },
                summary = "卡片左上角一枚「AI」。屏蔽开着时它没什么可摆的（那些条目根本不上屏），" +
                    "真正的用途只有一件：屏蔽关掉、图照旧上屏时，让人看得出来哪张是 AI 画的。",
            )
            SettingsAction(
                "分级遮罩与屏蔽规则在「屏蔽与过滤」里改",
                "那两样仍是漫画与画廊**共用**的一把判据，不在这里另摆一份开关 —— " +
                    "两处各设一遍迟早会不一致，出现「墙上被挡掉、搜索结果里全裸」那种分叉。" +
                    "（AI 这一把是例外：它按上面的决定分家了，但**词表仍是同一份**。）",
            )
        }
    }
}
