package com.crux.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.crux.app.data.AppContainer
import com.crux.app.domain.NudgeUrgency
import com.crux.app.domain.priorityNudges
import com.crux.app.domain.withinGroupComparator
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
import java.time.Instant
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
 * One deterministic priority nudge: this low-priority task is urgent, so propose bumping it to
 * [targetPriority]. Computed by rules (no LLM), so it stands even with AI off; [reason] is the plain why.
 */
data class PriorityNudge(
    val task: Task,
    val targetPriority: Int,
    val reason: String,
)

/**
 * A nudge plus the neighbor it would leapfrog: [above] is the open task currently sitting immediately
 * ABOVE [nudge]'s task in the app's sort — the one the task would move past once its priority rises.
 * Null when the task is already at the top or the neighbor cannot be resolved (degrade gracefully).
 * Lets the review card render a before/after "reorder" comparison of the two rows.
 */
data class NudgeContext(
    val nudge: PriorityNudge,
    val above: Task?,
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

    // True when the last scan could not reach the provider (quota/network) — the tab says so instead
    // of pretending there was simply nothing to suggest.
    private val _scanFailed = MutableStateFlow(false)
    val scanFailed: StateFlow<Boolean> = _scanFailed.asStateFlow()

    // Rejected task ids, so "not now" keeps a suggestion from reappearing on the next scan this session.
    private val dismissed = mutableSetOf<Long>()

    // Task ids the user waved off from the reprioritize list this session (kept out of the live flow).
    private val skippedNudges = MutableStateFlow<Set<Long>>(emptySet())

    /**
     * Deterministic priority nudges: open tasks left at a low priority (p3/p4) whose due date has become
     * urgent (see [priorityNudges]). Pure rules (no LLM, no scan), so this list is live and stands even
     * with AI off; here we just attach the plain-language reason copy for each urgency.
     */
    val nudges: StateFlow<List<PriorityNudge>> =
        combine(tasks.observeOpen(), skippedNudges) { open, skip ->
            val zone = ZoneId.systemDefault()
            priorityNudges(open, skip, Instant.now(), LocalDate.now(zone), zone).map { s ->
                val reason = when (s.urgency) {
                    NudgeUrgency.OVERDUE -> "overdue, still p${s.task.priority}"
                    NudgeUrgency.DUE_TODAY -> "due today, still p${s.task.priority}"
                    NudgeUrgency.DUE_TOMORROW -> "due tomorrow, still p${s.task.priority}"
                }
                PriorityNudge(s.task, s.targetPriority, reason)
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * Each nudge paired with the open task it sits directly below in the app's sort — the neighbor it
     * would leapfrog once bumped. We re-sort the open pile by [withinGroupComparator], find the nudge's
     * task, and take the task immediately above it (null at the top / if unresolved). The review card
     * renders both rows as a before/after "reorder" comparison.
     */
    val nudgeContexts: StateFlow<List<NudgeContext>> =
        combine(nudges, tasks.observeOpen()) { list, open ->
            val sorted = open.sortedWith(withinGroupComparator)
            list.map { n ->
                val idx = sorted.indexOfFirst { it.id == n.task.id }
                val above = if (idx > 0) sorted[idx - 1] else null
                NudgeContext(n, above)
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Apply the nudge: raise the task's priority. A manual/rules act, so provenance is left untouched. */
    fun acceptNudge(nudge: PriorityNudge) {
        viewModelScope.launch { tasks.updateTask(nudge.task.copy(priority = nudge.targetPriority)) }
    }

    /** Wave off a nudge; it stays at its priority and will not reappear this session. */
    fun skipNudge(nudge: PriorityNudge) {
        skippedNudges.update { it + nudge.task.id }
    }

    fun scan() {
        if (_scanning.value) return
        viewModelScope.launch {
            _scanning.value = true
            _scanFailed.value = false
            try {
                val zone = ZoneId.systemDefault()
                val today = LocalDate.now(zone)
                val inbox = tasks.observeOpen().first()
                    .filter { it.projectId == null && it.id !in dismissed }
                val known = projects.observeActive().first().map { KnownProject(it.id, it.name) }
                val suggestions = intelligence.suggestProjects(inbox, known, today)
                if (suggestions == null) {
                    _scanFailed.value = true // the provider could not be reached
                } else {
                    _proposals.value = suggestions.mapNotNull { s ->
                        val task = inbox.firstOrNull { it.id == s.taskId } ?: return@mapNotNull null
                        val project = known.firstOrNull { it.name.equals(s.projectName, ignoreCase = true) }
                            ?: return@mapNotNull null
                        ReviewProposal(task, project.id, project.name, s.reason)
                    }
                    _scanned.value = true
                }
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

    /**
     * Clear the whole queue in one tap: apply every pending nudge (raise each task) and file every
     * pending proposal, reusing the same per-item logic as [acceptNudge] and [accept] so provenance
     * and flow updates stay identical. Snapshots the current lists first, since each accept mutates them.
     */
    fun acceptAll() {
        nudges.value.forEach { acceptNudge(it) }
        proposals.value.forEach { accept(it) }
    }

    companion object {
        fun factory(container: AppContainer) = viewModelFactory {
            initializer { ReviewViewModel(container) }
        }
    }
}
