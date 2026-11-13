package com.nhom21.english_learning_app.core.ui.components

import android.content.res.Configuration
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nhom21.english_learning_app.R
import com.nhom21.english_learning_app.core.navigation.MainTab
import com.nhom21.english_learning_app.ui.theme.Baloo2
import com.nhom21.english_learning_app.ui.theme.OwlaColors

object OwlaBottomBarDefaults {
    // owla-design-system §3.1: Deep Teal. Color.kt has no equivalent token yet.
    val SelectedColor = Color(0xFF036268)
}

/** Stateless four-tab navigation. MainScaffold owns route and selection state. */
@Composable
fun OwlaBottomBar(
    selectedTab: MainTab,
    onTabSelected: (MainTab) -> Unit,
    modifier: Modifier = Modifier,
    hasPracticeNotification: Boolean = false
) {
    val isDark = isSystemInDarkTheme()
    val surface = if (isDark) OwlaColors.DarkSurface else OwlaColors.LightSurface
    val border = if (isDark) OwlaColors.DarkBorder else OwlaColors.LightBorder
    // Keep unselected icons/labels legible rather than dimming the source art with alpha.
    val inactive = if (isDark) OwlaColors.DarkTextPrimary else OwlaColors.LightTextPrimary

    Surface(
        modifier = modifier.fillMaxWidth().testTag("OwlaBottomBar"),
        color = surface,
        border = BorderStroke(2.dp, border)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .heightIn(min = 64.dp)
                .selectableGroup()
                .padding(horizontal = 4.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            MainTab.entries.forEach { tab ->
                val selected = tab == selectedTab
                val color = if (selected) OwlaBottomBarDefaults.SelectedColor else inactive
                val shape = RoundedCornerShape(12.dp)
                val hasBadge = tab == MainTab.PRACTICE && hasPracticeNotification
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .clip(shape)
                        // A light neutral backing keeps Deep Teal readable in dark mode too.
                        .background(if (selected) OwlaColors.LightBackground else Color.Transparent)
                        .border(2.dp, if (selected) color else Color.Transparent, shape)
                        .selectable(
                            selected = selected,
                            role = Role.Tab,
                            onClick = { onTabSelected(tab) }
                        )
                        .semantics {
                            if (hasBadge) stateDescription = "Có từ cần ôn"
                        }
                        .padding(vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box {
                        Icon(
                            painter = painterResource(tab.iconRes),
                            contentDescription = null, // The parent tab exposes its visible label.
                            tint = color,
                            modifier = Modifier.size(26.dp)
                        )
                        if (hasBadge) {
                            Box(
                                Modifier.size(8.dp).align(Alignment.TopEnd)
                                    .background(OwlaColors.AccentBase, CircleShape)
                            )
                        }
                    }
                    Text(
                        text = tab.label,
                        fontFamily = Baloo2,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                        fontSize = 14.sp,
                        color = color
                    )
                }
            }
        }
    }
}

internal val MainTab.label: String
    get() = when (this) {
        MainTab.HOME -> "Home"
        MainTab.LESSON -> "Lesson"
        MainTab.PRACTICE -> "Practice"
        MainTab.PROFILE -> "Profile"
    }

private val MainTab.iconRes: Int
    get() = when (this) {
        MainTab.HOME -> R.drawable.ic_home
        MainTab.LESSON -> R.drawable.ic_lesson
        MainTab.PRACTICE -> R.drawable.ic_practice
        MainTab.PROFILE -> R.drawable.ic_profile
    }

@Preview(showBackground = true, widthDp = 320, fontScale = 1.3f)
@Composable
private fun OwlaBottomBarPreview() {
    OwlaBottomBar(MainTab.HOME, {}, hasPracticeNotification = true)
}

@Preview(showBackground = true, widthDp = 320, fontScale = 1.3f, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun OwlaBottomBarDarkPreview() {
    OwlaBottomBar(MainTab.PROFILE, {})
}
