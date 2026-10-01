---
name: project-glass-chrome-inline-area-rules
description: 画廊/顶栏玻璃层上的"内联展开区"三条硬约束：别涂不透明底（会出硬边）、bottomContent 已在模糊范围内、候选行别套卡片
metadata:
  type: project
---

本仓库的 `components/venera/VeneraTopAppBar` 那层背景是 **miuix progressive blur 玻璃**，
它画在顶栏 `Box` 的 `matchParentSize` 上，**`bottomContent` 整块都在它的绘制范围内**
（下载页/分类页/收藏页/历史页都用 `bottomContent` 放"顶栏下方常驻内容"）。

由此三条（2026-09-25 画廊搜索区真机反馈「效果有点差」量出来的）：

1. **放进 `bottomContent` 的东西不要自己涂不透明底**。涂了 `tokens.color.background` 之后，
   上面那截是实时模糊（透出网格照片）、下面那截是实色近黑 —— 同一块面板两种材质，
   真机上就是**一道横贯屏幕的硬边**（量在 y≈268px≈97dp 处）。撤掉那层底即可：
   静止时透出的是同一层氛围底，滚动时整块一起糊。
   ⚠️ 但**顶置时玻璃层的 alpha 是 0**（`scrollProgress` 驱动），所以"不涂底"依赖页面背景本身有料
   （`drawVeneraAmbient` 那层），换到没有氛围底的页面上要另想。
2. **内联区的每一行都在跟内容抢高度**。真机量过：输入框 56dp + 源行 + 历史 FlowRow 换两行 + 提示行
   = **≈201dp**，约屏幕 1/4，网格只剩一条缝。收法：历史改**单行横向滚动**、提示行只在条件存在时出现、
   重复的那行（条件 chips 与框内胶囊）只留一份。
3. **候选/补全列表不要套 `VeneraCard`**：一行卡片约 56dp，8 行就是 450dp。
   候选是"扫一眼挑一个"的东西 → 单行紧凑项（≈32dp）+ 上限收到 6 行。

**How to apply:** 再往顶栏里塞常驻内容（筛选条、分段、展开区）时先问这三条；
收到"效果差/太挤/像廉价"这类观感反馈时，**先量像素再改**（PowerShell 扫列取亮度，见
[[reference-windows-pixel-measurement]]），不要凭感觉调间距。
相关：[[feedback-live-controls-no-submit-step]]、[[feedback-mirror-official-values]]、
[[venera-ui-refactor-authoritative-docs]]。
