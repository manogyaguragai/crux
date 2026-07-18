package com.crux.app.ui.screens.review

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.crux.app.ui.Copy
import com.crux.app.ui.NudgeContext
import com.crux.app.ui.ReviewProposal
import com.crux.app.ui.ReviewViewModel
import com.crux.app.ui.components.TabHeader
import com.crux.app.ui.theme.Blush
import com.crux.app.ui.theme.Cream
import com.crux.app.ui.theme.CruxType
import com.crux.app.ui.theme.Dimens
import com.crux.app.ui.theme.Ember
import com.crux.app.ui.theme.Garnet
import com.crux.app.ui.theme.Hairline
import com.crux.app.ui.theme.InkHi
import com.crux.app.ui.theme.InkLow
import com.crux.app.ui.theme.InkMid
import com.crux.app.ui.theme.LocalVoid
import com.crux.app.ui.theme.Surface
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * The review tab (phase 3): batched proposals, accepted or rejected one tap at a time, never applied
 * silently. Two proposal types: (1) REPRIORITIZE — deterministic priority nudges (a low-priority task
 * gone urgent), pure rules so the list is live and shows even with AI off; (2) SORT THE INBOX — AI
 * project inference over the unfiled pile, gated on AI being on + keyed and run only on "sort the inbox".
 */
@Composable
fun ReviewScreen(vm: ReviewViewModel, onOpenSettings: () -> Unit) {
    val aiActive by vm.aiActive.collectAsStateWithLifecycle()
    val proposals by vm.proposals.collectAsStateWithLifecycle()
    val scanning by vm.scanning.collectAsStateWithLifecycle()
    val scanned by vm.scanned.collectAsStateWithLifecycle()
    val scanFailed by vm.scanFailed.collectAsStateWithLifecycle()
    val inboxCount by vm.inboxCount.collectAsStateWithLifecycle()
    val nudges by vm.nudgeContexts.collectAsStateWithLifecycle()

    Column(
        Modifier
            .fillMaxSize()
            .background(LocalVoid.current)
            .padding(horizontal = Dimens.ScreenMargin),
    ) {
        Box(Modifier.fillMaxWidth().height(Dimens.ScreenMargin))
        val waiting = nudges.size + proposals.size
        TabHeader(
            title = Copy.REVIEW_TITLE,
            onOpenSettings = onOpenSettings,
            eyebrow = Copy.REVIEW_EYEBROW,
            subline = waiting.takeIf { it > 0 }?.let {
                { Text("$it to review", style = CruxType.Data, color = Ember) }
            },
        )
        Spacer(Modifier.height(Dimens.GroupGap))

        val hasNudges = nudges.isNotEmpty()
        val inboxActive = aiActive && (inboxCount > 0 || proposals.isNotEmpty() || scanning || scanned)

        // one-tap clear of the whole queue — shown only while something is actually pending
        if (nudges.isNotEmpty() || proposals.isNotEmpty()) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                ApproveAllPill(onClick = vm::acceptAll)
            }
            Spacer(Modifier.height(Dimens.Unit * 2))
        }

        // neither section has anything: one calm centered line
        if (!hasNudges && !inboxActive) {
            Centered(if (!aiActive) Copy.REVIEW_AI_OFF else Copy.REVIEW_EMPTY_INBOX)
            return@Column
        }

        LazyColumn(
            Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(Dimens.Unit * 3),
        ) {
            // section: reprioritize (deterministic, always live)
            if (hasNudges) {
                item(key = "hdr-priority") { SectionLabel(Copy.REVIEW_SECTION_PRIORITY) }
                items(items = nudges, key = { "n-${it.nudge.task.id}" }) { ctx ->
                    NudgeCard(
                        ctx,
                        onBump = { vm.acceptNudge(ctx.nudge) },
                        onSkip = { vm.skipNudge(ctx.nudge) },
                    )
                }
            }
            // section: sort the inbox (AI)
            if (aiActive) {
                item(key = "sort") {
                    if (hasNudges) Spacer(Modifier.height(Dimens.Unit * 2))
                    ScanButton(
                        label = "${Copy.REVIEW_SCAN} · $inboxCount",
                        enabled = !scanning && inboxCount > 0,
                        onClick = vm::scan,
                    )
                }
                when {
                    scanning -> item(key = "scanning") { PadCentered(Copy.REVIEW_SCANNING) }
                    scanFailed -> item(key = "failed") { PadCentered(Copy.AI_OFFLINE) }
                    proposals.isNotEmpty() -> items(items = proposals, key = { "p-${it.task.id}" }) { p ->
                        ProposalCard(p, onFile = { vm.accept(p) }, onDismiss = { vm.dismiss(p) })
                    }
                    scanned -> item(key = "scanned") { PadCentered(Copy.EMPTY_REVIEW) }
                }
            } else if (hasNudges) {
                // AI is off but we did show nudges: a quiet pointer at the inbox sort
                item(key = "aioff") { PadCentered(Copy.REVIEW_AI_OFF) }
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

/** An inline centered line (does not steal the whole screen — used inside the scrolling sections). */
@Composable
private fun PadCentered(text: String) {
    Box(
        Modifier.fillMaxWidth().padding(vertical = Dimens.GroupGap),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = text, style = CruxType.Passage, color = InkMid, textAlign = TextAlign.Center)
    }
}

/** A quiet section eyebrow over a group of cards. */
@Composable
private fun SectionLabel(text: String) {
    Text(text.uppercase(), style = CruxType.Eyebrow, color = InkLow)
}

/**
 * A reorder card (mockup screen 08): the plain reason the task is urgent, then a before/after pair of
 * mini-rows — the nudge's task in the "up" variant (blush, ember-ish border, showing where it lands),
 * and the neighbor it leapfrogs (plain, showing its priority). Two choices: keep the order, or move up.
 */
@Composable
private fun NudgeCard(ctx: NudgeContext, onBump: () -> Unit, onSkip: () -> Unit) {
    val nudge = ctx.nudge
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Dimens.RadiusCard))
            .background(Surface)
            .border(Dimens.HairlineWidth, Hairline, RoundedCornerShape(Dimens.RadiusCard))
            .padding(Dimens.Unit * 4),
    ) {
        Text(Copy.REVIEW_REORDER.uppercase(), style = CruxType.Eyebrow, color = InkLow)
        Spacer(Modifier.height(Dimens.Unit * 2))
        Text(nudge.reason, style = CruxType.Secondary, color = InkMid)
        Spacer(Modifier.height(Dimens.Unit * 3))
        // the task, rising — where it lands (its due day, or the target priority if undated)
        MiniRow(
            title = nudge.task.title,
            tag = nudge.task.dueAt?.let { "${dayTag(it)} · ↑" } ?: "→ p${nudge.targetPriority}",
            up = true,
        )
        // the neighbor it leapfrogs (omitted when there's nothing above it)
        ctx.above?.let { above ->
            Spacer(Modifier.height(Dimens.Unit * 1.5f))
            MiniRow(title = above.title, tag = "p${above.priority}", up = false)
        }
        Spacer(Modifier.height(Dimens.Unit * 3))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Pill(Copy.REVIEW_KEEP_ORDER, filled = false, onClick = onSkip)
            Spacer(Modifier.width(Dimens.Unit * 2))
            Pill(Copy.REVIEW_MOVE_UP, filled = true, onClick = onBump)
        }
    }
}

/**
 * One comparison row (mockup .minirow): a small stone dot, the task title, and a right-aligned mono tag.
 * [up] is the rising task — blush fill and an ember-ish border to read as "this moves"; otherwise plain.
 */
@Composable
private fun MiniRow(title: String, tag: String, up: Boolean) {
    val shape = RoundedCornerShape(Dimens.Unit * 3) // ~12.dp, the mockup's 11px
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (up) Blush else Color.Transparent)
            .border(Dimens.HairlineWidth, if (up) Ember.copy(alpha = 0.4f) else Hairline, shape)
            .padding(horizontal = Dimens.Unit * 3, vertical = Dimens.Unit * 2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(Dimens.Unit * 3)
                .border(Dimens.HairlineWidth, InkLow, RoundedCornerShape(percent = 50)),
        )
        Spacer(Modifier.width(Dimens.Unit * 2))
        Text(
            title,
            style = CruxType.Secondary,
            color = InkMid,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(Dimens.Unit * 2))
        Text(tag, style = CruxType.Data, color = if (up) Ember else InkLow)
    }
}

/** The right-aligned "approve all" pill (mockup .approveall): ember mono text in an ember-ish outline. */
@Composable
private fun ApproveAllPill(onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        Modifier
            .clip(RoundedCornerShape(Dimens.RadiusPill))
            .border(Dimens.HairlineWidth, Ember.copy(alpha = 0.4f), RoundedCornerShape(Dimens.RadiusPill))
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = Dimens.Unit * 3, vertical = Dimens.Unit * 1.5f),
    ) {
        Text(Copy.REVIEW_APPROVE_ALL, style = CruxType.Data, color = Ember)
    }
}

private val DayTagFmt = DateTimeFormatter.ofPattern("EEE", Locale.ENGLISH)

/** A short lowercase weekday for a due timestamp, e.g. "tue" — the mono tag on the rising row. */
private fun dayTag(millis: Long): String =
    Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()
        .format(DayTagFmt).lowercase()

@Composable
private fun ProposalCard(proposal: ReviewProposal, onFile: () -> Unit, onDismiss: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Dimens.RadiusCard))
            .background(Surface)
            .border(Dimens.HairlineWidth, Hairline, RoundedCornerShape(Dimens.RadiusCard))
            .padding(Dimens.Unit * 4),
    ) {
        Text(
            "${Copy.REVIEW_CARD_AI_FILED} · # ${proposal.projectName}".uppercase(),
            style = CruxType.Eyebrow,
            color = InkLow,
        )
        Spacer(Modifier.height(Dimens.Unit * 2))
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
