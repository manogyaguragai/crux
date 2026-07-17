package com.crux.app.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.crux.app.domain.model.Task
import com.crux.app.ui.theme.CruxType
import com.crux.app.ui.theme.Dimens
import com.crux.app.ui.theme.InkHi

/**
 * A task row. Phase 1 slice one: title only. The hold (checkbox) plus the meta line
 * (dates, priority chip) and the tick interaction arrive with the HoldCheckbox slice.
 */
@Composable
fun TaskRow(task: Task, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = Dimens.RowMinHeight)
            .padding(vertical = Dimens.Unit * 2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(1f)) {
            Text(text = task.title, style = CruxType.Body, color = InkHi)
        }
    }
}
