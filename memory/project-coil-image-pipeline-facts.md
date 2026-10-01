---
name: project-coil-image-pipeline-facts
description: Coil 3.6.2 取流层的几条实测真相：DiskCache 只有 NetworkFetcher 会写；SourceFetchResult 交内存 Buffer 会让动图解码器整片吸进堆再 allocateDirect 第三份；Options.extras 不映射成 OkHttp tag；同步 execute() 绕开每主机并发闸
metadata:
  type: project
---

2026-09-29 查画廊 GIF 闪退时从 gradle 缓存的 **sources jar** 里逐条读出来的（`coil-core-android` /
`coil-network-core-android` / `coil-network-okhttp-android` 三个 3.6.2 sources jar 都在缓存里，
拆开后读源码比 javap 快）。这几条照我们自己的代码**永远看不出来**，而且每一条都对应一次会复发的错。

- **Coil 的 `DiskCache` 只有一个写入者：`NetworkFetcher`**（`coil3.network`）。
  自定义 `Fetcher` 返回 `SourceFetchResult` 时**不会**落盘。所以"给 ImageLoader 配了 diskCache"
  与"图片真的进了磁盘缓存"是两件事 —— 只要有一个 `Fetcher.Factory` 把所有 http URL 截在前面，
  那把缓存就是**假开关**（画廊那把就是这样空转了一整轮，"当前占用"恒 0）。
- `SourceFetchResult(source = ImageSource(Buffer, FileSystem.SYSTEM))` 这种**没有 path metadata**
  的内存源：`StaticImageDecoder`/`BitmapFactoryDecoder` 的 `toImageDecoderSourceOrNull` 要求
  `fileSystem === FileSystem.SYSTEM && fileOrNull() != null` 才走 `ImageDecoder.createSource(File)`，
  拿不到文件就退化成流；而 `AnimatedImageDecoder` 对动图直接
  `squashToDirectByteBuffer()` = `request(Long.MAX_VALUE)` + `ByteBuffer.allocateDirect(整个文件大小)`。
  配合 fetcher 里的 `body.bytes()` + `Buffer().write(rawBytes)`，**一张动图同时在堆里三份编码字节**。
- `Options.extras` **不会**映射成 `okhttp3.Request` 的 tag（`CallFactoryNetworkClient.toRequest()`
  只填 url/method/headers/body）。→ 凡是"靠 request tag 分流"的应用级拦截器
  （我们的 `ImageFetchTag`：域名熔断豁免 + 交互式过盾豁免），**换到 Coil 自带网络 fetcher 时一定会掉标记**，
  必须自己包一层 `Call.Factory` 补上。`OkHttpNetworkFetcherFactory(callFactory = ...)` 收的就是
  `() -> Call.Factory`，包装点在那里最省。
- 自定义 fetcher 里用 `client.newCall(req).execute()`（**同步**）会**绕开 OkHttp `Dispatcher`
  的 `maxRequestsPerHost`（默认 5）—— 同步调用不进队列。改回 Coil 那路（`Call.await()` = `enqueue`）
  顺带把每主机并发收回来了。
- `ImageRequest.Builder.httpHeaders()` / `httpMethod()` / `httpBody()` 是 `coil-network-core` 提供的
  **extra 扩展**（默认空）；`NetworkFetcher.newRequest()` 只认 `options.httpHeaders`，
  不会替我们读 `ImageHeaderPolicy` 那张表 —— 表要挂在客户端拦截器上才对所有路径生效
  （我们的 `ImageHeaderInterceptor` 已经在 `VeneraNetworkClient` 第 5 条，UA 在第 1 条）。
- `coil3.gif` 的类名在 3.6.2 是 **`AnimatedImageDecoder`**（`Factory(boolean)` 那个参数是
  `enforceMinimumFrameDelay`，**不是**"要不要放动画"的开关）；早期文档里的 `ImageDecoderDecoder` 不存在。
- **`Accept-Encoding` 只要由调用方自己写上，OkHttp 就不再透明解压**（BridgeInterceptor 只在"这个头
  是它自己补的"时才拆 `Content-Encoding`）。于是响应体是 **gzip 原始字节**，一路喂到解码器才炸。
  本仓的入口是**源 JS 的 `getImgHeaders` 照抄浏览器头**（含 `Accept-Encoding: gzip`），它经
  `ImageHeaderPolicy` 贴到图片请求上 ⇒ 症状是"下载成功、图解不出来"，不是 403。
  2026-09-29 下载侧早已为同一类病修过一次，**取图侧三个贴头点又复现一遍** —— 说明过滤要放在
  唯一出口，别在各贴头点分别写。**现已修在唯一出口** `ImageHeaderPolicy.headersFor`（提交 `784fd62`），
  三个贴头点都从它取；**别再在那三处补第二遍过滤**，也别把这条剔除"挪回"贴头点。
- 判别"取到的字节到底是不是图"的最硬一条是**首 4 字节魔数**：`52494646`=RIFF(webp)、`ffd8`=JPEG、
  `89504e47`=PNG、`1f8b08`=**gzip**、`3c…`(`<`)=HTML 拦截页。配合 `BitmapFactory`
  `inJustDecodeBounds` 读出的 `-1x-1`，一次读数就能把"CDN 给的不是图 / 给的是压缩流 / 图本身损坏"
  三种病分开 —— 而它们在 UI 上都只写成"加载失败"或"去混淆失败"。

**Why:** 这三条叠起来是一次真实闪退的全部成因（`target footprint 268435456`、
`<1% of heap free after GC`，打开大图页时只剩 52 MB），而单看我们的代码只会怀疑"动图太大"。

**How to apply:**
- 新增/改动任何 ImageLoader 的 `components` 顺序前，先确认**谁在写盘**：把"http/https 全接管"的
  自定义 fetcher 放在 `OkHttpNetworkFetcherFactory` 之前 = 那把 DiskCache 直接作废。
- 判断"某张图要不要走自定义 fetcher"的口径是**它是否需要改写字节**（`ImagePipelinePolicy.needsBytePipeline`），
  不是"它是不是图片"。
- 报"缓存上限/清缓存不生效"这类问题，第一步看的是**有没有写入者**，不是看配额数字。
- 报"图全加载不出来"时，先确认**响应的编码协商有没有被我们自己接管**（搜 `Accept-Encoding` 的写入点），
  再怀疑防盗链与解码器；并把魔数/`inJustDecodeBounds` 尺寸打进失败文案，一次读数就能分岔。
- 相关：[[project-gallery-data-ceilings]]（视频条目没有更小转码档、两站缩略档恒 jpg）、
  [[reference-gradle-cache-sources-jars]]（读法）、[[feedback-degrade-paths-must-fail-loud]]
