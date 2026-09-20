# 标签统计（题材偏好 / 标签云）从 `master` 移植到 compose

日期：2026-09-20　分支：`compose-migration`　状态：**待拍板，未动代码**

## 0. Scope / Stop Condition

- **Scope**：把用户原项目里的「标签统计」半区移植到 compose —— 数据链路（读了哪些漫画带了哪些标签）→ 归一化（跨源、跨简繁把同义标签合到一个桶）→ 呈现（可点击的标签云 / 题材占比）→ 点击下钻到搜索。
- **Stop Condition**：本轮不碰阅读统计的**非标签**半区（今日页数、连续打卡、14 天趋势、Top 漫画、按源分布 —— 这些 compose 侧已有且在跑）；不做分享文案的平台适配；不做多语言 `.tl`。
- **保护域预告**：写入面要动 `Reader` + `ViewModel`（手册 §6.3 禁改项），**需显式授权**，见 D1。

## 1. 现场清点（两侧实测，非推测）

### 1.1 来源：`master` 分支（Flutter venera-miuix），不是外部仓库

用户给的 `https://github.com/w13630039663-bit/venera-miuix` 就是本仓库的 `origin`。所以「原项目」= 同一 `.git` 里的 `master` 分支，代码已在本地，无需 clone。

| 资产 | 位置 | 规模 |
|---|---|---|
| 统计页（含标签半区） | `master:lib/pages/stats_page.dart` | 1211 行，首版在 `fc8f0e9`（2026-09-12，v1.6.5） |
| 数据层查询 | `master:lib/foundation/history.dart:431-536` | `addReadingStats` / `readTagRows` / `dailyPages` / `pagesSince` / `readDates` / `topComics` / `comicsByType` |
| 落库表 | `read_stats(date, cid, type, pages, tags)`，PK `(date,cid,type)` | `tags` 存 **JSON 数组**（`plainTags` = `"namespace:tag"` 列表） |
| 写入点 | `reader.dart:287`（会话结束 flush）、`loading.dart:91`、`actions.dart:141`（把 `comic.plainTags` 穿进 Reader） | — |
| 归一化 | `stats_page.dart:1122-1211` `_TagNormalizer` | 四级管线，见 D3 |

`_TagNormalizer` 四级管线（原文注释：「原始 plainTags 原样入库，归一化只在聚合时做 —— 翻译库将来更新可重算」，这条设计要照搬）：

1. **namespace 排除表**（33 项，作者/状态/语言/社团类，注释说清单来自对 20 个真实源 JS 的 tags 键名普查）→ 命中即丢弃；
2. **合并字典**：`TagsTranslation` 的 female / male / mixed / other / parody / character 六个库按 `en → zh` 合并（`putIfAbsent`，先到先得）；key 规范化 = 小写 + 去尾部 `s`（`reclass` 除外）；
3. **别名表**：`loli → lolicon`、`shota → shotacon`；
4. **OpenCC 繁转简**兜底：字典查不到时 `hasChineseTraditional ? traditionalToSimplified : 原样`；
5. **格式词排除**（26 项：同人/短篇/画集/AI生成/连载中…）→ 命中即丢弃。比对前先 lower+繁转简。

### 1.2 compose 侧现状：这条链路是**假活**的

不是「半成品」，是**永不产出数据**：

| 环节 | 位置 | 实测状态 |
|---|---|---|
| 表 | `data/db/VeneraDatabase.kt:79-94` `reading_stats` | ✅ 有 `tags TEXT NOT NULL DEFAULT ''` 列 |
| 写 | `reader/VeneraReaderScreen.kt:252-259` → `recordSession(...)` | ❌ **调用点根本没传 `tags` 参数**，走默认 `emptyList()` → 列恒为 `''` |
| 读 | `stats/ReadingStatsManager.kt:198-218` `getTopTags()` | 逻辑在，但 `WHERE tags != ''` 永远 0 行 → 恒返回空 |
| UI | `feature/StatsScreen.kt:269-313`「题材偏好热度」卡 | `if (topTags.isNotEmpty())` 包住 → **永不渲染** |
| 会话模型 | `reader/ComicPageSource.kt:101` `ReaderSession` | ❌ **没有 tags 字段**，`createLiveSession`(:124) 也没有该参数 |

即：`recordSession` 的 `tags` 形参从写下那天起没有任何调用者喂过值。所以「引入标签统计」在 compose 侧的真实工作量 = 接通写入 + 换掉聚合语义 + 换掉 UI，**只有表结构是现成的**。

`getTopTags()` 即便喂上数据也与 Flutter 版语义差 4 处，每一处都会产出错的统计：

| # | 现状（`ReadingStatsManager.kt`） | Flutter 版 | 后果 |
|---|---|---|---|
| a | `counts[clean] += 1`（按**行**计次） | `tagPages[tag] += pages`（按**页**计） | 权重变成「读了几次会话」，翻得厚的本子和翻两页退出的本子等权 |
| b | 无任何归一化 | 四级管线 | 萝莉 / 蘿莉 / lolicon 三个桶并列 —— 正是这个功能的全部意义所在 |
| c | `raw.split(',', '，', '、', ' ')`，且写入侧 `joinToString(",")` | JSON 数组编解码 | **按空格切**会把 `big breasts` 劈成 `big` + `breasts`；标签含逗号则往返即坏 |
| d | `if (clean.length in 2..10)` | 用 namespace 做**语义**排除 | 带命名空间的 `female:lolicon`(14 字符) 整条被长度滤掉 → 英文标签几乎全灭 |

另外 `recordSession` 整体被 `catch (_: Exception) {}` 吞掉（:55），写失败无痕迹。

### 1.3 **更正**：上一轮「繁简归一做不到」的结论是错的

上一轮做多标签搜索排查时，我从 `tags.json` / `tags_tw.json` 双字典里**挖**字级对照，只得到 345 字 / 370 映射，据此判定「没有 OpenCC 那层归一，繁简互不认」，并把繁简方案（Option A）判为需要新增资产依赖、延后。**这个判据漏看了仓库本身**：

```
app/src/main/assets/opencc.txt      29,472 字节 / 3981 行 / 3980 对
```

- 它就是官方 OpenCC 简繁字级表（文件头写明 `Original source: https://github.com/BYVoid/OpenCC`），**已经在 compose 分支的 assets 里**，随 APK 打包；
- `git grep -i opencc compose-migration -- app/src` 只命中该文件自身的两行注释 → **Kotlin 侧零引用，是个死资产**（多半是从 Flutter 侧照搬资源清单时带过来的）；
- Flutter 版 `master:lib/utils/opencc.dart` 只有 66 行：读该 asset、建 `Map<Int,Int>` 双向表、逐 rune 替换。没有任何算法，**移植成本约 40 行 Kotlin**。

实测覆盖率（刚跑的，不是估计）：

| 测项 | 结果 |
|---|---|
| 表规模 | 3980 对；s2t 3980 个唯一键，t2s 3952 个唯一键 |
| 常用字探测 | 夢→梦、靈→灵、蘿→萝、國→国、圖→图、東→东、雲→云、龍→龙 全部命中；`靈夢→灵梦`、`蘿莉→萝莉`、`遊戲→游戏`、`一個→一个` 整词正确 |
| **对真字典的归一效力** | 六类题材字典共 **8734** 条，其中简繁两版译名**不同**的 **756** 条；走 opencc.txt 字级转换后 **639 条（84.5%）归一成功**，残留 **117 条** |
| 残留构成 | 大部分是**词级差异**（`太陽眼鏡` vs `太阳镜`、`黑眼白` vs `深色巩膜` —— 字表原理上修不了，只能靠字典）；小部分是字表真缺字（`鬍→胡`、`姦→奸`、`菸→烟`、`瀏→刘`） |
| 一个系统性缺口 | 表里 `发→發`，但**没有** `髮→发`。t2s 方向的「多繁对一简」被丢了 28 个字（3980 对 → 3952 唯一繁体键），`超長髮`、`抓頭髮`、`髮交` 这类头发义标签转不过来 |
| 是不是旧版本？ | **不是。** `master:assets/opencc.txt` 与本分支 `app/src/main/assets/opencc.txt` git blob 同为 29,472 字节；`.reference/flutter-master/assets/opencc.txt` 字符数看着多 4000 只是**行尾符差异**，实测量完全一致（同为 3980 对 / 3952 唯一繁体键 / 同样 84.5%）。**结论：不存在「换一份更全的表」这条路，84.5% 就是这张表的天花板**，缺 `髮/鬍/姦/菸/瀏` 是表本身的问题 |

→ 结论修正：**繁简归一在 compose 侧是可做的，成本约 40 行 + 零新增依赖（asset 已在）**。残留 15.5% 与「多繁对一简」缺字要如实写进 UI 文案，不能宣称完全归一。

这条更正同时打开搜索页那条路：`master:lib/utils/multi_tag_search.dart` 的 `normalizeTagValue` 注释就是「去 namespace 前缀、小写、**繁转简**」，而我们的 `TagSearchPolicy.normalizeTagValue` 缺的正是最后一环（见 D3-c）。

## 2. 决策点（需拍板，均可多选）

### D1 写入面 —— 触碰 `Reader` + `ViewModel` 两个保护域，要显式授权

| 选项 | 内容 | 代价 |
|---|---|---|
| **1a（推荐）** | 4 处最小接线：`ReaderSession` 加 `tags: List<String> = emptyList()`；`createLiveSession` 加同名参数；`ComicDetailViewModel.kt:799` 传 `comic.tags`（该作用域已有 `comic`）；`VeneraReaderScreen.kt:252` 传 `tags = session.tags` | ~8 行，全在保护域，**须独立成提交**便于真机二分 |
| 1b | 不动 Reader：进阅读时在详情页侧另写一张 `comic_tags` 表，聚合时 join | 不碰保护域，但**新增一张表 + 一份生命周期**，且「哪本被读过」仍以 reading_stats 为准 → 两处真相，将来必然漂移 |

无论文档怎么写：**历史数据不可回填**。老 `reading_stats` 行的 `tags` 恒空，标签统计从装上新版本之后开始积累（Flutter 版 `stats_page.dart:17-18` 同样写明「无历史回填」）。

### D2 `tags` 列的序列化格式（现在会切碎标签）

| 选项 | 内容 | 代价 |
|---|---|---|
| **2b（推荐）** | 改用 `\u001F`（单元分隔符）join/split。标签里不可能出现该字符 | 零依赖、单行改。老行是逗号串 → 读取时按 `\u001F` 解不出多值，退化成一个整串，被 D3 的 namespace 规则自然过滤掉，**不需要迁移** |
| 2a | 改 JSON 数组，逐字对齐 Flutter | 多一个 org.json 编解码（工程里已在用 JSONObject，成本可接受），但比 2b 啰嗦 |

⚠️ 已被空格切碎的历史标签无法复原（信息已丢失）。这不是迁移能解决的，只能等积累。

### D3 归一化管线范围

| 选项 | 内容 | 代价 / 收益 |
|---|---|---|
| 3a | `TagNormalizer`（120 行）：namespace 排除（**31** 项）→ 合并字典（**8734** 条）→ 别名（2 条）→ 格式词排除（**24** 项）。字典取自现成的 `TagTranslationManager`（其 `LangDict.scoped` 已按 namespace 分好） | ✅ 已落地，含 10 条 JVM 单测 |
| **3b（与 3a 叠加）** | 额外新增 `ChineseVariantConverter`：`init` 时读 `assets/opencc.txt` 建 t2s 表（3952 项），暴露 `traditionalToSimplified()` / `hasChineseTraditional()` | 约 40 行、**零新增依赖**（asset 已在 APK 里）。解锁 84.5% 的简繁归一（实测见 §1.3） |
| **3c（与 3b 叠加，顺手）** | 把同一个 `traditionalToSimplified` 接到 `TagSearchPolicy.normalizeTagValue`，补上缺失的第三级 | 约 +5 行。**同时了结上一轮延后的 Option A**：搜索页多标签不再因繁简整页判不匹配 → `filterByTagsWithFallback` 的 `relaxed` 降级触发率下降 |
| 3d | 移植时给 opencc 缺字打补丁（`鬍→胡`/`姦→奸`/`菸→烟`/`瀏→刘`/`髮→发` 等） | 一条 asset 追加若干行即可。建议**先不做**，等真机看到具体错例再补，别凭样例表猜全量 |

我推荐 **3a+3b+3c**（3d 押后）。3c 严格说超出「移植标签统计」的 Scope，但它和 3b 共用同一个新文件，拆开做反而要写两遍接线 —— 是否纳入由你定。

### D4 UI 范围（compose 侧现在只有一块死掉的 chip 列表）

Flutter 标签半区共 5 块：

| 块 | 位置 | 是否要自绘 |
|---|---|---|
| 范围切换（近 30 天 / 近 1 年） | `stats_page.dart:274-286` | 否 |
| 题材偏好 **环形图** + 图例（Top6 + 「其他」，图例第一行带「去挖」按钮） | `:287-313`，`_PreferenceDonut`(:832) + `_DonutPainter`(:947) | **是**（`drawArc`） |
| **Soul Tag**（第一标签大字号 + 分享按钮） | `:314-383` | 否 |
| **标签云**（Top12，字号 `12+8w`、底色 alpha `0.10+0.22w`，点击下钻） | `:344-379` | 否（`FlowRow` 已在用） |
| 追漫轨迹**时间轴**（按月 Top2，圆点竖线） | `:384-402`，`_TimelineRow`(:1016) | 轻（一条线一个圆） |

| 选项 | 内容 | 代价 |
|---|---|---|
| 4a | 五块全移植 | 最全，≈ 新增 400 行；环形图 + 时间轴两处自绘。注：本页**已有**自绘先例（`DailyTrendChart` 用 `Canvas` 画 14 天柱状图，StatsScreen.kt:370-426），所以环形图不算引入新基建，只是多一份 `drawArc` 与图例点色序列 |
| **4b（推荐）** | 范围切换 + Soul Tag + 标签云 + 时间轴；**环形图换成占比条**（横条 + 百分比，复用标签云同一批数据） | 省掉唯一一处复杂自绘；占比条读「第 1 名吃多少」其实比环形图更直接 |
| 4c | 只做 范围切换 + 标签云（+ 点击下钻） | 最小可用，约 120 行 |

注：所有文案按工程惯例直接写中文（`Soul Tag` → 「本命题材」还是保留英文？见 D5 附带的 Q）。

### D5 点击标签的落点语义（原版这里有个隐患，别照抄）

原版 `_dig(tag)` 把**归一化后的中文规范名**编码成 `tag:萝莉` 丢进聚合搜索。客户端过滤那一路（非原生源，源侧 tags 是中文）能命中；但**原生标签源**（EH / nhentai / hitomi / manga_dex）站方要的是英文原值，`tag:萝莉` 大概率空手而归 —— 桶里只剩显示名、可检索的原值被丢掉了。

| 选项 | 内容 |
|---|---|
| **5a（推荐）** | 每个桶同时记 `display`（中文规范名，用于显示）**和** `searchable`（该桶里出现次数最高的**原始** `namespace:tag`），点击带 `searchable`。顺手修掉原版这条缺陷 |
| 5b | 照抄原版（只带中文显示名） | 省事，但原生源那一路注定搜不到 |

落点接口：`TagSearchRoute(keyword, sourceName)`（`Navigation.kt:88`，工作区版本已带 `sourceName`）只携带**一个字符串**。

| 选项 | 内容 |
|---|---|
| 5c | 继续用 `keyword`，值形如 `tag:female:lolicon` 由 `TagSearchPolicy` 解析 | 不改路由，但要把字符串再 parse 回结构体，等于自己造一遍 `TagQuery.parse` |
| **5d（推荐）** | `TagSearchRoute` 加可选字段 `tagNamespace: String = ""` + `tagRaw: String = ""`，搜索页直接组 `SearchTag` 走既有的两路策略 | Navigation 是保护域（上一轮已为 `sourceName` 豁免过一次，这属同类小改），**要一并记豁免** |
| 5e | 标签点击不进搜索，只做展示 | 那 D5 整条作废，功能趣味掉一半 |

## 3. 原版已知缺陷 / 移植时的处理（逐条列，不含糊）

1. **`tagQueryOf` 的含空格保护**（`multi_tag_search.dart:104-115` 注释点名：e-hentai / nhentai 大量标签含空格，不加引号会被 `tag:(\S+)` 拆碎）—— 我们**没有** TagQuery 字符串解析层（compose 走结构化 `SearchTag`），若选 5d 则天然绕开；若选 5c 则必须自己补这层引号规则。**这是选 5d 的硬理由。**
2. **桶只存显示名 → 原生源搜不到**：见 D5-a。
3. **`_secondsPerPage = 15` 是拍的**（原版注释自认「没有真实计时」）。compose 侧 `reading_stats` 有**真实** `duration_seconds`，**不要**移植这个估算常数，直接用真实时长；`_estimate()` 那套「≈ x min」在 compose 侧是多余的。
4. **`normalize()` 要求必须有 `:`**（`idx <= 0` 直接 return null）：源给的裸标签（无 namespace，如 jm 的分类词、「校服」）会被整条丢掉。这是原版的口径（宁缺毋滥），我倾向**照搬**并在文档记一句，免得后来人当 bug 修。
5. **`topComics` / `bySource` 不在本 Scope**。

## 4. 诚实收窄（做不到 / 不做的部分）

- **无历史回填**（D1）。装新版之前读的本子，题材统计里不会有。
- **繁简归一 15.5% 残留**（§1.3 实测）+「多繁对一简」缺字（髮/發 类）。残留多为词级差异，字级表原理上修不了 → UI 上「本命题材」不能说「已合并全部同义写法」。
- **跨语言合并只到字典边界**：字典没有的英文标签（源自有但 EhTagTranslation 未收）会原样成桶，不与中文桶合并。
- 标签统计的分母是**页数**，而 `VeneraReaderScreen.kt:257` 记的是 `pagesRead = maxPageReached`（本次会话到达的最大页码，非增量），且每次进阅读器都新增一行 → 反复重读同一章会重复计入。这是**既存缺陷**，与本次移植无关，改它要动 Reader 翻页记账逻辑，另立条目。

## 5. 实施顺序（拍板后才动）

1. D2 + D3：纯函数层（`ChineseVariantConverter` / `TagNormalizer` / 聚合函数）+ JVM 单测。不碰 UI、不碰保护域 → 可先落地并全绿。
2. D1：写入面接线（保护域，**独立提交**）。
3. D4 + D5：UI + 下钻（若选 5d，含 Navigation 豁免记录）。
4. 每步跑 `:app:compileDebugKotlin` + `:app:testDebugUnitTest` + `:app:assembleDebug`；UI 完事后真机 QA。

## 6. 真机 QA 预告（手册 §7.3）

1. 读完 3~5 本不同源的本子退出 → 统计页「题材偏好」是否出数（**这条是本次移植的最低验收线**，现在永远为空）；
2. 故意读一本 jm（中文标签）+ 一本 EH（英文标签）→ 同义题材是否合桶；简繁两版写法是否归一（D3-b 生效判据）；
3. 点标签云 → 是否跳到**该源**的搜索且非原生源不整页空（`relaxed` 提示是否出现）；
4. 含空格标签（`big breasts`）点进去 → 是否被拆碎；
5. 范围切换 30 天 / 1 年 → 时间轴月数（6 / 12）与占比是否跟着变；
6. 老数据设备升级安装（非全新装）→ 不因新分隔符崩、老 `reading_stats` 行仍能被 `getSummary` / `topComics` 正常统计。

## 7. 实施记录（2026-09-20，D1~D5 全按推荐落地）

Build QA：`:app:compileDebugKotlin` + `:app:testDebugUnitTest` + `:app:assembleDebug` → **BUILD SUCCESSFUL**；单测 `TagNormalizerTest` 10 条、`TagSearchPolicyTest` 5 条（新增 1 条简繁用例），failures=0 errors=0。

| 落点 | 内容 |
|---|---|
| **新增** `data/tags/ChineseVariantConverter.kt` | opencc.txt 加载器（构造即在 IO 线程自加载，暴露 `ready: StateFlow<Boolean>`）。转换按**码点**走而不是按 `Char` —— CJK 扩展 B 是代理对，按 Char 迭代会把一个字劈半 |
| **新增** `data/tags/TagNormalizer.kt` | 五级管线，纯 JVM 类（字典与繁简函数注入）。namespace 排除 31 项、别名 2 项、格式词排除 24 项、字典 8734 条 |
| `data/tags/TagTranslationManager.kt` | 新增 `topicEntries(namespaces)`：按**传入顺序**取若干命名空间的 `en→中` 词条。为什么不是直接给 map —— `scoped` 是 HashMap 无序，而合并字典要确定性优先级 |
| `stats/ReadingStatsManager.kt` | 写侧 `tags` 改 `\u001F` join；删掉 `getTopTags`；新增 `getTagStats(days)`（按页加权 + 归一 + 月份 Top2 + 每桶记可检索原值）与 `buildNormalizer()`（等字典与简繁表，共享 3s 预算，超时也返回） |
| `stats/ReadingStatsModels.kt` | 新增 `TagStatBucket`（display + search* 双份）、`MonthlyTagTop`、`TagStats` |
| `feature/TagSearchPolicy.kt` | 3c：`normalizeTagValue` 加第四级繁转简（`toSimplified` 默认恒等），`matchesTagFilter` / `filterByTags` / `filterByTagsWithFallback` 同步透传。**顺带改掉那句错的注释**（「我们没有 OpenCC 那层归一」） |
| `feature/SearchViewModel.kt` | 加 `variantConverter` 字段；3 处客户端过滤（聚合 / 单源 / 加载更多）补第三参数 |
| `feature/StatsScreen.kt` | 删死卡；新增范围切换 / 题材占比（Top6 + 其它占比条）/ 本命题材 + 题材云（字号 `12+8w`、底色 `0.10+0.22w`）/ 追漫轨迹 / 空态说明卡。签名加 `onDigTag: (TagStatBucket) -> Unit` |
| `feature/Navigation.kt` + `SearchScreen.kt` | 5d：`TagSearchRoute` 加 `tagNamespace`/`tagRaw`/`tagLabel`；`AndroidSearchScreen` 加 `initialTag`，在下钻 `LaunchedEffect` 里**先挂标签再搜**（顺序反了 `search()` 取的就是不带标签的快照） |
| 保护域（D1，已授权） | `reader/ComicPageSource.kt`（`ReaderSession.tags` + `createLiveSession` 参数）、`reader/VeneraReaderScreen.kt`（落库补 `tags = session.tags`）、`feature/ComicDetailViewModel.kt`（补 `tags = comic.tags`）。豁免记录见 `FREEZE-STATEMENT.md` 2026-09-20 第三批 |

### 实施中的口径收窄与偏差（照实记）

1. **下钻不锁源**：一个题材桶可能来自多个源，`sourceName` 锁到任意一个都是猜 → 落在全网聚合上。非原生标签语法源会命中 D 那条已实现的「该源不支持纯标签搜索」提示，行为可解释。
2. **没移植分享按钮**：原版 Soul Tag 卡右上角那枚分享走的是 `Share.shareText` + `.tl` 多语言，属 §0 Scope 外。
3. **没移植 `_estimate()`**：原版按 `_secondsPerPage = 15` 拍脑袋估时长（其注释自认「没有真实计时」）。我们 `reading_stats` 有**真实** `duration_seconds`，不需要估。
4. **`taggedPages` 分母与原版有意分歧**：原版把所有「带 tags 的行」都计入分母（含标签被管线全滤掉的行）；这里只计至少贡献一个桶的行，否则占比条会被灌水。已写进 `getTagStats` 的 KDoc。
5. **不做数据迁移**：`tags` 列此前没有任何写入者（`recordSession` 的 `tags` 形参无人传值），存量行恒为空串，没有需要按旧逗号规则解读的数据。
6. **空态必须说话**：新增 `TopicEmptyCard`。因为「不带 namespace 的裸标签不参与统计」是照搬的原版口径，jm 这类只给分类词的源会**合法地**归不出桶 —— 不解释就会被当成 bug 报回来。

## 8. 全源标签形态普查（真机 QA 反馈后补，33 个源逐个查）

真机反馈：**只有 ehentai 记录得到标签，禁漫 / 哔咔都没有**。查完 33 个源，根因与影响面如下。

### 8.1 根因（一条，不是多条）

写入侧取的是 `ComicItem.tags` —— 那是**列表接口**的标签，而不是详情的 `tagMap`。列表标签对多数源是**裸词**（无 `namespace:`），而 `TagNormalizer` 按原版口径 `idx <= 0` 一律丢弃 → 整源零贡献。EH 之所以能出数纯属巧合：`ehentai.js` 的列表项直接把 DOM 的 title（`female:lolicon`）当标签字符串用，自带了冒号前缀。

第二个坑在解析层：`JsComicSource:470` 对 Map 形态的 tags 交出的是 `tagMap.values.flatten()` —— **命名空间在这一步就丢了**，所以即便去用 `details.comic.tags` 也一样是裸串。必须用 `tagMap` 自己打平，故新增 `ComicDetails.plainTags`（值已自带 `:` 时不重复加前缀，避免拼出 `标签:female:lolicon`）。

> 顺带证伪一个我差点写出去的结论：5 个源的详情 tags 是 JS `new Map()`（ehentai / nhentai / hitomi / wnacg / mycomic / jcomic / comic_walker），看着像会被 `JSON.stringify` 打成 `{}`，但 `venera-shim.js:74-83` 的 `_serializeMessage` 显式把 `Map`→对象、`Set`→数组 —— 桥是好的，这些源的 tagMap 确实到了 Kotlin。

### 8.2 逐源结果（分组键抄自 `app/src/main/assets/sources/*.js` 的 `loadInfo`）

| 源 | 详情 `tags` 分组键 | 存活（参与统计） | 会被排除表挡掉 | 修好后 |
|---|---|---|---|---|
| ehentai | 动态（= EhTag 原生命名空间 female/male/parody/character/…） | female/male/parody/character/mixed/other | group/artist/language/misc | ✅ **信息量最大** |
| nhentai | 动态（= `tag.type`） | tag/parody/character | artist/group/language/category | ✅ |
| hitomi | type/groups/series/characters/females/males/others/artists/language | type/series/characters/females/males/others | artists/language/groups | ✅（值自带 `f:`/`m:` 前缀） |
| picacg | Author / Chinese Team / Categories / Tags | Tags、**Chinese Team** | Author/Categories | ✅ 但会混进汉化组桶 |
| jm | Author / Tag / Work / Actor / View | Tag、**Actor** | Author/Work/View | ✅ 但会混进角色名桶 |
| copy_manga / copy_manga_multi_accounts / hot_manga | 作者/更新/标签/状态 | 标签 | 其余三项 | ✅ |
| baozi / ccc / ikmmh / komiic / manhuaren / mh1234 / zaimanhua / goda / mh18 / comick / happy / manwaba / mxs / manga_dex / wnacg / hcomic / mycomic / kavita / komga / lanraragi / manhuagui | 多为 `作者`+`标签`/`题材`/`类型` 组合 | 标签/標籤/Tags/题材/类型 | 作者/状态/更新/語言/分類 | ✅ 见 8.3 的垃圾桶列 |
| comic_walker | 动态 `a.role` + Labels | Labels | 作者类 role | ⚠️ 只有 Labels |
| jcomic | authors / categories | —— | 两项全在排除表 | ❌ **零贡献** |
| shonen_jump_plus | Author / Update | —— | 两项全在排除表 | ❌ **零贡献** |
| ykmh | 源直接返回 `tags: []` | —— | —— | ❌ **零贡献** |

**结论：修好后 30/33 个源会出数，3 个结构性零贡献**（jcomic / shonen_jump_plus / ykmh —— 前两个是源只给作者与更新，第三个是源脚本本身没填 tags）。这不是移植缺陷，是源的数据面。

### 8.3 普查顺带查出的垃圾桶键（排除表该补但**尚未**补）

以下分组会活着进统计，但值不是题材：

| 键 | 出处 | 值示例 | 判断 |
|---|---|---|---|
| `日期` | hcomic.js | `2023-04-12` | 明确该排 |
| `頁數` | wnacg.js | `32` | 明确该排 |
| `Pages` / `Extension` | lanraragi.js | `24P` / `.zip` | 明确该排 |
| `提示` | kavita.js / komga.js | 服务端提示语 | 明确该排 |
| `Chinese Team` / `汉化组` | picacg.js | 社团名 | 该排（与已排的 `group`/`社团` 同义） |
| `Actor` / `出演` | jm.js | 角色名 | 存疑：角色算不算「题材偏好」？原版排了 `work`(原作) 却没排角色 |
| `地区` | mxs / mycomic / manhuagui | `日本`/`韩国` | 存疑：是偏好但不是题材 |
| `年代` | manhuagui | `80年代` | 存疑：与已排的 `date`/`时间` 同类 |
| `系列` / `series` | komga / hitomi | 作品系列名 | 存疑：与已排的 `work` 同类，但 hitomi 的 series 常被当萌属性用 |

前三行是**确定该排**（数值/扩展名/提示语，进统计纯属噪声）；后六行是语义判断，需要拍板。



