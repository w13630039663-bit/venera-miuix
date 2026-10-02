package com.venera.compose.data.api

import com.venera.compose.security.guard.GuardRule
import com.venera.compose.source.model.Comic
import com.venera.compose.source.model.ExplorePagePart
import kotlinx.coroutines.flow.StateFlow

/**
 * 「内容守卫」的**消费面**唯一出处 —— 页面与 ViewModel 只许通过它问分级与屏蔽。
 *
 * 为什么要这一颗：`ContentGuardManager` 是 515 行的单例，公开面 16 个成员，其中
 * `loadRules()`、`registerSourceNameAliases()`、`isComicBlocked()` 三颗的调用面分别是
 * 「只有它自己」、「只有装配根 `VeneraApp.kt:50`」、「UI 零命中」。把这 16 个成员原样抄进
 * 契约等于没做收口 —— 这里只留 UI 真正需要的 9 条，实现类继续当实现类。
 *
 * 与 `data/db/DatabasePorts.kt:32-44` 的 `FavoritesPreferences` 同一条口径：**接口的宽度由
 * 消费面决定，不由实现决定**。
 *
 * ⚠️ 两条不许在实现里放宽的语义：
 * 1. `StateFlow` 成员必须**交回同一个实例**（适配器只做 `get() = manager.xxx`）。换一份流
 *    就等于给同一份偏好开两个订阅口 —— 页面的 `collectAsState()` 会读到不会随写入更新的那一个。
 * 2. `maskStateFor` 的三个重载**不许合一**。它们走的是不同的判定链：带 `sourceKey` 那一条要过
 *    源级别名解析与 LRU，不带的那一条刻意不进缓存（见 `ContentGuardManager.kt:390-404` 的注释）。
 */
interface ContentGuard {

    /** 分级遮罩模式："OFF" / "BLUR" / "HIDE"。 */
    val nsfwMaskMode: StateFlow<String>

    /** 「屏蔽 AI 生成漫画」开关，与上面那三档**正交**（一个开关管两件事 = 假开关）。 */
    val blockAiComics: StateFlow<Boolean>

    fun setNsfwMaskMode(mode: String)

    fun setBlockAiComics(enabled: Boolean)

    /** 无源身份场景（画廊、本地收藏等非 [Comic] 模型）的遮蔽判定。 */
    fun maskStateFor(
        title: String,
        author: String = "",
        tags: List<String> = emptyList(),
        comicId: String = "",
        description: String = "",
    ): String

    /** 带源身份的遮蔽判定（走源级预设与 LRU 缓存）。 */
    fun maskStateFor(
        sourceKey: String,
        title: String,
        author: String = "",
        tags: List<String> = emptyList(),
        comicId: String = "",
        description: String = "",
    ): String

    /** 列表场景的入口（走 LRU 缓存）。 */
    fun maskStateFor(comic: Comic): String

    /** HIDE 档的数据层剔除；BLUR 档条目必须保留交卡片打码。 */
    fun filterComics(comics: List<Comic>): List<Comic>

    /** 探索页分区的剔除（逐分区过滤，空分区整块剔除，避免留"孤儿标题"）。 */
    fun filterExploreParts(parts: List<ExplorePagePart>): List<ExplorePagePart>
}

/**
 * 「用户屏蔽规则」这本账的读写口。与 [ContentGuard] 分成两颗是因为消费面只有设置页
 * 与两处长按入口需要它，而列表页每一张卡片都要问遮蔽判定 —— 一颗契约同时背两个宽度，
 * 迟早有人为了少一个参数把整颗塞进另一个作用域。
 */
interface GuardRuleBook {

    val rules: StateFlow<List<GuardRule>>

    /**
     * 存**原值**、由调用点去重；写不进去回报 `-1L`（长按"屏蔽本作"据此如实提示失败）。
     * 这条签名是 `FREEZE-STATEMENT.md:122` 定过的口径，不许改成 `Result` 或 `Boolean`。
     */
    suspend fun addRule(type: String, pattern: String, isRegex: Boolean = false): Long

    suspend fun deleteRule(id: Long): Boolean
}
