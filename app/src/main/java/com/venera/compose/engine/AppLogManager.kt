package com.venera.compose.engine

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class AppLogEntry(
    val id: Long = System.nanoTime(),
    val timestamp: Long = System.currentTimeMillis(),
    val level: String, // "I", "W", "E", "D"
    val tag: String,
    val message: String
) {
    val formattedTime: String
        get() = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date(timestamp))
}

object AppLogManager {
    private const val MAX_LOGS = 300
    private val deque = ArrayDeque<AppLogEntry>()
    private val _logsFlow = MutableStateFlow<List<AppLogEntry>>(emptyList())
    val logsFlow: StateFlow<List<AppLogEntry>> = _logsFlow.asStateFlow()

    init {
        log("I", "AppLogManager", "Venera 原生日志诊断系统就绪")
    }

    @Synchronized
    fun log(level: String, tag: String, message: String) {
        val entry = AppLogEntry(level = level, tag = tag, message = message)
        deque.addLast(entry)
        while (deque.size > MAX_LOGS) {
            deque.removeFirst()
        }
        _logsFlow.value = deque.toList()
    }

    fun i(tag: String, message: String) = log("I", tag, message)
    fun w(tag: String, message: String) = log("W", tag, message)
    fun e(tag: String, message: String) = log("E", tag, message)
    fun d(tag: String, message: String) = log("D", tag, message)

    @Synchronized
    fun clear() {
        deque.clear()
        _logsFlow.value = emptyList()
    }
}
