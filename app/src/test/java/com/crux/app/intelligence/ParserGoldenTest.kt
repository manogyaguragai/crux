package com.crux.app.intelligence

import com.crux.app.domain.model.RecurrenceType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

/**
 * The parser golden table (04-build/testing.md). Phase 0 created it @Ignore'd; phase 2 wired
 * `intelligence.parse(input, today, projects)` and turned it green with typed assertions.
 *
 * The table only ever grows. Add a row for every parser bug ever found.
 * Weakening an assertion or deleting a row is never sanctioned; @Ignore is the only
 * way to park a row, so `./gradlew test` stays green after every task.
 */
class ParserGoldenTest {

    // A Wednesday, deliberately not a Sunday/Friday/Monday so the weekday rows below read as future
    // dates (a weekday name matching *today* would resolve to today — a separate, tested rule).
    private val today = LocalDate.of(2026, 7, 15)
    private val projects = listOf(KnownProject(1, "growbydata"), KnownProject(2, "freelance"))

    private fun parse(input: String) = parse(input, today, projects)

    private data class Golden(val input: String, val check: (ParseResult) -> Unit)

    private val goldenTable = listOf(
        Golden("buy milk") { r ->
            assertEquals("buy milk", r.title)
            assertNull(r.dueDate); assertNull(r.priority); assertNull(r.project)
            assertNull(r.recurrenceType); assertFalse(r.hasTime)
        },
        Golden("sov deck tomorrow 2pm #growbydata !p1") { r ->
            assertEquals("sov deck", r.title)
            assertEquals(today.plusDays(1), r.dueDate)
            assertTrue(r.hasTime); assertEquals(LocalTime.of(14, 0), r.time)
            assertEquals(ProjectRef.Matched(1, "growbydata"), r.project)
            assertEquals(1, r.priority)
        },
        Golden("gym every weekday 6am") { r ->
            assertEquals("gym", r.title)
            assertEquals(RecurrenceType.WEEKDAYS, r.recurrenceType)
            assertEquals(LocalTime.of(6, 0), r.time); assertTrue(r.hasTime)
            assertEquals(today, r.dueDate) // today (Wed) is a weekday => first due today
        },
        Golden("call didi sunday") { r ->
            assertEquals("call didi", r.title)
            assertEquals(LocalDate.of(2026, 7, 19), r.dueDate) // next Sunday after Wed 7/15
            assertFalse(r.hasTime)
        },
        Golden("pay rent monthly on 1") { r ->
            assertEquals("pay rent", r.title)
            assertEquals(RecurrenceType.MONTHLY, r.recurrenceType)
            assertEquals(1, r.recurrenceDay)
            assertEquals(LocalDate.of(2026, 8, 1), r.dueDate) // 15th > 1st => next month
        },
        Golden("review prs in 3 days !p2") { r ->
            assertEquals("review prs", r.title)
            assertEquals(today.plusDays(3), r.dueDate)
            assertEquals(2, r.priority)
        },
        Golden("#freelance invoice friday") { r ->
            assertEquals("invoice", r.title)
            assertEquals(ProjectRef.Matched(2, "freelance"), r.project)
            assertEquals(LocalDate.of(2026, 7, 17), r.dueDate) // next Friday
        },
        Golden("water plants every day") { r ->
            assertEquals("water plants", r.title)
            assertEquals(RecurrenceType.DAILY, r.recurrenceType)
            assertEquals(today, r.dueDate)
        },
        Golden("standup every monday 9:30") { r ->
            assertEquals("standup", r.title)
            assertEquals(RecurrenceType.WEEKLY, r.recurrenceType)
            assertEquals(1, r.recurrenceWeekday)
            assertEquals(LocalTime.of(9, 30), r.time); assertTrue(r.hasTime)
            assertEquals(LocalDate.of(2026, 7, 20), r.dueDate) // next Monday
        },
        Golden("fix ssl !p1 today") { r ->
            assertEquals("fix ssl", r.title)
            assertEquals(1, r.priority)
            assertEquals(today, r.dueDate)
        },
    )

    @Test
    fun `golden table round-trips`() {
        goldenTable.forEach { case ->
            try {
                case.check(parse(case.input))
            } catch (e: AssertionError) {
                throw AssertionError("golden row failed: \"${case.input}\" -> ${e.message}", e)
            }
        }
    }

    private fun assertFalse(b: Boolean) = assertTrue(!b)
}
