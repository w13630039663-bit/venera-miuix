# 画廊标签三项：分桶画师栏位 / 点标签返回丢搜索态 / 标签汉化词典

方案文档 · 2026-09-28 · **仅方案，未动代码**

本轮三条都来自用户点名的现象。三条的共同点：**站方数据面比想象中薄**，
而仓库里已经躺着不少"写好了但没接线"的件，所以先全部实测再谈改。

---

## §〇 实测底账（这一节是三条方案的共同依据）

取数环境说明：本机直连 `yande.re` / `gelbooru.com` / `sleazyfork.org` **都不通**
（一个 RST、一个超时，`curl` 走默认路由 http=000）；
`-x http://127.0.0.1:7890` 全部可达。下面每条都标了是走代理还是直连，
复跑时照抄。GitHub API / raw 直连可用。

### 0.1 语料

`https://yande.re/post.json?limit=200`（走代理，1 笔请求）取到 **200 张真帖**，
拆 `tags` 空格串去重得到 **483 枚标签 / 2,034 次出现**。
本轮所有覆盖率都对着这一份算，不引别的语料。

### 0.2 「画师在第一位」这条观察：对，但来源不是 API

真帖 `id=1269641` 同一条数据的两种摆法（原文抄）：

```
API   tags = "cameltoe gijang no_bra pantsu pubic_hair see_through thong undressing wet wet_clothes"
HTML  tag-list = "gijang cameltoe no bra pantsu pubic hair see through thong undressing wet wet clothes"
```

- API 那串是**字母序**（c < e < r < s < s < t），分类信息在序列化时被抹平；
- 站方网页把 `gijang` 排在最前 —— 用户看到的"画师第一位"是 **HTML 的分组顺序**，不是数据顺序。

**所以不能拿位置当判据。** 这条先钉死，否则后面全是空中楼阁。

### 0.3 post 端点确实没有分类（复核了旧结论）

`post.json?limit=1` 现查 44 个键，逐名列出：

```
id,tags,created_at,updated_at,creator_id,approver_id,author,change,source,score,md5,
file_size,file_ext,file_url,is_shown_in_index,preview_url,preview_width,preview_height,
actual_preview_width,actual_preview_height,sample_url,sample_width,sample_height,
sample_file_size,jpeg_url,jpeg_width,jpeg_height,jpeg_file_size,rating,is_rating_locked,
has_children,parent_id,status,is_pending,width,height,is_held,frames_pending_string,
frames_pending,frames_string,frames,is_note_locked,last_noted_at,last_commented_at
```

`docs/rounds/gallery-viewer-toolbar-and-infosheet-2026-09.md` §二 那条"44 个键里没有分类字段"**复核成立**。

### 0.4 但两站的 **post HTML 自带分类类名**（本轮的出路）

| 站点 | 匿名取该帖 HTML | 实测到的类名 |
|---|---|---|
| yande.re | `index.php` 之外走 `/post/view/{id}`，36,282 B | `tag-type-artist`, `tag-type-general` |
| Gelbooru | `/index.php?page=post&s=view&id={id}`，43,007 B | `tag-type-character`(2), `tag-type-copyright`(2), `tag-type-general`(32), `tag-type-metadata`；该帖**没有** artist 类 |

两边都是 `<li class="tag-type-xxx"><a …>标签名</a></li>` 的形状，
标签名用**空格**（`no bra` / `imai lisa`），而 API 用**下划线**（`no_bra` / `imai_lisa`）。

Gelbooru 那 43KB 是**匿名**拿到的（站方只把 **DAPI** 锁成匿名 401，帖子页没锁）。

### 0.5 tag 端点：单枚精确可查、不能批量、能按档枚举

| 试探 | 结果 |
|---|---|
| `tag.json?name=gijang` | `[{name:"gijang",count:136,type:1}]` —— 单枚精确给档 ✅ |
| `tag.json?name=ricol` | 回 4 行**子串**匹配（`fleia_de_ricoluna`/`ricol`/`seikoushikai_bricoleur`/`tricolour_lovestory`），**是 LIKE %x% 不是前缀** |
| `tag.json?names=a b c` / `?name=a,b,c` / `?query=…` | 全部退化成"最新标签"或空 —— **批量精确查不存在** ❌ |
| `tag/query.json?query=…` | 404 |
| `tag.json?type=1&order=count&limit=200&page=N` | ✅ 可按档+按帖数枚举（第 1 页 `kantoku` 4212 …，第 2 页 504→350） |
| Gelbooru `s=tag`（匿名） | `index.php` 版回空 body、`api.php` 版回 404 页 —— 与 `GelbooruClient.kt:26` 那条"DAPI 匿名一律 401"同向 |

### 0.6 现在那行「作者」是**假读数**

`YandeReClient.kt:211` `author = author`、`GelbooruClient.kt:393` `author = owner`。
真帖 `1269641`：`author = "Arsy"`，而该帖真正的画师标签是 `gijang`（站方给 `type=1`）。
`Arsy` 是**上传者的登录名**。Gelbooru 侧 `owner = "beamblue367"` 同理。

→ 「关于这张图」现在摆的那行「作者」，两站都是**上传者**，不是画师。

---

### 0.7 用户追加的第三份词典：`ffdkj/…Danbooru_Tag-Chinese-English-Translation-Table`（2026-09-28 补测）

这一份把 §三 的结论**整个推翻重来**，因为它同时喂两件事。

仓库实况（GitHub API 现查）：**MIT**（`LICENSE` 原文 "MIT License / Copyright (c) 2026 ffdkj"）、
★78、最后推送 **2026-09-27**、README 自报**每日更新**、收录**所有 post_count ≥ 10 的 Danbooru 标签共 330,241 条**，
译文来源标注为 "Gemini 3 Flash 翻译 + 能工智人校对"，另有纠错站点 `tagsuggest.zeabur.app`。
数据只有一个文件：`tag.sqlite` **24,387,584 B**，表 `tags(name PK, category, cn_name, post_count)`。

**它自带 `category`**，编号与本项目 `GalleryTag.kt:42` 那张表**同一套**（0 通用 / 1 画师 / 3 作品 / 4 角色 / 5 元数据）。

档位分布与体量：`category=1 画师 153,084 行`、`4 角色 103,514`、`0 通用 52,793`、`3 作品 20,105`、`5 元数据 745`；
`cn_name` 与 `name` 相同（即没有中文译名、多为人名）的行 **46,804** 条。

#### 分类准确率（无偏真值：站方自己的 post HTML 类名）

不是拿另一个词典比，而是拿**两站网页的 `tag-type-*` 类名**当答案。取 yande.re 真帖 10 张 + Gelbooru 最新 12 张，
逐枚比 `ffdkj.category` 与站方判定：

| 站 | 去重真值枚数 | 表里**收得到** | 收到后**档位一致** | ★站方判画师→表也给画师 | 表判画师而站方不判（误报） |
|---|---|---|---|---|---|
| Gelbooru | 253 | **248 = 98.0%** | **248/248 = 100%** | **10/10 = 100%** | **0 例** |
| yande.re | 56 | 41 = **73.2%** | 40/41 = **97.6%** | 5/7 = **71.4%** | **0 例** |

- 唯一一处不一致：`firefly` 站方=角色(4)、表=通用(0) —— 同名异义，Danbooru 与 yande.re 收了不同词条。
- yande.re 漏的两枚画师：`xix_arts`、`miura_naoko`；**漏的方向是"不摆画师行"，不是摆错**。
- yande.re 收不到的 27% 是 **Danbooru 已删除/改名而 yande.re 仍活着**的词条：`pantsu`、`see_through`、`seifuku`、`megane_neko`、`tinkle`、`clamp`。

**这一条直接改变了 §0.6 那次抽查的意义**：`halo`/`elf` 是 **EhTagTranslation 判错**（它把当过社团的通用词记成 artist），
而 ffdkj 与站方一样给 `halo`=0(光环)、`elf`=0(精灵)。**"表分类不能用"这条旧结论只对 EhTag 成立，对 ffdkj 不成立**。

另有一组 20 枚的定向抽查（按档各抽、逐枚 `tag.json?name=` 问站方，走代理）：19 枚可比对**全对**，
第 20 枚 `azur_lane` 是 yande.re 压根没这枚标签（子串匹配回 50 行无关项）→ 归"收不到"，不产生错桶。

#### 汉化覆盖率（同一份 483 枚 / 2,034 次语料）

| 词典 | 去重覆盖 | **按出现次数加权** | 许可 |
|---|---|---|---|
| 仓库现有 `tags.json`（EhTag 旧快照） | 21.3% | — | CC BY-NC-SA 3.0 CN（**没在 `assets/licenses/` 登记过**） |
| EhTagTranslation 全库 | 25.5% | 27.0% | 同上 |
| `Yellow-Rush/zh_CN-Tags` | 36.2% | 70.6% | **无 LICENSE**、2023-12-15 起停更 |
| **ffdkj（有译名的行）** | **60.7%** | **69.3%** | **MIT** ✅ |
| ffdkj ∪ EhTag | 67.5% | 72.3% | 混合 |
| ffdkj ∪ zh_CN-Tags | 67.1% | 86.1% | 含无许可那份 |
| 三家全并 | 73.5% | 88.4% | 含无许可那份 |

> 注意 ffdkj **去重覆盖最高但加权只 69.3%**、和 zh 的 70.6% 打平：
> 因为它那 46,804 条"译文=原文"的人名单算了收录不算译文。
> **加权掉的那 30.7% 主要是 Danbooru 已删词条**（§0.7 上面那批），不是翻译质量差。

抽样看了 16 条译文观感：`1girl→单人女性`、`thighhighs→过膝袜`、`blue_eyes→蓝瞳`、`no_bra→未戴胸罩`、
`skirt_lift→掀裙`、`tagme→待补充标签`、`azur_lane→碧蓝航线`、`imai_lisa→今井莉莎（BanG_Dream!）`、
`higa_yukari→绯贺由香里`、`koikawa_minoru→鲤川实`；而 `gijang→Gijang`、`huke→huke`、`wada_arco→wada_arco`
这类**罗马字照抄**是刻意的（无公认中文名），落到 §3.3 的「译文 (原文)」双显口径上正好退回原文显示，不算缺陷。

#### 打包体积（构建期从 sqlite 导出 TSV，`zlib.gzip` 实测）

| 打包档位 | 行数 | gzip 后 | 加权覆盖 |
|---|---|---|---|
| 全量（post_count ≥ 10） | 330,241 | **6.18 MB** | 69.3% |
| post_count ≥ 50 | 127,375 | **2.35 MB** | 67.0% |
| post_count ≥ 100 | 79,465 | **1.44 MB** | 66.1% |
| post_count ≥ 300 | 35,755 | 0.62 MB | 62.2% |

`≥50` 这一档用 **1/2.6 的体积换掉 2.3 个点覆盖**，是明显的甜点；`≥100` 再省 0.9MB 只多掉 0.9 个点，不如 `≥50`。
（对照 §包体口径：本仓对包体增量敏感，见记忆「预测式返回与 AOSP 跨 activity 事实」里那条 +40MB 脏增量的教训。）

---

## §一 第 1 项：按分类分桶 + 给画师单独一个栏位


### 1.1 判据

**主判据 = 站方自己的分类**（§0.4 的 HTML 类名），**兜底判据 = ffdkj 表的 `category`**（§0.7）。
两者都不是"按名字猜"：前者是本站对该帖的判定，后者是 Danbooru 官方词条分类，
而 §0.7 实测它与 yande.re / Gelbooru 的**画师档误报 0 例**。

**EhTagTranslation 的分类一律不用**，这是那轮抽查的直接结论 ——
语料里落在它 `artist` 组的标签有 31 枚，抽 8 枚问站方真档（逐枚 `tag.json?name=`，走代理）：

```
gijang          词典=画师(ギザン)    站方 type=1 ✅
chochomi        词典=画师            站方 type=1 ✅
koikawa_minoru  词典=画师            站方 type=1 ✅
laserflip       词典=画师            站方 type=1 ✅
kantoku         词典=画师(监督)      站方 type=1 ✅
higa_yukari     词典=画师            站方 type=1 ✅
halo            词典=画师(HALO)      站方 type=0 ❌ 通用
elf             词典=画师(ELF)       站方 type=0 ❌ 通用
```

**6/8 = 75%**，错的正是 `elf`/`halo` 这类"当过社团的通用词"。
`GalleryInfoSheet.kt:306` 那句既有警告（"硬按标签名猜分类会静默分错，比不分更坏"）**对这份词典成立**。
⚠️ 同两枚在 ffdkj 里给的是 `halo=0 光环`、`elf=0 精灵`，**与站方一致** ——
所以"词典分类不可信"这句话**绑的是 EhTag，不是所有词典**，别在下一轮把它又套到 ffdkj 身上。

### 1.2 取数

- 新增 `GalleryTagCategories`（放 `gallery/data/`）：
  `suspend fun fetch(site, postId): Map<String, Int>?` —— 取该帖 HTML（§0.4 的两个 URL），
  扫 `<li class="tag-type-(artist|character|copyright|general|metadata)">`，
  取**同一条目里那条 `…&tags=<名字>` 链接的参数值**（不是锚文本），
  产出 `标签名 → 档位数字`；档位数字直接用 `GalleryTag.kt:42` 那张表的同一套编号
  （artist=1 / copyright=3 / character=4 / general=0 / metadata=5），**不新造枚举**。
- **接键已实测**：yande.re 3 张帖里，HTML 的 `tags=` 值与 API `tags` 串**逐字相同**（0 例不符），
  而锚文本只是把下划线换成空格（`no_bra` ↔ `no bra`，10/10）。
  → **取 href 参数、不取锚文本**，就不需要归一化；锚文本那份空格形式是站方显示用的，别去还原。
  Gelbooru 同结构（`&tags=bang_dream%21`，需 URL 解码），**但 API 侧要账号才能取到，这条留真机验**（§六③）。
- 触发时机：**只在「关于这张图」那面板打开时发这一笔**，
  不进大图页就一笔不发 —— 多数浏览行为根本不看标签。
- 缓存：按 `(site, postId)` 放 `LruCache`（容量与画廊其它缓存同口径），
  同一张图反复开合不再发请求。
- 复用 `YandeReClient` / `GelbooruClient` 现有的那台 OkHttp（含过盾拦截链），不新造网络栈。

### 1.3 摆法

`GalleryPost.tagGroups` **保持原样**（解析期仍只出「标签」一桶）；
分类结果作为**页面侧的一份叠加状态**，`GalleryInfoSheet` 渲染时按档位把 `post.tagList` 重分组：

- 顶部信息卡：命中 `type=1` 时新增一行 **「画师」**（药丸形式，可点=沿用现有 `onSearchTag`）；
  同时把现在那行「作者」改字为 **「上传者」**（§0.6 那条假读数就地改掉，值不变、只改读数口径）。
- 标签墙：按 `画师 / 角色 / 作品 / 通用 / 元数据 / 标签` 的桶序摆，桶名与配色**全部走现成的**
  （`galleryTagCategoryLabel`、`GalleryTagCategoryColors`）。
- `GalleryInfoSheet.kt:143` 那条 `if (post.tagGroups.none { it.label == "画师" })` 的死条件一并清掉
  —— 它是 Danbooru 那版留下的字符串痕迹，两站恒不命中。
- 渲染件复用现成的 `GalleryTagGroups` / `GalleryTagChip`，**不新写组件**。

> 分桶接口 `GalleryTagGroup(label, tags)` + `galleryTagGroup()` + 五档配色 + 档位名表
> 全都在（`GalleryPost.kt:201-213`、`GalleryTag.kt:42-50`、`Color.kt:198-221`），
> 是 2026-09-26 换站前为 Danbooru 五串写的；今天只剩补全行在用。本轮是把它接回详情页。

### 1.4 降级（宁可少摆，不可摆错）

分两档，**兜底只补「画师」一栏，不补其它桶**（理由见 §0.7：表判画师而站方不判 = 0 例，
而表对 yande.re 只有 73.2% 收录率，拿它补齐全五桶会把三成标签挂进错桶）：

| 情况 | 屏上 |
|---|---|
| HTML 取成功 | 五桶齐全，站方判定为准（表只在站方没给某枚时**不参与**） |
| HTML 取失败 / 解析不出任何分类 | 退回**表分类的画师栏**（离线、零请求）：表里 `category=1` 且这枚确在 `post.tagList` 里 → 摆「画师」行；其余标签仍是一桶「标签」 |
| 表也收不到这枚（yande.re 约 27%） | **与今天完全一样**：一桶「标签」+「上传者」行，不出现任何桶名变化 |
| HTML 只解析出部分标签 | 对不上的进 **「标签」** 桶，**不塞进「通用」** —— 「通用」只在站方明说时才出现 |
| 该帖没有画师标签（Gelbooru 很常见，§0.4 那张真帖就没有） | **不摆「画师」行**，不摆空行、不摆 `—` |

> 这条兜底成立的前提是 ffdkj 那份 MIT 表被打进包（§三）。**若 §三 最终决定不打包，
> 这一档自动退掉，降级表第三行往上移** —— 两件事有依赖，别只做一个。

### 1.5 测试

- 单测：HTML 片段 → 分类映射（四档各一条 + 大小写/空格归一 + 无分类时回 null）。
- 单测：`GalleryPost.tagList` + 分类映射 → 分桶结果；重点锁两条
  「未命中不落通用」「无画师不出画师桶」。
- 现有 `GalleryPostParsingTest.kt:89`（`本站没有分类字段，标签只出一桶`）**不动** —— 解析期行为不变，
  分桶是渲染期叠加。

---

## §二 第 2 项：点标签搜完之后返回掉回日榜 —— 先取证再改

### 2.1 现状：静态读代码读不出这个结果

用户给的路径（§问题一的选择）：**搜索已经出结果的那面墙 → 点图 → 关于这张图 → 点标签**。

这条路上每一步都查过：

```
GalleryPostScreen.kt:543-546   postTag(site, tag) → infoOpen=false → onBack()=finish()
GallerySearchHandoff.kt:27     pending by mutableStateOf(...)   ← 快照字段，跨 Activity 可回读
GalleryScreen.kt:187-193       LaunchedEffect(pending) → consume() → svm.acceptHandoff(...)
GallerySearchViewModel.kt:264  switchSite(同站即早退) + active=true + filters=... + runSearch(1)
```

- `svm` 作用域 = 画廊那条 `NavBackStackEntry`（`GalleryScreen.kt:132`），
  大图页是**独立 Activity**，`finish()` 不动导航栈（`Navigation.kt:674-685`，GalleryRoute 无 query）；
- MainActivity **没有** `onResume`/`recreate()` 钩子（grep 过 `MainActivity.kt` 与全仓 `ON_RESUME`，
  只有 `HomeScreen.kt:154`、`settings/AppSettings.kt:83` 两处，都不在画廊子树）；
- `GalleryScreen` 内**没有任何** `active = false` 的自动路径；两处 `closeSearch()`
  分别是 BackHandler（`:180`）和顶栏那枚 ✕（`:811`），都要用户点。

结论：**代码里"应该回得到搜索那一屏"**，与真机表现矛盾。
按记忆「别凭推断断言做不到 / 自己算的数字要先自审」，这里**不下判断**，先加只读探针。

### 2.2 探针（只读，不改判定，照 `06b187b` 那一条的做法）

一个日志 tag `GalleryNavProbe`，四个观察点，**四条读数互相能拆开四种病因**：

| # | 打点处 | 读数回答的问题 |
|---|---|---|
| 1 | `GallerySearchHandoff.postTag()` 写入侧 | 站点键、标签、当时 Activity 状态 —— **槽到底写没写成** |
| 2 | `GalleryScreen` 里 `LaunchedEffect(pending)` 入口 | 收到 / 收到但为 null / **压根没进过这一行** —— **消费方活着吗** |
| 3 | `GalleryScreen` 外层 `DisposableEffect` 的 enter/dispose | 这一屏**有没有被移出组合再重建** |
| 4 | `GallerySearchViewModel` 的 `init` / `onCleared()` | ViewModel **是不是新造的一个**（若是，则导航条目被重建） |

四组读数分别对应：槽没人收 / 收了但条件被后续清掉 / 条目重建换了新 ViewModel / 交接根本没触发。
定了病因再谈修法，**本轮不预先动导航逻辑**。
（探完整轮已定案并落地：**病因不是链断，是覆盖语义 + 返回一格到底**，见 §7.4.2 / §7.4.3。）

探针日志同时打**当时的 `active/mode/filters.size/page/results.size` 快照**，
这样即便病因在别处，也能从屏上状态跳变序列看出来是哪一步清的。

---

## §三 第 3 项：标签汉化词典

### 3.1 三份候选的来源与许可（两份脚本都扒到源码，第三份是用户追加的）

| 候选 | 词典在哪 | 规模 | 许可 / 新鲜度 |
|---|---|---|---|
| 脚本 421970「yande.re 简体中文」v2.1.47 | `coderzhaoziwei/yande-re-chinese-patch` 的 `source/data/tags.json` | **10,129 B**（界面文案为主，几乎无词条） | MIT / 活跃 |
| 脚本 473170「booru图站汉化插件」v0.9.3 | `Yellow-Rush/zh_CN-Tags` → `yande.csv`、`gelbooru.csv`（另有 danbooru / sankaku） | 每站约 **10,600 行**，`标签,中文` 两列 | **无 LICENSE 文件**；最后提交 **2023-12-15** |
| **`ffdkj/…Danbooru_Tag-Chinese-English-Translation-Table`** | 单文件 `tag.sqlite` 24.4 MB，`tags(name, category, cn_name, post_count)` | **330,241 条**，含 **153,084 条画师** | **MIT** ✅；README 自报每日更新，最后推送 2026-09-27 |

仓库现有的 `assets/tags.json`（34,956 条）**就是 EhTagTranslation 的衍生数据**，且已落后于线上库
（`artist 13,136 → 15,715`、`character 5,263 → 9,841`、`parody 2,249 → 2,963`）。
EhTagTranslation 是 **CC BY-NC-SA 3.0 中国大陆**，
而 `app/src/main/assets/licenses/` 里**只登记了 miuix 那一份** —— 这笔漏记与本轮选不选它无关，都该补。

### 3.2 覆盖率与体积（数字全在 §0.7，这里只说结论）

同一份语料（483 枚去重 / 2,034 次出现）上的**加权**覆盖：

```
EhTag 全库 27.0%   <   ffdkj(MIT) 69.3%  ≈  zh_CN-Tags(无许可) 70.6%   <   ffdkj∪zh 86.1%   <   三家全并 88.4%
```

两条要点：

1. **"换更大的开源词典"这条本身曾被量成退步** —— 那时有理由：EhTagTranslation 体量最大的部分
   是专有名词，而它**没有 general 命名空间**（`other` 组只有 61 条"技术标签"），
   一屏里占版面的 `long_hair`/`blue_eyes` 它一条都没有。**用户追加的 ffdkj 正是补上 general 那一大块**，
   所以那条旧结论现在被推翻了：**存在既大、又干净许可、又真能覆盖的选项**。
2. ffdkj 加权 69.3% 里含一个非缺陷的成因：它有 **46,804 条 `cn_name == name`**（人名照抄罗马字，
   本来就没有公认中文名），按 §3.3 的双显口径会退回原文显示。
   **真正掉的那 30.7% 主要是 Danbooru 已删/改名而 yande.re 仍活着的词条**
   （实测漏样：`pantsu`、`see_through`、`seifuku`、`megane_neko`、`tinkle`、`clamp`）。

体积档（构建期从 sqlite 导 TSV 再 gzip，实测见 §0.7 末表）：
**全量 6.18 MB / `post_count≥50` 2.35 MB（只掉 2.3 个点覆盖）/ `≥100` 1.44 MB**。
`≥50` 是甜点，建议就取这一档。

### 3.3 建议（第 3 项现在与第 1 项有依赖，一起拍）

链路接通是零风险的现成件（`TagDisplay.kt:23` `rememberTagDisplayLabel()` →
「译文 (原文)」双显，**请求侧永远发原文**，见 `docs/rounds/venera-tag-multilang-plan.md` 的 L1 定稿）。
要拍的只有"打包哪份数据"：

- **D（推荐）**：**只打包 ffdkj**（`post_count≥50`，2.35 MB，MIT）。
  一份数据同时给到：显示层加权 **69.3%** 译文 **+ §1.4 那档离线画师兜底**。
  许可干净、每日更新、体积可控，且不引入第三份词表。**同时把 `assets/licenses/` 的 EhTag 漏记补上。**
- **E**：D + 叠 `zh_CN-Tags`（加权 69.3% → 86.1%，多约 1.5 MB）。
  **代价**：那份 csv 无许可，得先联系作者（README 留了邮箱 `2624696826@qq.com` 是 ffdkj 的，
  zh_CN-Tags 侧只能开 issue）；拿到书面授权再合，否则停在 D。
- **F**：D + 把仓库现有 `tags.json` 刷新到 EhTag 线上全库（加权 72.3%，专有名词更齐，
  但要接受 CC BY-NC-SA 的"非商业+相同方式共享"落到包里的含义，并登记出处）。

三条都不动"点标签发出去的是原文"这条不变量；显示层加译文**不影响搜索语义与屏蔽词表**。

---

## §四 本轮明确不做

1. **不按标签名猜分类**（§1.1 有 75% 的实测证据）。
2. **不做逐枚 `tag.json?name=` 的运行时分类**：一张帖 10~30 枚标签就是 10~30 笔请求，
   而每站预算 12s（`docs/rounds/gallery-recommendations-from-favourites-2026-09.md:70-80` 早算过这笔账）。
3. **不引 Danbooru 接口当分类神谕**：`danbooru.donmai.us/tags.json?search[name]=` 实测回
   Cloudflare "Just a moment..."，为一个分桶读数把第三站和过盾面拉进画廊，不值。
4. **不动 `GalleryPost.tagGroups` 的解析期形状**（分桶是渲染期叠加，见 §1.3）。
5. **第 2 项不在本轮预设修法**（§2.2 先取证）。
6. **不重新判 421970 那份 10KB 的表** —— 它是界面文案，没有词条价值。

---

## §五 落点文件清单

| 项 | 文件 | 动作 |
|---|---|---|
| 1 | `gallery/data/GalleryTagCategories.kt` | 新增：取该帖 HTML + 扫类名 + LruCache |
| 1 | `gallery/data/YandeReClient.kt` / `GelbooruClient.kt` | 各加一个 `fetchTagCategories(site, postId)`，复用现有 OkHttp |
| 1 | `gallery/ui/GalleryInfoSheet.kt` | 「画师」行、「作者→上传者」改字、渲染期重分组、清掉 `:143` 死条件 |
| 1 | `gallery/ui/GalleryPostScreen.kt` | 面板打开时触发一次取分类；把结果作为叠加状态交下去 |
| 1 | `gallery/GalleryPost.kt` | **不改形状**；只可能补一句注释指向新通道 |
| 2 | `gallery/ui/GallerySearchHandoff.kt` / `GalleryScreen.kt` / `GallerySearchViewModel.kt` | 只加四处只读打点，不改行为 |
| 3 | `data/tags/TagDisplay.kt`（现成）→ `GalleryInfoSheet.kt:361`、`GallerySearchArea.kt:962` | 把显示层接上；请求侧不动 |
| 3（若 B） | `app/src/main/assets/tags.json` + 构建期脚本 + `assets/licenses/` | 刷新并登记出处 |
| 测试 | `app/src/test/.../gallery/` | 新增两例（类名解析 / 分桶降级），现有 `GalleryPostParsingTest` 不动 |

---

## §六 真机待验（PJZ110 + Pixel Tablet 模拟器，设备只读不代操作）

1. 「关于这张图」打开后画师行出现，且**点画师药丸搜出来的是那张画的作者**（不是上传者）。
2. 无画师标签的 Gelbooru 帖：屏上**不出**空画师行。
3. 断网/站方改版模拟（把类名换成认不出的）：整面板退回今天那一桶，**不出错桶**。
4. 同一张图反复开合面板：分类请求只发**一笔**。
5. 顶栏那行「作者」改「上传者」后，读数与站方网页 "Posted by" 一致。
6. ~~第 2 项探针~~（探针已按 §7.4 删除，第 2 项的**真机清单在 §7.4.3 之后另立一节，见下面 §6.1**）。
7. 汉化接通后：点带译文的药丸，**发出去的查询串仍是原文**（这条必须有正面证据）。

### 6.1 第 2 项（逐级返回）真机清单

1. 搜 `touhou` 出墙 → 点图 → 「关于这张图」点画师 `wowoguni`：落到的是 wowoguni 的墙，
   胶囊**只有 `wowoguni`**（不是 `touhou wowoguni`）。
2. 按**一次**返回：回到 **touhou 那批图**，且**不转圈**（用的是快照、没重发请求 —— 这条是"没走网络"的正面证据）。
3. 再按一次返回：每日推荐；第三次返回按预期退出画廊（不能"多按一次才走"）。
4. 在 touhou 墙上展开改条件（INPUT）→ 返回：先**收成一条**、仍在 touhou；再按一次才走（两档顺序没被打乱）。
5. 在 wowoguni 墙上滑续页到一半 → **立刻**返回回 touhou → 等 3 秒：touhou 墙上**不能**多出来自 wowoguni 的图。
6. 反搜开着按返回：仍然先退回标签搜索那一层。
7. 顶栏 ✕ 一次关掉整层；再点 🔍 回到最后一轮的胶囊与结果，此时按返回**逐级**回退。
8. 连点 9 枚不同标签（每次进大图再返回）：回到底不该出现"某一次返回什么都没变"，也不该 OOM/掉帧。
9. 换站各做一遍 1-2：弹栈回来的**站**也要对（胶囊与图同源）。
10. 从**图片收藏**那面墙开大图点标签：仍是"这一下没反应、切进图库才兑现"（本轮不改这条，只确认没变坏）。
11. 弹栈落点确认：回到 touhou 时是**顶部**（沿用 `searchGeneration` 既有口径，本轮不做滚动锚点还原）。

---

## §七 落地记录（2026-09-28 本轮实施）

§四/§五 是动手前的计划，**这一节才是实际落地的形状**；两者不一致处以此节为准。

### 7.1 实际拍板与偏离计划的四处

1. **第 3 项选了 F**（只打包 ffdkj + 刷新 EhTag + 补登记许可）。
   但画廊侧的译文**只读 ffdkj 一份**，没有做 §3.2 那个"三家并集"：
   并集要的是 EhTag 那 3 个点，代价是同一条链上挂两份词典、两个更新节奏、两种键形
   （EhTag 用空格、booru 用下划线）。一片药丸后面只该有一个出处。
   屏上读数因此是 **加权 66.1%**，不是 §3.2 表里的 69.3%/88.4% —— 见下面第 2 条的档位改动。
2. **档位从 `post_count>=50` 改成 `>=100`**，并且**存成 SQLite 而不是 JSON**：
   §〇 那几档体积是按"打进包"算的，漏算了**常驻堆**。79,415 行灌进 HashMap 是十几 MB，
   而它服务的场景只是"用户打开了那一面板"、每回查屏幕上二三十枚。
   SQLite 是主键查 + 页缓存，常驻归零；`>=100` 又比 `>=50` 少 48% 行数而只掉 0.9 个点覆盖。
   实测包体增量 **1.74 MB**（APK 内 Defl:N，`Length 3257344 → Size 1736780`），
   `tags.json` 刷新那部分再 +0.16 MB。
3. **`GalleryPost.tagGroups` 字段删了**（§五 原本写"不改形状"）。
   面板改成渲染期分桶之后它**一个生产读者都不剩**，只剩两行"永远填一桶「标签」"的写入点 ——
   那是 §「实现了但零调用点」那一类缺口的镜像：**有写没读的死字段最容易骗到下一个人**，
   他会以为分类数据不存在。删字段的同时把那段"站方天花板"注释改写成了真出处指引。
   连带删掉 `galleryTagGroup()` 这个从 Danbooru 那版留下的切桶工具（零调用点）。
4. **画师那一行与标签墙里的「画师」桶不并存**（§1.3 原本两处都写）。
   同一个面板上把同几枚药丸摆两遍是噪声。落地口径：**画师独占信息卡那一行**，
   墙里从 `groups` 里滤掉这一桶；那一行的药丸**不带长按菜单**（复制/屏蔽画师名不是这里要有的能力）。

### 7.2 落地文件

| 文件 | 内容 |
|---|---|
| `scripts/build_tag_dictionaries.mjs` | 新增。两份产物的构建期来源：EhTag 13 个 namespace 的 `.md` 表 → `assets/tags.json`；ffdkj `tag.sqlite` → `assets/gallery_tags_<行数>.sqlite`；两份许可原文 → `assets/licenses/`。缺文件才 curl，`PROXY_URL` 给代理 |
| `app/src/main/assets/tags.json` | 34,956 → **44,344 条**（1,041,388 → 1,350,170 B）。`rows` 多出 `location` 一档 |
| `app/src/main/assets/gallery_tags_79415.sqlite` | 新增 79,415 行 `(name, category, cn)`，`WITHOUT ROWID` 按 name 排。`cn` 为 NULL 表示"词典里译名就是原词"（多为画师名） |
| `app/src/main/assets/licenses/` | 补 `ehtagtranslation-LICENSE.md`（CC BY-NC-SA 3.0 CN）与 `ffdkj-danbooru-tags-LICENSE.txt`（MIT）。**EhTag 那份是本仓一直漏记的一笔** |
| `gallery/domain/GalleryTagBuckets.kt` | 新增。`parseGalleryTagCategories(html)` + `buildGalleryTagBuckets(...)` + 档位常量；**纯函数，全部可单测** |
| `gallery/data/GalleryTagCategories.kt` | 新增。按 `post.pageUrl` 取那张帖的 HTML，`NoInteractiveBypassTag`（不弹盾），按 `site+id` LruCache 64 张；失败交 **null 不交空表** |
| `gallery/data/GalleryTagDictionary.kt` | 新增。首次使用把资产复制到 `databases/`（Android SQLite 要真实路径），**副本文件名就是资产文件名**（带行数 = 数据版本），只读打开；`artistNames()` / `translations()` 各一次 `name IN (?)` 主键查，标签名走占位符不拼串 |
| `gallery/ui/GalleryPostScreen.kt` | 面板打开时才发那一笔（`LaunchedEffect(currentUid, infoOpen)`）；三份额外数据以 `remember(currentUid)` 持有；站方判定拿到时**不查词典兜底** |
| `gallery/ui/GalleryInfoSheet.kt` | 「画师」行（药丸）、「作者」改字「上传者」、渲染期分桶、清掉 `:143` 那条恒不命中的死条件；药丸文本走 `tagDisplayLabel` |
| `gallery/ui/GallerySearchArea.kt` | 补全行整表一次查译名；`onPick` 交出的仍是 `suggestion.name` |
| `gallery/ui/GalleryNavProbe.kt` | 第 2 项的只读取证（§7.4）。**取完现场已整份删除**，读数留在 §7.4.1 |
| `gallery/domain/GallerySearchContext.kt` | 新增（第 2 项）。`GallerySearchContext` 快照值类 + `GallerySearchContextStack.plan/push/pop` + `isStaleContext`；**判据全在这一层，纯函数可单测** |
| `gallery/ui/GallerySearchViewModel.kt` | 第 2 项：`contexts` 栈 + `contextRound` + `openContext()` 单一落点（交接 / 点历史 / 点推荐标签行三条入口都走它）+ `popContext()`；`runSearch` 落地那道闸改成认轮次也认站点 |
| `gallery/ui/GalleryScreen.kt` | 第 2 项：BackHandler 加一档 `svm.canPopContext() -> svm.popContext()`；同时把 §7.4.1 那段**写错的定案**从注释里改回来 |
| 删 | `GalleryPost.tagGroups`、`galleryTagGroup()`、两站 `toPost()` 里的 `tagGroups = …` 两处 |

### 7.3 QA 实跑

- `:app:testDebugUnitTest`：第 1/3 项落地后 **298 项全过、0 失败**（新增 17 项 = `GalleryTagBucketsTest` 12 + `GalleryTagTranslationTest` 5）；
  第 2 项（上下文栈）落地后 **307 项全过、0 失败 0 跳过**（44 个套件，再 +9 = `GallerySearchContextStackTest`）。
  那 9 条是**先跑红再实现**的：第一次跑 `compileDebugUnitTestKotlin` 报了一串
  `Unresolved reference 'GalleryContextPlan' / 'plan' / 'isStaleContext' / 'pop'`（域文件还不存在），
  建出 `GallerySearchContext.kt` 之后才转绿 —— 记录在这里是为了让"这 9 条真能问倒实现"有据可查。
  两条改动的旧测：`GalleryPostParsingTest` 那条"标签只出一桶"改成锁"解析期不产生任何分组"；
  `GalleryTagSuggestionTest` 的测试名不再指向已删掉的 `tagGroups`。
- `:app:compileDebugKotlin`、`:app:assembleDebug`：**BUILD SUCCESSFUL**。
  产物里 `assets/gallery_tags_79415.sqlite` 确认存在、被 Deflate 收了（`Length 3257344 → Size 1736780`，47%），
  `assets/tags.json` 是 `1350170 → 713776`。
- **数据层没法在 JVM 单测里跑**（本仓只有 JUnit，无 Robolectric），所以改成直接验产物：
  用 Kotlin 侧同形状的 `select name,category,cn from tags where name in (?,?,?,?)` 打生成的资产 ——
  `thighhighs→过膝袜`、`gijang→(1, Gijang)`、`huke→(1, cn=NULL)`、`seifuku` 缺席（符合 §7.5③），
  行数 79,415、`name` 无重复、`cn IS NULL` 共 7,992 行（= 译名就是原词的那些，主要是画师名）。
- 单测里锁住的四件"错了也不报错"的事：
  ① 分类的键必须取 HTML 的 `tags=` 参数而不是锚文本（取错=每一枚都静默不分桶）；
  ② `+` 不能被当百分号编码解成空格（`c+union`）；
  ③ 站方判定为空时**只兜画师一栏**，不许摆出「通用/角色/作品/元数据」任何桶名；
  ④ **某条 `<li>` 自己没有检索链接时不许借用下一条的名字** ——
     解析器因此从"一条正则跨 700 字节匹配"改成了**按 `</li>` 切段扫描**。
     跨段匹配会把下一枚标签算进当前这一档，那是"分错桶"，比"没分桶"坏得多且屏上看不出来。

### 7.4 第 2 项：探针已装，等一次真机复现

`GalleryNavProbe` 六个点位（写入 / 消费 / 进组合 / 出组合 / VM新建 / VM销毁 / 交接后），
每条都带 `active/mode/filters/page/results/site` 快照，VM 那两条还带实例号。
读法：

```
adb logcat -s GalleryNavProbe
```

**复现路径**（用户 2026-09-28 选定）：画廊顶栏开搜索 → 加标签出结果墙 → 点一张进大图 →
「关于这张图」→ 点某枚标签 → 看回到的是哪一屏。

四种病因对应四种读数形状：

| 读数 | 结论 |
|---|---|
| 只有 `[写入]`，没有 `[消费] 命中=true` | 那一屏不在组合里，槽没人收 |
| `[消费] 命中=true` + `[交接后] filters=[…]` 正常，但 `[VM新建]` 插在中间 | 导航条目被重建，换了新 ViewModel |
| `[交接后]` 正常，之后某条读数里 `filters=[]` 或 `active=false` | 收了，但被后面某处清掉 —— 按序列找那一步 |
| 连 `[写入]` 都没有 | 点标签那一下根本没走到交接（另一条链，回来再看 `onSearchTag` 的调用点） |

**本轮没有预设修法**：静态读代码的结论是"不该丢"，与真机矛盾，所以先让现场说话。
定案后这份探针整份删掉，不留生产日志。

### 7.4.1 真机读数（与那份**错了的**定案）

原样抄 `adb logcat -s GalleryNavProbe`：

```
13:31:59.154 [VM新建] this#133960569 active=false mode=INPUT filters=[] page=0 results=0 site=gelbooru
13:31:59.205 [进组合] owner=svm#133960569
13:31:59.262 [消费] 命中=false …（冷启动槽里没有东西，正常）
13:32:17.852 [写入] site=yandere tag=chochomi 槽里原本是空的
13:32:17.902 [消费] 命中=true site=yandere tags=[chochomi] 消费前: active=false filters=[] site=gelbooru
13:32:17.902 [交接后] active=true mode=INPUT filters=[chochomi] page=0 results=0 site=yandere
13:32:17.986 [消费] 命中=false …（consume() 自己把槽写回 null 触发的那第二次，无害）
13:36:54.336 [写入] site=yandere tag=kirisame_marisa 槽里原本是空的
13:36:54.364 [消费] 命中=true 消费前: active=true mode=RESULTS filters=[touhou] page=1 results=100 site=yandere
13:36:54.365 [交接后] active=true mode=RESULTS filters=[kirisame_marisa] page=0 results=0 site=yandere
13:36:54.382 [消费] 命中=false …
```

**⚠️ 本节原先写的定案是错的**（"当前包里没有这个缺陷，没有代码可改"）。
错法很典型：探针证的是**链通了**，我把它读成了**行为对了** —— 而用户报的从来不是"链断"，
是"回不到最初那一串标签"。读数里其实已经躺着答案，只是当时没往那处读。

四条读数真正的含义：

1. **ViewModel 自始至终是同一个实例** `#133960569`，`[VM新建]` 全程只出现一次（冷启动那次），
   `[VM销毁]` / `[出组合]` **一次都没有** → 导航条目没被重建，那一屏的组合也没被拆过。
   —— 这一条仍然成立，它排除的是"条目重建换新 VM"那个假设。
2. 每一次 `[写入]` 都在 12~50ms 内跟上一个 `[消费] 命中=true` → 跨 Activity 的快照回读正常。
   —— 也仍然成立。
3. **被我当成"缺陷没重现"的那一条，恰恰就是缺陷本身**：交接前屏上是
   `filters=[touhou] page=1 results=100`，交接后是 `filters=[新那枚] page=0 results=0`，
   **同一个实例、没有第二次写入、没有别处清它** —— 是 `acceptHandoff()` 第 271 行
   `filters = tags.map { … }` **按设计整片覆盖**掉的。上一轮不是"丢了的 bug"，是"本来就要覆盖"。
4. `closeSearch()` 那侧同理：`GalleryScreen` 的 BackHandler 只有两档，收成一条之后那一档
   直接 `else -> svm.closeSearch()` —— 一次返回掉回每日推荐，也是**按设计**。

### 7.4.2 第二次复现与更正后的定案（2026-09-28 用户给出精确序列）

用户原话："搜 `touhou` → 点开图片 → 看到画师 `wowoguni` → 点这个 tag → 跳到了 wowoguni 的搜索 →
**返回就直接回到每日推荐了**，再点击搜索是 wowoguni 的 tag 搜索，就是回不到最初的 touhou 搜索了"。

同一份探针在同一台机器上取到的对应读数：

```
13:44:36.214 [写入] site=yandere tag=wowoguni 槽里原本是空的
13:44:36.242 [消费] 命中=true site=yandere tags=[wowoguni]
                   消费前状态: this#133960569 active=true mode=RESULTS filters=[touhou] page=1 results=100 site=yandere
13:44:36.242 [交接后] this#133960569 active=true mode=RESULTS filters=[wowoguni] page=0 results=0 site=yandere
13:44:36.255 [消费] 命中=false（consume() 自己写回 null 触发的那第二次，无害）
```

**定案（更正后）：缺陷在，而且两条病因都是"按设计执行"的语义**：

| # | 病因 | 落点 |
|---|---|---|
| 1 | 换一轮上下文时把上一轮**整片覆盖**，touhou 那 100 张不留 | `GallerySearchViewModel.acceptHandoff()`（旧 264-277） |
| 2 | 返回**一格到底**：收成一条之后下一档就是关掉整个搜索 | `GalleryScreen` BackHandler（旧 174-182） |

探针按 §7.4 的约定**整份删除**（`GalleryNavProbe.kt` 与六处调用点已删，`grep` 零命中）。
那张"四种病因 ↔ 四种读数形状"表留着当**取证手册**，但要用对它：
它只能回答"链断在哪一环"，回答不了"链通着而行为不对" —— 后者要靠把读数里的
**状态跳变**（`filters` 从什么变成什么）与用户的诉求对齐。

### 7.4.3 修法（2026-09-28 已落地，方案见 `plans` 那一份）

形态这一步用户拍了**同屏 + 上下文栈**（"另外新开一页"那条评估过：结果进不了导航参数、
per-entry VM 会推翻"再点 🔍 回到原上下文"的拍板、nav 栈删不掉中间那条，
而"回到上一轮"这件事本来就不需要新页面 —— 真正修好它的是**栈**）。三条决定：

1. **换一轮 = 压栈**：上一轮（站 / 条件 / 结果 / 页游标 / 每页张数 / 跳过张数）整片存进
   `contexts`，返回先 `popContext()` 逐字段抄回来、**不发请求**；栈空才 `closeSearch()`。
2. **三条入口统一**：`acceptHandoff`（大图页点标签）、`applyHistory`（点历史）、
   `onPickRecommendation`（点搜索卡里那行推荐标签）全部收敛到 `openContext()` 一个落点。
   同屏里不能出现两种语义（点标签能回退、点历史不能），否则下一轮必被当成缺陷再报。
3. **不与上一轮 AND 合并**（`touhou` + `wowoguni` 求交集只剩寥寥几张，恰好把用户想要的排掉）、
   **同站同条件不压栈**（幂等，否则白按一次返回）、**当前没搜过不压空壳**、
   **深度上限 8 轮、超限丢栈底**。判据全在新文件 `gallery/domain/GallerySearchContext.kt`，
   9 条单测锁住（本项目没有 Robolectric，ViewModel 层测不到，所以判据必须在 domain）。

顺带补了一条**没报但一定跟着来**的坑：`runSearch` 落地那道闸原先只比站点
（`if (siteAtRequest != site) return@launch`）。有了栈之后会出现"站没变、轮次变了"的串台：
touhou 正在飞第 2 页 → 交接进 wowoguni → 返回弹回 touhou，旧那笔照常落地就会把
100 张来自错误一轮的图接在正确的墙上（`GelbooruClient`/`YandeReClient` 那笔阻塞式取数
不在挂起点，`searchJob.cancel()` 掐不住它）。现在判据是 `isStaleContext(轮次, 站, 当前轮次, 当前站)`，
**认轮次也认站点**（站点那一半留着：顶栏换站不转轮次，只有它拦得住旧站响应回填到新站上下文）。

顶栏那枚 ✕ 维持"只关这一层、不清栈"（沿用 2026-09-25 那条"chips 与结果不清"的拍板）。
`GalleryPostScreen.kt` 的 `onSearchTag` 一个字没改，`Navigation.kt` 保护域没动。


### 7.5 已知副作用与未做的

1. **`tags.json` 刷新会同时改变漫画侧**：题材统计用的 `topicEntries(female/male/mixed/other/parody/character)`
   词表变大了（character 5,263 → 9,841 等），归一化命中的题材会比昨天多。
   这是刷新线上库的必然结果，不是缺陷；但**下一轮如果有人报"题材统计数字变了"，先想到这里**。
2. **`tags_tw.json` 没有刷新**：EhTag 的 Database 仓库只有简体，繁体那份另有出处。
   现在简体比繁体全，繁体模式下多出来的键按既有口径**原样显示**（宁可不译不可译错）。
   构建脚本的头注把这件事写死了，防止有人以为刷新是两条一起做的。
3. **画廊只接 ffdkj 一份**，所以 yande.re 上 Danbooru 已删/改名的词（实测 `pantsu`、
   `see_through`、`seifuku`、`megane_neko`、`tinkle`、`clamp`）**既没有译文也不进兜底画师栏**，
   表现是"这一枚没汉化"。要补这一档只能再引一份词表，本轮按 §7.1① 的口径不引。
4. **站方改版监控**：`parseGalleryTagCategories` 认不出任何类名时交 null → 走词典兜底 →
   屏上回到今天这一桶。这条**不会报警**，只能靠真机待验 §六③ 那种主动构造去测。
5. **没做**：把译文接进搜索胶囊（capsule 仍显示原词）、给画师行加长按菜单、
   把分类接进「猜你喜欢」的权重表（那是 §「题材统计的数据面坑」另一轮的活儿）。

## §八 真机第三轮回填（2026-09-29）

- **§7.4 那条"画师单独占信息卡一行"改版了**：现在画师/角色/作品一起搬到**与尺寸卡并排的右列**
  （站点那一排之下、出处之上），`ArtistRow` 删掉，右列复用 `GalleryTagGroups`（`showCount = false`）。
  用户点名的读法是"和左边的尺寸分开两列显示"。
- 第 1、3 项的真机待验仍未跑完；本轮新加的三条待验写在 `docs/rounds/gallery-ranking-windows-2026-09.md` §十三。
