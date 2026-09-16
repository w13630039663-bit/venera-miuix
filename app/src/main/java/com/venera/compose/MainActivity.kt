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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.theme.MiuixTheme
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

enum class NavTab(val title: String, val icon: String) {
    HOME("首页", "🏠"),
    SEARCH("搜索", "🔍"),
    FAVORITES("收藏", "⭐"),
    EXPLORE("探索", "🧭"),
    CATEGORIES("分类", "🏷️"),
    SETTINGS("设置", "⚙️")
}

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
    var currentTab by remember { mutableStateOf(NavTab.HOME) }
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
                        topBar = {
                            TopAppBar(
                                title = when (currentTab) {
                                    NavTab.HOME -> "Venera"
                                    NavTab.SEARCH -> "搜索与发现"
                                    NavTab.FAVORITES -> "我的收藏"
                                    NavTab.EXPLORE -> "全站探索"
                                    NavTab.CATEGORIES -> "分类索引"
                                    NavTab.SETTINGS -> "设置与关于"
                                }
                            )
                        },
                        bottomBar = {
                            AndroidBottomBar(
                                currentTab = currentTab,
                                onTabSelected = {
                                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                    currentTab = it
                                }
                            )
                        }
                    ) { paddingValues ->
                        Box(modifier = Modifier.padding(paddingValues)) {
                            when (currentTab) {
                                NavTab.HOME -> AndroidHomeScreen(
                                    animatedVisibilityScope = this@AnimatedContent,
                                    onSelect = {
                                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                        selectedComic = it
                                    }
                                )
                                NavTab.SEARCH -> AndroidSearchScreen(
                                    animatedVisibilityScope = this@AnimatedContent,
                                    onSelect = {
                                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                        selectedComic = it
                                    }
                                )
                                NavTab.FAVORITES -> AndroidFavoritesScreen(
                                    animatedVisibilityScope = this@AnimatedContent,
                                    onSelect = {
                                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                        selectedComic = it
                                    }
                                )
                                NavTab.EXPLORE -> AndroidExploreScreen(
                                    animatedVisibilityScope = this@AnimatedContent,
                                    onSelect = {
                                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                        selectedComic = it
                                    }
                                )
                                NavTab.CATEGORIES -> AndroidCategoriesScreen()
                                NavTab.SETTINGS -> AndroidSettingsScreen()
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

@Composable
fun AndroidBottomBar(
    currentTab: NavTab,
    onTabSelected: (NavTab) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MiuixTheme.colorScheme.surface,
        modifier = modifier
            .fillMaxWidth()
            .height(64.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            NavTab.entries.forEach { tab ->
                val isSelected = currentTab == tab
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onTabSelected(tab) }
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = tab.icon,
                        fontSize = if (isSelected) 18.sp else 16.sp
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = tab.title,
                        fontSize = 11.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        color = if (isSelected) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onBackgroundVariant
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun SharedTransitionScope.AndroidHomeScreen(
    animatedVisibilityScope: AnimatedVisibilityScope,
    onSelect: (ComicItem) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val historyList by HistoryDao.getInstance(context).historyFlow.collectAsState()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 今日推荐
        item {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "今日推荐", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                    Text(text = "›", fontSize = 20.sp, color = MiuixTheme.colorScheme.onBackgroundVariant)
                }
                Spacer(modifier = Modifier.height(4.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(sampleComics, key = { "home-${it.id}" }) { comic ->
                        Card(
                            modifier = Modifier
                                .width(270.dp)
                                .height(130.dp)
                                .sharedBounds(
                                    sharedContentState = rememberSharedContentState(key = "card-${comic.id}"),
                                    animatedVisibilityScope = animatedVisibilityScope,
                                    clipInOverlayDuringTransition = OverlayClip(RoundedCornerShape(16.dp))
                                )
                                .clickable { onSelect(comic) }
                        ) {
                            Row(modifier = Modifier.fillMaxSize().padding(8.dp)) {
                                AsyncImage(
                                    model = comic.coverUrl,
                                    contentDescription = comic.title,
                                    modifier = Modifier
                                        .size(width = 86.dp, height = 114.dp)
                                        .sharedElement(
                                            sharedContentState = rememberSharedContentState(key = "image-${comic.id}"),
                                            animatedVisibilityScope = animatedVisibilityScope
                                        )
                                        .clip(RoundedCornerShape(10.dp)),
                                    contentScale = ContentScale.Crop
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(
                                    modifier = Modifier.weight(1f).fillMaxHeight(),
                                    verticalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column {
                                        Text(text = comic.title, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 2)
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(text = "${comic.sourceName} · ${comic.latestChapter}", fontSize = 11.sp, color = MiuixTheme.colorScheme.onBackgroundVariant, maxLines = 1)
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
                                                .padding(horizontal = 5.dp, vertical = 2.dp)
                                        ) {
                                            Text(text = "NEW", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                        }
                                        Text(text = comic.updateTime, fontSize = 11.sp, color = MiuixTheme.colorScheme.onBackgroundVariant)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 阅读统计
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(text = "42", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Text(text = "今日页数", fontSize = 12.sp, color = MiuixTheme.colorScheme.onBackgroundVariant)
                    }
                    Box(modifier = Modifier.width(1.dp).height(24.dp).background(MiuixTheme.colorScheme.onBackgroundVariant.copy(alpha = 0.2f)))
                    Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(text = "286", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Text(text = "本周累计", fontSize = 12.sp, color = MiuixTheme.colorScheme.onBackgroundVariant)
                    }
                    Box(modifier = Modifier.width(1.dp).height(24.dp).background(MiuixTheme.colorScheme.onBackgroundVariant.copy(alpha = 0.2f)))
                    Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(text = "12 天", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MiuixTheme.colorScheme.primary)
                        Text(text = "连续打卡", fontSize = 12.sp, color = MiuixTheme.colorScheme.onBackgroundVariant)
                    }
                }
            }
        }

        // 历史记录
        item {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (historyList.isNotEmpty()) "历史记录 (${historyList.size})" else "历史记录",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(text = "›", fontSize = 20.sp, color = MiuixTheme.colorScheme.onBackgroundVariant)
                }
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
                            Card(modifier = Modifier.width(115.dp).clickable { onSelect(match) }) {
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
                            Card(modifier = Modifier.width(115.dp).clickable { onSelect(comic) }) {
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

        // 漫画源状态
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(text = "漫画源网络状态", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Spacer(modifier = Modifier.height(10.dp))
                    listOf("拷贝漫画" to true, "哔咔漫画" to true, "MangaDex" to true, "禁漫天堂" to false).forEach { (name, ok) ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = name, fontSize = 13.sp)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(modifier = Modifier.size(7.dp).clip(RoundedCornerShape(4.dp)).background(if (ok) Color(0xFF4CAF50) else Color(0xFFE53935)))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(text = if (ok) "网络正常" else "请检查代理", fontSize = 11.sp, color = if (ok) Color(0xFF4CAF50) else Color(0xFFE53935))
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class, ExperimentalLayoutApi::class)
@Composable
fun SharedTransitionScope.AndroidSearchScreen(
    animatedVisibilityScope: AnimatedVisibilityScope,
    onSelect: (ComicItem) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(text = "🔍", fontSize = 16.sp)
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(text = "搜索作品、作者、标签...", fontSize = 14.sp, color = MiuixTheme.colorScheme.onBackgroundVariant)
                }
            }
        }
        item {
            SmallTitle(text = "搜索历史")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("芙莉莲", "胆大党", "藤本树", "摇滚", "迷宫饭", "百合").forEach { tag ->
                    Surface(shape = RoundedCornerShape(12.dp), color = MiuixTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)) {
                        Text(text = tag, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp))
                    }
                }
            }
        }
        item {
            SmallTitle(text = "全站作品")
        }
        items(sampleComics) { comic ->
            Card(modifier = Modifier.fillMaxWidth().clickable { onSelect(comic) }) {
                Row(modifier = Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    AsyncImage(model = comic.coverUrl, contentDescription = null, modifier = Modifier.size(width = 75.dp, height = 100.dp).clip(RoundedCornerShape(8.dp)), contentScale = ContentScale.Crop)
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = comic.title, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Text(text = "${comic.author} · ${comic.latestChapter}", fontSize = 12.sp, color = MiuixTheme.colorScheme.onBackgroundVariant)
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(text = "★ ${comic.rating}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFFB800))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun SharedTransitionScope.AndroidFavoritesScreen(
    animatedVisibilityScope: AnimatedVisibilityScope,
    onSelect: (ComicItem) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val favoriteList by FavoriteDao.getInstance(context).favoritesFlow.collectAsState()

    if (favoriteList.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                Text(text = "⭐", fontSize = 48.sp)
                Spacer(modifier = Modifier.height(12.dp))
                Text(text = "暂无收藏漫画", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "在漫画详情页点击「收藏」即可加入书架",
                    fontSize = 13.sp,
                    color = MiuixTheme.colorScheme.onBackgroundVariant
                )
            }
        }
    } else {
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(favoriteList, key = { "fav-${it.comicId}" }) { record ->
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

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun SharedTransitionScope.AndroidExploreScreen(
    animatedVisibilityScope: AnimatedVisibilityScope,
    onSelect: (ComicItem) -> Unit
) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        items(sampleComics) { comic ->
            Card(modifier = Modifier.fillMaxWidth().clickable { onSelect(comic) }) {
                Row(modifier = Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    AsyncImage(model = comic.coverUrl, contentDescription = null, modifier = Modifier.size(width = 80.dp, height = 110.dp).clip(RoundedCornerShape(10.dp)), contentScale = ContentScale.Crop)
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = comic.title, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text(text = "${comic.author} · ${comic.latestChapter}", fontSize = 12.sp, color = MiuixTheme.colorScheme.onBackgroundVariant)
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(text = "★ ${comic.rating}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFFB800))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AndroidCategoriesScreen() {
    val categories = listOf("热血少年" to listOf("战斗", "冒险", "科幻", "竞技"), "奇幻探索" to listOf("异世界", "转生", "魔法", "迷宫"), "恋爱日常" to listOf("校园", "搞笑", "治愈", "百合"))
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.fillMaxSize()) {
        items(categories) { (group, tags) ->
            SmallTitle(text = group)
            Card(modifier = Modifier.fillMaxWidth()) {
                FlowRow(modifier = Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    tags.forEach { tag ->
                        Surface(shape = RoundedCornerShape(10.dp), color = MiuixTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)) {
                            Text(text = "$tag ›", fontSize = 13.sp, modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AndroidSettingsScreen() {
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.fillMaxSize()) {
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(modifier = Modifier.size(54.dp).clip(RoundedCornerShape(14.dp)).background(MiuixTheme.colorScheme.primary), contentAlignment = Alignment.Center) {
                        Text(text = "V", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = "Venera Compose", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text(text = "版本 1.0.0 · 原生极速版", fontSize = 12.sp, color = MiuixTheme.colorScheme.onBackgroundVariant)
                }
            }
        }
        item {
            SmallTitle(text = "分类设置")
        }
        items(listOf("探索与主页配置" to "🧭", "内容屏蔽与过滤" to "🛡️", "阅读器体验" to "📖", "外观与动效" to "🎨", "本地存储与缓存" to "📦", "网络与代理" to "🌐")) { (title, icon) ->
            Card(modifier = Modifier.fillMaxWidth().clickable { }) {
                Row(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(text = icon, fontSize = 18.sp)
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(text = title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    Text(text = "›", fontSize = 20.sp, color = MiuixTheme.colorScheme.onBackgroundVariant)
                }
            }
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class, ExperimentalLayoutApi::class)
@Composable
fun SharedTransitionScope.AndroidComicDetailScreen(
    comic: ComicItem,
    animatedVisibilityScope: AnimatedVisibilityScope,
    onBack: () -> Unit,
    onStartReading: (chapterIndex: Int, pageIndex: Int) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val view = LocalView.current
    val favoriteDao = remember { FavoriteDao.getInstance(context) }
    val historyDao = remember { HistoryDao.getInstance(context) }

    val favorites by favoriteDao.favoritesFlow.collectAsState()
    val isFav = favorites.any { it.comicId == comic.id }

    val historyList by historyDao.historyFlow.collectAsState()
    val historyRecord = historyList.find { it.comicId == comic.id }

    var isReversed by remember { mutableStateOf(false) }

    // 预测性返回支持
    PredictiveBackHandler { progress ->
        try {
            progress.collect { }
            onBack()
        } catch (_: Exception) { }
    }

    Scaffold(
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
                            .clip(RoundedCornerShape(20.dp))
                            .clickable {
                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                favoriteDao.toggleFavorite(
                                    comicId = comic.id,
                                    title = comic.title,
                                    coverUrl = comic.coverUrl,
                                    author = comic.author,
                                    sourceName = comic.sourceName,
                                    latestChapter = comic.latestChapter
                                )
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = if (isFav) "❤️" else "🤍", fontSize = 18.sp)
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(paddingValues),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Row(modifier = Modifier.fillMaxWidth()) {
                    AsyncImage(
                        model = comic.coverUrl,
                        contentDescription = null,
                        modifier = Modifier
                            .size(width = 120.dp, height = 165.dp)
                            .sharedElement(
                                sharedContentState = rememberSharedContentState(key = "image-${comic.id}"),
                                animatedVisibilityScope = animatedVisibilityScope
                            )
                            .clip(RoundedCornerShape(12.dp)),
                        contentScale = ContentScale.Crop
                    )
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f).height(165.dp), verticalArrangement = Arrangement.SpaceBetween) {
                        Column {
                            Text(text = comic.title, fontWeight = FontWeight.Bold, fontSize = 17.sp, maxLines = 2)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(text = "作者: ${comic.author}", fontSize = 13.sp, color = MiuixTheme.colorScheme.onBackgroundVariant)
                            Text(text = "源: ${comic.sourceName}", fontSize = 12.sp, color = MiuixTheme.colorScheme.onBackgroundVariant)
                            if (historyRecord != null) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "上次至: ${historyRecord.lastChapterTitle} P.${historyRecord.lastPageIndex + 1}",
                                    fontSize = 11.sp,
                                    color = MiuixTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = "★ ${comic.rating}", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFFB800))
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (isFav) Color(0xFFFF4081).copy(alpha = 0.15f) else MiuixTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.clickable {
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
                            ) {
                                Text(
                                    text = if (isFav) "❤️ 已收藏" else "🤍 收藏",
                                    fontSize = 12.sp,
                                    color = if (isFav) Color(0xFFFF4081) else MiuixTheme.colorScheme.onSurface,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                }
            }

            item {
                Button(
                    onClick = {
                        if (historyRecord != null) {
                            onStartReading(historyRecord.lastChapterIndex, historyRecord.lastPageIndex)
                        } else {
                            onStartReading(0, 0)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    content = {
                        Text(
                            text = if (historyRecord != null) {
                                "继续阅读 (${historyRecord.lastChapterTitle} 第 ${historyRecord.lastPageIndex + 1} 页)"
                            } else {
                                "开始阅读 (第 1 话)"
                            },
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                )
            }

            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(text = "作品简介", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(text = comic.description, fontSize = 13.sp, lineHeight = 22.sp)
                    }
                }
            }

            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text(text = "章节目录 (${comic.chapters.size} 话)", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            Surface(shape = RoundedCornerShape(8.dp), color = MiuixTheme.colorScheme.primaryContainer.copy(alpha = 0.3f), modifier = Modifier.clickable { isReversed = !isReversed }) {
                                Text(text = if (isReversed) "正序 ↑" else "倒序 ↓", fontSize = 12.sp, color = MiuixTheme.colorScheme.primary, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            val list = if (isReversed) comic.chapters.reversed() else comic.chapters
                            list.take(36).forEachIndexed { idx, chapter ->
                                val actualIdx = if (isReversed) comic.chapters.lastIndex - idx else idx
                                val isCurrentHistoryChapter = historyRecord?.lastChapterIndex == actualIdx
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (isCurrentHistoryChapter) MiuixTheme.colorScheme.primary.copy(alpha = 0.2f) else MiuixTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                    modifier = Modifier.clickable {
                                        val pIndex = if (isCurrentHistoryChapter) historyRecord.lastPageIndex else 0
                                        onStartReading(actualIdx, pIndex)
                                    }
                                ) {
                                    Text(text = chapter, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
