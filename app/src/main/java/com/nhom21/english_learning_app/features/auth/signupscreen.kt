package com.nhom21.english_learning_app.features.auth

import android.content.res.Configuration
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nhom21.english_learning_app.R
import com.nhom21.english_learning_app.core.ui.components.Owla3DButton
import com.nhom21.english_learning_app.ui.theme.Baloo2
import com.nhom21.english_learning_app.ui.theme.OwlaColors
import com.nhom21.english_learning_app.ui.theme.Quicksand

/**
 * Màn hình Đăng ký (SignUpScreen) của ứng dụng Owla English.
 * Thiết kế chuẩn UI/UX gamification với Mascot Owla, các ô nhập liệu bo tròn 20.dp,
 * phần chọn trình độ (A1, A2, B1), checkbox đồng ý điều khoản và nút 3D Đăng ký.
 */
@Composable
fun SignUpScreen(
    modifier: Modifier = Modifier,
    uiState: SignUpUiState = SignUpUiState(),
    onFullNameChange: (String) -> Unit = {},
    onIdentifierChange: (String) -> Unit = {},
    onPasswordChange: (String) -> Unit = {},
    onConfirmPasswordChange: (String) -> Unit = {},
    onTogglePasswordVisibility: () -> Unit = {},
    onToggleConfirmPasswordVisibility: () -> Unit = {},
    onLevelSelect: (String) -> Unit = {},
    onTermsCheckedChange: (Boolean) -> Unit = {},
    onRegisterClick: () -> Unit = {},
    onLoginClick: () -> Unit = {}
) {
    val isDark = isSystemInDarkTheme()
    val focusManager = LocalFocusManager.current

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
                .padding(horizontal = 24.dp, vertical = 36.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // 1. Mascot Owla
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.padding(bottom = 12.dp)
            ) {
                if (isDark) {
                    Box(
                        modifier = Modifier
                            .size(116.dp)
                            .clip(CircleShape)
                            .background(OwlaColors.PrimaryBase.copy(alpha = 0.12f))
                            .border(1.5.dp, OwlaColors.PrimaryBase.copy(alpha = 0.35f), CircleShape)
                    )
                }

                Image(
                    painter = painterResource(id = R.drawable.ic_owla),
                    contentDescription = "Mascot Owla",
                    modifier = Modifier.size(100.dp)
                )
            }

            // 2. Tiêu đề
            var visible by remember { mutableStateOf(false) }
            LaunchedEffect(Unit) { visible = true }

            AnimatedVisibility(
                visible = visible,
                enter = fadeIn(tween(300)) + slideInVertically(tween(300)) { it / 6 }
            ) {
                Text(
                    text = "Tạo tài khoản mới",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = Baloo2,
                    color = textPrimary,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "Bắt đầu hành trình chinh phục tiếng Anh\ncùng Owla English!",
                fontSize = 14.sp,
                fontWeight = FontWeight.Normal,
                fontFamily = Quicksand,
                color = textSecondary,
                textAlign = TextAlign.Center,
                lineHeight = 20.sp
            )

            Spacer(modifier = Modifier.height(24.dp))

            // 3. Ô nhập Họ và tên
            OutlinedTextField(
                value = uiState.fullName,
                onValueChange = onFullNameChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = {
                    Text(
                        text = "Tên đăng nhập",
                        fontFamily = Quicksand,
                        color = textSecondary,
                        fontSize = 15.sp
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = "Tên đăng nhập",
                        tint = OwlaColors.PrimaryBase
                    )
                },
                singleLine = true,
                shape = RoundedCornerShape(20.dp),
                colors = textFieldColors(surfaceColor, borderColor, textPrimary),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Next
                )
            )

            Spacer(modifier = Modifier.height(14.dp))

            // 4. Ô nhập Email hoặc số điện thoại
            OutlinedTextField(
                value = uiState.identifier,
                onValueChange = onIdentifierChange,
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
                        tint = OwlaColors.PrimaryBase
                    )
                },
                singleLine = true,
                shape = RoundedCornerShape(20.dp),
                colors = textFieldColors(surfaceColor, borderColor, textPrimary),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email,
                    imeAction = ImeAction.Next
                )
            )

            Spacer(modifier = Modifier.height(14.dp))

            // 5. Ô nhập Mật khẩu
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
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Mật khẩu",
                        tint = OwlaColors.PrimaryBase
                    )
                },
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
                singleLine = true,
                visualTransformation = if (uiState.isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                shape = RoundedCornerShape(20.dp),
                colors = textFieldColors(surfaceColor, borderColor, textPrimary),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Next
                )
            )

            Spacer(modifier = Modifier.height(14.dp))

            // 6. Ô nhập Xác nhận mật khẩu
            OutlinedTextField(
                value = uiState.confirmPassword,
                onValueChange = onConfirmPasswordChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = {
                    Text(
                        text = "Xác nhận mật khẩu",
                        fontFamily = Quicksand,
                        color = textSecondary,
                        fontSize = 15.sp
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Xác nhận mật khẩu",
                        tint = OwlaColors.PrimaryBase
                    )
                },
                trailingIcon = {
                    IconButton(onClick = onToggleConfirmPasswordVisibility) {
                        Icon(
                            painter = painterResource(
                                id = if (uiState.isConfirmPasswordVisible) R.drawable.ic_visibility else R.drawable.ic_visibility_off
                            ),
                            contentDescription = if (uiState.isConfirmPasswordVisible) "Ẩn mật khẩu" else "Hiện mật khẩu",
                            tint = if (uiState.isConfirmPasswordVisible) OwlaColors.PrimaryBase else textSecondary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                },
                singleLine = true,
                visualTransformation = if (uiState.isConfirmPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                shape = RoundedCornerShape(20.dp),
                colors = textFieldColors(surfaceColor, borderColor, textPrimary),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onDone = { focusManager.clearFocus() }
                )
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 7. Phần chọn Trình độ hiện tại (Card + Level Buttons A1, A2, B1)
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                color = surfaceColor,
                border = BorderStroke(1.dp, borderColor)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                modifier = Modifier.size(36.dp),
                                shape = RoundedCornerShape(10.dp),
                                color = OwlaColors.PrimaryBase.copy(alpha = 0.15f)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.BarChart,
                                        contentDescription = "Trình độ",
                                        tint = OwlaColors.PrimaryBase,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "Trình độ hiện tại",
                                    fontFamily = Baloo2,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = textPrimary
                                )
                                Text(
                                    text = "Chọn trình độ tiếng Anh của bạn",
                                    fontFamily = Quicksand,
                                    fontSize = 12.sp,
                                    color = textSecondary
                                )
                            }
                        }

                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = "Dropdown",
                            tint = textSecondary
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Các nút chọn mức độ A1, A2, B1
                    val levels = listOf("A1", "A2", "B1")
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        levels.forEach { level ->
                            val isSelected = uiState.selectedLevel == level
                            val btnBg = if (isSelected) OwlaColors.AccentBase else backgroundColor
                            val txtColor = if (isSelected) Color.White else textPrimary
                            val btnBorder = if (isSelected) null else BorderStroke(1.dp, borderColor)

                            Surface(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(42.dp)
                                    .clickable { onLevelSelect(level) },
                                shape = RoundedCornerShape(14.dp),
                                color = btnBg,
                                border = btnBorder
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = level,
                                        fontFamily = Baloo2,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                        color = txtColor
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 8. Checkbox Đồng ý điều khoản
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = uiState.isTermsAccepted,
                    onCheckedChange = onTermsCheckedChange,
                    colors = CheckboxDefaults.colors(
                        checkedColor = OwlaColors.AccentBase,
                        uncheckedColor = textSecondary
                    )
                )

                Text(
                    text = buildAnnotatedString {
                        append("Tôi đồng ý với ")
                        withStyle(SpanStyle(color = OwlaColors.AccentBase, fontWeight = FontWeight.SemiBold)) {
                            append("Điều khoản sử dụng")
                        }
                        append(" và ")
                        withStyle(SpanStyle(color = OwlaColors.AccentBase, fontWeight = FontWeight.SemiBold)) {
                            append("Chính sách bảo mật")
                        }
                        append(".")
                    },
                    fontSize = 13.sp,
                    fontFamily = Quicksand,
                    color = textSecondary,
                    modifier = Modifier.weight(1f)
                )
            }

            // 9. Thông báo lỗi
            AnimatedVisibility(
                visible = uiState.errorMessage != null,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                if (uiState.errorMessage != null) {
                    Text(
                        text = uiState.errorMessage,
                        color = OwlaColors.DangerBase,
                        fontSize = 13.sp,
                        fontFamily = Quicksand,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp, start = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // 10. Nút Đăng ký 3D
            Owla3DButton(
                text = "Đăng ký →",
                onClick = {
                    focusManager.clearFocus()
                    onRegisterClick()
                },
                isLoading = uiState.isLoading,
                baseColor = OwlaColors.PrimaryBase,
                shadowColor = OwlaColors.PrimaryShadow
            )

            Spacer(modifier = Modifier.height(20.dp))

            // 11. Footer: Đã có tài khoản? Đăng nhập
            Row(
                modifier = Modifier.padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "Đã có tài khoản? ",
                    fontSize = 15.sp,
                    fontFamily = Quicksand,
                    color = textSecondary
                )
                Text(
                    text = "Đăng nhập",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = Quicksand,
                    color = OwlaColors.AccentBase,
                    modifier = Modifier.clickable(onClick = onLoginClick)
                )
            }
        }
    }
}

@Composable
private fun textFieldColors(
    containerColor: Color,
    borderColor: Color,
    textPrimary: Color
) = OutlinedTextFieldDefaults.colors(
    focusedContainerColor = containerColor,
    unfocusedContainerColor = containerColor,
    disabledContainerColor = containerColor,
    focusedBorderColor = OwlaColors.PrimaryBase,
    unfocusedBorderColor = borderColor,
    focusedTextColor = textPrimary,
    unfocusedTextColor = textPrimary,
    cursorColor = OwlaColors.PrimaryBase
)

// ---------------------------------------------------------------------------
// PREVIEWS
// ---------------------------------------------------------------------------

@Preview(name = "1. Trạng thái rỗng (Default)", showBackground = true)
@Composable
fun SignUpScreenEmptyPreview() {
    var state by remember { mutableStateOf(SignUpUiState()) }

    SignUpScreen(
        uiState = state,
        onFullNameChange = { state = state.copy(fullName = it) },
        onIdentifierChange = { state = state.copy(identifier = it) },
        onPasswordChange = { state = state.copy(password = it) },
        onConfirmPasswordChange = { state = state.copy(confirmPassword = it) },
        onTogglePasswordVisibility = { state = state.copy(isPasswordVisible = !state.isPasswordVisible) },
        onToggleConfirmPasswordVisibility = { state = state.copy(isConfirmPasswordVisible = !state.isConfirmPasswordVisible) },
        onLevelSelect = { state = state.copy(selectedLevel = it) },
        onTermsCheckedChange = { state = state.copy(isTermsAccepted = it) }
    )
}

@Preview(name = "2. Trạng thái lỗi (Error)", showBackground = true)
@Composable
fun SignUpScreenErrorPreview() {
    SignUpScreen(
        uiState = SignUpUiState(
            fullName = "Nguyễn Văn A",
            identifier = "nguyenvana@owla.edu",
            password = "123",
            confirmPassword = "1234",
            selectedLevel = "A2",
            isTermsAccepted = true,
            errorMessage = "Mật khẩu xác nhận không khớp. Vui lòng kiểm tra lại!"
        )
    )
}

@Preview(name = "3. Trạng thái Loading", showBackground = true)
@Composable
fun SignUpScreenLoadingPreview() {
    SignUpScreen(
        uiState = SignUpUiState(
            fullName = "Trần Thị B",
            identifier = "tranthib@owla.edu",
            password = "securepassword",
            confirmPassword = "securepassword",
            selectedLevel = "B1",
            isTermsAccepted = true,
            isLoading = true
        )
    )
}

@Preview(
    name = "4. Giao diện Dark Mode",
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES
)
@Composable
fun SignUpScreenDarkModePreview() {
    Surface(color = OwlaColors.DarkBackground) {
        SignUpScreen(
            uiState = SignUpUiState(
                fullName = "Night Owl",
                identifier = "nightowl@owla.edu",
                selectedLevel = "A1",
                isTermsAccepted = true
            )
        )
    }
}

/**
 * Stateful wrapper for SignUpScreen that manages its state via SignUpViewModel.
 * Keeps MainActivity clean and decoupled from feature logic.
 */
@Composable
fun SignUpRoute(
    modifier: Modifier = Modifier,
    viewModel: SignUpViewModel = viewModel(),
    onNavigateToLogin: () -> Unit = {}
) {
    SignUpScreen(
        modifier = modifier,
        uiState = viewModel.uiState,
        onFullNameChange = viewModel::onFullNameChange,
        onIdentifierChange = viewModel::onIdentifierChange,
        onPasswordChange = viewModel::onPasswordChange,
        onConfirmPasswordChange = viewModel::onConfirmPasswordChange,
        onTogglePasswordVisibility = viewModel::onTogglePasswordVisibility,
        onToggleConfirmPasswordVisibility = viewModel::onToggleConfirmPasswordVisibility,
        onLevelSelect = viewModel::onLevelSelect,
        onTermsCheckedChange = viewModel::onTermsCheckedChange,
        onRegisterClick = { viewModel.register {} },
        onLoginClick = onNavigateToLogin
    )
}
