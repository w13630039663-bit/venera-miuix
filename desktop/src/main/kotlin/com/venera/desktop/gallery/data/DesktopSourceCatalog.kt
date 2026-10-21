package com.venera.desktop.gallery.data

import java.io.File

/**
 * 源管理那一屏的**数据层**（批 83）：不启引擎、不开库，只回答三件事 ——
 * 有哪几颗源、每颗叫什么、哪几颗被停用。
 *
 * ## 为什么名字要自己现取
 *
 * 装载那趟确实回一个显示名（`EngineSession.load`），可"列表"这一屏为了报名字去起一颗
 * GraalJS + 整源装载（30 张封面出网）正是批 82.7 那条 `desktopComicEngineNeeded` 反着来。
 * 而对岸那颗 `JsComicSource` 的 `name` 读的就是脚本头部那一行赋值 ⇒ 这里读同一行，
 * 不另造一套口径（同一份脚本两处读成两种形状，就是"桌面看到的与手机不同"那一族成因）。
 */
object DesktopSourceNames {

    /** 顶层那一行 `name = "显示名"`；**冒号**那种（`name: "每週必看"`）是分区名，不算。 */
    private val HEADER = Regex("""^\s*name\s*=\s*"([^"]+)"""", RegexOption.MULTILINE)

    /** 取不到就交回 `null`，让调用点念 key —— 编一个站名等于替站方作伪证。 */
    fun displayNameOf(script: String): String? =
        HEADER.find(script)?.groupValues?.get(1)?.takeIf { it.isNotBlank() }

    /**
     * 按 key 现取那一颗的脚本原文（仓库目录与包内两种落点都试，**安静地**试；取不到交回 null）。
     *
     * ⚠️ 不许改走 `EngineAssets.readText`：那一颗每读一份就打一行 `D_资源 …`，
     * 列表要读 34 份 ⇒ stdout 会被 34 行资源读数糊满，而它一条都不是这一屏的账。
     *
     * 单独一颗而不并进 [displayNameOfKey]：屏上同一趟还要读第二样东西（[cookieFieldsOf] 那份名单），
     * 两处各读一遍文件就是两条轨 —— 读到一半被改的话，屏上会摆出名与名单不配套的格子。
     */
    fun scriptOf(assetDir: File, key: String): String? {
        val file = File(assetDir, "sources/$key.js")
        if (file.isFile) runCatching { return file.readText() }.getOrNull()?.let { return it }
        return runCatching {
            DesktopSourceNames::class.java.getResourceAsStream("/sources/$key.js")
                ?.use { it.readBytes().toString(Charsets.UTF_8) }
        }.getOrNull()
    }

    /** 按 key 现取那一颗的显示名（取不到交回 null，让调用点念 key）。 */
    fun displayNameOfKey(assetDir: File, key: String): String? =
        scriptOf(assetDir, key)?.let { displayNameOf(it) }

    /**
     * 手填 cookie 那一形的字段名单（`account.loginWithCookies.fields`）。
     *
     * 没有那一形 ⇒ 交回**空表**，而空表在屏上的意思是"这一源不给那一格"（批 88 那条要求：
     * 不许摆一枚按不动的框）。名单从脚本文本现取，桌面一个字都不重述 —— 站方改了字段名，
     * 屏上那几格跟着变；写死一份名单的话，改的是脚本而屏上照旧摆老几格，答案永远过不了。
     *
     * `fields` 只在 `loginWithCookies` 之后那 **400 个字符**里找：窗口不给的话，
     * 同一颗源里后面别的 `fields:`（选项列表那一族）会被当成 cookie 名单。
     */
    fun cookieFieldsOf(script: String): List<String> {
        val block = COOKIE_FIELDS.find(script)?.groupValues?.get(1) ?: return emptyList()
        return QUOTED.findAll(block).mapNotNull { it.groupValues[1].takeIf { name -> name.isNotBlank() } }.toList()
    }

    private val COOKIE_FIELDS = Regex(
        """loginWithCookies\s*:\s*\{[\s\S]{0,400}?fields\s*:\s*\[([^\]]*)\]""",
    )

    private val QUOTED = Regex(""""([^"]+)"""")

    /** 按 key 现取那份名单（脚本取不到 = 空表 = 屏上不给那一格）。 */
    fun cookieFieldsOfKey(assetDir: File, key: String): List<String> =
        scriptOf(assetDir, key)?.let(::cookieFieldsOf) ?: emptyList()
}

/** 列表里的一行。[title] 取不到名字时就是 [key]（屏上永远有字可念，且那是实话）。 */
data class DesktopSourceRow(
    val key: String,
    val title: String,
    val enabled: Boolean,
)

object DesktopSourceCatalog {

    // ⚠️ `public` 是因为装配点 `VeneraDesktop`（public）要用 `rows` / `displayNameOfKey`，
    // 而 Kotlin 不许 public 文件里的 lambda 暴露 internal object 的返回类型。
    // 同文件另两颗 `DesktopSourceNames` / `DesktopSourceSelection` 保持 internal ——
    // 它们没有被 public 签名需要，摊开只是无谓扩大面。

    /** 停用**不是删除**：那颗还在列表里，只是标着停用（否则用户会以为源被抹掉了）。 */
    fun rows(
        keys: List<String>,
        names: Map<String, String>,
        disabled: Set<String>,
    ): List<DesktopSourceRow> = keys.map { key ->
        DesktopSourceRow(key = key, title = names[key] ?: key, enabled = key !in disabled)
    }

    /**
     * stdout 那一行：清单有几颗、在用哪一颗、而**在用的那颗是不是被停用了**。
     *
     * 最后那一格必须有读数：停用了正在用的那颗时屏上照旧摆着它，没有这一句就是"看着在用而它是关的"。
     */
    fun reading(sourceKey: String, rows: List<DesktopSourceRow>): String {
        val inUseDisabled = rows.firstOrNull { it.key == sourceKey }?.enabled == false
        return "D_源管理 清单=${rows.size} 在用=$sourceKey 停用=${if (inUseDisabled) "在用" else "无"}"
    }
}

/** 选源那一下落到哪一档。四档分开，因为修法各不一样。 */
internal enum class DesktopSourcePick {
    /** 照它走（启用着的那颗）。 */
    Exact,

    /** 那一颗被停用了 ⇒ 落回第一颗启用着源。 */
    FellBack,

    /** 键压根不存在（拼错 / 撤了的那颗）⇒ 不是"停用"，念法必须不同。 */
    UnknownKey,

    /** 一颗启用着的都没有 ⇒ 不许硬挑一颗关着的当"在用"。 */
    NoneEnabled,
}

/** [key] = 接下来该装载的那一颗（`NoneEnabled` 时是空串：宁可不装，也不装一颗用户关掉的）。 */
internal data class DesktopSourceOutcome(
    val kind: DesktopSourcePick,
    val key: String,
    val reading: String,
)

/**
 * 选源的落点判断（纯函数，不碰引擎也不碰盘）。
 *
 * 这一颗是"停用"那枚开关**真生效**的地方：只在列表上标个灰而装载照旧，就是本仓那一族假开关。
 */
internal object DesktopSourceSelection {

    fun resolve(keys: List<String>, disabled: Set<String>, requested: String): DesktopSourceOutcome {
        val fallback = keys.firstOrNull { it !in disabled }
        if (requested !in keys) {
            return if (fallback == null) {
                DesktopSourceOutcome(
                    DesktopSourcePick.NoneEnabled, "",
                    "D_源 没这颗 $requested 且清单里全停用 ⇒ 没有可用的源",
                )
            } else {
                DesktopSourceOutcome(
                    DesktopSourcePick.UnknownKey, fallback,
                    "D_源 没这颗 $requested ⇒ 用 $fallback",
                )
            }
        }
        if (requested !in disabled) {
            return DesktopSourceOutcome(DesktopSourcePick.Exact, requested, "D_源 用=$requested")
        }
        return if (fallback == null) {
            DesktopSourceOutcome(
                DesktopSourcePick.NoneEnabled, "",
                "D_源 全停用（$requested 也在里面）⇒ 漫画域没有可用的源",
            )
        } else {
            DesktopSourceOutcome(
                DesktopSourcePick.FellBack, fallback,
                "D_源 回落 $requested→$fallback（$requested 已停用）",
            )
        }
    }

    /** 同一颗开关的两档：点一下关，再点一下开（叠成两份就永远关不掉）。 */
    fun toggle(disabled: Set<String>, key: String): Set<String> =
        if (key in disabled) disabled - key else disabled + key

    /**
     * 这一颗关不关得掉：已经关着的当然可以再点开，而**只剩一颗启用着的**那一颗不许关。
     *
     * 那条不是装饰：清单全关之后 [resolve] 只能交回空手（不许硬挑一颗关着的当"在用"），
     * 于是漫画域没有任何一屏装得起来 —— 让用户一按就到那一步，等于给了一枚把自己锁在门外的开关。
     */
    fun canDisable(keys: List<String>, disabled: Set<String>, key: String): Boolean =
        key in disabled || keys.count { it !in disabled } > 1
}
