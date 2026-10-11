# 批 66 · 大图那一屏的键盘缩放（2026-10-09）

## 1. 这一格补的是什么

那层从批 50 起能滚轮缩放、批 55 起念得出倍数并有「回到原大小」那一枚按钮，
而 `DesktopKeyNavigation.kt` 那条链今天**只认三枚键**（`Esc` / `←` / `→`，`:63-69`）
⇒ 键盘上没有任何一档能放大、缩小、复位。这一批把那三档接上。

## 2. 落在哪几枚键上（为什么一枚不算）

| 动作 | 认的键 |
|---|---|
| 放大一档 | `Key.Plus`、`Key.Equals`、`Key.NumPadAdd` |
| 缩小一档 | `Key.Minus`、`Key.NumPadSubtract` |
| 复位 | `Key.Zero`、`Key.NumPad0` |

⚠️ 主键盘上 `+` 与 `=` 是**同一颗键的两个码点**（Shift 与否），小键盘另有一枚 ——
只认其中一枚的形状就是"我这儿能按，用户那儿按不动"。这几枚名字不是凭印象写的：
`javap` 读 `ui-desktop-1.12.1.jar` 里 `androidx.compose.ui.input.key.Key` 的常量表核过
（`Zero` / `Plus` / `Minus` / `Equals` / `NumPad0` / `NumPadAdd` / `NumPadSubtract` 都在）。

## 3. 三处落点

- **意图那颗件**（`DesktopKeyNavigation.kt`）：新增 `enum DesktopZoomIntent` 与
  `onDesktopZoomKey(signal): DesktopZoomIntent?`。抬起的那一半交回 `null`（不消费，让系统按自己的方式走完 ——
  那颗件里原有的那条规矩照搬）；不相干的键也交回 `null`，留给原有那支。
  **为什么另起一颗而不塞进 `onDesktopOverlayKey`**：那颗的契约是"关与翻页"、签名已五枚参数，
  再塞两枚就要为每一层都递缩放态，而缩放只属于大图那一层。
- **算式那颗件**（`DesktopGalleryViewerPolicy.kt`）：`DesktopZoomStepFactor = 1.25f` +
  `desktopViewerZoomStep(current, maxScale, direction)` —— **乘**不是加（库的滚轮也是乘的，
  加法会出现"滚两轮再按两下加号回不到原地"这种两套真相）；两头夹，且 `maxScale` 自己也要
  `coerceAtLeast(1f)`：它若是 0 或负，`coerceIn(1f, 0f)` 会**直接抛在按键上**，读起来就是"这枚键坏了"。
- **接线那一格**（`VeneraDesktopApp.kt` 那条链）：缩放那一支排在原有那支之前，其余原样交下去；
  全模块 `.onKeyEvent {` 仍**恰好一处**（批 50 那条判据不放宽）。复位那一支走库那颗 `zoomState.reset()`，
  与屏上那枚按钮同一颗（`changeScale(1f)` 只改倍数，拖偏过的图回不来）。

顺带改判一句屏上的话：`KEYNAV=浮层没拿到焦点：…` 原来只列 `Esc` 与 `← / →`，
接上键之后它得连 `+ / − / 0` 一起念 —— 否则焦点没拿到时那句是**少报了三档**的假读数。

## 4. 这一格**没有**图证（写清楚，别让它混成"验过了"）

按键属可见影响（本 agent 不许碰键盘也不许碰鼠标），所以"按下去屏上动没动"今天没有帧。
两条替代证据各自能证到哪：

- 判据 ①② 离线喂键与算式 ⇒ 证的是**意图映射与数字**，不证"事件真送得进那棵组合树"；
- 判据 ③⑥ 钉接线在不在（含"零调用点"那一族） ⇒ 证的是**这一支确实挂在链上**。

"焦点没拿到时这些键全不生效"这一条由那行 `KEYNAV=` 读数看着（批 11 起就有），
所以真机那一步挂在手册 §17 第 2 项那笔用户回执上：**进大图 → 按 `+` 三下 → 屏上倍数那一格该念「放大 2.0x」左右 → 按 `0` 该回到没放大**。

## 5. 判据与扰动

新增 `DesktopViewerZoomKeysTest` 6 颗；全量 **775 判据 / 100 类 / 0 失败**（`node _qa/check_ruler_catalog.js` 对得上）。

扰动 7 笔（`_qa/b66_perturb.js` + `.sh`；R4 的锚点第一版撞上 `desktopViewerZoomScale` 里同一句 `coerceIn` ⇒ 收到含上一行的形状才唯一，这是"锚点会咬到别人"的又一次实测）：

| 笔 | 改坏成 | 红了谁 |
|---|---|---|
| R1 | 加号只认 `Key.Plus` 一枚 | ① |
| R2 | 抬起的那一半也当成一次动作 | ① |
| R3 | 步进写成加法（±0.5） | ②（另带 `DesktopUnusedInternalTest` ① —— 那枚常量当场变零调用） |
| R4 | 上限不夹 | ② |
| R5 | 复位改回 `changeScale(1f)` | ④ |
| R6 | 放大不吃活的倍数（起点写死 1f） | ⑤（**第一版跑出绿**，见下） |
| R7 | 链上不调用那枚判读 | ③ + ⑥（另带 `DesktopUnusedInternalTest` ①③ —— 那枚判读件当场变零调用） |

⚠️ **R6 第一版是跑出绿的**：那颗尺子当时只钉"链上有没有一处 `desktopViewerZoomStep(zoomState.scale, …`"，
而链上有**两支**（放大与缩小）—— 把放大那支写成 `1f` 起点，缩小那支还留着活的读数，判式就被同屏另一处喂饱。
⇒ 尺子加宽成"活的读数必须**恰好两处** + 写死的起点必须**零处**"，R6 复跑才红在 `DesktopViewerZoomKeysTest.kt:90`。
这条与本仓那条老账同族：**"某一处必须存在"的锚点会被同屏另一处合法调用喂饱 ⇒ 接线类要数出现次数**。

## 6. 数字

`desktop/src/main` 86 颗 / 行数见手册 §0；`app/` 零改动；交付包**不重打**（本批改的是键盘面，
屏上像素与批 63 那份一致 —— 但这一句要留个尾巴：真机按键验过之前，"屏上无变化"是**推**出来的而不是量出来的）。
