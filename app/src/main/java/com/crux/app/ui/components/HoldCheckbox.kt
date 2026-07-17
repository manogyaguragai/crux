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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.crux.app.ui.theme.InkLow
import com.crux.app.ui.theme.Motion

// The hold, 24 dp grid, per design-tokens.md. One pebble outline.
private const val HOLD_PATH =
    "M21.98 11.65 C22.03 13.77 20.41 16.44 18.79 17.86 C17.18 19.28 14.47 20.10 12.29 20.19 " +
        "C10.10 20.29 7.40 19.74 5.70 18.44 C4.01 17.13 2.22 14.51 2.12 12.35 " +
        "C2.02 10.18 3.52 6.98 5.11 5.43 C6.71 3.88 9.46 3.07 11.69 3.03 " +
        "C13.92 2.98 16.77 3.72 18.49 5.16 C20.20 6.60 21.93 9.53 21.98 11.65 Z"

private val HoldFill = Color(0xFF857B71) // warm grey fill on tick

/**
 * The hold (checkbox). Empty: a 1.6 dp InkLow stroke, no fill. Ticked: fills warm grey, stroke
 * gone, 120 ms ease-out, light haptic. No green, no checkmark. Touch target stays 48 dp.
 * Display only when [onToggle] is null (home shows state; ticking is wired per screen).
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
    val path = remember { PathParser().parsePathString(HOLD_PATH).toPath() }

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
        Canvas(Modifier.size(24.dp)) {
            val scale = size.width / 24f
            withTransform({ scale(scale, scale, pivot = Offset.Zero) }) {
                // stroke fades out as the fill comes in
                drawPath(
                    path = path,
                    color = InkLow,
                    style = Stroke(width = 1.6.dp.toPx() / scale),
                    alpha = 1f - progress,
                )
                // warm-grey fill fades in
                drawPath(path = path, color = HoldFill, alpha = progress)
            }
        }
    }
}
