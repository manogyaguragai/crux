package com.crux.app.intelligence

import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

/**
 * Prompt construction for the LLM layer (intelligence.md, phase 3). Two shapes:
 *  - [captureMessages]: one input line → one action (the omnibar chain).
 *  - [suggestMessages]: the inbox pile → a project guess per task (the review tab).
 *
 * The capture prompt is the verbatim starting point from the doc: temperature 0, a strict JSON
 * contract, a handful of few-shot pairs. Keep the system text terse — every token is quota.
 */
object LlmPrompt {

    private const val SCHEMA_BLOCK = """
{"action":"add","title":"send sov deck","project":"growbydata","priority":1,
 "due":"2026-07-16T14:00","has_time":true,
 "recurrence":{"type":"WEEKDAYS","weekday":null,"day":null}}
{"action":"complete","query":"sov deck"}
{"action":"delete","query":"old ssl task"}
{"action":"reschedule","query":"sov deck","due":"2026-07-18","has_time":false}
{"action":"query","question":"what is due today"}"""

    fun captureMessages(
        input: String,
        today: LocalDate,
        zone: ZoneId,
        projects: List<KnownProject>,
    ): List<ChatMessage> {
        val weekday = today.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.ENGLISH)
        val projectList = projects.takeIf { it.isNotEmpty() }
            ?.joinToString(", ") { it.name } ?: "(none yet)"
        val system = """
You convert one line of a personal task app's input into JSON.
Today is $weekday $today. Timezone: ${zone.id}.
Existing projects: $projectList.

Return ONLY a JSON object, no prose, no code fences, matching exactly one of
these shapes: add | complete | delete | reschedule | query
${SCHEMA_BLOCK.trim()}

Rules:
- If the line is a thing to do, action is "add".
- Verbs like tick, done, finished mean "complete". Remove, scrap mean "delete".
  Move, push, postpone mean "reschedule". Questions mean "query".
- "project" must be one of the existing projects or null. Never invent one.
- Dates are ISO-8601 local. Resolve relative words against today.
- Anything you cannot determine is null. Do not guess priorities.
""".trim()

        val shots = listOf(
            "call the vendor about the serp dashboards" to
                """{"action":"add","title":"call the vendor about the serp dashboards","project":null,"priority":null,"due":null,"has_time":false,"recurrence":{"type":null,"weekday":null,"day":null}}""",
            "tick the deck i sent this morning" to
                """{"action":"complete","query":"deck i sent this morning"}""",
            "push the vendor call to friday" to
                """{"action":"reschedule","query":"vendor call","due":"2026-07-24","has_time":false}""",
            "whats overdue" to
                """{"action":"query","question":"what is overdue"}""",
        )

        return buildList {
            add(ChatMessage("system", system))
            shots.forEach { (u, a) ->
                add(ChatMessage("user", u))
                add(ChatMessage("assistant", a))
            }
            add(ChatMessage("user", input))
        }
    }

    fun suggestMessages(
        inbox: List<InboxTask>,
        projects: List<KnownProject>,
        today: LocalDate,
    ): List<ChatMessage> {
        val projectList = projects.joinToString(", ") { it.name }
        val taskLines = inbox.joinToString("\n") { "${it.id}: ${it.title}" }
        val system = """
You file unsorted tasks under a personal project. Today is $today.
Projects: $projectList.

For each task below, decide which project it most likely belongs to, or skip it if
none clearly fits. Return ONLY this JSON, no prose:
{"suggestions":[{"id":<task id>,"project":"<one of the projects>","reason":"<max 8 words>"}]}

Rules:
- "project" must be exactly one of the listed projects. Never invent one.
- Only include a task when the fit is clear. Omit the rest.
- "reason" is a short, plain phrase, lowercase, no punctuation at the end.
""".trim()
        return listOf(
            ChatMessage("system", system),
            ChatMessage("user", taskLines),
        )
    }
}

/** The minimum a task needs to be shown to the model for project inference (id + title only). */
data class InboxTask(val id: Long, val title: String)
