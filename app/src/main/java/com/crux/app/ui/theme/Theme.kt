package com.crux.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density

/**
 * The base surface colour ("the valley"). Screens read this instead of hardcoding [Void] so the
 * OLED "deep" setting can swap it to [Deep] app-wide from one place (CruxTheme).
 */
val LocalVoid = staticCompositionLocalOf { Void }

/**
 * CruxTheme maps the tokens into a Material3 substrate. Dynamic color is OFF
 * (never call dynamic*ColorScheme); these token values are the whole scheme.
 * Dark only. [deep] swaps the background to true-black OLED; [fontScale] multiplies all sp text
 * (applied via LocalDensity, so every .sp size scales without touching CruxType). Both come from
 * settings (DataStore); defaults keep the shipped look.
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
fun CruxTheme(
    deep: Boolean = false,
    fontScale: Float = 1f,
    content: @Composable () -> Unit,
) {
    val background = if (deep) Deep else Void
    val scheme = if (deep) CruxColorScheme.copy(background = Deep) else CruxColorScheme
    val base = LocalDensity.current
    CompositionLocalProvider(
        LocalVoid provides background,
        LocalDensity provides Density(base.density, base.fontScale * fontScale),
    ) {
        MaterialTheme(
            colorScheme = scheme,
            typography = CruxM3Typography,
            content = content,
        )
    }
}
