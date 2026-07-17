package com.crux.app.ui.screens.stack

import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.crux.app.ui.Copy
import com.crux.app.ui.TasksViewModel
import com.crux.app.ui.components.TaskRow
import com.crux.app.ui.theme.CruxType
import com.crux.app.ui.theme.Dimens
import com.crux.app.ui.theme.Garnet
import com.crux.app.ui.theme.InkHi
import com.crux.app.ui.theme.InkMid
import com.crux.app.ui.theme.Motion
import com.crux.app.ui.theme.Void

/**
 * The stack: every open and done task, grouped by project rank with the inbox last (data-model.md).
 * Each group carries a header and a short garnet "downhill rule"; done rows sink faded to the bottom
 * of their own group. Empty groups are omitted, so a project with no tasks shows no header.
 */
@Composable
fun StackScreen(vm: TasksViewModel, onOpenTask: (Long) -> Unit) {
    val groups by vm.groupedStack.collectAsStateWithLifecycle()
    val completing by vm.completingIds.collectAsStateWithLifecycle()

    Column(
        Modifier
            .fillMaxSize()
            .background(Void)
            .padding(horizontal = Dimens.ScreenMargin),
    ) {
        Spacer(Modifier.height(Dimens.ScreenMargin))
        Text(text = Copy.TAB_STACK, style = CruxType.Display, color = InkHi)
        Spacer(Modifier.height(Dimens.Unit * 2))

        if (groups.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth()) {
                Text(
                    text = Copy.EMPTY_STACK,
                    style = CruxType.Passage,
                    color = InkMid,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.align(Alignment.Center),
                )
            }
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
}

/** A group header: the project name (or "inbox"), underlined by the garnet-to-transparent rule. */
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
