# 优选 IP + Safebooru 收口 + 头像落盘（2026-10-01 落地记录）

对应那份四步计划（Safebooru / Cloudflare 优选 IP / JM 分流 / 缓存）。
本文只记**已落地的部分**与**实测出来的更正**，未落地的部分写清卡在哪。

## 〇、开工前发现：第 1 步已经在工作区里了

`SafebooruClient.kt`、`GallerySite.SAFEBOORU`、10 处分派点、来源胶囊、图标、
`SAFE_RATINGS` 里的 `g` 都是未提交状态（工作区多 AI 共享，先审未提交 diff 这条口径照旧）。
所以本轮第 1 步的动作是**复核 + 补判据**，不是重写。复核量出 3 条真缺陷（下面 1.1~1.3）。

## 一、Safebooru 收口（批次 A）

### 1.1 标签串键名错 → 整站卡片一枚标签都没有

站方的键叫 `tag_string`，**根本没有 `tags` 这个键**（实测字段表：`id created_at … tag_string fav_count …`）。
DTO 原来声明的是 `val tags`，`ignoreUnknownKeys = true` 之下不会抛错，只会静默解成空串 ——
表现是 HTTP 200、正文合法、卡片标签数为 0、屏蔽规则与标签翻译同时失效。
已改成 `@SerialName("tag_string")`。

### 1.2 降档尺寸写死常量 → 整站卡片按同一个假比例摆

原来 `largeWidth = 850, largeHeight = 1133, previewWidth = 180, previewHeight = 180` 是硬编码的，
而 `GalleryPost.cardRatio` **优先吃 `largeWidth/largeHeight`**（瀑布流摆放前就得知道比例）。
实测同一池子里 sample 档各不相同：850×1133 / 850×988 / 850×1416 / 850×1074。
顶层也没有任何 `sample_width` 类字段 —— 降档尺寸的唯一出处是 `media_asset.variants[]`（逐档带 `width/height`）。

已改成读 `variants`：`sample` 档给中档尺寸、`180x180` 档给缩略尺寸、缺 `media_asset` 时退回
`image_width/height`（降档不改比例，所以那是真值而不是编造）。
顺带钉住一条坑：**档名里的数字是外接框**（`180x180` 档对 3000×4000 的竖图实测是 135×180）。

### 1.3 视频条目的中档是原片 mp4 → 静默黑屏

站方对视频条目给 `has_large=false` 且 `large_file_url == file_url`（都是 `.mp4`），
与 Gelbooru 那个坑同形：交给 Coil 等于先下整片再解码失败。
`variants` 里有站方自己的静帧（720 档 webp / 360 档 jpg），所以中档换成静帧，
与 yande.re 那一路"中档本身就是静帧"的模型对齐。

另外这一站**给**两样另两站没有的真数据，已经接上：`fav_count`（如 170）与
`media_asset.duration`（如 9.055782 秒，图片条目是 JSON `null` → 保持 null）。
`/tags.json` 还带 `is_deprecated`（Gelbooru 没有，只能一律 false）→ 也用真值。

### 1.4 三条"待实测"收掉了

| 待定项 | 实测结论 |
|---|---|
| 单页 URL 前缀 | `https://safebooru.donmai.us/posts/{id}` → **200**，代码里那串是对的 |
| danbooru 画师端点对 safebooru 是否适用 | 适用。`artists.json?search[name]=qitoli` → 200，返回本人 + `other_names` |
| Safebooru 的分页/空结果口径 | `page` 真翻页、`limit` 天花板 320；**空数组是合法结果**（查无此标签），而高分池回 0 条当失败交出去 |

新量出来的一条：**Safebooru 全站只有 `g` 一档**（`rating:s` / `rating:q` / `rating:e` 各回 0 条，
而 `rating:g` 有结果）。所以 `g` 漏进 `SAFE_RATINGS` 的后果不是"少数几张误打码"而是**整站被打码**。

### 1.5 一处观感取舍（用户 2026-10-01 已确认保持）

`fastUrl`（大图页开门那一档）原来用 `preview_file_url`，只有 180px，铺满一屏糊到认不出画的是什么。
现在改用 `variants` 的 **360 档**，与另两站的 preview 级同口径（yande 300×212 / Gelbooru 350px 级）。
代价：开门多一笔约 32 KB 的下载，且**不再**与网格卡片同址命中缓存（实测同一素材 180 档 9.7 KB、
360 档 32 KB、sample 210 KB、原图 2.7 MB）。摆出这两个数之后用户选了 360。

### 1.6 判据

`GallerySafebooruParsingTest` —— 13 条，JSON 全部是 2026-10-01 的实测原文（不是照文档编的样本）。

## 二、头像与图片的重复加载（批次 B）

### 2.1 先量清楚"重复"的到底是什么

- **图片字节本来就落盘**：画廊那份 Coil `DiskCache`（`cacheDir/gallery_img`，默认 512 MB）
  2026-09-29 起真的会被写入（那之前配了却没人往里放东西）。CDN 回 `max-age=315360000`，回看不回网。
  → 所以"图片重复下载"这一条**不成立**，没为它改任何东西。
- 真正重复的是**头像地址的 JSON 解析链**（pixiv `/ajax/user` → fanbox `creator.get` → Mastodon 查询，
  每人 2~4 笔），两份缓存都只在进程内，冷启动全量重发。

### 2.2 修掉一条"实现了但没接线"

`GalleryArtistProfileScreen.loadCredits` 直调 `resolveArtistAvatar`，**绕过了**既有的
`GalleryArtistCreditsCache` 与 `GalleryArtistAvatarCache` —— 每进同一位画师都重发整链。
现在记忆化收在 `resolveArtistAvatar` 内部（三个读者共用同一份判据，不再各自决定要不要问一遍）。

### 2.3 推翻批次 L 那条"头像地址刻意不落盘"

那条裁决的理由是"头像地址会过期"。实测不成立：
pixiv 的 `imageBig` 是 `i.pximg.net/user-profile/img/2017-03-08/…/12240120_<md5>_170.jpg` ——
不带任何过期参数的静态路径，2017 年的头像今天仍回 200，响应 `Cache-Control: max-age=31536000`。
（那条理由里"编号会失效"那半仍然成立：拿旧 pixiv 用户号去要头像，站方回
`error=true / 該当ユーザーは既に退会したか…` —— 所以落盘的是**地址**，仍然不存用户编号。）

新增：
- `gallery/domain/GalleryArtistAvatars.kt` —— 纯判据，三档语义分开：
  **没有条目** = 没查过（该发请求）、**空串** = 查过了确实没脸（**不许再查**）、**有地址** = 直接画。
  键沿用 `GalleryArtistCreditsKey`（站点|名字|出处）—— 出处反查那条理由不改（混用会给这一位挂上另一位的脸）。
- `gallery/data/GalleryArtistAvatarStore.kt` —— 磁盘档 `filesDir/gallery_artist_avatars.json`，
  照关注名单那份规格：构造时同步读一次、写盘 `*.tmp` 再改名、解不开先留档再开空档。上限 600 条，丢最早解析的。
- `GalleryArtistAvatarCache`（进程内那份，uid 键）撤掉 —— 留两份迟早会漂。

外链那一档（`ArtistCredits` 的 links）**仍然只在进程内**，理由写在代码里：
它一次只要一笔请求、且画师换链接/删号会让它变味。

## 三、Cloudflare 优选 IP（批次 C）

### 3.1 前提两条，都量过

1. **绑 IP 不改证书校验对象**：`curl --resolve safebooru.donmai.us:443:104.21.91.168` → 200 且
   `ssl_verify_result=0`。OkHttp 侧就是自定义 `Dns` 这条路（阿里云 HTTPDNS 官方文档对 OkHttp 的口径
   也是"用它的自定义 DNS，比通用方案简单且更通用"），**不需要自造 `SocketFactory`**。
2. **TCP 通 ≠ 能用**：同一域名四台 CF 节点里 `108.162.193.171` 稳定 **403**、另三台 200；
   `konachan.net` 换两台边缘节点都还是 403（所以它不进默认适用域名表，那份可行性文档里
   "konachan.net 有戏值得试"这一条**被量掉了**）。
   延迟确实有差：0.41 / 0.62 / 0.64 / 0.66 s → 排序有意义。

### 3.2 落地的形状

| 文件 | 管什么 |
|---|---|
| `data/network/PreferredIpRules.kt` | 纯判据：读用户粘的 IP / 域名表、什么算通、按什么顺序、什么时候退回。**不 import OkHttp 与 Android**，已进 `_probe/l0` 单跑清单 |
| `data/network/PreferredIpRuntime.kt` | 进程内配置 + 读数。**探活结果刻意不落盘** |
| `data/network/PreferredIpProbe.kt` | 一台候选一个客户端（Dns 钉死那台地址）+ 强制 `NO_PROXY`，发**真实业务请求**并看状态码；并发 |
| `data/network/PreferredIpRouting.kt` | `PreferredIpDns`（不在表里/没开启 → `Dns.SYSTEM`）与回退拦截器 |
| `feature/settings/PreferredIpSettings.kt` | 网络设置页那一组：开关在页面上直接可见，列表编辑 + 保存并探活在弹窗里 |

三条设计要点（都有理由，不是顺手）：

- **回退必须显式做第二次**：共享客户端今天是 `retryOnConnectionFailure(false)`，同一笔里 OkHttp
  不会自己换下一个候选地址。回退拦截器挂在**熔断拦截器内侧**，这样两次尝试只有最终那次进熔断，
  不会把正常源计成两笔失败。只重投 GET/HEAD；POST 失败就照实失败（重投等于提交两次）。
- **默认关闭 = 零回归**：关闭时判据对任何域名都交 `Dns.SYSTEM`，即"这层等于没挂"。
- **挂代理时整层不参与**，UI 明写这一句：代理在的时候 OkHttp 只用 `Dns` 解析代理主机、
  目标域名由代理解析，从代理那儿量到的延迟不是用户这台机器的。

默认适用域名表预置两条实测过的：`safebooru.donmai.us`（`/posts.json?limit=1` → 200）与
JM 图床 `cdn-msp.jmapinodeudzn.net`（根路径 → 200，正文是节点标识串）。**IP 列表默认空**。

### 3.3 判据

`PreferredIpRulesTest` —— 22 条：分隔符与去重、上限 12、认不出的项丢但项数要说得出、
IPv6 形式（含 `[...]`、拒 zone id）、子域算覆盖而形近域不算、最长条目优先、
200 过 / 403 不过 / API 档的 404 不过 / `require2xx=false` 的 404 过 / 5xx 永不过、
超时文案的秒数与超时常数是同一个、健康候选按延迟升序且不通的不到点自动翻身、
结果过期不再采信、延迟相同按 IP 定序、关闭/不在表/没读数/观察窗内四种都退回系统解析、
过了窗自己翻身。

## 四、验证

- `_probe/l0/run-judgment-tests.sh` → **OK (147 tests)**
- 全量单测 → **713 / 0 失败 / 0 错误**（本轮新增 Safebooru 13 + 头像判据 6 + 优选 IP 22）
- `assembleDebug` 成功

## 五、要真机确认的

1. 画廊选 Safebooru：搜索 / 猜你喜欢 / 热门三处都出图，卡片**有标签**（1.1 那条修复的屏幕表现），
   且**高低错落**而不是清一色同比例（1.2 那条）；
2. Safebooru 的视频条目：能播，且没播之前那一屏是静帧而不是黑屏（1.3）；
3. 「关于这张图」里 Safebooru 的条目应出现**时长**行，卡片信息里出现**收藏数**（另两站仍没有，那是事实）；
4. 关注几位画师 → 杀进程冷启 → 首页「正在关注的画师」那一栏的脸应当几乎立刻出现（不再一张一张蹦）；
   同一位画师反复进出介绍页，日志里不该再出现重复的 pixiv/fanbox 头像请求；
5. 网络设置 → Cloudflare 优选 IP：不开启时画廊/漫画行为与今天一致；粘 IP → 保存并探活，
   每台候选各占一行并且 403 那台要说"这台边缘节点不服务该域名"。

## 六、第 3 步（JM）的最终状态：**只做图床，主站池子不做**

- **图床那一半已经成立**：`cdn-msp.jmapinodeudzn.net` 在默认适用域名表里（根路径实测 200），
  而 JM 取图走 `VeneraImageFetcher` + 共享客户端（`newBuilder()` 派生），
  所以批次 C 挂上的 `Dns` 与回退拦截器对它**自动生效**，不需要为 JM 另开一条链路。
- **主站多域名探活：用户 2026-10-01 拍板不做**。依据就是这次实测 —— 8 个候选域名
  （`18comic.vip` / `18comic.org` / `18comic-c.art` / `jmcomic.me` / `jmcomic1.me` /
  `18comic-palworld.vip` / `18comic-palworld.club` / `18comic-c.club`）
  在无代理下 `/`、`/promote?page=0`、`/week` **全是 000**，且它们都不走 CF。
  经代理再量才分得开"域名真死了"与"本机被封"：`18comic-c.art` 301、`18comic.vip` 403、其余 000。
  ⇒ 结论：**本机验收不了的网络机制不建**。换网络（或换出口的模拟器）之后再评估。

### 一条已知限制（不是缺陷）

`jm.js` 的图片域名可以被站方换掉：`refreshImgUrl()` 读 `${baseUrl}/setting?app_img_shunt=…`
返回的 `img_host` 并覆盖 `JM.imageUrl`。默认表里预置的是源码那个静态值；
哪天 `img_host` 换成别的域名，**图会退回正常解析**（不会变砖，只是优选 IP 不再参与），
处置办法是把那个域名加进设置页的适用域名表 —— 而不是在源码里再硬编码一批猜的域名。
（`konachan.net` 那种"看着像同族、实测恒 403"、`cdn.donmai.us/` 根路径实测 403，都是硬猜的代价。）

---

*落地：批次 A/B/C + 批次 D 的图床那一半 · 2026-10-01 · 主站域名池按实测结论不做。*
