package com.venera.desktop.gallery.data

/**
 * 阅读那一屏的模式档位 —— 桌面自持的一份同轨件（第八十七批 B 路）。
 *
 * ## 为什么这里是一份"自持"而不是转交
 *
 * 对岸那颗同名的在 `reader/ComicPageSource.kt`，而它零 Android 依赖（整颗只 import `java.io.File`），
 * 看上去**本该**直接 srcDir 进桌面编译面。挡住它的不是编译，是受保域里的一行：
 * `app/src/test/java/com/venera/compose/desktop/DesktopNoAndroidImportTest.kt` 那条 UI 禁串把
 * **整个** `reader/` 目录算作 Android UI 面，于是"桌面 import 那颗纯判据件"在对岸那把尺子上红。
 * 本批不动 `app/`，所以走 B 路：桌面留一份同轨件，而**键值表、归一别名表、跨页算式**三处
 * 都由尺子从对岸源码文本现取再比（对岸改了桌面会红）。
 *
 * ⚠️ 这一条路的代价写清楚：三处之外的东西没人对齐。对岸新增第四处口径（比如给档位加一枚
 * 屏上要念的字段）时尺子看不见，只有点头改走 A 路（把 `reader/` 那两颗纯件请进编译面）才根治。
 *
 * ## 五档都在，而屏上只摆三档
 *
 * 档位表整颗留着（键与档名逐字照对岸），因为**盘上那枚键存的就是这一串**，读档必须认得全；
 * 而"摆哪几档上屏"是另一件事，归 `DESKTOP_READER_MODE_CHOICES` 管 —— 本批做得了的只有三档，
 * 摆一枚按不动的档位上去就是本仓最忌的假开关。
 *
 * 对岸那两颗用不上的字段（`icon` 与 `description`）没有跟着搬：桌面这一屏不念它们，
 * 搬过来只是把对岸的表抄全，而不是把对岸的表**对齐**。
 */
enum class DesktopReaderMode(val key: String, val label: String) {
    VERTICAL_CONTINUOUS("VERTICAL_CONTINUOUS", "条漫·连续"),
    HORIZONTAL_RTL("HORIZONTAL_RTL", "日漫·右至左"),
    HORIZONTAL_LTR("HORIZONTAL_LTR", "美漫·左至右"),
    HORIZONTAL_CONTINUOUS("HORIZONTAL_CONTINUOUS", "横向·连续"),
    DOUBLE_PAGE("DOUBLE_PAGE", "对开·双页");

    companion object {
        /**
         * 盘上那一串 → 档位；认不出折回纵向连续那一档（对岸同一条归一规矩，逐字照它的别名表）。
         *
         * ⚠️ 这一颗**只给读档用**。取证参数不借它：借了就会出现"递了个错字而屏上静静回到默认档"，
         * 那一格由 `ForensicFlags.readMode` 自己念"认不出是哪一档"。
         */
        fun fromKey(key: String?): DesktopReaderMode = when (key?.uppercase()) {
            "VERTICAL_CONTINUOUS", "VERTICAL" -> VERTICAL_CONTINUOUS
            "HORIZONTAL_RTL", "RTL", "HORIZONTAL", "HORIZONTAL_PAGE" -> HORIZONTAL_RTL
            "HORIZONTAL_LTR", "LTR" -> HORIZONTAL_LTR
            "HORIZONTAL_CONTINUOUS", "H_CONTINUOUS" -> HORIZONTAL_CONTINUOUS
            "DOUBLE_PAGE", "DOUBLE" -> DOUBLE_PAGE
            else -> VERTICAL_CONTINUOUS
        }
    }
}
