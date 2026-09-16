package com.venera.compose.feature.sourcemanage

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.venera.compose.data.network.VeneraNetworkClient
import com.venera.compose.source.ComicSource
import com.venera.compose.source.ComicSourceManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Request

class ComicSourceViewModel(application: Application) : AndroidViewModel(application) {

    private val sourceManager = ComicSourceManager.getInstance(application)
    private val networkClient = VeneraNetworkClient.getInstance(application)
    private val gson = Gson()

    val sources: StateFlow<List<ComicSource>> = sourceManager.sourcesFlow
    val activeSourceKey: StateFlow<String> = sourceManager.activeSourceKey
    val latencyMap: StateFlow<Map<String, Long>> = sourceManager.latencyMapFlow

    private val _repoItems = MutableStateFlow<List<RepoSourceItem>>(emptyList())
    val repoItems: StateFlow<List<RepoSourceItem>> = _repoItems.asStateFlow()

    private val _isLoadingRepo = MutableStateFlow(false)
    val isLoadingRepo: StateFlow<Boolean> = _isLoadingRepo.asStateFlow()

    private val _installingKeys = MutableStateFlow<Set<String>>(emptySet())
    val installingKeys: StateFlow<Set<String>> = _installingKeys.asStateFlow()

    private val _messageEvent = MutableSharedFlow<String>()
    val messageEvent: SharedFlow<String> = _messageEvent.asSharedFlow()

    private val _configBundles = MutableStateFlow<Map<String, SourceConfigBundle>>(emptyMap())
    val configBundles: StateFlow<Map<String, SourceConfigBundle>> = _configBundles.asStateFlow()

    val defaultRepoUrl = "https://cdn.jsdelivr.net/gh/venera-app/venera-configs@main/index.json"

    init {
        loadRepo(defaultRepoUrl)
        viewModelScope.launch {
            sources.collect { list ->
                refreshConfigBundles(list)
            }
        }
    }

    fun refreshConfigBundles(currentSources: List<ComicSource> = sources.value) {
        viewModelScope.launch(Dispatchers.IO) {
            val map = mutableMapOf<String, SourceConfigBundle>()
            for (src in currentSources) {
                val settings = runCatching { src.getSettings() }.getOrDefault(emptyList())
                val account = runCatching { src.getAccountInfo() }.getOrDefault(SourceAccountInfo())
                map[src.key] = SourceConfigBundle(
                    sourceKey = src.key,
                    sourceName = src.name,
                    version = src.version,
                    isJsSource = src is com.venera.compose.source.js.JsComicSource,
                    settings = settings,
                    accountInfo = account
                )
            }
            _configBundles.value = map
        }
    }

    fun updateSetting(sourceKey: String, settingKey: String, value: Any) {
        val src = sourceManager.getSource(sourceKey) ?: return
        src.saveSetting(settingKey, value)
        refreshConfigBundles()
        viewModelScope.launch {
            _messageEvent.emit("已保存设置")
        }
    }

    fun executeCallback(sourceKey: String, settingKey: String) {
        val src = sourceManager.getSource(sourceKey) ?: return
        viewModelScope.launch {
            _messageEvent.emit("正在执行操作...")
            val res = withContext(Dispatchers.IO) {
                src.executeSettingCallback(settingKey)
            }
            if (res.isSuccess) {
                _messageEvent.emit("执行完成")
            } else {
                _messageEvent.emit("执行失败: ${res.exceptionOrNull()?.message}")
            }
            refreshConfigBundles()
        }
    }

    fun login(sourceKey: String, user: String, pass: String, onComplete: (Boolean, String?) -> Unit) {
        val src = sourceManager.getSource(sourceKey) ?: return
        viewModelScope.launch {
            val res = withContext(Dispatchers.IO) {
                src.login(user, pass)
            }
            if (res.isSuccess) {
                _messageEvent.emit("登录成功")
                refreshConfigBundles()
                onComplete(true, null)
            } else {
                val err = res.exceptionOrNull()?.message ?: "登录失败"
                _messageEvent.emit("登录失败: $err")
                onComplete(false, err)
            }
        }
    }

    fun relogin(sourceKey: String) {
        val src = sourceManager.getSource(sourceKey) ?: return
        viewModelScope.launch {
            _messageEvent.emit("正在重新登录...")
            val res = withContext(Dispatchers.IO) {
                src.relogin()
            }
            if (res.isSuccess) {
                _messageEvent.emit("重新登录成功")
            } else {
                _messageEvent.emit("重新登录失败: ${res.exceptionOrNull()?.message}")
            }
            refreshConfigBundles()
        }
    }

    fun logout(sourceKey: String) {
        val src = sourceManager.getSource(sourceKey) ?: return
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                src.logout()
            }
            _messageEvent.emit("已注销登录")
            refreshConfigBundles()
        }
    }

    fun reloadSource(sourceKey: String) {
        viewModelScope.launch(Dispatchers.IO) {
            sourceManager.loadInstalledJsSources()
            refreshConfigBundles()
            _messageEvent.emit("已重新加载源配置")
        }
    }

    fun refreshPings() {
        sourceManager.refreshPings()
    }

    fun setActiveSource(key: String) {
        sourceManager.setActiveSource(key)
        viewModelScope.launch {
            _messageEvent.emit("已切换活跃源为: $key")
        }
    }

    fun deleteSource(key: String) {
        val success = sourceManager.deleteJsSource(key)
        viewModelScope.launch {
            if (success) {
                _messageEvent.emit("已删除漫画源: $key")
            } else {
                _messageEvent.emit("内置漫画源不支持删除")
            }
        }
    }

    fun loadRepo(repoUrl: String = defaultRepoUrl) {
        viewModelScope.launch {
            _isLoadingRepo.value = true
            val items = withContext(Dispatchers.IO) {
                try {
                    val req = Request.Builder().url(repoUrl).build()
                    val response = networkClient.okHttpClient.newCall(req).execute()
                    val jsonStr = response.body?.string()
                    if (response.isSuccessful && !jsonStr.isNullOrBlank()) {
                        val type = object : TypeToken<List<RepoSourceItem>>() {}.type
                        gson.fromJson<List<RepoSourceItem>>(jsonStr, type) ?: emptyList()
                    } else {
                        loadBundledRepo()
                    }
                } catch (e: Exception) {
                    loadBundledRepo()
                }
            }
            _repoItems.value = items
            _isLoadingRepo.value = false
        }
    }

    private fun loadBundledRepo(): List<RepoSourceItem> {
        return try {
            val jsonStr = getApplication<Application>().assets.open("sources/index.json")
                .bufferedReader().use { it.readText() }
            val type = object : TypeToken<List<RepoSourceItem>>() {}.type
            gson.fromJson<List<RepoSourceItem>>(jsonStr, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun installFromRepo(item: RepoSourceItem, repoUrl: String = defaultRepoUrl) {
        viewModelScope.launch {
            _installingKeys.value = _installingKeys.value + item.key
            val result = withContext(Dispatchers.IO) {
                var jsContent: String? = null
                val fileUrl = repoUrl.substringBeforeLast('/') + "/" + item.fileName
                try {
                    val req = Request.Builder().url(fileUrl).build()
                    val resp = networkClient.okHttpClient.newCall(req).execute()
                    if (resp.isSuccessful) {
                        jsContent = resp.body?.string()
                    }
                } catch (e: Exception) {
                    // Fallback to bundled asset if online download fails
                }

                if (jsContent.isNullOrBlank()) {
                    jsContent = try {
                        getApplication<Application>().assets.open("sources/${item.fileName}")
                            .bufferedReader().use { it.readText() }
                    } catch (e: Exception) {
                        null
                    }
                }

                if (jsContent.isNullOrBlank()) {
                    Result.failure(Exception("无法获取脚本内容"))
                } else {
                    sourceManager.installJsSource(jsContent)
                }
            }
            _installingKeys.value = _installingKeys.value - item.key

            if (result.isSuccess) {
                _messageEvent.emit("成功安装: ${item.name} (${item.version})")
            } else {
                _messageEvent.emit("安装失败: ${result.exceptionOrNull()?.message}")
            }
        }
    }

    fun installFromUrl(url: String) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                try {
                    val req = Request.Builder().url(url).build()
                    val resp = networkClient.okHttpClient.newCall(req).execute()
                    val jsContent = resp.body?.string()
                    if (resp.isSuccessful && !jsContent.isNullOrBlank()) {
                        sourceManager.installJsSource(jsContent)
                    } else {
                        Result.failure(Exception("HTTP ${resp.code}: 获取脚本失败"))
                    }
                } catch (e: Exception) {
                    Result.failure(e)
                }
            }
            if (result.isSuccess) {
                val src = result.getOrNull()
                _messageEvent.emit("成功从链接安装: ${src?.name ?: url}")
            } else {
                _messageEvent.emit("安装失败: ${result.exceptionOrNull()?.message}")
            }
        }
    }

    fun installFromScriptContent(content: String, label: String = "本地文件") {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                sourceManager.installJsSource(content)
            }
            if (result.isSuccess) {
                val src = result.getOrNull()
                _messageEvent.emit("成功导入漫画源: ${src?.name ?: label}")
            } else {
                _messageEvent.emit("导入解析失败: ${result.exceptionOrNull()?.message}")
            }
        }
    }
}
