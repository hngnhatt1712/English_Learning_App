package com.nhom21.english_learning_app.features.round.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nhom21.english_learning_app.ui.theme.roundColors
import com.nhom21.english_learning_app.R
import com.nhom21.english_learning_app.ui.theme.OwlaType

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
    subtitle: String?,
    onExit: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = roundColors()
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        RoundCloseButton(onExit)
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, color = colors.accentInk, style = OwlaType.h1, textAlign = TextAlign.Center)
            if (subtitle != null) {
                Text(subtitle, color = colors.ink, style = RoundCaption, textAlign = TextAlign.Center)
            }
        }
        Box(Modifier.width(48.dp))
    }
}

@Composable
internal fun RoundFrame(
    header: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    footer: @Composable () -> Unit = {},
    content: @Composable () -> Unit,
) {
    Box(
        modifier.fillMaxSize().background(roundColors().background)
            .safeDrawingPadding().imePadding(),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(Modifier.widthIn(max = 520.dp).fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp)) {
            header()
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                content()
            }
            footer()
        }
    }
}
