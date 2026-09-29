# 画廊搜索方案（2026-09-25）

入口按用户点名：**画廊右上角的搜索图标**。参考实现是 Breadboard 的
`search/SearchScreen.kt`（浅克隆在 `build/_refs/breadboard/`），但**不照抄**：
它是"一个源 + 标签 chip 构建器 + 分级 chip + 搜索历史 sheet + 无痕开关"，
我们这边是**两个站、词表不通、匿名侧有标签数上限**，且分级已经有全站那套守卫。
下面每条取舍都写清是照它、改它、还是否掉它。

## 〇、先说实测：两站的检索面能力表（全部本机 curl，2026-09-25）

UA 一律 `Venera/1.0 (Android)`（换浏览器串会被 CF 挡，实测与推导见
`gallery-viewer-toolbar-and-infosheet-2026-09.md` §12.2）。

| 能力 | Danbooru | yande.re |
|---|---|---|
| 按 tag 前缀补全 | `GET /tags.json?search[name_matches]=hime*&search[order]=count&limit=N` → **200**，条目带 `name / post_count / category / is_deprecated / words` | `GET /tag.json?name=hime*&order=count&limit=N` → **200**，条目带 `name / count / type` |
| 官方 autocomplete 路由 | `/tags/autocomplete.json` → **404**（被当成 `#show` 的 id 解析，报 `ActiveRecord::RecordNotFound`） | `/tag/auto_complete.json`（`term`/`query` 两种参数名）→ **404** |
| 拿"前缀匹配"当补全的另一条 | ⚠️ `search[name_match]`（少个 `es`）**参数被静默忽略**，回的是"最新建的 tag"（`huasu01`、`yakuen_sapuri`）—— 拿它做补全会显示一堆不相干标签 | ⚠️ `search[name]=hime` 同样被忽略（回一条 `name:""` 的空标签 + 一串不相干）；`name=~hime*` 回 **`[]`** |
| 检索帖子 | `/posts.json?tags=<空格分隔>&limit≤200&page=N`，**page 真翻页** | `/post.json?tags=<空格分隔>&limit≤320&page=N`，**page 真翻页**（`tags=hime` 第 1/2 页实测不同条目） |
| 负向 tag `-xxx` | 有效（`hime_cut -long_hair` 结果集变了） | 有效（`hime -kaibutsu_oujo` → **0 条**，而那 22 条全带 `kaibutsu_oujo`） |
| 匿名标签数上限 | **2 个**：3 个直接 **422** + `{"error":"PostQuery::TagLimitError","message":"You cannot search for more than 2 tags at a time."}` | 无此限制（`hime -kaibutsu_oujo` 两枚照样通） |
| 分级过滤 | `rating:safe` 有效（结果集全变） | `rating:explicit` 有效（45 条全 `e`）；⚠️ **`rating:general` 被静默忽略** —— 那是 Danbooru 的词，yande.re 只有 `s/q/e`，写错不报错、直接不筛 |
| 命中总数 | 无 `/posts/count.json`（**404**） | 无 `/post/count.json`（**404**） |
| 查无此 tag | `[]` + **200** | `[]` + **200** |
| **匿名侧的 URL 键** | ⚠️ **按分级抹键**：`tags=loli` 那批全是 `rating=e`，`file_url / large_file_url / preview_file_url / md5` **四支整条缺席**（行照给、`image_width/height` 照给）→ 客户端拿到的是一条"尺寸正确、无图可摆"的记录。**这是"Danbooru 搜不出图"的真实成因**，不是网络、不是 CF | 未抹键：`tags=rating:explicit` 8 条全 `e`，`preview_url / jpeg_url / file_url / md5` 齐全 |
| ⚠️ **`search[tags]` 是红鲱鱼** | `/posts.json?search[tags]=loli` **照样回 20 条完整行**，但**筛选被完全忽略** —— 填一个不存在的 tag 也回行、填 `id:` 也回行。它只是"把 URL 键还给匿名视角"而已，**不是**过滤参数。**绝不能用它做检索**，否则结果是"搜什么都有一屏" | —— |

三条设计约束直接从这张表长出来：

1. **补全得自己做前缀查询**，官方 autocomplete 两站都不可用；而且**参数字符差一个字母就静默给错数据**
   （`name_match` vs `name_matches`），所以补全结果要按"是否真的以输入为前缀"**在客户端再滤一遍** ——
   不滤就会把 `huasu01` 摆给搜 `hime` 的用户。这是本方案里最容易静默出错的一处。
2. **没有总数端点** → 页尾不承诺"N 条结果"，只报**已取回张数 + 是否到底**（到底判据：本次返回条数 < `limit`）。
   单标签的 `post_count`/`count` 可以摆在补全行上，但要说清那是**该标签的**张数、不是本次查询的命中数。
3. **`[]` 是合法结果**（查无此 tag），不能沿用日榜那条"空列表当失败抛"的口径 ——
   两处必须分开，否则搜一个冷门 tag 会得到一句"加载失败"。
4. **"站方给了行"≠"站方给了图"**（上面那行抹键）。所以结果侧必须把"不可摆"的行**滤掉并计数报出来**，
   而不是原样摆成一屏灰卡；判据复用日榜那把 `GalleryMerge.isDisplayable`，两处不能分叉。
   这条正是 2026-09-25 真机反馈①「Danbooru 搜索不出图」的成因与修法（见 §十一）。

## 一、结构决策（第二轮，**已被 §十四 第四轮改判**，此节留档）：搜索层是画廊页内的一种模式

> ⚠️ 这一节的"顶栏整条换成搜索头部 + Hero 模糊底"已经作废。现行形态只看 **§十四**。
> 保留原文是因为它是真机第二轮那次改判的证据，改写成"现在长这样"会抹掉决策链。

第一轮拍的是"像图片详情页那个信息框那样"（全高 `ModalBottomSheet`），也照那样落地了。
真机看过之后用户 2026-09-25 第二轮改判，原话：

> "还是把搜索改成点击搜索后，画廊标题，图片上面的区域会出现搜索框，按取消搜索后自动关闭搜索框，
> 图片页同步收缩上去，搜索到的页面和画廊显示同一界面，但是可以返回为原画廊界面，搜索后下拉界面，
> 变成和这样一样（图二）的效果"

所以形态是**内嵌模式**而不是盖上去的层：

- 点右上角 🔍 → 顶栏整条（`VeneraTopAppBar`）换成 `GallerySearchHeader` 那一块头部，
  挂在日榜**同一个 `Box`** 里、贴 `Alignment.TopCenter`；
- 墙还是同一面墙（`GalleryCardsGrid`），只是喂给它的 `cards` 从日榜那片换成搜索结果那片，
  所以"搜索到的页面和画廊显示同一界面"是字面意义上的同一界面；
- 列表的顶部避让在搜索态下按**头部实测高度**算（`onSizeChanged` 回传），不写死数字 ——
  头部有几行 chips、补全面板开没开都会变高度；
- 退出：头部左箭头或系统返回 → `svm.active = false`，顶栏换回来、图片"同步收缩上去"
  （`AnimatedVisibility` 的 `shrinkVertically`），日榜那片原样还在（两份 `cards` 互不覆盖，
  滚动位置各自独立）；
- "下拉界面变成图二那样"：结果态下头部那一条底下铺**第一条结果**的封面做重模糊 Hero 底
  （复用详情页那件 `CoverHeroBackdrop`，模糊半径与透明度取它现成的口径，不另造），
  标题位读的是查询串。**被守卫打码的那条不能当底** —— 模糊大图会绕过打码。

三条不变的旧结论仍然成立，理由没被第二轮改判推翻：

- **不动 `Navigation.kt`**（保护域），不再开 Activity、不加 `GallerySearchRoute`。
  2026-09-28 为第 2 项**又评估过一次**"搜索另开一页"，结论是**仍然不加**：要修的其实是
  "返回回到上一轮"，那一轮在 `GallerySearchViewModel` 里压栈就够；加成目的地反而带进来三条硬墙
  （几百条结果进不了导航参数、per-entry VM 会推翻"再点 🔍 回到原上下文"那条拍板、
  nav 栈只能从顶上 pop 所以中间那条删不掉）。压栈的落地与判据见
  `gallery-tag-category-translation-and-nav-2026-09.md` §7.4.3。
- 状态放独立的 `GallerySearchViewModel`：导航条目被下一页覆盖时**组合会销毁**，
  裸 `remember` 会让"点进大图再返回"把整片搜索结果丢掉（同一根因见记忆「导航条目会重建组合」）。
- 输入框仍用 `BasicTextField`（与漫画搜索同一取舍），IME 不自建焦点（不自动弹键盘）——
  现在搜索态是页面的一部分，进来就弹键盘会把上面那片墙顶得没法看。

第一版的 sheet 文件 `GallerySearchSheet.kt` 已按可逆清理路径搬到 `build/_trash-from-repo/`。

## 二、一次只搜一个站

顶栏搜索图标进去后，最上面是两枚站点胶囊（Danbooru / yande.re），默认选中**上一次搜索用的站**
（没搜过就跟当前日榜无关，取 Danbooru —— 它的词表大、补全质量高）。

不做"两站合并搜索"，三条实测理由：① Danbooru 匿名只有 2 个标签预算，yande.re 没有这个限制，
同一串 chip 在两站的合法性不同；② 两站 tag 词表不通（`1girl` 是 Danbooru 的，yande.re 没有），
合并必然出现"一边 0 条一边 200 条"的假均衡；③ 翻页游标各站独立，合并后"到底了"没法一句话报准。
（这一条与 §〇 第 2、3 点是同一类"读数必须准"的要求。）

## 三、两段式：补全 → chip → 结果墙

**第一段（输入态）**：`BasicTextField`（与漫画搜索同一个取舍：不用 M3/Miuix 的 SearchBar，
理由抄 `feature/SearchScreen.kt:611` 那段），`imeAction = Search`。
输入防抖 **250 毫秒**后拉补全（Breadboard 用 200ms，这里取同量级），
列表每行：标签名 + 档位（`category`/`type` 翻成 通用/画师/角色/作品/元数据，
与 `GalleryPost.tagGroups` 那五桶**同一份映射**）+ 站方张数。
点一行 = 加一枚 chip 并清空输入；IME 的搜索键 = 加**第一条补全**（Breadboard 同做法，
它还在首行摆了个回车图标提示，这里照做）。

**chip 区**：已选的标签以 chip 横排，点一下切"排除"态（`-tag`），再点取消，长按删除。
超出本站预算时**当场说一句话**并把多出来的那枚拒掉（Danbooru 2 枚，含排除项），
不等发请求拿 422 —— 但 422 仍要认：`requestList` 现在对非 2xx 只报状态码，
这里要把 `PostQuery::TagLimitError` 的 `message` 翻成人话兜底（降级路径不静默）。

**第二段（结果态）**：**不换层、不换屏** —— 头部留在原处，下面那面墙改喂搜索结果
（`GalleryCardsGrid` 已是日榜与搜索共用的那一个，守卫过滤也共用 `buildGalleryWall`，
否则会出现"墙上被挡、搜索结果里全裸"的分叉）。
滚到底自动取下一页（`page++`，`limit` 各站取自己上限的整数档：Danbooru 200 / yande.re 100），
页尾一行按"到底判据"报：**已取回 N 张 · 第 M 页 · 到底了/还在加载**，
外加与日榜同一套「命中屏蔽规则 X 张（列出规则原文）· 按分级收起 Y 张」，
以及抹键那一批的跳过数（§〇 第 4 条）。第 1 页还在飞且一张都没落屏时摆整页级波浪环，
不摆那行读数 —— 那一刻"上滑继续取"是个假动作（续页 effect 正被 `isSearching` 挡着）。

点结果卡片 → 与日榜**完全同一条路**：`GalleryFlyIn.capture` + `openGalleryPost`，
大图页那套向上滑入/HD/下载/信息照用，零特化。

## 四、分级不做查询内过滤（否掉 Breadboard 的 ratings chips）

Breadboard 在搜索页摆一排 `Safe/Questionable/Explicit` chip。这里**不摆**，三条理由：

1. 应用已经有全站那套「成人内容处理」（`nsfwMaskMode` + 用户黑名单），
   画廊侧还单独有一把整词判据（`GalleryBlockMatch`）。再摆一排就是**两个开关管同一件事**，
   且用户改哪个都能让另一个"看起来失灵"。
2. 实测 yande.re 对不认识的 `rating:` 值**静默不筛**（`rating:general` 回全量），
   摆出来就是一个会假成功的开关。
3. Danbooru 匿名只有 2 枚预算，加一枚 rating 就只剩 1 枚给真标签。

结果侧照旧走 `GalleryGuard.maskStateFor`：该糊的糊、该收的收，并在页尾把收起数报出来。

## 五、搜索历史

`SharedPreferences` 文件 `venera_gallery_search`、键 `history`，上限 20、
空态展示最近 5 条 —— **与漫画搜索同口径**（`feature/SearchViewModel.kt:440`）。
存的是「站点 + 那串 chip 文本」一条一项（不是 Breadboard 那种带 ratings 快照的 entry，
因为我们没有查询内 ratings）。编解码抽成纯函数好上单测（本项目单元测试没有 Robolectric）。
落地的编码用**可打印分隔符**：条目之间 `\n`、站点与查询之间只认**第一个** `=`
（查询串里本身可能有 `=`，且 `routeKey` 不含它）；未知站点键的那条直接丢掉，不猜。
（方案初稿写的是 `\u001F`，落地时改成可打印串 —— 手写 SharedPrefs 时不可见字符会咬人。）
不做无痕开关：应用里没有"无痕"这个概念，单造一个开关等于多一条要维护的状态。

## 六、明确不做（本轮）

- **不做以图搜图**（Breadboard 的 `saucenao/ReverseSearchScreen.kt` 走 SauceNAO，要 API key，
  且是第三个外部服务）。
- **不做 tag 翻译**（漫画侧那套 `TagTranslationManager` 是中文漫画词表，
  与图站 tag 不是一回事；且画廊与漫画隔离是硬裁决）。
- **不做相关标签 / 标签详情**（`/tag/related_tag.json` 之类未实测，不凭猜摆 UI）。
- **不做补全结果的本地缓存/词表预下载**（Danbooru 全量 tag 是百万级，先不碰）。

（初稿在这里还有一条「**不做"从 InfoSheet 点 tag 直接跳搜"**，留第 2 期」——
用户 2026-09-25 第三轮点名要它，本期已落地，见 §十二。**"要做"的清单不要留在"不做"里**，
否则下一个人会当它仍然作废。）

## 七、改动面与保护域

| 文件 | 动作 |
|---|---|
| `gallery/data/GalleryTag.kt` | 新增：`GalleryTagSuggestion(name, site, count, category, deprecated)` + 两站 tag DTO 翻译 + **前缀复检**纯函数 |
| `gallery/data/DanbooruClient.kt` | 加 `searchTags(term, limit)` / `searchPosts(tags, page, limit)`；把 422 的 `TagLimitError` 翻成人话 |
| `gallery/data/YandeReClient.kt` | 同上两条（无预算限制）；`PAGE_SIZE=30`（零调用点）换成 `SEARCH_PAGE_SIZE=100` |
| `gallery/domain/GallerySearch.kt` | 新增纯函数：标签预算、chips↔`tags=` 串往返、到底判据、历史编解码 |
| `gallery/domain/GalleryMerge.kt` | 把"能不能摆上屏"那把判据**开放出来**（`isDisplayable`）—— 日榜抽样与搜索结果过滤必须同一把 |
| `gallery/ui/GallerySearchViewModel.kt` | 新增：搜索态（当前站、chips、补全、结果、page、到底、抹键跳过计数）+ 取数（两个调用方共用） |
| `gallery/ui/GallerySearchHeader.kt` | 新增：**画廊页内的搜索头部**（输入 + 补全 + chip + 站点 + 「搜索/改条件」+ Hero 底 + 页尾读数 `GallerySearchEnd`）。第一版的全高 sheet `GallerySearchSheet.kt` 已移到 `build/_trash-from-repo/` |
| `gallery/ui/GallerySearchHandoff.kt` | 新增：大图页点标签 → 一级画廊搜索态的**跨 Activity 一次性交接槽** |
| `gallery/ui/GalleryScreen.kt` | 顶栏 actions 加 🔍；内联那面墙抽成共用的 `GalleryCardsGrid`/`buildGalleryWall`；搜索态与日榜态共用同一 `Box`、避让按头部实测高度；消费交接槽 |
| `gallery/ui/GalleryInfoSheet.kt` | 标签 chips 从纯展示改成可交互：点击搜该标签，长按「搜索 / 复制 / 屏蔽」菜单 |
| `app/src/test/.../gallery/` | 新增 3 个测试文件 + 1 条 fixture 不变量（见下） |

**保护域**：`Navigation.kt` 与底栏枚举**都不动**。改动全在 `gallery/` 包内 ——
符合"画廊与漫画完全隔离"那条裁决（只共用 OkHttpClient / ImageLoader / 守卫这些技术层）。

单测（没有 Robolectric，所以钉的都是纯函数判据）：
1. `GalleryTagSuggestionTest` —— 前缀复检：`huasu01` 不能出现在搜 `hime` 的补全里；
   档位映射与 `tagGroups` 那五桶一致；废弃标签沉底而不是消失。
2. `GallerySearchBudgetTest` —— Danbooru 2 枚（含 `-` 排除项）、yande.re 不限；
   chips↔查询串往返（`-` 前缀、空格→`+`）；422 报文能翻成那句人话。
3. `GallerySearchPaginationTest` —— 到底判据（返回 < limit 即到底）、历史编解码的
   去重 / 上限 / 带等号的查询串 / 认不出的站点整条丢掉。
   **注意覆盖面**：`appendPage` 里"抹键行被过滤并计数"那一步**没有单测** ——
   它在 `GallerySearchViewModel`（`AndroidViewModel`，本项目单元测试没有 Robolectric，起不了 Context）。
   被单测钉住的是它用的那把**纯判据** `GalleryMerge.isDisplayable`
   （`GalleryMergeTest` 的「没有预览地址的条目不摆」「zip 这类不可摆的扩展名继续滤掉」两条）。
4. `GalleryDanbooruParsingTest` 新增一条 —— 五桶里每一枚 tag 都在 `tagList` 里：
   这是 InfoSheet 那个「屏蔽」不是假开关的前提（`TAG` 规则只拿 `tagList` 比）。

## 八、要拍的四条（已拍，见 §九）

1. **承载层**：同 Activity 内全屏 overlay（推荐，理由见 §一）还是照大图页那样再开一个 Activity？
2. **一次一个站**（推荐，§二）还是坚持两站合并？
3. **分级 chips 不摆**（推荐，§四）—— 这条是**否掉**参考实现的一个可见功能，明确一下。
4. **翻页取多少**：推荐 Danbooru 每页 200（它匿名上限就是 200）、yande.re 每页 100（320 是天花板，
   100 够一屏半且不至于让一次翻页解 100 张 preview）。

## 九、第一轮落地记录（2026-09-25 同日；其中"承载层"那条已被 §十 改判）

四条的答案：**①「直接像图片详情页开那个信息框那样」②一次一个站 ③不摆分级 chips ④200/100**。
① 把我推荐的"自绘全屏 overlay"改成了**复用 `ModalBottomSheet`**（见 §一改写后的那节），其余三条照推荐。

新增/改动：

- `gallery/data/GalleryTag.kt`（新）：`GalleryTagSuggestion` + 两站 tag DTO 翻译 +
  **`refineGalleryTagSuggestions` 前缀复检**（§〇 第 1 条那个"静默给错数据"的兜底）。
- `DanbooruClient` / `YandeReClient`：各加 `searchTags(term, limit)` 与 `searchPosts(tags, page, limit)`。
  Danbooru 侧把非 2xx 的**错误信封**翻出来（`failure()`：403 挑战页 / 站方 `message` 原文 / 状态码三支），
  所以哪天预算变了，用户看到的是站方那句 "more than 2 tags"，不是"返回 422"。
  yande.re 侧顺手清掉 `PAGE_SIZE = 30` —— 落地流改成日榜之后它**零调用点**，换成 `SEARCH_PAGE_SIZE = 100`。
- `gallery/domain/GallerySearch.kt`（新，全纯函数）：标签预算、`tags=` 串与 chips 的往返、
  到底判据、历史编解码（`routeKey=查询`，一行一条，只在**第一个** `=` 处拆）。
- `gallery/ui/GallerySearchViewModel.kt`（新）+ `GallerySearchSheet.kt`（新，全高 sheet。
  **这一份在第二轮被 `GallerySearchHeader.kt` 取代**，原文件按可逆清理路径移到
  `build/_trash-from-repo/GallerySearchSheet.kt.deleted`）。
- `GalleryScreen.kt`：顶栏 `actions` 加右上角搜索图标；把内联那面墙抽成共用的 `GalleryCardsGrid`，
  守卫过滤抽成共用的 `buildGalleryWall`（日榜与搜索**同一把判据**，
  否则会出现"墙上被挡、搜索结果里全裸"的分叉）。
- 单测 **209 条 0 失败**（本轮新增 3 个文件 19 条：补全复检 / 预算与语法串往返 / 到底与历史）。
  第二、三轮之后现为 **210 条 0 失败**。
- 端点这条链按代码**实际拼出来的 URL** 逐条复跑过（含 `URLEncoder` 把空格变成 `+` 这一形状）：
  两站的 `tags=a+b`、`tags=a+-b`（yande 那笔实测回 0 条 = 负向真生效）、
  `tags.json?search[name_matches]=hime*&order=count`、`tag.json?name=hime*&order=count` 全 200。

与方案的两处小偏差，都是落地时发现的：

1. 排序改成**未废弃的在前、再按张数降序**（方案里写的是"张数降序再把废弃沉底"）。
   理由：`hime_b` 这种 900 张的废弃标签不该压过 50 张的正常标签。
2. 补全行的档位**两站都能标** —— 方案 §〇 只说了 yande.re 的 post 里没有分类字段
   （那是 `tagGroups` 的事），但它的 **tag 端点有** `type`，编号与 Danbooru 的 `category` 同一套
   （实测 `hime` 两站都是角色、`hime-chan_no_ribbon` 都是作品）。

## 十、第二轮改判：内嵌搜索头 + Hero 头（2026-09-25 真机看过之后）

用户原话见 §一。落地成的样子：

- `GallerySearchHeader.kt`（新，取代第一轮那份全高 sheet）：状态栏避让 → 返回箭头 +
  `BasicTextField` + 清空钮这一行（底下铺 Hero 底）→ 站点两枚 + 「搜索/改条件」→
  一次性提示行 → 已选 chips（FlowRow）→ 补全面板 / 历史面板。整块高度由 `onSizeChanged`
  量给页面，列表避让按它算。
- `GalleryScreen.kt`：`svm.active` 决定顶栏是 `VeneraTopAppBar` 还是这一坨头部；
  系统返回先 `active = false`（`BackHandler(enabled = svm.active)`）；
  日榜与结果共用同一面 `GalleryCardsGrid`，只是喂的 `cards` 与 header/footer 换一份。
- Hero 底取**第一条没被打码的结果**的 `previewUrl`，复用 `CoverHeroBackdrop`
  （详情页那件，模糊半径/透明度取它现成口径），`bleedTop` 给状态栏那段高度 ——
  搜索头部在 `Box` 顶对齐、本身已经在状态栏下面，不顶穿就露不出模糊。
- 第一版 sheet 文件移到 `build/_trash-from-repo/`（可逆清理，没硬删）。

## 十一、「Danbooru 搜索不出图」（真机反馈①，2026-09-25 19:16 截图）

> ⚠️ **2026-09-26 补记：这一节的成因结论是错的，别照它改代码。**（完整复核见 §十七）
> 当年把"四个 URL 键被抹"归到了**成人分级**上，于是有了"登录后可见"的说法与那枚
> 「排掉成人分级再搜一次」的出口 —— 两件都已作废。**真正的成因是标签**：站方把
> `loli` / `shota` 列为**受管制标签**，对 **Member 与未登录一视同仁地封**；而成人分级
> （`rating:e`）**匿名本来就看得见**（`cat rating:e` 5/5 带图）。
> 教训是**对照组选错了**：当年拿 `tags=rating:explicit` 打到 yande.re 做对照，它排除了
> "图站通用行为"，却没有排除"Danbooru 按标签封"这一支 —— 而 `loli` 恰好两边都占。
> 下面这段留作"当年怎么想错的"的留档。

那张截图是**一排尺寸正确、内容全空**的灰卡 —— 尺寸对说明行是取到了、比例字段也在，
所以第一反应不该是网络。实测坐实（UA 用诚实的 `Venera/1.0 (Android)`）：

- `danbooru/posts.json?tags=loli&limit=4` → 4 条全 `rating=e`，
  `file_url / large_file_url / preview_file_url / md5` **四支键整条不存在**（不是空串，是键被抹掉）；
- 同一个 `tags=rating:explicit` 打到 yande.re → 8 条全 `e`，四支键**齐全**。
  所以这是 **Danbooru 对匿名视角的隐私处理**，不是图站的通用行为，也不是 CF 拦截
  （响应 200、`content-type: application/json`，没有 `cf-mitigated`）。

**走过的弯路（写下来防再踩）**：中途以为 `posts.json?search[tags]=…` 是"官方给的解法"，
因为它返回的行带全 URL。复核方式是拿它跑一个**不存在的** tag —— 照样回 20 条，
跑 `id:` 也回 20 条 —— 它**根本没过滤**，只是匿名视角的另一套序列化。用它做检索会得到
"搜什么都是一屏"的假结果，比空屏更坏。**红鲱鱼已记进 §〇 那张表。**

落地成的处置（三层，都不静默）：

1. **过滤**：`appendPage` 用日榜那把同一个判据 `GalleryMerge.isDisplayable`
   （`previewUrl` 非空 + 扩展名在可摆白名单里）把不可摆的行分出去，不原样上屏。
2. **说出来**：跳过张数累计成 `droppedNoImage`，其中 `rating=e` 的单列 `droppedNoImageAdult`；
   页尾 `GallerySearchEnd(noImage=…)` 与空态那句读数都把它念出来，
   并**点名成因**是"Danbooru 匿名不给图，登录后可见" —— 不点名就会被当成网络故障去查。
3. **给一个由用户点的出口**：空态按钮「排掉成人分级再搜一次」→ `excludeAdult()`
   往查询里加 `-rating:explicit` 再搜。**不自动加**：静默改用户的查询条件，
   搜出来的就不是他要的那串标签了。而且只在三个条件都成立时才摆这个按钮
   （跳过的确实主要是成人分级、本站还有预算加一枚、当前查询里还没这条排除项）——
   条件不成立就退成「改条件」，不做按下去没反应的假开关。

顺带把结果态那帧假读数堵掉：第 1 页还在飞、屏上还没落卡时，页尾那句会写
「已摆出 0 张；上滑继续取」，而此刻上滑什么都不会发生（续页 effect 被 `isSearching` 挡着）。
改成这一帧摆整页级波浪环（全站那条"整块重拉"的口径）。

## 十二、第三轮：大图页标签 → 点击搜索 / 长按菜单（真机反馈③）

用户原话："之前图片信息做的 tag，也要做到点击搜索，长按显示搜索 复制和屏蔽选项"。
**语义与画法都照漫画详情页那枚药丸**（`feature/ComicDetailScreen.kt` 的 `DetailTagChip`：
点击直达该标签搜索、长按起菜单、菜单逐项各挂一个锚），只是三项里多摆一项「搜索」——
它与点击重复是刻意的：长按菜单是"这块能做什么"的目录，少了这项用户只能靠猜。

- `GalleryInfoSheet`：`GalleryTagGroups` 收三个回调，`GalleryTagChip` 用 `VeneraChip`
  现成的 `onClick`/`onLongClick`（按压反馈归组件统一持有，页面不叠 `combinedClickable`）。
- **搜索**：`GallerySearchHandoff.postTag(post.site, tag)` → 关 sheet → `onBack()`（= `finish()`）。
  走 `onBack` 而不是页内那套下滑关闭动画：多 300ms 的离场动画挡在结果前面，看着像点了没反应。
  `GalleryScreen` 侧 `LaunchedEffect(GallerySearchHandoff.pending)` 消费：切站、
  `active = true`、**整片替换** chips（不是往上加 —— 那是另一张图的上下文，留着旧的会误导，
  而且 Danbooru 只有 2 枚预算）、`runSearch(1)`。
- 为什么槽位字段是**快照状态**而不是普通 `var`：写入方在另一个 Activity，靠快照失效才能让
  这边活着的组合立刻回读。大图页是透明窗口，底下那屏只是 paused、并没有停组合，
  所以这里**不能等 onResume**（透明窗上下的生命周期与不透明 Activity 不同，等它可能收不到）。
- 「取用一次即清」是刻意的，配合"消费方只把它写进活下来的 ViewModel、不挂自毁分支"，
  才不会重演记忆「导航条目会重建组合」里那两次翻车（切回 Tab 把同一次搜索再发一遍 / 退场期二次 pop）。
- **复制**：复用本文件已有的 `copyToClipboard`，toast 读「已复制标签「x」」。
- **屏蔽**：`ContentGuardManager.addRule("TAG", tag)`（与漫画详情页同一条 API、同三句 toast），
  先查重再落库，落库失败（返回 `-1`）说"屏蔽失败，请重试"，不报假成功。
  选 `TAG` 而不是 `KEYWORD` 是**量出来的**：画廊那面墙的屏蔽判据 `GalleryBlockMatch` 对 tag 只吃
  `post.tagList`，而 Danbooru 的 `tag_string`（= `tagList` 的来源）实测**含五桶全部**
  —— 60 条 post、五桶共 2280 个 tag 串，**没有一个**不在 `tag_string` 里；
  yande.re 那唯一一桶本来就是 `tags` 自己。所以每一枚 chip 的原串加下去必然命中。
  用 `KEYWORD` 会额外拿作者名做子串匹配，多一层误伤面。
  这条不变量已上单测（§七 第 4 条）。顺手校正了旧 fixture 里 `tag_string` 少写两支的想当然。

## 十三、真机待验

① 点右上角 🔍 → 顶栏整条换成搜索头部、图片"同步收缩上去"的观感；退出（箭头 / 系统返回）
   回到**原来那一屏日榜**，滚动位置与那批图都不变；
② 结果态下往上滚，头部那一条底下有没有真透出**第一条结果**的模糊底；被守卫打码那条
   不该当底（它若当了底就是绕过打码）；
③ 键盘弹起时输入框与补全面板没被盖住（挂了 `imePadding`），且**不会自动弹键盘**；
④ Danbooru 加第 3 枚标签时**当场**报预算，而不是发出去拿 422；
⑤ 结果卡片点进去的滑入、HD、下载、信息与日榜那条链完全一样（共用 `GalleryFlyIn` + `openGalleryPost`）；
⑥ 点进大图再返回，搜索结果与 chips **还在**（ViewModel 那份状态跨组合重建活下来）；
⑦ 搜一个不存在的标签：那一行说"没有以它开头的标签"，不是"搜索失败"；
⑧ **Danbooru 搜 `loli`**：不再是一屏灰卡 —— 页尾念"跳过 N 张（M 张是成人分级…）"；
   全被跳过时空态那句要点名同一成因，且带「排掉成人分级再搜一次」按钮，点它才加 `-rating:explicit`；
⑨ 大图页 `(i)` → **点**一枚标签：这一页关掉、画廊切进搜索态并直接出结果（不要停在输入态）；
   跨 Activity 那一跳**不靠 onResume**，所以要在"大图页刚 finish、画廊还没重新获得焦点"这一
   竞争窗口里现验一次；
⑩ **长按**一枚标签：菜单从**被长按的那一枚**上面弹出来（不是永远从第一枚）；三项各验一次 ——
   搜索同上；复制回读 toast；屏蔽后回到画廊，含该 tag 的卡**整张消失**（`TAG` 规则命中即 HIDDEN，
   与「成人内容处理」档位无关），页尾那句会念出命中的规则原文；再长按同一枚点屏蔽应说"已在屏蔽列表"；
⑪ sheet 里嵌 `DropdownMenu`（弹层里再开一层 popup）在 ColorOS 上有没有被裁 ——
   长标签串那种被裁的概率不低，被裁就改用 `DropdownMenu` 的 offset/overflow 参数而不是另造 UI；
⑫ 换站会清 chips（两站词表不通，留着上一站的标签必然 0 条）；点标签交接过来时**整片替换** chips。

## 十四、第四轮改判：搜索区按 MD3 重做成**顶栏下方的内联展开区**

> ⚠️ 本节有一处**已被 §十六 推翻**：那颗「MD3 Filled Button 提交钮」整颗去掉了 ——
> 第五轮改成"胶囊进框 + 同步搜索"。其余（`bottomContent` 落点、56dp 胶囊、
> `surfaceContainerHigh`、filter chips 单选、历史源名淡化、避让动画）仍然有效。

用户 2026-09-25 第四轮原话（要点）：「不要跳转新页面，也不要替换标题栏。搜索框要作为一个**内联展开的
搜索区域**，隐藏在顶部标题栏（如"画廊"）下方…点击标题栏的搜索图标时平滑向下展开，把下方的图片网格
自然向下推，完全不占用顶部标题栏」，并逐条给了 MD3 口径。这一轮同时否掉了前三轮的三处形态：
**顶栏被替换**（§十）、**Hero 模糊底**（§一）、**← 返回箭头**。

三条先拍清楚的（AskUserQuestion 的答案）：

1. **Hero 模糊底 → 去掉**。顶栏既然常驻，搜索区不该再自己找一层背景，只留 `surfaceContainerHigh` 实底。
2. **收起入口 → 输入框右侧那枚 X 兼任**：框里有字先清空，空了再点才收起整个搜索区。
3. **源切换仍单选**：一次只搜一个站（两站词表不通 + Danbooru 2 枚预算，见 §二 —— 这一条没被推翻）。

### 14.1 布局落点：顶栏的 `bottomContent` 槽位

`GallerySearchArea` 挂在 `VeneraTopAppBar(bottomContent = { … })` 里 —— 那枚槽位是这套顶栏给
"顶栏下方常驻内容"准备的**正规出口**（下载页 / 分类页 / 收藏页 / 历史页都在用），所以：

- 搜索区天然贴在标题下面、不占标题栏，也不需要页面自己再拼一层状态栏避让
  （§一 那版头部里的手写 status bar spacer 与 `CoverHeroBackdrop` 一并删掉）；
- 展开/收起用 `AnimatedVisibility(expandVertically + fadeIn / shrinkVertically + fadeOut)`，
  时长吃 `tokens.motion.medium`；
- **网格的"被推下去"不是自动的**：列表在顶栏之下是 overlay，避让靠 `contentPadding`。
  避让值本身不会动画，直接用会写成"整屏往下跳"，所以套 `animateDpAsState(tween(medium))`，
  与展开动画同一档时长才像同一段动作；
- 避让 = `statusBarTop + 104.dp`（与其他主 Tab 同一块地板，冻结声明第七轮）+ **搜索区实测高度**
  （`onSizeChanged` 上报；里面有补全 / chips / 历史，行数会变，写死必压字或留缝）。

顶栏右上角那枚图标现在**担开合两件事**：未展开是 🔍，展开后换成 ✕（`contentDescription` 跟着变）。
「换一批」在搜索态**整枚收掉** —— 它只作用于日榜那片池子，搜索态下按它用户看不见任何效果，
那是一颗假按钮。

### 14.2 MD3 各件落成的样子

| 用户给的口径 | 落成 | 取值出处 |
|---|---|---|
| 胶囊 28dp 圆角 / 高 56dp | `RoundedCornerShape(tokens.shape.extraLarge)` + `tokens.spacing.dockedSearchBarHeight` | MD3 档 `extraLarge` 实测就是 28dp（= 56 的一半，规范里的 full shape）；56dp 是 MD3 docked search bar 原值 |
| 底 `surfaceContainerHigh` + 微弱阴影 | `Surface(color = tokens.color.surfaceContainerHigh, shadowElevation = tokens.elevation.attached)` | 两套主题都桥得上（`ThemeColorBridge` 双向都映射了这一档）；`attached = 3.dp` = MD3 elevation3 |
| 去掉 ← ，保留搜索图标 | 左侧只剩 🔍 | — |
| 输入文字时右侧 X 清空 | X **常驻**，两个动作按"先局部后整体"排（有字清空 → 空了收起） | 拍板 2 |
| 提交钮 = MD3 Filled Button，别再是低对比淡紫 | 可点 `primary` 实底 + `onPrimary` 字；不可点换 `surfaceVariant` 底 + `textDisabled` 字 | **不再拿 alpha 压一个实底主色按钮** —— 上一版那颗就是 `primary` 叠 0.4 alpha，既读不出"按不动"又是这一轮被指对比度低的成因；MD3 的禁用态本来就是"中性低对比的一对" |
| 源切换独立一行横向滚动 filter chips | `Row(horizontalScroll)` + `VeneraChip(variant = Filter)` | 未选 = `outline` 描边；选中 = `secondaryContainer` 填充 + `onSecondaryContainer` 字（MD3 给 filter chip 的就是这一档，比 primaryContainer 退一级） |
| 历史另起一行 + 灰标题「最近搜索」 | `RecentSearches`：`type.caption` + `textTertiary` 标题 + chip 走 `variant = Neutral` | Neutral = 无描边、填 `surfaceContainerHigh` |
| 历史 chip 里源名淡化、关键词突出 | `VeneraChip(text = 查询, leadingText = 站点名)` | leadingText 吃 `type.overline` + `textTertiary`，**刻意不跟 contentColor**，否则一起变响 |
| 点历史 = 切站 + 把关键词填进输入框 | `svm.applyHistory(entry)`：**不再自动开搜** | 只有"查询串就一枚不含排除项的标签"才填进框；多枚或带 `-` 的串整个塞进框会让补全拿它当**前缀**去问站方，当场回一句"没有以它开头的标签" = 一句假的失败。那种情况 chips 已摆全，输入框留空更诚实 |
| 网格保持两列、平滑下移 | 列数口径没动（大屏 3 列 / 手机 2 列） | 见 §14.1 的 `animateDpAsState` |

### 14.3 一处**没有照抄**用户清单的地方（功能不能丢）

上一版头部右上角还有一枚 ✕ =「清空全部条件」。这一轮那个位置被输入框的 X（清空输入 / 收起）占了，
于是「清空」搬进 chips 那一行的**末尾一枚 Assist chip**。理由：yande.re 不限标签数，攒到六七枚之后
只能一枚一枚长按删是惩罚用户；动作可以换地方摆，但不能跟着换形式丢掉。

### 14.4 改动面（含一处**基础组件与 token 层的扩张**，要看得见）

| 文件 | 动作 |
|---|---|
| `ui/tokens/Color.kt` + `VeneraTokens.kt` | `VeneraColorTokens` 新增 3 个语义槽位：`surfaceContainerHigh` / `secondaryContainer` / `onSecondaryContainer`（取值全走 `buildVeneraColorTokens(m = MaterialTheme.colorScheme)`，两套主题都桥得上，页面不越层取色板） |
| `ui/tokens/VeneraTokens.kt` | `VeneraElevationTokens` 新增 `attached = 3.dp`（贴附输入面那档） |
| `ui/tokens/Spacing.kt` | 新增 `dockedSearchBarHeight = 56.dp`，与既有的表单输入框 `searchFieldHeight = 48.dp` **分档**，不混用 |
| `components/venera/VeneraChip.kt` | **契约扩张（加性）**：`VeneraChipVariant` 增 `Filter` / `Neutral` 两档；新增可选参数 `leadingText`（次要前缀）。默认值不变，既有 6 处调用点行为一字未动 —— 之所以扩展它而不是另写一枚 chip：组件自己的 KDoc 明写"这是**唯一**的 Chip 实现，禁止再复制一套 UI" |
| `gallery/ui/GallerySearchArea.kt` | 新增：内联搜索区（搜索条 + 提交钮 + 源 chips + 条件 chips + 补全 + 最近搜索），并承接 `GallerySearchEnd` 页尾读数 |
| `gallery/ui/GallerySearchHeader.kt` | **删除**（第二轮那版头部，含 Hero 底与 ← ），按可逆清理路径移到 `build/_trash-from-repo/GallerySearchHeader.kt.replaced` |
| `gallery/ui/GalleryScreen.kt` | 顶栏常驻（不再 `if (!svm.active)`）、搜索区进 `bottomContent`、避让走 `animateDpAsState`、`heroCover` 与 `CoverHeroBackdrop` 引用移除、图标担开合、搜索态收掉「换一批」 |
| `gallery/ui/GallerySearchViewModel.kt` | `pickHistory`（切站+装 chips+立刻搜）→ `applyHistory`（切站+装 chips+按需填输入框，**不自动搜**） |

保护域仍然一个字没动：`Navigation.kt`、底栏枚举。改动全在 `gallery/` 包 + token/组件层的加性字段。

## 十五、真机待验（第四轮，取代 §十三 里与形态有关的那几条）

① 点顶栏 🔍 → 标题栏**不动**、搜索区在它下面展开、下面那面墙被平滑推下去（不能有"整屏一跳"）；
② 展开后顶栏那枚图标应是 ✕，点它收起；搜索框里**有字时**点 X 只清空输入、搜索区不收；
   **空了再点**才收起 —— 这两个动作的先后要在真机上验一次手感；
③ 搜索胶囊是不是实底 `surfaceContainerHigh` + 微弱阴影（不该再透出后面的图）；
④ 提交钮：没选标签时是灰底弱字（明确"按不动"），选了标签之后是**饱和主色实底 + onPrimary 字**
   —— 这一条是本轮要被指"对比度太低"的正名，请对比一眼；
⑤ 源 chips 一行：未选描边 / 选中深紫灰填充，横向能滚；换站仍会清掉上一站的 chips；
⑥ 「最近搜索」那一行：标题灰小字、chip 无描边、**源名比关键词淡且小**；
   点单标签历史 → 切站 + 关键词进输入框 + 出补全；点带 `-` 或多标签的历史 → chips 装全、
   输入框留空（不该出现"没有以它开头的标签"那句）；
⑦ chips 行末尾那枚「清空」按下去真的把条件与输入一起清掉、结果那一屏还在；
⑧ 搜索态下顶栏**没有**「换一批」（这是刻意的，不是丢了）；退出搜索回来它仍在，且日榜那片
   的滚动位置与批次都没变；
⑨ 键盘弹起时输入框与补全列表不被盖住（`imePadding` 现在在顶栏的 bottomContent 里，与之前不同层，
   要现验）；
⑩ 大标题折叠（下滑）时搜索区跟着顶栏一起走，不该出现被裁一半或盖住第一行卡片。

## 十六、第五轮：标签胶囊进输入框 + 同步搜索（现行形态）

用户看了三张真机截图判「效果有点差」，并给了新的取数方式：
**「不要这样搜索的方法，弹出预测词选中后直接在搜索框内用胶囊显示标签，同步搜索」**。

### 16.1 先量，再改（三条都是像素，不是感觉）

| 量到的 | 数 | 结论 |
|---|---|---|
| 搜索区高度（输入态，图二） | 从 y≈366px 到网格首行 y≈918px ⇒ **≈201dp** | 约屏幕 1/4 被一块输入区吃掉，图一里网格只剩一条缝 |
| 顶栏玻璃带与搜索区的交界（图三） | 硬边切在 **y≈268px≈97dp** | 上面是实时模糊（透出网格照片），下面被我涂成不透明 near-black —— **同一块面板两种材质**，这就是"廉价"那一眼的成因 |
| 一屏 ✕ 的数量 | **2 枚**（顶栏一枚 + 输入框内一枚），职责还不同 | 用户要猜哪枚管什么 |

### 16.2 改了什么

1. **提交钮整颗去掉**（推翻 §十四 里"右侧放 MD3 Filled Button"那条 —— 那是上一轮用户自己的口径，
   这一轮被新口径覆盖）。选一枚补全词 = 加一枚胶囊 = 防抖 250ms 后自动重查；
   删胶囊、点胶囊改成排除、换站同样自动重查。
   **空条件不发这一笔**：站方对空 `tags` 回的是"最新一批"，摆成搜索结果就是假结果 ——
   所以删到零枚时整段退回输入态、屏上回到日榜那片、搜索区仍开着。
2. **同步搜索要能打断上一笔**：`runSearch(1)` 先 `searchJob?.cancel()` 再发，
   否则连选两枚标签时屏上留下的是上一串标签的结果（看着就是"点了没反应"）。
   收尾只在"这一笔还是当前那笔"时才改 `isSearching` —— 被取消的旧协程不能替新的灭灯。
   续页仍挡并发（同一批结果里不该并着取两页）。
3. **胶囊进框**：`TokenField` 里胶囊排在文字行的**上面一行**（同一枚胶囊面内，`heightIn(min=56dp)` 往上长），
   三个删除入口分工明确 —— 胶囊自带 × 删那一枚；框里没字时退格删最后一枚；点胶囊本体仍是改成排除。
   退格那条只是顺手：软键盘的退格事件不是每个输入法都送进 Compose，**不能把删除只押在它身上**。
4. **不透明底撤掉**：顶栏那层 progressive blur 本来就画到 `bottomContent` 整块，
   撤掉之后静止时透出的是同一层氛围底、滚动时整块一起糊，硬边消失。
5. **高度收**：历史从 FlowRow 换行改成**单行横向滚动**；提示行只在框里有胶囊时出现；
   补全从 8 行收到 **6 行**且每行从"套一枚 VeneraCard（≈56dp）"改成单行紧凑项（≈32dp）——
   候选列表是"扫一眼挑一个"的东西，不该有卡片级气泡。原来那行"已选条件 chips"整行还给网格。
   预估 201dp → 输入态 ≈130dp（无历史换行、无重复条件行）。
6. **✕ 收成一个入口**：收起只归顶栏那枚 ✕；框里那枚只管往回退（有字清字 → 无字删最后一枚胶囊 →
   都没了就消失）。
7. 顺带删掉一个**零调用点**的方法 `clearConditions`（「清空」那枚 chip 随条件行一起没了 ——
   每枚胶囊自带 × 之后它不再值一行高度）。

### 16.3 基础组件第二次加性扩张

`components/venera/VeneraChip.kt` 新增可选参数 **`onRemoveClick`**：给 `trailingIcon` 单独一个动作。
理由是这一轮的形态：**整枚胶囊的点击被"改成排除"占了**，那"删掉这枚"必须挂在 × 那一小块上 ——
否则要么两个动作打架，要么画一个按不动的 ×（假开关）。× 的触达位往外扩了一档
（原尺寸零内边距在真机上基本按不准，会连整枚的点击一起误触发）。
默认 `null` = 行为与之前一字不差，其余调用点不受影响。已登 `FREEZE-STATEMENT.md` 追加八。

### 16.4 真机待验（这一轮的，取代 §十五 里与提交方式/高度有关的条目）

① 选一枚补全词 → 胶囊**出现在框内**、下面网格直接换血（没有"还要按一颗钮"这一步）；
   连点两枚只发一笔（防抖），且第二笔要**盖掉**第一笔（不能留下上一串的结果）；
② 点胶囊上的 × 删那一枚；框里没字时按退格删最后一枚（这条按输入法而定，不通不算缺陷）；
   点胶囊本体仍是改成 `-tag`；
③ 删到零枚：搜索区退回输入态、屏上回到日榜那片、历史行回来（**不该**出现"已摆出 0 张"那种假结果）；
④ 顶栏与搜索区之间**那道硬边没有了**：静止时两截是同一层底，滚动时一起糊；
⑤ 搜索区高度：输入态应明显比 201dp 矮，历史只占一行且能横滑；
⑥ 一屏只剩顶栏那一枚 ✕ 管收起；框里的 × 只在有东西可退时出现；
⑦ 补全 6 行紧凑项在深色下仍读得清（档位·张数那截是 tertiary 小字）；
⑧ Danbooru 加第 3 枚：胶囊拒收、当场出那句预算提示（同步搜索不会把 422 打出去）；
⑨ ~~换站会清掉胶囊~~ 并**立刻**按新站重查 —— **换站保留胶囊这一条已于 2026-09-26 由用户改判**
   （口径：两站 tag 很多可共用，换站是换数据源不是换"我要找什么"），行为见 §十六 之后的落地记录；
⑩ 若未选中的源 chip 那圈描边比选中态还抢眼（截图上有这个倾向），就把 `Filter` 未选描边
   从 `outline` 降到 `outlineVariant` —— 一行改动，先听真机。

## 十七、第六轮：把 §十一 那条结论纠正过来 —— 真凶是**受管制标签**（2026-09-26）

真机反馈："danbooru 的还是没法获取，我已经登录的了，而且也验证邮箱了"。
把 §十一 那条成因从头复核，结论**反过来了**。

### 17.1 官方出处（唯一标准）

- `wiki_pages/help:censored_tags`：**用 API 取**（`/wiki_pages/help%3Acensored_tags.json`）——
  抓 HTML 那条路会被 Cloudflare 的 "Just a moment" 挡回来。原文：
  > Posts with the `loli` and `shota` tags are **blocked for Member-level and logged out users**.
  > You may view blocked posts by being promoted to a **Builder** for contributing to the site,
  > by upgrading to a **Gold** account (Currently unavailable. See topic #21157),
  > or by winning a **Platinum** upgrade raffle.
- `wiki_pages/help:users` 的 Browsing Differences 表里有对应的两行：
  `View censored tags` → 无账号 ✗ / **Member ✗** / Gold ✓ / Platinum ✓ / Builder ✓；
  `Max tags per search` → 2 / 2 / 6 / 不限 / 不限。
- 同页解释了等级来源：*A user is restricted if they signup using a VPN or proxy* ——
  受限账号（level 10）**验证邮箱前不能编辑**。**实测用户账号 `user_level` 就是 10。**

### 17.2 站方行为（本机 curl，UA `Venera/1.0 (Android)`，全部 200、无 CF 拦截）

| 查询 | 条数 | 顶层键数 | 带 `preview_file_url` |
|---|---|---|---|
| `cat` / `touhou` | 3 / 2 | 46 | 3 / 2 |
| `rating:s` / `rating:q` / `rating:e` | 5 | 46 | 5 / 4 / 4 |
| **`cat rating:e`** | 5 | **46** | **5** ← 成人在匿名侧**完全可见** |
| `loli` | 200 | **42** | **0** |
| `loli rating:s` / `loli -rating:e` | 5 | 42 | 0 / 0 |
| `shota` / `lolicon` | 5 | 42 | 0 / 0 |

- **被抹的是整整四个键**，不是给 null：`file_url` / `large_file_url` / `preview_file_url` /
  `media_asset`（46 → 42）。`tag_string` **照给** —— 所以判据只能、也只用条目自己的标签
  做逐字比对（`loli_(genshin_impact)` 是另一枚标签，不受影响）。
- **与分级无关**（`loli rating:s` 一样 0 张）、**与登录无关**（level 10 登录着一样 0 张）、
  **与搜索词也无关**（`lolicon` / `shotacon` 是空标签，post_count 实测 0；靠标签蕴含命中的
  仍是带 `loli` 的那批）。
- 于是 §十一 那枚红鲱鱼（`posts.json?search[tags]=…` 不做过滤）的结论不受影响，仍然作废。

### 17.3 落地成什么（代码）

- 规则只写一处：`DanbooruClient.CENSORED_TAGS` + `canViewCensoredTags(level)`
  （门槛 = Gold = 30，与"6 枚标签"同一档）+ `hasCensoredTag(post)`；
  查询侧 `GallerySearch.censoredTagsIn(site, filters)`（**排除项不算命中**）。
- **两处读数，两个时机**：发车前在展开卡里预警（`censoredQueryNotice`，只在
  条件命中且等级看不到时说，否则就是噪音）；拿回来后在空态/页尾按成因说
  （`noImageNotice` 的分支由"成人分级"改成"受管制标签"，并明确写出**登录解锁不了**）。
- **删掉了**那枚「排掉成人分级再搜一次」：前提就是错的，按下去是白按
  （`loli -rating:e` 依旧 0 张），比没有更糟 —— 它同时在暗示一个不存在的原因。
- **登录解不了这件事**，所以账号卡文案一并改了：登录只买到"标签预算按等级算"，
  而且 Member 仍是 2 枚；卡出现时 `DanbooruAccount.refresh()` 复查一次等级，
  免得"验证完邮箱了、App 里还显示登录那一刻的等级"。

### 17.4 单测

`GalleryCensoredTagTest`（5 条）：门槛是 Gold 所以 Member 与未登录一样看不到 /
判据是条目上的那两枚标签（`loli_(genshin_impact)` 不算）/ 查询里含管制标签要能在发车前看出来 /
排除项不算命中 / `lolicon` 不在名单里但搜出来的条目带着 `loli`。

### 17.5 真机复核（8bfdaeb5，2026-09-26）

- 搜 `loli`：卡里出预警；空态写"跳过 200 张，其中 200 张带 loli / shota… 会员与未登录一样
  取不到图（登录解锁不了它），要 Gold / Builder / Platinum 才行。换搜索词也绕不过"；
  那枚假出口已消失。
- 搜 `touhou`：图正常出、预警消失（正常路径未被改坏）。
- 全程无 FATAL。
