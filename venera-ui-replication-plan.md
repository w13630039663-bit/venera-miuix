# 1:1 像素级复刻原版 Flutter Venera UI 界面重构方案

> ⚠️ **本文件已被取代（2026-09-16）**：其中的 `file:///d:/venera/...` 路径指向已停用的旧目录；本方案的 UI 部分**已于 `0025266` 落地**（氛围光背景 + 悬浮胶囊底栏 + 首页六分区 + 详情动作条 + 彩色徽章），但「底栏毛玻璃」实为半透明纯色、无真实模糊。当前唯一有效基准是 `venera-migration-plan.md` 的「现实校准」章节 + `venera-gap-analysis.md`。

## 目标与背景
根据用户要求：**“更改 UI 界面，必须要和原版一模一样”**。
通过逆向研读 `master` 分支上的原版 Flutter 完整源码（`lib/pages/`、`lib/components/`、`lib/foundation/`），我们将现有 Compose 界面进行彻底重构，实现从配色体系、背景氛围光、悬浮毛玻璃胶囊导航栏、首页六大专属卡片分区、详情页彩色动作条与双操作按钮、搜索多源过滤、多收藏夹切换，到设置页色彩徽章的 **1:1 严格视觉与交互像素级对齐**。

---

## 视觉与交互差异对比及 1:1 对齐方案

| 页面/组件 | 原版 Flutter 核心设计 (`master` 源码) | 当前 Compose 状态 | 1:1 像素级复刻方案 |
| :--- | :--- | :--- | :--- |
| **全局背景** | `AppBackground._buildAmbient`：顶部 1.6 倍屏幕宽超大 Apple Music 径向渐变氛围光斑（主色 + 副色）+ 柔和暗化 | 纯平实色背景 | 构建 `VeneraAmbientBackground`：使用 `drawBehind` + `Brush.radialGradient` 纯原生渲染顶部双色氛围光晕，0 离屏损耗 |
| **底部导航栏** | `LiquidGlassLens` + `NaviPane`：**悬浮毛玻璃胶囊底栏**，居中悬浮在屏幕底栏上方，带物理回弹形变高亮滑动指示胶囊 | 贴底平铺 64dp 纯色栏，Emoji 图标 | 复刻 **`VeneraFloatingNavBar`**：悬浮药丸胶囊（宽度 92%、高度 64dp、圆角 32dp、半透明毛玻璃底色 + 边框高光），内含矢量级 Outlined/Filled 图标切换与滑动选中胶囊 |
| **首页** | `HomePage` 6 大卡片化分区：<br>1. `_TodayUpdates`（横向大卡片，封图 96x132 + 标题 + NEW角标）<br>2. `_MiuixReadingStats`（阅读统计三列等分卡）<br>3. `_MiuixHistory`（历史两行横向流，进度胶囊）<br>4. `_MiuixComicSources`（源网络延迟/绿红指示灯）<br>5. `_MiuixLocal`（本地漫画缓存与队列）<br>6. `_MiuixImageFavorites`（标签/作者/条形图切换） | 简略列表 | 1:1 还原 6 大专属分区，每个分区配备 `_MiuixSectionHeader`（标题 + 右侧细箭头 `›`）和专有卡片容器与排版 |
| **漫画详情页** | 1. 顶部半透明滑动遮罩顶栏 + "···" 更多按钮<br>2. 封面卡片（104x144dp，圆角 12dp，深色立体阴影，点击全屏查看封面）<br>3. **历史进度横幅**：圆角 24dp 浅底胶囊“上次阅读至 第X话 第Y页”+“继续阅读”<br>4. **彩色圆钮动作条**：继续/开始(橙)、下载(青)、收藏(紫)、评论(绿)、分享(蓝)<br>5. 底部双主按钮：下载(Tonal) + 开始/继续阅读(Filled)<br>6. 标签分类卡、作品简介卡、章节目录（正/倒序切换+上次阅读高亮）、评论预览卡 | 单个大按钮，无彩色动作条，无双主按钮，无历史进度横幅 | 完整还原原版 `ComicPage`：<br>- 顶部立体封面 + 评分 + 源标签<br>- 历史进度悬浮横幅胶囊<br>- 7 颗彩色圆钮动作条 (`_ActionButton`)<br>- 移动端底部双主按钮（下载 + 阅读）<br>- 详细分类标签卡片、章节目录正倒序、最新评论预览卡片 |
| **搜索页** | 1. 顶部 MIUI 药丸搜索框（放大镜、清空、过滤按钮）<br>2. **漫画源横向选择胶囊条**（拷贝漫画/哔咔/MangaDex/聚合搜索）<br>3. 搜索历史 Chips（带清除垃圾桶）<br>4. 热门标签 Chips<br>5. 搜索结果卡片 | 纯文本框，缺少源选择条 | 1:1 补全漫画源快速切换胶囊、搜索历史与清除动作、热门标签网格、双列/单列切换 |
| **收藏页** | 1. 顶部标题 + 文件夹切换抽屉/选择器（默认/追更/完结/稍后）<br>2. 单列/双列布局切换按钮<br>3. 2 列瀑布流卡片，带“NEW”更新角标与源标签 | 简单两列列表，无多文件夹切换 | 增加原版文件夹选择 Tab 切换，支持多收藏夹切换与角标 |
| **设置页** | 顶部 `_AboutSection`（Logo、版本、更新、开源链接）+ 7 大分类卡片（带彩色方形图标徽章）：探索(蓝)、屏蔽(红)、阅读(绿)、外观(紫)、收藏(橙)、通用(青)、网络(青绿) | 简单灰阶列表 | 1:1 复刻原版多彩徽章与卡片列表，加入顶部关于区块 |

---

## 实施方案与变更文件

### 1. 新增与重构组件

#### [NEW] [VeneraAmbientBackground.kt](file:///d:/venera/app/src/main/java/com/venera/compose/components/VeneraAmbientBackground.kt)
- 封装 Apple Music 式顶部超大双色渐变氛围光背景组件。
- 在浅色和深色模式下自动计算透明度与光晕扩散范围，铺在所有页面最底层。

#### [NEW] [VeneraFloatingNavBar.kt](file:///d:/venera/app/src/main/java/com/venera/compose/components/VeneraFloatingNavBar.kt)
- 封装悬浮毛玻璃胶囊导航栏（Liquid Glass Pill）。
- 居中悬浮在屏幕底部，拥有半透明毛玻璃底色、精致边框与滑动选中动画，支持 5 个主标签（首页、搜索、收藏、探索、分类/设置）。

#### [MODIFY] [MainActivity.kt](file:///d:/venera/app/src/main/java/com/venera/compose/MainActivity.kt)
- **`VeneraComposeApp` 容器**：外层包裹 `VeneraAmbientBackground`，底部挂载 `VeneraFloatingNavBar`。
- **`AndroidHomeScreen` 1:1 重构**：
  - `TodayUpdates`：今日推荐横向大卡片流（152dp 高、264dp 宽，封图 96dp，红色 NEW 胶囊，时间戳）。
  - `ReadingStats`：3 列均分阅读统计卡片。
  - `HistorySection`：历史横向卡片流，带上次阅读话数与页码进度条。
  - `ComicSourcesStatus`：多漫画源网络状态列表（带圆点绿/红指示灯与延迟提示）。
  - `LocalComicsSection`：本地已缓存漫画与下载队列管理入口。
  - `ImageFavoritesSection`：图片收藏统计与标签/作者/作品三档切换。
- **`AndroidComicDetailScreen` 1:1 重构**：
  - 顶部透明 Appbar 与更多按钮。
  - 封面深色阴影卡片（104x144dp，圆角 12dp）+ 标题 + 作者 + 源名 + ★ 评分。
  - 历史进度胶囊横幅（“上次阅读至 第X话 第Y页”+“继续阅读”）。
  - 水平滚动彩色动作条（继续/开始橙色、下载青色、收藏紫色、评论绿色、分享蓝色）。
  - 移动端底部双按钮（左侧“下载”Tonal 按钮，右侧“继续阅读/开始阅读”Filled 按钮）。
  - 分类标签分组卡片、作品简介折叠/展开、章节目录（正序/倒序切换 + 上次阅读章节高亮与点标）、评论预览卡片。
- **`AndroidSearchScreen` 1:1 重构**：
  - 药丸搜索框 + 搜索源切换胶囊条（拷贝漫画 / 哔咔漫画 / MangaDex / 全网聚合）。
  - 历史搜索记录 Chips（带垃圾桶一键清除）。
  - 热门标签矩阵。
- **`AndroidFavoritesScreen` 1:1 重构**：
  - 顶部收藏夹分类切换（“全部”、“默认”、“追更”、“完结”、“稍后”）。
  - 漫画瀑布流展示与状态标签。
- **`AndroidSettingsScreen` 1:1 重构**：
  - 顶部应用卡片（Venera 图标、版本号、检查更新、GitHub 源码）。
  - 7 大分类入口配备原版专属彩色方块徽章（探索-蓝、屏蔽-红、阅读-绿、外观-紫、收藏-橙、通用-青、网络-青绿）。

---

## 验证计划

### 1. 自动化编译与构建
- 执行构建命令验证无语法与类型错误：
  ```powershell
  cmd /c "set JAVA_HOME=D:\jdk17\jdk-17.0.20.1+1&& gradlew.bat :app:assembleDebug"
  ```
- 验证 APK 打包成功并输出至 `D:\venera-compose-replica.apk`。

### 2. 人工视觉与交互对齐核验
- **全局氛围光**：核对页面顶部是否有苹果音乐风格柔和主色光斑，深色模式下是否柔和不刺眼。
- **悬浮底栏**：核对导航栏是否为悬浮胶囊形状（非贴底），选中高亮胶囊滑动是否有弹簧质感。
- **首页六大分区**：核对今日推荐横向大卡片、阅读统计卡、历史记录卡、漫画源状态灯、本地漫画入口是否与原版一一对应。
- **详情页**：核对彩色圆钮动作条（橙/青/紫/绿/蓝）、历史进度横幅、双主按钮、章节正倒序切换与高亮。
- **搜索与收藏**：核对源选择胶囊、历史 Chips、收藏夹分组切换。
