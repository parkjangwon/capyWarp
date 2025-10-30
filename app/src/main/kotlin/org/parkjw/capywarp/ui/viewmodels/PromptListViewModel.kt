package org.parkjw.capywarp.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.parkjw.capywarp.data.model.Prompt
import org.parkjw.capywarp.domain.repository.PromptRepository
import javax.inject.Inject

@HiltViewModel
class PromptListViewModel @Inject constructor(
    private val repository: PromptRepository
) : ViewModel() {
    val prompts = repository.getPrompts()

    // Search query state
    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query

    // Filtered prompts based on query (title or template, case-insensitive)
    val filteredPrompts: StateFlow<List<Prompt>> = prompts
        .combine(_query) { list, q ->
            val queryText = q.trim()
            if (queryText.isBlank()) {
                list
            } else {
                val lower = queryText.lowercase()
                list.filter { p ->
                    p.title.contains(queryText, ignoreCase = true) ||
                        p.template.contains(queryText, ignoreCase = true)
                }
            }
        }
        .map { it.sortedBy { p -> p.order } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun updateQuery(newQuery: String) { _query.value = newQuery }
    fun clearQuery() { _query.value = "" }

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
                val candidateBase = (src.title.ifBlank { "Prompt" }) + " Copy"
                var newTitle = candidateBase
                val existingTitles = all.map { it.title }.toSet()
                if (existingTitles.contains(newTitle)) {
                    var n = 2
                    while (existingTitles.contains("$candidateBase $n")) {
                        n++
                    }
                    newTitle = "$candidateBase $n"
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
