package com.crux.app.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * The palette. Source of truth per 02-design/design-tokens.md.
 * Composables never hardcode a hex; they reference these names.
 * Dynamic color is OFF everywhere; these values are the whole scheme.
 */

// Surfaces ("the valley")
val Void = Color(0xFF141110)     // base background
val Deep = Color(0xFF0B0A09)     // OLED "deep" mode background (settings toggle, phase 1)
val Surface = Color(0xFF1D1917)  // cards, rows
val Raised = Color(0xFF262120)   // inputs, sheets, the omnibar shell
val Overlay = Color(0xFF2E2825)  // menus, dialogs

// Ink and ember
val InkHi = Color(0xFFEFE9E2)    // primary text
val InkMid = Color(0xFFA79E94)   // secondary text
val InkLow = Color(0xFF6F675F)   // tertiary text, p3 chip text, inactive tab
val Cream = Color(0xFFFBEFE9)    // text on garnet fills
val Oxblood = Color(0xFF6E2430)  // bloom base, deep fills
val Garnet = Color(0xFFB93A46)   // actions, active-tab dash, p1 fill
val Ember = Color(0xFFD6555C)    // live counts, active tab, pressed/hover
val Blush = Color(0x24B93A46)    // rgba(185,58,70,0.14): tint on ai-inferred chips
val Overdue = Color(0xFFE5484D)  // nudge dot and overdue date text only, never buttons
val Gold = Color(0xFFC99A5B)     // due-soon date text, sparingly

// Hairlines: InkHi at 8% default, 15% emphasis. 1 px, never drop shadows.
val Hairline = Color(0x14EFE9E2)        // InkHi @ 8%
val HairlineStrong = Color(0x26EFE9E2)  // InkHi @ 15%
