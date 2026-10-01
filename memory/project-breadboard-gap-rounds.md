---
name: project-breadboard-gap-rounds
description: 2026-09-28 对照 Breadboard 的全面核查结论：排好的 4 轮清单、明确不做的六项及其理由、以及核查中当场被推翻的两条错报（信息面板屏蔽早已存在、yande.re 列表无总数）
metadata:
  type: project
---

用户点名以 [breadboardapp/breadboard](https://github.com/breadboardapp/breadboard) 为参照做"全面核查"。
方法：三路 Explore（画廊能力盘点 / 全仓文档遗留清单**并逐条回代码验** / 读 Breadboard 源码而非 README）
+ 一个 Plan 代理压排序。结论按"屏幕上正在说错话 > 假按钮与断线 > 新功能"排序成 4 轮。

**第 1 轮（已落地 2026-09-28，六笔，记在 `docs/rounds/round1-false-readings-2026-09.md`）**：双页翻页不动 /
Gelbooru「0 MB」假读数 / JPEG 页导入后从书架消失 / 半成品章节当完整离线章 / 过盾 cookie 取证探针。

**第 2 轮（假按钮与断线，未开工）**：大图页菜单"已屏蔽态"（现在已屏蔽仍写「屏蔽该标签」，点了没反应）/
`GalleryFavoritesStore.notice` 没人 collect / `contains()` 零调用 /
`deleteJsSource`·`resetRepoUrl` 没有入口 / `invertSelection` / `onRead`·`editTags` /
`autoCropBorders` + 整个 `BitmapSliceHelper`（假开关，接上或删干净二选一）。

**第 3 轮（Breadboard 派生，未开工）**：画廊下拉刷新（**必须复用 `retryFromUser()`** —— 它含清熔断 + force，
自己另发一笔会撞 60s 熔断变假手势）/ 回到顶部 / Gelbooru 命中总数读数 / 备份合并与版本防倒灌 /
ACTION_SEND 图片直达反搜。

**第 4 轮（要产品决策）**：NSFW 第四态 `blurReveal`（设置页文案自己承认"尚未实现"）/
应用锁（新依赖 androidx.biometric）/ JS 弹窗协议（`showInputDialog` 恒 null，解锁面最大但成本 L）。

## 明确**不做**的六项（下次有人提"照 Breadboard 加"直接引这段）

- **标签分类权重与分类分桶**：post JSON 无分类字段，要付"候选池 × 1 笔 tag 端点请求"。
- **关注画师 / Following 页**：它的 Following 流靠**自家服务端** `breadboard.moe/api/v1/following/posts`，我们没有后端。
- **第二套分级 / 年龄门**：隔离方案 §二 要求画廊只读共享漫画侧那一份分级模式，再加开关就是两套语义分叉。
- **评论 / 笔记 / 投票 / pool 浏览 / 多站抽象 / 第三个站**：隔离方案 §八 逐条主动不做。
- **省流量档 DataSaver**：它做的是 sample↔file，而我们列表最小档就是 300/350px 缩略图，没有更小的可省。
- **推荐参数滑杆**：yande.re 池子恒 40 条，滑杆推不动输出 = 假控件。

## 核查中当场被推翻的两条错报（别照旧文档去"修"）

1. **「信息面板长按标签→屏蔽」早就有**（`GalleryInfoSheet` 三项菜单 搜索/复制/屏蔽，已屏蔽时 Toast 说明）。
   真缺口只是"已屏蔽态仍提供屏蔽项"这一枚假按钮。
2. **「搜索结果总数」不是两站都能做**：Gelbooru 的 `@attributes.count` 实测有，
   而 **yande.re 的列表 JSON 是裸数组**（直接 `decodeFromString<List<Dto>>`），根本没有总数
   —— 与 [[project-source-data-ceilings]] 那条"0/33 漫画源提供搜索总数"同形。

**Why:** 这两条都是我自己先写成"缺口"、被 Plan 代理/复核推翻的。全仓审计里"文档说没做但其实做了"
与"文档说做了其实没做"同频出现（见 [[project-multi-ai-regression-triage]]、[[project-implemented-but-unwired]]）。

**How to apply:**
- 动手前先量四件事：过盾 harvest 到的 cookie 名字与过盾后请求的 HTTP code / Gelbooru `count`
  在第 2 页与 `limit=100` 下是否仍是命中总数 / 现存离线章有多少没有完成标记（决定迁移语义）/
  推荐池子滑杆在恒 40 条下能否改变输出。
- 一个组件被 N 处共用时（分段控制器 4 个调用点、扩展名白名单 4 份拷贝），**改一次到位**并让判据单点化，
  别只改用户点名的那一处 —— 用户 2026-09-28 的裁决就是这个。

相关：[[project-gallery-for-you-plan]]、[[project-gallery-data-ceilings]]、
[[project-segmented-pills-style]]、[[project-saucenao-cloudflare-block]]
