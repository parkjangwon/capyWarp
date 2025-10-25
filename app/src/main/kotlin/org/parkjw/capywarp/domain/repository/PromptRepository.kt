package org.parkjw.capywarp.domain.repository

import org.parkjw.capywarp.data.local.PromptDatabase
import org.parkjw.capywarp.data.model.Prompt
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PromptRepository @Inject constructor(
    private val db: PromptDatabase
) {
    fun getPrompts(): Flow<List<Prompt>> = db.promptDao().getPrompts()

    suspend fun getAllPrompts(): List<Prompt> = db.promptDao().getAllPrompts()

    suspend fun getPrompt(id: Int): Prompt? = db.promptDao().getPrompt(id)

    suspend fun getMaxOrder(): Int = db.promptDao().getMaxOrder()

    suspend fun savePrompt(prompt: Prompt) {
        if (prompt.id == 0) {
            // Append to bottom if no explicit order was set (default 0 from editor)
            val next = (db.promptDao().getMaxOrder() + 1).coerceAtLeast(0)
            val toInsert = if (prompt.order == 0) prompt.copy(order = next) else prompt
            db.promptDao().insertPrompt(toInsert)
        } else {
            // Preserve existing order if caller didn't specify (order == 0 is treated as 'keep')
            val current = db.promptDao().getPrompt(prompt.id)
            val toUpdate = if (prompt.order == 0 && current != null) prompt.copy(order = current.order) else prompt
            db.promptDao().updatePrompt(toUpdate)
        }
    }

    suspend fun savePromptAndReturnId(prompt: Prompt): Int {
        return if (prompt.id == 0) {
            // Append to bottom if no explicit order was set
            val next = (db.promptDao().getMaxOrder() + 1).coerceAtLeast(0)
            val toInsert = if (prompt.order == 0) prompt.copy(order = next) else prompt
            db.promptDao().insertPromptReturningId(toInsert).toInt()
        } else {
            // Preserve existing order if not provided
            val current = db.promptDao().getPrompt(prompt.id)
            val toUpdate = if (prompt.order == 0 && current != null) prompt.copy(order = current.order) else prompt
            db.promptDao().updatePrompt(toUpdate)
            prompt.id
        }
    }

    suspend fun updatePrompts(prompts: List<Prompt>) {
        prompts.forEach { db.promptDao().updatePrompt(it) }
    }

    suspend fun deletePrompt(prompt: Prompt) = db.promptDao().deletePrompt(prompt)

    suspend fun importPrompts(prompts: List<Prompt>) = db.promptDao().replaceAllPrompts(prompts)
}
