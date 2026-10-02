package com.venera.desktop.platform

import com.venera.compose.data.platform.PathProvider
import java.io.File

/** 只给测试用：dataRoot/cacheRoot 都指到调用方给的临时目录，语义与 DesktopPaths 同构。 */
class FakePaths(override val dataRoot: File, override val cacheRoot: File = File(dataRoot, "cache")) : PathProvider
