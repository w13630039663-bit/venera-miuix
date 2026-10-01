# PicaWeb 功能对照 + 排行榜档位修复 · 2026-09-23

起因：用户给出哔咔网页版（PicaWeb）链接，问"它还有很多功能，看哪些能移植"。
本文是**调研结论 + 本轮已修的坏项**。轮次的顺序由用户拍板：先修坏的，另两条下一轮。

## 零、调研方法（为什么不是点页面）

PicaWeb 是 Vite + React Router 的**纯客户端 SPA**：抓 HTML 只有一个 loading 壳与
`/assets/logo_round-*.png`，看不到任何条目链接。所以功能清单从两处取，都比点页面全：

1. **它的路由表**：`fetch('.../assets/js/index-ea_4Germ.js')` 里 `path:"..."` 的全部注册项。
2. **它的接口调用**：`assets/js/feature-comics-*.js`（378 KB）里带 `comics` 的字符串字面量。

## 一、逐条对照结果

| PicaWeb | 我们这边 | 依据 |
| --- | --- | --- |
| `/categories`、`/comics`（分类与列表） | ✅ 已有 | 探索页两区块 + `SourceSectionScreen` |
| `/comics/category/:value` + 排序 | ✅ 已有 | `loadCategoryComics(category, param, options, page)` |
| `/comics/tag/:value` | ✅ 已有 | 详情页标签 → `TagSearchRoute` |
| `/comics/random`、最近更新、大濕推薦、那年今天、官方都在看、嗶咔漢化/全彩/長篇… | ✅ 已有 | 这些**本身就是 category 值**（`picacg.js:266-318` 的 `categories` 数组）+ explore 里的 Random/Latest |
| `/comics/leaderboard/:timeframe/:category` | ⚠️ 半有，且**是坏的** → 见 §二 | `SourceExploration.kt:137-145`（已修） |
| `/comic/reader/:comicId/:order/:readerType` | ✅ 已有 | 阅读器 + 预览图跳读到页 |
| `/favourite`、`/history`、`/settings`、`/keyword-filter` | ✅ 已有 | 网络收藏、历史页、设置子树、`ContentGuardManager` 屏蔽规则 |
| **`/comics/author/:value`、`/translator/:value`、`/creator/:value`** | ❌ 缺 | 详情页作者是纯文本（`ComicDetailScreen.kt:427`） |
| `/comic/:comicId/comments` 整页 | ⚠️ 形态不同 | 我们是半屏 sheet |
| 热门搜索词、`/my-comments`、`/knight-leaderboard`、`/announcements`、`/games`、`/support-us` | ❌ 做不了 / 不该做 | `picacg.js` 里**没有**对应协议成员（热搜词与"我的评论"都要源侧接口）；后四类是充值与推广页 |

→ 用户三条勾选：作者入口、排行榜二维、评论独立页。**其中"二维"是我的判断错误**，见 §二。

## 二、本轮修掉的四条（都是"master 有、我们缺或坏了"）

### 1. 排行榜档位从没读源声明，一直发臆造值
`master:lib/foundation/comic_source/parser.dart:611-665` 把 `categoryComics.ranking.options`
拆成 `RankingData(options: Map<key,label>, load, loadWithNext)`，**拆分规则是按第一个 `-` 切开**
（`split.removeAt(0)` 作 key，剩余 `join("-")` 作 Label，没有 `-` 的条目跳过）；
`ranking_page.dart:18-26,82` 遍历 `options.entries` 生成档位选择器。

我们这边三处都没有：`CategoryData` 里根本没有 options 字段；`SourceExploration.kt:143` 硬编码
`rankingOption = "day"`；`JsComicSource.loadCategoryRanking` 把它**原样**回传给源。
实测各源合法档位（脚本里逐条抄出来的，不是推的）：

| 源 | 档位 key |
| --- | --- |
| picacg | `H24` `D7` `D30` |
| jm | `mv` `mv_m` `mv_w` `mv_t` |
| ehentai | `15` `13` `12` `11` |
| hitomi | `today` `week` `month` `year` |
| wnacg | `day` `week` `month` |
| nhentai | `date` `today` `week` `month` `popular` |
| manhuagui | **空串**、`update`、`view`、`rate` |
| mycomic | **空串** 等（它的档位串里带两个 `-`，Label 保留剩余部分） |
| happy | `day` `dayBookcasesOne` `week` `weekBookcase` `month` `monthBookcases` `voteRank` `voteNumMonthRank` |
| komiic | `MONTH_VIEWS` `VIEWS` |

只有 wnacg/happy 的第一档**恰好**叫 `day` —— 其余源发出去的都是非法值。

**修正**：`CategoryData.rankingOptions: List<RankingOption>`，JS 侧按 master 的拆分规则读出来，
`SourceExploration` **一档一个探索方式**（`id = "$sourceKey:ranking:$key"`，label `排行·$Label`，
Label 走源字典翻译而 **key 绝不翻译** —— 它是要回传的请求参数）。
"时间×分类二维"是我判断错了：源侧只有**一维**（它自己的 options），PicaWeb 那个
`:timeframe/:category` 是它前端自己拼的两个 query 参数，我们不造第二维。

### 2. 排行加载失败被吞成"空榜"
`loadCategoryRanking` 原先写 `if (envelope["success"] != true) return Result.success(emptyList())`，
而 master 那条是 `Res.error(e.toString())`（`parser.dart:640-643`）。于是登录过期 / 档位非法 /
网络失败统统表现成**一个空排行榜**，用户零线索。现改为冒 `Result.failure`；
只有"源压根没有 ranking 块"（JS 里 `return []`）才是空成功。
调用方 `fetchModeParts` 本来就 `onFailure = { throw it }`，探索页会进错误态带重试。

### 3. `groupParam` 没读 → nhentai 语言分区整组参数丢失
协议里分类条目的 param 有两种声明：逐项的 `categoryParams`，和整组覆盖的 `groupParam`
（`master:parser.dart:489-493`：`groupParam != null` 时把它铺满整组）。
我们的脚本只吃了前者（`JsComicSource.kt:1071`）→ 实测受影响的是
**nhentai 的语言分区**（`nhentai.js:544` `groupParam: "language"`，条目 Chinese/English/Japanese）：
param 全丢成 null，点进去进不了语言维度。wnacg 的五处 `groupParam` 都是 `null`，无影响。
现已补上整组覆盖，且 `categoryParams` 那条路径不变。

### 4. 分类区块那张「排行榜」卡走的是**分类**端点
`UnifiedExploreScreen.kt:433-449` 原先 `onOpenNativeSection(category="排行", param="ranking")`，
而 `SourceSectionScreen.load()` 只会调 `loadCategoryComics` —— 对哔咔发出去的是 `&ranking=排行`，
**根本不是排行接口**。现改为：这张卡只做"选中第一档排行"（与上方快捷芯片同一套状态），
不发错请求。`hasRanking` 同时收紧成 `enableRankingPage && rankingOptions.isNotEmpty()`
—— 只有能力位没有档位时不摆死按钮。

## 三、验证做到哪一步

- 纯逻辑：新增 `SourceExplorationRankingTest`（5 例）锁住"源声明几档出几个入口""绝不出现臆造的
  `day`""空 key 的档位照样出""没开排行页不出""有 ability 无档位不出"。
- JS 那段文本**编译期抓不到错**，所以用 node 把 `getCategoryData` 里的真文本抽出来喂 stub 跑了一遍，
  实测结果：`groupParam` 铺满整组（Chinese/English 都拿到 `language`）、`categoryParams` 路径不回归、
  `H24-Day`→`{H24,Day}`、`-最新发布`→`{"",最新发布}`、`no-dash-skipped`→`{no,dash-skipped}`（与 master
  拆分逐字一致）、`""` 跳过。验证脚本是一次性的，已删。
- `:app:testDebugUnitTest` 全绿、`:app:assembleDebug` 出包。
- **未提交、真机未验**（见 §五）。

## 四、下一轮已定的两条口径（本轮不动）

- **② 评论**：用户拍板**继续用 sheet**，不做 master 那种"预览位 + 独立页"
  （`master:lib/pages/comic_details_page/comments_page.dart` + `comments_preview.dart`）。
  那就在 sheet 内把分页/回顶/加载态做齐。
- **③ 作者/汉化组入口**：用户拍板**只吃协议声明，余下退搜索**。也就是说 param 的来源限定为
  分类条目已有的 `categoryParams`/`groupParam`（`master:parser.dart:489-501`）；
  源没声明就没有"这个源认哪些 param 键"的依据 → 作者点击退到 `TagSearchRoute(keyword=作者名,
  sourceName=本源)`（全源可用，不承诺精度）。**不做试探式调用**：param 语义是源自定义字符串，
  未知 param 可能被别的源当成另一维度处理，返回"成功但是不相干的书"，那是本仓库最忌的静默交错。
  ⚠️ 详情页的作者是独立字段（`ComicDetails.author`），**不在**分类条目里，所以这条落地前要先定：
  没有协议声明时是否一律退搜索（倾向：是）。

## 五、真机验收（只读，不代操作）

1. 探索页选 Picacg → 快捷筛选里应出现「排行·Day / 排行·Week / 排行·Month」三档（**不再是一个空榜**）；
   逐档点，列表要各不相同且都是榜单内容。
2. 禁漫天堂 → 应有「總排行 / 月排行 / 周排行 / 日排行」四档；拷贝漫画多账号、wnacg、hitomi、nhentai
   各自档位数应与 §二第 1 条表里的一致。
3. 登录过期或断网时点排行榜 → 要看到**错误态 + 重试**，不再是"空列表"。
4. nhentai → 分类页「语言」分区点 Chinese/English/Japanese 任一，二级页应给出该语言的作品列表
   （修前是丢参数）。
5. 分类区块底部那张「排行榜」卡 → 点它应等效于选中第一档，且**不跳**到一个内容不相关的二级页。
