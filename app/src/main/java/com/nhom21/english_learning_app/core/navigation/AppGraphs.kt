package com.nhom21.english_learning_app.core.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.navigation

/** Slots keep graph registration independent of Hilt and screen state ownership. */
internal fun NavGraphBuilder.authGraph(
    startDestination: Any = Login,
    login: @Composable () -> Unit,
    signup: @Composable () -> Unit,
    selectLevel: @Composable () -> Unit,
    welcomeSuccess: @Composable () -> Unit,
    forgotPassword: @Composable () -> Unit
) {
    navigation<AuthGraph>(startDestination = startDestination) {
        composable<Login> { login() }
        composable<Signup> { signup() }
        composable<SelectLevel> { selectLevel() }
        composable<WelcomeSuccess> { welcomeSuccess() }
        composable<ForgotPassword> { forgotPassword() }
    }
}

internal fun NavGraphBuilder.mainGraph(
    startDestination: Any = Home,
    content: @Composable (MainTab, NavBackStackEntry) -> Unit
) {
    navigation<MainGraph>(startDestination = startDestination) {
        composable<Home> { content(MainTab.HOME, it) }
        composable<Lesson> { content(MainTab.LESSON, it) }
        composable<Practice> { content(MainTab.PRACTICE, it) }
        composable<Profile> { content(MainTab.PROFILE, it) }
    }
}
