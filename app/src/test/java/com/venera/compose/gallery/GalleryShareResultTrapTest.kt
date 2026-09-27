package com.venera.compose.gallery

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 锁住 2026-09-27 那次「分享一次都没成功过」的**成因形状**。
 *
 * 真机报的是 `Parcelable encountered IOException writing serializable object
 * (name = kotlin.Result)` —— 而 `GalleryPostScreen.share()` 里根本没有一处写着 `Result`：
 * ```
 * val uri = runCatching { FileProvider.getUriForFile(…) }.onFailure { …}   // uri 是 Result<Uri>！
 * putExtra(Intent.EXTRA_STREAM, uri)                                       // 挑中了 Serializable 那个重载
 * ```
 * `onFailure` 返回的是 `this`（`Result<T>`），不是 `T`；而 `kotlin.Result` 又声明了
 * `java.io.Serializable`，所以 `putExtra(String, Serializable)` 这个重载**在编译期合法**，
 * 到运行期 Parcel 用 `ObjectOutputStream` 写它才炸。
 *
 * ⚠️ 本仓单测是纯 JVM（没有 Robolectric），`Intent` 在测试类路径上是会抛的桩，
 * 所以**这里测不到真正的 putExtra 重载选择** —— 能测的只有上面那两条前提，
 * 它们一起构成"为什么编译器不拦、Parcel 才拦"。真正的防线是调用点那句
 * **显式类型** `val uri: Uri = …`：有了它，把 `Result` 赋给 `uri` 直接编译不过。
 * 这两条断言一旦哪天不成立（Kotlin 改了 `onFailure` 的返回类型、或 `Result` 不再
 * 是 Serializable），说明那道防线的前提变了，要回去重看调用点写法。
 */
class GalleryShareResultTrapTest {

    @Test
    fun `onFailure 交回的是 Result 而不是里面的值`() {
        val result: Result<String> = runCatching { "content://venera/1.jpg" }
        val picked: Any = result.onFailure { }
        assertTrue("onFailure 必须返回 this（Result），这正是 uri 被写成 Result 的原因",
            picked is Result<*>)
    }

    @Test
    fun `getOrElse 才把值交出来`() {
        val result: Result<String> = runCatching { "content://venera/1.jpg" }
        val picked: Any = result.getOrElse { "" }
        assertFalse(picked is Result<*>)
        assertTrue(picked is String)
    }

    @Test
    fun `Result 声明了 Serializable —— 所以 putExtra 的重载选择不会报错`() {
        // 这条就是"编译期放行、运行期才炸"的那半：类型检查只看是不是 Serializable。
        val maybe: java.io.Serializable? = runCatching { 1 } as? java.io.Serializable
        assertTrue("kotlin.Result 若不再是 Serializable，调用点那个显式类型的必要性要重估",
            maybe != null)
    }
}
