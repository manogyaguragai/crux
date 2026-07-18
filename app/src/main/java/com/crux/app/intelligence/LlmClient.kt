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
 * The LLM transport (intelligence.md, phase 3). Every provider speaks the OpenAI-compatible
 * `chat/completions` shape, so one client serves all — only the endpoint/models/key differ, and those
 * come from [LlmProvider]. Deliberately built on [HttpURLConnection] (no networking dependency): the
 * request is one small POST and the app already avoids heavy libs.
 *
 * Contract: return the assistant's message text on HTTP 200, or null on anything terminal — 429, 5xx,
 * an 8-second timeout, a malformed body. Null means "unavailable, fall through to rules". The one thing
 * we DO retry is a different model: [LlmProvider.models] is an ordered candidate list, and a
 * model-not-available response (400/404) advances to the next id — free ids rotate, so a single sunset
 * model must not sink the provider. We never retry the SAME model: quota is the scarce resource.
 */
class LlmClient {

    /** The result of trying one model id. */
    private sealed interface Attempt {
        data class Ok(val content: String) : Attempt
        data object NextModel : Attempt // this model id is unusable (400/404); try the next candidate
        data object GiveUp : Attempt    // terminal: rate limit, auth, network, bad shape — stop
    }

    suspend fun chat(provider: LlmProvider, apiKey: String, messages: List<ChatMessage>): String? =
        withContext(Dispatchers.IO) {
            for (model in provider.models) {
                when (val attempt = attempt(provider, model, apiKey, messages)) {
                    is Attempt.Ok -> return@withContext attempt.content
                    Attempt.NextModel -> Unit // this candidate is busy/gone; try the next one
                    Attempt.GiveUp -> return@withContext null
                }
            }
            null // every candidate was unusable right now — fall back to rules
        }

    private fun attempt(provider: LlmProvider, model: String, apiKey: String, messages: List<ChatMessage>): Attempt {
        val body = requestBody(model, messages).toString()
        var conn: HttpURLConnection? = null
        try {
            conn = (URL(provider.endpoint).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Authorization", "Bearer $apiKey")
                // OpenRouter attribution headers (optional, harmless elsewhere).
                setRequestProperty("HTTP-Referer", "https://github.com/crux-app")
                setRequestProperty("X-Title", "crux")
            }
            conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            if (code == HttpURLConnection.HTTP_OK) {
                val text = conn.inputStream.bufferedReader().use { it.readText() }
                val content = extractContent(text)
                return if (content != null) {
                    Attempt.Ok(content)
                } else {
                    Log.w(TAG, "chat: 200 but no content from ${provider.id}/$model; raw=${text.take(300)}")
                    Attempt.GiveUp
                }
            }
            val err = conn.errorStream?.bufferedReader()?.use { it.readText() }
            Log.w(TAG, "chat: HTTP $code from ${provider.id} model=$model body=${err?.take(400)}")
            // Only a bad key is truly terminal. 400/404 (wrong/sunset model) and 429/5xx (this model or
            // its upstream is busy right now) both mean "try the next candidate" — on OpenRouter's free
            // pool a different model routes through a different, un-throttled provider.
            return when (code) {
                HttpURLConnection.HTTP_UNAUTHORIZED, HttpURLConnection.HTTP_FORBIDDEN -> Attempt.GiveUp
                else -> Attempt.NextModel
            }
        } catch (e: Exception) {
            Log.w(TAG, "chat: exception talking to ${provider.id} at ${provider.endpoint}", e)
            return Attempt.GiveUp // timeout, no network, TLS — all "unavailable", handled by the caller
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
