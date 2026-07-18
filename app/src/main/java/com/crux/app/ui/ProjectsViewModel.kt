package com.crux.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.crux.app.data.ProjectRepository
import com.crux.app.data.TaskRepository
import com.crux.app.domain.model.Project
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

/**
 * State for the projects screen: the active projects in rank order, plus create / rename / archive /
 * re-rank. Name clashes never eat input; they surface as a one-shot [errors] signal the screen shows.
 */
class ProjectsViewModel(
    private val projects: ProjectRepository,
    private val tasks: TaskRepository,
) : ViewModel() {

    val active: StateFlow<List<Project>> =
        projects.observeActive()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * Per-project open/due counts, keyed by project id. "due" = an open task filed under the project
     * whose dueAt falls today-or-earlier (<= end of today, ZoneId.systemDefault()).
     */
    val counts: StateFlow<Map<Long, ProjectCounts>> =
        active.combine(tasks.observeOpen()) { projectList, open ->
            val ids = projectList.mapTo(HashSet()) { it.id }
            val endOfToday = LocalDate.now(ZoneId.systemDefault())
                .plusDays(1)
                .atStartOfDay(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()
            open.asSequence()
                .filter { it.projectId != null && it.projectId in ids }
                .groupBy { it.projectId!! }
                .mapValues { (_, list) ->
                    ProjectCounts(
                        open = list.size,
                        due = list.count { it.dueAt != null && it.dueAt <= endOfToday },
                    )
                }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /** Total open task count across all projects (the header subline's hot portion). */
    val totalOpen: StateFlow<Int> =
        tasks.observeOpen()
            .map { it.size }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    private val errorChannel = Channel<ProjectError>(Channel.CONFLATED)
    val errors = errorChannel.receiveAsFlow()

    fun create(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            if (projects.create(name, System.currentTimeMillis()) == null) {
                errorChannel.send(ProjectError.DUPLICATE_NAME)
            }
        }
    }

    fun rename(project: Project, newName: String) {
        if (newName.trim() == project.name) return // no-op rename (same name)
        viewModelScope.launch {
            if (!projects.rename(project, newName)) errorChannel.send(ProjectError.DUPLICATE_NAME)
        }
    }

    fun archive(project: Project) {
        viewModelScope.launch { projects.archive(project) }
    }

    /** Re-rank up (heavier). No-op when already at the top of the active order. */
    fun moveUp(project: Project) = swapWithNeighbour(project, -1)

    /** Re-rank down (lighter). No-op when already at the bottom. */
    fun moveDown(project: Project) = swapWithNeighbour(project, +1)

    private fun swapWithNeighbour(project: Project, offset: Int) {
        val list = active.value
        val index = list.indexOfFirst { it.id == project.id }
        val neighbourIndex = index + offset
        if (index < 0 || neighbourIndex !in list.indices) return
        val neighbour = list[neighbourIndex]
        viewModelScope.launch { projects.swapRanks(project, neighbour) }
    }

    companion object {
        fun factory(projects: ProjectRepository, tasks: TaskRepository) = viewModelFactory {
            initializer { ProjectsViewModel(projects, tasks) }
        }
    }
}

/** Per-project counts for a project row's trailing meta: open tasks and how many are due. */
data class ProjectCounts(val open: Int, val due: Int)

/** The only failure the projects screen shows for now: a name already in use. */
enum class ProjectError { DUPLICATE_NAME }
