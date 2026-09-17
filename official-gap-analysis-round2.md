# venera-app/venera 对比审计报告（第 2 轮，2026-09-17）

**对比基准**：官方 venera-app/venera `master` commit `a0eba914f4c2a84ac1bc925adec2baabe920b9be`（2026-04-05，GitHub API 已核实、GPG 签名 verified；官方仓库已声明停止维护）。解包于 `build/official-a0eba914/`（缩写 **O**）。
**当前项目**：本仓库 HEAD `de4656e` + 本轮补丁（缩写 **A** = `app/src/main/java/com/venera/compose/`）。
**验证环境**：无 Android 设备/模拟器（`adb devices` 为空）。全部发现为静态代码证据；除特别注明外未经运行时复现。**不能声称 UI 像素级一致或任何源实机可用。**

本轮推翻旧报告的关键纠偏：
- `official-gap-analysis.md:4`「核心链路已完整」、`:44`「33 源全部走通」→ **无证据支持，撤回**（未测试任何网络源）。
- 章节评论（P0-4）、封面查看器（P0-3）、搜索筛选（P0-1）已实现，不再是缺口。
- 双页阅读 **不是缺失**（真实 HorizontalPager），但有确定性翻页 bug（见 D-1）。

---

## 一、已修复（本轮，测试与构建均通过）

### F-1 详情路由进程恢复后丢失身份 ⭐本轮修复
- **问题**：`DetailRoute(comicId, sourceName)` 只传路由参数，页面却从 `shell.selectedComic`（内存）取漫画；进程被系统回收后从任务栈恢复详情页 → `return@composable` 空白页。且连续打开多本漫画时，返回栈上不同路由可能读到不属于它的条目。
- **修复**：新增 `resolveDetailComic(route, selectedComic)`（`feature/Navigation.kt`）——内存条目仅当 id+源名与路由一致时使用，否则以路由参数构造最小条目重新拉详情。
- **验证**：新增 `app/src/test/java/com/venera/compose/feature/DetailRouteTest.kt` 4 个用例（恢复、命中缓存、异书拒用、异源拒用）全部通过；`:app:assembleDebug` BUILD SUCCESSFUL（15 个单测 0 失败）。

## 二、确定的功能缺失 / 断点（高置信，双侧行号）

### P0（核心数据丢失或功能出口断开）

| # | 发现 | 当前证据 (A) | 官方证据 (O) | 影响 |
|---|---|---|---|---|
| G-1 | **备份漏掉真实收藏库**：备份仍读写已迁移清空的旧 `comic_favorite` 表 | `sync/BackupManager.kt:44-65,114-134`；真实库在 `data/db/LocalFavoriteDatabase.kt`（`LocalFavoritesManager.kt:29`；`:706-775` 迁移后清空旧表） | `utils/data.dart:28-40,75-81` 打包/恢复 `local_favorite.db` | 备份可能导出 0 部收藏；换设备恢复丢全部收藏及文件夹数据 |
| G-2 | **导出分享出口断开**：先 Toast「导出成功」，再调用未注册的 FileProvider 且异常吞掉 | `feature/LocalComicScreen.kt:225-234`、`feature/SyncBackupScreen.kt:339-348`；`AndroidManifest.xml` 无 provider 声明（合并清单仅 androidx.startup） | `pages/local_comics_page.dart:503-508`、`utils/io.dart:331-348`（真保存对话框） | CBZ/.venera 只写进私有 cache，用户拿不到文件；假成功提示 |
| G-3 | **CBZ 导入固定目录覆盖混书**：一律复制成 `import_temp.cbz` 且不传标题 | `feature/LocalComicScreen.kt:93-99`；`download/LocalComicManager.kt:222-249`（按文件名建 `imported/import_temp`，从 0001 覆盖） | `utils/cbz.dart:101-109,140-143`（按原文件名/元数据建独立目录，同名拒绝） | 导第二本必撞书：同号页覆盖、短书残留旧尾页 |
| G-4 | **官方 .venera 备份不兼容且零数据报成功**：只认 4 种 JSON，全部 `getEntry` 可选，无格式校验 | `sync/BackupManager.kt:81-92,177-195` | `utils/data.dart:28-40,58-108`（history.db/local_favorite.db/appdata/cookie/source） | 导入官方备份「恢复完成」实为 0 条；反向亦不互认 |
| G-5 | **JPEG 导入后消失**：导入保存 `.jpeg`，扫描只收 jpg/png/webp | `download/LocalComicManager.kt:238-249` vs `:64-66,132-134` | `utils/cbz.dart:110-118`（接受 jpeg/jpe/gif） | 纯 JPEG 书导入成功但书架没有；混书缺页 |

### P1（体验缺陷 / 数据不完整）

| # | 发现 | 当前证据 (A) | 官方证据 (O) | 影响 |
|---|---|---|---|---|
| G-6 | **未完成下载被当完整离线章**：下载前即写 chapter.json，任意非空图片即返回 | `download/DownloadManager.kt:211-229,272-279`；`feature/ComicDetailViewModel.kt:573-604` 命中即 return | `foundation/local.dart:455-478,502-507` 只认完成记录 | 下几页就暂停 → 点章节只读到前缀，不在线补全、不提示不完整 |
| G-7 | **双页模式翻页 bug（确定性）**：`currentPageIndex=pair*2 → next=+1 → jump=/2` | `reader/VeneraReaderScreen.kt:210-211,252,257-263`；调用点 `:452-459`(音量键)、`:496-502`(点击右 1/4) | `reader/reader.dart` 正常步进 | 双页下滑动可用，但**点击/音量键「下一页」永远停在当前组**（页 0/1 ↔ 反复） |
| G-8 | **自动翻页缺失** | 全 src 无 autoPage 实现（`VeneraReaderScreen.kt:243-271` 仅手动） | `reader/reader.dart:709-727` Timer + `settings/reader.dart:220-233` 1-20s | 功能缺失 |
| G-9 | **隐私锁缺失**：无启动认证、无后台重锁；ContentGuard 只是 NSFW 过滤不是锁 | `MainActivity.kt:20-28` 直入 App；manifest 无 biometric 权限；全 src 无 authenticate/Biometric/FLAG_SECURE | `main.dart:82-116,180-189` + `pages/auth_page.dart` + `settings/app.dart:163-175` | 功能缺失（勿将 R18 过滤称为隐私锁） |
| G-10 | **「全量备份」漏设置/源脚本/源持久数据/Cookie** | `sync/BackupManager.kt:23-26` 宣称全量，实际仅 4 张表 JSON | `utils/data.dart:30-40,83-108` | 重装/换机后源、设置、登录态全丢 |
| G-11 | **重复恢复累加 + 无版本防倒灌**：stats/guard 每次恢复都 insert | `sync/BackupManager.kt:137-173`；`WebDavSyncManager.kt:105-122` 按 mtime 挑文件 | `utils/data.dart:62-81`、`data_sync.dart:204-216` 有 dataVersion 拒旧 | 同一备份反复恢复数据翻倍；旧云包可覆盖较新阅读进度 |
| G-12 | **WebDAV 自动同步无调度**：仅字段存取，无启动/变更监听（注意：UI 上本来就没有该开关，不是「开关失效」） | `sync/WebDavSyncManager.kt:24-41`、`WebDavModels.kt:8` | `utils/data_sync.dart:19-36,79-83` | 只有手动上传/恢复 |
| G-13 | **归档下载协议缺失**：无 archiveDownloader/ArchiveDownloadTask 对应实现 | `download/DownloadManager.kt:287-345` 仅逐页 GET | `pages/comic_details_page/actions.dart:145-238`、`network/download.dart:656-802` | 声明归档能力的源（如 EH）无官方下载路径 |
| G-14 | **JS 协议缺口（源能力面）**：`handleClickTagEvent`/`onTagSuggestionSelected`/`linkHandler`/`idMatch`/`translations` 未接 Kotlin 层；JS UI 对话框 API 返回 null；like/vote/star 异常时仍返回 success(true) 掩盖失败 | `data/network/ComicUrlMatcher.kt`（4 源硬编码）；`engine/VeneraJsEngine.kt:646-653`；`source/js/JsComicSource.kt:1200-1270`（catch 后 `Result.success(true)`） | `parser.dart:1112-1268`、`components/js_ui.dart:21-61` | 链接直达、标签跳转语义、源内翻译、JS 弹窗输入均不工作；点赞/评论点赞失败无感知 |

### UI / 界面与官方不一致（要「一模一样」需逐项对齐）

| # | 差异 | 当前 (A) | 官方 (O) |
|---|---|---|---|
| U-1 | 主导航结构：官方 4 主项 + Search/Settings 是动作；本项 6 主 Tab | `components/VeneraFloatingNavBar.kt:34-45` | `pages/main_page.dart:47-58,68-110` |
| U-2 | 大屏自适应：官方随宽度切侧栏；本项始终底栏 | `VeneraFloatingNavBar.kt:63-67` | `components/navigation_bar.dart:145-210` |
| U-3 | 首页分区顺序/内容：官方 Search/Sync/History/Local/FollowUpdates/Sources/ImageFavorites；本项改成今日推荐+统计，且无 Sync/Search 分区 | `feature/HomeScreen.kt:82-228` | `pages/home_page.dart:31-41,126-213` |
| U-4 | 首页历史：官方恒显示单行 136 高卡片（空态也有）；本项空时隐藏、双行网格 | `HomeScreen.kt:229-254` | `home_page.dart:258-324` |
| U-5 | 首页本地卡：官方有近期封面横列、下载任务直达、导入入口；本项只有总数+管理入口 | `HomeScreen.kt:291-350` | `home_page.dart:408-460` |
| U-6 | 图片收藏统计不可按条目下钻过滤 | `HomeScreen.kt:417-453` 条目无 clickable | `home_page.dart:1023-1030`（initialKeyword） |
| U-7 | 主题：官方 Material 动态取色 + 6 主题色；本项只有亮暗跟随，无主题色 | `feature/VeneraTheme.kt:29-39` | `main.dart:165-212`、`settings/appearance.dart:28-44` |
| U-8 | 设置不统一消费主题：底栏直接读系统亮暗，手动暗色时走亮分支 | `VeneraFloatingNavBar.kt:62,71-80` | `main.dart:205-212` 统一 |
| U-9 | 初始页设置缺失 | `Navigation.kt:152-155` 硬编码 HomeRoute | `settings/explore_settings.dart:91-100` |
| U-10 | 探索/分类页 Tab 不支持用户自选与排序 | `ExploreScreen.kt:116-125`、`CategoriesScreen.kt:64-72` 全量展示 | `explore_page.dart:30-74`、`categories_page.dart:28-63` |
| U-11 | 详情收藏面板不消费 `localFavoritesFirst`（偏好有存取、无消费） | `feature/ComicDetailScreen.kt:1271-1308` | `comic_details_page/favorite.dart:103,136-144` |
| U-12 | 收藏页默认模式与恢复不符；无大屏双栏/小屏抽屉 | `FavoritesScreen.kt:83-114` | `favorites_page.dart:46-138` |
| U-13 | 设置页结构非官方分类（八类 + >720dp 双栏） | `SettingsScreen.kt:84-140` 重排清单 | `settings/settings_page.dart:47-140` |

### 已实现、勿再报缺失（旧报告纠偏）

- 下载调度/逐图 3 次重试/跳过已存在文件/任务 JSON 持久化（`DownloadManager.kt:244-265,340-406,469-534`）——但注意 G-6 的完成校验缺口。
- 本地扫描/ZIP 导入导出/删除（`LocalComicManager.kt:34-302`）、WebDAV 客户端全套动词（`WebDavClient.kt:66-229`）、手动云备份保留 10 份（`WebDavSyncManager.kt:59-92`）均为真实现。
- 搜索筛选弹窗（tune + optionList，`SearchScreen.kt:150-218` + `JsComicSource.kt:287`）、章评（`reader/ChapterCommentsSheet.kt` + 阅读器 `:834-839` 入口）、封面查看器（`CoverViewerScreen` + `Navigation.kt:339-345`）、标签点击跳搜索（`TagSearchRoute`）均已落地。
- 双页阅读本体存在（仅 G-7 步长 bug）。

## 三、建议修复顺序

1. **P0 数据安全**：G-1 备份改读 LocalFavoriteDatabase → G-2 注册 FileProvider（+ xml paths）→ G-3 导入目录按文件名隔离 → G-4 官方格式校验/明确拒绝。
2. **交互断点**：G-7 双页步长（小改动：next 目标应为 `(pair+1)`、末组判定用 pairCount）→ G-6 完成校验 → G-5 jpeg 扫描。
3. **补功能**：G-8 自动翻页 → G-9 隐私锁 → G-14 JS 协议缺口。
4. **UI 对齐**：U-1/U-3/U-4/U-7 优先（用户感知最强）。

## 四、未验证范围（明确声明）

- 无设备/模拟器：未实测任何页面、任何网络源、任何下载/备份闭环；G-2 为静态强证据（清单无 provider），未运行复现。
- 未测试 33 个 JS 源的任何在线可用性。
- 未审计反爬规避类技术改进（按任务范围排除）。
