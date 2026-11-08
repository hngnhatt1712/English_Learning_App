package com.nhom21.english_learning_app.core.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.nhom21.english_learning_app.core.network.SessionEvents
import com.nhom21.english_learning_app.features.auth.ForgotPasswordRoute
import com.nhom21.english_learning_app.features.auth.LoginRoute
import com.nhom21.english_learning_app.features.auth.SelectLevelRoute
import com.nhom21.english_learning_app.features.auth.SignupRoute
import com.nhom21.english_learning_app.features.auth.WelcomeSuccessScreen
import com.nhom21.english_learning_app.features.home.HomeRoute
import com.nhom21.english_learning_app.features.profile.ProfileRoute

/**
 * Root Navigation Host for the application (Sprint 3).
 *
 * Hosts 5 flat destinations: [Login], [Signup], [SelectLevel], [WelcomeSuccess], and [Home].
 * Observes [SessionEvents.sessionExpired] lifecycle-safely to redirect to [Login] and clear
 * the back stack upon `401 unauthorized` responses.
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
                    when (AppNavTransitions.onSessionExpired(isOnLoginDestination = isOnLogin)) {
                        is NavCommand.NavigateTo -> {
                            navController.navigate(Login) {
                                popUpTo(navController.graph.id) { inclusive = true }
                                launchSingleTop = true
                            }
                        }
                        NavCommand.Ignore, NavCommand.PopBackStack -> Unit
                    }
                }
            }
        }
    }

    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier
    ) {
        composable<Login> {
            LoginRoute(
                onLoginSuccess = { selectedLevelId ->
                    sessionEvents?.resetSession()
                    val targetRoute: Any = when (routeAfterAuth(selectedLevelId)) {
                        AuthDestination.SelectLevel -> SelectLevel
                        AuthDestination.Home -> Home
                    }
                    navController.navigate(targetRoute) {
                        popUpTo(Login) { inclusive = true }
                        launchSingleTop = true
                    }
                },
                onRegisterClick = {
                    navController.navigate(Signup) {
                        launchSingleTop = true
                    }
                }
            )
        }

        composable<Signup> {
            SignupRoute(
                onSignupSuccess = {
                    sessionEvents?.resetSession()
                    navController.navigate(SelectLevel) {
                        popUpTo(Login) { inclusive = true }
                        launchSingleTop = true
                    }
                },
                onNavigateToLogin = {
                    navController.popBackStack()
                }
            )
        }

        composable<SelectLevel> {
            SelectLevelRoute(
                onNavigateNext = { _ ->
                    navController.navigate(WelcomeSuccess) {
                        popUpTo(SelectLevel) { inclusive = true }
                        launchSingleTop = true
                    }
                }
            )
        }

        composable<WelcomeSuccess> {
            WelcomeSuccessScreen(
                onContinueToLogin = {
                    navController.navigate(Home) {
                        popUpTo(WelcomeSuccess) { inclusive = true }
                        launchSingleTop = true
                    }
                }
            )
        }

        composable<Home> {
            HomeRoute(
                onNavigateToProfile = {
                    navController.navigate(Profile) {
                        launchSingleTop = true
                    }
                }
            )
        }

        composable<Profile> {
            ProfileRoute(
                onNavigateToHome = {
                    navController.navigate(Home) {
                        popUpTo(Home) { inclusive = true }
                        launchSingleTop = true
                    }
                },
                onLoggedOut = {
                    navController.navigate(Login) {
                        popUpTo(navController.graph.id) { inclusive = true }
                        launchSingleTop = true
                    }
                }
            )
        }

        composable<ForgotPassword> {
            ForgotPasswordRoute(
                onNavigateToLogin = {
                    navController.popBackStack()
                }
            )
        }
    }
}
