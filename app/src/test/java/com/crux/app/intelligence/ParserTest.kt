package com.crux.app.intelligence

import com.crux.app.domain.model.RecurrenceType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

/**
 * Edge cases beyond the golden table: prefix matching, the ambiguity notices, the connectors, and
 * the deliberately-unparsed shapes. The golden table is the round-trip spec; this is the rulebook.
 */
class ParserTest {

    private val today = LocalDate.of(2026, 7, 15) // Wednesday
    private val projects = listOf(KnownProject(1, "growbydata"), KnownProject(2, "freelance"))
    private fun parse(input: String) = parse(input, today, projects)

    @Test fun `project prefix matches`() {
        assertEquals(ProjectRef.Matched(1, "growbydata"), parse("ship #grow").project)
    }

    @Test fun `unknown project becomes a create chip`() {
        assertEquals(ProjectRef.Unknown("garden"), parse("weed #garden").project)
    }

    @Test fun `bang-number priority is accepted`() {
        assertEquals(3, parse("thing !3").priority)
    }

    @Test fun `bare priority defaults to null so the task default of 3 applies`() {
        assertNull(parse("thing").priority)
    }

    @Test fun `a weekday name matching today resolves to today`() {
        // 2026-07-15 is a Wednesday
        assertEquals(today, parse("standup wednesday").dueDate)
        assertEquals(today, parse("standup wed").dueDate)
    }

    @Test fun `next weekday skips today`() {
        assertEquals(today.plusDays(7), parse("standup next wednesday").dueDate)
    }

    @Test fun `next week is monday of the following week`() {
        assertEquals(LocalDate.of(2026, 7, 20), parse("plan next week").dueDate)
    }

    @Test fun `tonight is today at eight pm`() {
        val r = parse("dinner tonight")
        assertEquals(today, r.dueDate)
        assertEquals(LocalTime.of(20, 0), r.time)
        assertTrue(r.hasTime)
    }

    @Test fun `at N is an oclock connector`() {
        val r = parse("call at 9")
        assertEquals(LocalTime.of(9, 0), r.time)
        assertEquals("call", r.title)
    }

    @Test fun `at with a meridiem time drops the connector`() {
        val r = parse("call at 2pm")
        assertEquals(LocalTime.of(14, 0), r.time)
        assertEquals("call", r.title)
    }

    @Test fun `month-day and day-month both parse`() {
        assertEquals(LocalDate.of(2026, 8, 5), parse("thing aug 5").dueDate)
        assertEquals(LocalDate.of(2026, 8, 5), parse("thing 5 aug").dueDate)
    }

    @Test fun `a past month-day rolls to next year`() {
        // today is July 15; "jan 5" already passed this year
        assertEquals(LocalDate.of(2027, 1, 5), parse("thing jan 5").dueDate)
    }

    @Test fun `two dates keep the first and raise a notice`() {
        val r = parse("thing tomorrow friday")
        assertEquals(today.plusDays(1), r.dueDate)
        assertTrue(r.notices.contains(ParseNotice.TWO_DATES))
    }

    @Test fun `suppressing a field returns its words to the title`() {
        val r = parse("call didi sunday", today, projects, setOf(ParseField.DATE))
        assertNull(r.dueDate)
        assertEquals("call didi sunday", r.title)
    }

    @Test fun `a dismissed date is not resurrected by a bare time`() {
        // "2pm" would normally imply today; with DATE suppressed it must stay dateless.
        val r = parse("ship it 2pm", today, projects, setOf(ParseField.DATE))
        assertNull(r.dueDate)
    }

    @Test fun `numeric slash dates are not parsed`() {
        val r = parse("thing 5/1")
        assertNull(r.dueDate)
        assertTrue(r.title.contains("5/1"))
    }

    @Test fun `daily without a date is due today`() {
        assertEquals(RecurrenceType.DAILY, parse("water plants daily").recurrenceType)
        assertEquals(today, parse("water plants daily").dueDate)
    }

    @Test fun `weekdays keyword alone works`() {
        assertEquals(RecurrenceType.WEEKDAYS, parse("standup weekdays").recurrenceType)
    }

    @Test fun `24-hour time parses`() {
        assertEquals(LocalTime.of(14, 30), parse("meet 14:30").time)
    }

    @Test fun `empty input yields an empty title`() {
        assertEquals("", parse("   ").title)
    }

    @Test fun `tokens can appear in any order`() {
        val r = parse("!p1 #growbydata friday 3pm sov deck")
        assertEquals("sov deck", r.title)
        assertEquals(1, r.priority)
        assertEquals(ProjectRef.Matched(1, "growbydata"), r.project)
        assertEquals(LocalDate.of(2026, 7, 17), r.dueDate)
        assertEquals(LocalTime.of(15, 0), r.time)
    }
}
