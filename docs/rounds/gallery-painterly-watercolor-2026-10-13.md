# 画廊查看器 · Painterly 水彩绘制（2026-10-13）

> 分支 `feat/painterly-watercolor`（worktree `.worktrees/painterly`）。用户 2026-10-13 指派：
> 参考 p5.js + p5.brush，给画廊大图页加一个按钮，**把当前这张图画成水彩**。
> 三条硬边界（用户原话）：不是播放 Willow 那套柳树池塘动画，不是"原图从模糊变清晰"，
> 而要"纸上先有底色与淡颜料块，再逐渐长出背景、主体与细节，最后是一幅完整的水彩画"。

## 一、技术选型：原生移植，不引 p5.js 运行时

候选三条路，结论是**纯 Kotlin 重写水彩模型**：

| 路线 | 否掉的原因 |
|---|---|
| 内嵌 WebView 跑 p5.js + p5.brush | 要往仓库塞 ~1 MB 的 JS 运行时与许可链；画一张图要在 WebView 里解码图片、跨 JNI/JS 桥传像素；GPU 上下文与页面转场打架；离线画廊模块不许依赖网络取脚本 |
| RenderEffect / 着色器滤镜 | 能做"渗色模糊"，做不了"笔触"——水彩的魂是一道道有起收笔的笔迹，滤镜只能糊 |
| **纯 Kotlin 规划 + Canvas 渲染（选定）** | 规划是纯函数，进 JVM 单测（本仓一贯口径）；渲染走位图 Canvas，无新依赖；3000 笔 / 27 秒的预算 CPU 扛得住 |

p5.brush 里被继承的是**模型**而不是代码：stamp 笔触、bleed 渗色（边缘多刷几遍的积边）、
ColorField 色块、顺着场走的笔触方向；再叠 Tyler Hobbs 的分层半透明罩染法
（p5.brush 的 watercolor fill 注释里点名的那篇）。

## 二、分层与文件

```
gallery/watercolor/
  WatercolorSource.kt       纯：像素网格 + 亮度/Sobel 梯度/边缘强度（按图自适应归一化）
  WatercolorPlanner.kt      纯：Source + seed → WatercolorPlan（四阶段笔触队列 + 纸色）
  WatercolorRenderer.kt     Android：纸底 + 尿点 + 锥形带笔触（渗色两遍 + 芯线）
  WatercolorPainterScreen.kt Compose 全屏浮层：取图 → 构图 → 逐笔绘制 → 动作条
```

判据（不依赖 Android 框架的那一半）按仓库纪律下沉在 Source/Planner，测试
`WatercolorPlannerTest` 16 条：阶段顺序与占比、同种子同构图、笔触不出画布、
细节笔挑高边缘 / 背景笔挑平坦、左红右蓝图上晕染左右分色、暗图纸底仍亮暖、高光笔更亮更淡、
锥形带的长度/宽度/alpha 分级、`plan` 对极端长宽比不抛。

## 三、画是怎么"长"出来的

四个阶段按序入列，屏上时间顺序即叙事顺序：

1. **晕染（12%）**：格网取平均色 → 三层同心圆章（外 0.10 / 中 0.09 / 心 0.12 alpha），
   每格 55% 概率加一枚偏移副章。格网刷不够预算就循环刷 —— 同一格落两三枚湿块才像"罩上去的"。
2. **背景（30%）**：长笔（0.30~0.70 短边）、低 alpha（0.13~0.19），落点挑**平坦**区
   （接受概率随边缘强度降），方向沿等亮度线（梯度法向）。顺着明暗交界"铺"，不描线。
3. **主体（46%）**：中等笔（0.10~0.32）、alpha 0.32~0.45，落点偏挑**有结构**处；
   阶段内按目标长度降序发出 —— 先大形后小形。
4. **细节（12%）**：85% 浓短边线（专拣高边缘、颜色压深 18%、饱和 +12%）+
   15% 纸色高光（拣亮且平处，把高光"留"出来）。

每一笔的渲染（对应 p5.brush 的 bleed）：沿折线法向**两侧各抖一遍**（略宽、各 0.16 alpha），
再画收窄到 0.90 的芯线 —— 边缘累计 ≈0.32、芯 ≈0.5，深浅差就是水彩的**积边**。
笔触一律画成**锥形带**（sin² 收笔曲线，端点留 0.18 不收死）：等粗圆头线读起来是套管，
锥形才是一"笔"。纸是暖白 `#F3EAD8` 打底 + 150 枚大半径低 alpha 尿点，
每笔颜色从原图取样后往纸色回收（背景回收 12%、主体 6%、饱和度压一档）——
纸的颗粒从颜料底下透出来，叠加几百笔也不会变成塑料。

管线里每一笔都是半透明 SRC_OVER 叠加，没有一处"画死"的边界 —— 这是与原图
"清晰度渐变"路线的根本区别：那边改的是清晰度，这边改的是**画没画完**。

## 四、界面与接线（UiDesigner 口径）

- **入口**：大图页底部 dock 倒数第三枚 `画笔` 钮（`Icons.Outlined.Brush`，tint 与其余动作一致）。
  **视频条目留 48dp 空位不放假钮**（与 HD 同一口径），dock 六枚钮的横向位置不随内容类型漂移。
- **浮层**：整屏不透明白底，画布 Fit 居中、圆角 `tokens.shape.large`。顶栏左侧 `✕` +
  阶段读数（"正在画·晕染 23%"+ 细进度线）；画成后底部动作条四枚：
  **保存**（PNG 进相册，进行中吃点击）/ **重画**（换颗种子，同图另画一幅）/ **看原图**（原档淡入淡出对照）/ **完成**。
  取图/构图期间是波浪环，取不到像素时说一句话并给退出，不摆空的转圈环。
- **让位**：浮层开着时连播不起线不翻页（`autoPlayRunning` 与计时 effect 两处判据都带上
  `painterPost != null`）；返回键收浮层（`BackHandler`）。
- 绘制节奏 **110 笔/秒**（3000 笔 ≈ 27 秒），每帧按帧间隔补笔、单帧最多追 0.25 秒的账
  （从浮层外回来不要一口气补一百笔）。帧计数只在 `drawBehind` lambda 里读 —— 每帧只重绘画布，
  不重组整棵树。

## 五、公共文件改动（先说明，后动手）

| 文件 | 改动 | 接口影响 |
|---|---|---|
| `gallery/ui/GalleryViewerToolbar.kt` | 加 `onPaint: () -> Unit` 参数 + 画笔钮（视频留空位） | **签名变更**，全仓唯一调用点 `GalleryPostScreen` 同批改 |
| `gallery/ui/GalleryPostScreen.kt` | `painterPost` 状态、dock 传 `onPaint`、两处 `shouldAdvance` 加 `painterPost != null`、根 Box 末尾挂 `WatercolorPainterScreen` | 行为面变化：浮层开着不连播；无对外签名变化 |
| `gallery/data/GallerySaver.kt` | 新增 `saveArtwork(context, bitmap, name): Result<Saved>`（PNG，落点/命名/双路写入与 `save` 同一套，**加法式**） | 纯新增，既有函数一字未动 |
| `README.md` / `README.en.md` | 画廊查看器工具条那一行加"画成水彩" | 文档 |

`gallery/` 的 `WatercolorPainterScreen` 只依赖模块内 internal（`galleryLargeRequest` /
`galleryCacheKey`）与 `GallerySaver.saveArtwork`，没有第二份真相。

## 六、验证

- `./gradlew :app:testDebugUnitTest`：**全量 BUILD SUCCESSFUL**（含水彩 16 条判据；本仓既有用例零回归）。
- `./gradlew :app:compileDebugKotlin`：**BUILD SUCCESSFUL**。
- **可视化验收**（`.worktrees/painterly/_qa/painterly-preview/`，未入库）：JVM 里用 java.awt
  按同一模型复刻渲染，对 app 图标 / 画廊截图 / 合成夕阳山水三个输入出四阶段 PNG。
  两轮调参的读数：① 初版等粗圆头线 + 短笔 —— 完成态是一地"毛虫"，图像不显影；
  ② 加长加宽各档笔触 + **锥形带**后，夕阳的"天空渐变—太阳—山影—水面波光"四层结构
  在第二阶段（背景）就已可读，完成态是成立的松散水彩。锥形带这一步观感跃升最大。
- **未验证**（交回用户侧）：真机帧率与内存（1024² 软件位图逐笔绘制）、
  `BlurMaskFilter` 在部分机型上的消耗、保存画作在各 Android 版本的相册落点、
  HD 档（`file_url`）不参与取色（画的是 large 档像素，与"下载存原档"的两档口径一致）、
  10 bit / 超宽长宽比图的构图。

## 七、已知限制（写进代码注释，不放"即将支持"）

- 视频条目不进水彩（无静帧语义，取 poster 没有意义）。
- 取的永远是 `large_url` 像素（与屏上默认档一致），不是原图档；重画换种子不换源图。
- 桌面端（`:desktop`）未接 —— 那是另一条线的编译面，本轮 `app/` 之外一行未动。
