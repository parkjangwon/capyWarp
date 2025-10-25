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
    // v1beta generateContent는 camelCase 키를 기대함
    @SerialName("responseMimeType")
    val responseMimeType: String? = null,
)

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
