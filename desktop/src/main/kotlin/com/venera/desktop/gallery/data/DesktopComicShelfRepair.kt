package com.venera.desktop.gallery.data

import java.io.File

/**
 * 第一百一十一批 · 「本机已下载」那一行的**补齐**那一跳。
 *
 * 批 110 把「这一页在不在」收成一把尺子之后，`缺 N 页` 那一档会**多起来**（闸装好之前落盘的过盾页
 * 从此不算一页），而那一屏唯一那枚把手是**删** —— 说了缺却不给补的路，用户只能回探索、点开那本、
 * 再长按同一话。这一颗就是把那一行自己变成入口。
 *
 * ⚠️ 两枚把手各只改一件事（批 105 立的那条）：这一枚**只入队**，盘上一颗文件都不动；
 * 删那一枚只删而不动队列表。混起来就会造出"点补齐结果把已有的 28 页清了"那种事故。
 * ⚠️ `comicTitle` 那一格盘上**没有**（`DesktopComicDownloadedGroup.label` 就是号，目录名也是净化过的号），
 * 于是递的是号 —— 它不上屏：队列那一行念的是 `第 N 话「章名」 · 档位 · 页 · 本子 <comicId>`
 * （`DesktopComicQueuePane.kt:176-177`），所以不会把号当题名念给用户看。
 */
internal data class DesktopComicRepairArgs(
    val sourceKey: String,
    val comicId: String,
    val comicTitle: String,
    val comicCover: String,
    val chapterId: String,
    val chapterTitle: String,
    val chapterOrder: Int,
    val placeholder: Boolean,
)

/** 只有缺页那一档才摆这枚把手 —— 齐了的话摆它就是多一枚没意义的按钮。 */
internal fun desktopComicRepairShouldShow(chapter: DesktopComicDownloadedChapter): Boolean = !chapter.complete

/** 把手上那一行字：说清补的是"还缺的那几页"，而不是"重下整话"。 */
internal fun desktopComicRepairLabel(chapter: DesktopComicDownloadedChapter): String =
    "补齐这一话 · 还缺 ${chapter.expectedPages - chapter.writtenPages} 页"

/**
 * 行模型 → 入队那八格：批 112 起这一跳**只有一条路** —— 先问回收那颗件要递哪一枚号
 * （`desktopComicShelfRepairArgsOf`，rounds `desktop-shelf-raw-id-2026-10-15.md`）。
 * 这里不再另摆一颗同义的 `…ArgsOf(chapter)`：两把尺子量同一件事，迟早有一把量错。
 */

/** 按完之后「上一次的果」那一格：缺几页、盘上留几页、递出去的是哪一形的号都得说清。 */
internal fun desktopComicRepairResultLine(chapter: DesktopComicDownloadedChapter, args: DesktopComicRepairArgs): String =
    "已排进下载队列：第 ${chapter.order + 1} 话「${chapter.title}」还缺 ${chapter.expectedPages - chapter.writtenPages} 页" +
        " · 盘上那 ${chapter.writtenPages} 页一颗都没动" +
        desktopComicShelfRepairIdNote(args.comicId, args.comicId != chapter.comicId)

/**
 * 把那一行排进队列表，交回上屏那一句。
 *
 * 重复按不会变成两笔（`enqueue` 自己按任务号互认，批 101 那颗口径），而盘上那些页一颗都不动 ——
 * 续跑那一路会跳过已齐的页，只取缺的那几页（批 110 那把尺子）。
 * ⚠️ `index` 由调用点显式递进来（不是让这一跳自己去读队列单例）：表里有没有那笔原号是**盘上事实**，
 * 让判式能钉住两种情形各喂一次，才不会红绿随用例顺序变。
 */
internal fun desktopComicRepairChapter(
    downloadsRoot: File, chapter: DesktopComicDownloadedChapter, index: DesktopComicShelfRawIdIndex,
): String {
    val args = desktopComicShelfRepairArgsOf(chapter, index)
    DesktopComicDownloadQueue.enqueue(
        downloadsRoot, args.sourceKey, args.comicId, args.comicTitle, args.comicCover,
        args.chapterId, args.chapterTitle, args.chapterOrder, args.placeholder,
    )
    return desktopComicRepairResultLine(chapter, args)
}

/** `--repair=` 那一枚种子**落空**时的那一句（号与话号在盘上那棵树里找不到 ⇒ 不排队也不猜）。 */
internal fun desktopComicRepairSeedMissLine(comicId: String, chapterId: String): String =
    "D_补齐种子 号=$comicId 话=$chapterId 本机没有这一话 ⇒ 不排队（盘上有什么去看「本机已下载」那一屏）"

/** `--repair=` 命中时的那一句：缺几页、留几页都从**按下之前**那一行现取（补完之后那一格自己改口）。 */
internal fun desktopComicRepairSeedHitLine(
    comicId: String, chapterId: String, chapter: DesktopComicDownloadedChapter, index: DesktopComicShelfRawIdIndex,
): String {
    val (used, resolved) = desktopComicShelfRepairComicId(chapter, index)
    return "D_补齐种子 号=$comicId 话=$chapterId 缺=${chapter.expectedPages - chapter.writtenPages}" +
        " 留=${chapter.writtenPages} 排队=是" + desktopComicShelfRepairIdNote(used, resolved)
}
