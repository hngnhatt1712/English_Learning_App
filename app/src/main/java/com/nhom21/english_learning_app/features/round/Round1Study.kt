package com.nhom21.english_learning_app.features.round

import android.animation.ValueAnimator
import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BookmarkAdd
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.nhom21.english_learning_app.R
import com.nhom21.english_learning_app.core.ui.components.VocabularySwipeCard
import com.nhom21.english_learning_app.features.round.components.RoundCaption
import com.nhom21.english_learning_app.features.round.components.RoundNeutralButton
import com.nhom21.english_learning_app.features.round.components.RoundPrimaryButton
import com.nhom21.english_learning_app.ui.theme.roundColors
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

@Composable
internal fun Round1Study(
    state: Round1UiState.Learning,
    onAction: (String, Round1Action) -> Unit,
    onAudio: (String) -> Unit,
    modifier: Modifier = Modifier,
    interactionEnabled: Boolean = true,
    imageContent: (@Composable (String) -> Unit)? = null,
    claimSwipeDemo: (suspend () -> Boolean)? = null
) {
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val latestAction by rememberUpdatedState(onAction)
    val view = LocalView.current
    val canMove = interactionEnabled && state.actionsEnabled
    val canStillMove by rememberUpdatedState(canMove)
    var committing by remember { mutableStateOf(false) }
    var offset by remember { mutableFloatStateOf(0f) }
    var swipeWidth by remember { mutableFloatStateOf(1f) }
    var settleJob by remember { mutableStateOf<Job?>(null) }
    var ticked by remember { mutableStateOf(false) }
    var touched by remember { mutableStateOf(false) }
    var showHand by remember { mutableStateOf(false) }
    val handOffset = remember { Animatable(0f) }
    val reducedMotion = Build.VERSION.SDK_INT >= 26 && !ValueAnimator.areAnimatorsEnabled()
    val velocityThreshold = with(density) { 900.dp.toPx() }
    val cardThreshold = swipeWidth * 0.4f

    LaunchedEffect(canMove, touched) {
        if (canMove && !touched && claimSwipeDemo?.invoke() == true) {
            showHand = true
            try {
                if (reducedMotion) delay(750)
                else handOffset.animateTo(1f, tween(750))
            } finally {
                showHand = false
            }
        }
    }

    fun returnCard() {
        settleJob?.cancel()
        settleJob = scope.launch {
            animate(offset, 0f, animationSpec = spring()) { value, _ -> offset = value }
        }
    }

    fun commit(action: Round1Action) {
        if (committing || !canStillMove) return
        touched = true
        committing = true
        settleJob?.cancel()
        scope.launch {
            try {
                if (!reducedMotion) {
                    val direction = if (action == Round1Action.Skip) -1f else 1f
                    animate(offset, direction * swipeWidth * 1.45f, animationSpec = tween(200)) { value, _ ->
                        offset = value
                    }
                }
                if (canStillMove) latestAction(state.word.id, action)
            } finally {
                offset = 0f
                committing = false
            }
        }
    }

    val drag = rememberDraggableState { change ->
        if (!committing && canMove) {
            offset = (offset + change).coerceIn(-swipeWidth, swipeWidth)
            if (!ticked && abs(offset) >= cardThreshold) {
                ticked = true
                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            }
        }
    }
    Column(
        modifier.widthIn(max = 480.dp).fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            VocabularySwipeCard(
                word = state.word.cardContent(),
                audio = state.audio,
                cardsBehind = state.remainingCardsBehind,
                onAudio = { if (interactionEnabled) onAudio(state.word.id) },
                modifier = Modifier.onSizeChanged { swipeWidth = it.width.coerceAtLeast(1).toFloat() },
                cardModifier = Modifier.draggable(
                    state = drag,
                    orientation = Orientation.Horizontal,
                    enabled = canMove && !committing,
                    onDragStarted = {
                        settleJob?.cancel()
                        ticked = false
                        touched = true
                    },
                    onDragStopped = { velocity ->
                        val enoughDistance = abs(offset) >= cardThreshold
                        val fling = abs(velocity) >= velocityThreshold && abs(offset) > cardThreshold / 5 &&
                            (velocity > 0f) == (offset > 0f)
                        if (enoughDistance || fling) {
                            val action = when {
                                offset < 0f -> Round1Action.Skip
                                state.word.isInFlashcards -> Round1Action.KeepAdded
                                else -> Round1Action.Add
                            }
                            commit(action)
                        } else if (!committing) {
                            returnCard()
                        }
                    }
                ).graphicsLayer {
                    translationX = if (reducedMotion) 0f else offset
                    rotationZ = if (reducedMotion) 0f else (offset / cardThreshold).coerceIn(-1f, 1f) * 12f
                },
                swipeFraction = (offset / cardThreshold).coerceIn(-1f, 1f),
                imageContent = state.word.imageUrl?.let { url ->
                    imageContent?.let { content -> { content(url) } }
                }
            )
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(
                    stringResource(R.string.round_swipe_hint),
                    style = RoundCaption,
                    color = roundColors().ink,
                    textAlign = TextAlign.Center
                )
                if (showHand) {
                    Icon(
                        Icons.Rounded.TouchApp,
                        contentDescription = null,
                        tint = roundColors().accentInk,
                        modifier = Modifier.size(32.dp).graphicsLayer {
                            translationX = 72.dp.toPx() * handOffset.value
                            translationY = -36.dp.toPx()
                            alpha = 1f - handOffset.value * 0.6f
                        }
                    )
                }
            }
        }
        Round1ActionButtons(
            alreadyAdded = state.word.isInFlashcards,
            enabled = canMove && !committing,
            onSkip = { commit(Round1Action.Skip) },
            onAdd = { commit(if (state.word.isInFlashcards) Round1Action.KeepAdded else Round1Action.Add) }
        )
    }
}

@Composable
private fun Round1ActionButtons(
    alreadyAdded: Boolean,
    enabled: Boolean,
    onSkip: () -> Unit,
    onAdd: () -> Unit
) {
    val colors = roundColors()
    val skip: @Composable (Modifier) -> Unit = { modifier ->
        RoundNeutralButton(stringResource(R.string.round_skip), onSkip, modifier, enabled) {
            Icon(Icons.Rounded.SkipNext, null, tint = colors.ink, modifier = Modifier.size(20.dp))
        }
    }
    val add: @Composable (Modifier) -> Unit = { modifier ->
        val label = stringResource(if (alreadyAdded) R.string.round_added else R.string.round_add)
        RoundPrimaryButton(label, onAdd, modifier, enabled) {
            Icon(
                if (alreadyAdded) Icons.Rounded.Check else Icons.Rounded.BookmarkAdd,
                null,
                tint = colors.primaryLabel,
                modifier = Modifier.size(20.dp)
            )
        }
    }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        if (maxWidth < 286.dp * LocalDensity.current.fontScale) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                add(Modifier)
                skip(Modifier)
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { skip(Modifier) }
                Box(Modifier.weight(1.9f)) { add(Modifier) }
            }
        }
    }
}
