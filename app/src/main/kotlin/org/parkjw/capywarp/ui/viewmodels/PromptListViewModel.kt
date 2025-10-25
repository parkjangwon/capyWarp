package org.parkjw.capywarp.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import org.parkjw.capywarp.data.model.Prompt
import org.parkjw.capywarp.domain.repository.PromptRepository
import javax.inject.Inject

@HiltViewModel
class PromptListViewModel @Inject constructor(
    private val repository: PromptRepository
) : ViewModel() {
    val prompts = repository.getPrompts()

    fun deletePromptsByIds(ids: Set<Int>, onDone: (() -> Unit)? = null) {
        if (ids.isEmpty()) { onDone?.invoke(); return }
        viewModelScope.launch {
            try {
                val all = repository.getAllPrompts()
                all.filter { ids.contains(it.id) }.forEach { repository.deletePrompt(it) }
                onDone?.invoke()
            } catch (_: Exception) {
                onDone?.invoke()
            }
        }
    }

    fun persistOrder(newOrder: List<Prompt>, onDone: (() -> Unit)? = null) {
        viewModelScope.launch {
            try {
                val reindexed = newOrder.mapIndexed { index, p -> p.copy(order = index) }
                repository.updatePrompts(reindexed)
                onDone?.invoke()
            } catch (_: Exception) {
                onDone?.invoke()
            }
        }
    }

    fun duplicatePrompt(id: Int, onDone: (() -> Unit)? = null) {
        viewModelScope.launch {
            try {
                val src = repository.getPrompt(id) ?: return@launch
                val all = repository.getAllPrompts().sortedBy { it.order }
                val baseTitle = src.title.ifBlank { "Prompt" }
                var newTitle = baseTitle + " Copy"
                var suffix = 2
                val existingTitles = all.map { it.title }.toSet()
                while (existingTitles.contains(newTitle)) {
                    newTitle = "$baseTitle ($suffix)"
                    suffix++
                }
                val insertIndex = all.indexOfFirst { it.id == id }.coerceAtLeast(0) + 1
                val adjusted = all.mapIndexed { idx, p ->
                    if (idx >= insertIndex) p.copy(order = p.order + 1) else p
                }
                repository.updatePrompts(adjusted)
                val newPrompt = src.copy(id = 0, title = newTitle, order = insertIndex)
                repository.savePrompt(newPrompt)
                // Reindex to 0..n
                val finalList = repository.getAllPrompts().sortedBy { it.order }.mapIndexed { index, p -> p.copy(order = index) }
                repository.updatePrompts(finalList)
                onDone?.invoke()
            } catch (_: Exception) {
                onDone?.invoke()
            }
        }
    }
}
