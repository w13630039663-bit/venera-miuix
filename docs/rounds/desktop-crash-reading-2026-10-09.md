# 桌面第五十三批 批 53 · 崩了要念得出是谁、为什么（2026-10-09）

## 1. 这一格在框架上的位置

本仓已经两次"崩了但读不出来"：批 47 那颗 `ImageLoader` 缺 swing dispatcher 的崩溃，屏上是一枚**空白
Error 框**（Compose 内容不进 UIA 树），成因只在 stdout 那一大段裸 stack 里；手册 §0.1 也记着
"崩溃的读数不在屏上"。而取证那一跑是在几十行 `D_` 读数里挑 —— 裸 stack 既没归属（哪颗线程、第几笔）、
也没法 `grep`。所以这一批不添功能，只把**崩了之后留下什么**这一层补上。

## 2. 落地

| 件 | 做什么 |
|---|---|
| `desktop/src/main/kotlin/com/venera/desktop/DesktopCrashReading.kt`（新） | 纯函数 `desktopCrashReading(线程, throwable, 第几笔)` 出一行 `D_异常 线程=… 次=… 因=类型: 消息 链=N 栈=文件:行`；`desktopScrubSecrets` 把 query 里的凭据值打成 `***`（键名留）；`installDesktopCrashHandler` 装在 `main()` 第一件事 |
| `ForensicFlags.crash` + `:desktop:app` 的 `-Pcrash=` | 取证种子 `--crash=thread\|edt`：各走一条线程路，故意崩一笔 |
| `VeneraDesktop.kt` | 装 handler（早于任何取数与开窗）+ 那一枚种子的消费点 |

`app/` 零改动、零新依赖。

## 3. 四条量出来的（不是推断）

- **非 EDT 那一档通**：`D_异常 线程=取证-非EDT 次=1 因=IllegalStateException: 取证种子：非 EDT 那一笔 链=0 栈=VeneraDesktop.kt:400`。
- **EDT 那一档也通**：`线程=AWT-EventQueue-0 次=1 …` ⇒ Compose Desktop 1.12 的事件派发线程**也走**
  `Thread.setDefaultUncaughtExceptionHandler`，不需要 `sun.awt.exception.handler` 那一枚私有钩子
  （本来准备加，量完就不加了）。
- **但屏上那半件事与批 47 不是一条路**：这一跑崩完之后**窗口照旧、墙照画**，`relatedTransientWindows`
  空、没有 Error 框 ⇒ 批 47 那枚空白框来自另一条路（协程 dispatcher 缺失在构造期抛），别把两句混成一句。
- **替掉 JVM 默认 handler 会连整段 stack 一起吞掉**：第一版只念那一行，stdout 里 `Exception in thread`
  那一整段整个不见了（读数从 2 行变 1 行）。正解是念完那一行**再 `printStackTrace()`** ——
  这一格要的是"两样都有"，不是换个更好看的东西没有。

## 4. 判据（+9 颗，`DesktopCrashReadingTest`）

归属四枚与顶帧 / 多行压成一行（且两行内容都留着）/ 超长截断且明说截断、短消息不截 /
**凭据不进读数**（反搜键、Gelbooru 的 `api_key`+`user_id` 各喂一条，另加一条"没键的消息不许被顺手抹平"的对照）/
链深两档（0 与 3）/ 拿不到顶帧念 `栈=没有` / 消息为 null 也出一行（读数件自己绝不许抛）/
入口只装一次且**装在开窗之前** / `-Pcrash=` 三跳（构建脚本 → `ForensicFlags` → 消费点）都在。

扰动没另做：这九颗里 ③④⑥ 三颗本身就是"喂两种输入"的形状（长与短、带键与不带键、有栈与没栈），
恒真与恒假两边都被钉住。

⚠️ 写判据时红过一次、红在尺子自己身上：锚点写成 `Thread.setDefaultUncaughtExceptionHandler(`
带尾括号，而那一行是 Kotlin 尾随 lambda（`{ thread, error ->`，**没有括号**）⇒ 数出 0 颗。
真形状拼错，与 §0.1 坑 ① 同一族。

## 5. 数字

**709 判据 / 92 类 / 0 失败**（三方对账：XML 属性 = `<testcase>` = 源码裸 `@Test` = 709）；
`desktop/src/main` 80 颗 / 15,744 行；jar **4,169,631 B**（交付包 `_qa/desktop-exe-2026-10-09-b51/` 已同步）；
起窗两次（thread / edt 各一），关窗后残留窗 **0**。
