package com.crux.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import com.crux.app.domain.model.Task
import com.crux.app.domain.model.TaskStatus
import com.crux.app.ui.theme.CruxType
import com.crux.app.ui.theme.Dimens
import com.crux.app.ui.theme.InkHi
import com.crux.app.ui.theme.InkLow
import com.crux.app.ui.theme.InkMid
import com.crux.app.ui.theme.Motion

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

    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = Dimens.RowMinHeight),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HoldCheckbox(checked = struck, onToggle = onToggle)
        Spacer(Modifier.width(Dimens.Unit * 2))
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
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
        }
    }
}

private val StrikeThickness = 1.5.dp
