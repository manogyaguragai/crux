package com.crux.app.intelligence

import org.junit.Ignore
import org.junit.Test

/**
 * The parser golden table (04-build/testing.md). Phase 0 creates it; phase 2 turns
 * it green by wiring `intelligence.parse(input)` and replacing each expectation
 * description with a typed assertion.
 *
 * The table only ever grows. Add a row for every parser bug ever found.
 * Weakening an assertion or deleting a row is never sanctioned; @Ignore is the only
 * way to park a row, so `./gradlew test` stays green after every task.
 */
class ParserGoldenTest {

    private data class Golden(val input: String, val expected: String)

    private val goldenTable = listOf(
        Golden("buy milk", "title only"),
        Golden(
            "sov deck tomorrow 2pm #growbydata !p1",
            "title 'sov deck', due tomorrow 14:00 hasTime, project growbydata, p1",
        ),
        Golden("gym every weekday 6am", "WEEKDAYS, 06:00, title 'gym'"),
        Golden("call didi sunday", "due next Sunday, all-day"),
        Golden("pay rent monthly on 1", "MONTHLY day 1"),
        Golden("review prs in 3 days !p2", "due today+3, p2"),
        Golden("#freelance invoice friday", "project freelance, due next Friday"),
        Golden("water plants every day", "DAILY"),
        Golden("standup every monday 9:30", "WEEKLY Monday, 09:30"),
        Golden("fix ssl !p1 today", "p1, due today"),
    )

    @Ignore("phase 2: parser not implemented until the deterministic grammar lands")
    @Test
    fun `golden table round-trips`() {
        // phase 2 wires this against intelligence.parse(input) and asserts each expectation.
        goldenTable.forEach { case ->
            // val result = parse(case.input)
            // assertEquals(case.expected, result.describe())
            check(case.input.isNotBlank())
        }
    }
}
