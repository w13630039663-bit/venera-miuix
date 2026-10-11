# 批 82.9b · 交付包里取不到 SQLite 的原生库（我们自己去 jar 里取那一档）2026-10-10

批 82.9 补上 `java.sql` 之后，包内 exe 仍然读不到库（同一屏、同一棵库、同一个 `行=0`），
报错从类缺失前进成 `NativeLibraryNotFoundException`。这一批修那一格。
账的前半（四条已量掉的候选、双向判据）在 `docs/rounds/desktop-package-runtime-modules-2026-10-10.md` §4.1/§4.2，本文件只记修法与本批读数。

## 1. 断点在哪一格（复述批 82.9 的读数，不重量）

- 包内：`Class.getResource("/org/sqlite/native/Windows/x86_64/sqlitejdbc.dll")` 交回 **null**（`%TEMP%` 里那一跑没留任何解出的 dll，也没有那颗 `FileException`）。
- 开发跑：同一颗 jar、同一条资源，非 null（每次起 JVM 都留一对 `sqlite-*-sqlitejdbc.dll` + `.lck`）。
- ⚠️ **"为什么包内 null"到今天没有定论** —— 本批不猜成因，只把这一格绕开并让它可测。
  绕开的前提是量过的：我们这一侧的包内资源读法在包里能用（`/sources/` 34 份、词典那份都读到了），
  而 `System.load` 那条路在包内也通（§4.2 用 `-Dorg.sqlite.lib.path` 拍出过 `行=3`）。

## 1.1 第二格读数：包内进程在 `java.io.tmpdir` 里建目录也被拒

第一版落点走 `Files.createTempDirectory`（默认落 `C:\Users\leimi\AppData\Local\Temp\`），重打后包内 exe 念出：

```
D_环境 jdk=23.0.2 tmp=C:\Users\leimi\AppData\Local\Temp\ 打包=true
D_异常 线程=DefaultDispatcher-worker-1 次=1 因=AccessDeniedException: C:\Users\leimi\AppData\Local\Temp\venera-sqlite-native-9822446818487778378 链=0 栈=sun.nio.fs.WindowsException:-1
java.nio.file.AccessDeniedException: …\Temp\venera-sqlite-native-9822446818487778378
	at java.base/sun.nio.fs.WindowsFileSystemProvider.createDirectory(Unknown Source)
	at java.base/java.nio.file.Files.createTempDirectory(Unknown Source)
	at com.venera.desktop.platform.DesktopSqliteNative.place(DesktopSqliteNative.kt:68)
D_历史 屏 源=jm 名=禁漫天堂 行=0
```

同一颗 JDK、同一行代码在开发跑里成功（那 6 条判据就是真跑落的盘），而**同一个包进程**在数据根里
写入回读是验过的（`D_数据目录 …（写入回读已验）`，那次跑的数据根已被 `LOCALAPPDATA` 指到 D 盘的
`_qa/b828_local`）⇒ 差的不是路径就是"那个进程被允许往哪儿写"。**这条也没定论**（本批第二条未定论），
只按量到的那一侧走：**落点由调用点递进来**，落在库文件旁边（同一棵数据根里）。
顺带换来一条好性质：隔离数据根跑实验时那份 dll 也一起被隔离，不往真用户目录堆东西。

## 2. 形状

新件 `desktop/src/main/kotlin/com/venera/desktop/platform/DesktopSqliteNative.kt`（桌面自有，不进共享面）：

| 那颗 | 管什么 |
|---|---|
| `nativeEntry(osName, osArch)` | 纯算式：系统 × 架构 → jar 里那一条资源路径；**认不出交回 null 而不就近猜档**（把 x86_64 的二进制发给 arm64 只会崩在更远的地方）。档位名逐条回核包内那颗 jar 的真实条目（`Windows/x86_64/sqlitejdbc.dll`、`Mac/aarch64/libsqlitejdbc.dylib`、`Linux/x86_64/libsqlitejdbc.so`） |
| `place(root, readResource)` | 取字节 → 落到**调用点递进来的那棵** `root/sqlite-native/`（文件名带档位，换系统换架构不撞车）→ 把驱动认的 `org.sqlite.lib.path` / `org.sqlite.lib.name` 指过去。同档位那份字节数一致时**复用而不重写**（正在用的 dll 被系统锁着，重写必红在写那一跳）。资源读法是**缝**：真跑走包内资源，用例递一份假的进来验"取不到必须响" |
| `ensure(root)` | 同一棵 root 一次 JVM 只落一份（按 root 记 memo）。第二次又叫不许换落点 |
| `reading(placed)` | 那句 `D_原生库 档=… 落点=… 字节=…`，三格**全从落点现取** |

接线一处：`JdbcSqliteDatabase` 的 `connection` 初始化里，`ensure(nativeRoot)` 排在 `DriverManager.getConnection(` **之前**，`nativeRoot` 就是库文件自己的父目录（读写与只读两条路共用同一个入口，所以只此一处；父目录取不到就抛，不静默换个落点）。

三条刻意不做的：
- **不做"先让驱动自己试、失败再兜底"** —— 两条路会漂成"只有开发跑能读库"，那正是这一族的成因；今天无条件自己落。
- **不把落点写死成 `java.io.tmpdir`** —— §1.1 那一格就是这一版红出来的。
- **不把 dll 作为文件随包**（那是候选②）—— 它要按 `os.arch` 在打包期选档，而打包期我们手上只有构建机那一档；资源里那份才是本机那一档。

## 3. 尺子（先 RED 后 GREEN）

`desktop/src/test/kotlin/com/venera/desktop/platform/DesktopSqliteNativeTest.kt` **6 条**：
① 档位算式（系统与架构各喂两种写法 + 两枚认不出的档交回 null）/ ② 本机这一档算得出来（否则 ③④ 在测空气）/
③ 真落盘：文件在、字节>0、头两字节是 `MZ` 或 ELF、那两枚属性确实指过去 / ④ 取不到时抛错并**点名那一条资源** /
⑤ 接线顺序：`ensure()` 那一跳的源码位置排在 `DriverManager.getConnection(` 之前 / ⑥ 读数从落点现取 + 一次 JVM 只落一份。

- **RED**（`_qa/b829b_red.log`）：编译面就红 —— `Unresolved reference 'DesktopSqliteNative'`（12 处，那颗件还不存在）。
- **GREEN 第一版**（`_qa/b829b_green.log`）：`tests="6" failures="0" errors="0"`，`BUILD SUCCESSFUL`（落点是 `Files.createTempDirectory` 那一版）。
- 包内量到 §1.1 那第二格之后改成"落点由调用点递进来"，复跑仍 `6/0`（`_qa/b829b_green2.log`）；台架 P7 跑出绿之后 ⑥ 换成 `assertSame`，再复跑仍 `6/0`（`_qa/b829b_green3.log`）。

## 4. 台架（9 笔，`_qa/b829b_perturb.js`）

| 笔 | 改坏哪一处 | 结果 |
|---|---|---|
| P1 | `amd64/x64/x86_64` 算成 `aarch64` | ① 红（`ComparisonFailure`）＋**连坐 126 条**（整层读不到库） |
| P2 | Windows 档名带上 `lib` 前缀 | ① ③ ⑥ 红 ＋ 连坐 126 条 |
| P3 | 取不到交空字节不响 | **只**红 ④ |
| P4 | 不指 `org.sqlite.lib.path` | ③ 红 ＋ 连坐 126 条 |
| P5 | 那一跳整个不接 | **只**红 ⑤ |
| P6 | 读数把字节写死 | **只**红 ⑥ |
| P7 | memo 的键每次变（每次都重落） | **第一次跑出绿** ⇒ 见下 |
| P8 | 落点回到 `java.io.tmpdir`（跑到 root 外面） | **只**红 ③ |
| P9 | 等价改写（去前缀换一种写法） | `BAD 一笔都没红` = **要求不红，正确** |

⚠️ **P7 那一笔换来的是一条判据缺口**：⑥ 从前比的是 `first.dir == second.dir`，而落点目录由 `root` **确定**算出
（`root/sqlite-native/`）⇒ "又走了一遍落盘"路径照样不变，判式看不见它名字里那件事（坑 ㉔ 那一族：
**判式只吃看得见的东西**）。正解不是给它造一枚只给测试看的计数器（那会被"零调用点 internal 件"那把尺子挡回来，
而且是为性能性质造 API），而是比**是不是同一份** —— `assertSame(first, second)`。改完复跑 P7：
`RED …读数念的是落点而同一棵 root 不重落 | AssertionError: 第二次又叫 ensure 时又走了一遍落盘` ✓，用例名也跟着改成它真测的那件事。

**连坐那 126 条不是误伤**：P1/P2/P4 挡住的正是"取到原生库"这一跳，而全仓吃 SQLite 的屏都排在这跳之后 ——
那串红单本身就是"取不到原生库 = 全线读不到库"的形状，与包内那两跑（`行=0` 而盘上 3 行）说的是同一件事。

还原后全量：`EXIT=0`、`suites=132 tests_sum=1025 testcase=1025 @Test=1025 failures=0`（三方口径一致）。

## 5. 交付物那一格（决定性）

判据全绿只证明开发跑能读库；这一批的正题是**包内**。跑法：`createDistributable --rerun-tasks` → 拷到
`_qa/desktop-exe-2026-10-10-b829b/`（**不带**批 82.9 §4.2 那两行手工 cfg —— 那份 dll 也不许出现在包里，
否则"修好了"其实是手工喂的）→ `check_cfg.js` + `check_pkg_jar_sha.js` → 包内 exe 跑
`--start-domain=COMIC --start-section=comic-history`，隔离库 `_qa/b828_local`（三行 `probe_*` 夹具）。

期望：`D_原生库 档=/org/sqlite/native/Windows/x86_64/sqlitejdbc.dll 落点=… 字节=1014784`、
`D_历史 屏 源=jm 名=禁漫天堂 行=3`、全日志 `D_异常` 计数 **0**、帧上三行夹具齐且「夹具乙·手机写法」不挂那句说明。

- **实测（达成）**：`_qa/b829b_packaged_run2.log`，起手窗数 0，跑完 `taskkill` 后 `residue=0`。
  包里三处复核：`classpath=100 missing=0`、包内那颗 jar 与 `desktop/build/libs/desktop.jar` **字节逐字相同**、
  而"手工喂的东西"两处都量到 **0**（`app/` 下 `sqlitejdbc*` 文件 0 颗、cfg 里 `org.sqlite` 0 处）
  ⇒ 这一格是代码修好的，不是批 82.9 §4.2 那次手工补出来的。包内 stdout：

  ```
  D_数据目录 D:\venera-compose\_qa\b828_local\venera（写入回读已验）
  D_原生库 档=/org/sqlite/native/Windows/x86_64/sqlitejdbc.dll 落点=D:\venera-compose\_qa\b828_local\venera\db\sqlite-native 字节=1014784
  D_历史 屏 源=jm 名=禁漫天堂 行=0     ← 首帧（IO 那笔还没回）
  D_历史 屏 源=jm 名=禁漫天堂 行=3
  （全日志 D_异常 计数 = 0）
  ```

  帧（PID 5136，`relatedTransientWindows` 空）：三行夹具都在 —— 夹具丙（`goda`）挂着「这一行是别的源记的，去源管理换过来才打得开」，
  夹具乙（`禁漫天堂`，手机写法）与夹具甲（`jm`，桌面写法）都不挂 ⇒ **批 82.8 那一格在交付物里成立，且不再依赖手工喂**。
- **顺带把 §1 那句"我们这一侧的包内资源读法在包里量过能用"从旁证升成正证**：`字节=1014784` 与 jar 里那条目的大小**逐字相同**，
  而它是包内进程读出来的 ⇒ 断点确实在**驱动那一侧的取法**（`SQLiteJDBCLoader` 用它自己的类去取），不在这台 JVM 的资源通道。
  这一条把"为什么包内 null"的候选面收窄到"哪一类调用取不到"，**仍未定论**（不写成已查明）。
