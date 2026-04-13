package com.cherryblossomdev.breakroom.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.outlined.Business
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Work
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag

enum class BottomNavDestination(
    val route: String,
    val label: String,
    val icon: ImageVector
) {
    HOME("home", "Breakroom", Icons.Filled.Home),
    CHAT("chat", "Chat", Icons.Outlined.ChatBubbleOutline),
    EMPLOYMENT("employment", "Jobs", Icons.Outlined.Work),
    COMPANY_PORTAL("company-portal", "Company", Icons.Outlined.Business),
    TOOL_SHED("tool-shed", "Tool Shed", Icons.Filled.Build)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BottomNavigationBar(
    currentRoute: String,
    onNavigate: (BottomNavDestination) -> Unit,
    chatUnread: Int = 0,
    modifier: Modifier = Modifier
) {
    NavigationBar(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        BottomNavDestination.entries.forEach { destination ->
            val isSelected = currentRoute == destination.route
            val badgeCount = if (destination == BottomNavDestination.CHAT) chatUnread else 0
            NavigationBarItem(
                selected = isSelected,
                onClick = { onNavigate(destination) },
                modifier = Modifier.testTag("nav-${destination.route}"),
                icon = {
                    BadgedBox(
                        badge = {
                            if (badgeCount > 0) {
                                Badge(containerColor = MaterialTheme.colorScheme.error) {
                                    Text(if (badgeCount > 99) "99+" else badgeCount.toString())
                                }
                            }
                        }
                    ) {
                        Icon(
                            imageVector = destination.icon,
                            contentDescription = destination.label
                        )
                    }
                },
                label = {
                    Text(text = destination.label)
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.primary,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    indicatorColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        }
    }
}
