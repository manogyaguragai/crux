package com.crux.app.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.unit.dp
import com.crux.app.ui.theme.Garnet
import com.crux.app.ui.theme.LocalVoid
import com.crux.app.ui.theme.Oxblood

/**
 * The loading mark (02-design/logo/crux-loader.html): the three stones assembling. Bottom to top,
 * 240 ms each, 140 ms apart, ease cubic-bezier(.23,1,.32,1); the garnet stone's landing compresses
 * the stack to scaleY .97 and recovers, and a garnet bloom breathes behind. One 2.6 s loop.
 *
 * Drawn (never a GIF): three animated pebble offsets + one settle scale + one bloom breath. All timing
 * is expressed as milliseconds on a 2600 ms keyframe track, matching the HTML spec's percentages.
 */
@Composable
fun CruxLoader(modifier: Modifier = Modifier) {
    val t = rememberInfiniteTransition(label = "loader")
    val period = 2600

    // land ease = the same curve the omnibar toggle uses (cubic-bezier .23,1,.32,1).
    val ease = com.crux.app.ui.theme.Motion.OmnibarToggle

    // per-stone vertical offset (in the 48-unit view space): held above, then dropped to rest.
    val bottomY by t.animateFloat(0f, 0f, infiniteRepeatable(keyframes {
        durationMillis = period
        -12f at 0 using ease
        0f at 239
    }), label = "bY")
    val middleY by t.animateFloat(0f, 0f, infiniteRepeatable(keyframes {
        durationMillis = period
        -12f at 0
        -12f at 140 using ease
        0f at 380
    }), label = "mY")
    val topY by t.animateFloat(0f, 0f, infiniteRepeatable(keyframes {
        durationMillis = period
        -14f at 0
        -14f at 281 using ease
        0f at 520
    }), label = "tY")

    // per-stone opacity: fades in on landing, holds, then fades the whole stack out before the loop.
    val bottomA by t.animateFloat(0f, 0f, infiniteRepeatable(keyframes {
        durationMillis = period
        0f at 0; 1f at 78; 1f at 2496; 0f at 2600
    }), label = "bA")
    val middleA by t.animateFloat(0f, 0f, infiniteRepeatable(keyframes {
        durationMillis = period
        0f at 0; 0f at 140; 1f at 218; 1f at 2496; 0f at 2600
    }), label = "mA")
    val topA by t.animateFloat(0f, 0f, infiniteRepeatable(keyframes {
        durationMillis = period
        0f at 0; 0f at 281; 1f at 359; 1f at 2496; 0f at 2600
    }), label = "tA")

    // the settle: on the garnet landing the stack compresses to .97 (90 ms) and recovers (140 ms).
    val settle by t.animateFloat(1f, 1f, infiniteRepeatable(keyframes {
        durationMillis = period
        1f at 520 using ease
        0.97f at 611 using ease
        1f at 749
    }), label = "settle")

    // the bloom breath behind the stack (5 s ease-in-out, .86 → .94), driven as a separate loop.
    val bloom by t.animateFloat(0.86f, 0.94f, infiniteRepeatable(
        animation = tween(2500), repeatMode = RepeatMode.Reverse,
    ), label = "bloom")

    Canvas(modifier) {
        val u = size.minDimension / 48f // 48-unit view space → px
        fun px(v: Float) = v * u

        // bloom: radial garnet glow anchored low (50% 86%), matching the mockup's gradient stops.
        drawRect(
            brush = Brush.radialGradient(
                colorStops = arrayOf(
                    0f to Oxblood.copy(alpha = 0.50f),
                    0.5f to Oxblood.copy(alpha = 0.14f),
                    1f to Color.Transparent,
                ),
                center = Offset(size.width * 0.5f, size.height * 0.86f),
                radius = size.minDimension * 0.72f,
            ),
            alpha = bloom,
        )

        // the stack, compressed about its bottom edge during the settle.
        scale(scaleX = 1f, scaleY = settle, pivot = Offset(px(24f), px(39.7f))) {
            // bottom pebble (#574E47), widest.
            drawOval(
                color = Color(0xFF574E47).copy(alpha = bottomA),
                topLeft = Offset(px(8.9f), px(29.5f + bottomY)),
                size = Size(px(31f), px(10.2f)),
            )
            // middle pebble (#857B71).
            drawOval(
                color = Color(0xFF857B71).copy(alpha = middleA),
                topLeft = Offset(px(12.5f), px(18.4f + middleY)),
                size = Size(px(22f), px(9.2f)),
            )
            // garnet pebble, the crown.
            drawOval(
                color = Garnet.copy(alpha = topA),
                topLeft = Offset(px(16.4f), px(8.55f + topY)),
                size = Size(px(15.7f), px(7.8f)),
            )
        }
    }
}

/**
 * The cold-start splash: the loader centred on the void ground. Shown briefly on launch and faded out
 * once the app is ready (the "show only for waits over 400 ms" rule — a launch qualifies).
 */
@Composable
fun CruxSplash(modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxSize()
            .background(LocalVoid.current),
        contentAlignment = Alignment.Center,
    ) {
        CruxLoader(Modifier.size(128.dp))
    }
}
