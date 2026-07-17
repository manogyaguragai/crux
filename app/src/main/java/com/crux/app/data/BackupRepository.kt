package com.crux.app.data

import com.crux.app.data.backup.BackupData
import com.crux.app.data.backup.BackupJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Export / import the whole database as one versioned JSON envelope (data-model.md). Export reads
 * every row (archived included); import REPLACES everything, so it restores a backup faithfully
 * rather than merging. Ids are preserved so references survive the round-trip.
 */
class BackupRepository(private val database: CruxDatabase) {

    suspend fun exportJson(now: Long): String = withContext(Dispatchers.IO) {
        val data = BackupData(
            projects = database.projectDao().getAll(),
            tasks = database.taskDao().getAll(),
            completions = database.completionLogDao().getAll(),
        )
        BackupJson.encode(data, now)
    }

    suspend fun importJson(json: String) = withContext(Dispatchers.IO) {
        val data = BackupJson.decode(json)
        database.clearAllTables()
        database.projectDao().insertAll(data.projects)
        database.taskDao().insertAll(data.tasks)
        database.completionLogDao().insertAll(data.completions)
    }
}
