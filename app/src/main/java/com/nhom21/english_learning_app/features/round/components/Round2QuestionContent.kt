package com.nhom21.english_learning_app.features.round.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.nhom21.english_learning_app.ui.theme.roundColors
import com.nhom21.english_learning_app.R
import com.nhom21.english_learning_app.core.ui.components.AnswerOption
import com.nhom21.english_learning_app.core.ui.components.AnswerOptionState
import com.nhom21.english_learning_app.features.round.RoundWordImage
import com.nhom21.english_learning_app.features.round.Round2Option
import com.nhom21.english_learning_app.features.round.Round2Question
import com.nhom21.english_learning_app.features.round.Round2UiState
import com.nhom21.english_learning_app.features.round.Round2Variant
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
        Round2Variant.Text -> R.string.round2_choose_answer
        Round2Variant.Picture -> R.string.round2_choose_picture
    }
    Text(stringResource(instruction), style = OwlaType.h2, color = colors.ink, textAlign = TextAlign.Center)
    if (question.imageUrl != null) {
        RoundWordImage(question.imageUrl, Modifier.fillMaxWidth().height(112.dp))
    } else if (question.variant == Round2Variant.Meaning) {
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
            round2DisplayPrompt(question.prompt),
            modifier = Modifier.weight(1f, fill = false),
            style = OwlaType.display,
            color = colors.accentInk,
            textAlign = TextAlign.Center
        )
        if (question.audioUrl != null || question.illustrationRes != null) {
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

internal fun round2DisplayPrompt(prompt: String): String {
    val templates = listOf(
        "Chọn hình minh họa cho từ «" to "».",
        "Từ «" to "» có nghĩa là gì?",
    )
    for ((prefix, suffix) in templates) {
        if (prompt.startsWith(prefix) && prompt.endsWith(suffix) && prompt.length >= prefix.length + suffix.length) {
            val word = prompt.substring(prefix.length, prompt.length - suffix.length)
            if (word.isNotBlank() && word.none { it == '«' || it == '»' || it == '\n' || it == '\r' }) {
                return word
            }
        }
    }
    return prompt
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
    optionDescription: String? = null,
) {
    val colors = roundColors()
    val feedback = state.feedback
    val selected = state.selectedOptionId == option.id
    val optionState = when {
        feedback == null -> if (selected) AnswerOptionState.Selected else AnswerOptionState.Default
        selected && !feedback.isCorrect -> AnswerOptionState.Wrong
        option.id == feedback.correctOptionId || selected && feedback.isCorrect -> AnswerOptionState.Correct
        else -> AnswerOptionState.Locked
    }
    val isPicture = state.question.variant == Round2Variant.Picture
    AnswerOption(
        state = optionState,
        onClick = onClick,
        modifier = modifier,
        selected = selected,
        enabled = !state.checking,
        optionDescription = optionDescription,
        indicatorAlignment = if (isPicture) Alignment.TopEnd else Alignment.CenterEnd,
    ) {
        if (isPicture) {
            Round2OptionImage(option, Modifier.fillMaxWidth().heightIn(min = 64.dp).aspectRatio(1.55f).padding(12.dp))
        } else {
            Text(
                option.label.orEmpty(),
                modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 48.dp, top = 8.dp, bottom = 8.dp),
                style = OwlaType.body.copy(fontWeight = FontWeight.Medium),
                color = colors.ink,
            )
        }
    }
}

@Composable
internal fun Round2OptionImage(option: Round2Option, modifier: Modifier = Modifier) {
    val image = option.imageRes
    Box(modifier, contentAlignment = Alignment.Center) {
        when {
            !option.imageUrl.isNullOrBlank() -> RoundWordImage(option.imageUrl, Modifier.fillMaxSize())
            image != null -> Image(
                painterResource(image),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
            )
            else -> Icon(Icons.Rounded.Image, contentDescription = null, tint = roundColors().ink.copy(alpha = 0.45f))
        }
    }
}
