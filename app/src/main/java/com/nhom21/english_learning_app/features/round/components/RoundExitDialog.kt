package com.nhom21.english_learning_app.features.round.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.nhom21.english_learning_app.ui.theme.roundColors
import com.nhom21.english_learning_app.R
import com.nhom21.english_learning_app.core.ui.components.Owla3DButton
import com.nhom21.english_learning_app.ui.theme.OwlaType

@Composable
fun RoundExitDialog(
    onKeepLearning: () -> Unit,
    onLeave: () -> Unit,
    body: String = stringResource(R.string.round_leave_default),
    title: String = stringResource(R.string.round_leave_title),
    keepLearningLabel: String = stringResource(R.string.round_keep_learning),
    leaveLabel: String = stringResource(R.string.round_leave)
) {
    Dialog(onDismissRequest = onKeepLearning) {
        RoundExitDialogContent(body, title, keepLearningLabel, leaveLabel, onKeepLearning, onLeave)
    }
}

@Composable
internal fun RoundExitDialogContent(
    body: String,
    title: String,
    keepLearningLabel: String,
    leaveLabel: String,
    onKeepLearning: () -> Unit,
    onLeave: () -> Unit
) {
    val colors = roundColors()
    val shape = RoundedCornerShape(28.dp)
    Surface(shape = shape, color = colors.surface, modifier = Modifier.border(2.dp, colors.border, shape)) {
        Column(
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            RoundMascot(R.drawable.ic_owla_ask, Modifier.size(116.dp))
            Text(title, color = colors.accentInk, style = OwlaType.h1, textAlign = TextAlign.Center)
            Text(body, color = colors.ink, style = OwlaType.body, textAlign = TextAlign.Center)
            RoundPrimaryButton(keepLearningLabel, onKeepLearning)
            RoundNeutralButton(leaveLabel, onLeave)
        }
    }
}

@Composable
internal fun RoundPrimaryButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    neutralWhenDisabled: Boolean = false,
    leadingIcon: (@Composable () -> Unit)? = null
) {
    val colors = roundColors()
    val neutral = neutralWhenDisabled && !enabled
    Owla3DButton(
        label, onClick, modifier,
        baseColor = if (neutral) lerp(colors.surface, colors.ink, 0.28f) else colors.primaryFace,
        shadowColor = if (neutral) lerp(colors.border, colors.ink, 0.30f) else colors.primaryBase,
        textColor = if (neutral) colors.ink else colors.primaryLabel,
        enabled = enabled,
        height = 60.dp,
        pressDepth = 6.dp,
        labelStyle = RoundButtonLabel,
        leadingIcon = leadingIcon
    )
}

@Composable
internal fun RoundNeutralButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: (@Composable () -> Unit)? = null
) {
    val colors = roundColors()
    Owla3DButton(
        label, onClick, modifier,
        baseColor = colors.surface,
        shadowColor = colors.border,
        textColor = colors.ink,
        enabled = enabled,
        height = 60.dp,
        labelStyle = RoundButtonLabel,
        leadingIcon = leadingIcon
    )
}
