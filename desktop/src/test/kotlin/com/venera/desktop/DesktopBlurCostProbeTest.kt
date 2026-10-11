package com.venera.desktop

import com.venera.desktop.gallery.ui.DesktopSourceTree
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 「那一层糊到底花多少钱」那枚探针的尺子（第六十四批）。
 *
 * ## 这一格补的是什么
 *
 * 批 62 量到应用内模糊**画得出来**，批 63 把它接成了真行为（糊挂在墙那棵子树上，半径 18dp）。
 * 而从那两批到今天，**没有任何一跑量过它的代价** —— 屏上好看与「每帧多花几毫秒」是两件事：
 * 一层 `Modifier.blur` 加在滚动的 `LazyVerticalGrid` 上，理论上每帧都要重算整层的高斯，
 * 那是与瓦片数无关而与**面积**有关的开销。没量过就只能说"今天不知道"，不能说"没问题"。
 *
 * ## 为什么探针也要有尺子（与批 62 同一族）
 *
 * 代价类探针最容易犯的错不是崩，而是**量出一串看着像数的东西**：
 * ① 只跑了一相（没有对照，那串毫秒什么都不能说明）；② 半径用的是探针里自己写的那个数，
 * 于是"18dp 的代价"这个结论盖的其实是探针里那个 30dp；③ 拿 `System.nanoTime()` 前后夹而不是
 * 从帧时钟取 —— 后者量的是这个循环转了多久，不是屏幕出了几帧；④ 两相起点不同（第二相从
 * 第一相滚到的位置接着滚），差值里混着"这一相画的是别的内容"；⑤ 读数不落 stdout（跑完只剩一扇窗）。
 * 下面每一条各钉住其中一种。
 *
 * ## 这一颗探针**不**回答什么
 *
 * 墙上的瓦片是本地生成的位图，不是网络上真下来的图 ⇒ 它量的是**这一层糊的合成开销**，
 * 不是"整面真墙在真网络下的帧时间"。文档与读数都必须这么写，别把前者写成后者。
 */
class DesktopBlurCostProbeTest {

    // 探针文件缺席时 `desktopSourceOf` 会直接抛（红在一次 I/O 上，没有信息量），所以路径先自己拿一次、
    // 由判据 ① 念出"代价这一格今天没有量具"那句，命名那颗留成惰性的。
    private val probePath = File(DesktopSourceTree.repoRoot, "desktop/src/main/kotlin/com/venera/desktop/BlurCostProbe.kt")
    private val probe: File by lazy { DesktopSourceTree.desktopSourceOf("BlurCostProbe.kt") }
    private val code: String by lazy { DesktopSourceTree.codeText(probe) }

    /** ① 各相必须在**同一进程**里（相表里有 `false` 也有 `true`），且那一层糊只有一处、吃的是相状态。 */
    @Test
    fun 同一进程里跑糊与不糊两向() {
        assertTrue(
            "探针文件不存在：代价这一格今天没有任何量具（${probePath.path}）",
            probePath.isFile,
        )
        assertTrue(
            "相表必须同时含糊与不糊（没有对照的一串毫秒什么都不能说明）：$code",
            Regex("""listOf\([^)]*false[^)]*true""").containsMatchIn(code),
        )
        assertEquals(
            "那一层模糊必须恰好一处（现在是 ${DesktopSourceTree.codeOccurrences(probe, "Modifier.blur(")} 处）",
            1,
            DesktopSourceTree.codeOccurrences(probe, "Modifier.blur("),
        )
        assertTrue("糊那一处没吃在相状态上（两相其实画的是同一棵树）", code.contains("if (blurOn)"))
        assertTrue("糊必须挂在那面墙上（挂在空 Box 上量到的不是墙的代价）", code.contains("LazyVerticalGrid("))
        assertTrue(
            "那一层没接进墙自己的 modifier（`LazyVerticalGrid` 在，糊却没挂它身上 ⇒ 两相画的是同一棵树）",
            code.contains("modifier = Modifier.fillMaxSize().then(wall)"),
        )
    }

    /** ② 半径必须吃产线那一枚常量：否则"18dp 花多少"这个结论盖的是探针里自己写的那个数。 */
    @Test
    fun 半径吃的是产线那一枚常量() {
        assertTrue(
            "没引产线那枚半径常量（探针自己造数 ⇒ 结论对不上屏上那一档）",
            code.contains("import com.venera.desktop.gallery.ui.DesktopWallBlurDp"),
        )
        assertTrue("blur 那一枚没吃半径常量", code.contains("Modifier.blur(radius = DesktopWallBlurDp.dp"))
        assertTrue(
            "半径被写成了本地字面量（负向钉：${Regex("""blur\(radius = \d""").find(code)}）",
            !Regex("""blur\(radius = \d""").containsMatchIn(code),
        )
    }

    /** ③ 帧时刻只能来自帧时钟；每相帧数是一笔像样的样本，且有预热。 */
    @Test
    fun 取的是帧时钟不是墙钟() {
        assertTrue("没从帧时钟取时刻（`System.nanoTime()` 前后夹量的是循环转了多久）", code.contains("withFrameNanos {"))
        assertEquals(
            "探针里出现了 ${DesktopSourceTree.codeOccurrences(probe, "System.nanoTime()")} 处墙钟计时",
            0,
            DesktopSourceTree.codeOccurrences(probe, "System.nanoTime()"),
        )
        assertTrue(
            "每相帧数不够（现在是：${Regex("""FRAMES_PER_PHASE = \d+""").find(code)?.value ?: "没有这枚常量"}）",
            Regex("""FRAMES_PER_PHASE = (1[2-9]\d|[2-9]\d\d)""").containsMatchIn(code),
        )
        assertTrue("没有预热相（第一帧带的是着色器与首帧布局，不是稳态）", code.contains("WARMUP_FRAMES"))
    }

    /** ④ 每相必须**回起点再采样**：设相 → 预热 → 回到 0 → 才进那一笔正经采样。钉的是顺序，不是"这几句在不在"。 */
    @Test
    fun 每相回到同一起点才采样() {
        val setPhase = code.indexOf("blurOn = ")
        val warmup = code.indexOf("sampleFrames(gridState, WARMUP_FRAMES)")
        val rewind = code.indexOf("scrollToItem(0)")
        val sample = code.indexOf("sampleFrames(gridState, FRAMES_PER_PHASE)")
        assertTrue("没有逐相设置那一枚状态", setPhase >= 0)
        assertTrue("没有预热那一笔（首帧带的是布局与着色器编译，不是稳态）", warmup >= 0)
        assertTrue("没有回到起点（第二相会接着第一相的位置滚 ⇒ 差值里混着内容不同）", rewind >= 0)
        assertTrue("没有那笔正经采样", sample >= 0)
        assertEquals(
            "回起点必须恰好一次（现在是 ${DesktopSourceTree.codeOccurrences(probe, "scrollToItem(0)")} 处）",
            1,
            DesktopSourceTree.codeOccurrences(probe, "scrollToItem(0)"),
        )
        assertTrue(
            "次序不对：设相($setPhase) → 预热($warmup) → 回起点($rewind) → 采样($sample)",
            setPhase in 0 until warmup && warmup < rewind && rewind < sample,
        )
    }

    /** ⑤ 读数必须落 stdout：归属一行 + 每相一行 + 差值一行 + 回退校验一行 + 收尾一行。 */
    @Test
    fun 念得出归属每相差值与回退() {
        assertTrue("没念归属那一行（WGC 靠标题里的 PID 认帧，stdout 靠这行认是谁在量）", code.contains("S09C_窗口 已开"))
        assertTrue("标题没带 PID", code.contains("ProcessHandle.current().pid()"))
        assertTrue("每相那一行没念 p50", code.contains("S09C_相") && code.contains("p50="))
        assertTrue("每相那一行没念 p95", code.contains("p95="))
        assertTrue("没念丢帧数（只看均值会把偶发的 200ms 抹平）", code.contains("丢帧="))
        assertTrue("没念堆（这一层若靠快照，代价先看堆）", code.contains("堆已用="))
        assertTrue("没念差值那一行", code.contains("S09C_差值"))
        assertTrue(
            "没念序偏置与重复性那两行（两枚同条件相自己相比 —— 批 64 缺的就是这一行，它把序偏置算进了代价）",
            code.contains("S09C_回退") && code.contains("S09C_重复"),
        )
        assertTrue("探针取完数没走（会把 gradle 任务吊在窗口上）", code.contains("exitProcess(0)"))
    }

    /** ⑥ 三环的第三环：构建脚本那一跳得把这个入口传出去，且中文读数不能被 GBK 吞掉。 */
    @Test
    fun 构建脚本里有那一跳而且带UTF8() {
        val gradle = File(DesktopSourceTree.repoRoot, "desktop/build.gradle.kts").readText()
        val marker = """tasks.register<JavaExec>("blurcost") {"""
        assertTrue("构建脚本里没有 `blurcost` 那一跳（判读层与探针都好了也起不来）", gradle.contains(marker))
        val block = gradle.substringAfter(marker).take(400)
        assertTrue("任务没指向探针：${block.take(120)}", block.contains("com.venera.desktop.BlurCostProbeKt"))
        assertTrue("那一跳没带 UTF-8 stdout（中文读数会全成 U+FFFD）", block.contains("-Dstdout.encoding=UTF-8"))
    }

    /**
     * ⑦ 必须有一档能停下来让人拍帧，而且**默认不走**。
     *
     * 为什么这一条属于尺子：这一颗的产物是毫秒数，而毫秒数证明不了"那一相真的糊了" ——
     * 那层 `Modifier.blur` 要是静默没吃到，读数会一模一样地写着"糊不要钱"（量具与目标错配）。
     * 唯一能证伪它的是一帧屏。默认 0 是因为取数那一跑不该白等。
     */
    @Test
    fun 有一档停下来让人拍帧而默认不走() {
        assertTrue("没有 hold 那一档（拍不到「那一相真糊了」那一帧）", code.contains("private var holdMs = 0L"))
        assertTrue("hold 那一档没真等（写了常数却没 delay）", code.contains("delay(holdMs)"))
        assertTrue("等待没排在起手（落在被量的那段里就会污染读数）", code.contains("起手等待="))
        val gradle = File(DesktopSourceTree.repoRoot, "desktop/build.gradle.kts").readText()
        val block = gradle.substringAfter("""tasks.register<JavaExec>("blurcost") {""").take(700)
        assertTrue(
            "构建脚本那一跳没把 hold 传下去（三环的第三环：判读层认、脚本不传就等于没这档）",
            block.contains("gradleProperty(\"hold\")"),
        )
    }

    /**
     * ⑧ 正对照的把手：每帧步长得能从命令行调大。
     *
     * 为什么这一条属于尺子：三相读数一模一样有两种成因 —— "糊真的不要钱"与"这把尺子压根不认账"
     * （帧间隔跟的是时钟而不是绘制）。把步进从 40 调到 400，每帧新铺的瓦片多一个量级；
     * 读数若一动不动，那"没代价"三个字就不许写进结论。没有这个把手，这架探针只会生产**看着像数的东西**。
     */
    @Test
    fun 步长得调大做正对照() {
        assertTrue("步进写死了（调不动就没有正对照）", code.contains("private var stepPx = SCROLL_STEP_PX"))
        assertTrue("那一枚把手没判读 `step=`", code.contains("startsWith(\"step=\")"))
        assertEquals(
            "推进那一处没吃可调的那枚步进（现在是 ${DesktopSourceTree.codeOccurrences(probe, "scrollBy(stepPx)")} 处）",
            1,
            DesktopSourceTree.codeOccurrences(probe, "scrollBy(stepPx)"),
        )
        assertTrue("步进没上归属那一行（两跑对不上是哪一档就是白跑）", code.contains("步进=\${stepPx}px"))
        val gradle = File(DesktopSourceTree.repoRoot, "desktop/build.gradle.kts").readText()
        val block = gradle.substringAfter("""tasks.register<JavaExec>("blurcost") {""").take(700)
        assertTrue("构建脚本那一跳没把 step 传下去（三环的第三环）", block.contains("gradleProperty(\"step\")"))
    }

    /**
     * ⑨ 屏上必须有一枚自报家门的牌，而且它不吃那层糊。
     *
     * 为什么这一条属于尺子：本批头一跑就是**两帧对不上相** —— 我按 `起手等待` 的秒数掐时间去拍，
     * 结果拍到的一软一锐与 `糊=off` / `糊=on` 正好相反，而那一刻分不清是接线反了还是我拍错了相。
     * 帧本身没有时间戳，也没有相号 ⇒ 唯一不靠掐时间的办法是让那一帧自己写着它是哪一相。
     * ⚠️ 牌必须挂在糊那层**外面**：牌自己被糊掉就读不出来了。
     */
    @Test
    fun 屏上那一枚牌自己写着是哪一相() {
        assertEquals(
            "`blurOn` 那一处写必须恰好一次（现在是 ${DesktopSourceTree.codeOccurrences(probe, "blurOn = ")} 处）",
            1,
            DesktopSourceTree.codeOccurrences(probe, "blurOn = "),
        )
        assertEquals(
            "牌上那枚相号必须与 `blurOn` 同处写（分开写就会一相对一相对不上，批 48 那颗戳的同族）",
            DesktopSourceTree.codeOccurrences(probe, "blurOn = "),
            DesktopSourceTree.codeOccurrences(probe, "phaseTag = "),
        )
        val write = code.indexOf("blurOn = ")
        val tag = code.indexOf("phaseTag = ")
        assertTrue("两枚写不在一处（次序：$write / $tag）", write in 0 until tag && tag - write < 200)
        assertTrue("牌上没写相号与糊态", code.contains("相\$i 糊="))
        assertTrue("牌读的是那枚相号", code.contains("text = phaseTag"))
        assertTrue(
            "牌挂到了糊那层里面（牌自己被糊掉就读不出来了）",
            code.indexOf("modifier = Modifier.fillMaxSize().then(wall)") < code.indexOf("text = phaseTag"),
        )
    }

    /**
     * ⑩ 覆盖层那一档（生产形状）：默认**不走**，走的时候归属行念得出。
     *
     * 为什么这一条属于尺子：批 64 量的是"墙自己在动"那一档，而生产里墙是静帧、动图在浮层里播 ——
     * 那两种形状下"这层糊要不要每帧重算"完全可以不一样。加一档而不把默认改掉，
     * 是为了让批 64 那两跑今天仍然可比（默认一变，旧读数就全成了另一件事的读数）。
     */
    @Test
    fun 覆盖层那一档默认不走而走了念得出() {
        assertTrue("没有默认静的那一档（默认一变，批 64 那两跑就不是同一件事了）", code.contains("private var overlayAnim = false"))
        assertTrue("那一枚把手没判读 `overlay=`", code.contains("startsWith(\"overlay=\")"))
        assertTrue("归属行没念是哪一种覆盖层（两跑对不上是哪一档就是白跑）", code.contains("覆盖层=\${if (overlayAnim)"))
        assertEquals(
            "归属行与屏上那枚牌都要念是哪一种覆盖层（现在是 ${DesktopSourceTree.codeOccurrences(probe, "覆盖层=\${if (overlayAnim)")} 处）",
            2,
            DesktopSourceTree.codeOccurrences(probe, "覆盖层=\${if (overlayAnim)"),
        )
        val gradle = File(DesktopSourceTree.repoRoot, "desktop/build.gradle.kts").readText()
        val block = gradle.substringAfter("""tasks.register<JavaExec>("blurcost") {""").take(900)
        assertTrue(
            "构建脚本那一跳没把 overlay 传下去（三环的第三环：判读层认、脚本不传就等于没这档）",
            block.contains("gradleProperty(\"overlay\")"),
        )
    }

    /** ⑪ 覆盖层逐帧推进走的是**帧时钟**，不是 `delay` 定步：两套节拍不同相，读数就把"等"算进"画"。 */
    @Test
    fun 覆盖层跟着帧时钟走() {
        assertTrue("覆盖层没从帧时钟取那一拍", code.contains("withFrameMillis {"))
        assertEquals(
            "`delay(` 全树只许起手那一处用（现在是 ${DesktopSourceTree.codeOccurrences(probe, "delay(")} 处）—— 第二处就是拿定步冒充逐帧",
            1,
            DesktopSourceTree.codeOccurrences(probe, "delay("),
        )
    }

    /** ⑫ 动的那一枚必须挂在糊那层**外面**（生产里浮层那张图是锐利的），且只在开那一档时摆出来。 */
    @Test
    fun 动的那一枚不吃那层糊() {
        assertEquals(
            "覆盖层没吃那一枚开关（现在是 ${DesktopSourceTree.codeOccurrences(probe, "if (overlayAnim) AnimatingOverlay(")} 处）",
            1,
            DesktopSourceTree.codeOccurrences(probe, "if (overlayAnim) AnimatingOverlay("),
        )
        assertTrue(
            "覆盖层排在了墙（含那层糊）之前 —— 那样它会被墙那棵盖住，量的就不是生产那一形",
            code.indexOf("modifier = Modifier.fillMaxSize().then(wall)") < code.indexOf("if (overlayAnim) AnimatingOverlay("),
        )
        assertTrue(
            "覆盖层自己吃了第二层糊（那枚 `Modifier.blur(` 全树必须恰好一处）",
            DesktopSourceTree.codeOccurrences(probe, "Modifier.blur(") == 1,
        )
    }

    /**
     * ⑬ 差值必须来自**配对**，而不是"拿第一相当基线"。
     *
     * 为什么这一条属于尺子（本批改判上一批的算法）：这台机器的第一相**总是最慢** ——
     * 批 64 七笔与批 65 两笔全一样（起步那一段的布局与着色器预热落在相 0）。
     * 拿相 0 当基线，等于把"相位序偏置"算进"糊的代价"，而方向正好**偏袒糊**（差值念成负的）。
     * ⇒ 两相糊两相不糊、差值从配对的相邻两对算。
     *
     * ⚠️ 批 71 再改判一次（同一枚尺子的形状跟着读数走）：批 65 那版是 ABAB（`off,on,off,on` + 两组均值），
     * 而批 70 的真读数说这台机器**整跑都在稳态化**（off 11.12→7.93、on 9.99→7.35 —— 单调下降，跨相不回头）。
     * 拿两组均值相减时，那笔趋势按"每组的第二相比第一相快多少"整笔留在差值里（`回退 −3.18ms` 就是它）；
     * 换成 **ABBA**（`off,on,on,off`）之后两对**相邻**配对里的趋势**等量反号**，取均值就抵消 ——
     * 这不是把结论改好看：同一批读数下两对估计各是 −1.13 / −0.58ms，散布 0.55ms 而组均值那把是 3.18ms，
     * **地板变小是设计的性质，不是挑出来的数**。
     */
    @Test
    fun 差值来自相邻配对而不是第一相() {
        assertTrue(
            "相表必须是 ABBA 那一形（相邻两对、方向相反）：现在是 ${Regex("""listOf\([^)]*\)""").find(code)?.value ?: "读不到"}",
            code.contains("listOf(false, true, true, false)"),
        )
        assertTrue(
            "差值没从两对相邻配对算（要一枚 `pairDelta`，且它吃的是紧邻的两相）",
            DesktopSourceTree.codeOccurrences(probe, "fun pairDelta(") == 1,
        )
        assertTrue("没念序偏置那一行（两枚同条件估计自己相比）", code.contains("S09C_回退"))
        assertTrue("没念估计的重复性（同条件两对差太多就不能只报一个均值）", code.contains("S09C_重复"))
    }

    /**
     * ⑳ 地板必须来自**那两对估计自己的散布**，而不是跨整跑的两组均值之差。
     *
     * 为什么这一条属于尺子：批 69/70 那两跑的地板（3.01 / 3.18ms）整笔是"整跑稳态化"的趋势，
     * 而趋势在 ABBA 里已经被设计抵消掉了 —— 继续拿它当地板，就等于**用一把已经扣掉偏置的尺子量的旧偏置**
     * 去否决新设计量出的东西（会把"量得出"永远读成"量不出"）。
     * ⇒ 地板 = |δ1 − δ2|（两对相邻估计之差）；`S09C_判` 那行仍必须念得出地板是多少，判词两支出路都留。
     */
    @Test
    fun 地板来自两对估计的散布() {
        assertTrue(
            "地板没取那两对估计之差（批 68 那版取的是两枚跨跑自比里更大的那枚，ABBA 之后那枚里还剩趋势）",
            code.contains("val noiseFloor = kotlin.math.abs(pairA - pairB)"),
        )
        assertTrue("两对估计没各自念得出（`S09C_对1`/`S09C_对2` 两行）", code.contains("S09C_对") && code.contains("相邻两对"))
        assertTrue(
            "差值只取了**一对**估计（两对都要，取均值：ABBA 的趋势靠反号抵消，拿一对就把它请回来了）",
            code.contains("val delta = (pairA + pairB) / 2.0"),
        )
        assertTrue("判词那行还在（判词由读数自己下，批 68 的账不许退）", code.contains("S09C_判 差值|p95|"))
        assertTrue(
            "不许把'量不出'说成'没有开销'（批 68 那条反向钉照旧）",
            !code.contains("无开销") && !code.contains("零开销") && !code.contains("没有开销"),
        )
    }

    /**
     * ⑭ 那一跑到底"量没量出东西"，得由**读数自己判**，不许留给人对着三行数字 eyeball。
     *
     * 为什么这一条属于尺子：批 65 那句"重载那一档 +1.4~+2.0ms 但与同跑散布同量级 ⇒ 未定"
     * 是我拿三行数字对着看的。判据不写进读数，下一跑（或下一个人、下一个 agent）就会把
     * "看着像有"写成"有"、把"看着像没有"写成"没有开销"。
     * ⇒ 地板必须取自**这把尺子自己的两枚同条件自比数**（序偏置与糊组重复里更大的那枚），
     *   而不是拍一枚常数 —— 常数过两天没人知道是从哪来的。
     */
    @Test
    fun 判词由读数自己下而地板来自自比() {
        val drift = code.indexOf("val pairA =")
        val spread = code.indexOf("val pairB =")
        val floor = code.indexOf("val noiseFloor = kotlin.math.abs(pairA - pairB)")
        val verdict = code.indexOf("S09C_判")
        assertTrue("没算第一对相邻估计", drift >= 0)
        assertTrue("没算第二对相邻估计", spread >= 0)
        assertTrue("地板没取那两对之差", floor >= 0)
        assertTrue("没念判词那一行", verdict >= 0)
        assertTrue(
            "次序不对：两对估计($drift/$spread) → 地板($floor) → 判词($verdict)",
            drift < floor && spread < floor && floor < verdict,
        )
    }

    /** ⑮ 地板不许是写死的常数，判词也不许把"量不出"说成"没有开销"。 */
    @Test
    fun 地板不是常数而量不出不等于没有开销() {
        assertTrue(
            "地板被写成了常数（${Regex("""noiseFloor = [0-9]""").find(code)?.value ?: "没有"}）",
            !Regex("""noiseFloor = [0-9]""").containsMatchIn(code),
        )
        assertTrue(
            "判词里说死了（本批那条老警告：量不出 ≠ 没有 —— 探针里不许出现「没有开销/零开销/无开销」）",
            !code.contains("无开销") && !code.contains("零开销") && !code.contains("没有开销"),
        )
        assertTrue("判词没把那枚地板念出来（判词要能被复核）", code.contains("噪声地板=%.2fms"))
    }

    /**
     * ⑯ 每一相等帧必须有**看门狗**：帧时钟不走时念一行 `S09C_挂` 并说不像是哪一环。
     *
     * 为什么这一条属于尺子（批 68 是被这一格卡住才立的）：那一批 `-Pstep=400` 那一跑起了窗、念完
     * `S09C_相0起手` 之后**十分钟零输出** —— 会话不可见（锁屏）时 `withFrameNanos` 根本不返回，
     * 于是进程活着、窗口开着、什么都不念。而那 15 条尺子全绿：它们钉的是"探针代码写没写对"，
     * **从不跑真探针**。⇒ 挂死与全绿可以同时成立，而"挂死"这一格今天没有任何读数会念它。
     * 超时排在那一笔正经采样**之外**还包括起手等待（`FrameWaitMs + holdMs`），否则 `hold` 一长就假报警。
     */
    @Test
    fun 每相等帧有看门狗() {
        assertEquals(
            "没给每一相套等帧超时（现在是 ${DesktopSourceTree.codeOccurrences(probe, "withTimeoutOrNull(")} 处）",
            1,
            DesktopSourceTree.codeOccurrences(probe, "withTimeoutOrNull("),
        )
        assertTrue(
            "超时那枚数太小或不是一枚常量（现在是：${Regex("""FrameWaitMs = \d[\d_]*""").find(code)?.value ?: "读不到"}）",
            Regex("""private const val FrameWaitMs = (3\d{4}|[4-9]\d{4}|[1-9]\d{5,})""").containsMatchIn(code),
        )
        val opening = code.indexOf("S09C_相\${i}起手")
        val watchdog = code.indexOf("withTimeoutOrNull(FrameWaitMs + holdMs) {")
        val warmup = code.indexOf("sampleFrames(gridState, WARMUP_FRAMES)")
        val sample = code.indexOf("sampleFrames(gridState, FRAMES_PER_PHASE)")
        assertTrue(
            "次序不对：起手念完($opening) → 套上看门狗($watchdog) → 预热($warmup) → 正经采样($sample)",
            watchdog >= 0 && opening in 0 until watchdog && warmup in (watchdog + 1) until sample,
        )
        assertTrue("挂死那一句没念是哪一相", code.contains("S09C_挂 相=\$i"))
        assertTrue(
            "挂死那一句没提这最像哪一环（会话不可见/锁屏 = 本机实测那一条）",
            code.contains("会话不可见？锁屏？"),
        )
        assertTrue("那一句里没把超时是多少 ms 念出来（报警要能复核门槛）", code.contains("等帧超时=\${FrameWaitMs + holdMs}ms"))
    }

    /**
     * ⑰ 挂了就必须**当场收尸**：不许带着空的/半的读数走到差值与判词那几行。
     *
     * 像真的假动作是把 `S09C_挂` 念出去然后继续跑 —— 下一相同样不出一帧，最后 `S09C_差值`
     * 拿着一串 0 帧的相念出"量不出"，而那件事的真正成因是这一跑根本没在画。
     * ⇒ 次序钉死：报警 → `exitProcess(2)` → 才轮到把读数收进账里；且 2 与 0 两支各恰好一处。
     */
    @Test
    fun 挂死那一支绝不念判词() {
        val alarm = code.indexOf("S09C_挂 相=")
        val exitTwo = code.indexOf("kotlin.system.exitProcess(2)")
        val accumulate = code.indexOf("reads.add(")
        val verdict = code.indexOf("S09C_判")
        val done = code.indexOf("S09C_DONE")
        assertTrue("没有报警那一句", alarm >= 0)
        assertTrue("报警后没走 exitProcess(2)（念完继续跑 = 半串的读数照样出判词）", exitTwo >= 0)
        assertTrue(
            "次序不对：报警($alarm) → 收尸($exitTwo) → 收读数($accumulate) → 判词($verdict) → 收尾($done)",
            alarm < exitTwo && exitTwo < accumulate && accumulate < verdict && verdict < done,
        )
        assertEquals(
            "`exitProcess(2)` 必须恰好一处（现在是 ${DesktopSourceTree.codeOccurrences(probe, "kotlin.system.exitProcess(2)")} 处）",
            1,
            DesktopSourceTree.codeOccurrences(probe, "kotlin.system.exitProcess(2)"),
        )
        assertEquals(
            "`exitProcess(0)` 必须恰好一处（现在是 ${DesktopSourceTree.codeOccurrences(probe, "kotlin.system.exitProcess(0)")} 处）",
            1,
            DesktopSourceTree.codeOccurrences(probe, "kotlin.system.exitProcess(0)"),
        )
        assertTrue(
            "挂死那一句里混进了成功收尾（`S09C_DONE` 只许在真跑完那一支念）",
            !code.substring(alarm, exitTwo).contains("S09C_DONE"),
        )
    }

    /**
     * ⑱ 四相配对**之前**必须先跑一相扔掉：起步那笔惩罚不落在"两组各一半"，它整笔砸在第一相上。
     *
     * 为什么这一条属于尺子（本批改判批 65 的抵消假设）：批 65 写下"序偏置在两组里各占一半就抵消"时
     * 没人去复核那条假设。批 69 第一笔真读数把它推翻了三成 —— `S09C_回退 两相不糊相比 p95 −3.01ms`，
     * 也就是说相 0（不糊组）比相 2 慢 3ms，那笔惩罚**全在对照组**，方向正好**对糊不利**（把对照抬高 ⇒ 差值被压小）。
     * ⇒ 配对只是"少算一半"，不是"不算"。扔掉一相才是把这笔从账上摘掉。
     * 结构钉四处：那一相在**同一个循环**里（所以也吃 ⑯ 那枚看门狗）、只从 `PHASES` 前面挂上去、
     * 不进那本 `reads` 账、归属行念得出扔了几相。
     */
    @Test
    fun 先扔一相再跑配对那四相() {
        assertTrue(
            "丢弃相数不是一枚 ≥1 的常量（现在是：${Regex("""DISCARD_PHASES = \d+""").find(code)?.value ?: "读不到"}）",
            Regex("""private const val DISCARD_PHASES = [1-9]""").containsMatchIn(code),
        )
        assertTrue(
            "那一相没挂在 `PHASES` 前面（另起一段循环就会绕过看门狗与次序那两条尺子）",
            code.contains("List(DISCARD_PHASES) { false } + PHASES"),
        )
        assertTrue("循环没走那串合并后的相表", code.contains("in ALL_PHASES.withIndex()"))
        assertTrue("配对那一形本身没保住（⑬ 钉的是 ABBA：`listOf(false, true, true, false)`）", code.contains("listOf(false, true, true, false)"))
        val guard = code.indexOf("if (i < DISCARD_PHASES) {")
        val branch = code.indexOf("} else {", guard)
        val accumulate = code.indexOf("reads.add(", branch)
        assertTrue("丢弃相没有被挡在账本外面（扔掉的那一相一旦进了 `reads`，它就是把相 0 的慢重新请回来）", guard >= 0)
        assertTrue("没有那一支 `else`（只有一支写法的话，`reads.add` 挡不住往里掉）", branch > guard)
        assertTrue(
            "次序不对：判式($guard) → 判式里不许有 `reads.add` → `else`($branch) → 收读数($accumulate)（把 `reads.add` 挪出 else 就是让丢弃相重新进账）",
            code.substring(guard, branch).let { !it.contains("reads.add(") } && accumulate in (branch + 1) until (branch + 140),
        )
        assertTrue("归属行没念得出扔了几相（两跑对不上是几相就是白跑）", code.contains("丢弃相=\${DISCARD_PHASES}"))
    }

    /**
     * ⑲ 扔掉的那一相**自己那一行要念得出 p95**：否则"惩罚真被吸收了"这件事只是我说的。
     *
     * 批 69 的教训是同一族：判据不写进读数，下一跑就把"看着像有"写成"有"。这一行让"该不该继续扔"
     * 变成可复核的数 —— 丢弃相的 p95 明显高于留下的不糊相，就说明吸收到位；两行一样，说明这笔不是起步造成的。
     */
    @Test
    fun 丢弃相自己念得出那一行() {
        assertTrue("没有丢弃相那一行读数", code.contains("S09C_丢弃相"))
        val discardLineText = code.lines().firstOrNull { it.contains("S09C_丢弃相") } ?: ""
        assertTrue(
            "丢弃相那一行没念 p95（不念数的丢弃相等于什么都没做；这一行是：$discardLineText）",
            discardLineText.contains("p95="),
        )
        val discardLine = code.indexOf("S09C_丢弃相")
        val keptLine = code.indexOf("S09C_相\${r.index}")
        assertTrue(
            "次序不对：丢弃相那一行($discardLine) 排在留下的那些相之前($keptLine) —— 反过来就是先出账再扔",
            discardLine >= 0 && keptLine >= 0 && discardLine < keptLine,
        )
        assertTrue("丢弃相没在屏上那枚牌里写清自己是丢弃相（拍帧的人会把扔掉的那一相当成相 0）", code.contains(" 丢弃（不进账）"))
    }

    /**
     * ㉑ 同一把尺子要能**跑几轮**，且每轮**独立**算地板、独立下判词；最后一行只回答"几轮一致不一致"。
     *
     * 为什么这一条属于尺子（批 72）：批 71 那句"地板 0.16ms"本身是**一笔单跑的散布** ——
     * 拿它去否决另一笔单跑的差值，等于用一次噪声去判另一次噪声。要么承认"一轮说不出话"，要么真的跑几轮。
     * ⇒ 混堆取均值是禁止的形状：把两轮的 `reads` 并成一串再算，得到的是一笔看着样本更多的地板，
     *   而它掩盖的正是"两轮各自的判词一致不一致"这一件唯一有用的事。
     * 三环照旧都要钉（判读层认 `rounds=` / 构建脚本把 `-Prounds=` 传下去 / 默认仍是 1 轮 ——
     *   默认一变，批 71 那一跑就成了另一件事的读数）。
     */
    @Test
    fun 一轮说不出话而几轮各自下判() {
        assertTrue("没有 `rounds` 那一枚可调的轮数（默认必须是 1）", code.contains("private var rounds = 1"))
        assertTrue("判读层不认 `rounds=`", code.contains("startsWith(\"rounds=\")"))
        val loop = code.indexOf("for (round in 1..rounds) {")
        val freshLedger = code.indexOf("val reads = ArrayList<PhaseRead>()")
        assertTrue(
            "轮循环与那本账的次序不对：账必须**每轮新建**（现在是 循环($loop) / 新建($freshLedger)）—— 并成一串再算就掩盖了'两轮各自判了什么'",
            loop >= 0 && freshLedger in (loop + 1) until (loop + 240),
        )
        assertTrue("每轮没有自己那一行判词（`S09C_轮`）", code.contains("S09C_轮"))
        assertTrue("收尾没有'几轮一致不一致'那一行", code.contains("S09C_总判"))
        assertTrue("一致性没由读数自己算（要拿各轮判词去重）", code.contains("verdicts.distinct().size == 1"))
        val gradle = File(DesktopSourceTree.repoRoot, "desktop/build.gradle.kts").readText()
        val block = gradle.substringAfter("""tasks.register<JavaExec>("blurcost") {""").take(1200)
        assertTrue(
            "构建脚本那一跳没把 rounds 传下去（三环的第三环：判读层认、脚本不传就等于没这档）",
            block.contains("gradleProperty(\"rounds\")"),
        )
    }

    /**
     * ㉒ 跨轮的**总判**只并堆「估计」而不并堆「相」，且**轮数不够时不许下任何判词**。
     *
     * 为什么这一条属于尺子（批 73）：批 72 那三跑念出 `地板=0.00 / 0.01 / 1.39ms` —— 每轮的地板只有**两枚**估计，
     * 而两枚数的散布可以正好是 0，于是"一丝差值也算量得出"（轮 2 就是这样判出的）。
     * 地板要跨轮，但**并的必须是每轮产出的那两对估计**（同一件事的 2N 次观测），绝不能并各轮的相 ——
     * 并相就把 ㉑ 那条"每轮独立一本账"废掉了，也就把"几轮各自判了什么"这件事重新埋进一堆数里。
     * ⇒ 还要钉一枚**最低观测数**：少于 4 枚估计（= 少于两轮）就不许说"量得出"，只许说"轮数不够"。
     *   这不是拍的常数门槛，是"两枚数算不出散布"这件事本身。
     */
    @Test
    fun 总判并堆估计而轮数不够就不许判() {
        assertTrue("没有跨轮那串估计", code.contains("val pairEstimates = ArrayList<Double>()"))
        assertTrue("每轮的两对没交进那串估计", code.contains("pairEstimates.add(pairA)"))
        assertTrue("每轮的两对没交进那串估计（第二对）", code.contains("pairEstimates.add(pairB)"))
        assertTrue(
            "那串估计住在轮循环**外面**而账本住在里面（并相=废掉 ㉑；并估计才是同一件事的 2N 枚观测）",
            code.indexOf("val pairEstimates = ArrayList<Double>()") < code.indexOf("for (round in 1..rounds) {"),
        )
        assertTrue("没有'少于 4 枚估计就不许判'那一支", code.contains("pairEstimates.size < 4"))
        assertTrue(
            "跨轮地板没取那串估计的极差（max−min）",
            code.contains("poolFloor = pairEstimates.max() - pairEstimates.min()"),
        )
        assertTrue("总判没念它吃了几枚估计（判词要能被复核）", code.contains("枚估计="))
        val shortLine = code.lines().firstOrNull { it.contains("判定=轮数不够") } ?: ""
        assertTrue(
            "轮数不够那一支没念「判定=轮数不够」，或它竟说了'量得出'（两枚数算不出散布，只能说不清；这一行是：$shortLine）",
            shortLine.contains("判定=轮数不够") && !shortLine.contains("量得出"),
        )
    }

    /**
     * ㉓ 地板要**同时念两把**（极差 + IQR），判词吃的是那把**稳健的**，而两把给的话不一样时必须明说。
     *
     * 为什么这一条属于尺子（批 74）：批 73 那跑 `枚估计=8 跨轮地板=2.66ms` —— 那 2.66ms 整笔由轮 1 的
     * 一枚野估计撑起（其余 7 枚都在 ±0.1ms 量级）。极差是**单枚观测的函数**，一枚野数就能把门槛撑满，
     * 于是"量不出"读的其实是"有一轮出过一格野数"。
     * ⇒ 换估计量（四分位距）而不是删那枚数（挑数就是造假）；并且**两把都要上读数**：
     *   只念稳健那把，下一跑就看不见"野数还在不在"；只念极差那把，就是批 73 的错。
     *   两把判得不一样时不许挑一个报 —— 那句分歧本身就是这一跑最重要的一条读数。
     */
    @Test
    fun 地板两把一起念而判词吃稳健那把() {
        assertEquals(
            "分位数那颗件必须恰好一处（现在是 ${DesktopSourceTree.codeOccurrences(probe, "private fun quartile(")} 处）",
            1,
            DesktopSourceTree.codeOccurrences(probe, "private fun quartile("),
        )
        assertTrue(
            "稳健地板没取四分位距（Q3−Q1）",
            code.contains("val iqrFloor = quartile(pairEstimates, 0.75) - quartile(pairEstimates, 0.25)"),
        )
        assertTrue("总判没念极差那一把（看不见野数还在不在）", code.contains("极差地板=%.2fms"))
        assertTrue("总判没念 IQR 那一把", code.contains("IQR地板=%.2fms"))
        assertTrue(
            "判词吃的是稳健那把（吃极差就是批 73 那枚被单枚野数撑满的门槛）",
            code.contains("kotlin.math.abs(poolDelta) > iqrFloor"),
        )
        assertTrue(
            "两把给的话不一样时没明说（不许挑一个报 —— 那句分歧才是这一跑最重要的一条读数）",
            code.contains("两把地板给的话不一样"),
        )
        assertTrue(
            "不许拿'删掉一枚估计'当稳健化（稳健靠的是估计量，不是筛数）",
            !code.contains("dropFirst()") && !code.contains("sorted().drop(1)"),
        )
    }

    /**
     * ㉔ 轮次那一句要念得出**人请求的那一枚**（第七十八批，§1 最后那一格）。
     *
     * 从前 `-Prounds=12` 被 `coerceIn(1, 8)` 静默夹成 8，而归属行只念生效值 —— 那一跑的读数看着像
     * "跑了 8 轮"，真相是"参数没生效"。没给 / 夹过 / 读不出三种各念各的，而判读走那颗抽出来的纯函数
     * （手册 §0：这类判断不许留在 `main()` 里，留在里面就没有尺子）。
     */
    @Test
    fun 轮次那句念得出请求的那一枚() {
        assertEquals("没给那一格要念默认", "未给（默认 1）", roundsRequestReading(null))
        assertEquals("生效的原样念", "原样=3", roundsRequestReading("3"))
        assertEquals("往上夹过要念出请求值与生效值", "夹过（请求=12→8）", roundsRequestReading("12"))
        assertEquals("往下夹过要念出请求值与生效值", "夹过（请求=0→1）", roundsRequestReading("0"))
        assertEquals("读不出要念出原样", "读不出（请求=abc）", roundsRequestReading("abc"))
        assertEquals(
            "四种请求念成四句各异（合并就是本批要禁的那一格）",
            4,
            listOf(roundsRequestReading(null), roundsRequestReading("3"), roundsRequestReading("12"), roundsRequestReading("abc"))
                .distinct().size,
        )
        assertTrue(
            "归属行没念请求的那一枚",
            code.contains("轮次=" + "$" + "{rounds}（请求=" + "$" + "{roundsRequest}）"),
        )
        assertTrue("判读层没走那颗抽出来的纯函数", code.contains("roundsRequest = roundsRequestReading("))
        // 对照组：夹那一档还在（它没了，上面那些句子就都是空话）
        assertTrue("对照组失效：夹那一档不见了", code.contains("coerceIn(1, 8)"))
    }
}
