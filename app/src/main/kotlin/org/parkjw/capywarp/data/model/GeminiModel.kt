package org.parkjw.capywarp.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class GeminiRequest(
    @SerialName("contents")
    val contents: List<GeminiContent>,
    @SerialName("generationConfig")
    val generationConfig: GenerationConfig? = null,
)

@Serializable
data class GenerationConfig(
    @SerialName("responseMimeType")
    val responseMimeType: String? = null,
    @SerialName("responseModalities")
    val responseModalities: List<String>? = null,
)

object GeminiModels {
    const val DEFAULT_TEXT = "gemini-3.5-flash"
    const val DEFAULT_IMAGE = "gemini-3.1-flash-image"

    val TEXT_OPTIONS = listOf(
        DEFAULT_TEXT to "Gemini 3.5 Flash",
        "gemini-3.1-flash-lite" to "Gemini 3.1 Flash-Lite",
        "gemini-3.1-pro-preview" to "Gemini 3.1 Pro Preview",
        "gemini-3-flash-preview" to "Gemini 3 Flash Preview",
    )

    val IMAGE_OPTIONS = listOf(
        DEFAULT_IMAGE to "Gemini 3.1 Flash Image (Nano Banana 2)",
        "gemini-3-pro-image" to "Gemini 3 Pro Image (Nano Banana Pro)",
    )

    fun normalizeTextModel(model: String?): String = when (model) {
        null, "" -> DEFAULT_TEXT
        "gemini-2.5-flash" -> DEFAULT_TEXT
        "gemini-2.5-flash-lite" -> "gemini-3.1-flash-lite"
        "gemini-2.5-pro" -> "gemini-3.1-pro-preview"
        else -> model
    }

    fun normalizeImageModel(model: String?): String = when (model) {
        null, "" -> DEFAULT_IMAGE
        "gemini-2.5-flash-image" -> DEFAULT_IMAGE
        else -> model
    }
}

@Serializable
data class GeminiContent(
    @SerialName("role")
    val role: String? = null,
    @SerialName("parts")
    val parts: List<GeminiPart>
)

@Serializable
data class GeminiPart(
    @SerialName("text")
    val text: String? = null,
    // v1beta에서는 inlineData (camelCase)
    @SerialName("inlineData")
    val inlineData: InlineData? = null,
)

@Serializable
data class InlineData(
    @SerialName("mimeType")
    val mimeType: String? = null,
    @SerialName("data")
    val data: String? = null,
)

@Serializable
data class GeminiResponse(
    @SerialName("candidates")
    val candidates: List<GeminiCandidate> = emptyList(),
)

@Serializable
data class GeminiCandidate(
    @SerialName("content")
    val content: GeminiContent,
)
