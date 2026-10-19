# pixiv 这一轮能引用哪些成熟项目（计划，2026-10-16）

> 用户那句「可以引用什么其他成熟 GitHub 项目来加快施工进度？计划就行」——这份是**计划**，不动代码。
> 所有仓库的许可证 / star / 活跃度都是**现查 `api.github.com`** 的读数（2026-10-16 当晚），不是凭印象；
> 仓内既有那份复用调研 `docs/rounds/remaining-plan-and-reuse-2026-10-10.md` 的判负**逐条继承**，不重判。

## 0 一句话结论

**引依赖这一档基本没有收益**（唯一像样的 Kotlin 库 `cssxsh/pixiv-client` 现量 `spdx_id=AGPL-3.0`、只有 19★，与 10-10 那条判负对上了）。
真正省时间的是另外两档：**① 抄源码**（MIT / Apache-2.0 / Unlicense 那几颗）与 **② 只读取证**（GPL 族那几颗 pixiv 客户端——读它们换端点知识，一行代码不进仓）。
其中最值钱的一颗是 **`137458/pixez-miuix`**：pixiv 客户端，而且**与我们同栈**（KMP + miuix + Compose 1.12 + Coil），今天还在 push，本机已有 569 颗文件的缓存。

## 1 三档许可纪律（先定档，再谈引用）

| 档 | 判据 | 允许产出 |
|---|---|---|
| **A 抄源码** | `MIT` / `Apache-2.0` / `Unlicense` | 可以搬实现进仓，**文件头留一行来源**（哪颗仓库哪个文件 + 许可证） |
| **B 只读取证** | `GPL-*` / `AGPL-*` / 无许可证 | **只允许产出"表与结论"**（端点、参数、取值域、鉴权边界），不允许产出 `.kt`。读过的代码会漂进自己的实现，这是 copyleft 传染的入口 |
| **C 判负** | 结构性不合（APK 载体 / sidecar 体积 / 已有更便宜的自研） | 不排 |

## 2 候选清单（现查读数）

### A 档 · 可以抄源码

| 仓库 | 许可 | 语言 / star / 最近 push | 买什么 |
|---|---|---|---|
| `ultranity/Pix-EzViewer` | **MIT** | Kotlin / 1271★ / 2026-10-11 | Android 侧 pixiv 客户端：DTO 字段映射、`illust_type` 取值、图床头、下载落盘 |
| `Notsfsssf/Pix-EzViewer` | **MIT** | Java / 2631★ / 2022-02-11（旧，ultranity 的前身） | 同上，作对照；旧就别拿它的端点表当准 |
| `darriousliu/PiPixiv` | **Apache-2.0** | Kotlin / 265★ / **2026-10-16（今天）** | 新一点、代码量小，适合对"当前还活着的端点长什么样"做第二证据 |
| `upbit/pixivpy` | **Unlicense**（≈公有域，连署名都不要求） | Python / 2060★ / 2025-07-31 | **参数面最全的一张表**：SAPI/AppAPI/web 三套端点、`search_target`/`sort`/`mode`/`filter` 的取值域、哪些一碰就要登录 |
| `pixiv/zip_player` | **MIT**，**pixiv 官方** | JS / 103★ / 2014 | ugoira 帧时序的权威语义（`frames` 的 `delay` 序列怎么吃） |
| `item4/ugoira` | MIT | Python / 14★ / 2025-03-12 | ugoira → 动图的现代小实现，作 zip_player 的时序对照 |
| `alphasp/pxview` / `alphasp/pixiv-api-client` | MIT | JS / 836★、155★ | 参数命名与翻页游标的第三证据（`/web/v1/` 那一族） |

### B 档 · 只读取证（代码不进仓）

| 仓库 | 许可 | 读数 | 为什么仍然值得读 |
|---|---|---|---|
| **`137458/pixez-miuix`** | GPL-3.0 | Kotlin / 14★ / **今天 push** / 未归档 | **同栈**（KMP + miuix + Compose 1.12 + Coil 3.6.3 + Decompose）。本机缓存 `%LOCALAPPDATA%\pixez-src\compose-miuix\`（现量 569 颗文件）。可取：`shared/…/data/model/{Ranking,Ugoira}.kt`、`data/repository/IllustRepository.kt`（`:216-219` 榜单 `mode`+`date`、`:247` `restrict`、`:156` `filter=for_ios`）、`ui/screens/SearchFilterSection.kt`（搜索筛选那一屏的取值域）、`desktop/…/DesktopProxySelector.kt`（**我们 Windows 线也用得上**）。⚠️ 它走的是 app-api + `filter=for_android` 那一族，**与我们"匿名不鉴权"的口径不同**，照搬参数前先问"这参数匿名给不给" |
| `CeuiLiSA/Pixiv-Shaft` | GPL-2.0 | Kotlin / 8089★ / **今天 push** | 装机量最大的 Kotlin pixiv 客户端之一，端点存活性的最好旁证 |
| `Notsfsssf/pixez-flutter` | GPL-3.0 | Dart / 13102★ / 2026-10-11 | 同上；Dart 侧不能抄码但能抄"用户怎么逛 p 站"的信息架构 |
| `asadahimeka/pixiv-viewer-app` | **AGPL-3.0** | JS / 599★ / 2026-10-12 | AGPL ⇒ 连"抄思路进文档"都要留出处；只读它记的 ugoira/多页行为 |
| `cssxsh/pixiv-client` | **AGPL-3.0** | Kotlin / 19★ / 2023-10-04 | **判负**（与 10-10 那条一致，今晚现量 `spdx_id` 再确认一次），不引 |
| `leoding86/webextension-pixiv-toolkit` | **无许可证** | JS / 1782★ / 2024-09-24 | 无许可证 = 默认保留所有权利 ⇒ **不抄**，最多读它的 issue 区了解站方改动 |

### C 档 · 文档线索（不是依赖）

- `daydreamer-json/pixiv-ajax-api-docs`（HTML / 51★ / 2025-06-11，**无 SPDX 且 README 写"禁止滥用"**）：只当**线索源**。它今晚已经值回票价——本仓那"搜索四条路全灭"的结论就是被它一句"关键词在路径里"推翻的（`/ajax/search/artworks/{关键词}` → 200 / 69 KB / `"error":false`，见 `gallery-pixiv-placement-options-2026-10-16.md` §4.1）。

### D 档 · 别再找了（继承既有判负）

- **booru 侧没有成熟可引物**：今晚现搜 `booru viewer` 全是 0–6★ 的小项目；`remaining-plan-and-reuse-2026-10-10.md:54` 那句"flexbooru/Yummybooru/GelbooruClient 全 Android，自研那几颗 client 继续自研"仍然对。
- 源脚本生态（Mihon/Tachiyomi 扩展 = APK 载体）、Suwayomi sidecar（321 MB + MPL 文件级 copyleft）—— 同那份文档，不重开。

## 3 四步用法（每步自带验收）

1. **取证出表**（半天，只读）：从 pixivpy（Unlicense）+ pixez-miuix / Pixiv-Shaft（GPL，只出表）抄出一张「端点 × 参数 × 取值域 × 鉴权边界」表。
   **验收**：表里每一行都要**本机复现一次**才允许进文档——别拿 README 当读数（今晚 `mode=day` 那类"看着像会被静默忽略"的坑已经抓到两个：`no_ai`、`s_mode` 首屏无差别）。
2. **抄实现**（A 档）：DTO 与字段映射（`illust_type`/`illust_page_count`/`xRestrict`）、ugoira 帧时序（`pixiv/zip_player`）、榜单→详情取数（`Pix-EzViewer`）。
   **验收**：每颗搬进来的文件头留一行来源与许可证；`./gradlew :app:testDebugUnitTest` 全绿；新件各配单测（照 `_qa/` 那套扰动台架补判据）。
3. **补掉那两格未判定**：`no_ai` 与 `s_mode` 的生效性——换样本词、比 `total` 与首条 id，而不是比"200 + 合法 JSON"。
   **验收**：读数进 `gallery-pixiv-placement-options-2026-10-16.md` §4.1，并在那儿标"哪次量的"。
4. **才谈界面**：等 §4.1 量完 + 五套放法（⑤/①/②/③）选定，再动 `app/`。

## 4 三条风险（本仓反复中招的那几族）

1. **B 档的"读过的东西会漂进代码"**：所以规定 B 档只产出表与结论，不产出 `.kt`；要落码必须先找到 A 档对应物或自己写。
2. **缓存会过期**：`%LOCALAPPDATA%\pixez-src\` 与 `%TEMP%\mihon\` 都是缓存不是镜像，**引用行号前先 `ls` 确认那颗文件还在**（今晚 `IllustRepository.kt` 的行号就是这么现取的）。
3. **站方会变，结论会烂**：本文件里"搜索全灭"那句话从写下到被推翻只隔了三小时。⇒ 端点表每条**必须带日期 + 复现命令**，别写成无时态的事实。

## 5 明确不做

不引任何 GPL/AGPL 依赖；不抄无许可证仓库的代码；不引 `cssxsh/pixiv-client`；不把 pixiv 接进漫画源与阅读器（要伪造 `comicId/sourceName/chapterTitle`）；不为"别人有库"而放弃已判负自研的 booru 三颗 client；不在选型点头前动 `app/`。
