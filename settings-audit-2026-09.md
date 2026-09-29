# 设置与设置二级页 · 功能审计（2026-09-19）

> 分支 `compose-migration` ｜ 基线 commit `4e9ffbc` ｜ 只读审计，未改动任何代码
> 证据来源：`app/src/main/java/com/venera/compose/feature/settings/`、`data/prefs/`、`security/guard/`、
> `reader/`、`data/network/`，以及仓库内**完整原版 Flutter 源码** `.reference/flutter-master/`（对照精确到行）。
> 状态图例：✅ 真实生效 ｜ 🚧 占位（`UnsupportedSetting` 灰行；此件已于 2026-09-30 整体删除，见文末追加）｜ ⚠️ 名不副实/降级/僵尸 ｜ ❌ 原版有而我们连占位都没挂

## 0. 总览

| 指标 | 数值 |
|---|---|
| 设置树结构 | 首页（内联「关于」）+ 7 个分区页：探索 / 屏蔽与过滤 / 阅读 / 外观 / 本地收藏 / 应用 / 网络 |
| 真正生效的可写设置 | **17 项** |
| `UnsupportedSetting` 占位调用点 | **54 处**（`BlockingSettings.kt:34` 在 forEach 内渲染 3 行）⇒ 实际禁用行 **56 条** |
| 另有「看着能用其实 disabled」的开关 | 2 条（`LocalFavoritesSettings.kt:33`、`ReaderSettings.kt:59`） |
| 僵尸键（有存储/有 setter，无人读或无人调） | 6 个（见 §3） |
| 原版有、我们**连占位都没有**的隐形缺口 | 6 处（见 §2） |
| 结构性缺陷（双入口不同步 / 入口不可达 / 文案不实） | 7 处（见 §4） |

入口路径：齿轮只在**首页**顶栏（`feature/HomeScreen.kt:464` → `feature/Navigation.kt:306-308`，路由 `Navigation.kt:386`）。
其余 4 个主 Tab 无入口 —— 这是 `FREEZE-STATEMENT.md` 记录的信息架构决策（设置=低频收口），改动需重新评审。

---

## 1. 全景表

### 1.1 关于（`settings/AboutSection.kt`，内联在设置首页顶部 `SettingsHome.kt:158`）

| 项 | 位置 | 状态 | 实际写入 / 消费者 |
|---|---|---|---|
| 参考项目代码仓库 / 原始项目 / 上游发布频道 | `:41` `:42` `:43` | ✅ | `Intent.ACTION_VIEW`，无持久化 |
| 检查更新 | `:39` | 🚧 | 无写操作 |
| 启动时检查更新 | `:40` | 🚧 | 无写操作 |

### 1.2 外观（`settings/AppearanceSettings.kt`）

| 项 | 位置 | 状态 | 实际写入 / 消费者 |
|---|---|---|---|
| 主题模式 | `:52` | ✅ | `pref_theme_mode` → `feature/VeneraTheme.kt:46` |
| 界面风格（Miuix／MD3 壁纸动态取色） | `:56` | ✅ | `pref_appearance_style` → `VeneraTheme.kt:47`（颜色+形状+字号整套）。**原版没有此项**，属我们真新增 |
| 导航栏样式（含 Liquid Glass） | `:70` | ✅ | `pref_navigation_bar_style` → `feature/Navigation.kt:164,167`（Android 13+ 生效）。选项语义与原版不同（原版 classic/floating/frosted） |
| 标签译文显示 | `:82` | ✅ | `pref_tag_translation_mode` → `data/tags/TagDisplay.kt:28`；仅影响显示，不改发往源的词。**原版没有** |
| 主题颜色 | — | ❌ | 原版 `appearance.dart:152`，7 色（system/red/pink/purple/green/orange/blue，默认值 `appdata.dart:177`）。Compose 全树无主题色选择器 |
| 返回动画样式 | `:99` | 🚧 | 路由已内置预返回滑动转场 |
| 沉浸式背景 | `:101` | 🚧 | 关闭／氛围／壁纸三态均无 |
| 自定义壁纸 | `:102` | 🚧 | 无壁纸持久化与全局背景渲染 |
| 模糊强度 | `:103` | 🚧 | 依赖尚未实现的壁纸背景 |

### 1.3 阅读（`settings/ReaderSettings.kt`）

| 项 | 位置 | 状态 | 实际写入 / 消费者 |
|---|---|---|---|
| 阅读模式 | `:22` | ✅ | `pref_reading_mode` → `reader/VeneraReaderScreen.kt:160`；阅读器面板 `:1291,1325` 同源回写 |
| 点击翻页 | `:27` | ✅ | `pref_click_to_turn` → `reader/ReaderInteractionPolicy.kt:14` + `VeneraReaderScreen.kt:170,478` |
| 音量键翻页 | `:43` | ✅ | `pref_volume_key_turn` → `VeneraReaderScreen.kt:169,558` |
| 页间距（0–32dp） | `:56` | ✅ | `pref_page_gap_dp` → `VeneraReaderScreen.kt:166,598,617` |
| 保持屏幕常亮 | `:57` | ✅ | `pref_keep_screen_on` → `VeneraReaderScreen.kt:168,419-421`（真控 FLAG_KEEP_SCREEN_ON） |
| 夜间柔光滤镜 | `:58` | ✅ | `pref_night_filter` → `VeneraReaderScreen.kt:167,461-462` |
| 单页与插图收藏 / 阅读足迹与统计 | `:61` `:62` | ✅ | 纯导航 → `FavoriteImagesRoute` / `StatsRoute` |
| 自动裁剪白边 | `:59` | ⚠️ | 键 `pref_auto_crop_borders` 存在，但 UI `enabled=false` ⇒ setter 永不触发；且**全库无非设置代码读它**。双向皆死 |
| 启用设备专属设置 | `:19` | 🚧 | 原版 `reader.dart:170`；其配套的「清除本设备专属阅读设置」按钮（`reader.dart:189`）我们**连占位都没有** |
| 翻页（从上到下）／连续（从右到左） | `:26` | 🚧 | — |
| 反转点击翻页方向 | `:28` | 🚧 | 原版 `reader.dart:240` |
| 翻页动画 / 自动翻页间隔 / 横屏每屏图数 / 竖屏每屏图数 / 首页单图 / 鼠标滚动速度 | `:29-34` | 🚧 ×6 | **其中「自动翻页间隔」是假否定**：`VeneraReaderScreen.kt:491-528` 已有 15–480 px/s 帧率无关巡航 + `:116` 间隔常量，只是没持久化、没设置入口 |
| 双击缩放 / 长按缩放 / 缩放位置 | `:37-39` | 🚧 ×3 | catalog 已含 `telephoto = 0.19.0`，手势与缩放能力在库内 |
| 限制图片宽度 | `:42` | 🚧 | — |
| 显示时间与电池 / 系统状态栏 / 页码 | `:44-46` | 🚧 ×3 | — |
| 快速收藏图片 / 自定义图片处理 / 预加载数量 / 显示章节评论 / 章末评论 | `:49-53` | 🚧 ×5 | — |
| **漫画专属阅读设置** | — | ❌ | 原版 `reader.dart:135`（开关）+ `:156`（清除该作品）+ 键 `comicSpecificSettings`（`appdata.dart:264`）。我们只有"设备专属"占位 |

### 1.4 探索（`settings/ExploreSettings.kt`）

| 项 | 位置 | 状态 | 实际写入 / 消费者 |
|---|---|---|---|
| 漫画卡片显示模式 | `:12` | ✅ | **不写 VeneraPreferences**：经 `components/ComicListPresentation.kt:16-41` 写 `comic_list_presentation/display_mode`，收藏/历史/搜索/下载/本地/探索/网络收藏全站实时消费。旧键 `VeneraPreferences.pref_comic_display_mode` 已成僵尸（见 §3） |
| 搜索源 | `:21` | ✅ 部分 | 纯导航 → `ComicSourceManageRoute`（启停真实生效）；缺原版的「独立搜索源排序」 |
| 关键词屏蔽 | `:24` | ✅ | 纯导航 → 内部页 `rules/KEYWORD` |
| 卡片大小 / 显示收藏状态 / 显示阅读历史 | `:13-15` | 🚧 ×3 | 无偏好键 |
| 探索页面 / 分类页面 / 网络收藏页面（排序与筛选存储） | `:18-20` | 🚧 ×3 | 无存储 |
| 评论关键词屏蔽 | `:25` | 🚧 | 评论过滤链不读 `content_guard_rules`（守卫链已有，接过来成本低） |
| 默认搜索目标 / 自动语言筛选 / 启动页面 / 章节默认倒序 | `:28-31` | 🚧 ×4 | 章节倒序目前只是详情页会话内状态 |

### 1.5 屏蔽与过滤（`settings/BlockingSettings.kt`）

| 项 | 位置 | 状态 | 实际写入 / 消费者 |
|---|---|---|---|
| 不允许成人内容 | `:25` | ⚠️ **降级** | 写 `venera_guard_prefs/nsfw_mode`，被 `security/guard/ContentGuardManager.kt:50,246,269,285` 与 explore/detail 卡片真实消费。两个缺陷：① 原版是 `off/blur/blurReveal/hide` **四态**（`blocking_settings.dart:99-104`），我们做成二态 toggle，`HIDE` 只能绕到「完整内容守卫」页选；② 当前为 `HIDE` 时，把它关再开会**静默降级成 `BLUR`** |
| 标签 / 画师 / 作品（计数行） | `:33` | ✅ | 纯导航 → `rules/TAG` 等；计数读真实 DB `_rules` |
| 完整内容守卫 | `:38` | ✅ | 纯导航 → `ContentGuardRoute`（OFF/BLUR/HIDE + 逐条规则） |
| 启用标签/画师/作品 屏蔽 | `:34`（forEach，3 行） | 🚧 | **字段其实已就绪**：`GuardRule.isEnabled`（`ContentGuardManager.kt:21-22`）被过滤逻辑消费（`:206,246,268`），只是设置 UI 从不写它 |
| 源分级 | `:29` | ⚠️ **文案不实** | 占位说明称"尚无逐源分级预设"，但 `ContentGuardManager.kt:118-131` 确实从 `assets/source_content_warning.json` 载入 33 源 safe/mixed/nsfw 预设并在 `:351-352` 生效。真正缺的是**逐源用户覆盖存储 + 可选 UI** |
| 屏幕防窥 | `:28` | 🚧 | 无 FLAG_SECURE 持久化开关（原版 `blocking_settings.dart:112`） |
| 添加屏蔽项 | `:64` `:67` | ✅ | 写 SQLite `content_guard_rules`（`ContentGuardManager.kt:171-181`），去重校验生效 |
| 已保存的屏蔽项（点击删除） | `:82-87` | ✅ | 确认后按 id 删 DB（`:189-192`），失败 Toast |
| 正则规则 | `:86` | ⚠️ | 只读展示。`addRule` 恒 `isRegex=false` ⇒ 应用内**无法创建**正则规则；而 `ContentGuardScreen.kt:50,198` 已有 regex 开关，两页不一致 |

### 1.6 本地收藏（`settings/LocalFavoritesSettings.kt`）

| 项 | 位置 | 状态 | 实际写入 / 消费者 |
|---|---|---|---|
| 新收藏添加到（开头／末尾） | `:36` | ✅ | `pref_new_favorite_add_to` → `data/db/LocalFavoritesManager.kt:260` 计算 display_order |
| 快捷收藏（收藏夹下拉） | `:43` | ✅ | `pref_quick_favorite` → `feature/ComicDetailViewModel.kt:469-471`（长按收藏按钮，入口 `ComicDetailScreen.kt:420`） |
| 在网络收藏前显示本地收藏 | `:33` | ⚠️ 纯装饰 | 键 `pref_local_favorites_first` 在，但 `enabled=false` ⇒ setter 永不触发；**无任何消费者** —— `ComicDetailScreen.kt:1462` 只是注释宣称由它决定顺序，`FavoritePanelSheet` 实际硬编码本地在前 |
| 阅读后移动收藏 | `:42` | ⚠️ | 消费者存在（`LocalFavoritesManager.kt:459-462` 读 `pref_move_favorite_after_read`），但 `onRead()` **全库无调用者**（阅读器未接入），且 `setMoveFavoriteAfterRead` 0 调用者 ⇒ 行内"已保存：不移动"恒定 |
| 操作后自动关闭收藏面板 / 点击收藏时 | `:35` `:51` | 🚧 | 无偏好键 |
| 删除所有不可用的本地收藏条目 | `:50` | 🚧 | 无清理方法（实用，建议补） |
| **收藏夹排序** | — | ❌ 设置树无行 | 偏好 `pref_favorite_sort_order` **真实生效**（`feature/FavoritesViewModel.kt:64,127`），但只能在收藏页顶栏改 |
| **追更文件夹** | — | ❌ 设置树无行 | `pref_follow_updates_folder` 真实生效（`data/db/FollowUpdatesWorker.kt:27`、`LocalFavoritesManager.kt:463,641`），唯一写入口在追更页右上角（`feature/FollowUpdatesScreen.kt:97`） |

### 1.7 应用（`settings/AppSettings.kt`）

| 项 | 位置 | 状态 | 实际写入 / 消费者 |
|---|---|---|---|
| 本地漫画存储路径 | `:30` | ✅ **2026-09-22 换成自选目录** | 原为「显示硬编码 `filesDir/downloads` + 点击复制」。现在点是系统目录选择器，真实路径经守卫+写探针后才切；复制路径独立成一行。消费者 `download/ComicStorageRoot.kt`（见 storage-path-selection-2026-09.md） |
| 打开日志 / 下载管理 / 本地漫画 | `:53` `:56` `:57` | ✅ | 纯导航 → `LogViewerRoute` / `DownloadRoute` / `LocalComicRoute` |
| 数据同步（WebDAV） | `:46` | ✅ | 纯导航 → `SyncBackupRoute`，配置/上传/恢复真实存在（`feature/SyncBackupScreen.kt:164-300`） |
| 导出数据 / 导入数据 | `:44` `:45` | ✅ **2026-09-22 换成直接动作** | 原来是与「数据同步」共用同一个**无参** `onSync` 的三行跳转（名不副实）。现在导出走系统「保存为」自选位置与文件名、导入走系统文件选择器，两条都经 `sync/BackupTransfers.kt`。备份包仍只含 history/favorite/stats/guard_rules，**不含 prefs、cookie、已装源** —— 文案已按实际覆盖面写 |
| 缓存大小与清理 | `:40` | 🚧 | 无统计/清理入口，但底层 API 全在（见 §5） |
| 缓存上限 | `:41` | 🚧 | `data/network/VeneraNetworkClient.kt:53` 写死 `100L*1024*1024`；OkHttp `Cache.maxSize()` 运行时可改 |
| 设置新的存储路径 | `:39` | ✅ 2026-09-22 | 见上「本地漫画存储路径」行与 `storage-path-selection-2026-09.md`：SAF 选目录 + 真实路径写探针 + 迁移确认 |
| 语言 | `:49` | 🚧 | 无 locale 持久化 |
| 需要身份验证 | `:50` | 🚧 | 无 Biometric 流程 |
| **WebDAV「跳过指定字段」+「自动同步」** | — | ❌ | 原版 `app.dart:482` `disableSyncFields`（键 `appdata.dart:241`）、`app.dart:522` 自动同步。`SyncBackupScreen.kt` 亦无 |

### 1.8 网络（`settings/NetworkSettings.kt`）

| 项 | 位置 | 状态 | 实际写入 / 消费者 |
|---|---|---|---|
| 代理类型 / 主机 / 端口 | `:90` `:96` `:105` | ✅ | `prefs.setProxy(...)` → `pref_proxy_type/host/port` + `VeneraNetworkClient.rebuildClient()`（`:129-130`）；消费者 `data/network/VeneraNetworkClient.kt:56-61`。原生源与 JS 源共用该 client（`engine/JsHttpHandler.kt:102`）⇒ 代理全局生效 |
| 重置网络熔断 | `:56` | ✅ | `data/network/HostCircuitBreaker.kt:95 resetAll()`（内存态）+ Toast |
| 强制直连 | `:94` | 🚧 | NONE 分支仍走系统默认 ProxySelector |
| 用户名与密码 | `:115` | 🚧 | 无凭据存储/认证 ⇒ 需要鉴权的代理实际连不上 |
| DNS 覆盖（含 DoH） | `:52` | ⚠️ | 行本身 disabled。但 `pref_enable_doh` **默认 true**、`setEnableDoH` 0 调用者、`enableDoH` 无非设置消费者 ⇒ 文案里显示的"开启/关闭"是个改不了也没人读的僵尸键。`okhttp3.dnsoverhttps.DnsOverHttps` 已 import（`VeneraNetworkClient.kt:11`）却从未 `builder.dns()` |
| 下载线程 | `:53` | 🚧 | `download/DownloadManager.kt:63` 写死 `maxConcurrency = 2`，但 `:328` 已是 Semaphore 队列 ⇒ 只差一个参数入口 |

---

## 2. 隐形缺口（原版设置页有，我们连占位都没挂）

| # | 缺口 | 原版证据 | 影响 |
|---|---|---|---|
| 1 | **主题颜色**（7 色） | `appearance.dart:152` / `appdata.dart:177` | 外观页看起来"有主题模式"，实际不能选色 |
| 2 | **漫画专属阅读设置** + 清除该作品 | `reader.dart:135` `:156` / `appdata.dart:264` | 原版体验核心之一：单部作品固定阅读方式 |
| 3 | 「清除本设备专属阅读设置」按钮 | `reader.dart:189` | 专属设置无出口 |
| 4 | WebDAV **跳过指定字段** + **自动同步** | `app.dart:482` `:522` / `appdata.dart:241` | 备份粒度与自动化缺失 |
| 5 | NSFW **四态**（含 `blurReveal` 点击揭示） | `blocking_settings.dart:99-104` | 被压成二态，且 `HIDE` 会被静默改写 |
| 6 | **Debug 分区** | 根目录 `.ref_settings.dart:57,68,264`（旧快照）；`.reference/flutter-master` 新版已移除 | 快照漂移，需确认以哪版为准 |

## 3. 僵尸键与假行（建议删除或实现，别留假开关）

| 对象 | 位置 | 问题 |
|---|---|---|
| `comicDisplayMode` + `setComicDisplayMode` | `VeneraPreferences.kt:118-124` | 0 调用者；真实存储已换到 `data/prefs/ComicListPreferences.kt:14-20`，旧键仅作一次性迁移来源。再接会打架 |
| `enableDoH` / `setEnableDoH` | `VeneraPreferences.kt:99,223-226` | 0 调用者 + 0 消费者，却默认 true |
| `setMoveFavoriteAfterRead` | `VeneraPreferences.kt:203-206` | 0 调用者 |
| `autoCropBorders` | `VeneraPreferences.kt:44-45,163` | 无消费者，UI 已 disabled（`ReaderSettings.kt:59`） |
| `localFavoritesFirst` | `VeneraPreferences.kt:84,208` | 唯一提及是 `ComicDetailScreen.kt:1462` 的注释；面板顺序实际硬编码 |
| `LocalFavoritesManager.onRead()` | `data/db/LocalFavoritesManager.kt:459` | 实现完整但无调用者 ⇒ 补 UI 也不会生效，需阅读器接入 |
| `@Suppress("UNUSED_PARAMETER")` | `AppSettings.kt:14` | 过时标注，4 个回调现均被使用 |

**呈现建议**：把剩余占位统一为三态标注 —— ✅已生效 / 🚧已排期 / ❌不做（直接删行）。56 条灰行会让设置页显得功能繁多，实际是审计与维护负担。

## 4. 结构性缺陷

1. **阅读器设置不热更新**：`VeneraReaderScreen.kt:166-170` 用 `remember { mutableStateOf(prefs.X.value) }` 一次性取值 ⇒ 设置页改完必须重开阅读器。5 项偏好受影响（阅读模式/点击翻页/音量键/页间距/夜间滤镜）。
2. **双入口互不同步**：上述 5 项同时在设置页与阅读器面板（`VeneraReaderScreen.kt:1281-1460`）可改。
3. **NSFW 三处形态不一**：设置二态 toggle（`BlockingSettings.kt:25`）vs 守卫页三态（`ContentGuardScreen.kt:113`）。
4. **正则能力两页不一**：守卫页可建（`ContentGuardScreen.kt:50,198`），设置中心规则页不可（`BlockingSettings.kt:64-79`，`is_enabled` 硬编码 1）。
5. **卡片显示模式三入口**：设置（`ExploreSettings.kt:12`）+ 探索顶栏（`UnifiedExploreScreen.kt:122`）+ 搜索顶栏（`SearchScreen.kt:108`）。
6. **「导出/导入」三行同一回调**，无直达态（见 §1.7）。
7. **设置入口仅首页齿轮**（见 §0）。

## 5. 引擎已就绪、只差入口（性价比排序）

| # | 能力 | 已有实现 | 还差 |
|---|---|---|---|
| 1 | 自动翻页间隔 | `VeneraReaderScreen.kt:491-528`（15–480 px/s 巡航）、`:116` 间隔常量 | 偏好键 + slider；⚠️ 触阅读器 |
| 2 | 下载并发 | `DownloadManager.kt:63-64,328`（Semaphore 队列） | 偏好键 + 入口，改 `maxConcurrency` |
| 3 | 缓存上限 / 清理 | `VeneraNetworkClient.kt:52-53`；`ImageHeaderPolicy.kt:85 clear()`、`reader/BitmapSliceHelper.kt:130 clearCache()`（整个 object 无引用） | 统计 + 上限设置 + 清理按钮 |
| 4 | 规则单条启停 / 正则创建 | `GuardRule.isEnabled`、`isRegex` 已被过滤消费（`ContentGuardManager.kt:206,246,268`），`addRule` 支持 regex（`:171`） | 设置中心规则页补两个控件 |
| 5 | NSFW 三/四态 | `ContentGuardManager` 已支持 OFF/BLUR/HIDE | 设置行改 select；`blurReveal` 需新行为 |
| 6 | 收藏排序 / 追更文件夹入口 | 两个偏好均真实生效 | 纯设置行 |
| 7 | 源分级预设展示 + 逐源覆盖 | `ContentGuardManager.kt:118-137` 已载入 33 源预设 | 去掉 private + 只读列表 + 覆盖存储 |
| 8 | 域名 UA / Cookie 管理 | `data/network/UserAgentPolicy.kt:50,62`（host→UA 持久表 + `clear()`）、`PersistentCookieJar.kt:19` | UI（唯一写入口目前是过盾 `CloudflareBypassManager.kt:105`） |
| 9 | 源健康度 / 熔断可视化 | `HostCircuitBreaker.openHosts()`（`:85`）、`ComicSourceManager.kt:114,1098 latencyMapFlow`（首页 `:118` 在用） | 设置内入口 |
| 10 | URL 匹配规则 | `data/network/ComicUrlMatcher.kt:18-137`（9 源正则）→ `SearchViewModel.kt:77,206` | 开关 / 规则表 / 剪贴板监听 |
| 11 | 搜索并发与超时 | `SearchViewModel.kt:223,227`（`Semaphore(4)`、`withTimeout(30_000)`） | 偏好键（谨慎，涉网络行为） |
| 12 | DoH | `DnsOverHttps` 已 import | ⚠️ **不建议直接接**：需选解析器，且国内网络下强制 DoH 可能整体不可达，属高风险网络变更 |

## 6. 可参考的更成熟开源方案

| 待解决问题 | 参考 | 用法 |
|---|---|---|
| 设置分层（全局→设备→单作品）、备份字段清单、源(扩展)管理、pager/webtoon 与自动滚动 | Mihon 系及其活跃分支 Komikku | 抄设置分层模型，正好补 §2-2 与 §1.7「导出名不副实」 |
| 沉浸式背景 / 壁纸 / 模糊强度 | `chrisbanes/haze`（2.0，模块化） | 比自研省一大截；⚠️ 与已用的 miuix Backdrop、Kyant0 LiquidGlass **三选一**，勿并存多套模糊 |
| 液态玻璃底栏 | `Kyant0/AndroidLiquidGlass`（已用）、`Yukonga/Miuix`（已用） | 保持现状；遵循"用官方原值不加码" |
| 双击/长按缩放三项占位 | `qwertyqr/telephoto`（catalog 已含 0.19.0） | 能力已在库内，属"接上即实现" |
| 需要身份验证 | `androidx.biometric` | 官方库，几十行 |
| 崩溃现场捕获 | ACRA / catmine `CustomActivityOnCrash` | 已有 `LogViewerRoute`，缺崩溃页 |
| 阅读统计图表 | `patrykandpatrick/vico` | `StatsScreen` 现为自建文本统计 |
| WebDAV | `inponomarev/sardine`（Android 客户端） | 现为手写实现，加"跳过字段/自动同步"时可换 |

## 7. 完全没提、也不在原版里的候选新增

屏幕方向锁定、亮度覆盖、字体缩放（全仓零命中 `requestedOrientation`/`screenBrightness`/`fontScale`）；
原版有键但**无 UI** 的：`blockAiWorks`/`showAiBadge`/`aiAction`（`appdata.dart:222-224` AI 作品筛选）、
`ignoreBadCertificate`（`:267`）、`unlockedComics`/`forcedComics`（`:214-215`）、`settingsEntry`（`:182`）。
这些若要暴露属"新增能力"，不是补漏，需单独评审。

## 8. 本批已实施（2026-09-19，紧随本审计）

| 项 | 改动 | 位置 |
|---|---|---|
| NSFW 二态 → **三态直选** | 修掉"HIDE 下关再开被静默改成 BLUR"的真 bug，并让 `HIDE` 不必绕道守卫页；同时改正"仅依据用户规则、不具备源级判定"的不实文案（预设实际已生效） | `settings/BlockingSettings.kt:24-38` |
| 正则规则**可创建** | 规则页补「按正则匹配」开关（`addRule` 本就支持 `isRegex`），并在写库前校验正则可编译 —— 坏正则会静默永不命中，比报错难查 | `settings/BlockingSettings.kt:63-88` |
| 收藏夹排序**进设置** | 纯入口补齐：偏好早已由 `FavoritesViewModel` 读写，此前只能在收藏页顶栏改 | `settings/LocalFavoritesSettings.kt:54-65` |

未做（按 §5 顺序排队，原因逐条注明）：

- **单条规则启停**：`GuardRule.isEnabled` 与过滤消费都已就绪，但现有 `SettingsAction`（点击=删除）与 `SettingsToggle` 无法在同一行共存两个动作，需要给共享控件加 trailing 槽 —— 属跨页组件改动，单独评审。
- **自动翻页间隔**：能力在 `VeneraReaderScreen.kt:491-528` 已存在，但接持久化要改阅读器（手册保护域），需明确授权。
- **DoH**：`DnsOverHttps` 已 import 却未接线；**不建议贸然接** —— 需选定解析器，且在国内网络下强制 DoH 可能整体不可达，属高风险网络变更。
- 下载并发、缓存上限/清理、源健康度、Cookie/UA 管理、URL 匹配规则表：均未动。
- **阅读器设置不热更新**（§4-1）：一行可修（`remember{mutableStateOf(prefs.X.value)}` → `collectAsStateWithLifecycle`），但同样落在阅读器保护域，等授权。

验证状态：`:app:compileDebugKotlin` / `:app:testDebugUnitTest` / `:app:assembleDebug` 三项 BUILD SUCCESSFUL；**真机未验**（设备在装包前掉线）。

## 9. 第二批已实施（2026-09-19，同日）

| 项 | 改动 | 位置 |
|---|---|---|
| **屏幕防窥** | 从占位灰行变成真实开关：新增 `pref_secure_screen`，`MainActivity` 以 `repeatOnLifecycle(STARTED)` 订阅并 `setFlags/clearFlags(FLAG_SECURE)`，拨一下当前窗口即刻生效、重启保持 | `data/prefs/VeneraPreferences.kt`、`MainActivity.kt:34-58`、`settings/BlockingSettings.kt:29-33` |
| **导出/导入文案诚实化** | 「导出应用数据」→「导出阅读与收藏数据」，并在 summary 里写明不含偏好/Cookie/已装源（`BackupManager` 实际只打包四类） | `settings/AppSettings.kt:39-45` |
| **阅读器设置热更新** | 6 项本地副本（阅读模式/页间距/夜间滤镜/常亮/音量键/点击翻页）改为直接订阅偏好。依据：面板里每处本地赋值都紧跟一次 `prefs.setX(...)`，本地副本纯属冗余且是"改设置要重开阅读器"的唯一成因 | `reader/VeneraReaderScreen.kt:158-166` 及其面板 7 处 |
| **自动翻页间隔** | 占位灰行 → 真 slider：新增 `pref_auto_scroll_interval_sec`（默认 4s，1–15s），替换原硬编码常量；循环内读 `.value`，改完下一页即生效 | `reader/VeneraReaderScreen.kt`、`settings/ReaderSettings.kt:33-37` |
| `SettingsSlider` 增加可选 `summary` | 默认空串，不影响既有调用 | `settings/SettingsComponents.kt:188-232` |

**删除的占位行：54 → 22（删 32 条）**，判据是"引擎已在、只差一个键"的保留，需要新子系统的删。删除明细：

- 外观 4：返回动画样式、沉浸式背景、自定义壁纸、模糊强度
- 阅读 15：设备专属设置、重复的模式说明、翻页动画、横/竖屏每屏图数、首图单张、鼠标滚动速度、限制图片宽度、时间电池/状态栏/页码、快速收藏图片、自定义图片处理、章节评论两处
- 探索 7：卡片大小、卡片收藏徽章、卡片历史徽章、探索/分类/网络收藏三类页面排序存储、自动语言筛选
- 本地收藏 2：「在网络收藏前显示本地收藏」（键与 setter 俱在但**无任何消费者**，收藏面板顺序硬编码，属假开关）、「点击收藏时」
- 应用 2：设置新的存储路径（需 SAF 迁移）、语言（需应用内 locale 切换工程）
- 网络 1：DNS 覆盖 —— **主动决定不做**：DoH 客户端虽已 import，但强制 DoH 在国内网络下可能整体不可达，属高风险网络变更，不是"差一个开关"

§2 的 6 处隐形缺口、§3 的其余僵尸键（`comicDisplayMode`、`enableDoH`、`setMoveFavoriteAfterRead`、`autoCropBorders`、`localFavoritesFirst`）**本批未清理**，仍按原计划另行处理。

验证状态：三项 Build QA **BUILD SUCCESSFUL**；单测全通过。**真机未验**，且本批动了阅读器，回归优先级最高：翻页/连续/双页三种模式的滚动、页间距实时变化、夜间滤镜、常亮、音量键与点击翻页、自动巡航启停。

## 10. 第三批：A 类转正 + 灰行形态改造（2026-09-19）

### A 类转正（原「引擎已在、只差一个键」）

| 项 | 新增偏好 | 消费者 |
|---|---|---|
| 预加载图片数量 | `pref_preload_image_count`（默认 5，0–20） | `VeneraReaderScreen` 预取循环（原编译期常量 `PRELOAD_AHEAD_PAGES` 已删） |
| 反转点击翻页方向 | `pref_reverse_tap_direction` | `ReaderInteractionPolicy.readerTapAction(reversed=…)`，与 RTL 镜像可叠加；6 条单测钉死四种组合 |
| 默认搜索目标 | `pref_default_search_target` | `SearchScreen` 首次进入时 `onSourceSelected` 预选一次，不自动搜索 |
| 启动页面 | `pref_start_page` | `Navigation.kt` 的 `startDestination`，非法值回落首页 |
| 默认倒序排列章节 | `pref_reverse_chapter_order` | `ComicDetailScreen` 进页时 `viewModel.setReversed(true)` 一次（会话内手动切换优先） |
| 下载并发 | `pref_download_threads`（1–16） | `DownloadManager.maxConcurrency` |
| 网络缓存上限 | `pref_http_cache_max_mb`（16–1024） | `VeneraNetworkClient.buildClient()`，改后 `rebuildClient()` |
| 网络缓存占用与清理 | — | 新增 `httpCacheSizeBytes()` / `clearHttpCache()`；设置页显示实时占用并可清空 |

### 灰行形态改造

- 新增 `SettingsFutureGroup`：**默认折叠**的「尚未实现」区块，替代原先满屏灰色不可点行。
- B/C 类未实现项全部迁入折叠区：源分级、分类总开关、评论关键词屏蔽、双击缩放、自动裁剪白边、章节评论默认展开、操作后自动关闭面板、阅读后移动收藏、删除不可用条目、需要身份验证。
- 代理对话框内的「强制直连」「用户名与密码」**保留原位**：它们属于代理表单的上下文，挪到页面底部折叠区反而找不到。
- **自动裁剪白边**原先是"看得到、点不动"的禁用开关，按零容忍假开关的原则移入折叠区并说明原因。

### 顶栏一致性

全站下钻页都是「短标题折叠 + 长标题展开」两级（`DownloadScreen.kt:130-131`、`LocalComicScreen.kt:465-466`、设置首页「设置」/「设置与偏好」），而 `SettingsPage` 此前是 `title = largeTitle`，7 个二级页展开态只有一个词、大标题折叠没有视觉变化。现给 `SettingsPage` 增加 `largeTitle` 参数并逐页赋值：外观/外观与主题、阅读/阅读设置、探索/探索与卡片、屏蔽/屏蔽与过滤、收藏/本地收藏、应用/应用与数据、网络/网络与代理。

### 未做（诚实记录）

**双击缩放**：telephoto 0.19 只暴露 `EnabledZoomGestures`（`None / PanOnly / ZoomOnly / ZoomAndPan`）这组粗粒度组合，从 class 文件里看不到"只关双击、保留捏合"的入口。没有确证 API 就不写 —— 用总开关冒充双击开关是假实现。留在折叠区并注明原因。

验证状态：三项 Build QA **BUILD SUCCESSFUL**，单测 **76 项全通过**（新增 6 条点击策略）。**真机未验**；本批动了阅读器与导航起点，回归清单见 §9 末段，另需加验：冷启动落点、进搜索页的源预选、详情页章节初始顺序、下载并发、缓存清理。

---

## 追加（2026-09-30）：折叠区整体撤销 + 设置页文案去 AI 味

用户原话：「设置页面你看下所有的文本，把 ai 编的或者很像 ai 写的东西去掉，换成正常表达」。
两条拍板（AskUserQuestion）：**全量按短句重写** ｜ 未实现项**连标题一起撤掉**。

### 一、撤掉的灰行（本文档里那些「迁入折叠区」的裁决，全部作废）

- `UnsupportedSetting` 调用点 **12 处**：阅读器 3（双击缩放、自动裁剪白边、章节评论默认展开）、
  本地收藏 3（自动关闭面板、阅读后移动收藏、删除不可用条目）、屏蔽 2（源分级、分类总开关）、
  网络 2（代理弹窗里的强制直连、用户名与密码）、应用 1（需要身份验证）、探索 1（评论关键词屏蔽）。
- 连带删掉两个只为它们存在的件：`SettingsFutureGroup`（底部「尚未实现」折叠区）与
  `UnsupportedSetting` 本身（`SettingsComponents.kt`）—— 没有调用点就不留尸体。
- 上面那句「代理对话框内的两条**保留原位**」的裁决**被这次推翻**：代理弹窗里那两条也删了。
- `moveFavoriteAfterRead` 这个键现在只剩 `VeneraPreferences` 与 `LocalFavoritesManager.onRead()`
  两边互相引用、**没有任何读写入口**（这次删的是显示行；键与 onRead 本来就没被阅读流程调用过）。
  按僵尸键记账，不在本轮顺手删数据键。

### 二、文案改了什么

形态审计（脚本 `Temp/settings_text_smell.cjs`，口径 = 只扫非注释行的中文字面量）：
**424 条中文串里 71 条命中可疑形态 → 改后 370 条里 15 条**（总数差 54 = 删掉的灰行自带的那些文案）。

| 命中形态 | 改前 | 改后 |
|---|---|---|
| Markdown 星号 `**x**` | 14 | **0**（`Text()` 不解析 markdown，用户屏幕上就是字面星号，这是"AI 直接吐出来没校对"的最硬证据） |
| 反引号 | 2 | 0 |
| `——` 破折号 | 8 | 0 |
| 内部黑话（判据/熔断/读数/录制层/叠画/档位/冷启动） | 15 | 0 |
| 自述式设计理由（不该/宁可/刻意/以免…） | 8 | 0 |
| 直角引号「」 | 33 | 6（剩下 6 条都是**精确引用系统里的名字**：「所有文件访问」「内部存储」「屏蔽与过滤」、系统设置路径） |
| 括号套长句 | 21 | 8（剩下全是格式举例，如 `站名-编号（yandere-1234567.png）`） |

顺手一起改的第二处：源管理页顶部横幅 `⚠️ N 个站点当前网络不可达，其源已自动熔断跳过`
→ `N 个站点连不上，已经跳过这些源`（去 emoji、去"熔断"），下面那行改成给路径（设置 → 网络 里配代理）。

### 三、量过但没动的

设置树点进去的 `SyncBackupScreen` / `LogViewerScreen` / `FavoriteImagesScreen` 用同一把尺量过，
**命中 0 条**，所以一个字没改。

### 四、这轮证明不了什么

**这批文案没有单测覆盖**：`testDebugUnitTest` 改前改后都是 56 套 / 405 条 / 0 失败，
说明没有一条断言吃这些串 —— 编译绿只能证明**没改坏语法**，不能证明**改对了字**。
真正的凭据只有真机逐页读一遍（清单见 `FREEZE-STATEMENT.md` 批次 I）。
