package com.crux.app.data

import com.crux.app.data.dao.CompletionLogDao
import com.crux.app.data.dao.TaskDao
import com.crux.app.domain.model.CompletionLog
import com.crux.app.domain.model.ParsedBy
import com.crux.app.domain.model.Source
import com.crux.app.domain.model.Task
import com.crux.app.domain.model.TaskStatus
import com.crux.app.domain.withinGroupComparator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.ZoneId

/**
 * The boundary between the UI and Room (architecture.md: the UI never writes to Room directly).
 * Concrete class, not an interface: there is one implementation and no reason to abstract it.
 *
 * Phase 1: no projects yet, so every task is inbox and the within-group comparator is the whole
 * order. Full grouping by project rank arrives with the projects and stack-grouping slices.
 */
class TaskRepository(
    private val taskDao: TaskDao,
    private val completionLogDao: CompletionLogDao,
) {

    /** Open tasks in master-sort order (home's top 3 reads the head of this). */
    fun observeOpen(): Flow<List<Task>> =
        taskDao.observeVisible().map { tasks ->
            tasks.filter { it.status == TaskStatus.OPEN }
                .sortedWith(withinGroupComparator)
        }

    /** Everything the stack renders: OPEN and DONE, ARCHIVED never. DONE sinks to the bottom. */
    fun observeStack(): Flow<List<Task>> =
        taskDao.observeVisible().map { it.sortedWith(withinGroupComparator) }

    /**
     * Capture: raw text becomes a title-only task. No parsing in phase 1 (that is phase 2).
     * Errors never eat input, so the caller persists the raw text as the title.
     */
    suspend fun addTitleOnly(title: String, now: Long): Long =
        taskDao.insert(
            Task(
                title = title.trim(),
                createdAt = now,
                source = Source.TYPED,
                parsedBy = ParsedBy.MANUAL,
            )
        )

    /**
     * Complete a task: mark DONE and write the permanent log row. Returns the log id so a 5 s
     * undo can reverse exactly this completion. Recurrence spawn-on-complete is phase 2; phase 1
     * tasks carry no recurrence, so there is nothing to spawn here yet.
     */
    suspend fun complete(task: Task, now: Long): Long {
        taskDao.update(task.copy(status = TaskStatus.DONE, completedAt = now))
        return completionLogDao.insert(
            CompletionLog(
                taskId = task.id,
                titleSnapshot = task.title,
                projectNameSnapshot = null, // set from the project name once projects exist
                completedAt = now,
            )
        )
    }

    /** Undo within the 5 s window: reopen the task and remove its log row. */
    suspend fun undoComplete(task: Task, logId: Long) {
        completionLogDao.deleteById(logId)
        taskDao.update(task.copy(status = TaskStatus.OPEN, completedAt = null))
    }

    /**
     * The sweep: delete DONE tasks completed before the start of today (the log keeps the record).
     * Runs on every app foreground; the scheduled WorkManager job arrives with the notifications
     * slice. Idempotent and cheap.
     */
    suspend fun sweepDoneBeforeToday(zone: ZoneId, now: Instant) {
        val startOfToday = now.atZone(zone).toLocalDate().atStartOfDay(zone).toInstant().toEpochMilli()
        taskDao.sweepDoneBefore(startOfToday)
    }
}
