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

  /// 已提交的搜索词。非 null 表示结果模式 —— 搜索结果显示在本页内，
  /// 不再 push 第二层页面（返回手势/返回键回到搜索输入页）。
  String? submittedQuery;

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
  void search([String? text]) {
    final query = (text ?? controller.text).trim();
    if (query.isEmpty) {
      return;
    }
    _setResultMode(query);
    appdata.addSearchHistory(query);
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
      body = ComicList(
        key: ValueKey(
            'result-$searchTarget-$submittedQuery-${options.join(',')}'),
        leadingSliver: SliverMainAxisGroup(
          slivers: [
            SliverSearchBar(
              controller: controller,
              showBackButton: true,
              action: buildResultAction(),
            ),
            buildResultSourceBar(),
          ],
        ),
        loadPage: data.loadPage == null
            ? null
            : (page) => data.loadPage!(submittedQuery!, page, options),
        loadNext: data.loadNext == null
            ? null
            : (next) => data.loadNext!(submittedQuery!, next, options),
      );
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
            ),
          );
          if (result == null) return;
          if (result.sourceKey != searchTarget ||
              !result.options.isEqualTo(options)) {
            setState(() {
              searchTarget = result.sourceKey;
              options = result.options;
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
      yield buildSearchTarget();
      yield SliverAnimatedPaintExtent(
        duration: const Duration(milliseconds: 200),
        child: buildSearchOptions(),
      );
      yield _SearchHistory(search);
    }
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

/// [SearchSettingsDialog] 的返回结果：用户可能同时切换了搜索源与选项。
class SearchSettingsResult {
  final String sourceKey;
  final List<String> options;

  const SearchSettingsResult(this.sourceKey, this.options);
}

/// 搜索设置弹窗：切换搜索源 + 调整该源的 searchOptions（含排序）。
///
/// 从 [SearchResultPage] 与 [SearchPage] 结果模式共用，避免重复实现。
class SearchSettingsDialog extends StatefulWidget {
  const SearchSettingsDialog({
    super.key,
    required this.initialSourceKey,
    required this.initialOptions,
  });

  final String initialSourceKey;

  final List<String> initialOptions;

  @override
  State<SearchSettingsDialog> createState() => _SearchSettingsDialogState();
}

class _SearchSettingsDialogState extends State<SearchSettingsDialog> {
  late String sourceKey;

  late List<String> options;

  @override
  void initState() {
    sourceKey = widget.initialSourceKey;
    options = List.from(widget.initialOptions);
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
          ListTile(
            contentPadding: const EdgeInsets.symmetric(horizontal: 16),
            title: Text("Search in".tl),
          ),
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
                  });
                },
              );
            }).toList(),
          ).fixWidth(double.infinity).paddingHorizontal(16),
          buildSearchOptions(),
          const SizedBox(height: 24),
          FilledButton(
            child: Text("Confirm".tl),
            onPressed: () {
              Navigator.pop(
                context,
                SearchSettingsResult(sourceKey, options),
              );
            },
          ),
        ],
      ).fixWidth(double.infinity),
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

    return Container(
      width: double.infinity,
      padding: const EdgeInsets.symmetric(horizontal: 16),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        mainAxisSize: MainAxisSize.min,
        children: children,
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
