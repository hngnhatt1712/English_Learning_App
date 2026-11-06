package com.nhom21.english_learning_app.features.auth

import android.content.res.Configuration
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nhom21.english_learning_app.ui.theme.OwlaColors
import com.nhom21.english_learning_app.ui.theme.Quicksand

/**
 * Màn hình Container Quên / Đặt lại mật khẩu (ForgotPasswordScreen).
 * Tọa độ điều phối 4 bước bằng cách tách các Composable thành từng file riêng biệt:
 * 1. ForgotPasswordHeader (Header + Step Indicator)
 * 2. ForgotPasswordRequestStep (Bước 1: Gửi mã + CAPTCHA)
 * 3. ForgotPasswordVerifyOtpStep (Bước 2: Nhập OTP + Đếm ngược)
 * 4. ForgotPasswordResetStep (Bước 3: Mật khẩu mới)
 * 5. ForgotPasswordSuccessStep (Bước 4: Hoàn tất)
 * 6. ForgotPasswordFooter (Chân trang Owla cổ vũ + Nền mây)
 */
@Composable
fun ForgotPasswordScreen(
    modifier: Modifier = Modifier,
    uiState: ForgotPasswordUiState = ForgotPasswordUiState(),
    onEmailOrPhoneChange: (String) -> Unit = {},
    onCaptchaInputChange: (String) -> Unit = {},
    onOtpInputChange: (String) -> Unit = {},
    onNewPasswordChange: (String) -> Unit = {},
    onConfirmNewPasswordChange: (String) -> Unit = {},
    onTogglePasswordVisibility: () -> Unit = {},
    onToggleConfirmPasswordVisibility: () -> Unit = {},
    onRefreshCaptcha: () -> Unit = {},
    onSendCodeClick: () -> Unit = {},
    onVerifyOtpClick: () -> Unit = {},
    onResendOtpClick: () -> Unit = {},
    onResetPasswordClick: () -> Unit = {},
    onNavigateToLogin: () -> Unit = {},
    onGoToPreviousStep: () -> Unit = {}
) {
    val isDark = isSystemInDarkTheme()
    val backgroundColor = if (isDark) OwlaColors.DarkBackground else OwlaColors.LightBackground
    val borderColor = if (isDark) OwlaColors.DarkBorder else OwlaColors.LightBorder
    val deepTealColor = Color(0xFF036268)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(backgroundColor)
    ) {
        // Nền mây trang trí ở dưới cùng
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
                .align(Alignment.BottomCenter)
        ) {
            val bgTint = if (isDark) Color(0xFF1B2B38) else Color(0xFFE2F3F0)
            val path = Path().apply {
                moveTo(0f, size.height * 0.4f)
                cubicTo(
                    size.width * 0.25f, size.height * 0.1f,
                    size.width * 0.5f, size.height * 0.5f,
                    size.width * 0.75f, size.height * 0.2f
                )
                cubicTo(
                    size.width * 0.88f, size.height * 0.05f,
                    size.width, size.height * 0.15f,
                    size.width, size.height * 0.15f
                )
                lineTo(size.width, size.height)
                lineTo(0f, size.height)
                close()
            }
            drawPath(path = path, color = bgTint)
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Header (Mascot + Logo ic_name + Step Indicator)
                ForgotPasswordHeader(currentStep = uiState.currentStep)

                // Nội dung từng bước với hiệu ứng chuyển đổi mượt mà (AnimatedContent)
                AnimatedContent(
                    targetState = uiState.currentStep,
                    transitionSpec = {
                        slideInHorizontally { width -> width } + fadeIn() togetherWith
                                slideOutHorizontally { width -> -width } + fadeOut()
                    },
                    label = "ForgotPasswordStepTransition"
                ) { step ->
                    when (step) {
                        ForgotPasswordStep.REQUEST_CODE -> {
                            ForgotPasswordRequestStep(
                                uiState = uiState,
                                onEmailOrPhoneChange = onEmailOrPhoneChange,
                                onCaptchaInputChange = onCaptchaInputChange,
                                onRefreshCaptcha = onRefreshCaptcha,
                                onSendCodeClick = onSendCodeClick
                            )
                        }

                        ForgotPasswordStep.VERIFY_OTP -> {
                            ForgotPasswordVerifyOtpStep(
                                uiState = uiState,
                                onOtpInputChange = onOtpInputChange,
                                onVerifyOtpClick = onVerifyOtpClick,
                                onResendOtpClick = onResendOtpClick
                            )
                        }

                        ForgotPasswordStep.RESET_PASSWORD -> {
                            ForgotPasswordResetStep(
                                uiState = uiState,
                                onNewPasswordChange = onNewPasswordChange,
                                onConfirmNewPasswordChange = onConfirmNewPasswordChange,
                                onTogglePasswordVisibility = onTogglePasswordVisibility,
                                onToggleConfirmPasswordVisibility = onToggleConfirmPasswordVisibility,
                                onResetPasswordClick = onResetPasswordClick
                            )
                        }

                        ForgotPasswordStep.SUCCESS -> {
                            ForgotPasswordSuccessStep(
                                onNavigateToLogin = onNavigateToLogin
                            )
                        }
                    }
                }

                // Banner hiển thị thông báo lỗi / thành công
                AnimatedVisibility(
                    visible = uiState.errorMessage != null || uiState.successMessage != null,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    val isError = uiState.errorMessage != null
                    val message = uiState.errorMessage ?: uiState.successMessage ?: ""
                    val msgColor = if (isError) OwlaColors.DangerBase else OwlaColors.SuccessBase

                    Text(
                        text = message,
                        color = msgColor,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        fontFamily = Quicksand,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp)
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Link chuyển màn hình ("Quay lại đăng nhập" hoặc "Quay lại bước trước")
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    HorizontalDivider(
                        modifier = Modifier.weight(1f),
                        color = borderColor
                    )

                    val linkText = if (uiState.currentStep == ForgotPasswordStep.REQUEST_CODE || uiState.currentStep == ForgotPasswordStep.SUCCESS) {
                        "Quay lại đăng nhập"
                    } else {
                        " Quay lại bước trước"
                    }

                    val linkAction = if (uiState.currentStep == ForgotPasswordStep.REQUEST_CODE || uiState.currentStep == ForgotPasswordStep.SUCCESS) {
                        onNavigateToLogin
                    } else {
                        onGoToPreviousStep
                    }

                    Text(
                        text = linkText,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = Quicksand,
                        color = deepTealColor,
                        modifier = Modifier
                            .padding(horizontal = 14.dp)
                            .clickable(onClick = linkAction)
                    )

                    HorizontalDivider(
                        modifier = Modifier.weight(1f),
                        color = borderColor
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Footer (Owla Mascot + Bong bóng cổ vũ)
            ForgotPasswordFooter()
        }
    }
}

// ---------------------------------------------------------------------------
// PREVIEWS
// ---------------------------------------------------------------------------

@Preview(name = "1. Bước 1: Gửi mã (Default)", showBackground = true)
@Composable
fun ForgotPasswordStep1Preview() {
    ForgotPasswordScreen(
        uiState = ForgotPasswordUiState(
            currentStep = ForgotPasswordStep.REQUEST_CODE,
            captchaCode = "739K"
        )
    )
}

@Preview(name = "2. Bước 2: Nhập OTP", showBackground = true)
@Composable
fun ForgotPasswordStep2Preview() {
    ForgotPasswordScreen(
        uiState = ForgotPasswordUiState(
            currentStep = ForgotPasswordStep.VERIFY_OTP,
            emailOrPhone = "user@owla.edu",
            resendTimerSeconds = 45
        )
    )
}

@Preview(name = "3. Bước 3: Đặt lại mật khẩu", showBackground = true)
@Composable
fun ForgotPasswordStep3Preview() {
    ForgotPasswordScreen(
        uiState = ForgotPasswordUiState(
            currentStep = ForgotPasswordStep.RESET_PASSWORD,
            emailOrPhone = "user@owla.edu"
        )
    )
}

@Preview(name = "4. Bước 4: Thành công", showBackground = true)
@Composable
fun ForgotPasswordStep4Preview() {
    ForgotPasswordScreen(
        uiState = ForgotPasswordUiState(
            currentStep = ForgotPasswordStep.SUCCESS
        )
    )
}

@Preview(
    name = "5. Dark Mode - Bước 1",
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES
)
@Composable
fun ForgotPasswordDarkModePreview() {
    Surface(color = OwlaColors.DarkBackground) {
        ForgotPasswordScreen(
            uiState = ForgotPasswordUiState(
                currentStep = ForgotPasswordStep.REQUEST_CODE,
                captchaCode = "9X2M"
            )
        )
    }
}

/**
 * Stateful Route wrapper cho ForgotPasswordScreen.
 */
@Composable
fun ForgotPasswordRoute(
    modifier: Modifier = Modifier,
    viewModel: ForgotPasswordViewModel = viewModel(),
    onNavigateToLogin: () -> Unit = {}
) {
    ForgotPasswordScreen(
        modifier = modifier,
        uiState = viewModel.uiState,
        onEmailOrPhoneChange = viewModel::onEmailOrPhoneChange,
        onCaptchaInputChange = viewModel::onCaptchaInputChange,
        onOtpInputChange = viewModel::onOtpInputChange,
        onNewPasswordChange = viewModel::onNewPasswordChange,
        onConfirmNewPasswordChange = viewModel::onConfirmNewPasswordChange,
        onTogglePasswordVisibility = viewModel::onTogglePasswordVisibility,
        onToggleConfirmPasswordVisibility = viewModel::onToggleConfirmPasswordVisibility,
        onRefreshCaptcha = viewModel::generateNewCaptcha,
        onSendCodeClick = viewModel::sendVerificationCode,
        onVerifyOtpClick = viewModel::verifyOtpCode,
        onResendOtpClick = viewModel::resendOtp,
        onResetPasswordClick = { viewModel.resetPassword {} },
        onNavigateToLogin = onNavigateToLogin,
        onGoToPreviousStep = viewModel::goToPreviousStep
    )
}
