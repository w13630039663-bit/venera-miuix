# Android UI 问题清单与设计方案（2026-10-15 · **自审修订版 v2**）

> v1 的三问自审已完成，读数在 `docs/rounds/ui-problem-list-selfaudit-2026-10-15.md`。
> **v1 有 5 条被读数推翻或改判，本文已就地改掉**；批次顺序按"画廊为主"重排；根因层从"逐页打补丁"改成"改一处系统件 + 页面接线"。

## 零、自审结论（先说被打穿的地方）

| 编号 | v1 怎么说 | 现量 | 判定 |
|---|---|---|---|
| A-01 | 阅读器 `⏮ ⏭ ▶ 📑` 符号当图标，且触达不足 | `📑` 在 `ui-54-reader-chrome.xml` **0 命中**（"目录"是矢量图标）；三枚符号的可点祖先各 **126×126px = 48×48dp** | **部分撤回**：符号当图标成立，📑 与"触达不足"撤 |
| A-05 | 五枚圆底里"分享/复制链接几乎没有底色，读起来像禁用态" | 五枚底色 vs 页底 **ΔL 全排 11.4~13.1，彼此差 <2 级**（底色是均等的、都在）；真问题在字形色：收藏 (174,47,52) 与点赞 **(186,26,26) 同为红**，分享与复制链接 **(119,86,85) 饱和 29% 且逐字同色** | **改判**：根因不在容器 alpha，在**语义色映射那一处**（`VeneraTokens.kt:258-261`） |
| A-11 | 统计页榜单源名是 `jm`/`ehentai`/`jpn` | 逐枚读树：`jm` ×3、`nhentai`、`ehentai` ×3，**没有 `jpn`**（本鱼看错 400px 缩略图）；`ehentai`/`nhentai` 是否算泄漏要回源表 | **改判**：确证的内部标识只剩 `jm`、`copy_manga`、`发布通道：…` 三处 |
| A-12 | `VeneraChip` 两枚 Text **没有间距** ⇒ 屏上 `Y·G·Slong_hair` | 实测间隙 **5px = 1.9dp**（有间距，只是太窄） | **改判 + 降级 P3**：症状成立，成因与措辞不准 |
| A-10 | 图源管理"六枚无标签裸图标" | 那一排可点件 **8 枚，带文字或描述的 1 枚**；中间六枚 **79×126px = 30×48dp**（宽不足 48dp） | **加强**：从"看不懂"升成"7/8 无名 + 触达宽度 30dp" |
| A-06/07/08 | 平板拉伸 / 三条整宽胶囊 / 底栏压正文 | 榜单标题与读数隔 **1506px=1010dp**；右栏正文单行 **1214px=815dp**；分段器每颗占 **645px** 而标签只 88px；底栏纵向带 y978~1051 与右栏"预览 (12 页)" y1028~1055 **坐标重叠** | **加强**：全部换成坐标读数 |
| A-02 | 面板与底栏"两层字叠在一起读不了" | 底栏被 80% 面板压到 28 级亮度摆幅（无面板时 199 级），**仍可读**；红色开关确实压在"设置"图标上 | **改判措辞**：不是"读不了"，是"面板末行与透出的底栏行同区并置" |
| A-09 | 画廊视频角标 `▶` 是符号当图标 | `▶ 3″` [958,833][1017,869] | **撤回**：缩略图上的播放三角是通用记号。成立的是 `″` 双撇号当秒数单位 |
| A-13 | 三处分组标题与行同名 | `主题模式` 2 处、`界面风格` 2 处、`界面材质` 1 处（行在屏外） | **改判**：两处有屏上凭据，第三处只有代码凭据 |

**量具自伤一条**：第一版探针解析 `bounds` 时逗号没切，坐标全成 `NaN`，输出过"当前默认 0 命中""可点祖先 0 层"这种像真的假数——差一点让本鱼撤掉一条**真**读数。修法已记进自审记录第三节。

---

## 一、产品方向核对（第 2 问）

**v1 跑偏的两处**：

1. **批次重心压在漫画侧**。v1 七批里只有 1.5 批落在画廊线（M3 + T1 的一部分），M1/M2/T2 全是漫画详情、漫画阅读器、漫画源管理。本 app 的主场是画廊（底栏第 4 位、独立 Activity、P3 收藏、每日热门、大图页），v1 的排序等于把次要场景排在前面。→ 本文 §三 重排为 **G 批在前**。
2. **两条"为统一而统一"的规则越界**，会伤到画廊的身份系统：
   - ~~"一屏只许一枚主色实心件"~~ → 限定为**同一族控件之内不许多彩**。画廊的站方品牌色（`ui/tokens/Color.kt:228-242` 的 `GallerySourceGelbooru`/`GallerySourceYandere` + `VeneraGallerySourceMark` 的真图标）与"一屏只留分级那一枚彩色锚点"（批 3 既定拍板）都在辖外，不许为了"少色"把它们中性化。
   - ~~"凡通栏内容块一律 `contentMaxWidthFor`"~~ → **画廊墙、大图页、阅读器页面区排除**。masonry 墙要铺满、画作要吃屏；v1 把 `GalleryScreen.kt:1449-1486`（墙本体）列进限宽名单是错的，那是这一轮最不该限宽的一块。
   - 补一条方向性约束：**不把设置页那套"图标 + 标题 + ›"的行形态推广到画廊**。画廊的密度靠画作缩略图本身承载，套卡片只会把画挤小。

## 二、根因核对（第 3 问）：四条根因都改在系统层，页面只接线

| 根因 | v1 的错误处置 | v2 的根因层修法 | 迁移方式 |
|---|---|---|---|
| **语义色映射**（A-05 实测：两枚状态型同红、两枚动作型同中性） | "改 5 个调用点 + 换底色" | `ui/tokens/VeneraTokens.kt:258-261` 一处：`actionLike` 不再借 `m.error`、`actionShare` 不再借 `m.secondary`；两族各自定档（状态型吃主色 + 换形，动作型吃中性）。MD3 与 Miuix 两档**同一把语义表**，不再一个走 `StatusColors` 一个走 MD3 角色 | 一处改，`ComicDetailScreen` 五枚与 `GalleryViewerToolbar:158` 同时好；配一把"动作色只能从这张表取"的尺子 |
| **容器 alpha 没有槽位**（A-17：页面 12 处手调；scrim 五档并存） | "逐页把 `copy(alpha=)` 换成 textSecondary" | `ui/tokens/Color.kt` 的 `VeneraColorTokens` 补三档：**identityTint**（身份色容器，如 12%/25% 那一族）、**ghostTint**（穿透/叠层）、**dividerTint**。页面只许取槽位 | 槽位先立，再按屏迁；`TokenBudgetFrozenTest` 加一条"新代码不许出现 `copy(alpha =` 于 `ui/tokens/` 之外" |
| **圆角缺的是"对象类"而不是"一个值"**（A-16） | "把裸 12/10dp 收进 `shape.card`" —— **这是类别误判**：`ComicTileLayout.kt:95` 的 12dp 是**封面图**的圆角，与容器卡不是同一种件；套 `shape.card` 会把封面拱成面包 | 先定**对象类表**：容器卡 / 封面图 / 药丸 / 面板 / 图内角标，每类一个槽位（两套字阶各给一档），再按类迁移 | 一类一批，`RoundedCornerShape` 存量尺子按类收口 |
| **大屏限宽靠每页自觉**（A-06：helper 存在但只有 3 个消费者） | "20 页各套一次 `widthIn(max=)`" | 做成**外壳级**：节头件（标题 + 动作同一容器）与内容容器各改一处，页面只决定"参不参与限宽"（画廊墙/大图页/阅读器页面区显式不参与） | 接线表进判据，钉"道数"不钉"存在" |

> 一句话：**v1 把根因写成了 20 个症状**。四条根因里，真正需要逐页动的只有"内部标识"那一族（因为它缺的是显示层解析器，接线点 5 处，且可尺子化）。

---

## 三、修订后的问题清单（只留判定后仍成立的；每条带坐标）

### P0

| # | 问题 | 截图 + 位置 | 源码 | 感知 |
|---|---|---|---|---|
| P0-1 | ✅ **G1 已修**（成因改判：不是"失败分支排在缓存之后"，是**整轮失败时那份「站 → 原因」的结构在发车那一处被压成一句串丢了**，唯一吃结构的提示因此拿不到本轮事实）。画廊首页断网时屏上没有任何"网络不通"的说法，只有推荐空态 | `scr-a8-gallery.png`；`ui-a8-gallery.xml` 全树相关文本仅 `标签 / 画师 / 作品`、`Safebooru 还没有可推荐的标签` | ~~`gallery/ui/GalleryScreen.kt:1935`~~ → `gallery/domain/GalleryDailyFeed.kt` 全空那一支 + `GalleryForYouViewModel.applyPage` 的 `.onFailure`（两支都收，见手册行 192） | 用户以为"这站没图"，实际是没网 |
| P0-2 | 封面取不到时是一片同色空块，无图无字无出口 | **屏上未取到样本**（需断网冷启补一次） | `components/venera/VeneraCover.kt:84,99,101,112` | 全站列表卡的公共底，影响面最大 |
| P0-3 | 阅读器"自动播放"开/关在屏上永远不换形（三元两臂同一个字），且那枚变量读的是"模式支持"不是"正在跑" | `scr-54-reader-chrome.png` 底排第一枚 `▶` 节点 [127,2149][163,2198]；`tab/scr-t22-reader-chrome.png` 同位 | `reader/VeneraReaderScreen.kt:1151`、`:1134` | 不知道巡航在跑没在跑 |
| P0-4 | 阅读器三张 sheet 容器 80% 不透明，底栏一整排透上来与面板末行并置，红色开关压在"设置"图标上 | 裁图 `audit-crop-sheet-bottom.png`（`scr-57-reader-settings.png` y 2080~2340）；读数：底栏亮度摆幅 199→28 级 | `VeneraReaderScreen.kt:1420,1534,1852` 的 `containerColor = StatusColors.BadgeSurface`（`ui/tokens/Color.kt:200` = `0xCC1F1F1F`） | 面板读起来"脏"，且像还有第二层内容 |

### P1

| # | 问题 | 截图 + 位置 | 源码 | 感知 |
|---|---|---|---|---|
| P1-1 | 详情动作行的颜色编码失效：两枚"状态型"同为红，两枚"动作型"同为 29% 饱和中性 | `scr-44-detail.png` 圆座排 y≈950~1050（字形最暗像素：收藏 (174,47,52)/点赞 (186,26,26)/评论 (115,90,46)/分享 (119,86,85)/链接 (119,86,85)）；深色档见 `_small/crop-detail-actions.png` | `ui/tokens/VeneraTokens.kt:258-261`；`feature/DetailActionButton.kt`；调用点 `feature/ComicDetailScreen.kt:642-713` | "收藏和点赞有什么区别"只能靠图标；右半排看着比左半排"旧" |
| P1-2 | 🔶 **已接四颗**：画廊非墙两节（G3，手册行 194）+ `StatsScreen` / `ComicSourceScreen` / `SourceSectionScreen`（T1a，行 195）；剩 `UnifiedExploreScreen`（冻结，待豁免登记）与详情右栏。平板 Medium 带内容不限宽：正文单行 815dp、榜单标题与读数隔 1010dp | `tab/scr-t17-detail.png`（正文节点 [673,918][1887,989]）、`tab/scr-t6-stats.png`（标题右端 x=321 vs 读数左端 x=1827）、`tab/scr-t0-home.png`、`tab/scr-t32-explore.png` | `components/WideScreenPolicy.kt:153-157`（helper 在，消费者只 3 处）；未接清单见 §二第 4 行 | 平板像"手机放大版"，一行读不完 |
| P1-3 | 收藏三档分段器每颗占 645px、标签只 88px，三颗之间全是空 ⚠️ **2026-10-15 算账改判**：批准的"与手机同推导（75% 屏宽）"只把每段从 ≈434dp 带到 ≈316dp，而标签 59dp ⇒ **只缓解不治好**；治好要动 `VeneraSegmentedButton` 的 `weight(1f)`（段宽目前恒由屏宽推导），会同时改画廊两页切换等全部调用点 = **需点头**。账在 rounds §九 | `tab/scr-t14-split.png`（标签中心 x=315/960/1605）、`tab/scr-t13-fav.png` | `feature/FavoritesScreen.kt:622-636`、`:650-667`（`if (wide) Modifier.fillMaxWidth()`，注释自写"平板档维持全宽不动"） | 看不出是一组互斥选项 |
| P1-4 | ✅ **G1 已修**（**本行源码那一栏是旧读数，已就地改判**：现量 `LocalBottomBarClearance` 有 **29 道 / 22 颗文件** 消费者，画廊六颗文件共 9 道**全在**，`FavoritesSplitPaneHost` 自己压根没有可滚动内容 —— 真缺的只有漫画详情那一页） | 悬浮底栏与右栏内容坐标重叠 | `tab/scr-t17-detail.png`（底栏带 y978~1051 ∩ "预览 (12 页)" y1028~1055）、`tab/scr-t26-gallery.png`、`scr-92-gal-home.png` | ~~缺 `gallery/ui/GalleryScreen.kt`、`feature/favorites/FavoritesSplitPaneHost.kt:105-142`~~ → 缺 `feature/ComicDetailScreen.kt:343`（`bottom = space8` 一颗，已接契约） | 滑到底那一行永远读不全 |
| P1-5 | 图源管理那一排 8 枚可点件里 7 枚无名，中间六枚触达 30×48dp | `scr-24-follow.png` y=2134 那一排（节点 79×126px ×6、126×126px ×2） | `feature/sourcemanage/ComicSourceScreen.kt`（同文件 36 处裸 `size(N.dp)` 共 9 档、11 枚裸环，`:1136`/`:1261` 两枚写死 `Color.White`） | 全 app 最易误触的一屏；删除与刷新相邻；读屏念不出 |
| P1-6 | 内部标识上屏三处确证 + 详情角标对读屏完全不可见 | `scr-24-follow.png`（`"当前默认: copy_manga"` [221,522][509,556]）、`scr-26-settings.png`（`"发布通道：w13630039663-bit/…"` [69,743][681,780]）、`scr-a24-stats.png`（榜单行 `jm` [163,1682][190,1712] 等 3 枚）、`scr-44-detail.png` 封面左上角 `manga_dex`（124 份 dump **0 命中** ⇒ 不在语义树） | `feature/sourcemanage/ComicSourceScreen.kt:214`、`feature/settings/AboutSection.kt:119`（值 = `data/update/AppUpdateChecker.kt:19`）、`feature/StatsScreen.kt:265`、`feature/ComicDetailScreen.kt:411` | 带下划线的脚本名；`⚠️` 修法见 §四方案 C3（**不能改写侧**） |
| P1-7 | 符号当图标（有坐标凭据的两族）：设置每行右端 `›`（7 处 19×56px）、评分 `★ 4.0`（可点节点 48×48dp） | `scr-26-settings.png`、`scr-44-detail.png` | `feature/ComicDetailScreen.kt:473,1355`、`feature/explore/UnifiedExploreScreen.kt:1221`、`feature/HomeScreen.kt:487`、`feature/LocalComicScreen.kt:808,877`、`components/ComicListPresentation.kt:189`；`ui/tokens/Typography.kt:34` 的 `chevron` 档就是为这个符号造的 | 图标不成套；读屏念"星号/大于号" |
| P1-8 | 画廊搜索结果区常驻一行内部语法 | `scr-81b-gresults-loaded.png`（结果区那几行灰字） | `gallery/ui/GallerySearchArea.kt:757-758`，渲染件 `:1574` | 屏上教 `-` 前缀语法，违反批 2 的 `compact` 口径 |

### P2 / P3

| # | 先 | 问题 | 证据 |
|---|---|---|---|
| P2-1 | P2 | MD3 档同屏两套字阶（差档 `itemTitle 16↔17`、`screenTitle 22↔24`、`display 28↔32`、`chevron 18↔16`） | `feature/VeneraTheme.kt:96` vs `:120`；两表 `ui/tokens/Typography.kt:51-90`。**⚠️ 未做屏上成对测量** ⇒ 潜在，不排工 |
| P2-2 | P2 | 两套底栏同值双写（裸 `64.dp` vs `ShellHeight = 64.dp`）、底部 12dp 只有一条路吃 token | `components/VeneraFloatingNavBar.kt:103`、`components/backdrop/VeneraLiquidGlassNavBar.kt:77`、`feature/Navigation.kt:1210/1248` |
| P2-3 | P2 | 角标秒数用双撇号 `″` | `gallery/ui/GalleryScreen.kt:1771`，节点 `▶ 3″` [958,833][1017,869] |
| P2-4 | P2 | 两处"分组标题 = 组内唯一行"同名 | `scr-30-appearance.png`：`主题模式` [69,1271]+[69,1370]、`界面风格` [69,1562]+[69,1611]；源码 `feature/settings/AppearanceSettings.kt:69-70,73-74` |
| P2-5 | P2 | 统计页手调 alpha 12 处绕过已有的文字层级档 | `feature/StatsScreen.kt:240,267,282,287,362,367,376,421,431,480,543,584` vs `ui/tokens/VeneraTokens.kt:248-252` |
| P3-1 | P3 | chip 前置站点字母与查询词之间只有 1.9dp 间隙，读起来黏成一串 | `scr-b16-infosheet.png`：`Y·G·S` [106,1120][189,1154] 与 `long_hair censored` [194,1119][419,1156]；源码 `components/venera/VeneraChip.kt:370-383` |
| P3-2 | P3 | 阅读器页点条 `steps = pages - 2`，页数一多就成噪点 | `VeneraReaderScreen.kt:1090-1101`；`scr-54-reader-chrome.png` 25 点。**≥100 页那一档未实拍** |

### 主观设计建议（不是缺陷，要出稿再动）

- **S-1 探索页层级**：标签胶囊的填充比作品标题更抢眼（`scr-12-explore.png`、`tab/scr-t32-explore.png`）。**未做面积/权重测量** ⇒ 只作为设计取向提出。⚠️ 探索页改版上一轮被整体回退过（`_qa/explore-revert-2026-10-14`），**必须先出稿等批准**。
- **S-2 首页统计卡三格等权**、统计页 14 天柱小值成细丝（`scr-10-home.png`、`tab/scr-t0-home.png`、`scr-a24-stats.png`）⇒ 与 `docs/designs/android-ui-polish-2026-10-15.html` ②同源，本轮只做"限宽 + 主副档"。
- **S-3 空态无承重物**（`scr-a5-fav-net.png`）⇒ 本轮只补"行动层接线"（`VeneraEmptyView` 的 `actionText`/`onAction` 几乎无人传），氛围层留 polish 稿。
- **S-4 平板四枚统计图标 chip 各吃一色**（实测 (29,35,41)/(32,41,39)/(33,32,41)/(42,35,36) vs 卡底 (29,32,36)）⇒ 属"有没有锚点"的取向问题，不是缺陷。

---

## 四、修订后的批次（画廊在前，系统层在前）

| 序 | 批 | 内容 | 层 |
|---|---|---|---|
| 1 | **S1** ✅ 已落地（手册行 191） | 令牌层：语义色映射一处重定（§二-1）**已做**；⚠️ §二-2「容器 alpha 三槽位」与 §二-3「圆角对象类表」两格**实施时撤销** —— 前者在 A-05 改判后没有消费者，后者现量 5 处裸 12dp 分属四种对象、真正撞车的只有封面那一处（改判过程在 rounds §四） | 系统 |
| 2 | **G1** ✅ 已落地（手册行 192） | 画廊：P0-1 断网说法（改成"发车那一处带走结构"，页面与提示件一行没改）+ P1-4 clearance。**P1-4 就地改判**：现量 `LocalBottomBarClearance` 29 道 / 22 颗文件，画廊六颗文件 9 道**全在**，本批只接上真缺的那一颗 `feature/ComicDetailScreen.kt:343` | 画廊 |
| 3 | **G2** ✅ 已落地（手册行 193） | 画廊：P1-8 内部语法行（撤"教"不撤能力）、P3-1 chip 前缀间距、P2-3 秒数单位归一档、S-3 画廊空态行动层。**两处就地改判**：P3-1 的成因不是"没有 Spacer"而是**故意收窄的那一档**（照原文补 Spacer 会叠成 6dp）；S-3 那句"几乎无人传"在画廊侧不成立（现量 34 处调用 16 处有出口，画廊 12 处里本批前只差 3 处，逐处理由见 rounds §六） | 画廊 |
| 4 | **G3** ✅ 已落地（手册行 194） | 画廊：节头"标题+动作"同容器（改共用件 `SectionHeader`，两支分叉）+ 非墙两节宽窗收口（**墙本体与紧接墙的那枚节头都排除**）。坐标凭据是本批现补的（探针 `_qa/section_header_gap_probe.js`：平板 621/634dp、手机 220~255dp）。⚠️ 本批**不动 FROZEN 首页**（现量首页 6 处全走「›」那一支），无豁免要记；§五-3 那条"新增 Compact 恒 null 的钉"**作废** —— `WideScreenPolicyTest:171` 早有 | 画廊 |
| 5 | **T1a** 🔶 三颗已接（手册行 195，2026-10-15 深夜）：`StatsScreen` / `ComicSourceScreen` / `SourceSectionScreen` 各**一处**收口（形状定了 = **逐颗包在页面级列表上**，不抽 `WideContentShell`：三颗的容器都是 `LazyColumn`，一处挂载就覆盖整屏 item，抽件会多引一层高度语义要验）；`UnifiedExploreScreen` **没接**（在冻结名单里，要先照 2026-10-14 那一节的写法记外观豁免 —— 宁欠一行不留半条豁免）。⚠️ 新增一条判据：**带网格的页面套收口前，先看列数那把尺子吃的是实测宽还是窗口宽**（吃窗口宽的那一路收了会得到 120dp 的小格）—— 判据与坐标读数在 rounds §十 | 系统+接线 |
| 5′ | **T1b** ⚠️ **需点头**（P1-3 收藏分段器）：本批算过一笔账 —— 批准的修法（与手机同推导 = 75% 屏宽居中）在 MuMu 平板上把每段从 ≈**434dp** 带到 ≈**316dp**，而标签实宽只有 **59dp** ⇒ **只缓解不治好**；治好的那条要动共用件 `VeneraSegmentedButton`（每颗现在吃 `weight(1f)`，段宽永远由屏宽而不是标签推导），会同时改画廊两页切换等**全部调用点**的观感。算术与两档对照在 rounds §九 | 系统 |
| 6 | **C1** | 漫画详情动作行接 S1 的新语义表（P1-1） | 漫画 |
| 7 | **C2** | 阅读器：P0-3 恒等三元 + 符号换图标 + P0-4 三张 sheet 容器 + P3-2 页点 | 漫画 |
| 8 | **C3** | 内部标识：新增**显示侧** `resolveSourceDisplayName`（P1-6） | 共用 |
| 9 | **C4** | 图源管理：P1-5 命名 + 触达 + 危险动作隔离 | 漫画 |
| 10 | **P0′** | 原型：`VeneraComponentsPreview.kt` 扩 before/after 矩阵 + `docs/designs/tablet-medium-band-2026-10-15.html` 线框稿 | 交付物 |

**C3 的一条硬纠正（v1 已写对，这里保留）**：详情角标那个 `manga_dex` **不能改写侧**——`feature/SearchScreen.kt:155-159` 与 `feature/HomeScreen.kt:299-301` 注释明写 `ComicItem.sourceName` 必须是 key，否则共享元素 `coverKey` 两端不同串、转场静默不飞。正解 = 镜像 `feature/ComicDetailViewModel.kt:989-993` 那把反向的 `resolveSourceKey`，新增显示侧解析器，取 `source/ComicSourceManager.kt:545 getSource(key)?.name`，取不到才回退原串；同时给角标补 `contentDescription`（它现在对读屏完全不可见）。

## 五、验证（v1 的六条不变，另加两条）

1~6 沿用 v1：`:app:testDebugUnitTest` 只增不减、判据"白名单只许缩短 + 接线钉道数 + 行为测"、手机档零退化（`WideScreenPolicyTest.kt:231-244` + `ComicColumnPolicyTest` + 新增"Compact 恒 null"）、扰动台架每批一笔、真机成对截图（PJZ110 411dp + MuMu 平板 1291dp 不旋转）、回写手册与 `FREEZE-STATEMENT.md` 豁免那一节。

7. **新增：每条问题开工前必须能报出坐标**。凡本轮要"修"的条目，先在同一张截图上给出节点 bounds 或取样框；给不出的（如 P0-2 封面失败、P2-1 两套字阶）先补取证，不许直接开工。
8. **新增：量具自证**。语义树探针一律用 `bounds="\[([0-9]+),([0-9]+)\]\[([0-9]+),([0-9]+)\]"` 抓四元组、文本用 `contains`；像素探针先跑一次"已知有字/已知无字"的对照框，读数同为 0 摆幅即判量具坏。

## 六、撤回清单（避免下一轮又被当待办）

- 阅读器 `📑` 当图标 —— 该 dump 0 命中，"目录"是矢量图标。
- 阅读器与详情评分钮"触达不足" —— 实测各为 48×48dp。
- "分享/复制链接几乎没有底色、像禁用态" —— 五枚底色 ΔL 均等（11.4~13.1）；真问题是色相，见 P1-1。
- 统计页源名 `jpn` —— 看错缩略图，实为 `nhentai`。
- 画廊视频角标 `▶` 当图标 —— 通用记号，撤；只留 `″` 单位写法（P2-3）。
- "圆角不一致 = 卡半径该统一" —— 12dp 是**封面图**圆角，与容器卡不同对象类；改成 §二-3 的对象类表。
- "凡通栏内容块一律限宽" —— 画廊墙、大图页、阅读器页面区排除。
