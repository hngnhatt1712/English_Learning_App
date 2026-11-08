package com.nhom21.english_learning_app.features.profile

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.nhom21.english_learning_app.BuildConfig
import com.nhom21.english_learning_app.R
import com.nhom21.english_learning_app.core.ui.components.Owla3DButton
import com.nhom21.english_learning_app.features.home.HomeTab
import com.nhom21.english_learning_app.ui.theme.Baloo2
import com.nhom21.english_learning_app.ui.theme.OwlaColors
import com.nhom21.english_learning_app.ui.theme.Quicksand

/**
 * Giao diện màn hình Hồ sơ cá nhân (ProfileScreen) bám sát Spec thiết kế Owla English (mục 8.15).
 */
@Composable
fun ProfileScreen(
    modifier: Modifier = Modifier,
    uiState: ProfileUiState = ProfileUiState(),
    onTabSelected: (HomeTab) -> Unit = {},
    onNavigateToStatistics: () -> Unit = {},
    onEditUsernameClick: () -> Unit = {},
    onChangePasswordClick: () -> Unit = {},
    onNotificationSettingsClick: () -> Unit = {},
    onThemeClick: () -> Unit = {},
    onContactUsClick: () -> Unit = {},
    onLogoutClick: () -> Unit = {},
    onToggleSound: (Boolean) -> Unit = {},
    onToggleVibrate: (Boolean) -> Unit = {},
    onDismissSnackbar: () -> Unit = {}
) {
    val isDark = isSystemInDarkTheme()

    val backgroundColor = if (isDark) OwlaColors.DarkBackground else OwlaColors.LightBackground
    val surfaceColor = if (isDark) OwlaColors.DarkSurface else OwlaColors.LightSurface
    val textPrimary = if (isDark) OwlaColors.DarkTextPrimary else OwlaColors.LightTextPrimary
    val textSecondary = if (isDark) OwlaColors.DarkTextSecondary else OwlaColors.LightTextSecondary
    val borderColor = if (isDark) OwlaColors.DarkBorder else OwlaColors.LightBorder

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.snackbarMessage) {
        uiState.snackbarMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            onDismissSnackbar()
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = backgroundColor,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            ProfileBottomBar(
                selectedTab = uiState.selectedTab,
                onTabSelected = onTabSelected,
                isDark = isDark
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Tiêu đề trang Profile
            Text(
                text = "Hồ sơ cá nhân",
                fontFamily = Baloo2,
                fontWeight = FontWeight.Bold,
                fontSize = 26.sp,
                color = textPrimary,
                modifier = Modifier.padding(top = 8.dp)
            )

            // 1. Header Card (Avatar Owla, username, email read-only, streak & star badges)
            ProfileHeaderCard(
                uiState = uiState,
                surfaceColor = surfaceColor,
                borderColor = borderColor,
                textPrimary = textPrimary,
                textSecondary = textSecondary,
                isDark = isDark
            )

            // 2. Option List (Danh sách cài đặt & tính năng)
            ProfileOptionList(
                uiState = uiState,
                surfaceColor = surfaceColor,
                borderColor = borderColor,
                textPrimary = textPrimary,
                textSecondary = textSecondary,
                onNavigateToStatistics = onNavigateToStatistics,
                onEditUsernameClick = onEditUsernameClick,
                onChangePasswordClick = onChangePasswordClick,
                onNotificationSettingsClick = onNotificationSettingsClick,
                onThemeClick = onThemeClick,
                onContactUsClick = onContactUsClick,
                onLogoutClick = onLogoutClick,
                onToggleSound = onToggleSound,
                onToggleVibrate = onToggleVibrate
            )

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

/**
 * 1. Component Header Card chứa Avatar Mascot Owla, Tên, Email (read-only) và các Badge.
 */
@Composable
private fun ProfileHeaderCard(
    uiState: ProfileUiState,
    surfaceColor: Color,
    borderColor: Color,
    textPrimary: Color,
    textSecondary: Color,
    isDark: Boolean
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = surfaceColor,
        border = BorderStroke(1.5.dp, borderColor),
        shadowElevation = 2.dp
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Avatar Owla với Halo viền sáng (Rim-light ở Dark mode)
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .clip(CircleShape)
                    .background(if (isDark) Color(0xFF2C3E50) else Color(0xFFE8F6F4)),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_owla),
                    contentDescription = "Owla Mascot Profile Avatar",
                    modifier = Modifier.size(80.dp)
                )
            }

            // Tên người dùng & Email
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = uiState.username,
                    fontFamily = Baloo2,
                    fontWeight = FontWeight.Bold,
                    fontSize = 24.sp,
                    color = textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                // Email dạng Read-Only mờ kèm Icon khoá 🔒 (Spec 8.15)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Email Read-only Lock Icon",
                        tint = textSecondary,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = uiState.email,
                        fontFamily = Quicksand,
                        fontWeight = FontWeight.Medium,
                        fontSize = 14.sp,
                        color = textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Dòng chứa 2 Badge: Streak 🔥 và Star ⭐
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Streak Badge
                Surface(
                    shape = RoundedCornerShape(9999.dp),
                    color = if (isDark) OwlaColors.DarkBackground else Color(0xFFFFF5F2),
                    border = BorderStroke(1.5.dp, OwlaColors.AccentBase.copy(alpha = 0.5f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_streak),
                            contentDescription = "Streak Icon",
                            tint = Color.Unspecified,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "${uiState.currentStreak} ngày",
                            fontFamily = Baloo2,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = textPrimary
                        )
                    }
                }

                // Total Stars Badge
                Surface(
                    shape = RoundedCornerShape(9999.dp),
                    color = if (isDark) OwlaColors.DarkBackground else Color(0xFFFFFDF0),
                    border = BorderStroke(1.5.dp, OwlaColors.SecondaryBase.copy(alpha = 0.5f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_star),
                            contentDescription = "Star Icon",
                            tint = Color(0xFFFFC93C),
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "${uiState.totalStars}/${uiState.maxStars} sao",
                            fontFamily = Baloo2,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = textPrimary
                        )
                    }
                }
            }
        }
    }
}

/**
 * 2. Component Danh sách cài đặt & tính năng (Option List).
 */
@Composable
private fun ProfileOptionList(
    uiState: ProfileUiState,
    surfaceColor: Color,
    borderColor: Color,
    textPrimary: Color,
    textSecondary: Color,
    onNavigateToStatistics: () -> Unit,
    onEditUsernameClick: () -> Unit,
    onChangePasswordClick: () -> Unit,
    onNotificationSettingsClick: () -> Unit,
    onThemeClick: () -> Unit,
    onContactUsClick: () -> Unit,
    onLogoutClick: () -> Unit,
    onToggleSound: (Boolean) -> Unit,
    onToggleVibrate: (Boolean) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = surfaceColor,
        border = BorderStroke(1.5.dp, borderColor),
        shadowElevation = 1.dp
    ) {
        Column(
            modifier = Modifier.padding(vertical = 8.dp)
        ) {
            // (1) Thống kê học tập
            ProfileOptionRow(
                iconRes = R.drawable.ic_star,
                title = "Thống kê học tập",
                subtitle = "Xem tổng số sao, tiến độ và từ vựng",
                textPrimary = textPrimary,
                textSecondary = textSecondary,
                iconTint = Color(0xFFFFC93C),
                onClick = onNavigateToStatistics
            )

            ProfileDivider(borderColor)

            // (2) Chỉnh sửa hồ sơ (đổi Username)
            ProfileOptionRow(
                iconRes = R.drawable.ic_name,
                title = "Chỉnh sửa hồ sơ",
                subtitle = "Đổi tên hiển thị",
                textPrimary = textPrimary,
                textSecondary = textSecondary,
                iconTint = OwlaColors.PrimaryBase,
                onClick = onEditUsernameClick
            )

            ProfileDivider(borderColor)

            // (3) Đổi mật khẩu
            ProfileOptionRow(
                iconRes = R.drawable.ic_reset_password,
                title = "Đổi mật khẩu",
                subtitle = "Cập nhật mật khẩu mới cho tài khoản",
                textPrimary = textPrimary,
                textSecondary = textSecondary,
                iconTint = OwlaColors.AccentBase,
                onClick = onChangePasswordClick
            )

            ProfileDivider(borderColor)

            // (4) Thông báo
            ProfileOptionRow(
                iconRes = R.drawable.ic_clock,
                title = "Thông báo",
                subtitle = if (uiState.dailyReminderEnabled) "Nhắc học hằng ngày lúc ${uiState.reminderTime}" else "Đã tắt nhắc nhở",
                textPrimary = textPrimary,
                textSecondary = textSecondary,
                iconTint = OwlaColors.PrimaryBase,
                onClick = onNotificationSettingsClick
            )

            ProfileDivider(borderColor)

            // (5) Giao diện
            val themeLabel = when (uiState.appThemeMode) {
                AppThemeMode.SYSTEM -> "Theo hệ thống"
                AppThemeMode.LIGHT -> "Giao diện Sáng"
                AppThemeMode.DARK -> "Giao diện Tối"
            }
            ProfileOptionRow(
                iconRes = R.drawable.ic_home,
                title = "Giao diện",
                subtitle = themeLabel,
                textPrimary = textPrimary,
                textSecondary = textSecondary,
                iconTint = OwlaColors.PrimaryBase,
                onClick = onThemeClick
            )

            ProfileDivider(borderColor)

            // (6a) Âm thanh SFX Switch
            ProfileSwitchRow(
                iconRes = R.drawable.ic_practice,
                title = "Âm thanh hiệu ứng",
                subtitle = "Bật âm thanh chúc mừng và phát âm",
                isChecked = uiState.soundEnabled,
                textPrimary = textPrimary,
                textSecondary = textSecondary,
                iconTint = OwlaColors.PrimaryBase,
                onCheckedChange = onToggleSound
            )

            ProfileDivider(borderColor)

            // (6b) Phản hồi rung Haptics Switch
            ProfileSwitchRow(
                iconRes = R.drawable.ic_flashcard,
                title = "Phản hồi rung",
                subtitle = "Rung nhẹ khi bấm nút hoặc trả lời đúng/sai",
                isChecked = uiState.vibrateEnabled,
                textPrimary = textPrimary,
                textSecondary = textSecondary,
                iconTint = OwlaColors.AccentBase,
                onCheckedChange = onToggleVibrate
            )

            ProfileDivider(borderColor)

            // (7) Liên hệ & Góp ý (mailto)
            ProfileOptionRow(
                iconRes = R.drawable.ic_profile,
                title = "Liên hệ & Báo lỗi",
                subtitle = "Gửi góp ý qua email (${uiState.adminEmail})",
                textPrimary = textPrimary,
                textSecondary = textSecondary,
                iconTint = OwlaColors.PrimaryBase,
                onClick = onContactUsClick
            )

            ProfileDivider(borderColor)

            // (8) Đăng xuất (Danger Red)
            ProfileOptionRow(
                iconRes = R.drawable.ic_logout,
                title = "Đăng xuất",
                subtitle = "Thoát khỏi tài khoản hiện tại",
                textPrimary = OwlaColors.DangerBase,
                textSecondary = OwlaColors.DangerBase.copy(alpha = 0.7f),
                iconTint = OwlaColors.DangerBase,
                isDanger = true,
                onClick = onLogoutClick
            )
        }
    }
}

/**
 * Dòng tùy chọn tiêu chuẩn với Icon, Tiêu đề, Subtitle và Chevron.
 */
@Composable
private fun ProfileOptionRow(
    iconRes: Int,
    title: String,
    subtitle: String,
    textPrimary: Color,
    textSecondary: Color,
    iconTint: Color,
    isDanger: Boolean = false,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .background(
                    color = if (isDanger) OwlaColors.DangerBase.copy(alpha = 0.12f)
                    else iconTint.copy(alpha = 0.12f),
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(id = iconRes),
                contentDescription = title,
                tint = iconTint,
                modifier = Modifier.size(22.dp)
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontFamily = Baloo2,
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp,
                color = textPrimary,
                lineHeight = 20.sp
            )
            Text(
                text = subtitle,
                fontFamily = Quicksand,
                fontWeight = FontWeight.Medium,
                fontSize = 13.sp,
                color = textSecondary
            )
        }

        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = "Navigate Next Arrow",
            tint = textSecondary,
            modifier = Modifier.size(20.dp)
        )
    }
}

/**
 * Dòng tùy chọn kèm công tắc Bật/Tắt (Switch).
 */
@Composable
private fun ProfileSwitchRow(
    iconRes: Int,
    title: String,
    subtitle: String,
    isChecked: Boolean,
    textPrimary: Color,
    textSecondary: Color,
    iconTint: Color,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .background(
                    color = iconTint.copy(alpha = 0.12f),
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(id = iconRes),
                contentDescription = title,
                tint = iconTint,
                modifier = Modifier.size(22.dp)
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontFamily = Baloo2,
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp,
                color = textPrimary,
                lineHeight = 20.sp
            )
            Text(
                text = subtitle,
                fontFamily = Quicksand,
                fontWeight = FontWeight.Medium,
                fontSize = 13.sp,
                color = textSecondary
            )
        }

        Switch(
            checked = isChecked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = OwlaColors.PrimaryBase,
                uncheckedThumbColor = Color.White,
                uncheckedTrackColor = Color(0xFFCBD3D9)
            )
        )
    }
}

@Composable
private fun ProfileDivider(borderColor: Color) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .padding(horizontal = 18.dp)
            .background(borderColor.copy(alpha = 0.5f))
    )
}

// ---------------------------------------------------------------------------
// DIALOGS & BOTTOM SHEETS
// ---------------------------------------------------------------------------

/**
 * Dialog chỉnh sửa Tên người dùng (Edit Username Dialog).
 */
@Composable
private fun EditUsernameDialog(
    currentValue: String,
    errorMessage: String?,
    isLoading: Boolean,
    onValueChange: (String) -> Unit,
    onSaveClick: () -> Unit,
    onDismissRequest: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        shape = RoundedCornerShape(28.dp),
        title = {
            Text(
                text = "Chỉnh sửa tên người dùng",
                fontFamily = Baloo2,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Email là cố định và không thể thay đổi. Tên người dùng từ 3 đến 30 ký tự.",
                    fontFamily = Quicksand,
                    fontSize = 14.sp
                )

                OutlinedTextField(
                    value = currentValue,
                    onValueChange = onValueChange,
                    label = { Text("Username") },
                    isError = errorMessage != null,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp)
                )

                if (errorMessage != null) {
                    Text(
                        text = errorMessage,
                        color = OwlaColors.DangerBase,
                        fontSize = 13.sp,
                        fontFamily = Quicksand
                    )
                }
            }
        },
        confirmButton = {
            Owla3DButton(
                text = "Lưu lại",
                onClick = onSaveClick,
                isLoading = isLoading,
                height = 44.dp,
                modifier = Modifier.width(110.dp)
            )
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(
                    text = "Hủy",
                    fontFamily = Baloo2,
                    fontWeight = FontWeight.Bold,
                    color = Color.Gray
                )
            }
        }
    )
}

/**
 * Dialog Đổi mật khẩu (Change Password Dialog).
 */
@Composable
private fun ChangePasswordDialog(
    currentPass: String,
    newPass: String,
    confirmPass: String,
    currentPassError: String?,
    newPassError: String?,
    confirmPassError: String?,
    errorMessage: String?,
    isLoading: Boolean,
    onCurrentPassChange: (String) -> Unit,
    onNewPassChange: (String) -> Unit,
    onConfirmPassChange: (String) -> Unit,
    onSubmitClick: () -> Unit,
    onDismissRequest: () -> Unit
) {
    var showCurrentPass by remember { mutableStateOf(false) }
    var showNewPass by remember { mutableStateOf(false) }
    var showConfirmPass by remember { mutableStateOf(false) }

    val passwordKeyboardOptions = KeyboardOptions(
        keyboardType = KeyboardType.Password,
        autoCorrectEnabled = false
    )

    AlertDialog(
        onDismissRequest = { if (!isLoading) onDismissRequest() },
        shape = RoundedCornerShape(28.dp),
        title = {
            Text(
                text = "Đổi mật khẩu",
                fontFamily = Baloo2,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // Ô 1: Mật khẩu hiện tại
                OutlinedTextField(
                    value = currentPass,
                    onValueChange = onCurrentPassChange,
                    label = { Text("Mật khẩu hiện tại") },
                    enabled = !isLoading,
                    isError = currentPassError != null,
                    supportingText = currentPassError?.let { err ->
                        {
                            Text(
                                text = err,
                                color = OwlaColors.DangerBase,
                                fontSize = 12.sp,
                                fontFamily = Quicksand
                            )
                        }
                    },
                    singleLine = true,
                    keyboardOptions = passwordKeyboardOptions,
                    visualTransformation = if (showCurrentPass) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(
                            onClick = { showCurrentPass = !showCurrentPass },
                            enabled = !isLoading
                        ) {
                            Icon(
                                imageVector = if (showCurrentPass) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                contentDescription = "Toggle password visibility"
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp)
                )

                // Ô 2: Mật khẩu mới
                OutlinedTextField(
                    value = newPass,
                    onValueChange = onNewPassChange,
                    label = { Text("Mật khẩu mới (≥ 8 ký tự)") },
                    enabled = !isLoading,
                    isError = newPassError != null,
                    supportingText = newPassError?.let { err ->
                        {
                            Text(
                                text = err,
                                color = OwlaColors.DangerBase,
                                fontSize = 12.sp,
                                fontFamily = Quicksand
                            )
                        }
                    },
                    singleLine = true,
                    keyboardOptions = passwordKeyboardOptions,
                    visualTransformation = if (showNewPass) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(
                            onClick = { showNewPass = !showNewPass },
                            enabled = !isLoading
                        ) {
                            Icon(
                                imageVector = if (showNewPass) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                contentDescription = "Toggle password visibility"
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp)
                )

                // Ô 3: Nhập lại mật khẩu mới
                OutlinedTextField(
                    value = confirmPass,
                    onValueChange = onConfirmPassChange,
                    label = { Text("Nhập lại mật khẩu mới") },
                    enabled = !isLoading,
                    isError = confirmPassError != null,
                    supportingText = confirmPassError?.let { err ->
                        {
                            Text(
                                text = err,
                                color = OwlaColors.DangerBase,
                                fontSize = 12.sp,
                                fontFamily = Quicksand
                            )
                        }
                    },
                    singleLine = true,
                    keyboardOptions = passwordKeyboardOptions,
                    visualTransformation = if (showConfirmPass) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(
                            onClick = { showConfirmPass = !showConfirmPass },
                            enabled = !isLoading
                        ) {
                            Icon(
                                imageVector = if (showConfirmPass) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                contentDescription = "Toggle password visibility"
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp)
                )

                if (errorMessage != null) {
                    Text(
                        text = errorMessage,
                        color = OwlaColors.DangerBase,
                        fontSize = 13.sp,
                        fontFamily = Quicksand
                    )
                }
            }
        },
        confirmButton = {
            Owla3DButton(
                text = "Đổi mật khẩu",
                onClick = onSubmitClick,
                enabled = !isLoading,
                isLoading = isLoading,
                height = 44.dp,
                modifier = Modifier.width(135.dp)
            )
        },
        dismissButton = {
            TextButton(
                onClick = { if (!isLoading) onDismissRequest() },
                enabled = !isLoading
            ) {
                Text(
                    text = "Hủy",
                    fontFamily = Baloo2,
                    fontWeight = FontWeight.Bold,
                    color = Color.Gray
                )
            }
        }
    )
}

/**
 * Dialog Xác nhận Đăng xuất với 2 nút xếp dọc (Hủy trên, Đăng xuất Danger dưới).
 */
@Composable
private fun LogoutDialog(
    isLoggingOut: Boolean,
    onConfirmLogout: () -> Unit,
    onDismissRequest: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { if (!isLoggingOut) onDismissRequest() },
        shape = RoundedCornerShape(28.dp),
        title = {
            Text(
                text = "Đăng xuất tài khoản?",
                fontFamily = Baloo2,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp
            )
        },
        text = {
            Text(
                text = "Bạn có chắc chắn muốn đăng xuất? Tiến độ học tập đã lưu trên máy chủ sẽ không bị mất.",
                fontFamily = Quicksand,
                fontSize = 15.sp
            )
        },
        confirmButton = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Nút 1: Ở lại / Hủy (Chính ở trên)
                Owla3DButton(
                    text = "Ở lại học tiếp",
                    onClick = { if (!isLoggingOut) onDismissRequest() },
                    enabled = !isLoggingOut,
                    baseColor = OwlaColors.PrimaryBase,
                    shadowColor = OwlaColors.PrimaryShadow,
                    height = 46.dp
                )

                // Nút 2: Đăng xuất (Phụ / Danger ở dưới)
                Owla3DButton(
                    text = "Đăng xuất",
                    onClick = onConfirmLogout,
                    enabled = !isLoggingOut,
                    isLoading = isLoggingOut,
                    baseColor = OwlaColors.DangerBase,
                    shadowColor = OwlaColors.DangerShadow,
                    height = 44.dp
                )
            }
        },
        dismissButton = null
    )
}

/**
 * Bottom Sheet Liên hệ & Góp ý qua Mailto (Spec 8.15).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ContactUsSheet(
    adminEmail: String,
    appVersionName: String,
    appVersionCode: Int,
    category: ContactCategory,
    messageInput: String,
    onCategorySelect: (ContactCategory) -> Unit,
    onMessageChange: (String) -> Unit,
    onDismissRequest: () -> Unit
) {
    val context = LocalContext.current
    var showTechDetails by remember { mutableStateOf(false) }

    val sheetState = rememberModalBottomSheetState()

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "Liên hệ & Góp ý",
                fontFamily = Baloo2,
                fontWeight = FontWeight.Bold,
                fontSize = 22.sp
            )

            Text(
                text = "Owla luôn lắng nghe góp ý từ bạn! Chọn loại phản hồi và nhập nội dung bên dưới:",
                fontFamily = Quicksand,
                fontSize = 14.sp
            )

            // Category Chips
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ContactCategory.entries.forEach { cat ->
                    FilterChip(
                        selected = category == cat,
                        onClick = { onCategorySelect(cat) },
                        label = {
                            Text(
                                text = cat.label,
                                fontFamily = Baloo2,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    )
                }
            }

            // Message Input
            Column {
                OutlinedTextField(
                    value = messageInput,
                    onValueChange = onMessageChange,
                    label = { Text("Nội dung góp ý / báo lỗi") },
                    placeholder = { Text("Nhập chi tiết ý kiến của bạn...") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(130.dp),
                    shape = RoundedCornerShape(16.dp)
                )

                Text(
                    text = "${messageInput.length}/1000 ký tự",
                    fontFamily = Quicksand,
                    fontSize = 12.sp,
                    color = Color.Gray,
                    modifier = Modifier
                        .align(Alignment.End)
                        .padding(top = 4.dp)
                )
            }

            // Technical Info Expander
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFF7F9FA), shape = RoundedCornerShape(12.dp))
                    .clickable { showTechDetails = !showTechDetails }
                    .padding(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Thông tin kỹ thuật gửi kèm",
                        fontFamily = Baloo2,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = Color(0xFF1F2933)
                    )
                    Icon(
                        imageVector = if (showTechDetails) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = "Expand info"
                    )
                }

                AnimatedVisibility(visible = showTechDetails) {
                    Column(
                        modifier = Modifier.padding(top = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text("• App Version: $appVersionName (Build $appVersionCode)", fontSize = 12.sp, fontFamily = Quicksand)
                        Text("• Device: ${Build.MANUFACTURER} ${Build.MODEL}", fontSize = 12.sp, fontFamily = Quicksand)
                        Text("• Android Version: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})", fontSize = 12.sp, fontFamily = Quicksand)
                    }
                }
            }

            // Button Mở Email Intent
            Owla3DButton(
                text = "Mở ứng dụng email",
                onClick = {
                    sendEmailIntent(
                        context = context,
                        adminEmail = adminEmail,
                        subject = "[Owla][${category.label}] Ý kiến từ người dùng",
                        body = "$messageInput\n\n---\nApp Version: $appVersionName (Build $appVersionCode)\nDevice: ${Build.MANUFACTURER} ${Build.MODEL}\nAndroid: ${Build.VERSION.RELEASE}"
                    )
                    onDismissRequest()
                },
                baseColor = OwlaColors.PrimaryBase,
                shadowColor = OwlaColors.PrimaryShadow,
                height = 50.dp,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        }
    }
}

/**
 * Bắn Intent mailto: mở ứng dụng email ngoài (Spec 8.15).
 */
private fun sendEmailIntent(
    context: Context,
    adminEmail: String,
    subject: String,
    body: String
) {
    val intent = Intent(Intent.ACTION_SENDTO).apply {
        data = Uri.parse("mailto:$adminEmail")
        putExtra(Intent.EXTRA_SUBJECT, subject)
        putExtra(Intent.EXTRA_TEXT, body)
    }

    try {
        context.startActivity(Intent.createChooser(intent, "Mở ứng dụng Email..."))
        Toast.makeText(context, "Đã mở ứng dụng email. Nhấn Gửi để hoàn tất.", Toast.LENGTH_LONG).show()
    } catch (_: Exception) {
        // Nếu không có app email: Copy địa chỉ vào clipboard
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Admin Email", adminEmail)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(
            context,
            "Chưa có ứng dụng email. Đã sao chép địa chỉ $adminEmail để bạn gửi thủ công.",
            Toast.LENGTH_LONG
        ).show()
    }
}

/**
 * Bottom Navigation Bar dùng chung cho trang Profile.
 */
@Composable
private fun ProfileBottomBar(
    selectedTab: HomeTab,
    onTabSelected: (HomeTab) -> Unit,
    isDark: Boolean
) {
    val barBackground = if (isDark) OwlaColors.DarkSurface else Color.White
    val border = if (isDark) OwlaColors.DarkBorder else OwlaColors.LightBorder

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = barBackground,
        border = BorderStroke(1.dp, border),
        shadowElevation = 8.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ProfileNavItem(
                modifier = Modifier.weight(1f),
                iconRes = R.drawable.ic_home,
                label = "Home",
                isSelected = selectedTab == HomeTab.HOME,
                onClick = { onTabSelected(HomeTab.HOME) }
            )

            ProfileNavItem(
                modifier = Modifier.weight(1f),
                iconRes = R.drawable.ic_lesson,
                label = "Lesson",
                isSelected = selectedTab == HomeTab.LESSON,
                onClick = { onTabSelected(HomeTab.LESSON) }
            )

            ProfileNavItem(
                modifier = Modifier.weight(1f),
                iconRes = R.drawable.ic_practice,
                label = "Practice",
                isSelected = selectedTab == HomeTab.PRACTICE,
                onClick = { onTabSelected(HomeTab.PRACTICE) }
            )

            ProfileNavItem(
                modifier = Modifier.weight(1f),
                iconRes = R.drawable.ic_profile,
                label = "Profile",
                isSelected = selectedTab == HomeTab.PROFILE,
                onClick = { onTabSelected(HomeTab.PROFILE) }
            )
        }
    }
}

@Composable
private fun ProfileNavItem(
    iconRes: Int,
    label: String,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val activeColor = OwlaColors.PrimaryBase
    val inactiveColor = Color(0xFF7B8B9A)

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            painter = painterResource(id = iconRes),
            contentDescription = label,
            tint = Color.Unspecified,
            modifier = Modifier
                .size(26.dp)
                .alpha(if (isSelected) 1f else 0.55f)
        )

        Text(
            text = label,
            fontFamily = Baloo2,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            fontSize = 13.sp,
            color = if (isSelected) activeColor else inactiveColor
        )
    }
}

// ---------------------------------------------------------------------------
// PREVIEWS & ROUTE
// ---------------------------------------------------------------------------

@Preview(name = "Profile Screen (Light)", showBackground = true)
@Composable
fun ProfileScreenPreview() {
    ProfileScreen()
}

@Preview(
    name = "Profile Screen (Dark Mode)",
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES
)
@Composable
fun ProfileScreenDarkModePreview() {
    ProfileScreen(uiState = ProfileUiState(username = "NguyenVanA", email = "nguyenvana@example.com", currentStreak = 12))
}

/**
 * Stateful Route cho ProfileScreen kết nối ViewModel & Hilt.
 */
@Composable
fun ProfileRoute(
    modifier: Modifier = Modifier,
    viewModel: ProfileViewModel = hiltViewModel(),
    onNavigateToHome: () -> Unit = {},
    onNavigateToLesson: () -> Unit = {},
    onNavigateToPractice: () -> Unit = {},
    onNavigateToStatistics: () -> Unit = {},
    onLoggedOut: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()

    ProfileScreen(
        modifier = modifier,
        uiState = uiState,
        onTabSelected = { tab ->
            when (tab) {
                HomeTab.HOME -> onNavigateToHome()
                HomeTab.LESSON -> onNavigateToLesson()
                HomeTab.PRACTICE -> onNavigateToPractice()
                HomeTab.PROFILE -> {}
            }
        },
        onNavigateToStatistics = onNavigateToStatistics,
        onEditUsernameClick = { viewModel.onOpenEditUsernameDialog() },
        onChangePasswordClick = { viewModel.onOpenChangePasswordDialog() },
        onNotificationSettingsClick = { viewModel.onOpenContactUsSheet() },
        onThemeClick = { viewModel.onOpenThemeDialog() },
        onContactUsClick = { viewModel.onOpenContactUsSheet() },
        onLogoutClick = { viewModel.onOpenLogoutDialog() },
        onToggleSound = { viewModel.toggleSound(it) },
        onToggleVibrate = { viewModel.toggleVibrate(it) },
        onDismissSnackbar = { viewModel.clearSnackbar() }
    )

    // Dialog Edit Username
    if (uiState.showEditUsernameDialog) {
        EditUsernameDialog(
            currentValue = uiState.editUsernameInput,
            errorMessage = uiState.editUsernameError,
            isLoading = uiState.isSaving,
            onValueChange = { viewModel.onUsernameInputChange(it) },
            onSaveClick = { viewModel.saveUsername() },
            onDismissRequest = { viewModel.onCloseEditUsernameDialog() }
        )
    }

    // Dialog Change Password
    if (uiState.showChangePasswordDialog) {
        ChangePasswordDialog(
            currentPass = uiState.currentPasswordInput,
            newPass = uiState.newPasswordInput,
            confirmPass = uiState.confirmPasswordInput,
            currentPassError = uiState.currentPasswordError,
            newPassError = uiState.newPasswordError,
            confirmPassError = uiState.confirmPasswordError,
            errorMessage = uiState.passwordError,
            isLoading = uiState.isSaving,
            onCurrentPassChange = { viewModel.onCurrentPasswordChange(it) },
            onNewPassChange = { viewModel.onNewPasswordChange(it) },
            onConfirmPassChange = { viewModel.onConfirmPasswordChange(it) },
            onSubmitClick = { viewModel.submitChangePassword() },
            onDismissRequest = { viewModel.onCloseChangePasswordDialog() }
        )
    }

    // Dialog Logout Confirmation
    if (uiState.showLogoutDialog) {
        LogoutDialog(
            isLoggingOut = uiState.isLoggingOut,
            onConfirmLogout = { viewModel.logout(onLoggedOut) },
            onDismissRequest = { viewModel.onCloseLogoutDialog() }
        )
    }

    // Sheet Contact Us
    if (uiState.showContactUsSheet) {
        ContactUsSheet(
            adminEmail = uiState.adminEmail,
            appVersionName = uiState.appVersionName,
            appVersionCode = uiState.appVersionCode,
            category = uiState.contactCategory,
            messageInput = uiState.contactMessageInput,
            onCategorySelect = { viewModel.onContactCategorySelect(it) },
            onMessageChange = { viewModel.onContactMessageChange(it) },
            onDismissRequest = { viewModel.onCloseContactUsSheet() }
        )
    }

    // Dialog Theme Mode Selection
    if (uiState.showThemeDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.onCloseThemeDialog() },
            shape = RoundedCornerShape(24.dp),
            title = {
                Text(
                    text = "Chọn giao diện",
                    fontFamily = Baloo2,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp
                )
            },
            text = {
                Column {
                    AppThemeMode.entries.forEach { mode ->
                        val label = when (mode) {
                            AppThemeMode.SYSTEM -> "Theo hệ thống"
                            AppThemeMode.LIGHT -> "Giao diện Sáng"
                            AppThemeMode.DARK -> "Giao diện Tối"
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.selectAppTheme(mode) }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = uiState.appThemeMode == mode,
                                onClick = { viewModel.selectAppTheme(mode) }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = label, fontFamily = Quicksand, fontSize = 16.sp)
                        }
                    }
                }
            },
            confirmButton = {}
        )
    }
}
