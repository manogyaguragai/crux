package com.crux.app.intelligence

import com.crux.app.domain.model.RecurrenceType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

/**
 * The action-schema parser (intelligence.md). The model's reply is untrusted input: these lock in that
 * every shape round-trips, that junk and missing fields degrade to null (so the chain falls back to
 * rules) rather than throwing, and that stray prose or code fences around the JSON are tolerated.
 */
class LlmActionTest {

    @Test fun `add parses structured fields`() {
        val a = parseLlmAction(
            """{"action":"add","title":"send sov deck","project":"growbydata","priority":1,
               "due":"2026-07-16T14:00","has_time":true,
               "recurrence":{"type":"WEEKDAYS","weekday":null,"day":null}}""",
        ) as LlmAction.Add
        assertEquals("send sov deck", a.title)
        assertEquals("growbydata", a.project)
        assertEquals(1, a.priority)
        assertEquals(LocalDate.of(2026, 7, 16), a.due)
        assertEquals(LocalTime.of(14, 0), a.time)
        assertTrue(a.hasTime)
        assertEquals(RecurrenceType.WEEKDAYS, a.recurrenceType)
    }

    @Test fun `add with date only is all-day`() {
        val a = parseLlmAction("""{"action":"add","title":"pay rent","due":"2026-08-01","has_time":false}""") as LlmAction.Add
        assertEquals(LocalDate.of(2026, 8, 1), a.due)
        assertNull(a.time)
        assertTrue(!a.hasTime)
    }

    @Test fun `add drops out-of-range priority`() {
        val a = parseLlmAction("""{"action":"add","title":"x","priority":9}""") as LlmAction.Add
        assertNull(a.priority)
    }

    @Test fun `null and missing fields become null`() {
        val a = parseLlmAction("""{"action":"add","title":"x","project":null}""") as LlmAction.Add
        assertNull(a.project)
        assertNull(a.due)
        assertNull(a.recurrenceType)
    }

    @Test fun `complete carries a query`() {
        assertEquals("sov deck", (parseLlmAction("""{"action":"complete","query":"sov deck"}""") as LlmAction.Complete).query)
    }

    @Test fun `reschedule parses the new due`() {
        val r = parseLlmAction("""{"action":"reschedule","query":"vendor call","due":"2026-07-24","has_time":false}""") as LlmAction.Reschedule
        assertEquals("vendor call", r.query)
        assertEquals(LocalDate.of(2026, 7, 24), r.due)
        assertTrue(!r.hasTime)
    }

    @Test fun `query carries a question`() {
        assertEquals("what is overdue", (parseLlmAction("""{"action":"query","question":"what is overdue"}""") as LlmAction.Query).question)
    }

    @Test fun `code fences and prose around the json are tolerated`() {
        val a = parseLlmAction("here you go:\n```json\n{\"action\":\"add\",\"title\":\"x\"}\n```") as LlmAction.Add
        assertEquals("x", a.title)
    }

    @Test fun `destructive action without a query is rejected`() {
        assertNull(parseLlmAction("""{"action":"delete"}"""))
    }

    @Test fun `unknown action and junk return null`() {
        assertNull(parseLlmAction("""{"action":"launch_missiles","query":"x"}"""))
        assertNull(parseLlmAction("not json at all"))
        assertNull(parseLlmAction(""))
    }
}
