package com.crux.app.ui.screens.overdue

import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.animation.core.VisibilityThreshold
import com.crux.app.ui.Copy
import com.crux.app.ui.TasksViewModel
import com.crux.app.ui.components.CruxIcons
import com.crux.app.ui.components.TaskRow
import com.crux.app.ui.theme.CruxType
import com.crux.app.ui.theme.Dimens
import com.crux.app.ui.theme.InkHi
import com.crux.app.ui.theme.InkMid
import com.crux.app.ui.theme.LocalVoid
import com.crux.app.ui.theme.Motion

/**
 * The overdue pile (ui-ux-decisions.md / phases.md): a pushed screen reached by tapping the home
 * nudge count. Just the open tasks that are genuinely past due, most overdue first, each still
 * tickable in place (completing one drops it from the pile). Empties out to a calm clear-trail line.
 */
@Composable
fun OverdueScreen(vm: TasksViewModel, onBack: () -> Unit, onOpenTask: (Long) -> Unit) {
    val tasks by vm.overdueTasks.collectAsStateWithLifecycle()
    val completing by vm.completingIds.collectAsStateWithLifecycle()

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
        Text(Copy.OVERDUE_TITLE, style = CruxType.Display, color = InkHi)
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
            LazyColumn(Modifier.fillMaxSize()) {
                items(items = tasks, key = { it.id }) { task ->
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
