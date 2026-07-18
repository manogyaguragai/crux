package com.crux.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.crux.app.data.AppContainer
import com.crux.app.domain.model.ParsedBy
import com.crux.app.domain.model.Task
import com.crux.app.intelligence.KnownProject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId

/** One AI proposal for the review tab: file this inbox task under this project, and the plain-language why. */
data class ReviewProposal(
    val task: Task,
    val projectId: Long,
    val projectName: String,
    val reason: String,
)

/**
 * The review tab (intelligence.md, phase 3): batched AI proposals the user accepts or rejects, never
 * silent edits. The one proposal type in this phase is project inference — the model reads the inbox
 * pile (open, unfiled tasks) and guesses which existing project each belongs to, with a short reason.
 * Nothing runs until the user asks: [scan] makes the one LLM call, and every accept is one explicit tap.
 */
class ReviewViewModel(private val container: AppContainer) : ViewModel() {

    private val tasks = container.taskRepository
    private val projects = container.projectRepository
    private val settings = container.settingsRepository
    private val keyStore = container.secureKeyStore
    private val intelligence = container.intelligence

    // Bumped by nothing in this VM, but folded in so aiActive re-reads the encrypted store on resubscribe.
    private val keyRefresh = MutableStateFlow(0)

    /** Whether the AI layer is on + keyed. When false the tab explains how to turn it on. */
    val aiActive: StateFlow<Boolean> =
        combine(settings.aiEnabled, settings.aiProvider, keyRefresh) { enabled, provider, _ ->
            enabled && provider != null && withContext(Dispatchers.IO) { keyStore.hasKey(provider.id) }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /** How many open tasks are still unfiled (the pile the scan would read). */
    val inboxCount: StateFlow<Int> =
        tasks.observeOpen().map { list -> list.count { it.projectId == null } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    private val _proposals = MutableStateFlow<List<ReviewProposal>>(emptyList())
    val proposals: StateFlow<List<ReviewProposal>> = _proposals.asStateFlow()

    private val _scanning = MutableStateFlow(false)
    val scanning: StateFlow<Boolean> = _scanning.asStateFlow()

    private val _scanned = MutableStateFlow(false)
    val scanned: StateFlow<Boolean> = _scanned.asStateFlow()

    // Rejected task ids, so "not now" keeps a suggestion from reappearing on the next scan this session.
    private val dismissed = mutableSetOf<Long>()

    fun scan() {
        if (_scanning.value) return
        viewModelScope.launch {
            _scanning.value = true
            try {
                val zone = ZoneId.systemDefault()
                val today = LocalDate.now(zone)
                val inbox = tasks.observeOpen().first()
                    .filter { it.projectId == null && it.id !in dismissed }
                val known = projects.observeActive().first().map { KnownProject(it.id, it.name) }
                val suggestions = intelligence.suggestProjects(inbox, known, today)
                _proposals.value = suggestions.mapNotNull { s ->
                    val task = inbox.firstOrNull { it.id == s.taskId } ?: return@mapNotNull null
                    val project = known.firstOrNull { it.name.equals(s.projectName, ignoreCase = true) }
                        ?: return@mapNotNull null
                    ReviewProposal(task, project.id, project.name, s.reason)
                }
                _scanned.value = true
            } finally {
                _scanning.value = false
            }
        }
    }

    /** File the task under the suggested project, marked AI so its provenance is honest. */
    fun accept(proposal: ReviewProposal) {
        viewModelScope.launch {
            tasks.updateTask(proposal.task.copy(projectId = proposal.projectId, parsedBy = ParsedBy.AI))
            _proposals.update { list -> list.filterNot { it.task.id == proposal.task.id } }
        }
    }

    /** Reject the proposal; the task stays in the inbox and will not be re-suggested this session. */
    fun dismiss(proposal: ReviewProposal) {
        dismissed += proposal.task.id
        _proposals.update { list -> list.filterNot { it.task.id == proposal.task.id } }
    }

    companion object {
        fun factory(container: AppContainer) = viewModelFactory {
            initializer { ReviewViewModel(container) }
        }
    }
}
