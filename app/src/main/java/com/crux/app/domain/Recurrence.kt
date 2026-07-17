package com.crux.app.domain

import com.crux.app.domain.model.RecurrenceType
import com.crux.app.domain.model.Task
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * Spawn-on-complete next-due math (data-model.md, reference implementation).
 * Copy this exactly; deviating is a bug even if it seems smarter.
 *
 * java.time is native on minSdk 26. recurrenceWeekday is ISO (Monday = 1),
 * matching DayOfWeek.value. Write the golden tests before touching this.
 */
fun nextDue(task: Task, today: LocalDate, zone: ZoneId): LocalDateTime {
    val base = task.dueAt?.let {
        Instant.ofEpochMilli(it).atZone(zone).toLocalDate()
    } ?: today
    val time = if (task.hasTime)
        Instant.ofEpochMilli(task.dueAt!!).atZone(zone).toLocalTime()
    else LocalTime.MIDNIGHT

    var d = when (task.recurrenceType!!) {
        RecurrenceType.DAILY -> base.plusDays(1)
        RecurrenceType.WEEKDAYS -> {
            var n = base.plusDays(1)
            while (n.dayOfWeek.value > 5) n = n.plusDays(1)
            n
        }
        RecurrenceType.WEEKLY -> {
            var n = base.plusDays(1)
            while (n.dayOfWeek.value != task.recurrenceWeekday!!) n = n.plusDays(1)
            n
        }
        RecurrenceType.MONTHLY -> {
            val m = base.plusMonths(1)
            m.withDayOfMonth(minOf(task.recurrenceDay!!, m.lengthOfMonth()))
        }
    }
    // completed late: roll forward, never stack a backlog
    while (!d.isAfter(today)) {
        d = nextDue(
            task.copy(
                dueAt = d.atTime(time).atZone(zone).toInstant().toEpochMilli()
            ),
            today, zone,
        ).toLocalDate()
    }
    return d.atTime(time)
}
