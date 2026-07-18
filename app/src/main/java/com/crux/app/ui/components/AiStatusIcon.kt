package com.crux.app.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.crux.app.ui.theme.Ember
import com.crux.app.ui.theme.Garnet
import com.crux.app.ui.theme.InkLow
import com.crux.app.ui.theme.LocalVoid
import com.crux.app.ui.theme.Oxblood

/** The three visual states of the ambient AI indicator. */
enum class AiPresence { OFF, IDLE, BUSY }

/** Provided once at the app shell so every header's [AiStatusIcon] reads the same live state. */
val LocalAiPresence = compositionLocalOf { AiPresence.OFF }

/**
 * The AI status mark: crux's bloom shrunk to a 24dp header glyph. A filled core inside a thin ring —
 * the same oxblood radiance that sits behind the omnibar, here as a small aura, so it reads as *this*
 * app's intelligence, not a stock sparkle. It breathes (the glow swelling and fading) only while a
 * call is in flight; sits steady when AI is on and idle; and turns grey with a diagonal slash when AI
 * is off or unkeyed. Purely decorative — it never intercepts touch, so it can sit beside the gear.
 */
@Composable
fun AiStatusIcon(modifier: Modifier = Modifier) {
    val presence = LocalAiPresence.current
    val void = LocalVoid.current
    // Always running, but only READ in the BUSY branch — so idle/off states never redraw per frame.
    val breath by rememberInfiniteTransition(label = "aiBreath").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1150, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "breath",
    )

    Canvas(modifier.size(24.dp)) {
        val c = center
        val u = size.minDimension / 24f
        val ringR = 7.2f * u
        val coreR = 2.6f * u
        val strokeW = 1.7f * u

        when (presence) {
            AiPresence.OFF -> {
                drawCircle(color = InkLow, radius = ringR, style = Stroke(width = strokeW))
                drawCircle(color = InkLow, radius = coreR)
                // the slash: a void-colored underlay first, so the ink line reads as a cut, not an overlay.
                val d = ringR + strokeW
                val a = Offset(c.x - d * 0.72f, c.y + d * 0.72f)
                val b = Offset(c.x + d * 0.72f, c.y - d * 0.72f)
                drawLine(void, a, b, strokeWidth = strokeW * 2.4f, cap = StrokeCap.Round)
                drawLine(InkLow, a, b, strokeWidth = strokeW, cap = StrokeCap.Round)
            }
            else -> {
                if (presence == AiPresence.BUSY) {
                    val glowAlpha = 0.20f + 0.55f * breath
                    val glowR = (9f + 3.5f * breath) * u
                    drawCircle(
                        brush = Brush.radialGradient(
                            0f to Garnet.copy(alpha = glowAlpha),
                            0.5f to Oxblood.copy(alpha = glowAlpha * 0.5f),
                            1f to Color.Transparent,
                            center = c,
                            radius = glowR,
                        ),
                        radius = glowR,
                        center = c,
                    )
                    drawCircle(color = Ember.copy(alpha = 0.65f + 0.35f * breath), radius = ringR, style = Stroke(width = strokeW))
                } else {
                    drawCircle(color = Ember.copy(alpha = 0.9f), radius = ringR, style = Stroke(width = strokeW))
                }
                drawCircle(color = Ember, radius = coreR)
            }
        }
    }
}
