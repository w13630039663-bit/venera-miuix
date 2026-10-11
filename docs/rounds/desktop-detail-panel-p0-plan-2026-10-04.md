# 桌面第十一批 P0：详情面板（覆盖浮层）· 开工单（2026-10-04，三条拍板点已批）

方案与代价在 `docs/rounds/desktop-detail-panel-plan-2026-10-04.md`（§6 三条用户已全部批准：
① 推翻"卡不可点"、② 填不出的格子**从稿上撤掉**而不是留"这一站没给"、③ 三档算式搬进 `gallery/domain`）。
**③ 是 P1 的事，本批不动 `app/`**。本文件是自含开工单。

---

## 1. 这一批只做四件事

1. 卡位**可点**：`DesktopGalleryPostTile` 加**必填** `onClick: (GalleryPost) -> Unit`（不给默认值 ——
   第十批那条"带默认值的新参数漏传不报错"的教训，`DesktopCallbackParamLedgerTest` 扫着）。
2. 选中态住在**应用根**一颗 `mutableStateOf<GalleryPost?>`，浮层叠在主区外面（A 档）。
3. 面板只摆**填得出**的格子（§3 那张表），三个动作：收藏 / 复制链接 / 在浏览器打开原图。
4. 页脚那句状态改判（"已选 1 项 · 详情浮层在位"这类话不许凭空摆上去）。

## 2. 尺寸一律取稿上的现成口径（**不造新数字**）

`docs/designs/windows-gallery-home-touhou-2026-10-03.html`：
`.detail{width:340px}`（`:215`）、`.detail::before{width:46px}` 左缘渐隐（`:217`）、
`.dhero{height:200px}`（`:218`）、`.dclose{30×30, radius 999}`（`:222`）、
`.dbody{padding:14px 16px 16px, gap:12px}`（`:226`）、`.infotbl .k{width:76px}`（`:236`）。
⇒ 进 `DesktopGalleryMetrics`：`detailWidth=340.dp`、`detailScrimWidth=46.dp`、`detailHeroHeight=200.dp`、
`detailCloseSize=30.dp`、`detailBodyPadding=16.dp`、`detailBodyGap=12.dp`、`detailInfoKeyWidth=76.dp`。
色一律走现有五档（`DesktopTheme.LayerBackground`/`SurfaceRaised` 那一族），**不新增第六档**。

## 3. 面板摆哪几格（逐格回代码量过的结论，抄自方案 §3/§4）

| 摆 | 格 | 出处 |
|---|---|---|
| ✅ | 顶部那行"站名 · ID xxx"（**不是标题** —— 标题结构性没有） | `post.site.displayName` + `post.id` |
| ✅ | 标签一排（原样展示，**本地不做二次归类**；页脚那句照稿抄） | `post.tags` 空格切 |
| ✅ | 收藏开关（真写侧） | `ports.favorites.toggle(post)`（桌面已有读侧件） |
| ✅ | 复制链接 | AWT `Toolkit.getDefaultToolkit().systemClipboard`，桌面今天零调用点 ⇒ 新接 |
| ✅ | 在浏览器打开原图 | `java.awt.Desktop.getDesktop().browse(URI)`；⚠️ 别写成"桌面做不到"（本仓公开错过一回） |
| ✅ | 来源 / 分辨率 / 格式 | `site.displayName` / `width×height` / `fileExt` |
| ✅ | 大小 —— **只在 `fileSize > 0` 时那一行才摆** | Gelbooru 站方 JSON 没有 `file_size`（client 明写"→ 0"），`0 MB` 是假读数 |
| ✅ | 已收藏 / 未收藏（"本地状态"那一格的诚实版） | `ports.favorites.contains(uid)` |
| ❌ **撤** | 标题、相关标签、画师"作品 N"、**上传时间**、下载按钮、底部那枚重复的"查看原图"、pips 页码 | 前三样结构性没有；上传时间三颗 DTO 都没解 `created_at`；下载是 P2；pips 是 P1 |

⚠️ 撤掉的意思是真的不画，**不是画一行"暂不支持"**（手册 §12 一号原则）。

## 4. 骨架那一处改动与它牵动的判据

`VeneraDesktopApp.kt:162-169` 现在是 `Column(Modifier.weight(1f)…) { DesktopDomainBody(…) }`。
A 档要的是**主区外面套一层 `Box`**：`Box(Modifier.weight(1f).fillMaxHeight())` 里
① `Column(Modifier.fillMaxSize()) { DesktopDomainBody(...) }`、② 面板 `align(TopEnd)`。
⇒ `DesktopShellLayoutTest` 那 5 条几何判据里"主区那一列收 `weight(1f)`"这条**必须改判**
（改成"`weight(1f)` 在主区那层 `Box` 上，Column 收 `fillMaxSize()`"），并加一条：
**面板不许进 `weight` 分配**（它是覆盖层；进了就是把列数从 6 压到 4 的那条旧账 —— 稿 `:660` 量过）。

## 5. 判据（先红一次）

| # | 钉什么 | 判法 |
|---|---|---|
| ① | `onClick` 必填且**五处调用点都真传**（漏一处就是"某一屏点不动"） | 扫五颗文件各含 `onPick =` / `onClick =`；对照组：墙那颗的签名里 `onClick` 不带默认值 |
| ② | 卡位可点之后**触达面积**：整张卡是点击面，不是里面某个小图标 | 扫 tile：`Modifier…clickable` 挂在外层 `Box` 上（`clickable` 在 `Modifier` 链里的位置） |
| ③ | 面板上**不许出现撤掉的那几样**：`上传时间`、`相关标签`、`作品 ` + 数字、`暂不支持`、`分享` | 扫面板件全部字符串字面量，一次列出全部违例（§23.3 那条规矩） |
| ④ | 大小那一格只在 >0 时摆 | 真跑那颗纯函数 `desktopDetailInfoRows(post): List<Pair<String,String>>` —— **整张信息表提成纯函数**，Gelbooru 那条不出现"0 MB"，yande.re 那条出现 |
| ⑤ | 选中态在应用根，且**切域/切行不清**（清了就是"翻一页面板自己关"）但**换种子（换一批）要清** | 扫 `VeneraDesktopApp` + 各 pane：`selectedPost` 只有一处声明；`lead`/`items` 的 key 与它无关 |
| ⑥ | 浮层不吃 `weight`、宽恒 `detailWidth` | 扫面板件与骨架：`align(Alignment.TopEnd)` + `width(DesktopGalleryMetrics.detailWidth)`，且同一层里没有 `weight(` |
| ⑦ | 三个动作各有真落点：`browse(` / `systemClipboard` / `favorites.toggle` 各 ≥1 次，且**没有一处是 TODO/空 lambda** | 扫源码 + 对照组 |
| ⑧ | 稿上那批尺寸**不许造新数**：面板那颗件里出现的 `.dp` 字面量个数必须为 0（全走 metrics） | 扫面板件代码行的 `Regex("[0-9]+\\.dp")`，违例一次列全 |

## 6. 收尾验证

`./gradlew :desktop:test`（不接管道，落文件读退出码）→ `:desktop:packageUberJarForCurrentOS` →
`git status --short app/` **必须仍是那三颗**（本批不动 app/）→ 图证：`-Pshot=` 或窗口捕获，
要看的是"点一张卡 → 右侧 340 浮层在位而**列数不变**"（这条是稿 `:660` 那句"内容区未重排"的实证）。
⚠️ 本机若被用户的窗口占着前台，`-Pshot` 会被自家判式判无效 —— 那就如实记"没有像素证据"，别硬凑。

## 7. 不在本批

P1 三档换图 + 面板内翻页（要 §6③ 那次 `app/` 改动，且"当前第几张"往 `DesktopGalleryBatch` 上加字段，
**不造第二颗**）；P2 下载（要新造桌面落盘件）；P3 三枚偏好（接上之后 `DesktopGallerySettingsSpec`
那笔 `4+6+2=12` 要跟着改，判据 ③ 会红一次，那是该红）。

---

## 8. 落地回写（2026-10-05）

**四件事全部落地**，总账在手册 §27。判据 343 / 0 / 0（46 颗类，新增 `DesktopGalleryDetailTest` 七条），
uber jar 97,814,772 B，`git status --short app/` 仍是那三颗 store。

四处与本工单不同，逐条点名：

1. **判据 ⑥ 换了判法**（不是放宽）。原句扫骨架那颗件里有没有 `DesktopGalleryMetrics.detailWidth`，
   而落地后"面板自己知道自己多宽"更对 —— 那句会红在一个更好的形状上。改成两侧各钉一处：
   宽由面板从口径表取（`width(DesktopGalleryMetrics.detailWidth)`），渐隐由骨架挂
   （`detailScrimWidth` 出现在 `VeneraDesktopApp.kt`），并补一条对照组"主区仍吃 `weight(1f)`"。
2. **点卡这条回调走 CompositionLocal**（`LocalDesktopWallPick`），而不是工单里默认的"一路加参数"。
   理由：它要穿四段派发，其中两段（`DesktopGalleryDomainPanes.kt` / `DesktopGalleryHomeSections.kt`）
   是并行会话正在改的文件。默认值 `{ error(...) }` 而不是 no-op —— 漏 provide 的表现是静默的假开关。
3. **口径表多了五颗**（`detailCloseInset` 10 / 信息表行内衬 7·10 / `hairline` 1 / `tightGap` 6 /
   `cardRadius` 8），全部有稿上的行号。原因是判据 ⑧ 禁了写死的 dp，而稿上这几档本来就有值。
4. **图证只拿到基线屏**：`--start-domain=gallery` 起窗 + WGC 捕获，证明骨架 `Column`→`Box`
   之后"内容区未重排"（六列、hero、rail、pane、状态栏全在位）。**浮层那一案没有像素证据** ——
   它要点一张卡，而动鼠标被禁（手册 §8）。补这一案缺一枚 `--detail` 取证种子，已记 §27.6 第 2 条。

顺带修掉两条**反方向的假读数**（文案说"还没有 X"而 X 已落地，手册 §「实现了但零调用点」那条的镜像）：
`DesktopDailyPane` 类注释第 3 条从前写"卡片不可点，卡位上不摆像素"（第四批起卡位就一直真取图），
`DesktopDailyHero` 从前引"桌面没有大图页也没有详情态"作理由 —— 两句都已改成今日实情，旧那句留在原地并写明它当时为什么对。
