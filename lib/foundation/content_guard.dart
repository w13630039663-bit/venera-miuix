import 'dart:collection';
import 'dart:convert';

import 'package:flutter/services.dart';
import 'package:venera/foundation/appdata.dart';
import 'package:venera/foundation/comic_source/comic_source.dart';
import 'package:venera/foundation/log.dart';

/// 遮蔽强度。用单一字符串存进 settings（JSON 友好），这里做一层枚举包装。
enum NsfwMaskStrength {
  /// 不遮蔽（总开关关闭）
  off,

  /// 只模糊封面，不可解锁
  blur,

  /// 模糊封面，点击后本次显示
  blurReveal,

  /// 整条从列表剔除
  hide;

  static NsfwMaskStrength parse(dynamic value) {
    return NsfwMaskStrength.values.firstWhere(
      (e) => e.name == value,
      orElse: () => NsfwMaskStrength.off,
    );
  }
}

/// 内容分级三态（对齐 keiyoushi 扩展仓库的 `contentWarning` 语义）。
///
/// - [safe]  整站无成人内容 → 默认不遮，只在命中显式关键词时遮单条
/// - [mixed] 成人站但确有非 H 内容（如 e-hentai 的 artbook）→ 默认遮，靠用户
///   豁免（点一下封面）和「永久放行此源」收敛
/// - [nsfw]  整站成人向 → 同上，语义上更明确
///
/// [mixed] 与 [nsfw] 在**判定行为上完全一致**（都遮），差别只在设置页的文案与
/// 用户的心理预期：看到「含非 H 内容」时用户更愿意去点豁免，而不是直接关总闸。
enum ContentLevel {
  safe,
  mixed,
  nsfw;

  static ContentLevel? parse(dynamic value) {
    if (value is! String) {
      return null;
    }
    for (final level in ContentLevel.values) {
      if (level.name == value.toLowerCase()) {
        return level;
      }
    }
    return null;
  }

  /// 是否需要遮蔽。注意 [mixed] 也算 —— 见类文档。
  bool get shouldMask => this != ContentLevel.safe;

  String get label => switch (this) {
    ContentLevel.safe => "Safe",
    ContentLevel.mixed => "Mixed",
    ContentLevel.nsfw => "Adult",
  };
}

/// 一条判定结果：**分级 + 为什么**。
///
/// `origin` 存在的唯一理由是「误判可撤销」：用户看到封面糊了，得能问出
/// "这条为什么被遮"。取值见 [ContentGuard] 的判定链。
class ContentVerdict {
  const ContentVerdict(this.level, this.origin);

  final ContentLevel level;

  /// user:item-unlock / user:item-flag / user:source / plugin /
  /// preset / preset:unverified / keyword / default
  final String origin;

  bool get shouldMask => level.shouldMask;

  static const safe = ContentVerdict(ContentLevel.safe, 'default');

  @override
  String toString() => "${level.name}($origin)";
}

/// 内容遮蔽（NSFW）判定。
///
/// ## 为什么判定要「源级优先 + 条目级兜底」而不是纯条目级
///
/// 聚合器和单一站点不同：33 个源里，**列表层能拿到有分级含义的 tag 的只有 8 个**
/// （其余是题材名、作者名、甚至日期和被正则漏出来的捕获组）。所以：
///
/// - **源级**决定基线（整站成人 → 全遮；普通站 → 不遮）。误判代价温和：
///   多糊一张封面，点一下就能看。
/// - **条目级**只在两个方向上有信号时才用：用户手工标注、以及标题/标签里
///   出现**显式**成人标记（`R-18` / `18禁` / `無修正` …）。判成"安全"当场暴露
///   且不可逆，所以关键词兜底只认显式命中，不启用中间档。
///
/// ## 判定顺序（命中即停）
///
/// 1. `unlockedComics` — 用户点过「显示」，永久豁免
/// 2. `forcedComics`   — 用户手动标记为敏感
/// 3. `sourceWarningOverride[sourceKey]` — 用户对这个源的覆盖（含「永久放行此源」）
/// 4. 插件声明的 `contentWarning`（向后兼容：老插件没有这个字段）
/// 5. 内置预设表 `assets/source_content_warning.json`
/// 6. 关键词兜底（仅显式命中）
/// 7. 默认 `safe`
///
/// 结果按 `sourceKey@id` 做 LRU 缓存（2000 条）。`Comic` 字段全 `final`，
/// 天然可缓存；但**设置一变就必须清缓存**，否则用户改完看不到效果。
class ContentGuard {
  ContentGuard._();

  static const presetAsset = 'assets/source_content_warning.json';

  /// 预设表：sourceKey → {level, reason, unverified?}
  static Map<String, Map<String, dynamic>> _preset = {};

  static bool _presetLoaded = false;

  /// LRU：`LinkedHashMap` 按插入序迭代，命中时先删后插即为「最近使用」。
  static final LinkedHashMap<String, ContentVerdict> _cache = LinkedHashMap();

  static const int _cacheLimit = 2000;

  /// 显式成人标记。**预编译**：判定在列表滚动时每帧都会跑，绝不能在
  /// build 里 `RegExp(...)`。
  static final List<RegExp> _explicitPatterns = [
    RegExp(r'r[\s\-_]?18', caseSensitive: false), // R18 / R-18 / R 18
    RegExp(r'18\s*[-_]?\s*禁'),
    RegExp(r'18\s*\+'),
    RegExp(r'成人向|成人漫画|成人漫畫|成人誌|成人志'),
    RegExp(r'無修正|无修正|無碼|无码'),
    RegExp(r'エロ'),
    RegExp(r'里番|裏番'),
    RegExp(r'hentai', caseSensitive: false),
    RegExp(r'\badult\b', caseSensitive: false),
    RegExp(r'\bnsfw\b', caseSensitive: false),
    RegExp(r'\bporn', caseSensitive: false),
  ];

  static NsfwMaskStrength get strength =>
      NsfwMaskStrength.parse(appdata.settings['nsfwMaskStrength']);

  /// 总开关：关闭时 [maskReason] 一律返回 null，行为与改动前完全一致。
  static bool get enabled => strength != NsfwMaskStrength.off;

  /// 覆盖表与屏蔽表的统一 key。
  ///
  /// 用显式的 `sourceKey@id` 而不是 `comic.toString()`：`Comic.toString()`
  /// 恰好也是这个格式，但 `FavoriteItem` 等子类各自覆写了 `toString`，
  /// 显式拼装才能保证"同一个作品在任何列表都是同一个 key"。
  static String keyOf(Comic comic) => "${comic.sourceKey}@${comic.id}";

  // ── 预设表 ──────────────────────────────────────────────────────────

  /// 载入内置预设表（启动时调用一次，失败不致命：退化为「全部 safe」）。
  static Future<void> init() async {
    if (_presetLoaded) {
      return;
    }
    try {
      final text = await rootBundle.loadString(presetAsset);
      final data = jsonDecode(text);
      final sources = data['sources'];
      if (sources is Map) {
        _preset = {
          for (final entry in sources.entries)
            entry.key.toString(): Map<String, dynamic>.from(entry.value as Map),
        };
      }
      _presetLoaded = true;
    } catch (e, s) {
      Log.error("ContentGuard", "load preset failed: $e\n$s");
    }
  }

  /// 该源在预设表里的条目（含 `reason` / `unverified`），供设置页展示。
  static Map<String, dynamic>? presetOf(String sourceKey) =>
      _preset[sourceKey];

  /// 预设表里所有源 key（设置页「源分级」列表用）。
  static List<String> get presetKeys => _preset.keys.toList();

  // ── 用户覆盖 ────────────────────────────────────────────────────────

  /// 用户对这个源的覆盖（null = 跟随插件/预设）。
  static ContentLevel? sourceOverride(String sourceKey) {
    final map = appdata.settings['sourceWarningOverride'];
    if (map is Map) {
      return ContentLevel.parse(map[sourceKey]);
    }
    return null;
  }

  /// 写入/清除源级覆盖。[level] 为 null 表示清除（恢复跟随预设）。
  static void setSourceOverride(String sourceKey, ContentLevel? level) {
    final raw = appdata.settings['sourceWarningOverride'];
    final map = raw is Map
        ? Map<String, dynamic>.from(raw)
        : <String, dynamic>{};
    if (level == null) {
      map.remove(sourceKey);
    } else {
      map[sourceKey] = level.name;
    }
    appdata.settings['sourceWarningOverride'] = map;
    invalidate();
    appdata.saveData();
  }

  // ── 判定 ────────────────────────────────────────────────────────────

  /// 该作品的判定结果（带缓存）。
  static ContentVerdict verdict(Comic comic) {
    if (!enabled) {
      return ContentVerdict.safe;
    }
    final key = keyOf(comic);
    final cached = _cache.remove(key);
    if (cached != null) {
      _cache[key] = cached;
      return cached;
    }
    final result = _judge(comic);
    _cache[key] = result;
    while (_cache.length > _cacheLimit) {
      _cache.remove(_cache.keys.first);
    }
    return result;
  }

  /// 该作品是否需要遮蔽。返回命中原因（`null` = 不遮），保留给 UI 直接展示。
  static String? maskReason(Comic comic) {
    final v = verdict(comic);
    return v.shouldMask ? v.origin : null;
  }

  static ContentVerdict _judge(Comic comic) {
    final key = keyOf(comic);
    if (_contains('unlockedComics', key)) {
      return const ContentVerdict(ContentLevel.safe, 'user:item-unlock');
    }
    if (_contains('forcedComics', key)) {
      return const ContentVerdict(ContentLevel.nsfw, 'user:item-flag');
    }
    final sourceVerdict = sourceVerdictOf(comic.sourceKey);
    if (sourceVerdict.shouldMask) {
      return sourceVerdict;
    }
    // 走到这里说明「源级判定为不遮」。只有此时才用条目级兜底：
    // 标题/副标题/简介/标签里出现显式成人标记 → 遮这一条。
    if (_hasExplicitMark(comic)) {
      return const ContentVerdict(ContentLevel.nsfw, 'keyword');
    }
    return sourceVerdict;
  }

  /// 单个**源**的有效分级，不含条目级兜底：用户覆盖 > 插件声明 > 预设表 > safe。
  ///
  /// 与 [verdict] 分开的理由：设置页要展示"这个源被判成了什么"，而那是源级的
  /// 概念 —— 拿某一条作品的判定去看源，会把关键词兜底的结果误当成源的性质。
  static ContentLevel sourceLevelOf(String sourceKey) =>
      sourceVerdictOf(sourceKey).level;

  /// 同 [sourceLevelOf]，但带 `origin`（设置页用它解释"凭什么"）。
  static ContentVerdict sourceVerdictOf(String sourceKey) {
    final override = sourceOverride(sourceKey);
    if (override != null) {
      return ContentVerdict(override, 'user:source');
    }
    final declared = ContentLevel.parse(
      ComicSource.find(sourceKey)?.contentWarning,
    );
    if (declared != null) {
      return ContentVerdict(declared, 'plugin');
    }
    final preset = _preset[sourceKey];
    if (preset != null) {
      final level = ContentLevel.parse(preset['level']) ?? ContentLevel.safe;
      return ContentVerdict(
        level,
        preset['unverified'] == true ? 'preset:unverified' : 'preset',
      );
    }
    return ContentVerdict.safe;
  }

  static bool _hasExplicitMark(Comic comic) {
    final fields = <String?>[
      comic.title,
      comic.subtitle,
      comic.description,
      ...?comic.tags,
    ];
    for (final field in fields) {
      if (field == null || field.isEmpty) {
        continue;
      }
      for (final pattern in _explicitPatterns) {
        if (pattern.hasMatch(field)) {
          return true;
        }
      }
    }
    return false;
  }

  /// 用户点过「显示」：写入豁免表，之后在任何列表都不再遮。
  static void unlock(Comic comic) {
    final key = keyOf(comic);
    _addUnique('unlockedComics', key);
    _remove('forcedComics', key);
    invalidate();
    appdata.saveData();
  }

  /// 用户手动标记为敏感（同时撤销该条的豁免）。
  static void force(Comic comic) {
    final key = keyOf(comic);
    _addUnique('forcedComics', key);
    _remove('unlockedComics', key);
    invalidate();
    appdata.saveData();
  }

  /// 撤销对单条作品的所有手工标注。
  static void clearMarks(Comic comic) {
    final key = keyOf(comic);
    _remove('unlockedComics', key);
    _remove('forcedComics', key);
    invalidate();
    appdata.saveData();
  }

  /// 设置变化后必须调用：否则用户改完预设/覆盖，列表还是旧结果。
  static void invalidate() => _cache.clear();

  static bool _contains(String settingKey, String value) {
    final list = appdata.settings[settingKey];
    return list is List && list.contains(value);
  }

  static void _addUnique(String settingKey, String value) {
    final list = appdata.settings[settingKey];
    if (list is List && !list.contains(value)) {
      list.add(value);
    }
  }

  static void _remove(String settingKey, String value) {
    final list = appdata.settings[settingKey];
    if (list is List) {
      list.remove(value);
    }
  }
}
