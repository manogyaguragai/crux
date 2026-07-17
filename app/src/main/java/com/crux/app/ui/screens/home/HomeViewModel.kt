package com.crux.app.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.crux.app.data.TaskRepository
import com.crux.app.domain.model.Task
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HomeUiState(val top: List<Task> = emptyList())

/**
 * Home screen state. Holds the top 3 open tasks of the master sort (data-model.md: home's top 3
 * is the first three OPEN tasks, nothing cleverer) and the capture action.
 */
class HomeViewModel(private val tasks: TaskRepository) : ViewModel() {

    val uiState: StateFlow<HomeUiState> =
        tasks.observeOpen()
            .map { HomeUiState(top = it.take(3)) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    /** Capture never fails: blank input is ignored, anything else becomes a title-only task. */
    fun capture(text: String) {
        if (text.isBlank()) return
        viewModelScope.launch { tasks.addTitleOnly(text, System.currentTimeMillis()) }
    }

    companion object {
        fun factory(tasks: TaskRepository) = viewModelFactory {
            initializer { HomeViewModel(tasks) }
        }
    }
}
