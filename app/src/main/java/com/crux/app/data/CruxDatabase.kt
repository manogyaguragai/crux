package com.crux.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.crux.app.data.dao.CompletionLogDao
import com.crux.app.data.dao.ProjectDao
import com.crux.app.data.dao.TaskDao
import com.crux.app.domain.model.CompletionLog
import com.crux.app.domain.model.Project
import com.crux.app.domain.model.Task

/**
 * The one database. Schema version 1, exported to app/schemas from day one so every
 * future bump ships a tested migration. No fallbackToDestructiveMigration, ever:
 * losing user data silently is not an option.
 */
@Database(
    entities = [Project::class, Task::class, CompletionLog::class],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class CruxDatabase : RoomDatabase() {
    abstract fun projectDao(): ProjectDao
    abstract fun taskDao(): TaskDao
    abstract fun completionLogDao(): CompletionLogDao

    companion object {
        const val NAME = "crux.db"

        fun build(context: Context): CruxDatabase =
            Room.databaseBuilder(context, CruxDatabase::class.java, NAME)
                .build()
    }
}
