package com.crux.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.crux.app.data.TaskRepository
import com.crux.app.domain.model.Task
import com.crux.app.domain.model.TaskStatus
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Shared state for the task-list screens (home and stack are two views of the same tasks).
 * Holds home's top 3, the full stack, capture, and complete/undo with a one-shot undo signal
 * that the app-level snackbar listens for.
 */
class TasksViewModel(private val tasks: TaskRepository) : ViewModel() {

    val top3: StateFlow<List<Task>> =
        tasks.observeOpen()
            .map { it.take(3) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val stack: StateFlow<List<Task>> =
        tasks.observeStack()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val undoChannel = Channel<Unit>(Channel.CONFLATED)
    val undoEvents = undoChannel.receiveAsFlow()

    private var lastCompletion: Pair<Task, Long>? = null

    fun capture(text: String) {
        if (text.isBlank()) return
        viewModelScope.launch { tasks.addTitleOnly(text, System.currentTimeMillis()) }
    }

    fun complete(task: Task) {
        if (task.status == TaskStatus.DONE) return // tapping a done hold is a no-op; undo is the reversal
        viewModelScope.launch {
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
        fun factory(tasks: TaskRepository) = viewModelFactory {
            initializer { TasksViewModel(tasks) }
        }
    }
}
