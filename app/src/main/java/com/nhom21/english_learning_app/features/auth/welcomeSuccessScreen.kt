package com.nhom21.english_learning_app.features.auth

import android.content.res.Configuration
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nhom21.english_learning_app.R
import com.nhom21.english_learning_app.core.ui.components.Owla3DButton
import com.nhom21.english_learning_app.ui.theme.Baloo2
import com.nhom21.english_learning_app.ui.theme.OwlaColors
import com.nhom21.english_learning_app.ui.theme.Quicksand

/**
 * Màn hình Chúc mừng hoàn thành Đăng ký và Chọn trình độ (WelcomeSuccessScreen).
 * Hiển thị Mascot Owla ăn mừng cùng bong bóng thoại "Hurray!!",
 * thông báo "Welcome 👋 Your profile has been created successfully." và nút "CONTINUE TO LOG IN".
 */
@Composable
fun WelcomeSuccessScreen(
    modifier: Modifier = Modifier,
    onContinueToLogin: () -> Unit = {}
) {
    val isDark = isSystemInDarkTheme()

    val backgroundColor = if (isDark) OwlaColors.DarkBackground else Color(0xFFEFF8F6)
    val textSecondary = if (isDark) OwlaColors.DarkTextSecondary else OwlaColors.LightTextSecondary

    Surface(
        modifier = modifier.fillMaxSize(),
        color = backgroundColor
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // Nội dung chính phía trên: Bong bóng thoại + Mascot + Text Chúc mừng
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 1. Bong bóng thoại "Hurray!!" chỉ hướng xuống Mascot Owla
                OwlaTopSpeechBubble(
                    text = "Hurray!!",
                    isDark = isDark
                )

                Spacer(modifier = Modifier.height(16.dp))

                // 2. Mascot Owla
                Image(
                    painter = painterResource(id = R.drawable.ic_owla),
                    contentDescription = "Mascot Owla Hurray",
                    modifier = Modifier.size(150.dp)
                )

                Spacer(modifier = Modifier.height(32.dp))

                // 3. Tiêu đề "Welcome 👋"
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Welcome",
                        fontFamily = Baloo2,
                        fontWeight = FontWeight.Bold,
                        fontSize = 36.sp,
                        color = OwlaColors.PrimaryBase
                    )
                    Text(
                        text = " 👋",
                        fontSize = 32.sp
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // 4. Mô tả "Your profile has been created successfully."
                Text(
                    text = "Your profile has been created\nsuccessfully.",
                    fontFamily = Quicksand,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                    color = textSecondary,
                    textAlign = TextAlign.Center,
                    lineHeight = 24.sp
                )
            }

            // 5. Nút 3D CONTINUE TO LOG IN
            Owla3DButton(
                text = "CONTINUE TO LOG IN",
                onClick = onContinueToLogin,
                baseColor = OwlaColors.PrimaryBase,
                shadowColor = OwlaColors.PrimaryShadow
            )
        }
    }
}

/**
 * Bong bóng thoại đỉnh đầu chỉ hướng xuống phía dưới (Top Speech Bubble).
 */
@Composable
private fun OwlaTopSpeechBubble(
    text: String,
    modifier: Modifier = Modifier,
    isDark: Boolean = false
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Thân bong bóng thoại
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = if (isDark) OwlaColors.DarkSurface else Color.White,
            border = BorderStroke(
                1.5.dp,
                OwlaColors.PrimaryBase.copy(alpha = 0.35f)
            ),
            shadowElevation = 1.dp
        ) {
            Text(
                text = text,
                fontFamily = Baloo2,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = if (isDark) OwlaColors.DarkTextPrimary else Color(0xFF134E45),
                modifier = Modifier.padding(horizontal = 32.dp, vertical = 10.dp)
            )
        }

        // Đuôi tam giác hướng xuống dưới
        Canvas(
            modifier = Modifier
                .size(width = 14.dp, height = 10.dp)
                .offset(y = (-1).dp)
        ) {
            val path = Path().apply {
                moveTo(0f, 0f)
                lineTo(size.width, 0f)
                lineTo(size.width / 2f, size.height)
                close()
            }
            drawPath(
                path = path,
                color = if (isDark) OwlaColors.DarkSurface else Color.White
            )
        }
    }
}

// ---------------------------------------------------------------------------
// PREVIEWS
// ---------------------------------------------------------------------------

@Preview(name = "Welcome Success Screen (Light)", showBackground = true)
@Composable
fun WelcomeSuccessScreenPreview() {
    WelcomeSuccessScreen()
}

@Preview(
    name = "Welcome Success Screen (Dark Mode)",
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES
)
@Composable
fun WelcomeSuccessScreenDarkModePreview() {
    WelcomeSuccessScreen()
}
