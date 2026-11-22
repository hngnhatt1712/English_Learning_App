package com.nhom21.english_learning_app.core.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.progressSemantics
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nhom21.english_learning_app.R
import com.nhom21.english_learning_app.ui.theme.OwlaColors
import com.nhom21.english_learning_app.ui.theme.OwlaType
import com.nhom21.english_learning_app.ui.theme.roundColors

enum class ProgressPillTone { Primary, Secondary }

@Composable
fun ProgressPill(
    completed: Int,
    total: Int,
    caption: String,
    stars: Int,
    modifier: Modifier = Modifier,
    tone: ProgressPillTone = ProgressPillTone.Primary,
) {
    val colors = roundColors()
    val fraction = if (total > 0) completed.coerceIn(0, total).toFloat() / total else 0f
    val fill = when (tone) {
        ProgressPillTone.Primary -> colors.primaryFace
        ProgressPillTone.Secondary -> OwlaColors.SecondaryBase
    }
    val captionStyle = OwlaType.caption.copy(fontSize = 14.sp)
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.weight(1f).height(16.dp).clip(CircleShape)
                    .background(colors.border).progressSemantics(fraction)
            ) {
                if (fraction > 0f) {
                    Box(
                        Modifier.fillMaxWidth(fraction).height(16.dp)
                            .clip(CircleShape).background(fill)
                    ) {
                        Box(
                            Modifier.padding(horizontal = 4.dp).padding(top = 3.dp)
                                .fillMaxWidth().height(2.dp)
                                .background(Color.White.copy(alpha = 0.28f), CircleShape)
                        )
                    }
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Image(painterResource(R.drawable.ic_star), null, Modifier.size(24.dp))
                Text(stringResource(R.string.round_saved_stars, stars), color = colors.ink, style = captionStyle)
            }
        }
        Text(caption, style = captionStyle, color = colors.accentInk)
    }
}
