package com.eltonkola.comfyflux.data.model

import android.content.Context
import android.content.SharedPreferences
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * The phone's own keep/discard mark on a history entry. ComfyUI has no such
 * notion (its history is just a log), so this lives on the device: a phone
 * can sort its own photos without installing anything server-side.
 */
enum class Triage { Kept, Discarded }

/** What the History list shows. */
enum class TriageFilter(val label: String) {
    All("All"),
    Kept("Kept"),
    Discarded("Discarded"),
    Unmarked("Unmarked")
}

/**
 * Entries matching [filter]. Unknown ids (a run that has since left the
 * server history) are irrelevant here; an entry with no mark is "unmarked".
 */
fun filterHistory(
    history: List<HistoryItem>,
    triage: Map<String, Triage>,
    filter: TriageFilter
): List<HistoryItem> = history.filter { item ->
    when (filter) {
        TriageFilter.All -> true
        TriageFilter.Kept -> triage[item.id] == Triage.Kept
        TriageFilter.Discarded -> triage[item.id] == Triage.Discarded
        TriageFilter.Unmarked -> triage[item.id] == null
    }
}

/** How many entries each filter would show; drives the chip labels. */
fun triageCounts(history: List<HistoryItem>, triage: Map<String, Triage>): Map<TriageFilter, Int> =
    TriageFilter.entries.associateWith { filter -> filterHistory(history, triage, filter).size }

/**
 * Marks in one JSON object `{"<history id>": "kept"}` in private prefs. A
 * corrupt value is treated as "nothing marked" rather than crashing the tab.
 */
class TriageStore(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("TriagePrefs", Context.MODE_PRIVATE)

    private val json = Json { ignoreUnknownKeys = true }

    fun all(): Map<String, Triage> {
        val raw = prefs.getString(KEY, null) ?: return emptyMap()
        return runCatching {
            json.parseToJsonElement(raw).jsonObject.mapNotNull { (id, value) ->
                when (value.jsonPrimitive.content) {
                    "kept" -> id to Triage.Kept
                    "discarded" -> id to Triage.Discarded
                    else -> null
                }
            }.toMap()
        }.getOrDefault(emptyMap())
    }

    /** null clears the mark. */
    fun mark(id: String, mark: Triage?) {
        val current = all().toMutableMap()
        if (mark == null) current.remove(id) else current[id] = mark
        prefs.edit().putString(
            KEY,
            buildJsonObject {
                current.forEach { (key, value) ->
                    put(
                        key,
                        JsonPrimitive(if (value == Triage.Kept) "kept" else "discarded")
                    )
                }
            }.toString()
        ).apply()
    }

    /** Drop marks for runs that are no longer on the server. */
    fun prune(liveIds: Set<String>) {
        val current = all()
        val dead = current.keys - liveIds
        if (dead.isEmpty()) return
        prefs.edit().putString(
            KEY,
            buildJsonObject {
                current.filterKeys { it in liveIds }.forEach { (key, value) ->
                    put(key, JsonPrimitive(if (value == Triage.Kept) "kept" else "discarded"))
                }
            }.toString()
        ).apply()
    }

    companion object {
        private const val KEY = "triage"
    }
}
