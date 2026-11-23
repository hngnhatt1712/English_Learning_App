package com.nhom21.english_learning_app.core.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nhom21.english_learning_app.R
import com.nhom21.english_learning_app.ui.theme.OwlaColors
import com.nhom21.english_learning_app.ui.theme.OwlaType
import com.nhom21.english_learning_app.ui.theme.roundColors

@Composable
fun FeedbackSheet(
    visible: Boolean,
    isCorrect: Boolean,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
    correctAnswer: String? = null,
    answerVisual: (@Composable () -> Unit)? = null,
    explanation: String? = null,
) {
    val colors = roundColors()
    val face = if (isCorrect) OwlaColors.SuccessBase else OwlaColors.DangerBase
    val shadow = if (isCorrect) OwlaColors.SuccessShadow else OwlaColors.DangerShadow
    val statusInk = if (isCorrect) colors.successInk else colors.dangerInk
    val shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    var continued by remember(visible) { mutableStateOf(false) }
    AnimatedVisibility(
        visible = visible,
        modifier = modifier.fillMaxWidth().clipToBounds(),
        enter = slideInVertically(tween(200, easing = LinearOutSlowInEasing)) { it },
        exit = ExitTransition.None,
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth().border(2.dp, colors.border, shape),
            shape = shape,
            color = lerp(colors.surface, face, 0.15f),
        ) {
            Column(
                Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Column(
                    Modifier.weight(1f, fill = false).heightIn(max = 176.dp)
                        .verticalScroll(rememberScrollState())
                        .semantics { liveRegion = LiveRegionMode.Polite },
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            if (isCorrect) Icons.Rounded.CheckCircle else Icons.Rounded.Cancel,
                            contentDescription = null,
                            tint = statusInk,
                            modifier = Modifier.size(24.dp),
                        )
                        Text(
                            stringResource(if (isCorrect) R.string.feedback_correct else R.string.feedback_wrong),
                            color = statusInk,
                            style = OwlaType.h2.copy(fontSize = 20.sp),
                        )
                    }
                    if (!isCorrect) {
                        correctAnswer?.takeIf { it.isNotBlank() }?.let {
                            Text(stringResource(R.string.feedback_correct_answer, it), color = colors.ink, style = OwlaType.body)
                        }
                        answerVisual?.invoke()
                    }
                    explanation?.takeIf { it.isNotBlank() }?.let {
                        Text(stringResource(R.string.feedback_explanation), color = colors.ink, style = OwlaType.h2)
                        Text(it, color = colors.ink, style = OwlaType.body)
                    }
                }
                Owla3DButton(
                    text = stringResource(R.string.feedback_continue),
                    onClick = {
                        if (!continued) {
                            continued = true
                            onContinue()
                        }
                    },
                    baseColor = face,
                    shadowColor = shadow,
                    textColor = OwlaColors.LightTextPrimary,
                    enabled = !continued,
                    height = 56.dp,
                    pressDepth = 6.dp,
                    labelStyle = OwlaType.button.copy(fontSize = 18.sp, lineHeight = 21.sp),
                )
            }
        }
    }
}
