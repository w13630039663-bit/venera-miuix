package com.venera.compose.gallery.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import coil3.ImageLoader
import com.venera.compose.components.coverSharedElement
import com.venera.compose.components.venera.VeneraCard
import com.venera.compose.components.venera.VeneraChip
import com.venera.compose.components.venera.VeneraChipVariant
import com.venera.compose.components.venera.VeneraCover
import com.venera.compose.components.venera.VeneraCoverMask
import com.venera.compose.components.venera.VeneraCoverShimmer
import com.venera.compose.components.venera.VeneraGallerySourceMark
import com.venera.compose.feature.MiuixSectionHeader
import com.venera.compose.gallery.data.GalleryPost
import com.venera.compose.gallery.data.GalleryTagDictionary
import com.venera.compose.gallery.domain.FollowedArtistRef
import com.venera.compose.gallery.domain.GalleryFollowedArtists
import com.venera.compose.gallery.domain.GalleryFollowedArtistsGap
import com.venera.compose.gallery.domain.GalleryFollowedArtistsReadout
import com.venera.compose.gallery.domain.GalleryWallFeed
import com.venera.compose.ui.tokens.VeneraTokens
import top.yukonga.miuix.kmp.basic.Text

/** 墙首的一节：key 定死（它是 grid 的 item key），内容随状态换。 */
internal data class GalleryGridSection(val key: String, val content: @Composable () -> Unit)

/**
 * 首页那几节的 **key** —— 与 [GALLERY_HOME_SECTION_ORDER] 一起构成"节的清单"这唯一一份事实源。
 *
 * 为什么要有这两样（而不是像以前那样在函数体里就地 `listOf(...)`）：
 *
 * 1. **key 会进 `item(key = …)`**，重复的 key 会让 `LazyStaggeredGrid` **直接崩**。
 *    以前那是"看着不会重复"的字符串字面量，现在它是一份可单测的清单（本项目没有 Robolectric，
 *    composable 测不到，所以判据必须落在常量上）。
 * 2. **对照新版效果图的模块次序**是一次具体的改动，而"顺序"这种东西散在函数体里就没人守得住。
 *    收成一份常量之后，重排只改一行，而"加了一节却忘了改清单"是**编译期**错误
 *    （见 [galleryHomeSections] 里那个无 `else` 的 `when`）。
 */
internal object GalleryHomeSectionKey {
    const val ARTISTS = "section-artists"
    const val DAILY = "section-daily"
    const val FOR_YOU = "section-for-you"
}

/**
 * 三节的**次序**，就是屏上自上而下的次序。
 *
 * 2026-09-30 第二轮（用户点名）从五节收到三节：
 * - 「根据你的收藏」那一栏**整节撤下**（用户：「去掉根据你的收藏这个栏」）。
 *   推荐标签本身没消失 —— 它仍是**猜你喜欢**那面墙的取数种子，也是搜索卡空框时那一行入口；
 *   撤掉的只是首页这排展示栏。
 * - 「最近搜索」**搬回搜索卡**（用户：「最近搜索放回点击搜索框后下面框框着」）——
 *   同一份历史只该在全站出现一处，而它的常态位置是"用户已经决定要搜"的那一刻。
 *
 * ⚠️ 节的**数量与次序都不许跟着数据变**：懒列表在第 0 项之后插一个新项会让视口锚点漂到下一项，
 * 内容于是被画进 `contentPadding` 里（首页顶栏重叠那次真机缺陷的真根因，见记忆
 * 「首页顶栏重叠的真根因：锚定漂移」）。所以三节**恒在**，"这一节此刻有没有货"由项内部判。
 */
internal val GALLERY_HOME_SECTION_ORDER: List<String> = listOf(
    GalleryHomeSectionKey.ARTISTS,
    GalleryHomeSectionKey.DAILY,
    GalleryHomeSectionKey.FOR_YOU,
)

/** 横卡一行摆几枚。与画师行同一档，超出的那几枚由节点头念出来（读数须真）。 */
private const val ROW_ITEMS = GalleryFollowedArtists.ROW_SIZE

/** 骨架比实卡少几枚：它只负责"这里马上有东西"，不必铺满一屏宽。 */
private const val ROW_SKELETON_ITEMS = 4

@Composable
private fun GalleryRowCaption(text: String) {
    val tokens = VeneraTokens
    Text(
        text = text,
        fontSize = tokens.type.caption,
        color = tokens.color.textTertiary,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = tokens.spacing.space4),
    )
}

/**
 * 一行的骨架占位。
 *
 * 为什么不能"取到再画"：那一行第一次到货时会**插在用户正在看的位置上面**，整屏往下顶一格 ——
 * 现象读起来像"页面自己跳了"。骨架先把高度占住，到货只是把内容填进去。
 *
 * ⚠️ 每一格必须套**与实卡同一个容器**（[VeneraCard]），不能只画一块 shimmer 矩形：
 * 实卡的高度是「卡内衬 8dp + 封面 + 4dp + 署名行 + 卡内衬 8dp」，
 * 骨架若省掉卡的那两层内衬，两边就差 16+16dp，到货那一帧仍然会跳。
 * 走同一个容器还顺带保证圆角与表面不会各算各的。
 */
@Composable
private fun GalleryRowSkeleton() {
    val tokens = VeneraTokens
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(tokens.spacing.space4),
    ) {
        repeat(ROW_SKELETON_ITEMS) {
            VeneraCard(modifier = Modifier.width(tokens.spacing.historyCardWidth)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(tokens.spacing.historyCoverHeight)
                        .clip(RoundedCornerShape(tokens.shape.medium)),
                ) {
                    VeneraCoverShimmer()
                }
                // 署名行的占位：与 GalleryCardCaption 同高（那里面最大的是 18dp 那枚站标）。
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = tokens.spacing.space2)
                        .height(tokens.spacing.sourceMarkSize)
                        .clip(RoundedCornerShape(tokens.shape.extraSmall))
                        .background(tokens.color.surfaceVariant),
                )
            }
        }
    }
}

/**
 * 横向图片卡一行（每日热门 / 猜你喜欢那一行的预览）。
 *
 * 卡片形状照参考图：**封面在上、来源标识与标题在下**，整卡是一枚圆角面板。
 * 标题来自离线词典的作品/角色档（判据见 [GalleryCardTitle]），算不出来时那一行只有来源标识。
 *
 * 卡宽高仍取 `historyCardWidth × historyCoverHeight`（与首页历史那排同一口径）：
 * 参考图的卡片更大，但那是**整屏一列**的形态，而这一排是横滑的节内预览 ——
 * 尺寸跟着放大只会让一屏看不见第二枚。
 */
@Composable
private fun GalleryPosterRow(
    cards: List<GalleryCard>,
    imageLoader: ImageLoader,
    onOpen: (GalleryPost) -> Unit,
) {
    val tokens = VeneraTokens
    val view = LocalView.current
    // 这一行的标题整批算一次（要开 SQLite，逐卡各查一次就是好几趟）。
    val titles = rememberGalleryCardTitles(cards.map { it.post })
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(tokens.spacing.space4),
    ) {
        cards.take(ROW_ITEMS).forEach { card ->
            // 起点矩形与墙上那张卡同一口径：量**封面那一块**，不是整张卡
            // （整卡像素里带着下面那条标题，落到大图页的画面框里就会多出一圈"包裹"再消失 —— 那就是闪）。
            var coverBounds by remember { mutableStateOf(Rect.Zero) }
            // 这一行也会被回收（横滑出屏那一侧、整节换了一批）——撤掉自己的返回落点，
            // 与墙上那张卡同一口径（`GalleryPostCard`）。
            DisposableEffect(card.post.uid) {
                onDispose { GalleryFlyIn.forgetCardBounds(card.post.uid) }
            }
            VeneraCard(
                modifier = Modifier.width(tokens.spacing.historyCardWidth),
                onClick = {
                    // 与墙上那张卡（`GalleryPostCard`）**同一个动作**：趁卡片还在屏上把这一帧截下来，
                    // 交给大图页当飞行体的起点。这一行此前根本没截 ⇒ `canFly` 恒假 ⇒
                    // 点预览行的图必然走"整页从屏幕下沿抬上来"那条退路（2026-10-01 查出来的）。
                    GalleryFlyIn.capture(view, coverBounds)
                    onOpen(card.post)
                },
            ) {
                VeneraCover(
                    url = card.post.previewUrl,
                    contentDescription = "${card.post.site.displayName} #${card.post.id}",
                    // 宽高由外部槽位定死，封面不能再自持比例，否则两侧留缝（轮播那条同因）。
                    preserveAspectRatio = false,
                    mask = if (card.masked) VeneraCoverMask.Masked else VeneraCoverMask.Visible,
                    imageLoader = imageLoader,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(tokens.spacing.historyCoverHeight)
                        .onGloballyPositioned {
                            val rect = it.boundsInWindow()
                            coverBounds = rect
                            // 返回程的落点，与墙上的卡同一把：打码卡不回写（红线：两边都不飞）。
                            if (!card.masked) GalleryFlyIn.noteCardBounds(card.post.uid, rect)
                        }
                        // 打码命中不飞行：飞行内容会渲染进 SharedTransitionLayout 的 overlay，
                        // 等于绕开页面级裁剪 —— 遮罩不能有机会被揭开（与漫画侧同一条硬口径）。
                        .coverSharedElement(
                            key = galleryCoverKey(card.post.uid),
                            allowFly = !card.masked,
                        ),
                )
                GalleryCardCaption(
                    site = card.post.site,
                    title = titles[card.post.uid],
                    modifier = Modifier.padding(top = tokens.spacing.space2),
                )
            }
        }
    }
}

/**
 * 「正在关注的画师」一行（圆形头像）。
 *
 * 圆座**先摆真头像**（现取，见 [artistAvatarOnSite]），取不到才退"那位名下最近一张被收藏的图"，
 * 两者都没有才是首字母圆座；下面两行是名字与张数。
 * 张数是真数（"有几张你收藏的图带着这个名字"），不是编出来的更新数。
 *
 * ⚠️ **圆形是有代价的**：圆外的像素会被裁掉，而头像与站方缩略图都是任意比例。
 * 用 `ContentScale.Crop` + 正方形容器，保证圆内始终填满、不留白边。
 *
 * 关注了、收藏里没有他名下的图、平台那头也没脸 —— 那位**照样摆**，卡面落回首字母圆座
 * （用户 2026-09-30 拍板）。把他挑掉等于让"关注"这件事在没收藏他的图时凭空失效；
 * 而"0 张收藏"那一行字**不摆**，没有的读数就不占屏。
 */
@Composable
private fun GalleryArtistRow(
    artists: List<FollowedArtistRef>,
    imageLoader: ImageLoader,
    translations: Map<String, String>,
    onPick: (FollowedArtistRef) -> Unit,
    /** 长按 = 进介绍页（批次 Q+R · 批量 4，用户 2026-09-30 拍板 2 那条不动：**单击仍直接搜**）。 */
    onOpenProfile: (FollowedArtistRef) -> Unit,
) {
    val tokens = VeneraTokens
    val context = LocalContext.current
    // 圆座**先试真头像**（用户 2026-10-01 报"关注了却只有英文字母"）：一位一位串行现取，
    // 谁先回来谁先换脸 —— 有收藏图垫着的那几位在这一趟里本来就有东西可摆，不是空着等。
    // 取法与代价见 [artistAvatarOnSite]（内含进程内缓存，切走再回来不重发）。
    var faces by remember(artists) { mutableStateOf(emptyMap<String, String>()) }
    LaunchedEffect(artists) {
        artists.forEach { artist ->
            // 跨站同名并成一条时按徽标次序试，先答上的赢（`sites` 恒为枚举自然序，不会飘）。
            val avatar = artist.sites.firstNotNullOfOrNull { site ->
                artistAvatarOnSite(context, site, artist.name)
            }
            if (avatar != null) faces = faces + (artist.name to avatar)
        }
    }
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(tokens.spacing.space6),
    ) {
        artists.forEach { artist ->
            // 圆座次序：真头像 > 名下最近一张收藏 > 首字母座（判据在 [GalleryFollowedArtists.faceOf]）。
            val face = GalleryFollowedArtists.faceOf(faces[artist.name], artist.previewUrl)
            // 每位画师**自己一张卡**（批次 N · N2）。表面在条目上，不在节上 ——
            // 节那一层的背板本轮撤了，理由见 [GalleryHomeSection]。
            //
            // 点击走 VeneraCard 自己的 onClick 重载，**不要**在外层再挂 combinedClickable：
            // squircle 裁剪层级会吞掉手势（VeneraCard 的注释里记着这条真机结论）。
            // 原先那个 `indication = null`（怕涟漪糊掉圆头像）也一并作废 —— 卡面自带按压反馈，
            // 涟漪压在卡底上碰不到头像的圆边。
            VeneraCard(
                modifier = Modifier.width(tokens.spacing.artistAvatarSlotWidth),
                onClick = { onPick(artist) },
                onLongClick = { onOpenProfile(artist) },
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        modifier = Modifier
                            .size(tokens.spacing.artistAvatarSize)
                            .clip(CircleShape)
                            .then(
                                if (face == null) {
                                    Modifier.background(tokens.color.surfaceVariant)
                                } else {
                                    Modifier
                                },
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (face == null) {
                            Text(
                                text = artist.name.take(1).uppercase(),
                                fontSize = tokens.type.statNumber,
                                color = tokens.color.onSurfaceVariant,
                                maxLines = 1,
                            )
                        } else {
                            VeneraCover(
                                url = face,
                                contentDescription = artist.name,
                                preserveAspectRatio = false,
                                imageLoader = imageLoader,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }
                    Text(
                        text = translations[artist.name] ?: artist.name,
                        // 回到 caption（批次 N · N4）。批次 M · M7 那一句原文是：
                        // 「降一档，这一栏的主体是头像，名字与下面那行「N 张收藏」同级就够了 ——
                        // 原先两行差一档，读起来像名字在喊」—— 降完名字比参考图还小一档，今天回改。
                        // 下面那行「N 张收藏」仍留 badge：它是补充读数，不是署名。
                        fontSize = tokens.type.caption,
                        color = tokens.color.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = tokens.spacing.space2),
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(tokens.spacing.space1),
                    ) {
                        // 来源标识留小一号：这一行是"这个人"，徽标只是补充信息，不该与名字抢。
                        // 跨站同名合并之后这里可能并排两枚（批次 M · M5）—— 那正是"点进去两站一起搜"的
                        // 屏上说法；只摆一枚就等于把另一站藏起来，用户点进去看到两站的图会觉得莫名其妙。
                        //
                        // ⚠️ 尺寸取 `artistSourceMarkSize`（14dp），**不是**卡片署名行共用的
                        // `sourceMarkSize`（18dp）。2026-10-01 用户报「站点图片太大了」：
                        // 18dp 时这一行是全卡最宽的东西（62dp / 内容宽 72dp），一枚饱和色块压在
                        // 一颗 44dp 头像下面，读序变成"先看见站标、再看见人"。降一档之后主次才反过来；
                        // 而署名行那枚仍留 18dp —— 那里它是署名、与标题同级，本该显眼。
                        artist.sites.forEach { site ->
                            VeneraGallerySourceMark(
                                site = site,
                                size = tokens.spacing.artistSourceMarkSize,
                            )
                        }
                        if (artist.count > 0) {
                            Text(
                                text = "${artist.count} 张收藏",
                                fontSize = tokens.type.badge,
                                color = tokens.color.textTertiary,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 节头右边那一半。
 *
 * 2026-09-30 起它**只服务每日热门**：那一节的头上有「查看全部」（进二级页），
 * 「换一批」搬进了二级页（首页只做预览，换一批在那儿才是"看全量的人"的动作）。
 * 猜你喜欢那一节的头只有「换一批」—— 它本身就是下面那条墙，没有"全部"可切。
 */
@Composable
private fun DailySectionActions(onShowAll: () -> Unit) {
    val tokens = VeneraTokens
    Row(
        horizontalArrangement = Arrangement.spacedBy(tokens.spacing.space2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        VeneraChip(text = "查看全部", variant = VeneraChipVariant.Assist, onClick = onShowAll)
    }
}

/**
 * 猜你喜欢节头右边那半：只有「换一批」（重抽标签并重取，走调用方那条清熔断的路径）。
 *
 * ## 重取期间的轻指示为什么摆在这里（2026-09-30 第三轮）
 *
 * 用户这轮的原话是「换一批不要整个页面刷新，只刷新下面推荐的内容」。旧写法是
 * `refresh()` 清空 `posts` + 打回 `IDLE`，于是整屏换成一个居中波浪环、**三节跟着一起消失**。
 * 现在旧卡片留着、三节不动，那"正在换"这件事就得有个说法 —— 摆在**用户刚点的那枚旁边**
 * 是最强的位置：他一定看得见（这枚节头就在屏幕上，否则他点不到），也不需要给整屏压遮罩。
 *
 * 芯片在重取期间**禁用**：连点两下会发两笔请求、换两次种子，屏上只会看到第一批被第二批盖掉。
 */
@Composable
private fun ForYouSectionActions(loading: Boolean, onRemix: () -> Unit) {
    val tokens = VeneraTokens
    Row(
        horizontalArrangement = Arrangement.spacedBy(tokens.spacing.space2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (loading) {
            CircularWavyProgressIndicator(
                modifier = Modifier.size(tokens.spacing.loaderInline),
                color = tokens.color.primary,
                trackColor = tokens.color.surfaceVariant,
            )
        }
        VeneraChip(
            text = "换一批",
            variant = VeneraChipVariant.Assist,
            enabled = !loading,
            onClick = onRemix,
        )
    }
}

/**
 * 画廊首页那**三节**（2026-09-30 第二轮定型）—— 正在关注的画师 / 每日热门 / 猜你喜欢。
 *
 * 结构上只有一件事是新的：**节与节之间不加分割线、不加分区底色**。参考图那种"内容很满"的观感
 * 来自每节都有真数据、一屏能看见三四节，而不是来自装饰 —— 深色底、圆角卡、浅蓝选中态、
 * 低对比描边都是既有语言，尺寸全部取现成 token，一个新数字都不立。
 *
 * ⚠️ 这一条 2026-09-30 当天被批次 M · M7 **推翻过一次**（"每组卡片层次分明"→ 三节各套一块背板），
 * 又在同日被批次 N · N2 **撤回来**：背板加在"节"上把每日热门变成卡里嵌卡、把画师行留成裸图标列，
 * 两节各错一半，而参考图的抬亮本来就只发生在**条目**上。
 * 走了一来一回不是白走 —— 现在"层次该由谁承载"这件事在 [GalleryHomeSection]、[GalleryArtistRow]、
 * [GalleryPosterRow] 三处都写着，而"条目卡为什么看得见地亮"落在 `veneraGlassCardColors()` 那一处。
 *
 * ## 为什么每一节都是墙里一个**恒定存在**的项
 *
 * 懒列表在第 0 项之后插一个新项，视口锚点会漂到下一项，内容就画进 `contentPadding` 里
 * （首页顶栏重叠那次真机缺陷的根因，见记忆「首页顶栏重叠的真根因：锚定漂移」）。
 * 所以节的**数量恒定**（三节永远在），"这一节此刻有没有货"由**项内部**去判 ——
 * 宁可看见一节空着的标题，也不要一节突然插进来把整屏推下去。
 *
 * ## 哪两节带预览行、哪一节不带
 *
 * - **每日热门**带：它的全量在**二级页**（`GalleryDailyRoute`），所以首页需要一行横卡当入口，
 *   节头给「查看全部」；
 * - **猜你喜欢**不带：首页那条**主墙就是它**，一行预览会让同一批图出现两遍
 *   （前 8 张横着一次、紧接着又在网格里）。所以这一节只是一个节头 + 那枚「换一批」。
 * - **收藏里的画师**带：它是一栏独立内容，全量在收藏页。
 *
 * ## 为什么横卡用固定尺寸
 *
 * 卡高若取自 `post.cardRatio`，摆放前就不知道它多高：懒列表要等测完才知道这一行占多少，
 * 于是每换一批都可能重排整屏。行里的卡一律 `historyCardWidth` 宽、封面
 * `historyCoverHeight` 高（与首页历史那排同一口径），下面再挂一行来源 + 标题。
 *
 * ## 为什么用 `Row + horizontalScroll` 而不是 LazyRow
 *
 * LazyRow 自带一把滚动状态（这一屏已经有两三把网格状态要记），而且**没有预组合** ——
 * 横滑时那排卡是一张一张现拉的，首滑会看到封面逐个蹦出来。一行最多八枚，全量摆不贵。
 *
 * ## 为什么这个列表每帧重建
 *
 * 刻意不 `remember`：这些内容里读的是 `fvm.isLoading`、`artists` 这类快照状态，
 * 记错键就会拿着旧值画新状态（"卡上写着 A 串、点进去 B 串"那类返祖正是这么来的）。
 * grid 项有恒定 key，成本就是三个 lambda。
 *
 * ## 节的次序与唯一性由 [GALLERY_HOME_SECTION_ORDER] 一处定
 *
 * 函数体写成"清单 → `when(key)`"的形状而不是一串 `listOf(...)` 字面量，为的是让
 * **加一节忘了登记**变成编译错误，而**重排**只改清单那一行。
 */
@Composable
internal fun galleryHomeSections(
    daily: List<GalleryCard>,
    dailyLoading: Boolean,
    onOpenDaily: (GalleryPost) -> Unit,
    artists: GalleryFollowedArtistsReadout,
    onPickArtist: (FollowedArtistRef) -> Unit,
    /** 「正在关注的画师」长按 = 进介绍页（单击仍是直接搜，批次 M 拍板 2 不动）。 */
    onOpenArtistProfile: (FollowedArtistRef) -> Unit,
    /** 「查看全部」= 进每日热门二级页看全量（首页这一行只是预览）。 */
    onOpenDailyAll: () -> Unit,
    /**
     * 「猜你喜欢」节头的「换一批」。
     *
     * ⚠️ 这一节**不收卡片数据**（`forYou` / `forYouStarted` 那两个参数 2026-09-30 第二轮删了）：
     * 首页那条主墙恒为猜你喜欢，卡片就在这一节下面由 `GalleryForYouPage` 画 ——
     * 把同一批图再传进来摆一行预览，就会出现"前 8 张横着一次、网格里又一次"。
     * 所以这一节只需要那枚动作与标题。
     */
    onRemixForYou: () -> Unit,
    /**
     * 猜你喜欢那面墙**此刻是不是在重取**（含冷启动第一轮）。
     *
     * 只用于节头那枚轻指示（见 [ForYouSectionActions]）。刻意不给默认值：
     * 漏传会编译不过，而给个 `false` 就会静默变成"按了换一批没有任何反应"的假按钮观感。
     */
    forYouLoading: Boolean,
    imageLoader: ImageLoader,
): List<GalleryGridSection> {
    val tokens = VeneraTokens
    val context = LocalContext.current
    val artistNames = artists?.artists?.map { it.name }.orEmpty()
    var translations by remember(artistNames) { mutableStateOf<Map<String, String>>(emptyMap()) }
    LaunchedEffect(artistNames) {
        translations = if (artistNames.isEmpty()) emptyMap() else GalleryTagDictionary
            .getInstance(context)
            .translations(artistNames)
    }

    // 清单驱动：`when` **不留 else** —— 加了 key 却忘了在这里出内容会编译不过，
    // 而"内容有了却忘了登记进清单"会在单测里红（见 GalleryHomeSectionsTest）。
    return GALLERY_HOME_SECTION_ORDER.map { key ->
        GalleryGridSection(key) {
            when (key) {
                GalleryHomeSectionKey.ARTISTS -> GalleryHomeSection {
                    MiuixSectionHeader(
                        title = "正在关注的画师",
                        trailing = artists.takeIf { it.overflow > 0 }?.let { readout ->
                            @Composable {
                                Text(
                                    text = "另有 ${readout.overflow} 位",
                                    fontSize = tokens.type.caption,
                                    color = tokens.color.textTertiary,
                                )
                            }
                        },
                    )
                    Spacer(modifier = Modifier.height(tokens.spacing.space2))
                    when (artists.gap) {
                        GalleryFollowedArtistsGap.NONE -> GalleryArtistRow(
                            artists.artists,
                            imageLoader,
                            translations,
                            onPickArtist,
                            onOpenArtistProfile,
                        )
                        // 一个都没关注 —— 这一节**照样摆**（首页恒三节，撤一节会让懒列表的锚点漂），
                        // 只把内容换成一句引导。它说的是"你还没做"，不是"这里坏了"。
                        GalleryFollowedArtistsGap.NO_FOLLOWS -> GalleryRowCaption("在大图上点关注，这一栏会列出你关注的人")
                    }
                }

                GalleryHomeSectionKey.DAILY -> GalleryHomeSection {
                    MiuixSectionHeader(
                        title = GalleryWallFeed.DAILY.label,
                        // 只留「查看全部」：它进二级页看全量，而「换一批」搬到了那一页
                        // （首页这一行是预览，"换一批"是看全量的人的动作）。
                        trailing = { DailySectionActions(onShowAll = onOpenDailyAll) },
                    )
                    Spacer(modifier = Modifier.height(tokens.spacing.space2))
                    when {
                        daily.isNotEmpty() -> GalleryPosterRow(daily, imageLoader, onOpenDaily)
                        dailyLoading -> GalleryRowSkeleton()
                        else -> GalleryRowCaption("这一轮还没有可摆的热门")
                    }
                }

                GalleryHomeSectionKey.FOR_YOU -> GalleryHomeSection {
                    MiuixSectionHeader(
                        title = GalleryWallFeed.FOR_YOU.label,
                        // 猜你喜欢**没有「查看全部」**：它本身就是下面那条墙。
                        trailing = { ForYouSectionActions(loading = forYouLoading, onRemix = onRemixForYou) },
                    )
                    // ⚠️ 这一节**只有节头**，没有预览行。
                    //
                    // 2026-09-30 第二轮：首页那条主墙**恒为猜你喜欢**（用户点名"主页下面的内容
                    // 改为猜你喜欢"），所以这一节底下紧接着的就是它自己的全部卡片。
                    // 再摆一行预览横卡就成了同一批图连着出现两遍（前 8 张横着、然后又在网格里），
                    // 那会让用户以为"上面那排是另一批"。
                    //
                    // 与「每日热门」那一节的区别正在这里：热门的全量在**二级页**，
                    // 所以首页需要一行预览当入口；猜你喜欢的全量就在脚下，不需要。
                }
            }
        }
    }
}

/**
 * 首页一节的**外壳**：只有宽度与节间距，**没有背板**。
 *
 * ## 为什么背板撤了（批次 N · N2，推翻 M7）
 *
 * 批次 M · M7 曾给每一节套一块 `VeneraCard` 背板，当时的理由原文是：
 * 「三节原先直接铺在页面底色上，只靠 sectionGap 那点留白分组 —— 真机对照参考图之后那个"组"
 * 根本读不出来。一节一块圆角表面，是"这几样东西属于同一组"在屏上唯一的说法。」
 *
 * 那个说法**方向错了**。参考图里根本没有"节"这一层表面 —— 抬亮发生在**每一条**上
 * （每位画师一张方卡、每张海报一张卡），页面底 → 条目卡只有两层。
 * 把背板加在"节"上同时错了两处：
 * - **每日热门**变成卡里嵌卡（节卡 + 条目卡），而两层颜色只差一档里的一小截，
 *   实测条目卡面比页面底只亮 +7，两层糊成一层；
 * - **正在关注的画师**那一节里面摆的是**裸条目**（没有自己的卡），于是它连"卡中卡"都不是，
 *   读起来是一排图标而不是四个人的卡。
 * 两节各错一半，而**两节的分层形状还不一样** —— 这比原来"三节糊在一片底色上"更糟。
 *
 * 本轮把表面下放到条目：撤节背板，画师行每位一张 [VeneraCard]，海报行本来就有。
 * 层次该由谁承载这件事，[GalleryArtistRow] 与 [GalleryPosterRow] 各自的注释里都写着。
 *
 * ## 抬亮本身不在这里做
 *
 * 条目卡为什么"看得见地比页面亮一档"，判据在 `veneraGlassCardColors()` /
 * `containerColorFor(CONTAINER)` 那一处（批次 N · N1：容器色从 `surface` 换成
 * `surfaceContainerHigh`）。这里只负责"表面该摆在哪一层"。**两处别互相顶**：
 * 在这里加背板救不了抬亮，在那里加也救不了分层。
 */
@Composable
private fun GalleryHomeSection(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            // 节间距：M7 时它挂在卡外（先 padding 再画表面，玻璃不会把留白也涂进去），
            // 背板撤了之后它仍然只能是外壳的第一道 padding —— 放进内容里就会跟着条目一起被裁。
            .padding(bottom = VeneraTokens.spacing.sectionGap),
        content = content,
    )
}

// 「最近搜索」的渲染件 2026-09-30 第二轮**搬回 `GallerySearchArea.kt`**（改回原名
// `HistoryHeader` / `HistoryList`）：用户拍板"最近搜索放回点击搜索框后下面框框着"，
// 同一份历史只该在全站出现一处。这里刻意不留副本、也不留空壳 ——
// 两份实现会在"在一处删掉、另一处还在"那里分叉，而那正是本轮要收口的事。

