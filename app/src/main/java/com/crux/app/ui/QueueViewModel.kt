package com.crux.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.crux.app.data.AppContainer
import com.crux.app.data.queue.QueueItem
import com.crux.app.data.queue.QueueStatus
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** State + actions for the capture-queue icon and its dropdown. Thin wrapper over the shared queue. */
class QueueViewModel(container: AppContainer) : ViewModel() {

    private val queue = container.captureQueue

    /** Newest first, so a freshly-fired line sits at the top of the list. */
    val items: StateFlow<List<QueueItem>> =
        queue.items.map { list -> list.sortedByDescending { it.createdAt } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Pending + processing — the count the badge shows and the icon's ember state. */
    val activeCount: StateFlow<Int> =
        queue.items.map { list -> list.count { it.status == QueueStatus.PENDING || it.status == QueueStatus.PROCESSING } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val hasFailed: StateFlow<Boolean> =
        queue.items.map { list -> list.any { it.status == QueueStatus.FAILED } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun remove(id: Long) = queue.remove(id)
    fun retry(id: Long) = queue.retry(id)
    fun clearFinished() = queue.clearFinished()

    companion object {
        fun factory(container: AppContainer) = viewModelFactory {
            initializer { QueueViewModel(container) }
        }
    }
}
