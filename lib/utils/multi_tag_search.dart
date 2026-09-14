/// 多 tag 搜索：搜索关键词支持若干 `tag:xxx` 词（AND 语义），其余为普通文本。
///
/// 按源分两路：
/// - **原生标签语法源**（[nativeTagSearchSources]）：tag 词反查翻译库后改写成
///   各源原生语法（如 `female:lolicon` / `tag:Romance`）透传，保留站方精度；
/// - **其余源**：剥离 tag 词、用剩余文本调源搜索，结果在 Dart 侧按标签宽松
///   过滤（[filterByTags]）——服务器不支持标签搜索的源也能用同一语法。
///
/// 例：`tag:萝莉 tag:校服 妹妹` = 同时带「萝莉」「校服」标签、标题含「妹妹」。
library;

import 'dart:math' as math;

import 'package:venera/foundation/comic_source/comic_source.dart';
import 'package:venera/foundation/res.dart';
import 'package:venera/utils/opencc.dart';
import 'package:venera/utils/tags_translation.dart';

/// 这些源的 search 原生支持标签语法（关键词透传 + 语法改写）。
const nativeTagSearchSources = {'ehentai', 'nhentai', 'hitomi', 'manga_dex'};

/// 这些源的**列表项把站方分区/分类也塞进了 `tags`**（见各源 JS 的 parseComic，
/// 如 picacg.js 里 `tags.push(...(comic.categories ?? []))`），因此可以在 Dart 侧
/// 按分类过滤结果，不需要站方支持分类参数。
///
/// ⚠️ 其余源虽然也有分类页（`category` 配置），但列表项不含分类信息，
/// 客户端过滤只会把整页清空，所以不要放开这个白名单。
const categoryFilterSources = {'picacg'};

/// 分类页里的「专题」入口（排行榜式聚合），不是作品分区：站方不会把
/// 这些值写进作品分类里，选它们只会把结果过滤空，因此从可选列表中剔除。
const _nonCategorySpecials = {
  '大家都在看',
  '大濕推薦',
  '那年今天',
  '官方都在看',
};

/// 取某源可用于客户端过滤的分类名。
///
/// 数据来源是该源的 category 页配置（[ComicSource.categoryData]）里的固定部分，
/// 分类值优先取跳转目标上的 `category` 属性（站方真正识别的取值），退回显示名。
/// 非 [categoryFilterSources] 或没有分类数据时返回空列表。
List<String> clientFilterCategories(String sourceKey) {
  if (!categoryFilterSources.contains(sourceKey)) {
    return const [];
  }
  final parts = ComicSource.find(sourceKey)?.categoryData?.categories;
  if (parts == null) {
    return const [];
  }
  final result = <String>[];
  for (final part in parts) {
    if (part is! FixedCategoryPart) {
      continue;
    }
    for (final item in part.categories) {
      final value = item.target.attributes?["category"] as String? ?? item.label;
      if (value.isEmpty || _nonCategorySpecials.contains(value)) {
        continue;
      }
      if (!result.contains(value)) {
        result.add(value);
      }
    }
  }
  return result;
}

/// 解析结果：tag 词列表（AND）+ 剩余文本。
class TagQuery {
  const TagQuery(this.tags, this.text);

  final List<String> tags;

  final String text;

  /// 解析关键词。tag 词支持 `tag:xxx` 与含空格的 `tag:"a b"` 两种写法。
  factory TagQuery.parse(String keyword) {
    final tags = <String>[];
    final words = <String>[];
    final token = RegExp(r'tag:"([^"]+)"|tag:(\S+)|(\S+)', caseSensitive: false);
    for (final match in token.allMatches(keyword)) {
      final quoted = match.group(1);
      final simple = match.group(2);
      final word = match.group(3);
      if (quoted != null) {
        final t = quoted.trim();
        if (t.isNotEmpty) {
          tags.add(t);
        }
      } else if (simple != null) {
        final t = simple.trim();
        if (t.isNotEmpty) {
          tags.add(t);
        }
      } else if (word != null && word.trim().isNotEmpty) {
        words.add(word.trim());
      }
    }
    return TagQuery(tags, words.join(' '));
  }
}

/// 把单个标签名编码成 `tag:` 查询词。
///
/// **所有「点击标签」类入口（详情页标签、标签云等）都必须走它。** 含空格的
/// 标签不加引号会被 [TagQuery.parse] 的 `tag:(\S+)` 拆成「tag=首词」+ 剩余
/// 文本，过滤条件静默失真——e-hentai / nhentai 的标签大量含空格
/// （`big breasts`、`sole female`），这是最容易踩的一处。
String tagQueryOf(String tag) {
  // 引号是语法字符，标签名里不该出现（站方标签也不含），去掉避免破坏解析。
  final t = tag.trim().replaceAll('"', '');
  if (t.isEmpty) {
    return '';
  }
  return t.contains(' ') ? 'tag:"$t"' : 'tag:$t';
}

/// 把文本与若干 tag 组合回搜索关键词（含空格的 tag 用引号包裹）。
String composeTagQuery({required String text, required List<String> tags}) {
  final parts = <String>[];
  if (text.trim().isNotEmpty) {
    parts.add(text.trim());
  }
  for (final tag in tags) {
    final part = tagQueryOf(tag);
    if (part.isNotEmpty) {
      parts.add(part);
    }
  }
  return parts.join(' ');
}

/// 规范化一个标签值：去 namespace 前缀、小写、繁转简。
String normalizeTagValue(String tag) {
  var v = tag.trim().toLowerCase();
  final idx = v.indexOf(':');
  if (idx >= 0 && idx < v.length - 1) {
    v = v.substring(idx + 1);
  }
  return OpenCC.traditionalToSimplified(v);
}

Map<String, (String, String)>? _reverseDict;

/// 反向翻译字典：规范化后的**中文值或英文值** → (namespace, 英文 key)。
/// 由 TagsTranslation 的各分库遍历构建（数据启动时已无条件加载）。
///
/// 两种键都要建：搜索框里用户输的多是中文（`萝莉`），而**详情页点击标签**
/// 拿到的是站方的裸值——原生源（e-hentai 等）那里是英文（`big breasts`，
/// 见 venera-configs 的 ehentai.js 详情页 tags 构造）。少了英文键，原生源
/// 的站方语法转换会静默失败、退化成普通文本搜索。
Map<String, (String, String)> _buildReverseDict() {
  final dict = <String, (String, String)>{};
  void addAll(String namespace, Map<String, String> tags) {
    // 中文键优先（同义冲突时以中文查得为准，行为与本次改动前一致）。
    tags.forEach((en, zh) {
      dict.putIfAbsent(normalizeTagValue(zh), () => (namespace, en));
    });
    // 英文键兜底（putIfAbsent：不覆盖上一步已插入的中文键）。
    tags.forEach((en, zh) {
      dict.putIfAbsent(normalizeTagValue(en), () => (namespace, en));
    });
  }

  addAll('female', TagsTranslation.femaleTags);
  addAll('male', TagsTranslation.maleTags);
  addAll('mixed', TagsTranslation.mixedTags);
  addAll('other', TagsTranslation.otherTags);
  addAll('parody', TagsTranslation.parodyTags);
  addAll('character', TagsTranslation.characterTranslations);
  addAll('artist', TagsTranslation.artistTags);
  addAll('group', TagsTranslation.groupTags);
  addAll('language', TagsTranslation.languageTranslations);
  addAll('cosplayer', TagsTranslation.cosplayerTags);
  addAll('reclass', TagsTranslation.reclassTags);
  return dict;
}

/// 中文标签 → 原生 "namespace:english"（如「萝莉」→ "female:lolicon"）。
/// 查不到返回 null（调用方降级为普通文本词）。
String? reverseTagToNative(String tag) {
  _reverseDict ??= _buildReverseDict();
  final hit = _reverseDict![normalizeTagValue(tag)];
  if (hit == null) {
    return null;
  }
  final (namespace, english) = hit;
  // ehentai 系语法：含空格的 tag 要加引号。
  return english.contains(' ') ? "$namespace:'$english'" : "$namespace:$english";
}

/// 原生 tag 语法源的查询改写：tag 词反查翻译库后转 `namespace:english`
/// （manga_dex 用 `tag:english`），反查失败的降级为普通文本词。
String buildNativeTagQuery(String sourceKey, List<String> tags, String text) {
  final parts = <String>[];
  for (final tag in tags) {
    final native = reverseTagToNative(tag);
    if (native == null) {
      parts.add(tag);
    } else if (sourceKey == 'manga_dex') {
      // manga_dex 的 JS 按**空格**切分关键词再查内置映射表（manga_dex.js
      // 的 search.load），所以多词标签必须用下划线连成**单个 token**——
      // 它自己的 onClickTag 就是 `tag.replaceAll(' ', '_')`。
      final en = native.split(':').last.replaceAll("'", '');
      parts.add('tag:${en.replaceAll(' ', '_')}');
    } else {
      parts.add(native);
    }
  }
  if (text.isNotEmpty) {
    parts.add(text);
  }
  return parts.join(' ');
}

/// 请求 tag 的候选规范化形式：自身 + 反查英文名。
Set<String> _tagCandidates(String tag) {
  final candidates = <String>{normalizeTagValue(tag)};
  final native = reverseTagToNative(tag);
  if (native != null) {
    candidates.add(normalizeTagValue(native));
  }
  return candidates.where((c) => c.length >= 2).toSet();
}

/// 宽松过滤：comic 的任一 tag 与请求 tag（的任一候选）双向包含即命中。
/// comic 没有 tags 信息时**宽松放行**——避免不填 tags 的源整页清空。
///
/// [categories] 是站方分区名（如哔咔的「全彩/長篇」），语义为 **AND + 归一化后精确
/// 匹配**：分区名是站方自己的枚举值，用包含匹配会误伤（`SM` 能命中别的 tag），
/// 而列表项带分类的源必然给出该字段，不需要宽松放行。
List<Comic> filterByTags(
  List<Comic> comics,
  List<String> tags, {
  List<String> categories = const [],
}) {
  if (tags.isEmpty && categories.isEmpty) {
    return comics;
  }
  final wanted = tags.map(_tagCandidates).toList();
  final wantedCategories = categories.map(normalizeTagValue).toSet();
  bool matches(Comic comic) {
    final comicTags = comic.tags;
    if (comicTags == null || comicTags.isEmpty) {
      return categories.isEmpty;
    }
    final normalized = comicTags.map(normalizeTagValue).toSet();
    if (!wantedCategories.every(normalized.contains)) {
      return false;
    }
    return wanted.every(
      (candidates) => normalized.any(
        (t) => candidates.any((c) => t.contains(c) || c.contains(t)),
      ),
    );
  }

  return comics.where(matches).toList();
}

/// 单源分页搜索的包装：解析 tag 词 → 原生源语法透传 / 其余源剥离后调搜索
/// 并做客户端过滤。纯 tag 无文本时先试空关键词（多数源返回最新/全部），
/// 失败回退用 tag 词拼接当文本。
Future<Res<List<Comic>>> wrapSearchPage({
  required String sourceKey,
  required String keyword,
  required int page,
  required List<String> options,
  required SearchFunction loadPage,
}) async {
  final q = TagQuery.parse(keyword);
  if (q.tags.isEmpty) {
    return loadPage(keyword, page, options);
  }
  if (nativeTagSearchSources.contains(sourceKey)) {
    return loadPage(buildNativeTagQuery(sourceKey, q.tags, q.text), page, options);
  }
  if (q.text.isEmpty) {
    final first = await loadPage('', page, options);
    if (!first.error) {
      return Res(filterByTags(first.data, q.tags), subData: first.subData);
    }
    final retry = await loadPage(q.tags.join(' '), page, options);
    return Res(filterByTags(retry.data, q.tags), subData: retry.subData);
  }
  final res = await loadPage(q.text, page, options);
  if (res.error) {
    return res;
  }
  return Res(filterByTags(res.data, q.tags), subData: res.subData);
}

/// 游标式（loadNext）搜索的包装，语义同 [wrapSearchPage]。
Future<Res<List<Comic>>> wrapSearchNext({
  required String sourceKey,
  required String keyword,
  required String? next,
  required List<String> options,
  required SearchNextFunction loadNext,
}) async {
  final q = TagQuery.parse(keyword);
  if (q.tags.isEmpty) {
    return loadNext(keyword, next, options);
  }
  if (nativeTagSearchSources.contains(sourceKey)) {
    return loadNext(buildNativeTagQuery(sourceKey, q.tags, q.text), next, options);
  }
  if (q.text.isEmpty) {
    final first = await loadNext('', next, options);
    if (!first.error) {
      return Res(filterByTags(first.data, q.tags), subData: first.subData);
    }
    final retry = await loadNext(q.tags.join(' '), next, options);
    return Res(filterByTags(retry.data, q.tags), subData: retry.subData);
  }
  final res = await loadNext(q.text, next, options);
  if (res.error) {
    return res;
  }
  return Res(filterByTags(res.data, q.tags), subData: res.subData);
}

/// 客户端过滤路的连续加载器：内部按源分页**顺序**抓取，每页全量过一遍标签
/// 过滤入缓冲；调用方每次 [serve] 取缓冲前 [emitSize] 条，缓冲不足时自动
/// 抓下一页，直到凑够一批或源分页耗尽。纯 tag 无文本时先试空关键词，失败
/// 回退用 tag 词拼接当文本（如 picacg 空关键词会报错的源）。
///
/// ComicList 的两种分页模型都能用：paged 源 subData 返回下一内部页码、
/// cursor 源 subData 返回非 null 游标（耗尽时返回空数据 / null 结束列表）。
/// 调用方请求的 page/next 参数被忽略——内部游标单调递增，天然无重复。
class TagFilteredLoader {
  TagFilteredLoader({
    required this.tags,
    required this.queryText,
    required this.options,
    required this.loadPage,
    this.categories = const [],
    this.loadNext,
    this.emitSize = 20,
    this.maxFetch = 8,
  });

  final List<String> tags;

  /// 站方分区名（AND、精确匹配），与 [tags] 一起参与过滤，见 [filterByTags]。
  final List<String> categories;

  final String queryText;

  final List<String> options;

  final SearchFunction loadPage;

  final SearchNextFunction? loadNext;

  /// 每次调用最多返回的过滤后条数。
  final int emitSize;

  /// 单次 serve 最多额外抓取的源分页数（防失控）。
  final int maxFetch;

  final _buffer = <Comic>[];

  int _fetched = 0;

  int _nextFetchPage = 1;

  String? _nextCursor;

  int? _maxPage;

  bool _exhausted = false;

  String? _error;

  String? _textOverride;

  bool get _useCursor => loadNext != null;

  /// 缓冲里的剩余条数（调试/展示用）。
  int get bufferedCount => _buffer.length;

  /// 取下一批过滤后结果。列表耗尽时返回空列表（ComicList 据此结束）。
  Future<Res<List<Comic>>> serve() async {
    await _fill();
    final emitted = _buffer.take(emitSize).toList();
    _buffer.removeRange(0, math.min(emitted.length, _buffer.length));
    if (emitted.isEmpty && _error != null) {
      return Res.error(_error!);
    }
    if (_useCursor) {
      return Res(emitted, subData: _exhausted ? null : 'next');
    }
    return Res(emitted, subData: _exhausted ? null : _nextFetchPage);
  }

  Future<void> _fill() async {
    while (_buffer.length < emitSize && !_exhausted && _fetched < maxFetch) {
      final res = await _fetchRaw();
      _fetched += 1;
      if (res.error) {
        // 空关键词失败（部分源不接受空文本）：回退用 tag 词拼接当文本，重试一次。
        if (queryText.isEmpty && _textOverride == null) {
          _textOverride = tags.join(' ');
          continue;
        }
        _error = res.errorMessage;
        _exhausted = true;
        return;
      }
      _maxPage = res.subData is int ? res.subData as int : _maxPage;
      _buffer.addAll(filterByTags(res.data, tags, categories: categories));
      if (_useCursor) {
        final next = res.subData as String?;
        if (next == null) {
          _exhausted = true;
        } else {
          _nextCursor = next;
        }
      } else {
        if (_nextFetchPage >= (_maxPage ?? _nextFetchPage)) {
          _exhausted = true;
        } else {
          _nextFetchPage += 1;
        }
      }
    }
  }

  Future<Res<List<Comic>>> _fetchRaw() {
    final text = _textOverride ?? queryText;
    if (_useCursor) {
      return loadNext!(text, _nextCursor, options);
    }
    return loadPage(text, _nextFetchPage, options);
  }
}
