# 画廊「按收藏推荐」（For You）方案 —— 2026-09-27

参考实现：[breadboardapp/breadboard](https://github.com/breadboardapp/breadboard)（Kotlin/Compose，
覆盖 Gelbooru / yande.re / Danbooru / Safebooru / Rule34，M3 Expressive）。
本文所有对它的引用都是**源码原文**，浅克隆在 `C:\Users\leimi\AppData\Local\Temp\bb`，
核心两文件：`app/src/main/java/moe/apex/breadboard/util/Recommendations.kt`（169 行）与
`util/RecommendationsHelper.kt`（224 行）。

## 〇、这份文档要定什么

**目标**：把画廊落地流从"两站热门池 + 打乱"升级成"能被用户收藏影响的一屏"。

**Scope（本轮只做设计与切分，不写实现）**：
1. 参考实现的机制讲清楚（§一）；
2. 逐条判定哪些在我们这边**照搬得了 / 照搬不了**，用代码与实测为证（§二、§三）；
3. 算法与接线点定形（§四、§五）；
4. UI 摆法给三个选项并推荐（§六）；
5. 与既有裁决的冲突点全部列出来（§七）—— 这一节是本文档最有价值的部分。

**Stop Condition**：§十 的七条拍完，就可以直接按 §九 的切分开工，不需要再回来问一轮。

## 一、Breadboard 的 For You 到底是什么

**一句话：它不推荐"图"，它推荐"标签"。** 从用户自己的收藏里挑出 N 枚标签，
然后把这 N 枚拼成一次**普通的站方搜索**。没有服务器、没有模型、没有向量、没有隐式反馈。

链条（行号都是它的源码）：

| 步 | 做什么 | 出处 |
|---|---|---|
| 1 | 种子 = 本地收藏，**先按当前站过滤**（`it.imageSource == imageSource`）+ 按分级过滤 | `Recommendations.kt:67-69` |
| 2 | 收藏的 tags 平铺、小写、去掉黑名单与未关注 → 词频 `groupingBy{}.eachCount()` | `RecommendationsHelper.kt:90-96` |
| 3 | **候选池 = 词频前 poolSize 名**（默认 7，滑块 1..20，设置项叫 "Maximum frequent tags"） | :117-121 |
| 4 | 从池里**加权随机**抽 1 枚当 `primaryTag` | :129 |
| 5 | 循环：对每个剩余候选，数它**与已选标签集在同一张收藏图里共现的次数**，按共现数加权随机抽下一枚，直到抽满 selectionSize（默认 3，"Search size"，1..5）；共现全 0 就提前 break | :134-160 |
| 6 | 权重 = `词频 × 分类权重`：`copyright 3.5 > character 2.0 > artist 1.5 > general 1.0`，**`meta` = 0.0（直接排除）** | :196-207 |
| 7 | 拿这串标签走**搜索那条路**取图：`imageBoard.loadPage(tags=…, page=n)`；翻页 = `onEndReached` 时 page+1 | `Recommendations.kt:125`、`HomeScreen.kt:417` |
| 8 | 分级：站方支持服务端过滤就拼进查询串，不支持就**本地过滤**；**黑名单永远本地过滤** | :100-140 |
| 9 | 该站没收藏 → `recommendedTags` 为空 → 明说"去 X 站收藏几张才会有个性化推荐"，**不拿热门池顶包** | `RecommendationsSettingsScreen.kt:207-213` |
| 10 | 改任何相关设置 → `resetProviders()` → 重建、重算 | `HomeScreen.kt` 多处 |

另有两条同族能力：`getMostCommonTags`（给设置页展示"你的高频标签"）与
`getRecommendedArtists`（按画师词频推荐关注的画师，:170-184）。

**为什么共现是精髓**：只按词频抽，得到的是 `1girl solo long_hair` 这种"每张图都有"的废组合；
要求"这几枚在同一张收藏里真的一起出现过"，抽出来的标签集才定位得准"你喜欢的那一类"。

## 二、逐条判定：哪些我们照搬得了

| 参考实现依赖的东西 | 我们有没有 | 证据 |
|---|---|---|
| 收藏条目带 `tags` 串 | **有**，且离线可得 | `GalleryFavorite.tags`（`GalleryFavoritesStore.kt:42`），存的是整条真实数据而非 id |
| 收藏条目带 `site` | **有**，且"认不出站点键就丢掉那行" | 同上 :64-65、:213 |
| 标签切分 | **有**，`splitGalleryTags` 就是按空格拆（无漫画侧旧切分器那个包袱） | `GalleryPost.kt:204-205` |
| 拼标签串、翻页、判到底 | **全部现成**，不必新写取数 | `GallerySearch.queryOf` :68、`isExhausted` :81、`YandeReClient.searchPosts` / `GelbooruClient.searchPosts` |
| 标签数预算 | **两站都不限**（实测），所以拼 3~5 枚没有风险 | `GallerySearch.tagBudget` :47-50 恒 null |
| 黑名单 / 分级过滤 | **有，且已有统一判据** | `ContentGuardManager.findGalleryBlockedRule(author, tags)`、`GalleryGuard.maskStateFor` |
| "能不能摆上屏"的滤条 | **有**（扩展名白名单 + 缩略图非空） | `GalleryMerge.isDisplayable` :58-59 |
| "装好条件并一步开搜"的入口 | **有**，且已被跨 Activity 交接验证过 | `GallerySearchViewModel.acceptHandoff(site, tags)` :264 |
| **标签的分类（copyright/character/artist）** | **拿不到** —— 见下面这段 | |

### 2.1 分类权重这一条**照搬不了**（这是硬数据面，不是我偷懒）

Breadboard 权重表里最值钱的三档（copyright 3.5 / character 2.0 / artist 1.5）依赖
**每张图的标签是分好类的**。而我们这边：

- yande.re 的 post JSON **44 个键里没有任何分类字段**（不含 `tag_string_*`）—— 实测，
  见 `YandeReDto.toPost` 的注释（`YandeReClient.kt:226-228`）；
- Gelbooru 也只有一串平铺 `tags`（`GelbooruClient.kt:408-409`）；
- 所以 `GalleryPost.tagGroups` 两站**都只能一桶**「标签」，这是站方天花板，
  我们历史上还专门写过"不硬造通用/角色那种假分组"。

**但分类信息在 tag 端点上有**：两站的 `s=tag` 都给 `type`（数字命名空间两站一致：
0 通用 / 1 画师 / 3 作品 / 4 角色 / 5 元数据），见 `GalleryTag.kt:19-25、32`。
所以想要分类权重，只有一条路：**对候选池的每一枚标签各查一次 tag 端点**。

代价要算清：tag 端点**不能批量精确查**（Gelbooru 的 `name_pattern` 是 SQL LIKE 前缀语法、
yande.re 是 `name=词*`），所以池子 7 枚 = **7 笔请求**。冷启动多 7 笔，
而我们现在给日榜每站的预算是 12s（`PER_SITE_TIMEOUT_MS`，见 `GalleryFeedSource`）——
这 7 笔要么串行拖慢，要么并发吃限流。**要不要为它付这笔，是 §十 的第 3 问。**

不给分类权重会损失多少？诚实说：**不知道**，得实测。所以 §九 的切分里，
"纯词频 + 共现"是第一期，分类权重单独一期，出问题时能二分。

### 2.2 画师信号有替代来源（免费）

`GalleryFavorite.author` 存着（yande.re 取站方 `author`、Gelbooru 取 `owner`，
`GelbooruDto.toPost:387`）。Breadboard 的 `getRecommendedArtists` 靠 `metadata.artists`，
我们直接有 author 字段 —— 这条**比它更便宜**。
⚠️ 但要注意 Gelbooru 的 `owner` 是**上传者**而不是画师（`GalleryPost.kt:48-51` 明写着两回事），
所以这一路在 Gelbooru 上推的是"这个人的口味圈"，措辞不能说成"画师推荐"。

## 三、冷启动语料：当前**未知**，而且它是本方案的前置门槛

**这条必须实测出来再谈算法**，理由很直白：整个推荐的地基是"用户收藏了多少张、
这些收藏的标签重合度够不够抽出 3 枚共现标签"。地基不成立，上面写得再漂亮都是空的。

我今早要从设备上量（收藏条数、分站分布、每条标签数、去重标签数、只出现 1 次的标签占比），
命令是：

```bash
adb exec-out "run-as com.github.w13630039663bit.venera.miuix cat files/gallery_favorites.json"
```

**结果：设备中途掉线，没量到。** 唯一拿到的弱证据是掉线前那次 `run-as ls files | grep -i gallery`
**没有回任何行**、`cat` 回 0 字节 —— 指向"**这台机器上还没有画廊收藏**"，
但我没有把它验实，所以**不当结论用**。

如果确实是 0，那对本方案的含义是决定性的：

1. **For You 上线即空态**，用户第一眼看到的不是推荐而是"请先收藏"；
2. 那么真正决定观感的是**空态与降级文案**（§七.3），不是算法；
3. 第一期应该做的是"**把已有能力接起来**"（§六 的方案 A：推荐标签 chips），
   而不是新造一屏推荐流。

**所以 §九 第 0 步就是重跑一次这条命令。** 拿不到数就不开工。

## 四、算法设计（纯函数层，可上单测）

本项目单元测试**没有 Robolectric**（`app/build.gradle.kts` 只有 `androidTestImplementation`），
所以判据层必须是纯函数 —— 与 `GallerySearch` / `GalleryMerge` 同一个路子。

新增一个文件 `gallery/domain/GalleryRecommendations.kt`，对外三个纯函数：

```kotlin
object GalleryRecommendations {
    /** 种子 → 候选池（词频前 poolSize）。黑名单/未关注在这里就剔掉。 */
    fun tagPool(seeds: List<GalleryFavorite>, poolSize: Int, blocked: Set<String>): List<String>

    /** 池 + 共现 + 带种子的随机 → 抽出 selectionSize 枚。抽不满就少给，不硬凑。 */
    fun pickTags(seeds, pool, selectionSize, seed: Long): List<String>

    /** 每站各算一次。返回按站的标签集，空集要能被调用方区分成"没收藏"还是"算不出"。 */
    fun recommend(seeds: List<GalleryFavorite>, seed: Long): Map<GallerySite, List<String>>
}
```

三条与参考实现**不同**的地方，都是它踩过的坑：

1. **随机必须带种子**。它用的是 `Random.Default`（`RecommendationsHelper.kt:6、215`），
   所以同一份收藏每次进来标签都可能变 —— 这与我们"种子只在一处生成、组合重建后还是同一个序列"
   的既有裁决直接冲突（`GalleryViewModel.seed:35`、`GalleryMerge` 顶部注释）。
   我们接 `GalleryViewModel.seed`，只有刷新换种子。
2. **共现必须先建索引**。它是 `O(候选 × 图 × 标签)`，且每张图都 `map{lowercase()}` 一遍
   （:138-149 那个嵌套循环）。收藏上千张会明显慢。我们先把"图 → 标签 Set"预计算一次，
   共现计数变成 `候选 × 图` 的 Set 判交。
3. **抽不满就少给**。它 `while (result.size < selectionSize && remainingPool.isNotEmpty())`
   里共现为 0 就 break（:151），可能只抽出 1~2 枚 —— 这是对的，
   我们照做，并且**要在页面上说出来抽到了几枚**（否则"推荐"读起来像坏了）。

## 五、取数与接线点（全部复用现成的，不新写链路）

| 环节 | 用哪个现成的 |
|---|---|
| 拼标签串 | `GallerySearch.queryOf(filters)`（`GallerySearch.kt:68`） |
| 取图 + 翻页 | `YandeReClient.searchPosts(tags, page)` / `GelbooruClient.searchPosts`（后者 `pid` 是 **0 基**，:139 已处理） |
| 判到底 | `GallerySearch.isExhausted(returned, requestedLimit)`（:81，"少于要的就是到底"） |
| 滤掉不能摆的 | `GalleryMerge.isDisplayable`（:58） |
| 黑名单 / 分级 | `ContentGuardManager.findGalleryBlockedRule` + `buildGalleryWall`（**与日榜、搜索同一面墙、同一把判据**，见 `GalleryScreen.kt:281`） |
| 装条件并开搜 | `GallerySearchViewModel.acceptHandoff(site, tags)`（:264，切站 + 装条件 + 开搜是**一步**） |
| 每站时间预算 | `GalleryFeedSource` 那套 `PER_SITE_TIMEOUT_MS` + `NoInteractiveBypassTag` 的口径要一并沿用（超时不是失败、要被说出来） |

**每站各算各的**（照搬 §一步骤 1）：两站词表不通，同一串标签换个站就是另一个结果 ——
这条我们已经在搜索历史上写过同样的判据（`GallerySearchEntry` 带 site，`GallerySearch.kt:23-27`）。

## 六、UI：三种摆法

### A. 推荐标签 chips（**推荐第一期**）

把算出来的标签集摆成几枚 chips（搜索区展开时、或日榜页尾"根据你的收藏"那一行），
点一枚 = `acceptHandoff` 一次正常搜索。

- **代价**：一个新纯函数 + 一行 UI。不新增状态机、不新增取数路径、不动 ViewModel 的既有字段。
- **好处**：个性化这件事**交给用户确认**（他点才算），不是替他决定 —— 与我们"零容忍假开关"同调；
  语料少的时候也不会难看（只出一枚 chip 也是一枚能点的 chip）。
- **坏处**：不是一屏"刷不完的推荐"。

### B. 落地流加一个源开关（日榜 ↔ 推荐）

同一面墙，换取数源。推荐态可翻页（搜索那套分页现成）。

- **代价**：`GalleryViewModel` 要加"这一屏是哪来的"状态（**保护域，要显式授权**），
  并且 loadedKey 的比对口径要跟着改 —— 历史上这里出过"假按钮"和"返回即重拉"两类缺陷。
- **好处**：真正意义的 For You。
- **风险**：语料不足时这一屏会**空**，而它现在唯一能摆的东西就是日榜池子。

### C. 底栏第 4 位内部再开一个 tab

- **代价**：动导航层（保护域 + 预测式返回 + 组合重建那几处已知坑），**不推荐**。

**我的建议：A 先落地，B 等 §三 的语料实测出来再决定做不做。**

## 七、与既有裁决的冲突点（这一节最要紧）

1. **"随机"必须可复现**。参考实现不满足（§四.1）。我们的种子只能有一个来源。
2. **不做静默降级**。"没有收藏就退回热门池"这件事如果**不说**，就是本仓最忌的静默交错 ——
   用户以为看到的是"按我口味推的"，实际是随机热门。
   要退，就得**明写**"还没攒够收藏，这一屏是两站热门"（参考实现也是这么处理的：
   它宁可摆一句"先去收藏"，也没拿热门池顶包，§一步骤 9）。
3. **空态要说人话并指路**。"去收藏几张才会有推荐"要能一键跳到画廊收藏页。
4. **页尾读数不能变成假数**。现在页尾写着两站各 20 张；换成推荐之后那 40 这个数就不成立了，
   得跟着改口径（这条在 B 方案里必须一起处理）。
5. **分级守卫不能被绕过**。推荐出来的条目照样要过 `buildGalleryWall`；
   种子过滤（"只用安全档收藏当种子"）与结果过滤（"HIDDEN 不落屏"）是两件事，
   参考实现里前者用 `showAllRatings` 控制（`Recommendations.kt:69`），我们沿用现成的 `maskMode`。
6. **图片流量与过盾**。推荐的取数是搜索链路（API 流量），不是图片流量 ——
   它**该**参与域名熔断，也**不该**弹交互式过盾（`NoInteractiveBypassTag` 那条口径直接沿用）。

## 八、明确不做

- 不做任何"学习用户行为"的隐式反馈（看了没点、停留时长）—— 参考实现没有，我们也没有这个数据面；
- 不做向量/相似度/跨站合并；
- 不做"推荐画师"关注流（Breadboard 的 `getRecommendedArtists` + Following tab）——
  那是另一条产品线，本轮不扩；
- 不给 `GalleryPost` 补一个假的分类字段去凑权重表（§二.1 已经说明那是站方天花板）。

## 九、实施切分（拍完之后照这个走）

| 步 | 内容 | 触碰保护域？ | 提交 |
|---|---|---|---|
| 0 | **重跑 §三 的实测命令**，拿到语料数（条数/分站/标签数/共现密度） | 否 | 不提交，只把数写回本文档 §三 |
| 1 | `GalleryRecommendations`（纯函数）+ 单测（词频池、共现、带种子可复现、抽不满少给） | 否 | 独立一笔 |
| 2 | 方案 A 的 UI（chips + `acceptHandoff`） | 否（只碰 `GallerySearchArea`/`GalleryScreen` 渲染层） | 独立一笔 |
| 3 | 分类权重（候选池各查一次 tag 端点） | 否（clients 只加读法） | 独立一笔，**可单独回退** |
| 4 | 方案 B（落地流换源） | **是**：`GalleryViewModel` + 页尾读数口径 | 需 §十.6 明确授权后单独一笔 |

每步都要跑：`compileDebugKotlin` / `testDebugUnitTest` / `assembleDebug`，
第 2、4 步另需真机观感确认。

## 十、待拍板（每项自带代价，推荐项标了）

1. **第一期做 A 还是直接做 B？**
   A：小、可测、语料不足也不难看（**推荐**）；B：一屏真推荐，但语料未知 + 动 ViewModel + 页尾口径要改。
2. **种子范围**：只用当前站的收藏（参考实现口径，**推荐**）／两站合并算一份再各站套用（词表不通，会推歪）。
3. **要不要为分类权重付"候选池 × 1 笔 tag 端点请求"**？
   不要（**推荐第一期不要**）：先量纯词频 + 共现的效果；要：§九 第 3 步单独做，能二分。
4. **`poolSize` / `selectionSize` 取几**？参考实现默认 7 / 3，滑块 1..20 与 1..5。
   我建议**先不摆滑块**，写死 7 / 3 并把两个数放进单测；等真机觉得"推得不准"再决定要不要开放设置
   （多一个设置项就多一处要解释的文案）。
5. **收藏为 0 时那一屏摆什么**：明说"还没有收藏，这是两站热门"（**推荐**）／干脆不显示 chips 那一行。
6. **是否授权本轮触碰 `GalleryViewModel`**（做 B 就必须）？不授权则本轮只到 §九 第 3 步。
7. **提交切分**：按 §九 一步一笔（**推荐**，便于真机出问题时二分）／合并一笔。

---

**一句话总结**：这套东西的成本比"推荐系统"听起来低一个数量级 —— 它就是一个带共现约束的
本地词频拼词器，接在我们已经有的搜索链路上。真正的风险不在算法，
在**语料有没有**（§三 没量到）和**空态/降级会不会说谎**（§七.2）。

## 十一、落地记录（2026-09-27 03:10）

用户「全部按推荐项定，开始写代码」→ 本轮做 §九 第 0/1/2 步，**不碰 `GalleryViewModel`**（没做 B）。

### 第 0 步：语料实测**没拿到**

设备在量之前掉线（`adb devices` 空）。掉线前唯一证据是 `run-as ls files | grep gallery`
没回行、`cat files/gallery_favorites.json` 回 0 字节 —— 指向"这台机器上还没有画廊收藏"，
**未验实，不当结论用**。本轮实现不依赖它：判据层对空收藏有明确的 `NoSeeds` 分支，
UI 那一行会直接说"还没有画廊收藏"。等设备回来补这一条。

### 第 1 步：`gallery/domain/GalleryRecommendations.kt`（纯函数）+ 11 例单测

提交 `3a6fc6d`。三条与参考实现不同的地方（带种子、共现走 Set、同次数按名字定序）
都各有一个用例锁住，其中"共现前缀判据"和"不硬凑"是**跑 200 个种子**验性质的，
不是只验一个抽样点。

### 第 2 步：UI —— 落在**搜索卡的输入态**

`GalleryScreen` 算好 `recommendations`（喂收藏 + 注入**与那面墙同一把**屏蔽判据 + 交 `vm.seed`），
`GallerySearchArea` 只负责画。位置在"最近搜索"**上面**：推荐要让用户先知道有这条路，
历史是他自己走过的。

两处口径要记下来：

1. **一枚条目 = 整串条件，不是一枚标签一行。** 拆成单枚就丢掉了共现约束，
   点下去只剩"搜一个高频词" —— 那正是这套算法要避开的废组合。
   所以行上写的串就是发出去的搜索串（走 `GallerySearch.queryOf`，不自己 join）。
2. **点它 = `svm.acceptHandoff(site, tags)`**，切站 + 装条件 + 开搜**一步**完成
   （沿用大图页点标签那条已验证的路径，不新造第二条）。
3. 两种"没有推荐"分开说：没收藏 → 引导；有收藏但标签全被挡完 → **点名是哪一站、为什么**，
   不能退化成"快去收藏"那句（那是把我们的缺陷说成用户没干活）。

⚠️ 一个踩过的坑：`Icons.Outlined.AutoRecommended` 在 material-icons-extended **1.7.0 里不存在**
（编译直接 unresolved）。是去 aar 的 `classes.jar` 里列了 `outlined/Auto*Kt` 才定下来用
`AutoAwesome`。别再凭印象写图标名。

### 本轮**没做**（按拍板）

- 分类权重（§九 第 3 步）—— 等真机觉得"推得不准"再决定要不要为候选池付那几笔 tag 端点请求；
- 方案 B（落地流换源）—— 需要授权碰 `GalleryViewModel`，且要先有 §三 那个数；
- 落地流页尾也摆一行推荐 —— 现在只在搜索卡里，**发现性依赖用户点开搜索**。
  真机如果觉得"根本看不到"，下一步就加页尾那一行。

### 验证

`testDebugUnitTest` **239 例全绿**（新增 11 例）、`assembleDebug` ✅ 03:10。
真机待验：① 有收藏时搜索卡输入态应出现"根据你的收藏"那一行，点它直接出结果；
② 没收藏时应看到那句引导，而不是一行空白；③ 返回画廊再进搜索，那一行的串**不该变**
（种子只在刷新时换 —— 这条是本轮最容易复发的性质）。
