package org.parkjw.capywarp.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.parkjw.capywarp.domain.repository.GeminiRepository
import org.parkjw.capywarp.domain.repository.PromptRepository
import javax.inject.Inject

@HiltViewModel
class WarpViewModel @Inject constructor(
    private val promptRepository: PromptRepository,
    private val geminiRepository: GeminiRepository
) : ViewModel() {
    private val _state = MutableStateFlow<WarpState>(WarpState.Initial)
    val state = _state.asStateFlow()

    fun processText(text: String, promptId: Int) {
        viewModelScope.launch {
            _state.value = WarpState.Loading
            try {
                val prompt = promptRepository.getPrompt(promptId)
                    ?: throw Exception("프롬프트를 찾을 수 없습니다.")
                val result = geminiRepository.generateContent(text, prompt)
                _state.value = WarpState.Success(result = result, resultAction = prompt.resultAction, outputType = prompt.outputType)
            } catch (e: Exception) {
                _state.value = WarpState.Error(e.message ?: "알 수 없는 오류가 발생했습니다.")
            }
        }
    }
}

sealed class WarpState {
    data object Initial : WarpState()
    data object Loading : WarpState()
    data class Success(val result: String, val resultAction: Int, val outputType: Int) : WarpState()
    data class Error(val message: String) : WarpState()
}