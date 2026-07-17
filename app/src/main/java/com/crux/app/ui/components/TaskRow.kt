package com.crux.app.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextDecoration
import com.crux.app.domain.model.Task
import com.crux.app.domain.model.TaskStatus
import com.crux.app.ui.theme.CruxType
import com.crux.app.ui.theme.Dimens
import com.crux.app.ui.theme.InkHi
import com.crux.app.ui.theme.InkLow

/**
 * A task row: the hold, then the title. Done rows fade (ink drops to InkLow) and strike through.
 * The meta line (dates, priority chip) arrives once tasks can carry those (detail / parsing).
 * [onToggle] null renders the hold display-only.
 */
@Composable
fun TaskRow(task: Task, modifier: Modifier = Modifier, onToggle: (() -> Unit)? = null) {
    val done = task.status == TaskStatus.DONE
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = Dimens.RowMinHeight),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HoldCheckbox(checked = done, onToggle = onToggle)
        Spacer(Modifier.width(Dimens.Unit * 2))
        Text(
            text = task.title,
            style = CruxType.Body,
            color = if (done) InkLow else InkHi,
            textDecoration = if (done) TextDecoration.LineThrough else null,
            modifier = Modifier.weight(1f),
        )
    }
}
