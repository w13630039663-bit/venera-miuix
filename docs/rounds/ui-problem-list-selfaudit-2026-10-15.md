# UI 问题清单 · 自审读数记录（2026-10-15）

> 题面：用户三问 —— ① 问题是否真实存在（要能报出截图文件名与具体位置）；② 方案是否符合"画廊为主、漫画为次"；③ 是否解决根因。
> 本文只放**读数**，判定与修订写进报告本体（`docs/rounds/Android UI 问题清单与设计方案（2026-10-15 · 平板档为主 + 手机档缺陷）.md` v2）。
> 手机：1080×2376，density 2.628（411dp 宽）；平板：1920×1080，density 1.49（1291dp 宽）。下文 px→dp 换算按此。
> 量具：`_qa/ui-verify-2026-10-15/audit_pixels.ps1`、`audit_pixels2.ps1`、`audit_glyphs.ps1` + 内联 node 语义树探针（bounds 正则 `[0-9]` 口径）。

---

## 一、语义树坐标读数（截图内可直接框出来的位置）

| 编号 | 截图 / dump | 现量 | 判 |
|---|---|---|---|
| A-01 | `scr-54-reader-chrome.png` / `ui-54-reader-chrome.xml` | `⏮` 文本节点 35×49px（13×19dp）、`⏭` 同、`▶` 36×49px；三枚的**可点祖先各 1 层 = 126×126px（48×48dp）** | 符号当图标 ✅；"触达不足"**❌ 撤回** |
| A-01′ | 同上 | `📑` 在该 dump **0 命中**；"目录"是独立文本节点 [277,2196][329,2226] | 原稿"📑 当图标"❌ **撤回**（上一位走查的读数，本鱼未复量即引用） |
| A-05 | `scr-44-detail.png` | 五枚圆底 vs 页底：页底 RGB(245,238,237) 亮度 240.0；底色依次 (240,224,223)/228.6、(240,221,220)/226.9、(235,226,222)/228.4、(235,226,225)/228.3、(235,226,225)/228.3 ⇒ **ΔL 全排 11.4~13.1，彼此差 <2 级** | 原稿"分享/链接几乎没有底色、读起来像禁用"❌ **改判**：底色五枚均等且都在，问题不在底色 |
| A-05′ | 同上（字形最暗像素） | 收藏 (174,47,52) 饱和 73% ｜点赞 **(186,26,26) 饱和 86%** ｜评论 (115,90,46) 60% ｜分享 **(119,86,85) 饱和 29%** ｜复制链接 (119,86,85) **29%，与分享逐字同色** | ✅ 真根因在色相：两枚"状态型"同为红、两枚"动作型"近中性 ⇒ 颜色编码在**令牌层**就失效 |
| A-06 | `tab/scr-t6-stats.png` / `tab/ui-t6-stats.xml` | 榜单行标题 `吃白飯的大肥魚的特殊服務` 右端 x=321，同行读数 `192 页` 左端 x=1827 ⇒ **间隔 1506px = 1010dp**；卡标签 `总阅读时长` 起点 x=39；图表日号 `15` 节点 14×15px（9×10dp） | ✅ 定量成立 |
| A-06′ | `tab/scr-t17-detail.png` / `tab/ui-t17-detail.xml` | 右栏正文节点 [673,918][1887,989] ⇒ **单行宽 1214px = 815dp** | ✅ 定量成立 |
| A-07 | `tab/scr-t14-split.png` / `tab/ui-t14-split.xml` | 三颗标签宽 92/88/88px，中心 x=315 / 960 / 1605 ⇒ **每颗占 645px 间距，内部约 550px 是空的** | ✅ 定量成立 |
| A-08 | `tab/scr-t17-detail.png` / `tab/ui-t17-detail.xml` | 底栏节点纵向带 y=978~1051；同带内右栏内容节点 `预览 (12 页)` y=1028~1055、正文块 y=918~989 ⇒ **坐标区间真重叠**，不是"看着像" | ✅ 加强 |
| A-09 | `scr-26-settings.png` / `ui-26-settings.xml` | `›` 命中 7 处，每枚 19×56px（7×21dp），非独立可点 | ✅ 定量成立 |
| A-09′ | `scr-44-detail.png` / `ui-44-detail.xml` | `★ 4.0` 是**可点节点** [345,613][471,739] = 48×48dp | ✅ 符号当图标成立；触达没问题 |
| A-09″ | `scr-92-gal-home.png` / `ui-92-gal-home.xml` | 视频角标 `▶ 3″` [958,833][1017,869]、`▶` [991,1228][1017,1264] | ❌ **撤回**"▶ 当图标"这一格（缩略图上的播放三角是通用记号）；改成立的是 `″` 双撇号当秒数单位 |
| A-10 | `scr-24-follow.png` / `ui-24-follow.xml`（y=2134 那一排） | 可点件 **8 枚，带文字或描述的只有 1 枚**；中间六枚各 **79×126px = 30×48dp（宽不足 48dp）**，首尾两枚 126×126px = 48dp | ✅ 从"六枚裸图标"升级为"7/8 无名 + 六枚触达宽度 30dp" |
| A-11 | `ui-24-follow.xml` | 文本节点 `"当前默认: copy_manga"` [221,522][509,556] | ✅ 确证 |
| A-11′ | `ui-26-settings.xml` | 文本节点 `"发布通道：w13630039663-bit/venera-miui…"` [69,743][681,780] | ✅ 确证 |
| A-11″ | `ui-a24-stats.xml`（榜单行 y>1650） | 源名逐枚：`jm` ×3、`nhentai`、`ehentai` ×3 —— **没有 `jpn`** | ❌ 原稿"jm/ehentai/jpn"里 `jpn` 是**本鱼看错 400px 缩略图**；`ehentai`/`nhentai` 是否算泄漏要回源表（其显示名可能本就是该串）⇒ 确证泄漏只剩 `jm` 与 `copy_manga` |
| A-11‴ | 124 份手机 dump 全扫 | `manga_dex` **0 命中** | ⇒ 详情封面角标既不在语义树、也只能靠像素证；"对读屏不可见"这一格成立 |
| A-12 | `ui-b16-infosheet.xml` | `Y·G·S` [106,1120][189,1154] 与 `long_hair censored` [194,1119][419,1156] ⇒ **间隙 5px = 1.9dp** | ❌ 原稿"没有间距/屏上是 Y·G·Slong_hair"**不准**；成立的是"1.9dp 间隙不足以读成两个字段" ⇒ 降级 P3 |
| A-13 | `ui-30-appearance.xml` | `主题模式` 2 处（[69,1271] 组头 + [69,1370] 行题）、`界面风格` 2 处、`界面材质` **1 处**（行在屏外） | 两处有屏上凭据，第三处只有代码凭据 |
| A-04 | `ui-a8-gallery.xml`（断网那趟画廊首页） | 全树文本节点里与"为什么没图"相关的只有 `标签 / 画师 / 作品` 与 `Safebooru 还没有可推荐的标签` | ✅ 断网时屏上确实没有任何"网络不通"的说法 |

## 二、阅读器面板 ghost（`scr-57-reader-settings.png`，裁图 `audit-crop-sheet-bottom.png`）

| 取样框 | 现量 |
|---|---|
| 面板内空白区 (300,1900)-(700,1960) | mean 38.0，min 25，max 60 |
| 底栏标签带 (93,2196)-(197,2228)，**面板开着** | mean 38.2，min 31，max 59，spread 28 |
| 同一框，**面板没开**（`scr-54-reader-chrome.png`） | mean 94.9，min 46，max 245，spread 199 |

⇒ 底栏那一排被 80% 面板压到只剩 28 级亮度摆幅（对照无面板时的 199 级），**肉眼仍可读**（裁图里"存图/插图/分享/设置"连同图标全部可辨，且面板那枚红色开关正压在"设置"图标上）。
原稿"两层字叠在一起读不了"**过强**；准确说法：**面板最后一行与透出的底栏行同区并置**，红色开关与"设置"图标重叠。

## 三、量具自己的一处坑（记进纪律）

第一版 node 探针用 `replace(/[][]/g," ").split(/ +/)` 解析 `bounds="[x,y][x,y]"`，**逗号没切** ⇒ 坐标全成 `NaN`，输出"可点祖先 0 层""当前默认 0 命中"这类**像真的假数**。差点让本鱼把一条真读数（`"当前默认: copy_manga"`）连同真结论一起撤掉。
正解：bounds 一律用 `bounds="\[([0-9]+),([0-9]+)\]\[([0-9]+),([0-9]+)\]"` 抓四元组；文本匹配用 `contains` 不用"整串相等"（`"★ 4.0"` 不等于 `"★"`）。

---

## 四、S1 收口（令牌层第一批，2026-10-15 晚）

**改了什么**（只动 `app/`）：`ui/tokens/Color.kt` 动作身份色四枚 → 两枚（删 `actionComment`/`actionShare`，MD3 档 `actionLike` 从 `m.error` 改 `m.tertiary`）；`feature/DetailActionButton.kt` 分状态型/动作型两族、圆座实底 `surfaceVariant`、字号与圆角归槽位；`feature/ComicDetailScreen.kt` 五处调用点两递三不递；`components/ComicTileLayout.kt:95` 封面圆角与 `VeneraCover.kt:79` 归同一把 `shape.medium`。

**两格自审后撤销**：① "容器 alpha 三槽位"（改判后没有消费者，不预留不生效的参数）；② "圆角对象类表"（5 处裸 12dp 分属四种对象，真正撞车的只有封面那一处）。

**台架 5 笔，逐笔各红在自己那颗 `@Test`**（`UiAuditS1Test`，跑前删旧 XML、跑后核 mtime）：

| 笔 | 扰动 | 红在哪 | 消息 |
|---|---|---|---|
| P1 | `actionLike` 终点改回 `else m.error` | ① 动作身份色只剩两枚 | "点赞的 MD3 档取值变了" expected 1 was 0 |
| P2 | 圆座底色改回 `surfaceVariant.copy(alpha = 0.12f)` | ③ 圆座不再抄 alpha | "这颗件里又出现了内联 alpha" |
| P3 | 封面圆角 `shape.medium` → `shape.large` | ④ 封面与 VeneraCover 同一把尺子 | expected 1 was 0 |
| P4 | 收藏那枚 `identityColor` 递成 `tokens.color.primary` | ② 只有两枚状态型递身份色 | "道数变了" expected 2 was 1 |
| P5 | `RichCommentContent.kt:72` 换成裸 `RoundedCornerShape(7.dp)` | ⑤ 裸圆角存量只许缩短 | "从 45 处涨到了 46" |

回滚后五枚锚点各现查 1 处、无残留扰动；复跑 `:app:testDebugUnitTest` = **1224 笔 / 157 颗类 / 0 失败 0 错误**，`:app:assembleDebug` 绿（universal debug 105,523,872 B）。

**未量的一格（下批上机第一件事）**：实底 `surfaceVariant` 圆座在**深色档**与**液态玻璃档**的表现 —— 这一批改的正是配色映射，单测只能钉"取值来自哪枚槽位"，钉不了"屏上好不好认"。


---

## 五、G1 收口（画廊第一批：整轮失败要说得出话 + 底栏让位接线，2026-10-15 晚）

**改了什么**（只动 `app/`，无一处在冻结表内 —— `ComicDetailScreen.kt` 那颗在 `FREEZE-STATEMENT.md:120` 明写"页面显示层，不在冻结表内"，四颗 FROZEN 屏本批一颗没碰，故**无豁免要记**）：

- `gallery/domain/GalleryDailyFeed.kt`：三站全空那一支从前把已经算好的「站 → 原因」压成一句 `IllegalStateException` 串就交出去；现在交出新的 `GalleryEmptyRound(reasons)` —— 结构跟着事实一起离开发车点。`message` 逐字保持旧形状（桌面 `DesktopFailureReadoutTest` 钉的是"消息里点名三站"，换类型不换读数）。
- `gallery/ui/GalleryViewModel.kt`：新增 `reject(e)` 与 `accept(...)` 对称 —— 一句失败文案 + 一份按站原因（只有 `e is GalleryEmptyRound` 才带得回原因）。
- `gallery/ui/GalleryScreen.kt` 与 `gallery/ui/GalleryDailyScreen.kt`：两处 `.onFailure` 各退回自己抄一句 `feedError = "这次没读到内容…"`，现在各走 `vm.reject(e)`（同一份事实此前有两个来源）。
- `gallery/ui/GalleryForYouViewModel.kt`：`applyPage` 的 `failures = outcome.reasons` 此前**只在成功那一支**写；失败那一支的原因串本来就在 `outcome` 里，现在两支都收。
- `feature/ComicDetailScreen.kt:343`：`bottom = tokens.spacing.space8` → `LocalBottomBarClearance.current + tokens.spacing.space8`。

**页面与提示件一行没改**：屏上那一排 `GalleryNotices` 吃的就是 `failures`，缓存铺屏时 `cards` 非空 ⇒ 走 `else -> GalleryCardsGrid(header = …)` 那一支，补上数据它就自己上屏（一档 `LegFailed`、tone=Error、带真能发请求的「重试」）。这就是"改根因不在页面打补丁"落在本批的形状。

**一处改判（v2 清单 §三 P1-4 的源码那一栏要就地纠正）**：`LocalBottomBarClearance` 现量 **29 道 / 22 颗文件**，画廊六颗文件共 **9 道**（首页搜索墙、猜你喜欢墙、日榜二级页、收藏墙两处、历史墙两处、大图页、反搜结果）**全在** —— v1/v2 写的"缺 `gallery/ui/GalleryScreen.kt`"是旧读数，本批复量后撤。真缺的只有**漫画详情**那一页（`feature/` 侧此前 15 颗文件已接、它没接），平板右栏那格坐标重叠的成因就是它。

**台架 5 笔，逐笔各红在自己那颗 `@Test`**（`_qa/g1_perturb.js`，跑前删对应 XML、跑后核锚点与 mtime）：

| 笔 | 扰动 | 红在哪 |
|---|---|---|
| P1 | 猜你喜欢失败那一支不落地原因 | ② 失败轮也收下按站原因（`failures = outcome.reasons` 2→1） |
| P2 | 首页那处退回自己抄一句 | ① 整轮失败的收口只有 ViewModel 那一处（`vm.reject(` 1→0） |
| P3 | `reject` 不再反解 `GalleryEmptyRound` | ② 同一颗（daily 那半句锚点没了） |
| P4 | 全空那一支退回拼串 `IllegalStateException` | 两笔行为判据：类型那条 `<failure>` + 归档那条（`assertTrue` 在前，不是 CCE） |
| P5 | 详情那一页退回手写 `bottom = 72.dp` | ③ 底栏让位接线只许加长（29→28 道 + 路径名单掉一颗） |

回滚后五枚锚点各现查 1 处（P1 那枚是 2 处中的一处，脚本按整段两行定位）、零残留；**复跑要带 `--rerun`** —— 台架最后一笔留下的 XML 是"被扰动那一跑"的读数，源码还原后 gradle 会判 UP-TO-DATE 而不重写，21:57:23 那份就是 P5 的，21:57:37 复跑才是干净的。

**数字** = 新立 `gallery/GalleryRoundFailureReadoutTest` **4 笔**（纯 JVM：三站全没货 ⇒ 失败带按站原因 / message 逐站点名 / 归成一档出错且给得出重试 / 缺一腿不算整轮失败）+ `components/UiAuditG1Test` **3 笔**（收口一处、两支都收原因、让位道数只许加长）；全量 `:app:testDebugUnitTest` **1231 笔 / 159 颗类 / 0 失败 0 错误**（上笔 1224 / 157，本批 +7 / +2 只增不减）、`:app:assembleDebug` 绿（universal debug **105,524,552 B**，S1 那笔 105,523,872 ⇒ +680 B）。

**没做与残差（写明，不当成已修）**：
1. **另一族整轮失败仍然无声**：三站都答上了、但 `GalleryMerge.mix` 的判据炸了（给了内容却滤光）时没有按站原因可交 —— `reject` 只能设 `feedError`，而那一屏的门还是 `error != null && !hasPosts`。要治它得让"整轮失败"能不以站为单位上屏，那是**新增一档提示**，不在本批口径内（本批只做"已算出来的结构别丢"）。
2. **真机成对截图没取**（设备没连着）：屏上那一排是否真的在断网冷启时上屏、深色档与玻璃档是否读得清，属未量。S1 欠的那一格（实底 `surfaceVariant` 圆座在深色/玻璃档）也在同一趟一起看。
3. 猜你喜欢那头的失败文案仍与日榜那头各写一句（两颗 VM、两种处境，措辞本就不同 —— 不是同一份事实两个来源，不动）。

---

## 六、G2 收口（画廊第二批：语法行 / 秒数 / chip 前缀 / 空态行动层，2026-10-15 晚）

**改了什么**（只动 `app/`，无一处在冻结表内）：

- `gallery/ui/GallerySearchArea.kt`：结果区那行常驻灰字撤掉"（前面加 -）"。撤的是**教**，不是那项能力 —— `GalleryTagFilter.of("-词")` 那一路照旧解析成排除项（新判据里有那条行为对照）。
- `gallery/data/GalleryPost.kt`：`durationLabel` 的 `3″` → `3s`，与同模块"超过 12s 没返回"、"熔断中，Ns 后重试"归成同一档写法。
- `components/venera/VeneraChip.kt`：带次要前缀时行间距不再自收一档（`space1` → 恒 `space2`）。
- `gallery/ui/GalleryScreen.kt`：日榜空态补**条件式**行动层 —— 只有"站方这一轮没回内容"那一档给「重试」，命中屏蔽规则 / 被分级收起那两档不给（重试还是同一个结果，给了就是假开关）。
- `gallery/ui/GalleryHistoryScreen.kt`：`GalleryHistoryEmpty` 多一个 `onClear` 形参，"浏览历史读不出来"那档给「清掉这份历史」，走顶栏那颗**同一枚**确认浮层（两步，不静默删）。

**两处对上一节读数的就地改判**（本鱼自己写的，现量之后要自己纠正）：

1. **P3-1 的成因写错了**。§二 那版写"两枚 `Text` 相邻、中间没有 `Spacer`"—— 实际那枚 `Row` 有 `horizontalArrangement = Arrangement.spacedBy(...)`，且带前缀那一支**故意**收成 `space1`（2dp），注释的理由是"留出两个独立元素的间距会把一枚 chip 读成两枚"。实测间隙 5px ≈ 1.9dp 与那 2dp 一致 ⇒ **症状成立、成因与修法都写反了**：照原方案"补一根 Spacer"会与既有 `spacedBy` **叠成 6dp**（两处间距通道）。正解是把那一档归位、`if` 一起删掉，并留注释说明为什么"读成两枚"那条不成立（两段文字同在一枚底色里）。
2. **S-3 那句"几乎无人传"在画廊侧不成立**。新写只读量具 `_qa/empty_action_scan.js` 现量：`VeneraEmptyView` 全仓 **34 处**调用，**16 处给了行动层、18 处没给**；画廊那 12 处里本批之前只有 **3 处**没给（`GalleryFavoritesBody.kt:144/154`、`GalleryReverseResults.kt:117`）。三处都**决定不接**并写明理由：前两处的出路是"去画廊点一张图的心"与"去设置收那条规则"，这一屏没有任何可跳的回调（新引一条导航只为摆一枚按钮 = 假出口）；第三处重试同一张必得同一结果，正文已经说了"换一张更清晰的图"。本批把**真有去处**的两处接上（日榜空态、历史坏档），画廊剩 1 处未接（`GalleryFavoritesBody` 两档共一类成因）。
   ⚠️ 那条量具有过一处坏读数：`actionText` 离调用点超过 16 行就报"没给"（`title = when { … }` 那种长块），第一版把 `GalleryScreen.kt:884/1953` 报成零出口。窗口放到 60 行后这两处翻成"有出口"。**结论：量具的窗口也是假设，读数要自审。**

**台架 6 笔，逐笔各红在自己那颗 `@Test`**（`_qa/g2_perturb.js`，跑前删 XML、跑后核锚点）：

| 笔 | 扰动 | 红在哪 |
|---|---|---|
| Q1 | 常驻行退回"（前面加 -）" | ① 常驻行不再教查询语法（负向尺） |
| Q2 | `durationLabel` 退回 `″` | ②′ 时长角标行为 **和** ② 负向尺（一笔咬两把，正是"写法只一档"的两面） |
| Q3 | chip 前缀那档收回 `space1` | ③ 同一档间隙（两处锚点都掉） |
| Q4 | 日榜空态条件出口改成恒给 | ④″ 条件与去向（表达式钉） |
| Q5 | 历史坏档那枚出口撤掉 | ④″ 同一颗（道数不掉 ⇒ 证明**光数道数会被这种改法喂饱**） |
| Q6 | 「看全部」整枚出口拿掉 | ④′ 道数封顶 13→12 |

中途一处台架自己绊了自己：Q4 第一版终点串写成 `actionText = "重试",`，而**同一颗文件里日榜整页失败视图本来就有一枚逐字相同的** ⇒ 回滚 `ANCHOR_NOT_UNIQUE` 直接退出，把树留在被扰动那一格（且前五笔台账全丢）。修法 = 终点串自带身份戳（`// 扰动：…`）+ 台账**逐笔即印**。这条是"扰动笔必须自带身份戳"那一课的第 N 次复发，已在脚本注释里写死。

**数字** = 新立 `components/UiAuditG2Test` **6 笔**（三条负向尺 + 两条行为尺 + 一条道数尺），全量 `:app:testDebugUnitTest` **1237 笔 / 160 颗类 / 0 失败 0 错误**（G1 收尾 1231 / 159，只增不减）、`:app:assembleDebug` 绿；台架 **6 笔全咬、逐笔对题、还原零残留、`BUILD_FAILED` 0 笔**。

⚠️ **包体那一格不算差值**：universal debug 现量 108,198,804 B，比 G1 那笔 105,524,552 B"涨了 2.67MB"—— 而本批改动是四行文案/间距级的东西，吃不下 2.67MB。同一现象本仓早已登记过（"包体 +40MB 是脏增量填充"），所以这里的字节数只作"包能打出来"的在场证据，**不许拿它当回归判据**；要量包体差异得先 `clean` 再打，那是另一件事。

**没做与需运行验证**：① 屏上成对截图全欠（设备没连着）—— chip 前缀那 4dp 在浅色/深色与玻璃档是否拉得开、`3s` 在角标那个尺寸是否读得清、日榜空态那枚「重试」是否真落在屏上，都要上机；② `actionText` 那两档"决定不接"若将来要接，缺的是**导航回调接线**，不是文案；③ 探索页/漫画侧那 15 处零出口空态不在本轮口径（画廊为主）。

---

## 七、跨 G1/G2 的一条口径声明（关于「大屏适配不许动手机档」）

G1 那枚 `ComicDetailScreen` 的底部让位**会改手机档的像素**（原来只垫 8dp，现在多垫一整个底栏高度）；G2 的 chip 前缀间距也是 2dp→4dp、两档一起变。这两处都不属于那条 standing 约束管的那一族 —— 约束防的是"为了宽窗把手机版的列数/几何改了"，而**悬浮底栏在手机上同样盖在内容上**、**前缀与查询词在手机上也同样黏成一串**：它们是两档共同的缺陷，不是大屏专属的适配。

若用户认为这两处只该在平板改，撤回代价很小：让位那一行按 `wideScreenLayoutMode` 分支即可，而 G1 那把尺子（只数接线道数、不数像素）不会因此变红。这一格写在明处，是为了下一轮复算时不用重新猜当时的口径。

---

## 八、G3 收口（画廊第三批：节头动作贴标题 + 非墙块宽度收口，2026-10-15 深夜）

**开工前先把这一格自己的凭据量出来**（清单 §五 第 7 条：凡本轮要"修"的条目，先在同一张截图上给出节点 bounds）。这一格 v2 里**只有代码形状、没有坐标**，所以先补取证再动：

- 探针 `_qa/section_header_gap_probe.js` → 读数落 `_qa/ui-verify-2026-10-15/section_header_gap.txt`。判据 = 节头标题右缘与**同一行**右邻第一枚节点的间隙：
  - 平板 1920px：「正在关注的画师」[24,454][185,488] → 「另有 2 位」[1827,459][1896,484]，间隙 **1642px ≈ 621dp**；「每日热门」[24,715][116,749] → 动作那枚 [1792,696][1896,767]，间隙 **1676px ≈ 634dp**；
  - 手机 1080px（density 2.625）：三节分别 **577 / 639 / 670px ≈ 220 / 243 / 255dp**。
  ⇒ 症状**两档都成立**，平板更重。这不是"平板专属适配"，是共用件本身的排法错 —— 所以改动落在 `feature/SectionHeader.kt` 一颗，手机一起修（口径见 §七）。

**改了什么**（只动 `app/` 两颗文件 + 尺子）：

1. `feature/SectionHeader.kt`：`horizontalArrangement` 从恒 `SpaceBetween` 改成两支分叉 —— `trailing == null`（「›」那一支）保持贴右缘（它表达"整行是一个入口"，箭头就是把手），有真按钮那一支改成 `Arrangement.spacedBy(tokens.spacing.space4)`（8dp，现成档），动作贴标题。
2. `gallery/ui/GalleryHomeSections.kt`：一节的外壳 `GalleryHomeSection` 多一枚**必填** `wallAdjacent`，非墙那两节（画师行 / 每日热门预览行）在宽窗吃现成的 `contentMaxWidthFor`（Medium 720 / Expanded 840）并在满宽 item 里居中；「猜你喜欢」那一节**不收口**。

**为什么「猜你喜欢」那一节必须不收口**（这是本批唯一一处容易做反的判断）：它只有节头，而节头底下紧接着的就是**它自己的全部卡片** —— 节头是这面墙的第一行。把它收到 720 居中，平板上会得到"节头比墙窄、左缘对不齐"这一种**新的**错位，等于拿一个新错位换一个旧错位。清单 §一-4 那句"墙本体不限宽"的边界正在这里，所以它不靠注释守着，靠尺子的负向名单守着（`GalleryScreen` / `GalleryForYouScreen` / `GalleryFavoritesBody` / `GalleryHistoryScreen` / `GalleryReverseResults` 五颗文件恒不许出现 `contentMaxWidthFor`）。

**一处差点写错的判据**（自审）：居中那一跳本鱼先写了 `Column(...align(Alignment.CenterHorizontally))` —— 编译报错 `expected 'Alignment'`。`CenterHorizontally` 是 `Alignment.Horizontal`，只在 **ColumnScope** 的 `align` 上成立（`GalleryArtistProfileScreen` 那处就住在 Column 里，抄形状要抄语境）；这一枚子件住在 **Box** 里，正解是 `Box(contentAlignment = Alignment.TopCenter)`。编译器抓到，不是肉眼抓到。

**免豁免的一条现量凭据**：`_qa/section_header_arm_scan.js` 扫全 main 的 `MiuixSectionHeader(` 调用点（读数 `_qa/ui-verify-2026-10-15/section_header_arms.txt`）—— 首页 6 处**全部**走 `onTap` + 「›」那一支，`trailing` 只有画廊这 3 处 ⇒ **本批不动 FROZEN 的 `HomeScreen`**，无需记外观豁免。首页那 6 行的箭头贴右缘仍然在册，但它属于 P1-7（符号当图标，`›` 换矢量件）那一族，归后续批，不在本批口径。

**台架 4 笔，逐笔各红在自己那颗 `@Test`**（`_qa/g3_perturb.js`，终点串一律自带 `// 扰动：` 身份戳 —— 上一批 Q4 那笔的终点串与同文件另一枚逐字相同，回滚 `ANCHOR_NOT_UNIQUE` 把树留在了坏那一笔，这一批把戳写进终点串并把台账改成逐笔即印）：

| 笔 | 扰动 | 红在哪 |
|---|---|---|
| R1 | 节头退回一档通吃 `SpaceBetween` | ① 两种行尾各有各的排法 |
| R2 | 「猜你喜欢」那一节误收口（`wallAdjacent = true` → `false`） | ② 三节对收口逐个表态（`false` 2→3、`true` 1→0） |
| R3 | 给**主墙**接上收口（在主墙那颗文件里放一枚 `contentMaxWidthFor` 探针） | ③ 墙本体恒不参与收口（负向名单）—— 这一笔证明 ③ 不是恒真尺 |
| R4 | 收口之后不居中（`TopCenter` → `TopStart`） | ② 同一颗（居中那条断言） |

`BUILD_FAILED` 0 笔、还原后锚点各现查 1 处、`rigProbe` 字样在树里 0 处。

**数字** = 新立 `components/UiAuditG3Test` **3 笔**；全量 `:app:testDebugUnitTest` **1240 笔 / 161 颗类 / 0 失败 0 错误**（G2 收尾 1237 / 160，只增不减）、`:app:assembleDebug` 绿；台架 **4 笔全咬、逐笔对题、零残留**。手机档零退化那三把现成尺（`WideScreenPolicyTest.phoneBandIsPinnedAtTwoColumnsAndNeverTouchesTheBudget` / `phoneBandNeverGetsAContentWidthCap` / `ComicColumnPolicyTest`）复跑仍全绿。

**一条"已有的尺子不许重复立"**：清单 §五-3 写着"新增一条『Compact 档 `contentMaxWidthFor` 恒 `null`』的钉"—— 现查 `WideScreenPolicyTest.kt:171` **早已存在** `phoneBandNeverGetsAContentWidthCap`（还有 `:176 contentCapWidensWithTheBand` 钉 720/840 与 Expanded>Medium）。那一格**作废**，本批不重复立尺（仓内纪律：同一件事只许一把尺子；这是"还没有 X 而 X 已落地"那一族读数漂移的又一次复发，主语换成了"要新增的判据"）。

**没做与需运行验证**：① 平板 720 居中这一格**没有屏上凭据**（本机无设备）—— 现量只能证"代码接到了现成口径"，横滑行在 1291dp 上居中后与下方满宽网格的关系要看图；② 节头动作贴标题后的观感（动作是否读起来属于标题那一组）同样待图；③ 首页那 6 行的 `›` 仍在右缘，属 P1-7 那一族，另批处理。

---

## 九、T1 开工前的一格算术（收藏分段器：批准的方案在那块屏上只缓解不治好）

清单 §四 T1 写的修法是把 `FavoritesScreen.kt:635-636/666-667` 的 `wide` 分支从 `fillMaxWidth()`
改成**与手机同一推导**（`segmentedCellWidthFraction × modes.size` = 75% 屏宽）再居中。
本批动手前先把那一档在 MuMu 平板上算了一遍（density = 1920px ÷ 1291dp ≈ 1.487，与 P1-2 那条
"正文单行 1214px = 815dp" 同一把换算）：

| 档 | 整条宽 | 每段宽（扣两颗 10dp 间隙） | 对照标签实宽 88px = **59dp** |
|---|---|---|---|
| 今天（`fillMaxWidth()`） | 1291dp | ≈ **434dp** | 富余 ≈ 7.4 倍 |
| 批准方案（75% 屏宽居中） | 968dp | ≈ **316dp** | 富余 ≈ 5.4 倍 |

⇒ 方案确实把症状往回带了一档（两边各留 161dp、段与段不再顶到屏幕缘），但"每颗占一屏的三分之一、
标签只占其中七分之一"这一条**没有被治好**，因为它缺的不是宽度上限，而是**段宽没有跟着内容走**。

治好的那条要动共用件本身：`VeneraSegmentedButton` 的每颗是 `Modifier.weight(1f)`（等分调用方给的宽度），
**只要还是 weight，段宽就永远由屏宽推导，不会由标签推导**。改成内容定宽会同时改变
画廊两页切换、图片收藏那几排的观感（那颗组件的全部调用点），属"要先出稿等点头"那一档 ——
不是本批能顺手做掉的。

**所以 T1 拆两格**（不发明新机制，沿用已批准的 helper 与既有 idiom）：
- **T1a · 限宽接线**（方案成立，可直接做）：`StatsScreen` / `ComicSourceScreen` / `SourceSectionScreen` /
  `UnifiedExploreScreen` 四颗通栏内容块接 `contentMaxWidthFor`（Compact 恒 null ⇒ 手机零退化已由
  `WideScreenPolicyTest:171` 钉着）。要先定一件事：这四颗是**逐颗包一层**（G3 在画廊做的那形状），
  还是抽成一枚 `WideContentShell` 共用外壳（清单 §二-4 说的"外壳级"）—— 后者是系统层，代价是多一枚
  公共件与一次 LazyColumn 高度语义确认（包层会不会让满屏列表拿不到高度），本鱼倾向前者先跑两颗、
  量过观感再决定要不要抽件。
- **T1b · 分段器**：等 T1a 的屏上成对截图回来一起判 —— 如果 316dp 已经读得出"一组互斥选项"，
  这一格就到此为止；如果仍读不出，就得点头动 `VeneraSegmentedButton`。

---

## 十、T1a 收口（平板限宽接线第一批：三颗屏各一个收口点，2026-10-15 深夜）

**坐标凭据先量**（清单 §五-7；`_qa/ui-verify-2026-10-15/tab/ui-t6-stats.xml`，node 现取 → 读数落 `stats_texts.txt`，density 1920px÷1291dp≈1.487）：

- 四格大卡两列起点 **x=39 / x=985** ⇒ 每列 ≈943px = **634dp**（清单 P1-2 那句"四卡各 ~930px"复算一致）；
- 14 天柱的横轴从 x=98 铺到 x=1822 ⇒ ≈1724px = **1159dp**；
- 榜单第一行标题「吃白飯的大肥魚的特殊服務」右缘 **x=321**，读数「192 页」左缘 **x=1827** ⇒ 同一行里隔 **1506px = 1010dp**（与 §零 那条改判后的数字逐字吻合）。

**改了什么**（三颗屏，每颗**只在页面级列表那一处**挂收口，全部吃现成的 `contentMaxWidthFor`，`Compact` 恒 `null` ⇒ 手机档逐像素不变）：

1. `feature/StatsScreen.kt` —— 三块内容同住一棵 `LazyColumn`，收口挂它，四个 item 一起收；
2. `feature/sourcemanage/ComicSourceScreen.kt` —— 只收页面级那一棵；内部四棵 `heightIn(max=…)` 的子列表（弹层/展开区）一行没动；
3. `feature/explore/SourceSectionScreen.kt` —— 这一颗是本批唯一要**多想一层**的：它的**列数**来自 `rememberContentWidth` 的 `onSizeChanged` **实测宽**，再喂给 `comicListColumnCount(mode, listWidth)`（每格预算 220dp / 详情档 360dp）。所以收口挂在 `.then(listWidthModifier)` **之前**，列数会自己跟着降（1267dp 上 5 列 → 696dp 上 3 列），**每格还是那一档预算宽，卡片不会变小**。

   ⇒ 这条就是"带网格的页面能不能套收口"的**分界判据**：列数若从**窗口宽**推导（而不是实测宽），套上去就会得到"6 列挤进 720dp、每格 120dp"那种事故 —— 那一路要先让列数接同一把尺子，才许收口。尺子里钉了**顺序**（`widthIn` 的位次必须早于 `listWidthModifier`），因为"存在但排错位"是这一族唯一会静默出事的形状。

**第四颗没接**：`feature/explore/UnifiedExploreScreen.kt` 与 SourceSection 同一形状（一行接线，且它的 `listWidth` 也是实测宽），但它住在冻结名单里 —— 动它要在 `FREEZE-STATEMENT.md` 记一条**外观豁免**，而那条登记要照 2026-10-14 探索页改版那一节的写法。本批没把那节读完就先没动：**宁欠一行，不留半条豁免**。

**G3 那把名单尺子顺手改了形状**：原先写成 `assertEquals(四颗名单, 现量名单)`，那会把"合法新增一颗屏"也判红 —— 红一次人就学会"别加尺子"，比没尺子更糟。改成 **名单内每一颗都还得在**（subset 判据）+ 墙那一族的**负向名单**，新增要登记但不punish合法工作。

**台架 5 笔**（`_qa/t1a_perturb.js`，逐笔即印、终点串自带 `// 扰动：`）：

| 笔 | 扰动 | 红在哪 |
|---|---|---|
| S1 | 统计页的收口从列表上摘掉 | ① 每一颗屏只在页面级列表那一处收口（adjacency 锚点掉） |
| S2 | 统计页收宽之后不居中（`TopCenter`→`TopStart`） | ① 同一颗（居中那条） |
| S3 | 口径抄成字面数 `widthIn(max = 720.dp)` | ② 收口宽度不许抄成字面数（**并**顺带掉 ① 的 adjacency） |
| S4 | 图源管理那页的收口摘掉 | ① 那颗文件的三条计数锚点 |
| S5 | 带网格那页把收口挪到量宽之后 | ① 顺序钉（`capAt in 0 until measureAt`） |

`BUILD_FAILED` 0 笔、还原后三颗文件各现查 1 处、全树 `扰动` 字样 0 处。
⚠️ 一条异常登记在册：这一套笔**第一次跑**时在中途 `ANCHOR_NOT_UNIQUE StatsScreen hits=2` 退出（`apply` 先查 hits 再写，所以退出时没污染树），我随后只改了量具与新增第三颗屏的接线，复跑同一套 5 笔全过 —— 那一次 hits=2 的成因**没查清**，所以它不算已证读数；如果下一轮再遇见，先 `git diff` 那三颗文件的 cap 行数再动量具。

**数字** = 新立 `components/UiAuditT1aTest` **2 笔**；全量 `:app:testDebugUnitTest` **1242 笔 / 162 颗类 / 0 失败 0 错误**（G3 收尾 1240 / 161，只增不减）、`:app:assembleDebug` 绿；手机零退化那两把尺（`WideScreenPolicyTest` 22 笔、`ComicColumnPolicyTest` 9 笔）复跑全绿。

**没做与需运行验证**：① 平板 720/840 居中后的观感（统计页四卡变成两列各 ~348dp、榜单行标题与读数仍隔 ~600dp —— 那一格是行内 `SpaceBetween` 的口径，不在本批）；② `UnifiedExploreScreen` 接线 + 豁免登记；③ 探索页/图源管理那两屏的成对截图（`tab/scr-t32-explore.png`、`_small/scr-b25-sourcemanage.png` 是改前那一份）。
