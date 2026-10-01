# 官方 venera-app/venera 与本项目（venera-compose）详细差距对比报告

对比基准：upstream/master（官方 Flutter 版）全部 130+ 页面/组件 vs 本项目 Compose 移植。
结论先览：**核心链路（源引擎/详情/阅读/收藏/下载/同步）已完整，主要缺口集中在「搜索筛选 UI」「标签交互」「评论增强」「账户周边 UI」四块。**

---

## 一、UI / 界面层差距（按优先级排序）

### 🔴 P0 缺失（用户可直接感知的官方核心交互）

| # | 官方功能 | 官方实现位置 | 本项目现状 | 补充方案 |
|---|---|---|---|---|
| 1 | **搜索结果筛选（tune 图标）**：源声明 search.optionList（28/33 源都有：排序/语言/分类），官方在搜索框旁 tune 图标弹 dialog 勾选，选中后重搜 | search_result_page.dart L180-208 + _SearchSettingsDialog | ❌ UI 无筛选入口；**但源层数据链路已通**（JsComicSource L297 已解析 optionList 默认值传给 JS） | SearchScreen 加 tune 按钮 + 弹层渲染 optionList（label + LinkedHashMap chips），选择后携带 options 调 search |
| 2 | **详情页标签点击跳搜索**：官方点击任意标签 → 跳到该源搜索结果页（handleClickTagEvent 可自定义目标） | comic_page.dart buildTag → PageJumpTarget | ❌ 只有 Toast「搜索标签: xxx」，没有实际跳转 | 详情页标签 chip onClick → 导航 SearchRoute 并预填 keyword（源声明 onClickTag 时优先走其 target） |
| 3 | **封面查看器（CoverViewer）**：详情页封面点击 → 全屏 PhotoView（双指缩放 1x-3x、点击切换顶栏显隐、长按保存） | cover_viewer.dart 全文 | ❌ 封面无任何点击行为 | 复用阅读器已有的 telephoto zoomable 组件，加 CoverViewerRoute（全屏 + 保存到相册） |
| 4 | **章节评论（章评）**：阅读器菜单 → 该章节专属评论列表与发表 | reader/chapter_comments.dart + source.chapterCommentsLoader | ❌ 阅读器无章评入口；**源接口 loadChapterComments/sendChapterComment 已定义**（ComicSource.kt L105-112） | 阅读器菜单加「本章评论」→ BottomSheet 复用详情页评论列表组件 |
| 5 | **评论富文本**：官方评论支持内嵌图片/表情/HTML 片段渲染（rich_comment_content.dart） | rich_comment_content.dart | ❌ 纯 Text 展示 | 最小可行：HtmlDocument 解析评论内容，<img> 内联渲染（HtmlDocument 已有） |

### 🟡 P1 差距（体验细化）

| # | 官方功能 | 本项目现状 | 补充方案 |
|---|---|---|---|
| 6 | **收藏页双栏侧栏**（宽屏：左文件夹树+右漫画流；窄屏抽屉） | FavoritesScreen 有文件夹列表（2 处侧栏痕迹）但窄屏为主 | 平板/横屏 windowSizeClass 分栏；手机保持现状 |
| 7 | **收藏排序**（名称/时间/评分，Order enum） | ❌ 无排序 UI | 文件夹工具栏加排序弹出（复用 Explore 同款 SortMenu） |
| 8 | **阅读器自动翻页**（autoPageTurning，Timer 按 interval 定时翻页） | ❌ 无（已有音量键/点击翻页/常亮/夜间滤镜/页间距/切边） | 阅读器设置面板加「自动翻页」开关 + 间隔选择（Timer + scrollToItem 即可） |
| 9 | **阅读器双页模式细节**：官方 double-page 支持封面单独一页 + 骑马钉分界 | DOUBLE_PAGE 已实现但无封面特殊处理 | doublePager 首页 index 0 独占一屏 |
| 10 | **主页源分区显示"可更新源数量"徽章**（availableUpdates 计数） | 源分区无更新徽章（源管理页有更新检查） | HomeScreen 源行尾补小徽章（复用 ComicSourceViewModel 的 update 检查结果） |
| 11 | **搜索标签建议（Tags Suggestions）**：搜索框输入时下拉 tag 翻译建议 + onTagSuggestionSelected 回调 | 已有 TagTranslationManager.suggestTags 基础建议；缺 onTagSuggestionSelected 源回调透传 | SearchScreen 建议选中后先调源回调再进结果页（JS 层 onTagSuggestionSelected 已在 venera-init.js） |
| 12 | **生物锁/隐私（auth_page + local_auth）**：启动/恢复时指纹/PIN 锁 | ❌ 无 | P1 后置（需 biometric 库 + 设置开关） |

### 🟢 P2 打磨项
- 官方 WebView 独立页（webview.dart，JS UI 面板 showCustomContextmenu 等）——本项目 WebLoginScreen 已覆盖登录场景，源内 js_ui 弹窗用 Dialog 即可
- 官方 clipboard_image（剪贴板图片导入）——低频
- 官方 epub/pdf 阅读支持（readium）——已按计划后置
- 官方 opencc 简繁转换（assets/opencc.txt 已在包内）——TagTranslation 已覆盖主要场景

---

## 二、数据源 / 引擎层差距

| 官方接口 | 本项目状态 |
|---|---|
| loadComicInfo / loadComicPages / search / explore / category | ✅ 全部实现（33 源全部走通） |
| commentsLoader / sendComment / vote / likeComment | ✅ 已实现 |
| **chapterCommentsLoader / sendChapterComment** | ⚠️ 接口已定义（ComicSource.kt L105-112）但 **UI 无入口**（见 P0-4） |
| **likeOrUnlikeComic（作品点赞）** | ❌ 缺失：详情页 isLiked/likesCount 已展示但点击无动作；ComicSource.kt likeComic 返回固定 success(true) 假实现 —— **需接 JS likeOrUnlikeComic** |
| **idMatcher / fromUrl**（链接→漫画解析，支持分享链接直达详情） | ❌ 缺失：官方 ComicSource.idMatcher 匹配 url 提取 id；本项目搜索框 URL 直达只有 ComicUrlMatcher 硬编码 3 源 —— 需把 JS idMatcher 动态接入 ComicUrlMatcher |
| **linkHandler**（源自定义链接处理） | ❌ 缺失（跟随 idMatcher 一起补） |
| **handleClickTagEvent** | ❌ 缺失：JS 层有声明（venera-init.js），Kotlin 源层未透传（见 P0-2 一起做） |
| **onTagSuggestionSelected** | ❌ 缺失（见 P1-11） |
| **translations（源内翻译字典 ts()）** | ❌ 缺失：源 UI 字符串翻译未接（探索页 tab 名等显示英文原文） |
| account（loginWithWebview/cookies/checkStatus/onLoginSuccess） | ✅ 已实现（WebLoginScreen + Cookie 直填） |
| loadComicThumbnail / getThumbnailLoadingConfig | ✅ 已实现（详情预览） |
| settings（源设置面板） | ✅ 已实现 |

---

## 三、建议实施顺序（4 个批次）

1. **批次 A（交互闭环，见效最快）**：P0-2 标签点击跳搜索 + P0-4 章评入口 + likeComic 接 JS（详情页点赞钮）
2. **批次 B（搜索补全）**：P0-1 搜索筛选 dialog（数据链路已通） + P1-11 源建议回调 + idMatcher 动态接入
3. **批次 C（视觉增强）**：P0-3 封面查看器 + P0-5 评论富文本 + P1-10 更新徽章
4. **批次 D（低频）**：P1-8 自动翻页 + P1-6 双栏收藏 + P1-12 生物锁 + translations

批次 A+B 做完后，用户日常操作路径（搜→筛→看→读→评→藏）与官方完全一致。
