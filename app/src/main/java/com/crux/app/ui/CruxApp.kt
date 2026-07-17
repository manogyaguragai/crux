package com.crux.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.crux.app.CruxApplication
import com.crux.app.ui.components.CruxIcons
import com.crux.app.ui.screens.detail.TaskDetailScreen
import com.crux.app.ui.screens.home.HomeScreen
import com.crux.app.ui.screens.projects.ProjectsScreen
import com.crux.app.ui.screens.stack.StackScreen
import com.crux.app.ui.theme.CruxType
import com.crux.app.ui.theme.Dimens
import com.crux.app.ui.theme.Ember
import com.crux.app.ui.theme.Garnet
import com.crux.app.ui.theme.Hairline
import com.crux.app.ui.theme.InkHi
import com.crux.app.ui.theme.InkLow
import com.crux.app.ui.theme.Overlay
import kotlinx.coroutines.withTimeoutOrNull
import com.crux.app.ui.theme.InkMid
import com.crux.app.ui.theme.Void

/** The four tabs. Overdue, detail, and settings are pushed screens, not tabs (ui-ux-decisions.md). */
private enum class CruxTab(
    val route: String,
    val label: String,
    val glyph: ImageVector,
    val empty: String?,
) {
    Home(Copy.TAB_HOME, Copy.TAB_HOME, CruxIcons.Home, Copy.EMPTY_HOME),
    Stack(Copy.TAB_STACK, Copy.TAB_STACK, CruxIcons.Stack, Copy.EMPTY_STACK),
    Projects(Copy.TAB_PROJECTS, Copy.TAB_PROJECTS, CruxIcons.Projects, null),
    Review(Copy.TAB_REVIEW, Copy.TAB_REVIEW, CruxIcons.Review, Copy.EMPTY_REVIEW),
}

@Composable
fun CruxApp() {
    val nav = rememberNavController()
    val context = LocalContext.current
    val container = (context.applicationContext as CruxApplication).container
    val vm: TasksViewModel =
        viewModel(factory = TasksViewModel.factory(container.taskRepository, container.projectRepository))
    val projectsVm: ProjectsViewModel =
        viewModel(factory = ProjectsViewModel.factory(container.projectRepository))
    val snackbarHostState = remember { SnackbarHostState() }

    // the 5 s undo window after a completion (copy bank: "done. undo")
    LaunchedEffect(vm) {
        vm.undoEvents.collect {
            snackbarHostState.currentSnackbarData?.dismiss()
            val result = withTimeoutOrNull(5_000L) {
                snackbarHostState.showSnackbar(
                    message = Copy.SNACKBAR_DONE,
                    actionLabel = Copy.SNACKBAR_UNDO,
                    duration = SnackbarDuration.Indefinite,
                )
            }
            if (result == SnackbarResult.ActionPerformed) vm.undoLast()
        }
    }

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            // imePadding lifts the whole shell (tab bar + the omnibar riding above it) above the
            // soft keyboard, so capture stays fully visible while typing (adjustResize in manifest).
            modifier = Modifier.imePadding(),
            containerColor = MaterialTheme.colorScheme.background,
            bottomBar = { CruxTabBar(nav) },
        ) { innerPadding ->
            val openTask: (Long) -> Unit = { id -> nav.navigate("task/$id") }
            NavHost(
                navController = nav,
                startDestination = CruxTab.Home.route,
                modifier = Modifier.padding(innerPadding),
            ) {
                CruxTab.entries.forEach { tab ->
                    composable(tab.route) {
                        when (tab) {
                            CruxTab.Home -> HomeScreen(vm, onOpenTask = openTask)
                            CruxTab.Stack -> StackScreen(vm, onOpenTask = openTask)
                            CruxTab.Projects -> ProjectsScreen(projectsVm)
                            else -> EmptyTabScreen(tab)
                        }
                    }
                }
                composable(
                    route = "task/{taskId}",
                    arguments = listOf(navArgument("taskId") { type = NavType.LongType }),
                ) { entry ->
                    val id = entry.arguments?.getLong("taskId") ?: return@composable
                    val detailVm: TaskDetailViewModel = viewModel(
                        factory = TaskDetailViewModel.factory(
                            container.taskRepository,
                            container.projectRepository,
                            id,
                        ),
                    )
                    TaskDetailScreen(vm = detailVm, onBack = { nav.popBackStack() })
                }
            }
        }
        // the undo snackbar rides at the top so it never blocks the omnibar or capture
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(horizontal = Dimens.ScreenMargin, vertical = Dimens.Unit * 2),
        ) { data ->
            Snackbar(
                snackbarData = data,
                containerColor = Overlay,
                contentColor = InkHi,
                actionColor = Ember,
                shape = RoundedCornerShape(Dimens.RadiusCard),
            )
        }
    }
}

@Composable
private fun CruxTabBar(nav: NavController) {
    val backStackEntry by nav.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    // docked shell: void ground, a single top hairline. the tab row keeps its full height;
    // the system nav-bar inset is added as a spacer BELOW it, never subtracted from the row.
    Column(Modifier.fillMaxWidth().background(Void)) {
        Box(Modifier.fillMaxWidth().height(Dimens.HairlineWidth).background(Hairline))
        Row(
            Modifier
                .fillMaxWidth()
                .height(Dimens.TabBarHeight),
        ) {
            CruxTab.entries.forEach { tab ->
                CruxTabItem(
                    tab = tab,
                    selected = currentRoute == tab.route,
                    onClick = {
                        if (currentRoute != tab.route) {
                            nav.navigate(tab.route) {
                                popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    },
                )
            }
        }
        Spacer(Modifier.fillMaxWidth().windowInsetsBottomHeight(WindowInsets.navigationBars))
    }
}

@Composable
private fun RowScope.CruxTabItem(tab: CruxTab, selected: Boolean, onClick: () -> Unit) {
    val tint = if (selected) Ember else InkLow
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        // the active-tab dash: garnet, rounded at the bottom (one of the four red places).
        if (selected) {
            Box(
                Modifier
                    .align(Alignment.TopCenter)
                    .width(Dimens.ActiveTabDashWidth)
                    .height(Dimens.ActiveTabDashHeight)
                    .clip(RoundedCornerShape(bottomStart = 3.dp, bottomEnd = 3.dp))
                    .background(Garnet),
            )
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Dimens.Unit),
        ) {
            Icon(
                imageVector = tab.glyph,
                contentDescription = tab.label,
                tint = tint,
                modifier = Modifier.size(19.dp),
            )
            Text(text = tab.label.uppercase(), style = CruxType.Eyebrow, color = tint)
        }
    }
}

/** Phase 0: a themed empty surface per tab. Real screens replace these in phase 1. */
@Composable
private fun EmptyTabScreen(tab: CruxTab) {
    Column(
        Modifier
            .fillMaxSize()
            .background(Void)
            .padding(horizontal = Dimens.ScreenMargin),
    ) {
        Box(Modifier.fillMaxWidth().height(Dimens.ScreenMargin))
        Text(text = tab.label, style = CruxType.Display, color = InkHi)
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            tab.empty?.let {
                Text(text = it, style = CruxType.Passage, color = InkMid, textAlign = TextAlign.Center)
            }
        }
    }
}
