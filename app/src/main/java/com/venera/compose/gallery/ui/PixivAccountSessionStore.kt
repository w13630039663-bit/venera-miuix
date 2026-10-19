package com.venera.compose.gallery.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.venera.compose.gallery.data.PixivAppPage
import com.venera.compose.gallery.data.PixivRestrict
import com.venera.compose.gallery.data.PixivUserDetail
import com.venera.compose.gallery.data.PixivUserHead
import com.venera.compose.gallery.data.PixivUserPage
import com.venera.compose.gallery.domain.PixivLegCursor
import com.venera.compose.gallery.domain.pixivFeedExhausted
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * pixiv 账号页的**进程级会话仓**（2026-10-19，用户点名「切屏幕都要留住数据」）。
 *
 * ## 为什么不能再用 `remember(requestKey)`
 *
 * 从前三段 / 二级三档 / 两档 scope 各是一颗 `remember(requestKey)`：key 一变，数据清空、
 * 重取、滚回顶。真机上的读感就是"每按一次页签都像第一次进来"，网络一慢还先白屏。
 * 而这一页的三份数据（收藏墙 / 关注动态 / 已关注名单）本来就互不替代 —— 没有理由切走就扔。
 *
 * 这个仓活到**进程结束**：Activity 的 `finish()`、旋转、跨页往返都不会清它；
 * 真正要清仓的只有一件事 —— 退出登录（见 [clear]）。
 *
 * ## 这里不负责"什么时候取"
 *
 * 取数时机在界面层：第一次进来 [PixivWallSession.ensure]，之后只有下拉刷新
 * （[PixivWallSession.refresh]）与错误页的"重试"才会再发一趟。仓只保证：
 * ① 同一份 key 只有一份真相；② 在途不并发；③ 失败不清屏。
 *
 * ## 为什么滚动位置也放这儿
 *
 * `LazyStaggeredGridState` 自己活不过组合销毁。切走再回来时它是新建的 —— 数据还在、
 * 位置归零，读起来同样像"重新进来了"。这里只记 index + offset 两个整数，恢复时交给
 * `rememberLazyStaggeredGridState(initialFirstVisibleItemIndex = …)`。
 */
/** 会话仓的宿主 scope：让一趟取数活过这一屏的组合，也活过 Activity 的销毁。 */
private val pixivAccountSessionScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

internal object PixivAccountSessionStore {

    private val walls = mutableMapOf<String, PixivWallSession>()
    private val following = mutableMapOf<String, PixivFollowingSession>()
    private val identities = mutableMapOf<String, PixivIdentitySession>()

    fun wall(key: String): PixivWallSession = walls.getOrPut(key) { PixivWallSession() }

    fun following(key: String): PixivFollowingSession = following.getOrPut(key) { PixivFollowingSession() }

    fun identity(userId: String): PixivIdentitySession =
        identities.getOrPut(userId) { PixivIdentitySession() }

    /**
     * 退出登录时清仓。不清的话换一个账号进来，旧的收藏墙还挂在新账号的用户号下面
     * （那些 key 里带着旧 id，看着不会串，但也没有一条理由继续留着别人的数据）。
     */
    fun clear() {
        walls.clear()
        following.clear()
        identities.clear()
    }
}

/** 一面墙（收藏 / 关注动态 / 我发布的）留在进程里的那一份。 */
internal class PixivWallSession {

    var pages by mutableStateOf<Map<PixivRestrict, PixivAppPage>>(emptyMap())
        private set

    /** 答过一趟没有。**失败也算答过**：否则每次回到这一屏都会替用户再发一次。 */
    var asked by mutableStateOf(false)
        private set

    var loading by mutableStateOf(false)
        private set

    var refreshing by mutableStateOf(false)
        private set

    var loadingMore by mutableStateOf(false)
        private set

    var pageError by mutableStateOf<String?>(null)
        private set

    var moreError by mutableStateOf<String?>(null)
        private set

    var scrollIndex by mutableIntStateOf(0)
    var scrollOffset by mutableIntStateOf(0)

    private var job: Job? = null

    val busy: Boolean get() = loading || refreshing || loadingMore

    /** 第一次进这一屏：没答过才发。 */
    fun ensure(
        legs: List<PixivRestrict>,
        fetch: suspend (PixivRestrict, String?) -> Result<PixivAppPage>,
    ) {
        if (asked || job?.isActive == true) return
        loading = true
        job = pixivAccountSessionScope.launch { load(legs, fetch) }
    }

    /** 下拉刷新 / 错误页重试。已经有内容时**不拿空表顶掉它**：取失败就继续摆上一回那批。 */
    fun refresh(
        legs: List<PixivRestrict>,
        fetch: suspend (PixivRestrict, String?) -> Result<PixivAppPage>,
    ) {
        if (job?.isActive == true) return
        refreshing = true
        job = pixivAccountSessionScope.launch { load(legs, fetch) }
    }

    private suspend fun load(
        legs: List<PixivRestrict>,
        fetch: suspend (PixivRestrict, String?) -> Result<PixivAppPage>,
    ) {
        pageError = null
        moreError = null
        try {
            // 第一页：**每一档各发一趟**。「全部」少发一趟就是屏上写着全部而只读了公开。
            val results = legs.map { restrict -> restrict to runLeg { fetch(restrict, null) } }
            val ok = results.filter { it.second.isSuccess }
            if (ok.isEmpty()) {
                val line = results.firstOrNull()?.second?.exceptionOrNull()?.readableWallError()
                if (pages.isEmpty()) pageError = line else moreError = "这一回没刷新上，下面还是上次那批。"
            } else {
                pages = ok.associate { (restrict, page) -> restrict to page.getOrThrow() }
                // 一趟成、一趟败：内容照摆，但那一句必须说 —— 不说就是把"这一档没取上"读成"这一档没有"。
                if (ok.size < results.size) moreError = "另一档没取上，上面这些只是取上的那一档。"
            }
            asked = true
        } finally {
            loading = false
            refreshing = false
        }
    }

    /**
     * 续页。三道闸门与云端书签墙同一套（有内容 / 还没到底 / 这一趟没在跑）。
     *
     * 判据全走 domain：[PixivLegCursor] 只记**答过**的腿，[pixivFeedExhausted] 要求每条在途的腿
     * 都到底。这里不再写第二套"到底"。
     */
    fun loadMore(
        legs: List<PixivRestrict>,
        fetch: suspend (PixivRestrict, String?) -> Result<PixivAppPage>,
    ) {
        if (!asked || job?.isActive == true) return
        val cursors = legs.mapNotNull { restrict -> pages[restrict]?.let { PixivLegCursor(restrict, it.nextUrl) } }
        if (cursors.size != legs.size || pixivFeedExhausted(cursors)) return
        val pending = legs.mapNotNull { restrict ->
            val next = pages[restrict]?.nextUrl ?: return@mapNotNull null
            restrict to next
        }
        if (pending.isEmpty()) return
        job = pixivAccountSessionScope.launch {
            loadingMore = true
            moreError = null
            try {
                val results = pending.map { (restrict, next) -> restrict to runLeg { fetch(restrict, next) } }
                val ok = results.filter { it.second.isSuccess }
                if (ok.isEmpty()) {
                    moreError = results.firstOrNull()?.second?.exceptionOrNull()?.readableWallError()
                } else {
                    val acc = pages.toMutableMap()
                    ok.forEach { (restrict, page) ->
                        page.getOrNull()?.let { fresh ->
                            val old = acc[restrict]
                            acc[restrict] = PixivAppPage(
                                works = (old?.works ?: emptyList()) + fresh.works,
                                nextUrl = fresh.nextUrl,
                            )
                        }
                    }
                    pages = acc
                    if (ok.size < results.size) moreError = "有一档的下一页没取上，再划一下重试。"
                }
            } finally {
                loadingMore = false
            }
        }
    }
}

/** 「已关注」那一列留在进程里的那一份。 */
internal class PixivFollowingSession {

    var userPages by mutableStateOf<Map<PixivRestrict, PixivUserPage>>(emptyMap())
        private set

    /** 用户号 → 三张预览地址。切走再回来直接接着摆，不按行重发。 */
    var covers by mutableStateOf<Map<Long, List<String>>>(emptyMap())
        private set

    var asked by mutableStateOf(false)
        private set

    var loading by mutableStateOf(false)
        private set

    var refreshing by mutableStateOf(false)
        private set

    var loadingMore by mutableStateOf(false)
        private set

    var error by mutableStateOf<String?>(null)
        private set

    var moreError by mutableStateOf<String?>(null)
        private set

    var scrollIndex by mutableIntStateOf(0)
    var scrollOffset by mutableIntStateOf(0)

    private var job: Job? = null

    val busy: Boolean get() = loading || refreshing || loadingMore

    fun putCovers(userId: Long, urls: List<String>) {
        if (covers[userId] == urls) return
        covers = covers + (userId to urls)
    }

    fun ensure(
        legs: List<PixivRestrict>,
        fetchUsers: suspend (PixivRestrict, String?) -> Result<PixivUserPage>,
    ) {
        if (asked || job?.isActive == true) return
        loading = true
        job = pixivAccountSessionScope.launch { load(legs, fetchUsers) }
    }

    fun refresh(
        legs: List<PixivRestrict>,
        fetchUsers: suspend (PixivRestrict, String?) -> Result<PixivUserPage>,
    ) {
        if (job?.isActive == true) return
        refreshing = true
        job = pixivAccountSessionScope.launch { load(legs, fetchUsers) }
    }

    private suspend fun load(
        legs: List<PixivRestrict>,
        fetchUsers: suspend (PixivRestrict, String?) -> Result<PixivUserPage>,
    ) {
        error = null
        moreError = null
        try {
            // 「全部」= 公开 + 非公开两趟，公开的在前（顺序判据在 `PixivReadScope.legs`）。
            val results = legs.map { restrict -> restrict to runLeg { fetchUsers(restrict, null) } }
            val ok = results.filter { it.second.isSuccess }
            if (ok.isEmpty()) {
                val line = results.firstOrNull()?.second?.exceptionOrNull()?.readableWallError()
                if (mergedUsers().isEmpty()) error = line else moreError = "这一回没刷新上，下面还是上次那批。"
            } else {
                userPages = ok.associate { (restrict, page) -> restrict to page.getOrThrow() }
                if (ok.size < results.size) moreError = "另一档没取上，上面这些只是取上的那一档。"
            }
            asked = true
        } finally {
            loading = false
            refreshing = false
        }
    }

    private fun mergedUsers() = userPages.values.flatMap { it.users }

    fun loadMore(
        legs: List<PixivRestrict>,
        fetchUsers: suspend (PixivRestrict, String?) -> Result<PixivUserPage>,
    ) {
        if (!asked || job?.isActive == true) return
        val cursors = legs.mapNotNull { restrict -> userPages[restrict]?.let { PixivLegCursor(restrict, it.nextUrl) } }
        if (cursors.size != legs.size || pixivFeedExhausted(cursors)) return
        val pending = legs.mapNotNull { restrict ->
            val next = userPages[restrict]?.nextUrl ?: return@mapNotNull null
            restrict to next
        }
        if (pending.isEmpty()) return
        job = pixivAccountSessionScope.launch {
            loadingMore = true
            moreError = null
            try {
                val results = pending.map { (restrict, next) -> restrict to runLeg { fetchUsers(restrict, next) } }
                val ok = results.filter { it.second.isSuccess }
                if (ok.isEmpty()) {
                    moreError = results.firstOrNull()?.second?.exceptionOrNull()?.readableWallError()
                } else {
                    val acc = userPages.toMutableMap()
                    ok.forEach { (restrict, page) ->
                        page.getOrNull()?.let { fresh ->
                            val old = acc[restrict]
                            acc[restrict] = PixivUserPage(
                                users = (old?.users ?: emptyList()) + fresh.users,
                                nextUrl = fresh.nextUrl,
                            )
                        }
                    }
                    userPages = acc
                    if (ok.size < results.size) moreError = "有一档的下一页没取上，再划一下重试。"
                }
            } finally {
                loadingMore = false
            }
        }
    }
}

/**
 * 身份读数（[PixivUserHead] + [PixivUserDetail]）。
 *
 * 只存不取：取数依赖票据与匿名两条腿，留在屏里那颗 `LaunchedEffect` 里做。
 * [asked] 是"这一进程里已经取过一趟"的标记，用来让旋转 / 跨页往返不再重取。
 */
internal class PixivIdentitySession {
    var head by mutableStateOf<PixivUserHead?>(null)
    var detail by mutableStateOf<PixivUserDetail?>(null)
    var asked by mutableStateOf(false)
}

/** 发一趟并把**抛出来**的异常也收进 `Result`。 */
internal suspend fun <T> runLeg(block: suspend () -> Result<T>): Result<T> = try {
    block()
} catch (failure: Throwable) {
    Result.failure(failure)
}

/** 站方那几族异常里最容易被读成"这站没图"的那一类，翻成一句人话（口径同云端书签墙那一条）。 */
internal fun Throwable?.readableWallError(): String = when (this) {
    null -> "这一回没取上"
    else -> message?.take(160)?.ifBlank { null } ?: "这一回没取上（${javaClass.simpleName}）"
}
