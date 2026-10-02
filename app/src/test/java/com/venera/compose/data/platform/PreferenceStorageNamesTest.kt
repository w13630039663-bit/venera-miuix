package com.venera.compose.data.platform

import com.venera.compose.testsupport.RepoSources
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 偏好**存储名**的化石表 + 内联字面量的守卫。
 *
 * 为什么值得钉住：一个存储名对应一个 SharedPreferences 文件。改错一个字（哪怕只是大小写不同）
 * 不报错、不崩溃 —— 新名字落到一个空文件上，老文件还在原地没人读，
 * 症状是「用户那个模块的设置全回到默认」，而且只有用户自己升级后发现。
 * 2026-10-03 审计实测 13 个存储名里有 10 个当时是内联字面量写的、另有 3 处各自私有 `const PREFS_NAME`。
 *
 * 两类断言都要：
 * 1. **值逐字不变**：收口只许改「名字从哪来」，不许改「名字是什么」；
 * 2. **不许长回内联字面量**：编译器管不到「新写了一处」，只能扫源码。
 */
class PreferenceStorageNamesTest {

    private val allNames = listOf(
        PreferenceKeys.PREFS_NAME,
        PreferenceKeys.PREFS_SOURCES,
        PreferenceKeys.PREFS_GUARD,
        PreferenceKeys.PREFS_UA_POLICY,
        PreferenceKeys.PREFS_COOKIES,
        PreferenceKeys.PREFS_COMIC_METRICS,
        PreferenceKeys.PREFS_COMIC_LIST_PRESENTATION,
        PreferenceKeys.PREFS_HOME_RECOMMEND_CACHE,
        PreferenceKeys.PREFS_GALLERY_SEARCH,
        PreferenceKeys.PREFS_WEBDAV,
        PreferenceKeys.PREFS_COPY_MANGA,
        PreferenceKeys.PREFS_GELBOORU_ACCOUNT,
        PreferenceKeys.PREFS_SAUCENAO_ACCOUNT,
    )

    @Test
    fun `存储名的值逐字等于收口前的实测值`() {
        assertEquals(
            listOf(
                "venera_preferences",
                "venera_sources",
                "venera_guard_prefs",
                "venera_ua_policy",
                "venera_cookies",
                "comic_source_metrics",
                "comic_list_presentation",
                "home_recommend_cache",
                "venera_gallery_search",
                "venera_webdav_prefs",
                "venera_source_copy_manga",
                "venera_gelbooru_account",
                "venera_saucenao_account",
            ),
            allNames,
        )
    }

    @Test
    fun `十三枚存储名两两不等`() {
        // 撞名 = 两个模块往同一个文件里写键，后写的一方静默覆盖前一方。
        assertEquals(
            "存储名撞车：${allNames.groupingBy { it }.eachCount().filterValues { it > 1 }.keys}",
            allNames.size,
            allNames.toSet().size,
        )
    }

    @Test
    fun `除白名单两处外不许再用内联字面量开存储`() {
        // 白名单两条：
        //  - ContentGuardManager —— 冻结文件，本轮不牵它进来（FREEZE-STATEMENT:18 只允许修实际
        //    Bug，而这里今天没有可指认的故障）；它的值由上面那条化石断言钉住。
        //  - AndroidKeyValueStore.kt —— 平台实现本身，它必须把名字交给 getSharedPreferences。
        val whitelist = setOf(
            "com/venera/compose/security/guard/ContentGuardManager.kt",
            "com/venera/compose/data/platform/android/AndroidKeyValueStore.kt",
        )
        val hits = RepoSources.allMainLines().filter { (rel, _, line) ->
            val t = line.trim()
            rel !in whitelist &&
                // 注释里举的反例（本文件与 PreferenceKeys 的 KDoc 都引了旧写法）不算回潮。
                !t.startsWith("//") && !t.startsWith("*") && !t.startsWith("/*") &&
                (line.contains("AndroidKeyValueStore(") || line.contains("getSharedPreferences(")) &&
                line.contains('"')
        }
        // 写成 PreferenceKeys.X 的行不在括号里带引号，不会误报；带引号就说明有人又把存储名写死了。
        assertTrue(
            "内联存储名回潮：\n" + hits.joinToString("\n") { "${it.first}:${it.second} ${it.third.trim()}" },
            hits.isEmpty(),
        )
    }
}
