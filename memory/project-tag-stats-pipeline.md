---
name: project-tag-stats-pipeline
description: 题材统计与推荐的数据面：读侧只按 TAG_SEPARATOR 切，但写侧存量行仍带着旧切分器劈出的假题材（"Fate/Grand Order"→fategrand+order），当年"不做迁移"的前提已失效
metadata:
  type: project
---

**存量 `reading_stats.tags` 里有被旧切分器劈坏的假题材，读侧修好了也不会自愈。**

2026-09-22 把模拟器的 `venera_core.db` 用 `run-as cat` 拉下来、用 node 内置的 `node:sqlite` 直接跑 SQL 看到：
同一本书的 tags 串里同时存着 `Tag:fategrand` 与 `Tag:order` —— 原标签是 `Fate/Grand Order`，
先被去掉 `/` 再按空格劈成两半。用户侧的表现是首页推荐说明行明晃晃写着
「按你最近 30 天读得最多的题材：fategrand · order · ねこてる」。

**Why:** 劈标签的 bug 修的是**读侧**（`ReadingStatsManager` 现在只按 `TAG_SEPARATOR` 切，
且去掉了 `length in 2..10` 那个会把 `female:lolicon` 整条丢掉的闸门），
但**写侧留下的行是坏数据，不会自愈**。而 `docs/rounds/tag-statistics-port-2026-09.md` 当年写「不做数据迁移」的理由是
"tags 列此前没有任何写入者，存量行恒为空串" —— 这个前提现在不成立了。
凡是"修了管线但没迁移存量"的决定，都要在数据真的开始积累之后重审一次。

**How to apply:**
- 再看到"某个题材桶名字很怪/像被截断"，先怀疑存量行而不是当前判据；判据类问题先看读侧是否已修。
- 推荐取数按题材桶各拉一页再 round-robin 去重，所以假桶会真的把不相关的本子推进推荐区。
- 读设备数据这条链是现成的：`adb exec-out "run-as <pkg> cat /data/data/<pkg>/databases/venera_core.db"` → `node:sqlite` 打开。
  表有 `comic_history` / `comic_favorite` / `reading_stats` / `content_guard_rules` / `comic_source` / `favorite_images`。
  设备侧没有 `sqlite3` 二进制，别去试。
- 顺带记一条同源的显示坑：`HomeScreen.kt` 的推荐网格 `RecommendGridRow` 里副标题是
  `if (comic.subTitle.isNotBlank())` 才渲染，缺副标题的那张就矮一行，一行五张卡底边参差。

相关：[[project-home-recommend]]、[[project-jm-card-tags-upstream-limit]]、[[feedback-verify-capability-claims]]
