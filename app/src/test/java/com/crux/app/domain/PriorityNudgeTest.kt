package com.crux.app.domain

import com.crux.app.domain.model.Task
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** The deterministic priority-nudge heuristic: which urgent low-priority tasks get a bump proposal. */
class PriorityNudgeTest {

    private val zone: ZoneId = ZoneId.of("UTC")
    private val today = LocalDate.of(2026, 1, 12)
    private val now = today.atTime(12, 0).atZone(zone).toInstant() // noon today

    private fun task(id: Long, date: LocalDate?, time: LocalTime? = null, priority: Int = 3) = Task(
        id = id,
        title = "t$id",
        dueAt = date?.atTime(time ?: LocalTime.MIDNIGHT)?.atZone(zone)?.toInstant()?.toEpochMilli(),
        hasTime = time != null,
        priority = priority,
        createdAt = 0,
    )

    private fun nudges(tasks: List<Task>, skip: Set<Long> = emptySet()) =
        priorityNudges(tasks, skip, now, today, zone)

    @Test fun `p3 due today proposes p1`() {
        val s = nudges(listOf(task(1, today))).single()
        assertEquals(1L, s.task.id)
        assertEquals(1, s.targetPriority)
        assertEquals(NudgeUrgency.DUE_TODAY, s.urgency)
    }

    @Test fun `p4 due tomorrow proposes p2`() {
        val s = nudges(listOf(task(1, today.plusDays(1), priority = 4))).single()
        assertEquals(2, s.targetPriority)
        assertEquals(NudgeUrgency.DUE_TOMORROW, s.urgency)
    }

    @Test fun `overdue all-day task proposes p1`() {
        val s = nudges(listOf(task(1, today.minusDays(1)))).single()
        assertEquals(1, s.targetPriority)
        assertEquals(NudgeUrgency.OVERDUE, s.urgency)
    }

    @Test fun `a timed task earlier today is overdue, later today is due-today`() {
        val past = nudges(listOf(task(1, today, time = LocalTime.of(9, 0)))).single()  // 9am, now noon
        assertEquals(NudgeUrgency.OVERDUE, past.urgency)
        val ahead = nudges(listOf(task(2, today, time = LocalTime.of(15, 0)))).single() // 3pm, now noon
        assertEquals(NudgeUrgency.DUE_TODAY, ahead.urgency)
    }

    @Test fun `undated and far-off tasks are not nudged`() {
        assertTrue(nudges(listOf(task(1, null))).isEmpty())              // no due date
        assertTrue(nudges(listOf(task(2, today.plusDays(3)))).isEmpty()) // 3 days out
    }

    @Test fun `already-high priorities are left alone`() {
        val tasks = listOf(
            task(1, today.minusDays(1), priority = 1), // p1 overdue
            task(2, today, priority = 2),              // p2 due today
        )
        assertTrue(nudges(tasks).isEmpty())
    }

    @Test fun `a waved-off id does not reappear`() {
        val tasks = listOf(task(1, today), task(2, today))
        val s = nudges(tasks, skip = setOf(1L))
        assertEquals(listOf(2L), s.map { it.task.id })
    }
}
