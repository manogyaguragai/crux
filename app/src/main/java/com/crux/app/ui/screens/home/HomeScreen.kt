package com.crux.app.ui.screens.home

import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.crux.app.ui.Copy
import com.crux.app.ui.TasksViewModel
import com.crux.app.ui.components.Omnibar
import com.crux.app.ui.components.SettingsGear
import com.crux.app.ui.components.TaskRow
import com.crux.app.ui.theme.CruxType
import com.crux.app.ui.theme.Dimens
import com.crux.app.ui.theme.Ember
import com.crux.app.ui.theme.InkLow
import com.crux.app.ui.theme.InkMid
import com.crux.app.ui.theme.LocalVoid
import com.crux.app.ui.theme.Motion

/**
 * Home: the omnibar riding low, the top 3 open tasks above it (data-model.md).
 * The nudge count and the meta line arrive in later phase 1 slices.
 */
@Composable
fun HomeScreen(vm: TasksViewModel, onOpenTask: (Long) -> Unit, onOpenSettings: () -> Unit) {
    val top by vm.top3.collectAsStateWithLifecycle()
    val completing by vm.completingIds.collectAsStateWithLifecycle()
    val overdue by vm.overdueCount.collectAsStateWithLifecycle()
    val knownProjects by vm.knownProjects.collectAsStateWithLifecycle()

    Column(
        Modifier
            .fillMaxSize()
            .background(LocalVoid.current)
            .padding(horizontal = Dimens.ScreenMargin),
    ) {
        Spacer(Modifier.height(Dimens.ScreenMargin))
        // top strip: the overdue nudge count on the left (live count -> ember), settings gear right.
        Box(Modifier.fillMaxWidth()) {
            if (overdue > 0) {
                Text(
                    text = "$overdue overdue",
                    style = CruxType.Data,
                    color = Ember,
                    modifier = Modifier.align(Alignment.CenterStart),
                )
            }
            SettingsGear(onClick = onOpenSettings, modifier = Modifier.align(Alignment.CenterEnd))
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (top.isEmpty()) {
                Text(
                    text = Copy.EMPTY_HOME,
                    style = CruxType.Passage,
                    color = InkMid,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.align(Alignment.Center),
                )
            } else {
                // a lazy list so a completed task fades out as it leaves the top 3 (animateItem),
                // instead of snapping away; Bottom keeps the rows riding low above the omnibar.
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Bottom,
                ) {
                    items(items = top, key = { it.id }) { task ->
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
        Omnibar(
            projects = knownProjects,
            onCapture = vm::capture,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(Dimens.GroupGap))
    }
}
