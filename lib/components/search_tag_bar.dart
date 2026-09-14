part of 'components.dart';

/// 搜索栏**下方**独立一行的标签 chips —— 多 tag 搜索的已选条件。
///
/// 位置刻意放在搜索胶囊外面：胶囊本身高度固定、还要摆返回键/清除键/设置按钮，
/// 把 chips 塞进去会把输入行挤掉（试过一次，加完标签搜索内容就看不见了）。
/// 放在下面由列表自然撑高，既不用预先算行数，也不影响胶囊里任何元素。
///
/// 空列表时这一行只剩「＋ Add Tag」入口，保证任何时刻都能加标签。
class SearchTagBar extends StatelessWidget {
  const SearchTagBar({
    super.key,
    required this.labels,
    required this.onRemove,
    required this.onAdd,
  });

  final List<String> labels;

  /// 点 × 删除第 [index] 个标签（等退场动画播完才回调）。
  final void Function(int index) onRemove;

  /// 点「＋ Add」打开标签输入抽屉。
  final VoidCallback onAdd;

  // ==== 布局常量 ====
  static const double chipHeight = 32;
  static const double chipGap = 6;
  static const double rowGap = 6;

  /// chip 内除文字外的固定宽度（左右内边距 + 文字与 × 之间的间距 + × 热区）。
  static const double chipExtraWidth = 13 + 4 + 24 + 2;

  static const TextStyle _textStyle = TextStyle(
    fontSize: 14,
    fontWeight: FontWeight.w500,
    height: 1,
  );

  /// 与搜索框的竖向间距（12）比下方（4）大：这一行是搜索框的从属内容，
  /// 拉开一点才不会被看成贴在胶囊上；下方接的源标签行/列表自带留白，
  /// 再叠加就会显得空。
  static const double padTop = 12;
  static const double padBottom = 4;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.fromLTRB(16, padTop, 16, padBottom),
      child: LayoutBuilder(
        builder: (context, constraints) {
          // 超长标签名要能省略号截断，否则 Wrap 会把整行顶出屏幕。
          final maxLabelWidth = constraints.maxWidth - chipExtraWidth;
          return Wrap(
            spacing: chipGap,
            runSpacing: rowGap,
            children: [
              for (var i = 0; i < labels.length; i++)
                _TagChip(
                  key: ValueKey(labels[i]),
                  label: labels[i],
                  maxLabelWidth: maxLabelWidth,
                  onRemove: () => onRemove(i),
                ),
              _AddTagChip(onTap: onAdd),
            ],
          );
        },
      ),
    );
  }
}

/// 单个标签胶囊：文字 + × 删除钮，带弹入/缩退动画。
class _TagChip extends StatefulWidget {
  const _TagChip({
    super.key,
    required this.label,
    required this.maxLabelWidth,
    required this.onRemove,
  });

  final String label;

  final double maxLabelWidth;

  final VoidCallback onRemove;

  @override
  State<_TagChip> createState() => _TagChipState();
}

class _TagChipState extends State<_TagChip> {
  static const _enterDuration = Duration(milliseconds: 260);
  static const _leaveDuration = Duration(milliseconds: 160);

  /// 首帧从「缩小 + 透明」起步，下一帧再切到正常——AnimatedScale /
  /// AnimatedOpacity 会自动补出弹入过程。
  double _scale = 0.6;
  double _opacity = 0;

  bool _leaving = false;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (mounted) {
        setState(() {
          _scale = 1;
          _opacity = 1;
        });
      }
    });
  }

  Future<void> _remove() async {
    if (_leaving) return;
    // 先播退场动画，播完再让父级移除——父级一移除本 widget 立刻销毁，
    // 动画就看不见了。
    setState(() {
      _leaving = true;
      _scale = 0.6;
      _opacity = 0;
    });
    await Future.delayed(_leaveDuration);
    if (mounted) {
      widget.onRemove();
    }
  }

  @override
  Widget build(BuildContext context) {
    // 用 MD3 colorScheme 而不是 MiuixTheme.of：本组件可能落在没有 MiuixTheme
    // 祖先的 context 上，那时 MiuixTheme.of 会回退浅色默认值，深色模式下穿帮。
    final fg = Theme.of(context).colorScheme.primary;
    return AnimatedScale(
      scale: _scale,
      duration: _leaving ? _leaveDuration : _enterDuration,
      curve: _leaving ? Curves.easeIn : Curves.easeOutBack,
      alignment: Alignment.centerLeft,
      child: AnimatedOpacity(
        opacity: _opacity,
        duration: _leaving ? _leaveDuration : _enterDuration,
        child: Container(
          height: SearchTagBar.chipHeight,
          padding: const EdgeInsets.only(left: 13, right: 2),
          decoration: BoxDecoration(
            color: fg.withValues(alpha: 0.13),
            borderRadius: BorderRadius.circular(SearchTagBar.chipHeight / 2),
          ),
          child: Row(
            mainAxisSize: MainAxisSize.min,
            children: [
              ConstrainedBox(
                constraints: BoxConstraints(
                  maxWidth: widget.maxLabelWidth.clamp(48, 1000),
                ),
                child: Text(
                  widget.label,
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: SearchTagBar._textStyle.copyWith(color: fg),
                ),
              ),
              const SizedBox(width: 4),
              _RemoveTagButton(onTap: _remove, color: fg),
            ],
          ),
        ),
      ),
    );
  }
}

/// 「＋ Add」胶囊：与标签 chip 同高，是标签行的固定入口（空列表时也在）。
class _AddTagChip extends StatelessWidget {
  const _AddTagChip({required this.onTap});

  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    final radius = BorderRadius.circular(SearchTagBar.chipHeight / 2);
    return Material(
      color: scheme.surfaceContainerHigh,
      borderRadius: radius,
      clipBehavior: Clip.antiAlias,
      child: InkWell(
        onTap: onTap,
        child: Container(
          height: SearchTagBar.chipHeight,
          padding: const EdgeInsets.symmetric(horizontal: 12),
          child: Row(
            mainAxisSize: MainAxisSize.min,
            children: [
              Icon(Icons.add, size: 16, color: scheme.onSurfaceVariant),
              const SizedBox(width: 5),
              Text(
                "Add Tag".tl,
                style: SearchTagBar._textStyle
                    .copyWith(color: scheme.onSurfaceVariant),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class _RemoveTagButton extends StatelessWidget {
  const _RemoveTagButton({required this.onTap, required this.color});

  final VoidCallback onTap;

  final Color color;

  @override
  Widget build(BuildContext context) {
    return SizedBox(
      width: 24,
      height: 24,
      child: Material(
        color: Colors.transparent,
        shape: const CircleBorder(),
        clipBehavior: Clip.antiAlias,
        child: InkWell(
          onTap: onTap,
          child: Icon(Icons.close, size: 14, color: color),
        ),
      ),
    );
  }
}

/// 弹出「添加标签」底部抽屉，返回输入的标签内容；取消/关闭返回 null。
///
/// 用 Flutter 原生 [showModalBottomSheet] 而不是 venera 的 [showSideBar]——后者
/// 是从**右侧**滑入、宽度自适应的侧栏，形态不是底部抽屉。
/// `useRootNavigator: true` 让抽屉成为根 Navigator 的顶层路由：按返回键先关
/// 抽屉，也不会被搜索页结果态挂在根路由上的 `contentBackOverride` 抢走。
Future<String?> showAddTagSheet(BuildContext context) {
  return showModalBottomSheet<String>(
    context: App.rootContext,
    useRootNavigator: true,
    isScrollControlled: true,
    backgroundColor: Colors.transparent,
    builder: (ctx) => withMiuixTheme(
      ctx,
      Padding(
        // 键盘弹起时把抽屉顶上去（原型里那个 --kb 变量就是这个作用）。
        padding: EdgeInsets.only(
          bottom: MediaQuery.of(ctx).viewInsets.bottom,
        ),
        child: const _AddTagSheet(),
      ),
    ),
  );
}

class _AddTagSheet extends StatefulWidget {
  const _AddTagSheet();

  @override
  State<_AddTagSheet> createState() => _AddTagSheetState();
}

class _AddTagSheetState extends State<_AddTagSheet> {
  final controller = TextEditingController();

  @override
  void dispose() {
    controller.dispose();
    super.dispose();
  }

  void _submit() => Navigator.of(context).pop(controller.text);

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    return Container(
      decoration: BoxDecoration(
        color: scheme.surface,
        borderRadius: const BorderRadius.vertical(top: Radius.circular(26)),
      ),
      padding: const EdgeInsets.fromLTRB(20, 10, 20, 20),
      child: SafeArea(
        top: false,
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            // 拖拽把手（原型里的 .sheet-handle）
            Center(
              child: Container(
                width: 40,
                height: 4,
                decoration: BoxDecoration(
                  color: scheme.outlineVariant,
                  borderRadius: BorderRadius.circular(2),
                ),
              ),
            ),
            const SizedBox(height: 18),
            Text(
              "Add Tag".tl,
              style: const TextStyle(fontSize: 17, fontWeight: FontWeight.w600),
            ),
            const SizedBox(height: 16),
            TextField(
              controller: controller,
              autofocus: true,
              textInputAction: TextInputAction.done,
              onSubmitted: (_) => _submit(),
              decoration: InputDecoration(
                hintText: "Tag".tl,
                filled: true,
                fillColor: scheme.surfaceContainerHigh,
                border: OutlineInputBorder(
                  borderRadius: BorderRadius.circular(14),
                  borderSide: BorderSide.none,
                ),
                contentPadding: const EdgeInsets.symmetric(
                  horizontal: 16,
                  vertical: 16,
                ),
              ),
            ),
            const SizedBox(height: 18),
            Row(
              children: [
                Expanded(
                  child: _AddTagSheetButton(
                    text: "Cancel".tl,
                    onTap: () => Navigator.of(context).pop(),
                    background: scheme.surfaceContainerHigh,
                    foreground: scheme.onSurfaceVariant,
                  ),
                ),
                const SizedBox(width: 10),
                Expanded(
                  child: _AddTagSheetButton(
                    text: "Add".tl,
                    onTap: _submit,
                    background: scheme.primary,
                    foreground: scheme.onPrimary,
                  ),
                ),
              ],
            ),
          ],
        ),
      ),
    );
  }
}

class _AddTagSheetButton extends StatelessWidget {
  const _AddTagSheetButton({
    required this.text,
    required this.onTap,
    required this.background,
    required this.foreground,
  });

  final String text;

  final VoidCallback onTap;

  final Color background;

  final Color foreground;

  @override
  Widget build(BuildContext context) {
    final radius = BorderRadius.circular(14);
    return Material(
      color: background,
      borderRadius: radius,
      child: InkWell(
        borderRadius: radius,
        onTap: onTap,
        child: SizedBox(
          height: 50,
          child: Center(
            child: Text(
              text,
              style: TextStyle(
                fontSize: 16,
                fontWeight: FontWeight.w500,
                color: foreground,
              ),
            ),
          ),
        ),
      ),
    );
  }
}
