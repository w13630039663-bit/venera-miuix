package com.venera.compose.gallery.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.venera.compose.gallery.data.GallerySite

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
 * 字段是快照状态而不是普通 var：写入方在另一个 Activity 里，靠快照失效才能让这边活着的组合
 * 立刻回读到，不依赖 onResume 时机（大图页是透明窗口，底下那屏只是 paused、并没有停组合）。
 */
object GallerySearchHandoff {

    data class Request(val site: GallerySite, val tags: List<String>)

    var pending by mutableStateOf<Request?>(null)
        private set

    /** 大图页里点某一枚标签：带着它所属的站点递回去（两站词表不通，站点不能猜）。 */
    fun postTag(site: GallerySite, tag: String) {
        pending = Request(site, listOf(tag))
    }

    /** 取走并清空；没有待处理的交接就返回 null。 */
    fun consume(): Request? = pending.also { pending = null }
}
