package com.venera.compose

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.venera.compose.feature.VeneraComposeApp
import com.venera.compose.feature.VeneraTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 应用入口（S0-3 之后只保留 Activity 本体）。
 *
 * 屏幕实现见 feature/ 包，导航栈见 feature/Navigation.kt。
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // S0-6：主题模式由 VeneraTheme 读取 VeneraPreferences.themeMode 统一管理
        setContent {
            VeneraTheme {
                VeneraComposeApp()
            }
        }
        if (intent.getBooleanExtra("run_engine_diagnostic", false)) {
            runEngineDiagnostic()
        }
    }

    private fun runEngineDiagnostic() {
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            val logTag = "ENGINE_TEST"
            android.util.Log.i(logTag, "========== S1 JS ENGINE END-TO-END TEST START ==========")
            val sourceManager = com.venera.compose.source.ComicSourceManager.getInstance(applicationContext)
            
            // 稍等引擎初始化与规则源释放加载
            kotlinx.coroutines.delay(2000)
            val sources = sourceManager.sourcesFlow.value
            android.util.Log.i(logTag, "Registered sources count: ${sources.size}")
            sources.forEach { s ->
                android.util.Log.i(logTag, "  - [${s.key}] ${s.name} v${s.version} (isJs: ${s is com.venera.compose.source.js.JsComicSource})")
            }

            // 测试 baozi 源
            val baoziSource = sources.find { it.key == "baozi" }
            if (baoziSource != null) {
                android.util.Log.i(logTag, "Testing baozi search('鬼灭之刃')...")
                val searchRes = baoziSource.search("鬼灭之刃", 1)
                if (searchRes.isSuccess) {
                    val list = searchRes.getOrNull().orEmpty()
                    android.util.Log.i(logTag, "baozi search success! Found ${list.size} comics")
                    if (list.isNotEmpty()) {
                        val first = list[0]
                        android.util.Log.i(logTag, "  First comic: id=${first.id}, title=${first.title}")
                        android.util.Log.i(logTag, "Testing baozi getComicDetails(${first.id})...")
                        val detailRes = baoziSource.getComicDetails(first.id)
                        if (detailRes.isSuccess) {
                            val detail = detailRes.getOrNull()!!
                            android.util.Log.i(logTag, "baozi details success! Chapters count: ${detail.chapters.size}")
                            if (detail.chapters.isNotEmpty()) {
                                val ch = detail.chapters[0]
                                android.util.Log.i(logTag, "  First chapter: id=${ch.id}, title=${ch.title}")
                                android.util.Log.i(logTag, "Testing baozi getChapterPages(${first.id}, ${ch.id})...")
                                val pageRes = baoziSource.getChapterPages(first.id, ch.id)
                                if (pageRes.isSuccess) {
                                    val pages = pageRes.getOrNull()!!
                                    android.util.Log.i(logTag, "baozi pages success! Total pages: ${pages.pages.size}, sample: ${pages.pages.firstOrNull()}")
                                } else {
                                    android.util.Log.e(logTag, "baozi pages failed: ${pageRes.exceptionOrNull()?.message}")
                                }
                            }
                        } else {
                            android.util.Log.e(logTag, "baozi details failed: ${detailRes.exceptionOrNull()?.message}")
                        }
                    }
                } else {
                    android.util.Log.e(logTag, "baozi search failed: ${searchRes.exceptionOrNull()?.message}")
                }
            }

            // 测试 copy_manga 源
            val copySource = sources.find { it.key == "copy_manga" }
            if (copySource != null) {
                android.util.Log.i(logTag, "Testing copy_manga search('进击的巨人')...")
                val searchRes = copySource.search("进击的巨人", 1)
                if (searchRes.isSuccess) {
                    val list = searchRes.getOrNull().orEmpty()
                    android.util.Log.i(logTag, "copy_manga search success! Found ${list.size} comics")
                    if (list.isNotEmpty()) {
                        val first = list[0]
                        android.util.Log.i(logTag, "  First comic: id=${first.id}, title=${first.title}")
                    }
                } else {
                    android.util.Log.e(logTag, "copy_manga search failed: ${searchRes.exceptionOrNull()?.message}")
                }
            }

            android.util.Log.i(logTag, "========== S1 JS ENGINE END-TO-END TEST FINISHED ==========")
        }
    }
}
