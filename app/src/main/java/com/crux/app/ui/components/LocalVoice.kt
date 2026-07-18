package com.crux.app.ui.components

import androidx.compose.runtime.staticCompositionLocalOf
import com.crux.app.voice.VoiceController

/**
 * The app-scoped voice controller, provided once in CruxApp so the omnibar mic and the settings voice
 * row share one download/record state. Null when voice is unavailable (e.g. previews) — the mic simply
 * does not render.
 */
val LocalVoice = staticCompositionLocalOf<VoiceController?> { null }
