package com.nhom21.english_learning_app.core.ui.components

import android.content.res.Configuration
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nhom21.english_learning_app.ui.theme.OwlaColors

enum class PathNodeState { Locked, Current, Completed }
enum class PathNodeShape { Round, Chest }

@Immutable
data class PathNodeColors(
    val face: Color,
    val base: Color,
    val content: Color,
    val ring: Color,
    val ringTrack: Color,
)

object PathGeometry {
    const val NodesPerTopic = 5
    private val factors = floatArrayOf(0f, .45f, .8f, .45f, 0f)

    fun xOffset(roundIndex: Int, topicIndex: Int, maxOffset: Dp): Dp {
        val factor = factors.getOrElse(roundIndex - 1) { 0f }
        val direction = if (topicIndex % 2 == 0) 1 else -1
        return maxOffset * factor * direction
    }
}

@Composable
fun PathNode(
    state: PathNodeState,
    stars: Int,
    colors: PathNodeColors,
    onClick: () -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
    shape: PathNodeShape = PathNodeShape.Round,
    centerIcon: (@Composable () -> Unit)? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val depth = 6.dp
    val pressedOffset by animateDpAsState(
        targetValue = if (pressed && state != PathNodeState.Locked) depth else 0.dp,
        animationSpec = tween(80),
        label = "pathNodePress",
    )
    val lockedFace = OwlaColors.LightTextSecondary.copy(alpha = .35f)
    val lockedContent = OwlaColors.LightTextSecondary
    val face = if (state == PathNodeState.Locked) lockedFace else colors.face
    val content = if (state == PathNodeState.Locked) lockedContent else colors.content
    val nodeSize = 72.dp
    val ringSize = 92.dp

    Column(
        modifier = modifier.semantics(mergeDescendants = true) {
            this.contentDescription = contentDescription
            role = Role.Button
        },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(modifier = Modifier.size(ringSize), contentAlignment = Alignment.TopCenter) {
            if (state == PathNodeState.Current) {
                Canvas(Modifier.size(ringSize).clearAndSetSemantics { }) {
                    drawArc(colors.ringTrack, 0f, 360f, false, style = Stroke(4.dp.toPx(), cap = StrokeCap.Round))
                    drawArc(colors.ring, -90f, 285f, false, style = Stroke(4.dp.toPx(), cap = StrokeCap.Round))
                }
            }
            if (state != PathNodeState.Locked) {
                Box(
                    Modifier.size(nodeSize).offset(y = depth)
                        .background(colors.base, CircleShape).clearAndSetSemantics { },
                )
            }
            Box(
                modifier = Modifier.size(nodeSize).offset(y = pressedOffset)
                    .background(face, CircleShape)
                    .clickable(interactionSource = interaction, indication = null, onClick = onClick)
                    .clearAndSetSemantics { },
                contentAlignment = Alignment.Center,
            ) {
                when {
                    state == PathNodeState.Locked -> Icon(Icons.Rounded.Lock, null, tint = content, modifier = Modifier.size(30.dp))
                    centerIcon != null -> centerIcon()
                    shape == PathNodeShape.Chest -> Icon(Icons.Rounded.Inventory2, null, tint = content, modifier = Modifier.size(38.dp))
                    else -> Icon(Icons.Rounded.Star, null, tint = content, modifier = Modifier.size(34.dp))
                }
            }
        }
        Spacer(Modifier.height(2.dp))
        NodeStars(stars.coerceIn(0, 3))
    }
}

@Composable
private fun NodeStars(stars: Int) {
    androidx.compose.foundation.layout.Row(modifier = Modifier.clearAndSetSemantics { }) {
        repeat(3) { index ->
            Icon(
                imageVector = Icons.Rounded.Star,
                contentDescription = null,
                tint = if (index < stars) OwlaColors.SecondaryBase else OwlaColors.LightTextSecondary.copy(alpha = .35f),
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Preview(name = "Path nodes - Light", showBackground = true)
@Preview(name = "Path nodes - Dark", uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Composable
private fun PathNodePreview() {
    val colors = PathNodeColors(Color(0xFF06D6A0), Color(0xFF049A75), Color(0xFF1F2933), Color(0xFF06D6A0), Color(0x3306D6A0))
    androidx.compose.foundation.layout.Row {
        PathNode(PathNodeState.Locked, 0, colors, {}, "Khoá")
        Spacer(Modifier.width(8.dp))
        PathNode(PathNodeState.Completed, 1, colors, {}, "Hoàn thành, 1 trên 3 sao")
        Spacer(Modifier.width(8.dp))
        PathNode(PathNodeState.Current, 2, colors, {}, "Hiện tại, 2 trên 3 sao")
        Spacer(Modifier.width(8.dp))
        PathNode(PathNodeState.Completed, 3, colors, {}, "Hoàn thành, 3 trên 3 sao", shape = PathNodeShape.Chest)
    }
}
