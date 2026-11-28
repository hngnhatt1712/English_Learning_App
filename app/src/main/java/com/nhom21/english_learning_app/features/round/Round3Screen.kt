package com.nhom21.english_learning_app.features.round

import android.graphics.Color as AndroidColor
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nhom21.english_learning_app.R
import com.nhom21.english_learning_app.core.network.ApiErrorType
import com.nhom21.english_learning_app.domain.model.MatchingItem
import com.nhom21.english_learning_app.features.round.components.MatchingBackButton
import com.nhom21.english_learning_app.features.round.components.MatchingButton
import com.nhom21.english_learning_app.features.round.components.MatchingButtonTone
import com.nhom21.english_learning_app.features.round.components.MatchingExitDialog
import com.nhom21.english_learning_app.features.round.components.MatchingLoading
import com.nhom21.english_learning_app.features.round.components.MatchingProgress
import com.nhom21.english_learning_app.features.round.components.MatchingRepairIntro
import com.nhom21.english_learning_app.features.round.components.MatchingResult
import com.nhom21.english_learning_app.features.round.components.MatchingStatusScreen
import com.nhom21.english_learning_app.features.round.components.MatchingTile
import com.nhom21.english_learning_app.features.round.components.MatchingTileState
import com.nhom21.english_learning_app.features.round.components.matchingColors
import com.nhom21.english_learning_app.features.round.components.matchingTextStyle
import kotlinx.coroutines.delay

private const val MatchingTransitionMillis = 220L
private const val WrongFeedbackMillis = 550L

@Composable
internal fun Round3Screen(
    state: Round3UiState,
    onSelectLeft: (String, String) -> Unit,
    onSelectRight: (String, String) -> Unit,
    onClearFeedback: (String, Long) -> Unit,
    onNext: (String) -> Unit,
    onStartRepair: () -> Unit,
    onFinishRepair: () -> Unit,
    onRetry: () -> Unit,
    onExit: () -> Unit,
    topicName: String? = null,
    imageContent: @Composable (String, Modifier) -> Unit = { url, modifier -> RoundWordImage(url, modifier) },
    previewTransition: Boolean = false,
) {
    var confirmExit by rememberSaveable { mutableStateOf(false) }
    val inspecting = LocalInspectionMode.current
    val repairing = state is Round3UiState.RepairIntro || (state is Round3UiState.Question && state.isRepair)
    val requestExit = { confirmExit = true }
    MatchingSystemBars(isSystemInDarkTheme())
    BackHandler(enabled = !confirmExit && state != Round3UiState.Exited) {
        if (state is Round3UiState.Completed) onExit() else requestExit()
    }

    when (state) {
        Round3UiState.Loading -> MatchingLoading(requestExit)
        Round3UiState.LoadError -> MatchingStatusScreen(
            title = R.string.round3_load_error,
            description = R.string.round3_load_error_body,
            action = R.string.round_retry,
            onAction = onRetry,
            onExit = onExit,
        )
        is Round3UiState.Question -> key(state.isRepair, state.question.id) {
            var transitioning by remember { mutableStateOf(false) }
            val questionId = state.question.id
            val wrong = state.feedback?.takeUnless { it.isCorrect }
            LaunchedEffect(wrong?.id) {
                if (wrong != null) {
                    delay(WrongFeedbackMillis)
                    onClearFeedback(questionId, wrong.id)
                }
            }
            LaunchedEffect(transitioning) {
                if (transitioning) {
                    delay(MatchingTransitionMillis)
                    onNext(questionId)
                    transitioning = false
                }
            }
            MatchingBoard(
                state = state,
                topicName = topicName,
                transitioning = transitioning || (inspecting && previewTransition),
                onSelectLeft = { onSelectLeft(questionId, it) },
                onSelectRight = { onSelectRight(questionId, it) },
                onNext = {
                    if (!transitioning) {
                        if (state.number < state.questionCount) transitioning = true
                        else onNext(questionId)
                    }
                },
                onExit = requestExit,
                imageContent = imageContent,
            )
        }
        is Round3UiState.Submitting -> MatchingStatusScreen(
            title = if (state.error == null) R.string.round3_submitting else R.string.round3_submit_error,
            description = when (state.error) {
                null -> R.string.round3_submitting_body
                ApiErrorType.VALIDATION_ERROR -> R.string.round2_submit_invalid
                else -> R.string.round3_submit_error_body
            },
            action = state.error?.let { R.string.round3_submit_retry },
            onAction = onRetry,
            onExit = requestExit,
        )
        is Round3UiState.RepairIntro -> MatchingRepairIntro(state.total, onStartRepair, requestExit)
        is Round3UiState.Completed -> MatchingResult(state, onRetry, onExit, onStartRepair)
        Round3UiState.Exited -> Unit
    }

    if (confirmExit && state !is Round3UiState.Completed && state != Round3UiState.Exited) {
        MatchingExitDialog(
            repairing = repairing,
            onStay = { confirmExit = false },
            onLeave = {
                confirmExit = false
                if (repairing) onFinishRepair() else onExit()
            },
        )
    }
}

@Composable
private fun MatchingSystemBars(darkTheme: Boolean) {
    val activity = LocalActivity.current as? ComponentActivity ?: return
    val darkScrim = matchingColors.ink.toArgb()
    DisposableEffect(activity, darkTheme, darkScrim) {
        val style = if (darkTheme) {
            SystemBarStyle.dark(AndroidColor.TRANSPARENT)
        } else {
            SystemBarStyle.light(AndroidColor.TRANSPARENT, darkScrim)
        }
        activity.enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
        onDispose { activity.enableEdgeToEdge() }
    }
}

@Composable
private fun MatchingBoard(
    state: Round3UiState.Question,
    topicName: String?,
    transitioning: Boolean,
    onSelectLeft: (String) -> Unit,
    onSelectRight: (String) -> Unit,
    onNext: () -> Unit,
    onExit: () -> Unit,
    imageContent: @Composable (String, Modifier) -> Unit,
) {
    val colors = matchingColors
    val question = state.question
    val wrong = state.feedback?.isCorrect == false
    val locked = state.pendingPair != null || wrong || transitioning
    val hasPictures = question.right.any { !it.imageUrl.isNullOrBlank() }
    val pictureBoard = hasPictures && question.right.all { !it.imageUrl.isNullOrBlank() }
    val title = when {
        state.isRepair -> R.string.round3_repair_title
        pictureBoard -> R.string.round3_picture_title
        else -> R.string.round3_title
    }
    val hint = when {
        transitioning -> R.string.round3_transition_hint
        state.checkError == ApiErrorType.NETWORK -> R.string.round3_check_network
        state.checkError == ApiErrorType.ROUND_LOCKED -> R.string.round_locked
        state.checkError != null -> R.string.round3_check_failed
        wrong -> R.string.round3_wrong
        state.pendingPair != null -> R.string.round2_checking
        state.canContinue -> R.string.round3_all_matched
        state.selectedRightId != null -> R.string.round3_select_pair
        state.selectedLeftId != null && pictureBoard -> R.string.round3_select_image
        state.selectedLeftId != null -> R.string.round3_select_meaning
        else -> R.string.round3_select_pair
    }
    val hintColor = when {
        wrong || state.checkError != null -> colors.wrongInk
        transitioning -> colors.muted
        state.canContinue -> colors.correctInk
        state.selectedLeftId != null || state.selectedRightId != null -> colors.selectedInk
        else -> colors.muted
    }

    Box(
        Modifier.fillMaxSize().background(colors.background).safeDrawingPadding(),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            Modifier.widthIn(max = 520.dp).fillMaxSize().padding(horizontal = 22.dp)
                .padding(top = 14.dp, bottom = 13.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(1.dp)) {
                MatchingBackButton(onExit)
                MatchingProgress(state, topicName, Modifier.weight(1f))
            }
            Column(
                Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
                    .padding(top = 22.dp, bottom = 12.dp),
            ) {
                Text(
                    stringResource(title),
                    Modifier.semantics { heading() },
                    color = colors.ink,
                    style = matchingTextStyle(26, 800).copy(letterSpacing = (-0.35).sp),
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    stringResource(
                        when {
                            pictureBoard -> R.string.round3_picture_hint
                            hasPictures -> R.string.round3_mixed_hint
                            else -> R.string.round3_board_hint
                        }
                    ),
                    color = colors.muted, style = matchingTextStyle(13),
                )
                Spacer(Modifier.height(34.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    val labelStyle = matchingTextStyle(11, 740).copy(letterSpacing = 1.sp)
                    Text(
                        stringResource(R.string.round3_words), Modifier.weight(1f),
                        style = labelStyle, color = colors.muted,
                    )
                    Text(
                        stringResource(
                            when {
                                pictureBoard -> R.string.round3_images
                                hasPictures -> R.string.round3_meanings_and_images
                                else -> R.string.round3_meanings
                            }
                        ),
                        Modifier.weight(1f), style = labelStyle, color = colors.muted,
                    )
                }
                Spacer(Modifier.height(12.dp))
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    val matchedLeft = state.pairs.map { it.leftId }.toSet()
                    val matchedRight = state.pairs.map { it.rightId }.toSet()
                    val wrongPair = state.feedback?.pair?.takeIf { wrong }
                    repeat(maxOf(question.left.size, question.right.size)) { index ->
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            val left = question.left.getOrNull(index)
                            val right = question.right.getOrNull(index)
                            MatchingCell(
                                left, index, state.selectedLeftId, matchedLeft, wrongPair?.leftId,
                                locked, onSelectLeft, Modifier.weight(1f), imageContent,
                            )
                            MatchingCell(
                                right, index, state.selectedRightId, matchedRight, wrongPair?.rightId,
                                locked, onSelectRight, Modifier.weight(1f), imageContent,
                            )
                        }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(hint),
                    Modifier.weight(1f).semantics { liveRegion = LiveRegionMode.Polite },
                    style = matchingTextStyle(13, 650), color = hintColor,
                )
                Text(
                    stringResource(R.string.round3_pair_count, state.pairs.size, question.left.size),
                    style = matchingTextStyle(13, 650), color = colors.muted,
                )
            }
            Spacer(Modifier.height(23.dp))
            val action = when {
                transitioning -> R.string.round3_transition
                state.isRepair -> R.string.round3_continue
                state.number == state.questionCount -> R.string.round3_submit
                else -> R.string.round3_continue
            }
            MatchingButton(
                stringResource(action), onNext, tone = MatchingButtonTone.Yellow,
                enabled = state.canContinue && !locked, loading = transitioning,
            )
        }
    }
}

@Composable
private fun MatchingCell(
    item: MatchingItem?,
    index: Int,
    selectedId: String?,
    matchedIds: Set<String>,
    wrongId: String?,
    locked: Boolean,
    onSelect: (String) -> Unit,
    modifier: Modifier,
    imageContent: @Composable (String, Modifier) -> Unit,
) {
    if (item == null) {
        Spacer(modifier)
        return
    }
    val status = when (item.id) {
        in matchedIds -> MatchingTileState.Correct
        wrongId -> MatchingTileState.Wrong
        selectedId -> MatchingTileState.Selected
        else -> MatchingTileState.Default
    }
    key(item.id) {
        MatchingTile(
            item = item, number = index + 1, state = status, enabled = !locked,
            onClick = { onSelect(item.id) }, modifier = modifier, imageContent = imageContent,
        )
    }
}
