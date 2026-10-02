package com.venera.desktop

/**
 * 取证参数的**诚实形状**（Task 6 第 4 件）。
 *
 * 改前的两颗毛病都是"看起来像开关但其实不是"：
 * - `args.contains("--autofav")` 只看给没给，`-Pautofav=0` 一样点亮 ⇒ 关掉它得靠"不给参数"，
 *   而"给了 0"却是开 —— 同一个参数两种口径。
 * - 两参同给时 `--favcheck` 先 `exitProcess`，`--autofav` 被**静默吃掉**，日志里看不见这回事。
 *
 * 现在：**值可判**（`1/true/on` 开、`0/false/off` 关，读不懂的值按关处理并如实说读不懂）、
 * **同给如实说一句**、**拼错的一律报未识别**。判读逻辑单独一颗对象是为了让它**可测**
 * （`main()` 要建窗口、要数据目录，测试碰不到；这里每条都能直接断返回值）。
 */
internal object ForensicFlags {

    /** 认识的参数名（带值也认：`--autofav=1`） */
    val KNOWN = listOf("--proxy", "--shot", "--autofav", "--favcheck")

    private val ON = setOf("1", "true", "on")
    private val OFF = setOf("0", "false", "off")

    /**
     * 某个开关的判读。
     * - `null` = 这个参数**没给**（本轮与它无关，不打读数）
     * - 只给名字不带值（`--autofav`）= 开
     * - 带值：`1/true/on` 开、`0/false/off` **关**（关照样打一条"参数给了但值为关"，不装成没给）
     * - 值读不懂：按**关**处理并打"值无法判读"（不猜成开）
     */
    fun state(args: List<String>, name: String): Boolean? {
        val given = args.firstOrNull { it == name || it.startsWith("$name=") } ?: return null
        val raw = given.removePrefix("$name=").takeIf { it != given }
        if (raw == null) {
            println("D_取证参数 $name 已给（不带值），按开处理")
            return true
        }
        val value = raw.trim().lowercase()
        return when {
            value in ON -> {
                println("D_取证参数 $name 开（值=$raw）")
                true
            }

            value in OFF -> {
                println("D_取证参数 $name 参数给了但值为关（值=$raw），本轮不执行")
                false
            }

            else -> {
                println("D_取证参数 $name 值无法判读（值=$raw），按关处理")
                false
            }
        }
    }

    /** 拼错的 / 不认识的 `--xxx`（调用方逐条打读数，不许静默忽略） */
    fun unknown(args: List<String>): List<String> =
        args.filter { it.startsWith("--") && KNOWN.none { name -> it == name || it.startsWith("$name=") } }

    /**
     * 两参同给的实话。返回 null 表示没同给（不必说）。
     * favcheck 为开时它会先读回再退出进程，**autofav 那一步根本不会执行** —— 这句就是原来缺的。
     */
    fun bothGivenNote(autofav: Boolean?, favcheck: Boolean?): String? {
        if (autofav == null || favcheck == null) return null
        return if (favcheck) {
            "D_取证参数 --autofav 与 --favcheck 同给，本轮只跑 favcheck（它读回后就退出进程，autofav 的写动作不执行）"
        } else {
            "D_取证参数 --autofav 与 --favcheck 同给，favcheck 的值为关 ⇒ 不提前退出，本轮跑 autofav"
        }
    }
}
