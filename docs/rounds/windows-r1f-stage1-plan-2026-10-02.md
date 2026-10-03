# R1-F 阶段 1「桌面地基」实施计划

> **给执行方的 agent 说明：** 建议用 `superpowers:subagent-driven-development`（逐任务派新血 + 任务间评审）或 `superpowers:executing-plans`（本会话内分批执行 + 检查点）按任务实施。下面的步骤用 `- [ ]` 复选框标记进度。
> 上游判据与既有读数：`windows-r1f-s0-spike-2026-10-02.md` 第十三节（本计划的事实来源，改动面数字都在那里）+ `windows-port-feasibility-2026-10.md` 第七节。

**Goal:** 让桌面端具备"存得下、读得回"的地基 —— 路径、键值、SQLite 三层各有一个双端共用的门面，14 个吃 Android 持久化类型的文件不再 import `android.*`，收藏与历史在桌面进程重启后仍在。

**Architecture:** 三层面门（`PathProvider` / `KeyValueStore` / `SqlDatabase`）放在 `:app` 的新包 `data/platform/` 里，Android 实现留在 `:app`，桌面实现放 `:desktop`；`:desktop` 沿用本仓库已有的 **srcDir 共享法**（`engine-probe/build.gradle.kts:16` 就是把它指到 `../app/src/main/java/...`），不引入 KMP 源集重构。SQL 语句原样保留，只把执行通道换成 JDBC。

**Tech Stack:** Kotlin 2.4.10 / `jvmToolchain(21)`、Compose Multiplatform Desktop 1.12.1、`org.xerial:sqlite-jdbc:3.53.4.0`（已在 Maven Central 核到该版本，直连经代理可达）、JUnit 4.13.2（仓库现有钉版 `gradle/libs.versions.toml:4`）。

**Spec:** `docs/rounds/windows-r1f-s0-spike-2026-10-02.md` §七末（签名/拦截）+ §十三（地基盘点）。

---

## 待拍板的 6 条（逐条定完再开工；每条都给了我的建议）

| # | 决策 | 我的建议 | 定了之后影响 |
|---|---|---|---|
| D1 | 门面放 `:app` 新包还是新建 `:core` 模块 | **`:app` 的 `data/platform/`** | 不动模块图，`:desktop` 只加 srcDir |
| D2 | 桌面键值存储格式 | **一个 prefs 名 = 一个 JSON 文件**（保持 13 个名字的边界，便于与 Android 侧对照） | `JsonKeyValueStore` 的文件布局 |
| D3 | 桌面 SQLite 驱动 | **`org.xerial:sqlite-jdbc:3.53.4.0`**（纯 JVM、自带 native、零安装） | Task 3 的实现与打包体积 |
| D4 | 收藏夹动态表名（用户输入直拼）怎么办 | **改白名单 + 反引号转义**，Android 侧同改 | Task 3 的安全测试用例；这是修 bug 不是重构 |
| D5 | Android 已有数据要不要自动搬到桌面 | **不做**（桌面当全新数据，跨端只走既有 WebDAV/备份通道） | 省一整块迁移代码；代价是老用户桌面首启是空的 |
| D6 | 产物签名 | 本机**没有可路**：jpackage（23 与 25 都核过）无签名选项、`signtool` 与 Windows Kits 目录均不存在、`Set-AuthenticodeSignature` 对 `venera-desktop-probe.exe` 报 `%1 不是有效的 Win32 应用程序` ⇒ 需要你装 Windows SDK（或换台机器签） | Task 6 的开工前提 |

## Global Constraints（每条任务都隐含吃下）

- 回复与注释一律中文；界面文案不带 AI 味（无星号/反引号/内部黑话/实现笔记）。
- **降级路径宁可错慢不可静默交错**：拿不到数据目录、驱动装不上、表名不合法 ⇒ 抛错并说清，绝不退回临时目录、绝不静默少存。
- **不新增假开关**：每个"开关"都要有真实读写路径与判据读数。
- 数字先回代码核对再用；本计划里的 14 文件 / 19 命中 / 13 个 prefs 名都是我复跑 Grep 得到的，改动后要回写 §十三。
- exe / zip / apk 产物只进 GitHub Releases，不进仓库。
- 不提交、不 push，除非用户明确说。
- 保护域（`:app` 的 UI 层与 material3 钉版）不动；本阶段只碰持久层与 `:desktop`。

## 完成判据（DoD）

| 判据 | 怎么量 |
|---|---|
| Android 持久化类型清零（口径已按 R31 收窄） | **12 颗已脱**（`data/db` 六颗 + `BackupManager`/`ForeignArchiveImport`/`ReadingStatsManager`/`ContentGuardManager`/`FavoriteImagesManager`/`GalleryTagDictionary`），Grep `android.database.`、`ContentValues`、`getSharedPreferences` 在这 12 颗里只剩 `data/platform/android/` 的实现命中。**2 颗豁免**：`GallerySaver`、`VeneraReaderScreen` 的存相册分支 —— 它们的 `ContentValues` 只喂 `MediaStore`（平台服务，不是持久层）。**另 2 颗属 `androidx.work` 的追更组件**（`FollowUpdatesRepository`/`FollowUpdatesWorker`），桌面没有 WorkManager 对位库，排在阶段 2 的"后台追更"决策里，不在本阶段判据内 |
| 桌面进程重启后收藏仍在 | 跑两次 `:desktop:app`：第一次点收藏写入，第二次启动即读回并打印 `D_收藏 命中=N` |
| 动态表名不可注入 | `:desktop:test` 里那 5 条恶意表名用例全绿（含反引号、空格、中文、`"; DROP`） |
| Android 侧不回归 | 真机：收藏增删、历史记录、备份导出各一遍（由用户点，我只读日志） |
| 单测真在 JVM 跑 | `./gradlew :desktop:test` 产出 `desktop/build/test-results/test/*.xml`，且 `tests="…"` 计数不为 0 |

## File Structure（新增 = 8，修改 = 4 + 14 个调用点文件）

```
app/src/main/java/com/venera/compose/data/platform/
  PathProvider.kt            # 接口 + 校验（新增）
  KeyValueStore.kt           # 接口 + 通用类型化读写扩展（新增）
  JsonKeyValueStore.kt       # JVM 实现：一 prefs 一 JSON，原子替换（新增）
  SqlDatabase.kt             # 接口 + SqlRow + 标识符转义（新增）
  android/AndroidPaths.kt    # Context → File（新增）
  android/AndroidKeyValueStore.kt  # SharedPreferences 适配（新增）
  android/AndroidSqlDatabase.kt    # SQLiteDatabase 适配（新增）
desktop/src/main/kotlin/com/venera/desktop/platform/DesktopPaths.kt   # 新增（LOCALAPPDATA，拿不到就抛）
```
> Android-only 的实现放 `android/` 子包，`:desktop` 的 srcDir **只指 `data/platform/` 根、不指 `android/`** —— 靠目录边界避开 expect/actual。

---

### Task 1: `PathProvider` —— 数据目录只有一条来源

**Files:**
- Create: `app/src/main/java/com/venera/compose/data/platform/PathProvider.kt`
- Create: `app/src/main/java/com/venera/compose/data/platform/android/AndroidPaths.kt`
- Create: `desktop/src/main/kotlin/com/venera/desktop/platform/DesktopPaths.kt`
- Modify: `desktop/src/main/kotlin/com/venera/desktop/VeneraDesktop.kt:67-83`（删掉本地的 `localDataDir()`，改用 `DesktopPaths`）
- Modify: `desktop/build.gradle.kts`（加 `testImplementation`，见 Step 4）

**Interfaces:**
- Produces: `interface PathProvider { val dataRoot: File; val cacheRoot: File; fun subDir(vararg parts: String): File }`；`DesktopPaths.create(): PathProvider`（JVM）；`AndroidPaths(context: Context): PathProvider`。

- [ ] **Step 1：先写失败的测试**（放 `desktop/src/test/kotlin/com/venera/desktop/platform/DesktopPathsTest.kt`）

```kotlin
class DesktopPathsTest {
    @Test fun `LOCALAPPDATA 缺失时抛错而不是退回临时目录`() {
        // DesktopPaths 收 env 提供者，测试里喂空 —— 不为可测性引入全局可变状态
        val err = runCatching { DesktopPaths.create { null } }.exceptionOrNull()
        assertNotNull(err)
        assertTrue(err!!.message!!.contains("LOCALAPPDATA"))
    }

    @Test fun `目录建得出且文件写得进读得出`() {
        val root = File(System.getProperty("java.io.tmpdir"), "r1f-paths-${System.nanoTime()}")
        val paths = DesktopPaths.create { root.path }
        val f = paths.subDir("comic_source").resolve("probe.txt")
        f.parentFile.mkdirs()
        f.writeText("ok")
        assertEquals("ok", f.readText())
        root.deleteRecursively()
    }
}
```

测试里还要一个"把根指到临时目录"的假实现，后续任务的 JVM 用例共用它（放 `desktop/src/test/kotlin/com/venera/desktop/platform/FakePaths.kt`）：

```kotlin
/** 只给测试用：dataRoot/cacheRoot 都指到调用方给的临时目录，语义与 DesktopPaths 同构。 */
class FakePaths(override val dataRoot: File, override val cacheRoot: File = File(dataRoot, "cache")) : PathProvider
```

- [ ] **Step 2：跑测试确认它失败**

Run: `cd /d/venera-compose && ./gradlew :desktop:test --tests "*DesktopPathsTest*"`
Expected: 编译失败（`Unresolved reference: DesktopPaths`）—— 这就是"先看见它坏"

- [ ] **Step 3：写实现**

```kotlin
// app/.../data/platform/PathProvider.kt
package com.venera.compose.data.platform

import java.io.File

/** 所有落盘路径的唯一来源。桌面侧不许出现 `java.io.tmpdir`，Android 侧不许出现硬编码 `/storage/`。 */
interface PathProvider {
    val dataRoot: File
    val cacheRoot: File
    fun subDir(vararg parts: String): File {
        val dir = parts.fold(dataRoot) { acc, p ->
            require(p.isNotBlank() && p != ".." && !p.contains('/') && !p.contains('\\')) { "路径段不合法：$p" }
            File(acc, p)
        }
        if (!dir.mkdirs() && !dir.isDirectory) throw IllegalStateException("建不出目录 $dir")
        return dir
    }
}
```

```kotlin
// desktop/src/main/kotlin/com/venera/desktop/platform/DesktopPaths.kt
class DesktopPaths private constructor(override val dataRoot: File, override val cacheRoot: File) : PathProvider {
    companion object {
        /** envProvider 可注入：判据是"拿不到 LOCALAPPDATA 就抛"，那就必须能在测试里造出拿不到的情形。 */
        fun create(envProvider: (String) -> String? = System::getenv): PathProvider {
            val base = envProvider("LOCALAPPDATA")?.takeIf { it.isNotBlank() }
                ?: throw IllegalStateException("环境里没有 LOCALAPPDATA，桌面数据目录无从谈起")
            val root = File(base, "venera")
            val cache = File(root, "cache")
            if (!root.mkdirs() && !root.isDirectory) throw IllegalStateException("建不出目录 $root")
            val probe = File(root, ".write-probe")
            probe.writeText("ok")
            if (probe.readText() != "ok") throw IllegalStateException("$root 写得进却读不出")
            probe.delete()
            return DesktopPaths(root, cache)
        }
    }
}
```

- [ ] **Step 4：让 `:desktop` 有测试栈**（`desktop/build.gradle.kts` 末尾加）

```kotlin
dependencies {
    testImplementation(libs.junit)
}
tasks.withType<Test> { useJUnit { } }
```
Run: `./gradlew :desktop:test` → Expected: 上面两条用例 PASS

- [ ] **Step 5：`VeneraDesktop.kt` 换用它**（删 `localDataDir()`，main 里 `val paths = DesktopPaths.create()`，把 `dataDir` 全部替换为 `paths`）
Run: `./gradlew :desktop:app -Pkey=jm -Pproxy=127.0.0.1:7890 -Pshot=_qa/t1-jm.png` → 日志仍出现 `D_数据目录 C:\Users\...\AppData\Local\venera`
- [ ] **Step 6：提交** `git add app/src/main/java/com/venera/compose/data/platform desktop && git commit -m "feat(desktop): 数据目录收进 PathProvider，拿不到 LOCALAPPDATA 即抛"`

> ⚠️ 打包 exe 在本机仍会因写入被拦而抛（见 §七末"追加更正"）—— 那是**环境闸门**，不是本任务的失败；gradle 侧读数是本任务的验收面。

---

### Task 2: `KeyValueStore` —— 13 处旁路收口成一层

**Files:**
- Create: `data/platform/KeyValueStore.kt`、`data/platform/JsonKeyValueStore.kt`、`data/platform/android/AndroidKeyValueStore.kt`
- Modify: `data/prefs/VeneraPreferences.kt:59-62`（构造参数 `Context` → `KeyValueStore`）+ 其余 13 个调用点（清单见 §十三 第 3 条）

**Interfaces:**
- Consumes: Task 1 的 `PathProvider.subDir("prefs")`
- Produces: `interface KeyValueStore { fun getString(k:String,d:String?):String?; fun getBoolean(k:String,d:Boolean):Boolean; fun getInt(k:String,d:Int):Int; fun getLong(k:String,d:Long):Long; fun getFloat(k:String,d:Float):Float; fun getStringSet(k:String):Set<String>; fun put(k:String,v:Any?); fun remove(k:String); fun clear(); fun keys():Set<String> }`；`JsonKeyValueStore(name:String, paths:PathProvider)`。

- [ ] **Step 1：失败测试**（`desktop/src/test/kotlin/.../JsonKeyValueStoreTest.kt`）

```kotlin
class JsonKeyValueStoreTest {
    private fun store(dir: File, name: String = "venera_preferences") =
        JsonKeyValueStore(name, FakePaths(dir))

    @Test fun `八种类型 round-trip`() {
        val dir = File(System.getProperty("java.io.tmpdir"), "r1f-kv-${System.nanoTime()}")
        val s = store(dir)
        s.put("s", "中文 值"); s.put("b", true); s.put("i", 42); s.put("l", 9_000_000_000L)
        s.put("f", 1.5f); s.put("set", setOf("a", "b"))
        val r = store(dir)   // 新实例 = 必须从磁盘重建
        assertEquals("中文 值", r.getString("s", null))
        assertTrue(r.getBoolean("b", false)); assertEquals(42, r.getInt("i", 0))
        assertEquals(9_000_000_000L, r.getLong("l", 0)); assertEquals(1.5f, r.getFloat("f", 0f))
        assertEquals(setOf("a", "b"), r.getStringSet("set"))
        dir.deleteRecursively()
    }

    @Test fun `删掉最后一个键后文件还在且该键取到默认值`() {
        val dir = File(System.getProperty("java.io.tmpdir"), "r1f-kv2-${System.nanoTime()}")
        store(dir).put("x", 1); store(dir).remove("x")
        assertEquals(7, store(dir).getInt("x", 7))
        dir.deleteRecursively()
    }

    @Test fun `半截 JSON 不许静默当成空表 —— 必须抛`() {
        val dir = File(System.getProperty("java.io.tmpdir"), "r1f-kv3-${System.nanoTime()}")
        store(dir).put("x", 1)
        File(dir, "prefs/venera_preferences.json").writeText("{\"x\": 1")   // 写坏
        assertTrue(runCatching { store(dir).getInt("x", 0) }.isFailure)
        dir.deleteRecursively()
    }
}
```

- [ ] **Step 2：Run** `./gradlew :desktop:test --tests "*JsonKeyValueStoreTest*"` → Expected: FAIL（类不存在）
- [ ] **Step 3：实现**：`JsonKeyValueStore` 用 `kotlinx-serialization` 的 `JsonElement` 树（`LinkedHashMap<String, JsonElement>`）落盘；写 = 先 `name.json.tmp` 再 `File.renameTo`（同目录原子替换，Windows 上改名前先删旧文件）；读 = 文件不存在给空表，**存在但解析失败 ⇒ 抛，并在消息里带上文件路径**（不许回退成"没配置"）。
- [ ] **Step 4：跑绿** `./gradlew :desktop:test` → PASS
- [ ] **Step 5：Android 适配 + 13 处替换**（机械规则：`context.getSharedPreferences(N,M).edit { putX(k,v) }` → `KeyValueStoreFactory(N).put(k, v)`，**同一批原先在一个 `edit{}` 里的**改 `putAll(map)` 保住一次事务；用 `Grep "getSharedPreferences" app/src/main` 复跑枚举，逐点改）；`VeneraPreferences` 的 `StateFlow` 镜像层不动，只换底下的存取。
  > R10 修正：本行原写作 `store(N).putX(...)`，与本任务 Interfaces 栏的 `put(k, v: Any?)` 冲突；以实现与 Interfaces 栏为准（`put`/`putAll` 两个写入口，六种类型以外当场抛）。
Run: `./gradlew :app:assembleDebug` → Expected: 编译通过（Android 侧行为等价）
- [ ] **Step 6：提交** `git commit -am "refactor(prefs): KeyValueStore 门面收口 13 处旁路，桌面走 JSON 实现"`

---

### Task 3: `SqlDatabase` —— JDBC 通道 + 动态表名转义（含修注入）

**Files:**
- Create: `data/platform/SqlDatabase.kt`（接口 + `SqlRow` + `quoteIdentifier`）、`data/platform/android/AndroidSqlDatabase.kt`、`desktop/.../platform/JdbcSqliteDatabase.kt`
- Modify: `data/db/VeneraDatabase.kt`（v2 的 `DROP` 重建语义要照搬：`VeneraDatabase.kt:130-135`）、`data/db/LocalFavoriteDatabase.kt:63-124`
- Modify: `desktop/build.gradle.kts`（`implementation("org.xerial:sqlite-jdbc:3.53.4.0")`）

**Interfaces:**
- Consumes: `PathProvider.subDir("db")`
- Produces: `interface SqlDatabase { fun exec(sql:String, vararg args:Any?); fun query(sql:String, vararg args:Any?):List<SqlRow>; fun inTransaction(block:()->Unit); fun close() }`、`fun quoteIdentifier(raw:String):String`，以及 `interface SqlRow { fun isNull(name:String):Boolean; fun typeOf(name:String):SqlType; fun string(name:String):String?; fun long(name:String):Long; fun double(name:String):Double; fun bytes(name:String):ByteArray? }`（`SqlType` = `INTEGER/FLOAT/TEXT/BLOB/NULL`）—— Task 4 里 `BackupManager` 判类型要吃的就是 `typeOf`。

- [ ] **Step 1：失败测试**（含 5 条注入样本）

```kotlin
class QuoteIdentifierTest {
    @Test fun `反引号必须被翻倍而不是被当结束符`() {
        assertEquals("`fav``s`", quoteIdentifier("fav`s"))
    }
    @Test fun `正常中文名与空格照收`() { assertEquals("`我的 收藏`", quoteIdentifier("我的 收藏")) }
    @Test fun `空串与换行直接拒`() {
        assertTrue(runCatching { quoteIdentifier("") }.isFailure)
        assertTrue(runCatching { quoteIdentifier("a\nb") }.isFailure)
    }
    @Test fun `注入串只能变成表名本体`() {
        val evil = "x`; DROP TABLE comic_favorite; --"
        val quoted = quoteIdentifier(evil)
        assertTrue(quoted.startsWith("`") && quoted.endsWith("`"))
        assertFalse(quoted.drop(1).dropLast(1).contains(";"))   // 里面的分号已被反引号包住
    }
}
```
另建 `JdbcSqliteDatabaseTest`：在临时 `.db` 上跑 `VeneraDatabase` 的 6 张建表 SQL、插入历史行再 `query` 回读、`inTransaction` 中途抛 ⇒ 断言回滚。

- [ ] **Step 2：Run** → FAIL（类不存在）
- [ ] **Step 3：实现 `quoteIdentifier`**

```kotlin
/** 表名来自用户输入（一个收藏夹一张表），只能走标识符转义；SQL 值一律走参数绑定。 */
fun quoteIdentifier(raw: String): String {
    require(raw.isNotBlank()) { "表名不能为空" }
    require(raw.none { it == '\n' || it == '\r' }) { "表名不能含换行" }
    return "`" + raw.replace("`", "``") + "`"
}
```

- [ ] **Step 4：实现 `JdbcSqliteDatabase`**（`DriverManager.getConnection("jdbc:sqlite:${file.path}")`；`exec`/`query` 用 `PreparedStatement`，`SqlRow` 由 `ResultSetMetaData` + `getString/getLong/...` 包一层；`inTransaction` = `autoCommit=false` + `try commit / catch rollback + throw`）
- [ ] **Step 5：跑绿** `./gradlew :desktop:test` → PASS；同时 `./gradlew :app:assembleDebug` 不坏
- [ ] **Step 6：提交** `git commit -am "feat(db): SqlDatabase 门面（JDBC/Android 双实现）+ 收藏夹表名转义"`

---

### Task 4: 14 个文件去掉 `android.*` 持久化 import

**Files（全清单，来自我复跑的 Grep）：** `data/db/{VeneraDatabase,LocalFavoriteDatabase,FavoriteDao,HistoryDao,ComicSourceDao}.kt`、`data/db/LocalFavoritesManager.kt`、`data/prefs/*`（Task 2 已改）、`favoriteimages/FavoriteImagesManager.kt`、`stats/ReadingStatsManager.kt`、`security/guard/ContentGuardManager.kt`、`sync/BackupManager.kt`、`sync/ForeignArchiveImport.kt`、`gallery/data/{GalleryTagDictionary,GallerySaver}.kt`、`reader/VeneraReaderScreen.kt`

**Interfaces:**
- Consumes: Task 2 的 `KeyValueStore`、Task 3 的 `SqlDatabase`/`SqlRow`/`quoteIdentifier`
- Produces: 上述类型不再有 `android.content.ContentValues` / `android.database.*`；`ContentValues` 的等价物是 `Map<String, Any?>` + `bind` 参数

- [ ] **Step 1：枚举改动面并记账**（数字要落进 §十三，别凭印象）
Run: `Grep "android\.database\.|ContentValues" app/src/main/**/*.kt` → 记录文件数，改完再跑一次取零
- [ ] **Step 2：逐文件机械替换**（DAO 优先，`BackupManager` 这种整块语义最后做：它用 `Cursor.FIELD_TYPE_*` 判类型（`BackupManager.kt:395-398`）⇒ 换成 `SqlRow.typeOf(name)`，`SqlRow` 里加这个成员，别在调用侧猜）
- [ ] **Step 3：`org.json` 的处置**：`BackupManager`/`DownloadManager`/`LocalComicManager`/`ForeignArchiveImport`/`ContentGuardManager`/`MangaDexSource` 吃 `org.json.*`（Android framework 内置）。**决策：全换成仓库已有的 gson**（`libs.gson`），不新增 `org.json:json` 依赖 —— 多一颗依赖就多一处打包面。
- [ ] **Step 4：`StartupTrace` 顺手验一遍**：`VeneraPreferences.kt:61` 调它，若它 import `android.os.*` ⇒ 在 `data/platform/` 加一个 `MonotonicClock` 常量替换，别把 Android 计时器拖进共享包。
- [ ] **Step 5：Android 侧回归**（`./gradlew :app:assembleDebug` + 真机点收藏/历史/备份 —— 真机由用户点，我只读 logcat）
- [ ] **Step 6：提交** `git commit -am "refactor(db): 持久层 14 文件脱离 android.* 类型"`

---

### Task 5: `:desktop` 接上共享源 + 收藏这颗心真跳

**Files:**
- Modify: `desktop/build.gradle.kts`（`kotlin.sourceSets["main"].kotlin { srcDir("../app/src/main/java/com/venera/compose/data/db"); exclude("**/android/**") }` 之类，粒度按编译报错收紧）
- Modify: `desktop/src/main/kotlin/com/venera/desktop/VeneraDesktop.kt`（卡片上加热心按钮 → `FavoriteDao`；顶栏加"收藏"分段，列 `dataRoot` 里真存的东西）

**Interfaces:** Consumes Task 1-4 全部；Produces `D_收藏 命中=N` 这行日志（阶段 2 的验收锚点）

- [ ] **Step 1** 加 srcDir，编译，按报错逐条决定"补共享"还是"这一层留 Android"（判据：**持久层不许依赖 UI**）
- [ ] **Step 2** 在 `:desktop:test` 里加"两进程"用例的进程内版：写一颗收藏 → `close()` → 新开 `JdbcSqliteDatabase` → 读回同一颗
- [ ] **Step 3** UI 接线（compose-fluent 的按钮 + FluentTheme，不引 material3）
- [ ] **Step 4** 无人值守读数：`./gradlew :desktop:app -Pkey=jm -Pproxy=127.0.0.1:7890 -Pshot=_qa/t5-fav.png` 跑两次，第二次日志须见 `D_收藏 命中=1`（解锁状态下才有图证）
- [ ] **Step 5** 提交

---

### Task 6: 产物侧两件事 —— assets 随包 + 签名闸门

**Files:** Modify `desktop/build.gradle.kts`（`runtime` 资源目录 + `VeneraDesktop` 的 `assetDir` 改为"包内优先、仓库目录兜底"并打印用的哪一个）

- [ ] **Step 1** `sourceSets["main"].resources { srcDir("../app/src/main/assets") }`，装载处先 `javaClass.getResourceAsStream("/sources/…")`，取不到再退回仓库目录；两条路都要打印走了哪条（不许静默）
- [ ] **Step 2** `./gradlew :desktop:createDistributable` 后**删掉仓库目录名改个临时名**再跑 exe —— 若仍出封面，才叫随包分发成功（这条是防"其实还是从仓库读的"自欺）
- [ ] **Step 3** 签名：D6 的环境闸门在你侧（装 Windows SDK 拿 `signtool`，或换机器签）。**并且先确认写拦截到底是不是火绒**：请你查火绒"拦截日志"里有没有 `venera-desktop-probe.exe` 创建文件的记录。若签名后 exe 仍写不进 `%LOCALAPPDATA%` ⇒ 签名不是成因，代码侧保持"启动即抛"，改走白名单。

---

## 自查（本计划对 §十三 的覆盖）

| §十三 缺口 | 落在 |
|---|---|
| 1-2 手写 SQLite、14 文件 | Task 3 + Task 4 |
| 3 键值 19 命中 / 14 文件 | Task 2 |
| 4 `org.json` | Task 4 Step 3 |
| 5 路径面约 25 文件 | Task 1（门面）+ Task 4（收口）；**未覆盖：下载目录与相册语义** ⇒ 明写进阶段 2，不假装做完 |
| 6 依赖判定 | Task 3 钉 sqlite-jdbc；coil3 桌面变体**不在本阶段**（S0-8 已给出 skia 直读的替代路径） |
| 7 WorkManager 追更 | **本阶段不做**（桌面先不做后台追更，避免"假开关"） |
| 8 MediaStore 相册 | 阶段 2 |
