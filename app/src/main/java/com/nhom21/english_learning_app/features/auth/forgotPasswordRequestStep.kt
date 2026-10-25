package com.nhom21.english_learning_app.features.auth

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material3.Icon
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
 * Bước 1 trong luồng Quên mật khẩu:
 * Nhập Email hoặc Số điện thoại + Mã xác nhận CAPTCHA và nút Gửi mã.
 */
@Composable
fun ForgotPasswordRequestStep(
    uiState: ForgotPasswordUiState,
    onEmailOrPhoneChange: (String) -> Unit,
    onCaptchaInputChange: (String) -> Unit,
    onRefreshCaptcha: () -> Unit,
    onSendCodeClick: () -> Unit,
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
        // Tiêu đề Bước 1
        Text(
            text = "Quên mật khẩu?",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = Baloo2,
            color = deepTealColor,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Nhập email hoặc số điện thoại đã đăng ký,\nchúng tôi sẽ gửi mã xác nhận để bạn đặt lại mật khẩu.",
            fontSize = 14.sp,
            fontWeight = FontWeight.Normal,
            fontFamily = Quicksand,
            color = textSecondary,
            textAlign = TextAlign.Center,
            lineHeight = 20.sp
        )

        Spacer(modifier = Modifier.height(28.dp))

        // Ô 1: Nhập Email hoặc Số điện thoại
        OutlinedTextField(
            value = uiState.emailOrPhone,
            onValueChange = onEmailOrPhoneChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = {
                Text(
                    text = "Email hoặc số điện thoại",
                    fontFamily = Quicksand,
                    color = textSecondary,
                    fontSize = 15.sp
                )
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Email,
                    contentDescription = "Email",
                    tint = textPrimary
                )
            },
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
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Next
            )
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Ô 2: Nhập Mã xác nhận + Khối CAPTCHA box
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedTextField(
                value = uiState.captchaInput,
                onValueChange = onCaptchaInputChange,
                modifier = Modifier.weight(1f),
                placeholder = {
                    Text(
                        text = "Mã xác nhận",
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
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onDone = {
                        focusManager.clearFocus()
                        onSendCodeClick()
                    }
                )
            )

            CaptchaBox(
                code = uiState.captchaCode,
                onRefresh = onRefreshCaptcha,
                isDark = isDark
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Nút 3D "Gửi mã xác nhận ✈"
        Owla3DButton(
            text = "Gửi mã xác nhận  \u2708",
            onClick = {
                focusManager.clearFocus()
                onSendCodeClick()
            },
            isLoading = uiState.isLoading,
            baseColor = deepTealColor,
            shadowColor = Color(0xFF02474B)
        )
    }
}
