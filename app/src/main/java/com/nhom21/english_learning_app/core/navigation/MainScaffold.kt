package com.nhom21.english_learning_app.core.navigation

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.nhom21.english_learning_app.core.ui.components.OwlaBottomBar
import com.nhom21.english_learning_app.ui.theme.OwlaColors

/**
 * Owns the single bottom bar and main-tab selection, using the root NavController.
 * MainActivity owns system insets; content only receives the bottom bar's padding.
 *
 * Sprint 5–7: register Round/RoundResult as non-tab destinations. Only the four exact
 * [MainTab] routes display the bar, including when learning routes live in MainGraph.
 * Do not infer visibility from MainGraph membership or a route-name prefix.
 */
@Composable
fun MainScaffold(
    navController: NavHostController,
    modifier: Modifier = Modifier,
    hasPracticeNotification: Boolean = false,
    content: @Composable (PaddingValues) -> Unit
) {
    val entry by navController.currentBackStackEntryAsState()
    val selectedTab = entry?.destination.mainTab()
    val background = if (isSystemInDarkTheme()) OwlaColors.DarkBackground else OwlaColors.LightBackground

    Scaffold(
        modifier = modifier,
        containerColor = background,
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            if (selectedTab != null) {
                OwlaBottomBar(
                    selectedTab = selectedTab,
                    onTabSelected = navController::selectMainTab,
                    hasPracticeNotification = hasPracticeNotification
                )
            }
        },
        content = content
    )
}

internal fun NavDestination?.mainTab(): MainTab? =
    mainTabMatching { route -> this?.hasRoute(route) == true }

/** Keep the graph's initial tab as the Back anchor and save/restore other tab stacks. */
internal fun NavHostController.selectMainTab(tab: MainTab) {
    if (currentDestination.mainTab() == tab) return
    val mainGraph = currentDestination?.hierarchy
        ?.firstOrNull { it.hasRoute<MainGraph>() } as? NavGraph ?: return
    navigate(tab.destination) {
        popUpTo(mainGraph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

/** Execute the same commands tested by AppNavigationTest, including saved-tab cleanup. */
internal fun NavHostController.applyCommand(command: NavCommand) {
    when (command) {
        NavCommand.Ignore -> Unit
        NavCommand.PopBackStack -> popBackStack()
        is NavCommand.NavigateTo -> {
            if (command.clearEntireBackStack) {
                // Saved tab entries may contain a previous user's UI/ViewModel state.
                // Route-based lookup only searches the current hierarchy: Auth/learning
                // destinations cannot resolve sibling Main-tab routes. Resolve IDs from
                // the root graph instead, including when there is no saved tab state yet.
                val mainGraph = graph.firstOrNull { it.hasRoute<MainGraph>() } as? NavGraph
                mainGraph?.forEach { clearBackStack(it.id) }
                if (mainGraph != null) clearBackStack(mainGraph.id)
            }
            navigate(command.destination) {
                if (command.clearEntireBackStack) {
                    popUpTo(graph.id) { inclusive = true }
                } else if (command.popUpToRoute != null) {
                    popUpTo(command.popUpToRoute) { inclusive = command.popUpToInclusive }
                }
                launchSingleTop = command.launchSingleTop
            }
        }
    }
}
