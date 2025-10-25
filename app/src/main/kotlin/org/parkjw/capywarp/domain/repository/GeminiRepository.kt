package org.parkjw.capywarp.domain.repository

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import org.parkjw.capywarp.data.model.*
import org.parkjw.capywarp.data.remote.GeminiApiService
import retrofit2.HttpException
import javax.inject.Inject
import javax.inject.Singleton
import java.net.SocketTimeoutException

@Singleton
class GeminiRepository @Inject constructor(
    private val api: GeminiApiService,
    private val settingsRepository: SettingsRepository
) {
    private fun extractFirstDataUriOrBase64(text: String): String {
        // 코드블록/마크다운 제거 후 첫 줄 처리
        val cleaned = text
            .replace("```", "\n")
            .replace("\r", "")
            .trim()
        // data:image... 패턴 우선 탐색
        val dataUriRegex = Regex("data:image/[a-zA-Z0-9+.-]+;base64,[A-Za-z0-9+/=]+")
        val match = dataUriRegex.find(cleaned)
        if (match != null) return match.value
        // 첫 줄만 취해 불필요한 설명 제거 시도
        val firstLine = cleaned.lineSequence().firstOrNull()?.trim() ?: cleaned
        // 백틱/따옴표 제거
        val line = firstLine.trim('`', '"', '\'', ' ')
        // data: 접두가 없으면 Base64만 반환 (호출 측 디코더가 처리 가능)
        return line
    }

    suspend fun generateContent(text: String, prompt: Prompt, attachmentBytes: ByteArray? = null, attachmentMime: String? = null): String {
        // API 키 사전 점검
        val apiKey = settingsRepository.getApiKey()
        if (apiKey.isBlank()) {
            throw Exception("Gemini API 키가 설정되어 있지 않습니다. 설정 화면에서 키를 입력해 주세요.")
        }
        // $TEXT 플레이스홀더가 없으면 전역 설정에 따라 선택 텍스트를 자동 첨부합니다.
        val basePrompt = if (prompt.template.contains("\$TEXT")) {
            prompt.template.replace("\$TEXT", text)
        } else {
            val autoAttach = try { settingsRepository.autoAttachSelectedText.first() } catch (_: Exception) { true }
            val position = try { settingsRepository.autoAttachPosition.first() } catch (_: Exception) { "top" }
            if (!autoAttach || text.isBlank()) {
                prompt.template
            } else {
                val sep = "\n\n---\n"
                if (position == "top") {
                    buildString {
                        append(text)
                        append(sep)
                        append(prompt.template.trim())
                    }
                } else {
                    buildString {
                        append(prompt.template.trim())
                        append(sep)
                        append(text)
                    }
                }
            }
        }

        val hasAttachment = attachmentBytes != null
        val isImageOutput = prompt.outputType == 1
        val isImageInput = (attachmentMime ?: "").startsWith("image/") && hasAttachment
        val isImageMode = isImageOutput || isImageInput
        // 이미지 출력 요청은 v1beta generateContent에서 바이너리 MIME 응답을 허용하지 않으므로,
        // 모델이 텍스트로 data URI 한 줄만 반환하도록 지시한다.
        val finalPrompt = if (isImageOutput) buildString {
            appendLine("다음 지시에 따라 이미지를 생성하고, 결과 이미지를 오직 한 줄의 데이터 URI로만 출력하세요.")
            appendLine("형식: data:image/png;base64,AAAA... (설명/마크다운/코드블록 없이 순수 문자열 한 줄)")
            appendLine()
            append(basePrompt)
        } else basePrompt

        // 사용자 프롬프트(항상 적용) 병합
        val userPrompt = try { settingsRepository.userPrompt.first() } catch (_: Exception) { "" }
        val mergedPrompt = if (userPrompt.isNotBlank()) buildString {
            appendLine("다음은 사용자의 지속 지시입니다. 모든 응답에 반드시 반영하세요:")
            appendLine(userPrompt.trim())
            appendLine()
            append(finalPrompt)
        } else finalPrompt

        val parts = buildList {
            if (hasAttachment) {
                val mime = (attachmentMime ?: "application/octet-stream")
                val b64 = android.util.Base64.encodeToString(attachmentBytes, android.util.Base64.NO_WRAP)
                val inlineSupported = mime.startsWith("image/") ||
                        mime == "application/pdf" ||
                        mime.startsWith("text/") ||
                        mime == "application/json"
                if (inlineSupported) {
                    // Use inlineData for supported MIME types
                    add(
                        GeminiPart(
                            inlineData = InlineData(
                                mimeType = mime,
                                data = b64
                            )
                        )
                    )
                } else {
                    // Fallback for Office and other unsupported types: provide as Base64 text
                    // so the model can still reason about the content.
                    add(
                        GeminiPart(
                            text = buildString {
                                appendLine("An attachment is included but the MIME is not supported by inline data: ${mime}.")
                                appendLine("The following is the Base64 content between the markers. If possible, decode and extract text to summarize or answer the user query.")
                                appendLine("BEGIN_BASE64 ${mime}")
                                appendLine(b64)
                                appendLine("END_BASE64")
                            }
                        )
                    )
                }
            }
            add(GeminiPart(text = mergedPrompt))
        }

        val request = GeminiRequest(
            contents = listOf(
                GeminiContent(
                    role = "user",
                    parts = parts
                )
            ),
            // 이미지 출력 모드도 텍스트 MIME만 허용됨: 400 방지 위해 text/plain으로 요청하거나 null
            generationConfig = if (isImageOutput) GenerationConfig(responseMimeType = "text/plain") else null
        )

        val model = if (isImageMode) {
            settingsRepository.imageModel.first()
        } else {
            settingsRepository.model.first()
        }

        // 429 대비 간단한 재시도 (최대 2회 추가, 총 3회 시도)
        var attempt = 0
        val maxAttempts = 3
        while (attempt < maxAttempts) {
            try {
                val body = api.generateContent(model, request)
                val raw = body.string()
                // 1) JSON 시도
                val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
                try {
                    val resp = json.decodeFromString(org.parkjw.capywarp.data.model.GeminiResponse.serializer(), raw)
                    val part = resp.candidates.firstOrNull()?.content?.parts?.firstOrNull()
                        ?: throw Exception("응답이 비어있습니다.")
                    return if (isImageOutput) {
                        val inline = part.inlineData?.data
                        val textOut = part.text
                        when {
                            inline != null -> inline
                            textOut != null -> extractFirstDataUriOrBase64(textOut)
                            else -> throw Exception("이미지 데이터가 응답에 없습니다.")
                        }
                    } else {
                        part.text ?: throw Exception("텍스트 응답이 비어있습니다.")
                    }
                } catch (se: Exception) {
                    // 2) 이미지인 경우, raw 텍스트에서 data URI 또는 Base64를 추출 시도
                    if (isImageOutput) {
                        val extracted = extractFirstDataUriOrBase64(raw)
                        val hasDataUri = extracted.startsWith("data:image")
                        val looksLikeBase64 = extracted.length > 80 && extracted.all { it.isLetterOrDigit() || it == '+' || it == '/' || it == '=' || it == ',' || it == ':' || it == ';' }
                        if (hasDataUri || looksLikeBase64) {
                            return extracted
                        }
                    }
                    // 파싱 형식 오류는 최대 3회까지 재시도
                    val msg = "AI 응답 형식이 예상과 다릅니다: ${'$'}{raw.take(200)}"
                    if (attempt + 1 < maxAttempts) {
                        val backoff = 250L * (1 shl attempt)
                        delay(backoff)
                        attempt++
                        continue
                    } else {
                        throw Exception(msg)
                    }
                }
            } catch (e: HttpException) {
                if (e.code() == 429) {
                    val retryAfter = e.response()?.headers()?.get("Retry-After")?.toLongOrNull()
                    val backoff = retryAfter?.coerceIn(1, 10)?.times(1000)
                        ?: (800L * (1 shl attempt))
                    delay(backoff)
                    attempt++
                    continue
                } else {
                    val body = try { e.response()?.errorBody()?.string() } catch (_: Exception) { null }
                    val detail = body?.take(500)?.replace('\n', ' ')
                    val msg = if (detail.isNullOrBlank()) {
                        "HTTP ${e.code()} ${e.message()}"
                    } else {
                        "HTTP ${e.code()} ${e.message()} - ${detail}"
                    }
                    throw Exception("AI 응답 생성 중 오류가 발생했습니다: ${msg}")
                }
            } catch (e: SocketTimeoutException) {
                // 타임아웃도 제한적 재시도
                val backoff = 1000L * (1 shl attempt)
                delay(backoff)
                attempt++
                if (attempt >= maxAttempts) {
                    throw Exception("요청 시간이 초과되었습니다. 네트워크 상태를 확인하거나 잠시 후 다시 시도해 주세요.")
                }
                continue
            } catch (e: Exception) {
                // 형식 오류 문구가 포함된 경우에도 루프 재시도
                if ((e.message ?: "").contains("AI 응답 형식이 예상과 다릅니다") && attempt + 1 < maxAttempts) {
                    val backoff = 250L * (1 shl attempt)
                    delay(backoff)
                    attempt++
                    continue
                }
                throw Exception("AI 응답 생성 중 오류가 발생했습니다: ${e.message}")
            }
        }
        throw Exception("요청이 너무 많습니다(429). 잠시 후 다시 시도해 주세요.")
    }
}
