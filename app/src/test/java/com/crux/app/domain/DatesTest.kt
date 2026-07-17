package com.crux.app.domain

import com.crux.app.domain.model.Task
import com.crux.app.domain.model.TaskStatus
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

/** Overdue rules (data-model.md): timed tasks by the instant; all-day tasks only once the day is past. */
class DatesTest {

    private val zone = ZoneId.of("America/New_York")
    // a fixed "now": 2026-07-17 09:00 in New York
    private val now = Instant.parse("2026-07-17T13:00:00Z")

    private fun midnight(date: String) =
        java.time.LocalDate.parse(date).atStartOfDay(zone).toInstant().toEpochMilli()

    private fun task(dueAt: Long?, hasTime: Boolean, status: TaskStatus = TaskStatus.OPEN) =
        Task(id = 1, title = "t", dueAt = dueAt, hasTime = hasTime, status = status, createdAt = 0)

    @Test
    fun `all-day task due today is not overdue`() {
        assertFalse(isOverdue(task(midnight("2026-07-17"), hasTime = false), now, zone))
    }

    @Test
    fun `all-day task due yesterday is overdue`() {
        assertTrue(isOverdue(task(midnight("2026-07-16"), hasTime = false), now, zone))
    }

    @Test
    fun `timed task earlier today is overdue`() {
        val eightAm = Instant.parse("2026-07-17T12:00:00Z").toEpochMilli() // 08:00 NY, before now
        assertTrue(isOverdue(task(eightAm, hasTime = true), now, zone))
    }

    @Test
    fun `timed task later today is not overdue`() {
        val fivePm = Instant.parse("2026-07-17T21:00:00Z").toEpochMilli() // 17:00 NY, after now
        assertFalse(isOverdue(task(fivePm, hasTime = true), now, zone))
    }

    @Test
    fun `done and undated tasks are never overdue`() {
        assertFalse(isOverdue(task(midnight("2026-07-01"), hasTime = false, status = TaskStatus.DONE), now, zone))
        assertFalse(isOverdue(task(null, hasTime = false), now, zone))
    }
}
