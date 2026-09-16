package com.venera.compose

import android.os.Bundle
import android.view.HapticFeedbackConstants
import androidx.activity.ComponentActivity
import androidx.activity.compose.PredictiveBackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.theme.MiuixTheme
import com.venera.compose.components.VeneraAmbientBackground
import com.venera.compose.components.VeneraFloatingNavBar
import com.venera.compose.components.VeneraNavTab
import com.venera.compose.reader.*
import com.venera.compose.data.db.*
import com.venera.compose.data.prefs.*

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

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MiuixTheme {
                VeneraComposeApp()
            }
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun VeneraComposeApp() {
    var currentTab by remember { mutableStateOf(VeneraNavTab.HOME) }
    var selectedComic by remember { mutableStateOf<ComicItem?>(null) }
    var activeReadingSession by remember { mutableStateOf<ReaderSession?>(null) }
    val view = LocalView.current

    if (activeReadingSession != null) {
        VeneraReaderScreen(
            session = activeReadingSession!!,
            onBack = {
                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                activeReadingSession = null
            }
        )
    } else {
        VeneraAmbientBackground {
            SharedTransitionLayout(modifier = Modifier.fillMaxSize()) {
                AnimatedContent(
                    targetState = selectedComic,
                    transitionSpec = {
                        fadeIn(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) togetherWith
                        fadeOut(animationSpec = spring(stiffness = Spring.StiffnessMediumLow))
                    },
                    label = "ScreenTransition"
                ) { targetComic ->
                    if (targetComic == null) {
                        Scaffold(
                            containerColor = Color.Transparent,
                            topBar = {
                                TopAppBar(
                                    title = when (currentTab) {
                                        VeneraNavTab.HOME -> "Venera"
                                        VeneraNavTab.SEARCH -> "搜索与发现"
                                        VeneraNavTab.FAVORITES -> "我的收藏"
                                        VeneraNavTab.EXPLORE -> "全站探索"
                                        VeneraNavTab.CATEGORIES -> "分类索引"
                                        VeneraNavTab.SETTINGS -> "设置与偏好"
                                    }
                                )
                            },
                            bottomBar = {
                                VeneraFloatingNavBar(
                                    currentTab = currentTab,
                                    onTabSelected = { tab ->
                                        currentTab = tab
                                    }
                                )
                            }
                        ) { innerPadding ->
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(innerPadding)
                            ) {
                                when (currentTab) {
                                    VeneraNavTab.HOME -> AndroidHomeScreen(
                                        animatedVisibilityScope = this@AnimatedContent,
                                        onSelect = {
                                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                            selectedComic = it
                                        }
                                    )
                                    VeneraNavTab.SEARCH -> AndroidSearchScreen(
                                        animatedVisibilityScope = this@AnimatedContent,
                                        onSelect = {
                                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                            selectedComic = it
                                        }
                                    )
                                    VeneraNavTab.FAVORITES -> AndroidFavoritesScreen(
                                        animatedVisibilityScope = this@AnimatedContent,
                                        onSelect = {
                                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                            selectedComic = it
                                        }
                                    )
                                    VeneraNavTab.EXPLORE -> AndroidExploreScreen(
                                        animatedVisibilityScope = this@AnimatedContent,
                                        onSelect = {
                                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                            selectedComic = it
                                        }
                                    )
                                    VeneraNavTab.CATEGORIES -> AndroidCategoriesScreen()
                                    VeneraNavTab.SETTINGS -> AndroidSettingsScreen()
                                }
                            }
                        }
                    } else {
                        AndroidComicDetailScreen(
                            comic = targetComic,
                            animatedVisibilityScope = this@AnimatedContent,
                            onBack = {
                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                selectedComic = null
                            },
                            onStartReading = { chapterIndex, pageIndex ->
                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                activeReadingSession = SampleReaderData.createSampleSession(
                                    comicId = targetComic.id,
                                    comicTitle = targetComic.title,
                                    coverUrl = targetComic.coverUrl,
                                    chapterNames = targetComic.chapters,
                                    initialIndex = chapterIndex,
                                    initialPageIndex = pageIndex
                                )
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun MiuixSectionHeader(
    title: String,
    onTap: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable { onTap() }
            .padding(horizontal = 4.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = "›",
            fontSize = 20.sp,
            color = MiuixTheme.colorScheme.onBackgroundVariant
        )
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun SharedTransitionScope.AndroidHomeScreen(
    animatedVisibilityScope: AnimatedVisibilityScope,
    onSelect: (ComicItem) -> Unit
) {
    val context = LocalContext.current
    val historyList by HistoryDao.getInstance(context).historyFlow.collectAsState()
    var selectedStatsType by remember { mutableIntStateOf(0) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 分区 1：今日推荐 (_TodayUpdates)
        item {
            Column {
                MiuixSectionHeader(title = "今日推荐", onTap = { })
                Spacer(modifier = Modifier.height(4.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(sampleComics.take(4)) { comic ->
                        Card(
                            modifier = Modifier
                                .width(264.dp)
                                .height(152.dp)
                                .clickable { onSelect(comic) }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                AsyncImage(
                                    model = comic.coverUrl,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(width = 96.dp, height = 136.dp)
                                        .sharedElement(
                                            sharedContentState = rememberSharedContentState(key = "image-${comic.id}"),
                                            animatedVisibilityScope = animatedVisibilityScope
                                        )
                                        .clip(RoundedCornerShape(12.dp)),
                                    contentScale = ContentScale.Crop
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight(),
                                    verticalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column {
                                        Text(
                                            text = comic.title,
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 2
                                        )
                                        Spacer(modifier = Modifier.height(3.dp))
                                        Text(
                                            text = "${comic.sourceName} · ${comic.latestChapter}",
                                            fontSize = 12.sp,
                                            color = MiuixTheme.colorScheme.onBackgroundVariant,
                                            maxLines = 1
                                        )
                                    }
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(Color(0xFFE53935))
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = "NEW",
                                                color = Color.White,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                        Text(
                                            text = comic.updateTime,
                                            fontSize = 11.sp,
                                            color = MiuixTheme.colorScheme.onBackgroundVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 分区 2：阅读统计 (_MiuixReadingStats)
        item {
            Column {
                MiuixSectionHeader(title = "阅读统计", onTap = { })
                Spacer(modifier = Modifier.height(4.dp))
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(text = "42", fontSize = 21.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(text = "今日页数", fontSize = 12.sp, color = MiuixTheme.colorScheme.onBackgroundVariant)
                        }
                        Box(modifier = Modifier.width(1.dp).height(24.dp).background(MiuixTheme.colorScheme.onBackgroundVariant.copy(alpha = 0.2f)))
                        Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(text = "286", fontSize = 21.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(text = "本周累计", fontSize = 12.sp, color = MiuixTheme.colorScheme.onBackgroundVariant)
                        }
                        Box(modifier = Modifier.width(1.dp).height(24.dp).background(MiuixTheme.colorScheme.onBackgroundVariant.copy(alpha = 0.2f)))
                        Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(text = "12 天", fontSize = 21.sp, fontWeight = FontWeight.Bold, color = MiuixTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(text = "连续打卡", fontSize = 12.sp, color = MiuixTheme.colorScheme.onBackgroundVariant)
                        }
                    }
                }
            }
        }

        // 分区 3：历史记录 (_MiuixHistory)
        item {
            Column {
                MiuixSectionHeader(
                    title = if (historyList.isNotEmpty()) "历史记录 (${historyList.size})" else "历史记录",
                    onTap = { }
                )
                Spacer(modifier = Modifier.height(4.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (historyList.isNotEmpty()) {
                        items(historyList, key = { "hist-${it.comicId}" }) { record ->
                            val match = sampleComics.find { it.id == record.comicId }
                                ?: ComicItem(
                                    id = record.comicId,
                                    title = record.title,
                                    author = record.author,
                                    coverUrl = record.coverUrl,
                                    tags = listOf("历史"),
                                    rating = "9.5",
                                    description = "上次阅读至 ${record.lastChapterTitle}",
                                    sourceName = record.sourceName
                                )
                            Card(modifier = Modifier.width(112.dp).clickable { onSelect(match) }) {
                                Column(modifier = Modifier.padding(6.dp)) {
                                    AsyncImage(
                                        model = record.coverUrl,
                                        contentDescription = null,
                                        modifier = Modifier.fillMaxWidth().height(145.dp).clip(RoundedCornerShape(8.dp)),
                                        contentScale = ContentScale.Crop
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(text = record.title, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1)
                                    Text(text = "${record.lastChapterTitle} P.${record.lastPageIndex + 1}", fontSize = 11.sp, color = MiuixTheme.colorScheme.primary, maxLines = 1)
                                }
                            }
                        }
                    } else {
                        items(sampleComics.take(4)) { comic ->
                            Card(modifier = Modifier.width(112.dp).clickable { onSelect(comic) }) {
                                Column(modifier = Modifier.padding(6.dp)) {
                                    AsyncImage(
                                        model = comic.coverUrl,
                                        contentDescription = null,
                                        modifier = Modifier.fillMaxWidth().height(145.dp).clip(RoundedCornerShape(8.dp)),
                                        contentScale = ContentScale.Crop
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(text = comic.title, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1)
                                    Text(text = "未读", fontSize = 11.sp, color = MiuixTheme.colorScheme.onBackgroundVariant, maxLines = 1)
                                }
                            }
                        }
                    }
                }
            }
        }

        // 分区 4：漫画源网络状态 (_MiuixComicSources)
        item {
            Column {
                MiuixSectionHeader(title = "漫画源", onTap = { })
                Spacer(modifier = Modifier.height(4.dp))
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        listOf(
                            Triple("拷贝漫画", true, "48ms"),
                            Triple("哔咔漫画", true, "112ms"),
                            Triple("MangaDex", true, "186ms"),
                            Triple("禁漫天堂", false, "超时 (需代理)")
                        ).forEach { (name, ok, ping) ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 7.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(text = name, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(if (ok) Color(0xFF4CAF50) else Color(0xFFE53935))
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = ping,
                                        fontSize = 12.sp,
                                        color = if (ok) Color(0xFF4CAF50) else Color(0xFFE53935)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // 分区 5：本地漫画 (_MiuixLocal)
        item {
            Column {
                MiuixSectionHeader(title = "本地漫画", onTap = { })
                Spacer(modifier = Modifier.height(4.dp))
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(text = "本地与下载", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "已缓存 14 部作品 · 0 个下载任务",
                                fontSize = 12.sp,
                                color = MiuixTheme.colorScheme.onBackgroundVariant
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MiuixTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                            modifier = Modifier.clickable { }
                        ) {
                            Text(
                                text = "管理本地 ›",
                                fontSize = 12.sp,
                                color = MiuixTheme.colorScheme.primary,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
            }
        }

        // 分区 6：图片收藏 (_MiuixImageFavorites)
        item {
            Column {
                MiuixSectionHeader(title = "图片收藏", onTap = { })
                Spacer(modifier = Modifier.height(4.dp))
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "从 6 部漫画与 428 张图片中计算的偏好",
                            fontSize = 12.sp,
                            color = MiuixTheme.colorScheme.onBackgroundVariant
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf("标签", "画师", "作品").forEachIndexed { idx, typeName ->
                                val selected = selectedStatsType == idx
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = if (selected) MiuixTheme.colorScheme.primaryContainer else MiuixTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable { selectedStatsType = idx }
                                ) {
                                    Box(modifier = Modifier.padding(vertical = 6.dp), contentAlignment = Alignment.Center) {
                                        Text(
                                            text = typeName,
                                            fontSize = 12.sp,
                                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                            color = if (selected) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        val statsList = when (selectedStatsType) {
                            0 -> listOf("奇幻" to 86, "冒险" to 72, "治愈" to 58, "日常" to 42, "魔法" to 36)
                            1 -> listOf("山田钟人" to 68, "龙幸伸" to 54, "九井谅子" to 44, "藤本树" to 38)
                            else -> listOf("葬送的芙莉莲" to 138, "胆大党" to 112, "迷宫饭" to 97, "孤独摇滚" to 72)
                        }
                        statsList.forEach { (label, count) ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = label,
                                    fontSize = 12.sp,
                                    modifier = Modifier.width(90.dp),
                                    maxLines = 1
                                )
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(8.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(MiuixTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth((count / 140f).coerceIn(0.1f, 1f))
                                            .fillMaxHeight()
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(MiuixTheme.colorScheme.primary)
                                    )
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = count.toString(),
                                    fontSize = 11.sp,
                                    color = MiuixTheme.colorScheme.onBackgroundVariant,
                                    modifier = Modifier.width(30.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // 底部避让悬浮底栏
        item {
            Spacer(modifier = Modifier.height(68.dp))
        }
    }
}

// ==================== 2. 漫画详情页 (ComicPage) 1:1 复刻 ====================
@OptIn(ExperimentalSharedTransitionApi::class, ExperimentalLayoutApi::class)
@Composable
fun SharedTransitionScope.AndroidComicDetailScreen(
    comic: ComicItem,
    animatedVisibilityScope: AnimatedVisibilityScope,
    onBack: () -> Unit,
    onStartReading: (chapterIndex: Int, pageIndex: Int) -> Unit
) {
    val context = LocalContext.current
    val view = LocalView.current
    val favoriteDao = remember { FavoriteDao.getInstance(context) }
    val historyDao = remember { HistoryDao.getInstance(context) }

    val favorites by favoriteDao.favoritesFlow.collectAsState()
    val isFav = favorites.any { it.comicId == comic.id }

    val historyList by historyDao.historyFlow.collectAsState()
    val historyRecord = historyList.find { it.comicId == comic.id }

    var isReversed by remember { mutableStateOf(false) }

    PredictiveBackHandler { progress ->
        try {
            progress.collect { }
            onBack()
        } catch (_: Exception) { }
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = comic.title,
                navigationIcon = {
                    Box(modifier = Modifier.size(40.dp).clickable { onBack() }, contentAlignment = Alignment.Center) {
                        Text(text = "←", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    }
                },
                actions = {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clickable {
                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = "···", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 1. 顶部封面与作品标题信息
            item {
                Row(modifier = Modifier.fillMaxWidth()) {
                    AsyncImage(
                        model = comic.coverUrl,
                        contentDescription = null,
                        modifier = Modifier
                            .size(width = 104.dp, height = 144.dp)
                            .sharedElement(
                                sharedContentState = rememberSharedContentState(key = "image-${comic.id}"),
                                animatedVisibilityScope = animatedVisibilityScope
                            )
                            .shadow(
                                elevation = 10.dp,
                                shape = RoundedCornerShape(12.dp),
                                spotColor = Color.Black.copy(alpha = 0.40f)
                            )
                            .clip(RoundedCornerShape(12.dp)),
                        contentScale = ContentScale.Crop
                    )
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .height(144.dp),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = comic.title,
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                maxLines = 2,
                                lineHeight = 24.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = comic.author,
                                fontSize = 13.sp,
                                color = MiuixTheme.colorScheme.onBackgroundVariant,
                                maxLines = 1
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MiuixTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                            ) {
                                Text(
                                    text = comic.sourceName,
                                    fontSize = 11.sp,
                                    color = MiuixTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Text(
                            text = "★ ${comic.rating}",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFFFB800)
                        )
                    }
                }
            }

            // 2. 历史进度悬浮横幅 (若读过则展示)
            if (historyRecord != null) {
                item {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = MiuixTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onStartReading(historyRecord.lastChapterIndex, historyRecord.lastPageIndex)
                            }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(text = "📖", fontSize = 14.sp)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "上次阅读至 ${historyRecord.lastChapterTitle} 第 ${historyRecord.lastPageIndex + 1} 页",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            Text(
                                text = "继续阅读 ›",
                                fontSize = 13.sp,
                                color = MiuixTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }

            // 3. 原版经典彩色圆钮动作条 (_ActionButton)
            item {
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    item {
                        DetailActionButton(
                            icon = Icons.Outlined.PlayCircleOutline,
                            label = if (historyRecord != null) "继续" else "开始",
                            iconColor = Color(0xFFFF9800),
                            onClick = {
                                if (historyRecord != null) {
                                    onStartReading(historyRecord.lastChapterIndex, historyRecord.lastPageIndex)
                                } else {
                                    onStartReading(0, 0)
                                }
                            }
                        )
                    }
                    item {
                        DetailActionButton(
                            icon = Icons.Outlined.Download,
                            label = "下载",
                            iconColor = Color(0xFF00BCD4),
                            onClick = {
                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            }
                        )
                    }
                    item {
                        DetailActionButton(
                            icon = if (isFav) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                            label = if (isFav) "已收藏" else "收藏",
                            iconColor = Color(0xFF9C27B0),
                            isActive = isFav,
                            onClick = {
                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                favoriteDao.toggleFavorite(
                                    comicId = comic.id,
                                    title = comic.title,
                                    coverUrl = comic.coverUrl,
                                    author = comic.author,
                                    sourceName = comic.sourceName,
                                    latestChapter = comic.latestChapter
                                )
                            }
                        )
                    }
                    item {
                        DetailActionButton(
                            icon = Icons.Outlined.Comment,
                            label = "128",
                            iconColor = Color(0xFF4CAF50),
                            onClick = {
                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            }
                        )
                    }
                    item {
                        DetailActionButton(
                            icon = Icons.Outlined.Share,
                            label = "分享",
                            iconColor = Color(0xFF2196F3),
                            onClick = {
                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            }
                        )
                    }
                }
            }

            // 4. 移动端双主按钮 (下载 + 阅读)
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = { view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP) },
                        modifier = Modifier.weight(0.4f),
                        colors = ButtonDefaults.buttonColors(
                            color = MiuixTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                        ),
                        content = {
                            Text(text = "下载", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        }
                    )
                    Button(
                        onClick = {
                            if (historyRecord != null) {
                                onStartReading(historyRecord.lastChapterIndex, historyRecord.lastPageIndex)
                            } else {
                                onStartReading(0, 0)
                            }
                        },
                        modifier = Modifier.weight(0.6f),
                        content = {
                            Text(
                                text = if (historyRecord != null) "继续阅读" else "开始阅读 (第 1 话)",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    )
                }
            }

            // 5. 分类标签卡片
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(text = "标签与分类", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Spacer(modifier = Modifier.height(10.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            comic.tags.forEach { tag ->
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = MiuixTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
                                ) {
                                    Text(
                                        text = tag,
                                        fontSize = 12.sp,
                                        color = MiuixTheme.colorScheme.primary,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 6. 作品简介卡片
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(text = "作品简介", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = comic.description,
                            fontSize = 13.sp,
                            lineHeight = 22.sp,
                            color = MiuixTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            // 7. 章节目录卡片
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "章节目录 (共 ${comic.chapters.size} 话)",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MiuixTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                                modifier = Modifier.clickable { isReversed = !isReversed }
                            ) {
                                Text(
                                    text = if (isReversed) "正序 ↑" else "倒序 ↓",
                                    fontSize = 12.sp,
                                    color = MiuixTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val list = if (isReversed) comic.chapters.reversed() else comic.chapters
                            list.take(36).forEachIndexed { idx, chapter ->
                                val actualIdx = if (isReversed) comic.chapters.lastIndex - idx else idx
                                val isCurrentHistoryChapter = historyRecord?.lastChapterIndex == actualIdx
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (isCurrentHistoryChapter) {
                                        MiuixTheme.colorScheme.primary.copy(alpha = 0.2f)
                                    } else {
                                        MiuixTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                                    },
                                    modifier = Modifier.clickable {
                                        val pIndex = if (isCurrentHistoryChapter) historyRecord.lastPageIndex else 0
                                        onStartReading(actualIdx, pIndex)
                                    }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = chapter,
                                            fontSize = 12.sp,
                                            color = if (isCurrentHistoryChapter) {
                                                MiuixTheme.colorScheme.primary
                                            } else {
                                                MiuixTheme.colorScheme.onSurface
                                            },
                                            fontWeight = if (isCurrentHistoryChapter) FontWeight.Bold else FontWeight.Normal
                                        )
                                        if (isCurrentHistoryChapter) {
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(text = "•", color = MiuixTheme.colorScheme.primary, fontSize = 12.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 8. 最新评论卡片
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = "最新评论 (128)", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            Text(text = "查看全部 ›", fontSize = 12.sp, color = MiuixTheme.colorScheme.primary)
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        listOf(
                            Triple("勇者欣梅尔", "这集的分镜与演出太神了，治愈而感动！", "2小时前"),
                            Triple("魔法使菲伦", "追番到现在最满意的一部，坐等下一话更新。", "5小时前")
                        ).forEach { (user, comment, time) ->
                            Column(modifier = Modifier.padding(vertical = 6.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(text = user, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                    Text(text = time, fontSize = 11.sp, color = MiuixTheme.colorScheme.onBackgroundVariant)
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(text = comment, fontSize = 12.sp, color = MiuixTheme.colorScheme.onBackgroundVariant)
                            }
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

/**
 * 详情页彩色圆形动作按钮 (_ActionButton)
 */
@Composable
fun DetailActionButton(
    icon: ImageVector,
    label: String,
    iconColor: Color,
    isActive: Boolean = false,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(iconColor.copy(alpha = if (isActive) 0.25f else 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = iconColor,
                modifier = Modifier.size(22.dp)
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            fontSize = 11.sp,
            color = if (isActive) iconColor else MiuixTheme.colorScheme.onBackgroundVariant,
            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal
        )
    }
}

// ==================== 3. 搜索页 (SearchPage) 1:1 复刻 ====================
@OptIn(ExperimentalSharedTransitionApi::class, ExperimentalLayoutApi::class)
@Composable
fun SharedTransitionScope.AndroidSearchScreen(
    animatedVisibilityScope: AnimatedVisibilityScope,
    onSelect: (ComicItem) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedSource by remember { mutableStateOf("拷贝漫画") }
    val searchSources = listOf("拷贝漫画", "哔咔漫画", "MangaDex", "全网聚合")
    val searchHistory = remember { mutableStateListOf("芙莉莲", "胆大党", "藤本树", "迷宫饭", "摇滚", "百合") }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. MIUI 风格药丸搜索栏
        item {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("搜索作品、作者、标签...", fontSize = 14.sp) },
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Outlined.Close, contentDescription = "Clear")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(24.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MiuixTheme.colorScheme.primary,
                    unfocusedBorderColor = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.15f)
                ),
                modifier = Modifier.fillMaxWidth()
            )
        }

        // 2. 漫画源选择胶囊条
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(searchSources) { source ->
                    val isSelected = selectedSource == source
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = if (isSelected) MiuixTheme.colorScheme.primaryContainer else MiuixTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.clickable { selectedSource = source }
                    ) {
                        Text(
                            text = source,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                        )
                    }
                }
            }
        }

        // 3. 搜索历史 (带一键清空)
        if (searchHistory.isNotEmpty()) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "搜索历史", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    IconButton(onClick = { searchHistory.clear() }, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Outlined.DeleteOutline, contentDescription = "清空", modifier = Modifier.size(18.dp))
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    searchHistory.forEach { tag ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MiuixTheme.colorScheme.primaryContainer.copy(alpha = 0.25f),
                            modifier = Modifier.clickable { searchQuery = tag }
                        ) {
                            Text(text = tag, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp))
                        }
                    }
                }
            }
        }

        // 4. 搜索结果作品列表
        item {
            Text(text = "全网热搜作品", fontSize = 15.sp, fontWeight = FontWeight.Bold)
        }
        items(sampleComics) { comic ->
            Card(modifier = Modifier.fillMaxWidth().clickable { onSelect(comic) }) {
                Row(modifier = Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    AsyncImage(
                        model = comic.coverUrl,
                        contentDescription = null,
                        modifier = Modifier.size(width = 75.dp, height = 100.dp).clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Crop
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = comic.title, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(text = "${comic.author} · ${comic.latestChapter}", fontSize = 12.sp, color = MiuixTheme.colorScheme.onBackgroundVariant)
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(text = "★ ${comic.rating}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFFB800))
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(68.dp))
        }
    }
}

// ==================== 4. 收藏页 (FavoritesPage) 1:1 复刻 ====================
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun SharedTransitionScope.AndroidFavoritesScreen(
    animatedVisibilityScope: AnimatedVisibilityScope,
    onSelect: (ComicItem) -> Unit
) {
    val context = LocalContext.current
    val favoriteList by FavoriteDao.getInstance(context).favoritesFlow.collectAsState()
    var currentFolder by remember { mutableStateOf("全部") }
    val folders = listOf("全部", "默认", "追更", "完结", "稍后")

    Column(modifier = Modifier.fillMaxSize()) {
        // 顶部收藏夹多分类 Tab
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(folders) { folder ->
                val isSelected = currentFolder == folder
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = if (isSelected) MiuixTheme.colorScheme.primaryContainer else MiuixTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.clickable { currentFolder = folder }
                ) {
                    Text(
                        text = folder,
                        fontSize = 12.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        color = if (isSelected) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                    )
                }
            }
        }

        val filteredList = if (currentFolder == "全部") {
            favoriteList
        } else {
            favoriteList.filter { it.folderName == currentFolder }
        }

        if (filteredList.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                    Text(text = "⭐", fontSize = 48.sp)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(text = "暂无收藏漫画", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "在漫画详情页点击「收藏」即可加入此文件夹",
                        fontSize = 13.sp,
                        color = MiuixTheme.colorScheme.onBackgroundVariant
                    )
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 80.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(filteredList, key = { "fav-${it.comicId}" }) { record ->
                    val match = sampleComics.find { it.id == record.comicId }
                        ?: ComicItem(
                            id = record.comicId,
                            title = record.title,
                            author = record.author,
                            coverUrl = record.coverUrl,
                            tags = listOf("本地收藏"),
                            rating = "9.6",
                            description = "已收藏至「${record.folderName}」",
                            sourceName = record.sourceName,
                            latestChapter = record.latestChapter
                        )
                    Card(modifier = Modifier.fillMaxWidth().clickable { onSelect(match) }) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            AsyncImage(
                                model = record.coverUrl,
                                contentDescription = null,
                                modifier = Modifier.fillMaxWidth().height(180.dp).clip(RoundedCornerShape(10.dp)),
                                contentScale = ContentScale.Crop
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(text = record.title, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1)
                            Text(
                                text = "${record.sourceName} · ${record.folderName}",
                                fontSize = 11.sp,
                                color = MiuixTheme.colorScheme.onBackgroundVariant,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }
}

// ==================== 5. 探索页 (ExplorePage) 1:1 复刻 ====================
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun SharedTransitionScope.AndroidExploreScreen(
    animatedVisibilityScope: AnimatedVisibilityScope,
    onSelect: (ComicItem) -> Unit
) {
    var selectedTab by remember { mutableStateOf("拷贝漫画 · 热门") }
    val exploreTabs = listOf(
        "拷贝漫画 · 热门",
        "拷贝漫画 · 更新",
        "哔咔 · 日榜",
        "哔咔 · 周榜",
        "MangaDex · 热门"
    )

    Column(modifier = Modifier.fillMaxSize()) {
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(exploreTabs) { tabName ->
                val isSelected = selectedTab == tabName
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = if (isSelected) MiuixTheme.colorScheme.primaryContainer else MiuixTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.clickable { selectedTab = tabName }
                ) {
                    Text(
                        text = tabName,
                        fontSize = 12.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        color = if (isSelected) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                    )
                }
            }
        }

        LazyColumn(
            contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 80.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(sampleComics) { comic ->
                Card(modifier = Modifier.fillMaxWidth().clickable { onSelect(comic) }) {
                    Row(modifier = Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        AsyncImage(
                            model = comic.coverUrl,
                            contentDescription = null,
                            modifier = Modifier.size(width = 80.dp, height = 110.dp).clip(RoundedCornerShape(10.dp)),
                            contentScale = ContentScale.Crop
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = comic.title, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(text = "${comic.author} · ${comic.latestChapter}", fontSize = 12.sp, color = MiuixTheme.colorScheme.onBackgroundVariant)
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(text = "★ ${comic.rating}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFFB800))
                        }
                    }
                }
            }
        }
    }
}

// ==================== 6. 分类页 (CategoriesPage) 1:1 复刻 ====================
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AndroidCategoriesScreen() {
    val categories = listOf(
        "热血少年" to listOf("战斗", "冒险", "科幻", "竞技", "超能力"),
        "奇幻探索" to listOf("异世界", "转生", "魔法", "迷宫", "魔王"),
        "恋爱日常" to listOf("校园", "搞笑", "治愈", "百合", "纯爱", "职场"),
        "悬疑暗黑" to listOf("推理", "恐怖", "惊悚", "末日", "心理")
    )
    LazyColumn(
        contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 80.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        items(categories) { (group, tags) ->
            MiuixSectionHeader(title = group, onTap = { })
            Card(modifier = Modifier.fillMaxWidth()) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    tags.forEach { tag ->
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MiuixTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                        ) {
                            Text(
                                text = "$tag ›",
                                fontSize = 13.sp,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

// ==================== 7. 设置页 (SettingsPage) 1:1 复刻 ====================
@Composable
fun AndroidSettingsScreen() {
    val settingCategories = listOf(
        Triple("探索与主页配置", Icons.Filled.Explore, Color(0xFF2196F3)),
        Triple("内容屏蔽与过滤", Icons.Filled.FilterAlt, Color(0xFFE53935)),
        Triple("阅读器体验", Icons.Filled.Book, Color(0xFF4CAF50)),
        Triple("外观与动效", Icons.Filled.ColorLens, Color(0xFF9C27B0)),
        Triple("本地与收藏夹", Icons.Filled.CollectionsBookmark, Color(0xFFFF9800)),
        Triple("应用与通用", Icons.Filled.Apps, Color(0xFF00BCD4)),
        Triple("网络与代理", Icons.Filled.Public, Color(0xFF009688))
    )

    LazyColumn(
        contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 80.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        // 顶部 About 区块
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(MiuixTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = "V", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(text = "Venera Compose", fontSize = 19.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(text = "版本 1.0.0 · 纯原生复刻版", fontSize = 12.sp, color = MiuixTheme.colorScheme.onBackgroundVariant)
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Surface(shape = RoundedCornerShape(12.dp), color = MiuixTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)) {
                            Text(text = "检查更新", fontSize = 11.sp, color = MiuixTheme.colorScheme.primary, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
                        }
                        Surface(shape = RoundedCornerShape(12.dp), color = MiuixTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)) {
                            Text(text = "GitHub 源码", fontSize = 11.sp, color = MiuixTheme.colorScheme.onSurface, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
                        }
                    }
                }
            }
        }

        // 7 大分类设置入口 (配备原版专属彩色方块徽章)
        items(settingCategories) { (title, icon, badgeColor) ->
            Card(modifier = Modifier.fillMaxWidth().clickable { }) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(badgeColor),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(imageVector = icon, contentDescription = title, tint = Color.White, modifier = Modifier.size(20.dp))
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Text(text = title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    Text(text = "›", fontSize = 20.sp, color = MiuixTheme.colorScheme.onBackgroundVariant)
                }
            }
        }
    }
}
