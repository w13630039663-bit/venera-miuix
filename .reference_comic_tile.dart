part of 'components.dart';

/// 列表/网格封面的解码宽度（物理像素）。
///
/// ## 为什么需要它
///
/// 漫画源的封面原图通常是 800×1200 甚至更大，而网格里一张卡片只显示
/// 100–200dp 宽。不限制解码尺寸时，每张封面都按原分辨率解进 ImageCache
/// （800×1200 的 RGBA 约 3.7MB），一屏 8 张加预取就是几十上百 MB，滚动时
/// 反复触发解码与 GC —— 这是列表掉帧最常见的原因。
///
/// `AnimatedImage` 通过 `ResizeImage` 把参数透给解码器，解码时就出缩略图，
/// 既不占显存也不占内存。传「显示宽度 × devicePixelRatio」即可让解码结果
/// 与屏幕像素基本 1:1，画质无损。
///
/// [logicalWidth] 传该封面的**逻辑显示宽度**；不确定时传一个偏大的值
/// （如 240）也比不传安全。
int coverDecodeWidth(BuildContext context, double logicalWidth) {
  final dpr = MediaQuery.maybeDevicePixelRatioOf(context) ?? 1.0;
  // 上限 1440：够 480dp 宽的大图；下限 64：避免极小卡片解出糊图。
  return (logicalWidth * dpr).round().clamp(64, 1440);
}

ImageProvider? _findImageProvider(Comic comic) {
  ImageProvider image;
  if (comic is LocalComic) {
    image = LocalComicImageProvider(comic);
  } else if (comic is History) {
    image = HistoryImageProvider(comic);
  } else if (comic.sourceKey == 'local') {
    var localComic = LocalManager().find(comic.id, ComicType.local);
    if (localComic == null) {
      return null;
    }
    image = FileImage(localComic.coverFile);
  } else {
    image = CachedImageProvider(
      comic.cover,
      sourceKey: comic.sourceKey,
      cid: comic.id,
      fallbackToLocalCover: comic is FavoriteItem,
    );
  }
  return image;
}

class ComicTile extends StatelessWidget {
  const ComicTile({
    super.key,
    required this.comic,
    this.enableLongPressed = true,
    this.badge,
    this.menuOptions,
    this.onTap,
    this.onLongPressed,
    this.heroID,
    this.miuixGrid = false,
  });

  final Comic comic;

  final bool enableLongPressed;

  final String? badge;

  final List<MenuEntry>? menuOptions;

  final VoidCallback? onTap;

  final VoidCallback? onLongPressed;

  final int? heroID;

  /// Miuix 双列卡片模式：封面在上、标题/副标题在下，外层 MiuixCard。
  /// 由 [SliverGridComics] 在发现页（Miuix 画风）传入 —— 原单列布局里
  /// 封面旁的标题信息在此移到封面下方完整保留。
  final bool miuixGrid;

  void _onTap() {
    if (onTap != null) {
      onTap!();
      return;
    }
    App.mainNavigatorKey?.currentContext?.to(
      () => ComicPage(
        id: comic.id,
        sourceKey: comic.sourceKey,
        cover: comic.cover,
        title: comic.title,
        heroID: heroID,
      ),
      sharedElementPopTransition: true,
    );
  }

  void _onLongPressed(context) {
    if (onLongPressed != null) {
      onLongPressed!();
      return;
    }
    onLongPress(context);
  }

  void onLongPress(BuildContext context) {
    var renderBox = context.findRenderObject() as RenderBox;
    var size = renderBox.size;
    var location = renderBox.localToGlobal(
      Offset((size.width - 242) / 2, size.height / 2),
    );
    showMenu(location, context);
  }

  void onSecondaryTap(TapDownDetails details, BuildContext context) {
    showMenu(details.globalPosition, context);
  }

  void showMenu(Offset location, BuildContext context) {
    showMenuX(
      App.rootContext,
      location,
      [
        MenuEntry(
          icon: Icons.chrome_reader_mode_outlined,
          text: 'Details'.tl,
          onClick: () {
            App.mainNavigatorKey?.currentContext?.to(
              () => ComicPage(
                id: comic.id,
                sourceKey: comic.sourceKey,
                cover: comic.cover,
                title: comic.title,
              ),
            );
          },
        ),
        MenuEntry(
          icon: Icons.copy,
          text: 'Copy Title'.tl,
          onClick: () {
            Clipboard.setData(ClipboardData(text: comic.title));
            App.rootContext.showMessage(message: 'Title copied'.tl);
          },
        ),
        MenuEntry(
          icon: Icons.stars_outlined,
          text: 'Add to favorites'.tl,
          onClick: () {
            addFavorite([comic]);
          },
        ),
        MenuEntry(
          icon: Icons.block,
          text: 'Block'.tl,
          onClick: () => block(context),
        ),
        ..._maskMenuEntries(),
        ...?menuOptions,
      ],
    );
  }

  /// 「H 是不行的」相关的长按菜单项。
  ///
  /// 只在总开关打开时出现 —— 关着的时候这些项没有意义，放进菜单只会让长按菜单
  /// 更啰嗦。每个动作执行后都把判定来源（`preset` / `keyword` / `plugin` /
  /// `user:source` …）toast 出来：这是「误判可撤销」的最后一环 —— 用户得能问出
  /// "这条凭什么被遮"，才改得动它。
  List<MenuEntry> _maskMenuEntries() {
    if (!ContentGuard.enabled) {
      return const [];
    }
    final verdict = ContentGuard.verdict(comic);
    final override = ContentGuard.sourceOverride(comic.sourceKey);
    void notify(String message) =>
        App.rootContext.showMessage(message: message);
    return [
      if (verdict.shouldMask)
        MenuEntry(
          icon: Icons.visibility_outlined,
          text: 'Show this cover'.tl,
          onClick: () {
            ContentGuard.unlock(comic);
            notify("Shown permanently (reason: @r)".tlParams({
              'r': verdict.origin,
            }));
          },
        ),
      if (!verdict.shouldMask)
        MenuEntry(
          icon: Icons.visibility_off_outlined,
          text: 'Mark as adult'.tl,
          onClick: () {
            ContentGuard.force(comic);
            notify("Marked as adult".tl);
          },
        ),
      if (override == null)
        MenuEntry(
          icon: Icons.shield_outlined,
          text: 'Always allow this source'.tl,
          onClick: () {
            ContentGuard.setSourceOverride(comic.sourceKey, ContentLevel.safe);
            notify("Covers from this source will not be masked".tl);
          },
        )
      else
        MenuEntry(
          icon: Icons.shield_moon_outlined,
          text: 'Follow preset for this source'.tl,
          onClick: () {
            ContentGuard.setSourceOverride(comic.sourceKey, null);
            notify("This source follows the preset again".tl);
          },
        ),
      if (verdict.origin.startsWith('user:'))
        MenuEntry(
          icon: Icons.restart_alt,
          text: 'Clear content mark'.tl,
          onClick: () {
            ContentGuard.clearMarks(comic);
            notify("Content mark cleared".tl);
          },
        ),
    ];
  }

  @override
  Widget build(BuildContext context) {
    var type = appdata.settings['comicDisplayMode'];

    Widget child = miuixGrid
        ? _buildMiuixGridMode(context)
        : type == 'detailed'
            ? _buildDetailedMode(context)
            : _buildBriefMode(context);

    var isFavorite = appdata.settings['showFavoriteStatusOnTile']
        ? LocalFavoritesManager()
            .isExist(comic.id, ComicType(comic.sourceKey.hashCode))
        : false;
    var history = appdata.settings['showHistoryStatusOnTile']
        ? HistoryManager().find(comic.id, ComicType(comic.sourceKey.hashCode))
        : null;
    if (history?.page == 0) {
      history!.page = 1;
    }

    if (!isFavorite && history == null) {
      return child;
    }

    return Stack(
      children: [
        Positioned.fill(
          child: child,
        ),
        Positioned(
          left: miuixGrid ? 12 : (type == 'detailed' ? 16 : 6),
          top: miuixGrid ? 14 : 8,
          child: Container(
            height: 24,
            decoration: BoxDecoration(
              borderRadius: BorderRadius.circular(4),
            ),
            clipBehavior: Clip.antiAlias,
            child: Row(
              children: [
                if (isFavorite)
                  Container(
                    height: 24,
                    width: 24,
                    color: Colors.green,
                    child: const Icon(
                      Icons.bookmark_rounded,
                      size: 16,
                      color: Colors.white,
                    ),
                  ),
                if (history != null)
                  Container(
                    height: 24,
                    color: Colors.blue.toOpacity(0.9),
                    constraints: const BoxConstraints(minWidth: 24),
                    padding: const EdgeInsets.symmetric(horizontal: 4),
                    child: CustomPaint(
                      painter:
                          _ReadingHistoryPainter(history.page, history.maxPage),
                    ),
                  )
              ],
            ),
          ),
        )
      ],
    );
  }

  /// 封面。三种卡片布局（detailed / brief / miuix 网格）唯一的封面入口 ——
  /// 遮蔽壳与解码尺寸控制都收在这一处，改一处即全覆盖。
  ///
  /// [logicalWidth] 是该封面在屏幕上的逻辑宽度，用来决定解码分辨率
  /// （见 [coverDecodeWidth]）。三种布局的格子宽度算法不同，由调用方传入。
  Widget buildImage(BuildContext context, {double? logicalWidth}) {
    var image = _findImageProvider(comic);
    if (image == null) {
      return const SizedBox();
    }
    return NsfwCover(
      comic: comic,
      child: AnimatedImage(
        image: image,
        fit: BoxFit.cover,
        width: double.infinity,
        height: double.infinity,
        // 缩略图按显示尺寸解码：800×1200 的原图在 100dp 卡片上白占 3.7MB。
        cacheWidth: logicalWidth == null
            ? null
            : coverDecodeWidth(context, logicalWidth),
        // 已按 1:1 解码，双线性（low）足够，且省掉 mipmap 的显存与三线性采样。
        filterQuality: FilterQuality.low,
      ),
    );
  }

  Widget _buildDetailedMode(BuildContext context) {
    return LayoutBuilder(builder: (context, constrains) {
      final height = constrains.maxHeight - 16;

      Widget image = CoverHeroChrome(
        width: height * 0.68,
        height: double.infinity,
        background: Theme.of(context).colorScheme.secondaryContainer,
        borderRadius: BorderRadius.circular(8),
        shadows: [
          BoxShadow(
            color: context.colorScheme.outlineVariant,
            blurRadius: 1,
            offset: const Offset(0, 1),
          ),
        ],
        child: buildImage(context, logicalWidth: height * 0.68),
      );

      if (heroID != null) {
        image = Hero(
          tag: "cover$heroID",
          child: image,
        );
      }

      Widget row = Row(
        children: [
          image,
          SizedBox.fromSize(
            size: const Size(16, 5),
          ),
          Expanded(
            child: _ComicDescription(
              title: comic.maxPage == null
                  ? comic.title.replaceAll("\n", "")
                  : "[${comic.maxPage}P]${comic.title.replaceAll("\n", "")}",
              subtitle: comic.subtitle ?? '',
              description: comic.description,
              badge: badge ?? comic.language,
              tags: comic.tags,
              maxLines: 2,
              enableTranslate:
                  ComicSource.find(comic.sourceKey)?.enableTagsTranslate ??
                      false,
              rating: comic.stars,
            ),
          ),
        ],
      );

      // Miuix 画风：横向卡片包进 MiuixCard（squircle 圆角 + 卡片底色 +
      // 按压下沉反馈）。外层 8dp 边距与网格 delegate 的加高（+16）对应。
      if (useMiuixStyle) {
        return withMiuixTheme(
          context,
          Padding(
            padding: const EdgeInsets.all(8),
            child: MiuixCard(
              insideMargin: EdgeInsets.zero,
              onPressed: _onTap,
              onLongPress:
                  enableLongPressed ? () => _onLongPressed(context) : null,
              feedbackType: MiuixPressFeedbackType.sink,
              child: ClipRRect(
                borderRadius: BorderRadius.circular(16),
                child: Padding(
                  padding: const EdgeInsets.fromLTRB(12, 8, 16, 8),
                  child: row,
                ),
              ),
            ),
          ),
        );
      }

      return InkWell(
        borderRadius: BorderRadius.circular(12),
        onTap: _onTap,
        onLongPress: enableLongPressed ? () => _onLongPressed(context) : null,
        onSecondaryTapDown: (detail) => onSecondaryTap(detail, context),
        child: Padding(
          padding: const EdgeInsets.fromLTRB(16, 8, 24, 8),
          child: row,
        ),
      );
    });
  }

  /// Miuix 双列卡片（发现页专用）：MiuixCard = 封面 + 信息区。
  ///
  /// 原单列布局里封面**旁边**的标题/副标题，在双列下移到封面**下方**
  /// 完整保留（标题最多 2 行、副标题 1 行）—— 这是"保留卡片旁标题"的
  /// 折中形态：信息不丢，布局适应双列。
  ///
  /// 格子几何与 [SliverGridDelegateWithComics.getMiuixTwoColumnLayout]
  /// 严格对应：外间距 6、封面区高 = (格宽-12)/0.68、信息区 66dp。
  Widget _buildMiuixGridMode(BuildContext context) {
    // 封面宽度 = 格宽 − 12（表里 Padding 6×2，与
    // SliverGridDelegateWithComics.getMiuixTwoColumnLayout 对应）。
    // 用 LayoutBuilder 取实测宽度，交给解码器出对应尺寸的缩略图。
    Widget image = LayoutBuilder(
      builder: (context, constraints) => CoverHeroChrome(
        // miuix 双列卡片：封面顶部两角随 MiuixCard 的 squircle（16）走，
        // 底部两角与下方信息区相连保持直角 —— 与原来由外层 ClipRRect
        // 提供的静态外观一致。
        borderRadius: const BorderRadius.vertical(top: Radius.circular(16)),
        background: context.colorScheme.secondaryContainer,
        child: buildImage(context, logicalWidth: constraints.maxWidth),
      ),
    );

    if (heroID != null) {
      image = Hero(
        tag: "cover$heroID",
        child: image,
      );
    }

    final subtitle = comic.subtitle?.replaceAll('\n', '').trim() ?? '';

    return withMiuixTheme(
      context,
      // Builder 让取色 context 拿到刚注入的 MiuixTheme（外层 context
      // 没有 Miuix 祖先，MiuixTheme.of 会回退浅色）。
      Builder(
        builder: (context) {
          final subtitleColor =
              MiuixTheme.of(context).colors.onSurfaceVariantSummary;
          return Padding(
            padding: const EdgeInsets.all(6),
            child: MiuixCard(
              insideMargin: EdgeInsets.zero,
              onPressed: _onTap,
              onLongPress:
                  enableLongPressed ? () => _onLongPressed(context) : null,
              feedbackType: MiuixPressFeedbackType.sink,
              child: ClipRRect(
                borderRadius: BorderRadius.circular(16),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Expanded(
                      child: SizedBox(
                        width: double.infinity,
                        child: Stack(
                          fit: StackFit.expand,
                          children: [
                            image,
                            // 半透明毛玻璃角标：页数 / AI，悬浮在封面底部右侧。
                            Positioned(
                              right: 7,
                              bottom: 7,
                              child: Row(
                                mainAxisSize: MainAxisSize.min,
                                children: [
                                  if (comic.maxPage != null &&
                                      comic.maxPage! > 0)
                                    _GlassCoverBadge('${comic.maxPage}P'),
                                  if (comic.tags?.any((t) =>
                                          t.toLowerCase() == 'ai' ||
                                          t.toLowerCase() == 'ai-generated') ??
                                      false) ...[
                                    if (comic.maxPage != null &&
                                        comic.maxPage! > 0)
                                      const SizedBox(width: 4),
                                    const _GlassCoverBadge('AI'),
                                  ],
                                ],
                              ),
                            ),
                          ],
                        ),
                      ),
                    ),
                    SizedBox(
                      height: 66,
                      child: Padding(
                        padding: const EdgeInsets.fromLTRB(10, 8, 10, 8),
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Text(
                              comic.title.replaceAll('\n', ''),
                              maxLines: 2,
                              overflow: TextOverflow.ellipsis,
                              style: const TextStyle(
                                fontSize: 14,
                                fontWeight: FontWeight.w500,
                                height: 1.25,
                              ),
                            ),
                            if (subtitle.isNotEmpty)
                              Padding(
                                padding: const EdgeInsets.only(top: 3),
                                child: Text(
                                  subtitle,
                                  maxLines: 1,
                                  overflow: TextOverflow.ellipsis,
                                  style: TextStyle(
                                    fontSize: 12,
                                    color: subtitleColor,
                                  ),
                                ),
                              ),
                          ],
                        ),
                      ),
                    ),
                  ],
                ),
              ),
            ),
          );
        },
      ),
    );
  }

  Widget _buildBriefMode(BuildContext context) {
    return LayoutBuilder(
      builder: (context, constraints) {
        Widget image = CoverHeroChrome(
          background: context.colorScheme.secondaryContainer,
          borderRadius: BorderRadius.circular(8),
          shadows: [
            BoxShadow(
              color: Colors.black.toOpacity(0.2),
              blurRadius: 2,
              offset: const Offset(0, 2),
            ),
          ],
          child: buildImage(context, logicalWidth: constraints.maxWidth),
        );

        if (heroID != null) {
          image = Hero(
            tag: "cover$heroID",
            child: image,
          );
        }

        return InkWell(
          borderRadius: BorderRadius.circular(8),
          onTap: _onTap,
          onLongPress: enableLongPressed ? () => _onLongPressed(context) : null,
          onSecondaryTapDown: (detail) => onSecondaryTap(detail, context),
          child: Column(
            children: [
              Expanded(
                child: Stack(
                  children: [
                    Positioned.fill(
                      child: image,
                    ),
                    Align(
                      alignment: Alignment.bottomRight,
                      child: (() {
                        final subtitle =
                            comic.subtitle?.replaceAll('\n', '').trim();
                        final text = comic.description.isNotEmpty
                            ? comic.description.split('|').join('\n')
                            : (subtitle?.isNotEmpty == true ? subtitle : null);
                        final fortSize = constraints.maxWidth < 80
                            ? 8.0
                            : constraints.maxWidth < 150
                                ? 10.0
                                : 12.0;

                        if (text == null) {
                          return const SizedBox();
                        }

                        var children = <Widget>[];
                        var lines = text.split('\n');
                        lines.removeWhere((e) => e.trim().isEmpty);
                        if (lines.length > 3) {
                          lines = lines.sublist(0, 3);
                        }
                        for (var line in lines) {
                          children.add(Container(
                            margin: const EdgeInsets.fromLTRB(2, 0, 2, 2),
                            padding: constraints.maxWidth < 80
                                ? const EdgeInsets.fromLTRB(3, 1, 3, 1)
                                : constraints.maxWidth < 150
                                    ? const EdgeInsets.fromLTRB(4, 2, 4, 2)
                                    : const EdgeInsets.fromLTRB(5, 2, 5, 2),
                            decoration: BoxDecoration(
                              borderRadius: BorderRadius.circular(8),
                              color: Colors.black.toOpacity(0.5),
                            ),
                            constraints: BoxConstraints(
                              maxWidth: constraints.maxWidth,
                            ),
                            child: Text(
                              line,
                              style: TextStyle(
                                fontWeight: FontWeight.w500,
                                fontSize: fortSize,
                                color: Colors.white,
                              ),
                              textAlign: TextAlign.right,
                              maxLines: 1,
                              overflow: TextOverflow.ellipsis,
                            ),
                          ));
                        }
                        return Column(
                          mainAxisSize: MainAxisSize.min,
                          crossAxisAlignment: CrossAxisAlignment.end,
                          children: children,
                        );
                      })(),
                    ),
                  ],
                ),
              ),
              Padding(
                padding: const EdgeInsets.fromLTRB(4, 4, 4, 0),
                child: Text(
                  comic.title.replaceAll('\n', ''),
                  maxLines: 1,
                  overflow: TextOverflow.clip,
                  style: const TextStyle(
                    fontWeight: FontWeight.w500,
                  ),
                ),
              ),
            ],
          ).paddingHorizontal(6).paddingVertical(8),
        );
      },
    );
  }

  List<String> _splitText(String text) {
    // split text by comma, brackets
    var words = <String>[];
    var buffer = StringBuffer();
    var inBracket = false;
    String? prevBracket;
    for (var i = 0; i < text.length; i++) {
      var c = text[i];
      if (c == '[' || c == '(') {
        if (inBracket) {
          buffer.write(c);
        } else {
          if (buffer.isNotEmpty) {
            words.add(buffer.toString().trim());
            buffer.clear();
          }
          inBracket = true;
          prevBracket = c;
        }
      } else if (c == ']' || c == ')') {
        if (prevBracket == '[' && c == ']' || prevBracket == '(' && c == ')') {
          if (buffer.isNotEmpty) {
            words.add(buffer.toString().trim());
            buffer.clear();
          }
          inBracket = false;
        } else {
          buffer.write(c);
        }
      } else if (c == ',') {
        if (inBracket) {
          buffer.write(c);
        } else {
          words.add(buffer.toString().trim());
          buffer.clear();
        }
      } else {
        buffer.write(c);
      }
    }
    if (buffer.isNotEmpty) {
      words.add(buffer.toString().trim());
    }
    words.removeWhere((element) => element == "");
    words = words.toSet().toList();
    return words;
  }

  void block(BuildContext comicTileContext) {
    showDialog(
      context: App.rootContext,
      builder: (context) {
        var words = <String>[];
        // 拆成两个集合：标签走**精确**的 blockedTags，标题/副标题拆出的词走
        // **模糊**的 blockedWords。原来这里把两者混在一个列表里、一律存
        // blockedWords，结果用户想"屏蔽这个标签"实际上变成了"屏蔽任何字段里
        // 出现这两个字的作品"，误伤面大得多。
        final tagSet = <String>{...?comic.tags};
        final all = <String>[];
        all.addAll(_splitText(comic.title));
        if (comic.subtitle != null && comic.subtitle != "") {
          all.add(comic.subtitle!);
        }
        all.addAll(tagSet);
        return StatefulBuilder(builder: (context, setState) {
          return ContentDialog(
            title: 'Block'.tl,
            content: ConstrainedBox(
              constraints: BoxConstraints(
                maxHeight: math.min(400, context.height - 136),
              ),
              child: SingleChildScrollView(
                child: Wrap(
                  runSpacing: 8,
                  spacing: 8,
                  children: [
                    for (var word in all)
                      OptionChip(
                        text: tagSet.contains(word)
                            ? word.translateTagIfNeed
                            : word,
                        isSelected: words.contains(word),
                        onTap: () {
                          setState(() {
                            if (!words.contains(word)) {
                              words.add(word);
                            } else {
                              words.remove(word);
                            }
                          });
                        },
                      ),
                  ],
                ),
              ).paddingHorizontal(16),
            ),
            actions: [
              Button.filled(
                onPressed: () {
                  context.pop();
                  for (var word in words) {
                    if (tagSet.contains(word)) {
                      final list = appdata.settings['blockedTags'];
                      if (list is List && !list.contains(word)) {
                        list.add(word);
                      }
                    } else {
                      final list = appdata.settings['blockedWords'];
                      if (list is List && !list.contains(word)) {
                        list.add(word);
                      }
                    }
                  }
                  appdata.saveData();
                  context.showMessage(message: 'Blocked'.tl);
                  comicTileContext
                      .findAncestorStateOfType<_SliverGridComicsState>()!
                      .update();
                },
                child: Text('Block'.tl),
              ),
            ],
          );
        });
      },
    );
  }
}

class _ComicDescription extends StatelessWidget {
  const _ComicDescription({
    required this.title,
    required this.subtitle,
    required this.description,
    required this.enableTranslate,
    this.badge,
    this.maxLines = 2,
    this.tags,
    this.rating,
  });

  final String title;
  final String subtitle;
  final String description;
  final String? badge;
  final List<String>? tags;
  final int maxLines;
  final bool enableTranslate;
  final double? rating;

  @override
  Widget build(BuildContext context) {
    if (tags != null) {
      tags!.removeWhere((element) => element.removeAllBlank == "");
      for (var s in tags!) {
        s = s.replaceAll("\n", " ");
      }
    }
    var enableTranslate =
        App.locale.languageCode == 'zh' && this.enableTranslate;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: <Widget>[
        Text(
          title.trim(),
          style: const TextStyle(
            fontWeight: FontWeight.w500,
            fontSize: 14.0,
          ),
          maxLines: maxLines,
          overflow: TextOverflow.ellipsis,
          softWrap: true,
        ),
        if (subtitle != "")
          Text(
            subtitle,
            style: TextStyle(
                fontSize: 10.0,
                color: context.colorScheme.onSurface.toOpacity(0.7)),
            maxLines: 1,
            softWrap: true,
            overflow: TextOverflow.ellipsis,
          ),
        const SizedBox(height: 4),
        if (tags != null && tags!.isNotEmpty)
          Expanded(
            child: LayoutBuilder(builder: (context, constraints) {
              if (constraints.maxHeight < 22) {
                return Container();
              }
              int cnt = (constraints.maxHeight - 22).toInt() ~/ 25;
              return Container(
                clipBehavior: Clip.antiAlias,
                height: 21 + cnt * 24,
                width: double.infinity,
                decoration: const BoxDecoration(),
                child: Wrap(
                  runAlignment: WrapAlignment.start,
                  clipBehavior: Clip.antiAlias,
                  crossAxisAlignment: WrapCrossAlignment.end,
                  spacing: 4,
                  runSpacing: 3,
                  children: [
                    for (var s in tags!)
                      Container(
                        height: 21,
                        padding: const EdgeInsets.symmetric(horizontal: 4),
                        constraints: BoxConstraints(
                          maxWidth: constraints.maxWidth * 0.45,
                        ),
                        decoration: BoxDecoration(
                          color: s == "Unavailable"
                              ? context.colorScheme.errorContainer
                              : context.colorScheme.secondaryContainer,
                          borderRadius: BorderRadius.circular(8),
                        ),
                        child: Center(
                          widthFactor: 1,
                          child: Text(
                            enableTranslate
                                ? TagsTranslation.translateTag(s)
                                : s.split(':').last,
                            style: const TextStyle(fontSize: 12),
                            softWrap: true,
                            overflow: TextOverflow.ellipsis,
                            maxLines: 1,
                          ),
                        ),
                      ),
                  ],
                ),
              ).toAlign(Alignment.topCenter);
            }),
          )
        else
          const Spacer(),
        Row(
          crossAxisAlignment: CrossAxisAlignment.end,
          children: [
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  if (rating != null) StarRating(value: rating!, size: 18),
                  Text(
                    description,
                    style: const TextStyle(
                      fontSize: 12.0,
                    ),
                    maxLines: (tags == null || tags!.isEmpty) ? 3 : 2,
                    overflow: TextOverflow.ellipsis,
                  ),
                ],
              ),
            ),
            if (badge != null)
              Container(
                padding: const EdgeInsets.fromLTRB(6, 4, 6, 4),
                decoration: BoxDecoration(
                  color: Theme.of(context).colorScheme.tertiaryContainer,
                  borderRadius: const BorderRadius.all(Radius.circular(8)),
                ),
                child: Center(
                  child: Text(
                    "${badge![0].toUpperCase()}${badge!.substring(1).toLowerCase()}",
                    style: const TextStyle(fontSize: 12),
                  ),
                ),
              ),
          ],
        )
      ],
    );
  }
}

class _ReadingHistoryPainter extends CustomPainter {
  final int page;
  final int? maxPage;

  const _ReadingHistoryPainter(this.page, this.maxPage);

  @override
  void paint(Canvas canvas, Size size) {
    if (maxPage == null) {
      // 在中央绘制page
      final textPainter = TextPainter(
        text: TextSpan(
          text: "$page",
          style: TextStyle(
            fontSize: size.width * 0.8,
            color: Colors.white,
          ),
        ),
        textDirection: TextDirection.ltr,
      );
      textPainter.layout();
      textPainter.paint(
          canvas,
          Offset((size.width - textPainter.width) / 2,
              (size.height - textPainter.height) / 2));
    } else if (page == maxPage) {
      // 在中央绘制勾
      final paint = Paint()
        ..color = Colors.white
        ..strokeWidth = 2
        ..style = PaintingStyle.stroke;
      canvas.drawLine(Offset(size.width * 0.2, size.height * 0.5),
          Offset(size.width * 0.45, size.height * 0.75), paint);
      canvas.drawLine(Offset(size.width * 0.45, size.height * 0.75),
          Offset(size.width * 0.85, size.height * 0.3), paint);
    } else {
      // 在左上角绘制page, 在右下角绘制maxPage
      final textPainter = TextPainter(
        text: TextSpan(
          text: "$page",
          style: TextStyle(
            fontSize: size.width * 0.8,
            color: Colors.white,
          ),
        ),
        textDirection: TextDirection.ltr,
      );
      textPainter.layout();
      textPainter.paint(canvas, const Offset(0, 0));
      final textPainter2 = TextPainter(
        text: TextSpan(
          text: "/$maxPage",
          style: TextStyle(
            fontSize: size.width * 0.5,
            color: Colors.white,
          ),
        ),
        textDirection: TextDirection.ltr,
      );
      textPainter2.layout();
      textPainter2.paint(
          canvas,
          Offset(size.width - textPainter2.width,
              size.height - textPainter2.height));
    }
  }

  @override
  bool shouldRepaint(covariant CustomPainter oldDelegate) {
    return oldDelegate is! _ReadingHistoryPainter ||
        oldDelegate.page != page ||
        oldDelegate.maxPage != maxPage;
  }
}

class SliverGridComics extends StatefulWidget {
  const SliverGridComics(
      {super.key,
      required this.comics,
      this.onLastItemBuild,
      this.badgeBuilder,
      this.menuBuilder,
      this.onTap,
      this.onLongPressed,
      this.selections,
      this.twoColumnMiuix = false});

  final List<Comic> comics;

  final Map<Comic, bool>? selections;

  final void Function()? onLastItemBuild;

  final String? Function(Comic)? badgeBuilder;

  final List<MenuEntry> Function(Comic)? menuBuilder;

  final void Function(Comic, int heroID)? onTap;

  final void Function(Comic, int heroID)? onLongPressed;

  /// Miuix 双列卡片模式（发现页 Miuix 画风专用），见
  /// [SliverGridDelegateWithComics.getMiuixTwoColumnLayout]。
  final bool twoColumnMiuix;

  @override
  State<SliverGridComics> createState() => _SliverGridComicsState();
}

class _SliverGridComicsState extends State<SliverGridComics> {
  List<Comic> comics = [];
  List<int> heroIDs = [];

  /// 当前场景是否参与遮蔽（[NsfwMaskScope]）。缓存成字段是因为过滤发生在
  /// `initState` / `didUpdateWidget` 里，那里读不到 InheritedWidget。
  bool _nsfwActive = true;

  static int _nextHeroID = 0;

  void generateHeroID() {
    heroIDs.clear();
    for (var i = 0; i < comics.length; i++) {
      heroIDs.add(_nextHeroID++);
    }
  }

  void _refilter() {
    comics.clear();
    for (var comic in widget.comics) {
      if (isBlocked(comic, honorNsfw: _nsfwActive) == null) {
        comics.add(comic);
      }
    }
    generateHeroID();
  }

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    final active = NsfwMaskScope.of(context);
    if (active != _nsfwActive) {
      _nsfwActive = active;
      // 场景切换（例如同一个网格被复用到「进源之后」的页面）：立刻重新过滤。
      // 这里不调 setState —— 依赖变化本身已经安排了这一帧的重建。
      _refilter();
    }
  }

  @override
  void didUpdateWidget(covariant SliverGridComics oldWidget) {
    if (!comics.isEqualTo(widget.comics)) {
      _refilter();
    }
    super.didUpdateWidget(oldWidget);
  }

  @override
  void initState() {
    for (var comic in widget.comics) {
      if (isBlocked(comic) == null) {
        comics.add(comic);
      }
    }
    generateHeroID();
    HistoryManager().addListener(update);
    // 布局设置（comicDisplayMode/comicTileScale）变更时全局重排——
    // 列表页的「两列/单列」切换按钮写的就是这个设置。
    appdata.settings.addListener(update);
    super.initState();
  }

  @override
  void dispose() {
    HistoryManager().removeListener(update);
    appdata.settings.removeListener(update);
    super.dispose();
  }

  void update() {
    setState(_refilter);
  }

  @override
  Widget build(BuildContext context) {
    return _SliverGridComics(
      comics: comics,
      heroIDs: heroIDs,
      selection: widget.selections,
      onLastItemBuild: widget.onLastItemBuild,
      badgeBuilder: widget.badgeBuilder,
      menuBuilder: widget.menuBuilder,
      onTap: widget.onTap,
      onLongPressed: widget.onLongPressed,
      twoColumnMiuix: widget.twoColumnMiuix,
    );
  }
}

class _SliverGridComics extends StatelessWidget {
  const _SliverGridComics({
    required this.comics,
    required this.heroIDs,
    this.onLastItemBuild,
    this.badgeBuilder,
    this.menuBuilder,
    this.onTap,
    this.onLongPressed,
    this.selection,
    this.twoColumnMiuix = false,
  });

  final List<Comic> comics;

  final List<int> heroIDs;

  final Map<Comic, bool>? selection;

  final void Function()? onLastItemBuild;

  final String? Function(Comic)? badgeBuilder;

  final List<MenuEntry> Function(Comic)? menuBuilder;

  final void Function(Comic, int heroID)? onTap;

  final void Function(Comic, int heroID)? onLongPressed;

  final bool twoColumnMiuix;

  @override
  Widget build(BuildContext context) {
    return SliverGrid(
      delegate: SliverChildBuilderDelegate((context, index) {
        if (index == comics.length - 1) {
          onLastItemBuild?.call();
        }
        var badge = badgeBuilder?.call(comics[index]);
        var isSelected = selection == null
            ? false
            : selection![comics[index]] ?? false;
        var comic = ComicTile(
          comic: comics[index],
          badge: badge,
          menuOptions: menuBuilder?.call(comics[index]),
          onTap: onTap != null
              ? () => onTap!(comics[index], heroIDs[index])
              : null,
          onLongPressed: onLongPressed != null
              ? () => onLongPressed!(comics[index], heroIDs[index])
              : null,
          heroID: heroIDs[index],
          miuixGrid: twoColumnMiuix,
        );
        if (selection == null) {
          return comic;
        }
        return AnimatedContainer(
          key: ValueKey(comics[index].id),
          duration: const Duration(milliseconds: 150),
          decoration: BoxDecoration(
            color: isSelected
                ? Theme.of(
                    context,
                  ).colorScheme.secondaryContainer.toOpacity(0.72)
                : null,
            borderRadius: BorderRadius.circular(12),
          ),
          margin: const EdgeInsets.all(4),
          child: comic,
        );
      }, childCount: comics.length),
      gridDelegate: SliverGridDelegateWithComics(
        miuixTwoColumn: twoColumnMiuix,
      ),
    );
  }
}

/// 扁平屏蔽表里的精确匹配。表本身是 `List<String>`，但从 settings 取出来是
/// `dynamic`，所以这里统一做类型兜底（脏数据不该让列表页崩掉）。
bool _inBlockList(String settingKey, String value) {
  final list = appdata.settings[settingKey];
  return list is List && list.contains(value);
}

/// return the first blocked keyword, or null if not blocked
String? isBlocked(Comic item, {bool honorNsfw = true}) {
  for (var word in appdata.settings['blockedWords']) {
    if (item.title.contains(word)) {
      return word;
    }
    if (item.subtitle?.contains(word) ?? false) {
      return word;
    }
    if (item.description.contains(word)) {
      return word;
    }
    for (var tag in item.tags ?? <String>[]) {
      if (tag == word) {
        return word;
      }
      if (tag.contains(':')) {
        tag = tag.split(':')[1];
        if (tag == word) {
          return word;
        }
      }
    }
  }

  // ── 「屏蔽与过滤」的三张精确表 ────────────────────────────────────────
  // 与上面的 `blockedWords` 语义不同：那里是**模糊 contains**（拦"某个上传者的
  // 名字出现在任何字段"），这里是**精确相等**（拦"这个标签"，不会因为标题里
  // 恰好含这两个字而误伤）。两者并存。

  if (appdata.settings['enableTagBlock'] != false) {
    for (var tag in item.tags ?? <String>[]) {
      // 支持带命名空间的写法：e-hentai 的 `female:big breasts`、
      // `other:ai generated`，命中前缀或裸值都算。
      final plain = tag.contains(':') ? tag.split(':').sublist(1).join(':') : tag;
      if (_inBlockList('blockedTags', tag) ||
          _inBlockList('blockedTags', plain)) {
        return tag;
      }
    }
  }

  if (appdata.settings['enableArtistBlock'] != false) {
    final artists = appdata.settings['blockedArtists'];
    if (artists is List && artists.isNotEmpty) {
      // ⚠️ 列表层的 `Comic` **没有 author 字段**（只有 title / cover / id /
      // subtitle / tags / description / sourceKey / maxPage / language）。
      // 所以这里只能从 subtitle 和带命名空间的作者标签里找 —— 这就是方案里
      // 说的「画师屏蔽在列表层作用面有限」。这里刻意**不做模糊匹配**：
      // 列表 tags 里混着题材名和日期，模糊匹配会大面积误伤。
      final subtitle = item.subtitle;
      if (subtitle != null && artists.contains(subtitle)) {
        return subtitle;
      }
      for (var tag in item.tags ?? <String>[]) {
        if (!tag.contains(':')) {
          continue;
        }
        final parts = tag.split(':');
        final ns = parts.first.toLowerCase();
        if (ns == 'artist' || ns == 'artists' || ns == 'author' || ns == 'group' || ns == 'circle') {
          final name = parts.sublist(1).join(':');
          if (artists.contains(name)) {
            return name;
          }
        }
      }
    }
  }

  if (appdata.settings['enableComicBlock'] != false) {
    final key = ContentGuard.keyOf(item);
    if (_inBlockList('blockedComics', key)) {
      return key;
    }
  }

  // 「H 是不行的」强度设为 hide 时，命中遮蔽的条目整条剔除（而不是糊封面）。
  // 这是用户显式选择的强度，与"程序替用户猜"导致的误删是两码事。
  //
  // `honorNsfw = false` 对应「进源之后不遮」的场景（[NsfwMaskScope]）：既然那一屏
  // 连封面都不糊，条目更不该被剔除 —— 否则用户点进成人源会发现列表一片空白，
  // 比"糊着"更糟。
  if (honorNsfw &&
      ContentGuard.strength == NsfwMaskStrength.hide &&
      ContentGuard.maskReason(item) != null) {
    return 'nsfw';
  }

  return null;
}

/// 列表层统一过滤：把 [isBlocked] 命中的条目**整条剔除**
/// （屏蔽词 / 标签 / 画师 / 收录作品，以及「H 是不行的」强度设为 hide 时
/// 命中的条目）。
///
/// [SliverGridComics] 内部已经做过这件事，所以走 [ComicList] / [ComicTile] 的
/// 页面天然生效；但**自己拼卡片**的地方（分类页的排行榜预览、主页的
/// 今日推荐 / 历史这类横向小卡片流）拿的是原始列表，必须显式过一遍，
/// 否则屏蔽强度选 hide 时那些卡片照旧出现。
List<T> filterBlocked<T extends Comic>(Iterable<T> list) =>
    list.where((item) => isBlocked(item) == null).toList();

class ComicList extends StatefulWidget {
  const ComicList({
    super.key,
    this.loadPage,
    this.loadNext,
    this.leadingSliver,
    this.trailingSliver,
    this.errorLeading,
    this.menuBuilder,
    this.controller,
    this.refreshHandlerCallback,
    this.enablePageStorage = false,
  });

  final Future<Res<List<Comic>>> Function(int page)? loadPage;

  final Future<Res<List<Comic>>> Function(String? next)? loadNext;

  final Widget? leadingSliver;

  final Widget? trailingSliver;

  final Widget? errorLeading;

  final List<MenuEntry> Function(Comic)? menuBuilder;

  final ScrollController? controller;

  final void Function(VoidCallback c)? refreshHandlerCallback;

  final bool enablePageStorage;

  @override
  State<ComicList> createState() => ComicListState();
}

class ComicListState extends State<ComicList> {
  int? _maxPage;

  final Map<int, List<Comic>> _data = {};

  int _page = 1;

  String? _error;

  final Map<int, bool> _loading = {};

  String? _nextUrl;

  late bool enablePageStorage = widget.enablePageStorage;

  Map<String, dynamic> get state => {
        'maxPage': _maxPage,
        'data': _data,
        'page': _page,
        'error': _error,
        'loading': _loading,
        'nextUrl': _nextUrl,
      };

  void restoreState(Map<String, dynamic>? state) {
    if (state == null || !enablePageStorage) {
      return;
    }
    _maxPage = state['maxPage'];
    _data.clear();
    _data.addAll(state['data']);
    _page = state['page'];
    _error = state['error'];
    _loading.clear();
    _loading.addAll(state['loading']);
    _nextUrl = state['nextUrl'];
  }

  void storeState() {
    if (enablePageStorage) {
      PageStorage.of(context).writeState(context, state);
    }
  }

  void refresh() {
    _data.clear();
    _page = 1;
    _maxPage = null;
    _error = null;
    _nextUrl = null;
    _loading.clear();
    storeState();
    setState(() {});
  }

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    // 仅在开启页面状态持久化时才读 PageStorage。
    // PageStorage 的同一个 identifier（祖先链上的 PageStorageKey 组合）下，
    // 存的不止本组件写入的 state —— Scrollable 会把滚动偏移（double）写进
    // 「同一个 PageStorageKey 子树」的同一 identifier 里。当 ComicList 被嵌进
    // 带 PageStorageKey 的页面（如搜索标签页）后，readState 可能返回那个
    // double，直接传给参数类型为 Map<String, dynamic>? 的 restoreState 会抛：
    //   type 'double' is not a subtype of type 'Map<String, dynamic>?'
    // 这里既加上「只读自己写的 Map」的类型校验，也让未开启持久化时完全不读。
    if (enablePageStorage) {
      final saved = PageStorage.of(context).readState(context);
      if (saved is Map<String, dynamic>) {
        restoreState(saved);
      }
    }
    widget.refreshHandlerCallback?.call(refresh);
  }

  void remove(Comic c) {
    if (_data[_page] == null || !_data[_page]!.remove(c)) {
      for (var page in _data.values) {
        if (page.remove(c)) {
          break;
        }
      }
    }
    setState(() {});
  }



  Future<void> _loadPage(int page) async {
    if (widget.loadPage == null && widget.loadNext == null) {
      _error = "loadPage and loadNext can't be null at the same time";
      Future.microtask(() {
        setState(() {});
      });
    }
    if (_data[page] != null || _loading[page] == true) {
      return;
    }
    _loading[page] = true;
    try {
      if (widget.loadPage != null) {
        var res = await widget.loadPage!(page);
        if (!mounted) return;
        if (res.success) {
          if (res.data.isEmpty) {
            setState(() {
              _data[page] = const [];
              _maxPage ??= page;
            });
          } else {
            setState(() {
              _data[page] = res.data;
              if (res.subData != null && res.subData is int) {
                _maxPage = res.subData;
              }
            });
          }
        } else {
          setState(() {
            _error = res.errorMessage ?? "Unknown error".tl;
          });
        }
      } else {
        try {
          while (_data[page] == null) {
            await _fetchNext();
          }
          if (mounted) {
            setState(() {});
          }
        } catch (e) {
          if (mounted) {
            setState(() {
              _error = e.toString();
            });
          }
        }
      }
    } finally {
      _loading[page] = false;
      storeState();
    }
  }

  Future<void> _fetchNext() async {
    var res = await widget.loadNext!(_nextUrl);
    _data[_data.length + 1] = res.data;
    if (res.subData == null) {
      _maxPage = _data.length;
    } else {
      _nextUrl = res.subData;
    }
  }

  @override
  Widget build(BuildContext context) {
    // 无限滚动模式：滚动到列表末尾时 onLastItemBuild 自动异步加载下一页，
    // 不再提供「上一页/下一页」手动翻页 UI。
    return buildContinuousMode();
  }

  Widget buildContinuousMode() {
    if (_error != null && _data.isEmpty) {
      return Column(
        children: [
          if (widget.errorLeading != null) widget.errorLeading!,
          Expanded(
            child: NetworkError(
              withAppbar: false,
              message: _error!,
              retry: () {
                setState(() {
                  _error = null;
                });
              },
            ),
          ),
        ],
      );
    }
    if (_data[1] == null) {
      _loadPage(1);
      // 首屏加载：骨架屏占位（几何与真实卡片网格一致 + Shimmer 动画）。
      return Column(
        children: [
          if (widget.errorLeading != null) widget.errorLeading!,
          const Expanded(
            child: ComicGridSkeleton(),
          ),
        ],
      );
    }
    return SmoothCustomScrollView(
      key: enablePageStorage ? PageStorageKey('scroll$_page') : null,
      controller: widget.controller,
      slivers: [
        if (widget.leadingSliver != null) widget.leadingSliver!,
        SliverGridComics(
          comics: _data.values.expand((element) => element).toList(),
          menuBuilder: widget.menuBuilder,
          onLastItemBuild: () {
            if (_error == null && (_maxPage == null || _data.length < _maxPage!)) {
              _loadPage(_data.length + 1);
            }
          },
        ),
        if (_error != null)
          SliverToBoxAdapter(
            child: Column(
              children: [
                Row(
                  children: [
                    const Icon(Icons.error_outline),
                    const SizedBox(width: 8),
                    Expanded(child: Text(_error!, maxLines: 3)),
                  ],
                ),
                const SizedBox(height: 8),
                Center(
                  child: OutlinedButton(
                    onPressed: () {
                      setState(() {
                        _error = null;
                      });
                    },
                    child: Text("Retry".tl),
                  ),
                ),
              ],
            ).paddingHorizontal(16).paddingVertical(8),
          )
        else if (_maxPage == null || _data.length < _maxPage!)
          const SliverListLoadingIndicator(),
        if (widget.trailingSliver != null) widget.trailingSliver!,
      ],
    );
  }
}

class StarRating extends StatelessWidget {
  const StarRating({
    super.key,
    required this.value,
    this.onTap,
    this.size = 20,
  });

  final double value; // 0-5

  final VoidCallback? onTap;

  final double size;

  @override
  Widget build(BuildContext context) {
    var interval = size * 0.1;
    var value = this.value;
    if (value.isNaN) {
      value = 0;
    }
    var child = SizedBox(
      height: size,
      width: size * 5 + interval * 4,
      child: Row(
        children: [
          for (var i = 0; i < 5; i++)
            _Star(
              value: (value - i).clamp(0.0, 1.0),
              size: size,
            ).paddingRight(i == 4 ? 0 : interval),
        ],
      ),
    );
    return onTap == null
        ? child
        : GestureDetector(
            onTap: onTap,
            child: child,
          );
  }
}

class _Star extends StatelessWidget {
  const _Star({required this.value, required this.size});

  final double value; // 0-1

  final double size;

  @override
  Widget build(BuildContext context) {
    return SizedBox(
      width: size,
      height: size,
      child: Stack(
        children: [
          Icon(
            Icons.star_outline,
            size: size,
            color: context.colorScheme.secondary,
          ),
          ClipRect(
            clipper: _StarClipper(value),
            child: Icon(
              Icons.star,
              size: size,
              color: context.colorScheme.secondary,
            ),
          ),
        ],
      ),
    );
  }
}

class _StarClipper extends CustomClipper<Rect> {
  final double value;

  _StarClipper(this.value);

  @override
  Rect getClip(Size size) {
    return Rect.fromLTWH(0, 0, size.width * value, size.height);
  }

  @override
  bool shouldReclip(covariant CustomClipper<Rect> oldClipper) {
    return oldClipper is! _StarClipper || oldClipper.value != value;
  }
}

class RatingWidget extends StatefulWidget {
  /// star number
  final int count;

  /// Max score
  final double maxRating;

  /// Current score value
  final double value;

  /// Star size
  final double size;

  /// Space between the stars
  final double padding;

  /// Whether the score can be modified by sliding
  final bool selectable;

  /// Callbacks when ratings change
  final ValueChanged<double> onRatingUpdate;

  const RatingWidget(
      {super.key,
      this.maxRating = 10.0,
      this.count = 5,
      this.value = 10.0,
      this.size = 20,
      required this.padding,
      this.selectable = false,
      required this.onRatingUpdate});

  @override
  State<RatingWidget> createState() => _RatingWidgetState();
}

class _RatingWidgetState extends State<RatingWidget> {
  double value = 10;

  @override
  Widget build(BuildContext context) {
    return Listener(
      onPointerDown: (PointerDownEvent event) {
        double x = event.localPosition.dx;
        if (x < 0) x = 0;
        pointValue(x);
      },
      onPointerMove: (PointerMoveEvent event) {
        double x = event.localPosition.dx;
        if (x < 0) x = 0;
        pointValue(x);
      },
      onPointerUp: (_) {},
      behavior: HitTestBehavior.deferToChild,
      child: buildRowRating(),
    );
  }

  pointValue(double dx) {
    if (!widget.selectable) {
      return;
    }
    if (dx >=
        widget.size * widget.count + widget.padding * (widget.count - 1)) {
      value = widget.maxRating;
    } else {
      for (double i = 1; i < widget.count + 1; i++) {
        if (dx > widget.size * i + widget.padding * (i - 1) &&
            dx < widget.size * i + widget.padding * i) {
          value = i * (widget.maxRating / widget.count);
          break;
        } else if (dx > widget.size * (i - 1) + widget.padding * (i - 1) &&
            dx < widget.size * i + widget.padding * i) {
          value = (dx - widget.padding * (i - 1)) /
              (widget.size * widget.count) *
              widget.maxRating;
          break;
        }
      }
    }
    if (value % 1 >= 0.5) {
      value = value ~/ 1 + 1;
    } else {
      value = (value ~/ 1).toDouble();
    }
    if (value < 0) {
      value = 0;
    } else if (value > 10) {
      value = 10;
    }
    setState(() {
      widget.onRatingUpdate(value);
    });
  }

  int fullStars() {
    return (value / (widget.maxRating / widget.count)).floor();
  }

  double star() {
    if (widget.count / fullStars() == widget.maxRating / value) {
      return 0;
    }
    return (value % (widget.maxRating / widget.count)) /
        (widget.maxRating / widget.count);
  }

  List<Widget> buildRow() {
    int full = fullStars();
    List<Widget> children = [];
    for (int i = 0; i < full; i++) {
      children.add(Icon(
        Icons.star,
        size: widget.size,
        color: context.colorScheme.secondary,
      ));
      if (i < widget.count - 1) {
        children.add(
          SizedBox(
            width: widget.padding,
          ),
        );
      }
    }
    if (full < widget.count) {
      children.add(ClipRect(
        clipper: _SMClipper(rating: star() * widget.size),
        child: Icon(
          Icons.star,
          size: widget.size,
          color: context.colorScheme.secondary,
        ),
      ));
    }

    return children;
  }

  List<Widget> buildNormalRow() {
    List<Widget> children = [];
    for (int i = 0; i < widget.count; i++) {
      children.add(Icon(
        Icons.star_border,
        size: widget.size,
        color: context.colorScheme.secondary,
      ));
      if (i < widget.count - 1) {
        children.add(SizedBox(
          width: widget.padding,
        ));
      }
    }
    return children;
  }

  Widget buildRowRating() {
    return Stack(
      children: <Widget>[
        Row(
          children: buildNormalRow(),
        ),
        Row(
          children: buildRow(),
        )
      ],
    );
  }

  @override
  void initState() {
    super.initState();
    value = widget.value;
  }
}

class _SMClipper extends CustomClipper<Rect> {
  final double rating;

  _SMClipper({required this.rating});

  @override
  Rect getClip(Size size) {
    return Rect.fromLTRB(0.0, 0.0, rating, size.height);
  }

  @override
  bool shouldReclip(_SMClipper oldClipper) {
    return rating != oldClipper.rating;
  }
}

class SimpleComicTile extends StatelessWidget {
  const SimpleComicTile(
      {super.key, required this.comic, this.onTap, this.withTitle = false, this.heroID});

  final Comic comic;

  final void Function()? onTap;

  final bool withTitle;

  final int? heroID;

  @override
  Widget build(BuildContext context) {
    var image = _findImageProvider(comic);

    Widget child = image == null
        ? const SizedBox()
        : AnimatedImage(
            image: image,
            width: double.infinity,
            height: double.infinity,
            fit: BoxFit.cover,
            // 卡片固定 98dp 宽（见下方 Container）；按它解码即可。
            cacheWidth: coverDecodeWidth(context, 98),
            filterQuality: FilterQuality.low,
          );

    child = CoverHeroChrome(
      width: 98,
      height: 136,
      background: Theme.of(context).colorScheme.secondaryContainer,
      borderRadius: BorderRadius.circular(8),
      child: NsfwCover(comic: comic, child: child),
    );

    if (heroID != null) {
      child = Hero(
        tag: "cover$heroID",
        child: child,
      );
    }

    child = AnimatedTapRegion(
      borderRadius: 8,
      onTap: onTap ??
          () {
            context.to(
              () => ComicPage(
                id: comic.id,
                sourceKey: comic.sourceKey,
                cover: comic.cover,
                title: comic.title,
                heroID: heroID,
              ),
              sharedElementPopTransition: true,
            );
          },
      child: child,
    );

    if (withTitle) {
      child = Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          child,
          const SizedBox(height: 4),
          SizedBox(
            width: 92,
            child: Center(
              child: Text(
                comic.title.replaceAll('\n', ''),
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
              ),
            ),
          ),
        ],
      );
    }

    return child;
  }
}

/// 封面底部的角标（页数 / AI 标记），Miuix 双列卡片专用。
///
/// ## 为什么不是毛玻璃
///
/// 这里原本用 `BackdropFilter` 做雾面玻璃。它在**滚动热路径**上代价很高：
/// 每个角标都要让渲染器为本帧单独起一次离屏合成（saveLayer）并重新采样
/// 背板，一屏 6–10 张卡片就是十几次，滚动时明显掉帧。
///
/// 角标下面压的只是一张静态封面图，糊与不糊肉眼几乎无从分辨；改用足够深的
/// 半透明底（黑 45%）即可保证任意封面上白字可读，合成本钱为零。
class _GlassCoverBadge extends StatelessWidget {
  const _GlassCoverBadge(this.text);

  final String text;

  @override
  Widget build(BuildContext context) {
    return ClipRRect(
      borderRadius: BorderRadius.circular(7),
      child: Container(
        padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2.5),
        color: Colors.black.withValues(alpha: 0.45),
        child: Text(
          text,
          style: const TextStyle(
            color: Colors.white,
            fontSize: 11,
            fontWeight: FontWeight.w600,
            height: 1.15,
          ),
        ),
      ),
    );
  }
}

/// 漫画列表「两列 / 单列」布局切换按钮：切换 `comicDisplayMode`
/// （brief = 两列封面网格 / detailed = 单列大卡）。放在列表页 AppBar
/// actions 里；所有 [SliverGridComics] 网格监听该设置即时重排。
class ComicLayoutToggleButton extends StatelessWidget {
  const ComicLayoutToggleButton({super.key});

  @override
  Widget build(BuildContext context) {
    return AnimatedBuilder(
      animation: appdata.settings,
      builder: (context, _) {
        final isBrief = appdata.settings['comicDisplayMode'] == 'brief';
        return IconButton(
          tooltip: isBrief ? "Single Column".tl : "Two Columns".tl,
          icon: Icon(
            isBrief ? Icons.view_agenda_outlined : Icons.grid_view_outlined,
          ),
          onPressed: () {
            appdata.settings['comicDisplayMode'] =
                isBrief ? 'detailed' : 'brief';
            appdata.saveData();
          },
        );
      },
    );
  }
}
