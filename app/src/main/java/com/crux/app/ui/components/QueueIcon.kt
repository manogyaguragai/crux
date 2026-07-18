package com.crux.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.crux.app.ui.theme.Cream
import com.crux.app.ui.theme.CruxType
import com.crux.app.ui.theme.Ember
import com.crux.app.ui.theme.Garnet
import com.crux.app.ui.theme.InkLow
import com.crux.app.ui.theme.Overdue

/** What the queue icon needs: how many items are active, whether any failed, and how to open the list. */
data class QueueBar(val activeCount: Int, val hasFailed: Boolean, val onOpen: () -> Unit)

val LocalQueueBar = compositionLocalOf { QueueBar(0, false) {} }

/**
 * The capture-queue mark: a small stack of cards (queued lines waiting their turn), with a garnet count
 * badge when anything is in flight. Ember while work is pending, a red dot when the last drain left a
 * failure, otherwise a quiet grey. Tapping it opens the queue list.
 */
@Composable
fun QueueIcon(modifier: Modifier = Modifier) {
    val bar = LocalQueueBar.current
    val interaction = remember { MutableInteractionSource() }
    val tint = when {
        bar.activeCount > 0 -> Ember
        bar.hasFailed -> Overdue
        else -> InkLow
    }
    Box(
        modifier
            .size(24.dp)
            .clickable(interactionSource = interaction, indication = null, onClick = bar.onOpen),
    ) {
        Canvas(Modifier.size(24.dp)) {
            val u = size.minDimension / 24f
            val w = 11f * u
            val h = 8.5f * u
            val off = 2.2f * u
            val sw = 1.6f * u
            val corner = CornerRadius(2f * u, 2f * u)
            // back card (dimmer), then front card, offset to read as a stack
            drawRoundRect(
                color = tint.copy(alpha = 0.45f),
                topLeft = Offset(center.x - w / 2 - off, center.y - h / 2 - off),
                size = Size(w, h),
                cornerRadius = corner,
                style = Stroke(width = sw),
            )
            drawRoundRect(
                color = tint,
                topLeft = Offset(center.x - w / 2 + off, center.y - h / 2 + off),
                size = Size(w, h),
                cornerRadius = corner,
                style = Stroke(width = sw),
            )
        }
        if (bar.activeCount > 0) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .size(15.dp)
                    .clip(CircleShape)
                    .background(Garnet),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (bar.activeCount > 9) "9+" else bar.activeCount.toString(),
                    style = CruxType.Eyebrow.copy(fontSize = 9.sp),
                    color = Cream,
                )
            }
        }
    }
}
