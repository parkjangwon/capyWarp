package org.parkjw.capywarp.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
@Entity(tableName = "prompts")
data class Prompt(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val title: String,
    val content: String,
    val template: String,
    val resultAction: Int = 0, // 0: 텍스트 교체, 1: 클립보드 복사, 2: 알림
    val outputType: Int = 0, // 0: 텍스트, 1: 이미지
    val isEnabled: Boolean = true,
    val order: Int = 0
)
