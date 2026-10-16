# Android UI 统一性体检 · 手机档（2026-10-15）

> 题面：用户「那我们自己项目的 ui 统一性怎么样」。
> 范围：只量 `app/`（手机档）。`desktop/**` 一行未量（那条线此刻有另一位在飞；工作区现量 114 颗文件未提交 + 102 颗未跟踪）。
> 方法：三路并行取证 + 主会话逐条复算。**每条数字都给了口径**，凡我（主会话）自己复核过的打「✅我复核」，其余打「⚠️待复算」。
> 判定基准：`UI-UX-Intelligence-Expert` 的优先级档（无障碍/触达 CRITICAL → 布局/性能 HIGH → 字色/动效 MEDIUM）+ 本仓既有纪律（层次走填充差不补描边、尺寸吃现成口径不造新数、屏上文案不带 AI 味）。

---

## 〇、一句话结论

**结构层是统一的，表层不是。**
公共件、主题桥、导航、分派层都只有单一入口（裸 `IconButton` 0 处、`TopAppBar` 只走 miuix 一条路、m3 `Card` 0 处），
但**尺寸/字阶/色板访问路径/状态四态**这四样是各屏各写各的 —— 而且真正让人读成"不像同一个 app"的，是**状态层的缺口**，不是散抄的 dp。

打分（10 分制，按"用户能不能看出来"加权，不按代码洁癖）：

| 维度 | 分 | 一句话依据 |
|---|---|---|
| 令牌体系与存量纪律 | **8** | 尺子在且只许缩短；但它钉的是"别变多"，不是"归位" |
| 控件复用 / 双后端分派 | **7** | 分派层存在且被三把尺子钉住；漏网是"绕过"而不是"没有" |
| 主题与取色链路 | **6.5** | 值安全（bridge 保证三套 primary 同色），但**路径分叉**、一处绕过深色判定 |
| 图标语言 | **5.5** | 四家族混用 + `★`/`›` 当图标 + 裸尺寸 76 处 |
| 文案术语 | **5** | **没有集中文案表**（`stringResource` 0 处）；量词/进行时/重试各写各的 |
| 圆角与间距节奏 | **4.5** | 同一"卡"层级三种半径；`percent=50` 与固定 dp 双写法并存 |
| 动效时长 | **4.5** | token 有且画廊侧在用，漫画侧手抄；共享元素 400ms 未入 token |
| 字阶 | **4** | 裸 `fontSize` 129 处与走令牌 435 处并存；MD3 档同屏两表 |
| 状态四态覆盖 | **3** | 19 屏里四态全齐只有 6 屏；**8 屏失败态没有出口** |
| 可访问性 | **3** | 触达这条优秀，但**减弱动画完全缺位**、无焦点环、HD 钮只靠颜色分开关 |
| 跨后端图证矩阵 | **2** | 8 个对照 `@Preview` 只覆盖 16 颗公共件里的 7 颗 |

---

## 一、状态四态矩阵（最该先修的一格）

✅齐 / ⚠️缺件或自绘 / ❌无。取证：`consistency-components`，格子内容我抽查了 6 格。

| 屏 | 加载 | 空 | 失败 | 成功 |
|---|---|---|---|---|
| 首页 | ⚠️`HomeScreen.kt:288` 只有 Shimmer | ✅`:1087` | ❌`:324-340` 裸 `Text+clickable`「重试」 | ✅ |
| 探索页 | ❌`UnifiedExploreScreen.kt:415,605` 一枚 `CenteredLoader()` 没说法 | ✅`:422,599,628,650` | ✅`:605` | ✅ |
| 搜索页 | ✅`SearchScreen.kt:1419,1439` | ✅`:1000,1006,1091` | ✅`:1041,1094,1180,1685` | ✅ |
| 漫画详情 | ✅`:1156` | ⚠️评论空档不摆（`:1907-1910`） | ✅`:1169` | ✅ |
| 收藏·本地 | ❌无 | ✅`FavoritesScreen.kt:867` | ❌无 | ✅ |
| 收藏·网络 | ✅`:277,483,674` | ✅`:239,291,316,327` | ✅`:319` | ✅ |
| 历史·漫画 | ❌无（Flow 首帧即读成空态） | ✅`HistoryScreen.kt:127` | ❌无 | ✅ |
| 历史·图片 | ✅`FavoriteImagesScreen.kt:219` | ✅`:229` | ❌无 | ✅ |
| 下载队列 | ❌无 | ✅`DownloadScreen.kt:96,102` | ⚠️`:322` 仅行内文本 | ✅ |
| 设置首页 | ❌无 | ❌无 | ❌无 | ✅ |
| 图库首页 | ✅`GalleryScreen.kt:854,1918` | ✅`:883,1929` | ✅`:1935` | ✅ |
| 画廊搜索 | ✅`GallerySearchArea.kt:1269` | ✅ | ✅`:743` | ✅ |
| 按收藏推荐 | ✅`GalleryForYouScreen.kt:109` | ✅`:122` | ✅`:264,296` | ✅ |
| 画师关注 | ❌无 | ⚠️`GalleryHomeSections.kt:262` 只「不摆」 | ❌无 | ✅ |
| 大图页 | ✅`GalleryPostScreen.kt:1874,2038` | n/a | ✅`:1265` | ✅ |
| 「关于这张图」 | ✅`GalleryInfoSheet.kt:1056` | ❌`:549` 标签墙空则整块静默消失 | ❌无 | ✅ |
| 阅读器 | ✅`:2524` | ❌`:430` 只弹 Toast 且带 🧩 | ✅`:2135,2262` | ✅ |
| 追更页 | ❌无环，`:142` 把加载写进空态文案 | ❌`:110,140` 手搓 emoji 空态 | ❌无 | ✅ |
| 源管理页 | ⚠️11 颗裸 `CircularProgressIndicator` | ✅`:171` | ❌无 | ✅ |

**三族结论**
1. **8 屏失败态没有出口**（首页、收藏本地、历史两档、设置首页、画师关注、关于这张图、源管理）——这一族是"静默失败"，与仓内既有纪律「失败就抛错，永不原样交回」同性质，但发生在**屏上**而不是代码里。
2. **6 屏连"加载中"都没有**（收藏本地、历史漫画、下载、设置首页、画师关注、追更）——其中 `HistoryScreen` 那一格最坏：**Flow 首帧就被读成"空"**，用户看到的是"你还没有历史"，而实际是还没取到。
3. 「只有一枚转圈没有任何说法」的两处：`UnifiedExploreScreen.kt:1020`、`LocalComicScreen.kt:187`（后者还是裸环）。
4. `VeneraCover` 占位只有三档，其中**失败档退回静态底、无图无字**（`VeneraCover.kt:84,112`）= 封面取不到时屏上是一片同色方块，没有任何说法。这是全站列表卡的公共底，影响面最大。

## 二、组件重复（同一个概念几颗件）

| 概念 | 件数 | 现量 | 病灶 |
|---|---|---|---|
| 底栏 | **2+1** | 玻璃 `Navigation.kt:1194` / 胶囊 `:1242` / 宽屏 `VeneraSideBar` | 宽屏上限共用 ✅，但 **64dp 各写一遍**（`VeneraFloatingNavBar.kt:103` 裸 `64.dp` vs `VeneraLiquidGlassNavBar.kt:77` `ShellHeight`）；底部间距只有玻璃路径吃 `bottomBarBottomGap`（`Navigation.kt:1210`），胶囊路径内建 `12.dp`（`:96`） |
| 可点圆座 | 4 轨 | 统一件 `VeneraIconButton` 38 / `VeneraTopBarPill` 37 / `VeneraTextButton` 21 / `VeneraButton` 5 | **裸件漏网 55 处/17 文件**（miuix `Button` 30、m3 `TextButton` 17…）；另有**手搓图标钮 8 处无 48dp 触达无按压反馈**：`FollowUpdatesScreen.kt:81,96`、`FavoritesScreen.kt:764`、`VeneraReaderScreen.kt:1337,1351,1362,1385,1402` ⚠️待复算 |
| 胶囊/选择器 | **4 颗真重叠** | `VeneraChip` 38 / `VeneraTagChip` 8（纯别名）/ `VeneraFilterPill` 4 / `VeneraSourceBadge` 5 | **同一个"横滑源选择器"用了四种件、两种选中色**：`SearchScreen.kt:867`(Assist→primaryContainer)、`NetworkFavoritesScreen.kt:431`(实心 primary)、`UnifiedExploreScreen.kt:1047`(Assist)、`FavoritesScreen.kt:629,657`+`GallerySearchArea.kt:712`(SegmentedButton→实心 primary)，而 `GallerySearchArea.kt:949,1012,1117,1227` 同文件又切回 `Chip(Filter)` |
| 弹层 | **三宿主** | `VeneraDialog`（双后端）14 / m3 `ModalBottomSheet` 7 / 裸 `AlertDialog` 8 / `DropdownMenu` 7 | **8 颗裸 `AlertDialog` 不进 `useMiuixWidgets` 分派**（`VeneraControls.kt:62`）⇒ miuix 档下必然掉回 m3 外观：`SearchScreen.kt:519,1520,1662`、`NetworkSettings.kt:101`、`PreferredIpSettings.kt:120`、`SourceEditScreen.kt:180`、`ComicDetailScreen.kt:1670`、`GallerySearchArea.kt:1102` |
| 卡片 | 3 轨 | ✅`VeneraCard` 46 处/18 文件；✅裸 miuix `Card(` 23 处/14 文件；✅m3 `Card` 0 处 | **材质轴漏做**：接玻璃的只有 3 处（`VeneraCard.kt:45`、`SettingsComponents.kt:82`、`GallerySheetBlock.kt:64`），**那 23 颗裸 Card 一颗都拿不到材质** ⇒ 液态玻璃档下整屏卡面没有玻璃响应。`StatsScreen` 一颗占 7 |

## 三、令牌层（尺寸/字阶/色/圆角）

- ✅**裸 `fontSize = N.sp` 129 处 / 20 文件**（走令牌 435 处/59 文件）。Top5：`StatsScreen` 22、`FollowUpdatesScreen` 16、`SyncBackupScreen` 14、`ComicDetailScreen` 11、`VeneraReaderScreen` 10。
  `StatsScreen` 实际 **9 个字号档 {8,9,10,11,12,13,15,19,26}**（从前文档记的"5 档"是漏报）。⚠️待复算
- **两套字阶并行成立**：`VeneraTheme.kt:120` 恒喂 Miuix 表，而 `:96` MD3 档才喂 m3 表 ⇒ MD3 档同屏两表。档位差最大的几档：`display` 28↔32、`chevron` 18↔16、`screenTitle` 22↔24、`itemTitle` 16↔17。⚠️待复算
- ✅**色板路径分叉**：`MiuixTheme.colorScheme` 160 处/33 文件 vs `MaterialTheme.colorScheme` 7 处/3 文件，而 `VeneraTokens.kt:17` 自称"页面访问 Token 的唯一入口"——**160 处绕着它走**。同屏跨两套路径的实例：`VeneraGlass.kt:97-100`（一个 `when` 里 CONTAINER 档读 token、CONTROL/INLINE 档读 miuix）。`StatsScreen.kt:249,267,282,362,376` 通篇 `onSurface.copy(alpha=)` 手调，既绕 token 也绕 `textSecondary/textTertiary`。
- **色值散抄**：✅`ui/tokens/` 外 `Color(0x` 26 处/10 文件（尺子顶格 27，贴脸）。同一语义各抄一遍的两族坐实：`0xFF6750A4` 兜底主色在 `VeneraAmbientBackground.kt:100` 与 `VeneraTopAppBar.kt:252` 各一份；`0xFFE53935` 在 `SourceEditScreen` 三次 = `StatusColors.Failing`。⚠️同值判断待复算
- **alpha 无档位**：内联 `copy(alpha = 0.Nf)` 156 处/27 文件 vs token 常量消费 55 处/23 文件；scrim 强度**并存五档**（容器 0.22/0.28、窗遮罩 0.32、图叠层 35%/45%/55%、分区洗底 0.07、徽标 0.15）。`GlassTintAlpha=0.16f` 是**唯一真收口的一个**（4 引用点、零残留，✅我复核）。
- **圆角**：`RoundedCornerShape` 192 处里裸值 47 + `percent=50` 式 13（**两种写法并存**），散成约 13 档。同一"卡"层级：12dp（`ComicTileLayout:95`/`FavoritesScreen:746`）↔ `shape.card` 16/18dp ↔ 10dp（`GalleryAccountCard:105`）。⚠️待复算
- **间距**：`spacedBy` 206 处（裸 dp 44）、`padding` 裸 dp 71 处；主峰落在 14/16/8/6，怪值 28/30/13/9dp 各 1~2 处。⚠️待复算

## 四、图标语言与屏上符号

- ✅**尺寸这条是好的**：`components/` 裸 `size(N.dp)` **0 处**（全 token 化）；违例集中在 `feature/` 76 处，`ComicSourceScreen` 独占 40（15/17/18dp 同一行乱跳）。
- 家族混用：`material.icons` 217 处 = **outlined 110 / 根 48 / filled 32 / automirrored 27**，miuix 图标仅 6 处，自绘 vector 38 处/17 文件。⚠️待复算
- ✅**符号当图标**（违反"图标出自同一套"这条硬规则）：`★`（`ComicDetailScreen.kt:473,503`）、`›`（`:1355`、`HomeScreen.kt:487`）。
- ✅**emoji 上屏**（属文案语气不是图标，本仓无明文禁令，但与"不带 AI 味"那条相邻）：`SourceHttpDiagnostics.kt:116-119` 🔑🛠🥲、`DownloadManager.kt:373,489`、`LocalComicScreen.kt:161,167,312`、`VeneraReaderScreen.kt:430` 🧩。

## 五、文案术语

- ✅**没有集中文案表**：`stringResource` / `R.string` **0 命中**，屏上中文全是各屏内联字面量（含中文引号串的行 5221/325 是上限，大头在 KDoc 与日志）。
- 术语对撞（建议定名按屏上主流写法）：

| 概念 | 各叫法 | 定名建议 |
|---|---|---|
| 提供方 | 站点 / 漫画源(30+) / 源(大量) / 来源(仅"来源链接"另一义) | 漫画线=**漫画源**，画廊线=**站点**；「来源」只留给"原图出处"。`ComicSourceScreen.kt:238` 一句里同现两词要平 |
| 章节量词 | ✅同屏撞：`:1092`「共 N **话**」vs `:1222`「N **章**」 | 单元统一**话**，总称**章节** |
| 加载态 | 加载中 / 正在加载更多 / 正在取图 / 正在载入 / 正在拉取 / 请稍候（四写法） | **正在加载…** |
| 重试 | 重试(20+) / 再试一次(3：`ComicDetailScreen:1172,1631`、`ReverseResults:105`) / 重新加载(1) | 动作钮统一**重试**，「刷新」留给下拉 |
| 空态 | 还没有X ≈24 / 暂无X ≈19 | 择一 |
- ✅**常驻行带技术解释**（批 2 的 `compact` 口径违例）：`GallerySearchArea.kt:1574` 的 `SearchNoticeLine` 是纯灰字一行老形态，常驻显示"（实测匿名过不去 Cloudflare）"；`VeneraReaderScreen.kt:2195,2285` 直接把 `ZIP: ${page.entryName}` 甩上屏；`SearchScreen.kt:1691`「该源未声明排序或高级筛选选项。」是报告腔。
- ✅**合规样本**（别误伤）：`GallerySearchSource.kt:81` 那句 401 技术解释进的是 `VeneraNotice` 的**展开正文**，常驻行只有「Gelbooru 需要先配置账号才能用」——这才是批 2 定的形。

## 六、可访问性（按 UI 准则的 CRITICAL 两档）

| 准则 | 现量 | 判定 |
|---|---|---|
| 触达 ≥44dp | ✅`VeneraControls.kt:168` `VeneraIconButtonTouchSize=48.dp`，**裸 `IconButton(` 0 处**，dock 图标位 48dp 占位（`GalleryViewerToolbar.kt:184`） | **优秀**，但 8 处手搓图标钮与 `ComicDetailScreen.kt:1228` 那类 caption 文字链是漏网 |
| 色不作唯一指示 | ✅源状态点、分级徽标、`VeneraGallerySourceMark` 都有伴生文字/图形；**违例 1 处**：`GalleryViewerToolbar.kt:162-169` HD 开关不换形、只 `tint=primary`（注释自认"不染色就分不清开还是关"）；同排收藏钮靠 Filled/Outlined 换形 = 单点漏网 | 一处要改 |
| 读屏语义 | `contentDescription` 232 处 / `Icon(` 178 处 / 有意 `null` 69 处；但 `onClickLabel` **仅 2 处**（`VeneraNotice.kt:127`、`GalleryPostScreen.kt:1576`） | 语义挂在 `Icon` 上而非可点容器上，**"形"没铺开** |
| 焦点可见 | `onFocusChanged` 只在搜索框；**无任何统一焦点环** | 结构性缺口 |
| 动效可关 | ✅`reduceMotion` / `ANIMATION_SCALE` / `getFloat` **0 命中**，而 `AppearanceSettings.kt:194` 写着"审计后删掉：返回动画样式等 4 条不做的灰行" | **结构性缺位**（现有 `GalleryAnimationGate` 只管 GIF 内存，不是减弱 UI 动效） |
| 字号跟随系统 | ✅文字全用 `sp`（`fontScale` 0 命中但无需读），需注意 `RichCommentContent.kt:59 .height(60.dp)`、`AppearanceSettings.kt:62 .height(26.dp)` 这类压文字的定高容器 | 良好，两处风险 |

## 七、动效时长与跨后端图证

- 动效令牌 `Spacing.kt:539-563`（short=120/medium=220/long=320/imageFadeIn=200/disintegration=900）✅存在且画廊侧在用；漫画侧手抄 6 档（400 共享元素未入 token、300、**220 与 token 同值双写**、240、200、160）。✅我复核过其中 `HANDOFF_MS=160`（`GalleryPostScreen.kt:1614`）与 `Navigation.kt:485` 那格同值双写。
- ✅**跨后端图证只有半张**：`VeneraComponentsPreview.kt` 有 8 个 `@Preview`（Miuix/MD3 × 明/暗 × 普通/玻璃四路矩阵），但只覆盖 `components/venera/` 16 颗公共件里的 **7 颗**；缺 `VeneraControls` `VeneraFilterPill` `VeneraGlass` `VeneraHScrollFade` `VeneraNotice` `VeneraSegmentedButton` `VeneraSelectBox` `VeneraTopAppBar` `VeneraGallerySourceMark`。批 3 那个「白字压浅蓝底」正是这一族缺陷在真机才被拍到。
- 判据投入产出反了一格：⚠️五颗统一件 `VeneraCard` `VeneraCover` `VeneraShimmer` `VeneraFilterPill` `VeneraSourceBadge` **零尺子**，而只有 2 个调用点的 `VeneraNotice` 配了 10+4 颗尺子。
- 产路径诊断码：⚠️`GalleryInfoSheet.kt` 有 25 行含 `GallerySheetProbe`（头注自写"撤除时连着删"）。

---

## 八、整改次序建议（按"用户看不看得出"排，不按洁癖）

| 档 | 内容 | 代价 | 为什么这个位次 |
|---|---|---|---|
| **P0** | 四态补齐：8 屏失败态出口 + 6 屏加载态，全走 `VeneraEmptyView` / 波浪环；`VeneraCover` 补第四档（失败要有图标 + 一句为什么 + 重试） | 小-中 | 静默失败与"首帧读成空"是功能性缺陷，不是观感问题 |
| **P0** | `HistoryScreen` 那格**单独立案**：Flow 首帧未到时不许读成"还没有历史" | 小 | 最坏的假话形态 |
| **P1** | 「横滑源选择器」定 one-of-four 归位表（6 个调用点、两种选中色）；8 颗裸 `AlertDialog` 接进 `VeneraDialog`；23 颗裸 `Card(` 接进 `VeneraCard`（否则玻璃档卡面无材质） | 中 | 三处都是"同屏看得出不是一个人做的" |
| **P1** | `FollowUpdatesScreen` 归位（15 页里唯一手搓顶栏 + 不吃 `VeneraTokens` 的那颗） | 小 | 单颗文件收口，风险可控 |
| **P2** | 字阶：先**拍板 MD3 档两表并行要不要合成一把**，再谈 129 处收敛；`StatsScreen` 9 档连同映射表一起拍 | 中-大 | 动它 = 整屏 miuix 控件字阶换档，按 D6「不做大迁移」要先出 before/after 成对图 |
| **P2** | 圆角/间距落格 + `percent=50` 与固定 dp 双写法择一；图标家族定档（默认 outlined，状态用 Filled 换形）+ `★`/`›` 换真图标 | 中 | 逐屏改，观感可见但非缺陷 |
| **P2** | 文案术语表（话/章、正在加载…、重试、空态择一）——**顺带决定要不要建集中文案表**（今天 0 处 `stringResource`） | 中 | 建表是架构决定，不是改字 |
| **P3** | 可访问性三件：减弱动画（读系统 `ANIMATION_SCALE` 或加一枚偏好）、统一焦点环、HD 钮换形 | 中 | 桌面线也在同一族上有账 |
| **P3** | 预览矩阵补齐那 9 颗公共件；删 `GallerySheetProbe` 诊断码 | 小 | 这是**后面每一批统一性整改能被看见**的前置条件，性价比其实最高 |

**判据该怎么立**（沿用本仓既有那形，不发明新机制）：
- 「归位表」类尺子写成**白名单只许缩短**（照 `TokenBudgetFrozenTest` 那把），对象是 `裸 Button(55)` / `裸 Card(23)` / `裸 AlertDialog(8)` / `裸 fontSize(129)` 四张名单；
- 接线类判据**钉道数不钉存在**（批 7 那条教训：存在性会被同屏另一处合法调用喂饱）；
- 四态那批要**行为测**（语义树 + `performClick`，进程内零窗口），不许只扫源码字符串——"有一枚指示器"和"失败时屏上真的说话"不是一件事。

## 九、两条排期约束

1. ⚠️`FREEZE-STATEMENT.md` 冻着 8 颗文件（`UnifiedExploreScreen` `SourceSectionScreen` `SearchScreen` `ContentGuardManager` `HistoryScreen` `FavoritesScreen` `NetworkFavoritesScreen` `HomeScreen`），碰这些要走行级解冻。**最脏的三颗（`StatsScreen` 22 处裸 sp + 7 裸 Card、`ComicSourceScreen` 40 裸图标尺寸 + 11 裸环、`SyncBackupScreen` 14 sp + 4 Card + 6 White + 7 Toast emoji）都不在名单里** ⇒ P0/P1 可以从这三颗开刀而不申请解冻。但 `HistoryScreen`/`FavoritesScreen`/`SearchScreen` 在，P0 里那格"历史首帧"要先解冻。
2. ⚠️工作区 114 颗文件未提交 + 102 颗未跟踪：本体检**只读未编译**，所有数字是文本层现量。开工前那批先归账，否则扰动台架的还原与"包内 jar 与源码同步"两把尺子会失真。

## 十、我没能量到的

- 真机观感（本轮没有新截图；`_qa/ui-audit-2026-10-14/after/` 那批是上一轮的）。上面所有"看得出/看不出"的判断都基于代码结构，**观感结论要靠成对截图定案**。
- `desktop/**` 的统一性（用户本轮口径先不动桌面线；那边另有一把 `[值]/[术]` 精简裁决尺子）。
- 打码 `blur(18.dp)`、玻璃 `blur(8dp)+lens(24dp)` 这类模糊半径是否该并入一把"材质档位表"——只数到了值，没判够不够。
- ⚠️标记的条目是我抽查过路径、但**逐条数字未复算**的（约 12 项）。下一批开工前按「先复算再动手」处理，别直接引用本文数字。
