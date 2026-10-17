# 「关于这张图」面板下拉鬼畜（帧级上下跳）—— 取证与修复

> ⚠️ **2026-10-17 仓库卫生改判**：本文里所有 `docs/forensics/…` 路径的日志原文**已撤出仓库** —— 那是本机 logcat 取证原文，含成人站标签原文（`1girl`/`1boy` 等 84 行）与本机进程号，按本仓纪律不入库。镜像在本机 `_qa/forensics-mirror-2026-10-17/forensics/`（78 颗 / 3,563,150 B，与撤出前逐字节相同）；本文引用的行号、`grep -c` 命令与读数**都在镜像上复现得了**，把路径前缀换成镜像即可。

> 2026-10-05 建。与 `gallery-sheet-dead-tap-forensics-2026-10-05.md` 是**同一张面板的两个症状**，
> 但**成因不同**（那边是 `dragTarget` 漏勺导致点不动；这边是锚点自我引用环导致帧级抖动）。
> 不要把两边的修法互相套用。

---

## 一、用户口述的准确形态（2026-10-05 02:45）

> 「在关于卡片**全屏展示**，并且把**标签展示完全**（比如 50 个）的时候，**往上拉到底**后，
> 如果不拉上面的把手下拉、**直接在内容区下拉**，面板就会**快速鬼畜上下跳，间隔可能不到几帧**。」

追问确认（同轮）：

| 问 | 答 | 它排掉/坐实了什么 |
| --- | --- | --- |
| 鬼畜时面板档位 | **已顶格（全展）** | `current == Expanded` ⇒ 内容区已用满 `bodyMaxHeight` |
| 「上拉到底」指 | **到底，不能再下滑那种** | 内容 `verticalScroll` 已到 `maxValue` ⇒ 内容区**吃不下任何下拉位移** |
| 下拉后面板最终 | **滑动距离够就下来、不够就下不来** | 不是"完全卡死"，是**锚点拉扯**：位移没跨过阈值就回弹 |

**三条合起来 = 环的完整触发条件**：面板 Expanded + 内容滚到底 + 手指在内容区下拉。
缺任一条都不触发（这解释了为什么它「偶尔」—— 其实要「内容比屏高 + 面板顶格 + 滚到底」三件事同时成立）。

---

## 二、机理链（每一环都有源码行号）

### 2.1 m3 自己在内容之上挂了一道嵌套滚动

`BottomSheet.kt:275-292` —— `Surface` 上挂了
`ConsumeSwipeWithinBottomSheetBoundsNestedScrollConnection`（仅当 `gesturesEnabled`）。

它的实现（`SheetDefaults.kt:635-679`）：

| 钩子 | 条件 | 行为 | 行号 |
| --- | --- | --- | --- |
| `onPreScroll` | `delta < 0`（**上滑**）+ UserInput | 面板先吃 | `:643-644` |
| `onPostScroll` | UserInput | 面板吃掉内容区**吃剩的全部** | `:655-656` |
| `onPreFling` | `toFling < 0` 且 `currentOffset > minAnchor` | 面板 fling | `:666-667` |
| `onPostFling` | 无条件 | 残余速度给面板 | `:677` |

**`onPostScroll` 的 `:656` 就是入口**：

```kotlin
sheetState.anchoredDraggableState.dispatchRawDelta(available.toFloat())
```

内容区滚到底、你继续下拉 ⇒ `available.y > 0` 整份交给面板 ⇒ 面板被**逐帧**推动。

### 2.2 面板被推动会触发锚点重算，而锚点依赖内容高

`BottomSheet.kt:293-308`：

```kotlin
.draggableAnchors(state.anchoredDraggableState, Orientation.Vertical) {
    val fullHeight = constraints.maxHeight.toFloat()
    val sheetHeight = sheetSize.height.toFloat()        // ← 内容量出来的高
    val newAnchors = DraggableAnchors {
        Hidden at fullHeight
        PartiallyExpanded at calculatePartiallyExpandedOffset(...)
        if (sheetHeight != 0f) Expanded at max(0f, fullHeight - sheetHeight)   // ← 锚点 = 屏高 − 内容高
    }
    ...
}
```

`Expanded` 锚点 = `fullHeight − sheetHeight`。**只要 `sheetHeight` 变一次，锚点就变一次**，
`updateAnchors` 就被触发一次。

### 2.3 环闭合

```
你在内容区下拉（内容已到底，吃不下）
        ↓
onPostScroll dispatchRawDelta(available)          SheetDefaults.kt:656
        ↓
面板 offset 变
        ↓
draggableAnchors 重算挡被触发                      BottomSheet.kt:293
        ↓
Expanded 锚点 = fullHeight − sheetHeight 重算      BottomSheet.kt:306
        ↓
updateAnchors ⇒ (同 2.2 那条) dragTarget 被反复置上/清
        ↓
面板姿态变 ⇒ 你在屏上看到上下跳 ─────────────┐
        ↑                                    │
        └────────────────────────────────────┘
```

### 2.5 ★ 两个 bug 是同一条链的两个出口（2026-10-05 02:34 读数支持）

死档（「点不动」）现场按档位 / 动画态统计（`deadtap-...-0234/sheet-probe.log`）：

| 维度 | 分布 |
| --- | --- |
| 面板档位 | **Expanded 153 笔** : PartiallyExpanded 3 笔 |
| `isAnimationRunning` | **true 156 笔 : false 0 笔** |

`Expanded + dragTarget 残留` 恰恰是**鬼畜那条环的产物**（面板被逐帧推 ⇒ 锚点反复重算 ⇒
`dragTarget` 反复置上 ⇒ 残留）。⇒ **方案 2 扣住位移 ⇒ 面板不再被逐帧推 ⇒ 锚点不重算
⇒ `dragTarget` 不再被反复置 ⇒ 两个症状应当一起消失。**

⚠️ **这条是推断，不是已证结论**（样本是同一场会话，且现场本身就在抖）。
判据：装上带方案 2 的包后，**若「点不动」也不再出现** ⇒ 推断成立；
**若鬼畜好了但「点不动」照旧** ⇒ 两条链**部分独立**（`dragTarget` 还有别的置上源，比如内容
异步到达本身），那时才需要再单独动「点不动」那条（候选：`sheetGesturesEnabled = false`，
但会连带失去拖动，用户 2026-10-05 已明确**要保留拖动** ⇒ 实际只剩「把内容到达与拖动的时序错开」
这一条路，成本高）。

**「间隔不到几帧」的来源**：`dispatchRawDelta` 是**逐帧**调用的（每一帧的 scroll 事件一次），
锚点重算也就逐帧发生 ⇒ 帧间来回。

### 2.4 为什么上一版修法没断掉它

`GallerySheetScroll.consumedFromDrag` 现在**恒 0（全放行）**，理由写在判据头注：
「拖动必须放行，否则下滑关闭封死」。

**但这条理由在这种情况下是错的**：这里内容区**已经滚到底**，这份下拉位移**本来就不该是「滚内容」**
（内容没得滚了），m3 的设计是把它当「收起面板」。

而我们的内层连接器在**内层**放行 ⇒ 位移照样流到**外层**的 m3 连接器
⇒ `:656` 照样 `dispatchRawDelta`。**两道嵌套滚动是串联的，内层放行拦不住外层。**

> ⚠️ 另需澄清：`.heightIn(max = bodyMaxHeight)`（`GalleryInfoSheet.kt:393`）**没有断开环**，
> 尽管 `Spacing.kt:398-401` 的注释声称「上限一旦是屏高减常量，环断开」。
> 实测证明环仍在 ⇒ 该注释的判断**过于乐观**：
> `heightIn(max)` 只是**约束上界**，内容实际量出的高在「面板位移过程中」仍可能因
> `windowInsetsPadding`（`BottomSheet.kt:353`）等外部约束变化而变。

---

## 三、候选修法（按侵入性排序）

### 方案 1（❌ 已否决）：把 `heightIn(max=)` 换成 `height(固定)`
理由：内容不足时会被撑出空白；且**不能证明**它能断开环（环的另一头在 m3 的锚点计算里，
不在我们可控的高度约束上）。

### 方案 2（✅ 推荐）：内层连接器按「面板档位 + 内容位置」条件扣位移

把 `consumedFromDrag` 从「恒 0」改成带条件：

```
面板 current == Expanded && 内容已滚到底 ⇒ 扣住（不放给面板）
否则 ⇒ 放行（保持「下拉关面板」的手势）
```

**为什么这样能断开环**：环的入口是 `:656` 的 `dispatchRawDelta`。
我们在**更内层**把它吃掉（`consumedFromDrag` 返回全部），外层 m3 连接器拿到的
`available` 就是 0 ⇒ `dispatchRawDelta(0)` ⇒ 面板不动 ⇒ 锚点不重算 ⇒ 环断。

**为什么保住「下拉关面板」**：
- 面板**不是** Expanded（半开档）⇒ 放行 ⇒ 下拉照常收起；
- 面板 Expanded **但内容没到底**（还能往上滚）⇒ 放行 ⇒ 下拉先滚内容（符合直觉）；
- 面板 Expanded **且内容到底** ⇒ 扣住 —— 此时用户想滚内容已经无内容可滚，
  想关面板应该**用把手**（用户原话："不拉上面的把手下拉"= 他知道正确姿势是拉把手）。

⚠️ **这一条要跟用户确认**：顶格档时「内容区下拉关面板」会被禁用（只能拉把手/点遮罩）。

**用户 2026-10-05 02:55 已拍板**：选**方案 2**（接受这条代价），**并且明确要保留面板拖动** ——
所以「一刀切关掉 `sheetGesturesEnabled`」这条路**被否决**（它会同时关掉拖动）。
同时核清一件事：m3 默认画了一条把手（`ModalBottomSheet.android.kt:107`
`dragHandle = { BottomSheetDefaults.DragHandle() }`，本页没传该参 ⇒ 用的是默认那条），
用户说的「顶部小横条」就是它；拖把手走 `Surface` 上那颗 `anchoredDraggable`（`BottomSheet.kt:329`），
**不经过内容区的嵌套滚动** ⇒ 天然不触发这个环。这解释了「拉把手没事、拉内容区就鬼畜」。

### 方案 3（备选）：条件挂载 `nestedScroll`
等价于方案 2，但用 `Modifier.then(if (条件) Modifier else Modifier.nestedScroll(...))` 实现。
**更外科手术但更贵**（每次档位变都要重挂 modifier，有重组成本），不推荐。

### 方案 4（❌ 用户已否决）：`sheetGesturesEnabled = false`
`BottomSheet.kt:281/332/376` 三处同时关掉（嵌套滚动连接器 / `anchoredDraggable` / 无障碍语义）。
**能同时断掉两个 bug 的入口**（`:281` 关掉 ⇒ m3 的连接器没了 ⇒ `dispatchRawDelta` 消失），
但**代价是面板彻底不能拖** —— 用户明确要保留，故否决。留档备查。

---

## 四、量具（若方案 2 上路，需先补）

当前 `touchProbe` **每次按下只报一次** `top按下/top抬起` —— **抓不到帧级现象**。
鬼畜是逐帧的，量具必须**逐帧采样**面板 top。

**建议量具形态**（照 `android-intermittent-ui-bug-instrumentation` 技能的教训：

量具触发方式必须与判据落点对齐）：

```
Modifier.onGloballyPositioned { coord ->
    val top = coord.boundsInWindow().top
    if (top != lastTop) {           // 只在变化时记，避免刷屏
        log("帧 top=$top Δ=${top - lastTop} 帧号=$frame")
        lastTop = top
    }
}
```

配一个**自动 dump 指纹**：单帧 `|Δtop| > 3px` 且**连续 3 帧方向翻转** ⇒ 判定抖动，dump 环形缓冲。
（连续 3 帧方向翻转 = 「上下跳」的直接指纹；正常滚动是单向的，不会翻转。）

---

## 五、验证口径

- 单测：`GallerySheetScroll` 的条件版要补用例（三档 × 内容位置），先红后绿。
  ⚠️ 若判据签名引入 `Dp`／Compose 类型，**只能走 `gradle :app:testDebugUnitTest`**
  （见 MEMORY：`_probe/l0/run-judgment-tests.sh` 的 classpath 进不去 Compose 依赖）。
- 编译：`:app:compileDebugKotlin` + `:app:assembleDebug`。
- 真机（用户）：照 §一 三条件复现，看是否还跳 + 拉把手是否能关。

---

## 六、已实施（2026-10-05 03:00）

### 6.1 判据（`gallery/domain/GallerySheetScroll.kt`）

`consumedFromDrag` 由「恒 0」改为三参数纯函数：

```kotlin
fun consumedFromDrag(availableY: Float, panelExpanded: Boolean, contentAtBottom: Boolean): Float =
    if (panelExpanded && contentAtBottom) availableY else 0f
```

**为什么用三个平铺参数而不是 `Dp` / `ScrollState`**：判据要能被 §五 那条单测路径直接跑；
`ScrollState` 是 Compose 类型，一旦进签名，`_probe/l0/run-judgment-tests.sh` 的 classpath 就进不去。

### 6.2 调用点（`gallery/ui/GalleryInfoSheet.kt`）

- `bodyScrollState = rememberScrollState()` **提到 `sheetScrollConnection` 之前**（原来内联在
  `Modifier.verticalScroll(rememberScrollState())` 里，判据读不到）；
- `rememberScrollState()` 那一处改成复用 `bodyScrollState`；
- `onPostScroll` 的拖动分支改成先算两个门再调新签名。**两个门都在事件发生的那一刻读**、不缓存：
  用户在拖的过程中「面板档位」与「内容到底与否」都可能翻转，缓存一次就会判错。

### 6.3 单测（`test/.../GallerySheetScrollTest.kt`）

原来 1 条 `拖拽那一份全部放行给面板` 拆成 3 条（照三档写）：
`面板没顶格时…放行` / `面板顶格但内容还能滚时仍然放行` / `面板顶格且内容已到底时整份扣住`。
先红后绿已验证（改判据签名后旧用例编译不过 ⇒ 强制我改，这是设计意图）。

### 6.4 与「点不动」的关系（**未验证的推断**）

见 §2.5。**本轮没有为「点不动」单独落修法** —— 理由是：用户要保留拖动 ⇒ 方案 4 被否决，
而剩下的干净外部开关一个都没有（`BottomSheet` 的 `startDragImmediately` 是内部写死的，
`ModalBottomSheet` 公开参数里没有它）。所以**先让方案 2 顺带治**，读数说话。

`GalleryInfoSheet.kt` 里既有的 `SHEET_SETTLE_WATCHDOG`（档 A 看门狗）**保留**：
它在真机上已证「能执行但治不了复发」（见 dead-tap 文档 §12.3），
现在当**缓解 + 观测点**用（日志里的 `修法 看门狗 已重申` 能告诉我们它是否又撞上了）。

### 6.5 装机后判读表

| 观察到 | 结论 | 下一步 |
| --- | --- | --- |
| 鬼畜消失 **且** 「点不动」也消失 | §2.5 推断成立（同一条链两个出口） | 收口：删看门狗与探针 |
| 鬼畜消失 **但**「点不动」照旧 | 两条链**部分独立**（`dragTarget` 另有置上源） | 单独处理：查内容异步到达与拖动的时序 |
| 鬼畜**照旧** | 方案 2 没生效 | 先看日志里 `扣掉=` 是否为 0（若为 0 ⇒ 门没进对，查 `currentValue` / `maxValue` 读数） |

---

## 七、★ 装机后读数：**方案 2 被证伪**（2026-10-05 03:1x）

用户反馈：「**点不动 应该修好了我复现不出来了，但是鬼畜还是有**」。
⇒ §6.5 判读表落到**第三行**（鬼畜照旧）。拉取新包日志
（`docs/forensics/jitter-verify-2026-10-05-0300/forensics/sheet-probe.log`，160483 B，含 02:34~03:00，其中 **02:58:15 起为新包段**）后，结论比"没生效"更硬：

### 7.1 决定性读数：**面板没在动，是内容在动**

新包段 `02:58~03:00` 共 434 条事件。对 110 条 `盒子收到`（chip 见证）做配对分析：

| 判定 | 条数 | 依据 |
| --- | --- | --- |
| **内容自己滚**（面板 top 没变） | **39** | 该笔的 `面板 触摸` 里 `top按下 == top抬起`（差 < 1px） |
| 面板在动 | 3 | `|盒顶变化| ≈ |top抬起 − top按下|`（差 < 30px） |
| 混合 / 孤例 | 12 | 其余 |

**46/110 条 chip 见证里 `高变化=0.0` 且 `宽变化=0.0`** —— 内容**形状没变**，
只是整块在窗口里**平移**（`盒顶变化` 从 **−1799 到 +1211**）。
而同一刻的**面板 top 是稳定的 247**（顶格那条边）且 `偏移=0.0`。

> **平移量只能来自两处**：面板 top（已排除）或内容区 `scrollState.value`。
> ⇒ **真正在剧烈运动的，是 `bodyScrollState`**，不是面板。
> 用户看到的"面板上下跳"，**视觉上是面板里的内容在猛窜**。

⚠️ **撤回一条早前的错读**：本轮中途曾把 `盒顶变化` 直接当成"内容滚动量"、
并据此推"位移被放大 5.6 倍"。**那条不成立** —— `盒顶` 取自 `boundsInWindow()`，
**完全被裁剪时返回 `Rect.Zero`**（`GallerySheetProbe.kt:370-377` 头注已写明）。
日志里确有一笔 `02:43:23.052 #293 抬手时盒顶=0.0`（chip 被裁没），
说明这个读数**在边缘处会塌**。结论"内容在动"仍站得住（面板 top 等值那一列是独立证据），
但**不能**用它的数值去算倍数。

### 7.2 新包段仍有 17 次「面板自起动画」，但**目标没变**

`current == target == Expanded` 却 `落定 running=true` 共 **17 次**，偏移
`[43.8, 92.9, 93.5, 93.8, 102.7, 105.4, 108.3, 112.4, 118.3, 130.2, 130.5, 135.1, 136.2, 144.3, 174.0, 174.4, 203.0]`。

**两个特征都很关键**：

1. **没有任何一次跑到 0 或 1188**（那两个才是真锚点）—— 说明**动画是往一个"浮动目标"跑的**，
   而浮动的只能是 `Expanded` 锚点 = `fullHeight − sheetHeight`（§2.2）。
2. **值连续散布在 43~203，没有整齐常量特征** —— 排除了"某个固定的 offset 补偿"。

### 7.3 最干净的一次循环（`02:59:58~03:00:00`，无干扰）

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
⇒ 用户**在内容区滑了一下**（65px），**面板就被推走了 135px**，`current` 掉到 `PartiallyExpanded`，
**下一笔触摸立刻变成 `初=true`（点不动）**，看门狗「已重申」救回，**然后 #310/#311 的循环再来一遍**。

**这个循环就是鬼畜的本体**，而且它把两个 bug 缝在了同一条时间线上（与 §2.5 的推断方向一致，但机制不同）。

### 7.4 方案 2 为什么没治住（两条都成立）

**(a) 门可能压根没进去。** `consumedFromDrag` 要 `panelExpanded && contentAtBottom` 同时为真。
但 `#310` 那一刻 `current=Expanded`（第一个门过），
**第二个门 `bodyScrollState.maxValue > 0 && value >= maxValue` 没有任何读数能证明它当时为真** ——
上一轮的量具**完全没有量内容区滚动状态**（`grep scrollState sheet-probe.log` = **0 行**）。
**在没有读数的字段上写判据，等于把结论建立在推理上**（本仓已有同类教训）。

**(b) 即便门进去了，也打错了目标。** §七-7.1 已证：**面板 top 稳定、内容在动**。
方案 2 扣的是 `onPostScroll` 里**送给面板**的那一份位移 —— 而现象里**面板根本没收到位移**。
⇒ **扣一个不存在的量**，自然看不出任何变化。

### 7.5 下一步：先把「内容为什么自己滚」量出来（量具已补）

新包段里 `02:58:56.347 #136` 的配对是最干净的形态证据：

```
#136 面板 触摸 down=(696,1477) 移动=70px top按下=247.0 top抬起=247.0 下[偏移=0.0] 抬[偏移=0.0]
#137 标签「ahegao」 盒顶变化=-392.0 高变化=0.0 宽变化=0.0
```

**手指只动了 70px，chip 却挪了 392px，且面板纹丝不动** ⇒ `scrollState.value` 被**非手指来源**改了。

**最强候选（待读数裁决）**：`ScrollState` 的 `value` setter 里有 `coerceIn(0, maxValue)` ——
**`maxValue` 一变小，`value` 就被强制拉回**，屏上就是"内容猛地一跳"。
而 `maxValue = max(0, 内容自然高 − 视口高)`，**视口高** 又由 `heightIn(max = bodyMaxHeight)` 与
**内容自然高**共同决定 ⇒ **内容自然高在 `bodyMaxHeight` 附近游走时，`maxValue` 会在 0 与非 0 之间翻**。

**已补的量具**（本轮改动，只为取证、不改行为）：
`GallerySheetProbe.onBodyScroll(what, value, maxValue, inProgress)` +
`GalleryInfoSheet.kt` 里一条 `snapshotFlow { Triple(value, maxValue, isScrollInProgress) }.distinctUntilChanged()`
（事件驱动，不轮询 —— 与「点不动」那条教训一致）。
⇒ 下一份日志里 `面板内容 滚动 value=… max=… 滚中=…` 这一行能**直接裁决** 7.5 的候选：
`maxValue` 是否在跳、`value` 是否被夹、`滚中` 是否 false（= 没人滚，是程序夹的）。

### 7.6 ★★ 两个 bug 的因果链钉死：「点不动」是「自起动画」的**下游**

新包段 5 次「点不动」（`初=true`）**全部紧跟在一笔「Expanded 自起动画」之后**，无一例外：

| 自起动画 | 偏移 | → | 点不动（约 200ms 后） | 下时姿态 |
| --- | --- | --- | --- | --- |
| 02:58:22.584 | 174.03 | → | 02:58:23.246 | `动画中=true 偏移=1188 PartiallyExpanded` |
| 02:59:09.008 | 203.03 | → | 02:59:09.757 | 同上 |
| 02:59:56.989 | 144.29 | → | 02:59:58.108 | 同上 |
| 02:59:59.302 | 135.11 | → | 03:00:00.080 | 同上 |
| 03:00:08.882 | 108.31 | → | 03:00:09.857 | 同上 |

**机制**：自起动画把面板从 Expanded 推离（跑到 108~203），途中 `current` 掉到 `PartiallyExpanded`
⇒ `dragTarget` 残留 ⇒ `isAnimationRunning == true` ⇒ 下一笔 DOWN 在 Initial 段被拖动层吃掉
⇒ **点不动**。**「点不动」不是独立故障，是「自起动画」的产物。**

**新旧包对比（同一台机、同一份面板）**：

| 指标 | 旧包（02:34~02:57） | 新包（02:58~03:00） | 说明 |
| --- | --- | --- | --- |
| 触摸 `初=true`（点不动） | **103 / 150 = 68.7%** | **5 / 106 = 4.7%** | **↓↓↓ 与用户"复现不出来"吻合** |
| `Expanded` 自起动画 | 11 | 6 | 降但**未消** |
| 死档 dump | 20 | 6 | 随点不动一起降 |

⇒ **两个数一起降**：方案 2 / 看门狗压住了「自起动画」的发生率，下游的「点不动」跟着暴跌。
⇒ **只要根治「自起动画」，「点不动」会一并消失** —— 不必为它单独立修法（这与 §6.4 的处置一致，
但理由从"顺带"变成"它就是上游")。

### 7.7 ★★★ 自起动画的真正入口：**手指在内容区的位移被整份转成了面板位移**

`02:59:56.304 #299` 那一笔是本轮**最干净的单点证据**（`动画中=false` 起手，无看门狗干扰）：

```
02:59:56.304 #299 面板 触摸 down=(801,1224) 移动=34px
        下时[动画中=false 偏移=0.0 current=Expanded target=Expanded]     ← 起手干净、面板在 Expanded、偏移 0
        抬时[动画中=false 偏移=83.86 current=Expanded target=Expanded]   ← 抬手时偏移已 83.86！
02:59:56.953 #300 面板 触摸 down=(829,1175) … 下时[偏移=0.0] 抬时[偏移=0.0]   ← 又回 0
02:59:56.989 #301 ★自起动画 偏移=144.29 current=Expanded target=Expanded
02:59:57.228 #302 姿态 current=PartiallyExpanded target=Expanded
```

**`#299` 的 `抬时[偏移=83.86]`（起手 0.0）** ⇒ 用户手指在内容区动了 34px，**面板被推动 83.86px**。
**这正是方案 2 声称要扣住的那一份位移**（内容到底 + Expanded + 内容区手势）。

⇒ **方案 2 该扣而没扣**。为什么没扣，两种可能，**都必须靠新量具裁决**：

| 可能 | 新量具里长什么样 |
| --- | --- |
| **(a) `contentAtBottom` 为 false**（`maxValue` 门没进） | `扣判 … atBottom=false max=0` 或 `value<max` |
| **(b) 这一份位移**不走 `onPostScroll` 的拖动分支 | `扣判` 行**完全不出现**（说明位移没经过我们的连接器） |

> **(b) 是一条此前没考虑过的路径**：`dy总` 的符号能分辨——若用户是**上滑**（`dy总<0`），
> 那么 `m3` 的 `onPreScroll`（`SheetDefaults.kt:643-644`，`delta<0` 时面板先吃）会在
> **我们的 `onPostScroll` 之前**就把位移拿走 —— **我们根本没有机会扣它**。
> 这也解释为什么"扣了看不见效果"：**它在我们之前就被拿走了**。
> ⇒ 新量具里 `dy总` 与 `扣判` 两者对照即可判定。

### 7.8 这一轮的教训（已同期写入技能）

1. **「没量到的字段不配写判据」** —— 方案 2 的第二个门（`contentAtBottom`）**从来没有读数**，
   它在判据里存在、在证据里不存在。凡是要进判据的状态，**量具必须先能读到它**。
2. **修法要打在"被观测到在动的那个东西"上** —— 现象里动的是内容 / 面板被推 83.86px，
   而判据的两个门里有一个从未被观测。**"机理链说得通"不等于"现象在那一环上"**；
   必须有一步读数**把某一环钉死**（这里就是 7.1 那张 39:3 的配对表）。
3. **上下游要分清** —— 「点不动」与「鬼畜」被当成两个平级 bug 追了一轮半，
   实际是**上游（自起动画）→ 下游（dragTarget 残留 → 点不动）**。
   旧包 103:11 的比例早就该提示这件事（下游数远大于上游数，说明一个上游事件会产多个下游事件）。

---

## 八、★★★ 根因找到了：`nestedScroll` 挂错侧 —— 判据**从未被调用过**（2026-10-05 03:12）

用户第二轮反馈「**已经复现，另外按钮又点不动了**」后，拉新一批日志
（`docs/forensics/jitter-verify-2026-10-05-0312/forensics/sheet-probe.log`，220006 B，
新包段自 **03:11:19**）。补的三列量具**全部命中**，而结果直接指向一个此前完全没被考虑的层面：

| 新量具 | 命中 |
| --- | --- |
| `面板内容 滚动 value=… max=… 滚中=…` | **202** 条 |
| `触摸 … dy总=±N` | **74** 条 |
| **`扣判`（`onPostScroll` 的判据读数）** | **0 条** |

**`扣判` 0 条**在上一版里是「组合没变」和「一次都没被调」两种可能混在一起 ——
本轮加的**无条件调用计数**把它分开了：**这个连接器根本没进过嵌套滚动链。**

### 8.1 为什么：`Modifier.nestedScroll` 是**向祖先注册**的

两份官方源码（compose-ui 1.12.0）说得非常直白：

- `NestedScrollNode.kt:60-61` —— `parentNestedScrollNode` = `findNearestAncestor()`，**只向上找**；
- `NestedScrollModifier.kt:54-59` —— `onPostScroll` 的原文注释是
  *"the dispatching (scrolling) descendant made their consumption and **notifies ancestors**"*。

**⇒ 嵌套滚动事件是从「正在滚动的后代」向**祖先**传播的。**

而上一版的写法是：

```kotlin
.heightIn(max = bodyMaxHeight)
.verticalScroll(bodyScrollState)        // ← 滚动容器（"dispatching descendant"）
.nestedScroll(sheetScrollConnection)    // ← 挂在它的**内侧**
```

`Modifier.a.b` 里 `b` 在内层。所以我们的连接器挂在滚动容器的**后代侧** ——
**既不是它的祖先、也不在"后代的祖先链"上** ⇒ `onPostScroll` **一次都不会被调用**。

> **这解释了此前所有"改了没效果"**：判据本体、单测、dex 里新字符串全都在，
> 唯独这份位移**不会经过那段代码**。屏上自然一点变化都没有。

### 8.2 修法：把 `nestedScroll` 挪到 `verticalScroll` **之前**（祖先位）

```kotlin
.heightIn(max = bodyMaxHeight)
.nestedScroll(sheetScrollConnection)    // ← 修：祖先位
.verticalScroll(bodyScrollState)
```

**挪位后的语义正是我们要的**：`onPostScroll` 是「自己先、父后」（`NestedScrollNode.kt:93-105`），
我们在外 ⇒ `verticalScroll` 是**子**、我们是**父** ⇒ **它先滚，我们拿到它滚不掉的那一份**
（= 内容到底之后多余的位移）。这正是 `consumedFromDrag` 要处理的对象。

**已有的两道门（`panelExpanded && contentAtBottom`）语义不变**，单测不用改。

### 8.3 旁证：两个「此前解释不通」的读数现在都有解了

| 读数 | 旧解释 | 真相 |
| --- | --- | --- |
| `扣判` 0 条 | "组合没变" | **判据从未被调用**（8.1） |
| `#313 下拉 28px → 面板掉 278px` | 位移被放大 10 倍 | 面板 `初=true`（拖动层已抢按下）+ `dragTarget` 残留 ⇒ 面板自己跑；**不是我们的位移**（§7.3 同源） |

另外新包里 `maxValue` 的分布（`max=2276` 169 次 / `max=0` 31 次）证实：
**内容确实是可滚的常态**（2276），`max=0` 集中在面板掉档后的瞬间 ——
即 §7.5 那个候选（`maxValue` 在 0/非 0 间翻）**方向对但只是副产物**，
主因是 8.1 这条「判据不在链上」。

### 8.4 本轮教训（已同步进技能）

1. **`Modifier.nestedScroll` 只能挂在「滚动容器的祖先侧」** —— 挂在滚动容器**内侧**时
   它**静默失效**：不报错、不警告，`onPostScroll` 一次都不调。
   排查口诀：**「我这个连接器，是滚动容器的祖先吗？」** 不是 ⇒ 事件永远不会来。
2. **量具要能区分「没被调用」和「被调用了但值没变」** ——
   只在「值变了才记」的读数，遇到"整个函数没被调"会**伪装成"值一直没变"**。
   凡是新加的钩子，第一版都该带一条**无条件计数**（哪怕只是首次 + 每 N 次），跑一轮再削。



