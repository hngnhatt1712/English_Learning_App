package com.nhom21.english_learning_app.features.round.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.BookmarkAdd
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.nhom21.english_learning_app.R
import com.nhom21.english_learning_app.features.round.Round1Action
import com.nhom21.english_learning_app.features.round.Round1Word
import com.nhom21.english_learning_app.features.round.RoundAudioState
import com.nhom21.english_learning_app.features.round.roundPartOfSpeechLabel
import com.nhom21.english_learning_app.ui.theme.OwlaType

@Composable
internal fun VocabularySwipeCard(
    word: Round1Word,
    audio: RoundAudioState,
    cardsBehind: Int,
    onAudio: () -> Unit,
    modifier: Modifier = Modifier,
    cardModifier: Modifier = Modifier,
    swipeFraction: Float = 0f,
    showExample: Boolean = false,
    imageContent: (@Composable (String) -> Unit)? = null
) {
    val colors = roundColors()
    val cardShape = RoundedCornerShape(28.dp)
    val stackSize = cardsBehind.coerceIn(0, 2)
    val add = swipeFraction > 0f
    val hintColor = if (add) colors.successInk else colors.ink

    Box(modifier.fillMaxWidth()) {
        for (depth in stackSize downTo 1) {
            Box(
                Modifier.matchParentSize()
                    .padding(horizontal = (depth * 8).dp, vertical = (stackSize * 4).dp)
                    .offset(y = (depth * 7).dp)
                    .background(colors.surface, cardShape)
                    .border(2.dp, colors.border, cardShape)
            )
        }
        Box(Modifier.fillMaxWidth().padding(bottom = (stackSize * 12).dp).then(cardModifier)) {
            Column(
                modifier = Modifier.fillMaxWidth().shadow(4.dp, cardShape)
                    .background(colors.surface, cardShape)
                    .border(2.dp, colors.border, cardShape)
                    .padding(horizontal = 20.dp, vertical = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                val imageUrl = word.imageUrl
                if (imageUrl != null && imageContent != null) {
                    imageContent(imageUrl)
                }
                Text(word.word, style = OwlaType.display, color = colors.accentInk, textAlign = TextAlign.Center)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val label = roundPartOfSpeechLabel(word.partOfSpeech)
                    if (label != null) {
                        Text(
                            stringResource(label),
                            Modifier.background(colors.border, CircleShape).padding(horizontal = 10.dp, vertical = 4.dp),
                            color = colors.ink,
                            style = RoundCaption
                        )
                    }
                    Text(
                        word.phonetic,
                        modifier = Modifier.weight(1f, fill = false),
                        color = colors.ink,
                        style = OwlaType.body.copy(fontFamily = FontFamily.SansSerif),
                        textAlign = TextAlign.Center
                    )
                    val audioLabel = if (audio == RoundAudioState.Playing) {
                        R.string.round_audio_playing
                    } else {
                        R.string.round_audio_play
                    }
                    IconButton(
                        onClick = onAudio,
                        enabled = audio == RoundAudioState.Ready || audio == RoundAudioState.Error,
                        modifier = Modifier.size(48.dp)
                    ) {
                        val audioColor = when (audio) {
                            RoundAudioState.Unavailable, RoundAudioState.Error -> colors.border
                            else -> colors.primaryFace
                        }
                        Box(
                            Modifier.size(36.dp).background(audioColor, CircleShape)
                                .then(
                                    if (audio == RoundAudioState.Playing) Modifier.border(2.dp, colors.accentInk, CircleShape)
                                    else Modifier
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.AutoMirrored.Rounded.VolumeUp,
                                stringResource(audioLabel),
                                tint = if (audioColor == colors.border) colors.ink else colors.primaryLabel,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }
                val audioStatus = when (audio) {
                    RoundAudioState.Loading -> R.string.round_audio_loading
                    RoundAudioState.Playing -> R.string.round_audio_playing
                    RoundAudioState.Unavailable -> R.string.round_audio_unavailable
                    RoundAudioState.Error -> R.string.round_audio_error
                    RoundAudioState.Ready -> null
                }
                if (audioStatus != null) {
                    Text(stringResource(audioStatus), style = RoundCaption, color = colors.ink, textAlign = TextAlign.Center)
                }
                HorizontalDivider(color = colors.border)
                Text(word.meaning, style = OwlaType.h1, color = colors.accentInk, textAlign = TextAlign.Center)
                if (showExample && word.example != null) {
                    Text(word.example, style = OwlaType.body, color = colors.ink, textAlign = TextAlign.Center)
                }
            }
            if (swipeFraction != 0f && (!word.isInFlashcards || !add)) {
                Row(
                    modifier = Modifier.align(if (add) Alignment.TopStart else Alignment.TopEnd)
                        .offset(y = (-13).dp)
                        .padding(horizontal = 16.dp)
                        .graphicsLayer { alpha = kotlin.math.abs(swipeFraction).coerceIn(0f, 1f) }
                        .background(colors.surface, CircleShape).border(2.dp, hintColor, CircleShape)
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        if (add) Icons.Rounded.BookmarkAdd else Icons.Rounded.SkipNext,
                        contentDescription = null,
                        tint = hintColor,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(stringResource(if (add) R.string.round_swipe_add else R.string.round_swipe_skip), color = hintColor, style = RoundCaption)
                }
            }
        }
    }
}

internal fun vocabularyForwardAction(word: Round1Word): Round1Action =
    if (word.isInFlashcards) Round1Action.KeepAdded else Round1Action.Add
