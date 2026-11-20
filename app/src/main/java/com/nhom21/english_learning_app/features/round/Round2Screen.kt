package com.nhom21.english_learning_app.features.round

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.nhom21.english_learning_app.R
import com.nhom21.english_learning_app.features.review.RoundCompletionScreen
import com.nhom21.english_learning_app.features.round.components.Round2QuestionContent
import com.nhom21.english_learning_app.features.round.components.RoundCaption
import com.nhom21.english_learning_app.features.round.components.RoundExitDialog
import com.nhom21.english_learning_app.features.round.components.RoundHeading
import com.nhom21.english_learning_app.features.round.components.RoundLoadingScreen
import com.nhom21.english_learning_app.features.round.components.RoundNeutralButton
import com.nhom21.english_learning_app.features.round.components.RoundPrimaryButton
import com.nhom21.english_learning_app.features.round.components.RoundProgress
import com.nhom21.english_learning_app.features.round.components.round2StatusColor
import com.nhom21.english_learning_app.features.round.components.roundColors
import com.nhom21.english_learning_app.ui.theme.OwlaType

@Composable
internal fun Round2Screen(
    state: Round2UiState,
    onSelect: (questionId: String, optionId: String) -> Unit,
    onCheck: (questionId: String) -> Unit,
    onNext: (questionId: String) -> Unit,
    onAudio: (questionId: String) -> Unit,
    onRetry: () -> Unit,
    onExit: () -> Unit,
    modifier: Modifier = Modifier
) {
    var confirmExit by rememberSaveable { mutableStateOf(false) }
    var leaving by remember { mutableStateOf(false) }

    fun leave() {
        if (leaving) return
        leaving = true
        confirmExit = false
        onExit()
    }

    val requestExit = { if (!leaving) confirmExit = true }
    val exitVisible = confirmExit && state !is Round2UiState.Completed
    BackHandler(enabled = !exitVisible && !leaving) {
        if (state is Round2UiState.Completed) leave() else requestExit()
    }

    Box(modifier.fillMaxSize().background(roundColors().background).safeDrawingPadding()) {
        when (state) {
            Round2UiState.Loading -> RoundLoadingScreen(requestExit, mascot = R.drawable.ic_owla_loading)
            is Round2UiState.Question -> Round2QuestionScreen(
                state = state,
                onSelect = { onSelect(state.question.id, it) },
                onCheck = { onCheck(state.question.id) },
                onNext = { onNext(state.question.id) },
                onAudio = { onAudio(state.question.id) },
                onExit = requestExit
            )
            is Round2UiState.Completed -> Round2Completion(state, onRetry, ::leave)
        }
    }

    if (exitVisible) {
        RoundExitDialog(
            onKeepLearning = { confirmExit = false },
            onLeave = ::leave,
            body = stringResource(R.string.round2_leave_body)
        )
    }
}

@Composable
private fun Round2QuestionScreen(
    state: Round2UiState.Question,
    onSelect: (String) -> Unit,
    onCheck: () -> Unit,
    onNext: () -> Unit,
    onAudio: () -> Unit,
    onExit: () -> Unit
) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier = Modifier.widthIn(max = 520.dp).fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            RoundHeading(stringResource(R.string.round2_title), subtitle = null, onExit = onExit)
            val completed = state.number - 1
            RoundProgress(
                processed = completed,
                total = state.total,
                caption = stringResource(R.string.round2_question_number, state.number, state.total),
                progressLabel = stringResource(R.string.round2_progress, completed, state.total)
            )
            key(state.question.id) {
                Round2QuestionContent(
                    state = state,
                    onSelect = onSelect,
                    onAudio = onAudio,
                    modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(vertical = 12.dp)
                )
            }
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val feedback = state.feedback
                AnimatedVisibility(visible = feedback != null, enter = fadeIn(tween(200))) {
                    if (feedback != null) Round2FeedbackPanel(state, feedback)
                }
                if (feedback == null) {
                    RoundPrimaryButton(
                        stringResource(R.string.round2_check),
                        onCheck,
                        enabled = state.selectedOptionId != null,
                        neutralWhenDisabled = true
                    )
                } else {
                    RoundPrimaryButton(stringResource(R.string.round_continue), onNext)
                }
            }
        }
    }
}

@Composable
private fun Round2FeedbackPanel(state: Round2UiState.Question, feedback: Round2Feedback) {
    val colors = roundColors()
    val statusColor = round2StatusColor(feedback.isCorrect)
    val answer = if (!feedback.isCorrect && state.question.variant != Round2Variant.Picture) {
        state.question.options.firstOrNull { it.id == feedback.correctOptionId }?.label
    } else {
        null
    }
    Row(
        modifier = Modifier.fillMaxWidth().background(lerp(colors.surface, statusColor, 0.15f), RoundedCornerShape(20.dp))
            .semantics { liveRegion = LiveRegionMode.Polite }.padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            if (feedback.isCorrect) Icons.Rounded.CheckCircle else Icons.Rounded.Cancel,
            contentDescription = null,
            tint = statusColor,
            modifier = Modifier.size(24.dp)
        )
        Column {
            Text(
                stringResource(if (feedback.isCorrect) R.string.round2_correct else R.string.round2_wrong),
                color = statusColor,
                style = OwlaType.h2
            )
            if (answer != null) {
                Text(stringResource(R.string.round2_correct_answer, answer), color = colors.ink, style = RoundCaption)
            }
        }
    }
}

@Composable
private fun Round2Completion(state: Round2UiState.Completed, onRetry: () -> Unit, onExit: () -> Unit) {
    RoundCompletionScreen(
        summary = stringResource(R.string.round2_result, state.correctCount, state.total),
        stars = state.stars,
        actionLabel = stringResource(if (state.passed) R.string.round_continue else R.string.round_retry),
        onAction = if (state.passed) onExit else onRetry,
        mascot = if (state.passed) R.drawable.ic_owla_celebrate else R.drawable.ic_owla,
        secondaryAction = if (state.passed) null else {
            { RoundNeutralButton(stringResource(R.string.round_back_to_lesson), onExit) }
        }
    )
}
