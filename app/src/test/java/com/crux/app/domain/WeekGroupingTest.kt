package com.crux.app.domain

import com.crux.app.domain.model.Task
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** The 7-day week view: windowing, day bucketing, empty-day omission, and in-day time order. */
class WeekGroupingTest {

    private val zone: ZoneId = ZoneId.of("UTC")
    private val today = LocalDate.of(2026, 1, 12) // a Monday

    private fun task(id: Long, date: LocalDate?, time: LocalTime? = null, priority: Int = 3) = Task(
        id = id,
        title = "t$id",
        dueAt = date?.atTime(time ?: LocalTime.MIDNIGHT)?.atZone(zone)?.toInstant()?.toEpochMilli(),
        hasTime = time != null,
        priority = priority,
        createdAt = 0,
    )

    @Test fun `buckets tasks into their day and omits empty days`() {
        val tasks = listOf(
            task(1, today),                 // today
            task(2, today.plusDays(2)),     // wednesday
            task(3, today.plusDays(2)),     // wednesday
        )
        val week = groupWeek(tasks, today, zone)
        assertEquals(2, week.size) // only the two non-empty days
        assertEquals(today, week[0].date)
        assertEquals("mon 12 · today", week[0].label)
        assertEquals(1, week[0].tasks.size)
        assertEquals(today.plusDays(2), week[1].date)
        assertEquals(2, week[1].tasks.size)
    }

    @Test fun `excludes overdue, undated, and beyond-a-week tasks`() {
        val tasks = listOf(
            task(1, today.minusDays(1)),   // overdue -> out
            task(2, null),                 // undated -> out
            task(3, today.plusDays(7)),    // 8 days out -> out (ceiling is +6)
            task(4, today.plusDays(6)),    // last day in window -> in
        )
        val week = groupWeek(tasks, today, zone)
        assertEquals(1, week.size)
        assertEquals(today.plusDays(6), week[0].date)
        assertEquals(4L, week[0].tasks.single().id)
    }

    @Test fun `labels short weekday and day-of-month, with a today tail`() {
        assertEquals("mon 12 · today", weekLabel(today, today))
        assertEquals("tue 13", weekLabel(today.plusDays(1), today))
        assertEquals("wed 14", weekLabel(today.plusDays(2), today))
    }

    @Test fun `within a day, timed tasks sort by clock then all-day, ties by priority`() {
        val tasks = listOf(
            task(1, today, time = LocalTime.of(15, 0)),  // 3pm
            task(2, today, time = LocalTime.of(9, 0)),   // 9am
            task(3, today),                              // all-day (midnight) -> first by dueAt
            task(4, today, priority = 1),                // all-day p1 -> before all-day p3
        )
        val day = groupWeek(tasks, today, zone).single()
        // midnight (all-day) sorts before timed; among the two all-day, p1 before p3
        assertEquals(listOf(4L, 3L, 2L, 1L), day.tasks.map { it.id })
    }
}
