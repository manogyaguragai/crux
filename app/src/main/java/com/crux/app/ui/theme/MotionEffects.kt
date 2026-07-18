package com.crux.app.ui.theme

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * Motion effects that the tokens in [Motion] drive. Kept next to the tokens so the two loops and the
 * cascade all read against the same law: nothing over 300 ms, exits faster than entries, never ease-in,
 * transform and opacity only.
 *
 * `prefers-reduced-motion` on Android reads through the system animator scale: when the user has turned
 * animations off (Settings > Developer options / Accessibility, animator duration scale 0), we keep the
 * fades but kill travel and both bloom loops, per the motion law.
 */
@Composable
fun rememberReducedMotion(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f,
        ) == 0f
    }
}

/**
 * The list-first-open cascade: rows drop into place downhill, top row first, each a stagger later
 * ([Motion.CascadeStaggerMs]), capped at [Motion.CascadeMaxRows]. Plays once per session per list, so a
 * tab you return to does not re-cascade; [SessionMotion.claim] holds that flag for the process lifetime.
 *
 * [index] is the row's position in the flat visual order. [play] is the list's session claim. With
 * reduced motion, or past the row cap, or after the claim is spent, the row simply renders in place.
 */
fun Modifier.cascadeIn(index: Int, play: Boolean): Modifier = composed {
    val reduced = rememberReducedMotion()
    val active = play && index < Motion.CascadeMaxRows && !reduced
    val drop = with(LocalDensity.current) { CascadeDropDp.dp.toPx() }
    val anim = remember { Animatable(if (active) 0f else 1f) }
    LaunchedEffect(Unit) {
        if (active) {
            delay(index * Motion.CascadeStaggerMs.toLong())
            anim.animateTo(1f, tween(Motion.CascadeRowMs, easing = Motion.EaseOut))
        }
    }
    graphicsLayer {
        alpha = anim.value
        // downhill: starts a touch high and settles, like a stone placed.
        translationY = -(1f - anim.value) * drop
    }
}

/** How far a cascading row drops into place (small, so it settles rather than travels). */
private const val CascadeDropDp = 8

/**
 * Process-lifetime record of which lists have already played their first-open cascade. "Once per
 * session" means once per app process: this clears when the process dies, never persisted.
 */
object SessionMotion {
    private val cascaded = mutableSetOf<String>()

    /** Returns true the first time a list key is seen this session, false every time after. */
    fun claim(listKey: String): Boolean = cascaded.add(listKey)
}
