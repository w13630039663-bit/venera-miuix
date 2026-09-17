package com.venera.compose.feature.sourcemanage

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.venera.compose.engine.explainSourceFailure
import com.venera.compose.source.ComicSource
import com.venera.compose.source.ComicSourceManager
import com.venera.compose.source.InstalledSourceMeta
import com.venera.compose.source.RepoIndexEntry
import com.venera.compose.source.js.JsComicSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 源管理列表行：把「已安装元数据」与「运行期源实例」合并成 UI 可直接渲染的模型。
 *
 * `fileName` 为 null 表示仅有原生实现、尚未安装 JS 源。
 */
data class SourceRow(
    val fileName: String?,
    val key: String,
    val name: String,
    val version: String,
    val enabled: Boolean,
    val pinned: Boolean,
    val isJs: Boolean,
    val latency: Long? = null,
    val testing: Boolean = false,
    /** 远端可用新版本号（对应官方 availableUpdates）；null 表示无更新 */
    val latestVersion: String? = null
) {
    /** 是否存在新版本 */
    val hasUpdate: Boolean get() = latestVersion != null
}

class ComicSourceViewModel(application: Application) : AndroidViewModel(application) {

    private val sourceManager = ComicSourceManager.getInstance(application)

    companion object {
        /** logcat 过滤标签：`adb logcat -s VeneraSourceOp` */
        private const val TAG = "VeneraSourceOp"
    }

    val sources: StateFlow<List<ComicSource>> = sourceManager.sourcesFlow
    val activeSourceKey: StateFlow<String> = sourceManager.activeSourceKey
    val latencyMap: StateFlow<Map<String, Long>> = sourceManager.latencyMapFlow

    private val _repoItems = MutableStateFlow<List<RepoIndexEntry>>(emptyList())
    val repoItems: StateFlow<List<RepoIndexEntry>> = _repoItems.asStateFlow()

    private val _isLoadingRepo = MutableStateFlow(false)
    val isLoadingRepo: StateFlow<Boolean> = _isLoadingRepo.asStateFlow()

    private val _installingFileNames = MutableStateFlow<Set<String>>(emptySet())
    val installingFileNames: StateFlow<Set<String>> = _installingFileNames.asStateFlow()

    private val _installAllProgress = MutableStateFlow<Pair<Int, Int>?>(null)
    val installAllProgress: StateFlow<Pair<Int, Int>?> = _installAllProgress.asStateFlow()

    private val _testingKeys = MutableStateFlow<Set<String>>(emptySet())
    val testingKeys: StateFlow<Set<String>> = _testingKeys.asStateFlow()

    private val _messageEvent = MutableSharedFlow<String>()
    val messageEvent: SharedFlow<String> = _messageEvent.asSharedFlow()

    private val _configBundles = MutableStateFlow<Map<String, SourceConfigBundle>>(emptyMap())
    val configBundles: StateFlow<Map<String, SourceConfigBundle>> = _configBundles.asStateFlow()

    /** 网络层判定为不可达的域名，用于提示用户配置代理 */
    private val _unreachableHosts = MutableStateFlow<Set<String>>(emptySet())
    val unreachableHosts: StateFlow<Set<String>> = _unreachableHosts.asStateFlow()

    val defaultRepoUrl = ComicSourceManager.DEFAULT_REPO_URL

    /** 当前仓库索引地址（用户可在仓库清单里改） */
    val repoUrl: StateFlow<String> = sourceManager.repoUrl

    /** 可更新源：fileName -> 远端版本号 */
    val availableUpdates: StateFlow<Map<String, String>> = sourceManager.availableUpdates

    private val _checkingUpdates = MutableStateFlow(false)
    val checkingUpdates: StateFlow<Boolean> = _checkingUpdates.asStateFlow()

    /** 检查更新结果弹窗内容：null = 不显示；空列表 = 无更新 */
    private val _updateCandidates = MutableStateFlow<List<Pair<String, String>>?>(null)
    val updateCandidates: StateFlow<List<Pair<String, String>>?> = _updateCandidates.asStateFlow()

    /** 批量更新进度 (已完成, 总数) */
    private val _batchUpdateProgress = MutableStateFlow<Pair<Int, Int>?>(null)
    val batchUpdateProgress: StateFlow<Pair<Int, Int>?> = _batchUpdateProgress.asStateFlow()

    /** UI 渲染用的合并列表（置顶优先，其余保持安装顺序） */
    private val _sourceRows = MutableStateFlow<List<SourceRow>>(emptyList())
    val sourceRows: StateFlow<List<SourceRow>> = _sourceRows.asStateFlow()

    init {
        loadRepo()
        viewModelScope.launch {
            combine(
                sourceManager.installedMeta,
                sourceManager.sourcesFlow,
                sourceManager.latencyMapFlow,
                _testingKeys,
                sourceManager.availableUpdates
            ) { meta, sources, latency, testing, updates ->
                buildRows(meta, sources, latency, testing, updates)
            }.collect { _sourceRows.value = it }
        }
        viewModelScope.launch {
            sources.collect { list -> refreshConfigBundles(list) }
        }
    }

    private fun buildRows(
        meta: List<InstalledSourceMeta>,
        sources: List<ComicSource>,
        latency: Map<String, Long>,
        testing: Set<String>,
        updates: Map<String, String> = emptyMap()
    ): List<SourceRow> {
        val byKey = sources.associateBy { it.key }
        val rows = mutableListOf<SourceRow>()
        val coveredKeys = mutableSetOf<String>()

        for (m in meta) {
            val runtime = byKey[m.key] ?: continue
            coveredKeys.add(m.key)
            rows.add(
                SourceRow(
                    fileName = m.fileName,
                    key = m.key,
                    name = m.name.ifBlank { runtime.name },
                    version = runtime.version.ifBlank { m.version },
                    enabled = m.enabled,
                    pinned = m.pinned,
                    isJs = runtime is JsComicSource,
                    latency = latency[m.key],
                    testing = m.key in testing,
                    latestVersion = updates[m.fileName]
                )
            )
        }
        // 仅有原生实现、尚未安装 JS 源的兜底源
        for (s in sources) {
            if (s.key in coveredKeys) continue
            rows.add(
                SourceRow(
                    fileName = null,
                    key = s.key,
                    name = s.name,
                    version = s.version,
                    enabled = true,
                    pinned = false,
                    isJs = s is JsComicSource,
                    latency = latency[s.key],
                    testing = s.key in testing
                )
            )
        }
        return rows
    }

    /* ------------------------------------------------------------------ *
     * 源配置（设置项 / 账号）
     * ------------------------------------------------------------------ */

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
                    isJsSource = src is JsComicSource,
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
        emit("已保存设置")
    }

    fun executeCallback(sourceKey: String, settingKey: String) {
        val src = sourceManager.getSource(sourceKey) ?: return
        viewModelScope.launch {
            emit("正在执行操作...")
            val res = withContext(Dispatchers.IO) { src.executeSettingCallback(settingKey) }
            emit(if (res.isSuccess) "执行完成" else "执行失败: ${res.exceptionOrNull()?.message}")
            refreshConfigBundles()
        }
    }

    fun login(sourceKey: String, user: String, pass: String, onComplete: (Boolean, String?) -> Unit) {
        val src = sourceManager.getSource(sourceKey) ?: return
        viewModelScope.launch {
            val res = withContext(Dispatchers.IO) { src.login(user, pass) }
            if (res.isSuccess) {
                android.util.Log.i(TAG, "login ok: $sourceKey")
                emit("登录成功")
                refreshConfigBundles()
                onComplete(true, null)
            } else {
                // picacg 这类源会把「非 200」一律压成 Failed to login，服务端的真实原因
                // （例如 1023 限流）被丢掉。这里用 HTTP 层记录的服务端响应补全提示，
                // 否则用户会误以为是自己账号密码错。
                val err = explainSourceFailure(res.exceptionOrNull()?.message ?: "登录失败")
                // 落到 logcat，便于 `adb logcat -s VeneraSourceOp` 直接取证
                android.util.Log.w(TAG, "login failed: $sourceKey -> $err", res.exceptionOrNull())
                emit("登录失败: $err")
                onComplete(false, err)
            }
        }
    }

    fun relogin(sourceKey: String) {
        val src = sourceManager.getSource(sourceKey) ?: return
        viewModelScope.launch {
            emit("正在重新登录...")
            val res = withContext(Dispatchers.IO) { src.relogin() }
            emit(if (res.isSuccess) "重新登录成功" else "重新登录失败: ${res.exceptionOrNull()?.message}")
            refreshConfigBundles()
        }
    }

    fun logout(sourceKey: String) {
        val src = sourceManager.getSource(sourceKey) ?: return
        viewModelScope.launch {
            withContext(Dispatchers.IO) { src.logout() }
            sourceManager.clearLocalStorage(sourceKey)
            emit("已注销登录")
            refreshConfigBundles()
        }
    }

    /* ------------------------------------------------------------------ *
     * 网页登录 / Cookie 直填登录
     * （对应官方 account.loginWithWebview / account.loginWithCookies）
     * ------------------------------------------------------------------ */

    /**
     * 把内嵌 WebView 的当前 url/title 交给源判定是否已登录成功。
     *
     * 官方在 WebView 每次导航 / 标题变化时都会调用，故这里刻意保持轻量。
     */
    suspend fun checkWebLogin(sourceKey: String, url: String, title: String): Boolean {
        val src = sourceManager.getSource(sourceKey) ?: return false
        return withContext(Dispatchers.IO) {
            runCatching { src.checkLoginStatus(url, title) }.getOrDefault(false)
        }
    }

    /**
     * 网页登录命中后的落库编排。
     *
     * 顺序很关键：**localStorage 必须先于 `onLoginSuccess()` 写入** ——
     * 源的回调正是靠 `this.loadData("_localStorage")` 取 token 的。
     */
    suspend fun completeWebLogin(
        sourceKey: String,
        cookieHeader: String?,
        localStorageJson: String?,
        url: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val src = sourceManager.getSource(sourceKey)
            ?: return@withContext Result.failure(Exception("源不存在: $sourceKey"))
        try {
            sourceManager.saveWebLoginLocalStorage(sourceKey, localStorageJson)
            sourceManager.saveCookiesFromWebLogin(url, cookieHeader)
            sourceManager.markLoggedIn(sourceKey)
            val hook = runCatching { src.onWebLoginSuccess() }
                .getOrElse { Result.failure(it) }
            refreshConfigBundles()
            hook
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Cookie 直填登录：把用户手填的字段交给源校验 */
    suspend fun loginWithCookies(sourceKey: String, cookies: List<String>): Result<Boolean> {
        val src = sourceManager.getSource(sourceKey)
            ?: return Result.failure(Exception("源不存在"))
        return withContext(Dispatchers.IO) {
            try {
                if (src.validateCookies(cookies)) {
                    sourceManager.markLoggedIn(sourceKey)
                    refreshConfigBundles()
                    Result.success(true)
                } else {
                    Result.failure(Exception("Cookie 无效，请检查是否填写正确"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    /* ------------------------------------------------------------------ *
     * 启用 / 置顶 / 排序 / 卸载
     * ------------------------------------------------------------------ */

    fun setEnabled(fileName: String, enabled: Boolean) {
        sourceManager.setSourceEnabled(fileName, enabled)
        emit(if (enabled) "已启用该源，将参与聚合搜索" else "已禁用该源，不再参与聚合搜索")
    }

    fun setPinned(fileName: String, pinned: Boolean) {
        sourceManager.setSourcePinned(fileName, pinned)
    }

    fun moveSource(up: Boolean, fileName: String) {
        val rows = sourceRows.value
        val index = rows.indexOfFirst { it.fileName == fileName }
        if (index < 0) return
        val target = if (up) index - 1 else index + 1
        if (target < 0 || target >= rows.size) return
        sourceManager.moveSource(index, target)
    }

    fun uninstall(fileName: String) {
        val ok = sourceManager.uninstallSource(fileName)
        emit(if (ok) "已卸载漫画源" else "卸载失败")
        refreshConfigBundles()
    }

    /** 卸载 / 删除漫画源（支持原生内置源与 JS 扩展源） */
    fun deleteSource(key: String, fileName: String? = null) {
        val resolvedFileName = fileName ?: sourceRows.value.firstOrNull { it.key == key }?.fileName
        val ok = sourceManager.deleteSource(key, resolvedFileName)
        emit(if (ok) "已删除漫画源" else "删除失败")
        refreshConfigBundles()
    }

    fun reloadSource(@Suppress("UNUSED_PARAMETER") sourceKey: String? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            sourceManager.loadInstalledJsSources()
            refreshConfigBundles()
            emit("已重新加载源配置")
        }
    }

    fun refreshPings() {
        sourceManager.resetNetworkBreaker()
        sourceManager.refreshPings()
        viewModelScope.launch {
            kotlinx.coroutines.delay(1500)
            _unreachableHosts.value = sourceManager.unreachableHosts()
        }
    }

    fun setActiveSource(key: String) {
        sourceManager.setActiveSource(key)
        emit("已切换活跃源为: $key")
    }

    /**
     * 单源可用性测试：真实发起一次搜索请求，输出延迟或具体错误。
     * 这是排查"搜不到"最直接的手段。
     */
    fun testSource(row: SourceRow) {
        viewModelScope.launch {
            _testingKeys.value = _testingKeys.value + row.key
            val (latency, error) = withContext(Dispatchers.IO) { sourceManager.testSource(row.key) }
            _testingKeys.value = _testingKeys.value - row.key
            if (error == null) {
                emit("${row.name}：可用（${latency}ms）")
                sourceManager.refreshPings()
            } else {
                android.util.Log.w(TAG, "test failed: ${row.key} -> $error")
                emit("${row.name}：不可用 —— $error")
            }
            _unreachableHosts.value = sourceManager.unreachableHosts()
        }
    }

    /* ------------------------------------------------------------------ *
     * 仓库
     * ------------------------------------------------------------------ */

    fun loadRepo(repoUrl: String? = null) {
        val url = repoUrl ?: sourceManager.repoUrl.value
        viewModelScope.launch {
            _isLoadingRepo.value = true
            val items = sourceManager.fetchRepoIndex(url)
            _repoItems.value = items
            _isLoadingRepo.value = false
            if (items.isEmpty()) emit("源仓库加载失败")
        }
    }

    /** 修改仓库地址并立即重新拉取（对齐官方仓库清单里的「Refresh」） */
    fun setRepoUrl(url: String) {
        sourceManager.setRepoUrl(url)
        loadRepo(sourceManager.repoUrl.value)
        emit("仓库地址已更新")
    }

    fun installFromRepo(item: RepoIndexEntry, repoUrl: String? = null) {
        val url = repoUrl ?: sourceManager.repoUrl.value
        _installingFileNames.value = _installingFileNames.value + item.fileName
        sourceManager.installFromRepo(item, url) { result ->
            _installingFileNames.value = _installingFileNames.value - item.fileName
            if (result.isSuccess) {
                val src = result.getOrNull()
                emit("成功安装: ${item.name} (${src?.version ?: item.version})")
                refreshConfigBundles()
            } else {
                emit("安装失败: ${result.exceptionOrNull()?.message}")
            }
        }
    }

    /** 一键安装/更新全部 33 个官方源 */
    fun installAll(repoUrl: String? = null) {
        val url = repoUrl ?: sourceManager.repoUrl.value
        val entries = _repoItems.value.ifEmpty { sourceManager.readBundledRepoIndex() }
        if (entries.isEmpty()) {
            emit("源清单为空，请先刷新仓库")
            return
        }
        viewModelScope.launch {
            sourceManager.installAll(
                entries = entries,
                repoUrl = url,
                onProgress = { done, total, current ->
                    _installAllProgress.value = done to total
                    _installingFileNames.value = _installingFileNames.value + current
                },
                onDone = { success, failed, skipped ->
                    _installAllProgress.value = null
                    _installingFileNames.value = emptySet()
                    emit("一键同步完成：新增/更新 $success 个，跳过 $skipped 个，失败 $failed 个")
                    refreshConfigBundles()
                }
            )
        }
    }

    /* ------------------------------------------------------------------ *
     * 单源「编辑 / 更新 / 删除」与检查更新
     * （对应官方 comic_source_page.dart 的 edit / update / delete / _CheckUpdatesButton）
     * ------------------------------------------------------------------ */

    /** 检查全部源的远端版本（官方 checkComicSourceUpdate） */
    fun checkUpdates() {
        if (_checkingUpdates.value) return
        viewModelScope.launch {
            _checkingUpdates.value = true
            val count = withContext(Dispatchers.IO) {
                sourceManager.checkUpdates(sourceManager.repoUrl.value)
            }
            _checkingUpdates.value = false
            when {
                count < 0 -> emit("检查更新失败：网络错误")
                count == 0 -> {
                    _updateCandidates.value = emptyList()
                    emit("已是最新版本")
                }
                else -> {
                    val rows = sourceRows.value
                    _updateCandidates.value = sourceManager.availableUpdates.value
                        .map { (fileName, newVersion) ->
                            val name = rows.firstOrNull { it.fileName == fileName }?.name ?: fileName
                            name to newVersion
                        }
                        .sortedBy { it.first }
                }
            }
            refreshConfigBundles()
        }
    }

    fun dismissUpdateCandidates() {
        _updateCandidates.value = null
    }

    /** 批量更新全部「有新版本」的源（官方 showUpdateDialog → 逐个 update） */
    fun updateAllAvailable() {
        val targets = sourceManager.availableUpdates.value.keys.toList()
        if (targets.isEmpty()) {
            _updateCandidates.value = null
            return
        }
        viewModelScope.launch {
            var ok = 0
            var fail = 0
            targets.forEachIndexed { index, fileName ->
                _batchUpdateProgress.value = (index + 1) to targets.size
                val res = withContext(Dispatchers.IO) {
                    sourceManager.updateSource(fileName, sourceManager.repoUrl.value)
                }
                if (res.isSuccess) ok++ else fail++
            }
            _batchUpdateProgress.value = null
            _updateCandidates.value = null
            emit("批量更新完成：成功 $ok 个，失败 $fail 个")
            reloadSource()
        }
    }

    /** 单源更新：按 source.url（或仓库同文件）重新下载 */
    fun updateSource(fileName: String) {
        _installingFileNames.value = _installingFileNames.value + fileName
        viewModelScope.launch {
            emit("正在更新源...")
            val res = withContext(Dispatchers.IO) {
                sourceManager.updateSource(fileName, sourceManager.repoUrl.value)
            }
            _installingFileNames.value = _installingFileNames.value - fileName
            if (res.isSuccess) {
                emit("已更新到 v${res.getOrNull()?.version}")
                refreshConfigBundles()
            } else {
                emit("更新失败: ${res.exceptionOrNull()?.message}")
            }
        }
    }

    /** 读取源脚本原文，供编辑器打开 */
    fun readSourceText(fileName: String): String? = sourceManager.readSourceText(fileName)

    /**
     * 保存编辑后的脚本。
     *
     * 先解析校验再落盘，因此语法错误不会污染本地文件 ——
     * 失败时把原因回给 UI，由编辑器原地展示，不关闭页面。
     */
    fun saveSourceText(fileName: String, content: String, onResult: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            val res = withContext(Dispatchers.IO) { sourceManager.saveSourceText(fileName, content) }
            if (res.isSuccess) {
                emit("已保存并重新加载源")
                refreshConfigBundles()
                onResult(true, null)
            } else {
                val msg = res.exceptionOrNull()?.message ?: "保存失败"
                emit("保存失败: $msg")
                onResult(false, msg)
            }
        }
    }

    fun installFromUrl(url: String) {
        viewModelScope.launch {
            val result: Result<ComicSource> = withContext(Dispatchers.IO) {
                try {
                    val client = com.venera.compose.data.network.VeneraNetworkClient.getInstance(getApplication())
                    val req = okhttp3.Request.Builder().url(url).build()
                    val resp = client.okHttpClient.newCall(req).execute()
                    val content = resp.body?.string()
                    if (resp.isSuccessful && !content.isNullOrBlank()) {
                        val name = url.substringAfterLast('/').takeIf { it.endsWith(".js") }
                        sourceManager.installJsSource(content, name)
                    } else {
                        Result.failure(Exception("HTTP ${resp.code}: 获取脚本失败"))
                    }
                } catch (e: Exception) {
                    Result.failure(e)
                }
            }
            if (result.isSuccess) {
                emit("成功从链接安装: ${result.getOrNull()?.name ?: url}")
                refreshConfigBundles()
            } else {
                emit("安装失败: ${result.exceptionOrNull()?.message}")
            }
        }
    }

    fun installFromScriptContent(content: String, label: String = "本地文件") {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                sourceManager.installJsSource(content, label.takeIf { it.endsWith(".js") })
            }
            if (result.isSuccess) {
                emit("成功导入漫画源: ${result.getOrNull()?.name ?: label}")
                refreshConfigBundles()
            } else {
                emit("导入解析失败: ${result.exceptionOrNull()?.message}")
            }
        }
    }

    private fun emit(msg: String) {
        viewModelScope.launch { _messageEvent.emit(msg) }
    }
}
