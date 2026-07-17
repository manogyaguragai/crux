package com.crux.app.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.crux.app.domain.model.Project
import kotlinx.coroutines.flow.Flow

@Dao
interface ProjectDao {
    @Insert
    suspend fun insert(project: Project): Long

    @Update
    suspend fun update(project: Project)

    /** Active projects in rank order; the list grouping relies on this. */
    @Query("SELECT * FROM projects WHERE archived = 0 ORDER BY rank ASC")
    fun observeActive(): Flow<List<Project>>

    @Query("SELECT * FROM projects ORDER BY rank ASC")
    fun observeAll(): Flow<List<Project>>

    @Query("SELECT * FROM projects WHERE id = :id")
    suspend fun getById(id: Long): Project?

    /** Case-insensitive name lookup; the repository uses it to keep names unique. */
    @Query("SELECT * FROM projects WHERE name = :name COLLATE NOCASE LIMIT 1")
    suspend fun findByNameIgnoreCase(name: String): Project?

    /** Highest rank currently in use among active projects; null when there are none. */
    @Query("SELECT MAX(rank) FROM projects WHERE archived = 0")
    suspend fun maxActiveRank(): Int?
}
