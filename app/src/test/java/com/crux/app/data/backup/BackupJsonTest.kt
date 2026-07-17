package com.crux.app.data.backup

import com.crux.app.domain.model.CompletionLog
import com.crux.app.domain.model.ParsedBy
import com.crux.app.domain.model.Project
import com.crux.app.domain.model.RecurrenceType
import com.crux.app.domain.model.Source
import com.crux.app.domain.model.Task
import com.crux.app.domain.model.TaskStatus
import org.junit.Assert.assertEquals
import org.junit.Test

/** The export/import envelope must round-trip every field, including nulls and enums, unchanged. */
class BackupJsonTest {

    @Test
    fun `encode then decode returns the same data`() {
        val data = BackupData(
            projects = listOf(
                Project(id = 1, name = "work", rank = 1, archived = false, createdAt = 100),
                Project(id = 2, name = "old", rank = 5, archived = true, createdAt = 50),
            ),
            tasks = listOf(
                Task(
                    id = 10, projectId = 1, title = "ship it", notes = "with tests", priority = 1,
                    dueAt = 999, hasTime = true, recurrenceType = RecurrenceType.WEEKLY,
                    recurrenceWeekday = 3, recurrenceDay = null, status = TaskStatus.OPEN,
                    createdAt = 200, completedAt = null, source = Source.VOICE, parsedBy = ParsedBy.AI,
                    calendarEventId = 42,
                ),
                Task(id = 11, projectId = null, title = "loose", createdAt = 300),
            ),
            completions = listOf(
                CompletionLog(id = 7, taskId = 10, titleSnapshot = "ship it", projectNameSnapshot = "work", completedAt = 400),
                CompletionLog(id = 8, taskId = 11, titleSnapshot = "loose", projectNameSnapshot = null, completedAt = 500),
            ),
        )

        val decoded = BackupJson.decode(BackupJson.encode(data, exportedAt = 12345))

        assertEquals(data.projects, decoded.projects)
        assertEquals(data.tasks, decoded.tasks)
        assertEquals(data.completions, decoded.completions)
    }
}
