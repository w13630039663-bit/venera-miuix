package com.venera.compose.feature

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.navigation.NavHostController
import com.venera.compose.MainActivity
import com.venera.compose.SettingsSubActivity
import com.venera.compose.data.api.BusinessPorts
import com.venera.compose.data.prefs.VeneraPreferences
import com.venera.compose.feature.settings.AppearanceSettings
import com.venera.compose.feature.settings.AppSettings
import com.venera.compose.feature.settings.BlockingRulesSettings
import com.venera.compose.feature.settings.BlockingSettings
import com.venera.compose.feature.settings.ExploreSettings
import com.venera.compose.feature.settings.GallerySettings
import com.venera.compose.feature.settings.LocalFavoritesSettings
import com.venera.compose.feature.settings.NetworkSettings
import com.venera.compose.feature.settings.PreferredIpSpeedTestScreen
import com.venera.compose.feature.settings.ReaderSettings
import com.venera.compose.feature.sourcemanage.ComicSourceScreen
import com.venera.compose.feature.favoriteimages.FavoriteImageItem
import com.venera.compose.feature.favoriteimages.toComicItem
import com.venera.compose.reader.ReaderSession

/**
 * 设置子树里三个「越界」出口的载荷。
 *
 * 这三个目标页（阅读器 / 漫画详情 / 标签搜索）仍只在 MainActivity 的导航图上，
 * 而 SettingsSubActivity 是另一个 Activity —— 见 [SettingsEscapeHandoff] 为什么不能走 intent。
 */
sealed interface SettingsEscape {
    data class ReadLocal(val session: ReaderSession) : SettingsEscape

    /** 详情页普通打开。 */
    data class OpenComic(val comic: ComicItem) : SettingsEscape

    /**
     * 设置首页 → 阅读历史。
     *
     * 历史 2026-09-23 从主 Tab 降回二级页后，它只挂在 MainActivity 的图上，
     * 而设置主页是另一个 Activity —— 所以这条必须走越界交接，不能在设置侧 navigate。
     */
    data object OpenHistory : SettingsEscape

    /** 详情页打开并立刻读到某一页（插图收藏长按）：一条记录同时给出漫画身份与页码。 */
    data class ReadPage(val image: FavoriteImageItem) : SettingsEscape

    /** 插图收藏 → 预览页。设置那侧不飞：跨 Activity，共享元素接不上（见 consumeSettingsEscape）。 */
    data class PreviewPage(val image: FavoriteImageItem) : SettingsEscape

    data class DigTag(
        val namespace: String,
        val raw: String,
        val label: String,
    ) : SettingsEscape
}

/**
 * 越界出口的一次性进程内交接槽。
 *
 * 不用 intent extras：ReaderSession 含非序列化对象（仓库里既有结论见
 * Navigation.kt「阅读会话含非序列化对象，交给宿主 ViewModel 暂存」那条注释），
 * ComicItem / TagStatBucket 也不在路由参数里，parcel 传不过去。
 * MainActivity 消费后必须立刻置空，否则旋屏重建会把同一页再推一次。
 */
object SettingsEscapeHandoff {
    /** 同一帧不可能触发两个出口，后来者覆盖前者即可。 */
    var pending: SettingsEscape? = null
}

/**
 * 设置子树的屏名。
 *
 * 走 intent extra 而不是 NavHost 目的地：每一屏必须**真的换一个 Activity**，
 * 系统才会给它施加 AOSP 跨 activity 预测式返回动画（缩放 / 圆角 / 遮罩全是系统原值）。
 * 留在同一个 NavHost 或同一个页面内部栈里，系统都不认为是换页 —— 那正是
 * `SettingsHome` 原先自绘的 `PredictiveBackStack` 观感生硬、也拿不到模糊的原因。
 *
 * [BLOCKING_RULES] 需要额外参数（规则类型），见 [EXTRA_ARG]。
 */
enum class SettingsSubScreen {
    // 设置首页下的 8 个分区（原 PredictiveBackStack 内部栈的 entries）
    EXPLORE,
    GALLERY,
    BLOCKING,
    BLOCKING_RULES,
    READER,
    APPEARANCE,
    LOCAL_FAVORITES,
    APP,
    NETWORK,

    // 分区内部再往下走的叶子页
    LINE_SPEEDTEST,
    SOURCE_MANAGE,
    DOWNLOADS,
    LOCAL_COMICS,
    STATS,
    FAVORITE_IMAGES,
    SYNC,
    LOGS,
}

/** 从上一级打开一个设置子页（每次都是新 Activity，返回落回来源页）。 */
fun Context.openSettingsSubScreen(screen: SettingsSubScreen, arg: String? = null) {
    startActivity(
        Intent(this, SettingsSubActivity::class.java)
            .putExtra(SettingsSubActivity.EXTRA_SCREEN, screen.name)
            .apply { if (arg != null) putExtra(SettingsSubActivity.EXTRA_ARG, arg) },
    )
}

/**
 * 设置子页的统一内容：按屏名分发。
 *
 * 分区 composable 自带顶栏与返回箭头，所以这里只负责三件事：给它 onBack（=finish）、
 * 给它往下跳的回调（=再开一个子 Activity）、以及底部 navigationBars inset
 * （与外壳同一份几何：顶部不加 padding，让顶栏覆盖状态栏）。
 */
@Composable
fun VeneraSettingsSubHost(screen: SettingsSubScreen, arg: String?) {
    val context = LocalContext.current
    val view = LocalView.current
    // ⚠️ 只有画廊那一页还抓实现类：它是唯一需要 setGalleryXxx 写口的消费点，而画廊侧契约按
    // 口径是 getter-only（gallery/data/GalleryPorts.kt 的 GalleryPreferences）。解锁条件见
    // docs/rounds/business-api-boundary-2026-10.md §六（与 Part B 的 W5 同批）。
    val prefs = remember(context) { VeneraPreferences.getInstance(context) }
    val readerPrefs = remember(context) { BusinessPorts.of(context).readerPrefs }
    val appearancePrefs = remember(context) { BusinessPorts.of(context).appearancePrefs }
    val comicPrefs = remember(context) { BusinessPorts.of(context).comicPrefs }
    val networkPrefs = remember(context) { BusinessPorts.of(context).networkPrefs }

    fun haptic() = view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)

    fun back() {
        haptic()
        (context as? Activity)?.finish()
    }

    fun open(next: SettingsSubScreen, nextArg: String? = null) {
        haptic()
        context.openSettingsSubScreen(next, nextArg)
    }

    /**
     * 交回 MainActivity 的图。默认 launchMode 会新建实例压在本页之上，
     * 从阅读器 / 详情 / 标签搜索返回时仍落回这个子页 —— 返回语义没丢，
     * 代价是那三个出口会重建一次外壳（首页数据要重拉）。
     */
    fun escape(escape: SettingsEscape) {
        haptic()
        SettingsEscapeHandoff.pending = escape
        context.startActivity(Intent(context, MainActivity::class.java))
    }

    // 这些页面 composable 本身不吃 modifier 参数，所以外面包一层 Box 消费底部 inset，
    // 与之前挂在 NavHost 上的 padding 等价。
    Box(
        Modifier
            .fillMaxSize()
            .padding(
                bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
            )
    ) {
        when (screen) {
            SettingsSubScreen.EXPLORE -> ExploreSettings(
                onBack = ::back,
                onSources = { open(SettingsSubScreen.SOURCE_MANAGE) },
                onKeywords = { open(SettingsSubScreen.BLOCKING_RULES, "KEYWORD") },
            )
            SettingsSubScreen.GALLERY -> GallerySettings(prefs = prefs, onBack = ::back)
            SettingsSubScreen.BLOCKING -> BlockingSettings(
                onBack = ::back,
                onRules = { open(SettingsSubScreen.BLOCKING_RULES, it) },
            )
            SettingsSubScreen.BLOCKING_RULES -> BlockingRulesSettings(
                // 缺 arg 只会来自编码错误，交给 Activity 侧的 error() 拦，这里不再兜底。
                type = requireNotNull(arg) { "BLOCKING_RULES 需要规则类型" },
                onBack = ::back,
            )
            SettingsSubScreen.READER -> ReaderSettings(
                prefs = readerPrefs,
                onBack = ::back,
                onImages = { open(SettingsSubScreen.FAVORITE_IMAGES) },
                onStats = { open(SettingsSubScreen.STATS) },
            )
            SettingsSubScreen.APPEARANCE -> AppearanceSettings(prefs = appearancePrefs, onBack = ::back)
            SettingsSubScreen.LOCAL_FAVORITES -> LocalFavoritesSettings(prefs = comicPrefs, onBack = ::back)
            SettingsSubScreen.APP -> AppSettings(
                prefs = networkPrefs,
                onBack = ::back,
                onSync = { open(SettingsSubScreen.SYNC) },
                onLogs = { open(SettingsSubScreen.LOGS) },
                onDownloads = { open(SettingsSubScreen.DOWNLOADS) },
                onLocalComics = { open(SettingsSubScreen.LOCAL_COMICS) },
            )
            SettingsSubScreen.NETWORK -> NetworkSettings(
                prefs = networkPrefs,
                onBack = ::back,
                onSpeedTest = { open(SettingsSubScreen.LINE_SPEEDTEST) },
            )
            SettingsSubScreen.LINE_SPEEDTEST -> PreferredIpSpeedTestScreen(prefs = networkPrefs, onBack = ::back)

            SettingsSubScreen.SOURCE_MANAGE -> ComicSourceScreen(onNavigateBack = ::back)
            SettingsSubScreen.DOWNLOADS -> DownloadScreen(
                onBack = ::back,
                onNavigateToLocalLibrary = { open(SettingsSubScreen.LOCAL_COMICS) },
            )
            SettingsSubScreen.LOCAL_COMICS -> LocalComicScreen(
                onBack = ::back,
                onOpenLocalSession = { session -> escape(SettingsEscape.ReadLocal(session)) },
                onNavigateToDownloads = { open(SettingsSubScreen.DOWNLOADS) },
                onOpenComicDetail = { item -> escape(SettingsEscape.OpenComic(item)) },
            )
            SettingsSubScreen.STATS -> StatsScreen(
                onBack = ::back,
                // 与 MainActivity 侧同口径：带的是该桶的源生原值，不是中文显示名，且不锁源。
                onDigTag = { bucket ->
                    escape(
                        SettingsEscape.DigTag(bucket.searchNamespace, bucket.searchRaw, bucket.display)
                    )
                },
            )
            SettingsSubScreen.FAVORITE_IMAGES -> FavoriteImagesScreen(
                onBack = ::back,
                onPreviewImage = { item -> escape(SettingsEscape.PreviewPage(item)) },
                onOpenComicDetail = { item -> escape(SettingsEscape.OpenComic(item.toComicItem())) },
                onReadFromPage = { item -> escape(SettingsEscape.ReadPage(item)) },
            )
            SettingsSubScreen.SYNC -> SyncBackupScreen(onBack = ::back)
            SettingsSubScreen.LOGS -> LogViewerScreen(onBack = ::back)
        }
    }
}

/**
 * 设置主页的宿主。跑在 [com.venera.compose.SettingsActivity] 里。
 *
 * 不再需要 NavHost，也不再需要页面内部栈：7 个分区与各分区下的叶子页全部走
 * [openSettingsSubScreen] 跨 Activity，系统与 MainActivity 那侧的自绘转场就此分开、互不冒充。
 */
@Composable
fun VeneraSettingsHost() {
    val context = LocalContext.current
    val view = LocalView.current

    AndroidSettingsScreen(
        onBack = {
            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            (context as? Activity)?.finish()
        },
        // 历史页只挂在 MainActivity 的图上，这里 navigate 不到 —— 与阅读器/详情那两个出口
        // 同一条路：填越界交接槽 + 拉起 MainActivity（消费即清见 consumeSettingsEscape）。
        onOpenHistory = {
            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            SettingsEscapeHandoff.pending = SettingsEscape.OpenHistory
            context.startActivity(Intent(context, MainActivity::class.java))
        },
    )
}

/** 供 [MainActivity] 消费越界出口：取走即清，避免重建时重复推同一页。 */
internal fun NavHostController.consumeSettingsEscape(shell: VeneraShellViewModel) {
    val escape = SettingsEscapeHandoff.pending ?: return
    SettingsEscapeHandoff.pending = null
    when (escape) {
        is SettingsEscape.ReadLocal -> {
            shell.pendingSession = escape.session
            navigate(ReaderRoute)
        }
        is SettingsEscape.OpenComic -> {
            shell.selectedComic = escape.comic
            navigate(DetailRoute(comicId = escape.comic.id, sourceName = escape.comic.sourceName))
        }
        is SettingsEscape.ReadPage -> {
            // 与 MainActivity 侧 readFavoriteImage 同一套动作：标题/作者先由收藏记录顶上，
            // 「读到哪一章哪一页」交给详情页在目录就绪后找回。
            shell.selectedComic = escape.image.toComicItem()
            shell.pendingReadTarget = ReadTarget(escape.image)
            navigate(
                DetailRoute(
                    comicId = escape.image.comicId,
                    sourceName = escape.image.sourceName,
                )
            )
        }
        is SettingsEscape.PreviewPage -> {
            // 与 MainActivity 侧 openFavoriteImagePreview 同一套动作。差别只在：这条是从
            // 另一个 Activity 交回来的，收藏页那张卡不在当前导航栈里，共享元素接不上 ——
            // 预览页照常打开，只是不飞。
            shell.favoriteImagePayloads[escape.image.id] = escape.image
            navigate(FavoriteImageRoute(itemId = escape.image.id))
        }
        is SettingsEscape.OpenHistory -> navigate(HistoryRoute)
        is SettingsEscape.DigTag -> navigate(
            TagSearchRoute(
                keyword = "",
                tagNamespace = escape.namespace,
                tagRaw = escape.raw,
                tagLabel = escape.label,
            )
        )
    }
}
