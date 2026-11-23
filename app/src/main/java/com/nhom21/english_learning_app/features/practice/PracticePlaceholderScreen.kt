package com.nhom21.english_learning_app.features.practice

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nhom21.english_learning_app.ui.theme.Baloo2
import com.nhom21.english_learning_app.ui.theme.OwlaColors
import com.nhom21.english_learning_app.ui.theme.Quicksand

@Composable
fun PracticePlaceholderScreen(modifier: Modifier = Modifier) {
    val textColor = if (isSystemInDarkTheme()) OwlaColors.DarkTextPrimary else OwlaColors.LightTextPrimary
    Column(
        modifier = modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Practice", fontFamily = Baloo2, fontWeight = FontWeight.SemiBold, fontSize = 24.sp, color = textColor)
        Text("Khu vực ôn tập sẽ sớm có mặt tại đây.", fontFamily = Quicksand, fontWeight = FontWeight.Medium, fontSize = 16.sp, color = textColor)
    }
}
