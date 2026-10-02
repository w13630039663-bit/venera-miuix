# R1-F 阶段 0 探针：S0-1 / S0-2 / S0-3 / S0-4 / S0-5 / S0-6 / S0-7 / S0-8 / S0-9（2026-10-02 落地）

> 分支：`compose-migration` ｜ 状态：**S0-1 通过（真机回归待用户点）｜ S0-2 通过 8/8 ｜ S0-3 主体通过（headless oracle 对拍未做）｜ S0-4 主体通过（ehentai 搜索+出图皆通，唯 SauceNAO 搜索端点被真盾且换 UA 无效）｜ S0-5 库层已证·人工四条待点 ｜ S0-6 否决原依赖·改道候选 a 判负（材质没画、`WS_EX_NOREDIRECTIONBITMAP` 事后上不了）｜ S0-7 通过（预期推翻）｜ S0-8 通过（阅读器不改分页制）｜ S0-9 可移植通过·安装包待拍板 ｜ **阶段 0 收官件：端到端最小闭环已在桌面窗口刷出真源封面（jm/goda/nhentai 三源），阅读链路（详情→章节→页表→去混淆取图）也已接通，**且整链在打包 exe 里复现成功**（顺手挖出两个只在包内复现的坑，见 §七末）**
> 上游方案：`windows-port-feasibility-2026-10.md` 第七节阶段 0
> 本轮性质：**新增 `:desktop` 探针模块，对 `:app` 零改动**（不加豁免、不动 material3 钉版）

---

## 一、做了什么

只加不改，四个文件：

| 文件 | 改动 |
|---|---|
| `gradle/libs.versions.toml` | 新增 3 个版本 + 3 个库 + 1 个插件别名（`composeMultiplatform=1.12.1`、`composeFluent=v0.1.0`、`windowStyler=0.3.2`） |
| `settings.gradle.kts` | `include(":desktop")` |
| `build.gradle.kts` | `alias(libs.plugins.compose.multiplatform) apply false` |
| `desktop/` | 新模块：`build.gradle.kts` + `SpikeWindow.kt`（`FluentTheme { Text } ` + `WindowStyle(Mica)`） |

`:app` 的 `build.gradle.kts`、依赖表、源码 **一行未动** —— 所以"Android 会不会被拖下水"这一问要用读数回答，不能靠断言。

## 二、判据读数

### S0-1 版本矩阵：**通过**

| 判据 | 读数 | 证据 |
|---|---|---|
| 桌面窗口跑起来 | ✅ 窗口标题 `Venera R1-F / S0-1 spike`，PID 31300，520×320；stdout 打出 `S01_COMPOSED_OK` | `_qa/s01-desktop-run.log:24` |
| `:app:assembleDebug` 不受影响 | ✅ `BUILD SUCCESSFUL in 3m 6s`，四支 APK 正常产出 | `_qa/s01-app-assemble.log` |
| material3 解析结果不变 | ✅ 仍是 `androidx.compose.material3:material3:1.3.1 -> 1.5.0-alpha22`（钉版语义原样） | `_qa/s01-app-deps.log` |
| 真机首页零回归 | ️ **未验** —— 按既有规矩设备由用户点 | 待办 |

外部构件坐标（本轮实证，别再查第二遍）：

- `org.jetbrains.compose`（CMP Gradle 插件）当前 stable **1.12.1**，对应 Jetpack Compose 1.12.1；官方口径"CMP 与最新 Kotlin 自动对齐，无需手工匹配"，K2 门槛是 Kotlin ≥ 2.1.0 ⇒ 我们的 **Kotlin 2.4.10 在范围内**。
- `io.github.compose-fluent:fluent-desktop` 只有 **v0.1.0**（2025-08-10），包名 `io.github.composefluent`（**无连字符**），组件全在 `io.github.composefluent.component.*`。
- `com.mayakapps.compose:window-styler` **0.3.2**，KMP 构件（JVM 侧重定向到 `window-styler-jvm`；`window-styler-0.3.2.jar` 本体只有 261 字节，是 KMP 桩，不是下载坏了）。
- `top.yukonga.miuix.kmp:miuix-ui-desktop` **0.9.4-rc01 有 desktop 变体**（我们正好用这版），与 fluent-desktop 同域解析无冲突。

### S0-2 二进制兼容：**通过（8/8 运行期组合成功，零 `NoSuchMethodError`）**

探针 `desktop/src/main/kotlin/com/venera/desktop/SpikeWindow.kt` 一次组合方案文档点名的 8 个组件：
`Text` / `ProgressBar`（确定+不确定两重载）/ `Switcher` / `TextField` / `TooltipBox` / `FlyoutContainer` / `MenuFlyout`+`MenuFlyoutItem` / `FluentDialog` / `NavigationView`+`menuItem`。

| 判据 | 读数 |
|---|---|
| 编译期 | ✅ `BUILD SUCCESSFUL` |
| 运行期组合 | ✅ stdout `S02_COMPOSED_OK`（打在 `NavigationView` 的 pane 里 ⇒ 前面 7 组都已组合过） |
| `NoSuchMethodError` / `IncompatibleClassChangeError` | ✅ **0 命中**（`_qa/s02-desktop-run.log` 全文只有那一行输出） |
| 真实渲染 | ✅ 截图核对：Fluent 蓝开关、进度条轨道、Flyout 弹层、菜单项、对话框、侧栏汉堡按钮全部成形 |

⇒ compose-fluent v0.1.0（拿 Compose 1.8.2 编的）在 **CMP 1.12.1 / Kotlin 2.4.10 / Gradle 9.7.1** 上运行期兼容。**R1-F 最大的那条不确定轴已经落地**，P2（要不要把观感地基压在 experimental 库上）从"赌"变成"已知它现在能跑"。

三条落地时踩到的 API 事实（下一轮别再查）：

- 两个 experimental 标记要显式 opt-in，否则**编译直接报错**（不是警告）：`io.github.composefluent.ExperimentalFluentApi` + `androidx.compose.foundation.ExperimentalFoundationApi`。
- `MenuItem`/`menuItem`/`MenuFlyoutItem` 都是 **scope 扩展函数**，具名 import 会 `Unresolved reference` ⇒ 组件包要 `import io.github.composefluent.component.*`。
- `MenuFlyoutItem` 有 3 个重载（`selected+onSelectedChanged` / `onClick` / `items` 子菜单），且 `text` 是**第 3 位**参数 ⇒ 尾随 lambda 会绑到末位 `colors`，必须写 `text = { ... }`。

### S0-6 真 Mica：**否决，且失败方式是静默的**

`WindowStyle(backdropType = WindowBackdrop.Mica)` 运行期抛：

```
Exception in thread "AWT-EventQueue-0" java.lang.NoSuchFieldException: delegate
    at com.mayakapps.compose.windowstyler.TransparencyUtilsKt$delegateField$2.invoke(TransparencyUtils.kt:57)
    at ...TransparencyUtilsKt.getSkiaLayer(TransparencyUtils.kt:36)
    at ...WindowsWindowStyleManager._init_$lambda-0(WindowsWindowStyleManager.kt:66)
```

根因：window-styler 0.3.2 靠反射拿 skiko `SkiaLayer` 的 `delegate` 字段来做透明 hack，新版 Compose Desktop 的内部结构已改 ⇒ 反射目标不存在。它抛在 AWT 事件线程上被吞掉，**窗口照常显示成实心浅灰**（截图核对：无 Mica、无桌面壁纸透色）。

这条直接推翻方案文档 3.2 的假设"真 Mica 有现成依赖"。按"降级必须可见"的纪律，现状是不可接受的：控件挂着、材质没有、什么也不说。

三条改道候选（要拍板）：

- **a. 自研 FFM 调用**：`DwmSetWindowAttribute(DWMWA_SYSTEMBACKDROP_TYPE, DWMSBT_MAINWINDOW)`，用 JDK 21+ 的 `java.lang.foreign`，零第三方依赖，Win11 22H2+ 真 Mica；仍需解决 AWT 窗口透明才能让 Mica 透出来。
- **b. compose-fluent 自带的 haze 应用内模糊**（它 pom 里就有 `dev.chrisbanes.haze`）—— 观感降一档，但无跨窗口依赖，也不会静默失效。
- **c. 找 window-styler 的维护中 fork 或本地升版**（**未查，别当已知**）。

## 三、阶段 0 余项（同日更新到第五节之后）

已收口的：S0-1（除真机回归）、S0-2、S0-3、S0-4（除"手动导 cookie"那条待钥匙）、S0-5（库层）、S0-7、S0-8、S0-9（可移植产物），以及**阶段 0 收官件：端到端最小闭环已能跑**（第十一节）。剩下的是：

1. **真机回归（用户）**：装本轮新产的 `app/build/outputs/apk/debug/app-universal-debug.apk`，点首页 5 主 Tab —— S0-1 的最后一格（设备按既有规矩只由用户操作）。
2. **S0-5 人工四条（用户）**：探针窗 PID 30880 还开着，判据见第五节。
3. **S0-4 的待钥匙那条**：`cf_clearance` 跨客户端能不能认账，需要用户从浏览器 devtools 手抄一枚喂进探针才量得动（判读见第九节末）。桌面侧要不要给 SauceNAO 一个"标记不可用 + 说明"的落地，属阶段 2 的 UI 决定。
4. **S0-6 改道（要拍板，候选 a 已量到判负，见第十节）**：FFM/HWND/透明三块砖都在，但**材质根本没画出来**（Mica 与 Tabbed 逐字节相同、三档全是 R=G=B 的死灰、方差只来自我们那行字），而唯一的补法 `WS_EX_NOREDIRECTIONBITMAP` **事后改不上**（只能在 `CreateWindowEx` 时指定）。⇒ 剩下两条真路：**b. haze 应用内模糊**（fluent 的 pom 自带，观感降一档但不会静默失效）或 **c. 桌面侧明确不做材质**（实底 + 设置里写清楚）。a 只在"愿意自建 HWND 窗口层"时才重新可选。
5. **运行期那条待拍板已量完，问题换了形状**（第十一节事实 3）：普通 HotSpot 从 21 升到 25 **拿不到 GraalJS 的 JIT**（两档都还在报 fallback runtime，差值只有 5%–41%），而解释器档的绝对速度够用（装载源 1.4s、探索页 0.9s）。⇒ 要拍的不是"锁哪个 HotSpot 版本"，而是**"要不要为桌面捆绑 GraalVM 运行时"**；不捆也完全能发布，只是 JS 重路径慢一档。
5. **S0-9 安装包（要拍板）**：①装 WiX 3.14 进 PATH（只有你能点安装器）②可移植 zip + 自制 Inno Setup（我倾向这条）③换 Msi 不解决问题。

## 四、S0-3 GraalJS 跑真源：**通过**（同日追加）

新增 `:engine-probe` 模块（JVM，与 `:app` 无依赖边），用 GraalJS 顶替 WebView。它**复用同一批 handler 源文件**（`srcDir` 指过去 + 排除 `VeneraJsEngine.kt`），所以两侧的差异只可能来自引擎本身。

| 判据 | 读数 |
|---|---|
| 第一级 `nhentai.js` 出 ≥10 条 | ✅ **25 条**，maxPage=8413，标题真实 |
| 第二级 `jm.js` 出结果 | ✅ **80 条**，maxPage=5（含其 hmac/md5 签名链） |
| jm 详情 | ✅ `comic.loadInfo` success，字段表 `chapters/cover/description/isFavorite/isLiked/likesCount/recommend/tags/title/updateTime` |
| jm 页表 | ✅ `comic.loadEp` 出 **104** 页 |
| `onImageLoad` | ✅ 返回 url+headers+`modifyImage`，解出 **num=4** |
| 取图 + 去混淆 + 落盘 | ✅ HTTP 200 → WebP 720×500 → 重排 → `_qa/s03-jm-descrambled.png` |
| "已还原"的量化判据 | ✅ 相邻行平均像素差 **混淆态 0.452 → 还原态 0.245（降 45.7%）** |
| 微任务排干语义（文档标"必须实测"） | ✅ 见下方口径 3 |

| 判据里"与上游 `--headless` 对拍" | ⚠️ **未做**。那条 oracle 的前提是有一份能跑 `--headless` 的 Flutter 二进制，而 W0 已按 P1 改道撤掉，本机也没有 Flutter SDK。⇒ S0-3 的**引擎可跑性**已证（含去混淆的量化验证），**与上游逐字段一致**这条仍挂着，要么后面补一次 headless 对照，要么改用 Android 侧同源代码出同一笔读数做对照（后者不需要新工具链）。 |

Android 侧同步复验（因这轮真的动了 `:app` 的源码）：`:app:compileDebugKotlin` ✅、`:app:testDebugUnitTest` ✅（92 个结果文件）、`:app:assembleDebug` ✅。

### 六条新事实（下一轮直接照用，别再试错）

1. **现有"JS 引擎"其实是 Android WebView**（`android.webkit.WebView` + `@JavascriptInterface`），不是任何 JS 库。所以桌面侧不存在"换个引擎绑定"的捷径，必须自建宿主 —— 但桥协议只有 6 个方法（`sendMessage` / `sendMessageAsync` / `takeAsyncResult` / `postAsyncResult` / `log` / `getVersion`）。
2. **GraalJS 需要宿主注入四样东西**，缺一个就静默断链：`window`（shim 往它挂 `__veneraResolve`）、`setTimeout`（shim 抓成 `_nativeSetTimeout`）、`console`、**`btoa`/`atob`**（`_serializeMessage` 靠它做 ArrayBuffer↔base64，GraalJS 默认没有）。
3. **微任务排干口径**：桌面侧 `sendMessageAsync` **内联做完再回调 `window.__veneraResolve`**（WebView 那条异步通道是为了绕 JavaBridge 单线程串行，JVM 侧没这个约束），外层 `evaluateAsync` 用 `eval → 跑到期定时器 → context.eval("js","void 0")` 的循环给 GraalJS 制造"JS 栈空"边界来排干微任务。实测有效，两级探针都在 90s 上限内 5–10s 返回。
4. **源给的 headers 带 `Accept-Encoding: gzip`** ⇒ OkHttp 会关掉透明解压。Android 侧由 `ImagePipelinePolicy.decodeBody` 兜；任何自取图的通道都要自己兜（探针第一版就取回了一坨 `1F 8B 08`）。
5. **jm 的页图是 WebP**（`RIFF....WEBP / VP8`）⇒ JVM 侧 ImageIO 原生不认，需外挂 `com.twelvemonkeys.imageio:imageio-webp:3.15.2`（实测解出 720×500）。这条要进阶段 3 的依赖清单。
6. **`chapters` 不是 `episodes`**：jm 的 `loadInfo` 给的是 `chapters`；且无章节时 `loadEp(cid, null)` 与 `loadEp(cid,"0")` 都报 `Invalid Data`，**传 cid 本身**才出页表。Android 侧的 `placeholderChapters` 机制是否覆盖了同一形状，值得单独核（不在本轮改）。

### 顺带做的步骤 A 首刀（引擎层去 Android 化）

`engine/` 下 6 个文件现在**零 android import**，可被 JVM 模块直接编译：新增 `EnginePorts.kt`（`EngineLog` 出口 + `EngineBase64`），`JsSourceDataStore` 改收 `baseDir: File`，`JsHttpHandler` 改收 `() -> OkHttpClient`，日志在 Android 侧由 `VeneraJsEngine` 装回 logcat sink（真机 QA 读数不丢）。Base64 语义对齐：编码 ≙ `NO_WRAP`、解码用 MIME decoder 以 ≙ `DEFAULT` 的容忍换行。

## 五、S0-5 中文 IME：库层已证，**真机半条待用户**

新增入口 `desktop/src/main/kotlin/com/venera/desktop/ImeProbe.kt`，跑法 `./gradlew :desktop:ime`（窗口已开着，PID 30880）。

### 已经量到的（不需要人）

| 事实 | 证据 |
|---|---|
| CMP 1.12.1 的桌面文本输入**实现了 AWT 的 IME 请求接口** | `javap ui-desktop-1.12.1.jar` ⇒ `androidx.compose.ui.platform.InputMethodSession implements java.awt.im.InputMethodRequests`，带 `getTextLocation(TextHitInfo)`、`getLocationOffset(int,int)`、`getInsertPositionOffset()`、`getCommittedText(...)`、`getSelectedText(...)`、`getImeComposingText()`、`inputMethodTextChanged(InputMethodEvent)` |
| 候选窗定位所依赖的**光标矩形来源**在接口里 | `PlatformTextInputMethodRequest` 带 `getTextLayoutResult(): Function0<TextLayoutResult>` —— 即 `getTextLocation` 有真实文本度量可算，不是返回 null 的摆设 |
| 上游专门为 Windows 打过一个 IME 补丁 | 类 `androidx.compose.ui.scene.skia.InputMethodEndCompositionWorkaround$CInputMethodWorkaround`（含 `inputContext`），即"结束合成"这条 Windows 特有的坑已被处理 |
| 中文渲染与文本回路正常 | 探针窗内中文成形；每次变更往 stdout 打 `IME_TEXT=`（启动即打出前置文本） |

### 只能由人给的（判据原文：候选窗落在光标下、可翻页、可上屏）

自动化按键会绕过真 IME 会话（UIA 送的是字符而非合成事件），量出来是假读数；且此刻前台有一个全屏游戏，我不去抢焦点。**请你在已打开的探针窗里做四件事**：

1. `Win+Space` 切微软拼音，在输入框末尾打 `jinman` —— 候选窗是否贴在光标正下方（不是窗口左上角）；
2. 候选窗翻页（`-`/`=` 或 `,`/`.`）是否可用；
3. 选词上屏后，框内与下方"当前框内"回显是否同步；
4. 连续打完一整句再空格确认，有没有吞字/重复上屏（那条 `InputMethodEndCompositionWorkaround` 管的就是这个）。

日志在 `_qa/s05-ime.log`，每行 `IME_TEXT=` 就是上屏后的真实框内文本，可直接对账。

### 为什么这条不能像 S0-6 那样降级

判据写的是"不能降级（入口就是搜索框），不通即改路线"。所以读数只有过/不过两态，没有"先上再说"的中间档。

## 六、S0-7 玻璃能力：**通过，且结论与文档预期相反**

探针 `desktop/src/main/kotlin/com/venera/desktop/GlassProbe.kt`（跑法 `./gradlew :desktop:glass`），四层读数：

| 层 | 读数 |
|---|---|
| `top.yukonga.miuix.kmp.blur.isRuntimeShaderSupported()` | **true** |
| `com.kyant.backdrop.isRuntimeShaderSupported()` | **true** |
| `com.kyant.backdrop.RuntimeShader("<AGSL>")` 构造 | ✅ 成功，实现类 **`com.kyant.backdrop.SkikoRuntimeShader`** |
| `androidx.compose.ui.graphics.BlurEffect(4f, 4f)` | ✅ 构造成功，`isSupported() = true` |
| `androidx.compose.ui.graphics.RuntimeShader` 在桌面 classpath | ❌ **ClassNotFoundException**（`ui-graphics-desktop` 里根本没有 `RuntimeShader`/`ShaderStage` 这两个类） |

**方案文档 S0-7 担心的失败模式不成立**：它预设"gate 返回 false ⇒ 现有 4 个消费点整段静默跳过玻璃"。实测两个 gate 都返回 true，玻璃走的是**库自家的 skiko `RuntimeEffect` 通道**，不依赖 androidx 那个缺失的 `RuntimeShader`。所以 `VeneraTopAppBar.kt:122,134,199` / `VeneraTopBarPill.kt:66` / `InteractiveHighlight.kt:49` 这四处在桌面侧**会进玻璃分支**，不是被跳过。

**还差最后一步才算完**：gate 说 true 不等于画面对。需要一次像素级/目视确认（真把 `drawBackdrop` 画出来看有没有折射与模糊）。这条挂在阶段 1 的壳层验收里。

## 七、S0-9 打包分发：**可移植产物通过，安装包被 WiX 版本卡住（原因明确）**

| 判据 | 读数 |
|---|---|
| 出可运行产物 | ✅ `:desktop:createDistributable` → `desktop/build/compose/binaries/main/app/venera-desktop-probe/`，**129MB 自包含**（含 JRE），实测双击起窗（PID 32204，标题就是 S0-2 探针窗） |
| 捆绑运行时含无障碍模块 | ✅ `runtime/release` 的 `MODULES` 共 8 个，**含 `jdk.accessibility` 与 `java.desktop`**；`runtime/bin/javaaccessbridge.dll` 在位（NVDA/JAWS 的硬前提）。⇒ 我一度怀疑 `modules("jdk.accessibility")` 是假开关，用 release 文件查实是生效的 |
| 数据落 `%LOCALAPPDATA%` | ⏳ 未验 —— 探针窗本来不写数据，这条要等阶段 1 有真实存储层再接 |
| `packageExe` / `packageMsi` | ❌ **失败且原因清楚**：compose 插件下载的是 **WiX 3.11**（落在 `build/wix311/`），JDK 23 的 `jpackage.exe` 拒绝它 —— 日志原文要"WiX 3.0 或更高版本并加进 PATH"，并判 `类型 [exe] 无效` |

三条出路（要拍板，与第四节"产物只进 Releases"的纪律一起看）：**①** 装 WiX 3.14 并进 PATH（只有你能点安装器）；**②** 正式产物走"可移植 zip + 自制 Inno Setup 脚本"，绕开 jpackage 的 WiX 依赖（master 分支的 `windows/build.py` 本来就是 Inno Setup 那套，口径能对齐）；**③** 换 `TargetFormat.Msi`（同样要 WiX，不解决问题）。我倾向 **②**。

### 一条环境读数，省下一轮试错

构建 JVM **不吃系统代理**：`packageDistributionForCurrentOS` 的 `:downloadWix` 直连 github 超时；加
`-Dorg.gradle.jvmargs="… -Dhttps.proxyHost=127.0.0.1 -Dhttps.proxyPort=7890"` 后下载即通。
凡是要从 github/maven 拉外部工具的 gradle 任务，都得显式喂这两个参数。

### 同日追加：把打包对象从探针窗换成真闭环 —— 挖出两个**只在包内复现**的坑

前面 §七 那轮打的是 S0-2 探针窗，**它根本不碰引擎**，所以"GraalJS 能不能活过 jlink 出来的运行时"这一条当时等于没测。
把 `mainClass` 换成 `com.venera.desktop.VeneraDesktopKt` 重新打包（129MB → **199MB**，多的是 GraalJS + 引擎那批 jar），第一次跑就中两个雷：

| 坑 | 症状（原文） | 根因 | 修法 |
|---|---|---|---|
| **jlink 掉了 `jdk.unsupported`** | `PolyglotException: java.lang.NoClassDefFoundError: sun/misc/Unsafe` → `Caused by: ClassNotFoundException: sun.misc.Unsafe` → `Internal GraalVM error`；抛点是 `com.oracle.truffle.api.nodes.NodeClassImpl$NodeFieldData.getUnsafe` | Truffle 反射拿 `sun.misc.Unsafe`，而这个类住在 `jdk.unsupported` 模块里；jlink 的**自动模块推导看不见反射目标**，Gradle 侧编译运行全在 classpath 模式（模块全开）所以从来不复现 | `modules("jdk.accessibility", "jdk.unsupported")`；改完 `runtime/release` 的 MODULES 里确实出现 `jdk.unsupported` |
| **ImageIO 的磁盘缓存写不出去** | `页图解不开 num=2 头=52 49 46 46 … WEBPVP8 \| RIFF^...WEBPVP8 解码器=[bmp,gif,jpeg,jpg,png,tif,tiff,wbmp,wbp,webp] <- ImageIO IIOException: Can't create cache file!` | 掉的**不是**解码器 —— webp 在解码器清单里，TwelveMonkeys 的 SPI 在包内注册成功。真正拒的是**文件创建**本身，成因见下面"追加更正" | `ImageIO.setUseCache(false)`（字节本来整幅在内存里，落盘缓存零收益；`encodePng` 的写侧同理） |

**第二个坑能挖出来，靠的是先把吞异常的写法改掉**：`decode` 原本写作 `runCatching { ImageIO.read(...) }.getOrNull()`，把真异常压成一句"页图解不了码"，
格式问题与插件问题混成一团 —— 降级路径不能静默交回一个 `null`，先把 cause 带出来再谈归因。改成带 cause 抛出、错误串里附上
**头 16 字节的 hex+ASCII** 与**运行期真认得哪些格式**，一次跑就分清了"数据是 WebP / 解码器有 WebP / 死在缓存"。

修完后的打包内读数（exe 直跑，`_qa/pkg-jm5.log`）：

```
D_启动 sources=34 起始=jm proxy=127.0.0.1:7890 assets=D:\venera-compose\app\src\main\assets
D_环境 jdk=23.0.2 tmp=C:\Users\leimi\AppData\Local\Temp\ 打包=true
D_结果 源=禁漫天堂 分区=[…] 出图=30/72
D_详情 荒淫地下城支配者 章数=61 前3=[Episode(name=第1話, id=1193811, …), …]
D_阅读 1193811 页数=8
```

⇒ **阶段 0 收官件的整链（源脚本 → GraalJS → 网络 → 解码去混淆 → Fluent 窗口）在 shipped app image 里成立**，不再是"只在 gradle 起跑得通"。

三条附带事实：
1. **打包 exe 默认没有控制台**，`println` 全部丢弃 —— 日志取不到曾被误读成"进程没起来"。加 `nativeDistributions { windows { console = true } }` 才有读数；
   正式产物不该带控制台窗，届时要改成写 UTF-8 日志文件（顺手一条：打包侧 stdout 是 **GBK**，取证要 `iconv -f gbk -t utf-8 -c`）。
2. **打包运行时是 JDK 23.0.2**（= Gradle 所在 JVM），gradle 起跑的是 toolchain 21 —— 两者对 Truffle 都是 fallback 解释器
   （GraalJS 25.4.4.1.1 要 JDK ≥25 且 <26 才启用优化运行时），与 §十二 JIT 那条一致：**打包既不带来退化也不带来提升**。
3. `assets` 仍是从 `user.dir` 推出来的仓库路径 —— 正式产物必须把 `app/src/main/assets/sources` 随包分发（`runtime` 资源目录或首启解包），
   这条记在阶段 1 的账上，不是本轮判据。

截图这条本轮拿不到：判据层如实报了 `D_截图 无效：WindowFromPoint 不认这扇窗`（当时锁屏），**没有产出假图**。图证待你在解锁状态下再跑一次。

### 追加更正：上一条的归因是错的 —— 真成因是**这台机器按可执行文件拦写入**

把数据目录从 `%TEMP%` 换成判据要求的 `%LOCALAPPDATA%`（`VeneraDesktopKt.localDataDir()`，取不到就抛、不静默退回 tmp）之后，
打包 exe 连**启动**都过不去，两处目录一起拒：

```
java.io.FileNotFoundException: C:\Users\leimi\AppData\Local\venera\.write-probe (拒绝访问。)
    at java.base/java.io.FileOutputStream.open0(Native Method)
    at com.venera.desktop.VeneraDesktopKt.localDataDir(VeneraDesktop.kt:79)
Failed to launch JVM

LOCALAPPDATA=D:\tmp-probe  →  IllegalStateException: 建不出目录 D:\tmp-probe\venera
```

三条排除性读数，把"路径/ACL/jpackage 上下文"这类解释量掉：**①** 同一台机器上 `node` 往 `D:\tmp-probe` 写文件并读回 = `ok`，目录本身可写；
**②** `%TEMP%` 与 `%LOCALAPPDATA%` 与 `D:\` 三种位置全拒 ⇒ 不是某个受保护目录；**③** 系统里 **Windows Defender 是关的**
（`Get-MpPreference` 直接 `0x800106ba`（服务未运行），WSC 里 Defender 的 `productState=393472`），
而进程表里有 **`C:\Program Files\Huorong\Sysdiag\bin\HipsTray.exe` —— 火绒 HIPS 在跑**（它没注册进 WSC，所以"注册的 AV"那一栏只看得到关掉的 Defender）。

⇒ 上一段"`ImageIO.setUseCache(false)` 修好了 jpackage 的缓存问题"这个归因要改：**不是 jpackage 启动器的上下文问题，
是这个未签名的 exe 被按程序拦创建文件**，`%TEMP%` 里那颗 cache 文件是同一条拦阻的另一个落点。gradle 侧永不复现，
因为跑的是 `.gradle\jdks\eclipse_adoptium-23-...` 里那颗签好名的 `java.exe`（顺带把"打包运行时为什么是 23.0.2"也对上了：jpackage 用的就是 Gradle 所在 JVM）。

**对产物的直接含义（要记进阶段 1）**：桌面端的 **Authenticode 签名不是美化项，是数据能落盘的前提** ——
仓库现在只有 Android 侧 `key.properties` 那套签名，桌面这边一次都没接过。两条待办：
① 你在火绒里查"拦截日志"，若确有 `venera-desktop-probe.exe` 创建文件的记录，加白名单后 `--shot` 那条判据可复跑（解锁 + 白名单都在你侧）；
② 正式产物接签名，且**数据目录拿不到时宁可启动即抛**（本轮就是这么发现问题的，不要改成退回 tmp）。

## 八、S0-8 长列表（条漫）帧时间与内存：**通过 —— 阅读器不必改分页制**

探针 `desktop/src/main/kotlin/com/venera/desktop/ReaderProbe.kt`，跑法 `./gradlew :desktop:reader`（加 `-PdecodePerItem=true` 跑第二档），日志 `_qa/s08-shared.log` / `_qa/s08-decode.log`。场景写死：单列 LazyColumn 3000 项、每项 1200×1800 的 JPEG（噪声内容，防止压缩走捷径），每帧推进 160px 取 240 帧，堆上限 `-Xmx2g` 写死以便复现。

| 档 | 读数 |
|---|---|
| 单页解码成本 | **skia 11.6–11.8 ms/页**（同一字节 20 次取平均）；`ImageIO` 对照 19.9–20.0 ms ⇒ 桌面侧解码器**比 AWT 快约 1.7×**，别拿 `ImageIO` 的数当口径 |
| 解码后驻留 | **8.2 MB/页**（1200×1800×4） |
| 档一：共享一张位图（纯绘制吞吐上限） | `p50=5.56ms p95=6.13ms max=124.15ms 丢帧(>16.7ms)=2/239 堆已用=152MB/上限=2048MB` |
| 档二：每项各自在组合期同步解码（朴素做法，端到端） | `p50=5.56ms p95=15.38ms max=135.84ms 丢帧(>16.7ms)=6/239 堆已用=112MB/上限=2048MB` |

结论三条：

1. **绘制吞吐不是瓶颈**：3000 项单列、每项一张 1200×1800 位图，p50 5.6ms / p95 6.1ms —— 也就是 60Hz 的 16.7ms 预算里，Compose Desktop 的列表本身只吃掉约三分之一。**"要不要为桌面把条漫改成分页制"这一问，答案是不用**：Android 侧的分列/窗口化缓存设计可以照搬到桌面。
2. **解码才是**：把解码放进组合期（朴素做法），p95 从 6.1ms 涨到 **15.4ms**、丢帧从 2 涨到 6/239，正好卡在 16.7ms 预算的边缘。11.8ms/页 这个数要拿去算预算：**一屏跨页速率 × 11.8ms 必须小于 16.7ms**，所以桌面的阅读器同样必须**异步预取解码**（阶段 3 接 Coil 时这条是硬约束，不能"先简单写着"）。
3. **全驻留不可行**：3000 页 × 8.2MB = **24.6 GB**，任何桌面机都放不下 ⇒ 窗口化缓存在桌面上不是优化项而是必需项。

### 一条桌面特有的构件事实（影响阅读器写法）

`toComposeImageBitmap()` 在桌面返回的是 `androidx.compose.ui.graphics.SkiaBackedImageBitmap`，探针实测 **`is java.io.Closeable = false`**；`javap` 该类也没有 `dispose`/`close`。⇒ 桌面侧**没有 Android 那套 `DisposableImageBitmap` 显式释放契约**，native 像素的归还时机由 GC/skiko 的托管清理决定。写阅读器时不要照抄"离组即 dispose"，而要靠**限制同时驻留的页数**来约束内存 —— 这是与 Android 侧的真实差异，不是可以靠封装抹平的。

两条取数口径的诚实边界：档二里 240 帧共跨过约 28 个页边界，只有 6 帧超预算，说明并非每笔解码都落在被采样的那一帧内（预取/组合发生在帧外），所以 **15.4ms 是"有下界意义的真实体验"，不是最坏值**；另外 `withFrameNanos` 的节拍绑 Windows vsync，窗口被遮挡时帧会合批。像素级逐页解码对拍归阶段 3。

### 阅读器依赖在桌面的可用性（直接查 Maven Central 的 module metadata，不是猜）

| 构件 | 变体读数 | 判定 |
|---|---|---|
| `io.coil-kt.coil3:coil-compose:3.6.3` | 含 `jvmApiElements-published` / `jvmRuntimeElements-published` | ✅ 桌面可直接用（KMP 的 jvm 变体就是桌面） |
| `me.saket.telephoto:zoomable:0.19.0` | 含 `desktopApiElements-published` / `desktopRuntimeElements-published` | ✅ 缩放手势内核有桌面变体 |
| `me.saket.telephoto:zoomable-image-coil3:0.19.0` | 变体只有 `releaseVariantRelease*Publication`（**连 KMP 变体表都没有 ⇒ 纯 Android aar**） | ❌ 缺的**只是这一层 Coil3→zoomable 适配**，不是整个缩放能力 |

⇒ 阶段 3 的桌面阅读器不需要换缩放方案。`zoomable-desktop-0.19.0.jar` 里 `javap` 实测 `me.saket.telephoto.zoomable.ZoomableKt` 公开的是

```
public static final androidx.compose.ui.Modifier zoomable(
    Modifier, ZoomableState, Function1<Offset,Unit> onTap?, Function1<Offset,Unit> onLongPress?,
    boolean doubleTapToZoomEnabled, DoubleClickToZoomListener, MutableInteractionSource)
```

签名里**没有任何 Android-only 类型** ⇒ 这层适配与平台无关，桌面侧就是 `Modifier.zoomable(state, ...)` 挂到 Coil 的 `SubcomposeAsyncImage` 上，把 aar 里那层薄桥接重写一遍即可。

**一条自审**：我上一版在这里写过 `subsampling-image-desktop` 在 Maven Central 上 404 —— 那是**我搜错了构件名**（正确坐标是 `me.saket.telephoto:sub-sampling-image`，桌面产物叫 `sub-sampling-image-desktop`）。实测 `sub-sampling-image-desktop/0.19.0/` 与 `flick-desktop/0.19.0/` 都 **HTTP 200 存在**，`sub-sampling-image-0.19.0.module` 的变体表里 `desktopApiElements-published` / `desktopRuntimeElements-published` 都在。⇒ 超大页图的**分块采样加载（subsampling）桌面也有现成件**，阶段 3 遇到 6000px 长图时不必自己写分块解码。

## 九、S0-4 CF 过盾：**主体通过（桌面没有 WebView 并不等于过不了盾），唯一被真挡的是 SauceNAO 的搜索端点**

探针 `engine-probe/src/main/kotlin/com/venera/engineprobe/CfProbe.kt`，跑法 `./gradlew :engine-probe:cf -Pproxy=127.0.0.1:7890`，最终读数日志 `_qa/s04-cf5.log`（中间三版 `_qa/s04-cf.log` / `s04-cf2.log` / `s04-cf3.log` 留着对照，因为它们各自的错读都有记录价值）。日志是 GBK 控制台编码，读它要 `iconv -f GBK`。

用的是**桌面将来真实那颗客户端**（JVM OkHttp，同一 TLS/H2 指纹），每个 URL 打两档 UA：`UserAgentPolicy.DEFAULT_USER_AGENT`（应用默认那条移动端 Chrome 串）与 `ImageHeaderPolicy` 给 donmai 的那条非浏览器串 `Venera/1.0 (Android)`。挑战判据不另造，照 `CloudflareBypassManager.isCloudflareChallenge` 那四个 body 特征 + `cf-mitigated` 头逐字抄。

| 站点/端点 | app 默认 UA | 非浏览器 UA |
|---|---|---|
| `e-hentai.org/` | **200** 真页（`<title>E-Hentai Galleries`） | 200 真页 |
| `e-hentai.org/?f_search=Venera&mode=search`（**业务端点**） | **200** 真页 | 200 真页 |
| `exhentai.org/` | `ProtocolException: Too many follow-up requests: 21`（重定向死循环） | 同 |
| `exhentai.org/api.php`（**业务端点**） | **200** + `{"error":"Empty JSON Request"}` | 200 同 |
| `danbooru.donmai.us/posts.json?limit=1`（**业务端点**） | **403 + `cf-mitigated: challenge`** + `Just a moment...`（5691 字节挑战页） | **200 + 真 JSON**（1514 字节，`"id":12297172`） |
| `saucenao.com/search.php?...`（**业务端点**） | **403 + `cf-mitigated: challenge`**（5869 字节挑战页） | **403 + challenge**（换 UA 无效） |
| `saucenao.com/` 根页 | 200 真页 | 200 真页 |
| `gelbooru.com/index.php?page=dapi&s=post&q=index` | 401（**要 api_key，与 CF 无关**，别混进判据） | 401 同 |
| `yande.re/post.json?limit=1` | **200 + 真 JSON**（`Server: freenginx`，本来就不在 CF 后面） | 200 同 |
| `cdn.jsdelivr.net/.../index.json`（源索引，5.4 那条） | **200 + 真内容**（4426 字节） | 200 同 |

**"ehentai 出图"这半句单独量了**（判据点名的就是它）：探针从搜索页走到详情页 `https://e-hentai.org/g/<id>/<token>/`，抠出详情页自己的图地址逐条打，判据是**图像签名魔数而不是 200**：

```
https://ehgt.org/w/02/319/29476-fuupgow6.webp -> code=200 server=nginx/1.30.4 mitigated=-
    ct=image/webp bytes=21366 magic=[52 49 46 46]  是图像=true
```

同页另三条：`g/opensearchdescription.xml` 200 但 `magic=[3C 3F 78 6D]`（`<?xm`，不是图）、`g/ygm.png` 200/190 字节、`g/blank.gif` 200/49 字节（站点装饰与占位图）。⇒ **真封面 21KB WebP 出图成立，且 `ehgt.org` 根本不在 CF 后面（`Server: nginx`）**。

另外同一颗 JVM 客户端跑真源脚本（`:engine-probe:ehentai -Pproxy=…`）：**ehentai 搜索出 25 条真结果**（标题、`https://e-hentai.org/g/4135074/…` 地址都是实的，带 `next=` 翻页游标），所以 S0-3 的"引擎能跑真源"已覆盖到第三个源。那一条日志里还读到 `s.explore[0].load is not a function` —— 探针的 explore 取数口径是按"数组项各自带 `load`"写的，ehentai 这支的形态不同（Android 侧 `JsComicSource` 怎么分支的没查，**不在本轮改**，留给阶段 2 对照）。

五条判读：

1. **判据里"ehentai 出图"这一半在纯 JVM 上是通的**：e-hentai 的搜索页与 exhentai 的 API 端点都没有被挑战页拦。⇒ 桌面侧不需要为 comic 源准备任何过盾载体，这一条比方案文档预期的乐观。
2. **`exhentai.org/` 的重定向死循环不是 CF**。API 端点直接 200 就是证据；根路径循环是 exhentai 缺登录 cookie（`igneous` 那枚）的已知行为。⇒ 它归"源账号"这一档，不归 S0-4，桌面侧与 Android 侧同条件、同表现。
3. **换 UA 就能过的这一条，实测跨域复现了**：`danbooru.donmai.us` 的 API 在应用默认 UA 下吃挑战页、在非浏览器 UA 下 200 出真数据，同一颗 TLS 指纹、同一瞬间、唯一变量是 UA。本仓 `ImageHeaderPolicy` 里"donmai.us 用非浏览器串"那条（原来只在图片档上实测过）**对 API 端点同样成立**。而且这条规则在 Android 侧已经生效 —— `headersFor` 用的是 `host.endsWith(key)`，`danbooru.donmai.us` 命中 `"donmai.us"`；`VeneraNetworkClient.userAgentFor` 又会在没有 host 绑定时回落到这张表。**所以桌面移植要把这两张纯 Kotlin 策略表原样搬过去**（它们目前是阶段 1 的去 Android 化对象，`UserAgentPolicy` 里那颗 `SharedPreferences` 要换 `EnginePorts` 那种接缝）。
4. **真正需要"过盾"这一动作的只有 SauceNAO 的搜索端点**，且**换 UA 救不了它**（两档都 403+challenge）。它需要的是执行 CF 的 JS challenge —— 而这正是 Android 侧 `CloudflareBypassActivity`（WebView）在干的事，桌面没有对应物。⇒ 判据的"后果"分支在此落地：**SauceNAO 在桌面标记不可用**，并按"降级必须可见"要求界面明说，不是一个静默的空结果。
5. 顺带量到根页与端点是两回事：`saucenao.com/` 根页 200、搜索端点 403（第一版探针只打根路径，读出来是"全绿"，那是假绿）。**后续 CF 类判据一律打业务端点。**

### 这轮探针的三个自审点（都是"读数看着对、其实没量到/记错账"那一类）

- **200 不等于图**：第一版在搜索页抠第一个 `ehgt.org` 链接，抓到的是 `g/opensearchdescription.xml` —— 200 且看起来"出图了"，实际是 XML。改成走到详情页 + 用魔数收口之后才拿到真读数。
- **正则尾巴造成假 403/404**：详情页的图地址出现在 `url(...)` 里，第一版字符类没排 `)`，抠出 `...webp)` 直接 404。差点把"CDN 挡不挡"读成"图挂了"。
- **别把账号问题记到 CF 头上**：`exhentai.org/` 的 `Too many follow-up requests: 21` 是重定向循环，同域 `api.php` 却直接 200 —— 挡它的是缺登录 cookie，不是 CF 盾。gelbooru API 的 401 同理（要 `api_key`）。这两条都不算 S0-4 的失分项。

### 那条"浏览器登录后手动导入 cookie"的替代路径，本轮**没有**验，也不能装验过

判据原文的通过态是"拿到 `cf_clearance` 且后续请求 200"。要量它必须有一枚真 `cf_clearance`，而这台机器上：Chrome/Edge 的 `cf_clearance` 是 **HttpOnly**，脚本通道读不到；从浏览器 cookie 库直接取属于取他人凭据，不做。所以下面这条是**推断，不是读数**，落地前要单独量：`cf_clearance` 与签发它的 UA + 出口 IP +（新版 CF 的）TLS 指纹绑定，把它从浏览器搬到 JVM OkHttp 上大概率不被认账 —— 上游 `master:lib/network/cloudflare.dart:122` 那句 `windows version of package flutter_inappwebview cannot get some cookies` 与此同族（方案文档 5.1 已记：Windows 过盾从来就是缺的）。若要坐实，两把钥匙：①用户在浏览器 devtools 里手抄一枚 cookie 给探针喂进去；②桌面侧改用 WebView2/系统浏览器载体，让**同一颗指纹**完成 challenge。

## 十、S0-6a 自接 DWM：**通道全通、材质没画 —— 候选 a 判负（硬伤在窗口层，不在依赖）**

探针 `desktop/src/main/kotlin/com/venera/desktop/MicaProbe.kt`，跑法 `./gradlew :desktop:mica`（任务里带 `--enable-native-access=ALL-UNNAMED`）。这条是第二节 S0-6 三条候选里 **a** 的可行性量测 —— 在你拍板之前先把它量成读数，别让你凭猜选。

| 环节 | 读数 |
|---|---|
| 运行期 JDK | `java=21.0.12.1 Eclipse Adoptium`（`:desktop:mica` 这个 JavaExec 走 toolchain 21，与 `:desktop:run` 起 23 那次不同 —— 别再拿旧读数当口径）。**FFM 在 21 上直接可用**，没要 `--enable-preview`，四笔调用全部返回 HRESULT。 |
| 拿 HWND · 路 1（`sun.awt.AWTAccessor` → `WComponentPeer.getHashCode()`） | ❌ **实测走不通**：accessor 的运行期类是 `java.awt.Component$1`（匿名类落在 `java.awt` 包里），反射 `IllegalAccessException`；而接口名 `sun.awt.ComponentAccessor` 在 JDK 21 里 **`ClassNotFoundException`**（已不存在）。要修得 `--add-opens java.desktop/java.awt=ALL-UNNAMED` 并绑死 JDK 内部。 |
| 拿 HWND · 路 2（`user32.FindWindowA(null, title)`） | ✅ `HWND=0xb00d5e`，**零 JDK 内部依赖、零 add-opens**；代价是窗口标题要唯一且纯 ASCII（探针标题因此改成 ASCII，中文标题在 `FindWindowA` 的 ANSI 通道下不保证）。 |
| `DwmSetWindowAttribute(20 深色标题栏, 1)` | HRESULT=0，**肉眼生效**（截图标题栏由浅变深）。⇒ 回读通道与属性面都可信。 |
| `DwmSetWindowAttribute(38 backdrop, 2 Mica)`（装饰态窗口） | HRESULT=0、`DwmGetWindowAttribute` 回读 **=2 认账**，但**前后两张截图 sha256 完全相同** ⇒ 设上≠看得见，客户区不透明把它盖死了。 |
| `DwmExtendFrameIntoClientArea(MARGINS{-1,-1,-1,-1})` | HRESULT=0，截图有变化（与深色态不同帧）。 |
| **`Window(transparent = true)`** | ✅ **CMP 1.12 的公开参数**（不必反射 skiko 内部！），但硬要求 `undecorated = true` —— 单开 `transparent` 在组合期直接抛 `IllegalStateException: Transparent window should be undecorated!`（`ComposeWindowPanel.setWindowTransparent`）。开成 `transparent+undecorated` 后**透明真的生效**：`_qa/s06a-before.png` 里桌面图标与壁纸整片透出来（同一矩形此前是 34KB 实心帧，之后是 337KB 高熵帧）。 |
| 透明态下再设 backdrop | 截图从"透出壁纸"变成**一片均匀暗场**（`_qa/s06a-mica.png`，620×420 只压成 4.1KB ⇒ 几乎零方差）。 |

**最后一条读数我不敢替它下结论**：那片均匀暗场既可能是 Win11 的 Mica（暗档 Mica 是"壁纸去饱和 + 深色叠加"，本来就接近纯色），也可能是 DWM 把 backdrop 画成了实心 fallback。**它不是壁纸透视**（图标不再透出），但也**没有 Mica 该有的那种渐变**。

### 同日补：backdrop 四档扫描 + `WS_EX_NOREDIRECTIONBITMAP` 尝试 —— **候选 a 判负，且是硬伤**

判据不靠眼看：每帧算**均值 RGB + 亮度标准差 + PNG 字节数**，并在窗口之外同尺寸取一笔**桌面参照**（Mica 的色调是从壁纸算出来的，没有参照就没法区分"材质"与"fallback 填充"）。跑法不变，日志 `_qa/s06a-sweep3.utf8.log`。

| 帧（620×420） | 均值 RGB | 亮度标准差 | PNG 字节 |
|---|---|---|---|
| 桌面参照（窗口之外） | (47,41,36) | **46.9** | 61,939 |
| 未设 backdrop | (140,151,157) | 57.1 | 442,833 |
| `NONE(1)` | (140,151,157) | 57.1 | 442,833（与上一行**逐字节相同**） |
| `MICA(2)` | (32,32,32) | 10.0 | 4,148 |
| `ACRYLIC(3)` | (84,84,84) | 7.8 | 4,354 |
| `TABBEDWINDOW(4)` | (32,32,32) | 10.0 | 4,148（与 MICA **逐字节相同**） |

四条判读：

1. **材质根本没画**：Mica 与 Tabbed 给出**同一串字节**，Acrylic 也只是换个灰 —— 真 Acrylic 会实时模糊桌面内容（高方差 + 带壁纸色偏），真 Mica 会带壁纸的暖色偏（参照是 (47,41,36) 这种不平衡值，而三档全是 R=G=B 的死灰）。方差 7.8~10 那点起伏只够是我们那行文字。⇒ 看到的是 **DWM 的 fallback 填充色**，不是材质。
2. **补最后一块砖也没补上**：Win32 上 DWM 材质要求窗口**没有重定向位图**，所以试着加 `WS_EX_NOREDIRECTIONBITMAP(0x200000)` —— `SetWindowLongPtrW` 返回旧值 `0x0`、回读扩展样式仍是 `0x80000`，**那位根本上不上**（该样式只能在 `CreateWindowEx` 时指定，事后改无效），`SetWindowPos(SWP_FRAMECHANGED)` 返回 1 也没用；B 档两帧与 A 档**逐字节相同**。
3. ⇒ **候选 a 在"沿用 Compose Desktop 那颗 AWT 窗口"的前提下不成立**。要拿到真材质必须自建 HWND（`CreateWindowEx` + 把 skiko 挂进去），那已经不是"换个依赖"的工作量，而是替掉 CMP 的窗口层。
4. 顺带一条 FFM 的硬坑（下次直接撞上）：`invokeExact` 的**静态返回类型必须与句柄签名一致**，把 `SetWindowLongPtrW` 的返回值当语句丢弃会编译成 `void`，运行期抛 `WrongMethodTypeException` —— 我因此白跑了一整轮 B 档，是回读 `0x80000` 才暴露出"上一轮根本没执行到"。

**给拍板的落点**：S0-6 现在只剩两条真路 —— **b. haze 应用内模糊**（compose-fluent 的 pom 里就带 `dev.chrisbanes.haze`，观感降一档但**不会静默失效**，且与本仓现有 4 个玻璃消费点同一套画法）；或 **c. 明确不做材质**（桌面侧一律实底 + 在设置里写清楚）。a 只在"愿意自建窗口层"时才重新变得可选。


**这一段当时读到这儿停住**：三块砖（FFM / HWND / 官方透明参数）都在，但"设上 backdrop 后画面变成均匀暗场"还不能判它是材质还是 fallback —— 下面那节扫描把它判死了。留在这儿的理由：`transparent` 与 `undecorated` 的绑定、以及 `Robot` 帧字节数当"画面变没变"的判据，这两条本身是可复用的读数。

## 十一、端到端最小闭环：**桌面窗口里真的刷出了真源的封面**（同日追加）

前面十条读数都是"单点能不能"。这一件把它们接成一个**能跑的东西**：

```
真源脚本 → GraalJS 宿主 → 桥协议 → 真网络(Cookie/gzip/WebP) → skia 解码 → Compose Desktop + fluent 渲染
```

| 构件 | 位置 |
|---|---|
| 可复用的引擎会话（装载 / explore / search / 取缩略图） | `engine-probe/.../EngineSession.kt`（新） |
| 桌面窗口（34 个源在侧栏 + 分区网格 + 状态行 + 失败上屏） | `desktop/.../VeneraDesktop.kt`（新） |
| 跑法 | `./gradlew :desktop:app -Pkey=jm -Pproxy=127.0.0.1:7890`；加 `-Pshot=路径` 则自截图后退出（无人值守取证） |

`:desktop` 只加了一行 `implementation(project(":engine-probe"))` —— 它直接吃 `:app` 那批已去 Android 化的 handler，**没有第二份实现**，所以桌面与 Android 的差异仍然只可能来自引擎与 UI 层。

### 三个源的读数（截图 `_qa/desktop-{jm3,goda,nhentai3}.png`）

| 源 | 探索结果 | 出图 |
|---|---|---|
| `jm`（禁漫天堂） | 6 个分区 / 72 条 | **30/72**（受探针自设的"取图总量闸 30"限制，不是失败） |
| `goda` | 4 个分区 / 48 条 | **29/48**（同上；另有源侧 403 会直接写在状态行） |
| `nhentai` | 2 个分区（Popular Now 5、New Uploads 12） | **17/17 = 100%** |

截图核对：`_qa/desktop-jm3.png` 与 `_qa/desktop-goda.png` **已目视核对**（侧栏 34 源 + 选中态、分区标题、封面与中文标题、状态行都成形）。`nhentai` 那一行**只有 stdout 读数** —— 它的同名截图事后查明是桌面壁纸（字节数与壁纸帧一模一样），成因与闸门见第十二节。

### 阅读链路也接上了：详情 → 章节 → 页表 → 去混淆取图

点卡片进阅读器（`VeneraDesktop` 的 `open` 分支），跑 `jm` 的实测读数：

```
D_详情 <标题> 章数=61 前3=[Episode(name=第1話, id=1193811, placeholder=false), …]
D_阅读 1193811 页数=8
```

即 `loadInfo` 出 **61 章**、`loadEp` 出页表、前 8 页经 `onImageLoad` → 取字节 → **块序倒回** → 解码成位图。这条链里挖出两条**必须与 Android 侧对齐的语义**（我第一版两条都写错，靠读数暴露）：

1. **`chapters` 是 `{话号: 话名}`，key 才是 id**（我按 value 当 id → `loadEp` 当场 `Invalid Data`）；值还是 Map 的是分组表、要摊平；完全没有 chapters 时补一个**沿用漫画 id 的占位章**，取页时话号要还原成 `null` —— 与 `JsComicSource.kt:490-533` 同一套。
2. **页表的键有两家**：jm 系回 `images`、多数源回 `pages`，**优先序与 Android 侧一致**（`JsComicSource.kt:642-643` 先 `images` 后 `pages`）。我第一版只认 `pages`，症状是"loadEp 成功但 0 页"。

JVM 侧的去混淆与梯度判据已从探针抽成**唯一一份** `JvmImageOps`（`descrambleBlocks` / `verticalGradient` / `decode` / `encodePng`），`ProbeMain` 与桌面 App 共用，不再各写一版。⚠️ 它与 Android 侧 `ImagePipelinePolicy.reorderBlocksBottomUp` 仍是**同构的两份实现**（那边 `android.graphics.Bitmap`、这边 `BufferedImage`），**像素级对拍尚未做**，挂在阶段 3。

**一条没归因干净的观察**：`nhentai` 那次不带 `-Pshot` 的长跑（400 秒后被 timeout 杀掉）里，日志出现了**连装 5 个不同源**（nhentai → CCC追台漫 → jcomic → ? → Picacg）。我没有发过任何输入，所以最可能是**你在窗口上点了**（那就正好证明侧栏切换链路通）；但也可能是 `NavigationView` 自己回调了 `onClick`。**这条我先记成"未归因"，不当成功能验证** —— 下次跑带 `-Pshot` 的短命窗口时若复现，就要当成 fluent 侧的缺陷查。

### 三条新事实（阶段 2 接线时直接照用）

1. **`explore` 的返回形状至少有三种**，`search` 又是另一种：
   - `search` → `{comics:[…], maxPage}`；
   - `jm` / `goda` 的 `explore` → **分区数组** `[{title, comics:[…]}, …]`；
   - `nhentai` 的 `explore` → **多套一层** `{data: [分区数组]}`。
   解析器现在三种都兜（`EngineSession.call`），判据是"列表项里有没有 `comics` 键"和"`data` 里有没有 `data` 键"。
2. **`ehentai` 的 explore 直接报 `s.explore[0].load is not a function`** —— 它的 explore 项形状又不同。桌面侧当前把错误原样写在状态行（不静默变空页），Android 侧 `JsComicSource` 怎么分支的**本轮没查**，留给阶段 2 对照。
3. **换 HotSpot 版本号拿不到 GraalJS 的 JIT** —— 这条已实测（`./gradlew :engine-probe:bench -Pjdk=21` / `-Pjdk=25`，日志 `_qa/bench21.log` / `_qa/bench25b.log`）：

   | 指标 | JDK 21.0.12 | JDK 25.0.4 | 差 |
   |---|---|---|---|
   | `fib(26)×10`（纯 JS 计算） | 269ms | **256ms** | −5% |
   | 预热 `fib(24)×3` | 76ms | 47ms | −38% |
   | `JSON.stringify/parse ×4000`（桥协议每笔都走） | 100ms | **59ms** | −41% |
   | 装载 `jm` 源 | 1478ms | 1352ms | −9% |
   | `explore(jm)` 端到端（含网络） | 925ms | 705ms | −24% |

   **关键是两档的警告都还在**：JDK 25 上仍打 `The polyglot engine uses a fallback runtime that does not support runtime compilation to native code`，只是"版本不兼容"那句消失了。⇒ 我们引的 `org.graalvm.polyglot:js-community` 在**普通 HotSpot 上永远是解释器档**，要 Truffle JIT 得换 **GraalVM/JVMCI 运行时**（或补 compiler 构件），不是把 `jvmToolchain` 从 21 调到 25。
   **绝对值判据**：解释器档下"装载一个源 1.4s、探索页 0.9s"，与 Android 侧首屏同一量级 ⇒ **JIT 不是路线阻塞项**，只是可选优化。所以原第 5 条待拍板改写成："要不要为桌面捆绑 GraalVM 运行时"，而不是"锁哪个 HotSpot 版本"。
   顺带：为跑这笔对照，`settings.gradle.kts` 加了 `org.gradle.toolchains.foojay-resolver-convention`（让 gradle 能按需自动下载 toolchain；JDK 25 落在 `~/.gradle/jdks`，不动系统 JDK）。

## 十二、留档的坑

- **APK 体积读数不可信的那次**：本轮 02:33 的 `app-universal-debug.apk` 是 98,225,247，03:07 变成 101,702,873。前者吃的是上一轮会话留下的 Kotlin 增量缓存（同一时刻 `:app:compileDebugKotlin` 报的是 UP-TO-DATE），改过 `engine/` 之后是全量重编 —— **拿它当基线算差值本身就不成立**。已排除真实风险：新 APK 的 20 个 dex 里 `graalvm`/`polyglot`/`composefluent`/`mayakapps`/`twelvemonkeys`/`skiko`/`awt/image` **全部 0 命中**，桌面侧没有任何东西进 Android 包。要坐实体积口径，得拿 HEAD 开 worktree 现编一份对照。

- **`Robot` 自截图会静默产出假证据**（本轮废掉四张图）：机器**锁屏或切到别的虚拟桌面**时，窗口矩形照样存在、Win32 的 visible 位照样是 true，而 `createScreenCapture` 截回来的是**一整张纯壁纸**（连任务栏都没有）。三道必须的闸：
  1. **标题带 PID** —— 上一轮被 `timeout` 杀掉的 gradle 客户端会留下还开着的探针窗，标题相同则 `FindWindow*` 命中哪扇全凭运气；
  2. **用 `FindWindowW` 不用 `FindWindowA`** —— A 版走 ANSI 码页，**带中文的标题匹配不到**（实测返回 0），而返回 0 不是 null，`Long?` 的判空会放它过去；
  3. **截之前先 `WindowFromPoint(窗心)`** —— 只有它能识破"矩形对但画面不是本窗"。三道都过才落 PNG，否则打 `D_截图 无效：…`，**不产出假证据**。
  教训：**截图前加对照（同屏再抓一张全屏）**是这次定位的关键 —— 看到全屏也是纯壁纸才知道问题不在窗口矩形。
- `jvmToolchain(21)` 只约束了编译；`:desktop:run` 起的进程实际是 `eclipse_adoptium-23-amd64-windows.2\bin\java.exe`。要锁运行期 JDK 得单独管 `javaLauncher`。
- 探针窗口的 `println` 信号（`S01_COMPOSED_OK`）是**无 GUI 也能判组合成功**的取数口径，比截图可靠，后续 spike 沿用。
- **CMP 1.12 的两处 API 形状**（写桌面代码时会当场撞上，先记）：
  - `BasicText(..., color = Color.White)` **不再接受 Color** —— `color` 参数已是 `ColorProducer?`（带 `ColorScope` 接收者的 lambda），要写 `style = TextStyle(color = ...)`。
  - FFM 的字符串分配在 **JDK 21 叫 `arena.allocateUtf8String(s)`**，22+ 才统一成 `allocateFrom(s)`。本模块 `jvmToolchain(21)` ⇒ 只能用 21 的名字；**换运行期 JDK 时这行要跟着改**（`MicaProbe.kt` 里已就地标注）。
- **`Robot().createScreenCapture` + PNG 字节数**是个便宜的"画面变没变"判据：同一矩形 34,925 → 337,449 → 4,103 字节三档，分别对应"实心"、"透出壁纸（高熵）"、"均匀暗场（零方差）"。比人眼看缩略图可靠，且两帧 sha256 相同就是**铁证没变**。

---

## 十三、阶段 1 地基盘点：持久层与依赖面（子代理盘点 + 我复跑的数）

方法账先记明：这份底账由一个只读检索的子代理产出，**它的负结论与计数我复跑过**，改处的地方写明在下面（不写明就是在往下游传假数）。

1. **不是 Room，是手写 `SQLiteOpenHelper`** —— 全仓 `@Database` / `@Entity` / `Room.database` / `SQLDelight` / `Realm` **0 命中**（我复跑 Grep 确认）。两棵库：
   - `data/db/VeneraDatabase.kt:11`（v3，6 张表：history / favorite / comic_source / reading_stats / favorite_images / content_guard_rules），**v2 迁移是 DROP 重建**（:130-135）—— 移植时这段语义要显式复刻，别当"没迁移"。
   - `data/db/LocalFavoriteDatabase.kt:25`（v1，`onUpgrade` 空）—— **每个收藏夹一张动态表**，表名来自用户输入（:63-85），运行时 `ALTER` 追列（:111-124）。这个形态 Room / SQLDelight 都套不上 ⇒ **桌面侧保留手写 SQL 是唯一不重写的路**。
2. **摸 Android SQLite 类型的文件是 14 个，不是子代理报的 8 个**（复跑 `SQLiteOpenHelper|android\.database\.|ContentValues`）：3 个 DAO、2 个 Database、`LocalFavoritesManager`、`FavoriteImagesManager`、`BackupManager`、`ForeignArchiveImport`、`ReadingStatsManager`、`ContentGuardManager`、`VeneraReaderScreen`、`GallerySaver`、`GalleryTagDictionary`。子代理漏了 `GalleryTagDictionary.kt:162`、`FavoriteImagesManager.kt`、`VeneraReaderScreen.kt` 三处 —— 备份/导入/图库词典都是**整块语义**，不是一个 DAO 的活。
3. **键值面**：`getSharedPreferences` **19 命中 / 14 文件**（我复跑；子代理报 16 处 / 15 文件）。集中封装只有 `data/prefs/VeneraPreferences.kt:61` 一处，其余旁路直连的包括 `PersistentCookieJar`、`UserAgentPolicy`、`ComicSourceManager`、`ComicListPreferences`、`ComicMetricsCache`、`ContentGuardManager`、`WebDavSyncManager`、两个账号（`GelbooruAccount`、`SauceNaoAccount`）、`HomeViewModel`、`SearchViewModel`、`GallerySearchViewModel`、`CopyMangaSource`。⇒ 阶段 1 的第一刀应是 **`KeyValueStore` 门面 + 13 处旁路收口**，而不是先动 UI。
4. **下载记录不在库里，是 JSON 文件**：`download_tasks.json`（`ComicStorageRoot.kt:26`）、`chapter.json`（`DownloadManager.kt:524`）、`comic_info.json`（`LocalComicManager.kt:45`），解析吃 **`org.json.*`**（Android framework 内置）⇒ 桌面要么引 `org.json:json`（纯 JVM 构件），要么换 gson（仓库里已有）。
5. **路径面**：`filesDir` / `cacheDir` / `Environment.getExternalStorage*` / 硬编码 `/storage/` 散在约 25 个文件里，要收进一个 `AppPaths`。**`getExternalFilesDir` 全仓 0 使用**（好消息：没有分区存储的分支要搬）。脚本副本目录 = `filesDir/comic_source`（`ComicSourceManager.kt:100`），与桌面侧 `JsSourceDataStore(File(dataDir,"comic_source"))` 已经同构。
6. **依赖判定**（`gradle/libs.versions.toml` + `app/build.gradle.kts`）：**Android-only** = 框架 SQLite（无坐标）、`androidx.work:2.9.1`、`androidx.core-ktx:1.12.0`（prefs 用了它的 `edit` 扩展）、`org.json`（framework）、`media3:1.11.1`；**纯 JVM 可用** = okhttp 4.12.0、gson 2.11.0、kotlinx-serialization 1.11.0、jsoup 1.18.1、coroutines（`-android` 换 `core` 即可）；**不确定，别当可用** = coil3 3.6.2 的桌面变体（KMP 有 desktop 构件，但 `VeneraApp.kt:5` 吃 `PlatformContext`、gif 解码器未验；S0-8 那条已单独证伪过 `zoomable-image-coil3` 是纯 Android aar）。
7. **`WorkManager` 只有一个 worker**（`FollowUpdatesWorker.kt`，追更周期任务，`VeneraApp.kt:59-62` 排程）⇒ 桌面用 `ScheduledExecutorService` 自己排即可，**没有对位库**这件事不用找。
8. **"保存到相册"这簇在桌面语义不存在**：`GallerySaver.kt:120-136`、`CoverViewerScreen.kt:114`、`VeneraReaderScreen.kt:1995` 吃 `MediaStore`；`GallerySaver` 本来就是"MediaStore 主 + File 兜底"的双路径（:36-45）⇒ 桌面裁到只剩 File 分支，风险中低。

阶段 1 的三条 hardest blocker（按"改动面 × 风险"）：**①** DAO 层换 `org.xerial:sqlite-jdbc`（纯 JVM、成熟）并**保留现 SQL**，14 个文件、含备份/导入这类整块语义，外加 v2 `DROP` 迁移要照抄；**②** `AppPaths` + `KeyValueStore` 两个门面的建立与 13 处旁路替换，**风险在 13 个 prefs 文件的数据搬迁**（Android 上已有的 XML prefs 与桌面新存储不可能自动对齐，跨端导入功能会撞这个）；**③** 追更调度与相册语义的平台替代。另有一条**排在它们前面的**：第七节末"追加更正"里的**签名/白名单**——它不解决，桌面产物在这台机器上连数据目录都建不出来。
