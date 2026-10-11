package com.venera.engineprobe

/**
 * `comic.loadInfo` 那一坨答复读回来的四样东西。
 *
 * 为什么不做成 `Pair<String, List<Episode>>`（它从前就是那一形）：详情那一屏要摆**简介**，
 * 而简介就在同一份 `data` 里 —— 再发一次 `loadInfo` 只为补那一栏等于把同一次 JS 往返跑两遍，
 * 而"两遍之间源换了答复"是量不出来的那种错。
 *
 * [cover] 那一栏是批 82.6 补的：桌面从前没读它，于是写进 `comic_history` 的封面恒为空，
 * 而那张表两端共用（键名与"缺栏算空串"的口径都逐字照 Android 侧 `JsComicSource.getComicDetails`，
 * 判据钉着那一处，桌面不许自己多认一个候选键）。
 */
data class ComicInfo(
    val title: String,
    val description: String,
    val episodes: List<EngineSession.Episode>,
    val cover: String,
)

/**
 * 纯解析：信封 → [ComicInfo]。**解析语义逐条对齐 Android 侧 `JsComicSource.getComicDetails`**
 * （同一份 JS 答复在两处读成两种形状，就是"桌面看到的章节比手机少/反"那一族缺陷的成因）。
 *
 * 抽成顶层纯函数的理由：`details` 那一层只剩"发一次 JS"，判据能直接喂假信封进来。
 * 留在方法里就只能起 GraalJS，而那要真源、真网络，判据一条也写不出来。
 */
fun parseComicLoadInfo(comicId: String, env: Map<*, *>): ComicInfo {
    if (env["success"] == false) error("loadInfo 失败: ${env["error"]}")
    val data = env["data"] as? Map<*, *> ?: error("loadInfo 没有 data")
    val eps = mutableListOf<EngineSession.Episode>()
    when (val raw = data["chapters"] ?: data["episodes"]) {
        // Map 时 **key=话号、value=话名**（这一条从前被写过一次反的：屏上列表照摆，只有点开是错的）；
        // 值还是 Map 的就是分组表，按外层次序摊平。
        is Map<*, *> -> raw.forEach { (groupKey, groupVal) ->
            if (groupVal is Map<*, *>) {
                groupVal.forEach { (k, v) -> eps.add(EngineSession.Episode(v.toString(), k.toString())) }
            } else {
                eps.add(EngineSession.Episode(groupVal.toString(), groupKey.toString()))
            }
        }

        is List<*> -> raw.forEachIndexed { i, item ->
            if (item is Map<*, *>) {
                eps.add(
                    EngineSession.Episode(
                        item["title"]?.toString() ?: "第 $i 话",
                        item["id"]?.toString() ?: i.toString(),
                    ),
                )
            }
        }
    }
    // 一章都没有时补一枚占位章而**话号沿用漫画号**：`pages` 吃 `placeholder` 决定第三个实参交 `null`
    // 还是交货号，留空表等于那一本永远翻不出页。
    if (eps.isEmpty()) eps.add(EngineSession.Episode("", comicId, placeholder = true))
    return ComicInfo(
        title = data["title"]?.toString().orEmpty(),
        description = data["description"]?.toString().orEmpty(),
        episodes = eps,
        // 只认 `cover` 这一栏，与对岸 `getComicDetails` 同一键名、同一兜底（缺栏 = 空串）。
        cover = data["cover"]?.toString().orEmpty(),
    )
}
