package com.example.breakroom.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Article
import androidx.compose.material.icons.outlined.People
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

enum class NavDestination(
    val route: String,
    val label: String,
    val icon: ImageVector
) {
    HOME("home", "Home", Icons.Filled.Home),
    BLOG("blog", "Blog", Icons.Outlined.Article),
    CHAT("chat", "Chat", Icons.Outlined.ChatBubbleOutline),
    FRIENDS("friends", "Friends", Icons.Outlined.People),
    PROFILE("profile", "Profile", Icons.Filled.Person)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopNavigationBar(
    currentRoute: String,
    onNavigate: (NavDestination) -> Unit,
    modifier: Modifier = Modifier
) {
    TopAppBar(
        title = {
            Text(
                text = "Breakroom",
                fontWeight = FontWeight.Bold
            )
        },
        actions = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                NavDestination.entries.forEach { destination ->
                    val isSelected = currentRoute == destination.route
                    IconButton(
                        onClick = { onNavigate(destination) }
                    ) {
                        Icon(
                            imageVector = destination.icon,
                            contentDescription = destination.label,
                            tint = if (isSelected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        modifier = modifier
    )
}
