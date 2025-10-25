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

// Keep meta for future-proofing, but only persist DB version in backups.
@Serializable
data class BackupMeta(
    val dbVersion: Int
)

@Serializable
data class BackupPayload(
    val prompts: List<Prompt>,
    val settings: SettingsData,
    val meta: BackupMeta? = null
)
