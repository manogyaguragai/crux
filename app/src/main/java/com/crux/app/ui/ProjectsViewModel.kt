package com.crux.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.crux.app.data.ProjectRepository
import com.crux.app.domain.model.Project
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * State for the projects screen: the active projects in rank order, plus create / rename / archive /
 * re-rank. Name clashes never eat input; they surface as a one-shot [errors] signal the screen shows.
 */
class ProjectsViewModel(private val projects: ProjectRepository) : ViewModel() {

    val active: StateFlow<List<Project>> =
        projects.observeActive()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

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
        fun factory(projects: ProjectRepository) = viewModelFactory {
            initializer { ProjectsViewModel(projects) }
        }
    }
}

/** The only failure the projects screen shows for now: a name already in use. */
enum class ProjectError { DUPLICATE_NAME }
