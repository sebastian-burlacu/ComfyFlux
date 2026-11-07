package com.eltonkola.comfyflux.data

import android.content.Context
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Stack traces have to outlive the process, otherwise a crash that kills the
 * app leaves nothing behind to look at. Traces are appended to a small file in
 * internal storage; [MainApp] replays them into [LogStore] on the next start,
 * so Settings -> Logs shows (and exports) the crash that ended the last run.
 */
class CrashJournal(context: Context) {

    private val file = File(context.filesDir, CRASH_FILE)

    /** Record one fatal error; the newest [MAX_BLOCKS] traces are kept. */
    @Synchronized
    fun record(threadName: String, error: Throwable) {
        val block = StringBuilder()
        block.append("${STAMP.format(Date())} CRASH [${threadName}] " +
            "${error.javaClass.name}: ${error.message}\n")
        val frames = StringWriter()
        error.printStackTrace(PrintWriter(frames))
        frames.toString().lineSequence()
            .filter { it.startsWith("\tat ") || !it.startsWith("\t") }
            .take(MAX_FRAMES)
            .forEach { block.append("  ").append(it.trim()).append('\n') }
        var cause = error.cause
        var depth = 0
        while (cause != null && depth < MAX_CAUSES) {
            block.append("  caused by ${cause.javaClass.name}: ${cause.message}\n")
            cause.stackTrace.take(MAX_FRAMES / 2).forEach { block.append("    at $it\n") }
            cause = cause.cause
            depth++
        }
        val kept = (readBlocks() + block.toString().trimEnd()).takeLast(MAX_BLOCKS)
        runCatching { file.writeText(kept.joinToString("\n\n")) }
    }

    /** Recorded traces, oldest first. */
    @Synchronized
    fun recent(): List<String> = readBlocks()

    private fun readBlocks(): List<String> =
        if (!file.exists()) emptyList()
        else runCatching {
            file.readText().split("\n\n").map { it.trim() }.filter { it.isNotEmpty() }
        }.getOrDefault(emptyList())

    companion object {
        private const val CRASH_FILE = "crash.log"
        private const val MAX_BLOCKS = 12
        private const val MAX_FRAMES = 40
        private const val MAX_CAUSES = 3
        private val STAMP = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
    }
}
