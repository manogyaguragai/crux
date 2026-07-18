package com.crux.app.intelligence

/**
 * The LLM providers the owner can bring a key for (intelligence.md, phase 3). The owner picks ONE and
 * pastes ONE key; on failure the chain falls back to rules-only, never to a second provider. All three
 * speak the OpenAI-compatible `chat/completions` shape with an `Authorization: Bearer <key>` header,
 * so a single [LlmClient] serves them all — only the endpoint, model list, and "where to get a key"
 * copy differ.
 *
 * [models] is an ORDERED list of candidates: the client tries each until one answers, so a single
 * sunset id (which is exactly what bit Gemini's `2.5-flash-lite`, and rotates constantly on
 * OpenRouter's free tier) does not break the provider. First entry is the preferred model. These live
 * here in one place, per the doc; verify them against the provider when this phase is revisited.
 *
 * OpenRouter is the no-card path: a free account with a $0 balance can use `:free` models at ~20/min,
 * ~50/day — enough for one person's task parsing — without any billing set up.
 */
enum class LlmProvider(
    val id: String,
    val displayName: String,
    val endpoint: String,
    val models: List<String>,
    val keyUrl: String,
    val keyHint: String,
) {
    GEMINI(
        id = "gemini",
        displayName = "gemini",
        endpoint = "https://generativelanguage.googleapis.com/v1beta/openai/chat/completions",
        models = listOf("gemini-2.0-flash"), // 2.5-flash-lite was sunset for new keys (404); 2.0-flash is the free GA default
        keyUrl = "https://aistudio.google.com/apikey",
        keyHint = "free tier needs billing in some regions",
    ),
    OPENAI(
        id = "openai",
        displayName = "chatgpt",
        endpoint = "https://api.openai.com/v1/chat/completions",
        models = listOf("gpt-4o-mini"), // cheapest broadly-available chat model; the key is the owner's, billed to them
        keyUrl = "https://platform.openai.com/api-keys",
        keyHint = "needs a funded account",
    ),
    OPENROUTER(
        id = "openrouter",
        displayName = "openrouter",
        endpoint = "https://openrouter.ai/api/v1/chat/completions",
        // Ordered free candidates (July 2026); the client advances past any that a key can't use.
        models = listOf(
            "meta-llama/llama-3.3-70b-instruct:free",
            "qwen/qwen3-next-80b-a3b-instruct:free",
            "openai/gpt-oss-20b:free",
            "google/gemma-4-31b-it:free",
        ),
        keyUrl = "https://openrouter.ai/keys",
        keyHint = "free, no card · starts with sk-or-",
    );

    companion object {
        /** Resolve a persisted provider id back to the enum; null for absent/unknown ids. */
        fun fromId(id: String?): LlmProvider? = entries.firstOrNull { it.id == id }
    }
}
