---
name: project-jm-card-tags-upstream-limit
description: 禁漫天堂(jm) 卡片只有 1-2 个分类词是上游脚本+接口的天花板，非迁移缺陷；description 真机抽样为空，话题抽取已判死，别再重开这条调查
metadata:
  type: project
---

禁漫天堂（key `jm`）**搜索/列表卡片上只有 1-2 枚"标签"，且它们是分类词不是标签**。这是上游限制，不是 Compose 端口丢逻辑：

- 设备上运行的官方 `files/comic_source/jm.js` 与仓库 `assets/sources/jm.js` **逐字一致**（2026-09-19 diff 过，差异仅我们自加的崩溃守卫）；官方 `parseComic` 本身就只 push `category.title` + `category_sub.title`，硬上限 2。
- 真标签只存在于**详情页**（`/album?id=` → `data.tags` → 分组键 `Tag`），卡片拿不到。用户 2026-09-22 拿详情页截图（某本标着 `AI繪圖`）确认过这条通道是通的：Kotlin 侧 `ComicDetails.tagMap` → `plainTags` 能吃到。
- **已实测定论（2026-09-22 探针）**：jm 列表/分类/搜索接口的 album 对象字段就是
  `id, author, description, name, image, category, category_sub, liked, is_favorite, update_at, adddate`
  —— **没有 tags 字段**。所以卡片级标签判据在 jm 上是结构性不可能，不是我们实现漏了；
  master 的 AI 角标读同一个字段，同样不可能。探针做法见 [[project-js-source-assets-not-live]]，
  设备副本已按 md5 还原成与仓库逐字一致。
- 真机 Kotlin 探针抽样：`descLen=0`、`descHasHash=false` —— 该作品 `description` 为空（详情页也显示"暂无详细简介"），所以**"从 description 抽 `#话题` 补卡片标签"这条路判死**。
- 唯一还能**增加卡片标签**的办法是逐条拉详情 = 一页 80 条 80 个请求，已明确否决。
- 但"否决逐条拉详情"只针对**卡片显示**。若目的换成**屏蔽**，还有一条不需要额外请求的路：详情加载后用真标签判一次、命中就把 `(源键, 作品号)` 记进小表，之后所有列表按这张表剔除（看过即拉黑）。见 [[project-content-guard-ai-blocking]]。

**Why:** 用户连续三轮追问"禁漫天堂卡片 tag 太少/显示有问题"，容易让人以为是我们改坏了或还能修。实测下来它是站点接口 + 官方脚本的天花板，继续投入只会浪费轮次。

**How to apply:** 再遇到"某源卡片标签少"的反馈，先按 [[project-js-source-assets-not-live]] 的办法把设备副本与仓库 diff 定性（端口丢失 vs 上游限制），再用一次性 Kotlin 文件探针取真实字段，**不要凭猜测改源脚本**。已确认的 JM 详情页显示缺陷走 UI 层收口（英文组名 `Author/Work/View` 字典查不到就原样显示；纯数值组如 `View: 3132` 保留展示但取消点击），不改源数据。

## 2026-10-04 复查（用户第二次报，结论不变，补当日实时读数）

用户附截图再报一次「禁漫漫画列表只有两个 tag 显示」，并连上 adb 授权直接读取。走 CDP 路径 B
在活引擎里给 `parseComic` 包一层抓原始 album 对象，实时读数：

- 接口 album 字段仍是 `id, author, description, name, image, category, category_sub, liked,
  is_favorite, update_at, adddate` —— **依旧没有 tags**。
- tag 数分布（`search.load('無修正',['mr'],1)`，80 条）：**1 枚 24 / 2 枚 56 / ≥3 枚 0**。
- **新发现：42/80（52.5%）条目 `category.title == category_sub.title`**（如 `["同人","同人"]`），
  卡片会画出两枚一模一样的 chip。官方同样不去重 —— 这是唯一"能改"的点，但去重后这些卡从 2 枚变 1 枚、
  观感更少，**必须用户拍板**，别自作主张。
- `description` 非空 **0/80** ⇒ 该通道再次判死。
- 设备 `files/comic_source/jm.js` md5 = 仓库 md5 = `ab05dd4f11500ff18ab46b14f9b755e0`。

**四个可选方向**：A 维持现状（=官方）｜B 卡片标签去重｜C 详情反哺小表（打开过的作品存真标签，
卡片下次显示，零额外请求但只覆盖看过的）｜D 逐条拉详情（80 请求/页，已否决）。

**How to re-verify 不必再问人**：CDP 路径 B 已实测可用（本轮一次求值即得字段清单），
做法见 skill `venera-source-js-debug`「抓源侧原始接口字段」一节。

### 2026-10-04 补：禁漫的两条数据通道（用户要求「先搞清楚到底拿到了什么」）

**① 列表 / 搜索接口**（`/search`、`/categories/filter`，一页 80 条）—— 逐字字段：
`id, author, description, name, image, category, category_sub, liked, is_favorite, update_at, adddate`
只有两个**分类**字段，且**共用同一个 id 值域**（采样 960 条，只出现 6 个值）：

| id | title |
|---|---|
| 1 | 同人 |
| 2 | 單本 |
| 3 | 短篇 |
| 4 | 其他類 |
| 5 | 韓漫 |
| 6 | English Manga |

⇒ 卡片上那 1~2 枚 chip 说的是「这份作品归在哪个分区」，**不是内容标签**；
两字段同值域 ⇒ 同名概率高（实测 **52.5%** 的条目完全重复）。

**② 详情接口** `/album?id=` → `data.tags`，5 个命名空间 + 实测样本：

| 作品 id | Author | Tag | Work | Actor | View |
|---|---|---|---|---|---|
| 1478844 | TsarAi | AI繪圖, 巨乳, 中出, 中文, 巨乳, 口交, 足交, 中出, 接吻, 校服, AI繪圖, 無修正, 中文 | 哈利波特 | — | 747 |
| 1478833 | あぶぶ | 全彩, 巨乳, 無修正, 中文, CG集, 巨乳, 奶牛娘, 乳汁, 熟女, 怪物女孩, 肌肉, 中出, 修女, 母女丼, 援交, 中文 | — | — | 1150 |

⇒ 真标签（`AI繪圖` / `無修正` / `巨乳`…）**只在这条通道**。
另注：详情侧 `Tag` 组**自身也带重复项**（巨乳/中出/中文 各出现两次），去重要在 Kotlin 侧做。
Kotlin 侧 `ComicDetails` 的字段名是 **`tags`**（不是 `tagMap`）。

### 2026-10-04 决定：禁漫「卡片显示真标签」增强 —— **不做**（用户拍板）

用户问「能不能单独给禁漫这个源拉更多详情通道的标签放到列表/搜索」，并给了网页版路径
`18comic.vip/search/photos?search_query=…` 让我研究官方怎么做。代价/收益实测完毕后，
**用户决定不做**，维持现状（= 官方 Venera 行为）。本轮**零代码改动**。

**已测定的关键数据（下次别重测）**：
- 唯一真标签通道 = 逐本 `/album?id=`；列表系接口（`/search` `/categories/filter`
  `/week/filter` `/promote` `/favorite`）payload 均无 tags。
- 单本 `comic.loadInfo` **505 ms**；**32 并发 3.07 s、32/32 成功、0 失败**（≈96 ms/本）
  ⇒ 禁漫**未限流**。
- 真标签 **4~21 枚/本**，且详情侧 `Tag` 数组**自带重复项**。
- 卡片 chip 上限 `tags.take(10)` / 3 行（`components/ComicTileLayout.kt:179`）。

**若将来重启这条线，三条硬约束必须先解决**：
1. 卡片必须截断（只取 `Tag` 命名空间 + 去重 + 前 N 枚），否则撑爆卡片。
2. 改 `assets/sources/*.js` **只对全新安装生效**：`ComicSourceManager.bootstrapBundledSources`
   只写「本机没有的 fileName」，`KEY_BOOTSTRAPPED` 是一次性标记 ⇒ 覆盖安装 APK **不重拷**。
   交付只能走 in-app「源管理 → 编辑/粘贴」或清数据/重装。
3. 上游更新会覆盖本地：jm.js 声明 `url=cdn.jsdelivr.net/gh/venera-app/venera-configs@main/jm.js`，
   `updateSource` 直接拉官方版覆盖；更新角标靠 `compareVersion(remote, current) > 0`
   ⇒ 改内置源必须 **bump `version`**。
- 推荐方案（若做）= **缓存优先 + 后台限流回填**：`search.load`/`categoryComics.load` 先读
  `loadData('tagCache')` 覆盖 `c.tags`，未命中者进后台队列按并发 6 拉详情、**批量写一次**
  `saveData`（`JsSourceDataStore` 落 `files/comic_source/jm.data`，每次全量重写 ⇒ 禁逐条写）。
- 曾评估并否决：A 阻塞预取全页（80 请求/页，拖垮聚合搜索）｜C 只在打开详情时记（覆盖太低）。
- 结论定性：这是**与官方 Venera 的有意分歧**，不是缺陷修复。

**工具坑（本轮踩到，根因未定位）**：用 CDP 直连 App 内活 WebView 去请求**外部站点**
（`Network.get('https://18comic.vip/...')`）时**挂死 >4 分钟不返回**；之后连最小探针
（`return 1+1`）也不再出结果（`venera_cdp.txt` 未生成），疑似把 devtools 通道占死。
⇒ **用 CDP 探针时：凡涉及源侧网络请求，表达式里必须加 `Promise.race` 超时保护；
怀疑通道被占死后先 `adb forward --remove-all` 重开再试。**
仅求值**内存内对象**（本轮 e5/e6 给 `loadInfo` 计时）则完全正常、秒回。
