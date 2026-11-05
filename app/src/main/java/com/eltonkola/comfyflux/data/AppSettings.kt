package com.eltonkola.comfyflux.data

import android.content.SharedPreferences
import com.eltonkola.comfyflux.data.network.DEFAULT_URL
import com.eltonkola.comfyflux.data.network.normalizeServerUrl
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Last create-screen selection, restored after an app restart. */
@Serializable
data class GenerationDraft(
    val prompt: String = "",
    val seed: Long = 0,
    val isRandom: Boolean = true,
    val width: Int = 512,
    val height: Int = 512,
    val batchSize: Int = 1,
    val cfg: Float = 0f,
    val steps: Int = 0,
    val workflowName: String = "",
    val workflowUri: String = "",
    val workflowIsServer: Boolean = false
)

data class SettingsState(
    val dark: Boolean,
    val system: Boolean,
    val dynamicColor: Boolean,
    val grqApiKey: String,
    val verboseLogging: Boolean,
    val logBodies: Boolean
)

class AppSettings(private val sharedPreferences: SharedPreferences) {

    private val _settingsState =
        MutableStateFlow(SettingsState(isDarkTheme(), isSystemTheme(), isDynamicColor(), groqApi(), verboseLogging(), logBodies()))
    val settingsState: StateFlow<SettingsState> = _settingsState.asStateFlow()

    companion object {
        const val GROQ_KEY = "GROQ_KEY"
        const val DARK_THEME = "dark_theme"
        const val SYSTEM_THEME = "system_theme"
        const val DYNAMIC_COLOR = "dynamic_color"
        const val SERVER_URL = "server_url"
        const val RECENT_PROMPTS = "recent_prompts"
        const val GENERATION_DRAFT = "generation_draft"
        const val VERBOSE_LOGGING = "verbose_logging"
        const val LOG_BODIES = "log_bodies"
    }

    private val json = Json { ignoreUnknownKeys = true }

    fun recentPrompts(): List<String> {
        val raw = sharedPreferences.getString(RECENT_PROMPTS, null) ?: return emptyList()
        return runCatching { json.decodeFromString<List<String>>(raw) }.getOrDefault(emptyList())
    }

    fun setRecentPrompts(prompts: List<String>) {
        sharedPreferences.edit().putString(RECENT_PROMPTS, json.encodeToString(prompts)).apply()
    }

    fun generationDraft(): GenerationDraft? {
        val raw = sharedPreferences.getString(GENERATION_DRAFT, null) ?: return null
        return runCatching { json.decodeFromString<GenerationDraft>(raw) }.getOrNull()
    }

    fun setGenerationDraft(draft: GenerationDraft) {
        sharedPreferences.edit().putString(GENERATION_DRAFT, json.encodeToString(draft)).apply()
    }

    fun serverUrl(): String {
        return normalizeServerUrl(
            sharedPreferences.getString(SERVER_URL, DEFAULT_URL) ?: DEFAULT_URL
        )
    }

    private fun verboseLogging(): Boolean {
        return sharedPreferences.getBoolean(VERBOSE_LOGGING, false)
    }

    /** On: the log keeps DEBUG entries (HTTP/websocket traces, stack chains). */
    fun setVerboseLogging(value: Boolean) {
        sharedPreferences.edit().putBoolean(VERBOSE_LOGGING, value).apply()
        _settingsState.update { it.copy(verboseLogging = value) }
    }

    private fun logBodies(): Boolean {
        return sharedPreferences.getBoolean(LOG_BODIES, false)
    }

    /** On (second verbosity level): the log also keeps full request payloads. */
    fun setLogBodies(value: Boolean) {
        sharedPreferences.edit().putBoolean(LOG_BODIES, value).apply()
        _settingsState.update { it.copy(logBodies = value) }
    }

    fun setServerUrl(url: String) {
        sharedPreferences.edit().putString(SERVER_URL, url).apply()
    }

    fun groqApi(): String {
        return sharedPreferences.getString(GROQ_KEY, "") ?: ""
    }

    fun setGroqApiKey(key: String) {
        sharedPreferences.edit().putString(GROQ_KEY, key).apply()
        _settingsState.update { it.copy(grqApiKey = key) }
    }

    private fun isDarkTheme(): Boolean {
        return sharedPreferences.getBoolean(DARK_THEME, false)
    }

    fun setDarkTheme(value: Boolean) {
        sharedPreferences.edit().putBoolean(DARK_THEME, value).apply()
        _settingsState.update { it.copy(dark = value) }
    }

    private fun isSystemTheme(): Boolean {
        return sharedPreferences.getBoolean(SYSTEM_THEME, true)
    }

    fun setSystemTheme(value: Boolean) {
        sharedPreferences.edit().putBoolean(SYSTEM_THEME, value).apply()
        _settingsState.update { it.copy(system = value) }
    }

    private fun isDynamicColor(): Boolean {
        return sharedPreferences.getBoolean(DYNAMIC_COLOR, true)
    }

    fun setDynamicColor(value: Boolean) {
        sharedPreferences.edit().putBoolean(DYNAMIC_COLOR, value).apply()
        _settingsState.update { it.copy(dynamicColor = value) }
    }

}
