package com.nhom21.english_learning_app.core.ui.components

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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.nhom21.english_learning_app.R
import com.nhom21.english_learning_app.ui.theme.OwlaType
import com.nhom21.english_learning_app.ui.theme.roundColors

internal data class VocabularyCardContent(
    val word: String,
    val partOfSpeech: String?,
    val phonetic: String,
    val meaning: String,
    val example: String?,
    val isInFlashcards: Boolean
)

enum class VocabularyAudioState { Ready, Unavailable, Loading, Playing, Error }

@Composable
internal fun VocabularySwipeCard(
    word: VocabularyCardContent,
    audio: VocabularyAudioState,
    cardsBehind: Int,
    onAudio: () -> Unit,
    modifier: Modifier = Modifier,
    cardModifier: Modifier = Modifier,
    swipeFraction: Float = 0f,
    imageContent: (@Composable () -> Unit)? = null
) {
    val colors = roundColors()
    val cardShape = RoundedCornerShape(28.dp)
    val stackSize = cardsBehind.coerceIn(0, 2)
    val add = swipeFraction > 0f

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
                imageContent?.invoke()
                Text(word.word, style = OwlaType.display, color = colors.accentInk, textAlign = TextAlign.Center)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (word.partOfSpeech != null) {
                        Text(
                            word.partOfSpeech,
                            Modifier.background(colors.border, CircleShape).padding(horizontal = 10.dp, vertical = 4.dp),
                            color = colors.ink,
                            style = OwlaType.caption
                        )
                    }
                    Text(
                        word.phonetic,
                        modifier = Modifier.weight(1f, fill = false),
                        color = colors.ink,
                        style = OwlaType.body.copy(fontFamily = FontFamily.SansSerif),
                        textAlign = TextAlign.Center
                    )
                    val audioLabel = if (audio == VocabularyAudioState.Playing) {
                        R.string.round_audio_playing
                    } else {
                        R.string.round_audio_play
                    }
                    IconButton(
                        onClick = onAudio,
                        enabled = audio == VocabularyAudioState.Ready || audio == VocabularyAudioState.Error,
                        modifier = Modifier.size(48.dp)
                    ) {
                        val audioColor = when (audio) {
                            VocabularyAudioState.Unavailable, VocabularyAudioState.Error -> colors.border
                            else -> colors.primaryFace
                        }
                        Box(
                            Modifier.size(36.dp).background(audioColor, CircleShape)
                                .then(
                                    if (audio == VocabularyAudioState.Playing) Modifier.border(2.dp, colors.accentInk, CircleShape)
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
                    VocabularyAudioState.Loading -> R.string.round_audio_loading
                    VocabularyAudioState.Playing -> R.string.round_audio_playing
                    VocabularyAudioState.Unavailable -> R.string.round_audio_unavailable
                    VocabularyAudioState.Error -> R.string.round_audio_error
                    VocabularyAudioState.Ready -> null
                }
                if (audioStatus != null) {
                    Text(stringResource(audioStatus), style = OwlaType.caption, color = colors.ink, textAlign = TextAlign.Center)
                }
                HorizontalDivider(color = colors.border)
                Text(word.meaning, style = OwlaType.h1, color = colors.accentInk, textAlign = TextAlign.Center)
                if (word.example != null) {
                    Text(word.example, style = OwlaType.body, color = colors.ink, textAlign = TextAlign.Center)
                }
            }
            if (swipeFraction != 0f) {
                SwipeActionIndicator(
                    swipeFraction,
                    word.isInFlashcards,
                    Modifier.align(if (add) Alignment.TopStart else Alignment.TopEnd)
                        .offset(y = (-13).dp).padding(horizontal = 16.dp)
                )
            }
        }
    }
}

