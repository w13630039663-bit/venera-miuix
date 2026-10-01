# 直连源调研报告（2026-10-01 实测）

> 结论全部来自**本机实测**（系统代理开关 ProxyEnable=0，PAC 指向 127.0.0.1:7890 但未启用）。
> 方法：TCP 443 建连 + `web_fetch` 真实 HTTP 取数对照。
> 沙箱屏蔽了 curl/.NET 的出站（curl 全返回 000），但 **TCP 层探测与 web_fetch 有效**。

## 〇、先纠正一个前提

用户说"我系统都是开着代理的"——是的，PAC 存在（`127.0.0.1:10000/pac` → 全流量 `PROXY 127.0.0.1:7890`），
而 7890 端口确实是 OPEN 的。**但 `ProxyEnable=0`**，注册表级的系统代理开关是关的。

这意味着：本次测得的 `Direct=False` = "不开代理时不可达"，正是我们要找的**必须挂梯子的域名**；
`Direct=True` = 裸连就能用。这个对照恰好是想要的口径，结论可直接用。

## 一、核心发现：封锁是**按主机名(SNI)** 的，不是按 IP

这是本次最有价值的发现，直接决定改造策略：

| 域名 | 直连 | 说明 |
|---|---|---|
| `danbooru.donmai.us` | ❌ | 被墙 |
| `safebooru.donmai.us` | ✅ | **同站同 IP 段，通** |
| `cdn.donmai.us` | ✅ | **同站图床，通** |
| `yande.re` | ❌ | 被墙 |
| `assets.yande.re` | ✅ | **子域名通** |
| `www.pixiv.net` / `i.pximg.net` | ❌ | 被墙 |
| `s.pximg.net` / `i.pixiv.re` | ✅ | **pixiv 图床子域名通** |

**推论**：不要按"某个站通不通"下结论，要按**具体 hostname** 逐个测。
同一站点的 API 域名和图床域名可能一个通一个不通，可以混搭使用。

## 二、图库（Gallery）：可直连的替代站 —— 三个都真验证过

| 站 | API | 匿名 | 图床 | 图床直连 | 实测 |
|---|---|---|---|---|---|
| **Safebooru** | `safebooru.donmai.us/posts.json` | ✅ 无需 key | `cdn.donmai.us` | ✅ | 200 取到真数据 |
| **e926** | `e926.net/posts.json` | ✅ 无需 key | `static1.e926.net` | ✅ | 200 取到真数据 |
| **Zerochan** | `zerochan.net/?json`、`/Tag?json&p=N` | ✅ | `s3.zerochan.net` | ✅ | 200 取到真数据，分页 `p=N` 有效 |

### ✅ 首推 Safebooru —— yande.re 的近乎完美替代

理由（全部实测）：
- 匿名 `/posts.json?limit=1` → HTTP 200，字段与 Danbooru 系完全一致
  （`file_url` / `large_file_url` / `preview_file_url` / `media_asset.variants[]` / `tag_string_*` / `rating` / `fav_count`）
- 图床 `cdn.donmai.us` 直连通 ✅（**这是关键**——很多站 API 通但图加载不出来）
- 标签补全：`/tags.json?search[name_matches]=hatsune*&limit=5` → 200，可直接复用现有 `searchTags`
- 排行：`/posts.json?tags=order%3Arank` → 200，可替代 yande.re 的 `popular_recent`
- **全年龄**（rating:g），无成人内容风险
- 分级：Danbooru 系的 `g/s/q/e`，与现有 Gelbooru 归一化逻辑同源

对比现有站：
- `yande.re` ❌ 被墙
- `danbooru.donmai.us` ❌ 被墙（**Safebooru 是它的全年龄镜像，且没被墙**）
- `gelbooru.com` ❌ 被墙 + DAPI 匿名 401 必须 key（双重不可达）

### ⚠️ e621 / e926 的差异（重要坑）

- `e926.net`（全年龄）/posts.json → **200 可用**
- `e621.net`（成人版）TCP 通，但 HTTP 取数**反复失败**
  推测原因：e621 强制要求**自定义 User-Agent**（官方规则，用默认 UA 会被拒）。
  现有 `UserAgentPolicy.kt` 会注入 UA，接入时需确认 UA 符合 e621 要求。
  **建议先只接 e926（全年龄）**，e621 单独作为可选源。

### ❌ 已排除的图库站

| 站 | 结果 |
|---|---|
| `konachan.net` | Cloudflare 403 `Just a moment...` |
| `konachan.com` / `lolibooru.moe` / `chan.sankakucomplex.com` | TCP 不通 |
| `rule34.xxx` / `api.rule34.xxx` | TCP 不通 |
| `gelbooru.com` / `img3|img4.gelbooru.com` | TCP 不通 |
| `anime-pictures.net` | TCP 不通 |

### SauceNAO（以图搜图）

- `saucenao.com` **TCP 通**，但项目 memory 里记着它一直卡在 Cloudflare 403
  （见 `memory/project-saucenao-cloudflare-block.md`：换 UA 三种全 403、填了 Key 仍 403）
- 所以 SauceNAO 的问题**不是网络可达性，是过盾**。别把这两件事混在一起。

## 三、漫画源：MangaDex 是最稳的直连源

### ✅ MangaDex —— 全链路直连通

| 环节 | 域名 | 直连 |
|---|---|---|
| 搜索/详情 | `api.mangadex.org` | ✅ 200（`total: 94863`） |
| 章节列表 | `api.mangadex.org/chapter` | ✅ 200（`total: 9514` 中文章节） |
| 图片服务器 | `at-home/server/{id}` | ✅ 200 |
| 图片实际域名 | `*.mangadex.network`（动态） | ✅ 通（实测 `cmdxd98sb0x3yprd.mangadex.network`） |
| 封面 | `uploads.mangadex.org` | ✅ 通 |
| OG 图 | `og.mangadex.org` | ✅ 通 |

注意：`at-home/server` 返回的 `baseUrl` 是**动态子域名**（`cmdxd98sb0x3yprd.mangadex.network`），
不是固定的 `uploads.mangadex.org`。实测这类动态子域名直连通，但 `s2.mangadex.network` 不通——
**接入时必须用 API 返回的 baseUrl，不能硬编码**。

MangaDex 已有官方 JSON API，无 Cloudflare，无需登录（登录只影响论坛评论）。
项目里已有 `manga_dex.js` 与 `source/mangadex` 包，是**改造成本最低**的直连源。

### ✅ ComicK —— 真实 API 域名是 `api.comick.dev`

踩坑记录（这三个域名别搞混）：
- `api.comick.fun` → DNS 解析失败 ❌
- `api.comick.cc` → 能解析，但返回"**This domain is for sale**" ❌
- **`api.comick.dev`** → ✅ 200，返回完整漫画详情（title / hid / chapter_count / recommendations / reviews）
- `comick.art/api/search?q=xxx` → ✅ 200（搜索可用，返回 hid、slug、chapter_count、封面）
- 封面 `cdn1.comicknew.pictures`、`meo.comick.pictures` → 都通 ✅

⚠️ `comick.art/api/comic/{hid}/chapters` 和 `comick.art/api/comic/{hid}` 都返回 404——
**章节 API 要走 `api.comick.dev`，不要走 comick.art**。

### ✅ 其他 TCP 直连可用的漫画源候选

`api.copy-manga.com`、`www.copy20.com`（拷贝漫画）、`api.creative-comic.tw`（CCC追漫台）、
`mycomic.com`、`www.manga2026.com`（热辣漫画）、`mangaplus.shueisha.co.jp`、
`webtoons.com`、`mangapark.net`、`manganato.com`、`chapmanganato.to`、`mangakakalot.com`、
`bato.to`、`weebcentral.com`、`manga.bilibili.com`、`api.bilibili.com`、`cdn.jsdelivr.net`

其中 `cdn.jsdelivr.net` 直连通是个好消息：**现有的源脚本更新机制**（所有 .js 头部都指向
`cdn.jsdelivr.net/gh/venera-app/venera-configs@main/xxx.js`）在国内可用，不用改。

### ❌ 必须挂梯子的漫画源

`hitomi.la`、`nhentai.net`、`e-hentai.org`、`exhentai.org`、`wn01.link`、
`picacg`（`picaapi.picacomic.com`）、`api.pixiv.net`、`jm` 相关域名

## 四、接入成本评估（现有代码）

### 图库侧
架构是 `GallerySite` 枚举 + 每站一个 Client，走统一 `VeneraNetworkClient`。
加一个站要改的 `when(site)` 分支约 10 处：
- `GallerySite.kt`（枚举本体）
- `GalleryFeedSource.kt`、`GalleryForYouViewModel.kt`、`GallerySearchViewModel.kt`（取数分派）
- `GalleryPostScreen.kt`（`fetchById`）
- `GalleryRanking.kt`（排行语法）
- `GalleryTag.kt`、`GalleryArtistRows.kt`、`GallerySearchSource.kt`（标记字母/建议）
- `SauceNao.kt`（反查结果映射，可选）

**好消息**：Safebooru 是 Danbooru 系，字段与现有 `GelbooruClient` 的 `GalleryPost` 映射高度重合，
分页（`page`/`limit`）、标签语法（`tags=`）、rating 分级都同族，**改起来比接一个全新 API 便宜很多**。

### 漫画源侧
基础建设已经齐了，不需要新做：
- `VeneraNetworkClient.kt:79-85` 已有 HTTP/SOCKS 代理设置
- `HostCircuitBreaker.kt` 已有域名熔断（连续 2 次失败熔断 60s），不可达源会毫秒级失败
- `CloudflareBypassInterceptor` 已有过盾
- 源脚本是 assets 里的 .js，加新源 = 加一个 .js + 在 `index.json` 加一条

## 五、建议的落地顺序

1. **MangaDex** —— 已有实现，只需确认图片走 API 返回的动态 baseUrl；全链路直连通，风险最低
2. **Safebooru** —— 替换 yande.re 的主力图库站，匿名、全年龄、图床直连
3. **Zerochan** —— 作为图库的第二个源补充（有 JSON，分页明确）
4. **ComicK（api.comick.dev）** —— 漫画源补充，注意用 `.dev` 域名
5. **e926** —— 图库第三源（e621 因 UA 要求先放着）

不建议动的：`e-hentai` / `nhentai` / `hitomi` / `pixiv`，这些只能靠代理，保留现状即可。

## 六、遗留待确认

- `e621.net` 的强制 User-Agent 规则具体是什么（e926 已可用，e621 待单独验证）
- Zerochan 的 rating/分级过滤参数（返回的 JSON 里没看到 rating 字段）
- `manga.bilibili.com` 的 twirp API 鉴权细节（TCP 通，API 未实测）
- `api.copy-manga.com` 的鉴权 header（TCP 通，API 未实测）
