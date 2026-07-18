package com.crux.app.data.queue

import com.crux.app.data.ProjectRepository
import com.crux.app.data.TaskRepository
import com.crux.app.domain.isOverdue
import com.crux.app.domain.model.ParsedBy
import com.crux.app.domain.model.TaskStatus
import com.crux.app.intelligence.FuzzyResult
import com.crux.app.intelligence.Intelligence
import com.crux.app.intelligence.KnownProject
import com.crux.app.intelligence.LlmAction
import com.crux.app.intelligence.LlmOutcome
import com.crux.app.intelligence.ParseField
import com.crux.app.intelligence.ParseResult
import com.crux.app.intelligence.ProjectRef
import com.crux.app.intelligence.matchTask
import com.crux.app.intelligence.parse
import com.crux.app.intelligence.toTask
import kotlinx.coroutines.flow.first
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * The autonomous capture pipeline behind the queue: rules first, then the optional LLM, then act — all
 * UI-free so a background worker can run it. It is the same chain the omnibar used to run inline, but
 * without interactive sheets: a command that resolves to a single clear fuzzy match (>=0.75) executes;
 * anything ambiguous or unmatched FAILS with a message (shown on the queue item, retryable). Adds never
 * fail — rules always yield at least a title-only task, so the queue's only failures are commands that
 * could not be resolved without a human choice. Delete is a soft archive, so an over-eager match is
 * recoverable.
 */
class CaptureProcessor(
    private val tasks: TaskRepository,
    private val projects: ProjectRepository,
    private val intelligence: Intelligence,
) {

    suspend fun process(text: String, dismissed: Set<ParseField>): QueueResult {
        if (text.isBlank()) return QueueResult.Done(null)
        val now = System.currentTimeMillis()
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val known = projects.observeActive().first().map { KnownProject(it.id, it.name) }
        val rules = parse(text, today, known, dismissed)
        return when (val outcome = intelligence.interpret(text, today, zone, known)) {
            is LlmOutcome.Acted -> when (val action = outcome.action) {
                is LlmAction.Add -> addTask(text, rules, action, now, zone)
                is LlmAction.Complete -> doComplete(action.query, now, zone)
                is LlmAction.Delete -> doDelete(action.query)
                is LlmAction.Reschedule -> doReschedule(action, zone)
                is LlmAction.Query -> doQuery(action.question, zone)
            }
            // AI off or unavailable: file on rules alone. The AI status icon shows the "why" for a
            // failed call; the queue item just records a successful add.
            LlmOutcome.Unavailable, LlmOutcome.Inactive -> addTask(text, rules, null, now, zone)
        }
    }

    private suspend fun addTask(text: String, rules: ParseResult, ai: LlmAction.Add?, now: Long, zone: ZoneId): QueueResult {
        var aiTouched = false
        var projectId = when (val p = rules.project) {
            is ProjectRef.Matched -> p.id
            is ProjectRef.Unknown -> projects.create(p.raw, now)
            null -> null
        }
        if (projectId == null && ai?.project != null) {
            val known = projects.observeActive().first().firstOrNull { it.name.equals(ai.project, ignoreCase = true) }
            if (known != null) { projectId = known.id; aiTouched = true }
        }
        val safe = if (rules.title.isBlank()) rules.copy(title = text.trim()) else rules
        var task = safe.toTask(projectId, zone, now)
        if (ai != null) {
            if (task.dueAt == null && ai.due != null) {
                val withTime = ai.hasTime && ai.time != null
                val local = ai.due.atTime(if (withTime) ai.time else LocalTime.MIDNIGHT)
                task = task.copy(dueAt = local.atZone(zone).toInstant().toEpochMilli(), hasTime = withTime)
                aiTouched = true
            }
            if (rules.priority == null && ai.priority != null) { task = task.copy(priority = ai.priority); aiTouched = true }
            if (rules.recurrenceType == null && ai.recurrenceType != null) {
                task = task.copy(
                    recurrenceType = ai.recurrenceType,
                    recurrenceWeekday = ai.recurrenceWeekday,
                    recurrenceDay = ai.recurrenceDay,
                )
                aiTouched = true
            }
        }
        if (aiTouched) task = task.copy(parsedBy = ParsedBy.AI)
        tasks.add(task)
        return QueueResult.Done(null) // a plain add: the resulting task IS the feedback
    }

    private suspend fun doComplete(query: String, now: Long, zone: ZoneId): QueueResult =
        when (val m = matchTask(query, tasks.observeOpen().first())) {
            is FuzzyResult.One -> {
                tasks.complete(m.task, now, zone)
                QueueResult.Done("completed ${m.task.title}")
            }
            is FuzzyResult.Many -> QueueResult.Failed("several tasks match \"$query\"")
            FuzzyResult.None -> QueueResult.Failed("couldn't find \"$query\"")
        }

    private suspend fun doDelete(query: String): QueueResult =
        when (val m = matchTask(query, tasks.observeOpen().first())) {
            is FuzzyResult.One -> {
                tasks.updateTask(m.task.copy(status = TaskStatus.ARCHIVED))
                QueueResult.Done("archived ${m.task.title}")
            }
            is FuzzyResult.Many -> QueueResult.Failed("several tasks match \"$query\"")
            FuzzyResult.None -> QueueResult.Failed("couldn't find \"$query\"")
        }

    private suspend fun doReschedule(action: LlmAction.Reschedule, zone: ZoneId): QueueResult {
        val due = action.due ?: return QueueResult.Failed("no new date in \"${action.query}\"")
        val withTime = action.hasTime && action.time != null
        val newDueAt = due.atTime(if (withTime) action.time else LocalTime.MIDNIGHT).atZone(zone).toInstant().toEpochMilli()
        return when (val m = matchTask(action.query, tasks.observeOpen().first())) {
            is FuzzyResult.One -> {
                tasks.updateTask(m.task.copy(dueAt = newDueAt, hasTime = withTime))
                QueueResult.Done("moved ${m.task.title} to ${dateLabel(newDueAt, zone)}")
            }
            is FuzzyResult.Many -> QueueResult.Failed("several tasks match \"${action.query}\"")
            FuzzyResult.None -> QueueResult.Failed("couldn't find \"${action.query}\"")
        }
    }

    private suspend fun doQuery(question: String, zone: ZoneId): QueueResult {
        val open = tasks.observeOpen().first()
        val nowInstant = Instant.now()
        val overdue = open.count { isOverdue(it, nowInstant, zone) }
        val q = question.lowercase()
        val answer = when {
            "overdue" in q -> if (overdue == 0) "nothing overdue" else "$overdue overdue"
            "today" in q -> {
                val today = LocalDate.now(zone)
                val due = open.count { t -> t.dueAt?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() == today } == true }
                if (due == 0) "nothing due today" else "$due due today"
            }
            else -> "${open.size} open, $overdue overdue"
        }
        return QueueResult.Done(answer)
    }

    private fun dateLabel(dueAt: Long, zone: ZoneId): String =
        Instant.ofEpochMilli(dueAt).atZone(zone).toLocalDate().format(DATE_LABEL).lowercase(Locale.ENGLISH)

    private companion object {
        val DATE_LABEL: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d", Locale.ENGLISH)
    }
}
