package com.crux.app.data

import android.content.Context

/**
 * Manual dependency container (architecture.md: Hilt is overkill for one module).
 * Holds the database and, from phase 1, the repositories built on its DAOs.
 * Constructed once in CruxApplication; the database opens lazily on first use.
 */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val database: CruxDatabase by lazy { CruxDatabase.build(appContext) }

    val taskRepository: TaskRepository by lazy {
        TaskRepository(database.taskDao(), database.completionLogDao())
    }

    val projectRepository: ProjectRepository by lazy {
        ProjectRepository(database.projectDao(), database.taskDao())
    }
}
