package com.nhom21.english_learning_app.features.auth

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nhom21.english_learning_app.ui.theme.Baloo2
import com.nhom21.english_learning_app.ui.theme.OwlaColors
import java.util.Random

/**
 * Custom Shield Icon (Biểu tượng Khiên bảo mật 🛡 cho ô nhập Mã xác nhận)
 */
@Composable
fun ShieldIcon(
    modifier: Modifier = Modifier,
    tint: Color = OwlaColors.LightTextPrimary
) {
    Canvas(modifier = modifier.size(20.dp)) {
        val w = size.width
        val h = size.height
        val path = Path().apply {
            moveTo(w * 0.5f, h * 0.08f)
            lineTo(w * 0.88f, h * 0.22f)
            cubicTo(w * 0.88f, h * 0.65f, w * 0.5f, h * 0.92f, w * 0.5f, h * 0.92f)
            cubicTo(w * 0.5f, h * 0.92f, w * 0.12f, h * 0.65f, w * 0.12f, h * 0.22f)
            close()
        }
        drawPath(
            path = path,
            color = tint,
            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
    }
}

/**
 * Custom Captcha Box (Khối hiển thị Mã xác nhận CAPTCHA kèm các chấm nhiễu và nút Refresh)
 */
@Composable
fun CaptchaBox(
    code: String,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    isDark: Boolean = false
) {
    val captchaColors = listOf(
        Color(0xFF2C3E50),
        Color(0xFFC0392B),
        Color(0xFF8E44AD),
        Color(0xFF16A085),
        Color(0xFF2980B9)
    )

    val boxBg = if (isDark) OwlaColors.DarkSurface else Color(0xFFF2F6F8)
    val boxBorder = if (isDark) OwlaColors.DarkBorder else Color(0xFFD8E0E5)

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(boxBg)
            .border(1.dp, boxBorder, RoundedCornerShape(16.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .height(38.dp)
                .padding(horizontal = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.matchParentSize()) {
                val dots = 18
                val random = Random(code.hashCode().toLong())
                for (i in 0 until dots) {
                    val x = random.nextFloat() * size.width
                    val y = random.nextFloat() * size.height
                    val color = captchaColors[random.nextInt(captchaColors.size)].copy(alpha = 0.35f)
                    drawCircle(color = color, radius = 2.dp.toPx(), center = Offset(x, y))
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                code.forEachIndexed { index, char ->
                    val color = captchaColors[index % captchaColors.size]
                    Text(
                        text = char.toString(),
                        fontFamily = Baloo2,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        color = color,
                        letterSpacing = 1.sp
                    )
                }
            }
        }

        IconButton(
            onClick = onRefresh,
            modifier = Modifier.size(28.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Refresh,
                contentDescription = "Tải lại mã CAPTCHA",
                tint = if (isDark) OwlaColors.DarkTextPrimary else Color(0xFF1A2634),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
