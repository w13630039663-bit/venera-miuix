---
name: project-multi-ai-regression-triage
description: 本仓库由多个 AI 会话交替改动且工作区长期带多批未提交修改；"本来正常"先审那一轮 diff，提交要按主题切笔，push 前先看 ahead 数
metadata:
  type: project
---

**事实**：`D:\venera-compose` 同时被好几个 AI 会话（含用户自己另开的 AI）交替改。2026-09-20 那轮是 03:13–03:56，一次改了 18 个 `assets/sources/*.js` + 4 个 Kotlin，全部**未提交**。用户报"绅士漫画/禁漫 加载不出来"时，这两处真因一个在 Kotlin 网络层默认值、一个就在那轮未提交 diff 里。

**Why:** 只读源码容易把"端口自己写错"误判成"上游改版/源失效" —— 我就先花了一轮去逐字 diff 官方 `venera-configs` 脚本，结论是"与官方一致，所以不是我们改坏的"，方向完全对反了（官方一致只排除了*脚本*，排除不了*宿主 Kotlin*）。是用户补一句"昨晚03:58用另外的ai改了后就有问题了"才纠回来。

**How to apply（定位侧）:**
- 听到"本来正常/改完就坏"：第一步 `git status` + 按 mtime 排 `find app/src -name '*.kt' -o -name '*.js'`，把嫌疑收敛到那一轮的**未提交工作区 diff**，逐处问"这行改的是谁"；再谈上游。
- 用户说「**你刚才的改动有个问题，检查一下**」时同理，而且**编译是绿的**（2026-09-21 实测：`compileDebugKotlin` / 单测 / `assembleDebug` 全过）。所以别拿编译当检查，去审自己那一轮的**生效条件**：一次性标记（bootstrap 只在首装跑）、异步注册的竞态（冷启动拿不到源）、只对全新安装起效的默认清单。这三类都会"写对了但设备上看不见"。
- 端口对 JS 源的一切默认行为都要拿 `master` 分支（官方 Flutter 版）逐字对齐，而不是只对齐 `.js`。已踩过的两处：`JsHttpHandler` 兜底 UA（官方 `js_engine.dart` 注入桌面 `webUA`，端口写成移动端 UA → wnacg 这类按 UA 分模板的站整源选择器失配）；`JsComicSource.getChapterPages` 的 epId 归一化（用 `chapterId == comicId` 猜"占位章"会误伤 jm 这类**源自己**把单章 id 写成漫画 id 的源）。
- 改动落在 Kotlin 侧才能直接装机生效；改 `assets/sources/*.js` 对已装设备无效（见 [[project-js-source-assets-not-live]]）。

**提交与推送侧（同一批事实的另一半）:**
- 工作区一次会压着**好几批不同主题**的未提交改动，而且**同一个文件里混着不同主题的 hunk**（2026-09-20 实测：`ComicDetailScreen.kt` 既有上一轮的"预览图折叠"又有我的"封面共享元素"）。非交互 `git add` 拆不开同文件的 hunk，所以：**新文件要随依赖它的那一笔一起落地**（否则那一笔编译不过），并在 message 里写明搭车了什么。
- 用户已验证的选择**取决于能否按文件干净切开**：
  - 2026-09-20：能切开时选「**按主题分笔**」，其中包括替上一轮 AI 把它那两批各自单独提。别打包，也别只提自己那半截留个半成品。
  - 2026-09-23：`Navigation.kt` / `SettingsHost.kt` / `ComicDetailScreen.kt` / `ui/tokens/*` 全是**同文件跨轮混改**，非交互 `git add` 拆不开 hunk，我列出「只提本轮这条链 / 先列清单再拆多提 / 全部一笔」三选项后，用户选了「**全部一次提交**」。→ 切开无望时打包一笔是可接受的，但 message 必须**按主题分段**写清本轮做了什么、又搭车收了哪些既存改动（那次 94 文件 / +5398 −1194，搭了更新检查、主题色板、启动图标、存储根、AI 屏蔽判据等）。
- **拆笔先看暂存区与工作区的天然边界，通常不用切 hunk**：上一轮做完时往往**整体已 `git add`**，于是 `git diff --cached --stat`（≈ 上一轮）与 `git diff --stat`（≈ 后面几轮）**本身就是主题分界线**，同文件跨轮（`Navigation.kt`、`JsComicSource.kt`、`ComicSourceModels.kt`）会自动各归其位。顺序：把只属第一轮的文件的未暂存增量也 `git add` 进去 → 提第一笔 → 依次加后面几轮的文件。提完第一笔前用 `git diff --cached -- <file> | grep -c "<下一轮关键词>"` 验没串味（2026-09-24 这样把三轮干净拆成三笔，事前**并不知道**上一轮暂存过）。
- **用户默认偏好是按轮拆**：2026-09-24 我先建议拆三笔并等确认，用户直接答「拆成三个 commit 提交一下吧」。所以**先探能否按文件切开**，切不开才退回上面那条打包一笔。
- **09-29 复现了"整天空着没提交"的形状**：上一笔停在 09-28 03:16，而 09-29 一天里第三/四/五轮 + 画廊批次 A/B/C1/C2
  + 玻璃批次 D **全部未入库**（99 条状态、54 个改动文件）。此时"按轮拆"已经**不可重建** ——
  `VeneraPreferences.kt` 一个文件里物理叠着三批偏好键，`SettingsComponents.kt`、`FREEZE-STATEMENT.md` 同理，
  而 `FREEZE-STATEMENT.md` 里 C2 那节与 D 那节在同一文件的相邻段落。→ 按 09-23 已验证的选择**打包一笔**，
  并在 message 里逐轮点名 + 写明"为什么没拆"。
- **诊断/加日志这类"只加读数、不改行为"的改动要单独一笔**，并在 subject 里写死"未改行为"：
  否则下一轮看 diff 会把"我加了成因日志"误读成"这个 bug 修过了"（2026-09-29 实测：我提完日志笔后必须先向用户声明
  这条 bug 仍未修，用户回"禁漫还是有问题"才没串味）。
- 工作区排除面再加三项（都别入库，同 `.tmp-src` 一族）：`_probe/`（从库缓存解出的取证副本）、`_qa/`（本轮截图）、
  `_trash/`（可逆清理镜像）。一次带全部用 `git add -A -- . ':(exclude)_probe' ':(exclude)_qa' ':(exclude)_trash'`，
  提完立刻 `git status --short` 核对**只剩这三项**。
- **暂存集自己也要复核，别信自己的 grep**：我用 `grep -iE "key|secret|token"` 查密钥，结果命中
  `ui/tokens/Color.kt` 这种**包名片段**，差点据此报"发现可疑文件"。判据要按**完整路径/行首**匹配。
- 提交前的门：`:app:compileDebugKotlin` + `:app:testDebugUnitTest` 实跑过再提（2026-09-23 用户只说"提交吧"，我先跑了这两条才动手）。
- `compose-migration` 的领先数**不要拿历史值当基线，每次现查**：2026-09-20 领先 **42 笔**、09-22 **46**、09-23 **49**，而 2026-09-24 只剩 **4**（含本轮 3 笔）→ 中间有别的线推过远端。用户说"提交"≠"push"；说"push"时**先 `git status -sb` 报出 ahead 数再确认范围** —— git 推不了"只推最新几笔"，而远端是共享状态。永不 `--force`。
- **push 不通 ≠ GitHub 不可达**（2026-09-22 实测）：`git push` 走直连失败，但 `curl` 打 `api.github.com`、`raw.githubusercontent.com`、`github.com` 全是 200/301。所以**查外部现成实现这条路是通的**（仓库树用 `api.github.com/repos/<r>/git/trees/<branch>?recursive=1`，取文件用 raw），只是推不出去；push 需要用户给代理端口。本机**无 `gh`、无 `python`**，解析 JSON 用 `node -e`。
- **拉取侧比记的更强**（2026-09-22 实测）：`git clone --filter=blob:none --sparse --depth 1 https://github.com/<r>.git` + `git sparse-checkout set <dir>` 直接成功，340 个源文件一次到位，比逐文件 curl 快一个量级。研究第三方仓库实现一律走这条，别再用 trees API 一个个取。

**改数值前先二次校准磁盘值（2026-09-22 实测坑）**：我在同一轮里对 `ui/tokens/Typography.kt` 第一次 `Read` 拿到 `caption 12 / overline 11 / badge 10`，几分钟后第二次 `Read` 与 `sed`/`cat -A` 都是 `13 / 12 / 11`，而 `git status --porcelain -- <path>` 输出**空**、mtime 停在昨天 22:34 —— 即磁盘从头到尾没被人改过，是**第一次读到的值不对**，不是并发编辑。差点据此在方案文档里写出一张「原值→新值」的假对账表。**How to apply:** 凡是要把某个数值写成「原来是 X、改成 Y」的对比结论，先用第二个来源校一次（再 `Read` 一遍、或 `sed -n '/锚点/,/)/p'`、`cat -A`），并用 `git status --porcelain -- <path>` 区分「有人同时在改」与「自己读岔了」；别拿单次 Read 当基线。

**方案文档里别的会话写的"已落地"必须回代码核实（2026-09-22 实测）**：`docs/rounds/reader-large-screen-adaptation-2026-09.md` 被补了一节，明写"阅读器返回跳过详情页的根因已定位并落地、两条护栏已加、单测已过、判别实验不再必要"。实际 `git diff` + 读文件后确认 **`Navigation.kt` 那一段一个字都没改**，自毁分支还在原地，声称新增的用例也不存在。**How to apply:** 看到文档写"已落地/已修/单测已过"，先把它当**待核的主张**而不是事实 —— 读一次目标文件或 `git diff` 即可；据此再决定要不要动手。**绝不要为了让代码追上文档去伪造那几条测试**（本轮明确拒绝补假单测，改完只做设备复测并如实说明"这条没有单测，验收靠设备"）。同理，文档被外部改过时系统会提示"file was modified since last read"，那正是有人（或另一个会话）动过的信号，别当成噪声。

**另一轮正在改首页时，判据层要能脱离 gradle 单跑（2026-09-30 实测）**：那天 `:app:compileDebugKotlin` 与整个单测源集**两次**因另一 AI 的中间态编不过（`GalleryScreen.kt` 缺 import、`GalleryHomeSectionsTest.kt` 引用已被删掉的 `CHIPS`/`RECENT`）。gradle 的 test 任务把整个源集一起编，于是自己的 RED/GREEN 也做不了。**做法**：纯 Kotlin 的判据层用 `kotlin-compiler-embeddable` + JUnit4 直接 `java org.junit.runner.JUnitCore` 单跑（脚本 `_probe/l0/run-judgment-tests.sh`，文件清单写死在脚本里），要点四条：① 用 **gradle 那台 JDK 21**（PATH 上的 Java 26 会让 2.2.20 的 codegen 报 `Unsafe` 弃用）；② 编译器自己的 `-cp` 要带 **stdlib + kotlin-reflect + kotlinx-coroutines-core-jvm + org.jetbrains:annotations**，缺一条就是 `NoClassDefFoundError` 而不是源码错误；③ 被测源文件必须**逐个列进清单**，漏一个就是 `unresolved reference`；④ 编译输出可能含 NUL，用 `tr -d '\000'` 过一遍再 grep，否则 `grep` 只会回一句 "Binary file matches"。**不去改别人那一屏的文件**，也不要把红说成绿 —— 等它收口后再跑一次全量 gradle 单测作为结案读数（那天最后 510/0/0）。

相关：[[project-subagent-search-hazards]]、[[feedback-design-review-then-code]]
