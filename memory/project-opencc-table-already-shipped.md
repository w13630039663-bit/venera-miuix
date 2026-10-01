---
name: project-opencc-table-already-shipped
description: 简繁转换表一直在仓库里 —— app/src/main/assets/opencc.txt（3980 对官方 OpenCC 字级映射）；2026-09-20 已由 ChineseVariantConverter 接上并喂给题材归一化与搜索标签匹配，实测归一效力 84.5% 且那是该表天花板
metadata:
  type: project
---

**事实**：`app/src/main/assets/opencc.txt` 存在于 `compose-migration` 分支，29,472 字节 / 3980 对，文件头标明源为 `github.com/BYVoid/OpenCC`。**2026-09-20 之前**它随 APK 打包却 Kotlin 零引用（`git grep -i opencc compose-migration -- app/src` 只命中自身注释），是个从 Flutter 侧照搬资源清单时带来的死资产；现已由 `data/tags/ChineseVariantConverter.kt` 加载（构造即异步自加载 + `ready: StateFlow`），消费者是 `data/tags/TagNormalizer.kt`（题材统计合桶）与 `feature/TagSearchPolicy.normalizeTagValue`（搜索页客户端标签过滤的第四级）。

**Why:** 2026-09-19 做多标签搜索排查时，我从 `tags.json` / `tags_tw.json` 双字典**挖**字级对照，只得到 345 字 / 370 映射，于是判定「没有 OpenCC 那层归一，繁简互不认」，把繁简方案（Option A）当作「需要新增资产依赖」延后。那次漏看了仓库自己的 assets 目录 —— 表已经在 APK 里，缺的只是一个约 40 行的加载器。`master:lib/utils/opencc.dart` 全部 66 行就是「读 asset + 建 Map + 逐 rune 替换」，零算法。

**实测覆盖率（2026-09-20 跑脚本）**：t2s 方向 3952 个唯一繁体键；`靈夢→灵梦`、`蘿莉→萝莉`、`遊戲→游戏` 整词正确。对六类题材字典（8734 条）中简繁译名不同的 756 条，字级转换可归一 **639 条（84.5%）**；残留 117 条多为**词级**差异（`太陽眼鏡` vs `太阳镜`，字表原理上修不了）。系统性缺口：表里有 `发→發` 但**没有** `髮→发`（多繁对一简丢了 28 个字），头发义标签（`超長髮`、`抓頭髮`、`髮交`）转不过来。

**How to apply:**
- 任何涉及**简繁**的需求（题材合桶、`TagSearchPolicy.normalizeTagValue` 的客户端标签匹配、标签显示归一），这一级**已经存在**，直接复用 `ChineseVariantConverter` / `TagNormalizer`，别再新写一份；也不要再引用「本仓库无转换表」这个旧结论（`docs/rounds/venera-tag-multilang-plan.md` §4.1 与旧方案文档里那句都已过时）。引用前先 `ls app/src/main/assets/`。
- **84.5% 是天花板不是过渡状态**：已逐字节比对 `master:assets/opencc.txt`、`.reference/flutter-master/assets/opencc.txt` 与本分支三份，pair 数与唯一繁体键完全一致（看着字节数不同只是行尾符差异），所以**没有"换一份更全的表"这条路**；残留要么靠字典（`tags.json` 六类 8734 条），要么是词级差异原理上修不了。别为它立项。
- 但**不要宣称完全归一**：15.5% 残留 + 缺字要如实反映到 UI 文案与文档（本轮 `TopicEmptyCard` / `filterByTagsWithFallback` 的降级说明就是这个用途）。
- 判据沿用：先在 assets 里找现成资产，再谈「需要新增依赖」。同类踩坑见 [[project-source-data-ceilings]]（那条是"确实做不到"，这条是"我误判成做不到"，两者共同点是**都要先实测再下结论**）。

相关：[[venera-ui-refactor-authoritative-docs]]、[[feedback-design-review-then-code]]
