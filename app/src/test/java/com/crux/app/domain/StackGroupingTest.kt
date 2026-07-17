package com.crux.app.domain

import com.crux.app.domain.model.Project
import com.crux.app.domain.model.Task
import com.crux.app.domain.model.TaskStatus
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Grouping the stack (data-model.md): active projects by rank ascending, then one inbox group last.
 * Empty groups are dropped; tasks under an archived (non-active) project fall to the inbox; within a
 * group the within-group sort applies (done sinks).
 */
class StackGroupingTest {

    private fun task(id: Long, projectId: Long? = null, status: TaskStatus = TaskStatus.OPEN, createdAt: Long = id) =
        Task(id = id, title = "t$id", projectId = projectId, status = status, createdAt = createdAt)

    private fun project(id: Long, rank: Int, name: String = "p$id") =
        Project(id = id, name = name, rank = rank, createdAt = 0)

    @Test
    fun `groups follow project rank, inbox last`() {
        val projects = listOf(project(2, rank = 2, name = "b"), project(1, rank = 1, name = "a"))
        val tasks = listOf(
            task(id = 10, projectId = 2),
            task(id = 11, projectId = 1),
            task(id = 12, projectId = null),
        )

        val groups = groupStack(tasks, projects, inboxTitle = "inbox")

        assertEquals(listOf("a", "b", "inbox"), groups.map { it.title })
        assertEquals(listOf(1L, 2L, null), groups.map { it.projectId })
    }

    @Test
    fun `empty groups are omitted`() {
        val projects = listOf(project(1, rank = 1, name = "a"), project(2, rank = 2, name = "b"))
        val tasks = listOf(task(id = 10, projectId = 2)) // nothing under project 1, nothing in inbox

        val groups = groupStack(tasks, projects, inboxTitle = "inbox")

        assertEquals(listOf("b"), groups.map { it.title })
    }

    @Test
    fun `tasks under an archived project fall to the inbox`() {
        val projects = listOf(project(1, rank = 1, name = "a")) // project 9 is not active (archived)
        val tasks = listOf(task(id = 10, projectId = 9), task(id = 11, projectId = null))

        val groups = groupStack(tasks, projects, inboxTitle = "inbox")

        assertEquals(1, groups.size)
        assertEquals("inbox", groups.single().title)
        assertEquals(listOf(10L, 11L), groups.single().tasks.map { it.id })
    }

    @Test
    fun `done sinks within its group`() {
        val projects = listOf(project(1, rank = 1, name = "a"))
        val tasks = listOf(
            task(id = 10, projectId = 1, status = TaskStatus.DONE, createdAt = 1),
            task(id = 11, projectId = 1, status = TaskStatus.OPEN, createdAt = 2),
        )

        val groups = groupStack(tasks, projects, inboxTitle = "inbox")

        assertEquals(listOf(11L, 10L), groups.single().tasks.map { it.id })
    }
}
