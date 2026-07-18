package com.crux.app.ui.screens.review

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.crux.app.ui.Copy
import com.crux.app.ui.ReviewProposal
import com.crux.app.ui.ReviewViewModel
import com.crux.app.ui.components.TabHeader
import com.crux.app.ui.theme.Cream
import com.crux.app.ui.theme.CruxType
import com.crux.app.ui.theme.Dimens
import com.crux.app.ui.theme.Garnet
import com.crux.app.ui.theme.InkHi
import com.crux.app.ui.theme.InkLow
import com.crux.app.ui.theme.InkMid
import com.crux.app.ui.theme.LocalVoid
import com.crux.app.ui.theme.Surface

/**
 * The review tab (phase 3): the AI's batched proposals, accepted or rejected one tap at a time, never
 * applied silently. This phase's proposal is project inference — the model reads the inbox pile and
 * guesses a home for each unfiled task. Off by default: with AI disabled the tab just says how to turn
 * it on, and even with AI on nothing runs until the owner taps "sort the inbox".
 */
@Composable
fun ReviewScreen(vm: ReviewViewModel, onOpenSettings: () -> Unit) {
    val aiActive by vm.aiActive.collectAsStateWithLifecycle()
    val proposals by vm.proposals.collectAsStateWithLifecycle()
    val scanning by vm.scanning.collectAsStateWithLifecycle()
    val scanned by vm.scanned.collectAsStateWithLifecycle()
    val scanFailed by vm.scanFailed.collectAsStateWithLifecycle()
    val inboxCount by vm.inboxCount.collectAsStateWithLifecycle()

    Column(
        Modifier
            .fillMaxSize()
            .background(LocalVoid.current)
            .padding(horizontal = Dimens.ScreenMargin),
    ) {
        Box(Modifier.fillMaxWidth().height(Dimens.ScreenMargin))
        TabHeader(title = Copy.REVIEW_TITLE, onOpenSettings = onOpenSettings)
        Spacer(Modifier.height(Dimens.GroupGap))

        when {
            !aiActive -> Centered(Copy.REVIEW_AI_OFF)
            inboxCount == 0 && proposals.isEmpty() -> Centered(Copy.REVIEW_EMPTY_INBOX)
            else -> {
                ScanButton(
                    label = "${Copy.REVIEW_SCAN} · $inboxCount",
                    enabled = !scanning && inboxCount > 0,
                    onClick = vm::scan,
                )
                Spacer(Modifier.height(Dimens.GroupGap))
                when {
                    scanning -> Centered(Copy.REVIEW_SCANNING)
                    scanFailed -> Centered(Copy.AI_OFFLINE)
                    proposals.isNotEmpty() -> LazyColumn(
                        Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(Dimens.Unit * 3),
                    ) {
                        items(items = proposals, key = { it.task.id }) { p ->
                            ProposalCard(p, onFile = { vm.accept(p) }, onDismiss = { vm.dismiss(p) })
                        }
                    }
                    scanned -> Centered(Copy.EMPTY_REVIEW)
                    else -> Unit // initial: the scan button is the only prompt
                }
            }
        }
    }
}

@Composable
private fun Centered(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text = text, style = CruxType.Passage, color = InkMid, textAlign = TextAlign.Center)
    }
}

@Composable
private fun ProposalCard(proposal: ReviewProposal, onFile: () -> Unit, onDismiss: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Dimens.RadiusCard))
            .background(Surface)
            .padding(Dimens.Unit * 4),
    ) {
        Text(proposal.task.title, style = CruxType.Body, color = InkHi)
        Spacer(Modifier.height(Dimens.Unit * 2))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("# ${proposal.projectName}", style = CruxType.Data, color = Garnet)
            Spacer(Modifier.width(Dimens.Unit * 2))
            Text(proposal.reason, style = CruxType.Secondary, color = InkMid)
        }
        Spacer(Modifier.height(Dimens.Unit * 3))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Pill(Copy.REVIEW_DISMISS, filled = false, onClick = onDismiss)
            Spacer(Modifier.width(Dimens.Unit * 2))
            Pill(Copy.REVIEW_FILE, filled = true, onClick = onFile)
        }
    }
}

@Composable
private fun ScanButton(label: String, enabled: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        Modifier
            .clip(RoundedCornerShape(Dimens.RadiusPill))
            .background(if (enabled) Surface else Surface.copy(alpha = 0.5f))
            .clickable(interactionSource = interaction, indication = null, enabled = enabled, onClick = onClick)
            .padding(horizontal = Dimens.Unit * 4, vertical = Dimens.Unit * 3),
    ) {
        Text(label, style = CruxType.Action, color = if (enabled) InkHi else InkLow)
    }
}

@Composable
private fun Pill(label: String, filled: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        Modifier
            .clip(RoundedCornerShape(Dimens.RadiusPill))
            .background(if (filled) Garnet else Color.Transparent)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = Dimens.Unit * 4, vertical = Dimens.Unit * 2),
    ) {
        Text(label, style = CruxType.Action, color = if (filled) Cream else InkMid)
    }
}
