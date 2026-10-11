package com.venera.desktop

import com.venera.desktop.gallery.data.DesktopDictionaryPresence

/**
 * 启动那一行的读数件（第五十八批）：**这台机器给不给**与**这个包里有没有**。
 *
 * 两件事从前只有屏上那一种读法：剪贴板拿不到时「复制图片」不摆（批 54），词典没进包时译名静默走原文
 * （批 40 那一族的前科）。两种在日志里都是一个字都没有，事后翻不动。
 *
 * 措辞只住这一颗件：集成方拼第二份就是把同一件事说两遍（将来只改一处，另一处留着假话）。
 */
internal fun desktopCapabilityReading(
    clipboard: Boolean,
    dictionary: DesktopDictionaryPresence,
): String =
    "D_能力 剪贴板=" + desktopClipboardFragment(clipboard) + " 词典=" + desktopDictionaryFragment(dictionary)

/**
 * 这一枚量的是 `GraphicsEnvironment.isHeadless()` —— **那条 API 在不在**，不是"写一定成"。
 *
 * ⚠️ 本批活体改判：第一版这句念「可写」，而同一台机器上批 54 量过真写被 OS 拒（手册 坑 ㊛，八次），
 * 那句"可写"就是假话 —— 措辞因此退到量到的那一层。真到写那一跳失败时，出声的是批 54 那颗
 * `desktopCopyImageRefusalReading`（它念的是结果，这一枚念的是通路）。
 */
private fun desktopClipboardFragment(clipboard: Boolean): String =
    if (clipboard) "有通路" else "无通路（headless）"

/** 三态三句：「N行」/「包里没有」/「数不出（型=…）」。「没取到 / 没有 / 没接」不许压成同一句哑巴。 */
private fun desktopDictionaryFragment(dictionary: DesktopDictionaryPresence): String = when (dictionary) {
    is DesktopDictionaryPresence.InPackage -> "${dictionary.rows}行"
    DesktopDictionaryPresence.NotInPackage -> "包里没有"
    // 只带类型不带消息：那条消息里可能写着 classpath 的整段路径，而这一行会进日志。
    is DesktopDictionaryPresence.Unreadable -> "数不出（型=${desktopFailureType(dictionary.error)}）"
}
