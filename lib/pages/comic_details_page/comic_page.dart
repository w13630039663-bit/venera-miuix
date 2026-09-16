import 'dart:async';
import 'dart:ui';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_miuix/miuix.dart';
import 'package:photo_view/photo_view.dart';
import 'package:shimmer_animation/shimmer_animation.dart';
import 'package:sliver_tools/sliver_tools.dart';
import 'package:url_launcher/url_launcher_string.dart';
import 'package:venera/components/components.dart';
import 'package:venera/components/rich_comment_content.dart';
import 'package:venera/foundation/app.dart';
import 'package:venera/foundation/appdata.dart';
import 'package:venera/foundation/comic_source/comic_source.dart';
import 'package:venera/foundation/comic_type.dart';
import 'package:venera/foundation/consts.dart';
import 'package:venera/foundation/favorites.dart';
import 'package:venera/foundation/history.dart';
import 'package:venera/foundation/image_provider/cached_image.dart';
import 'package:venera/foundation/image_provider/reader_image.dart';
import 'package:venera/foundation/local.dart';
import 'package:venera/foundation/preview_hero.dart';
import 'package:venera/foundation/res.dart';
import 'package:venera/network/download.dart';
import 'package:venera/network/cache.dart';
import 'package:venera/pages/favorites/favorites_page.dart';
import 'package:venera/pages/reader/reader.dart';
import 'package:venera/pages/search_result_page.dart';
import 'package:venera/utils/file_type.dart';
import 'package:venera/utils/io.dart';
import 'package:venera/utils/tags_translation.dart';
import 'package:venera/utils/translations.dart';
import 'dart:math' as math;

part 'comments_page.dart';

part 'chapters.dart';

part 'thumbnails.dart';

part 'favorite.dart';

part 'comments_preview.dart';

part 'actions.dart';

part 'cover_viewer.dart';

/// 详情页卡片化令牌（仅 miuix 分支使用，classic 分支保持原样）。
/// pageBg = 页面背景，cardBg = 卡片背景；两者在明暗模式下互换，
/// 形成「浅灰底 + 白卡 / 深底 + 浅灰卡」的层次。
Color comicPageBg(BuildContext context) => context.isDarkMode
    ? context.colorScheme.surface
    : context.colorScheme.surfaceContainerLow;

Color comicCardBg(BuildContext context) => context.isDarkMode
    ? context.colorScheme.surfaceContainerLow
    : context.colorScheme.surface;

const double kComicCardRadius = 16.0;

/// 水平边距 12 + 区块间距（顶部）12。
const EdgeInsets kComicCardMargin = EdgeInsets.fromLTRB(12, 12, 12, 0);

/// miuix 详情页统一区块卡片：圆角 16 + cardBg。classic 分支不使用。
class _ComicSectionCard extends StatelessWidget {
  const _ComicSectionCard({required this.child});

  final Widget child;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: kComicCardMargin,
      child: Material(
        color: comicCardBg(context),
        borderRadius: BorderRadius.circular(kComicCardRadius),
        clipBehavior: Clip.antiAlias,
        child: child,
      ),
    );
  }
}

class ComicPage extends StatefulWidget {
  const ComicPage({
    super.key,
    required this.id,
    required this.sourceKey,
    this.cover,
    this.title,
    this.heroID,
  });

  final String id;

  final String sourceKey;

  final String? cover;

  final String? title;

  final int? heroID;

  @override
  State<ComicPage> createState() => _ComicPageState();
}

class _ComicPageState extends LoadingState<ComicPage, ComicDetails>
    with _ComicPageActions {
  @override
  History? history;

  bool showAppbarTitle = false;

  var scrollController = ScrollController();

  bool isDownloaded = false;

  bool showFAB = false;

  @override
  void onReadEnd() {
    history ??= HistoryManager().find(
      widget.id,
      ComicType(widget.sourceKey.hashCode),
    );
    update();
  }

  @override
  Widget buildLoading() {
    return _ComicPageLoadingPlaceHolder(
      cover: widget.cover,
      title: widget.title,
      sourceKey: widget.sourceKey,
      cid: widget.id,
      heroID: widget.heroID,
    );
  }

  @override
  Widget buildError() {
    final isDownloaded = LocalManager().isDownloaded(
      widget.id,
      ComicType.fromKey(widget.sourceKey),
    );
    Widget? action;
    if (isDownloaded) {
      action = FilledButton.tonal(
        child: Text("Read".tl),
        onPressed: () {
          final localComic = LocalManager().find(
            widget.id,
            ComicType.fromKey(widget.sourceKey),
          );
          if (localComic == null) {
            context.showMessage(message: "Local comic not found".tl);
            return;
          }
          localComic.read();
        },
      );
    }
    return NetworkError(message: error!, retry: retry, action: action);
  }

  @override
  void initState() {
    scrollController.addListener(onScroll);
    super.initState();
  }

  @override
  void dispose() {
    scrollController.removeListener(onScroll);
    super.dispose();
  }

  @override
  void update() {
    setState(() {});
  }

  @override
  ComicDetails get comic => data!;

  void onScroll() {
    var offset =
        scrollController.position.pixels -
        scrollController.position.minScrollExtent;
    var showFAB = offset > 0;
    if (showFAB != this.showFAB) {
      setState(() {
        this.showFAB = showFAB;
      });
    }
    if (offset > 100) {
      if (!showAppbarTitle) {
        setState(() {
          showAppbarTitle = true;
        });
      }
    } else {
      if (showAppbarTitle) {
        setState(() {
          showAppbarTitle = false;
        });
      }
    }
  }

  var isFirst = true;

  @override
  Widget buildContent(BuildContext context, ComicDetails data) {
    Widget scroll = SmoothCustomScrollView(
      controller: scrollController,
      slivers: [
        ...buildTitle(),
        buildActions(),
        buildDescription(),
        buildInfo(),
        buildChapters(),
        buildComments(),
        buildThumbnails(),
        buildRecommend(),
        SliverPadding(
          padding: EdgeInsets.only(
            bottom: context.padding.bottom + 80,
          ), // Add additional padding for FAB
        ),
      ],
    );
    // Miuix 画风：树内注入 Miuix 主题供 Miuix 组件取色。页面背景 = pageBg，
    // 各区块卡片化（_ComicSectionCard）。原先的封面模糊沉浸背景
    // （_ImmersiveCoverBackground）是掉帧头号原因（无 RepaintBoundary +
    // 封面全尺寸解码 + 转场逐帧重算），已删。
    if (useMiuixStyle) {
      scroll = withMiuixTheme(context, scroll);
      return Scaffold(
        backgroundColor: comicPageBg(context),
        floatingActionButton: showFAB
            ? FloatingActionButton(
                onPressed: () {
                  scrollController.animateTo(
                    0,
                    duration: const Duration(milliseconds: 200),
                    curve: Curves.ease,
                  );
                },
                child: const Icon(Icons.arrow_upward),
              )
            : null,
        body: PreviewFlightBackdrop(child: scroll),
      );
    }
    return Scaffold(
      floatingActionButton: showFAB
          ? FloatingActionButton(
              onPressed: () {
                scrollController.animateTo(
                  0,
                  duration: const Duration(milliseconds: 200),
                  curve: Curves.ease,
                );
              },
              child: const Icon(Icons.arrow_upward),
            )
          : null,
      body: PreviewFlightBackdrop(child: scroll),
    );
  }

  @override
  Future<Res<ComicDetails>> loadData() async {
    if (widget.sourceKey == 'local') {
      var localComic = LocalManager().find(widget.id, ComicType.local);
      if (localComic == null) {
        return const Res.error('Local comic not found');
      }
      var history = HistoryManager().find(widget.id, ComicType.local);
      if (isFirst) {
        Future.microtask(() {
          App.rootContext.to(() {
            return Reader(
              type: ComicType.local,
              cid: widget.id,
              name: localComic.title,
              chapters: localComic.chapters,
              initialPage: history?.page,
              initialChapter: history?.ep,
              initialChapterGroup: history?.group,
              history:
                  history ??
                  History.fromModel(model: localComic, ep: 0, page: 0),
              author: localComic.subTitle ?? '',
              tags: localComic.tags,
            );
          });
          App.mainNavigatorKey!.currentContext!.pop();
        });
        isFirst = false;
      }
      await Future.delayed(const Duration(milliseconds: 200));
      return const Res.error('Local comic');
    }
    var comicSource = ComicSource.find(widget.sourceKey);
    if (comicSource == null) {
      return const Res.error('Comic source not found');
    }
    isAddToLocalFav = LocalFavoritesManager().isExist(
      widget.id,
      ComicType(widget.sourceKey.hashCode),
    );
    history = HistoryManager().find(
      widget.id,
      ComicType(widget.sourceKey.hashCode),
    );
    return comicSource.loadComicInfo!(widget.id);
  }

  @override
  Future<void> onDataLoaded() async {
    isLiked = comic.isLiked ?? false;
    isFavorite = comic.isFavorite ?? false;
    // For sources with multi-folder favorites, prefer querying folders to get accurate favorite status
    // Some sources may not set isFavorite reliably when multi-folder is enabled
    if (comicSource.favoriteData?.loadFolders != null && comicSource.isLogged) {
      var res = await comicSource.favoriteData!.loadFolders!(comic.id);
      if (!res.error) {
        if (res.subData is List) {
          var list = List<String>.from(res.subData);
          isFavorite = list.isNotEmpty;
          update();
        }
      }
    }
    if (comic.chapters == null) {
      isDownloaded = LocalManager().isDownloaded(comic.id, comic.comicType, 0);
    }
  }

  Iterable<Widget> buildTitle() sync* {
    // Miuix：未滚动时 Appbar 全透明（pageBg 直接透出来）；上滑显示标题后
    // 切 shadow —— 不再用 blur（BackdropFilter σ15 在滚动路径上是持续
    // 性能负担，且卡片化后页面已有不透明背景，模糊无意义）。
    yield SliverAppbar(
      title: AnimatedOpacity(
        opacity: showAppbarTitle ? 1.0 : 0.0,
        duration: const Duration(milliseconds: 200),
        child: Text(comic.title),
      ),
      style: useMiuixStyle
          ? (showAppbarTitle ? AppbarStyle.shadow : AppbarStyle.transparent)
          : AppbarStyle.blur,
      actions: [
        IconButton(
          onPressed: showMoreActions,
          icon: const Icon(Icons.more_horiz),
        ),
      ],
    );

    yield const SliverPadding(padding: EdgeInsets.only(top: 8));

    Widget header = Row(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        const SizedBox(width: 16),
        GestureDetector(
          onTap: () => _viewCover(context),
          onLongPress: () => _saveCover(context),
          child: Hero(
            tag: "cover${widget.heroID}",
            // 列表卡片 ⇄ 详情页封面：飞行中把圆角 / 底色从卡片端插值到这边
            // （Container Transform），见 foundation/preview_hero.dart。
            flightShuttleBuilder: coverHeroFlightShuttle,
            child: CoverHeroChrome(
              width: 144 * 0.72,
              height: 144,
              background: context.colorScheme.primaryContainer,
              borderRadius: BorderRadius.circular(useMiuixStyle ? 12 : 8),
              shadows: [
                BoxShadow(
                  color: Colors.black.withValues(alpha: 0.35),
                  blurRadius: 12,
                  offset: const Offset(0, 4),
                ),
              ],
              child: AnimatedImage(
                image: CachedImageProvider(
                  widget.cover ?? comic.cover,
                  sourceKey: comic.sourceKey,
                  cid: comic.id,
                ),
                width: double.infinity,
                height: double.infinity,
                // 封面显示尺寸 ~104 逻辑宽；此前无限制解码（原图可达 2K+），
                // 是详情页内存/掉帧的大头之一。
                cacheWidth: useMiuixStyle
                    ? coverDecodeWidth(context, 144 * 0.72)
                    : null,
              ),
            ),
          ),
        ),
        const SizedBox(width: 16),
        Expanded(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              SelectableText(comic.title, style: ts.s18),
              if (comic.subTitle != null)
                SelectableText(comic.subTitle!, style: ts.s14)
                    .paddingVertical(4),
              Text(
                (ComicSource.find(comic.sourceKey)?.name) ?? '',
                style: ts.s12.copyWith(
                  color: context.colorScheme.onSurfaceVariant,
                ),
              ),
            ],
          ),
        ),
      ],
    );
    if (useMiuixStyle) {
      // 头部（封面 + 信息）进第一张卡片；文字用主题色
      // （原先的白色硬编码是给已删除的暗化模糊背景配套的）。
      header = _ComicSectionCard(child: header);
    }
    yield SliverLazyToBoxAdapter(child: header);
  }

  Widget buildActions() {
    bool isMobile = context.width < changePoint;
    bool hasHistory = history != null && (history!.ep > 1 || history!.page > 1);
    Widget column = Column(
      children: [
          ListView(
            scrollDirection: Axis.horizontal,
            padding: const EdgeInsets.symmetric(horizontal: 8),
            children: [
              if (hasHistory && !isMobile)
                _ActionButton(
                  icon: const Icon(Icons.menu_book),
                  text: 'Continue'.tl,
                  onPressed: continueRead,
                  iconColor: context.useTextColor(Colors.yellow),
                ),
              if (!isMobile || hasHistory)
                _ActionButton(
                  icon: const Icon(Icons.play_circle_outline),
                  text: 'Start'.tl,
                  onPressed: read,
                  iconColor: context.useTextColor(Colors.orange),
                ),
              if (!isMobile && !isDownloaded)
                _ActionButton(
                  icon: const Icon(Icons.download),
                  text: 'Download'.tl,
                  onPressed: download,
                  iconColor: context.useTextColor(Colors.cyan),
                ),
              if (data!.isLiked != null)
                _ActionButton(
                  icon: const Icon(Icons.favorite_border),
                  activeIcon: const Icon(Icons.favorite),
                  isActive: isLiked,
                  text:
                      ((data!.likesCount != null)
                              ? (data!.likesCount! + (isLiked ? 1 : 0))
                              : (isLiked ? 'Liked'.tl : 'Like'.tl))
                          .toString(),
                  isLoading: isLiking,
                  onPressed: likeOrUnlike,
                  iconColor: context.useTextColor(Colors.red),
                ),
              _ActionButton(
                icon: const Icon(Icons.bookmark_outline_outlined),
                activeIcon: const Icon(Icons.bookmark),
                isActive: isFavorite || isAddToLocalFav,
                text: 'Favorite'.tl,
                onPressed: openFavPanel,
                onLongPressed: quickFavorite,
                iconColor: context.useTextColor(Colors.purple),
              ),
              if (comicSource.commentsLoader != null)
                _ActionButton(
                  icon: const Icon(Icons.comment),
                  text: (comic.commentCount ?? 'Comments'.tl).toString(),
                  onPressed: showComments,
                  iconColor: context.useTextColor(Colors.green),
                ),
              _ActionButton(
                icon: const Icon(Icons.share),
                text: 'Share'.tl,
                onPressed: share,
                iconColor: context.useTextColor(Colors.blue),
              ),
            ],
          ).fixHeight(48),
          if (isMobile)
            Row(
              children: [
                Expanded(
                  child: FilledButton.tonal(
                    onPressed: download,
                    child: Text("Download".tl),
                  ),
                ),
                const SizedBox(width: 16),
                Expanded(
                  child: hasHistory
                      ? FilledButton(
                          onPressed: continueRead,
                          child: Text("Continue".tl),
                        )
                      : FilledButton(onPressed: read, child: Text("Read".tl)),
                ),
              ],
            ).paddingHorizontal(16).paddingVertical(8),
          if (history != null)
            Container(
              margin: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
              padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 6),
              decoration: BoxDecoration(
                color: context.colorScheme.surfaceContainerLow,
                borderRadius: BorderRadius.circular(24),
              ),
              child: Row(
                mainAxisSize: MainAxisSize.min,
                children: [
                  Icon(Icons.history, color: context.useTextColor(Colors.teal)),
                  const SizedBox(width: 8),
                  Builder(
                    builder: (context) {
                      bool haveChapter = comic.chapters != null;
                      var page = history!.page;
                      var ep = history!.ep;
                      var group = history!.group;
                      String text;
                      if (haveChapter) {
                        var epName = "E$ep";
                        String? groupName;
                        try {
                          if (group == null) {
                            epName = comic.chapters!.titles.elementAt(
                              math.min(ep - 1, comic.chapters!.length - 1),
                            );
                          } else {
                            groupName = comic.chapters!.groups.elementAt(
                              group - 1,
                            );
                            epName = comic.chapters!
                                .getGroupByIndex(group - 1)
                                .values
                                .elementAt(ep - 1);
                          }
                        } catch (e) {
                          // ignore
                        }
                        text = groupName == null
                            ? "${"Last Reading".tl}: $epName P$page"
                            : "${"Last Reading".tl}: $groupName $epName P$page";
                      } else {
                        text = "${"Last Reading".tl}: P$page";
                      }
                      return Text(text);
                    },
                  ),
                  const SizedBox(width: 4),
                ],
              ),
            ).toAlign(Alignment.centerLeft),
          if (!useMiuixStyle) const Divider(),
        ],
      ).paddingTop(useMiuixStyle ? 0 : 16);
    // miuix：Actions 区块包进卡片；classic 保持原样。
    return SliverLazyToBoxAdapter(
      child: useMiuixStyle ? _ComicSectionCard(child: column) : column,
    );
  }

  Widget buildDescription() {
    if (comic.description == null || comic.description!.trim().isEmpty) {
      return const SliverPadding(padding: EdgeInsets.zero);
    }
    Widget content = Column(
      children: [
        ListTile(title: Text("Description".tl)),
        Padding(
          padding: const EdgeInsets.symmetric(horizontal: 16),
          child: SelectableText(comic.description!).fixWidth(double.infinity),
        ),
        const SizedBox(height: 16),
        if (!useMiuixStyle) const Divider(),
      ],
    );
    return SliverLazyToBoxAdapter(
      child: useMiuixStyle ? _ComicSectionCard(child: content) : content,
    );
  }

  Widget buildInfo() {
    if (comic.tags.isEmpty &&
        comic.uploader == null &&
        comic.uploadTime == null &&
        comic.uploadTime == null &&
        comic.maxPage == null) {
      return const SliverPadding(padding: EdgeInsets.zero);
    }

    int i = 0;

    Widget buildTag({
      required String text,
      VoidCallback? onTap,
      bool isTitle = false,
      String? rawTag,
      String? namespace,
    }) {
      Color color;
      if (isTitle) {
        const colors = [
          Colors.blue,
          Colors.cyan,
          Colors.red,
          Colors.pink,
          Colors.purple,
          Colors.indigo,
          Colors.teal,
          Colors.green,
          Colors.lime,
          Colors.yellow,
        ];
        color = context.useBackgroundColor(colors[(i++) % (colors.length)]);
      } else {
        color = context.colorScheme.surfaceContainerLow;
      }

      final borderRadius = BorderRadius.circular(12);

      const padding = EdgeInsets.symmetric(horizontal: 16, vertical: 6);

      if (onTap != null) {
        return Material(
          color: color,
          borderRadius: borderRadius,
          child: InkWell(
            borderRadius: borderRadius,
            onTap: onTap,
            onLongPress: () {
              if (rawTag != null) {
                // 长按 = 加入标签屏蔽列表（与 miuix 分支的 _InfoChip 一致）。
                final list = appdata.settings['blockedTags'];
                if (list is! List) return;
                if (!list.contains(rawTag)) list.add(rawTag);
                final full = namespace == null || namespace.isEmpty
                    ? null
                    : "$namespace:$rawTag";
                if (full != null && !list.contains(full)) list.add(full);
                appdata.saveData();
                context.showMessage(message: "Added to block list".tl);
              } else {
                Clipboard.setData(ClipboardData(text: text));
                context.showMessage(message: "Copied".tl);
              }
            },
            onSecondaryTapDown: (details) {
              showMenuX(context, details.globalPosition, [
                MenuEntry(
                  icon: Icons.remove_red_eye,
                  text: "View".tl,
                  onClick: onTap,
                ),
                if (rawTag != null)
                  MenuEntry(
                    icon: Icons.block,
                    text: "Block this tag".tl,
                    onClick: () {
                      final list = appdata.settings['blockedTags'];
                      if (list is! List) return;
                      if (!list.contains(rawTag)) list.add(rawTag);
                      final full = namespace == null || namespace.isEmpty
                          ? null
                          : "$namespace:$rawTag";
                      if (full != null && !list.contains(full)) list.add(full);
                      appdata.saveData();
                      context.showMessage(
                          message: "Added to block list".tl);
                    },
                  ),
                MenuEntry(
                  icon: Icons.copy,
                  text: "Copy".tl,
                  onClick: () {
                    Clipboard.setData(ClipboardData(text: text));
                    context.showMessage(message: "Copied".tl);
                  },
                ),
              ]);
            },
            child: Text(text).padding(padding),
          ),
        );
      } else {
        return Container(
          decoration: BoxDecoration(color: color, borderRadius: borderRadius),
          child: Text(text).padding(padding),
        );
      }
    }

    String formatTime(String time) {
      if (int.tryParse(time) != null) {
        var t = int.tryParse(time);
        if (t! > 1000000000000) {
          return DateTime.fromMillisecondsSinceEpoch(
            t,
          ).toString().substring(0, 19);
        } else {
          return DateTime.fromMillisecondsSinceEpoch(
            t * 1000,
          ).toString().substring(0, 19);
        }
      }
      if (time.contains('T') || time.contains('Z')) {
        var t = DateTime.parse(time);
        return t.toString().substring(0, 19);
      }
      return time;
    }

    Widget buildWrap({required List<Widget> children}) {
      return Wrap(
        runSpacing: 8,
        spacing: 8,
        children: children,
      ).paddingHorizontal(16).paddingBottom(8);
    }

    bool enableTranslation =
        App.locale.languageCode == 'zh' && comicSource.enableTagsTranslate;

    // ---- Miuix 沉浸式信息区 ----
    if (useMiuixStyle) {
      // 元数据键值对（更新时间/页数/上传者等）转轻量双列清单。
      var metaEntries = <MapEntry<String, String>>[
        if (comic.uploader != null) MapEntry('Uploader'.tl, comic.uploader!),
        if (comic.uploadTime != null)
          MapEntry('Upload Time'.tl, formatTime(comic.uploadTime!)),
        if (comic.updateTime != null)
          MapEntry('Update Time'.tl, formatTime(comic.updateTime!)),
        if (comic.maxPage != null)
          MapEntry('Pages'.tl, comic.maxPage.toString()),
      ];

      Widget buildMetaKV(MapEntry<String, String> e) {
        return Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(
              e.key,
              style: ts.s12.copyWith(
                color: context.colorScheme.onSurfaceVariant,
              ),
            ),
            const SizedBox(height: 2),
            SelectableText(
              e.value,
              style: ts.s14,
            ),
          ],
        );
      }

      Widget column = Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          ListTile(title: Text("Information".tl)),
          if (comic.stars != null)
            Row(
              children: [
                StarRating(value: comic.stars!, size: 24, onTap: starRating),
                const SizedBox(width: 8),
                Text(comic.stars!.toStringAsFixed(2)),
              ],
            ).paddingLeft(16).paddingVertical(8),
          if (metaEntries.isNotEmpty)
            Padding(
              padding: const EdgeInsets.fromLTRB(16, 0, 16, 12),
              child: Column(
                children: [
                  for (var i = 0; i < metaEntries.length; i += 2)
                    Padding(
                      padding: const EdgeInsets.only(bottom: 10),
                      child: Row(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Expanded(child: buildMetaKV(metaEntries[i])),
                          const SizedBox(width: 24),
                          Expanded(
                            child: i + 1 < metaEntries.length
                                ? buildMetaKV(metaEntries[i + 1])
                                : const SizedBox(),
                          ),
                        ],
                      ),
                    ),
                ],
              ),
            ),
          // 内容标签扁平化：组名灰色小字 + 统一微透 Chips。
          for (var e in comic.tags.entries)
            if (e.value.isNotEmpty)
              Padding(
                padding: const EdgeInsets.fromLTRB(16, 0, 16, 10),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      e.key.ts(comicSource.key),
                      style: ts.s12.copyWith(
                        fontWeight: FontWeight.w600,
                        color: context.colorScheme.onSurfaceVariant,
                      ),
                    ),
                    const SizedBox(height: 6),
                    Wrap(
                      runSpacing: 6,
                      spacing: 6,
                      children: [
                        for (var tag in e.value)
                          _InfoChip(
                            text: enableTranslation
                                ? TagsTranslation.translationTagWithNamespace(
                                    tag,
                                    e.key.toLowerCase(),
                                  )
                                : tag,
                            rawTag: tag,
                            namespace: e.key,
                            onTap: () => onTapTag(tag, e.key),
                          ),
                      ],
                    ),
                  ],
                ),
              ),
          const SizedBox(height: 4),
        ],
      );
      // miuix：Information 区块包进卡片（原独立 Divider 去掉，区块间距由
      // 卡片自带的顶部 12px 提供）。
      return SliverLazyToBoxAdapter(
        child: _ComicSectionCard(child: column),
      );
    }

    return SliverLazyToBoxAdapter(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          ListTile(title: Text("Information".tl)),
          if (comic.stars != null)
            Row(
              children: [
                StarRating(value: comic.stars!, size: 24, onTap: starRating),
                const SizedBox(width: 8),
                Text(comic.stars!.toStringAsFixed(2)),
              ],
            ).paddingLeft(16).paddingVertical(8),
          for (var e in comic.tags.entries)
            buildWrap(
              children: [
                if (e.value.isNotEmpty)
                  buildTag(text: e.key.ts(comicSource.key), isTitle: true),
                for (var tag in e.value)
                  buildTag(
                    text: enableTranslation
                        ? TagsTranslation.translationTagWithNamespace(
                            tag,
                            e.key.toLowerCase(),
                          )
                        : tag,
                    onTap: () => onTapTag(tag, e.key),
                    rawTag: tag,
                    namespace: e.key,
                  ),
              ],
            ),
          if (comic.uploader != null)
            buildWrap(
              children: [
                buildTag(text: 'Uploader'.tl, isTitle: true),
                buildTag(text: comic.uploader!),
              ],
            ),
          if (comic.uploadTime != null)
            buildWrap(
              children: [
                buildTag(text: 'Upload Time'.tl, isTitle: true),
                buildTag(text: formatTime(comic.uploadTime!)),
              ],
            ),
          if (comic.updateTime != null)
            buildWrap(
              children: [
                buildTag(text: 'Update Time'.tl, isTitle: true),
                buildTag(text: formatTime(comic.updateTime!)),
              ],
            ),
          if (comic.maxPage != null)
            buildWrap(
              children: [
                buildTag(text: 'Pages'.tl, isTitle: true),
                buildTag(text: comic.maxPage.toString()),
              ],
            ),
          const SizedBox(height: 12),
          const Divider(),
        ],
      ),
    );
  }

  Widget buildChapters() {
    if (comic.chapters == null) {
      return const SliverPadding(padding: EdgeInsets.zero);
    }
    return _ComicChapters(
      history: history,
      groupedMode: comic.chapters!.isGrouped,
    );
  }

  Widget buildThumbnails() {
    // 有 thumbnails，或源提供 loadComicThumbnail，或可从首话页面图兜底时，都渲染预览区。
    final canFallbackPreview = comicSource.loadComicPages != null &&
        comic.chapters != null &&
        comic.chapters!.ids.isNotEmpty;
    if (comic.thumbnails == null &&
        comicSource.loadComicThumbnail == null &&
        !canFallbackPreview) {
      return const SliverPadding(padding: EdgeInsets.zero);
    }
    return const _ComicThumbnails();
  }

  Widget buildRecommend() {
    if (comic.recommend == null || comic.recommend!.isEmpty) {
      return const SliverPadding(padding: EdgeInsets.zero);
    }
    // Miuix 画风：横向 Carousel（紧凑卡片展示更多内容）。
    if (useMiuixStyle) {
      return SliverToBoxAdapter(
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Builder(builder: (context) {
              return ListTile(title: Text("Related".tl));
            }),
            SizedBox(
              height: 196,
              child: ListView.builder(
                scrollDirection: Axis.horizontal,
                padding: const EdgeInsets.symmetric(horizontal: 8),
                itemCount: comic.recommend!.length,
                itemBuilder: (context, index) {
                  return _RelatedComicCard(comic: comic.recommend![index]);
                },
              ),
            ),
            const SizedBox(height: 8),
          ],
        ),
      );
    }
    return SliverMainAxisGroup(
      slivers: [
        SliverToBoxAdapter(child: ListTile(title: Text("Related".tl))),
        SliverGridComics(comics: comic.recommend!),
      ],
    );
  }

  Widget buildComments() {
    if (comic.comments == null || comic.comments!.isEmpty) {
      return const SliverPadding(padding: EdgeInsets.zero);
    }
    return _CommentsPart(comments: comic.comments!, showMore: showComments);
  }

  void _viewCover(BuildContext context) {
    final imageProvider = CachedImageProvider(
      widget.cover ?? comic.cover,
      sourceKey: comic.sourceKey,
      cid: comic.id,
    );

    context.to(
      () => _CoverViewer(
        imageProvider: imageProvider,
        title: comic.title,
        heroTag: "cover${widget.heroID}",
      ),
    );
  }

  void _saveCover(BuildContext context) async {
    try {
      final imageProvider = CachedImageProvider(
        widget.cover ?? comic.cover,
        sourceKey: comic.sourceKey,
        cid: comic.id,
      );

      final imageStream = imageProvider.resolve(const ImageConfiguration());
      final completer = Completer<Uint8List>();

      imageStream.addListener(
        ImageStreamListener((ImageInfo info, bool _) async {
          final byteData = await info.image.toByteData(
            format: ImageByteFormat.png,
          );
          if (byteData != null) {
            completer.complete(byteData.buffer.asUint8List());
          }
        }),
      );

      final data = await completer.future;
      final fileType = detectFileType(data);
      await saveFile(filename: "cover${fileType.ext}", data: data);
    } catch (e) {
      if (context.mounted) {
        context.showMessage(message: "Error".tl);
      }
    }
  }
}

class _ActionButton extends StatelessWidget {
  const _ActionButton({
    required this.icon,
    required this.text,
    required this.onPressed,
    this.onLongPressed,
    this.activeIcon,
    this.isActive,
    this.isLoading,
    this.iconColor,
  });

  final Widget icon;

  final Widget? activeIcon;

  final bool? isActive;

  final String text;

  final void Function() onPressed;

  final bool? isLoading;

  final Color? iconColor;

  final void Function()? onLongPressed;

  @override
  Widget build(BuildContext context) {
    // Miuix 画风：胶囊按钮，surfaceContainerHigh 底、无边框。
    if (useMiuixStyle) {
      return Container(
        margin: const EdgeInsets.symmetric(horizontal: 4, vertical: 6),
        decoration: BoxDecoration(
          borderRadius: BorderRadius.circular(20),
          color: context.colorScheme.surfaceContainerHigh,
        ),
        child: InkWell(
          onTap: () {
            if (!(isLoading ?? false)) {
              onPressed();
            }
          },
          onLongPress: onLongPressed,
          borderRadius: BorderRadius.circular(20),
          child: IconTheme.merge(
            data: IconThemeData(size: 20, color: iconColor),
            child: Row(
              mainAxisSize: MainAxisSize.min,
              children: [
                if (isLoading ?? false)
                  const SizedBox(
                    width: 20,
                    height: 20,
                    child: CircularProgressIndicator(strokeWidth: 1.8),
                  )
                else
                  (isActive ?? false) ? (activeIcon ?? icon) : icon,
                const SizedBox(width: 8),
                Text(text),
              ],
            ).paddingHorizontal(16),
          ),
        ),
      );
    }
    return Container(
      margin: const EdgeInsets.symmetric(horizontal: 4, vertical: 6),
      decoration: BoxDecoration(
        borderRadius: BorderRadius.circular(18),
        border: Border.all(
          color: context.colorScheme.outlineVariant,
          width: 0.6,
        ),
      ),
      child: InkWell(
        onTap: () {
          if (!(isLoading ?? false)) {
            onPressed();
          }
        },
        onLongPress: onLongPressed,
        borderRadius: BorderRadius.circular(18),
        child: IconTheme.merge(
          data: IconThemeData(size: 20, color: iconColor),
          child: Row(
            mainAxisSize: MainAxisSize.min,
            children: [
              if (isLoading ?? false)
                const SizedBox(
                  width: 20,
                  height: 20,
                  child: CircularProgressIndicator(strokeWidth: 1.8),
                )
              else
                (isActive ?? false) ? (activeIcon ?? icon) : icon,
              const SizedBox(width: 8),
              Text(text),
            ],
          ).paddingHorizontal(16),
        ),
      ),
    );
  }
}

/// 详情页信息区统一风格 Chip：微透底 + 主题前景色。点击跳多 tag 搜索页，
/// **长按加入标签屏蔽列表**（blockedTags 同时存裸值与 namespace:tag 两种
/// 形式，与 comic.dart 的命中逻辑对齐），副键菜单保留查看/复制。
class _InfoChip extends StatelessWidget {
  const _InfoChip({required this.text, this.rawTag, this.namespace, this.onTap});

  final String text;

  /// 原始标签（未翻译、未带 namespace）——屏蔽列表存这个。
  final String? rawTag;

  final String? namespace;

  final VoidCallback? onTap;

  void _block(BuildContext context) {
    if (rawTag == null) return;
    final list = appdata.settings['blockedTags'];
    if (list is! List) return;
    if (!list.contains(rawTag)) list.add(rawTag);
    final full = "$namespace:$rawTag";
    if (namespace != null && namespace!.isNotEmpty && !list.contains(full)) {
      list.add(full);
    }
    appdata.saveData();
    context.showMessage(message: "Added to block list".tl);
  }

  @override
  Widget build(BuildContext context) {
    Widget content = Container(
      padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 5),
      decoration: BoxDecoration(
        borderRadius: BorderRadius.circular(14),
        color: Theme.of(context).colorScheme.onSurface.withValues(alpha: 0.08),
      ),
      child: Text(
        text,
        style: ts.s14,
      ),
    );
    if (onTap == null) {
      return content;
    }
    return InkWell(
      borderRadius: BorderRadius.circular(14),
      onTap: onTap,
      onLongPress: () => _block(context),
      onSecondaryTapDown: (details) {
        showMenuX(context, details.globalPosition, [
          MenuEntry(
            icon: Icons.remove_red_eye,
            text: "View".tl,
            onClick: onTap!,
          ),
          MenuEntry(
            icon: Icons.block,
            text: "Block this tag".tl,
            onClick: () => _block(context),
          ),
          MenuEntry(
            icon: Icons.copy,
            text: "Copy".tl,
            onClick: () {
              Clipboard.setData(ClipboardData(text: text));
              context.showMessage(message: "Copied".tl);
            },
          ),
        ]);
      },
      child: content,
    );
  }
}

/// 相关推荐横向 Carousel 卡片：封面在上 + 标题两行。
class _RelatedComicCard extends StatelessWidget {
  const _RelatedComicCard({required this.comic});

  final Comic comic;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 4),
      child: MiuixCard(
        cornerRadius: 12,
        insideMargin: const EdgeInsets.all(6),
        onPressed: () {
          App.mainNavigatorKey?.currentContext?.to(
            () => ComicPage(
              id: comic.id,
              sourceKey: comic.sourceKey,
              cover: comic.cover,
              title: comic.title,
            ),
          );
        },
        feedbackType: MiuixPressFeedbackType.sink,
        child: SizedBox(
          width: 104,
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Expanded(
                child: Container(
                  width: double.infinity,
                  decoration: BoxDecoration(
                    borderRadius: BorderRadius.circular(8),
                    color: context.colorScheme.onSurfaceVariant
                        .withValues(alpha: 0.12),
                  ),
                  clipBehavior: Clip.antiAlias,
                  child: AnimatedImage(
                    image: CachedImageProvider(
                      comic.cover,
                      sourceKey: comic.sourceKey,
                      cid: comic.id,
                    ),
                    width: double.infinity,
                    height: double.infinity,
                    fit: BoxFit.cover,
                    filterQuality: FilterQuality.medium,
                    // 卡片显示 ~92 逻辑宽，限解码宽。
                    cacheWidth: coverDecodeWidth(context, 104),
                  ),
                ),
              ),
              const SizedBox(height: 6),
              Text(
                comic.title.replaceAll('\n', ''),
                maxLines: 2,
                overflow: TextOverflow.ellipsis,
                style: const TextStyle(
                  fontSize: 13,
                  fontWeight: FontWeight.w500,
                  height: 1.25,
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class _SelectDownloadChapter extends StatefulWidget {
  const _SelectDownloadChapter(this.eps, this.finishSelect, this.downloadedEps);

  final List<String> eps;
  final void Function(List<int>) finishSelect;
  final List<int> downloadedEps;

  @override
  State<_SelectDownloadChapter> createState() => _SelectDownloadChapterState();
}

class _SelectDownloadChapterState extends State<_SelectDownloadChapter> {
  List<int> selected = [];

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: Appbar(
        title: Text("Download".tl),
        backgroundColor: context.colorScheme.surfaceContainerLow,
      ),
      body: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Expanded(
            child: ListView.builder(
              padding: EdgeInsets.zero,
              itemCount: widget.eps.length,
              itemBuilder: (context, i) {
                return CheckboxListTile(
                  title: Text(widget.eps[i]),
                  value:
                      selected.contains(i) || widget.downloadedEps.contains(i),
                  onChanged: widget.downloadedEps.contains(i)
                      ? null
                      : (v) {
                          setState(() {
                            if (selected.contains(i)) {
                              selected.remove(i);
                            } else {
                              selected.add(i);
                            }
                          });
                        },
                );
              },
            ),
          ),
          Container(
            height: 50,
            decoration: BoxDecoration(
              border: Border(
                top: BorderSide(color: context.colorScheme.outlineVariant),
              ),
            ),
            child: Row(
              children: [
                const SizedBox(width: 16),
                Expanded(
                  child: TextButton(
                    onPressed: () {
                      var res = <int>[];
                      for (int i = 0; i < widget.eps.length; i++) {
                        if (!widget.downloadedEps.contains(i)) {
                          res.add(i);
                        }
                      }
                      widget.finishSelect(res);
                      context.pop();
                    },
                    child: Text("Download All".tl),
                  ),
                ),
                const SizedBox(width: 16),
                Expanded(
                  child: FilledButton(
                    onPressed: selected.isEmpty
                        ? null
                        : () {
                            widget.finishSelect(selected);
                            context.pop();
                          },
                    child: Text("Download Selected".tl),
                  ),
                ),
                const SizedBox(width: 16),
              ],
            ),
          ),
          SizedBox(height: MediaQuery.of(context).padding.bottom),
        ],
      ),
    );
  }
}

class _ComicPageLoadingPlaceHolder extends StatelessWidget {
  const _ComicPageLoadingPlaceHolder({
    this.cover,
    this.title,
    required this.sourceKey,
    required this.cid,
    this.heroID,
  });

  final String? cover;

  final String? title;

  final String sourceKey;

  final String cid;

  final int? heroID;

  @override
  Widget build(BuildContext context) {
    Widget buildContainer(
      double? width,
      double? height, {
      Color? color,
      double? radius,
    }) {
      return Container(
        height: height,
        width: width,
        decoration: BoxDecoration(
          color: color ?? context.colorScheme.surfaceContainerLow,
          borderRadius: BorderRadius.circular(radius ?? 4),
        ),
      );
    }

    return Shimmer(
      color: context.isDarkMode ? Colors.grey.shade700 : Colors.white,
      child: Column(
        children: [
          Appbar(title: Text(""), backgroundColor: context.colorScheme.surface),
          const SizedBox(height: 8),
          Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              const SizedBox(width: 16),
              buildImage(context),
              const SizedBox(width: 16),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    if (title != null)
                      Text(title ?? "", style: ts.s18)
                    else
                      buildContainer(200, 25),
                    const SizedBox(height: 8),
                    buildContainer(80, 20),
                  ],
                ),
              ),
            ],
          ),
          const SizedBox(height: 8),
          if (context.width < changePoint)
            Row(
              children: [
                Expanded(child: buildContainer(null, 36, radius: 18)),
                const SizedBox(width: 16),
                Expanded(child: buildContainer(null, 36, radius: 18)),
              ],
            ).paddingHorizontal(16),
          const Divider(),
          const SizedBox(height: 8),
          Center(
            child: CircularProgressIndicator(
              strokeWidth: 2.4,
            ).fixHeight(24).fixWidth(24),
          ),
        ],
      ),
    );
  }

  Widget buildImage(BuildContext context) {
    Widget child;
    if (cover != null) {
      child = AnimatedImage(
        image: CachedImageProvider(cover!, sourceKey: sourceKey, cid: cid),
        width: double.infinity,
        height: double.infinity,
        fit: BoxFit.cover,
      );
    } else {
      child = const SizedBox();
    }

    return Hero(
      tag: "cover$heroID",
      // 加载期占位封面必须挂同一个 shuttle —— 详情数据回来之前用户点的就是
      // 它，不挂的话"卡片形变"在大多数情况下根本不生效。
      flightShuttleBuilder: coverHeroFlightShuttle,
      child: CoverHeroChrome(
        width: 144 * 0.72,
        height: 144,
        background: context.colorScheme.primaryContainer,
        // 圆角与真实封面保持一致（原来是硬编码 8，miuix 下会与真实头部的
        // 12 对不上，飞行落地后再跳一次）。
        borderRadius: BorderRadius.circular(useMiuixStyle ? 12 : 8),
        // 阴影必须与真实封面端一致（blur 12 / offset(0,4)）。这一端是飞行
        // 插值的终点，若沿用占位自己的小阴影（blur 1），会出现两个问题：
        // ① 卡片端阴影同样是 blur 1（网格卡片甚至没有阴影），整段飞行阴影
        //    从 1 插到 1 —— 全程零变化，"卡片形变"最强的那根视觉线索没了；
        // ② 落地换真实封面（blur 12）时阴影会"啪"地跳一下。
        // 注：底色插值其实看不见（图片加载后把 background 完全盖住），
        // 圆角受两端设计值约束（8→12），所以能感知的形变主要就靠阴影。
        shadows: [
          BoxShadow(
            color: Colors.black.withValues(alpha: 0.35),
            blurRadius: 12,
            offset: const Offset(0, 4),
          ),
        ],
        child: child,
      ),
    );
  }
}
