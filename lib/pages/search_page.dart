import 'dart:async';
import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_miuix/miuix.dart';
import 'package:sliver_tools/sliver_tools.dart';
import 'package:venera/components/components.dart';
import 'package:venera/foundation/app.dart';
import 'package:venera/foundation/appdata.dart';
import 'package:venera/foundation/comic_source/comic_source.dart';
import 'package:venera/foundation/global_state.dart';
import 'package:venera/pages/aggregated_search_page.dart';
import 'package:venera/pages/settings/settings_page.dart';
import 'package:venera/utils/app_links.dart';
import 'package:venera/utils/ext.dart';
import 'package:venera/utils/multi_tag_search.dart';
import 'package:venera/utils/tags_translation.dart';
import 'package:venera/utils/translations.dart';

import 'comic_details_page/comic_page.dart';
import 'comic_source_page.dart';

class SearchPage extends StatefulWidget {
  const SearchPage({super.key});

  @override
  State<SearchPage> createState() => _SearchPageState();
}

class _SearchPageState extends State<SearchPage> {
  late final SearchBarController controller;

  late List<String> searchSources;

  String searchTarget = "";

  SearchPageData get currentSearchPageData =>
      ComicSource.find(searchTarget)!.searchPageData!;

  bool aggregatedSearch = false;

  var focusNode = FocusNode();

  var options = <String>[];

  /// 搜索设置里选中的站方分区（分类限定）。语义为 AND + 精确匹配，在 Dart 侧
  /// 按列表项的 tags 过滤（仅 [categoryFilterSources] 里的源支持）。
  var selectedCategories = <String>[];

  /// 已提交的搜索词。非 null 表示结果模式 —— 搜索结果显示在本页内，
  /// 不再 push 第二层页面（返回手势/返回键回到搜索输入页）。
  String? submittedQuery;

  /// 多 tag 搜索：已选标签（chips，显示在搜索框内、输入行上方）。
  /// ⊕ 打开底部抽屉输入，点 chip 上的 × 删除。
  final tagList = <String>[];

  /// 结果态下改动 chips 后重搜的防抖计时器（连加几个标签只重搜一次）。
  Timer? _retagDebounce;

  /// 客户端过滤路的连续加载器（MIUIX/AOSP 与之无关，仅单源非原生 tag 源用）。
  TagFilteredLoader? _tagLoader;

  void update() {
    setState(() {});
  }

  /// 切换结果模式（[query] 为 null 表示回到输入态），并同步登记/注销
  /// 「接管系统返回」。
  ///
  /// 系统返回（含返回手势）最先到达**根 Navigator**，只有挂在根路由上的
  /// PopScope 能拦住它；本页挂在内嵌 Navigator 的路由上，拦不住 —— 不登记
  /// 的话结果态按返回会直接退出应用。详见 NaviPane.contentBackOverride。
  void _setResultMode(String? query) {
    setState(() {
      submittedQuery = query;
      if (query != null) {
        // 结果模式复用同一个搜索栏控制器，输入框内容即已提交的关键词。
        controller.currentText = query;
      }
    });
    NaviPane.contentBackOverride.value = query != null;
  }

  /// 提交搜索：结果直接在本页渲染（结果模式），不 push 新路由。
  /// 动态标签输入框（+ 号添加）里的 tag 会与关键词 AND 组合。
  void search([String? text]) {
    final tags = [
      ...{
        for (final t in tagList)
          if (t.trim().isNotEmpty) t.trim(),
      },
    ];
    var base = (text ?? controller.text).trim();
    // 提交后主输入框会被回填成完整关键词（含 tag: 词）。再次搜索时必须先
    // 拆解、只保留纯文本部分，否则旧 tag 会与 tag 输入框里的重复组合。
    base = TagQuery.parse(base).text;
    final query = composeTagQuery(text: base, tags: tags);
    if (query.isEmpty) {
      return;
    }
    // 先回填 chips 再切结果态：_setResultMode 里的 setState 会在下一帧读它们。
    _setupTagFields(query);
    _setResultMode(query);
    appdata.addSearchHistory(query);
  }

  /// 新搜索提交：把关键词里的 tag: 词拆回 chips，并重置过滤加载器。
  void _setupTagFields(String query) {
    final q = TagQuery.parse(query);
    tagList
      ..clear()
      ..addAll(q.tags);
    _tagLoader = null;
  }

  /// 退出结果模式，回到搜索输入页（保留关键词）。
  void exitResult() {
    _setResultMode(null);
  }

  var suggestions = <Pair<String, TranslationType>>[];

  bool canHandleUrl(String text) {
    if (!text.isURL) return false;
    for (var source in ComicSource.all()) {
      if (source.linkHandler != null) {
        var uri = Uri.parse(text);
        if (source.linkHandler!.domains.contains(uri.host)) {
          return true;
        }
      }
    }
    return false;
  }

  void findSuggestions() {
    var text = controller.text.split(" ").last;
    var suggestions = this.suggestions;

    suggestions.clear();

    if (canHandleUrl(controller.text)) {
      suggestions.add(Pair("**URL**", TranslationType.other));
    } else {
      var text = controller.text;

      for (var comicSource in ComicSource.all()) {
        if (comicSource.idMatcher?.hasMatch(text) ?? false) {
          suggestions.add(Pair(
            "**${comicSource.key}**",
            TranslationType.other,
          ));
        }
      }
    }

    if (!ComicSource.find(searchTarget)!.enableTagsSuggestions) {
      update();
      return;
    }

    bool check(String text, String key, String value) {
      if (text.removeAllBlank == "") {
        return false;
      }
      if (key.length >= text.length && key.substring(0, text.length) == text ||
          (key.contains(" ") &&
              key.split(" ").last.length >= text.length &&
              key.split(" ").last.substring(0, text.length) == text)) {
        return true;
      } else if (value.length >= text.length && value.contains(text)) {
        return true;
      }
      return false;
    }

    void find(Map<String, String> map, TranslationType type) {
      for (var element in map.entries) {
        if (suggestions.length > 100) {
          break;
        }
        if (check(text, element.key, element.value)) {
          suggestions.add(Pair(element.key, type));
        }
      }
    }

    find(TagsTranslation.femaleTags, TranslationType.female);
    find(TagsTranslation.maleTags, TranslationType.male);
    find(TagsTranslation.parodyTags, TranslationType.parody);
    find(TagsTranslation.characterTranslations, TranslationType.character);
    find(TagsTranslation.otherTags, TranslationType.other);
    find(TagsTranslation.mixedTags, TranslationType.mixed);
    find(TagsTranslation.languageTranslations, TranslationType.language);
    find(TagsTranslation.artistTags, TranslationType.artist);
    find(TagsTranslation.groupTags, TranslationType.group);
    find(TagsTranslation.cosplayerTags, TranslationType.cosplayer);
    update();
  }

  @override
  void initState() {
    findSearchSources();
    var defaultSearchTarget = appdata.settings['defaultSearchTarget'];
    if (defaultSearchTarget == "_aggregated_") {
      aggregatedSearch = true;
    } else if (defaultSearchTarget != null &&
        searchSources.contains(defaultSearchTarget)) {
      searchTarget = defaultSearchTarget;
    }
    controller = SearchBarController(
      onSearch: search,
    );
    appdata.settings.addListener(updateSearchSourcesIfNeeded);
    super.initState();
  }

  @override
  void dispose() {
    focusNode.dispose();
    _retagDebounce?.cancel();
    appdata.settings.removeListener(updateSearchSourcesIfNeeded);
    // 本页若停在结果态被卸载（切 tab 时 PageView 会销毁页面），必须注销
    // 返回接管，否则别的标签页按返回会被拦下。
    // dispose 可能发生在 build 阶段，直接写会触发 NaviPane 的
    // ValueListenableBuilder 在 build 期 setState，故延到当前任务之后。
    if (submittedQuery != null) {
      Future.microtask(() {
        NaviPane.contentBackOverride.value = false;
      });
    }
    super.dispose();
  }

  void findSearchSources() {
    var all = ComicSource.all()
        .where((e) => e.searchPageData != null)
        .map((e) => e.key)
        .toList();
    var settings = appdata.settings['searchSources'] as List;
    var sources = <String>[];
    for (var source in settings) {
      if (all.contains(source)) {
        sources.add(source);
      }
    }
    searchSources = sources;
    if (!searchSources.contains(searchTarget)) {
      searchTarget = searchSources.firstOrNull ?? "";
    }
  }

  void updateSearchSourcesIfNeeded() {
    var old = searchSources;
    findSearchSources();
    if (old.isEqualTo(searchSources)) {
      return;
    }
    setState(() {});
  }

  void manageSearchSources() {
    showPopUpWidget(App.rootContext, setSearchSourcesWidget());
  }

  Widget buildEmpty() {
    var msg = "No Search Sources".tl;
    msg += '\n';
    VoidCallback onTap;
    if (ComicSource.isEmpty) {
      msg += "Please add some sources".tl;
      onTap = () {
        context.to(() => ComicSourcePage());
      };
    } else {
      msg += "Please check your settings".tl;
      onTap = manageSearchSources;
    }
    return NetworkError(
      message: msg,
      retry: onTap,
      withAppbar: true,
      buttonText: "Manage".tl,
    );
  }

  @override
  Widget build(BuildContext context) {
    if (submittedQuery != null) {
      return buildResultView(context);
    }
    if (searchSources.isEmpty) {
      return buildEmpty();
    }
    Widget body = SmoothCustomScrollView(
      slivers: buildSlivers().toList(),
    );
    // Miuix 画风：整页注入 Miuix 主题（搜索栏胶囊、OptionChip、历史卡片取色）。
    if (useMiuixStyle) {
      body = withMiuixTheme(context, body);
    }
    return Scaffold(
      body: body,
    );
  }

  /// 结果模式：结果直接渲染在本页内（不 push 第二层）。
  /// 布局沿用原版 SearchResultPage 的做法 —— 顶部标准搜索栏（尺寸与首页
  /// 搜索框一致）+ 源标签行 + 默认网格，不做双列瀑布流。
  Widget buildResultView(BuildContext context) {
    final source = ComicSource.find(searchTarget);
    Widget body;

    if (aggregatedSearch) {
      body = AggregatedSearchPage(
        key: ValueKey('aggregated-$submittedQuery'),
        keyword: submittedQuery!,
        embedded: true,
      );
    } else if (source == null ||
        source.searchPageData == null ||
        (source.searchPageData!.loadPage == null &&
            source.searchPageData!.loadNext == null)) {
      body = buildEmpty();
    } else {
      final data = source.searchPageData!;
      // 与原版 SearchResultPage.validateOptions 一致：选项数量对不上时用
      // 默认值补齐，避免把空/过期 options 传给源导致解析异常。
      final searchOptions = data.searchOptions ?? const <SearchOptions>[];
      if (searchOptions.length != options.length) {
        options = searchOptions.map((e) => e.defaultValue).toList();
      }
      // 同理校验分类选择：源更新后消失的分类要剔除，否则过滤条件会把结果搜空。
      final validCategories = clientFilterCategories(searchTarget);
      if (selectedCategories.any((e) => !validCategories.contains(e))) {
        selectedCategories =
            selectedCategories.where(validCategories.contains).toList();
        _tagLoader = null;
      }
      // 已提交关键词里的 tag: 词就是要过滤/透传的标签（输入页的组合结果）。
      final q = TagQuery.parse(submittedQuery!);
      final hasTags = q.tags.isNotEmpty;
      final isNative = nativeTagSearchSources.contains(searchTarget);
      // 分类限定与 tag 词同走客户端过滤：稀疏时由加载器连续抓页补齐。
      final hasCategories = selectedCategories.isNotEmpty;
      final canClientFilter = (hasTags || hasCategories) && !isNative;

      final List<Widget> leadingSlivers = [
        SliverSearchBar(
          controller: controller,
          showBackButton: true,
          action: Row(
            mainAxisSize: MainAxisSize.min,
            children: [
              const ComicLayoutToggleButton(),
              buildResultAction(),
            ],
          ),
        ),
        // 标签 chips 行：搜索栏下方独立一行，胶囊里的按钮一个不动。
        buildTagBar(),
        buildResultSourceBar(),
      ];

      if (canClientFilter && data.loadPage != null) {
        // 多 tag / 分类限定客户端过滤：连续抓页加载器（页码/游标由加载器接管）。
        final key = ValueKey(
            'result-filtered-$searchTarget-$submittedQuery-${options.join(',')}'
            '-${selectedCategories.join(',')}');
        _tagLoader ??= TagFilteredLoader(
          tags: q.tags,
          categories: selectedCategories,
          queryText: q.text,
          options: options,
          loadPage: data.loadPage!,
          loadNext: data.loadNext,
        );
        body = ComicList(
          key: key,
          leadingSliver: SliverMainAxisGroup(slivers: leadingSlivers),
          loadPage: (page) => _tagLoader!.serve(),
        );
      } else {
        // 原生 tag 语法源：改写成站方语法透传；无 tag 词 = 原样搜索。
        final effective = hasTags && isNative
            ? buildNativeTagQuery(searchTarget, q.tags, q.text)
            : submittedQuery!;
        _tagLoader = null;
        body = ComicList(
          key: ValueKey(
              'result-$searchTarget-$effective-${options.join(',')}'),
          leadingSliver: SliverMainAxisGroup(slivers: leadingSlivers),
          loadPage: data.loadPage == null
              ? null
              : (page) =>
                  data.loadPage!(effective, page, options),
          loadNext: data.loadNext == null
              ? null
              : (next) =>
                  data.loadNext!(effective, next, options),
        );
      }
    }

    if (useMiuixStyle) {
      body = withMiuixTheme(context, body);
    }

    // 结果模式：返回手势 / 返回键退回搜索输入页。
    // canPop: false 会关掉本路由的预测返回手势（routes.dart 中
    // popGestureEnabled 遇 popDisposition == doNotPop 即为 false），系统
    // 返回于是改为派发 onPopInvokedWithResult —— 实现「在本页面单独响应
    // 返回手势回到搜索主页」，且完全不涉及路由栈。
    return PopScope(
      canPop: false,
      onPopInvokedWithResult: (didPop, result) {
        if (didPop) {
          return;
        }
        // 从结果里点进漫画详情等会在内嵌导航器上再压一层：此时返回应当
        // 先弹那一层（由 NaviPane 自带的 PopScope 处理），不能把结果模式
        // 一起退掉。maybePop 内部 await，弹出发生在微任务里，所以这里读到的
        // canPop() 仍是真值。
        if (App.mainNavigatorKey?.currentState?.canPop() ?? false) {
          return;
        }
        exitResult();
      },
      child: Scaffold(body: body),
    );
  }

  /// ⊕：打开「添加标签」底部抽屉，确认后作为新 chip 加入多 tag 条件。
  Future<void> _openAddTagSheet() async {
    final text = await showAddTagSheet(context);
    final label = text?.trim() ?? '';
    // 去重：同一标签加两次没有意义，TagQuery 的 AND 语义也不会更严格。
    if (label.isEmpty || tagList.contains(label)) return;
    setState(() {
      tagList.add(label);
    });
    _scheduleRetag();
  }

  /// 删除第 [index] 个标签（由 chip 上的 × 触发，退场动画播完后回调）。
  void _removeTag(int index) {
    if (index < 0 || index >= tagList.length) return;
    setState(() {
      tagList.removeAt(index);
    });
    _scheduleRetag();
  }

  /// 结果态下 chips 变了要立刻反映到结果里——否则 chip 看着像「已生效的条件」，
  /// 实际过滤条件还是旧的，用户会以为改了没反应。输入页（未提交）不需要，
  /// 条件等按下搜索键才提交。
  void _scheduleRetag() {
    if (submittedQuery == null) return;
    _retagDebounce?.cancel();
    _retagDebounce = Timer(const Duration(milliseconds: 350), () {
      if (mounted) {
        search();
      }
    });
  }

  /// 结果模式搜索栏右侧动作：打开搜索设置（切换源 / 调整排序等选项）。
  Widget buildResultAction() {
    return Tooltip(
      message: "Settings".tl,
      child: IconButton(
        icon: const Icon(Icons.tune),
        onPressed: () async {
          final result = await showDialog<SearchSettingsResult>(
            context: context,
            useRootNavigator: true,
            builder: (context) => SearchSettingsDialog(
              initialSourceKey: searchTarget,
              initialOptions: options,
              initialCategories: selectedCategories,
            ),
          );
          if (result == null) return;
          final categoriesChanged =
              !result.categories.isEqualTo(selectedCategories);
          if (result.sourceKey != searchTarget ||
              !result.options.isEqualTo(options) ||
              categoriesChanged) {
            setState(() {
              searchTarget = result.sourceKey;
              options = result.options;
              selectedCategories = result.categories;
              // 过滤条件（选项/分类）变了，旧的连续加载器持有旧条件，不能复用。
              _tagLoader = null;
            });
          }
        },
      ),
    );
  }

  /// 结果模式下的搜索源标签行（横向滚动，保持原版的多源切换能力）。
  Widget buildResultSourceBar() {
    final sources = searchSources.map((e) => ComicSource.find(e)!).toList();
    if (sources.length <= 1) {
      return const SliverToBoxAdapter(child: SizedBox.shrink());
    }
    return SliverToBoxAdapter(
      child: SizedBox(
        height: 44,
        child: ListView.separated(
          scrollDirection: Axis.horizontal,
          padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 4),
          itemCount: sources.length,
          separatorBuilder: (_, __) => const SizedBox(width: 8),
          itemBuilder: (context, i) {
            return Center(
              child: OptionChip(
                text: sources[i].name,
                isSelected: searchTarget == sources[i].key,
                onTap: () {
                  if (searchTarget == sources[i].key) {
                    return;
                  }
                  setState(() {
                    searchTarget = sources[i].key;
                    useDefaultOptions();
                    // 分类是源专属的，换源清空。
                    selectedCategories = <String>[];
                  });
                },
              ),
            );
          },
        ),
      ),
    );
  }

  Iterable<Widget> buildSlivers() sync* {
    // 搜索胶囊保持原样：加标签的入口不在它里面，避免挤压输入框。
    yield SliverSearchBar(
      controller: controller,
      onChanged: (s) {
        findSuggestions();
      },
      focusNode: focusNode,
      // 搜索已是底部标签页，没有「上一层」可返回。
      showBackButton: false,
    );
    if (suggestions.isNotEmpty) {
      yield buildSuggestions(context);
    } else {
      // 标签 chips 行（含「＋ Add」入口）紧跟搜索框 —— 放在最前面，
      // 不要夹在源列表/搜索选项后面（那样离搜索框太远，不好找）。
      yield buildTagBar();
      yield buildSearchTarget();
      yield SliverAnimatedPaintExtent(
        duration: const Duration(milliseconds: 200),
        child: buildSearchOptions(),
      );
      yield _SearchHistory(search);
    }
  }

  /// 标签 chips 行：显示已选条件 + 「＋ Add」入口；紧贴搜索框下方。
  Widget buildTagBar() {
    return SliverToBoxAdapter(
      child: SearchTagBar(
        labels: tagList,
        onRemove: _removeTag,
        onAdd: _openAddTagSheet,
      ),
    );
  }

  Widget buildSearchTarget() {
    var sources = searchSources.map((e) => ComicSource.find(e)!).toList();
    return SliverToBoxAdapter(
      child: Container(
        width: double.infinity,
        padding: const EdgeInsets.symmetric(horizontal: 16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            ListTile(
              contentPadding: EdgeInsets.zero,
              leading: const Icon(Icons.search),
              title: Text("Search in".tl),
              trailing: IconButton(
                icon: const Icon(Icons.settings),
                onPressed: manageSearchSources,
              ),
            ),
            Wrap(
              spacing: 8,
              runSpacing: 8,
              children: sources.map((e) {
                return OptionChip(
                  text: e.name,
                  isSelected: searchTarget == e.key || aggregatedSearch,
                  onTap: () {
                    if (aggregatedSearch) return;
                    setState(() {
                      searchTarget = e.key;
                      useDefaultOptions();
                      // 分类是源专属的，换源清空。
                      selectedCategories = <String>[];
                    });
                  },
                );
              }).toList(),
            ),
            ListTile(
              contentPadding: EdgeInsets.zero,
              title: Text("Aggregated Search".tl),
              leading: useMiuixStyle
                  ? MiuixSwitch(
                      value: aggregatedSearch,
                      onChanged: (value) {
                        setState(() {
                          aggregatedSearch = value;
                        });
                      },
                    )
                  : Checkbox(
                      value: aggregatedSearch,
                      onChanged: (value) {
                        setState(() {
                          aggregatedSearch = value ?? false;
                        });
                      },
                    ),
            ),
          ],
        ),
      ),
    );
  }

  void useDefaultOptions() {
    final searchOptions = currentSearchPageData.searchOptions ?? [];
    options = searchOptions.map((e) => e.defaultValue).toList();
  }

  Widget buildSearchOptions() {
    if (aggregatedSearch) {
      return const SliverToBoxAdapter(child: SizedBox());
    }

    var children = <Widget>[];

    final searchOptions = currentSearchPageData.searchOptions ?? [];
    if (searchOptions.length != options.length) {
      useDefaultOptions();
    }
    if (searchOptions.isEmpty) {
      return const SliverToBoxAdapter(child: SizedBox());
    }
    for (int i = 0; i < searchOptions.length; i++) {
      final option = searchOptions[i];
      children.add(SearchOptionWidget(
        option: option,
        value: options[i],
        onChanged: (value) {
          options[i] = value;
          update();
        },
        sourceKey: searchTarget,
      ));
    }

    return SliverToBoxAdapter(
      child: Container(
        width: double.infinity,
        padding: const EdgeInsets.symmetric(horizontal: 16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: children,
        ),
      ),
    );
  }

  Widget buildSuggestions(BuildContext context) {
    bool check(String text, String key, String value) {
      if (text.removeAllBlank == "") {
        return false;
      }
      if (key.length >= text.length && key.substring(0, text.length) == text ||
          (key.contains(" ") &&
              key.split(" ").last.length >= text.length &&
              key.split(" ").last.substring(0, text.length) == text)) {
        return true;
      } else if (value.length >= text.length && value.contains(text)) {
        return true;
      }
      return false;
    }

    void onSelected(String text, TranslationType? type) {
      var words = controller.text.split(" ");
      if (words.length >= 2 &&
          check("${words[words.length - 2]} ${words[words.length - 1]}", text,
              text.translateTagsToCN)) {
        controller.text = controller.text.replaceLast(
            "${words[words.length - 2]} ${words[words.length - 1]}", "");
      } else {
        controller.text =
            controller.text.replaceLast(words[words.length - 1], "");
      }
      final source = ComicSource.find(searchTarget);
      String insert;
      if (source?.onTagSuggestionSelected != null) {
        insert = source!.onTagSuggestionSelected!(type?.name ?? '', text);
      } else {
        var t = text;
        if (t.contains(' ')) t = "'$t'";
        insert = type != null ? "${type.name}:$t" : t;
      }
      controller.text += "$insert ";
      suggestions.clear();
      update();
      focusNode.requestFocus();
    }

    bool showMethod = MediaQuery.of(context).size.width < 600;
    bool showTranslation = App.locale.languageCode == "zh";
    Widget buildItem(Pair<String, TranslationType> value) {
      if (value.left == "**URL**") {
        return ListTile(
          leading: const Icon(Icons.link),
          title: Text("Open link".tl),
          subtitle: Text(
            controller.text,
            maxLines: 1,
            overflow: TextOverflow.fade,
          ),
          trailing: const Icon(Icons.arrow_right),
          onTap: () {
            setState(() {
              suggestions.clear();
            });
            handleAppLink(Uri.parse(controller.text));
          },
        );
      }

      if (RegExp(r"^\*\*.*\*\*$").hasMatch(value.left)) {
        var key = value.left.substring(2, value.left.length - 2);
        var comicSource = ComicSource.find(key);
        if (comicSource == null) {
          return const SizedBox();
        }
        return ListTile(
          leading: const Icon(Icons.link),
          title: Text("${"Open comic".tl}: ${comicSource.name}"),
          subtitle: Text(
            controller.text,
            maxLines: 1,
            overflow: TextOverflow.fade,
          ),
          trailing: const Icon(Icons.arrow_right),
          onTap: () {
            context.to(
              () => ComicPage(
                sourceKey: key,
                id: controller.text,
              ),
            );
          },
        );
      }

      var subTitle = TagsTranslation.translationTagWithNamespace(
          value.left, value.right.name);
      return ListTile(
        title: Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            Expanded(
              child: Text(value.left),
            ),
            if (!showMethod)
              const SizedBox(
                width: 12,
              ),
            if (!showMethod && showTranslation)
              Text(
                subTitle,
                style: TextStyle(
                  fontSize: 14,
                  color: Theme.of(context).colorScheme.outline,
                ),
              )
          ],
        ),
        subtitle: (showMethod && showTranslation) ? Text(subTitle) : null,
        trailing: Text(
          value.right.name,
          style: const TextStyle(fontSize: 13),
        ),
        onTap: () => onSelected(value.left, value.right),
      );
    }

    return SliverMainAxisGroup(
      slivers: [
        SliverToBoxAdapter(
          child: ListTile(
            leading: const Icon(Icons.hub_outlined),
            title: Text("Suggestions".tl),
            trailing: Tooltip(
              message: "Clear".tl,
              child: IconButton(
                icon: const Icon(Icons.clear_all),
                onPressed: () {
                  suggestions.clear();
                  update();
                },
              ),
            ),
          ),
        ),
        SliverList(
          delegate: SliverChildBuilderDelegate(
            (context, index) {
              return buildItem(suggestions[index]);
            },
            childCount: suggestions.length,
          ),
        ),
      ],
    );
  }
}

class SearchOptionWidget extends StatelessWidget {
  const SearchOptionWidget({
    super.key,
    required this.option,
    required this.value,
    required this.onChanged,
    required this.sourceKey,
  });

  final SearchOptions option;

  final String value;

  final void Function(String) onChanged;

  final String sourceKey;

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        ListTile(
          contentPadding: EdgeInsets.zero,
          title: Text(option.label.ts(sourceKey)),
        ),
        if (option.type == 'select')
          Wrap(
            runSpacing: 8,
            spacing: 8,
            children: option.options.entries.map((e) {
              return OptionChip(
                text: e.value.ts(sourceKey),
                isSelected: value == e.key,
                onTap: () {
                  onChanged(e.key);
                },
              );
            }).toList(),
          ),
        if (option.type == 'multi-select')
          Wrap(
            runSpacing: 8,
            spacing: 8,
            children: option.options.entries.map((e) {
              return OptionChip(
                text: e.value.ts(sourceKey),
                isSelected: (jsonDecode(value) as List).contains(e.key),
                onTap: () {
                  var list = jsonDecode(value) as List;
                  if (list.contains(e.key)) {
                    list.remove(e.key);
                  } else {
                    list.add(e.key);
                  }
                  onChanged(jsonEncode(list));
                },
              );
            }).toList(),
          ),
        if (option.type == 'dropdown')
          Select(
            current: option.options[value],
            values: option.options.values.toList(),
            onTap: (index) {
              onChanged(option.options.keys.elementAt(index));
            },
            minWidth: 96,
          )
      ],
    );
  }
}

/// [SearchSettingsDialog] 的返回结果：用户可能同时切换了搜索源、选项与分类。
class SearchSettingsResult {
  final String sourceKey;
  final List<String> options;

  /// 站方分区（分类限定），见 [categoryFilterSources]。
  final List<String> categories;

  const SearchSettingsResult(
    this.sourceKey,
    this.options, [
    this.categories = const [],
  ]);
}

/// 搜索设置弹窗：切换搜索源 + 调整该源的 searchOptions（含排序）。
///
/// 从 [SearchResultPage] 与 [SearchPage] 结果模式共用，避免重复实现。
class SearchSettingsDialog extends StatefulWidget {
  const SearchSettingsDialog({
    super.key,
    required this.initialSourceKey,
    required this.initialOptions,
    this.initialCategories = const [],
  });

  final String initialSourceKey;

  final List<String> initialOptions;

  /// 已选中的站方分区（分类限定），仅 [categoryFilterSources] 里的源会展示。
  final List<String> initialCategories;

  @override
  State<SearchSettingsDialog> createState() => _SearchSettingsDialogState();
}

class _SearchSettingsDialogState extends State<SearchSettingsDialog> {
  late String sourceKey;

  late List<String> options;

  late List<String> categories;

  @override
  void initState() {
    sourceKey = widget.initialSourceKey;
    options = List.from(widget.initialOptions);
    categories = List.from(widget.initialCategories);
    super.initState();
  }

  @override
  Widget build(BuildContext context) {
    var sources = ComicSource.all();
    var enabled = appdata.settings['searchSources'] as List;
    sources.removeWhere((e) => !enabled.contains(e.key));
    return ContentDialog(
      title: "Settings".tl,
      content: Column(
        children: [
          _buildSection(
            "Search in".tl,
            Wrap(
              spacing: 8,
              runSpacing: 8,
              children: sources.map((e) {
                return OptionChip(
                  text: e.name.tl,
                  isSelected: sourceKey == e.key,
                  onTap: () {
                    setState(() {
                      sourceKey = e.key;
                      options.clear();
                      final searchOptions = ComicSource.find(sourceKey)!
                              .searchPageData!
                              .searchOptions ??
                          <SearchOptions>[];
                      options = searchOptions.map((e) => e.defaultValue).toList();
                      // 分类是源专属的（且只有白名单源支持），换源必须清空。
                      categories = <String>[];
                    });
                  },
                );
              }).toList(),
            ).fixWidth(double.infinity),
          ),
          buildCategoryFilter(),
          buildSearchOptions(),
          const SizedBox(height: 24),
          _buildConfirmButton(),
        ],
      ).fixWidth(double.infinity),
    );
  }

  /// 弹窗分节：Miuix 画风 = 小节标题 + 卡片容器；否则沿用 ListTile 标题 + 平铺。
  /// [title] 为空时不渲染标题。
  Widget _buildSection(String title, Widget body) {
    if (!useMiuixStyle) {
      return Container(
        width: double.infinity,
        padding: const EdgeInsets.symmetric(horizontal: 16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          mainAxisSize: MainAxisSize.min,
          children: [
            if (title.isNotEmpty)
              ListTile(
                contentPadding: const EdgeInsets.symmetric(horizontal: 16),
                title: Text(title),
              ),
            body,
          ],
        ),
      );
    }
    final colorScheme = context.colorScheme;
    return Container(
      width: double.infinity,
      padding: const EdgeInsets.symmetric(horizontal: 16),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        mainAxisSize: MainAxisSize.min,
        children: [
          if (title.isNotEmpty)
            Padding(
              padding: const EdgeInsets.fromLTRB(4, 10, 4, 8),
              child: Text(
                title,
                style: TextStyle(
                  fontSize: 13,
                  fontWeight: FontWeight.w600,
                  color: colorScheme.onSurfaceVariant,
                ),
              ),
            ),
          MiuixCard(
            cornerRadius: 20,
            insideMargin: const EdgeInsets.all(12),
            colors: MiuixCardColors(
              color: colorScheme.surfaceContainerHigh.toOpacity(0.45),
              contentColor: colorScheme.onSurface,
            ),
            child: body,
          ).fixWidth(double.infinity),
        ],
      ),
    );
  }

  Widget _buildConfirmButton() {
    if (!useMiuixStyle) {
      return FilledButton(
        child: Text("Confirm".tl),
        onPressed: () {
          Navigator.pop(
            context,
            SearchSettingsResult(sourceKey, options, categories),
          );
        },
      );
    }
    final colorScheme = context.colorScheme;
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 16),
      child: MiuixButton(
        onPressed: () {
          Navigator.pop(
            context,
            SearchSettingsResult(sourceKey, options, categories),
          );
        },
        minWidth: double.infinity,
        minHeight: 44,
        cornerRadius: 22,
        colors: MiuixButtonColors(
          color: colorScheme.primary,
          disabledColor: colorScheme.onSurface.toOpacity(0.12),
          contentColor: colorScheme.onPrimary,
          disabledContentColor: colorScheme.onSurface.toOpacity(0.38),
        ),
        child: Text("Confirm".tl),
      ),
    );
  }

  Widget buildSearchOptions() {
    var children = <Widget>[];

    final searchOptions =
        ComicSource.find(sourceKey)!.searchPageData!.searchOptions ??
            <SearchOptions>[];
    if (searchOptions.length != options.length) {
      options = searchOptions.map((e) => e.defaultValue).toList();
    }
    if (searchOptions.isEmpty) {
      return const SizedBox();
    }
    for (int i = 0; i < searchOptions.length; i++) {
      final option = searchOptions[i];
      children.add(SearchOptionWidget(
        option: option,
        value: options[i],
        onChanged: (value) {
          setState(() {
            options[i] = value;
          });
        },
        sourceKey: sourceKey,
      ));
    }

    return _buildSection(
      "",
      SizedBox(
        width: double.infinity,
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          mainAxisSize: MainAxisSize.min,
          children: children,
        ),
      ),
    );
  }

  /// 「分类限定」多选组。
  ///
  /// 只对能把站方分区带进列表 tags 的源显示（[categoryFilterSources]，目前是
  /// picacg）。选中项不传给源，而是在结果层按 [filterByTags] 做 AND 过滤，
  /// 因此对不认分类参数的源同样有效。
  Widget buildCategoryFilter() {
    final all = clientFilterCategories(sourceKey);
    if (all.isEmpty) {
      return const SizedBox();
    }
    // 源更新后分类表可能变动，丢掉已失效的选择，避免过滤条件永久卡死。
    categories.removeWhere((e) => !all.contains(e));
    return _buildSection(
      "Categories".tl,
      Wrap(
        spacing: 8,
        runSpacing: 8,
        children: all.map((e) {
          return OptionChip(
            text: e.ts(sourceKey),
            isSelected: categories.contains(e),
            onTap: () {
              setState(() {
                if (categories.contains(e)) {
                  categories.remove(e);
                } else {
                  categories.add(e);
                }
              });
            },
          );
        }).toList(),
      ),
    );
  }
}

class _SearchHistory extends StatefulWidget {
  const _SearchHistory(this.search);

  final void Function(String) search;

  @override
  State<_SearchHistory> createState() => _SearchHistoryState();
}

class _SearchHistoryState extends State<_SearchHistory> {
  @override
  Widget build(BuildContext context) {
    return SliverList(
      delegate: SliverChildBuilderDelegate(
        (context, index) {
          if (index == 0) {
            return const SizedBox(
              height: 16,
            );
          }
          if (index == 1) {
            return ListTile(
              leading: const Icon(Icons.history),
              contentPadding: EdgeInsets.zero,
              title: Text("Search History".tl),
              trailing: Flyout(
                flyoutBuilder: (context) {
                  return FlyoutContent(
                    title: "Clear Search History".tl,
                    actions: [
                      FilledButton(
                        child: Text("Clear".tl),
                        onPressed: () {
                          appdata.clearSearchHistory();
                          context.pop();
                          setState(() {});
                        },
                      )
                    ],
                  );
                },
                child: Builder(
                  builder: (context) {
                    return Tooltip(
                      message: "Clear".tl,
                      child: IconButton(
                        icon: const Icon(Icons.clear_all),
                        onPressed: () {
                          context
                              .findAncestorStateOfType<FlyoutState>()!
                              .show();
                        },
                      ),
                    );
                  },
                ),
              ),
            );
          }
          return buildItem(index - 2);
        },
        childCount: 2 + appdata.searchHistory.length,
      ),
    ).sliverPaddingHorizontal(16);
  }

  Widget buildItem(int index) {
    void showMenu(Offset offset) {
      showMenuX(
        context,
        offset,
        [
          MenuEntry(
            icon: Icons.copy,
            text: 'Copy'.tl,
            onClick: () {
              Clipboard.setData(
                  ClipboardData(text: appdata.searchHistory[index]));
            },
          ),
          MenuEntry(
            icon: Icons.delete,
            text: 'Delete'.tl,
            onClick: () {
              appdata.removeSearchHistory(appdata.searchHistory[index]);
              appdata.saveData();
              setState(() {});
            },
          ),
        ],
      );
    }

    return Builder(builder: (context) {
      // Miuix 画风：历史条目改为胶囊行卡（surfaceContainerHigh + sink 反馈）。
      if (useMiuixStyle) {
        return Padding(
          padding: const EdgeInsets.symmetric(horizontal: 4, vertical: 4),
          child: MiuixCard(
            cornerRadius: 14,
            insideMargin:
                const EdgeInsets.symmetric(horizontal: 14, vertical: 10),
            onPressed: () {
              widget.search(appdata.searchHistory[index]);
            },
            onLongPress: () {
              var renderBox = context.findRenderObject() as RenderBox;
              var offset = renderBox.localToGlobal(Offset.zero);
              showMenu(Offset(
                offset.dx + renderBox.size.width / 2 - 121,
                offset.dy + renderBox.size.height - 8,
              ));
            },
            child: Text(appdata.searchHistory[index], style: ts.s14),
          ),
        );
      }
      return InkWell(
        onTap: () {
          widget.search(appdata.searchHistory[index]);
        },
        onLongPress: () {
          var renderBox = context.findRenderObject() as RenderBox;
          var offset = renderBox.localToGlobal(Offset.zero);
          showMenu(Offset(
            offset.dx + renderBox.size.width / 2 - 121,
            offset.dy + renderBox.size.height - 8,
          ));
        },
        onSecondaryTapUp: (details) {
          showMenu(details.globalPosition);
        },
        child: Container(
          decoration: BoxDecoration(
            border: Border(
              left: BorderSide(
                color: context.colorScheme.outlineVariant,
                width: 2,
              ),
            ),
          ),
          padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
          child: Text(appdata.searchHistory[index], style: ts.s14),
        ),
      ).paddingBottom(8).paddingHorizontal(4);
    });
  }
}
