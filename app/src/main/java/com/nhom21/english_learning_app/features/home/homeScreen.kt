package com.nhom21.english_learning_app.features.home

import android.content.res.Configuration
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.nhom21.english_learning_app.R
import com.nhom21.english_learning_app.core.ui.components.Owla3DButton
import com.nhom21.english_learning_app.core.ui.components.OwlaHeaderLogo
import com.nhom21.english_learning_app.ui.theme.Baloo2
import com.nhom21.english_learning_app.ui.theme.OwlaColors
import com.nhom21.english_learning_app.ui.theme.Quicksand

/**
 * Giao diện Home (HomeScreen) của ứng dụng Owla English bám sát thiết kế 3D Cartoon.
 */
@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    uiState: HomeUiState = HomeUiState(),
    onContinueClick: () -> Unit = {},
    onReviewClick: () -> Unit = {},
    onFlashcardClick: () -> Unit = {},
    onStarsClick: () -> Unit = {},
) {
    val isDark = isSystemInDarkTheme()

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
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // 1. Header (Mascot Avatar, "Hello {username}", Streak Badge)
            HomeHeader(
                username = uiState.username,
                subtitle = uiState.greetingSubtitle,
                currentStreak = uiState.currentStreak,
                isDark = isDark
            )

            // 2. Thẻ bài học chính (Current Learning Topic 3D Card)
            CurrentTopicCard(
                uiState = uiState,
                surfaceColor = surfaceColor,
                borderColor = borderColor,
                textPrimary = textPrimary,
                textSecondary = textSecondary,
                onContinueClick = onContinueClick
            )

            // 3. Thẻ thống kê giữa (Flashcards & Total Stars)
            HomeMiddleStatTiles(
                uiState = uiState,
                surfaceColor = surfaceColor,
                borderColor = borderColor,
                textPrimary = textPrimary,
                textSecondary = textSecondary,
                onFlashcardClick = onFlashcardClick,
                onStarsClick = onStarsClick
            )

            // 4. Thẻ Ôn tập hôm nay (Review Today 3D Card)
            ReviewTodayCard(
                reviewCount = uiState.reviewCount,
                surfaceColor = surfaceColor,
                borderColor = borderColor,
                textPrimary = textPrimary,
                textSecondary = textSecondary,
                onReviewClick = onReviewClick
            )

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

/**
 * Component Header gồm Logo Owla, lời chào tên người dùng và Badge Streak.
 */
@Composable
private fun HomeHeader(
    username: String,
    subtitle: String,
    currentStreak: Int,
    isDark: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Cụm Avatar + Tên người dùng
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Circle Avatar chứa Mascot Owla
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFE8F6F4)),
                contentAlignment = Alignment.Center
            ) {
                OwlaHeaderLogo(
                    contentDescription = null,
                    modifier = Modifier.size(44.dp)
                )
            }

            Column {
                Text(
                    text = "Hello $username",
                    fontFamily = Baloo2,
                    fontWeight = FontWeight.Bold,
                    fontSize = 22.sp,
                    color = if (isDark) OwlaColors.DarkTextPrimary else OwlaColors.LightTextPrimary,
                    lineHeight = 26.sp
                )
                Text(
                    text = subtitle,
                    fontFamily = Quicksand,
                    fontWeight = FontWeight.Medium,
                    fontSize = 14.sp,
                    color = if (isDark) OwlaColors.DarkTextSecondary else OwlaColors.LightTextSecondary
                )
            }
        }

        // Badge Streak 🔥
        Surface(
            shape = RoundedCornerShape(9999.dp),
            color = if (isDark) OwlaColors.DarkSurface else Color(0xFFFFF5F2),
            border = BorderStroke(1.5.dp, OwlaColors.AccentBase.copy(alpha = 0.5f))
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_streak),
                    contentDescription = "Streak Flame Icon",
                    tint = Color.Unspecified,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = "$currentStreak",
                    fontFamily = Baloo2,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = if (isDark) OwlaColors.DarkTextPrimary else Color(0xFF1F2933)
                )
            }
        }
    }
}

/**
 * Component Thẻ Bài học chính (Main Learning Topic Card 3D).
 */
@Composable
private fun CurrentTopicCard(
    uiState: HomeUiState,
    surfaceColor: Color,
    borderColor: Color,
    textPrimary: Color,
    textSecondary: Color,
    onContinueClick: () -> Unit
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
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Pill Badge: "Đang học · Level 1"
                    Box(
                        modifier = Modifier
                            .background(
                                color = OwlaColors.PrimaryBase.copy(alpha = 0.12f),
                                shape = RoundedCornerShape(9999.dp)
                            )
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "${uiState.learningStatus} · ${uiState.levelName}",
                            fontFamily = Baloo2,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = OwlaColors.PrimaryBase
                        )
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    // Tên Topic: "Food and drinks"
                    Text(
                        text = uiState.currentTopicName,
                        fontFamily = Baloo2,
                        fontWeight = FontWeight.Bold,
                        fontSize = 24.sp,
                        color = textPrimary,
                        lineHeight = 28.sp
                    )

                    // Subtitle: "Topic 2 · Round 3/5"
                    Text(
                        text = "Topic ${uiState.topicOrder} · Round ${uiState.currentRound}/${uiState.totalRounds}",
                        fontFamily = Quicksand,
                        fontWeight = FontWeight.Medium,
                        fontSize = 15.sp,
                        color = textSecondary
                    )
                }

                // Hình minh họa Mascot Owla
                Image(
                    painter = painterResource(id = R.drawable.ic_owla),
                    contentDescription = "Mascot Owla Learning",
                    modifier = Modifier
                        .size(80.dp)
                        .padding(start = 8.dp)
                )
            }

            // Thanh tiến độ Progress Bar
            LinearProgressIndicator(
                progress = { uiState.progressPercent.coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .clip(RoundedCornerShape(9999.dp)),
                color = OwlaColors.PrimaryBase,
                trackColor = Color(0xFFE5E9EC),
                strokeCap = StrokeCap.Round
            )

            // Nút 3D "Tiếp tục"
            Owla3DButton(
                text = if (uiState.currentRound == 1) "Bắt đầu" else "Tiếp tục",
                onClick = onContinueClick,
                baseColor = OwlaColors.PrimaryBase,
                shadowColor = OwlaColors.PrimaryShadow,
                height = 50.dp
            )
        }
    }
}

/**
 * Component 2 Thẻ thống kê nhỏ ở giữa (Flashcards & Tổng số sao).
 */
@Composable
private fun HomeMiddleStatTiles(
    uiState: HomeUiState,
    surfaceColor: Color,
    borderColor: Color,
    textPrimary: Color,
    textSecondary: Color,
    onFlashcardClick: () -> Unit,
    onStarsClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Tile trái: Flashcard
        Surface(
            modifier = Modifier
                .weight(1f)
                .clickable { onFlashcardClick() },
            shape = RoundedCornerShape(20.dp),
            color = surfaceColor,
            border = BorderStroke(1.5.dp, borderColor),
            shadowElevation = 1.dp
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .background(
                            color = OwlaColors.PrimaryBase.copy(alpha = 0.12f),
                            shape = RoundedCornerShape(12.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_flashcard),
                        contentDescription = "Flashcard Icon",
                        tint = OwlaColors.PrimaryBase,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Column {
                    Text(
                        text = uiState.flashcardCount.toString(),
                        fontFamily = Baloo2,
                        fontWeight = FontWeight.Bold,
                        fontSize = 22.sp,
                        color = textPrimary,
                        lineHeight = 24.sp
                    )
                    Text(
                        text = "từ Flashcard",
                        fontFamily = Quicksand,
                        fontWeight = FontWeight.Medium,
                        fontSize = 13.sp,
                        color = textSecondary
                    )
                }
            }
        }

        // Tile phải: Tổng số sao
        Surface(
            modifier = Modifier
                .weight(1f)
                .clickable { onStarsClick() },
            shape = RoundedCornerShape(20.dp),
            color = surfaceColor,
            border = BorderStroke(1.5.dp, borderColor),
            shadowElevation = 1.dp
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_star),
                    contentDescription = "Star Icon",
                    tint = Color(0xFFFFC93C),
                    modifier = Modifier.size(38.dp)
                )

                Column {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = "${uiState.totalStars}",
                            fontFamily = Baloo2,
                            fontWeight = FontWeight.Bold,
                            fontSize = 22.sp,
                            color = textPrimary,
                            lineHeight = 24.sp
                        )
                        Text(
                            text = "/${uiState.maxStars}",
                            fontFamily = Baloo2,
                            fontWeight = FontWeight.Medium,
                            fontSize = 14.sp,
                            color = textSecondary
                        )
                    }
                    Text(
                        text = "tổng số sao",
                        fontFamily = Quicksand,
                        fontWeight = FontWeight.Medium,
                        fontSize = 13.sp,
                        color = textSecondary
                    )
                }
            }
        }
    }
}

/**
 * Component Thẻ Ôn tập hôm nay (Review Today 3D Card).
 */
@Composable
private fun ReviewTodayCard(
    reviewCount: Int,
    surfaceColor: Color,
    borderColor: Color,
    textPrimary: Color,
    textSecondary: Color,
    onReviewClick: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = surfaceColor,
        border = BorderStroke(1.5.dp, borderColor),
        shadowElevation = 1.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Icon Đồng hồ cam
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .background(
                        color = Color(0xFFFFF5F2),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_clock),
                    contentDescription = "Clock Review Icon",
                    tint = OwlaColors.AccentBase,
                    modifier = Modifier.size(26.dp)
                )
            }

            // Tiêu đề & phụ đề
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Ôn tập hôm nay",
                    fontFamily = Baloo2,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = textPrimary,
                    lineHeight = 22.sp
                )
                Text(
                    text = "$reviewCount từ cần ôn",
                    fontFamily = Quicksand,
                    fontWeight = FontWeight.Medium,
                    fontSize = 14.sp,
                    color = textSecondary
                )
            }

            // Nút 3D Accent "Ôn ngay"
            Owla3DButton(
                text = "Ôn ngay",
                onClick = onReviewClick,
                baseColor = OwlaColors.AccentBase,
                shadowColor = OwlaColors.AccentShadow,
                modifier = Modifier.width(105.dp),
                height = 44.dp
            )
        }
    }
}

// ---------------------------------------------------------------------------
// PREVIEWS
// ---------------------------------------------------------------------------

@Preview(name = "1. Home Screen (Light)", showBackground = true)
@Composable
fun HomeScreenPreview() {
    HomeScreen()
}

@Preview(
    name = "2. Home Screen (Dark Mode)",
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES
)
@Composable
fun HomeScreenDarkModePreview() {
    HomeScreen(uiState = HomeUiState(username = "User", currentStreak = 5))
}

/**
 * Stateful Route cho HomeScreen kết nối ViewModel.
 */
@Composable
fun HomeRoute(
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = hiltViewModel(),
    onNavigateToLesson: () -> Unit = {},
    onNavigateToPractice: () -> Unit = {},
    onNavigateToProfile: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()

    HomeScreen(
        modifier = modifier,
        uiState = uiState,
        onContinueClick = onNavigateToLesson,
        onReviewClick = onNavigateToPractice,
        onFlashcardClick = onNavigateToPractice,
        onStarsClick = onNavigateToProfile
    )
}
