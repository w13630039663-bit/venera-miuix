package com.venera.compose.gallery.domain

/**
 * pixiv 账号页在**发任何一趟之前**要过的闸门（2026-10-19）。
 *
 * ## 为什么四档必须各说各的一句话
 *
 * 这四档在屏上长得几乎一样（一段空墙），但用户的出路完全不同：
 * 没登录要去登录、登录着却没记下用户号要**重新登录一次**、票据这一刻取不到只要**再点一下**。
 * 合成一句"没有内容"或"加载失败"，用户就不知道该点哪儿 —— 而更坏的是给一档摆一枚按下去
 * 还是同一句话的「重试」，那是本仓反复立案的**假开关**（见 [PixivAccountGate.retryIsMeaningful]）。
 *
 * 这一颗只管**发请求之前**判得出的四档。401 与 403 是站方答完之后才分得开的两件事，
 * 由调用点从异常里读，不在这里 —— 这里替它们预留的话只会把两句冻成一句。
 */
internal sealed interface PixivAccountGate {

    /** 这一档上屏要说的那句话。[Ready] 没有话（它就是正常那一档）。 */
    val screenLine: String?

    /**
     * 「重试」按下去**是否可能**换回一个不同结果。
     *
     * false 的那几档不许摆重试按钮：出路在别处（登录、重新登录），在这一屏重试一万次
     * 也还是同一句话。
     */
    val retryIsMeaningful: Boolean

    /** 凭据齐了，可以发趟。 */
    data object Ready : PixivAccountGate {
        override val screenLine: String? = null
        override val retryIsMeaningful: Boolean = true
    }

    /**
     * 票据**还没量到**（`bearerOrNull()` 是 suspend，第一帧它必然没回来）。
     *
     * 为什么单独一档，而不是"先当 Ready"或"先当取不到"：前者会让三段墙各发一趟必然被 401 的
     * 请求，然后才跳到 [BearerUnavailable]（用户读到"先空一下、再变成一句取不到"）；
     * 后者会让没登录的用户也先看见一句"这会儿没接上"。这一档什么都不说，量完再判真档。
     *
     * 它与 [BearerUnavailable] 的区别是**有没有量过**：这一档的重试没有意义（再点也还是同一句
     * "正在确认登录态"），所以 [retryIsMeaningful] 为 false。
     */
    data object Measuring : PixivAccountGate {
        override val screenLine: String? = "正在确认登录态…"
        override val retryIsMeaningful: Boolean = false
    }

    /** 这台机器上根本没有 pixiv 登录。 */
    data object NotSignedIn : PixivAccountGate {
        override val screenLine = "登录后才看得到。你关注的人的动态、你的收藏、你的关注列表都存在 pixiv 账号里。"
        override val retryIsMeaningful: Boolean = false
    }

    /**
     * 登录着，但那条记录里**没存下 pixiv 用户号**（老版本写下的登录记录）。
     *
     * ⚠️ 这一档与 [NotSignedIn] 分开念是硬要求：顶栏那颗头像此时说的是"已登录"，
     * 这一屏若也跟着说"你还没登录"，两句话在屏上打架，用户只能读成"应用自己不知道自己的状态"。
     */
    data object MissingUserId : PixivAccountGate {
        override val screenLine = "这一版没记下你的 pixiv 用户号。回到画廊首页点顶栏那颗头像，重新登录一次就能读出来。"
        override val retryIsMeaningful: Boolean = false
    }

    /**
     * 登录着、用户号也有，只是**这一刻**换不到 Bearer（多半刚过期，还没自动续上）。
     *
     * ⚠️ 这一句原先打算写"公开的那些还在，切回公开就能看" —— 撤了，因为那是一句**没量过的承诺**：
     * 账号页这三段（我的收藏、我的关注、关注动态）全是账号维度的数据，公开那一档走的也是同一条带票据的腿，
     * 票据取不到的时候它们一起取不到。屏上写"切回公开就能看"，用户切过去看到的还是这一段。
     */
    data object BearerUnavailable : PixivAccountGate {
        override val screenLine = "pixiv 这会儿没接上，稍后再试。如果一直这样，再重新登录一次。"
        override val retryIsMeaningful: Boolean = true
    }
}

/**
 * 过闸门。三枚输入都是**调用点已经拿在手上的读数**，这里不再去读任何存储 ——
 * 判据要能被单测直接钉，就得是一句纯函数。
 *
 * @param signedIn `authState` 是不是 `SignedIn`
 * @param userId `SignedIn.userId`（没登录时为 null）
 * @param bearerReady `bearerOrNull()` 这一刻有没有值
 */
internal fun pixivAccountGate(
    signedIn: Boolean,
    userId: String?,
    bearerReady: Boolean,
): PixivAccountGate = when {
    !signedIn -> PixivAccountGate.NotSignedIn
    userId.isNullOrBlank() -> PixivAccountGate.MissingUserId
    !bearerReady -> PixivAccountGate.BearerUnavailable
    else -> PixivAccountGate.Ready
}
