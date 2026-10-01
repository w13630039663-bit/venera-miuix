# 借鉴项目研究：Matoi / yande.re-spider / JM / Pica

> 目标：不挂梯子的前提下，尽可能把 yande.re / danbooru / gelbooru / pixiv / pica / jm 用起来。
> 上一份已证伪「纯 DNS 手段」（IP 层封锁，真 IP 也连不上）。
> 本文档转向：**借鉴成熟项目的架构手段**——统一 API、媒体代理、缓存、分流探活、CDN 探测。

---

## 一、Matoi（最值得抄的整体架构）

仓库：https://github.com/sinkaroid/matoi — Go，"Unified REST + GraphQL gateway for booru imageboards"

### 1.1 它解决了什么

一张图站一个 provider，统一成一套 API。已接：
`danbooru / safebooru / gelbooru / e621 / e926 / yandere / konachan_com / konachan_net /
rule34 / realbooru / xbooru / tbib / hypnohub / furbooru / derpibooru`（14 个）

结构：
```
handlers/    ← HTTP 入口（每站一个 .go），含分页/参数校验/缓存读写
providers/   ← 取数（每站一个 .go），只管"怎么问站方"
models/post.go ← 统一 Post 模型（ID/FileURL/PreviewURL/SampleURL/Rating/Score/Tags/Link...）
cache/redis.go ← 统一缓存层
prd/media-resolver.md ← 媒体代理设计文档
```

**对我们的价值**：我们的 `GallerySite` 枚举 + 每站 Client，跟 Matoi 的 handler/provider 分层是同构的。
它验证了一件事——**"每站一个 provider + 统一 Post 模型" 这套抽象是可行的**，可以照着收敛我们的 `when(site)` 分派。

### 1.2 核心武器："Hunt Down" 媒体代理（Extension Resolver）

来源：`prd/media-resolver.md`，实现在 `handlers/realbooru.go` 的 `ProxyMedia()`。

**解决的问题**：列表页只给缩略图（永远 `.jpg`），不给原图真实扩展名。
原图可能是 `.jpg` / `.png` / `.gif` / `.mp4` / `.webm`，**光看缩略图猜不出来**。

**机制**（顺序探测 + 快速失败）：
```go
originalExt := path.Ext(parsedURL.Path)           // 从缩略图路径取扩展名（通常 .jpg）
extensionsToTry := []string{originalExt, ".jpg", ".png", ".gif", ".jpeg", ".mp4", ".webm"}
// 去重
seen := make(map[string]bool)
var uniqueExtensions []string
for _, ext := range extensionsToTry {
    if ext == "" || seen[ext] { continue }
    seen[ext] = true
    uniqueExtensions = append(uniqueExtensions, ext)
}

var resp *http.Response
var body io.ReadCloser
for _, ext := range uniqueExtensions {
    var testURL string
    if originalExt != "" {
        testURL = targetURL[:strings.LastIndex(targetURL, originalExt)] + ext
    } else {
        testURL = targetURL + ext
    }
    req, reqErr := http.NewRequestWithContext(context.Background(), http.MethodGet, testURL, http.NoBody)
    if reqErr != nil { continue }
    req.Header.Set("Referer", "https://realbooru.com/")     // ← 过防盗链
    if h.Cfg.UserAgent != "" { req.Header.Set("User-Agent", h.Cfg.UserAgent) }
    r, doErr := client.Do(req)
    if doErr != nil { continue }
    if r.StatusCode == http.StatusOK { resp = r; body = r.Body; break }
    _ = r.Body.Close()      // ← 关键：失败立即关 body，防资源泄漏
}
if resp == nil { return fiber.NewError(fiber.StatusNotFound, "upstream returned status 404 for all common extensions") }
defer func() { _ = body.Close() }()

c.Set("Content-Type", resp.Header.Get("Content-Type"))
c.Set("Cache-Control", "public, max-age=31536000")   // ← 1 年，让客户端彻底绕过代理
data, err := io.ReadAll(body)
return c.Send(data)
```

**关键设计点（逐条可抄）**：
1. **候选扩展名队列**：`[原扩展, .jpg, .png, .gif, .jpeg, .mp4, .webm]`，去重后顺序探测
2. **URL 构造**：把原扩展名后缀替换成候选（`LastIndex` 定位），而不是简单拼接
3. **Referer 注入**：`https://realbooru.com/`——绕防盗链（我们已有 `ImageHeaderPolicy.kt` 做同类事）
4. **快速失败 + 立即 Close**：非 200 就 `r.Body.Close()`，socket 立即回池，不等 GC
5. **找到就 break**：第一个 200 即停止，不做多余请求
6. **`Cache-Control: public, max-age=31536000`（1 年）**：因为探测很贵，
   让浏览器/CDN 缓存住，**后续加载完全绕过代理和源站** ← 这是省流量的关键

**对我们的直接用途**：
- Gelbooru 有 hotlink 问题（我们代码注释里记着：不带 Referer → 302 到 `gelbooru.com/hotlink.php`）
- yande.re 的 `webm` 条目、Gelbooru 的 sample/original 差异，都存在"扩展名不确定"
- 现有 `GalleryVideoViewer.kt:149` 那条注释正好是这个场景

### 1.3 缓存层（`cache/redis.go`）——极简，可直接照搬思路

```go
var RDB *redis.Client
func InitRedis(addr string) error { ... RDB.Ping(ctx).Err() }

func Get(ctx, key string, dest interface{}) (bool, error) {
    val, err := RDB.Get(ctx, key).Result()
    if err == redis.Nil { return false, nil }      // ← 未命中不是错误
    if err != nil { return false, err }
    if err := json.Unmarshal([]byte(val), dest); err != nil { return false, err }
    return true, nil
}
func Set(ctx, key string, value interface{}, ttl time.Duration) error {
    data, err := json.Marshal(value)
    if err != nil { return err }
    return RDB.Set(ctx, key, data, ttl).Err()
}
func Delete(ctx, key string) error { return RDB.Del(ctx, key).Err() }
```

**关键设计点**：
- `redis.Nil`（未命中）**不当错误返回**——这是最容易写错的地方，写错了会把"没缓存"变成"取数失败"
- 缓存 key 带全参数：`fmt.Sprintf("realbooru:posts:tags=%s:limit=%d:page=%d", url.QueryEscape(tags), limit, page)`
  （`url.QueryEscape` 防止标签里的特殊字符破坏 key）
- 命中时 `c.Locals("source", "CACHE")`，未命中 `"API"`——**来源可观测**，便于排查
- **空列表也缓存**（`len(posts)==0` 时 `posts = []models.Post{}` 然后照常返回），但注意我们这边
  `YandeReClient` 的口径是"空列表当失败"，两者不同，别混

（Android 侧没有 Redis，对应的是 OkHttp Cache + 自建 LRU，见 `VeneraNetworkClient` 的 `httpCacheMaxMb`，
以及 `YandeReClient` 里的 `LruCache` aliasCache。Matoi 的 key 设计思路可直接用。）

### 1.4 yandere provider 的具体写法（可直接对照我们的 YandeReClient）

```go
reqURL, err := p.buildURL(tags, limit, page)   // https://yande.re/post.json?tags=&limit=&page=
// ⚠️ 注释：Yandere uses 1-based page index
q.Set("page", strconv.Itoa(page))
```
- 确认了 **`page` 是 1-based**（我们 `YandeReClient` 也是这么用的，一致）
- 用 `crypto/tls` 的 `InsecureSkipVerify: true`——⚠️ 这是它的取舍，**我们不要抄这个**
- `mapYandereRating`：`explicit/e → e`、`questionable/q → q`、`safe/s → s`，**default 是 "s"**
  （我们 `GalleryPost` 的 rating 归一可以对照检查）
- 标签补全走 **HTML 解析**：`https://yande.re/tag?name=*{query}*` + goquery 找
  `table.highlightable td:nth-child(2) a`，取 href 里 `tags=` 后的部分
  —— 印证了我们 `YandeReClient` 注释里"官方 autocomplete.json 在本站 404"的结论

---

## 二、yande.re-spider（本地缓存/离线优先的样本）

仓库：https://github.com/exa160/yande.re-spider — Python + Vue，"yande.re downloader & local picture manager"

结构要点：
```
backend/src/infrastructure/yande_api.py     ← 站方 API 封装
backend/src/infrastructure/image_cache.py   ← 图片缓存（含超时、清理）
backend/src/infrastructure/download_queue.py ← 下载队列（20KB，最重的一块）
backend/src/dao/yande_data_dao.py           ← 数据落库（14KB）
backend/src/services/gallery.py             ← 19KB，主服务
backend/src/api/v1/tag_cache.py             ← 标签缓存
unit_test/common/test_proxy_mode.py         ← 代理模式测试
```

**对我们的价值**：
1. **它是"本地图片管理器"定位**——先把数据抓到本地库，再看。
   这正好是"被墙环境下唯一能稳定看到内容"的思路：**离线缓存优先**。
2. 有 `image_cache.py` + 专门的 `test_image_cache_cleanup.py` / `test_image_cache_timeout.py`
   —— 缓存的**清理策略和超时**是独立设计的，不是顺手做的。我们的 `GalleryFeedCache.kt`
   / `GalleryForYouCache.kt` 可以对照补这一层。
3. `test_proxy_mode.py` 存在 → 它把"代理模式"作为一等公民配置，不是硬编码。
   我们 `VeneraNetworkClient` 已有 proxy 设置，可对齐成可测试的配置项。
4. `download_queue.py` 20KB —— 下载队列是重头，断点续传/重试/并发都在这里。

⚠️ 注意：它是 Python 后端 + 有服务器，我们是 Android App。
**不能直接搬代码**，要搬的是"缓存 + 队列 + 配置化代理"这个三层结构。

---

## 三、JM / jmcomic（分流探活 + CDN 探测 + 重试）

仓库：https://github.com/hect0x7/JMComic-Crawler-Python（7401 star）

### 3.1 官方文档里的分流策略（`12_domain_strategy.md`）

三种机制，层层递进：

**(1) 静态配置多个域名**
```yaml
client:
  impl: html
  domain:
    html:
      - 18comic.vip
      - 18comic.org
```
Client 会加载上面的域名列表，**第一个失败就自动重试列表里的下一个**。

**(2) 动态获取域名**
```python
domain_list = JmModuleConfig.get_html_domain_all()
JmModuleConfig.DOMAIN_HTML_LIST = domain_list   # 覆盖全局默认
```
`[!NOTE]`：默认走 `get_html_domain_all_via_github`（从 GitHub 拿），
但在无全局代理环境（如 Linux 服务器）下访问 GitHub 不稳，**要手动指定代理**：
```python
JmModuleConfig.get_html_domain_all(
    postman=JmModuleConfig.new_postman(properties={
        'http': 'http://127.0.0.1:7890',
        'https': 'http://127.0.0.1:7890'
    }))
```
⚠️ `[!WARNING]`：`get_html_domain_all_via_github` 依赖 GitHub 仓库，**该仓库不再提供域名**，
未来版本会移除，建议迁移到 `get_html_domain_all`。

**(3) 单个跳转域名**
```python
domain = JmModuleConfig.get_html_domain()
op = JmOption.default()
op.client = op.new_jm_client(domain_list=[domain], impl='html')
```

### 3.2 探活机制（`8_pick_domain.md`）——这是最值得抄的

```python
def test_domain(domain: str):
    client = option.new_jm_client(impl='html', domain_list=[domain], **meta_data)
    status = 'ok'
    try:
        client.get_album_detail('123456')      # ← 用一个已知 ID 做真实探测
    except Exception as e:
        status = str(e.args)
        pass
    domain_status_dict[domain] = status

multi_thread_launcher(
    iter_objs=domain_set,              # ← 并发测所有域名
    apply_each_obj_func=test_domain,
)
for domain, status in domain_status_dict.items():
    print(f'{domain}: {status}')
```

输出示例：
```
获取到7个域名，开始测试
18comic.vip: ok
18comic.org: ok
18comic-palworld.vip: ok
18comic-c.art: ok
jmcomic1.me: ok
jmcomic.me: ok
18comic-palworld.club: ok
```

**关键设计点**：
1. **用真实业务请求探活**（`get_album_detail('123456')`），不是 ping/HEAD
   —— 因为"能连上"≠"能取到数据"（我们实测里 TCP 通但 Cloudflare 403 的 konachan 就是反例）
2. **并发探测**所有候选域名，不是串行
3. **记录每个域名的失败原因**（`str(e.args)`），不只是 ok/fail
   —— 这就对应我们 `HostCircuitBreaker` 该补的东西：**失败原因分类**
4. 探活结果用于**排序/淘汰**，不是一次性决定

### 3.3 重试插件 `advanced_retry`

```yaml
plugins:
  after_init:
    - plugin: advanced_retry     # 启用高级重试插件
      kwargs:
        retry_config:
          retry_rounds: 3              # 单个域名列表循环多少轮
          retry_domain_max_times: 5    # 单个域名最大失败次数（超过则拉黑）
```

**关键设计点**：
- **双层计数**：`retry_rounds`（整体轮数）× `retry_domain_max_times`（单域名上限）
- **超过上限拉黑域名** ← 这就是熔断，和我们 `HostCircuitBreaker` 的
  `FAILURE_THRESHOLD=2 / OPEN_DURATION_MS=60_000` 是同一件事，
  但 JM 是**按域名计数 + 可配置阈值**，我们目前是硬编码的
- 文档明说：配置了 option 后，**Client 循环域名列表时会自动套用这个重试机制**

### 3.4 对我们的改造点（对照现有代码）

| JM 的机制 | 我们现状 | 建议 |
|---|---|---|
| 多域名列表 + 失败切换 | 单域名硬编码（`GallerySite.apiHost`） | 给每个站配**备用域名列表** |
| 真实业务请求探活 | 靠 `HostCircuitBreaker` 被动记录失败 | 补**主动探活**（并发 + 记录原因） |
| `retry_domain_max_times` 拉黑 | `FAILURE_THRESHOLD=2` 硬编码 | 阈值可配，且按域名独立计数 |
| `retry_rounds` 多轮重试 | `retryOnConnectionFailure(false)` | 域名列表循环重试，而非关掉重试 |
| 代理可配置（postman） | `VeneraNetworkClient` 已有 proxy | ✅ 已有，对齐即可 |

---

## 四、Pica（分流 / CDN / 反代逻辑）

⚠️ **诚实说明**：本轮没能直接取到 Pica 官方或权威第三方仓库的源码来核实其分流细节。
搜到的相关项目有 `niuhuan/jenny`（Dart，topics 含 `pica`/`picacg`，1119 star，
2024-04 后未更新）、`tonquer/JMComic-qt`（topics 含 `picacg`）。
**Pica 的具体分流/CDN/反代逻辑待进一步取证，不在本轮结论里假装有。**

已有的确定事实（来自我们自己的代码与实测）：
- 我们的 `picacg.js` 里 `defaultApiUrl = "https://picaapi.picacomic.com"`，注册页 `manhuabika.com`
- Picacg 需要**签名鉴权**（Venera 源脚本里普遍有），不是纯匿名 API
- 实测 `picaapi.picacomic.com` 相关域名不在我们测过的直连列表里，需单独探测

**建议**：若要认真做 Pica，下一步应直接抓包/阅读 jenny 的 Dart 源码来核实
（它的 `pica` topic 说明有实现），本轮不下结论。

---

## 五、综合：落到我们项目上该做什么

按"投入产出比"排序，这四份研究共同指向同一套改造：

### P0 — 统一媒体代理（抄 Matoi 的 Hunt Down）
新建 `MediaProxyResolver`：
- 输入：疑似图片 URL（可能扩展名不对）
- 行为：候选扩展名队列顺序探测 + Referer 注入 + 快速失败 + 找到即停
- 输出：真实可用流 + `Cache-Control: max-age=31536000`
- 直接解决：Gelbooru hotlink 302、yande 的 webm/动图、sample vs original 不确定

### P1 — 多域名 + 探活 + 可配熔断（抄 JM）
- `GallerySite` 每个站配 `hosts: List<String>`（主 + 备），而非单个 `apiHost`
- 新增 `HostProbe`：并发对候选域名做**真实业务请求**，记录失败原因分类
- `HostCircuitBreaker` 阈值从硬编码改为可配，且按域名独立计数 + 超阈值拉黑
- 复用我们已有的"按 hostname 封锁有漏网"发现（safebooru/donmai 同族可互备）

### P2 — 缓存策略（抄 Matoi cache + yande-spider image_cache）
- 未命中**不当错误**（对齐 Matoi 的 `redis.Nil` 处理）
- 缓存 key 带全参数 + `QueryEscape`
- 补**清理策略与超时**（yande-spider 有独立测试覆盖，我们没有）
- 空结果的缓存口径要写明（Matoi 缓存空，我们 `YandeReClient` 空=失败，两者不同）

### P3 — 统一 Booru 抽象（抄 Matoi 的 handler/provider 分层）
- 我们已有 `GallerySite` + 每站 Client，与 Matoi 同构
- 可考虑把 `when(site)` 的 10 处分派收敛成一张 provider 注册表

---

## 六、遗留 / 下一步

- **Pica 的分流/CDN/反代细节未取证** —— 需读 `niuhuan/jenny` 的 Dart 源码或抓包
- e621 的强制 User-Agent 规则未确认（e926 可用，e621 待单独验）
- JM 的 `get_html_domain_all()` 具体从哪里拉域名（文档说 GitHub 仓库不再提供，需看新实现）
- Matoi 用了 `InsecureSkipVerify: true`，我们**不要抄**
