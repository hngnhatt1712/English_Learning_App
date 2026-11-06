package com.nhom21.english_learning_app.features.auth

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.nhom21.english_learning_app.R
import com.nhom21.english_learning_app.ui.theme.OwlaColors

/**
 * Component Header chung cho luồng Quên mật khẩu.
 * Hiển thị Logo Owla mascot + Logo chữ Owla English và Thanh chỉ báo tiến trình (Step Indicator).
 */
@Composable
fun ForgotPasswordHeader(
    currentStep: ForgotPasswordStep,
    modifier: Modifier = Modifier
) {
    // Nếu ở trang cuối (SUCCESS), ẩn hoàn toàn logo nhỏ + chữ OWLA ENGLISH để nhường chỗ cho logo to
    if (currentStep == ForgotPasswordStep.SUCCESS) return

    val isDark = isSystemInDarkTheme()
    val deepTealColor = Color(0xFF036268)
    val borderColor = if (isDark) OwlaColors.DarkBorder else OwlaColors.LightBorder

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.fillMaxWidth()
    ) {
        // Logo Owla mascot + Logo chữ thương hiệu ic_name
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.padding(top = 12.dp, bottom = 12.dp)
        ) {
            Image(
                painter = painterResource(id = R.drawable.ic_owla),
                contentDescription = "Logo Owla",
                modifier = Modifier.size(52.dp)
            )

            Spacer(modifier = Modifier.width(10.dp))

            Image(
                painter = painterResource(id = R.drawable.ic_name),
                contentDescription = "Logo Owla English",
                modifier = Modifier.height(48.dp)
            )
        }

        // Thanh chỉ báo tiến trình 3 bước
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 16.dp)
        ) {
            val stepIndex = when (currentStep) {
                ForgotPasswordStep.REQUEST_CODE -> 0
                ForgotPasswordStep.VERIFY_OTP -> 1
                else -> 2
            }

            for (i in 0..2) {
                val isActive = i <= stepIndex
                val barWidth = if (i == stepIndex) 28.dp else 12.dp
                val barColor = if (isActive) deepTealColor else borderColor

                Box(
                    modifier = Modifier
                        .width(barWidth)
                        .height(6.dp)
                        .clip(CircleShape)
                        .background(barColor)
                )
            }
        }
    }
}
