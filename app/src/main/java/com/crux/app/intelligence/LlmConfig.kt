package com.crux.app.intelligence

/**
 * The LLM providers the owner can bring a key for (intelligence.md, phase 3). The docs originally
 * scoped a fixed Gemini-primary → OpenRouter-fallback chain; the owner's decision (2026-07-18) is
 * simpler: pick ONE provider, paste ONE key, use only that. On failure the chain falls back to
 * rules-only, never to a second provider.
 *
 * Both providers speak the OpenAI-compatible `chat/completions` shape with an `Authorization:
 * Bearer <key>` header, so a single [LlmClient] serves both — only the endpoint, model id, and the
 * user-facing "where to get a key" copy differ. Model ids live here, in one place, per the doc:
 * free-tier ids rotate, so verify [model] against the provider console when this phase is revisited.
 */
enum class LlmProvider(
    val id: String,
    val displayName: String,
    val endpoint: String,
    val model: String,
    val keyUrl: String,
    val keyHint: String,
) {
    GEMINI(
        id = "gemini",
        displayName = "gemini",
        endpoint = "https://generativelanguage.googleapis.com/v1beta/openai/chat/completions",
        model = "gemini-2.5-flash-lite", // free tier; verify current id at aistudio.google.com
        keyUrl = "https://aistudio.google.com/apikey",
        keyHint = "starts with AIza",
    ),
    OPENAI(
        id = "openai",
        displayName = "chatgpt",
        endpoint = "https://api.openai.com/v1/chat/completions",
        model = "gpt-4o-mini", // cheapest broadly-available chat model; the key is the owner's, billed to them
        keyUrl = "https://platform.openai.com/api-keys",
        keyHint = "starts with sk-",
    );

    companion object {
        /** Resolve a persisted provider id back to the enum; null for absent/unknown ids. */
        fun fromId(id: String?): LlmProvider? = entries.firstOrNull { it.id == id }
    }
}
