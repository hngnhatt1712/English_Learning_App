package com.nhom21.english_learning_app

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.nhom21.english_learning_app.features.auth.LoginRoute
import com.nhom21.english_learning_app.features.auth.SelectLevelRoute
import com.nhom21.english_learning_app.features.auth.SignUpRoute
import com.nhom21.english_learning_app.features.auth.SignupRoute
import com.nhom21.english_learning_app.features.auth.WelcomeSuccessScreen
import com.nhom21.english_learning_app.ui.theme.English_Learning_AppTheme
import dagger.hilt.android.AndroidEntryPoint

private enum class AuthScreen { SignUp, SelectLevel, WelcomeSuccess, Login }

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            English_Learning_AppTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    SignupRoute(
                        modifier = Modifier.padding(innerPadding),
                        onSignupSuccess = {
                            // TODO(Task Navigation): replace with navigation
                            Toast.makeText(this@MainActivity, "Signup successful", Toast.LENGTH_SHORT).show()
                        }
                    )
                }
                OwlaApp(
                    onLoginSuccess = {
                        Toast.makeText(this, "Login successful", Toast.LENGTH_SHORT).show()
                    }
                )
            }
        }
    }
}

@Composable
private fun OwlaApp(
    onLoginSuccess: () -> Unit
) {
    var currentScreen by remember { mutableStateOf(AuthScreen.SignUp) }

    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        val modifier = Modifier.padding(innerPadding)
        when (currentScreen) {
            AuthScreen.SignUp -> SignUpRoute(
                modifier = modifier,
                onSignUpSuccess = { currentScreen = AuthScreen.SelectLevel },
                onNavigateToLogin = { currentScreen = AuthScreen.Login }
            )
            AuthScreen.SelectLevel -> SelectLevelRoute(
                modifier = modifier,
                onNavigateNext = { _ -> currentScreen = AuthScreen.WelcomeSuccess }
            )
            AuthScreen.WelcomeSuccess -> WelcomeSuccessScreen(
                modifier = modifier,
                onContinueToLogin = { currentScreen = AuthScreen.Login }
            )
            AuthScreen.Login -> LoginRoute(
                modifier = modifier,
                onLoginSuccess = onLoginSuccess,
                onRegisterClick = { currentScreen = AuthScreen.SignUp }
            )
        }
    }
}
