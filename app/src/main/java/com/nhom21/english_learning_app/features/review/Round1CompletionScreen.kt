package com.nhom21.english_learning_app.features.review

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.nhom21.english_learning_app.R
import com.nhom21.english_learning_app.features.round.Round1UiState
import com.nhom21.english_learning_app.features.round.components.RoundCaption
import com.nhom21.english_learning_app.features.round.components.roundColors

@Composable
internal fun Round1CompletionScreen(
    result: Round1UiState.Completed,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier
) {
    RoundCompletionScreen(
        summary = stringResource(R.string.round_learned_words, result.learnedWords),
        stars = result.stars,
        actionLabel = stringResource(R.string.round_continue),
        onAction = onContinue,
        modifier = modifier,
        details = {
            if (result.addedWords != null || result.skippedWords != null) {
                val colors = roundColors()
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
        }
    )
}
