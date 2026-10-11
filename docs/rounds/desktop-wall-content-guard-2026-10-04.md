# 桌面第四批：墙上那一关 —— 分级与屏蔽第一次真落到桌面（2026-10-04）

范围：`desktop/`。**Android 侧零改动**（`git status --short app/` 空，全程未碰）。
分支 `compose-migration`，本批未提交（提交要用户明示）。

---

## 1. 这批本来要做的事，和它的前提被量掉

方向表（手册 §6）第 1 条一直是「设置页」。开工前先数了一遍消费点，读数比"写侧要新建"更糟：

| 档 | 枚数 | 桌面今天有没有读它的地方 |
|---|---|---|
| 大图页专属：`galleryKeepScreenOn` / `galleryVolumeKeyTurn` / `galleryAutoPlaySec` / `galleryPreload` / `galleryAnimated` / `galleryBackdrop` | 6 | **没有** —— 桌面根本没有大图页（P5 那一条没搬） |
| 墙上就能接：`galleryColumnMode` / `galleryPreviewQuality` / `galleryAiBadge` | 3 | 没有，但**接得住**（列数已有现成出处 `DesktopGalleryMetrics.imageWallColumnCount`，缩略档与角标都在 `DesktopGalleryPostTile` 里） |
| `galleryBlockAi` | 1 | **本批接上了** |
| `galleryHideTopBar` / `galleryHideBottomBar` | 2 | 结构性不可能 —— 桌面没有那两条栏，`DesktopGalleryPreferences` 给的是常量 `false` |

判法：`grep -rn "galleryColumnMode\|…\|galleryHideBottomBar" desktop/src/main --include=*.kt`，
扣掉定义处与注释，剩下的就是消费点。

⇒ 今天做设置页，摆出来的 11 枚里 **10 枚是假开关**（按下去存盘成功、屏上什么都不变）。
本仓对假开关零容忍，所以这批**换了一件事做**，并把设置页的前置条件从"写侧要新建"改成"**要先有消费点**"。

## 2. 换做的那件：三面墙从来没走内容守卫

这不是"顺手对齐观感"，是**安全相关的缺口**：桌面从 S2-A 起就在**读**
`contentGuard.nsfwMaskMode` 与 `.rules`（`DesktopGalleryContentGuard.kt:44,:53`），
然后一处都不消费。Android 那把过滤判据 `buildGalleryWall` 住在
`app/src/main/java/com/venera/compose/gallery/ui/GalleryScreen.kt:1281`，
而 `gallery/ui/` **不在桌面编译面内**（桌面只共享 `gallery/data` 与 `gallery/domain`）。

后果：同一份存档、同一条屏蔽规则，**手机上挡着、桌面上直接摆出来**。
用户设的「成人内容处理 = 隐藏」与「屏蔽 AI 生成」在桌面形同虚设。

## 3. 三层落点，判据一个字都不重抄

| 层 | 落在哪 | 干什么 |
|---|---|---|
| 判定 | `GalleryGuard.maskStateFor`（`gallery/domain/GalleryGuard.kt:26`，**共享面内**） | 三档 `VISIBLE` / `BLURRED` / `HIDDEN`。**用户黑名单排在模式判定之前**（`blockedByUser` 先判）—— 黑名单就是"别给我看这个"，不该因为分级模式关着而失效，否则它是假开关 |
| 组装 | `desktop/.../gallery/ui/DesktopGalleryWallPolicy.kt`（新，128 行） | 纯函数 `of(posts, maskMode, blockAi, guard)` → `DesktopWall(cards, blockedCount, blockedRules, hiddenByRating)`。不 require `@Composable`，所以能被单测直接取值 |
| 落屏 | `DesktopGalleryPostTile`（卡）+ `DesktopWallGuardLine`（那句读数）+ 三面墙 | 遮挡档卡上写「分级遮挡」；两笔缺席分开念 |

`DesktopGalleryWallPolicy.of` 与 Android 的 `buildGalleryWall` 是**同一套结构**（连 `LinkedHashSet` 收规则、
`blockedCount` 与 `hiddenByRating` 分账都一样），差别只在两处：入参从 `blockedRuleOf: (GalleryPost) -> String?`
换成整颗 `guard`（桌面没有 Android 那层的闭包），以及下面 §4 那条观感差异。

## 4. 与 Android 唯一一处不同：遮挡档**不发起取图**

Android 的 `BLURRED` 是"图取回来再糊掉"。桌面改成"根本不去取"：一张注定要盖住的内容，
没必要先花一次流量与一次解码再盖住它 —— 遮挡的语义本就是"这台机器的这个人不该看到它"。

这一条差异**摆在卡上**（角标「分级遮挡」+ 站名），不藏在代码注释里。
判据由 `DesktopWallGuardPolicyTest` ⑧ 钉：`heroBackdropOf(masked card) == null`。

## 5. hero 有**两处**绕过，第二处是新判据抓出来的

`DesktopDailyHero.kt` 原来直接取 `daily.merged.posts.firstOrNull()` 并把 `backdropUrl`
（**中档大图**，比墙上缩略档更清楚）铺上去 —— 等于"该挡的内容摆在最大最清楚的那一格"。
修完大位（改问 `heroBackdropOf(wall.cards.firstOrNull())`）之后，新加的 ⑨
（扫**整文件**不许出现 `merged.posts`）**又抓到第二处**：右列「这一轮取到了什么」还在从原始池取前三条报名，
于是墙上念着「13 张按「成人内容处理」收起」、右列照旧报那三条的站名与分数。
现在两处都走 `wall.cards`（`:175`），标题也跟着改成「屏上的头三条」。

⇒ 沉淀一条：**负向判据要按整文件收，不要按"我以为的那一处"收**（与手册 §4.1 同族）。

## 6. 一张开不出来的支票：注释指向的测试类从来没存在过

`DesktopGalleryPreferences.kt` 的类头注与键名区两处都写着「改这里之前先改那里，
否则 `DesktopGalleryPreferencesTest` 会红」。**这颗类此前不存在**（`desktop/src/test/` 下连
`gallery/data/` 这一档目录都没有，全仓搜不到类声明，`git log` 也从没有过）。
于是那 10 枚重述的键名与 9 枚默认档一直没人回核 —— 注释指向不存在的守卫，比没注释更坏：
读的人会以为漂了会被抓到。

本批兑现成 7 颗用例（`DesktopGalleryPreferencesTest.kt`，193 行）。两个漂移方向各有各的腿：
**对岸**改了默认档由 ② 抓（文本回核），**桌面这边**改漂了由 ④ 抓（空存储真跑）。

顺带记一条形状差异：Android 的 `nsfw_mode` 住在 `venera_guard_prefs`，
桌面 `DesktopGalleryContentGuard.kt:48` 读的是 **`venera_preferences` 这一颗 store**
（键名一致、文件不同）。将来接写侧时要认这一点，别以为两端共档。

## 7. 判据 +16，两处"改判据而非改实现"

新增 `DesktopWallGuardPolicyTest`（9 颗）：① 黑名单先判而成人档才跟着模式走 ② 命中同一规则的三条念三条而规则点两颗
③ 屏蔽 AI 开关关掉时一张都不挡、打开时按站方标签判 ④ 收起与屏蔽两句分开念、都没挡时一句不念（含两句措辞逐字回核 Android）
⑤ 分级判定只许调共享那颗而不许自己写条件 ⑥ `AI_BLOCKED_RULE` 与 Android 那枚常量逐字同串
⑦ 三面墙都从这一层过 ⑧ hero 在遮挡档根本不去取那张大图 ⑨ hero 也走这一层而不是自己挑第一张。

新增 `DesktopGalleryPreferencesTest`（7 颗）：见 §6。

改判据（不是改实现）的一处：`DesktopCardOverlayTest` 那条「Hero 大位走底图那一档而不是网格缩略档」
因 §5 搬了家而红 —— 判据要防的事（UI 层自己写取哪一档）**没发生**，发生的是决定挪了位置，
所以改的是**判据落点**：`.backdropUrl` 那半颗钉到 `DesktopGalleryWallPolicy.kt`，
hero 那半颗改成"必须经 `heroBackdropOf(`、且自己不许出现 `.backdropUrl`"。

当场修的一处措辞漂移：桌面那句原本写的是「命中屏蔽规则 x、y」，而 Android 的
`blockedByRulesFragment`（`GalleryFeedReadout.kt:36`）是「**N 张**命中屏蔽规则 x、y」——
漏掉张数就把"一条规则挡下三张"与"三条规则各挡一张"念成同一句。已对齐，并由 ④ 逐字回核钉住。

故意红两次（都是真红）：
- 桌面键名改成 `pref_gallery_preview2` + `keepScreenOn` 默认档改成 `false` ⇒ ①④ 同跑红；
- hero 的 `heroBackdropOf(...)` 换回 `wall.cards.firstOrNull()?.post?.backdropUrl` ⇒ 搬家后那条红。
两次都改回后复绿。

## 8. 读数（实跑，非估计）

- `:desktop:test`：**255 用例 / 0 失败 / 0 错误（33 颗类）**（判法：汇总
  `desktop/build/test-results/test/*.xml` 的 `tests/failures/errors`，不是数 `@Test`）。上一批 239，本批净 +16。
- `:desktop:packageUberJarForCurrentOS` EXIT=0，产物 `desktop/build/compose/jars/venera-desktop-probe-windows-x64-1.0.0.jar`
  **97,572,701 B**（本批中途两颗 97,572,679 / 97,572,684 B 是改动前的树；本仓包体差值不可解读，只记数）。
- `desktop/src` 行数：12863 → **13837**。
- **窗口图证三档**（手册 §16.4 那条 WGC 路，`occlusionSafe=true`，窗口不在前台也拿到图；
  取证条件是本机 `%LOCALAPPDATA%\venera\prefs\venera_preferences.json` 里写 `nsfw_mode`，**跑完已删回原状**，
  那颗文件本轮前不存在）：
  - `OFF`（键不存在时的默认档）：整墙原样出图，与 Android 同档同形。
  - `BLUR`：墙上「bra」「elf」两张变**深底 + 「分级遮挡 / yande.re」**、不发取图，其余照旧出图；
    右列「屏上的头三条」三条报名与墙上顺序一致。
  - `HIDE`：段头念「精选推荐 进屏 **27** 张 · Safebooru 20 张、yande.re 20 张」，
    紧跟一行「**13 张按「成人内容处理」收起**」（40 − 27 = 13，两句分开念：进屏数与收起数各报各的）；
    右列 = Safebooru·9 / Safebooru·20 / yande.re·4，与墙上前三张同序（改之前它会去报原始池那三条）。
- 参数判读顺带复跑：`-PstartDomain=DAILY` 被**拒**（枚举里没有 DAILY）⇒ 退出码 1、两条中文读数、没开窗。
  中文没变方块 ⇒ 手册 §16.3 那颗 UTF-8 修复在 `:desktop:app` 这一路上生效。

## 9. 三路后台 agent 的报告都不作数

本批期间收到三路"S4 桌面组件移植"的后台报告，中途点名的产物（`ResultStatePanel` 插墙、
`DesktopGlassSurface.kt`、`DesktopDomainNavigationTest` ⑮ 被削、`docs/plans/…s4-widget-counterparts.md`）
在主检出里**全部查无**，三条最终也都改口为"零改动收尾"。判法与逐条实测记在手册 §9。

它们那份"6 颗候选件在 fluent 面里都没有对应物"的结论，我按同一判法独立复算后**错了 2 颗**：
`AutoSuggestBoxKt` 与 `ProgressBarKt` 都在（44 颗组件的名单已整份抄进手册 §16.2 末尾）。
错因大概是包名 grep 成了 `io.github.compose_fluent...`，而 jar 里是 `io/github/composefluent/component/` ——
按前者 grep 会**恒零命中**，看起来就像"全都判负"。

## 10. 下一批的输入

1. **先补三枚消费点**（列数 / 缩略档 / AI 角标），再谈设置页写侧。
2. `galleryAnimated` 桌面给 `ALWAYS` 是登记过的刻意分档（用例 ③ 两头都钉着）；
   等桌面大图页搬过来时**重新判一次**，别顺手改成 `WIFI_ONLY`。
3. 桌面屏蔽规则的**写侧** `DesktopGalleryContentGuard.addRule` 已有实现、**零调用点** ——
   与"实现了但没接线"那一族同族，归设置页那一批一起解。
4. 收藏域与搜索域"全被挡下"那一档各有一句自己的话，本机存档里没有命中规则的收藏，
   所以那一档**只有单测覆盖、没有图证** —— 如实挂着。
