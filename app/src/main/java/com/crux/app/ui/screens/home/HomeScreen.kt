package com.crux.app.ui.screens.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.crux.app.ui.Copy
import com.crux.app.ui.TasksViewModel
import com.crux.app.ui.components.AiStatusIcon
import com.crux.app.ui.components.Omnibar
import com.crux.app.ui.components.QueueIcon
import com.crux.app.ui.components.SettingsGear
import com.crux.app.ui.components.TaskRow
import com.crux.app.ui.theme.CruxType
import com.crux.app.ui.theme.Dimens
import com.crux.app.ui.theme.HairlineStrong
import com.crux.app.ui.theme.InkHi
import com.crux.app.ui.theme.InkLow
import com.crux.app.ui.theme.InkMid
import com.crux.app.ui.theme.LocalVoid
import com.crux.app.ui.theme.Motion
import com.crux.app.ui.theme.Overdue
import com.crux.app.ui.theme.Raised
import com.crux.app.ui.theme.SessionMotion
import com.crux.app.ui.theme.cascadeIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.PI
import kotlin.math.sin

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
    // the top-3 cascade downhill on the first home open of the session (once per session, per list).
    val cascade = remember { SessionMotion.claim("home-top3") }
    val todayLabel = remember {
        LocalDate.now().format(DateTimeFormatter.ofPattern("EEE · MMM d", Locale.ENGLISH))
            .lowercase(Locale.ENGLISH)
    }

    // The "skyrocket into the queue" animation: on submit, a chip of the line flies from the omnibar up
    // to the queue icon along a gentle arc, shrinking + fading, then the icon pops. Positions are in
    // root coords; the flyer overlay lives in this screen's Box, so we subtract the Box's own origin.
    val scope = rememberCoroutineScope()
    var rootOffset by remember { mutableStateOf(Offset.Zero) }
    var queueIconCenter by remember { mutableStateOf(Offset.Zero) }
    var omnibarAnchor by remember { mutableStateOf(Offset.Zero) }
    val flyers = remember { mutableStateListOf<Flyer>() }
    val iconPop = remember { Animatable(1f) }
    var flyerSeq by remember { mutableStateOf(0L) }
    fun launchFly(text: String) {
        if (queueIconCenter == Offset.Zero || omnibarAnchor == Offset.Zero) return
        val flyer = Flyer(flyerSeq++, text.trim(), Animatable(0f))
        flyers.add(flyer)
        scope.launch {
            flyer.progress.animateTo(1f, tween(520, easing = FastOutSlowInEasing))
            flyers.remove(flyer)
            iconPop.snapTo(1f)
            iconPop.animateTo(1.35f, tween(90))
            iconPop.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMedium))
        }
    }

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
            .onGloballyPositioned { rootOffset = it.positionInRoot() }
            .background(LocalVoid.current)
            .clipToBounds()
            .nestedScroll(scrollWatcher)
            .padding(horizontal = Dimens.ScreenMargin),
    ) {
        Column(Modifier.fillMaxSize()) {
            Spacer(Modifier.height(Dimens.ScreenMargin))
            // top strip: the day's date as a quiet mono eyebrow on the left; on the right the overdue
            // nudge (a bordered pill carrying the one red dot), then the queue / ai / settings icons.
            Box(Modifier.fillMaxWidth()) {
                Text(
                    text = todayLabel,
                    style = CruxType.Eyebrow,
                    color = InkLow,
                    modifier = Modifier.align(Alignment.CenterStart),
                )
                Row(
                    modifier = Modifier.align(Alignment.CenterEnd),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (overdue > 0) {
                        NudgePill(count = overdue, onClick = onOpenOverdue)
                        Spacer(Modifier.width(Dimens.Unit * 2))
                    }
                    Box(
                        Modifier
                            .onGloballyPositioned { queueIconCenter = it.boundsInRoot().center }
                            .graphicsLayer { scaleX = iconPop.value; scaleY = iconPop.value },
                    ) { QueueIcon() }
                    Spacer(Modifier.width(Dimens.Unit * 2))
                    AiStatusIcon()
                    Spacer(Modifier.width(Dimens.Unit * 2))
                    SettingsGear(onClick = onOpenSettings)
                }
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
                        // the label rides just above the low list (mockup "top of the stack").
                        item(key = "top3-eyebrow") {
                            Text(
                                text = "top of the stack",
                                style = CruxType.Eyebrow,
                                color = InkLow,
                                modifier = Modifier.padding(bottom = Dimens.Unit * 2),
                            )
                        }
                        itemsIndexed(items = tasks, key = { _, t -> t.id }) { index, task ->
                            TaskRow(
                                task = task,
                                completing = task.id in completing,
                                onToggle = { vm.complete(task) },
                                onOpen = { onOpenTask(task.id) },
                                modifier = Modifier
                                    .animateItem(
                                        fadeOutSpec = tween(Motion.VanishMs, easing = Motion.EaseOut),
                                        placementSpec = spring(
                                            dampingRatio = Motion.ReorderDamping,
                                            visibilityThreshold = IntOffset.VisibilityThreshold,
                                        ),
                                    )
                                    .cascadeIn(index, cascade),
                            )
                        }
                    }
                }
            }
        }
        // the omnibar, docked over the bottom; slides away on downward scroll, springs back on up.
        Omnibar(
            projects = knownProjects,
            onCapture = { text, dismissed, source -> vm.capture(text, dismissed, source); launchFly(text) },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .onGloballyPositioned { omnibarAnchor = it.boundsInRoot().let { r -> Offset(r.center.x, r.top) } }
                .onSizeChanged { omnibarHeightPx = it.height }
                .graphicsLayer { translationY = hideOffset }
                .padding(bottom = Dimens.GroupGap),
        )

        // the flying chips: each animates from the omnibar up into the queue icon
        flyers.forEach { flyer ->
            val p = flyer.progress.value
            val cx = omnibarAnchor.x + (queueIconCenter.x - omnibarAnchor.x) * p
            val baseY = omnibarAnchor.y + (queueIconCenter.y - omnibarAnchor.y) * p
            val arcY = baseY - (90.0 * sin(p * PI)).toFloat()
            val scale = 1f - 0.72f * p
            Box(
                Modifier
                    .align(Alignment.TopStart)
                    .graphicsLayer {
                        translationX = cx - rootOffset.x
                        translationY = arcY - rootOffset.y
                        scaleX = scale
                        scaleY = scale
                        alpha = 1f - p * p
                        transformOrigin = TransformOrigin(0f, 0f)
                    }
                    .clip(RoundedCornerShape(Dimens.RadiusPill))
                    .background(Raised)
                    .padding(horizontal = Dimens.Unit * 3, vertical = Dimens.Unit * 1.5f),
            ) {
                Text(
                    text = flyer.text.take(24),
                    style = CruxType.Data,
                    color = InkHi,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * The overdue nudge: a bordered pill carrying the one red dot and the waiting count, in the quietest
 * possible voice (mockup .nudge). Tapping it opens the overdue pile behind it.
 */
@Composable
private fun NudgePill(count: Int, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Row(
        Modifier
            .clip(RoundedCornerShape(Dimens.RadiusPill))
            .border(Dimens.HairlineWidth, HairlineStrong, RoundedCornerShape(Dimens.RadiusPill))
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = Dimens.Unit * 2, vertical = Dimens.Unit),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(Dimens.Unit).clip(CircleShape).background(Overdue))
        Spacer(Modifier.width(Dimens.Unit * 1.5f))
        Text(text = "$count waiting", style = CruxType.Data, color = InkMid)
    }
}

/** One in-flight "skyrocket" chip: its [progress] 0→1 drives the arc from omnibar to queue icon. */
private class Flyer(val id: Long, val text: String, val progress: Animatable<Float, *>)

/** Per-event scroll delta (px) that counts as a deliberate direction change, filtering micro-jitter. */
private const val SCROLL_INTENT_PX = 2f
