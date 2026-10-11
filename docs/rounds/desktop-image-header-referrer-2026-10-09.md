# 批 79 · 取图那一跳接上防盗链头（B 路，2026-10-09）

**一句话**：桌面那一路图片以前走的是 **Coil 的默认客户端** —— 既没有防盗链 `Referer`，也没有 `--proxy=`，
也没有引擎那三枚超时。本批把 Coil 的单例供体装上，客户端从 `DesktopHttpEngine` 那台**派生**并多挂一枚
只认 `pximg.net` 的拦截器，值与对岸那张表**逐字同值**由判据回读核对。`app/` 零改动。

## 1. 这一格是谁量出来的（批 43 那三帧）

`docs/rounds/desktop-basic-gaps-plan-2026-10-05.md` §48 记着断点：pixiv 那一档把头像地址取到并落了档
（`i.pximg.net/…_170.jpg`），而屏上仍是字母座「4」。同一次 curl 里成对读数：**无 `Referer` → 403**
（146 字节 nginx 错误页）/ **带 `Referer: https://www.pixiv.net/` → 200、`image/jpeg`、14,565 字节**。
那一节把缺口分成三件事，本批做的是前两件：

| 编号 | 那一格的原文 | 本批 |
|---|---|---|
| G1 | 图片那一层**根本没接线**（Coil 默认客户端，连 `--proxy=` 都不走） | ✅ 装上单例供体，客户端派生自引擎那台 |
| G2 | 就算接上 engine 也还缺那枚拦截器 | ✅ `DesktopImageHeaderInterceptor` |
| G3 | 旧负档会把新那一路永久遮住（帧 A 那一格） | ❌ 不在本批，仍等拍板 |

## 2. 为什么走 B 路而不是 A 路

A 路（把 `app/…/data/network/ImageHeaderPolicy.kt:67,101` 那两处的常量改成转交自纯 JVM 那颗，
让桌面复用共享那张表）到今天**仍没点头** —— 那是受保域。四颗 parked 判据（`_trash/scratch-2026-10-08/DesktopImagePipelineTest.kt.hold`）
是按 A 路写的，其中第 ③ 颗「桌面不许出现 `pximg` 字面」与 B 路正好相反，所以本批把 parked 那份**留在原处不动**，
另写 B 路那五颗。

B 路的代价是**两份会漂**：桌面自持一枚值，对岸改了而这里没跟上，屏上的形状仍然是字母座。
所以抄的范围钉到最小，并由判据看着 —— 只抄 `pximg.net` 那一枚（⑤ 机械扫其余 host 一颗都不许多写）、
值逐字回读对岸那一行核对（③ 锚按对岸整行 `"pximg.net" to …`，不是只数标识符）、
客户端派生而不新造（②）、装的那一刻出声且全应用恰好一处（④）。

## 3. 落点

- 新颗 `desktop/src/main/kotlin/com/venera/desktop/gallery/data/DesktopImagePipeline.kt`：
  `DESKTOP_PIXIV_IMAGE_HOST` / `DESKTOP_PIXIV_REFERER` 两枚常量、那颗拦截器（后缀匹配**带点**：
  `cdn.notpximg.net` 不算，判据 ① 的第三枚对照钉着）、`clientForImages(base) = base.newBuilder().addInterceptor(…)`、
  `install(engine)` 装 `SingletonImageLoader.setSafe` + `OkHttpNetworkFetcherFactory(callFactory = { … })`，
  并念一行 `D_取图 装=防盗链一枚 站=pximg.net 客户端=引擎那台派生`。
- 接线在 `VeneraDesktop.kt` 的 `DesktopGalleryPorts.install(galleryHandle)` **之后一行**（早于任何开窗与取图）。
- ⚠️ 一条 API 真相：Coil 3.6.2 的 `coil3.network.okhttp.OkHttpNetworkFetcher` 在 Kotlin 侧是 **internal**
  （字节码里是 public，`javap` 看得见而编译器不给用），公开入口是**顶层**的 `OkHttpNetworkFetcherFactory`。
  parked 那份 A 路判据里写的 `OkHttpNetworkFetcher.factory(` 与 `ImageLoader.Builder.okHttpClient {`
  在这一版都不存在（`Builder` 根本没有 `callFactory`/`okHttpClient` 那两枚方法）——
  本批按 `javap` 读到的实际形状改锚。

## 4. 五条尺子与七笔扰动

`DesktopImagePipelineTest.kt`（5 条）：① 那一枚头真盖到 `i.pximg.net` / `s.pximg.net` / 裸域，
而 safebooru、yande.re、`cdn.notpximg.net` 三枚对照一颗都不许吃 / ② 图片客户端派生自引擎那台
（代理与两枚超时逐条继承、拦截器只多一枚且必须是那颗）/ ③ 值与对岸逐字同值 + 桌面全树那枚字面只住一颗文件 /
④ 应用根恰好一处 + 那颗件里 `SingletonImageLoader.setSafe` / `OkHttpNetworkFetcherFactory(` / `clientForImages(`
三样都在 + 装的那一刻念 `D_取图` / ⑤ 负向：对岸表里其余七个 host 的字面在桌面代码行里一颗都不许有。

扰动 `_qa/b79_perturb.js`（7 笔）：P1 后缀匹配退成 `contains` / P2 host 名漂一位（`pximg.com`）/
P3 新造一台而不是派生 / P4 值抄漂一位 / P5 只念不装 / P6 装两处 / P7 顺手把对岸表里另一行搬过来。

## 5. 读数（收尾现数）

- `:desktop:test`：**843 判据 / 106 类 / 0 失败**（`node _qa/three_way_counts.js` ⇒ `tests_sum=843 testcase=843 @Test=843`，三方对得上）；
  判据目录 `node _qa/check_ruler_catalog.js` ⇒ **0 处漂移**（表行 106 / 表合计 843）。
- `desktop/src/main`：**92 颗 / 18,314 行**。
- 交付包 `_qa/desktop-exe-2026-10-09-b79/`：classpath 100 条 / missing=0 / orphan=0，
  `desktop-f7f193398c27989634ea7fd25dab0a.jar` = **4,371,456 B**，`unzip -l` 数得到
  `DesktopImagePipeline`、`DesktopImageHeaderInterceptor`、`DesktopImagePipelineTest` 等新类。
- 扰动 7 笔 ⇒ 落进结果 XML 的红共 **9 条**（P5「只念不装」一次红两把尺子：装载那一处 + 零调用的 internal 件），
  全部红在正主上，无一笔红在拼写或无关用例上；还原后全量 `EXIT=0`。
- 站方那对成对读数（今天现量，不是批 43 那份过期值）：
  同一枚活地址 `curl -x http://127.0.0.1:7890` ⇒ **无 `Referer` 403 / 146 B `text/html`**，
  **带 `Referer: https://www.pixiv.net/` 200 / 29,111 B `image/jpeg`**。

## 6. 图证（带对照）

种子：隔离数据根 `_qa/b79_local/venera/` 里放一份关注名单（`yandere` / `4ddd`）与一份头像地址档，
地址档的 `source` 交**空串**（桌面规范键轨 `DESKTOP_AVATAR_SOURCE_KEY = ""`，读侧写的侧必须是同一条），
`avatar` 填今天现取的那枚活地址。起窗参数两边一致：
`--proxy=127.0.0.1:7890 --start-domain=ARTISTS --start-section=gallery-artists-follows --detail=0 --viewer=0`。

| 包 | 那一格 | 帧 |
| --- | --- | --- |
| **b78**（没有那一枚头） | 字母座「4」—— 与批 43 拍到的同一形状 | `_qa/b79_frame_control2_b78.png` |
| **b79**（装上那一枚头） | 真头像画出来了（一张红底星形profile图） | `_qa/b79_frame_artists_wall2.png` |

stdout 侧同刻的旁证：b79 那份日志念 `D_取图 装=防盗链一枚 站=pximg.net 客户端=引擎那台派生`，
而 b78 那份**没有这一行**（那枚读数本批新加）；b79 那一跑往隔离根的 okhttp 档里落下
`6923cc3f….1` = **29,111 B**（与 curl 量到的 Content-Length 逐字相同）⇒ 图字节确实是这一跑取回来的，
且只有带 `Referer` 的那一路能取回 200。

## 7. 三条工具坑（本批自踩）

1. **对照会被全局缓存做成假的**：Coil 的盘缓存不在数据根里，而在
   `%LOCALAPPDATA%\Temp\coil3_disk_cache`（本机 256 MB / 1933 颗）。第一版对照两个包都画出了脸
   —— 因为 b78 那一跑直接命中了 b79 落下的那 29,111 B。**对照前先量缓存落在哪一层**，
   这次移开那一颗条目与 okhttp 那条目（连同 `journal`）才拍到分岔。
2. **手抄的 URL 会把"我没抄对"演成"没修好"**：种子地址第一次抄成 `img/2026-09-21-15-14-14/`
   （日期段之间该是 `/` 而不是 `-`）⇒ 站方 404、屏上仍是字母座。上屏前先把那一枚 URL 单独
   curl 一遍（本轮 403/200 那对读数就是这么来的），否则图证判的是自己的抄写。
3. **落地那一屏要先确认"这一屏到底画不画那颗件"**：第一次用 `--start-domain=GALLERY` 拍到的是每日热门那面墙
   （头像栏不在里面），第二次用 `--artist=` 拍到的是单人页（名单被它盖过去了），
   第三次才对上 `--start-section=gallery-artists-follows`。取证种子换的是**落点**，
   而消费点在哪一屏要看代码里那枚 `peek` 的调用者，不是看参数像不像。
