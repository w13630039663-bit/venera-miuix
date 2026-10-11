# 批 61 · 大图那一屏的那层底接上消费点（2026-10-09）

`galleryBackdrop` 从「屏上点名缺席」搬进「屏上真能改」：设置那一屏第八行、四档、写口与消费点一次到位。
12 枚画廊偏好的下落因此从 **7 / 3 / 2** 变成 **8 / 2 / 2**。

## 1. 落点

| 面 | 落在哪 |
|---|---|
| 读口 | `DesktopGalleryPreferences.galleryBackdrop`（`readEnum`，默认 `GLASS`，键 `pref_gallery_backdrop`）—— 从前就有 |
| 写口 | 新 `setGalleryBackdrop`：先落盘、再推那条流（与 Android 侧同形状；少第二步就是"改完要重启才变"） |
| 换算 | 新颗件 `DesktopViewerBackdropPolicy.desktopBackdropColor(backdrop, seat)`：三档吃枚举自带的 `argb`，`GLASS` 交回调用方那枚坐底色 |
| 消费点 | `DesktopGalleryViewer` 那层 `.background(...)`（**必填形参**，不给默认值）；集成方 `VeneraDesktopApp` 收流并递进去 |
| 屏上 | 第八行四颗单选，档位表 `backdropTierLabels` = 共享枚举的全部，一档不删 |

## 2. 判据 7 条 + 扰动 7 笔（各红各的尺子）

`DesktopViewerBackdropTest` 7 条；每一笔扰动都跑**全量**（750 条），红了谁逐笔记（`_qa/b61_perturb.js` + `_qa/b61_perturb.sh`，跑完逐字还原并复跑全量 0 红）：

| 扰动 | 改坏什么 | 红了谁 |
|---|---|---|
| P1 | `GLASS` 不再交回坐底色而交回纯黑 | ① 四档各算各的色（四档撞成三色） |
| P2 | 把 `0xFF121212` 另写一遍进桌面这颗件 | ② 三档的色来自枚举而不是桌面另造 |
| P3 | `GLASS` 标签撤掉「桌面：不透明底」那半句 | ③ 四档标签齐而 GLASS 那枚说实话 |
| P4 | 把已过期的点名句子「桌面的浮层没有那一层背景可以换」请回代码行 | ④ 那一枚已从点名搬进能改而旧句子下架 |
| P5 | 写口只落盘、不推流 | ⑤ 写口两步都真 ＋ `DesktopGalleryPreferencesTest` ⑧（同实例读不到新值） |
| P6 | 那一层绕开共用件、直接吃主题底 | ⑥ 那一层只从这颗件拿色 ＋ `DesktopUnusedInternalTest` ①③（那颗件当场零调用） |
| P7 | 调用点写死 `backdrop = GalleryViewerBackdrop.GLASS` | ⑦ 集成方递进去的是那条流而不是常量 |

⚠️ **P7 是本批最值钱的一笔**：⑥ 只钉"那一层吃这颗件"，管不到**谁把档递进来**。调用点写死一枚常量时
⑥ 全绿，而屏上第八行当场变回假开关（按下去存盘成功、那一层永远画同一色）。这一条是本批**动手之后自审补上的**，
不是开工前想到的 —— 判据形状：从 `val X by settings.galleryBackdrop.collectAsState()` 那句把变量名**抽出来**，
再要求调用点递的正是那一名（写死 `backdropTier` 这个名字也行，但改名重构会把判据一起改红，
而"名字换了、线没断"与"线断了"是两句话）。另钉两头：负向「调用点不许出现 `backdrop = GalleryViewerBackdrop.`」、
反向「那一枚形参不许带默认值」。

## 3. 自审抓到的一条错引（改判，不是改措辞）

第一版在三处注释里把 GLASS 的"为什么"记成了 **`S0-7 探针量过：桌面采不到底下那面墙`**。回读那份探针文档，
S0-7 的读数**恰恰相反**：两枚 `isRuntimeShaderSupported()` 都 `true`、`SkikoRuntimeShader` 构造成功、
`BlurEffect.isSupported() = true`，而它自己写着「gate 说 true 不等于画面对，还差一次像素级确认」—— 那一步至今没做。

正确的两枚读数（现已写进 `DesktopViewerBackdropPolicy` 与 `DesktopGallerySettingsSpec`）：

- **窗口级那一路判负**：S0-6 的 `WindowStyle(backdropType = Mica)` 靠反射拿 skiko `SkiaLayer.delegate`，
  新版内部结构已改 ⇒ 抛在 AWT 线程被吞、三档材质逐字节相同全是死灰。对岸那枚 `blur-behind` 在桌面**没有承载件**，这条量过。
- **应用内那一层没量**：`miuix-blur-desktop` 与 `backdrop-desktop` 都在桌面 classpath 上（打包清单里各一颗），
  差的是**像素级确认**，不是能力。⇒ 本批说的是"没走那条路"，**不是**"那条路做不到"。

屏上那句 caption 仍然成立（它说的是"没有对岸那种**窗口**模糊"，那是 S0-6 的账），一字未改。
⇒ 下一件里有一条现成的：**把 S0-7 §六 欠的那一步像素确认补上**，量得出真模糊就把 GLASS 做成真模糊。

## 4. 图证三帧 + 一条量不到的

| 帧 | 读数 |
|---|---|
| `pref_gallery_backdrop=WHITE`，`--start-domain=SEARCH --query=animated_gif --detail=1 --viewer=1` | 大图那一屏图两侧那一条是**白**（chrome 仍是暗色） |
| 同一枚种子、同一张卡（Safebooru · ID 12340628），只把档换成 `BLACK` | 那一条整片变**黑** |
| `--start-domain=SETTINGS` | 设置那一屏第八行「大图那一屏的底」四颗单选都在，选中「跟着主题（桌面：不透明底）」；页顶那句已是「这一屏管画廊那**八枚**档」 |

⇒ 这一枚的行为是**量到的**：一次只换一个变量（盘上那枚档），屏上那一层跟着换色。

⚠️ 一条量不到：本机屏幕当时是**锁着**的。于是两条数值路都不通 ——
`_qa/b61_grab.ps1` 的 `CopyFromScreen` 只拍到壁纸（四个采样点全是 `R32 G66 B203` 那类蓝色，与窗口无关），
而应用自带的 `-Pshot` 守卫**正确地拒绝产出**：`D_截图 无效：中心点落在 hwnd=… 标题「Backstop Window」类名「LockScreenBackstopFrame」… 不产出假证据`。
⇒ 所以这一批的图证只有 WGC 逐窗帧（`occlusionSafe=true`，锁屏也拍得到），**没有像素数值**。
别把这两条路混着写：数值像素那一格等解锁再量。

## 5. 数字

判据 **750 / 97 类 / 0 失败**（三方口径：`tests=` 之和 750 = `<testcase>` 数 750 = 源码 `@Test` 数 750）；
`docs/rounds/desktop-ruler-catalog.md` 补一行 `DesktopViewerBackdropTest.kt | 7` ⇒ `node _qa/check_ruler_catalog.js` = **0 处漂移**；
`desktop/src/main` 84 颗 / 16,379 行；打包 desktop jar **4,213,074 B**（内含 `DesktopViewerBackdropPolicy*` 两颗类，classpath 100 条 missing=0 orphan=0）；
交付包 `_qa/desktop-exe-2026-10-09-b61/`（215 MB）；prefs 跑前备份、跑完还原；窗口残留 0 枚。

顺带把五处过期计数与措辞对齐（`7 能改 / 3 点名` → `8 / 2`）：`DesktopGallerySettingsSpecTest` ①②⑦、
`DesktopGalleryPreloadTest` ⑤、`DesktopAutoPlayTest` ③、`DesktopGalleryPreferencesTest` ⑧⑩ 的名字与文案、
以及设置那一屏页顶那句**上屏**的文案。
