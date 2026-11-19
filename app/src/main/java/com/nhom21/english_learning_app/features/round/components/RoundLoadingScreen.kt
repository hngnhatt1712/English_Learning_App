package com.nhom21.english_learning_app.features.round.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nhom21.english_learning_app.R
import com.nhom21.english_learning_app.ui.theme.OwlaType

@Composable
fun RoundLoadingScreen(
    onRequestExit: () -> Unit,
    modifier: Modifier = Modifier,
    message: String = stringResource(R.string.round_loading),
    description: String = stringResource(R.string.round_get_ready)
) {
    val colors = roundColors()
    BoxWithConstraints(modifier.fillMaxSize().background(colors.background)) {
        val availableHeight = maxHeight
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            RoundCloseButton(onRequestExit, Modifier.padding(start = 12.dp, top = 8.dp))
            Column(
                modifier = Modifier.fillMaxWidth().heightIn(min = (availableHeight - 56.dp).coerceAtLeast(0.dp))
                    .padding(horizontal = 24.dp, vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically)
            ) {
                RoundMascot(R.drawable.ic_owla, Modifier.size(208.dp), imagePadding = 24.dp)
                Text(message, style = OwlaType.display, color = colors.accentInk, textAlign = TextAlign.Center)
                LinearProgressIndicator(
                    modifier = Modifier.widthIn(max = 280.dp).fillMaxWidth().height(12.dp).clip(CircleShape),
                    color = colors.primaryFace,
                    trackColor = colors.border
                )
                if (description.isNotBlank()) {
                    Text(description, style = OwlaType.body, color = colors.ink, textAlign = TextAlign.Center)
                }
            }
        }
    }
}

@Composable
internal fun RoundMascot(drawable: Int, modifier: Modifier = Modifier, imagePadding: Dp = 8.dp) {
    val colors = roundColors()
    Box(
        modifier = modifier.background(colors.surface, CircleShape).border(2.dp, colors.border, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painterResource(drawable),
            contentDescription = null,
            modifier = Modifier.fillMaxSize().padding(imagePadding),
            contentScale = ContentScale.Fit
        )
    }
}
