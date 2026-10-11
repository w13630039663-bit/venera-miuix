# 桌面第六批：`--query=` 让搜索域进得了无人值守取证（2026-10-04）

范围：`desktop/`。**Android 侧零改动**（`git status --short app/` 空）。分支 `compose-migration`，未提交。

---

## 1. 为什么补这一枚

第五批收尾时留了一条**结构性拍不到的账**（手册 §19）：AI 角标那一枚只有单测覆盖、没有图证。
根因不在角标，在**搜索域** —— 七枚域里只有它"不给输入就什么都拍不到"：

| 域 | 起来就有内容吗 |
|---|---|
| 图库 / 收藏 / 漫画 | 有（日榜取数、本机存档、引擎探索） |
| 发现 / 画师 / 设置 | 坦白页，但那本身就是要看的东西 |
| **搜索** | **没有** —— `var query by remember { mutableStateOf("") }` 是写死的空串，不发查询 |

而键盘与鼠标自验是禁的（手册 §8），`-Pshot` 那一环又越不过窗口前台（§16.4）。
于是"AI 角标在真数据上长什么样""搜索域的开窗形态"这两类账**没有一条路能拍**。
这一枚把查询串变成命令行上的读数，与 `--start-domain=` 同一族。

## 2. 判读三档，与 `--start-domain=` 故意不同

```kotlin
fun query(args: List<String>): String?          // null = 没给；"" = 给了空；非空 = 预填
fun queryNote(query: String?, domain: DesktopDomain): String?   // 落在没有搜索框的域 → 一句实话
```

- **认不出没有"认不出"这一档**：值是一条自由文本，任何字符串都是合法查询。
  所以它不像 `startDomain` 那样会产出假证据 ⇒ **不中止启动**。
  两枚参数的口径**是故意不同的**，这一点写进了 `query` 的注释，免得下一个人"顺手统一"。
- `null` 与 `""` 分开：两档在**那一屏上落同一个初值**（都是空查询），分开是为了 stdout 念的是哪句
  （"不预填" vs "按空查询起"）。注释里明说了别假装空串能拍出不一样的屏 —— 那是两回事。
- 落在非搜索域：不中止，但必须出声。静默就是"我给了它却没做任何事"，那是假开关的口径。

## 3. 四跳接线，参数一律必填

`main()` → `VeneraDesktopApp(startQuery=…)` → `DesktopDomainBody(…, startQuery=…)`
→ `DesktopGallerySearchBody(row, ports, startQuery)` → `DesktopGallerySearchPane(ports, startQuery)`
→ `var query by remember { mutableStateOf(startQuery ?: "") }`。

**不给默认值**：漏传落下来的形状是"搜索那一屏永远拍不到内容"，而那**不会报错**。
判据 ⑯ 钉的就是这条链的两端（`main()` 调了 `query(`、pane 的初值是那颗参数而不是写死的空串）。

## 4. 判据 +4（262 → 266），两处对照组各抓到一次我自己的错

- `ForensicFlagsTest` +3：三档判读（含带空格的 `flat chest` 要原样留着）、登记进 KNOWN（对照组拼错那枚必报）、
  落在别的域那句实话（点名查询串与那一域两个东西）。
- `DesktopDomainNavigationTest` ⑯ +1：起始查询串必须真被交给搜索那一屏。

两处**对照组**都立功了：
① ⑯ 最初拿 `DesktopGallerySearchPane(` 作对照组，跑出来是红的 —— 因为应用根调的是域路那颗
`DesktopGallerySearchBody`，pane 在另一颗文件里。**对照组把我的错误假设抓了出来**，判据落点跟着改对。
② 第五批那条 `previewUrl` 对照组（同族）已记在 §19。

## 5. 图证：这一枚把"拍不到"变成了"拍得到"

`./gradlew :desktop:app -Pproxy=127.0.0.1:7890 -PstartDomain=SEARCH -Pquery=ai-generated`，
WGC 窗口捕获（`occlusionSafe=true`，窗口在 z 序最底也拿得到）：

- 输入框里是 **`ai-generated`**，下面一排建议词胶囊（`ai_generated`、`ai-generated_post`、
  `ai-generated_woman_underwater_breathing_bubble`、…），来源轴四枚（全部/yande.re/Gelbooru/Safebooru）。
- 段头「[ai-generated]」+「2 站 · 148 张 · Gelbooru 128 张」，墙上是真结果。
- **AI 角标定死了**：每张被站方打标的卡右下角有一枚「AI」胶囊 —— 这一条正是第五批拍不到的那个东西。
- 顶部那行红字如实念 Gelbooru 缺凭据（`DAPI 匿名一律 401，需 api_key + user_id`），没有把它算进"给内容"。

⚠️ **一条取证方法的读数（已写进手册 §16.4）**：第一次判图时我以为角标**没渲染** ——
ON/OFF 两帧在 0.85 缩放的整窗截图上看起来一模一样。用"把字号临时放大到 16sp"的探针复跑才定死：
**渲染着，是看不清**。判"屏上有没有某个小件"不能靠缩放整窗图肉眼比，要么放大那一件跑一次探针，
要么量像素。另外那次 A/B 本身也不干净：我忘了上一跑写过的 prefs 文件还在（角标开关被钉成 false），
清掉之后才是真的"默认档"那一跑。

## 6. `-Pshot` 顺带又量了一次，结论更硬

同一形状再跑一次带 `--shot=` 的启动：

```
D_截图 抬窗 AWT 报了 isAlwaysOnTop=true ｜ Win32 on=true SetWindowPos=1（非 0 才算生效）位已上=true exstyle 0x108→0x108
D_截图 无效：中心点 (768,498) 落在 hwnd=65904 标题「FolderView」类名「SysListView32」，本窗 hwnd=… 类名「SunAwtFrame」 —— 不产出假证据
```

上一轮压在上面的是**播视频的 Chrome**，这一轮是**文件资源管理器的 FolderView** ——
两类完全不同的窗口都越不过，而 `exstyle 0x108→0x108` 说明 TOPMOST 位本来就在。
⇒ §16.4 那条"别再跟 z-order 较劲"现在有两类反例支撑，不再是单点读数。

## 7. 两条共享面发现（都在 `app/` 侧，等拍板，本批没动）

1. **`GalleryContentGuard.addRule` 是接口上唯一有实现、零调用点的写方法**，且**不在**
   `GalleryWriteSurfaceTest` ⑩ 那份"零调用点写方法"清单里（那份枚举的是 `GalleryBusinessApi` 的 12 个方法）。
   我独立复核：`override suspend fun addRule` 共 **4 处**（`AndroidBusinessPorts.kt:156`、
   `GalleryPortsAndroid.kt:72`、`DesktopGalleryContentGuard.kt:59`、桌面测试假实现），
   改签名要动 4 颗实现 —— 与那 12 个方法（各 2 处）不是一个量级。
   **今天没有故障**（Android 侧守卫页在用那颗 manager），所以它属 Part B 那一档，等拍板。
2. **`splitGalleryTags` 按空格切分**（`GalleryPost.kt:271-272`：`raw.trim().split(' ')…`），
   所以带空格的标签会被劈成碎片（`male:chest` 这类命名空间标签不受影响）。
   报告方给的"这种标签真实存在"的证据在 `docs/rounds/tag-statistics-and-similarity-tuning-2026-09-26.md:212`，
   **那一条我没复算**。它同时影响题材统计与 AI 判定（都读 `tagList`），属 Android 侧，等拍板。

## 8. 读数（实跑）

- `:desktop:test`：**266 用例 / 0 失败 / 0 错误（34 颗类）**。`ForensicFlagsTest` 12 颗、
  `DesktopDomainNavigationTest` 16 颗。
- `:desktop:packageUberJarForCurrentOS` EXIT=0，产物 **97,577,366 B**（第五批末 97,575,969 B）。
- 运行期读数三条：`--start-domain 认成「搜索」`、`--query 预填搜索框（值=ai-generated）`、
  以及落在别的域时那句 `queryNote` 实话（单测钉着，未在这一跑触发）。
- 取证条件已还原：`%LOCALAPPDATA%\venera\prefs\` 目录为空（本轮前也是空）。
- 探针残留已查：`grep -n "BADGE-ON\|if (true)\|fontSize = 16.sp\|fontSize = 22.sp"` 在
  `DesktopGalleryPostTile.kt` 里**零命中**，真实角标那行 `aiBadgeFor(post, aiBadge)` 恰 1 处。

## 9. 下一批的输入

1. **设置页写侧**：现在能摆的档有 4 枚（列数 / 清晰度 / AI 角标 / 屏蔽 AI），
   另外 6 枚等桌面大图页 —— 摆出来就是假开关，宁缺。写侧要新建（桌面今天 getter-only）。
2. 屏蔽规则写侧 `addRule` 零调用点（§7.1），与设置页同一批解最省。
3. 建议词那一排现在是"按空格切"的产物（§7.2 那条会影响它），要不要接成可点，等拍板。
