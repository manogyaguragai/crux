@file:OptIn(ExperimentalTextApi::class)

package com.crux.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.crux.app.R

/**
 * Chisel: the type system, per 02-design/design-tokens.md.
 * Bricolage Grotesque (display), Instrument Sans (interface, italic = soft voice),
 * Geist Mono (data). All three are variable TTFs bundled in res/font.
 *
 * Weights are requested with FontVariation on the variable axis; if a device renders
 * an axis wrong, note the fallback in Decisions rather than fighting it (design-tokens.md).
 */

private fun bricolage(weight: Int) = FontFamily(
    Font(
        R.font.bricolage_grotesque,
        weight = FontWeight(weight),
        variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
    )
)

private fun instrument(weight: Int) = FontFamily(
    Font(
        R.font.instrument_sans,
        weight = FontWeight(weight),
        variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
    )
)

private fun instrumentItalic(weight: Int) = FontFamily(
    Font(
        R.font.instrument_sans_italic,
        weight = FontWeight(weight),
        style = FontStyle.Italic,
        variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
    )
)

private fun geist(weight: Int) = FontFamily(
    Font(
        R.font.geist_mono,
        weight = FontWeight(weight),
        variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
    )
)

/**
 * The named roles. Compose has no text-transform, so [Eyebrow] carries only its
 * tracking and weight; callers uppercase the string (group headers, section labels).
 */
object CruxType {
    val Display = TextStyle(
        fontFamily = bricolage(620), fontWeight = FontWeight(620),
        fontSize = 34.sp, lineHeight = 37.sp, letterSpacing = (-0.015).em,
    )
    val Subhead = TextStyle(
        fontFamily = bricolage(540), fontWeight = FontWeight(540),
        fontSize = 22.sp, lineHeight = 26.sp,
    )
    val Passage = TextStyle(
        fontFamily = instrumentItalic(400), fontWeight = FontWeight(400), fontStyle = FontStyle.Italic,
        fontSize = 17.sp, lineHeight = 24.sp,
    )
    val Body = TextStyle(
        fontFamily = instrument(400), fontWeight = FontWeight(400),
        fontSize = 16.sp, lineHeight = 22.sp,
    )
    val Secondary = TextStyle(
        fontFamily = instrument(400), fontWeight = FontWeight(400),
        fontSize = 14.sp, lineHeight = 20.sp,
    )
    val Action = TextStyle(
        fontFamily = instrument(600), fontWeight = FontWeight(600),
        fontSize = 15.sp, lineHeight = 20.sp, letterSpacing = 0.02.em,
    )
    val Data = TextStyle(
        fontFamily = geist(400), fontWeight = FontWeight(400),
        fontSize = 11.5.sp, lineHeight = 16.sp, letterSpacing = 0.02.em,
    )
    val Eyebrow = TextStyle(
        fontFamily = geist(500), fontWeight = FontWeight(500),
        fontSize = 11.sp, lineHeight = 14.sp, letterSpacing = 0.18.em,
    )
}

/**
 * Minimal Material3 typography so any stray M3 component inherits Chisel faces
 * instead of Roboto. In-app text uses [CruxType] directly.
 */
val CruxM3Typography = Typography(
    bodyLarge = CruxType.Body,
    bodyMedium = CruxType.Secondary,
    titleLarge = CruxType.Subhead,
    labelLarge = CruxType.Action,
    labelSmall = CruxType.Data,
)
