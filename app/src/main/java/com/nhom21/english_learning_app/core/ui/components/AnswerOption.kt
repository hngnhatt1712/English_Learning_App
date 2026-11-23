package com.nhom21.english_learning_app.core.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.RadioButtonChecked
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.nhom21.english_learning_app.R
import com.nhom21.english_learning_app.ui.theme.OwlaColors
import com.nhom21.english_learning_app.ui.theme.roundColors
import kotlin.coroutines.coroutineContext
import kotlin.math.PI
import kotlin.math.sin

enum class AnswerOptionState { Default, Selected, Correct, Wrong, Locked }

@Composable
fun AnswerOption(
    state: AnswerOptionState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = state == AnswerOptionState.Selected,
    enabled: Boolean = true,
    optionDescription: String? = null,
    indicatorAlignment: Alignment = Alignment.CenterEnd,
    content: @Composable BoxScope.() -> Unit,
) {
    val colors = roundColors()
    val isCorrect = state == AnswerOptionState.Correct
    val isWrong = state == AnswerOptionState.Wrong
    val isGraded = isCorrect || isWrong
    val canSelect = enabled && !isGraded && state != AnswerOptionState.Locked
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val depth = 4.dp
    val offset by animateDpAsState(
        if (pressed && canSelect) depth else 0.dp,
        animationSpec = tween(80, easing = LinearOutSlowInEasing),
        label = "AnswerOptionPress",
    )
    val face = when (state) {
        AnswerOptionState.Default -> colors.surface
        AnswerOptionState.Selected -> lerp(colors.surface, colors.primaryFace, 0.10f)
        AnswerOptionState.Correct -> lerp(colors.surface, OwlaColors.SuccessBase, 0.15f)
        AnswerOptionState.Wrong -> lerp(colors.surface, OwlaColors.DangerBase, 0.15f)
        AnswerOptionState.Locked -> lerp(colors.surface, colors.background, 0.60f)
    }
    val border = when (state) {
        AnswerOptionState.Selected -> colors.primaryFace
        AnswerOptionState.Correct -> colors.successInk
        AnswerOptionState.Wrong -> colors.dangerInk
        AnswerOptionState.Locked -> lerp(colors.border, colors.background, 0.60f)
        AnswerOptionState.Default -> colors.border
    }
    val indicator = when {
        isCorrect -> Icons.Rounded.CheckCircle
        isWrong -> Icons.Rounded.Cancel
        selected -> Icons.Rounded.RadioButtonChecked
        else -> Icons.Rounded.RadioButtonUnchecked
    }
    val indicatorTint = when {
        isCorrect -> colors.successInk
        isWrong -> colors.dangerInk
        selected -> colors.accentInk
        else -> colors.ink.copy(alpha = 0.65f)
    }
    val description = when {
        isCorrect -> stringResource(R.string.answer_option_correct)
        isWrong -> stringResource(R.string.answer_option_wrong)
        else -> null
    }
    val shape = RoundedCornerShape(20.dp)
    val shake = remember { Animatable(0f) }
    val pop = remember { Animatable(1f) }
    // A composed answer starts at rest, including when a screen returns with existing feedback.
    var awaitingGrade by remember { mutableStateOf(!isGraded) }
    LaunchedEffect(isGraded) {
        if (isGraded && awaitingGrade) {
            awaitingGrade = false
            if (coroutineContext[MotionDurationScale]?.scaleFactor == 0f) return@LaunchedEffect
            if (isCorrect) {
                pop.animateTo(1f, keyframes {
                    durationMillis = 200
                    0.8f at 0
                    1.15f at 95
                })
            } else {
                shake.animateTo(1f, tween(150, easing = LinearEasing))
            }
        } else if (!isGraded) {
            awaitingGrade = true
            shake.snapTo(0f)
            pop.snapTo(1f)
        }
    }
    Box(
        modifier.fillMaxWidth()
            .selectable(
                selected = selected,
                enabled = canSelect,
                role = Role.RadioButton,
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
            )
            .semantics {
                optionDescription?.let { contentDescription = it }
                description?.let { stateDescription = it }
            }
            .graphicsLayer {
                if (isWrong) {
                    val phase = shake.value
                    translationX = 4.dp.toPx() * sin(phase * 4 * PI).toFloat() * (1f - phase)
                }
            }
            .background(if (state == AnswerOptionState.Selected) colors.primaryBase else border, shape)
            .padding(top = offset, bottom = depth - offset),
    ) {
        Box(
            Modifier.fillMaxWidth().heightIn(min = 64.dp).background(face, shape).border(2.dp, border, shape),
            contentAlignment = Alignment.CenterStart,
        ) {
            content()
            Icon(
                indicator,
                contentDescription = null,
                tint = indicatorTint,
                modifier = Modifier.align(indicatorAlignment).padding(8.dp).size(24.dp)
                    .background(face, CircleShape)
                    .graphicsLayer {
                        scaleX = pop.value
                        scaleY = pop.value
                    },
            )
        }
    }
}
