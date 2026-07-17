package com.crux.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.crux.app.ui.theme.InkLow
import com.crux.app.ui.theme.Motion

private val HoldFill = Color(0xFF857B71) // warm grey fill on tick (no green anywhere)

/**
 * The checkbox. Per the owner's decision (DECISIONS.log 2026-07-17) this is a plain circle,
 * overriding the kit's irregular "hold" pebble. Behaviour is unchanged: empty is a 1.6 dp InkLow
 * ring; ticked fills warm grey, ring gone, 120 ms ease-out, light haptic. Touch target stays 48 dp.
 * Display only when [onToggle] is null.
 */
@Composable
fun HoldCheckbox(
    checked: Boolean,
    onToggle: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    val interaction = remember { MutableInteractionSource() }
    val progress by animateFloatAsState(
        targetValue = if (checked) 1f else 0f,
        animationSpec = tween(durationMillis = Motion.TickMs, easing = Motion.EaseOut),
        label = "hold-fill",
    )

    Box(
        modifier = modifier
            .size(48.dp)
            .then(
                if (onToggle != null) {
                    Modifier.clickable(interactionSource = interaction, indication = null) {
                        if (!checked) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        onToggle()
                    }
                } else Modifier
            ),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(22.dp)) {
            val strokeWidth = 1.6.dp.toPx()
            val radius = size.minDimension / 2f
            // ring (open) fades out as the fill comes in
            drawCircle(
                color = InkLow,
                radius = radius - strokeWidth / 2f,
                style = Stroke(width = strokeWidth),
                alpha = 1f - progress,
            )
            // warm-grey fill (done) fades in
            drawCircle(color = HoldFill, radius = radius, alpha = progress)
        }
    }
}
