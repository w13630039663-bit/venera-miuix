# 批 82.9 · 交付包里读库整条路是死的（jlink 运行时缺 `java.sql`；顺带抓出第二笔 82.9b）2026-10-10

这一批不是新功能，是**批 82.8 收尾时"交付包同步"那一格抓出来的缺陷**。它同时给这条线立了一条新口径：
**判据全绿 + 开发跑起窗正常，都不等于交付物能用**。

## 1. 读数（包内 exe，隔离数据根 `_qa/b828_local`）

跑法：`_qa/desktop-exe-2026-10-10-b828/venera-desktop-probe/venera-desktop-probe.exe --start-domain=COMIC --start-section=comic-history`
（日志是 GBK，读前先 `iconv -f GBK`；这条口径来自批 80 那笔"UTF-8 模式串去 grep GBK 文件会假空"）

```
D_环境 jdk=23.0.2 tmp=C:\Users\leimi\AppData\Local\Temp\ 打包=true
D_数据目录 D:\venera-compose\_qa\b828_local\venera（写入回读已验）
D_接线 平台=desktop core=…\venera\db\venera_core.db 已建=true fav=…\local_favorite.db 已建=false
D_落点 域=漫画(启动) 行=阅读历史 浮层=关(换域或换页自动收) 查看器=关 详情=关(启动) 阅读=关(启动) 种子=无
D_源 用=jm
D_历史 屏 源=jm 名=禁漫天堂 行=0        ← 盘上明明有 3 行
D_异常 线程=DefaultDispatcher-worker-1 次=1 因=NoClassDefFoundError: java/sql/SQLException 链=1 栈=DesktopDatabasePorts.kt:200
java.lang.NoClassDefFoundError: java/sql/SQLException
	at com.venera.desktop.platform.DesktopDatabasePorts$DesktopDatabaseSource.obtain(DesktopDatabasePorts.kt:200)
	at com.venera.desktop.platform.DesktopDatabasePorts$DesktopDatabaseSource.reader(DesktopDatabasePorts.kt:207)
	at com.venera.compose.data.db.HistoryDao.getAllHistorySync(HistoryDao.kt:53)
	at com.venera.compose.data.db.HistoryDao$refresh$1.invokeSuspend(HistoryDao.kt:49)
```

盘上那份是裁判（`node:sqlite` 只读打开同一棵库）：`SELECT count(*) FROM comic_history` → **3**。
帧上：pane 停在「阅读历史」而内容区是空态那两句「还没有阅读记录 / 去随便翻两页吧」——
**它没崩，可它也没读到东西**，而且屏上那句话是句假话（库里有三行）。`relatedTransientWindows` 空。

## 2. 为什么只有包里坏

`desktop/build.gradle.kts:236` 那串 `modules("jdk.accessibility", "jdk.unsupported")` 是 **jlink 的模块清单**。
sqlite-jdbc 没有 `module-info`，是**自动模块** ⇒ jlink 的依赖推导看不见它对 `java.sql` 的需要，
打出来的运行时里没有 `java.sql.SQLException` 这一枚类；`DesktopDatabasePorts.kt:200` 那一跳（取连接）当场 `NoClassDefFoundError`。
开发跑（`:desktop:app`）用的是全 JDK（`jdk=21` adoptium），`java.sql` 一直在 ⇒ **同一份代码两种命运**。

同一族的第一课是 `sun.misc.Unsafe`：缺 `jdk.unsupported` 时 Truffle 的 `NodeClassImpl` 启动即死，
也是**只有打包跑**才露出来（`docs/rounds/windows-r1f-s0-spike-2026-10-02.md` §七）。
⇒ 这条线从此定口径：**每批的"交付包同步"不是走过场**，它是唯一能照到这一类缺陷的那一层。

## 3. 修法与尺子（先 RED 后 GREEN）

- 尺子：`desktop/src/test/kotlin/com/venera/desktop/DesktopRuntimeModulesTest.kt` 三颗 ——
  ① 那行 `modules(` 里必须含 `"java.sql"`；② 原有两枚一颗不许掉（掉 `jdk.unsupported` = GraalJS 死，掉 `jdk.accessibility` = 读屏全哑）；
  ③ 钉**顺序**：那行必须住在 `nativeDistributions` 之后（跑到块外就不生效）。
  ⚠️ 判式只看"非注释那一行含 `modules(`"——否则口径注释里那句 `java.sql` 会自己把尺子喂饱（本仓那一族"名字在不在"的尺子咬自己）。
- **RED**（`_qa/b829_red.log`）：`tests="3" failures="1"`，红的正是 ① 那一条，报错文本就是本文件 §2 那句成因。
  ⇒ ② 当场绿说明这把尺子不是恒红（批 83 那一课的正面应用）。
- **GREEN**（`_qa/b829_green.log`）：`tests="3" failures="0"`。
- 改动只有一行：`modules("jdk.accessibility", "jdk.unsupported", "java.sql")`（`desktop/build.gradle.kts:236`，注释里写清两课的出处）。

## 4. 复验（决定性那一格）

带修重打（`:desktop:createDistributable --rerun-tasks`，`11 actionable tasks: 11 executed`）之后，
用**包内 exe** 跑同一串参数、同一棵隔离库：

- 期望：`D_历史 屏 源=jm 名=禁漫天堂 行=3` 且**没有** `D_异常` 那一行；帧上三行夹具都在，其中「夹具乙·手机写法」不挂那句说明（= 批 82.8 那一格在**交付物**里也成立）。
- 实测（`_qa/b829_packaged_run.log`，包 `_qa/desktop-exe-2026-10-10-b829/`）：**没达成**。`java.sql` 那一枚确实补上了 —— 报错从 `NoClassDefFoundError: java/sql/SQLException` 前进到 sqlite 自己的连接层（栈里出现 `java.sql/java.sql.DriverManager.getConnection`），
  但屏上还是同一形状：`D_历史 屏 源=jm 名=禁漫天堂 行=0`，而盘上 `count(*) FROM comic_history` = **3**：

  ```
  D_异常 线程=DefaultDispatcher-worker-1 次=1 因=IllegalStateException: 建不出 SQLite 连接：…\venera_core.db：Error opening connection 链=2 栈=JdbcSqliteDatabase.kt:51
  Caused by: java.sql.SQLException: Error opening connection
      at org.sqlite.SQLiteConnection.open(SQLiteConnection.java:264)
  Caused by: org.sqlite.NativeLibraryNotFoundException: No native library found for os.name=Windows, os.arch=x86_64, paths=[/org/sqlite/native/Windows/x86_64;…\venera-desktop-probe;C:\Windows\Sun\Java\bin;…]
      at org.sqlite.SQLiteJDBCLoader.loadSQLiteNativeLibrary(SQLiteJDBCLoader.java:365)
  ```

  ⇒ 这一格拆成**两笔**：82.9（缺 `java.sql`，已修已验）+ **82.9b（包内解不出 sqlite 原生库，未修）**。

### 4.1 82.9b 已量掉的候选（四条读数，别重量）

1. **不是包内 jar 被重写坏**：包内 `sqlite-jdbc-3.53.4.0-f4efbbf7….jar` 里 `org/sqlite/native/Windows/x86_64/sqlitejdbc.dll` 与 gradle 缓存那颗**字节逐字相同**（1,014,784 B，`MZ` 头，`cmp` 无差）。
2. **不是没挂上 classpath**：`check_cfg.js` → `classpath=100 missing=0 orphan_jars=0`；且 `org.sqlite.JDBC` 加载成功、`DriverManager` 靠那颗 jar 的 `META-INF/services/java.sql.Driver` 找到了它 ⇒ 那颗 jar 的**资源通道至少对 ServiceLoader 是通的**。
3. **不是写盘失败**：日志里没有 `Failed to write a native library file at %s` 那颗 `FileException`；且 `%TEMP%` 里 13:33 那一次**没有**新增 `sqlite-*-sqlitejdbc.dll`（同日 dev 跑 13:18 / 13:20 各留一对 dll+lck 为对照）⇒ 解出那一步根本没发生。
4. 三条合起来只指向一处：`LibraryLoaderUtil.hasNativeLib` 里那句 `Class.getResource("/org/sqlite/native/Windows/x86_64/sqlitejdbc.dll")` 在包内 JVM 交回 **null**（javap 读到的调用序列：`Class.getPackage().getName()` → `String.format("/%s/native/%s", …, OSInfo.getNativeLibFolderPathForCurrentOS())` → `Class.getResource` → 判 null）。
   ⚠️ **"为什么 dev 非 null、包内 null"本批没定论**，那是 82.9b 的题面；不许把它写成"已查明"。

### 4.2 双向判据：把原生库放到包旁

在**副本包**（`_qa/desktop-exe-2026-10-10-b829/`，不是源码）里抽一份 dll 到 `app/` 旁，cfg 尾部加两行
`java-options=-Dorg.sqlite.lib.path=$APPDIR` 与 `java-options=-Dorg.sqlite.lib.name=sqlitejdbc.dll`，同一串参数重起（`_qa/b829_libpath_probe.log`）：

```
D_历史 屏 源=jm 名=禁漫天堂 行=0     ← 首帧（IO 那笔还没回）
D_历史 屏 源=jm 名=禁漫天堂 行=3     ← 数据回来那一帧
（全日志 D_异常 计数 = 0）
```

帧上三行夹具都在：「夹具丙·别源」挂着那句「这一行是别的源记的，去源管理换过来才打得开」，
而「夹具乙·手机写法」（库里写的是显示名「禁漫天堂」）与「夹具甲·桌面写法」（写的是键 `jm`）**都不挂说明** = 两档写法在交付物里都按得动。`relatedTransientWindows` 空（没 Error 框）。

⇒ 结论分两半：**`System.load` 这条路在包里是通的**，断的只有"从 jar 里取原生库资源"那一格；
而 82.9 那一枚 `java.sql` 是**必要但不充分**——只加它，包仍然读不到库。

## 5. 连带账

- 这一格一修，包里那些**吃库**的屏一起活了：阅读历史、漫画收藏夹、画廊那份收藏、`venera_desktop_sources.json` 之外的 SQLite 侧。
  ⚠️ 但"哪些屏在包里此前是坏的"这句**没有逐屏量过**，只量到了历史那一屏 —— 别把它写成"全部验过"。
- 交付包名：`_qa/desktop-exe-2026-10-10-b828/`（缺 `java.sql` 的那一份）→ `_qa/desktop-exe-2026-10-10-b829/`（带 `java.sql`，**仍因 82.9b 读不到库**；§4.2 那两行 cfg 与那份 dll 只加在 `_qa` 的副本上，源码侧一行没动）。
- 旧包 `_qa/desktop-exe-2026-10-10-b80r/` 及更早那几份同一条病（它们的 `modules(` 里也没有 `java.sql`）⇒ 这批之后它们正式作废，删不删等点头。
- **下一批 = 批 82.9b**：包内 `Class.getResource` 取不到 sqlite 的原生库。修法候选两条，先量再选：
  ① 我们自己的类去读同一枚资源（`/sources/` 那 34 份在包里读得到 ⇒ 资源通道本身没坏），能读到就自己解到 `org.sqlite.tmpdir` 并显式指 `org.sqlite.lib.path`；
  ② 打包时把 dll 作为**文件**随包（`$APPDIR` 旁）+ `javaOptions("-Dorg.sqlite.lib.path=…")`。② 要按 `os.arch` 选档，别把 x86_64 那份发给 arm64。
