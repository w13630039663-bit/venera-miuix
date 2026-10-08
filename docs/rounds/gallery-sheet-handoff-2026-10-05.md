# 「关于这张图」面板两个 bug —— 交接文档（2026-10-05）

> **这份是交接件，给接手的 AI 一次性读全。**
> 前身是两份平行文档（`gallery-sheet-dead-tap-forensics-2026-10-05.md` = 「点不动」、
> `gallery-sheet-jitter-forensics-2026-10-05.md` = 「鬼畜」）。本文件把它们**合成一条时间线**，
> 并且**严格分三层写**：
>
> - **① 实测读数** —— 有时间戳、有原始日志行，可复查；
> - **② 源码判据** —— 带文件与行号，来自官方 sources jar；
> - **③ 未证候选 / 推断** —— **不许当成结论往下走**。
>
> 凡是没标「已坐实」的，都当 ③ 读。
>
> 🔴 **接手第一件事**：读 §6「我的看法与判断」——那里写了**哪些是钉死的、哪些是推断、哪些已被证伪**。
> 这轮最大的教训就是「机理链说得通 ≠ 现象在那一环上」，前两轮各栽一次。

---

## 0. 一分钟版（TL;DR）

- **两个 bug，一条上游。** 用户报的是「点不动」和「下拉鬼畜」两个症状，追下来发现它们是**同一个结构性问题的两个出口**：Compose 半模态里「**内容高度 → 锚点 → 面板姿态 → 可用高度**」这条自我引用环，加上「**内容异步长高**」这个触发器。
- **「点不动」根因已钉死**（109 笔死档全部命中同一指纹）：`dragTarget` 漏勺 ⇒ `isAnimationRunning` 恒真 ⇒ 面板拖动层在 Initial 段抢走每一笔 DOWN ⇒ 整块内容点不动、但拖动照常。
- **「鬼畜」根因尚未完全钉死**（这是本文件最重要的一句诚实声明）。目前最强证据指向「**内容在动、面板 top 稳定**」，但**最后一环没有读数**。
- **本轮做了一件当时以为会解决问题、实际被证伪的事**：把 `consumedFromDrag` 改成条件扣位移（方案 2）。它没生效，**原因不是判据写错，而是判据从未被调用** —— `Modifier.nestedScroll` 挂在了滚动容器的**内侧**，而它是**向祖先注册**的（§3.3）。
- **当前状态**：根因修法（`nestedScroll` 挪到祖先位）已落地在 03:20 的包里，**但还没有真机验证过**。接手者要做的第一件事，就是验证它（§7）。

---

## 1. 两个 bug 是什么

### Bug A ——「点不动」（内部代号：死档）

用户原话（2026-10-04，用户**自己推翻了我第一次的归因**后重述）：

> - 「打开面版就无法点击，但是可以正常拖动」
> - 「第一次打开就不动」
> - 面板松手后「没有」微动
> - 「就是很单纯的点不动，**什么内容都点不了，长按也无反应**」

**三条关键约束**（限定了成因的形状）：
1. **整块面板哑** —— 不是某一枚标签点不动，是所有可点内容都不行；
2. **拖动照常** —— 面板本身能拖、能收，说明输入通道没整体坏；
3. **与"先做过某个动作"无关** —— 第一次打开就可能中。

> 「退出图片、重新打开『关于这张图』可能就好了」—— 这是**面板与它的锚点被重建**的旁证。

**复发频率**：偶发（用户原话：「这个复现不一定能复现，这个是偶尔会这样，不是一定会这样」）。
旧包统计：触摸 `初=true`（= 点不动）**103 / 150 = 68.7%**；新包压到 **5 / 106 = 4.7%**（§5.4）。

### Bug B ——「鬼畜」（内部代号：帧级上下跳）

用户原话（2026-10-05 02:45）：

> 「在关于卡片**全屏展示**，并且把**标签展示完全**（比如 50 个）的时候，**往上拉到底**后，
> 如果不拉上面的把手下拉、**直接在内容区下拉**，面板就会**快速鬼畜上下跳，间隔可能不到几帧**。」

追问确认（同轮）：

| 问 | 答 | 它排掉/坐实了什么 |
| --- | --- | --- |
| 鬼畜时面板档位 | **已顶格（全展）** | `current == Expanded` ⇒ 内容区已用满高度上限 |
| 「上拉到底」指 | **到底，不能再下滑那种** | 内容 `verticalScroll` 已到 `maxValue` ⇒ 吃不下任何下拉位移 |
| 下拉后面板最终 | **滑动距离够就下来、不够就下不来** | 不是「完全卡死」，是**锚点拉扯**：位移没跨过阈值就回弹 |

**三条合起来 = 完整触发条件**：面板 `Expanded` + 内容滚到底 + 手指在**内容区**（非把手）下拉。
缺任一条都不触发 —— 这解释了它为什么「偶尔」：要「内容比屏高 + 面板顶格 + 滚到底」三件事同时成立。

> **「拉把手就没事」**（用户确认）是关键旁证：拖把手走 m3 自绘的那条把手（`Surface` 上的
> `anchoredDraggable`，`BottomSheet.kt:329`），**不经过内容区的嵌套滚动** ⇒ 天然不触发这条环。

---

## 2. 环境与复现口径

| 项 | 值 |
| --- | --- |
| 仓库 | `D:\venera-compose`，分支 `compose-migration` |
| 栈 | Kotlin + Compose；`:app`(Android) / `:desktop`(CMP) / `:engine-probe` |
| 关键依赖 | miuix `0.9.4-rc01` + material3 `1.5.0-alpha22`；**foundation / ui 实际解析到 `1.12.0`** |
| 版本 | `versionName = 2.0.3`；minSdk 33 / targetSdk 37 |
| applicationId | `com.github.w13630039663bit.venera.miuix` |
| 验证机 | 一加 **PJZ110**，1080×2376 @420dpi（density 2.625）；屏幕高 2376px |

**复现三条件**（Bug B）：
1. 画廊墙点进一张大图 → 点「关于这张图」；
2. **标签全展**（点「展开全部标签」，让内容高到超过屏高）；
3. 面板**拉到全屏（Expanded）** → 在内容区**上拉到底** → **在内容区持续下拉**。

**复现动作（Bug A，一次跑完能拿死活两档对照）**：
1. 点进大图 → 点「关于这张图」；
2. **等 3 秒**（让译文/署名异步到位、内容长高）→ 连点 3~4 枚标签；
3. 面板往上拖到底、松手停稳 → 再连点 3~4 枚；
4. 按不动时点一次节标题「展开全部」、一次底部「复制链接」（这两处是**普通 `Modifier.clickable`**，
   用来分「坏在 chip 那条链」还是「整块内容都收不到」）；
5. 关面板 → 重开 → 立刻点一枚（这一档通常是活的，当基线）。

**取日志（设备侧落盘，主缓冲只留约 48 秒，主机侧抓取必丢）**：
```bash
adb pull /sdcard/Android/data/com.github.w13630039663bit.venera.miuix/files/forensics/
```
（走外部私有目录 ⇒ **不需要 `run-as`**。Android 11+ 的系统文件管理器**不能**浏览 `Android/data`，
所以主路径是 adb pull。）

⚠️ **杀抓取进程用 `pkill -x logcat`**：写 `pkill -f 'logcat.*probe.log'` 会**连执行命令的那个 shell
一起杀掉**（命令行里就含这串字），表现为退出码 143、什么都没做。

---

## 3. 已坐实的机理（分层写）

### 3.1 【已坐实】Bug A 的完整链 —— 每一环都有源码行号 + 真机读数

| # | 环节 | 出处 | 内容 |
| --- | --- | --- | --- |
| ① | 面板拖动层的「抢按下」开关 | foundation `AnchoredDraggable.kt:260`（默认值）；m3 `BottomSheet.kt:329-334`（调用点**没传**该参 ⇒ 走默认） | `startDragImmediately: Boolean = state.isAnimationRunning` |
| ② | 抢到就立刻吃 down | foundation `Draggable.kt:812-820`（注释原话 *"If we shouldn't await touch slop, we consume the event immediately"*） | `if (pass == Initial) { if (awaitTouchSlop == No) { firstDown.consume(); state.consumedOnInitial = true } }` |
| ③ | down 被吃过 ⇒ clickable 不开始跟踪 | foundation `Clickable.kt:1111-1115` | `isChangedToDown(requireUnconsumed = true)` 为假 ⇒ 整条点击链**静默作废**（不回调 onClick、不发 PressInteraction） |
| ④ | 为什么 `isAnimationRunning` 恒真 | foundation `AnchoredDraggable.kt:993` / `:1024-1027` / `:935-936` | `dragTarget` 是 `mutableStateOf(null)`；**`updateAnchors` 里 `if (!trySnapTo(newTarget)) dragTarget = newTarget` —— 没有 `try/finally`，置上之后没人负责清**（=「漏勺」）；而 `isAnimationRunning ⇔ dragTarget != null` |
| ⑤ | 清它的唯一路径 | foundation `AnchoredDraggable.kt:1197-1226`（`finally` 在 `:1217-1219`） | 带 `targetValue` 的重载里 `try { dragMutex.mutate { dragTarget = targetValue } } finally { dragTarget = null }` |
| ⑥ | 用户拖动走的是**不带** `targetValue` 的重载 | foundation `AnchoredDraggable.kt:1154-1172` | 它**既不置也不清** `dragTarget` ⇒ 拖动过程中被置上的标志，拖动结束也**不清** |
| ⑦ | 什么时候会「拖动持锁时锚点变化」 | m3 `BottomSheet.kt:293-308`（锚点定义） | `Expanded at max(0f, fullHeight - sheetHeight)` ⇒ **锚点依赖内容量出的高**。而本页内容（标签译文 / 画师署名）是**异步分批到达**的（`LaunchedEffect(tagTranslations.size, artistCredits.size)`）⇒ 每来一批就 `updateAnchors` 一次 ⇒ 反复撞竞态 |

**合成**：`dragTarget` 被置上 → `isAnimationRunning` 恒真 → 拖动层在 Initial 段吃掉每一笔 DOWN →
clickable 根本不用启动 ⇒ **整块内容点不动、但拖动照常**。

**真机读数坐实（决定性）**：2026-10-05 02:34 那份日志里，
**109 笔死档（`初=true`）全部满足 `下时[动画中=true]`**，且死档期内面板 `偏移` 持续非 0、抬手还在变
（= 与「能拖」口述一致）。活档（`初=false`）则是 `下时[动画中=false]`。

### 3.2 【部分坐实】Bug B 的机理 —— 前半段有读数，最后一环没量到

**早期假设（曾被写进文档，后来被证伪）**：
> m3 自己的连接器 `ConsumeSwipeWithinBottomSheetBoundsNestedScrollConnection.onPostScroll`
> （`SheetDefaults.kt:655-656`）对 UserInput **无条件** `dispatchRawDelta(available)` ⇒ 面板被逐帧推动
> ⇒ 触发 m3 的 `draggableAnchors` 重算 ⇒ `Expanded` 锚点 = 屏高 − 内容高 ⇒ 环 ⇒ 帧间来回。

这条链「听起来」完美解释了症状。**但它是错的（或至少不完整）**，理由见 §3.3 与 §6.2。

**中期读数（2026-10-05 03:00 那份日志，3 批数据里最关键的一批）**：
对新包段（02:58~03:00）110 条 chip 见证做配对分析：

| 判定 | 条数 | 依据 |
| --- | --- | --- |
| **内容自己滚**（面板 top 没变） | **39** | 该笔的 `面板 触摸` 里 `top按下 == top抬起` |
| 面板在动 | 3 | `\|盒顶变化\| ≈ \|top抬起 − top按下\|` |
| 混合 / 孤例 | 12 | 其余 |

其中 **46/110 条 chip 见证里 `高变化=0.0` 且 `宽变化=0.0`** —— 内容**形状没变**，只是整块在窗口里
**平移**（`盒顶变化` 从 **−1799 到 +1211**）。而同一刻**面板 top 稳定在 247**、`偏移=0.0`。

> ⇒ **真正在剧烈运动的，是内容区 `bodyScrollState`，不是面板。**
> 用户看到的「面板上下跳」，视觉上是**面板里的内容在猛窜**。

⚠️ **一条要撤回的早期错读**：中途曾把 `盒顶变化` 直接当「内容滚动量」并推出「位移被放大 5.6 倍」。
**那条不成立** —— `盒顶` 取自 `boundsInWindow()`，**完全被裁剪时返回 `Rect.Zero`**
（`GallerySheetProbe.kt:370-377` 头注已写明；日志里确有一笔 `抬手时盒顶=0.0`）。
「内容在动」这个结论仍站得住（面板 top 等值那一列是独立证据），但**不能**用它的数值算倍数。

**「自起动画」这个现象（已坐实存在，成因未定）**：
新包段共 **17 次**「面板 `current == target == Expanded` 却仍 `落定 running=true`」，偏移分布
`[43.8, 92.9, 93.5, 93.8, 102.7, 105.4, 108.3, 112.4, 118.3, 130.2, 130.5, 135.1, 136.2, 144.3, 174.0, 174.4, 203.0]`。

两个特征很关键：
1. **没有任何一次跑到 0 或 1188**（那两个才是真锚点）⇒ 动画是往一个**浮动目标**跑的，
   而浮动的只能是 `Expanded` 锚点 = `fullHeight − sheetHeight`；
2. 值连续散布在 43~203、**没有整齐常量特征** ⇒ 排除了「某个固定 offset 补偿」。

**最干净的一次循环**（`02:59:58~03:00:00`，无干扰）：
```
02:59:58.253 #306 面板 姿态 current=Expanded target=Expanded      ← 面板静止在 Expanded
02:59:58.264 #307 看门狗 评估 手指=false 动画中=true 偏移=0.0     ← 声称静止却卡着
02:59:58.265 #308 看门狗 已重申                                    ← 看门狗清掉 dragTarget
02:59:58.265 #309 面板 落定 running=false 动画中=false 偏移=0.0    ← 干净了
02:59:59.263 #310 面板 触摸 down=(847,1078) 移动=65px … top=247→248 下[0.0] 抬[27.5]
02:59:59.302 #311 面板 落定 running=true 动画中=true 偏移=135.1 current=Expanded target=Expanded  ← 又跳！
02:59:59.540 #312 面板 姿态 current=PartiallyExpanded target=Expanded
```
**注意 `#310` 的 `down=(847,1078)`** —— y=1078 在屏高 2376 的中部，**不是把手那条**（顶格时把手在 top≈247）。
⇒ 用户**在内容区滑了一下**（65px），**面板就被推走 135px**，`current` 掉到 `PartiallyExpanded`。

**最强候选（待读数裁决）**：`ScrollState` 的 `value` setter 里有 `coerceIn(0, maxValue)` ——
**`maxValue` 一变小，`value` 就被强制拉回**，屏上就是「内容猛地一跳」。
而 `maxValue = max(0, 内容自然高 − 视口高)`，**视口高** 又由 `heightIn(max = bodyMaxHeight)` 决定
⇒ 内容自然高在 `bodyMaxHeight` 附近游走时，`maxValue` 会在 0 与非 0 之间翻。

### 3.3 【已坐实，且是本轮最重要的发现】`nestedScroll` 挂错侧 ⇒ 判据从未被调用

这是「**改了代码却完全没效果**」的真相，前两轮都没想到这一层。

**两份官方源码（compose-ui 1.12.0）说得非常直白**：
- `NestedScrollNode.kt:60-61` —— `parentNestedScrollNode = findNearestAncestor()`，**只向上找**；
- `NestedScrollModifier.kt:54-59` —— `onPostScroll` 原文注释：
  *"the dispatching (scrolling) descendant made their consumption and **notifies ancestors**"*。

⇒ **嵌套滚动事件是从「正在滚动的后代」向祖先传播的。**

而上一版的写法是：
```kotlin
.heightIn(max = bodyMaxHeight)
.verticalScroll(bodyScrollState)        // ← 滚动容器（"dispatching descendant"）
.nestedScroll(sheetScrollConnection)    // ← 挂在它的**内侧**
```
`Modifier.a.b` 里 `b` 在内层 ⇒ 我们的连接器挂在滚动容器的**后代侧** ⇒
**既不是它的祖先、也不在「后代的祖先链」上** ⇒ `onPostScroll` **一次都不会被调用**。

**读数指纹**：新包日志里 `扣判`（`onPostScroll` 的判据读数）**0 条**，
而本轮加的**无条件调用计数**（`postScrollCalls`，`count==1 || count%200==0` 时记一行）**也是 0 条**
⇒ 从「组合没变」和「一次都没被调」两种可能里，**排除了前者**。

> 这解释了此前所有「改了没效果」：判据本体、单测、dex 里新字符串**全都在**，
> 唯独这份位移**不会经过那段代码**。

**修法**：把 `.nestedScroll(...)` 挪到 `.verticalScroll(...)` **之前**（祖先位）。
挪位后语义正是我们要的：`onPostScroll` 是「自己先、父后」（`NestedScrollNode.kt:93-105`），
我们在外 ⇒ `verticalScroll` 是**子**、我们是**父** ⇒ 它先滚，我们拿到它滚不掉的**那一份**
（= 内容到底后多余的位移），正是 `consumedFromDrag` 要处理的对象。

**单测不用改**（两道门语义不变）。

### 3.4 【已坐实】两个 bug 的关系：「点不动」是「自起动画」的**下游**

新包段 5 次「点不动」（`初=true`）**全部紧跟在一笔「Expanded 自起动画」之后**（约 200ms），无一例外：

| 自起动画 | 偏移 | → | 点不动 | 下时姿态 |
| --- | --- | --- | --- | --- |
| 02:58:22.584 | 174.03 | → | 02:58:23.246 | `动画中=true 偏移=1188 PartiallyExpanded` |
| 02:59:09.008 | 203.03 | → | 02:59:09.757 | 同上 |
| 02:59:56.989 | 144.29 | → | 02:59:58.108 | 同上 |
| 02:59:59.302 | 135.11 | → | 03:00:00.080 | 同上 |
| 03:00:08.882 | 108.31 | → | 03:00:09.857 | 同上 |

**机制**：自起动画把面板从 Expanded 推离（跑到 108~203），途中 `current` 掉到 `PartiallyExpanded`
⇒ `dragTarget` 残留 ⇒ `isAnimationRunning == true` ⇒ 下一笔 DOWN 在 Initial 段被拖动层吃掉 ⇒ **点不动**。

**新旧包对比（同一台机、同一份面板）**：

| 指标 | 旧包（02:34~02:57） | 新包（02:58~03:00） | 说明 |
| --- | --- | --- | --- |
| 触摸 `初=true`（点不动） | **103 / 150 = 68.7%** | **5 / 106 = 4.7%** | **↓↓↓ 与用户「复现不出来」吻合** |
| `Expanded` 自起动画 | 11 | 6 | 降但**未消** |
| 死档 dump | 20 | 6 | 随点不动一起降 |

> **旧包 103:11 的比例早就该提示上下游关系** —— 下游数远大于上游数，说明**一个上游事件会产多个下游事件**。
> 我把它俩当两个平级 bug 追了一轮半，这是本轮的教训之一。

---

## 4. 做过什么修改（按批次，含验证状态）

### 批次 1（02:30 包，`SHEET_SETTLE_WATCHDOG`）—— **已验证：能执行，但不治复发**

**改动**：`GalleryInfoSheet.kt` 里加一段「落定看门狗」：
内容到位后，若面板**声称自己在 `Expanded`** 却还卡着（`isAnimationRunning == true`）**且手指不在屏上**，
就重申一次落定（`sheetState.expand()`），借 `anchoredDrag` 走完的 `finally` 清掉那个标志。

配套加 `Modifier.observePointerDown(down)`（`GalleryInfoSheet.kt:1279-1294`）提供「手指不在屏上」这道门
—— 理由是一条被核过的库语义：`SheetState.expand()` 与用户拖动**同为 `MutatePriority.Default`**
（m3 `SheetDefaults.kt:343` / foundation `AnchoredDraggable.kt:1155`），而
`MutatorMutex.canInterrupt` 判的是 `>=`（`MutatorMutex.kt:82`）⇒ **同优先级也会互相抢**
⇒ 在用户拖动当中重申落定会**取消掉那次拖动**。

**真机结论（02:34 那批）**：
- ✅ 看门狗**确实能执行**（日志里有 `修法 看门狗 已重申`）；
- ❌ **没解决复发**：`#21`/`#42` 两次「已重申」之后，`dragTarget` **又被重新置上**
  （内容还在异步长高，每次 `updateAnchors` 抢不到锁就又置一次）。
- ⚠️ 它还**覆盖不到**两种死档姿态：① `current=Expanded` 但 `isAnimationRunning` 恒真（重申一次，下一拍又被置）；
  ② `current=PartiallyExpanded`（看门狗按设计「卡在别的档时只记日志不动作」）。

**⇒ 看门狗只能算缓解，不是修法。** 但它作为**观测点**有价值（日志能告诉我们是否又撞上）。

### 批次 2（03:00 包，判据条件化）—— **已验证：被证伪（没生效）**

**改动**（两处）：
```kotlin
// gallery/domain/GallerySheetScroll.kt
fun consumedFromDrag(availableY: Float, panelExpanded: Boolean, contentAtBottom: Boolean): Float =
    if (panelExpanded && contentAtBottom) availableY else 0f
```
```kotlin
// gallery/ui/GalleryInfoSheet.kt —— bodyScrollState 提到 sheetScrollConnection 之前，判据读它「到底了没有」
```

**设计意图**：面板 `Expanded` + 内容到底 ⇒ 扣住位移 ⇒ 外层 m3 拿到 0 ⇒ 面板不动 ⇒ 锚点不重算 ⇒ 环断。
其余两档放行，保住「下拉关面板」的手势。

**验证结果**：**没生效**。用户反馈「鬼畜还是有」。日志显示 `扣判` **0 条**。
原因**不是判据写错**，而是**判据从未被调用**（§3.3，`nestedScroll` 挂错侧）。
⇒ 这就是「修法打空」的真相，也是本轮最大的教训。

### 批次 3（03:20 包，`nestedScroll` 挪位）—— **⚠️ 尚未真机验证（接手者第一件事）**

**唯一行为改动**（`GalleryInfoSheet.kt:446-482`）：
```kotlin
Column(
    modifier = Modifier
        .fillMaxWidth()
        .heightIn(max = bodyMaxHeight)
        .nestedScroll(sheetScrollConnection)   // ← 修：祖先位（原来是 .verticalScroll(...).nestedScroll(...)）
        .verticalScroll(bodyScrollState)
        .padding(horizontal = tokens.spacing.screenHorizontal),
)
```
附 20 余行头注解释「上一版为什么完全没生效（`扣判` 0 条）」+「挪位后的语义」。

**同包的量具加固**：`postScrollCalls` 无条件计数（把「没被调用」与「被调了但值没变」分开）。

**回归口径（已跑）**：
- `:app:compileDebugKotlin` EXIT=0（仅剩一条既有的 `rememberModalBottomSheetState` 弃用警告）；
- `:app:testDebugUnitTest` 通过；
- `:app:assembleDebug` EXIT=0；
- 4 个 ABI 包已出（`app/build/outputs/apk/debug/*-debug.apk`，102 MB 级，03:20 时间戳）；
- 新串已 grep `classes16.dex` 核实（`扣判 第` / `扣判 ` / `dy总` / `面板内容` / `滚动 value=`）。

> ⚠️ **包体尺寸不可用来判新旧**：多颗 arm64 debug **逐字节同尺寸**（102,144,511 B），
> 而里面各加了不同的新字符串。判新代码进包要**直接 grep dex**：
> ```bash
> cd /tmp/dexchk && unzip -o -q <apk> 'classes*.dex' && grep -a -l "扣判" classes*.dex
> ```

### 批次 4（本轮新增，**只因写交接文档**）—— 无代码改动

---

## 5. 日志内容（关键原文，可复查）

日志落盘位置：`docs/forensics/`。三批：

| 批次 | 目录 | 大小 | 覆盖时段 |
| --- | --- | --- | --- |
| 02:34（旧包，Bug A 复现） | `deadtap-2026-10-05-0234/`（12 文件 = `sheet-probe.log` + 11 份 `deadtap-*.log`） | 49092 B | 02:34~02:57 |
| 03:00（批次 2 包） | `jitter-verify-2026-10-05-0300/forensics/` | 160483 B | 02:34~03:00（**02:58:15 起为新包段**） |
| 03:12（批次 3 前的最新包） | `jitter-verify-2026-10-05-0312/forensics/sheet-probe.log` | 220006 B | 新包段自 **03:11:19** |

另有早期一份 `docs/forensics/gallery-sheet-dead-tap-probe-2026-10-05-0138.log`（367 行，01:38 那批）。

### 5.1 【决定性】`扣判` 0 条（新包 03:12 那份）

```
$ grep -c "扣判" docs/forensics/jitter-verify-2026-10-05-0312/forensics/sheet-probe.log
0
$ grep -c "面板内容 滚动" docs/forensics/jitter-verify-2026-10-05-0312/forensics/sheet-probe.log
202
$ grep -c "dy总" docs/forensics/jitter-verify-2026-10-05-0312/forensics/sheet-probe.log
74
```

⇒ 三支量具里**两支命中、判据那一支 0 条** ⇒ 判据不在链上（§3.3）。

### 5.2 【决定性】`maxValue` 分布（同一份日志）

```
$ grep -o "面板内容 滚动 value=[0-9]* max=[0-9]*" ... | grep -o "max=[0-9]*" | sort | uniq -c | sort -rn
    169 max=2276
     31 max=0
      1 max=276
      1 max=2166
```

⇒ **内容确实是可滚的常态**（2276）；`max=0` 集中在面板掉档后的瞬间 ⇒
§3.2 那个候选（`maxValue` 在 0/非 0 间翻）**方向对，但只是副产物**。

### 5.3 【决定性】死活转折点（02:34 那批，0.5 秒内从活变死）

```
#24 16.182 面板 触摸 down=(736,1093) … 按下三段 初=false 主=true  终=true
            下时[动画中=false 偏移=0.0      current=Expanded target=Expanded]   ← 活
#26 16.221 面板 落定 running=true 动画中=true 偏移=130.22067 current=Expanded target=Expanded
#27 16.671 面板 触摸 down=(688,217) … 按下三段 初=true  主=true  终=true
            下时[动画中=true  偏移=1187.9287 current=Expanded target=Expanded]   ← 死
            抬时[动画中=true  偏移=1512.4248 current=Expanded target=Expanded]
#28 16.672 死档 已 dump（面板 根节点 down 在 Initial 段已被祖先消费（拖动层抢按下））
```
**关键 `#26`**：`target` 已经是 `Expanded`（目标没变），但面板又开了一段动画把 `偏移` 从 130 收回 0。
就在这段动画**还没走完**时用户又按下 → `#27` 的 `下时 动画中=true` ⇒ 拖动层在 Initial 段吃掉了 DOWN。

### 5.4 【最干净的单点证据】手指动 34px，面板被推 83.86px

```
02:59:56.304 #299 面板 触摸 down=(801,1224) 移动=34px
        下时[动画中=false 偏移=0.0 current=Expanded target=Expanded]     ← 起手干净、面板在 Expanded、偏移 0
        抬时[动画中=false 偏移=83.86 current=Expanded target=Expanded]   ← 抬手时偏移已 83.86！
02:59:56.953 #300 面板 触摸 down=(829,1175) … 下时[偏移=0.0] 抬时[偏移=0.0]   ← 又回 0
02:59:56.989 #301 ★自起动画 偏移=144.29 current=Expanded target=Expanded
02:59:57.228 #302 姿态 current=PartiallyExpanded target=Expanded
```

### 5.5 最干净的「内容自己滚」形态（02:58:56）

```
#136 面板 触摸 down=(696,1477) 移动=70px top按下=247.0 top抬起=247.0 下[偏移=0.0] 抬[偏移=0.0]
#137 标签「ahegao」 盒顶变化=-392.0 高变化=0.0 宽变化=0.0
```
⇒ 手指只动 70px，chip 却挪了 392px，**且面板纹丝不动** ⇒ `scrollState.value` 被**非手指来源**改了。

---

## 6. 我的看法与判断（★ 接手必须读完这节）

### 6.1 已钉死的（可以当下结论往下走）

| # | 结论 | 证据强度 |
| --- | --- | --- |
| 1 | **Bug A 的机制链成立**：`dragTarget` 漏勺 ⇒ `isAnimationRunning` 恒真 ⇒ 拖动层 Initial 段吃 DOWN ⇒ clickable 不启动 | 强：源码行号齐全 + **109 笔死档 100% 命中 `下时 动画中=true`** + 与「能拖」口述一致 |
| 2 | **「点不动」是「自起动画」的下游**，不是独立故障 | 强：新包 5 次点不动全部紧跟一笔自起动画约 200ms |
| 3 | **`Modifier.nestedScroll` 挂在滚动容器内侧 = 静默失效** | 强：两份官方源码 `findNearestAncestor()` + "notifies ancestors" + **`扣判` 0 条** |
| 4 | **现象本体是「内容在动」，不是「面板在动」**（至少 02:58~03:00 那段） | 中强：39:3 配对表 + 面板 top 稳定 247 |

### 6.2 被证伪的（**别再走这两条路**）

| 被证伪的说法 | 证伪依据 |
| --- | --- |
| **「m3 连接器 `dispatchRawDelta` 逐帧推面板 ⇒ 锚点自我引用环」是鬼畜的根因** | ① 面板 top 稳定（39:3 配对表）；② 就算它是真的，我们的连接器**压根没被调用**，改它不会有任何变化 |
| **「方案 2（条件扣位移）能治鬼畜」** | 真机实测没生效，`扣判` 0 条 ⇒ 打空（§4 批次 2） |
| 「位移被放大 5.6 倍」 | `盒顶变化` 在裁剪处会塌成 `Rect.Zero`，不能用来算倍数（§3.2 的撤回声明） |

### 6.3 尚未钉死的（**这是接手者的主战场**）

**Bug B「鬼畜」的最后一环没有读数。** 具体地说：

1. **「内容为什么自己滚」** —— §5.5 证明 `scrollState.value` 被非手指来源改了。
   最强候选是 `ScrollState.value` setter 的 `coerceIn(0, maxValue)`（`maxValue` 变小 ⇒ `value` 被拉回），
   但也可能是别的（比如 `verticalScroll` 与 m3 的 insets 布局期/绘制期不同轨）。
   **`面板内容 滚动` 这一列（202 条）就是为裁决它加的** —— 有读数，但**还没人来判读过**。
2. **「自起动画为什么会发生」** —— 17 次、目标浮动、值散布 43~203。
   最可能仍是 `Expanded` 锚点漂移，但**没有一步读数把它钉死**。
3. **「面板为什么会被推 83.86px」（§5.4 `#299`）** —— 这是**最该先查的一条**：
   起手干净（`动画中=false 偏移=0`）、用户只动 34px、抬手时面板已偏 83.86px。
   这正是**方案 2 声称要扣住的那一份**。现在 `nestedScroll` 挪位后，这一份**应当**开始经过我们的连接器
   ⇒ 下一份日志里 `扣判` 的出现/不出现**直接裁决**这一条。

### 6.4 我的判断：一条「结构性」的看法

把两个 bug 放一起看，**它们都是同一个结构问题的表现**：

> **半模态的内容高度与面板锚点互相引用。**
> `Expanded` 锚点 = 屏高 − 内容高（m3 `BottomSheet.kt:306`）；而内容高又依赖面板给的可用高度。
> 平时这个环是「收敛」的（内容稳定 ⇒ 锚点稳定）。但只要**内容在面板开着时异步长高**，
> 环就变成「自我推进」的 —— 一边是 `dragTarget` 漏勺导致面板永不落定（⇒ 点不动），
> 一边是内容高/最大滚动量在临界点附近翻转导致内容猛窜（⇒ 鬼畜）。

**两条修法方向**（我倾向第 1 条）：

1. **让内容不在面板开着时长高**（治本）：
   数据到位前预占高度（骨架/预留），或让 `bodyMaxHeight` 按最大内容预留，
   使 `updateAnchors` 集中在拖动之前发生。
   代价：观感上可能多一次「内容闪现」，需要设计。
2. **让 `dragTarget` 不被害**（治标但精确）：自持 `AnchoredDraggableState` 并复刻 m3 拖动层，
   在 `updateAnchors` 前后自行兜底清 `dragTarget` —— 等于 fork m3 内部实现，**维护成本最高，不推荐**。

**已被否决的路（用户明确要求保留面板拖动）**：
- ❌ `ModalBottomSheet(sheetGesturesEnabled = false)`（公开参数，`Draggable.kt:586` `if (enabled)`）
  —— 从根上让面板不再参与 Initial 段吃 DOWN，是**唯一能一次断掉整条链**且零视觉改动的开关，
  **但会同时失去拖动**。用户 2026-10-05 已明确否决。
  （留档备查：如果哪天用户改主意，这是最省事的解法。）

### 6.5 三条方法论教训（已从这两轮沉淀进技能）

1. **「没量到的字段不配写判据」** —— 方案 2 的第二个门（`contentAtBottom`）从来没有读数，
   它在判据里存在、在证据里不存在 ⇒ 一枪打空。
2. **「修法要打在『被观测到在动的那个东西』上」** —— 机理链说得通 ≠ 现象在那一环上。
   必须有一步读数**把某一环钉死**（这里就是 §3.2 那张 39:3 配对表）。
3. **「量具要能区分『没被调用』和『被调用了但值没变』」** —— 只在「值变了才记」的读数，
   遇到「整个函数没被调」会**伪装成「值一直没变」**。新钩子第一版都该带**无条件计数**。

---

## 7. 下一步（接手者该做什么）

### 第一步（必须）：验证 03:20 包的 `nestedScroll` 挪位

装 `app/build/outputs/apk/debug/app-universal-debug.apk`，按 §2 三条件复现，然后**只看这两行**：

| 读数 | 结论 | 动作 |
| --- | --- | --- |
| **`扣判 第N次进入 onPostScroll …`** | **开始出现** ⇒ 连接器进链，修法方向对 | 继续看下面那个 |
| **`扣判 …` 仍然 0 条** | 位置还没对（还有第二层原因，比如 `ModalBottomSheet` 内部又包了一层滚动） | 拉新日志，查 m3 内部结构 |
| **`扣判 下拉 expanded=true atBottom=true … 扣掉=…`** | `扣掉` 应**等于** `available.y`（真扣住） | 若 `扣掉=0` ⇒ 门没进对，查 `currentValue` / `maxValue` 读数 |

同时观察：**鬼畜与点不动是否一起消失**（若一起消失 ⇒ §3.4 的上下游推断彻底坐实）。

### 第二步：判读 202 条 `面板内容 滚动` 读数（一直没人判读）

这一列是为裁决 §3.2「内容为什么自己滚」加的，**已经有数据，但没有判读**。
重点看：
- `maxValue` 是否在 **0 与非 0 之间翻**（若翻 ⇒ `coerceIn` 夹取这条候选成立）；
- `value` 是否在**没有人滚的时候突变**（看 `滚中=false` 却有 `value` 变化 ⇒ 是程序夹的，不是人滚的）。

### 第三步：按 §6.4 方向 1 做设计（若前两步证明环还在）

**注意：这一步之前必须重新量一次**，别直接改代码 —— 本轮的教训就是「跳步改代码」。

### 判读表（照这张表分叉，不要跳步）

| 观察到 | 结论 | 下一步 |
| --- | --- | --- |
| `扣判` 出现 + 鬼畜消失 + 点不动消失 | 修法成立 | 收口：撤探针、删计数、清理取证目录、提交 |
| `扣判` 出现 + 鬼畜**仍在** | 判据生效但不够 | 看 `atBottom` 门是否为真、`扣掉` 是否等于 `available.y` |
| `扣判` 仍 0 条 | 位置还没对 | 查 `ModalBottomSheet` 内部是否还有一层滚动/`layout` 包裹 |
| 鬼畜消失 + 点不动**仍在** | 两条链部分独立 | 查 `dragTarget` 是否还有别的置上源（内容异步到达本身） |

---

## 8. 边界与纪律（接手必须知道，**不是建议**）

### 8.1 项目约定

- **回复一律中文**；UI 文案不许带 AI 味（星号 / 反引号 / 内部黑话一律不进界面）。
- **先方案再动代码**；改完**当轮回写文档**。
- **保护域**（导航层 `feature/Navigation.kt`、Reader、Source、ViewModel）要**显式授权**，
  且授权改动**单独提交**，便于真机二分。
- **agent 默认只交「编译 + 单测 + 出包」，不做设备自验**（不点屏幕、不装包）。
  本轮装包与读日志都是**用户逐次点亮的例外**，**不要外推到别的操作**。
- **不伪造内容**：数据缺失留空、UI 如实降级、新字段默认空/未知。
- 观感尺寸用现成口径（官方原值 / 应用内 token），**不造新数字**。
- UI 尺寸/颜色/字重一律走 `ui/tokens/`（不得裸 `xx.dp` / `xx.sp` / `FontWeight`）。
- **不做 `git push`**（用户从未要求）；提交按批、按是否触碰保护域拆。

### 8.2 并行会话边界（**很重要**）

- `desktop/*`、`GalleryArtistAvatarStore.kt`、`GalleryArtistFollowsStore.kt`、
  `GalleryFavoritesStore.kt`、`docs/rounds/desktop-*.md` 等是**并行会话**的改动，
  **不要 stage、不要碰**。本轮的改动**未提交**，与它们混在同一工作区里。

### 8.3 本轮涉及的文件（**改动都在，未提交**）

| 文件 | 状态 | 内容 |
| --- | --- | --- |
| `app/src/main/java/com/venera/compose/gallery/ui/GalleryInfoSheet.kt` | M | **修法（nestedScroll 挪位）+ 量具 + 看门狗**（三类混在一文件，撤除时必须分开！） |
| `app/src/main/java/com/venera/compose/gallery/ui/GallerySheetProbe.kt` | M | 探针（**整文件删**） |
| `app/src/main/java/com/venera/compose/gallery/domain/GallerySheetScroll.kt` | M | 判据（**留**） |
| `app/src/test/java/com/venera/compose/gallery/GallerySheetScrollTest.kt` | M | 单测（**留**） |

### 8.4 ⚠️ `GalleryInfoSheet.kt` 里「量具」与「修法」必须分开撤

撤除时**逐处核**（详见 §9）：
- **撤**：`probeHolder`、`LaunchedEffect(Unit) { GallerySheetProbe.install(context) }`、
  三条 `snapshotFlow`（`落定 running=` / `姿态 current=` / `目标 target=`）、
  `touchProbe("面板", probeHolder, sheetState)`、`onGloballyPositioned`、
  新增的 `snapshotFlow { Triple(value, maxValue, isScrollInProgress) }`、
  以及 `sheetScrollConnection` 里的 `postScrollCalls` 计数与 `扣判` 日志、
  `甩` 两条、`到达` 若干、`tapWitness`。
- **留**：`SHEET_SETTLE_WATCHDOG` 常量、那段看门狗 `LaunchedEffect`、`fingerDown` 与
  `Modifier.observePointerDown(...)` —— 这三样是**档 A 的修复本体**，
  与探针的耦合点只有几行 `GallerySheetProbe.log(...)`，把日志行摘掉即可独立存在。

---

## 9. 成因定死之后要撤的东西（清单）

诊断包不留档，**撤除时按这张表逐处删**：

1. `app/src/main/java/com/venera/compose/gallery/ui/GallerySheetProbe.kt` —— **整文件删**。
2. `GalleryInfoSheet.kt` —— 见 §8.4（量具撤 / 修法留）。
3. `VeneraChip.kt`：`composeTick` 与 `chip 装配` 日志块、`内` 见证、`深` 见证，
   以及为探针加的 4 个 import（`awaitEachGesture` / `awaitFirstDown` / `PointerEventPass` / `pointerInput`）。
4. ⚠️ **不要跟着撤** `components/ChromeCaptureSuppress.kt` 与 `gallery/ui/GalleryFlyIn.kt` 里那条 alpha 让位
   —— 那是 hero 转场的**修复**（同一轮里落的），有 8 颗单测 `ChromeCaptureSuppressTest` 钉着。
5. 取证目录（**待清理**）：
   - `.tmp_shot_probe/`
   - `docs/forensics/jitter-verify-2026-10-05-0300/`
   - `docs/forensics/jitter-verify-2026-10-05-0312/`
   - `docs/forensics/deadtap-2026-10-05-0234/`

---

## 10. 文件索引（按接手顺序）

| 类别 | 路径 |
| --- | --- |
| **本交接文档** | `docs/rounds/gallery-sheet-handoff-2026-10-05.md` |
| 「点不动」原取证（**详细**） | `docs/rounds/gallery-sheet-dead-tap-forensics-2026-10-05.md`（§十一 §十二 是重点） |
| 「鬼畜」原取证（**详细**） | `docs/rounds/gallery-sheet-jitter-forensics-2026-10-05.md`（§七 §八 是重点） |
| 判据本体 | `app/src/main/java/com/venera/compose/gallery/domain/GallerySheetScroll.kt` |
| 被量的 UI | `app/src/main/java/com/venera/compose/gallery/ui/GalleryInfoSheet.kt` |
| 探针（整文件删） | `app/src/main/java/com/venera/compose/gallery/ui/GallerySheetProbe.kt` |
| 单测 | `app/src/test/java/com/venera/compose/gallery/GallerySheetScrollTest.kt` |
| 日志（02:34 旧包） | `docs/forensics/deadtap-2026-10-05-0234/` |
| 日志（03:00 批次 2） | `docs/forensics/jitter-verify-2026-10-05-0300/forensics/sheet-probe.log` |
| 日志（03:12 批次 3 前） | `docs/forensics/jitter-verify-2026-10-05-0312/forensics/sheet-probe.log` |

**库真相的读法**（无 sources 时用 `javap`，别猜）—— 本轮用的是：
```
~/.gradle/caches/modules-2/files-2.1/androidx.compose.foundation/foundation-android/1.12.0/*/foundation-android-1.12.0-sources.jar
~/.gradle/caches/modules-2/files-2.1/androidx.compose.material3/material3-android/1.5.0-alpha22/*/material3-android-1.5.0-alpha22-sources.jar
```
（**两者都有 sources，解出来直接读**。）

---

## 11. 一句话总结给接手者

> **「点不动」已经查清（`dragTarget` 漏勺 + 内容异步长高竞态），且它是「自起动画」的下游；
> 「鬼畜」查到了『内容在动』这一步，但最后一环没有读数。
> 我上一轮的修法（条件扣位移）没生效，因为我把它挂在了滚动容器的内侧 —— `nestedScroll` 只向祖先注册。
> 已改成祖先位但还没验证。**接手第一件事：装包看 `扣判` 有没有出现。**」

---

## 12. 2026-10-05 后续：固定面板高度的修复

这节更新并覆盖 §7 当时提出的下一步。设备日志（09:26–09:27，PJZ110，2.0.3）确认：

- `onPostScroll` 已进入连接器，故祖先位修正生效；
- 复现期间 `maxValue=346` 稳定，没有支持「滚动上限反复翻转」的读数；
- 内容滚回 `value=0` 后出现正向剩余位移，旧的 `contentAtBottom` 判据返回 0，位移交给 M3 面板。

用户选择改走结构性约束：不让面板进入近全屏档。实现方式是：

- `rememberModalBottomSheetState(skipPartiallyExpanded = true)`：关闭与唯一展开档之间切换；
- 整个内容 Column 固定为 `screenHeight - sheetTopClearance`，展开锚点始终在屏幕顶端以下；
- 标题、操作条占固定区域，内容区填充剩余高度并独立滚动；译文或标签回填不再改变面板总高度。

`GallerySheetScroll.consumedFromDrag` 留作旧实验判据，不再声称它修复了当前鬼畜问题。安装后需由用户按原步骤复验；本轮构建/安装结果记在交接回复中。

**2026-10-05 09:43 状态**：`:app:assembleDebug` 构建成功，`app-universal-debug.apk` 已通过 ADB 安装到 PJZ110（`lastUpdateTime=2026-10-05 09:43:04`），并已启动画廊活动。新面板的真机复验待用户查看。
