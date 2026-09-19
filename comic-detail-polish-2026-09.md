# 漫画详情页视觉打磨 + 加载态三段式（含阅读器波浪环）

> 日期：2026-09-19 ｜ 分支：compose-migration ｜ 页面：`feature/ComicDetailScreen.kt`
> 状态：**代码完成，Build QA 三项全绿，真机 QA 待做**
> 批准口径（三轮批量点选）：① 「做 A 组和 B3 B4，另外返回和分享按钮以下的内容往下移动一点」；
> ② 「悬浮钮贴屏幕边、未与下方内容对齐」；③ 「预览加骨架屏 + 模糊小图 + 200ms 淡入；阅读器加载也换波浪圆环」。

## 0. Scope / Stop Condition

**只做**：详情页（未冻结页）的 7 处显示层打磨 + 2 处顶栏相关（内容下移、悬浮钮水平对齐）+ 3 处加载态（预览骨架屏、预览渐进式淡入、章节波浪进度指示器）+ 阅读器 6 处加载指示器。
**不碰**：ViewModel / Data Model / Source / JS / Network / Navigation / BottomBar / 已稳定 Dialog-Sheet Host。**Reader 属保护域，本轮由用户点名授权，只换加载指示器，未动任何加载逻辑。**
**停止条件**：本轮 13 项落地 + Build QA 通过即停；B1（预览改横排）、B2（封面衍生 Hero）、页级低清预览档（需改 Source）明确留档未做。

真机反馈的问题现场：`picacg` 源详情页，浅色动态色主题。

## 1. 已实施项

| # | 问题（现场证据） | 落点 | 改动 |
|---|---|---|---|
| A1 | 「标签与分类」组名裸显英文：`Author:` / `Chinese Team:` / `Categories:` / `Tags:` | `data/tags/TagTranslationManager.kt` | `getNamespaceName()` 由「字典 rows 命中 → 否则原样」改为三级：rows → **源原生键中文兜底表** → 原样 |
| A2 | 组名列 `Categories:` 从单词中间断行成 `Categorie / s:`（定宽 56dp 所致） | `ComicDetailScreen.kt` 标签区 | 定宽 → `widthIn(min=56dp)` + `maxLines=1` + `softWrap=false` + Ellipsis；`FlowRow` 补 `weight(1f)` |
| A3 | 简介只有一行时仍挂着「展开 ↓」——假开关 | `ComicDetailScreen.kt` 简介卡 | `onTextLayout` + `isLineEllipsized(末行)` 判真溢出；只在折叠态回写，`remember(desc)` 随文案重置 |
| A4 | 四联钮里「7」和「1」没有名词，语义全靠图标色扛 | `ComicDetailScreen.kt` 动作行 | label 改「点赞 7」「评论 1」（计数为 0 时仍是「点赞」「评论」） |
| A5 | 预览第二张是纯白块，读起来像加载失败；且「点任意一张从该页开读」完全不可见 | `ComicDetailScreen.kt` 预览网格 | 左下页码角标（复用 `StatusColors.BadgeSurface` / `OnBadgeSurface`，与 SourceBadge 同一固定深底语义）；加载态见下一行 |
| B3 | 顶置态返回/分享钮的 35% 半透黑底在浅色粉背景上发灰发脏 | `DetailOverlayIconButton` + 两个 Icon tint | 底色改 `surfaceVariant` / `selectedSurfaceAlpha`（与「离线下载」同语义）；图标统一 `textPrimary`，消掉页内 `Color.Black` / `Color.White` 字面量 |
| B4 | 有阅读历史时「继续阅读」只有一行小字说明读到哪 | `ComicDetailScreen.kt` 主胶囊 | 胶囊底边叠 2dp 章节进度线（`(lastChapterIndex+1)/chapters.size`），`clip(RoundedCornerShape(50))` 随胶囊收圆角 |
| 留白 | 封面行紧贴顶栏，拥挤 | `ui/tokens/Spacing.kt` | 新增 `detailTopBarClearance = detailTopBarHeight + 24 = 72.dp`，替换页内 `statusBarTop + 56.dp` 字面量 |
| 对齐 | **补做（用户真机前反馈）**：悬浮返回/分享钮贴屏幕边，比封面与卡片左边缘外凸 10dp | `ComicDetailScreen.kt` 顶栏 Row | 水平内边距 `space2`(4dp) → `rowHorizontal`(14dp)，与 `LazyColumn.contentPadding` 同值；顺带把裸写的 `height(48.dp)` 收进新 Token `detailTopBarHeight` |
| 波浪进度 | **追加需求**：章节加载态顶着一枚**静态** Inbox 图标（复用 `VeneraEmptyView`），读起来是「这里没内容」而不是「还在拉」 | 章节卡 loading 分支 | 换成 `CircularWavyProgressIndicator`（M3 Expressive 波浪圆环，32dp）+ 同行「章节信息加载中…」 |
| 渐进式预览 | **追加需求**：预览格「固定灰底 + 流光骨架屏 → 加载完成后 200ms 淡入」 | 预览网格逐格 | 两层：① 占位底 `surfaceVariant`/`placeholderAlpha` + 其上 `VeneraShimmer` ② 真图 `graphicsLayer { alpha }` + `animateFloatAsState(tween(imageFadeInMillis))`。骨架与真图**反向淡出**做交叉过渡（否则骨架突然消失、露出灰底闪一下）；`alpha` 到 1 后骨架整层卸载，不让 12 个无限动画常驻跑。新 Token `VeneraMotionTokens.imageFadeInMillis = 200`（内容替换与界面转场分开调） |
| 阅读器加载 | **追加需求**：阅读器加载也用波浪圆环 | `reader/VeneraReaderScreen.kt` **（保护域）** | 6 处 `CircularProgressIndicator` → 新增私有 `ReaderWavyIndicator`（32dp；章节遮罩那处 48dp）。收敛成一个私有 helper 而非 6 份重复调用：加载态全压在 `BadgeSurface` 固定深底上，轨道色必须是浅色半透，散着写迟早有一份配错 |
| 动作钮取色 | **追加需求（2026-09-20）**：收藏 / 点赞 / 评论 / 分享 四钮在 MD3 下自动取色 | `ui/tokens/Color.kt` + `VeneraTokens.kt` + 页内 4 处 `iconColor` | 新增 4 个语义槽 `actionFavorite/Like/Comment/Share`；MD3 风格下映射 primary/error/tertiary/secondary，MIUIX 风格下仍是 `StatusColors` 固定四色。详见下一小节 |

### 四联动作钮的 MD3 取色：槽位映射与已知代价

动手前用编号选项让用户拍板过两件事，记录如下（将来再调这四钮颜色不要重新发明）：

**1. 映射取「四角色」而非「容器对」或「中性为主」。** 硬约束：Material 动态色板只给 **3 个随壁纸的色相**（primary / secondary / tertiary）+ **1 个不随壁纸的 error 红**，凑不出「四色互异且保住原语义（紫收藏 / 桃点赞 / 绿评论 / 蓝分享）」。用户选定的分配是：

| 动作 | MD3 槽位 | 说明 |
|---|---|---|
| 收藏 | `primary` | 收藏是四钮里最主要的动作，吃主色 |
| 点赞 | `error` | **error 不参与壁纸取色**，恒为红族 → 心形语义是这批里唯一被完整保住的 |
| 评论 | `tertiary` | 随壁纸，色相相对 primary 旋转，与主色可辨 |
| 分享 | `secondary` | 随壁纸，但**动态色板里 secondary 是刻意低饱和的** → 真机上可能偏灰、读起来像次要动作。这是四角色映射的已知代价，**不是 bug**，别再当缺陷去查 |

渲染层没动：仍是「图标着 `iconColor` + 同色 12%（激活 25%）圆盘」，只是 `iconColor` 的来源换成槽位。

**2. MIUIX 风格下保持固定四色，不跟着取色。** 理由（已核对 `ThemeColorBridge.kt:67-107`）：miuix 色板实质只有一个主蓝，`toMaterialColors()` 把 `tertiary` 映成了 `onTertiaryContainer`、`secondary` 也贴着主色 —— 跟着取色会让四钮退化成同色系，**辨识度反而低于固定语义色**。这与 `SettingsBadgeColors` 拒绝随主题变的判据同源（见 `ui/tokens/Color.kt` 该对象注释）。分支收在 `VeneraTokens.color` 里读 `LocalVeneraTokens.current.appearance`，页面不写 if。

### 「先加载一张极其模糊、体积很小的缩略图」—— 已实现后按用户要求删除，原因记录如下

**先说结论**：这一档最终**没有留在代码里**。用户看过方案后决定回到「固定灰底 + 流光骨架屏 + 200ms 淡入」两层。但当时查出来的三条事实是硬约束，将来再提 blur-up 时必须先推翻它们：

- `picacg.js` 的 `ComicDetails` 返回体里**没有 `thumbnails` 字段**（只有 `cover` / `chapters` / `maxPage`），所以 `ComicDetailViewModel:536-560` 是回退到 `fallbackChapterPages` —— 即**整页原图 URL**。预览格下载的就是阅读器的原图。
- 源协议里专为小图准备的 `onThumbnailLoad` 钩子（`picacg.js:626`）**在 Kotlin 侧零调用点**（全仓 grep 无引用）—— 这个档位在本移植版里根本不存在。
- Coil 的 `size()` 只影响**解码**尺寸，不减网络字节 → 对同一 URL 做「先小后大」是纯浪费。

→ 所以**页级**低清图做不到；当时实现的是**作品级**近似（封面 `size(12,16)` + `Precision.INEXACT` 极低清解码放大，12 格共用一个请求）。要做到页级，必须接上 `onThumbnailLoad` 并让各源真的提供小图 URL —— Source 层改动，按手册要独立立项。

### 追加两项的依赖核实（不是装个新库）

`CircularWavyProgressIndicator` **Miuix 里没有** —— 0.9.4-rc01（当前最新 tag，main 分支亦同）只有 `LinearProgressIndicator` / `CircularProgressIndicator` / `InfiniteProgressIndicator`。它在 **androidx material3** 的 `WavyProgressIndicatorKt` 里，而本项目已把 material3 钉在 `1.5.0-alpha22`（M3 Expressive 线，见 `gradle/libs.versions.toml:14`），反编译该 aar 确认符号存在 → **零新增依赖**，仅需 `@OptIn(ExperimentalMaterial3Api::class)`（该函数 :79 早已带）。

这是手册 §4.2「不用 Material3 完整 UI 组件当业务组件」的一处**有意例外**：由用户点名要求，且页面此前已在 4 处使用 M3 `CircularProgressIndicator`；组件层（Venera Components）未引入任何 M3 业务组件。

## 2. A1 的接线口径修正（与初次提议不同）

我在方案里最初说「走 `JsComicSource.translate()` 拿源自带字典」。**实施时否掉了这个接法**，两个理由：

1. `venera-tag-multilang-plan.md` §1.3 已写明那份字典只服务探索页分类名，「与标签无关，别混用」；
2. 从 UI 拿源实例要穿 Source / ViewModel 两个保护域，违反手册 §6.3。

改为在 `TagTranslationManager` 里加一张 Kotlin 侧兜底表。表项**全部来自 `app/src/main/assets/sources` 下各 .js 里实际写出的键**（脚本扫出来的，不是猜的）：jm（`Author`/`Tag`/`Work`/`Actor`/`View`）、picacg（`Author`/`Chinese Team`/`Categories`/`Tags`）、manga_dex（`Status`/`Authors`/`Artists`/`Tags`）、lanraragi（`Tags`/`Pages`/`Extension`）、nhentai（`Categories`/`Tags`）、shonen_jump_plus（`Update`）。goda/mh18/hcomic 本来就直接写中文键，走原样返回。

**风险为零的依据**：组名只是列标题，点击检索走的是 `onSearchTag(tag)` 的标签值本身，命名空间从不进请求串 —— 改显示不可能改变发给源的内容。

## 3. 诚实收窄与仍存的不确定（真机才能定）

- **A1 覆盖不到的键仍会裸显英文**。表里没有的键一律原样显示，不臆造组名（与 L1「宁可不译不可译错」同口径）。新源接入时需要补表。
- **A5 那张白块没定性**：按只读 QA 约定我不动手机界面，无法确认是加载失败还是本来就是一张白页。处理对两种情况都成立 —— 占位底解决「没图时的洞」，页码角标解决「白页被读成破图」。若真机看是加载失败，还需要单独查 Coil 侧。
- **B4 是近似值不是精确进度**：分子用 `lastChapterIndex`、分母用 `liveDetails.chapters.size`。若源两次返回的章节顺序不同（例如按时间倒序 vs 正序），比例会偏。分母未知（=0）时不给线，避免画出没有分母的百分比。
- **B3 若真机对比度不足的回退方案已备好**：改用 `StatusColors.BadgeSurface`（0xCC 固定深底 + `OnBadgeSurface` 图标）—— 那是仓库里「压在不可控图像上」的既有判据。本轮先按「悬浮在页面背景上」的语义走主题色。
- **淡入不影响流量**：alpha 动画只作用于已解码完成的真图（`onSuccess` 才触发），加载失败时 alpha 永远停在 0，露出的是灰底占位 + 页码角标而不是破图。
- **骨架层会自己卸载**：条件是 `!thumbFailed && thumbAlpha < 1f`，淡入结束后不再组合 —— 否则 12 个 `rememberInfiniteTransition` 会一直跑（`VeneraShimmer` 本身是无限动画）。
- **阅读器只碰了视觉**：6 处指示器换成同一个私有 helper，`isChapterLoading` / 分页 `loading=` / `error=` 分支条件、切片解码与重试计数一律未动。

## 4. 本轮明确不做

- **预览卡头部那枚 `CircularProgressIndicator`**（`isLoadingThumbnails`，尺寸 `statusDotSize*2` = 16dp）**没跟着换波浪形**：波浪环的描边/振幅/波长默认值是按 M3 的 48dp 默认尺寸给的（aar 里这些默认值是 `dp.toPx()` 内联的，未导出成常量 —— 此处是按 API 形态推断，未实测），16dp 下大概率糊成一团。要换就得连尺寸一起调，那是第二个决定，不是顺手的一致性。
- **评论 sheet 与收藏面板里的 3 处 `CircularProgressIndicator`** 在已稳定的 Dialog / Sheet Host 内，按手册 §6.3 不动。阅读器内的 `ChapterCommentsSheet.kt:145` 同理（它是评论面板，不是播放器加载）。
- **B1 预览 3 列网格 → 横向 LazyRow**：12 页要占 4 行，确实吃屏。但 `:576` 注释写明当前实现是对齐官方 `comic_details_page/thumbnails.dart` 的网格铺排，改横排属主动偏离官方，未获批准。
- **B2 封面衍生 Hero 背景（顶部主色渐变 / 整页封面模糊底）**：仓库里没有任何**调色板提取**基础设施（取主色要读像素），且官方无此效果，属加码。按「对齐官方特效用原值」的既有判据默认不推。

## 5. QA

**Build QA（2026-09-19）**：`:app:compileDebugKotlin`、`:app:testDebugUnitTest`、`:app:assembleDebug` 三项 **BUILD SUCCESSFUL**。
（过程中踩到一处：KDoc 里写 `sources/*.js` 时 `/*` 被 Kotlin 当作嵌套块注释起始，报 Unclosed comment —— 注释措辞已改。）

**Build QA（2026-09-20，四钮取色）**：同上三项 **BUILD SUCCESSFUL**（首跑编译 + 单测 + 打包全绿，二次跑 44 tasks up-to-date）。`VeneraColorTokens` 全仓只有一处构造点（`VeneraTokens.kt:152`），`SearchScreen.kt:922/1009` 只是把它当参数类型传递，故加 4 个必填字段不波及其他文件。

**真机 QA 待做清单**（手册 §7.3）：
1. LIGHT / DARK 两模式看 A5 页码角标与 B3 顶栏钮对比度；
2. picacg（本例）+ jm + 一个 EH 家族源，确认 A1 组名译法与 rows 优先顺序无冲突；
3. 长简介（触发展开/收起）、一行简介（不出现按钮）、空简介（「暂无详细简介」）三种；
4. 有历史 / 无历史两种进入方式看 B4 进度线；进度线在 MIUIX 与 MD3 两套圆角阶梯下都被胶囊裁住；
5. 360dp 窄屏 + 字体放大一档：A4 的「点赞 123」不撑破四等分行、A2 组名 Ellipsis 不与 chip 重叠；
6. 顶部留白 72dp 后，封面行与顶栏钮不再贴脸，且 shared-element 封面过渡起点仍对齐列表卡片；
7. 悬浮返回/分享钮的**圆边**是否与封面/卡片左边缘成一条直线（14dp 对齐的是圆的左切线；若你更希望图标字形对齐，需把内缩改到 ~5dp，那是另一个判据）。
8. 波浪环：32dp 下还看不看得出「波浪」而非普通圆环；MIUIX 与 MD3 两套外观主题下各看一次（这是页面里唯一的 M3 Expressive 组件）。
9. 预览加载态：弱网/断网进详情页，看「灰底 + 流光骨架 → 200ms 淡入」是否连贯、有无骨架突然消失露灰底闪一下的跳变；12 格同屏滚动有无掉帧；加载失败的格应停在灰底 + 页码角标，不出现破图、也不继续呼吸。
10. 阅读器（保护域回归）：翻页时各加载态的波浪环在深色底板上是否清晰；章节遮罩那处 48dp 与分页那几处 32dp 的观感；重点确认**只换了指示器** —— 图片加载、切片、失败重试、进度记忆均无回归。
11. **四钮取色（2026-09-20 追加）**：切到 MD3 风格、换一张高饱和壁纸再看一次 —— ① 「分享」（`secondary`）是不是灰到像禁用；② 「评论」（`tertiary`）与「收藏」（`primary`）色相间距够不够分；③ LIGHT/DARK 各看一次圆盘 12% 底在两种 surface 上的可见度。这三条任一不过，回到本节换映射（「容器对」或「中性为主」两个备选方案的代价已写明），不要在页面里加临时字面量。

## 6. 改动文件清单

| 文件 | 性质 |
|---|---|
| `app/src/main/java/com/venera/compose/feature/ComicDetailScreen.kt` | 页面显示层（A2/A3/A4/A5/B3/B4/留白/对齐/预览三层加载/章节波浪环） |
| `app/src/main/java/com/venera/compose/data/tags/TagTranslationManager.kt` | 字典层（A1，新增 internal 纯函数 + 兜底表） |
| `app/src/main/java/com/venera/compose/ui/tokens/Spacing.kt` | Token 层（`detailTopBarHeight` / `detailTopBarClearance` / `imageFadeInMillis`，改写 `detailCategoryLabelWidth` 语义） |
| `app/src/main/java/com/venera/compose/ui/tokens/Color.kt` | Token 层（`VeneraColorTokens` 新增 `actionFavorite/Like/Comment/Share` 四个语义槽） |
| `app/src/main/java/com/venera/compose/ui/tokens/VeneraTokens.kt` | Token 层（`color` getter 内按 `appearance` 分派：MD3 取 `MaterialTheme` 色板，MIUIX 落 `StatusColors` 固定色） |
| `app/src/main/java/com/venera/compose/reader/VeneraReaderScreen.kt` | **保护域**：仅 6 处加载指示器 + 1 个私有 helper，未动加载逻辑 |

未新增 public 组件，未新增第二套 Card/Chip，未改 ViewModel / Source / JS / Network / Navigation。

**提交拆分建议**：Reader 那一个文件单独成提交（真机若出问题便于二分）。
