package com.nhom21.english_learning_app.features.review

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.nhom21.english_learning_app.R
import com.nhom21.english_learning_app.features.round.components.RoundMascot
import com.nhom21.english_learning_app.features.round.components.RoundPrimaryButton
import com.nhom21.english_learning_app.ui.theme.roundColors
import com.nhom21.english_learning_app.ui.theme.OwlaType

@Composable
internal fun RoundCompletionScreen(
    summary: String,
    stars: Int,
    actionLabel: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
    @DrawableRes mascot: Int = R.drawable.ic_owla_celebrate,
    details: (@Composable () -> Unit)? = null,
    secondaryAction: (@Composable () -> Unit)? = null
) {
    val colors = roundColors()
    BoxWithConstraints(modifier.fillMaxSize().background(colors.background)) {
        val availableHeight = maxHeight
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).heightIn(min = availableHeight)
                .padding(horizontal = 16.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically)
        ) {
            RoundMascot(mascot, Modifier.size(208.dp))
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
            val displayedStars = stars.coerceIn(0, 3)
            val starDescription = stringResource(R.string.round_result_stars, displayedStars)
            Row(
                Modifier.semantics { contentDescription = starDescription },
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                repeat(3) { index ->
                    Image(
                        painterResource(R.drawable.ic_star),
                        contentDescription = null,
                        colorFilter = if (index < displayedStars) null else ColorFilter.tint(colors.border),
                        modifier = Modifier.size(54.dp)
                    )
                }
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
