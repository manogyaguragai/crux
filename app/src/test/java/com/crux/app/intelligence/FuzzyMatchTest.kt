package com.crux.app.intelligence

import com.crux.app.domain.model.Task
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The local fuzzy match behind the destructive verbs (safety contract). A hallucinated query must
 * never silently hit the wrong task: a clear winner acts, a muddle offers a pick list, and a miss
 * finds nothing. These pin the three outcomes and the ordering.
 */
class FuzzyMatchTest {

    private fun task(id: Long, title: String) = Task(id = id, title = title, createdAt = 0L)

    @Test fun `identical strings score 1`() {
        assertEquals(1.0, titleScore("vendor call", "vendor call"), 0.0001)
    }

    @Test fun `word order does not matter much`() {
        assertTrue(titleScore("vendor call", "call the vendor") >= 0.5)
    }

    @Test fun `unrelated strings score low`() {
        assertTrue(titleScore("buy milk", "renew the ssl certificate") < 0.3)
    }

    @Test fun `clear winner acts`() {
        val open = listOf(
            task(1, "send the sov deck to the client"),
            task(2, "book a dentist appointment"),
        )
        val result = matchTask("sov deck", open)
        assertTrue(result is FuzzyResult.One)
        assertEquals(1L, (result as FuzzyResult.One).task.id)
    }

    @Test fun `two close matches offer a pick list`() {
        val open = listOf(
            task(1, "call the vendor about dashboards"),
            task(2, "call the vendor about invoices"),
        )
        val result = matchTask("call the vendor", open)
        assertTrue(result is FuzzyResult.Many)
        assertEquals(2, (result as FuzzyResult.Many).candidates.size)
    }

    @Test fun `no plausible match finds nothing`() {
        val open = listOf(task(1, "water the plants"))
        assertTrue(matchTask("quarterly tax filing", open) is FuzzyResult.None)
    }

    @Test fun `empty task list is None`() {
        assertTrue(matchTask("anything", emptyList()) is FuzzyResult.None)
    }
}
