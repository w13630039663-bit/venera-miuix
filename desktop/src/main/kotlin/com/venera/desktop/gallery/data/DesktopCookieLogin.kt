package com.venera.desktop.gallery.data

import com.venera.engineprobe.DesktopCookieJar
import com.venera.engineprobe.DesktopJsHost
import com.venera.engineprobe.EngineSession
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 手填 cookie 那一形的**跑一趟**（批 88·下半）：起宿主 → 装那颗源 → 交**源自己**校验 →
 * 现读盘上那份回核 → 关宿主。
 *
 * ## 为什么要单独一颗件而不写在源管理那一屏里
 *
 * 那一屏今天刻意**不起引擎**（`desktopComicEngineNeeded` 里 SOURCES 那格是 false —— 白起一颗 GraalJS
 * 加整源装载是代价不是准备）。这一趟是用户按下「登录」那一下才付的钱，得跟"列表只是读盘上清单"
 * 分开写；混在一处就会有人为了测登录而把整屏的装载都拖起来。
 *
 * ## 回核为什么读**盘**而不是读刚才那颗罐
 *
 * 源回 `true` 只说明站方认了这批凭据；「重启还带不带得上」是另一件事，而本批要防的正是
 * 那两半不同轨（屏上念「已登录」而重启后请求空手）。所以数枚数用的是**另起一颗只读实例**去读
 * `DesktopCookieJar` 落的那份文件 —— 内存里那份是刚才那一趟写的，拿它作证等于自己给自己签字。
 */
internal data class DesktopCookieOutcome(
    /** 源那句 `validate` 的回话（`=== true` 才算过，与对岸 `JsComicSource.validateCookies` 同一判式）。 */
    val ok: Boolean,
    /** 没过时屏上要念的那一句（源自己的说法优先，桌面不重述判据）。 */
    val note: String,
    /** 脚本自己那份字段名单（回核数的是这几枚名字落了几个）。 */
    val fields: List<String>,
    /** 盘上那份里每个名字落了几枚（键 = cookie 名）。 */
    val onDisk: Map<String, Int>,
)

internal object DesktopCookieLogin {

    /**
     * 用户敲的那几枚 → 源那句 `validate(values)` 的实参表：**按名单次序**摊成一串。
     *
     * 为什么单独一颗：顺序是源脚本认的口径（`fields[i]` 与 `values[i]` 一一对应，ehentai 的
     * `validate` 就按 `fields[i]` 取名字）。屏上格子是竖着排的，而字典无序 —— 直接从
     * `mapValues` 那种形状里取值就会把四枚装成任意一套，源那边看着像"填了但不对"。
     */
    fun payload(fields: List<String>, draft: Map<String, String>): List<String> =
        fields.map { draft[it].orEmpty() }

    suspend fun run(
        assetDir: File,
        dataDir: File,
        proxy: String?,
        sourceKey: String,
        values: List<String>,
        seedCookies: List<String>,
    ): DesktopCookieOutcome {
        val login = withContext(Dispatchers.IO) {
            runCatching {
                val host = DesktopJsHost(assetDir, dataDir, proxy, seedCookies)
                val engine = EngineSession(host, assetDir)
                try {
                    engine.load(sourceKey)
                    engine.loginWithCookies(values)
                } finally {
                    // 关引擎就是关宿主（`EngineSession.close` 走 `host.close()`）：
                    // 这里不留第二颗没人关的 GraalJS context（批 80 那一格泄漏的同族）。
                    engine.close()
                }
            }
        }
        if (login.isFailure) {
            val err = login.exceptionOrNull()
            return DesktopCookieOutcome(
                ok = false,
                note = "这一趟没跑成：${err?.let { "${it::class.java.simpleName}: ${it.message?.take(160)}" } ?: "未知成因"}",
                fields = emptyList(),
                onDisk = emptyMap(),
            )
        }
        val result = login.getOrThrow()
        val onDisk = DesktopCookieJar(DesktopCookieJar.fileIn(dataDir)).tally(result.fields)
        return DesktopCookieOutcome(result.ok, result.note, result.fields, onDisk)
    }

    /**
     * 屏上那一句。三档两两不同，而**中间那一档是本批存在的理由**：
     * 源认了而盘上一枚没落 = 重启就丢，那一句绝不许被"登录成功"盖掉。
     *
     * ⚠️ 这一句要上屏，所以里面不许有 `D_` 那类内部读数、也不许有实现黑话。
     */
    fun screenNote(outcome: DesktopCookieOutcome): String {
        val landed = outcome.onDisk.values.sum()
        return when {
            !outcome.ok -> "没登上：${outcome.note}"
            landed == 0 -> "源认了这批凭据，可本机一份都没落下来 —— 重启就带不上，先别当已经登录"
            else -> "已登录：这批凭据落在本机那份里共 $landed 枚，重启也带得上"
        }
    }

    /** stdout 那一行：登录态、名单、盘上每枚名字各落了几颗，全部从状态现取。 */
    fun reading(sourceKey: String, outcome: DesktopCookieOutcome): String {
        val detail = outcome.fields.joinToString(",") { name -> "$name=${outcome.onDisk[name] ?: 0}" }
        return "D_手填登录 源=$sourceKey 源答=${if (outcome.ok) "过" else "没过"} " +
            "名单=${outcome.fields.size} 盘上=[$detail] 计=${outcome.onDisk.values.sum()} logged=${if (outcome.ok) 1 else 0}"
    }
}
