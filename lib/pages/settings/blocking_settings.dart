part of 'settings_page.dart';

/// 屏幕防窥：Android 走 `FLAG_SECURE`（禁止截屏 + 最近任务里缩略图空白）。
///
/// 这是系统级开关，客户端只是转调原生。iOS 没有等价的 API（PxEz 那边是在
/// `AppLifecycleState.inactive` 时自己盖一层遮罩），属于另一个平台分支，
/// P0 先不铺开。
///
/// ⚠️ 部分 ROM 上 FLAG_SECURE 需要重建 window 才彻底生效，所以设置项副标题
/// 里如实写了「需重启应用生效」—— 不写的话用户会以为开关坏了。
Future<void> applySecureWindow(bool value) async {
  if (!App.isAndroid) {
    return;
  }
  try {
    await const MethodChannel(
      'venera/method_channel',
    ).invokeMethod('setSecureWindow', {'set': value});
  } catch (e) {
    Log.error("SecureWindow", "$e");
  }
}

/// 「屏蔽与过滤」设置页。
///
/// 本页三组开关对列表数据的处置方式**完全不同**，这是设计里唯一"想错了就要
/// 返工"的地方：
///
/// - **遮蔽**（H 是不行的）→ 条目保留、只糊封面，标题标签照常可读
/// - **隐藏**（标签 / 画师 / 作品）→ 从列表剔除
///
/// 前者是安全边界，删了既"没了"又会让 `maxPage` 分页错位；后者是用户**显式**
/// 意图（"别再让我看到"），且项目里既有的 `blockedWords` 就是这么做的，保持一致。
/// 至于为什么"剔除"不违反"不要替用户删元素"—— 因为那是用户自己点进去加的，
/// 可见、可撤销、可管理，代价（分页漂移）用户是接受的。
class BlockingSettings extends StatefulWidget {
  const BlockingSettings({super.key});

  @override
  State<BlockingSettings> createState() => _BlockingSettingsState();
}

class _BlockingSettingsState extends State<BlockingSettings> {
  int _count(String settingKey) {
    final list = appdata.settings[settingKey];
    return list is List ? list.length : 0;
  }

  String _strengthSummary(NsfwMaskStrength strength) {
    return switch (strength) {
      NsfwMaskStrength.off => "Disabled".tl,
      NsfwMaskStrength.blur => "Blur covers".tl,
      NsfwMaskStrength.blurReveal => "Blur covers, tap to show".tl,
      NsfwMaskStrength.hide => "Hide entries".tl,
    };
  }

  void _open(Widget Function() builder) {
    showPopUpWidget(App.rootContext, builder());
  }

  /// 源分级概况：「N 个源 · M 个成人向 · K 个已覆盖」。
  ///
  /// 数字直接来自判定链，不另存一份计数 —— 免得和真实状态漂移。
  String _sourceSummary() {
    final sources = ComicSource.all();
    var masked = 0;
    var overridden = 0;
    for (final source in sources) {
      if (ContentGuard.sourceOverride(source.key) != null) {
        overridden++;
      }
      if (ContentGuard.sourceLevelOf(source.key).shouldMask) {
        masked++;
      }
    }
    return "@n sources · @a adult · @o overridden".tlParams({
      'n': sources.length.toString(),
      'a': masked.toString(),
      'o': overridden.toString(),
    });
  }

  @override
  Widget build(BuildContext context) {
    final strength = ContentGuard.strength;
    return SmoothCustomScrollView(
      slivers: [
        SliverAppbar(title: Text("Blocking & Filtering".tl)),

        // ── 隐私设置 ──────────────────────────────────────────────────
        _SettingPartTitle(
          title: "Privacy".tl,
          icon: Icons.lock_outline,
        ),
        _BlockingCard(
          children: [
            _BlockingSwitch(
              title: "H is not allowed".tl,
              subtitle: _strengthSummary(strength),
              value: strength != NsfwMaskStrength.off,
              onChanged: (v) {
                setState(() {
                  appdata.settings['nsfwMaskStrength'] = v
                      ? NsfwMaskStrength.blurReveal.name
                      : NsfwMaskStrength.off.name;
                });
                appdata.saveData();
              },
            ),
            _BlockingSwitch(
              title: "Secure window".tl,
              subtitle:
                  "Prevent screenshots and hide the app in recent tasks. Restart required."
                      .tl,
              value: appdata.settings['secureWindow'] == true,
              onChanged: (v) {
                setState(() => appdata.settings['secureWindow'] = v);
                appdata.saveData();
                applySecureWindow(v);
              },
            ),
            // 预设表必然有错（33 个源不可能一个个核实干净），所以必须给用户
            // 一个逐源纠正的入口 —— 这条是「误判可撤销」在源级上的落点。
            _BlockingArrow(
              title: "Source ratings".tl,
              summary: _sourceSummary(),
              onOpen: () => _open(() => const _ManageSourceLevels()),
            ),
          ],
        ),

        // ── 标签 ─────────────────────────────────────────────────────
        _SettingPartTitle(title: "Tags".tl, icon: Icons.sell_outlined),
        _BlockingCard(
          children: [
            _BlockingListTile(
              title: "Tags".tl,
              summary: "${"Blocked tags".tl} · ${_count('blockedTags')}",
              enabled: appdata.settings['enableTagBlock'] != false,
              onToggle: (v) {
                setState(() => appdata.settings['enableTagBlock'] = v);
                appdata.saveData();
              },
              onOpen: () => _open(
                () => const _ManageBlockList(
                  settingKey: 'blockedTags',
                  title: "Blocked tags",
                  addTitle: "Add tag",
                  hint: "Match tags exactly, without affecting titles",
                ),
              ),
            ),
          ],
        ),

        // ── 画师 ─────────────────────────────────────────────────────
        _SettingPartTitle(title: "Artists".tl, icon: Icons.brush_outlined),
        _BlockingCard(
          children: [
            _BlockingListTile(
              title: "Artists".tl,
              summary: "${"Blocked artists".tl} · ${_count('blockedArtists')}",
              enabled: appdata.settings['enableArtistBlock'] != false,
              onToggle: (v) {
                setState(() => appdata.settings['enableArtistBlock'] = v);
                appdata.saveData();
              },
              onOpen: () => _open(
                () => const _ManageBlockList(
                  settingKey: 'blockedArtists',
                  title: "Blocked artists",
                  addTitle: "Add artist",
                  hint:
                      "Only takes effect on detail pages and sources that expose author tags",
                ),
              ),
            ),
          ],
        ),

        // ── 收录作品 ─────────────────────────────────────────────────
        _SettingPartTitle(
          title: "Comics".tl,
          icon: Icons.menu_book_outlined,
        ),
        _BlockingCard(
          children: [
            _BlockingListTile(
              title: "Comics".tl,
              summary: "${"Blocked comics".tl} · ${_count('blockedComics')}",
              enabled: appdata.settings['enableComicBlock'] != false,
              onToggle: (v) {
                setState(() => appdata.settings['enableComicBlock'] = v);
                appdata.saveData();
              },
              onOpen: () => _open(
                () => const _ManageBlockList(
                  settingKey: 'blockedComics',
                  title: "Blocked comics",
                  addTitle: "Add comic",
                  hint:
                      "Blocked works will not appear in home, search or recommendations",
                ),
              ),
            ),
          ],
        ),

        const SliverToBoxAdapter(child: SizedBox(height: 24)),
      ],
    );
  }
}

/// 一个分区的容器。
///
/// Miuix 画风下包一张 [MiuixCard]（卡片内每行自带按压反馈，靠间距而非分隔线
/// 区分），Classic 画风下就是一行行平铺 —— 与阅读/浏览设置页保持一致。
class _BlockingCard extends StatelessWidget {
  const _BlockingCard({required this.children});

  final List<Widget> children;

  @override
  Widget build(BuildContext context) {
    final column = Column(children: children);
    if (_useMiuixStyle) {
      return SliverToBoxAdapter(
        child: Padding(
          padding: const EdgeInsets.symmetric(horizontal: 12),
          child: MiuixCard(child: column),
        ),
      );
    }
    return SliverToBoxAdapter(child: column);
  }
}

/// 本页专用的开关行。
///
/// 不复用 `_SwitchSetting`：那个组件的 `subtitle` 是构造时定死的字符串，
/// 而这里两条开关的副标题都要**随状态变化**（"未开启" ↔ "模糊封面"）。
class _BlockingSwitch extends StatelessWidget {
  const _BlockingSwitch({
    required this.title,
    required this.subtitle,
    required this.value,
    required this.onChanged,
  });

  final String title;

  final String subtitle;

  final bool value;

  final void Function(bool) onChanged;

  @override
  Widget build(BuildContext context) {
    if (_useMiuixStyle) {
      return MiuixSwitchPreference(
        title: title,
        summary: subtitle,
        value: value,
        onChanged: onChanged,
      );
    }
    return SwitchListTile(
      title: Text(title),
      subtitle: Text(subtitle),
      value: value,
      onChanged: onChanged,
    );
  }
}

/// 带计数与总闸的列表入口行。
///
/// 总闸的意义是**可撤销**：关掉之后已屏蔽的条目立刻恢复显示，用户能马上确认
/// "是这条规则把它拦掉的"，而不是在几十条规则里猜。
class _BlockingListTile extends StatelessWidget {
  const _BlockingListTile({
    required this.title,
    required this.summary,
    required this.enabled,
    required this.onToggle,
    required this.onOpen,
  });

  final String title;

  final String summary;

  final bool enabled;

  final void Function(bool) onToggle;

  final VoidCallback onOpen;

  @override
  Widget build(BuildContext context) {
    if (_useMiuixStyle) {
      return MiuixArrowPreference(
        title: title,
        summary: summary,
        endActions: [
          MiuixButton(
            onPressed: () => onToggle(!enabled),
            minHeight: 32,
            insideMargin: const EdgeInsets.symmetric(
              horizontal: 12,
              vertical: 6,
            ),
            child: MiuixText(enabled ? "Enabled".tl : "Disabled".tl),
          ),
        ],
        onClick: onOpen,
      );
    }
    return ListTile(
      title: Text(title),
      subtitle: Text(summary),
      trailing: Switch(value: enabled, onChanged: onToggle),
      onTap: onOpen,
    );
  }
}

/// 三个屏蔽列表共用的管理子页。
///
/// 与既有的 `Keyword blocking`（`_ManageBlockingWordView`）用同一套交互：
/// 底部弹层 + 右上角「添加」，所以用户在两个入口学到的操作是一致的。
///
/// 不用 Miuix 组件：本页通过根 Overlay 打开，没有 `MiuixTheme` 祖先，
/// 取色会回退到浅色默认值（深色下必穿帮）。
class _ManageBlockList extends StatefulWidget {
  const _ManageBlockList({
    required this.settingKey,
    required this.title,
    required this.addTitle,
    required this.hint,
  });

  final String settingKey;

  final String title;

  final String addTitle;

  final String hint;

  @override
  State<_ManageBlockList> createState() => _ManageBlockListState();
}

class _ManageBlockListState extends State<_ManageBlockList> {
  List get _list {
    final list = appdata.settings[widget.settingKey];
    // 脏数据兜底：settings 是持久化的扁平字典，旧版本或同步来的损坏数据
    // 可能让这里不是 List。直接崩掉不如就地重建。
    if (list is! List) {
      appdata.settings[widget.settingKey] = [];
      return appdata.settings[widget.settingKey] as List;
    }
    return list;
  }

  @override
  Widget build(BuildContext context) {
    return PopUpWidgetScaffold(
      title: widget.title.tl,
      tailing: [
        TextButton.icon(
          icon: const Icon(Icons.add),
          label: Text("Add".tl),
          onPressed: add,
        ),
      ],
      body: Column(
        children: [
          Padding(
            padding: const EdgeInsets.fromLTRB(16, 0, 16, 8),
            child: Text(
              widget.hint.tl,
              style: TextStyle(
                fontSize: 12,
                color: Theme.of(context).colorScheme.onSurfaceVariant,
              ),
            ),
          ),
          Expanded(
            child: ListView.builder(
              itemCount: _list.length,
              itemBuilder: (context, index) {
                return ListTile(
                  title: Text(_list[index].toString()),
                  trailing: IconButton(
                    icon: const Icon(Icons.close),
                    onPressed: () {
                      setState(() => _list.removeAt(index));
                      appdata.saveData();
                    },
                  ),
                );
              },
            ),
          ),
        ],
      ),
    );
  }

  void add() {
    showDialog(
      context: App.rootContext,
      builder: (context) {
        var controller = TextEditingController();
        String? error;
        return StatefulBuilder(
          builder: (context, setState) {
            return ContentDialog(
              title: widget.addTitle.tl,
              content: TextField(
                controller: controller,
                autofocus: true,
                decoration: InputDecoration(
                  border: const OutlineInputBorder(),
                  label: Text(widget.title.tl),
                  hintText: widget.settingKey == 'blockedComics'
                      ? 'sourceKey@id'
                      : null,
                  errorText: error,
                ),
                onChanged: (s) {
                  if (error != null) {
                    setState(() => error = null);
                  }
                },
              ).paddingHorizontal(12),
              actions: [
                Button.filled(
                  onPressed: () {
                    final value = controller.text.trim();
                    if (value.isEmpty) {
                      return;
                    }
                    if (_list.contains(value)) {
                      setState(() => error = "Keyword already exists".tl);
                      return;
                    }
                    _list.add(value);
                    appdata.saveData();
                    this.setState(() {});
                    context.pop();
                  },
                  child: Text("Add".tl),
                ),
              ],
            );
          },
        );
      },
    );
  }
}

/// 不带总闸的跳转行（与 [_BlockingListTile] 同款外观，但没有开关）。
class _BlockingArrow extends StatelessWidget {
  const _BlockingArrow({
    required this.title,
    required this.summary,
    required this.onOpen,
  });

  final String title;

  final String summary;

  final VoidCallback onOpen;

  @override
  Widget build(BuildContext context) {
    if (_useMiuixStyle) {
      return MiuixArrowPreference(title: title, summary: summary, onClick: onOpen);
    }
    return ListTile(
      title: Text(title),
      subtitle: Text(summary),
      trailing: const Icon(Icons.chevron_right),
      onTap: onOpen,
    );
  }
}

/// 「源分级」子页：逐个源查看/纠正内容分级。
///
/// 为什么这一个页面是 P1 里最不能省的部分：预设表是**猜**出来的（33 个源，
/// 有 3 个连站点都核实不了），判定链又把这个猜当默认值。没有这个页面，
/// 用户遇到"某站点封面全糊了"只能去关总闸 —— 那是把整个功能放弃掉。
///
/// 每行会显示判定来源（预设 / 插件 / 用户），改完立刻生效（[ContentGuard]
/// 内部清缓存），不需要重启。
///
/// 不用 Miuix 组件：本页通过根 Overlay 打开，没有 `MiuixTheme` 祖先，
/// 取色会回退到浅色默认值（深色下必穿帮）。
class _ManageSourceLevels extends StatefulWidget {
  const _ManageSourceLevels();

  @override
  State<_ManageSourceLevels> createState() => _ManageSourceLevelsState();
}

class _ManageSourceLevelsState extends State<_ManageSourceLevels> {
  /// 用户覆盖的三种取值 + 「跟随预设」。
  static const _choices = <ContentLevel?>[
    null,
    ContentLevel.safe,
    ContentLevel.mixed,
    ContentLevel.nsfw,
  ];

  String _choiceLabel(ContentLevel? level) {
    return switch (level) {
      null => "Follow preset".tl,
      ContentLevel.safe => "Safe".tl,
      ContentLevel.mixed => "Mixed (also has non-adult works)".tl,
      ContentLevel.nsfw => "Adult".tl,
    };
  }

  String _originLabel(ContentVerdict verdict) {
    if (verdict.origin == 'user:source') {
      return "Set by you".tl;
    }
    if (verdict.origin == 'plugin') {
      return "Declared by the source".tl;
    }
    if (verdict.origin == 'preset:unverified') {
      return "Preset (unverified)".tl;
    }
    return "Preset".tl;
  }

  @override
  Widget build(BuildContext context) {
    final sources = ComicSource.all().toList()
      ..sort((a, b) => a.name.compareTo(b.name));
    final cs = Theme.of(context).colorScheme;
    return PopUpWidgetScaffold(
      title: "Source ratings".tl,
      body: Column(
        children: [
          Padding(
            padding: const EdgeInsets.fromLTRB(16, 0, 16, 8),
            child: Text(
              "Ratings are guesses from a built-in table, not authoritative. "
                      "Correct any source below."
                  .tl,
              style: TextStyle(fontSize: 12, color: cs.onSurfaceVariant),
            ),
          ),
          Expanded(
            child: ListView.builder(
              itemCount: sources.length,
              itemBuilder: (context, index) {
                final source = sources[index];
                final verdict = ContentGuard.sourceVerdictOf(source.key);
                final override = ContentGuard.sourceOverride(source.key);
                final reason = ContentGuard.presetOf(source.key)?['reason'];
                final safe = verdict.level == ContentLevel.safe;
                return ListTile(
                  title: Text(source.name),
                  subtitle: Text(
                    "${_originLabel(verdict)} · "
                    "${_choiceLabel(override)}"
                    "${reason is String ? "\n$reason" : ""}",
                    style: TextStyle(
                      fontSize: 12,
                      color: cs.onSurfaceVariant,
                    ),
                  ),
                  isThreeLine: reason is String,
                  trailing: Text(
                    verdict.level.label.tl,
                    style: TextStyle(
                      color: safe ? cs.primary : cs.error,
                      fontWeight: FontWeight.w600,
                    ),
                  ),
                  onTap: () => _pick(source.key, source.name),
                );
              },
            ),
          ),
        ],
      ),
    );
  }

  void _pick(String sourceKey, String sourceName) {
    showDialog(
      context: App.rootContext,
      builder: (context) {
        return StatefulBuilder(
          builder: (context, setDialogState) {
            final override = ContentGuard.sourceOverride(sourceKey);
            final verdict = ContentGuard.sourceVerdictOf(sourceKey);
            return ContentDialog(
              title: sourceName,
              content: Column(
                mainAxisSize: MainAxisSize.min,
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  // 不用 RadioListTile：本项目用的 Flutter 版本里它的
                  // groupValue/onChanged 已废弃（要求 RadioGroup 祖先），
                  // 直接做成可点的行 + 选中勾更省事，也不会有版本兼容问题。
                  for (final choice in _choices)
                    ListTile(
                      dense: true,
                      contentPadding: EdgeInsets.zero,
                      title: Text(_choiceLabel(choice)),
                      trailing: override == choice
                          ? Icon(
                              Icons.check,
                              color: Theme.of(context).colorScheme.primary,
                            )
                          : null,
                      onTap: () {
                        ContentGuard.setSourceOverride(sourceKey, choice);
                        setDialogState(() {});
                        setState(() {});
                      },
                    ),
                  const SizedBox(height: 8),
                  Text(
                    "${"Current".tl}: ${verdict.level.label.tl} · "
                    "${_originLabel(verdict)}",
                    style: TextStyle(
                      fontSize: 12,
                      color: Theme.of(context).colorScheme.onSurfaceVariant,
                    ),
                  ),
                ],
              ),
              actions: [
                Button.filled(
                  onPressed: () => context.pop(),
                  child: Text("OK".tl),
                ),
              ],
            );
          },
        );
      },
    );
  }
}
