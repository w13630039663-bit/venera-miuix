---
name: project-content-guard-ai-blocking
description: 「屏蔽 AI 生成漫画」与分级守卫的既有裁决：AI汉化/翻译刻意不屏蔽；master 角标读同一字段故 jm 卡片受限；开关真实状态直接读 venera_guard_prefs.xml
metadata:
  type: project
---

2026-09-22 落地「屏蔽所有源的 AI 生成漫画」后，用户拿截图追问「还是有啊，你确定屏蔽和分级可以用吗」，逐源审计出的结论（权威文档：仓库根 `block-ai-comics-2026-09.md` §十）：

**判据的刻意边界（用户已认可，别再"顺手放宽"）**
- 命中面 = 标签整串等值（`ai` / `ai-generated` / `ai生成` / `ai绘图`，繁转简后）**或** 标题词组（`[AI Generated]`、`AI Art`、`AI生成/繪圖`、括号独立词 `(AI)`/`【AI】`）。
- **`AI汉化` / `AI翻譯` 刻意不命中**：那两个词标的是翻译工具链，不是画面。收进来会屏蔽整批人工作画只是 AI 嵌字的正常漫画 —— 用户对误杀零容忍。单测 `aiTranslationMarkersDoNotHit` 钉死。
- 也不匹配裸 `ai` 字样（`AI少女`、`openai`、`waiting`）。

**"屏蔽所有源"的真实天花板**：标签判据吃的是**卡片**的 tags。master 的 AI 角标读的是**同一个字段**（`comic.dart:485-487` `comic.tags`），所以 master 在 jm 卡片上同样不显示角标 —— 这是上游数据面，不是移植丢逻辑（详见 [[project-jm-card-tags-upstream-limit]]）。卡片带真内容标签的源只有 ehentai / manga_dex / copy_manga / hot_manga / picacg / comic_walker / hcomic / manhuaren / happy 这一批。

**定性"开关到底开没开"的客观读数（比观感快）**：
`adb -s emulator-5554 shell run-as com.github.w13630039663bit.venera.miuix cat /data/data/.../shared_prefs/venera_guard_prefs.xml`
→ 直接看到 `nsfw_mode` 与 `block_ai`。那次实测 `nsfw_mode=OFF`（所以 jm 封面不打码是设定如此，不是坏了）+ `block_ai=true`。
`filterComicModels` 另有一行 `VeneraGuard` 逐源剔除日志（只记源键与条数，不落标题）。

**仍未拍板（2026-09-22 挂起）**：① 拨开关对已加载列表不即时生效（要动 3 个 ViewModel 保护域）；② 详情标签指纹表（看过即拉黑，零额外请求）—— 探针已回，**列表接口确实不返回 tags**（见 [[project-jm-card-tags-upstream-limit]]），所以"给源脚本 parseComic 补 tags"这条路直接排除，只剩指纹表；③ 单条解除出口缺失（master 的 `_maskMenuEntries` 没移植），判据越激进越需要它，见下。

**2026-09-22 `VeneraGuard` 日志的实测剔除量（`mode=OFF ai=true`）**：jm 剔 43/80、46/80、5/80，copy_manga 1/30，**ehentai 25/25 全灭**。25/25 有两种解释且现有日志不足以判别：用户当时就在搜 "ai generated"（正确），或标题判据在 e-hentai 标题形态上过度命中（误杀一整页）。下一步是把**命中的子串**（不是整条标题）加进日志，用户搜一次即可定性；最可疑的是 `ai[\s\-_]?(作画|作畫)` —— 罗马字人名以 ai 结尾 + 「作画」是常见标题形态。历史表 5 条 jm 标题实测 0 命中，样本太小不能当结论。

**源级预设表实读**：33 源 = nsfw 9（jm, picacg, mh18, hcomic, wnacg, nhentai, hitomi, hot_manga, jcomic）+ mixed 2（ehentai, mxs）+ safe 22。

**仍未拍板（2026-09-22 挂起）**：① 拨开关对已加载列表不即时生效（要动 3 个 ViewModel 保护域）；② 若要让 jm 这类"卡片只有分类词"的源也能屏蔽，两条路线 —— 详情标签指纹表（看过即拉黑，零额外请求）vs 给源脚本 `parseComic` 补 tags（取决于列表接口到底返不返回 tags，探针已下、结论未回）。
相关：[[project-source-data-ceilings]]、[[feedback-verify-capability-claims]]
