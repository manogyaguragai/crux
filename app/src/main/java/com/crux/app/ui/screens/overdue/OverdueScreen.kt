package com.crux.app.ui.screens.overdue

import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.animation.core.VisibilityThreshold
import com.crux.app.domain.model.Task
import com.crux.app.ui.Copy
import com.crux.app.ui.TasksViewModel
import com.crux.app.ui.components.CruxIcons
import com.crux.app.ui.components.TaskRow
import com.crux.app.ui.theme.Cream
import com.crux.app.ui.theme.CruxType
import com.crux.app.ui.theme.Dimens
import com.crux.app.ui.theme.Garnet
import com.crux.app.ui.theme.InkHi
import com.crux.app.ui.theme.InkLow
import com.crux.app.ui.theme.InkMid
import com.crux.app.ui.theme.LocalVoid
import com.crux.app.ui.theme.Motion
import com.crux.app.ui.theme.Overdue
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit

/**
 * The overdue pile (ui-ux-decisions.md / phases.md): a pushed screen reached by tapping the home
 * nudge count. Just the open tasks that are genuinely past due, most overdue first, each still
 * tickable in place (completing one drops it from the pile). Each stone can be swiped right to carry
 * it to today or held to reschedule, and a garnet "carry all" sweeps the whole pile forward. Empties
 * out to a calm clear-trail line.
 */
@Composable
fun OverdueScreen(vm: TasksViewModel, onBack: () -> Unit, onOpenTask: (Long) -> Unit) {
    val tasks by vm.overdueTasks.collectAsStateWithLifecycle()
    val completing by vm.completingIds.collectAsStateWithLifecycle()
    val zone = ZoneId.systemDefault()

    Column(
        Modifier
            .fillMaxSize()
            .background(LocalVoid.current)
            .padding(horizontal = Dimens.ScreenMargin),
    ) {
        Spacer(Modifier.height(Dimens.Unit * 2))
        val backInteraction = remember { MutableInteractionSource() }
        Box(
            Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(Dimens.RadiusPill))
                .clickable(interactionSource = backInteraction, indication = null) { onBack() },
            contentAlignment = Alignment.CenterStart,
        ) {
            Icon(CruxIcons.Back, contentDescription = "back", tint = InkMid, modifier = Modifier.size(24.dp))
        }
        Spacer(Modifier.height(Dimens.Unit * 2))
        if (tasks.isNotEmpty()) {
            Text(Copy.OVERDUE_EYEBROW.uppercase(), style = CruxType.Eyebrow, color = InkLow)
            Spacer(Modifier.height(Dimens.Unit))
        }
        Text(Copy.OVERDUE_TITLE, style = CruxType.Display, color = InkHi)
        if (tasks.isNotEmpty()) {
            Spacer(Modifier.height(Dimens.Unit))
            OverdueSubline(tasks)
        }
        Spacer(Modifier.height(Dimens.GroupGap))

        if (tasks.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = Copy.OVERDUE_EMPTY,
                    style = CruxType.Passage,
                    color = InkMid,
                    textAlign = TextAlign.Center,
                )
            }
        } else {
            LazyColumn(Modifier.weight(1f)) {
                items(items = tasks, key = { it.id }) { task ->
                    OverdueTaskRow(
                        task = task,
                        completing = task.id in completing,
                        onToggle = { vm.complete(task) },
                        onOpen = { onOpenTask(task.id) },
                        onCarry = { vm.reschedule(task, LocalDate.now(zone)) },
                        onReschedule = { date -> vm.reschedule(task, date) },
                        modifier = Modifier.animateItem(
                            fadeOutSpec = tween(Motion.VanishMs, easing = Motion.EaseOut),
                            placementSpec = spring(
                                dampingRatio = Motion.ReorderDamping,
                                visibilityThreshold = IntOffset.VisibilityThreshold,
                            ),
                        ),
                    )
                }
            }

            // hint, then the sweep-all action, then the footer pinned near the bottom.
            Spacer(Modifier.height(Dimens.Unit * 2))
            Text(
                text = Copy.OVERDUE_HINT,
                style = CruxType.Data,
                color = InkLow,
                modifier = Modifier.padding(horizontal = Dimens.Unit),
            )
            Spacer(Modifier.height(Dimens.Unit * 3))
            CarryAllButton(onClick = { vm.carryAllToToday() })
            Spacer(Modifier.height(Dimens.Unit * 4))
            Text(
                text = Copy.OVERDUE_FOOTER,
                style = CruxType.Data,
                color = InkLow,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = Dimens.Unit * 4),
            )
        }
    }
}

/** The garnet full-width primary that carries the whole pile forward to today. */
@Composable
private fun CarryAllButton(onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Dimens.RadiusPill))
            .background(Garnet)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(vertical = 13.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(Copy.OVERDUE_CARRY_ALL, style = CruxType.Action, color = Cream)
    }
}

/**
 * An overdue row with its two gestures: swipe right carries the single stone to today (snaps back,
 * the write drops it from the pile), hold opens a date picker to reschedule it elsewhere. Tap still
 * opens the task and the hold checkbox still completes it (both via the underlying TaskRow).
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun OverdueTaskRow(
    task: Task,
    completing: Boolean,
    onToggle: () -> Unit,
    onOpen: () -> Unit,
    onCarry: () -> Unit,
    onReschedule: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    val zone = ZoneId.systemDefault()
    var showDatePicker by remember { mutableStateOf(false) }

    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            when (value) {
                SwipeToDismissBoxValue.StartToEnd -> { onCarry(); false } // swipe right = carry to today (snap back)
                else -> false
            }
        },
    )

    SwipeToDismissBox(
        state = dismissState,
        modifier = modifier,
        enableDismissFromEndToStart = false,
        backgroundContent = {
            val toEnd = dismissState.targetValue == SwipeToDismissBoxValue.StartToEnd
            Box(
                Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(Dimens.RadiusPill))
                    .background(if (toEnd) Overdue.copy(alpha = 0.18f) else LocalVoid.current)
                    .padding(horizontal = Dimens.Unit * 4),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (toEnd) Text("today", style = CruxType.Action, color = Overdue)
            }
        },
    ) {
        // onOpen is handled by the outer combinedClickable so the same gesture area also carries the
        // hold-to-reschedule; the inner row keeps only its hold checkbox (onToggle → complete).
        TaskRow(
            task = task,
            completing = completing,
            onToggle = onToggle,
            onOpen = null,
            modifier = Modifier
                .background(LocalVoid.current)
                .combinedClickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onOpen,
                    onLongClick = { showDatePicker = true },
                ),
        )
    }

    if (showDatePicker) {
        val initial = task.dueAt?.let {
            Instant.ofEpochMilli(it).atZone(zone).toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        } ?: LocalDate.now(zone).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val state = rememberDatePickerState(initialSelectedDateMillis = initial)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { utc ->
                        onReschedule(Instant.ofEpochMilli(utc).atZone(ZoneOffset.UTC).toLocalDate())
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
}

/** The subline under "overdue": the waiting count in overdue red, plus the age of the oldest stone. */
@Composable
private fun OverdueSubline(tasks: List<com.crux.app.domain.model.Task>) {
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now(zone)
    val oldestDays = tasks.mapNotNull { it.dueAt }
        .minOfOrNull { ChronoUnit.DAYS.between(Instant.ofEpochMilli(it).atZone(zone).toLocalDate(), today) }
        ?.coerceAtLeast(0)
    Row {
        Text("${tasks.size} waiting", style = CruxType.Data, color = Overdue)
        if (oldestDays != null && oldestDays > 0) {
            Text(" · oldest $oldestDays days", style = CruxType.Data, color = InkLow)
        }
    }
}
