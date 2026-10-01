# 画廊第四轮：8 条真机反馈 + 画廊设置（2026-09-29）

用户跑完第三轮那版 APK 后回了 8 条（附 3 张截图 + 一张「看哪一期」弹层截图）。
本文记：**每条的成因（实测/读码得到的）→ 落法 → 还没验的**。四条拍板见 §〇。

## 〇、本轮四条拍板（AskUserQuestion，全选推荐项）

1. **共享元素**：大图页**保留独立 Activity**（blur-behind 实时模糊不丢），把"卡片那一帧"改成
   按起点矩形→大图矩形插值飞行。不搬回主 Activity 用真 `sharedElement`。
2. **刷新口径**：缓存**跨天则后台补拉一次**（不是"严格只手动下拉"，也不是"进 Tab 就补拉"）。
3. **自动播放**：沿当前那面墙自动翻到下一张、**到底停**。（→ 落在批次 C，本轮未做）
4. **画廊设置分两批**：批次 1 = 骨架 + 迁移 + 浏览/下载/缓存；批次 2 = 动图三档 / 预加载 /
   自动播放间隔 / 屏幕常亮 / 背景颜色 / AI 角标。

## 一、逐条成因与落法

### 1 返回后不保存滚动进度

**不是**「导航条目重建」那条老根因 —— 大图页是另一个 Activity，MainActivity 上那一条目的地
的组合**没停**。真凶是这一句：

- `GalleryScreen` 里 `LaunchedEffect(svm.searchGeneration) { gridState.scrollToItem(0) }`，
  而 `popContext()`（返回弹上一轮）**也**推这一代 → 结果抄回来了、人却被扔回顶部。

落法：`GallerySearchContext` 多带一个 `scrollIndex`（压栈时抄当前值），`popContext()` 把它写进
`pendingScrollRestore`，页面 `scrollToItem(consume() ?: 0)`。滚动位置由
`snapshotFlow { gridState.layoutInfo.visibleItemsInfo.firstOrNull()?.index }` 报回，
不挂 `onScroll`（程序滚动与条目增删它都不报，会把 0 记成"用户看到的深度"）。
真发一笔新查询（`beginSearch`）清掉待恢复值 —— 新那轮本来就该从第 1 张看起。

### 2 展开态点「排行」菜单闪一下就没 + 一闪一闪

链条（读码定位到行，不需要日志）：

`DropdownMenu` / `AlertDialog` 是**可聚焦弹层** → 焦点离开输入框 → IME 落下 →
`GallerySearchArea` 那道"键盘收起就收成一条"的 effect 点亮 → `mode=RESULTS` →
顶栏折平进度还在 1 → `searchCollapsed` 成立 → 整块搜索区被移出组合 → **菜单的锚点跟着没了**。
"一闪一闪"是卡片 `shrinkVertically` + `gridTopPadding` 动画与弹层抢帧。

落法：判据抽成纯函数 `GallerySearchCollapse.shouldCollapseOnImeHidden(imeWasVisible, popupOpen,
reverseOpen)`，effect 的 key **只留 `imeVisible`**（把两个弹层开关写进 key 会引入"关菜单那一下
补一次收起"的新 bug），弹层开关在 effect 里就地读。弹层造成的那次下落**既不收起也不清上升沿**。

### 3 「按年」还是一屏天数日历

**能力边界先查了库源码**（不是猜）：Material3 1.5.0-alpha22 的 `DatePicker.kt` 里
`private fun YearPicker(...)` 是私有的，`showModeToggle` 只切「日历 ↔ 数字输入」，
公开 API 到不了年份列表；Miuix 0.9.4 也没有日期/年份控件。→ **只能自绘**。

落法：`GalleryRankings.yearChoices(todayUtc)`（2007 地板沿用既有实测常量，由近及远）+
弹层里 `按年` 走 `FlowRow` 年份芯片、`按月` 保留日历（点任意一天取那一月）。
两档各存各的选中值（`pickedYear` 与日历的 `selectedDateMillis` 分开），否则"日历上点过的某一天"
会冒充"选过的那一年"。

### 4 点图片要共享元素

`GalleryFlyIn` 本来就递了"卡片那一帧"位图，只是**没用过它的坐标**（只当占位）。
本轮补 `origin: Rect`（窗口坐标，卡片本来就是这么裁的），大图页加一层 `drawBehind` 飞行体：
起点矩形 → 画面那一框回报的落点矩形逐帧插值，最后四分之一程淡出交棒给框内占位。

三处细节都是坑：
- 落点由 `GalleryViewerMedia` 的 `onGloballyPositioned` 回报，**不自己复制定框算式**（那是第二处真相）；
- 落点最多等 400ms，等不到就**放弃飞行**（停在半路的飞行体比不飞难看）；
- 飞行期间**不往画面框里摆同一帧占位**，否则一眼看见"两个起点"；
- 能飞时页面不再"从下沿抬上来"，改 120ms 淡入 —— 两套动作并行就是散（漫画侧换 shared axis 时同一条理由）。
- 拿不到起点（收藏页、反搜入口）照旧抬页，退路必须存在。

### 5 切 Tab 要方向化水平滑动

`NavHost` 的 `enterTransition/exitTransition` 原本恒等于 `materialSharedAxisXIn(forward = true)`
—— 方向是写死的，与"往哪个 Tab 走"无关。落法：两端**都是主 Tab** 时换成整页
`slideInHorizontally/slideOutHorizontally`（方向 = 目标序号 − 起始序号），其余目的地仍走 shared axis
（列表→详情是"同一本书换个容器"，封面在按自己的曲线飞，不能被整页横推盖掉）。

⚠️ **保护域豁免**：`Navigation.kt` 是冻结文件，本轮第二次点名豁免（第一次是撤内容区横滑）。
判据 `NavDestination.tabIndex()` 与页面里 `currentTab` 那串 `hasRoute` 同源（含 CategoriesRoute
重定向那条），两处口径不一致会长成"底栏点亮了、动画却没方向"。

### 6 画廊要存历史缓存 + 手动下拉刷新

**成因**：`GalleryViewModel.seed = System.currentTimeMillis()` 只在 VM 存活期内有效，
进程一没就得重取 —— 同进程内切 Tab 其实不重取（loadedKey 守卫 + 条目作用域 VM 都活着）。

落法（照 `HomeViewModel` 那份"缓存 + 节流"先例，它已在首页推荐区验证过）：
- 新增 `gallery/data/GalleryFeedCache.kt`：`filesDir/gallery_feed_cache.json`，
  **复用 `GalleryFavorite` 那套 @Serializable 形状与 `toFavorite()/toPost()` 两条既有转换**
  （这一屏要的字段与收藏是同一批，写第三套 schema 就是两份漂）；
- `GalleryViewModel` 改 `AndroidViewModel`，`init` 里**同步**读（异步读会抢不过"这一轮要不要联网"那次判断）；
- 跨天判据抽纯函数 `GalleryFeedRefreshPolicy.shouldRefetch`；补拉是后台的：
  页面摆加载环的条件是 `isLoading && !hasPosts`，有内容就不清屏；
- 失败保住缓存（`HomeViewModel.failRecommend` 同法）；
- 下拉刷新：M3 `PullToRefreshBox` 套在两面墙外面那个 pager 上，动作分发到既有的
  `retryFromUser()` / `retryForYouFromUser()`（这两条已经会清熔断 + force，是真刷新）。

**本轮范围只到日榜缓存**：猜你喜欢那一屏要复原的是 8 个字段（`seenKeys` / `sitesDone` /
`page` / `perSite` / `excludedFavourite` / `queryBySite` / `stage` / `generation`），
少复原一个就产出"到底了还在转"那类假读数；而它**只在滑到那一页时**才取数，
不是用户抱怨的那一下。→ 它拿到了下拉，没拿到缓存。这条要用户点头再补。

### 7 GIF 放不出来

**结构性成因**：Coil 3.6.2 在 Android 上内置只有 `StaticImageDecoder` 与 `BitmapFactoryDecoder`，
后者显式请求 `animated = false` → GIF 只能解出**首帧**；仓库里此前没有任何一处注册过动图解码器。

一处**计划错**要记下来：方案里写的 `coil3.gif.ImageDecoderDecoder.Factory()` 在 3.6.2 里**不存在**
（那是早期文档的名字）。拆开本机 aar 的 classes.jar 实测，这一档叫
**`coil3.gif.AnimatedImageDecoder.Factory`**（还有 `GifDecoder`、`AnimatedTransformation` 等）。

落法：`io.coil-kt.coil3:coil-gif:3.6.2` 只挂到**画廊那把 ImageLoader** 上
（`components { add(AnimatedImageDecoder.Factory()) }`，漫画侧那把不动 —— 隔离裁决）。
循环不用另设，`AnimatedImage` 默认无限循环。

**墙上会不会动起来**：实测过了 —— 两站的缩略档**都是 jpg**：
yande.re `/post.json?limit=100` 100/100 条 `preview_url` 是 `.jpg`（原档分布 jpg 53 / png 41 / webp 6）；
Gelbooru 的 `tags=gif` 列表页 HTML 里 42/42 枚缩略图是 `thumbnail_*.jpg`。
所以**默认档**（预览清晰度 = 预览）的墙上不会出现一屏乱动，不需要"停首帧"那套额外机制。

⚠️ 这一段的前提当晚就被同一批新开关打破：**「清晰预览」把 `largeUrl` 搬上了墙**，而 gif 条目的
large 档就是动图本体；更要命的是**视频条目**（mp4/webm）在两站的 large 档会落到原片 —— 
一条就是 16~26 MB。见 §五.2。批次 C 的三档设置只管大图页。

### 8 单独的画廊设置（批次 1）

新分区 `SettingsSubScreen.GALLERY` → `feature/settings/GallerySettings.kt`，
用现成组件（`SettingsPage` / `SettingsGroup` / `SettingsSelect` / `SettingsAction`），
分组四块：账号与密钥 / 浏览与布局 / 缓存 / 下载。另加一条**只说明、不摆开关**的
「内容与屏蔽」行 —— 分级与黑名单是漫画与画廊**共用**的一把判据，这里再摆一枚开关就是第二份真相。

- 新组件 `SettingsGroupTitle`：只有标题、没有卡片容器。因为账号那一组摆的两张卡**本身已是
  VeneraCard**，套进 `SettingsGroup` 就是卡里嵌卡。标题样式仍由 `SettingsGroup` 走这一处。
- **迁移**（不是复制）：`GalleryAccountCard` / `SauceNaoKeyCard` 从「漫画源管理」页摘掉挂载点，
  搬进新页。那一页的主题一直是 JS 源脚本，这两站压根不在那份源清单里 —— 寄居理由已消失。
- 新偏好 5 条（`venera_preferences`）：`pref_gallery_columns` / `pref_gallery_preview` /
  `pref_gallery_cache_max_mb` / `pref_gallery_download_path` / `pref_gallery_save_naming`。
- 判据全抽纯函数（`GallerySettingsModel`：列数、墙上取哪一档、文件名），11 条用例钉着；
  其中一条**当场抓出我的实现 bug**：`ORIGINAL` 那档会把原名自带的 `.png` 再拼一次 → `hash.png.png`。
- 缓存上限改完要 `GalleryImageLoader.reset()` 丢实例重建（`DiskCache.maxSize` 只在构造时读一次），
  否则是假开关；「立即清除」用 `diskCache.clear()`，当前占用**自己数目录**
  （Coil 没有公开的"目录当前大小"稳定读数）。
- 下载目录复用 `evaluatePickedDir` 那三道关（换算真实路径 → 准入守卫 → 实写探针），
  把它从 `private` 提成 `internal` 而不是抄第二份。
- **命名规则的默认值 = 原来那个 `站名-编号`**，不改设置的用户看不到任何变化。

## 二、本轮 QA

- `:app:testDebugUnitTest`：**49 套 / 356 条 / 0 失败 / 0 错误**（第四轮收口时 48 套 / 344 条，
  上一轮基线 45 套 / 325 条）。
  新增：`GallerySearchCollapseTest`(4) / `GalleryFeedRefreshPolicyTest`(3) /
  `GallerySettingsModelTest`(11) / `GalleryRankingTest` 补年份列表 1 条；
  第五轮再补 `ImageFetchCallFactoryTest`(2) / `ImagePipelinePolicyTest`(6) /
  `GalleryRankingTest`(2) / `GallerySettingsModelTest`(2)。
- `:app:compileDebugKotlin`、`:app:assembleDebug` 均 BUILD SUCCESSFUL。
- `io.coil-kt.coil3:coil-gif:3.6.2` 在 `debugRuntimeClasspath` 里解析到 3.6.2（没被降级）。
- `app-universal-debug.apk` 三次读数：**100,582,056 → 104,139,266 B**，中间那一次只改了两处
  纯换行/缩进（把被吞掉的一行拆开）。**同一份代码两次构建差 3.5 MB** →
  这台机器上 debug universal APK 的字节数**不是可用的回归指标**，本轮不拿它记账，
  也不据此说"上一轮的 103,669,063 被谁改小了"。要量包体得固定一条口径（比如只量 `lib/` + `assets/` 之和）。

## 三、探针环境的两条读数（防下次误判）

1. 本机出口今天对 **Gelbooru DAPI 匿名一律 401**（与 09-28 那条一致），对
   **yande.re 任何带 `tags=` 的查询一律回 `[]`**（连 `tags=1girl`、`tags=id:3551918` 都空，
   而不带 tags 的 `limit=5` 正常回 5 条）。后者是**出口侧的读数**，不是"站方标签检索坏了"——
   用户真机上同一时刻搜索是出图的。下次再拿 curl 探 yande.re，先跑一次无 tags 的对照再下结论。
2. 探测产物在 `_qa/2026-09-29/`（`ya100.json`、`ge_html.html`、`coil-gif-3.6.2.aar` 等）。

## 四、还没验的（真机，用户点）

批次 A 七条 + 批次 B 的五个开关，逐条清单见提交说明。其中三条我特别没底：
① 飞行体跨窗口坐标是否对齐（假设两扇窗口都 edge-to-edge 全屏）；
② `AnimatedImage` 与 telephoto `.zoomable` 同层是否打架；
③ `PullToRefreshBox` 与"下拉看图时胶囊并入顶栏"那套嵌套滚动是否抢手。

批次 C（动图三档 / 预加载 / 自动播放间隔 / 屏幕常亮 / 背景颜色 / AI 角标）另开一轮。
其中一条要当场纠正用户前提：**"保留现在的样式（提取图片边缘颜色做渐变背景）"现在并不存在** ——
大图页背景是窗口 blur-behind 实时糊底下那面墙 + 一层黑色 scrim。

## 五、第五轮追加（同日下午，用户三点指令）

指令原话：「Gelbooru 排行只留『默认』与『全部排行』，其他删掉，另外 gif 同屏加载过多会闪退，
检查下，处理好了直接推进 批次B」。**批次 B 不在这条之后** —— 它在本会话内已经落完（四组开关、
迁移、命名规则、缓存上限都在包里，入口 设置 → 画廊），所以这一节只处理前两条 + 一条纠错。

### 1 Gelbooru 的排行下拉：不列出的四行

`RankingChip` 原来是六行照站画、不支持的四行 `enabled = false` 并加后缀"　本站没有"。
第四轮我在文档里给这条的理由是"藏掉 = 用户分不清站方没有和我们没做"——当晚被否：
四行点不动比少四行更烦。**改成 `GalleryRanking.entries.filter { supports(site, it) }` 之后再画**，
判据没动（还是 `GalleryRankings.supports` 那一把），变的只是不再画不参与的行。

顺带把末行「选具体哪一期…　月 / 年」也照站点收了：Gelbooru 连"本期"的时间窗都给不了，
这个入口点开只能翻出空墙。新增 `supportsPeriodPicker(site) = supports(site, MONTH)` ——
定义挂在 `supports` 上，它不可能漂成"某站多了个假入口"。用例两条（下拉只列两行 / 选期入口）。

### 2 GIF（与视频条目）同屏闪退：真实成因是取流那一路，不是解码器

**取证读数**（adb 只读，用户点的操作；崩的是 12:36 那版含 coil-gif 的包）：

```
12:41:21.823 OplusExceptionHelper  PID 13284
java.lang.OutOfMemoryError: Failed to allocate a 104 byte allocation with 1573808 free bytes
  and 1536KB until OOM, target footprint 268435456, growth limit 268435456;
  giving up on allocation because <1% of heap free after GC
  at androidx.compose.runtime.composer.gapbuffer.SlotTable.openWriter(SlotTable.kt:239)
12:41:15.909 _Desupression freeMemory2OOM: 52830464     ← 打开 GalleryPostActivity 那一刻堆里只剩 52 MB
```

**结论**：256 MB 的 Java 堆是被**墙上的图片字节**吃掉的，不是"某一张太大"，也不是解码器崩的。
读数落在 `SlotTable.openWriter`（Compose 运行时在分配一个 104 字节的小对象）—— 栈顶是受害者不是凶手。

凶手在 `VeneraImageFetcher`（它当时接管**所有** http 图片）：一张图在 Java 堆里同时存在三份编码字节 ——
① `response.body.bytes()` 一份；② `Buffer().write(rawBytes)` 第二份；
③ 交出去的 `ImageSource(source=buffer, …)` **没有 path metadata**，于是
`AnimatedImageDecoder` 走 `squashToDirectByteBuffer()`（`request(Long.MAX_VALUE)` +
`ByteBuffer.allocateDirect`）第三份。动图恰好是最吃这条路的类型。

叠两条放大器：那条 `execute()` 是**同步**调用，不走 OkHttp `Dispatcher`，
所以"每主机并发 ≤5"那道闸对图片形同不存在；而批次 B 的「清晰预览」把 `largeUrl` 搬上了墙，
**视频条目**（mp4/webm，两站都不给更小的转码档，Gelbooru 的 `sample_url` 是空串、
翻译时兜底成了原片）一条就是 16~26 MB —— 一屏几张就是几百 MB。

**落法（两条一起）**：
1. `VeneraImageFetcher` 从此**只接管真的需要改字节的两条路**（JM 去混淆 / EH 雪碧图裁剪），
   判据抽成 `ImagePipelinePolicy.needsBytePipeline(url)` 并钉 6 条用例；其余图片交回 Coil 自带的
   `OkHttpNetworkFetcherFactory` —— 流式写进 DiskCache、以**文件源**解码（第三份拷贝消失、
   能降采样）、且走 `enqueue` 异步调用（每主机并发有上限）。
2. 接管条件一收窄，`ImageFetchTag` 就没人打了（Coil 自己构造 `okhttp3.Request` 只填
   url/method/headers，`Options.extras` **不映射成 tag**）。少这个标记的后果是两条已裁决的行为回归：
   域名熔断会把图片超时算进去（两笔拉黑整站 60 秒）、撞盾会在取图线程上弹人机验证。
   所以新增 `ImageFetchCallFactory`（一层 `Call.Factory`，只补标记），两个 ImageLoader 都改挂它，
   各钉 2 条用例。防盗链头与 UA **不用**在这层补 —— 它们本来就是共享客户端上的第 1、5 条拦截器。
3. `GallerySettingsModel.wallUrl` 补一条：`post.isVideo` 一律走 `videoPosterUrl`（静帧），
   与大图页底图同一把判据。用例两条（Gelbooru 兜底成原片的那例 / yande.re 本来就给 jpg 静帧的那例）。

### 3 一条必须公开的纠错：批次 B 的「图片缓存上限」当时是个假开关

`GalleryImageLoader` 配了 `DiskCache(directory=cache/gallery_img, maxSize=用户档位)`，
但**没有任何一条路径往里写** —— 写盘是 `NetworkFetcher` 干的活，而所有图片都被
`VeneraImageFetcher` 截在前面了。所以那枚"最大缓存容量"和"当前占用 x MB / 立即清除"
在本轮之前读数恒为 0、档位恒无效果。本轮改完才第一次真正生效。
文档先前写的"独立目录 + 独立预算"这句**当时只成立了一半**（目录有，预算没人花）。

### 4 本轮 QA

- `:app:testDebugUnitTest`：**49 套 / 356 条 / 0 失败 / 0 错误**；`:app:assembleDebug` BUILD SUCCESSFUL。
- 有一条用例**当场抓出我自己的错**：我照上面那条「固定 10 块」的旧用例抄了
  `cdn.mhimg.net/media/photos/250000/…` 当作"需要去混淆"的样本，而 `getScrambleNum` 还有一道
  **域名门**（`isJmPhotoUrl` 要求域名命中 `18comic` / `cdn-msp` 那张表），旧用例走的是
  `calculateJmScrambleNum`（绕过域名门）所以它是绿的、我这条是红的。
  改成 `cdn-msp.18comic.org/…` 才对，并补一条"同路径形状、陌生域名 → 不进管道"。

### 5 待真机验（本轮这两条）

① 画廊一屏动图/视频卡不再闪退（尤其「清晰预览」开成"更清晰"那一档）；
② 图片**还能正常出图**——接管方换了，防盗链头虽然由同一批拦截器提供，但这条链没在真机上跑过：
   重点看 yande.re 的 HD 原图档、Gelbooru 的图（那站 CDN 无 Referer 会 302 到 `hotlink.php` 并回 HTML）；
③ 二次进入同一面墙应当**不再重新下载**（这是缓存第一次真的生效，观察点是"秒开"而不是"慢"）；
④ 漫画侧封面/详情页缩略同样走了新链路，顺手看一眼有没有哪源的图片变成不出图。
⑤ 排行下拉：Gelbooru 只剩两行且没有「选具体哪一期」；yande.re 仍六行 + 末行。

