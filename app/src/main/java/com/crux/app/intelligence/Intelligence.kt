package com.crux.app.intelligence

import com.crux.app.data.SecureKeyStore
import com.crux.app.data.SettingsRepository
import com.crux.app.domain.model.Task
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.time.LocalDate
import java.time.ZoneId

/** One project guess for an unfiled task (review tab): which project, and a one-line why. */
data class ProjectSuggestion(val taskId: Long, val projectName: String, val reason: String)

/**
 * The intelligence chain (intelligence.md, phase 3). Rules always run first and elsewhere; this class
 * is only the optional LLM step layered on top. Every entry point is gated three ways — AI switched
 * on, a provider chosen, a key stored — and guarded by a daily call budget. When any gate is shut or
 * the network call comes back empty, the methods return "nothing", and the caller proceeds on rules
 * alone. That is the whole "AI off changes nothing" contract, enforced in one place.
 */
class Intelligence(
    private val settings: SettingsRepository,
    private val keys: SecureKeyStore,
    private val client: LlmClient,
) {

    /** The active provider — on, chosen, and keyed — or null if the LLM step should be skipped. */
    suspend fun activeProvider(): LlmProvider? {
        if (!settings.aiEnabled.first()) return null
        val provider = settings.aiProvider.first() ?: return null
        val keyed = withContext(Dispatchers.IO) { keys.hasKey(provider.id) }
        return provider.takeIf { keyed }
    }

    /** True when the omnibar should present itself as AI-capable (drives placeholder/affordances). */
    suspend fun isActive(): Boolean = activeProvider() != null

    /**
     * Interpret one omnibar line into an action, or null to fall back to rules-only. Null covers every
     * off/unavailable path: AI off, no key, budget spent, network failure, or an unparseable reply.
     */
    suspend fun interpret(
        input: String,
        today: LocalDate,
        zone: ZoneId,
        projects: List<KnownProject>,
    ): LlmAction? {
        val provider = activeProvider() ?: return null
        if (settings.aiCallsToday(today) >= SettingsRepository.AI_DAILY_CAP) return null
        val key = withContext(Dispatchers.IO) { keys.keyFor(provider.id) } ?: return null
        val messages = LlmPrompt.captureMessages(input, today, zone, projects)
        settings.recordAiCall(today) // count the send: quota is consumed whether or not the reply parses
        val raw = client.chat(provider, key, messages) ?: return null
        return parseLlmAction(raw)
    }

    /**
     * Ask the model to file the inbox pile under existing projects (the review tab's proposals). Returns
     * an empty list on any off/unavailable path. Only suggestions whose id and project both check out
     * against what we sent survive — the model can never point at a task or project we did not offer.
     */
    suspend fun suggestProjects(
        inbox: List<Task>,
        projects: List<KnownProject>,
        today: LocalDate,
    ): List<ProjectSuggestion> {
        if (inbox.isEmpty() || projects.isEmpty()) return emptyList()
        val provider = activeProvider() ?: return emptyList()
        if (settings.aiCallsToday(today) >= SettingsRepository.AI_DAILY_CAP) return emptyList()
        val key = withContext(Dispatchers.IO) { keys.keyFor(provider.id) } ?: return emptyList()
        val messages = LlmPrompt.suggestMessages(inbox.map { InboxTask(it.id, it.title) }, projects, today)
        settings.recordAiCall(today)
        val raw = client.chat(provider, key, messages) ?: return emptyList()
        return parseSuggestions(raw, inbox.map { it.id }.toSet(), projects.map { it.name }.toSet())
    }

    private fun parseSuggestions(raw: String, validIds: Set<Long>, validProjects: Set<String>): List<ProjectSuggestion> {
        val start = raw.indexOf('{')
        val end = raw.lastIndexOf('}')
        if (start < 0 || end <= start) return emptyList()
        val root = runCatching { JSONObject(raw.substring(start, end + 1)) }.getOrNull() ?: return emptyList()
        val arr = root.optJSONArray("suggestions") ?: return emptyList()
        val out = mutableListOf<ProjectSuggestion>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val id = o.optLong("id", -1L)
            val project = o.optString("project").trim()
            val reason = o.optString("reason").trim().ifBlank { "looks related" }
            // Validate against what we actually sent; drop anything invented, and one guess per task.
            if (id in validIds && validProjects.any { it.equals(project, ignoreCase = true) } &&
                out.none { it.taskId == id }
            ) {
                val canonical = validProjects.first { it.equals(project, ignoreCase = true) }
                out += ProjectSuggestion(id, canonical, reason)
            }
        }
        return out
    }
}
