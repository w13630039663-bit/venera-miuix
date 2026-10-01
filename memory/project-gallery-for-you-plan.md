---
name: project-gallery-for-you-plan
description: 画廊「按收藏推荐」(For You) 的机制与落地状态：判据层+搜索卡 chips（09-27）与主 Tab 双页化（09-28 每日推荐/猜你喜欢）都已落地；语料实测=5 张，真正的病是"复读页"不是空页
metadata:
  type: project
---

2026-09-27 出方案 `gallery-recommendations-from-favourites-2026-09.md`（判据层 + chips）；
2026-09-28 出方案 `gallery-two-page-for-you-2026-09.md` 并落地**主 Tab 双页化**。
参考实现 = [breadboardapp/breadboard](https://github.com/breadboardapp/breadboard)。它的"For You"
**不推荐图，推荐标签**：本地收藏算词频 → 前 7 名当候选池 → 加权随机抽 1 枚主标签 → 其余按
"与已选在同一张收藏图里共现"继续抽满 3 枚 → 拼成一次普通站方搜索。没有服务器、模型、向量。

**Why:** 这个结论把"要不要上推荐系统"的成本问题直接消掉了 —— 接的是现成的搜索链路
（`GallerySearch.queryOf` / 两站 `searchPosts` / `isExhausted`），不是新造基础设施。

## 语料实测（2026-09-28，PJZ110，`run-as cat files/gallery_favorites.json`）

**5 张**（yandere 3 / gelbooru 2），全部带 tags，123 个去重标签里 **115 个只出现 1 次**。
把真实判据跑在这批语料上：yandere 池满 7 枚、池内 19/21 对共现 >0（最大共现 2 张）能抽满 3 枚；
gelbooru 两图词频全 1、仅 9/21 对共现 >0（最大共现 1 张）→ 主标签之外的候选**只剩同一张图自己的其余标签**。

**所以当初担心的"上线即空页"不是病，真正的病是"复读页"**：抽出的标签串就是收藏那几张的标签子集，
一搜把自己顶回来，读起来像坏了。这条实测直接改变了设计（"排掉已收藏"从可选优化变成第一性需求）。
**教训：语料/数据面的数没量到之前不要定形态，量到之后形态往往会变。**

## 已落地（2026-09-28，六笔，见 `gallery-two-page-for-you-2026-09.md` §十三）

- `GalleryForYouMerge`（分页合并判据层，13 例单测）+ `GalleryForYouViewModel` + 主 Tab 两页 pager。
- 四条用户拍板：横滑 pager + 顶栏分段行都做 / 「换一批」**只换当前页**（For You 自持 seed，两页不联动）/
  排掉已收藏并在页尾报数 / 冷启动固定落每日推荐、推荐页首次可见才取数。
- 三条我按既有裁决定死的：**两站混合**（只按一站=静默降级）、**返回直接关画廊**不做"先回第 0 页"、
  空态**四档不同脸**（NoSeeds / NoUsableTags / 取数失败 / **全被"已收藏"剔空**）。
- 缺席的站**不算到底**（超时/没账号下一轮还要重新问它）；"到底"的判据是"这一轮发得出去的站都到底"。
- 顺带修掉一处错牵：chips 从前读 `vm.seed`，于是"换一批日榜"会把推荐标签一起换掉 —— 现在读 For You 那颗。

## 仍然没做

- 标签分类权重（post JSON 无分类字段，见 [[project-gallery-data-ceilings]]）；
- 真机六条验收（尤其"排除收藏后每页实际几张"——语料 5 张可能一页即到底）；
- Breadboard 的 Following（关注画师流）明确**不做**：它靠自家服务端聚合，我们没有后端。

相关：[[project-gallery-data-ceilings]]、[[project-gallery-module-isolation]]、
[[project-breadboard-gap-rounds]]、[[project-favorites-secondary-row-in-chrome]]、
[[feedback-degrade-paths-must-fail-loud]]、[[project-home-recommend]]
