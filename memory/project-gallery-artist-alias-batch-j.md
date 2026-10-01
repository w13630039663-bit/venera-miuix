---
name: project-gallery-artist-alias-batch-j
description: 批次 J（画廊画师别名解析）已定方案未开工，且用户明确排在「画廊首页重构」之后；触发判据是「站方给 0 行才解析、拿不到站内明确关联就保持 0」
metadata:
  type: project
---

2026-09-30：外部提案要求「把 Danbooru 当画师跨站身份枢纽 + 建 GalleryArtist/GalleryArtistSourceMapping 两张表」。
实测把它的核心样例推翻后（见 [[project-gallery-data-ceilings]] 的 2026-09-30 那一节），方案被缩成一条判据，
落在 `gallery-artist-alias-2026-09.md`（仓库根目录，与其余 gallery-*-2026-09.md 同名单列）。**未动代码。**

**Why:** 用户 2026-09-30 定的原话是「在明确的 Artist 搜索场景中，某站原始查询返回 0 张时，查询该站 Artist 元数据，
尝试解析 canonical name / aliases / source-specific identifier；**只有获得明确站内关联时才重搜，否则保持 0 结果**」。
"明确站内关联"= 那一站自己的画师记录指针（yande 的 `alias_id`），**不接受**名字相似度、不接受随包词典、不接受第三方站。
排期上用户说要先做**画廊首页重构**，所以这条压在后面。

**How to apply:**
- **开工前必须回核行号**：接线点在 `GallerySearchViewModel.kt` 的落地那一支（onSuccess → appendPage/saveHistory）
  与空态区（`GalleryScreen.kt` 的 `buildGalleryWall` / 空态 `listOfNotNull`）。**首页重构一旦改动这两处，文档 §3.4/§3.5 的挂点作废重来。**
- 判据的六项里最要紧的是 **`searchError == null`**：熔断/超时不许触发解析（否则给已熔断主机再压请求）；
  以及**「非 0 张绝不替换」**——`a-10` 换成正名会从 12 张掉到 1 张，这条是红线。
- 这一轮**只做 yande.re**，Gelbooru 保持 0 张、界面不装出"两站都会换名"（假开关零容忍）。
- 历史与胶囊要仍存**用户原词**，只把发出去的 query 换正名（现仓库只有排行伪标签一处这种形态，需另开一条）。
- 不建表、不扩充词典、不请回 Danbooru、不做图像/作品反推（`sourceKey` 两站交集实测为 0）。
