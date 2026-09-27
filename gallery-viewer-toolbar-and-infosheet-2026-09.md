# 画廊播放器改版：满屏 viewer + 底部工具条 + InfoSheet（2026-09-25）

参考实现：**Breadboard**（`github.com/breadboardapp/breadboard`，浅克隆在 `build/_refs/breadboard/`，
main @ `1836043`）。用户给的两张真机截图分别对应它的
`largeimageview/LargeImageView.kt`（底部工具条 + 独立分享 FAB）与
`largeimageview/InfoSheet.kt`（"About this art" 半模态面板）。

技术栈对照（**telephoto 与我们同版本 0.19.0**，所以缩放那套手感可以直接对齐）：

| | Breadboard | 我们 |
|---|---|---|
| material3 | 1.5.0-alpha26（BOM 2026.08） | **1.5.0-alpha22**（被 Miuix 钉住，见 `libs.versions.toml:9-14` 的注释） |
| Coil | 3.5.0 | 3.6.2 |
| telephoto | `zoomable` | `zoomable` + `zoomable-image-coil3` |
| 播放器 | `io.github.kdroidfilter:composemediaplayer:0.10.0` | 手写 media3 1.11.1 + `AndroidView(PlayerView)` |
| 图片下载 | 自己写 | **两条现成路**：`CoverViewerScreen.kt:102`（File API + JPEG 重编码）与 `VeneraReaderScreen.kt:1945`（MediaStore） |

## 〇、本轮两条已拍板的事

1. 工具条**不摆心形收藏** —— 画廊没有落库的表（P3 那件事用户仍未拍板），摆了就是假按钮。
   本轮工具条 = `HD` / `下载` / `信息 (i)` + 独立 `分享 FAB`。
2. InfoSheet **替换**二级那两张往下滚的卡（`GalleryInfoCard` + `GalleryTagsCard`），
   二级改成"图占满屏 + 底部工具条 + 点 (i) 拉 sheet"。

## 一、结构决策：二级与三级合并成一层

现状是三层：一级网格 → 二级（`verticalScroll`：large 档 + 信息卡 + 标签卡）→ 三级 overlay
（`GalleryFullViewer`：file_url + `Modifier.zoomable` + 守卫 blur + 进度环）。

截图里只有一层：图满屏、工具条、sheet，**HD 钮负责换档**。所以：

- `GalleryPostScreen` 改成满屏层：`AsyncImage(large|file, zoomable)` + chrome（顶栏 + 工具条）+ `GalleryInfoSheet`。
- **`GalleryFullViewer.kt` 删掉**，它那份"两层底图 / 守卫 blur / 波浪环 / chrome 切显隐"的口径整体上移到满屏层。
  留着两层的后果是"两屏几乎一样、其中一屏多一个 HD 钮"，而且守卫判定会出现两份实现 ——
  今天刚因为"一级与二级各算一套"修过一次分叉（见 `gallery-dual-source-hot-pool-plan-2026-09.md` §十四）。
- 视频条目**不进 zoomable**（沿用现裁定），且**不摆 HD 钮**：实测站方没有更小的视频转码档
  （`media_asset.variants` 只有 180/360 jpg、720 webp 静帧 + original mp4），HD 对视频是假开关。

## 二、数据面实测：能摆什么、不能摆什么

**标签分桶**（截图里 `Character` / `Copyright` 那种分组）：

- Danbooru **五串全给**（今天日榜实测）：`tag_string_general` / `tag_string_character` /
  `tag_string_copyright` / `tag_string_artist` / `tag_string_meta`。
- yande.re **44 个键里没有任何分类字段**，只有一个平铺 `tags` 串 + `author`。
  → yande 那侧只能出「标签」「作者」两桶。**这是站方天花板，不是我少写。**
- 归一模型：`GalleryPost.tagGroups: List<GalleryTagGroup(label, tags)>`，
  两站各自的 DTO 负责翻译，UI 层不再出现单站专属字段（与现有归一约定一致）。

**不照抄的三处**（照抄就会摆成假数据）：

- 截图里 `Source` + `Pixiv URL` 两行：它把"上游出处"与"原站作品页"分开存；
  我们只有 `source` 一个串（实测 yande 40 条里 3 条为空 → **空就整行不摆**，与现有 `favCount` 同口径）。
- 底部 `Sources | Imageboard` 分段（"同一张画的其他来源"）：无数据面，不摆。
- 截图二里那张 `Rating` / `Imageboard` 小卡：字段都有，照做。

## 三、工具条四个钮的真身

- **HD**：默认吃 `large_url`，点开切 `file_url`（逐张覆盖，`rememberSaveable(site, id)`），
  切档期间保留旧档当底图（就是现在三级那套两层画法）。
  **本轮不做 WiFi/省流量自动档**：全仓在 `data/prefs` 与 `data/network` 里**零命中**任何流量相关偏好，
  要先加设置项，那是另一轮的事。
- **下载**：走 **MediaStore 原样落字节**，口径照 `VeneraReaderScreen.kt:1945`
  （`RELATIVE_PATH = Pictures/Venera` + `IS_PENDING`），MIME 按 `file_ext`（jpg/png/webp/gif + mp4/webm）。
  **刻意不复用 `CoverViewerScreen.saveCoverToGallery`**，两条原因都在代码里：
  ① 它 `bmp.compress(JPEG, 95)` —— 会把 png 的 alpha 洗掉、把原图重编码降质；
  ② 它用 `context.imageLoader`（Coil 全局单例），而画廊的图在**独立 ImageLoader**
  （`GalleryImageLoader`，独立 cacheDir + 64/512 MB 预算）里，走那个单例等于把原图**再下一遍**。
  视频一并支持（`video/mp4`，16~26 MB，Toast 里把大小说清）。
  失败必须说话（返回 `Result`，UI 出 Toast），不许像现在那样 `catch → false` 静默。
- **信息 (i)**：拉起 `ModalBottomSheet`（M3，**不用 miuix 那个 OverlayBottomSheet** ——
  它的内容渲染在独立窗口，读不到 CompositionLocal 会崩，`SearchScreen.kt:1500` 已记过这个坑）。
- **分享 FAB**：`pageUrl` 走系统分享（上一轮 share 那批已有 Intent 口径）。
- sheet 内的**复制**按钮：沿用现成的 `ContentCopy` + Toast 口径。

## 四、顶栏与 chrome（要拍的一处观感）

满屏之后没有滚动源，`rememberVeneraTopAppBarBehavior` 的折叠失去输入。两条候选：

- **A（建议）**：顶栏固定**折叠态**（小标题 + 返回 + 复制/在站点打开），与工具条一起被"单击图"切显隐。
  保留顶栏是因为用户上一轮点名"顶栏要和现在的样式同一"，不能像截图那样干脆没有。
- B：顶栏整个去掉，返回只靠下滑关闭 + 系统返回（最接近截图，但会丢掉"在站点打开"这个入口）。

满屏黑底上 `blurBackdropSource` 那层毛玻璃背板没有可糊的内容 → 顶栏底板改纯色（随 A/B 一并定）。

**同时纳入上一轮清单的 1+2**（不做这两条，满屏层的观感对不上截图）：

- 下滑关闭：`Animatable(offset = 窗口高)` + 阈值「速度 > 1 屏高/秒 或 位移 > 25% 屏高」（照它 `:1223`）。
- `PredictiveBackHandler` 把手势 progress 直接映射成位移（照它 `:1275`）。
- **放大时禁下滑**（`canDragDown = !zoomedIn`，照它 `:1342`），否则与平移抢手势。
- 画廊侧没有共享元素转场（全目录 grep `sharedElement` 零命中），所以满屏化不会撞
  「两端 key 必须同串否则静默不飞」那条坑。

## 五、交付切分与验收

1. **数据层**：`tagGroups` + Danbooru 五桶解析 + yande 单桶 → 单测（fixture 用今天实测那条 post）。
2. **结构层**：满屏 + 合并三级 + chrome + 下滑关闭/预测式返回 + 放大禁下滑。
3. **工具条**：HD 切档 / 下载（MediaStore 原样字节，含视频）/ 信息 / 分享。
4. **sheet**：`ModalBottomSheet` 版信息 + 分桶标签 + 复制行；`GalleryInfoCard`/`GalleryTagsCard` 迁入后删除。
5. **回归**：`testDebugUnitTest` 全绿（现 185 条基线）+ `assembleDebug`；真机由用户点：
   满屏观感、单击切 chrome、下滑关闭、预测式返回、HD 切档、下载是否真落相册、视频能否播与存。

## 六、明确不做（本轮）

- 心形收藏（等 P3 建表拍板）。
- 同图其他来源分段、`Pixiv URL` 第二行。
- WiFi/省流量自动 HD。
- `HorizontalPager` 横滑换张（上一轮清单第 4 项，要动导航载荷，单独评审）。
- 换 `composemediaplayer` 替掉手写 PlayerView（上一轮清单第 6 项，引第三方依赖面）。
- M3 Expressive 形变播放键（第 7 项）—— 与本次两张图无关，留给视频控件那一轮。

## 七、保护域

`Navigation.kt` 与底栏枚举**不动**（详情页路由参数仍是 `site + id`）；一级 `GalleryScreen` 不改；
漫画侧一行不碰。本轮改动全部落在 `gallery/` 包内 + 一个共用的 MediaStore 保存工具。

---

## 八、落地记录（2026-09-25 同日）

按 §五 的 1→4 全部落完，构建绿。逐条对账：

- **数据层**：`GalleryPost.tagGroups: List<GalleryTagGroup(label, tags)>`。
  Danbooru 五桶（通用/画师/角色/作品/元数据）、yande.re 一桶（标签）。
  切分口径收进 `splitGalleryTags`，`tagList`（黑名单判据吃的平铺串）与分桶共用同一个 splitter，
  不留两份实现。**分桶只给人看**：黑名单判定仍吃平铺那串，换成按桶判会让跨桶 tag 漏判。
- **结构层**：`GalleryPostScreen` 改满屏，`GalleryFullViewer.kt` **删除**
  （镜像在 `build/_trash-from-repo/GalleryFullViewer.kt.deleted`，等真机复看过再硬删）。
  两层图（底图 large + 上层当前档）、守卫 blur、骨架/进度环、"已按你的分级设置打码"那行
  全部上移到 `GalleryViewerMedia`。
- **定框**：`BoxWithConstraints` 里按 `cardRatio` 与视口比例算出贴图的框（横图按高、竖图按宽），
  圆角挂在这个框上 —— 黑底上裁圆角是看不见的，改成浅色底后必须让圆角贴着图才不露馅。
- **背景改浅色底**（`tokens.color.background` 而非纯黑）：`VeneraTopAppBar` 的标题色写死主题
  `textPrimary`，黑底会把标题糊成看不见。这一条同时决定了顶栏可以透明浮着（不传 `scrollBehavior`
  与 `backdrop` → 走组件里 `alpha = scrollProgress = 0` 那条路，即完全透明）。
- **chrome 三件套**：单击图切显隐（顶栏与工具条一起，带上下滑入滑出）；
  下滑关闭阈值 **25% 屏高** 或 **0.6 屏高/秒**；`zoomedIn` 时顶栏/工具条隐掉且**禁下滑**。
  `dismissing` 标志挡住二次 pop（本项目导航条目退场重组会二次 pop，见记忆同名条）。
- **预测式返回更正**：§四 原写"照它 `:1275` 挂 `PredictiveBackHandler`"，实现时判定**不做** ——
  本页是路由目的地，返回手势归 NavHost 的 seekable 机制（`components/PredictiveBack.kt` 的注释
  明写"Route pops belong to Navigation's seekable NavHost"），在页面里再挂一个 handler 会抢同一次手势。
- **工具条**：`GalleryViewerToolbar` = pill(`HD` / `下载` / `信息`) + 独立分享 FAB。
  `HD` 用 `HighQuality` 图标，**开启态用主色染色**（图标本身没有开关形态，不染色就分不清开关）。
  **视频条目不摆 HD**（站方没有更小转码档 = 假开关）。**没有心形**。
- **下载**：`GallerySaver` 走 MediaStore（`MediaStore.Files.getContentUri("external")` +
  `IS_PENDING` 两段式），**原样落字节**；图片 `Pictures/Venera`、视频 `Movies/Venera`；
  MIME 按扩展名映射，未知扩展名退回 `application/octet-stream`（不猜 `image/jpeg`）；
  半途失败 `delete(uri)`，不给相册留一张 0 字节坏图；Toast 报实际字节数。
- **InfoSheet**：`ModalBottomSheet`（沿用仓内既有 3 处 `rememberModalBottomSheetState` 口径，
  它在 alpha22 已标 deprecated，但换 `rememberBottomSheetState` 会与其他页分叉 —— 本轮不单独改）。
  内容是"分级/站点两小卡 + 尺寸评分收藏时长(+作者) + 出处与本站地址各带复制 + 分桶标签"。
  作者行**只在没有「画师」桶时摆**（Danbooru 那一桶已经列全，重复一行是噪音）。
  出处为空整块不摆。截图里的 `Pixiv URL` 第二行与 `Sources | Imageboard` 分段**没做**（无数据面）。

**构建**：`:app:testDebugUnitTest` **187 条 0 失败**（185 → +2：Danbooru 五桶与 yande 单桶各一条）/
`:app:assembleDebug` 绿，universal 包 98,118,361 → **98,169,049**（+50,688 B）。

**真机待验（用户点页面）**：① 满屏观感与圆角是否贴图；② 单击切 chrome、下滑关闭、放大后是否还能平移；
③ HD 切档是否秒出（底图在不在）；④ 下载是否真落相册（png 保留 alpha、视频落 Movies）；
⑤ sheet 在标签多的条目上能否滚到底；⑥ 打码态这一屏是否仍被糊（守卫链从三级搬上来后没验过）。

---

## 九、同日追加：毛玻璃底 + 去顶栏 + 合并成一条 Dock（用户看完真机后改判）

§八 那版落地后用户给了三条新要求：**背景做毛玻璃（糊 + 暗化）、顶栏整条去掉、
四个动作合成底部一条悬浮 Dock**。三条都做了，其中"背景怎么做"我自己先走错一格：

### 1. 先做成了一次性截屏，被否掉后换成实时重画

第一版是"点击那一刻把窗口画进一张 1/4 降采样的位图，详情页糊那张位图"。
用户一句话打回：「**背景模糊就行，不是一次性的**」。回头看这条判据是对的 ——
位图是**死的**：切 HD、转屏、下滑关闭时背景不跟着走，而且要在点击那一刻把整屏画进位图。

现行做法（`GalleryBackdrop` 只递**数据**，不递位图）：
一级在点击时把**守卫之后实际摆出来的那份列表** `publish` 出去，
详情页背后用 `LazyVerticalStaggeredGrid(userScrollEnabled = false)` **重新画同一面墙**，
整层挂 `blur(space11 = 32dp)` + 一层 `Color.Black(0.45f)` 压暗。
成本账：缩略图刚在一级显示过一轮，全在画廊那个独立 ImageLoader 的缓存里，
所以这层是**命中缓存的复用**；模型传**裸 URL**（与 `VeneraCover` 同一形状）就是为了撞上同一批 cache key。
递"守卫之后"那份，是为了背景里不会出现屏上没有的图。

列表为空（冷启动直接进详情、或一级被规则挡完）时这一层不画，退回 `tokens.color.background`。

### 2. 顶栏整条去掉，功能一个没丢

`VeneraTopAppBar` 从这一页删掉。返回因此只剩两条路：**下滑关闭** 与 **系统返回**
（预测式返回仍归 NavHost，见 §八）。原来挂在顶栏的两枚钮：
- `复制本站地址` → sheet 里那行 `本站地址 + 复制` 早就有了；
- `在站点打开` → 搬进 sheet，新增一行 `LinkRow`（同形状，尾巴换成 `OpenInNew`）。

图占满屏之后只让开 `状态栏 + space5`。
**顺带一条必须跟着改的**：底变成深色玻璃后，系统两条栏的图标要翻成浅色 ——
全局那处（`feature/VeneraTheme.kt:116`）会把图标设成深色（浅色主题下），压在黑玻璃上看不见。
那个 SideEffect 只在主题变化时跑，不会替本页翻回来，所以本页进出各设一次
（`DisposableEffect` + `WindowCompat.getInsetsController`，离开时按 `!isDark` 还原）。
页内直接画在玻璃上的文字（"已按你的分级设置打码"）改走 `StatusColors.OnBadgeSurface` 固定白。

### 3. 两截合成一条 Dock

§八 的排法是"pill(HD/下载/信息) + 右边独立分享 FAB"（照截图）。现在按用户要求合成**一条**：
`HD` / `下载` / `信息` / `分享` 四枚全在那颗 pill 里，FAB 去掉。
其余判据不变：视频条目不摆 `HD`（站方无更小转码档 = 假开关）、保存中图标换波浪环并吃掉点击、
**仍然没有心形**（P3 建表未拍板）。

### 4. 落地与账

- 新增 `gallery/ui/GalleryBackdrop.kt`（只存 `posts`，`mutableStateOf`，快照换列表时页面会重画）。
- `GalleryScreen` 卡片点击处 `publish`；`GalleryPostScreen` 加 `GalleryFrostedBackdrop`、删顶栏、
  加两条栏图标翻转；`GalleryViewerToolbar` 合一条；`GalleryInfoSheet` 加 `LinkRow`。
- `:app:testDebugUnitTest` **187 条 0 失败**（本轮无新增判据可测，全是观感与手势）/
  `:app:assembleDebug` 绿，universal 包 98,169,049 → **98,179,001**。
- **真机待验（新增三条）**：① 背景那面墙重画会不会掉帧（40 张预览全走缓存，但仍是第二次组合）；
  ② 模糊层与主体那张图叠在一起时，下滑关闭过程中背景不动、内容下移，观感是否像"抬起离玻璃"；
  ③ 深色玻璃上状态栏/导航栏图标是否真翻成浅色（浅色主题下尤其要看）。

---

## 十、改判：大图页搬进独立 Activity（真实时模糊 + 跨 activity 预测手势 + 飞入）

§九 的"重画一面墙"被用户判为**不是实时**（它永远从列表第一行开始糊，对不上刚看到的那一屏），
同时追加两条：跨 activity 预测式返回、图片从卡片飞进详情页。三条合起来只有一条路——

> **壳内 overlay 拿不到跨 activity 系统动画**（只有跨过 Activity 边界，`targetSdk ≥ 36`
> 才会被系统施加 AOSP 预测式返回；这条判据与实现都在 `VeneraSubActivityBase` 的类注释里）。
> 所以 §九 选的"壳内 overlay"作废，改走 `GalleryPostActivity`。

三条要求一次满足：

| 要求 | 由谁给 |
|---|---|
| 实时背景模糊 | 该窗口的 **blur-behind**（系统实时糊掉后面的 MainActivity，也就是真那一屏一级列表） |
| 跨 activity 预测手势 | Activity 边界 + `targetSdk 37`（≥36）→ 系统施加，不用自己写 |
| 图片飞入 | **只能自己画**（见下） |

### 1. 平台没有可用的跨 activity 共享图像 API（实测平台 jar）

翻了本机 SDK 的 `platforms/android-{33,34,35,36}/android.jar`：
`android.window.*` 里只有 `SplashScreen` / `SplashScreenView` / `SplashScreen$OnExitAnimationListener`
（那是**启动图**那套），**没有** `SplashScreenViewProvider`、也没有
`overrideNextTransitionSplashScreenStartingPoint`。
所以 Android 14/15 文档里那种"系统帮你把图从上一个页面的矩形飞过来"在本项目够不着。

剩下的两条：
- 经典 View scene transition（`ActivityOptions.makeSceneTransitionAnimation` + `View.transitionName`，API 21+）
  —— 它要**真的 View** 带 transitionName，而我们的卡片是 Composable，
  框架会去搬整个 `ComposeView`，不是一行图。不划算。
- **自己画**：点击那一刻把那张卡片的首帧位图 + 它在窗口里的矩形交给新 Activity，
  新页面用 `graphicsLayer` 从那个矩形弹到位。系统那套打开动画用
  `ActivityOptions.makeCustomAnimation(ctx, 0, 0)` 压掉，免得两套动画打架。
  选这条。（顺带：§九 被否掉的"一次性截图"在这条路上反而有了正当用途——它就是飞入的那张图。）

### 2. 窗口必须真的透

`VeneraSubActivityBase` 现在给内容铺了一层**不透明**的 `VeneraAmbientBackground`
（设置子页需要它，否则会露出平台深色窗口底）。大图页正相反：要的就是透出后面那屏，
所以基类要开一个口子让子类关掉这层底，并给一个可覆写的模糊半径
（18dp 是"内容重度模糊"口径，照片墙这种整屏底要更糊）。
主题换 `@android:style/Theme.Translucent.NoTitleBar`（`windowIsTranslucent` + 透明窗口底）。

### 3. 连带要删的东西

- `GalleryFrostedBackdrop`（重画的那面墙）与 `GalleryBackdrop` 里递列表的那半套 —— 系统糊了，
  再画一份就是白烧一次组合。
- `GalleryPostRoute` 这个目的地（`Navigation.kt`，用户已授权改）：改完点卡片是起 Activity。
  深链/外部入口若指过这条路由要一并检查，别留下点了没反应的入口。

### 4. 落地记录

- 新增 `GalleryPostActivity`（继承 `VeneraSubActivityBase`）+ manifest 一条
  （主题 `@android:style/Theme.Translucent.NoTitleBar`）+ `Activity.openGalleryPost(site, id)`。
- `VeneraSubActivityBase` 开了两个口子：`opaqueAmbientBackground`（本页必须 false，
  否则基类那层不透明氛围底会把系统糊好的东西盖死）、`blurBehindDp`（本页 32dp，
  默认那档 18dp 是打码封面口径，糊不动整面照片墙）。
- 新增 `GalleryFlyIn`：点击那一刻只截**卡片那一块矩形**（画布平移后整屏只写进这张小位图），
  连同窗口坐标矩形交给新页面；`GalleryPostScreen` 用 `graphicsLayer` 在两个矩形之间插值弹到位，
  **飞完且大图真正落位**才收（早收会在"弹到位"与"图加载好"之间露出一块玻璃 = 闪一下）。
  起终点圆角是同一个 token（`shape.large`），所以半径不插值。
- 删掉：`GalleryFrostedBackdrop`（重画的那面墙）、`GalleryBackdrop`（递列表那半套，
  镜像在 `build/_trash-from-repo/`）、`GalleryPostRoute` 这个目的地与它的 `composable`。
- 页内根 Box **不再铺不透明底色**（那是 blur-behind 透出与否的开关），只留一层
  `Color.Black(0.45f)` 压暗；状态栏/导航栏图标翻转那套照旧。
- 单测 **187 条 0 失败**（本轮全是观感与手势，没有新的纯函数判据可钉）。
- **包体口径纠正**：clean 构建 universal 包 = **97,573,787**。本节之前记的那些数
  （98,118,361 / 98,169,049 / 98,179,001 / 100,934,541）都是**增量打包**产物
  （20 个 dex、`classes.dex` 42 MB 那种脏填充），**不能与 clean 数相减**，
  本轮真实增量待下一次 clean 基线出来再说。

### 5. 真机待验（这一轮的三条是新的）

① 背景是不是**真在动**：在一级滚到中间再点进去，糊的底应该就是你停的那一屏；
② 跨 activity 预测式返回有没有系统的缩放/圆角/遮罩（`targetSdk 37 ≥ 36` 才给）；
③ 飞入：卡片那一帧是否从原位弹到大图位、有没有"弹到位后闪一下玻璃"；
④ ColorOS 若关了跨窗口模糊（`isCrossWindowBlurEnabled` 为 false），基类会**不挂** blur-behind ——
那时背景就是透出的原屏（不糊），这是设备能力，不是我们画了个假的。

---

## 十二、真机第一轮反馈四条（同日第二轮，2026-09-25）

§十 那版装到真机后，用户回两张截图 + 四条。逐条：

| # | 反馈 | 处置 |
|---|---|---|
| 1 | 进场改成 Breadboard 那样**向上滑入** | 已改（§12.1） |
| 2 | donmai 开 HD 会弹 Cloudflare 验证页 | 已改（§12.2，根因不是"该不该弹"） |
| 3 | 详情页状态栏那一条是黑的，要跟背景一样糊 | 已改（§12.3，换本仓库自己的主题） |
| 4 | 漫画详情页底栏加收藏；画廊收藏进「图片收藏」并与漫画图片收藏分开 | **待拍板**（§12.4） |

### 12.1 进场：从"两矩形插值"改成"整页抬上来"

参考实现是 Breadboard 的 `OffsetBasedLargeImageView`：不做形状插值，整张图从屏幕下沿平移到位。
比原来那套两矩形 `scaleX/scaleY` 插值干净两条：非等比拉伸没了（原方案把卡片矩形拉到大图矩形，
横竖图都会有一瞬间变形），圆角也不用管两端是否同径。

- `GalleryFlyIn` 从此**只递位图**，不再递窗口矩形（`Payload` 这个 data class 删掉）；
  卡片那边 `onOpen` 的矩形只用于裁剪，大图页不再需要终点矩形。
- 占位那一帧挪进 `GalleryViewerMedia` 的框里当**第三层**（`fillMaxSize` + `Crop`）：
  卡片与这块框同一个 `cardRatio`，所以 `Crop` 不改变构图，大图到位是"变清晰"而不是"跳一下"。
  于是 `onBoundsChanged` / `mediaBounds` / `TransformOrigin` 那套几何全删。
- 落位闸门换了变量：原来是"当前档 loaded"，现在是 `firstTierSettled`（**第一档**成功或失败都算）。
  切 HD 时 `loaded` 会重新变 false，那一帧不能又盖回已经看清的图上。
- 滑入挂在**内容外层那一个 Box** 的 `graphicsLayer.translationY` 上，起点 `screenHeightPx`、
  弹簧 `spring(StiffnessMediumLow)` —— 与下滑关闭同一档，进与出手感对称，不另造数字。
  **压暗那层不参与滑入**：玻璃是"后面那一屏"，页面抬起来时它当然不动，这才是图从玻璃后面升起来。
- 交还时机补了一条：详情取不到（`error != null`）也要交，否则这张位图一直攥在 `GalleryFlyIn` 里。

### 12.2 HD 撞盾：图片流量不再走交互式过盾

截图那页是 `CloudflareBypassActivity` 自己的界面（标题里那串 `f539…jpg (1292×2500)` 是
WebView 直接渲染 JPG 时 Chrome 给的页面标题）。也就是说：图片请求撞盾 → 拦截器
`runBlocking` 拉起过盾页 → 用户在 WebView 里看着图加载出来。

本机 curl 实测（同一张 `cdn.donmai.us/original/4e/46/…jpg`，逐档都试过）：

| 请求 | 结果 |
|---|---|
| `-A "Venera/1.0 (Android)"` | **200**（`cf-cache-status: HIT`） |
| `-A "<Chrome 128 移动串>"` | 403 + `cf-mitigated: challenge` |
| Chrome 串 + 全套 `Sec-Fetch-*` + `Accept: image/*` | 403 + challenge |
| 同一串 Chrome 串打 `180x180` / `sample` | 同样 403 + challenge |

→ CF 对 donmai 判的是"**你自称 Chrome，可你的 TLS/H2 指纹不是**"，越装越像 bot；
老实报 `Venera/1.0 (Android)` 反而放行。这与仓内 `ImageHeaderPolicy` 里那段缩略图实测同口径，互相印证。

于是"过盾 → 把 WebView 那串浏览器 UA 绑到该 host → 用它重放"这条链在图片上是**自相矛盾**的：
重放必然再撞一次盾。再叠加第二条代价 —— 这里是 `runBlocking` 等一次人机交互，
跑在 Coil 的取图线程上，一张图能把一个 worker 挂到用户点"取消"为止。
结论：图片流量撞盾 = 原样把 403 交回，由调用方说一句话。

- `CloudflareBypassInterceptor` 新增纯函数判据 `offersInteractiveBypass(request)`
  （= 这条请求**没**打 `ImageFetchTag`）。图片那支 `return response`，API/网页流量一字未动。
  与域名熔断共用同一个标记，这条"两处豁免必须一起换"的约束有单测钉着
  （`CloudflareBypassScopeTest`，3 条）。
- HD 失败从此**必须说话**：`GalleryViewerMedia` 的 `onError` 在 `preferHd` 时回调 `onHdError`，
  父层把 `preferHd` **拨回 false** 并 Toast 出 `HTTP 403`。
  留着 HD 亮着、画的却是原档 = 假开关（这条是全站口径，不是审美问题）。
  原档失败仍不响：底下本来就压着同一张图的另一档，说句话反而是噪音。

⚠️ **没查清的一半**：设备上"缩略图通、原图撞盾"这件事本机 IP 复现不出来（我这边三档全 200）。
剩下两种可能都在上游：`/original/` 那条规则更严，或匿名侧按 IP 的取图限流。
应用侧能做的（不弹验证页、失败如实报）已做完；**没有**为了绕盾去装浏览器指纹 —— 实测那只会更糟。

### 12.3 状态栏那条黑带

`@android:style/Theme.Translucent.NoTitleBar` 只管"窗口透"这一件事，它
`windowDrawsSystemBarBackgrounds=false` —— 状态栏那一条不由本窗口画，系统就在上面补了个
不透明黑底，于是糊好的背景到状态栏下沿齐刷刷断掉（截图里那条黑带，通知图标全在黑底上）。

新增 `res/values/themes.xml` 里一份 `Theme.Venera.GlassOverlay`（父主题仍是平台那个），四条：
`windowDrawsSystemBarBackgrounds=true` + `statusBarColor`/`navigationBarColor` 透明
+ `enforceStatusBarContrast`/`enforceNavigationBarContrast` = false（Android 10 起系统会自作主张
压一层渐变黑"保证图标可读"，这里图标颜色由页面自己翻浅色，不需要它压）
+ `windowLayoutInDisplayCutoutMode=shortEdges`（刘海屏上那条又是黑的）。manifest 指过去。

顺带一条事实：`app/src/main/res/values/` 之前是**空目录** —— 整仓没有 themes.xml，
三个 Activity 全用平台主题。这是第一份自有主题资源。

### 12.4 收藏（P3，这条要拍板才动代码）

用户这次把 §〇 那条"先不摆心形"的前提推翻了 —— 当时否掉是因为"画廊没有落库的表"，
现在明确要建。所以画廊 Dock 该补心形。

现状（已核对代码）：

- `favorite_images`（`VeneraDatabase` v3）那 5 列 `comic_id / comic_title / source_name /
  chapter_title / page_index` 全是 **NOT NULL 且无默认值**，语义是"漫画里的某一页"。
  画廊那张画没有母漫画。墙上的"作者"也不是表里的列，是 `authorIndex()` 拿 `comic_id`
  去 `reading_stats.tags` + 本地收藏反查出来的 —— 画廊行没有 `comic_id`，这条链整体失效。
- 写库只有一处：阅读器 `VeneraReaderScreen.kt:2020`（先 `persistPage` 落一份 jpg 再建行）。
- 详情页底栏那枚"收藏"是**漫画级**（`openFavoritePanel` / `quickFavorite` → `comic_favorite`），
  与图片收藏无关。
- 图片收藏墙 `FavoriteImagesScreen` 没有分段、没有分组，纯瀑布流；刷新靠进入组合时拉一次。
- `gallery-module-isolation-plan` 里有既往裁决："不把图库并进 `favorite_images`"。

三条路：

| 方案 | 做法 | 代价 |
|---|---|---|
| A | `favorite_images` 加一列 `kind`，画廊行把漫画那 5 列填空串 | 翻掉既有裁决；"为了塞进别人的表而撒谎"；补作者那条链对画廊行整体失效 |
| **B（推荐）** | 同一个 `venera_core.db` 里另建 `gallery_favorites`（v3→v4），列按画廊自己的语义：`site / post_id / author / tags / rating / large_url / file_url / card_ratio / created_at`；墙里加一枚两段切换（漫画图片 / 画廊） | 一次迁移 + 一个 Manager + 墙加分段。语义各归一张表，备份/清库仍是一条链 |
| C | 再开一个 db 文件（照 `LocalFavoriteDatabase` 先例） | 与 B 相比只多了"两份 helper、两条备份"，而"同一面墙要一起读"这个需求用不上这份隔离 |

还有两处要拍：

1. **存什么**：只存站方 URL（Coil 重取，不占本地空间，离线看不了）还是照阅读器 `persistPage`
   落一份本地副本？推荐**只存 URL** —— 阅读器那套落盘是因为漫画源会失效，而画廊这两档是公开 CDN，
   重取成本≈0。
2. **"漫画详情页底栏新增收藏"这句有两种读法**：
   (a) 底栏**再加一枚"收藏封面"**，把这张封面存进图片收藏（与已有的漫画级收藏钮并存，两件事）；
   (b) 指的是阅读器那一枚（已存在），想把它也搬到详情页。
   推荐 (a)：它落库时 `comic_id` 用真实漫画身份，补作者那条链照样有效，且与"分开"那半句对得上。

拍板后的改动面：`VeneraDatabase` v4 + `GalleryFavoritesManager` + Dock 心形（带已收藏态）
+ `FavoriteImagesScreen` 分段 + 详情页底栏一枚。保护域：底栏枚举不动
（详情页底栏是 LazyColumn 里的一个 item，不是 `Navigation.kt` 那套）。

### 12.5 本轮账

- 单测 **190 条 0 失败**（新增 `CloudflareBypassScopeTest` 3 条，钉"图片不弹过盾"这条判据）。
- 包体：增量打包 universal = 98,350,161。**这是增量数，不能与 §十 那条 clean 基线 97,573,787 相减**
  （口径见 §10.4 最后一条）。
- 第一份自有主题资源：`app/src/main/res/values/themes.xml`（此前该目录是空的）。

### 12.6 真机待验（本轮三条新的）

① 进场是不是"整页从下沿抬上来"、背景那条玻璃**没跟着动**；
② donmai 开 HD：不再弹验证页，改为 Toast 报 `HTTP 403` 且 HD 钮自己弹回原档；
③ 状态栏那一条是不是跟下面一样糊（若仍是黑带，说明 ColorOS 不吃 `enforceStatusBarContrast`，
下一档就要在窗口上直接调 `isStatusBarContrastEnforced`）。

## 十三、真机第二轮反馈三条（同日第三轮，2026-09-25）

| # | 反馈 | 状态 |
|---|---|---|
| 1 | 「Danbooru 搜索不出图」（19:16 截图：一排尺寸对、内容全空的灰卡） | 已改，根因与那三条处置见 `gallery-search-2026-09.md` §十一 |
| 2 | 搜索层从全高 sheet 改成**画廊页内的搜索头**（19:19 那张 Hero 头截图为图二） | 已改，见同一文档 §一 / §十 |
| 3 | 「之前图片信息做的 tag，也要做到点击搜索，长按显示搜索 复制和屏蔽选项」 | 已改，见本节 |

### 13.1 InfoSheet 那五桶标签从"纯展示"改成可交互

画法**逐项照漫画详情页那枚药丸**（`feature/ComicDetailScreen.kt` 的 `DetailTagChip`）：
点击=直达该标签搜索，长按=菜单，菜单**逐项各挂一个锚**（FlowRow 下共用锚会永远从第一枚弹出，
`FavoriteImagesScreen.kt:601` 记着同一件事）。三项里"搜索"与点击重复是刻意的 ——
长按菜单是这块区域"能做什么"的目录，缺了它用户只能靠猜知道点击也可以。

`GalleryInfoSheet` 里原来那句「chips 刻意不可点，因为 `/tag.json` 那条链还没实测」的前提
**已作废**（两站的标签检索与结果接口本轮都实测过了），文档与代码同步改掉。

### 13.2 「屏蔽」为什么存成 `TAG` 而不是 `KEYWORD`（量的，不是挑的）

画廊那面墙的屏蔽判据 `GalleryBlockMatch.blocked` 对 tag 只吃 `post.tagList`
（= Danbooru 的 `tag_string`、yande.re 的 `tags`），作者名只对 `KEYWORD`/`AUTHOR` 生效。
2026-09-25 实测 **60 条 post、五桶（general/artist/character/copyright/meta）共 2280 个 tag 串，
没有一个不在 `tag_string` 里** —— 也就是「画师」那一桶的 tag 同样在 `tag_string` 内。
于是每一枚 chip 的原串存成 `TAG` 都**必然命中**，那句"已屏蔽「x」"不是假成功；
换 `KEYWORD` 只会多拿作者名做子串匹配，白白多一层误伤面。
这条不变量已上单测（`GalleryDanbooruParsingTest`），并在同一轮**校正了旧 fixture 里
`tag_string` 少写两支的想当然**（原来按"tag_string 只是 general"写的，与实测不符）。

### 13.3 跨 Activity 的交接槽（点标签要回一级画廊搜）

大图页是独立 Activity，搜索状态在 MainActivity 那侧的 `GallerySearchViewModel` 里，
两边没有共同的 ViewModel 作用域、也不是 NavHost 目的地 → 新开一个
`gallery/ui/GallerySearchHandoff.kt`（方向与 `GalleryFlyIn` 相反）。两点要紧的：

- 槽位字段是**快照状态**：写入方在另一个 Activity，靠快照失效叫醒这边活着的组合。
  大图页是透明窗口，底下那屏只是 paused、并没有停组合，所以**不能等 `onResume`**（透明窗上下
  的生命周期与不透明 Activity 不同，等它可能收不到）。
- 「取用一次即清」+ 消费方**只把它写进活下来的 ViewModel、不挂"取不到就 pop"的自毁分支**
  —— 这是记忆「导航条目会重建组合」第三族的两条硬要求。

点标签之后走 `onBack()`（= `finish()`）而不是页内那套下滑关闭动画：多那 300ms 挡在结果前面，
看着像点了没反应。画廊侧 `LaunchedEffect` 消费时**整片替换** chips 而不是往上加
（另一张图的上下文，留着旧的会误导；Danbooru 只有 2 枚预算，多留一枚还可能当场顶满）。

### 13.4 本轮账

- 单测 **210 条 0 失败**（新增 1 条：五桶 tag ⊆ tagList）。
- 包体：增量打包 universal = 101,825,633 / arm64 = 99,408,252。**仍是增量数，
  不能与 §12.5 那条 98,350,161 或 clean 基线 97,573,787 相减**（口径见 §10.4 最后一条）。
- 顺带堵掉一帧假读数：搜索第 1 页还在飞、屏上还没落卡时，页尾那句会写「已摆出 0 张；上滑继续取」，
  而此刻上滑什么都不会发生 → 这一帧改摆整页级波浪环（全站"整块重拉"那条口径）。

### 13.5 真机待验（本轮三条新的，其余见搜索方案 §十三）

① 大图页 `(i)` 里**点**一枚标签：这一页立刻关掉、画廊切进搜索态并**直接出结果**
   （不停在输入态）。这条跨 Activity 的交接不靠 onResume，要在"刚 finish、画廊还没重新拿到焦点"
   这个竞争窗口里现验一次；
② **长按**一枚标签：菜单从被长按的那一枚上面弹（不是永远第一枚）；三项各验一次，
   屏蔽后回画廊，含该 tag 的卡**整张消失**（`TAG` 命中即 HIDDEN，与「成人内容处理」档位无关），
   再长按同一枚点屏蔽应说"已在屏蔽列表"；
③ sheet 里再开一层 `DropdownMenu`（弹层里的弹层）在 ColorOS 上有没有被裁 ——
   被裁就调 `DropdownMenu` 的 offset/overflow 参数，而不是另造一套 UI。

## 十四、2026-09-27：分享「一次都没成功过」的真因 + Gelbooru 视频那一摊

用户真机一张截图定案：`分享没打开：Parcelable encountered IOException writing serializable
object (name = kotlin.Result)`。上一轮（§cd9a1d2）的提交信息写着"把分享换成真图片"，
但那条路**从来没通过** —— 又一次印证「commit/文档写已落地要回代码核」。

### 1. 分享：`Result<Uri>` 被当成 `Uri` 塞进了 Intent

`runCatching { … }.onFailure { … }` 交出的是 **`Result<Uri>`**，不是 `Uri`
（`onFailure` 返回 `this`）。于是 `putExtra(Intent.EXTRA_STREAM, uri)` 在编译期
静默挑中了 `putExtra(String, Serializable)` 那个重载 —— `kotlin.Result` 声明了
`java.io.Serializable`，所以**类型检查放行**；运行期 Parcel 用 `ObjectOutputStream`
写它才炸。表现就是：图片、视频、两站，**每一次分享都失败**，且失败得像是"分享面板打不开"。

修法两条，缺一不可：
- `.onFailure{}` → **`.getOrElse{}`**（真的把 `Uri` 取出来）；
- **显式写类型** `val uri: Uri = …` —— 这一条才是防复发：下次再有人把 `Result` 赋给
  一个要当 `Uri` 用的 val，编译器直接拦下，而不是落在用户手上。

顺手同一条：落盘那三步（`mkdirs` + 写 16~26 MB + `getUriForFile`）原先跑在
`Main.immediate` 的续点上 = 拿主线程写几十兆，能卡出 ANR，已挪进 `withContext(Dispatchers.IO)`。

**没有加运行时回归用例**：本仓单测是纯 JVM（无 Robolectric），`Intent` 在测试类路径上是
会抛的桩，Parcel 那一层测不到。真正的防线是调用点那句**显式类型** `val uri: Uri = …`。
另加了 `GalleryShareResultTrapTest`（3 例）把**成因的两条前提**钉住：
`onFailure` 返回 `Result` 而不是值、`getOrElse` 才交值、`kotlin.Result` 声明了 `Serializable`
（所以重载选择在编译期不报错）。它测不到那次崩溃本身，测的是"为什么编译器不拦"。

### 2. 分享在途没有任何反馈

`sharing` 标志早就有（防重入），但工具条没接它 —— 视频原片要先下一次，
那几秒里点一下什么也不动，读起来就是"按钮坏了"。现在与「下载」同一形态：
进行中把图标换成波浪环并吃掉点击。

### 3. Gelbooru 视频的底图档 = 原片 mp4（先白下几十 MB，再解不出来）

`GalleryGelbooruParsingTest` 第 140 行原本写着"视频底图会落空，这一条由 UI 那边处理"，
而 **UI 并没有处理**：`GalleryVideoViewer` 直接把 `largeUrl` 喂给 Coil。
Gelbooru 对视频的 `sample_url` 给空串 → 翻译时兜底成 `file_url`（原片 mp4）→
Coil 先下十几二十 MB、再解码失败、失败之前那一屏什么都没有。

修法：新增 `GalleryPost.videoPosterUrl`，判据是**「large 档与原片同址 = 站方没给静帧」**，
只有那种情况才退到 `preview_url`（实测恒非空）；yande.re 那一路 large 档本来就是
jpg 静帧，保留它（比 350px 缩略图清楚）。两条各一个用例锁住 —— 先按**今天的实现**
（`= largeUrl`）跑红，确认用例不是空的，再改判据跑绿。

⚠️ **缓存键必须一起分开**（视频用 `poster`、图片用 `large`）：Coil 的显式键不认地址，
那个键下已经存过 mp4 字节，只换判据的话真机还会命中那份坏数据 ——
就是"修好了但黑屏依旧"。旧条目交给那 512 MB 的 LRU 收拾。

### 4. 两处假读数与一处静默失败

- 播放钮文案原本恒等于 `播放 · ${fileSize/1024/1024} MB`，而 **Gelbooru 的 JSON 没有
  `file_size`**（实测，翻译为 0）→ 每条视频都写着"播放 · 0 MB"。改成读数没有就不摆。
- 时长同样按 `> 0` 判，不再对 null 编数。
- `GalleryPlayer` 原来**没有任何错误监听**：media3 走自己的 HTTP 栈（不过
  `VeneraNetworkClient`、拿不到防盗链那张表），所以"缩略图看得到、点开播不了"最容易
  在这层发作，而表现是一块黑框、零线索。现在 `onPlayerError` 报一句
  `errorCodeName + message`，并把播放钮还回去（不留假状态）。

### 5. 视频播不出来的真因：防盗链那个 302 被 media3 当成媒体在解析（真机读数定案）

新加的报错出口第一次发作就抓到读数：`这条播不出来：ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED
Source error` —— **不是 403、不是网络**，是"拿到字节了但那个容器不认识"。

拿测试夹具里那条真实视频地址复现（2026-09-27，本机 curl）：

| 送出去的请求 | 站方回 |
|---|---|
| 只有 UA（= 旧实现的全部） | `302 → https://gelbooru.com/hotlink.php?hash=/images/…`，`Content-Type: text/html` |
| 加 `Referer: https://gelbooru.com/` | `206 video/mp4`，前 12 字节 `\0\0\0 ftypisom`，Range 正常（能 seek） |

media3 会**跟着那个 302 走**，然后把 hotlink 页的 HTML 交给 extractor 当容器解析 ——
报出来正是那个码。gzip 也排除了：同一地址带 `Accept-Encoding: gzip` 照样回裸字节 + 206。

**这张表里早就有正确条目**：`ImageHeaderPolicy` 的 `gelbooru.com` = `Referer` + `User-Agent`，
注释里还记着图片侧同一个坑（302→hotlink.php、以及"这不是地域封禁"那段弯路，见 cfc965d）。
视频这一层当时**只搬了 UA、没搬 Referer** —— 而它自己的注释恰好预言了
"不带就会出现'缩略图看得到、点开播不了'那种最难查的错"，预言中了却没接。

修法：删掉本地那把按站别 switch 的 `userAgentFor(post)`，改成 `videoRequestHeaders(url)`
**直接吃整张表**；表里没有 UA 的站（yande.re 就是）补全局默认串 —— 只交表里那几点
等于把现在能正常播的那一路改成"不带 UA"，那是修一个坏一个。
从此图片与视频共用一把判据，不会再各修各的。

⚠️ API 名：media3 1.11.1 上是 **`setDefaultRequestProperties`**，没有 `setRequestProperties`
（按旧名写直接编译不过 —— 这一版是靠编译器而不是靠记忆定的）。

### 6. 本轮仍**没做**的

- 视频进度条样式、M3 Expressive 形变播放键（§六 第 7 项）仍归"视频控件那一轮"。

### 7. 验证

`testDebugUnitTest` **228 例全绿**（新增 5 例：视频底图 2 例先按今天的实现跑红、确认用例
不是空的再改判据跑绿；分享成因 3 例）、`assembleDebug` ✅（01:38 那份起修好分享，
01:56 那份起修好视频播放）。
真机归用户：① 图片条目分享应真把图发出去；② 视频条目分享原片、且那几秒有波浪环；
③ Gelbooru 视频条目点播放 —— 底图应是缩略图而不是黑屏，播不了要报一句话。

## 十五、2026-09-27 第二轮：视频"点了没反应 / 起播慢 / 没有全屏钮"

用户一次报三条，三条各自的根因不同，分开记。

### 1. 缓冲中没有任何读数（"点了没反应"）

快门色是**透明**的（起播前不闪黑），所以首帧之前屏上看到的**就是底图那张静帧** ——
"正在缓冲"和"什么都没发生"长得一模一样。
现在 `GalleryPlayer` 自己持两枚独立读数：`buffering`（只认 `STATE_BUFFERING`，
`ENDED` 不算，否则片尾会挂着一枚假"加载中"）与 `firstFrame`（`onRenderedFirstFrame`）。
首帧之前、或中途 rebuffer 时，画面正中挂全站口径的波浪环 + 一行字（缓冲中 / 重新缓冲中）。

### 2. "加载完了还停在预览，得再点一下才动"

`playWhenReady = true` 在 `prepare()` 之前就设了，所以"进 READY 却没在播"只可能是被压住
（音频焦点 / 恢复策略）。现在在**首帧之前**那次进 READY 时补一次 `player.play()`；
不在首帧之后补，否则用户自己暂停后一次 seek 就被替他按了播放。
同时每次状态变化写一条 `Log.d("GalleryVideo", state/playing/suppressed/buffered)` ——
若真机还犯，这条读数直接指出是哪一个压住的。**这一条我没能自己定案**（要人手点一次），
所以它是"补一手 + 留读数"，不是"已证实根因"。

### 3. 起播慢：不是 faststart，是 media3 默认的起播缓冲

先量了再改（免得照着传闻去治一个不存在的病）：
```
Range 0-262143 → Content-Range: bytes 0-262143/2493372
偏移 36   = ftyp   偏移 36    = moov（在前 48 KB 内）
偏移 48413 = mdat
吞吐：256 KB / 3.06s ≈ 84 KB/s
```
→ 这些 mp4 **是 faststart**，`moov` 在最前面，不存在"整片下完才播"那种成因，
所以**没有**去动什么"预取 moov"的歪路。

真凶是 `DefaultLoadControl` 的默认 `bufferForPlaybackMs = 2500` —— 它攒的是
**2.5 秒"媒体时长"**，而实测这条 26.3 秒 / 2.4 MB 的流媒体码率约 92 KB/s，
2.5 秒就是两百多 KB；池子里那些 16~26 MB 的原片要先攒好几 MB 才肯动。
84 KB/s 的吞吐下，光这一段就够"很慢"。
现在改成 `10s / 30s / 500ms / 1.5s`（min/max/起播/rebuffer 后）。
代价：慢网下更容易中途 rebuffer —— 那有 §1 那枚"重新缓冲中"的环在说，不是静默。

### 4. 全屏钮：media3 一直带着它，是我们没设监听器

`javap` + 抄 aar 的 `res/layout/exo_player_control_view.xml` 确认：
`PlayerControlView` 里有 `fullscreenButton` / `minimalFullscreenButton`，
可见性按"设过 `FullscreenButtonClickListener`"判 —— 我们从没设过，所以那枚钮**一直不存在**。
现在设了（`setFullscreenButtonClickListener` + `setFullscreenButtonState` 翻图标）。

⚠️ 一个几何事实，差点在这里做出一个假开关：**FIT 下把容器扩大不会让画面变大** ——
画面尺寸由受限的那一边定，而竖屏看横屏视频时那一边本来就是屏宽（实测 639×470 已铺满宽度）。
所以只扩框 = 点了没反应。现在这枚钮同时换 `resizeMode`：
**裁切铺满（ZOOM）↔ 还原（FIT）**，代价写在了代码注释里 —— 竖屏看横屏视频时 ZOOM 只留中间那一条。

**没做的另一半**：真·横屏全屏（藏系统栏 + 请求 landscape）。它才是"画面明显变大且不裁"的那条路，
但 `GalleryPostActivity` 没设 `configChanges`，转屏会重建组合 → 播放器重建 + 重新缓冲，
要先处理那一层才谈得上。要不要做请拍板。

### 5. 验证与待验

`testDebugUnitTest` 228 例全绿、`assembleDebug` ✅（02:17 那份起）。
真机三条：① 点播放应立刻看到波浪环（不是"没反应"）；② 起播应明显快于上一版；
③ 控制器右下角应有全屏钮，点了画面会裁切铺满。
若②仍慢或②里"停在预览"还犯，我读 `adb logcat -s GalleryVideo` 就能定，不用再猜。
