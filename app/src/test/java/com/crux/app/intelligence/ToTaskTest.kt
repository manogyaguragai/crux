package com.crux.app.intelligence

import com.crux.app.domain.model.ParsedBy
import com.crux.app.domain.model.RecurrenceType
import com.crux.app.domain.model.Source
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** ParseResult -> Task: the epoch-milli fold, the parsedBy attribution, and the all-day anchor. */
class ToTaskTest {

    private val zone: ZoneId = ZoneId.of("UTC")
    private val today = LocalDate.of(2026, 7, 15)
    private val now = 1_000L

    @Test fun `a timed dated task folds to the right instant`() {
        val r = parse("sov deck friday 2pm", today, emptyList())
        val task = r.toTask(projectId = 7L, zone = zone, now = now)
        assertEquals("sov deck", task.title)
        assertEquals(7L, task.projectId)
        assertTrue(task.hasTime)
        val expected = LocalDate.of(2026, 7, 17).atTime(LocalTime.of(14, 0))
            .atZone(zone).toInstant().toEpochMilli()
        assertEquals(expected, task.dueAt)
        assertEquals(ParsedBy.RULES, task.parsedBy)
        assertEquals(Source.TYPED, task.source)
    }

    @Test fun `an all-day task anchors to midnight`() {
        val r = parse("call didi sunday", today, emptyList())
        val task = r.toTask(projectId = null, zone = zone, now = now)
        assertFalse(task.hasTime)
        val expected = LocalDate.of(2026, 7, 19).atStartOfDay(zone).toInstant().toEpochMilli()
        assertEquals(expected, task.dueAt)
    }

    @Test fun `a pure title is MANUAL with no fields`() {
        val r = parse("buy milk", today, emptyList())
        val task = r.toTask(projectId = null, zone = zone, now = now)
        assertEquals("buy milk", task.title)
        assertNull(task.dueAt)
        assertEquals(3, task.priority) // the task default
        assertEquals(ParsedBy.MANUAL, task.parsedBy)
    }

    @Test fun `a recurring task carries its shape and first due`() {
        val r = parse("pay rent monthly on 1", today, emptyList())
        val task = r.toTask(projectId = null, zone = zone, now = now)
        assertEquals(RecurrenceType.MONTHLY, task.recurrenceType)
        assertEquals(1, task.recurrenceDay)
        val expected = LocalDate.of(2026, 8, 1).atStartOfDay(zone).toInstant().toEpochMilli()
        assertEquals(expected, task.dueAt)
        assertEquals(ParsedBy.RULES, task.parsedBy)
    }

    @Test fun `priority alone counts as grammar-touched`() {
        val r = parse("fix ssl !p1", today, emptyList())
        val task = r.toTask(projectId = null, zone = zone, now = now)
        assertEquals(1, task.priority)
        assertEquals(ParsedBy.RULES, task.parsedBy)
    }
}
