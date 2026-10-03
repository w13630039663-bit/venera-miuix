# S2-A · 桌面接线与网络（一份可以独立干完的计划）

> 你是谁、在干什么：你是**同时开工的两个执行方之一（A）**，只做"把画廊业务口在桌面上接成真路"这一半。
> 另一半（B）在另一个 git worktree 里同时做桌面 UI 壳，你们**不共享任何一颗文件**。
> 上级方案：`~/.qoder-cn/plans/wistful-inlet-snipe.md` · 上一批读数：`docs/rounds/windows-gallery-home-s1-2026-10-03.md`
> 你的对岸计划：`docs/rounds/windows-gallery-home-s2b-shell-2026-10-03.md`（只读，别改它）

## 0. 硬约束（违反一条就等于这批白做）

1. **物理与逻辑分离**：Android 端 UI 代码零改动 —— `app/src/main/java/com/venera/compose/{feature,gallery/ui,reader,components}/` 与 `MainActivity.kt` **一行都不许进 diff**。
2. **行为零变更**（Android 侧）：你这批**原则上不动 `app/` 任何文件**。要动之前先停下来报告，说明为什么非动不可。
3. **严禁 `import android.*`**（桌面侧），含**内联 FQN** `android.util.Log.i(...)` 这种不带 import 的写法 —— S1 就是被这句绊过一次（见 rounds 文档坑 1）。
4. **降级路径宁可错慢，不可静默交错**：接不了的路返回 `Result.failure(明确原因对象)`，**不许**返回 `Result.success(空表)` 或 `null`。"没取到 / 没有 / 没接"是三句话，必须说得出是哪一句。
5. **凭据只落本机键值存储，不写日志、不进任何提示文案**。
6. **设备只读**：真机与模拟器由用户点页面，执行方只截图读日志，不代操作。

## 1. 你已经有的地基（S1 已落地，别重做）

- 契约与容器**已经在桌面编译面里**：`com.venera.compose.gallery.data.GalleryPorts`（12 个必填构造参数）、
  `GalleryContentGuard` / `GalleryPreferences` / `GalleryFavorites` / `GalleryArtistFollows` / `GalleryArtistAvatars` /
  `GalleryTagLexicon` / `GalleryCredentials` / `GalleryStoreFactory` / `GalleryBoards` / `GalleryArtistDirectory` /
  `GalleryReverseSearch` / `GalleryNetworkHygiene`，以及 `BoardSource` / `YandeReBoard` / `ScoredBoard`。
- 十颗三站客户端已经是 `internal constructor(engine: HttpEngine, …)` + `getInstance(engine)`（**没有** Context 版本；
  Context 版本是 `app/…/gallery/data/android/` 里的同包扩展，桌面看不到）。
- 平台口已存在：`data.platform.HttpEngine`（只有 `okHttpClient` 一枚成员）、`Logger`（`warn` / `info`）、
  `PathProvider`（`dataRoot` / `cacheRoot` / `subDir(vararg)`）、`KeyValueStore`、`JsonKeyValueStore`、`LruMap`、
  `NoInteractiveBypassTag`、`UserAgentStrings`。
- 桌面侧已有的件：`com.venera.desktop.platform.DesktopPaths`（实现 `PathProvider`）、`DesktopDatabasePorts`。
- `GalleryDailyFeed(boards)` 是 android-free 的日榜编排件，桌面直接 `GalleryDailyFeed(ports.boards).loadDaily(seed)`。

## 2. 你的交付物（全部是新文件，路径按下面这张表，一颗不许越界）

### 2.1 网络与日志（`desktop/src/main/kotlin/com/venera/desktop/platform/`）

| 文件 | 内容 | 判据 |
|---|---|---|
| `DesktopHttpEngine.kt` | `class DesktopHttpEngine(paths: PathProvider) : HttpEngine`，自建 `OkHttpClient`：`cache = Cache(paths.cacheRoot.resolve("venera_http"))`、connect/read/write 超时**逐一对齐** `data/network/VeneraNetworkClient.kt` 里那三枚数值（去读那份文件抄数，别自己定） | UA 必须带 `UserAgentStrings.DEFAULT_USER_AGENT`；构造期不建 client（`get()` 现取，与 `AndroidHttpEngine` 同一纪律） |
| `DesktopLogger.kt` | `object DesktopLogger : Logger`，两枚成员写 stdout 或本仓已有的桌面日志件 | **不许**把 message 之外的东西（URL query、请求头值）打出去 |

**必须做的那道决策（做之前先写进你的提交说明）**：Android 那条链上还挂着三条判据 —— UA 策略、
`HostCircuitBreaker`（每主机熔断）、`RateLimitingInterceptor`（429/Retry-After 退避）。它们今天全住在
`com.venera.compose.data.network`，而那颗目录是**禁引族**（`DesktopSharedFaceLedgerTest` 的 `FORBIDDEN` 里有
`^import com.venera.compose.data.network`）。两条路：
- **路 1（推荐）**：这三颗本身如果只依赖 okhttp/okio（去逐颗数过它们的 import 再判），把 android-free 的那几颗
  一起开进桌面 srcDir，桌面复用同一份判据。⚠️ 但 `desktop/build.gradle.kts` **不在你的所有权里**（见 §4），
  要走这条路就**停下来报告**，说明要点名 srcDir 哪颗、哪些 exclude 需要重判，由集成方改。
- **路 2**：桌面自研一条最小退避（429 时按 `Retry-After` 等待一次，然后仍失败就 `Result.failure`），
  并在类注释里明写"桌面没有过盾、没有熔断"。**不许**写一条看起来像有而降级成静默重试到成功的。

### 2.2 十二颗桌面实现件 + 接线（`desktop/src/main/kotlin/com/venera/desktop/gallery/data/`）

`GalleryPorts` 的 12 枚构造参数**全必填**，所以桌面要一次交满 12 颗对象 —— 包括今天做不了的：
做不了的也必须是**存在的对象**、成员返回显式失败，不是 `null`、不是抛 `NotImplementedError`（装配不能炸首页）。

| 文件 | 覆盖的契约 | 内容档位 |
|---|---|---|
| `DesktopGalleryContentGuard.kt` | `GalleryContentGuard` | 直连**已在编译面**的 `data/db/GuardRuleStore.kt`；`addRule` 走它的写侧；`nsfwMaskMode` 给桌面默认档 |
| `DesktopGalleryPreferences.kt` | `GalleryPreferences`（**12 枚读成员，一颗不许少**） | "默认值 + `JsonKeyValueStore` 覆盖"。两颗要点名：`galleryAnimated` 走 `true`（桌面按有线同等对待，别留恒假开关）；`galleryHideTopBar` / `galleryHideBottomBar` 给 `false` 常量并注明"桌面档不参与" |
| `DesktopGalleryFavorites.kt` | `GalleryFavorites` | 复用已共享的 `GalleryFavoritesStore(PathProvider)`（那颗已脱 Context） |
| `DesktopGalleryArtistFollows.kt` | `GalleryArtistFollows` | 复用 `GalleryArtistFollowsStore(PathProvider)` |
| `DesktopGalleryArtistAvatars.kt` | `GalleryArtistAvatars` | 复用 `GalleryArtistAvatarStore(PathProvider, Logger)`（用 `DesktopLogger`） |
| `DesktopGalleryTagLexicon.kt` | `GalleryTagLexicon` | 词典三枚返回显式失败："词典未随包分发"（`GalleryTagDictionary` 在排除清单里，assets 那份没进桌面）；`fetchTagCategories` 走 `GalleryTagCategories.getInstance(engine)` 真网络 |
| `DesktopGalleryCredentials.kt` | `GalleryCredentials` | Gelbooru 身份与 SauceNao key 从桌面 `KeyValueStore` 读；没配就是没配（返回空 identity / `hasKey=false`），**不许**读浏览器 cookie 库（本仓红线，已拍板不做） |
| `DesktopStoreFactory.kt` | `GalleryStoreFactory` | 交 `JsonKeyValueStore`；存储名由调用点原样递进来，**不许改名**（改名=换数据文件） |
| `DesktopGalleryBoards.kt` | `GalleryBoards`（`yandere: YandeReBoard` / `gelbooru: ScoredBoard` / `safebooru: ScoredBoard`） | 三站真网络，全部 `getInstance(engine)`；` GelbooruClient.getInstance(engine, credentials)` 吃的凭据来自 `DesktopGalleryCredentials` 那颗的存储读数 |
| `DesktopGalleryArtistDirectory.kt` | `GalleryArtistDirectory` | `danbooru*` / `probe*` 真网络；**`pixivAvatarUrl` / `pixivArtworkAuthor` 返回 `Result.failure(PixivNotWiredOnDesktop)`**，不是 `success(null)`（Pixiv 本轮明确不接） |
| `DesktopGalleryReverseSearch.kt` | `GalleryReverseSearch` | 整颗"这一路本机没接"：两枚方法返回显式失败对象，`maxResults` 仍给真数 |
| `DesktopGalleryNetworkHygiene.kt` | `GalleryNetworkHygiene` | 桌面没有 Android 那颗熔断器 ⇒ 要么接上（见 §2.1 路 1），要么这颗明确说"桌面档无熔断可清"并让 reset 成为**有据可查的 no-op**（注释里点名为什么合法） |
| `DesktopGalleryPorts.kt` | — | `object DesktopGalleryPorts { const val PLATFORM = "desktop"; fun install(); private fun createPorts(handle: Any?): GalleryPorts }`，形状逐字照 `app/…/gallery/data/android/GalleryPortsAndroid.kt`（S1 刚落的那颗）：`@Volatile` 缓存 + `synchronized`、句柄不对就抛并说清收到的是什么 |
| `DesktopGalleryHandle.kt` | — | `data class DesktopGalleryHandle(paths, engine, store, …)` —— 接线句柄的形状；`GalleryPorts.of(handle: Any?)` 收的就是它 |

⚠️ 装配纪律：**适配器构造里不调 `getInstance`**，一律 `private val x get() = X.getInstance(engine)` 在成员体里现取
（S1 从头守到尾的那条，理由：建图时机与改造前逐点相同）。

### 2.3 你的测试（`desktop/src/test/kotlin/com/venera/desktop/gallery/data/`）

JUnit4，跟仓库钉版一致，**不引新测试框架**（本仓无 Robolectric、无 Compose UI 测试）。

| 用例 | 钉住什么 |
|---|---|
| `DesktopGalleryPortsWiringTest` | ①`GalleryPorts` 十二枚成员**全部非 null** 且每枚的类型是它那颗契约；②"没接"的那几颗（reverse / lexicon 词典 / pixiv 两枚）调用后拿到的是 `isFailure == true`，**绝不许** `success(emptyList())` 或 null —— 这是本仓最忌的假读数形状 |
| `DesktopHttpEngineTest` | UA 值逐字等于 `UserAgentStrings.DEFAULT_USER_AGENT`；三枚超时数值与 Android 侧一致（把 Android 那三枚数的出处写进注释，别抄成字面量）；cache 目录落在 `PathProvider.cacheRoot` 下且不越界 |
| `DesktopGalleryPreferencesTest` | 12 枚成员都有值、默认档与 Android 侧默认档**逐枚对齐**（不一致要在注释里点名为什么可以不一致） |
| `DesktopFailureReadoutTest` | `GalleryDailyFeed` 的 `failures.keys` 语义：某站取不到 ⇒ 那一站**必须**出现在 `failures` 里，且 `pools` 给它空表而不是把整轮判成失败（这条 S3 还会复用，先立起来） |

## 3. 验证（每步都要跑，不接受"看起来对"）

```bash
./gradlew :desktop:test          # 你新增的用例 + 原有 156 条，全绿
./gradlew :desktop:compileKotlin # 任务名不是 compileDebugKotlin
git diff --name-only             # 逐行看：只允许出现 §2 里点名的那些路径
```

⚠️ **gradle 那几条不要接管道**（`| tail`、`| grep`）—— 本仓记过两次"退出码被管道吞掉、失败看着像成功"，
后台任务的完成通知也会再吞一次。先跑完再看退出码，或落文件后再读。

并发提醒：你和 B 各在一个 worktree 里跑 gradle，共享同一份 `~/.gradle` 缓存，偶发
`Timeout waiting to lock` —— 那**不是代码问题**，重跑一次即可，别去改构建脚本"绕开"。

## 4. 所有权与禁区（这就是"互不打扰"的具体形状）

**你拥有**：`desktop/src/main/kotlin/com/venera/desktop/platform/DesktopHttpEngine.kt`、`DesktopLogger.kt`；
`desktop/src/main/kotlin/com/venera/desktop/gallery/data/**`；`desktop/src/test/kotlin/com/venera/desktop/gallery/data/**`。

**你禁止碰**（碰了就会和 B 或集成冲突）：
- `desktop/build.gradle.kts` —— 冻结。srcDir 与排除清单 S1 已经落好，`okhttp` / `kotlinx-serialization` /
  kotlin-serialization 插件都已就位，你**不需要**新依赖。要走 §2.1 路 1 或确实缺依赖 ⇒ 停下来报告。
- `desktop/src/main/kotlin/com/venera/desktop/VeneraDesktop.kt` —— 归集成（`install()` 那一行由集成方加）。
- `desktop/.../gallery/ui/**`、`desktop/src/test/.../gallery/ui/**` —— B 的。
- `app/**` 全部、`docs/**` 里除你自己那份说明文档之外的任何一颗、`FREEZE-STATEMENT.md`（冻结豁免归集成方按流程记）。
- `desktop/src/test/.../DesktopSharedFaceLedgerTest.kt` / `DesktopNoAndroidImportTest.kt` /
  `testsupport/DesktopFace.kt` —— 这三颗是 S1 落的守卫，**改判式等于把尺子改了**；它们红的时候先怀疑自己的代码。

**你的提交**：按仓内风格一笔，`feat(desktop): S2-A …`，正文要含 §2.1 那条决策怎么选、为什么，以及三条 gradle 读数。
不要 `git push`，不要合并分支 —— 集成由人做。

## 5. 完工判据（对外只认这几条）

1. `:desktop:test` 绿，且新增用例数与文件名在提交说明里列全。
2. `git diff --name-only` 与 §4 的"你拥有"清单**逐条对得上**，越界 0 颗。
3. 十二颗实现件齐、`DesktopGalleryPorts.install()` 在被调用后 `GalleryPorts.of(handle)` 返回的对象
   十二枚成员全非 null。
4. 三条"没接"的路（Pixiv 两枚、`reverse` 两枚、词典三枚）都能在测试里读出一句**具体**的失败原因，
   而不是空集合。
5. 出图（`:desktop:app --shot`）**不归你验** —— 你的件没有 UI 消费点，那一步在集成 commit 之后由人跑一次。
