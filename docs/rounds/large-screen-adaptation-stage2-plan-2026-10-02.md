# 大屏适配第二轮方案（档位模型 + 契约解法 + 侧栏落地）

> 状态：**§7 十三项已于 2026-10-02 全部照推荐批准**；**批次 1、批次 2 均已落地**（批次 2 主体由并行线写、本线收下并补齐 2 处 explore + 修复提交完整性，见 §8.5）；批次 3 仍暂缓
> 拍板项 13（侧栏档收顶栏）**未照做并已被推翻**，理由与代价记在 §8.5
> 日期：2026-10-02　范围：**只做 Android 端大屏适配**。桌面化（R1-F）暂停、冻结不扩建（见 §0）
> 依据：`master` 分支 Flutter 源码（逐条复核，命令与行号见 §1）
>
> ⚠️ 落地时纠正了本文 8 处事实错误（provider 落点、R5 命令名、600 归属自相矛盾、
> 消费点计数、批次 1 独立价值、剩余 20 处的迁移义务、`VeneraTokens.spacing` 转发读法、
> 拍板项 13 的前提），逐条见 **§8** 与 §附修正表。

---

## §0 前置决策

### 0.1 桌面化暂停的裁决与理由

**决定**：R1-F **跑到阶段 1 结束即冻结**，不进入阶段 2。

**理由**：`windows-port-feasibility-2026-10.md:197` 的阶段 1 = 「壳层与首屏」，要点含 **`NavigationView`**。而 compose-fluent 的 `NavigationView` 有**自己的默认宽度**，不是官方的 72/224。两边都建侧栏就是两套宽度 —— 即「第二套布局系统」。

**冻结处置**：标记「冻结不扩建」，**不是废弃**。三模块已在编译面内（`settings.gradle.kts:27-31`），冻结比移除便宜；`engine-probe` 是 S0 探针结论的载体，删了以后无法回答「为什么不做桌面」。

**硬约束**：本方案的改动面**不得进入** `data/db` 与 `data/platform`（`desktop/build.gradle.kts:21,43` 两个 `srcDir` 共享源）。每批跑一次 **`:desktop:compileKotlin`** 确认没破（不是 `compileDebugKotlin`，那个任务名不存在，见 §6.1 R5）。

**回桌面时的顺序**：先做完本文的侧栏宽度 token → 再开桌面阶段 1 壳层 → 届时侧栏宽度已同源，不返工。

### 0.2 三份文档的分工（防打架）

| 文档 | 状态 | 管辖范围 |
|---|---|---|
| `docs/rounds/large-screen-adaptation-2026-09.md` | ✅ **已落地** | 第一轮 A 方案（chrome 540 收口）。是既成事实的记录，本文只引用、不重述、不改写 |
| `docs/收藏页双栏分屏方案_2026-09.md` | ⏸ 待拍板，**代码零痕迹** | 本轮**不动它**。但本文 §6.3 裁决后须**回填其 §1 表格**（见 §6.4） |
| **本文** | ⏸ 待拍板 | 第二轮：档位模型 + 契约解法 + 侧栏落地 |

**第一轮 §五 已拍板的两条，本文承接**：
1. 底栏走 A 方案（胶囊按 master 口径收口）—— 已落地，本轮不动
2. **B 方案（宽窗换左侧 NavigationRail）被否**，理由是「要打穿 `bottomBarClearance` 的 23 处契约与保护域 `Navigation.kt`，本轮不做」

⇒ **本文就是那个「不做」的下一轮**。本文要做的是：**先把契约解掉（B 方案的真正障碍），再落侧栏**，而不是硬打。

---

## §1 官方数值权威表

复核命令：
```
git show master:lib/foundation/consts.dart
git grep -n 'changePoint' master -- 'lib/**/*.dart'
```

| 值 | 常量名 | master 出处 | **精确用法（比较符）** | 本仓已有 | 本仓对应物 | 抄写裁决 |
|---|---|---|---|---|---|---|
| **600** | `changePoint` | `consts.dart:2` | **三种写法并存**（见下） | ✅ | `WideScreenPolicy.kt:17`（`> 600`） | 抄 `>`（§2.4） |
| **1300** | `changePoint2` | `consts.dart:7` | `width > changePoint2`（**全仓唯一**，`nav:254`） | ✅ | `WideScreenPolicy.kt`（`WideScreenExpandedThreshold`） | 已抄（批次 1） |
| **720** | `_kTwoPanelChangeWidth` | `favorites_page.dart:36` | **`<=` 与 `<` 混用**（见 §1.2） | ❌ | — | 抄值，**统一为 `<`**（§2.4） |
| **720** | `enableTwoViews` | `settings_page.dart:71` | `context.width > 720`，消费于 `:125,232,271` | ❌ | — | **本轮不做**（§6.2） |
| **256** | `_kLeftBarWidth` | `favorites_page.dart:34` | `AnimatedPositioned` + `Positioned` 左内缩 | ❌ | — | 第 3 批才用 |
| **72** | `_kFoldedSideBarWidth` | `navigation_bar.dart:132` | 见 §1.3 插值公式 | ✅ | `WideScreenPolicy.kt`（`FoldedSideBarWidth`） | 已抄（批次 1） |
| **224** | `_kSideBarWidth` | `navigation_bar.dart:134` | 同上 | ✅ | `WideScreenPolicy.kt`（`ExpandedSideBarWidth`） | 已抄（批次 1） |
| **540** | `_kGlassBarMaxWidth` | `navigation_bar.dart:148` | `min(540, 宽 − 24×2)` | ✅ | `WideScreenPolicy.kt:26` | 已有，不动 |
| **24** | `_kGlassBarHorizontalPadding` | `navigation_bar.dart:145` | ×2 = 48 | ✅ | `WideScreenPolicy.kt:30` | 已有，不动 |
| **400** | 抽屉定宽 | `scaffold.dart:662,727` | 定宽 | ✅ | `WideScreenPolicy.kt:45` | 已有，不动 |
| — | `LayoutBuilder` × **19** | 见下 | 取**实测**宽 | ✅ | `ComicPresentationPolicy.kt:51` | 原则已在本仓 |

### 1.1 ⚠️ 陷阱一：600dp 处 master 有三种写法，且注释与代码矛盾

`consts.dart:1-2` 原文注释：
```
/// If window width is less than this value, it is considered as mobile.
const changePoint = 600;
```
⇒ 注释语义是 **`< 600` = 手机**。

但 `navigation_bar.dart:251` 的代码是 `if (width > changePoint) { target = 2 }`，判的是 **`> 600` = 平板**。

**⇒ 恰好 600dp 时，`nav` 判平板、注释判手机。** 14 处引用的比较符分布（`git grep` 实测）：

| 比较符 | 处数 | 位置 |
|---|---|---|
| `> changePoint` | 3 | `nav:251`（形态分档）、`home_page.dart:65`（padding）、`image_favorites_page.dart:303` |
| `< changePoint` | 9 | `comic_page.dart:437,1460`、`thumbnails.dart:215,328`、`local_favorites_page.dart:284,491,601`、`network_favorites_page.dart:99,231` |
| `>= changePoint` | 1 | `nav:295`（`popGesture`，iOS 专用） |

⇒ **不能照抄 master 的矛盾**，必须裁决（§2.4）。

### 1.2 ⚠️ 陷阱二：720 自身也不自洽，且**成对出现**

`_kTwoPanelChangeWidth` 的 15 处引用里，`<=` 与 `<` 成对出现：

```dart
// favorites_page.dart:87  左栏滑出（抽屉）
left: context.width <= _kTwoPanelChangeWidth ? -_kLeftBarWidth : 0,
// favorites_page.dart:95  左栏常驻
left: context.width <= _kTwoPanelChangeWidth ? 0 : _kLeftBarWidth,
// favorites_page.dart:149 左栏显隐
child: context.width <= _kTwoPanelChangeWidth
// favorites_page.dart:158 标题是否可点
onTap: context.width < _kTwoPanelChangeWidth ? showFolders : null,
```

**⇒ 恰好 720dp 时：左栏已常驻（`:95` 取 `+256`），但标题被判为抽屉态（`:158` 的 `onTap` 为 null、点不动）。** 这是 master 的边界缺陷，**不是本仓的选择**，抄的时候必须统一（§2.4）。

同一模式在三个收藏页各重复 5–6 次：`favorites_page.dart`(4) + `local_favorites_page.dart`(2) + `network_favorites_page.dart`(6) ≈ **15 处混用**。

### 1.3 侧栏宽度的官方插值公式（抄这个，不抄结果）

`navigation_bar.dart:303,310-312`：
```dart
// 侧栏本体
left: _kFoldedSideBarWidth * ((value - 2.0).clamp(-1.0, 0.0)),
// 内容内缩
left: _kFoldedSideBarWidth * ((value - 1).clamp(0, 1)) +
     (_kSideBarWidth - _kFoldedSideBarWidth) * ((value - 2).clamp(0, 1)),
```

`value` 是连续动画值（`targetFormContext` 返回 `0`/`2`/`3`，`form 1` 是 0→2 的中间态）。稳态下：
- `value = 2` ⇒ 内缩 `72×1 + 152×0` = **72**
- `value = 3` ⇒ 内缩 `72×1 + 152×1` = **224**

⇒ **内容内缩恰好等于侧栏宽**，这是两条路线的自洽性保证。本轮取稳态值 72/224，插值见 §6 R1。

### 1.4 方法论裁决：屏幕宽 vs 实测宽

| | master | 本仓 | 裁决 |
|---|---|---|---|
| **断点判定** | `context.width` = **屏幕宽**（`lib/foundation/context.dart:52`：`MediaQuery.of(this).size.width`） | — | **照 master 用屏幕宽** |
| **内容度量**（列数） | 同上（同一个 `context.width`） | `ComicPresentationPolicy.kt:25-27` 用 `onSizeChanged` **实测宽**，注释明写「宽度传网格自身的可用宽，不传屏幕宽：分屏与自由窗口下两者不等」 | **保留本仓实测宽** |

**为什么不冲突**：两者回答不同问题。
- 「该换布局了吗」= 设备形态的粗粒度判断 ⇒ 屏幕宽正是这个语义，且照抄才可能与官方像素级一致
- 「该排几列」= 网格自身能放几张 ⇒ master 在分屏/自由窗口下**是缺陷**，本仓已经比它正确，不要退回去

⇒ **本方案每个函数都要标明属哪一类。** §2.3 的三个函数中，`wideScreenLayoutMode` 属第一类（吃屏幕宽），另两个是查表（不涉及宽度来源）。

---

## §2 断点档位模型

### 2.1 取 3 档，阈值 600 / 1300

抄 master 的「2 个阈值分 3 档」（`targetFormContext` 返回 0/2/3）。

**不取 4 档**：master 的 720 是**页面局部**阈值（只在 3 个收藏页 + 设置页出现），与其他页面无联动。抬成全局档位会造出 master 没有的第四档，且需要拍一个 master 没有的边界值。

### 2.2 命名

```kotlin
enum class WideScreenLayoutMode { Compact, Medium, Expanded }
```

| 本仓 | master `targetFormContext` | 屏幕宽 | 导航形态 | 侧栏宽 |
|---|---|---|---|---|
| `Compact` | 0 | ≤ 600 | 底部胶囊（`form 0`） | 0 |
| `Medium` | 2 | 600 < w ≤ 1300 | 收起侧栏（`form 2`） | 72 |
| `Expanded` | 3 | > 1300 | 展开侧栏（`form 3`） | 224 |

**为什么用 MD3 的名字**：① compact/medium/expanded 是行业通用语，跨人沟通成本低；② 未来若引 `material3-adaptive`，命名一致可少一层翻译；③ 不用「手机/平板/桌面」是因为 1300dp 以上的形态在折叠屏/自由窗口上并非「桌面」。

> ⚠️ **名字是 MD3 的，值不是 MD3 的。**
> MD3 官方断点是 compact <600 / medium 600–839 / **expanded ≥840**。本文取 600/1300。
> **必须在枚举的 KDoc 里点明**，否则日后有人看到 `Expanded` 就以为该按 840 判。

### 2.3 新增 API（全部纯函数、可单测）

```kotlin
// components/WideScreenPolicy.kt
enum class WideScreenLayoutMode { Compact, Medium, Expanded }

/** 断点判定 —— 吃屏幕宽（§1.4 第一类） */
fun wideScreenLayoutMode(screenWidth: Dp): WideScreenLayoutMode

/** 侧栏宽度 —— 查表抄 master _kFoldedSideBarWidth / _kSideBarWidth */
fun sideBarWidthFor(mode: WideScreenLayoutMode): Dp          // 0 / 72 / 224

/** 底部避让 —— Compact 才留底栏，故 76 / 0 / 0 */
fun bottomBarClearanceFor(mode: WideScreenLayoutMode): Dp
```

### 2.4 比较符裁决（不照抄 master 的矛盾）

| 阈值 | 裁决 | 理由 |
|---|---|---|
| **600** | **`>`（严格大于）⇒ 恰好 600 属于 `Compact`**　⚠️ **本行原文自相矛盾，落地时已裁**：原写「600 属于 Medium」，但其理由①「现状已是 `>`、`assertNull(wideScreenChromeMaxWidth(600.dp))` 不用改」只有在 600 归 Compact 时才成立；理由②「600dp 平板该拿侧栏」与 master 事实相反 —— `nav:251` 原文 `if (width > changePoint) { target = 2 }`，**恰好 600dp 时官方给的是 form 0（底部胶囊），不是侧栏**。§2.2 的档位表（`Compact = ≤ 600`）本来就对。2026-10-02 用户裁「600 归 Compact」。 | 保留 `>` 的三条真理由：① 与 master `nav:251` 逐字同形（§1.4「照 master 才可能像素级一致」的前提）；② 既有 3 个 chrome 单测零改动，且档位与 chrome 限宽在 600 处判同一侧，不留两套口径；③ `isWideScreen` 的 11 个消费点行为一字不变 |
| **720** | **`<`**（720 属于双栏） | master `:87,95,149` 用 `<=`、`:158` 用 `<`，冲突时挑 `<`，即「双栏下标题可点」，与多数派意图一致。**并标注这是 master 的边界缺陷，不是本仓的选择** |

### 2.5 `isWideScreen` 的处置：保留签名，只改函数体

```kotlin
// 现状
fun isWideScreen(screenWidth: Dp) = screenWidth > WideScreenWidthThreshold

// 改后（函数体一行，签名不变）
fun isWideScreen(screenWidth: Dp) = wideScreenLayoutMode(screenWidth) != WideScreenLayoutMode.Compact
```

**为什么保留**：
1. 已核实 **11 处**消费点（原文写 12，落地时 `grep 'isWideScreen('` 实数 11，与编译警告逐条对得上）**全部只调列数/尺寸，没有一处结构分支** ⇒ 它们只需要一个「是不是宽屏」的布尔，不需要档位
2. **11 处一行都不用改** —— 这是本轮能给出的最大零回归面
3. 改签名会同时打到 6 个文件，其中 `FavoritesScreen` / `SearchScreen` 是 FROZEN

**落地读数**：`@Deprecated(level = WARNING)` 已加。编译面**没有** `allWarningsAsErrors`/`-Werror`
（已核 `app/build.gradle.kts`/`build.gradle.kts`/`gradle.properties` 三处 0 命中）⇒ 11 个调用点只出警告不失败，
`:app:compileDebugKotlin` 通过。新增单测把 `isWideScreen(w) == (mode(w) != Compact)` 在 9 个宽度上钉住，
证明这 11 处一字未变。

**标 `@Deprecated(level = WARNING)`**，KDoc 指向 `wideScreenLayoutMode()`，但**本轮不删**。

**何时删**：出现第一个需要区分 `Medium` / `Expanded` 的消费点时，且那时再一次性改那 12 处比现在改更省。

---

## §3 `bottomBarClearance` 契约解法（核心章）

### 3.1 地基事实：master 侧栏档顶栏与底栏**都不存在**

`navigation_bar.dart:933`：
```dart
var shouldShowAppBar = state.controller.value < 2;
```
`value >= 2` 即 `Medium` / `Expanded` ⇒ `shouldShowAppBar = false`。而 `:955-968` 的 `content` 用它分别决定两件事：
- `:957` `if (shouldShowAppBar) state.buildTop()...` ⇒ **顶栏不渲染**
- `:967-968` `if (shouldShowAppBar && !isFloating) SizedBox(height: state.bottomBarHeight)` ⇒ **底栏占位不存在**
- `:972-974` `if (!shouldShowAppBar) return content;` ⇒ **提前返回，整段 overlay 逻辑（`:976` 起的底栏叠加/玻璃采样）全部跳过**

> **⇒ master 的做法不是「把 clearance 换成 0」，是整个 overlay 层消失、内容改从左侧让宽。**
> 这一条同时回答了三个问题：clearance 该传什么、顶栏要不要留、侧栏档为什么不需要底栏避让。

**推论**：`Expanded` 档下顶栏也没有。⇒ 页面顶栏（`VeneraTopAppBar`）也要跟着收起，否则会出现「没底栏却有顶栏」的畸形态。这条要写进 §3.4 的 provider 设计。

### 3.2 问题：24 处消费点全部硬绑底栏

`ui/tokens/Spacing.kt:206`：`bottomBarClearance = bottomBarHeight(64) + bottomBarBottomGap(12)` = **76dp**。
契约文本 `:184-206` 定义三方责任：系统 inset / BottomBar 自身高度+间距 / **Screen 只消费 `bottomBarClearance`，不得自行叠加 Bar 高度**。`:193` 明文禁止额外加 `Spacer(bottomBarClearance)`。

实测消费点 **24 处 / 19 文件**（文档记载 23 处/13 文件，说明后续新增页面沿用了契约但没人回填文档）：
`DownloadScreen:122`、`explore/SourceSectionScreen:252`、`explore/UnifiedExploreScreen:316`、`FavoriteImagesScreen:240,351`、`FavoritesScreen:435,813`、`HistoryScreen:139`、`LocalComicScreen:217`、`Navigation:642`、`NetworkFavoritesScreen:230,385`、`SearchScreen:285,436`、`settings/SettingsComponents:352`、`settings/SettingsHome:222`、`StatsScreen:129`、`GalleryFavoritesBody:178,235`、`GalleryForYouScreen:82`、`GalleryPostScreen:1059`、`GalleryReverseResults:132`、`GalleryScreen:859,1786`

⚠️ **`HomeScreen.kt` 不在其中** —— `:474` 只是注释（「底部留白由 Navigation 统一提供」）。所以涉及的是 **6 个 FROZEN 文件，不是 7 个**。

### 3.3 四方案对比

| | A1 显式参数 | **A2 CompositionLocal** | B 保留底栏+另给 clearance | C 页面各自分支 |
|---|---|---|---|---|
| 消费点改动 | 19 文件 / 24 处全加参数 | **0 个消费点** | 0 | 23 处加开关 |
| FROZEN 触碰 | 6 个文件 | **3 行** | 0 | 6 个文件 |
| 隐式性 | 无 | 有（读不到当前值） | 无 | 无 |
| 契约真源 | ❌ 破了（24 个各自的值来源） | ✅ **保住**（唯一注入点） | ❌ 破了（两套 clearance 并存） | ❌ 破了（24 个分支） |
| 档位判断散落 | 24 处 | **1 处** | 2 处 | 24 处 |

### 3.4 推荐 A2，三条理由

**① 可行性已验证（决定性优势）**：24 处消费点**全部在 `@Composable` 作用域内**（已抽查 `SearchScreen.kt:285,436`、`FavoritesScreen.kt:435,813` 均在 composable 体内）⇒ `CompositionLocal` 注入对消费点**零改动**。A1 要动 19 个文件 + 6 个 FROZEN，A2 一行不动。

**② provider 落点唯一，「有底栏 ⟺ 有 clearance」由构造保证**：

> ⚠️ **原文的落点是错的**：`Navigation.kt:642` 不是「Scaffold 层的 provider 落点」，它是
> `NavHost { composable<HomeRoute> { … } }` 里给 `AndroidHomeScreen` 传的**一个参数**（消费点之一）。
> 真正的公共祖先在 `VeneraComposeApp()` 最外层那个 `Box(Modifier.fillMaxSize())`（现 :579）——
> 内容层（`VeneraAmbientBackground` → `Scaffold` → `NavHost`）与底栏 overlay（`Box`，现 :1015）
> 是它的**两个子节点**，只有包在这里才能让两侧读到同一个数。

```kotlin
// feature/Navigation.kt，VeneraComposeApp() 最外层 Box 之外
val layoutMode = wideScreenLayoutMode(LocalConfiguration.current.screenWidthDp.dp)
CompositionLocalProvider(LocalBottomBarClearance provides bottomBarClearanceFor(layoutMode)) {
    Box(modifier = Modifier.fillMaxSize()) { /* 内容层 + 底栏 overlay */ }
}
```
档位判断**只在这一处发生**。契约文本明令禁止「按档位的 if」散进 24 处（那是方案 C）。
宽度源与既有 11 处 `isWideScreen` 消费点同为 `LocalConfiguration.current.screenWidthDp.dp`（已核，无第二种宽度源）。

**③ 隐式性用契约文本补**，把 `Spacing.kt:184-206` 从「常量契约」升级为「可注入契约」，写死两条纪律：
- `LocalBottomBarClearance.current` 是唯一真源
- **新页面禁止直读 `VeneraSpacing.bottomBarClearance`**（该常量降级为窄窗兜底默认值 + 单测基线）

### 3.5 A2 唯一要碰的 3 行（行级解冻，不解冻文件）

| 文件:行 | 现状 | 改法 |
|---|---|---|
| `feature/SearchScreen.kt:436` | `VeneraSpacing.bottomBarClearance + VeneraSpacing.space9` | `LocalBottomBarClearance.current + VeneraSpacing.space9`（FAB 让位） |
| `feature/FavoritesScreen.kt:435` | `val favBackToTopBottom = VeneraSpacing.bottomBarClearance + VeneraSpacing.space9` | 同上（回到顶部钮） |
| `feature/SearchScreen.kt:285` | 已有 `if (consumesBottomBarClearance)` 开关 | **保留开关**，值改从 Local 取 |

**不点名改的后果**：侧栏档底栏没了，但这 3 处仍按 76dp 让位 ⇒ 右下角 FAB / 回到顶部按钮**浮空 76dp**。而且这种 bug **只在侧栏档出现，手机档一切正常，回归测试抓不到**。

**已授权并落地**（2026-10-02 用户明确批准这 3 行行级解冻，不走「顺手改」）：三处只把**值来源**从常量换成
`LocalBottomBarClearance.current`，数值与几何语义一字未变（手机档仍是 76dp）。`SearchScreen:285` 的
`if (consumesBottomBarClearance)` 开关按本节要求**保留**。三处均在组合期内，由 `:app:compileDebugKotlin` 通过证明，不靠抽查推断。

> ⚠️ **原文「其余 21 处 A2 下一行不动」这句只在批次 1（值不变）成立，批次 2 不成立。**
> 事实清点：真消费点 24 处，本批动了 4 处（上表 3 行 + `Navigation.kt:642` provider 自身），**剩 20 处仍直读常量**。
> 一旦 `bottomBarClearanceFor()` 的 Medium/Expanded 归 0，这 20 处会继续按 76dp 让位 ⇒ 侧栏档每页底部
> **多 76dp 空白** —— 不显眼，但正是本文 §4.2 声称批次 1 能消掉的那个「莫名留白」，方向恰好相反。
> ⇒ **批次 2 的前置**：把剩余 20 处一次性迁到 `LocalBottomBarClearance.current`，与「值归 0」和
> 「侧栏档不渲染底栏」落在同一颗提交里。这 20 处含 `UnifiedExploreScreen:316`、`SourceSectionScreen:252`、
> `NetworkFavoritesScreen:230,385`、`HistoryScreen:139`、`FavoritesScreen:813` 等，需另行申请行级解冻。

**解冻纪律**：`FREEZE-STATEMENT.md:17-20` 的「允许：修实际 Bug、修明确回归」**不覆盖**本次改动（这是契约重构，不是 bug 修复）⇒ 必须走**明确的行级解冻评审**，不能靠「顺手改」蒙过去。

### 3.6 `Spacing.kt:184-206` 契约文本改写要求

- **保留**：三方责任划分；`:193` 禁止额外 `Spacer(bottomBarClearance)`；`:192` clearance 不含系统 inset
- **改**：`bottomBarClearance` KDoc 点明它**不再是真源**，只是窄窗默认档值；新增 `LocalBottomBarClearance` 声明与 provider；`:190` 第 3 条责任改为「只消费 `LocalBottomBarClearance.current`」
- **扩写 `Navigation.kt:1050-1057` 的「几何零差异」不变量**：现在是「两条底栏路径几何零差异」，扩档后要变成：

> **给定档位下，底栏可见 ⟺ `LocalBottomBarClearance.current == 76.dp`；底栏不可见 ⟺ `== 0.dp`。**

这条是 A2 的正确性判据，单测要能钉住。

- **禁止**：不许在契约里写「宽窗时 clearance 传 0」这种按档位的 if

---

## §4 分批实施（3 批）

### 4.1 为什么官方把它排批次 D 而我们先做

`official-gap-analysis.md:64` 把「收藏页双栏侧栏」排在**批次 D（低频）**。

**排序前提已经不成立**：官方口径里它是低频，是因为在「只有手机 + 平板」的产品形态下它少人用。而本项目**同时有桌面化在等** —— `VeneraDesktop.kt:242` 已在用 `NavigationView`（侧栏）、`:283` 已在用 `GridCells.Adaptive`。

更关键：按 P5「允许分叉」先做桌面版，会产出第二套布局系统 —— 1300dp 档的侧栏是 master 公式的一端（§1.3），桌面版必然要实现同一套。**先做大屏适配，桌面版的地基就顺带完成；顺序反过来，桌面版会把这套公式重写一遍。**

### 4.2 分批表

| 批 | 内容 | 独立价值（真机可见） | 判据 | 碰 FROZEN | 改动面 |
|---|---|---|---|---|---|
| **1** | **档位模型 + clearance 注入（A2 全部，但**值不动**）**：3 个纯函数 + `isWideScreen` 改函数体 + `LocalBottomBarClearance` + provider + 契约文本改写 + 4 处迁 Local | **真机零变化**（全档 76dp，含手机档）。原文此处写「1300dp 底部空白从 76dp 收到 0」**是错的**：实测底栏今天仍**无条件渲染**（`Navigation.kt` 两条路径的门都只有 `currentTab != null`，不判宽度），那 76dp 正是底栏占位而非「莫名留白」；先归 0 会让平板档最后一行内容被悬浮底栏压住 —— 与 §3.5 警告的是同一类「只在侧栏档出现、回归测试抓不到」的 bug。**2026-10-02 裁：批次 1 只注入不改值** | 全单测 + 手机档回归 | **3 行解冻** | `WideScreenPolicy.kt` + `Spacing.kt` + `Navigation.kt` + 3 处 + 测试 |
| **2** | **侧栏落地**：3 形态 + 顶栏按 `mode < Medium` 收起（§3.1 推论） | **平板档出现 72dp 侧栏；宽窗 224dp 展开侧栏** | 侧栏宽度/内容内缩/折叠态几何**必须真机**（`clamp` 插值 + 600dp 突变窗口无法单测） | 否 | `Navigation.kt` + 新侧栏组件 + 2 个底栏 |
| **3** | 收藏页双栏（**条件性**，待拍板） | 平板收藏页左 256dp 常驻 + 抽屉 | 内缩几何单测 + 真机 | 需解冻 `FavoritesScreen` | 见 §6.4 |

**批次 1 的独立价值要诚实说**：它本身**没有**真机可见的布局变化。价值是「把断点判定从散落收口成可单测的纯函数」+「把 24 处契约从硬绑变成可注入」。不为了凑「每批都有价值」而虚报。

### 4.3 单测分两条通道

| 通道 | 能跑什么 | 登记在哪 |
|---|---|---|
| **`gradle :app:testDebugUnitTest`** | 一切依赖 `androidx.compose.ui.unit.Dp` 的纯函数 —— 即 `WideScreenPolicy.kt` 全部 | 扩展现有 `app/src/test/java/com/venera/compose/components/WideScreenPolicyTest.kt` |
| **`_probe/l0/run-judgment-tests.sh`** | **只能跑不依赖 Compose 的纯 Kotlin** | **进不去，不必改** |

⚠️ **为什么进不去（已核实）**：该脚本 classpath 只有 `kotlin-stdlib` / `kotlin-reflect` / `junit` / `hamcrest-core` / `kotlinx-coroutines-core` / `kotlinx-serialization-core` / `kotlinx-serialization-json` 七类 jar，而 `WideScreenPolicy.kt` 依赖 `androidx.compose.ui.unit.Dp` ⇒ **编译不过**。别浪费时间登记。

### 4.4 必写用例

**批次 1 已落地的四条**（`WideScreenPolicyTest`，实测 7 用例 0 失败 = 原有 3 + 新增 4）：

```
// 断点边界：两条阈值都卡两侧，把 §2.4 的严格 `>` 钉死
assertEquals(Compact,  wideScreenLayoutMode(599.dp))
assertEquals(Compact,  wideScreenLayoutMode(600.dp))    // ← 600 归 Compact（原文写 Medium，已裁正）
assertEquals(Medium,   wideScreenLayoutMode(601.dp))    // ← 进 Medium 的第一格
assertEquals(Medium,   wideScreenLayoutMode(1299.dp))
assertEquals(Medium,   wideScreenLayoutMode(1300.dp))   // ← 1300 归 Medium
assertEquals(Expanded, wideScreenLayoutMode(1301.dp))

// 侧栏宽度抄 master
assertEquals(0.dp,   sideBarWidthFor(Compact))
assertEquals(72.dp,  sideBarWidthFor(Medium))    // == master _kFoldedSideBarWidth
assertEquals(224.dp, sideBarWidthFor(Expanded))  // == master _kSideBarWidth

// 批次 1 的中间态：三档同值 76。用例名自带理由，谁先改值就绊红谁
for (mode in WideScreenLayoutMode.entries) assertEquals(76.dp, bottomBarClearanceFor(mode))

// isWideScreen 改走档位后，11 个消费点必须一字不变（9 个宽度逐个对齐）
assertEquals(mode(w) != Compact, isWideScreen(w))
```

**以下两条属批次 2，批次 1 提前写必红，故不写**（原文把它们列在本批是错的）：

```
// (a) clearance 终值
assertEquals(0.dp, bottomBarClearanceFor(Medium)); assertEquals(0.dp, bottomBarClearanceFor(Expanded))
// (b) 黄金不变量（§3.6 扩写那条）
for (mode in entries) assertEquals(mode == Compact, bottomBarClearanceFor(mode) > 0.dp)
```

(a)(b) 与「侧栏档不渲染底栏」+「剩余 20 处迁 Local」三者必须同一批落，理由见 §3.5 的批次 2 前置。

**保留现有 3 个测试不动**（`phoneBandNeverGetsAWidthCap` / `wideBandIsCappedByMasterGlassBarContract` / `windowTermCannotBeatTheCapAboveTheThreshold`）—— 它们锁的是 chrome 限宽，与档位模型**正交**，扩档不能动它们。**实测确认三条仍绿**。

---

## §5 侧栏交互态最小约定

**明确说明**：这是本轮的**副产品**，不是独立议题。

1. **选中态复用既有 token，不新造** —— 遵循「现成口径优先」的既有纪律。
2. **不做 hover 态** —— 桌面化已暂停，鼠标是未来输入，现在做等于为一个没有消费者的输入设备写交互。（已核实全仓 `hoverable` / `onHoverEvent` / `onFocusEvent` **0 命中**。）
3. **不做折叠动画，先跳变后动画** —— master 是 `clamp` 连续插值（§1.3），我们是三态离散。`animateDpAsState` 造假 0→72 会与真机不符；而且**动画会掩盖问题**，跳变在真机上一眼能看出要不要修。

### §5.1 不在本轮（防范围蔓延）

三轴 8 种观感（`AppearanceStyle × NavigationBarStyle × SurfaceMaterial`）、圆角 180 处统一 —— 都是**独立议题**，撞上侧栏时按「复用现成 token、不顺手统一」处理。

---

## §6 风险与未定项

### 6.1 风险表

| # | 风险 | 根因（带出处） | 本轮对策 | 判据归属 |
|---|---|---|---|---|
| **R1** | **600dp 跨越时内容底部突变 76dp + 左侧突变 72dp** | master 是 `clamp` 连续插值（`nav:303,310-312`），我们三态离散 | 接受跳变，真机验；**不引入假动画** | **必须真机** |
| **R2** | ~~`BoxWithConstraints` + `requiredWidth(540)` 居中会随侧栏折叠跳变~~ | **本条风险描述需修正**：`VeneraLiquidGlassNavBar.kt:131` 的 cap 取自 `screenWidthDp`（**屏幕宽**，非 constraints）；`:135` 的 `BoxWithConstraints` 只用于 `:139` 算 tabWidth；`requiredWidth(540)` 走 fixed 约束**不随父宽变化**。且按 §3.1 **侧栏档底栏整体不渲染**，此路径不进入 | 无需对策 | — |
| **R3** | 自由窗口下屏幕宽 ≠ 应用宽 | 15 处 `screenWidthDp`（含 `WideScreenPolicy.kt:37` 的 chrome 收口） | 断点用屏幕宽（已裁决）、列数用实测宽（已正确） | 已由现有列数单测部分覆盖 |
| **R4** | 侧栏与共享元素飞行两端度量 | `Navigation.kt` 保护域；`FREEZE-STATEMENT.md:32` 另禁 Tab 枚举/路由/齿轮入口变更 | 批次 2 只动「是否渲染底栏/顶栏」这一处，**不碰 Tab 枚举与路由** | 真机 |
| **R5** | `desktop/` 编译面破裂 | `desktop/build.gradle.kts:21,43` 共享 `data/platform` + `data/db` 两个 `srcDir` | **硬约束**：改动面不得进入这两个目录 | 每批跑 **`:desktop:compileKotlin`**（⚠️ 原文写的 `:desktop:compileDebugKotlin` **任务名不存在**，gradle 直接报 `Cannot locate tasks`；桌面是 Compose Multiplatform，`dev` 档叫 `compileDevKotlin` 且实测 **NO-SOURCE**，真正吃共享源的是 `compileKotlin`）。批次 1 读数：`compileKotlin` **UP-TO-DATE** —— 这本身就是决定性证据：改动的 `components/`、`ui/tokens/`、`feature/` 三个文件不在桌面模块的输入集里，否则该任务不可能保持新鲜 |
| **R6** | 侧栏档下**顶栏也不存在**（§3.1 推论） | `nav:933,957` | 批次 2 一并落：顶栏按 `mode < Medium` 收起 | 真机 |

### 6.2 明确「不做」（各给依据）

- **折叠屏**：全仓 `hinge` / `foldable` / `FoldingFeature` / `WindowLayoutInfo` **0 命中**，master 也没有。**不做**。但文档要留一句：折叠屏展开后屏幕宽跨过 600 → 自动进 `Medium` 拿侧栏，这个行为**恰好是对的**，无需专门适配。
- **阅读器宽屏**：已查证 `lib/pages/reader/` 对 `changePoint` 的引用数**全为 0** —— 负结果确认。第一轮 §七 已拍板（顶栏/控制岛沿用 540 收口、抽屉 400 居中）。**确认不做**。但要加一条**新风险**：侧栏档下阅读器是否也内缩？master 未定义（阅读器不走 `buildMainView` 的 `left`）→ 标注**未查证**，批次 2 开工前必须补查。
- **设置页 U-13**（`settings_page.dart:71` `> 720` 双栏，消费于 `:125,232,271`）：**不做**。理由：master 的 720 是**页面局部**阈值，与收藏页 720 无联动；一次做两处页面局部分栏会同时碰 2 个 FROZEN。列批次 3 候选。

### 6.3 ⚠️ 裁决：MD3 600/840 vs master 600/720

`docs/收藏页双栏分屏方案_2026-09.md:§1` 用 MD3 官方 600/840 做**三栏**；master 实际是 600/720 做**两栏**。

**裁决：断点值一律以 master 为准，不引 840，三栏不做。**

1. **「第一方」的定义**：`official-gap-analysis-round2.md` 全部 U-xx 条目的 O 列都是 **master 行号** ⇒ 本项目要对标的第一方是 Flutter master，不是 Google MD3。MD3 是 Compose 的通用规范，master 是要 1:1 复刻的**具体对象**。冲突时 master 优先。
2. **840 三栏是自造**：master 收藏页只有两根柱（`_kLeftBarWidth = 256` + 内容），三栏要新增第三根（文件夹树 + 列表 + 详情），master 任何页面都没有这个形态。
3. **组件可借、数值不可借**：若批次 3 引 `ListDetailPaneScaffold`，**必须**显式传 `ListDetailPaneScaffoldDirective` 覆盖断点，**禁止吃库默认 840**。
4. **依赖现状更正**：`material3-adaptive` / `material3-window-size-class` **当前均未出现在 `gradle/libs.versions.toml` 与 `app/build.gradle.kts`**（已核实 0 命中）⇒ 双栏方案 §0 的「已核实可用」指的是**可引入**，不是**已引入**，该措辞要修正。

### 6.4 与收藏页双栏文档的衔接（回填待办）

本文 §6.3 裁决后，`docs/收藏页双栏分屏方案_2026-09.md` §1 的三档表格须回填：
- medium/expanded 的 840 → 改用 720 的两档制
- 三栏 → 降级为两栏 + 左栏（256dp）
- 若引 `ListDetailPaneScaffold` 必须覆盖断点

**用户 2026-09-23 已定「分栏先不要改」** ⇒ 本轮保持暂缓，批次 3 为**条件性**。

---

## §7 拍板项清单

> **2026-10-02 用户裁决：13 项全部照推荐批准**，并**显式授权 §3.5 那 3 行 FROZEN 行级解冻**。
> 落地时当场追加两条裁决，见 §8.2（批次 1 只注入不改值 / 恰好 600dp 归 Compact）。

| # | 拍板项 | 推荐 | 备选及其代价 |
|---|---|---|---|
| **1** | 档位数与阈值 | **3 档 600 / 1300**（抄 master） | 4 档要造 master 没有的第四档边界 |
| **2** | 命名 | **`Compact/Medium/Expanded`** | 叫「手机/平板/桌面」在自由窗口/折叠屏上语义错 |
| **3** | 600dp 归哪档 | **`>`（600 = Medium）** | 取 `<` 则平板拿不到侧栏；且要改现有单测 |
| **4** | `isWideScreen` | **保留签名只改函数体**（12 处零改动） | 改签名要动 6 文件含 2 个 FROZEN |
| **5** | clearance 解法 | **A2 CompositionLocal** | A1 动 19 文件 + 6 FROZEN；C 动 23 处；B 破契约真源 |
| **6** | 3 行解冻 | **申请行级解冻，不靠「顺手改」** | 不申请就只能走 A1 动 19 文件 |
| **7** | 侧栏宽度过渡动画 | **先跳变，后动画** | 假动画与真机不符且会掩盖问题 |
| **8** | 720 断点与三栏 | **照 master 720 两栏；三栏不做** | 三栏是自造，master 无此形态 |
| **9** | `desktop/` `engine-probe/` | **冻结不扩建** | 废弃要动 settings + 29 个 kt + build 脚本 |
| **10** | 收藏页双栏 | **维持暂缓**（批次 3 条件性） | 现在做要解冻 `FavoritesScreen` |
| **11** | 设置页 720 双栏 | **本轮不做** | 同时碰 2 个 FROZEN |
| **12** | 折叠屏 | **不做**（跨 600 自动进 Medium 即正确） | 全仓 0 命中 + master 也无 |
| **13** | 批次 2 是否含顶栏收起 | **含**（§3.1 推论：master 侧栏档顶栏也不渲染） | 不含会做出「没底栏却有顶栏」的畸形态 |

---

## §8 批次 1 落地记录（2026-10-02）

### 8.1 改动清单

| 文件 | 改了什么 |
|---|---|
| `components/WideScreenPolicy.kt` | 新增 `enum WideScreenLayoutMode`（KDoc 按 §2.2 明写「名字是 MD3 的、阈值 600/1300 是 master 的，看到 `Expanded` 不要按 840 判」）；新增 `wideScreenLayoutMode()` / `sideBarWidthFor()` / `bottomBarClearanceFor()` 三个纯函数；新增私常量 1300 / 72 / 224（各带 master 出处）；`isWideScreen()` **保签名只改函数体** + `@Deprecated(WARNING)` + `ReplaceWith` |
| `ui/tokens/Spacing.kt` | 新增 `LocalBottomBarClearance`（`compositionLocalOf`，默认值取窄窗档那一档，KDoc 写了为什么不用 static）；契约块 185-207 从「常量契约」升级为「可注入契约」：三方责任第 3 条改为只消费 `Local`，常量 KDoc 明写「**这个常量不是真源**」；`:193` 禁止额外 Spacer、`:192` 不含系统 inset 两条**原样保留** |
| `feature/Navigation.kt` | provider 包在 `VeneraComposeApp()` 最外层 `Box`（现 :579）之外 —— 内容层与底栏 overlay 同为其子节点；原 :642 那处迁读 `Local` |
| `feature/SearchScreen.kt` ×2、`feature/FavoritesScreen.kt` ×1 | **已授权的 3 行行级解冻**，只换值来源 |
| `app/src/test/.../WideScreenPolicyTest.kt` | 新增 4 条用例（见 §4.4），原有 3 条一字未动 |

76 这个数没有新造字面量：`bottomBarClearanceFor()` 读 `VeneraSpacing.bottomBarClearance`，与 `Spacing.kt` 的 `64+12` 同源。

### 8.2 落地时追加的两条裁决

1. **批次 1 只注入、不改值**（三档恒 76dp）。原计划让批次 1 就把 Medium/Expanded 归 0，但实测底栏今天仍无条件渲染 ⇒ 中间态会让平板档最后一行内容被悬浮底栏压住。归 0 挪进批次 2，与「侧栏档不渲染底栏」同一颗提交。
2. **恰好 600dp 归 `Compact`**（严格 `>`）。裁掉了本文 §2.2 / §2.4 / §4.4 三处互相打脸的写法，依据是 master `nav:251` 的 `if (width > changePoint)`。

### 8.3 验证读数（全部实跑取退出码，非推断）

| 命令 | 结果 | 这条读数证明了什么 |
|---|---|---|
| `:app:compileDebugKotlin` | **通过** | A2 的地基。4 处新读的 `LocalBottomBarClearance.current` 全在组合期内 —— 这是**编译器给的判定**，不是本文原来的抽查；`SearchScreen.kt:257` 那句「LazyColumn 的 content lambda 不是 @Composable」的旧坑没有被踩（两处读点都在 `LazyColumn(` 的参数位，不在 content lambda 里） |
| 同上 · 警告面 | 11 条 `isWideScreen is deprecated` | 与 `grep` 实数的 11 个调用点逐条对齐；三处 `build.gradle.kts` / `gradle.properties` 无 `allWarningsAsErrors` ⇒ 降级为 WARNING 不会变编译失败 |
| `:app:testDebugUnitTest` | **BUILD SUCCESSFUL**，任务态 `executed`（非 UP-TO-DATE） | 全模块 767 用例通过；`WideScreenPolicyTest` `tests="7" failures="0" errors="0"` = 原有 3 + 新增 4 |
| `:desktop:compileKotlin` | **UP-TO-DATE** | R5 没破。UP-TO-DATE 本身就是证据：改动的三个文件不在桌面模块输入集里，否则该任务必然变陈旧 |
| `_probe/l0/run-judgment-tests.sh` | **未改** | §4.3 已核实它的 classpath 无 `compose.ui`，档位用例依赖 `Dp` 进不去，登记了也不会跑 |

### 8.4 批次 2 的落地记录（2026-10-02 追加，同日完成）

**改动清单**

| 文件 | 改了什么 |
|---|---|
| `components/VeneraSideBar.kt`（**新建**） | 侧栏组件。`Row` 两段：侧栏本体（`surfaceContainerHigh` 底 + `systemBarsPadding`）+ 内容层。宽度取 `sideBarWidthFor(mode)`（72/224），`Expanded` 额外出文字标签。选中态 = `primary` 染图标 + `selectedSurfaceAlpha` 圆底；`Compact` 档内部直通 content（可被无条件调用） |
| `components/WideScreenPolicy.kt` | `bottomBarClearanceFor` 三档改真值 `76 / 0 / 0`，KDoc 写明「归 0 的前提是底栏真的不渲染，两者必须同批」与回退要求 |
| `feature/Navigation.kt` | 新增 `layoutMode` / `useSideBar` 两个局部量；`VeneraSideBar` 包住内容层（`SharedTransitionLayout`），底栏 overlay 留在其外；底栏两条路径的门各加 `&& !useSideBar` |
| 13 个消费点文件 / 18 处 | `VeneraSpacing.bottomBarClearance` 与 `tokens.spacing.bottomBarClearance` → `LocalBottomBarClearance.current`（含 4 个 FROZEN：`NetworkFavoritesScreen`×2、`FavoritesScreen:815`、`HistoryScreen`） |
| `WideScreenPolicyTest.kt` | 3 → 9 条：新增 `bottomBarClearanceIsZeroOnceSideBarReplacesBottomBar`、`bottomBarVisibleIffClearanceIsPositive`（黄金不变量）、`sideBarWidthIsZeroOnCompactSoItNeverStacksWithBottomBar` |

**迁移后的全仓读数**：`VeneraSpacing.bottomBarClearance` / `tokens.spacing.bottomBarClearance` 仅剩 2 处——`WideScreenPolicy.kt:87`（实现本身）与 `Spacing.kt:539`（`Local` 的默认值），**消费点零直读**。

**验证读数（实跑取退出码）**

| 命令 | 结果 |
|---|---|
| `:app:compileDebugKotlin` | **BUILD SUCCESSFUL**（首轮 2 个 `e:` 是猜错 token 字段名——`tokens.alpha.*` 不存在、`type.caption` 本身就是 `TextUnit` 不是 `.fontSize`；已按编译器读数改正） |
| `:app:testDebugUnitTest` | **BUILD SUCCESSFUL**，`WideScreenPolicyTest tests="9" failures="0"`（原有 3 + 批次 1 的 4 + 本批 3… 实为原有 3 + 6） |
| `:desktop:compileKotlin` | **UP-TO-DATE** ⇒ R5 硬约束没破 |
| `:app:assembleDebug` | **BUILD SUCCESSFUL** |

⚠️ **`:desktop` 的任务名是 `compileKotlin` 不是 `compileDebugKotlin`**（它是 `kotlin-jvm` 模块，只有 `compileDevKotlin` / `compileKotlin`）。写「每批跑 `:desktop:compileDebugKotlin`」会得到 `No matches` 的假失败——方案 §0.3 那条硬约束的命令要按此更正。

**批次 3（收藏页双栏）未做**：维持暂缓（用户 2026-09-23 已定「分栏先不要改」），且它需解冻 `FavoritesScreen` 整文件而非行。

### 8.5 批次 2 遗留（真机验，agent 到编译为止）

1. **侧栏 72/224 几何**：内容内缩量 == 侧栏宽这条不变量单测钉不了（`Row` 第二子节点自然占剩余宽），必须真机看 1280dp 与 700dp 两档。
2. **600dp 跨越的突变窗口**：R1 说的「底部 76→0 + 左侧 0→72 同时跳变」，单测无法覆盖。
3. **顶栏未收**（拍板项13）：本仓顶栏是页内自治（各页自绘 `VeneraTopAppBar`），外壳管不到，所以本批只关了底栏。侧栏档下顶栏仍在，与 master `nav:957` 不同 —— **这是有意留的欠账**，不是漏做。
4. **阅读器侧栏档内缩**（原「未查证」项，本轮已查证）：`nav:963` 的内容层走 `buildMainViewContent()`，它被 `nav:310-312` 的 `Positioned.fill(left: 内缩)` 包住 ⇒ **阅读器确实跟着内缩**，master 无例外。原 §6.2 那条「未查证」到此关闭。


### 8.6 批次 2 并行线对账（2026-10-02，主体由并行线写、本线收下并补齐）

**并发现场，必须记账**（否则下一轮读不到真相）：本线提完批次 1（`8dfd9ff`）后才发现，批次 2 的主体是**另一条线在同一工作树里并行写的** —— `VeneraSideBar.kt`（新组件）、`Navigation.kt` 的侧栏接入与底栏 `!useSideBar` 门、`bottomBarClearanceFor` 归 0、对应单测、22 处消费点迁 Local。

> ⚠️ **本线造成的事故**：批次 1 用 `git add <整文件>` 提交，把并行线当时已落在盘上的 `Navigation.kt`（调用 `VeneraSideBar`）与 `WideScreenPolicy.kt`（已归 0）连同改过的单测一起带进了 `8dfd9ff`，而 `VeneraSideBar.kt` 那时还是**未跟踪新文件** ⇒ **`8dfd9ff` 这个提交自身编不过**（悬空引用）。修复 = 批次 2 提交把组件与其余迁移一起收进库。
> **教训（写进纪律）**：并行工作树里 `git add` 整文件前必须逐文件 `git diff` 过一遍；新组件与它的调用方**必须同一颗提交**，否则提交面是坏的。

**本线补的部分**：
- **2 处 `explore/` 消费点**（`SourceSectionScreen`、`UnifiedExploreScreen`）。它们写作 `VeneraTokens.spacing.bottomBarClearance` —— 经 token 对象转发读同一个数，所以**之前所有以 `VeneraSpacing.bottomBarClearance` 为字面的清点都数不到它们**（含本文 §3.2 的「24 处 / 19 文件」那张表，它按字面列的 24 处里根本没有这两行）。不补的后果：侧栏档这两个页面底部各留 76dp 永远填不满的空。
- ⇒ **契约现已彻底收口**：全仓只剩 2 处仍读该常量 —— `WideScreenPolicy.kt:89`（76 的真身）与 `Spacing.kt:539`（Local 的默认值）。页面侧零直读。
- **阅读器那条 §8.5-4 的对账补一句本仓侧的确定事实**（那条讲的是 master，本仓这侧无需推断）：`composable<ReaderRoute>`（`Navigation.kt:986`）→ `VeneraReaderScreen`（`:1003`）在 **NavHost 内**，`AndroidManifest.xml` 的 Activity 只有 Main / Settings / SettingsSub / GalleryPost / GalleryArtistProfile，**没有 ReaderActivity** ⇒ `VeneraSideBar` 包的是 `SharedTransitionLayout`，所以**侧栏档下阅读器会一并拿到侧栏并左内缩**。这与 §6.2 原文「阅读器不走 buildMainView 的 left」相反，以本仓事实为准。**观感是否可接受（阅读器要不要保持沉浸全屏）留给真机裁**，本批不改。

**拍板项 13（侧栏档一并收起顶栏）—— 本批未做，且建议不做**（推翻已批准项，理由与代价都要写清）：
- master 敢收顶栏，是因为它把动作项搬进了侧栏：`nav:620-627` 在 `buildLeft()` 底部渲染 `paneActions`；而 `nav:957` 关掉的那条"顶栏"，全文就是 `Text(页名) + Spacer + paneActions 图标`（`buildTop()`，`nav:390-419`）—— 一条壳层细条。
- 本仓的顶栏**不是那条细条**：2026-09-18 起顶栏页内自治（`Navigation.kt:612-615`），且外壳本就**没有页无关的 `paneActions`**（设置齿轮同日迁入首页顶栏，见 `FREEZE-STATEMENT.md` 第二批）。照收 = 设置入口、搜索入口、页内工具行、返回钮在平板档无家可归 ⇒ 直接撞「重功能不丢」。
- **代价如实说**：平板档会留着一条 master 没有的顶部 chrome，属观感差异不是缺陷。日后若要真 1:1，前置是先给 rail 加动作区并把各页顶栏的动作项收进去 —— 那是独立一轮，不该塞进批次 2。

**与 master 的几何差项 —— 留给真机裁，不在盘面上抢改**：本仓条目是「图标在上、标题在下」竖排 + 整组垂直居中；master 是 `Row[icon, 12, label]` 横排、顶部起排、`Spacer()` 把动作压到底（`nav:603-629`）。分隔线也不同：master 只有右侧 1dp `outlineVariant`（`nav:596-600`），本仓给整条 rail 涂了 `surfaceContainerHigh` 底衬。尺寸口径也不照 `nav:663,674` 的「高 38 + 竖直 4 + 圆角 12」而用 space 系 token。
> 不动它们的理由就是本文 §4.2 自己写的「侧栏宽度/内容内缩/折叠态几何**必须真机**」—— 这些正该由真机读数裁决，没读数之前重写一遍等于再来一次无据改动。
> 另一条一并留给真机：rail 没有 `if (tab != currentTab)` 门，重复点当前 Tab 会多震一次（`gotoTab` 是 `launchSingleTop`，不堆栈，只是反馈多余）。

**批次 2 验证读数**（本线实跑，非 UP-TO-DATE 蒙混）：`:app:compileDebugKotlin` **executed 通过**（含并行线的 `VeneraSideBar` 与本线 2 处 explore ⇒ 24 处 `Local.current` 读取全部由编译器判定在组合期内）；`:app:testDebugUnitTest` **executed 通过**，`WideScreenPolicyTest tests="9" failures="0" errors="0"`（原 3 条 chrome 用例仍未动）；`:desktop:compileKotlin` UP-TO-DATE（R5 未破）。
**仍未验的**：侧栏 72/224 的实际观感、600dp 跨越时的 76→0 + 左内缩 0→72 双向突变（R1）、平板档顶栏保留后的整体比例 —— 全部需要真机，见 §8.4 第 4 条；阅读器在侧栏档是否内缩**仍未查证**（§6.2 那条未闭合）。

---

## §附 本轮复核修正记录

调研过程中纠正的几处，**写在这里是为了不让下一轮再踩**：

| 交办说法 | 复核结果 |
|---|---|
| master `LayoutBuilder` 21 处 | **19 处** |
| 7 个 FROZEN 都在契约消费点里 | **6 个**（`HomeScreen.kt:474` 只是注释） |
| R2「`BoxWithConstraints` 会随侧栏折叠跳变」 | **风险描述需修正**，且侧栏档底栏整体不渲染 ⇒ 该路径不进入 |
| 收藏页 720 与 MD3 840 矛盾 | **更深**：master 720 自身也不自洽，`<=`/`<` 成对出现，**15 处混用** |
| master 600dp「注释与代码相反」 | **更准确**：注释说 `<600=mobile`，代码 `nav:251` 判 `>600=平板`；14 处引用中 `>` 3 处 / `<` 9 处 / `>=` 1 处 |
| `getSharedPreferences` 19 命中/14 文件 | **全仓仅 1 处**，早已收口到 `KeyValueStore`（阶段 1 的脱 Android 工作已完成） |
| material3 被 miuix 钉死 `1.5.0-alpha22` | 那个坑**只在 `:app`**；桌面走 `org.jetbrains.compose.material3` 不同坐标 |
| 「`Navigation.kt:642` 是 Scaffold 层的 provider 落点」 | **错**。它是 `composable<HomeRoute>` 里给 `AndroidHomeScreen` 传的**参数**（消费点）。真正的公共祖先在 `VeneraComposeApp()` 最外层 `Box`，底栏 overlay 与内容层同为它的子节点 —— 见 §8.1 |
| 「批次 1 可独立落地、1300dp 底部留白当场归 0」 | **会造中间态 bug**。底栏今天无条件渲染（`Navigation.kt` 两条路径的门只有 `currentTab != null`），归 0 = 内容钻到栏底下。已裁：批次 1 只注入不改值 —— 见 §8.2 |
| 「其余 21 处 A2 下一行不动」 | 只在**批次 1（值不变）**成立。值一旦归 0，剩这 20 处仍按 76dp 让位 ⇒ 侧栏档每页底部多 76dp 空白。批次 2 必须一并迁 —— 见 §3.5、§8.4 |
| 「`isWideScreen` 12 处消费点」 | 实数 **11 处**，且全部同读 `LocalConfiguration.current.screenWidthDp.dp`，无第二种宽度源 |
| 「每批跑 `:desktop:compileDebugKotlin`」 | **任务名不存在**（gradle 报 `Cannot locate tasks`）。桌面是 Compose Multiplatform：`compileDevKotlin` 实测 NO-SOURCE，吃共享源的是 `:desktop:compileKotlin` |
| §4.4 把「clearance 三档 76/0/0」与黄金不变量列为本批必写 | 本批写了**必红**（三档同值 76 才是当前事实）。已挪进批次 2 清单 —— 见 §8.4 |
| 「消费点 24 处 / 19 文件」（§3.2 那张按字面列的表） | 表本身数对了**行数**，但数漏了**写法**：`SourceSectionScreen`、`UnifiedExploreScreen` 两处经 `VeneraTokens.spacing.bottomBarClearance` 转发读同一个数，字面 `VeneraSpacing.bottomBarClearance` 的 grep 抓不到 ⇒ 真总数 24 + 2。批次 2 已补齐 —— 见 §8.5 |
| 拍板项 13「批次 2 含顶栏收起」 | **前提不成立**：master `nav:957` 关的是一条壳层细条 `Text(页名)+Spacer+paneActions`（`nav:390-419`），且它把 `paneActions` 搬进了 rail 底部（`nav:620-627`）。本仓顶栏是页内自治的 chrome 宿主、外壳没有 `paneActions`（齿轮 09-18 已迁首页顶栏）⇒ 照收 = 丢入口。**已推翻，批次 2 不收顶栏** —— 见 §8.5 |

---

## §附 相关文件索引

| 用途 | 路径 |
|---|---|
| 唯一宽度阈值 + 4 消费点 | `app/src/main/java/com/venera/compose/components/WideScreenPolicy.kt` |
| 唯一列数口径 | `app/src/main/java/com/venera/compose/components/ComicPresentationPolicy.kt` |
| 契约文本（✅ 批次 1 已改写为可注入契约） | `app/src/main/java/com/venera/compose/ui/tokens/Spacing.kt:185-207` + 同文件 `LocalBottomBarClearance` |
| 唯一 provider 落点（✅ 批次 1 已落） | `app/src/main/java/com/venera/compose/feature/Navigation.kt` — `VeneraComposeApp()` 最外层 `Box`（现 :579）之外；原 :642 那处只是消费点 |
| 行级解冻 3 处（✅ 已授权并落地） | `feature/SearchScreen.kt`（FAB 让位 / contentPadding 两处）、`feature/FavoritesScreen.kt`（顶置钮） |
| 单测（✅ 已扩到 7 条，原有 3 条未动） | `app/src/test/java/com/venera/compose/components/WideScreenPolicyTest.kt` |
| 保护域声明 | `FREEZE-STATEMENT.md:8-15`（8 个 FROZEN） |
| 桌面模块 | `desktop/build.gradle.kts`（两个 `srcDir` 共享源）、`settings.gradle.kts:27-31` |
| 路线决策文档 | `docs/rounds/windows-port-feasibility-2026-10.md`（阶段表 §七、P5 分叉拍板） |
| S0 探针读数 | `docs/rounds/windows-r1f-s0-spike-2026-10-02.md` |
| 第一轮大屏方案（已落地） | `docs/rounds/large-screen-adaptation-2026-09.md` |
| 收藏页双栏（待拍板） | `docs/收藏页双栏分屏方案_2026-09.md` |