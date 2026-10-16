# 探索页改版第二轮：探测容错 + 分类回常驻 + 删副标题（2026-10-14）

题面（用户原话）：下午改的探索页越改越差 —— 只剩两个源、默认源 403、内容要手动点击才显示、UI 不行。
前一轮「折叠态按需展开」（筛选/分类折进按钮 + 顶栏副标题）被真机读数否掉。

## 1. 三个根因

| 截图 | 现象 | 根因 |
|---|---|---|
| 18:26（改后） | 源下拉只剩 2 颗、包子漫画 403 | `getSourceExplorations()` 探测失败的源被 `filter { !it.isEmpty }` 整颗剔除 —— 网络一抖（16:57 还有禁漫天堂，18:26 就没了）源就"凭空消失" |
| 16:57（改前） | 「筛选·3 / 分类·15」+ 死空态 | 折叠派把浏览主干（分类）藏进按钮：探索 = 浏览，折进去等于要先点一下才看得见能逛什么 |
| 两图 | UI 不行 | 副标题「禁漫天堂 · 禁漫天堂」同词两遍（为此还加了 `modeLabelSaysMoreThanSourceName` 判据 + 24dp 地板补偿，补丁摞补丁）；M3 暴露式下拉在自绘 miuix 风格里是外来户 |

## 2. 落地了什么

### 阶段 1 · 止血（功能）

- `SourceExploration` 加 `probeFailed` 标记；`getSourceExplorations()` 对探测抛异常的源
  **降级保留**（能力未知）而不是剔除；`isEmpty` 随之改为 `!probeFailed && ...`。
- VM 加 `lastGoodExplorations` 内存缓存：本轮探测失败的源若上次成功过，沿用上次结果 ——
  网络抖动时列表不缩水。
- `ExplorePolicy.friendlySourceErrorMessage`：403 这类裸异常 message 翻成人话
  （「这个源暂时拒绝访问（403）……稍后重试，或换一颗源」），内容加载与触底加载共用。
- 错误态加「换一颗源」chip（与空态同款 `switchToNextSource` 闭包）；探测失败的源
  内容区摆「重新检测」（走 refreshTick 重探源，不是 modeTick 空转）。
- 源下拉菜单里失败源标注「暂不可达 · 点击进入可重试」。

### 阶段 2 · 信息架构

- **删顶栏副标题**：源名在源下拉格里、模式名在模式行实心选中态上，两处都在屏上。
  连带删除 `modeLabelSaysMoreThanSourceName`、`Spacing.topBarSubtitleExtra`（24dp）、
  contentPadding 里的补偿项、UiAuditBatch7Test ②③ 两条锚点。
- **分类回常驻**：删「分类 · N」按钮与 `categoriesExpanded` 状态；分类区（榜档组 + 分类组 +
  「更多分类」）直接常驻摊着，首屏仍按 SECTION_ENTRY_BUDGET=8 截断。动作行只剩「筛选 · N」
  （筛选是辅助动作，3~5 枚标签折叠合理）。空态「挑一个分类」chip 随之删除 —— 分类区就在
  空态上面，再摆一枚按钮是假入口。
- 连带删除 `categoryEntryCount`、`exploreFoldedChromeDp`（折叠态那一轮的产物，生产零调用）。

### 阶段 3 · 视觉

- **源下拉自绘**：替掉 M3 `ExposedDropdownMenuBox`。锚点 Surface（描边 outlineVariant +
  surfaceContainerHigh 底 + 圆角 medium）+ 箭头，菜单 `DropdownMenu` 实底 + 纵向滚动
  （34 颗源放不下，上限 `sheetActionHeight * 6` 吃既有 token）。与本仓其余 5 处
  DropdownMenu 同一套视觉语言（先例：SearchScreen 的 SearchDropdownOption）。

## 3. 判据处置

| # | 尺子 | 结果 |
|---|---|---|
| ① | `UiAuditBatch7Test` ②③（副标题两条） | 删（副标题整个撤了），类头注释注明同轮加、当天删 |
| ② | `UiAuditBatch7Test` ④ 分类按钮 | 改判为 `分类区常驻不被展开开关门控`（防回潮：不许有 categoriesExpanded 门、「分类 · N」按钮） |
| ③ | `UiAuditBatch4Test` 分类区判据 | 保留 hasAnyCategoryEntry 整串判式，删 categoriesExpanded 门控断言 |
| ④ | `ExplorePolicyTest` | 删 categoryCount / foldedChrome / modeLabel 三条测试；新增 friendlySourceErrorMessage 三条（403/404超时/空兜底） |
| ⑤ | `ExploreEmptyStateTest` | 三条出路改两条（删「挑一个分类」），新增「那枚芯片不许回来」反向断言 |
| ⑥ | `TokenBudgetFrozenTest` | 绿：本轮零新增裸 dp/sp/color（topBarSubtitleExtra 是减项），三档只减不增 |
| ⑦ | `TopBarFloorGuardTest` / `ViewModelAssemblyGuardTest` | 绿（新代码零魔数、零新增 viewModel() 调用点） |

## 4. 验证读数

```
:app:compileDebugKotlin            → BUILD SUCCESSFUL
:app:testDebugUnitTest             → 143 类 / 1129 用例 / 0 失败 0 错误（基线 140 类 / 1112；
                                     增量来自今天折叠轮与本轮新增测试，删除 6 颗旧用例）
:app:assembleDebug                 → BUILD SUCCESSFUL，arm64-v8a 102,779,659 B
APK → venera-arm64-debug-2026-10-14-p2.apk（项目根）
```

## 5. 真机验证（未跑 —— MuMu 没开）

`adb connect 127.0.0.1:16384` → 连接被拒（只有后台服务在跑）。**MuMu 开起来后
验收清单**：

1. 冷启动探索页：源下拉是否列出全部已启用源（不再只剩 2 颗）
2. 点源下拉：锚点描边与菜单实底是否与 app 其余下拉一致；34 颗源滚动是否顺
3. 首屏 chrome：模式行（多模式源）+ 动作行（筛选）+ 常驻分类区（前 8 入口）的行数与高度
4. 失败源：菜单里「暂不可达」标注、点进去「重新检测」是否重探
5. 403 类源：错误态文案是否人话、「换一颗源」是否可用
6. 空态：两条出路（换一种方式 / 换一颗源）显隐；分类区就在空态上方
7. 触底翻页、返回不重拉、切源/切模式清筛选
