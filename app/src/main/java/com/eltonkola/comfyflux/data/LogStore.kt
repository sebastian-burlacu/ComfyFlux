package com.eltonkola.comfyflux.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class LogLevel { TRACE, DEBUG, INFO, WARN, ERROR }

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
        // Full request bodies are the point of the second verbosity level.
        const val MAX_BODY_LENGTH = 30000
    }
}

/**
 * In-memory event log surfaced by Settings -> Logs. Bounded ring buffer: the
 * app lives across reconnects and retries, unbounded growth would be the bug.
 *
 * Two verbosity levels: DEBUG entries (HTTP traces, websocket events, stack
 * chains) need the "Verbose logging" switch; TRACE entries add full request
 * payloads and need the stronger "Log request bodies" switch on top.
 */
class LogStore(private val capacity: Int = 1000) {

    @Volatile
    var verbose = false

    @Volatile
    var bodies = false

    private val _entries = MutableStateFlow<List<LogEntry>>(emptyList())
    val entries: StateFlow<List<LogEntry>> = _entries.asStateFlow()

    @Synchronized
    fun log(level: LogLevel, source: String, message: String) {
        if (level == LogLevel.DEBUG && !verbose) return
        if (level == LogLevel.TRACE && !bodies) return
        val cap = if (level == LogLevel.TRACE) LogEntry.MAX_BODY_LENGTH else LogEntry.MAX_MESSAGE_LENGTH
        val entry = LogEntry(
            timestamp = System.currentTimeMillis(),
            level = level,
            source = source,
            message = message.take(cap),
        )
        _entries.value = (_entries.value + entry).let {
            if (it.size > capacity) it.takeLast(capacity) else it
        }
    }

    fun trace(source: String, message: String) = log(LogLevel.TRACE, source, message)
    fun debug(source: String, message: String) = log(LogLevel.DEBUG, source, message)
    fun info(source: String, message: String) = log(LogLevel.INFO, source, message)
    fun warn(source: String, message: String) = log(LogLevel.WARN, source, message)
    fun error(source: String, message: String) = log(LogLevel.ERROR, source, message)

    /** Plain-text dump of every kept entry, newest last, for export/sharing. */
    fun exportText(): String {
        val header = "ComfyFlux log export ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())}" +
                " (${_entries.value.size} entries, verbose=$verbose, bodies=$bodies)"
        return (listOf(header) + _entries.value.map {
            "${it.timeString()} ${it.level.name} [${it.source}] ${it.message}"
        }).joinToString("\n")
    }

    @Synchronized
    fun clear() {
        _entries.value = emptyList()
    }
}

/** Full exception chain (class: message, then each cause) with the top frames, for debugging. */
fun exceptionChain(e: Throwable, maxFrames: Int = 12): String {
    val sb = StringBuilder()
    var current: Throwable? = e
    var depth = 0
    while (current != null && depth < 5) {
        if (depth > 0) sb.append("\nCaused by: ")
        sb.append(current.javaClass.name).append(": ").append(current.message)
        current.stackTrace.take(maxFrames).forEach { frame -> sb.append("\n    at ").append(frame) }
        val cause = current.cause
        current = if (cause === current) null else cause
        depth++
    }
    return sb.toString()
}
