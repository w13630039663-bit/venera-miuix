package com.venera.compose.gallery.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.venera.compose.components.venera.VeneraNotice
import com.venera.compose.components.venera.VeneraNoticeTone
import com.venera.compose.feature.SettingsSubScreen
import com.venera.compose.feature.openSettingsSubScreen
import com.venera.compose.gallery.domain.GalleryNotice
import com.venera.compose.gallery.domain.GalleryNoticeAction
import com.venera.compose.gallery.domain.GalleryNoticeKind
import com.venera.compose.gallery.domain.GalleryNoticeTone
import com.venera.compose.gallery.domain.GallerySearchSource
import com.venera.compose.ui.tokens.VeneraTokens

/**
 * 画廊各面墙上**「这一轮少了什么」那一条**的宿主。
 *
 * ── 它替掉的是什么 ──
 *
 * 三处（首页猜你喜欢、搜索卡、每日热门二级页）从前各写一行
 * `Text(fontSize = caption, color = textSecondary)`，内容是
 * `failures.entries.joinToString(" · ") { "站：原因" }`。两个问题：
 *
 * 1. **三类处境同一张脸**：没配账号（去设置填两个字段）、这轮超时（重试）、
 *    这一站本来没货（什么都不用做）—— 全是同一档灰字，只能靠读完字分辨；
 * 2. **没有出口**：第一类那条长句里明明写着"到「设置 → 画廊」里去填"，
 *    而屏上没有任何能按的东西 —— 用户得自己记住那句话，退出去、翻设置、再找回来。
 *
 * 现在每一条按档给底色与图标，带出口的那一档给一枚**真能到达目的地**的按钮。
 *
 * ── 为什么"去配置账号"是真的能到 ──
 *
 * 走 [openSettingsSubScreen]（设置子树那套跨 Activity 的入口），落点是
 * [SettingsSubScreen.GALLERY] —— 也就是那句文案里点名的「设置 → 画廊」那一页，
 * 账号卡就在那一页的「账号与密钥」组里（`GalleryAccountCard`）。
 * 文案与目的地是**同一个地方**，不是"指着 A 却跳到 B"。
 *
 * ⚠️ 这一条**不吞掉任何原因**：原来那串 `站：原因` 里的原因一个字都没丢，
 * 只是从"一整段拼接的灰字"变成"按档分组的块"，每组里仍逐站念原因。
 *
 * ── 为什么三面墙都走 [compact]（一行档）──
 *
 * 2026-10-14 手机档截图量到的原样：首页那面墙的两块提示各占 3~4 行（标题 +
 * 一段"为什么 + 去哪儿配 + 另一站不受影响"），把「正在关注的画师 / 每日热门」
 * **整块推出首屏**。画廊是这款产品的核心模块，而它的第一眼是一份故障说明书。
 *
 * 所以内容区只允许"一行摘要"这一级：标题 + 出口 + 展开把手，正文点了才就地铺开
 * （一个字不删）。文档级长文另有归属（设置 → 画廊 那一页的账号卡）。
 *
 * @param onRetry 有出口那一档按下去要做什么。传 null 时**不摆**那枚「重试」——
 *   摆一枚按不动的按钮比不摆更坏（它同时在暗示一个不存在的出路）。
 * @param compact 是否只摆一行。**必填、无默认值**：这一档是"这块提示摆在哪面墙上"的
 *   决定，第四个调用点出现时必须当场决定一次，不许靠默认值静默长成多行块。
 */
@Composable
internal fun GalleryNotices(
    notices: List<GalleryNotice>,
    compact: Boolean,
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null,
) {
    if (notices.isEmpty()) return
    val tokens = VeneraTokens
    val context = LocalContext.current

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(tokens.spacing.space3),
    ) {
        notices.forEach { notice ->
            // 出口按 kind 给（判据在 domain，这里只做映射与接线）。
            val actionLabel = when (notice.action) {
                GalleryNoticeAction.ConfigureAccount -> "去配置账号"
                GalleryNoticeAction.Retry -> "重试".takeIf { onRetry != null }
                null -> null
            }
            // 两个出口的**目的地不同**（配置页 / 原地重发），所以这里显式给出类型：
            // 让 `when` 自己去推会得到 `Any?`（一个 lambda 字面量与一个可空函数引用混在一支里），
            // 编译器当场报"actual type is Any?"—— 而那正是本仓不许的"参数类型靠猜"。
            val openAccountSettings: () -> Unit = {
                context.openSettingsSubScreen(SettingsSubScreen.GALLERY)
            }
            val onAction: (() -> Unit)? = when (notice.action) {
                GalleryNoticeAction.ConfigureAccount -> openAccountSettings
                GalleryNoticeAction.Retry -> onRetry
                null -> null
            }
            VeneraNotice(
                title = noticeTitle(notice),
                // 正文由 domain 给（它知道各档该补哪半句，见 `bodyOf`）——
                // 这一层**不再自己拼**：拼第二份就是"标题与正文各说一遍"那类重复的来源。
                message = notice.body,
                tone = noticeToneOf(notice.tone),
                actionLabel = actionLabel,
                onAction = onAction,
                compact = compact,
            )
        }
    }
}

/**
 * 标题：**说清是哪一站、出了什么事**，一行。
 *
 * 站名必须在标题上：这一块与另外两档块并排摆着时，"是哪个站"是用户第一眼要的东西，
 * 而它在正文里会被"为什么"盖住。多站同类时按站表序并列（顺序由 domain 定死）。
 *
 * ⚠️ 措辞按 [GalleryNoticeKind]**穷尽分档**，不去猜原因串。少写一档编译器就报错，
 * 而猜串的写法（`reason.contains("推荐")`）在措辞调整时会静默换掉屏上那句话。
 *
 * ⚠️ 标题与正文是**分工**不是复述：正文由 domain 的 `bodyOf` 给，只写标题答不出的那半句。
 *
 * ⚠️ `internal`（不是 private）是为的这条不变量能被**行为**测到而不是靠扫字符串：
 * 一行档把标题压在单行上，标题超长会静默变成省略号 —— 那正是本批要防的失败形态，
 * 扫描式判据只看得见"代码里写了什么"，看不见"两站并起时那一行有多长"。
 * 尺子在 `UiAuditBatch2Test`。
 */
internal fun noticeTitle(notice: GalleryNotice): String {
    val sites = notice.sites.joinToString(" · ") { it.displayName }
    return when (notice.kind) {
        // 未配账号那一档：标题用那句**唯一来源**的标题半句（不另造措辞）。
        // 站名不重复前缀 —— 那句话自己就以 "Gelbooru" 起头。
        GalleryNoticeKind.MissingAccount -> GallerySearchSource.NEEDS_ACCOUNT_TITLE
        // 标题只说"是什么事"，站方原话（401 还是 403）留在正文里逐站念 ——
        // 那句 `REJECTED_HINT` 有 40 来个字，压在一行档的标题上只会读成省略号。
        GalleryNoticeKind.CredentialRejected -> "$sites 不认这组凭据"
        GalleryNoticeKind.LegFailed -> "$sites 这一轮没答上"
        GalleryNoticeKind.NoContent -> "$sites 这一轮没有内容"
        GalleryNoticeKind.NoRecommendTags -> "$sites 还没有可推荐的标签"
    }
}

/**
 * 域层的严重度 → 视觉那一枚。**`when` 不留 `else`**：加档时编译器当场报错，
 * 不会静默落进某一档长成同一个颜色（两枚枚举分开住是因为分层守卫不许 domain import components）。
 */
private fun noticeToneOf(tone: GalleryNoticeTone): VeneraNoticeTone = when (tone) {
    GalleryNoticeTone.Error -> VeneraNoticeTone.Error
    GalleryNoticeTone.Warning -> VeneraNoticeTone.Warning
    GalleryNoticeTone.Info -> VeneraNoticeTone.Info
}
