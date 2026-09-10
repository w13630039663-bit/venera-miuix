import 'dart:async';

import 'package:display_mode/display_mode.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';
import 'package:flutter_saf/flutter_saf.dart';
import 'package:rhttp/rhttp.dart';
import 'package:venera/foundation/app.dart';
import 'package:venera/foundation/cache_manager.dart';
import 'package:venera/foundation/comic_source/comic_source.dart';
import 'package:venera/foundation/content_guard.dart';
import 'package:venera/foundation/js_engine.dart';
import 'package:venera/foundation/log.dart';
import 'package:venera/network/cookie_jar.dart';
import 'package:venera/pages/comic_source_page.dart';
import 'package:venera/pages/follow_updates_page.dart';
import 'package:venera/pages/settings/settings_page.dart';
import 'package:venera/utils/app_links.dart';
import 'package:venera/utils/handle_text_share.dart';
import 'package:venera/utils/opencc.dart';
import 'package:venera/utils/tags_translation.dart';
import 'package:venera/utils/translations.dart';
import 'foundation/appdata.dart';

extension _FutureInit<T> on Future<T> {
  /// Prevent unhandled exception
  ///
  /// A unhandled exception occurred in init() will cause the app to crash.
  Future<void> wait() async {
    try {
      await this;
    } catch (e, s) {
      Log.error("init", "$e\n$s");
    }
  }
}

Future<void> init() async {
  await App.init().wait();
  // 顺序上一句必须等：App.init 定出 cachePath/dataPath，下面的初始化全靠它。
  // 之后这些互相之间没有依赖，全部并行 —— 串起来等待会让启动时长变成
  // 各项之和（读 tags.json / opencc 字典 / 起 JS 引擎都是几十毫秒级）。
  try {
    var futures = [
      // CookieJar 原先在批外单独 await，白等一个串行往返；移进来并行，
      // 顺带纳入 try —— 它失败时也只会记日志，不该让启动直接崩。
      SingleInstanceCookieJar.createInstance(),
      Rhttp.init(),
      App.initComponents(),
      SAFTaskWorker().init().wait(),
      AppTranslation.init().wait(),
      TagsTranslation.readData().wait(),
      JsEngine().init().wait(),
      ComicSourceManager().init().wait(),
      // 内容分级预设表：必须早于任何列表渲染（判定层的第一跳）。
      ContentGuard.init(),
      OpenCC.init(),
    ];
    await Future.wait(futures);
  } catch (e, s) {
    Log.error("init", "$e\n$s");
  }
  CacheManager().setLimitSize(appdata.settings['cacheSize']);
  _checkOldConfigs();
  if (App.isAndroid) {
    // 恢复「屏幕防窥」。FLAG_SECURE 是 window 级状态，不随进程存活，
    // 必须在每次启动时重新设置 —— 这正是设置项副标题写「需重启生效」的原因。
    if (appdata.settings['secureWindow'] == true) {
      const MethodChannel('venera/method_channel')
          .invokeMethod('setSecureWindow', {'set': true});
    }
    handleLinks();
    handleTextShare();
    // 高刷新率申请不阻塞首帧：它只是一次 platform channel 调用，结果是后续
    // 帧才受益。放 await 会让启动多等一个往返（冷启动时更明显）。
    unawaited(
      FlutterDisplayMode.setHighRefreshRate().then<void>(
        (_) {},
        onError: (Object e) =>
            Log.error("Display Mode", "Failed to set high refresh rate: $e"),
      ),
    );
  }
  FlutterError.onError = (details) {
    Log.error("Unhandled Exception", "${details.exception}\n${details.stack}");
  };
  if (App.isWindows) {
    // Report to the monitor thread that the app is running
    // https://github.com/venera-app/venera/issues/343
    Timer.periodic(const Duration(seconds: 1), (_) {
      const methodChannel = MethodChannel('venera/method_channel');
      methodChannel.invokeMethod("heartBeat");
    });
  }
}

void _checkOldConfigs() {
  if (appdata.settings['searchSources'] == null) {
    appdata.settings['searchSources'] = ComicSource.all()
        .where((e) => e.searchPageData != null)
        .map((e) => e.key)
        .toList();
  }

  if (appdata.implicitData['webdavAutoSync'] == null) {
    var webdavConfig = appdata.settings['webdav'];
    if (webdavConfig is List &&
        webdavConfig.length == 3 &&
        webdavConfig.whereType<String>().length == 3) {
      appdata.implicitData['webdavAutoSync'] = true;
    } else {
      appdata.implicitData['webdavAutoSync'] = false;
    }
    appdata.writeImplicitData();
  }

  if (appdata.settings['comicSourceListUrl'].toString().contains("git.nyne.dev")) {
    // migrate to jsdelivr cdn
    appdata.settings['comicSourceListUrl'] = "https://cdn.jsdelivr.net/gh/venera-app/venera-configs@main/index.json";
    appdata.saveData();
  }
}

Future<void> _checkAppUpdates() async {
  var lastCheck = appdata.implicitData['lastCheckUpdate'] ?? 0;
  var now = DateTime.now().millisecondsSinceEpoch;
  if (now - lastCheck < 24 * 60 * 60 * 1000) {
    return;
  }
  appdata.implicitData['lastCheckUpdate'] = now;
  appdata.writeImplicitData();
  ComicSourcePage.checkComicSourceUpdate();
  if (appdata.settings['checkUpdateOnStart']) {
    await checkUpdateUi(false, true);
  }
}

void checkUpdates() {
  _checkAppUpdates();
  FollowUpdatesService.initChecker();
}
