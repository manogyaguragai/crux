package com.crux.app.intelligence

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** One chat message in the OpenAI-compatible request. */
data class ChatMessage(val role: String, val content: String)

/** Why a call could not produce an answer — surfaced to the AI status icon as a plain message. */
enum class AiErrorKind { QUOTA, RATE_LIMIT, NETWORK, AUTH, FAILED }

/** The outcome of a [LlmClient.chat] call: the model text, or a classified failure. */
sealed interface ChatResult {
    data class Ok(val content: String) : ChatResult
    data class Failed(val kind: AiErrorKind) : ChatResult
}

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
        data class Retry(val kind: AiErrorKind) : Attempt // this model failed; try the next candidate
        data class Stop(val kind: AiErrorKind) : Attempt  // terminal (bad key / no network); stop now
    }

    suspend fun chat(provider: LlmProvider, apiKey: String, messages: List<ChatMessage>): ChatResult =
        withContext(Dispatchers.IO) {
            var lastKind = AiErrorKind.FAILED
            for (model in provider.models) {
                when (val attempt = attempt(provider, model, apiKey, messages)) {
                    is Attempt.Ok -> return@withContext ChatResult.Ok(attempt.content)
                    is Attempt.Retry -> lastKind = attempt.kind // remember why, try the next candidate
                    is Attempt.Stop -> return@withContext ChatResult.Failed(attempt.kind)
                }
            }
            ChatResult.Failed(lastKind) // every candidate failed; report the last reason
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
                    Attempt.Retry(AiErrorKind.FAILED)
                }
            }
            val err = conn.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
            // A bad key is terminal (no other model helps). Everything else advances to the next
            // candidate — on OpenRouter's free pool a different model routes through a different,
            // un-throttled upstream, so a 429/5xx on one model is often fine on the next.
            return when {
                code == HttpURLConnection.HTTP_UNAUTHORIZED || code == HttpURLConnection.HTTP_FORBIDDEN ->
                    Attempt.Stop(AiErrorKind.AUTH)
                code == 429 && looksLikeQuota(err) -> Attempt.Retry(AiErrorKind.QUOTA)
                code == 429 -> Attempt.Retry(AiErrorKind.RATE_LIMIT)
                else -> Attempt.Retry(AiErrorKind.FAILED)
            }
        } catch (_: Exception) {
            // No network / timeout / TLS: trying the other models would just be more timeouts, so stop.
            return Attempt.Stop(AiErrorKind.NETWORK)
        } finally {
            conn?.disconnect()
        }
    }

    /** Tell a hard "no quota / billing needed" (Gemini `limit: 0`, OpenAI `insufficient_quota`) apart
     *  from a transient upstream rate-limit, so the icon can say the right thing. */
    private fun looksLikeQuota(body: String): Boolean {
        val b = body.lowercase()
        return "insufficient_quota" in b || ("quota" in b && ("billing" in b || "limit: 0" in b || "limit:0" in b))
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
    }
}
