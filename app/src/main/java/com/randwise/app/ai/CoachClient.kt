package com.randwise.app.ai

import com.randwise.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.net.HttpURLConnection
import java.net.URL

/** Talks to the RandWise gateway, which holds the Gemini key and injects the system prompt. */
class CoachClient {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    suspend fun ask(gatewayUrl: String, request: CoachRequest): CoachResponse = withContext(Dispatchers.IO) {
        val url = URL(gatewayUrl.trimEnd('/') + "/api/coach")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 8_000
            readTimeout = 30_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "application/json")
            if (BuildConfig.GATEWAY_CLIENT_KEY.isNotEmpty()) setRequestProperty("X-RandWise-Client-Key", BuildConfig.GATEWAY_CLIENT_KEY)
        }
        try {
            conn.outputStream.use { it.write(json.encodeToString(CoachRequest.serializer(), request).toByteArray()) }
            val code = conn.responseCode
            val body = (if (code in 200..299) conn.inputStream else conn.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) error("Gateway error $code")
            json.decodeFromString(CoachResponse.serializer(), body)
        } finally {
            conn.disconnect()
        }
    }
}
