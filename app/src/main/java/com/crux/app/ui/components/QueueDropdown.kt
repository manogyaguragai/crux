package com.crux.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.crux.app.data.queue.QueueItem
import com.crux.app.data.queue.QueueStatus
import com.crux.app.ui.Copy
import com.crux.app.ui.theme.CruxType
import com.crux.app.ui.theme.Dimens
import com.crux.app.ui.theme.Ember
import com.crux.app.ui.theme.InkHi
import com.crux.app.ui.theme.InkLow
import com.crux.app.ui.theme.InkMid
import com.crux.app.ui.theme.Overdue
import com.crux.app.ui.theme.Overlay
import com.crux.app.ui.theme.Surface

/**
 * The capture-queue list, shown as a themed panel dropping from the queue icon (top-right). Each row is
 * swipeable — left to remove, right to retry — and shows the line plus its live status/error. A close
 * (x) sits top-right; "clear finished" sweeps the settled rows.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueueDropdown(
    items: List<QueueItem>,
    onClose: () -> Unit,
    onRemove: (Long) -> Unit,
    onRetry: (Long) -> Unit,
    onClearFinished: () -> Unit,
) {
    Column(
        Modifier
            .width(320.dp)
            .clip(RoundedCornerShape(Dimens.RadiusCard))
            .background(Surface)
            .padding(Dimens.Unit * 3),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(Copy.QUEUE_TITLE, style = CruxType.Body, color = InkHi, modifier = Modifier.weight(1f))
            val closeInteraction = remember { MutableInteractionSource() }
            // reuse the plus glyph rotated into an ✕
            Icon(
                imageVector = CruxIcons.Add,
                contentDescription = "close",
                tint = InkMid,
                modifier = Modifier
                    .size(22.dp)
                    .rotate(45f)
                    .clickable(interactionSource = closeInteraction, indication = null, onClick = onClose),
            )
        }
        Spacer(Modifier.height(Dimens.Unit * 2))

        if (items.isEmpty()) {
            Box(Modifier.fillMaxWidth().padding(vertical = Dimens.Unit * 6), contentAlignment = Alignment.Center) {
                Text(Copy.QUEUE_EMPTY, style = CruxType.Secondary, color = InkMid, textAlign = TextAlign.Center)
            }
        } else {
            LazyColumn(Modifier.heightIn(max = 360.dp)) {
                items(items = items, key = { it.id }) { item ->
                    QueueRow(item = item, onRemove = { onRemove(item.id) }, onRetry = { onRetry(item.id) })
                }
            }
            if (items.any { it.status == QueueStatus.DONE || it.status == QueueStatus.FAILED }) {
                Spacer(Modifier.height(Dimens.Unit * 2))
                val clearInteraction = remember { MutableInteractionSource() }
                Text(
                    text = Copy.QUEUE_CLEAR,
                    style = CruxType.Action,
                    color = InkMid,
                    modifier = Modifier
                        .align(Alignment.End)
                        .clickable(interactionSource = clearInteraction, indication = null, onClick = onClearFinished)
                        .padding(Dimens.Unit),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QueueRow(item: QueueItem, onRemove: () -> Unit, onRetry: () -> Unit) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            when (value) {
                SwipeToDismissBoxValue.StartToEnd -> { onRetry(); false } // swipe right = retry (snap back)
                SwipeToDismissBoxValue.EndToStart -> { onRemove(); true } // swipe left = delete
                SwipeToDismissBoxValue.Settled -> false
            }
        },
    )
    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {
            val toStart = dismissState.targetValue == SwipeToDismissBoxValue.EndToStart
            val toEnd = dismissState.targetValue == SwipeToDismissBoxValue.StartToEnd
            Box(
                Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(Dimens.RadiusCard))
                    .background(
                        when {
                            toStart -> Overdue.copy(alpha = 0.22f)
                            toEnd -> Ember.copy(alpha = 0.18f)
                            else -> Overlay
                        },
                    )
                    .padding(horizontal = Dimens.Unit * 4),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (toEnd) Text("retry", style = CruxType.Action, color = Ember)
                if (toStart) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.CenterEnd) {
                        Text("remove", style = CruxType.Action, color = Overdue)
                    }
                }
            }
        },
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .background(Surface)
                .padding(vertical = Dimens.Unit * 2, horizontal = Dimens.Unit),
        ) {
            Text(item.text, style = CruxType.Body, color = InkHi, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(statusLine(item), style = CruxType.Secondary, color = statusColor(item))
        }
    }
}

private fun statusLine(item: QueueItem): String = when (item.status) {
    QueueStatus.PENDING -> Copy.QUEUE_PENDING
    QueueStatus.PROCESSING -> Copy.QUEUE_WORKING
    QueueStatus.DONE -> item.message ?: Copy.QUEUE_ADDED
    QueueStatus.FAILED -> item.message ?: "failed"
}

private fun statusColor(item: QueueItem) = when (item.status) {
    QueueStatus.PENDING -> InkMid
    QueueStatus.PROCESSING -> Ember
    QueueStatus.DONE -> InkLow
    QueueStatus.FAILED -> Overdue
}
