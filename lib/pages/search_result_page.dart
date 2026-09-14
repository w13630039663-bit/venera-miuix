import 'package:flutter/material.dart';
import 'package:venera/components/components.dart';
import 'package:venera/foundation/app.dart';
import 'package:venera/foundation/appdata.dart';
import 'package:venera/foundation/comic_source/comic_source.dart';
import 'package:venera/foundation/global_state.dart';
import 'package:venera/pages/search_page.dart';
import 'package:venera/utils/ext.dart';
import 'package:venera/utils/multi_tag_search.dart';
import 'package:venera/utils/tags_translation.dart';
import 'package:venera/utils/translations.dart';

class SearchResultPage extends StatefulWidget {
  const SearchResultPage({
    super.key,
    required this.text,
    required this.sourceKey,
    this.options,
  });

  final String text;

  final String sourceKey;

  final List<String>? options;

  @override
  State<SearchResultPage> createState() => _SearchResultPageState();
}

class _SearchResultPageState extends State<SearchResultPage> {
  late SearchBarController controller;

  late String sourceKey;

  late List<String> options;

  late String text;

  /// 多 tag 条件（chips，`tag:` 语义）。与关键词 AND 组合：原生源转站方
  /// 标签语法透传，其余源客户端过滤，见 lib/utils/multi_tag_search.dart。
  final tagList = <String>[];

  /// 搜索设置里选中的站方分区（分类限定），见 [categoryFilterSources]。
  var selectedCategories = <String>[];

  /// 分类限定的连续抓页加载器（只在选中分类时启用）。
  TagFilteredLoader? _categoryLoader;

  OverlayEntry? get suggestionOverlay => suggestionsController.entry;

  late _SuggestionsController suggestionsController;

  /// 关键词（纯文本）与 chips 组合后的完整查询词。
  String get effectiveQuery => composeTagQuery(text: text, tags: tagList);

  void search([String? text]) {
    if (text != null) {
      if (suggestionsController.entry != null) {
        suggestionsController.remove();
      }
      // 手输的关键词里若有 `tag:` 词，收编进 chips、输入框只留纯文本，
      // 否则会和 chips 里的条件重复组合（与主搜索页一致）。
      final parsed = TagQuery.parse(text);
      text = checkAutoLanguage(parsed.text);
      setState(() {
        tagList
          ..clear()
          ..addAll(parsed.tags);
        this.text = text!;
        // 换关键词 = 换请求，旧的过滤加载器不能复用。
        _categoryLoader = null;
      });
      appdata.addSearchHistory(effectiveQuery);
      controller.currentText = text;
    }
  }

  void onChanged(String s) {
    if (!ComicSource.find(sourceKey)!.enableTagsSuggestions) {
      return;
    }
    suggestionsController.findSuggestions();
    if (suggestionOverlay != null) {
      if (suggestionsController.suggestions.isEmpty) {
        suggestionsController.remove();
      } else {
        suggestionsController.updateWidget();
      }
    } else if (suggestionsController.suggestions.isNotEmpty) {
      suggestionsController.entry = OverlayEntry(
        builder: (context) {
          return Positioned(
            top: context.padding.top + 56,
            left: 0,
            right: 0,
            bottom: 0,
            child: Material(
              child: _Suggestions(
                controller: suggestionsController,
              ),
            ),
          );
        },
      );
      Overlay.of(context).insert(suggestionOverlay!);
    }
  }

  @override
  void dispose() {
    Future.microtask(() {
      suggestionsController.remove();
    });
    super.dispose();
  }

  String checkAutoLanguage(String text) {
    var setting = appdata.settings["autoAddLanguageFilter"] ?? 'none';
    if (setting == 'none') {
      return text;
    }
    var searchSource = sourceKey;
    // TODO: Move it to a better place
    const enabledSources = [
      'nhentai',
      'ehentai',
    ];
    if (!enabledSources.contains(searchSource)) {
      return text;
    }
    if (!text.contains('language:')) {
      return '$text language:$setting';
    }
    return text;
  }

  @override
  void initState() {
    sourceKey = widget.sourceKey;
    // 关键词里若带 `tag:` 词（主搜索页 / 标签云跳进来时可能有），拆出来放
    // chips、输入框只留纯文本，与主搜索页的 chips 语义保持一致。
    final parsed = TagQuery.parse(widget.text);
    tagList.addAll(parsed.tags);
    text = checkAutoLanguage(parsed.text);
    controller = SearchBarController(
      currentText: text,
      onSearch: search,
    );
    options = widget.options ?? const [];
    validateOptions();
    appdata.addSearchHistory(effectiveQuery);
    suggestionsController = _SuggestionsController(controller, sourceKey);
    super.initState();
  }

  void validateOptions() {
    var source = ComicSource.find(sourceKey);
    if (source == null) {
      return;
    }
    // 分类选择与选项无关，单独校验：源更新后消失的分类要剔除，否则会搜空。
    final validCategories = clientFilterCategories(sourceKey);
    if (selectedCategories.any((e) => !validCategories.contains(e))) {
      selectedCategories =
          selectedCategories.where(validCategories.contains).toList();
      _categoryLoader = null;
    }
    var searchOptions = source.searchPageData!.searchOptions;
    if (searchOptions == null) {
      return;
    }
    if (options.length != searchOptions.length) {
      options = searchOptions.map((e) => e.defaultValue).toList();
    }
  }

  @override
  Widget build(BuildContext context) {
    var source = ComicSource.find(sourceKey);
    // 关键词 = 输入框纯文本 + chips 里的 `tag:` 词（AND 语义）：原生源转站方
    // 标签语法透传，其余源剥词后客户端过滤，见 lib/utils/multi_tag_search.dart。
    final query = effectiveQuery;
    final q = TagQuery.parse(query);
    final isNative = nativeTagSearchSources.contains(sourceKey);
    final clientTags = isNative ? const <String>[] : q.tags;
    // 分类限定 / 客户端 tag 过滤：选中时改走连续抓页加载器（过滤后结果稀疏，
    // 需要按源分页连着抓几页才能凑够一批）。
    if ((clientTags.isNotEmpty || selectedCategories.isNotEmpty) &&
        (source?.searchPageData?.loadPage != null)) {
      _categoryLoader ??= TagFilteredLoader(
        tags: clientTags,
        categories: selectedCategories,
        queryText: q.text,
        options: options,
        loadPage: source!.searchPageData!.loadPage!,
        loadNext: source.searchPageData!.loadNext,
      );
    } else {
      _categoryLoader = null;
    }
    // 单源搜索结果 = 用户自己选了这个源来搜，属于「进源之后」，不再糊封面。
    return NsfwMaskScope(
      mask: false,
      child: ComicList(
        key: Key(query +
            options.toString() +
            sourceKey +
            selectedCategories.toString()),
        errorLeading: AppSearchBar(
          controller: controller,
          action: buildAction(),
        ),
        leadingSliver: SliverMainAxisGroup(
          slivers: [
            SliverSearchBar(
              controller: controller,
              onChanged: onChanged,
              action: Row(
                mainAxisSize: MainAxisSize.min,
                children: [
                  const ComicLayoutToggleButton(),
                  buildAction(),
                ],
              ),
            ),
            // 标签 chips 行（含「＋ Add」入口）紧跟搜索框下方。
            buildTagBar(),
          ],
        ),
        loadPage: source!.searchPageData!.loadPage == null
            ? null
            : (i) {
                final loader = _categoryLoader;
                if (loader != null) {
                  return loader.serve();
                }
                return wrapSearchPage(
                  sourceKey: sourceKey,
                  keyword: query,
                  page: i,
                  options: options,
                  loadPage: source.searchPageData!.loadPage!,
                );
              },
        loadNext: source.searchPageData!.loadNext == null
            ? null
            : (i) {
                final loader = _categoryLoader;
                if (loader != null) {
                  return loader.serve();
                }
                return wrapSearchNext(
                  sourceKey: sourceKey,
                  keyword: query,
                  next: i,
                  options: options,
                  loadNext: source.searchPageData!.loadNext!,
                );
              },
      ),
    );
  }

  /// ⊕：打开「添加标签」抽屉；chips 变更即重建列表（ComicList 的 key 含 chips）。
  Future<void> _openAddTagSheet() async {
    final label = (await showAddTagSheet(context))?.trim() ?? '';
    if (label.isEmpty || tagList.contains(label)) return;
    setState(() {
      tagList.add(label);
      // 过滤条件变了，旧的连续抓页加载器持有旧条件，不能复用。
      _categoryLoader = null;
    });
  }

  /// 删除第 [index] 个标签（chip 上的 × 触发，退场动画播完后回调）。
  void _removeTag(int index) {
    if (index < 0 || index >= tagList.length) return;
    setState(() {
      tagList.removeAt(index);
      _categoryLoader = null;
    });
  }

  /// 标签 chips 行（含「＋ Add」入口），跟在搜索框正下方。
  Widget buildTagBar() {
    return SliverToBoxAdapter(
      child: SearchTagBar(
        labels: tagList,
        onRemove: _removeTag,
        onAdd: _openAddTagSheet,
      ),
    );
  }

  Widget buildAction() {
    return Tooltip(
      message: "Settings".tl,
      child: IconButton(
        icon: const Icon(Icons.tune),
        onPressed: () async {
          if (suggestionOverlay != null) {
            suggestionsController.remove();
          }

          var previousOptions = List<String>.from(options);
          var previousSourceKey = sourceKey;
          var previousCategories = List<String>.from(selectedCategories);
          final result = await showDialog<SearchSettingsResult>(
            context: context,
            useRootNavigator: true,
            builder: (context) => SearchSettingsDialog(
              initialSourceKey: sourceKey,
              initialOptions: options,
              initialCategories: selectedCategories,
            ),
          );
          if (result == null) return;
          if (!previousOptions.isEqualTo(result.options) ||
              previousSourceKey != result.sourceKey ||
              !previousCategories.isEqualTo(result.categories)) {
            sourceKey = result.sourceKey;
            options = result.options;
            selectedCategories = result.categories;
            // 过滤条件变了，旧的连续抓页加载器持有旧选择，不能复用。
            _categoryLoader = null;
            text = checkAutoLanguage(controller.text);
            controller.currentText = text;
            setState(() {});
          }
        },
      ),
    );
  }
}

class _SuggestionsController {
  _SuggestionsState? _state;

  final SearchBarController controller;

  final String sourceKey;

  OverlayEntry? entry;

  void updateWidget() {
    _state?.update();
  }

  void remove() {
    entry?.remove();
    entry = null;
  }

  var suggestions = <Pair<String, TranslationType>>[];

  void findSuggestions() {
    var text = controller.text.split(" ").last;
    var suggestions = this.suggestions;

    suggestions.clear();

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
        if (suggestions.length > 200) {
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
  }

  _SuggestionsController(this.controller, this.sourceKey);
}

class _Suggestions extends StatefulWidget {
  const _Suggestions({required this.controller});

  final _SuggestionsController controller;

  @override
  State<_Suggestions> createState() => _SuggestionsState();
}

class _SuggestionsState extends State<_Suggestions> {
  void update() {
    setState(() {});
  }

  @override
  void initState() {
    widget.controller._state = this;
    super.initState();
  }

  @override
  void didUpdateWidget(covariant _Suggestions oldWidget) {
    if (oldWidget.controller != widget.controller) {
      oldWidget.controller._state = null;
      widget.controller._state = this;
    }
    super.didUpdateWidget(oldWidget);
  }

  @override
  Widget build(BuildContext context) {
    return buildSuggestions(context);
  }

  Widget buildSuggestions(BuildContext context) {
    bool showMethod = MediaQuery.of(context).size.width < 600;
    bool showTranslation = App.locale.languageCode == "zh";

    Widget buildItem(Pair<String, TranslationType> value) {
      var subTitle = TagsTranslation.translationTagWithNamespace(
          value.left, value.right.name);
      return ListTile(
        title: Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            Expanded(
              child: Text(
                value.left,
                maxLines: 2,
              ),
            ),
            if (!showMethod)
              const SizedBox(
                width: 12,
              ),
            if (!showMethod && showTranslation)
              Text(
                subTitle,
                style: TextStyle(
                    fontSize: 14, color: Theme.of(context).colorScheme.outline),
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

    return Column(
      children: [
        ListTile(
          leading: const Icon(Icons.hub_outlined),
          title: Text("Suggestions".tl),
          trailing: Tooltip(
            message: "Clear".tl,
            child: IconButton(
              icon: const Icon(Icons.clear_all),
              onPressed: () {
                widget.controller.suggestions.clear();
                widget.controller.remove();
              },
            ),
          ),
        ),
        Expanded(
          child: ListView.builder(
            padding: const EdgeInsets.symmetric(horizontal: 16),
            itemCount: widget.controller.suggestions.length,
            itemBuilder: (context, index) =>
                buildItem(widget.controller.suggestions[index]),
          ),
        )
      ],
    );
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

  void onSelected(String text, TranslationType? type) {
    var controller = widget.controller.controller;
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
    final source = ComicSource.find(widget.controller.sourceKey);
    String insert;
    if (source?.onTagSuggestionSelected != null) {
      insert = source!.onTagSuggestionSelected!(type?.name ?? '', text);
    } else {
      var t = text;
      if (t.contains(' ')) t = "'$t'";
      insert = type != null ? "${type.name}:$t" : t;
    }
    controller.text += "$insert ";
    widget.controller.suggestions.clear();
    widget.controller.remove();
  }
}
