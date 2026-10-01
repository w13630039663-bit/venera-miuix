---
name: gallery-tag-bridge
description: 漫画兴趣 tag → 图库站 tag 的可行性实测（GalleryTagResolver 待拍板未动代码）：EhTag 字典键用空格而空格在图站是 AND，机械换下划线命中率天花板 54%，反向表歧义仅 0.7% 但字典值带 emoji，yande.re 无 counts 端点且 /tag.json 参数被静默忽略
metadata:
  type: project
---

**要做的事**：用漫画侧已有的题材统计桶去画廊两站拉"与偏好相似"的图。用户 2026-09-25 定的组件形态：
`GalleryTagResolver.resolve(mangaTag) → List<ResolvedGalleryTag(namespace, sourceRaw, galleryTag, danbooru: Boolean, yande: Boolean)>`，
职责**只有词表桥接 + 逐站可用性**，不含配比、降级文案、UI。状态：**可行性报告已交、等 4 条边界拍板，未写一行代码、未接线。**

**待用户拍的四条**（提问已编号给出）：① 输入是否两种形态都吃（`parody:azur lane` 优先 / `parody:碧蓝航线` 兜底）；
② 反向表归一口径 = 剥 emoji + 繁→简 + 去空白 + 小写，是否认可；
③ 两站都 false 的条目保留还是丢（倾向保留，让调用方能区分"图库没这个标签"和"用户没偏好"）；
④ 是否暴露 `dictionarySize`，让"字典还没异步加载完 → resolve 必然空"被当成错误而不是"暂无推荐"。

## 实测结论（2026-09-25 探针，`assets/tags.json` 六类题材库 8734 键）

- **桥接只差一条机械规则：空格→下划线 + URL 编码。** 字典键的字符集实测只有三种非字母数字：空格 ×8403、`-` ×281、`.` ×112，**括号/斜杠/冒号为 0**。`-` `.` 原样可用（`dr._drakken` DB=27）。
- **绝不能把空格原样送出去**：空格在 Danbooru/yande.re 是**多标签 AND 分隔符**。`azur lane`=0、`azur_lane`=157,966；而 `corpse party` **返回 2 条**（`corpse` AND `party`）——静默给错结果，比返回 0 更危险。
- **天花板 ~54%，补不上**：24 键随机样本（14 parody + 10 character）换下划线后 **DB 13/24、yande.re 10/24**；原样带空格只有 5/24（含 1 条跑偏）。没命中的是**真名不同**：`2b`→真名 `2b_(nier:automata)`、`fate grand order`→真名 `fate/grand_order`（含 `/` 必须 %2F）、`ro-kyu-bu`=0。
- **反向查表（中文规范名→英文键）可行且歧义极小**：8199 个规范名里只有 **54 个（0.7%）**对应多个英文键，最多 3 个（莫妮卡）。`碧蓝航线`→唯一 `azur lane`。所以返回 `List` 对，但通常 size=1。
- **不归一就直接查不到**：字典**值**里有装饰 emoji 167 条（1.9%）——`female:glasses` 的值是 **`眼镜👓`**，输入"眼镜"精确匹配失败；另有繁体值 21 条、含空白 255 条、含括号 67 条、含全角冒号 9 条、纯拉丁值 195 条（2.2%，如 `Kanon❄`）。
- **`searchRaw` 已经是英文原值**：`ReadingStatsManager` 的 `pickOriginal()` 已按 `:` 拆好 `searchNamespace`/`searchRaw`，所以 **EH 系源根本不需要反向翻译**，只要空格→下划线；`display`（中文规范名）是派生**有损**值，一次都不能送给 API（中文实测全 0）。
- **两个布尔值必须联网，且两站手段不对称**：Danbooru `/counts/posts.json?tags=`（匿名 200、约 30 字节；实测 original=1,589,242 / yuri=332,478 / ro-kyu-bu=0）；**yande.re 同名端点 404**，只能用 `post.json?limit=1&tags=` 看 rows>0（只知道有/无）。成本 = Top-10 种子最多 20 次请求，进程内缓存一次。探针**失败必须抛错**，返回 false 会让"网络不通"伪装成"图库没这个标签"。
- ⚠️ **yande.re `/tag.json` 是陷阱**：`name=` / `search[name]=` / `name_matches=` 三种写法**全部 200 + 50 条不相干标签，参数被静默忽略**（与 `/post.json?id=` 同坑法）。任何"rows>0 即存在"的判据会把每个标签都判成可用。
- **中文源是结构天花板**：jm/哔咔给的标签本来就是中文，且很多词压根不在字典值里（`学生`/`少女` 无对应值、`女仆` 在表里是 `女仆装` 不同串）。EH 系源接近全可用，纯中文源只覆盖"恰好有该规范名"的部分。

## 已被实测否掉、不要再提的两条

- **通配自愈**（`danbooru/tags.json?search[name_matches]=*尾词*`）：**不做**。严格接受条件（候选须等于 seed 或以 `seed_`/`seed(`/`seed:` 开头）只救回 ~2/11；宽松条件捞进来的是**自信地错** —— `burn_up_w→white_background`、`equestria_girls→girls'_frontline`、`mitsuki_sonoda→sonoda_umi`（另一个人）、`kanna_ogata→ogata_hyakunosuke`。
- **姓/名倒序**：`oreki_houtarou`=1656 对，但 `rengoku_kyojuro`=0（真名带长音符 `rengoku_kyoujurou`）、`sonoda_mitsuki`=0 → 收益 1/5 且会认错人。

**Why:** 这一轮的价值全在"先量再写"——用户先要组件、中途改口「你先不要干，先研究是否可行」，而实测把一件看着像"翻译问题"的事拆成了三条不同性质的东西：一条机械规则（可做）、一次异步字典反查（可做但有归一坑）、以及 46% 的**站方词表缺失（不可做）**。若不量化就写码，会把"一半兴趣标签拉不到图"实现成看起来像 bug 的空屏。

**How to apply:** 动画廊个性化之前先读本文件 + [[gallery-module-isolation]] 那张端点能力表；个性化种子取数不要建图库侧的表（种子来自 `reading_stats`，浏览本身不写库）。写这个 resolver 时把它做成**纯 JVM 可单测**（注入字典列表 + 注入 probe，像 `TagNormalizer` 那样），字典键规范化复用 `TagNormalizer.normKey`/`ALIASES` 以免两侧漂移。存量统计行里有旧切分器劈坏的假题材（见 [[project-tag-stats-pipeline]]），会把 `fategrand`/`order` 这种假名当种子 → 桥接层拿到的命中率有一部分是被这些假桶拖累的，别把它归因成规则失效。
