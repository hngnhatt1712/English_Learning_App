package com.nhom21.english_learning_app.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BookmarkAdd
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import com.nhom21.english_learning_app.R
import com.nhom21.english_learning_app.ui.theme.OwlaType
import com.nhom21.english_learning_app.ui.theme.roundColors
import kotlin.math.abs

@Composable
internal fun SwipeActionIndicator(progress: Float, isInFlashcards: Boolean, modifier: Modifier = Modifier) {
    val colors = roundColors()
    val adding = progress > 0
    val tint = if (adding) colors.successInk else colors.ink
    val (label, icon) = when {
        !adding -> R.string.round_skip to Icons.Rounded.SkipNext
        isInFlashcards -> R.string.round_added to Icons.Rounded.Check
        else -> R.string.round_add to Icons.Rounded.BookmarkAdd
    }
    Row(
        modifier = modifier
            .clearAndSetSemantics { }
            .graphicsLayer { alpha = abs(progress).coerceIn(0f, 1f) }
            .background(colors.surface, CircleShape)
            .border(2.dp, tint, CircleShape)
            .padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
        Text(stringResource(label), color = tint, style = OwlaType.caption)
    }
}
