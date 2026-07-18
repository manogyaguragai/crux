package com.crux.app.domain

import com.crux.app.domain.model.Task
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

/** One day of the week view: its date, a human label, and the tasks due that day (time order). */
data class WeekDay(val date: LocalDate, val label: String, val tasks: List<Task>)

/**
 * The week view (phases.md: a 7-day ceiling inside the stack tab). Buckets tasks that fall due in
 * the window [today, today+6] by their local day; days with nothing are omitted (emptiness is the
 * feature), so this reads as a calm upcoming agenda, not an empty grid. Overdue and undated tasks
 * are out of scope here — overdue has its own pile, undated has the stack. Pure and zone-aware.
 *
 * Within a day, timed tasks come first in clock order (dueAt carries the time), all-day tasks after,
 * ties broken by priority. The label is friendly for the near days (today / tomorrow) and the
 * weekday name otherwise — unambiguous because the window never spans more than seven days.
 */
fun groupWeek(tasks: List<Task>, today: LocalDate, zone: ZoneId): List<WeekDay> {
    val end = today.plusDays(6)
    val byDay: Map<LocalDate, List<Task>> = tasks
        .mapNotNull { task ->
            val due = task.dueAt ?: return@mapNotNull null
            val date = Instant.ofEpochMilli(due).atZone(zone).toLocalDate()
            if (!date.isBefore(today) && !date.isAfter(end)) date to task else null
        }
        .groupBy({ it.first }, { it.second })

    return (0..6)
        .map { today.plusDays(it.toLong()) }
        .filter { byDay.containsKey(it) }
        .map { date ->
            val ordered = byDay.getValue(date).sortedWith(compareBy({ it.dueAt }, { it.priority }))
            WeekDay(date, weekLabel(date, today), ordered)
        }
}

/**
 * The day-group header (mockup 03 .ghead): the short weekday and day-of-month, e.g. "mon 14", with
 * a "· today" tail on the current day. Unambiguous because the window never spans more than 7 days.
 */
fun weekLabel(date: LocalDate, today: LocalDate): String {
    val weekday = date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.ENGLISH).lowercase(Locale.ENGLISH)
    val base = "$weekday ${date.dayOfMonth}"
    return if (date == today) "$base · today" else base
}
