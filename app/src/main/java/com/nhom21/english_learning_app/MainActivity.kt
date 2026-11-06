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
import com.nhom21.english_learning_app.features.auth.WelcomeSuccessScreen
import com.nhom21.english_learning_app.features.home.HomeRoute
import com.nhom21.english_learning_app.ui.theme.English_Learning_AppTheme
import dagger.hilt.android.AndroidEntryPoint

private enum class AppScreen { SignUp, SelectLevel, WelcomeSuccess, Login, Home }

/**
 * Activity chính của ứng dụng Owla English.
 * Đóng vai trò làm Router điều hướng giữa các luồng Đăng ký, Chọn trình độ, Đăng nhập, và Home.
 * Toàn bộ phần xử lý logic nghiệp vụ và state của từng màn hình đều được đưa vào ViewModel tương ứng.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            English_Learning_AppTheme {
                OwlaApp(
                    onLoginSuccess = {
                        Toast.makeText(this, "Đăng nhập thành công!", Toast.LENGTH_SHORT).show()
                    }
                )
            }
        }
    }
}

@Composable
private fun OwlaApp(
    onLoginSuccess: () -> Unit,
) {
    var currentScreen by remember { mutableStateOf(AppScreen.SignUp) }

    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        val modifier = Modifier.padding(innerPadding)
        when (currentScreen) {
            AppScreen.SignUp -> SignUpRoute(
                modifier = modifier,
                onSignupSuccess = { currentScreen = AuthScreen.SelectLevel },
                onNavigateToLogin = { currentScreen = AuthScreen.Login }
            )
            AppScreen.SelectLevel -> SelectLevelRoute(
                modifier = modifier,
                onNavigateNext = { _ -> currentScreen = AppScreen.WelcomeSuccess }
            )
            AppScreen.WelcomeSuccess -> WelcomeSuccessScreen(
                modifier = modifier,
                onContinueToLogin = { currentScreen = AppScreen.Login }
            )
            AppScreen.Login -> LoginRoute(
                modifier = modifier,
                onLoginSuccess = {
                    onLoginSuccess()
                    currentScreen = AppScreen.Home
                },
                onRegisterClick = { currentScreen = AppScreen.SignUp }
            )
            AppScreen.Home -> HomeRoute(
                modifier = modifier,
                onNavigateToLesson = { /* Chuyển tab Lesson khi phát triển thêm */ },
                onNavigateToPractice = { /* Chuyển tab Practice khi phát triển thêm */ },
                onNavigateToProfile = { /* Chuyển tab Profile khi phát triển thêm */ }
            )
        }
    }
}
