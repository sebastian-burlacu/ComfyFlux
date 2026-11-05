package com.eltonkola.comfyflux.data

import android.content.SharedPreferences
import com.eltonkola.comfyflux.data.network.DEFAULT_URL
import com.eltonkola.comfyflux.data.network.normalizeServerUrl
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class SettingsState(
    val dark: Boolean,
    val system: Boolean,
    val dynamicColor: Boolean,
    val grqApiKey: String
)

class AppSettings(private val sharedPreferences: SharedPreferences) {

    private val _settingsState =
        MutableStateFlow(SettingsState(isDarkTheme(), isSystemTheme(), isDynamicColor(), groqApi()))
    val settingsState: StateFlow<SettingsState> = _settingsState.asStateFlow()

    companion object {
        const val GROQ_KEY = "GROQ_KEY"
        const val DARK_THEME = "dark_theme"
        const val SYSTEM_THEME = "system_theme"
        const val DYNAMIC_COLOR = "dynamic_color"
        const val SERVER_URL = "server_url"
    }

    fun serverUrl(): String {
        return normalizeServerUrl(
            sharedPreferences.getString(SERVER_URL, DEFAULT_URL) ?: DEFAULT_URL
        )
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
