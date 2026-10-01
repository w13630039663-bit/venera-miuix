# 画廊：chrome 钉位 + 预览提速（2026-09-30 晚）

用户原话：「画廊页你调整下，顶住在这个位置就行，不需要跟随顶栏，另外探索下目前画廊的预览图源，能不能更快显示预览」。

---

## 一、chrome 不再跟随顶栏

### 根因（照库源码读出来的，不是猜）

常驻 chrome（搜索入口条 + 来源分段器）从前挂在 `VeneraTopAppBar.bottomContent`，而 miuix
`TopAppBar`（0.9.4-rc01，sources jar 已拆在 `_probe/miuix-ui-sources/`）的布局是：

- `barHeight = lerp(collapsedHeight=52dp, collapsedHeight+expansion, 1f - collapseFraction)`（TopAppBar.kt:865-879）；
- `contentTop = maxOf(barHeight + expandedBottomPadding, …)`（:889）；
- `bottomContentPlaceable.placeRelative(x = 0, y = contentTop)`（:927，注释自称 "pinned"）。

"pinned" 钉的是**跟随栏高的下方**：大标题一折叠，`barHeight` 收缩，整条 chrome 跟着上爬。
用户要的是「顶住在这个位置就行」。

### 改法（GalleryScreen.kt）

- 顶栏的 `bottomContent = {}` 清空；
- chrome 改成独立浮层：`align(TopCenter) + offset(y = topBarFloor + topBarHiddenOffset)`；
- **锚点取 `topBarFloor`（= statusBarTop + 104dp）**，与搜索浮层 `searchOverlayTop` 同一条
  基准线 —— 网格避让 `gridTopPadding = topBarFloor + homeChromeHeight` **一个数字没改**，
  静止时逐像素与从前一致；
- 「上滑收起顶栏」开关（默认关）语义不变：开着时 chrome 与顶栏共用同一枚
  `topBarHiddenOffset` 一起滑走；
- 搜索开着时（`svm.active`）chrome 照旧不渲染 —— 与搜索浮层的两态互斥原样保留。

### 未真机验收

- 折叠到深处后，chrome（自带实底浮层）与收成小标题的顶栏之间会露出一段空隙 —— 刻意的，
  它现在压在滚动网格上，与"浮在内容上的搜索条"同一种语言；真机读观感再定要不要收。
- `GalleryHomeChrome.kt` 头注已同步改写（旧注释会让下一个人以为它还住在 bottomContent）。

---

## 二、预览提速

### 图源盘点结论（预览源本身没有更好的档）

- 墙上默认档 = 站方 preview 档（yande.re `preview_url` 300×212、Gelbooru 缩略 jpg），
  已是**两站最小的图**；「设置 → 画廊 → 预览清晰度」可手动切 sample 档。
- **两站缩略 CDN 都是完全可缓存的静态内容**（2026-09-30 本机 HEAD 实测）：
  - `assets.yande.re/data/preview/**`：`max-age=315360000` + ETag，13.5 KB；
  - `img4.gelbooru.com/thumbnails/**`：**必须带 Referer**（无 → 302 hotlink.php 回 HTML），
    带上后同样 `max-age=315360000` + ETag，25 KB。
  - ⇒ 慢不在"图源档位"，在**请求调度**。

### 两个提速杠杆（都落在画廊自己的链路里，漫画侧零改动）

1. **每主机并发 5 → 16**（`GalleryImageLoader`）：
   共享 OkHttpClient 的 Dispatcher 全吃默认 `maxRequestsPerHost=5`，一屏 12~18 张缩略图
   全打同一个图床主机，第 6 张起排队 —— "预览一张张慢慢蹦"的主因。
   做法：`baseOkHttpClient.newBuilder().dispatcher(Dispatcher(16/32)).build()` 派生客户端，
   拦截器链 / 连接池 / CookieJar 共享，只有并发上限变了。 Coil 3.6.2 取流走 `enqueue`
   异步入队（2026-09-29 起），**不**绕开 Dispatcher，这一抬真生效。
2. **滚动越界预取 24 张**（`GalleryCardsGrid.rememberGalleryPreviewPrefetch`）：
   `snapshotFlow` 盯最后可见项 → `imageLoader.enqueue` 无 target 请求暖缓存。
   - 内存 key 无 transformations 时不带尺寸、compose 侧精度恒 INEXACT
     （`AsyncImagePainter.updateRequest` 字节码实测）⇒ 预取位图网格直接命中，
     不重解码不重请求；就算被挤出内存，字节已落磁盘缓存（max-age 十年，NetworkFetcher 写）。
   - 判重按 uid 存普通 `HashSet`；只预取 **preview 档**（sample 档几百 KB~几 MB，
     自动预取是拿用户流量下赌注，大档只在真正可见时取）；
   - `leadingItemCount`（header+节头）必须从 item index 里减掉，否则预取窗口错位。

---

## 三、验证

- `:app:compileDebugKotlin` 通过；全量单测 **559 条 / 0 失败 / 0 错误**（73 个结果文件，
  其中 55 份因中文测试名编码问题解析失败，用表头正则数齐 —— 历史报告层现象，非本轮引入）；
- `:app:assembleDebug` 通过，arm64 包 23:31 装入 PJZ110（`adb install -r` Success）。

### 真机清单（页面由用户点）

1. 首页上滑：搜索条 + 分段器**钉在原位不动**，只有大标题折叠；
2. 折叠深处 chrome 与小标题之间的空隙观感是否可接受；
3. 「上滑收起顶栏」开着时：chrome 仍与顶栏一起滑走、回滚立即回来；
4. 主墙快速上滑：进入视野的缩略图应当**大多已经就位**（shimmer 一闪而过或没有）；
5. 回滚已看过的区域：零加载（磁盘缓存命中）。
