# 画廊「以图搜图」接入 SauceNAO —— 设计与实测记录（2026-09-27）

参照实现：[breadboardapp/breadboard](https://github.com/breadboardapp/breadboard) 的
`saucenao/` 一包（`SauceNao.kt` 266 行 / `ReverseSearchScreen.kt` 354 行 /
`SauceNaoResultsScreen.kt` 563 行）。本文只记**我们这边的判断与实测数字**，
不重述它的代码。

---

## 一、需求落地形态

| 需求原话 | 落地 |
|---|---|
| 在画廊搜索的**搜索源的右边**加一个以图搜图按钮 | `GallerySearchArea.kt` 站点分段器那一行改成 `Row { VeneraSegmentedButton(weight 1f) ; Icon(ImageSearch) }` |
| 点击后**整个搜索框自然变化**成 saucenao 的效果 | `rvm.open` 时搜索卡**套同一个壳**（同一个 `cardModifier`，含内边距与高度上报），里面整片换成反搜那一具身体 |
| 接入 SauceNAO | `gallery/data/SauceNao.kt`（解析 + 客户端）+ `SauceNaoAccount.kt`（Key） |

结果摆在**墙那一片**，不新造一屏。

## 二、与参照实现的四处**刻意不同**

1. **结果不丢"没有作者"的命中。** Breadboard 在 `SauceNao.kt` 里对没有作者名的条目 `continue`
   掉（它那一屏是"按图找画师"）。我们这一屏是"按图找这张图"，一条带 `yandere_id`
   的命中即使没作者也照样能点开、能收藏 —— 照抄会把最值钱的那类结果筛掉。
2. **命中优先映射回我们自己的两站。** SauceNAO 的 `data` 带 `yandere_id` / `gelbooru_id`，
   对上的那一行给「在 yande.re 打开」，走与一级点卡片**同一条**路（跨 Activity 才有
   实时 blur-behind 与预测式返回）。对不上（pixiv / Twitter / 番剧）才退化成外链。
   Breadboard 没有这一层，因为它的大图页与列表不同源。
3. **结果用列表而不是瀑布流。** 命中大半不是我们那两站的图，没有宽高比也没有分级字段，
   塞进瀑布流只会得到一排等高方块；而这一屏真正要读的是**相似度**与**作者**。
4. **没配 Key 时直接不发**（见 §四，这一条是被实测逼出来的，与 Breadboard 同归）。

## 三、解析层为什么能锁住

`parseSauceNao()` 是纯函数，且**刻意走 kotlinx 的动态 JSON 树而不是 `org.json`** ——
后者在 Android 上是 stub，纯 JVM 单测里一抛 "not mocked" 就整层锁不住。
`SauceNaoParsingTest` 11 条用例锁的全是**站方的不规则之处**：

- `similarity` 是字符串数字（`"95.29"` → 95，向下取整）；
- 作者名有四个候选键 `member_name → author_name → creator → twitter_user_handle`，
  且值可能是**数组的字面量串** `"[\"d\"]"`；
- `status = -2`（"没找到"）是**一次有效回答**，不抛错、交回空页；其余非零状态一律抛，
  且错误文案优先用站方给的 `message`；
- `id` 字段 `0` 表示"没有"，不是"第 0 条"；`gelbooru_id` 有时是字符串数字；
- 同一字段给过字符串、数字、`null`，也给过整个对象 —— 三个取值器一律 `runCatching`，
  一条命中形状不对的代价应该是这一格空着，而不是整次搜索在解析中途炸。

## 四、实测卡点：Cloudflare（**未解，需要一次判断**）

### 现象

真机第一次走通全流程（`uiautomator` 采样 r01–r06 量到输入形态、预览、失败态全部就位），
但请求回 **403**。本机直连复现三种 UA 全部：

```
status=403  server=cloudflare  body: <!DOCTYPE html>...<title>Just a moment...</title>
```

### 第一轮修法与其后果

画廊 API 流量从前统一打 `NoInteractiveBypassTag`（豁免交互式过盾，2026-09-26 为修
"永久转圈"而设）。SauceNAO 这一路**去掉该标**后，`CloudflareBypassInterceptor` 的判定
（`code in {403,503}` + `server: cloudflare` + body 含 `Just a moment...`）**三条全中**，
过盾确实开始跑：

```
22:32:29.982  Saved 1 cookies from WebView for saucenao.com (includes cf_clearance)
22:32:29.990  Bypass successful! Retrying request for https://saucenao.com/search.php
22:32:36.235  Cloudflare challenge detected on: https://saucenao.com/search.php   ← 重试仍被挡
```

即：**过盾成功、cookie 存下、重试发出，然后仍然 403。**

### 两个候选成因（这就是要请对方拍板的地方）

| # | 成因 | 判据 | 修法所在 |
|---|---|---|---|
| A | 匿名请求本就被 SauceNAO 挡在盾外，带 `api_key` 才放行 | Breadboard 的 `ReverseSearchScreen.onSearch()` 在 `saucenaoApiKey.isEmpty()` 时**直接拒绝发起**并跳设置页 —— 作者实测过匿名不可用，与我们同一条现象 | 已按这一条改：没 Key 不发，并写明去哪配 |
| B | `CloudflareBypassManager` 只回收了 **1 枚** cookie（日志原话），而 Cloudflare 正常同时下发 `cf_clearance` **与 `__cf_bm`**；缺后者则重试必被再挡 | 日志里那句 `Saved 1 cookies`；且 22:32:36 那次从"启动过盾页"到 `onBypassSuccess` 只花 **120ms**，不可能是人解开的 | 改 `data/network/CloudflareBypassManager.kt` —— **共享网络设施，属保护域，未授权不动** |

A 与 B **不互斥**：很可能是"匿名必挡（A）+ 即使过盾也只带半套 cookie（B）"。
现在按 A 收了口，B 留着没碰。

### 为什么这条要问而不是自己改

B 那一改动的是全应用共用的过盾实现，漫画侧所有源都走它。
把它的 cookie 回收范围放宽，代价由那 33 个源一起承担。

## 五、UI 审核结论

**已量到并判定合理的**（PJZ110，1080×2376 / 420dpi）：

| 元素 | bounds | 判读 |
|---|---|---|
| 输入行图标 | `[922,347][1048,453]` | 40dp 见方，落在行内 |
| 隐私那句 | `[32,453][1048,490]` | 一行，未截断 |
| 两颗动作钮 | 文字 537..580 | 等宽、居中 |
| 预览缩略图 | `[74,638][242,806]` = 64dp | 与容器同尺寸 |
| 失败态 | 标题 1546 / 正文 1606 / 再试一次 1727 | 居中在剩余区域 |

**审出来并已修的 8 条**：LazyColumn 重复 key 崩溃风险、上传体积无上限（OOM 面）、
加载/失败/空态三框漏挂 `nestedScroll`、预览图宽屏尺寸打架、设置页草稿不跨重组、
"只回前 20 条"没说明、失败态标题与"没有相似图"混为一谈、403 文案不可操作。

**审出来但**没动**的一条（形态设计，等拍板）**：
反搜那一具身体**不随滚动收成一条** —— `searchCollapsed` 的判据含 `svm.mode == RESULTS`，
而反搜走的是 `rvm.open` 早退分支。后果：往下翻结果时那 ~200dp 一直占着，
与画廊"顶栏每一行都在抢内容高度"的既有裁决相冲。
两条出路：给反搜也做一态收成一条；或让它随 `searchCollapsed` 整条收走 + 在 actions 里
给一枚回得来的钮。

## 六、验证边界（不假装）

- **能锁**：解析层 11 条 JVM 单测（全绿）。
- **锁不到**：真机布局、AnimatedVisibility 时序、Cloudflare 是否放行 —— 只能真机验。
- **至今未拿到**：一次**成功的**反搜结果屏。结果列表那段 UI 从未在真机上被看见过。
