package com.nhom21.english_learning_app.features.round

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalAccessibilityManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.nhom21.english_learning_app.R
import com.nhom21.english_learning_app.features.review.Round1CompletionScreen
import com.nhom21.english_learning_app.features.round.components.RoundExitDialog
import com.nhom21.english_learning_app.features.round.components.RoundHeading
import com.nhom21.english_learning_app.features.round.components.RoundLoadingScreen
import com.nhom21.english_learning_app.features.round.components.RoundNeutralButton
import com.nhom21.english_learning_app.features.round.components.RoundPrimaryButton
import com.nhom21.english_learning_app.features.round.components.RoundWordProgress
import com.nhom21.english_learning_app.ui.theme.roundColors
import com.nhom21.english_learning_app.ui.theme.OwlaType
import kotlinx.coroutines.withTimeoutOrNull

@Composable
fun Round1Screen(
    state: Round1UiState,
    onCardAction: (String, Round1Action) -> Unit,
    onUndo: (Long) -> Unit,
    onAudio: (String) -> Unit,
    onFinish: () -> Unit,
    onRetry: () -> Unit,
    onExit: () -> Unit,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
    exitBody: String? = null,
    imageContent: (@Composable (String) -> Unit)? = null,
    claimSwipeDemo: (suspend () -> Boolean)? = null,
    feedbackManaged: Boolean = false
) {
    var confirmExit by rememberSaveable { mutableStateOf(false) }
    var leaving by remember { mutableStateOf(false) }

    fun leave(callback: () -> Unit) {
        if (leaving) return
        leaving = true
        confirmExit = false
        callback()
    }

    val requestExit = { if (!leaving) confirmExit = true }
    val exitVisible = confirmExit && state !is Round1UiState.Completed
    BackHandler(enabled = !exitVisible) {
        if (state is Round1UiState.Completed) leave(onContinue) else requestExit()
    }

    when (state) {
        Round1UiState.Loading -> RoundLoadingScreen(requestExit, modifier)
        is Round1UiState.Completed -> Round1CompletionScreen(state, { leave(onContinue) }, modifier)
        else -> Round1Content(
            state, onCardAction, onUndo, onAudio, onFinish, onRetry, requestExit, { leave(onExit) },
            modifier, !confirmExit && !leaving, imageContent, claimSwipeDemo, feedbackManaged
        )
    }

    if (exitVisible) {
        val pending = state is Round1UiState.Learning && state.hasPendingActions ||
            state is Round1UiState.Finishing && (state.hasPendingActions || state.status == Round1FinishStatus.Saving)
        RoundExitDialog(
            onKeepLearning = { confirmExit = false },
            onLeave = { leave(onExit) },
            body = when {
                pending -> stringResource(R.string.round_leave_pending)
                exitBody != null -> exitBody
                else -> stringResource(R.string.round_leave_saved)
            }
        )
    }
}

@Composable
private fun Round1Content(
    state: Round1UiState,
    onCardAction: (String, Round1Action) -> Unit,
    onUndo: (Long) -> Unit,
    onAudio: (String) -> Unit,
    onFinish: () -> Unit,
    onRetry: () -> Unit,
    onRequestExit: () -> Unit,
    onBackToLesson: () -> Unit,
    modifier: Modifier,
    interactionEnabled: Boolean,
    imageContent: (@Composable (String) -> Unit)?,
    claimSwipeDemo: (suspend () -> Boolean)?,
    feedbackManaged: Boolean
) {
    val colors = roundColors()
    val snackbars = remember { SnackbarHostState() }
    val latestUndo by rememberUpdatedState(onUndo)
    val feedback = when (state) {
        is Round1UiState.Learning -> state.feedback
        is Round1UiState.Finishing -> state.feedback
        else -> null
    }
    val message = feedback?.let { stringResource(it.message) }
    val undoLabel = stringResource(R.string.round_undo)
    val accessibility = LocalAccessibilityManager.current
    LaunchedEffect(feedback?.id, message, undoLabel, feedbackManaged) {
        if (!feedbackManaged && feedback != null && message != null) {
            val timeout = accessibility?.calculateRecommendedTimeoutMillis(
                3_000L, containsText = true, containsControls = feedback.canUndo
            ) ?: 3_000L
            val result = withTimeoutOrNull(timeout) {
                snackbars.showSnackbar(
                    message,
                    actionLabel = undoLabel.takeIf { feedback.canUndo },
                    duration = SnackbarDuration.Indefinite
                )
            }
            if (result == SnackbarResult.ActionPerformed) latestUndo(feedback.id)
        }
    }

    Box(modifier.fillMaxSize().background(colors.background)) {
        Column(
            Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                RoundHeading(
                    stringResource(R.string.round_title),
                    stringResource(R.string.round_learn_words),
                    onRequestExit,
                    Modifier.widthIn(max = 520.dp)
                )
                when (state) {
                    is Round1UiState.Learning -> RoundWordProgress(state.processedWords, state.totalWords, Modifier.padding(top = 12.dp))
                    is Round1UiState.Finishing -> RoundWordProgress(state.totalWords, state.totalWords, Modifier.padding(top = 12.dp))
                    else -> Unit
                }
                when (state) {
                    is Round1UiState.Learning -> {
                        key(state.word.id) {
                            Round1Study(
                                state, onCardAction, onAudio,
                                interactionEnabled = interactionEnabled,
                                imageContent = imageContent,
                                claimSwipeDemo = claimSwipeDemo
                            )
                        }
                        state.syncError?.let { error ->
                            Round1Message(stringResource(error), stringResource(R.string.round_retry), onRetry)
                        }
                    }
                    is Round1UiState.Finishing -> Round1FinishPanel(state, onFinish)
                    Round1UiState.Error -> Round1Message(
                        stringResource(R.string.round_error), stringResource(R.string.round_retry), onRetry
                    )
                    Round1UiState.Locked -> {
                        Icon(Icons.Rounded.Lock, contentDescription = null, tint = colors.ink)
                        Round1Message(
                            stringResource(R.string.round_locked),
                            stringResource(R.string.round_back_to_lesson),
                            onBackToLesson
                        )
                    }
                    Round1UiState.Empty -> Round1Message(
                        stringResource(R.string.round_empty), stringResource(R.string.round_back_to_lesson), onBackToLesson
                    )
                    else -> Unit
                }
            }
            val snackbarWidth = Modifier.widthIn(max = 480.dp).fillMaxWidth()
            if (feedbackManaged) {
                if (feedback != null && message != null) {
                    Snackbar(
                        modifier = snackbarWidth.semantics { liveRegion = LiveRegionMode.Polite },
                        containerColor = colors.surface,
                        contentColor = colors.ink
                    ) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(message, Modifier.weight(1f))
                            if (feedback.canUndo) {
                                TextButton(onClick = { latestUndo(feedback.id) }, enabled = interactionEnabled) {
                                    Text(undoLabel, color = colors.accentInk)
                                }
                            }
                        }
                    }
                }
            } else {
                SnackbarHost(snackbars, snackbarWidth) { data ->
                    Snackbar(
                        snackbarData = data,
                        containerColor = colors.surface,
                        contentColor = colors.ink,
                        actionColor = colors.accentInk
                    )
                }
            }
        }
    }
}

@Composable
private fun Round1FinishPanel(state: Round1UiState.Finishing, onFinish: () -> Unit) {
    val colors = roundColors()
    val status = state.status
    var clicked by remember(status) { mutableStateOf(false) }
    Text(stringResource(R.string.round_done), style = OwlaType.h1, color = colors.accentInk, textAlign = TextAlign.Center)
    when (status) {
        Round1FinishStatus.Saving -> {
            CircularProgressIndicator(Modifier.size(32.dp), color = colors.primaryFace)
            Text(
                stringResource(R.string.round_saving),
                Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                style = OwlaType.body,
                color = colors.ink
            )
        }
        Round1FinishStatus.Error, Round1FinishStatus.Ready -> {
            val error = status == Round1FinishStatus.Error
            if (error) Text(stringResource(state.errorMessage), style = OwlaType.body, color = colors.ink)
            RoundPrimaryButton(
                stringResource(if (error) R.string.round_retry else R.string.round_finish),
                onClick = {
                    if (!clicked) {
                        clicked = true
                        onFinish()
                    }
                },
                modifier = Modifier.widthIn(max = 340.dp),
                enabled = !clicked
            )
        }
    }
}

@Composable
private fun Round1Message(title: String, button: String, onClick: () -> Unit) {
    Text(title, style = OwlaType.h1, color = roundColors().accentInk, textAlign = TextAlign.Center)
    RoundNeutralButton(button, onClick, Modifier.widthIn(max = 340.dp))
}
