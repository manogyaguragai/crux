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
import com.crux.app.domain.model.ParsedBy
import com.crux.app.domain.model.Task
import com.crux.app.domain.model.TaskStatus
import com.crux.app.intelligence.FuzzyResult
import com.crux.app.intelligence.Intelligence
import com.crux.app.intelligence.KnownProject
import com.crux.app.intelligence.LlmAction
import com.crux.app.intelligence.ParseField
import com.crux.app.intelligence.ProjectRef
import com.crux.app.intelligence.matchTask
import com.crux.app.intelligence.parse
import com.crux.app.intelligence.toTask
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import com.crux.app.ui.theme.Motion
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
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
    private val intelligence: Intelligence,
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

    // AI command-mode outcomes the shell renders: a disambiguation pick list, a destructive confirm,
    // or a plain message (a query answer, "not found", the offline notice). Completions still ride the
    // existing undoChannel so an AI "tick x" gets the same 5 s undo as a tap.
    private val commandChannel = Channel<CommandOutcome>(Channel.BUFFERED)
    val commandEvents = commandChannel.receiveAsFlow()

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
            val rules = parse(text, today, knownProjects.value, dismissed)
            // The LLM step runs only when AI is on and keyed; otherwise interpret() returns null and
            // this is exactly the phase-2 behavior. AI never rewrites the user's words — for `add` it
            // only fills structured fields the rules left blank; the destructive verbs match locally.
            val action = intelligence.interpret(text, today, zone, knownProjects.value)
            when (action) {
                is LlmAction.Complete -> handleComplete(action.query)
                is LlmAction.Delete -> handleDelete(action.query)
                is LlmAction.Reschedule -> handleReschedule(action, today, zone)
                is LlmAction.Query -> handleQuery(action.question, zone)
                is LlmAction.Add -> addTask(text, rules, action, now, zone)
                null -> addTask(text, rules, null, now, zone)
            }
        }
    }

    /**
     * File a captured line as a task. Rules win every field they filled; the AI [ai] only fills the
     * gaps — a project (must be one the user already has, never invented), a due date, a priority, a
     * recurrence — and any field it supplies flips provenance to AI. The title is always the user's own
     * cleaned text, never the model's rewrite, so capture cannot silently reword a task.
     */
    private suspend fun addTask(text: String, rules: com.crux.app.intelligence.ParseResult, ai: LlmAction.Add?, now: Long, zone: ZoneId) {
        var aiTouched = false
        var projectId = when (val p = rules.project) {
            is ProjectRef.Matched -> p.id
            is ProjectRef.Unknown -> projects.create(p.raw, now)
            null -> null
        }
        if (projectId == null && ai?.project != null) {
            val known = knownProjects.value.firstOrNull { it.name.equals(ai.project, ignoreCase = true) }
            if (known != null) { projectId = known.id; aiTouched = true }
        }
        val safe = if (rules.title.isBlank()) rules.copy(title = text.trim()) else rules
        var task = safe.toTask(projectId, zone, now)
        if (ai != null) {
            if (task.dueAt == null && ai.due != null) {
                val withTime = ai.hasTime && ai.time != null
                val local = ai.due.atTime(if (withTime) ai.time else LocalTime.MIDNIGHT)
                task = task.copy(
                    dueAt = local.atZone(zone).toInstant().toEpochMilli(),
                    hasTime = withTime,
                )
                aiTouched = true
            }
            if (rules.priority == null && ai.priority != null) {
                task = task.copy(priority = ai.priority); aiTouched = true
            }
            if (rules.recurrenceType == null && ai.recurrenceType != null) {
                task = task.copy(
                    recurrenceType = ai.recurrenceType,
                    recurrenceWeekday = ai.recurrenceWeekday,
                    recurrenceDay = ai.recurrenceDay,
                )
                aiTouched = true
            }
        }
        if (aiTouched) task = task.copy(parsedBy = ParsedBy.AI)
        tasks.add(task)
    }

    private suspend fun handleComplete(query: String) {
        when (val m = matchTask(query, tasks.observeOpen().first())) {
            is FuzzyResult.One -> complete(m.task) // rides the same 5 s undo as a tap
            is FuzzyResult.Many -> commandChannel.send(CommandOutcome.Pick(CommandKind.COMPLETE, m.candidates, null, false))
            FuzzyResult.None -> commandChannel.send(CommandOutcome.Message(Copy.AI_NOT_FOUND))
        }
    }

    private suspend fun handleDelete(query: String) {
        when (val m = matchTask(query, tasks.observeOpen().first())) {
            // one auto-match still needs an explicit confirm; an explicit pick is itself the confirm.
            is FuzzyResult.One -> commandChannel.send(CommandOutcome.ConfirmArchive(m.task))
            is FuzzyResult.Many -> commandChannel.send(CommandOutcome.Pick(CommandKind.DELETE, m.candidates, null, false))
            FuzzyResult.None -> commandChannel.send(CommandOutcome.Message(Copy.AI_NOT_FOUND))
        }
    }

    private suspend fun handleReschedule(action: LlmAction.Reschedule, today: LocalDate, zone: ZoneId) {
        val due = action.due ?: run {
            commandChannel.send(CommandOutcome.Message(Copy.AI_NOT_FOUND)); return
        }
        val withTime = action.hasTime && action.time != null
        val newDueAt = due.atTime(if (withTime) action.time else LocalTime.MIDNIGHT)
            .atZone(zone).toInstant().toEpochMilli()
        when (val m = matchTask(action.query, tasks.observeOpen().first())) {
            is FuzzyResult.One -> commandChannel.send(
                CommandOutcome.ConfirmReschedule(m.task, newDueAt, withTime, dateLabel(m.task.dueAt, zone), dateLabel(newDueAt, zone)),
            )
            is FuzzyResult.Many -> commandChannel.send(CommandOutcome.Pick(CommandKind.RESCHEDULE, m.candidates, newDueAt, withTime))
            FuzzyResult.None -> commandChannel.send(CommandOutcome.Message(Copy.AI_NOT_FOUND))
        }
    }

    /** Answer a "query" action locally from the open list — never from a model-authored string. */
    private suspend fun handleQuery(question: String, zone: ZoneId) {
        val open = tasks.observeOpen().first()
        val now = Instant.now()
        val overdue = open.count { isOverdue(it, now, zone) }
        val q = question.lowercase()
        val answer = when {
            "overdue" in q -> if (overdue == 0) "nothing overdue" else "$overdue overdue"
            "today" in q -> {
                val today = LocalDate.now(zone)
                val due = open.count { t -> t.dueAt?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() == today } == true }
                if (due == 0) "nothing due today" else "$due due today"
            }
            else -> "${open.size} open, $overdue overdue"
        }
        commandChannel.send(CommandOutcome.Message(answer))
    }

    /** Apply an AI command to the task the user picked from a disambiguation list. */
    fun resolvePick(pick: CommandOutcome.Pick, task: Task) {
        when (pick.kind) {
            CommandKind.COMPLETE -> complete(task)
            CommandKind.DELETE -> archive(task)
            CommandKind.RESCHEDULE -> pick.targetDueAt?.let { reschedule(task, it, pick.hasTime) }
        }
    }

    fun confirmArchive(task: Task) = archive(task)

    fun confirmReschedule(task: Task, newDueAt: Long, hasTime: Boolean) = reschedule(task, newDueAt, hasTime)

    private fun archive(task: Task) = launchWrite {
        tasks.updateTask(task.copy(status = TaskStatus.ARCHIVED))
    }

    private fun reschedule(task: Task, newDueAt: Long, hasTime: Boolean) = launchWrite {
        tasks.updateTask(task.copy(dueAt = newDueAt, hasTime = hasTime))
    }

    private fun launchWrite(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
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

    /** "mmm d" for a due instant, or "none" for an undated task (used in the reschedule confirm). */
    private fun dateLabel(dueAt: Long?, zone: ZoneId): String =
        dueAt?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate().format(DATE_LABEL).lowercase(Locale.ENGLISH) }
            ?: Copy.DETAIL_NONE

    companion object {
        private val DATE_LABEL = DateTimeFormatter.ofPattern("MMM d", Locale.ENGLISH)

        fun factory(
            tasks: TaskRepository,
            projects: ProjectRepository,
            settings: SettingsRepository,
            intelligence: Intelligence,
        ) = viewModelFactory {
            initializer { TasksViewModel(tasks, projects, settings, intelligence) }
        }
    }
}

/** An AI command's outcome, surfaced to the shell (CruxApp) which renders the sheet or snackbar. */
sealed interface CommandOutcome {
    /** Several tasks fuzzy-matched; the user picks which one the [kind] command applies to. */
    data class Pick(
        val kind: CommandKind,
        val candidates: List<Task>,
        val targetDueAt: Long?, // reschedule target, null for complete/delete
        val hasTime: Boolean,
    ) : CommandOutcome

    data class ConfirmArchive(val task: Task) : CommandOutcome
    data class ConfirmReschedule(
        val task: Task,
        val newDueAt: Long,
        val hasTime: Boolean,
        val fromLabel: String,
        val toLabel: String,
    ) : CommandOutcome

    /** A one-line answer or notice (query result, "not found", the offline chip). */
    data class Message(val text: String) : CommandOutcome
}

enum class CommandKind { COMPLETE, DELETE, RESCHEDULE }
