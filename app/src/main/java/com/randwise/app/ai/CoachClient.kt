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

    suspend fun ask(gatewayUrl: String, request: CoachRequest): CoachResponse =
        json.decodeFromString(CoachResponse.serializer(), post(gatewayUrl, "/api/coach", json.encodeToString(CoachRequest.serializer(), request), 30_000))

    /** A three-month review takes the model longer than a chat answer, and the gateway may retry once. */
    suspend fun checkup(gatewayUrl: String, request: CheckupRequest): CheckupResponse =
        json.decodeFromString(CheckupResponse.serializer(), post(gatewayUrl, "/api/checkup", json.encodeToString(CheckupRequest.serializer(), request), 110_000))

    private suspend fun post(gatewayUrl: String, path: String, body: String, readTimeoutMs: Int): String = withContext(Dispatchers.IO) {
        val url = URL(gatewayUrl.trimEnd('/') + path)
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 8_000
            readTimeout = readTimeoutMs
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "application/json")
            if (BuildConfig.GATEWAY_CLIENT_KEY.isNotEmpty()) setRequestProperty("X-RandWise-Client-Key", BuildConfig.GATEWAY_CLIENT_KEY)
        }
        try {
            conn.outputStream.use { it.write(body.toByteArray()) }
            val code = conn.responseCode
            val text = (if (code in 200..299) conn.inputStream else conn.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) error("Gateway error $code")
            text
        } finally {
            conn.disconnect()
        }
    }
}
