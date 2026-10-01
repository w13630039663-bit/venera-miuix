---
name: reference-gradle-cache-sources-jars
description: 库 API 的真相在 gradle 缓存的 *-sources.jar 里（material3 / miuix 都有），别猜签名也别 javap；含 carousel、Coil 3 五个真相、material3 菜单重载陷阱与顶栏尺寸的既有结论
metadata:
  type: reference
---

**去哪看**：`~/.gradle/caches/modules-2/files-2.1/<group>/<artifact>/<version>/<hash>/` 下面通常同时躺着 `*-sources.jar` 与 `*-samples-samples.jar`（material3 是 `material3-<ver>-samples-sources.jar`）。直接 `unzip` 到临时目录读 `.kt`，比猜 API 快。

**注意**：`find ~/.gradle/caches ...` 全扫会跑满 2 分钟超时（2026-09-27 又犯一次）；先按上面的定长路径 `ls`，或用 `ls files-2.1/<group>/<artifact>/` 挑版本目录。`/tmp` 在 Git Bash 里要用 `cygpath -w` 换成 `C:\Users\leimi\AppData\Local\Temp\...` 才能喂给 Read 工具。

**没有 sources jar 时的正解（2026-09-27 实测，推翻本条目旧版"别 javap / 本机没有 unzip 与 javap"那句）**：`unzip <lib>.aar -d x` 拿到 `x/classes.jar`，再用 **gradle 自己下的那个 JDK** 去 javap —— `~/.gradle/jdks/eclipse_adoptium-21-amd64-windows.2/bin/javap.exe -classpath x/classes.jar 'androidx.media3.ui.PlayerView'`。`unzip` 与 `javap` 都通，**PATH 里没有 javap 不等于机器上没有 JDK**。判"某个 UI 元素到底存不存在"再补一刀：`unzip` aar 的 `res/layout/*.xml` 去 grep 那个 id，或 `grep -ao "字面量" SomeClass.class` 读常量池。最快最硬的裁判还是**编译器**：按猜的名字写一次，报错信息就直接给了正名。

用这条退路定到的 **media3 1.11.1 两处真相**（猜一定会猜错）：① `DefaultHttpDataSource.Factory` 上**没有** `setRequestProperties`，正名是 **`setDefaultRequestProperties`**；② `PlayerView` 的控制器**本来就带一枚全屏钮**（`exo_player_control_view.xml` 里的 `fullscreenButton` / `minimalFullscreenButton`），但 `PlayerControlView` 按"有没有设过 `FullscreenButtonClickListener`"判可见 —— 没设过就等于那枚钮不存在（别急着"自己造一枚"）。

**图标名同样要查，别凭印象**：material-icons-extended **1.7.0 里没有 `Icons.Outlined.AutoRecommended`**（编译直接 unresolved，而它看着"必然存在"）。这一版 `outlined/Auto*` 只有 AutoAwesome / AutoAwesomeMosaic / AutoAwesomeMotion / AutoDelete / AutoFixHigh·Normal·Off / AutoGraph / AutoMode / AutoStories / Autorenew。查法：`unzip -l classes.jar | grep -oE "outlined/Auto[A-Za-z0-9]*Kt"`。

**已经查到的、可以直接复用的结论**：

- **Coil 3.6.2 的五个 API 真相**（都踩过）：①`ImageRequest.Builder` **没有 `.key()`**，只有 `memoryCacheKey` / `diskCacheKey`；②`coil3.BitmapImage` 是 **internal**，公开入口是扩展 `Bitmap.asImage()`（`coil3/Image_androidKt`）；③fetcher 返回 `ImageFetchResult(image=…, isSampled=…, dataSource=…)` 会被 `EngineInterceptor` 认下并**整个跳过解码器** —— 于是 Coil 的降采样也一起失效，要按 `options.size`（`Dimension.Pixels.px`）自己做 2 的幂；④`LocalContext.current` 是 @Composable 取值，**不能出现在 `remember {}` 里**，得先在提升为局部变量；⑤`imageLoader.execute()` 返回的位图**就是内存缓存里的那一个实例**，拿到后 `compress` 完**绝不能 `recycle()`**（阅读器正在显示它，回收=下次绘制"使用已回收位图"崩）。另：`coil3.size.Size.ORIGINAL` 两维都是 `Dimension.Undefined`，配合③意味着我们自己那套 sampleSize 决策会拿不到像素盒子而退化成 1（原尺寸）—— 想要"存一份最清晰的"就正好用它。

- **material3 1.5.0-alpha22 的 `DropdownMenuItem`**：`Menu.kt` 里躺着十来支重载，**绝大多数带必填 `shape: Shape` 或 `shapes: MenuItemShapes`**（都是 `@Deprecated(HIDDEN)` 的二进制兼容支），公开可命名调用的 `expect fun DropdownMenuItem(text, onClick, …)` **没有 `supportingText`**。所以菜单项要"副标题"只能在 `text = {}` 里自己放 `Column { Text(主) Text(副) }`。同理 `TooltipBox` / `PlainTooltip` / `TooltipPlacement` 在这一版解析不稳，别指望。

- **material3 1.5.0-alpha22 的 carousel**：`HorizontalMultiBrowseCarousel` 与 `rememberCarouselState` **不需要** `@OptIn(ExperimentalMaterial3Api)`（只有 `HorizontalCenteredHeroCarousel` 要）。`CarouselItemScope` 这一版**没有** `isCurrent` / `currentItem`，判焦点只能自己写 `state.currentItem == index`；也没有 alpha/scale 的默认工具，淡出要自己按 `carouselItemDrawInfo.size` 在 min/max 之间插值。官方用法抄 samples jar 的 `CarouselSamples.kt`。
- **miuix 0.9.4-rc01 的 `TopAppBar`**：`CollapsedHeight = 52.dp`、大标题用 `textStyles.title1 = 32.sp`、底部留白 `LargeTitleBottomPadding = 4.dp`，并且它**内部自己挂了 `windowInsetsPadding(systemBars.only(Top))`** —— 所以页面侧再叠一次 `statusBarTop` 就会与它错位，尺寸类遮挡一律以「实测顶栏高度」为准（见 [[project-card-size-drivers]]）。

相关：[[project-home-recommend]]、[[project-loading-indicator-direction]]
