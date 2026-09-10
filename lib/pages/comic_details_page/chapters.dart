part of 'comic_page.dart';

class _ComicChapters extends StatelessWidget {
  const _ComicChapters({this.history, required this.groupedMode});

  final History? history;

  final bool groupedMode;

  @override
  Widget build(BuildContext context) {
    return groupedMode
        ? _GroupedComicChapters(history)
        : _NormalComicChapters(history);
  }
}

class _NormalComicChapters extends StatefulWidget {
  const _NormalComicChapters(this.history);

  final History? history;

  @override
  State<_NormalComicChapters> createState() => _NormalComicChaptersState();
}

class _NormalComicChaptersState extends State<_NormalComicChapters> {
  late _ComicPageState state;

  late bool reverse;

  bool showAll = false;

  late History? history;

  late ComicChapters chapters;

  @override
  void initState() {
    super.initState();
    reverse = appdata.settings["reverseChapterOrder"] ?? false;
    history = widget.history;
  }

  @override
  void didChangeDependencies() {
    state = context.findAncestorStateOfType<_ComicPageState>()!;
    chapters = state.comic.chapters!;
    super.didChangeDependencies();
  }

  @override
  void didUpdateWidget(covariant _NormalComicChapters oldWidget) {
    super.didUpdateWidget(oldWidget);
    setState(() {
      history = widget.history;
    });
  }

  @override
  Widget build(BuildContext context) {
    return SliverLayoutBuilder(
      builder: (context, constrains) {
        int length = chapters.length;
        bool canShowAll = showAll;
        if (!showAll) {
          var width = constrains.crossAxisExtent - 16;
          // Miuix 画风：紧凑网格每行 3~4 个，一屏展示更多话数。
          var rowLimit = useMiuixStyle ? 4 : 8;
          var crossItems = useMiuixStyle
              ? (width ~/ 160).clamp(3, 4)
              : width ~/ 200;
          if (width % (useMiuixStyle ? 160 : 200) != 0) {
            crossItems += 1;
          }
          length = math.min(length, crossItems * rowLimit);
          if (length == chapters.length) {
            canShowAll = true;
          }
        }

        return SliverMainAxisGroup(
          slivers: [
            SliverToBoxAdapter(
              child: ListTile(
                title: Text("Chapters".tl),
                trailing: Tooltip(
                  message: "Order".tl,
                  child: IconButton(
                    icon: Icon(reverse
                        ? Icons.vertical_align_top
                        : Icons.vertical_align_bottom_outlined),
                    onPressed: () {
                      setState(() {
                        reverse = !reverse;
                      });
                    },
                  ),
                ),
              ),
            ),
            SliverGrid(
              delegate: SliverChildBuilderDelegate(
                childCount: length,
                (context, i) {
                  if (reverse) {
                    i = chapters.length - i - 1;
                  }
                  var key = chapters.ids.elementAt(i);
                  var value = chapters[key]!;
                  bool visited = (history?.readEpisode ?? {}).contains(i + 1);
                  return Padding(
                    padding: EdgeInsets.all(useMiuixStyle ? 3 : 4),
                    child: Material(
                      color: context.colorScheme.surfaceContainer,
                      borderRadius:
                          BorderRadius.circular(useMiuixStyle ? 10 : 16),
                      child: InkWell(
                        onTap: () => state.read(i + 1),
                        borderRadius:
                            BorderRadius.circular(useMiuixStyle ? 10 : 16),
                        child: Padding(
                          padding: const EdgeInsets.symmetric(horizontal: 6),
                          child: Center(
                            child: Text(
                              value,
                              maxLines: 1,
                              textAlign: TextAlign.center,
                              overflow: TextOverflow.ellipsis,
                              style: TextStyle(
                                fontSize: useMiuixStyle ? 12.5 : null,
                                color: visited
                                    ? context.colorScheme.outline
                                    : null,
                              ),
                            ),
                          ),
                        ),
                      ),
                    ),
                  );
                },
              ),
              gridDelegate: SliverGridDelegateWithFixedHeight(
                maxCrossAxisExtent: useMiuixStyle ? 160 : 250,
                itemHeight: useMiuixStyle ? 40 : 48,
              ),
            ).sliverPadding(const EdgeInsets.symmetric(horizontal: 8)),
            if (!canShowAll)
              SliverToBoxAdapter(
                child: Align(
                  alignment: Alignment.center,
                  child: TextButton.icon(
                    icon: const Icon(Icons.arrow_drop_down),
                    onPressed: () {
                      setState(() {
                        showAll = true;
                      });
                    },
                    label: Text("${"Show all".tl} (${chapters.length})"),
                  ).paddingTop(12),
                ),
              ),
            const SliverToBoxAdapter(
              child: Divider(),
            ),
          ],
        );
      },
    );
  }
}

class _GroupedComicChapters extends StatefulWidget {
  const _GroupedComicChapters(this.history);

  final History? history;

  @override
  State<_GroupedComicChapters> createState() => _GroupedComicChaptersState();
}

class _GroupedComicChaptersState extends State<_GroupedComicChapters>
    with SingleTickerProviderStateMixin {
  late _ComicPageState state;

  late bool reverse;

  bool showAll = false;

  late History? history;

  late ComicChapters chapters;

  late TabController tabController;

  late int index;

  @override
  void initState() {
    super.initState();
    reverse = appdata.settings["reverseChapterOrder"] ?? false;
    history = widget.history;
    if (history?.group != null) {
      index = history!.group! - 1;
    } else {
      index = 0;
    }
  }

  @override
  void didChangeDependencies() {
    state = context.findAncestorStateOfType<_ComicPageState>()!;
    chapters = state.comic.chapters!;
    tabController = TabController(
      initialIndex: index,
      length: chapters.ids.length,
      vsync: this,
    );
    tabController.addListener(onTabChange);
    super.didChangeDependencies();
  }

  void onTabChange() {
    if (index != tabController.index) {
      setState(() {
        index = tabController.index;
      });
    }
  }

  @override
  void didUpdateWidget(covariant _GroupedComicChapters oldWidget) {
    super.didUpdateWidget(oldWidget);
    setState(() {
      history = widget.history;
    });
  }

  @override
  Widget build(BuildContext context) {
    return SliverLayoutBuilder(
      builder: (context, constrains) {
        var group = chapters.getGroupByIndex(index);
        int length = group.length;
        bool canShowAll = showAll;
        if (!showAll) {
          var width = constrains.crossAxisExtent - 16;
          var crossItems = width ~/ 200;
          if (width % 200 != 0) {
            crossItems += 1;
          }
          length = math.min(length, crossItems * 8);
          if (length == group.length) {
            canShowAll = true;
          }
        }

        return SliverMainAxisGroup(
          slivers: [
            SliverToBoxAdapter(
              child: ListTile(
                title: Text("Chapters".tl),
                trailing: Tooltip(
                  message: "Order".tl,
                  child: IconButton(
                    icon: Icon(reverse
                        ? Icons.vertical_align_top
                        : Icons.vertical_align_bottom_outlined),
                    onPressed: () {
                      setState(() {
                        reverse = !reverse;
                      });
                    },
                  ),
                ),
              ),
            ),
            SliverToBoxAdapter(
              child: AppTabBar(
                withUnderLine: false,
                controller: tabController,
                tabs: chapters.groups.map((e) => Tab(text: e)).toList(),
              ),
            ),
            SliverPadding(padding: const EdgeInsets.only(top: 8)),
            SliverGrid(
              delegate: SliverChildBuilderDelegate(
                childCount: length,
                (context, i) {
                  if (reverse) {
                    i = group.length - i - 1;
                  }
                  var key = group.keys.elementAt(i);
                  var value = group[key]!;
                  var chapterIndex = 0;
                  for (var j = 0; j < chapters.groupCount; j++) {
                    if (j == index) {
                      chapterIndex += i;
                      break;
                    }
                    chapterIndex += chapters.getGroupByIndex(j).length;
                  }
                  String rawIndex = (chapterIndex + 1).toString();
                  String groupedIndex = "${index + 1}-${i + 1}";
                  bool visited = false;
                  if (history != null) {
                    visited = history!.readEpisode.contains(groupedIndex) ||
                        history!.readEpisode.contains(rawIndex);
                  }
                  return Padding(
                    padding: EdgeInsets.all(useMiuixStyle ? 3 : 4),
                    child: Material(
                      color: context.colorScheme.surfaceContainerLow,
                      borderRadius:
                          BorderRadius.circular(useMiuixStyle ? 10 : 12),
                      child: InkWell(
                        onTap: () => state.read(chapterIndex + 1),
                        borderRadius:
                            BorderRadius.circular(useMiuixStyle ? 10 : 12),
                        child: Padding(
                          padding: const EdgeInsets.symmetric(horizontal: 6),
                          child: Center(
                            child: Text(
                              value,
                              maxLines: 1,
                              textAlign: TextAlign.center,
                              overflow: TextOverflow.ellipsis,
                              style: TextStyle(
                                fontSize: useMiuixStyle ? 12.5 : null,
                                color: visited
                                    ? context.colorScheme.outline
                                    : null,
                              ),
                            ),
                          ),
                        ),
                      ),
                    ),
                  );
                },
              ),
              gridDelegate: SliverGridDelegateWithFixedHeight(
                maxCrossAxisExtent: useMiuixStyle ? 160 : 250,
                itemHeight: useMiuixStyle ? 40 : 48,
              ),
            ).sliverPadding(const EdgeInsets.symmetric(horizontal: 8)),
            if (!canShowAll)
              SliverToBoxAdapter(
                child: Align(
                  alignment: Alignment.center,
                  child: TextButton.icon(
                    icon: const Icon(Icons.arrow_drop_down),
                    onPressed: () {
                      setState(() {
                        showAll = true;
                      });
                    },
                    label: Text("${"Show all".tl} (${group.length})"),
                  ).paddingTop(12),
                ),
              ),
            const SliverToBoxAdapter(
              child: Divider(),
            ),
          ],
        );
      },
    );
  }
}
