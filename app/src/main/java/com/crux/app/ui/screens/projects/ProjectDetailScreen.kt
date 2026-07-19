package com.crux.app.ui.screens.projects

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.crux.app.ui.Copy
import com.crux.app.ui.ProjectDetailViewModel
import com.crux.app.ui.theme.Cream
import com.crux.app.ui.theme.CruxType
import com.crux.app.ui.theme.Dimens
import com.crux.app.ui.theme.Garnet
import com.crux.app.ui.theme.InkHi
import com.crux.app.ui.theme.InkLow
import com.crux.app.ui.theme.InkMid
import com.crux.app.ui.theme.LocalVoid
import com.crux.app.ui.theme.Overdue
import com.crux.app.ui.theme.Raised
import kotlinx.coroutines.delay

/**
 * The project detail screen (pushed from a tapped project row). Two edits: the name, and a free-text
 * description of what the project holds. The description is the whole point — it is fed to the LLM so
 * new tasks land in the right project. Both commit on blur, mirroring task detail's free-text fields.
 */
@Composable
fun ProjectDetailScreen(vm: ProjectDetailViewModel, onBack: () -> Unit) {
    val project by vm.project.collectAsStateWithLifecycle()
    val focusManager = LocalFocusManager.current
    var showDuplicate by remember { mutableStateOf(false) }

    LaunchedEffect(vm) { vm.errors.collect { showDuplicate = true } }
    LaunchedEffect(showDuplicate) {
        if (showDuplicate) {
            delay(2500)
            showDuplicate = false
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(LocalVoid.current)
            .statusBarsPadding()
            .padding(horizontal = Dimens.ScreenMargin)
            .verticalScroll(rememberScrollState()),
    ) {
        Spacer(Modifier.height(Dimens.ScreenMargin))
        // back breadcrumb (mockup 05 chrome), mirroring task detail.
        val backInteraction = remember { MutableInteractionSource() }
        Text(
            text = Copy.DETAIL_BACK.uppercase(),
            style = CruxType.Eyebrow,
            color = InkMid,
            modifier = Modifier
                .clip(RoundedCornerShape(Dimens.RadiusPill))
                .clickable(interactionSource = backInteraction, indication = null, onClick = onBack)
                .padding(vertical = Dimens.Unit * 2, horizontal = Dimens.Unit),
        )
        Spacer(Modifier.height(Dimens.Unit * 3))

        val p = project
        if (p == null) {
            // brief flash while the project loads off the active list.
            Spacer(Modifier.height(Dimens.GroupGap))
            return@Column
        }

        Text(Copy.PROJECT_DETAIL_EYEBROW, style = CruxType.Eyebrow, color = InkLow)
        Spacer(Modifier.height(Dimens.Unit * 2))

        // the project name, editable in place (Display scale). commits on blur; a clash flashes below.
        var nameDraft by remember(p.id, p.name) { mutableStateOf(p.name) }
        BasicTextField(
            value = nameDraft,
            onValueChange = { nameDraft = it },
            textStyle = CruxType.Display.copy(color = InkHi),
            singleLine = true,
            cursorBrush = SolidColor(Garnet),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { if (!it.isFocused) vm.rename(nameDraft) },
        )
        if (showDuplicate) {
            Spacer(Modifier.height(Dimens.Unit))
            Text(Copy.PROJECT_DUPLICATE, style = CruxType.Secondary, color = Overdue)
        }
        Spacer(Modifier.height(Dimens.GroupGap))

        // the description: the context the assistant reads to file new tasks here.
        Text(Copy.PROJECT_DESC_LABEL, style = CruxType.Eyebrow, color = InkLow)
        Spacer(Modifier.height(Dimens.Unit * 2))
        var descDraft by remember(p.id, p.description) { mutableStateOf(p.description) }
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(Dimens.RadiusCard))
                .background(Raised)
                .padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
            if (descDraft.isEmpty()) {
                Text(Copy.PROJECT_DESC_PLACEHOLDER, style = CruxType.Body, color = InkLow)
            }
            BasicTextField(
                value = descDraft,
                onValueChange = { descDraft = it },
                textStyle = CruxType.Body.copy(color = InkHi),
                cursorBrush = SolidColor(Garnet),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 96.dp)
                    .onFocusChanged { if (!it.isFocused) vm.setDescription(descDraft) },
            )
        }
        Spacer(Modifier.height(Dimens.Unit * 2))
        Text(Copy.PROJECT_DESC_HINT, style = CruxType.Secondary, color = InkMid)

        // explicit save (both fields also commit on blur; enter inserts a newline in the description).
        val dirty = nameDraft.trim() != p.name || descDraft.trim() != p.description
        if (dirty) {
            Spacer(Modifier.height(Dimens.Unit * 4))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                SaveButton {
                    vm.rename(nameDraft)
                    vm.setDescription(descDraft)
                    focusManager.clearFocus()
                }
            }
        }
        Spacer(Modifier.height(Dimens.GroupGap))
    }
}

/** A filled garnet "save" pill — the explicit commit for the name + description fields. */
@Composable
private fun SaveButton(onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        Modifier
            .clip(RoundedCornerShape(Dimens.RadiusPill))
            .background(Garnet)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = Dimens.Unit * 4, vertical = Dimens.Unit * 2),
    ) {
        Text(Copy.SAVE, style = CruxType.Action, color = Cream)
    }
}
