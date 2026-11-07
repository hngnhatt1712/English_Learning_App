package com.nhom21.english_learning_app.core.navigation

import com.nhom21.english_learning_app.core.network.SessionEvents
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for centralized routes, [routeAfterAuth] decision logic (T5),
 * [AppNavTransitions] back-stack rules (Section 3), and 401 session expiration handling (T7).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AppNavigationTest {

    // ── T5: Pure routeAfterAuth decision function ───────────────────────────

    @Test
    fun `routeAfterAuth returns SelectLevel when selectedLevelId is null`() {
        val destination = routeAfterAuth(selectedLevelId = null)
        assertEquals(AuthDestination.SelectLevel, destination)
    }

    @Test
    fun `routeAfterAuth returns Home when selectedLevelId is a valid UUID`() {
        val destination = routeAfterAuth(selectedLevelId = "11111111-1111-1111-1111-111111111101")
        assertEquals(AuthDestination.Home, destination)
    }

    @Test
    fun `routeAfterAuth treats empty or blank string as invalid and returns SelectLevel`() {
        // Note: api-contract.md section 7.2 specifies null when no level is selected (never ""),
        // but routeAfterAuth defensively rejects empty/whitespace strings as unselected.
        assertEquals(AuthDestination.SelectLevel, routeAfterAuth(selectedLevelId = ""))
        assertEquals(AuthDestination.SelectLevel, routeAfterAuth(selectedLevelId = "   "))
    }

    // ── Section 3: Individual navigation transition commands ────────────────

    @Test
    fun `onLoginRegisterClick pushes Signup with launchSingleTop without popping Login`() {
        val command = AppNavTransitions.onLoginRegisterClick()
        assertEquals(Signup, command.destination)
        assertNull("Login must remain on back stack so Back returns to Login", command.popUpToRoute)
        assertFalse(command.popUpToInclusive)
        assertTrue(command.launchSingleTop)
    }

    @Test
    fun `onSignupBackToLogin returns PopBackStack without creating a new Login destination`() {
        val command = AppNavTransitions.onSignupBackToLogin()
        assertEquals(NavCommand.PopBackStack, command)
    }

    @Test
    fun `onLoginSuccess with null selectedLevelId navigates to SelectLevel and pops Login inclusive`() {
        val command = AppNavTransitions.onLoginSuccess(selectedLevelId = null)
        assertEquals(SelectLevel, command.destination)
        assertEquals(Login, command.popUpToRoute)
        assertTrue(command.popUpToInclusive)
        assertTrue(command.launchSingleTop)
    }

    @Test
    fun `onLoginSuccess with valid selectedLevelId navigates to Home and pops Login inclusive`() {
        val command = AppNavTransitions.onLoginSuccess(selectedLevelId = "11111111-1111-1111-1111-111111111102")
        assertEquals(Home, command.destination)
        assertEquals(Login, command.popUpToRoute)
        assertTrue(command.popUpToInclusive)
        assertTrue(command.launchSingleTop)
    }

    @Test
    fun `onSignupSuccess always navigates to SelectLevel and pops Login inclusive`() {
        val command = AppNavTransitions.onSignupSuccess()
        assertEquals(SelectLevel, command.destination)
        assertEquals(Login, command.popUpToRoute)
        assertTrue("Must pop up to Login inclusive to remove both Signup and Login", command.popUpToInclusive)
        assertTrue(command.launchSingleTop)
    }

    @Test
    fun `onSelectLevelSuccess navigates to WelcomeSuccess and pops SelectLevel inclusive`() {
        val command = AppNavTransitions.onSelectLevelSuccess()
        assertEquals(WelcomeSuccess, command.destination)
        assertEquals(SelectLevel, command.popUpToRoute)
        assertTrue(command.popUpToInclusive)
        assertTrue(command.launchSingleTop)
    }

    @Test
    fun `onWelcomeContinue navigates to Home and pops WelcomeSuccess inclusive`() {
        val command = AppNavTransitions.onWelcomeContinue()
        assertEquals(Home, command.destination)
        assertEquals(WelcomeSuccess, command.popUpToRoute)
        assertTrue(command.popUpToInclusive)
        assertTrue(command.launchSingleTop)
    }

    // ── Section 3 & T7: Session expiration (401 unauthorized) ───────────────

    @Test
    fun `onSessionExpired when on non-Login screen navigates to Login and clears entire back stack`() {
        val command = AppNavTransitions.onSessionExpired(isOnLoginDestination = false)
        assertTrue(command is NavCommand.NavigateTo)
        val nav = command as NavCommand.NavigateTo
        assertEquals(Login, nav.destination)
        assertTrue("Must clear entire back stack on 401 unauthorized", nav.clearEntireBackStack)
        assertTrue(nav.launchSingleTop)
    }

    @Test
    fun `onSessionExpired when already on Login ignores event to prevent duplicate navigation`() {
        val command = AppNavTransitions.onSessionExpired(isOnLoginDestination = true)
        assertEquals(NavCommand.Ignore, command)
    }

    @Test
    fun `sessionExpired flow collection navigates to Login once and ignores subsequent emissions on Login`() = runTest {
        val sessionEvents = SessionEvents()
        var backStack = listOf<Any>(Home)
        val executedCommands = mutableListOf<NavCommand>()

        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            sessionEvents.sessionExpired.collect {
                val isOnLogin = backStack.lastOrNull() == Login
                val command = AppNavTransitions.onSessionExpired(isOnLoginDestination = isOnLogin)
                executedCommands.add(command)
                backStack = AppNavTransitions.applyToBackStack(backStack, command)
            }
        }

        // First 401 unauthorized while on Home -> navigates to Login and clears stack
        sessionEvents.notifySessionExpired()
        assertEquals(listOf<Any>(Login), backStack)
        assertEquals(1, executedCommands.size)
        assertTrue(executedCommands[0] is NavCommand.NavigateTo)

        // Duplicate 401 while already expired / on Login -> deduplicated, stack remains [Login]
        sessionEvents.notifySessionExpired()
        assertEquals(listOf<Any>(Login), backStack)
        assertEquals(1, executedCommands.size)

        // Even if session is reset while still on Login and another 401 fires on Login, it is ignored
        sessionEvents.resetSession()
        sessionEvents.notifySessionExpired()
        assertEquals(listOf<Any>(Login), backStack)
        assertEquals(2, executedCommands.size)
        assertEquals(NavCommand.Ignore, executedCommands[1])

        job.cancel()
    }

    // ── Back-stack cleanliness across complete user flows ───────────────────

    @Test
    fun `full signup flow maintains clean single-screen back stack after auth`() {
        // Start at Login
        var stack = listOf<Any>(Login)

        // 1. Login -> click Register -> [Login, Signup]
        stack = AppNavTransitions.applyToBackStack(stack, AppNavTransitions.onLoginRegisterClick())
        assertEquals(listOf<Any>(Login, Signup), stack)

        // 2. Signup success -> pops Login (inclusive) -> [SelectLevel]
        stack = AppNavTransitions.applyToBackStack(stack, AppNavTransitions.onSignupSuccess())
        assertEquals(listOf<Any>(SelectLevel), stack)
        // Pressing Back at SelectLevel exits app (empty stack), never returns to Signup or Login
        assertTrue(AppNavTransitions.applyToBackStack(stack, NavCommand.PopBackStack).isEmpty())

        // 3. SelectLevel success -> pops SelectLevel (inclusive) -> [WelcomeSuccess]
        stack = AppNavTransitions.applyToBackStack(stack, AppNavTransitions.onSelectLevelSuccess())
        assertEquals(listOf<Any>(WelcomeSuccess), stack)
        // Pressing Back at WelcomeSuccess exits app, never returns to SelectLevel
        assertTrue(AppNavTransitions.applyToBackStack(stack, NavCommand.PopBackStack).isEmpty())

        // 4. WelcomeSuccess continue -> pops WelcomeSuccess (inclusive) -> [Home]
        stack = AppNavTransitions.applyToBackStack(stack, AppNavTransitions.onWelcomeContinue())
        assertEquals(listOf<Any>(Home), stack)
        // Pressing Back at Home exits app
        assertTrue(AppNavTransitions.applyToBackStack(stack, NavCommand.PopBackStack).isEmpty())
    }

    @Test
    fun `signup back to login pops Signup without duplicating Login`() {
        var stack = listOf<Any>(Login)
        stack = AppNavTransitions.applyToBackStack(stack, AppNavTransitions.onLoginRegisterClick())
        assertEquals(listOf<Any>(Login, Signup), stack)

        stack = AppNavTransitions.applyToBackStack(stack, AppNavTransitions.onSignupBackToLogin())
        assertEquals(listOf<Any>(Login), stack)
    }

    @Test
    fun `login flow with null level goes to SelectLevel and pops Login`() {
        var stack = listOf<Any>(Login)
        stack = AppNavTransitions.applyToBackStack(stack, AppNavTransitions.onLoginSuccess(selectedLevelId = null))
        assertEquals(listOf<Any>(SelectLevel), stack)
        assertTrue(AppNavTransitions.applyToBackStack(stack, NavCommand.PopBackStack).isEmpty())
    }

    @Test
    fun `login flow with existing level goes directly to Home and pops Login`() {
        var stack = listOf<Any>(Login)
        stack = AppNavTransitions.applyToBackStack(
            stack,
            AppNavTransitions.onLoginSuccess(selectedLevelId = "11111111-1111-1111-1111-111111111101")
        )
        assertEquals(listOf<Any>(Home), stack)
        assertTrue(AppNavTransitions.applyToBackStack(stack, NavCommand.PopBackStack).isEmpty())
    }
}

