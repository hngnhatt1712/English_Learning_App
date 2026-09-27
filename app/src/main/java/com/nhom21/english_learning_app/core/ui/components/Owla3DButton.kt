package com.nhom21.english_learning_app.core.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nhom21.english_learning_app.ui.theme.OwlaColors
import com.nhom21.english_learning_app.ui.theme.Baloo2

@Composable
fun Owla3DButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    baseColor: Color = OwlaColors.PrimaryBase,
    shadowColor: Color = OwlaColors.PrimaryShadow,
    textColor: Color = Color.White,
    isLoading: Boolean = false,
    enabled: Boolean = true,
    height: Dp = 50.dp,
    pressDepth: Dp = 4.dp,
    shape: Shape = RoundedCornerShape(20.dp)
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    // Hiệu ứng lún 4px, 90ms ease-out (Mục 6)
    val animatedOffsetY by animateDpAsState(
        targetValue = if (isPressed && enabled && !isLoading) pressDepth else 0.dp,
        animationSpec = tween(durationMillis = 90, easing = FastOutSlowInEasing),
        label = "Owla3DButtonElevation"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height + pressDepth),
        contentAlignment = Alignment.TopCenter
    ) {
        // Lớp đế 3D (Shadow) cố định phía dưới
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(height)
                .offset(y = pressDepth)
                .background(
                    color = if (enabled) shadowColor else shadowColor.copy(alpha = 0.35f),
                    shape = shape
                )
        )

        // Lớp mặt (Base) di chuyển lún xuống khi tap
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(height)
                .offset(y = animatedOffsetY)
                .background(
                    color = if (enabled) baseColor else baseColor.copy(alpha = 0.35f),
                    shape = shape
                )
                .clickable(
                    interactionSource = interactionSource,
                    indication = null, // Tắt ripple phẳng kiểu Material để giữ trọn vẹn cảm giác khối 3D
                    enabled = enabled && !isLoading,
                    onClick = onClick
                ),
            contentAlignment = Alignment.Center
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = textColor,
                    strokeWidth = 3.dp
                )
            } else {
                Text(
                    text = text,
                    color = textColor,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = Baloo2,
                    letterSpacing = 0.5.sp
                )
            }
        }
    }
}