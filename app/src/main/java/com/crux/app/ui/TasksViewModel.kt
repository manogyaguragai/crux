package com.crux.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.crux.app.data.ProjectRepository
import com.crux.app.data.TaskRepository
import com.crux.app.domain.StackGroup
import com.crux.app.domain.groupStack
import com.crux.app.domain.isOverdue
import com.crux.app.domain.model.Task
import com.crux.app.domain.model.TaskStatus
import java.time.Instant
import java.time.ZoneId
import com.crux.app.ui.theme.Motion
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Shared state for the task-list screens (home and stack are two views of the same tasks).
 * Holds home's top 3, the full stack, capture, and complete/undo with a one-shot undo signal
 * that the app-level snackbar listens for.
 */
class TasksViewModel(
    private val tasks: TaskRepository,
    private val projects: ProjectRepository,
) : ViewModel() {

    val top3: StateFlow<List<Task>> =
        tasks.observeOpen()
            .map { it.take(3) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** How many open tasks are past due right now (home's nudge count). */
    val overdueCount: StateFlow<Int> =
        tasks.observeOpen()
            .map { list ->
                val now = Instant.now()
                val zone = ZoneId.systemDefault()
                list.count { isOverdue(it, now, zone) }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    /** The stack, grouped by project rank with the inbox last (empty groups omitted). */
    val groupedStack: StateFlow<List<StackGroup>> =
        combine(tasks.observeStack(), projects.observeActive()) { taskList, projectList ->
            groupStack(taskList, projectList, Copy.STACK_INBOX)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val undoChannel = Channel<Unit>(Channel.CONFLATED)
    val undoEvents = undoChannel.receiveAsFlow()

    /**
     * Ids mid-ceremony: tapped, striking through, not yet written DONE. The rows read this to draw
     * the strike-through in place before the task commits and sinks. Pruned the moment the db
     * confirms DONE, so `done` takes over the same frame the id leaves and nothing flickers.
     */
    private val completing = MutableStateFlow<Set<Long>>(emptySet())
    val completingIds: StateFlow<Set<Long>> = completing.asStateFlow()

    private var lastCompletion: Pair<Task, Long>? = null

    init {
        viewModelScope.launch {
            tasks.observeStack().collect { list ->
                if (completing.value.isEmpty()) return@collect
                val landed = list.asSequence()
                    .filter { it.status == TaskStatus.DONE }
                    .map { it.id }
                    .toSet()
                if (completing.value.any { it in landed }) completing.update { it - landed }
            }
        }
    }

    fun capture(text: String) {
        if (text.isBlank()) return
        viewModelScope.launch { tasks.addTitleOnly(text, System.currentTimeMillis()) }
    }

    fun complete(task: Task) {
        if (task.status == TaskStatus.DONE) return // tapping a done hold is a no-op; undo is the reversal
        if (task.id in completing.value) return    // ceremony already running; ignore the re-tap
        completing.update { it + task.id }
        viewModelScope.launch {
            // savour the strike-through in place, then commit and let it sink. runs on
            // viewModelScope, so leaving the tab mid-ceremony still lands the completion.
            delay(Motion.StrikeMs.toLong())
            val logId = tasks.complete(task, System.currentTimeMillis())
            lastCompletion = task to logId
            undoChannel.send(Unit)
        }
    }

    fun undoLast() {
        val (task, logId) = lastCompletion ?: return
        viewModelScope.launch {
            tasks.undoComplete(task, logId)
            lastCompletion = null
        }
    }

    companion object {
        fun factory(tasks: TaskRepository, projects: ProjectRepository) = viewModelFactory {
            initializer { TasksViewModel(tasks, projects) }
        }
    }
}
