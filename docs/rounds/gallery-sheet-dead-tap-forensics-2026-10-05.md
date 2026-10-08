# 「关于这张图」面板点不动 —— 取证与接力文档（2026-10-05）

> 这份是**交接件**：给接手的人/AI 一次性读全。里面严格分三层写：  
> **① 实测读数**（有时间戳、原始日志行）、**② 从库源码读到的判据**（带文件与行号）、**③ 还没量掉的候选**。  
> 凡是 ③，都不许当成结论往下走。
>
> 🔴 **最新一轮在 §十一（2026-10-05 02:30）**：量具重造 + 档 A 修法同包。
> 那一节**推翻了 §四/§七 的取数方式**（「等它出现再抓 log」作废，改成「每笔按下自报 + 自动落盘 + 死档快照」），
> 并给出了一条每个环节都有行号的机理链。**先读 §十一，再回头看前面的层。**

---

## 一、症状（用户原话，两条都在同一台真机上）

1. 「关于图片还是有可能点不动，得要退出图片，再点击关于图片可能才能点动。」
2. 2026-10-04 用户**自己推翻了我第一次的归因**并重述（原话）：
   - 「打开面版就无法点击，但是可以正常拖动」
   - 「第一次打开就不动」
   - 面板松手后「没有」微动
   - 「就是很单纯的点不动，**什么内容都点不了，长按也无反应**」

要点：**整块面板哑、拖动照常、与"先做过某个动作"无关**。这三条限定了成因的形状 —— 不是单枚 chip 的问题，是**面板这一层的输入通道**问题。

---

## 二、① 实测读数（2026-10-05 01:37–01:38，诊断包，367 行）

**日志原文已进仓**（不连手机也能读全）：`docs/forensics/gallery-sheet-dead-tap-probe-2026-10-05-0138.log`
—— 367 行、`盒子收到` 50 条、`到达` 1 条，下面引用的每一条都能在这份里搜到。
设备侧那份在 `/data/local/tmp/probe.log`；02:06 那颗（带 `动画中= / 偏移=` 的）因手机从 adb 掉线，**还没有产生新读数**。

抓取方式：设备侧落盘（**主缓冲只留约 48 秒，主机侧抓取必然丢**）  
`adb shell "logcat -v time -s GallerySheetProbe:* -f /data/local/tmp/probe.log -r 2048 -n 3"`

### 2.1 总量（本份日志）

| 标记            | 条数     | 含义                                                     |
| ------------- | ------ | ------------------------------------------------------ |
| `盒子收到`        | 50     | chip 盒子被命中了 50 次                                       |
| `到达`          | **1**  | 动作真正回调了 1 次                                            |
| `按着时重组=true`  | **0**  | 按住期间没有任何一枚 chip 重新组合                                   |
| `在盒内=false`   | 18     | 抬手落点在盒子外 —— **全是滑动**（事件数 9/12/16/30、每步移动 29–63px），不是点击 |
| `甩`           | **0**  | 面板内容的惯性滚动一次都没触发                                        |
| `姿态` / `采样开始` | 16 / 3 | 面板开关 3 次                                               |

### 2.2 唯一一次「能点」（01:38:10.533）

```
01:38:10.144 面板 #8  画=469.0 未裁=469.0 差=0.0 dy=-1.0 高=1865.0 dh=0.0
01:38:10.533 面板 触摸 down=(609,1459) 移动=0px top按下=469.0 top抬起=469.0 屏高=2376
01:38:10.533 到达 标签chip 点击 「cum」
01:38:10.537 标签「cum」 盒子收到 down=(97,47) 盒子顶=1881.0 盒子高=69.0 …
                移动=0px 已抬手=true 到Final已被消费=true down最终已被消费=true 事件数=1 按住=86ms … 在盒内=true
```

### 2.3 一次典型「按不动」（同一枚、手指静止）

```
01:37:56.764 面板 触摸 down=(587,1475) 移动=0px top按下=247.0 top抬起=247.0 屏高=2376
01:37:56.764 内 东北切蒲英（VOICEROID）(touhoku_kiritan) 收到 down已被消费=true 事件数=1 按下时scaleX=1.0 alpha=1.0 组合=2→2 按着时重组=false
01:37:56.764 标签「touhoku_kiritan」 盒子收到 down=(345,63) 盒子顶=1659.0 盒子高=69.0 …
                移动=0px 已抬手=true 到Final已被消费=false down最终已被消费=true 事件数=1 按住=524ms … 在盒内=true
```

### 2.4 死/活两档的**唯一结构性差别**（这份读数最值钱的一条）

| 档            | 面板 `画`(top) | 面板 `高`     | 底边     | 点击 |
| ------------ | ----------- | ---------- | ------ | -- |
| 活（第 3 次开面板）  | **469.0**   | **1865.0** | 2334.0 | 通  |
| 死（第 1、2、4 次） | **247.0**   | **2087.0** | 2334.0 | 全哑 |

底边两档**都是 2334**，差的只有内容高度（+222px）。而内容高度在开面板后 3 秒内跳了 4 档：

```
#0 高=1754 → #3 高=1865(dh=+111) → #14 高=2179(dh=+314) → #23 高=2096 → #24 高=2087，此后一直 2087
```

`GalleryInfoSheet.kt:282` 的内层滚动列吃了 `heightIn(max = bodyMaxHeight)`，`bodyMaxHeight = screenHeight - 140.dp`。  
**死档的高度正好贴在裁顶那一档**（外层 = 内层裁顶 + 底部那条下载栏），也就是说：**死 = 内容超过裁顶、内层真的可滚；活 = 内容没超、滚动范围为 0**。这一条是"整块哑"与"面板高度"之间目前唯一量到的相关项，**但它只是相关，不是因果 —— 见 §四 的判式**。

---

## 三、② 从库源码读到的判据（不是推测，行号可查）

- **`Clickable.kt:1111-1115`（foundation 1.12.0）**：
  ```kotlin
  if (pass == PointerEventPass.Main) {
      if (downEvent == null) {
          if (pointerEvent.isChangedToDown(requireUnconsumed = true)) {
              handleDownEvent(pointerEvent.changes[0])
  ```
  ⇒ **`down` 只要在这一层之前被吃过，clickable 就根本不开始跟踪**：不 `consume` 抬手、不回调 `onClick`、不发 `PressInteraction`。  
  这正是本 bug 的观感："看得见、点不着、长按也没反应，但拖动照常"。  
  同时也解释了 2.3 里 `down最终已被消费=true` + `抬手没人消费` + `一条到达都没有` 这一组看似矛盾的形状。
- `Clickable.kt:1139-1148`：抬手若 `changedToUp()` 且未被吃 → `up.consume()` + `handleUpEvent` → `performClick()`。  
  ⇒ **抬手被没被消费，就是"点击有没有成立"的直接指纹**（活档 = true，死档 = false，2.2/2.3 已对上）。
- `Clickable.kt:1345-1354` + `:1774-1782 getExtendedTouchPadding`：非抬手事件会按 `isOutOfBounds(bounds, touchPadding)` 判负；  
  触达被**补到 48dp 最小目标**（本机 density 2.625 ⇒ 126px）。药丸实测高 69px ⇒ 上下各扩 **28.5px**。  
  ⇒ 相邻两行/两枚的**扩展触达区会重叠**；重叠时 Main 段由"画在上面的那一枚"先吃 down。  
  也解释了日志里大量 `down` 的 local y = -1/-4/-22/70/73/79/85/93 这类"盒外"落点仍算命中。
- `ModalBottomSheet.kt:141-161`（material3 1.5.0-alpha22）：
  ```kotlin
  Box(modifier = Modifier.fillMaxSize().semantics { isTraversalGroup = true }) {
      Scrim(...)                                     // 先画 = 在下方
      BottomSheet(modifier = modifier.align(TopCenter).consumeWindowInsets(sheetWindowInsets)) // 后画 = 在上方
  }
  ```
  `SheetWindowInsets.getTop`（:246-252）= `state.anchoredDraggableState.offset.toInt()` —— **布局期**用它裁 insets，而面板挪动是**绘制期** `graphicsLayer { translationY }`。这两条不同轨，是本仓上一轮已经踩过的面（见 `memory/project-placement-rect-recomposition.md`）。
- `Scrim.kt:58-72`：Scrim 是 `Canvas(Modifier.fillMaxSize().then(Modifier.pointerInput(onClick){ detectTapGestures { onClick() } }))`，  
  **铺满整窗**；`detectTapGestures` 的 `awaitFirstDown` 默认走 **Main** 段。它在 z 序上压在面板**下面**，所以 Main 段排在 chip 之后 —— 但它确实是"能吃 down 的一整层"，**不许当已排除**。

### 3.1 目前唯一能串通全部症状的那条链（**四个环节里三个已有源码行号，只剩最后一环待读数**）

| 环节                         | 出处                                                                                                             | 内容                                                                                                                            |
| -------------------------- | -------------------------------------------------------------------------------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------- |
| ① 拖动层的"抢按下"开关              | foundation `AnchoredDraggable.kt:166` 与 `:260`                                                                 | `startDragImmediately: Boolean = state.isAnimationRunning` —— **默认值就是"动画正在跑"**，m3 的面板走的是这个默认                                  |
| ② 抢到就立刻吃 down              | foundation `Draggable.kt:812-820`（注释原话 *"If we shouldn't await touch slop, we consume the event immediately"*） | `if (pass == PointerEventPass.Initial) { if (awaitTouchSlop == No) { firstDown.consume(); state.consumedOnInitial = true } }` |
| ③ down 被吃过 ⇒ clickable 不开始 | foundation `Clickable.kt:1111-1115`                                                                            | `isChangedToDown(requireUnconsumed = true)` 为假 → 整条点击链**静默作废**                                                                |
| ④ **待量**：面板是否"永不落定"        | m3 `SheetDefaults.kt:247-248` `val isAnimationRunning`（**公开可读**）、`:266 requireOffset()`                        | 死档里若 `动画中=true` 而采样 `dy=0`（看着是静的），这条链闭合                                                                                       |

这条链能一次解释全部四条症状：**整块面板哑 / 长按也没反应 / 拖动照常（抢 down 的正是拖动本身）/ 退出大图重开就好（面板与它的锚点被重建）**。  
而 §2.4 那条"死档内容贴裁顶"的相关项也有了着落：内容开面板后 3 秒内跳 4 档高度（1754→1865→2179→2096→2087），**每一跳都会重算锚点并重发 settle 动画**；若某一跳之后 `offset` 被 clamp 到可达边界、目标却永远对不上，`isAnimationRunning` 就恒真。  
**注意：①②③ 是我读到的库事实，④ 还没量 —— 不许把这条链当已定案往下写代码。**

---

## 四、现场量具（这一版已编译进包，等读数）

文件：`app/src/main/java/com/venera/compose/gallery/ui/GallerySheetProbe.kt`（文件级私有 `consumeFlags` + object 内三颗）  
挂点：`GalleryInfoSheet.kt:286-296`（面板根）、`:1016-1018`（每一枚标签药丸）、`VeneraChip.kt:139-155 / 245-277 / 299-340`（chip 内部）

**同一次按下连读三段**（`初=` / `主=` / `终=`），三枚挂在三个深度上：

| 挂点                                              | 位置                                                                        | 报什么                                |
| ----------------------------------------------- | ------------------------------------------------------------------------- | ---------------------------------- |
| `面板 #N … 动画中=? 偏移=?`（**01:55 新加，直接量 §3.1 的 ④**） | `sample()` 每 120ms 轮询 `sheetState.isAnimationRunning` / `requireOffset()` | 面板是否"看着静、其实一直在 settle"             |
| `面板 触摸 … 按下三段 初/主/终`                            | 面板根 Column（m3 面板容器的**子**）                                                 | 面板外那一层有没有先吃                        |
| `标签「x」 盒子收到 … 按下三段`                             | chip 的 **modifier 链首**（比 clickable 更外）                                    | 到本枚之前干净不干净                         |
| `深 x down=(…) 初=? 主=? 已抬手= 抬手被消费=`              | chip 的**内容行**（比 clickable 更**深**一格，Main 段比 clickable 先跑）                  | **clickable 轮到之前 down 干不干净** ← 决定性 |

判式表（拿到日志就照这张表分叉，不要跳步）：

| 读数                                   | 结论                                            | 下一步查什么                                                                                       |
| ------------------------------------ | --------------------------------------------- | -------------------------------------------------------------------------------------------- |
| 死档 `动画中=true`（且 `dy=0`）+ `深 …初=true` | **§3.1 那条链闭合**：面板永不落定 ⇒ 拖动层在 Initial 段抢走每一笔按下 | 修法就落在"让面板落定"或"不让它长高到贴裁顶"，见 §六.1                                                              |
| 死档 `动画中=false` 但 `深 …初=true`         | 另有祖先/低 z 兄弟在 Initial 段吃 down                  | Scrim、`nestedScroll(sheetScrollConnection)`、m3 面板容器逐条量掉                                      |
| `深 …初=false 主=true`                  | 吃它的是**比 clickable 更深或 z 序更高的节点**              | ① FlowRow 里后画的相邻可点物（48dp 扩展触达重叠）；② 面板内容里某个盖在上面的浮层/整块可点行                                      |
| `深 …初=false 主=false`                 | clickable 拿到了干净的 down，是它**自己判负**              | `isOutOfBounds` 的 28.5px 扩展边界、长按分支（`按住=524ms` 那种）、`checkForCancellation`（Final 段看到别人吃了就整条取消） |

⚠️ 有一条**已经量到的反证**要连着看：**50 次按下里，每次只有一枚 chip 的见证报**（按时间戳排重后无一对同毫秒），  
所以"抢 down 的是另一枚被量的药丸"这一支基本不成立 —— 抢的人要么**没有见证**（节标题行 / 信息网格 / 底部动作条这类全宽 `clickable`），要么根本是**祖先**。  
另外死档的落点分布覆盖 `local y = -22 … 93`，**盒内正中（y=13…63）也照样哑**，所以不是"只坏在边缘"。

配合 `面板 #N 画=… 高=…` 的采样，还能定"死档 = 内容贴裁顶"这条相关项到底是不是因果。

---

## 五、已经被读数打死的三条（**别再走**）

1. **"滚动中图片反复重建 / 面板每帧重组"** —— 本份 `按着时重组=true` 出现 **0 次**；`chip 装配` 43 行都发生在**没有手指按着**的时刻（数据到位时重排标签墙）。
2. **"那枚药丸自己在动（或面板在动）"** —— 静止点击的读数里 `移动=0px 盒顶变化=0.0 高变化=0.0 宽变化=0.0 在盒内=true`。
3. **"面板位移不收敛 / 松手后还在回弹"** —— 用户 2026-10-04 15:40 直接否认（「没有」），且 60 帧 ×120ms 的采样里 `dy=0` 连续 3.5 秒。

另外两条**方法论**教训（写过记忆，仍要防）：

- `isConsumed` 在 **Initial** 段读，那时子节点还没轮到，报出来的 `false` **什么也没说**；
- **"解释力强"不等于"对"** —— 本轮我两次因为一条假设能解释很多现象就直接推进，都被下一条读数打死。要量，不要挑。

---

## 六、还没量掉的候选（③ 层，按性价比排序）

1. **面板"永不落定"→ 拖动层在 Initial 段抢走每一笔按下**（§3.1，四个环节只差 ④ 没量）。驱动它的是**内容一开面板就长高 222px**（`tagTranslations` / `artistCredits` 异步到位后标签墙重排，高度 3 秒内跳 4 档），**这是唯一与死/活同时变的东西**。两条可分别验证的修法候选：
   - 试法 A：**不让内容在面板开着时长高** —— 数据到位前先把标签墙的高度占住（骨架/预留），或把 `bodyMaxHeight`（`GalleryInfoSheet.kt:282`，= `screenHeight - 140.dp`）那档按最大内容预留；若死档消失，链闭合；
   - 试法 B：数据到位后**主动** `sheetState.expand()`（或触发一次重锚），看是否恢复可点；
   - 试法 C（对照）：`ModalBottomSheet` 底层是 `Modifier.anchoredDraggable(..., startDragImmediately = state.isAnimationRunning)`（默认）。**换掉 m3 那颗自持的 `ModalBottomSheet`、用 `draggableAnchoredSheet` 自己组一层并显式传 `startDragImmediately = false`**，若立刻全活，就实锤是这一档 —— 代价是面板自己那套 insets/落定逻辑要接管，属于保护域级别的改动，**须先授权**。
2. **48dp 扩展触达让相邻可点项互相抢 down**（§三）。药丸实测高 69px、扩展后 126px ⇒ 上下各扩 28.5px；而标签墙 `FlowRow` 的横竖间隙都是 `tokens.spacing.space2 = 4.dp`（`Spacing.kt:21`；本机 **10.5px**）⇒ **相邻两行的扩展触达重叠约 46.5px**。  
   ⚠️ 反证已在 §四：每次按下只有一枚 chip 的见证报，所以"另一枚被量的药丸抢的"基本不成立，这一条只剩"**没有见证的全宽可点行**抢的"这一支。判据仍是 `深 …主=true`。
3. **m3 的布局期 insets 与绘制期 translationY 不同轨**（`SheetWindowInsets.getTop = anchoredDraggableState.offset`），面板在 469↔247 之间换档时，`verticalScroll` + `nestedScroll(sheetScrollConnection)`（`GalleryInfoSheet.kt:208-230 / :302-303`）与 `anchoredDraggable` 谁先在哪个段吃 down。**注意本仓已有一条已修的同族**：`GallerySheetScroll` 那套"拖动放行、惯性全扣"就是为"面板盖满屏之后一直抖、还拉不下来"加的（用户 2026-10-01 报的第 3 条）—— 也就是说"贴裁顶"这一档**本来就是一条已知会出事的边**。
4. **Scrim 铺满整窗**这一事实（Main 段它排在 chip 之后，但 Initial 段**低 z 兄弟排在前面**，而 `detectTapGestures` 的分支我没逐条读全 —— **不许当已排除**）。
5. 已知**没做**的一条同族隐患：全仓 Lazy 项没有一处传 `contentType`（与本 bug 无关，但同属"回收/重排"一族，见 `memory/project-placement-rect-recomposition.md`）。

---

## 七、环境与验证口径（复现与交付都要照这个）

- 仓：`D:\venera-compose`，分支 `compose-migration`。栈：Kotlin + Compose；`:app`(Android) / `:desktop`(Compose Multiplatform) / `:engine-probe`；miuix 0.9.4-rc01 + material3 1.5.0-alpha22，foundation/ui 实际解析到 **1.12.0**；minSdk 33 / targetSdk 37；`versionName=2.0.3`；applicationId `com.github.w13630039663bit.venera.miuix`。
- 验证机：**一加 PJZ110**，serial `8bfdaeb5`，1080×2376 @420dpi（density 2.625）。**必须用裸 adb，带 `-s` 会被权限层拦**。
- 本机网络：curl 直连 Google/booru/GreasyFork 全不通，`-x http://127.0.0.1:7890` 全通（系统代理开关是关的，别拿它当判据）。
- 本轮验证口径（已跑）：`:app:compileDebugKotlin` EXIT=0；`:app:testDebugUnitTest` **116 类 / 884 颗 / 0 失败 0 错误**；`:app:assembleDebug` EXIT=0。
- ⚠️ **包体尺寸不可用来判新旧**：01:33、01:54、02:06 三颗 arm64 debug **逐字节同尺寸**（102,144,511 B），而里面明明各加了不同的新字符串。判新代码进包要**直接 grep dex**：
  ```
  cd /tmp/dexchk && unzip -o -q <apk> 'classes*.dex' && grep -a -l "按下三段" classes*.dex
  ```
  01:54 那颗核实 `按下三段 / 深 / 初= / 抬手被消费 / 在盒内` 五串都在 `classes16.dex`；
  02:06 那颗（**当前装在手机上的**）另核实 `动画中= / 偏移=` 也在 `classes16.dex`。
- 抓日志**必须设备侧落盘**（主缓冲只留约 48 秒）：
  ```
  adb shell "pkill -x logcat; logcat -c; rm -f /data/local/tmp/probe.log*"
  adb shell "logcat -v time -s GallerySheetProbe:* -f /data/local/tmp/probe.log -r 2048 -n 3"   # 挂后台
  adb shell "cat /data/local/tmp/probe.log" > probe.log
  ```
  ⚠️ 杀抓取进程要用 `pkill -x logcat`；写 `pkill -f 'logcat.*probe.log'` 会**连执行命令的那个 shell 一起杀掉**（命令行里就含这串字），表现为退出码 143、什么都没做。


### 复现动作（一次跑完能同时拿到死/活两档对照）

1. 画廊墙点进一张大图 → 点「关于这张图」；
2. **等 3 秒**（让译文/署名到位、内容长高）→ 连点 3～4 枚标签；
3. 把面板往上拖到底、松手让它停稳 → 再连点 3～4 枚；
4. 在按不动的状态下点一次节标题「展开全部」、一次底部「复制链接」（这两处是**普通 `Modifier.clickable`**，用来分"坏在 chip 那条链"还是"整块内容都收不到"）；
5. 关闭面板 → 重新打开 → 立刻点一枚（这一档通常是活的，用来当基线）。

---

## 八、纪律（接手必须知道的项目约束，不是建议）

- **回复一律中文**；UI 文案不许带 AI 味（星号/反引号/内部黑话一律不进界面，Kotlin 串里别用英文双引号）。
- 先方案再动代码；改完**当轮回写文档**（`docs/HANDOFF-2026-10-04.md` 实时同步、本轮细节在 `docs/rounds/explore-and-gallery-remediation-2026-10-04.md`）。
- **保护域**（导航层 `feature/Navigation.kt`、Reader、Source、ViewModel）要**显式授权**，且授权改动**单独提交**，便于真机二分。
- agent 默认只交「编译 + 单测 + 出包」，**不做设备自验**（不点屏幕、不装包）；本轮装包与读 logcat 是用户逐次点亮的例外，**不要外推到别的操作**。
- 降级路径宁可错慢、不可静默交错；任何一条出口都必须离开 WAITING。
- 观感尺寸用现成口径（官方原值 / 应用内 token），不造新数字。
- **不做 `git push`**（用户从未要求）；提交按批、按是否触碰保护域拆。
- `desktop/*` 与 `docs/rounds/desktop-start-domain-forensics-2026-10-04.md` 是**并行会话**的改动，**不要 stage**；手册里他们写的段落如实披露但不改。

---

## 九、成因定死之后要撤的东西（清单）

诊断包不留档，**撤除时按这张表逐处删**：

1. `app/src/main/java/com/venera/compose/gallery/ui/GallerySheetProbe.kt` —— 整文件删。
2. `GalleryInfoSheet.kt` —— **⚠️ 2026-10-05 02:30 起这个文件里混着「量具」和「修法」两类东西，撤的时候必须分开**：
   - **量具（撤）**：`probeHolder`、`LaunchedEffect(Unit) { GallerySheetProbe.install(context) }`、
     三条 `snapshotFlow`（`落定 running=` / `姿态 current=` / `目标 target=`）、
     `touchProbe("面板", probeHolder, sheetState)`、`onGloballyPositioned`；
     另有 `甩` 两条、`复制链接 / 下载 / 在浏览器中打开` 三条 `到达`、`到达 节标题「展开全部」`、
     标签 chip 点击 / `+N` chip / 长按 三条 `到达`、`tapWitness`。
     （旧版的 `sample(...)` 与 60×120ms 轮询**已删**，别照旧行号找。）
   - **修法（留）**：`SHEET_SETTLE_WATCHDOG` 常量、那段 `LaunchedEffect(tagTranslations.size, artistCredits.size)`
     看门狗、`fingerDown` 与文件末尾的 `Modifier.observePointerDown(...)`。
     这三样是**档 A 的修复本体**，不是探针 —— 除非同时决定撤回档 A，否则**不要跟着删**。
     它们与 `GallerySheetProbe` 的耦合点只有那几行 `GallerySheetProbe.log(...)`，把日志行摘掉即可独立存在。
3. `VeneraChip.kt`：`composeTick` 与 `chip 装配` 日志块（`:139-155`）、`内` 见证（`:245-277`）、`深` 见证（内容行 `modifier` 上那一整块 `.then(...)`，`:299-340`）、以及为探针加的 4 个 import（`awaitEachGesture` / `awaitFirstDown` / `PointerEventPass` / `pointerInput`）。
4. ⚠️ **不要跟着撤** `components/ChromeCaptureSuppress.kt` 与 `gallery/ui/GalleryFlyIn.kt` 里那条 alpha 让位 —— 那是 hero 转场的**修复**（同一轮里落的），有 8 颗单测 `ChromeCaptureSuppressTest` 钉着。

---

## 十、相关文件与既有记录（按接手顺序）

- 本轮方案与逐条对账：`docs/rounds/explore-and-gallery-remediation-2026-10-04.md`（「回测」那节 §一–§六）
- 交接手册（活文档）：`docs/HANDOFF-2026-10-04.md`（§17）
- 量具：`app/src/main/java/com/venera/compose/gallery/ui/GallerySheetProbe.kt`（头注里写了两条已死的假设与三条自我约束）
- 被量的那块 UI：`app/src/main/java/com/venera/compose/gallery/ui/GalleryInfoSheet.kt`、`components/venera/VeneraChip.kt`
- 记忆（同类坑，别重踩）：`project-placement-rect-recomposition`、`miuix-topbar-state-not-observable`、`feedback-diagnose-object-before-fixing`、`feedback-probe-numbers-self-audit`、`project-venera-qa-device`、`feedback-device-qa-read-only`、`reference-local-network-egress`
- 库真相的读法（无 sources 时用 javap，别猜）：`reference-gradle-cache-sources-jars`；本轮用的是
  `~/.gradle/caches/modules-2/files-2.1/androidx.compose.foundation/foundation-android/1.12.0/*/foundation-android-1.12.0-sources.jar`
  与 `androidx.compose.material3/material3-android/1.5.0-alpha22/*/material3-android-1.5.0-alpha22-sources.jar`（**两者都有 sources，解出来直接读**）。

---

## 十一、2026-10-05 02:30 本轮：量具重造 + 档 A 修法

> ⚠️ **先读这一节。它推翻了 §四 的取数方式**，并给出了一条已经把全部已知症状解释干净的机理链。
> 触发它的是一条用户约束（原话）：「**这个复现不一定能复现，这个是偶尔会这样，不是一定会这样**」
> ⇒ §七「复现动作」那一套（等它出现 → 手动抓 log）**作废**。

### 11.1 为什么前 4~5 轮「取了日志但没解决」——**量具与目标错配**

不是运气差。三支量具里，**唯一有分叉力的那一列根本不在「每次按下」那一行上**：

| 量具 | 触发方式 | 报什么 | 致命缺陷 |
| --- | --- | --- | --- |
| `sample("面板",…)`（旧） | **定时轮询 60 × 120ms**，`LaunchedEffect(Unit)` 只跑一趟 | `动画中=` `偏移=` `画/未裁/高/dh` | ① 只活在**开面板后 7.2 秒内**；② 粒度 120ms；③ **与「哪一次按下」对不上时间轴** |
| `touchProbe("面板",…)` | **每次按下** | `按下三段 初/主/终`、`移动=` | **不带 `动画中=`** |
| `tapWitness` | 每次按下 | chip 盒子读写、`按下三段` | 同上，且是**后代**节点 |

一个 7~100ms 级的偶发态，落进一个只活 7.2 秒的 120ms 采样格 —— 概率极低。**再取 10 次也一样。**
（§二那份 01:38 日志更是连 `按下三段`/`动画中=` 都没有：它们是 01:54/02:06 才进探针的。）

### 11.2 机理闭合（逐条核过 foundation 1.12.0 / material3 1.5.0-alpha22）

| # | 事实 | 出处 |
| --- | --- | --- |
| 1 | 面板拖动层 = foundation `anchoredDraggable`，调用点**没传** `startDragImmediately` ⇒ 用默认值 `state.isAnimationRunning` | m3 `BottomSheet.kt:329-334`；默认值在 foundation `AnchoredDraggable.kt:260` |
| 2 | `isAnimationRunning` ⇔ `dragTarget != null` | `AnchoredDraggable.kt:935-936` |
| 3 | `dragTarget` 是 `by mutableStateOf(null)` ⇒ **snapshot 可观察** | `:993` |
| 4 | **漏勺**：`updateAnchors` 里 `val snapSuccessful = trySnapTo(newTarget); if (!snapSuccessful) dragTarget = newTarget` —— **没有 `try/finally`**，置上之后没人负责清 | `:1012-1029`（关键 `:1024-1027`） |
| 5 | 清它**只有**一条路：带 `targetValue` 的重载里 `try { dragMutex.mutate { dragTarget = targetValue … } } finally { dragTarget = null }` | `:1197-1226`（`finally` 在 `:1217-1219`） |
| 6 | **用户拖动**走的是**不带 `targetValue`** 的重载 —— 它**既不置也不清** `dragTarget` | `:1154-1172`；`AnchoredDraggableNode.drag()` → `:410-429` |
| 7 | ⇒ 拖动**过程中**内容长高 ⇒（拖动持锁）`trySnapTo` 失败 ⇒ `dragTarget` 被置上 ⇒ 拖动结束只放锁、**不清它** ⇒ 卡死 | 4+6 |
| 8 | 拖动**结束**走 `onDragStopped → fling(…)` ⇒ 多半以带 `targetValue` 的 `animateTo` 收尾 ⇒ **那次 `finally` 把卡住的标志清掉** ⇒ 预期「拖一下再点就好了」 | `:433-445` |
| 9 | ⚠️ 但 `finally` 在 `if (anchors.hasPositionFor(targetValue))` **之内** —— 目标没有锚点时**不清** | `:1202 / :1217` |
| 10 | `MutatorMutex.canInterrupt(other) = priority >= other.priority`，**同优先级也能抢**；低优先级的**新来者**自己抛 `CancellationException`、`block` 不执行 | `MutatorMutex.kt:82 / :90-99`、文档 `:105-110` |
| 11 | `SheetState.expand()` 与用户拖动**同为 `MutatePriority.Default`** ⇒ **同优先级会互相抢** | `SheetDefaults.kt:343`；`AnchoredDraggable.kt:1155 / :1199` |
| 12 | m3 那份 `DraggableAnchorsNode` 是**纯 `LayoutModifierNode`，完全不碰指针事件**（只算锚点 + `place`）——**吃 down 的不是它** | m3 `internal/DraggableAnchors.kt:99-181` |

**合成**：`dragTarget` 被置上 → `isAnimationRunning` 恒真 → 拖动层在 **Initial 段**把每一笔 DOWN
直接 `consume()`（`Draggable.kt:812-820` 原话 "If we shouldn't await touch slop, we consume the event
immediately"）→ `Clickable.kt:1113` 的 `isChangedToDown(requireUnconsumed = true)` 判否 ⇒
**clickable 根本不开始跟踪** ⇒ 整块内容点不动、拖动照常。

**为什么是偶发**：置上它的前提是「有人正持着锁」的那一瞬恰好发生锚点变化（内容异步长高）—— 竞态。

### 11.3 本包装了什么（诊断 + 档 A 同包）

**A. 每次按下自报姿态**（`GallerySheetProbe.touchProbe` 收 `SheetState`）—— 点得动的每一笔、
点不动的每一笔**都自带答案**，不需要复现：

```
面板 触摸 down=(x,y) 移动=Npx top按下=… 屏高=… 按下三段 初=… 主=… 终=…
        下时[动画中=… 偏移=… current=… target=…] 抬时[动画中=… 偏移=… current=… target=…]
```

**B. 落盘 + 死档自动快照**——把「抓 log 的时机」这件事整个删掉：

- 每行追加到 **`/sdcard/Android/data/com.github.w13630039663bit.venera.miuix/files/forensics/sheet-probe.log`**
  （滚动截断 256KB）。开面板时会打一行 `探针 落盘=<绝对路径>`。
  取法：`adb pull /sdcard/Android/data/com.github.w13630039663bit.venera.miuix/files/forensics/`
  （**无需 `run-as`**）。如实记一笔：Android 11+ 的**系统文件管理器不能浏览 `Android/data`**，主路径是 adb。
- 抬手命中异常指纹就把内存里最近 500 行整段 dump 成 `deadtap-<时间戳>.log`（2 秒节流）。指纹两条，任一命中：
  **(a)** 面板根 down 在 Initial 段已被祖先消费（拖动层抢按下的直接指纹）；
  **(b)** chip 见证「盒子收到 down」而抬手 `终=false` 且移动 ≤12px（01:38 那份里死档 40+ 次全是这条）。

**C. 事件驱动替换 120ms 轮询**：`dragTarget` 是 `mutableStateOf`（§11.2 第 3 条）⇒ 三条 `snapshotFlow`
（`isAnimationRunning` / `currentValue` / `targetValue`）记每一次翻转，**比轮询更全、日志更少**。
`sample()` 与 `TICKS/INTERVAL_MS` 已删。

**D. 档 A：落定看门狗**（`GalleryInfoSheet.kt`，`SHEET_SETTLE_WATCHDOG` 为真机二分开关）——
内容到位后，若面板**声称自己在 `Expanded`** 却还卡着（`isAnimationRunning == true`）**且手指不在屏上**，
就重申一次落定，借 `anchoredDrag` 走完的 `finally` 清掉那个标志。
`Expanded → Expanded` 是**零距离动画**，画面不动 ⇒ 不改观感。卡在别的档时**只记日志不动作**。

⚠️ 必须带「手指不在屏上」这道门（§11.2 第 10/11 条）：在用户拖动当中重申落定会把那次拖动**取消掉**。
门由 `GalleryInfoSheet.kt` 末尾的 `Modifier.observePointerDown` 提供，**只观察、一次都不消费**。

### 11.4 读数到手后怎么判

| 观察到 | 结论 | 动作 |
| --- | --- | --- |
| 仍出现死档，且该笔 `下时[动画中=true]` | §11.2 链成立，看门狗没拦住（时序不对 / 卡在非 Expanded 档） | 扩看门狗的条件，或改走「不让内容长高」那条（§六.1 试法 A） |
| 仍出现死档，但该笔 `动画中=false` | **§11.2 链被证伪** | 转查第二候选：内层 `verticalScroll`（`AbstractScrollableNode.kt:198` = `isScrollInProgress \|\| overscroll.isInProgress`，`Scrollable.kt:812`） |
| 不再出现死档，且 `修法 看门狗 已重申` 出现过 | 修法有效 | 真实机再跑一两周，然后把探针整体撤掉（§九） |
| 不再出现死档，但 `修法 看门狗` 一次都没触发 | **分不清**「修好了」还是「这轮没撞上」 | 把 `SHEET_SETTLE_WATCHDOG` 改 `false` 再装一颗，同样的用法跑一轮 |

### 11.5 本轮回归口径（已跑）

`:app:compileDebugKotlin` EXIT=0（仅剩一条既有的 `rememberModalBottomSheetState` 弃用警告）；
`:app:testDebugUnitTest` 通过；`:app:assembleDebug` EXIT=0。
**新串已核实进包**：`classes16.dex` 里能搜到 `下时` / `抬时` / `屏高=` / `死档 已 dump` /
`修法 看门狗` / `面板 落定 running=` / `探针 落盘=` / `observePointerDown`。

---

## 十二、2026-10-05 02:34 真机复现成功：**§11.2 链成立**（决定性读数）

复现形态由用户口述确认为「**点不动 + 拖动还能动**」。落盘目录 `docs/forensics/deadtap-2026-10-05-0234/`
（12 个文件：`sheet-probe.log` 49092B + 11 份 `deadtap-*.log`，大小单调递增 = 同一场会话被连续 dump）。
设备 `PJZ110` / applicationId `com.github.w13630039663bit.venera.miuix`。

### 12.1 死活转折点（16.182 → 16.671，0.5 秒内从活变死）

```
#24 16.182 面板 触摸 down=(736,1093) … 按下三段 初=false 主=true  终=true
            下时[动画中=false 偏移=0.0      current=Expanded target=Expanded]   ← 活
#26 16.221 面板 落定 running=true 动画中=true 偏移=130.22067 current=Expanded target=Expanded
#27 16.671 面板 触摸 down=(688,217) … 按下三段 初=true  主=true  终=true
            下时[动画中=true  偏移=1187.9287 current=Expanded target=Expanded]   ← 死
            抬时[动画中=true  偏移=1512.4248 current=Expanded target=Expanded]
#28 16.672 死档 已 dump（面板 根节点 down 在 Initial 段已被祖先消费（拖动层抢按下））
```

**关键#26**：`target` 已经是 `Expanded`（目标没变），但面板又开了一段动画把 `偏移` 从 130 收回 0。
就在这段动画**还没走完**时用户又按下 → `#27` 的 `下时 动画中=true` ⇒ 拖动层在 Initial 段吃掉了 DOWN。

### 12.2 三条判据全部落在「链成立」那一格

| 判据（§11.4 表） | 本轮实测 | 落格 |
| --- | --- | --- |
| 死档笔的 `下时[动画中=…]` | **true**（#27 / #36 / #85 / #90 / #93… #206 全部 true） | 链成立 |
| 死档笔的 `current` | `Expanded`（#27 等）或 `PartiallyExpanded`（#206，卡在非 Expanded 档） | 看门狗**条件不够** |
| 拖动是否仍生效 | 是。死档期内 `偏移` 持续非 0 且**抬手还在变**（#126 73→93、#132 1.5→9.1、#202 93→516、#206 1188→1292） | 与口述「能拖」一致 |
| 死/活计数 | `初=true` 109 笔 / `初=false` 23 笔 | 死档占全场绝大部分 |

### 12.3 看门狗**为什么没拦住**（本轮唯一的新信息）

看门狗条件写的是 `isAnimationRunning && 手指不在屏上 && currentValue == Expanded`。
但本轮死档恰好卡在两种它**覆盖不到**的姿态：

1. **`current=Expanded` 但 `isAnimationRunning` 恒真**：`#20`/`#41` 两次评估时 `动画中=true`，
   下一拍 `#21`/`#42` 变成 `动画中=false` 才重申 —— 说明**重申之后 `dragTarget` 又被重新置上了**
   （内容还在异步长高，每次 `updateAnchors` 抢不到锁就又置一次）。看门狗是「事后补救」，
   而 `dragTarget` 的置上动作会**反复发生**，补救一次不解决复发。
2. **`current=PartiallyExpanded`**（`#205`：`动画中=true 偏移=1188 current=PartiallyExpanded target=Expanded`）：
   看门狗明写「卡在别的档时只记日志不动作」⇒ 这一支**按设计就不动作**。

⚠️ **诚实记一笔**：`#21`/`#42` 两次「已重申」证明档 A 修法**确实能执行**，
但**没解决复发**（`#26→#27` 之间又断言 3.4 秒 `动画中=true`）。所以档 A 作为**修法**不成立，
只能算**缓解**。真正的修法要落在「**让 `dragTarget` 不被害**」那一环，见 §12.4。

### 12.4 症结精确定位：不是「补一次」而是「别让它反复置上」

`AnchoredDraggable.kt:1024-1027` 的 `if (!trySnapTo(newTarget)) dragTarget = newTarget`
在 **`updateAnchors`** 里。`updateAnchors` 由谁触发？—— **锚点集合变化时**。
本页内容（标签 chip / 画师名）是**异步到达**的（`tagTranslations.size`、`artistCredits.size` 为 key 的
`LaunchedEffect`），每来一批就重算 anchors ⇒ 只要**用户拖动/回弹动画还没停**，就再撞一次竞态。
所以「重复置上」与「内容分批到达」是同源事件 —— 这解释了为什么 `#26` 之后仍有 109 笔死在现场。

**候选修法（按侵入性排序，均未实施，待选）：**

- **A′（最小、最推荐）**：`ModalBottomSheet(sheetGesturesEnabled = false)`（公开参数，
  `Draggable.kt:586` `if (enabled)`）—— 从根上让面板**不再参与 Initial 段吃 DOWN**。
  代价：**失去下拉收起面板的手势**（需确认页面是否依赖它；本页有明确的关闭入口则无损）。
  这是**唯一能一次性断掉整条链**且零视觉改动的开关。
- **B′**：内容全部到齐前**不渲染可点内容**（`isLoading` 门），让 `updateAnchors` 集中在拖动前发生。
  代价：观感上多一次「内容闪现」。
- **C′**：自持 `AnchoredDraggableState` 并复刻 `BottomSheet` 的拖动层，在 `updateAnchors` 前后
  自行兜底清 `dragTarget` —— 等于 fork m3 内部实现，**维护成本最高，不推荐**。

### 12.5 结论一句话

**§11.2 的机理链在真机上被完整证实**（109 笔死档全部 `下时 动画中=true`，
且「点不动但拖动有效」与链的预测一致）；档 A 看门狗能执行但**不能治复发**，
真修法应选 **A′（`sheetGesturesEnabled = false`）**，其次 B′。下一轮先验证「本页是否需要下拉收起手势」。
