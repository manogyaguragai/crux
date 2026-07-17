package com.crux.app.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.crux.app.CruxApplication
import com.crux.app.ui.Copy
import com.crux.app.ui.components.Omnibar
import com.crux.app.ui.components.TaskRow
import com.crux.app.ui.theme.CruxType
import com.crux.app.ui.theme.Dimens
import com.crux.app.ui.theme.InkMid
import com.crux.app.ui.theme.Void

/**
 * Home: the omnibar riding low, the top 3 open tasks above it (data-model.md).
 * The nudge count and the full row (hold + meta) arrive in later phase 1 slices.
 */
@Composable
fun HomeScreen() {
    val context = LocalContext.current
    val container = (context.applicationContext as CruxApplication).container
    val vm: HomeViewModel = viewModel(factory = HomeViewModel.factory(container.taskRepository))
    val state by vm.uiState.collectAsStateWithLifecycle()

    Column(
        Modifier
            .fillMaxSize()
            .background(Void)
            .padding(horizontal = Dimens.ScreenMargin),
    ) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (state.top.isEmpty()) {
                Text(
                    text = Copy.EMPTY_HOME,
                    style = CruxType.Passage,
                    color = InkMid,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.align(Alignment.Center),
                )
            } else {
                Column(Modifier.align(Alignment.BottomStart).fillMaxWidth()) {
                    state.top.forEach { TaskRow(it) }
                }
            }
        }
        Omnibar(onCapture = vm::capture, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(Dimens.GroupGap))
    }
}
