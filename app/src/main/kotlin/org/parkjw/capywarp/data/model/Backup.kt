package org.parkjw.capywarp.data.model

import kotlinx.serialization.Serializable

@Serializable
data class SettingsData(
    val apiKey: String,
    val model: String,
    val imageModel: String,
    val theme: String,
    val userPrompt: String,
    val language: String = "system",
    val autoAttachSelectedText: Boolean = true,
    val autoAttachPosition: String = "top"
)

@Serializable
data class BackupPayload(
    val prompts: List<Prompt>,
    val settings: SettingsData
)
