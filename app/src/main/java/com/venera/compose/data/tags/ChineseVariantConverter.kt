package com.venera.compose.data.tags

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 字级简繁转换。数据源 `assets/opencc.txt` —— 官方 OpenCC 字符表（原样从 master 分支
 * 的 Flutter 侧带过来，git blob 与上游逐字节相同），每行是「简体码点 + 繁体码点」两个字符，
 * `#` 开头是注释。
 *
 * **为什么只是字级、并且只能到此为止**：这张表解决的是「同一个字的两种写法」（蘿↔萝），
 * 解决不了「同一个词的两种写法」（`太陽眼鏡` vs `太阳镜`）。实测对 EhTagTranslation
 * 六类题材字典中简繁译名不同的 756 条，能归一 **639 条 = 84.5%**，剩下 117 条绝大多数
 * 是词级差异。换表没有意义（与上游同源同尺寸），所以调用方**不得**宣称「已合并全部同义写法」。
 *
 * 表未加载时所有转换按**恒等**返回：宁可少归一，不可抛错 —— 标签统计与搜索过滤都不该
 * 因为一个可选的语言资源没读上来而整块不显示。
 */
class ChineseVariantConverter private constructor(context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var simplifiedToTraditional: Map<Int, Int> = emptyMap()

    @Volatile
    private var traditionalToSimplifiedTable: Map<Int, Int> = emptyMap()

    private val _ready = MutableStateFlow(false)

    /** 表是否已就绪。没就绪时转换按恒等返回，所以调用方**可以**不等它。 */
    val ready: StateFlow<Boolean> = _ready.asStateFlow()

    init {
        // 与 TagTranslationManager 同一套路：构造即在 IO 线程自加载，调用方永远只调纯函数，
        // 不必操心「该在哪个线程读 asset」。表很小（29KB / 3980 行）。
        scope.launch { load(context) }
    }

    private fun load(context: Context) {
        try {
            val lines = context.assets.open(ASSET).bufferedReader().use { it.readLines() }
            val (s2t, t2s) = parseLines(lines.asSequence())
            simplifiedToTraditional = s2t
            traditionalToSimplifiedTable = t2s
            _ready.value = true
            Log.i(TAG, "Loaded $ASSET: ${s2t.size} pairs, ${t2s.size} traditional keys.")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load $ASSET: ${e.message}")
        }
    }

    /** 繁体→简体。逐码点替换，表里没有的字符原样保留。 */
    fun traditionalToSimplified(text: String): String =
        convertCodePoints(text, traditionalToSimplifiedTable)

    /** 文本里是否含有被表认识为繁体的码点。 */
    fun hasChineseTraditional(text: String): Boolean =
        containsMappedCodePoint(text, traditionalToSimplifiedTable)

    /** 简体→繁体（标签显示语言为繁体时用；当前无调用方，留着是因为表本身是双向的）。 */
    fun simplifiedToTraditional(text: String): String =
        convertCodePoints(text, simplifiedToTraditional)

    companion object {
        private const val TAG = "ChineseVariantConverter"
        private const val ASSET = "opencc.txt"

        @Volatile
        private var INSTANCE: ChineseVariantConverter? = null

        fun getInstance(context: Context): ChineseVariantConverter {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ChineseVariantConverter(context.applicationContext).also { INSTANCE = it }
            }
        }

        /**
         * 解析对照表。一行的码点数**必须正好是 2**，其余（注释、空行、词级条目）全部跳过 ——
         * 与 master 侧 `opencc.dart` 的 `line.length != 2` 判据一致。
         */
        internal fun parseLines(lines: Sequence<String>): Pair<Map<Int, Int>, Map<Int, Int>> {
            val s2t = HashMap<Int, Int>(4096)
            val t2s = HashMap<Int, Int>(4096)
            for (line in lines) {
                val trimmed = line.trim()
                if (trimmed.isEmpty() || trimmed.startsWith("#")) continue
                val points = codePointsOf(trimmed)
                if (points.size != 2) continue
                val simplified = points[0]
                val traditional = points[1]
                s2t[simplified] = traditional
                t2s[traditional] = simplified
            }
            return s2t to t2s
        }
    }
}

/**
 * 逐码点走 [table] 替换。用码点而不是 `Char`：CJK 扩展 B 及平面 2 以上的字在 Kotlin 里
 * 是**代理对**，按 Char 迭代会把一个字劈成两半，替换后拼不回原字。
 */
internal fun convertCodePoints(text: String, table: Map<Int, Int>): String {
    if (text.isEmpty() || table.isEmpty()) return text
    val out = StringBuilder(text.length)
    var i = 0
    while (i < text.length) {
        val cp = text.codePointAtCompat(i)
        val mapped = table[cp]
        out.appendCodePoint(mapped ?: cp)
        i += Character.charCount(cp)
    }
    return out.toString()
}

internal fun containsMappedCodePoint(text: String, table: Map<Int, Int>): Boolean {
    if (text.isEmpty() || table.isEmpty()) return false
    var i = 0
    while (i < text.length) {
        val cp = text.codePointAtCompat(i)
        if (table.containsKey(cp)) return true
        i += Character.charCount(cp)
    }
    return false
}

internal fun codePointsOf(text: String): List<Int> {
    val res = ArrayList<Int>(text.length)
    var i = 0
    while (i < text.length) {
        val cp = text.codePointAtCompat(i)
        res.add(cp)
        i += Character.charCount(cp)
    }
    return res
}

/** `String.codePoints()` 要 API 24+，这里用等价的 `Character.codePointAt`（API 1 起有）。 */
private fun String.codePointAtCompat(index: Int): Int = Character.codePointAt(this, index)
