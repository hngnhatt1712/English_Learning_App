package com.nhom21.english_learning_app.features.round.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.RadioButtonChecked
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.nhom21.english_learning_app.R
import com.nhom21.english_learning_app.features.round.Round2Option
import com.nhom21.english_learning_app.features.round.Round2Question
import com.nhom21.english_learning_app.features.round.Round2UiState
import com.nhom21.english_learning_app.features.round.Round2Variant
import com.nhom21.english_learning_app.ui.theme.OwlaColors
import com.nhom21.english_learning_app.ui.theme.OwlaType

@Composable
internal fun Round2QuestionContent(
    state: Round2UiState.Question,
    onSelect: (String) -> Unit,
    onAudio: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Round2Prompt(state.question, onAudio)
        if (state.question.variant == Round2Variant.Picture) {
            Round2PictureOptions(state, onSelect)
        } else {
            Column(
                modifier = Modifier.fillMaxWidth().selectableGroup(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                state.question.options.forEach { option ->
                    key(option.id) {
                        Round2AnswerOption(state, option, onClick = { onSelect(option.id) })
                    }
                }
            }
        }
    }
}

@Composable
private fun Round2Prompt(question: Round2Question, onAudio: () -> Unit) {
    val colors = roundColors()
    val instruction = when (question.variant) {
        Round2Variant.Meaning -> R.string.round2_choose_meaning
        Round2Variant.Word -> R.string.round2_choose_word
        Round2Variant.Picture -> R.string.round2_choose_picture
    }
    Text(stringResource(instruction), style = OwlaType.h2, color = colors.ink, textAlign = TextAlign.Center)
    if (question.variant == Round2Variant.Meaning) {
        question.illustrationRes?.let { image ->
            Image(
                painterResource(image),
                contentDescription = null,
                modifier = Modifier.fillMaxWidth().height(112.dp),
                contentScale = ContentScale.Fit
            )
        }
    }
    Row(
        modifier = Modifier.fillMaxWidth()
            .background(lerp(colors.surface, colors.primaryFace, 0.12f), RoundedCornerShape(20.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            question.prompt,
            modifier = Modifier.weight(1f, fill = false),
            style = OwlaType.display,
            color = colors.accentInk,
            textAlign = TextAlign.Center
        )
        if (question.variant != Round2Variant.Word) {
            IconButton(onClick = onAudio, modifier = Modifier.size(48.dp)) {
                Box(
                    Modifier.size(36.dp).background(colors.primaryFace, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.AutoMirrored.Rounded.VolumeUp,
                        stringResource(R.string.round_audio_play),
                        tint = colors.primaryLabel,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun Round2PictureOptions(state: Round2UiState.Question, onSelect: (String) -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().selectableGroup(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        state.question.options.chunked(2).forEachIndexed { rowIndex, options ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                options.forEachIndexed { columnIndex, option ->
                    key(option.id) {
                        Round2AnswerOption(
                            state = state,
                            option = option,
                            optionDescription = stringResource(R.string.round2_picture_option, rowIndex * 2 + columnIndex + 1),
                            onClick = { onSelect(option.id) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Round2AnswerOption(
    state: Round2UiState.Question,
    option: Round2Option,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    optionDescription: String? = null
) {
    val colors = roundColors()
    val feedback = state.feedback
    val selected = state.selectedOptionId == option.id
    val correct = feedback != null && (feedback.correctOptionId == option.id || selected && feedback.isCorrect)
    val wrong = feedback != null && selected && !feedback.isCorrect
    val highlight = when {
        correct -> round2StatusColor(true)
        wrong -> round2StatusColor(false)
        selected -> colors.accentInk
        else -> colors.border
    }
    val face = when {
        correct || wrong -> lerp(colors.surface, highlight, 0.15f)
        selected -> lerp(colors.surface, colors.primaryFace, 0.10f)
        else -> colors.surface
    }
    val statusIcon = when {
        correct -> Icons.Rounded.CheckCircle
        wrong -> Icons.Rounded.Cancel
        else -> null
    }
    val description = when {
        correct -> stringResource(R.string.round2_option_correct)
        wrong -> stringResource(R.string.round2_option_wrong)
        else -> null
    }
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val depth = 4.dp
    val offset by animateDpAsState(
        if (pressed && feedback == null) depth else 0.dp,
        animationSpec = tween(100),
        label = "Round2AnswerPress"
    )
    val shape = RoundedCornerShape(20.dp)
    val shadowColor = if (selected && feedback == null) colors.primaryBase else colors.border
    Box(
        modifier = modifier.fillMaxWidth()
            .background(shadowColor, shape)
            .selectable(
                selected = selected,
                enabled = feedback == null,
                role = Role.RadioButton,
                interactionSource = interaction,
                indication = null,
                onClick = onClick
            )
            .semantics {
                optionDescription?.let { contentDescription = it }
                description?.let { stateDescription = it }
            }
            .padding(top = offset, bottom = depth - offset)
    ) {
        val faceModifier = Modifier.fillMaxWidth().background(face, shape).border(2.dp, highlight, shape)
        if (state.question.variant == Round2Variant.Picture) {
            Box(faceModifier.aspectRatio(1.55f).heightIn(min = 64.dp), contentAlignment = Alignment.Center) {
                val image = option.imageRes
                if (image != null) {
                    Image(
                        painterResource(image),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize().padding(12.dp),
                        contentScale = ContentScale.Fit
                    )
                } else {
                    Icon(Icons.Rounded.Image, contentDescription = null, tint = colors.ink.copy(alpha = 0.45f))
                }
                val indicator = statusIcon ?: Icons.Rounded.RadioButtonChecked.takeIf { selected }
                if (indicator != null) {
                    Icon(
                        indicator,
                        contentDescription = null,
                        tint = highlight,
                        modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)
                            .size(22.dp).background(face, CircleShape)
                    )
                }
            }
        } else {
            Row(
                modifier = faceModifier.heightIn(min = 64.dp).padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    if (selected) Icons.Rounded.RadioButtonChecked else Icons.Rounded.RadioButtonUnchecked,
                    contentDescription = null,
                    tint = if (selected) highlight else colors.ink.copy(alpha = 0.65f),
                    modifier = Modifier.size(22.dp)
                )
                Text(
                    option.label.orEmpty(),
                    modifier = Modifier.weight(1f),
                    style = OwlaType.body.copy(fontWeight = FontWeight.Medium),
                    color = colors.ink
                )
                if (statusIcon != null) {
                    Icon(statusIcon, contentDescription = null, tint = highlight, modifier = Modifier.size(24.dp))
                }
            }
        }
    }
}

@Composable
internal fun round2StatusColor(correct: Boolean): Color {
    val colors = roundColors()
    return when {
        correct -> colors.successInk
        isSystemInDarkTheme() -> OwlaColors.DangerBase
        else -> lerp(OwlaColors.DangerShadow, colors.ink, 0.2f)
    }
}
