package com.nhom21.english_learning_app.features.round

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nhom21.english_learning_app.ui.theme.English_Learning_AppTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Standalone debug launcher with an in-memory session. */
class Round2DemoActivity : ComponentActivity() {
    private val model by viewModels<Round2DemoViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            English_Learning_AppTheme {
                val state by model.uiState.collectAsStateWithLifecycle()
                Round2Screen(
                    state = state,
                    onSelect = model::select,
                    onCheck = model::check,
                    onNext = model::next,
                    onAudio = {},
                    onRetry = model::retry,
                    onExit = ::finish
                )
            }
        }
    }
}

internal class Round2DemoViewModel : ViewModel() {
    private var session = Round2DemoSession()
    private val mutableUiState = MutableStateFlow(session.state())
    val uiState = mutableUiState.asStateFlow()

    fun select(questionId: String, optionId: String) {
        if (session.select(questionId, optionId)) publish()
    }

    fun check(questionId: String) {
        if (session.check(questionId)) publish()
    }

    fun next(questionId: String) {
        if (session.next(questionId)) publish()
    }

    fun retry() {
        if (uiState.value is Round2UiState.Completed) {
            session = Round2DemoSession()
            publish()
        }
    }

    private fun publish() {
        mutableUiState.value = session.state()
    }
}
