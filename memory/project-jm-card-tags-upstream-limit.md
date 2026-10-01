---
name: project-jm-card-tags-upstream-limit
description: 禁漫天堂(jm) 卡片只有 1-2 个分类词是上游脚本+接口的天花板，非迁移缺陷；description 真机抽样为空，话题抽取已判死，别再重开这条调查
metadata:
  type: project
---

禁漫天堂（key `jm`）**搜索/列表卡片上只有 1-2 枚"标签"，且它们是分类词不是标签**。这是上游限制，不是 Compose 端口丢逻辑：

- 设备上运行的官方 `files/comic_source/jm.js` 与仓库 `assets/sources/jm.js` **逐字一致**（2026-09-19 diff 过，差异仅我们自加的崩溃守卫）；官方 `parseComic` 本身就只 push `category.title` + `category_sub.title`，硬上限 2。
- 真标签只存在于**详情页**（`/album?id=` → `data.tags` → 分组键 `Tag`），卡片拿不到。用户 2026-09-22 拿详情页截图（某本标着 `AI繪圖`）确认过这条通道是通的：Kotlin 侧 `ComicDetails.tagMap` → `plainTags` 能吃到。
- **已实测定论（2026-09-22 探针）**：jm 列表/分类/搜索接口的 album 对象字段就是
  `id, author, description, name, image, category, category_sub, liked, is_favorite, update_at, adddate`
  —— **没有 tags 字段**。所以卡片级标签判据在 jm 上是结构性不可能，不是我们实现漏了；
  master 的 AI 角标读同一个字段，同样不可能。探针做法见 [[project-js-source-assets-not-live]]，
  设备副本已按 md5 还原成与仓库逐字一致。
- 真机 Kotlin 探针抽样：`descLen=0`、`descHasHash=false` —— 该作品 `description` 为空（详情页也显示"暂无详细简介"），所以**"从 description 抽 `#话题` 补卡片标签"这条路判死**。
- 唯一还能**增加卡片标签**的办法是逐条拉详情 = 一页 80 条 80 个请求，已明确否决。
- 但"否决逐条拉详情"只针对**卡片显示**。若目的换成**屏蔽**，还有一条不需要额外请求的路：详情加载后用真标签判一次、命中就把 `(源键, 作品号)` 记进小表，之后所有列表按这张表剔除（看过即拉黑）。见 [[project-content-guard-ai-blocking]]。

**Why:** 用户连续三轮追问"禁漫天堂卡片 tag 太少/显示有问题"，容易让人以为是我们改坏了或还能修。实测下来它是站点接口 + 官方脚本的天花板，继续投入只会浪费轮次。

**How to apply:** 再遇到"某源卡片标签少"的反馈，先按 [[project-js-source-assets-not-live]] 的办法把设备副本与仓库 diff 定性（端口丢失 vs 上游限制），再用一次性 Kotlin 文件探针取真实字段，**不要凭猜测改源脚本**。已确认的 JM 详情页显示缺陷走 UI 层收口（英文组名 `Author/Work/View` 字典查不到就原样显示；纯数值组如 `View: 3132` 保留展示但取消点击），不改源数据。
