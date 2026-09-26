package com.venera.compose.gallery.domain

import com.venera.compose.gallery.data.GalleryPost

/**
 * 画廊侧的遮罩判定 —— **只读共享**漫画侧那一份「分级模式」与「用户屏蔽规则」，
 * 不新造第二个 NSFW 开关（一个语义不能有两份，见方案 §二中间那一列）。
 *
 * 与漫画侧的分工：漫画那边要靠源级预设 + 显式正则去**推断**是不是成人内容，
 * 而图站自己就把分级写在 `rating` 字段里（实测取值 `s` / `q` / `e`），
 * 所以这里不需要任何推断，判据更强、也更少误伤。
 *
 * 返回值与 `ContentGuardManager.coverMaskStateFor` 用**同一套字面值**
 * （`VISIBLE` / `BLURRED` / `HIDDEN`），卡片侧就能直接复用现成的 `VeneraCoverMask` 口径。
 */
object GalleryGuard {

    const val VISIBLE = "VISIBLE"
    const val BLURRED = "BLURRED"
    const val HIDDEN = "HIDDEN"

    /**
     * @param mode 共享的分级模式，取值 `OFF` / `BLUR` / `HIDE`（与 `venera_guard_prefs` 同源）
     * @param blockedByUser 用户黑名单（KEYWORD / TAG / AUTHOR 规则）是否命中该条
     */
    fun maskStateFor(post: GalleryPost, mode: String, blockedByUser: Boolean): String {
        // 用户显式黑名单**排在模式判定之前**：黑名单就是"别给我看这个"，
        // 不该因为"成人内容处理"关着就失效 —— 那会让它变成一个假开关。
        if (blockedByUser) return HIDDEN
        if (!post.isAdultMarked) return VISIBLE
        return when (mode) {
            "OFF" -> VISIBLE
            "HIDE" -> HIDDEN
            else -> BLURRED
        }
    }
}
