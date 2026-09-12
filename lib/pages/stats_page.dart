import 'dart:convert';
import 'dart:math' as math;

import 'package:flutter/material.dart';
import 'package:flutter_miuix/miuix.dart';
import 'package:venera/components/components.dart';
import 'package:venera/foundation/app.dart';
import 'package:venera/foundation/comic_type.dart';
import 'package:venera/foundation/history.dart';
import 'package:venera/pages/aggregated_search_page.dart';
import 'package:venera/utils/io.dart';
import 'package:venera/utils/opencc.dart';
import 'package:venera/utils/tags_translation.dart';
import 'package:venera/utils/translations.dart';

/// 阅读统计：数据来自 read_stats 表（阅读器会话结束时累计的页数）。
/// 本版本安装起开始积累，无历史回填。图表自绘，零新依赖。
class ReadingStatsPage extends StatefulWidget {
  const ReadingStatsPage({super.key});

  @override
  State<ReadingStatsPage> createState() => _ReadingStatsPageState();
}

class _ReadingStatsPageState extends State<ReadingStatsPage> {
  /// 估算每页阅读耗时（秒）——没有真实计时，仅供「估算时长」展示。
  static const _secondsPerPage = 15;

  late final Map<String, int> _daily7;
  late final int _todayPages;
  late final int _weekPages;
  late final int _monthPages;
  late final int _streakDays;
  late final List<StatsTopComic> _topComics;
  late final List<(String, int)> _bySource;

  /// 题材偏好（近30天/近1年可切）：归一化标签 → 页数（降序）。
  Map<String, int> _tagPages = {};

  /// 有标签记录的范围内的总页数（分享文案用）。
  int _taggedPages = 0;

  /// 按月聚合的 Top2 标签（时间轴）。
  List<({String month, List<(String, int)> tags})> _monthlyTop = [];

  /// 题材统计范围（30 天 / 365 天）。
  int _scopeDays = 30;

  Widget _scopeChip(String label, int days) {
    final selected = _scopeDays == days;
    return _ScopeChip(label, days, selected, (d) {
      if (d == _scopeDays) return;
      _scopeDays = d;
      _recomputeTagData();
    });
  }

  bool get _hasData => _monthPages > 0;

  bool get _hasTagData => _tagPages.isNotEmpty;

  @override
  void initState() {
    super.initState();
    final hm = HistoryManager();
    final now = DateTime.now();
    _daily7 = hm.dailyPages(7);
    _todayPages = _daily7[statsDateKey(now)] ?? 0;
    _weekPages = hm.pagesSince(statsWeekStart(now));
    _monthPages = hm.pagesSince(DateTime(now.year, now.month, 1));
    _streakDays = statsStreakDays(hm.readDates());
    _topComics = hm.topComics(30, 5);
    _bySource = [
      for (final (typeValue, count) in hm.comicsByType(30))
        (_resolveSourceName(typeValue), count),
    ];
    _recomputeTagData();
  }

  /// 题材聚合：readTagRows 的原始 plainTags 逐条过归一化管线，
  /// 每行页数按其全部标签计入（一个漫画有 10 个 tag 就给 10 个 tag 各记一次）。
  void _recomputeTagData() {
    final normalizer = _TagNormalizer.instance;
    final rows = HistoryManager().readTagRows(_scopeDays);
    final tagPages = <String, int>{};
    final monthly = <String, Map<String, int>>{};
    var taggedPages = 0;
    for (final (date, pages, tagsJson) in rows) {
      taggedPages += pages;
      List<String> tags;
      try {
        tags = (jsonDecode(tagsJson) as List).cast<String>();
      } catch (_) {
        continue;
      }
      final month = date.substring(0, 7);
      for (final raw in tags) {
        final tag = normalizer.normalize(raw);
        if (tag == null) continue;
        tagPages[tag] = (tagPages[tag] ?? 0) + pages;
        final m = monthly.putIfAbsent(month, () => {});
        m[tag] = (m[tag] ?? 0) + pages;
      }
    }
    final sorted = tagPages.entries.toList()
      ..sort((a, b) => b.value.compareTo(a.value));
    final months = monthly.keys.toList()..sort((a, b) => b.compareTo(a));
    setState(() {
      _tagPages = {for (final e in sorted) e.key: e.value};
      _taggedPages = taggedPages;
      _monthlyTop = [
        for (final month in months.take(_scopeDays > 30 ? 12 : 6))
          (
            month: month,
            tags: [
              for (final e in (monthly[month] ?? {}).entries.toList()
                ..sort((a, b) => b.value.compareTo(a.value)))
                (e.key, e.value),
            ].take(2).toList(),
          ),
      ];
    });
  }

  void _dig(String tag) {
    App.rootContext.to(() => AggregatedSearchPage(keyword: tag));
  }

  void _shareSoulTag(String tag) {
    final scope = _scopeDays > 30 ? "Last 1 Year".tl : "Last 30 Days".tl;
    Share.shareText(
      "🎈 $scope $_taggedPages ${"Pages".tl} · ${"Soul Tag".tl} #$tag — venera-miuix",
    );
  }

  String _resolveSourceName(int typeValue) {
    final type = ComicType(typeValue);
    if (type == ComicType.local) {
      return "Local".tl;
    }
    return type.comicSource?.name ?? "Unknown".tl;
  }

  String _estimate(int pages) {
    final seconds = pages * _secondsPerPage;
    if (seconds >= 3600) {
      return "≈ ${(seconds / 3600).toStringAsFixed(1)} h";
    }
    return "≈ ${(seconds / 60).round()} min";
  }

  @override
  Widget build(BuildContext context) {
    final isDark = context.isDarkMode;
    final cardColors = isDark
        ? MiuixCardColors(
            // 深色下用 MD3 surfaceContainerHigh，避免 Miuix 默认灰与背景同色
            // （与 home_page 的 _MiuixLocal 同款处理，铁律 #3）。
            color: context.colorScheme.surfaceContainerHigh,
            contentColor: context.colorScheme.onSurface,
          )
        : null;
    final subtitleColor = context.colorScheme.onSurfaceVariant;

    Widget body = SmoothCustomScrollView(
      slivers: [
        SliverAppbar(
          leading: IconButton(
            icon: const Icon(Icons.arrow_back),
            onPressed: () => context.pop(),
          ),
          title: Text("Reading Stats".tl),
        ),
        if (!_hasData)
          SliverToBoxAdapter(
            child: Padding(
              padding: const EdgeInsets.fromLTRB(24, 48, 24, 0),
              child: Column(
                children: [
                  Icon(
                    Icons.insights,
                    size: 48,
                    color: context.colorScheme.outline,
                  ),
                  const SizedBox(height: 16),
                  Text(
                    "Start reading and your stats will show up here.".tl,
                    textAlign: TextAlign.center,
                    style: TextStyle(color: subtitleColor),
                  ),
                ],
              ),
            ),
          )
        else ...[
          SliverToBoxAdapter(
            child: _StatsCard(
              cardColors: cardColors,
              child: Row(
                children: [
                  _StatCell(
                    label: "Today".tl,
                    value: "$_todayPages",
                    icon: Icons.today,
                  ),
                  _divider(context),
                  _StatCell(
                    label: "This Week".tl,
                    value: "$_weekPages",
                    icon: Icons.date_range,
                  ),
                  _divider(context),
                  _StatCell(
                    label: "Day Streak".tl,
                    value: "$_streakDays",
                    icon: Icons.local_fire_department,
                    iconColor: Colors.orange,
                  ),
                ],
              ),
            ),
          ),
          SliverToBoxAdapter(
            child: _StatsCard(
              cardColors: cardColors,
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  _CardTitle("Last 7 Days".tl),
                  const SizedBox(height: 12),
                  SizedBox(
                    height: 150,
                    child: _WeeklyBarChart(
                      values: [
                        for (var i = 6; i >= 0; i--)
                          _daily7[statsDateKey(
                            DateTime.now().subtract(Duration(days: i)),
                          )] ?? 0,
                      ],
                      highlightColor: context.colorScheme.primary,
                      barColor: context.colorScheme.surfaceContainerHighest,
                      valueColor: context.colorScheme.onSurfaceVariant,
                    ),
                  ),
                ],
              ),
            ),
          ),
          SliverToBoxAdapter(
            child: _StatsCard(
              cardColors: cardColors,
              child: Row(
                children: [
                  _StatCell(
                    label: "This Month".tl,
                    value: _monthPages.toString(),
                    subtitle: _estimate(_monthPages),
                    icon: Icons.calendar_month,
                  ),
                  _divider(context),
                  _StatCell(
                    label: "Daily Average".tl,
                    value: (_monthPages / DateTime.now().day).round().toString(),
                    icon: Icons.trending_up,
                  ),
                ],
              ),
            ),
          ),
          if (_hasTagData) ...[
            SliverToBoxAdapter(
              child: Padding(
                padding: const EdgeInsets.fromLTRB(12, 12, 12, 0),
                child: Row(
                  children: [
                    _scopeChip("Last 30 Days".tl, 30),
                    const SizedBox(width: 8),
                    _scopeChip("Last 1 Year".tl, 365),
                  ],
                ),
              ),
            ),
            SliverToBoxAdapter(
              child: _StatsCard(
                cardColors: cardColors,
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    _CardTitle("Reading Preference".tl),
                    const SizedBox(height: 12),
                    _PreferenceDonut(
                      entries: [
                        for (final e in _tagPages.entries.take(6))
                          (e.key, e.value),
                        if (_tagPages.length > 6)
                          (
                            "Other".tl,
                            _tagPages.values
                                .skip(6)
                                .fold(0, (a, b) => a + b),
                          ),
                      ],
                      total: _tagPages.values.fold(0, (a, b) => a + b),
                      onDig: _dig,
                    ),
                  ],
                ),
              ),
            ),
            SliverToBoxAdapter(
              child: _StatsCard(
                cardColors: cardColors,
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Row(
                      children: [
                        Expanded(child: _CardTitle("Soul Tag".tl)),
                        IconButton(
                          icon: const Icon(Icons.share, size: 20),
                          tooltip: "Share".tl,
                          onPressed: () =>
                              _shareSoulTag(_tagPages.keys.first),
                        ),
                      ],
                    ),
                    const SizedBox(height: 4),
                    GestureDetector(
                      onTap: () => _dig(_tagPages.keys.first),
                      child: Text(
                        "#${_tagPages.keys.first}",
                        style: TextStyle(
                          fontSize: 26,
                          fontWeight: FontWeight.w800,
                          color: context.colorScheme.primary,
                        ),
                      ),
                    ),
                    const SizedBox(height: 12),
                    // 标签云：权重越大字号越大，点击挖掘该标签。
                    Wrap(
                      spacing: 8,
                      runSpacing: 8,
                      children: [
                        for (final e in _tagPages.entries.take(12))
                          InkWell(
                            borderRadius: BorderRadius.circular(10),
                            onTap: () => _dig(e.key),
                            child: Container(
                              padding: const EdgeInsets.symmetric(
                                horizontal: 10,
                                vertical: 4,
                              ),
                              decoration: BoxDecoration(
                                borderRadius: BorderRadius.circular(10),
                                // 权重越大底色越实，形成视觉层级。
                                color: context.colorScheme.primary.withValues(
                                  alpha: 0.10 +
                                      0.22 *
                                          (e.value / _tagPages.values.first)
                                              .clamp(0.0, 1.0),
                                ),
                              ),
                              child: Text(
                                "#${e.key}",
                                style: TextStyle(
                                  fontSize:
                                      12 + 8 * (e.value / _tagPages.values.first),
                                  color: context.colorScheme.onSurface,
                                ),
                              ),
                            ),
                          ),
                      ],
                    ),
                  ],
                ),
              ),
            ),
            SliverToBoxAdapter(
              child: _StatsCard(
                cardColors: cardColors,
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    _CardTitle("Reading Timeline".tl),
                    const SizedBox(height: 12),
                    for (var i = 0; i < _monthlyTop.length; i++)
                      _TimelineRow(
                        month: _monthlyTop[i].month,
                        tags: _monthlyTop[i].tags,
                        isFirst: i == 0,
                        isLast: i == _monthlyTop.length - 1,
                      ),
                  ],
                ),
              ),
            ),
          ] else if (_hasData)
            SliverToBoxAdapter(
              child: _StatsCard(
                cardColors: cardColors,
                child: Text(
                  "Tag stats will appear here after you read a few comics."
                      .tl,
                  style: TextStyle(color: context.colorScheme.onSurfaceVariant),
                ),
              ),
            ),
          if (_topComics.isNotEmpty)
            SliverToBoxAdapter(
              child: _StatsCard(
                cardColors: cardColors,
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    _CardTitle("Top Comics".tl),
                    const SizedBox(height: 8),
                    for (var i = 0; i < _topComics.length; i++)
                      Padding(
                        padding: const EdgeInsets.symmetric(vertical: 6),
                        child: Row(
                          children: [
                            SizedBox(
                              width: 24,
                              child: Text(
                                "${i + 1}",
                                style: TextStyle(
                                  fontSize: 14,
                                  fontWeight: FontWeight.w600,
                                  color: i == 0
                                      ? context.colorScheme.primary
                                      : subtitleColor,
                                ),
                              ),
                            ),
                            const SizedBox(width: 8),
                            Expanded(
                              child: Text(
                                _topComics[i].title.replaceAll("\n", ""),
                                maxLines: 1,
                                overflow: TextOverflow.ellipsis,
                              ),
                            ),
                            const SizedBox(width: 8),
                            Text(
                              "${_topComics[i].pages} ${"Pages".tl}",
                              style: TextStyle(fontSize: 13, color: subtitleColor),
                            ),
                          ],
                        ),
                      ),
                  ],
                ),
              ),
            ),
          if (_bySource.isNotEmpty)
            SliverToBoxAdapter(
              child: _StatsCard(
                cardColors: cardColors,
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    _CardTitle("By Source".tl),
                    const SizedBox(height: 8),
                    for (final (name, pages) in _bySource)
                      Padding(
                        padding: const EdgeInsets.symmetric(vertical: 6),
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Row(
                              children: [
                                Expanded(
                                  child: Text(
                                    name,
                                    maxLines: 1,
                                    overflow: TextOverflow.ellipsis,
                                  ),
                                ),
                                Text(
                                  "$pages ${"Comics".tl}",
                                  style: TextStyle(
                                    fontSize: 13,
                                    color: subtitleColor,
                                  ),
                                ),
                              ],
                            ),
                            const SizedBox(height: 4),
                            ClipRRect(
                              borderRadius: BorderRadius.circular(3),
                              child: SizedBox(
                                height: 6,
                                child: Stack(
                                  children: [
                                    Container(
                                      color: context
                                          .colorScheme.surfaceContainerHighest,
                                    ),
                                    FractionallySizedBox(
                                      widthFactor: (pages /
                                              math.max(_bySource.first.$2, 1))
                                          .clamp(0.0, 1.0),
                                      child: Container(
                                        color: context.colorScheme.primary,
                                      ),
                                    ),
                                  ],
                                ),
                              ),
                            ),
                          ],
                        ),
                      ),
                  ],
                ),
              ),
            ),
        ],
        SliverPadding(padding: EdgeInsets.only(bottom: context.padding.bottom + 16)),
      ],
    );

    body = withMiuixTheme(context, body);
    return Scaffold(
      body: Material(
        color: Colors.transparent,
        child: body,
      ),
    );
  }

  Widget _divider(BuildContext context) => Container(
        width: 1,
        height: 28,
        color: context.colorScheme.outlineVariant,
      );
}

/// 统计页统一卡片：标题区 + 内容，卡片间距走外层 Padding。
class _StatsCard extends StatelessWidget {
  const _StatsCard({required this.child, this.cardColors});

  final Widget child;

  final MiuixCardColors? cardColors;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.fromLTRB(12, 12, 12, 0),
      child: MiuixCard(
        cornerRadius: 16,
        colors: cardColors,
        insideMargin: const EdgeInsets.all(16),
        child: child,
      ),
    );
  }
}

class _CardTitle extends StatelessWidget {
  const _CardTitle(this.text);

  final String text;

  @override
  Widget build(BuildContext context) {
    return Text(
      text,
      style: const TextStyle(fontSize: 16, fontWeight: FontWeight.w600),
    );
  }
}

/// 三列统计单元（图标 + 大数字 + 标签 + 可选副文本）。
class _StatCell extends StatelessWidget {
  const _StatCell({
    required this.label,
    required this.value,
    this.subtitle,
    this.icon,
    this.iconColor,
  });

  final String label;

  final String value;

  final String? subtitle;

  final IconData? icon;

  final Color? iconColor;

  @override
  Widget build(BuildContext context) {
    return Expanded(
      child: Column(
        children: [
          if (icon != null) ...[
            Icon(icon, size: 18, color: iconColor ?? context.colorScheme.primary),
            const SizedBox(height: 4),
          ],
          Text(
            value,
            style: const TextStyle(fontSize: 22, fontWeight: FontWeight.w700),
          ),
          const SizedBox(height: 4),
          Text(
            label,
            style: TextStyle(
              fontSize: 12,
              color: context.colorScheme.onSurfaceVariant,
            ),
          ),
          if (subtitle != null)
            Text(
              subtitle!,
              style: TextStyle(
                fontSize: 11,
                color: context.colorScheme.onSurfaceVariant,
              ),
            ),
        ],
      ),
    );
  }
}

/// 近 7 天柱状图：7 等分列，柱顶标数值（0 不标），今天高亮。
/// 星期/日期标签由外层 Row 对齐绘制（与 painter 同列宽）。
class _WeeklyBarChart extends StatelessWidget {
  const _WeeklyBarChart({
    required this.values,
    required this.highlightColor,
    required this.barColor,
    required this.valueColor,
  });

  final List<int> values;

  final Color highlightColor;

  final Color barColor;

  final Color valueColor;

  @override
  Widget build(BuildContext context) {
    final now = DateTime.now();
    return Column(
      children: [
        Expanded(
          child: CustomPaint(
            size: Size.infinite,
            painter: _BarsPainter(
              values: values,
              highlightColor: highlightColor,
              barColor: barColor,
              valueColor: valueColor,
            ),
          ),
        ),
        const SizedBox(height: 4),
        Row(
          children: [
            for (var i = 0; i < values.length; i++)
              Expanded(
                child: Center(
                  child: Text(
                    "${now.subtract(Duration(days: values.length - 1 - i)).day}",
                    style: TextStyle(
                      fontSize: 11,
                      fontWeight: i == values.length - 1
                          ? FontWeight.w700
                          : FontWeight.w400,
                      color: i == values.length - 1
                          ? highlightColor
                          : valueColor,
                    ),
                  ),
                ),
              ),
          ],
        ),
      ],
    );
  }
}

class _BarsPainter extends CustomPainter {
  _BarsPainter({
    required this.values,
    required this.highlightColor,
    required this.barColor,
    required this.valueColor,
  });

  final List<int> values;

  final Color highlightColor;

  final Color barColor;

  final Color valueColor;

  @override
  void paint(Canvas canvas, Size size) {
    final maxValue = values.fold<int>(0, (a, b) => math.max(a, b));
    if (maxValue == 0) {
      return;
    }
    final columnWidth = size.width / values.length;
    final maxValueHeight = size.height - 18;
    for (var i = 0; i < values.length; i++) {
      final value = values[i];
      final isToday = i == values.length - 1;
      final barWidth = math.min(columnWidth - 10, 28.0);
      final dx = columnWidth * i + (columnWidth - barWidth) / 2;
      if (value > 0) {
        final barHeight =
            math.max((value / maxValue) * maxValueHeight, 4.0);
        final rrect = RRect.fromRectAndCorners(
          Rect.fromLTWH(dx, size.height - barHeight, barWidth, barHeight),
          topLeft: const Radius.circular(4),
          topRight: const Radius.circular(4),
        );
        canvas.drawRRect(
          rrect,
          Paint()..color = isToday ? highlightColor : barColor,
        );
        // 柱顶数值。
        final tp = TextPainter(
          text: TextSpan(
            text: "$value",
            style: TextStyle(
              fontSize: 10,
              fontWeight: isToday ? FontWeight.w700 : FontWeight.w400,
              color: valueColor,
            ),
          ),
          textDirection: TextDirection.ltr,
        )..layout();
        tp.paint(
          canvas,
          Offset(
            dx + (barWidth - tp.width) / 2,
            math.max(size.height - barHeight - tp.height - 2, 0),
          ),
        );
      }
    }
  }

  @override
  bool shouldRepaint(_BarsPainter oldDelegate) =>
      oldDelegate.values != values ||
      oldDelegate.highlightColor != highlightColor ||
      oldDelegate.barColor != barColor;
}

// ---- 日期/统计工具（主页摘要卡与统计页共用）----

String statsDateKey(DateTime d) =>
    "${d.year.toString().padLeft(4, '0')}-"
    "${d.month.toString().padLeft(2, '0')}-${d.day.toString().padLeft(2, '0')}";

/// 本周一（周起始 = 周一），只保留日期。
DateTime statsWeekStart(DateTime now) =>
    DateTime(now.year, now.month, now.day - (now.weekday - 1));

/// 连续阅读天数：从今天（或昨天）往前数连续有记录的天数。
int statsStreakDays(Set<String> dates) {
  var days = 0;
  var cursor = DateTime.now();
  if (!dates.contains(statsDateKey(cursor))) {
    cursor = cursor.subtract(const Duration(days: 1));
  }
  while (dates.contains(statsDateKey(cursor))) {
    days += 1;
    cursor = cursor.subtract(const Duration(days: 1));
  }
  return days;
}

/// 范围切换 chip（近30天/近1年）。
class _ScopeChip extends StatelessWidget {
  const _ScopeChip(this.label, this.days, this.selected, this.onTap);

  final String label;

  final int days;

  final bool selected;

  final void Function(int days) onTap;

  @override
  Widget build(BuildContext context) {
    return InkWell(
      borderRadius: BorderRadius.circular(14),
      onTap: () => onTap(days),
      child: Container(
        padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 5),
        decoration: BoxDecoration(
          borderRadius: BorderRadius.circular(14),
          color: selected
              ? context.colorScheme.primary
              : context.colorScheme.surfaceContainerHighest,
        ),
        child: Text(
          label,
          style: TextStyle(
            fontSize: 13,
            color: selected
                ? context.colorScheme.onPrimary
                : context.colorScheme.onSurfaceVariant,
          ),
        ),
      ),
    );
  }
}

/// 题材偏好：环形图（Top6+其他）+ 图例。Top1 图例带「挖掘」按钮。
class _PreferenceDonut extends StatelessWidget {
  const _PreferenceDonut({
    required this.entries,
    required this.total,
    required this.onDig,
  });

  final List<(String, int)> entries;

  final int total;

  final void Function(String tag) onDig;

  static const _palette = [
    Color(0xFF6D9EEB),
    Color(0xFF93C47D),
    Color(0xFFE06666),
    Color(0xFF8E7CC3),
    Color(0xFFE2A45C),
    Color(0xFF4CB8C4),
  ];

  @override
  Widget build(BuildContext context) {
    final top = entries.firstOrNull;
    return Row(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        SizedBox(
          width: 132,
          height: 132,
          child: CustomPaint(
            painter: _DonutPainter(
              segments: [
                for (var i = 0; i < entries.length; i++)
                  (
                    _palette[i % _palette.length],
                    entries[i].$2 / math.max(total, 1),
                  ),
              ],
              trackColor: context.colorScheme.surfaceContainerHighest,
              centerLabel: top == null
                  ? null
                  : "${(top.$2 / math.max(total, 1) * 100).round()}%",
            ),
          ),
        ),
        const SizedBox(width: 16),
        Expanded(
          child: Column(
            children: [
              for (var i = 0; i < entries.length; i++)
                Padding(
                  padding: const EdgeInsets.symmetric(vertical: 4),
                  child: Row(
                    children: [
                      Container(
                        width: 10,
                        height: 10,
                        decoration: BoxDecoration(
                          color: _palette[i % _palette.length],
                          shape: BoxShape.circle,
                        ),
                      ),
                      const SizedBox(width: 8),
                      Expanded(
                        child: Text(
                          entries[i].$1,
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                        ),
                      ),
                      Text(
                        "${(entries[i].$2 / math.max(total, 1) * 100).round()}%",
                        style: TextStyle(
                          fontSize: 13,
                          color: context.colorScheme.onSurfaceVariant,
                        ),
                      ),
                      if (i == 0)
                        Padding(
                          padding: const EdgeInsets.only(left: 8),
                          child: InkWell(
                            borderRadius: BorderRadius.circular(10),
                            onTap: () => onDig(entries[i].$1),
                            child: Container(
                              padding: const EdgeInsets.symmetric(
                                horizontal: 8,
                                vertical: 2,
                              ),
                              decoration: BoxDecoration(
                                borderRadius: BorderRadius.circular(10),
                                color: context.colorScheme.primaryContainer,
                              ),
                              child: Text(
                                "Dig".tl,
                                style: TextStyle(
                                  fontSize: 12,
                                  color: context.colorScheme.onPrimaryContainer,
                                ),
                              ),
                            ),
                          ),
                        ),
                    ],
                  ),
                ),
            ],
          ),
        ),
      ],
    );
  }
}

class _DonutPainter extends CustomPainter {
  _DonutPainter({
    required this.segments,
    required this.trackColor,
    this.centerLabel,
  });

  final List<(Color, double)> segments;

  final Color trackColor;

  final String? centerLabel;

  @override
  void paint(Canvas canvas, Size size) {
    final center = Offset(size.width / 2, size.height / 2);
    final radius = math.min(size.width, size.height) / 2 - 2;
    final stroke = radius * 0.38;
    final rect = Rect.fromCircle(center: center, radius: radius - stroke / 2);
    // 底环：让数据不满一圈时形状也完整。
    canvas.drawArc(
      rect,
      0,
      2 * math.pi,
      false,
      Paint()
        ..color = trackColor
        ..style = PaintingStyle.stroke
        ..strokeWidth = stroke,
    );
    const gap = 0.03;
    var start = -math.pi / 2;
    for (final (color, fraction) in segments) {
      final sweep = math.max(fraction * 2 * math.pi - gap, 0.01);
      canvas.drawArc(
        rect,
        start + gap / 2,
        sweep,
        false,
        Paint()
          ..color = color
          ..style = PaintingStyle.stroke
          ..strokeWidth = stroke,
      );
      start += fraction * 2 * math.pi;
    }
    if (centerLabel != null) {
      final tp = TextPainter(
        text: TextSpan(
          text: centerLabel,
          style: TextStyle(
            fontSize: radius * 0.3,
            fontWeight: FontWeight.w700,
          ),
        ),
        textDirection: TextDirection.ltr,
      )..layout();
      tp.paint(canvas, center - Offset(tp.width / 2, tp.height / 2));
    }
  }

  @override
  bool shouldRepaint(_DonutPainter oldDelegate) =>
      oldDelegate.segments != segments ||
      oldDelegate.trackColor != trackColor ||
      oldDelegate.centerLabel != centerLabel;
}

/// 追漫轨迹时间轴单行：月份 + 圆点竖线 + 当月 Top 标签。
class _TimelineRow extends StatelessWidget {
  const _TimelineRow({
    required this.month,
    required this.tags,
    required this.isFirst,
    required this.isLast,
  });

  final String month;

  final List<(String, int)> tags;

  final bool isFirst;

  final bool isLast;

  @override
  Widget build(BuildContext context) {
    final lineColor = context.colorScheme.outlineVariant;
    return IntrinsicHeight(
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          SizedBox(
            width: 52,
            child: Align(
              alignment: Alignment.topLeft,
              child: Padding(
                padding: const EdgeInsets.only(top: 2),
                child: Text(
                  month,
                  style: TextStyle(
                    fontSize: 12,
                    color: context.colorScheme.onSurfaceVariant,
                  ),
                ),
              ),
            ),
          ),
          SizedBox(
            width: 20,
            child: Column(
              children: [
                Container(
                  width: 2,
                  height: isFirst ? 10 : 0,
                  color: lineColor,
                ),
                Container(
                  width: 8,
                  height: 8,
                  decoration: BoxDecoration(
                    color: context.colorScheme.primary,
                    shape: BoxShape.circle,
                  ),
                ),
                if (!isLast)
                  Expanded(child: Container(width: 2, color: lineColor)),
              ],
            ),
          ),
          const SizedBox(width: 10),
          Expanded(
            child: Padding(
              padding: const EdgeInsets.only(bottom: 16),
              child: Wrap(
                spacing: 6,
                runSpacing: 4,
                children: [
                  for (final (tag, pages) in tags)
                    Container(
                      padding: const EdgeInsets.symmetric(
                        horizontal: 10,
                        vertical: 4,
                      ),
                      decoration: BoxDecoration(
                        borderRadius: BorderRadius.circular(12),
                        color: pages == tags.first.$2
                            ? context.colorScheme.primaryContainer
                            : context.colorScheme.surfaceContainerHighest,
                      ),
                      child: Text(
                        "#$tag",
                        style: TextStyle(
                          fontSize: 13,
                          fontWeight: pages == tags.first.$2
                              ? FontWeight.w600
                              : FontWeight.w400,
                          color: context.colorScheme.onSurface,
                        ),
                      ),
                    ),
                ],
              ),
            ),
          ),
        ],
      ),
    );
  }
}

/// 跨源标签归一化：把各源写法不一的同一题材（萝莉/蘿莉/lolicon）归到同一个
/// 规范名。原始 plainTags 原样入库，归一化只在聚合时做——翻译库将来更新可
/// 重算。四级管线：namespace 过滤 → 合并字典查表（TagsTranslation 11 库中
/// 题材相关的 6 个，跨 namespace 合并）→ 别名表 → OpenCC 繁转简 → 兜底原样。
class _TagNormalizer {
  _TagNormalizer._();

  static final _TagNormalizer instance = _TagNormalizer._();

  /// 与「题材」无关的 namespace（角色/题材/标签类全部参与聚合）。
  /// 清单来自对 .workbuddy/tmp 下 20 个真实源 JS 的 tags 键名普查：
  /// 作者(14)/标签(11)/状态(9)/更新(8)/Tags(3)/题材(2)/Author(2)/Categories(2)
  /// /Tag/Artists/Authors/Status/Update/Actor/View/Work/语言/热度/地区/分類 等。
  static const _excludedNamespaces = {
    // 作者类
    '作者', 'author', 'authors', 'artists', 'artist', '画师', '插画',
    // 状态/元信息类
    '状态', 'status', '连载中', '更新', 'update', 'date', '时间', '热度',
    'view', 'work', 'misc',
    // 语言/来源分类（值为 同人/短篇/AI生成 等格式词，不是题材）
    '语言', 'language', 'categories', 'category', '分类', '分類',
    // 社团/上传者
    'group', '社团', 'uploader', '上传', '上传者', 'cosplayer', 'reclass',
  };

  /// 别名表：翻译库未覆盖的同义写法（规范化后的英文 key → 规范英文 key）。
  static const _aliases = {
    'loli': 'lolicon',
    'shota': 'shotacon',
  };

  /// 格式/元信息类标签值：不是题材，不进偏好统计（「同人/短篇/AI生成/AI绘图」
  /// 之类）。比对前会先 lower+繁转简，所以繁体写法（AI繪圖）也会命中。
  static const _excludedTagValues = {
    '同人', '同人志', '短篇', '短篇集', '单行本', '画集', '画册', 'cg', 'cg集',
    'ai生成', 'ai绘图', 'ai-generated', '漫画', '杂志', '其他', 'unknown',
    '系列', '连载中', '已完结', '完结', '全彩', '彩色', '无修', '无修正',
  };

  late final Map<String, String> _dict = _buildDict();

  Map<String, String> _buildDict() {
    final dict = <String, String>{};
    void addAll(Map<String, String> tags) {
      tags.forEach((en, zh) {
        dict.putIfAbsent(_normKey(en), () => zh);
      });
    }

    addAll(TagsTranslation.femaleTags);
    addAll(TagsTranslation.maleTags);
    addAll(TagsTranslation.mixedTags);
    addAll(TagsTranslation.otherTags);
    addAll(TagsTranslation.parodyTags);
    addAll(TagsTranslation.characterTranslations);
    return dict;
  }

  /// 复用 TagsTranslation 的既有规范化语义：小写 + 去尾部 s（reclass 除外）。
  static String _normKey(String tag) {
    var t = tag.trim().toLowerCase();
    if (!t.endsWith('reclass') && t.endsWith('s') && t.length > 2) {
      t = t.substring(0, t.length - 1);
    }
    return t;
  }

  /// 返回 null = 该标签被过滤（杂项 namespace / 格式类标签值）或不合法。
  String? normalize(String plainTag) {
    final idx = plainTag.indexOf(':');
    if (idx <= 0) {
      return null;
    }
    final namespace = plainTag.substring(0, idx).trim().toLowerCase();
    final tag = plainTag.substring(idx + 1).trim();
    if (tag.isEmpty || _excludedNamespaces.contains(namespace)) {
      return null;
    }
    var key = _normKey(tag);
    key = _aliases[key] ?? key;
    // 先查规范化 key，再查不去 s 的原样 key（有些词合法以 s 结尾）。
    String? result = _dict[key] ?? _dict[tag.trim().toLowerCase()];
    result ??= OpenCC.hasChineseTraditional(tag)
        ? OpenCC.traditionalToSimplified(tag)
        : tag;
    // 格式/元信息类值（同人/短篇/AI生成/AI繪圖等）不是题材，剔除。
    // 值可能随 locale 是繁体（tags_tw 字典），比对前先繁转简。
    if (_excludedTagValues
        .contains(OpenCC.traditionalToSimplified(result).toLowerCase())) {
      return null;
    }
    return result;
  }
}
