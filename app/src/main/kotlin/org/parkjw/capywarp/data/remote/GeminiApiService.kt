package org.parkjw.capywarp.data.remote

import okhttp3.ResponseBody
import org.parkjw.capywarp.data.model.GeminiRequest
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Path

interface GeminiApiService {
    @POST("v1beta/models/{model}:generateContent")
    suspend fun generateContent(
        @Path("model") model: String,
        @Body request: GeminiRequest
    ): ResponseBody
}
