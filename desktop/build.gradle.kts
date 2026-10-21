@file:OptIn(org.jetbrains.compose.ExperimentalComposeLibrary::class)

/**
 * R1-F 桌面化 S0-1 探针模块：验证 `Compose Desktop + compose-fluent + WindowStyler`
 * 这套版本矩阵能否与本仓的 `AGP 9.3.2 / Kotlin 2.4.10 / material3 钉版` 同仓共存。
 *
 * 判据（写死在方案文档 `docs/rounds/windows-port-feasibility-2026-10.md` 第七节）：
 * 本模块的窗口能起来，**同时** `:app` 的 APK 与 material3 解析结果一字不变。
 *
 * 下面那份排除清单**为什么是这些颗**，从 2026-10-10 起有一把成文的尺子：
 * `docs/rounds/desktop-port-scope-verdicts-2026-10-10.md` §4（逐颗对账 + `[术]`/`[值]` 两类理由的口径）。
 * 摘行之前先在那张表里找到那一颗的裁决行；找不到就当场补一行，别只改这里。
 */
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.compose.multiplatform)
}

// 与 :app 解耦：桌面侧用 gradle 已自动装好的 JDK 21，不去碰系统那颗 JDK 26
kotlin { jvmToolchain(21) }

// PathProvider 的门面接口住在 :app（Android 模块），纯 JVM 模块依赖不了它 ——
// 沿用 :engine-probe 的共享源目录手法：同一份文件、不复制第二份，
// android/ 子包引用 android.content.Context，桌面侧编不进也无需编进，排除即可。
kotlin.sourceSets["main"].kotlin {
    srcDir("../app/src/main/java/com/venera/compose/data/platform")
    exclude("android/**")
    // Task 3：两端共享的建表 DDL。
    // Task 4a：`data/db` 的持久层核心已经全部脱离 android.*，整个目录进桌面编译，
    // 只剩三个文件点名排除（清单见下方 exclude）。
    // Task 4b：4b 从 8 颗业务文件里抽出的持久层内核（ReadingStatsStore / FavoriteImagesStore /
    // GuardRuleStore / TagDictionaryStore / CoreTableBackup / JsonValues）也放进了 `data/db`，
    // 所以它们**不需要新增 srcDir** 就自动进桌面编译面，`:desktop:test` 因此能拿真
    // JdbcSqliteDatabase + 临时 `.db` 跑它们的行为。
    // 那 8 颗业务文件本身**没有**搬进来，各自的原因为（详见 4b 报告，不许假摘）：
    //  - sync/BackupManager、sync/ForeignArchiveImport：cacheDir 落点/解包、ZipFile、logcat，
    //    BackupManager 还要画廊两个 store 与 FavoriteImagesManager（Bitmap）的单例；
    //  - stats/ReadingStatsManager：标签字典与繁简表要 Context；
    //  - security/guard/ContentGuardManager：源级预设表在 assets 里，另有 StartupTrace 与偏好；
    //  - feature/favoriteimages/FavoriteImagesManager：android.graphics.Bitmap 落盘 + filesDir；
    //  - gallery/data/GalleryTagDictionary：词典库是 assets 复制出来的真实路径（Context.assets）；
    //  - gallery/data/GallerySaver、reader/VeneraReaderScreen：唯一的 ContentValues 是 MediaStore
    //    的 ContentResolver 写入，按本轮口径（平台服务不碰）一行未动。
    // 为什么不用 include：KGP 的 sourceSet 过滤器对整个 source set 生效而非单条 srcDir，
    // 实测 include("**/SchemaSql.kt") 会把本模块自己的源文件一并滤掉且 BUILD SUCCESSFUL（静默假绿），
    // 故排除只能按文件名点着写；剩下三个文件脱离 Android（VeneraDatabase 挪进 data/platform/android、
    // 追更那颗等后台任务方案）后整段 srcDir 可以撤掉。
    srcDir("../app/src/main/java/com/venera/compose/data/db")
    // 摘（= 撤掉某行的 exclude）的判据两条：这颗进桌面能编译，且真有桌面代码调用它。
    // 4b-2 逐颗核过（2026-10），三颗都不满足第一条，全留：
    //  - VeneraDatabase.kt：直接 import android.database.sqlite.SQLiteOpenHelper 与 android.content.Context，
    //    桌面编不过；它唯一的 Android 生产引用点是 data/platform/android/AndroidDatabasePorts.kt（那颗本来
    //    就被上面的 exclude("android/**") 挡在桌面编译面外）。桌面侧没有任何代码调用它。
    //    4a §七.3 预告过的收口（整个文件挪进 data/platform/android/，让 data/db 目录对桌面全透明）
    //    前提已凑齐、但那是 Android 接线面的文件搬迁，不在本段的决策面里，留在这里写明。
    //  - FollowUpdatesRepository.kt：构造吃 android.content.Context，并依赖 source/ComicSourceManager
    //    （那坨直接 import android.util.LruCache 的 Android 网络/引擎层）；桌面调用方是 0。
    //    :app 侧使用点是**两处**：feature/FollowUpdatesViewModel.kt 与同清单里的 FollowUpdatesWorker.kt
    //    （Worker 里 `FollowUpdatesRepository.getInstance(applicationContext).updateFolder`）。
    //  - FollowUpdatesWorker.kt：androidx.work（WorkManager/PeriodicWorkRequest），桌面没有后台任务
    //    调度面，本阶段桌面不做后台追更；调用方同样在 :app 侧。
    exclude(
        "VeneraDatabase.kt",
        "FollowUpdatesRepository.kt",
        "FollowUpdatesWorker.kt",
    )

    // S1（Windows 图库首页）：画廊的**取数层与判据层**进桌面编译面。
    // 三颗目录必须一起点名，因为它们互相指：`gallery/domain` 的编排（`GalleryDailyFeed`）
    // 吃 `gallery/data` 的契约（`GalleryBoards`），而那颗契约的返回类型里有
    // `security/guard` 的 `GuardRule` 与 `data/db` 之外的词汇表 —— 只共享其中一颗编不过，
    // KGP 又不许按单文件挑（上面那段"include 的坑"说的就是这件事）。
    //
    // `security/guard` 里今天只有 `ContentGuardManager.kt` 上不了桌面（assets 预设表 + 偏好 +
    // StartupTrace），剩下三颗是零 import 的纯声明，正是 S1 把 `GuardRule` / `AiTagKeys`
    // 从冻结那颗文件里搬出来的原因（豁免记录见 FREEZE-STATEMENT.md 末尾）。
    //
    // ⚠️ 排除清单的判据是**禁引集合**，不是 `^import android`。反例就在我自己量过的第一遍：
    // `GalleryImageLoader.kt` 只 import 了一句 `android.content.Context`，看起来"换个平台件
    // 就能上桌面"，真正把它钉死的是它间接吃的 `data.prefs` + `data.network`
    // （`VeneraImageFetcher` / `ImageFetchCallFactory`）。只看 android 会一边把它误判成
    // "该进却没进"、一边对这类间接绑定视而不见，所以两份账都在
    // `DesktopSharedFaceLedgerTest` 里按禁引集合对。
    // 第八十七批**量过而没走**的一条路（登记在 `docs/rounds/desktop-reader-paging-2026-10-11.md` §3.1，等点头）：
    // 把 `compose/reader` 也 srcDir 进来，桌面就能直接转交对岸那两颗纯件
    // （`ReaderInteractionPolicy.kt` 零 import、`ComicPageSource.kt` 只 import `java.io.File`，
    //  且现量过这两颗**不指**目录里那三颗 Android 面 —— 逐颗核过，不是推断）。
    // 挡住它的不是编译而是受保域里的一行：`app/src/test/java/com/venera/compose/desktop/DesktopNoAndroidImportTest.kt:82`
    // 那条 UI 禁串把**整个** `reader/` 算作 Android UI 面，于是「桌面 import 那颗纯判据件」在对岸那把尺子上红。
    // 本批不动 `app/`（规矩），改走 B 路：桌面自持一份同轨件，而**键值表、归一别名表、步长算式三处都用
    // 正则从对岸源码文本现取再比**（对岸改了桌面会红 —— 批 82.6 与批 85 用的是同一招回核，不是"抄一份了事"）。
    // ⚠️ B 路的代价要说清：两张表从此要有人对齐，而回核尺子只覆盖"我量过的那三处"，
    //    对岸新增第四处算式时尺子看不见。所以点头之后**应当**改走上面那条 A 路。
    srcDir("../app/src/main/java/com/venera/compose/gallery/data")
    srcDir("../app/src/main/java/com/venera/compose/gallery/domain")
    srcDir("../app/src/main/java/com/venera/compose/security/guard")

    // ── 写法 A 第一批（2026-10-20）：把安卓大屏 UI 的**基建层**真接进桌面编译面 ──
    //
    // 用户的裁决：「之前做的平行线就不要了，按照大屏端复用」。
    // 此前桌面自持一整套 Fluent 平行线（`desktop/gallery/ui/`，72 颗）与另一套仿制 pane
    // （`desktop/ui/`），那就是"维护两份"。这一批开始改成**同一份源码两端编译**。
    //
    // 先接这两颗目录，理由是它们的依赖闭合性最好（现量 2026-10-20）：
    //  - `components/`  48 颗 —— 侧栏（`VeneraSideBar`）、顶栏（`VeneraTopAppBar`）、
    //    卡片（`VeneraCard`）、封面（`VeneraCover`）、宽屏判据（`WideScreenPolicy`）全在这一层；
    //    **零 `import android.`**（本轮把最后 5 颗的平台调用抽进了 `UxPlatformServices` 接口）。
    //  - `ui/tokens/`   7 颗 —— 色板 / 间距 / 字号 / 形状，**零 Android 依赖**（原本就是）。
    //
    // 为什么这两颗必须一起点名：`components/` 有 56 处 import `ui.tokens`，
    // KGP 又不许按单文件挑（见上面"include 的坑"）。只挂一颗编不过。
    //
    // ⚠️ 排除清单的判据仍是**禁引集合**（`DesktopSharedFaceLedgerTest.FORBIDDEN`），
    //    不是 `^import android`：`AndroidUxPlatformServices.kt` 已经搬出本目录
    //    （它现在住 `com/venera/compose/platform/`，那颗目录**不进**桌面编译面）。
    srcDir("../app/src/main/java/com/venera/compose/components")
    srcDir("../app/src/main/java/com/venera/compose/ui/tokens")
    // `source/model/ComicSourceModels.kt`（309 行、零 import、24 个纯 data class）。
    // 加它的直接触发点很具体：`components/ComicCardContextMenu.kt` 要 `Comic` 这个类型
    // （长按菜单画的是漫画卡），而 `Comic` 住这里。整颗目录只有这一颗文件，点名进来即可。
    srcDir("../app/src/main/java/com/venera/compose/source/model")
    // ── 目标③：把 gallery/ui 里**与 Android 宿主无关**的那 11 颗挂进来 ──
    srcDir("../app/src/main/java/com/venera/compose/gallery/ui")
    // ── 目标③ 续：feature/ 里与 Android 宿主无关的那些 ──
    srcDir("../app/src/main/java/com/venera/compose/feature")
    // ── 2026-10-20：业务门面（`BusinessPorts` 那 17 个成员的接口层 + 它引用的纯模型）──
    // 量过：data/api 的 8 颗接口需要 31 个外部符号，其中 30 个定义在**干净的纯模型**文件里，
    // 只有 `UpdateDownload`（在 `data/update/ApkDownloader.kt`，那颗是 Android 下载器）例外。
    // 所以这些目录按**文件**挑着挂，不整目录挂。
    srcDir("../app/src/main/java/com/venera/compose/data/api")
    srcDir("../app/src/main/java/com/venera/compose/stats")
    srcDir("../app/src/main/java/com/venera/compose/download")
    srcDir("../app/src/main/java/com/venera/compose/data/update")
    srcDir("../app/src/main/java/com/venera/compose/source")
    srcDir("../app/src/main/java/com/venera/compose/source/explore")
    srcDir("../app/src/main/java/com/venera/compose/feature/sourcemanage")
    exclude(
        // sourcemanage 只有那颗 94 行的纯模型能进；另两颗（Screen 1629 行 / ViewModel 621 行）绑 Android。
        "**/ComicSourceScreen.kt",
        "**/ComicSourceViewModel.kt",
    )

    // ── AUTO-EXCLUDE 自动收敛段（`_tools/auto_exclude.py` 写，勿手改）──
    exclude(
        "**/ApkDownloader.kt",
        "**/ApkInstallLauncher.kt",
        "**/AppUpdateChecker.kt",
        "**/BaoziMangaSource.kt",
        "**/ComicLinkResolver.kt",
        "**/ComicSourceManager.kt",
        "**/ComicSourceParser.kt",
        "**/ComicSourceScreen.kt",
        "**/ComicSourceViewModel.kt",
        "**/ComicStorageRoot.kt",
        "**/CopyMangaSource.kt",
        "**/DownloadManager.kt",
        "**/DownloadNotificationChannel.kt",
        "**/GalleryAccountCard.kt",
        "**/JsComicSource.kt",
        "**/LocalComicManager.kt",
        "**/MangaDexSource.kt",
        "**/ReadingStatsManager.kt",
        "**/SauceNaoKeyCard.kt",
        "**/SourcePayloadParser.kt",
        "**/TagStatsAggregation.kt",
        "**/WebLoginScreen.kt",
    )
    // ── AUTO-EXCLUDE 结束 ──

















    exclude(
        // ⚠️ 必须带 `sourcemanage/` 这一层：`**/ComicSourceModels.kt` 会把
        // `source/model/ComicSourceModels.kt`（那颗 309 行纯模型，两端都在用）一起排掉。
        "**/sourcemanage/ComicSourceModels.kt",
        "**/ComicItem.kt",
        "**/DetailActionButton.kt",
        "**/ReaderSettings.kt",
        "**/PreferredIpSettings.kt",
        "**/ExploreViewModel.kt",
        "**/ExplorePolicy.kt",
        "**/ExploreState.kt",
        // ↑ 这几颗依赖 `data.prefs`（桌面禁引族）或 material3 实验组件。
        "**/AboutSection.kt",
        "**/AppearanceSettings.kt",
        "**/AppSettings.kt",
        "**/BlockingSettings.kt",
        "**/ComicDetailScreen.kt",
        "**/ComicDetailViewModel.kt",
        "**/ComicSourceScreen.kt",
        "**/ComicSourceViewModel.kt",
        "**/CoverViewerScreen.kt",
        "**/DownloadScreen.kt",
        "**/ExploreSettings.kt",
        "**/FavoriteImagesManager.kt",
        "**/FavoriteImagesScreen.kt",
        "**/FavoritesScreen.kt",
        "**/FavoritesSplitPaneHost.kt",
        "**/FavoritesViewModel.kt",
        "**/FollowUpdatesScreen.kt",
        "**/FollowUpdatesViewModel.kt",
        "**/GalleryAccountCard.kt",
        "**/GallerySettings.kt",
        "**/HistoryScreen.kt",
        "**/HistoryViewModel.kt",
        "**/HomeScreen.kt",
        "**/HomeViewModel.kt",
        "**/LocalComicScreen.kt",
        "**/LocalFavoritesSettings.kt",
        "**/LogViewerScreen.kt",
        "**/Navigation.kt",
        "**/NetworkFavoritesScreen.kt",
        "**/NetworkFavoritesViewModel.kt",
        "**/NetworkSettings.kt",
        "**/PetImageLoader.kt",
        "**/PetMascot.kt",
        "**/PreferredIpSpeedTestScreen.kt",
        "**/SauceNaoKeyCard.kt",
        "**/SearchScreen.kt",
        "**/SearchViewModel.kt",
        "**/SettingsComponents.kt",
        "**/SettingsHero.kt",
        "**/SettingsHeroImage.kt",
        "**/SettingsHome.kt",
        "**/SettingsHost.kt",
        "**/SettingsQuoteCard.kt",
        "**/SettingsScreen.kt",
        "**/SourceEditScreen.kt",
        "**/SourceSectionScreen.kt",
        "**/StatsScreen.kt",
        "**/SyncBackupScreen.kt",
        "**/UnifiedExploreScreen.kt",
        "**/UpdateCheckUi.kt",
        "**/VeneraTheme.kt",
        "**/WebLoginScreen.kt",
    )

    exclude(
        // 这 5 颗虽然自己不含 Android 绑定，但引用了被排除的同包文件
        // （CardsGrid / CardsAxis / 分享条 / fly 动画状态机…），挂进来会连带 200+ 错。
        // 它们是"干净的叶子"才有意义，一旦依赖被排除的兄弟就不是叶子了。
        "**/GalleryForYouScreen.kt",
        "**/GalleryBookmarksHubScreen.kt",
        "**/GalleryLiveFlyBody.kt",
        "**/GalleryDisintegration.kt",
        // ↑ `asAndroidBitmap`（androidx.compose.ui.graphics 的 Android 位图互转），桌面侧没有。
        "**/GalleryViewerToolbar.kt",
        // ↑ 需要 material3 的 `CircularWavyProgressIndicator`（实验性），桌面侧那颗版本没带。
        "**/GalleryPixivSearchArea.kt",
        // 2026-10-20：`GalleryNotices` 已改走 LocalUxPlatformServices（原来那处 `context.openSettingsSubScreen`），
        // 它现在是干净叶子 —— 不再排除。
"**/GalleryArtistProfileScreen.kt",
"**/GalleryArtistRows.kt",
"**/GalleryCardActionSheet.kt",
"**/GalleryCardCaption.kt",
"**/GalleryDailyScreen.kt",
"**/GalleryFavoritesBody.kt",
"**/GalleryFlyIn.kt",
"**/GalleryForYouViewModel.kt",
"**/GalleryHistoryScreen.kt",
"**/GalleryHomeChrome.kt",
"**/GalleryHomeSections.kt",
"**/GalleryImageShare.kt",
"**/GalleryInfoSheet.kt",
"**/GalleryPageAxis.kt",
"**/GalleryPixivAccount.kt",
"**/GalleryPixivHome.kt",
"**/GalleryPostScreen.kt",
"**/GalleryReverseResults.kt",
"**/GalleryReverseSearchArea.kt",
"**/GalleryReverseViewModel.kt",
"**/GalleryScreen.kt",
"**/GallerySearchArea.kt",
"**/GallerySearchViewModel.kt",
"**/GallerySharedTransition.kt",
"**/GalleryVideoViewer.kt",
"**/GalleryViewModel.kt",
"**/PixivAccountScreen.kt",
"**/PixivAccountSessionStore.kt",
"**/PixivArtistScreen.kt",
"**/PixivWorkInfoPanel.kt",
"**/PixivWorkScreen.kt",
    )

    exclude(
        // 「预测性返回」是 AndroidX 的系统手势（`androidx.activity.compose.PredictiveBackHandler`），
        // 桌面侧没有那颗件。共享面已经把**状态机**与**handler 调用**拆开了：
        // `PredictiveBack.kt` 里 `rememberPredictiveBackState` 按
        // `UxPlatformServices.supportsPredictiveBack` 分支，但 handler 那一段
        // （`PredictiveBackGestureReceiver`）仍 import 了 androidx.activity ⇒ 桌面编不过。
        // 所以整颗排除，桌面上"多选退出跟手"退化成不跟手（浮层仍可点关）。
        // 将来要收口：把 handler 也抽进平台层（`supportsPredictiveBack` 已经预留了那个开关）。
        // 站点标记：`VeneraGallerySourceMark.kt` 用四颗 `R.drawable.ic_source_*` 位图
        // （gelbooru / yandere / safebooru / pixiv）。`R` 是 AGP 生成的资源索引类，
        // 桌面侧根本没有这套资源管线；要收口得先把四颗矢量搬成 Compose `ImageVector`
        // 或 Compose Resources（那是一次独立的资源迁移，不在本批）。
        // 调用点只有 `VeneraCard` 的站点角标那一处 —— 桌面侧的卡因此不画角标。
        //
        // ⚠️ 写成 `**/VeneraGallerySourceMark.kt` 而不是裸文件名：`exclude` 的匹配对象是
        // **相对各 srcDir 根**的路径，而这颗文件在 `components/venera/` 子目录里。
        // 裸文件名在顶层 srcDir（`data/platform` 等）有效，但对子目录里的文件不命中 ——
        // 实测（2026-10-20）裸名此处**静默不生效**，编译照样去编它并报 `Unresolved 'R'`。
        "**/VeneraGallerySourceMark.kt",
        "**/PredictiveBack.kt",
    )

    // 逐颗点名，每颗的成因各不相同（写在这里而不是"一张总理由"，是因为撤排除时要按颗重判）。
    exclude(
        // Android 接线那半边：包名没变、只是搬进 `android/` 子目录，
        // 上面的 `exclude("android/**")` 已经把各颗目录里的 android 子目录全挡掉了；
        // 这里再点名一次不是为了生效，是为了让清单读得完整 —— 有人把接线搬出子目录时会红。
        //
        // —— 以下七颗是 gallery/data 里的 Android 面 ——
        // 解码闸门：整颗是 coil3 的 Extras/Decoder 管线，桌面侧取图那一层今天还没接 coil。
        "GalleryAnimationGate.kt",
        // ConnectivityManager + NetworkCapabilities：平台网络状态句柄，桌面要另一套判据。
        "GalleryConnectivity.kt",
        // 日榜在途缓存：read(app)/write(app,…) 的形状被 gallery/ui 的 ViewModel 直接拿着。
        "GalleryFeedCache.kt",
        // 推荐页在途缓存：同上，两颗是同一族。
        "GalleryForYouCache.kt",
        // 取图器：coil3 + data.prefs + data.network（不是 android import 挡的，是间接依赖挡的）。
        "GalleryImageLoader.kt",
        // 存图到相册：MediaStore/ContentValues/Environment，平台服务，本轮口径明确不碰。
        "GallerySaver.kt",
        // 标签词典：库文件是 assets 复制出来的真实路径，且走 platform.android 的开库口子。
        // （批 40：桌面那两颗自持件补的就是这两件事 —— DesktopTagDictionaryAsset 从 classpath 落成真实路径，
        //  DesktopGalleryTagDictionary 只读打开后交给共享的 data/db/TagDictionaryStore。）
        "GalleryTagDictionary.kt",
        // —— 以下四颗是"实现类留在 Android、契约与窄接口进桌面"的那一组 ——
        // Gelbooru 账号：Context + AndroidKeyValueStore；客户端只认它实现的 GelbooruCredentials。
        "GelbooruAccount.kt",
        // SauceNao 账号：同上，客户端只认 SauceNaoCredentials 那一枚 apiKey。
        "SauceNaoAccount.kt",
        // 日榜取数的 Android 门面：唯一存在理由是 gallery/ui 那两处 getInstance(context) 零改动，
        // 编排本体在 GalleryDailyFeed.kt（那颗才是共享面）。
        "GalleryFeedSource.kt",
        // 内容守卫实现：assets 里的源级预设表 + VeneraPreferences + StartupTrace。
        // 桌面屏蔽判据直连 data/db 的 GuardRuleStore，不需要这颗（那是 4b 抽出来的内核）。
        "ContentGuardManager.kt",
    )


}

// Task 6：源脚本**随包分发**。桌面链路真用得上的只有这几类（逐个点名，不整个目录搬）：
//  - `sources/*.js` —— EngineSession.load 取的那份源脚本；
//  - `venera-init.js` / `venera-shim.js` —— DesktopJsHost 装载序列的前两颗。
//     （派单口径 R39 只列了 init，读数在案：DesktopJsHost.kt:102 `readText(venera-shim.js)`
//      是无条件执行的，不带它包内装载必崩，所以按"装载路径确实用到"这条判据把它一起带上。）
//  - `gallery_tags_*.sqlite` —— 批 40 起：画廊那三处译名消费点的数据源
//     （`DesktopGalleryTagDictionary` 复制成真实路径再只读打开；3,257,344 B 的包体增量就是它）。
// **不带** `opencc.txt`：它唯一的消费点是 `app/…/data/tags/ChineseVariantConverter.kt`
//   （`data/tags` 不在本模块 srcDir 的编译面里），桌面侧今天一行都不读它。
// 同理不带 `tags.json` / `tags_tw.json` / `licenses/` / `sources/index.json`
// （源清单是枚举 `sources/` 得来的，不是读这份索引）。
//
// ⚠️ **2026-10-21 更正**：`source_content_warning.json` 从「不带」改成**带上**。
// 原注释把它和 tags.json 归成一类（"桌面侧今天一行都不读它"），那句话当时是真的；
// 但安全评审 C 发现桌面因此**少了一整道内容判据** ——
// 安卓 `ContentGuardManager.maskHit:441-443` 靠这张表判 `nsfw`/`mixed` 源，
// 桌面没有它 ⇒ 历史记录那种**不带 tags** 的模型永远判不出成人向 ⇒ 封面恒裸。
// 现在 `DesktopSourceContentPresets` 真在读它（包内优先，走 classpath）。
// 批 40 起**带上** `gallery_tags_*.sqlite`：桌面那颗 `DesktopGalleryTagDictionary` 真在读它
// （标签墙译名 / 关注栏 / 补全那排三处消费点），取的是"包内优先"那条 classpath 路，
// 名字里的行数就是词典的版本号 ⇒ include 写成 `*` 而不是钉死 `79415`，换一批词典不必改构建脚本。
// include 的坑与上面 kotlin 源集同一条：过滤器对**整个 resources 源集**生效，本模块今天
// 没有自己的 `src/main/resources`，将来加了必须往这张清单里补一颗，否则会被**静默滤掉**。
sourceSets["main"].resources.srcDir("../app/src/main/assets")
sourceSets["main"].resources.include(
    "sources/*.js",
    "venera-init.js",
    "venera-shim.js",
    "gallery_tags_*.sqlite",
    // 源级内容分级预设表：`DesktopSourceContentPresets` 读它判 `nsfw`/`mixed` 源。
    // 不带这颗，历史卡（无 tags）就永远判不出成人向 —— 安全评审 C。
    "source_content_warning.json",
)

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(compose.foundation)
    implementation(compose.ui)
    implementation(compose.material)
    implementation(compose.materialIconsExtended)
    implementation(compose.components.uiToolingPreview)

    // ── 写法 A（2026-10-20）：安卓大屏 UI 那一层的 material3 ──
    //
    // `components/` 与 `ui/tokens/` 有 60 余处 `androidx.compose.material3.*`
    // （`MiuixTheme` 的色板桥接、`MaterialTheme`、`DropdownMenu`、`Icon`、`Surface`…）。
    // 桌面侧原本只用 `compose.material`（M2），编不过。
    //
    // 坐标取 **JetBrains 的 `org.jetbrains.compose.material3`**（由
    // `compose.material3` 那枚 DSL 提供），不是 `androidx.compose.material3`：
    // 前者是对桌面/JS/iOS 打过包的 CMP 产物，后者是 Android-only 的 AAR。
    //
    // **版本共存的既有事实**（方案文档 §6.5 判据 4 的 A0 实测）：
    // `:app` 今天就已经经由 `com.materialkolor:material-kolor:5.0.1` 把 JetBrains 那颗
    // material3 带在 runtimeClasspath 上（`_qa/a0/baseline_app_deps.txt:887`），
    // 所以"两颗坐标族同挂而不撞同名类"不是本条新引入的风险。
    // 而 `:app` 自己仍解析为 `androidx.compose.material3:1.5.0-alpha22`，一个字节都没变 ——
    // 那正是"隔离安卓端"这条硬约束的兑现方式。
    @OptIn(org.jetbrains.compose.ExperimentalComposeLibrary::class)
    implementation(compose.material3)

    implementation(libs.compose.fluent.desktop)
    implementation(libs.window.styler)
    // 端到端最小闭环：真源脚本 + GraalJS 宿主 + 取图，全走 :engine-probe 那批共享 handler
    implementation(project(":engine-probe"))
    // A0 探针（docs/rounds/desktop-reuse-android-ui-2026-10-18.md §6.2 第 3 条）：同一份 `Hello()`，
    // 桌面侧吃的是那颗 KMP 模块的 `-jvm`/`-desktop` 变体。入口见 SharedProbeWindow.kt（任务 `sharedprobe`）。
    implementation(project(":shared-probe"))
    // Miuix 的 desktop 变体：同一版本目录里能不能与 fluent-desktop 并存，是 S0-1 要量的一格
    implementation(libs.miuix.ui.desktop)
    // 写法 A（2026-10-20）：`components/ComicTileLayout.kt` 用 Miuix 官方图标集那对
    // `MiuixIcons.ListView` / `MiuixIcons.GridView`（注释写明"线宽与其余 Miuix chrome 同源"）。
    // 那颗坐标的依赖面已核：只拉 miuix-core + compose foundation，**不引用 material3**
    // ⇒ 不会顶掉被 Miuix 钉住的那一版（见 libs.versions.toml 的登记）。
    implementation(libs.miuix.icons)
    // S0-7：现有 4 个玻璃消费点都先过 isRuntimeShaderSupported()，
    // 它在 desktop 返回什么决定"玻璃整段静默跳过"还是"真能画"
    implementation(libs.miuix.blur)
    implementation(libs.backdrop)
    implementation(libs.kyant.shapes)
    // JsonKeyValueStore 的落盘格式（`data/platform` 的共享源里就用这一棵树，不再引第二套 JSON 库）。
    // 必须显式声明：`:engine-probe` 那份是 implementation，不透传到本模块的编译 classpath。
    implementation(libs.kotlinx.serialization.json)
    // Task 3：JdbcSqliteDatabase 的驱动。坐标版本照阶段 1 方案 D3 写死（已在 Maven Central 核到）。
    implementation("org.xerial:sqlite-jdbc:3.53.4.0")
    // Task 4b：`data/db` 的持久层 JSON（JsonValues / CoreTableBackup）替代原来的 android `org.json`
    // ——后者桌面侧根本没有。生产调用方是真的：`sync/BackupManager` 导出与恢复归档的九个 member、
    // `sync/ForeignArchiveImport` 读 PicaComic 的 `sync_data`、`security/guard/ContentGuardManager`
    // 读 assets 的源级预设表，全走这一层。
    // （Task 4a 曾为已删的 `folderToJson` 加过它又撤掉；这次带进来的是有生产调用方的那份。）
    implementation(libs.gson)

    // Coil3 图片加载器（桌面侧自持，不走 VeneraImageFetcher）
    // libs.coil = coil3 (3.6.2)，已在 libs.versions.toml 定义
    // 桌面这一枚**不是可选项**：Coil 的 `ImageLoader` 作用域吃 `Dispatchers.Main.immediate`，
    // 而 Compose Desktop 不把那个 dispatcher 带上 classpath —— 少了这一行，第一次构造
    // `SingletonImageLoader` 就弹一枚 AWT Error 框「Module with the Main dispatcher is missing」
    // （2026-10-08 第四十七批的图证拍到的，判据 645 条当时全绿）。版本不写：靠图里已有的
    // `kotlinx-coroutines-bom` 供，写死一枚反而会与 core 不同版（那句报错后半段警告的正是这件事）。
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing")
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)

    // 大图那一屏的缩放能力（第五十批）。选它的三条读数都写在
    // docs/rounds/desktop-image-viewer-design-2026-10-08.md §2：stable 2.16.0、
    // desktopRuntime 的 compose 三件与本仓逐字同版（1.12.1）、滚轮是一等公民。
    // ⚠️ 它的滚轮默认档是 EnabledWithCtrlKey，Task 5 那处必须显式选 Enabled。
    // ⚠️ 本仓 Android 侧在用的 me.saket.telephoto 那一族不取：`zoomable-image-coil3` 没有
    // desktop 变体（0.16.0 与 0.19.0 两次实测 desktop=NONE），且它的桌面件不含 Wheel。
    implementation("net.engawapg.lib:zoomable:2.16.0")

    // `data/platform/HttpEngine.kt`在上面的共享 srcDir 里，而它签名上的 `OkHttpClient` 是 okhttp 的类型
    // —— 不显式声明就编不过。必须显式：`:engine-probe` 那份是 implementation，不透传到本模块编译 classpath。
    // 注意这是"接口签名需要它的类型"，不等于桌面已经接好了一条真路（桌面侧实现件与它一起落在 S2）。
    implementation(libs.okhttp)

    // 阶段 1 起 :desktop 有 JVM 单测；栈跟仓库钉版一致（JUnit4，libs.junit = junit:junit:4.13.2），不引新测试框架
    testImplementation(libs.junit)
    // 离屏语义树测试接入（2026-10-09）：runComposeUiTest 把界面渲成语义树，performClick 验屏上文案变没变。
    // 仍是 JUnit4 runner（compose 自带），未脱离既有测试栈；只补"像素层那一段"盲区（qoder 的纯函数判据盖不到）。
    testImplementation(compose.uiTest)
}

tasks.withType<Test> {
    useJUnit()
    // ⚠️ 把**本构建脚本**声明成测试输入（2026-10-04 桌面第十批实测才加）。
    //   有几条判据是"扫源码文本"的，其中一条扫的就是**这颗文件**（`-PstartSection=` 有没有真被
    //   拼进 `args`）。而 Gradle 不知道这件事：改这里它仍报 `:desktop:test UP-TO-DATE` ——
    //   扰动实验第一次就是这么"绿"过去的（读数：`Task :desktop:test UP-TO-DATE` + `GRADLE_EXIT=0`，
    //   加 `--rerun-tasks` 复跑才红）。**扫构建脚本的判据必须自己声明这个输入，否则它会拿着旧结果放行。**
    inputs.file(project.buildFile)
}

compose.desktop {
    application {
        // 打包对象从"探针窗"换成真正的最小闭环 App：
        // 要验的是 GraalJS 能不能活过 jlink 出来的运行时（S0-9 那次打的是 SpikeWindow，没覆盖到）
        mainClass = "com.venera.desktop.VeneraDesktopKt"

        // S0-9 打包分发探针：出可安装的 exe/msi，并显式带 jdk.accessibility
        // （Compose Desktop 的无障碍走 Java Access Bridge，缺这个模块则读屏软件全哑）
        nativeDistributions {
            targetFormats(org.jetbrains.compose.desktop.application.dsl.TargetFormat.Exe, org.jetbrains.compose.desktop.application.dsl.TargetFormat.Msi)
            packageName = "venera-desktop-probe"
            packageVersion = "1.0.0"
            // 无障碍：Compose Desktop 走 Java Access Bridge，缺 jdk.accessibility 则读屏软件全哑。
            // jdk.unsupported：`sun.misc.Unsafe` 住在这个模块里，而 jlink 的自动模块推导看不出来
            // —— Truffle 的 NodeClassImpl 启动即 NoClassDefFoundError: sun/misc/Unsafe，
            // 打包跑第一次就把 GraalJS 打死（实测读数见 docs/rounds/windows-r1f-s0-spike-2026-10-02.md §七）。
            // java.sql：同一族的第二课，这次打死的是"读库"那一条路。sqlite-jdbc 是**自动模块**，
            // 它对 java.sql 的依赖 jlink 推不出来 ⇒ 交付包里 `java.sql.SQLException` 直接找不到，
            // 而开发跑用的是全 JDK，判据与 dev 起窗都抓不到 —— 只有打包跑看得见：
            // `D_异常 … 因=NoClassDefFoundError: java/sql/SQLException 栈=DesktopDatabasePorts.kt:200`（2026-10-10，批 82.9）。
            modules("jdk.accessibility", "jdk.unsupported", "java.sql")
            description = "R1-F 阶段 0 探针（非正式产物，只验打包链）"
            vendor = "venera-miuix"
            // 不带控制台时 exe 的 stdout 直接丢弃，打包后的启动证据就全看不到了。
            // 正式产物要改成写日志文件；这里是为了量"闭环在 jlink 运行时里到底起没起"。
            windows {
                console = true
            }
        }
    }
}

// S0-2 的 8 组件组合探针（`:desktop:run` 现在指向真 App，探针窗改走这个任务）
tasks.register<JavaExec>("spike") {
    group = "probe"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass = "com.venera.desktop.SpikeWindowKt"
}

// A0 探针判据 3：`:shared` 那颗 KMP 模块在桌面侧起一扇窗（同一颗 runtimeClasspath，
// 与 `:desktop:run` 解析到的东西一致 —— 换的只有 main class，手法与上面 `spike` 同一族）。
//   sh gradlew :desktop:sharedprobe                读数打完 4 秒自己关
//   加 -Phold=20000 让它多停一会儿（要给窗口捕获留时间）
tasks.register<JavaExec>("sharedprobe") {
    group = "probe"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass = "com.venera.desktop.SharedProbeWindowKt"
    workingDir = rootDir
    jvmArgs("-Dfile.encoding=UTF-8", "-Dstdout.encoding=UTF-8", "-Dstderr.encoding=UTF-8")
    args("hold=" + providers.gradleProperty("hold").getOrElse("4000"))
}

// S0-7：玻璃能力三层读数（纯 stdout，不需要窗口）
tasks.register<JavaExec>("glass") {
    group = "probe"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass = "com.venera.desktop.GlassProbeKt"
}

// 批 62：S0-7 §六 欠的那一步 —— 应用内模糊**画不画得出来**（要开一扇窗，靠 WGC 拍帧）
tasks.register<JavaExec>("blurprobe") {
    group = "probe"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass = "com.venera.desktop.BlurRenderProbeKt"
}

// 批 64：S0-9 —— 那一层糊**每帧花多少钱**（三相：不糊 → 糊 → 再不糊；读数全在 stdout，取完数自己走）。
//   ⚠️ 那三条 UTF-8 是必需的：这一颗念的每一行都带中文（`sh gradlew :desktop:blurcost`）。
tasks.register<JavaExec>("blurcost") {
    group = "probe"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass = "com.venera.desktop.BlurCostProbeKt"
    jvmArgs("-Dfile.encoding=UTF-8", "-Dstdout.encoding=UTF-8", "-Dstderr.encoding=UTF-8")
    // 堆上限写死（与 `reader` 同一颗纪律）：读数里那两枚 MB 才可复现。
    jvmArgs("-Xmx2g")
    // 加 -Phold=20000 让每一相起手真等 20 秒 —— 那一档是给 WGC 拍帧用的（毫秒数证明不了"那一相真糊了"）。
    // 加 -Pstep=400 把每帧推进调大：那是**正对照的把手**（三相读数一模一样时，得先证明这把尺子认账）。
    // 加 -Poverlay=anim 让**覆盖层**每帧换内容（生产那一形：墙静帧、动图在浮层里播）。默认静。
    // 加 -Prounds=3 把整副 ABBA 连跑几轮：每轮独立算地板、独立下判词（一轮的地板就是那一轮的散布，说不了话）。
    args(
        listOf(
            providers.gradleProperty("hold").orNull?.let { "hold=$it" },
            providers.gradleProperty("step").orNull?.let { "step=$it" },
            providers.gradleProperty("overlay").orNull?.let { "overlay=$it" },
            providers.gradleProperty("rounds").orNull?.let { "rounds=$it" },
        ).filterNotNull()
    )
}

// S0-8：条漫单列数千图的滚动帧时间与内存
tasks.register<JavaExec>("reader") {
    group = "probe"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass = "com.venera.desktop.ReaderProbeKt"
    // 堆上限写死，读数才可复现；-PdecodePerItem=true 跑"每项各自解码"的端到端档
    jvmArgs("-Xmx2g")
    args(providers.gradleProperty("decodePerItem").getOrElse("false"))
}

// 端到端最小闭环：真源 → GraalJS → 取图 → Fluent 窗口
//   ./gradlew :desktop:app -Pkey=jm -Pproxy=127.0.0.1:7890
//   加 -Pshot=_qa/desktop-jm.png 时它自截图后退出（无人值守取证）
//   加 -Pautofav=1 / -Pfavcheck=1 走收藏取证两跑（写 / 读回，只为读数存在，见 VeneraDesktop.kt）
//   加 -PstartDomain=SETTINGS 让无人值守取证落在**指定那一域**（认枚举名或中文题名；
//     认不出就退出码 1 且不开窗 —— 不退到默认域，理由在 ForensicFlags.startDomain 的注释里）。
//     ⚠️ 走命令行时**首选枚举名**：中文值要穿过 gradle 属性与 Windows 控制台编码那两层。
//   加 -PstartSection=LATEST 让取证落在**域内 pane 的那一行**（第十批 ⑤；`--start-domain=` 只到域）。
//     认的是行键或中文题名两种，对不上任何行时**不中止但出声**（那句在 ForensicFlags.sectionNote）。
//     例：`-PstartDomain=GALLERY -PstartSection=最新` 才拍得到刚点亮的「最新」那一屏。
//   加 -Pdetail=1 让**详情浮层自己摆出来**（那一层本来要点一张卡才出来，而鼠标自验是禁的）：
//     判读层认 `--detail=<值>`，0/false/off 是关；不给值只按开处理（见 ForensicFlags.state）。
//   加 -Partist=yandere/某标签 让取证**直接进那一位的单人页**（第二十批；那一屏本来要在画师列表上
//     点「看他的图」才出来，而鼠标自验是禁的 ⇒ 没有这一枚时那一屏只编译得过、拍不到图）。
//     值两截 `<站点>/<标签>`，站点段认键、枚举名、站方写法三种；读不出不中止但出声（ForensicFlags.artistNote）。
//   加 -Pranking=MONTH/2024-03 让取证**把排行榜拨到那一档、那一期**（第三十批；「上一期 / 下一期 / 回到本期」
//     那一排本来要把档拨到月/年之后才出现，而鼠标自验是禁的 ⇒ 没有这一枚时那一层只编译得过、拍不到图）。
//     档段认枚举名、下拉里那一整行的题、胶囊上那一小截三种；期段可省（省了就是本期）。
//     ⚠️ 走命令行时**首选枚举名**：中文值要穿过 gradle 属性与 Windows 控制台编码那两层。
// Task 6 第 4 件：这两个开关**把值一起传下去**（`--autofav=<值>`），由程序判读 ——
//   原来只传"给没给"，`-Pautofav=0` 也会点亮，是个假开关形状。现在 0/false/off 是关，且照样打读数。
// 手填凭据那枚取证种子（批 88·下半）。同一颗纪律第十一次：**判读层与消费点接好了也不算通**，
// 漏了这一跳 `-Pcookie=…` 就是"不报错也不做事"，而"罐里没那枚凭据"会被读成"手填那一路坏了"。
// 为什么在这一层摊成**多枚** `--cookie=`：一站要粘几枚字段（ehentai 四枚），而 `-P` 只能递一个串 ——
// 分隔符归 transport 这一层，`ForensicFlags.cookies` 始终只认那一枚参数的规范形状。
// 值形如 `-Pcookie=exhentai.org:ipb_a=1|exhentai.org:ipb_b=2`。
val cookieSeeds: List<String> = providers.gradleProperty("cookie").orNull
    ?.split("|")?.filter { it.isNotBlank() }?.map { "--cookie=$it" }
    .orEmpty()

tasks.register<JavaExec>("app") {
    group = "probe"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass = "com.venera.desktop.VeneraDesktopKt"
    workingDir = rootDir
    // ⚠️ 强制 UTF-8 写 stdout（2026-10-04 实测才加）：JDK 18+ 的 `System.out` 跟的是
    //   `stdout.encoding`，中文 Windows 上默认落到 GBK，而 Gradle 按 UTF-8 解码子进程输出 ——
    //   结果是**每一条 `D_` 中文读数都变成 U+FFFD**，只有 ASCII 部分（枚举名、数字）活下来。
    //   这一颗任务的产物就是读数，读数码着等于没产；S0 那轮没暴露是因为探针窗只打 `S02_COMPOSED_OK`。
    jvmArgs("-Dfile.encoding=UTF-8", "-Dstdout.encoding=UTF-8", "-Dstderr.encoding=UTF-8")
    args(
        listOf(
            providers.gradleProperty("key").getOrElse("jm"),
            providers.gradleProperty("proxy").orNull?.let { "--proxy=$it" },
            providers.gradleProperty("shot").orNull?.let { "--shot=$it" },
            providers.gradleProperty("startDomain").orNull?.let { "--start-domain=$it" },
            providers.gradleProperty("query").orNull?.let { "--query=$it" },
            providers.gradleProperty("startSection").orNull?.let { "--start-section=$it" },
            // 详情浮层那颗取证种子（第十一批 P1 判读层接好了，这一跳当时漏了 ⇒ `-Pdetail=1` 不报错也不做事，
            // 而"浮层拍不到图"会被读成"浮层起不来"。判据 `DesktopKeyNavigationTest` ⑨ 钉这一跳。）
            providers.gradleProperty("detail").orNull?.let { "--detail=$it" },
            // 画师单人页那颗种子（第二十批）。同一颗纪律：**判读层与消费点接好了也不算通**，
            // 这一跳漏了的话 `-Partist=…` 就是"不报错也不做事"，而"那一屏拍不到图"会被读成"那一屏起不来"。
            // 值两截 `<站点>/<标签>`，站点写法与判读见 ForensicFlags.artist。
            providers.gradleProperty("artist").orNull?.let { "--artist=$it" },
            // 漫画详情那一屏那颗种子（第八十批）。同一颗纪律第七次：**判读层与消费点接好了也不算通**，
            // 这一跳漏了的话 `-Pcomic=…` 就是"不报错也不做事"，而"那一屏拍不到图"会被读成"那一屏起不来"。
            // 值两截 `<漫画号>[/<题名>]`，判读见 ForensicFlags.comic。
            providers.gradleProperty("comic").orNull?.let { "--comic=$it" },
            // 阅读那一屏那颗种子（第八十一批）。同一颗纪律第八次：**判读层与消费点接好了也不算通**，
            // 这一跳漏了的话 `-Pread=…` 就是"不报错也不做事"，而"那一屏拍不到图"会被读成"那一屏起不来"。
            // 值一到三截 `<漫画号>[/<话号>[/<章名>]]`，判读见 ForensicFlags.read。
            providers.gradleProperty("read").orNull?.let { "--read=$it" },
            // 阅读那一屏的**起点页**那颗种子（第八十二批半）。同一颗纪律第九次：**判读层与消费点接好了也不算通**，
            // 这一跳漏了的话 `-PreadPage=3` 就是"不报错也不做事"，而"页号没写进历史那一行"会被读成"写口坏了"。
            // 值是 0 起的页索引（不是第几页），判读见 ForensicFlags.readPage。
            providers.gradleProperty("readPage").orNull?.let { "--read-page=$it" },
            // 第八十七批：把阅读那一屏顶到某一档模式（`-PreadMode=HORIZONTAL_RTL`）。
            // 没有这一跳，横向那一面墙在帧上永远不存在 —— 它要"点模式那一排的一枚"才出来，而取证不许动鼠标。
            providers.gradleProperty("readMode").orNull?.let { "--read-mode=$it" },
            // 批 92：把浮层与大图那一屏**起在哪一档**种下来（`-PdetailTier=原图`，屏名与枚举名都认）。
            // 同一颗纪律第十一次：**判读层与消费点接好了也不算通**，漏了这一跳 `-PdetailTier=…` 就是
            // "不报错也不做事"，而"原图档那一格拍不到图"会被读成"那一层坏了"。判读见 ForensicFlags.detailTier。
            providers.gradleProperty("detailTier").orNull?.let { "--detail-tier=$it" },
            // 批 96：把某一话**整话写进本机**那颗种子（`-Pdownload=<漫画号>/<话号>`）。
            // 同一颗纪律第十二次：**判读层与消费点接好了也不算通**，漏了这一跳 `-Pdownload=…` 就是
            // "不报错也不做事"，而"那一格拍不到落盘"会被读成"整话下载坏了"。
            // 它没有别的入口可点：桌面那一格挂在**长按**上（`combinedClickable` 有 onLongClick 而没有 onRightClick），
            // 而取证不许动鼠标（手册 §8.1）⇒ 只能让种子去按同一颗 runner。判读见 ForensicFlags.download。
            providers.gradleProperty("download").orNull?.let { "--download=$it" },
            // 把某一颗随包源当成本台"停用着"起来（批 83.1）：只为拍到帧 —— 那一格今天只有离屏语义树量过。
            // 同一颗纪律第十次：**判读层与消费点接好了也不算通**，漏了这一跳 `-Pdisable=jm` 就是
            // "不报错也不做事"，而"源管理那一屏说一颗都没停用"会被读成"停用那枚开关坏了"。
            providers.gradleProperty("disable").orNull?.let { "--disable=$it" },
            // 排行榜那颗种子（第三十批）。同一颗纪律：**判读层与消费点接好了也不算通**，
            // 这一跳漏了的话 `-Pranking=…` 就是"不报错也不做事"，而"那一排拍不到图"会被读成"那一层起不来"。
            // 值两截 `<档>[/<期>]`，写法与判读见 ForensicFlags.ranking。
            providers.gradleProperty("ranking").orNull?.let { "--ranking=$it" },
            // 反搜那颗种子（第三十六批）。同一颗纪律：**判读层与消费点接好了也不算通**，
            // 这一跳漏了的话 `-Preverse=1` 就是"不报错也不做事"，而"结果那一格拍不到图"会被读成"那一格起不来"。
            // 值只判开不开（`--reverse` / `--reverse=0`），它顺带把详情浮层摆出来（那一枚按钮在浮层里）。
            providers.gradleProperty("reverse").orNull?.let { "--reverse=$it" },
            // 反搜本机那一格那颗种子（第七十七批 下半）。同一颗纪律第六次：**判读层与消费点接好了也不算通**，
            // 这一跳漏了的话 `-PreverseFile=C:/x.png` 就是"不报错也不做事"，而"那一格拍不到图"会被读成"那一格起不来"。
            // 值是一条本机路径（它替用户跳过系统文件框那一步 —— 那一步今天没有鼠标走不到）。
            providers.gradleProperty("reverseFile").orNull?.let { "--reverse-file=$it" },
            // 大图那一层的两枚种子（第五十批）。同一颗纪律第三次：**判读层与消费点接好了也不算通**，
            // 这一跳漏了的话 `-Pviewer=1` 就是"不报错也不做事"，而"那一屏拍不到图"会被读成"那一屏起不来"。
            // `viewer` 判开不开（`--viewer` / `--viewer=0`，默认是**进**）；`viewerZoom` 是一个倍数。
            providers.gradleProperty("viewer").orNull?.let { "--viewer=$it" },
            providers.gradleProperty("viewerZoom").orNull?.let { "--viewer-zoom=$it" },
            // 崩一枚那颗种子（第五十三批）。同一颗纪律第四次：**判读层与消费点接好了也不算通**，
            // 这一跳漏了的话 `-Pcrash=thread` 就是"不报错也不做事"，而"崩了没念出那一行"会被读成
            // "那颗 handler 没装上"。值只认 thread 与 edt（两档各走一条线程路，判读见 ForensicFlags.crash）。
            providers.gradleProperty("crash").orNull?.let { "--crash=$it" },
            // 「复制图片」那颗种子（第五十四批）。同一颗纪律第五次：**判读层与消费点接好了也不算通**，
            // 这一跳漏了的话 `-Pcopy=1` 就是"不报错也不做事"，而"剪贴板是空的"会被读成"那一枚写口坏了"。
            // 值只判开不开，它顺带把详情浮层摆出来（那一枚按钮在浮层里）。
            providers.gradleProperty("copy").orNull?.let { "--copy=$it" },
            // 「存到本机」那颗种子（第八十五批）。同一颗纪律第六次：**判读层与消费点接好了也不算通**，
            // 这一跳漏了的话 `-Psave=1` 就是"不报错也不做事"，而"盘上没多那颗文件"会被读成"落盘那层坏了"。
            // 值只判开不开，它顺带把详情浮层摆出来（那一枚按钮也在浮层里，与 -Pcopy 同一格）。
            providers.gradleProperty("save").orNull?.let { "--save=$it" },
            providers.gradleProperty("autofav").orNull?.let { "--autofav=$it" },
            providers.gradleProperty("favcheck").orNull?.let { "--favcheck=$it" },
        ).filterNotNull() + cookieSeeds
    )
}

// S0-5：中文 IME 探针窗（判据只能由真 IME 会话给出，所以单独一个可启动入口）
tasks.register<JavaExec>("ime") {
    group = "probe"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass = "com.venera.desktop.ImeProbeKt"
}

// S0-6a：不引 window-styler，自己用 FFM 调 DWM 设 Mica（要拿 HWND ⇒ 两个 add-exports 是探针的一部分）
tasks.register<JavaExec>("mica") {
    group = "probe"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass = "com.venera.desktop.MicaProbeKt"
    workingDir = rootDir
    jvmArgs(
        "--add-exports", "java.desktop/sun.awt=ALL-UNNAMED",
        "--add-exports", "java.desktop/sun.awt.windows=ALL-UNNAMED",
        "--enable-native-access=ALL-UNNAMED",
    )
}
