# Better Cloudflare IP 能否用于图库 & 其他漫画源（2026-10-01 实测）

> 问题：Better Cloudflare IP 这套（优选 CF 边缘 IP + hosts/SNI 绑定）
> 能不能用在我们的**图库站**和**其他漫画源**上？

## 结论先行（一张表看懂）

**能用的前提只有一个：目标站必须真的挂在 Cloudflare 上。**
实测下来，规律极其清晰：

| 类别 | 站点 | 走 CF? | 本机直连 | 优选 IP 有用? |
|---|---|---|---|---|
| **图库** | `safebooru.donmai.us` | ✅ | ✅ | 不需要（本来就通） |
| | `cdn.donmai.us`（图床） | ✅ | ✅ | 不需要 |
| | `konachan.net` | ✅ | ✅ **TCP 通** | ⚠️ **有戏，值得试** |
| | `yande.re` | ❌ | ❌ | ❌ **无效** |
| | `danbooru.donmai.us` | ❌ | ❌ | ❌ **无效** |
| | `gelbooru.com` / `img4.gelbooru.com` | ❌ | ❌ | ❌ **无效** |
| | `konachan.com` | ❌ | ❌ | ❌ 无效 |
| | `i.pximg.net` / `www.pixiv.net` | ❌ | ❌ | ❌ **无效** |
| | `nhentai.net` / `i3/t3.nhentai.net` | ❌ | ❌ | ❌ 无效 |
| | `e-hentai.org` / `exhentai.org` | ❌ | ❌ | ❌ 无效 |
| | `hitomi.la` / `a.hitomi.la` | ❌ | ❌ | ❌ 无效 |
| **漫画源** | `cdn-msp.jmapinodeudzn.net`（JM 图床） | ✅ | ✅ **HTTP 200** | ✅ **已验证** |
| | `picaapi.picacomic.com` / `manhuabika.com` | ✅ | ⚠️ TCP 通 HTTP 失败 | ⚠️ 待补测 |
| | `18comic.vip` / `18comic.org` / `jmcomic.me` | ❌ | ❌ | ❌ **无效** |
| | `weebcentral.com` / `mangapark.net` | ✅ | ✅ | 不需要 |
| | `manganato.com` / `chapmanganato.to` | ✅ | ✅ | 不需要 |
| | `komiic.com` | ✅ | ✅ | 不需要 |
| | `i.pixiv.re` / `wsrv.nl` / `images.weserv.nl` | ✅ | ✅ | 不需要（本来就是反代） |
| | `manga.bilibili.com` | ❌（腾讯/阿里系） | ✅ | 不需要（国内站） |
| | `mangaplus.shueisha.co.jp` | ❌ | ✅ | 不需要 |

---

## 一、最关键的发现：**"走 CF" 和 "能否直连" 高度相关**

把所有测过的站按「走 CF 吗」分组，规律一目了然：

### 走 CF 的站 → 基本都能直连

```
safebooru.donmai.us  104.26.10.39   CF ✅  TCP ✅
cdn.donmai.us        104.26.11.39   CF ✅  TCP ✅
konachan.net         104.21.85.89   CF ✅  TCP ✅
safebooru.org        104.21.34.109  CF ✅  TCP ✅
donmai.moe           104.21.52.64   CF ✅  TCP ✅
komiic.com           104.26.9.161   CF ✅  TCP ✅
weebcentral.com      172.67.151.248 CF ✅  TCP ✅
mangapark.net        172.67.147.24  CF ✅  TCP ✅
manganato.com        104.26.2.67    CF ✅  TCP ✅
chapmanganato.to     104.21.23.222  CF ✅  TCP ✅
```

### 不走 CF 的站 → 基本都连不上

```
yande.re             103.97.176.73    CF ❌  TCP ❌
danbooru.donmai.us   74.86.17.48      CF ❌  TCP ❌
gelbooru.com         202.160.128.238  CF ❌  TCP ❌
img4.gelbooru.com    38.121.72.166    CF ❌  TCP ❌
konachan.com         173.252.105.21   CF ❌  TCP ❌
i.pximg.net          157.240.8.36     CF ❌  TCP ❌
www.pixiv.net        103.97.176.73    CF ❌  TCP ❌
nhentai.net          74.86.12.173     CF ❌  TCP ❌
e-hentai.org         108.160.167.165  CF ❌  TCP ❌
hitomi.la            162.125.32.6     CF ❌  TCP ❌
18comic.vip          37.209.192.x     CF ❌  TCP ❌
```

**为什么？** 因为防火墙**不能封 CF 全段**——CF 段上有大量正常网站，封了会大规模误伤。
所以 CF 段天然"漏"，不走 CF 的独立 IP 则被精确封锁。

这也解释了上一份发现的**"按 hostname 封锁"**：
同一个 donmai.us，`danbooru.donmai.us`（非 CF）被封，`safebooru.donmai.us`（CF）通。
同理 `konachan.com`（非 CF）封、`konachan.net`（CF）通。

---

## 二、逐回答你的问题

### Q1：图库能用 Better Cloudflare IP 吗？

**大部分不能，但有一个值得试的例外。**

- ❌ **yande.re / danbooru / gelbooru / pixiv / nhentai / e-hentai / hitomi**：
  **都不走 CF**，优选 IP 对它们**完全无效**。
  这是硬前提不满足，不是配置问题。

- ⚠️ **`konachan.net` 值得试**：
  它走 CF（`104.21.85.89`）且 **TCP 通**。
  但注意——上一轮本喵测过 `konachan.net` 的 HTTP 层是 **403 Cloudflare**
  （`Just a moment...`）。所以它是"CF 通但被 CF 自己的挑战页挡了"。
  **换优选 IP 有可能绕开**（换到未被 CF 标记的节点），值得一试，但不保证。

- ✅ **Safebooru 本来就通**（本喵之前已验证 `/posts.json` 200 可用），
  **不需要**优选 IP——它已经是推荐的替代站了。

**结论**：图库这边，优选 IP **救不了** yande.re/danbooru/gelbooru/pixiv 这几个主力站。
对这些站，唯一现实的路仍是**换用同族未封站（Safebooru / e926 / Zerochan）**。

### Q2：其他漫画源能用吗？

**分两拨：**

**✅ 已验证可用的**
- `cdn-msp.jmapinodeudzn.net`（**JM 图床**）：走 CF，HTTP 200 返回真实内容
  → 禁漫的**图片**可通过此路加载，即使主站打不开
- `picaapi.picacomic.com`（Pica API）：走 CF，TCP 通，但 HTTP 失败
  → 需带签名 + 换优选 IP 补测，**不能现在判定不行**

**❌ 无效的**
- `18comic.vip` / `18comic.org` / `jmcomic.me` / `jmcomic1.me` / `18comic-c.art`
  → **都不走 CF**（`37.209.x` / `199.249.x` / `211.139.x`），优选 IP 无效

**✅ 本来就能直连的（不需要优选 IP）**
一整批走 CF 的漫画源实测全通：
```
weebcentral.com      mangapark.net      manganato.com
chapmanganato.to     komiic.com         bato.to
api.copy-manga.com   www.copy20.com     api.creative-comic.tw
mangaplus.shueisha.co.jp（非CF但通）
manga.bilibili.com（国内站，通）
```
这些**直接就能用**，根本不需要折腾优选 IP。

---

## 三、一个重要的反向结论

**Better Cloudflare IP 在这些站上，价值没有想象中大。**

原因是：
- 走 CF 的站 —— **CF 段本身就能直连**，不需要优选（Safebooru、mangapark、manganato 等已验证）
- 不走 CF 的站 —— 优选 IP **根本不适用**（yande.re、danbooru、pixiv、JM 主站）

真正需要优选 IP 的，只有**"走 CF 但当前被挡"**的窄场景：
- `konachan.net`（CF 403 挑战页）
- `picaapi.picacomic.com`（CF 通但 HTTP 失败）
- JM 图床（已通，但优选可提升稳定性/速度）

---

## 四、和"换 DNS"的区别（别再混淆）

| | 机制 | 适用 |
|---|---|---|
| 换 DNS / DoH | 只换解析结果 | ❌ 对 IP 层封锁无效 |
| **优选 CF IP** | 主动挑**可达的 CF 边缘节点** + SNI 绑定 | ✅ 仅对**走 CF** 的站有效 |
| 同族未封站 | 换入口域名 | ✅ 已验证（Safebooru 等） |

---

## 五、建议

### 图库侧
1. **主力仍用 Safebooru**（已验证匿名、图床直连、`/tags.json` 补全、`order:rank` 排行全可用）
2. `konachan.net` 可尝试加优选 IP 列表作为**可选加速项**，但别当主力
3. yande.re / danbooru / gelbooru / pixiv **放弃优选 IP 这条路**，改用 Safari/e926/Zerochan 替代
4. **pixiv 图片**可走公共反代：`i.pixiv.re`、`wsrv.nl`、`images.weserv.nl`（都走 CF 且实测通）

### 漫画源侧
1. **JM 图床落地优选 IP**：`cdn-msp.jmapinodeudzn.net` 已验证 HTTP 200
2. **JM 主站**：放弃优选 IP（不走 CF），改用 jmcomic SDK 那套**多域名探活**
   （其生态有 7+ 域名，逐个探测哪些可用）
3. **Pica**：补测——带签名 header + 换优选 IP（`104.16.x` / `104.24.x` / `162.159.x`）
4. **直接可用的一批**（`mangapark` / `manganato` / `weebcentral` / `komiic` 等）
   优先接进来，**性价比远高于折腾优选 IP**

### App 侧设计
- 若要做优选 IP，做成**用户可配置 + 定期探活 + 失败回退**，
  不要硬编码 IP（CF 会调节点，防火墙会持续封，写死的 IP 失效后比不绑更糟）

---

## 六、风险

- CF 优选 IP **会失效**，必须探活 + 回退
- 硬绑 hosts 的 IP 若被 CF 回收 → 该域名**彻底不可用**
- 本轮每个 CF 段只取了代表地址（`.0.1`/`.1.1` 等），**不构成完整优选集**
- `konachan.net` 的 403 是 **CF 挑战页**（上一轮实测），换 IP 只是可能绕过，非必然
