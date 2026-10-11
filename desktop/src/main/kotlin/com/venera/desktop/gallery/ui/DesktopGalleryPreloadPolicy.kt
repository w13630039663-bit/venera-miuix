package com.venera.desktop.gallery.ui

import com.venera.compose.gallery.domain.GalleryPreload
import com.venera.compose.gallery.domain.GalleryPreloadMode
import com.venera.compose.gallery.domain.GalleryPreviewQuality

/**
 * 浮层刚打开时先摆哪一档。**两头共用这一颗**：浮层的初始档，与预取提前取的那一档。
 *
 * 为什么单独一颗：这两处一旦各写一遍那条 ternary，就会漂成两条轨 —— 预取按预览档取，
 * 浮层按清楚那一档摆，缓存键不同，点下去照样重新取一趟。那时"已经预取过了"就是假话，
 * 而屏上看不出任何异常（这一族坑在判据里叫"不同轨"，`DesktopGalleryPreloadTest` ① 钉着它）。
 */
internal fun desktopDetailInitialTier(initialQuality: GalleryPreviewQuality): DesktopDetailTier =
    if (initialQuality == GalleryPreviewQuality.LARGE) DesktopDetailTier.SAMPLE else DesktopDetailTier.PREVIEW

/**
 * 这一轮预取的**一本账**（第五十七批）：四枚计数住在同一次过滤里。
 *
 * 为什么不是"只回一张地址表"：`排=0` 那一句要说得出因，而那四枚计数正是那三道 filter 顺手就有的副产品 ——
 * 另起一颗件去"再数一遍"就是两条轨（各写一遍就会漂，判据 ⑤ 钉着四枚各在其位）。
 */
internal class DesktopPreloadPlan(
    val urls: List<String>,
    /** 共享那颗计划点名的那几张（不含当前页） */
    val wanted: Int,
    /** 其中被分级挡住几张 */
    val blocked: Int,
    /** 其中站方没给可取地址几张（视频没封面也算这一枚） */
    val noUrl: Int,
)

/**
 * 此刻这一张的**前后该提前取哪几张的图**（第四十七批）+ 那一本账（第五十七批）。不发网络，只出账 ——
 * 网络那一跳住在集成方（`VeneraDesktopApp`），为的是这颗能被单测直接取值。
 *
 * 计划本体**转交**共享的 [GalleryPreload.plan]（`gallery/domain/GalleryViewerPolicies.kt:65`）：
 * 不含当前页、夹在列表范围内、近的先来、同距离向前 —— 那四条是对岸量出来的，桌面不重述。
 *
 * ## 为什么吃的是**共用那一枚档位**而不是设置里那枚质量（第五十批改判）
 *
 * 原来这枚形参是设置里的 `galleryPreviewQuality`，自己在内部再推一档。那在"浮层开着的档位
 * 永远由设置起步"的前提下成立；档位上提之后这个前提没了 —— 用户在大图那一屏或浮层里手动换过档，
 * 屏上那一枚与设置里那一枚就分岔了。预取若还按设置推，它取的就是**另一档**的字节，
 * "翻到的下一张命中缓存"当场成假（判据 ⑧ 钉住这一条，形状与批 47 修掉的那条缺陷同一族）。
 *
 * 三条桌面自己的规矩叠在计划之上，每条都对应一种"说了而没做"的形状：
 * - 被分级挡住的那一张**连发起取图都不该有**（与墙上、浮层同一条，见 [DesktopWallCard.masked]）；
 * - 站方没给那一档的地址时不许把空串发出去（两档在不同站的覆盖度不同）；
 * - 两张卡指向同一地址只取一遍（同一次翻页里换批、或站方给的是同一枚缩略）。
 */
internal fun desktopPreloadPlan(
    mode: GalleryPreloadMode,
    current: Int,
    cards: List<DesktopWallCard>,
    tier: DesktopDetailTier,
): DesktopPreloadPlan {
    val window = GalleryPreload.plan(mode, current, cards.size).map { cards[it] }
    val kept = window.filterNot { it.masked }
    val tierUrls = kept.map { desktopDetailTierUrl(it.post, tier) }
    return DesktopPreloadPlan(
        urls = tierUrls.filter { it.isNotBlank() }.distinct(),
        wanted = window.size,
        blocked = window.size - kept.size,
        noUrl = tierUrls.count { it.isBlank() },
    )
}

/**
 * 发那一行的读数（第五十六批）：**这一轮排了几张、吃的是哪一档**。
 *
 * 那枚开关屏上能改、消费点也接了，可 stdout 上关于它的字一直是零 —— 而这一族偏偏最容易"说了而没做"：
 * `enqueue` 不吃组合那层的取消（批 49 那条），少一格收手就是几十张没人看的图。**没有读数，这类缺陷
 * 只能等它变成流量账单。** 一次一行是刻意的：`LaunchedEffect` 每换页/换档/换模式重开一次就念一行，
 * 刷屏与静默一样坏。
 *
 * 形参里**没有地址**：站方那一路常带 `api_key`/`user_id`，做成结构上带不出，比"记得脱敏"硬
 * （与批 54 那颗 `desktopCopyImageReadout` 同一条纪律）。
 *
 * `排=0` 那一句要跟着半句实话：OFF 那一档、与"开了但前后几张都被挡着"，屏上都是"什么都没发生"，
 * 读数不许把这两种压成同一句哑巴（「没取到 / 没有 / 没接」是三句话那条）。
 */
internal fun desktopPreloadReading(
    mode: GalleryPreloadMode,
    plan: DesktopPreloadPlan,
    tier: DesktopDetailTier,
): String =
    "D_预取 档=$mode 排=${plan.urls.size} 取=$tier" +
        if (plan.urls.isEmpty()) "（这一轮一笔都不发：${desktopPreloadZeroCause(mode, plan)}）" else ""

/**
 * `排=0` 那一句的**因**（第五十七批）。四种缺席在屏上同形，一句只许认一种：
 * 档设成了不预取 / 前后没有那几张了（翻到底、或墙本来就是空的）/ 前后那几张被分级挡住 / 站方没给可取的地址。
 *
 * 次序是判式的一部分：**先认档，再认"计划本身是空的"** —— 不预取那一档同样算出 `wanted=0`，
 * 反过来念就会把"人关掉了"报成"翻到底了"（判据 ② 钉这一条）。
 *
 * ⚠️ 后两种可以同时成立，那时两枚计数都念（只报第一枚 = 把第二种缺席藏起来，判据 ⑧）。
 * 走到这里时 `urls` 必空 ⇒ 窗口里那几张必被这两道 filter 之一吃掉，`挡 + 无址 = 计划` 恒大于 0，不留兜底句。
 */
internal fun desktopPreloadZeroCause(mode: GalleryPreloadMode, plan: DesktopPreloadPlan): String {
    if (mode == GalleryPreloadMode.OFF) return "档设成了不预取"
    if (plan.wanted == 0) return "前后没有那几张了"
    return buildString {
        if (plan.blocked > 0) append("挡=${plan.blocked}")
        if (plan.blocked > 0 && plan.noUrl > 0) append(" ")
        if (plan.noUrl > 0) append("无址=${plan.noUrl}")
    }
}

/**
 * 收手那一行的读数：这一批不再要了，`dispose` 掉几笔。
 *
 * ⚠️ 调用点必须排在 `dispose()` **之后** —— 先念"收手=3"再停手，就是拿请求当结果（批 49 钉过发与收的
 * 顺序，这一条是那枚钉的另一半：顺序不许只钉一半）。
 */
internal fun desktopPreloadStopReading(disposed: Int): String =
    "D_预取 收手=$disposed（这一批不再要了）"
