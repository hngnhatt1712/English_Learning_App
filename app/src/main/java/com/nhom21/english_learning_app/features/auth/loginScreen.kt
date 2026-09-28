package com.nhom21.english_learning_app.features.auth

import android.content.res.Configuration
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nhom21.english_learning_app.R
import com.nhom21.english_learning_app.ui.theme.OwlaColors
import androidx.compose.animation.slideInVertically
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import com.nhom21.english_learning_app.ui.theme.Baloo2
import com.nhom21.english_learning_app.ui.theme.Quicksand
import com.nhom21.english_learning_app.core.ui.components.Owla3DButton

/**
 * Màn hình đăng nhập Owla English ( UI tĩnh / Wireframe).
 */
@Composable
fun LoginScreen(
    modifier: Modifier = Modifier,
    uiState: LoginUiState = LoginUiState(),
    onIdentifierChange: (String) -> Unit = {},
    onPasswordChange: (String) -> Unit = {},
    onTogglePasswordVisibility: () -> Unit = {},
    onLoginClick: () -> Unit = {},
    onRegisterClick: () -> Unit = {}
) {
    val isDark = isSystemInDarkTheme()
    val focusManager = LocalFocusManager.current

    // Màu nền & bề mặt theo Token 2.1
    val backgroundColor = if (isDark) OwlaColors.DarkBackground else OwlaColors.LightBackground
    val surfaceColor = if (isDark) OwlaColors.DarkSurface else OwlaColors.LightSurface
    val textPrimary = if (isDark) OwlaColors.DarkTextPrimary else OwlaColors.LightTextPrimary
    val textSecondary = if (isDark) OwlaColors.DarkTextSecondary else OwlaColors.LightTextSecondary
    val borderColor = if (isDark) OwlaColors.DarkBorder else OwlaColors.LightBorder

    Surface(
        modifier = modifier.fillMaxSize(),
        color = backgroundColor
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // 1. Mascot Owla (Biểu cảm idle/chào mừng ở trên cùng)
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.padding(bottom = 16.dp)
            ) {
                // Viền rim-light khi ở Dark Mode (Mục 8)
                if (isDark) {
                    Box(
                        modifier = Modifier
                            .size(136.dp)
                            .clip(CircleShape)
                            .background(OwlaColors.PrimaryBase.copy(alpha = 0.12f))
                            .border(1.5.dp, OwlaColors.PrimaryBase.copy(alpha = 0.35f), CircleShape)
                    )
                }

                Image(
                    painter = painterResource(id = R.drawable.ic_owla),
                    contentDescription = "Mascot Owla chào mừng",
                    modifier = Modifier.size(120.dp)
                )
            }

            // 2. Tiêu đề H1 (Baloo 2, 24px, SemiBold)
            var visible by remember { mutableStateOf(false) }
            LaunchedEffect(Unit) { visible = true }

            AnimatedVisibility(
                visible = visible,
                enter = fadeIn(tween(300)) + slideInVertically(tween(300)) { it / 6 }
            ) {
                Text(
                    text = buildAnnotatedString {
                        append("Đăng nhập vào ")
                        withStyle(SpanStyle(color = OwlaColors.PrimaryBase)) {
                            append("Owla")
                        }
                    },
                    fontSize = 24.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = Baloo2,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Mô tả phụ (Quicksand Body/Caption)
            Text(
                text = "Cùng luyện tiếng Anh mỗi ngày để giữ streak nhé!",
                fontSize = 14.sp,
                fontWeight = FontWeight.Normal,
                fontFamily = Quicksand,
                color = textSecondary,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(32.dp))

            // 3. Ô nhập Email hoặc Username (Corner radius Medium 20px - Mục 2.3)
            OutlinedTextField(
                value = uiState.identifier,
                onValueChange = onIdentifierChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = {
                    Text(
                        text = "Email hoặc tên đăng nhập",
                        fontFamily = Quicksand,
                        color = textSecondary,
                        fontSize = 16.sp
                    )
                },
                singleLine = true,
                shape = RoundedCornerShape(20.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = surfaceColor,
                    unfocusedContainerColor = surfaceColor,
                    disabledContainerColor = surfaceColor,
                    focusedBorderColor = OwlaColors.PrimaryBase,
                    unfocusedBorderColor = borderColor,
                    focusedTextColor = textPrimary,
                    unfocusedTextColor = textPrimary,
                    cursorColor = OwlaColors.PrimaryBase
                ),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email,
                    imeAction = ImeAction.Next
                )
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 4. Ô nhập Mật khẩu (Có nút xem/ẩn mật khẩu)
            OutlinedTextField(
                value = uiState.password,
                onValueChange = onPasswordChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = {
                    Text(
                        text = "Mật khẩu",
                        fontFamily = Quicksand,
                        color = textSecondary,
                        fontSize = 15.sp
                    )
                },
                singleLine = true,
                visualTransformation = if (uiState.isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = onTogglePasswordVisibility) {
                        Icon(
                            painter = painterResource(
                                id = if (uiState.isPasswordVisible) R.drawable.ic_visibility else R.drawable.ic_visibility_off
                            ),
                            contentDescription = if (uiState.isPasswordVisible) "Ẩn mật khẩu" else "Hiện mật khẩu",
                            tint = if (uiState.isPasswordVisible) OwlaColors.PrimaryBase else textSecondary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                },
                shape = RoundedCornerShape(20.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = surfaceColor,
                    unfocusedContainerColor = surfaceColor,
                    disabledContainerColor = surfaceColor,
                    focusedBorderColor = OwlaColors.PrimaryBase,
                    unfocusedBorderColor = borderColor,
                    focusedTextColor = textPrimary,
                    unfocusedTextColor = textPrimary,
                    cursorColor = OwlaColors.PrimaryBase
                ),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onDone = {
                        focusManager.clearFocus()
                        onLoginClick()
                    }
                )
            )

            // 5. Hiển thị thông báo lỗi (Ẩn khi null - Dùng màu Danger Red - Mục 2.1 & 2.2)
            AnimatedVisibility(
                visible = uiState.errorMessage != null,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                if (uiState.errorMessage != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp, start = 6.dp, end = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = uiState.errorMessage,
                            color = OwlaColors.DangerBase,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            fontFamily = Quicksand
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // 6. Nút Đăng nhập 3D Button (base/shadow, lún khi bấm theo mục 3 và 6)
            Owla3DButton(
                text = "ĐĂNG NHẬP",
                onClick = {
                    focusManager.clearFocus()
                    onLoginClick()
                },
                isLoading = uiState.isLoading,
                baseColor = OwlaColors.PrimaryBase,
                shadowColor = OwlaColors.PrimaryShadow
            )

            Spacer(modifier = Modifier.height(24.dp))

            // 7. Link phụ "Chưa có tài khoản? Đăng ký" (Chỉ hiển thị, chưa điều hướng)
            Row(
                modifier = Modifier.padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "Chưa có tài khoản? ",
                    fontSize = 15.sp,
                    fontFamily = Quicksand,
                    color = textSecondary
                )
                Text(
                    text = "Đăng ký",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = Quicksand,
                    color = OwlaColors.AccentBase,
                    modifier = Modifier.clickable(onClick = onRegisterClick)
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// PREVIEWS THEO YÊU CẦU BƯỚC 4
// ---------------------------------------------------------------------------

/**
 * Preview 1: State rỗng (người dùng mới mở app, hỗ trợ gõ thử state cục bộ).
 */
@Preview(name = "1. Trạng thái rỗng (Default)", showBackground = true)
@Composable
fun LoginScreenEmptyPreview() {
    var state by remember { mutableStateOf(LoginUiState()) }

    LoginScreen(
        uiState = state,
        onIdentifierChange = {
            state = state.copy(identifier = it)
        },
        onPasswordChange = { state = state.copy(password = it) },
        onTogglePasswordVisibility = { state = state.copy(isPasswordVisible = !state.isPasswordVisible) },
        onLoginClick = {}
    )
}

/**
 * Preview 2: State có lỗi (errorMessage != null, hiển thị text đỏ Danger).
 */
@Preview(name = "2. Trạng thái lỗi (Error)", showBackground = true)
@Composable
fun LoginScreenErrorPreview() {
    var state by remember {
        mutableStateOf(
            LoginUiState(
                identifier = "user@owla.edu",
                password = "123",
                errorMessage = "Email hoặc mật khẩu không chính xác. Vui lòng thử lại!"
            )
        )
    }

    LoginScreen(
        uiState = state,
        onIdentifierChange = {
            state = state.copy(identifier = it)
        },
        onPasswordChange = { state = state.copy(password = it) },
        onTogglePasswordVisibility = { state = state.copy(isPasswordVisible = !state.isPasswordVisible) },
        onLoginClick = {}
    )
}

/**
 * Preview 3: State Loading (nút hiển thị CircularProgressIndicator thay cho text).
 */
@Preview(name = "3. Trạng thái Loading", showBackground = true)
@Composable
fun LoginScreenLoadingPreview() {
    LoginScreen(
        uiState = LoginUiState(
            identifier = "nhom21@owla.edu",
            password = "secure_password",
            isLoading = true
        )
    )
}

/**
 * Preview 4 (Bonus): Trạng thái Dark Mode theo Mục 8 trong Design System.
 */
@Preview(name = "4. Giao diện Dark Mode",
        showBackground = true,
        uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun LoginScreenDarkModePreview() {
    Surface(color = OwlaColors.DarkBackground) {
        LoginScreen(
            uiState = LoginUiState(
                identifier = "nightowl@owla.edu"
            )
        )
    }
}
