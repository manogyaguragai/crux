package com.crux.app.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import com.crux.app.domain.model.CompletionLog
import kotlinx.coroutines.flow.Flow

@Dao
interface CompletionLogDao {
    @Insert
    suspend fun insert(log: CompletionLog): Long

    @Query("SELECT * FROM completion_log ORDER BY completedAt DESC")
    fun observeAll(): Flow<List<CompletionLog>>

    /** Undo a completion within the 5 s window reverses the log row too. */
    @Delete
    suspend fun delete(log: CompletionLog)

    @Query("DELETE FROM completion_log WHERE id = :id")
    suspend fun deleteById(id: Long)
}
