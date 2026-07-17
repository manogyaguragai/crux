package com.crux.app.data

import com.crux.app.data.dao.ProjectDao
import com.crux.app.domain.model.Project
import kotlinx.coroutines.flow.Flow

/**
 * The boundary between the UI and Room for projects (architecture.md). Concrete class, one
 * implementation, no interface. Owns the two invariants the DAO cannot: names are unique
 * case-insensitive, and a new project appends at the bottom of the rank order.
 *
 * rank 1 = heaviest. Ranks stay unique among active projects but need not be contiguous;
 * archiving leaves a gap, which the order tolerates (the UI shows positional numbers, not raw rank).
 */
class ProjectRepository(private val projectDao: ProjectDao) {

    /** Active projects in rank order; the projects screen and (later) the stack grouping read this. */
    fun observeActive(): Flow<List<Project>> = projectDao.observeActive()

    /**
     * Create a project, appended below the current lowest rank. Returns the new id, or null if the
     * name is blank or already taken (case-insensitive). Callers surface null as a gentle message;
     * input is never lost.
     */
    suspend fun create(name: String, now: Long): Long? {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return null
        if (projectDao.findByNameIgnoreCase(trimmed) != null) return null
        val nextRank = (projectDao.maxActiveRank() ?: 0) + 1
        return projectDao.insert(Project(name = trimmed, rank = nextRank, createdAt = now))
    }

    /**
     * Rename a project. Returns true on success; false if the new name is blank or collides with a
     * different project (case-insensitive). Renaming to the same name (any casing) is allowed.
     */
    suspend fun rename(project: Project, newName: String): Boolean {
        val trimmed = newName.trim()
        if (trimmed.isEmpty()) return false
        val clash = projectDao.findByNameIgnoreCase(trimmed)
        if (clash != null && clash.id != project.id) return false
        projectDao.update(project.copy(name = trimmed))
        return true
    }

    /** Soft archive (data-model.md: ARCHIVED is a soft delete; a settings purge removes it for good). */
    suspend fun archive(project: Project) {
        projectDao.update(project.copy(archived = true))
    }

    /** Swap two projects' ranks; the up/down re-rank controls call this with adjacent neighbours. */
    suspend fun swapRanks(a: Project, b: Project) {
        projectDao.update(a.copy(rank = b.rank))
        projectDao.update(b.copy(rank = a.rank))
    }
}
