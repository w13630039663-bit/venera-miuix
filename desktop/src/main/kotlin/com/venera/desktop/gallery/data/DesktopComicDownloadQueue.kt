package com.venera.desktop.gallery.data

import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update

/**
 * 第一百零一批 · 下载队列那一层（多话排队 + 重启续排）。
 *
 * ## 对岸那颗 738 行怎么切的（逐条处置表在 rounds `desktop-comic-download-queue-2026-10-13.md` §1）
 *
 * **照形**的只有三样：入队去重（`:133-184`）、FIFO 取表里第一枚待跑（`:352`）、
 * 任务清单落盘/读盘（`:659-725`，内存态是 `MutableStateFlow<List<DownloadTask>>`、每次变更全量刷盘）。
 * **弃掉**的是绑 Android 的那几样：通知渠道与 `POST_NOTIFICATIONS`、`SAF treeUri` 换算、换根改写路径、
 * 走偏好的并发上限（默认 2、1–16）。存储根桌面恒 `数据根/downloads`（[desktopComicDownloadsRoot]）。
 *
 * ## 四条拍板落在码上的形状
 *
 * ① **同名字段不同语义**（批 97 那一课的正面用法）：文件名 `download_tasks.json` 与 15 枚字段名逐字沿用对岸，
 *   但 `directoryPath` 那一格桌面存的是**相对数据根的目录名**（绝对路径换一台机器就全认不出，
 *   而两端数据根本来不可比），`downloadedPages` 只许是**盘上回读**出来的数（不是"这一趟写了几页"的内存计数 ——
 *   半截续跑那一趟内存计数是 0 而真相是 62）。
 * ② **判齐只有那一颗**：`DONE` 那一档是 [desktopComicChapterIsComplete] 回读盘之后的结论，
 *   不是"runner 交回了 `Completed`"。两者不符时那一笔说**失败**并点名成因，绝不许塌成已完成（那正是第四种"齐不齐"口径）。
 * ③ **恒串行 1 而不立并发常量**：一枚喂给 `take(1)` 的常量是假开关（改成 2 也不会真并发），
 *   所以串行由**循环形状**钉 —— 见 `gallery/ui/DesktopComicQueueRunner.kt` 里那一枚 [nextPending] 与零处分叉。
 * ④ **写盘那一跳就把"进行中"降级成"待跑"**：盘上那份永远不出现 `RUNNING`，于是读盘时不需要猜"这条是上次的僵尸还是这次的活"
 *   （宁可错慢不可静默交错那一族的正解形态）。内存里那一档照旧是 `RUNNING`，屏上因此仍念得出"正在下"。
 *
 * ⚠️ 表是**进程级单例**（前案 `object DesktopGalleryFailureLog`）：顶栏那枚牌子与详情那一屏都读同一份，
 * 而它必须活得当过换屏 —— 这正是这一批要的那一格。测试里每颗用例先 [prime] 一枚全新的临时根，等于换了一份表。
 */

/** 任务清单那份文件的名字（逐字沿用对岸 —— 本仓那把尺子管的是名字，字段语义另见本文件类注释①）。 */
internal const val DESKTOP_COMIC_QUEUE_FILE_NAME = "download_tasks.json"

/** 某一笔在队列里走到哪一档。 */
internal enum class DesktopComicQueueStatus {
    /** 排着队（也含"上次没跑完、这次读回来接着排"）。 */
    PENDING,

    /** 正在跑（**只在内存里**，写盘那一跳会降级成 [PENDING]，见类注释④）。 */
    RUNNING,

    /** 回读盘之后确认齐了。 */
    DONE,

    /** 有一页取不到、或说跑完而盘上不齐。 */
    FAILED,

    /** 用户扳了那枚「停这一笔」（协作式，页一颗都不删）。 */
    STOPPED,
}

/**
 * 队列里的一笔（**纯数据**：展示那一件只吃它，不吃引擎句柄）。
 *
 * ⚠️ 字段名与对岸那 15 枚逐字同一份，只有 [placeholder] 是桌面多的那一枚：
 * `EngineSession.Episode` 有三枚字段而其中这一格决定"要不要真去问页表"，
 * 队列不带过去，占位章就会被当成正常话去问页（对岸那一层在目录时就把占位章跳开了，形状不同轨）。
 */
internal data class DesktopComicQueueTask(
    val taskId: String,
    val sourceKey: String,
    val comicId: String,
    val comicTitle: String,
    val comicCover: String,
    val chapterId: String,
    val chapterTitle: String,
    val chapterOrder: Int,
    /** 相对数据根的目录名（`站/号/0000_标题`），**不是**设备绝对路径。 */
    val directoryPath: String,
    val status: DesktopComicQueueStatus,
    /** 盘上回读出来的页数（不是这一趟写了几页）。 */
    val downloadedPages: Int,
    val totalPages: Int,
    val error: String,
    val placeholder: Boolean,
    val createTime: Long,
    val updateTime: Long,
)

/** 任务号那三格的拼法：`源_子_话`（对岸 `DownloadManager.kt:144` 同一形），每格各自净化过。 */
internal fun desktopComicQueueTaskId(sourceKey: String, comicId: String, chapterId: String): String =
    listOf(desktopComicSafePathKey(sourceKey), desktopComicSafePathKey(comicId), desktopComicSafePathKey(chapterId))
        .joinToString("_")

/** 相对目录名：`站/号/0000_标题`，分隔符恒 `/`（写进盘上那份时不许长出盘符或反斜杠）。 */
internal fun desktopComicQueueRelativeDir(sourceKey: String, comicId: String, order: Int, title: String): String =
    listOf(
        desktopComicSafePathKey(sourceKey),
        desktopComicSafePathKey(comicId),
        desktopComicChapterDirName(order, title),
    ).joinToString("/")

/** 把那一格相对目录名拼回根上 —— 与 [desktopComicChapterDir] 必须算出同一颗目录（判据钉这一条）。 */
internal fun desktopComicQueueDir(root: File, task: DesktopComicQueueTask): File = File(root, task.directoryPath)

/**
 * 盘上这一话已有几页：只数**这一颗件写下的那种页名**且**非空**的。
 *
 * 为什么不用 `listFiles().size`：`.tmp` 那颗、清单那颗、以及旧版本留下的别的扩展名都会被我数进去，
 * 而"齐不齐"那一判吃的是精确页名（批 103 把临时档留在同一目录里，靠的正是这件事）。
 */
internal fun desktopComicQueuePagesOnDisk(dir: File): Int {
    val names = dir.list()?.toList() ?: return 0
    val pattern = Regex("""\d{4}\.jpg""")
    return names.count { name ->
        // 名先过一遍（`.tmp` 与别的扩展名一律不算），再走那颗**共用判式**（第一百一十批）：
        // 头部不是图的那一颗不算"已有" —— 这一格与屏上「缺 N 页」必须同一把尺子，否则同一列名吃两种口径。
        val matched = pattern.matchEntire(name)
        matched != null && desktopComicPageLooksWhole(dir, matched.value.substringBefore('.').toInt() - 1)
    }
}

private fun queueJsonString(raw: String): String = buildString {
    append('"')
    raw.replace("\\", "\\\\").replace("\"", "\\\"").forEach { ch ->
        when (ch) {
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            else -> append(ch)
        }
    }
    append('"')
}

/** 那份队列文件的内容：一个 JSON 数组，每笔 16 枚字段。 */
internal fun desktopComicQueueTasksJson(tasks: List<DesktopComicQueueTask>): String = buildString {
    append('[')
    tasks.forEachIndexed { index, task ->
        if (index > 0) append(',')
        append('{')
        append("\"taskId\":").append(queueJsonString(task.taskId)).append(',')
        append("\"sourceKey\":").append(queueJsonString(task.sourceKey)).append(',')
        append("\"comicId\":").append(queueJsonString(task.comicId)).append(',')
        append("\"comicTitle\":").append(queueJsonString(task.comicTitle)).append(',')
        append("\"comicCover\":").append(queueJsonString(task.comicCover)).append(',')
        append("\"chapterId\":").append(queueJsonString(task.chapterId)).append(',')
        append("\"chapterTitle\":").append(queueJsonString(task.chapterTitle)).append(',')
        append("\"chapterOrder\":").append(task.chapterOrder).append(',')
        append("\"status\":").append(queueJsonString(downgradeRunning(task.status))).append(',')
        append("\"downloadedPages\":").append(task.downloadedPages).append(',')
        append("\"totalPages\":").append(task.totalPages).append(',')
        append("\"errorMsg\":").append(queueJsonString(task.error)).append(',')
        append("\"directoryPath\":").append(queueJsonString(task.directoryPath)).append(',')
        append("\"createTime\":").append(task.createTime).append(',')
        append("\"updateTime\":").append(task.updateTime).append(',')
        append("\"placeholder\":").append(task.placeholder)
        append('}')
    }
    append(']')
}

/** 写盘那一跳的降级（类注释④）：`RUNNING` 交回成 `PENDING`，别的原样。 */
private fun downgradeRunning(status: DesktopComicQueueStatus): String =
    if (status == DesktopComicQueueStatus.RUNNING) DesktopComicQueueStatus.PENDING.name else status.name

private val QUEUE_STRING_KEYS = listOf(
    "taskId", "sourceKey", "comicId", "comicTitle", "comicCover", "chapterId", "chapterTitle",
    "status", "errorMsg", "directoryPath",
)

/**
 * 读回那份队列文件。读不出来一律**抛**（由 [DesktopComicDownloadQueue.prime] 负责留档并点名），
 * 因为"读不出"与"没有队列"是两件事 —— 后者可以静默，前者必须留痕。
 */
internal fun desktopComicQueueParseTasks(text: String): List<DesktopComicQueueTask> {
    val trimmed = text.trim()
    if (!trimmed.startsWith("[") || !trimmed.endsWith("]")) error("那份队列文件读不出形状（要的是一个数组）")
    if (trimmed == "[]") return emptyList()
    val bodies = Regex("\\{[^{}]*}").findAll(trimmed).map { it.value }.toList()
    if (bodies.isEmpty()) error("那份队列文件里一筆对象都没有")
    return bodies.map { body ->
        fun string(key: String): String {
            val match = Regex("\"$key\"\\s*:\\s*(\"(?:[^\"\\\\]|\\\\.)*\")").find(body)
                ?: error("那一笔里缺 $key 那一格")
            return match.groupValues[1].removeSurrounding("\"").replace("\\\"", "\"").replace("\\\\", "\\")
                .replace("\\n", "\n").replace("\\r", "")
        }

        fun int(key: String): Int = Regex("\"$key\"\\s*:\\s*(-?\\d+)").find(body)?.groupValues?.get(1)?.toIntOrNull()
            ?: error("那一笔里缺 $key 那一格（或它不是一个数）")
        fun long(key: String): Long = Regex("\"$key\"\\s*:\\s*(-?\\d+)").find(body)?.groupValues?.get(1)?.toLongOrNull()
            ?: error("那一笔里缺 $key 那一格（或它不是一个数）")
        fun bool(key: String): Boolean = Regex("\"$key\"\\s*:\\s*(true|false)").find(body)?.groupValues?.get(1) == "true"
        QUEUE_STRING_KEYS.forEach { key -> string(key) }
        val statusName = string("status")
        val status = DesktopComicQueueStatus.entries.firstOrNull { it.name == statusName }
            ?: error("那一笔的档位认不出来：$statusName")
        DesktopComicQueueTask(
            taskId = string("taskId"),
            sourceKey = string("sourceKey"),
            comicId = string("comicId"),
            comicTitle = string("comicTitle"),
            comicCover = string("comicCover"),
            chapterId = string("chapterId"),
            chapterTitle = string("chapterTitle"),
            chapterOrder = int("chapterOrder"),
            directoryPath = string("directoryPath"),
            status = status,
            downloadedPages = int("downloadedPages"),
            totalPages = int("totalPages"),
            error = string("errorMsg"),
            placeholder = bool("placeholder"),
            createTime = long("createTime"),
            updateTime = long("updateTime"),
        )
    }
}

/**
 * 顶栏那枚牌子念的那一句（从前它是硬编码的「三站 · 本地无任务队列」，而队列一落地那句就变假）。
 *
 * 四档分开写：空 / 排队中（念得出笔数）/ 正在跑（念得出第几话与页码）/ 都排完了（念得出笔数）。
 * "正在跑"那一档**不**说"已完成几笔"，因为那一格由 `本机已下载` 那一屏说更准。
 */
internal fun desktopComicQueueLampText(tasks: List<DesktopComicQueueTask>): String {
    val running = tasks.firstOrNull { it.status == DesktopComicQueueStatus.RUNNING }
    if (running != null) {
        return "三站 · 正在下 第 ${running.chapterOrder + 1} 话 · ${running.downloadedPages}/${running.totalPages} 页"
    }
    val pending = tasks.count { it.status == DesktopComicQueueStatus.PENDING }
    if (pending > 0) return "三站 · ${pending} 话排队中"
    if (tasks.isEmpty()) return "三站 · 本机队列空"
    return "三站 · 本机 ${tasks.size} 笔已排完"
}

/** 队列表那一行读数（`D_队列` 只进 stdout，不进界面）。 */
internal fun desktopComicQueueReading(tasks: List<DesktopComicQueueTask>, asideName: String?): String {
    fun count(status: DesktopComicQueueStatus): Int = tasks.count { it.status == status }
    return "D_队列 笔=${tasks.size} 跑=${count(DesktopComicQueueStatus.RUNNING)} " +
        "待=${count(DesktopComicQueueStatus.PENDING)} 停=${count(DesktopComicQueueStatus.STOPPED)} " +
        "败=${count(DesktopComicQueueStatus.FAILED)} 完=${count(DesktopComicQueueStatus.DONE)} " +
        "留档=${asideName ?: "无"}"
}

/**
 * 那张任务表（进程级单例，见本文件类注释最后一段）。
 *
 * 表里的事只有三类：入队、报进度、定档。真正**跑**的那一圈不在这里（在 `DesktopComicQueueRunner.kt`），
 * 理由是这一颗要能被喂着在测试里跑 —— 装载与执行分家是本仓的既有规矩。
 */
internal object DesktopComicDownloadQueue {

    private val _tasks = MutableStateFlow<List<DesktopComicQueueTask>>(emptyList())

    val tasks: StateFlow<List<DesktopComicQueueTask>> = _tasks.asStateFlow()

    private val _primed = MutableStateFlow(false)

    /**
     * 这张进程级的表**读回来过没有**（`false` = 还没读到盘上那份）。
     *
     * 为什么要有这一格：第一百零五批包内实测到，那一屏起手比读表那一跳快，于是第一帧念的是
     * 「本机队列空」—— 把"还没读到"说成"读到了没有"，正是批 97 那一屏立"正在数"那一档的同一族假话。
     * 它在 `prime` **赋完 `_tasks` 之后**才置真：两格若反着置，会有那么一帧"说读到了而数是空的"。
     */
    val primed: StateFlow<Boolean> = _primed.asStateFlow()

    /** 读盘读不出时那份被挪走留下的档名（`null` = 没留过档）。 */
    var asideName: String? = null
        private set

    /** 用户扳了「停这一笔」的那些笔（只在内存 —— 它不是任务的状态，而是一个待处理的请求）。 */
    private val stops = ConcurrentHashMap.newKeySet<String>()

    /** `--download-cancel-after=<N>` 那枚页上限（与上面同一颗闸，见 [stopRequested]）。 */
    private val caps = ConcurrentHashMap<String, Int>()

    /**
     * 从盘上把表读回来（重启续排那一格，也是测试里换一份表的办法）。
     *
     * 读不出**不许**静默当"没有队列"：那份挪走留档（与批 88 那颗 cookie 罐同一形），
     * 名字留在 [asideName] 里由读数念出来。
     */
    fun prime(root: File) {
        val file = File(root, DESKTOP_COMIC_QUEUE_FILE_NAME)
        asideName = null
        if (!file.isFile) {
            _tasks.value = emptyList()
            _primed.value = true
            return
        }
        _tasks.value = runCatching { desktopComicQueueParseTasks(file.readText()) }.fold(
            onSuccess = { it },
            onFailure = { cause ->
                val stamp = System.currentTimeMillis()
                val aside = File(file.parentFile, "${file.name}.bad-$stamp.json")
                val moved = runCatching { file.copyTo(aside, overwrite = true) }.isSuccess && file.delete()
                asideName = if (moved) aside.name else "留档没成"
                println("D_队列 读不出那份 因=${cause.message?.take(160)} 留档=${asideName ?: "无"}")
                emptyList()
            },
        )
        _primed.value = true
    }

    /**
     * 排进一笔（**去重**按任务号）。
     *
     * 已在队里的那笔不变成第二笔；而它如果已经跑完/被停/失败过，这一次入队**把它排回去** ——
     * 对岸 `:145-153` 那一形（"已完成那档重排"），而用户再按一次就是明说要它。
     * 正在跑的那一笔不动（否则会出现两圈页循环同时写同一颗目录）。
     */
    fun enqueue(
        root: File, sourceKey: String, comicId: String, comicTitle: String, comicCover: String,
        chapterId: String, chapterTitle: String, chapterOrder: Int, placeholder: Boolean,
    ) {
        val taskId = desktopComicQueueTaskId(sourceKey, comicId, chapterId)
        val now = System.currentTimeMillis()
        val directoryPath = desktopComicQueueRelativeDir(sourceKey, comicId, chapterOrder, chapterTitle)
        val onDisk = desktopComicQueuePagesOnDisk(File(root, directoryPath))
        _tasks.update { current ->
            val existing = current.firstOrNull { it.taskId == taskId }
            if (existing != null && existing.status == DesktopComicQueueStatus.RUNNING) current else {
                val fresh = DesktopComicQueueTask(
                    taskId = taskId,
                    sourceKey = sourceKey,
                    comicId = comicId,
                    comicTitle = comicTitle,
                    comicCover = comicCover,
                    chapterId = chapterId,
                    chapterTitle = chapterTitle,
                    chapterOrder = chapterOrder,
                    directoryPath = directoryPath,
                    status = DesktopComicQueueStatus.PENDING,
                    downloadedPages = onDisk,
                    totalPages = existing?.totalPages ?: 0,
                    error = "",
                    placeholder = placeholder,
                    createTime = existing?.createTime ?: now,
                    updateTime = now,
                )
                if (existing == null) current + fresh else current.map { if (it.taskId == taskId) fresh else it }
            }
        }
        stops.remove(taskId)
        caps.remove(taskId)
        save(root)
    }

    /** 表里第一枚待跑的那一笔（**一次只取一枚** —— 恒串行 1 就钉在这一枚返回值上，见类注释③）。 */
    fun nextPending(root: File): DesktopComicQueueTask? = _tasks.value.firstOrNull { it.status == DesktopComicQueueStatus.PENDING }

    /**
     * 取表里第一枚待跑的那一笔，而**表空时挂在这一点等新的一笔排进来**（第一百零八批）。
     *
     * 为什么要有这一跳：泵起来的那一刻表是空的（`nextPending` 交回 null ⇒ 旧那一形 `?: break` 让那一圈当场退出），
     * 之后用户排进一笔，而句柄没换 ⇒ 那颗 `DisposableEffect(session)` 不会把泵再踢一次 ⇒ 那一笔一直排到关窗没人接
     *（包内读数见 `DesktopComicQueueWakeTest` 类注释）。
     *
     * 等的是那张 `MutableStateFlow` 自己：新入队那一次赋值就是把挂着的这一跳叫醒的信号，
     * 所以**既不轮询也不丢唤醒**（StateFlow 一上来先给当下那个值，挂上去之前已经排好的那一笔同样接得到）。
     * 返回值非空 —— 这一跳只有"等到一笔"和"被掐掉"两种结局，没有第三种；旧的那个 null 档正是缺陷本体。
     */
    suspend fun awaitNextPending(root: File): DesktopComicQueueTask {
        while (true) {
            nextPending(root)?.let { return it }
            _tasks.first { tasks -> tasks.any { it.status == DesktopComicQueueStatus.PENDING } }
        }
    }

    /** 屏上那枚「停这一笔」扳的闸（与 [armPageCap] 落到同一个判式）。 */
    fun requestStop(taskId: String) {
        stops.add(taskId)
    }

    /** 取证种子那一档：写下 [pages] 页之后停手 —— 扳的是**同一颗**闸，不另开一条只为取证的路。 */
    fun armPageCap(taskId: String, pages: Int) {
        caps[taskId] = pages
    }

    /** 这一笔该不该停在页边界上（runner 每一页动手之前查这一次）。 */
    fun stopRequested(taskId: String): Boolean {
        if (stops.contains(taskId)) return true
        val cap = caps[taskId] ?: return false
        val task = _tasks.value.firstOrNull { it.taskId == taskId } ?: return false
        return task.downloadedPages >= cap
    }

    /** 开跑那一档：内存里念得出"正在下"，而写盘那一跳会把它降级成待跑（类注释④）。 */
    fun markRunning(root: File, taskId: String) {
        mutate(taskId) { it.copy(status = DesktopComicQueueStatus.RUNNING, error = "") }
        save(root)
    }

    /** 页表那一问答出来的分母（只有跑到那一笔才知道它有几页，所以入队时它是 0）。 */
    fun reportTotal(root: File, taskId: String, totalPages: Int) {
        mutate(taskId) {
            it.copy(
                totalPages = totalPages,
                downloadedPages = desktopComicQueuePagesOnDisk(desktopComicQueueDir(root, it)),
            )
        }
        save(root)
    }

    /** 每写一页递回来一次（分子是**盘上已有**的页数，与批 100 那颗进度件同一口径）。 */
    fun reportProgress(root: File, taskId: String, downloadedPages: Int) {
        mutate(taskId) { it.copy(downloadedPages = downloadedPages) }
        save(root)
    }

    /**
     * 那一趟跑完了或被停了 —— **回读盘**之后才定档（类注释②）。
     *
     * `Completed` 而盘上不齐 ⇒ 说失败并点名"清单"，绝不许塌成已完成。
     */
    fun settle(root: File, taskId: String, run: DesktopComicChapterRun) {
        mutate(taskId) { task ->
            val dir = desktopComicQueueDir(root, task)
            val onDisk = desktopComicQueuePagesOnDisk(dir)
            when (run) {
                is DesktopComicChapterRun.Cancelled -> task.copy(
                    status = DesktopComicQueueStatus.STOPPED,
                    downloadedPages = onDisk,
                    error = "",
                )

                is DesktopComicChapterRun.Completed ->
                    if (desktopComicChapterIsComplete(dir, task.totalPages)) {
                        task.copy(status = DesktopComicQueueStatus.DONE, downloadedPages = onDisk, error = "")
                    } else {
                        task.copy(
                            status = DesktopComicQueueStatus.FAILED,
                            downloadedPages = onDisk,
                            error = "那一趟说跑完了而清单与盘上不齐 页=$onDisk/${task.totalPages} 因=清单那一格没落成齐",
                        )
                    }
            }
        }
        stops.remove(taskId)
        caps.remove(taskId)
        save(root)
    }

    /** 有一页取不到那一档（站方原话先进 [DesktopComicQueueTask.error]，屏上那一行再吃它）。 */
    fun fail(root: File, taskId: String, reason: String) {
        mutate(taskId) { task ->
            task.copy(
                status = DesktopComicQueueStatus.FAILED,
                error = reason.replace('\n', ' ').replace('\r', ' ').trim(),
                downloadedPages = desktopComicQueuePagesOnDisk(desktopComicQueueDir(root, task)),
            )
        }
        stops.remove(taskId)
        caps.remove(taskId)
        save(root)
    }

    /**
     * 退出漫画域那一档：把"正在跑"交回"待跑"（**回这一域就接着跑**，屏上念的是这句而不是"已暂停"）。
     *
     * 页一颗都不动 —— 那是用户盘上的东西。
     */
    fun abandonRunning(root: File) {
        mutateAll { if (it.status == DesktopComicQueueStatus.RUNNING) it.copy(status = DesktopComicQueueStatus.PENDING) else it }
        save(root)
    }

    /**
     * 屏上那枚「撤掉这一笔」：把那一行从表里摘出去，**盘上那一棵目录一颗页都不动**（批 105 §2①②）。
     *
     * 回值是摘掉那一刻**盘上还剩几页** —— 屏上那句「已写的页留着」要念得出数字，那一格只能现量。
     * 没这一号（已经撤过、或表里本来就没有）回 0 而不炸：撤掉是可逆动作，重复按它不该有第二种果。
     *
     * ⚠️ 这里**不清** [stops]（`settle` 与 `fail` 都清，这一颗不清，是有意为之）：撤的要是正在跑那一笔，
     * 在飞那一圈只剩那颗闸能管 —— 清了它就等于「屏上已经说撤掉了，而它一路写到最后一页才停」。
     * [caps] 那枚只服务取证种子的页上限可以清：那一档要停的是「这一趟」，而行已经不在表里了。
     */
    fun drop(root: File, taskId: String): Int {
        val task = _tasks.value.firstOrNull { it.taskId == taskId } ?: return 0
        val pages = desktopComicQueuePagesOnDisk(desktopComicQueueDir(root, task))
        _tasks.update { current -> current.filterNot { it.taskId == taskId } }
        caps.remove(taskId)
        save(root)
        return pages
    }

    private fun mutate(taskId: String, block: (DesktopComicQueueTask) -> DesktopComicQueueTask) {
        val stamp = System.currentTimeMillis()
        _tasks.update { current -> current.map { if (it.taskId == taskId) block(it).copy(updateTime = stamp) else it } }
    }

    private fun mutateAll(block: (DesktopComicQueueTask) -> DesktopComicQueueTask) {
        val stamp = System.currentTimeMillis()
        _tasks.update { current -> current.map { block(it).copy(updateTime = stamp) } }
    }

    /** 每次变更**全量刷盘**（对岸 `:624` `:653` `:684` 那一形），走那颗原子写件。 */
    private fun save(root: File) {
        if (!root.isDirectory && !root.mkdirs()) return
        desktopComicAtomicWriteTo(File(root, DESKTOP_COMIC_QUEUE_FILE_NAME), desktopComicQueueTasksJson(_tasks.value).toByteArray())
    }
}
