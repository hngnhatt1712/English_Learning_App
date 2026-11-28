package com.nhom21.english_learning_app.features.round.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.progressSemantics
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nhom21.english_learning_app.R
import com.nhom21.english_learning_app.domain.model.MatchingItem
import com.nhom21.english_learning_app.features.round.Round3UiState
import kotlin.math.PI
import kotlin.math.sin

internal data class Round3Palette(
    val background: Color,
    val surface: Color,
    val ink: Color,
    val muted: Color,
    val border: Color,
    val track: Color,
    val accent: Color,
    val selectedFace: Color,
    val selectedBorder: Color,
    val selectedInk: Color,
    val wrongFace: Color,
    val wrongBorder: Color,
    val wrongInk: Color,
    val correctFace: Color,
    val correctBorder: Color,
    val correctInk: Color,
    val disabledFace: Color,
    val disabledInk: Color,
) {
    val yellow = Color(0xFFFFCE54)
    val yellowInk = Color(0xFF282018)
}

private val DarkMatching = Round3Palette(
    background = Color(0xFF151D26),
    surface = Color(0xFF1D2934),
    ink = Color(0xFFF5F7F9),
    muted = Color(0xFFAEBDCA),
    border = Color(0xFF3C4D5C),
    track = Color(0xFF344655),
    accent = Color(0xFFFFCE54),
    selectedFace = Color(0xFF193746),
    selectedBorder = Color(0xFF66B9DD),
    selectedInk = Color(0xFF89D9FA),
    wrongFace = Color(0xFF46292F),
    wrongBorder = Color(0xFFCC7177),
    wrongInk = Color(0xFFFFADAD),
    correctFace = Color(0xFF193C32),
    correctBorder = Color(0xFF4C9F7F),
    correctInk = Color(0xFF83E7B7),
    disabledFace = Color(0xFF273541),
    disabledInk = Color(0xFFAAB9C6),
)

private val LightMatching = Round3Palette(
    background = Color(0xFFECF5F4),
    surface = Color.White,
    ink = Color(0xFF1D2831),
    muted = Color(0xFF4C676A),
    border = Color(0xFFB9CDCB),
    track = Color(0xFFD8E5E4),
    accent = Color(0xFF00695F),
    selectedFace = Color(0xFFDEEDF1),
    selectedBorder = Color(0xFF4388A5),
    selectedInk = Color(0xFF205C77),
    wrongFace = Color(0xFFF1E0DC),
    wrongBorder = Color(0xFFBD666D),
    wrongInk = Color(0xFF8C424A),
    correctFace = Color(0xFFDFEBDE),
    correctBorder = Color(0xFF5B8F73),
    correctInk = Color(0xFF2F684B),
    disabledFace = Color(0xFFE0EAE9),
    disabledInk = Color(0xFF526663),
)

internal val matchingColors: Round3Palette
    @Composable get() = if (isSystemInDarkTheme()) DarkMatching else LightMatching

internal fun matchingTextStyle(size: Int, weight: Int = 490) = TextStyle(
    fontFamily = FontFamily.SansSerif,
    fontSize = size.sp,
    fontWeight = FontWeight(weight),
    lineHeight = (size * 1.25f).sp,
)

@Composable
internal fun MatchingBackButton(onClick: () -> Unit, close: Boolean = false) {
    val colors = matchingColors
    val shape = if (close) CircleShape else RoundedCornerShape(12.dp)
    Box(
        Modifier.size(48.dp).clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            Modifier.size(36.dp).background(colors.surface, shape).border(1.dp, colors.border, shape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (close) Icons.Rounded.Close else Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = stringResource(R.string.round3_close),
                tint = colors.muted,
                modifier = Modifier.size(17.dp),
            )
        }
    }
}

@Composable
internal fun MatchingProgress(state: Round3UiState.Question, topicName: String?, modifier: Modifier = Modifier) {
    val colors = matchingColors
    val total = state.totalQuestions.coerceAtLeast(1)
    val fraction = (state.completedPairsBefore + state.pairs.size).toFloat().div(total).coerceIn(0f, 1f)
    val label = topicName?.takeIf { it.isNotBlank() }?.let { stringResource(R.string.round3_topic, it) }
        ?: stringResource(R.string.round3_header)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                label, Modifier.weight(1f), style = matchingTextStyle(13, 720),
                color = colors.accent, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Text(
                stringResource(
                    if (state.isRepair) R.string.round3_repair_number else R.string.round2_question_number,
                    state.number,
                    state.questionCount,
                ),
                style = matchingTextStyle(13, 670), color = colors.muted,
            )
        }
        Row(
            Modifier.fillMaxWidth().progressSemantics(fraction),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            repeat(state.questionCount) { index ->
                val segment = when {
                    index < state.number - 1 -> 1f
                    index == state.number - 1 -> state.pairs.size.toFloat() / state.question.left.size.coerceAtLeast(1)
                    else -> 0f
                }
                Box(Modifier.weight(1f).height(7.dp).clip(CircleShape).background(colors.track)) {
                    if (segment > 0f) {
                        Box(
                            Modifier.fillMaxWidth(segment.coerceAtMost(1f)).height(7.dp)
                                .background(colors.accent, CircleShape)
                        )
                    }
                }
            }
        }
    }
}

internal enum class MatchingTileState { Default, Selected, Correct, Wrong }

@Composable
internal fun MatchingTile(
    item: MatchingItem,
    number: Int,
    state: MatchingTileState,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier,
    imageContent: @Composable (String, Modifier) -> Unit,
) {
    val colors = matchingColors
    val (face, border, ink) = when (state) {
        MatchingTileState.Default -> Triple(colors.surface, colors.border, colors.ink)
        MatchingTileState.Selected -> Triple(colors.selectedFace, colors.selectedBorder, colors.selectedInk)
        MatchingTileState.Correct -> Triple(colors.correctFace, colors.correctBorder, colors.correctInk)
        MatchingTileState.Wrong -> Triple(colors.wrongFace, colors.wrongBorder, colors.wrongInk)
    }
    val canSelect = enabled && state != MatchingTileState.Correct && state != MatchingTileState.Wrong
    val label = if (item.imageUrl.isNullOrBlank()) null else stringResource(R.string.round2_picture_option, number)
    val feedback = when (state) {
        MatchingTileState.Correct -> stringResource(R.string.answer_option_correct)
        MatchingTileState.Wrong -> stringResource(R.string.answer_option_wrong)
        else -> null
    }
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val shake = remember { Animatable(0f) }
    LaunchedEffect(state) {
        if (state == MatchingTileState.Wrong && coroutineContext[MotionDurationScale]?.scaleFactor != 0f) {
            shake.snapTo(0f)
            shake.animateTo(1f, tween(150, easing = LinearEasing))
        }
    }
    Box(
        modifier.selectable(
            selected = state == MatchingTileState.Selected,
            enabled = canSelect,
            role = Role.Button,
            interactionSource = interaction,
            indication = null,
            onClick = onClick,
        ).semantics {
            label?.let { contentDescription = it }
            feedback?.let { stateDescription = it }
        }.graphicsLayer {
            if (state == MatchingTileState.Wrong) {
                translationX = 4.dp.toPx() * sin(shake.value * 4 * PI).toFloat() * (1 - shake.value)
            }
        }.background(border, RoundedCornerShape(15.dp))
            .padding(start = 1.5.dp, end = 1.5.dp,
                top = if (pressed && canSelect) 4.dp else 1.5.dp,
                bottom = if (pressed && canSelect) 2.dp else 4.5.dp),
    ) {
        Box(
            Modifier.fillMaxWidth().heightIn(min = 54.dp).background(face, RoundedCornerShape(13.5.dp)),
            contentAlignment = Alignment.Center,
        ) {
            val image = item.imageUrl?.takeIf { it.isNotBlank() }
            if (image == null) {
                Text(
                    item.text.orEmpty(), Modifier.padding(horizontal = 25.dp, vertical = 5.dp),
                    color = ink, style = matchingTextStyle(16, 650), textAlign = TextAlign.Center,
                )
            } else {
                imageContent(image, Modifier.size(38.dp))
            }
            val indicator = Modifier.align(Alignment.CenterEnd).padding(end = 8.dp)
            when (state) {
                MatchingTileState.Default -> Unit
                MatchingTileState.Selected -> Box(indicator.size(7.dp).background(ink, CircleShape))
                MatchingTileState.Correct -> Icon(Icons.Rounded.Check, null, indicator.size(17.dp), tint = ink)
                MatchingTileState.Wrong -> Icon(Icons.Rounded.Close, null, indicator.size(15.dp), tint = ink)
            }
        }
    }
}

internal enum class MatchingButtonTone { Yellow, Teal, Neutral }

@Composable
internal fun MatchingButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tone: MatchingButtonTone = MatchingButtonTone.Teal,
    enabled: Boolean = true,
    loading: Boolean = false,
    compact: Boolean = false,
) {
    val colors = matchingColors
    val border = when {
        !enabled || tone == MatchingButtonTone.Neutral -> colors.border
        tone == MatchingButtonTone.Yellow -> Color(0xFFCE9C35)
        else -> Color(0xFF00564E)
    }
    val face = when {
        !enabled -> listOf(colors.disabledFace, colors.disabledFace)
        tone == MatchingButtonTone.Yellow -> listOf(colors.yellow, colors.yellow)
        tone == MatchingButtonTone.Neutral -> listOf(colors.surface, colors.surface)
        else -> listOf(Color(0xFF08786E), Color(0xFF00695F))
    }
    val ink = when {
        !enabled -> colors.disabledInk
        tone == MatchingButtonTone.Yellow -> colors.yellowInk
        tone == MatchingButtonTone.Neutral -> colors.ink
        else -> Color.White
    }
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val shape = RoundedCornerShape(if (compact) 9.dp else 14.dp)
    Row(
        modifier.fillMaxWidth().heightIn(min = if (compact) 48.dp else 52.dp)
            .clip(shape).background(border)
            .clickable(interaction, null, enabled && !loading, role = Role.Button, onClick = onClick)
            .padding(start = 1.dp, end = 1.dp, top = if (pressed) 4.dp else 1.dp,
                bottom = if (pressed) 1.dp else 3.dp)
            .background(Brush.verticalGradient(face), RoundedCornerShape(if (compact) 8.dp else 13.dp))
            .padding(horizontal = 10.dp, vertical = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (loading) CircularProgressIndicator(Modifier.size(17.dp), color = colors.accent, strokeWidth = 2.dp)
        Text(label, style = matchingTextStyle(if (compact) 13 else 15, 800), color = ink, textAlign = TextAlign.Center)
    }
}
