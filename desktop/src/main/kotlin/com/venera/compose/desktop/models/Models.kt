package com.venera.compose.desktop.models

data class ComicItem(
    val id: String,
    val title: String,
    val author: String,
    val coverUrl: String,
    val tags: List<String>,
    val rating: String,
    val description: String,
    val sourceName: String = "拷贝漫画",
    val latestChapter: String = "第 128 话",
    val updateTime: String = "10分钟前",
    val hasUpdate: Boolean = true,
    val chapters: List<String> = (1..60).map { "第 $it 话" }.reversed()
)

data class HistoryItem(
    val comic: ComicItem,
    val lastReadChapter: String,
    val pageProgress: String,
    val readTime: String
)

data class ReadingStats(
    val todayPages: Int = 42,
    val weekPages: Int = 286,
    val streakDays: Int = 12
)

enum class SourceConnectionStatus {
    OK,
    CHECKING,
    ERROR
}

data class SourceStatus(
    val name: String,
    val key: String,
    val status: SourceConnectionStatus,
    val message: String? = null
)

// 丰富写实的模拟数据集
object MockData {
    val sampleComics = listOf(
        ComicItem(
            id = "1",
            title = "葬送的芙莉莲 (Sousou no Frieren)",
            author = "山田钟人 / 阿部司",
            coverUrl = "https://images.unsplash.com/photo-1579783902614-a3fb3927b675?w=500&q=80",
            tags = listOf("奇幻", "治愈", "冒险", "史诗"),
            rating = "9.8",
            description = "打倒魔王之后的勇者一行的后日谈。作为长寿精灵的魔法使芙莉莲，在勇者辛美尔逝世后，开始踏上探索生命与人心的崭新旅程。",
            sourceName = "拷贝漫画",
            latestChapter = "第 135 话 帝国的暗流",
            updateTime = "2小时前",
            hasUpdate = true,
            chapters = (1..135).map { "第 $it 话" }.reversed()
        ),
        ComicItem(
            id = "2",
            title = "胆大党 (Dandadan)",
            author = "龙幸伸",
            coverUrl = "https://images.unsplash.com/photo-1534447677768-be436bb09401?w=500&q=80",
            tags = listOf("热血", "幽灵", "外星人", "搞笑"),
            rating = "9.5",
            description = "相信幽灵存在的女高中生绫濑桃与相信外星人存在的厄卡伦，因一场赌注卷入了一连串超自然灵异大冒险！",
            sourceName = "哔咔漫画",
            latestChapter = "第 168 话 全力反击",
            updateTime = "5小时前",
            hasUpdate = true,
            chapters = (1..168).map { "第 $it 话" }.reversed()
        ),
        ComicItem(
            id = "3",
            title = "孤独摇滚！(Bocchi the Rock!)",
            author = "滨路晶",
            coverUrl = "https://images.unsplash.com/photo-1511671782779-c97d3d27a1d4?w=500&q=80",
            tags = listOf("音乐", "萌系", "搞笑", "日常"),
            rating = "9.7",
            description = "极度社恐却拥有精湛吉他技巧的后藤一里，在公园被伊地知虹夏偶然拉入束带乐队，开启闪闪发光的青春摇滚乐章。",
            sourceName = "MangaDex",
            latestChapter = "第 72 话 下北泽夏日祭",
            updateTime = "昨天",
            hasUpdate = false,
            chapters = (1..72).map { "第 $it 话" }.reversed()
        ),
        ComicItem(
            id = "4",
            title = "电锯人 第二部 (Chainsaw Man)",
            author = "藤本树",
            coverUrl = "https://images.unsplash.com/photo-1607604276583-eef5d076aa5f?w=500&q=80",
            tags = listOf("黑暗", "战斗", "邪典"),
            rating = "9.4",
            description = "背负巨额债务的少年电次与链锯恶魔波奇塔相依为命，在经历背叛后重生为拥有电锯之心的恶魔猎人，杀入荒诞与残酷的世界。",
            sourceName = "拷贝漫画",
            latestChapter = "第 185 话 老化之魔",
            updateTime = "3天前",
            hasUpdate = true,
            chapters = (1..185).map { "第 $it 话" }.reversed()
        ),
        ComicItem(
            id = "5",
            title = "迷宫饭 (Dungeon Meshi)",
            author = "九井谅子",
            coverUrl = "https://images.unsplash.com/photo-1518709268805-4e9042af9f23?w=500&q=80",
            tags = listOf("美食", "奇幻", "冒险", "完结"),
            rating = "9.9",
            description = "在迷宫深处被红龙吞噬的妹妹……为了拯救妹妹，莱欧斯一行人决定在粮饷耗尽的情况下吃魔物度日！",
            sourceName = "拷贝漫画",
            latestChapter = "第 97 话 迷宫饭 (完结)",
            updateTime = "已完结",
            hasUpdate = false,
            chapters = (1..97).map { "第 $it 话" }.reversed()
        ),
        ComicItem(
            id = "6",
            title = "间谍过家家 (SPY×FAMILY)",
            author = "远藤达哉",
            coverUrl = "https://images.unsplash.com/photo-1563089145-599997674d42?w=500&q=80",
            tags = listOf("搞笑", "家庭", "温情", "动作"),
            rating = "9.6",
            description = "西国顶尖间谍“黄昏”为了执行任务，组建了一个由读心超能力者女儿与杀手妻子构成的虚假家庭，啼笑皆非的日常由此展开！",
            sourceName = "MangaDex",
            latestChapter = "第 106 话 游乐园大骚动",
            updateTime = "4天前",
            hasUpdate = false,
            chapters = (1..106).map { "第 $it 话" }.reversed()
        )
    )

    val historyItems = listOf(
        HistoryItem(sampleComics[0], "第 134 话", "页 18/24", "10分钟前"),
        HistoryItem(sampleComics[1], "第 167 话", "页 2/22", "1小时前"),
        HistoryItem(sampleComics[2], "第 71 话", "页 8/16", "昨天"),
        HistoryItem(sampleComics[3], "第 184 话", "页 14/20", "3天前"),
        HistoryItem(sampleComics[4], "第 97 话", "已读完", "1周前")
    )

    val comicSources = listOf(
        SourceStatus("拷贝漫画 (Copymanga)", "copymanga", SourceConnectionStatus.OK),
        SourceStatus("哔咔漫画 (Picacg)", "picacg", SourceConnectionStatus.OK),
        SourceStatus("MangaDex", "mangadex", SourceConnectionStatus.OK),
        SourceStatus("E-Hentai / ExHentai", "ehentai", SourceConnectionStatus.OK),
        SourceStatus("禁漫天堂 (JMComic)", "jmcomic", SourceConnectionStatus.ERROR, "请检查代理"),
        SourceStatus("NHentai", "nhentai", SourceConnectionStatus.OK)
    )

    val readingStats = ReadingStats(todayPages = 38, weekPages = 246, streakDays = 7)

    val searchHistory = listOf("芙莉莲", "胆大党", "藤本树", "摇滚", "迷宫饭", "百合", "热血")

    val trendingTags = listOf("恋爱", "热血", "搞笑", "奇幻", "悬疑", "百合", "转生", "治愈", "科幻", "校园", "冒险", "全彩")

    val favoriteFolders = listOf("默认收藏夹", "正在追更", "已看完结", "稍后想看")

    val categories = listOf(
        "热血少年" to listOf("战斗", "冒险", "武侠", "魔幻", "科幻", "竞技"),
        "奇幻探索" to listOf("异世界", "转生", "魔法", "史诗", "勇者", "迷宫"),
        "恋爱日常" to listOf("校园", "搞笑", "治愈", "百合", "日常", "少女"),
        "悬疑剧场" to listOf("推理", "悬疑", "惊悚", "黑暗", "智斗", "犯罪")
    )
}
