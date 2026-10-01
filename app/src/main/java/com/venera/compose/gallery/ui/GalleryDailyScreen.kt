package com.venera.compose.gallery.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import coil3.ImageLoader
import com.venera.compose.components.isWideScreen
import com.venera.compose.components.venera.VeneraTopAppBar
import com.venera.compose.components.venera.VeneraTopBarPill
import com.venera.compose.components.venera.rememberTopBarBackdrop
import com.venera.compose.components.venera.rememberVeneraTopAppBarBehavior
import com.venera.compose.data.network.HostCircuitBreaker
import com.venera.compose.data.prefs.VeneraPreferences
import com.venera.compose.gallery.data.GalleryImageLoader
import com.venera.compose.gallery.data.GallerySite
import com.venera.compose.gallery.domain.GalleryFeedSource
import com.venera.compose.gallery.domain.GallerySettingsModel
import com.venera.compose.gallery.domain.GalleryWallFeed
import com.venera.compose.security.guard.ContentGuardManager
import com.venera.compose.ui.tokens.VeneraTokens
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 「每日热门」的**二级页**（2026-09-30 新增，用户拍板「查看全部可以转跳二级界面查看」）。
 *
 * ## 为什么是独立路由而不是页内切一面墙
 *
 * 首页那一节从此只是**预览**（一行横卡）。全量要有一个"看得完、能返回"的地方，
 * 而页内切换会把返回语义变成页内状态机（"按返回是回预览还是退出画廊？"）——
 * 本仓在搜索态上已经吃过这个亏（`GallerySearchViewModel.contexts` 那一整套就是为它建的）。
 * 独立路由天然有返回栈、有转场、有大标题，与历史页/标签搜索页同一形状。
 *
 * ## 它复用首页那个 [GalleryViewModel]
 *
 * **不是**新开一个 `viewModel()`：那会让二级页重新联网拉一遍同一份日榜
 * （用户从首页点进来看到的是同一个池子，等两次网络毫无意义），
 * 而"换一批"在本地重排也要那份**原始池**。所以调用方按 `GalleryRoute` 的返回栈条目
 * 作 owner 取同一个实例，见 `Navigation.kt` 那个 composable（`dailyViewModel`）。
 *
 * ## 「换一批」在这儿（不在首页）
 *
 * 首页那一行是预览，换给谁看？而进了这一页的人正是"我要看全量、我要换一批"的人。
 * 它走 [GalleryViewModel.remix] —— **零请求的本地重排**（从两站的原始池里再抽一次），
 * 回 `false` 时退回真取一次（`retryFromUser` 那条路径），不让按钮按下去没反应。
 *
 * @param vm 与首页共享的那个日榜 ViewModel（由调用方取同一个实例）。
 * @param onOpenPost 打开大图页。
 * @param onBack 返回。
 */
@Composable
internal fun GalleryDailyScreen(
    vm: GalleryViewModel,
    onOpenPost: (GallerySite, Long) -> Unit,
    onBack: () -> Unit,
) {
    val tokens = VeneraTokens
    val context = LocalContext.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()

    val source = remember { GalleryFeedSource.getInstance(context) }
    val imageLoader = remember { GalleryImageLoader.get(context) }
    val guard = remember { ContentGuardManager.getInstance(context) }
    val maskMode by guard.nsfwMaskMode.collectAsState()
    val rules by guard.rules.collectAsState()
    val blockAi by VeneraPreferences.getInstance(context).galleryBlockAi.collectAsState()

    val topBarBehavior = rememberVeneraTopAppBarBehavior()
    val topBarBackdrop = rememberTopBarBackdrop()
    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val gridState = rememberLazyStaggeredGridState()

    val columnMode by VeneraPreferences.getInstance(context).galleryColumnMode.collectAsState()
    val wide = isWideScreen(LocalConfiguration.current.screenWidthDp.dp)
    val columnCount = GallerySettingsModel.gridColumns(columnMode, wide)

    // 与首页**同一把过滤判据**（`buildGalleryWall`）：同一张图在首页预览与这一页上待遇必须一致。
    val wall = remember(vm.posts, maskMode, rules, blockAi) {
        buildGalleryWall(vm.posts, maskMode, blockAi = blockAi) { post ->
            guard.findGalleryBlockedRule(author = post.author, tags = post.tagList)?.pattern
        }
    }

    /** 用户主动重来一次：清熔断（否则按下去必然还是"熔断中"，是假按钮）+ 置 force 绕过在途标志。 */
    fun refreshFromUser() {
        GallerySite.entries.forEach { HostCircuitBreaker.reset(it.apiHost) }
        vm.refresh()
        loadDailyFeed(vm, source, scope)
    }

    /** 「换一批」：先本地重排（零请求），池子不够才真取一次。 */
    fun remix() {
        if (!vm.remix()) refreshFromUser()
    }

    // 二级页也用与主 Tab 同一条**顶栏避让地板**（口径出处见冻结声明第七轮），
    // 它没有常驻 chrome（搜索条在首页那一屏），所以只需地板本身。
    val topPadding = statusBarTop + 104.dp

    // 根节点是一层 Box：内容与顶栏是**兄弟**，顶栏靠 `align(TopCenter)` 叠在内容之上。
    // 形状与 `GalleryScreen` 那一屏逐字同构（页内自治：外壳不挂 TopAppBar）。
    Box(modifier = Modifier.fillMaxSize()) {
        // ⚠️ 2026-09-30 第三轮：**下拉刷新撤了**（用户原话「取消掉整个画廊的下拉刷新」）。
        // 这一页"我要新内容"的出口仍在：顶栏那枚「换一批」（先本地重排、池子不够才真取）
        // 与错误态那枚「重试」，两条都走 [refreshFromUser]，不是另造一套取数。
        Box(
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(topBarBehavior.nestedScrollConnection),
        ) {
            GalleryDailyPage(
                hasPosts = vm.posts.isNotEmpty(),
                wall = wall,
                date = vm.date,
                sourceNotice = vm.sourceNotice,
                isLoading = vm.isLoadingFeed,
                error = vm.feedError,
                imageLoader = imageLoader,
                columnCount = columnCount,
                state = gridState,
                topPadding = topPadding,
                backdrop = topBarBackdrop,
                onOpen = { post ->
                    GalleryViewerQueue.set(wall.cards.map { it.post })
                    onOpenPost(post.site, post.id)
                },
                onRetry = { refreshFromUser() },
            )
        }

        VeneraTopAppBar(
            title = GalleryWallFeed.DAILY.label,
            largeTitle = GalleryWallFeed.DAILY.label,
            scrollBehavior = topBarBehavior,
            backdrop = topBarBackdrop,
            navigationIcon = {
                VeneraTopBarPill(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = "返回",
                        tint = tokens.color.textPrimary,
                    )
                }
            },
            actions = {
                VeneraTopBarPill(onClick = {
                    view.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                    remix()
                }) {
                    Icon(
                        imageVector = Icons.Outlined.Refresh,
                        // 它干的是"从两站原始池里再抽一批" —— 与首页那枚同一个动作、同一句读数。
                        contentDescription = "换一批",
                        tint = tokens.color.textPrimary,
                    )
                }
            },
            modifier = Modifier.align(Alignment.TopCenter),
        )
    }
}

/**
 * 取一轮日榜并落进共享的 ViewModel。
 *
 * 与首页那条 `loadDaily` **同一形状**（清错误 → 置在途 → try/finally 里清标志），
 * 但**刻意不共用**：首页那份住在 composable 的局部函数里，而它的
 * "永久转圈"防护（`NonCancellable` 清标志）是那一处最难复现的缺陷的产物 ——
 * 抽成共享件要连首页一起动，而首页现在是稳定态。这一份照抄的是**结论**，
 * 理由与那三条注释逐条对应（见 `GalleryScreen` 的 `loadDaily`）。
 *
 * 唯一的差别：二级页**不做缓存判据**（`shouldAutoLoad` 那套）—— 用户主动进了这一页，
 * 屏上是缓存的旧一天就该补一次，而不是让他看着昨天的热门以为今天没更新。
 */
private fun loadDailyFeed(
    vm: GalleryViewModel,
    source: GalleryFeedSource,
    scope: kotlinx.coroutines.CoroutineScope,
) {
    if (vm.isLoadingFeed) return
    vm.feedError = null
    vm.isLoadingFeed = true
    scope.launch {
        try {
            source.loadDaily(seed = vm.seed)
                .onSuccess { daily ->
                    vm.accept(
                        list = daily.merged.posts,
                        day = daily.date,
                        notice = daily.failures.entries
                            .joinToString(" · ") { "${it.key.displayName}：${it.value}" }
                            .takeIf { daily.failures.isNotEmpty() },
                        pools = daily.pools,
                    )
                    vm.feedLoadedKey = "feed#${vm.refreshTick}|${vm.seed}"
                }.onFailure { e ->
                    vm.feedError = e.message ?: "加载失败"
                }
        } finally {
            // 取消路径也要清：见首页那份的长注释（标志卡在 true 就是一个永久波浪环 + 假按钮）。
            withContext(NonCancellable) {
                vm.isLoadingFeed = false
            }
        }
    }
}
