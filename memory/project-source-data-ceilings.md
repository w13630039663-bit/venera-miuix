---
name: project-source-data-ceilings
description: 实测出来的两处多源数据天花板 —— 搜索总数没有任何源提供、详情页预览图其实是整页原图（onThumbnailLoad 已接线但它不换小图）；再提相关功能前先推翻这些事实
metadata:
  type: project
---

2026-09-19 用脚本扫全部 33 个源脚本 + Kotlin 解析层实测出的两条硬约束。**再提相关功能前先想办法推翻它们，否则就是重复调查。**

## 1. 「一个源的全部检索结果数」拿不到

用户想把搜索页表头改成「已加载 N 个，总 M 个」。实测：

| 搜索 `search` 返回形态 | 源数 | 能推出什么 |
|---|---|---|
| 直接返回 `total` | **0** | —— 没有任何一个源交出总数 |
| 返回 `{comics, maxPage}` | **26** | 页数精确；条数只能 `maxPage × 首页条数` 估算，**只会高估**（jm 是 `ceil(total/80)×80`，误差最多 +79） |
| 两者都无 | **7** | ccc / ehentai / ikmmh / lanraragi / manwaba / mh1234 / **nhentai** —— 游标型源协议上没有"总数"概念 |

- `JsComicSource.search()`（`:369-377`）只读了 `next`，**`maxPage` 在解析层就被丢了**；接口是 `Result<List<Comic>>` 装不下它。
- 要透出 maxPage 需改 6 个文件、跨 Source + Data Model + ViewModel 三个保护域：`ComicSource:35` 接口 + Js/Baozi/CopyManga/MangaDex 四个实现 + `ComicSourceManager:810` + `SearchViewModel`。
- 想要**精确**条数只能改源脚本多返回 `total`，但只有上游 API 本身给 total 的源才行（jm 给 `data.total`），且见 [[project-js-source-assets-not-live]] —— 改 `assets` 脚本对设备无效。
- 「真的把所有页拉下来」技术上可行但是爬虫行为：jm 一页 80 条 × maxPage 几十页 × 33 源并发 = 每搜一次几百个请求，会撞限流/封号，也会把刚做的行级虚拟化压穿。原版 Venera 没这么干。

**How to apply:** 涉及"总数/全部结果"的需求，默认答案是给不了精确值；可写的诚实口径只有「已加载 N · 约 M」「已加载 N · 共 M 页」两种，且 7 个源只能显示"未知"。别为了表头好看编一个数。

## 2. 详情页预览格下载的是整页原图，没有低清档

用户想要「先加载一张极其模糊、体积很小的缩略图 → 骨架 → 真图淡入」。实测：

- `picacg.js` 的 `ComicDetails` **没有 `thumbnails` 字段**（只有 cover/chapters/maxPage），所以 `ComicDetailViewModel:536-560` 回退到 `fallbackChapterPages` = **阅读器整页原图 URL**。预览格吃的就是原图。
- 源协议里专为小图准备的 `onThumbnailLoad` 钩子：2026-09-19 当时 Kotlin 侧**零调用点**，
  **现已接线**（`ComicDetailViewModel.ensureThumbnailConfigs` + `ResolvedThumbnailConfig`）——
  但接完之后代码注释里记的实测结论是它**只回 url + 防盗头，不换小图**，所以本节结论不变：
  **没有低清档**，blur-up 仍做不了。
  （教训：这条从"没接线"变成了"接了也没用"，两种状态下"能不能做 blur-up"的答案是一样的，
  但**修法完全不同** —— 别拿旧记忆里的"零调用点"去解释今天的行为。）
- Coil 的 `size()` 只减解码尺寸**不减网络字节**，所以对同一 URL 做"先小后大"是纯浪费。

**How to apply:** 页级 blur-up 在接上 `onThumbnailLoad` 并让各源真提供小图 URL 之前做不了（Source 层改动，要独立立项）。当时我用"封面极低清解码放大"做了**作品级**近似替代，用户看过后要求删掉、回到「固定灰底 + 流光骨架 + 200ms 淡入」—— 参见 [[feedback-design-review-then-code]] 那条"别把替代方案直接实现进去"。

相关：[[venera-ui-refactor-authoritative-docs]]、[[project-jm-card-tags-upstream-limit]]
