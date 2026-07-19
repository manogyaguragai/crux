package com.crux.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.crux.app.ui.components.LocalAiPresence
import com.crux.app.ui.components.LocalOpenAiSettings
import com.crux.app.ui.components.LocalQueueBar
import com.crux.app.ui.components.LocalVoice
import com.crux.app.ui.components.QueueBar
import com.crux.app.ui.components.QueueDropdown
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
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
import com.crux.app.ui.screens.history.HistoryScreen
import com.crux.app.ui.screens.home.HomeScreen
import com.crux.app.ui.screens.overdue.OverdueScreen
import com.crux.app.ui.screens.projects.ProjectDetailScreen
import com.crux.app.ui.screens.projects.ProjectsScreen
import com.crux.app.ui.screens.review.ReviewScreen
import com.crux.app.ui.screens.settings.SettingsScreen
import com.crux.app.ui.screens.stack.StackScreen
import com.crux.app.ui.theme.Cream
import com.crux.app.ui.theme.CruxType
import com.crux.app.ui.theme.Dimens
import com.crux.app.ui.theme.Ember
import com.crux.app.ui.theme.Garnet
import com.crux.app.ui.theme.Hairline
import com.crux.app.ui.theme.InkHi
import com.crux.app.ui.theme.InkLow
import com.crux.app.ui.theme.Overlay
import kotlinx.coroutines.withTimeoutOrNull
import com.crux.app.ui.theme.LocalVoid

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
        viewModel(
            factory = TasksViewModel.factory(
                container.taskRepository,
                container.projectRepository,
                container.settingsRepository,
                container.intelligence,
                container.captureQueue,
            ),
        )
    val projectsVm: ProjectsViewModel =
        viewModel(factory = ProjectsViewModel.factory(container.projectRepository, container.taskRepository))
    val settingsVm: SettingsViewModel = viewModel(factory = SettingsViewModel.factory(container))
    val reviewVm: ReviewViewModel = viewModel(factory = ReviewViewModel.factory(container))
    // what is waiting in review, for the tab badge (mockup .tbadge): the live priority nudges plus any
    // pending inbox proposals.
    val reviewNudges by reviewVm.nudges.collectAsStateWithLifecycle()
    val reviewProposals by reviewVm.proposals.collectAsStateWithLifecycle()
    val reviewWaiting = reviewNudges.size + reviewProposals.size
    val aiStatusVm: AiStatusViewModel = viewModel(factory = AiStatusViewModel.factory(container))
    val aiPresence by aiStatusVm.presence.collectAsStateWithLifecycle()
    val aiNotice by aiStatusVm.notice.collectAsStateWithLifecycle()
    val queueVm: QueueViewModel = viewModel(factory = QueueViewModel.factory(container))
    val queueActive by queueVm.activeCount.collectAsStateWithLifecycle()
    val queueFailed by queueVm.hasFailed.collectAsStateWithLifecycle()
    val queueItems by queueVm.items.collectAsStateWithLifecycle()
    var queueOpen by remember { mutableStateOf(false) }
    // hold the last message so it stays legible through the bubble's exit animation
    var lastNotice by remember { mutableStateOf("") }
    LaunchedEffect(aiNotice) { aiNotice?.let { lastNotice = it } }
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

    CompositionLocalProvider(
        LocalAiPresence provides aiPresence,
        LocalOpenAiSettings provides { nav.navigate("settings?focus=ai") },
        LocalQueueBar provides QueueBar(queueActive, queueFailed) { queueOpen = true },
        LocalVoice provides container.voiceController,
    ) {
    Box(Modifier.fillMaxSize()) {
        Scaffold(
            // imePadding lifts the whole shell (tab bar + the omnibar riding above it) above the
            // soft keyboard, so capture stays fully visible while typing (adjustResize in manifest).
            modifier = Modifier.imePadding(),
            containerColor = MaterialTheme.colorScheme.background,
            bottomBar = { CruxTabBar(nav, reviewWaiting) },
        ) { innerPadding ->
            val openTask: (Long) -> Unit = { id -> nav.navigate("task/$id") }
            val openSettings: () -> Unit = { nav.navigate("settings") }
            val openOverdue: () -> Unit = { nav.navigate("overdue") }
            NavHost(
                navController = nav,
                startDestination = CruxTab.Home.route,
                modifier = Modifier.padding(innerPadding),
            ) {
                CruxTab.entries.forEach { tab ->
                    composable(tab.route) {
                        when (tab) {
                            CruxTab.Home -> HomeScreen(vm, onOpenTask = openTask, onOpenSettings = openSettings, onOpenOverdue = openOverdue)
                            CruxTab.Stack -> StackScreen(vm, onOpenTask = openTask, onOpenSettings = openSettings)
                            CruxTab.Projects -> ProjectsScreen(projectsVm, onOpenSettings = openSettings, onOpenProject = { id -> nav.navigate("project/$id") })
                            CruxTab.Review -> ReviewScreen(reviewVm, onOpenSettings = openSettings)
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
                composable(
                    route = "project/{projectId}",
                    arguments = listOf(navArgument("projectId") { type = NavType.LongType }),
                ) { entry ->
                    val id = entry.arguments?.getLong("projectId") ?: return@composable
                    val projectDetailVm: ProjectDetailViewModel = viewModel(
                        factory = ProjectDetailViewModel.factory(container.projectRepository, id),
                    )
                    ProjectDetailScreen(vm = projectDetailVm, onBack = { nav.popBackStack() })
                }
                composable("overdue") {
                    OverdueScreen(vm = vm, onBack = { nav.popBackStack() }, onOpenTask = openTask)
                }
                composable(
                    route = "settings?focus={focus}",
                    arguments = listOf(navArgument("focus") { type = NavType.StringType; defaultValue = "" }),
                ) { entry ->
                    SettingsScreen(
                        vm = settingsVm,
                        onBack = { nav.popBackStack() },
                        onOpenHistory = { nav.navigate("history") },
                        focusAi = entry.arguments?.getString("focus") == "ai",
                    )
                }
                composable("history") {
                    val historyVm: HistoryViewModel =
                        viewModel(factory = HistoryViewModel.factory(container.taskRepository))
                    HistoryScreen(vm = historyVm, onBack = { nav.popBackStack() })
                }
            }
        }
        // an AI notice (quota, rate limit, offline…) breathes out from the status icon, top-right
        AnimatedVisibility(
            visible = aiNotice != null,
            enter = fadeIn() + scaleIn(initialScale = 0.85f),
            exit = fadeOut() + scaleOut(targetScale = 0.85f),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(top = 52.dp, end = Dimens.ScreenMargin),
        ) {
            Row(
                Modifier
                    .clip(RoundedCornerShape(Dimens.RadiusCard))
                    .background(Overlay)
                    .padding(horizontal = Dimens.Unit * 3, vertical = Dimens.Unit * 2),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(6.dp).clip(CircleShape).background(Ember))
                Spacer(Modifier.width(Dimens.Unit * 2))
                Text(lastNotice, style = CruxType.Secondary, color = InkHi)
            }
        }
        // the capture-queue dropdown: a scrim to dismiss, then the panel growing from the icon
        if (queueOpen) {
            val scrimInteraction = remember { MutableInteractionSource() }
            Box(
                Modifier
                    .fillMaxSize()
                    .clickable(interactionSource = scrimInteraction, indication = null) { queueOpen = false },
            )
        }
        AnimatedVisibility(
            visible = queueOpen,
            enter = fadeIn() + scaleIn(initialScale = 0.9f, transformOrigin = TransformOrigin(1f, 0f)),
            exit = fadeOut() + scaleOut(targetScale = 0.9f, transformOrigin = TransformOrigin(1f, 0f)),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(top = 52.dp, end = Dimens.ScreenMargin),
        ) {
            QueueDropdown(
                items = queueItems,
                onClose = { queueOpen = false },
                onRemove = queueVm::remove,
                onRetry = queueVm::retry,
                onClearFinished = queueVm::clearFinished,
            )
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
    } // CompositionLocalProvider(LocalAiPresence)
}

@Composable
private fun CruxTabBar(nav: NavController, reviewBadge: Int) {
    val backStackEntry by nav.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    // docked shell: void ground, a single top hairline. the tab row keeps its full height;
    // the system nav-bar inset is added as a spacer BELOW it, never subtracted from the row.
    Column(Modifier.fillMaxWidth().background(LocalVoid.current)) {
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
                    badge = if (tab == CruxTab.Review) reviewBadge else 0,
                    onClick = {
                        if (currentRoute != tab.route) {
                            // If we're leaving a PUSHED screen (settings/overdue/history/detail), don't
                            // save or restore that stack — otherwise a pushed screen sitting on top of a
                            // non-start tab gets restored right back, and the tab looks unresponsive.
                            // Between actual tabs, keep save/restore so each tab holds its own state.
                            val onTab = CruxTab.entries.any { it.route == currentRoute }
                            nav.navigate(tab.route) {
                                popUpTo(nav.graph.findStartDestination().id) { saveState = onTab }
                                launchSingleTop = true
                                restoreState = onTab
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
private fun RowScope.CruxTabItem(tab: CruxTab, selected: Boolean, badge: Int = 0, onClick: () -> Unit) {
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
        // the count badge (mockup .tbadge): a garnet pill riding up-right of the glyph.
        if (badge > 0) {
            Box(
                Modifier
                    .align(Alignment.TopCenter)
                    .offset(x = 14.dp, y = 6.dp)
                    .clip(RoundedCornerShape(Dimens.RadiusPill))
                    .background(Garnet)
                    .padding(horizontal = Dimens.Unit + 2.dp, vertical = 1.dp),
            ) {
                Text(text = badge.toString(), style = CruxType.Data, color = Cream)
            }
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

