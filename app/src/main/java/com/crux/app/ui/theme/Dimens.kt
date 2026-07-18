package com.crux.app.ui.theme

import androidx.compose.ui.unit.dp

/**
 * Spacing, radii, hairlines, per 02-design/design-tokens.md.
 * Nested corners stay concentric (inner radius = outer minus inset).
 * Emptiness is the feature; do not densify.
 */
object Dimens {
    val Unit = 4.dp             // base grid
    val ScreenMargin = 20.dp
    val RowMinHeight = 60.dp    // a task row never compresses below this
    val GroupGap = 28.dp        // between project groups
    val RadiusShell = 26.dp     // docked shells (omnibar, tab bar)
    val RadiusCard = 19.dp      // cards and inputs
    val RadiusPill = 999.dp     // chips and buttons
    val HairlineWidth = 1.dp    // 1 px hairline, never a drop shadow
    val RadiusRankChip = 7.dp   // projects rank badge (mockup .rchip): a soft rect, not a pill
    val DownhillRuleHeight = 2.dp   // garnet-to-transparent, under group headers
    val DownhillRuleLength = 72.dp
    val TabBarHeight = 64.dp    // crux-screens-v3.html .tabbar
    val ActiveTabDashWidth = 24.dp   // the active-tab dash (one of the four red places)
    val ActiveTabDashHeight = 3.dp
}
