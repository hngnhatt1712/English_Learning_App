package com.nhom21.english_learning_app.features.auth

import android.content.res.Configuration
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material3.Icon
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.nhom21.english_learning_app.R
import com.nhom21.english_learning_app.core.ui.components.Owla3DButton
import com.nhom21.english_learning_app.domain.model.Level
import com.nhom21.english_learning_app.ui.theme.Baloo2
import com.nhom21.english_learning_app.ui.theme.OwlaColors
import com.nhom21.english_learning_app.ui.theme.Quicksand

/**
 * Màn hình Chọn trình độ tiếng Anh (SelectLevelScreen) của ứng dụng Owla English.
 * Mascot Owla cùng bong bóng thoại "How much English do you know?",
 * danh sách trình độ từ backend và nút 3D Continue bên dưới.
 */
@Composable
fun SelectLevelScreen(
    modifier: Modifier = Modifier,
    uiState: SelectLevelUiState = SelectLevelUiState(),
    onLevelSelect: (String) -> Unit = {},
    onContinueClick: () -> Unit = {},
    onRetry: () -> Unit = {}
) {
    val isDark = isSystemInDarkTheme()

    val backgroundColor = if (isDark) OwlaColors.DarkBackground else Color(0xFFEFF8F6)
    val textPrimary = if (isDark) OwlaColors.DarkTextPrimary else OwlaColors.LightTextPrimary
    val surfaceColor = if (isDark) OwlaColors.DarkSurface else Color.White

    Surface(
        modifier = modifier.fillMaxSize(),
        color = backgroundColor
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // 1. Owla Mascot + Bong bóng thoại
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_owla),
                    contentDescription = "Mascot Owla",
                    modifier = Modifier.size(90.dp)
                )

                Spacer(modifier = Modifier.width(8.dp))

                OwlaSpeechBubble(
                    text = "How much English\ndo you know?",
                    isDark = isDark,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(40.dp))

            // The existing cards scroll only when server content exceeds the available space.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .selectableGroup(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (uiState.isLoading) {
                    CircularProgressIndicator(
                        color = OwlaColors.PrimaryBase,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )
                    Text("Đang tải trình độ…", fontFamily = Quicksand, color = textPrimary)
                } else if (uiState.levels.isEmpty()) {
                    Text(
                        text = uiState.errorMessage ?: "Chưa có trình độ nào. Vui lòng thử lại.",
                        fontFamily = Quicksand,
                        color = textPrimary,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                    )
                    Owla3DButton(text = "Thử lại", onClick = onRetry)
                } else uiState.levels.forEach { option ->
                    val isSelected = uiState.selectedLevelId == option.id

                    val cardBorder = if (isSelected) {
                        BorderStroke(2.dp, OwlaColors.PrimaryBase)
                    } else {
                        BorderStroke(1.dp, if (isDark) OwlaColors.DarkBorder else OwlaColors.LightBorder)
                    }

                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = isSelected,
                                enabled = !uiState.isSubmitting && !uiState.isSelectionSaved,
                                role = Role.RadioButton,
                                onClick = { onLevelSelect(option.id) }
                            ),
                        shape = RoundedCornerShape(20.dp),
                        color = surfaceColor,
                        border = cardBorder,
                        shadowElevation = if (isSelected) 2.dp else 0.dp
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 18.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            // Badge Icon + Code (Ví dụ: Icon BarChart + A1)
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .background(
                                        color = OwlaColors.PrimaryBase.copy(alpha = 0.12f),
                                        shape = CircleShape
                                    )
                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.BarChart,
                                    contentDescription = null,
                                    tint = OwlaColors.PrimaryBase,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = option.name,
                                    fontFamily = Baloo2,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp,
                                    color = if (isDark) OwlaColors.DarkTextPrimary else Color(0xFF134E45)
                                )
                            }

                            // Đường vạch phân cách
                            Box(
                                modifier = Modifier
                                    .height(36.dp)
                                    .width(1.dp)
                                    .background(if (isDark) OwlaColors.DarkBorder else OwlaColors.LightBorder)
                            )

                            // Mô tả trình độ
                            Text(
                                text = option.description.orEmpty(),
                                fontFamily = Quicksand,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 15.sp,
                                color = textPrimary,
                                lineHeight = 20.sp,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            if (!uiState.isLoading && uiState.levels.isNotEmpty() && uiState.errorMessage != null) {
                Text(
                    text = uiState.errorMessage,
                    fontFamily = Quicksand,
                    color = textPrimary,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                )
                Owla3DButton(text = "Thử lại", onClick = onRetry)
                Spacer(modifier = Modifier.height(16.dp))
            }

            // 3. Nút 3D Continue
            Owla3DButton(
                text = "Continue",
                onClick = onContinueClick,
                isLoading = uiState.isSubmitting,
                enabled = uiState.canContinue,
                baseColor = OwlaColors.PrimaryBase,
                shadowColor = OwlaColors.PrimaryShadow
            )

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

/**
 * Component bong bóng thoại (Speech Bubble) có đuôi chỉ về Mascot Owla phía bên trái.
 */
@Composable
private fun OwlaSpeechBubble(
    text: String,
    modifier: Modifier = Modifier,
    isDark: Boolean = false
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Đuôi tam giác hướng về Owla
        Canvas(
            modifier = Modifier
                .size(width = 10.dp, height = 14.dp)
                .offset(x = 1.dp)
        ) {
            val path = Path().apply {
                moveTo(size.width, 0f)
                lineTo(0f, size.height / 2f)
                lineTo(size.width, size.height)
                close()
            }
            drawPath(
                path = path,
                color = if (isDark) OwlaColors.DarkSurface else Color.White
            )
        }

        // Thân bong bóng thoại
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = if (isDark) OwlaColors.DarkSurface else Color.White,
            border = BorderStroke(1.5.dp, OwlaColors.PrimaryBase.copy(alpha = 0.35f)),
            shadowElevation = 1.dp
        ) {
            Text(
                text = text,
                fontFamily = Baloo2,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = if (isDark) OwlaColors.DarkTextPrimary else Color(0xFF134E45),
                lineHeight = 22.sp,
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp)
            )
        }
    }
}

// ---------------------------------------------------------------------------
// PREVIEWS
// ---------------------------------------------------------------------------

@Preview(name = "1. Select Level Screen (Light)", showBackground = true)
@Composable
fun SelectLevelScreenPreview() {
    var state by remember { mutableStateOf(previewSelectLevelState()) }

    SelectLevelScreen(
        uiState = state,
        onLevelSelect = { state = state.copy(selectedLevelId = it) }
    )
}

@Preview(
    name = "2. Select Level Screen (Dark Mode)",
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES
)
@Composable
fun SelectLevelScreenDarkModePreview() {
    var state by remember { mutableStateOf(previewSelectLevelState()) }

    SelectLevelScreen(
        uiState = state,
        onLevelSelect = { state = state.copy(selectedLevelId = it) }
    )
}

/**
 * Stateful wrapper cho SelectLevelScreen kết nối với SelectLevelViewModel.
 */
@Composable
fun SelectLevelRoute(
    modifier: Modifier = Modifier,
    viewModel: SelectLevelViewModel = hiltViewModel(),
    onNavigateNext: (String) -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    val navigateNext by rememberUpdatedState(onNavigateNext)

    LaunchedEffect(viewModel, lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.events.collect { event ->
                when (event) {
                    is SelectLevelEvent.SelectionSaved -> navigateNext(event.levelId)
                }
            }
        }
    }

    SelectLevelScreen(
        modifier = modifier,
        uiState = uiState,
        onLevelSelect = viewModel::onLevelSelect,
        onContinueClick = viewModel::onContinueClick,
        onRetry = viewModel::onRetry
    )
}

/** Design-only fixtures; the route always uses the API-backed ViewModel state. */
private fun previewSelectLevelState(): SelectLevelUiState {
    val levels = listOf(
        Level("preview-a1", "A1", "I just started learning English", 1),
        Level("preview-a2", "A2", "I know some words and phrases", 2),
        Level("preview-b1", "B1", "I can have a simple conversation", 3)
    )
    return SelectLevelUiState(levels = levels, selectedLevelId = "preview-a2")
}
