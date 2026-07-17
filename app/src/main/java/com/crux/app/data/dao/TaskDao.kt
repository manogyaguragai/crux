package com.crux.app.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.crux.app.domain.model.Task
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {
    @Insert
    suspend fun insert(task: Task): Long

    @Update
    suspend fun update(task: Task)

    @Delete
    suspend fun delete(task: Task)

    /** Everything a list may render: OPEN and DONE, never ARCHIVED. Ordering is applied in domain. */
    @Query("SELECT * FROM tasks WHERE status != 'ARCHIVED'")
    fun observeVisible(): Flow<List<Task>>

    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun getById(id: Long): Task?

    /** Midnight sweep: delete DONE tasks completed before the start of today (log row keeps the record). */
    @Query("DELETE FROM tasks WHERE status = 'DONE' AND completedAt < :startOfTodayMillis")
    suspend fun sweepDoneBefore(startOfTodayMillis: Long): Int
}
