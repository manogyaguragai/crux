package com.crux.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import com.crux.app.domain.isOverdue
import com.crux.app.domain.model.ParsedBy
import com.crux.app.domain.model.RecurrenceType
import com.crux.app.domain.model.Task
import com.crux.app.domain.model.TaskStatus
import com.crux.app.ui.Copy
import com.crux.app.ui.theme.Blush
import com.crux.app.ui.theme.CruxType
import com.crux.app.ui.theme.Dimens
import com.crux.app.ui.theme.Ember
import com.crux.app.ui.theme.Gold
import com.crux.app.ui.theme.InkHi
import com.crux.app.ui.theme.InkLow
import com.crux.app.ui.theme.InkMid
import com.crux.app.ui.theme.Motion
import com.crux.app.ui.theme.Overdue
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/**
 * A task row: the hold, then the title.
 *
 * Completing a task is a small ceremony (DECISIONS.log 2026-07-17): the hold fills at once, then a
 * line draws left-to-right across the title over ~0.75 s while the ink fades InkHi -> InkLow. Only
 * after that does the row commit and sink; here we just render the state the view-model hands us.
 * [completing] is true while that draw is in flight (the task is still OPEN in the db until it lands).
 * [onToggle] null renders the hold display-only. [onOpen] (tap the title) opens the detail screen.
 */
@Composable
fun TaskRow(
    task: Task,
    modifier: Modifier = Modifier,
    completing: Boolean = false,
    onToggle: (() -> Unit)? = null,
    onOpen: (() -> Unit)? = null,
) {
    val done = task.status == TaskStatus.DONE
    // one signal drives the whole ceremony: struck once the hold is tapped (completing) and stays
    // struck once the db confirms done. entry is the slow savour; the retract on undo is quick.
    val struck = done || completing
    val strike by animateFloatAsState(
        targetValue = if (struck) 1f else 0f,
        animationSpec = tween(
            durationMillis = if (struck) Motion.StrikeMs else Motion.StrikeRetractMs,
            easing = Motion.EaseOut,
        ),
        label = "strike-through",
    )
    val ink by animateColorAsState(
        targetValue = if (struck) InkLow else InkHi,
        animationSpec = tween(
            durationMillis = if (struck) Motion.StrikeMs else Motion.StrikeRetractMs,
            easing = Motion.EaseOut,
        ),
        label = "title-fade",
    )

    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    val bodyInteraction = remember { MutableInteractionSource() }
    // a synced calendar event (mockup 03): not a tickable stone, it wears the gold glyph instead.
    val isEvent = task.calendarEventId != null

    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = Dimens.RowMinHeight),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (isEvent) {
            Icon(
                imageVector = CruxIcons.Calendar,
                contentDescription = null,
                tint = Gold,
                modifier = Modifier.size(20.dp),
            )
        } else {
            HoldCheckbox(checked = struck, onToggle = onToggle)
        }
        Spacer(Modifier.width(Dimens.Unit * 2))
        Box(
            modifier = Modifier
                .weight(1f)
                .then(
                    if (onOpen != null) {
                        Modifier.clickable(
                            interactionSource = bodyInteraction,
                            indication = null,
                            onClick = onOpen,
                        )
                    } else Modifier
                ),
            contentAlignment = Alignment.CenterStart,
        ) {
            Column {
                Text(
                    text = task.title,
                    style = CruxType.Body,
                    color = ink,
                    onTextLayout = { layout = it },
                    modifier = Modifier.drawWithContent {
                        drawContent()
                        val result = layout ?: return@drawWithContent
                        if (strike <= 0f) return@drawWithContent
                        val thickness = StrikeThickness.toPx()
                        // total ink to spend across every line, filled top line first
                        val widths = FloatArray(result.lineCount) { i ->
                            result.getLineRight(i) - result.getLineLeft(i)
                        }
                        var budget = strike * widths.sum()
                        for (i in 0 until result.lineCount) {
                            if (budget <= 0f) break
                            val drawn = minOf(budget, widths[i])
                            val y = (result.getLineTop(i) + result.getLineBottom(i)) / 2f
                            val x0 = result.getLineLeft(i)
                            drawLine(
                                color = InkMid,
                                start = Offset(x0, y),
                                end = Offset(x0 + drawn, y),
                                strokeWidth = thickness,
                                cap = StrokeCap.Round,
                            )
                            budget -= widths[i]
                        }
                    },
                )
                TaskMeta(task = task, faded = struck, event = isEvent)
            }
        }
    }
}

/**
 * The meta line under a title: priority (p1/p2/p4, the default p3 stays silent), the due date
 * (coloured by urgency: overdue red, due-soon gold, else muted), and an `ai` tag when the model
 * touched this task (blush tint + ember — no silent AI, ui-ux-decisions.md). Renders nothing when a
 * task carries none of these, keeping bare rows clean. Fades with the title when completing/done.
 */
@Composable
private fun TaskMeta(task: Task, faded: Boolean, event: Boolean = false) {
    // a synced event reads differently (mockup 03): gold clock time + "event", then the gcal tag.
    if (event) {
        Spacer(Modifier.height(2.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            task.dueAt?.let { due ->
                val zdt = Instant.ofEpochMilli(due).atZone(ZoneId.systemDefault())
                val time = zdt.format(MetaTimeFmt).lowercase(Locale.ENGLISH)
                Text(text = "$time · event", style = CruxType.Data, color = Gold)
                Spacer(Modifier.width(Dimens.Unit * 2))
            }
            Text(text = Copy.WEEK_SYNCED, style = CruxType.Data, color = InkLow)
        }
        return
    }

    val priorityLabel = when (task.priority) {
        1 -> "p1"; 2 -> "p2"; 4 -> "p4"; else -> null
    }
    val showAi = task.parsedBy == ParsedBy.AI
    val recurrence = recurrenceLabel(task)
    if (priorityLabel == null && task.dueAt == null && !showAi && recurrence == null) return

    Spacer(Modifier.height(2.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (priorityLabel != null) {
            Text(
                text = priorityLabel,
                style = CruxType.Data,
                color = if (faded) InkLow else InkMid,
            )
        }
        if (priorityLabel != null && task.dueAt != null) {
            Spacer(Modifier.width(Dimens.Unit * 2))
        }
        task.dueAt?.let { due ->
            val zone = ZoneId.systemDefault()
            val now = Instant.now()
            val zdt = Instant.ofEpochMilli(due).atZone(zone)
            val label = buildString {
                append(zdt.format(MetaDateFmt).lowercase(Locale.ENGLISH))
                if (task.hasTime) {
                    append(" · ")
                    append(zdt.format(MetaTimeFmt).lowercase(Locale.ENGLISH))
                }
            }
            val overdue = isOverdue(task, now, zone)
            val soon = !overdue && task.status == TaskStatus.OPEN && due < now.toEpochMilli() + TWO_DAYS_MS
            val color = when {
                faded -> InkLow
                overdue -> Overdue
                soon -> Gold
                else -> InkMid
            }
            Text(text = label, style = CruxType.Data, color = color)
        }
        // the recurring task wears its ↻ quietly (mockup 02): "every mon ↻", "daily ↻".
        if (recurrence != null) {
            if (priorityLabel != null || task.dueAt != null) Spacer(Modifier.width(Dimens.Unit * 2))
            Text(text = recurrence, style = CruxType.Data, color = if (faded) InkLow else InkMid)
        }
        if (showAi) {
            if (priorityLabel != null || task.dueAt != null || recurrence != null) {
                Spacer(Modifier.width(Dimens.Unit * 2))
            }
            AiTag(faded = faded)
        }
    }
}

/** The quiet recurrence line (mockup .tmeta ↻): "daily ↻", "weekdays ↻", "every mon ↻", "monthly ↻". */
private fun recurrenceLabel(task: Task): String? = when (task.recurrenceType) {
    null -> null
    RecurrenceType.DAILY -> "daily ↻"
    RecurrenceType.WEEKDAYS -> "weekdays ↻"
    RecurrenceType.WEEKLY -> task.recurrenceWeekday?.let { wd ->
        "every ${DayOfWeek.of(wd).getDisplayName(TextStyle.SHORT, Locale.ENGLISH).lowercase(Locale.ENGLISH)} ↻"
    } ?: "weekly ↻"
    RecurrenceType.MONTHLY -> "monthly ↻"
}

/** The blush `ai` tag: a small pill marking a task the model touched. Fades with the row. */
@Composable
private fun AiTag(faded: Boolean) {
    Box(
        Modifier
            .clip(RoundedCornerShape(Dimens.RadiusPill))
            .background(if (faded) Color.Transparent else Blush)
            .padding(horizontal = Dimens.Unit * 2, vertical = 1.dp),
    ) {
        Text(text = "ai", style = CruxType.Data, color = if (faded) InkLow else Ember)
    }
}

private val MetaDateFmt = DateTimeFormatter.ofPattern("MMM d", Locale.ENGLISH)
private val MetaTimeFmt = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)
private const val TWO_DAYS_MS = 2L * 24 * 60 * 60 * 1000

private val StrikeThickness = 1.5.dp
