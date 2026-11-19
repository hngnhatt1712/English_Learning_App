package com.nhom21.english_learning_app.features.round.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nhom21.english_learning_app.R
import com.nhom21.english_learning_app.ui.theme.OwlaColors
import com.nhom21.english_learning_app.ui.theme.OwlaType

internal data class RoundColors(
    val background: Color,
    val surface: Color,
    val ink: Color,
    val accentInk: Color,
    val border: Color,
    val primaryFace: Color,
    val primaryBase: Color,
    val primaryLabel: Color,
    val successInk: Color
)

@Composable
internal fun roundColors(): RoundColors {
    val dark = isSystemInDarkTheme()
    val background = if (dark) OwlaColors.DarkBackground else OwlaColors.LightBackground
    val surface = if (dark) OwlaColors.DarkSurface else OwlaColors.LightSurface
    val ink = if (dark) OwlaColors.DarkTextPrimary else OwlaColors.LightTextPrimary
    // Use the existing teal pair to tint the screen and preserve legibility on the button face.
    return RoundColors(
        background = lerp(background, OwlaColors.PrimaryBase, if (dark) 0.05f else 0.07f),
        surface = surface,
        ink = ink,
        accentInk = if (dark) ink else lerp(OwlaColors.PrimaryShadow, ink, 0.42f),
        border = if (dark) OwlaColors.DarkBorder else OwlaColors.LightBorder,
        primaryFace = OwlaColors.PrimaryBase,
        primaryBase = OwlaColors.PrimaryShadow,
        primaryLabel = OwlaColors.LightTextPrimary,
        successInk = if (dark) OwlaColors.SuccessBase else lerp(OwlaColors.SuccessShadow, ink, 0.36f)
    )
}

internal val RoundButtonLabel = OwlaType.button.copy(fontSize = 18.sp, lineHeight = 21.sp)
internal val RoundCaption = OwlaType.caption.copy(fontSize = 14.sp, lineHeight = 18.sp)

@Composable
internal fun RoundCloseButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = roundColors()
    IconButton(onClick, modifier = modifier.size(48.dp)) {
        Box(
            modifier = Modifier.size(36.dp).background(colors.border, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Rounded.Close,
                contentDescription = stringResource(R.string.round_close),
                tint = colors.ink,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
internal fun RoundHeading(
    title: String,
    subtitle: String,
    onExit: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = roundColors()
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        RoundCloseButton(onExit)
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, color = colors.accentInk, style = OwlaType.h1, textAlign = TextAlign.Center)
            Text(subtitle, color = colors.ink, style = RoundCaption, textAlign = TextAlign.Center)
        }
        Box(Modifier.width(48.dp))
    }
}

@Composable
internal fun RoundWordProgress(processed: Int, total: Int, modifier: Modifier = Modifier) {
    val colors = roundColors()
    val fraction = if (total > 0) (processed.toFloat() / total).coerceIn(0f, 1f) else 0f
    val progressLabel = stringResource(R.string.round_progress_label)
    Column(
        modifier.widthIn(max = 520.dp).fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        BoxWithConstraints(Modifier.fillMaxWidth().semantics { contentDescription = progressLabel }) {
            val trackWidth = maxWidth
            val gutter = 10.dp
            val diameter = 16.dp
            val scale = LocalDensity.current.fontScale
            val usableWidth = (maxWidth - gutter * 2).coerceAtLeast(0.dp)
            val labelWidth = (48.dp * scale).coerceAtMost(maxWidth)
            val separateMiddleLabel = usableWidth * 0.1f < labelWidth
            val labelHeight = 22.dp * scale
            Box(Modifier.fillMaxWidth().height(23.dp + labelHeight * if (separateMiddleLabel) 2 else 1)) {
                Box(
                    Modifier.fillMaxWidth().padding(horizontal = gutter).offset(y = 6.dp)
                        .height(12.dp).clip(CircleShape).background(colors.border)
                ) {
                    if (fraction > 0f) {
                        Box(
                            Modifier.fillMaxWidth(fraction).height(12.dp)
                                .clip(CircleShape).background(colors.primaryFace)
                        ) {
                            Box(
                                Modifier.fillMaxWidth().padding(horizontal = 4.dp).padding(top = 2.dp)
                                    .height(2.dp).background(Color.White.copy(alpha = 0.28f), CircleShape)
                            )
                        }
                    }
                }
                for (milestone in listOf(70, 80, 100)) {
                    val center = gutter + usableWidth * (milestone / 100f)
                    Image(
                        painter = painterResource(R.drawable.ic_star),
                        contentDescription = null,
                        colorFilter = ColorFilter.tint(colors.accentInk.copy(alpha = 0.7f)),
                        modifier = Modifier.offset(x = center - diameter / 2, y = 4.dp).size(diameter)
                    )
                    Text(
                        stringResource(R.string.round_progress_milestone, milestone),
                        modifier = Modifier.offset(
                            x = (center - labelWidth / 2).coerceIn(0.dp, trackWidth - labelWidth),
                            y = 23.dp + if (separateMiddleLabel && milestone == 80) labelHeight else 0.dp
                        ).width(labelWidth),
                        textAlign = TextAlign.Center,
                        color = colors.ink,
                        style = RoundCaption
                    )
                }
            }
        }
        Text(
            stringResource(R.string.round_words_progress, processed, total),
            style = RoundCaption,
            color = colors.accentInk,
            textAlign = TextAlign.Center
        )
    }
}
