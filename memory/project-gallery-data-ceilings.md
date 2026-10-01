---
name: project-gallery-data-ceilings
description: 画廊（图站侧）实测数据面：post 无分类字段但 **post HTML 有 tag-type-\* 类名**、API tags 串是字母序、tag 端点是子串匹配且不能批量、author/owner 是上传者不是画师、汉化词典档位一致性实测；另含 Gelbooru 无 file_size/时长、视频无更小转码档、media3 不吃防盗链表、**yande.re 没有 AI 标签而裸 ai 是角色名**、数 HTML 卡片/画师档要按站取标记、**画师跨站分歧 22/25 同名 + Danbooru 枢纽已被推翻**；09-30 下午补：**pixiv `ajax/illust/{作品号}` 匿名可反查作者（我先前"不通"是错的）且作者名与站方画师档一致率仅 4/11**、**fanbox creator.get 必须带 Origin**、Mastodon 的 missing.png 不是头像、X 无匿名头像路、第三方图标服务不造分辨率、**yande.re 全站只有 16px 粉脸 favicon 与 484×75 横版字标**
metadata:
  type: project
---

2026-09-27 一轮画廊视频/分享的实测攒下的数，都是**照代码看不出来、照文档容易再猜一次**的那类。

**图站侧数据面（实测）**
- 两站 post JSON 都**没有标签分类字段**（yande.re 44 键无 `tag_string_*`、Gelbooru 只有一串平铺 `tags`）→ 分类桶只能一桶；分类只在 **tag 端点**的 `type` 上。
- **出路：两站的 post HTML 自带分类类名**，而且**匿名可取**（Gelbooru 只锁 DAPI，帖子页没锁）：`<li class="tag-type-(artist|character|copyright|general|metadata)">`，yande.re 36KB / Gelbooru 43KB 一页。HTML 里标签名用**空格**、API 用**下划线**，归一后才能对上。
- API 那串 `tags` 是**字母序**，站方网页是**画师在前** —— 所以"画师排第一"是 HTML 的性质，**拿位置当判据不成立**。
- `tag.json?name=X` 是 **LIKE %X% 子串匹配**（不是前缀！`ricol` 会带回 `fleia_de_ricoluna`/`tricolour_lovestory`）；**没有批量精确查**（`names=`/`name=a,b`/`query=` 全部退化成"最新标签"或空）；但 `tag.json?type=1&order=count&limit=200&page=N` **能按档枚举**。
- 按词典命名空间猜画师档**精度只有 6/8**（`halo`/`elf` 在 EhTag 是 artist、站方是 type=0 通用）→ 分桶只能用站方 HTML，词典只配做显示翻译。
- yande.re 的 `author` 与 Gelbooru 的 `owner` 都是**上传者登录名，不是画师**（真帖 1269641：`author=Arsy` 而画师标签是 `gijang`）→ 详情页那行「作者」是假读数。
- **汉化词典：三选一，只有 ffdkj 那份同时满足"大 + 许可干净 + 带分类"**。对 200 真帖语料（483 枚去重 / 2,034 次出现）**按出现次数加权**：EhTagTranslation 全库 **27.0%**（它**没有 general 命名空间**，`other` 组仅 61 条，条目全压在画师/角色这些"枚数多、出现少"的专有名词上）、`Yellow-Rush/zh_CN-Tags`（约 10.6k 条/站，通用词向）**70.6%**、**`ffdkj/ffdkj-Danbooru_Tag-Chinese-English-Translation-Table` 69.3%**（`tag.sqlite` 33 万条、**MIT**、每日更新、**自带 `category` 列**，且是 Danbooru 里唯一补齐了 general 的那一份）。并集只比 ffdkj 单独多约 19 个点，但要挂两份更新节奏与两种键形。
- ⚠️ 上一轮我据此写过"更大≠覆盖更好"，**那条已经被推翻**：当时只在 EhTag 与 zh_CN-Tags 之间比。教训是**别拿两次抽样当结论**，换一次候选就得重量。
- ffdkj 的跨站档位一致性（真值=两站 post HTML 的 `tag-type-*` 类名，22 帖 309 枚去重）：**Gelbooru 名称覆盖 98.0%、档位一致 100%、画师召回 10/10；yande.re 覆盖 73.2%、一致 97.6%（唯一一例 `firefly` 同名异义）、画师召回 5/7**。两站**画师误报都是 0 例** → 它只能用来兜"画师一栏"，不能补满五桶。
- ffdkj 漏的那 27% 是 **Danbooru 已删/改名而 yande.re 仍活着**的词：`pantsu`、`see_through`、`seifuku`、`megane_neko`、`tinkle`、`clamp`、`wada_arco`、`ricol`。
- 反例留档：**EhTag 的命名空间可以判但判错** —— `halo`/`elf` 在 EhTag 是 artist、两站站方都是 type=0 通用（抽查 6/8）；ffdkj 对这两枚与站方一致。**"词典分类不可信"这句话绑的是哪本词典，别说漏**。
- 421970「yande.re 简体中文」那个脚本的词典只有 **10KB 界面文案**，没有词条价值；词条在 473170 背后。

**时间窗口与排序伪标签（2026-09-29 实测，五批探针）**
- **yande.re 原生认 `date:` 区间**，四种形式逐个验过（返回条目的 `created_at` 全在窗口内）：
  `date:2026-05-01..2026-05-31`、`date:2026-08-01..`（下界开）、`date:..2026-02-28`（上界开）、`date:2026-05-20`（单日）。
  配 `order:score` 就是"标签内 × 窗口 × 最热"，**一次额外请求都不用发**。
  出处是参考客户端 `yueeng/moebooru`（Android，flavor 里有 yande/konachan）的 `Model.kt`：
  它把五档排行拼成 `order:score` + `date:A..B` 伪标签，而不是走 popular 端点。
- **站方"排行榜"端点全部忽略 `tags=`**：`popular_recent.json?period=1w` 带 `tags=touhou`、
  带一个不存在的标签、不带标签 —— 三批结果**一字不差**（40 条里 0 条含 touhou）；`popular_by_day.json` 同样。
  所以"标签内的周榜"**不能**照站上 Popular 页去接，那必然长成假开关。
- `period=all` 与 `period=BOGUS` 都**静默回落到 day**（站方没有"全部"这一档）；`order=rank` 被静默忽略
  （与默认返回完全相同的一批）；`order:id_asc` 同样被忽略。→ "200 + 合法 JSON"绝不等于参数生效。
- **Gelbooru 不认任何时间伪标签**：`date:` / `created:` / `time:` / `between:` / `newer_than:` /
  `date_range:` / `age:` / `posted:` / `created_at:` 九种全回空结果页（同批 `sort:score:desc` 130KB、
  `id:>` 105KB 正常，判据可用）；它也不认 `order:score`。唯一路子是 `sort:score:desc` + `id:>=B` 区间。
  ⚠️ 这些是**站内 HTML** 上验的；DAPI 匿名一律 401，本机当时拿不到凭据。
  → **2026-09-29 真机第二轮已证实 DAPI 认 `id:>=`**（Gelbooru 选「按周排行」出的确实是本周那批，
  不是全站高分）。所以"HTML 旁证"那次赌对了，但**下次别把旁证当结论写**。
- **yande.re 的 `date:` 对任意历史期都成立，成本与"本期"完全一样（一笔请求）**：
  `touhou date:2024-03-01..2024-03-31 order:score` → 首条 1158988@2024-03-14 分 144；
  `date:2019-01-01..2019-12-31` → 首条 561904@2019-08 分 876；`date:2025-07-01..2025-07-31` → 分 86/69/45。
  **存档起点**：`date:..2006-06-01` 回 0 条、`date:..2008-01-01` 有货 → 最早在 2006 中~2008 初之间，
  选期弹层的年份范围取 **2007..今年**（宁可少给一年，也别给一个翻得出空页的年份）。
- **Gelbooru 的 id 增速约 1.2 万条/天**（比 yande.re 快 200 倍）→ 任何"按 id 定时间边界"的路子
  在月/年档上都要 **28~30 次**串行探测。2026-09-29 那次定界器给的预算是 24 次，
  于是**周档能通、月/年必然收不了口** —— 用户看到的"月年不行"其实是我们的常数，不是站方天花板。
  撤下的实现镜像在仓库 `_trash/gallery-gelbooru-boundary-2026-09-29/`（README 写了恢复只需把预算提到 40）。
- 两站**未知伪标签 = 匹配不到 → 空数组**（不是被忽略），所以"结果为空"不能读成"窗口内没有新图"。
- **id 随创建时间单调**，可以做时间↔id 换算：yande.re 实测约 **54 条 id/天**
  （id 1267499→2026-08-19、1269669→2026-09-28，2170 条跨 40 天；最新 id 1269670 = 2026-09-28）。
  定界要用 `tags=id:<M&limit=1`（"比 M 新的那一条"）而**不能用 `id:=M`** —— 删帖会留 id 空洞。
  ⚠️ 这五个单调性样本点是 **yande.re 的 `post.json`** 上量的，而当时那套定界器只给 Gelbooru 用 ——
  **原语的单调性在目标站上从来没量过**（第二轮复盘才发现）。
- Gelbooru 的 `created_at` 是 **Ruby asctime 串** `Sat Sep 26 03:51:09 -0500 2026`（带时区偏移），
  不是 epoch 也不是 ISO；解析必须吃满整串，差半天就够把窗口边界挪到错误的一天。
  （这条是**撤下的定界探针**量出来的，站方字段形状仍然成立，只是当前代码不再读它。）
- 资产形状上的两条实测教训：① 79k 行灌 HashMap 是十几 MB 常驻，改成 **SQLite 资产**（`WITHOUT ROWID` 按 name 排）后常驻归零，Android 要真实路径所以首用复制到 `databases/`，**副本文件名带行数=数据版本**才能让旧副本失效；② APK 里 `.sqlite` 照样被 Deflate（3.11MB→1.74MB），量包体要用 `unzip -v` 看 Size 而不是 Length。
- **yande.re 上没有任何"AI 生成"标签**（2026-09-29 两条通路各量一遍：JSON API 与站方 HTML 列表页）：
  `ai-generated` / `ai_generated` / `generated_by_ai` / `ai_drawn` / `male:ai_generated` **全 0 条**，
  而 `landscape` 40 张、`maid` 40 张、不带 tags 的对照正常回数据。
  **裸标签 `ai` 有 19 条，那是角色名**（《Artery Gear》的 AI，标签串 `ai maid ... suzuhira_hiro tick_tack`）
  → 画廊侧 AI 判据必须剔掉裸 `ai`（漫画侧保留），否则就是把 19 张手工插画判成 AI 画。
  Gelbooru 两种写法都出图（第一页各 9 张卡片、阴性对照 0），条目标签串上那一版是**连字符**。
  另：`1girl` 在 yande.re 也是 0 条 —— 这一站标签面极窄，"某标签 0 条"在它那儿很常见，不等于探针坏了。
- ⚠️ 数 HTML 列表页卡片要**按站取标记**：Gelbooru 是 `a id="p<id>"`，yande.re 是链接 `/post/show/<id>`。
  我第一次拿 Gelbooru 的标记去套 yande.re，量出"所有标签全 0 包括 landscape"—— **整站全 0 的第一反应
  应该是我的标记错了**，不是站方空了（换标记后 landscape 立刻 40 张）。
- **两站的缩略档恒为 jpg**（2026-09-29 两批实测：yande.re `post.json?limit=100` 100/100 条
  `preview_url` 是 `.jpg`，原档分布 jpg 53 / png 41 / webp 6；Gelbooru `tags=gif` 列表页 HTML
  42/42 枚是 `thumbnail_*.jpg`）→ 画廊墙上**不会**出现动图，"给缩略图停首帧"是没有对象的机制。
  动图只在大图页那一档（`fastUrl`=preview、`largeUrl`=sample、`fileUrl`=原档 gif）才需要解码器。
- **Coil 3.6.2 的动图解码器类名是 `coil3.gif.AnimatedImageDecoder`**（`Factory()` 无参构造）。
  早期文档/常见答案里的 `ImageDecoderDecoder` 在这一版 `coil-gif` 里**不存在** —— 拆开
  `io.coil-kt.coil3:coil-gif:3.6.2` 的 classes.jar 数过全部 10 个顶层类才定的名。
  另：这一版 `coil-gif` 的 **Android aar 就是 `coil-gif` 本体**，`coil-gif-android` 在 Maven Central 是 404。
- Gelbooru **不给 `file_size`、不给时长** → 任何"播放 · 0 MB"式的读数都是假数，没有就不摆。
- 视频**没有更小的转码档**（只有静帧 jpg + 原片 mp4/webm，原片实测 2.4 MB ~ 26 MB）→ "切高清"对视频是假开关。
- 那些 mp4 **是 faststart**：实测 `moov` 在偏移 36、`mdat` 在 48413，Range 请求回 206（可 seek），本机吞吐 256 KB / 3.06s ≈ **84 KB/s**。→ 起播慢的成因**不是**"要下完整片"，而是 media3 默认 `bufferForPlaybackMs = 2500`（攒的是 2.5 秒**媒体时长**，高码率下就是几百 KB 起）。
- Gelbooru 的 CDN **无 Referer 会 302 到 `hotlink.php` 并吐 HTML**。图片侧早就修过（`ImageHeaderPolicy` 里 `gelbooru.com` 那条），但 **media3 用自己的 HTTP 栈，既不过 `VeneraNetworkClient` 也拿不到那张表** → 视频层当时只搬了 UA、没搬 Referer，于是跟着 302 把 HTML 交给容器解析器，报 `ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED`。

**画师跨站身份与别名（2026-09-30 探针，为「Danbooru 当跨站枢纽」那份提案做的可行性测量）**
- **两站画师名的分歧率没有想象的那么大**：yande `tag.json?type=1&order=count` 前 25 个头部画师标签里，
  **22/25 在 Gelbooru 与 Danbooru 都同名存在**；真分歧 4 条（`tinkle`/`twinbox` 同名但对方站上只有个位数、
  `harahera`/`ruzhai` 是 yande 独有且 **Danbooru 也 0 条记录**）。外部提案给的样例
  `Yande=setmen / Gelbooru=tokenbox / Danbooru=tokenbox` **方向整个是反的**：
  真实为 yande=`tokenbox`（`tags=setmen` → 0 张）、Gelbooru=`setmen` 362 张且**根本没有 tokenbox 这个标签**、
  Danbooru=`setmen` 353 张（`tokenbox` 那条 `post_count=0`）。⇒ 拿 Danbooru 翻名对 Gelbooru 多余、对 yande 有害。
- **帖子上的画师标签本身就是正名**：抽 12 个头部画师记录 `alias_id` 全为 null ⇒ 点标签不会踩到错名，**只有用户手输才踩**。
- **站方 tag 别名已在服务端自动生效**：`tags=vocaloid_2` 回 100 张，而 `vocaloid_2` 不是标签（只是别名）。
  所以要补的只有一类：**画师记录名存在、但它不是标签别名**（`setmen` 正是这种）。别重做站方已有的事。
- **yande 的别名解析链（两笔请求，匿名通）**：`/artist.json?name=X` → 取 `alias_id` → `GET /artist/show/<alias_id>`
  跟 302 落到 `/wiki/show?title=<正名>`，正名直接从**落点 URL 参数**读，不用解析 HTML（抽 16 条全部命中）。
  ⚠️ `artist.json` 的 `id=` / `ids=` / `show=` **全部被静默忽略**（回默认列表）；`name=` 是**模糊匹配**
  （`name=se` 回 16 条）⇒ 必须按 `name` 全等取记录，否则会拿错人。`tag_alias.json` 的 `name=`/`search[name]=` 同样被忽略。
- **别名指针可能指向内容更少的名字**：`a-10` 是 `fuwa_daisuke` 的别名，但 yande 上 `a-10` 有 12 张、正名只有 1 张
  （抽样 11 条：7 条"原词 0 → 正名有货"，2 条换名变多，**2 条换名变少**）⇒ **非 0 张时绝不替换查询词**。
- **Gelbooru 匿名没有别名路**：`page=tag_details` / `tag_list&do=alias` 只回 3383 字节的 JS 壳、
  `/related_tags` 与 `/api/v2/tags/` 404、DAPI 全家（含 `s=tag_get`）匿名 **401**。
  **但 `index.php?page=autocomplete2&term=X&type=tag_order` 匿名 200**，回 `name+post_count+category` 且为前缀匹配
  ⇒ 补全与张数读数**不需要凭据**（现在那两样走的是要凭据的 DAPI）；代价是它只列真有内容的标签，别名不在表内。
- **Danbooru 匿名可用（连打 12 次全 200、无限流头），但参数形状反直觉**：`/tags.json?name=` 与
  `/artists.json?url=` / `search[url]` **都被静默忽略**（回 200 + 合法 JSON 的最新列表，我第一次就据此错读出
  "Danbooru 0/25"）；标签精确查要用 **`search[name]=`**，`name_matches=` 亦可。
  ⇒ 提案要的"相同 Pixiv user ID / 外链互指"那套 CONFIRMED 级证据，**匿名反查拿不到**。
- **随包词典 `gallery_tags_79415.sqlite` 只有一张 `tags(name,category,cn)`、没有别名表** ⇒ 离线归一不可用。

**画师外链与头像那一面（2026-09-30 下午，批次 L 取证）**
- **Gelbooru 没有任何匿名可走的"画师名 → 画师记录"路**：`page=artist&s=list&name=X` 那个 `name=` **根本不生效**
  —— 真名 / 不存在的名字 / 不带参数三次返回**逐字节相同**（都 17621 字节、同样 64 条链接）。
  我上一轮判它"可用"的判据是"页面里出现了这个名字"，而那是**壳页回显输入**：判参数生效只能比正负例的输出差异。
  `s=show&id=NNN` 匿名确实可读（`id=1` → 8781 字节、正文两处 `member.php?id=9311`），但要的是 Gelbooru 自己的编号；
  DAPI `s=artist` 匿名一律 401。⇒ **Gelbooru 侧画师的外链只能借第三方站**（Danbooru 与它共享画师名）。
- **⚠️ 同一天里 Danbooru 的可达性翻转过**：上午那批别名探针是"连打 12 次全 200"，
  下午 danbooru / safebooru / testbooru **三站同测全部 403 + `Just a moment...`**。
  ⇒ 上面那句"匿名可用"和这句"全卡盾"**都不是长期事实**，动这一站之前必须现量一次；
  拿任何一次读数当承诺去做 UI，就会做出"只在某个网络窗口才出声"的假开关。
- Danbooru 的画师地址字段形态**没量到真样例**，源码给的候选是 `url_string`（`url_array.join("\n")`，
  `ArtistURL.parse_prefix` 用**行首 `-`** 标"这条已停用"）与 `urls`（数组；同源 yande.re 实测是**字符串数组**）。
  ⇒ 现在按**三种形态全吃**实现，每种一条单测；写 DTO 押单一形态的风险是"键对不上→静默什么都不显示"。
- **yande.re `artist.json` 在同一个调用里就给 `urls`**（与批次 J 用的 `alias_id` 同一份记录）⇒ 这一站不需要新端点。
- **pixiv `/ajax/user/{id}` 匿名零头即 200**（不带 UA、不带 Referer、带 Referer 三档对照都是同一份 687 字节），
  `body.image`/`imageBig` 就是头像两档（实测 `_50.png` / `_170.png`）；**但 `i.pximg.net` 上的图本身必须带
  `Referer: https://www.pixiv.net/`**（无 → 403 且只有 146 字节，有 → 200、5149 字节真图）。
- **pixiv 的编号与头像地址都会过期**：拿一个旧编号（1360347）去要，站方回 `error=true`。
  ⇒ 关注名单只存"站别 + 名字 + 时刻"，**头像 URL 与 pixiv 编号一律不落库**。

**画师入口/头像与站标（2026-09-30 下午续，批次 L · L9~L12）**
- **`www.pixiv.net/ajax/illust/{作品号}` 匿名可读，能反查作者** —— 我先前写"作品页反查作者不通"是错的：
  我只试了「拿作品号当用户号」那一条（必然 `error=true`）就结了案。真答复里 `body.userId` / `userAccount` /
  `userName` **三个字段都是字符串**（113455481 → `7075448` / `tokenbox` / `セトマン`）。删帖/锁帖那档回
  **404 + `{"error":true}`**。
- 覆盖面（15 条带 pixiv 出处的真帖，样本取自设备 `gallery_feed_cache.json` + `gallery_favorites.json`，都是公开帖数据）：
  **4 条站方 404**（真机屏上那张 9544852 的出处 109123828 就在内 ⇒ 接了这条路它也还是不出入口），11 条给得出作者。
- **作者名与站方画师档一致率只有 4/11（36%）**，但分歧逐条看几乎全是**同一人换写法**：
  `setmen`↔`tokenbox`、`xueli_shimazaki`↔`shimazakixueli/ArtXueli`、`ying_ling`↔`莹泠vv`、
  `lalazyt`↔`kumokoneko/雲小猫`（同一人在 pawoo 的 display_name 是 雲こ猫，假名变体）；
  只有 `death_by_lolis`↔`gusius`、`kottungyang`↔`successym86` 判不准。
  ⇒ **"要求名字一致才摆"会砍掉 7/11**，实际处置是照摆 + 把账号写进胶囊（`pixiv tokenbox`），让张冠李戴看得见。
- **站方 post HTML 里画师档的取法**：`<li class="tag-type-artist">` 内**第一个 `<a>` 是 wiki 的 "?"**、第二个才是标签本体
  （两站同形状）。我第一版正则取第一个，量出"yande 全是 ?、Gelbooru 一条不剩" —— 又是一次标记错而不是站方空。
- **fanbox `api.fanbox.cc/creator.get?creatorId=<子域>` 必须带 `Origin`**：不带 / 只带 Referer 一律
  400 `{"error":"general_error"}`；带 `Origin: https://<子域>.fanbox.cc`（`www` 那档也认）才 200。
  `creatorId` 要的是**子域**（`setmen`），传 pixiv 数字号 400。头像在 `body.user.iconUrl`（160×160，
  主机 `pixiv.pximg.net`，实测**不挡防盗链**：无 Referer / 带 pixiv / 带 fanbox 三档都 200 同字节）。
- **Mastodon `<实例>/api/v1/accounts/lookup?acct=<handle>` 匿名 200**（pawoo 通；**baraag 从本机出口含代理 TLS 握不上**，
  只能真机再验）。字段有 `avatar`（可能动图）与 `avatar_static`；没设过头像的账号实例给
  `/avatars/original/missing.png` 通用剪影 —— **是个合法地址但不是头像**，要判掉。
- **X / Twitter 没有匿名头像路**：站方 og:image 是占位图，`unavatar.io/twitter|/x/<handle>` 回 568 字节占位 SVG。
- **`java.net.URI.host` 对带下划线的主机名返回 null**（`a-b_c.fanbox.cc` 是 fanbox 真发的形状）
  ⇒ 这类 URL 要手工切 authority，用 URI 会把真出处判成"认不出"。
- **第三方图标服务不造分辨率**：`google.com/s2/favicons?domain=X&sz=128` 两站都只回 **16×16 原图**（532/521 字节）、
  `unavatar.io/website/<domain>` 404、`cdn.brandfetch.io/<domain>` 回它自己的 451KB 网页。
  ⇒ 用户问"有没有第三方的图标"时，答案是**逐条量掉**而不是"应该没有"。
- **两站站标档实测**：yande.re 只有 `/favicon.ico`（ICO 目录条目写 16×16/**1bpp**，BMP 头实为 **8bpp 256 色**；
  256 像素全不透明，主色 `#fff3e4`(12)/`#522726`(6)/`#333333`(3)/`#ffbabb`(2) ⇒ **一张裁自插画的粉脸**，不是设计出来的站标）
  加 `assets.yande.re` 上一枚 **484×75 横版字标**（含三个角色，裁方要眼估）；`favicon.png` / `apple-touch-icon*` /
  `logo.png` / `static/` / `img/` **全 404**。Gelbooru 有 `layout/gelbooru-logo.svg`（**360×360 单路径、fill=#FFFFFF**）
  与 16×16 的 `favicon.png`。徽标位是 `sourceMarkSize = 24.dp`（本机 3.0 倍密度 ≈72 物理像素）
  ⇒ **16px 档放大 4.5 倍必糊**，这一点要在用户选之前先摆出来。

**Why:** "缩略图看得到、点开播不了"这种形状最难查，而它自己的注释早就预言过这句却没人接；分类字段那条则直接决定推荐算法能不能照搬参考实现（见 [[project-gallery-for-you-plan]]）。上面那一节决定的是**画师跨站这条功能到底该接在哪一站**，以及"枢纽"这种听着合理的架构说法在被量之前一律不算数。

**How to apply:**
- **新增任何一条取字节的 HTTP 客户端层（播放器、下载器、第三方 SDK）都必须显式复用 `ImageHeaderPolicy` 那张表**，不能各写一份 UA/Referer —— 两层各修各的是结构性的。表里没有 UA 的站要补全局默认串，否则会把现在能用的那一路改坏。
- 视频/图片条目"读数没有就不摆"是硬口径；`0 MB`、`0″` 都算假读数。**同一条口径也管空态归因**：
  墙空着的时候，只有 `blockedCount > 0` 才准提屏蔽规则、只有 `hiddenByRating > 0` 才准提分级，
  两个都是 0 就说"站方这一轮没有回内容" —— 把"没有"说成"被挡住"，用户会去收规则，收完还是空的。
- 报"某站慢/取不到"之前先分清是**容器解析失败**还是**网络失败**：前者十有八九是防盗链头没带。
- **从本机出口探 yande.re 时，先跑一次不带 `tags=` 的对照**：2026-09-29 当天
  `post.json?tags=anything`（连 `tags=1girl`、`tags=id:3551918` 都一样）全部回 `[]`，
  而 `post.json?limit=5` 正常回 5 条 —— 那是**出口侧**的读数。同一时刻用户真机上搜索是出图的，
  所以"标签检索坏了"这个结论是错的。Gelbooru DAPI 匿名一律 401 也复现了（当天两次）。
- 相关：[[project-source-data-ceilings]]、[[gallery-module-isolation]]、[[feedback-degrade-paths-must-fail-loud]]、[[reference-gradle-cache-sources-jars]]
