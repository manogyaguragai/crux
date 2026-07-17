package com.crux.app.ui.screens.home

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.crux.app.ui.Copy
import com.crux.app.ui.TasksViewModel
import com.crux.app.ui.components.Omnibar
import com.crux.app.ui.components.SettingsGear
import com.crux.app.ui.components.TaskRow
import com.crux.app.ui.theme.CruxType
import com.crux.app.ui.theme.Dimens
import com.crux.app.ui.theme.Ember
import com.crux.app.ui.theme.InkMid
import com.crux.app.ui.theme.LocalVoid
import com.crux.app.ui.theme.Motion

/**
 * Home: the omnibar riding low, the open tasks above it (data-model.md). How many rows show is the
 * owner's setting (1..10, default 3); the list scrolls when it overflows.
 *
 * The omnibar is docked over the bottom of the list, not in the column flow, so it can slide away.
 * Scrolling down toward more tasks tucks it out of sight; scrolling back up pulls it home. The slide
 * is a single interruptible spring animating from the omnibar's live on-screen position (never from
 * the target), so a reversal mid-slide follows the finger instead of snapping — critically damped,
 * no overshoot, per the motion law. It is forced back the instant the keyboard opens (you are about
 * to type) or the list returns to the top, so capture is never a scroll away.
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(
    vm: TasksViewModel,
    onOpenTask: (Long) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenOverdue: () -> Unit,
) {
    val tasks by vm.homeTasks.collectAsStateWithLifecycle()
    val completing by vm.completingIds.collectAsStateWithLifecycle()
    val overdue by vm.overdueCount.collectAsStateWithLifecycle()
    val knownProjects by vm.knownProjects.collectAsStateWithLifecycle()

    val listState = rememberLazyListState()
    val density = LocalDensity.current
    val imeVisible = WindowInsets.isImeVisible

    // The omnibar's full docked height (shell + its chips + the bottom gap), measured live so the
    // slide distance and the list's bottom inset always match the real thing.
    var omnibarHeightPx by remember { mutableIntStateOf(0) }

    // Direction intent: a downward drag toward more content hides; an upward drag reveals.
    var revealed by remember { mutableStateOf(true) }
    val atTop by remember {
        derivedStateOf { listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0 }
    }
    val scrollWatcher = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                val dy = available.y
                if (dy < -SCROLL_INTENT_PX) revealed = false   // finger up, reading downward -> hide
                else if (dy > SCROLL_INTENT_PX) revealed = true // finger down, heading back up -> reveal
                return Offset.Zero
            }
        }
    }

    val show = revealed || imeVisible || atTop
    val hideTarget = if (show) 0f else omnibarHeightPx.toFloat()
    val hideOffset by animateFloatAsState(
        targetValue = hideTarget,
        // critically damped (no overshoot), response ~0.4s: chrome should settle, not bounce.
        animationSpec = spring(dampingRatio = 1f, stiffness = Spring.StiffnessMediumLow),
        label = "omnibarHide",
    )

    val omnibarInsetDp = with(density) { omnibarHeightPx.toDp() }

    Box(
        Modifier
            .fillMaxSize()
            .background(LocalVoid.current)
            .clipToBounds()
            .nestedScroll(scrollWatcher)
            .padding(horizontal = Dimens.ScreenMargin),
    ) {
        Column(Modifier.fillMaxSize()) {
            Spacer(Modifier.height(Dimens.ScreenMargin))
            // top strip: the overdue nudge count on the left (live count -> ember), settings gear right.
            Box(Modifier.fillMaxWidth()) {
                if (overdue > 0) {
                    // tap the nudge to open the overdue pile (the screen behind it).
                    val nudgeInteraction = remember { MutableInteractionSource() }
                    Text(
                        text = "$overdue overdue",
                        style = CruxType.Data,
                        color = Ember,
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .clip(RoundedCornerShape(Dimens.RadiusPill))
                            .clickable(interactionSource = nudgeInteraction, indication = null) { onOpenOverdue() }
                            .padding(vertical = Dimens.Unit, horizontal = Dimens.Unit),
                    )
                }
                SettingsGear(onClick = onOpenSettings, modifier = Modifier.align(Alignment.CenterEnd))
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                if (tasks.isEmpty()) {
                    Text(
                        text = Copy.EMPTY_HOME,
                        style = CruxType.Passage,
                        color = InkMid,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.align(Alignment.Center),
                    )
                } else {
                    // Bottom arrangement keeps a short list riding low above the omnibar (the shipped
                    // feel); a long list simply scrolls. The bottom inset clears the docked omnibar so
                    // the last row is never hidden behind it, and a completed row fades as it leaves.
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Bottom,
                        contentPadding = PaddingValues(bottom = omnibarInsetDp),
                    ) {
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
        // the omnibar, docked over the bottom; slides away on downward scroll, springs back on up.
        Omnibar(
            projects = knownProjects,
            onCapture = vm::capture,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .onSizeChanged { omnibarHeightPx = it.height }
                .graphicsLayer { translationY = hideOffset }
                .padding(bottom = Dimens.GroupGap),
        )
    }
}

/** Per-event scroll delta (px) that counts as a deliberate direction change, filtering micro-jitter. */
private const val SCROLL_INTENT_PX = 2f
