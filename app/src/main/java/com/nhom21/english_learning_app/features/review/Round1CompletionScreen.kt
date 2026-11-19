package com.nhom21.english_learning_app.features.review

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.nhom21.english_learning_app.features.round.Round1UiState
import com.nhom21.english_learning_app.features.round.components.RoundCaption
import com.nhom21.english_learning_app.features.round.components.RoundMascot
import com.nhom21.english_learning_app.features.round.components.RoundPrimaryButton
import com.nhom21.english_learning_app.features.round.components.roundColors
import com.nhom21.english_learning_app.ui.theme.OwlaType

@Composable
internal fun Round1CompletionScreen(
    result: Round1UiState.Completed,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier
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
            RoundMascot(R.drawable.ic_owla_celebrate, Modifier.size(208.dp))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    stringResource(R.string.round_complete),
                    style = OwlaType.display,
                    color = colors.accentInk,
                    textAlign = TextAlign.Center
                )
                Text(
                    stringResource(R.string.round_learned_words, result.learnedWords),
                    style = OwlaType.body,
                    color = colors.ink,
                    textAlign = TextAlign.Center
                )
            }
            val stars = result.stars.coerceIn(0, 3)
            val starDescription = stringResource(R.string.round_result_stars, stars)
            Row(
                Modifier.semantics { contentDescription = starDescription },
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                repeat(3) { index ->
                    Image(
                        painterResource(R.drawable.ic_star),
                        contentDescription = null,
                        colorFilter = if (index < stars) null else ColorFilter.tint(colors.border),
                        modifier = Modifier.size(54.dp)
                    )
                }
            }
            if (result.addedWords != null || result.skippedWords != null) {
                val shape = RoundedCornerShape(20.dp)
                Column(
                    Modifier.background(colors.surface, shape).border(2.dp, colors.border, shape).padding(14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    result.addedWords?.let {
                        Text(stringResource(R.string.round_result_added, it), style = RoundCaption, color = colors.ink)
                    }
                    result.skippedWords?.let {
                        Text(stringResource(R.string.round_result_skipped, it), style = RoundCaption, color = colors.ink)
                    }
                }
            }
            RoundPrimaryButton(stringResource(R.string.round_continue), onContinue, Modifier.widthIn(max = 380.dp))
        }
    }
}
