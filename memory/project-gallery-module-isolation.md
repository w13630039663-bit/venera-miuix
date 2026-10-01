---
name: gallery-module-isolation
description: 图库/画廊模块（yande.re + Danbooru）按用户裁定必须与漫画完全隔离；落地流=两站上一天热门各 20 打乱+mp4，入口底栏第 4 位；画廊黑名单走整词判据、页尾读数须来自守卫后，剩 P3 建表未决
metadata:
  type: project
---

用户 2026-09-23 裁定：图库模块**必须与现有漫画全部隔离、全部单独出来**,不做"当漫画源接"那条便宜路。
边界、分期与**实测端点能力表**都在仓库根 `gallery-module-isolation-plan-2026-09.md`;
底栏接入的重新评审记录在 `FREEZE-STATEMENT.md` 的 2026-09-24 那一节。

**已落地（2026-09-24，P0 浏览部分）**:`com.venera.compose.gallery/` 包 =
`data/GalleryPost` + `data/YandeReClient` + `data/GalleryImageLoader`(独立 cacheDir + 64/512 MB 显式预算)
+ `domain/GalleryGuard` + `ui/GalleryScreen`/`GalleryViewModel`/`GalleryPostScreen`。
漫画侧只改了 `VeneraCover` 加可选 `imageLoader` 参数;数据层零改动。

**入口 = 底栏第 4 位(搜索右侧)**,顺序 `HOME → FAVORITES → SEARCH → GALLERY → EXPLORE`。
09-23 我曾按「收藏右侧第 3 位」预留并写进代码注释与冻结声明,**09-24 用户改口第 4 位,以第 4 位为准** ——
下次看到"第 3 位预留"的旧表述不要当真。成为主 Tab 的四处连带后果都已处理
(`routeFor`/`titleFor`/`currentTab`/横滑自动多一格/设置「启动页面」加 GALLERY)。

**P3 建表：前提已翻转（2026-09-25 用户点名要收藏）** —— 之前"刻意不建表"的理由（浏览不写库）不再成立。
方案与三条备选写在 `gallery-viewer-toolbar-and-infosheet-2026-09.md` §12.4，**推荐 B**：
`venera_core.db` 里另建 `gallery_favorites`（v3→v4，列按画廊自己的语义），图片收藏墙加两段切换
（漫画图片 / 画廊），**不往 `favorite_images` 塞 `kind` 列**（那 5 列漫画身份 NOT NULL 无默认，
塞进去就是撒谎，且墙上"作者"靠 `comic_id` 反查的那条链对画廊行整体失效）。
推荐"只存站方 URL、不落本地副本"。待拍的还有"漫画详情页底栏新增收藏"到底是"再加一枚收藏封面"还是"搬阅读器那枚"。

**Why(隔离的三条结构性理由,不是风格偏好)**:① `favorite_images` 有四列 `NOT NULL` 的漫画身份列;
② `FavoriteImageItem` 整条语义链假设母漫画存在;③ 图站一张原图解码后约 87 MB,
而漫画链路的 Coil 配置连 memoryCache/diskCache 都没显式设过,混用必然互相挤兑。

⚠️ **落地流的形态已被用户改判四次，以最后一版为准**（2026-09-25）：
第 1 版「yande.re 人气(order:score) 单源」→ 第 2 版「两站月榜池 + 站内分位加权 + ES 不放回随机 + 动态配比」
→ 第 3 版「两站最新列表交错混搭，无权重无随机」→ **第 4 版（现行）「两站上一天热门各 20 张 + 打乱 + 引入 mp4」**。
用户原话：「画廊改成 `yande.re/post/popular_recent?period=1d` 和 `danbooru/explore/posts/popular?date=&scale=day`
上一天的热门 各取 20 张然后打乱，如果 donmai 有 mp4 就引入 mp4」。
实现是 `GalleryMerge.mix(pools, seed)`：白名单滤非图片 → 三键去重 → 各站 `Random(seed+站点序)` 抽 20
→ 整体 `Random(seed)` 打乱。**打乱必须锁种子**（组合重建会重跑这段，裸 shuffle 就是"点进大图再返回整屏换序"），
只有「刷新」换种子。交错额度 / `RUN_PATTERN` / 翻页 `exclude` / `hasMore` 全套已删 ——
日榜是一屏到底的固定池子，留着那套必然产出「到底了还显示加载更多」（与搜索页同一个病）。
**别把加权/随机/月榜当现成需求重新提**；`fav_count` 只剩二级信息卡那一行。

视频：`GalleryPost.isVideo`（`VIDEO_EXTS` = mp4/webm）+ `durationSeconds`；
翻译时**视频的中间档换成 `720x720` 静帧**，否则 `.mp4` 会被交给 Coil 解码 = 一张永远加载失败的图；
卡片右下角 `▶ N″` 角标；二级页就地播（`GalleryVideoViewer`，**不进三级**），点一下才建 ExoPlayer
（实测原片 16~26 MB，自动播等于替用户预下载）。依赖是 `androidx.media3:media3-exoplayer` + `media3-ui`
**1.11.1**，APK universal **+4.20 MB**（93.7→98.1 MB）。

两站端点形状不同且**都不能照另一站推断**（全部实测，2026-09-25）：
yande.re `popular_recent.json` **固定 40 条**、`limit`/`page` 被忽略、**不认的 `period` 值静默退回 `1d`**
（`1d`∩`1mo`=40/40 同一批，`1d`∩`1w` 只 2 条）；另有 `popular_by_day.json` 32 条与 `period=1d` **零重叠**（另一套算法，别混）。
Danbooru `explore/posts/popular.json?date=&scale=day&limit=200` → 200 条，但 **`date` 必填，缺省静默回 0 条（状态仍 200）**
→ 两站客户端都把空列表当失败抛出，不退成"今天没有热门"。
当天池子成分实测 `jpg 128 / png 61 / mp4 10 / zip 1`，分级 `e 87 / q 40 / s 72 / g 1`。
yande.re 的 `/post/hot.json` 与 `/post/{id}.json` 都是 **404**、`/post.json?id=N` 是 **200 但参数被静默忽略**
→ 单条只能 `tags=id:N`；Danbooru 的 `type:jpg` / `file_ext:jpg` 实测**返回 0 条** → 非图片只能客户端按 `file_ext` 滤，
匿名 `limit` 上限 **200** 而不是 yande.re 的 320。
月度端点（`scale=month`、`popular_by_month.json` 固定 40 条忽略 limit）的能力仍记着，恢复月榜入口不用重新探。

⚠️ **画廊的黑名单判据与漫画侧刻意不同**（2026-09-25 用户拍板「只有画廊走整词」）：
图站 tag 是下划线标识符，`isComicBlocked` 的非正则分支是 `contains()`，
用户那条关键字 `ai` 会命中 `long_hair`(144) / `hair_ornament`(49) / `tail`(39) ——
实测回放当天日榜：yande **12/40**、Danbooru **199/200** 被挡，真机表象就是「yande 只 13 张、donmai 零张」。
新判据在 `security/guard/GalleryBlockMatch.kt`（整串或 `: _ - 空格` 词元整词相等；正则用 `matches()`；
author 仍子串；坏正则返回 false；`COMIC_ID` 不参与），入口 `ContentGuardManager.findGalleryBlockedRule`
**返回命中的那条规则**给页面念出来，一级与二级同源。**漫画侧那一份一字未动**（中文 tag 没有分隔符，
改整词会把已挡的放出来）。别再"统一成一把判据"。

⚠️ **页尾那行读数必须来自守卫之后**：`GalleryFeedEnd` 原先吃 `vm.posts`（守卫前），
只摆 13 张时写着「yande.re 20 · Danbooru 20」，且某站为 0 时被 `mapNotNull` 整段省略 ——
唯一线索被抹掉。现在吃 `GalleryWall`：实际落屏条数（**含 0 照报**）+ 分成因报
「N 张命中屏蔽规则 {原文}」/「M 张按成人内容处理收起」。**任何"少了"都要有可见读数**，别只修判据不修报表。

**yande.re「隐藏图片」这条已实测关闭，不要再当待办提**：油猴脚本 572984 零网络请求，
只是 `classList.remove` 放掉 HTML 列表里被 CSS 藏掉的条目；对应字段是 JSON 的 `is_shown_in_index`
（实测当天 40 条里 2 条 `false`）。我们走 JSON 且从不读该字段 → 那 2 张本来就上屏。
`is_banned` 一类匿名响应里根本不出现，客户端无从取回。
⚠️ 顺带记一条**无效证据**：设备 `venera_http_cache` 里搜不到 donmai 条目不能证明请求失败 ——
Danbooru 的 JSON 是 `Cache-Control: max-age=0, private, must-revalidate`，本来就不进缓存。

`danbooru.donmai.us` 与 `cdn.donmai.us` 是 **API 和图片全 403 + `cf-mitigated: challenge`**;
换 `Venera/1.0 (Android)` 同一 URL 就 200。图片那一路走 `ImageHeaderPolicy` 内置表,JSON 由客户端自己带。
**media3 播放器两条都拿不到**（不过 `VeneraNetworkClient`、不进 `ImageHeaderPolicy`）→ 必须自己按站点带 UA：
Danbooru 用 `Venera/1.0 (Android)`、yande.re 用全局默认串（其图片流量一直如此且实测正常）。
不带就是"缩略图看得到、点开播不了"，最难查的一种错。

⚠️ **CF 对 donmai 判的是指纹，不是 UA —— 装浏览器只会更糟**（2026-09-25 本机 curl 逐档实测）：
同一张 `/original/`，`Venera/1.0 (Android)` 回 200，Chrome 128 移动串回 403 + `cf-mitigated: challenge`，
**补全 `Sec-Fetch-*` + `Accept: image/*` 仍然 403**；那串 Chrome UA 打 `180x180`/`sample` 同样 403。
所以：**图片流量一律不弹交互式过盾**（判据 `offersInteractiveBypass` = 没打 `ImageFetchTag`，有单测）。
两条硬理由：① 过盾成功后绑到 host 上的是 WebView 那串浏览器 UA，拿它重放图片**必然再撞盾**（自相矛盾）；
② 那里是 `runBlocking` 等一次人机交互，跑在 Coil 取图线程上。撞盾就原样抛 403，
HD 钮必须**报状态码 + 自己弹回原档**（留着 HD 亮着画原档 = 假开关）。
设备上"缩略图通、原图撞盾"本机 IP 复现不出来，剩下两种可能都在上游（`/original/` 规则更严 / 匿名按 IP 限流）。

⚠️ **透出的玻璃页要自己画系统栏底**：平台 `@android:style/Theme.Translucent.NoTitleBar`
`windowDrawsSystemBarBackgrounds=false` → 状态栏那条不由本窗口画，系统补不透明黑底，
糊好的背景到状态栏下沿齐刷刷断掉（真机截图那条黑带）。本仓库第一份自有主题
`res/values/themes.xml` 的 `Theme.Venera.GlassOverlay` 就是为这个（四条见注释）。

分级与去重的实测口径：**分级守卫是前置项不是润色项** —— 判据必须放行 `s` 与 `g`
（沿用第一轮 `rating != "s"` 会白糊半屏）,未知分级值仍宁可错打码,用户黑名单排在分级模式之前。
去重只走零成本三键 `uid`(站点+id) / `md5` / 规范化 `source`；**翻页 `exclude` 已随日榜改造删除**。
**感知哈希经实测判定不做**：跨站三种键交集全 0,同站内 128-bit `pixel_hash` Hamming ≤14 也是 0 对,
而 yande.re 根本没这字段 —— 自建哈希要为每张候选图多下载 2.8~31.9 MB 换 0 条去重。
用户 2026-09-25 看过数据后确认不做。
"只读共享"的边界:分级模式偏好与屏蔽规则共用一份,取数/缓存/表各走各的。

⚠️ **画廊的"二级+三级"已在 2026-09-25 合并成一层满屏播放器**（用户点名照 Breadboard 复刻）：
`GalleryPostScreen` = 满屏图（`BoxWithConstraints` 按 `cardRatio` 定贴图的框 + 圆角）+
底部 pill 工具条（`HD`/`下载`/`信息`）+ 独立分享 FAB + `(i)` 拉 `GalleryInfoSheet`；
`GalleryFullViewer.kt` 已删（镜像在 `build/_trash-from-repo/`）。**不要再往"三层"方向改回去** ——
两层守卫判定会分叉，那条分叉今天刚修过一次。
关键约束：**大图页是独立 Activity**（`GalleryPostActivity : VeneraSubActivityBase`，
主题 `Theme.Venera.GlassOverlay`、`opaqueAmbientBackground=false`、`blurBehindDp=32f`）——
背景模糊走**系统 blur-behind**（实时糊掉后面那屏真列表），跨 activity 预测式返回走 `targetSdk≥36` 的系统动画。
用户明确否掉过两版假方案：①一次性截屏当底（死图，切 HD/转屏不跟）②在详情页**重画一面墙**
（永远从列表第一行开始糊，"对不上刚看到的那一屏"）。**别再往这两条路上退**。
"图片进场"只能自己画：平台 jar（33~36）里**没有** `SplashScreenViewProvider` /
`overrideNextTransitionSplashScreenStartingPoint`，经典 View scene transition 又搬不动 Composable。
**第 1 版是两矩形 `graphicsLayer` 插值弹到位，2026-09-25 被用户否掉换成第 2 版（现行）**：
照 Breadboard 的 `OffsetBasedLargeImageView` 改成**整页从屏幕下沿向上滑入**
（外层 Box 的 `translationY = entrance.value * screenHeightPx`，弹簧与下滑关闭同档 `StiffnessMediumLow`；
**压暗那层不参与滑入**，玻璃是"后面那一屏"）。`GalleryFlyIn` 从此**只递位图**当滑入期间的占位第三层
（卡片与大图框同一个 `cardRatio`，`Crop` 不改构图），终点矩形那套几何已全删；
落位闸门是 `firstTierSettled`（第一档成功或失败都算，**不是**当前档 loaded —— 切 HD 会把占位盖回去）；
系统打开转场用 `makeCustomAnimation(0,0)` 压掉。
`GalleryPostRoute` 已从 `Navigation.kt` 删除。**顶栏整条已删**（返回只剩下滑关闭 + 系统返回，
"在站点打开"搬进 sheet 的 `LinkRow`），四个动作合成**一条 Dock**（HD/下载/信息/分享，无 FAB）；
深色玻璃页要自己翻系统栏图标（`DisposableEffect` + `WindowCompat`，离开按 `!isDark` 还原），
页内文字走 `StatusColors.OnBadgeSurface`；`HD` 钮对视频条目不摆（站方无更小转码档 = 假开关）；
**心形收藏该摆**（09-25 第一轮"先不摆"的前提是"没有落库的表"，用户这次点名要建，见 §12.4）。
下载走 `GallerySaver`：MediaStore **原样落字节**，不复用 `CoverViewerScreen.saveCoverToGallery`
（那条 `compress(JPEG,95)` 会洗掉 png alpha，且用全局 ImageLoader = 把原图再下一遍）。
标签分桶 `GalleryPost.tagGroups`：Danbooru 五桶、yande.re 只一桶（44 键无分类字段）；
**黑名单判定仍吃平铺 `tags` 串**，别改成按桶判。

⚠️ **画廊搜索（2026-09-25 落地）的形态与实测面**：入口=画廊顶栏右上角图标，
承载=**与大图页信息框同一形态的全高 `ModalBottomSheet`**（用户原话"直接像图片详情页开那个信息框那样"，
否掉了我提的"自绘全屏 overlay"与"再开 Activity"两条）；一次只搜**一个站**；**不摆分级 chips**
（yande.re 对不认识的 `rating:` 值静默不筛 = 假开关，分级仍由守卫管）；页 Danbooru 200 / yande.re 100。
实测面（全表在 `gallery-search-2026-09.md` §〇，**别再重新探**）：两站官方 autocomplete 路由都 **404**，
补全只能 `danbooru /tags.json?search[name_matches]=词*&search[order]=count` 与
`yande.re /tag.json?name=词*&order=count`；⚠️ **参数写错不报错、静默回不相干数据**
（`name_match` 少个 s → 回"最新建的标签"；yande 的 `search[name]` 同理）→ 必须客户端复检前缀；
Danbooru 匿名 **2 枚**标签预算（第 3 枚 422 `TagLimitError`，排除项也算），yande.re 不限；
两站**都没有总数端点**（`/posts/count.json` 404）→ 到底只能按"给满没给满"判；
查无此标签两站都回 **200 + `[]`** = 合法空结果，**不能**沿用日榜那条"空列表当失败抛"。

**参考实现的位置**：Breadboard（booru 浏览 app，Compose + telephoto 0.19.0 与我们同版本）浅克隆在 `build/_refs/breadboard/`，播放器是 `largeimageview/LargeImageView.kt`（拖拽层/pager/工具条三层）
与 `InfoSheet.kt`；它用 `io.github.kdroidfilter:composemediaplayer:0.10.0` 替 media3、
`HorizontalPager` 横滑换张、缩放门控翻页（`zoomFraction < 0.075`）—— 这三样**本轮刻意没做**，
清单与理由在 `gallery-viewer-toolbar-and-infosheet-2026-09.md` §六。
它的 Blocked tags 页用**精确 tag 名**（`ai-generated`/`ai_generated`）挡 AI，与画廊整词判据同口径。
