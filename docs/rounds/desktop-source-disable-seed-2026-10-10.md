# 批 83.1 · `--disable=<源键>` 取证种子（2026-10-10）

排期那份（`remaining-plan-and-reuse-2026-10-10.md` §4）里标为"小"的那一档：照 `--comic=` / `--detail=` 那一族抄三层落点。
`app/` 一字未动。

## 1. 这一批要的东西是"帧上看得见"

批 83 立的那条口径写在 `DesktopComicSourcesScreen.kt` 的类注释里：「**停用是真生效的** —— 只在列表上标个灰而装载照旧，就是本仓那一族假开关」。
今天证明它的只有离屏语义树那一层（`DesktopComicSourcesSemanticsTest`）。那意味着"标灰而照旧装载"这种缺陷形状
**在帧上看不看得见全凭运气** —— 而它恰好是那种"判据全绿而屏上说的是另一回事"的老族。
这一枚种子让起窗那一趟直接带着"某颗源是停用着的"起来，于是两样同时进画：源管理那一屏的枚数与灰行、以及真装的那一颗源。

三条定案（都配了尺子）：

1. **种子只并进本次装载，不写盘上那份**。`venera_desktop_sources.json` 是用户在这台机器上的状态，
   取证参数把它改掉等于量具动了被量的东西。尺子 ⑥ 钉的是结构：`setDisabled(` 在这一屏的代码行里恰好一处，
   而那一处是用户点一下那一路。
2. **认不认得那串由消费点念**，判读层只管"读没读到一串字"。清单（`keys`）只在消费点现取，
   递到集成方去比 = 列两遍资产目录，还会让"念的那一份"与"画的那一份"分家（两条轨那一族）。
3. **只取第一颗**：同一个名字给两遍时并两颗，屏上"停用了 N 颗"会念出用户没打的数。

## 2. 落点（六处）

| 层 | 文件 | 落的那一跳 |
|---|---|---|
| 判读 | `desktop/…/ForensicFlags.kt` | `DISABLE = "--disable"` 登记进 `KNOWN`、`disable(args): String?`（空值与只给名都算没给并出声）、`disableNote(seed, keys): String?` 三档 |
| 集成方 | `desktop/…/VeneraDesktop.kt` | `val seedDisabled = listOfNotNull(ForensicFlags.disable(args.asList())).toSet()` ⇒ 递进 `DesktopComicEngineConfig(seedDisabled = seedDisabled)` |
| 配置面 | `gallery/ui/DesktopComicPane.kt` | `DesktopComicEngineConfig` 多一枚 **必填** `seedDisabled: Set<String>`（不给默认值：漏传的形状与本批要抓的缺陷同形） |
| 消费点 | `gallery/ui/DesktopComicSourcesScreen.kt` | 起手那份 `disabled` = 盘上那份 **∪ 种子**（写成一行，判式只看含锚点那一行）；`LaunchedEffect` 里多念 `disableNote` 那一句 |
| 构建脚本 | `desktop/build.gradle.kts` | `providers.gradleProperty("disable").orNull?.let { "--disable=$it" }`（同一颗纪律第十次） |
| 尺子 | `desktop/src/test/…/DesktopSourceDisableSeedTest.kt` | 6 条，见目录那一行 |

## 3. 起窗那一趟（决定性那一格：帧上看得见）

一条命令里做完：数窗 → 起窗（隔离数据目录 `LOCALAPPDATA=D:\venera-compose\_qa\b831_local`）→ 轮询 stdout → 拍帧 → `Stop-Process` → 复数残留。
参数：`-Pkey=goda -Pdisable=goda -PstartDomain=COMIC -PstartSection=comic-sources`（**故意点名要一颗被停用的**，
这样"折进装载"这件事在屏上是可看见的落回，而不是一句自述）。

stdout（`_qa/b831_run.log`，逐字）：

```
D_启动 sources=34 起始=goda proxy=直连 仓库兜底=D:\venera-compose\app\src\main\assets
D_数据目录 D:\venera-compose\_qa\b831_local\venera（写入回读已验）
D_落点 域=漫画(启动) 行=源管理 浮层=关(换域或换页自动收) 查看器=关 详情=关(启动) 阅读=关(启动) 种子=无
D_源 回落 goda→baozi（goda 已停用）
D_源 停用=1 清单=34 口径=桌面本地（与共享表 comic_source 刻意不同轨）
D_取证参数 --disable=goda 清单里有这一颗 ⇒ 已并进本次装载（只这一趟；要动盘上那份得在源管理那一屏点一下）
```

帧（WGC，窗口 `Venera Desktop R1-F 最小闭环 #39648`，`relatedTransientWindows=[]` ⇒ 没有 Error 从属窗）读到的三样：

- 段头「清单共 34 颗，这一台机器上**停用了 1 颗**」；
- 第一行标着「**在用** 包子漫画（baozi）」—— 装载落回的那一颗，与 stdout 那句同一份状态；
- 「GoDa漫画（goda）」那一行右侧写着「已停用」而按钮是「启用」—— 种子折进来的那一颗，**第一次在帧上看得见**。

**"不落盘"那一格有正面读数**：这一趟结束后 `_qa/b831_local/venera/prefs/` 是**空目录** ——
`venera_desktop_sources.json` 从来没被建过，而屏上写着"停用了 1 颗" ⇒ 那 1 颗只活在本次装载里（定案 1 成立，不是推断）。
关窗后同标题窗 **0** 枚。⚠️ 真人那份 `%LOCALAPPDATA%\venera` 这一趟没碰过（数据目录那行念的就是 `_qa/b831_local`）。

## 4. 现量与 RED→GREEN

- RED（实现之前）：编译面 **9 处 `Unresolved reference`**（`disable` / `disableNote`）—— 形状与批 82.9b 同一族。
- 第一次 GREEN 是 **6 条里红 1 条**，而红的是**我自己刚写的锚点**：② 当初钉的是动词措辞（`折进` 或 `并入`），
  实现写的是「已**并进**本次装载」⇒ 按"它要防的事发生了吗"改判：换成钉事实词「本次装载」与「一颗也没」。
  这是手册 §0 坑 ㉖ 那一族的**又一次**，而且红在刚写的那条判据自己身上（第六次以上）。
- 全量：**1027 → 1033 条 / 133 → 134 颗类 / 0 失败**（+6），测试文件 141 颗、`desktop/src/main` 101 颗 / **20,514 → 20,572 行**（+58，全在上面那六处里）。
- `app/` 一字未动（`git diff --stat -- app/` 空）；交付包**未重打**（本批只加一枚命令行种子，产品面行为在无种子时一字未变 —— 那一格由台架 P6/P7 与全量 0 红撑着）。

## 5. 台架 `_qa/b831_perturb.js` 十一笔

`sh _qa/perturb_driver.sh b831 11` ⇒ **P1~P10 各红在自己那条**，P11（等价改写）**要求不红且确实一笔没红**；收尾全量 `EXIT=0` / 1033 / 134 / 0。

| 笔 | 改坏什么 | 红在哪条（逐笔报告带身份戳） |
|---|---|---|
| P1 | 空值不再算没给（`return null` → `return raw`） | ① 判读四档 |
| P2 | 只给名字不带值时把名字自己当源键（去掉 `.let{}` 那半） | ①（`expected null, but was:<--disable>`） |
| P3 | 同名给两遍取最后一颗（`firstOrNull` → `lastOrNull`） | ① |
| P4 | 认不出那档不点名那串（去掉 `$seed`） | ② 三档读数 |
| P5 | 认出那档不说"并进的是本次装载" | ② |
| P6 | 消费点不并种子（起手那份退回 `getOrDefault(emptySet())`） | ⑤ 三层落点（起手那一行没吃种子） |
| P7 | 集成方递一枚 `emptySet()` 当假动作 | ⑤（锚点是 `seedDisabled = seedDisabled`，只钉"这个词在不在"会被这种假动作喂饱） |
| P8 | 构建脚本那一跳整行注释掉 | ⑤（`-Pdisable=` 变成"不报错也不做事"） |
| P9 | `KNOWN` 漏登记 `DISABLE` | ⑤（会被念成拼错的参数） |
| P10 | 起手顺手 `config.sourceState().setDisabled(disabled)`（把用户状态写了） | ⑥ 种子不落盘（写盘点 1 → 2） |
| P11 | **等价改写**：并集交换两个操作数（`seedDisabled + 盘上那份`） | **不红** ✅ 锚点没冻住写法 |

⚠️ 台架自己被抓两笔（都在 `check` 阶段就拒跑，白省两趟全量）：P2 与 P9 第一版的 `to` 串是 `from` 串的**子串**
（"删掉列表末尾一枚"、"去掉尾巴上那段 `.let{}`"这类改写的必然形状），撞上"终点串在改坏之前必须 0 处"那道闸。
正解是**换一种不等价的形状**（换序、加 `+ ""`），而不是放宽那道闸 —— 那道闸是批 81 那族"回滚把另一行改坏"换来的。

⚠️ 另一条量具账（本批立竿见影的回报）：`report` 认"当前是第几笔"**只认 apply 留下的戳**（`_qa/b831_current.txt`），
不反查文件状态 —— 那是批 84′ 刚踩过的那一格（P6 与 P8 共用同一条改动时把正确输出念成"尺子不咬"）。

## 6. 下一件

排期那份的 **批 85**（下载落盘：Coil 单例 + `DiskCache`，A 档第一件）。
挂着没动的：交付包这一批**没重打**（种子只在带 `-Pdisable=` 时改行为，无种子时产品面一字未变 —— 那一格由 P6/P7 与全量 0 撑着；
要在包里验这一枚得能递命令行参数，而 app-image 那一路的参数怎么递**今天没量过**）；
`-Pshot=` 那一格这一批**没走**（帧是窗口捕获拿的，不是应用自己截的）。
