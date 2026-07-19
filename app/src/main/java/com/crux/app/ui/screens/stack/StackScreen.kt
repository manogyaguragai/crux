package com.crux.app.ui.screens.stack

import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.crux.app.ui.Copy
import com.crux.app.util.CalendarLauncher
import com.crux.app.ui.TasksViewModel
import com.crux.app.ui.components.TabHeader
import com.crux.app.ui.components.TaskRow
import com.crux.app.domain.model.TaskStatus
import com.crux.app.domain.weekLabel
import com.crux.app.ui.theme.Blush
import com.crux.app.ui.theme.Cream
import com.crux.app.ui.theme.CruxType
import com.crux.app.ui.theme.Dimens
import com.crux.app.ui.theme.Ember
import com.crux.app.ui.theme.Garnet
import com.crux.app.ui.theme.Hairline
import com.crux.app.ui.theme.HairlineStrong
import com.crux.app.ui.theme.InkHi
import com.crux.app.ui.theme.InkLow
import com.crux.app.ui.theme.InkMid
import com.crux.app.ui.theme.LocalVoid
import com.crux.app.ui.theme.Motion
import com.crux.app.ui.theme.SessionMotion
import com.crux.app.ui.theme.cascadeIn
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private enum class StackMode { Stack, Week }

/**
 * The stack tab, in two views (phases.md). "stack": every open and done task grouped by project rank
 * with the inbox last (done rows sink faded within their group). "week": the next 7 days, open tasks
 * bucketed by day, empty days omitted. A small toggle in the header switches between them; both share
 * the same group-header language (name + garnet downhill rule) and the same tickable rows.
 */
@Composable
fun StackScreen(vm: TasksViewModel, onOpenTask: (Long) -> Unit, onOpenSettings: () -> Unit) {
    val groups by vm.groupedStack.collectAsStateWithLifecycle()
    val week by vm.weekDays.collectAsStateWithLifecycle()
    val completing by vm.completingIds.collectAsStateWithLifecycle()
    var mode by remember { mutableStateOf(StackMode.Stack) }

    Column(
        Modifier
            .fillMaxSize()
            .background(LocalVoid.current)
            .padding(horizontal = Dimens.ScreenMargin),
    ) {
        Spacer(Modifier.height(Dimens.ScreenMargin))
        TabHeader(
            title = Copy.TAB_STACK,
            onOpenSettings = onOpenSettings,
            eyebrow = if (mode == StackMode.Stack) Copy.STACK_EYEBROW else weekRangeLabel(week),
            subline = { StackSubline(mode, groups, week) },
            trailing = { ViewToggle(mode = mode, onChange = { mode = it }) },
        )
        Spacer(Modifier.height(Dimens.Unit * 2))

        when (mode) {
            StackMode.Stack -> StackList(groups, completing, onOpenTask, vm)
            StackMode.Week -> WeekList(week, completing, onOpenTask, vm)
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.ColumnScope.StackList(
    groups: List<com.crux.app.domain.StackGroup>,
    completing: Set<Long>,
    onOpenTask: (Long) -> Unit,
    vm: TasksViewModel,
) {
    if (groups.isEmpty()) {
        EmptyBody(Copy.EMPTY_STACK)
    } else {
        // the whole list cascades downhill on the first stack open of the session; [flat] is the
        // running row index across groups so the stagger and the 6-row cap span the visible list.
        val cascade = remember { SessionMotion.claim("stack-list") }
        LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
            var flat = 0
            groups.forEachIndexed { groupIndex, group ->
                item(key = "header-${group.projectId}") {
                    GroupHeader(
                        title = if (group.projectId != null) "# ${group.title}" else group.title,
                        first = groupIndex == 0,
                        trailing = group.rank?.let { "R$it" },
                        modifier = Modifier.animateItem(
                            placementSpec = spring(
                                dampingRatio = Motion.ReorderDamping,
                                visibilityThreshold = IntOffset.VisibilityThreshold,
                            ),
                        ),
                    )
                }
                val base = flat
                itemsIndexed(items = group.tasks, key = { _, t -> t.id }) { i, task ->
                    TaskRow(
                        task = task,
                        completing = task.id in completing,
                        onToggle = { vm.complete(task) },
                        onOpen = { onOpenTask(task.id) },
                        // the sink: once a completion lands, the row glides to the bottom of its
                        // group on a soft-landing spring; fadeOut so a swept row dissolves.
                        modifier = Modifier
                            .animateItem(
                                fadeOutSpec = tween(Motion.VanishMs, easing = Motion.EaseOut),
                                placementSpec = spring(
                                    dampingRatio = Motion.ReorderDamping,
                                    visibilityThreshold = IntOffset.VisibilityThreshold,
                                ),
                            )
                            .cascadeIn(base + i, cascade),
                    )
                }
                flat += group.tasks.size
            }
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.ColumnScope.WeekList(
    week: List<com.crux.app.domain.WeekDay>,
    completing: Set<Long>,
    onOpenTask: (Long) -> Unit,
    vm: TasksViewModel,
) {
    val context = LocalContext.current
    // the day-strip drives the list: tapping a cell filters below to just that day. defaults to today.
    var selectedDate by remember { mutableStateOf(LocalDate.now(ZoneId.systemDefault())) }
    if (week.isEmpty()) {
        EmptyBody(Copy.EMPTY_WEEK)
    } else {
        val cascade = remember { SessionMotion.claim("stack-week") }
        // the 7-cell day-strip always shows the coming week, whether or not those days hold tasks;
        // it stays fully visible so the user can switch days — only the list below filters.
        DayStrip(
            loaded = week.map { it.date }.toSet(),
            selected = selectedDate,
            onSelect = { selectedDate = it },
        )
        // weekDays omits empty days, so the selected day may be absent from [week].
        val day = week.firstOrNull { it.date == selectedDate }
        LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
            item(key = "day-header-$selectedDate") {
                GroupHeader(
                    title = day?.label ?: weekLabel(selectedDate, LocalDate.now(ZoneId.systemDefault())),
                    first = true,
                    trailing = "${day?.tasks?.size ?: 0}",
                    modifier = Modifier.animateItem(
                        placementSpec = spring(
                            dampingRatio = Motion.ReorderDamping,
                            visibilityThreshold = IntOffset.VisibilityThreshold,
                        ),
                    ),
                )
            }
            if (day == null) {
                item(key = "day-empty-$selectedDate") {
                    Text(
                        text = Copy.WEEK_DAY_EMPTY,
                        style = CruxType.Passage,
                        color = InkMid,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = Dimens.Unit * 3),
                    )
                }
            } else {
                itemsIndexed(items = day.tasks, key = { _, t -> t.id }) { i, task ->
                    TaskRow(
                        task = task,
                        completing = task.id in completing,
                        onToggle = { vm.complete(task) },
                        onOpen = { onOpenTask(task.id) },
                        // a synced event's gold glyph opens that event in google calendar.
                        onEventClick = task.calendarEventId?.let { evId ->
                            { CalendarLauncher.openEvent(context, evId) }
                        },
                        modifier = Modifier
                            .animateItem(
                                fadeOutSpec = tween(Motion.VanishMs, easing = Motion.EaseOut),
                                placementSpec = spring(
                                    dampingRatio = Motion.ReorderDamping,
                                    visibilityThreshold = IntOffset.VisibilityThreshold,
                                ),
                            )
                            .cascadeIn(i, cascade),
                    )
                }
            }
        }
        // gcal handoff (mockup .gcal): the full-month jump-off, opening google calendar at the day in view.
        val handoffInteraction = remember { MutableInteractionSource() }
        Text(
            text = Copy.WEEK_HANDOFF,
            style = CruxType.Data,
            color = InkMid,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(interactionSource = handoffInteraction, indication = null) {
                    CalendarLauncher.openDay(context, selectedDate)
                }
                .padding(vertical = Dimens.Unit * 3),
        )
    }
}

/**
 * The week day-strip (mockup .daystrip): seven equal cells for [today .. today+6]. Each shows the
 * weekday short lowercase over its day number, with a dot below when that date carries tasks. Today
 * is a solid garnet chip in cream; the rest are hairline-bordered ink-low.
 */
@Composable
private fun DayStrip(loaded: Set<LocalDate>, selected: LocalDate, onSelect: (LocalDate) -> Unit) {
    val today = LocalDate.now(ZoneId.systemDefault())
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = Dimens.Unit * 2),
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(Dimens.Unit * 1.5f),
    ) {
        for (offset in 0..6) {
            val date = today.plusDays(offset.toLong())
            val isToday = offset == 0
            val isSelected = date == selected
            val hasTasks = date in loaded
            val weekday = date.format(DayStripFmt).lowercase(Locale.ENGLISH)
            val cellInteraction = remember(date) { MutableInteractionSource() }
            // selection wins: the picked day is the solid garnet chip. today, when not selected,
            // keeps a quiet garnet-hairline marker; the rest sit in plain hairline.
            val cellBackground: Modifier = when {
                isSelected -> Modifier.background(Garnet)
                isToday -> Modifier.border(
                    Dimens.HairlineWidth,
                    Garnet,
                    RoundedCornerShape(Dimens.Unit * 3.25f),
                )
                else -> Modifier.border(
                    Dimens.HairlineWidth,
                    Hairline,
                    RoundedCornerShape(Dimens.Unit * 3.25f),
                )
            }
            val textColor = when {
                isSelected -> Cream
                isToday -> Garnet
                else -> InkLow
            }
            Column(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(Dimens.Unit * 3.25f))
                    .clickable(interactionSource = cellInteraction, indication = null) { onSelect(date) }
                    .then(cellBackground)
                    .padding(vertical = Dimens.Unit * 1.5f),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = weekday,
                    style = CruxType.Data,
                    color = textColor,
                )
                Text(
                    text = "${date.dayOfMonth}",
                    style = CruxType.Subhead,
                    color = textColor,
                )
                Spacer(Modifier.height(Dimens.Unit * 0.5f))
                Box(
                    Modifier
                        .height(4.dp)
                        .width(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(
                            if (hasTasks) {
                                if (isSelected) Cream else InkLow
                            } else {
                                Color.Transparent
                            },
                        ),
                )
            }
        }
    }
}

private val DayStripFmt = DateTimeFormatter.ofPattern("EEE", Locale.ENGLISH)

@Composable
private fun androidx.compose.foundation.layout.ColumnScope.EmptyBody(text: String) {
    Box(Modifier.weight(1f).fillMaxWidth()) {
        Text(
            text = text,
            style = CruxType.Passage,
            color = InkMid,
            textAlign = TextAlign.Center,
            modifier = Modifier.align(Alignment.Center),
        )
    }
}

/**
 * The stack/week segmented toggle: a hairline-bordered capsule, the active pill blush-tinted with
 * ember mono caps (mockup .viewseg), the inactive one quiet ink-low.
 */
@Composable
private fun ViewToggle(mode: StackMode, onChange: (StackMode) -> Unit) {
    Row(
        Modifier
            .clip(RoundedCornerShape(Dimens.RadiusPill))
            .border(Dimens.HairlineWidth, HairlineStrong, RoundedCornerShape(Dimens.RadiusPill)),
    ) {
        SegPill(Copy.STACK_VIEW_STACK, selected = mode == StackMode.Stack) { onChange(StackMode.Stack) }
        SegPill(Copy.STACK_VIEW_WEEK, selected = mode == StackMode.Week) { onChange(StackMode.Week) }
    }
}

@Composable
private fun SegPill(label: String, selected: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        Modifier
            .clip(RoundedCornerShape(Dimens.RadiusPill))
            .background(if (selected) Blush else Color.Transparent)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = Dimens.Unit * 3, vertical = Dimens.Unit * 1.5f),
    ) {
        Text(label.uppercase(), style = CruxType.Data, color = if (selected) Ember else InkLow)
    }
}

/**
 * A group header: the group name (project or day) in mono caps (mockup .ghead), underlined by the
 * garnet-to-transparent downhill rule.
 */
@Composable
private fun GroupHeader(
    title: String,
    first: Boolean,
    modifier: Modifier = Modifier,
    trailing: String? = null,
) {
    Column(modifier.fillMaxWidth()) {
        Spacer(Modifier.height(if (first) Dimens.Unit * 2 else Dimens.GroupGap))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = title.uppercase(), style = CruxType.Eyebrow, color = InkMid)
            if (trailing != null) {
                Text(text = trailing, style = CruxType.Data, color = InkLow)
            }
        }
        Spacer(Modifier.height(Dimens.Unit * 2))
        Box(
            Modifier
                .width(Dimens.DownhillRuleLength)
                .height(Dimens.DownhillRuleHeight)
                .background(Brush.horizontalGradient(listOf(Garnet, Color.Transparent))),
        )
        Spacer(Modifier.height(Dimens.Unit * 2))
    }
}

/** The header subline: live counts, the "hot" portion in ember (mockup .subline). Mode-dependent. */
@Composable
private fun StackSubline(
    mode: StackMode,
    groups: List<com.crux.app.domain.StackGroup>,
    week: List<com.crux.app.domain.WeekDay>,
) {
    Row {
        if (mode == StackMode.Stack) {
            val zone = ZoneId.systemDefault()
            val today = LocalDate.now(zone)
            val open = groups.sumOf { g -> g.tasks.count { it.status == TaskStatus.OPEN } }
            val forToday = groups.sumOf { g ->
                g.tasks.count { t ->
                    t.status == TaskStatus.OPEN && t.dueAt != null &&
                        Instant.ofEpochMilli(t.dueAt).atZone(zone).toLocalDate() == today
                }
            }
            Text("$open open", style = CruxType.Data, color = InkLow)
            if (forToday > 0) Text(" · $forToday for today", style = CruxType.Data, color = Ember)
        } else {
            val scheduled = week.sumOf { it.tasks.size }
            val events = week.sumOf { d -> d.tasks.count { it.hasTime } }
            Text("$scheduled scheduled", style = CruxType.Data, color = InkLow)
            if (events > 0) Text(" · $events events", style = CruxType.Data, color = Ember)
        }
    }
}

/** "jul 14 - 20" style range for the week eyebrow, from the first and last loaded days. */
private fun weekRangeLabel(week: List<com.crux.app.domain.WeekDay>): String {
    if (week.isEmpty()) return "this week"
    val start = week.first().date
    val end = week.last().date
    val startLabel = start.format(WeekRangeFmt).lowercase(Locale.ENGLISH)
    val endLabel = if (start.month == end.month) {
        end.dayOfMonth.toString()
    } else {
        end.format(WeekRangeFmt).lowercase(Locale.ENGLISH)
    }
    return "$startLabel - $endLabel"
}

private val WeekRangeFmt = DateTimeFormatter.ofPattern("MMM d", Locale.ENGLISH)
