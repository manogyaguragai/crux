package com.crux.app.data

import com.crux.app.data.dao.TaskDao
import com.crux.app.domain.model.ParsedBy
import com.crux.app.domain.model.Source
import com.crux.app.domain.model.Task
import com.crux.app.domain.model.TaskStatus
import com.crux.app.domain.withinGroupComparator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * The boundary between the UI and Room (architecture.md: the UI never writes to Room directly).
 * Concrete class, not an interface: there is one implementation and no reason to abstract it.
 *
 * Phase 1: no projects yet, so every task is inbox and the within-group comparator is the whole
 * order. Full grouping by project rank arrives with the projects and stack slices.
 */
class TaskRepository(private val taskDao: TaskDao) {

    /** Open tasks in master-sort order. */
    fun observeOpen(): Flow<List<Task>> =
        taskDao.observeVisible().map { tasks ->
            tasks.filter { it.status == TaskStatus.OPEN }
                .sortedWith(withinGroupComparator)
        }

    /**
     * Capture: raw text becomes a title-only task. No parsing in phase 1 (that is phase 2).
     * Errors never eat input, so the caller persists the raw text as the title.
     */
    suspend fun addTitleOnly(title: String, now: Long): Long =
        taskDao.insert(
            Task(
                title = title.trim(),
                createdAt = now,
                source = Source.TYPED,
                parsedBy = ParsedBy.MANUAL,
            )
        )
}
