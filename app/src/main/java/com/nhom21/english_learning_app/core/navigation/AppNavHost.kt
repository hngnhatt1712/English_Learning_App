package com.nhom21.english_learning_app.core.navigation

import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.nhom21.english_learning_app.core.network.SessionEvents
import com.nhom21.english_learning_app.features.auth.ForgotPasswordRoute
import com.nhom21.english_learning_app.features.auth.LoginRoute
import com.nhom21.english_learning_app.features.auth.SelectLevelRoute
import com.nhom21.english_learning_app.features.auth.SignupRoute
import com.nhom21.english_learning_app.features.auth.WelcomeSuccessScreen
import com.nhom21.english_learning_app.features.home.HomeRoute
import com.nhom21.english_learning_app.features.home.HomeViewModel
import com.nhom21.english_learning_app.features.lesson.LessonPlaceholderScreen
import com.nhom21.english_learning_app.features.practice.PracticePlaceholderScreen
import com.nhom21.english_learning_app.features.profile.ProfileRoute

/**
 * Root Navigation Host for the application (Sprint 3).
 *
 * Auth and Main are separate nested graphs on one NavController. Login remains the
 * start destination until the separate Splash/session-validation task is implemented.
 * No token-only shortcut bypasses the server-confirmed selected-level decision.
 */
@Composable
fun AppNavHost(
    navController: NavHostController = rememberNavController(),
    startDestination: Any = Login, // temporary until Splash exists
    modifier: Modifier = Modifier,
    sessionEvents: SessionEvents? = null
) {
    val lifecycleOwner = LocalLifecycleOwner.current

    if (sessionEvents != null) {
        LaunchedEffect(sessionEvents, navController, lifecycleOwner) {
            lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                sessionEvents.sessionExpired.collect {
                    val isOnLogin = navController.currentBackStackEntry
                        ?.destination
                        ?.hasRoute<Login>() == true
                    navController.applyCommand(AppNavTransitions.onSessionExpired(isOnLogin))
                }
            }
        }
    }

    val currentEntry by navController.currentBackStackEntryAsState()
    val isInMain = currentEntry?.destination?.hierarchy?.any { it.hasRoute<MainGraph>() } == true
    // Share Home's existing review badge/state across all tabs, scoped to this login session.
    val mainEntry = if (isInMain) {
        remember(currentEntry) { navController.getBackStackEntry(MainGraph) }
    } else null
    val homeViewModel = mainEntry?.let { hiltViewModel<HomeViewModel>(it) }
    val homeState = homeViewModel?.uiState?.collectAsStateWithLifecycle()?.value
    val startsInMain = startDestination == MainGraph ||
        MainTab.entries.any { it.destination == startDestination }

    MainScaffold(
        navController = navController,
        modifier = modifier,
        hasPracticeNotification = homeState?.hasPracticeNotification == true
    ) { contentPadding ->
        NavHost(
            navController = navController,
            startDestination = if (startsInMain) MainGraph else AuthGraph,
            modifier = Modifier.padding(contentPadding).consumeWindowInsets(contentPadding)
        ) {
            authGraph(
                startDestination = if (startsInMain || startDestination == AuthGraph) Login else startDestination,
                login = {
                    LoginRoute(
                        onLoginSuccess = { selectedLevelId ->
                            sessionEvents?.resetSession()
                            navController.applyCommand(AppNavTransitions.onLoginSuccess(selectedLevelId))
                        },
                        onRegisterClick = {
                            navController.applyCommand(AppNavTransitions.onLoginRegisterClick())
                        }
                    )
                },
                signup = {
                    SignupRoute(
                        onSignupSuccess = {
                            sessionEvents?.resetSession()
                            navController.applyCommand(AppNavTransitions.onSignupSuccess())
                        },
                        onNavigateToLogin = {
                            navController.applyCommand(AppNavTransitions.onSignupBackToLogin())
                        }
                    )
                },
                selectLevel = {
                    SelectLevelRoute(
                        onNavigateNext = { confirmedLevelId ->
                            // Keep the existing PUT /profile confirmation guard.
                            if (confirmedLevelId.isNotBlank()) {
                                navController.applyCommand(AppNavTransitions.onSelectLevelSuccess())
                            }
                        }
                    )
                },
                welcomeSuccess = {
                    WelcomeSuccessScreen(
                        onContinueToLogin = {
                            navController.applyCommand(AppNavTransitions.onWelcomeContinue())
                        }
                    )
                },
                forgotPassword = {
                    ForgotPasswordRoute(onNavigateToLogin = { navController.popBackStack() })
                }
            )
            mainGraph(
                startDestination = MainTab.entries.firstOrNull { it.destination == startDestination }
                    ?.destination ?: Home
            ) { tab, entry ->
                when (tab) {
                    MainTab.HOME -> {
                        val parent = remember(entry) { navController.getBackStackEntry(MainGraph) }
                        HomeRoute(
                            viewModel = hiltViewModel(parent),
                            onNavigateToLesson = { navController.selectMainTab(MainTab.LESSON) },
                            onNavigateToPractice = { navController.selectMainTab(MainTab.PRACTICE) },
                            onNavigateToProfile = { navController.selectMainTab(MainTab.PROFILE) }
                        )
                    }
                    MainTab.LESSON -> LessonPlaceholderScreen()
                    MainTab.PRACTICE -> PracticePlaceholderScreen()
                    MainTab.PROFILE -> ProfileRoute(
                        onLoggedOut = { navController.applyCommand(AppNavTransitions.onLogout()) }
                    )
                }
            }
        }
    }
}
