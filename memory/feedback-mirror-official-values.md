---
name: feedback-mirror-official-values
description: 视觉/尺寸类改动一律采用现成口径（官方原值、应用内既有 token/组件/图标 glyph 配对），不要自加码也不要自造数字或自换图标
metadata:
  type: feedback
---

视觉与尺寸类改动，**先找已有口径照抄**：移植官方参考实现（pixez / miuix catalog、官方 Flutter 版）时用官方原值；在应用内做同类元素时用仓库里已有的 token/组件。既不加码，也不自造新数字。

**Why:** 三次被否。① LiquidGlass 底栏曾把折射量拉到高度 2 倍并开深度效果（blur 4dp + lens(24,48,depth)），真机反馈「反射效果有点过了」，回到官方原值 blur 8dp + lens(24,24)、不开 depthEffect 才对；玻璃感应来自 vibrancy ⇒ blur ⇒ lens 的**顺序正确**，不是加大参数。② 2026-09-20 收藏页单列卡嫌大，我自定了 `maxTagChips=2 / maxTagLines=1`、又自定封面 122dp，用户回「**不要自己乱改**，跟搜索页一致」—— 应用里本来就有搜索页单列行卡这个成规（`listCoverWidth` 92dp、`searchVisibleTags` 取 5 个、`FlowRow maxLines=2`、`VeneraTagChip`），照抄即可。③ 2026-09-22 预测式返回转场自造了 `0.92f` 缩放 + `400ms`，还写了注释论证「不用 VeneraMotionTokens 那一档」—— 实际上 `Navigation.kt` 里本来就躺着 `tween(300)` + `it / 4` 视差这套横滑口径（`popEnter/popExit` 与横滑切 Tab 都在用）。改用现成口径后**零新常数**，且与点击返回同构，顺带消掉了提交帧跳形。自造数字常常不只是难看，还会破坏与既有分支的一致性。

**现成口径要按"部件类型"分档，别把 chrome 的口径套到内容块上（2026-09-22 第四次被否）**：大屏适配里 `min(540dp, 宽-48)` 这个从 master 抄来的口径，用在悬浮底栏 / 阅读器顶栏 / 控制岛上都被接受，但拿它去收口**首页推荐区**（一个内容块）就被用户否掉：「但是这样空出来感觉太多了」—— 1280dp 上两侧各留 394dp 空白。改成"按漫画网格同一口径排多列封面"（每 220dp 一列 → 5 列）才通过。**Why:** 悬浮 chrome 天生该窄（一屏一个，居中限宽是移动端的正常形态），内容块则该随窗口**加列/加项**，两者参照物不同；同一个"有出处"的数用在错的类型上，仍然是错的。**How to apply:** 做宽屏适配先判这个部件是 chrome 还是内容 —— 内容块优先"加列"（复用列宽口径）而不是"限宽居中"；master 对某个部件**没有**宽屏形态时（阅读器、首页 Hero 都是这种），如实说明"没有可照的形态"并给几条有出处的路线让用户拍，不要默默把别的部件的数搬过来。

**图标观感被否时，先查"设计系统归属方"有没有官方成套图标，别凭 taste 从 material-icons-extended 里挑（2026-09-23 两次往返）**：用户嫌收藏页布局钮图标难看，我先换 `GridView`→`SpaceDashboard`，被否并要求"退回 23:48 那版"；再提 Miuix 才走通 —— 正解是 **`top.yukonga.miuix.kmp:miuix-icons`** 这个独立 artifact（与在用 miuix 逐字同版本，本仓此前只引了 ui/blur，没引 icons），里面有官方 `MiuixIcons.GridView` / `MiuixIcons.ListView` 一对，语义正好、线圆角与其余 Miuix chrome 同源。**How to apply:** 本仓观感的归属系统是 Miuix，图标优先取它的官方集而不是 material；引新 artifact 前照例核 `.module` 依赖面（icons 包只拉 miuix-core + foundation，不碰 material3，这条必须验，material3 版本被 Miuix 钉死）。

**"太大/太丑"要先量一遍再改**：同一轮里我按"图标太大"去缩 glyph，但按截图状态栏字号反算，图标实测仅约 18dp —— 真正的问题是**容器底座在浅色主题上完全不可见**（半透明 surface + 0.5dp 描边等于没画），于是只剩一团实心主色浮在角上，读起来就是"又大又丑"。**How to apply:** 收到"太大/太丑"先分辨是尺寸错还是**承载物没画出来**；后者要补的是可见底座（真毛玻璃走 `textureBlur` 复用顶栏那份 `rememberTopBarBackdrop` 采样层 + 与 `VeneraTopAppBar` 同一组 blurRadius/blendColor 参数），而不是继续缩图标。

**How to apply:** 动手前先 grep 有没有同类实现/token 在管着这个观感；有就复用它（必要时抽成公共组件共用，别复制配方，两处必飘）。找不到现成口径再自定，并在汇报里说明"应用内无先例，这是我的取值"。一次只动一个维度，以真机观感为准，不凭「理论上更强」下结论。相关流程见 [[venera-ui-refactor-authoritative-docs]]。

