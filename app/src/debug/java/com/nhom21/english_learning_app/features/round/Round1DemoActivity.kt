package com.nhom21.english_learning_app.features.round

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.nhom21.english_learning_app.R
import com.nhom21.english_learning_app.ui.theme.English_Learning_AppTheme
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Independent debug entry point. Opening it needs neither a session nor a server. */
class Round1DemoActivity : ComponentActivity() {
    private val model by viewModels<Round1DemoViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            English_Learning_AppTheme {
                val state by model.uiState.collectAsStateWithLifecycle()
                Scaffold(contentWindowInsets = WindowInsets.safeDrawing, modifier = Modifier.fillMaxSize()) { insets ->
                    Round1Screen(
                        state = state,
                        onCardAction = model::onCardAction,
                        onUndo = model::undo,
                        onAudio = model::playAudio,
                        onFinish = model::finishRound,
                        onRetry = {},
                        onExit = ::finish,
                        onContinue = ::finish,
                        modifier = Modifier.padding(insets).consumeWindowInsets(insets),
                        exitBody = stringResource(R.string.round_leave_local)
                    )
                }
            }
        }
    }

    override fun onStop() {
        model.stopAudio()
        super.onStop()
    }
}

internal class Round1DemoViewModel : ViewModel() {
    private val session = Round1DemoSession()
    private val mutableUiState = MutableStateFlow(session.state())
    val uiState = mutableUiState.asStateFlow()
    private var audioJob: Job? = null

    fun onCardAction(id: String, action: Round1Action) {
        if (session.onCardAction(id, action)) stopAudio()
    }

    fun undo(id: Long) {
        if (session.undo(id)) stopAudio()
    }

    fun playAudio(id: String) {
        if (!session.playAudio(id)) return
        publish()
        audioJob = viewModelScope.launch {
            delay(1_200)
            session.stopAudio()
            publish()
        }
    }

    fun stopAudio() {
        audioJob?.cancel()
        session.stopAudio()
        publish()
    }

    fun finishRound() {
        if (!session.beginFinish()) return
        publish()
        viewModelScope.launch {
            delay(500)
            session.complete()
            publish()
        }
    }

    private fun publish() {
        mutableUiState.value = session.state()
    }
}
