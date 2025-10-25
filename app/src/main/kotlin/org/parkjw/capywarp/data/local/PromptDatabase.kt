package org.parkjw.capywarp.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import org.parkjw.capywarp.data.model.Prompt

@Database(
    entities = [Prompt::class],
    version = 2,
    exportSchema = true
)
abstract class PromptDatabase : RoomDatabase() {
    abstract fun promptDao(): PromptDao
}
