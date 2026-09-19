# 设置与设置二级页 · 功能审计（2026-09-19）

> 分支 `compose-migration` ｜ 基线 commit `4e9ffbc` ｜ 只读审计，未改动任何代码
> 证据来源：`app/src/main/java/com/venera/compose/feature/settings/`、`data/prefs/`、`security/guard/`、
> `reader/`、`data/network/`，以及仓库内**完整原版 Flutter 源码** `.reference/flutter-master/`（对照精确到行）。
> 状态图例：✅ 真实生效 ｜ 🚧 占位（`UnsupportedSetting` 灰行）｜ ⚠️ 名不副实/降级/僵尸 ｜ ❌ 原版有而我们连占位都没挂

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
| 本地漫画存储路径（点击复制） | `:30` | ✅ | 显示 `filesDir/downloads`（`:26` 硬编码）+ 复制剪贴板 |
| 打开日志 / 下载管理 / 本地漫画 | `:53` `:56` `:57` | ✅ | 纯导航 → `LogViewerRoute` / `DownloadRoute` / `LocalComicRoute` |
| 数据同步（WebDAV） | `:46` | ✅ | 纯导航 → `SyncBackupRoute`，配置/上传/恢复真实存在（`feature/SyncBackupScreen.kt:164-300`） |
| 导出应用数据 / 导入应用数据 | `:44` `:45` | ⚠️ **名不副实** | ① `sync/BackupManager.kt:44-47` 只打包 history/favorite/stats/guard_rules，**不含 prefs、cookie、已装源**；② 与「数据同步」三行共用同一个**无参** `onSync`（`SettingsHome.kt:102`）⇒ 不执行导出、也不直达对应区块（导出按钮在 `SyncBackupScreen.kt:356`） |
| 缓存大小与清理 | `:40` | 🚧 | 无统计/清理入口，但底层 API 全在（见 §5） |
| 缓存上限 | `:41` | 🚧 | `data/network/VeneraNetworkClient.kt:53` 写死 `100L*1024*1024`；OkHttp `Cache.maxSize()` 运行时可改 |
| 设置新的存储路径 | `:39` | 🚧 | 需 SAF 迁移 |
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
