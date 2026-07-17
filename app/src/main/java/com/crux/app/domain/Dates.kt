package com.crux.app.domain

import com.crux.app.domain.model.Task
import com.crux.app.domain.model.TaskStatus
import java.time.Instant
import java.time.ZoneId

/**
 * Overdue means the moment has genuinely passed. A timed task is overdue once its instant is behind
 * now; an all-day task is overdue only once the whole day is behind (start of today), so today's
 * all-day tasks read as due, not overdue at 00:01. Only OPEN tasks can be overdue.
 */
fun isOverdue(task: Task, now: Instant, zone: ZoneId): Boolean {
    val due = task.dueAt ?: return false
    if (task.status != TaskStatus.OPEN) return false
    return if (task.hasTime) {
        due < now.toEpochMilli()
    } else {
        val startOfToday = now.atZone(zone).toLocalDate().atStartOfDay(zone).toInstant().toEpochMilli()
        due < startOfToday
    }
}
