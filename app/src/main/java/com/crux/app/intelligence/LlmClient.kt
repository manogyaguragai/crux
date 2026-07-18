package com.crux.app.intelligence

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** One chat message in the OpenAI-compatible request. */
data class ChatMessage(val role: String, val content: String)

/**
 * The LLM transport (intelligence.md, phase 3). Both providers speak the OpenAI-compatible
 * `chat/completions` shape, so one client serves both — only the endpoint/model/key differ, and those
 * come from [LlmProvider]. Deliberately built on [HttpURLConnection] (no networking dependency): the
 * request is one small POST and the app already avoids heavy libs.
 *
 * Contract: return the assistant's message text on HTTP 200, or null on ANYTHING else — 429, 5xx, an
 * 8-second timeout, a malformed body. Null means "unavailable, fall through to rules". We never retry:
 * quota is the scarce resource, and the deterministic layer is always underneath as the floor.
 */
class LlmClient {

    suspend fun chat(provider: LlmProvider, apiKey: String, messages: List<ChatMessage>): String? =
        withContext(Dispatchers.IO) {
            val body = requestBody(provider.model, messages).toString()
            var conn: HttpURLConnection? = null
            try {
                conn = (URL(provider.endpoint).openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = TIMEOUT_MS
                    readTimeout = TIMEOUT_MS
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json")
                    setRequestProperty("Authorization", "Bearer $apiKey")
                }
                conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
                val code = conn.responseCode
                if (code != HttpURLConnection.HTTP_OK) {
                    val err = conn.errorStream?.bufferedReader()?.use { it.readText() }
                    Log.w(TAG, "chat: HTTP $code from ${provider.id} model=${provider.model} url=${provider.endpoint} body=$err")
                    return@withContext null
                }
                val text = conn.inputStream.bufferedReader().use { it.readText() }
                val content = extractContent(text)
                if (content == null) Log.w(TAG, "chat: 200 but no content; raw=${text.take(500)}")
                content
            } catch (e: Exception) {
                Log.w(TAG, "chat: exception talking to ${provider.id} at ${provider.endpoint}", e)
                null // timeout, no network, TLS, malformed — all mean "unavailable", handled by the caller
            } finally {
                conn?.disconnect()
            }
        }

    private fun requestBody(model: String, messages: List<ChatMessage>): JSONObject {
        val arr = JSONArray()
        messages.forEach { m ->
            arr.put(JSONObject().put("role", m.role).put("content", m.content))
        }
        return JSONObject()
            .put("model", model)
            .put("temperature", 0)
            .put("max_tokens", MAX_TOKENS)
            .put("messages", arr)
    }

    /** Pull `choices[0].message.content` out of the response, or null if the shape is off. */
    private fun extractContent(body: String): String? {
        val root = runCatching { JSONObject(body) }.getOrNull() ?: return null
        val choice = root.optJSONArray("choices")?.optJSONObject(0) ?: return null
        val content = choice.optJSONObject("message")?.optString("content").orEmpty()
        return content.trim().ifBlank { null }
    }

    private companion object {
        const val TIMEOUT_MS = 8_000
        const val MAX_TOKENS = 256
        const val TAG = "CruxAI"
    }
}
