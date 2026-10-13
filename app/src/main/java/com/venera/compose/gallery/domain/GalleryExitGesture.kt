package com.venera.compose.gallery.domain

/**
 * 大图页**跟手返回**的决策层：把「系统返回事件的先后」与「像素到没到」收成一颗纯状态机。
 *
 * ## 为什么这一层不能就地写在 UI 里
 *
 * 它要防的四件事全是**时序**缺陷，不是算术缺陷：屏上看一眼看不出来，而单测里又构造不出那扇窗口。
 * 1. **杂散 / 乱序事件凭空启动一次假手势** —— 澎湃 / HyperOS 在确认返回之后还会补发进度。
 *    补发被当成新手势时，离场那趟会跟别的动作抢同一份画面，症状是重影、返回后一片空白。
 *    闸门：只有**先起手、再进进度**才激活；[GalleryExitGesturePhase.Committed] 之后一律丢。
 * 2. **像素还没回来就让页面让开** —— 离场那一帧要 `PixelCopy`（异步，一到数帧），
 *    那几帧里页面必须**钉在 0**，否则屏上先空一下。闸门：[GalleryExitGesturePhase.Arming] 不产出视觉指令。
 * 3. **截不到像素时假装在飞** —— 交回失败就直接 finish（把"我没能飞"说成"飞完了"是假开关）。
 * 4. **确认之后又补发取消**（或反之）—— 已确认的那笔不再被回滚翻掉。
 *
 * ## 轴
 *
 * 视觉进度与系统手势进度**同向**：`0 = 页面还在原位`、`1 = 完全收进卡片`（与 [exitExpansion] 的
 * `progress` 同一根轴，这里刻意不再反一次 —— 两处各反一次就变成"看起来对、其实差一格"）。
 *
 * 纯 Kotlin、不碰 Android/Compose 类型，口径同 [GalleryExitExpansion]：能脱 gradle 单跑
 * （`_probe/l0/run-judgment-tests.sh`）。
 */
enum class GalleryExitGesturePhase {
    /** 没在返回。 */
    Idle,

    /** 已起手、像素在路上：页面**不许**动。 */
    Arming,

    /** 飞着，视觉进度跟手指。 */
    Flying,

    /** 已确认返回：此后一切进度 / 取消事件都丢，只等收尾并 finish。 */
    Committed,
}

/**
 * 决策层交给 UI 的指令，**按序执行**，其中 [SetVisualTarget] 要等动画收敛再走下一条
 * （"先落到位再 finish"正是这一层要的次序，随手并发就变成半路抹掉飞行体）。
 */
sealed interface GalleryExitGestureCommand {
    /** 起手要做的事：摘窗口模糊 + 起 `PixelCopy` + 标记"正在离开"。 */
    data object Prepare : GalleryExitGestureCommand

    /** 把离场那一帧放回去：玻璃底重新挂上、取消"正在离开"。 */
    data object Restore : GalleryExitGestureCommand

    /**
     * 走原来那趟**自己驱动**的固定弹簧（`spring(StiffnessMediumLow)` 推到 1 再 finish）：
     * 三键导航点返回、`adb` 送按键这类**没有进度事件**的返回用它 —— 没有手指可跟，也要有同一趟 hero。
     *
     * 它**自己备料**（本体就是页里那条 `leavePage()`，内含"截不到帧就直接 finish"的退路），
     * 所以这一条前面**不配** [Prepare]：配了就是同一起手截两帧，白烧一次 `PixelCopy`。
     */
    data object FlySelfDriven : GalleryExitGestureCommand

    /** 本页收尾并 finish。 */
    data object Finish : GalleryExitGestureCommand

    /** 视觉进度落到这一档：UI 侧用**高刚度弹簧**追赶，不 snapTo —— 系统派发稀疏时 snap 会整页跳位。 */
    data class SetVisualTarget(val target: Float) : GalleryExitGestureCommand
}

/** 一次决策：新状态 + 要 UI 执行的指令序列。 */
data class GalleryExitGestureDecision(
    val phase: GalleryExitGesturePhase,
    val commands: List<GalleryExitGestureCommand>,
)

/** 决策层的输入事件。[GalleryExitGestureEvent.Started] 与 [Progress] 带系统手势进度（0..1）。 */
sealed interface GalleryExitGestureEvent {
    /** 起手：系统 `onBackStarted` 之后的第一笔进度。 */
    data class Started(val progress: Float) : GalleryExitGestureEvent

    /** 拖动中的后续进度。 */
    data class Progress(val progress: Float) : GalleryExitGestureEvent

    /** **没有**进度事件的一笔返回（三键导航、`adb shell input keyevent`）。 */
    data object BackWithoutProgress : GalleryExitGestureEvent

    /** 松手确认。 */
    data object Commit : GalleryExitGestureEvent

    /** 松手回弹。 */
    data object Cancel : GalleryExitGestureEvent

    /** `captureRegion` 回报：拿到了离场那一帧。 */
    data object PixelsReady : GalleryExitGestureEvent

    /** `captureRegion` 回报：拿不到（超出窗口、窗口没有 surface）。 */
    data object PixelsFailed : GalleryExitGestureEvent
}

/** 越界钳进 0..1，不外推（回弹过头、系统给出 >1 的进度都是同一档）。 */
fun clampExitGestureProgress(progress: Float): Float = progress.coerceIn(0f, 1f)

/**
 * 单步决策。[lastProgress] 是**最近一次收到的手势进度**（arming 期间攒着，像素一到就补到那一档，
 * 于是手指已经拖出去半屏时不会出现"帧到了还得从头飞"）。
 *
 * 纯函数：`(phase, lastProgress, event) -> (phase', commands)`。UI 侧只管执行，
 * 不再自己判"这一笔该不该理"。
 */
fun nextExitGesture(
    phase: GalleryExitGesturePhase,
    lastProgress: Float,
    event: GalleryExitGestureEvent,
): GalleryExitGestureDecision = when (event) {
    is GalleryExitGestureEvent.Started -> when (phase) {
        // 只有稳态 Idle 能被起手激活；其余档要么已经在处理，要么已经确认（闸门 1）。
        GalleryExitGesturePhase.Idle -> GalleryExitGestureDecision(
            GalleryExitGesturePhase.Arming,
            listOf(GalleryExitGestureCommand.Prepare),
        )

        else -> GalleryExitGestureDecision(phase, emptyList())
    }

    is GalleryExitGestureEvent.Progress -> when (phase) {
        // 没有起手就来的进度（乱序 / 杂散）不激活；确认之后的补发也不理（闸门 1 的另一半）。
        GalleryExitGesturePhase.Idle, GalleryExitGesturePhase.Committed ->
            GalleryExitGestureDecision(phase, emptyList())
        // 像素没到：视觉钉在 0，进度先攒着（闸门 2）。
        GalleryExitGesturePhase.Arming -> GalleryExitGestureDecision(phase, emptyList())
        GalleryExitGesturePhase.Flying -> GalleryExitGestureDecision(
            phase,
            listOf(GalleryExitGestureCommand.SetVisualTarget(clampExitGestureProgress(event.progress))),
        )
    }

    GalleryExitGestureEvent.BackWithoutProgress -> when (phase) {
        GalleryExitGesturePhase.Committed -> GalleryExitGestureDecision(phase, emptyList())
        // 三档都只交 FlySelfDriven：它自带备料与退路。前面再配一条 Prepare 会先把 leaving 置真，
        // 而自驱那一趟开头就是 if (leaving) return —— 结果一帧都不飞（判式抓到过这一条）。
        GalleryExitGesturePhase.Flying, GalleryExitGesturePhase.Arming, GalleryExitGesturePhase.Idle ->
            GalleryExitGestureDecision(
                GalleryExitGesturePhase.Committed,
                listOf(GalleryExitGestureCommand.FlySelfDriven),
            )
    }

    GalleryExitGestureEvent.Commit -> when (phase) {
        GalleryExitGesturePhase.Committed -> GalleryExitGestureDecision(phase, emptyList())
        // 像素还没到就松手确认：没有帧可画，直接 finish（不硬飞，见闸门 3）。
        GalleryExitGesturePhase.Arming, GalleryExitGesturePhase.Idle -> GalleryExitGestureDecision(
            GalleryExitGesturePhase.Committed,
            listOf(GalleryExitGestureCommand.Finish),
        )

        GalleryExitGesturePhase.Flying -> GalleryExitGestureDecision(
            GalleryExitGesturePhase.Committed,
            listOf(GalleryExitGestureCommand.SetVisualTarget(1f), GalleryExitGestureCommand.Finish),
        )
    }

    GalleryExitGestureEvent.Cancel -> when (phase) {
        // 已确认的那笔不许被补发的取消翻掉（闸门 4）。
        GalleryExitGesturePhase.Committed, GalleryExitGesturePhase.Idle ->
            GalleryExitGestureDecision(phase, emptyList())

        else -> GalleryExitGestureDecision(
            GalleryExitGesturePhase.Idle,
            listOf(GalleryExitGestureCommand.SetVisualTarget(0f), GalleryExitGestureCommand.Restore),
        )
    }

    GalleryExitGestureEvent.PixelsReady -> when (phase) {
        GalleryExitGesturePhase.Arming -> GalleryExitGestureDecision(
            GalleryExitGesturePhase.Flying,
            listOf(GalleryExitGestureCommand.SetVisualTarget(clampExitGestureProgress(lastProgress))),
        )

        else -> GalleryExitGestureDecision(phase, emptyList())
    }

    GalleryExitGestureEvent.PixelsFailed -> when (phase) {
        GalleryExitGesturePhase.Arming -> GalleryExitGestureDecision(
            GalleryExitGesturePhase.Committed,
            listOf(GalleryExitGestureCommand.Finish),
        )

        else -> GalleryExitGestureDecision(phase, emptyList())
    }
}
