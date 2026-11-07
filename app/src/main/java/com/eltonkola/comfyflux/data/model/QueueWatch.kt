package com.eltonkola.comfyflux.data.model

/**
 * The queue watch compares what the server was running a moment ago with what
 * it runs now. A job that left a now-empty running list was finished; if this
 * app never queued that prompt id, the run came from elsewhere (web UI,
 * another phone) and nobody told the user about it.
 */
internal fun finishedForeignRuns(
    previousRunning: Set<String>,
    runningNow: Set<String>,
    wasLocallyQueued: (String) -> Boolean
): List<String> {
    if (runningNow.isNotEmpty()) return emptyList()
    return (previousRunning - runningNow).filterNot(wasLocallyQueued)
}
