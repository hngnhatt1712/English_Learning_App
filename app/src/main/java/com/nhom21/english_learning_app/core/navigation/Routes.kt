package com.nhom21.english_learning_app.core.navigation

import kotlinx.serialization.Serializable

/**
 * Centralized type-safe route definitions for Navigation Compose.
 * Splash route is intentionally deferred to a subsequent task.
 */
@Serializable
data object Login

@Serializable
data object Signup

@Serializable
data object SelectLevel

@Serializable
data object WelcomeSuccess

@Serializable
data object Home

/**
 * Post-authentication destination decision result for the Login flow.
 * Decoupled from Android/Navigation classes so it can be unit-tested on the JVM.
 */
enum class AuthDestination {
    SelectLevel,
    Home
}

/**
 * Pure function that determines the target screen after a successful Login based on
 * `user.selectedLevelId` per `api-contract.md` section 7.2.
 *
 * - `null` (or blank defensively) -> [AuthDestination.SelectLevel]
 * - Non-blank level UUID -> [AuthDestination.Home]
 *
 * Note: This function is used **exclusively** for the Login flow.
 * The Signup flow always navigates directly to [SelectLevel].
 */
fun routeAfterAuth(selectedLevelId: String?): AuthDestination {
    return if (selectedLevelId.isNullOrBlank()) {
        AuthDestination.SelectLevel
    } else {
        AuthDestination.Home
    }
}

/**
 * Declarative representation of a navigation command in the authentication/onboarding graph.
 * Used to verify route targets, `popUpTo` rules, `launchSingleTop`, and back-stack transitions
 * in pure JVM unit tests.
 */
sealed interface NavCommand {
    data class NavigateTo(
        val destination: Any,
        val popUpToRoute: Any? = null,
        val popUpToInclusive: Boolean = false,
        val clearEntireBackStack: Boolean = false,
        val launchSingleTop: Boolean = true
    ) : NavCommand

    data object PopBackStack : NavCommand

    data object Ignore : NavCommand
}

/**
 * Pure navigation transition resolvers matching the specification table in Section 3.
 */
object AppNavTransitions {

    /** Login -> click "Register": push [Signup] onto the back stack with `launchSingleTop = true`. */
    fun onLoginRegisterClick(): NavCommand.NavigateTo = NavCommand.NavigateTo(
        destination = Signup,
        launchSingleTop = true
    )

    /** Signup -> click "Already have an account": pop back to existing [Login] instance. */
    fun onSignupBackToLogin(): NavCommand = NavCommand.PopBackStack

    /**
     * Login -> success: route via [routeAfterAuth] and pop [Login] (`inclusive = true`).
     */
    fun onLoginSuccess(selectedLevelId: String?): NavCommand.NavigateTo {
        val target: Any = when (routeAfterAuth(selectedLevelId)) {
            AuthDestination.SelectLevel -> SelectLevel
            AuthDestination.Home -> Home
        }
        return NavCommand.NavigateTo(
            destination = target,
            popUpToRoute = Login,
            popUpToInclusive = true,
            launchSingleTop = true
        )
    }

    /**
     * Signup -> success: always navigate to [SelectLevel] and pop [Login] (`inclusive = true`),
     * removing both [Signup] and [Login] from the back stack. Does NOT call [routeAfterAuth].
     */
    fun onSignupSuccess(): NavCommand.NavigateTo = NavCommand.NavigateTo(
        destination = SelectLevel,
        popUpToRoute = Login,
        popUpToInclusive = true,
        launchSingleTop = true
    )

    /** SelectLevel -> level chosen: navigate to [WelcomeSuccess] and pop [SelectLevel] (`inclusive = true`). */
    fun onSelectLevelSuccess(): NavCommand.NavigateTo = NavCommand.NavigateTo(
        destination = WelcomeSuccess,
        popUpToRoute = SelectLevel,
        popUpToInclusive = true,
        launchSingleTop = true
    )

    /** WelcomeSuccess -> primary continue action: navigate to [Home] and pop [WelcomeSuccess] (`inclusive = true`). */
    fun onWelcomeContinue(): NavCommand.NavigateTo = NavCommand.NavigateTo(
        destination = Home,
        popUpToRoute = WelcomeSuccess,
        popUpToInclusive = true,
        launchSingleTop = true
    )

    /**
     * 401 `unauthorized` session expiration event:
     * - If already on [Login], ignore (`No-Op`) to prevent duplicate navigation.
     * - Otherwise navigate to [Login] and clear the entire back stack.
     */
    fun onSessionExpired(isOnLoginDestination: Boolean): NavCommand {
        return if (isOnLoginDestination) {
            NavCommand.Ignore
        } else {
            NavCommand.NavigateTo(
                destination = Login,
                clearEntireBackStack = true,
                launchSingleTop = true
            )
        }
    }

    /**
     * Applies a [NavCommand] to an immutable list representing the active back stack of routes.
     * Used in unit tests to verify back-stack cleanliness across multi-step user journeys.
     */
    fun applyToBackStack(backStack: List<Any>, command: NavCommand): List<Any> {
        return when (command) {
            is NavCommand.Ignore -> backStack
            is NavCommand.PopBackStack -> {
                if (backStack.size > 1) backStack.dropLast(1) else emptyList()
            }
            is NavCommand.NavigateTo -> {
                val afterPop = when {
                    command.clearEntireBackStack -> emptyList()
                    command.popUpToRoute != null -> {
                        val idx = backStack.lastIndexOf(command.popUpToRoute)
                        if (idx >= 0) {
                            val endIdx = if (command.popUpToInclusive) idx else idx + 1
                            backStack.subList(0, endIdx)
                        } else {
                            backStack
                        }
                    }
                    else -> backStack
                }
                if (command.launchSingleTop && afterPop.lastOrNull() == command.destination) {
                    afterPop
                } else {
                    afterPop + command.destination
                }
            }
        }
    }
}

