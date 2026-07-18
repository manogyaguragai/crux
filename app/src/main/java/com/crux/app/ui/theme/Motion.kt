package com.crux.app.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing

/**
 * Motion, per 02-design/design-tokens.md.
 * "Stones being placed: immediate to start, soft to land, silent when repeated."
 *
 * Laws: nothing over 300 ms; exits faster than entries; never ease-in;
 * animate transform and opacity only. Exactly two loops exist, both the bloom.
 * These are the tokens; the animations that consume them arrive with their screens.
 *
 * Sanctioned exceptions to the 300 ms cap (each earns its length): the two bloom loops,
 * and the completion ceremony (StrikeMs) — a slow, savoured strike-through the owner asked
 * for so finishing a task feels earned (DECISIONS.log 2026-07-17). Its retract obeys the
 * "exits faster than entries" law.
 */
object Motion {
    // Durations (ms)
    const val TickMs = 120            // hold fills; ease-out
    const val StrikeMs = 750          // completion ceremony: strike-through draws + title fades; ease-out
    const val StrikeRetractMs = 240   // undo un-strikes; faster than the entry, per the law
    const val VanishMs = 300          // a completed row fades out as it leaves a list (home top-3, sweep)
    const val OmnibarToggleMs = 160   // add <-> search dash slide
    const val DetailOpenMs = 240      // shared-element rise from the row
    const val BloomIdleMs = 6000      // sanctioned loop 1: alpha drifts +/-4%
    const val BloomListenMs = 1600    // sanctioned loop 2: breathes with the mic
    const val CascadeStaggerMs = 40   // list first open, max 6 rows, once per session
    const val CascadeMaxRows = 6
    const val CascadeRowMs = 220      // each cascading row's own drop-and-fade (entry, under the 300 cap)

    // Easings. Never ease-in.
    val EaseOut: Easing = CubicBezierEasing(0f, 0f, 0.58f, 1f)
    val OmnibarToggle: Easing = CubicBezierEasing(0.23f, 1f, 0.32f, 1f)

    // Reorder / rank change: spring, damping 0.8, no overshoot past 1.
    const val ReorderDamping = 0.8f
}
