---
name: project-noncancellable-job-guard
description: withContext(NonCancellable) 会把 coroutineContext[Job] 换成 NonCancellable 本身 —— 在 finally 里用「job === coroutineContext[Job]」当"只清自己这一笔"的守卫会恒不成立，在途标志永不清零；画廊三条真机读数（下拉环转不停 / 两枚加载图标 / 墙上只摆 40 张）同一根因
metadata:
  type: project
---

2026-09-29 的一条实测库行为（写成用例钉住了：`app/src/test/.../NonCancellableJobIdentityTest.kt`）：

```kotlin
job = scope.launch {
    try { work() } finally {
        withContext(NonCancellable) {
            if (job === coroutineContext[Job]) isLoading = false   // ❌ 恒不成立
        }
    }
}
```

`NonCancellable` 本身就是一个 `Job` 元素，`withContext(NonCancellable)` 会把它放进新上下文，
于是块内 `coroutineContext[Job]` 读到的是 `NonCancellable`，**不是**那一笔 `launch` 的 Job。
守卫的意图（"被新一轮取代的旧笔不许替新的灭灯"）是对的，错在**拿谁跟谁比**。

正确写法：进协程第一件事就抓住自身，块内比那个局部量。

```kotlin
job = scope.launch {
    val self = coroutineContext[Job]      // 在 withContext 之前抓
    try { work() } finally { withContext(NonCancellable) { if (job === self) isLoading = false } }
}
```

**Why:** 这个恒假守卫让 `GalleryForYouViewModel` 的 `isLoading`/`isLoadingMore` **永远停在 true**，
一次产出三条看起来互不相干的真机读数：① 下拉刷新的环转不停（`isRefreshing` 含它）；
② 同屏两枚加载图标（顶部环 + 页尾续页环）；③ **墙上只摆 40 张** —— `loadMore()` 第一道闸
`if (isLoading || isLoadingMore) return` 永远进不去，第 2 页从不发，而 40 正好是第 1 页
两站各 `PER_SITE_PAGE = 20`。同一形写法在 `GallerySearchViewModel` 里**没有这个毛病**
（那里是裸 `finally` 比较，外面没套 `withContext`），所以搜索翻页一直正常 —— 这个对照是定位的关键。

**How to apply:**
- 见到"取消也要清标志"的 `withContext(NonCancellable)`，先问：**块内那个 `coroutineContext[Job]` 还是原来那个吗**。
  要比较就必须在进块之前把 Job 抓成局部量。
- 用户报"多个不相干的症状同时出现"时，先找一个**能同时解释全部读数**的单因，再分别查；
  三条各自去修会留下第 4 条（这次第 4 条是连播停在半页，成因独立，另案）。
- 相关：[[project-implemented-but-unwired]]（同一类"实现了但永远进不去"）、
  [[feedback-degrade-paths-must-fail-loud]]、[[project-gallery-data-ceilings]]
