package com.venera.desktop.gallery.data

/**
 * 第一百一十三批 · 「什么时候该再读一次盘」那一枚键。
 *
 * 批 111 给「缺 N 页」那一行装了「补齐」把手、批 112 让它排的是原号，而那一屏的**重读时机**当时仍挂在
 * "入队那一刻"（`DesktopComicDownloadsPane.kt` 里那颗 `pass += 1`）—— 那一刻盘上一颗文件都没多，
 * 重数出来的还是同一句「缺 2 页」。批 112 的包内 A 趟把这一格念得清清楚楚：
 * `D_下载 … 页=2/30 全=是` 之后再也未出现一行 `D_本机下载`，而盘上那时已经是 30/30。
 *
 * ⚠️ 删除那一路是**同步**改盘的（返回就是删完了），所以它那次推进是对的；补齐是异步的（入队 ≠ 落盘）。
 *    这一枚键只服务异步那一路：**任何一笔落到终局**就意味着盘上多了/少了东西 ⇒ 该重读。
 * ⚠️ 逐页刷进度**不算**迁移（批 110 那把尺子每读一次盘就要把每一页的文件头读一遍，28 页 × 28 次是纯浪费），
 *    所以这里只数三档终局，不吃 `downloadedPages`。
 * ⚠️ 它只回答"何时再读一次"，**不是屏上那些行的数据来源** —— 行仍只从读盘那件来（组合期读队列会把
 *    「已下全」绑到队列状态上，批 112 的 ⑨ 钉的就是那一格）。
 */
internal fun desktopComicShelfRerollKey(tasks: List<DesktopComicQueueTask>): String =
    "完=" + tasks.count { it.status == DesktopComicQueueStatus.DONE } +
        " 败=" + tasks.count { it.status == DesktopComicQueueStatus.FAILED } +
        " 停=" + tasks.count { it.status == DesktopComicQueueStatus.STOPPED }
