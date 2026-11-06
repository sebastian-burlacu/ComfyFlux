package com.eltonkola.comfyflux.app.presets

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * A saved prompt the user keeps retyping. Ids are generated once and never
 * reused, so an old favorite can still be referenced after edits.
 */
@Serializable
data class PromptPreset(
    val id: String,
    val name: String,
    val text: String,
    val tags: List<String> = emptyList(),
    val favorite: Boolean = false,
    val createdAt: Long = 0L
)

private val presetJson = Json {
    ignoreUnknownKeys = true
    prettyPrint = true
}

/**
 * Decodes the stored array, or null when the string is not a valid preset
 * list. null is kept distinct from an empty list so the store can stash a
 * corrupt snapshot instead of overwriting it with "[]".
 */
internal fun decodePresetsOrNull(raw: String): List<PromptPreset>? =
    runCatching { presetJson.decodeFromString<List<PromptPreset>>(raw) }.getOrNull()

/** Safe decode for callers that only need the list; corrupt input yields empty. */
internal fun presetsFromJson(raw: String): List<PromptPreset> = decodePresetsOrNull(raw) ?: emptyList()

/** Pretty-printed so the raw prefs XML stays readable while debugging. */
internal fun encodePresets(presets: List<PromptPreset>): String = presetJson.encodeToString(presets)

/** Comma-separated chip text -> trimmed, lowercased, de-duplicated tags. */
internal fun parseTags(csv: String): List<String> =
    csv.split(',')
        .map { tag -> tag.trim().lowercase() }
        .filter { tag -> tag.isNotEmpty() }
        .distinct()

/** Short display name for a preset whose save row left the name blank. */
internal fun suggestName(text: String): String {
    val words = text.trim().split(Regex("\\s+")).take(5).joinToString(" ")
    return if (words.length > 40) words.take(40).trimEnd() + "…" else words
}

/** Unique per save: base-36 timestamp plus a counter, so same-millisecond saves never collide. */
internal fun newPresetId(nowMillis: Long, counter: Int): String =
    "p" + nowMillis.toString(36) + counter

/**
 * Pure search used by the UI: case-insensitive substring over name, text and
 * tags; `tag` matches a whole tag; favorites first, then newest first.
 */
internal fun filterPresets(
    presets: List<PromptPreset>,
    query: String,
    favoriteOnly: Boolean,
    tag: String?
): List<PromptPreset> {
    val q = query.trim().lowercase()
    return presets
        .filter { preset ->
            (q.isEmpty() ||
                    preset.name.lowercase().contains(q) ||
                    preset.text.lowercase().contains(q) ||
                    preset.tags.any { it.lowercase().contains(q) }) &&
                    (!favoriteOnly || preset.favorite) &&
                    (tag == null || preset.tags.any { it.equals(tag, ignoreCase = true) })
        }
        .sortedWith(
            compareByDescending<PromptPreset> { it.favorite }
                .thenByDescending { it.createdAt }
        )
}

/**
 * User prompt presets in their own prefs file, kept away from AppSettings so
 * a corrupt preset list can never take the app's settings down with it.
 */
class PresetStore(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("UserPromptPresets", Context.MODE_PRIVATE)

    fun all(): List<PromptPreset> {
        val raw = prefs.getString(KEY, null) ?: return emptyList()
        decodePresetsOrNull(raw)?.let { return it }
        // Corrupt list: park the bad string aside before the next write
        // replaces KEY, so the user can still recover their prompts.
        prefs.edit().putString(CORRUPT_KEY, raw).apply()
        return emptyList()
    }

    /** Upsert: same id replaces, new id appends. */
    fun save(preset: PromptPreset) {
        val current = all().toMutableList()
        val index = current.indexOfFirst { it.id == preset.id }
        if (index >= 0) current[index] = preset else current += preset
        prefs.edit().putString(KEY, encodePresets(current)).apply()
    }

    fun delete(id: String) {
        prefs.edit().putString(KEY, encodePresets(all().filter { it.id != id })).apply()
    }

    fun toggleFavorite(id: String) {
        prefs.edit()
            .putString(
                KEY,
                encodePresets(all().map { if (it.id == id) it.copy(favorite = !it.favorite) else it })
            )
            .apply()
    }

    private companion object {
        const val KEY = "PRESETS"
        const val CORRUPT_KEY = "PRESETS_CORRUPT"
    }
}

/** Preset list as seen by the create screen; every write reloads the flow. */
class PresetUseCase(private val store: PresetStore) {

    val presets = MutableStateFlow(store.all())

    // Guard against two saves inside one millisecond producing the same id.
    private var idCounter = 0

    fun reload() {
        presets.value = store.all()
    }

    /** Saves the create-screen prompt as a new preset; blank prompts are never stored. */
    fun saveFromCurrent(name: String, text: String, tagsCsv: String) {
        if (text.isBlank()) return
        val preset = PromptPreset(
            id = newPresetId(System.currentTimeMillis(), idCounter++),
            name = name.trim().ifBlank { suggestName(text) },
            text = text.trim(),
            tags = parseTags(tagsCsv),
            createdAt = System.currentTimeMillis()
        )
        store.save(preset)
        reload()
    }

    fun delete(id: String) {
        store.delete(id)
        reload()
    }

    fun setFavorite(id: String, favorite: Boolean) {
        val current = presets.value.firstOrNull { it.id == id } ?: return
        if (current.favorite != favorite) {
            store.toggleFavorite(id)
            reload()
        }
    }

    fun search(query: String, favoriteOnly: Boolean, tag: String?): List<PromptPreset> =
        filterPresets(presets.value, query, favoriteOnly, tag)
}
