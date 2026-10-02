package com.venera.compose.data.db

import com.venera.compose.feature.favoriteimages.ImageFavoriteBackupRow
import com.venera.compose.feature.favoriteimages.ImageFavoriteBackupRows
import org.junit.Assert.assertEquals
import org.junit.Test
import java.lang.reflect.Modifier

/**
 * 插图收藏备份行的两份定义之间没有编译期约束，这里用反射把它们对齐断死。
 *
 * 背景（4b-1 评审 Minor 7）：导出链是
 * `FavoriteImagesStore.backupFields()` → [FavoriteImageBackupFields]
 * →（`FavoriteImagesManager.exportBackupRows` 的手写逐参映射）→ [ImageFavoriteBackupRow]
 * →（`ImageFavoriteBackupRows.toMap` 的手写逐键映射）→ 归档 JSON。
 * 两份 data class 的字段名靠逐字相同维系：任何一侧单独加一列（带默认值时手写映射照样编译），
 * 导出侧就会**静默漏掉那一列**，恢复出的归档少字段且不报错。本文件把这条线钉成用例：
 *  - 字段名集不对齐 ⇒ 第一条红；
 *  - 两边一起漂移（同时加列但 toMap 忘了写键）⇒ 第三条红。
 * 为什么不直接把那段手写逐参映射跑通来测：`FavoriteImagesManager.exportBackupRows` 是 suspend
 * 且持 Context（管理器只能在 Android 上实例化，仓内无 Robolectric），JVM 单测进不去 ——
 * 所以钉的是**两份字段定义 + `toMap` 键集**这三段可静态反射对齐的事实。
 *
 * 为什么放 `:app` 的测试面而不是 `:desktop`：[FavoriteImageBackupFields] 在桌面编译面，
 * 而 `ImageFavoriteBackupRow`/`ImageFavoriteBackupRows` 住在 `feature/favoriteimages/FavoriteImagesManager.kt`
 * ——那颗业务文件按排除口径不进桌面源码集，`:desktop:test` 根本看不到它，只能放在两边同模块的这里。
 */
class FavoriteImageBackupFieldsParityTest {

    /** data class 的构造属性即同名的私有 final 实例字段（排除合成与静态成员）。 */
    private fun properties(clazz: Class<*>): List<Pair<String, Class<*>>> =
        clazz.declaredFields
            .filterNot { it.isSynthetic || Modifier.isStatic(it.modifiers) }
            .map { it.name to it.type }

    private fun camelToSnake(name: String): String =
        name.replace(Regex("([a-z0-9])([A-Z])")) { "${it.groupValues[1]}_${it.groupValues[2]}" }.lowercase()

    @Test
    fun `两份备份行定义的字段名集逐一对齐`() {
        val storeFields = properties(FavoriteImageBackupFields::class.java).map { it.first }.toSet()
        val managerFields = properties(ImageFavoriteBackupRow::class.java).map { it.first }.toSet()
        assertEquals(
            "FavoriteImageBackupFields 与 ImageFavoriteBackupRow 的字段集漂移了——" +
                "备份导出链上的手写逐参映射会在这一列上静默漏值，加列必须两边一起加并补映射",
            managerFields,
            storeFields,
        )
        // 「恰好七个」也要钉住：只断两边相等时，两份定义一起多长出一列仍会双双通过，
        // 而那种列在归档与 BACKUP_SELECT 里未必存在，属于没人想看到的默认状态。
        assertEquals(7, storeFields.size)
    }

    @Test
    fun `两份定义的字段类型逐列一致`() {
        val storeTypes = properties(FavoriteImageBackupFields::class.java).associate { it.first to it.second }
        val managerTypes = properties(ImageFavoriteBackupRow::class.java).associate { it.first to it.second }
        // 名字对上了但一侧把 pageIndex 换成 Long、createdAt 换成 String，手写映射靠隐式拓宽编得过去，
        // 归档文本的读法（int/long 判据在 JsonValues 里）却按另一套走——所以类型也要逐个对死。
        for ((name, expected) in managerTypes) {
            assertEquals("字段「$name」的类型在两份定义间不一致", expected, storeTypes[name])
        }
    }

    @Test
    fun `编解码 toMap 的键集由行定义的字段名逐一对出`() {
        // 归档列名就是字段名的 snake_case 形式（ImageFavoriteBackupRows 的既有口径）。
        // 有人给 ImageFavoriteBackupRow 加了列而忘了往 toMap 写键，这里立刻红——
        // 那一列会从导出侧静默消失，正是本文件存在的理由。
        val row = ImageFavoriteBackupRow(
            comicId = "c1",
            comicTitle = "标题",
            sourceName = "jm",
            chapterTitle = "第1话",
            pageIndex = 3,
            imageUrl = "https://example.com/1.jpg",
            createdAt = 123L,
        )
        val keys = ImageFavoriteBackupRows.toMap(row).keys.toSet()
        val expected = properties(ImageFavoriteBackupRow::class.java)
            .map { camelToSnake(it.first) }
            .toSet()
        assertEquals("toMap 的 JSON 键与行定义字段不再一一对应", expected, keys)
    }
}
