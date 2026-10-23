package com.venera.compose.gallery.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.venera.compose.gallery.data.GallerySite
import com.venera.compose.gallery.domain.sanitizeGalleryTagName

/**
 * 大图页点标签 → 一级画廊切进搜索态的**交接槽**。
 *
 * 为什么需要这么一层：大图页是独立 Activity（实时背景模糊要跨窗口，见 `GalleryPostActivity`），
 * 而画廊的搜索状态在 MainActivity 那一侧的 [GallerySearchViewModel] 里 —— 两者没有共同的
 * ViewModel 作用域，也不是 NavHost 目的地，导航参数够不着。方向与 [GalleryFlyIn] 相反：
 * 那张位图是"一级递给二级"，这一条是"二级递回一级"。
 *
 * 做成「取用一次即清」是刻意的：消费方被切走再回来时组合会重建（见记忆「导航条目会重建组合」），
 * 不清槽位就会把同一次搜索再发一遍。安全的前提是消费方**只把它写进活下来的 ViewModel**，
 * 不挂任何"取不到就 pop"的自毁分支 —— 那才是上一次真机翻车（退场期二次 pop）的成因。
 *
 * 2026-09-28 交接的**语义**变了：消费方拿到它之后不再覆盖上一轮，而是把上一轮压进
 * `GallerySearchViewModel.contexts`，返回逐级回退（搜 touhou → 点画师标签 → 返回 = 回 touhou 那面墙）。
 * 槽位本身的形状与"取用一次即清"的前提都没变，所以这一层不需要跟着改。
 *
 * 字段是快照状态而不是普通 var：写入方在另一个 Activity 里，靠快照失效才能让这边活着的组合
 * 立刻回读到，不依赖 onResume 时机（大图页是透明窗口，底下那屏只是 paused、并没有停组合）。
 */
object GallerySearchHandoff {

    data class Request(val site: GallerySite, val tags: List<String>)

    var pending by mutableStateOf<Request?>(null)
        private set

    /**
     * 大图页里点某一枚标签：带着它所属的站点递回去（两站词表不通，站点不能猜）。
     *
     * 标签名先过一道清洗（2026-10-23，用户「去掉多余前缀与非法符号」）：站方原串与画师名
     * 都可能带前导 `@` / `-` 或引号尖括号一类符号，原样进搜索要么搜错（`-` 会变排除项）、
     * 要么捅穿拼 URL 那一步。判据与"洗什么、不洗什么"见 [sanitizeGalleryTagName]。
     * 洗成空串的（整枚都是符号）**不写槽** —— 没有可搜的词，写了也是空转一轮。
     */
    fun postTag(site: GallerySite, tag: String) {
        val cleaned = sanitizeGalleryTagName(tag)
        if (cleaned.isEmpty()) return
        pending = Request(site, listOf(cleaned))
    }

    /** 取走并清空；没有待处理的交接就返回 null。 */
    fun consume(): Request? = pending.also { pending = null }
}
