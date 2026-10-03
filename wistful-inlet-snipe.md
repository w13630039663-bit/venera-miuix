# Windows 桌面端图库首页 · 分批实施方案

## Context

设计稿 `docs/designs/windows-gallery-home-touhou-2026-10-03.html` 已定稿（画板 A 图库首页 + 画板 B 内联详情态，装饰三档，漫画块整块不做）。现在要回答的是"开工怎么排"。

三份取证把问题的重心挪了位置：**真瓶颈不是 UI，是取数层今天不在桌面编译面上。**

- `:desktop:app` 是 `desktop/build.gradle.kts:177` 的一个 JavaExec **任务**，不是模块；`settings.gradle.kts:27-30` 只注册 `:app` / `:desktop` / `:engine-probe`。
- `desktop/src/main` 对 `com.venera.compose` 的 import 全集只有 17 个符号，全在 `data/db` 与 `data/platform` —— **零 Composable、零 ViewModel**。桌面今天那一屏是 `VeneraDesktop.kt:386-515` 里三个手写分支。
- 图库取数 26 颗 `gallery/data` 里 21 颗带 android import，而它们的公共脐带是同一颗 `VeneraNetworkClient.getInstance(appContext).okHttpClient`（实测 11 颗文件命中，其中 10 颗只用 `.okHttpClient`，唯一例外 `GallerySaver.kt:75,86` 用的是 `downloadBytes`）。
- 稿上 pane 六项里四项今天不存在（「最新」「排行榜」只是搜索的排序档；图库无浏览历史；无已下载列表）。

预期结果：桌面端拿到一份**读数不撒谎**的图库首页 —— 三站真取数、缺席必说明、每个可见控件都能指到一处真实现或一条显式原因。

## 已锁定的前提（本轮拍板，不再重开）

1. **搬不写**：`gallery/domain` + `gallery/data` 纳入桌面 srcDir，Android 依赖换成仓库里已存在的平台件，不在桌面侧重写第二份三站接口。
2. **pane 六项全留**，缺的四项显式标「未实现」。**这是对既有口径「未实现灰行连标题全撤」（2026-09-30，Android 设置页）的一次定点豁免**，Android 侧一字不动；豁免的前提是"禁用 + 缺席原因 + 变可点判据"三条同时成立，任一条破了豁免即失效（S2 落进 `FREEZE-STATEMENT.md` 豁免记录节）。
3. **桌面实底，不承诺材质**：S0-6 已判负（系统 Mica/Acrylic 拿不到）。视觉走 Fluent 深色 surface 阶梯 #202020/#272727/#2C2C2C/#353535。
4. **先不接入 Pixiv**。
5. **Gelbooru 进首屏**，无凭据时把 401 的原因说出来，不静默少一站。
6. **物理与逻辑分离（2026-10-03 补，覆盖本文件其余一切写法）**：Android 端 **UI 代码零改动**；桌面 UI 壳独立编写，只经共享的 `gallery/data` 或 `GalleryPorts` 契约取数；桌面端严禁 `import android.*`；两端 UI 严禁互相耦合。
   - 本文件里的"UI 代码"取守卫同一口径：`feature/`、`gallery/ui/`、`reader/`、`components/` + `MainActivity.kt`（`BusinessApiBoundaryTest.kt:37-39,261`）。
   - 这条**推翻了本计划早先的两处写法**：① 原 S1 要改 `GalleryDailyScreen.kt:81` 的 `GalleryFeedSource.getInstance(context)`；② 原 S2 要往 `components/WideScreenPolicy.kt` 加 `railWidth()`。两处均已改为"Android 侧零 diff"的解法，见 S1 与 S2 里标 ⟵ 约束 6 的段落。

## 约束 6 的可行性读数（决定解法形状，我实测过）

`gallery/ui` 里**没有任何一处**直接调 client 或 store 的 `getInstance`。UI 只直接碰两颗实现类：

- `GalleryFeedSource.getInstance(context)` —— `GalleryDailyScreen.kt:81`、`GalleryScreen.kt:221`（跨包：`gallery.ui` → `gallery.domain`）
- `GalleryImageLoader.get(context)` —— 6 处（`GalleryFavoritesBody.kt:98`、`GalleryDailyScreen.kt:82`、`GalleryArtistProfileScreen.kt:138`、`GalleryScreen.kt:223`、`GalleryReverseResults.kt:74`、`GalleryPostScreen.kt:186`、`GalleryReverseSearchArea.kt:147`），而 `GalleryImageLoader` **本来就在桌面排除名单里** ⇒ 一行不用动

推论：10 颗 client + 5 颗 store + 2 颗 account 的 `getInstance(context)` 调用方，**除了下面点名的两处之外全在同包或同文件内**，所以"把 Context 工厂搬到同包的 `gallery/data/android/` 文件"不需要改任何调用点 —— 同包声明无需 import。

我把全 `app/src/main/java/com/venera/compose` 的调用点扫了一遍（`grep` 命中 16 处），跨包的只有两处，且**都不是 UI**：

- `gallery/domain/GalleryFeedSource.kt:73-75` 调三颗 client —— 但按下面的 S1 解法，这三行整段搬进新的 `GalleryDailyFeed` 并改走 `boards` 契约，**调用点消失**，不需要 import。
- `sync/BackupManager.kt:106,107` 调 `GalleryArtistFollowsStore.getInstance(context)` 与 `GalleryFavoritesStore.getInstance(context)` —— 跨包，需要补一行 `import com.venera.compose.gallery.data.getInstance`。`sync/` 不在 UI 口径内（也不在桌面编译面，`desktop/build.gradle.kts:31` 已写明它因 cacheDir/ZipFile/logcat 被排除）。若这一行的 `getInstance` 扩展与 `BackupManager` 里已有的同名扩展撞歧义，就把搬出去的那颗改名 `forContext(context)`，代价是 `GalleryBusinessApi.kt` 里对应适配器多改几行（那颗文件本来就要整体搬）。

⚠️ **但"全案 Android 侧只有一行 diff"这句是错的**，桌面压测（见下面专节）又量出两处非 UI diff：`gallery/domain/GalleryLegGuard.kt:52,71` 的默认参数指向，与 `gallery/data/GalleryArtistAvatarStore.kt:86,114` 的 `android.util.Log`。三处都不碰 UI，但排期得按三处算。

其余 12 处调用点全在 `gallery/data/GalleryBusinessApi.kt`（同包，且该文件的 `Android*` 适配器整颗要搬进 `gallery/data/android/`）⇒ 零 import、零改动。

## 承重事实（我逐条读码核过，方案站在这上面）

- **`exclude` 对整个 source set 生效，不是对单条 srcDir** —— `desktop/build.gradle.kts:39-42` 原文写明了这条，并说 `include("**/SchemaSql.kt")` 会把本模块自己的源一并滤掉且 BUILD SUCCESSFUL（静默假绿）。两个推论都用得上：
  - ① `:22` 那颗 `exclude("android/**")` 是全局的 → 把 Android 接线挪进 `gallery/data/android/` 就**自动**出桌面编译面，不需要新增 exclude。
  - ② 反过来，**不能只共享单个文件**。所以 `components/WideScreenPolicy.kt`（我核过：只 import `androidx.compose.ui.unit.*` 与 `ui.tokens.VeneraSpacing`，本身 android-free）今天共享不到桌面 —— 要它就得连整棵 `components/` + `ui/tokens/` 一起进。本批不做，见 S2 的处置。
- **`gallery/data/GalleryBusinessApi.kt` 已经是"契约在上、`internal class AndroidXxx(context)` 在下"的形状**（契约 `:26-236`，Android 适配器 `:102-302`）→ S1 的"拆半颗"是机械搬迁，不是重新设计。
- **`GalleryStoreFactory.open(name): KeyValueStore` 已存在**（`GalleryBusinessApi.kt:95-97`）→ 桌面 KV 走这颗现成口，不新造抽象。
- **`PathProvider` 的真实成员是 `dataRoot` / `cacheRoot` / `subDir(vararg)`**（`data/platform/PathProvider.kt:7-16`，且 `subDir` 自带路径段校验）。
- **契约面上缺两枚成员，且两枚的"归属面"不一样**（设计代理原话说"已实现"是把客户端当成契约面了）：
  - `fetchDailyPopular` 只在 `YandeReClient.kt:61` ⇒ 进 `YandeReBoard`（那颗本来就是 yande.re 专属窄口）✓
  - `fetchTopScored` 只在 `GelbooruClient.kt:93` 与 `SafebooruClient.kt:70`，**`YandeReClient` 没有** ⇒ ⚠️ **不许进公共 `BoardSource`**（`GalleryBusinessApi.kt:166-178`），那会逼 `AndroidYandeReBoard` 写一颗假实现。正确形状是新立 `interface ScoredBoard : BoardSource { suspend fun fetchTopScored(): Result<List<GalleryPost>> }`，把 `GalleryBoards.gelbooru` / `.safebooru` 的声明类型（`:190-191`）收窄到它 —— 对既有 `searchPosts` 调用方源码兼容（子类型加成员，不减成员）。
  - 两枚都要补 Android 适配器转发（`AndroidGelbooruBoard` / `AndroidSafebooruBoard` / `AndroidYandeReBoard` 各一行）。
- **`gallery/domain` 里除 `GalleryFeedSource` 外都干净**：`GalleryLegGuard.guard(site) { … }` 与 `GalleryMerge.mix(pools, seed)` 都不吃 Context（domain 37 颗里只有 1 颗带 android import，就是 `GalleryFeedSource.kt:3`）⇒ 新的 `GalleryDailyFeed` 可以直接复用这两个现成件，不必重写并发与合并。
- **`GalleryFeedSource` 的 KDoc 是陈旧的**：`:18,20,23-24,26-34,38,56-57` 通篇写"两站"，而 `:72-77` 实打实是三站（yande.re / Gelbooru / Safebooru），`:86-88` 也是三站。这条与本轮无关但同批顺手改（它正是会误导下一次排期的那种文字）。
- **`GalleryMotion.animates(mode, unmetered: Boolean)`**（`domain/GalleryMotion.kt:24,27`）只收 Boolean，`GalleryConnectivity` 只在 KDoc `:21` 里被提到 → 排除 `GalleryConnectivity` 不会炸 `GalleryMotion`。
- **`data/db` 的 `GuardRuleStore` 今天已在桌面编译面**（`desktop/build.gradle.kts:26-29` 明写 4b 抽出的内核不需要新增 srcDir 就自动进）→ 桌面屏蔽判据可直连它，不必碰冻结屏 `ContentGuardManager.kt`。
- **`WideScreenPolicy` 没有 48dp 那颗**：`FoldedSideBarWidth = 72.dp`（`:57`，private）、`ExpandedSideBarWidth = 224.dp`（`:60`，private）、`sideBarWidthFor`（`:67`）、`imageWallColumnCount`（`:123`，`:130` 是 `ceil(available/200f).coerceAtLeast(3)`）、`imageWallHorizontalPadding() = VeneraSpacing.screenHorizontal * 2`（`:121`）、`isWideScreen` 已 `@Deprecated`（`:147`）。

## 批次

### S1 脐带：让 `gallery/data` 脱离 Android 类型

> ✅ **S1 已落地（2026-10-03）**，读数、四条坑、三件"方案里写了但故意留给 S2"的事，
> 以及新增的两条桌面守卫，都结在仓库里那份
> `docs/rounds/windows-gallery-home-s1-2026-10-03.md`。下面这一段留作决策记录，不再更新。

**目标**：三站客户端与五颗 store 只靠现成平台件 + 一颗新共享口 `HttpEngine` 就能编过桌面。

新建：
- `app/.../data/platform/HttpEngine.kt` —— `interface HttpEngine { val okHttpClient: OkHttpClient }`。只这一枚成员就够（实测 10 颗消费方只用 `.okHttpClient`；用 `downloadBytes` 的 `GallerySaver` 本来就在排除名单里，**不要为它放宽接口**）。
- `app/.../data/platform/android/AndroidHttpEngine.kt` —— 转调 `VeneraNetworkClient.getInstance(context)`。放 `android/` 子包即被全局 `exclude("android/**")` 免费挡在桌面外。
- `desktop/.../platform/DesktopHttpEngine.kt` —— 自建 `OkHttpClient`，cache 落 `PathProvider.cacheRoot`；保留 UA / `HostCircuitBreaker` / `RateLimitingInterceptor` 三条 android-free 判据；**不接** Cloudflare 交互式过盾，缺席要说一句。
- `desktop/src/test/.../DesktopSharedFaceLedgerTest.kt`、`DesktopNoAndroidImportTest.kt`（判据见下）。

修改（全部在 `app/.../gallery/data/` 与 `gallery/domain/`，**`gallery/ui` 一行不动**）：
- 10 颗客户端：类体 `VeneraNetworkClient.getInstance(appContext).okHttpClient` → 构造吃的 `HttpEngine`；类上的 `private constructor(context: Context)` → `internal constructor(engine: HttpEngine, …)`。
- **⟵ 约束 6 的关键一步**：把每颗的 `fun getInstance(context: Context)` 从伴生对象里**搬出去**，落成同包扩展 `fun XxxClient.Companion.getInstance(context: Context): XxxClient = getInstance(AndroidHttpEngine(context))`，放新建的 `app/.../gallery/data/android/ClientsAndroid.kt`，**包名仍写 `com.venera.compose.gallery.data`**（Kotlin 允许包名不等于目录名）。这样 `exclude("android/**")` 把它挡在桌面外，而 `gallery/data` 内所有既有调用点（`GalleryBusinessApi.kt` 的 12 颗 `Android*` 适配器 + 客户端彼此）因同包而**无需 import、无需改一行**。
  - ⚠️ 这条是整个 S1 的承重点，落地后要立刻用 `git diff --name-only` 验：`feature/`、`gallery/ui/`、`reader/`、`components/`、`MainActivity.kt` 命中数必须为 0。我已把全 `app/src/main` 的调用点扫净（见上节），跨包只有 `sync/BackupManager.kt` 一处非 UI；若实施时冒出新的跨包 UI 调用方，**改用"那颗 client 维持原样 + 进 exclude 清单"**，不许为了少一颗 exclude 去动 UI。
- 五颗 store（`GalleryFavoritesStore` / `GalleryArtistFollowsStore` / `GalleryArtistAvatarStore` / `GalleryFeedCache` / `GalleryForYouCache`）与两颗 account（`GelbooruAccount` / `SauceNaoAccount`）：同一手法 —— 类体 `File(context.filesDir, …)` → `PathProvider.dataRoot`、`AndroidKeyValueStore(ctx, NAME)` → 构造吃 `KeyValueStore`（桌面 = `data/platform/JsonKeyValueStore.kt:27`），`getInstance(context)` 搬成同包扩展。
- `YandeReClient.kt` 与 `GalleryTagCategories.kt` 顶部的 `android.util.LruCache` → 就地 `LinkedHashMap(accessOrder = true)`。
- `GalleryPorts.kt` 与 `GalleryBusinessApi.kt` 各拆两半：容器 + 契约留原目录（桌面可编），`AndroidGallery*` / `Android*` 适配器整体搬进新建的 `gallery/data/android/`。
- **⟵ 约束 6：`GalleryFeedSource.kt` 原样不动**（它是唯一被 `gallery/ui` 跨包直接调的 domain 实现类，改它就等于改 UI）。改为**新抽一颗 android-free 的编排件** `gallery/domain/GalleryDailyFeed.kt`：`class GalleryDailyFeed(private val boards: GalleryBoards)` + `suspend fun loadDaily(seed: Long): Result<Daily>`，把 `GalleryFeedSource.kt:70-93` 的三腿并发、`PER_SITE_TIMEOUT_MS`、`failures` 语义与 `pools` 出参**整段搬过来**（搬完 `GalleryFeedSource` 退化成 Android 门面：`getInstance(context)` 里用 `GalleryPorts.of(context).boards` 造一颗 `GalleryDailyFeed` 并转发）。桌面直接 `GalleryDailyFeed(boards).loadDaily(seed)`，不碰那颗 Android 门面。
  - 于是 `GalleryFeedSource.kt` 进桌面 exclude 清单，理由串写"Android 门面，唯一存在理由是给 `gallery/ui` 那 2 处 `getInstance(context)` 保持零 diff"。
  - **搬之前先 grep `PER_SITE_TIMEOUT_MS` 与 `yesterdayString` 的全部读点**（`:49` 自陈"日榜、推荐翻页与搜索两腿念的是同一个数"）：凡跨文件的读点都要跟着改指 `GalleryDailyFeed`，否则就是两个预算值各活一半。
  - 契约面缺口同时补上：`fetchDailyPopular` 进 `YandeReBoard`、`fetchTopScored` 进 `BoardSource`，并补 `Android*Board` 三枚适配器转发（实测这两枚只住在 client 上，`GalleryBusinessApi.kt:166-185` 的契约里没有）。
- 同批修 `GalleryFeedSource` 的"两站"陈旧 KDoc（`:18,20,23-24,26-34,38,56-57` 通篇两站，而 `:72-77` 实为三站）—— 搬进 `GalleryDailyFeed` 的那份注释按三站重写，门面那颗只留一句"Android 门面，语义见 `GalleryDailyFeed`"。

判据：`:desktop:compileKotlin` BUILD SUCCESSFUL（**注意 `:desktop:compileDebugKotlin` 这个任务名不存在**）；`:app:testDebugUnitTest` 807 用例全绿；`:app:assembleDebug` 过；两颗新桌面用例绿；**约束 6 的机器核对**：`git diff --name-only` 在 `feature/`、`gallery/ui/`、`reader/`、`components/`、`MainActivity.kt` 五处命中数 **= 0**（命令见「验证」节）。
风险与回滚：Android 侧 10 颗客户端是行为敏感面 → 回滚 = `git revert` 整批，桌面侧因 srcDir 未动而不受影响。
守卫影响：**零白名单变更**。我原话写的是"`gallery/ui` 调 `XxxClient.getInstance` 的条目会随签名变更失效"—— 压测后作废：实测 `gallery/ui` 里**没有任何一处**调 client/store 的 `getInstance`（见「约束 6 的可行性读数」），所以 `BASELINE_GET_INSTANCE` / `SITE_TOTAL_GET_INSTANCE = 33` 一条都不动。S1 改的是 `gallery/data` 内部的接线，那不在守卫的 UI 扫描面上。

#### S1 逻辑压测（桌面复核，2026-10-03）

七条，前四条是"照原计划动刀会当场卡住"的：

1. **exclude 清单不能由 `^import android` 推出来。** `GalleryImageLoader.kt` 只 import 了一颗 `android.content.Context`（看起来"换个平台件就上桌面"），但它真正上不了桌面的是间接吃 `VeneraPreferences` + `data/network/VeneraImageFetcher`。→ 我原先给 `DesktopSharedFaceLedgerTest` 写的判据（"`^import android` 集合 == exclude 集合"）**本身是错的**：它会一边把 `GalleryImageLoader` 误判成"该进却没进"，一边对 `data.prefs` 这类间接绑定视而不见。判据已改成"禁引集合"= `^import android` ∪ `data.prefs` ∪ `data.network` ∪ `androidx.` ∪ `coil`。
2. **`fetchTopScored` 不能进公共 `BoardSource`。** 实测只有 `GelbooruClient.kt:93` / `SafebooruClient.kt:70` 有，`YandeReClient` 只有 `fetchDailyPopular`（`:61`）。→ 改立 `ScoredBoard : BoardSource`，把 `GalleryBoards.gelbooru` / `.safebooru` 的声明类型收窄（详见「承重事实」）。
3. **`PER_SITE_TIMEOUT_MS` 与 `yesterdayString` 是个死结，只能"搬 + 留转发"。** 读点实测：`GalleryLegGuard.kt:52,71` 拿它做**默认参数**、`GalleryForYouViewModel.kt:415` 注释引用、`GalleryFeedBudgetTest.kt:25,28,32,43,53` 五处、`GalleryLegGuardTest.kt:81` 注释。死结在于 `GalleryDailyFeed`（要上桌面）不能引用 `GalleryFeedSource`（要进 exclude）。→ 常量与 `yesterdayString` 落到 `GalleryDailyFeed`；`GalleryLegGuard:52,71` 的默认参数改指 `GalleryDailyFeed`（domain→domain，合法）；`GalleryFeedSource` 侧留转发 —— `const val` 不能转发，改成 `val PER_SITE_TIMEOUT_MS: Long get() = GalleryDailyFeed.PER_SITE_TIMEOUT_MS`，测试里 `GalleryFeedSource.PER_SITE_TIMEOUT_MS > 0` 照样绿；`yesterdayString` 本来就是 fun，转发无障碍，`GalleryFeedDateTest` 五处零改动。**净 Android diff：`GalleryLegGuard.kt` 两行。**
4. **`GalleryPorts` 是 12 个必填构造参数，桌面要一次给满 12 颗。** 好消息：容器 + `install(platform, factory: (Any?) -> GalleryPorts)` + `of(handle: Any?)` 的形状**已经是桌面就绪的**（`GalleryPorts.kt:42-68`，文件自陈"这颗文件里出现的只有 `Context` 一句平台类型"，而 Context 只被同文件的 Android 工厂用到 → 搬进 `android/` 后它自然进编译面，**无需 exclude**）。坏消息：S2 原先只列了 `DesktopGalleryContentGuard`，**漏了 `DesktopGalleryPreferences`（12 枚读成员）**；而 `lexicon` / `reverse` 这些"桌面今天做不了"的口也必须以**存在的对象**形态进构造（返回显式失败，不是 null）。→ 桌面侧实现类的量级是 12 颗（多数是几行的显式失败件），S2 的排期按这个数算。
5. **`Daily` 嵌套类没人点名，可以整颗搬。** 实测 `GalleryFeedSource.Daily` 与 `.Daily` 在全 `app/src` 树零命中（UI 靠类型推导用 `daily.merged` / `.failures`）⇒ 跟着 `loadDaily` 一起迁进 `GalleryDailyFeed`，UI 侧零 diff。**这条是"能不能不碰 UI"的决定性前提，成立。**
6. **`GalleryArtistAvatarStore` 有第二个 Android 面：`android.util.Log`（`:86`、`:114` 两处 `Log.w`）。** 处置表原先只算了 filesDir。本仓**没有** android-free 日志口（按 `interface Log*` / `LogcatWriter` / `VeneraLog` 三种命名搜过，零命中）。这两条 warn 报的是"头像地址档没写成"，删掉就成了本仓最忌的静默交错。→ 二选一，我倾向前者：加一颗与 `HttpEngine` 同形状的 `data/platform/Logger.kt`（Android 实现落 `data/platform/android/`），只这一颗 store 用它；或者把这颗 store 整颗 exclude —— 但那样桌面「正在关注的画师」栏的头像档就没了。
7. **21 颗带 android import 的真实成因分布**（逐颗数过，别再当"18 颗换个 Context 就行"）：只带 `Context` 的 18 颗 ⇒ 换平台件即可；`YandeReClient` 与 `GalleryTagCategories` 各多一颗 `android.util.LruCache` ⇒ 就地换 `LinkedHashMap(accessOrder=true)`；`GalleryArtistAvatarStore` 多 `Log.w` ×2 ⇒ 见第 6 条。另有 `GallerySaver`（5 枚 android import，MediaStore）与 `GalleryConnectivity`（3 枚，ConnectivityManager）整颗 exclude。

### S2 编译面 + 节注册表：pane 六项上屏

**目标**：桌面首页有两级导航骨架与"节的唯一事实源"，六项全在，缺的四项按下面的形状呈现。

新建：
- `desktop/.../gallery/DesktopGalleryPorts.kt` —— `GalleryPorts.install("desktop") { handle -> ... }`（`GalleryPorts.kt:49-59`：平台标签不同直接抛，与 `BusinessPorts` 同一条纪律），句柄 `Any?` 收 `DesktopGalleryHandle(paths, engine, storeFactory, contentGuard)`；`lexicon` 三枚词典成员给"词典未随包分发"的显式失败，`fetchTagCategories` 走真网络给真数；`reverse` 给"这一路本机没接"。
- **⟵ 压测第 4 条补的件**：`desktop/.../gallery/DesktopGalleryPreferences.kt` —— `GalleryPreferences` 那 12 枚读成员（`GalleryPorts.kt:110`，实测 12 枚 `StateFlow`：columnMode / previewQuality / keepScreenOn / volumeKeyTurn / autoPlaySec / preload / animated / backdrop / blockAi / aiBadge / hideTopBar / hideBottomBar，`:113-137`）的桌面实现，值取"默认 + `JsonKeyValueStore` 覆盖"。**`GalleryPorts` 的 12 个构造参数全是必填**，所以桌面侧一次要交满 12 颗实现件（多数只有几行、内容是显式失败），S2 的工作量按这个数算，不是按"能真跑的那几颗"算。
  - 里面有一颗要当场拍死：`galleryAnimated` 与 `GalleryMotion.animates(mode, unmetered)` 的 `unmetered` —— 供体 `GalleryConnectivity` 在桌面被 exclude，而 `unmetered` 硬编码 `false` 等于"桌面永远不动图"、硬编码 `true` 等于"永远动"。**桌面走有线/Wi-Fi 同等对待，取 `true`**，并在桌面侧写一句为什么（不许留一个看起来能配其实恒真的开关）。另外 `galleryHideTopBar` / `galleryHideBottomBar` 两枚在桌面语义上无对应物（桌面没底栏，P5 已拍），桌面实现直接给 `false` 常量并注明"此枚在桌面档不参与"。
- `desktop/.../gallery/DesktopGalleryContentGuard.kt` —— 直连已在编译面上的 `data/db/GuardRuleStore.kt`，不碰冻结的 `ContentGuardManager.kt`。
- `app/.../gallery/DesktopGalleryHomeSections.kt` —— key 常量 + `ORDER` 六项 + `when(key)` **不留 else**，照 `gallery/ui/GalleryHomeSections.kt:73-97 / :482-522` 的形状。
- `desktop/.../gallery/DesktopGalleryMetrics.kt` —— rail 48 与 pane 224 的桌面唯一出处。
- `desktop/.../gallery/DesktopUnimplementedRow.kt`。
- 测试：`DesktopGalleryHomeSectionsTest.kt`、`DesktopWidthCaliberDriftTest.kt`。

修改：
- `desktop/build.gradle.kts` —— 新增两条 srcDir（`gallery/data`、`gallery/domain`）+ 点名 exclude：`GallerySaver.kt`、`GalleryTagDictionary.kt`、`GalleryConnectivity.kt`、`GalleryImageLoader.kt`，**再加 `GalleryFeedSource.kt`**（Android 门面，约束 6 逼出来的那颗；理由串按上节写）。S1 搬家后 `GalleryPorts.kt` / `GalleryBusinessApi.kt` 应**无需** exclude。
- `VeneraDesktop.kt:386-515` —— 三个手写分支改注册表驱动。不动 `:207` 的 1080×760 窗口档，不动系统 title bar。
- **⟵ 约束 6：`components/WideScreenPolicy.kt` 一行不改**（原计划要往里加 `fun railWidth(): Dp = 48.dp`，那是动 Android 侧 UI 基建，撤掉）。rail 48 与 pane 224 全落在桌面自己的 `DesktopGalleryMetrics.kt`，靠 `DesktopWidthCaliberDriftTest` **只读** `components/WideScreenPolicy.kt` 的文本来核对 224 / 72 / 每列 200 有没有漂。
  - 口径账要算清：Android 没有 rail，所以 rail 48 **没有可漂的对岸**，只是桌面自持的一颗数；真会漂的只有 pane 224（`WideScreenPolicy.kt:60` 的 `ExpandedSideBarWidth`，private）与每列 200（`:130` 表达式里的除数），这两处由用例钉。
  - 代价写明：桌面与 Android 各持一份 224，是**受机器看管的重复**，不是自由重复。等第二颗 UI 件真要共享时，再拍"要不要给 `components/` + `ui/tokens/` 开整目录 srcDir"（`WideScreenPolicy.kt` 本身 android-free，只 import `androidx.compose.ui.unit.*` 与 `ui.tokens.VeneraSpacing`，卡点纯粹是 KGP 不能只共享单文件）。
- 列数按 `imageWallColumnCount` 的表达式算，**不许照稿抄 5**：默认 1080 窗口下推算为 4 列（1080 − rail 48 − pane 224 − `imageWallHorizontalPadding()`，再 `ceil(/200)`），S2 用扫描用例钉死这个数。

判据：`:desktop:compileKotlin` 绿；`DesktopGalleryHomeSectionsTest` 断言 `ORDER` 恰含六 key、次序钉死、**项数恒 6 不跟数据变**、每 key 二选一（有内容件 XOR `reason` 非空）；`DesktopWidthCaliberDriftTest` 绿；手跑 `:desktop:app --shot` 出图，pane 六行齐、四行带「未实现」。
风险与回滚：`VeneraDesktop.kt` 是唯一入口 → 回滚 = 还原 `:386-515`，S1 产物不受影响。
冻结影响：不碰 8 颗冻结文件；且按约束 6，`gallery/ui` 整棵（含 `GalleryDailyScreen.kt`、`GalleryScreen.kt`）**一行都不进 diff** —— 它们既非冻结也在本批的禁改面上，S2 的判据要两条都报。「未实现」四项的形状规范见后面专节。

### S3 真内容到货：日榜墙 + 收藏 + 图

新建 `desktop/.../gallery/DesktopGalleryWall.kt`（照 `gallery/ui/GalleryScreen.kt:1266 GalleryCardsGrid(columnCount, header, sections)` 的形状**在桌面侧自持一颗** —— `gallery/ui` 不进桌面编译面，别指望 import）、`DesktopCoilWiring.kt`、`DesktopGalleryFooter.kt`；测试 `DesktopWallColumnTest.kt`、`DesktopFailureReadoutTest.kt`。
修改 `desktop/build.gradle.kts`：+ coil3（`libs.versions.toml:18` = 3.6.2，已是多平台栈）；`coil-network-okhttp` 与 `data/network/VeneraImageFetcher.kt` **都不进桌面**，桌面走 coil3 自带网络件与自带磁盘缓存，**不留第二条缓存账**；referer 由 `ImageHeaderPolicy` 提供。
判据：`--shot` 截图里封面是像素不是"无图"；`loadDaily` 的 `failures.keys` 与 footer 渲染的缺席站集合**逐字相等**（`DesktopFailureReadoutTest` 对账）；动图未验 ⇒ 首帧 + 角标，禁"看起来像坏图"。

### S4 Gelbooru 凭据与"缺席必须明说"

新建 `desktop/.../gallery/DesktopGelbooruCredentialPanel.kt`（写侧只落 `KeyValueStore`，`GelbooruAccount` 已吃它）、`desktop/src/test/.../DesktopAbsentSitesTest.kt`。
判据：清凭据跑一轮 ⇒ `failures[GELBOORU]` 非空且 footer 有"DAPI 匿名一律 401，需 `api_key` + `user_id`"这句；带凭据 ⇒ 三站均有货；桌面翻页处对 `GelbooruClient.kt:131` 的 **pid 0 基**口径断言一次。
红线：不许从浏览器 cookie 库取凭据（已拍板不做）。

### S5 稿件回改 + README 双语

按 S1–S4 实测读数回勾稿上标记，跑 `_qa/readme-sync.mjs`。**同批修掉一处已存在的文档矛盾**：`README.en.md:219` 写着 "This is an Android-only project … not started"，而桌面代码已存在；中文侧同四行（`README.md:199,214,292,381`）一起改。
判据：`node _qa/readme-sync.mjs` 不 throw；稿内每条数字能 `grep -n` 到代码行号，无锚数字为 0。

## 「未实现」四项的形状规范（S2 的实现判据）

- **呈现**：沿用稿上已有的 `.nav.off` + `.lock` 形状。文字 `#8B8B8B`、图标 `#5E5E5E`、行尾一枚 10.5px 药丸写「未实现」，完整原因写在**同一行下方 caption**（12px、`maxLines=2`），句子里必须点名缺席的那颗件，不许写"敬请期待"。
- **整行 `selected = false`、`onClick = null`**；若 Fluent `menuItem` 给不出 disabled，就在 onClick 里只做"展开原因"这一件真事 —— 绝不 no-op。
- **计数徽标一律删**（`1.2k` / `286` / `2` / `3,412`）：没有 store 就没有数。
- **变可点的判据**（写成用例条件，句式统一为 `reason != null` 与 `contentProvider == null` 恒等）：
  - 历史：存在 `gallery/data/GalleryHistoryStore.kt`（或 `data/db` 里一张 gallery 历史表）**且** 注册表有 `HISTORY` 且有内容件 —— 三者同时。
  - 下载：`GallerySaver` 落点从 MediaStore 改为 `PathProvider` 目录 **且** 存在一颗枚举读侧。
  - 最新：`BoardSource.searchPosts` 有可用排序参数且三站中 ≥2 站真返回。
  - 排行榜：`GalleryBoards` 上有跨站统一取榜成员 **且** "yande.re 固定 40 条、忽略分页"这条天花板在 UI 上被说出来（否则它点亮那一刻就是"看起来还有更多"的假读数）。
- **豁免怎么记**：`FREEZE-STATEMENT.md` 尾部「冻结豁免记录」新增一条，标题点名"桌面首页 pane「未实现」行（用户点名豁免）"，正文写上面三条前提 + 顶栏仍走"页内自治"（`:25`）+ Tab 枚举/路由/齿轮（`:32`）未触。同批在 `docs/rounds/windows-gallery-home-2026-10.md` 留四条判据原文与用例名。

## Pixiv 缺席的替代

- 稿上「热门画师」→ **「正在关注的画师」**（`domain/GalleryFollowedArtists.rowOf(follows, favorites)`，卡面次序 = 真头像 > 名下最近收藏 > 首字母座）。仓库里**从来没有热门画师榜**，硬造就是假读数。空名单时写"还没有关注画师 · 关注是本地动作（`GalleryArtistFollowsStore.kt:36-42` 不与站方发生关注）"，不写"0 位热门"。
- 「热门标签」的 `12.4 万` 归因今天不成立（`BoardSource` 没有 count 成员）→ 改挂 `lexicon.fetchTagCategories(pageUrl)` 的**当页**分类计数并写明"当页"。
- `PixivClient` 两颗端点失去消费方后：契约成员**不删**、Android 适配器不动；桌面 `DesktopGalleryArtistDirectory` 对 `pixivAvatarUrl` / `pixivArtworkAuthor` 返回 `Result.failure(PixivNotWiredOnDesktop)`，**不是 `Result.success(null)`**。头像链照 `GalleryArtistProbeClient.kt:41-43` 走 fanbox / Mastodon 两路，最后才首字母座，卡底 caption 写"Pixiv 未接入，本行不代表该画师没有头像"。
- 本仓红线在这里的具体形式：**"没取到"（failure/超时）与"没有"（站方确实零条）与"没接"（本机无此端点）是三句话**。配套加一条纯 JVM 用例断言 `rowOf` 对 `failure` 与 `success(null)` 产出不同 caption（那颗是纯函数，不需要 Robolectric）。

## 桌面侧要补的机器判据

`desktop/src` 不被守卫扫描（`BusinessApiBoundaryTest.kt:19-21` 如实声明只看 `app/src/main/java`），所以这一侧的穿透今天**没有机器兜底**。补六颗：

| 用例 | 判据 | 红在什么场景 |
|---|---|---|
| `DesktopSharedFaceLedgerTest` | 扫 `gallery/{data,domain}/*.kt`（`gallery/data/android/` 除外）得"命中**禁引集合**的文件集"，与**解析 `desktop/build.gradle.kts` 文本**得到的 exclude 集合做双向差集，必须为空；每条 exclude 带 ≥20 字理由串。⚠️ 禁引集合**不等于 `^import android`** —— 必须是 `^import android` ∪ `data.prefs` ∪ `data.network` ∪ `androidx.` ∪ `coil`，理由见下面压测第 1 条 | ①共享面文件加了不可解析依赖却没登记；②已经解绑却留着 exclude（过期账本）；③exclude 名字写错字面（今天会静默假绿） |
| `DesktopNoAndroidImportTest` | `desktop/src/main/**/*.kt` 零 `^import android`、零 `data.prefs`、零 `VeneraNetworkClient` | 桌面上图时顺手抄 Android 接线 |
| `DesktopUiIsolationTest` | **双向**：`desktop/src/main` 零 `com.venera.compose.(feature\|gallery\.ui\|reader\|components)` import；`app/src/main` 零 `com.venera.desktop` import | 两端 UI 互相耦合（约束 6 的后半句）。桌面要哪块 UI 就在自己壳里写一颗，或把纯判据件下沉到 `gallery/domain`，不许跨端引 Composable |
| `DesktopGalleryHomeSectionsTest` | 见 S2 | 新增 pane 忘登记；假 pane；数据到货才插项（锚定漂移的桌面版） |
| `DesktopWidthCaliberDriftTest` | 读 `components/WideScreenPolicy.kt` 文本核 `224.dp` / `72.dp` / 每列 `200`，并 grep 桌面源里这三个字面量各 ≤1 处 | 桌面自造宽度；或 Android 改了口径桌面不知道 |
| `DesktopFailureReadoutTest` | `loadDaily` 的 `failures.keys` 与 footer 渲染的缺席站集合逐字相等 | 静默少一站 |

防"第二份账本"只有一条做法：**真相来自文件系统与注册表本身，gradle 配置与文案是被核对的一方**。绝不新建一份手写的"桌面共享文件清单"。

## 稿件回改清单（行号按现稿）

| 处 | 现稿 | 改成 |
|---|---|---|
| 305 / 479 cap-line | 「1440×900 … 净宽 1120 → 5 列」 | 保留公式，另注桌面默认窗 1080×760（`VeneraDesktop.kt:207`）⇒ 4 列；rail 48 是 `WideScreenPolicy` 新增的一颗 |
| 312 / 486 | 「34 源 · 2 个任务」 | 「三站 · 本地无任务队列」（漫画侧 33 源与追更 Worker 都不在桌面：`FollowUpdatesWorker.kt` 已 exclude） |
| 313 / 488 | 头像 `title="已登录 Pixiv"` | 删，换本机数据目录指示（`DesktopPaths`） |
| 335-340 / 509-514 | pane 六项无状态 | 推荐✅、收藏✅（本地 JSON）；最新/排行榜/历史/下载 四项 `.nav.off` + 「未实现」+ caption，删全部计数 |
| 341 / 515 | 以图搜源「桌面不可用」 | 保留，补两句原因（选图器 `OpenableColumns`/`GetContent()` + 交互式过盾 Activity） |
| 344-346 / 517-519 | 「画师 418」「标签 9,077」「图库源」 | 画师 = 本机关注条数；标签 → 未实现（词典 sqlite 不随包）；图库源 → "三站固定，无源管理界面" |
| 347-351 / 521 | 「Pixiv 已登录 · 会员」「缓存 2.4 GB / 上限 5 GB」 | 删 Pixiv 行；Gelbooru 行改写为"DAPI 匿名一律 401 ⇒ 无凭据则这一站一张图都取不到"；缓存行 → "缓存上限未接（`VeneraPreferences` 不在桌面编译面）" |
| 356-357 | 「六源并行取数」 | 「三站并行 · 每站 12s 预算」（`GalleryFeedSource.kt:105`）；seg 后两档标未实现 |
| 371-382 hero | 「1 / 8」+ 8 个 pips | 真源是 `loadDaily(seed)`：刻意无缓存无翻页、换一批 = 只换种子本地重排不再联网（`:59-61` 拍板）；pips 改「重排」不写页码；hero 标出每站"热门"口径的差别（`:26-34`：yande.re 真日榜 / Gelbooru·Safebooru 全站高分池） |
| 389-392 / 395-401 | 「热门画师 · 作品 1.2 万 · Pixiv」「本地已存 214」「热门标签 12.4 万」 | 见上两节 |
| 406-456 | 作品卡 `src=Pixiv` | 站点标只能三选一；作品名保留但注明是虚构占位 |
| 459-467 | 「最近浏览」节 | 无历史 store ⇒ 整节标未实现；"节的数量不跟数据变"这条在桌面注册表同样成立 |
| 470-472 status | 「六源 · 5 正常 · 1 需登录」「代理 127.0.0.1:7890」「缓存 2.4 GB」 | 「三站 · 2 正常 · 1 需凭据」+ `failures` 原句；代理与缓存删；「本次取数 00:41 前」保留 |
| 604-609 详情 | `来源 Pixiv`、`本地状态：已缓存缩略 · 原图未下载` | 站点改三站；本地状态 → 「未读（`GalleryImageLoader` 不在桌面编译面）」 |
| 643 / 645 / 674 | LIQUID_GLASS 桌面档、装饰第四轴、模糊纹理内存测算 | 桌面**恒 SOLID**：`LocalSurfaceMaterial` 照常 provide、值钉 SOLID，`veneraGlassEnabled()` 仍是唯一读取口，材质轴**不联动**另两轴（`SurfaceMaterialPolicy.kt:85-88`）；同批撤 `libs.miuix.blur` / `backdrop` / `kyant.shapes` 三行依赖（`desktop/build.gradle.kts:96-98`，S0-7 探针使命已结束）；第四轴三档未实现就标未实现；674 从"待量"改"不做" |
| 675 / 686-687 | GIF、1280 挤列 | 保留；686 改成实测：≤1280 时 rail/pane 不缩、列数 4→3，由 `DesktopWallColumnTest` 钉住 |

## 批次顺序的判据

- **S1 必须先走**：`gallery/data` 上不了编译面，S2 的「推荐」pane 也只能标未实现 —— 首屏六项里五项灰，直接违背前提 2。
- **S2 先于 S3**：内容件必须落在注册表的 `when(key)` 上；先画墙再补注册表 = 骨架重排一次，而骨架的判据用例（项数恒在）会被重写。
- **不能并行的两对**：① S2 与 S3 争同一颗 `VeneraDesktop.kt` 与同一份节注册表；② S3 与 S4 争 `DesktopGalleryFooter` / `failures` 文案（`DesktopFailureReadoutTest` 的对账会互相打红）。S5 只能最后 —— 它的判据是"稿与代码一致"，代码没定稿就没有靶。
- **只能做两批就停：停在 S1 + S2。** 那一站点的完整态是"能取到真数、能说清真缺席"：三站取数在桌面上编得过并跑得出读数，六项 pane 有判据与原因，宽度口径与冻结豁免都留了痕，没有一张可能渲染错位的图，也没有一条假计数。S3 的图是观感增益，S1+S2 是读数不撒谎 —— 后者才是本仓的地板。

## 验证（每批都要跑，不接受"看起来对"）

```bash
# 约束 6 的核对：Android UI 侧必须零 diff（每批都跑，S1/S2 尤其）
git diff --name-only | grep -E 'app/src/main/java/com/venera/compose/(feature/|gallery/ui/|reader/|components/|MainActivity\.kt)' | wc -l   # 必须输出 0

./gradlew :desktop:compileKotlin        # 任务名不是 compileDebugKotlin
./gradlew :desktop:test                 # 现有 20 颗类 + 本方案新增 5 颗
./gradlew :app:testDebugUnitTest        # 807 用例基线
./gradlew :app:assembleDebug            # Android 侧行为面未破
./gradlew :desktop:app --shot           # 出图，人眼核对 pane 六项与「未实现」标记
node _qa/readme-sync.mjs                # S5：不 throw 即中英两四行同步
```

⚠️ gradle 那几条**不要接管道**（`| tail`、`| grep`）—— 本仓记过两次"退出码被管道吞掉、失败看着像成功"。要截输出就先跑完再看退出码，或落文件后再读。

桌面无 CI（`.github/` 不存在），所以 `:desktop:compileKotlin` 每批必须手跑。
装机核对：本轮全部改动不进 8 颗冻结文件，Android 侧真机回归只需在结案时跑一次冷启动进首页/搜索/详情/历史各一次。

## 本方案不含

跨端数据自动迁移（桌面当全新数据，已拍板）、i18n、多窗口、系统级材质、从浏览器 cookie 库取凭据、桌面复用 `feature/Navigation.kt`（P5 已拍板桌面走侧栏）、把 `components/` + `ui/tokens/` 开进桌面 srcDir（等第二颗 UI 件要共享时再拍）。
