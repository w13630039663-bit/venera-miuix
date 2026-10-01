# 多源标签多语言问题 —— 设计方案（仅方案，未实施）

> 日期：2026-09-19 ｜ 分支：compose-migration ｜ 状态：**L1 决策已定稿，待实施**（见 §6）
> 结论先行：**L1（显示统一）可以做且低风险；你举的"简体/繁体/英文重复"这个具体例子现有字典解不了，属于 L2 概念表范畴。**

## 0. 问题拆解

你描述的其实是两个问题，混在一起会做出错误方案：

| 编号 | 问题 | 本质 | 归属层 |
|---|---|---|---|
| **A** | 同一意义的标签以简/繁/英多种写法出现，看起来重复、占位、看不懂 | **展示**问题 | UI / Component |
| **B** | 用不同语言的词去搜不同源，命中结果不一样 | **检索**问题 | 查询构造（Source 侧知识） |

A 能低成本解掉；B 是硬问题，且**不能靠"UI 自己合并"来解**（手册 §11：不得伪造 Source 能力）。

---

## 1. 现状事实清单（全部已核实，带 file:line）

### 1.1 基础设施已造好，但没接线
- `data/tags/TagTranslationManager.kt` 加载 `assets/tags.json`（1.04MB，EhTagTranslation）。
  - `translate(tag, namespace)`（`:88`）与 `getNamespaceName()`（`:100`）**零调用点**。
  - 只有 `suggestTags()`（`:107`）被搜索联想使用（`SearchViewModel.kt:55, :72`）。
- `assets/tags_tw.json`（1.3MB 繁体）**从未被 Kotlin 加载**。
- 即：**当前界面上没有任何一个标签被翻译过**，全部显示源生原文。

### 1.2 请求链路已经是"显示/查询分离"的（这是 L1 安全性的根据）
- `SearchTag(namespace, raw, label)` —— `feature/SearchOptionsState.kt:55`，`label` 纯展示，`raw` 才是查询词。
- 构造真实请求时只取 `raw`：`TagSearchPolicy.requestKeyword` → `tags.map { format(it.namespace, it.raw) }`（`feature/TagSearchPolicy.kt:30`）。
- 客户端按标签过滤同样只用 `raw`：`matchesTagFilter`（`:45`）。
- **「中文 (原文)」双显样式已存在**：搜索联想药丸 `label + " (" + raw + ")"`（`feature/SearchScreen.kt:299-301`）。
- 详情页/结果卡片标签点击发出的是原始 tag（`ComicDetailScreen.kt:493` → `TagSearchRoute(keyword = tag)`）。

→ **结论：在显示层做任何翻译/折叠，都不会改变发给源的内容。这是本方案风险可控的核心依据。**

### 1.3 已有的同族先例（不要另起一套）
- `source/explore/UnifiedTags.kt`：文件头写死纪律「绝不能覆盖/修改/删除源自己的原生 Tag」，别名 `UnifiedTag.aliases` **仅用于提示，不做强制归一**（`:17`）。
- `TagSearchPolicy.nativeTagSources = {ehentai, nhentai, hitomi, manga_dex}`（`:14`）：**"按源区分标签行为"在本项目已有先例**，L2a 的"源→主语言"表是同一思路的延伸。
- UI 层已有纯展示归一化：`searchVisibleTags` / `normalizeTagKey`（`SearchScreen.kt:1599-1613`），目前只去空白+小写去重。
- 源生字典 `JsComicSource.translate(text)`（`:59`）读的是 `s.translation['zh_CN']`，只服务探索页分类名，**与标签无关**，别混用。

### 1.4 数据层现状（决定能做什么）
- `Comic.tags: List<String>`（`source/model/ComicSourceModels.kt:12`）—— 扁平字符串，**没有命名空间**。
- `ComicDetails.tagMap: Map<String, List<String>>`（`:94`）—— 有命名空间。
- `JsComicSource.kt:449-467`：列表形态的标签被强行塞进 `tagMap["标签"]` → **对多数源，命名空间信息在解析阶段就已丢失**。

---

## 2. 用真实字典跑出来的量化结论

对 `assets/tags.json` / `tags_tw.json` 实测（不是估计）：

| 指标 | 数值 | 对方案的影响 |
|---|---|---|
| 字典总条目 | **34,956** | 覆盖面足够 |
| 唯一英文键 | 33,890 | — |
| 同一英文键在不同命名空间下**译法不同** | **403 条（1.2%）** | 现在的 `tagDictionary` 是"先到先赢"全局表（`TagTranslationManager.kt:71-73`）→ 这 1.2% **会译错** |
| 一个中文值对应**多个英文键** | **175 组** | **禁止**按中文译值反向合并，否则过度合并 |
| 简繁两版**键集合是否一致** | **完全一致**（`chinese → 汉语 / 漢語`） | 繁体是**drop-in 第二字典**，零映射工作量 |
| `language` 命名空间含 "chinese" 的键 | **只有 `chinese → 汉语` 一条** | ⚠️ 见下 |

**最重要的一条**：字典里**没有"简体中文 / 繁体中文"这种区分**，只有笼统的"汉语"。
而字典的键是**英文** → 中文。所以：
- 英文源（EH 家族）的 `chinese` → 显示"汉语" ✅ L1 能解；
- 中文源（JM / 哔咔等）的原生标签本来就是中文（"简体中文"、"大漫迷"…），在字典里**查不到 → 原样显示**，L1 无法把「简体中文」和 `traditional chinese` 认作同一个概念。

→ **你举的那个例子（简体/繁体/英文同义重复）落在 B 侧，需要 L2 的概念表，不是接一下字典就有。**

---

## 3. 能力边界（手册 §11 约束）

| 做法 | 判定 |
|---|---|
| 英文标签旁显示中文译名，请求仍发原文 | ✅ 允许（最小必要规范化，Dart 原版行为） |
| 把同一概念的多种**英文写法**折叠成一个药丸 | 🟡 有条件允许（只在键全局唯一译法时） |
| 按中文译值合并不同英文标签 | ❌ 禁止（175 组歧义 → 语义被改写） |
| 给源拼 `tagA OR tagB` 多词查询 | ❌ 禁止（多数源不支持 OR 语法 = 伪造能力） |
| 只合并已加载页的结果做"跨语言聚合" | ❌ 禁止（受分页限制，给出"看似全其实漏"的结果） |
| 按源的主语言，把同一概念翻译成**该源自己的词**再下发 | ✅ 这才是正解（L2a），因为每个源收到的都是它语言里的同一个概念 |

---

## 4. 方案

### L1｜显示统一（建议本轮做，纯 UI 层）

**规则**
1. **只翻译、不改写**：`VeneraTagChip` 收到的 `text` 变，`onClick` 携带的 `raw` 永不变。
2. **命名空间缺失即降级**：多数源没有 namespace（§1.4），所以查字典用「**全局唯一译法**」判据 —— 该英文键在全部命名空间里译法唯一才翻译；属于那 403 条歧义的，**保持原文**（宁可不译，不可译错）。
3. **折叠键 = canonical 英文小写键**，绝不用中文值折叠（175 组反向歧义）。
4. **显示形态**：沿用已有 `中文 (原文)` 双显（`SearchScreen.kt:299-301` 的样式），让原文始终可见 —— 既解决"看不懂"，也保证不制造"翻译后丢失原始信息"的新问题。
5. **繁体**：新增加载 `tags_tw.json`，按语言偏好选用；两版键一致，只需 `translate(tag, lang)` 加一层字典槽。

### 4.1 合并范围的定稿修正（重要：有一条做不到）

已定决策是「只合并字面同义」。字面同义里**只有两类真的能合并**：

| 重复类型 | 例子 | L1 能否合并 | 依据 |
|---|---|---|---|
| 大小写 / 空白 / 全半角差异 | `Full Color` vs `full  color` | ✅ 能 | 现有 `normalizeTagKey`（`SearchScreen.kt:1611`）已经覆盖，改折叠键即可 |
| 同一英文键带/不带命名空间 | `chinese` vs `language:chinese` | ✅ 能 | 折叠到 canonical 英文键；但**译法唯一**才做（§4 规则 2） |
| **跨脚本字面重复（简体字面 vs 繁体字面）** | 「简体中文」vs「簡體中文」 | ❌ **做不到，不在 L1 范围** | 项目里**没有任何简繁转换表**。`tags.json`/`tags_tw.json` 的键都是英文（§2），中文原生标签无法互相判定为同词。硬做只能新引入一张转换表 = 新资产依赖，超出 L1「不新增基础设施」的边界 |
| 语义同义但写法不同 | `chinese` vs `translated` vs 「汉语」 | ❌ 按决策**不合并** | 合并会削减可点的检索面词 = 功能损失 |

**并且这一条在现网 UI 上影响很小**：当前搜索页是**按源分区**渲染的（`aggregatedResults` 以 `sourceKey` 为键，`SearchViewModel.kt:37`），跨源标签本来就不在同一个列表里，所以"跨源简繁重复"在现在的界面结构下并不成组出现；用户看到的重复主要发生在**单个源的标签列表 / 搜索条件面板 / 标签联想**里，那里恰恰以大小写与命名空间差异为主 —— 前两类能解的就是主力场景。

→ 落地口径：**L1 只合并前两行**；跨脚本与语义同义一律各自成药丸，只是都换成中文显示 + 括号带原文。若你之后要求"简繁字面也必须合"，那必须先引入简繁转换表，按 L2 立项。

**接线点**
| 位置 | 现在 | 改后 |
|---|---|---|
| `TagTranslationManager` | 单字典，`translate()` 死代码 | 加简/繁双槽 + `uniqueTranslation()`（歧义返回 null） |
| `SearchScreen.kt:1093, :1153`（结果卡标签） | `text = tag` 原文 | 显示经 L1 处理；点击语义不变 |
| `ComicDetailScreen.kt:491, :506` | `text = tag` | 同上 |
| `UnifiedExploreScreen.kt:677` | 同上 | 同上 |
| `searchVisibleTags`（`SearchScreen.kt:1599`） | 按字面去重 | 折叠键换 canonical 英文键（只此一处即覆盖搜索页） |
| `VeneraTagChip.kt` | 薄封装，无逻辑 | **保持无逻辑**，翻译在调用侧的展示函数里做（避免组件内藏全局单例） |

**新增设置项**：「标签翻译显示」= 跟随系统 / 简体 / 繁体 / 关闭。**默认 = 跟随系统**（zh-Hans→简体、zh-TW/HK→繁体、其他→原文）。
沿用现成枚举偏好模式：`VeneraPreferences.kt:55, :182` + `AppearanceSettings.kt:24, :70`（`navigationBarStyle` 就是这么做的）。

**不做**：不新增 public 组件（用现有 `VeneraTagChip`）；不改 ViewModel / Source / JS；不改已稳定的 Dialog / Sheet Host。

### L2｜跨语言检索一致性（建议独立模块，另起一轮完整 9 阶段）

**2a（推荐路线）概念 → 各源表层词**
- 建一张**人工维护的小概念表**（先只覆盖高频的语言/格式/分类类标签，几十条量级，不是全量 34k），每条 = `{canonical, zh_hans, zh_hant, en, 别名[]}`。
- 建一张**源 → 主语言**表（几十个源，一次性人工标注，允许留 unknown）。
- 聚合搜索时：用户选一个概念 → 对中文源发中文词、对英文源发英文词、unknown 源发用户所选语言的词。
- 复用现有机制：`requestKeyword` 已经是"每源单独构造关键词"的形态（`TagSearchPolicy.kt:22-35`），且 `nativeTagSources` 就是同族先例 → **架构上不需要新增抽象**。
- 代价：两张表要维护；混合语言源判定不会 100% 准 → **必须在 UI 上标注"该词按各源语言分别匹配"，不可静默**。

**2b（否决）** 同义词并集/OR 语法查询 —— 伪造源能力。
**2c（否决）** 结果侧事后归并 —— 受分页限制，语义上伪造"搜全了"。

**L3｜诚实表达**：某源确实不认某语言词时，UI 标注该概念在哪些源有命中，而不是抹平差异。

---

## 5. 验收要点（L1，真机 QA 按手册 §7.3）

1. 英文源详情页/搜索结果标签显示 `中文 (原文)`，**点进去搜索结果与翻译前完全一致**（这条是回归底线）。
2. 那 403 条歧义键显示原文，不出现错译。
3. 中文源标签保持原样，未被强行翻译或折叠。
4. 设置切「简体 / 繁体 / 关闭」立即生效；关闭后与今日构建逐屏一致。
5. 系统语言为英文时默认不翻译；zh-TW 下显示繁体。
6. 长标签（`中文 (原文)` 双显后变长）在 360dp 窄屏不溢出不裁切。
7. Dark / Light 双模式下药丸对比度；字体放大一档。
8. 详情页、搜索结果卡、探索页、搜索联想四处渲染点表现一致。

---

## 6. 决策记录

**已定（2026-09-19）**
1. **折叠力度 = 只合并字面同义**。语义同义（`chinese` vs `translated`）一律保留为各自药丸 —— 理由：折叠会削减用户可点的检索面词，属功能损失，违反手册第一原则「视觉统一不得以丢功能为代价」。
   - ⚠️ 定稿修正：字面同义中**跨脚本（简↔繁）那部分实现不了**，项目无简繁转换表；L1 实际只合并「大小写/空白/全半角」+「同英文键带否命名空间」两类，详见 §4.1。
2. **默认语言 = 跟随系统**，且系统语言同时决定"译不译"与"译成什么"：zh-Hans→简体、zh-TW/HK→繁体、其他语言→**不翻译，直接显原文**。设置里可手动覆盖为 简体 / 繁体 / 关闭。
3. **L1 先做，L2 不立项**（本轮范围仅方案文档，未动代码）。

**仍待你定（不阻塞 L1 起步）**
4. ~~是否始终双显原文~~ → **实施时定：`中文 (原文)` 常驻双显**。理由：1.2% 歧义键与中文站标签都要退回原文，若同时隐藏原文，用户无从判断这枚药丸对应源里的哪个词；且仓库既有模式就是双显（`SearchScreen` 联想药丸）。
5. **L2a 若日后立项**：需允许新增「概念表」+「源→主语言表」两张资产，并改 `TagSearchPolicy` 的每源关键词构造 —— 碰请求语义，按手册必须独立走完整 9 阶段。

---

## 8. L1 实施记录（2026-09-19）

**改动面：5 改 + 1 新增 + 1 测试文件。全部落在显示层 / 偏好 / 字典，未碰 ViewModel、Source、JS、Network、Reader、Navigation、Dialog-Sheet Host。**

| 文件 | 内容 |
|---|---|
| `data/tags/TagTranslationManager.kt` | 单字典 → 每语言 `LangDict` 三视图（`scoped` / `unique` / `flat`）；`unique` **排除**跨命名空间异译键；新增 `displayLabel(tag, language)`、`ensureLanguage`、`loadedLanguages: StateFlow<Set<String>>`；繁体按需加载，简体仍构造即加载（联想依赖）。删除零调用的 `isLoaded` |
| `data/tags/TagDisplay.kt`（新增） | `rememberTagDisplayLabel(): (String) -> String` —— 解析显示语言、触发字典加载、输出 `译文 (原文)`；字典未就绪 / 无译文 / 译文与原文同名时一律返回原文 |
| `data/prefs/VeneraPreferences.kt` | `TagTranslationMode { SYSTEM, SIMPLIFIED, TRADITIONAL, OFF }` + flow + setter + `pref_tag_translation_mode`，默认 `SYSTEM` |
| `feature/SearchScreen.kt` | 屏幕顶层取一次 labeler 并下传 5 个私有签名；`searchVisibleTags` 折叠键换成概念键、保留带命名空间的存活者、应用译文 |
| `feature/ComicDetailScreen.kt` | 命名空间分组与扁平标签两处 `text = tagLabel(tag)`，`onClick` 仍传原词 |
| `feature/settings/AppearanceSettings.kt` | 新增「标签」组：`标签译文显示` 四态 + 说明文案 |
| `app/src/test/.../SearchVisibleTagsTest.kt`（新增） | 6 条锁定折叠口径 |

**实施中对方案的两处收窄**
- `UnifiedExploreScreen.kt:677` **不改**：那里的 `tag.label` 是 `UnifiedTag` 里硬编码的应用级中文词（"长篇/短篇"），不是源生标签，无译文可加。
- `SearchConditionSheet` **不改**：它已经是 `label（raw）` 双显。
- 因此 §7 第 8 条的"四处渲染点"实际为**三处**：详情页、搜索结果卡（网格 + 列表）、搜索联想。

**仍未覆盖的一处（需你豁免）**
`FavoritesScreen.kt:672` 本地收藏卡标签仍显示原文。该页已 FROZEN 且不在批准的接线表内，**未擅自改动**；要统一只是一个 labeler 调用的事。

**顺带发现的既存缺陷（未修，待评审）**
`TagSearchPolicy.normalizeTagValue`（`feature/TagSearchPolicy.kt:38-39`）写作
`tag.trim().lowercase().substringAfter(':', tag.trim())` —— 标签**不含冒号**时 `substringAfter` 的 missingDelimiterValue 回退的是**未小写**的原始串，于是该助手只在带命名空间时才大小写无关。它参与 `matchesTagFilter` 的**结果筛选**，改它等于改变搜索命中（`JK` 与 `jk` 之间会新增匹配），所以本次在展示层另写修正版 `normalizeTagKey`，没有动它。若要修，应作为独立行为变更走评审 + 真机回归。
（这条是新增单测跑出来的：`Full Color` / `full color` 当时并不折叠。）

**Build QA**：`:app:compileDebugKotlin`、`:app:testDebugUnitTest`（68 项全通过，其中新增 6 条）、`:app:assembleDebug` 三项 **BUILD SUCCESSFUL**；`app-universal-debug.apk` 已重建。真机 QA 待设备进行（本会话 `adb devices` 为空）。


