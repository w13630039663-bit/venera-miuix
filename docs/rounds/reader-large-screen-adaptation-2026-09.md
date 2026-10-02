# 阅读器（播放器）宽屏适配 —— 方案

用户原话（2026-09-22，附 Pixel Tablet 1280dp 横屏阅读器截图）：

> 播放器的ui也做下宽屏适配，另外返回的时候不知道为啥直接跳过了详情页回到了主界面
> 一定要记得也一样要做手机端的适配的

截图病状：阅读页居中、左右一大片黑（这是正常的，见 §一.3）；**顶栏横跨 1280dp**，
标题被拉到最左、模式胶囊被推到最右，中间空一大段；**底部控制岛同样横跨全宽**，
5 个功能键（自动播放/目录/存图/分享/设置）等分摊开，图标之间隔着一臂远。

---

## 一、master 的权威事实（逐字核过，`lib/pages/reader/`）

**结论先行：master 的阅读器 chrome 完全没有宽屏分支。** 顶栏与底栏是贴边通栏
（`left:0, right:0`），页面图是纯黑底上的 `BoxFit.contain` 不限宽。全局宽屏常量
`changePoint = 600`、`changePoint2 = 1300`（`lib/foundation/consts.dart:1,7`）
在整个 `lib/pages/reader` 下**一次都没被引用**。

所以"照 master"这条路在阅读器上是有边界的 —— 能照的只有下面三条真实存在的规则：

| # | master 的做法 | 位置 | 是否宽度相关 |
| --- | --- | --- | --- |
| 1 | `small = (maxWidth - buttons.length * 50) < 120`：宽分支 = 左侧 `E#:P#` 页码芯片 + `Spacer()` 把**纯图标**按钮推到右端；窄分支 = 按钮间插 `Spacer()` 均分、丢掉芯片 | `scaffold.dart:528-550` | **是**，master 唯一一条按宽度分叉的 chrome 规则 |
| 2 | 顶栏定高 `kTopBarHeight = 56`、底栏定高 `kBottomBarHeight = 105` | `scaffold.dart:15,17,496` | 否 |
| 3 | 章节目录与阅读设置都是**侧边抽屉**，定宽 `width: 400`，`showSideBar(dismissible: true)`，任何窗口宽都一样 | `scaffold.dart:662,727,657-728` | 否（但 400 是现成数） |

补充事实，供判断"哪些是我们的、不该动"：

- master 底栏按钮是 **纯图标 + Tooltip**，没有文字标签；我们这版是"图标在上、中文标签在下"的 5 张等分卡（`VeneraReaderScreen.kt:915-1046`）。标签字号 `tokens.type.badge` 在 master 没有对应物。
- master 的条是**直角通栏 + BlurEffect**，圆角 0、无阴影；我们的是四边留白 16/12dp 的 24/28dp 圆角悬浮岛 + `shadow(12/16.dp)`（`:717-722`、`:806-811`）。
- master 的滑块是自绘 `CustomSlider`（24dp 高、6/8dp 轨道、22dp 滑块）；我们用的是 M3 原生 `Slider`（`:855-868`）。
- **页面图不限宽**：`comic_image.dart:338-365` 只读传入约束、`images.dart:365-373` 用 `BoxFit.contain`，横屏看竖页就是两侧留黑。→ 我们现在的表现和 master 一致，**这一项不改**。
- master 的"每屏几张图"是**按方向**（`readerScreenPicNumberForPortrait/Landscape`，默认各 1）而不是按宽度，属用户设置项，不是自适应。

## 二、当前实现的位置

| 部件 | 位置 | 现状 |
| --- | --- | --- |
| 顶栏 | `reader/VeneraReaderScreen.kt:715-793` | `padding(16,8) + fillMaxWidth()`，无宽屏档 |
| 底部控制岛 | `:804-1047` | `padding(16,12) + fillMaxWidth()`，两行：上一话/Slider/下一话 + 5 键等分 |
| 自动播放迷你控制器 | `:1052-1061` | 右下 16dp，与 master `right:16 bottom:36` 一致，不用改 |
| 章节目录抽屉 | `:1130-1200` 区段 | 贴底通栏 `fillMaxWidth()` + 顶部大圆角 |
| 阅读设置抽屉 | 同文件后段 | 同上 |

## 三、可选路线（待拍板）

### 路线甲：纯照 master —— 只搬它那条唯一的宽度规则

把 `scaffold.dart:528-550` 的 `small` 判据搬过来：宽屏时功能键**右靠 + 图标化 + 左侧页码芯片**，
手机档维持现状。零新数字，全部有出处。
代价：宽屏会变成"右边一排小图标"，和我们现在的中文标签岛是两种设计语言，
手机档与宽屏档观感差异被拉大；而且等于在宽屏上推翻已定稿的岛式面板。

### 路线乙：复用我们已定稿的宽屏收口口径（推荐）

顶栏与控制岛在 `>600dp` 时收口为 `min(540dp, 宽 - 24×2)` 并居中，
与本轮刚落地并量过的底栏**同一个契约、同一个函数**（`wideScreenChromeMaxWidth`，现行落点
`components/WideScreenPolicy.kt:152`；落地当时名为 `navBarWideScreenMaxWidth`）。
540 与 24 都来自 master（`_kGlassBarMaxWidth` / `_kGlassBarHorizontalPadding`），不是新造数。
手机档：`<=600dp` 时该口径返回 `null`，几何一字不改。
代价：540dp 要放"上一话 + Slider + 下一话"和 5 个带标签的键，比现在挤；
需要同时确认标签行不换行（必要时宽屏档走 master 的图标化，即甲乙混合）。

### 抽屉（目录 / 设置）单独一条

master 是定宽 400 的侧边抽屉，我们是通栏贴底 sheet。
建议：宽屏档把 sheet 内容收口到 **400dp 居中**（master 现成数），手机档不动。
这样既不改我们已定稿的"底部抽屉"形态，又不再让目录在 1280dp 上摊成一整条。

## 四、手机端不能被改坏（硬约束）

- 所有新分支一律挂在 `>600dp` 上，手机档走的代码路径与数值**逐字不变**；
  判据与底栏共用同一个函数，避免两处阈值漂移。
- 宽度用 `rememberContentWidth` / `LocalConfiguration.screenWidthDp` 现成 helper，
  不引入新的测量方式。
- 改完必须在**手机档**复测一遍：竖屏 411dp 与横屏 600dp 边界两侧各看一次顶栏/岛/抽屉。

## 五、本轮同时暴露、但属于另一族的问题（待单独排）

1. **阅读器返回会跳过详情页直接落主界面**。首要嫌疑：`Navigation.kt:603-611`
   的自毁分支 —— `onBack` 先写 `shell.pendingSession = null` 再 `popBackStack()`，
   而退场动画期间阅读器条目仍在组合内，一旦重组就会走进
   `if (session == null) LaunchedEffect(Unit) { popBackStack() }` 这条**新**分支，
   于是 pop 第二次，把详情页一起弹掉。
   判别实验（等用户跑）：左上角返回箭头 vs 系统返回手势，两条路径只有一条会跳 → 即锁定。
2. **探索页底部把库的原始异常直接印给用户**：`feature/explore/UnifiedExploreScreen.kt:234`
   存 `e.message`、`:459-462` 原样上屏，实测文案 "The coroutine scope left the composition"。
   与 1 同族（离开组合后仍有协程在跑）。次生：该文案被悬浮底栏压住半截。

## 六、待办

- [ ] 用户拍板：顶栏/控制岛走甲 / 乙 / 甲乙混合；抽屉是否收 400dp
- [ ] 用户跑判别实验（箭头 vs 手势），定 1 的根因
- [ ] 方案定稿后实现 + 手机档回归
- [ ] 模拟器逐像素复核：宽屏档顶栏/岛实测宽度是否等于 540.0dp、是否居中

## 七、已拍板并落地（2026-09-22）

拍板：**顶栏/控制岛走乙（沿用底栏 540 收口）**；**抽屉内容收口到 400dp 居中**。

| 改动 | 位置 |
| --- | --- |
| 宽屏口径抽成唯一出处 | 新文件 `components/WideScreenPolicy.kt`：`wideScreenChromeMaxWidth(screenWidth)`（>600dp → `min(540, 宽-48)`，手机档 `null`）、`WideScreenDrawerWidth = 400.dp`。原 `VeneraFloatingNavBar.kt` 里的 `navBarWideScreenMaxWidth` 搬进来并改名，三处调用点（胶囊底栏 / 玻璃底栏 / 阅读器）统一 |
| 阅读器顶栏 | `VeneraReaderScreen.kt` 顶栏 Surface：`.fillMaxWidth()` → `.then(chromeWidthModifier)` |
| 底部控制岛 | 同上，岛 Surface 一处 |
| 目录 / 设置抽屉 | 两个内容根 Column：`.fillMaxWidth()` → `.then(drawerWidthModifier)`（宽档 `fillMaxWidth().wrapContentSize(TopCenter).width(400)`，sheet 本体仍通栏，不动 m3 抽屉的动画与手势） |
| 单测 | 新 `components/WideScreenPolicyTest.kt`：锁手机档恒为 `null`（360/411/600dp）、宽档恒为 540dp |

**刻意未做**：章节评论 sheet（`ReaderPanel.COMMENTS`）没收口 —— 它的内容根在另一个文件
`ChapterCommentsSheet.kt`，要在调用点包一层 `Box` 才限得了宽，而 `wrapContentSize` 会同时
把高度也交给内容自己决定，可能改坏它的滚动。风险/收益不划算，留作单独一条。

**顺带查出的一个事实**（不改，但值得记）：master 原式 `min(540, 宽 - 48)` 在 `>600dp` 档里
"宽 - 48" 这一项**永远赢不了** 540（600 − 48 = 552 > 540）。也就是说 master 的宽窗底栏
实际上就是定宽 540；只有把阈值降到 588 以下，窗口项才开始生效。
