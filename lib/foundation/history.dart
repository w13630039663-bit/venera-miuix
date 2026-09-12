import 'dart:async';
import 'dart:convert';
import 'dart:isolate';
import 'dart:ffi' as ffi;

import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';
import 'package:flutter/widgets.dart' show ChangeNotifier;
import 'package:sqlite3/sqlite3.dart';
import 'package:venera/foundation/comic_source/comic_source.dart';
import 'package:venera/foundation/comic_type.dart';
import 'package:venera/foundation/favorites.dart';
import 'package:venera/foundation/image_provider/image_favorites_provider.dart';
import 'package:venera/foundation/log.dart';
import 'package:venera/utils/channel.dart';
import 'package:venera/utils/ext.dart';
import 'package:venera/utils/translations.dart';

import 'app.dart';
import 'consts.dart';

part "image_favorites.dart";

typedef HistoryType = ComicType;

abstract mixin class HistoryMixin {
  String get title;

  String? get subTitle;

  String get cover;

  String get id;

  int? get maxPage => null;

  HistoryType get historyType;
}

class History implements Comic {
  HistoryType type;

  DateTime time;

  @override
  String title;

  @override
  String subtitle;

  @override
  String cover;

  /// index of chapters. 1-based.
  int ep;

  /// index of pages. 1-based.
  int page;

  /// index of chapter groups. 1-based.
  /// If [group] is not null, [ep] is the index of chapter in the group.
  int? group;

  @override
  String id;

  /// readEpisode is a set of episode numbers that have been read.
  /// For normal chapters, it is a set of chapter numbers.
  /// For grouped chapters, it is a set of strings in the format of "group_number-chapter_number".
  /// 1-based.
  Set<String> readEpisode;

  @override
  int? maxPage;

  History.fromModel(
      {required HistoryMixin model,
      required this.ep,
      required this.page,
      this.group,
      Set<String>? readChapters,
      DateTime? time})
      : type = model.historyType,
        title = model.title,
        subtitle = model.subTitle ?? '',
        cover = model.cover,
        id = model.id,
        readEpisode = readChapters ?? <String>{},
        time = time ?? DateTime.now();

  History.fromMap(Map<String, dynamic> map)
      : type = HistoryType(map["type"]),
        time = DateTime.fromMillisecondsSinceEpoch(map["time"]),
        title = map["title"],
        subtitle = map["subtitle"],
        cover = map["cover"],
        ep = map["ep"],
        page = map["page"],
        id = map["id"],
        readEpisode = Set<String>.from(
            (map["readEpisode"] as List<dynamic>?)?.toSet() ??
                const <String>{}),
        maxPage = map["max_page"];

  @override
  String toString() {
    return 'History{type: $type, time: $time, title: $title, subtitle: $subtitle, cover: $cover, ep: $ep, page: $page, id: $id}';
  }

  History.fromRow(Row row)
      : type = HistoryType(row["type"]),
        time = DateTime.fromMillisecondsSinceEpoch(row["time"]),
        title = row["title"],
        subtitle = row["subtitle"],
        cover = row["cover"],
        ep = row["ep"],
        page = row["page"],
        id = row["id"],
        readEpisode = Set<String>.from((row["readEpisode"] as String)
            .split(',')
            .where((element) => element != "")),
        maxPage = row["max_page"],
        group = row["chapter_group"];

  @override
  bool operator ==(Object other) {
    return other is History && type == other.type && id == other.id;
  }

  @override
  int get hashCode => Object.hash(id, type);

  @override
  String get description {
    var res = "";
    if (group != null){
      res += "${"Group @group".tlParams({
        "group": group!,
      })} - ";
    }
    if (ep >= 1) {
      res += "Chapter @ep".tlParams({
        "ep": ep,
      });
    }
    if (page >= 1) {
      if (ep >= 1) {
        res += " - ";
      }
      res += "Page @page".tlParams({
        "page": page,
      });
    }
    return res;
  }

  @override
  String? get favoriteId => null;

  @override
  String? get language => null;

  @override
  String get sourceKey => type == ComicType.local
      ? 'local'
      : type.comicSource?.key ?? "Unknown:${type.value}";

  @override
  double? get stars => null;

  @override
  List<String>? get tags => null;

  @override
  Map<String, dynamic> toJson() {
    throw UnimplementedError();
  }
}

class HistoryManager with ChangeNotifier {
  static HistoryManager? cache;

  HistoryManager.create();

  factory HistoryManager() =>
      cache == null ? (cache = HistoryManager.create()) : cache!;

  late Database _db;

  int get length => _db.select("select count(*) from history;").first[0] as int;

  /// Cache of history ids. Improve the performance of find operation.
  Map<String, bool>? _cachedHistoryIds;

  /// Cache records recently modified by the app. Improve the performance of listeners.
  final cachedHistories = <String, History>{};

  bool isInitialized = false;

  Future<void> init() async {
    if (isInitialized) {
      return;
    }
    _db = sqlite3.open("${App.dataPath}/history.db");

    _db.execute("""
        create table if not exists history  (
          id text primary key,
          title text,
          subtitle text,
          cover text,
          time int,
          type int,
          ep int,
          page int,
          readEpisode text,
          max_page int,
          chapter_group int
        );
      """);

    var columns = _db.select("PRAGMA table_info(history);");
    if (!columns.any((element) => element["name"] == "chapter_group")) {
      _db.execute("alter table history add column chapter_group int;");
    }

    // 阅读统计：按 (日期, 漫画) 聚合的阅读页数。history 表只有「当前进度」，
    // 推不出每天的阅读增量，统计页靠这张表。tags 存 JSON 数组（plainTags
    // 的 "namespace:tag"），供题材分布/标签云聚合（原始写法入库，归一化在
    // 展示侧做——翻译库将来更新可重算）。
    _db.execute("""
        create table if not exists read_stats (
          date text not null,
          cid text not null,
          type int not null,
          pages int not null,
          tags text,
          primary key (date, cid, type)
        );
      """);

    // 老库迁移：补 tags 列。
    var readStatsColumns = _db.select("PRAGMA table_info(read_stats);");
    if (!readStatsColumns.any((element) => element["name"] == "tags")) {
      _db.execute("alter table read_stats add column tags text;");
    }

    notifyListeners();
    ImageFavoriteManager().init();
    isInitialized = true;
  }

  static const _insertHistorySql = """
        insert or replace into history (id, title, subtitle, cover, time, type, ep, page, readEpisode, max_page, chapter_group)
        values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?);
      """;

  static Future<void> _addHistoryAsync(int dbAddr, History newItem) {
    return Isolate.run(() {
      var db = sqlite3.fromPointer(ffi.Pointer.fromAddress(dbAddr));
      db.execute(_insertHistorySql, [
        newItem.id,
        newItem.title,
        newItem.subtitle,
        newItem.cover,
        newItem.time.millisecondsSinceEpoch,
        newItem.type.value,
        newItem.ep,
        newItem.page,
        newItem.readEpisode.join(','),
        newItem.maxPage,
        newItem.group
      ]);
    });
  }

  bool _haveAsyncTask = false;

  /// Create a isolate to add history to prevent blocking the UI thread.
  Future<void> addHistoryAsync(History newItem) async {
    while (_haveAsyncTask) {
      await Future.delayed(Duration(milliseconds: 20));
    }

    _haveAsyncTask = true;
    await _addHistoryAsync(_db.handle.address, newItem);
    _haveAsyncTask = false;
    if (_cachedHistoryIds == null) {
      updateCache();
    } else {
      _cachedHistoryIds![newItem.id] = true;
    }
    cachedHistories[newItem.id] = newItem;
    if (cachedHistories.length > 10) {
      cachedHistories.remove(cachedHistories.keys.first);
    }
    notifyListeners();
  }

  /// add history. if exists, update time.
  ///
  /// This function would be called when user start reading.
  void addHistory(History newItem) {
    _db.execute(_insertHistorySql, [
      newItem.id,
      newItem.title,
      newItem.subtitle,
      newItem.cover,
      newItem.time.millisecondsSinceEpoch,
      newItem.type.value,
      newItem.ep,
      newItem.page,
      newItem.readEpisode.join(','),
      newItem.maxPage,
      newItem.group
    ]);
    if (_cachedHistoryIds == null) {
      updateCache();
    } else {
      _cachedHistoryIds![newItem.id] = true;
    }
    cachedHistories[newItem.id] = newItem;
    if (cachedHistories.length > 10) {
      cachedHistories.remove(cachedHistories.keys.first);
    }
    notifyListeners();
  }

  void clearHistory() {
    _db.execute("delete from history;");
    updateCache();
    notifyListeners();
  }

void clearUnfavoritedHistory() {
  _db.execute('BEGIN TRANSACTION;');
  try {
    final idAndTypes = _db.select("""
      select id, type from history;
    """);
    for (var element in idAndTypes) {
      final id = element["id"] as String;
      final type = ComicType(element["type"] as int);
      if (!LocalFavoritesManager().isExist(id, type)) {
        _db.execute("""
          delete from history
          where id == ? and type == ?;
        """, [id, type.value]);
      }
    }
    _db.execute('COMMIT;');
  } catch (e) {
    _db.execute('ROLLBACK;');
    rethrow;
  }
  updateCache();
  notifyListeners();
}

  void remove(String id, ComicType type) async {
    _db.execute("""
      delete from history
      where id == ? and type == ?;
    """, [id, type.value]);
    updateCache();
    notifyListeners();
  }

  void updateCache() {
    _cachedHistoryIds = {};
    var res = _db.select("""
        select id from history;
      """);
    for (var element in res) {
      _cachedHistoryIds![element["id"] as String] = true;
    }
    for (var key in cachedHistories.keys.toList()) {
      if (!_cachedHistoryIds!.containsKey(key)) {
        cachedHistories.remove(key);
      }
    }
  }

  History? find(String id, ComicType type) {
    if (_cachedHistoryIds == null) {
      updateCache();
    }
    if (!_cachedHistoryIds!.containsKey(id)) {
      return null;
    }
    if (cachedHistories.containsKey(id)) {
      return cachedHistories[id];
    }

    var res = _db.select("""
      select * from history
      where id == ? and type == ?;
    """, [id, type.value]);
    if (res.isEmpty) {
      return null;
    }
    return History.fromRow(res.first);
  }

  List<History> getAll() {
    var res = _db.select("""
      select * from history
      order by time DESC;
    """);
    return res.map((element) => History.fromRow(element)).toList();
  }

  /// 获取最近阅读的漫画
  List<History> getRecent() {
    var res = _db.select("""
      select * from history
      order by time DESC
      limit 20;
    """);
    return res.map((element) => History.fromRow(element)).toList();
  }

  /// 获取历史记录的数量
  int count() {
    var res = _db.select("""
      select count(*) from history;
    """);
    return res.first[0] as int;
  }

  /// 阅读统计：把一次阅读会话读过的页数累加到当天（按 日期+漫画 聚合）。
  /// [tags] 为该漫画的 plainTags（"namespace:tag" 列表），题材分布用；
  /// upsert 时已有行的 tags 不被空值覆盖。
  void addReadingStats({
    required ComicType type,
    required String cid,
    required int pages,
    List<String>? tags,
  }) {
    if (pages <= 0) return;
    _db.execute(
      "insert into read_stats (date, cid, type, pages, tags) values (?, ?, ?, ?, ?) "
      "on conflict(date, cid, type) do update set pages = pages + excluded.pages, "
      "tags = coalesce(excluded.tags, tags);",
      [
        _formatDate(DateTime.now()),
        cid,
        type.value,
        pages,
        tags == null || tags.isEmpty ? null : jsonEncode(tags),
      ],
    );
  }

  /// 有标签记录的阅读行（date/pages/tags），供题材分布/标签云/时间轴聚合。
  List<(String, int, String)> readTagRows(int days) {
    final rows = _db.select(
      "select date, pages, tags from read_stats where date >= ? and tags is not null;",
      [_formatDate(DateTime.now().subtract(Duration(days: days - 1)))],
    );
    return [
      for (final row in rows) (row[0] as String, row[1] as int, row[2] as String),
    ];
  }

  static String _formatDate(DateTime d) =>
      "${d.year.toString().padLeft(4, '0')}-"
      "${d.month.toString().padLeft(2, '0')}-${d.day.toString().padLeft(2, '0')}";

  /// 最近 [days] 天（含今天）每天的阅读页数，key = 'yyyy-MM-dd'，缺的天补 0。
  Map<String, int> dailyPages(int days) {
    final now = DateTime.now();
    final result = <String, int>{
      for (var i = 0; i < days; i++) _formatDate(now.subtract(Duration(days: i))): 0,
    };
    final rows = _db.select(
      "select date, sum(pages) from read_stats where date >= ? group by date;",
      [_formatDate(now.subtract(Duration(days: days - 1)))],
    );
    for (final row in rows) {
      final key = row[0] as String;
      if (result.containsKey(key)) {
        result[key] = row[1] as int? ?? 0;
      }
    }
    return result;
  }

  /// 自 [start]（含当天）以来的累计页数。
  int pagesSince(DateTime start) {
    final res = _db.select(
      "select sum(pages) from read_stats where date >= ?;",
      [_formatDate(start)],
    );
    return res.first[0] as int? ?? 0;
  }

  /// 有阅读记录的日期集合（连续天数用）。
  Set<String> readDates() => {
    for (final row in _db.select("select distinct date from read_stats;"))
      row[0] as String,
  };

  /// 最近 [days] 天最常读的漫画（title/cover 从 history 表补全）。
  List<StatsTopComic> topComics(int days, int limit) {
    final rows = _db.select(
      """
      select s.cid, s.type, sum(s.pages) as pages, h.title, h.cover
      from read_stats s left join history h on h.id = s.cid and h.type = s.type
      where s.date >= ?
      group by s.cid, s.type
      order by pages desc
      limit ?;
      """,
      [_formatDate(DateTime.now().subtract(Duration(days: days - 1))), limit],
    );
    return [
      for (final row in rows)
        StatsTopComic(
          cid: row[0] as String,
          type: ComicType(row[1] as int),
          pages: row[2] as int? ?? 0,
          title: (row[3] as String?) ?? (row[0] as String),
          cover: row[4] as String?,
        ),
    ];
  }

  /// 最近 [days] 天按来源统计**读过的漫画本数**（去重 cid），降序。
  List<(int, int)> comicsByType(int days) {
    final rows = _db.select(
      "select type, count(distinct cid) from read_stats where date >= ? group by type order by count(distinct cid) desc;",
      [_formatDate(DateTime.now().subtract(Duration(days: days - 1)))],
    );
    return [for (final row in rows) (row[0] as int, row[1] as int)];
  }

  void close() {
    isInitialized = false;
    _db.dispose();
  }

  void batchDeleteHistories(List<ComicID> histories) {
    if (histories.isEmpty) return;
    _db.execute('BEGIN TRANSACTION;');
    try {
      for (var history in histories) {
        _db.execute("""
          delete from history
          where id == ? and type == ?;
        """, [history.id, history.type.value]);
      }
      _db.execute('COMMIT;');
    } catch (e) {
      _db.execute('ROLLBACK;');
      rethrow;
    }
    updateCache();
    notifyListeners();
  }

  /// Refresh history info from comic source.
  /// Fetches the latest cover, title and subtitle from the source.
  /// Keeps the reading progress (ep, page, etc.).
  Future<bool> refreshHistoryInfo(History history) async {
    if (history.sourceKey == 'local') {
      // Local comics don't need refresh
      return false;
    }

    return await _refreshSingleHistory(history);
  }

  /// Internal method to refresh a single history
  /// Retries up to 3 times on failure with 2 second delay between retries
  Future<bool> _refreshSingleHistory(History history) async {
    var comicSource = ComicSource.find(history.sourceKey);
    if (comicSource == null || comicSource.loadComicInfo == null) {
      return false;
    }

    int retries = 3;
    while (true) {
      try {
        var res = await comicSource.loadComicInfo!(history.id);
        if (res.error) {
          await Future.delayed(const Duration(seconds: 2));
          retries--;
          if (retries == 0) {
            return false;
          }
          continue;
        }

        var comicDetails = res.data;
        // Update history info while keeping reading progress
        var updatedHistory = History.fromMap({
          'type': history.type.value,
          'time': history.time.millisecondsSinceEpoch,
          'title': comicDetails.title,
          'subtitle': comicDetails.subTitle ?? '',
          'cover': comicDetails.cover,
          'ep': history.ep,
          'page': history.page,
          'id': history.id,
          'readEpisode': history.readEpisode.toList(),
          'max_page': history.maxPage,
        });
        updatedHistory.group = history.group;

        addHistory(updatedHistory);
        return true;
      } catch (e, s) {
        Log.error("History", "Exception while refreshing history info: $e\n$s");
        await Future.delayed(const Duration(seconds: 2));
        retries--;
        if (retries == 0) {
          return false;
        }
      }
    }
  }

  /// Refresh all histories from comic sources.
  /// Returns a stream with progress updates.
  /// From e0ea449c.
  Stream<RefreshProgress> refreshAllHistoriesStream() {
    var controller = StreamController<RefreshProgress>();
    _refreshAllHistoriesBase(controller);
    return controller.stream;
  }

  void _refreshAllHistoriesBase(
    StreamController<RefreshProgress> controller,
  ) async {
    var histories = getAll();
    int total = histories.length;
    int current = 0;
    int success = 0;
    int failed = 0;
    int skipped = 0;

    controller.add(RefreshProgress(total, current, success, failed, skipped));

    var historiesToRefresh = <History>[];
    for (var history in histories) {
      if (history.sourceKey == 'local') {
        skipped++;
        current++;
        controller.add(RefreshProgress(total, current, success, failed, skipped));
        continue;
      }
      historiesToRefresh.add(history);
    }

    total = historiesToRefresh.length;
    current = 0;
    controller.add(RefreshProgress(total, current, success, failed, skipped));

    var channel = Channel<History>(10);

    () async {
      var c = 0;
      for (var history in historiesToRefresh) {
        await channel.push(history);
        c++;
        if (c % 5 == 0) {
          var delay = c % 100 + 1;
          if (delay > 10) {
            delay = 10;
          }
          await Future.delayed(Duration(seconds: delay));
        }
      }
      channel.close();
    }();

    var updateFutures = <Future>[];
    for (var i = 0; i < 5; i++) {
      var f = () async {
        while (true) {
          var history = await channel.pop();
          if (history == null) {
            break;
          }
          var result = await _refreshSingleHistory(history);
          current++;
          if (result) {
            success++;
          } else {
            failed++;
          }
          controller.add(
            RefreshProgress(total, current, success, failed, skipped),
          );
        }
      }();
      updateFutures.add(f);
    }

    await Future.wait(updateFutures);

    notifyListeners();
    controller.close();
  }
}

class RefreshProgress {
  final int total;
  final int current;
  final int success;
  final int failed;
  final int skipped;

  RefreshProgress(
    this.total,
    this.current,
    this.success,
    this.failed,
    this.skipped,
  );
}

/// 阅读统计里的「最常读漫画」条目。
class StatsTopComic {
  StatsTopComic({
    required this.cid,
    required this.type,
    required this.pages,
    required this.title,
    this.cover,
  });

  final String cid;

  final ComicType type;

  final int pages;

  final String title;

  final String? cover;
}
