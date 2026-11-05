package com.eltonkola.comfyflux.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class LogLevel { INFO, WARN, ERROR }

data class LogEntry(
    val timestamp: Long,
    val level: LogLevel,
    /** "server" for anything the ComfyUI server reported, "client" for app-side failures. */
    val source: String,
    val message: String,
) {
    fun timeString(): String = TIME_FORMAT.format(Date(timestamp))

    companion object {
        private val TIME_FORMAT = SimpleDateFormat("HH:mm:ss", Locale.US)
        const val MAX_MESSAGE_LENGTH = 2000
    }
}

/**
 * In-memory event log surfaced by Settings -> Logs. Bounded ring buffer: the
 * app lives across reconnects and retries, unbounded growth would be the bug.
 */
class LogStore(private val capacity: Int = 400) {

    private val _entries = MutableStateFlow<List<LogEntry>>(emptyList())
    val entries: StateFlow<List<LogEntry>> = _entries.asStateFlow()

    @Synchronized
    fun log(level: LogLevel, source: String, message: String) {
        val entry = LogEntry(
            timestamp = System.currentTimeMillis(),
            level = level,
            source = source,
            message = message.take(LogEntry.MAX_MESSAGE_LENGTH),
        )
        _entries.value = (_entries.value + entry).let {
            if (it.size > capacity) it.takeLast(capacity) else it
        }
    }

    fun info(source: String, message: String) = log(LogLevel.INFO, source, message)
    fun warn(source: String, message: String) = log(LogLevel.WARN, source, message)
    fun error(source: String, message: String) = log(LogLevel.ERROR, source, message)

    @Synchronized
    fun clear() {
        _entries.value = emptyList()
    }
}
