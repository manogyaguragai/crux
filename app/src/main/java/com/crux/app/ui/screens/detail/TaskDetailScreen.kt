package com.crux.app.ui.screens.detail

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.crux.app.domain.model.ParsedBy
import com.crux.app.domain.model.RecurrenceType
import com.crux.app.domain.model.Source
import com.crux.app.domain.model.Task
import com.crux.app.ui.Copy
import com.crux.app.ui.TaskDetailViewModel
import com.crux.app.ui.components.CruxIcons
import com.crux.app.ui.theme.Cream
import com.crux.app.ui.theme.CruxType
import com.crux.app.ui.theme.Dimens
import com.crux.app.ui.theme.Garnet
import com.crux.app.ui.theme.Hairline
import com.crux.app.ui.theme.InkHi
import com.crux.app.ui.theme.InkLow
import com.crux.app.ui.theme.InkMid
import com.crux.app.ui.theme.Overlay
import com.crux.app.ui.theme.Raised
import com.crux.app.ui.theme.Surface
import com.crux.app.ui.theme.Void
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * The task detail screen (a pushed screen, ui-ux-decisions.md). Edits write straight through: the
 * chips and pickers commit on tap; the title and notes commit on blur or on leaving. Phase 1 sets
 * project / priority / due / time / recurrence / notes explicitly; typed shortcuts are phase 2.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskDetailScreen(vm: TaskDetailViewModel, onBack: () -> Unit) {
    val task by vm.task.collectAsStateWithLifecycle()
    val projects by vm.projects.collectAsStateWithLifecycle()
    val zone = remember { ZoneId.systemDefault() }
    val current = task

    var titleDraft by remember(current?.id) { mutableStateOf(current?.title ?: "") }
    var notesDraft by remember(current?.id) { mutableStateOf(current?.notes ?: "") }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }

    fun commitText() {
        val t = current ?: return
        if (titleDraft.trim().isNotEmpty() && titleDraft.trim() != t.title) vm.setTitle(titleDraft)
        if (notesDraft.trim() != (t.notes ?: "")) vm.setNotes(notesDraft)
    }
    fun leave() {
        commitText()
        onBack()
    }
    BackHandler { leave() }

    Column(
        Modifier
            .fillMaxSize()
            .background(Void)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Dimens.ScreenMargin),
    ) {
        Spacer(Modifier.height(Dimens.Unit * 2))
        // back affordance (pushed screen)
        val backInteraction = remember { MutableInteractionSource() }
        Box(
            Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(Dimens.RadiusPill))
                .clickable(interactionSource = backInteraction, indication = null) { leave() },
            contentAlignment = Alignment.CenterStart,
        ) {
            Icon(CruxIcons.Back, contentDescription = "back", tint = InkMid, modifier = Modifier.size(24.dp))
        }
        Spacer(Modifier.height(Dimens.Unit * 2))

        if (current == null) {
            // task gone (e.g. swept/undone away) or still loading: nothing to edit.
            return@Column
        }

        // title (editable, the one large field)
        BasicTextField(
            value = titleDraft,
            onValueChange = { titleDraft = it },
            textStyle = CruxType.Subhead.copy(color = InkHi),
            cursorBrush = SolidColor(Garnet),
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { if (!it.isFocused) commitText() },
            decorationBox = { inner ->
                if (titleDraft.isEmpty()) {
                    Text(Copy.DETAIL_TITLE_PLACEHOLDER, style = CruxType.Subhead, color = InkLow)
                }
                inner()
            },
        )
        Spacer(Modifier.height(Dimens.GroupGap))

        // project
        Section(Copy.DETAIL_PROJECT) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Dimens.Unit * 2)) {
                Chip(Copy.DETAIL_INBOX, selected = current.projectId == null) { vm.setProject(null) }
                projects.forEach { p ->
                    Chip(p.name, selected = current.projectId == p.id) { vm.setProject(p.id) }
                }
            }
        }

        // priority (p1 carries the rationed garnet fill; p2-p4 a neutral fill)
        Section(Copy.DETAIL_PRIORITY) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Dimens.Unit * 2)) {
                (1..4).forEach { p ->
                    Chip(
                        label = "p$p",
                        selected = current.priority == p,
                        accent = if (p == 1) Garnet else null,
                    ) { vm.setPriority(p) }
                }
            }
        }

        // due date
        Section(Copy.DETAIL_DUE) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Chip(
                    label = current.dueAt?.let { formatDate(it, zone) } ?: Copy.DETAIL_SET_DATE,
                    selected = current.dueAt != null,
                ) { showDatePicker = true }
                if (current.dueAt != null) {
                    Spacer(Modifier.width(Dimens.Unit * 2))
                    ClearButton { vm.setDue(null) }
                }
            }
        }

        // time (only meaningful once there is a date)
        if (current.dueAt != null) {
            Section(Copy.DETAIL_TIME) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Dimens.Unit * 2)) {
                    Chip(Copy.DETAIL_ALL_DAY, selected = !current.hasTime) {
                        vm.setTime(startOfDay(current.dueAt, zone), hasTime = false)
                    }
                    Chip(
                        label = if (current.hasTime) formatTime(current.dueAt, zone) else Copy.DETAIL_TIME,
                        selected = current.hasTime,
                    ) { showTimePicker = true }
                }
            }
        }

        // repeat
        Section(Copy.DETAIL_REPEAT) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Dimens.Unit * 2)) {
                Chip(Copy.DETAIL_NONE, selected = current.recurrenceType == null) {
                    vm.setRecurrence(null, null, null)
                }
                RecurrenceType.entries.forEachIndexed { i, type ->
                    Chip(Copy.RECURRENCE_LABELS[i], selected = current.recurrenceType == type) {
                        applyRecurrence(vm, type, current, zone)
                    }
                }
            }
            recurrenceHint(current)?.let { hint ->
                Spacer(Modifier.height(Dimens.Unit * 2))
                Text(hint, style = CruxType.Data, color = InkLow)
            }
        }

        // notes
        Section(Copy.DETAIL_NOTES) {
            BasicTextField(
                value = notesDraft,
                onValueChange = { notesDraft = it },
                textStyle = CruxType.Body.copy(color = InkHi),
                cursorBrush = SolidColor(Garnet),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = ImeAction.Default),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Dimens.RadiusCard))
                    .background(Raised)
                    .padding(14.dp)
                    .onFocusChanged { if (!it.isFocused) commitText() },
                decorationBox = { inner ->
                    if (notesDraft.isEmpty()) {
                        Text(Copy.DETAIL_NOTES_PLACEHOLDER, style = CruxType.Body, color = InkLow)
                    }
                    inner()
                },
            )
        }

        // provenance line (numbers-first, mono): how this task arrived.
        Text(formatProvenance(current, zone), style = CruxType.Data, color = InkLow)
        Spacer(Modifier.height(Dimens.GroupGap))
    }

    if (showDatePicker) {
        val initial = current?.dueAt?.let { toUtcMidnight(it, zone) }
            ?: toUtcMidnight(System.currentTimeMillis(), zone)
        val state = rememberDatePickerState(initialSelectedDateMillis = initial)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { utc ->
                        val date = Instant.ofEpochMilli(utc).atZone(ZoneOffset.UTC).toLocalDate()
                        val hadTime = current?.hasTime == true
                        val time = current?.dueAt?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalTime() }
                        val millis = if (hadTime && time != null) {
                            date.atTime(time).atZone(zone).toInstant().toEpochMilli()
                        } else {
                            date.atStartOfDay(zone).toInstant().toEpochMilli()
                        }
                        vm.setDue(millis)
                    }
                    showDatePicker = false
                }) { Text(Copy.DETAIL_DIALOG_OK) }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text(Copy.DETAIL_DIALOG_CANCEL) }
            },
        ) {
            DatePicker(state = state)
        }
    }

    if (showTimePicker && current?.dueAt != null) {
        val existing = Instant.ofEpochMilli(current.dueAt).atZone(zone)
        val state = rememberTimePickerState(
            initialHour = if (current.hasTime) existing.hour else 9,
            initialMinute = if (current.hasTime) existing.minute else 0,
            is24Hour = false,
        )
        DatePickerDialog(
            onDismissRequest = { showTimePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    val date = Instant.ofEpochMilli(current.dueAt).atZone(zone).toLocalDate()
                    val millis = date.atTime(state.hour, state.minute).atZone(zone).toInstant().toEpochMilli()
                    vm.setTime(millis, hasTime = true)
                    showTimePicker = false
                }) { Text(Copy.DETAIL_DIALOG_OK) }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) { Text(Copy.DETAIL_DIALOG_CANCEL) }
            },
        ) {
            Box(Modifier.padding(Dimens.ScreenMargin), contentAlignment = Alignment.Center) {
                TimePicker(state = state)
            }
        }
    }
}

@Composable
private fun Section(label: String, content: @Composable () -> Unit) {
    Text(label.uppercase(), style = CruxType.Eyebrow, color = InkLow)
    Spacer(Modifier.height(Dimens.Unit * 2))
    content()
    Spacer(Modifier.height(Dimens.GroupGap))
}

/** A pill. Unselected: quiet outline. Selected: a neutral fill, or [accent] (p1's rationed garnet). */
@Composable
private fun Chip(
    label: String,
    selected: Boolean,
    accent: Color? = null,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val bg = when {
        selected && accent != null -> accent
        selected -> Overlay
        else -> Surface
    }
    val fg = when {
        selected && accent != null -> Cream
        selected -> InkHi
        else -> InkMid
    }
    Box(
        Modifier
            .clip(RoundedCornerShape(Dimens.RadiusPill))
            .background(bg)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = Dimens.Unit * 4, vertical = Dimens.Unit * 2),
    ) {
        Text(label, style = CruxType.Action, color = fg)
    }
}

@Composable
private fun ClearButton(onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Text(
        text = Copy.DETAIL_CLEAR,
        style = CruxType.Action,
        color = InkMid,
        modifier = Modifier
            .clip(RoundedCornerShape(Dimens.RadiusPill))
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = Dimens.Unit * 3, vertical = Dimens.Unit * 2),
    )
}

private val DateFmt = DateTimeFormatter.ofPattern("MMM d", Locale.ENGLISH)
private val TimeFmt = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)

private fun formatDate(millis: Long, zone: ZoneId): String =
    Instant.ofEpochMilli(millis).atZone(zone).format(DateFmt).lowercase(Locale.ENGLISH)

private fun formatTime(millis: Long, zone: ZoneId): String =
    Instant.ofEpochMilli(millis).atZone(zone).format(TimeFmt).lowercase(Locale.ENGLISH)

private fun startOfDay(millis: Long, zone: ZoneId): Long =
    Instant.ofEpochMilli(millis).atZone(zone).toLocalDate().atStartOfDay(zone).toInstant().toEpochMilli()

/** DatePicker works in UTC; convert a local date to the UTC-midnight millis it expects. */
private fun toUtcMidnight(millis: Long, zone: ZoneId): Long =
    Instant.ofEpochMilli(millis).atZone(zone).toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

private fun applyRecurrence(vm: TaskDetailViewModel, type: RecurrenceType, task: Task, zone: ZoneId) {
    val anchor = task.dueAt?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() } ?: LocalDate.now(zone)
    when (type) {
        RecurrenceType.DAILY, RecurrenceType.WEEKDAYS -> vm.setRecurrence(type, null, null)
        RecurrenceType.WEEKLY -> vm.setRecurrence(type, anchor.dayOfWeek.value, null)
        RecurrenceType.MONTHLY -> vm.setRecurrence(type, null, anchor.dayOfMonth)
    }
}

private fun recurrenceHint(task: Task): String? = when (task.recurrenceType) {
    RecurrenceType.WEEKLY -> task.recurrenceWeekday?.let {
        "every " + DayOfWeek.of(it).getDisplayName(java.time.format.TextStyle.FULL, Locale.ENGLISH).lowercase(Locale.ENGLISH)
    }
    RecurrenceType.MONTHLY -> task.recurrenceDay?.let { "on the ${ordinal(it)}" }
    else -> null
}

private fun ordinal(n: Int): String {
    val suffix = if (n in 11..13) "th" else when (n % 10) {
        1 -> "st"; 2 -> "nd"; 3 -> "rd"; else -> "th"
    }
    return "$n$suffix"
}

private fun formatProvenance(task: Task, zone: ZoneId): String {
    val added = "added " + formatDate(task.createdAt, zone)
    val via = when (task.source) {
        Source.TYPED -> "typed"
        Source.VOICE -> "voice"
        Source.SYSTEM -> "system"
    }
    val parsed = when (task.parsedBy) {
        ParsedBy.MANUAL -> null
        ParsedBy.RULES -> "parsed by rules"
        ParsedBy.AI -> "parsed by ai"
    }
    return listOfNotNull(added, via, parsed).joinToString(" · ")
}
