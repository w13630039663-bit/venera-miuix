package com.venera.compose.components

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * 共享 UI 面上**唯一允许存在的平台服务出口**（写法 A，2026-10-20）。
 *
 * ## 为什么需要它
 *
 * `components/` 那 48 颗是大屏 UI 的核心层（侧栏、顶栏、卡片、琥珀光、玻璃）。
 * 桌面要复用它们，整层就得进 `:desktop` 编译面。可其中 5 颗各有一处平台调用：
 *
 * | 文件 | 那一处 | 平台服务 |
 * |---|---|---|
 * | `VeneraFloatingNavBar.kt:167` | `view.performHapticFeedback(KEYBOARD_TAP)` | 触觉反馈 |
 * | `backdrop/Coroutines.kt:14` | `Choreographer.getInstance().postFrameCallback` | 帧钟 |
 * | `ComicCardContextMenu.kt:102` | `Toast.makeText(...)` | 吐司 |
 * | `ComicListPresentation.kt:28,57` | `SharedPreferences.OnSharedPreferenceChangeListener` | 偏好监听 |
 * | `PredictiveBack.kt:53` | `PredictiveBackHandler` | 预测性返回 |
 *
 * 逐颗改写成 `expect/actual` 不成立：`:app` 是普通 Android 模块，不是 KMP，没有 `expect`。
 * 而"为这一层单独建一颗 KMP 模块"会把 `:app` 的依赖图改掉 —— 那正是主人明令不许碰的。
 *
 * 所以走注入：共享面只认这枚接口，**平台实现由集成方在入口挂上去**。
 * 这是写法 A 的形状 —— 安卓侧的实现逐字保留（`AndroidUxServices`），桌面侧另给一份
 * （`DesktopUxServices`），共享面一行 Android import 都没有。
 *
 * ## 为什么默认值是"什么都不做"
 *
 * 默认实现必须让**没挂 provider 的调用方**（预览、离屏测试、`@Preview`）照常跑：
 * 触觉震动打不出来、吐司弹不出来，但布局与状态机一切照旧。反过来若默认抛异常，
 * 每张预览图都会炸 —— 那是把"没接平台"这件事变成了硬失败，而它本来只是静默降级。
 *
 * ⚠️ 别让它长成第二个 `BusinessPorts`：这里只放**纯 UI 副作用**（震一下、提示一句、
 * 等一帧）。任何带业务语义的东西（读偏好、发请求）都不许进来。
 */
interface UxPlatformServices {

    /** 一次轻触觉反馈。Android 侧是 `KEYBOARD_TAP`，桌面侧无操作。 */
    fun hapticTap()

    /** 短吐司。Android 侧是 `Toast.makeText(.., LENGTH_SHORT)`，桌面侧无操作。 */
    fun toast(message: String)

    /** 等下一帧 —— 回弹动画慢一拍启动用。Android 侧走 `Choreographer`。 */
    suspend fun awaitNextFrame()

    /**
     * 系统偏好键变化订阅（目前唯一消费方是 `ComicListPresentation`）。
     *
     * @return 取消订阅。
     */
    fun observePreferenceChange(key: String?, onChange: () -> Unit): () -> Unit

    /**
     * 是否支持「预测性返回手势」。桌面没有这颗手势，恒 `false`。
     *
     * 调用点据此决定挂不挂 `PredictiveBackHandler` —— 挂一颗永远不触发的 handler
     * 在桌面侧没有意义，而"挂不上"必须是一条**显式的读数**，不是靠编译不过表达。
     */
    val supportsPredictiveBack: Boolean

    /**
     * 漫画列表的展示模式（列表 / 网格）持久化读写 + 变更订阅。
     *
     * 为什么在接口上而不是留在 `ComicListPreferences`：那颗类整颗吃
     * `android.content.Context` + `SharedPreferences`（`data/prefs` 的已知 Android 面），
     * 而调用它的 `rememberComicListDisplayMode` 住在 `components/`。写法 A 的落点就是这里 ——
     * **平台侧提供实现，共享面只认接口**。
     *
     * @return 当前值；订阅成功后每次变更回调 [onChange]（回调里自己重读 [readComicDisplayMode]）。
     */
    fun readComicDisplayMode(): String

    /** 写入展示模式（`normalizeComicDisplayMode` 的归一由调用点做，这里只存）。 */
    fun writeComicDisplayMode(value: String)

    /** 订阅展示模式变更。@return 取消订阅。 */
    fun observeComicDisplayMode(onChange: () -> Unit): () -> Unit

    /**
     * 缓存里的漫画源指标（赞数 / 评分）—— 与展示模式同一族的第二个口子。
     *
     * 返回**非空**快照（字段各自可空），与 `ComicMetricsCache.get(..)` 的形状逐字对应：
     * 那一颗方法本身恒返回一个对象（没有读数时里面两个字段是 `null`），
     * 所以这里也恒返回对象 —— 若返回 `null`，"没缓存"与"缓存了空读数"就分不开了，
     * 而调用点（`FavoritesScreen` / `FollowUpdatesScreen`）直接读字段，多一层判空纯属噪音。
     */
    fun cachedComicMetrics(sourceKey: String, comicId: String): CachedComicMetricsSnapshot

    /** 订阅指标缓存变更。@return 取消订阅。 */
    fun observeComicMetrics(onChange: () -> Unit): () -> Unit

    /**
     * 漫画卡片长按菜单的三件业务动作 —— 收藏 / 屏蔽 / 剪贴板。
     *
     * 为什么这些也要走接口：`ComicCardContextMenu` 原实现是 `BusinessPorts.of(context)`。
     * `BusinessPorts` 本身住在中性的 `data/api`，但 `of(context)` 吃
     * `android.content.Context` —— 那一句就把整颗文件钉在了 Android 上，而它在 `components/`。
     *
     * 三个动作由平台侧实现（Android 侧转发给 `BusinessPorts`，逐字保留原有语义），
     * 共享面只声明"要什么"。
     *
     * ⚠️ 两枚业务动作是 **suspend**，不是普通函数：原实现跑在 `rememberCoroutineScope().launch { }`
     * 里，改成阻塞式调用会卡住 UI 线程。接口若声明成普通函数，实现方就只能用
     * `runBlocking` 硬扛 —— 那是把"异步"偷偷改成"同步"。形状必须跟着语义走。
     */
    suspend fun saveComicToFavorites(
        id: String,
        title: String,
        author: String,
        sourceKey: String,
        tags: List<String>,
        coverPath: String,
        folder: String,
    ): FavoriteSaveResult

    /** 屏蔽本作（按 COMIC_ID 等值）。@return 是否写入成功。 */
    suspend fun blockComic(comicId: String): Boolean

    /** 复制一段文本到剪贴板。 */
    fun copyToClipboard(text: String)

    /**
     * 用系统浏览器打开一个网址。
     *
     * 原调用点（`gallery/ui` 与 `feature/` 共 55 处）写的是
     * `context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(...))` ——
     * 那是 Android 的 Activity 跳转，桌面侧没有 `Intent` 与 `startActivity`。
     *
     * @return 是否真的打开了（Android 侧没有可处理该 URL 的 Activity 时为 false，
     *   原实现是抛 `ActivityNotFoundException`；这里改成返回 false，让调用点自己决定要不要吐司）。
     */
    fun openUrl(url: String): Boolean

    /**
     * 分享一段图片字节（系统分享面板）。
     *
     * 原调用点 `gallery/ui/GalleryImageShare.kt` 走 `Intent.ACTION_SEND` +
     * `FileProvider` 授权；桌面侧的对应物是系统文件管理器/剪贴板，由平台实现决定。
     *
     * @param fileName 建议的文件名（含扩展名），平台据此决定落盘临时文件的名字。
     */
    suspend fun shareImage(fileName: String, bytes: ByteArray): Boolean

    /** 跳到本应用的某个设置子屏（`feature/settings` 那边的二级页）。 */
    fun openSettingsSubScreen(subKey: String)

    fun copyImageToClipboard(bytes: ByteArray): Boolean
    /**
     * 封面要不要打码（内容守卫的那一条判定链）。
     *
     * 原调用点是 `HomeScreen.kt` 的两处
     * `ContentGuardManager.getInstance(LocalContext.current).coverMaskStateFor(…)` ——
     * 那是 Android 侧的静态单例 + `Context`，桌面侧没有。
     *
     * 返回三档串：`"VISIBLE"` / `"BLURRED"` / `"HIDDEN"`。
     * ⚠️ 用串而不是自己造枚举：这一个值是**判据的产物**，`ContentGuardManager`
     * 与 `GalleryContentGuard` 两岸都用同一批字面量，造枚举等于开第二份口径。
     *
     * @param sourceKey 源身份（历史记录那条路给的是显示名，守卫内部会做别名解析）。
     */
/**
     * 给这一块区域向系统申请「手势排除区」（Android 12+ 的 `systemGestureExclusionRects`）。
     *
     * 原调用点是 `HomeScreen.kt` 的 `Modifier.systemGestureExclusionBand()`：全面屏手势下，
     * 屏幕左右边缘的横拖被判成「返回」，轮播贴边就永远滑不动（真机症状：想抽卡却退出了页面）。
     *
     * @param left/top/right/bottom 单位是**根坐标系的 px**（与 `LayoutCoordinates.positionInRoot` 同一系）。
     *   不用 `android.graphics.Rect` 是刻意的：那是 Android 类型，进不了共享面。
     * @param rects 一次交一批会更省（系统每变一次就重算），所以签名收 `List`。
     *   交 `emptyList()` = 撤销这一块的所有排除区。
     */
    fun setGestureExclusionRects(rects: List<FloatArray>)

    fun coverMaskStateOf(
        sourceKey: String,
        title: String,
        author: String = "",
        tags: List<String> = emptyList(),
        comicId: String = "",
        description: String = "",
    ): String

    /**
     * 本平台的图片加载器（Coil3 `ImageLoader`）。
     *
     * 原调用点 `CoverHeroBackdrop.kt:145` 写的是 `context.imageLoader`（Coil 的 `Context` 扩展属性），
     * 那是 Android 侧的全局单例取法。桌面侧的 Coil3 单例走的是另一个入口，
     * 所以"取哪一颗实例"必须由平台报上来。
     *
     * ⚠️ 类型用 `coil3.ImageLoader`（Coil3 自己就是 KMP 库，两端都有）而不是包一层自己的接口：
     * 需要平台化的只是**取实例**，不是图片加载本身。包一层只会让调用点多写一次转换。
     *
     * @return `null` 表示"平台没配过" —— 调用点退到 Coil 的默认单例（原行为）。
     */
    fun imageLoader(): coil3.ImageLoader?
}

/**
 * 收藏那一颗的**结果三档** —— 与 `ComicCardContextMenu` 里原来那句 `when` 逐档对应。
 *
 * 为什么不用 `Boolean?`：原实现刻意区分了三种去向（成功 / 已在里面 / 失败），
 * 因为"重复收藏"与"收藏失败"对用户是两件事，用可空布尔会把它们压成一档。
 */
sealed interface FavoriteSaveResult {
    /** 已加入 [folder]。 */
    data class Added(val folder: String) : FavoriteSaveResult

    /** 本来就在 [folder] 里。 */
    data class AlreadyThere(val folder: String) : FavoriteSaveResult

    /** 失败（异常原文对用户没有可操作性，不往上抛）。 */
    data object Failed : FavoriteSaveResult
}

/**
 * 漫画指标的**平台无关快照** —— 与 `data/prefs/CachedComicMetrics` 同形，
 * 但因为那颗类住在 `data/prefs`（被 `DesktopSharedFaceLedgerTest` 的 `FORBIDDEN` 点名禁引），
 * 共享面只能认这枚自己的类型。
 */
data class CachedComicMetricsSnapshot(
    val rating: Double? = null,
    val likesCount: Int? = null,
)

/** 默认实现：一切副作用静默降级（预览 / 离屏测试 / 漏挂 provider 时用这条）。 */
object NoOpUxPlatformServices : UxPlatformServices {
    override fun hapticTap() = Unit
    override fun toast(message: String) = Unit
    override suspend fun awaitNextFrame() = Unit
    override fun observePreferenceChange(key: String?, onChange: () -> Unit): () -> Unit = {}
    override val supportsPredictiveBack: Boolean = false
    // 展示模式默认"网格"：与 `normalizeComicDisplayMode` 的默认档同值，
    // 让没接平台的预览也能画出一张像样的卡（而不是空串导致 when 落空）。
    override fun readComicDisplayMode(): String = "GRID"
    override fun writeComicDisplayMode(value: String) = Unit
    override fun observeComicDisplayMode(onChange: () -> Unit): () -> Unit = {}
    override fun cachedComicMetrics(sourceKey: String, comicId: String) = CachedComicMetricsSnapshot()
    override fun observeComicMetrics(onChange: () -> Unit): () -> Unit = {}
    override suspend fun saveComicToFavorites(
        id: String,
        title: String,
        author: String,
        sourceKey: String,
        tags: List<String>,
        coverPath: String,
        folder: String,
    ): FavoriteSaveResult = FavoriteSaveResult.Failed
    override suspend fun blockComic(comicId: String): Boolean = false
    override fun copyToClipboard(text: String) = Unit
    override fun openUrl(url: String): Boolean = false
    override suspend fun shareImage(fileName: String, bytes: ByteArray): Boolean = false
    override fun openSettingsSubScreen(subKey: String) = Unit
    override fun copyImageToClipboard(bytes: ByteArray): Boolean = false
    override fun coverMaskStateOf(
        sourceKey: String,
        title: String,
        author: String,
        tags: List<String>,
        comicId: String,
        description: String,
    ): String = "VISIBLE"
    override fun setGestureExclusionRects(rects: List<FloatArray>) = Unit
    // NoOp 拿不到平台 Context，所以这里走 Coil 的**进程级单例**入口：coil3.compose 那一侧
    // 的 AsyncImage 在 imageLoader 缺省时用的就是它。预览图因此仍能取到图，
    // 而它不依赖任何 Android/Linux 平台服务。
    override fun imageLoader(): coil3.ImageLoader? = null
}

/**
 * 平台服务的注入点。集成方在应用根部 [androidx.compose.runtime.CompositionLocalProvider] 挂上去。
 *
 * 默认 [NoOpUxPlatformServices] 而不是 `null`：调用点因此不必写 `?.` 或 `!!`，
 * 少一层可空分支就少一处"漏判"的机会。
 */
val LocalUxPlatformServices = staticCompositionLocalOf<UxPlatformServices> { NoOpUxPlatformServices }
