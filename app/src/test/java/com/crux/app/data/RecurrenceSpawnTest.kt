package com.crux.app.data

import com.crux.app.data.dao.CompletionLogDao
import com.crux.app.data.dao.ProjectDao
import com.crux.app.data.dao.TaskDao
import com.crux.app.domain.model.CompletionLog
import com.crux.app.domain.model.Project
import com.crux.app.domain.model.RecurrenceType
import com.crux.app.domain.model.Source
import com.crux.app.domain.model.Task
import com.crux.app.domain.model.TaskStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * Spawn-on-complete at the repository seam: completing a recurring task inserts the next occurrence
 * (next-due math is domain.nextDue, tested separately), and undo removes that occurrence so nothing
 * is left duplicated. Non-recurring completions spawn nothing.
 */
class RecurrenceSpawnTest {

    private val zone: ZoneId = ZoneId.of("UTC")
    private fun millis(d: LocalDate, t: LocalTime): Long =
        d.atTime(t).atZone(zone).toInstant().toEpochMilli()

    @Test fun `completing a daily task spawns tomorrow at the same time`() = runBlocking {
        val tasks = FakeTaskDao()
        val repo = TaskRepository(tasks, FakeLogDao(), FakeProjectDao())
        val due = millis(LocalDate.of(2026, 1, 10), LocalTime.of(9, 0))
        val id = tasks.insert(
            Task(title = "water", dueAt = due, hasTime = true,
                recurrenceType = RecurrenceType.DAILY, priority = 2, createdAt = 0),
        )
        val original = tasks.getById(id)!!
        val now = millis(LocalDate.of(2026, 1, 10), LocalTime.of(20, 0))

        val result = repo.complete(original, now, zone)

        // original is DONE
        assertEquals(TaskStatus.DONE, tasks.getById(id)!!.status)
        // a spawn exists, OPEN, due the 11th at 09:00, carrying the shape + priority
        assertTrue(result.spawnedTaskId != null)
        val spawn = tasks.getById(result.spawnedTaskId!!)!!
        assertEquals(TaskStatus.OPEN, spawn.status)
        assertEquals(millis(LocalDate.of(2026, 1, 11), LocalTime.of(9, 0)), spawn.dueAt)
        assertEquals("water", spawn.title)
        assertEquals(2, spawn.priority)
        assertEquals(RecurrenceType.DAILY, spawn.recurrenceType)
        assertTrue(spawn.hasTime)
        assertEquals(Source.SYSTEM, spawn.source)
        assertEquals(now, spawn.createdAt)
    }

    @Test fun `undo removes the spawned occurrence and reopens the original`() = runBlocking {
        val tasks = FakeTaskDao()
        val repo = TaskRepository(tasks, FakeLogDao(), FakeProjectDao())
        val id = tasks.insert(
            Task(title = "standup", dueAt = millis(LocalDate.of(2026, 1, 12), LocalTime.MIDNIGHT),
                recurrenceType = RecurrenceType.WEEKLY, recurrenceWeekday = 1, createdAt = 0),
        )
        val original = tasks.getById(id)!!
        val now = millis(LocalDate.of(2026, 1, 12), LocalTime.of(10, 0))

        val result = repo.complete(original, now, zone)
        assertTrue(result.spawnedTaskId != null)

        repo.undoComplete(original, result)

        assertNull(tasks.getById(result.spawnedTaskId!!)) // spawn gone
        assertEquals(TaskStatus.OPEN, tasks.getById(id)!!.status) // original reopened
    }

    @Test fun `a non-recurring completion spawns nothing`() = runBlocking {
        val tasks = FakeTaskDao()
        val repo = TaskRepository(tasks, FakeLogDao(), FakeProjectDao())
        val id = tasks.insert(Task(title = "one off", createdAt = 0))
        val result = repo.complete(tasks.getById(id)!!, now = 1_000, zone = zone)
        assertNull(result.spawnedTaskId)
        assertEquals(1, tasks.count()) // only the original, now DONE
    }
}

// --- in-memory DAO fakes (only the methods the repository touches are real) ---

private class FakeTaskDao : TaskDao {
    private val rows = LinkedHashMap<Long, Task>()
    private var seq = 0L
    fun count() = rows.size
    override suspend fun insert(task: Task): Long {
        val id = ++seq
        rows[id] = task.copy(id = id)
        return id
    }
    override suspend fun update(task: Task) { rows[task.id] = task }
    override suspend fun getById(id: Long): Task? = rows[id]
    override suspend fun deleteById(id: Long) { rows.remove(id) }
    override suspend fun delete(task: Task) { rows.remove(task.id) }
    override suspend fun getAll(): List<Task> = rows.values.toList()
    override fun observeVisible(): Flow<List<Task>> = throw NotImplementedError()
    override fun observeById(id: Long): Flow<Task?> = throw NotImplementedError()
    override suspend fun insertAll(tasks: List<Task>) = throw NotImplementedError()
    override suspend fun clearProject(projectId: Long) = throw NotImplementedError()
    override suspend fun sweepDoneBefore(startOfTodayMillis: Long): Int = throw NotImplementedError()
}

private class FakeLogDao : CompletionLogDao {
    private val rows = LinkedHashMap<Long, CompletionLog>()
    private var seq = 0L
    override suspend fun insert(log: CompletionLog): Long {
        val id = ++seq
        rows[id] = log.copy(id = id)
        return id
    }
    override suspend fun deleteById(id: Long) { rows.remove(id) }
    override suspend fun delete(log: CompletionLog) { rows.remove(log.id) }
    override suspend fun getAll(): List<CompletionLog> = rows.values.toList()
    override fun observeAll(): Flow<List<CompletionLog>> = throw NotImplementedError()
    override suspend fun insertAll(logs: List<CompletionLog>) = throw NotImplementedError()
}

private class FakeProjectDao : ProjectDao {
    override suspend fun getById(id: Long): Project? = null
    override suspend fun insert(project: Project): Long = throw NotImplementedError()
    override suspend fun update(project: Project) = throw NotImplementedError()
    override fun observeActive(): Flow<List<Project>> = throw NotImplementedError()
    override fun observeAll(): Flow<List<Project>> = throw NotImplementedError()
    override suspend fun findByNameIgnoreCase(name: String): Project? = throw NotImplementedError()
    override suspend fun maxActiveRank(): Int? = throw NotImplementedError()
    override suspend fun getAll(): List<Project> = throw NotImplementedError()
    override suspend fun insertAll(projects: List<Project>) = throw NotImplementedError()
    override fun observeArchivedCount(): Flow<Int> = throw NotImplementedError()
    override suspend fun deleteArchived(): Int = throw NotImplementedError()
}
