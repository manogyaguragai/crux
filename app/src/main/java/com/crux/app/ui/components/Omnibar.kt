package com.crux.app.ui.components

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.crux.app.domain.model.RecurrenceType
import com.crux.app.domain.model.Source
import com.crux.app.intelligence.KnownProject
import com.crux.app.intelligence.ParseField
import com.crux.app.intelligence.ParseNotice
import com.crux.app.intelligence.ParseResult
import com.crux.app.intelligence.ProjectRef
import com.crux.app.intelligence.parse
import com.crux.app.ui.Copy
import com.crux.app.ui.theme.CruxType
import com.crux.app.ui.theme.Dimens
import com.crux.app.ui.theme.Ember
import com.crux.app.ui.theme.Garnet
import com.crux.app.ui.theme.Hairline
import com.crux.app.ui.theme.InkHi
import com.crux.app.ui.theme.InkLow
import com.crux.app.ui.theme.InkMid
import com.crux.app.ui.theme.Motion
import com.crux.app.ui.theme.Oxblood
import com.crux.app.ui.theme.rememberReducedMotion
import com.crux.app.ui.theme.Raised
import com.crux.app.ui.theme.Surface
import com.crux.app.voice.VoiceController
import com.crux.app.voice.VoiceModel
import com.crux.app.voice.VoiceState
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt

/**
 * The omnibar: the single capture input, the centerpiece of home (ui-ux-decisions.md).
 * Phase 2 runs the deterministic grammar live as you type and shows the extracted fields as neutral
 * chips riding just above the input (never a popup, never a modal; the input stays at thumb reach
 * per the inputs-at-bottom rule). Tapping a chip dismisses that field — its words fall back into the
 * title — which is the whole correction affordance. Capture is never interrupted.
 *
 * The bloom (an oxblood radiance) sits behind the shell, home and app icon only. The idle
 * breathing loop is a phase 4 motion item; this draws the static glow.
 */
@Composable
fun Omnibar(
    projects: List<KnownProject>,
    onCapture: (String, Set<ParseField>, Source) -> Unit,
    modifier: Modifier = Modifier,
) {
    var text by rememberSaveable { mutableStateOf("") }
    // fields the user tapped away this capture; reset on submit or when the line is cleared.
    var dismissed by remember { mutableStateOf(emptySet<ParseField>()) }
    val today = remember { LocalDate.now() } // preview only; the VM re-parses authoritatively

    // voice (phase 4): the mic rides the input's right edge. Hidden until the controller exists.
    // Each finished transcription lands in the input for review (never auto-submits), so a misheard
    // word costs one tap to fix — capture can never fail. [spokenOrigin] remembers the line came from
    // a take (surviving small edits to fix a word) so the captured task keeps its "via voice" mark; it
    // clears once the line is emptied or a plain typed line begins.
    val voice = LocalVoice.current
    val voiceState = voice?.state?.collectAsStateWithLifecycle()
    val installed = voice?.installed?.collectAsStateWithLifecycle()
    var showChooser by remember { mutableStateOf(false) }
    var spokenOrigin by remember { mutableStateOf(false) }
    if (voice != null) {
        LaunchedEffect(voice) {
            voice.transcripts.collect { spoken ->
                text = if (text.isBlank()) spoken else "$text $spoken"
                dismissed = emptySet()
                spokenOrigin = true
            }
        }
    }

    fun submit() {
        if (text.isNotBlank()) {
            onCapture(text, dismissed, if (spokenOrigin) Source.VOICE else Source.TYPED)
            text = ""
            dismissed = emptySet()
            spokenOrigin = false
        }
    }

    val parsed: ParseResult? = if (text.isBlank()) null else parse(text, today, projects, dismissed)
    val chips = parsed?.let { chipsOf(it) } ?: emptyList()
    val voiceStatus = voiceState?.value?.let { voiceStatusText(it) }

    val listening = voiceState?.value is VoiceState.Listening

    Box(modifier) {
        // the bloom: an oxblood-to-garnet radiance behind the shell (home and app icon only). It
        // breathes — one of the app's only two loops. Idle it drifts +/-4% over 6s; while a take is
        // live it tightens and quickens to 1.6s, breathing with the mic (design-tokens.md motion).
        Bloom(listening = listening, modifier = Modifier.matchParentSize())
        Column(Modifier.fillMaxWidth()) {
            // the one-time voice download chooser rides above the input, like the parse chips.
            if (voice != null && showChooser) {
                VoiceChooser(
                    onPick = { model -> voice.download(model); showChooser = false },
                    onDismiss = { showChooser = false },
                )
                Spacer(Modifier.height(Dimens.Unit * 2))
            }
            if (chips.isNotEmpty()) {
                FlowRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = Dimens.Unit),
                    horizontalArrangement = Arrangement.spacedBy(Dimens.Unit * 2),
                    verticalArrangement = Arrangement.spacedBy(Dimens.Unit),
                ) {
                    chips.forEach { chip ->
                        ParseChip(chip) { field ->
                            // a time cannot stand without a date, so dismissing the date drops the
                            // time with it; both words then fall back into the title.
                            dismissed = dismissed + field +
                                (if (field == ParseField.DATE) setOf(ParseField.TIME) else emptySet())
                        }
                    }
                }
                Spacer(Modifier.padding(top = Dimens.Unit))
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Dimens.RadiusShell))
                    .background(Raised)
                    .padding(horizontal = 16.dp, vertical = 15.dp),
                // bottom-aligned so the +/send/mic sit by the last line as the field grows taller.
                verticalAlignment = Alignment.Bottom,
            ) {
                Icon(
                    imageVector = CruxIcons.Add,
                    contentDescription = null,
                    tint = Garnet,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(12.dp))
                Box(Modifier.weight(1f)) {
                    if (text.isEmpty()) {
                        // while voice is working, the status ("listening", "downloading voice · 42%")
                        // takes the placeholder's spot instead of adding another line of chrome.
                        Text(
                            text = voiceStatus ?: Copy.OMNIBAR_PLACEHOLDER,
                            style = CruxType.Body,
                            color = if (voiceStatus != null) InkMid else InkLow,
                        )
                    }
                    BasicTextField(
                        value = text,
                        onValueChange = {
                            text = it
                            if (it.isBlank()) {
                                dismissed = emptySet()
                                spokenOrigin = false // an emptied line is no longer a voice line
                            }
                        },
                        textStyle = CruxType.Body.copy(color = InkHi),
                        // grows with the line (up to 6 lines, then scrolls) so a long voice take is not
                        // cut off; the keyboard's action is "send", and enter submits rather than no-ops.
                        singleLine = false,
                        maxLines = 6,
                        cursorBrush = SolidColor(Garnet),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = { submit() }),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                // a send button so a line (especially a voice take) can be submitted without opening
                // the keyboard — appears only when there is something to send, left of the mic.
                if (text.isNotBlank()) {
                    Spacer(Modifier.width(8.dp))
                    SendButton(onClick = { submit() })
                }
                if (voice != null && voiceState != null) {
                    Spacer(Modifier.width(8.dp))
                    MicButton(
                        state = voiceState.value,
                        hasModel = installed?.value != null,
                        onNeedModel = { showChooser = true },
                        voice = voice,
                    )
                }
            }
        }
    }
}

/**
 * The bloom: a soft, wide, STATIC radiance behind the omnibar — like Gemini's search glow and the
 * mockup .bloom. Garnet at the core (garnet reads as light on near-black; oxblood alone does not),
 * fading gently and widely to nothing. Each lobe is drawn into a square that fully CONTAINS its
 * circle, so the gradient always fades to transparent before any edge — no hard rectangle, no flicker.
 * (No animation: the breathing loop read as a light-bulb flick on device, so the owner chose static.)
 */
@Composable
private fun Bloom(listening: Boolean, modifier: Modifier = Modifier) {
    // A soft glow in the omnibar's OWN rounded-rect shape: draw the pill in garnet, then blur it wide
    // so it becomes a smooth pill-shaped halo — no banding, no hard edge. The real pill sits on top,
    // so only the blurred surround shows. Blur needs API 31+; below that it degrades to no glow (the
    // garnet pill is fully hidden behind the real one) rather than a hard rectangle.
    Spacer(
        modifier
            .blur(54.dp, BlurredEdgeTreatment.Unbounded)
            .drawBehind {
                // draw the pill a touch larger than the real one so the halo sits a little proud of it
                val g = 6.dp.toPx()
                drawRoundRect(
                    color = Garnet.copy(alpha = 0.62f),
                    topLeft = Offset(-g, -g),
                    size = Size(size.width + 2f * g, size.height + 2f * g),
                    cornerRadius = CornerRadius(Dimens.RadiusShell.toPx() + g),
                )
            },
    )
}

/** The send button on the omnibar's right edge: a garnet up-arrow that submits the current line. */
@Composable
private fun SendButton(onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        Modifier
            .size(40.dp)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = CruxIcons.Send,
            contentDescription = "send",
            tint = Garnet,
            modifier = Modifier.size(22.dp),
        )
    }
}

/**
 * The mic that rides the omnibar's right edge. With a model present it is press-and-hold: press starts
 * recording, release transcribes. With no model a tap opens the one-time download chooser. The press
 * gesture keys only on [hasModel]/permission — never on the fine-grained [state] — so a hold survives
 * the Ready→Listening→Ready cycle without the recorder being stranded by a mid-hold restart.
 */
@Composable
private fun MicButton(
    state: VoiceState,
    hasModel: Boolean,
    onNeedModel: () -> Unit,
    voice: VoiceController,
) {
    val context = LocalContext.current
    var granted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted = it }

    val listening = state is VoiceState.Listening
    val busy = state is VoiceState.Downloading || state is VoiceState.Preparing ||
        state is VoiceState.Transcribing
    val tint = when {
        listening || state is VoiceState.Failed -> Ember
        hasModel && !busy -> InkMid
        else -> InkLow
    }
    val fraction = (state as? VoiceState.Downloading)?.fraction ?: 0f

    Box(
        Modifier
            .size(40.dp)
            .pointerInput(hasModel, granted) {
                if (hasModel) {
                    detectTapGestures(
                        onPress = {
                            if (!granted) {
                                permLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            } else {
                                voice.startListening()
                                tryAwaitRelease()
                                voice.stopListening()
                            }
                        },
                    )
                } else {
                    detectTapGestures(onTap = { onNeedModel() })
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        if (state is VoiceState.Downloading) {
            Box(
                Modifier.matchParentSize().drawBehind {
                    val sw = 2.dp.toPx()
                    val inset = sw / 2 + 2.dp.toPx()
                    val arc = Size(size.width - inset * 2, size.height - inset * 2)
                    drawArc(
                        color = Ember.copy(alpha = 0.25f), startAngle = -90f, sweepAngle = 360f,
                        useCenter = false, topLeft = Offset(inset, inset), size = arc,
                        style = Stroke(width = sw, cap = StrokeCap.Round),
                    )
                    drawArc(
                        color = Ember, startAngle = -90f, sweepAngle = 360f * fraction,
                        useCenter = false, topLeft = Offset(inset, inset), size = arc,
                        style = Stroke(width = sw, cap = StrokeCap.Round),
                    )
                },
            )
        }
        Icon(
            imageVector = CruxIcons.Mic,
            contentDescription = Copy.SETTINGS_VOICE,
            tint = tint,
            modifier = Modifier.size(22.dp),
        )
    }
}

/** The one-time download prompt: pick the lightweight or capable model. Calm, positive framing. */
@Composable
private fun VoiceChooser(
    onPick: (VoiceModel) -> Unit,
    onDismiss: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Dimens.RadiusShell))
            .background(Raised)
            .padding(Dimens.Unit * 4),
    ) {
        Text(Copy.VOICE_SETUP_TITLE, style = CruxType.Subhead, color = InkHi)
        Spacer(Modifier.height(Dimens.Unit))
        Text(Copy.VOICE_SETUP_SUB, style = CruxType.Secondary, color = InkMid)
        Spacer(Modifier.height(Dimens.Unit * 3))
        VoiceChoiceRow(Copy.VOICE_LIGHT, Copy.VOICE_LIGHT_SUB) { onPick(VoiceModel.LIGHT) }
        Box(Modifier.fillMaxWidth().height(Dimens.HairlineWidth).background(Hairline))
        VoiceChoiceRow(Copy.VOICE_CAPABLE, Copy.VOICE_CAPABLE_SUB) { onPick(VoiceModel.CAPABLE) }
        Spacer(Modifier.height(Dimens.Unit * 2))
        val cancel = remember { MutableInteractionSource() }
        Text(
            text = Copy.VOICE_SETUP_CANCEL,
            style = CruxType.Data,
            color = InkLow,
            modifier = Modifier
                .clip(RoundedCornerShape(Dimens.RadiusPill))
                .clickable(interactionSource = cancel, indication = null) { onDismiss() }
                .padding(vertical = Dimens.Unit, horizontal = Dimens.Unit * 2),
        )
    }
}

@Composable
private fun VoiceChoiceRow(title: String, sub: String, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Dimens.RadiusCard))
            .clickable(interactionSource = interaction, indication = null) { onClick() }
            .padding(vertical = Dimens.Unit * 2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = CruxType.Body, color = InkHi)
            Text(sub, style = CruxType.Secondary, color = InkMid)
        }
        Icon(CruxIcons.Add, contentDescription = null, tint = Garnet, modifier = Modifier.size(18.dp))
    }
}

/** The voice status shown in the placeholder's spot while a take or a download is in flight. */
private fun voiceStatusText(state: VoiceState): String? = when (state) {
    is VoiceState.Downloading -> "${Copy.VOICE_DOWNLOADING} · ${(state.fraction * 100).roundToInt()}%"
    VoiceState.Preparing -> Copy.VOICE_PREPARING
    VoiceState.Listening -> Copy.VOICE_LISTENING
    VoiceState.Transcribing -> Copy.VOICE_TRANSCRIBING
    is VoiceState.Failed -> state.message
    VoiceState.Ready, VoiceState.NeedsModel -> null
}

/** One preview chip. [field] null = a non-dismissable notice; otherwise tapping it dismisses [field]. */
private data class OmniChip(val label: String, val field: ParseField?, val offer: Boolean = false)

@Composable
private fun ParseChip(chip: OmniChip, onDismiss: (ParseField) -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val field = chip.field
    val bg = if (chip.offer) Garnet.copy(alpha = 0.16f) else Surface
    val fg = when {
        chip.offer -> Garnet
        field != null -> InkMid
        else -> InkLow
    }
    Box(
        Modifier
            .clip(RoundedCornerShape(Dimens.RadiusPill))
            .background(bg)
            .then(
                if (field != null) Modifier.clickable(
                    interactionSource = interaction,
                    indication = null,
                ) { onDismiss(field) } else Modifier,
            )
            .padding(horizontal = Dimens.Unit * 3, vertical = Dimens.Unit * 1.5f),
    ) {
        Text(chip.label, style = CruxType.Data, color = fg)
    }
}

private val ChipDateFmt = DateTimeFormatter.ofPattern("MMM d", Locale.ENGLISH)
private val ChipTimeFmt = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)

/** Build the neutral chip row from a parse result: project, priority, date, time, recurrence, notices. */
private fun chipsOf(r: ParseResult): List<OmniChip> {
    val chips = mutableListOf<OmniChip>()
    when (val p = r.project) {
        is ProjectRef.Matched -> chips += OmniChip("# ${p.name}", ParseField.PROJECT)
        is ProjectRef.Unknown -> chips += OmniChip("${Copy.OMNIBAR_CHIP_NEW} # ${p.raw}", ParseField.PROJECT, offer = true)
        null -> {}
    }
    r.priority?.let { chips += OmniChip("p$it", ParseField.PRIORITY) }
    r.recurrenceType?.let { chips += OmniChip(recurrenceLabel(it, r.recurrenceWeekday, r.recurrenceDay), ParseField.RECURRENCE) }
    // A recurrence already implies its first due; only show a standalone date chip when it is a
    // plain (non-recurring) due date, so the row does not say "daily" and "jul 18" as two things.
    if (r.recurrenceType == null) r.dueDate?.let { chips += OmniChip(dateLabel(it, LocalDate.now()), ParseField.DATE) }
    // a time only stands with a date, so never show a lone time chip.
    if (r.hasTime && r.dueDate != null) r.time?.let { chips += OmniChip(timeLabel(it), ParseField.TIME) }
    r.notices.forEach { chips += OmniChip(noticeLabel(it), null) }
    return chips
}

/** today -> "today", tomorrow -> "tomorrow", within a week -> weekday name, else "mmm d". */
private fun dateLabel(date: LocalDate, today: LocalDate): String = when {
    date == today -> "today"
    date == today.plusDays(1) -> "tomorrow"
    date.isAfter(today) && date.isBefore(today.plusDays(7)) ->
        date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.ENGLISH).lowercase(Locale.ENGLISH)
    else -> date.format(ChipDateFmt).lowercase(Locale.ENGLISH)
}

private fun timeLabel(time: LocalTime): String =
    time.format(ChipTimeFmt).lowercase(Locale.ENGLISH)

private fun recurrenceLabel(type: RecurrenceType, weekday: Int?, day: Int?): String = when (type) {
    RecurrenceType.DAILY -> "daily"
    RecurrenceType.WEEKDAYS -> "weekdays"
    RecurrenceType.WEEKLY -> weekday?.let {
        "every " + DayOfWeek.of(it).getDisplayName(TextStyle.FULL, Locale.ENGLISH).lowercase(Locale.ENGLISH)
    } ?: "weekly"
    RecurrenceType.MONTHLY -> day?.let { "on the ${ordinal(it)}" } ?: "monthly"
}

private fun ordinal(n: Int): String {
    val suffix = if (n in 11..13) "th" else when (n % 10) {
        1 -> "st"; 2 -> "nd"; 3 -> "rd"; else -> "th"
    }
    return "$n$suffix"
}

private fun noticeLabel(notice: ParseNotice): String = when (notice) {
    ParseNotice.TWO_DATES -> Copy.OMNIBAR_NOTICE_TWO_DATES
}
