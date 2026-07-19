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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * State for the project detail screen: one project, observed live off the active list, plus a rename
 * and a free-text description edit. The description is what the LLM reads to file new tasks, so it
 * commits straight through the repository (on blur). A rename that collides surfaces the same
 * one-shot duplicate signal the projects list uses; input is never lost.
 */
class ProjectDetailViewModel(
    private val projects: ProjectRepository,
    private val projectId: Long,
) : ViewModel() {

    val project: StateFlow<Project?> =
        projects.observeActive()
            .map { list -> list.firstOrNull { it.id == projectId } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val errorChannel = Channel<ProjectError>(Channel.CONFLATED)
    val errors = errorChannel.receiveAsFlow()

    /** Rename; a blank or clashing name is rejected with a duplicate signal (no-op on same name). */
    fun rename(newName: String) {
        val current = project.value ?: return
        if (newName.trim() == current.name) return
        viewModelScope.launch {
            if (!projects.rename(current, newName)) errorChannel.send(ProjectError.DUPLICATE_NAME)
        }
    }

    /** Save the description (context for AI assignment). No-op when unchanged; empty clears it. */
    fun setDescription(text: String) {
        val current = project.value ?: return
        if (text.trim() == current.description) return
        viewModelScope.launch { projects.setDescription(current, text) }
    }

    companion object {
        fun factory(projects: ProjectRepository, projectId: Long) = viewModelFactory {
            initializer { ProjectDetailViewModel(projects, projectId) }
        }
    }
}
