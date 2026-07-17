package com.crux.app.data

import com.crux.app.data.dao.CompletionLogDao
import com.crux.app.data.dao.ProjectDao
import com.crux.app.data.dao.TaskDao
import com.crux.app.domain.model.CompletionLog
import com.crux.app.domain.model.Source
import com.crux.app.domain.model.Task
import com.crux.app.domain.model.TaskStatus
import com.crux.app.domain.nextDue
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
/** What one completion produced: its log row, and the id of any spawned recurrence (null if none). */
data class CompletionResult(val logId: Long, val spawnedTaskId: Long?)

class TaskRepository(
    private val taskDao: TaskDao,
    private val completionLogDao: CompletionLogDao,
    private val projectDao: ProjectDao,
) {

    /** The permanent completion history, newest first (the total-history screen reads this). */
    fun observeHistory(): Flow<List<CompletionLog>> = completionLogDao.observeAll()

    /** Open tasks in master-sort order (home's top 3 reads the head of this). */
    fun observeOpen(): Flow<List<Task>> =
        taskDao.observeVisible().map { tasks ->
            tasks.filter { it.status == TaskStatus.OPEN }
                .sortedWith(withinGroupComparator)
        }

    /** Everything the stack renders: OPEN and DONE, ARCHIVED never. DONE sinks to the bottom. */
    fun observeStack(): Flow<List<Task>> =
        taskDao.observeVisible().map { it.sortedWith(withinGroupComparator) }

    /** A single task, observed live for the detail screen. Emits null once the task is gone. */
    fun observeTask(id: Long): Flow<Task?> = taskDao.observeById(id)

    /** Persist an edited task (detail screen). The caller supplies the fully-formed row. */
    suspend fun updateTask(task: Task) = taskDao.update(task)

    /**
     * Capture: persist a fully-composed task. The caller (the omnibar path) runs the deterministic
     * grammar and maps the result to a Task; a pure-title line simply arrives as a MANUAL task with
     * no fields set. Capture is never interrupted, so this only ever inserts.
     */
    suspend fun add(task: Task): Long = taskDao.insert(task)

    /**
     * Complete a task: mark DONE, write the permanent log row, and — if the task recurs — spawn its
     * next occurrence as a fresh OPEN task (spawn-on-complete, data-model.md). The next due comes
     * straight from domain.nextDue (time-of-day preserved, late completions roll forward without
     * stacking a backlog). Returns the log id plus the spawned id so a 5 s undo can reverse exactly
     * this completion, spawn included.
     */
    suspend fun complete(task: Task, now: Long, zone: ZoneId): CompletionResult {
        taskDao.update(task.copy(status = TaskStatus.DONE, completedAt = now))
        val projectName = task.projectId?.let { projectDao.getById(it)?.name }
        val logId = completionLogDao.insert(
            CompletionLog(
                taskId = task.id,
                titleSnapshot = task.title,
                projectNameSnapshot = projectName, // snapshot so history survives project renames/deletes
                completedAt = now,
            )
        )
        val spawnedId = task.recurrenceType?.let {
            val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
            val next = nextDue(task, today, zone) // LocalDateTime; midnight for all-day tasks
            taskDao.insert(
                task.copy(
                    id = 0, // a brand-new row
                    dueAt = next.atZone(zone).toInstant().toEpochMilli(),
                    status = TaskStatus.OPEN,
                    completedAt = null,
                    createdAt = now,
                    source = Source.SYSTEM, // spawned, not re-typed; the provenance line says so
                )
            )
        }
        return CompletionResult(logId, spawnedId)
    }

    /**
     * Undo within the 5 s window: reopen the task, remove its log row, and delete the occurrence that
     * completing a recurring task spawned (else undo would leave a duplicate).
     */
    suspend fun undoComplete(task: Task, result: CompletionResult) {
        result.spawnedTaskId?.let { taskDao.deleteById(it) }
        completionLogDao.deleteById(result.logId)
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
