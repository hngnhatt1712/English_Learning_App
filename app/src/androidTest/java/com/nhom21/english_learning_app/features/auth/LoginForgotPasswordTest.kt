package com.nhom21.english_learning_app.features.auth

import android.content.res.Configuration
import android.graphics.Bitmap
import androidx.compose.material3.Text
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.click
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import androidx.test.espresso.Espresso.pressBack
import androidx.test.platform.app.InstrumentationRegistry
import com.nhom21.english_learning_app.core.navigation.AppNavTransitions
import com.nhom21.english_learning_app.core.navigation.AuthGraph
import com.nhom21.english_learning_app.core.navigation.ForgotPassword
import com.nhom21.english_learning_app.core.navigation.Login
import com.nhom21.english_learning_app.core.navigation.Signup
import com.nhom21.english_learning_app.core.navigation.applyCommand
import com.nhom21.english_learning_app.core.navigation.authGraph
import com.nhom21.english_learning_app.ui.theme.OwlaColors
import com.nhom21.english_learning_app.ui.theme.Quicksand
import com.nhom21.english_learning_app.ui.theme.English_Learning_AppTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.File

/** Exercises the real Login UI and existing auth graph without network requests or Hilt. */
class LoginForgotPasswordTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun emptyLoginHasAccessibleIndependentLinkInLightMode() = checkLink(dark = false)

    @Test
    fun emptyLoginHasAccessibleIndependentLinkInDarkMode() = checkLink(dark = true)

    private fun checkLink(dark: Boolean) {
        var forgotClicks = 0
        var loginClicks = 0
        var registerClicks = 0
        compose.setContent {
            val configuration = Configuration(LocalConfiguration.current).apply {
                uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                    if (dark) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
            }
            CompositionLocalProvider(LocalConfiguration provides configuration) {
                English_Learning_AppTheme(darkTheme = dark) {
                    Surface {
                        LoginScreen(
                            onForgotPasswordClick = { forgotClicks++ },
                            onLoginClick = { loginClicks++ },
                            onRegisterClick = { registerClicks++ }
                        )
                    }
                }
            }
        }
        compose.onAllNodesWithText("Quên mật khẩu?").assertCountEquals(1)
        val link = compose.onNodeWithText("Quên mật khẩu?")
            .performScrollTo()
            .assertIsDisplayed()
            .assertIsEnabled()
            .assertHasClickAction()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            .assertHeightIsAtLeast(48.dp)
            .assertWidthIsAtLeast(48.dp)
        link.assert(SemanticsMatcher("has an accessible action label") {
            it.config[SemanticsActions.OnClick].label == "Mở màn hình quên mật khẩu"
        })
        val layouts = mutableListOf<TextLayoutResult>()
        link.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        val style = layouts.single().layoutInput.style
        assertEquals(Quicksand, style.fontFamily)
        assertEquals(14.sp, style.fontSize)
        val background = if (dark) OwlaColors.DarkBackground else OwlaColors.LightBackground
        val contrast = (maxOf(style.color.luminance(), background.luminance()) + 0.05f) /
            (minOf(style.color.luminance(), background.luminance()) + 0.05f)
        assertTrue("Link text contrast must be at least 4.5:1", contrast >= 4.5f)
        val screenshot = compose.onRoot().captureToImage().asAndroidBitmap()
        val cache = InstrumentationRegistry.getInstrumentation().targetContext.externalCacheDir
        File(requireNotNull(cache), "login-forgot-${if (dark) "dark" else "light"}.png")
            .outputStream().use { screenshot.compress(Bitmap.CompressFormat.PNG, 100, it) }
        link.performTouchInput { click() }
        compose.runOnIdle {
            assertEquals(1, forgotClicks)
            assertEquals(0, loginClicks)
            assertEquals(0, registerClicks)
        }
    }

    @Test
    fun linkPreservesEnteredValuesAndExistingLoginRegisterActions() {
        val initial = LoginUiState(identifier = "learner", password = "Sample123")
        lateinit var current: LoginUiState
        var forgotClicks = 0
        var loginClicks = 0
        var registerClicks = 0
        compose.setContent {
            var state by remember { mutableStateOf(initial) }
            current = state
            LoginScreen(
                uiState = state,
                onIdentifierChange = { state = state.copy(identifier = it) },
                onPasswordChange = { state = state.copy(password = it) },
                onForgotPasswordClick = { forgotClicks++ },
                onLoginClick = { loginClicks++ },
                onRegisterClick = { registerClicks++ }
            )
        }
        compose.onAllNodes(hasSetTextAction())[0].performClick()
        compose.onNodeWithText("Quên mật khẩu?").performScrollTo().performTouchInput { click() }
        compose.runOnIdle {
            assertEquals(initial, current)
            assertEquals(1, forgotClicks)
            assertEquals(0, loginClicks)
            assertEquals(0, registerClicks)
        }
        compose.onNodeWithText("ĐĂNG NHẬP").performScrollTo().performClick()
        compose.onNodeWithText("Đăng ký").performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals(1, loginClicks)
            assertEquals(1, registerClicks)
            assertEquals(1, forgotClicks)
        }
    }

    @Test
    fun existingForgotPasswordRequestOpensAndAndroidBackReturnsToLogin() {
        val nav = setAuthGraph()
        compose.onNodeWithText("Quên mật khẩu?").performScrollTo().performClick()
        compose.onNodeWithText("Email hoặc số điện thoại").assertExists()
        compose.runOnIdle { assertTrue(nav.currentDestination!!.hasRoute<ForgotPassword>()) }
        pressBack()
        compose.onNodeWithText("Quên mật khẩu?").assertHasClickAction()
        compose.runOnIdle { assertTrue(nav.currentDestination!!.hasRoute<Login>()) }
        compose.onNodeWithText("Đăng ký").performScrollTo().performClick()
        compose.onNodeWithText("Signup destination").assertExists()
        compose.runOnIdle { assertTrue(nav.currentDestination!!.hasRoute<Signup>()) }
    }

    @Test
    fun repeatedForgotPasswordNavigationKeepsOneDestinationAndReturnLinkWorks() {
        val nav = setAuthGraph()
        compose.onNodeWithText("Quên mật khẩu?").performScrollTo().performClick()
        compose.runOnIdle {
            repeat(3) { nav.navigate(ForgotPassword) { launchSingleTop = true } }
            assertTrue(nav.previousBackStackEntry!!.destination.hasRoute<Login>())
        }
        compose.onNodeWithText("Quay lại đăng nhập").performScrollTo().performClick()
        compose.runOnIdle { assertTrue(nav.currentDestination!!.hasRoute<Login>()) }
        compose.onAllNodes(hasSetTextAction()).assertCountEquals(2)
    }

    private fun setAuthGraph(): NavHostController {
        lateinit var nav: NavHostController
        compose.setContent {
            nav = rememberNavController()
            NavHost(nav, startDestination = AuthGraph) {
                authGraph(
                    login = {
                        LoginScreen(
                            onForgotPasswordClick = {
                                nav.navigate(ForgotPassword) { launchSingleTop = true }
                            },
                            onRegisterClick = {
                                nav.applyCommand(AppNavTransitions.onLoginRegisterClick())
                            }
                        )
                    },
                    signup = { Text("Signup destination") },
                    selectLevel = {},
                    welcomeSuccess = {},
                    forgotPassword = {
                        ForgotPasswordScreen(onNavigateToLogin = { nav.popBackStack() })
                    }
                )
            }
        }
        compose.waitForIdle()
        return nav
    }
}
