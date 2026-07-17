package com.crux.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.crux.app.ui.components.CruxIcons
import com.crux.app.ui.theme.CruxType
import com.crux.app.ui.theme.Dimens
import com.crux.app.ui.theme.Ember
import com.crux.app.ui.theme.Garnet
import com.crux.app.ui.theme.Hairline
import com.crux.app.ui.theme.InkHi
import com.crux.app.ui.theme.InkLow
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
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = { CruxTabBar(nav) },
    ) { innerPadding ->
        NavHost(
            navController = nav,
            startDestination = CruxTab.Home.route,
            modifier = Modifier.padding(innerPadding),
        ) {
            CruxTab.entries.forEach { tab ->
                composable(tab.route) { EmptyTabScreen(tab) }
            }
        }
    }
}

@Composable
private fun CruxTabBar(nav: NavController) {
    val backStackEntry by nav.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    // docked shell: void ground, a single top hairline, content lifted above the nav bar.
    Column(Modifier.fillMaxWidth().background(Void)) {
        Box(Modifier.fillMaxWidth().height(Dimens.HairlineWidth).background(Hairline))
        Row(
            Modifier
                .fillMaxWidth()
                .height(Dimens.TabBarHeight)
                .navigationBarsPadding(),
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
