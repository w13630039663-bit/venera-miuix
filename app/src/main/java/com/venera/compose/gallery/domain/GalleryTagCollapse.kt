package com.venera.compose.gallery.domain

/**
 * 「关于这张图」那一面标签墙的**收起 / 展开**判据（2026-10-01，用户报"信息卡有点乱"那一批）。
 *
 * ## 为什么要有收起态
 *
 * 这一页的用户抱怨是**读不下去**，而标签墙是唯一一处长度不可控的块：实测一张 yande.re 的
 * `general` 桶 16 枚、整面墙 20 枚出头，安静地占掉四五行的滚屏。而它在这一页里的分量是最低的
 * （参考用的长尾）。收起态把每一桶压到 [COLLAPSED_PER_GROUP] 枚，整面墙就稳在两三行以内。
 *
 * ## 为什么是"每桶各留几枚"而不是"整面墙一共留几枚"
 *
 * 整墙截断会把**后面那几桶整个吃掉**（`角色`/`作品` 常常排在通用前面但条数极少，
 * 截断线一旦落在通用里，用户就再也看不到这页还有"角色"这一栏）。分桶各自截断之后，
 * 每一桶至少露出自己那一小块 —— "这页有几类标签"这件事在收起态依然是可读的。
 *
 * ## 为什么是一条纯函数
 *
 * 它决定"屏上摆哪几枚、尾巴上写 +几"，这正是那种**看着对、错了也看不出来**的判断
 * （少摆一枚不会报错，只会让人以为这张画没那个标签）。所以它落在判据层，有单测钉着，
 * 也能跟着 `_probe/l0/run-judgment-tests.sh` 脱离 gradle 单跑。
 *
 * ⚠️ 它**不碰 `GalleryTagGroup`**，只吃一串字符串 —— 刻意不断开与 `GalleryPost`/`GallerySite`
 * 那条依赖链，这样判据层单跑时不需要把半个 data 包拖进来。
 */
internal object GalleryTagCollapse {

    /**
     * 收起态下**每一桶**最多摆几枚。
     *
     * 6：按 12sp 的胶囊实宽量，一枚中文标签 ≈ 60dp、英文长标签 ≈ 110dp，
     * 而这一页给标签的可摆宽约 280dp（屏宽 − 两边内衬 − 左侧那列分类名）。
     * 6 枚大约落满两行，正好是"看得出这桶是什么、又不占掉半屏"的那一档。
     */
    const val COLLAPSED_PER_GROUP = 6

    /**
     * 一桶标签该摆出来的那一截。
     *
     * [hiddenCount] 是**这一桶**没摆出来的枚数，由调用方在行尾写成一枚 `+N`。
     * 为 0 时不摆那枚 —— 一枚 `+0` 是纯粹的噪声，而且会让人以为"还有东西没看见"。
     */
    data class Slice(val visible: List<String>, val hiddenCount: Int) {
        /** 行尾那枚 `+N` 摆不摆。 */
        val hasMore: Boolean get() = hiddenCount > 0
    }

    /**
     * @param expanded 用户已经点过「展开全部」。**展开态一律全给**，与 [limit] 无关 ——
     *   展开之后还留着一条截断线，那个按钮就成了假开关。
     * @param limit 收起态每桶最多几枚。传 0 表示"收起时一枚都不摆"（整桶只剩 `+N`），
     *   负数按 0 处理，不抛异常：这个值只会来自常量，取值离谱时该让屏上退化得难看一点，
     *   而不是把整页崩掉。
     */
    fun slice(tags: List<String>, expanded: Boolean, limit: Int = COLLAPSED_PER_GROUP): Slice {
        if (tags.isEmpty()) return Slice(emptyList(), 0)
        val cap = limit.coerceAtLeast(0)
        // `<=` 而不是 `<`：正好等于上限的一桶不该在尾巴上留一枚 `+0`。
        if (expanded || tags.size <= cap) return Slice(tags, 0)
        return Slice(tags.take(cap), tags.size - cap)
    }

    /**
     * 这一桶在收起态会不会被截。节标题右端那枚「展开全部」摆不摆，读的就是**整页有没有一处会截**
     * （只要有一桶会，那个按钮就有事可做）。它与 `slice(tags, expanded = false).hasMore`
     * 必须同真同假 —— 不成立的话，要么按钮是个假开关、要么有一桶永远展不开。
     */
    fun overflows(tags: List<String>, limit: Int = COLLAPSED_PER_GROUP): Boolean = tags.size > limit.coerceAtLeast(0)
}
