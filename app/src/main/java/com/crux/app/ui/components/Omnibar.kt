package com.crux.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.crux.app.domain.model.RecurrenceType
import com.crux.app.intelligence.KnownProject
import com.crux.app.intelligence.ParseField
import com.crux.app.intelligence.ParseNotice
import com.crux.app.intelligence.ParseResult
import com.crux.app.intelligence.ProjectRef
import com.crux.app.intelligence.parse
import com.crux.app.ui.Copy
import com.crux.app.ui.theme.CruxType
import com.crux.app.ui.theme.Dimens
import com.crux.app.ui.theme.Garnet
import com.crux.app.ui.theme.InkHi
import com.crux.app.ui.theme.InkLow
import com.crux.app.ui.theme.InkMid
import com.crux.app.ui.theme.Oxblood
import com.crux.app.ui.theme.Raised
import com.crux.app.ui.theme.Surface
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

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
    onCapture: (String, Set<ParseField>) -> Unit,
    modifier: Modifier = Modifier,
) {
    var text by rememberSaveable { mutableStateOf("") }
    // fields the user tapped away this capture; reset on submit or when the line is cleared.
    var dismissed by remember { mutableStateOf(emptySet<ParseField>()) }
    val today = remember { LocalDate.now() } // preview only; the VM re-parses authoritatively

    fun submit() {
        if (text.isNotBlank()) {
            onCapture(text, dismissed)
            text = ""
            dismissed = emptySet()
        }
    }

    val parsed: ParseResult? = if (text.isBlank()) null else parse(text, today, projects, dismissed)
    val chips = parsed?.let { chipsOf(it) } ?: emptyList()

    Box(modifier) {
        // the bloom: radial oxblood behind the shell, per design-tokens.md.
        Spacer(
            Modifier
                .matchParentSize()
                .drawBehind {
                    drawRect(
                        brush = Brush.radialGradient(
                            0.00f to Oxblood.copy(alpha = 0.55f),
                            0.46f to Oxblood.copy(alpha = 0.18f),
                            1.00f to Color.Transparent,
                            center = Offset(size.width * 0.5f, size.height * 0.82f),
                            radius = 0.72f * maxOf(size.width, size.height),
                        ),
                    )
                },
        )
        androidx.compose.foundation.layout.Column(Modifier.fillMaxWidth()) {
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
                verticalAlignment = Alignment.CenterVertically,
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
                        Text(Copy.OMNIBAR_PLACEHOLDER, style = CruxType.Body, color = InkLow)
                    }
                    BasicTextField(
                        value = text,
                        onValueChange = {
                            text = it
                            if (it.isBlank()) dismissed = emptySet()
                        },
                        textStyle = CruxType.Body.copy(color = InkHi),
                        singleLine = true,
                        cursorBrush = SolidColor(Garnet),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { submit() }),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
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
