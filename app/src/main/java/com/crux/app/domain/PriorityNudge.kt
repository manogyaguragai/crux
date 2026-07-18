package com.crux.app.domain

import com.crux.app.domain.model.Task
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Why a task is urgent enough to nudge (drives the reason copy in the UI). */
enum class NudgeUrgency { OVERDUE, DUE_TODAY, DUE_TOMORROW }

/** A proposed priority bump: raise [task] to [targetPriority] because it is [urgency]. */
data class NudgeSuggestion(
    val task: Task,
    val targetPriority: Int,
    val urgency: NudgeUrgency,
)

/**
 * Deterministic priority nudges for the review tab. Surfaces open tasks left at a LOW priority (p3/p4)
 * whose due date has turned urgent, proposing a bump the user can accept or wave off:
 *
 * - overdue, or due today  -> propose p1
 * - due tomorrow           -> propose p2
 *
 * Undated tasks are skipped (no urgency to judge); already-high tasks (p1/p2) are left alone to keep
 * the list calm; a suggestion is only emitted when the target is an actual raise. Ids in [skip] (waved
 * off this session) are excluded. Pure and zone-aware, so it stands with AI off and is unit-testable.
 */
fun priorityNudges(
    open: List<Task>,
    skip: Set<Long>,
    now: Instant,
    today: LocalDate,
    zone: ZoneId,
): List<NudgeSuggestion> = open.mapNotNull { t ->
    if (t.id in skip) return@mapNotNull null
    val due = t.dueAt ?: return@mapNotNull null       // undated: nothing to judge
    if (t.priority < 3) return@mapNotNull null          // only the low pile (p3/p4)
    val dueDate = Instant.ofEpochMilli(due).atZone(zone).toLocalDate()
    val (target, urgency) = when {
        isOverdue(t, now, zone) -> 1 to NudgeUrgency.OVERDUE
        dueDate == today -> 1 to NudgeUrgency.DUE_TODAY
        dueDate == today.plusDays(1) -> 2 to NudgeUrgency.DUE_TOMORROW
        else -> return@mapNotNull null
    }
    if (target >= t.priority) return@mapNotNull null    // only a real bump
    NudgeSuggestion(t, target, urgency)
}
