# 桌面大图查看器（图片播放器）这一屏 · 设计 2026-10-08

## 0. 这份东西要解决的那一句原话

> 「先做图片播放器吧，现在都没能点击图片跳转到大图的功能呢，你看下有什么可以使用的图片播放器，做好」

拆成三条可验收的判据：

1. **点卡真的进"大图"** —— 不是今天那枚 200.dp 高的侧栏 hero。
2. **有可用的播放器能力**：缩放（滚轮 / 双击）、平移、翻页、退出，且这些动作与既有那对步进 lambda 同一个主人。
3. **优先复用现成库**，不自己从零写手势；但"能用"必须以**当场读数**为准，不以 README 为准。

## 1. 现状（全部现量，带位置）

| 事实 | 证据 |
|---|---|
| 卡可点，点的是"右侧浮层" | `DesktopGalleryPostTile.kt:343` `onClick = { onPick(DesktopWallSelection(wall.cards, index)) }` |
| 浮层宽 340、里面那张图位只有 200.dp 高 | `DesktopGalleryMetrics.kt:201` `detailWidth = 340.dp`；`:207` `detailHeroHeight = 200.dp` |
| 桌面**从来没有**缩放/平移/全屏那一档 | 扫 `desktop/src/main/kotlin/com/venera/desktop/gallery/ui/`：`transformable` / `graphicsLayer` / `zoom` **零命中**（只有注释里提"没有那两档"） |
| 键盘那一层**已经有了**，且只有一处接线 | `DesktopKeyNavigation.kt:48-59`（`Esc -> onClose`、`←/→ -> onStep(±1)` 的纯函数）+ `VeneraDesktopApp.kt:451-477`（焦点重试、`KEYNAV=` 读数、三件套修饰）⇒ 这一屏的键不新起一颗件，扩它 |
| 内容区**没有**宽度读数可用 | 应用根那枚内容区是 `VeneraDesktopApp.kt:335` 的 `Box(` + `:337` 的 `.weight(1f)`（外层 `:290` `Row(Modifier.weight(1f).fillMaxWidth())` 带 rail），全模块唯一一枚 `BoxWithConstraints` 在卡内（`DesktopGalleryPostTile.kt:304`）⇒ 避让宽度那一档要新挂一颗，且挂在"只在有层开着时才组合"的格上 |
| 浮层今天占的是 **340 + 46** 两道 | `DesktopGalleryMetrics.kt:201` `detailWidth = 340.dp`、`:204` `detailScrimWidth = 46.dp`，两枚挂在同一个 `Row` 上（`VeneraDesktopApp.kt:469-491`）⇒ 避让算式少减那 46 就是"大图压在渐隐带底下" |
| 连播判据里 `zoomed` 交的是**假值** | `VeneraDesktopApp.kt:410` `zoomed = false`，旁边注释明写"桌面浮层没有那两档" |
| Android 那套真查看器**不在桌面编译面** | `desktop/build.gradle.kts:22,44,80,81,82` 只 `srcDir` 了 `data/platform`、`data/db`、`gallery/data`、`gallery/domain`、`security/guard`；`gallery/ui` 不在其中 |
| 动图结构性无解码器 | `coil-gif` 3.6.2 无 `-jvm` 变体（手册 §76 记的实测）⇒ **本批不做动图**（已拍板：紧接第二批） |

结论：这不是"坏了"，是**这一屏还没搬**。所以接口要新长，政策件继续转交。

**改前基线帧（2026-10-08 07:55 现拍，`-PstartDomain=GALLERY -Pdetail=1`，窗口 1440×900 / 捕获 1226×768）**：屏上右侧那一整条 340 浮层里，图只占顶上那一条（`detailHeroHeight = 200.dp`），下面是「预览 / 大图 / 原图」三档、`第 5 / 40 张` 与作品信息；左边整块全是墙。⇒ "点卡之后能看大的极限"今天就是那一条 200 高的图位，**没有**任何一层把图铺到内容区。
⚠️ 帧是缩放过的（1226/1440 ≈ 0.851），**别拿帧上量出的像素当 dp 用** —— 这一帧只用来证明"有没有那一层"，不用来报尺寸。

**基线帧顺带抓到一枚量具缺口**（本批一并补）：`-Pdetail=1` 开的浮层，stdout 念的是 `D_落点 … 浮层=开(墙上点卡)` —— 与人手点卡**同串**。查过成因不是"漏配对的戳"：种子是**故意走人手那条链**的（`VeneraDesktopApp.kt:351` `LocalDesktopWallSeedFirst provides startDetail`，注释明写"五面墙不用改签名就能知道自己该不该自开浮层"），而戳写在 `onPick` 里（`:348` `pickStamp = "墙上点卡"`）⇒ 拆开来改会把那条设计拆坏。**修法**：在那行读数上补一枚从 flag 现取的字段（`种子=detail`），键链与戳都不动；判据钉"这行里 `种子=` 与 `-Pdetail` 同真假"。

## 2. 播放器选型（三族全部量到桌面变体层，不是看 README）

量法：对每一族逐枚取 Maven Central 的 `*.module`（Gradle 变体元数据），顺着 root 的 `available-at` 指针取到**真会被解析的那颗 `*-desktop` 件**，再读它 `desktopApi`/`desktopRuntime` 两个变体的依赖表；滚轮那一半靠 unpack `*-desktop-sources.jar` 后 grep。

| 候选 | 桌面变体实测 | 关键读数 | 判 |
|---|---|---|---|
| **`net.engawapg.lib:zoomable` 2.16.0**（stable） | root → `zoomable-desktop:2.16.0`，`desktopApi`+`desktopRuntime` 俱在 | 滚轮缩放是**一等公民**（`MouseWheelZoom` 六档枚举）；`desktopRuntime` 里 `foundation/runtime/ui/ui-util` 全 **1.12.1**；件本身薄（common jar 10,540 B） | **取** |
| `io.github.panpf.zoomimage` 1.4.0-beta02 | `zoomimage-compose-desktop` 与 **`zoomimage-compose-coil3-desktop`** 都存在（先前我判它"是 Android 件"—— **判错了，已改判**） | 带 Coil3 集成（声明 `coil-compose 3.2.0`，本仓 3.6.2 在其上）+ 自带 pager；但**停在 beta**，compose 声明 **1.8.0**（跨版本，Gradle 取高可用但属跨版本跑） | 备用：beta + 能力超出这一屏所需（翻页我们已有那对步进 lambda） |
| `me.saket.telephoto` —— **本仓 Android 侧已在用**：`libs.versions.toml:25` 锁 **0.19.0**、`app/build.gradle.kts:148` 引 `zoomable-image-coil3`、`GalleryPostScreen.kt:130-132` 用 `rememberZoomableState`/`Modifier.zoomable` | `zoomable-desktop:0.19.0` **在**（还带 `desktopMain` 源集），但 **`zoomable-image-coil3` 无 desktop 变体**（0.16.0 与 0.19.0 两次实测都是 `desktop=NONE`） | unpack `zoomable-desktop-0.19.0-sources.jar`（59,656 B）后 **`Wheel` 关键字零命中** ⇒ 手势集不含鼠标滚轮；桌面主要输入就是滚轮 | **不取（这一屏）** |
| `SubsamplingScaleImageView` / Fresco / landscapist | 未见任何 desktop 变体（Android View / Android 件系） | — | 不取 |

- **两端用不同库不是问题**：`gallery/ui` 不在桌面编译面（§1 已量），Android 那颗查看器搬不过来；这一屏要的只是"缩放/平移/滚轮"那一小块能力面。telephoto 那条线留到"真要把两端查看器合成一件"时再议 —— 那时 `zoomable-image-coil3` 在桌面结构性取不到（刚量的），得连那一层一起改。
- **兜底方案（写进设计，不是"以后再说"）**：若依赖探针失败（版本冲突 / 滚轮在 skiko 上不生效），退回**自写**：`Modifier.transformable` + `Modifier.pointerInput` 收滚轮 + `graphicsLayer{scaleX/scaleY/translationX/translationY}` 自己夹边界。自写那一版（粗估一两百行，落地时以实际为准，不当承诺），且天然可单测（纯函数算缩放夹取）。

### 2.1 已经逐字读到的那枚库的公开面（`2.16.0` 的 sources jar，不是 README）

| 事实 | 位置 | 对本设计的直接影响 |
|---|---|---|
| `Modifier.zoomable(zoomState, …)` 收**外部造好的** state | `Zoomable.kt:60` | 缩放态可以住在应用根，与"档位上提"同一形 |
| 滚轮缩放默认档是 **`EnabledWithCtrlKey`** —— 裸滚轮**不**缩放 | `Zoomable.kt:70` + `MouseWheelZoom.kt:12-53` | 必须显式选 `MouseWheelZoom.Enabled`，否则"屏上滚轮没反应"会被误读成库在桌面不生效 |
| `rememberZoomState(maxScale, contentSize, …, initialScale, bounce)` 公开，而**构造函数那枚 `initialScale` 是 private** | `ZoomState.kt:59` vs `:487-495` | 取证种子**不能**靠构造参数喂（见 §7 改判） |
| `reset()` / `changeScale(targetScale, position, animationSpec)` / `scale` / `offsetX` / `offsetY` 全公开 | `ZoomState.kt:164, 276, 75, 84, 91` | 翻页复位与"种子放大"都走公开 API；`zoomed` 真值 = `scale > 1f` |
| `changeScale` 内部 `coerceIn(1f, maxScale)` | `ZoomState.kt:281` | 种子超 `maxScale` 会**静默**落到上限 ⇒ 种子那侧自己先夹并念出来 |
| `zoomable-desktop:2.16.0` 的 **`desktopRuntimeElements-published`** 变体带 `foundation/runtime/ui/ui-util` 全 **1.12.1** | 该件的 `.module`（不是 POM，也不是 root 件） | 与本仓现量 `composeMultiplatform = "1.12.1"`（`gradle/libs.versions.toml:40`）**逐字同版** ⇒ §8 那条"compose 版本打架"风险基本量掉 |
| 同一颗件的 **`desktopApiElements-published`** 带 `kotlin-stdlib 2.4.20`，本仓 `kotlin = 2.4.10`（`libs.versions.toml:7`） | 同上 | **这条已经量掉了，不用等探针**：`javap -v` 读 `zoomable-desktop-2.16.0.jar` 里 `ZoomState.class` 的 `@kotlin.Metadata` 得 **`mv=[2,4,0]`** —— 元数据版本是 2.4.0 而不是 2.4.20，与本仓 2.4.10 同一 minor；class 文件 **major 65**（Java 21）与 `desktop/build.gradle.kts:16` 的 `jvmToolchain(21)` 逐字对上。stdlib **产物**版本被 Gradle 取高（2.4.20）仍会发生，但那是运行时同一族里的正常共存，不是编译面 |
| `zoomable-desktop-2.16.0.jar` 的 **64 颗 class 里非 `androidx` 的 `android/*` 引用 = 0**，只碰 `androidx/compose/{animation,foundation,runtime,ui}` 四包，且**无反射、无 ServiceLoader** | `javap -v` 全量扫常量池（`_trash/scratch-2026-10-08/zx/all.txt`） | 运行期"撞上 Android-only 类而 `NoClassDefFoundError`"这一族风险 ≈ 0 —— 这一条编译面抓不到，只有字节码看得见 |
| 滚轮那一路读的是 `PointerEventType.Scroll` + `awaitPointerEvent`（`DetectMouseWheelZoom.kt:17-18`），也就是桌面鼠标滚轮进 Compose 的**同一个通道** | 同上一轮 unpack 的 sources | 静态证据到这一格为止；**仍不等于验收** —— 禁鼠标令下没人滚过，屏上那半依旧只能由用户手动确认 |
| `zoomable-desktop-2.16.0.jar` = **167,898 B**（`zoomable-2.16.0.jar` 那 10,540 B 是 common 元数据件） | 本地下载后实测字节数 | 桌面真身不薄，先前那句"薄不等于不能用"里的"薄"指的是另一颗件，措辞已分清 |
| ⚠️ **root 件 `zoomable:2.16.0` 的 desktop 变体 `deps=[]`** —— 它只是 `available-at` 指针 | 同一轮实测 | 拿 root 件的依赖表读数当结论会得出"零依赖"这种假数；必须顺着指针读到 `*-desktop` 那颗 |
| `zoomable-2.16.0.jar` 仅 10,540 B（common 那枚） | 本地已下的 jar 字节数 | 真桌面变体是否解析得到仍以 `:desktop:dependencies` 为准，薄不等于不能用 |

⚠️ 探针是**第一步**，不是"落地时顺便"：本仓吃过一次教训（645 条判据全绿而屏上弹 AWT Error 框 —— 单测从不构造 `ImageLoader`）。这一枚库的"在桌面真能用"同样只能由起窗证明。

取的是 `Modifier.zoomable`（`Zoomable.kt:60`）这一枚，不取 `Modifier.zoomableWithScroll`（`:139`）与 `snapBackZoomable`（`:96`）—— 查看器那一层没有可滚的父容器，也不做"松手弹回"（放大态要能停住翻页）。这三枚都在同一颗 `Zoomable.kt` 里，选型时是一次读到的，不是猜的。

## 3. 形态（已拍板）

**全屏接管内容区 + 原浮层继续浮在右侧。**

```
┌ rail ┬──────────────── 大图查看器（占满内容区）───────────────┬ 340 浮层 ┐
│      │        （缩放 / 平移 / 页码 / 档位读数都在这层）        │  照旧覆盖 │
└──────────────────────────────────────────────────────────────┴──────────┘
```

- 浮层开着时，查看器的**可用宽度 = 内容区宽 - 340 - 46**。那个 46 是浮层左缘那道渐隐带（`DesktopGalleryMetrics.kt:204` `detailScrimWidth = 46.dp`，与 `:201` `detailWidth = 340.dp` 挂在同一个 `Row` 上，`VeneraDesktopApp.kt:469-507`）—— 只减 340 会让大图右缘压在渐隐带底下，而那条带今天就在屏上。这条是布局判据，不是观感描述。
- **宽度从哪来（今天没有这一档）**：应用根那枚内容区是 `Box(Modifier.weight(1f))`，**没有** `BoxWithConstraints`；全模块只有 `DesktopGalleryPostTile.kt:304` 那枚卡内的 `BoxWithConstraints` 在读 `maxWidth`。⇒ 新增的一档挂在**只在有层开着时才组合**的那颗包装 `Box` 上（见 §4 的键链那一格），不把约束挂到常驻的容器上（约束常驻 = 每次重组都重算，且与焦点那一格会纠缠）。
- 退出是**一层一层退**：`Esc` 在查看器开着时只关查看器（回到今天的浮层那一屏），再按一次才关浮层回墙。**这一条不新写一颗键盘件** —— 桌面已有一枚纯函数 `onDesktopOverlayKey(signal, onClose, onStep)`（`DesktopKeyNavigation.kt:48-59`，`Key.Escape -> onClose()` 在那儿），扩它两枚参数（`viewerOpen` + `onCloseViewer`）就是那"一层"，键与鼠标继续同一对 lambda。
- `←` / `→` 与浮层那两枚步进按钮**吃同一对 lambda**（批 46 立的"步进只有一个主人"），查看器不复制第二份算式。
- **滚轮档显式选 `MouseWheelZoom.Enabled`**（裸滚轮即缩放）。库的默认档是 `EnabledWithCtrlKey`（§2.1），选默认会让"滚轮没反应"被误读成"库在桌面不生效"；这一屏是满屏图、底下没有可滚的内容，让开滚轮不抢任何东西。

## 4. 组件与接口

| 件 | 位置 | 职责 | 依赖 |
|---|---|---|---|
| `DesktopGalleryViewer` | `desktop/.../gallery/ui/DesktopGalleryViewer.kt`（新） | 那一屏的布局：底图（`AsyncImage` + `Modifier.zoomable`）、页码读数、遮挡态、失败态、浮层避让宽度（读那颗布局纯函数的结果） | 必填参数，不吃 compositionLocal（覆盖层那三条教训） |
| `desktopViewerContentWidth` | `DesktopGalleryPreloadPolicy.kt` 新文件 DesktopGalleryViewerPolicy.kt（与预取那颗纯函数件同一族，但不合并：一颗件只说一件事） | 「浮层开着时这一屏能用多宽」—— 算式只这一处 | 窗口宽、`detailWidth`、浮层开没开 |
| 档位状态**上提** | 现在 `tier` 住在 `DesktopGalleryDetailPanel.kt:209` 的 `remember(post.uid)` 里，写点在 `:314`（那排分段控件按下去） | 查看器与浮层**必须共用同一枚档位**，否则两条轨（预取那批刚踩过） | 应用根持有，两边读同一颗。⚠️ 浮层签名要多一枚必填档位 ⇒ **真调用点只有一处**（`VeneraDesktopApp.kt:492`）；另外两处命中（`DesktopGalleryDetailTest.kt:190`、`DesktopKeyNavigationTest.kt:132`）是**扫字面量的判据**（断言根文件里含 `DesktopGalleryDetailPanel(`），不是调用点 —— 别照"三处调用点"去铺，那是一句没回代码核的旧账 |
| 键链**只能有一处** | 今天那三件套（`focusRequester` + `focusTarget` + `.onKeyEvent`）挂在浮层那颗 `Row` 上（`VeneraDesktopApp.kt:469-477`），而现有判据 ⑥ 钉的是"**全模块只有一处** `.onKeyEvent {`"（`DesktopKeyNavigationTest.kt:136-159`） | 把这三件**上提**到一颗包着"查看器 + 浮层"的 `Box` 上，且那颗 Box 仍只在 `selected != null` 时才组合 ⇒ 一处链服务两层，判据 ⑥ 不必放宽。焦点那一路的 `KEYNAV=` 读数与两次重试照搬（`VeneraDesktopApp.kt:451-465`） |
| `zoomed` 真值 | 库的 `ZoomState.scale`（公开 `val`，`ZoomState.kt:75`）⇒ `zoomed = scale > 1f`；`ZoomState` 本身与档位**同样上提到应用根**，查看器吃必填参数（覆盖层那三条教训） | 放大时连播不许抢页 —— 把 `VeneraDesktopApp.kt:410` 那枚假 `false` 换成真读数 | 转交共享的 `GalleryAutoPlay.shouldAdvance` |

**不做**（明确划出去，避免范围膨胀）：动图逐帧（第二批）、视频播放、下载、手势左右滑动翻页、独立第二窗口、共享元素转场、`galleryBackdrop` 的设置入口。

### 4.1 进查看器时落在哪一档（这一条要你拍板，我给推荐）

现场读数：桌面本来就有**三档**，而它们在屏上的名字叫「预览 / **大图** / 原图」（`DesktopGalleryDetailPanel.kt:650-653` `panelLabel()`），档位表由 `desktopDetailTiers(post, masked)`（`DesktopGalleryDetailPolicy.kt:98-105`）给 —— 被挡的一档都不摆、视频撤「原图」、站方没给 `file_url` 也撤「原图」；当前档不在表里时已有 `shownTier` 那枚兜底（`DesktopGalleryDetailPanel.kt:216`）。

- 你说的"跳转到大图"在**屏上是有名字的**：`SAMPLE` 那一档就叫「大图」。而现有的入口档纯函数 `desktopDetailInitialTier(previewQuality)` **永远给不出「原图」**，只在预览/大图之间切（`DesktopGalleryPreloadPolicy.kt`）。
- **推荐（拍板项 A）**：进查看器时把共用档位**升到「大图」**（该卡没有「大图」这档就按现有 `shownTier` 兜底降档），「原图」仍要手动点。代价说清：一屏一开就取站方 large 档（比缩略大数倍），且**预取会跟着这一档走**（批 47 那颗吃的就是同一枚 tier）⇒ 预取的字节数同比涨。
- **不推荐的那条**：入口直接吃「原图」。原片在这一族站上常有 5000px 级，而桌面动图结构性无解码器（§1 那条）—— 撞上动图原片就是白屏；这一条留着等第二批动图落地后再议。
- **备选（拍板项 B）**：查看器不升档，仍按设置 `previewQuality` 推（今天那颗纯函数的原语义，零新增代价，但"点卡进大图"可能给你看的是缩略档放大后的马赛克）。

⚠️ 无论选哪条，**查看器与浮层共用同一枚 tier** 这一条不许改（两条轨在批 47 刚踩过：预取算地址与消费点取地址必须同一颗档）。

## 5. 数据流

1. 墙上点卡 → `onPick(DesktopWallSelection(cards, index))`（**这条今天就有，不改**）。
2. 应用根：`selected != null` 且查看器开关为真 → 渲染 `DesktopGalleryViewer`，档位取那枚**上提后的**共享状态。
3. 取哪一枚地址：**转交** `desktopDetailTierUrl`（前两档逐字转交共享的 `GallerySettingsModel.wallUrl`），不重述；入口档按 §4.1 那颗纯函数定（A 案 = 升到屏上叫「大图」的那档）。
   查看器那一层的 `contentScale` **恒为 `Fit`** —— 浮层今天是「原图档才 Fit、其余 Crop」（`DesktopGalleryDetailPanel.kt:272`），Crop 会把边缘裁掉，而放大看图时"被裁掉的那部分"是看不回来的。
4. 遮挡卡 → 不发请求，摆遮挡态（与墙上、浮层同一条规矩）。
5. 翻页 → 同一对 `step` lambda → `selection.index` 变 → 查看器换图、缩放态**复位到 1x**（带着放大态翻页是假读数）。
6. 预取（批 47 那颗）继续吃同一份 `SingletonImageLoader` ⇒ 翻到的下一张应当命中缓存。

## 6. 错误处理（每条都得能在屏上念出来）

| 情况 | 念什么 |
|---|---|
| 站方没给这一档地址 | 「站方没给这一档的图」（沿用浮层那句，两处同一句） |
| 取图失败 | 失败原话 + 本机那半句（反搜那批定的写法），不静默变白屏 |
| 被分级挡住 | 遮挡态，且**不发请求** |
| 库探针失败 | 走自写兜底那一版；**不允许**"装了但没接"（那是假开关） |
| 依赖装不上 | 直接改走自写兜底，并把"为什么不用库"写进本节（带读数） |
| 放大种子越界（`<1f` 或 `>maxScale`） | 夹到合法区间，并把"给的 / 夹后的"两个数一起念到 stdout —— 库的 `changeScale` 会静默落到上限，不念就成了假读数 |

## 7. 验证与验收

- **探针前置读数（已量，省掉一次瞎试）**：
  - 本机到 `repo1.maven.org` **直连是通的** —— 连拿同一枚 `.module` 三次全 `http=200`（0.24s / 0.36s / 2.26s）。⇒ "依赖下不下来"不属于 [[reference-local-network-egress]] 那条"Google/booru 全不通"的账，**不必**给 Gradle 加代理参数（`gradle.properties` 里今天也没有任何 `systemProp.*proxy`）。
  - 本地 gradle 缓存（192 个组）里 `me.saket.telephoto` **只有 root 与 `-android` 那 8 颗，没有任何 `-desktop` 件** ⇒ "选 telephoto 就能离线"这句是假的：**三族里任何一族**都要现拉桌面件。选型的网络成本相同，这条不再构成偏好。
  - `settings.gradle.kts:20-22` 的仓库表带 `mavenCentral()` ⇒ 坐标写进去就能解析，不需要新增仓库声明。
- **依赖探针**（第 1 步，先于功能）：`desktop/build.gradle.kts` 加依赖 → `:desktop:dependencies` 解析到 `desktop` 变体 → **`:desktop:compileKotlin` 过**（这一步才量得出"编译器 2.4.10 能不能读 `kotlin-stdlib 2.4.20` 产出的元数据"，§2.1）→ 起窗 `gradlew :desktop:app -Pproxy=127.0.0.1:7890 -PstartDomain=GALLERY -Pviewer=1 -PviewerZoom=2` → 屏上那一帧确实被放大了（走公开的 `changeScale`，落进与滚轮同一枚 `scale` ⇒ 放大态**画得出来**这条能被证明）。装不上/画不出来 ⇒ 走自写兜底，两条路都算完成这一节。
  - **探针证明不了的那半**：滚轮事件本身在 skiko 上是否投递进 Compose 手势链 —— 禁鼠标令下没人去滚，量不了。这一半只有"显式档位存在"（判据 8）+ 库里 `DetectMouseWheelZoom.kt` 确在桌面变体内（§2.1 已读到源文件）两枚**间接**撑着的证据，**不写成验收结论**。
- **判据（TDD，先红后绿）**：
  1. 取哪一枚地址**转交** `desktopDetailTierUrl`，桌面不重述（数次数 + 死支负锚点，批 46 加宽后的形状）。
  2. 档位两头同一颗状态：`tier` 在应用根一处声明，浮层与查看器各读它（浮层内不再 `remember` 自己的那份）。
  3. 浮层避让宽度那颗纯函数：浮层开着 = 窗口宽 - `detailWidth`(340) - `detailScrimWidth`(46)；浮层关着 = 全宽；窗口窄到不足一档时**不许变负数**（这条今天没有尺子也没有算式）。
  4. `Esc` 一层一层退：**扩** `onDesktopOverlayKey` 那枚既有纯函数（`viewerOpen` + `onCloseViewer` 两枚参数），判式钉**次序** —— 查看器开着时 `Escape` 落到 `onCloseViewer` 而**不是** `onClose`，查看器关着时才落到 `onClose`；另加一条"全模块仍只有一处 `.onKeyEvent {`"（现有判据 ⑥ 的原样复用，不许放宽成两处）。
  5. 步进只有一个主人：`←`/`→` 与浮层按钮吃同一对 lambda（数出现次数，批 46 那条尺子的同族）。
  6. `zoomed` 交真值：`GalleryAutoPlay.shouldAdvance(zoomed = <真读数>` 且翻页时缩放复位。
  7. 遮挡那张进查看器不发请求（负向：不出现对该地址的 `AsyncImage`）。
  8. 滚轮档**不吃库的默认值**：查看器那处 `Modifier.zoomable(` 必须显式带 `mouseWheelZoom = MouseWheelZoom.Enabled`（数出现次数，缺省即红 —— 默认档是 `EnabledWithCtrlKey`，漏了会让"滚轮没反应"被读成"库在桌面不生效"）。
  9. 放大种子那枚夹取纯函数：`raw < 1f` / `raw > maxScale` / 正常三档各过一遍，**返回值与 stdout 念的原值是两件事**（夹了不许装作没夹）。
  10. 入口档那颗纯函数（拍板项 A 落地时才有）：档位表里有「大图」⇒ 升到「大图」；只有「预览」⇒ 留在「预览」；**空表（被挡那张）⇒ 一档都不升**。不许写成"直接 `SAMPLE`"那种跳过档位表的版本（视频与原图那两条撤档规则会被它绕过去）。
  11. 那行 `D_落点` 补一枚 `种子=` 字段（从 flag 现取，不动键链也不动 `pickStamp`）：判式正反各钉一次 —— 传了 `-Pdetail` ⇒ 那行含 `种子=detail`；没传 ⇒ 不含。
- **图证**：`-Pviewer=1` 种子起窗 → `get_window_state` 拍**三帧**（1x、放大后、以及"两层都关掉之后在搜索框里打得进字"那一帧 —— 焦点上提那格唯一的可见证据），用完 `taskkill`、残留归零；禁鼠标令照守。
  - **放大那一帧的种子机制（本节已改判一次）**：原写"起窗就把缩放态设到给定倍数"，而 `ZoomState` 构造函数的 `initialScale` 是 **private**（`ZoomState.kt:59`），从公开构造面喂不进去（§2.1）。改成走**公开的** `changeScale(targetScale, position, animationSpec)`（`ZoomState.kt:276`）：`-PviewerZoom=` 进来后，在查看器进入那一拍的 `LaunchedEffect` 里 `changeScale(seed, Offset.Zero, tween(0))`。
  - 这样 `initialScale` 在生产与取证两条路上都保持 `1f` ⇒ `reset()`（翻页复位，`ZoomState.kt:165` 是 `_scale.snapTo(initialScale)`）语义一致，不会让取证跑出一条生产里不存在的复位落点。
  - 种子**进 `changeScale` 之前先自己夹**到 `[1f, maxScale]` 并把"给的倍数 / 夹后的倍数"一起念到 stdout —— `changeScale` 内部 `coerceIn(1f, maxScale)`（`ZoomState.kt:281`）会**静默**落到上限，不念就成了"我给了 8 倍屏上只有 5 倍"那种错读。
  - 键盘修饰键那一族（Ctrl+滚轮）这次**不写进承诺**：禁鼠标令下没人去按，测不到就是不测到。
- **账本**：`galleryAnimated` 那句点名措辞要改（这一屏有了大图位，但**还不动逐帧**）；`galleryBackdrop` 那句仍真（有那层背景但还不能改）。12 枚那本账维持 **6 能改 / 4 点名 / 2 结构性**，枚数不动。

## 8. 风险与对策

| 风险 | 对策 |
|---|---|
| ~~compose 版本打架~~ / ~~元数据吃不下~~ 两条都**已量掉**：`desktopRuntime` 里 compose 全 1.12.1（与本仓逐字同版），`ZoomState.class` 的 `mv=[2,4,0]`、class major 65（与 `jvmToolchain(21)` 对上）（§2.1） | 探针仍照跑，但这两条不再是"未知的雷"；真余下的风险只有"滚轮事件投递"那一半（下一行） |
| 滚轮事件在 skiko 上不进 Compose 手势链 —— **这一半禁鼠标令下量不了** | 静态证据已加到两枚（§2.1：64 颗 class 里 `android/*` 引用 0、无反射；滚轮读的是 `PointerEventType.Scroll` 那个桌面同通道）⇒ **风险降了但仍不是验收**。屏上那半由用户手动滚一下确认，交付时在缺口账上明写这一条 |
| 放大态与浮层避让宽度互相抢 | 避让算式收成一颗纯函数（判据 3），两处不许各写一遍 |
| 翻页时缩放没复位 ⇒ "看着换了张图其实还放大着" | 判据 6 明钉复位 |
| 只测不拍 ⇒ 判据全绿而屏上没动（本仓栽过三次） | 图证是**验收项**，不是收尾装饰 |
| 拍板项 A 会让**预取也跟着「大图」档走**（批 47 那颗吃的就是同一枚 tier）⇒ 图片字节同比上涨 | 这一条**当场认下来并写进 §4.1**，不偷偷加：预取的档位来源不变（仍是同一颗状态），要收就整条收回 B 案；另给预取补一行 `D_预取` 读数（挂账里本来就有这条），让"多取了多少"看得见 |
| 焦点上提到那颗 Box 之后，层全关时它可能还攥着焦点 ⇒ 搜索/凭据那五枚 `TextField`（`DesktopGallerySearchPane.kt:297`、`DesktopGallerySettingsPane.kt:359,572`、`DesktopGelbooruCredentialPanel.kt:119,125`）打字被吞 | Box 仍只在 `selected != null` 时才组合（层全关它就不在组合树里，焦点自然交回）；落地时**图证要拍一枚"关掉查看器与浮层之后在搜索框里打得进字"**的那一帧，别只测键那一路 |

## 9. 与既有挂账的关系

- 批 44（`app/` 那一行受保域改动）**不阻塞**这一屏：查看器取的是站方图地址，pixiv 头像那一格是另一条链。
- 动图（第二批）落在这同一屏上：解码器一颗自有件 + `galleryAnimated` 接上消费点。
- 本批顺手把 `zoomed` 那枚假读数换真，是"屏上说话必须是实话"那一族的账，不额外扩范围。
- **"点击图片"这一句的范围已核**：桌面今天**没有**接进 UI 的漫画阅读器 —— `desktop/.../ReaderProbe.kt` 是 S0-8 那枚性能探针（自带 `application {}`，全模块**零引用**），不是那一屏。⇒ 这一批的"图片"就是**画廊那批图**；漫画页面要等桌面真长出阅读器那一屏再谈，不在这个 spec 里。
