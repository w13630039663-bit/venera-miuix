import 'package:flutter/material.dart';
import 'package:flutter_miuix/miuix.dart';
import 'package:shimmer_animation/shimmer_animation.dart';
import 'package:venera/components/components.dart';
import 'package:venera/foundation/app.dart';
import 'package:venera/foundation/appdata.dart';
import 'package:venera/foundation/comic_source/comic_source.dart';
import 'package:venera/foundation/image_provider/cached_image.dart';
import 'package:venera/foundation/res.dart';
import 'package:venera/pages/comic_details_page/comic_page.dart';
import 'package:venera/pages/ranking_page.dart';
import 'package:venera/pages/settings/settings_page.dart';
import 'package:venera/utils/ext.dart';
import 'package:venera/utils/translations.dart';

import 'comic_source_page.dart';

class CategoriesPage extends StatefulWidget {
  const CategoriesPage({super.key});

  @override
  State<CategoriesPage> createState() => _CategoriesPageState();
}

class _CategoriesPageState extends State<CategoriesPage>
    with
        TickerProviderStateMixin,
        AutomaticKeepAliveClientMixin<CategoriesPage> {
  var categories = <String>[];

  late TabController controller;

  void onSettingsChanged() {
    var categories = List.from(
      appdata.settings["categories"],
    ).whereType<String>().toList();
    var allCategories = ComicSource.all()
        .map((e) => e.categoryData?.key)
        .where((element) => element != null)
        .map((e) => e!)
        .toList();
    categories = categories
        .where((element) => allCategories.contains(element))
        .toList();
    if (!categories.isEqualTo(this.categories)) {
      setState(() {
        this.categories = categories;
      });
      controller = TabController(length: categories.length, vsync: this);
    }
  }

  @override
  void initState() {
    super.initState();
    var categories = List.from(
      appdata.settings["categories"],
    ).whereType<String>().toList();
    var allCategories = ComicSource.all()
        .map((e) => e.categoryData?.key)
        .where((element) => element != null)
        .map((e) => e!)
        .toList();
    this.categories = categories
        .where((element) => allCategories.contains(element))
        .toList();
    appdata.settings.addListener(onSettingsChanged);
    controller = TabController(length: categories.length, vsync: this);
  }

  void addPage() {
    showPopUpWidget(App.rootContext, setCategoryPagesWidget());
  }

  /// 分区标签的显示文案（先按 app 翻译表 `tl`，取不到数据时退回 key）。
  List<String> get _categoryLabels => categories.map((e) {
        try {
          return getCategoryDataWithKey(e).title.tl;
        } catch (_) {
          return e;
        }
      }).toList();

  @override
  void dispose() {
    super.dispose();
    controller.dispose();
    appdata.settings.removeListener(onSettingsChanged);
  }

  Widget buildEmpty() {
    var msg = "No Category Pages".tl;
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
    if (categories.isEmpty) {
      return buildEmpty();
    }

    // Miuix 画风：与探索页同款的 HyperOS 胶囊分段 TabRow + 圆形添加按钮；
    // Classic 保持原版下划线 AppTabBar。
    Widget tabBar;
    if (useMiuixStyle) {
      final labels = _categoryLabels;
      tabBar = withMiuixTheme(
        context,
        // Builder：胶囊宽度测量要读注入后的 MiuixTheme。
        Builder(
          builder: (miuixContext) {
            final tabWidth = miuixTabRowWidth(miuixContext, labels);
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
                          final index = (controller.animation!.value.round())
                              .clamp(0, categories.length - 1);
                          return MiuixTabRow(
                            tabs: labels,
                            selectedTabIndex: index,
                            colors: translucentTabRowColors(context),
                            // 与探索页保持一致：按最长分区名自适应宽度 + 加高。
                            minWidth: tabWidth,
                            maxWidth: tabWidth,
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
      tabBar = AppTabBar(
        controller: controller,
        key: PageStorageKey(categories.toString()),
        tabs: categories.map((e) {
          String title = e;
          try {
            title = getCategoryDataWithKey(e).title;
          } catch (e) {
            //
          }
          return Tab(text: title, key: Key(e));
        }).toList(),
        actionButton: TabActionButton(
          icon: const Icon(Icons.add),
          text: "Add".tl,
          onPressed: addPage,
        ),
      ).paddingTop(context.padding.top);
    }

    return Material(
      // 背景由根部 AppBackground 绘制（沉浸式背景/壁纸全局生效）。
      color: Colors.transparent,
      child: Column(
        children: [
          tabBar,
          Expanded(
            child: TabBarView(
              controller: controller,
              children: categories.map((e) => _CategoryPage(e)).toList(),
            ),
          ),
        ],
      ),
    );
  }

  @override
  bool get wantKeepAlive => true;
}

typedef ClickTagCallback = void Function(String, String?);

class _CategoryPage extends StatefulWidget {
  const _CategoryPage(this.category);

  final String category;

  @override
  State<_CategoryPage> createState() => _CategoryPageState();
}

class _CategoryPageState extends State<_CategoryPage>
    with AutomaticKeepAliveClientMixin<_CategoryPage> {
  CategoryData get data => getCategoryDataWithKey(widget.category);

  /// 排行榜橱窗预览：进入页面即提前加载第一页榜单，取前几本展示。
  /// null = 加载中；空列表/失败 = 静默隐藏该区块。
  List<Comic>? _rankingPreview;

  bool _rankingFailed = false;

  CategoryComicsData? _findCategoryComicsData() {
    for (var source in ComicSource.all()) {
      if (source.categoryData?.key == widget.category) {
        return source.categoryComicsData;
      }
    }
    return null;
  }

  @override
  void initState() {
    super.initState();
    _loadRankingPreview();
  }

  Future<void> _loadRankingPreview() async {
    if (!data.enableRankingPage) return;
    var ranking = _findCategoryComicsData()?.rankingData;
    if (ranking == null) return;
    var option = ranking.options.keys.first;
    Res<List<Comic>> res;
    try {
      if (ranking.load != null) {
        res = await ranking.load!(option, 1);
      } else if (ranking.loadWithNext != null) {
        res = await ranking.loadWithNext!(option, null);
      } else {
        return;
      }
    } catch (_) {
      if (mounted) {
        setState(() => _rankingFailed = true);
      }
      return;
    }
    if (!mounted) return;
    setState(() {
      // 列表层同样的屏蔽口径：「H 是不行的」强度为 hide、或命中屏蔽词 /
      // 标签 / 画师 / 收录作品时整条剔除。这里是自绘的卡片流，不走
      // SliverGridComics，必须显式过滤，否则被屏蔽的内容会从这个预览位
      // 漏出来。
      final preview = filterBlocked(res.data).take(10).toList();
      if (res.error || preview.isEmpty) {
        // 全被屏蔽 / 加载失败：整块隐藏，避免留 420dp 的空白。
        _rankingFailed = true;
      } else {
        _rankingPreview = preview;
      }
    });
  }

  void _openRankingPage() {
    context.to(() => RankingPage(categoryKey: data.key));
  }

  @override
  Widget build(BuildContext context) {
    super.build(context);
    var content = SingleChildScrollView(
      // 悬浮液态玻璃底栏会盖住页面底部内容：64dp 栏高 + 20dp 留白，
      // 再加安全区，保证最底部的 tag 也能按到。
      padding: EdgeInsets.only(
        bottom: MediaQuery.viewPaddingOf(context).bottom +
            (appdata.settings['navBarStyle'] == 'floating' ? 80 : 24),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: buildChildren(),
      ),
    );
    // Miuix 取色必须在包裹层树内进行。
    if (useMiuixStyle) {
      return withMiuixTheme(context, content);
    }
    return content;
  }

  List<Widget> buildChildren() {
    var children = <Widget>[];
    // Miuix 画风：排行榜橱窗预览（提前加载），Classic 不渲染。
    if (useMiuixStyle && data.enableRankingPage && !_rankingFailed) {
      children.add(_buildRankingSection());
    }
    if (data.enableRankingPage || data.buttons.isNotEmpty) {
      children.add(buildTitle(data.title));
      children.add(
        Padding(
          padding: const EdgeInsets.fromLTRB(10, 0, 10, 16),
          child: Wrap(
            children: [
              if (data.enableRankingPage)
                buildTag("Ranking".tl, _openRankingPage),
              for (var buttonData in data.buttons)
                buildTag(buttonData.label.tl, buttonData.onTap),
            ],
          ),
        ),
      );
    }

    for (var part in data.categories) {
      if (part.enableRandom) {
        children.add(
          StatefulBuilder(
            builder: (context, updater) {
              return Column(
                mainAxisSize: MainAxisSize.min,
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  buildTitleWithRefresh(part.title, () => updater(() {})),
                  buildTags(part.categories),
                ],
              );
            },
          ),
        );
      } else {
        children.add(buildTitle(part.title));
        children.add(buildTags(part.categories));
      }
    }
    return children;
  }

  /// 排行榜橱窗预览：分区标题 + 横向两行网格（与主页历史区同款几何）。
  Widget _buildRankingSection() {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Builder(builder: (context) {
          return Padding(
            padding: const EdgeInsets.fromLTRB(16, 10, 5, 6),
            child: Row(
              children: [
                Text(
                  "Ranking".tl,
                  style: TextStyle(
                    fontSize: 14,
                    fontWeight: FontWeight.w600,
                    color: MiuixTheme.of(context).colors.onSurfaceVariantSummary,
                  ),
                ),
                const Spacer(),
                TextButton(
                  onPressed: _openRankingPage,
                  child: Text("View more".tl),
                ),
              ],
            ),
          );
        }),
        if (_rankingPreview == null)
          // 骨架：两行灰色占位。
          SizedBox(
            height: 420,
            child: GridView.builder(
              scrollDirection: Axis.horizontal,
              physics: const NeverScrollableScrollPhysics(),
              padding: const EdgeInsets.symmetric(horizontal: 8),
              gridDelegate: const SliverGridDelegateWithFixedCrossAxisCount(
                crossAxisCount: 2,
                crossAxisSpacing: 8,
                mainAxisSpacing: 8,
                mainAxisExtent: 124,
              ),
              itemCount: 4,
              itemBuilder: (context, i) => const _RankingCardSkeleton(),
            ),
          )
        else
          SizedBox(
            height: 420,
            child: GridView.builder(
              scrollDirection: Axis.horizontal,
              padding: const EdgeInsets.symmetric(horizontal: 8),
              gridDelegate: const SliverGridDelegateWithFixedCrossAxisCount(
                crossAxisCount: 2,
                crossAxisSpacing: 8,
                mainAxisSpacing: 8,
                mainAxisExtent: 124,
              ),
              itemCount: _rankingPreview!.length,
              itemBuilder: (context, index) {
                return _RankingCard(
                  comic: _rankingPreview![index],
                  rank: index + 1,
                );
              },
            ),
          ),
        const SizedBox(height: 6),
      ],
    );
  }

  Widget buildTitle(String title) {
    // Miuix 画风：HyperOS 分区小标题；Classic 保持原版大标题。
    if (useMiuixStyle) {
      return Builder(
        builder: (context) => Padding(
          padding: const EdgeInsets.fromLTRB(16, 10, 5, 10),
          child: Text(
            title.tl,
            style: TextStyle(
              fontSize: 14,
              fontWeight: FontWeight.w600,
              color: MiuixTheme.of(context).colors.onSurfaceVariantSummary,
            ),
          ),
        ),
      );
    }
    return Padding(
      padding: const EdgeInsets.fromLTRB(16, 10, 5, 10),
      child: Text(
        title.tl,
        style: const TextStyle(fontSize: 20, fontWeight: FontWeight.w500),
      ),
    );
  }

  Widget buildTitleWithRefresh(String title, void Function() onRefresh) {
    if (useMiuixStyle) {
      return Builder(
        builder: (context) => Padding(
          padding: const EdgeInsets.fromLTRB(16, 10, 5, 10),
          child: Row(
            children: [
              Text(
                title.tl,
                style: TextStyle(
                  fontSize: 14,
                  fontWeight: FontWeight.w600,
                  color: MiuixTheme.of(context).colors.onSurfaceVariantSummary,
                ),
              ),
              const Spacer(),
              MiuixIconButton(
                onPressed: onRefresh,
                child: const Icon(Icons.refresh, size: 18),
              ),
            ],
          ),
        ),
      );
    }
    return Padding(
      padding: const EdgeInsets.fromLTRB(16, 10, 5, 10),
      child: Row(
        children: [
          Text(
            title.tl,
            style: const TextStyle(fontSize: 20, fontWeight: FontWeight.w500),
          ),
          const Spacer(),
          IconButton(onPressed: onRefresh, icon: const Icon(Icons.refresh)),
        ],
      ),
    );
  }

  Widget buildTags(List<CategoryItem> categories) {
    return Padding(
      padding: const EdgeInsets.fromLTRB(10, 0, 10, 16),
      child: Wrap(
        children: List<Widget>.generate(
          categories.length,
          (index) => buildCategory(categories[index]),
        ),
      ),
    );
  }

  Widget buildCategory(CategoryItem c) {
    return buildTag(c.label, () {
      var context = App.mainNavigatorKey!.currentContext!;
      c.target.jump(context);
    });
  }

  Widget buildTag(String label, VoidCallback onClick) {
    if (useMiuixStyle) {
      // Miuix 画风：胶囊标签（surfaceContainer 底 + sink 按压反馈）。
      return Padding(
        padding: const EdgeInsets.fromLTRB(8, 6, 8, 6),
        child: Builder(
          builder: (context) => MiuixCard(
            cornerRadius: 14,
            insideMargin: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
            onPressed: onClick,
            feedbackType: MiuixPressFeedbackType.sink,
            child: Text(
              label,
              style: const TextStyle(fontSize: 14),
            ),
          ),
        ),
      );
    }
    return Padding(
      padding: const EdgeInsets.fromLTRB(8, 6, 8, 6),
      child: Builder(
        builder: (context) {
          return Material(
            borderRadius: const BorderRadius.all(Radius.circular(8)),
            color: context.colorScheme.primaryContainer.toOpacity(0.72),
            child: InkWell(
              borderRadius: const BorderRadius.all(Radius.circular(8)),
              onTap: onClick,
              child: Padding(
                padding: const EdgeInsets.fromLTRB(16, 8, 16, 8),
                child: Text(label),
              ),
            ),
          );
        },
      ),
    );
  }

  @override
  bool get wantKeepAlive => true;
}

/// 排行榜橱窗卡片：封面在上 + 排名角标，标题两行 + 副标题一行。
class _RankingCard extends StatelessWidget {
  const _RankingCard({required this.comic, required this.rank});

  final Comic comic;

  final int rank;

  @override
  Widget build(BuildContext context) {
    return MiuixPressable(
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
      borderRadius: BorderRadius.circular(12),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Expanded(
            child: Stack(
              children: [
                Container(
                  width: double.infinity,
                  decoration: BoxDecoration(
                    borderRadius: BorderRadius.circular(12),
                    color: MiuixTheme.of(context)
                        .colors
                        .onSurfaceVariantSummary
                        .withValues(alpha: 0.12),
                  ),
                  clipBehavior: Clip.antiAlias,
                  // 排行榜预览是自绘封面，绕过了 ComicTile.buildImage，
                  // 所以要自己套遮蔽壳（否则「H 是不行的」对它无效）。
                  child: NsfwCover(
                    comic: comic,
                    child: AnimatedImage(
                      image: CachedImageProvider(
                        comic.cover,
                        sourceKey: comic.sourceKey,
                        cid: comic.id,
                      ),
                      width: double.infinity,
                      height: double.infinity,
                      fit: BoxFit.cover,
                      // 卡片宽度 = 网格 mainAxisExtent（124dp，见上方
                      // SizedBox/ListView 的几何）。
                      cacheWidth: coverDecodeWidth(context, 124),
                      filterQuality: FilterQuality.low,
                    ),
                  ),
                ),
                // 排名角标：1/2/3 金色，其余灰色半透明。
                Positioned(
                  left: 6,
                  top: 6,
                  child: Container(
                    padding: const EdgeInsets.symmetric(
                        horizontal: 7, vertical: 2),
                    decoration: BoxDecoration(
                      borderRadius: BorderRadius.circular(8),
                      color: rank <= 3
                          ? const Color(0xFFF5A623)
                          : Colors.black.withValues(alpha: 0.55),
                    ),
                    child: Text(
                      rank.toString(),
                      style: const TextStyle(
                        fontSize: 12,
                        fontWeight: FontWeight.w700,
                        color: Colors.white,
                        height: 1.2,
                      ),
                    ),
                  ),
                ),
              ],
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
          if (comic.subtitle != null && comic.subtitle!.isNotEmpty)
            Text(
              comic.subtitle!,
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
              style: TextStyle(
                fontSize: 12,
                color: MiuixTheme.of(context).colors.onSurfaceVariantSummary,
              ),
            ),
        ],
      ),
    );
  }
}

/// 排行榜预览骨架占位块。
class _RankingCardSkeleton extends StatelessWidget {
  const _RankingCardSkeleton();

  @override
  Widget build(BuildContext context) {
    final dark = Theme.of(context).brightness == Brightness.dark;
    final color = dark
        ? Colors.white.withValues(alpha: 0.10)
        : Colors.black.withValues(alpha: 0.08);
    return Shimmer(
      color: dark ? Colors.white : Colors.black,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Expanded(
            child: Container(
              width: double.infinity,
              decoration: BoxDecoration(
                borderRadius: BorderRadius.circular(12),
                color: color,
              ),
            ),
          ),
          const SizedBox(height: 6),
          Container(
            width: 90,
            height: 13,
            margin: const EdgeInsets.only(bottom: 5),
            decoration: BoxDecoration(
              borderRadius: BorderRadius.circular(6),
              color: color,
            ),
          ),
          Container(
            width: 56,
            height: 12,
            decoration: BoxDecoration(
              borderRadius: BorderRadius.circular(6),
              color: color,
            ),
          ),
        ],
      ),
    );
  }
}
