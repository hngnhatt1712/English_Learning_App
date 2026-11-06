package com.nhom21.english_learning_app.features.auth

import androidx.compose.foundation.Image
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nhom21.english_learning_app.R
import com.nhom21.english_learning_app.core.ui.components.Owla3DButton
import com.nhom21.english_learning_app.ui.theme.Baloo2
import com.nhom21.english_learning_app.ui.theme.OwlaColors
import com.nhom21.english_learning_app.ui.theme.Quicksand

/**
 * Bước 4 trong luồng Quên mật khẩu:
 * Màn hình Hoàn tất thành công với Mascot Owla ăn mừng và nút Đăng nhập ngay.
 */
@Composable
fun ForgotPasswordSuccessStep(
    onNavigateToLogin: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isDark = isSystemInDarkTheme()
    val textSecondary = if (isDark) OwlaColors.DarkTextSecondary else OwlaColors.LightTextSecondary

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 16.dp)
    ) {
        // Logo Owla Mascot lớn ăn mừng trong huy hiệu xanh lục
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.padding(bottom = 16.dp)
        ) {
            Surface(
                modifier = Modifier.size(110.dp),
                shape = CircleShape,
                color = OwlaColors.SuccessBase.copy(alpha = 0.15f)
            ) {}

            Image(
                painter = painterResource(id = R.drawable.ic_owla),
                contentDescription = "Owla ăn mừng",
                modifier = Modifier.size(90.dp)
            )
        }

        Text(
            text = "Thành công! 🎉",
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = Baloo2,
            color = OwlaColors.SuccessBase,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Mật khẩu của bạn đã được đặt lại thành công.\nHãy đăng nhập ngay để tiếp tục học nhé!",
            fontSize = 15.sp,
            fontFamily = Quicksand,
            color = textSecondary,
            textAlign = TextAlign.Center,
            lineHeight = 22.sp
        )

        Spacer(modifier = Modifier.height(28.dp))

        // Nút 3D "Đăng nhập ngay ➔"
        Owla3DButton(
            text = "Đăng nhập ngay  \u2794",
            onClick = onNavigateToLogin,
            baseColor = OwlaColors.PrimaryBase,
            shadowColor = OwlaColors.PrimaryShadow
        )
    }
}
