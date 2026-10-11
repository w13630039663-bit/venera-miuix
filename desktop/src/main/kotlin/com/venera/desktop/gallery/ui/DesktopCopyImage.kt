package com.venera.desktop.gallery.ui

import com.venera.desktop.desktopFailureFrame
import com.venera.desktop.desktopFailureType
import com.venera.desktop.desktopOneLineMessage
import com.venera.desktop.gallery.data.desktopCoilBytes
import coil3.PlatformContext
import java.awt.GraphicsEnvironment
import java.awt.Image
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable
import java.awt.datatransfer.UnsupportedFlavorException
import java.io.ByteArrayInputStream
import javax.imageio.ImageIO
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 把当前这一张图交给剪贴板（批 54）。
 *
 * ## 为什么单独一格
 *
 * 浮层那排动作今天只有「复制链接」能把图的信息交出去 —— 而对面要的是图本身时，一条 URL 不够
 * （多数应用不把 URL 当图片粘）。写位图是纯 JDK（`Toolkit.systemClipboard` + 一枚只交位图的
 * `Transferable`），与那颗「复制链接」用的是同一份剪贴板，所以这一格**不加依赖、不动共享面**。
 *
 * ## 三条口径，各由一颗判据钉着
 *
 * 1. **字节走 [desktopCoilBytes]**：与动图播放器同一颗件，冷缓存那段教训只许住在一处。
 *    这一格因此天然与屏上摆的那一张同源 —— 复制的就是刚看到的那一份字节，不是另发一趟请求拿回来的另一张。
 * 2. **读数只吃数字**：站方那一路地址常带 `api_key`/`user_id`，而"顺手把 URL 念进读数"是这一格
 *    最容易犯的错。做成**结构上拿不到**（[desktopCopyImageReadout] 的形参里就没有地址），
 *    比"记得脱敏"硬。
 * 3. **失败说得出是哪一种**：四种成因各一句（[desktopCopyImageFallback]），塌成一句「复制失败」
 *    就等于把四种病读成同一种（「没取到 / 没有 / 没接」是三句话那条纪律）。
 *
 * ⚠️ 动图复制的是 `ImageIO` 解出来的**那一帧位图**：剪贴板那头（`DataFlavor.imageFlavor`）
 * 本来就是位图，没有"带帧的图"这一档 —— 这不是简化，是那一层接口的形状。
 */

/** 复制不成的四种成因。**各一句**，不许塌成一句「复制失败」。 */
internal enum class DesktopCopyImageCause {
    /** 这一张眼下没有可复制的地址（站方没给当前这一档，或这一张正被分级挡着）。 */
    NO_URL,

    /** 字节取不回来：缓存没有它，而那趟"先取一趟"也没成（不是"站方不给这张图"那句）。 */
    NO_BYTES,

    /** 字节到手而 `ImageIO` 解不出位图（那是坏了，不是"这一张是动图"）。 */
    NOT_AN_IMAGE,

    /** 这台机器不给写剪贴板（无头环境），或那一笔写被拒（剪贴板正被别的应用占着）。 */
    CLIPBOARD_UNAVAILABLE,
}

/** 复制成了带得出两个数；没写成带得出是哪一种。 */
internal sealed interface DesktopCopyImageOutcome {
    /** 真写进去了。字节数与那一张的宽高都是从**真物件**上取的，不是请求参数。 */
    data class Copied(val bytes: Int, val width: Int, val height: Int) : DesktopCopyImageOutcome

    /**
     * 没写成。[error] 只在"那一笔真的抛了东西"时带上（写剪贴板被拒、解码抛异常），其余档是 `null` ——
     * 它是必填的：留默认值就等于允许新调用点把"抛了什么"丢掉，而屏上那句只剩一个成因名。
     */
    data class Refused(val cause: DesktopCopyImageCause, val error: Throwable?) : DesktopCopyImageOutcome
}

/**
 * 这台机器给不给"写图片到剪贴板"这条路。
 *
 * `GraphicsEnvironment.isHeadless()` 是唯一一枚问得出这件事的判据（AWT 没有"图片剪贴板支持不支持"
 * 那一枚 `isSupported` 档），而它挡住的正是**无头跑**那一档：那时 `systemClipboard` 直接抛
 * `HeadlessException`，按下去什么都不发生的一枚按钮比没有按钮更糟。
 */
internal fun desktopCanCopyImage(): Boolean = !GraphicsEnvironment.isHeadless()

/** 四句失败说明（[DesktopCopyImageCause] 与之一一对应，判据 ① 钉着两两不许相同）。 */
internal fun desktopCopyImageFallback(cause: DesktopCopyImageCause): String = when (cause) {
    DesktopCopyImageCause.NO_URL -> "这一张眼下没有可复制的图片地址（站方没给这一档，或这一张正被挡着）"
    DesktopCopyImageCause.NO_BYTES -> "本机还没这张图的字节，那趟取图也没成，复制不了"
    DesktopCopyImageCause.NOT_AN_IMAGE -> "字节拿到了而解不出位图，剪贴板那头要的就是位图"
    DesktopCopyImageCause.CLIPBOARD_UNAVAILABLE -> "这台机器没给出写图片到剪贴板的路"
}

/**
 * 成功那一行读数：**只有数字**（字节、宽高）。
 *
 * 前缀 `D_复制图片` 是给 stdout 挑的；判据 ② 钉着它整行带不出 `http` ——
 * 形参里没有地址，所以这一条不是"记得脱敏"而是结构性的。
 */
internal fun desktopCopyImageReadout(bytes: Int, width: Int, height: Int): String =
    "D_复制图片 字节=$bytes 尺寸=${width}x${height}"

/** 上屏那一句（成功报尺寸，失败报成因，真抛了东西就把类型名跟着念）。 */
internal fun desktopCopyImageScreenReading(outcome: DesktopCopyImageOutcome): String = when (outcome) {
    is DesktopCopyImageOutcome.Copied -> "图片已复制（${outcome.width}×${outcome.height}）"
    is DesktopCopyImageOutcome.Refused ->
        desktopCopyImageFallback(outcome.cause) +
            (outcome.error?.let { "：${desktopFailureType(it)}" } ?: "")
}

/**
 * 失败那一行的读数（批 54 图证换来的）：**没成也要有一行可 grep 的东西**。
 *
 * 第一版只有成功打读数，于是取证那一跑屏上念了「这台机器没给出写图片到剪贴板的路：
 * IllegalArgumentException」而 stdout 一字没有 —— "没跑到"与"跑到了而被拒"在 stdout 上同一个样，
 * 归因只能靠猜（手册 §0.1 那条"屏上每句行为断言都要答得出哪次量的"）。
 * 消息与栈顶复用批 53 那三颗原子（类型、一行且脱敏、栈顶帧），这里不重写第二份。
 */
internal fun desktopCopyImageRefusalReading(cause: DesktopCopyImageCause, error: Throwable?): String =
    if (error == null) {
        "D_复制图片 未成 因=${cause.name} 栈=没有"
    } else {
        "D_复制图片 未成 因=${cause.name} 型=${desktopFailureType(error)} " +
            "消息=${desktopOneLineMessage(error)} 栈=${desktopFailureFrame(error) ?: "没有"}"
    }

/** 唯一的失败出口：先念那一行，再交回屏上那一句要用的成因。 */
private fun refuse(cause: DesktopCopyImageCause, error: Throwable?): DesktopCopyImageOutcome.Refused {
    println(desktopCopyImageRefusalReading(cause, error))
    return DesktopCopyImageOutcome.Refused(cause, error)
}

/**
 * 按一次「复制这张图」：取字节 → 解位图 → 写剪贴板，四步各自的失败各归一档。
 *
 * 地址由调用方给**屏上正在摆的那一档**（浮层顶部那一张），不是原图档 ——
 * 复制一张没在屏幕上出现过的图，用户看不出自己复制了什么。
 * 拿不到地址（被挡着 / 站方没给这一档）都算 [DesktopCopyImageCause.NO_URL]，那句实话里两样都点名。
 */
internal suspend fun desktopCopyImageRun(
    context: PlatformContext,
    url: String?,
): DesktopCopyImageOutcome {
    if (url.isNullOrBlank()) return refuse(DesktopCopyImageCause.NO_URL, null)
    if (!desktopCanCopyImage()) return refuse(DesktopCopyImageCause.CLIPBOARD_UNAVAILABLE, null)
    val bytes = desktopCoilBytes(context, url)
        ?: return refuse(DesktopCopyImageCause.NO_BYTES, null)
    val decoded = withContext(Dispatchers.IO) {
        runCatching { ImageIO.read(ByteArrayInputStream(bytes)) }
    }
    val image = decoded.getOrNull() ?: return refuse(DesktopCopyImageCause.NOT_AN_IMAGE, decoded.exceptionOrNull())
    val written = runCatching {
        Toolkit.getDefaultToolkit().systemClipboard.setContents(ImageToClipboard(image), null)
    }
    if (written.isFailure) {
        return refuse(DesktopCopyImageCause.CLIPBOARD_UNAVAILABLE, written.exceptionOrNull())
    }
    println(desktopCopyImageReadout(bytes.size, image.width, image.height))
    return DesktopCopyImageOutcome.Copied(bytes.size, image.width, image.height)
}

/**
 * 只交得出**一张位图**的那枚 `Transferable`。
 *
 * ⚠️ 为什么不是更短的那条路：JDK 里那枚现成的 `ImageSelection` 住在 `sun.datatransfer` ——
 * 内部件、没导出，2026-10-08 编译器直接报 Unresolved reference。公开面上写图就只有自持一枚
 * `Transferable` 这一条，而它必须答得出那三个方法（少一个就是粘出来是空的）。
 * flavor 表只此一处（判据 ⑤ 数这一枚的出现次数，两处各列一份就会漂成"这边能粘那边不能"）。
 */
private class ImageToClipboard(private val image: Image) : Transferable {

    private val flavors = arrayOf(DataFlavor.imageFlavor)

    override fun getTransferDataFlavors(): Array<DataFlavor> = flavors

    override fun isDataFlavorSupported(flavor: DataFlavor): Boolean = flavor in flavors

    override fun getTransferData(flavor: DataFlavor): Any {
        if (!isDataFlavorSupported(flavor)) throw UnsupportedFlavorException(flavor)
        return image
    }
}
