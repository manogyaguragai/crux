package com.crux.app.domain

import com.crux.app.domain.model.RecurrenceType
import com.crux.app.domain.model.Task
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * Spawn-on-complete next-due math (data-model.md). All four shapes, month-end
 * clamping, the late-completion roll-forward (never stacks a backlog), and
 * time-of-day preservation.
 */
class RecurrenceTest {

    private val zone: ZoneId = ZoneId.of("UTC")

    private fun millis(date: LocalDate, time: LocalTime = LocalTime.MIDNIGHT): Long =
        date.atTime(time).atZone(zone).toInstant().toEpochMilli()

    private fun task(
        type: RecurrenceType,
        due: LocalDate,
        time: LocalTime? = null,
        weekday: Int? = null,
        day: Int? = null,
    ) = Task(
        title = "recurring",
        dueAt = millis(due, time ?: LocalTime.MIDNIGHT),
        hasTime = time != null,
        recurrenceType = type,
        recurrenceWeekday = weekday,
        recurrenceDay = day,
        createdAt = 0,
    )

    @Test
    fun `daily is base plus one day`() {
        val t = task(RecurrenceType.DAILY, LocalDate.of(2026, 1, 10))
        val next = nextDue(t, today = LocalDate.of(2026, 1, 10), zone = zone)
        assertEquals(LocalDate.of(2026, 1, 11), next.toLocalDate())
    }

    @Test
    fun `weekdays skips the weekend`() {
        // 2026-01-09 is a Friday; next weekday is Monday the 12th
        val t = task(RecurrenceType.WEEKDAYS, LocalDate.of(2026, 1, 9))
        val next = nextDue(t, today = LocalDate.of(2026, 1, 9), zone = zone)
        assertEquals(LocalDate.of(2026, 1, 12), next.toLocalDate())
    }

    @Test
    fun `weekly lands on the next matching weekday`() {
        // base Monday 2026-01-12, recur on Monday -> 2026-01-19
        val t = task(RecurrenceType.WEEKLY, LocalDate.of(2026, 1, 12), weekday = 1)
        val next = nextDue(t, today = LocalDate.of(2026, 1, 12), zone = zone)
        assertEquals(LocalDate.of(2026, 1, 19), next.toLocalDate())
    }

    @Test
    fun `monthly clamps to the last day of a short month`() {
        // day 31 in January -> February has no 31st -> clamp to Feb 28 (2026 is not a leap year)
        val t = task(RecurrenceType.MONTHLY, LocalDate.of(2026, 1, 31), day = 31)
        val next = nextDue(t, today = LocalDate.of(2026, 1, 31), zone = zone)
        assertEquals(LocalDate.of(2026, 2, 28), next.toLocalDate())
    }

    @Test
    fun `late completion rolls forward without stacking a backlog`() {
        // daily task due the 10th, completed on the 15th -> next is the 16th, not the 11th
        val t = task(RecurrenceType.DAILY, LocalDate.of(2026, 1, 10))
        val next = nextDue(t, today = LocalDate.of(2026, 1, 15), zone = zone)
        assertEquals(LocalDate.of(2026, 1, 16), next.toLocalDate())
    }

    @Test
    fun `time of day is preserved`() {
        val t = task(RecurrenceType.WEEKLY, LocalDate.of(2026, 1, 12), time = LocalTime.of(9, 30), weekday = 1)
        val next = nextDue(t, today = LocalDate.of(2026, 1, 12), zone = zone)
        assertEquals(LocalTime.of(9, 30), next.toLocalTime())
    }
}
