package com.nhom21.english_learning_app.features.review

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.keyframes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.nhom21.english_learning_app.R
import com.nhom21.english_learning_app.core.ui.components.OwlaExpression
import com.nhom21.english_learning_app.core.ui.components.OwlaMascot
import com.nhom21.english_learning_app.features.round.components.RoundPrimaryButton
import com.nhom21.english_learning_app.ui.theme.roundColors
import com.nhom21.english_learning_app.ui.theme.OwlaType
import kotlinx.coroutines.launch

private const val StarCount = 3
private const val StarStaggerMillis = 150
private const val StarPopMillis = 250
private const val StarStartScale = 0.65f

@Composable
internal fun RoundResultScreen(
    resultId: Long,
    summary: String,
    stars: Int,
    actionLabel: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
    details: (@Composable () -> Unit)? = null,
    secondaryAction: (@Composable () -> Unit)? = null
) {
    val colors = roundColors()
    BoxWithConstraints(modifier.fillMaxSize().background(colors.background).safeDrawingPadding()) {
        val availableHeight = maxHeight
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).heightIn(min = availableHeight)
                .padding(horizontal = 16.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically)
        ) {
            OwlaMascot(OwlaExpression.Idle, Modifier.size(208.dp))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    stringResource(R.string.round_complete),
                    style = OwlaType.display,
                    color = colors.accentInk,
                    textAlign = TextAlign.Center
                )
                Text(
                    summary,
                    style = OwlaType.body,
                    color = colors.ink,
                    textAlign = TextAlign.Center
                )
            }
            key(resultId) {
                RoundResultStars(stars.coerceIn(0, StarCount))
            }
            details?.invoke()
            Column(
                modifier = Modifier.widthIn(max = 380.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                RoundPrimaryButton(actionLabel, onAction)
                secondaryAction?.invoke()
            }
        }
    }
}

@Composable
private fun RoundResultStars(earnedStars: Int) {
    // The result key scopes restoration to a single completed submission.
    var started by rememberSaveable { mutableStateOf(false) }
    val scales = remember(earnedStars) {
        List(earnedStars) { Animatable(if (started) 1f else StarStartScale) }
    }
    LaunchedEffect(scales) {
        if (started || scales.isEmpty()) return@LaunchedEffect
        started = true
        if (coroutineContext[MotionDurationScale]?.scaleFactor == 0f) {
            scales.forEach { it.snapTo(1f) }
            return@LaunchedEffect
        }
        scales.forEachIndexed { index, scale ->
            launch {
                val offset = index * StarStaggerMillis
                scale.animateTo(1f, keyframes {
                    durationMillis = offset + StarPopMillis
                    StarStartScale at offset using LinearOutSlowInEasing
                    1.14f at offset + 110 using FastOutSlowInEasing
                })
            }
        }
    }

    val label = stringResource(R.string.round_result_stars, earnedStars)
    val emptyTint = ColorFilter.tint(roundColors().border)
    val star = painterResource(R.drawable.ic_star)
    Row(
        Modifier.semantics { contentDescription = label },
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        repeat(StarCount) { index ->
            val scale = scales.getOrNull(index)
            Image(
                painter = star,
                contentDescription = null,
                colorFilter = if (scale == null) emptyTint else null,
                modifier = Modifier.size(54.dp).graphicsLayer {
                    scaleX = scale?.value ?: 1f
                    scaleY = scaleX
                },
            )
        }
    }
}
