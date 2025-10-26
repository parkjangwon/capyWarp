package org.parkjw.capywarp.ui.viewmodels

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.parkjw.capywarp.data.model.Prompt
import org.parkjw.capywarp.domain.repository.PromptRepository
import javax.inject.Inject

@HiltViewModel
class PromptEditorViewModel @Inject constructor(
    private val repository: PromptRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val promptId: Int? = savedStateHandle.get<Int>("promptId")

    data class UiState(
        val id: Int = 0,
        val title: String = "",
        val template: String = "",
        val resultAction: Int = 2,
        val outputType: Int = 0, // 0: 텍스트, 1: 이미지
        val order: Int = 0,
        val isNew: Boolean = true,
        val isSaving: Boolean = false,
        val error: String? = null,
        val message: String? = null,
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private var autoSaveJob: Job? = null

    init {
        if (promptId != null && promptId >= 0) {
            viewModelScope.launch {
                val p = repository.getPrompt(promptId)
                if (p != null) {
                    val coercedAction = when (p.outputType) {
                        0 -> when {
                            p.resultAction == 0 -> 2
                            p.resultAction == 3 -> 2 // text mode doesn't support save gallery
                            else -> listOf(1, 2, 4).firstOrNull { it == p.resultAction } ?: 2
                        }
                        else -> when {
                            p.resultAction == 1 -> 2 // image mode doesn't support clipboard
                            else -> listOf(2, 3, 4).firstOrNull { it == p.resultAction } ?: 2
                        }
                    }
                    _uiState.value = UiState(
                        id = p.id,
                        title = p.title,
                        template = p.template,
                        resultAction = coercedAction,
                        outputType = p.outputType,
                        order = p.order,
                        isNew = false
                    )
                }
            }
        }
    }

    fun updateTitle(value: String) {
        _uiState.value = _uiState.value.copy(title = value, error = null, message = null)
        scheduleAutoSave()
    }
    fun updateTemplate(value: String) {
        _uiState.value = _uiState.value.copy(template = value, error = null, message = null)
        scheduleAutoSave()
    }
    fun updateResultAction(value: Int) {
        _uiState.value = _uiState.value.copy(resultAction = value, error = null, message = null)
        scheduleAutoSave()
    }
    fun updateOutputType(value: Int) {
        val current = _uiState.value
        val coercedResultAction = when (value) {
            0 -> when (current.resultAction) {
                1, 2, 4 -> current.resultAction
                else -> 2
            }
            else -> when (current.resultAction) {
                2, 3, 4 -> current.resultAction
                else -> 2
            }
        }
        _uiState.value = current.copy(outputType = value, resultAction = coercedResultAction, error = null, message = null)
        scheduleAutoSave()
    }

    private fun scheduleAutoSave() {
        autoSaveJob?.cancel()
        autoSaveJob = viewModelScope.launch {
            delay(500) // debounce
            val s = _uiState.value
            if (s.title.isBlank() || s.template.isBlank()) return@launch
            try {
                _uiState.value = s.copy(isSaving = true)
                val prompt = Prompt(
                    id = s.id,
                    title = s.title,
                    content = s.template,
                    template = s.template,
                    resultAction = s.resultAction,
                    outputType = s.outputType,
                    order = s.order
                )
                val newId = repository.savePromptAndReturnId(prompt)
                _uiState.value = _uiState.value.copy(
                    id = newId,
                    isNew = false,
                    isSaving = false,
                    message = null,
                    error = null
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isSaving = false, error = e.message ?: "자동 저장 중 오류가 발생했습니다.")
            }
        }
    }
}