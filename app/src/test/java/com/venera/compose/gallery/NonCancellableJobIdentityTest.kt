package com.venera.compose.gallery

import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * `withContext(NonCancellable)` 会**换掉** `coroutineContext[Job]` —— 这条库事实钉住。
 *
 * 起因是画廊「猜你喜欢」那三条真机读数（2026-09-29）：下拉环转不停、同时出现两枚加载图标、
 * 墙上只摆 40 张（= 第 1 页两站各 20 张，第 2 页永远没来）。三条同一个根因：
 * 那两处在 `finally { withContext(NonCancellable) { if (loadJob === coroutineContext[Job]) ... } }`
 * 里清在途标志，而块内的 `coroutineContext[Job]` 是 `NonCancellable` 本身，
 * 于是那个"只清自己这一笔"的守卫**恒不成立** → 标志永远停在 true。
 *
 * 写成用例而不是注释：这处守卫的意图是对的（被取代的那一笔不许清掉新一轮的标志），
 * 错在拿谁跟谁比。用例改不了库的行为，但能保证下次有人照抄这段时先看见它。
 */
class NonCancellableJobIdentityTest {

    @Test
    fun `NonCancellable 块里读到的 Job 不是外层那一笔`() = runBlocking {
        coroutineScope {
            // 先声明再赋值：块体要引用"这一笔自己的 Job"，而它正是 launch 的返回值。
            // runBlocking/单线程调度下，赋值发生在块体第一次被调度之前，所以拿得到。
            var self: Job? = null
            self = launch {
                assertSame("块外：coroutineContext[Job] 就是这一笔自身", self, coroutineContext.job)
                withContext(NonCancellable) {
                    assertNotSame(
                        "withContext(NonCancellable) 把 Job 换成了 NonCancellable 本身 —— " +
                            "拿 coroutineContext[Job] 和 launch 返回的 Job 比，结果恒为 false",
                        self,
                        coroutineContext.job,
                    )
                }
            }
            self.join()
        }
    }
}
