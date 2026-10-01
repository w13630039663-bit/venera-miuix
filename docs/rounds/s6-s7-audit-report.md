# S6 / S7 核查报告与补修记录

核查时间：2026-09-17 · 方式：逐文件实读源码验证，不采信先前汇报

## 一、核查结论总表

| 模块 | 文件 | 声称 | 实查 | 结论 |
|---|---|---|---|---|
| 下载调度器 | download/DownloadManager.kt (547行) | 并发池/重试/断点/测速/持久化 | Semaphore 并发、逐图3次重试、.tmp 原子写、速度采样、download_tasks.json 落盘恢复 | ✅ 真实 |
| 本地漫画 | download/LocalComicManager.kt (315行) | 扫描/CBZ导入导出 | exportToCbz/importCbz 均有实现且 UI 已接 | ✅ 真实 |
| WebDAV | sync/WebDavClient.kt (300行) | PROPFIND/MKCOL/PUT | OkHttp 真实现三方法 + Basic 鉴权 | ✅ 真实 |
| 备份还原 | sync/BackupManager.kt (242行) | 4表全量ZIP | history/favorite/stats/guard 四表导出+导入事务 | ✅ 真实 |
| 阅读统计 | stats/ReadingStatsManager.kt + 阅读器埋点 | 真实埋点 | VeneraReaderScreen DisposableEffect onDispose recordSession（页数+时长） | ✅ 真实 |
| 插画收藏 | favoriteimages/ + 阅读器 L813 | 阅读器入口 | favoriteCurrentPage 已挂接菜单动作 | ✅ 真实 |
| 导航接线 | Navigation.kt | 7 个新页面 | 7 条路由全部注册 + 设置页 8 大入口 | ✅ 真实 |
| 本地秒开 | ComicDetailViewModel.openChapter | 本地优先 | getDownloadedChapterFiles 命中即直读本地文件 | ✅ 真实 |
| 数据库迁移 | VeneraDatabase v3 | 2→3 平滑 | onUpgrade 补建三表三索引，1→2 重建路径保留 | ✅ 真实 |
| **内容屏蔽生效** | guard/ContentGuardManager.kt | 过滤探索/搜索/详情 | **❌ 仅管理页自用，业务页 0 调用（假交付）** | 🔴→✅ 已补修 |
| **R18 遮罩** | nsfwMaskMode | BLUR/HIDE 生效 | **❌ 仅设置页读写，无任何封面消费（假交付）** | 🔴→✅ 已补修 |

## 二、本次补修内容

1. **ContentGuardManager 增强**
   - `filterComicModels(List<Comic>)`：源生 Comic 模型口径过滤（探索/分类/搜索共用）
   - `filterExploreParts(List<ExplorePagePart>)`：分区级过滤，空分区整块剔除不留孤儿标题
   - `coverMaskStateFor(...)`：R18 遮罩分级判定（VISIBLE/BLURRED/HIDDEN）

2. **探索页 ExploreScreen.kt**
   - `loadContentForTab` 数据流接入 `filterExploreParts`
   - `LaunchedEffect(guardRules)`：规则变化即时重放过滤（无需手动刷新）
   - ExploreComicCard 封面 BLUR 模式打码 + "R18 · 已打码" 角标

3. **分类页 CategoriesScreen.kt（CategoryComicsScreen）**
   - `loadComics` 接入 `filterComicModels` + 规则变化重放

4. **搜索链路 SearchViewModel.kt**
   - 全网聚合流：每个源结果进 UI 前剔除屏蔽条目
   - 单源流：结果统一过滤

5. **搜索页 SearchScreen.kt**
   - 聚合流横滑卡片 + 单源流列表卡片封面 BLUR 打码

## 三、验收

- `:app:compileDebugKotlin` BUILD SUCCESSFUL（0 错误）
- `:app:assembleDebug` BUILD SUCCESSFUL
- APK：app/build/outputs/apk/debug/app-debug.apk（30,639,977 字节 ≈ 30.64 MB）
