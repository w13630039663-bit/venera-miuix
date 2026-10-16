# 批 108 · 表空过那一瞬之后，新排进来的一笔仍要被接走（方案 + 落地账，2026-10-14 凌晨）

上一格是批 107（三颗取证种子的 slug 号，rounds `desktop-forensic-seed-slug-ids-2026-10-13.md`）。
本批做批 107 §8 登记而未做的那一条：**泵在表空那一刻退出，之后排进来的一笔没人接**。

## 0. 缺陷那一形（不是推测，是包内读数）

`_qa/b107_dl2.log`（批 107 第二趟包内复验，窗口落在「探索」+ 详情浮层，隔离数据根）：

```
D_队列 笔=0 跑=0 待=0 停=0 败=0 完=0 留档=无      ← 引擎装载完、泵起来的那一刻，表是空的
D_详情 源=goda 号=/manga/pandajunheleisajun-hiyaku 态=上屏 话=15 …
D_下载种子 号=/manga/pandajunheleisajun-hiyaku 话=19777@1253812 落在=第 1 话 目录=15 话
D_队列 笔=1 跑=0 待=1 停=0 败=0 完=0 留档=无      ← 一直到关窗：待=1 而跑=0，盘上 0 页 0 清单
```

`desktop/src/main/kotlin/com/venera/desktop/gallery/ui/DesktopComicQueueRunner.kt:66` 那一行就是缺陷本体：

```kotlin
val task = DesktopComicDownloadQueue.nextPending(root) ?: break
```

泵起来时表空 ⇒ 那一圈当场退出。之后 `enqueue` 把那一笔排上，而**没有任何一圈在等它**：
泵挂在 `DisposableEffect(session)` 上（`DesktopComicPane.kt:459`），而 `session` 没换 ⇒ 不会再踢一次。
批 106 让「下载队列」那一行自己驱动泵，可那一行是"有活才起句柄"，
**"句柄已经起来了而表空过一次"** 这一形它管不到 —— 详情那一屏长按一笔、跑完，再长按第二笔，就是这条路径的日常版本。

屏上那一句「站在这一屏就接着跑」（批 106 改准的）在这一形下也是半句假话。

## 1. 定档：挂在那张表上等新的一笔（不轮询、不另立状态件）

`DesktopComicDownloadQueue` 里 `nextPending` 留着（纯读一眼，测试与归账都要它），另加一颗会等的：

```kotlin
suspend fun awaitNextPending(root: File): DesktopComicQueueTask {
    while (true) {
        nextPending(root)?.let { return it }
        _tasks.first { tasks -> tasks.any { it.status == DesktopComicQueueStatus.PENDING } }
    }
}
```

三档备选，为什么挑这一档：

- **A（本批做）**：等的是那张 `_tasks`（`MutableStateFlow`）。新入队那一次赋值就是把挂着的这一跳叫醒的信号；
  StateFlow 一上来先给当下那个值 ⇒ **挂上去之前已经排好的那一笔同样接得到**（不丢唤醒）；零新地基、零空转。
- B（否决）：`enqueue` 里自己踢一圈泵。要能把"当前那一圈"递到入队处，就得让队列对象持有协程句柄 ——
  数据层长出一颗作用域引用是批 101 明确避开的那条形（泵的执行器在 UI 层）。
- C（否决）：每 200ms 问一次表。读数会变好，但那是**假活**：把"没人等"换成"有人空转"，
  还凭空多一枚没有任何界面说得清的延迟常数。判式 ⑥ 专门负向钉 `delay(`。

返回值**非空**：这一跳只有"等到一笔"与"被掐掉"两种结局。旧的那个 `null` 档正是缺陷本体，
所以泵那一行不再需要 `?: break`，而挂起期间被取消（退域）走的是 `finally` 那一格 —— 与从前同一形，
`abandonRunning` 仍把「正在跑」交回「待跑」。

## 2. 接缝现量（2026-10-14 现数，`grep` 可复核）

| 要动的地方 | 现量 |
|---|---|
| 缺陷那一行 | `DesktopComicQueueRunner.kt:66`（`?: break` 在全 main 只这一处） |
| 取表那一枚纯读 | `DesktopComicDownloadQueue.kt:344`（`nextPending`，测试里 8 处在用 ⇒ 保留） |
| 那张表的宿主 | `_tasks: MutableStateFlow`（`DesktopComicDownloadQueue.kt:246`），收表的两屏 = `DesktopComicPane.kt` + `VeneraDesktopApp.kt`（批 101 ⑩ 钉着） |
| 泵的重启键 | `DesktopComicPane.kt:458` `remember(session)` 与 `:459` `DisposableEffect(session)` —— 句柄不换就不重跑，这是"没人接"的第二半成因 |
| 旧签名点名的尺子 | `DesktopComicDownloadQueueTest.kt:261`（批 101 ⑨ 数的是"取待跑恰一处"）⇒ 本批只把 token 换成 `awaitNextPending(`，**计数口径不变** |
| 屏上那一句 | `DesktopComicQueuePane.kt:164`（批 106 ⑥ 钉「回到漫画域」在该文件零处）—— 本批不动文案，只补台架笔 P6 钉它有牙齿 |

## 3. 判式（新文件 `DesktopComicQueueWakeTest`，6 颗 @Test）

1. **有待跑那一笔时立刻交回** —— 表里已有一笔 ⇒ 那一跳不挂起（防"恒不返回"那一形）。
2. **表空那一格等新排进来的一笔**（缺陷本体）—— 先断言那一跳**还没交回**，再 `enqueue`，
   再断言唤醒后交回的是**新排进来那一笔**（防"恒返回旧的/返回 null"）。两档各喂一次，是本仓第七次同一条纪律。
3. **旧那一形在泵里零处** —— `DesktopComicDownloadQueue.nextPending(` 与 `?: break` 各 0 处（正面"恰一处"仍由批 101 ⑨ 钉，不双轨）。
4. **唤醒看的是档位** —— 钉整串 `_tasks.first { tasks -> tasks.any { it.status == DesktopComicQueueStatus.PENDING } }`
   与整串 `suspend fun awaitNextPending(root: File): DesktopComicQueueTask`（标识符看不见死分支；写成恒真就是每次都往下跑＝没等）。
5. **没有第二颗状态件** —— 那颗文件里 `Channel(` 与 `MutableSharedFlow(` 各零处，收表的两屏名单不变（两颗状态会漂，本仓踩过两回）。
6. **等待不是轮询** —— 那颗文件与泵那颗文件里 `delay(` 与 `Timer(` 各零处。

## 4. 台架（`_qa/b108_perturb.js`，6 笔，全要求红）

- **P1** 唤醒条件认错档位（等 `RUNNING`）⇒ 咬 ②④；① 仍绿（证明 ①② 不冗余）。
- **P2** 改成 `delay(200)` 轮询 ⇒ ①② 两笔行为**照样绿**，只有 ⑥ 咬得住。这一笔就是"假活只能靠形状尺子"的实证。
- **P3** 泵退回 `?: break` ⇒ 咬 ③ + 批 101 ⑨。
- **P4** 为唤醒另立一颗 `Channel` ⇒ 咬 ⑤（顺带 ②）。
- **P5** 取表改成末位优先 ⇒ 咬批 101 ⑰（执行序漂了）。
- **P6** 屏上那句改回「回到漫画域就接着跑」 ⇒ 咬批 106 ⑥。

笔形纪律照旧：终点串仍可编译、起终不互为子串、终点不空、不与同文件已有字面量同串、不用死支。

## 5. 取证种子

复批 107 那一路就够，不加新的：`--start-domain=COMIC --comic=<slug> --download=<slug>/<话号>`，
**起手表空**（隔离数据根里不放 `download_tasks.json`）⇒ 期望读数从 `待=1 跑=0` 变成 `跑=1` 再到 `完=1`，
盘上真长出页与清单。这一格同时把批 107 欠的"演示种子那格数据"一起结掉（号用真 slug）。

## 6. 代价档

**小**：一颗 suspend 件（三行）+ 泵那一行去掉 `?: break` + 一把旧尺子换 token + 六笔台架 + 包内一趟。
不动作用域宿主、不引第二颗 `EngineSession`、不加任何延迟常数或新状态件。

## 7. 落地账（2026-10-14 凌晨）

① `awaitNextPending` 落地（`DesktopComicDownloadQueue.kt`），泵那一行换成它，`?: break` 在全 main 归零。
② 新尺子 `DesktopComicQueueWakeTest` 6 颗：先写、先看它红（`Unresolved reference 'awaitNextPending'` 那一族 + `Unresolved reference 'File'` 一处，补 import 后编译面才走到"缺实现"那一步）。
③ 批 101 ⑨ 换 token（`awaitNextPending(`，计数仍是 1，口径没放宽）。
④ 台架 6 笔：逐笔归账见本文 §7a。
⑤ **顺带结掉两处 HEAD 就红的桌面尺子**（都是移动端那两笔提交留下的接缝，不是本批造的）：
   - `DesktopGalleryPreferences.kt:48` 没回答 `GalleryPreferences` 第 13 枚成员 `galleryWatercolorBeta`（随 `802a900` 进来，手册行 169 明写了"`:desktop:compileKotlin` 是断的，任务仍欠"）。
     按桌面既形补：那一枚在桌面**没有入口** ⇒ 给 `ALWAYS_FALSE` 常量，与 `galleryHideTopBar/BottomBar` 同一形，不造一条能写却没人读的键。
   - `DesktopGallerySettingsSpecTest` ③ 数到 13 枚而账上是 12 ⇒ 缺席那一区补一行 `galleryWatercolorBeta`，加法跟着走 8 + 3 + 2 = 13（措辞现量：`WatercolorRenderer.kt` 吃 `android.graphics` 的 `Bitmap/Canvas/Paint/BlurMaskFilter`，桌面结构性没有那层位图管线）。
   - `DesktopReaderPagedSeedTest` ②（默认阅读档回核）与 ③（不许在表外写死档位串）在 `cd0e1bd` 把对岸默认从别名 `"VERTICAL"` 换成表名 `"HORIZONTAL_LTR"` 之后**互相咬死**。
     改判 ②：两边各过 `DesktopReaderMode.fromKey` 归一再比那一档（比的是行为而不是字面），并加一枚"默认那枚真的喂给读侧"的接线钉；③ 一个字不放宽。
     桌面默认档跟着同轨：`READING_MODE_FALLBACK` 改成从那张表取（`DesktopReaderMode.HORIZONTAL_LTR.key`），
     理由是那颗文件自己的口径 —— 键与默认值都不是这一颗定的，对岸那串由尺子现取。
⑥ 交付包与包内那趟见 §7b。

## 7a. 台架逐笔归账

`_qa/b108_perturb.summary.txt`（2026-10-14 凌晨现跑，驱动 `sh _qa/perturb_driver.sh b108 3` + `… 6 4`，每笔跑全量）：

| 笔 | 改坏那一形 | 红了谁 |
|---|---|---|
| P1 | 唤醒条件等的是「正在跑」那一档 | ② 行为（`Timed out waiting for 2000 ms`）+ ④ 形状（钉整串那一枚 0 处）—— **① 仍绿**，两档不冗余这件事由这一笔证出来 |
| P2 | 改成 `delay(200)` 轮询 | 只有 ⑥ 反轮询 + ④（那一整串没了）—— **①② 两枚行为判式照样绿**：这就是"假活只能靠形状尺子"的实证，也是本批最要紧的一笔 |
| P3 | 泵退回 `?: break` | 批 101 ⑨（取待跑恰一处）+ 新 ③（旧那一形必须零处）|

还原后全量：`suites=166 tests_sum=1436 testcase=1436 @Test=1436 files=173 failures=0`（`EXIT=0`）。

P4-P6：

| 笔 | 改坏那一形 | 红了谁 |
|---|---|---|
| P4 | 为唤醒另立一颗 `Channel(1).receive()` | ② 行为（挂死到 2000ms 超时）+ ④（那一整串没了）—— **但 ⑤ 第一遍没咬**：那一枚寻的是裸串 `Channel(`，而笔写出来的是 `Channel<DesktopComicQueueTask>(1)`，尖括号插在 `Channel` 与 `(` 中间 ⇒ 恒寻不到。**改的是尺子不是笔**：⑤ 换成裸词 `Channel` / `MutableSharedFlow`（那颗文件里这两个词今天 0 处，收紧不会误咬），P4 复跑才算收口 |
| P5 | 取表改成末位优先（`firstOrNull`→`lastOrNull`） | 批 101 ⑰ `重启续排只排没跑完的`（`expected:<[a]> but was:<[b]>`）|
| P6 | 屏上那句改回「回到漫画域就接着跑」 | 批 106 ⑥（那一串在队列那颗文件里必须零处）|

⚠️ **P5 连红两遍量具才走通，两次都是本鱼自己造成的并发**（记下来免得下次又当成"尺子不咬"）：
第一遍是 `_qa/b108_dist.log` 那一趟交付包**与前 3 笔的驱动撞车** —— 驱动放锁与第二颗 `while [ -d lock ]`
之间有一段空档，打包链在那一刻冲进去，于是那一笔的 gradle 什么都没跑出来（`_qa/b108_p5.log` 0 行）而 `report` 撞 `ENOENT`；
第二遍 `rm -rf desktop/build/test-results/test` 被那颗**被停掉却还活着的构建 JVM** 占着（`rm` 退 1、后面的 `&&` 全没跑）。
处置：`TaskStop` 那一条并不杀子进程 ⇒ 数 `Get-Process java` 现量 2 颗、隔一分钟自己退了，`rm` 复跑退 0，第三遍 P5 当场咬住。
**这一形是本仓记过的「后台长任务被我停了而它的 JVM 还在」的正面复用：停链不等于停进程，量具坏了要现数。**

## 7b. 包内复验（交付物里那一圈真的把新的一笔接走了）

包 `_qa/desktop-exe-2026-10-14-b108/`（216 MB / `createDistributable --offline --rerun-tasks` = 11 actionable tasks: 11 executed /
`check_pkg_jar_sha.js` 念「两颗都对得上」（desktop jar 5,089,316 B、engine-probe 147,715 B）/ `check_cfg.js` classpath=100 missing=0 orphan_jars=0）。

**第一趟作废**（两格都是我的账，不是应用的）：`try-empty-table-wake.bat` 第一版把 slug 号写成**裸斜杠**，
应用按第一百零七批那口径当场拒收并念「漫画号段是空的」/「不是两截」；而那一趟的 `run-wake.log` 后半截
出现 `详情=开(点封面)`、`阅读=开(点某一话)`、`侧栏点「图库」` ⇒ **有外部操作者正在用那一扇窗**，
所以本鱼没有 taskkill 它，也没拿那一份 log 立案（前案：「行自己在走」先排外部操作者）。启动器改成 `%2F` 那一形（正解本来就在批 107 那句提示里）。

**第二趟**（同一颗 exe、另起一份隔离数据根 `probe2/`，`--download-cancel-after=2`；用完按 PID `taskkill`，收尾残留 0）：

```
D_队列 笔=0 跑=0 待=0 停=0 败=0 完=0 留档=无      ← 泵起来时表是空的：批 108 之前那一圈就在这里收摊
D_详情 源=goda 号=/manga/pandajunheleisajun-hiyaku 态=上屏 话=15 … 话号=19777@1253812,…
D_下载种子 号=/manga/pandajunheleisajun-hiyaku 话=19777@1253812 落在=第 1 话 目录=15 话
D_队列 笔=1 跑=1 待=0 停=0 败=0 完=0 留档=无      ← 本批那一格：新排进来的一笔被同一圈泵当场接走
D_下载进度 号=/manga/pandajunheleisajun-hiyaku 话=0 页=0/30
D_下载 … 全=否 因=取页失败 页=0 共=30 因=onImageLoad 失败: (intermediate value).sources["goda"].comic.onImageLoad is not a function
D_队列 笔=1 跑=0 待=0 停=0 败=1 完=0 留档=无      ← 失败抛而不静默少页（批 102 那颗口径守着）
```

盘上快照（进程停之后才数）：`probe2/venera/downloads/goda/_manga_pandajunheleisajun-hiyaku/0000_第01话/chapter.json`
= `{"chapterId":"19777@1253812","title":"第01话","order":0,"expectedPages":30,"complete":false}`，页 0 颗。
两处顺带对上口径：目录名里那一格号是 `desktopComicSafePathKey` 把 `/` 替成 `_` 的结果（与本文 §5 推的一致），
而 `chapterId` 那一格装的是**话号**不是本子号（批 97 那一课的写侧）。

**wake 这一格由此收口**：行为判式（②）在单测层用真队列证了"挂住 → 新入队唤醒 → 交回新那笔"，
这一趟在交付物里证了"同一圈泵真的把那一笔跑起来了"。两条缺一都不算。

## 7c. 这一趟顺手炸出来的新缺陷（登记 = 批 109，题面已带读数）

`sources["goda"].comic.onImageLoad is not a function` —— 桌面取页图那一路（`EngineSession.pageImage`）只认 `onImageLoad`，
而**不是每一族源都声明它**（goda 这一族没有；混淆站那一族才有）。后果：整话落盘在 goda 上恒 `失败了`，
而墙上/详情/预览都正常（那几路走的是另一颗地址）。要做的是：先量对岸 `ComicSource` 在缺 `onImageLoad` 时用的是哪一条默认
（大概是"原样交回 url"那一形），再让桌面同一形，而不是自己造一条。读数就在这一份 log 里，别凭印象接。
