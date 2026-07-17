package com.crux.app.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.crux.app.domain.model.CompletionLog
import com.crux.app.domain.model.ParsedBy
import com.crux.app.domain.model.Project
import com.crux.app.domain.model.RecurrenceType
import com.crux.app.domain.model.Source
import com.crux.app.domain.model.Task
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** DAO round-trip for each entity, including enum conversion (testing.md). */
@RunWith(AndroidJUnit4::class)
class DaoRoundTripTest {

    private lateinit var db: CruxDatabase

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, CruxDatabase::class.java).build()
    }

    @After
    fun teardown() = db.close()

    @Test
    fun projectTaskAndLog_roundTrip() = runBlocking {
        val projectId = db.projectDao().insert(Project(name = "growbydata", rank = 1, createdAt = 1))
        val taskId = db.taskDao().insert(Task(projectId = projectId, title = "sov deck", createdAt = 2))

        val task = db.taskDao().getById(taskId)
        assertEquals("sov deck", task?.title)
        assertEquals(projectId, task?.projectId)

        db.completionLogDao().insert(
            CompletionLog(taskId = taskId, titleSnapshot = "sov deck", projectNameSnapshot = "growbydata", completedAt = 3),
        )
        assertEquals(1, db.completionLogDao().observeAll().first().size)
    }

    @Test
    fun enums_roundTrip() = runBlocking {
        val id = db.taskDao().insert(
            Task(
                title = "standup",
                recurrenceType = RecurrenceType.WEEKLY,
                recurrenceWeekday = 1,
                source = Source.SYSTEM,
                parsedBy = ParsedBy.RULES,
                createdAt = 4,
            ),
        )
        val task = db.taskDao().getById(id)
        assertEquals(RecurrenceType.WEEKLY, task?.recurrenceType)
        assertEquals(Source.SYSTEM, task?.source)
        assertEquals(ParsedBy.RULES, task?.parsedBy)
    }
}
