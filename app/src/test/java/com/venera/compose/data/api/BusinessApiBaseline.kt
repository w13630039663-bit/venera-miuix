package com.venera.compose.data.api

/**
 * [BusinessApiBoundaryTest] 的基线与归属表（B0 落地当天实测的**全量**，此后只许缩短）。
 *
 * 值有两种形态：
 * - 以 `B` 开头 ⇒ 归那一批摘掉（`B0`…`B7'`，见 `docs/rounds/business-api-boundary-2026-10.md` §三 的批次表）。
 * - 否则 ⇒ **解锁条件原文**，必须写清「解锁需要谁做什么」并带可复核的锚（`文件.kt:行号` / `W1..W6` /
 *   `FREEZE-STATEMENT`）。断言 F 逐条检查这一点，所以「先登记了再说」蒙不过去。
 *
 * 键有两种形态：`"Symbol"` 是符号级默认归属；`"path#Symbol"` 是**站点级覆写**，优先级更高
 * （冻结屏、导航保护域、跨侧凭据、以及「住在 feature 里的实现类」都走覆写）。
 *
 * 表本体在本文件末尾（`DEFAULT_ATTRIBUTION` / `SITE_OVERRIDE` / 四张基线）—— 顶层属性的初始化
 * 按声明顺序，`ATTRIBUTION` 必须排在它们之后，否则编译器报「must be initialized」。
 */

private val FROZEN_SCREEN =
    "解锁条件：契约与适配器已就绪（B0 已建 data/api），只差 FREEZE-STATEMENT.md:8-15 的一次点名豁免 + 一次真机回归。" +
        "入场券已逐颗核实：同一行的取用表达式替换、不加减 remember、不改 collectAsState 的订阅对象，" +
        "且 ContentGuardManager.kt:511 / ComicSourceManager.kt:1448 / VeneraPreferences.kt:707 全部在构造时归一 applicationContext"

private val INFRA_FACING_PAGE =
    "解锁条件：这颗文件本身就是操作基础设施的界面（feature/sourcemanage 整域，入口见 feature/sourcemanage/ComicSourceScreen.kt:80）。" +
        "它实吃 ComicSourceManager 44 个公开成员里的 32 个 ⇒ 契约宽度≈实现宽度，窄接口在这颗上不成立（方案 §七.1）；与 Part B 的 W2 同批"

private val FEATURE_RESIDENT_IMPL =
    "解锁条件：FavoriteImagesManager 是住在 feature 包里的实现类，返回类型 FavoriteImageItem 声明在 feature/favoriteimages/FavoriteImagesManager.kt:92" +
        "⇒ data/api 的契约必须 import feature.，而那正是 W3 LayeringEdgeTest 要断言禁止的边；与 W1 同批搬家后本条自动可摘"

private val CROSS_SIDE_CREDENTIAL =
    "解锁条件：凭据类住 gallery/data，漫画侧建契约 = 新增 feature→gallery import（现存点位 feature/sourcemanage/GalleryAccountCard.kt:85、" +
        "feature/sourcemanage/SauceNaoKeyCard.kt:65、feature/settings/PreferredIpSpeedTestScreen.kt:89）。" +
        "审计 :39 判画廊隔离已双向被打破，:43 的中性基建豁免只给了 components / ui.tokens / data/platform，不含凭据"

private val DEFAULT_ATTRIBUTION: Map<String, String> = mapOf(
    "ContentGuardManager" to "B1（契约 data/api/ContentGuardApi.kt 的 ContentGuard + GuardRuleBook）",
    "VeneraPreferences" to "B2（契约 data/api/PreferencesApi.kt 三颗分域 + 画廊侧 GalleryPreferences）",
    "AndroidKeyValueStore" to "B2（漫画侧两颗 VM 的自建存储改走偏好契约；画廊那颗见覆写）",
    "ComicSourceManager" to "B3（契约 data/api/SourceApi.kt 的 ComicContentApi + SourceCatalog）",
    "JsComicSource" to "B3（穿过接口拿实现那一处收进 ComicContentApi.tagSuggestionKeyword；sourcemanage 两颗见覆写）",
    "HistoryDao" to "B4（契约 data/api/LibraryApi.kt 的 ReadingHistory）",
    "ReadingStatsManager" to "B4（同文件的 ReadingStats）",
    "LocalFavoritesManager" to "B5（契约 FavoriteLibrary，随 B5 与守卫同批落地）",
    "DownloadManager" to "B5（契约 OfflineLibrary，同上）",
    "LocalComicManager" to "B5（契约 OfflineLibrary，同上）",
    "VeneraNetworkClient" to "B6（契约 data/api/NetworkApi.kt 的 NetworkHygiene）",
    "HostCircuitBreaker" to "B6（同上；漫画侧今天已有 ComicSourceManager.kt:1276 那层包装的先例）",
    "YandeReClient" to "B7'（画廊契约 gallery/data/GalleryPorts.kt 的 GalleryBoards + GalleryArtistDirectory）",
    "GelbooruClient" to "B7'",
    "SafebooruClient" to "B7'",
    "DanbooruArtistClient" to "B7'",
    "PixivClient" to "B7'",
    "SauceNaoClient" to "B7'",
    "GalleryArtistProbeClient" to "B7'",
    "GalleryArtistAvatarStore" to "B7'",
    "GalleryFavoritesStore" to "B7'（notice/consumeNotice 是一次性消费槽位，必须交回同一个 store 实例的流）",
    "GalleryArtistFollowsStore" to "B7'",
    "GalleryTagDictionary" to "B7'",
    "GalleryTagCategories" to "B7'",
    "GalleryFeedSource" to "B7'（只做装配收口：它今天已是聚合端口、且 gallery/domain/GalleryFeedSource.kt:3 自己 import Context，方案 §七.9）",
    "TagTranslationManager" to "解锁条件：data/tags/TagTranslationManager.kt 是另一条并行线在飞的脏文件（git status 实测 M），碰它 = 抢改；现存点位 feature/ComicDetailScreen.kt:146 与 feature/SearchViewModel.kt:81。那颗文件变干净后的独立批",
    "ChineseVariantConverter" to "解锁条件：与 TagTranslationManager 同域（简繁表已在 assets/opencc.txt，审计 :238 已判该域归位零收益）；点位 feature/SearchViewModel.kt:78，随同一批做",
    "FollowUpdatesRepository" to "解锁条件：让给 Part B 的 W6（审计 :233 已定「构造参数注入 + desktop/build.gradle.kts:57-61 三行 exclude 一行不撤」，边本身在 data/db/FollowUpdatesRepository.kt:4,44）；本轮再包一层会让同一个 DAO 成员被转发两次",
    "BackupManager" to "解锁条件：sync/BackupManager.kt:19-20 同时持画廊两颗 store 与 FavoriteImagesManager ⇒ 端口要么跨侧要么 import feature；且备份是用户数据面，审计 :241 已判它的画廊解耦是产品裁决；点位 feature/SyncBackupScreen.kt:58",
    "WebDavSyncManager" to "解锁条件：同 BackupManager（点位 feature/SyncBackupScreen.kt:57，同一颗文件；成员 7 枚与契约一一对应，收口无收效）",
    "FavoriteImagesManager" to FEATURE_RESIDENT_IMPL,
    "FavoriteImagesStore" to FEATURE_RESIDENT_IMPL,
    "ReadingStatsStore" to FEATURE_RESIDENT_IMPL,
    "ComicLinkResolver" to "解锁条件两段：① feature/Navigation.kt 属导航保护域（FREEZE-STATEMENT.md:32）需重新评审；② 硬技术前提 = ComicLinkResolver.Outcome 是嵌套在吃 Context 的类体里的 sealed interface（source/ComicLinkResolver.kt:3,23,25-36），而 Navigation.kt:313 逐条消费那四档 ⇒ 契约签名要它必须先做一次 W1 同族的类型搬家",
    "ComicListPreferences" to "解锁条件：它的 registerListener 签名带 SharedPreferences.OnSharedPreferenceChangeListener（data/prefs/ComicListPreferences.kt:27），契约化要先断掉那个 Android 类型；范围内仅 components/ComicListPresentation.kt:15 一处引用，不构成散落多处",
    "AppLogManager" to "解锁条件：feature/LogViewerScreen.kt:27 那颗屏就是来看日志的界面（engine/AppLogManager 是日志汇），与源管理页同一判据（INFRA_FACING_PAGE 那条）",
    "GelbooruAccount" to CROSS_SIDE_CREDENTIAL,
    "SauceNaoAccount" to CROSS_SIDE_CREDENTIAL,
)

/**
 * 站点级覆写。
 *
 * 前 15 条就是用户裁决「甲」长期留下的那 15 处冻结屏 / 导航保护域点位
 * （13 处在 7 颗 FROZEN 屏 + `Navigation.kt:286,491`），逐条写明解锁条件。
 */
private val SITE_OVERRIDE: Map<String, String> = mapOf(
    // ── 冻结屏（FREEZE-STATEMENT.md:8-15）──
    "feature/explore/UnifiedExploreScreen.kt#ComicSourceManager" to FROZEN_SCREEN,
    "feature/explore/UnifiedExploreScreen.kt#ContentGuardManager" to FROZEN_SCREEN,
    "feature/explore/SourceSectionScreen.kt#ComicSourceManager" to FROZEN_SCREEN,
    "feature/explore/SourceSectionScreen.kt#ContentGuardManager" to FROZEN_SCREEN,
    "feature/HomeScreen.kt#ComicSourceManager" to FROZEN_SCREEN,
    "feature/HomeScreen.kt#ContentGuardManager" to FROZEN_SCREEN,
    "feature/SearchScreen.kt#ContentGuardManager" to FROZEN_SCREEN,
    "feature/SearchScreen.kt#VeneraPreferences" to FROZEN_SCREEN,
    "feature/FavoritesScreen.kt#ContentGuardManager" to FROZEN_SCREEN,
    "feature/HistoryScreen.kt#ContentGuardManager" to FROZEN_SCREEN,
    "feature/NetworkFavoritesScreen.kt#ContentGuardManager" to FROZEN_SCREEN,
    // ── 导航保护域（FREEZE-STATEMENT.md:32 只管 Tab 枚举顺序 / 路由映射 / 顶栏齿轮入口这三项）──
    "feature/Navigation.kt#VeneraPreferences" to
        "解锁条件：B2 的 AppearancePreferences 已覆盖 navigationBarStyle 与 startPage，本行只差同文件改动的一次重新评审" +
            "（FREEZE-STATEMENT.md:32）；不改 Tab 枚举顺序、路由映射表与顶栏齿轮入口",
    "feature/Navigation.kt#ComicLinkResolver" to
        "解锁条件两段：① 同文件改动需重新评审（FREEZE-STATEMENT.md:32）；② 硬技术前提 = Outcome 是嵌套在吃 Context 的类里的 " +
            "sealed interface（source/ComicLinkResolver.kt:25-36），须与 W1 同批搬进 source/model 后本行才有契约可引",
    // ── 跨侧凭据：漫画侧长期、画廊内两处顺路收 ──
    "gallery/ui/GallerySearchViewModel.kt#GelbooruAccount" to "B7'（画廊内顺路收：GalleryPorts 上一枚派生的「凭据已配置」判据，把两处变一处，账号类留在画廊 infra，方案 §七.8）",
    "gallery/ui/GalleryForYouViewModel.kt#GelbooruAccount" to "B7'（同上）",
    "gallery/ui/GalleryReverseViewModel.kt#SauceNaoAccount" to "B7'（同上）",
    "gallery/ui/GallerySearchViewModel.kt#AndroidKeyValueStore" to "B7'（画廊搜索缓存随画廊侧收口，不并进漫画侧 B2）",
    // ── 住在 feature 里的实现类：整文件长期 ──
    "feature/sourcemanage/ComicSourceScreen.kt#ComicSourceManager" to INFRA_FACING_PAGE,
    "feature/sourcemanage/ComicSourceViewModel.kt#ComicSourceManager" to INFRA_FACING_PAGE,
    "feature/sourcemanage/ComicSourceViewModel.kt#JsComicSource" to INFRA_FACING_PAGE,
    "MainActivity.kt#JsComicSource" to
        "B3（MainActivity.kt:97 那处只是诊断日志里数了一句 `is JsComicSource`，不是依赖；:90 那处取用改引契约后本行自动消失）",
    "feature/sourcemanage/ComicSourceViewModel.kt#VeneraNetworkClient" to
        "B6（唯一的例外：:569-571 那笔裸 okhttp3 请求绕过了 cookie jar / 限流 / 熔断 / 缓存四件套，" +
            "是审计 §二.8 里可指认的缺陷，用 HttpTextFetch 外科手术收掉，不是架构偏好）",
    "feature/favoriteimages/FavoriteImagesManager.kt#LocalFavoritesManager" to FEATURE_RESIDENT_IMPL,
    "feature/favoriteimages/FavoriteImagesManager.kt#ReadingStatsManager" to FEATURE_RESIDENT_IMPL,
    "feature/favoriteimages/FavoriteImagesManager.kt#FavoriteImagesManager" to
        "解锁条件：这颗文件自己就是那颗实现类（class FavoriteImagesManager 声明在 feature/favoriteimages/FavoriteImagesManager.kt:132），" +
            "引用自己的名字不算穿透；随 W1 同批把它搬出 feature/ 后本条自动消失",
)

/** A ——「UI import 业务实现类」的全量基线：56 颗文件 / 125 条。 */
internal val BASELINE_IMPORT: Map<String, Set<String>> = mapOf(
    "components/ComicCardContextMenu.kt" to setOf("ContentGuardManager", "LocalFavoritesManager"),
    "components/ComicListPresentation.kt" to setOf("ComicListPreferences"),
    "feature/ComicDetailScreen.kt" to setOf("ContentGuardManager", "TagTranslationManager", "VeneraPreferences"),
    "feature/ComicDetailViewModel.kt" to setOf("ComicSourceManager", "HistoryDao", "LocalFavoritesManager", "VeneraPreferences"),
    "feature/DownloadScreen.kt" to setOf("DownloadManager"),
    "feature/FavoriteImagesScreen.kt" to setOf("FavoriteImagesManager"),
    "feature/FavoritesViewModel.kt" to setOf("LocalFavoritesManager", "VeneraPreferences"),
    "feature/FollowUpdatesViewModel.kt" to setOf("FollowUpdatesRepository", "VeneraPreferences"),
    "feature/HistoryViewModel.kt" to setOf("HistoryDao", "LocalFavoritesManager"),
    "feature/HomeScreen.kt" to setOf("ComicSourceManager"),
    "feature/HomeViewModel.kt" to setOf("AndroidKeyValueStore", "ComicSourceManager", "HistoryDao", "LocalFavoritesManager", "ReadingStatsManager"),
    "feature/LocalComicScreen.kt" to setOf("DownloadManager", "LocalComicManager"),
    "feature/LogViewerScreen.kt" to setOf("AppLogManager"),
    "feature/Navigation.kt" to setOf("ComicLinkResolver", "VeneraPreferences"),
    "MainActivity.kt" to setOf("VeneraPreferences"),
    "feature/NetworkFavoritesViewModel.kt" to setOf("ComicSourceManager"),
    "feature/SearchScreen.kt" to setOf("ContentGuardManager", "VeneraPreferences"),
    "feature/SearchViewModel.kt" to setOf("AndroidKeyValueStore", "ChineseVariantConverter", "ComicSourceManager", "TagTranslationManager"),
    "feature/SettingsHost.kt" to setOf("VeneraPreferences"),
    "feature/StatsScreen.kt" to setOf("ReadingStatsManager"),
    "feature/SyncBackupScreen.kt" to setOf("BackupManager", "WebDavSyncManager"),
    "feature/VeneraTheme.kt" to setOf("VeneraPreferences"),
    "feature/explore/SourceSectionScreen.kt" to setOf("ComicSourceManager", "ContentGuardManager"),
    "feature/explore/UnifiedExploreScreen.kt" to setOf("ComicSourceManager", "ContentGuardManager"),
    "feature/favoriteimages/FavoriteImagesManager.kt" to setOf("FavoriteImagesStore", "LocalFavoritesManager", "ReadingStatsManager", "ReadingStatsStore"),
    "feature/settings/AboutSection.kt" to setOf("VeneraPreferences"),
    "feature/settings/AppSettings.kt" to setOf("DownloadManager", "VeneraNetworkClient", "VeneraPreferences"),
    "feature/settings/AppearanceSettings.kt" to setOf("VeneraPreferences"),
    "feature/settings/BlockingSettings.kt" to setOf("ContentGuardManager", "VeneraPreferences"),
    "feature/settings/ExploreSettings.kt" to setOf("ComicSourceManager", "VeneraPreferences"),
    "feature/settings/GallerySettings.kt" to setOf("VeneraPreferences"),
    "feature/settings/LocalFavoritesSettings.kt" to setOf("LocalFavoritesManager", "VeneraPreferences"),
    "feature/settings/NetworkSettings.kt" to setOf("VeneraNetworkClient", "VeneraPreferences"),
    "feature/settings/PreferredIpSettings.kt" to setOf("VeneraPreferences"),
    "feature/settings/PreferredIpSpeedTestScreen.kt" to setOf("ComicSourceManager", "GelbooruAccount", "VeneraPreferences"),
    "feature/settings/ReaderSettings.kt" to setOf("VeneraPreferences"),
    "feature/settings/SettingsComponents.kt" to setOf("VeneraPreferences"),
    "feature/settings/SettingsHome.kt" to setOf("VeneraPreferences"),
    "feature/settings/SettingsQuoteCard.kt" to setOf("VeneraPreferences"),
    "feature/settings/UpdateCheckUi.kt" to setOf("VeneraPreferences"),
    "feature/sourcemanage/ComicSourceScreen.kt" to setOf("ComicSourceManager", "JsComicSource"),
    "feature/sourcemanage/ComicSourceViewModel.kt" to setOf("ComicSourceManager", "JsComicSource"),
    "feature/sourcemanage/GalleryAccountCard.kt" to setOf("GelbooruAccount"),
    "feature/sourcemanage/SauceNaoKeyCard.kt" to setOf("SauceNaoAccount"),
    "gallery/ui/GalleryArtistProfileScreen.kt" to setOf("ContentGuardManager", "DanbooruArtistClient", "GalleryArtistFollowsStore", "GelbooruClient", "PixivClient", "SafebooruClient", "VeneraPreferences", "YandeReClient"),
    "gallery/ui/GalleryArtistRows.kt" to setOf("DanbooruArtistClient", "GalleryArtistAvatarStore", "GalleryArtistFollowsStore", "GalleryArtistProbeClient", "PixivClient", "YandeReClient"),
    "gallery/ui/GalleryDailyScreen.kt" to setOf("ContentGuardManager", "HostCircuitBreaker", "VeneraPreferences"),
    "gallery/ui/GalleryFavoritesBody.kt" to setOf("ContentGuardManager", "GalleryFavoritesStore", "VeneraPreferences"),
    "gallery/ui/GalleryForYouViewModel.kt" to setOf("GalleryFavoritesStore", "GelbooruAccount", "GelbooruClient", "SafebooruClient", "YandeReClient"),
    "gallery/ui/GalleryInfoSheet.kt" to setOf("ContentGuardManager"),
    "gallery/ui/GalleryPostScreen.kt" to setOf("ContentGuardManager", "GalleryFavoritesStore", "GelbooruClient", "SafebooruClient", "VeneraPreferences", "YandeReClient"),
    "gallery/ui/GalleryReverseResults.kt" to setOf("SauceNaoClient"),
    "gallery/ui/GalleryReverseViewModel.kt" to setOf("SauceNaoAccount", "SauceNaoClient"),
    "gallery/ui/GalleryScreen.kt" to setOf("ContentGuardManager", "GalleryArtistFollowsStore", "GalleryFavoritesStore", "HostCircuitBreaker", "VeneraPreferences"),
    "gallery/ui/GallerySearchViewModel.kt" to setOf("AndroidKeyValueStore", "GelbooruAccount", "GelbooruClient", "SafebooruClient", "YandeReClient"),
    "reader/ChapterCommentsSheet.kt" to setOf("ComicSourceManager"),
    "reader/VeneraReaderScreen.kt" to setOf("ComicSourceManager", "HistoryDao", "VeneraPreferences"),
)

/** B ——「UI 直连取单例」的全量基线：51 颗文件 / 150 处（值里的数字是落地当天的行号，用于对账）。 */
internal val BASELINE_GET_INSTANCE: Map<String, Set<String>> = mapOf(
    "components/ComicCardContextMenu.kt" to setOf("ContentGuardManager:93", "LocalFavoritesManager:66"),
    "feature/ComicDetailScreen.kt" to setOf("ContentGuardManager:151", "ContentGuardManager:372", "DownloadManager:1643", "DownloadManager:1743", "TagTranslationManager:146", "VeneraPreferences:246"),
    "feature/ComicDetailViewModel.kt" to setOf("ComicSourceManager:174", "DownloadManager:860", "HistoryDao:177", "LocalFavoritesManager:175", "VeneraPreferences:176"),
    "feature/DownloadScreen.kt" to setOf("DownloadManager:67"),
    "feature/FavoriteImagesScreen.kt" to setOf("FavoriteImagesManager:154", "FavoriteImagesManager:430"),
    "feature/FavoritesScreen.kt" to setOf("ContentGuardManager:795", "ContentGuardManager:913"),
    "feature/FavoritesViewModel.kt" to setOf("LocalFavoritesManager:45", "VeneraPreferences:46"),
    "feature/FollowUpdatesScreen.kt" to setOf("ContentGuardManager:219"),
    "feature/FollowUpdatesViewModel.kt" to setOf("FollowUpdatesRepository:19", "LocalFavoritesManager:71", "LocalFavoritesManager:78", "VeneraPreferences:20"),
    "feature/HistoryScreen.kt" to setOf("ContentGuardManager:269"),
    "feature/HistoryViewModel.kt" to setOf("HistoryDao:22", "LocalFavoritesManager:23"),
    "feature/HomeScreen.kt" to setOf("ComicSourceManager:134", "ContentGuardManager:610", "ContentGuardManager:796"),
    "feature/HomeViewModel.kt" to setOf("ComicSourceManager:201", "ContentGuardManager:207", "DownloadManager:112", "HistoryDao:79", "LocalComicManager:110", "LocalFavoritesManager:78", "ReadingStatsManager:209"),
    "feature/LocalComicScreen.kt" to setOf("DownloadManager:102", "LocalComicManager:94"),
    "feature/Navigation.kt" to setOf("ComicLinkResolver:286", "VeneraPreferences:491"),
    "feature/NetworkFavoritesScreen.kt" to setOf("ContentGuardManager:653"),
    "MainActivity.kt" to setOf("ComicSourceManager:90", "VeneraPreferences:167"),
    "feature/NetworkFavoritesViewModel.kt" to setOf("ComicSourceManager:54"),
    "feature/SearchScreen.kt" to setOf("ContentGuardManager:136", "VeneraPreferences:138"),
    "feature/SearchViewModel.kt" to setOf("ChineseVariantConverter:78", "ComicSourceManager:80", "ContentGuardManager:82", "TagTranslationManager:81"),
    "feature/SettingsHost.kt" to setOf("VeneraPreferences:136"),
    "feature/StatsScreen.kt" to setOf("ReadingStatsManager:72"),
    "feature/SyncBackupScreen.kt" to setOf("BackupManager:58", "WebDavSyncManager:57"),
    "feature/VeneraTheme.kt" to setOf("VeneraPreferences:51"),
    "feature/explore/SourceSectionScreen.kt" to setOf("ComicSourceManager:104", "ContentGuardManager:105"),
    "feature/explore/UnifiedExploreScreen.kt" to setOf("ComicSourceManager:123", "ContentGuardManager:124"),
    "feature/favoriteimages/FavoriteImagesManager.kt" to setOf("LocalFavoritesManager:351"),
    "feature/settings/AppSettings.kt" to setOf("DownloadManager:369", "VeneraNetworkClient:42"),
    "feature/settings/BlockingSettings.kt" to setOf("ContentGuardManager:22", "ContentGuardManager:70", "VeneraPreferences:23"),
    "feature/settings/ExploreSettings.kt" to setOf("ComicSourceManager:23", "VeneraPreferences:22"),
    "feature/settings/LocalFavoritesSettings.kt" to setOf("LocalFavoritesManager:15"),
    "feature/settings/NetworkSettings.kt" to setOf("VeneraNetworkClient:148"),
    "feature/settings/PreferredIpSpeedTestScreen.kt" to setOf("ComicSourceManager:87", "GelbooruAccount:89"),
    "feature/settings/SettingsComponents.kt" to setOf("VeneraPreferences:294"),
    "feature/settings/SettingsHome.kt" to setOf("VeneraPreferences:105"),
    "feature/settings/UpdateCheckUi.kt" to setOf("VeneraPreferences:62"),
    "feature/sourcemanage/ComicSourceViewModel.kt" to setOf("ComicSourceManager:47", "VeneraNetworkClient:569"),
    "feature/sourcemanage/GalleryAccountCard.kt" to setOf("GelbooruAccount:85"),
    "feature/sourcemanage/SauceNaoKeyCard.kt" to setOf("SauceNaoAccount:65"),
    "gallery/ui/GalleryArtistProfileScreen.kt" to setOf("ContentGuardManager:146", "DanbooruArtistClient:552", "DanbooruArtistClient:561", "GalleryArtistFollowsStore:149", "GelbooruClient:174", "PixivClient:573", "SafebooruClient:176", "VeneraPreferences:148", "YandeReClient:172", "YandeReClient:539"),
    "gallery/ui/GalleryArtistRows.kt" to setOf("DanbooruArtistClient:164", "DanbooruArtistClient:165", "DanbooruArtistClient:542", "DanbooruArtistClient:543", "GalleryArtistAvatarStore:494", "GalleryArtistFollowsStore:238", "GalleryArtistProbeClient:521", "PixivClient:175", "PixivClient:510", "YandeReClient:163", "YandeReClient:541"),
    "gallery/ui/GalleryCardCaption.kt" to setOf("GalleryTagDictionary:102"),
    "gallery/ui/GalleryDailyScreen.kt" to setOf("ContentGuardManager:85", "GalleryFeedSource:83", "VeneraPreferences:88", "VeneraPreferences:95"),
    "gallery/ui/GalleryFavoritesBody.kt" to setOf("ContentGuardManager:95", "GalleryFavoritesStore:93", "VeneraPreferences:99"),
    "gallery/ui/GalleryForYouViewModel.kt" to setOf("GalleryFavoritesStore:431", "GelbooruAccount:183", "GelbooruClient:479", "SafebooruClient:481", "YandeReClient:480"),
    "gallery/ui/GalleryInfoSheet.kt" to setOf("ContentGuardManager:192"),
    "gallery/ui/GalleryPostScreen.kt" to setOf("ContentGuardManager:194", "GalleryFavoritesStore:416", "GalleryTagCategories:379", "GalleryTagDictionary:369", "GelbooruClient:670", "SafebooruClient:671", "VeneraPreferences:199", "YandeReClient:669"),
    "gallery/ui/GalleryReverseViewModel.kt" to setOf("SauceNaoAccount:58", "SauceNaoClient:57"),
    "gallery/ui/GalleryScreen.kt" to setOf("ContentGuardManager:230", "GalleryArtistFollowsStore:596", "GalleryFavoritesStore:254", "GalleryFeedSource:225", "VeneraPreferences:1520", "VeneraPreferences:1600", "VeneraPreferences:239", "VeneraPreferences:278", "VeneraPreferences:282", "VeneraPreferences:283"),
    "gallery/ui/GallerySearchViewModel.kt" to setOf("GelbooruAccount:287", "GelbooruClient:1046", "GelbooruClient:1108", "SafebooruClient:1048", "SafebooruClient:1110", "YandeReClient:1047", "YandeReClient:1061", "YandeReClient:1109"),
    "reader/ChapterCommentsSheet.kt" to setOf("ComicSourceManager:44"),
    "reader/VeneraReaderScreen.kt" to setOf("ComicSourceManager:1701", "ComicSourceManager:183", "ComicSourceManager:1980", "ComicSourceManager:2056", "ComicSourceManager:2145", "HistoryDao:446", "ReadingStatsManager:306", "VeneraPreferences:182"),
)

/** C ——「把实现类当类型用」的全量基线：31 颗文件 / 45 条。 */
internal val BASELINE_TYPE_SITE: Map<String, Set<String>> = mapOf(
    "components/ComicListPresentation.kt" to setOf("ComicListPreferences"),
    "feature/ComicDetailScreen.kt" to setOf("ContentGuardManager"),
    "feature/HomeViewModel.kt" to setOf("AndroidKeyValueStore", "ComicSourceManager"),
    "feature/LogViewerScreen.kt" to setOf("AppLogManager"),
    "feature/Navigation.kt" to setOf("ComicLinkResolver"),
    "feature/SearchViewModel.kt" to setOf("AndroidKeyValueStore", "ComicSourceManager", "JsComicSource"),
    "feature/explore/SourceSectionScreen.kt" to setOf("ContentGuardManager"),
    "feature/explore/UnifiedExploreScreen.kt" to setOf("ComicSourceManager", "ContentGuardManager"),
    "feature/favoriteimages/FavoriteImagesManager.kt" to setOf("FavoriteImagesManager", "FavoriteImagesStore", "ReadingStatsManager", "ReadingStatsStore"),
    "feature/settings/AboutSection.kt" to setOf("VeneraPreferences"),
    "feature/settings/AppSettings.kt" to setOf("VeneraPreferences"),
    "feature/settings/AppearanceSettings.kt" to setOf("VeneraPreferences"),
    "feature/settings/GallerySettings.kt" to setOf("VeneraPreferences"),
    "feature/settings/LocalFavoritesSettings.kt" to setOf("VeneraPreferences"),
    "feature/settings/NetworkSettings.kt" to setOf("HostCircuitBreaker", "VeneraPreferences"),
    "feature/settings/PreferredIpSettings.kt" to setOf("VeneraPreferences"),
    "feature/settings/PreferredIpSpeedTestScreen.kt" to setOf("VeneraPreferences"),
    "feature/settings/ReaderSettings.kt" to setOf("VeneraPreferences"),
    "feature/settings/SettingsHome.kt" to setOf("VeneraPreferences"),
    "feature/settings/SettingsQuoteCard.kt" to setOf("VeneraPreferences"),
    "feature/sourcemanage/ComicSourceScreen.kt" to setOf("ComicSourceManager"),
    "feature/sourcemanage/ComicSourceViewModel.kt" to setOf("ComicSourceManager", "JsComicSource"),
    "MainActivity.kt" to setOf("JsComicSource"),
    "feature/sourcemanage/GalleryAccountCard.kt" to setOf("GelbooruAccount"),
    "feature/sourcemanage/SauceNaoKeyCard.kt" to setOf("SauceNaoAccount"),
    "gallery/ui/GalleryDailyScreen.kt" to setOf("GalleryFeedSource", "HostCircuitBreaker"),
    "gallery/ui/GalleryForYouViewModel.kt" to setOf("GelbooruClient", "SafebooruClient", "YandeReClient"),
    "gallery/ui/GalleryHomeSections.kt" to setOf("GalleryTagDictionary"),
    "gallery/ui/GalleryReverseResults.kt" to setOf("SauceNaoClient"),
    "gallery/ui/GallerySearchArea.kt" to setOf("GalleryTagDictionary"),
    "gallery/ui/GalleryScreen.kt" to setOf("HostCircuitBreaker"),
    "gallery/ui/GallerySearchViewModel.kt" to setOf("AndroidKeyValueStore", "GelbooruClient", "SafebooruClient", "YandeReClient"),
    "reader/VeneraReaderScreen.kt" to setOf("ComicSourceManager", "FavoriteImagesManager"),
)

/**
 * E 的存量名单：B 的出处文件全集 + 四棵 UI 目录之外的 `MainActivity.kt`。
 * 这批之外再出现任何 `*.getInstance` 出处 ⇒ 立刻红（要开口就加契约）。
 */
internal val E_SEED: Set<String> = BASELINE_GET_INSTANCE.keys.toSet() + "MainActivity.kt"

// D ——「非 getInstance 的直连」五张名单（行号是这几颗文件的稳定坐标，逐条登记）
internal val BASELINE_KEY_VALUE_STORE: Map<String, Set<Int>> = mapOf(
    "feature/HomeViewModel.kt" to setOf(137),
    "feature/SearchViewModel.kt" to setOf(72),
    "gallery/ui/GallerySearchViewModel.kt" to setOf(1145),
)
internal val BASELINE_BREAKER: Map<String, Set<Int>> = mapOf(
    "feature/settings/NetworkSettings.kt" to setOf(70),
    "gallery/ui/GalleryDailyScreen.kt" to setOf(108),
    "gallery/ui/GalleryScreen.kt" to setOf(401),
)
internal val BASELINE_RAW_OKHTTP: Map<String, Set<Int>> = mapOf(
    "feature/sourcemanage/ComicSourceViewModel.kt" to setOf(570),
)
internal val BASELINE_PREFERRED_IP: Map<String, Set<Int>> = mapOf(
    "feature/settings/PreferredIpSettings.kt" to setOf(242, 248),
    "feature/settings/PreferredIpSpeedTestScreen.kt" to setOf(122, 126, 153),
)
internal val BASELINE_STORAGE_ROOT: Map<String, Set<Int>> = mapOf(
    "feature/settings/AppSettings.kt" to setOf(44, 55, 71, 72, 90, 315, 320, 321, 336, 341, 359, 361, 364, 365),
    "feature/settings/GallerySettings.kt" to setOf(91),
)

/** 符号默认归属 + 站点覆写；必须排在两者之后（顶层属性按声明顺序初始化）。 */
internal val ATTRIBUTION: Map<String, String> = DEFAULT_ATTRIBUTION + SITE_OVERRIDE
