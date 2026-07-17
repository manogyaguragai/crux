package com.crux.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.crux.app.data.TaskRepository
import com.crux.app.domain.model.CompletionLog
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

/** State for the total-history screen: every completion, newest first, straight from the log. */
class HistoryViewModel(tasks: TaskRepository) : ViewModel() {

    val entries: StateFlow<List<CompletionLog>> =
        tasks.observeHistory()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    companion object {
        fun factory(tasks: TaskRepository) = viewModelFactory {
            initializer { HistoryViewModel(tasks) }
        }
    }
}
