package com.crux.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

/**
 * CruxTheme maps the tokens into a Material3 substrate. Dynamic color is OFF
 * (never call dynamic*ColorScheme); these token values are the whole scheme.
 * Dark only. The OLED "deep" background swap arrives with settings in phase 1.
 */
private val CruxColorScheme = darkColorScheme(
    background = Void,
    onBackground = InkHi,
    surface = Surface,
    onSurface = InkHi,
    surfaceVariant = Raised,
    onSurfaceVariant = InkMid,
    surfaceContainerHighest = Overlay,
    primary = Garnet,
    onPrimary = Cream,
    secondary = Ember,
    onSecondary = Cream,
    error = Overdue,
    onError = Cream,
    outline = InkLow,
    outlineVariant = HairlineStrong,
)

@Composable
fun CruxTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = CruxColorScheme,
        typography = CruxM3Typography,
        content = content,
    )
}
