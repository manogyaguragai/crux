package com.crux.app.ui.screens.history

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.crux.app.domain.model.CompletionLog
import com.crux.app.ui.Copy
import com.crux.app.ui.HistoryViewModel
import com.crux.app.ui.components.CruxIcons
import com.crux.app.ui.theme.CruxType
import com.crux.app.ui.theme.Dimens
import com.crux.app.ui.theme.InkHi
import com.crux.app.ui.theme.InkLow
import com.crux.app.ui.theme.InkMid
import com.crux.app.ui.theme.LocalVoid
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Total history (a pushed screen from settings): every completed task, newest first, grouped by day.
 * Reads the completion log, which outlives the tasks themselves (they clear at the sweep), so this is
 * the permanent record. Filters by project/priority are a follow-up; the day grouping is the date view.
 */
@Composable
fun HistoryScreen(vm: HistoryViewModel, onBack: () -> Unit) {
    val entries by vm.entries.collectAsStateWithLifecycle()
    val zone = remember { ZoneId.systemDefault() }
    val rows = remember(entries) { groupByDay(entries, zone) }

    Column(
        Modifier
            .fillMaxSize()
            .background(LocalVoid.current)
            .padding(horizontal = Dimens.ScreenMargin),
    ) {
        Spacer(Modifier.height(Dimens.Unit * 2))
        val backInteraction = remember { MutableInteractionSource() }
        Box(
            Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(Dimens.RadiusPill))
                .clickable(interactionSource = backInteraction, indication = null) { onBack() },
            contentAlignment = Alignment.CenterStart,
        ) {
            Icon(CruxIcons.Back, contentDescription = "back", tint = InkMid, modifier = Modifier.size(24.dp))
        }
        Spacer(Modifier.height(Dimens.Unit * 2))
        Text(Copy.HISTORY_TITLE, style = CruxType.Display, color = InkHi)
        Spacer(Modifier.height(Dimens.Unit * 2))

        if (rows.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth()) {
                Text(
                    text = Copy.HISTORY_EMPTY,
                    style = CruxType.Passage,
                    color = InkMid,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.align(Alignment.Center),
                )
            }
        } else {
            LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
                items(items = rows, key = { it.key }) { row ->
                    when (row) {
                        is HistoryRow.DayHeader -> DayHeader(row.label)
                        is HistoryRow.Entry -> EntryRow(row.log, zone)
                    }
                }
            }
        }
    }
}

@Composable
private fun DayHeader(label: String) {
    Spacer(Modifier.height(Dimens.GroupGap))
    Text(label.uppercase(Locale.ENGLISH), style = CruxType.Eyebrow, color = InkLow)
    Spacer(Modifier.height(Dimens.Unit * 2))
}

@Composable
private fun EntryRow(log: CompletionLog, zone: ZoneId) {
    Column(Modifier.fillMaxWidth().heightIn(min = Dimens.RowMinHeight * 0.7f)) {
        Spacer(Modifier.height(Dimens.Unit * 2))
        Text(log.titleSnapshot, style = CruxType.Body, color = InkHi)
        val time = Instant.ofEpochMilli(log.completedAt).atZone(zone).format(TimeFmt).lowercase(Locale.ENGLISH)
        val meta = log.projectNameSnapshot?.let { "$it · $time" } ?: time
        Text(meta, style = CruxType.Data, color = InkMid)
        Spacer(Modifier.height(Dimens.Unit * 2))
    }
}

private sealed interface HistoryRow {
    val key: String
    data class DayHeader(val label: String) : HistoryRow {
        override val key get() = "h_$label"
    }
    data class Entry(val log: CompletionLog) : HistoryRow {
        override val key get() = "e_${log.id}"
    }
}

private val TimeFmt = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)
private val DayFmt = DateTimeFormatter.ofPattern("MMM d", Locale.ENGLISH)

private fun groupByDay(entries: List<CompletionLog>, zone: ZoneId): List<HistoryRow> {
    val today = LocalDate.now(zone)
    val rows = ArrayList<HistoryRow>(entries.size + 8)
    var lastDate: LocalDate? = null
    for (log in entries) {
        val date = Instant.ofEpochMilli(log.completedAt).atZone(zone).toLocalDate()
        if (date != lastDate) {
            rows += HistoryRow.DayHeader(dayLabel(date, today))
            lastDate = date
        }
        rows += HistoryRow.Entry(log)
    }
    return rows
}

private fun dayLabel(date: LocalDate, today: LocalDate): String = when (date) {
    today -> Copy.HISTORY_TODAY
    today.minusDays(1) -> Copy.HISTORY_YESTERDAY
    else -> date.format(DayFmt).lowercase(Locale.ENGLISH)
}
