package com.crux.app.ui.screens.projects

import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.crux.app.domain.model.Project
import com.crux.app.ui.Copy
import com.crux.app.ui.ProjectsViewModel
import com.crux.app.ui.components.CruxIcons
import com.crux.app.ui.components.TabHeader
import com.crux.app.ui.theme.CruxType
import com.crux.app.ui.theme.Dimens
import com.crux.app.ui.theme.Garnet
import com.crux.app.ui.theme.InkHi
import com.crux.app.ui.theme.InkLow
import com.crux.app.ui.theme.InkMid
import com.crux.app.ui.theme.Motion
import com.crux.app.ui.theme.Overdue
import com.crux.app.ui.theme.LocalVoid
import com.crux.app.ui.theme.Raised
import kotlinx.coroutines.delay

/**
 * The projects tab: name projects and rank them. rank 1 sits at the top and carries the most weight;
 * the stack will group by this order (inbox last) once tasks can be filed. Re-ranking is up/down
 * controls inside an edit mode, not drag (ui-ux-decisions.md: drag is polish, not spine).
 */
@Composable
fun ProjectsScreen(vm: ProjectsViewModel, onOpenSettings: () -> Unit) {
    val projects by vm.active.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf(false) }
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
            .padding(horizontal = Dimens.ScreenMargin),
    ) {
        Spacer(Modifier.height(Dimens.ScreenMargin))
        TabHeader(title = Copy.TAB_PROJECTS, onOpenSettings = onOpenSettings) {
            if (projects.isNotEmpty()) {
                val interaction = remember { MutableInteractionSource() }
                Text(
                    text = if (editing) Copy.PROJECTS_DONE else Copy.PROJECTS_EDIT,
                    style = CruxType.Action,
                    color = if (editing) Garnet else InkMid,
                    modifier = Modifier
                        .clip(RoundedCornerShape(Dimens.RadiusPill))
                        .clickable(interactionSource = interaction, indication = null) {
                            editing = !editing
                        }
                        .padding(horizontal = Dimens.Unit * 2, vertical = Dimens.Unit),
                )
            }
        }
        Spacer(Modifier.height(Dimens.Unit * 3))

        if (projects.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth()) {
                Text(
                    text = Copy.EMPTY_PROJECTS,
                    style = CruxType.Passage,
                    color = InkMid,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.align(Alignment.Center),
                )
            }
        } else {
            LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
                itemsIndexed(items = projects, key = { _, p -> p.id }) { index, project ->
                    ProjectRow(
                        project = project,
                        position = index + 1,
                        editing = editing,
                        isFirst = index == 0,
                        isLast = index == projects.lastIndex,
                        onRename = { vm.rename(project, it) },
                        onArchive = { vm.archive(project) },
                        onMoveUp = { vm.moveUp(project) },
                        onMoveDown = { vm.moveDown(project) },
                        modifier = Modifier.animateItem(
                            placementSpec = spring(
                                dampingRatio = Motion.ReorderDamping,
                                visibilityThreshold = IntOffset.VisibilityThreshold,
                            ),
                        ),
                    )
                }
            }
        }

        // the create field rides at the bottom for thumb reach, mirroring the home omnibar; the
        // Scaffold's imePadding lifts it above the keyboard. The duplicate note sits just above it.
        if (showDuplicate) {
            Text(
                text = Copy.PROJECT_DUPLICATE,
                style = CruxType.Secondary,
                color = Overdue,
                modifier = Modifier.padding(horizontal = Dimens.Unit),
            )
            Spacer(Modifier.height(Dimens.Unit))
        }
        CreateField(onCreate = vm::create)
        Spacer(Modifier.height(Dimens.GroupGap))
    }
}

/** The add-a-project input: a small Raised shell, mirroring the omnibar's shape at a calmer scale. */
@Composable
private fun CreateField(onCreate: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    fun submit() {
        if (text.isNotBlank()) {
            onCreate(text)
            text = ""
        }
    }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Dimens.RadiusCard))
            .background(Raised)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = CruxIcons.Add,
            contentDescription = null,
            tint = Garnet,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f)) {
            if (text.isEmpty()) {
                Text(Copy.PROJECT_CREATE_PLACEHOLDER, style = CruxType.Body, color = InkLow)
            }
            BasicTextField(
                value = text,
                onValueChange = { text = it },
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

@Composable
private fun ProjectRow(
    project: Project,
    position: Int,
    editing: Boolean,
    isFirst: Boolean,
    isLast: Boolean,
    onRename: (String) -> Unit,
    onArchive: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = Dimens.RowMinHeight),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // positional rank, numbers-first (Chisel voice). Shows order, not the raw stored rank.
        Text(
            text = position.toString(),
            style = CruxType.Data,
            color = InkLow,
            modifier = Modifier.width(24.dp),
        )
        Spacer(Modifier.width(Dimens.Unit * 2))

        if (editing) {
            // re-key the draft on the committed name so a successful rename syncs the field.
            var draft by remember(project.id, project.name) { mutableStateOf(project.name) }
            fun commit() {
                val next = draft.trim()
                if (next.isNotEmpty() && next != project.name) onRename(next)
            }
            BasicTextField(
                value = draft,
                onValueChange = { draft = it },
                textStyle = CruxType.Body.copy(color = InkHi),
                singleLine = true,
                cursorBrush = SolidColor(Garnet),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { commit() }),
                modifier = Modifier
                    .weight(1f)
                    .onFocusChanged { if (!it.isFocused) commit() },
            )
            RowControl(CruxIcons.ChevronUp, enabled = !isFirst, onClick = onMoveUp)
            RowControl(CruxIcons.ChevronDown, enabled = !isLast, onClick = onMoveDown)
            RowControl(CruxIcons.Archive, enabled = true, onClick = onArchive)
        } else {
            Text(
                text = project.name,
                style = CruxType.Body,
                color = InkHi,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** A 40 dp tap target for the edit-mode controls; dimmed and inert when [enabled] is false. */
@Composable
private fun RowControl(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .size(40.dp)
            .then(
                if (enabled) {
                    Modifier.clickable(interactionSource = interaction, indication = null, onClick = onClick)
                } else Modifier
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = InkMid,
            modifier = Modifier
                .size(20.dp)
                .alpha(if (enabled) 1f else 0.3f),
        )
    }
}
