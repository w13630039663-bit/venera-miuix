package com.venera.desktop.gallery.data

import com.venera.compose.data.db.TagDictRow
import com.venera.compose.data.db.TagDictionaryStore
import com.venera.compose.data.platform.PathProvider
import com.venera.compose.gallery.domain.GalleryTagCategory
import com.venera.compose.gallery.domain.GalleryTitleTag
import com.venera.desktop.desktopFailureType
import com.venera.desktop.desktopOneLineMessage
import com.venera.desktop.platform.JdbcSqliteDatabase

/**
 * 桌面侧读画廊离线词典的那颗件（批 40）：把 [DesktopTagDictionaryAsset] 那份包内副本只读打开，
 * 读法与档位判据全部走共享的 [TagDictionaryStore]（那颗本来就在桌面编译面里，
 * `desktop/src/test/…/TagDictionaryStoreTest` 一直在钉它）。
 *
 * ## 三条与 Android 侧对齐的形状，一条刻意不同
 *
 * 对齐的三条：
 * - **只读打开**（Android 走 `openReadOnlySqlDatabase`，桌面走 [JdbcSqliteDatabase.openReadOnly]）；
 * - **按名字里的行数取最新那一份资产**、副本文件名带行数（换词典必然重复制）；
 * - **没读到只记一次**（`denied` 记住是哪一种），不每次重试到无限；之后每次原样交回**同那一态**。
 * 刻意不同的一条：**所有查询都切批**。Android 侧只有 `titleTags` 走 `rowsForInChunks`，
 * 而 `translations` / `artistNames` 走的是不切批的那颗 —— 一屏几十张卡摊平去重能到上千枚名字，
 * 超了 SQLite 的变量上限不是"少查几个"而是**抛**，外面那层 `runCatching` 会把它吞成"这批名字都没译名"。
 * 桌面这边不分档，一律 [CHUNK] 切批（那是 Android 自己注释里写下的同一 hazards）。
 *
 * ## 三态而不是"null 与空表"
 *
 * 批 78 起三枚读口交 [DesktopDictRead]：命中（可以是"这批名字里确实没有"的空结果）/
 * 包里没带词典 / 读不出（带着异常那一个）。前两形在批 40 都叫 null，而契约那一侧（[DesktopGalleryTagLexicon]）
 * 把 null 压成空集时**一个字都不念** —— 于是"我们的故障"在屏上就是"这批标签没译名"。
 * 现在契约口在降级那一支必须出声（`desktopDictionaryDegradedReading`），屏上那句兜底按态分支。
 */
internal class DesktopGalleryTagDictionary(private val paths: PathProvider) {

    private val lock = Any()

    @Volatile
    private var store: TagDictionaryStore? = null

    /** 打不开/没有：只判一次（不每次重试到无限），之后每次原样交回**同一种**没读到。 */
    @Volatile
    private var denied: DesktopDictRead.Miss? = null

    /** 这批名字的中文译名（键恒小写）；命中的空结果 = 这批名字里确实没有，`Miss` = 我们这侧没读到。 */
    fun translations(names: List<String>): DesktopDictRead<Map<String, String>> = when (val rows = query(names)) {
        is DesktopDictRead.Miss -> rows
        is DesktopDictRead.Hit -> {
            val out = LinkedHashMap<String, String>()
            for (row in rows.value) {
                val cn = row.cn?.takeIf { it.isNotBlank() } ?: continue
                out[row.name] = cn
            }
            DesktopDictRead.Hit(out)
        }
    }

    /** 这批名字里哪些是**画师档**（站方表里 `category = 1`）；判据与对岸同一套。 */
    fun artistNames(names: List<String>): DesktopDictRead<Set<String>> = when (val rows = query(names)) {
        is DesktopDictRead.Miss -> rows
        is DesktopDictRead.Hit ->
            DesktopDictRead.Hit(rows.value.filter { it.category == GalleryTagCategory.ARTIST }.map { it.name }.toSet())
    }

    /**
     * 这批名字里能当卡片标题用的那些，连同档位；没读到 ⇒ `Miss`（整批交出去，不交半份）。
     *
     * 档位表与 Android 侧 `GalleryTagDictionary.titleTags` 同一套：作品(3)/角色(4)/画师(1) 无条件进
     * （没译名就用原词，人名与罗马字那类本来就没有公认中文名），通用(0) **只在有译名时**进
     * （否则标题行会摆出一串 `shirt_lift` 这种原词），元数据(5) 与认不出的档位不进。
     */
    fun titleTags(names: List<String>): DesktopDictRead<Map<String, GalleryTitleTag>> = when (val rows = query(names)) {
        is DesktopDictRead.Miss -> rows
        is DesktopDictRead.Hit -> {
            val out = LinkedHashMap<String, GalleryTitleTag>()
            for (row in rows.value) {
                val cn = row.cn?.takeIf { it.isNotBlank() }
                val tag = when (row.category) {
                    GalleryTagCategory.COPYRIGHT, GalleryTagCategory.CHARACTER, GalleryTagCategory.ARTIST ->
                        GalleryTitleTag(label = cn ?: row.name, category = row.category)

                    GalleryTagCategory.GENERAL -> cn?.let { GalleryTitleTag(label = it, category = row.category) }

                    else -> null
                } ?: continue
                out[row.name] = tag
            }
            DesktopDictRead.Hit(out)
        }
    }

    /** 一次切批主查；三态各归各的：命中给行、包里没带给 `Absent`、打开或查询抛了给 `Unreadable(那个异常)`。 */
    private fun query(names: List<String>): DesktopDictRead<List<com.venera.compose.data.db.TagDictRow>> =
        when (val opened = access()) {
            is DesktopDictRead.Miss -> opened
            is DesktopDictRead.Hit -> runCatching { opened.value.rowsForInChunks(names, CHUNK) }.fold(
                onSuccess = { DesktopDictRead.Hit(it) },
                onFailure = {
                    DesktopGalleryFailureLog.warn(TAG, "查画廊标签词典失败：${it.message}")
                    DesktopDictRead.Miss(DesktopDictWhy.Unreadable(it))
                },
            )
        }

    private fun access(): DesktopDictRead<TagDictionaryStore> {
        store?.let { return DesktopDictRead.Hit(it) }
        denied?.let { return it }
        synchronized(lock) {
            store?.let { return DesktopDictRead.Hit(it) }
            denied?.let { return it }
            val listed = DesktopTagDictionaryAsset.assetOf(DesktopTagDictionaryAsset.classpathListing())
            val miss = listed as? DesktopDictRead.Miss
            if (miss != null) {
                denied = miss
                val reason = miss.why as? DesktopDictWhy.Unreadable
                if (reason != null) {
                    DesktopGalleryFailureLog.warn(
                        TAG,
                        "数不出包里的词典清单（数据目录 ${paths.dataRoot.path}）：${reason.error.message}",
                    )
                }
                return miss
            }
            val assetName = (listed as DesktopDictRead.Hit).value
            val opened = runCatching {
                TagDictionaryStore(JdbcSqliteDatabase.openReadOnly(DesktopTagDictionaryAsset.materialize(paths, assetName)))
            }
            val failure = opened.exceptionOrNull()
            if (failure != null) {
                DesktopGalleryFailureLog.warn(
                    TAG,
                    "打不开画廊标签词典，标签译名/画师档/标题档三枚都只能交空（数据目录 ${paths.dataRoot.path}）：${failure.message}",
                )
                val miss = DesktopDictRead.Miss(DesktopDictWhy.Unreadable(failure))
                denied = miss
                return miss
            }
            val fresh = opened.getOrThrow()
            store = fresh
            return DesktopDictRead.Hit(fresh)
        }
    }

    private companion object {
        const val TAG = "DesktopGalleryTagDictionary"

        /** 一批查多少枚：与 Android 那颗同一取中（400 ≪ SQLite 变量上限 999，上千枚 → 3 批左右）。 */
        const val CHUNK = 400
    }
}

/**
 * 词典那一问的三种结果（批 78）：命中 / 没命中读口而原因是"包里没带" / 原因是"读不出（带着那一个异常）"。
 *
 * _covariant_ 是有意的：`Miss` 是 `DesktopDictRead<Nothing>`，任何一层把 `Miss` 原样交回都不用重造。
 */
internal sealed interface DesktopDictRead<out T> {
    /** 读到了。`value` 可以是空集/空映象 —— 那一句才是"这批名字里确实没有"。 */
    data class Hit<out T>(val value: T) : DesktopDictRead<T>

    /** 没读到，且说得出是哪一种没读到。 */
    data class Miss(val why: DesktopDictWhy) : DesktopDictRead<Nothing>
}

/** 两种"没读到"，屏上与日志里各念各的（合并就是本批要禁的那一格）。 */
internal sealed interface DesktopDictWhy {
    /** 包里没带可用的词典资产（构建脚本没 include、或资产名里认不出行数）。 */
    data object Absent : DesktopDictWhy

    /** 有而读不动：清单数不出来、库打不开、或查询抛了 —— 带着那一个异常。 */
    data class Unreadable(val error: Throwable) : DesktopDictWhy
}

/** 三枚读口在屏上/日志里的名字；措辞只住这一颗件（第二份就是把同一件事说两遍）。 */
internal const val DESKTOP_DICT_MEMBER_TRANSLATIONS: String = "译名"
internal const val DESKTOP_DICT_MEMBER_ARTISTS: String = "画师档"
internal const val DESKTOP_DICT_MEMBER_TITLES: String = "标题档"

/**
 * 恒空返回那一句**出声**的措辞：`D_词典 枚=… 态=…`。
 *
 * ⚠️ 只带 `型` 与过完凭据剔除的那一行消息（批 53 那两颗件），不带栈 —— 这一句每次降级都要念，
 * 栈留在崩溃那一族里；也不许把整段 classpath 之外的用户数据原样抄进日志。
 */
internal fun desktopDictionaryDegradedReading(member: String, why: DesktopDictWhy): String = when (why) {
    DesktopDictWhy.Absent -> "D_词典 枚=$member 态=包里没有"
    is DesktopDictWhy.Unreadable ->
        "D_词典 枚=$member 态=读失败 型=${desktopFailureType(why.error)} 因=${desktopOneLineMessage(why.error)}"
}
