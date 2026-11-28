package com.nhom21.english_learning_app.features.round

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
internal fun RoundRoute(
    onExit: () -> Unit,
    onSubmitted: () -> Unit,
    viewModel: RoundViewModel = hiltViewModel(),
    topicName: String? = null,
    isMatching: Boolean = false,
    isApplication: Boolean = false,
    applicationImage: @Composable (String, Modifier) -> Unit = { url, modifier -> RoundWordImage(url, modifier) },
    matchingImage: @Composable (String, Modifier) -> Unit = { url, modifier -> RoundWordImage(url, modifier) },
) {
    val observed by viewModel.uiState.collectAsStateWithLifecycle()
    val state = if (isApplication) {
        when (observed) {
            Round1UiState.Loading -> Round4UiState.Loading
            Round1UiState.Error -> Round4UiState.Message(RoundMessage.LoadFailed)
            else -> observed
        }
    } else if (isMatching) {
        when (observed) {
            Round1UiState.Loading -> Round3UiState.Loading
            Round1UiState.Error -> Round3UiState.LoadError
            else -> observed
        }
    } else observed
    val completed = when (val current = state) {
        is Round1UiState.Completed, is Round2UiState.Completed,
        is Round3UiState.RepairIntro, is Round3UiState.Completed, is Round4UiState.Completed -> true
        is Round3UiState.Question -> current.isRepair
        else -> false
    }
    LaunchedEffect(completed) { if (completed) onSubmitted() }
    when (val current = state) {
        Round1UiState.Locked -> RoundStatus(RoundMessage.Locked, onExit, viewModel::retry)
        Round1UiState.Error -> RoundStatus(RoundMessage.LoadFailed, onExit, viewModel::retry)
        is Round1UiState -> Round1ContentRoute(current, onExit, viewModel)
        is Round3UiState -> Round3Screen(
            state = current,
            onSelectLeft = viewModel::selectMatchingLeft,
            onSelectRight = viewModel::selectMatchingRight,
            onClearFeedback = viewModel::acknowledgeMatchingFeedback,
            onNext = viewModel::nextMatchingQuestion,
            onStartRepair = viewModel::startMatchingRepair,
            onFinishRepair = viewModel::finishMatchingRepair,
            onRetry = viewModel::retry,
            topicName = topicName,
            imageContent = matchingImage,
            onExit = {
                viewModel.leaveMatching()
                onExit()
            },
        )
        is Round4UiState -> Round4Screen(
            state = current,
            onToken = viewModel::selectApplicationToken,
            onText = viewModel::typeApplicationAnswer,
            onMode = viewModel::toggleApplicationKeyboard,
            onCheck = viewModel::checkApplicationAnswer,
            onRetryAnswer = viewModel::retryApplicationAnswer,
            onNext = viewModel::nextApplicationQuestion,
            imageContent = applicationImage,
            onRetry = viewModel::retry,
            onExit = onExit,
        )
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
