package org.parkjw.capywarp.domain.repository

import android.content.SharedPreferences
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.booleanPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    private val securePrefs: SharedPreferences
) {
    companion object {
        private val THEME_KEY = stringPreferencesKey("theme")
        private val LANGUAGE_KEY = stringPreferencesKey("language")
        private val MODEL_KEY = stringPreferencesKey("model")
        private val IMAGE_MODEL_KEY = stringPreferencesKey("image_model")
        private val USER_PROMPT_KEY = stringPreferencesKey("user_prompt")
        private val AUTO_ATTACH_KEY = booleanPreferencesKey("auto_attach_selected_text")
        private val AUTO_ATTACH_POS_KEY = stringPreferencesKey("auto_attach_position")
        private const val API_KEY = "gemini_api_key"
        // Synchronous mirror for theme to avoid first-frame race in services/overlays
        private const val THEME_MIRROR_KEY = "theme_mirror"
    }

    val theme: Flow<String> = dataStore.data.map { it[THEME_KEY] ?: "system" }
    val language: Flow<String> = dataStore.data.map {
        val value = it[LANGUAGE_KEY]
        when (value) {
            null, "system" -> "en" // default to English and migrate old 'system' to 'en'
            else -> value
        }
    }
    val model: Flow<String> = dataStore.data.map { it[MODEL_KEY] ?: "gemini-2.5-flash" }
    val imageModel: Flow<String> = dataStore.data.map { it[IMAGE_MODEL_KEY] ?: "gemini-2.5-flash-image" }
    val userPrompt: Flow<String> = dataStore.data.map { it[USER_PROMPT_KEY] ?: "" }
    val autoAttachSelectedText: Flow<Boolean> = dataStore.data.map { it[AUTO_ATTACH_KEY] ?: true }
    val autoAttachPosition: Flow<String> = dataStore.data.map { it[AUTO_ATTACH_POS_KEY] ?: "top" }

    suspend fun setTheme(theme: String) {
        // Persist to DataStore (reactive) and mirror to SharedPreferences (sync)
        dataStore.edit { it[THEME_KEY] = theme }
        securePrefs.edit().putString(THEME_MIRROR_KEY, theme).apply()
    }

    fun getThemeSync(): String {
        // 1) Try fast SharedPreferences mirror
        val mirrored = securePrefs.getString(THEME_MIRROR_KEY, null)
        if (!mirrored.isNullOrBlank()) return mirrored
        // 2) Fallback to DataStore (blocking read) and backfill mirror
        return try {
            val key = THEME_KEY
            val value = kotlinx.coroutines.runBlocking {
                dataStore.data.map { it[key] ?: "system" }.first()
            }
            securePrefs.edit().putString(THEME_MIRROR_KEY, value).apply()
            value
        } catch (_: Exception) {
            "system"
        }
    }

    suspend fun setLanguage(language: String) {
        dataStore.edit { it[LANGUAGE_KEY] = language }
    }

    suspend fun setModel(model: String) {
        dataStore.edit { it[MODEL_KEY] = model }
    }

    suspend fun setImageModel(model: String) {
        dataStore.edit { it[IMAGE_MODEL_KEY] = model }
    }

    suspend fun setUserPrompt(userPrompt: String) {
        dataStore.edit { it[USER_PROMPT_KEY] = userPrompt }
    }

    suspend fun setAutoAttachSelectedText(enabled: Boolean) {
        dataStore.edit { it[AUTO_ATTACH_KEY] = enabled }
    }

    suspend fun setAutoAttachPosition(position: String) {
        dataStore.edit { it[AUTO_ATTACH_POS_KEY] = position }
    }

    fun getApiKey(): String = securePrefs.getString(API_KEY, "") ?: ""

    fun setApiKey(key: String) {
        securePrefs.edit().putString(API_KEY, key).apply()
    }
}
