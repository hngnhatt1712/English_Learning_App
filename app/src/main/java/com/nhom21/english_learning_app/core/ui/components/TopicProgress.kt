package com.nhom21.english_learning_app.core.ui.components

import android.content.res.Configuration
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.nhom21.english_learning_app.ui.theme.OwlaType

enum class TopicProgressStyle { Full, Compact }

internal fun topicProgressFraction(completed: Int, total: Int): Float =
    if (total <= 0) 0f else completed.coerceIn(0, total).toFloat() / total

@Composable
fun TopicProgress(
    roundsCompleted: Int,
    roundsTotal: Int,
    stars: Int,
    maxStars: Int,
    accent: Color,
    accentDeep: Color,
    track: Color,
    modifier: Modifier = Modifier,
    style: TopicProgressStyle = TopicProgressStyle.Full,
) {
    val safeCompleted = roundsCompleted.coerceIn(0, roundsTotal.coerceAtLeast(0))
    val safeStars = stars.coerceIn(0, maxStars.coerceAtLeast(0))
    val target = topicProgressFraction(roundsCompleted, roundsTotal)
    val progress by animateFloatAsState(target, tween(200), label = "topicProgress")
    val safeTotal = roundsTotal.coerceAtLeast(0)
    val safeMaxStars = maxStars.coerceAtLeast(0)
    val description = "Đã hoàn thành $safeCompleted trên $safeTotal Round, $safeStars trên $safeMaxStars sao"

    Column(
        modifier = modifier.clearAndSetSemantics { contentDescription = description },
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (style == TopicProgressStyle.Full) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("$safeCompleted/$safeTotal Round", style = OwlaType.caption)
                Text("★ $safeStars/$safeMaxStars", style = OwlaType.caption)
            }
        }
        Box(
            Modifier.fillMaxWidth().height(if (style == TopicProgressStyle.Full) 12.dp else 8.dp)
                .background(track, RoundedCornerShape(999.dp)),
        ) {
            Box(
                Modifier.fillMaxWidth(progress).height(if (style == TopicProgressStyle.Full) 12.dp else 8.dp)
                    .background(accentDeep, RoundedCornerShape(999.dp)).padding(top = 3.dp),
            ) {
                Box(Modifier.fillMaxWidth().height(3.dp).background(accent.copy(alpha = .7f), RoundedCornerShape(999.dp)))
            }
        }
        if (style == TopicProgressStyle.Compact) {
            Text("$safeCompleted/$safeTotal Round · ★ $safeStars/$safeMaxStars", style = OwlaType.caption)
        }
    }
}

@Preview(name = "Progress - Light", showBackground = true)
@Preview(name = "Progress - Dark", uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Composable
private fun TopicProgressPreview() {
    TopicProgress(0, 5, 0, 15, Color(0xFF06D6A0), Color(0xFF049A75), Color(0x3306D6A0), Modifier.padding(16.dp))
}
