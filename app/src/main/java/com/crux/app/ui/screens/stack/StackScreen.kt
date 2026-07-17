package com.crux.app.ui.screens.stack

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.ui.unit.IntOffset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.crux.app.ui.Copy
import com.crux.app.ui.TasksViewModel
import com.crux.app.ui.components.TaskRow
import com.crux.app.ui.theme.CruxType
import com.crux.app.ui.theme.Dimens
import com.crux.app.ui.theme.InkHi
import com.crux.app.ui.theme.InkMid
import com.crux.app.ui.theme.Motion
import com.crux.app.ui.theme.Void

/**
 * The stack: every open and done task. Phase 1 is a flat list in master-sort order (done rows
 * sink faded to the bottom). Grouping by project rank arrives with the projects slice.
 */
@Composable
fun StackScreen(vm: TasksViewModel, onOpenTask: (Long) -> Unit) {
    val tasks by vm.stack.collectAsStateWithLifecycle()
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

        if (tasks.isEmpty()) {
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
                items(items = tasks, key = { it.id }) { task ->
                    TaskRow(
                        task = task,
                        completing = task.id in completing,
                        onToggle = { vm.complete(task) },
                        onOpen = { onOpenTask(task.id) },
                        // the sink: once a completion lands, the row glides to the bottom on a
                        // soft-landing spring (damping 0.8, "immediate to start, soft to land").
                        // fadeOut so a swept row dissolves rather than snapping out.
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
