# 设置 → 屏蔽与过滤：新增「屏蔽 AI 生成漫画」—— 方案

用户原话（2026-09-22）：

> 在设置 屏蔽添加一个选项，可以直接屏蔽所有源的AI生成的漫画

## 一、master 有没有可照的？

**没有这个功能。** master 只在卡片上挂 AI 角标，判据是（`lib/components/comic.dart:485-487`）：

```dart
comic.tags?.any((t) => t.toLowerCase() == 'ai' || t.toLowerCase() == 'ai-generated')
```

即**只看 tags、精确等值、小写归一**，不扫标题也不扫描述。所以这条是新功能，
能照的只有 master 那条"判据形状"。

## 二、仓库里已有的现成口径

| 可复用物 | 位置 | 内容 |
| --- | --- | --- |
| master 的角标判据 | 上表 | tag ∈ {`ai`, `ai-generated`} |
| 应用内 AI 词表 | `data/tags/TagNormalizer.kt:84` | `ai生成`、`ai绘图`、`ai-generated`（连同"同人/短篇/漫画/杂志"一起被列为**格式/元信息类标签**，即"不是题材"） |
| 屏蔽判定链 | `security/guard/ContentGuardManager.kt` | 用户规则(KEYWORD/TAG/AUTHOR/COMIC_ID) → 源级预设 → 成人词正则 → 默认 safe |
| 命中后的处理模式 | 同上 + `feature/ContentGuardScreen.kt:113` | `OFF 不过滤` / `BLUR 封面打码` / `HIDE 彻底隐藏` |
| 落库位置 | `ContentGuardManager` 的 `venera_guard_prefs` | 与 `nsfw_mode` 同一份 SharedPreferences |

**不复用「加一条 TAG 用户规则」的做法**：那样开关只是往规则表里插/删一行，
用户会在规则列表里看到一条莫名出现的 `ai` 并能误删，且命中面只有精确 `ai` 一种写法。

## 三、必须先讲清的数据天花板（否则就是假开关）

判定依赖**源是否把 AI 标签交上来**。已实测过的两件事（见项目记忆）：
jm 的卡片只给分类词、`description` 为空；搜索总数 0/33 个源提供。
所以"屏蔽所有源"诚实的表述是"屏蔽**所有暴露了 AI 标记**的源"。

落地后要**逐源实测**：在探索/搜索里对每个源数一遍"带 AI 标签的条目占比"，
把"哪些源根本没有这个标签"写进文档，而不是让用户以为全都能屏蔽。
这一步没做完之前，本条不算验证通过。

## 四、待定（用户拍板）

1. **判据范围**：甲 = 只 master 那两个精确 tag；乙 = 甲 + `TagNormalizer` 已有的中文 AI 词；
   丙 = 乙 + 扫标题/描述（会误伤 "AI少女"/角色名/"A.I."，master 没这么做）。
2. **命中后怎么处理**：直接彻底隐藏；还是跟随现有 R18 的遮蔽模式（OFF/BLUR/HIDE）。
   注意语义差别：AI 漫画未必是成人内容，"打码"对它其实说不通。

## 五、改动面预估

| 文件 | 改什么 |
| --- | --- |
| `security/guard/ContentGuardManager.kt` | 加 `blockAiComics: StateFlow<String/Boolean>` + 判定链里一步 AI 命中（预编译，判定在列表滚动每帧都跑，绝不在调用点 new Regex） |
| `feature/ContentGuardScreen.kt` | 加一张卡（图标 + 标题 + 副标题 + 开关/模式），与现有 R18 卡同构 |
| 判定链的四个出口 | 确认 `filterComicModels` / `filterExploreParts` / `coverMaskStateFor` / 详情页都吃到这一步 |

不动：源层脚本、ViewModel、`Navigation.kt`。

## 六、已拍板并落地（2026-09-22）

拍板：**判据走乙**（tag 等值命中 `ai` / `ai-generated` / `ai生成` / `ai绘图`）；**命中即彻底隐藏**，
与 R18 的三档模式互不干扰。

| 改动 | 位置 |
| --- | --- |
| 开关状态 | `ContentGuardManager`：`blockAiComics: StateFlow<Boolean>` + `setBlockAiComics()`，存 `venera_guard_prefs` 的 `block_ai`，与 `nsfw_mode` 同一份 |
| 判据 | 同文件顶层 `AiTagKeys`+`isAiTagValue()`、`AiTitlePatterns`+`isAiTitleMarked()`（都是文件级纯函数、可单测），合成 `isAiMarked(title, tags)`：标签先过 `ChineseVariantConverter.traditionalToSimplified` 再等值比对，**或**标题命中预编译词组 |
| 物理剔除 | `filterComicModels` 加 `aiHide` 一路，并把它补进 `filterExploreParts` 的早退守卫 |
| 卡片兜底 | 三个 `coverMaskStateFor` 重载都在 `"OFF"` 早退**之前**判 AI 并返回 `HIDDEN` —— 否则关掉 R18 遮罩会把 AI 屏蔽一起关掉（一个开关管两件事 = 假开关） |
| 设置页 | 先加在 `ContentGuardScreen`，随后该页整体取消 —— 现落在 `feature/settings/BlockingSettings.kt` 的「内容守卫」分组（`SettingsToggle`），见 §八 |
| 顺带修的真洞 | `explore/SourceSectionScreen.kt` 原先把过滤调用本身 gate 在 `nsfwMaskMode == "HIDE"` 上 —— R18 模式为 OFF 时该页**根本不执行任何过滤**（用户规则也失效）。已改为交给 `filterComicModels` 自己分流，并把 `blockAi` 加进它的重放 `LaunchedEffect` key |
| 单测 | 新 `security/guard/AiTagPredicateTest.kt`：正例（master 两词 + 中文两词 + 大小写）、反例（`AI少女`/`openai`/`ai generated`/`air`/`waiting`/空/`Tag:`）、以及**命名空间不剥**（`female:ai` 不命中，避免误伤角色名 AI） |

一处与初稿的判断差异：原打算照用户规则那条约定剥 `female:` / `Tag:` 前缀，写测的时候发现
`female:ai` 是角色名 AI 的常见写法，剥前缀等于误伤一整本正常漫画，而 master 的角标判据
本来就是**整串小写等值**。所以收紧成不剥前缀，代价是带前缀的写法会漏（漏比错杀安全）。

## 七、还没做完的（不算验证通过）

1. **开关只影响"之后的加载"**：`filterComicModels` 是在 ViewModel 取数时跑的，
   `invalidate()` 只清判定缓存、不会让**已显示的列表**重筛。所以切开关后当前这一屏不会立刻变，
   要下拉刷新或重新进页。这是**既有 R18 模式切换就有的行为**，不是本条新引入的；
   目前只有 `SourceSectionScreen` 有重放 effect。要做到"一开就掉"需要给 Search / Home /
   UnifiedExplore 三个 ViewModel 加 epoch 重筛 —— 那是保护域，等点头。
2. **逐源实测命中面**：还没数过"每个源到底有多少条目带 AI 标签"。
   已知 jm 卡片只给分类词，所以很可能部分源命中率为 0。没做完这条之前，
   不能对用户宣称"屏蔽所有源"。

## 九、追加：判据扩到标题（同日，用户实测带出）

用户拿 e-hentai 搜 `ai generated` 的截图反馈「直接标题也禁吧」—— 那批结果标题是
`RENZEN 3 [AI Generated]` / `[Only4u AI Art] ...`，而 **tags 里没有 ai**，
纯标签判据整批漏掉。所以补了标题判据。

刻意**不匹配裸 `ai`**，只用两类形状：

| 形状 | 模式 | 命中例 |
| --- | --- | --- |
| 词组 | `ai[\s\-_]?generated`、`ai[\s\-_]?(生成\|绘图\|繪圖\|作画\|作畫)`、`ai[\s\-_]?art\b` | `AI Generated`、`AI-Generated`、`AI生成`、`AI繪圖`、`AI Art` |
| 括号独立词 | `[\[【(（]\s*ai\s*[\]】)）]` | `[AI]`、`【AI】`、`(AI)` |

反例（单测钉住，必须不命中）：`AI少女与战车`（角色名）、`openai 教程`、`waiting for you`、
`Maid in the air`、裸标题 `RENZEN 3`。

标题判据**不过繁转简表**（那是逐字符查表，跑在每帧的列表判定上不划算），
所以简体/繁体两种写法直接写进模式里。

踩到的坑：中文与括号两条模式**漏加 `RegexOption.IGNORE_CASE`**，于是大写 `AI生成`、`(AI)`
全部不命中 —— 单测两条 assertTrue 直接失败才发现。英文词组那两条当时加了，所以过了。

## 十、逐源可达性审计 + 两个真实漏洞（同日，用户「你全部源过一次」带出）

用户截图：禁漫天堂搜 `ai generated`，仍见 `[個人AI漢化] [Serge3DX] Milf Breeder Chapter 6`、
`賺錢的方法 ② … [AI翻譯]`。逐条查下来结论分三层，**不是同一个原因**。

### 10-1 截图那两条是刻意不命中的，不是漏

`AI漢化` / `AI翻譯` 标的是**翻译工具链**用 AI，不是画面用 AI 生成。收进判据会连带屏蔽
整批人工作画、只是 AI 嵌字的正常漫画 —— 误杀面远大于收益（用户零容忍的那一类）。
已用单测钉死：`aiTranslationMarkersDoNotHit`。第一条 `有村麻央堕入BBC` 根本没有 AI 字样。

### 10-2 标签判据在多数源**结构上不可达**（这才是"屏蔽所有源"的真实边界）

判据吃的是**卡片**的 `tags`。逐源读 `assets/sources/*.js` 的卡片构造，实测口径：

| 卡片 tags 是什么 | 源 | 标签判据 |
| --- | --- | --- |
| 真·内容标签 | ehentai(`div.gt/gtl` title :336)、manga_dex(`attributes.tags` :47)、copy_manga 与 hot_manga(`theme[].name`)、picacg(`tags`+`categories` :93)、comic_walker(`comic_labels`)、hcomic(`c.tags`)、manhuaren、happy(`genre_ids`) | **可达** |
| 只有分类词 | jm（`category` / `category_sub` 的 title，:211-221）、baozi(`type_names`)、jcomic(分类按钮文本)、komiic | **恒不命中** |
| 卡片未设 tags | 其余多数源（含 html-builder 型） | **恒不命中** |

关键旁证：**master 的 AI 角标读的是同一个字段**（`comic.dart:485-487` `comic.tags`），
所以 master 在 jm 卡片上同样不显示 AI 角标 —— 不是本仓接漏，是上游卡片数据没有。
内容标签只在 `getComicDetails` 里（jm.js:811 的 `"Tag"` 组），要为每张卡发一次详情请求，不划算。

→ 所以 jm 这类源上，AI 屏蔽**只剩标题判据**这一条腿。

### 10-3 顺手钉住的分级事实

`assets/source_content_warning.json` 实读：33 源 = nsfw 9（jm, picacg, mh18, hcomic, wnacg,
nhentai, hitomi, hot_manga, jcomic）+ mixed 2（ehentai, mxs）+ safe 22。
即 R18 遮罩设成 BLUR 时，**jm 的整页结果必须逐张打码**（源级预设直接命中）。
截图里没打码 = 当时模式是「不处理」，与 AI 开关无关。

### 10-4 修掉的两个真实漏洞（`"HIDDEN"` 被映射成"不打码"）

`coverMaskStateFor` 对 AI 命中一律返回 `"HIDDEN"`（与 R18 三档无关）。但两个消费点只认 `"BLURRED"`：

| 位置 | 原判定 | 后果 |
| --- | --- | --- |
| `components/ComicTileLayout.kt:77`（detailed 单列封面） | `== "BLURRED"` 才 blur | 探索页/二级页**单列**模式下 AI 命中条目完全不打码，而同页**双列**是打码的 —— 同一列表两种显示模式互相矛盾 |
| `feature/SearchScreen.kt:135` | `== "BLURRED"` 才 Masked | 搜索结果里 `"HIDDEN"` 被映射成 `Visible`，与其余各页（非 VISIBLE 一律 Masked）口径相反 |

两处都改成「非 `"VISIBLE"` 一律打码」。这两处只在「列表已加载后才拨开关」时才会露出来
（正常路径下条目已被 `filterComicModels` 物理剔除）—— 也就是挂着的那条「即时生效」缺口的遮羞布。

### 10-5 新增客观读数：逐源剔除日志

`filterComicModels` 有剔除时打一行 `VeneraGuard`：`剔除 n/总数 mode=… ai=… rules=… {源键=条数}`。
只记源键与条数，不落标题到 logcat。这样"全部源过一次"变成可核对的读数而不是观感。

### 10-6 仍未做完

1. 拨开关对**已加载列表**不即时生效（要动 SearchViewModel / HomeViewModel / UnifiedExplore 三个保护域）。
2. 真机/模拟器逐源实跑一遍 10-5 的日志（静态审计不等于设备验证）。
3. 若要给"只有分类词的源"加判据，可选路径是**标题里补常见 AI 工具名**
   （NovelAI / Stable Diffusion / Civitai / Midjourney），误杀面低但需要用户点头。

## 十一、探针实测：jm 列表接口到底有没有 tags（同日，用户「你看下能不能读到禁漫的tag」）

§十 里「卡片接口没有 tags」是我**从 `parseComic` 没读它推断**的，不够硬。改成实测：

- 探针方式：源脚本运行期读的是 `files/comic_source/` 副本，且默认清单只在缺失时落盘
  （`ComicSourceManager:282` `if (name in installedFiles) continue`），所以直接给副本打补丁就是真正跑的那份；
  `venera-init.js:991` 的 `console.log` 直通 logcat。
- 在 `parseComic` 开头插一行 `console.log("JM-PROBE album-keys=" + Object.keys(comic).join(","))`
  （只记字段名，不落内容到 logcat），用户跑一次禁漫搜索。
- 实测返回：`id, author, description, name, image, category, category_sub, liked, is_favorite, update_at, adddate`

**结论：接口确实没有 tags 字段。推断被证实，但现在是读数而不是推断。**
→ jm 的卡片级标签判据**结构性不可能**，不是实现漏了。master 的 AI 角标读同一个字段，同样不可能。

顺带记下：`description` 这个键**是存在的**（此前项目记忆写的是「jm description 实测为空」，
键在 ≠ 值非空，本次未探值）。

还原核对：设备副本 / 备份 / 仓库 `assets/sources/jm.js` 三者 md5 同为 `ab05dd4f…`，探针残留 0 条，
`/data/local/tmp` 临时文件已删。

### 11-1 那要不要走「详情指纹」？

详情接口 `jm.js:774 /album?id=` → `data.tags` → `tagMap["Tag"]`，截图那本里面就有 `AI繪圖`。
所以「看过即拉黑」是可行的：详情加载后判一次，命中就把 `(源键, 作品号)` 记进小表，
之后所有列表按这张表剔除。零额外请求、判据吃的是真标签（不会因标题写法误杀）。

**必须先解决的一个坑**：详情侧标签到 Kotlin 是 `ComicDetails.plainTags`，形状是**带命名空间**的
`Tag:AI繪圖`。而 [isAiTagValue] 刻意不剥命名空间（剥了会把 e-hentai 的 `female:ai` 角色名算成 AI 生成）。
所以指纹判据**不能**简单 `substringAfter(':')`，要过 `TagNormalizer.normalize()` ——
它已经有 `EXCLUDED_NAMESPACES` / `TOPIC_NAMESPACES` 的现成分组知识
（`docs/rounds/tag-statistics-port-2026-09.md` §8.2 逐源列过 jm 存活组 = `Tag` / `Actor`）。

代价说清楚：**没点开过的那一次仍会露出来**。要全覆盖只能等源给卡片标签（jm 不给）。
