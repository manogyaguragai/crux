package com.crux.app.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/**
 * The four tab glyphs, transcribed verbatim from the tab bar SVGs in
 * 02-design/crux-screens-v3.html. Never Icons.Default.* (design-tokens / phases.md).
 *
 * Built with a neutral fill/stroke; the tab tints them (ember active, ink-low idle)
 * via Icon(tint = ...), so the color lives at the call site, not baked in here.
 */
private val Ink = SolidColor(Color.Black) // placeholder; recolored by Icon tint

private fun path(d: String) = PathParser().parsePathString(d).toNodes()

// stroke rendering that matches the mockups' 24-viewport line glyphs
private fun ImageVector.Builder.stroke(
    d: String,
    width: Float = 1.7f,
    join: StrokeJoin = StrokeJoin.Miter,
) = addPath(
    pathData = path(d),
    stroke = Ink,
    strokeLineWidth = width,
    strokeLineCap = StrokeCap.Round,
    strokeLineJoin = join,
)

private fun ImageVector.Builder.fill(d: String) = addPath(pathData = path(d), fill = Ink)

object CruxIcons {

    /** home: the stone stack (three pebbles). viewBox 48. */
    val Home: ImageVector by lazy {
        ImageVector.Builder("home", 24.dp, 24.dp, 48f, 48f).apply {
            fill("M39.86 34.58 C39.83 35.78 37.26 37.05 34.64 37.89 C32.02 38.74 27.79 39.58 24.12 39.67 C20.46 39.75 15.19 39.12 12.65 38.40 C10.12 37.69 8.89 36.60 8.91 35.40 C8.94 34.19 10.30 32.18 12.79 31.20 C15.28 30.23 20.19 29.63 23.86 29.55 C27.52 29.48 32.12 29.91 34.78 30.75 C37.45 31.58 39.88 33.39 39.86 34.58 Z")
            fill("M34.57 23.98 C34.63 25.05 33.60 26.23 31.78 26.83 C29.96 27.43 26.25 27.65 23.66 27.56 C21.07 27.48 18.10 27.07 16.23 26.34 C14.37 25.62 12.47 24.28 12.48 23.20 C12.49 22.13 14.38 20.69 16.30 19.89 C18.22 19.10 21.45 18.35 23.98 18.44 C26.51 18.52 29.70 19.46 31.46 20.39 C33.23 21.31 34.52 22.90 34.57 23.98 Z")
            fill("M32.47 12.38 C32.47 13.35 31.07 14.52 29.76 15.18 C28.44 15.83 26.35 16.22 24.58 16.29 C22.82 16.37 20.51 16.14 19.15 15.63 C17.79 15.12 16.44 14.19 16.40 13.22 C16.37 12.25 17.66 10.57 18.95 9.79 C20.25 9.02 22.37 8.63 24.18 8.55 C25.98 8.48 28.40 8.68 29.78 9.32 C31.16 9.96 32.48 11.40 32.47 12.38 Z")
        }.build()
    }

    /** stack: bulleted list (three dots + three lines). viewBox 24. */
    val Stack: ImageVector by lazy {
        ImageVector.Builder("stack", 24.dp, 24.dp, 24f, 24f).apply {
            fill("M3.4,6 a1.1,1.1 0 1,0 2.2,0 a1.1,1.1 0 1,0 -2.2,0 Z")
            fill("M3.4,12 a1.1,1.1 0 1,0 2.2,0 a1.1,1.1 0 1,0 -2.2,0 Z")
            fill("M3.4,18 a1.1,1.1 0 1,0 2.2,0 a1.1,1.1 0 1,0 -2.2,0 Z")
            stroke("M9 6h11M9 12h11M9 18h11")
        }.build()
    }

    /** projects: descending lines (a ranked outline). viewBox 24. */
    val Projects: ImageVector by lazy {
        ImageVector.Builder("projects", 24.dp, 24.dp, 24f, 24f).apply {
            stroke("M4 6h16M4 12h11M4 18h7")
        }.build()
    }

    /** review: an inbox tray. viewBox 24. */
    val Review: ImageVector by lazy {
        ImageVector.Builder("review", 24.dp, 24.dp, 24f, 24f).apply {
            stroke("M4 13l3 5h10l3-5M4 13V7a2 2 0 0 1 2-2h12a2 2 0 0 1 2 2v6", join = StrokeJoin.Round)
        }.build()
    }
}
