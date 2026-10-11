# 桌面大图查看器（图片播放器）实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development（推荐）或 superpowers:executing-plans 逐任务执行。步骤用 `- [ ]` 复选框跟踪。

**Goal:** 让桌面端「点一张卡」真的进到全屏大图那一屏，带滚轮缩放、平移、翻页与一层层退出。

**Architecture:** 新增一层满屏查看器（`DesktopGalleryViewer`）与一颗纯函数件（`DesktopGalleryViewerPolicy`）；缩放能力取现成库 `net.engawapg.lib:zoomable`，取址/连播/预取三族算式继续**转交**共享面不重述；档位与 `ZoomState` 上提到应用根，浮层与查看器共用同一颗状态；键盘那一层沿用既有那颗纯函数并扩两枚参数，全模块仍只有一处按键接线。

**Tech Stack:** Kotlin 2.4.10 / Compose Multiplatform 1.12.1（desktop）、Coil 3.6.2、JUnit 4（`org.junit.Test`）、Gradle。

**Spec:** `docs/rounds/desktop-image-viewer-design-2026-10-08.md`（本计划逐条实现它的 §0~§9；两份一起读）

## Global Constraints（每一条都隐含在每个任务里）

- **`app/` 一字不动**（受保域，逐条改动要单独授权）。本计划全部落在 `desktop/` 与 `docs/`。
- **不 commit、不 push**（等用户明确指令）。每个任务的收尾步骤是"回写手册 + 报数字"，不是提交。
- 桌面测试用 **`org.junit.Test` + `org.junit.Assert.*`**；`kotlin.test` **不在 classpath**。
- **扫描式判据一律用现成那批件**（`desktop/src/test/…/gallery/ui/DesktopSourceTree.kt`，逐枚核过）：`desktopUiSource("X.kt")` 取 gallery/ui 里的一颗文件、`desktopSourceOf("…相对路径")` 取桌面 main 任意文件、`desktopUiSources()` / `desktopMainSources()` 取语料、`codeOccurrences(file, 字面)` 数出现次数、`codeText(file)` 拿**去注释**后的代码。
  ⚠️ 别写 `File("src/main/…").readText()`：① 它依赖 cwd（本批已实测 cwd=模块目录，但那枚 helper 是向上找 `settings.gradle.kts`，换机器/换 worktree 不炸）；② `readText` 含注释 ⇒ 禁串锚点会被自己的注释喂饱（本仓判例：批 15 那次注释引旧句把禁串判成恒红）。
- 跑测试**不许接管道**：`sh gradlew :desktop:test > 日志文件 2>&1`，然后读退出码与 XML。
- 每跑一次判据前先 `rm -f desktop/build/test-results/test/TEST-*.xml`（旧 XML 会被读成假绿）。
  ⚠️ **这一条本批已现场实锤一次**：RED 那一跑是**编译失败**、测试压根没跑，而那颗类的结果 XML 照样在（是上一笔留下的）。看到"XML 在"就当"跑过了"= 假读数。
- **尺子环境已验（不必重跑，2026-10-08 现量）**：`:desktop:test` 的工作目录是 `D:\venera-compose\desktop\.` ⇒ 计划里那批扫描式判据用的相对路径（`src/main/kotlin/…`、`build.gradle.kts`）**都读得到**；临时探针跑完已移回 `_trash/scratch-2026-10-08/parked/DesktopViewerRulerEnvTest.kt.hold`，复跑全量 **85 类 / 653 / 0 失败**，树回到原样。
- 三方对账：XML 里 `tests=` 求和 = `<testcase` 块数 = 源码 `@Test` 计数。开工前基线是 **653 / 85 类 / 0 失败**。
- 界面文案规矩：不带 AI 味（无星号/反引号/内部黑话），Kotlin 字符串里用「」不用英文双引号。
- 降级口径：「没取到 / 没有 / 没接」是三句话；失败要念原话 + 本机那半句，**绝不静默**。
- 取证起窗：一次命令内"起 → 轮询 → 关"，用完 `taskkill`，窗口残留必须为 0；**禁鼠标禁键盘自验**。
- 依赖坐标逐字：`implementation("net.engawapg.lib:zoomable:2.16.0")`。compose 同版已量（`desktopRuntime` 全 1.12.1）、`mv=[2,4,0]`、class major 65。
- 判据写法：转交类钉**出现次数 + 重述字面**；接线类钉**顺序**；覆盖层动作走**必填形参**不吃 compositionLocal；正向锚点之外还要一枚**负向**锚点。

**待用户拍板（阻塞 Task 2 的第 3 颗纯函数与 Task 5 的入口档）**：设计 §4.1 的 A/B —— A = 进查看器升到屏上那档「大图」（预取跟着涨），B = 不升档。未拍之前按 **A 实现、B 的判据同写**（那颗纯函数两种输入都测得出来），落地时用户改主意只需换实参。

---

### Task 1: 依赖探针（先量"库在桌面解析得到、编得动"，再写功能）

**Files:**
- Modify: `desktop/build.gradle.kts`（`dependencies {` 块内，紧挨 `implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing")` 那行之后）
- Create: `desktop/src/test/kotlin/com/venera/desktop/gallery/ui/DesktopViewerDependencyTest.kt`

**Interfaces:**
- Consumes: 无
- Produces: 坐标 `net.engawapg.lib:zoomable:2.16.0` 在桌面编译面内可用；`DesktopViewerDependencyTest` 三颗判据（后续任务不再自证依赖）

- [ ] **Step 1: 写三颗先红的判据**

```kotlin
package com.venera.desktop.gallery.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 依赖探针。批 47 那枚「单测从不构造 ImageLoader」的教训反过来读就是：
 * 依赖**解析得到**不等于**编得动**，而这两条都不等于**屏上真能用**（那一条在 Task 7 的图证里）。
 */
class DesktopViewerDependencyTest {
    // 与既有那把同族尺子同一口径（`DesktopGalleryPreloadTest` ⑦ 就是这么取的）：
    // desktopSourceOf 只到 desktop/src/main/kotlin/… 底下，构建脚本要用 repoRoot 拼
    private val build get() = java.io.File(DesktopSourceTree.repoRoot, "desktop/build.gradle.kts")

    @Test
    fun `构建脚本带那枚坐标`() {
        assertTrue(
            "构建脚本里没有 net.engawapg.lib:zoomable —— 探针没做或依赖没落地",
            DesktopSourceTree.codeOccurrences(build, "\"net.engawapg.lib:zoomable:2.16.0\"") == 1,
        )
    }

    @Test
    fun `构建脚本仍是这颗判据的输入`() {
        // 这条钉的是**既有事实**（第十批就落了 `inputs.file(project.buildFile)`）：它现在就该绿，
        // 作用是防以后被删 —— 删掉之后改依赖会被判成 UP-TO-DATE 而拿旧结果放行。
        assertTrue(
            "构建脚本没把自身声明成测试输入，改依赖会被 UP-TO-DATE 放行旧结论",
            DesktopSourceTree.codeOccurrences(build, "inputs.file(project.buildFile)") >= 1,
        )
    }

    @Test
    fun `桌面没有偷偷改用另一族缩放件`() {
        val forbidden = listOf("me.saket.telephoto", "io.github.panpf.zoomimage")
        val hits = DesktopSourceTree.desktopMainSources().flatMap { f ->
            DesktopSourceTree.codeLinesOf(f).mapIndexedNotNull { n, l ->
                if (forbidden.any { l.contains(it) }) "${f.name}:${n + 1} $l" else null
            }
        }
        assertTrue("桌面引了别的缩放族：$hits", hits.isEmpty())
    }
}
```

- [ ] **Step 2: 跑一次确认它红**

Run: `sh gradlew :desktop:test --tests "com.venera.desktop.gallery.ui.DesktopViewerDependencyTest" > _trash/scratch-2026-10-08/logs/red50t1.log 2>&1; echo exit=$?`
Expected: **只有第 1 颗红**（坐标还没进脚本）。第 2 颗今天就该绿 —— 它钉的是既有事实，不是本批新增；第 3 颗也该绿（今天确实没引别族），它是**防以后**的负向锚点。⚠️ 这一笔是编译失败还是断言失败要看形状：坐标那颗红是断言失败（文件读得到、串不在），所以**这一笔会产出 XML**，读它按块切开归属。

- [ ] **Step 3: 加依赖**

`desktop/build.gradle.kts` 的 `dependencies {` 里加：

```kotlin
    // 大图那一屏的缩放能力（第五十批）。选它的三条读数都写在
    // docs/rounds/desktop-image-viewer-design-2026-10-08.md §2：stable 2.16.0、
    // desktopRuntime 的 compose 三件与本仓逐字同版（1.12.1）、滚轮是一等公民。
    // ⚠️ 它的滚轮默认档是 EnabledWithCtrlKey，Task 5 那处必须显式选 Enabled。
    implementation("net.engawapg.lib:zoomable:2.16.0")
```

同一文件里给那颗扫描判据补输入声明（放在 `tasks.withType<KotlinJvmTest>` 或既有 test 任务配置块内）：

```kotlin
    inputs.file(project.buildFile)
```

- [ ] **Step 4: 量解析与编译**

Run: `sh gradlew :desktop:dependencies --configuration debugRuntimeClasspath > _trash/scratch-2026-10-08/logs/dep50.log 2>&1; echo exit=$?`
Expected: 日志里出现 `net.engawapg.lib:zoomable-desktop:2.16.0`（**不是** root 那颗 `zoomable:2.16.0` —— root 只是 `available-at` 指针）。

Run: `sh gradlew :desktop:compileKotlin > _trash/scratch-2026-10-08/logs/compile50.log 2>&1; echo exit=$?`
Expected: `exit=0`。若报元数据版本不兼容 ⇒ 停手，按设计 §2 的兜底改自写那一版，并把这条读数写进设计 §8（不许硬凑）。

> 已经替这一跳量过的两枚（**不必重跑**，读数在设计 §2.1）：`javap -v` 全量扫 `zoomable-desktop-2.16.0.jar` 的 64 颗 class ⇒ 非 `androidx` 的 `android/*` 引用 **0**、无反射无 ServiceLoader（运行期撞 Android-only 类那一族风险 ≈ 0）；滚轮那一路读的是 `PointerEventType.Scroll`（桌面鼠标滚轮的同一个通道）。**唯一还悬着的仍是"屏上滚一下到底动没动"** —— 那一条在 Task 7 之后由用户手动确认。

- [ ] **Step 5: 复跑判据确认绿 + 回写手册**

Run: `rm -f desktop/build/test-results/test/TEST-*.xml && sh gradlew :desktop:test > _trash/scratch-2026-10-08/logs/green50t1.log 2>&1; echo exit=$?`
Expected: 全绿；总数 **656**（653 + 3）。把"探针三条读数 + 新总数"各一行写进 `docs/HANDOFF-2026-10-04.md`（§80 之下续写）。**不提交。**

---

### Task 2: 那三颗纯函数（宽度 / 入口档 / 放大种子）

**Files:**
- Create: `desktop/src/main/kotlin/com/venera/desktop/gallery/ui/DesktopGalleryViewerPolicy.kt`
- Create: `desktop/src/test/kotlin/com/venera/desktop/gallery/ui/DesktopGalleryViewerPolicyTest.kt`

**Interfaces:**
- Consumes: `DesktopGalleryMetrics.detailWidth`、`.detailScrimWidth`（`DesktopGalleryMetrics.kt:201,204`）、`DesktopDetailTier`（`DesktopGalleryDetailPolicy.kt:69`）
- Produces:
  - `internal fun desktopViewerContentWidth(contentWidth: Dp, overlayOpen: Boolean): Dp`
  - `internal fun desktopViewerEntryTier(tiers: List<DesktopDetailTier>): DesktopDetailTier?`
  - `internal fun desktopViewerZoomScale(raw: Float, maxScale: Float): Float`
  - `internal fun desktopViewerZoomReadout(raw: Float, applied: Float): String`

- [ ] **Step 1: 写八颗先红的判据**

```kotlin
package com.venera.desktop.gallery.ui

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DesktopGalleryViewerPolicyTest {
    // ① 浮层开着 ⇒ 减两道（340 面板 + 46 渐隐带），不是只减 340
    @Test
    fun `浮层开着时让开面板与渐隐带`() {
        assertEquals(1226.dp - 340.dp - 46.dp, desktopViewerContentWidth(1226.dp, overlayOpen = true))
    }

    // ② 浮层关着 ⇒ 全宽
    @Test
    fun `浮层关着时吃全宽`() {
        assertEquals(1226.dp, desktopViewerContentWidth(1226.dp, overlayOpen = false))
    }

    // ③ 窗口窄到不足一档 ⇒ 不许负数（负宽度会让布局直接崩）
    @Test
    fun `窄到不足一档时不许变负数`() {
        val narrow = desktopViewerContentWidth(120.dp, overlayOpen = true)
        assertTrue("窄窗算出了 $narrow", narrow >= 0.dp)
    }

    // ④ 入口档：表里有「大图」就升到「大图」
    @Test
    fun `有那一档就升`() {
        assertEquals(
            DesktopDetailTier.SAMPLE,
            desktopViewerEntryTier(listOf(DesktopDetailTier.PREVIEW, DesktopDetailTier.SAMPLE)),
        )
    }

    // ⑤ 表里只有「预览」⇒ 留在「预览」，不许凭空造一档
    @Test
    fun `没那一档就照表里第一枚`() {
        assertEquals(DesktopDetailTier.PREVIEW, desktopViewerEntryTier(listOf(DesktopDetailTier.PREVIEW)))
    }

    // ⑥ 被挡那张（空表）⇒ 一档都不升。这条是"直接写 SAMPLE"那种偷懒版本的死穴
    @Test
    fun `空表不升`() {
        assertNull(desktopViewerEntryTier(emptyList()))
    }

    // ⑦ 放大种子三档输入各过一遍：越界要夹，合法要原样
    @Test
    fun `种子夹到合法区间`() {
        assertEquals(1f, desktopViewerZoomScale(0f, 5f), 0f)
        assertEquals(2.5f, desktopViewerZoomScale(2.5f, 5f), 0f)
        assertEquals(5f, desktopViewerZoomScale(9f, 5f), 0f)
    }

    // ⑧ 夹了就必须说出来：读数里同时有"给的"和"用的"
    @Test
    fun `读数把两枚数都念出来`() {
        val line = desktopViewerZoomReadout(raw = 9f, applied = 5f)
        assertTrue("读数少了给的那枚：$line", line.contains("9"))
        assertTrue("读数少了用的那枚：$line", line.contains("5"))
    }
}
```

- [ ] **Step 2: 跑一次确认它红**

Run: `rm -f desktop/build/test-results/test/TEST-*.xml && sh gradlew :desktop:test > _trash/scratch-2026-10-08/logs/red50t2.log 2>&1; echo exit=$?`
Expected: 编译失败，形状已现场量过 —— `e: file:///…/DesktopGalleryViewerPolicyTest.kt:<行>:<列> Unresolved reference 'desktopViewerContentWidth'.`，退出码 1（**这一笔不会产出新的结果 XML**；看见旧 XML 在别当"跑过了"）。

- [ ] **Step 3: 写最小实现**

```kotlin
package com.venera.desktop.gallery.ui

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 大图那一屏的三道算式。全部纯函数：布局与取证都只吃它们的结果，不许在调用点各写一遍。
 */

/** 浮层开着时那一屏能用多宽 —— 面板与它左缘那道渐隐带**都要**让开。 */
internal fun desktopViewerContentWidth(contentWidth: Dp, overlayOpen: Boolean): Dp {
    if (!overlayOpen) return contentWidth
    val used = DesktopGalleryMetrics.detailWidth + DesktopGalleryMetrics.detailScrimWidth
    return (contentWidth - used).coerceAtLeast(0.dp)
}

/**
 * 进那一屏时落在哪一档（设计 §4.1 的 A 案）。
 *
 * 吃的是 `desktopDetailTiers` 给出来的那张表而不是直接回 `SAMPLE`：
 * 被挡那张表是空的、视频与原图那两条撤档规则也住在表里，跳过表就等于把它们的判据绕过去。
 */
internal fun desktopViewerEntryTier(tiers: List<DesktopDetailTier>): DesktopDetailTier? =
    if (tiers.contains(DesktopDetailTier.SAMPLE)) DesktopDetailTier.SAMPLE else tiers.firstOrNull()

/** 取证种子的倍数夹进合法区间。库自己也会夹，但它夹得不出声。 */
internal fun desktopViewerZoomScale(raw: Float, maxScale: Float): Float =
    raw.coerceIn(1f, maxScale.coerceAtLeast(1f))

/** 夹完那句实话：给的与用的都要在，不然"我给了 9 倍"与"屏上 5 倍"分不清谁错。 */
internal fun desktopViewerZoomReadout(raw: Float, applied: Float): String =
    "D_放大种子 给=$raw 用=$applied" + if (raw == applied) "" else "（已夹到合法区间）"
```

- [ ] **Step 4: 跑一次确认绿**

Run: `rm -f desktop/build/test-results/test/TEST-*.xml && sh gradlew :desktop:test > _trash/scratch-2026-10-08/logs/green50t2.log 2>&1; echo exit=$?`
Expected: 全绿，总数 **664**（656 + 8）。

- [ ] **Step 5: 扰动自审**

把 `desktopViewerEntryTier` 临时改成 `= DesktopDetailTier.SAMPLE`（偷懒版本），复跑：判据 ⑥ 必须红。改回。把这次扰动结果写进 `docs/rounds/desktop-image-viewer-plan-2026-10-08.md` 末尾的「扰动账」。

---

### Task 3: 键链扩一层（Esc 一层层退，全模块仍只有一处接线）

**Files:**
- Modify: `desktop/src/main/kotlin/com/venera/desktop/gallery/ui/DesktopKeyNavigation.kt:48-59`
- Modify: `desktop/src/test/kotlin/com/venera/desktop/gallery/ui/DesktopKeyNavigationTest.kt`（追加，不动既有 9 颗）

**Interfaces:**
- Consumes: `DesktopKeySignal`
- Produces: `internal fun onDesktopOverlayKey(signal: DesktopKeySignal, viewerOpen: Boolean, onCloseViewer: () -> Unit, onClose: () -> Unit, onStep: (Int) -> Unit): Boolean`（**签名变更**：既有测试那 9 颗的调用点必须同批铺完）

- [ ] **Step 1: 写四颗先红的判据（次序 + 两层不串 + 抬起不吞 + 一处链）**

```kotlin
    // ⑩ 查看器开着时 Esc 只关查看器，浮层那一句不许被叫
    @Test
    fun `Esc 先关查看器`() {
        val r = Recorder()
        assertTrue(onDesktopOverlayKey(down(Key.Escape), viewerOpen = true, onCloseViewer = r::closeViewer, onClose = r::close, onStep = r::step))
        assertEquals(1, r.closeViewerCalls)
        assertEquals(0, r.closeCalls)
    }

    // ⑪ 查看器关着时 Esc 才关浮层
    @Test
    fun `Esc 再关浮层`() {
        val r = Recorder()
        assertTrue(onDesktopOverlayKey(down(Key.Escape), viewerOpen = false, onCloseViewer = r::closeViewer, onClose = r::close, onStep = r::step))
        assertEquals(0, r.closeViewerCalls)
        assertEquals(1, r.closeCalls)
    }

    // ⑫ KeyUp 那一半照旧不消费（长按连发的老坑，两层都不许破）
    @Test
    fun `抬起那一半不吞`() {
        val r = Recorder()
        assertFalse(onDesktopOverlayKey(up(Key.Escape), viewerOpen = true, onCloseViewer = r::closeViewer, onClose = r::close, onStep = r::step))
        assertEquals(0, r.closeViewerCalls)
    }

    // ⑬ ← / → 在两层里都落到同一对 onStep（步进只有一个主人，不因查看器开着就换人）
    @Test
    fun `键与步进同向`() {
        val r = Recorder()
        onDesktopOverlayKey(down(Key.DirectionRight), viewerOpen = true, onCloseViewer = r::closeViewer, onClose = r::close, onStep = r::step)
        onDesktopOverlayKey(down(Key.DirectionLeft), viewerOpen = false, onCloseViewer = r::closeViewer, onClose = r::close, onStep = r::step)
        assertEquals(listOf(1, -1), r.steps)
    }
```

`Recorder` 加一枚 `var closeViewerCalls = 0` 与 `fun closeViewer() { closeViewerCalls++ }`；既有的 ①~⑨ 调用点补上 `viewerOpen = false` 与新 lambda（**同一批铺完，不留中间态**）。

- [ ] **Step 2: 跑一次确认红**

Run: `rm -f desktop/build/test-results/test/TEST-*.xml && sh gradlew :desktop:test > _trash/scratch-2026-10-08/logs/red50t3.log 2>&1; echo exit=$?`
Expected: 编译失败（`no value passed for parameter viewerOpen`）。

- [ ] **Step 3: 实现**

```kotlin
internal fun onDesktopOverlayKey(
    signal: DesktopKeySignal,
    viewerOpen: Boolean,
    onCloseViewer: () -> Unit,
    onClose: () -> Unit,
    onStep: (Int) -> Unit,
): Boolean = when {
    !signal.isKeyDown -> false
    signal.key == Key.Escape -> {
        // 一层一层退：查看器开着时只关它，再按一次才关浮层。反过来写就是"点进去一下全没了"。
        if (viewerOpen) onCloseViewer() else onClose()
        true
    }
    signal.key == Key.DirectionLeft -> { onStep(-1); true }
    signal.key == Key.DirectionRight -> { onStep(1); true }
    else -> false
}
```

那颗件顶部的类注释里"详情浮层的三枚键"改成"浮层与大图那一层的四枚键"，并补一句两层次序。

- [ ] **Step 4: 跑一次确认绿**

Run: `rm -f desktop/build/test-results/test/TEST-*.xml && sh gradlew :desktop:test > _trash/scratch-2026-10-08/logs/green50t3.log 2>&1; echo exit=$?`
Expected: 全绿；总数 **668**。既有判据 ⑥「全模块只有一处 `.onKeyEvent {`」必须仍然绿 —— 它绿说明这一批没起第二条链。

---

### Task 4: 档位与缩放态上提（浮层签名多两枚必填参数，三处调用点一次铺完）

**Files:**
- Modify: `desktop/src/main/kotlin/com/venera/desktop/gallery/ui/DesktopGalleryDetailPanel.kt:119`（签名）、`:209`（删掉自持的 `tier`）、`:216`（`shownTier` 兜底改吃形参）
- Modify: `desktop/src/main/kotlin/com/venera/desktop/gallery/ui/VeneraDesktopApp.kt:203-205`（新增两颗状态）、`:492-506`（调用点补参数）
- Modify: `desktop/src/main/kotlin/com/venera/desktop/gallery/ui/VeneraDesktopApp.kt:492-506`（唯一真调用点补两枚参数）

**Interfaces:**
- Consumes: `desktopDetailInitialTier`、`desktopDetailTiers`
- Produces: `DesktopGalleryDetailPanel(ports, selection, tier, onTierChange, initialQuality, onStep, onClose, onSearch, seedReverse)` —— 档位从"自持"变成"应用根持有、两边读同一颗"；应用根暴露 `tier`、`onTierChange`、`zoomState`

- [ ] **Step 1: 写三颗先红的判据**（新建 `DesktopGalleryViewerStateTest.kt`）

```kotlin
    private val panel get() = DesktopSourceTree.desktopUiSource("DesktopGalleryDetailPanel.kt")
    private val app get() = DesktopSourceTree.desktopUiSource("VeneraDesktopApp.kt")

    // ① 浮层里不许再自己 remember 一份档位（两条轨的形状）
    @Test
    fun `档位只有一处声明`() {
        assertEquals("浮层还自持档位", 0, DesktopSourceTree.codeOccurrences(panel, "var tier by remember"))
        assertEquals("应用根的档位声明应恰好一处", 1, DesktopSourceTree.codeOccurrences(app, "var tier by remember"))
    }

    // ② 缩放态也只有一处（ZoomState 上提，查看器与连播判据读同一颗）
    @Test
    fun `缩放态只有一处声明`() {
        assertEquals("rememberZoomState 应恰好一处", 1, DesktopSourceTree.codeOccurrences(app, "rememberZoomState("))
    }

    // ③ 浮层的档位形参是必填（带默认值时漏传不报错，而漏传的形状正是"两条轨"）
    @Test
    fun `浮层吃必填档位`() {
        assertEquals("形参表里那枚档位应恰好一处", 1, DesktopSourceTree.codeOccurrences(panel, "tier: DesktopDetailTier,"))
        assertEquals("档位不许带默认值", 0, DesktopSourceTree.codeOccurrences(panel, "tier: DesktopDetailTier ="))
    }
```

- [ ] **Step 2: 跑一次确认红**

Run: `rm -f desktop/build/test-results/test/TEST-*.xml && sh gradlew :desktop:test > _trash/scratch-2026-10-08/logs/red50t4.log 2>&1; echo exit=$?`
Expected: 三颗红（① 里 `var tier by remember` 还在浮层；② 没有 `rememberZoomState`；③ 没有该形参）。

- [ ] **Step 3: 应用根持有两颗状态**

`VeneraDesktopApp.kt` 在 `var selected …`（`:203`）之后加：

```kotlin
    // 档位与缩放态**上提**到这一层（第五十批）：浮层与查看器读同一颗。
    // 两条轨的形状在批 47 刚踩过 —— 预取算的地址与屏上取的不是同一枚档，就是"预取白费、点下去重取一趟"。
    var tier by remember { mutableStateOf(desktopDetailInitialTier(previewQuality)) }
    // 缩放态住在根上而不是查看器里：连播那颗判据要读 `scale`，而它住在集成方这一层。
    val zoomState = rememberZoomState(maxScale = 5f)
    // 查看器开没开（点卡 = 两层一起开；Esc 一层层退）
    var viewerOpen by remember { mutableStateOf(false) }
```

`LaunchedEffect(domain, selectedKey)`（`:209-212`）里补一行 `viewerOpen = false`（换域换页时两层一起收，否则留下一层压在新屏上）。

- [ ] **Step 4: 浮层改吃形参 + 三处调用点一次铺完**

`DesktopGalleryDetailPanel.kt`：形参表加 `tier: DesktopDetailTier,` 与 `onTierChange: (DesktopDetailTier) -> Unit,`，删掉 `:209` 那两行自持状态，`:216` 的兜底保留（它现在兜的是"传进来的档不在表里"）。所有 `tier = …` 的写处改调 `onTierChange(…)`。

`VeneraDesktopApp.kt:492` 的调用点补 `tier = tier, onTierChange = { tier = it },`。**真调用点只有这一处**（现场核过）：另外两处命中 `DesktopGalleryDetailTest.kt:190`、`DesktopKeyNavigationTest.kt:132` 是**扫字面量的判据**（断言根文件里含 `DesktopGalleryDetailPanel(` 这一串），不是调用点、改签名不会让它们红 —— 别去"铺"它们。**同一批铺完，不许留中间态**（批 45 整批退回就是栽在中间态上）。

- [ ] **Step 5: 预取跟着**共用档位**走（不改就是两条轨复现）**

今天那颗算地址的纯函数吃的是 `previewQuality`（设置里那枚），自己内部再推一档：

```kotlin
internal fun desktopPreloadUrls(
    mode: GalleryPreloadMode, current: Int,
    cards: List<DesktopWallCard>, quality: GalleryPreviewQuality,
): List<String>
```

档位上提之后，**屏上那一档**与**设置里那一枚**可以不同（用户在浮层手动换档、或 A 案升档）—— 预取若还按设置推，它取的就是另一档的字节，"翻到的下一张命中缓存"当场成假。改形参：

```kotlin
internal fun desktopPreloadUrls(
    mode: GalleryPreloadMode, current: Int,
    cards: List<DesktopWallCard>, tier: DesktopDetailTier,
): List<String> = GalleryPreload.plan(mode, current, cards.size)
    .map { cards[it] }
    .filterNot { it.masked }
    .map { desktopDetailTierUrl(it.post, tier) }
    .filter { it.isNotBlank() }
    .distinct()
```

调用点（`VeneraDesktopApp.kt` 那枚 `LaunchedEffect`）：钥匙与实参都从 `previewQuality` 换成 `tier`。批 47 那 7 颗判据里凡是钉 `quality` 形参名的（尤其"三元只住一处"那颗靠 `initialQuality` 这个名字钉的）要**同批改判**，不许留一条钉旧形状的死尺子。

新增一颗判据（`DesktopGalleryPreloadTest` 第 8 颗）：

```kotlin
    // ⑧ 预取吃的是屏上那一档，不是设置里那一枚
    @Test
    fun `预取跟着共用档位`() {
        val policy = DesktopSourceTree.desktopUiSource("DesktopGalleryPreloadPolicy.kt")
        assertEquals("预取的形参应是共用档位", 1, DesktopSourceTree.codeOccurrences(policy, "tier: DesktopDetailTier"))
        assertEquals("预取不该再吃设置里那枚质量", 0, DesktopSourceTree.codeOccurrences(policy, "quality: GalleryPreviewQuality"))
        // 整串实参一起钉（只数 `desktopPreloadUrls(` 会被"另一处合法调用"喂饱，批 34 那一族）
        val app = DesktopSourceTree.desktopUiSource("VeneraDesktopApp.kt")
        val wired = DesktopSourceTree.codeLinesOf(app).count { it.contains("desktopPreloadUrls(") && it.contains(", tier)") }
        assertEquals("调用点应把共用那枚 tier 原样递进去，恰好一处", 1, wired)
    }
```

- [ ] **Step 6: 跑一次确认绿**

Run: `rm -f desktop/build/test-results/test/TEST-*.xml && sh gradlew :desktop:test > _trash/scratch-2026-10-08/logs/green50t4.log 2>&1; echo exit=$?`
Expected: 全绿；总数 **672**（668 + 3 颗上提 + 1 颗预取）。⚠️ 若批 47 那几颗判据改判后总数对不上，先回代码看是谁多算了 —— 不许靠改判式里的数字凑平。

---

### Task 5: 那一屏 `DesktopGalleryViewer`

**Files:**
- Create: `desktop/src/main/kotlin/com/venera/desktop/gallery/ui/DesktopGalleryViewer.kt`
- Create: `desktop/src/test/kotlin/com/venera/desktop/gallery/ui/DesktopGalleryViewerTest.kt`

**Interfaces:**
- Consumes: Task 2 的三颗纯函数、Task 4 的 `tier`/`zoomState`、`desktopDetailTierUrl`、`desktopDetailTiers`、`Modifier.zoomable`、`coil3.compose.AsyncImage`
- Produces: `@Composable internal fun DesktopGalleryViewer(selection: DesktopWallSelection, tier: DesktopDetailTier, zoomState: ZoomState, viewerZoomSeed: Float?, contentWidth: Dp)`（遮挡态从 `selection.card?.masked` 现取；**失败那句在本地展示** —— 应用根今天没有可复用的提示状态，见 Step 3 那条更正）

- [ ] **Step 1: 写五颗先红的判据**（扫描式，覆盖"不许重述 / 恒 Fit / 显式滚轮档 / 遮挡不发请求 / 种子念两枚数"）

```kotlin
    // 一律走 codeText：那两句缺席文案也住在文件头的 KDoc 里，用 readText 会让正锚点被注释喂饱（恒真装饰）
    private val viewer get() = DesktopSourceTree.codeText(DesktopSourceTree.desktopUiSource("DesktopGalleryViewer.kt"))

    // ① 取址转交，桌面不重述三档算式
    @Test
    fun `取址转交那颗件`() {
        assertEquals("desktopDetailTierUrl 应恰好一处", 1, viewer.split("desktopDetailTierUrl(").size - 1)
        assertEquals("不许重述共享判据", 0, viewer.split("GallerySettingsModel.wallUrl").size - 1)
    }

    // ② 恒 Fit：Crop 裁掉的边缘放大了回不来
    @Test
    fun `恒用 Fit`() {
        assertEquals(1, viewer.split("contentScale = ContentScale.Fit").size - 1)
        assertEquals("不许出现 Crop", 0, viewer.split("ContentScale.Crop").size - 1)
    }

    // ③ 滚轮档必须显式选 Enabled（库默认是 EnabledWithCtrlKey，漏了就是"滚轮没反应"）
    @Test
    fun `显式开滚轮缩放`() {
        assertEquals("mouseWheelZoom 应恰好一处", 1, viewer.split("mouseWheelZoom = MouseWheelZoom.Enabled").size - 1)
    }

    // ④ 被挡那张不发请求，且念的是**浮层那一句原话**（两处两句说法=假话）
    @Test
    fun `遮挡那张不发请求且与浮层同句`() {
        val panel = DesktopSourceTree.codeText(DesktopSourceTree.desktopUiSource("DesktopGalleryDetailPanel.kt"))
        assertEquals("要先判遮挡再摆图", 1, viewer.split("card?.masked == true").size - 1)
        for (line in listOf("\"分级遮挡\"", "\"站方没给这一档的图\"")) {
            assertTrue("浮层今天就该有这句对照：$line", panel.contains(line))
            assertTrue("查看器那句要与浮层同串：$line", viewer.contains(line))
        }
        assertEquals("取图失败那句要上屏，不许只打 stdout", 1, viewer.split("failure?.let").size - 1)
        assertEquals("失败要带原话", 1, viewer.split("D_大图 取图失败").size - 1)
    }

    // ⑤ 种子那句读数两枚数都在（夹了不许装作没夹）
    @Test
    fun `种子念两枚数`() {
        assertTrue(viewer.contains("desktopViewerZoomReadout("))
        assertTrue("翻页要复位缩放", viewer.contains("zoomState.reset()"))
    }
```

- [ ] **Step 2: 跑一次确认红**

Run: `rm -f desktop/build/test-results/test/TEST-*.xml && sh gradlew :desktop:test > _trash/scratch-2026-10-08/logs/red50t5.log 2>&1; echo exit=$?`
Expected: 五颗红（文件不存在 → `readText()` 抛 / 断言失败）。

- [ ] **Step 3: 写实现**

```kotlin
package com.venera.desktop.gallery.ui

import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import com.venera.compose.gallery.domain.GalleryPost
import net.engawapg.lib.zoomable.MouseWheelZoom
import net.engawapg.lib.zoomable.ZoomState
import net.engawapg.lib.zoomable.zoomable

/**
 * 大图那一屏：满内容区的一张图 + 缩放 + 页码 + 三档态（遮挡 / 没给地址 / 取图失败）。
 *
 * 全部动作走**必填形参**：这一层是覆盖层，compositionLocal 在那里恒读默认值（本仓栽过三次，
 * 最近一次是批 36 那格「stdout 念了而屏上没动」）。
 * 两句缺席文案与浮层**逐字同串**（「分级遮挡」「站方没给这一档的图」）—— 同一件事在两屏念成两个名字
 * 就是假话（批 39 那条：行件两处共用）。
 */
@Composable
internal fun DesktopGalleryViewer(
    selection: DesktopWallSelection,
    tier: DesktopDetailTier,
    zoomState: ZoomState,
    viewerZoomSeed: Float?,
    contentWidth: Dp,
) {
    // 失败那句在**本地**展示。应用根今天没有可复用的提示状态：`VeneraDesktopApp.kt:233-234`
    // 那两枚是启动时各消费一次的坏档读数，不是提示通道；浮层自己也是这么办的
    //（`DesktopGalleryDetailPanel.kt:171` 的 `notice` 就是本地态）。
    // 翻页/换档就清掉：上一张的失败原话压在下一张上面就是假读数。
    var failure by remember(selection.index, tier) { mutableStateOf<String?>(null) }
    val card = selection.card
    val post: GalleryPost? = card?.post
    Box(
        Modifier
            .width(contentWidth)
            .fillMaxHeight()
            .background(DesktopTheme.WindowBackground)
            // 库的默认档是 EnabledWithCtrlKey：不显式选 Enabled，「滚轮没反应」会被读成「库在桌面不生效」
            .zoomable(zoomState, mouseWheelZoom = MouseWheelZoom.Enabled),
        contentAlignment = Alignment.Center,
    ) {
        when {
            card?.masked == true -> Text("分级遮挡", textAlign = TextAlign.Center)
            post == null -> Text("这一批里没有这一张", textAlign = TextAlign.Center)
            else -> {
                val url = desktopDetailTierUrl(post, tier)
                if (url.isBlank()) {
                    // 「没给」与「没取到」是两句话：这一句说的是站方那一档本来就没有
                    Text("站方没给这一档的图", textAlign = TextAlign.Center)
                } else {
                    AsyncImage(
                        model = url,
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize(),
                        onState = { state ->
                            if (state is AsyncImagePainter.State.Error) {
                                // 失败原话 + 本机那半句（反搜那批定的写法），不静默变白屏
                                val why = state.result.throwable.message?.take(200)
                                    ?: state.result.throwable.javaClass.simpleName
                                failure = "取这一档失败：$why"
                                println("D_大图 取图失败 $why 档=$tier")
                            }
                        },
                    )
                }
            }
        }
        failure?.let { why -> Text(why, Modifier.align(Alignment.BottomStart)) }
        Text(
            "第 ${selection.index + 1} / ${selection.size} 张",
            Modifier.align(Alignment.BottomCenter),
        )
    }
    // 翻页复位到 1x：带着放大态翻页，页码与屏上那张就对不上（假读数）
    LaunchedEffect(selection.index) { zoomState.reset() }
    // 取证种子（设计 §7）：走公开的 changeScale，initialScale 两侧都留 1f ⇒ reset 语义一致
    LaunchedEffect(viewerZoomSeed) {
        val raw = viewerZoomSeed ?: return@LaunchedEffect
        val applied = desktopViewerZoomScale(raw, zoomState.maxScale)
        println(desktopViewerZoomReadout(raw, applied))
        zoomState.changeScale(applied, Offset.Zero, tween(0))
    }
}
```

签名里**故意不给** `onStep` / `onCloseViewer`：翻页与退出的主人是上层那一条键链与浮层那两枚按钮（批 46 立的"步进只有一个主人"）。给一枚没人调的 lambda 就是"接了但没人按"的假接线（批 26/27 那本零调用点的账）。

- [ ] **Step 4: 跑一次确认绿**

Run: `rm -f desktop/build/test-results/test/TEST-*.xml && sh gradlew :desktop:test > _trash/scratch-2026-10-08/logs/green50t5.log 2>&1; echo exit=$?`
Expected: 全绿；总数 **677**。

- [ ] **Step 5: 扰动自审**

把 `mouseWheelZoom = MouseWheelZoom.Enabled` 删掉再跑：判据 ③ 必须红。改回复跑。结果记进「扰动账」。

---

### Task 6: 接线（一层 Box 包两层 + 焦点上提 + `zoomed` 换真值 + 三枚种子）

**Files:**
- Modify: `desktop/src/main/kotlin/com/venera/desktop/gallery/ui/VeneraDesktopApp.kt:346-349`（点卡时开两层）、`:405-420`（`zoomed` 真值）、`:447-508`（焦点三件套上提 + 挂查看器）
- Modify: `desktop/src/main/kotlin/com/venera/desktop/ForensicFlags.kt:100-134`（登记 `--viewer` / `--viewer-zoom`）
- Modify: `desktop/src/main/kotlin/com/venera/desktop/VeneraDesktop.kt:299-325`（读参数）、`:428-448`（往下传）
- Modify: `desktop/build.gradle.kts:286-300`（`-Pviewer` / `-PviewerZoom` → CLI 那一跳）
- Modify: `desktop/src/main/kotlin/com/venera/desktop/gallery/ui/VeneraDesktopApp.kt:218-220`（`D_落点` 补 `种子=` 字段）

**Interfaces:**
- Consumes: Task 3 的 `onDesktopOverlayKey(…, viewerOpen, onCloseViewer, …)`、Task 4 的 `zoomState`、Task 5 的 `DesktopGalleryViewer`、Task 2 的 `desktopViewerContentWidth` / `desktopViewerEntryTier`
- Produces: `-Pviewer=1`、`-PviewerZoom=2.5` 两枚取证种子；`D_落点 … 查看器=开/关 种子=…` 读数

- [ ] **Step 1: 写六颗先红的判据**（`DesktopGalleryViewerWiringTest.kt`）

```kotlin
    private val app get() = DesktopSourceTree.codeText(DesktopSourceTree.desktopUiSource("VeneraDesktopApp.kt"))
    private val flags get() = DesktopSourceTree.codeText(DesktopSourceTree.desktopSourceOf("ForensicFlags.kt"))
    private val main get() = DesktopSourceTree.codeText(DesktopSourceTree.desktopSourceOf("VeneraDesktop.kt"))
    private val gradle get() = java.io.File(DesktopSourceTree.repoRoot, "desktop/build.gradle.kts").readText()

    // ① 点卡时两层一起开（用户原话：点卡就进大图，不该再多一步）
    @Test
    fun `点卡开两层`() {
        assertTrue("点卡没把那一层带上", app.contains("viewerOpen = viewerOpensOnPick"))
        assertTrue("默认该进大图（只有 -Pviewer=0 才不进）", app.contains("val viewerOpensOnPick = startViewer != false"))
    }

    // ② zoomed 交真读数，那枚假 false 下架
    @Test
    fun `连播吃真缩放态`() {
        assertFalse("还在交假的 zoomed = false", app.contains("zoomed = false"))
        assertTrue(app.contains("zoomed = zoomState.scale > 1f"))
    }

    // ③ 全模块仍只有一处按键接线（既有判据 ⑥ 的同族，这里钉"上提之后仍是一处"）
    @Test
    fun `仍只有一处按键链`() {
        // 数的是**行**，不是"几个文件"：同一颗文件里写两处会被 distinct() 读成一处（这一版我自己就写错过）
        val hits = DesktopSourceTree.desktopMainSources().flatMap { f ->
            DesktopSourceTree.codeLinesOf(f).mapNotNull { l ->
                if (l.contains(".onKeyEvent {") || l.contains(".onPreviewKeyEvent {")) "${f.name}: ${l.trim()}" else null
            }
        }
        assertEquals("按键接线处数应恰好 1：$hits", 1, hits.size)
    }

    // ④ 种子登记进 KNOWN（漏登记会被 unknown 报成拼错）
    @Test
    fun `两枚种子登记了`() {
        assertTrue(flags.contains("\"--viewer\""))
        assertTrue(flags.contains("VIEWER_ZOOM"))
    }

    // ⑤ gradle 那一跳接上了（批 47 那条纪律：判读层接好而这一跳漏 = 不报错也不做事）
    @Test
    fun `参数从 gradle 传到 CLI`() {
        assertTrue(gradle.contains("providers.gradleProperty(\"viewer\")"))
        assertTrue(gradle.contains("providers.gradleProperty(\"viewerZoom\")"))
        assertTrue(main.contains("\"--viewer\""))
        assertTrue(main.contains("--viewer-zoom="))
    }

    // ⑥ 落点那行补了种子字段，且正反两钉
    @Test
    fun `落点念得出种子`() {
        assertTrue(app.contains("种子="))
    }
```

- [ ] **Step 2: 跑一次确认红**

Run: `rm -f desktop/build/test-results/test/TEST-*.xml && sh gradlew :desktop:test > _trash/scratch-2026-10-08/logs/red50t6.log 2>&1; echo exit=$?`
Expected: 六颗红。

- [ ] **Step 3: 参数那一族（KNOWN + 判读 + 传递）**

`ForensicFlags.kt`：加 `private const val VIEWER = "--viewer"`、`private const val VIEWER_ZOOM = "--viewer-zoom"`，两枚都进 `KNOWN`；加

```kotlin
    /** `--viewer-zoom=` 的判读：不是数字就念一句实话并按"不带种子"走（不猜一个倍数）。 */
    fun viewerZoom(args: List<String>): Float? {
        val given = args.firstOrNull { it == VIEWER_ZOOM || it.startsWith("$VIEWER_ZOOM=") } ?: return null
        val raw = given.removePrefix("$VIEWER_ZOOM=")
        val value = raw.toFloatOrNull()
        if (value == null) {
            println("D_取证参数 --viewer-zoom=$raw 不是个数，本轮不放大（不替你猜一个倍数）")
            return null
        }
        println("D_取证参数 --viewer-zoom 起窗后放大到 $value")
        return value
    }
```

`VeneraDesktop.kt`：`val viewer = ForensicFlags.state(args.asList(), "--viewer")`、`val viewerZoom = ForensicFlags.viewerZoom(args.asList())`，往下传 `startViewer = viewer, startViewerZoom = viewerZoom`（**必填形参、三态原样往下递**：`null` = 没给参数，`false` = 给了 `=0`，这两态在屏上是同一个行为，但在 `D_落点` 的 `种子=` 那一格里必须是两句话）。

`build.gradle.kts` args 块补：

```kotlin
            providers.gradleProperty("viewer").orNull?.let { "--viewer=$it" },
            providers.gradleProperty("viewerZoom").orNull?.let { "--viewer-zoom=$it" },
```

- [ ] **Step 4: 应用根接线**

- [ ] **Step 4: 应用根接线**

`VeneraDesktopApp` 形参加两枚必填：`startViewer: Boolean?`（`null` = 没给参数）、`startViewerZoom: Float?`。应用根里再导出那枚行为开关：

```kotlin
    // 「点卡进不进大图」这一屏的默认行为是**进**（用户原话）。`-Pviewer=0` 只服务取证时
    // "只想拍浮层那一屏"那一档，所以"没给参数"与"给了 0"必须分得开 —— 形参留三态，导出值才二态。
    val viewerOpensOnPick = startViewer != false
```

点卡那一处（`VeneraDesktopApp.kt:346-349` 那枚 `LocalDesktopWallPick provides { selection -> … }`）整颗换成：

```kotlin
                        LocalDesktopWallPick provides { selection ->
                            selected = selection
                            pickStamp = "墙上点卡"
                            // 进那一屏时升档（设计 §4.1 A 案）。吃的是那张表，不是直接写 SAMPLE：
                            // 被挡那张表是空的、视频与原图那两条撤档规则都住在表里。
                            val card = selection.card
                            if (card != null) {
                                val entry = desktopViewerEntryTier(desktopDetailTiers(card.post, card.masked))
                                if (entry != null) tier = entry
                            }
                            viewerOpen = viewerOpensOnPick
                        },
```

`:447-508` 那一段：把 `focusRequester` / `focusTarget` / `.onKeyEvent` 三件从浮层那颗 `Row` **上提**到一颗包着"查看器 + 浮层 Row"的 `BoxWithConstraints`，且这颗仍只在 `selection != null` 时组合（层全关时它不在组合树里，焦点自然交回那五枚 `TextField`）：

```kotlin
                    BoxWithConstraints(
                        Modifier
                            .matchParentSize()
                            .focusRequester(overlayFocus)
                            .focusTarget()
                            .onKeyEvent { event ->
                                onDesktopOverlayKey(
                                    event.asDesktopKeySignal(),
                                    viewerOpen = viewerOpen,
                                    onCloseViewer = { viewerOpen = false },
                                    onClose = onClose,
                                    onStep = step,
                                )
                            },
                    ) {
                        // 宽度只有这一条来源：maxWidth 是这格**真被约束到的**宽度，不是整窗宽（设计 §3）
                        val viewerWidth = desktopViewerContentWidth(maxWidth, overlayOpen = true)
                        if (viewerOpen) {
                            DesktopGalleryViewer(
                                selection = selection,
                                tier = tier,
                                zoomState = zoomState,
                                viewerZoomSeed = startViewerZoom,
                                contentWidth = viewerWidth,
                            )
                        }
                        // 下面这颗 Row（渐隐带 + DesktopGalleryDetailPanel）**整颗原样搬进来**：
                        // 内容一字不改，只把它身上那三件修饰（focusRequester / focusTarget / onKeyEvent）
                        // 删掉——它们已经在上面的 BoxWithConstraints 上了。它的 .align(Alignment.TopEnd) 保留。
                        Row(Modifier.align(Alignment.TopEnd).fillMaxHeight()) {
                            // 这两格原样搬：① `VeneraDesktopApp.kt:479-491` 那枚 46 宽渐隐带 Box
                            //（fillMaxHeight + width(detailScrimWidth) + horizontalGradient）；
                            // ② `:492-506` 那枚 DesktopGalleryDetailPanel(…) 连同它全部实参。
                        }
                    }
```

> ⚠️ 搬完之后 `grep -n "\.onKeyEvent {"` 在整个 `desktop/src/main` 下必须只剩一处（Task 6 判据 ③）。浮层那颗 Row 上若还留着第二处，两层就会各读一遍键，Esc 会一次退两层。

失败那句由查看器**本地**展示并打一行 `D_大图 取图失败 …`：应用根没有可复用的提示通道（`:233-234` 那两枚是启动时各消费一次的坏档读数），而浮层自己也是本地态（`DesktopGalleryDetailPanel.kt:171`）。**别为这一句去新开第二本通知账。**

`:405-420` 连播那格：`zoomed = false` → `zoomed = zoomState.scale > 1f`，并把 `zoomState.scale > 1f` 一起加进那枚 `LaunchedEffect` 的钥匙（放大结束时要重新起跑，否则连播永远醒不过来）。

`:218-220` 读数补种子字段：

```kotlin
        println("D_落点 域=${domain.title}(${domainStamp}) 行=${selectedRow.title} 浮层=${if (selected != null) "开" else "关"}(${pickStamp}) 查看器=${if (viewerOpen) "开" else "关"} 种子=${listOfNotNull("detail".takeIf { startDetail }, "viewer".takeIf { startViewer }, "viewerZoom".takeIf { startViewerZoom != null }).joinToString("+").ifBlank { "无" }}")
```

> ⚠️ 这一句必须是**一行**（两行拼接会把证据送到判式看不见的地方 —— 批 48 那条）。

- [ ] **Step 5: 跑一次确认绿 + 全量对账**

Run: `rm -f desktop/build/test-results/test/TEST-*.xml && sh gradlew :desktop:test > _trash/scratch-2026-10-08/logs/green50t6.log 2>&1; echo exit=$?`
Expected: 全绿；总数 **683**；三方对账一致（XML `tests=` 和 = `<testcase` 块数 = 源码 `@Test` 数）。

---

### Task 7: 图证 + 账本 + 收尾（这一步不做完就不算落地）

**Files:**
- Modify: `desktop/src/main/kotlin/com/venera/desktop/gallery/ui/DesktopGallerySettingsSpec.kt`（`galleryAnimated` 那句点名措辞改判）
- Modify: `docs/HANDOFF-2026-10-04.md`、`docs/rounds/desktop-image-viewer-design-2026-10-08.md`（把实测结果写回原节）

**Interfaces:**
- Consumes: 前六个任务的全部产物
- Produces: 三帧图证、`D_落点` 新读数实录、账本枚数与措辞、本批数字

- [ ] **Step 1: 起窗拍三帧**（一次命令内起 → 轮询 → 关）

```bash
sh gradlew :desktop:app -Pproxy=127.0.0.1:7890 -PstartDomain=GALLERY -Pdetail=1 -Pviewer=1 -PviewerZoom=2.5 \
  > _trash/scratch-2026-10-08/logs/shot50a.log 2>&1 &
# 轮询到 D_落点 含「查看器=开」再抓帧；抓完 taskkill 对应 PID
```

三帧依次：① 1x 那一屏；② 放大到 2.5 那一屏（`-PviewerZoom=2.5` 种的，stdout 应有 `D_放大种子 给=2.5 用=2.5`）；③ 关掉两层后在搜索框打得进字那一屏（焦点上提那格唯一的可见证据）。
Expected: 每帧 `relatedTransientWindows` 里**没有** `title:"Error"`；关窗后 `MainWindowTitle -ne ''` 的 java 计数为 0。

- [ ] **Step 2: 读数与像素不一致时以像素为准**

若 stdout 念了「查看器=开」而屏上没动 ⇒ **不许记成通过**（批 36 那格教训：覆盖层 + compositionLocal 的形状）。把不一致写进设计 §7 并回到 Task 6 修。

- [ ] **Step 3: 账本措辞改判**

`galleryAnimated` 那句从"桌面没有大图位"改成"有那一屏，但动图仍停在首帧"（`coil-gif` 无 `-jvm` 变体那条不变）；12 枚账本仍 **6 能改 / 4 点名 / 2 结构性**，枚数不动。跑一遍钉账本的判据（`DesktopGallerySettingsSpecTest` 一族）确认没红。

- [ ] **Step 4: 数字回写**

`docs/HANDOFF-2026-10-04.md` 第 16 行那一档：判据/类数/`main` 行数/jar 字节**全部从现场取**（XML 求和、`ls -l` jar），不许手抄。

- [ ] **Step 5: 收尾复跑**

Run: `rm -f desktop/build/test-results/test/TEST-*.xml && sh gradlew :desktop:test > _trash/scratch-2026-10-08/logs/green50final.log 2>&1; echo exit=$?`
Expected: 全绿；`app/` 仍一字未动（`git status --porcelain app/` 空）。**不提交。**

---

## 扰动账（执行时逐条填，空着 = 没做）

| 判据 | 扰动 | 期望 | 实际 |
|---|---|---|---|
| Task 2 ⑥ | `desktopViewerEntryTier` 改成恒 `SAMPLE` | 红 | ✅ 红**2 颗**（⑥ 空表不升 + ⑤ 没那档就照表里第一枚）。比预期多一颗是好事：偷懒版本同时破了两条口径。已改回 |
| Task 5 ③ | 删 `mouseWheelZoom = …` | 红 | ✅ 红 1 颗，`mouseWheelZoom 应恰好一处 expected:<1> but was:<0>`。已改回 |
| Task 6 ② | `zoomed` 改回 `false` | 红 | ✅ 红 1 颗。已改回 |
| Task 6 ⑤ | 删 gradle 那一跳 | 红 | ✅ 红 1 颗。已改回 |
| Task 6 ③ | 起第二条 `.onKeyEvent {` | 红 | ✅ 红**两处**：新判据 ③ 与既有 `DesktopKeyNavigationTest` ⑥ 同时抓到（这是想要的结果——一把尺子不够，两把都响才算数）。已改回 |
| 计划外（新写的那颗 ⑧） | 撤掉 `onClose` 里的 `viewerOpen = false` | 红 | ✅ 红 1 颗（这条判据是**图证**逼出来的，见下面「图证量到的那条缺口」，不是计划里的） |

三档扰动改回后全量复跑：**686 条 / 90 类 / 0 失败**（树回到原样）。

---

## 执行实录（2026-10-08，与计划有出入的地方逐条记）

### 计划算错/漏算的四处

1. **Task 1 Step 4 的配置名是猜的**：`--configuration debugRuntimeClasspath` **不存在**，本仓实际是 `devRuntimeClasspath`
   （Gradle 直接报 `configuration 'debugRuntimeClasspath' not found`，候选只有 `devRuntimeClasspath`）。
   ⇒ **任何"跑 `:desktop:dependencies --configuration X`"的写法都别照抄这一条**，X 要先查。
2. **`rm -f desktop/build/test-results/test/TEST-*.xml` 会被沙箱的安全删除拦掉**（85 颗 > 阈值 50，
   报 `SAFE_DELETE_BULK_CONFIRM_REQUIRED`）。⇒ 改用 **`sh gradlew :desktop:cleanTest`**，一次清空且不触发拦截。
   本批全部复跑都走这一条。
3. **计划里 `logs/` 目录当时不存在**，第一笔基线的重定向直接失败、`gradle` 压根没跑而退出码是 1
   ⇒ **那一笔 exit=1 是"命令没跑起来"，不是"测试失败"**。`mkdir -p` 之后才有着落。
   ⚠️ 这与"编译失败那笔不产出新 XML"是**两种**不同的假读数，别混。
4. **计划漏算了 `DesktopUnusedInternalTest` 这一族**：Task 2 写完四颗纯函数之后，全量跑会红 2 颗
   （`desktopViewerContentWidth` / `desktopViewerEntryTier` / `desktopViewerZoomScale` / `desktopViewerZoomReadout`
   四枚**仅测试引用**）。这是那把尺子的**正确拦截**，不是缺陷 ⇒ 计划里"Task 2 Step 4 全绿 664"这句不成立。
   Task 6 接线之后它们有了产品调用点，自动转绿。**账不是"少写了两颗判据"，是"那把尺子提前拦了一次"**。

### 实现期才撞到的两处

5. **桌面没有 material3**：计划给的 `import androidx.compose.material3.Text` 编不过
   （`Unresolved reference 'material3'`）。本仓文字走 **compose-fluent** 那枚：
   `import io.github.composefluent.component.Text`（与 `DesktopGalleryDetailPanel` 同一颗）。
   它恰好也接 `textAlign`，所以计划的写法其余部分照抄成立。
6. **Task 6 判据 ⑤ 的锚点定错了对象**（已改判一次）：原判式钉集成方源码里有 `--viewer-zoom=`，
   而那一串在**代码**里只出现在 `ForensicFlags` 的 KDoc 与函数注释里 —— 判据走 `codeText`（跳注释），
   所以读到空。集成方在代码里真正调的是那颗函数 `ForensicFlags.viewerZoom(args…)`。
   ⇒ 改钉函数名。**这是手册 §4.2「判据的对象是"是什么"不是"在哪"」的又一形状**：锚点挑了一个"长得像接线"的东西。

### 图证量到的那条缺口（计划外，本批的真产物）

**读数**（第一轮起窗，`-Pdetail=1 -Pviewer=1 -PviewerZoom=2.5`）里出现这一行：

```
D_落点 域=图库(启动) 行=每日热门 浮层=关(关掉浮层) 查看器=开 种子=detail+viewer+viewerZoom
```

`浮层=关` 与 `查看器=开` **并存** ⇒ 屏上留下「浮层没了而大图还开着」。

- **成因**：`onClose` 是浮层唯一的关闭主人（Esc 那条路因为有 `viewerOpen` 分流不走它，
  而**浮层顶上那枚 × 走的就是它**）。我接线时它只写了 `selected = null`，没人收 `viewerOpen`。
- **形状**：两层各有各的退路（`onCloseViewer` / `onClose`），但**"浮层 × = 关掉这一片"**这一语义
  没有落到任何一处。判据全绿时屏上已经错了 —— 计划里那条"685 条判据全绿"的乐观估计被现实否掉。
- **修法**：`onClose` 里补 `viewerOpen = false`；另加判据 ⑧ 钉住它（按函数体切、不数全文件）。
- **红线已验**：把那一行撤掉复跑，判据 ⑧ 精确变红（`关掉浮层那一处没收大图层`），修复后转绿。
- **复跑确认**：第二轮起窗那条读数**出现 0 次**（`grep -c` = 0）。

### 图证与读数（2026-10-08 14:2x）

- **起窗**：`sh gradlew :desktop:app -Pproxy=127.0.0.1:7890 -PstartDomain=GALLERY -Pdetail=1 -Pviewer=1 -PviewerZoom=2.5`
- **读数全对**：`--viewer 开（值=1）` / `--viewer-zoom 起窗后放大到 2.5` / `D_放大种子 给=2.5 用=2.5` /
  `查看器=开 种子=detail+viewer+viewerZoom`，stdout **无 Exception**。
- **帧**（1440×900，捕获 outer=1440×900 / client=1424×861）：大图那一屏铺满内容区、
  档位落在三档中间的「大图」（**自动升档生效**）、页码 `第 1 / 40 张`、右侧浮层照旧浮着且图未压在它下面
  ⇒ **浮层避让算式在屏上成立**；图被放大后按 `Fit` 居中（2.5 倍那一颗种子确实画得出来）。
- **用完即删**：该帧是真实成人内容，只落系统临时目录、核对后**当场删掉**，不进仓库、不 present。
- **关窗残留 0**（`taskkill` 之后 `EnumWindows` 数 venera 窗口 = 0）。
- ⚠️ **滚轮那一半仍未验**：屏上"手动滚一下到底动没动"要用户自己确认（禁鼠标令）。
  静态侧两枚已量（`mouseWheelZoom = MouseWheelZoom.Enabled` 显式选档 + 库里 `DetectMouseWheelZoom.kt`
  读的是 `PointerEventType.Scroll`），**不写成验收结论**。
- ⚠️ **焦点交回那一帧没拍成**：`两层都关掉之后在搜索框里打得进字` 需要先关两层（要点/按键），
  而 agent 侧禁鼠标禁键盘自验 ⇒ 那一格只能由用户确认，不记成"已验"。
- ⚠️ 第一轮起窗时读数出现过落点反复漂移（`墙上点卡` 重复出现），
  与批 48 §77.3 那次"外部真指针事件"同形状；**第二轮（修完缺口后）落点稳定不漂**，全程无人操作。
  这一条如实记下：形状像外部事件，但**不在本批的判定范围**内，两次跑法不同不构成对照复跑。

### 账本与数字（现场取，未手抄）

- `galleryAnimated` 那句改判为「大图那一屏现在有了，但动图仍停在第一帧：`coil-gif` 没有桌面变体」。
  `galleryBackdrop` 那句**仍成立**（背景设置入口确实没接），未动。
- **判据 687 条 / 90 类 / 0 失败**（三方对账：XML `testcase` 数 = 源码 `@Test` 数 = 687）；
  `desktop/src/main` **77 颗 / 15321 行**（批 47~49 是 75 颗 / 14985 行）；
  jar **4,139,538 B**；**`app/` 零改动**（`git status --porcelain app/` 空）。
- **不提交**（等指令）。

