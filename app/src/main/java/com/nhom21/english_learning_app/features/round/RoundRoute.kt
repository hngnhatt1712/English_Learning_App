package com.nhom21.english_learning_app.features.round

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
internal fun RoundRoute(onExit: () -> Unit, onSubmitted: () -> Unit, viewModel: RoundViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val completed = state is Round1UiState.Completed || state is Round2UiState.Completed
    LaunchedEffect(completed) { if (completed) onSubmitted() }
    when (val current = state) {
        Round1UiState.Locked -> RoundStatus(RoundMessage.Locked, onExit, viewModel::retry)
        Round1UiState.Error -> RoundStatus(RoundMessage.LoadFailed, onExit, viewModel::retry)
        is Round1UiState -> Round1ContentRoute(current, onExit, viewModel)
        is Round3UiState -> RoundStatus(RoundMessage.Unsupported, {
            viewModel.leaveMatching()
            onExit()
        }, viewModel::retry)
        is Round2UiState -> {
            val question = (current as? Round2UiState.Question)?.question
            val audio = key(question?.id) { rememberRound1Audio(question?.audioUrl) }
            Round2Screen(
                state = current, onSelect = viewModel::selectOption, onCheck = viewModel::checkOption,
                onNext = viewModel::nextQuestion, onAudio = { if (it == question?.id) audio.play() },
                onRetry = viewModel::retry, onExit = onExit,
            )
        }
    }
}

@Composable
private fun Round1ContentRoute(state: Round1UiState, onExit: () -> Unit, viewModel: RoundViewModel) {
    val learning = state as? Round1UiState.Learning
    val audio = key(learning?.word?.id, learning?.word?.audioUrl) {
        rememberRound1Audio(learning?.word?.audioUrl)
    }

    Round1Screen(
        state = learning?.copy(audio = audio.state) ?: state,
        onCardAction = { id, action ->
            audio.stop()
            viewModel.swipe(id, action)
        },
        onUndo = viewModel::undo,
        onAudio = { if (it == learning?.word?.id) audio.play() },
        onFinish = viewModel::finish,
        onRetry = viewModel::retry,
        onExit = onExit,
        onContinue = onExit,
        imageContent = { url -> RoundWordImage(url) },
        claimSwipeDemo = viewModel::claimSwipeDemo,
        feedbackManaged = true
    )
}
