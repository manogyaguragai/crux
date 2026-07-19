package com.crux.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.crux.app.data.dao.CompletionLogDao
import com.crux.app.data.dao.ProjectDao
import com.crux.app.data.dao.TaskDao
import com.crux.app.domain.model.CompletionLog
import com.crux.app.domain.model.Project
import com.crux.app.domain.model.Task

/**
 * The one database. Exported to app/schemas from day one so every bump ships a tested migration.
 * No fallbackToDestructiveMigration, ever: losing user data silently is not an option.
 *
 * v2 adds tasks.remindOffsetMinutes (nullable): a per-task reminder offset before a timed due.
 * v3 adds projects.description (TEXT, default ''): free-text context fed to the LLM for assignment.
 */
@Database(
    entities = [Project::class, Task::class, CompletionLog::class],
    version = 3,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class CruxDatabase : RoomDatabase() {
    abstract fun projectDao(): ProjectDao
    abstract fun taskDao(): TaskDao
    abstract fun completionLogDao(): CompletionLogDao

    companion object {
        const val NAME = "crux.db"

        /** 1 -> 2: add the nullable remindOffsetMinutes column to tasks (no default; null = no reminder). */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE tasks ADD COLUMN remindOffsetMinutes INTEGER")
            }
        }

        /** 2 -> 3: add projects.description (non-null, default '') — free-text project context for the LLM. */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE projects ADD COLUMN description TEXT NOT NULL DEFAULT ''")
            }
        }

        fun build(context: Context): CruxDatabase =
            Room.databaseBuilder(context, CruxDatabase::class.java, NAME)
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .build()
    }
}
