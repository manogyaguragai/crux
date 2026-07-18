package com.crux.app.intelligence

import com.crux.app.domain.model.RecurrenceType
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * The action schema (intelligence.md, phase 3). The model returns exactly one of these shapes as a
 * JSON object with a single `action` discriminator — never free text. Everything it is unsure of is
 * null. For the destructive verbs the model carries only a `query` string, never a task id or a title
 * to act on directly: the safety contract fuzzy-matches that query against OPEN tasks locally.
 */
sealed interface LlmAction {

    /** A new task. Structured fields only; the app keeps the user's own words as the title (see merge). */
    data class Add(
        val title: String?,
        val project: String?,
        val priority: Int?,
        val due: LocalDate?,
        val time: LocalTime?,
        val hasTime: Boolean,
        val recurrenceType: RecurrenceType?,
        val recurrenceWeekday: Int?,
        val recurrenceDay: Int?,
    ) : LlmAction

    data class Complete(val query: String) : LlmAction
    data class Delete(val query: String) : LlmAction
    data class Reschedule(
        val query: String,
        val due: LocalDate?,
        val time: LocalTime?,
        val hasTime: Boolean,
    ) : LlmAction

    data class Query(val question: String) : LlmAction
}

/**
 * Parse the model's reply into an [LlmAction], or null when it is unusable (the chain then falls back
 * to rules-only). We locate the JSON by slicing from the first `{` to the last `}`, which tolerates
 * stray prose or accidental ``` code fences without a brittle stripper. Parsing is strict past that:
 * a missing discriminator, a bad shape, or a destructive action with no query all return null.
 */
fun parseLlmAction(raw: String): LlmAction? {
    val obj = extractJson(raw) ?: return null
    return when (obj.optString("action").trim().lowercase()) {
        "add" -> parseAdd(obj)
        "complete" -> obj.strOrNull("query")?.let { LlmAction.Complete(it) }
        "delete" -> obj.strOrNull("query")?.let { LlmAction.Delete(it) }
        "reschedule" -> obj.strOrNull("query")?.let { q ->
            val due = readDue(obj)
            LlmAction.Reschedule(q, due.date, due.time, due.hasTime)
        }
        "query" -> obj.strOrNull("question")?.let { LlmAction.Query(it) }
        else -> null
    }
}

private fun parseAdd(obj: JSONObject): LlmAction.Add {
    val due = readDue(obj)
    val recurrence = obj.optJSONObject("recurrence")
    val recType = recurrence?.strOrNull("type")
        ?.let { runCatching { RecurrenceType.valueOf(it.uppercase()) }.getOrNull() }
    return LlmAction.Add(
        title = obj.strOrNull("title"),
        project = obj.strOrNull("project"),
        priority = obj.intOrNull("priority")?.takeIf { it in 1..4 }, // "do not guess priorities": drop out-of-range
        due = due.date,
        time = due.time,
        hasTime = due.hasTime,
        recurrenceType = recType,
        recurrenceWeekday = recurrence?.intOrNull("weekday")?.takeIf { it in 1..7 },
        recurrenceDay = recurrence?.intOrNull("day")?.takeIf { it in 1..31 },
    )
}

/** A parsed `due` + `has_time`: date and (optional) time, with hasTime true only when a time survived. */
private data class Due(val date: LocalDate?, val time: LocalTime?, val hasTime: Boolean)

private fun readDue(obj: JSONObject): Due {
    val raw = obj.strOrNull("due") ?: return Due(null, null, false)
    val wantsTime = obj.optBoolean("has_time", raw.contains('T'))
    // Full datetime first (2026-07-16T14:00), then date-only (2026-07-18). Anything else is dropped.
    runCatching { LocalDateTime.parse(raw) }.getOrNull()?.let {
        return Due(it.toLocalDate(), it.toLocalTime(), true)
    }
    runCatching { LocalDate.parse(raw) }.getOrNull()?.let {
        return Due(it, null, false) // date with no time is all-day regardless of the has_time hint
    }
    return Due(null, null, false)
}

private fun extractJson(raw: String): JSONObject? {
    val start = raw.indexOf('{')
    val end = raw.lastIndexOf('}')
    if (start < 0 || end <= start) return null
    return runCatching { JSONObject(raw.substring(start, end + 1)) }.getOrNull()
}

/** A trimmed non-blank string for [key], or null when absent, JSON-null, or empty. */
private fun JSONObject.strOrNull(key: String): String? {
    if (!has(key) || isNull(key)) return null
    return optString(key).trim().ifBlank { null }
}

/** An int for [key], or null when absent or JSON-null (a non-numeric value also yields null). */
private fun JSONObject.intOrNull(key: String): Int? {
    if (!has(key) || isNull(key)) return null
    return runCatching { getInt(key) }.getOrNull()
}
