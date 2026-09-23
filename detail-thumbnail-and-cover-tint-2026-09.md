# 详情页预览小图接线 + 封面取色头部 + 批量整理 · 2026-09-23

用户点名：「做 1 和 4 试试」。1 = 详情页预览条改走源的缩略图钩子（现在下的是整页原图）；
4 = 美化（详情页封面主色渐变头 + 收藏侧批量整理）。**本文档是方案，未动代码。**

## 零、开工前必须知道的三件事

1. **冻结域**：`FavoritesScreen.kt`、`NetworkFavoritesScreen.kt` 在 `FREEZE-STATEMENT.md`
   第三批「验收冻结」清单里（底栏 5 大主 Tab 全部闭环那条）；`ComicDetailViewModel` 是保护域
   （09-21 第五批与 Tier 2 都明确写着"未动"）。本文 C 方案与 A 方案的第 3 步都要动它们 → **要显式授权**。
2. **`onThumbnailLoad` 是 optional 钩子**。master 的判据是 `_checkExists("comic.onThumbnailLoad")`，
   没有就返回 null。也就是说接完之后**多数源不会有任何变化**，除非它的源脚本自己实现了。
   不先量实现率就动手，做完就是个假功能（用户口径：假开关零容忍）。
3. **我们的 JS 引擎是 WebView**，所有 `evaluateJavascript` 都 post 到主线程
   （`engine/VeneraJsEngine.kt:252`），JS 侧单线程串行。所以**绝不能逐张调钩子** ——
   一屏 12 张就是 12 次主线程往返，正好砸在刚做完掉帧审计的详情页上。必须批量一次调用。

## 一、master 的权威口径（已核，别照推断改）

- **签名**：`comic.onThumbnailLoad(url)` **同步**返回一个 `ImageLoadingConfig` **对象**，
  不是小图 url 字符串。`.url` 是换算后的小图地址，`.headers` 是防盗头。
  协议明写 `modifyImage` 与 `onLoadFailed` **会被忽略**。
  证据：`.reference/flutter-master/doc/comic_source.md:511-519`、
  `lib/foundation/comic_source/parser.dart:1085-1099`、`types.dart:55-56`
  （与 `onImageLoad` 不同族：那个允许返回 Future，这个不允许）。
- **单一入口 + 缓存口径**：`lib/network/images.dart:13-31`。
  `cacheKey = "$url@$sourceKey$cid"` 用**原始 url** 成键，**命中缓存就不跑钩子**；TTL 7 天。
- **预览条三级优先级**：`thumbnails.dart:37-49` ——
  详情自带 `comic.thumbnails` → 源 `loadThumbnails` → 首话页面图兜底。
  ⚠️ 这三条我们**已经照抄**（`ComicDetailViewModel.kt:284 / 571-650 / 614-638`），优先级不用改。
- **master 那条"兜底必须走阅读器 provider"的坑，我们天然规避**
  （`thumbnails.dart:190-212`：兜底图若走缩略图链路会显示被打乱的原图，因为解混淆只在
  `loadComicImage` 里跑）。我们的去混淆在 `data/network/VeneraImageFetcher.kt:75-93`
  **按 URL 生效**，不区分缩略图与阅读器，所以预览条现在就能正确显示禁漫页
  （09-22「详情页预览图裂成横条」与 Tier 1-2 那两轮修的就是这条链）。
  **记下来防误改**：不要为了"对齐 master"给预览条单开 provider。

## 二、A 方案：预览条接 `onThumbnailLoad`

### A0 探针结果（2026-09-23 已做，**前提被证伪**）
不必跑引擎 —— 源脚本里有没有 `onThumbnailLoad:` 就是判据，直接量文本更准。
`app/src/main/assets/sources/` 共 34 个源，**14 个实现**（41%，过 30% 线），
且用户实际在用的 picacg / jm / ehentai 三个都有。

**但我原来给 A 的卖点是错的，公开纠正**：我说「预览条下的是整页原图，接上钩子能换成小图省流量」——
逐个读完 14 个实现后，**没有一个是「换成小图」**：全部是 `url` 原样返回 + 加防盗头
（`jm/goda/komga/lanraragi/mh18/manhuaren` 只回 `headers`；`picacg/hitomi/jcomic/mycomic/comick`
回 `url: url` + headers；`nhentai` 走 `_fixAndWrap`）。ehentai 是唯一改了 url 的，
但那是把 `s.exhentai.org` 换成镜像域 `ehgt.org`，**同分辨率**（不过在墙内这是实打实的可达性收益）。
它的真实身份就是**缩略图版的 `onImageLoad`**（协议原文：返回 `ImageLoadingConfig`，
且 `modifyImage`/`onLoadFailed` 被忽略），不是缩放钩子。
→ 「整页原图」这个问题是真的，但**能解决它的不是 A**，而是已经接好的 `details.thumbnails`
与 `loadThumbnails` 两条；走首话页面图兜底时源本身就不提供小图变体，无解。

### A 收窄后仍然要做的理由（是真 bug，不是优化）
`data/network/ImageHeaderInterceptor.kt:114-125` 只注入 `ImageHeaderPolicy.headersFor(url)`
里**按 URL 记账过的**头，没记到的请求就只有一个裸 UA。而三条来源里：
- 来源 C（首话页面图兜底）**已经发过头**：`ComicDetailViewModel.kt:624`
  `ImageHeaderPolicy.publishForUrls(chPages.pages, chPages.headers)`。
- 来源 A（`:284` `thumbnails = d.thumbnails`）与来源 B（`loadThumbnails` 合并段 `:598-611`）
  **一处都没发**。→ 对需要 referer/UA 的站（jcomic / mycomic / manhuaren / hitomi / picacg / EH…），
  详情页那一排小图就是 403 空白。这正是 A 该修的东西。

**所以 A 的目标从「省流量提速」改成「给预览条补源声明的防盗头 + EH 换镜像域」。**
下面的 A1~A4 仍然成立，只是收益口径变了；批量一次调用的约束（§零第 3 条）也仍然必要。

### A1 JsComicSource 新增批量钩子
`source/js/JsComicSource.kt`（紧跟 `:751-781` 的 `loadThumbnails`，抄 `:684-738` 的 `onImageLoad` 样板，
统一用已抽好的 `evaluateEnvelope` `:1671-1678`）：

```
suspend fun resolveThumbnailLoadingConfigs(urls: List<String>): Result<List<ResolvedThumbConfig?>>
```

JS 侧一条脚本内 for 循环 map 整个数组，一次往返。语义逐条对齐 master：
- 源没实现 → 信封里回 `supported=false`，Kotlin 侧整批退化（不是失败）。
- 单张钩子抛错 → **只退化那一张**（回原 url、无头），不整批失败。
  退化方向是安全的：原 url 就是今天的现状，不会比现在更差。
- 只取 `url` 与 `headers`，`modifyImage`/`onLoadFailed` 按协议忽略。

### A2 ComicSourceManager 暴露 + 缓存
`source/ComicSourceManager.kt:1004` 之后加 `resolveThumbnailConfigs(sourceKey, urls): Result<...>`，
沿用现成的 `imageConfigCache` + `imageConfigInflight` + `scope.async` 模式（`:948-967`）。
缓存 key 用**原 url**（对齐 master，避免同一页出现两个缓存身份）。非 JS 源固定文案「该源不支持…」。

### A3 ComicDetailViewModel（**保护域，需授权**）
三条来源填完 `thumbnails` 之后，对**当前要显示的那一屏**（`PREVIEW_LIMIT=10` 与展开后的分页窗口，
即 `ComicDetailScreen.kt:165-184` 算出来的可见区间）批量换算一次，存
`thumbnailConfigs: Map<String, ResolvedThumbConfig>`。换算失败或源不支持 → 空 map，UI 走现状。
⚠️ 不做成"整本几百页一次换算" —— 那是把主线程往返按章节长度放大。

### A4 ComicDetailScreen 消费
`:868-876` 那两处：`data = cfg?.url ?: thumbUrl`；`cfg.headers` 交给现成的
`ImageHeaderPolicy.publishForUrls`；**`cacheKeyFor` 继续用原 url**。

### A 的验收口径
真机：EH（走 loadThumbnails 雪碧图）/ 禁漫（走兜底页面图）/ 拷贝或包子（走详情自带 thumbnails）
三类各看一次预览条，要求 ① 出图不比现在慢 ② 禁漫预览仍不是条状 ③ 日志里能看到钩子被调与退化计数。

## 三、B 方案：详情页头部封面取色渐变

现状：头部**没有任何背景/渐变**，页面直接铺 `VeneraAmbientBackground`
（主题 primary/secondary 双 radial 光斑，`components/VeneraAmbientBackground.kt:26-75`）；
只有顶栏滚动后有 `progressiveTextureBlur` 背板（`ComicDetailScreen.kt:2170-2218`）。

### B1 取色实现（要拍板，见 P3）
仓库**没有**任何图像取色能力（`gradle/libs.versions.toml` 无 androidx.palette）。两条路：
- **(a) 加 `androidx.palette-kotlin`**：官方 OctTree 量化，质量稳，代价是多一个依赖。
- **(b) 自算 ~30 行**：位图 `createScaledBitmap` 到 32×32，按饱和度加权取众数簇。

⚠️ **两条都绕不开的设计判断**：漫画封面大面积白底/黑底极常见，取"主色(dominant)"
往往得到接近白或黑 → 渐变看不出来，头部像没改。真正要的是**最有彩度的那一簇**
（palette 的 `getVibrantColor`，或自算时按 saturation 加权）。这个口径先定，
否则真机上一半漫画的头部是灰的。

### B2 边界：只染头部，不动主题
`theme-palette-2026-09.md` 已拍板「取色只在 MD3 风格下开放、来源是系统壁纸或自定义色板」。
本方案是**每本漫画的局部染色**，与那套全局种子色链**完全无关**，绝不能反向写进
`VeneraPreferences.themeSeedColor`，否则就是拿封面劫持全局主题。

### B3 取色时机
Coil 解码封面成功时顺手取（`ImageRequest` 的 listener / `AsyncImage` 的 onSuccess 拿 paint 的位图），
**不能**在重组里取；结果按 `comicId` 存一张小表（几十条上限）。Miuix 风格下是否同样生效要单独定
（Miuix 是固定色板设计，给它加动态染色可能违背"观感尺寸用现成口径"那条）→ 见 P3 附带问题。

### B4 拦路石：状态栏图标深浅是**全局**的
`feature/VeneraTheme.kt:113-117` 按全局 `isDark` 设 `isAppearanceLightStatusBars`。
头部若变深底，图标就得变白。两种做法：
- 在详情页加 `DisposableEffect` 局部覆写、离开时还原（改动局部、风险小，但要多处小心返回栈）；
- 给主题层加"按页覆写"钩子（动 VeneraTheme，风险大）。
默认选前者。

## 四、C 方案：批量整理（**两条都撞冻结域**）

现状盘点：
- 历史页：多选**已有**（`HistoryViewModel.kt:27-60`，只能删除；长按进、顶栏变全选/删除）。
- 本地收藏：多选 + folder 体系**完整**（`FavoritesScreen.kt:328-335` 工具条，
  VM `enterMultiSelect/selectAll/deleteSelected/moveSelectedTo/copySelectedTo`，
  底层 `LocalFavoritesManager.batchMoveFavorites:358`、`batchCopyFavorites:388`）。
- **图片收藏：无多选**（`FavoriteImagesManager.kt` 只有单条 `removeFavorite(id):130`）。
- **网络收藏：无多选**（`NetworkFavoritesViewModel.kt` 只有单条 `deleteComic:301`）。

最小可行：
- C1 图片收藏多选：抄 `FavoritesScreen` 现成多选壳（同一套顶栏 + bottomContent 计数 + 圆圈勾选），
  `FavoriteImagesManager` 补 `removeFavorites(ids: List<Long>)` 批量 SQL。
  ⚠️ 删除必须沿用单条那两条纪律：**先读 local_path 再删行**、**只删该目录内的文件**
  （本地页的 local_path 指向书本体，越界删就是删用户的书）。
- C2 网络收藏批量移除：`NetworkFavoritesViewModel` 补 `deleteComics(listOf(...))`。

需要解冻：`FavoritesScreen.kt`（只读它的壳，不改它 → 其实不用解冻）、
`NetworkFavoritesScreen.kt`（**要改**，验收冻结）、`FavoriteImagesScreen.kt`（本轮刚重写，附九）。

## 五、拍板记录（2026-09-23 用户逐条点选，四项全按推荐）

- **P1 → 先探针再决定**：已做，见 A0。实现率 41%，但**卖点被证伪**，A 已收窄为「补防盗头 + EH 镜像域」。
- **P2 → 只详情页预览条**：不动 `VeneraCover` 与全局小图链。跑通后是否扩到所有小图，另开一轮。
- **P3 → 加 `androidx.palette-kotlin`**：取 `vibrants` 而非 `dominant`（漫画封面白底多）。
- **P4 → 只做图片收藏多选**：`NetworkFavoritesScreen.kt`（验收冻结）**不解冻**。

## 六、本轮明确不做

- 不给预览条单开图片 provider（§一最后一条，我们的架构不需要）。
- 不做整本一次性换算（A3）。
- 不把封面色写进全局主题种子（B2）。
- 不动 `ContentGuardManager`（FROZEN）、不动打码判定链。

## 七、落地记录（2026-09-23，已编译通过；真机未验）

### A 已接线
- `JsComicSource.resolveThumbnailLoadingConfigs(urls)`：**一条 JS 脚本内 for 循环算完整批**，
  一次主线程往返。源没实现钩子 → `{supported:false}` → Kotlin 侧回空列表（不是失败）；
  单张抛错只退化那一张。协议里被忽略的 `modifyImage`/`onLoadFailed` 不取。
  另外对"源把钩子写成 async"补了一句 `typeof c.then === 'function'` 的 await ——
  master 那种情况直接判 invalid 抛错，内置 14 个实现无一 async，但用户自导的源不受我们约束。
- `ComicSourceManager.resolveThumbnailConfigs(sourceKey, urls)`：按 `sourceKey|原url` 走
  独立 LruCache(512)，**只给 miss 的那部分起 JS**；超时 `THUMBNAIL_CONFIG_TIMEOUT_MS = 8s`
  （不是检索那套 20s：这钩子按协议是同步纯计算，而 JS 在主线程串行，挂住就是堵详情页）。
  超时/失败回空并留一条 Warn。
- `ComicDetailViewModel`：新增 `DetailUiState.thumbnailConfigs`（原 url → 配置）与
  `ensureThumbnailConfigs(urls)`。私有 `thumbnailConfigAsked` 保证**每个 url 只问一次**
  （否则源不支持时每遇预览窗口变化就重跑一遍 JS）。拿到的头按**实际要请求的那个 url** 的
  host 记进 `ImageHeaderPolicy`。
- `ComicDetailScreen`：`LaunchedEffect(previewThumbnails)` 只喂**挂载窗口**；
  预览格的 `data` 用 `thumbnailConfigs[thumbUrl]?.url ?: thumbUrl`，
  而 **cacheKey 仍按原 url** 算（对齐 master `images.dart:15-24`，否则换域前后各存一份缓存）。

### B 第一版（封面取色染色）真机被否，改成真·Hero 背景
**第一版长什么样、为什么被否**：`androidx.palette` 取封面彩度最高的一簇，往头部叠一层
`0.18f` 起的竖向渐变。真机结果是深色主题下头部变成**一块边缘清晰的紫色矩形** ——
因为 `Modifier.background(Brush.verticalGradient(tint → 透明))` 画的是"往页面上盖一层色"，
而页面背景底下还有 `VeneraAmbientBackground` 那两团主题色光斑；盖下去的色块与它没有过渡，
边界就是矩形。用户原话：「这个效果不好」。

**整套撤掉**：`CoverPalette.kt` 删除、`androidx.palette` 依赖回退（`libs.versions.toml` 与
`app/build.gradle.kts` 都复原）、token `headerTintAlpha` 改名成 `heroBackdropAlpha`。
不留死代码。

**第二版 = `components/CoverHeroBackdrop.kt`**（下列为真机调完后的**最终值**）：封面
`ContentScale.Crop` 铺满头部整块区域 + `.scale(1.35f)` + `blur(spacing.space11)`（32dp，比打码那档
`space10`=24dp 再重一级，仍取现成档位不另立新数）+ 图层 `alpha = heroBackdropAlpha(0.40)`。
底边**擦**成透明而不是**盖**：`drawWithContent` 里一笔 `BlendMode.DstIn`
（竖向 `0→0.46` 白、`0.46→1` 擦到透明）。DstIn 只看 alpha，擦出来的洞透出的是**真正那层背景**，
所以淡出是连续的、没有边界。
两处与第一稿不同的值是同一轮真机调出来的，记下防回退：
- 不放大 + `space10` 时背景还能"认出是那张封面"，构图细节全在，观感是"贴了张模糊图"而不是氛围
  → 放大一档、模糊再重一档。
- 原先四边各擦一笔（竖 + 横）在顶栏下面留了一条**可见的收口线**；出血之后左/右/上三条边本来就
  压在屏幕边上，没有"边界"可言 → 只留底边那一笔。

三条实现要点（都是会静默出错的）：
- 这一笔 DstIn 必须落在**离屏层**上：`.graphicsLayer { compositingStrategy = Offscreen }`。
  没有它，混合直接作用到窗口画布，等于把整扇窗口那块擦穿。
- `tokens.current` 是 @Composable getter，**不能**在 `graphicsLayer {}` 的 lambda 里读 ——
  那里不是组合上下文，读了不会随深浅色刷新。先在组合里取成局部 val 再带进图层。
  （本仓库同一格踩过第二次，见 `FavoriteImagesScreen` 的 `placeholderRatio` 那条。）
- **内容守卫打码命中时整层不画**：前景封面被模糊 + 暗遮罩 + R18 角标三重压住，
  背景再铺一张同图的放大模糊版等于把打码绕过一半。`maskState == "VISIBLE"` 才挂。

挂载方式：头部那个 `item` 由 `Row` 改成 `Box { CoverHeroBackdrop(matchParentSize); Row }`。
`maskState` 与 `coverUrl` 因此**上提到 item 层**（背景层要判打码），Row 内那份重复计算删掉。
未做：让 Hero 顶穿状态栏/顶栏做全出血 —— 那要给 item 负 padding，牵动 `contentPadding`，
等真机看过这一版再说。

### ⚠️ 第二版的出血做法把应用搞闪退了（真机），换实现
"顶穿状态栏 + 左右满版"我先用的是 **`Modifier.padding` 传负值**（`horizontal = -rowHorizontal`、
`top = -(statusBarTop + detailTopBarClearance)`）。真机一进详情页即崩：
`java.lang.IllegalArgumentException: Padding must be non-negative`
（栈在 `PaddingElement.<init>` → `ComicDetailScreen.kt:362`）。**Compose 的 padding 是硬校验的**，
负值不是"效果不好"，是运行时抛异常。
→ 出血改到**绘制层**：`CoverHeroBackdrop` 收 `bleedHorizontal` / `bleedTop` 两个 Dp 参数，
自己 `onSizeChanged` 量出图层尺寸，再在 `graphicsLayer {}` 里算
`scaleX = (w + 2*bh) / w`、`scaleY = (h + bt) / h`、`translationY = -bt / 2f`
（缩放以中心为原点，所以纵向只往上扩要靠平移把下边缘挪回原位）。
绘制变换完全不参与测量与布局，item 高度不受影响；背景是一团模糊，非等比拉伸看不出来。
教训：**"负 padding 撑出出血区"这个想法在 Compose 里根本不成立**，别再试第二次。



### C 已接线，工具条**做在面板内**而不是顶栏
- `FavoriteImagesManager.removeFavorites(ids)`：一次 SQL 删完（不在 UI 侧循环调单条版，
  几十张就是几十次事务）。**先读 `local_path` 再删行**、且只删 `persistedDir()` 内的副本
  —— 这两条纪律与单条版逐字一致，本地漫画页的 `local_path` 指向书本体，越界就是删用户的书。
  返回真正删掉的行数。
- `FavoriteImagesBody`：`selectionMode` + `selectedIds`；长按菜单加「多选」起手；
  多选态下**整卡可勾**（只让人点小圆圈太费劲），勾选圈就占原先垃圾桶那一格
  （不换语义外的布局，也不碰上面那支共享元素图的几何 —— 一改形状飞入动画就变形）；
  `BackHandler(enabled = selectionMode)` 让系统返回先退多选，不把整个收藏 tab 弹掉；
  移除后按返回行数如实提示，0 就说"移除失败"，不报"已移除"。
- ⚠️ 与 `HistoryScreen`/`FavoritesScreen` 那套"多选工具条迁顶部分段区"的既有形态**不一致**：
  因为顶栏归 `FavoritesScreen.kt`，而它在冻结声明第三批标了验收冻结，本轮承诺不改它。
  若真机觉得浮层别扭，下一轮要么解冻 FavoritesScreen 把条子挪进顶栏，要么接受两种形态并存。

