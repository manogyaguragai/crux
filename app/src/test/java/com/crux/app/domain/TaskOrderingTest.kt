package com.crux.app.domain

import com.crux.app.domain.model.Task
import com.crux.app.domain.model.TaskStatus
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The within-group sort (data-model.md): OPEN before DONE, then priority asc,
 * then dueAt asc nulls last, then createdAt asc. Grouping by project rank is separate.
 */
class TaskOrderingTest {

    private fun task(
        id: Long,
        status: TaskStatus = TaskStatus.OPEN,
        priority: Int = 3,
        dueAt: Long? = null,
        createdAt: Long,
    ) = Task(id = id, title = "t$id", priority = priority, dueAt = dueAt, status = status, createdAt = createdAt)

    @Test
    fun `open before done, then priority, then due nulls-last, then created`() {
        val e = task(id = 5, priority = 1, dueAt = 100, createdAt = 0)
        val a = task(id = 1, priority = 1, dueAt = 100, createdAt = 1)
        val b = task(id = 2, priority = 1, dueAt = null, createdAt = 2)
        val c = task(id = 3, priority = 2, dueAt = 50, createdAt = 3)
        val d = task(id = 4, status = TaskStatus.DONE, priority = 1, dueAt = 10, createdAt = 4)

        val sorted = listOf(a, b, c, d, e).sortedWith(withinGroupComparator).map { it.id }

        // p1 due-100 by created (e before a), then p1 undated (b), then p2 (c), then DONE (d)
        assertEquals(listOf(5L, 1L, 2L, 3L, 4L), sorted)
    }

    @Test
    fun `done always sinks below open regardless of priority`() {
        val openLow = task(id = 1, priority = 4, createdAt = 10)
        val doneHot = task(id = 2, status = TaskStatus.DONE, priority = 1, createdAt = 1)

        val sorted = listOf(doneHot, openLow).sortedWith(withinGroupComparator).map { it.id }

        assertEquals(listOf(1L, 2L), sorted)
    }
}
