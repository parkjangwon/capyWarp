package org.parkjw.capywarp.ui.viewmodels

import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.parkjw.capywarp.data.model.Prompt
import org.parkjw.capywarp.data.model.BackupPayload
import org.parkjw.capywarp.data.model.SettingsData
import org.parkjw.capywarp.domain.repository.PromptRepository
import org.parkjw.capywarp.domain.repository.SettingsRepository

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settings: SettingsRepository,
    private val prompts: PromptRepository
) : ViewModel() {

    data class UiState(
        val apiKey: String = "",
        val model: String = DEFAULT_MODEL,
        val imageModel: String = DEFAULT_IMAGE_MODEL,
        val theme: String = "system",
        val availableModels: List<Pair<String, String>> = AVAILABLE_MODELS,
        val availableImageModels: List<Pair<String, String>> = AVAILABLE_IMAGE_MODELS,
    )


    // 사용자 커스텀 프롬프트(항상 덧붙일 지시)
    val userPrompt: StateFlow<String> = settings.userPrompt.stateIn(
        viewModelScope, SharingStarted.Eagerly, ""
    )

    companion object {
        val AVAILABLE_MODELS = listOf(
            "gemini-2.5-flash-lite" to "Gemini 2.5 Flash Lite",
            "gemini-2.5-flash" to "Gemini 2.5 Flash",
            "gemini-2.5-pro" to "Gemini 2.5 Pro",
        )
        val AVAILABLE_IMAGE_MODELS = listOf(
            "gemini-2.5-flash-image" to "Gemini 2.5 Flash Image"
        )
        val AVAILABLE_LANGUAGES = listOf(
            "en" to "English",
            "ko" to "한국어",
            "ja" to "日本語",
            "zh-CN" to "简体中文",
            "zh-TW" to "繁體中文",
            "es" to "Español",
            "fr" to "Français",
            "de" to "Deutsch",
            "ru" to "Русский",
            "pt" to "Português",
        )
        const val DEFAULT_MODEL = "gemini-2.5-flash"
        const val DEFAULT_IMAGE_MODEL = "gemini-2.5-flash-image"
    }

    private val _apiKey = MutableStateFlow(settings.getApiKey())
    val apiKey: StateFlow<String> = _apiKey

    val model: StateFlow<String> = settings.model.stateIn(
        viewModelScope, SharingStarted.Eagerly, DEFAULT_MODEL
    )
    val imageModel: StateFlow<String> = settings.imageModel.stateIn(
        viewModelScope, SharingStarted.Eagerly, DEFAULT_IMAGE_MODEL
    )
    val theme: StateFlow<String> = settings.theme.stateIn(
        viewModelScope, SharingStarted.Eagerly, "system"
    )
    val language: StateFlow<String> = settings.language.stateIn(
        viewModelScope, SharingStarted.Eagerly, "en"
    )
    val autoAttachSelectedText: StateFlow<Boolean> = settings.autoAttachSelectedText.stateIn(
        viewModelScope, SharingStarted.Eagerly, true
    )
    val autoAttachPosition: StateFlow<String> = settings.autoAttachPosition.stateIn(
        viewModelScope, SharingStarted.Eagerly, "top"
    )

    fun setApiKey(key: String) {
        _apiKey.value = key
        settings.setApiKey(key)
    }

    fun setModel(model: String) {
        viewModelScope.launch { settings.setModel(model) }
    }

    fun setImageModel(model: String) {
        viewModelScope.launch { settings.setImageModel(model) }
    }

    fun setTheme(theme: String) {
        viewModelScope.launch { settings.setTheme(theme) }
    }

    fun setLanguage(language: String) {
        viewModelScope.launch { settings.setLanguage(language) }
    }

    fun setUserPrompt(value: String) {
        viewModelScope.launch { settings.setUserPrompt(value) }
    }

    fun setAutoAttachSelectedText(enabled: Boolean) {
        viewModelScope.launch { settings.setAutoAttachSelectedText(enabled) }
    }

    fun setAutoAttachPosition(position: String) {
        viewModelScope.launch { settings.setAutoAttachPosition(position) }
    }

    fun exportPrompts(resolver: ContentResolver, uri: Uri, onDone: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            try {
                val all: List<Prompt> = prompts.getAllPrompts()
                val settingsData = SettingsData(
                    apiKey = settings.getApiKey(),
                    model = model.first(),
                    imageModel = imageModel.first(),
                    theme = theme.first(),
                    userPrompt = userPrompt.first(),
                    language = language.first(),
                    autoAttachSelectedText = autoAttachSelectedText.first(),
                    autoAttachPosition = autoAttachPosition.first()
                )
                val payload = BackupPayload(all, settingsData)
                val json = Json { prettyPrint = true }
                val data = json.encodeToString(payload)
                resolver.openOutputStream(uri)?.use { out ->
                    out.write(data.toByteArray())
                }
                onDone(true, "백업이 완료되었습니다.")
            } catch (e: Exception) {
                onDone(false, e.message ?: "백업 중 오류가 발생했습니다.")
            }
        }
    }

    fun importPrompts(resolver: ContentResolver, uri: Uri, onDone: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            try {
                val jsonText = resolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                    ?: throw IllegalStateException("파일을 읽을 수 없습니다.")
                val json = Json { ignoreUnknownKeys = true }
                try {
                    val payload = json.decodeFromString<BackupPayload>(jsonText)
                    // restore settings
                    settings.setApiKey(payload.settings.apiKey)
                    settings.setTheme(payload.settings.theme)
                    settings.setModel(payload.settings.model)
                    settings.setImageModel(payload.settings.imageModel)
                    settings.setUserPrompt(payload.settings.userPrompt)
                    val importedLang = if (payload.settings.language == "system") "en" else payload.settings.language
                    settings.setLanguage(importedLang)
                    // new fields with defaults
                    settings.setAutoAttachSelectedText(payload.settings.autoAttachSelectedText)
                    settings.setAutoAttachPosition(payload.settings.autoAttachPosition)
                    // restore prompts
                    prompts.importPrompts(payload.prompts)
                } catch (_: Exception) {
                    // backward compatibility: prompt list only
                    val list = json.decodeFromString<List<Prompt>>(jsonText)
                    prompts.importPrompts(list)
                }
                onDone(true, "복원이 완료되었습니다.")
            } catch (e: Exception) {
                onDone(false, e.message ?: "복원 중 오류가 발생했습니다.")
            }
        }
    }

    // 이전 '페르소나 설정 프롬프트 추가' 기능은 사용자 프롬프트 설정으로 대체되었습니다.
}