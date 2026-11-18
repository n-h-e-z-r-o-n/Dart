package com.zadhron.adaa.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.People
import androidx.compose.material.icons.rounded.Spa
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import com.zadhron.adaa.ui.theme.AdaaIcons
import com.zadhron.adaa.ui.theme.AdaaTheme
import com.zadhron.adaa.ui.theme.PrimaryRose
import com.zadhron.adaa.ui.theme.SecondaryWarm

enum class AdaaScreen(val title: String, val icon: ImageVector) {
    SCHEDULE("Schedule", Icons.Rounded.CalendarMonth),
    CLIENTS("Clients", Icons.Rounded.People),
    SERVICES("Services", Icons.Rounded.Spa),
    FINANCES("Finances", AdaaIcons.TrendingUp)
}

@Composable
fun AdaaBottomNavBar(
    currentScreen: AdaaScreen,
    onScreenSelected: (AdaaScreen) -> Unit
) {
    NavigationBar {
        AdaaScreen.entries.forEach { screen ->
            val selected = currentScreen == screen
            NavigationBarItem(
                selected = selected,
                onClick = { onScreenSelected(screen) },
                icon = {
                    Icon(
                        imageVector = screen.icon,
                        contentDescription = screen.title
                    )
                },
                label = { Text(screen.title) },
                colors = NavigationBarItemDefaults.colors(
                    indicatorColor = PrimaryRose.copy(alpha = 0.15f),
                    selectedIconColor = PrimaryRose,
                    selectedTextColor = PrimaryRose,
                    unselectedIconColor = SecondaryWarm.copy(alpha = 0.7f),
                    unselectedTextColor = SecondaryWarm.copy(alpha = 0.7f)
                )
            )
        }
    }
}

@Preview(name = "Bottom Navigation Bar", showBackground = true)
@Composable
fun AdaaBottomNavBarPreview() {
    AdaaTheme {
        AdaaBottomNavBar(
            currentScreen = AdaaScreen.SCHEDULE,
            onScreenSelected = {}
        )
    }
}
