package com.venera.compose.data.network

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

/**
 * 过盾的**只读取证**记录。
 *
 * 为什么要这一份：`onBypassSuccess` 里那句日志 `"... (includes cf_clearance)"` 是**无条件**打的，
 * 它断言了一件从来没人验过的事 —— 而 `cf_clearance` 是 HttpOnly，
 * `CookieManager.getCookie()` 按规范取不到 HttpOnly 值。两者必有一个是假的：
 * 要么那行日志一直是假的，要么"过盾成功"之后请求照样 403。
 * 现在的证据只有"用户说反搜那面墙从来没见过结果"，指不出断在哪一环。
 *
 * 本机应用层 logcat 读不到（`Log.i` 一条都出不来，见交接手册里那条实测），
 * 所以落到 `filesDir` 的一个文本文件，`adb exec-out run-as <pkg> cat files/cf_bypass_evidence.txt` 能读。
 *
 * ⚠️ 这一层**只记录，不参与任何判定**：把它整个删掉，过盾行为逐字不变。
 * 这也是它单独成一个文件的原因 —— 取证拿到数之后，这一份应该整体撤掉，而不是留在仓里长草。
 */
internal object CfBypassEvidence {

    private const val FILE_NAME = "cf_bypass_evidence.txt"

    /** 超过这个长度就重开一份：要的是"最近这一次"的证据，不是历史流水。 */
    private const val MAX_BYTES = 32 * 1024

    private val executor = Executors.newSingleThreadExecutor()

    private val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)

    /**
     * 记一行。[line] 里请带够能对照的字段（host、条数、名字清单、HTTP code）——
     * 一句"过盾成功"没有任何取证价值。
     */
    fun record(context: Context, line: String) {
        val appContext = context.applicationContext
        executor.execute {
            runCatching {
                val file = File(appContext.filesDir, FILE_NAME)
                val header = if (file.exists() && file.length() > MAX_BYTES) {
                    "# 证据记录超出上限，旧内容已丢弃\n"
                } else {
                    ""
                }
                file.appendText("${header}${stamp.format(Date())} $line\n")
            }
        }
    }

    /**
     * 把 `CookieManager.getCookie()` 那串收成**名字清单**（不带值 —— 值里可能含会话凭据，
     * 不该被顺手写进一个能被 `run-as` 读到的文件里）。
     *
     * **只记名字，不记值** —— 值里可能含会话凭据，不该被顺手写进一个 `run-as` 就能读到的文件里。
     *
     * 返回空清单 = `getCookie` 那串本身是空的（null 或全空白），
     * 与"拿到了一些名字但没有 cf_clearance"是两种完全不同的读数：前者是 WebView 与 OkHttp
     * 两套 cookie 存储之间根本没通，后者才是 HttpOnly 取不到。
     */
    fun cookieNames(cookieString: String?): List<String> =
        cookieString?.split(';')
            ?.map { it.substringBefore('=').trim() }
            ?.filter { it.isNotEmpty() }
            .orEmpty()
}
