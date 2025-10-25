package org.parkjw.capywarp.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow
import org.parkjw.capywarp.data.model.Prompt

@Dao
interface PromptDao {
    @Query("SELECT * FROM prompts ORDER BY `order` ASC")
    fun getPrompts(): Flow<List<Prompt>>

    @Query("SELECT * FROM prompts ORDER BY `order` ASC")
    suspend fun getAllPrompts(): List<Prompt>

    @Query("SELECT COALESCE(MAX(`order`), -1) FROM prompts")
    suspend fun getMaxOrder(): Int

    @Query("SELECT * FROM prompts WHERE id = :id")
    suspend fun getPrompt(id: Int): Prompt?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPrompt(prompt: Prompt)

    // For obtaining generated id on insert
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPromptReturningId(prompt: Prompt): Long

    @Update
    suspend fun updatePrompt(prompt: Prompt)

    @Delete
    suspend fun deletePrompt(prompt: Prompt)

    @Transaction
    suspend fun replaceAllPrompts(prompts: List<Prompt>) {
        deleteAllPrompts()
        insertPrompts(prompts)
    }

    @Query("DELETE FROM prompts")
    suspend fun deleteAllPrompts()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPrompts(prompts: List<Prompt>)
}
