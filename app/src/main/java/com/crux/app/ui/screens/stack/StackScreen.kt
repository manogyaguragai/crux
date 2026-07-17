package com.crux.app.ui.screens.stack

import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.items
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.crux.app.ui.Copy
import com.crux.app.ui.TasksViewModel
import com.crux.app.ui.components.TabHeader
import com.crux.app.ui.components.TaskRow
import com.crux.app.ui.theme.CruxType
import com.crux.app.ui.theme.Dimens
import com.crux.app.ui.theme.Garnet
import com.crux.app.ui.theme.InkHi
import com.crux.app.ui.theme.InkMid
import com.crux.app.ui.theme.LocalVoid
import com.crux.app.ui.theme.Motion
import com.crux.app.ui.theme.Overlay
import com.crux.app.ui.theme.Surface

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
        TabHeader(title = Copy.TAB_STACK, onOpenSettings = onOpenSettings) {
            ViewToggle(mode = mode, onChange = { mode = it })
        }
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
        LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
            groups.forEachIndexed { groupIndex, group ->
                item(key = "header-${group.projectId}") {
                    GroupHeader(
                        title = group.title,
                        first = groupIndex == 0,
                        modifier = Modifier.animateItem(
                            placementSpec = spring(
                                dampingRatio = Motion.ReorderDamping,
                                visibilityThreshold = IntOffset.VisibilityThreshold,
                            ),
                        ),
                    )
                }
                items(items = group.tasks, key = { it.id }) { task ->
                    TaskRow(
                        task = task,
                        completing = task.id in completing,
                        onToggle = { vm.complete(task) },
                        onOpen = { onOpenTask(task.id) },
                        // the sink: once a completion lands, the row glides to the bottom of its
                        // group on a soft-landing spring; fadeOut so a swept row dissolves.
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
    if (week.isEmpty()) {
        EmptyBody(Copy.EMPTY_WEEK)
    } else {
        LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
            week.forEachIndexed { dayIndex, day ->
                item(key = "day-${day.date}") {
                    GroupHeader(
                        title = day.label,
                        first = dayIndex == 0,
                        modifier = Modifier.animateItem(
                            placementSpec = spring(
                                dampingRatio = Motion.ReorderDamping,
                                visibilityThreshold = IntOffset.VisibilityThreshold,
                            ),
                        ),
                    )
                }
                items(items = day.tasks, key = { it.id }) { task ->
                    TaskRow(
                        task = task,
                        completing = task.id in completing,
                        onToggle = { vm.complete(task) },
                        onOpen = { onOpenTask(task.id) },
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
        }
    }
}

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

/** The stack/week segmented toggle: two quiet pills, the active one filled (Overlay + InkHi). */
@Composable
private fun ViewToggle(mode: StackMode, onChange: (StackMode) -> Unit) {
    Row(
        Modifier
            .clip(RoundedCornerShape(Dimens.RadiusPill))
            .background(Surface)
            .padding(Dimens.Unit / 2),
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
            .background(if (selected) Overlay else Color.Transparent)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = Dimens.Unit * 3, vertical = Dimens.Unit * 1.5f),
    ) {
        Text(label, style = CruxType.Action, color = if (selected) InkHi else InkMid)
    }
}

/** A group header: the group name (project or day), underlined by the garnet-to-transparent rule. */
@Composable
private fun GroupHeader(title: String, first: Boolean, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth()) {
        Spacer(Modifier.height(if (first) Dimens.Unit * 2 else Dimens.GroupGap))
        Text(text = title, style = CruxType.Subhead, color = InkHi)
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
