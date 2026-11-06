package com.nhom21.english_learning_app.features.auth

import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nhom21.english_learning_app.core.ui.components.Owla3DButton
import com.nhom21.english_learning_app.ui.theme.Baloo2
import com.nhom21.english_learning_app.ui.theme.OwlaColors
import com.nhom21.english_learning_app.ui.theme.Quicksand

/**
 * Bước 2 trong luồng Quên mật khẩu:
 * Nhập mã OTP xác nhận gửi về email/SĐT và đếm ngược gửi lại mã.
 */
@Composable
fun ForgotPasswordVerifyOtpStep(
    uiState: ForgotPasswordUiState,
    onOtpInputChange: (String) -> Unit,
    onVerifyOtpClick: () -> Unit,
    onResendOtpClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isDark = isSystemInDarkTheme()
    val focusManager = LocalFocusManager.current

    val surfaceColor = if (isDark) OwlaColors.DarkSurface else OwlaColors.LightSurface
    val textPrimary = if (isDark) OwlaColors.DarkTextPrimary else OwlaColors.LightTextPrimary
    val textSecondary = if (isDark) OwlaColors.DarkTextSecondary else OwlaColors.LightTextSecondary
    val borderColor = if (isDark) OwlaColors.DarkBorder else OwlaColors.LightBorder
    val deepTealColor = Color(0xFF036268)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.fillMaxWidth()
    ) {
        // Tiêu đề Bước 2
        Text(
            text = "Nhập mã OTP xác nhận",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = Baloo2,
            color = deepTealColor,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Mã xác nhận 4-6 chữ số đã được gửi tới:\n${uiState.emailOrPhone}",
            fontSize = 14.sp,
            fontFamily = Quicksand,
            color = textSecondary,
            textAlign = TextAlign.Center,
            lineHeight = 20.sp
        )

        Spacer(modifier = Modifier.height(28.dp))

        // Ô nhập OTP
        OutlinedTextField(
            value = uiState.otpInput,
            onValueChange = onOtpInputChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = {
                Text(
                    text = "Mã OTP (ví dụ: 123456)",
                    fontFamily = Quicksand,
                    color = textSecondary,
                    fontSize = 15.sp
                )
            },
            leadingIcon = { ShieldIcon(tint = textPrimary) },
            singleLine = true,
            shape = RoundedCornerShape(20.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = surfaceColor,
                unfocusedContainerColor = surfaceColor,
                disabledContainerColor = surfaceColor,
                focusedBorderColor = deepTealColor,
                unfocusedBorderColor = borderColor,
                focusedTextColor = textPrimary,
                unfocusedTextColor = textPrimary,
                cursorColor = deepTealColor
            ),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Number,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(
                onDone = {
                    focusManager.clearFocus()
                    onVerifyOtpClick()
                }
            )
        )

        Spacer(modifier = Modifier.height(14.dp))

        // Đếm ngược gửi lại mã
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Chưa nhận được mã? ",
                fontSize = 14.sp,
                fontFamily = Quicksand,
                color = textSecondary
            )

            if (uiState.isResendEnabled) {
                Text(
                    text = "Gửi lại ngay",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = Quicksand,
                    color = OwlaColors.AccentBase,
                    modifier = Modifier.clickable(onClick = onResendOtpClick)
                )
            } else {
                Text(
                    text = "Gửi lại sau ${uiState.resendTimerSeconds}s",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    fontFamily = Quicksand,
                    color = textSecondary
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Nút 3D "Xác nhận mã OTP ➔"
        Owla3DButton(
            text = "Xác nhận mã OTP  \u2794",
            onClick = {
                focusManager.clearFocus()
                onVerifyOtpClick()
            },
            isLoading = uiState.isLoading,
            baseColor = deepTealColor,
            shadowColor = Color(0xFF02474B)
        )
    }
}
