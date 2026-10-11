# 剩余功能重规划 + 哪些改用现成品（2026-10-10）

用户那句「重新规划整个项目还没做的功能，看有没有能用 GitHub 现有项目代替的」。
这份是**重排后的排期**，不是实施记录。两路只读调研（缺口盘点 + 现成品逐族量）的结论都在这里，
每条带证据；**所有 Maven 坐标都现查过 `repo1.maven.org` 的 `maven-metadata.xml`**（六颗全部命中，见 §3）。

## 0. 一句话结论

**能拿现成品省的都是"能力层"那几件（下载落盘、归档直读、超大页分块、网页登录的回调服务、崩溃上报、取色），
省不掉的恰恰是本项目最贵的两族：源脚本生态与反搜/站方 SDK** —— 前者只有 Suwayomi 真跑得动而它的代价是 321 MB sidecar + MPL 文件级 copyleft，后者 JVM 侧根本没有产物（查过一圈，pixiv 那颗唯一像样的是 **AGPL-3.0**，直接判负）。
所以答案是：**换库不能当主线，主线仍是"搬对岸 + 补入口"；但有 4 件可以立刻靠现成品少造一大截。**

## 1. 先纠三处文档失真（不纠，后面每一档排期都建在沙上）

| 那句假话 | 证据 | 该怎么改 |
|---|---|---|
| `README.md:256` 与 `README.en.md:256`「动图：桌面 classpath 上没有 gif 解码器，所以那一档在桌面上**不解动**」 | `desktop/…/gallery/ui/DesktopAnimatedImage.kt:21` `import org.jetbrains.skia.Codec`、`:58` `Codec.makeFromData(Data.makeFromBytes(bytes))`；而 `DesktopGallerySettingsSpec.kt:175` 自己写明"停在第一帧只对**墙上与浮层**那一路成立" | 前半句仍真（`coil-gif` 无 JVM 变体），后半句已假：**大图那一屏是自己逐帧解的**。改成"不走 Coil 解码器，走桌面自持的 Skia `Codec`；墙上与浮层仍停在首帧" |
| `docs/HANDOFF-2026-10-04.md` §10 标题「判据（**743** 条）」 | 现量 `@Test` **1025** / 132 类 / 139 颗测试文件（`node _qa/three_way_counts.js`） | 改成现数，并按既定口径"数字每次改判都回代码现取" |
| 排期表 `clever-mesa-quail.md` §1 五格 + §2 五行整张过期（批 76/77/78/80/81/82/83 已全落地） | 同一文件 `:70-99` 就是它们的落地记录 | 划掉旧格子，指向本文件作为新的排期入口 |

⚠️ 这三处都属于本项目反复中招的那一族：**对外文档说"还没有 X"而 X 早已落地**（反方向也要防）。已列成批 84′（见 §4）。

**批 84′ 已纠完（2026-10-10 同日，账在 `readme-stale-claims-2026-10-10.md`）**，两处与上表略有出入，按落地写：
① README 两份那句改成了点名"墙上与浮层"的那一句，并新立 `DesktopReadmeStaleClaimsTest`（2 条）钉住 ——
上表写"改成…首帧"仍然对，只是**改动带了一把尺子**，所以判据总数从 1025 变 **1027**（133 类）。
② §10 那枚数字**不再是靠人回头改**：`_qa/check_ruler_catalog.js` 现在也拿现跑 XML 比 §10 那两行的三枚数，
第一次跑就报出 `条=743 实=1027 / 类=96 实=133`（那句从 §16 表行 96 起就冻着，其后 34 行批次账各自现数了总数）。
③ `clever-mesa-quail.md` 顶部加了过期横幅并**逐格点名**哪几格被哪一批吃掉（§1 六格全落地、§2 三块落地两块仍缺），
而不是划掉 —— 那份文件留着当"当时怎么量出来的"记录。
⚠️ **实盘出五处，不是三处**：另两处是 README 里 Android 侧用例数同一件事写了三个数（`109 个文件（106 类）/ 814 用例` 与另一处的 `104`），
以及手册 §0「下一件」那行还写着"当前第一件 = 批 77 · 下半"（那一格 10-09 就落地了）。全清单与证据在 `readme-stale-claims-2026-10-10.md` §1。

## 2. 缺口全量（按"能不能靠现成品"分档）

### A 档 · 立刻用现成品，能少造一大截（4 件）

| 缺什么 | 现成品（**坐标已现查**） | 拿来省掉 | 接进来的代价（本项目实测过的） |
|---|---|---|---|
| **章节下载 / 图片落盘**（`README.md:253`、`DesktopGalleryHomeSections.kt:235` 那句"存图落点还没接"） | Coil 3.6.2 自己的 `DiskCache`（`coil-core-jvm` 已在 classpath）+ `okio`；**不引第三方下载库**（KronDownload / Downstorm / KDownloader 全无 desktop 声明） | 自造落盘队列与缓存目录管理 | 前置**已在批 79 落地**（`DesktopImagePipeline.kt:62-65` 装的就是 `SingletonImageLoader` + `OkHttpNetworkFetcherFactory`，`DesktopCoilBytes.kt:33` 吃同一份单例）⇒ 这一格缺的只剩**落盘那一层**；线程边界必须带 `kotlinx-coroutines-swing`（批 47 那枚 AWT Error 就是它）；**Coil 的盘缓存在 `%LOCALAPPDATA%\Temp\coil3_disk_cache`，不在数据根里**（批 79 抓到，取证时会误判"命中缓存"） |
| **归档 / CBZ 直读**（Android 侧明写不做 `README.md:244`） | ~~zip4j 或 commons-compress~~ → **不引库：JDK 自带的 `java.util.zip` 就是这一格要的现成品**（2026-10-10 批 86 现量改判，读数在 `desktop-gallery-browse-history-2026-10-10.md` §1.1：对岸两处已在用它、交付包镜像里 43 条 zip 类、`java.base` 导出这一包 ⇒ jlink 不必补票、包体 0 增量；而 7z/EPUB 是明写的结构性不做） | 解包与流式随机读整颗件 —— **本来就造好了** | 代价为零：唯一的"接进来的代价"那一栏（jlink 现量 + commons-compress 的传递依赖与包体）随改判一起消失。⚠️ 桌面今天**没有**归档读写点：唯一的 zip 消费点会是备份打包，而那一件在裁决表里是「W6 用户明确不点头」⇒ 引一颗库进来就是**零调用点的一颗依赖**（本仓那一族的正解是不装） |
| **超大页分块 / 大图增强**（大图屏已落地，缺 subsampling 与旋转联动） | `io.github.panpf.zoomimage:zoomimage-compose-coil3-desktop`（Apache-2.0，**最新稳定 1.6.0，1.7.0 只有 alpha01**）或 `me.saket.telephoto:sub-sampling-image-desktop:0.19.0`（Apache-2.0） | 自写分块解码与手势联动 | 两者都不引 material3（✓）；zoomimage 要 `coil-compose 3.5.0`，本仓 3.6.2 ✓；**换 zoomimage 等于把批 66 那套键盘缩放判据重做一遍** —— 建议只补 Telephoto 的 subsampling，不换壳 |
| **网页登录 / OAuth**（桌面完全缺；`verdicts:40-41` 点名 `WebLoginScreen.kt:9` 吃 `android.webkit`） | 轻路：**零新依赖** —— `Desktop.browse` + JDK 自带 `com.sun.net.httpserver` 收 loopback 回调；重路：`dev.datlag:kcef:2025.03.23`（Apache-2.0，Chromium 原生走 `me.friwi:jcef-natives-windows-amd64`）或 JavaFX `javafx-web`（LGPL+CE，要 `JFXPanel`） | 回调服务、Cookie 落地、超时与取消那一圈 | 轻路要 **`modules(...)` 补 `jdk.httpserver`**（与 `java.sql` 同一坑，`desktop/build.gradle.kts:236`）；重路 JCEF 是 AWT 重型件，**压不进 Compose 浮层**，只能占一整扇窗，且带 native 分发体积 |

### B 档 · 不用买 —— 平台自带，只是我们没接

- **横向翻页 / 双页**：Compose Multiplatform 自带 `Pager`（`foundation-desktop` 里齐全，本仓 gradle 缓存可查）。Android 侧那套阅读器手势搬不过来 ≠ 桌面没有翻页件。
- **音量键翻页**：~~AWT `KeyEvent`（桌面已有那一层 `.onKeyEvent`，全模块只许一处，扩展它即可）~~ ⇒ **批 87·下半量完之后改判**：`.onKeyEvent` 那条口径现在是「**一屏一处**」（阅读那一屏自己一条链，前提钉在"换域收浮层"那句上）；而 `Key.VolumeUp/Down` 是合成哨兵、真按键永远对不上它，唯一可能的路是 `awtEventOrNull?.keyCode`（VK 175/176）—— 那条**通不通要一次真按的读数**，本机没有 OS 级输入。这一批交的是量具（`D_按键 … 原始=…`），不是那条分支；细节见 `desktop-reader-keys-2026-10-11.md` §1。
- **裁边距 / 反色**：Skia 层，大图屏已在用。
- **虚拟网格性能**：`LazyStaggeredGrid*` 都在 `foundation-desktop`；真正的账在"LazyVerticalGrid × verticalScroll 整屏崩"那条不变式上（本项目已立判据），不是缺库。

### C 档 · 结构性没有现成品，自研是对的（别找了）

- **源脚本生态**：Mihon/Tachiyomi 扩展载体是 **APK**，宿主读 `android.content.pm.PackageInfo` + `dalvik` 的 `BaseDexClassLoader` ⇒ JVM 上结构性不通；Kotatsu / TachiyomiX 全 Android（Central 零命中）；ComicCatcher 主仓是 Python OPDS，不是 JS 规则引擎。**唯一真跑得动的是 Suwayomi-Server**（MPL-2.0，活跃，它自己重写了那两枚 dex 类并用 GraphQL 对外）—— 代价是 **321 MB sidecar**（其 `windows-x64.msi` 实测 336,352,325 B，自带 JRE）+ Source/Chapter 两套轨要跟我们现有的 `comic_history`/收藏两本账对账 + MPL 文件级 copyleft。**建议：只借它的接口形状，不搬它。**
- **反搜与站方 SDK**：SauceNAO 侧 JVM 产物零颗（查过 luk1337/SauceNAO=Android、saucenao-client=JS、Go/Swift 各一）；booru 侧 flexbooru/Yummybooru/GelbooruClient 全 Android；pixiv 侧唯一像样的 `xyz.cssxsh.pixiv:pixiv-client` 是 **AGPL-3.0** ⇒ 对本项目判负。**自研那几颗 client 继续自研。**
- **简繁转换**：`opencc4j:1.13.1` 存在，但仓库里那张 `assets/opencc.txt`（3980 对）已接上且 84.5% 是它的天花板 ⇒ 不换。

### D 档 · 桌面根本没入口的那些，不登记为缺口

`verdicts:93-96` 那条判断继续有效：**"为不存在的按钮道歉＝把没搬说成坏了"**。
应用内更新（Android 整颗接线）、后台追更（WorkManager）、分享语义、运行时权限、折叠/挖孔、常亮 —— 桌面侧不排。

## 3. 现查读数（六颗坐标，`maven-metadata.xml` 的 `latest`/`release`）

```
io/github/panpf/zoomimage/zoomimage-compose-coil3-desktop   1.7.0-alpha01（稳定档取 1.6.0）
me/saket/telephoto/sub-sampling-image-desktop               0.19.0
dev/datlag/kcef                                             2025.03.23
com/materialkolor/material-kolor-jvm                        5.0.1
net/lingala/zip4j/zip4j                                     2.11.6
org/apache/commons/commons-compress                         1.28.0
```
另有 `io.sentry:sentry:8.16.0`（MIT，**普通 jar**）可做桌面崩溃上报；`update4j` 两档都 2022 年停更 ⇒ 桌面自更新**不引它**（那条本来也归 D 档）。
取色那颗（`material-kolor-jvm`）先别接：桌面今天走 compose-fluent 的调色，**引入前要先量它会不会顶掉 material3 的钉版与 fluent 的主题来源**。

## 4. 重排后的批次顺序（建议）

| 批次 | 内容 | 为什么排这 |
|---|---|---|
| **批 84′** ✅ 已落地（2026-10-10，手册行 130；实盘出**五处**不是三处，见 `readme-stale-claims-2026-10-10.md`） | 纠 §1 那三处文档失真（纯文档，两份 README 同步 + 手册 §10 标题 + 排期表指向本文件） | 最便宜且最要紧：不清它，后面每条排期的"还剩多少"都是错的 |
| **批 83.1** ✅ 已落地（2026-10-10，手册行 131；帧与 stdout 都在 `desktop-source-disable-seed-2026-10-10.md` §3） | `--disable=` 取证种子（源管理那一格挪进②档） | 小、纯桌面、无新依赖；把批 83 欠的那格图证补成真的 |
| **批 85** ✅ 已落地（2026-10-10 同日第二批，手册行 132；落点、图证与台架在 `desktop-image-save-2026-10-10.md`） | **章节下载 / 图片落盘**（A 档第 1 行的**后一半**） | 它是两件事的地基：下载与"存图落点"；且**不引新依赖** |

⚠️ **批 85 的前提当场量掉一半**（本行写下来不到一小时，正是本仓那一族"文档说还没做而 X 已落地"的新样本）：
"桌面接 Coil 单例那一层"**早在批 79 就落地了** —— `gallery/data/DesktopImagePipeline.kt:62-65` 就是
`SingletonImageLoader.setSafe { ImageLoader.Builder(context).components { add(OkHttpNetworkFetcherFactory(callFactory = { clientForImages(engine.okHttpClient) })) } }`，
而 `DesktopCoilBytes.kt:33` 已在吃同一份单例。所以批 85 真正缺的只有**落盘那一层**：
把页字节写进一个用户可枚举的目录（`GallerySaver` 因 MediaStore/通知在 `desktop/build.gradle.kts:107` 排除，桌面得自持一颗）、
以及首页「下载」那一行随之点亮。Coil 自己的盘缓存在 `%LOCALAPPDATA%\Temp\coil3_disk_cache`（批 79 量过）——
**那是缓存不是落盘**，别拿它当"下载好了"。
✅ **同日批 85 落地回填**：落盘那一层接完了 —— `gallery/data/DesktopImageSaver.kt` 那颗自持件 +
浮层第六枚「存到本机」+ 首页「下载」那一行的枚举读侧 + `--save` 取证种子；判据 30 条、台架 14 笔、
两帧图证都在 `desktop-image-save-2026-10-10.md`。
⚠️ 这一格里**仍然没做**的是「漫画章节下载」：一整话的页序列写进目录。它现在**有地基了**（那颗件吃的就是
一枚 `dir` 加一串字节），缺的是上面那一圈"取页 → 逐页落盘 → 进度与取消"。**别再把它记成"落点还没接"** ——
那句已经假了，而假话在本仓平均活四批才被抓住（§1 那一表就是证据）。
| **批 86** ✅ 同日落地（手册行 133） | ~~归档直读（zip4j 或 commons-compress，二选一）+ 备份导出打包~~ → **归档那一格改判成零新依赖** + **gallery 浏览历史点亮首页「历史」那一行**（原 批 89 的那半提前） | 两笔现量把这一行改了：① 那两颗库**都不必引** —— 对岸两处（`LocalComicManager.kt:13-15`、`BackupManager.kt:29-31`）用的就是 `java.util.zip`，交付包镜像里 `java/util/zip/*.class` **43 条**在内 ⇒ jlink 那格不存在，包体 0 增量；② **备份导出打包 = 裁决表 §5 那行「W6 用户明确不点头」** ⇒ 不动，退回待点头。⚠️ 这一行从前写「一次把 A 档第 2、**5** 两格吃掉」而 A 档只有四行 —— 数错的那格就是备份那一格，改判之后它整个不在本批面上了 |
| **批 87 上半** ✅ 落地（手册行 134） | ~~阅读器翻页那一档（`Pager` + 音量键，B 档）~~ → **当场拆两半**：上半 = 整页翻那一档 + 模式那枚偏好 + 页号 + 历史两档（落地） | 不引依赖；档位与算式走 **B 路**（桌面自持一份 + 四处回核），A 路（`reader/` 进编译面）**量过而登记等点头** |
| **批 87 下半** ✅ 落地（手册行 135） | 阅读那一屏的键位（`←` / `→` / `PageUp` / `PageDown`）+ 音量键那一枚 | 键位那半已落（两档都落、与按钮同一颗步进）；音量键那半**按这一行原定次序交的是量具**（`D_按键 … 原始=…`），分支没接 —— 那一格等的是一次真按的读数，只有人能给（`desktop-reader-keys-2026-10-11.md` §7） |
| **批 88** ✅ 落地（手册行 136） | ~~网页登录**轻路**（`Desktop.browse` + `jdk.httpserver`，并补 `modules(...)`）~~ → **当场量掉那半条前提**：对岸全仓**零颗**本地 HTTP 服务、零颗 `ServerSocket`（唯一的 `999999` 命中是设备 ID 随机数），站方也不往 localhost 回调 ⇒ `jdk.httpserver` 与那枚 `modules(...)` 补票**没有对象**；`Desktop.browse` 那一半只能"陪着人看登录页"，cookie 留在那台浏览器自己的罐里，而"程序去读浏览器的罐"是已拍板的红线 | 落的是六条尺子 + 三处文档改判（`desktop-web-login-verdict-2026-10-11.md`）。真接缝**已经在桥里**（`DesktopJsHost.kt` 那三档 cookie 读写、`isLogged`、那颗罐挂在客户端上、`buildCookie` 已处理前导点与脏域），缺的只有两样：**手工粘贴那一个入口**与**把内存罐落到盘上**（今天重启就丢，而 `isLogged` 那格已经落盘 ⇒ 只补入口就是假开关）。开工单在同一份 rounds §4，顺序是**先盘后入口** |
| **批 88·下半** ✅ 落地（手册行 137） | 把 (c) 那一形接到桌面：罐接盘（**改账那条现状读数**）→ `EngineSession` 那枚私有 `call(script)` 跑源的 `loginWithCookies`/`validateCookies`（字段名单从脚本现取，不许桌面重述）→ 每源一枚"填 cookie"而**只摆给得出的源** → `--cookie=` 取证种子（只并本次装载不写盘）→ 交付包重打（动 `:engine-probe` 必重打） | ⚠️ 落地时**四条与本行预期不同**：① 34 颗里给得出的只有 **3 颗**（`ehentai`/`lanraragi`/`mycomic`），另 31 颗连格子都没有；② 罐接盘之后那条现状读数**改成"内存与盘上两轨不许"而不许删**（`DesktopWebLoginVerdictTest` 5→4）；③ 取证种子那一路**在真窗口里第一次就是坏的**（裸 host 解不成 URL，屏上报 2/2 而真进罐 0 枚，离屏 14 条全绿 ⇒ 见手册 §0.1 坑 ㊢）；④ 重打之后核同步那把尺子（`check_pkg_jar_sha.js`）**只看着 `desktop.jar`**，对本批改的 `engine-probe` 是盲的 ⇒ 当场扩成两颗都核（坑 ㊣）。**仍未量**：源那句 `validate` 在桌面真跑一次（要真凭据 + 真网络）、包内起窗那一格、`ccc` 那一族的 `localStorage` 那条路 |
| **批 89** ← 现第一件（**2026-10-11 只读量完一轮 ⇒ 档位改了**） | 大图屏补 subsampling / ~~gallery 浏览历史（新表 + 一屏）~~ **已随批 86 落地**（落地形状不是"新表"而是桌面自持那份 JSON 档 —— 原判据点名的两种存储形态都在对岸，`app/` 是受保域） | ⚠️ **subsampling 那一半既不用自研、也不该沿用旧判负**：`me.saket.telephoto:sub-sampling-image` 在 Maven Central 上**有 `-desktop` 那一颗**（0.19.0，jar **29,887 B / 13 颗 class**，`ImageRegionTile`、`ImageRegionTileGrid`、`ImageSampleSize`、`TileGridGenerator` 逐颗点名 ⇒ 是**真的分块解码**，不是空壳 expect）。从前记的那条"`zoomable-image-coil3` 没有 desktop 变体"（`desktop/build.gradle.kts:204-206`，因此缩放那一路改吃 `net.engawapg.lib:zoomable:2.16.0`）**只管 zoomable 那一支，管不到 sub-sampling 这一支** —— 两支的发布矩阵不同（同一族里 `zoomable-desktop` 也在，而 `sub-sampling-image` 的 ios/js/wasm 各一颗齐）。**开工单（先量后接，四条）**：① 那颗 desktop 实现吃的解码 API 是什么（`ImageFormatsKt` 疑似 ImageIO/AWT ⇒ 交付包 jlink 那格要按批 82.9 `java.sql` 那一课**先量 `java.desktop` 在不在镜像里**；"包内取不到 jar 里的原生库"那条成因至今未定论，别再踩）；② 它与现有那枚 `zoomable` 怎么叠（Telephoto 的 `SubSamplingImage` 自带 pan/zoom，两套缩放同挂会打架 ⇒ 要么只取它的解码层，要么整屏换掉现那枚，不许两枚并存）；③ 大图那三档地址（`desktopDetailTierUrl(card.post, tier)`）里网络档与本机档各走不走得通分块；④ 判据要能证"放大时**不是一次解全图**"（证不到就是假开关：读数念"分块"而内存里仍是整张）。**代价分档**：接现成品 ≈ 一批（新依赖 + 一屏换件 + 台架），自研 ≥ 三批 ⇒ 这一行从"中"降到**小-中**，等 ①② 量完定档 |
| 挂着等点头 | 源脚本**导入**（要先把引擎读路径改成用户可写目录，并动到 `app/` 的键口径）、MSI 与正式包名、`ImageHeaderPolicy.kt:67,101` 那枚受保域的一行、"从库里那一页续读"要不要给 `comic_history` 加话号列（**两端共用表**） | 四条都要人拍：动对岸口径或动交付形态 |

## 5. 换库之前要先答的三个问题（本项目血泪）

1. **jlink 看不见的依赖**：任何新 jar 进包之前先问"它是自动模块吗？它 require 哪个 JDK 模块？" —— `java.sql`（批 82.9）与 `jdk.unsupported`（S0-9）两次的形状一模一样，而**只有起交付包才量得出来**。
2. **Coil 与 Swing 的线程边界**：凡碰 `SingletonImageLoader` 就要 `kotlinx-coroutines-swing`，且判据抓不到、只在屏上炸（批 47）。
3. **License 与包体**：AGPL 直接判负；MPL 是文件级 copyleft（改它的文件要开源）；带 JRE/native 的 sidecar 要现量 MB 数，别拿"应该不大"当读数。
