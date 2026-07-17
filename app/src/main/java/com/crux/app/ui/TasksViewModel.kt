package com.crux.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.crux.app.data.CompletionResult
import com.crux.app.data.ProjectRepository
import com.crux.app.data.SettingsRepository
import com.crux.app.data.TaskRepository
import com.crux.app.domain.StackGroup
import com.crux.app.domain.WeekDay
import com.crux.app.domain.groupStack
import com.crux.app.domain.groupWeek
import com.crux.app.domain.isOverdue
import com.crux.app.domain.model.Task
import com.crux.app.domain.model.TaskStatus
import com.crux.app.intelligence.KnownProject
import com.crux.app.intelligence.ParseField
import com.crux.app.intelligence.ProjectRef
import com.crux.app.intelligence.parse
import com.crux.app.intelligence.toTask
import java.time.Instant
import java.time.LocalDate
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
    private val settings: SettingsRepository,
) : ViewModel() {

    /** Home's open tasks, capped at the owner's configurable count (settings; default 3, max 10). */
    val homeTasks: StateFlow<List<Task>> =
        combine(tasks.observeOpen(), settings.homeCount) { list, count -> list.take(count) }
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

    /** The overdue pile (the screen behind the nudge): open + past due, most overdue first. */
    val overdueTasks: StateFlow<List<Task>> =
        tasks.observeOpen()
            .map { list ->
                val now = Instant.now()
                val zone = ZoneId.systemDefault()
                list.filter { isOverdue(it, now, zone) }.sortedBy { it.dueAt }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** The stack, grouped by project rank with the inbox last (empty groups omitted). */
    val groupedStack: StateFlow<List<StackGroup>> =
        combine(tasks.observeStack(), projects.observeActive()) { taskList, projectList ->
            groupStack(taskList, projectList, Copy.STACK_INBOX)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** The week view (stack tab's alternate mode): open tasks due in the next 7 days, by day. */
    val weekDays: StateFlow<List<WeekDay>> =
        tasks.observeOpen()
            .map { list -> groupWeek(list, LocalDate.now(ZoneId.systemDefault()), ZoneId.systemDefault()) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Active projects reduced to what the omnibar parser needs (id + name, for #tag matching). */
    val knownProjects: StateFlow<List<KnownProject>> =
        projects.observeActive()
            .map { list -> list.map { KnownProject(it.id, it.name) } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val undoChannel = Channel<Unit>(Channel.CONFLATED)
    val undoEvents = undoChannel.receiveAsFlow()

    /**
     * Ids mid-ceremony: tapped, striking through, not yet written DONE. The rows read this to draw
     * the strike-through in place before the task commits and sinks. Pruned the moment the db
     * confirms DONE, so `done` takes over the same frame the id leaves and nothing flickers.
     */
    private val completing = MutableStateFlow<Set<Long>>(emptySet())
    val completingIds: StateFlow<Set<Long>> = completing.asStateFlow()

    private var lastCompletion: Pair<Task, CompletionResult>? = null

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

    /**
     * Capture through the deterministic grammar. The omnibar hands over the raw line plus the set of
     * fields the user dismissed (a tapped chip); we re-parse authoritatively here — the UI's live
     * parse was only a preview. An unknown `#tag` is created on the spot and the task filed there;
     * if the title ends up empty (a line of nothing but tokens) we keep the raw text so nothing is
     * lost. Capture never fails.
     */
    fun capture(text: String, dismissed: Set<ParseField> = emptySet()) {
        if (text.isBlank()) return
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val zone = ZoneId.systemDefault()
            val today = LocalDate.now(zone)
            val result = parse(text, today, knownProjects.value, dismissed)
            val projectId = when (val p = result.project) {
                is ProjectRef.Matched -> p.id
                is ProjectRef.Unknown -> projects.create(p.raw, now) // null if blank/reserved -> inbox
                null -> null
            }
            val safe = if (result.title.isBlank()) result.copy(title = text.trim()) else result
            tasks.add(safe.toTask(projectId, zone, now))
        }
    }

    fun complete(task: Task) {
        if (task.status == TaskStatus.DONE) return // tapping a done hold is a no-op; undo is the reversal
        if (task.id in completing.value) return    // ceremony already running; ignore the re-tap
        completing.update { it + task.id }
        viewModelScope.launch {
            // savour the strike-through in place, then commit and let it sink. runs on
            // viewModelScope, so leaving the tab mid-ceremony still lands the completion.
            delay(Motion.StrikeMs.toLong())
            val result = tasks.complete(task, System.currentTimeMillis(), ZoneId.systemDefault())
            lastCompletion = task to result
            undoChannel.send(Unit)
        }
    }

    fun undoLast() {
        val (task, result) = lastCompletion ?: return
        viewModelScope.launch {
            tasks.undoComplete(task, result)
            lastCompletion = null
        }
    }

    companion object {
        fun factory(tasks: TaskRepository, projects: ProjectRepository, settings: SettingsRepository) =
            viewModelFactory {
                initializer { TasksViewModel(tasks, projects, settings) }
            }
    }
}
