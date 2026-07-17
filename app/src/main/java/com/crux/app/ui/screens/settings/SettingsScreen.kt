package com.crux.app.ui.screens.settings

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.crux.app.ui.Copy
import com.crux.app.ui.SettingsViewModel
import com.crux.app.ui.components.CruxIcons
import com.crux.app.ui.theme.Cream
import com.crux.app.ui.theme.CruxType
import com.crux.app.ui.theme.Dimens
import com.crux.app.ui.theme.Garnet
import com.crux.app.ui.theme.InkHi
import com.crux.app.ui.theme.InkLow
import com.crux.app.ui.theme.InkMid
import com.crux.app.ui.theme.LocalVoid
import com.crux.app.ui.theme.Overdue
import com.crux.app.ui.theme.Overlay
import com.crux.app.ui.theme.Surface
import kotlin.math.abs

/**
 * Settings (a pushed screen, ui-ux-decisions.md). Phase 1 ships the two appearance controls the
 * owner asked for (OLED deep mode, text size) and the hard reset. Notification toggles and backup
 * entry points arrive with their own slices.
 */
@Composable
fun SettingsScreen(vm: SettingsViewModel, onBack: () -> Unit) {
    val deep by vm.deepMode.collectAsStateWithLifecycle()
    val fontScale by vm.fontScale.collectAsStateWithLifecycle()
    var confirmingReset by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .background(LocalVoid.current)
            .verticalScroll(rememberScrollState())
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
        Text(Copy.SETTINGS_TITLE, style = CruxType.Display, color = InkHi)
        Spacer(Modifier.height(Dimens.GroupGap))

        // appearance
        SectionLabel(Copy.SETTINGS_APPEARANCE)
        ToggleRow(
            title = Copy.SETTINGS_DEEP,
            subtitle = Copy.SETTINGS_DEEP_SUB,
            checked = deep,
            onCheckedChange = vm::setDeepMode,
        )
        Spacer(Modifier.height(Dimens.Unit * 4))
        Text(Copy.SETTINGS_TEXT_SIZE, style = CruxType.Body, color = InkHi)
        Spacer(Modifier.height(Dimens.Unit * 2))
        Row(horizontalArrangement = Arrangement.spacedBy(Dimens.Unit * 2)) {
            SettingsViewModel.FONT_STEPS.forEach { (label, scale) ->
                Chip(
                    label = label,
                    selected = abs(fontScale - scale) < 0.01f,
                ) { vm.setFontScale(scale) }
            }
        }
        Spacer(Modifier.height(Dimens.GroupGap))

        // data
        SectionLabel(Copy.SETTINGS_DATA)
        if (!confirmingReset) {
            DangerRow(
                title = Copy.SETTINGS_RESET,
                subtitle = Copy.SETTINGS_RESET_SUB,
                onClick = { confirmingReset = true },
            )
        } else {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Dimens.RadiusCard))
                    .background(Surface)
                    .padding(Dimens.Unit * 4),
            ) {
                Text(Copy.SETTINGS_RESET_WARN, style = CruxType.Body, color = InkHi)
                Spacer(Modifier.height(Dimens.Unit * 4))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    PillButton(Copy.SETTINGS_CANCEL, filled = false) { confirmingReset = false }
                    Spacer(Modifier.width(Dimens.Unit * 2))
                    PillButton(Copy.SETTINGS_RESET_CONFIRM, filled = true) {
                        confirmingReset = false
                        vm.hardReset()
                        onBack()
                    }
                }
            }
        }
        Spacer(Modifier.height(Dimens.GroupGap))

        Text(Copy.SETTINGS_LATER, style = CruxType.Secondary, color = InkLow)
        Spacer(Modifier.height(Dimens.GroupGap))
    }
}

@Composable
private fun SectionLabel(label: String) {
    Text(label.uppercase(), style = CruxType.Eyebrow, color = InkLow)
    Spacer(Modifier.height(Dimens.Unit * 3))
}

@Composable
private fun ToggleRow(title: String, subtitle: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = CruxType.Body, color = InkHi)
            Text(subtitle, style = CruxType.Secondary, color = InkMid)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Cream,
                checkedTrackColor = Garnet,
                uncheckedThumbColor = InkMid,
                uncheckedTrackColor = Surface,
                uncheckedBorderColor = InkLow,
            ),
        )
    }
}

@Composable
private fun DangerRow(title: String, subtitle: String, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Dimens.RadiusCard))
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(vertical = Dimens.Unit * 2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = CruxType.Body, color = Overdue)
            Text(subtitle, style = CruxType.Secondary, color = InkMid)
        }
    }
}

@Composable
private fun Chip(label: String, selected: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        Modifier
            .clip(RoundedCornerShape(Dimens.RadiusPill))
            .background(if (selected) Overlay else Surface)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = Dimens.Unit * 4, vertical = Dimens.Unit * 2),
    ) {
        Text(label, style = CruxType.Action, color = if (selected) InkHi else InkMid)
    }
}

@Composable
private fun PillButton(label: String, filled: Boolean, onClick: () -> Unit) {
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
