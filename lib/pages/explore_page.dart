import 'package:flutter/material.dart';
import 'package:flutter_miuix/miuix.dart';
import 'package:venera/components/components.dart';
import 'package:venera/foundation/app.dart';
import 'package:venera/foundation/appdata.dart';
import 'package:venera/foundation/comic_source/comic_source.dart';
import 'package:venera/foundation/global_state.dart';
import 'package:venera/foundation/res.dart';
import 'package:venera/pages/comic_source_page.dart';
import 'package:venera/pages/settings/settings_page.dart';
import 'package:venera/utils/ext.dart';
import 'package:venera/utils/translations.dart';

class ExplorePage extends StatefulWidget {
  const ExplorePage({super.key});

  @override
  State<ExplorePage> createState() => _ExplorePageState();
}

class _ExplorePageState extends State<ExplorePage>
    with TickerProviderStateMixin, AutomaticKeepAliveClientMixin<ExplorePage> {
  late TabController controller;

  bool showFB = true;

  double location = 0;

  late List<String> pages;

  void onSettingsChanged() {
    var explorePages = List<String>.from(appdata.settings["explore_pages"]);
    var all = ComicSource.all()
        .map((e) => e.explorePages)
        .expand((e) => e.map((e) => e.title))
        .toList();
    explorePages = explorePages.where((e) => all.contains(e)).toList();
    _refreshTabMeta();
    if (!pages.isEqualTo(explorePages)) {
      setState(() {
        pages = explorePages;
        controller = TabController(
          length: pages.length,
          vsync: this,
        );
      });
    }
  }

  /// explore 页标题 → 胶囊双列文案（左来源名 / 右分区名）。
  ///
  /// settings 里存的 `explore_pages` 是源插件里的**原文标题**（多数是英文），
  /// 必须用该源的翻译表 `ts(sourceKey)` 转换 —— Classic 分支的 [buildTab]
  /// 一直是这么做的，Miuix 分支漏了这一步，于是标签显示未翻译的
  /// "Picacg Random" 之类。
  ///
  /// 来源名 / 分区名的拆分规则（对 `venera-configs` 全部 36 个源实测过）：
  /// - **绝大多数源的分区标题是自包含的**（「热门推荐」「最近更新」「完结优选」），
  ///   左列直接取 `source.name`，右列就是标题本身；
  /// - **少数源把源名写进了标题**（picacg 的 `Picacg D7` → 「哔咔周榜」）。
  ///   这类源有个可判定的特征：所有分区标题共享同一个原始前缀，且该前缀
  ///   等于 `source.name`。此时把各分区**译名**的最长公共前缀当成中文源名
  ///   （「哔咔」），再从译名里剥掉它，得到分区名（「周榜」）。
  ///   （全量普查里只有 picacg 满足这个特征，所以这条分支不会误伤别的源。）
  /// - 标题与源名相同（不少源的 explore 页就叫源名）、或剥完为空时，
  ///   退化为**单列居中**，不会出现「哔咔 | 哔咔」这种重复。
  ///
  /// 键用**原文标题**，取值按 `ComicSource.all()` 顺序取首个命中 —— 与
  /// [SingleExplorePageState.initState] / [buildTab] 的解析口径一致
  /// （不同源出现同名分区时，全 app 都以第一个为准）。
  Map<String, MiuixTabLabel> _tabMeta = {};

  void _refreshTabMeta() {
    final map = <String, MiuixTabLabel>{};
    for (final source in ComicSource.all()) {
      final pages = source.explorePages;
      if (pages.isEmpty) continue;
      final raws = [for (final page in pages) page.title];
      final fulls = [for (final raw in raws) raw.ts(source.key)];
      // 中文源名：仅当原始标题统一带 `source.name` 前缀时才推导。
      String? cnName;
      final rawPrefix = _commonPrefix(raws).trim();
      if (rawPrefix.isNotEmpty &&
          rawPrefix.toLowerCase() == source.name.toLowerCase()) {
        final common = _commonPrefix(fulls);
        if (common.isNotEmpty &&
            common.length <= 8 &&
            fulls.any((e) => e.length > common.length)) {
          cnName = common;
        }
      }
      for (var i = 0; i < raws.length; i++) {
        final full = fulls[i];
        final sourceLabel = cnName ?? source.name;
        var category = full;
        if (category.length > sourceLabel.length &&
            category.toLowerCase().startsWith(sourceLabel.toLowerCase())) {
          category = category.substring(sourceLabel.length).trim();
        }
        final single = category.isEmpty ||
            category.toLowerCase() == sourceLabel.toLowerCase();
        map.putIfAbsent(
          raws[i],
          () => single ? MiuixTabLabel("", full) : MiuixTabLabel(sourceLabel, category),
        );
      }
    }
    _tabMeta = map;
  }

  MiuixTabLabel _metaOf(String title) =>
      _tabMeta[title] ?? MiuixTabLabel("", title);

  static String _commonPrefix(List<String> strs) {
    if (strs.isEmpty) return "";
    var prefix = strs.first;
    for (final s in strs.skip(1)) {
      var n = 0;
      while (n < prefix.length &&
          n < s.length &&
          prefix.codeUnitAt(n) == s.codeUnitAt(n)) {
        n++;
      }
      prefix = prefix.substring(0, n);
      if (prefix.isEmpty) break;
    }
    return prefix;
  }

  void onNaviItemTapped(int index) {
    if (index == 2) {
      int page = controller.index;
      String currentPageId = pages[page];
      GlobalState.find<SingleExplorePageState>(currentPageId).toTop();
    }
  }

  void addPage() {
    showPopUpWidget(App.rootContext, setExplorePagesWidget());
  }

  NaviPaneState? naviPane;

  @override
  void initState() {
    pages = List<String>.from(appdata.settings["explore_pages"]);
    var all = ComicSource.all()
        .map((e) => e.explorePages)
        .expand((e) => e.map((e) => e.title))
        .toList();
    pages = pages.where((e) => all.contains(e)).toList();
    _refreshTabMeta();
    controller = TabController(
      length: pages.length,
      vsync: this,
    );
    appdata.settings.addListener(onSettingsChanged);
    NaviPane.of(context).addNaviItemTapListener(onNaviItemTapped);
    super.initState();
  }

  @override
  void didChangeDependencies() {
    naviPane = NaviPane.of(context);
    super.didChangeDependencies();
  }

  @override
  void dispose() {
    controller.dispose();
    appdata.settings.removeListener(onSettingsChanged);
    naviPane?.removeNaviItemTapListener(onNaviItemTapped);
    super.dispose();
  }

  void refresh() {
    int page = controller.index;
    String currentPageId = pages[page];
    GlobalState.find<SingleExplorePageState>(currentPageId).refresh();
  }

  Widget buildFAB() => Material(
        color: Colors.transparent,
        child: FloatingActionButton(
          key: const Key("FAB"),
          onPressed: refresh,
          child: const Icon(Icons.refresh),
        ),
      );

  Tab buildTab(String i) {
    var comicSource = ComicSource.all()
        .firstWhere((e) => e.explorePages.any((e) => e.title == i));
    return Tab(text: i.ts(comicSource.key), key: Key(i));
  }

  Widget buildBody(String i) => Material(
        // 背景由根部 AppBackground 绘制（沉浸式背景/壁纸全局生效）。
        color: Colors.transparent,
        child: SingleExplorePage(i, key: PageStorageKey(i)),
      );

  Widget buildEmpty() {
    var msg = "No Explore Pages".tl;
    msg += '\n';
    VoidCallback onTap;
    if (ComicSource.isEmpty) {
      msg += "Please add some sources".tl;
      onTap = () {
        context.to(() => ComicSourcePage());
      };
    } else {
      msg += "Please check your settings".tl;
      onTap = addPage;
    }
    return NetworkError(
      message: msg,
      retry: onTap,
      withAppbar: false,
      buttonText: "Manage".tl,
    );
  }

  @override
  Widget build(BuildContext context) {
    super.build(context);
    if (pages.isEmpty) {
      return buildEmpty();
    }

    // Miuix 画风：HyperOS 胶囊分段 TabRow（随 TabController 滑动/点击双向
    // 同步）+ 右侧圆形添加按钮；Classic 保持原版下划线 AppTabBar。
    Widget tabBar;
    if (useMiuixStyle) {
      final labels = [for (final page in pages) _metaOf(page)];
      tabBar = withMiuixTheme(
        context,
        // Builder：胶囊宽度测量要读注入后的 MiuixTheme（取 body1/body2 字号）。
        Builder(
          builder: (miuixContext) {
            return Material(
              color: Colors.transparent,
              child: Padding(
                padding: EdgeInsets.fromLTRB(12, context.padding.top + 4, 12, 6),
                child: Row(
                  children: [
                    Expanded(
                      child: AnimatedBuilder(
                        animation: controller.animation!,
                        builder: (context, _) {
                          final progress =
                              controller.animation!.value.clamp(
                            0.0,
                            (pages.length - 1).toDouble(),
                          );
                          // 双列胶囊：左「来源名」右「分区名」，每颗按自身
                          // 内容实测宽度（库的 MiuixTabRow 只支持等宽单列，
                          // 会把「哔咔周榜」这类标签压到 76dp 截断）。
                          return MiuixTwoColumnTabRow(
                            tabs: labels,
                            progress: progress,
                            selectedTabIndex: progress.round(),
                            colors: translucentTabRowColors(context),
                            height: 48,
                            cornerRadius: 14,
                            onTabSelected: (i) {
                              controller.animateTo(
                                i,
                                duration: const Duration(milliseconds: 280),
                                curve: Curves.easeOutCubic,
                              );
                            },
                          );
                        },
                      ),
                    ),
                    const SizedBox(width: 10),
                    MiuixIconButton(
                      onPressed: addPage,
                      child: const Icon(Icons.add, size: 20),
                    ),
                  ],
                ),
              ),
            );
          },
        ),
      );
    } else {
      tabBar = Material(
        color: Colors.transparent,
        child: AppTabBar(
          key: PageStorageKey(pages.toString()),
          tabs: pages.map((e) => buildTab(e)).toList(),
          controller: controller,
          actionButton: TabActionButton(
            icon: const Icon(Icons.add),
            text: "Add".tl,
            onPressed: addPage,
          ),
        ),
      ).paddingTop(context.padding.top);
    }

    return Stack(
      children: [
        Positioned.fill(
          child: Column(
            children: [
              tabBar,
              Expanded(
                child: NotificationListener<ScrollNotification>(
                  onNotification: (notifications) {
                    if (notifications.metrics.axis == Axis.horizontal) {
                      if (!showFB) {
                        setState(() {
                          showFB = true;
                        });
                      }
                      return true;
                    }

                    var current = notifications.metrics.pixels;
                    var overflow = notifications.metrics.outOfRange;
                    if (current > location && current != 0 && showFB) {
                      setState(() {
                        showFB = false;
                      });
                    } else if ((current < location - 50 || current == 0) &&
                        !showFB) {
                      setState(() {
                        showFB = true;
                      });
                    }
                    if ((current > location || current < location - 50) &&
                        !overflow) {
                      location = current;
                    }
                    return false;
                  },
                  child: MediaQuery.removePadding(
                    context: context,
                    removeTop: true,
                    child: TabBarView(
                      controller: controller,
                      children: pages.map((e) => buildBody(e)).toList(),
                    ),
                  ),
                ),
              )
            ],
          ),
        ),
        Positioned(
          right: 16,
          // floating 底栏是悬浮玻璃胶囊（inset + 8/20dp 留白 + 56dp 高），
          // 会盖住 bottom:16 的 FAB —— 抬到胶囊上方 12dp；其它风格维持原位。
          bottom: appdata.settings['navBarStyle'] == 'floating'
              ? MediaQuery.viewPaddingOf(context).bottom +
                  (MediaQuery.viewPaddingOf(context).bottom != 0 ? 8.0 : 20.0) +
                  56.0 +
                  12.0
              : 16,
          child: AnimatedSwitcher(
            duration: const Duration(milliseconds: 150),
            reverseDuration: const Duration(milliseconds: 150),
            child: showFB ? buildFAB() : const SizedBox(),
            transitionBuilder: (widget, animation) {
              var tween = Tween<Offset>(
                  begin: const Offset(0, 1), end: const Offset(0, 0));
              return SlideTransition(
                position: tween.animate(animation),
                child: widget,
              );
            },
          ),
        )
      ],
    );
  }

  @override
  bool get wantKeepAlive => true;
}

class SingleExplorePage extends StatefulWidget {
  const SingleExplorePage(this.title, {super.key});

  final String title;

  @override
  State<SingleExplorePage> createState() => SingleExplorePageState();
}

class SingleExplorePageState extends AutomaticGlobalState<SingleExplorePage>
    with AutomaticKeepAliveClientMixin<SingleExplorePage> {
  late final ExplorePageData data;

  late final String comicSourceKey;

  bool _wantKeepAlive = true;

  var scrollController = ScrollController();

  VoidCallback? refreshHandler;

  void onSettingsChanged() {
    var explorePages = appdata.settings["explore_pages"];
    if (!explorePages.contains(widget.title)) {
      _wantKeepAlive = false;
      updateKeepAlive();
    }
  }

  @override
  void initState() {
    super.initState();
    for (var source in ComicSource.all()) {
      for (var d in source.explorePages) {
        if (d.title == widget.title) {
          data = d;
          comicSourceKey = source.key;
          return;
        }
      }
    }
    appdata.settings.addListener(onSettingsChanged);
    throw "Explore Page ${widget.title} Not Found!";
  }

  @override
  void dispose() {
    appdata.settings.removeListener(onSettingsChanged);
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    super.build(context);
    if (data.loadMultiPart != null) {
      return _MultiPartExplorePage(
        key: const PageStorageKey("comic_list"),
        data: data,
        controller: scrollController,
        comicSourceKey: comicSourceKey,
        refreshHandlerCallback: (c) {
          refreshHandler = c;
        },
      );
    } else if (data.loadPage != null || data.loadNext != null) {
      return ComicList(
        enablePageStorage: true,
        loadPage: data.loadPage,
        loadNext: data.loadNext,
        key: const PageStorageKey("comic_list"),
        controller: scrollController,
        refreshHandlerCallback: (c) {
          refreshHandler = c;
        },
      );
    } else if (data.loadMixed != null) {
      return _MixedExplorePage(
        data,
        comicSourceKey,
        key: const PageStorageKey("comic_list"),
        controller: scrollController,
        refreshHandlerCallback: (c) {
          refreshHandler = c;
        },
      );
    } else {
      return const Center(
        child: Text("Empty Page"),
      );
    }
  }

  @override
  Object? get key => widget.title;

  @override
  void refresh() {
    refreshHandler?.call();
  }

  @override
  bool get wantKeepAlive => _wantKeepAlive;

  void toTop() {
    if (scrollController.hasClients) {
      scrollController.animateTo(
        scrollController.position.minScrollExtent,
        duration: const Duration(milliseconds: 200),
        curve: Curves.easeInOut,
      );
    }
  }
}

class _MixedExplorePage extends StatefulWidget {
  const _MixedExplorePage(this.data, this.sourceKey,
      {super.key, this.controller, required this.refreshHandlerCallback});

  final ExplorePageData data;

  final String sourceKey;

  final ScrollController? controller;

  final void Function(VoidCallback c) refreshHandlerCallback;

  @override
  State<_MixedExplorePage> createState() => _MixedExplorePageState();
}

class _MixedExplorePageState
    extends MultiPageLoadingState<_MixedExplorePage, Object> {
  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    widget.refreshHandlerCallback(refresh);
  }

  void refresh() {
    reset();
  }

  Iterable<Widget> buildSlivers(BuildContext context, List<Object> data) sync* {
    List<Comic> cache = [];
    for (var part in data) {
      if (part is ExplorePagePart) {
        if (cache.isNotEmpty) {
          yield SliverGridComics(
            comics: (cache),
            twoColumnMiuix: useMiuixStyle,
          );
          yield const SliverToBoxAdapter(child: Divider());
          cache.clear();
        }
        yield* _buildExplorePagePart(part, widget.sourceKey);
        yield const SliverToBoxAdapter(child: Divider());
      } else {
        cache.addAll(part as List<Comic>);
      }
    }
    if (cache.isNotEmpty) {
      yield SliverGridComics(
        comics: (cache),
        twoColumnMiuix: useMiuixStyle,
      );
    }
  }

  @override
  Widget buildContent(BuildContext context, List<Object> data) {
    return SmoothCustomScrollView(
      controller: widget.controller,
      slivers: [
        ...buildSlivers(context, data),
        const SliverListLoadingIndicator(),
      ],
    );
  }

  @override
  Future<Res<List<Object>>> loadData(int page) async {
    var res = await widget.data.loadMixed!(page);
    if (res.error) {
      return res;
    }
    for (var element in res.data) {
      if (element is! ExplorePagePart && element is! List<Comic>) {
        return const Res.error("function loadMixed return invalid data");
      }
    }
    return res;
  }
}

Iterable<Widget> _buildExplorePagePart(
    ExplorePagePart part, String sourceKey) sync* {
  Widget buildTitle(ExplorePagePart part) {
    // Miuix 画风：HyperOS 分区小标题（紧凑、次级色）；Classic 保持原版大标题。
    // 顶层函数没有 context，用 Builder 自建（内层才有注入的 MiuixTheme）。
    return SliverToBoxAdapter(
      child: Builder(
        builder: (outerContext) => withMiuixTheme(
          outerContext,
          Builder(
            builder: (context) => SizedBox(
              height: useMiuixStyle ? 44 : 60,
              child: Padding(
                padding:
                    EdgeInsets.fromLTRB(16, 10, 5, useMiuixStyle ? 6 : 10),
                child: Row(
                  children: [
                    if (useMiuixStyle)
                      Text(
                        part.title,
                        style: TextStyle(
                          fontSize: 14,
                          fontWeight: FontWeight.w600,
                          color: MiuixTheme.of(context)
                              .colors
                              .onSurfaceVariantSummary,
                        ),
                      )
                    else
                      Text(
                        part.title,
                        style: const TextStyle(
                            fontSize: 20, fontWeight: FontWeight.w500),
                      ),
                    const Spacer(),
                    if (part.viewMore != null)
                      TextButton(
                        onPressed: () {
                          var context = App.mainNavigatorKey!.currentContext!;
                          part.viewMore!.jump(context);
                        },
                        child: Text("View more".tl),
                      )
                  ],
                ),
              ),
            ),
          ),
        ),
      ),
    );
  }

  Widget buildComics(ExplorePagePart part) {
    // Miuix 画风：双列卡片网格（标题移到封面下方保留）。
    return SliverGridComics(
      comics: part.comics,
      twoColumnMiuix: useMiuixStyle,
    );
  }

  yield buildTitle(part);
  yield buildComics(part);
}

class _MultiPartExplorePage extends StatefulWidget {
  const _MultiPartExplorePage({
    super.key,
    required this.data,
    required this.controller,
    required this.comicSourceKey,
    required this.refreshHandlerCallback,
  });

  final ExplorePageData data;

  final ScrollController controller;

  final String comicSourceKey;

  final void Function(VoidCallback c) refreshHandlerCallback;

  @override
  State<_MultiPartExplorePage> createState() => _MultiPartExplorePageState();
}

class _MultiPartExplorePageState extends State<_MultiPartExplorePage> {
  late final ExplorePageData data;

  List<ExplorePagePart>? parts;

  bool loading = true;

  String? message;

  Map<String, dynamic> get state => {
        "loading": loading,
        "message": message,
        "parts": parts,
      };

  void restoreState(dynamic state) {
    if (state == null) return;
    loading = state["loading"];
    message = state["message"];
    parts = state["parts"];
  }

  void storeState() {
    PageStorage.of(context).writeState(context, state);
  }

  void refresh() {
    setState(() {
      loading = true;
      message = null;
      parts = null;
    });
    storeState();
  }

  @override
  void initState() {
    super.initState();
    data = widget.data;
  }

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    restoreState(PageStorage.of(context).readState(context));
    widget.refreshHandlerCallback(refresh);
  }

  void load() async {
    var res = await data.loadMultiPart!();
    loading = false;
    if (mounted) {
      setState(() {
        if (res.error) {
          message = res.errorMessage;
        } else {
          parts = res.data;
        }
      });
      storeState();
    }
  }

  @override
  Widget build(BuildContext context) {
    if (loading) {
      load();
      return const Center(
        child: CircularProgressIndicator(),
      );
    } else if (message != null) {
      return NetworkError(
        message: message!,
        retry: () {
          setState(() {
            loading = true;
            message = null;
          });
        },
        withAppbar: false,
      );
    } else {
      return buildPage();
    }
  }

  Widget buildPage() {
    return SmoothCustomScrollView(
      key: const PageStorageKey('scroll'),
      controller: widget.controller,
      slivers: _buildPage().toList(),
    );
  }

  Iterable<Widget> _buildPage() sync* {
    for (var part in parts!) {
      yield* _buildExplorePagePart(part, widget.comicSourceKey);
    }
  }
}
