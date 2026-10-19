# 画廊转场动画审计（2026-10-19）

**范围**：只审「画廊卡片 → 作品详情」「作品详情 → 大图播放器」「返回」这三条转场。
不是整仓审计 —— 四类打分只覆盖这条链路涉及的文件（`PixivWorkScreen.kt`、`GalleryPostScreen.kt`、
`GalleryFlyIn.kt`、`GalleryExitExpansion.kt`、`ChromeCaptureSuppress.kt`）。

**结论一句话**：三条转场各自只做了一半，缺的是同一件事 —— **新一屏在飞行期间以完整形态出现了**；
再叠加一处键不一致，返回程干脆没有动画。用户说的「问题很大」成立。

---

## 一、取证方法

| 项 | 值 |
|---|---|
| 设备 | PJZ110，1080×2376，density 420 |
| 动画时长 | `settings put global animator_duration_scale 3`（3×，验收后已恢复 1×） |
| 录制 | `screenrecord --bit-rate 40000000`，运动段实测约 120fps |
| 抽帧 | `ffmpeg -i x.mp4 -ss … -t … -vf fps=120 out/%04d.png` |
| 判据 | 逐帧亮度（`signalstats.YAVG`）+ 逐帧目视 |

3× 只影响 `Animatable`/`animate*` 的时长，不影响接口往返与布局时序 —— 所以「黑屏多久」这类读数
可以直接除以 3 还原，而「谁盖着谁」这类结构读数与倍率无关。

---

## 二、五条缺陷

### D1（P0）入场先硬切到黑屏，再整块亮起

**帧证据**（`_probe/anim/e3/`，120fps）：

```
帧 1–13   YAVG 124.7 → 128.1   画廊静止（点击前）
帧 14     YAVG 128.1 → 35.1    一帧之内整屏变黑（硬切，没有淡出）
帧 14–31  YAVG 35.1 恒定       黑屏 + 顶栏两枚按钮，约 150ms
帧 32     YAVG 35.1 → 100.7
帧 33     YAVG → 126.9         详情内容一次性全亮
```

**成因**：`PixivWorkScreen` 是独立 Activity（`PixivWorkActivity`）。新窗口第一帧就画
`tokens.color.background`（深色档即近黑），而详情数据还没到 —— `details[currentId]` 为 null，
页面没有内容可画。飞行体又要等 `detail != null` 才起飞（`PixivWorkScreen.kt:403`），
于是「黑屏」必然排在「起飞」前面。

- `PixivWorkScreen.kt:536-539` 根 Box：`background(tokens.color.background)`
- `PixivWorkScreen.kt:392-431` 入场 effect：`if (detail == null) return@LaunchedEffect`

**为什么这条最严重**：它不是「动画不好看」，是**画面断了**。用户先看到一整块黑，
再看到图飞过来 —— 中间那 150ms 把「从卡片长出来」的连续性彻底打断，这正是读成「闪」的来源。

---

### D2（P0）入场飞行全程，页面已经完整可见 —— 同一张画两份

**帧证据**（`02-entry-double-image.png`）：卡片截图（496×634）在左上角，而详情页的大图
（1080×1058）已经全不透明地铺在下面。飞行过程中屏上是「同一张画、两个尺寸、两个位置」。

**成因**：`PixivWorkScreen` 没有 `GalleryPostScreen` 那套交棒档。`entryFlying` 只控制飞行体
画不画，**不控制页面 alpha**：

- `PixivWorkScreen.kt:715-766`：飞行体是页面的兄弟节点，页面本身没有任何 `alpha` 门
- 对照 `GalleryPostScreen.kt:1112-1130`：那边 `Entrance.FLY` 期间 `alpha = handoffPageAlpha(handoff)`，
  起飞时 `handoff.snapTo(0f)`（`GalleryPostScreen.kt:667`），所以飞行全程页面是透明的

样板那一半已经写对了，作品页这一半没抄。

---

### D3（P1）详情 → 播放器：飞行体里带着上一屏的顶栏

**帧证据**（`03-viewer-entry-chrome-in-body.png`）：飞行体内部可以清楚看到
返回箭头圆钮、右上「更多」圆钮、以及「1/1」页码胶囊。交棒时它们变成一层鬼影
（`04-viewer-exit-page-gone.png` 同族）。

**成因**：`openPages` 交给 `GalleryFlyIn.capture` 的矩形是**画面框**
（`PixivWorkScreen.kt:273-288`，日志实测 `origin=(0,0,1080,1380)`），
而顶栏那三枚是浮在画面之上的。`PixelCopy` 读的是合成后的表面，分不出谁是谁。

本该由 `ChromeCaptureSuppress` 兜住，但它只认**登记过矩形**的 chrome：
`VeneraTopAppBar.kt:191` 与 `Navigation.kt:1316` 登记了，而作品页这三枚用的是
`VeneraTopBarPill`（`PixivWorkScreen.kt:604-646`），**没有登记** →
`obstructs()` 判 false → 不让位 → 被截进飞行体。

---

### D4（P0）详情返回：完全没有动画，硬切

**帧证据**（`05-detail-back-hardcut.png`）：返回按下后第 7 帧（约 0.1s）屏上已经是画廊，
中间没有任何飞行体、没有淡出、没有位移。对比入场那 1.4s 的密集帧，返回段几乎是空的。

**成因（2026-10-19 订正）**：`PixivWorkScreen` 从来没有接系统返回 —— 这一屏只给顶栏
那颗返回按钮挂了离场链，而三键/手势返回直接把 Activity `finish()` 掉，**整条自定义离场
链一次都没跑过**。

**初版把这一条写成了「两端键不一致」—— 那是错的，实测已经排除**：

- 同一趟改点顶栏那颗返回，探针里 `pixiv 返回查落点 target=Rect.fromLTRB(32.0, 1103.0, 528.0, 1379.0)`
  是拿得到的，说明 `cardBoundsOf(post.viewerKey)` 查得到、键没错；
- 差别只在**入口**：系统返回压根不进 `back()`。

所以这一条不是"飞不起来"，是"根本没走这条路"。修复 = 补一枚
`BackHandler(enabled = !leaving) { back() }`（`PixivWorkScreen.kt:636`），与顶栏那颗同链。
用 `BackHandler` 而不是 `PredictiveBackHandler`：这一屏的离场是"整屏一帧缩回卡片"，
没有跟手的中间态可言，接上进度反而要再编一套它没有的姿态。

订正后的真机验收（3× 缩放，60fps 抽帧）：返回段 `YAVG` 162.2 → 117.3 平滑走完
（1.04s），中间没有一帧跳变；帧序是「整屏缩成圆角块 → 逐帧变小 → 落回墙上那张卡」。

---

### D5（P1）播放器返回：页面在前 25% 就消失，只剩一块图往回收

**帧证据**（`04-viewer-exit-page-gone.png`）：返回开始约 0.17s，下面那屏详情页已经完整可见，
而飞行体还停在起点附近没走多远 —— 读起来是「页面先没了，然后一块图慢慢飘回去」。

**成因**：`exitPageAlpha(progress) = 1 - progress / 0.25`（`GalleryExitExpansion.kt:60-69`），
`progress` 是弹簧值（= 路程百分比）。弹簧前 25% 走得很快，所以页面在飞行的头一小段就归零，
剩下 75% 的路程是在**下面那屏已经全亮**的状态下走完的。

`exitPageAlpha` 的头注写的「飞行体与页面首帧逐像素相同，交叉淡出不会露馅」只在
**起点那一帧**成立；一旦飞行体离开起点，两者就不再重合，交叉淡出必然露出下面那屏。

**成因订正（2026-10-19，逐帧复核后）**：页面让开得快**不是**病根 —— 那一半必须快，
慢了就是"页面自己的图 + 飞行体 + 落点那份"三份同一张画同时半透明。真正的病根是
**飞行体整程不透明**，而落点那一侧自己就画着同一张图、且一直全亮：

```
起点框（画面框）  1080×1386 @ y=400
落点框（作品页那一格） 1080×1877 @ y=0
```

两者套在一起，于是返回的每一帧都是**两份比例不同的同一张画叠着**：飞行体不是"飞回去"，
是"一块不透明的同一张图压在同一张图上滑过去"。真机第 12 帧（约 0.2s）就能读到这条：
下面那屏已经全亮，飞行体才刚起步，两块画错着位。

---

## 三、四类打分（仅这条链路）

| 类别 | 分 | 依据 |
|---|---|---|
| Performance | 6 | 动画值都读在绘制 lambda（`graphicsLayer` / `drawBehind`）里，没有每帧重组；扣分在 `PixivWorkScreen` 的入场把「等网络」与「等布局」串在一条链上，空转 150ms 全黑 |
| State Management | 3 | D4 的键不一致是同一份状态被两套键各写一遍；`uid` / `viewerKey` 在三个文件里三种用法 |
| Side Effects | 5 | `LaunchedEffect` 的等待预算（600ms / 160ms）合理；但入场把 `entryFlying` 与页面可见性拆成两个互不知情的开关，效果层承担了本该由状态层表达的「进行到哪一档」 |
| Composable API Quality | 5 | 飞行体绘制在 `GalleryPostScreen` 与 `PixivWorkScreen` 各写一遍（约 70 行同构代码），口径靠注释同步而不是靠同一颗件；`ChromeCaptureSuppress` 的登记契约是「谁被压谁登记」，新 chrome 漏登记是静默的 |

---

## 四、修复清单（按优先级）

### P0-1 入场不要黑屏：飞行体先走，页面后亮

新窗口第一帧就应该只有「卡片那一帧」在屏上，而不是黑底。

- 在 `detail == null` 期间，根 Box 不画 `tokens.color.background`，改成画
  `GalleryFlyIn.payload` 铺满（起点那一帧已经在手上，`capture` 是点击那一刻同步给的）；
- 落点不必等接口：`GalleryPost` 自带 `width`/`height`（`GalleryPost.kt:76-77`），
  详情页的图是满宽顶对齐，落点可以**先算**再校正；
- 详情到了、真实落点量出来之后，用 1~2 帧把预测值收敛到实测值（差值通常为 0）。

### P0-2 飞行期间页面必须是透明的

照抄 `GalleryPostScreen` 的 `handoff` 三档，作品页加同一套：

```
entryFlying = true  → 页面 alpha = 0，飞行体不透明
飞完               → handoff 0→1（160ms），前半程页面淡入、后半程飞行体淡出
```

对应改动：`PixivWorkScreen.kt:715-766` 外层加 `graphicsLayer { alpha = … }`，
`entryFly.animateTo` 之后补一段 `handoff.animateTo`。

### P0-3 返回的键统一

两条路选一条，**不能两头各写一半**：

- 方案 A（推荐）：`GalleryScreen.kt:3134` 改成登记 `post.viewerKey`。
  理由：返回程查的就是 `viewerKey`，而且 `PixivWorkScreen.kt:1326` 已经这么登记了；
- 方案 B：`PixivWorkScreen.kt:483-486` 退回 `post.uid`。但这会让「同一作品第 2 页返回」
  落到同一张卡上，页轴那一屏会错位。

改完必须补一条判据：**同一部作品从主墙进、从 pixiv 首页那两排进，返回都要飞**。

### P1-1 顶栏 chrome 登记占位

给作品页那三枚（返回 / 更多 / 页码）挂 `Modifier.reportChromeBand("pixiv-detail-top")`，
并在离开组合时 `forgetBand`。这样 `capture` 会自动走「收 chrome 两帧再截」那条既有路径
（`GalleryFlyIn.kt:171-183`），不用在 `openPages` 里另写一遍。

顺带把这条契约变成显式的：`ChromeCaptureSuppress` 目前漏登记是**静默**的，
建议在 `reportBand` 之外加一条「已知 chrome 名单」的编译期/单测检查。

### P1-2 播放器返回：让飞行体在末段化进落点那一份

`exitPageAlpha` 的归零点**不动**（0.25 是对的，慢了就是三份同一张画同时半透明）。
要动的是飞行体自己：新增 `exitBodyAlpha`（`GalleryExitExpansion.kt`），
起步点钉在**页面让开的终点**（0.25）—— 两条曲线首尾相接、不重叠，之后线性到 0。

先取过"后 40% 才淡"（`0.4`），真机复核发现返回中段（进度 0.5 附近）那两份同一张画
还是一份全不透明地压在另一份上（标题文字、卡片圆角都读得出两层），所以往前挪到 0.75。

判据在 `GalleryExitExpansionTest`：
`页面让开之后飞行体才淡，末段归零`（错开归零点、单调不增、越界不回弹）
与 `飞行体淡出那一段分数钉住`（0.75，且与页面那枚 0.25 首尾相接）。

### P2 飞行体绘制收口

`GalleryPostScreen.kt:1361-1440` 与 `PixivWorkScreen.kt:718-766` 是同构的两份。
抽一颗 `GalleryEntryFlyBody(bitmap, from, to, progress, cornerRadiusPx)`，两处都调它 ——
现在两边的圆角口径、cover 裁切、alpha 门各写一遍，任何一边改了另一边不会跟着改。

---

## 五、验收方法

1. `animator_duration_scale` 调到 3×，`screenrecord` 录「点卡 → 等 3s → 返回」；
2. 抽帧后按三个判据过一遍：
   - 点击后**第一帧**不得出现整屏近黑（`YAVG` 不得跌破 60）；
   - 飞行段内不得同时出现两份同一张画（目视 + 帧差）；
   - 飞行体内部不得出现任何上一屏的按钮/胶囊；
3. 返回段：飞行体必须从画面框起、落到卡片矩形，帧数不少于 20 帧（3× 下）。

---

## 六、证据帧

| 文件 | 说明 |
|---|---|
| `01-entry-black-gap.png` | D1：入场黑屏（帧 16/120） |
| `02-entry-double-image.png` | D2：飞行中页面已全亮，两份同图 |
| `03-viewer-entry-chrome-in-body.png` | D3：飞行体里带「1/1」与两枚圆钮 |
| `04-viewer-exit-page-gone.png` | D5：返回 0.17s，下面那屏已全亮 |
| `05-detail-back-hardcut.png` | D4：返回后 0.1s 已是画廊 |

原始录屏与抽帧在 `_probe/anim/`（`anim3.mp4` 入场、`anim4.mp4` 全链路、`a7.mp4` 返回）。

---

## 七、修复后的真机验收（2026-10-19，`animator_duration_scale = 3`）

| 链路 | 判据 | 读数 | 结论 |
|---|---|---|---|
| 墙 → 作品页（D1） | 点击后无整屏近黑 | `YAVG` 116.2 → 118.1 → 113.8 → 114.9，**没有一帧跌破 60** | 过 |
| 墙 → 作品页（D2） | 飞行段不得两份同图 | 帧序「垫帧站原位 → 模糊背景 → 放大 → 铺满」，飞行期间只有一份 | 过 |
| 作品页 → 播放器（D3） | 飞行体里不得有顶栏 | 截帧前顶栏三枚被 `ChromeCaptureSuppress` 收掉（`pv3e/0010` 顶栏已空），飞行体内无按钮/胶囊 | 过 |
| 作品页返回（D4） | 返回要有飞行、不得硬切 | 探针 `target=Rect.fromLTRB(32,1103,528,1379)`；`YAVG` 162.2 → 117.3 平滑走完 1.04s | 过 |
| 播放器返回（D5） | 飞行体不得整程压在落点那份上 | `YAVG` 139.4 → 156.9 → 122.9 无跳变；末段两份同一张画合成一份（`pv5t/14.05` 只剩淡影，`pv4f/0035` 那版是全不透明两份） | 过 |

未做（已知、留档）：播放器返回**首帧**会摘窗口模糊，玻璃底从「糊」变「清」有一次很轻的提亮
（`YAVG` 139 → 157，1× 下约 1~2 帧）。它不属于 D1~D5 任何一条，改法要动"什么时候摘模糊"
这条既有契约（摘早了提亮、摘晚了整屏糊到 finish），本轮不动。

`FlyProbe` 那一批取证探针仍在代码里（`PixivWorkScreen` / `GalleryFlyIn` / `GalleryPostScreen`），
撤的时候一并删。

---

## 参考

- Value-based animations（`Animatable` / `animate*AsState`）：https://developer.android.com/develop/ui/compose/animation/value-based
- Customize animations（`AnimationSpec` / `spring` / `tween`）：https://developer.android.com/develop/ui/compose/animation/customize
- Animation overview：https://developer.android.com/develop/ui/compose/animation/introduction
- Shared element transitions（Compose 内、跨目的地；跨 Activity 无此 API）：https://developer.android.com/develop/ui/compose/animation/shared-elements
- Compose 性能最佳实践（绘制阶段读动画值）：https://developer.android.com/develop/ui/compose/performance/bestpractices
