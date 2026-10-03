package com.venera.compose.data.api

/**
 * Venera 的**业务 API**：UI（屏、组件、ViewModel）能问出口的那几颗契约的聚合体。
 *
 * ## 为什么要这一颗
 *
 * 审计（`docs/rounds/responsibility-dependency-audit-2026-10-03.md` §二 第 8 行）记的是
 * 「UI 直连基础设施」，实测规模是 **150 处 `*.getInstance` / 51 颗文件**加 **125 行实现类 import**：
 * 屏文件抓 `ContentGuardManager`（23 处）、`VeneraPreferences`（25 处）、`ComicSourceManager`（16 处），
 * 也抓 `HistoryDao`、`DownloadManager`、七颗 booru 站方客户端，甚至 `HostCircuitBreaker` 这种
 * 进程级 `object`。这带来两类各自独立的病：
 *
 * - **病因①「实现类的名字出现在消费层」** —— 于是 UI 编译期依赖一颗 852 行的 DAO 管理器、
 *   依赖它今天的成员表。治法：改引窄契约。
 * - **病因②「对象来自进程全局单例」** —— 治法只有构造注入。本仓 `ViewModelProvider.Factory` 零先例，
 *   且 17 处 `viewModel()` 真调用点里 7 处落在冻结屏内（**这句原先写 18 处，是错的**：那次 grep 的 21 条命中里
 *   有 4 条是注释字样，包括这颗文件自己 —— 实数与逐文件核对见方案 §九 第 2 条）。
 *
 * **本层治①**，并把②变成"能一眼列出依赖"的下一次机械替换：改完之后一颗 ViewModel 的外部
 * 依赖就是它字段声明里那几颗契约类型，不再藏在各个方法体的 `getInstance` 里。
 * 那一次替换已于 2026-10-03 排进 **D3**，走的是「主构造吃契约 + 保留 `constructor(app)` 委托」这条
 * 不动 17 处调用点的形状（方案 §九）。
 *
 * ## 三条形状上的硬约束（都是仓内已验证过的，不是偏好）
 *
 * 1. **`of(handle: Any?)` 的句柄类型只能是 `Any?`** —— 抄 `data/db/LocalFavoritesManager.kt:836`。
 *    本层不许出现 `android.content.Context`：句柄原样交给平台装的 factory 去解释。
 * 2. **`object` 里不存 Context**。装配只装「怎么从调用点递来的句柄造出口」这段 lambda；
 *    若改成 `install(app)` 后提供无参全局口，就是**用一条新的进程级 Context 持有**去换掉
 *    150 条旧穿透，正是审计 §二 第 9 行批评的那种东西（`HostCircuitBreaker.kt:26,39`）。
 * 3. **适配器的构造不许调 `getInstance`**，只在成员方法体里调 —— 于是「谁第一次真正用到
 *    才建那颗单例」的时机与改造前逐点相同（`LocalFavoritesManager.kt:829-835` 那句
 *    「时机与改造前完全一致」的第三次落地），`VeneraApp.kt:31-38` 的 `StartupTrace`
 *    冷启动读数也不会换人付账。
 *
 * ## 为什么不与 `data/platform` / `data/db` 同树
 *
 * 那两棵树整体在 `:desktop` 的 `srcDir` 编译面里（`desktop/build.gradle.kts:21,43`），
 * 落进去就必须先让每个被引用的类型桌面可编 —— 那是把 Part B 的 W1/W2 类型搬家原地吸进本轮。
 * 而 `include` 收不回范围（同文件 `:39-42` 记过「滤错文件仍 BUILD SUCCESSFUL」的静默假绿）。
 * 本层的 `android/` 子包布局照 `data/platform/android/`，将来真要共享只需加一行 `srcDir`
 * 并按子包点名排除实现那一半（`desktop/build.gradle.kts:21-22` 那两行的写法）。
 *
 * @param contentGuard 分级遮罩与屏蔽判定（`ContentGuardApi.kt`）
 * @param guardRuleBook 用户屏蔽规则的读写口
 * @param readerPrefs / appearancePrefs / comicPrefs / networkPrefs 偏好的四份分域（`PreferencesApi.kt`）
 * @param stores 按名字取自有缓存（偏好域之外那两颗：首页推荐快照、搜索历史）
 * @param comics / sources 上游源的两颗口（`SourceApi.kt`：取内容 / 问源清单）
 * @param history 阅读历史
 * @param stats 阅读统计
 * @param network 熔断与取流客户端的运维动作
 * @param httpText 一次取正文的 HTTP 动作（治 VM 内裸 okhttp 那一处）
 */
class BusinessPorts(
    val contentGuard: ContentGuard,
    val guardRuleBook: GuardRuleBook,
    val readerPrefs: ReaderPreferences,
    val appearancePrefs: AppearancePreferences,
    val comicPrefs: ComicPreferences,
    val networkPrefs: NetworkPreferences,
    val stores: NamedStoreFactory,
    val comics: ComicContentApi,
    val sources: SourceCatalog,
    val history: ReadingHistory,
    val stats: ReadingStats,
    val favorites: FavoriteLibrary,
    val downloads: DownloadQueue,
    val localComics: LocalComicLibrary,
    val network: NetworkHygiene,
    val httpText: HttpTextFetch,
) {
    companion object {
        @Volatile
        private var factory: ((Any?) -> BusinessPorts)? = null

        @Volatile
        private var installedPlatform: String? = null

        /**
         * 装「怎么从调用点递来的句柄造业务出口」这段逻辑。
         *
         * 同一平台重复调用以先装的那份为准（幂等）；**平台标签不同再装 ⇒ 直接抛** ——
         * 静态口不会被清零，测试装了假端口会留给整个进程（这条纪律抄 `DatabasePorts.kt:77-86`）。
         */
        fun install(platform: String, factory: (Any?) -> BusinessPorts) {
            val current = installedPlatform
            if (current == null) {
                installedPlatform = platform
                this.factory = factory
            } else if (current != platform) {
                throw IllegalStateException(
                    "业务 API 已由平台「$current」接线，拒绝平台「$platform」重复安装（静态口不会清零）"
                )
            }
        }

        /**
         * 取业务出口。未接线 ⇒ **抛**并说清谁该在什么时候装；不许返回 null 让上层拿空实现继续跑
         * ——「拿不到收藏库」与「收藏库是空的」是两件事，让前者冒充后者就是静默交错。
         */
        fun of(handle: Any?): BusinessPorts {
            val f = factory
                ?: throw IllegalStateException(
                    "业务 API 尚未完成平台接线（of 取用时无任何已装 factory）：请在启动处调用 " +
                        "AndroidBusinessPorts.install()。当前收到的句柄：${handle?.javaClass?.name ?: "null"}"
                )
            return f(handle)
        }
    }
}
