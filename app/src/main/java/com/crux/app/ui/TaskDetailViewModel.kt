package com.crux.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.crux.app.data.ProjectRepository
import com.crux.app.data.TaskRepository
import com.crux.app.domain.model.Project
import com.crux.app.domain.model.RecurrenceType
import com.crux.app.domain.model.Task
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * State for the task detail screen: one task, observed live, plus the active projects for the
 * project picker. Every edit writes straight through the repository; discrete fields (project,
 * priority, date, recurrence) commit on tap, free-text fields (title, notes) commit on blur.
 */
class TaskDetailViewModel(
    private val tasks: TaskRepository,
    projects: ProjectRepository,
    private val taskId: Long,
) : ViewModel() {

    val task: StateFlow<Task?> =
        tasks.observeTask(taskId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val projects: StateFlow<List<Project>> =
        projects.observeActive()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private fun mutate(block: (Task) -> Task) {
        val current = task.value ?: return
        viewModelScope.launch { tasks.updateTask(block(current)) }
    }

    /** Blank titles are ignored, so a task is never left nameless. */
    fun setTitle(title: String) {
        val trimmed = title.trim()
        if (trimmed.isEmpty()) return
        mutate { it.copy(title = trimmed) }
    }

    fun setProject(projectId: Long?) = mutate { it.copy(projectId = projectId) }

    fun setPriority(priority: Int) = mutate { it.copy(priority = priority) }

    /** Set or clear the due date. Clearing a date also drops the time and any recurrence anchor. */
    fun setDue(dueAt: Long?) = mutate {
        if (dueAt == null) it.copy(dueAt = null, hasTime = false) else it.copy(dueAt = dueAt)
    }

    fun setTime(dueAt: Long, hasTime: Boolean) = mutate { it.copy(dueAt = dueAt, hasTime = hasTime) }

    /**
     * Set or clear recurrence. WEEKLY carries the weekday, MONTHLY the day-of-month; the screen
     * derives those from the due date (or today) so there is no separate sub-picker in phase 1.
     */
    fun setRecurrence(type: RecurrenceType?, weekday: Int?, day: Int?) =
        mutate { it.copy(recurrenceType = type, recurrenceWeekday = weekday, recurrenceDay = day) }

    fun setNotes(notes: String) {
        val trimmed = notes.trim()
        mutate { it.copy(notes = trimmed.ifEmpty { null }) }
    }

    companion object {
        fun factory(tasks: TaskRepository, projects: ProjectRepository, taskId: Long) =
            viewModelFactory {
                initializer { TaskDetailViewModel(tasks, projects, taskId) }
            }
    }
}
