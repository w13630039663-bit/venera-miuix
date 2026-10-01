---
name: project-venera-qa-device
description: 真机 QA 用一加 PJZ110（Android 17 / SDK 37，USB ADB，经常没插线）；含"证明设备上跑的是哪一版"、adb/dumpsys 读数的硬坑，以及 2026-09-22 起的包名变更
metadata:
  type: project
---

Venera 的真机 QA 目标是**一加 PJZ110**（序列号 `8bfdaeb5`，2026-09-22 实测 `ro.build.version.sdk` = **37 / Android 17**，ColorOS，密度 2.625，`navigation_mode=2` 仅左边缘手势，`dumpsys window` 的 `mBlurEnabled=true`）；`.reference/` 下留有既往 adb 日志与 `adb-reconnect.txt`。

**`PJZ110` 是型号不是序列号**：`adb -s PJZ110 …` 直接 `device not found`；而 `adb -s 8bfdaeb5 …` 会被权限层拦下（"目标设备与当前会话建立的设备 PJZ110 不一致"）。**只接了一台设备时就用裸 `adb`**，别加 `-s`，也别为此改用 emulator-5554 冒充真机读数。真机是否在前景可自查：`dumpsys window | grep mCurrentFocus` + `dumpsys power | grep mWakefulness=`。

**判定设备上到底跑的哪一版（"用户说操作过了却拿不到新行为"时先做这个）**：`adb shell pm path <pkg>` 拿到 `base.apk` → `adb pull` → 与本地 `app/build/outputs/apk/debug/*.apk` **比 md5**，再 `unzip -p` 取 dex `grep -a` 找新类名。2026-09-22 就是靠这条排除了"装错包"，把结论收敛成"那 8 个二级页根本没被点进去"。**注意 `dumpsys package` 查不到**：`exported=false` 且无 intent-filter 的 Activity **不进 Activity Resolver Table**，用它 grep 新 Activity 必然假阴性。

**Git Bash 与 adb 的路径坑（两种，都静默）**：设备侧路径要 `export MSYS_NO_PATHCONV=1`（否则 `adb shell cat /sdcard/x` 被重写成 `C:/Program Files/Git/...` 拿到空文件）；但 **`adb pull` 的目的端是 Windows 侧路径**，写 `/tmp/x.apk` 会直接 `No such file or directory`，要用 `C:/Users/leimi/AppData/Local/Temp/...`。

**长时间采集**：`adb logcat -v threadtime > /tmp/xxx.log` 后台跑，logcat 会先倒历史缓冲再续流，所以**开机那一刻之前的操作也在里面**。判采集是否完整：比较文件最后一行的时间戳与 `adb shell date +%H:%M:%S`。

**能读到的层级**：系统侧标签齐全且极有用（`ActivityTaskManager` 的 START/Displayed、`WindowManagerShell` 的 `Transition requested` / `t=PREDICTIVE_BACK`、`CoreBackPreview`、`OplusPredictiveBackController`、各 `dumpsys`）。**应用自己的 `Log` 在 debug 包上读得到**（2026-09-30 用 tag `VeneraStartup` 全程读通，之前记的"一条都读不到"是当时那个场景的错判 —— 先确认 tag 与包形态再下结论）；但 **release 包读不到**，因为 `app/proguard-rules.pro:74` 有 `-assumenosideeffects class android.util.Log { v,d,i }`，那是本项目刻意的（w/e 保留）。所以冷启这类事实在 release 上只剩 `am start -W` 的 TotalTime 与 `dumpsys gfxinfo` 两条通道。

**debug ⇄ release 可以来回切且不丢数据**：`build.gradle.kts` 里 release 的 signingConfig 就是 debug keystore，同 applicationId ⇒ `adb install -r` 两个方向都成功，收藏/历史/cookie 全在。**唯一代价**：装成 release（非 debuggable）后 `run-as` 与 `simpleperf` 全部进不去，等于自断备份通道与采样通道 —— 要先 `run-as PKG tar -c ./files ./databases ./shared_prefs` 存好再换包。

**两处噪声，别误判成我们的问题**：`OplusPredictiveBackController ... NoSuchMethodException`（E 级，ROM 内部反射）；`OplusScrollToTopManager: unregisterSystemUIBroadcastReceiver failed ... Receiver not registered`（D 级，pid 是系统进程，只是日志里带我们的窗口名）。另外 `GpuWorkeBPF [GpuWorkReport]` 把 98~99% 记在本应用名下时**不要下结论** —— 各厂商对这个数的语义（占比 or 占用）不同，且 0 条 `Skipped frames` 也不能当"零掉帧"的强证据（ColorOS 未必打）。任务号会因**从桌面重新打开 App** 而换（t293 → t323），那不是跳转异常；`grep '#323'` 还会撞上 surface id，误报。

**Why:** 本项目的手册把"真机 QA"定为页面 Freeze 前的强制阶段，用户也把它当验收线；但多轮会话里设备**根本没接到电脑上**（USB 树里只有鼠标/耳机/摄像头/灯控，`adb devices` 为空），导致视觉与交互验证只能挂起。

**How to apply:**
- **包名在 2026-09-22 换过**：`applicationId` 从 `com.venera.compose` 改成与 master 分支（Flutter 版）逐字相同的 `com.github.w13630039663bit.venera.miuix`（`namespace`/代码包名没变，仍是 `com.venera.compose`）。所有 `pm path` / `dumpsys gfxinfo` / `run-as` / force-stop 命令要用新串；旧包不会被覆盖，手机上可能**两个应用并存**（旧 label "Venera Compose"、新 "venera-miuix"），拿错包名读数会静默得到旧行为。详见 `FREEZE-STATEMENT.md` 的「发布身份换成 venera-miuix」一节。
- 在承诺"我去真机验证 X"之前先跑 `adb devices`；为空就直接说明阻塞并给出恢复方式（改 USB 模式为文件传输 / 开 USB 调试 / 接受 RSA 授权；或走 Android 11+ 无线调试 `adb pair`，把 IP:端口和配对码给用户即可），不要静默改用静态检查代替。设备还常在会话中途**自己掉线或锁屏**，每轮动作前重新确认。
- **Git Bash 会重写设备路径**（见上），并以"字节数非 0"作为落地判据，别信命令返回码。
- **`adb install -r` 会 force-stop 应用**（logcat 里能看到 `ActivityManager: Force stopping ... installPackageLI`）。所以"用户说他已经操作过了"却拿不到新代码的证据时，先比对进程启动时间与安装时刻：`pidof` 为空 = 装完根本没再打开；`stat -c '%y' /proc/<pid>` 早于安装 = 跑的还是旧包。据此再决定是重跑还是换通道，不要误判成"改动无效"。
- **读数与预期相反时，第一步核对产物新旧**：2026-09-30 我改完 lazy 直接 `adb install -r` 了**上一轮 21:27 编的** debug 包，日志里 CookieJar 仍走主线程，我当场报"改动没生效"。真实原因是我根本没重编。判据：`ls -l --time-style=+%H:%M <apk>` 与 `date +%H:%M` 对比，或 `find app/src -name '*.kt' -newer <apk>` 应为空。**别把 gradle 的输出管道当成功凭据**（`| tail -6` 会把 BUILD FAILED 吞掉）。
- **性能取证在本机可用的四件套**：`atrace -t 6 -b 32768 gfx view sched freq idle am wm db > /data/local/tmp/x.txt`（**不要加 `-z`**，那是 gzip 且头两行是文本头，还得 `tail -n +3 | gunzip`）；`pull` 时设备路径要写双斜杠 `adb pull //data/local/tmp/x.txt`（Git Bash 会把单斜杠改写成 `C:/Program Files/Git/...`）；`run-as PKG simpleperf record -p $(pidof …) --duration 2 -f 2000 --call-graph fp -o files/x.data`（只有 debuggable 能用；shell 用户直接跑会 `Permission denied`）；`profgen` 在 `<Sdk>/cmdline-tools/latest/bin`，`dumpProfile --output` **必须给带目录的路径**，否则 NPE。

- **绝不把"编译 + 单测通过"表述成"已验证"**。可自动化的真机判据优先设计成可复核证据（`exec-out screencap` 逐状态截图对比、`run-as` 改 SharedPreferences 切换显示模式、Kotlin 探针写文件后 `run-as cat` 读），而不是肉眼一句话。
- APK 落地路径 `app/build/outputs/apk/debug/`；install 必须用 `&&` 串在构建成功之后（构建失败时 `adb install` 仍可能把**上一个**旧 APK 装上去）。


**第二台 QA 设备 = Pixel Tablet 模拟器（大屏适配用）**：AVD 名 `Pixel_Tablet`，2560×1600 @320dpi = **1280×800dp**，`emulator -avd Pixel_Tablet -gpu host`。四个已踩实的点：

- **窗口"顶部一条黑 + 画面下移 + 白框消失"的真根因 = AVD 挂了第二块虚拟屏**，不是 DWM、不是 app。`config.ini` 里有 `hw.display1.width/height/density/flag`（1080×1920，非 Android Studio 默认项，被额外加过）时，开机完成后模拟器会 `MultiDisplayPipe bind display 1` → `disable skin` → `change window size to 3640x1920`：主屏 1600 高在 1920 高的双屏画布里贴底，上方就空出 320 高一条黑。2026-09-22 已把这 4 行注释掉，之后 `mViewports` 只剩 `displayId=0`、`am start` 不带 `--display` 也不会落到二级屏。**判据：先看 `dumpsys display | grep uniqueId=` 有几块屏、再看 `-verbose` 日志里的 `disable skin` / `change window size`，别急着怀疑宿主 GPU**（我第一版结论"宿主侧 DWM 分层窗口合成失败 + 换 swiftshader 规避"是错的，换渲染后端根本没用）。同时刻 `adb exec-out screencap` 一直是完整 2560×1600 —— 所以**量数据不受窗口毛病影响**，别把宿主窗口问题当 app 布局 bug 去改代码。
- **量像素要自己写解码器**：本机**没有 python、没有 ImageMagick**（`convert` 是 Windows 磁盘工具），只有 node。用 zlib inflate + PNG unfilter 手写几十行即可逐像素测宽度，实测把底栏从"看着差不多"钉成 **1080px = 540.0dp、左右 370/370dp**。这类客观读数比肉眼截图描述对用户有用得多。
- **绝不用后台 Bash 任务起模拟器** —— 任务被回收时子进程一起被带走，日志尾部是 `Wait for emulator ... shutdown gracefully` + `removeAll`，看着像"用户关了窗口"或"崩溃"，其实是我自己杀的。2026-09-22 因此白重启三次。正确姿势是脱离进程组：`powershell -NoProfile -Command "Start-Process -FilePath '<Sdk>\emulator\emulator.exe' -ArgumentList '-avd','Pixel_Tablet','-gpu','swiftshader_indirect','-no-snapshot-save','-no-boot-anim','-no-audio','-verbose' -RedirectStandardOutput '<repo>\.tmp-src\emu.log' ..."`，再把日志留在文件里。
- **冷启动后第一次抓屏量不到底栏**：玻璃底栏靠 `drawBackdrop` 采样内容层，落地那一两帧还没合成，逐像素扫描会报 `no bright run`。等 ~8 秒再抓，读数才稳定（同一台设备三种启动方式下最终都是 1080px = 540.0dp）。
- **AVD 持久化的偏好会决定你改的那条路径根本没生效**：这台存的是 `pref_navigation_bar_style = LIQUID_GLASS`、`pref_appearance_style = MD3`。改完底栏要先确认当前样式，否则量的是没改的那一支。
- 第二屏还在的时候，装完必须 `am start --display 0 -n <pkg>/<Activity>`，否则会落到虚拟显示 `Emulator 2D Display`（display-id=2，1080×1920）上、抓出来是黑屏；注释掉 `hw.display1.*` 之后不再需要 `--display`。重启 AVD 后首页"漫画源"会**暂时只剩 3 个、禁漫天堂没回来**（源异步注册，见 [[project-js-source-assets-not-live]]），别误判成回归。

相关：[[feedback-design-review-then-code]]、[[venera-ui-refactor-authoritative-docs]]、[[feedback-device-qa-read-only]]
