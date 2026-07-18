package com.crux.app.ui.screens.settings

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.first
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.crux.app.data.SettingsRepository
import com.crux.app.intelligence.LlmProvider
import com.crux.app.ui.Copy
import com.crux.app.ui.SettingsViewModel
import com.crux.app.ui.components.CruxIcons
import com.crux.app.ui.theme.Cream
import com.crux.app.ui.components.LocalVoice
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
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import com.crux.app.voice.VoiceController
import com.crux.app.voice.VoiceModel
import com.crux.app.voice.VoiceState
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Settings (a pushed screen, ui-ux-decisions.md). Appearance (OLED deep, text size), notifications
 * (three toggles with configurable morning/wrap times), and the hard reset. Backup entry points
 * arrive with their own slice.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    vm: SettingsViewModel,
    onBack: () -> Unit,
    onOpenHistory: () -> Unit,
    focusAi: Boolean = false,
) {
    val deep by vm.deepMode.collectAsStateWithLifecycle()
    val fontScale by vm.fontScale.collectAsStateWithLifecycle()
    val homeCount by vm.homeCount.collectAsStateWithLifecycle()
    val notif by vm.notifications.collectAsStateWithLifecycle()
    val archived by vm.archivedCount.collectAsStateWithLifecycle()
    val aiEnabled by vm.aiEnabled.collectAsStateWithLifecycle()
    val aiProvider by vm.aiProvider.collectAsStateWithLifecycle()
    val aiHasKey by vm.hasKey.collectAsStateWithLifecycle()
    val savedKeys by vm.savedKeys.collectAsStateWithLifecycle()
    var showKeySheet by remember { mutableStateOf(false) }
    var confirmingReset by remember { mutableStateOf(false) }
    var purgeArmed by remember { mutableStateOf(false) }
    var showMorningPicker by remember { mutableStateOf(false) }
    var showWrapPicker by remember { mutableStateOf(false) }

    LaunchedEffect(purgeArmed) {
        if (purgeArmed) {
            kotlinx.coroutines.delay(3000)
            purgeArmed = false
        }
    }

    // When opened from the AI status icon, scroll the intelligence section near the top. We measure in
    // ROOT (window) coordinates to sidestep the scroll container's internal layout nodes: the section's
    // content offset = (aiTop - columnTop on screen) + current scroll. One-shot via didFocus.
    val scrollState = rememberScrollState()
    var columnTop by remember { mutableStateOf(0f) }
    var aiTop by remember { mutableStateOf(0f) }
    // Keyed ONLY on focusAi: wait (via snapshotFlow) for the section to be laid out, then scroll once.
    // Keying on the measured positions would relaunch — and cancel — the animation as they settle.
    LaunchedEffect(focusAi) {
        if (focusAi) {
            val top = snapshotFlow { aiTop }.first { it > 0f }
            val target = (top - columnTop + scrollState.value - 32f).coerceAtLeast(0f)
            scrollState.animateScrollTo(target.toInt())
        }
    }

    val context = LocalContext.current
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { /* granted or not; digests simply stay silent until granted */ }
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri -> uri?.let { vm.export(context.contentResolver, it) } }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let { vm.import(context.contentResolver, it) } }
    fun ensureNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(LocalVoid.current)
            .onGloballyPositioned { columnTop = it.positionInRoot().y }
            .verticalScroll(scrollState)
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
        Spacer(Modifier.height(Dimens.Unit * 5))
        StepperRow(
            title = Copy.SETTINGS_HOME_COUNT,
            subtitle = Copy.SETTINGS_HOME_COUNT_SUB,
            value = homeCount,
            min = SettingsRepository.HOME_COUNT_MIN,
            max = SettingsRepository.HOME_COUNT_MAX,
            onChange = vm::setHomeCount,
        )
        Spacer(Modifier.height(Dimens.GroupGap))

        // notifications (times are configurable below)
        SectionLabel(Copy.SETTINGS_NOTIFICATIONS)
        NotifRow(
            title = Copy.NOTIF_MORNING,
            subtitle = Copy.NOTIF_MORNING_SUB,
            checked = notif.morningEnabled,
            time = minutesLabel(notif.morningMinutes),
            onToggle = { on -> vm.setMorning(on, notif.morningMinutes); if (on) ensureNotificationPermission() },
            onTimeClick = { showMorningPicker = true },
        )
        Spacer(Modifier.height(Dimens.Unit * 4))
        NotifRow(
            title = Copy.NOTIF_DUE,
            subtitle = Copy.NOTIF_DUE_SUB,
            checked = notif.dueEnabled,
            time = null,
            onToggle = { on -> vm.setDue(on); if (on) ensureNotificationPermission() },
            onTimeClick = {},
        )
        Spacer(Modifier.height(Dimens.Unit * 4))
        NotifRow(
            title = Copy.NOTIF_WRAP,
            subtitle = Copy.NOTIF_WRAP_SUB,
            checked = notif.wrapEnabled,
            time = minutesLabel(notif.wrapMinutes),
            onToggle = { on -> vm.setWrap(on, notif.wrapMinutes); if (on) ensureNotificationPermission() },
            onTimeClick = { showWrapPicker = true },
        )
        Spacer(Modifier.height(Dimens.GroupGap))

        // intelligence (the optional AI layer; off by default). Toggling on with no key opens the
        // BYOK sheet rather than latching the switch, so "on" always means "actually working".
        SectionLabel(
            Copy.SETTINGS_AI,
            Modifier.onGloballyPositioned { aiTop = it.positionInRoot().y },
        )
        ToggleRow(
            title = Copy.SETTINGS_AI_ASSIST,
            subtitle = if (aiHasKey && aiProvider != null) {
                "${aiProvider!!.displayName} · ${Copy.AI_KEY_SET}"
            } else {
                Copy.SETTINGS_AI_ASSIST_SUB
            },
            checked = aiEnabled && aiHasKey,
            onCheckedChange = { on ->
                if (on && !aiHasKey) showKeySheet = true else vm.setAiEnabled(on)
            },
        )
        if (aiHasKey) {
            Spacer(Modifier.height(Dimens.Unit * 4))
            NavRow(
                title = Copy.AI_KEY_TITLE,
                subtitle = Copy.AI_KEY_REMOVE,
                onClick = { showKeySheet = true },
            )
        }
        // voice (phase 4): the on-device model lives here — set up, switch, or remove. Independent of
        // AI assist above; voice needs no key and runs fully offline once a model is downloaded.
        LocalVoice.current?.let { voice ->
            Spacer(Modifier.height(Dimens.Unit * 4))
            VoiceRow(voice)
        }
        Spacer(Modifier.height(Dimens.GroupGap))

        // data
        SectionLabel(Copy.SETTINGS_DATA)
        NavRow(
            title = Copy.SETTINGS_HISTORY,
            subtitle = Copy.SETTINGS_HISTORY_SUB,
            onClick = onOpenHistory,
        )
        Spacer(Modifier.height(Dimens.Unit * 4))
        NavRow(
            title = Copy.SETTINGS_EXPORT,
            subtitle = Copy.SETTINGS_EXPORT_SUB,
            onClick = { exportLauncher.launch(Copy.BACKUP_FILENAME) },
        )
        Spacer(Modifier.height(Dimens.Unit * 4))
        NavRow(
            title = Copy.SETTINGS_IMPORT,
            subtitle = Copy.SETTINGS_IMPORT_SUB,
            onClick = { importLauncher.launch(arrayOf("application/json")) },
        )
        Spacer(Modifier.height(Dimens.Unit * 4))
        if (archived > 0) {
            DangerRow(
                title = if (purgeArmed) "clear $archived archived? tap again" else Copy.SETTINGS_CLEAR_ARCHIVED,
                subtitle = "$archived archived · frees their names, permanent",
                onClick = {
                    if (purgeArmed) {
                        vm.clearArchived()
                        purgeArmed = false
                    } else {
                        purgeArmed = true
                    }
                },
            )
            Spacer(Modifier.height(Dimens.Unit * 4))
        }
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
    }

    if (showMorningPicker) {
        TimePickerDialog(
            initialMinutes = notif.morningMinutes,
            onDismiss = { showMorningPicker = false },
            onConfirm = { minutes -> vm.setMorning(notif.morningEnabled, minutes); showMorningPicker = false },
        )
    }
    if (showWrapPicker) {
        TimePickerDialog(
            initialMinutes = notif.wrapMinutes,
            onDismiss = { showWrapPicker = false },
            onConfirm = { minutes -> vm.setWrap(notif.wrapEnabled, minutes); showWrapPicker = false },
        )
    }
    if (showKeySheet) {
        ApiKeySheet(
            current = aiProvider,
            savedKeys = savedKeys,
            onSave = { provider, key -> vm.saveKey(provider, key); showKeySheet = false },
            onUse = { provider -> vm.useProvider(provider); showKeySheet = false },
            onRemove = { provider -> vm.removeKey(provider) }, // stay open so the sheet reflects the removal
            onDismiss = { showKeySheet = false },
        )
    }
}

/**
 * The bring-your-own-key sheet (phase 3). Rides at the bottom for thumb reach. Pick a provider; keys
 * are kept per provider, so several can be saved at once and the owner switches which one parses. A
 * provider that already has a key shows a masked preview (head + last 4, never the raw secret) with a
 * minus to remove just that key, and a "use this key" action when it is not the active one. Pasting a
 * new key saves it and makes that provider active.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ApiKeySheet(
    current: LlmProvider?,
    savedKeys: Map<String, String>,
    onSave: (LlmProvider, String) -> Unit,
    onUse: (LlmProvider) -> Unit,
    onRemove: (LlmProvider) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState()
    val uriHandler = LocalUriHandler.current
    var provider by remember { mutableStateOf(current ?: LlmProvider.GEMINI) }
    var key by remember { mutableStateOf("") }
    val savedMasked = savedKeys[provider.id]

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Surface,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = Dimens.ScreenMargin)
                .padding(bottom = Dimens.GroupGap),
        ) {
            Text(Copy.AI_KEY_TITLE, style = CruxType.Body, color = InkHi)
            Spacer(Modifier.height(Dimens.Unit * 2))
            Text(Copy.AI_PROVIDER_PICK, style = CruxType.Eyebrow, color = InkLow)
            Spacer(Modifier.height(Dimens.Unit * 2))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(Dimens.Unit * 2),
                verticalArrangement = Arrangement.spacedBy(Dimens.Unit * 2),
            ) {
                LlmProvider.entries.forEach { p ->
                    Chip(label = p.displayName, selected = provider == p, saved = savedKeys.containsKey(p.id)) {
                        provider = p
                        key = ""
                    }
                }
            }
            Spacer(Modifier.height(Dimens.Unit * 4))

            // the saved key for the selected provider (if any): masked preview + a minus to remove it.
            if (savedMasked != null) {
                Text(Copy.AI_KEY_SAVED_LABEL, style = CruxType.Eyebrow, color = InkLow)
                Spacer(Modifier.height(Dimens.Unit * 2))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(Dimens.RadiusShell))
                        .background(Overlay)
                        .padding(start = 16.dp, end = Dimens.Unit, top = Dimens.Unit, bottom = Dimens.Unit),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(savedMasked, style = CruxType.Data, color = InkMid, modifier = Modifier.weight(1f))
                    MinusButton { onRemove(provider) }
                }
                if (current != provider) {
                    Spacer(Modifier.height(Dimens.Unit * 3))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        PillButton(Copy.AI_KEY_USE, filled = true) { onUse(provider) }
                    }
                }
                Spacer(Modifier.height(Dimens.Unit * 4))
                Text(Copy.AI_KEY_REPLACE, style = CruxType.Secondary, color = InkMid)
                Spacer(Modifier.height(Dimens.Unit * 2))
            }

            // the paste field, styled like the omnibar's input well
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Dimens.RadiusShell))
                    .background(LocalVoid.current)
                    .padding(horizontal = 16.dp, vertical = 15.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.weight(1f)) {
                    if (key.isEmpty()) {
                        Text(Copy.AI_KEY_PLACEHOLDER, style = CruxType.Body, color = InkLow)
                    }
                    BasicTextField(
                        value = key,
                        onValueChange = { key = it },
                        textStyle = CruxType.Body.copy(color = InkHi),
                        singleLine = true,
                        cursorBrush = SolidColor(Garnet),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            Spacer(Modifier.height(Dimens.Unit * 2))
            Text(
                text = "${provider.keyHint} · ${Copy.AI_KEY_GET}",
                style = CruxType.Secondary,
                color = InkMid,
                modifier = Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { uriHandler.openUri(provider.keyUrl) },
            )
            Spacer(Modifier.height(Dimens.GroupGap))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                PillButton(Copy.AI_KEY_SAVE, filled = true) {
                    if (key.isNotBlank()) onSave(provider, key)
                }
            }
        }
    }
}

/** The round − well that removes a stored key. Garnet-tinted glyph on the sheet's raised chip. */
@Composable
private fun MinusButton(onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        Modifier
            .size(36.dp)
            .clip(RoundedCornerShape(Dimens.RadiusPill))
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(CruxIcons.Minus, contentDescription = "remove key", tint = Overdue, modifier = Modifier.size(20.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimePickerDialog(initialMinutes: Int, onDismiss: () -> Unit, onConfirm: (Int) -> Unit) {
    val state = rememberTimePickerState(
        initialHour = initialMinutes / 60,
        initialMinute = initialMinutes % 60,
        is24Hour = false,
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { onConfirm(state.hour * 60 + state.minute) }) { Text(Copy.DETAIL_DIALOG_OK) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(Copy.DETAIL_DIALOG_CANCEL) } },
    ) {
        Box(Modifier.padding(Dimens.ScreenMargin), contentAlignment = Alignment.Center) {
            TimePicker(state = state)
        }
    }
}

/**
 * A bounded numeric stepper: label + subtitle on the left, [− N +] on the right. The mono numeral
 * (CruxType.Data) reads as a count, not prose. The − / + wells dim to InkLow at their bounds so the
 * limit is felt, not enforced by a silent no-op.
 */
@Composable
private fun StepperRow(
    title: String,
    subtitle: String,
    value: Int,
    min: Int,
    max: Int,
    onChange: (Int) -> Unit,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = CruxType.Body, color = InkHi)
            Text(subtitle, style = CruxType.Secondary, color = InkMid)
        }
        StepButton(icon = CruxIcons.Minus, enabled = value > min) { onChange(value - 1) }
        Text(
            text = "$value",
            style = CruxType.Data,
            color = InkHi,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(min = 28.dp).padding(horizontal = Dimens.Unit),
        )
        StepButton(icon = CruxIcons.Add, enabled = value < max) { onChange(value + 1) }
    }
}

@Composable
private fun StepButton(icon: androidx.compose.ui.graphics.vector.ImageVector, enabled: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        Modifier
            .size(36.dp)
            .clip(RoundedCornerShape(Dimens.RadiusPill))
            .background(Surface)
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (enabled) InkMid else InkLow.copy(alpha = 0.4f),
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
private fun NotifRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    time: String?,
    onToggle: (Boolean) -> Unit,
    onTimeClick: () -> Unit,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = CruxType.Body, color = InkHi)
            Text(subtitle, style = CruxType.Secondary, color = InkMid)
        }
        if (time != null && checked) {
            Chip(label = time, selected = true, onClick = onTimeClick)
            Spacer(Modifier.width(Dimens.Unit * 2))
        }
        Switch(checked = checked, onCheckedChange = onToggle, colors = cruxSwitchColors())
    }
}

@Composable
private fun cruxSwitchColors() = SwitchDefaults.colors(
    checkedThumbColor = Cream,
    checkedTrackColor = Garnet,
    uncheckedThumbColor = InkMid,
    uncheckedTrackColor = Surface,
    uncheckedBorderColor = InkLow,
)

private val MinuteFmt = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)
private fun minutesLabel(minutes: Int): String =
    LocalTime.of(minutes / 60, minutes % 60).format(MinuteFmt).lowercase(Locale.ENGLISH)

@Composable
private fun SectionLabel(label: String, modifier: Modifier = Modifier) {
    Text(label.uppercase(), style = CruxType.Eyebrow, color = InkLow, modifier = modifier)
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
        Switch(checked = checked, onCheckedChange = onCheckedChange, colors = cruxSwitchColors())
    }
}

@Composable
private fun NavRow(title: String, subtitle: String, onClick: () -> Unit) {
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
            Text(title, style = CruxType.Body, color = InkHi)
            Text(subtitle, style = CruxType.Secondary, color = InkMid)
        }
    }
}

/**
 * Voice model management (phase 4). Reads the shared [VoiceController] so a download started from the
 * omnibar shows its progress here too. Offers a pill to get (or switch to) every model that is not the
 * current one, plus "remove" when one is installed — switching downloads the new model first and only
 * then reclaims the old files, so a failed switch never leaves the user with no voice. All models are
 * multilingual (they recognise non-English place names); [VoiceModel.HIGH] is the larger opt-in
 * high-accuracy tier the owner asked to expose here.
 */
@Composable
private fun VoiceRow(voice: VoiceController) {
    val state by voice.state.collectAsStateWithLifecycle()
    val installed by voice.installed.collectAsStateWithLifecycle()
    val status = when (val s = state) {
        is VoiceState.Downloading -> "${Copy.VOICE_DOWNLOADING} · ${(s.fraction * 100).roundToInt()}%"
        VoiceState.Preparing -> Copy.VOICE_PREPARING
        is VoiceState.Failed -> s.message
        else -> when (installed) {
            VoiceModel.LIGHT -> Copy.SETTINGS_VOICE_LIGHT_READY
            VoiceModel.CAPABLE -> Copy.SETTINGS_VOICE_CAPABLE_READY
            VoiceModel.HIGH -> Copy.SETTINGS_VOICE_HIGH_READY
            null -> Copy.SETTINGS_VOICE_NONE
        }
    }
    Column(Modifier.fillMaxWidth()) {
        Text(Copy.SETTINGS_VOICE, style = CruxType.Body, color = InkHi)
        Text(status, style = CruxType.Secondary, color = InkMid)
        val busy = state is VoiceState.Downloading || state is VoiceState.Preparing
        if (!busy) {
            Spacer(Modifier.height(Dimens.Unit * 3))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(Dimens.Unit * 2),
                verticalArrangement = Arrangement.spacedBy(Dimens.Unit * 2),
            ) {
                VoiceModel.entries.filter { it != installed }.forEach { model ->
                    val label = if (installed == null) voiceLabel(model)
                    else "${Copy.SETTINGS_VOICE_SWITCH} ${voiceLabel(model)}"
                    PillButton(label, filled = false) {
                        voice.download(model, replaceOthers = installed != null)
                    }
                }
                installed?.let { current ->
                    PillButton(Copy.SETTINGS_VOICE_REMOVE, filled = false) { voice.remove(current) }
                }
            }
        }
    }
}

private fun voiceLabel(model: VoiceModel): String = when (model) {
    VoiceModel.LIGHT -> Copy.VOICE_LIGHT
    VoiceModel.CAPABLE -> Copy.VOICE_CAPABLE
    VoiceModel.HIGH -> Copy.VOICE_HIGH
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
private fun Chip(label: String, selected: Boolean, saved: Boolean = false, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Row(
        Modifier
            .clip(RoundedCornerShape(Dimens.RadiusPill))
            .background(if (selected) Overlay else Surface)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = Dimens.Unit * 4, vertical = Dimens.Unit * 2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (saved) {
            Box(Modifier.size(6.dp).clip(CircleShape).background(Garnet))
            Spacer(Modifier.width(Dimens.Unit))
        }
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
