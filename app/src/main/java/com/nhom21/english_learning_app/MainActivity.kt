package com.nhom21.english_learning_app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import com.nhom21.english_learning_app.core.navigation.AppNavHost
import com.nhom21.english_learning_app.core.navigation.Profile
import com.nhom21.english_learning_app.core.network.SessionEvents
import com.nhom21.english_learning_app.ui.theme.English_Learning_AppTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Main entry-point Activity for Owla English.
 * Cấu hình xem trực tiếp màn hình ProfileScreen khi build/chạy ứng dụng.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var sessionEvents: SessionEvents

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            English_Learning_AppTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    AppNavHost(
                        startDestination = Profile,
                        modifier = Modifier.padding(innerPadding),
                        sessionEvents = sessionEvents
                    )
                }
            }
        }
    }
}
