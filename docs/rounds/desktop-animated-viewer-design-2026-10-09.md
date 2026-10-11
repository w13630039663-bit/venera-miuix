# 批 51 · 动图那一档（只在大图那一屏逐帧）

日期 2026-10-09 · 分支 `compose-migration` · 上一批 = 批 50（§81）· 本批 `app/` **零改动**、**零新依赖**

## 1. 用户拍板的两条

- 范围：「先做大图屏，**动图紧接第二批**」（2026-10-08）。
- 层：「**只在大图那一屏动**」（2026-10-09 三选一里选的那一档）⇒ 墙上缩略卡与浮层 hero 一行不改。

## 2. 四条前置读数（都量过，不是推断）

1. `javap coil3.Image`（`coil-core-jvm 3.6.2`）只有 `getSize/getWidth/getHeight/getShareable/draw(Canvas)`
   —— **接口里没有「帧」这一档** ⇒ "写一颗 Coil Decoder 让 `AsyncImage` 自己动"结构性不通。
   这才是 `coil-gif` 没有 `-jvm` 变体的机制（批 47 只量到"没有变体"那个症状）。
2. `org.jetbrains.skia.Codec`（`skiko-awt 0.150.1`，Compose 自带）逐帧要的四件全公开：
   `Codec.makeFromData(Data)`、`getFrameCount()`、`getFrameInfo(i).getDuration()`、
   `readPixels(Bitmap, frame, priorFrame)`（参数名读自 class 文件 `d2`，**不是** `frameCount`）。
   ⚠️ `priorFrame` 意味着**帧合成由 Skia 自己做**（从 `getFrameInfo(i).requiredFrame` 起解）⇒
   我们不自写 disposal 逻辑。另外三件也一并量实：`Data.makeFromBytes(byte[], off, len)`、
   `Bitmap.allocPixels(ImageInfo)`、`Image.makeFromBitmap(Bitmap)`（+ `toComposeImageBitmap()`）。
3. 字节不必重下一趟：`DiskCache.openSnapshot(key)` → `Snapshot.getData(): okio.Path`（+ `close()`）。
   ⚠️ 键不中恒空 ⇒ **这一格必须带读数**（命中/未命中），不许拿"我调了 openSnapshot"当命中。
4. 位图到 Compose 那两跳都在：`Bitmap.allocPixels(ImageInfo)` / `installPixels(ImageInfo, ByteArray, rowBytes)`
   → `Image.makeFromBitmap(Bitmap)` → `toComposeImageBitmap()`（`DesktopComicPane.kt:208` 已经在用后一跳）。

## 3. 组件（两颗新件 + 一处接线）

| 件 | 职责 | 落点 |
|---|---|---|
| `gallery/ui/DesktopGalleryViewerMotion.kt` | **纯函数**：该不该逐帧、帧间隔下界、那行读数 | 新 |
| `gallery/ui/DesktopAnimatedImage.kt` | 播放器：取字节 → `Codec` → 按 duration 推进帧号，**一次只持一帧** | 新 |
| `gallery/ui/DesktopGalleryViewer.kt:80` 那个 `else ->` | URL 像动图且该动 ⇒ 摆播放器；其余照旧 `AsyncImage` | 改 |

- 该不该动调**共享那颗** `GalleryMotion.animates(mode, unmetered)`（`gallery/domain`，在桌面编译面上），
  桌面 mode 由 `DesktopGalleryPreferences.kt:95` 给 `ALWAYS`（那里已写明为什么）⇒ **不许重述那个 when**。
- 失败形态一律**回落 `AsyncImage`** 并念读数：没缓存快照、`Codec` 解不出、`frameCount <= 1` ——
  三句分开念，不许合并成一句"动图失败"。
- 帧间隔下界取 **1 ms**，且它**不是观感口径**：站方给 0 或负数时不许把 CPU 打满，仅此一条。
  （本仓与 Coil 桌面侧都没有既有的下限常量可抄，所以这里不造第二个数。）

## 4. 取证：不新造种子（改判）

原打算加 `-PanimatedWall=1`（起窗就落在这一批里第一张 `.gif`）。量过现成那一族之后**不加**：
`--start-domain=SEARCH --query=<动图标签> --detail=1` 已经能把落点送到"一面全是 GIF 的墙的第一张"，
而 `--detail` 走的就是 `LocalDesktopWallSeedFirst`（与用户点卡同一个 `onPick`）⇒
新造那枚种子要再铺一层"挑第一张 gif"的选择器 + 三颗判据，而它换来的证据与现成这一组**同一格**。
⇒ 判据不减，代码不加（YAGNI）。

- 读数一行：`D_动图 帧=N 循环=R 缓存=中|空 档=<tier> 尺寸=WxH 每帧=<n>[K|M]B`，全从状态现取，参数一个字不念。
- 退化另念一行 `D_动图 退化 <那一句> 档=<tier>`。
- ⚠️ §83 那条教训仍然守着：**不带这组种子起一次**也要拍一帧，确认默认落点没被动图改坏。

## 5. 判据（落地实数：+12 颗）

`DesktopGalleryViewerMotionTest` 10 颗：该不该逐帧（三档各过）/ 帧间隔只夹下界且合法值原样（四种输入）/
三种退化两两不同句 / 读数六枚都在（含 `每帧=` 的 KB 与 MB 两档）/ 无限循环不许念成 -1（喂 -1 与 3 两种）/
共享那颗判据只被调一次也没被重述（**按分支形状扫而不是按枚举成员名** —— 第一版红在
`DesktopGalleryPreferences` 那枚合法默认档上）/ 解码器不许出现在墙上与浮层 /
大图那一屏留着 `AsyncImage` 那条回落 / 地址那道门认扩展名而不认 query 尾巴 /
**播放器一次只持一帧位图**（§8 那决定唯一的凭据）。
`DesktopAnimatedDecodeProbeTest` 加两颗：④ `Codec` 那一档交出第 1 帧（蓝）而 ② 那颗入口仍交第 0 帧（红）；
⑤ 每帧真实字节 = w×h×`bytesPerPixel`（量 `Bitmap.computeByteSize()`，不拿 4 当假设）。

原计划里"恒 Fit 不许被换掉"与"种子那一跳全在"两枚**没新写**：前者批 50 已钉着（本批没动它），
后者随 §4 的改判一起取消。

## 6. 账本改的那一句

`DesktopGallerySettingsSpec.kt:130` 那句 Absent 从「动图仍停在第一帧：那一族解码器没有桌面变体」
改成「**大图那一屏逐帧在播；墙上与浮层仍静帧 —— `coil3.Image` 接口没有帧这一档，接不进 `AsyncImage`**」。
上面那四环 KDoc 同时补一段：那枚探针量的仍是 `AsyncImage` 那一路，**没被本批改掉**。

## 7. 落地账（四类各一行，§14 的口径）

- **改动**：新增 `DesktopGalleryViewerMotion.kt`（四道算式）+ `DesktopAnimatedImage.kt`（播放器）；
  大图那一屏的 `else ->` 加一档；集成方现算 `GalleryMotion.animates` 传下来。`app/` 零改动、零新依赖。
- **新规则**：**"起窗即开的那一层"不许依赖缓存已被别人预热** —— 播放器第一次真跑就撞上冷缓存，
  屏上永远是首帧而代码看着全对（读数 `D_动图 退化 …还没进缓存` 当场抓到）。
  判式：任何"读现成缓存"的起手式都要问"谁先写它、我这一刻等了多久"。修法是**先让同一颗 loader 取一趟**
  再读快照，不另起一条轨（§76 那条账不许破）。
- **新知识**：`readPixels(bitmap, frame, priorFrame)` 的第三枚是 `priorFrame`（参数名读 class 的 `d2`，
  不是 `frameCount`）；`Codec` 交出第 1 帧而 `Image.makeFromEncoded` 永远交第 0 帧 —— 这条现在有判据 ④ 钉着。
- **数字**：**699 判据 / 91 类 / 0 失败**（三方对账：XML 属性 = `<testcase>` 计数 = 源码 `@Test` = 699）；
  `desktop/src/main` 79 颗 / 15,573 行；jar **4,163,382 B**；起窗两次、关窗后残留窗 **0**。
- **扰动三笔**：P1 重述分支 ⇒ 红 ⑥（并连带被零调用点那把尺子报出）；P2 墙上出现 `Codec` ⇒ 红 ⑦；
  P3 把实参改成具名写法 ⇒ **一条不红**（锚点没钉深）。⚠️ 第一版**读数器**恒绿（手册 §0.1 ㊖），三笔全部重跑过。
- **图证三帧**：退化帧（冷缓存，屏上仍是首帧而**不是白屏**）+ 相隔 1.4 秒的两帧（姿势各不相同，
  且都不同于首帧）⇒「动着放」这一句今天是有帧的。读数 `D_动图 帧=70 循环=无限 缓存=中 档=SAMPLE`。
- **仍只能用户手动**：滚轮（批 50 那半条没变）；两层都关完后焦点交回。
- **补一格（§4 那条自审）**：**一颗种子都不给**另起一次 ⇒ 读数 `域=图库(启动) 行=每日热门 种子=无`，
  且随后出现两笔 `浮层=开(墙上点卡) 查看器=开` 与两笔 `浮层=关(关掉浮层) 查看器=关`。
  这一跑我没碰鼠标 ⇒ 那两笔是**外部真点击**落在新窗上（与 §77.3 那一格同形状，不是新缺陷）。
  顺带第一次拿到**不经过取证种子**的凭据：卡上那一路 `onClick` → 大图 → × 一次关两层，全对。
  ⚠️ 那一跑**只取了读数、没拍帧**（默认落点仍是那一面墙，批 50 已有帧）。

## 8. 收尾那一格：每帧到底多大（同日第二次跑，读数在手才改口）

- **改动**：那行读数补两枚 —— `尺寸=WxH 每帧=<n>[K|M]B`。位深从 `Bitmap.bytesPerPixel` **现取**，
  不拿"32 位那一档"当假设；字节标签用整数除法而不是 `format("%.1f")`（默认 locale 会把小数点变成逗号，
  那条读数就再也匹配不上判式）。探针加第 ⑤ 颗把 Skia 自己的算法钉成恒等式（w×h×bpp == `computeByteSize()`）。
- **三笔真读数**：`帧=70 循环=无限 缓存=中 档=SAMPLE 尺寸=1000x1000 每帧=3.8MB`（正是"该不该收上限"的对象）、
  `帧=4 … 尺寸=768x320 每帧=960.0KB`、夹具 100×100 那笔 `色型=BGRA_8888 位深=4 computeByteSize=40000`。
- **决定：不收帧数上限。** 凭据是"一次只持一帧"—— 帧数不进堆，只有尺寸进 ⇒ 70 帧与 700 帧一样贵；
  1000×1000 那一档同时活着的只有两颗帧缓冲（复用的 `Bitmap` + 每帧新交上屏的 `ImageBitmap`）≈ 7.6MB，
  而 `:desktop:app` 与交付包那颗 launcher 都没写 `-Xmx` ⇒ 上限是本机物理内存的 1/4（31.8GB → ≈8GB）。
  新尺子 ⑩ 把这句钉住，三把各管一种偷懒：数 `allocPixels`（新加一处）/ 钉顺序（那一处被搬进循环 ——
  §0 那条"钉顺序不钉存在"的旧账）/ 数 `<ImageBitmap` 字面（存成一整片）。扰动两笔各红在对应那把上：
  P-a 把 alloc 搬进 `while (true)` ⇒ 只红顺序；P-b 加 `ArrayList<ImageBitmap>` ⇒ 只红计数。
- ⚠️ **两颗红是尺子自己的**（写判据当场抓到，不是代码错）：`toComposeImageBitmap` 数出 2 颗 ——
  `import` 那一行也是代码行，锚点要带括号才数的是"调用"；`<ImageBitmap>` 数出 0 颗 ——
  真形状是 `mutableStateOf<ImageBitmap?>`，锚点得按真形状拼。两形都是 §0 坑 ① 的近亲。
- **交付包那一跑抓到 launcher 的一条真缺陷**（应用侧无错）：`$seed` 只筛出一枚时被 PowerShell 摊成**标量字符串**，
  而"字符串 + 数组"是**串接** ⇒ 传四枚参数落到进程里是一整串 `--viewer=1--proxy=… --start-domain=SEARCH …`，
  于是 `D_取证参数 --viewer 值无法判读（…），按关处理` + `域=图库(启动)` + `proxy=直连`。
  双击不带参数时默认那三枚成数组，所以批 51 那次验证**没暴露**。修法：两边都 `@(...)` 强制成数组，
  并另写一颗纯命令行对照（`_trash/scratch-2026-10-09/seed-check.ps1`：带四枚 ⇒ 5 颗干净的参数；
  不带 ⇒ 3 颗默认）。**这条同时是"出声"那条规矩的又一次凭据**：崩不了、参数打错时应用自己念了那一句。
- **数字**：**699 判据 / 91 类 / 0 失败**；`desktop/src/main` 79 颗 / 15,573 行；
  重建 `:desktop:createDistributable` ⇒ 交付包 app jar 换成 4,163,382 B 那颗（旧的镜像进 `_trash/desktop-jar-stale-2026-10-09/`）；
  launcher 补上那三枚 UTF-8 编码位（否则 `D_动图` 那行中文在 PowerShell 里变 U+FFFD，只剩数字活着）。
  本格起窗三次（gradle 那颗任务一次、交付包两次），关窗后残留窗 **0**。
- **交付包那一跑转正为凭据**：`D_启动 … proxy=127.0.0.1:7890`、`D_落点 域=搜索(启动) … 种子=detail+viewer`、
  `D_动图 帧=70 循环=无限 缓存=中 档=SAMPLE 尺寸=1000x1000 每帧=3.8MB`，且**中文在 PowerShell 里读得清了**。
  那一帧还给了一个**独立对照**：浮层「作品信息」那栏写着 `分辨率 1000×1000`、`格式 GIF`、`文件大小 2.2 MB`
  —— 与读数那枚 `尺寸=1000x1000` 两处来源不同而同一格（站方给的 vs 我们解出来的），
  所以 `每帧=3.8MB` 不是拿错的宽高算出来的。
